#!/usr/bin/env python3
"""Export actual CatBoost scores and whole candidate groups for Kotlin package parity."""

import argparse
from collections import defaultdict
from dataclasses import replace
import hashlib
import json
import math
from pathlib import Path
import shutil
import struct
import sys
import tempfile
import zipfile

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from dataset.task_io import inherit_parent_acl, parse_json, private_path, write_json
from package.build_package import decode_model, score_model
from train.data import rank_indices, read_dataset, sha256
from train.evaluate import predict


SCORE_TOLERANCE = 1e-4
ENTRIES = {"manifest.json", "model.bin", "features.json", "MODEL_CARD.md", "LICENSE"}


def float32(value):
    """Wire conversion only, matching the original Kotlin FloatArray and CatBoost Pool."""
    if type(value) not in (int, float) or not math.isfinite(value):
        raise ValueError("Parity feature must be finite")
    try:
        result = struct.unpack("<f", struct.pack("<f", value))[0]
    except (OverflowError, struct.error) as error:
        raise ValueError("Parity feature exceeds Float32") from error
    if not math.isfinite(result):
        raise ValueError("Parity feature exceeds Float32")
    return result


def select_prefix(rows, requested):
    """Keep the first requested rows, completing the final group for a truthful top-1 check."""
    if type(requested) is not int or requested <= 0:
        raise ValueError("max-candidates must be positive")
    if not rows:
        raise ValueError("No test candidates are available")
    end = min(requested, len(rows))
    while end < len(rows) and rows[end].group_id == rows[end - 1].group_id:
        end += 1
    return tuple(replace(row, values=tuple(float32(value) for value in row.values)) for row in rows[:end])


def parity_records(rows, reference_scores, binary_scores, schema):
    if len(rows) != len(reference_scores) or len(rows) != len(binary_scores):
        raise ValueError("Every candidate requires a reference and binary score")
    if any(not math.isfinite(value) for value in (*reference_scores, *binary_scores)):
        raise ValueError("Parity scores must be finite")
    maximum_error = max((abs(left - right) for left, right in zip(reference_scores, binary_scores)), default=0.0)
    if maximum_error > SCORE_TOLERANCE:
        raise ValueError(f"Float32 binary scores differ from CatBoost by {maximum_error}, above {SCORE_TOLERANCE}")
    groups = defaultdict(list)
    seen = set()
    for index, row in enumerate(rows):
        key = (row.group_id, row.plan_key)
        if key in seen:
            raise ValueError("Duplicate parity candidate")
        seen.add(key)
        groups[row.group_id].append(index)
    rank_column = schema.names.index("rule_rank_norm")
    records = []
    for indices in groups.values():
        group = [rows[index] for index in indices]
        expected_order = rank_indices(group, [reference_scores[index] for index in indices], schema)
        binary_order = rank_indices(group, [binary_scores[index] for index in indices], schema)
        if expected_order[0] != binary_order[0]:
            raise ValueError("Float32 binary model changes the top choice for " + group[0].group_id)
        rule_order = sorted(range(len(group)), key=lambda index: group[index].values[rank_column])
        rule_rank = {index: rank for rank, index in enumerate(rule_order)}
        for local, index in enumerate(indices):
            row = rows[index]
            records.append({"format": 1, "groupId": row.group_id, "planKey": row.plan_key,
                            "ruleRank": rule_rank[local], "groupSize": len(indices),
                            "features": list(row.values), "expectedScore": reference_scores[index],
                            "expectedTop": local == expected_order[0]})
    return records, {"candidates": len(records), "groups": len(groups), "maxBinaryScoreDifference": maximum_error,
                     "sameTopChoice": True, "scoreTolerance": SCORE_TOLERANCE}


def read_package(path, schema):
    """Read bounded package contents for the desktop check; Android revalidates independently."""
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        if len(entries) != len(ENTRIES) or {entry.filename for entry in entries} != ENTRIES:
            raise ValueError("Package must contain exactly the five model entries")
        if sum(entry.file_size for entry in entries) > 100 * 1024 * 1024:
            raise ValueError("Package is too large")
        for entry in entries:
            limit = 5 * 1024 * 1024 if entry.filename == "model.bin" else 256 * 1024 if entry.filename == "features.json" else 1024 * 1024
            if entry.file_size > limit or entry.file_size > 200 * max(1, entry.compress_size):
                raise ValueError("Package entry is too large or too highly compressed")
        # Reading every bounded entry also checks zip CRCs for documentation/license files.
        contents = {entry.filename: archive.read(entry) for entry in entries}
    manifest = parse_json(contents["manifest.json"].decode("utf-8"))
    feature_info = parse_json(contents["features.json"].decode("utf-8"))
    expected_schema = {"format": 1, "featureSchemaVersion": schema.version,
                       "names": list(schema.names), "namesHash": schema.names_hash}
    if feature_info != expected_schema:
        raise ValueError("Package feature schema differs from the dataset")
    if (manifest.get("format") != 1 or manifest.get("featureSchemaVersion") != schema.version
            or manifest.get("featureNamesHash") != schema.names_hash):
        raise ValueError("Package manifest schema differs from the dataset")
    binary = contents["model.bin"]
    if manifest.get("model", {}).get("sha256") != hashlib.sha256(binary).hexdigest():
        raise ValueError("Package binary hash mismatch")
    model = decode_model(binary)
    if model.feature_count != len(schema.names):
        raise ValueError("Package binary feature count differs from the dataset")
    return model, manifest


def export_parity(dataset_dir, reference_model, package_file, output, *, max_candidates=5000):
    dataset = read_dataset(dataset_dir)
    reference_model, package_file, output = (Path(path).resolve() for path in (reference_model, package_file, output))
    if output.exists():
        raise ValueError("Parity output already exists; choose a new directory")
    if output.is_relative_to(dataset.root):
        raise ValueError("Parity output must be outside the frozen dataset")
    if any(group["source"] == "real" for group in dataset.groups.values()):
        private_path(output)
    rows = select_prefix(dataset.rows_for("test"), max_candidates)
    reference_hash, package_hash = sha256(reference_model), sha256(package_file)
    model, package_manifest = read_package(package_file, dataset.schema)
    expected = predict(reference_model, rows, dataset.schema)
    binary = [score_model(model, row.values) for row in rows]
    records, summary = parity_records(rows, expected, binary, dataset.schema)
    for name in ("features", "labels", "rules", "splitMap", "featureSchema", "export"):
        if sha256(dataset.artifact_path(name)) != dataset.hashes[name]:
            raise ValueError("Dataset changed during parity export: " + name)
    if sha256(dataset.root / "dataset-manifest.json") != dataset.hashes["manifest"]:
        raise ValueError("Dataset manifest changed during parity export")
    if sha256(reference_model) != reference_hash or sha256(package_file) != package_hash:
        raise ValueError("Reference model or package changed during parity export")
    output.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=".codegen-parity-", dir=output.parent))
    try:
        # Before anything is written, so the renamed output gets the access rights of its parent directory.
        inherit_parent_acl(staging)
        with (staging / "parity.jsonl").open("w", encoding="utf-8", newline="\n") as target:
            for record in records:
                target.write(json.dumps(record, ensure_ascii=False, allow_nan=False, separators=(",", ":")) + "\n")
        shutil.copyfile(package_file, staging / package_file.name)
        if sha256(staging / package_file.name) != package_hash:
            raise ValueError("Package changed while being copied")
        manifest = {"format": 1, "tool": "export_parity", "datasetId": dataset.manifest["datasetId"],
                    "modelId": package_manifest["modelId"], "packageFile": package_file.name,
                    "packageSha256": package_hash, "referenceModelSha256": reference_hash,
                    "paritySha256": sha256(staging / "parity.jsonl"), "featuresSha256": dataset.hashes["features"],
                    "featureSchemaVersion": dataset.schema.version, "featureNamesHash": dataset.schema.names_hash,
                    "requestedCandidates": max_candidates, **summary,
                    "note": "The first requested candidates plus the rest of the final group; every top-choice check uses a complete group."}
        write_json(staging / "parity-manifest.json", manifest)
        staging.rename(output)
        return manifest
    finally:
        if staging.exists():
            if staging.resolve().parent != output.parent:
                raise RuntimeError("Unexpected parity staging path")
            shutil.rmtree(staging)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--reference-model", type=Path, required=True, help="CatBoost .cbm or original/canonical .json")
    parser.add_argument("--package", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--max-candidates", type=int, default=5000)
    args = parser.parse_args(argv)
    try:
        result = export_parity(args.dataset, args.reference_model, args.package, args.output, max_candidates=args.max_candidates)
    except (ValueError, OSError, ImportError, zipfile.BadZipFile) as error:
        parser.exit(2, str(error) + "\n")
    print(json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
