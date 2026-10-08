"""Guard the third-round development/holdout boundary and the preregistered choice."""

import json
from pathlib import Path
import tempfile
import unittest

from construction.select_third import MEASURES, TIE_BREAK, decide, digest, select


METHODS = ("baseline", "fair_heuristic", "fair_model_r2", "fair_model_mean", "fair_model_best")


class ThirdSelectionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.protocol_dir = self.root / "protocol"
        self.development = self.root / "development"
        self.protocol_dir.mkdir()
        self.development.mkdir()
        self.protocol = {
            "role": "construction-third-experiment", "sourceSha256": {"search.kt": "frozen"},
            "methods": list(METHODS), "candidateMethods": ["fair_model_mean", "fair_model_best"],
            "referenceMethod": "fair_model_r2",
            "selectionCriteria": {"metrics": list(MEASURES), "selectionOrder": ["top1Survival", "bestSurvival", "coverage"],
                                  "selectionSplit": "valid", "tieBreak": TIE_BREAK},
            "developmentGroups": [{"groupId": "t", "split": "train"}, {"groupId": "v", "split": "valid"}],
        }
        self.write_protocol()
        # Both candidates beat the reference on valid top1; best also beats mean on valid bestSurvival.
        quality = {"baseline": .5, "fair_heuristic": .25, "fair_model_r2": .5, "fair_model_mean": .75, "fair_model_best": .75}
        self.rows = [{"groupId": group, "split": split, "cohort": "captured-development", "method": method,
                      "coverage": 1.0, "bestSurvival": 1.0 if method == "fair_model_best" else .75,
                      "top1Survival": quality[method], "stopReason": "EXHAUSTED", "error": None}
                     for group, split in (("t", "train"), ("v", "valid")) for method in METHODS]
        self.write_results()

    def write_protocol(self):
        (self.protocol_dir / "protocol.json").write_text(json.dumps(self.protocol), encoding="utf-8")

    def write_results(self):
        path = self.development / "results.jsonl"
        path.write_text("".join(json.dumps(row) + "\n" for row in self.rows), encoding="utf-8")
        (self.development / "metrics.json").write_text(json.dumps({
            "phase": "develop", "sourceSha256": self.protocol["sourceSha256"],
            "protocolSha256": digest(self.protocol_dir / "protocol.json"),
            "resultsSha256": digest(path), "summary": []}), encoding="utf-8")

    def row(self, group, method):
        return next(row for row in self.rows if row["groupId"] == group and row["method"] == method)

    def test_selects_by_frozen_validation_order_and_binds_inputs(self):
        decision = select(self.protocol_dir, self.development)
        self.assertTrue(decision["passed"])
        self.assertEqual("fair_model_best", decision["chosenMethod"])
        self.assertEqual(["fair_model_best", "fair_model_mean"], decision["candidateOrder"])
        self.assertEqual([], decision["reasons"])
        self.assertEqual(digest(self.development / "metrics.json"), decision["developmentMetricsSha256"])
        self.assertEqual(self.protocol["sourceSha256"], decision["sourceSha256"])
        self.assertEqual(10, len(decision["comparison"]))
        with self.assertRaises(FileExistsError):
            select(self.protocol_dir, self.development)

    def test_top1_outranks_best_survival_and_ties_prefer_the_best_label(self):
        self.row("v", "fair_model_mean").update(top1Survival=1.0, bestSurvival=1.0)
        self.write_results()
        decision = decide(self.protocol_dir, self.development)
        self.assertEqual("fair_model_mean", decision["chosenMethod"])
        self.row("v", "fair_model_mean").update(top1Survival=.75, bestSurvival=1.0)
        self.write_results()
        self.assertEqual("fair_model_best", decide(self.protocol_dir, self.development)["chosenMethod"])
        # Training quality never participates in the choice between candidates.
        self.row("t", "fair_model_mean").update(top1Survival=1.0, bestSurvival=1.0)
        self.write_results()
        self.assertEqual("fair_model_best", decide(self.protocol_dir, self.development)["chosenMethod"])

    def test_chosen_candidate_must_not_regress_the_reference_on_validation(self):
        for row in self.rows:
            if row["method"] == "fair_model_best":
                row["top1Survival"] = 1.0
        self.row("v", "fair_model_best").update(coverage=0, bestSurvival=0, top1Survival=0)
        self.write_results()
        decision = decide(self.protocol_dir, self.development)
        self.assertEqual("fair_model_mean", decision["chosenMethod"])
        self.assertTrue(decision["passed"])
        self.row("v", "fair_model_mean")["top1Survival"] = .25
        self.write_results()
        decision = decide(self.protocol_dir, self.development)
        self.assertEqual("fair_model_mean", decision["chosenMethod"])
        self.assertFalse(decision["passed"])
        self.assertIn("Validation top1Survival regressed against fair_model_r2", decision["reasons"])

    def test_equal_validation_allowed_only_with_strict_development_gain(self):
        for row in self.rows:
            if row["method"] in ("fair_model_mean", "fair_model_best"):
                row["top1Survival"], row["bestSurvival"] = .5, .75
        self.row("t", "fair_model_best")["top1Survival"] = .75
        self.write_results()
        self.assertTrue(decide(self.protocol_dir, self.development)["passed"])
        self.row("t", "fair_model_best")["top1Survival"] = .5
        self.write_results()
        decision = decide(self.protocol_dir, self.development)
        self.assertFalse(decision["passed"])
        self.assertIn("No strict quality improvement over fair_model_r2 in train or valid", decision["reasons"])

    def test_zero_candidate_group_counts_as_quality_loss_and_cannot_be_dropped(self):
        row = self.row("v", "fair_model_best")
        row.update(coverage=0, bestSurvival=0, top1Survival=0)
        self.write_results()
        decision = decide(self.protocol_dir, self.development)
        self.assertEqual("fair_model_mean", decision["chosenMethod"])
        self.rows.remove(row)
        self.write_results()
        with self.assertRaisesRegex(ValueError, "Missing"):
            decide(self.protocol_dir, self.development)

    def test_heldout_group_and_foreign_methods_are_rejected(self):
        self.rows.append(dict(self.rows[0], groupId="unseen", split="test"))
        self.write_results()
        with self.assertRaisesRegex(ValueError, "Held-out"):
            decide(self.protocol_dir, self.development)
        self.rows.pop()
        self.rows.append(dict(self.rows[0], method="legacy_model"))
        self.write_results()
        with self.assertRaisesRegex(ValueError, "Unexpected development cohort or method"):
            decide(self.protocol_dir, self.development)

    def test_rejects_deadline_and_policy_failure_in_any_comparison_run(self):
        for stop in ("HARD_DEADLINE", "DEADLINE", "POLICY_FAILURE", "FAILED"):
            with self.subTest(stop=stop):
                self.rows[0]["stopReason"] = stop
                self.write_results()
                self.assertFalse(decide(self.protocol_dir, self.development)["passed"])

    def test_rejects_result_protocol_or_criteria_changes(self):
        with (self.development / "results.jsonl").open("a", encoding="utf-8") as stream:
            stream.write("\n")
        with self.assertRaisesRegex(ValueError, "digest changed"):
            decide(self.protocol_dir, self.development)
        self.write_results()
        with (self.protocol_dir / "protocol.json").open("a", encoding="utf-8") as stream:
            stream.write("\n")
        with self.assertRaisesRegex(ValueError, "different frozen protocol"):
            decide(self.protocol_dir, self.development)
        for field, value in (("tieBreak", "fair_model_mean"), ("selectionOrder", ["coverage", "top1Survival", "bestSurvival", "extra"]),
                             ("selectionSplit", "train"), ("metrics", ["top1Survival"])):
            with self.subTest(field=field):
                criteria = dict(self.protocol["selectionCriteria"])
                criteria[field] = value
                protocol = dict(self.protocol, selectionCriteria=criteria)
                (self.protocol_dir / "protocol.json").write_text(json.dumps(protocol), encoding="utf-8")
                self.write_results()
                with self.assertRaisesRegex(ValueError, "criteria differ"):
                    decide(self.protocol_dir, self.development)
        self.write_protocol()
        self.write_results()
        protocol = dict(self.protocol, role="construction-second-experiment")
        (self.protocol_dir / "protocol.json").write_text(json.dumps(protocol), encoding="utf-8")
        self.write_results()
        with self.assertRaisesRegex(ValueError, "third construction experiment"):
            decide(self.protocol_dir, self.development)

    def test_duplicate_or_nonfinite_results_do_not_enter_means(self):
        self.rows.append(self.rows[0])
        self.write_results()
        with self.assertRaisesRegex(ValueError, "Duplicate group"):
            decide(self.protocol_dir, self.development)
        self.rows.pop()
        self.row("v", "fair_model_best")["top1Survival"] = float("nan")
        self.write_results()
        with self.assertRaisesRegex(ValueError, "Invalid top1Survival"):
            decide(self.protocol_dir, self.development)

    def test_holdout_metrics_and_changed_source_identity_are_rejected(self):
        path = self.development / "metrics.json"
        for field, value in (("phase", "holdout"), ("sourceSha256", {"search.kt": "changed"})):
            with self.subTest(field=field):
                self.write_results()
                metrics = json.loads(path.read_text(encoding="utf-8"))
                metrics[field] = value
                path.write_text(json.dumps(metrics), encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "frozen sources"):
                    decide(self.protocol_dir, self.development)


if __name__ == "__main__":
    unittest.main()
