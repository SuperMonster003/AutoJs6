"""Convert a fitted candidate ranker into the flat tree JSON the offline construction experiments load.

The input is a ``train/train.py`` run directory (``model.json``, ``model.cbm``,
``features.json``, ``train-log.json``) fitted on a P3-contract candidate dataset,
for example the merged rule + construction export. The output ``ranker.json``
declares the role ``candidate-ranker`` and the host's 64-feature schema; it is an
experiment artifact, not an installable ``.ajcgm`` package, and it is never signed
or published. Parity rows let the Kotlin interpreter prove it reproduces CatBoost.
"""

import argparse
from copy import deepcopy
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import sys
import tempfile

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from dataset.task_io import inherit_parent_acl, read_json, write_json
from package.build_package import decode_model, encode_model, score_model
from train.data import read_dataset, read_schema, sha256
from train.train import PINNED_VERSIONS


ROLE = "candidate-ranker"
SCORE_SEMANTICS = "yetirank-raw-v1"
SCORE_TOLERANCE = 1e-4


def flatten(canonical, schema):
    """Depth-0 constant trees become one split with identical leaves, as in the construction export."""
    adjusted = deepcopy(canonical)
    trees = adjusted.get("oblivious_trees")
    if not isinstance(trees, list) or not 1 <= len(trees) <= 1024:
        raise ValueError("Ranker requires 1..1024 symmetric trees")
    constant_trees = 0
    for tree in trees:
        splits, leaves = tree.get("splits"), tree.get("leaf_values")
        if splits is None or splits == []:
            if not isinstance(leaves, list) or len(leaves) != 1:
                raise ValueError("A constant tree requires one leaf")
            tree["splits"] = [{"split_type": "FloatFeature", "float_feature_index": 0, "border": 0.0, "split_index": 0}]
            tree["leaf_values"] = [leaves[0], leaves[0]]
            constant_trees += 1
        elif not isinstance(splits, list) or len(splits) > 6:
            raise ValueError("Ranker tree depth must not exceed 6")
    decoded = decode_model(encode_model(adjusted, schema))
    model = {"depths": [], "splitOffsets": [], "leafOffsets": [], "featureIndices": [],
             "thresholds": [], "leafValues": [], "bias": decoded.bias, "scale": decoded.scale}
    for tree in decoded.trees:
        model["depths"].append(len(tree.features))
        model["splitOffsets"].append(len(model["featureIndices"]))
        model["leafOffsets"].append(len(model["leafValues"]))
        model["featureIndices"].extend(tree.features)
        model["thresholds"].extend(tree.borders)
        model["leafValues"].extend(tree.leaves)
    document = {"format": 1, "role": ROLE, "featureSchemaVersion": schema.version, "featureNamesHash": schema.names_hash,
                "featureCount": len(schema.names), "scoreSemantics": SCORE_SEMANTICS, "model": model}
    return document, decoded, constant_trees


def select_parity_groups(rows, maximum):
    """Complete candidate groups only; a ranker's first choice needs the whole group."""
    if type(maximum) is not int or maximum <= 0:
        raise ValueError("parity-max-rows must be a positive integer")
    groups = {}
    for row in rows:
        groups.setdefault(row.group_id, []).append(row)
    result = []
    for group in groups.values():
        if len(result) + len(group) <= maximum:
            result.extend(group)
    if not result:
        raise ValueError("No complete candidate group fits parity-max-rows")
    return tuple(result)


def export_ranker(fit_dir, dataset_dir, output_dir, *, parity_max_rows=5000):
    fit, output = Path(fit_dir).resolve(), Path(output_dir).resolve()
    if output.exists():
        raise ValueError("Ranker export output already exists; choose a fresh directory")
    log = read_json(fit / "train-log.json")
    if log.get("format") != 1 or "treeCount" not in log:
        raise ValueError("Expected a train/train.py run directory")
    for name, artifact in log["artifacts"].items():
        if sha256(fit / name) != artifact["sha256"]:
            raise ValueError("Fit artifact changed: " + name)
    schema = read_schema(fit / "features.json")
    if log["namesHash"] != schema.names_hash or log["featureSchemaVersion"] != schema.version:
        raise ValueError("Fit log schema differs from features.json")
    dataset = read_dataset(dataset_dir)
    for name in ("features", "labels", "rules", "splitMap", "featureSchema", "export"):
        if dataset.hashes[name] != log["inputSha256"][name]:
            raise ValueError("The ranker was fitted on a different dataset: " + name)
    if dataset.schema.names != schema.names:
        raise ValueError("Dataset feature names differ from the fit schema")
    canonical = read_json(fit / "model.json")
    document, decoded, constant_trees = flatten(canonical, schema)
    parity_rows = select_parity_groups(dataset.rows_for("train", "valid"), parity_max_rows)
    import importlib.metadata
    for name, expected in PINNED_VERSIONS.items():
        if importlib.metadata.version(name) != expected:
            raise ValueError(f"Expected {name}=={expected}")
    import catboost
    import numpy
    model = catboost.CatBoostRanker()
    model.load_model(str(fit / "model.cbm"))
    values = numpy.asarray([row.values for row in parity_rows], dtype=numpy.float32)
    # CatBoostRanker.predict returns raw formula values and does not accept prediction_type.
    expected = model.predict(catboost.Pool(values, group_id=[row.group_id for row in parity_rows], feature_names=list(schema.names)),
                             thread_count=1)
    binary = [score_model(decoded, row.values) for row in parity_rows]
    difference = max(abs(float(a) - b) for a, b in zip(expected, binary))
    if difference > SCORE_TOLERANCE:
        raise ValueError("Float32 ranker scores exceed CatBoost parity tolerance")
    groups = {}
    for index, row in enumerate(parity_rows):
        groups.setdefault(row.group_id, []).append(index)
    for indices in groups.values():
        best_expected = max(float(expected[index]) for index in indices)
        best_binary = max(binary[index] for index in indices)
        if ({parity_rows[index].plan_key for index in indices if float(expected[index]) == best_expected}
                != {parity_rows[index].plan_key for index in indices if binary[index] == best_binary}):
            raise ValueError("Float32 ranker changes a group's first choice")
    output.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix="." + output.name + "-", dir=output.parent)).resolve()
    try:
        # Before anything is written, so no file starts with the restricted mkdtemp() access rights.
        inherit_parent_acl(staging)
        write_json(staging / "ranker.json", document)
        with (staging / "parity.jsonl").open("w", encoding="utf-8", newline="\n") as stream:
            for row, score in zip(parity_rows, expected):
                stream.write(json.dumps({"format": 1, "split": row.split, "groupId": row.group_id, "planKey": row.plan_key,
                                         "features": list(row.values), "expectedScore": float(score)},
                                        ensure_ascii=False, allow_nan=False, separators=(",", ":")) + "\n")
        shutil.copyfile(fit / "features.json", staging / "features.json")
        artifacts = {name: {"sha256": sha256(staging / name), "bytes": (staging / name).stat().st_size}
                     for name in ("ranker.json", "parity.jsonl", "features.json")}
        write_json(staging / "export-log.json", {
            "format": 1, "role": ROLE, "scoreSemantics": SCORE_SEMANTICS, "datasetId": dataset.manifest["datasetId"],
            "fitTrainLogSha256": sha256(fit / "train-log.json"), "fitModelJsonSha256": sha256(fit / "model.json"),
            "fitModelCbmSha256": sha256(fit / "model.cbm"), "treeCount": log["treeCount"], "bestIteration": log["bestIteration"],
            "featureSchemaVersion": schema.version, "featureNamesHash": schema.names_hash, "featureCount": len(schema.names),
            "constantTreesExpandedToIdenticalLeaves": constant_trees, "parityRows": len(parity_rows), "parityGroups": len(groups),
            "maxRankerScoreDifference": difference, "scoreTolerance": SCORE_TOLERANCE, "sameGroupFirstChoice": True,
            "modelFile": "ranker.json", "modelSha256": artifacts["ranker.json"]["sha256"],
            "parityFile": "parity.jsonl", "paritySha256": artifacts["parity.jsonl"]["sha256"],
            "exportedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"), "artifacts": artifacts,
        })
        output.mkdir()
        for name in (*artifacts, "export-log.json"):
            (staging / name).rename(output / name)
            # Inherit from the output directory itself, not through the staging directory.
            inherit_parent_acl(output / name)
    finally:
        if staging.exists():
            shutil.rmtree(staging)
    return read_json(output / "export-log.json")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fit-dir", required=True, type=Path)
    parser.add_argument("--dataset-dir", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--parity-max-rows", type=int, default=5000)
    args = parser.parse_args(argv)
    try:
        log = export_ranker(args.fit_dir, args.dataset_dir, args.output_dir, parity_max_rows=args.parity_max_rows)
    except (ValueError, OSError, ImportError) as error:
        parser.exit(2, str(error) + "\n")
    print(json.dumps({key: log[key] for key in ("datasetId", "treeCount", "parityRows", "parityGroups", "maxRankerScoreDifference", "modelSha256")},
                     ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
