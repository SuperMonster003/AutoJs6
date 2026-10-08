"""Admit the merged rule + construction pipeline from preregistered development results only.

The candidate method ranks the union of the original rule search's candidates and
the frozen construction policy's candidates with a ranker refitted on that merged
distribution. It is admitted to one held-out assessment only when its validation
top1 survival is not below the complete rule pipeline, the original search with
the frozen tiny ranker, or the original search with the refitted ranker, when it
strictly improves top1 or top3 over the refitted-ranker baseline on train or
valid (so construction itself contributes), and when every development run of
every method completes without an error or deadline stop. This is an experiment
admission check, never the model publication gate.
"""

import argparse
import hashlib
import json
import math
from pathlib import Path


MEASURES = ("coverage", "bestSurvival", "top1Survival", "top3BestSurvival")
BAD_STOPS = {"FAILED", "HARD_DEADLINE", "DEADLINE", "POLICY_FAILURE"}


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def _object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON field: {key}")
        result[key] = value
    return result


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"), object_pairs_hook=_object)


def decide(protocol_dir, development_dir):
    protocol_dir, development_dir = Path(protocol_dir), Path(development_dir)
    protocol_file = protocol_dir / "protocol.json"
    metrics_file = development_dir / "metrics.json"
    results_file = development_dir / "results.jsonl"
    protocol, metrics = read_json(protocol_file), read_json(metrics_file)
    if protocol.get("role") != "construction-fourth-experiment":
        raise ValueError("Expected a fourth construction experiment protocol")
    if metrics["phase"] != "develop" or metrics["sourceSha256"] != protocol["sourceSha256"]:
        raise ValueError("Expected development metrics from the frozen sources")
    if metrics["protocolSha256"] != digest(protocol_file):
        raise ValueError("Development results belong to a different frozen protocol")
    if metrics["resultsSha256"] != digest(results_file):
        raise ValueError("Development results digest changed")
    methods = tuple(protocol["methods"])
    criteria = protocol["selectionCriteria"]
    chosen = criteria["chosenMethod"]
    references = tuple(criteria["noRegressionAgainst"])
    gain_reference = criteria["strictGainAgainst"]
    if (chosen not in methods or not references or not set(references) <= set(methods) or chosen in references
            or gain_reference not in methods or gain_reference == chosen or tuple(criteria["metrics"]) != MEASURES
            or criteria["noRegressionMeasure"] != "top1Survival" or tuple(criteria["strictGainMeasures"]) != ("top1Survival", "top3BestSurvival")):
        raise ValueError("Protocol selection criteria differ from this tool")
    expected = {}
    for group in protocol["developmentGroups"]:
        group_id, split = group["groupId"], group["split"]
        if group_id in expected or split not in ("train", "valid"):
            raise ValueError("Development membership must be unique train/valid groups")
        expected[group_id] = split
    if set(expected.values()) != {"train", "valid"}:
        raise ValueError("Both train and valid groups are required")

    rows = {}
    with results_file.open(encoding="utf-8") as stream:
        for line in stream:
            row = json.loads(line, object_pairs_hook=_object)
            group_id, method = row["groupId"], row["method"]
            if group_id not in expected or row["split"] != expected[group_id]:
                raise ValueError("Held-out or unknown group in development results")
            if row["cohort"] != "captured-development" or method not in methods:
                raise ValueError("Unexpected development cohort or method")
            key = (group_id, method)
            if key in rows:
                raise ValueError("Duplicate group/method result")
            for measure in MEASURES:
                value = row[measure]
                if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not 0 <= value <= 1:
                    raise ValueError(f"Invalid {measure}")
            if row["coverage"] not in (0, 1) or row["top1Survival"] > row["bestSurvival"] or row["top3BestSurvival"] > row["bestSurvival"]:
                raise ValueError("Inconsistent candidate quality")
            if row["coverage"] == 0 and any(row[k] != 0 for k in MEASURES[1:]):
                raise ValueError("Zero-candidate groups must have zero quality")
            rows[key] = row
    if set(rows) != {(group_id, method) for group_id in expected for method in methods}:
        raise ValueError("Missing group/method results; failures cannot be dropped")

    means = {}
    for split in ("train", "valid"):
        group_ids = [group_id for group_id, member_split in expected.items() if member_split == split]
        for method in methods:
            means[(split, method)] = {
                measure: math.fsum(rows[group_id, method][measure] for group_id in group_ids) / len(group_ids)
                for measure in MEASURES
            }
    reasons = []
    for reference in references:
        if means["valid", chosen]["top1Survival"] < means["valid", reference]["top1Survival"]:
            reasons.append(f"Validation top1Survival regressed against {reference}")
    if not any(means[split, chosen][measure] > means[split, gain_reference][measure]
               for split in ("train", "valid") for measure in ("top1Survival", "top3BestSurvival")):
        reasons.append(f"No strict top1/top3 improvement over {gain_reference} in train or valid")
    failures = sum(row.get("error") is not None or row["stopReason"] in BAD_STOPS for row in rows.values())
    if failures:
        reasons.append(f"Development includes {failures} failed or deadline-limited runs")
    return {
        "format": 1, "chosenMethod": chosen, "noRegressionAgainst": list(references), "strictGainAgainst": gain_reference,
        "passed": not reasons,
        "protocolSha256": digest(protocol_file), "developmentMetricsSha256": digest(metrics_file),
        "developmentResultsSha256": digest(results_file), "sourceSha256": protocol["sourceSha256"],
        "reasons": reasons,
        "comparison": [{"split": split, "method": method, **means[split, method]}
                       for split in ("train", "valid") for method in methods],
        "scope": "Admission of the merged pipeline to a fresh held-out experiment only; not a publication decision",
    }


def select(protocol_dir, development_dir):
    decision = decide(protocol_dir, development_dir)
    output = Path(protocol_dir) / "selection.json"
    with output.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(decision, stream, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False)
        stream.write("\n")
    return decision


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--protocol-dir", type=Path, required=True)
    parser.add_argument("--development-dir", type=Path, required=True)
    args = parser.parse_args()
    decision = select(args.protocol_dir, args.development_dir)
    print(json.dumps(decision, ensure_ascii=False, indent=2))
    return 0 if decision["passed"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
