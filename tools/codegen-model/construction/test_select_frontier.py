"""Guard the development/holdout boundary and complete-task denominators."""

import json
from pathlib import Path
import tempfile
import unittest

from construction.select_frontier import METHODS, decide, digest, select


class FrontierSelectionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.protocol_dir = self.root / "protocol"
        self.development = self.root / "development"
        self.protocol_dir.mkdir()
        self.development.mkdir()
        self.protocol = {"sourceSha256": {"search.kt": "frozen"}, "developmentGroups": [
            {"groupId": "t", "split": "train"}, {"groupId": "v", "split": "valid"}]}
        (self.protocol_dir / "protocol.json").write_text(json.dumps(self.protocol), encoding="utf-8")
        self.rows = [{"groupId": group, "split": split, "cohort": "captured-development",
                      "method": method, "coverage": 1.0, "bestSurvival": 1.0,
                      "top1Survival": 1.0 if method == "fair_model" else .5,
                      "stopReason": "EXHAUSTED", "error": None}
                     for group, split in (("t", "train"), ("v", "valid")) for method in METHODS]
        self.write_results()

    def write_results(self):
        path = self.development / "results.jsonl"
        path.write_text("".join(json.dumps(row) + "\n" for row in self.rows), encoding="utf-8")
        (self.development / "metrics.json").write_text(json.dumps({
            "phase": "develop", "sourceSha256": self.protocol["sourceSha256"],
            "protocolSha256": digest(self.protocol_dir / "protocol.json"),
            "resultsSha256": digest(path), "summary": []}), encoding="utf-8")

    def fair_valid(self):
        return next(row for row in self.rows if row["groupId"] == "v" and row["method"] == "fair_model")

    def test_selects_from_raw_development_and_binds_inputs_without_heldout_files(self):
        decision = select(self.protocol_dir, self.development)
        self.assertTrue(decision["passed"])
        self.assertEqual([], decision["reasons"])
        self.assertEqual(digest(self.development / "metrics.json"), decision["developmentMetricsSha256"])
        self.assertEqual(self.protocol["sourceSha256"], decision["sourceSha256"])
        with self.assertRaises(FileExistsError):
            select(self.protocol_dir, self.development)

    def test_validation_regression_cannot_be_hidden_by_training_gain(self):
        self.fair_valid()["top1Survival"] = 0
        self.write_results()
        decision = select(self.protocol_dir, self.development)
        self.assertFalse(decision["passed"])
        self.assertIn("Validation top1Survival regressed", decision["reasons"])
        self.assertTrue((self.protocol_dir / "selection.json").is_file())

    def test_equal_validation_allowed_only_with_strict_development_gain(self):
        self.fair_valid()["top1Survival"] = .5
        self.write_results()
        self.assertTrue(decide(self.protocol_dir, self.development)["passed"])
        for row in self.rows:
            row["top1Survival"] = .5
        self.write_results()
        self.assertFalse(decide(self.protocol_dir, self.development)["passed"])

    def test_zero_candidate_group_counts_as_quality_loss_and_cannot_be_dropped(self):
        row = self.fair_valid()
        row.update(coverage=0, bestSurvival=0, top1Survival=0)
        self.write_results()
        self.assertFalse(decide(self.protocol_dir, self.development)["passed"])
        self.rows.remove(row)
        self.write_results()
        with self.assertRaisesRegex(ValueError, "Missing"):
            decide(self.protocol_dir, self.development)

    def test_heldout_group_is_rejected_even_if_label_would_favor_selection(self):
        self.rows.append(dict(self.rows[0], groupId="unseen", split="test"))
        self.write_results()
        with self.assertRaisesRegex(ValueError, "Held-out"):
            decide(self.protocol_dir, self.development)

    def test_rejects_deadline_and_policy_failure_in_any_comparison_run(self):
        for stop in ("HARD_DEADLINE", "DEADLINE", "POLICY_FAILURE", "FAILED"):
            with self.subTest(stop=stop):
                self.rows[0]["stopReason"] = stop
                self.write_results()
                self.assertFalse(decide(self.protocol_dir, self.development)["passed"])

    def test_rejects_result_or_protocol_digest_change(self):
        with (self.development / "results.jsonl").open("a", encoding="utf-8") as stream:
            stream.write("\n")
        with self.assertRaisesRegex(ValueError, "digest changed"):
            decide(self.protocol_dir, self.development)
        self.write_results()
        with (self.protocol_dir / "protocol.json").open("a", encoding="utf-8") as stream:
            stream.write("\n")
        with self.assertRaisesRegex(ValueError, "different frozen protocol"):
            decide(self.protocol_dir, self.development)

    def test_duplicate_or_nonfinite_results_do_not_enter_means(self):
        self.rows.append(self.rows[0])
        self.write_results()
        with self.assertRaisesRegex(ValueError, "Duplicate group"):
            decide(self.protocol_dir, self.development)
        self.rows.pop()
        self.fair_valid()["top1Survival"] = float("nan")
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
