from copy import deepcopy
import json
import math
import os
from pathlib import Path
import tempfile
import unittest

from dataset.split import content_hash, sha256
from dataset.task_io import read_json, write_json
from train.data import CandidateRow, Dataset, FeatureSchema
from train.evaluate import METRICS, evaluate_groups, evaluation_records, markdown_report, predict, rank_candidates


def candidate(key, rank, survival, score, family="SELF", failures=()):
    return {"planKey": key, "ruleRank": rank, "survival": survival, "score": score, "family": family,
            "failedStates": [{"stateId": f"s{index}", "mutations": [mutation]} for index, mutation in enumerate(failures, 1)]}


def group(group_id, candidates, mutations=(), split="test", family="list"):
    return {"groupId": group_id, "split": split, "source": "real" if split == "real-test" else "fixture",
            "app": "example.app", "screenFamily": family, "mutations": list(mutations),
            "candidateCount": len(candidates), "candidates": candidates}


def three_groups():
    return [group("g1", [candidate("a", 0, 1, 0), candidate("b", 1, .5, 2, "SELF_INDEX", ["shuffle"]),
                         candidate("c", 2, 0, 1)], ["shuffle"]),
            group("g2", [candidate("a", 0, 0, 4), candidate("b", 1, .5, 3),
                         candidate("c", 2, 0, 2), candidate("d", 3, 1, 1)], ["retext"]),
            group("g3", [candidate("a", 0, .5, 1), candidate("b", 1, 1, 2)], ["wrap"])]


class EvaluateTest(unittest.TestCase):
    def test_all_six_metrics_match_three_hand_computed_groups(self):
        report = evaluate_groups(three_groups())
        test = report["test"]
        self.assertEqual(3, test["groups"])
        expected = {"top1Survival": .5, "ruleTop1Survival": .5, "regret": .5,
                    "perfectRate": 1 / 3, "worseThanRule": 1 / 3, "top3Recall": 2 / 3}
        for metric, value in expected.items():
            with self.subTest(metric=metric):
                self.assertAlmostEqual(value, test[metric])
        self.assertEqual("g1", report["failures"][0]["groupId"])
        self.assertEqual(["shuffle"], report["failures"][0]["modelFailedMutations"])
        self.assertEqual(("b", "a"), (report["failures"][0]["modelPlanKey"], report["failures"][0]["rulePlanKey"]))

    def test_absent_real_test_is_null_and_not_perfect(self):
        real = evaluate_groups(three_groups())["realTest"]
        self.assertEqual(0, real["groups"])
        self.assertIsNone(real["coverage"])
        self.assertTrue(all(real[metric] is None for metric in METRICS))

    def test_no_candidate_groups_are_in_coverage_and_counts(self):
        report = evaluate_groups([*three_groups(), group("empty", [])])
        self.assertEqual(3, report["test"]["groups"])
        self.assertEqual(4, report["test"]["totalGroups"])
        self.assertEqual(1, report["test"]["groupsWithoutCandidates"])
        self.assertEqual(.75, report["test"]["coverage"])
        self.assertEqual(1, report["breakdowns"]["test"]["family"]["<no-candidate>"]["totalGroups"])
        empty = evaluate_groups([group("empty", [])])["test"]
        self.assertEqual(0, empty["coverage"])
        self.assertIsNone(empty["top1Survival"])

    def test_exact_and_near_ties_follow_rules_deterministically(self):
        candidates = [candidate("a", 0, 1, 0), candidate("b", 1, 0, .5e-6)]
        self.assertEqual("a", rank_candidates(candidates)[0]["planKey"])
        self.assertEqual("a", rank_candidates(list(reversed(candidates)))[0]["planKey"])
        candidates[1]["score"] = 0
        self.assertEqual("a", rank_candidates(candidates)[0]["planKey"])

    def test_near_tie_chain_uses_nonoverlapping_batches(self):
        candidates = [candidate("a", 0, 1, 0), candidate("b", 1, .5, .75e-6), candidate("c", 2, 0, 1.5e-6)]
        self.assertEqual(["b", "c", "a"], [item["planKey"] for item in rank_candidates(candidates)])
        self.assertEqual(["b", "c", "a"], [item["planKey"] for item in rank_candidates(list(reversed(candidates)))])

    def test_breakdowns_keep_test_and_real_separate_and_mutations_overlap(self):
        groups = three_groups()
        groups[0]["mutations"] = ["shuffle", "wrap"]
        groups.append(group("real", [candidate("x", 0, 1, 1)], ["locale"], "real-test", "real-screen"))
        report = evaluate_groups(groups)
        self.assertEqual(1, report["realTest"]["groups"])
        self.assertEqual(2, report["breakdowns"]["test"]["mutation"]["wrap"]["groups"])
        self.assertEqual(1, report["breakdowns"]["test"]["family"]["SELF_INDEX"]["groups"])
        self.assertEqual(["real"], list(report["breakdowns"]["realTest"]["source"]))

    def test_failures_are_limited_to_fifty_in_deterministic_loss_order(self):
        groups = [group(f"g{index:03}", [candidate("r", 0, 1, 0), candidate("m", 1, .5 if index % 2 else 0, 1)])
                  for index in range(60)]
        a, b = evaluate_groups(groups), evaluate_groups(list(reversed(groups)))
        self.assertEqual(a, b)
        self.assertEqual(60, a["failureGroups"])
        self.assertEqual(50, len(a["failures"]))
        self.assertEqual("g000", a["failures"][0]["groupId"])
        self.assertEqual("g058", a["failures"][29]["groupId"])

    def test_duplicate_missing_nan_and_infinite_inputs_are_rejected(self):
        base = three_groups()
        invalids = []
        duplicate_group = deepcopy(base)
        duplicate_group.append(duplicate_group[0])
        invalids.append(duplicate_group)
        for value in (float("nan"), float("inf"), float("-inf")):
            changed = deepcopy(base)
            changed[0]["candidates"][0]["score"] = value
            invalids.append(changed)
        changed = deepcopy(base)
        changed[0]["candidates"].pop()
        invalids.append(changed)
        changed = deepcopy(base)
        changed[0]["candidates"][1]["planKey"] = "a"
        invalids.append(changed)
        changed = deepcopy(base)
        changed[0]["candidates"][1]["ruleRank"] = 0
        invalids.append(changed)
        changed = deepcopy(base)
        changed[0]["candidates"][1]["survival"] = -1
        invalids.append(changed)
        for index, data in enumerate(invalids):
            with self.subTest(index=index), self.assertRaises(ValueError):
                evaluate_groups(data)

    def test_real_data_cannot_be_evaluated_as_fixture_test(self):
        data = three_groups()
        data[0]["source"] = "real"
        with self.assertRaisesRegex(ValueError, "real-test"):
            evaluate_groups(data)

    def test_report_has_all_metrics_failures_and_coverage(self):
        report = evaluate_groups([*three_groups(), group("empty", [])])
        markdown = markdown_report(report)
        self.assertIn("3 / 4", markdown)
        self.assertIn("n/a", markdown)
        for metric in METRICS:
            self.assertIn(metric, markdown)
        self.assertIn("g1", markdown)
        self.assertIn("shuffle", markdown)

    def test_export_adapter_checks_exact_candidates_and_rule_choice(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            groups = three_groups()
            metadata, rows, labels, rules, scores = {}, [], [], [], []
            for item in groups:
                directory = root / item["groupId"]
                directory.mkdir()
                states = []
                for index in range(3):
                    filename = f"s{index}.layout.json"
                    write_json(directory / filename, {"root": {"text": str(index)}})
                    states.append({"stateId": f"s{index}", "snapshot": filename, "targets": [[]],
                                   "mutations": item["mutations"] if index else []})
                task_path = directory / "task.cgtask.json"
                write_json(task_path, {"format": 1, "groupId": item["groupId"], "source": "fixture", "app": item["app"],
                                       "screenFamily": item["screenFamily"], "request": {"scope": "single", "attributes": [], "action": "none"},
                                       "states": states})
                metadata[item["groupId"]] = {**{field: item[field] for field in ("groupId", "split", "source", "app", "screenFamily")},
                                             "task": str(task_path)}
                for candidate in item["candidates"]:
                    rows.append(CandidateRow("test", item["groupId"], candidate["planKey"], candidate["survival"],
                                             (candidate["ruleRank"] / item["candidateCount"],)))
                    scores.append(candidate["score"])
                    failed = [{"stateId": f"s{index + 1}", "mutations": item["mutations"], "reason": "WRONG_TARGET",
                               "extraCount": 1, "missingCount": 1} for index in range(round((1 - candidate["survival"]) * 2))]
                    labels.append({"format": 1, "groupId": item["groupId"], "planKey": candidate["planKey"], "family": candidate["family"],
                                   "survival": candidate["survival"], "ruleSelected": candidate["ruleRank"] == 0, "failedStates": failed})
                first = item["candidates"][0]
                rules.append({"format": 1, **{field: item[field] for field in ("groupId", "source", "app", "screenFamily")},
                              "candidateCount": item["candidateCount"], "rulePlanKey": first["planKey"], "ruleSurvival": first["survival"]})
            for name, records in (("labels.jsonl", labels), ("rules.jsonl", rules)):
                (root / name).write_text("".join(json.dumps(record) + "\n" for record in records), encoding="utf-8")
            manifest = {"artifacts": {"labels": {"status": "complete", "file": "labels.jsonl"},
                                      "rules": {"status": "complete", "file": "rules.jsonl"}}}
            manifest["artifacts"].update({
                "tasks": {"sha256": content_hash([{"groupId": group_id, "sha256": sha256(item["task"])}
                                                   for group_id, item in sorted(metadata.items())])},
                "snapshots": {"sha256": content_hash([{"groupId": group_id, "stateId": f"s{index}",
                                                       "sha256": sha256(Path(item["task"]).parent / f"s{index}.layout.json")}
                                                      for group_id, item in sorted(metadata.items()) for index in range(3)])},
            })
            dataset = Dataset(root, FeatureSchema(1, ("rule_rank_norm",), "unused"), manifest, {}, metadata, tuple(rows), {})
            report = evaluate_groups(evaluation_records(dataset, rows, scores))
            self.assertEqual(evaluate_groups(three_groups())["test"], report["test"])
            with self.assertRaisesRegex(ValueError, "Missing prediction"):
                evaluation_records(dataset, rows[:-1], scores[:-1])
            with self.assertRaisesRegex(ValueError, "duplicate prediction"):
                evaluation_records(dataset, rows + rows[:1], scores + scores[:1])

    def test_current_target_reselection_or_snapshot_replacement_rejects_frozen_labels(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            task_path = root / "task.cgtask.json"
            snapshot_path = root / "s0.layout.json"
            write_json(snapshot_path, {"root": {"text": "root", "children": [{"text": "other valid target"}]}})
            task = {"format": 1, "groupId": "same-group", "source": "fixture", "app": "fixture", "screenFamily": "screen",
                    "request": {"scope": "single", "attributes": [], "action": "none"},
                    "states": [{"stateId": state_id, "snapshot": snapshot_path.name, "mutations": [], "targets": [[]]}
                               for state_id in ("capture", "future")]}
            write_json(task_path, task)
            metadata = {"same-group": {"groupId": "same-group", "split": "test", "source": "fixture", "app": "fixture",
                                        "screenFamily": "screen", "task": str(task_path)}}
            manifest = {"artifacts": {
                "tasks": {"sha256": content_hash([{"groupId": "same-group", "sha256": sha256(task_path)}])},
                "snapshots": {"sha256": content_hash([{"groupId": "same-group", "stateId": state_id, "sha256": sha256(snapshot_path)}
                                                       for state_id in ("capture", "future")])},
                "labels": {"status": "complete", "file": "labels.jsonl"},
                "rules": {"status": "complete", "file": "rules.jsonl"},
            }}
            label = {"format": 1, "groupId": "same-group", "planKey": "plan", "family": "SELF", "survival": 1,
                     "ruleSelected": True, "failedStates": []}
            rule = {"format": 1, "groupId": "same-group", "source": "fixture", "app": "fixture", "screenFamily": "screen",
                    "candidateCount": 1, "rulePlanKey": "plan", "ruleSurvival": 1}
            for filename, record in (("labels.jsonl", label), ("rules.jsonl", rule)):
                (root / filename).write_text(json.dumps(record) + "\n", encoding="utf-8")
            rows, scores = (CandidateRow("test", "same-group", "plan", 1., (0.,)),), [1.]
            dataset = Dataset(root, FeatureSchema(1, ("rule_rank_norm",), "unused"), manifest, {}, metadata, rows, {})
            self.assertEqual(1, evaluate_groups(evaluation_records(dataset, rows, scores))["test"]["groups"])
            original_task = task_path.read_bytes()
            task["states"][0]["targets"] = [[0]]
            write_json(task_path, task)
            with self.assertRaisesRegex(ValueError, "Current tasks differ"):
                evaluation_records(dataset, rows, scores)
            task_path.write_bytes(original_task)
            snapshot = read_json(snapshot_path)
            snapshot["root"]["text"] = "replacement layout; group and mutations unchanged"
            write_json(snapshot_path, snapshot)
            with self.assertRaisesRegex(ValueError, "Current snapshots differ"):
                evaluation_records(dataset, rows, scores)

    def test_evaluation_requires_current_input_digests_even_when_labels_are_empty(self):
        dataset = Dataset(Path("."), FeatureSchema(1, ("rule_rank_norm",), "unused"), {"artifacts": {}}, {}, {}, (), {})
        with self.assertRaisesRegex(ValueError, "Current tasks differ"):
            evaluation_records(dataset, [], [])

    @unittest.skipUnless(os.environ.get("CODEGEN_TEST_TRAINING") == "1", "set CODEGEN_TEST_TRAINING=1 in Python 3.12 training venv")
    def test_actual_catboost_ranker_predictions_match_cbm_and_json(self):
        from train.data import read_dataset
        from train.test_train import DatasetFixture
        from train.train import fit_dataset

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fixture = DatasetFixture(root / "dataset")
            dataset = read_dataset(fixture.root)
            fit_dataset(dataset, root / "run", iterations=20, early_stopping_rounds=5)
            rows = dataset.rows_for("test")
            cbm = predict(root / "run" / "model.cbm", rows, dataset.schema)
            exported = predict(root / "run" / "model.json", rows, dataset.schema)
            self.assertEqual(len(rows), len(cbm))
            self.assertTrue(all(math.isfinite(value) for value in cbm))
            for left, right in zip(cbm, exported):
                self.assertAlmostEqual(left, right, places=12)


if __name__ == "__main__":
    unittest.main()
