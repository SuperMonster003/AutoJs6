#!/usr/bin/env python3
"""Fit an offline numeric construction policy from Kotlin state/action exports.

The default target is the observed mean of completed-rollout survival; a format 2
export also offers the best observed continuation (``--label best``). Neither is
the exact optimal continuation value, and unknown or unfinished continuations are
not zeroes. This tool emits an experimental policy; it does not build/sign/publish
a ranker package or consume held-out labels for fitting, early stopping, or tuning.
"""

import argparse
from collections import Counter
from copy import deepcopy
from datetime import datetime, timezone
import importlib.metadata
import json
import math
import os
from pathlib import Path
import platform
import shutil
import sys
import tempfile
import time

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from construction.data import read_dataset
from dataset.task_io import private_path, read_json, write_json
from package.build_package import decode_model, encode_model, score_model
from train.data import FeatureSchema as NumericSchema, sha256
from train.train import PINNED_VERSIONS, canonical_model_json


DEFAULT_SEED = 20261007
SCORE_SEMANTICS = "observed-rollout-survival-v1"
BEST_SCORE_SEMANTICS = "best-observed-continuation-v1"
# Label column -> (declared score semantics, meaning recorded in the training log).
LABELS = {
    "mean": (SCORE_SEMANTICS,
             "Mean survival of completed observed rollouts; not an exact optimal value; unknown actions omitted"),
    "best": (BEST_SCORE_SEMANTICS,
             "Maximum survival among completed observed rollouts through the state/action; "
             "an observed lower bound of the best continuation, not an exact optimal value; unknown actions omitted"),
}
SCORE_TOLERANCE = 1e-4
TIE_EPSILON = 1e-6


def training_parameters(*, iterations=256, depth=6, seed=DEFAULT_SEED, early_stopping_rounds=32):
    for value, name, maximum in ((iterations, "iterations", 256), (depth, "depth", 6),
                                 (early_stopping_rounds, "early-stopping-rounds", 256)):
        if type(value) is not int or not 1 <= value <= maximum:
            raise ValueError(f"{name} must be between 1 and {maximum}")
    if type(seed) is not int or not 0 <= seed <= 2**31 - 1:
        raise ValueError("seed must be a nonnegative 32-bit integer")
    return {"iterations": iterations, "depth": depth, "learning_rate": 0.05,
            "loss_function": "RMSE", "eval_metric": "RMSE", "grow_policy": "SymmetricTree",
            "boosting_type": "Plain", "random_seed": seed, "thread_count": 1, "task_type": "CPU",
            "use_best_model": True, "od_type": "Iter", "od_wait": early_stopping_rounds,
            "allow_writing_files": False, "logging_level": "Silent"}


def _dependencies():
    if sys.version_info[:2] != (3, 12):
        raise ValueError("Construction training is pinned to Python 3.12")
    versions = {}
    for name, expected in PINNED_VERSIONS.items():
        try:
            versions[name] = importlib.metadata.version(name)
        except importlib.metadata.PackageNotFoundError as error:
            raise ValueError("Install train/requirements.txt before construction training") from error
        if versions[name] != expected:
            raise ValueError(f"Expected {name}=={expected}, found {versions[name]}")
    return versions


def _output_path(dataset, output):
    output = Path(output).resolve()
    if output.exists():
        raise ValueError("Training output already exists; choose a fresh directory")
    if output == dataset.root or output.is_relative_to(dataset.root):
        raise ValueError("Training output must be separate from the frozen dataset")
    if any(group["source"] == "real" for group in dataset.groups.values()):
        return private_path(output)
    ignored = (Path(__file__).resolve().parents[1] / "out").resolve()
    if not output.is_relative_to(ignored):
        if any((ancestor / ".git").exists() for ancestor in (output, *output.parents)):
            raise ValueError("Construction outputs belong under ignored tools/codegen-model/out or outside Git")
    return output


def export_policy(canonical, schema, score_semantics=SCORE_SEMANTICS):
    """Reuse the checked numeric tree codec in memory; never label this as an .ajcgm ranker."""
    if score_semantics not in {semantics for semantics, _ in LABELS.values()}:
        raise ValueError("Unknown construction score semantics")
    adjusted = deepcopy(canonical)
    trees = adjusted.get("oblivious_trees")
    if not isinstance(trees, list) or not 1 <= len(trees) <= 256:
        raise ValueError("Construction policy requires 1..256 symmetric trees")
    constant_trees = 0
    for tree in trees:
        if not isinstance(tree, dict):
            raise ValueError("Construction tree must be an object")
        splits, leaves = tree.get("splits"), tree.get("leaf_values")
        if splits is None or splits == []:
            if not isinstance(leaves, list) or len(leaves) != 1:
                raise ValueError("A constant tree requires one leaf")
            # The host interpreter requires depth >= 1. Identical leaves keep
            # the constant exactly; do not fold it into bias and reorder sums.
            tree["splits"] = [{"split_type": "FloatFeature", "float_feature_index": 0,
                               "border": 0.0, "split_index": 0}]
            tree["leaf_values"] = [leaves[0], leaves[0]]
            constant_trees += 1
        elif not isinstance(splits, list) or len(splits) > 6:
            raise ValueError("Construction tree depth must not exceed 6")
    numeric = NumericSchema(schema.version, schema.names, schema.names_hash)
    decoded = decode_model(encode_model(adjusted, numeric))
    model = {"depths": [], "splitOffsets": [], "leafOffsets": [], "featureIndices": [],
             "thresholds": [], "leafValues": [], "bias": decoded.bias, "scale": decoded.scale}
    for tree in decoded.trees:
        model["depths"].append(len(tree.features))
        model["splitOffsets"].append(len(model["featureIndices"]))
        model["leafOffsets"].append(len(model["leafValues"]))
        model["featureIndices"].extend(tree.features)
        model["thresholds"].extend(tree.borders)
        model["leafValues"].extend(tree.leaves)
    policy = {"format": 1, "role": "construction-policy", "grammarVersion": schema.grammar_version,
              "featureSchemaVersion": schema.version, "featureNamesHash": schema.names_hash,
              "featureCount": len(schema.names), "scoreSemantics": score_semantics, "model": model}
    return policy, decoded, constant_trees


def select_parity_rows(rows, maximum):
    """Keep complete observed action groups, never a truncated prefix of a state."""
    if type(maximum) is not int or maximum <= 0:
        raise ValueError("parity-max-rows must be a positive integer")
    groups = {}
    for row in rows:
        groups.setdefault((row.group_id, row.state_key), []).append(row)
    result = []
    for group in groups.values():
        if len(result) + len(group) <= maximum:
            result.extend(group)
    if not result:
        raise ValueError("No complete observed action group fits parity-max-rows")
    return tuple(result)


def action_order(rows, scores):
    """Exact sorting followed by disjoint max-anchored near-tie batches."""
    if (len(rows) != len(scores) or any(not math.isfinite(score) for score in scores)
            or len({(row.group_id, row.state_key) for row in rows}) > 1):
        raise ValueError("Rank one state with one finite score per action")
    order = sorted(range(len(rows)), key=lambda index: (-scores[index], rows[index].action_key))
    result, start = [], 0
    while start < len(order):
        end = start + 1
        while end < len(order) and scores[order[start]] - scores[order[end]] <= TIE_EPSILON:
            end += 1
        result.extend(sorted(order[start:end], key=lambda index: rows[index].action_key))
        start = end
    return tuple(result)


def parity_records(rows, scores, decoded):
    if len(rows) != len(scores) or any(not math.isfinite(score) for score in scores):
        raise ValueError("Every parity row requires one finite CatBoost score")
    binary_scores = [score_model(decoded, row.values) for row in rows]
    difference = max((abs(a - b) for a, b in zip(scores, binary_scores)), default=0.0)
    if difference > SCORE_TOLERANCE:
        raise ValueError("Float32 policy scores exceed CatBoost parity tolerance")
    states = {}
    for index, row in enumerate(rows):
        states.setdefault((row.group_id, row.state_key), []).append(index)
    for indices in states.values():
        actions = [rows[index] for index in indices]
        if action_order(actions, [scores[index] for index in indices]) != action_order(
                actions, [binary_scores[index] for index in indices]):
            raise ValueError("Float32 policy changes action ordering under the declared tie rule")
    records = [{"format": 1, "split": row.split, "groupId": row.group_id, "stateKey": row.state_key,
                "actionKey": row.action_key, "features": list(row.values), "expectedScore": float(score)}
               for row, score in zip(rows, scores)]
    return records, {"parityRows": len(rows), "parityStates": len(states),
                     "maxPolicyScoreDifference": difference, "sameActionOrder": True,
                     "scoreTolerance": SCORE_TOLERANCE, "tieEpsilon": TIE_EPSILON}


def row_label(row, label):
    if label == "mean":
        return row.label
    if label == "best":
        if row.label_best is None:
            raise ValueError("The best observed continuation label requires a format 2 construction export")
        return row.label_best
    raise ValueError("label must be mean or best")


def fit_dataset(dataset, output, *, iterations=256, depth=6, seed=DEFAULT_SEED,
                early_stopping_rounds=32, parity_max_rows=5000, label="mean"):
    if label not in LABELS:
        raise ValueError("label must be mean or best")
    score_semantics, label_meaning = LABELS[label]
    parameters = training_parameters(iterations=iterations, depth=depth, seed=seed,
                                     early_stopping_rounds=early_stopping_rounds)
    output = _output_path(dataset, output)
    # Real-train applications join fitting (roadmap D25); early stopping stays on fixture valid.
    fixture_rows, real_rows = dataset.rows_for("train"), dataset.rows_for("real-train")
    train_rows, valid_rows = fixture_rows + real_rows, dataset.rows_for("valid")
    if not fixture_rows or not valid_rows:
        raise ValueError("Both train and valid must contain observed action rows")
    fitted_splits = tuple((split, rows) for split, rows in (("train", fixture_rows), ("real-train", real_rows), ("valid", valid_rows)) if rows)
    # Resolve every fitted label before any dependency is imported or any file is written.
    for row in (*train_rows, *valid_rows):
        row_label(row, label)
    if any(dataset.groups[row.group_id]["source"] != ("real" if dataset.groups[row.group_id]["split"] == "real-train" else "fixture")
           for row in (*train_rows, *valid_rows)):
        raise ValueError("Only real-train application data may participate in fitting; early stopping uses fixtures")
    # Parity uses train/valid vectors only. Even parity sampling cannot expose
    # held-out labels to fitting or influence selected hyperparameters.
    parity_rows = select_parity_rows((*train_rows, *valid_rows), parity_max_rows)
    dataset.check_unchanged()
    versions = _dependencies()
    import catboost
    import numpy

    def pool(rows, labelled=True):
        # Allocate one input matrix, then copy compact Float32 rows. This does
        # not materialize a nested Python float list or a Float64 matrix.
        values = numpy.empty((len(rows), len(dataset.schema.names)), dtype=numpy.float32)
        for index, row in enumerate(rows):
            values[index] = row.values
        return catboost.Pool(values,
                             label=[row_label(row, label) for row in rows] if labelled else None,
                             feature_names=list(dataset.schema.names))

    model = catboost.CatBoostRegressor(**parameters)
    started_at = datetime.now(timezone.utc).isoformat(timespec="seconds")
    started = time.perf_counter()
    model.fit(pool(train_rows), eval_set=pool(valid_rows))
    elapsed = time.perf_counter() - started
    dataset.check_unchanged()

    output.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix="." + output.name + "-", dir=output.parent)).resolve()
    try:
        model.save_model(str(staging / "model.cbm"))
        model.save_model(str(staging / "model.raw.json"), format="json")
        canonical, removed = canonical_model_json(read_json(staging / "model.raw.json"))
        (staging / "model.json").write_text(
            json.dumps(canonical, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n",
            encoding="utf-8", newline="\n")
        policy, decoded, constant_trees = export_policy(canonical, dataset.schema, score_semantics)
        records, parity = parity_records(
            parity_rows, model.predict(pool(parity_rows, labelled=False), prediction_type="RawFormulaVal"), decoded)
        write_json(staging / "policy.json", policy)
        with (staging / "parity.jsonl").open("w", encoding="utf-8", newline="\n") as stream:
            for record in records:
                stream.write(json.dumps(record, ensure_ascii=False, allow_nan=False, separators=(",", ":")) + "\n")
        shutil.copyfile(dataset.root / "features.json", staging / "features.json")
        dataset.check_unchanged()
        artifacts = {name: {"sha256": sha256(staging / name), "bytes": (staging / name).stat().st_size}
                     for name in ("model.cbm", "model.raw.json", "model.json", "policy.json", "parity.jsonl", "features.json")}
        log = {
            "format": 1, "role": "construction-policy", "datasetId": dataset.manifest["datasetId"],
            "grammarVersion": dataset.schema.grammar_version, "featureSchemaVersion": dataset.schema.version,
            "featureNamesHash": dataset.schema.names_hash, "featureCount": len(dataset.schema.names),
            "scoreSemantics": score_semantics, "labelSelection": label, "exportFormat": dataset.export_format,
            "parameters": parameters,
            "resolvedParameters": model.get_all_params(),
            "splitsUsed": ["train", "real-train", "valid"] if real_rows else ["train", "valid"],
            "paritySplits": ["train", "valid"], "weightPolicy": "uniform-state-action-row",
            "observationsUsedAsWeight": False,
            "labelMeaning": label_meaning,
            "actions": {split: len(rows) for split, rows in fitted_splits},
            "groups": {split: len({row.group_id for row in rows}) for split, rows in fitted_splits},
            "observationCountHistogram": {split: dict(sorted(Counter(str(row.observations) for row in rows).items()))
                                          for split, rows in fitted_splits},
            "inputSha256": dataset.hashes, "exportMetadata": dataset.manifest,
            "startedAt": started_at, "completedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "elapsedSeconds": round(elapsed, 6), "treeCount": model.tree_count_,
            "maximumTreeDepth": max(policy["model"]["depths"]), "bestIteration": model.get_best_iteration(),
            "bestScore": model.get_best_score(), "evalsResult": model.get_evals_result(),
            "versions": {"python": platform.python_version(), **versions},
            "machine": {"system": platform.system(), "release": platform.release(),
                        "machine": platform.machine(), "processor": platform.processor(), "logicalCpuCount": os.cpu_count()},
            "jsonNormalization": {"removedFields": removed, "numericValuesModified": False,
                                  "original": "model.raw.json", "serialization": "UTF-8, sorted object keys, indent 2, LF"},
            "policyConversion": {"thresholdsAndLeaves": "Float32", "biasAndScale": "Float64",
                                 "constantTreesExpandedToIdenticalLeaves": constant_trees},
            "modelFile": "policy.json", "modelSha256": artifacts["policy.json"]["sha256"],
            "parityFile": "parity.jsonl", "paritySha256": artifacts["parity.jsonl"]["sha256"],
            **parity, "artifacts": artifacts,
        }
        write_json(staging / "train-log.json", log)
        # Exclusive destination creation rejects a concurrent publisher on all
        # platforms. The manifest is moved last; incomplete directories cannot
        # be consumed as successful runs and are intentionally not overwritten.
        output.mkdir()
        for name in (*artifacts, "train-log.json"):
            (staging / name).rename(output / name)
        return log
    finally:
        if staging.exists():
            if not staging.is_relative_to(output.parent.resolve()) or staging == output.parent.resolve():
                raise ValueError("Refusing cleanup outside the construction output parent")
            shutil.rmtree(staging)


def train(dataset_dir, output_dir, **options):
    return fit_dataset(read_dataset(dataset_dir), output_dir, **options)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset-dir", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--iterations", type=int, default=256)
    parser.add_argument("--depth", type=int, default=6)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--early-stopping-rounds", type=int, default=32)
    parser.add_argument("--parity-max-rows", type=int, default=5000)
    parser.add_argument("--label", choices=sorted(LABELS), default="mean",
                        help="supervised target column: mean observed return (default) or best observed continuation (format 2)")
    args = parser.parse_args(argv)
    try:
        log = train(args.dataset_dir, args.output_dir, iterations=args.iterations, depth=args.depth, seed=args.seed,
                    early_stopping_rounds=args.early_stopping_rounds, parity_max_rows=args.parity_max_rows,
                    label=args.label)
    except (ValueError, OSError, ImportError) as error:
        parser.exit(2, str(error) + "\n")
    print(json.dumps({key: log[key] for key in ("datasetId", "labelSelection", "scoreSemantics", "treeCount",
                                                 "bestIteration", "elapsedSeconds", "parityRows")},
                     ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
