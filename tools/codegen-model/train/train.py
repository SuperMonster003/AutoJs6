#!/usr/bin/env python3
"""Train a small, reproducible CPU ranker from the Kotlin numeric export.

Run with Python 3.12 and the exact dependencies in this directory's
requirements.txt. No snapshots are read and no numeric features are recomputed.
"""

import argparse
from collections import Counter
from copy import deepcopy
from datetime import datetime, timezone
import importlib.metadata
import json
import os
from pathlib import Path
import platform
import shutil
import sys
import tempfile
import time

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from dataset.task_io import private_path, read_json, write_json
from train.data import read_dataset, sha256, survival_grade


PINNED_VERSIONS = {"catboost": "1.2.10", "numpy": "2.2.6"}
DEFAULT_SEED = 20261003
VOLATILE_MODEL_INFO = ("model_guid", "train_finish_time")


def training_parameters(iterations=1024, seed=DEFAULT_SEED, early_stopping_rounds=64):
    if type(iterations) is not int or not 1 <= iterations <= 1024:
        raise ValueError("iterations must be between 1 and 1024")
    if type(seed) is not int or not 0 <= seed <= 2**31 - 1:
        raise ValueError("seed must be a nonnegative 32-bit integer")
    if type(early_stopping_rounds) is not int or not 1 <= early_stopping_rounds <= 1024:
        raise ValueError("early-stopping-rounds must be between 1 and 1024")
    return {
        "iterations": iterations,
        "depth": 6,
        "learning_rate": 0.05,
        "loss_function": "YetiRank",
        "eval_metric": "NDCG:top=1",
        "grow_policy": "SymmetricTree",
        "boosting_type": "Plain",
        "random_seed": seed,
        "thread_count": 1,
        "task_type": "CPU",
        "use_best_model": True,
        "od_type": "Iter",
        "od_wait": early_stopping_rounds,
        "allow_writing_files": False,
        "logging_level": "Silent",
    }


def canonical_model_json(value):
    """Remove only CatBoost's two volatile informational fields.

    Numerical splits, borders, leaf values, scale/bias and training options are
    preserved. The original JSON is saved separately for an honest comparison.
    """
    result = deepcopy(value)
    if not isinstance(result, dict) or not isinstance(result.get("model_info"), dict):
        raise ValueError("Invalid CatBoost JSON model_info")
    removed = []
    for field in VOLATILE_MODEL_INFO:
        if field in result["model_info"]:
            del result["model_info"][field]
            removed.append("model_info." + field)
    return result, removed


def _dependencies():
    if sys.version_info[:2] != (3, 12):
        raise ValueError("Training is pinned to Python 3.12; create the documented training environment")
    versions = {}
    for name, expected in PINNED_VERSIONS.items():
        try:
            actual = importlib.metadata.version(name)
        except importlib.metadata.PackageNotFoundError as error:
            raise ValueError("Install train/requirements.txt before training") from error
        if actual != expected:
            raise ValueError(f"Expected {name}=={expected}, found {actual}")
        versions[name] = actual
    return versions


def _validate_model(model, schema, iterations):
    info = model.get("features_info", {})
    if any(info.get(name) for name in ("categorical_features", "text_features", "embedding_features", "ctrs")):
        raise ValueError("The model must contain only numeric features")
    features = info.get("float_features", [])
    if len(features) != len(schema.names):
        raise ValueError("Model feature count differs from the Kotlin schema")
    for index, (feature, name) in enumerate(zip(features, schema.names)):
        if feature.get("flat_feature_index") != index or feature.get("feature_id") != name:
            raise ValueError("Model feature order differs from the Kotlin schema")
    trees = model.get("oblivious_trees")
    if not isinstance(trees, list) or not 1 <= len(trees) <= iterations:
        raise ValueError("Expected between 1 and the requested number of symmetric trees")
    for tree in trees:
        splits = tree.get("splits") or []
        if len(splits) > 6 or len(tree.get("leaf_values", [])) != 2**len(splits):
            raise ValueError("Model contains a non-symmetric or overly deep tree")
        if any(split.get("split_type") != "FloatFeature" for split in splits):
            raise ValueError("Model split must use a numeric feature")
    return max(len(tree.get("splits") or []) for tree in trees)


def _pool(rows, schema, numpy, catboost):
    if not rows:
        raise ValueError("Both train and valid must contain candidate rows")
    values = numpy.asarray([row.values for row in rows], dtype=numpy.float32)
    labels = [survival_grade(row.survival) for row in rows]
    return catboost.Pool(values, label=labels, group_id=[row.group_id for row in rows],
                         feature_names=list(schema.names))


def fit_dataset(dataset, output, *, iterations=1024, seed=DEFAULT_SEED, early_stopping_rounds=64):
    """Publish a new run directory only after every validation and fit succeeds."""
    parameters = training_parameters(iterations, seed, early_stopping_rounds)
    output = Path(output).resolve()
    if output.exists():
        raise ValueError("Training output already exists; use a fresh run directory")
    if output == dataset.root or output.is_relative_to(dataset.root):
        raise ValueError("Training output must be separate from the frozen dataset")
    if any(group["source"] == "real" for group in dataset.groups.values()):
        private_path(output)
    # Real-train applications join fitting (roadmap D25); early stopping stays on fixture valid,
    # and real-test applications never reach this point.
    fixture_rows, real_rows = dataset.rows_for("train"), dataset.rows_for("real-train")
    train_rows, valid_rows = fixture_rows + real_rows, dataset.rows_for("valid")
    if not fixture_rows or not valid_rows:
        raise ValueError("Both train and valid must contain candidate rows")
    # Validation precedes dependency loading and any filesystem mutation.
    for row in (*train_rows, *valid_rows):
        expected = "real" if dataset.groups[row.group_id]["split"] == "real-train" else "fixture"
        if dataset.groups[row.group_id]["source"] != expected:
            raise ValueError("Only real-train application data may participate in fitting; early stopping uses fixtures")
        survival_grade(row.survival)
    versions = _dependencies()
    import catboost
    import numpy

    train_pool = _pool(train_rows, dataset.schema, numpy, catboost)
    valid_pool = _pool(valid_rows, dataset.schema, numpy, catboost)
    model = catboost.CatBoostRanker(**parameters)
    started_at = datetime.now(timezone.utc).isoformat(timespec="seconds")
    started = time.perf_counter()
    model.fit(train_pool, eval_set=valid_pool)
    elapsed = time.perf_counter() - started

    # Catch edits during a long fit; otherwise the log could describe different
    # input bytes from those that produced the model.
    for name in ("features", "labels", "rules", "splitMap", "featureSchema", "export"):
        if sha256(dataset.artifact_path(name)) != dataset.hashes[name]:
            raise ValueError("Dataset changed during training: " + name)
    for name, path in (("schema", dataset.root / "features.json"), ("manifest", dataset.root / "dataset-manifest.json")):
        if sha256(path) != dataset.hashes[name]:
            raise ValueError("Dataset changed during training: " + name)

    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix="." + output.name + "-", dir=output.parent))
    try:
        model.save_model(str(temporary / "model.cbm"))
        model.save_model(str(temporary / "model.raw.json"), format="json")
        raw = read_json(temporary / "model.raw.json")
        maximum_depth = _validate_model(raw, dataset.schema, iterations)
        canonical, removed = canonical_model_json(raw)
        (temporary / "model.json").write_text(
            json.dumps(canonical, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n",
            encoding="utf-8", newline="\n")
        shutil.copyfile(dataset.root / "features.json", temporary / "features.json")
        log = {
            "format": 1,
            "datasetId": dataset.manifest["datasetId"],
            "featureSchemaVersion": dataset.schema.version,
            "namesHash": dataset.schema.names_hash,
            "parameters": parameters,
            "resolvedParameters": model.get_all_params(),
            "labelGrades": {"0": 0, "0.5": 1, "1": 2},
            "splitsUsed": ["train", "real-train", "valid"] if real_rows else ["train", "valid"],
            "candidates": {"train": len(fixture_rows), "valid": len(valid_rows),
                           **({"real-train": len(real_rows)} if real_rows else {})},
            "groups": {"train": len({row.group_id for row in fixture_rows}),
                       "valid": len({row.group_id for row in valid_rows}),
                       **({"real-train": len({row.group_id for row in real_rows})} if real_rows else {})},
            "gradeCounts": {split: dict(sorted(Counter(str(survival_grade(row.survival)) for row in rows).items()))
                            for split, rows in (("train", fixture_rows), ("real-train", real_rows), ("valid", valid_rows)) if rows},
            "inputSha256": dataset.hashes,
            "generatorCommit": dataset.manifest.get("generatorCommit"),
            "exportGeneratorCommit": read_json(dataset.artifact_path("export"))["generatorCommit"],
            "startedAt": started_at,
            "completedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "elapsedSeconds": round(elapsed, 6),
            "treeCount": model.tree_count_,
            "maximumTreeDepth": maximum_depth,
            "bestIteration": model.get_best_iteration(),
            "bestScore": model.get_best_score(),
            "evalsResult": model.get_evals_result(),
            "versions": {"python": platform.python_version(), **versions},
            "machine": {"system": platform.system(), "release": platform.release(),
                        "machine": platform.machine(), "processor": platform.processor(),
                        "logicalCpuCount": os.cpu_count()},
            "jsonNormalization": {"removedFields": removed,
                                  "numericValuesModified": False,
                                  "original": "model.raw.json",
                                  "serialization": "UTF-8, sorted object keys, indent 2, LF"},
            "artifacts": {name: {"sha256": sha256(temporary / name), "bytes": (temporary / name).stat().st_size}
                          for name in ("model.cbm", "model.raw.json", "model.json", "features.json")},
        }
        write_json(temporary / "train-log.json", log)
        # rename cannot replace a run another process published in the meantime.
        temporary.rename(output)
        return log
    finally:
        if temporary.exists():
            if not temporary.resolve().is_relative_to(output.parent.resolve()):
                raise ValueError("Refusing cleanup outside the training output parent")
            shutil.rmtree(temporary)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path, help="New run directory outside the frozen dataset")
    parser.add_argument("--iterations", type=int, default=1024)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--early-stopping-rounds", type=int, default=64)
    args = parser.parse_args(argv)
    try:
        log = fit_dataset(read_dataset(args.dataset), args.output, iterations=args.iterations,
                          seed=args.seed, early_stopping_rounds=args.early_stopping_rounds)
    except (ValueError, OSError, ImportError) as error:
        parser.error(str(error))
    print(json.dumps({key: log[key] for key in ("datasetId", "treeCount", "bestIteration", "elapsedSeconds")},
                     ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
