import hashlib
import unittest

from package.export_parity import float32, parity_records, select_prefix
from train.data import CandidateRow, FeatureSchema


NAMES = ("value", "rule_rank_norm")
SCHEMA = FeatureSchema(1, NAMES, hashlib.sha256("\n".join(NAMES).encode()).hexdigest()[:16])


def row(group, index, count, value=0.0):
    return CandidateRow("test", group, "plan-" + str(index), 1.0, (value, index / count))


class ExportParityTest(unittest.TestCase):
    def test_first_candidates_include_the_end_of_the_last_group(self):
        rows = [row("g1", index, 3) for index in range(3)] + [row("g2", index, 2) for index in range(2)]
        self.assertEqual(5, len(select_prefix(rows, 4)))
        self.assertEqual(3, len(select_prefix(rows, 3)))
        self.assertEqual(5, len(select_prefix(rows, 100)))
        with self.assertRaises(ValueError):
            select_prefix(rows, 0)
        with self.assertRaises(ValueError):
            select_prefix([], 5)

    def test_wire_conversion_uses_kotlin_float_values_at_a_border(self):
        value = .3465736
        border = .3465735912322998
        self.assertGreater(value, border)
        self.assertEqual(border, float32(value))
        self.assertEqual(border, select_prefix([row("g", 0, 1, value)], 1)[0].values[0])
        for value in (float("nan"), float("inf"), 1e100, True):
            with self.assertRaises(ValueError):
                float32(value)

    def test_expected_top_and_rule_order_are_independent_of_candidate_input_order(self):
        rows = [row("g", index, 3) for index in range(3)]
        records, summary = parity_records(rows, [0.0, 2.0, 1.0], [0.0, 2.0 + 1e-6, 1.0], SCHEMA)
        self.assertEqual([False, True, False], [record["expectedTop"] for record in records])
        self.assertTrue(all(record["groupSize"] == 3 for record in records))
        self.assertEqual([0, 1, 2], [record["ruleRank"] for record in records])
        self.assertEqual(1, summary["groups"])
        reversed_records, _ = parity_records(list(reversed(rows)), [1.0, 2.0, 0.0], [1.0, 2.0, 0.0], SCHEMA)
        self.assertEqual("plan-1", next(record["planKey"] for record in reversed_records if record["expectedTop"]))

    def test_small_score_error_does_not_hide_a_changed_top_choice(self):
        rows = [row("g", index, 2) for index in range(2)]
        with self.assertRaisesRegex(ValueError, "changes the top choice"):
            parity_records(rows, [0.0, .00001], [.00002, .00001], SCHEMA)
        with self.assertRaisesRegex(ValueError, "above"):
            parity_records(rows, [0.0, 1.0], [0.0, 1.001], SCHEMA)

    def test_near_ties_follow_the_shared_rule_rank_batches(self):
        rows = [row("g", index, 3) for index in range(3)]
        records, _ = parity_records(rows, [0.0, .75e-6, 1.5e-6], [0.0, .75e-6, 1.5e-6], SCHEMA)
        self.assertEqual("plan-1", next(record["planKey"] for record in records if record["expectedTop"]))

    def test_duplicate_missing_and_nonfinite_predictions_are_rejected(self):
        rows = [row("g", 0, 1)]
        with self.assertRaises(ValueError):
            parity_records(rows, [], [1.0], SCHEMA)
        with self.assertRaises(ValueError):
            parity_records(rows, [1.0], [float("nan")], SCHEMA)
        with self.assertRaises(ValueError):
            parity_records(rows * 2, [1.0, 1.0], [1.0, 1.0], SCHEMA)
