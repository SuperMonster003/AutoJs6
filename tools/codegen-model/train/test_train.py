import csv
from copy import deepcopy
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from dataset.task_io import read_json, write_json
from train.data import (CandidateRow, FeatureSchema, rank_indices, read_dataset,
                        read_feature_rows, read_schema, sha256, survival_grade)
from train.train import (DEFAULT_SEED, canonical_model_json, fit_dataset,
                         training_parameters)


class DatasetFixture:
    """Small independently authored rank-learning task, without Android data."""

    def __init__(self, root):
        self.root = Path(root)
        self.root.mkdir()
        names = ["quality", "rule_rank_norm"]
        self.schema = {"format": 1, "featureSchemaVersion": 1, "names": names,
                       "namesHash": hashlib.sha256("\n".join(names).encode()).hexdigest()[:16]}
        write_json(self.root / "features.json", self.schema)
        self.groups = [
            {"groupId": "g1", "split": "train", "source": "fixture", "app": "fixture", "screenFamily": "login"},
            {"groupId": "g2", "split": "train", "source": "fixture", "app": "fixture", "screenFamily": "login"},
            {"groupId": "v1", "split": "valid", "source": "fixture", "app": "fixture", "screenFamily": "settings"},
            {"groupId": "t1", "split": "test", "source": "fixture", "app": "fixture", "screenFamily": "list"},
        ]
        self.rows = []
        for group in self.groups:
            for index, label in enumerate((1, 0.5, 0) if group["groupId"] == "g2" else (0, 0.5, 1)):
                self.rows.append([group["split"], group["groupId"], "p" + str(index), label, label, index / 2])
        self.refresh()

    def refresh(self):
        with (self.root / "features.csv").open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream, lineterminator="\n")
            writer.writerow(["split", "groupId", "planKey", "label", *self.schema["names"]])
            writer.writerows(self.rows)
        split_map = {"format": 1, "datasetId": "unit-fixture", "groups": self.groups,
                     "assignment": {group["groupId"]: group["split"] for group in self.groups}}
        write_json(self.root / "split-map.json", split_map)
        labels, rules = [], []
        for group in self.groups:
            rows = [row for row in self.rows if row[1] == group["groupId"]]
            first = min(rows, key=lambda row: row[5]) if rows else None
            labels.extend({"format": 1, "groupId": row[1], "planKey": row[2], "survival": row[3],
                           "family": "SELF", "ruleSelected": row is first, "failedStates": []} for row in rows)
            rules.append({"format": 1, **{name: group[name] for name in ("groupId", "source", "app", "screenFamily")},
                          "stateCount": 3, "candidateCount": len(rows),
                          "rulePlanKey": first[2] if first else None, "ruleSurvival": first[3] if first else None})
        for name, records in (("labels.jsonl", labels), ("rules.jsonl", rules)):
            (self.root / name).write_text("".join(json.dumps(record) + "\n" for record in records), encoding="utf-8")
        manifest = {"format": 1, "datasetId": "unit-fixture", "status": "complete", "groups": len(self.groups),
                    "candidates": len(self.rows), "generatorCommit": "a" * 40, "splits": {}, "artifacts": {}}
        # Like manifests written before D25, real-train is described only when a group uses it.
        real_train = ("real-train",) if any(group["split"] == "real-train" for group in self.groups) else ()
        for split in ("train", "valid", "test", "real-test", *real_train):
            groups = {group["groupId"] for group in self.groups if group["split"] == split}
            rows = [row for row in self.rows if row[0] == split]
            manifest["splits"][split] = {"groups": len(groups), "candidates": len(rows),
                                         "groupsWithoutCandidates": len(groups - {row[1] for row in rows})}
        for name, file in (("features", "features.csv"), ("labels", "labels.jsonl"),
                           ("rules", "rules.jsonl"), ("splitMap", "split-map.json"), ("featureSchema", "features.json")):
            manifest["artifacts"][name] = {"status": "complete", "file": file, "sha256": sha256(self.root / file)}
        self.provenance(manifest)
        write_json(self.root / "dataset-manifest.json", manifest)

    def provenance(self, manifest):
        export = {"format": 1, "tool": "CodeGenDatasetExportTask", "generatorCommit": "b" * 40,
                  "featureSchemaVersion": self.schema["featureSchemaVersion"], "featureNamesHash": self.schema["namesHash"],
                  "groups": manifest["groups"], "candidates": manifest["candidates"]}
        for field, artifact in (("featuresSha256", "features"), ("featureSchemaSha256", "featureSchema"),
                                ("labelsSha256", "labels"), ("rulesSha256", "rules"), ("splitMapSha256", "splitMap")):
            export[field] = manifest["artifacts"][artifact]["sha256"]
        path = self.root / "export-manifest.json"
        write_json(path, export)
        manifest["artifacts"]["export"] = {"status": "complete", "file": path.name, "sha256": sha256(path)}

    def rehash(self, name):
        manifest = read_json(self.root / "dataset-manifest.json")
        item = manifest["artifacts"][name]
        item["sha256"] = sha256(self.root / item["file"])
        self.provenance(manifest)
        write_json(self.root / "dataset-manifest.json", manifest)


class TrainingDataTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.fixture = DatasetFixture(Path(self.temporary.name) / "dataset")

    def test_merged_construction_export_provenance_is_accepted_but_unknown_tools_are_not(self):
        manifest = read_json(self.fixture.root / "dataset-manifest.json")
        for tool, accepted in (("ConstructionMergedExportTask", True), ("CodeGenDatasetExportTask", True), ("SomeOtherExporter", False)):
            with self.subTest(tool=tool):
                export = read_json(self.fixture.root / "export-manifest.json")
                export["tool"] = tool
                write_json(self.fixture.root / "export-manifest.json", export)
                manifest["artifacts"]["export"]["sha256"] = sha256(self.fixture.root / "export-manifest.json")
                write_json(self.fixture.root / "dataset-manifest.json", manifest)
                if accepted:
                    self.assertEqual(12, len(read_dataset(self.fixture.root).rows))
                else:
                    with self.assertRaisesRegex(ValueError, "provenance"):
                        read_dataset(self.fixture.root)

    def test_dataset_keeps_test_rows_out_of_training_selection(self):
        dataset = read_dataset(self.fixture.root)
        self.assertEqual({"g1", "g2"}, set(dataset.grouped_rows("train")))
        self.assertEqual({"v1"}, set(dataset.grouped_rows("valid")))
        self.assertEqual(12, len(dataset.rows))
        self.assertEqual(6, len(dataset.rows_for("train")))
        with self.assertRaisesRegex(ValueError, "Unknown requested"):
            dataset.rows_for("development")

    def test_schema_hash_column_order_and_duplicates_are_checked(self):
        path = self.fixture.root / "features.json"
        original = deepcopy(self.fixture.schema)
        for names, digest in ((["rule_rank_norm", "quality"], original["namesHash"]),
                              (["quality", "quality"], "0" * 16)):
            write_json(path, {**original, "names": names, "namesHash": digest})
            with self.assertRaises(ValueError):
                read_schema(path)
        write_json(path, original)
        csv_path = self.fixture.root / "features.csv"
        csv_path.write_text(csv_path.read_text().replace("quality,rule_rank_norm", "rule_rank_norm,quality"), encoding="utf-8")
        self.fixture.rehash("features")
        with self.assertRaisesRegex(ValueError, "columns differ"):
            read_dataset(self.fixture.root)

    def test_nonfinite_and_float_overflow_features_are_rejected(self):
        for invalid in ("nan", "inf", "-inf", "1e300"):
            self.fixture.rows[0][4] = invalid
            self.fixture.refresh()
            with self.assertRaisesRegex(ValueError, "finite|Float range"):
                read_dataset(self.fixture.root)

    def test_hash_mismatch_is_detected_before_data_is_used(self):
        path = self.fixture.root / "features.csv"
        path.write_text(path.read_text() + "\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            read_dataset(self.fixture.root)

    def test_schema_and_features_are_bound_to_the_kotlin_exporter(self):
        path = self.fixture.root / "export-manifest.json"
        original = read_json(path)
        for field in ("featuresSha256", "featureSchemaSha256", "splitMapSha256", "featureNamesHash", "generatorCommit"):
            value = {**original, field: "invalid"}
            write_json(path, value)
            manifest_path = self.fixture.root / "dataset-manifest.json"
            manifest = read_json(manifest_path)
            manifest["artifacts"]["export"]["sha256"] = sha256(path)
            write_json(manifest_path, manifest)
            with self.assertRaisesRegex(ValueError, "provenance"):
                read_dataset(self.fixture.root)

    def test_real_data_and_outputs_stay_inside_the_private_directory(self):
        self.fixture.groups[-1].update(source="real", split="real-test", app="private.app")
        for row in self.fixture.rows:
            if row[1] == "t1":
                row[0] = "real-test"
        self.fixture.refresh()
        with patch.dict(os.environ, {"CODEGEN_PRIVATE_DIR": ""}):
            with self.assertRaisesRegex(ValueError, "CODEGEN_PRIVATE_DIR"):
                read_dataset(self.fixture.root)
        with patch.dict(os.environ, {"CODEGEN_PRIVATE_DIR": self.temporary.name}):
            dataset = read_dataset(self.fixture.root)
            self.assertEqual({"g1", "g2"}, set(dataset.grouped_rows("train")))
            with self.assertRaisesRegex(ValueError, "under CODEGEN_PRIVATE_DIR"):
                fit_dataset(dataset, Path(self.temporary.name).parent / "outside-private-training")

    def test_family_leak_and_real_training_are_rejected_even_with_new_hashes(self):
        self.fixture.groups[2]["screenFamily"] = "login"
        self.fixture.refresh()
        with self.assertRaisesRegex(ValueError, "leaked across"):
            read_dataset(self.fixture.root)
        self.fixture.groups[2]["screenFamily"] = "settings"
        self.fixture.groups[0]["source"] = "real"
        self.fixture.refresh()
        with self.assertRaisesRegex(ValueError, "only be in real-train or real-test"):
            read_dataset(self.fixture.root)

    def add_real_train_group(self):
        self.fixture.groups.append({"groupId": "r1", "split": "real-train", "source": "real", "app": "private.app", "screenFamily": "login"})
        self.fixture.rows.extend(["real-train", "r1", "p" + str(index), label, label, index / 2] for index, label in enumerate((0, 0.5, 1)))
        self.fixture.refresh()

    def test_real_train_applications_are_read_and_fixtures_cannot_enter_real_splits(self):
        self.add_real_train_group()
        with patch.dict(os.environ, {"CODEGEN_PRIVATE_DIR": self.temporary.name}):
            dataset = read_dataset(self.fixture.root)
            # A real screen family may share a fixture family name without leaking it.
            self.assertEqual({"r1"}, set(dataset.grouped_rows("real-train")))
            self.assertEqual({"g1", "g2"}, set(dataset.grouped_rows("train")))
        self.fixture.groups[0]["split"] = "real-train"
        for row in self.fixture.rows:
            if row[1] == "g1":
                row[0] = "real-train"
        self.fixture.refresh()
        with patch.dict(os.environ, {"CODEGEN_PRIVATE_DIR": self.temporary.name}):
            with self.assertRaisesRegex(ValueError, "Fixture data cannot be in real-train"):
                read_dataset(self.fixture.root)

    def test_unknown_split_and_group_are_rejected(self):
        self.fixture.rows[0][0] = "future"
        self.fixture.refresh()
        with self.assertRaisesRegex(ValueError, "Unknown split"):
            read_dataset(self.fixture.root)
        self.fixture.rows[0][0] = "train"
        self.fixture.rows[0][1] = "unassigned"
        self.fixture.refresh()
        with self.assertRaisesRegex(ValueError, "differs from split-map"):
            read_dataset(self.fixture.root)

    def test_duplicate_candidate_and_noncontiguous_group_are_rejected(self):
        original = deepcopy(self.fixture.rows)
        self.fixture.rows.append(original[0])
        self.fixture.refresh()
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            read_dataset(self.fixture.root)
        self.fixture.rows = original[1:] + original[:1]
        self.fixture.refresh()
        with self.assertRaisesRegex(ValueError, "contiguous"):
            read_dataset(self.fixture.root)

    def test_rule_order_is_validated_against_exporter_choice(self):
        self.fixture.rows[0][5] = self.fixture.rows[1][5]
        self.fixture.refresh()
        with self.assertRaisesRegex(ValueError, "distinct rule order"):
            read_dataset(self.fixture.root)
        self.fixture.rows[0][5] = 0
        self.fixture.refresh()
        path = self.fixture.root / "rules.jsonl"
        records = [json.loads(line) for line in path.read_text().splitlines()]
        records[0]["rulePlanKey"] = "p2"
        path.write_text("".join(json.dumps(record) + "\n" for record in records), encoding="utf-8")
        self.fixture.rehash("rules")
        with self.assertRaisesRegex(ValueError, "RuleRanker choice"):
            read_dataset(self.fixture.root)

    def test_labels_and_manifest_counts_cannot_silently_disagree(self):
        path = self.fixture.root / "labels.jsonl"
        path.write_text(path.read_text().replace('"survival": 0,', '"survival": 1,', 1), encoding="utf-8")
        self.fixture.rehash("labels")
        with self.assertRaisesRegex(ValueError, "survival differs"):
            read_dataset(self.fixture.root)
        self.fixture.refresh()
        manifest_path = self.fixture.root / "dataset-manifest.json"
        manifest = read_json(manifest_path)
        manifest["splits"]["test"]["candidates"] -= 1
        write_json(manifest_path, manifest)
        with self.assertRaisesRegex(ValueError, "split counts"):
            read_dataset(self.fixture.root)

    def test_artifact_paths_cannot_escape_the_dataset(self):
        path = self.fixture.root / "dataset-manifest.json"
        manifest = read_json(path)
        manifest["artifacts"]["features"]["file"] = "../features.csv"
        write_json(path, manifest)
        with self.assertRaisesRegex(ValueError, "directly inside"):
            read_dataset(self.fixture.root)

    def test_unlabelled_features_require_explicit_queue_mode(self):
        self.fixture.rows[0][3] = ""
        self.fixture.refresh()
        schema = read_schema(self.fixture.root / "features.json")
        path = self.fixture.root / "features.csv"
        with self.assertRaisesRegex(ValueError, "numeric"):
            read_feature_rows(path, schema)
        self.assertIsNone(read_feature_rows(path, schema, allow_unlabelled=True)[0].survival)

    def test_general_survival_is_evaluable_but_not_silently_graded_for_training(self):
        self.fixture.rows[0][3] = 1 / 3
        self.fixture.refresh()
        dataset = read_dataset(self.fixture.root)
        self.assertEqual(1 / 3, dataset.rows[0].survival)
        output = Path(self.temporary.name) / "bad-run"
        with patch("train.train._dependencies") as dependencies:
            with self.assertRaisesRegex(ValueError, "grades 0, 0.5, or 1"):
                fit_dataset(dataset, output)
            dependencies.assert_not_called()
        self.assertFalse(output.exists())

    def test_existing_and_nested_output_are_preserved(self):
        dataset = read_dataset(self.fixture.root)
        output = Path(self.temporary.name) / "existing"
        output.mkdir()
        (output / "keep.txt").write_text("prior result")
        for target in (output, self.fixture.root / "run"):
            with patch("train.train._dependencies") as dependencies:
                with self.assertRaisesRegex(ValueError, "already exists|separate"):
                    fit_dataset(dataset, target)
                dependencies.assert_not_called()
        self.assertEqual("prior result", (output / "keep.txt").read_text())

    def test_empty_validation_never_reaches_training(self):
        self.fixture.rows = [row for row in self.fixture.rows if row[0] != "valid"]
        self.fixture.refresh()
        dataset = read_dataset(self.fixture.root)
        with patch("train.train._dependencies") as dependencies:
            with self.assertRaisesRegex(ValueError, "Both train and valid"):
                fit_dataset(dataset, Path(self.temporary.name) / "no-valid")
            dependencies.assert_not_called()

    @unittest.skipUnless(os.environ.get("CODEGEN_TEST_TRAINING") == "1", "set CODEGEN_TEST_TRAINING=1 in Python 3.12 training venv")
    def test_actual_catboost_training_twice_has_identical_numeric_json(self):
        dataset = read_dataset(self.fixture.root)
        first, second = (Path(self.temporary.name) / name for name in ("first", "second"))
        logs = [fit_dataset(dataset, output, iterations=20, early_stopping_rounds=5) for output in (first, second)]
        self.assertEqual((first / "model.json").read_bytes(), (second / "model.json").read_bytes())
        for output, log in zip((first, second), logs):
            self.assertEqual(["train", "valid"], log["splitsUsed"])
            self.assertLessEqual(log["treeCount"], 20)
            self.assertEqual(DEFAULT_SEED, log["parameters"]["random_seed"])
            raw = read_json(output / "model.raw.json")
            canonical = read_json(output / "model.json")
            self.assertEqual(raw["oblivious_trees"], canonical["oblivious_trees"])
            self.assertEqual(raw["features_info"], canonical["features_info"])
            self.assertEqual(raw["scale_and_bias"], canonical["scale_and_bias"])

    @unittest.skipUnless(os.environ.get("CODEGEN_TEST_TRAINING") == "1", "set CODEGEN_TEST_TRAINING=1 in Python 3.12 training venv")
    def test_actual_catboost_training_fits_real_train_but_stops_early_on_fixtures(self):
        self.add_real_train_group()
        with patch.dict(os.environ, {"CODEGEN_PRIVATE_DIR": self.temporary.name}):
            dataset = read_dataset(self.fixture.root)
            log = fit_dataset(dataset, Path(self.temporary.name) / "with-real-train", iterations=20, early_stopping_rounds=5)
        self.assertEqual(["train", "real-train", "valid"], log["splitsUsed"])
        self.assertEqual({"train": 2, "valid": 1, "real-train": 1}, log["groups"])
        self.assertEqual(3, log["candidates"]["real-train"])


class TrainingPolicyTest(unittest.TestCase):
    def test_grade_mapping_and_invalid_levels(self):
        self.assertEqual([0, 1, 2], [survival_grade(value) for value in (0, 0.5, 1)])
        for value in (None, True, -1, 0.4, 1.1, float("nan"), float("inf")):
            with self.assertRaises(ValueError):
                survival_grade(value)

    def test_seed_early_stopping_and_model_limits_are_fixed(self):
        first = training_parameters()
        self.assertEqual(first, training_parameters())
        self.assertEqual(("CPU", 1, 6, "YetiRank", "SymmetricTree"),
                         tuple(first[name] for name in ("task_type", "thread_count", "depth", "loss_function", "grow_policy")))
        self.assertTrue(first["use_best_model"])
        self.assertEqual("Iter", first["od_type"])
        self.assertEqual(17, training_parameters(seed=17)["random_seed"])
        for kwargs in ({"iterations": 1025}, {"iterations": 0}, {"iterations": True},
                       {"seed": -1}, {"seed": 2**32}, {"early_stopping_rounds": 0}):
            with self.assertRaises(ValueError):
                training_parameters(**kwargs)

    def test_metadata_normalization_preserves_all_model_numbers_and_input(self):
        original = {"model_info": {"model_guid": "random", "train_finish_time": "today", "params": {"seed": 7}},
                    "oblivious_trees": [{"leaf_values": [1e-20, -1.5], "splits": [{"border": 0.25}]}],
                    "scale_and_bias": [1, [0]]}
        before = deepcopy(original)
        normalized, removed = canonical_model_json(original)
        self.assertEqual(before, original)
        self.assertEqual(["model_info.model_guid", "model_info.train_finish_time"], removed)
        self.assertEqual({"params": {"seed": 7}}, normalized["model_info"])
        self.assertEqual(before["oblivious_trees"], normalized["oblivious_trees"])
        self.assertEqual(before["scale_and_bias"], normalized["scale_and_bias"])

    def test_near_ties_are_deterministic_without_nontransitive_comparison(self):
        schema = FeatureSchema(1, ("rule_rank_norm",), "handwritten")
        rows = [CandidateRow("test", "g", "p" + str(index), 1, (rank,)) for index, rank in enumerate((1, 0.5, 0))]
        scores = [0, 0.75e-6, 1.5e-6]
        order = rank_indices(rows, scores, schema)
        self.assertEqual((2, 1, 0), order)
        reverse = list(reversed(range(3)))
        mapped = rank_indices([rows[index] for index in reverse], [scores[index] for index in reverse], schema)
        self.assertEqual(order, tuple(reverse[index] for index in mapped))
        self.assertEqual((2, 1, 0), rank_indices(rows, [1, 1, 1], schema))
        for invalid in ([0, float("nan"), 1], [0, 1], [0, float("inf"), 1]):
            with self.assertRaises(ValueError):
                rank_indices(rows, invalid, schema)


if __name__ == "__main__":
    unittest.main()
