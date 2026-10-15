"""Read checked Kotlin state/action vectors without deriving any model features."""

from array import array
from collections.abc import Sequence
from dataclasses import dataclass
import csv
import hashlib
import math
from pathlib import Path
import re
import struct

from dataset.task_io import nonempty_string, private_path, read_json, require_fields
from train.data import sha256


SPLITS = ("train", "valid", "test", "real-test", "real-train")
# Real applications are split by app (roadmap D25): real-train may join fitting, real-test only evaluates.
REAL_SPLITS = ("real-train", "real-test")
PREFIX = ("split", "groupId", "stateKey", "actionKey", "label", "observations")
# Format 2 adds the best observed continuation beside the mean; features follow unchanged.
PREFIX_V2 = ("split", "groupId", "stateKey", "actionKey", "label", "labelBest", "observations")
PREFIXES = {1: PREFIX, 2: PREFIX_V2}
FILES = ("features.json", "actions.csv", "export-manifest.json")


@dataclass(frozen=True)
class FeatureSchema:
    grammar_version: int
    version: int
    names: tuple[str, ...]
    names_hash: str


@dataclass(frozen=True, slots=True)
class ActionRow:
    split: str
    group_id: str
    state_key: str
    action_key: str
    label: float
    observations: int
    values: Sequence[float]
    # Maximum survival among the completed observed rollouts through this edge; None in format 1.
    label_best: float | None = None


@dataclass(frozen=True)
class Dataset:
    root: Path
    schema: FeatureSchema
    manifest: dict
    groups: dict[str, dict]
    rows: tuple[ActionRow, ...]
    hashes: dict[str, str]

    @property
    def export_format(self):
        return self.manifest["format"]

    def rows_for(self, *splits):
        if not set(splits) <= set(SPLITS):
            raise ValueError("Unknown requested split")
        return tuple(row for row in self.rows if not splits or row.split in splits)

    def check_unchanged(self):
        for name, digest in self.hashes.items():
            if sha256(self.root / name) != digest:
                raise ValueError("Construction dataset changed during processing: " + name)


def read_schema(path):
    value = read_json(path)
    require_fields(value, ("format", "role", "grammarVersion", "featureSchemaVersion", "names", "namesHash"),
                   description="construction feature schema")
    if any(type(value[field]) is not int or value[field] != 1
           for field in ("format", "grammarVersion", "featureSchemaVersion")):
        raise ValueError("Unsupported construction feature schema/grammar version")
    if value["role"] != "construction-state-action":
        raise ValueError("Feature role must be construction-state-action")
    names = value["names"]
    if (not isinstance(names, list) or not 1 <= len(names) <= 65535
            or any(not isinstance(name, str) or not re.fullmatch(r"[a-z][a-z0-9_]*", name) for name in names)
            or len(set(names)) != len(names) or set(names) & set(PREFIX)):
        raise ValueError("Feature names must be distinct numeric column identifiers")
    expected = hashlib.sha256("\n".join(names).encode("utf-8")).hexdigest()[:16]
    if value["namesHash"] != expected:
        raise ValueError("Feature namesHash differs from the ordered names")
    return FeatureSchema(value["grammarVersion"], value["featureSchemaVersion"], tuple(names), expected)


def _number(text, name):
    try:
        value = float(text)
    except (ValueError, TypeError) as error:
        raise ValueError(name + " must be numeric") from error
    if not math.isfinite(value):
        raise ValueError(name + " must be finite")
    return value


def float32(value):
    """Wire conversion only: CSV represents Kotlin FloatArray values, not new features."""
    if type(value) not in (int, float) or not math.isfinite(value):
        raise ValueError("Feature must be finite")
    try:
        result = struct.unpack("<f", struct.pack("<f", value))[0]
    except (OverflowError, struct.error) as error:
        raise ValueError("Feature exceeds finite Float32 range") from error
    if not math.isfinite(result):
        raise ValueError("Feature exceeds finite Float32 range")
    return result


def _groups(manifest):
    if not isinstance(manifest.get("groups"), list):
        raise ValueError("Construction manifest groups must be an array")
    groups, families = {}, {}
    for group in manifest["groups"]:
        if not isinstance(group, dict):
            raise ValueError("Group metadata must be objects")
        group_id = nonempty_string(group.get("groupId"), "groupId")
        family = nonempty_string(group.get("screenFamily"), "screenFamily")
        split, source = group.get("split"), group.get("source")
        if group_id in groups:
            raise ValueError("Duplicate groupId in the construction manifest")
        if split not in SPLITS:
            raise ValueError("Unknown split in the construction manifest")
        if source == "real":
            if split not in REAL_SPLITS:
                raise ValueError("Real application data must only be in real-train or real-test")
        elif source == "fixture":
            if split in REAL_SPLITS:
                raise ValueError("Fixture data cannot be in real-train or real-test")
            # Real screen family names are free text and may coincide with fixture families.
            families.setdefault(family, set()).add(split)
        else:
            raise ValueError("Unknown construction data source")
        groups[group_id] = group
    if any(len(splits) != 1 for splits in families.values()):
        raise ValueError("Screen family leaked across splits")
    return groups


def read_rows(path, schema, groups, export_format=1):
    prefix = PREFIXES.get(export_format)
    if prefix is None:
        raise ValueError("Unsupported construction export format")
    rows, seen = [], set()
    with Path(path).open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.reader(stream)
        if next(reader, None) != [*prefix, *schema.names]:
            raise ValueError("actions.csv columns differ from the ordered construction schema")
        for line, columns in enumerate(reader, 2):
            if len(columns) != len(prefix) + len(schema.names):
                raise ValueError(f"Invalid action column count at line {line}")
            if export_format == 1:
                split, group_id, state, action, label_text, observations_text = columns[:len(prefix)]
                best_text = None
            else:
                split, group_id, state, action, label_text, best_text, observations_text = columns[:len(prefix)]
            for value, name in ((group_id, "groupId"), (state, "stateKey"), (action, "actionKey")):
                nonempty_string(value, name)
            if group_id not in groups or groups[group_id]["split"] != split:
                raise ValueError("Action group/split differs from the construction manifest")
            key = (group_id, state, action)
            if key in seen:
                raise ValueError("Duplicate groupId/stateKey/actionKey in actions.csv")
            seen.add(key)
            label = _number(label_text, "label")
            if not 0 <= label <= 1:
                raise ValueError("Observed return labels must be between 0 and 1")
            label_best = None
            if best_text is not None:
                label_best = _number(best_text, "labelBest")
                if not 0 <= label_best <= 1:
                    raise ValueError("Best observed continuation labels must be between 0 and 1")
                if label_best < label:
                    raise ValueError("labelBest cannot be below the mean observed return")
            if (not re.fullmatch(r"[1-9][0-9]*", observations_text)
                    or len(observations_text) > 10 or int(observations_text) > 2**31 - 1):
                raise ValueError("observations must be a positive 32-bit integer; unobserved actions have no row")
            # Each export may contain millions of rows. Keep the original
            # Float32 payload compact instead of allocating a Python float
            # object and tuple reference for every feature of every action.
            values = array("f", (float32(_number(value, name))
                                 for value, name in zip(columns[len(prefix):], schema.names)))
            rows.append(ActionRow(split, group_id, state, action, label, int(observations_text), values, label_best))
    return tuple(rows)


def read_dataset(directory):
    root = Path(directory).resolve()
    for name in FILES:
        path = (root / name).resolve()
        if not path.is_relative_to(root) or not path.is_file():
            raise ValueError("Missing construction export or file escapes its directory: " + name)
    hashes = {name: sha256(root / name) for name in FILES}
    manifest = read_json(root / "export-manifest.json")
    if not isinstance(manifest, dict) or type(manifest.get("format")) is not int or manifest["format"] not in PREFIXES:
        raise ValueError("Unsupported construction export manifest format")
    nonempty_string(manifest.get("datasetId"), "datasetId")
    for field, name in (("featuresSha256", "features.json"), ("actionsSha256", "actions.csv")):
        if manifest.get(field) != hashes[name]:
            raise ValueError("Construction export SHA-256 mismatch: " + field)
    schema, groups = read_schema(root / "features.json"), _groups(manifest)
    if any(group["source"] == "real" for group in groups.values()):
        private_path(root)
    rows = read_rows(root / "actions.csv", schema, groups, manifest["format"])
    dataset = Dataset(root, schema, manifest, groups, rows, hashes)
    dataset.check_unchanged()
    return dataset
