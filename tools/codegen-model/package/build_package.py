#!/usr/bin/env python3
"""Convert a checked numeric CatBoost JSON model to an offline .ajcgm package."""

import argparse
from dataclasses import dataclass
from datetime import datetime
import hashlib
import io
import json
import math
from pathlib import Path
import re
import struct
import sys
import zipfile
import zlib

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from dataset.task_io import parse_json
from train.data import FeatureSchema, read_schema, sha256


MAX_TREES = 4096
MAX_DEPTH = 8
MAX_FEATURES = 65535
MAX_TOTAL_BYTES = 100 * 1024 * 1024
MAX_TEXT_BYTES = 1024 * 1024
MAX_SCHEMA_BYTES = 256 * 1024
MAX_MODEL_BYTES = 32 + MAX_TREES * (1 + MAX_DEPTH * 6 + (1 << MAX_DEPTH) * 4)
ZIP_NAMES = ("manifest.json", "model.bin", "features.json", "MODEL_CARD.md", "LICENSE")
MODEL_ID = re.compile(r"[a-z0-9][a-z0-9._-]{1,63}")
HEX_SHA256 = re.compile(r"[0-9a-f]{64}")
OFFSET_TIMESTAMP = re.compile(
    r"([0-9]{4}-[0-9]{2}-[0-9]{2}T(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9])"
    r"(?:\.([0-9]{1,9}))?(Z|[+-](?:0[0-9]|1[0-8]):[0-5][0-9])"
)


@dataclass(frozen=True)
class BinaryTree:
    features: tuple[int, ...]
    borders: tuple[float, ...]
    leaves: tuple[float, ...]


@dataclass(frozen=True)
class BinaryModel:
    feature_count: int
    bias: float
    scale: float
    trees: tuple[BinaryTree, ...]


def _integer(value, name, minimum, maximum):
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError(f"{name} must be an integer in {minimum}..{maximum}")
    return value


def _finite(value, name):
    if type(value) not in (int, float):
        raise ValueError(name + " must be a finite number")
    try:
        value = float(value)
    except OverflowError as error:
        raise ValueError(name + " exceeds the finite float64 range") from error
    if not math.isfinite(value):
        raise ValueError(name + " must be finite")
    return value


def _float32(value, name):
    value = _finite(value, name)
    try:
        result = struct.unpack("<f", struct.pack("<f", value))[0]
    except (OverflowError, struct.error) as error:
        raise ValueError(name + " exceeds the finite float32 range") from error
    if not math.isfinite(result):
        raise ValueError(name + " exceeds the finite float32 range")
    return result


def _schema_check(schema):
    if not isinstance(schema, FeatureSchema):
        raise ValueError("Expected a validated numeric FeatureSchema")
    _integer(schema.version, "featureSchemaVersion", 1, 2)
    _integer(len(schema.names), "feature count", 1, MAX_FEATURES)
    if (len(set(schema.names)) != len(schema.names)
            or any(not isinstance(name, str) or not re.fullmatch(r"[a-z][a-z0-9_]*", name) for name in schema.names)
            or hashlib.sha256("\n".join(schema.names).encode()).hexdigest()[:16] != schema.names_hash):
        raise ValueError("FeatureSchema ordered names/hash is inconsistent")


def encode_model(model_json, schema):
    """Serialize numeric symmetric trees, mapping internal indices to flat columns.

    CatBoost's float_feature_index indexes feature_index, not the JSON list
    position or the flat input column. The explicit map is checked as a bijection.
    """
    _schema_check(schema)
    if not isinstance(model_json, dict) or "trees" in model_json:
        raise ValueError("Only symmetric CatBoost oblivious_trees are supported")
    info = model_json.get("features_info")
    if not isinstance(info, dict):
        raise ValueError("CatBoost features_info is required")
    for field in ("categorical_features", "text_features", "embedding_features", "estimated_features", "ctrs"):
        if info.get(field):
            raise ValueError("Unsupported categorical/CTR/text/embedding features: " + field)
    if model_json.get("ctr_data"):
        raise ValueError("Categorical CTR data is unsupported")
    features = info.get("float_features")
    if not isinstance(features, list) or len(features) != len(schema.names):
        raise ValueError("Model feature count differs from features.json")
    index_to_flat, flat_indices = {}, set()
    for feature in features:
        if not isinstance(feature, dict):
            raise ValueError("Float feature metadata must be an object")
        internal = _integer(feature.get("feature_index"), "feature_index", 0, len(features) - 1)
        flat = _integer(feature.get("flat_feature_index"), "flat_feature_index", 0, len(features) - 1)
        if internal in index_to_flat or flat in flat_indices:
            raise ValueError("Duplicate internal/flat feature index")
        if feature.get("feature_id") != schema.names[flat]:
            raise ValueError("Model feature_id differs from the flat schema column")
        if feature.get("has_nans", False) is not False:
            raise ValueError("NaN-dependent feature routing is unsupported")
        borders = feature.get("borders")
        if not isinstance(borders, list):
            raise ValueError("Float feature borders must be an array")
        for border in borders:
            _float32(border, "feature border")
        index_to_flat[internal] = flat
        flat_indices.add(flat)
    trees = model_json.get("oblivious_trees")
    if not isinstance(trees, list):
        raise ValueError("CatBoost oblivious_trees must be an array")
    _integer(len(trees), "tree count", 1, MAX_TREES)
    scale_bias = model_json.get("scale_and_bias")
    if (not isinstance(scale_bias, list) or len(scale_bias) != 2
            or not isinstance(scale_bias[1], list) or len(scale_bias[1]) != 1):
        raise ValueError("Only single-output CatBoost scale_and_bias is supported")
    scale, bias = _finite(scale_bias[0], "scale"), _finite(scale_bias[1][0], "bias")
    output = bytearray(struct.pack("<4sHHIdd", b"AJCG", 1, len(features), len(trees), bias, scale))
    for tree in trees:
        if not isinstance(tree, dict) or not isinstance(tree.get("splits"), list):
            raise ValueError("Every oblivious tree must have a splits array")
        splits, leaves = tree["splits"], tree.get("leaf_values")
        depth = _integer(len(splits), "tree depth", 1, MAX_DEPTH)
        if not isinstance(leaves, list) or len(leaves) != 1 << depth:
            raise ValueError("Single-output tree leaf count must be 2^depth")
        output.extend(struct.pack("<B", depth))
        for split in splits:
            if not isinstance(split, dict) or split.get("split_type") != "FloatFeature":
                raise ValueError("Only numeric FloatFeature splits are supported")
            internal = _integer(split.get("float_feature_index"), "split feature index", 0, len(features) - 1)
            output.extend(struct.pack("<Hf", index_to_flat[internal], _float32(split.get("border"), "split border")))
        for leaf in leaves:
            output.extend(struct.pack("<f", _float32(leaf, "leaf value")))
    output.extend(struct.pack("<I", zlib.crc32(output) & 0xFFFFFFFF))
    return bytes(output)


def decode_model(data):
    """Strict standard-library decoder used by tooling and parity checks."""
    if not isinstance(data, (bytes, bytearray)) or not 32 <= len(data) <= MAX_MODEL_BYTES:
        raise ValueError("Invalid model.bin size")
    if struct.unpack_from("<I", data, len(data) - 4)[0] != zlib.crc32(data[:-4]) & 0xFFFFFFFF:
        raise ValueError("model.bin CRC32 mismatch")
    magic, version, feature_count, count, bias, scale = struct.unpack_from("<4sHHIdd", data)
    if magic != b"AJCG" or version != 1:
        raise ValueError("Unsupported model.bin magic/version")
    _integer(feature_count, "feature count", 1, MAX_FEATURES)
    _integer(count, "tree count", 1, MAX_TREES)
    _finite(bias, "bias")
    _finite(scale, "scale")
    offset, end, trees = 28, len(data) - 4, []
    for _ in range(count):
        if offset >= end:
            raise ValueError("Truncated model.bin tree")
        depth = _integer(data[offset], "tree depth", 1, MAX_DEPTH)
        offset += 1
        required = depth * 6 + (1 << depth) * 4
        if offset + required > end:
            raise ValueError("Truncated model.bin split/leaf array")
        features, borders = [], []
        for _ in range(depth):
            feature, border = struct.unpack_from("<Hf", data, offset)
            _integer(feature, "split feature index", 0, feature_count - 1)
            features.append(feature)
            borders.append(_finite(border, "split border"))
            offset += 6
        leaves = struct.unpack_from("<" + "f" * (1 << depth), data, offset)
        for leaf in leaves:
            _finite(leaf, "leaf value")
        offset += (1 << depth) * 4
        trees.append(BinaryTree(tuple(features), tuple(borders), leaves))
    if offset != end:
        raise ValueError("Trailing bytes after model.bin trees")
    return BinaryModel(feature_count, bias, scale, tuple(trees))


def score_model(model, features):
    """Match host FloatArray comparisons, bit order, and double accumulation."""
    if len(features) != model.feature_count:
        raise ValueError("Feature vector length differs from model")
    values = tuple(_float32(value, "input feature") for value in features)
    total = 0.0
    for tree in model.trees:
        leaf = 0
        for bit, (feature, border) in enumerate(zip(tree.features, tree.borders)):
            if values[feature] > border:
                leaf |= 1 << bit
        total += tree.leaves[leaf]
    result = model.bias + model.scale * total
    return _finite(result, "model score")


def _bounded_text(value, name, maximum=1024):
    if (not isinstance(value, str) or not value.strip() or len(value.encode("utf-16-le")) // 2 > maximum
            or any(ord(character) < 32 or 127 <= ord(character) <= 159 for character in value)):
        raise ValueError(f"{name} must be nonempty text of at most {maximum} characters without controls")
    return value


def _timestamp(value, name):
    _bounded_text(value, name, 64)
    if OFFSET_TIMESTAMP.fullmatch(value) is None:
        raise ValueError(name + " must be a calendar ISO-8601 timestamp with seconds and a UTC offset")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ValueError(name + " must be an ISO-8601 timestamp with a UTC offset") from error
    if parsed.tzinfo is None or abs(parsed.utcoffset().total_seconds()) > 18 * 3600:
        raise ValueError(name + " must be an ISO-8601 timestamp with a UTC offset")
    # datetime only checks the calendar/offset. Retain all nine fractional digits
    # for Java OffsetDateTime; do not rewrite from Python's microsecond precision.
    return value


def _read_bytes(path, maximum, description):
    path = Path(path)
    if not path.is_file() or path.stat().st_size > maximum:
        raise ValueError(description + " is missing or exceeds its size limit")
    with path.open("rb") as source:
        data = source.read(maximum + 1)
    if len(data) > maximum:
        raise ValueError(description + " exceeds its size limit")
    return data


def _json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n").encode("utf-8")


def _read_json(path, maximum=MAX_TEXT_BYTES):
    return parse_json(_read_bytes(path, maximum, Path(path).name).decode("utf-8"))


def _metric_summary(value, name):
    if not isinstance(value, dict):
        raise ValueError(name + " metrics are required")
    groups = _integer(value.get("groups"), name + ".groups", 0, 2**31 - 1)
    result = {"groups": groups}
    for field in ("top1Survival", "ruleTop1Survival", "regret"):
        metric = value.get(field)
        if groups == 0:
            if metric is not None:
                raise ValueError("Empty metric groups must have null metrics")
        elif not 0 <= _finite(metric, name + "." + field) <= 1:
            raise ValueError("Survival/regret metrics must be within [0, 1]")
        result[field] = metric
    return result


def _manifest(binary, schema, dataset, training, metrics, *, model_id, version_code, version_name,
              display_name, min_host_version_code, spdx, publisher):
    if not isinstance(model_id, str) or MODEL_ID.fullmatch(model_id) is None:
        raise ValueError("modelId must match " + MODEL_ID.pattern)
    if not isinstance(display_name, dict) or "en" not in display_name or not 1 <= len(display_name) <= 64:
        raise ValueError("displayName must map languages to names and contain en")
    for language, name in display_name.items():
        if not isinstance(language, str) or not re.fullmatch(r"[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*", language):
            raise ValueError("Invalid displayName language tag")
        _bounded_text(name, "displayName", 256)
    parsed = decode_model(binary)
    commit = training.get("exportGeneratorCommit")
    if not isinstance(commit, str) or re.fullmatch(r"[0-9a-f]{40}", commit) is None:
        raise ValueError("Training exportGeneratorCommit must be a full lowercase Git commit")
    versions = training.get("versions", {})
    summary = {name: _metric_summary(metrics.get(name), name) for name in ("test", "realTest")}
    return {
        "format": 1,
        "modelId": model_id,
        "displayName": display_name,
        "versionCode": _integer(version_code, "versionCode", 1, 2**31 - 1),
        "versionName": _bounded_text(version_name, "versionName", 256),
        "kind": "ranker",
        "algorithm": "catboost-oblivious",
        "featureSchemaVersion": schema.version,
        "featureNamesHash": schema.names_hash,
        "model": {"file": "model.bin", "size": len(binary), "sha256": hashlib.sha256(binary).hexdigest(),
                  "trees": len(parsed.trees), "depth": max(len(tree.features) for tree in parsed.trees),
                  "features": parsed.feature_count},
        "dataset": {"datasetId": _bounded_text(dataset.get("datasetId"), "datasetId"),
                    "groups": _integer(training.get("groups", {}).get("train"), "training groups", 1, 2**31 - 1)
                    + _integer(training.get("groups", {}).get("real-train", 0), "real-train groups", 0, 2**31 - 1),
                    "createdAt": _timestamp(dataset.get("generatedAt"), "dataset.createdAt")},
        "metrics": summary,
        "training": {"catboostVersion": _bounded_text(versions.get("catboost"), "CatBoost version"),
                     "pythonVersion": _bounded_text(versions.get("python"), "Python version"),
                     "hostCommit": commit,
                     "trainedAt": _timestamp(training.get("completedAt"), "training.trainedAt")},
        "minHostVersionCode": _integer(min_host_version_code, "minHostVersionCode", 1, 2**31 - 1),
        "license": _bounded_text(spdx, "license SPDX expression", 256),
        "publisher": _bounded_text(publisher, "publisher", 256),
    }


def _provenance(model_json_path, features_json_path, dataset_manifest_path, dataset, training, metrics, schema, binary):
    for value, name in ((dataset, "dataset manifest"), (training, "training log"), (metrics, "evaluation metrics")):
        if not isinstance(value, dict) or type(value.get("format")) is not int or value["format"] != 1:
            raise ValueError("Unsupported " + name)
    if dataset.get("status") != "complete":
        raise ValueError("Dataset manifest must be complete")
    for value, fields in ((dataset, ("splits",)),
                          (training, ("inputSha256", "artifacts", "groups", "versions")),
                          (metrics, ("datasetHashes", "test", "realTest"))):
        if any(not isinstance(value.get(field), dict) for field in fields):
            raise ValueError("Invalid nested provenance metadata")
    if any(not isinstance(dataset["splits"].get(split), dict) for split in ("train", "valid", "test", "real-test")):
        raise ValueError("Dataset must describe all four splits")
    if any(not isinstance(artifact, dict) for artifact in training["artifacts"].values()):
        raise ValueError("Training artifacts must be metadata objects")
    for artifact in training["artifacts"].values():
        if not isinstance(artifact.get("sha256"), str) or HEX_SHA256.fullmatch(artifact["sha256"]) is None:
            raise ValueError("Training artifact SHA-256 must contain 64 lowercase hexadecimal characters")
    if any(value.get("datasetId") != dataset.get("datasetId") for value in (training, metrics)):
        raise ValueError("Model, training, and metrics datasetId must agree")
    model_hash, schema_hash, dataset_hash = (sha256(path) for path in (model_json_path, features_json_path, dataset_manifest_path))
    if training.get("inputSha256", {}).get("manifest") != dataset_hash:
        raise ValueError("Training is not bound to this dataset manifest")
    if training.get("inputSha256", {}).get("schema") != schema_hash:
        raise ValueError("Training is not bound to these feature schema bytes")
    artifacts = training.get("artifacts", {})
    json_hashes = {artifacts.get(name, {}).get("sha256") for name in ("model.json", "model.raw.json")}
    model_hashes = json_hashes | {artifacts.get("model.cbm", {}).get("sha256")}
    if model_hash not in json_hashes or metrics.get("modelSha256") not in model_hashes - {None}:
        raise ValueError("Model or metrics are not bound to the recorded training artifacts")
    for value, hash_key in ((training, "namesHash"), (metrics, "featureNamesHash")):
        if value.get("featureSchemaVersion") != schema.version or value.get(hash_key) != schema.names_hash:
            raise ValueError("Training/evaluation feature schema mismatch")
    if metrics.get("datasetHashes", {}).get("manifest") != dataset_hash:
        raise ValueError("Metrics are not bound to this dataset manifest")
    if metrics.get("datasetHashes", {}).get("schema") != schema_hash:
        raise ValueError("Metrics are not bound to these feature schema bytes")
    for split, metric in (("test", "test"), ("real-test", "realTest")):
        stats = dataset.get("splits", {}).get(split, {})
        groups = _integer(stats.get("groups"), split + " dataset groups", 0, 2**31 - 1)
        empty = _integer(stats.get("groupsWithoutCandidates"), split + " empty groups", 0, groups)
        if metrics.get(metric, {}).get("groups") != groups - empty:
            raise ValueError("Metrics do not cover the dataset split: " + split)
    for split in ("train", "valid"):
        stats = dataset.get("splits", {}).get(split, {})
        groups = _integer(stats.get("groups"), split + " dataset groups", 1, 2**31 - 1)
        empty = _integer(stats.get("groupsWithoutCandidates"), split + " empty groups", 0, groups)
        if training.get("groups", {}).get(split) != groups - empty:
            raise ValueError("Training group count differs from the dataset: " + split)
    real_stats = dataset.get("splits", {}).get("real-train") or {"groups": 0, "groupsWithoutCandidates": 0}
    real_groups = _integer(real_stats.get("groups"), "real-train dataset groups", 0, 2**31 - 1)
    real_empty = _integer(real_stats.get("groupsWithoutCandidates"), "real-train empty groups", 0, real_groups)
    if training.get("groups", {}).get("real-train", 0) != real_groups - real_empty:
        raise ValueError("Training group count differs from the dataset: real-train")
    parsed = decode_model(binary)
    if (_integer(training.get("treeCount"), "training tree count", 1, MAX_TREES) != len(parsed.trees)
            or _integer(training.get("maximumTreeDepth"), "training maximum depth", 1, MAX_DEPTH)
            != max(len(tree.features) for tree in parsed.trees)):
        raise ValueError("Model tree dimensions differ from the training log")


def model_card(manifest, real_train=(0, 0)):
    """Aggregate facts only; no selectors, group IDs, or snapshots enter the card.
    [real_train] is the (groups, apps) count of real applications used in fitting (roadmap D25)."""
    def plain(value):
        return str(value).replace("\\", "\\\\").replace("`", "\\`").replace("|", "\\|")

    rows = []
    for split, value in manifest["metrics"].items():
        values = [str(value["groups"])] + ["not measured" if value[field] is None else f"{value[field]:.10f}"
                                             for field in ("top1Survival", "ruleTop1Survival", "regret")]
        rows.append("| " + split + " | " + " | ".join(values) + " |")
    return ("# " + plain(manifest["displayName"]["en"]) + "\n\n"
            + f"Model `{plain(manifest['modelId'])}`, version `{plain(manifest['versionName'])}` "
              f"({manifest['versionCode']}). Publisher: {plain(manifest['publisher'])}.\n\n"
            + f"License expression: `{plain(manifest['license'])}`. The accompanying LICENSE contains the supplied terms.\n\n"
            + "## Purpose and inputs\n\n"
            + "Ranks candidate selector plans that the host has already verified against the captured layout. "
              "It does not generate script text or bypass target validation. Inputs are the numeric Kotlin feature schema; "
              "the package contains no layout snapshots or captured text.\n\n"
            + "## Training\n\n"
            + f"Dataset `{plain(manifest['dataset']['datasetId'])}`; {manifest['dataset']['groups']} training task groups"
              + (f", of which {real_train[0]} come from {real_train[1]} real applications assigned to real-train. "
                 "Real-test applications are disjoint from them and never enter training. " if real_train[0] else ". ")
            + f"Dataset created at {plain(manifest['dataset']['createdAt'])}.\n\n"
            + f"CatBoost {plain(manifest['training']['catboostVersion'])}, Python {plain(manifest['training']['pythonVersion'])}; "
              f"training completed at {plain(manifest['training']['trainedAt'])}. "
              f"Host source commit `{manifest['training']['hostCommit']}`.\n\n"
            + f"{manifest['model']['trees']} symmetric trees, maximum depth {manifest['model']['depth']}, "
              f"{manifest['model']['features']} numeric features. Schema {manifest['featureSchemaVersion']} "
              f"(`{manifest['featureNamesHash']}`).\n\n"
            + "## Held-out evaluation\n\n"
            + "| Split | Groups | Model top-1 survival | Rule top-1 survival | Regret |\n"
              "| --- | ---: | ---: | ---: | ---: |\n" + "\n".join(rows) + "\n\n"
            + "Survival measures success in the sampled later interface states. Regret is the best observed candidate "
              "survival minus the selected candidate's survival, averaged by task group.\n\n"
            + "## Known limits\n\n"
            + "Controlled fixture coverage does not establish performance on unseen applications, devices, or future updates. "
              "A realTest count of zero means real application performance has not been measured. "
              "Finite sampled changes cannot prove that a selector will remain valid in every future state.\n\n"
            + "Split thresholds and leaves are stored as float32; desktop/host parity must be checked before release. "
              "Packaging alone does not assert release-gate approval or verified official provenance.\n")


def _real_train(dataset, training):
    groups = training.get("groups", {}).get("real-train", 0)
    apps = (dataset.get("splits", {}).get("real-train") or {}).get("apps") or {}
    return groups, len(apps)


def _archive(entries):
    if tuple(entries) != ZIP_NAMES:
        raise ValueError("Package entries must use the fixed five-entry order")
    if sum(len(data) for data in entries.values()) > MAX_TOTAL_BYTES:
        raise ValueError("Package exceeds the 100 MiB uncompressed size limit")
    for name, data in entries.items():
        limit = MAX_MODEL_BYTES if name == "model.bin" else MAX_SCHEMA_BYTES if name == "features.json" else MAX_TEXT_BYTES
        if not data or len(data) > limit:
            raise ValueError("Empty or oversized package entry: " + name)
    buffer = io.BytesIO()
    # STORED is deterministic across zlib versions and always has ratio 1.
    with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_STORED, allowZip64=False) as archive:
        for name, data in entries.items():
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_STORED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            info.internal_attr = 0
            archive.writestr(info, data)
    return buffer.getvalue()


def build_package(model_json_path, features_json_path, dataset_manifest_path, train_log_path, metrics_path, license_file,
                  *, spdx, publisher, model_id, version_code, version_name, display_name, min_host_version_code, output):
    """Build locally with explicit license terms; do not publish or assign official trust."""
    output = Path(output).resolve()
    if output.suffix.lower() != ".ajcgm" or output.exists():
        raise ValueError("Output must be a new .ajcgm file")
    schema_bytes = _read_bytes(features_json_path, MAX_SCHEMA_BYTES, "features.json")
    parse_json(schema_bytes.decode("utf-8"))  # Strict UTF-8/JSON; package readers need no BOM compatibility mode.
    schema = read_schema(features_json_path)
    model_json = _read_json(model_json_path, MAX_TOTAL_BYTES)
    binary = encode_model(model_json, schema)
    dataset, training, metrics = (_read_json(path) for path in (dataset_manifest_path, train_log_path, metrics_path))
    _provenance(model_json_path, features_json_path, dataset_manifest_path, dataset, training, metrics, schema, binary)
    manifest = _manifest(binary, schema, dataset, training, metrics, model_id=model_id, version_code=version_code,
                         version_name=version_name, display_name=display_name, min_host_version_code=min_host_version_code,
                         spdx=spdx, publisher=publisher)
    license_bytes = _read_bytes(license_file, MAX_TEXT_BYTES, "LICENSE")
    if not license_bytes.decode("utf-8").strip():
        raise ValueError("Explicit license text must not be empty")
    entries = {"manifest.json": _json_bytes(manifest), "model.bin": binary, "features.json": schema_bytes,
               "MODEL_CARD.md": model_card(manifest, _real_train(dataset, training)).encode("utf-8"), "LICENSE": license_bytes}
    package = _archive(entries)
    output.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive creation preserves an existing package even if another process
    # creates it while validation is running. Never replace versioned artifacts.
    with output.open("xb") as stream:
        stream.write(package)
    return manifest


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ("model-json", "features-json", "dataset-manifest", "train-log", "metrics", "license-file", "output"):
        parser.add_argument("--" + flag, required=True, type=Path)
    for flag in ("spdx", "publisher", "model-id", "version-name"):
        parser.add_argument("--" + flag, required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--min-host-version-code", required=True, type=int)
    parser.add_argument("--display-name", required=True, action="append", help="LANGUAGE=TEXT; repeatable, en required")
    args = parser.parse_args(argv)
    try:
        display_name = {}
        for value in args.display_name:
            language, separator, name = value.partition("=")
            if not separator or language in display_name:
                raise ValueError("Display names must use distinct LANGUAGE=TEXT entries")
            display_name[language] = name
        manifest = build_package(args.model_json, args.features_json, args.dataset_manifest, args.train_log,
                                 args.metrics, args.license_file, spdx=args.spdx, publisher=args.publisher,
                                 model_id=args.model_id, version_code=args.version_code, version_name=args.version_name,
                                 display_name=display_name, min_host_version_code=args.min_host_version_code, output=args.output)
    except (ValueError, OSError, UnicodeError) as error:
        parser.exit(2, str(error) + "\n")
    print(json.dumps({"modelId": manifest["modelId"], "versionCode": manifest["versionCode"],
                      "packageSha256": sha256(args.output), "packageBytes": args.output.stat().st_size,
                      "modelBytes": manifest["model"]["size"]}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
