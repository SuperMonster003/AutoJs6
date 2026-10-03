#!/usr/bin/env python3
"""Prioritize private, single-state tasks for manual collection; never approve targets."""

import argparse
from collections import Counter
import json
import math
from pathlib import Path
import sys

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from dataset.split import sha256
from dataset.task_io import load_group, nonempty_string, parse_json, private_path, read_json, write_json
from train.data import CandidateRow, read_schema
from train.evaluate import predict, rank_candidates


def read_candidates(path, schema):
    path = private_path(path)
    groups, seen = [], set()
    rank_column = schema.names.index("rule_rank_norm")
    with path.open(encoding="utf-8-sig") as source:
        for line in source:
            if not line.strip():
                continue
            record = parse_json(line)
            if not isinstance(record, dict) or type(record.get("format")) is not int or record["format"] != 1:
                raise ValueError("Unsupported queue candidate format")
            group_id = nonempty_string(record.get("groupId"), "groupId")
            if group_id in seen:
                raise ValueError("Duplicate queue groupId")
            seen.add(group_id)
            if record.get("source") not in ("real", "fixture"):
                raise ValueError("Unknown candidate source")
            if record["source"] != "real":
                groups.append(record)
                continue
            task_path = private_path(nonempty_string(record.get("task"), "task"))
            task, snapshots = load_group(task_path)
            if record.get("taskSha256") != sha256(task_path):
                raise ValueError("Task changed since queue export; regenerate candidates")
            if record.get("captureSnapshotSha256") != sha256(snapshots[task.get("captureState", 0)]):
                raise ValueError("Capture snapshot changed since queue export; regenerate candidates")
            for field in ("groupId", "source", "app", "screenFamily"):
                if record.get(field) != task[field]:
                    raise ValueError("Queue metadata differs from the manually labelled task: " + field)
            if type(record.get("stateCount")) is not int or record["stateCount"] != len(task["states"]):
                raise ValueError("Queue stateCount differs from task")
            candidates = record.get("candidates")
            if (not isinstance(candidates, list) or type(record.get("candidateCount")) is not int
                    or record["candidateCount"] != len(candidates)):
                raise ValueError("Missing/extra queue candidates")
            keys, ranks = set(), set()
            for candidate in candidates:
                if not isinstance(candidate, dict):
                    raise ValueError("Candidate must be an object")
                key = nonempty_string(candidate.get("planKey"), "planKey")
                rank, features = candidate.get("ruleRank"), candidate.get("features")
                if key in keys or type(rank) is not int or rank < 0 or rank in ranks:
                    raise ValueError("Duplicate candidate or invalid rule rank")
                keys.add(key)
                ranks.add(rank)
                if (not isinstance(features, list) or len(features) != len(schema.names)
                        or any(type(value) not in (int, float) or not math.isfinite(value)
                               or abs(value) > 3.4028234663852886e38 for value in features)):
                    raise ValueError("Candidate feature vector must match schema and contain only finite numbers")
                if abs(features[rank_column] - rank / len(candidates)) > 1e-6:
                    raise ValueError("Candidate ruleRank differs from rule_rank_norm")
            if ranks != set(range(len(candidates))):
                raise ValueError("Missing queue rule ranks")
            record["task"] = str(task_path)
            groups.append(record)
    return groups


def build_queue(groups, scores_by_group, *, labelled_group_ids=(), max_score_gap=0.1, limit=100):
    if type(max_score_gap) not in (int, float) or not math.isfinite(max_score_gap) or max_score_gap < 0:
        raise ValueError("max-score-gap must be a finite nonnegative number")
    if type(limit) is not int or limit < 1:
        raise ValueError("limit must be a positive integer")
    labelled = set(labelled_group_ids)
    seen, items, skipped = set(), [], Counter()
    expected_predictions = set()
    for group in groups:
        group_id = nonempty_string(group.get("groupId"), "groupId")
        if group_id in seen:
            raise ValueError("Duplicate queue groupId")
        seen.add(group_id)
        if group.get("source") != "real":
            skipped["notReal"] += 1
            continue
        if type(group.get("stateCount")) is not int or group["stateCount"] < 1:
            raise ValueError("Candidate task must have a confirmed capture state")
        if group["stateCount"] > 1 or group_id in labelled:
            skipped["alreadyAnnotated"] += 1
            continue
        candidates = group.get("candidates")
        if (not isinstance(candidates, list) or type(group.get("candidateCount")) is not int
                or len(candidates) != group["candidateCount"]):
            raise ValueError("Missing/extra queue candidates")
        if not candidates:
            skipped["noCandidates"] += 1
            continue
        expected_predictions.add(group_id)
        scores = scores_by_group.get(group_id)
        if scores is None or len(scores) != len(candidates):
            raise ValueError("Missing/extra queue predictions")
        keys, ranks, ranked_input = set(), set(), []
        for candidate, score in zip(candidates, scores):
            key = nonempty_string(candidate.get("planKey"), "planKey")
            rank = candidate.get("ruleRank")
            if key in keys or type(rank) is not int or rank < 0 or rank in ranks:
                raise ValueError("Duplicate queue candidate or rule rank")
            keys.add(key)
            ranks.add(rank)
            if type(score) not in (int, float) or not math.isfinite(score):
                raise ValueError("Queue predictions must be finite")
            ranked_input.append({"planKey": key, "ruleRank": rank, "score": score})
        if ranks != set(range(len(candidates))):
            raise ValueError("Missing queue rule ranks")
        model = rank_candidates(ranked_input)[0]
        rule = next(candidate for candidate in ranked_input if candidate["ruleRank"] == 0)
        if model["planKey"] == rule["planKey"]:
            skipped["sameSelection"] += 1
            continue
        gap = abs(model["score"] - rule["score"])
        if gap > max_score_gap:
            skipped["scoreGapTooLarge"] += 1
            continue
        items.append({"format": 1, "groupId": group_id, "source": "real", "app": group["app"],
                      "screenFamily": group["screenFamily"], "task": group["task"], "stateCount": 1,
                      "modelPlanKey": model["planKey"], "rulePlanKey": rule["planKey"],
                      "modelScore": model["score"], "ruleScore": rule["score"], "scoreGap": gap,
                      "requiresHumanConfirmation": True, "reason": "model-rule-disagreement-with-small-score-gap"})
    if set(scores_by_group) != expected_predictions:
        raise ValueError("Predictions reference missing/ineligible queue groups")
    items.sort(key=lambda item: (item["scoreGap"], item["groupId"], item["modelPlanKey"], item["rulePlanKey"]))
    summary = {"inputGroups": len(seen), "eligibleDisagreements": len(items), "queued": min(limit, len(items)),
               "maxScoreGap": max_score_gap, "limit": limit, "skipped": dict(sorted(skipped.items())),
               "note": "Score gaps are uncalibrated model values. Collect another state and confirm its targets in label_real.py."}
    return items[:limit], summary


def _labelled_groups(path):
    if path is None:
        return set()
    result = set()
    with private_path(path).open(encoding="utf-8-sig") as source:
        for line in source:
            if line.strip():
                row = parse_json(line)
                if not isinstance(row, dict):
                    raise ValueError("Label row must be an object")
                result.add(nonempty_string(row.get("groupId"), "label groupId"))
    return result


def validate_queue_manifest(candidates_path, features_path, schema, groups):
    manifest_path = private_path(Path(candidates_path).parent / "queue-manifest.json")
    manifest = read_json(manifest_path)
    if (not isinstance(manifest, dict) or type(manifest.get("format")) is not int or manifest["format"] != 1
            or manifest.get("tool") != "CodeGenQueueExportTask" or type(manifest.get("toolVersion")) is not int
            or manifest["toolVersion"] != 1):
        raise ValueError("Unsupported queue-manifest.json")
    if manifest.get("candidatesSha256") != sha256(candidates_path) or manifest.get("featureSchemaSha256") != sha256(features_path):
        raise ValueError("Queue export file hashes differ; regenerate candidates")
    if manifest.get("featureSchemaVersion") != schema.version or manifest.get("featureNamesHash") != schema.names_hash:
        raise ValueError("Queue export feature schema differs")
    counts = [record.get("candidateCount") for record in groups]
    if any(type(count) is not int or count < 0 for count in counts):
        raise ValueError("Invalid queue candidateCount")
    if (type(manifest.get("groups")) is not int or manifest["groups"] != len(groups)
            or type(manifest.get("candidates")) is not int or manifest["candidates"] != sum(counts)):
        raise ValueError("Queue export group/candidate counts differ")
    return manifest_path, manifest


def run_queue(candidates_path, features_path, model_path, output, *, labels_path=None, max_score_gap=0.1, limit=100):
    candidates_path, features_path, output = (private_path(path) for path in (candidates_path, features_path, output))
    protected = {candidates_path, features_path, Path(model_path).resolve()}
    if labels_path is not None:
        protected.add(private_path(labels_path))
    if protected & {output / "queue.jsonl", output / "queue-summary.json"}:
        raise ValueError("Queue output would overwrite an input artifact")
    schema = read_schema(features_path)
    groups = read_candidates(candidates_path, schema)
    manifest_path, export = validate_queue_manifest(candidates_path, features_path, schema, groups)
    labelled = _labelled_groups(labels_path)
    model_hash = sha256(model_path)
    scores, flat_rows, slices = {}, [], []
    for group in groups:
        if group["source"] != "real" or group.get("stateCount") != 1 or group["groupId"] in labelled or not group.get("candidates"):
            continue
        start = len(flat_rows)
        flat_rows.extend(CandidateRow("real-test", group["groupId"], candidate["planKey"], None, tuple(candidate["features"]))
                         for candidate in group["candidates"])
        slices.append((group["groupId"], start, len(flat_rows)))
    predicted = predict(model_path, flat_rows, schema)
    for group_id, start, end in slices:
        scores[group_id] = predicted[start:end]
    items, summary = build_queue(groups, scores, labelled_group_ids=labelled, max_score_gap=max_score_gap, limit=limit)
    # A maintainer may revise capture targets while scoring. Never queue obsolete features.
    if read_candidates(candidates_path, schema) != groups:
        raise ValueError("Queue inputs changed during prediction")
    _, latest_export = validate_queue_manifest(candidates_path, features_path, schema, groups)
    if latest_export != export or sha256(model_path) != model_hash:
        raise ValueError("Queue export or model changed during prediction")
    summary.update({"format": 1, "candidateSha256": sha256(candidates_path), "featuresSha256": sha256(features_path),
                    "modelSha256": model_hash, "featureSchemaVersion": schema.version, "featureNamesHash": schema.names_hash,
                    "queueExportSha256": sha256(manifest_path), "queueExportInputSha256": export.get("inputSha256"),
                    "labelsSha256": sha256(labels_path) if labels_path is not None else None})
    output.mkdir(parents=True, exist_ok=True)
    temporary = output / "queue.jsonl.tmp"
    temporary.write_text("".join(json.dumps(item, ensure_ascii=False, allow_nan=False) + "\n" for item in items),
                         encoding="utf-8", newline="\n")
    temporary.replace(output / "queue.jsonl")
    write_json(output / "queue-summary.json", summary)
    return summary


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidates", type=Path, required=True)
    parser.add_argument("--features", type=Path, required=True)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--labels", type=Path)
    parser.add_argument("--max-score-gap", type=float, default=0.1)
    parser.add_argument("--limit", type=int, default=100)
    args = parser.parse_args(argv)
    try:
        result = run_queue(args.candidates, args.features, args.model, args.output, labels_path=args.labels,
                           max_score_gap=args.max_score_gap, limit=args.limit)
    except (ValueError, OSError, ImportError) as error:
        parser.exit(2, str(error) + "\n")
    print(json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
