import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from dataset.task_io import write_json
from dataset.split import sha256
from train.data import FeatureSchema
from train.queue import build_queue, read_candidates, run_queue


SCHEMA = FeatureSchema(1, ("rule_rank_norm",), hashlib.sha256(b"rule_rank_norm").hexdigest()[:16])


def queue_group(group_id, source="real", state_count=1):
    return {"format": 1, "groupId": group_id, "source": source, "app": "example.app", "screenFamily": "screen",
            "task": "/private/example.cgtask.json", "stateCount": state_count, "candidateCount": 2,
            "candidates": [{"planKey": "rule", "ruleRank": 0, "features": [0]},
                           {"planKey": "model", "ruleRank": 1, "features": [.5]}]}


class QueueTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        env = patch.dict(os.environ, {"CODEGEN_PRIVATE_DIR": str(self.root)})
        env.start()
        self.addCleanup(env.stop)

    def input_file(self, record):
        task_path = self.root / "task.cgtask.json"
        states = []
        for index in range(record["stateCount"]):
            snapshot_name = f"s{index}.layout.json"
            write_json(self.root / snapshot_name, {"root": {"text": "Confirmed capture target"}})
            states.append({"stateId": f"s{index}", "snapshot": snapshot_name, "mutations": [], "targets": [[]]})
        write_json(task_path, {"format": 1, "groupId": record["groupId"], "source": "real", "app": record["app"],
                               "screenFamily": record["screenFamily"], "request": {"scope": "single", "attributes": [], "action": "none"},
                               "states": states})
        record["task"] = str(task_path)
        record["taskSha256"] = sha256(task_path)
        record["captureSnapshotSha256"] = sha256(self.root / "s0.layout.json")
        candidates = self.root / "queue-candidates.jsonl"
        candidates.write_text(json.dumps(record) + "\n", encoding="utf-8")
        return candidates, task_path

    def test_smaller_disagreement_first_then_group_id(self):
        groups = [queue_group("z"), queue_group("b"), queue_group("a")]
        scores = {"z": [0, .08], "b": [0, .01], "a": [0, .01]}
        queued, summary = build_queue(groups, scores)
        self.assertEqual(["a", "b", "z"], [item["groupId"] for item in queued])
        self.assertEqual(3, summary["queued"])
        self.assertTrue(all(item["requiresHumanConfirmation"] for item in queued))
        self.assertFalse(any("targets" in item for item in queued))
        self.assertEqual(queued, build_queue(list(reversed(groups)), scores)[0])

    def test_annotated_real_tasks_and_fixtures_are_excluded(self):
        groups = [queue_group("future-confirmed", state_count=2), queue_group("already-labelled"),
                  queue_group("fixture", source="fixture"), queue_group("pending")]
        queued, summary = build_queue(groups, {"pending": [0, .01]}, labelled_group_ids={"already-labelled"})
        self.assertEqual(["pending"], [item["groupId"] for item in queued])
        self.assertEqual({"alreadyAnnotated": 2, "notReal": 1}, summary["skipped"])

    def test_same_selection_and_large_gaps_do_not_enter_queue(self):
        groups = [queue_group("same"), queue_group("large"), queue_group("tie")]
        queued, summary = build_queue(groups, {"same": [1, 0], "large": [0, 5], "tie": [0, .5e-6]})
        self.assertEqual([], queued)
        self.assertEqual({"sameSelection": 2, "scoreGapTooLarge": 1}, summary["skipped"])

    def test_limit_and_no_candidate_coverage(self):
        empty = queue_group("empty")
        empty.update(candidateCount=0, candidates=[])
        queued, summary = build_queue([queue_group("b"), empty, queue_group("a")],
                                      {"a": [0, .01], "b": [0, .02]}, limit=1)
        self.assertEqual(["a"], [item["groupId"] for item in queued])
        self.assertEqual(2, summary["eligibleDisagreements"])
        self.assertEqual(1, summary["skipped"]["noCandidates"])

    def test_missing_duplicate_nonfinite_predictions_are_rejected(self):
        for scores in ({}, {"a": [0]}, {"a": [0, float("nan")]}, {"a": [0, float("inf")]},
                       {"a": [0, .1], "extra": [1]}):
            with self.subTest(scores=scores), self.assertRaises(ValueError):
                build_queue([queue_group("a")], scores)
        with self.assertRaisesRegex(ValueError, "Duplicate queue group"):
            build_queue([queue_group("a"), queue_group("a")], {"a": [0, .1]})
        changed = queue_group("a")
        changed["candidates"][1]["planKey"] = "rule"
        with self.assertRaisesRegex(ValueError, "Duplicate queue candidate"):
            build_queue([changed], {"a": [0, .1]})

    def test_candidate_input_requires_real_existing_confirmed_task(self):
        record = queue_group("g")
        path, _ = self.input_file(record)
        loaded = read_candidates(path, SCHEMA)
        self.assertEqual([record], loaded)
        record["stateCount"] = 2
        path.write_text(json.dumps(record) + "\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "stateCount"):
            read_candidates(path, SCHEMA)

    def test_schema_nan_and_rule_feature_disagreement_are_rejected(self):
        for features in ([], [1e309], [1]):
            record = queue_group("g")
            record["candidates"][1]["features"] = features
            path, _ = self.input_file(record)
            with self.subTest(features=features), self.assertRaises(ValueError):
                read_candidates(path, SCHEMA)

    def test_public_input_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "CODEGEN_PRIVATE_DIR"):
            read_candidates(self.root.parent / "public.jsonl", SCHEMA)

    def test_changed_truth_or_snapshot_rejects_stale_queue_features(self):
        record = queue_group("g")
        candidates, task = self.input_file(record)
        with task.open("a", encoding="utf-8") as output:
            output.write("\n")
        with self.assertRaisesRegex(ValueError, "Task changed"):
            read_candidates(candidates, SCHEMA)
        candidates, task = self.input_file(record)
        write_json(self.root / "s0.layout.json", {"root": {"text": "changed"}})
        with self.assertRaisesRegex(ValueError, "Capture snapshot changed"):
            read_candidates(candidates, SCHEMA)

    def test_queue_writer_preserves_task_and_only_writes_private_outputs(self):
        record = queue_group("g")
        candidates, task = self.input_file(record)
        before = task.read_bytes()
        schema_file = self.root / "features.json"
        write_json(schema_file, {"format": 1, "featureSchemaVersion": 1, "names": list(SCHEMA.names), "namesHash": SCHEMA.names_hash})
        model = self.root / "model.cbm"
        model.write_bytes(b"test-model-placeholder")
        write_json(self.root / "queue-manifest.json", {
            "format": 1, "tool": "CodeGenQueueExportTask", "toolVersion": 1,
            "groups": 1, "candidates": 2, "featureSchemaVersion": 1, "featureNamesHash": SCHEMA.names_hash,
            "candidatesSha256": sha256(candidates), "featureSchemaSha256": sha256(schema_file), "inputSha256": "0" * 64,
        })
        with patch("train.queue.predict", return_value=[0., .01]):
            summary = run_queue(candidates, schema_file, model, self.root / "queue")
        self.assertEqual(1, summary["queued"])
        self.assertEqual(before, task.read_bytes())
        row = json.loads((self.root / "queue" / "queue.jsonl").read_text(encoding="utf-8"))
        self.assertTrue(row["requiresHumanConfirmation"])
        with self.assertRaisesRegex(ValueError, "CODEGEN_PRIVATE_DIR"):
            run_queue(candidates, schema_file, model, self.root.parent / "public")


if __name__ == "__main__":
    unittest.main()
