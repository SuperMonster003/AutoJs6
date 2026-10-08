"""Guard the fourth-round admission: no regression against three baselines plus a construction gain."""

import json
from pathlib import Path
import tempfile
import unittest

from construction.select_fourth import MEASURES, decide, digest, select


METHODS = ("rule_pipeline", "baseline_tiny", "baseline_ranker", "construction_ranker", "merged_rule", "merged_tiny", "merged_ranker", "merged_ranker_shared")


class FourthSelectionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.protocol_dir = self.root / "protocol"
        self.development = self.root / "development"
        self.protocol_dir.mkdir()
        self.development.mkdir()
        self.protocol = {
            "role": "construction-fourth-experiment", "sourceSha256": {"search.kt": "frozen"}, "methods": list(METHODS),
            "selectionCriteria": {"chosenMethod": "merged_ranker", "noRegressionAgainst": ["rule_pipeline", "baseline_tiny", "baseline_ranker"],
                                  "strictGainAgainst": "baseline_ranker", "metrics": list(MEASURES), "noRegressionMeasure": "top1Survival",
                                  "strictGainMeasures": ["top1Survival", "top3BestSurvival"]},
            "developmentGroups": [{"groupId": "t", "split": "train"}, {"groupId": "v", "split": "valid"}],
        }
        self.write_protocol()
        top1 = {"rule_pipeline": .5, "baseline_tiny": .5, "baseline_ranker": .75, "construction_ranker": .5, "merged_rule": .5,
                "merged_tiny": .5, "merged_ranker": 1.0, "merged_ranker_shared": .75}
        self.rows = [{"groupId": group, "split": split, "cohort": "captured-development", "method": method,
                      "coverage": 1.0, "bestSurvival": 1.0, "top1Survival": top1[method], "top3BestSurvival": 1.0,
                      "stopReason": "EXHAUSTED", "error": None}
                     for group, split in (("t", "train"), ("v", "valid")) for method in METHODS]
        self.write_results()

    def write_protocol(self):
        (self.protocol_dir / "protocol.json").write_text(json.dumps(self.protocol), encoding="utf-8")

    def write_results(self):
        path = self.development / "results.jsonl"
        path.write_text("".join(json.dumps(row) + "\n" for row in self.rows), encoding="utf-8")
        (self.development / "metrics.json").write_text(json.dumps({
            "phase": "develop", "sourceSha256": self.protocol["sourceSha256"],
            "protocolSha256": digest(self.protocol_dir / "protocol.json"), "resultsSha256": digest(path), "summary": []}), encoding="utf-8")

    def row(self, group, method):
        return next(row for row in self.rows if row["groupId"] == group and row["method"] == method)

    def test_admits_merged_pipeline_and_binds_inputs(self):
        decision = select(self.protocol_dir, self.development)
        self.assertTrue(decision["passed"])
        self.assertEqual("merged_ranker", decision["chosenMethod"])
        self.assertEqual([], decision["reasons"])
        self.assertEqual(16, len(decision["comparison"]))
        self.assertEqual(digest(self.development / "metrics.json"), decision["developmentMetricsSha256"])
        with self.assertRaises(FileExistsError):
            select(self.protocol_dir, self.development)

    def test_each_baseline_can_veto_on_validation_top1(self):
        for reference in ("rule_pipeline", "baseline_tiny", "baseline_ranker"):
            with self.subTest(reference=reference):
                self.setUp()
                self.row("v", reference)["top1Survival"] = 1.0
                self.row("v", "merged_ranker")["top1Survival"] = .75
                self.write_results()
                decision = decide(self.protocol_dir, self.development)
                self.assertFalse(decision["passed"])
                self.assertIn(f"Validation top1Survival regressed against {reference}", decision["reasons"])

    def test_requires_strict_gain_over_the_refitted_baseline_on_top1_or_top3(self):
        for row in self.rows:
            if row["method"] in ("merged_ranker", "baseline_ranker"):
                row["top1Survival"], row["top3BestSurvival"] = .75, .75
        self.write_results()
        decision = decide(self.protocol_dir, self.development)
        self.assertFalse(decision["passed"])
        self.assertIn("No strict top1/top3 improvement over baseline_ranker in train or valid", decision["reasons"])
        self.row("t", "merged_ranker")["top3BestSurvival"] = 1.0
        self.write_results()
        self.assertTrue(decide(self.protocol_dir, self.development)["passed"])

    def test_failures_zero_candidates_and_heldout_rows_are_rejected(self):
        self.row("t", "merged_tiny")["stopReason"] = "HARD_DEADLINE"
        self.write_results()
        self.assertFalse(decide(self.protocol_dir, self.development)["passed"])
        self.row("t", "merged_tiny")["stopReason"] = "EXHAUSTED"
        self.row("v", "merged_ranker").update(coverage=0, bestSurvival=0, top1Survival=0, top3BestSurvival=0)
        self.write_results()
        self.assertFalse(decide(self.protocol_dir, self.development)["passed"])
        self.rows.append(dict(self.rows[0], groupId="unseen", split="test"))
        self.write_results()
        with self.assertRaisesRegex(ValueError, "Held-out"):
            decide(self.protocol_dir, self.development)
        self.rows.pop()
        self.rows.pop()
        self.write_results()
        with self.assertRaisesRegex(ValueError, "Missing"):
            decide(self.protocol_dir, self.development)

    def test_rejects_changed_criteria_protocol_or_results(self):
        criteria = dict(self.protocol["selectionCriteria"], chosenMethod="baseline_ranker")
        (self.protocol_dir / "protocol.json").write_text(json.dumps(dict(self.protocol, selectionCriteria=criteria)), encoding="utf-8")
        self.write_results()
        with self.assertRaisesRegex(ValueError, "criteria differ"):
            decide(self.protocol_dir, self.development)
        self.write_protocol()
        self.write_results()
        with (self.development / "results.jsonl").open("a", encoding="utf-8") as stream:
            stream.write("\n")
        with self.assertRaisesRegex(ValueError, "digest changed"):
            decide(self.protocol_dir, self.development)
        self.write_results()
        metrics = json.loads((self.development / "metrics.json").read_text(encoding="utf-8"))
        metrics["phase"] = "holdout"
        (self.development / "metrics.json").write_text(json.dumps(metrics), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "frozen sources"):
            decide(self.protocol_dir, self.development)


if __name__ == "__main__":
    unittest.main()
