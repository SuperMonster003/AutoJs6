"""Validated numeric exports shared by training and offline evaluation.

Features are produced only by the Kotlin exporter. This module never derives
features from snapshots, labels, or future states.
"""

from dataclasses import dataclass
import csv
import hashlib
import math
from pathlib import Path
import re

from dataset.task_io import nonempty_string, parse_json, private_path, read_json, require_fields


SPLITS = ("train", "valid", "test", "real-test")
PREFIX = ("split", "groupId", "planKey", "label")
# Kotlin exporters whose provenance is accepted: the original rule-search candidate export and the
# merged rule + construction candidate export of the construction experiments (same features/labels).
EXPORT_TOOLS = ("CodeGenDatasetExportTask", "ConstructionMergedExportTask")


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


@dataclass(frozen=True)
class FeatureSchema:
    version: int
    names: tuple[str, ...]
    names_hash: str


@dataclass(frozen=True)
class CandidateRow:
    split: str
    group_id: str
    plan_key: str
    survival: float | None
    values: tuple[float, ...]


@dataclass(frozen=True)
class Dataset:
    root: Path
    schema: FeatureSchema
    manifest: dict
    split_map: dict
    groups: dict[str, dict]
    rows: tuple[CandidateRow, ...]
    hashes: dict[str, str]

    def rows_for(self, *splits):
        if not set(splits) <= set(SPLITS):
            raise ValueError("Unknown requested split")
        return tuple(row for row in self.rows if not splits or row.split in splits)

    def grouped_rows(self, *splits):
        result = {}
        for row in self.rows_for(*splits):
            result.setdefault(row.group_id, []).append(row)
        return {key: tuple(rows) for key, rows in result.items()}

    def artifact_path(self, name):
        return artifact_path(self.root, self.manifest, name)


def read_schema(path):
    schema = read_json(path)
    require_fields(schema, ("format", "featureSchemaVersion", "names", "namesHash"),
                   description="feature schema")
    if type(schema["format"]) is not int or schema["format"] != 1:
        raise ValueError("Unsupported feature schema format")
    if type(schema["featureSchemaVersion"]) is not int or schema["featureSchemaVersion"] != 1:
        raise ValueError("Unsupported featureSchemaVersion")
    names = schema["names"]
    if (not isinstance(names, list) or not names
            or any(not isinstance(name, str) or not re.fullmatch(r"[a-z][a-z0-9_]*", name) for name in names)
            or len(set(names)) != len(names) or set(names) & set(PREFIX)):
        raise ValueError("Feature names must be distinct nonempty numeric column identifiers")
    expected = hashlib.sha256("\n".join(names).encode("utf-8")).hexdigest()[:16]
    if schema["namesHash"] != expected:
        raise ValueError("Feature schema namesHash does not match its ordered names")
    if "rule_rank_norm" not in names:
        raise ValueError("Feature schema is missing rule_rank_norm")
    return FeatureSchema(schema["featureSchemaVersion"], tuple(names), expected)


def _number(text, name):
    try:
        value = float(text)
    except (ValueError, TypeError) as error:
        raise ValueError(name + " must be numeric") from error
    if not math.isfinite(value):
        raise ValueError(name + " must be finite")
    return value


def read_feature_rows(path, schema, assignment=None, allow_unlabelled=False):
    """Check the full export, including unselected splits, before fitting a model.

    Empty labels are accepted only for the separate active-learning queue. Rows
    from each group must be contiguous as required by CatBoost ranking pools.
    """
    rows, pairs, finished, previous = [], set(), set(), None
    with Path(path).open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.reader(stream)
        if next(reader, None) != [*PREFIX, *schema.names]:
            raise ValueError("features.csv columns differ from the ordered feature schema")
        for line, columns in enumerate(reader, 2):
            if len(columns) != len(PREFIX) + len(schema.names):
                raise ValueError(f"Invalid feature column count at line {line}")
            split, group_id, plan_key, label = columns[:4]
            nonempty_string(group_id, "groupId")
            nonempty_string(plan_key, "planKey")
            if split not in SPLITS:
                raise ValueError(f"Unknown split at line {line}: {split}")
            if assignment is not None and assignment.get(group_id) != split:
                raise ValueError("Feature group/split differs from split-map.json")
            pair = (group_id, plan_key)
            if pair in pairs:
                raise ValueError("Duplicate groupId/planKey in features.csv")
            pairs.add(pair)
            if previous != group_id:
                if group_id in finished:
                    raise ValueError("Feature rows for a group must be contiguous")
                if previous is not None:
                    finished.add(previous)
                previous = group_id
            if label == "" and allow_unlabelled:
                survival = None
            else:
                survival = _number(label, "label")
                if not 0 <= survival <= 1:
                    raise ValueError("Survival labels must be between 0 and 1")
            values = tuple(_number(value, name) for value, name in zip(columns[4:], schema.names))
            # Kotlin exports FloatArray. Reject doubles which would overflow a
            # float32 Pool even though they are finite as Python doubles.
            if any(abs(value) > 3.4028234663852886e38 for value in values):
                raise ValueError("Feature exceeds the finite Kotlin Float range")
            rows.append(CandidateRow(split, group_id, plan_key, survival, values))
    by_group = {}
    for row in rows:
        by_group.setdefault(row.group_id, []).append(row)
    for group_rows in by_group.values():
        _rule_ranks(group_rows, schema)
        if len({row.split for row in group_rows}) != 1:
            raise ValueError("A group cannot appear in multiple splits")
    return tuple(rows)


def artifact_path(root, manifest, name):
    artifact = manifest.get("artifacts", {}).get(name)
    if not isinstance(artifact, dict) or artifact.get("status") != "complete":
        raise ValueError("Dataset artifact is not complete: " + name)
    filename = artifact.get("file")
    if (not isinstance(filename, str) or not filename or filename in (".", "..")
            or "/" in filename or "\\" in filename or ":" in filename):
        raise ValueError("Dataset artifact must be a file directly inside the dataset: " + name)
    root = Path(root).resolve()
    path = (root / filename).resolve()
    if not path.is_relative_to(root) or not path.is_file():
        raise ValueError("Dataset artifact is missing or escapes its directory: " + name)
    return path


def _checked_artifact(root, manifest, name):
    path = artifact_path(root, manifest, name)
    digest = sha256(path)
    if digest != manifest["artifacts"][name].get("sha256"):
        raise ValueError("Dataset artifact SHA-256 mismatch: " + name)
    return path, digest


def _split_groups(split_map):
    assignment, metadata = split_map.get("assignment"), split_map.get("groups")
    if not isinstance(assignment, dict) or not isinstance(metadata, list):
        raise ValueError("split-map.json requires assignment and groups")
    if any(not isinstance(key, str) or split not in SPLITS for key, split in assignment.items()):
        raise ValueError("Unknown split in split-map.json")
    groups, families, apps = {}, {}, {}
    for group in metadata:
        if not isinstance(group, dict):
            raise ValueError("Split group metadata must be objects")
        group_id = nonempty_string(group.get("groupId"), "groupId")
        if group_id in groups or assignment.get(group_id) != group.get("split"):
            raise ValueError("Duplicate or inconsistent split group metadata")
        source, split = group.get("source"), group["split"]
        app = nonempty_string(group.get("app"), "app")
        family = nonempty_string(group.get("screenFamily"), "screenFamily")
        if source == "real":
            if split != "real-test":
                raise ValueError("Real application data must only be in real-test")
            apps.setdefault(app, set()).add(split)
        elif source == "fixture":
            if split == "real-test":
                raise ValueError("Fixture data cannot be in real-test")
            families.setdefault(family, set()).add(split)
        else:
            raise ValueError("Unknown data source")
        groups[group_id] = group
    if set(groups) != set(assignment):
        raise ValueError("Split metadata does not cover every assignment")
    if any(len(splits) > 1 for splits in [*families.values(), *apps.values()]):
        raise ValueError("Screen family or real app leaked across splits")
    return groups


def _validate_label_rows(paths, rows, groups, schema):
    candidates = {(row.group_id, row.plan_key): row for row in rows}
    seen = set()
    with paths["labels"].open(encoding="utf-8-sig") as stream:
        for line in stream:
            label = parse_json(line)
            if not isinstance(label, dict) or type(label.get("format")) is not int or label["format"] != 1:
                raise ValueError("Unsupported candidate label format")
            pair = (nonempty_string(label.get("groupId"), "label groupId"),
                    nonempty_string(label.get("planKey"), "label planKey"))
            if pair in seen or pair not in candidates:
                raise ValueError("Candidate labels differ from features.csv")
            if (type(label.get("survival")) not in (int, float)
                    or label["survival"] != candidates[pair].survival):
                raise ValueError("Candidate survival differs from features.csv")
            seen.add(pair)
    if seen != set(candidates):
        raise ValueError("Candidate labels do not cover features.csv")
    by_group = {}
    for row in rows:
        by_group.setdefault(row.group_id, []).append(row)
    rule_groups = set()
    with paths["rules"].open(encoding="utf-8-sig") as stream:
        for line in stream:
            rule = parse_json(line)
            if not isinstance(rule, dict) or type(rule.get("format")) is not int or rule["format"] != 1:
                raise ValueError("Unsupported rule record format")
            group_id = nonempty_string(rule.get("groupId"), "rule groupId")
            if group_id not in groups or group_id in rule_groups:
                raise ValueError("Duplicate or unknown group in rules.jsonl")
            rule_groups.add(group_id)
            candidates = by_group.get(group_id, [])
            if type(rule.get("candidateCount")) is not int or rule["candidateCount"] != len(candidates):
                raise ValueError("Rule candidate count differs from features.csv")
            if any(rule.get(name) != groups[group_id][name] for name in ("source", "app", "screenFamily")):
                raise ValueError("Rule metadata differs from split-map.json")
            if candidates:
                ranks = _rule_ranks(candidates, schema)
                first = candidates[min(range(len(candidates)), key=lambda index: ranks[index])]
                if rule.get("rulePlanKey") != first.plan_key or rule.get("ruleSurvival") != first.survival:
                    raise ValueError("rule_rank_norm disagrees with the Kotlin RuleRanker choice")
            elif rule.get("rulePlanKey") is not None or rule.get("ruleSurvival") is not None:
                raise ValueError("Zero-candidate groups cannot select a rule plan")
    if rule_groups != set(groups):
        raise ValueError("rules.jsonl does not cover every task group")


def read_dataset(dataset_dir):
    root = Path(dataset_dir).resolve()
    manifest_path = root / "dataset-manifest.json"
    manifest = read_json(manifest_path)
    if (not isinstance(manifest, dict) or type(manifest.get("format")) is not int
            or manifest["format"] != 1 or manifest.get("status") != "complete"):
        raise ValueError("Training/evaluation requires a complete format-1 dataset manifest")
    nonempty_string(manifest.get("datasetId"), "datasetId")
    paths, hashes = {}, {"manifest": sha256(manifest_path)}
    for name in ("features", "labels", "rules", "splitMap", "featureSchema", "export"):
        paths[name], hashes[name] = _checked_artifact(root, manifest, name)
    schema_path = paths["featureSchema"]
    if schema_path.name != "features.json":
        raise ValueError("Feature schema artifact must be features.json")
    schema = read_schema(schema_path)
    hashes["schema"] = sha256(schema_path)
    exported = read_json(paths["export"])
    if (not isinstance(exported, dict) or type(exported.get("format")) is not int
            or exported["format"] != 1 or exported.get("tool") not in EXPORT_TOOLS):
        raise ValueError("Unsupported Kotlin feature export provenance")
    for field, artifact in (("featuresSha256", "features"), ("featureSchemaSha256", "featureSchema"),
                            ("labelsSha256", "labels"), ("rulesSha256", "rules"), ("splitMapSha256", "splitMap")):
        if exported.get(field) != hashes[artifact]:
            raise ValueError("Kotlin export provenance mismatch: " + field)
    if (exported.get("featureSchemaVersion") != schema.version or exported.get("featureNamesHash") != schema.names_hash
            or not isinstance(exported.get("generatorCommit"), str)
            or not re.fullmatch(r"[0-9a-f]{40}", exported["generatorCommit"])):
        raise ValueError("Kotlin export schema/commit provenance is missing or inconsistent")
    split_map = read_json(paths["splitMap"])
    if (not isinstance(split_map, dict) or type(split_map.get("format")) is not int or split_map["format"] != 1
            or split_map.get("datasetId") != manifest["datasetId"]):
        raise ValueError("Split map format/datasetId does not match the dataset")
    groups = _split_groups(split_map)
    if any(group["source"] == "real" for group in groups.values()):
        private_path(root)
    rows = read_feature_rows(paths["features"], schema, split_map["assignment"])
    _validate_label_rows(paths, rows, groups, schema)
    if manifest.get("groups") != len(groups) or manifest.get("candidates") != len(rows):
        raise ValueError("Dataset manifest group/candidate counts do not match")
    if exported.get("groups") != len(groups) or exported.get("candidates") != len(rows):
        raise ValueError("Kotlin export provenance group/candidate counts do not match")
    for split in SPLITS:
        group_ids = {group_id for group_id, group in groups.items() if group["split"] == split}
        selected = [row for row in rows if row.split == split]
        stats = manifest.get("splits", {}).get(split, {})
        if (stats.get("groups") != len(group_ids) or stats.get("candidates") != len(selected)
                or stats.get("groupsWithoutCandidates") != len(group_ids - {row.group_id for row in selected})):
            raise ValueError("Dataset manifest split counts do not match: " + split)
    return Dataset(root, schema, manifest, split_map, groups, rows, hashes)


def survival_grade(survival):
    """P3.3's three explicit grades; do not silently quantize new task formats."""
    if type(survival) not in (int, float) or survival not in (0, 0.5, 1):
        raise ValueError("Training requires survival grades 0, 0.5, or 1; unsupported label: " + str(survival))
    return int(survival * 2)


def _rule_ranks(rows, schema):
    try:
        index = schema.names.index("rule_rank_norm")
    except ValueError as error:
        raise ValueError("Feature schema is missing rule_rank_norm") from error
    ranks = []
    for row in rows:
        if len(row.values) != len(schema.names):
            raise ValueError("Feature vector length differs from schema")
        rank = row.values[index]
        if not math.isfinite(rank) or not 0 <= rank <= 1:
            raise ValueError("rule_rank_norm must be finite and between 0 and 1")
        ranks.append(rank)
    if len(set(ranks)) != len(ranks):
        raise ValueError("rule_rank_norm must give each candidate a distinct rule order")
    return ranks


def rank_indices(rows, scores, schema, epsilon=1e-6):
    """Score order with deterministic rule ordering for near-equal scores.

    An approximate comparison is not transitive. Sort exact scores first, then
    make disjoint batches anchored at each batch's maximum. Members less than
    epsilon from that maximum use rule order. No integer rounding boundary is
    introduced, and candidate input order cannot change the result.
    """
    if len(rows) != len(scores) or any(not math.isfinite(score) for score in scores):
        raise ValueError("Expected one finite score per candidate")
    if not math.isfinite(epsilon) or epsilon <= 0:
        raise ValueError("Score epsilon must be positive and finite")
    if len({row.group_id for row in rows}) > 1:
        raise ValueError("Rank candidates from one task group at a time")
    ranks = _rule_ranks(rows, schema)
    order = sorted(range(len(rows)), key=lambda index: (-scores[index], ranks[index], rows[index].plan_key))
    result, start = [], 0
    while start < len(order):
        end = start + 1
        while end < len(order) and scores[order[start]] - scores[order[end]] < epsilon:
            end += 1
        result.extend(sorted(order[start:end], key=lambda index: (ranks[index], rows[index].plan_key)))
        start = end
    return tuple(result)
