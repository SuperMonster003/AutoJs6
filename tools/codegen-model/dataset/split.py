#!/usr/bin/env python3
"""Freeze family-level splits and build/refresh a dataset provenance manifest.

Hash-sort atomic screen families, then allocate 70/15/15 by largest remainders.
Eight families yield 6/1/1, so a three-percentage-point ratio bound is impossible.
Report actual family AND group ratios; never split seeds to improve a ratio.
Real apps go to real-train or real-test by a per-app hash threshold (roadmap D25), regardless of
their screen family. Each app is assigned on its own, so adding apps never moves an existing app,
and a real-test app never enters training.
"""

import argparse
from collections import Counter, defaultdict
import csv
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import re
import sys

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from dataset.task_io import load_group, nonempty_string, parse_json, private_path, read_json, write_json


SPLITS = ("train", "valid", "test", "real-test", "real-train")
RATIOS = (0.70, 0.15, 0.15)
FAMILIES = ("SELF", "SELF_INDEX", "ANCHOR_PATH", "ANCHOR_SEARCH", "CONTAINER_ITEMS", "SELF_RELATIVE")
# Feature schemas the host can extract or project; schema 2 added the relative family (roadmap D22).
SUPPORTED_FEATURE_SCHEMAS = (1, 2)
HASH_NAMESPACE = "autojs6.codegen.family-split.v1"
REAL_HASH_NAMESPACE = "autojs6.codegen.real-app-split.v1"
# Fraction of real applications held out for real-test (D25); the rest may train.
REAL_TEST_FRACTION = 0.5


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def content_hash(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True,
                                     separators=(",", ":"), allow_nan=False).encode("utf-8")).hexdigest()


def real_app_split(app):
    """A stable per-app assignment: the first 64 hash bits as a fraction of 2^64, below the threshold for real-test."""
    point = int(hashlib.sha256((REAL_HASH_NAMESPACE + "\0" + app).encode("utf-8")).hexdigest()[:16], 16) / 2**64
    return "real-test" if point < REAL_TEST_FRACTION else "real-train"


def assign_splits(groups):
    ids = [group["groupId"] for group in groups]
    if len(set(ids)) != len(ids):
        raise ValueError("Duplicate groupId")
    families = {group["screenFamily"] for group in groups if group["source"] == "fixture"}
    ordered = sorted(families, key=lambda family: (hashlib.sha256(
        (HASH_NAMESPACE + "\0" + family).encode("utf-8")).hexdigest(), family))
    quotas = [len(ordered) * ratio for ratio in RATIOS]
    counts = [math.floor(quota) for quota in quotas]
    for index in sorted(range(3), key=lambda i: (-(quotas[i] - counts[i]), i))[:len(ordered) - sum(counts)]:
        counts[index] += 1
    family_splits, offset = {}, 0
    for split, count in zip(SPLITS, counts):
        family_splits.update((family, split) for family in ordered[offset:offset + count])
        offset += count
    return {group["groupId"]: (real_app_split(group["app"]) if group["source"] == "real" else family_splits[group["screenFamily"]])
            for group in sorted(groups, key=lambda item: item["groupId"])}


def _json_lines(path):
    with Path(path).open(encoding="utf-8-sig") as source:
        for line_number, text in enumerate(source, 1):
            if not text.strip():
                continue
            try:
                value = parse_json(text)
            except (ValueError, TypeError) as error:
                raise ValueError(f"Invalid {Path(path).name} line {line_number}: {error}") from error
            if not isinstance(value, dict):
                raise ValueError(f"{Path(path).name} line {line_number} must be an object")
            yield value


def validate_labels(groups, labels_path, rules_path):
    """Cross-check the independent exporter index, including explicit no-candidate groups."""
    by_id = {group["groupId"]: group for group in groups}
    rules = {}
    for row in _json_lines(rules_path):
        group_id = row.get("groupId")
        if not isinstance(group_id, str) or group_id not in by_id:
            raise ValueError("rules.jsonl contains an unknown groupId")
        if group_id in rules:
            raise ValueError("Duplicate group in rules.jsonl: " + group_id)
        if type(row.get("format")) is not int or row["format"] != 1:
            raise ValueError("Unsupported rules format")
        if type(row.get("candidateCount")) is not int or row["candidateCount"] < 0:
            raise ValueError("Invalid candidateCount")
        if row.get("source") != by_id[group_id]["source"] or row.get("screenFamily") != by_id[group_id]["screenFamily"]:
            raise ValueError("Rule metadata differs from task: " + group_id)
        if row.get("app") != by_id[group_id]["app"]:
            raise ValueError("Rule app differs from task: " + group_id)
        if row["candidateCount"] == 0:
            if row.get("rulePlanKey") is not None or row.get("ruleSurvival") is not None:
                raise ValueError("A zero-candidate group must not have a rule selection")
        else:
            nonempty_string(row.get("rulePlanKey"), "rulePlanKey")
        rules[group_id] = row
    if set(rules) != set(by_id):
        raise ValueError("Missing groups in rules.jsonl: " + ", ".join(sorted(set(by_id) - set(rules))))
    labels, counts, selected = {}, Counter(), defaultdict(list)
    for row in _json_lines(labels_path):
        group_id, plan_key = row.get("groupId"), row.get("planKey")
        if not isinstance(group_id, str) or group_id not in by_id:
            raise ValueError("labels.jsonl contains an unknown groupId")
        nonempty_string(plan_key, "planKey")
        key = (group_id, plan_key)
        if key in labels:
            raise ValueError("Duplicate candidate in labels.jsonl: " + group_id)
        if type(row.get("format")) is not int or row["format"] != 1 or row.get("family") not in FAMILIES:
            raise ValueError("Invalid label format or plan family")
        survival = row.get("survival")
        if type(survival) not in (int, float) or not math.isfinite(survival) or not 0 <= survival <= 1:
            raise ValueError("survival must be finite and within [0, 1]")
        if type(row.get("ruleSelected")) is not bool:
            raise ValueError("ruleSelected must be a boolean")
        group = by_id[group_id]
        evaluation_states = {state["stateId"]: state for index, state in enumerate(group["states"])
                             if index != group.get("captureState", 0)}
        failures = row.get("failedStates")
        if not isinstance(failures, list) or not evaluation_states:
            raise ValueError("Survival labels require failedStates and at least one evaluation state")
        failed_ids = set()
        for failure in failures:
            if not isinstance(failure, dict) or failure.get("stateId") not in evaluation_states:
                raise ValueError("Label failure references an unknown/capture state")
            state_id = failure["stateId"]
            if state_id in failed_ids:
                raise ValueError("Duplicate failed state")
            failed_ids.add(state_id)
            if failure.get("mutations") != evaluation_states[state_id]["mutations"]:
                raise ValueError("Failed-state mutations differ from task")
            nonempty_string(failure.get("reason"), "failure reason")
            for field in ("extraCount", "missingCount"):
                if type(failure.get(field)) is not int or failure[field] < 0:
                    raise ValueError("Invalid failed-state " + field)
        expected_survival = 1 - len(failed_ids) / len(evaluation_states)
        if abs(survival - expected_survival) > 1e-9:
            raise ValueError("survival disagrees with failedStates")
        if row["ruleSelected"]:
            selected[group_id].append(plan_key)
        # Keep only fields needed by summaries/features, not every failure transcript.
        labels[key] = {"family": row["family"], "survival": survival}
        counts[group_id] += 1
    for group_id, rule in rules.items():
        if counts[group_id] != rule["candidateCount"]:
            raise ValueError("Missing/extra label candidates for " + group_id)
        expected_selected = [] if rule["candidateCount"] == 0 else [rule["rulePlanKey"]]
        if selected[group_id] != expected_selected:
            raise ValueError("Rule selection differs from labels for " + group_id)
        if expected_selected:
            survival = rule.get("ruleSurvival")
            if type(survival) not in (int, float) or not math.isfinite(survival):
                raise ValueError("Invalid ruleSurvival")
            if abs(labels[(group_id, rule["rulePlanKey"])]["survival"] - survival) > 1e-9:
                raise ValueError("ruleSurvival differs from label")
    return labels, counts


def validate_features(path, labels, assignment, schema=None):
    seen = set()
    with Path(path).open(encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        columns = reader.fieldnames or []
        metadata = {"split", "groupId", "planKey", "label"}
        if len(columns) != len(set(columns)) or not metadata.issubset(columns) or len(columns) <= 4:
            raise ValueError("features.csv requires split/groupId/planKey/label and numeric feature columns")
        if schema is not None and columns != ["split", "groupId", "planKey", "label", *schema["names"]]:
            raise ValueError("Feature columns differ from features.json")
        for row in reader:
            key = (row["groupId"], row["planKey"])
            if None in row or key not in labels or key in seen:
                raise ValueError("Unknown/duplicate/malformed feature row")
            if row["split"] != assignment[key[0]]:
                raise ValueError("Feature split differs from split-map.json")
            try:
                values = {name: float(value) for name, value in row.items() if name not in metadata - {"label"}}
            except (TypeError, ValueError) as error:
                raise ValueError("Features and labels must be finite numbers") from error
            if not all(math.isfinite(value) for value in values.values()):
                raise ValueError("Features and labels must be finite numbers")
            if abs(values["label"] - labels[key]["survival"]) > 1e-9:
                raise ValueError("Feature label differs from survival")
            seen.add(key)
    if seen != set(labels):
        raise ValueError("Missing feature candidates")


def validate_feature_schema(path):
    schema = read_json(path)
    if (not isinstance(schema, dict) or set(schema) != {"format", "featureSchemaVersion", "names", "namesHash"}
            or type(schema["format"]) is not int or schema["format"] != 1
            or type(schema["featureSchemaVersion"]) is not int or schema["featureSchemaVersion"] not in SUPPORTED_FEATURE_SCHEMAS):
        raise ValueError("Unsupported feature schema")
    names = schema["names"]
    if (not isinstance(names, list) or not names or any(not isinstance(name, str) or not re.fullmatch(r"[a-z][a-z0-9_]*", name) for name in names)
            or len(names) != len(set(names))):
        raise ValueError("Feature names must be distinct column names")
    expected = hashlib.sha256("\n".join(names).encode("utf-8")).hexdigest()[:16]
    if schema["namesHash"] != expected:
        raise ValueError("Feature namesHash differs from its ordered names")
    return schema


def _artifact(path, pending_reason):
    if path is None:
        return {"status": "pending", "file": None, "sha256": None, "reason": pending_reason}
    return {"status": "complete", "file": Path(path).name, "bytes": Path(path).stat().st_size, "sha256": sha256(path)}


def validate_export(manifest_path, input_root, records, labels_path, rules_path, candidates):
    """Reject stale labels even when group IDs and candidate counts still happen to match."""
    exported = read_json(manifest_path)
    if (not isinstance(exported, dict) or exported.get("format") != 1
            or exported.get("tool") != "CodeGenDatasetExportTask" or exported.get("toolVersion") != 1):
        raise ValueError("Unsupported export-manifest.json")
    for field, path in (("labelsSha256", labels_path), ("rulesSha256", rules_path)):
        if exported.get(field) != sha256(path):
            raise ValueError("Exporter " + field + " differs; rerun the JVM export")
    if exported.get("groups") != len(records) or exported.get("candidates") != candidates:
        raise ValueError("Exporter counts differ from the selected dataset")
    root = Path(input_root).resolve()
    paths = {path for task_path, _, snapshots in records for path in (task_path, *snapshots)}
    if any(not path.is_relative_to(root) for path in paths):
        raise ValueError("All exported inputs must be below --export-input-root")
    digest = hashlib.sha256()
    # Match Kotlin/Java String ordering, including supplementary-plane file names.
    for path in sorted(paths, key=lambda item: item.relative_to(root).as_posix().encode("utf-16-be")):
        digest.update(path.relative_to(root).as_posix().encode("utf-8"))
        digest.update(b"\0")
        digest.update(sha256(path).encode("ascii"))
        digest.update(b"\n")
    if exported.get("inputSha256") != digest.hexdigest():
        raise ValueError("Task/snapshot content changed since label export; rerun the JVM export")
    return exported


def discover_groups(inputs):
    paths = set()
    for source in inputs:
        source = Path(source).resolve()
        if source.is_file() and source.name.endswith(".cgtask.json"):
            paths.add(source)
        elif source.is_dir():
            paths.update(path.resolve() for path in source.rglob("*.cgtask.json"))
        else:
            raise ValueError("Input must be a task file or directory: " + str(source))
    if not paths:
        raise ValueError("No .cgtask.json tasks found")
    records, ids = [], set()
    for path in sorted(paths):
        group, snapshots = load_group(path)
        if group["groupId"] in ids:
            raise ValueError("Duplicate groupId across task files: " + group["groupId"])
        ids.add(group["groupId"])
        records.append((path, group, snapshots))
    return sorted(records, key=lambda record: record[1]["groupId"])


def validate_collection(provenance, dataset_id, groups):
    """A live/failed collection must not be frozen as a complete training dataset."""
    if provenance.get("format") != 1 or "plan" not in provenance:
        return  # Handwritten/offline provenance need not be a capture.collect manifest.
    if provenance.get("datasetId") != dataset_id:
        raise ValueError("Collection datasetId differs from the requested dataset")
    if not provenance.get("completedAt") or provenance.get("pendingState") is not None:
        raise ValueError("Collection is not complete; finish capture before freezing the dataset")
    counts = [provenance.get(name) for name in ("attemptedStates", "acceptedStates", "discardedStates")]
    if any(type(value) is not int or value < 0 for value in counts) or counts[0] != counts[1] + counts[2]:
        raise ValueError("Collection attempt/accept/discard counts disagree")
    expected_rate = counts[2] / max(1, counts[0])
    recorded_rate = provenance.get("discardRate")
    if type(recorded_rate) not in (int, float) or not math.isfinite(recorded_rate) or abs(recorded_rate - expected_rate) > 1e-9:
        raise ValueError("Collection discard rate disagrees with its counts")
    if expected_rate > .02:
        raise ValueError("Collection discard rate exceeds 2%; fix association before using the dataset")
    fixture_count = sum(group["source"] == "fixture" for group in groups)
    if type(provenance.get("groups")) is not int or provenance["groups"] != fixture_count:
        raise ValueError("Collection group count differs from discovered fixture tasks")


def build_dataset(inputs, *, output, dataset_id, provenance_path=None, labels_path=None,
                  rules_path=None, features_path=None, generated_at=None, generator_commit=None,
                  fixture_version_code=None, export_manifest_path=None, export_input_root=None):
    nonempty_string(dataset_id, "datasetId")
    records = discover_groups(inputs)
    groups = [record[1] for record in records]
    output = Path(output).resolve()
    if any(group["source"] == "real" for group in groups):
        private_path(output)
        for path in (labels_path, rules_path, features_path, export_manifest_path):
            if path is not None:
                private_path(path)
    provenance = read_json(provenance_path) if provenance_path else {}
    if not isinstance(provenance, dict):
        raise ValueError("Provenance must be an object")
    validate_collection(provenance, dataset_id, groups)
    generator_commit = generator_commit or provenance.get("generatorCommit")
    if not isinstance(generator_commit, str) or not re.fullmatch(r"[0-9a-fA-F]{40}", generator_commit):
        raise ValueError("Provide the full generatorCommit in --provenance or --generator-commit")
    fixture_version_code = fixture_version_code if fixture_version_code is not None else provenance.get("fixtureVersionCode")
    if any(group["source"] == "fixture" for group in groups):
        if type(fixture_version_code) is not int or fixture_version_code < 1:
            raise ValueError("Provide a positive fixtureVersionCode")
    devices = provenance.get("devices", [])
    if not isinstance(devices, list) or any(not isinstance(device, dict) for device in devices):
        raise ValueError("Provenance devices must be an object list")
    assignment = assign_splits(groups)
    if (labels_path is None) != (rules_path is None):
        raise ValueError("Provide labels.jsonl and rules.jsonl together to verify candidate completeness")
    labels, candidate_counts = ({}, None) if labels_path is None else validate_labels(groups, labels_path, rules_path)
    exported = None
    if export_manifest_path is None and labels_path is not None:
        adjacent = Path(labels_path).parent / "export-manifest.json"
        if adjacent.is_file():
            export_manifest_path = adjacent
    if export_manifest_path is not None:
        if labels_path is None:
            raise ValueError("Exporter provenance requires completed labels and rules")
        if export_input_root is None:
            if len(inputs) != 1 or not Path(inputs[0]).is_dir():
                raise ValueError("Provide --export-input-root matching CODEGEN_DATASET_DIR")
            export_input_root = inputs[0]
        exported = validate_export(export_manifest_path, export_input_root, records, labels_path, rules_path, sum(candidate_counts.values()))
    feature_schema_path, feature_schema = None, None
    if features_path is not None:
        if labels_path is None:
            raise ValueError("features.csv requires completed labels")
        feature_schema_path = Path(features_path).parent / "features.json"
        if any(group["source"] == "real" for group in groups):
            private_path(feature_schema_path)
        feature_schema = validate_feature_schema(feature_schema_path)
        validate_features(features_path, labels, assignment, feature_schema)
        if exported is not None:
            for field, value in (("featuresSha256", sha256(features_path)), ("featureSchemaSha256", sha256(feature_schema_path)),
                                 ("featureSchemaVersion", feature_schema["featureSchemaVersion"]), ("featureNamesHash", feature_schema["namesHash"])):
                if exported.get(field) != value:
                    raise ValueError("Exporter " + field + " differs; rerun the JVM feature export")
    statistics = {}
    for split in SPLITS:
        selected = [group for group in groups if assignment[group["groupId"]] == split]
        statistics[split] = {
            "groups": len(selected),
            "candidates": None if candidate_counts is None else sum(candidate_counts[group["groupId"]] for group in selected),
            "groupsWithoutCandidates": None if candidate_counts is None else sum(candidate_counts[group["groupId"]] == 0 for group in selected),
            "states": sum(len(group["states"]) for group in selected),
            "source": dict(sorted(Counter(group["source"] for group in selected).items())),
            "apps": dict(sorted(Counter(group["app"] for group in selected).items())),
            "screenFamilies": dict(sorted(Counter(group["screenFamily"] for group in selected).items())),
            "mutations": dict(sorted(Counter(mutation for group in selected for state in group["states"]
                                             for mutation in state["mutations"]).items())),
            "planFamilies": dict(sorted(Counter(row["family"] for (group_id, _), row in labels.items()
                                                if assignment[group_id] == split).items())),
        }
    fixture_groups = sum(group["source"] == "fixture" for group in groups)
    fixture_families = len({group["screenFamily"] for group in groups if group["source"] == "fixture"})
    task_records, snapshot_records, cached_hashes = [], [], {}
    for task_path, group, snapshots in records:
        task_records.append({"groupId": group["groupId"], "sha256": sha256(task_path)})
        for state, snapshot in zip(group["states"], snapshots):
            if snapshot not in cached_hashes:
                cached_hashes[snapshot] = sha256(snapshot)
            snapshot_records.append({"groupId": group["groupId"], "stateId": state["stateId"],
                                     "sha256": cached_hashes[snapshot]})
    split_map = {"format": 1, "datasetId": dataset_id, "assignment": assignment,
                 "groups": [{"groupId": group["groupId"], "split": assignment[group["groupId"]],
                             "source": group["source"], "app": group["app"], "screenFamily": group["screenFamily"],
                             "task": str(task_path)} for task_path, group, _ in records]}
    manifest = {
        "format": 1, "datasetId": dataset_id,
        "generatedAt": generated_at or datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "generatorCommit": generator_commit.lower(), "fixtureVersionCode": fixture_version_code,
        "devices": devices,
        "groups": len(groups), "candidates": None if candidate_counts is None else sum(candidate_counts.values()),
        "status": "labels-pending" if labels_path is None else "features-pending" if features_path is None else "complete",
        "splits": statistics,
        "splitPolicy": {
            "algorithm": "sha256-ranked-screen-family-largest-remainder-v1", "namespace": HASH_NAMESPACE,
            "fixtureUnit": "screenFamily", "realUnit": "app",
            "realAlgorithm": "sha256-app-threshold-v1", "realNamespace": REAL_HASH_NAMESPACE,
            "realTestFraction": REAL_TEST_FRACTION,
            "targetFamilyRatios": dict(zip(SPLITS, RATIOS)),
            "actualFamilyRatios": {split: len(statistics[split]["screenFamilies"]) / fixture_families if fixture_families else 0
                                   for split in SPLITS[:3]},
            "actualGroupRatios": {split: statistics[split]["groups"] / fixture_groups if fixture_groups else 0 for split in SPLITS[:3]},
            "note": "Atomic families are never divided by seed. Eight families round to 6/1/1 (75/12.5/12.5%). "
                    "Group ratios can differ further when family sizes differ. Freeze this manifest before training; "
                    "adding families can change hash-ranked assignments. Real apps are assigned one by one, "
                    "so adding apps never moves an existing app between real-train and real-test.",
        },
        "artifacts": {
            "labels": _artifact(labels_path, "Run CodeGenDatasetExportTask to generate labels.jsonl and rules.jsonl"),
            "rules": _artifact(rules_path, "Run CodeGenDatasetExportTask"),
            "features": _artifact(features_path, "Feature export is implemented in P3.2; no placeholder hash is used"),
            "featureSchema": _artifact(feature_schema_path, "Run the JVM exporter to generate features.json"),
            "tasks": {"count": len(task_records), "sha256": content_hash(task_records)},
            "snapshots": {"count": len(snapshot_records), "uniqueFiles": len(cached_hashes), "sha256": content_hash(snapshot_records)},
            "provenance": _artifact(provenance_path, "Capture provenance not supplied"),
            "export": _artifact(export_manifest_path, "Exporter provenance not supplied; counts are checked against rules.jsonl"),
        },
        "capture": {key: provenance[key] for key in ("startedAt", "completedAt", "elapsedSeconds", "attemptedStates",
                                                    "acceptedStates", "discardedStates", "discardRate", "plan") if key in provenance},
    }
    # Write only after every group, label, feature and provenance check succeeds.
    write_json(output / "split-map.json", split_map)
    manifest["artifacts"]["splitMap"] = _artifact(output / "split-map.json", "")
    write_json(output / "dataset-manifest.json", manifest)
    return manifest


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, action="append", required=True, help="Task directory or .cgtask.json; repeatable")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--dataset-id", required=True)
    parser.add_argument("--provenance", type=Path, help="Capture collection.json")
    parser.add_argument("--labels", type=Path)
    parser.add_argument("--rules", type=Path)
    parser.add_argument("--features", type=Path)
    parser.add_argument("--export-manifest", type=Path, help="Auto-detected next to labels.jsonl")
    parser.add_argument("--export-input-root", type=Path, help="Original CODEGEN_DATASET_DIR if different from the single --input directory")
    parser.add_argument("--generator-commit")
    parser.add_argument("--fixture-version-code", type=int)
    parser.add_argument("--generated-at", help="Optional fixed ISO timestamp for reproducible manifest generation")
    args = parser.parse_args(argv)
    # Rerunning the same command refreshes pending artifacts after the JVM exporter runs.
    for field, filename in (("labels", "labels.jsonl"), ("rules", "rules.jsonl"), ("features", "features.csv")):
        if getattr(args, field) is None and (args.output / filename).is_file():
            setattr(args, field, args.output / filename)
    if args.labels is not None and args.rules is None and (args.labels.parent / "rules.jsonl").is_file():
        args.rules = args.labels.parent / "rules.jsonl"
    try:
        manifest = build_dataset(args.input, output=args.output, dataset_id=args.dataset_id,
                                 provenance_path=args.provenance, labels_path=args.labels, rules_path=args.rules,
                                 features_path=args.features, generated_at=args.generated_at,
                                 generator_commit=args.generator_commit, fixture_version_code=args.fixture_version_code,
                                 export_manifest_path=args.export_manifest, export_input_root=args.export_input_root)
    except (ValueError, OSError) as error:
        parser.exit(2, str(error) + "\n")
    print(json.dumps({"datasetId": manifest["datasetId"], "status": manifest["status"],
                      "groups": manifest["groups"], "candidates": manifest["candidates"],
                      "actualFamilyRatios": manifest["splitPolicy"]["actualFamilyRatios"]}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
