"""Protocol, isolation, and real CatBoost checks for construction-policy training."""

from array import array
from copy import deepcopy
import csv
import hashlib
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from construction.data import ActionRow, FeatureSchema, PREFIX, PREFIX_V2, read_dataset, read_schema
from construction.train import (BEST_SCORE_SEMANTICS, DEFAULT_SEED, SCORE_SEMANTICS, action_order, export_policy,
                                fit_dataset, parity_records, row_label, select_parity_rows, train, training_parameters)
from dataset.task_io import read_json, write_json
from package.build_package import score_model
from train.data import sha256


class ConstructionFixture:
    """Numeric protocol fixture; it does not invent a Kotlin feature implementation."""

    def __init__(self, root, export_format=1):
        self.root = Path(root)
        self.root.mkdir()
        self.export_format = export_format
        names = ["test_signal", "test_action_index"]
        self.schema = {"format": 1, "role": "construction-state-action", "grammarVersion": 1,
                       "featureSchemaVersion": 1, "names": names,
                       "namesHash": hashlib.sha256("\n".join(names).encode()).hexdigest()[:16]}
        self.groups = [
            {"groupId": "g1", "split": "train", "source": "fixture", "screenFamily": "login"},
            {"groupId": "g2", "split": "train", "source": "fixture", "screenFamily": "login"},
            {"groupId": "v1", "split": "valid", "source": "fixture", "screenFamily": "settings"},
            {"groupId": "t1", "split": "test", "source": "fixture", "screenFamily": "list"},
            {"groupId": "empty", "split": "test", "source": "fixture", "screenFamily": "zero"},
        ]
        self.rows = []
        for group in self.groups[:-1]:
            for state in range(2):
                for action, label in enumerate((0, 1 / 3, 1)):
                    row = [group["split"], group["groupId"], "state" + str(state), "action" + str(action),
                           label, 1 + action, label, action / 2]
                    if export_format == 2:
                        # The best observed continuation is at least the mean: 0 -> 0.5, 1/3 -> 1, 1 -> 1.
                        row.insert(5, 1.0 if action else 0.5)
                    self.rows.append(row)
        self.refresh()

    @property
    def prefix(self):
        return PREFIX_V2 if self.export_format == 2 else PREFIX

    @property
    def observations_index(self):
        return len(self.prefix) - 1

    @property
    def first_feature_index(self):
        return len(self.prefix)

    def refresh(self):
        write_json(self.root / "features.json", self.schema)
        with (self.root / "actions.csv").open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream, lineterminator="\n")
            writer.writerow([*self.prefix, *self.schema["names"]])
            writer.writerows(self.rows)
        self.rehash()

    def rehash(self):
        write_json(self.root / "export-manifest.json", {
            "format": self.export_format, "datasetId": "construction-protocol-test",
            "featuresSha256": sha256(self.root / "features.json"), "actionsSha256": sha256(self.root / "actions.csv"),
            "groups": self.groups, "coverage": {"note": "Only explicitly observed actions are listed"},
        })


class ConstructionDataTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.fixture = ConstructionFixture(Path(self.temporary.name) / "dataset")

    def test_split_selection_preserves_zero_observation_groups_and_continuous_labels(self):
        dataset = read_dataset(self.fixture.root)
        self.assertEqual(12, len(dataset.rows_for("train")))
        self.assertEqual(6, len(dataset.rows_for("valid")))
        self.assertIn("empty", dataset.groups)
        self.assertNotIn("empty", {row.group_id for row in dataset.rows})
        self.assertAlmostEqual(1 / 3, dataset.rows[1].label)
        self.assertEqual(2, dataset.rows[1].observations)
        self.assertIsInstance(dataset.rows[1].values, array)
        self.assertEqual(("f", 4), (dataset.rows[1].values.typecode, dataset.rows[1].values.itemsize))
        with self.assertRaisesRegex(ValueError, "Unknown requested"):
            dataset.rows_for("future")

    def test_roles_versions_names_hash_and_column_order_are_independent_from_ranker(self):
        original = deepcopy(self.fixture.schema)
        for field, value in (("role", "ranker"), ("grammarVersion", True), ("featureSchemaVersion", 2),
                             ("names", ["test_signal", "test_signal"]), ("namesHash", "0" * 16)):
            with self.subTest(field=field):
                self.fixture.schema = {**original, field: value}
                self.fixture.refresh()
                with self.assertRaises(ValueError):
                    read_dataset(self.fixture.root)
        self.fixture.schema = original
        self.fixture.refresh()
        path = self.fixture.root / "actions.csv"
        path.write_text(path.read_text().replace("test_signal,test_action_index", "test_action_index,test_signal"),
                        encoding="utf-8")
        self.fixture.rehash()
        with self.assertRaisesRegex(ValueError, "columns differ"):
            read_dataset(self.fixture.root)

    def test_hashes_bind_actions_and_schema_before_use(self):
        for name in ("actions.csv", "features.json"):
            with self.subTest(name=name):
                self.fixture.refresh()
                path = self.fixture.root / name
                path.write_bytes(path.read_bytes() + b"\n")
                with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                    read_dataset(self.fixture.root)

    def test_family_leak_and_real_training_are_rejected(self):
        self.fixture.groups[2]["screenFamily"] = "login"
        self.fixture.refresh()
        with self.assertRaisesRegex(ValueError, "leaked across"):
            read_dataset(self.fixture.root)
        self.fixture.groups[2]["screenFamily"] = "settings"
        self.fixture.groups[0]["source"] = "real"
        self.fixture.refresh()
        with self.assertRaisesRegex(ValueError, "only be in real-test"):
            read_dataset(self.fixture.root)

    def test_real_test_data_and_output_require_private_directory(self):
        self.fixture.groups[3].update(source="real", split="real-test")
        for row in self.fixture.rows:
            if row[1] == "t1":
                row[0] = "real-test"
        self.fixture.refresh()
        with patch.dict(os.environ, {"CODEGEN_PRIVATE_DIR": ""}):
            with self.assertRaisesRegex(ValueError, "CODEGEN_PRIVATE_DIR"):
                read_dataset(self.fixture.root)
        with patch.dict(os.environ, {"CODEGEN_PRIVATE_DIR": self.temporary.name}):
            dataset = read_dataset(self.fixture.root)
            self.assertEqual(6, len(dataset.rows_for("real-test")))
            with self.assertRaisesRegex(ValueError, "CODEGEN_PRIVATE_DIR"):
                fit_dataset(dataset, Path(self.temporary.name).parent / "not-private-construction")

    def test_format_two_exposes_best_continuations_and_rejects_inconsistent_labels(self):
        fixture = ConstructionFixture(Path(self.temporary.name) / "format2", export_format=2)
        dataset = read_dataset(fixture.root)
        self.assertEqual(2, dataset.export_format)
        self.assertEqual((0.0, 0.5), (dataset.rows[0].label, dataset.rows[0].label_best))
        self.assertAlmostEqual(1 / 3, dataset.rows[1].label)
        self.assertEqual(1.0, dataset.rows[1].label_best)
        self.assertEqual(2, dataset.rows[1].observations)
        self.assertEqual(("f", 4), (dataset.rows[1].values.typecode, dataset.rows[1].values.itemsize))
        self.assertEqual(0.5, row_label(dataset.rows[0], "best"))
        self.assertEqual(0.0, row_label(dataset.rows[0], "mean"))
        with self.assertRaisesRegex(ValueError, "mean or best"):
            row_label(dataset.rows[0], "max")
        # Format 1 rows have no best continuation, so the best target cannot be fitted from them.
        legacy = read_dataset(self.fixture.root)
        self.assertIsNone(legacy.rows[0].label_best)
        with self.assertRaisesRegex(ValueError, "format 2"):
            row_label(legacy.rows[0], "best")
        with patch("construction.train._dependencies") as dependencies:
            with self.assertRaisesRegex(ValueError, "format 2"):
                fit_dataset(legacy, Path(self.temporary.name) / "legacy-best", label="best")
            dependencies.assert_not_called()
        original = deepcopy(fixture.rows)
        for value in ("", "nan", -0.1, 1.1, 0.2):  # 0.2 is below the 1/3 mean of row 1
            with self.subTest(value=value):
                fixture.rows = deepcopy(original)
                fixture.rows[1][5] = value
                fixture.refresh()
                with self.assertRaises(ValueError):
                    read_dataset(fixture.root)
        fixture.rows = deepcopy(original)
        fixture.refresh()
        path = fixture.root / "actions.csv"
        path.write_text(path.read_text().replace("label,labelBest,observations", "label,observations,labelBest"),
                        encoding="utf-8")
        fixture.rehash()
        with self.assertRaisesRegex(ValueError, "columns differ"):
            read_dataset(fixture.root)
        manifest = read_json(fixture.root / "export-manifest.json")
        manifest["format"] = 3
        write_json(fixture.root / "export-manifest.json", manifest)
        with self.assertRaisesRegex(ValueError, "Unsupported"):
            read_dataset(fixture.root)

    def test_group_assignment_duplicate_actions_and_zero_observations_are_rejected(self):
        original = deepcopy(self.fixture.rows)
        for index, value in ((0, "test"), (1, "unknown"), (2, ""), (3, "line\nbreak"),
                             (4, ""), (4, -0.1), (4, 1.1), (5, 0), (5, -1), (5, 0.5),
                             (5, 2**31), (5, "01")):
            with self.subTest(index=index, value=value):
                self.fixture.rows = deepcopy(original)
                self.fixture.rows[0][index] = value
                self.fixture.refresh()
                with self.assertRaises(ValueError):
                    read_dataset(self.fixture.root)
        self.fixture.rows = original + original[:1]
        self.fixture.refresh()
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            read_dataset(self.fixture.root)

    def test_held_out_rows_are_validated_too(self):
        for invalid in ("nan", "inf", "-inf", "1e100"):
            with self.subTest(invalid=invalid):
                self.fixture.rows[-1][6] = invalid
                self.fixture.refresh()
                with self.assertRaisesRegex(ValueError, "finite"):
                    read_dataset(self.fixture.root)

    def test_existing_frozen_and_tracked_outputs_are_refused_before_loading_dependencies(self):
        dataset = read_dataset(self.fixture.root)
        existing = Path(self.temporary.name) / "existing"
        existing.mkdir()
        marker = existing / "keep.txt"
        marker.write_text("prior run")
        for output in (existing, self.fixture.root / "nested", Path(__file__).parent / "would-be-tracked"):
            with self.subTest(output=output), patch("construction.train._dependencies") as dependencies:
                with self.assertRaises(ValueError):
                    fit_dataset(dataset, output)
                dependencies.assert_not_called()
        self.assertEqual("prior run", marker.read_text())

    def test_missing_valid_and_mutated_inputs_fail_before_fit(self):
        dataset = read_dataset(self.fixture.root)
        self.fixture.rows[0][6] = 0.123
        self.fixture.refresh()
        with patch("construction.train._dependencies") as dependencies:
            with self.assertRaisesRegex(ValueError, "changed during"):
                fit_dataset(dataset, Path(self.temporary.name) / "stale")
            dependencies.assert_not_called()
        self.fixture.rows = [row for row in self.fixture.rows if row[0] != "valid"]
        self.fixture.refresh()
        with patch("construction.train._dependencies") as dependencies:
            with self.assertRaisesRegex(ValueError, "Both train and valid"):
                train(self.fixture.root, Path(self.temporary.name) / "no-valid")
            dependencies.assert_not_called()

    @unittest.skipUnless(os.environ.get("CODEGEN_TEST_TRAINING") == "1", "set CODEGEN_TEST_TRAINING=1 in Python 3.12 venv")
    def test_actual_best_label_fit_declares_its_semantics_and_differs_from_the_mean_fit(self):
        fixture = ConstructionFixture(Path(self.temporary.name) / "format2-fit", export_format=2)
        options = {"iterations": 24, "depth": 3, "early_stopping_rounds": 5}
        outputs = [Path(self.temporary.name) / name for name in ("mean-fit", "best-fit", "best-repeat")]
        mean = train(fixture.root, outputs[0], label="mean", **options)
        best = train(fixture.root, outputs[1], label="best", **options)
        train(fixture.root, outputs[2], label="best", **options)
        self.assertEqual(("mean", SCORE_SEMANTICS, 2), (mean["labelSelection"], mean["scoreSemantics"], mean["exportFormat"]))
        self.assertEqual(("best", BEST_SCORE_SEMANTICS, 2), (best["labelSelection"], best["scoreSemantics"], best["exportFormat"]))
        self.assertIn("Maximum survival", best["labelMeaning"])
        for name in ("model.json", "policy.json", "parity.jsonl"):
            self.assertEqual((outputs[1] / name).read_bytes(), (outputs[2] / name).read_bytes())
        self.assertNotEqual((outputs[0] / "model.json").read_bytes(), (outputs[1] / "model.json").read_bytes())
        self.assertEqual(SCORE_SEMANTICS, read_json(outputs[0] / "policy.json")["scoreSemantics"])
        self.assertEqual(BEST_SCORE_SEMANTICS, read_json(outputs[1] / "policy.json")["scoreSemantics"])
        self.assertEqual(mean["inputSha256"], best["inputSha256"])

    @unittest.skipUnless(os.environ.get("CODEGEN_TEST_TRAINING") == "1", "set CODEGEN_TEST_TRAINING=1 in Python 3.12 venv")
    def test_actual_repeat_fit_is_reproducible_and_held_out_changes_cannot_change_weights(self):
        outputs = [Path(self.temporary.name) / name for name in ("first", "repeat", "changed-test")]
        options = {"iterations": 24, "depth": 3, "early_stopping_rounds": 5}
        first = train(self.fixture.root, outputs[0], **options)
        train(self.fixture.root, outputs[1], **options)
        # Held-out labels, vectors, and observation counts differ radically;
        # neither fitting nor early stopping may observe any of them.
        for row in self.fixture.rows:
            if row[0] == "test":
                row[4], row[5], row[6], row[7] = 1 - row[4], 10000, 100, -100
        self.fixture.refresh()
        third = train(self.fixture.root, outputs[2], **options)
        for name in ("model.json", "policy.json", "parity.jsonl"):
            self.assertEqual((outputs[0] / name).read_bytes(), (outputs[1] / name).read_bytes())
            self.assertEqual((outputs[0] / name).read_bytes(), (outputs[2] / name).read_bytes())
        self.assertNotEqual(first["inputSha256"], third["inputSha256"])
        self.assertEqual(["train", "valid"], first["splitsUsed"])
        self.assertEqual("uniform-state-action-row", first["weightPolicy"])
        self.assertFalse(first["observationsUsedAsWeight"])
        self.assertEqual(SCORE_SEMANTICS, first["scoreSemantics"])
        self.assertEqual(("mean", 1), (first["labelSelection"], first["exportFormat"]))
        self.assertEqual(DEFAULT_SEED, first["parameters"]["random_seed"])
        self.assertEqual((18, 6), (first["parityRows"], first["parityStates"]))
        self.assertLessEqual(first["maxPolicyScoreDifference"], 1e-4)
        for output in outputs:
            log = read_json(output / "train-log.json")
            for name, artifact in log["artifacts"].items():
                self.assertEqual(artifact["sha256"], sha256(output / name))
            self.assertEqual(log["modelSha256"], sha256(output / "policy.json"))
            raw, canonical = read_json(output / "model.raw.json"), read_json(output / "model.json")
            for field in ("oblivious_trees", "features_info", "scale_and_bias"):
                self.assertEqual(raw[field], canonical[field])


class ConstructionPolicyTest(unittest.TestCase):
    @staticmethod
    def row(action, state="s", features=(0.25,)):
        return ActionRow("train", "g", state, action, 1 / 3, 3, features)

    def test_training_limits_and_rmse_semantics(self):
        parameters = training_parameters()
        self.assertEqual((256, 6, 1, "CPU", "RMSE", "SymmetricTree"),
                         tuple(parameters[name] for name in ("iterations", "depth", "thread_count", "task_type",
                                                             "loss_function", "grow_policy")))
        self.assertTrue(parameters["use_best_model"])
        for options in ({"iterations": 257}, {"iterations": True}, {"depth": 0}, {"depth": 7},
                        {"seed": -1}, {"seed": 2**31}, {"early_stopping_rounds": 0}):
            with self.assertRaises(ValueError):
                training_parameters(**options)

    def test_parity_selection_uses_complete_groups_including_noncontiguous_input(self):
        rows = [self.row("a"), self.row("x", "other"), self.row("b"), self.row("c")]
        self.assertEqual((rows[0], rows[2], rows[3]), select_parity_rows(rows, 3))
        self.assertEqual((rows[1],), select_parity_rows(rows, 2))
        with self.assertRaisesRegex(ValueError, "complete observed"):
            select_parity_rows([rows[0], rows[2]], 1)
        with self.assertRaisesRegex(ValueError, "positive"):
            select_parity_rows(rows, 0)

    def test_near_ties_are_transitive_and_include_the_exact_epsilon_boundary(self):
        rows = [self.row("c"), self.row("b"), self.row("a")]
        self.assertEqual((2, 1, 0), action_order(rows, [0, 0.75e-6, 1.5e-6]))
        self.assertEqual((2, 1, 0), action_order(rows, [1, 1, 1]))
        self.assertEqual((1, 0), action_order(rows[:2], [1e-6, 0]))
        reversed_rows = list(reversed(rows))
        order = action_order(reversed_rows, [1.5e-6, 0.75e-6, 0])
        self.assertEqual(("a", "b", "c"), tuple(reversed_rows[index].action_key for index in order))
        for invalid in ([0], [0, float("nan"), 0]):
            with self.assertRaises(ValueError):
                action_order(rows, invalid)

    def test_export_preserves_double_bias_maps_features_and_expands_constant_trees(self):
        names = ("test_value",)
        schema = FeatureSchema(1, 1, names, hashlib.sha256("\n".join(names).encode()).hexdigest()[:16])
        source = {"features_info": {"float_features": [{"feature_index": 0, "flat_feature_index": 0,
                                                        "feature_id": names[0], "borders": [0.25]}]},
                  "oblivious_trees": [
                      {"splits": [{"split_type": "FloatFeature", "float_feature_index": 0, "border": 0.25}],
                       "leaf_values": [0.125, 0.75]},
                      {"splits": None, "leaf_values": [0.1]},
                  ], "scale_and_bias": [1.00000000000001, [0.123456789012345]]}
        original = deepcopy(source)
        policy, binary, constants = export_policy(source, schema)
        self.assertEqual(original, source)
        self.assertEqual(1, constants)
        self.assertEqual("construction-policy", policy["role"])
        self.assertEqual(SCORE_SEMANTICS, policy["scoreSemantics"])
        self.assertEqual(BEST_SCORE_SEMANTICS, export_policy(source, schema, BEST_SCORE_SEMANTICS)[0]["scoreSemantics"])
        with self.assertRaisesRegex(ValueError, "semantics"):
            export_policy(source, schema, "sum-of-action-logits")
        self.assertEqual(source["scale_and_bias"][1][0], policy["model"]["bias"])
        self.assertEqual(source["scale_and_bias"][0], policy["model"]["scale"])
        self.assertEqual([1, 1], policy["model"]["depths"])
        self.assertEqual([0, 2], policy["model"]["leafOffsets"])
        # Strict > threshold: equality chooses the lower leaf, as on Android.
        self.assertAlmostEqual(0.34845679050246343, score_model(binary, [0.25]))
        self.assertGreater(score_model(binary, [0.2500001]), score_model(binary, [0.25]))
        rows = [self.row("a"), self.row("b", features=(0.5,))]
        scores = [score_model(binary, row.values) for row in rows]
        records, summary = parity_records(rows, scores, binary)
        self.assertEqual((2, 1), (summary["parityRows"], summary["parityStates"]))
        self.assertEqual(scores[0], records[0]["expectedScore"])
        with self.assertRaisesRegex(ValueError, "tolerance"):
            parity_records(rows, [scores[0] + 0.01, scores[1]], binary)


if __name__ == "__main__":
    unittest.main()
