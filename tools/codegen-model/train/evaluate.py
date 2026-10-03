#!/usr/bin/env python3
"""Evaluate held-out survival with actual model predictions and the captured rule baseline."""

import argparse
from collections import defaultdict
import hashlib
import json
import math
from pathlib import Path
import sys

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from dataset.split import FAMILIES, content_hash, sha256, validate_labels
from dataset.task_io import load_group, nonempty_string, parse_json, private_path, write_json
from train.data import CandidateRow, FeatureSchema, rank_indices, read_dataset


METRICS = ("top1Survival", "ruleTop1Survival", "regret", "perfectRate", "worseThanRule", "top3Recall")
_RANK_SCHEMA = FeatureSchema(1, ("rule_rank_norm",),
                             hashlib.sha256(b"rule_rank_norm").hexdigest()[:16])


def _finite(value, name):
    if type(value) not in (int, float) or not math.isfinite(value):
        raise ValueError(name + " must be finite")
    return value


def rank_candidates(candidates):
    """Use the exact same anchored epsilon batches as training/parity evaluation."""
    count = len(candidates)
    if not count:
        return []
    rows = [CandidateRow("test", "group", candidate["planKey"], candidate.get("survival"),
                         (candidate["ruleRank"] / count,)) for candidate in candidates]
    indices = rank_indices(rows, [candidate["score"] for candidate in candidates], _RANK_SCHEMA)
    return [candidates[index] for index in indices]


def _group_result(group):
    for field in ("groupId", "app", "screenFamily"):
        nonempty_string(group.get(field), field)
    if group.get("split") not in ("test", "real-test") or group.get("source") not in ("fixture", "real"):
        raise ValueError("Evaluation accepts test fixtures and independent real-test groups only")
    if (group["source"] == "real") != (group["split"] == "real-test"):
        raise ValueError("Real groups must remain in real-test")
    candidates = group.get("candidates")
    if not isinstance(candidates, list) or type(group.get("candidateCount")) is not int or len(candidates) != group["candidateCount"]:
        raise ValueError("Missing/extra candidates in evaluation group")
    keys, ranks = set(), set()
    for candidate in candidates:
        nonempty_string(candidate.get("planKey"), "planKey")
        if candidate["planKey"] in keys:
            raise ValueError("Duplicate evaluation candidate")
        keys.add(candidate["planKey"])
        rank = candidate.get("ruleRank")
        if type(rank) is not int or rank < 0 or rank in ranks:
            raise ValueError("Rule ranks must be unique zero-based integers")
        ranks.add(rank)
        if candidate.get("family") not in FAMILIES:
            raise ValueError("Unknown plan family")
        if not 0 <= _finite(candidate.get("survival"), "survival") <= 1:
            raise ValueError("survival must be within [0, 1]")
        _finite(candidate.get("score"), "score")
        failed = candidate.get("failedStates", [])
        if not isinstance(failed, list) or any(not isinstance(state, dict) or not isinstance(state.get("mutations"), list) for state in failed):
            raise ValueError("failedStates must contain mutation lists")
        for state in failed:
            for mutation in state["mutations"]:
                nonempty_string(mutation, "failed mutation")
    if ranks != set(range(len(candidates))):
        raise ValueError("Missing rule rank in evaluation group")
    mutations = group.get("mutations", [])
    if not isinstance(mutations, list):
        raise ValueError("Group mutations must be a list")
    for mutation in mutations:
        nonempty_string(mutation, "mutation")
    common = {field: group[field] for field in ("groupId", "split", "source", "app", "screenFamily")}
    common["mutations"] = sorted(set(mutations))
    if not candidates:
        return {**common, "metrics": None, "family": "<no-candidate>", "failure": None}
    ranked = rank_candidates(candidates)
    top = ranked[0]
    rule = next(candidate for candidate in candidates if candidate["ruleRank"] == 0)
    best = max(candidate["survival"] for candidate in candidates)
    metrics = {
        "top1Survival": top["survival"], "ruleTop1Survival": rule["survival"],
        "regret": best - top["survival"], "perfectRate": float(top["survival"] == 1),
        "worseThanRule": float(top["survival"] < rule["survival"] - 0.01),
        "top3Recall": float(any(candidate["survival"] == best for candidate in ranked[:3])),
    }
    failure = None
    if top["survival"] < rule["survival"]:
        failure = {**common, "modelPlanKey": top["planKey"], "rulePlanKey": rule["planKey"],
                   "modelSurvival": top["survival"], "ruleSurvival": rule["survival"],
                   "loss": rule["survival"] - top["survival"],
                   "modelFailedMutations": sorted({mutation for state in top.get("failedStates", []) for mutation in state["mutations"]}),
                   "ruleFailedMutations": sorted({mutation for state in rule.get("failedStates", []) for mutation in state["mutations"]})}
    return {**common, "metrics": metrics, "family": top["family"], "failure": failure}


def summarize(results):
    scored = [result["metrics"] for result in results if result["metrics"] is not None]
    return {"groups": len(scored), "totalGroups": len(results),
            "groupsWithoutCandidates": len(results) - len(scored),
            "coverage": len(scored) / len(results) if results else None,
            **{metric: math.fsum(result[metric] for result in scored) / len(scored) if scored else None for metric in METRICS}}


def evaluate_groups(groups):
    """Pure evaluator for checked prediction records, including explicit no-candidate groups."""
    groups = list(groups)
    group_ids = [group.get("groupId") for group in groups]
    if any(not isinstance(group_id, str) for group_id in group_ids) or len(set(group_ids)) != len(group_ids):
        raise ValueError("Invalid/duplicate evaluation groupId")
    results = [_group_result(group) for group in sorted(groups, key=lambda group: group["groupId"])]
    report = {"format": 1, "metricDefinition": "observed-survival-v1", "breakdowns": {}}
    for split, name in (("test", "test"), ("real-test", "realTest")):
        selected = [result for result in results if result["split"] == split]
        report[name] = summarize(selected)
        breakdowns = {}
        for field in ("family", "screenFamily", "source", "mutation"):
            buckets = defaultdict(list)
            for result in selected:
                values = (result["mutations"] or ["<none>"]) if field == "mutation" else [result[field]]
                for value in values:
                    buckets[value].append(result)
            breakdowns[field] = {key: summarize(value) for key, value in sorted(buckets.items())}
        report["breakdowns"][name] = breakdowns
    failures = sorted((result["failure"] for result in results if result["failure"] is not None),
                      key=lambda failure: (-failure["loss"], failure["groupId"], failure["modelPlanKey"], failure["rulePlanKey"]))
    report["failureGroups"] = len(failures)
    report["failures"] = failures[:50]
    report["notes"] = [
        "groups counts scorable groups; totalGroups/coverage include groups with no valid candidates.",
        "Empty sets have null metrics, not a success score.",
        "Family breakdown uses the model's selected plan family; baseline is the original group's rule selection.",
        "A group appears in each of its evaluation-state mutation buckets; mutation buckets may overlap.",
        "Scores within 1e-6 of a descending batch maximum share a batch, ordered by rule rank.",
    ]
    return report


def predict(model_path, rows, schema):
    """Use named CatBoost Pool columns and raw scores; no Python feature recomputation."""
    from catboost import CatBoostRanker, Pool

    model = CatBoostRanker()
    model_path = Path(model_path)
    model.load_model(str(model_path), format="json" if model_path.suffix.lower() == ".json" else "cbm")
    if tuple(model.feature_names_) != tuple(schema.names):
        raise ValueError("Model feature names/order do not match features.json")
    if not rows:
        return []
    pool = Pool([list(row.values) for row in rows], feature_names=list(schema.names))
    # CatBoostRanker returns raw ranking scores; unlike the base CatBoost class,
    # its actual Python signature does not accept prediction_type.
    values = model.predict(pool, thread_count=1)
    scores = [float(value) for value in values]
    if len(scores) != len(rows) or any(not math.isfinite(score) for score in scores):
        raise ValueError("Model predictions must be finite, one per candidate")
    return scores


def evaluation_records(dataset, rows, scores):
    if len(rows) != len(scores):
        raise ValueError("Missing/extra candidate predictions")
    tasks, task_hashes, snapshot_hashes, cached_hashes = {}, [], [], {}
    for group_id, metadata in sorted(dataset.groups.items()):
        task_path = Path(metadata["task"])
        if not task_path.is_absolute():
            task_path = dataset.root / task_path
        task, snapshots = load_group(task_path)
        if task["groupId"] != group_id:
            raise ValueError("Task identity differs from split metadata")
        tasks[group_id] = task
        task_hashes.append({"groupId": group_id, "sha256": sha256(task_path)})
        for state, snapshot in zip(task["states"], snapshots):
            if snapshot not in cached_hashes:
                cached_hashes[snapshot] = sha256(snapshot)
            snapshot_hashes.append({"groupId": group_id, "stateId": state["stateId"], "sha256": cached_hashes[snapshot]})
    # Labels/feature bytes alone cannot prove that a maintainer has not since
    # reselected targets or replaced a layout under the same group/state IDs.
    # Match split.build_dataset's canonical digests, including original state order.
    for name, hashes in (("tasks", task_hashes), ("snapshots", snapshot_hashes)):
        expected = dataset.manifest.get("artifacts", {}).get(name, {}).get("sha256")
        if expected != content_hash(hashes):
            raise ValueError("Current " + name + " differ from the frozen dataset manifest; regenerate labels and features")
    labels_path, rules_path = dataset.artifact_path("labels"), dataset.artifact_path("rules")
    labels, counts = validate_labels(list(tasks.values()), labels_path, rules_path)
    rules = {row["groupId"]: row for row in _rule_rows(rules_path)}
    failures = {}
    with labels_path.open(encoding="utf-8-sig") as source:
        for line in source:
            if line.strip():
                row = parse_json(line)
                failures[(row["groupId"], row["planKey"])] = row["failedStates"]
    selected_groups = {group_id for group_id, metadata in dataset.groups.items() if metadata["split"] in ("test", "real-test")}
    expected = {key for key in labels if key[0] in selected_groups}
    seen, grouped = set(), defaultdict(list)
    rank_column = dataset.schema.names.index("rule_rank_norm")
    for row, score in zip(rows, scores):
        key = (row.group_id, row.plan_key)
        if key not in expected or key in seen:
            raise ValueError("Unknown/duplicate prediction candidate")
        seen.add(key)
        _finite(score, "score")
        if abs(labels[key]["survival"] - row.survival) > 1e-9:
            raise ValueError("Feature survival differs from labels")
        grouped[row.group_id].append({"planKey": row.plan_key, "family": labels[key]["family"],
                                      "survival": row.survival, "score": score,
                                      "ruleRankNorm": row.values[rank_column], "failedStates": failures[key]})
    if seen != expected:
        raise ValueError("Missing prediction candidates")
    records = []
    for group_id in sorted(selected_groups):
        task, metadata = tasks[group_id], dataset.groups[group_id]
        candidates = grouped[group_id]
        if len({candidate["ruleRankNorm"] for candidate in candidates}) != len(candidates):
            raise ValueError("Duplicate rule rank in features")
        for rank, candidate in enumerate(sorted(candidates, key=lambda item: item["ruleRankNorm"])):
            candidate["ruleRank"] = rank
        for candidate in candidates:
            if candidate["ruleRank"] == 0:
                # validate_labels has already checked the original RuleRanker selection.
                with_rule = rules.get(group_id)
                if with_rule is None or with_rule["rulePlanKey"] != candidate["planKey"]:
                    raise ValueError("Feature rule rank differs from exported RuleRanker selection")
        mutations = sorted({mutation for index, state in enumerate(task["states"])
                            if index != task.get("captureState", 0) for mutation in state["mutations"]})
        records.append({"groupId": group_id, "split": metadata["split"], "source": task["source"],
                        "app": task["app"], "screenFamily": task["screenFamily"], "mutations": mutations,
                        "candidateCount": counts[group_id], "candidates": candidates})
    return records


def _rule_rows(path):
    with Path(path).open(encoding="utf-8-sig") as source:
        for line in source:
            if line.strip():
                yield parse_json(line)


def markdown_report(report):
    def cell(value):
        return "n/a" if value is None else f"{value:.6f}" if isinstance(value, float) else str(value)

    def escape(value):
        return str(value).replace("|", "\\|").replace("\n", " ").replace("\r", " ")

    lines = ["# Code-generation ranker evaluation", "", f"Dataset: `{escape(report.get('datasetId', 'unspecified'))}`", "",
             "Metrics use held-out observed survival, not predicted success probabilities.", "",
             "| Split | Scorable / total | Coverage | " + " | ".join(METRICS) + " |",
             "| --- | --- | --- | " + " | ".join("---" for _ in METRICS) + " |"]
    for name in ("test", "realTest"):
        data = report[name]
        lines.append(f"| {name} | {data['groups']} / {data['totalGroups']} | {cell(data['coverage'])} | "
                     + " | ".join(cell(data[metric]) for metric in METRICS) + " |")
    for split, breakdowns in report["breakdowns"].items():
        for field, buckets in breakdowns.items():
            lines += ["", f"## {split}: {field}", "", "| Bucket | Scorable / total | Model survival | Rule survival | Regret |",
                      "| --- | --- | --- | --- | --- |"]
            for key, data in buckets.items():
                lines.append(f"| {escape(key)} | {data['groups']} / {data['totalGroups']} | {cell(data['top1Survival'])} | "
                             f"{cell(data['ruleTop1Survival'])} | {cell(data['regret'])} |")
    lines += ["", "## Groups where the model is worse than the rule baseline", "",
              f"Total: {report['failureGroups']}; showing at most 50, largest survival loss first.", "",
              "| Group | Model plan | Rule plan | Model / rule survival | Model failed mutations |",
              "| --- | --- | --- | --- | --- |"]
    for failure in report["failures"]:
        lines.append(f"| {escape(failure['groupId'])} | {escape(failure['modelPlanKey'])} | {escape(failure['rulePlanKey'])} | "
                     f"{cell(failure['modelSurvival'])} / {cell(failure['ruleSurvival'])} | {escape(', '.join(failure['modelFailedMutations']))} |")
    lines += ["", *["- " + note for note in report["notes"]], ""]
    return "\n".join(lines)


def run_evaluation(dataset_dir, model_path, output):
    dataset = read_dataset(dataset_dir)
    output = Path(output).resolve()
    model_path = Path(model_path).resolve()
    if model_path in {output / "metrics.json", output / "report.md"}:
        raise ValueError("Evaluation output would overwrite the model")
    if any(metadata["source"] == "real" for metadata in dataset.groups.values()):
        private_path(dataset.root)
        private_path(output)
    rows = dataset.rows_for("test", "real-test")
    model_hash = sha256(model_path)
    scores = predict(model_path, rows, dataset.schema)
    report = evaluate_groups(evaluation_records(dataset, rows, scores))
    if sha256(model_path) != model_hash:
        raise ValueError("Model changed during evaluation")
    for name in ("features", "labels", "rules", "splitMap"):
        if sha256(dataset.artifact_path(name)) != dataset.hashes[name]:
            raise ValueError("Dataset changed during evaluation: " + name)
    report.update({"datasetId": dataset.manifest["datasetId"], "modelSha256": model_hash,
                   "featureSchemaVersion": dataset.schema.version, "featureNamesHash": dataset.schema.names_hash,
                   "datasetHashes": dataset.hashes})
    output.mkdir(parents=True, exist_ok=True)
    write_json(output / "metrics.json", report)
    temporary = output / "report.md.tmp"
    temporary.write_text(markdown_report(report), encoding="utf-8", newline="\n")
    temporary.replace(output / "report.md")
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        report = run_evaluation(args.dataset, args.model, args.output)
    except (ValueError, OSError, ImportError) as error:
        parser.exit(2, str(error) + "\n")
    print(json.dumps({"test": report["test"], "realTest": report["realTest"]}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
