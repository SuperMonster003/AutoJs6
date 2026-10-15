import json
import hashlib
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from dataset.split import assign_splits, build_dataset, main, real_app_split, sha256
from dataset.task_io import read_json, write_json


COMMIT = "1234567890abcdef1234567890abcdef12345678"


def group(group_id, family="login", source="fixture", app="fixture.app"):
    return {"format": 1, "groupId": group_id, "source": source, "app": app, "screenFamily": family,
            "request": {"scope": "single", "attributes": ["id", "text", "desc"], "action": "none"},
            "captureState": 0,
            "states": [{"stateId": "s0", "snapshot": "s0.layout.json", "mutations": [], "targets": [[]]},
                       {"stateId": "s1", "snapshot": "s1.layout.json", "mutations": ["retext"], "targets": [[]]}]}


class SplitTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.inputs, self.output = self.root / "inputs", self.root / "out"
        self.inputs.mkdir()
        self.provenance = self.root / "collection.json"
        write_json(self.provenance, {"generatorCommit": COMMIT, "fixtureVersionCode": 1,
                                     "devices": [{"serial": "test", "model": "Fixture", "api": 33}],
                                     "discardRate": 0, "acceptedStates": 16, "elapsedSeconds": 42})
        self.env = patch.dict(os.environ, {"CODEGEN_PRIVATE_DIR": str(self.root.resolve())})
        self.env.start()
        self.addCleanup(self.env.stop)

    def task(self, data, filename=None):
        folder = self.inputs / (filename or data["groupId"])
        folder.mkdir(exist_ok=True)
        for state in data["states"]:
            write_json(folder / state["snapshot"], {"version": 1, "root": {"text": state["stateId"], "children": []}})
        write_json(folder / "task.cgtask.json", data)
        return folder / "task.cgtask.json"

    def artifacts(self, data, candidate_count=1):
        labels = [{"format": 1, "groupId": data["groupId"], "planKey": f"plan-{index}", "family": "SELF",
                   "survival": 1, "ruleSelected": index == 0, "failedStates": []} for index in range(candidate_count)]
        rules = [{"format": 1, "groupId": data["groupId"], "source": data["source"], "app": data["app"],
                  "screenFamily": data["screenFamily"], "candidateCount": candidate_count,
                  "rulePlanKey": "plan-0" if candidate_count else None, "ruleSurvival": 1 if candidate_count else None}]
        self.output.mkdir(exist_ok=True)
        self.jsonl(self.output / "labels.jsonl", labels)
        self.jsonl(self.output / "rules.jsonl", rules)
        return labels, rules

    @staticmethod
    def jsonl(path, rows):
        path.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")

    def build(self, **kwargs):
        return build_dataset([self.inputs], output=self.output, dataset_id="test-dataset", provenance_path=self.provenance,
                             generated_at="2026-10-03T00:00:00+00:00", **kwargs)

    def test_deterministic_with_no_family_or_seed_leakage(self):
        groups = [group(f"g-{family}-{seed}", f"family-{family}") for family in range(100) for seed in range(3)]
        assignment = assign_splits(groups)
        self.assertEqual(assignment, assign_splits(list(reversed(groups))))
        families = {}
        for data in groups:
            families.setdefault(data["screenFamily"], set()).add(assignment[data["groupId"]])
        self.assertTrue(all(len(splits) == 1 for splits in families.values()))
        for name, target in (("train", .70), ("valid", .15), ("test", .15)):
            self.assertLessEqual(abs(sum(split == name for split in assignment.values()) / len(groups) - target), .03)

    def test_eight_atomic_families_report_actual_rounded_ratios(self):
        for index in range(8):
            self.task(group(f"g{index}", f"family{index}"))
        manifest = self.build()
        self.assertEqual([6, 1, 1], [manifest["splits"][key]["groups"] for key in ("train", "valid", "test")])
        self.assertEqual({"train": .75, "valid": .125, "test": .125}, manifest["splitPolicy"]["actualFamilyRatios"])
        self.assertIn("Eight families", manifest["splitPolicy"]["note"])

    def test_live_or_partial_collection_cannot_be_frozen(self):
        self.task(group("g"))
        provenance = dict(read_json(self.provenance), format=1, plan={"statesPerGroup": 2}, datasetId="test-dataset",
                          completedAt="2026-10-03T00:00:00Z", pendingState=None, attemptedStates=2,
                          acceptedStates=2, discardedStates=0, discardRate=0, groups=1)
        write_json(self.provenance, provenance)
        self.assertEqual(1, self.build()["groups"])
        for changes in ({"completedAt": None}, {"pendingState": {"stateId": "s1"}}, {"groups": 2},
                        {"datasetId": "another-dataset"}, {"acceptedStates": 1}, {"discardRate": .01}):
            with self.subTest(changes=changes):
                write_json(self.provenance, dict(provenance, **changes))
                with self.assertRaises(ValueError):
                    self.build()

    def test_excess_collection_discard_rate_is_rejected(self):
        self.task(group("g"))
        write_json(self.provenance, dict(read_json(self.provenance), format=1, plan={}, datasetId="test-dataset",
                                        completedAt="2026-10-03T00:00:00Z", pendingState=None, attemptedStates=100,
                                        acceptedStates=97, discardedStates=3, discardRate=.03, groups=1))
        with self.assertRaisesRegex(ValueError, "exceeds 2%"):
            self.build()

    def test_real_apps_are_split_by_app_and_never_move_when_apps_are_added(self):
        apps = [f"real.app{index}" for index in range(40)]
        sides = {app: real_app_split(app) for app in apps}
        # Both sides occur, roughly half each, and the assignment is a pure function of the app.
        self.assertEqual({"real-train", "real-test"}, set(sides.values()))
        self.assertTrue(10 <= list(sides.values()).count("real-test") <= 30)
        groups = [group(f"r{index}", f"screen{index % 3}", "real", apps[index % 4]) for index in range(12)]
        groups += [group("fixture", "screen0")]
        assigned = assign_splits(groups)
        for data in groups[:-1]:
            self.assertEqual(sides[data["app"]], assigned[data["groupId"]])
        self.assertNotIn(assigned["fixture"], ("real-train", "real-test"))
        # Adding further applications and groups leaves every earlier assignment unchanged.
        more = assign_splits(groups + [group(f"x{index}", "screen9", "real", app) for index, app in enumerate(apps[4:])])
        self.assertEqual(assigned, {key: more[key] for key in assigned})

    def test_pending_artifacts_have_null_hash_and_no_placeholder_files(self):
        self.task(group("g"))
        manifest = self.build()
        self.assertEqual("labels-pending", manifest["status"])
        self.assertIsNone(manifest["candidates"])
        for field in ("labels", "rules", "features"):
            self.assertEqual("pending", manifest["artifacts"][field]["status"])
            self.assertIsNone(manifest["artifacts"][field]["sha256"])
        self.assertFalse((self.output / "features.csv").exists())
        self.assertEqual(sha256(self.provenance), manifest["artifacts"]["provenance"]["sha256"])

    def test_refresh_after_export_records_exact_counts_hashes_and_split(self):
        data = group("g")
        self.task(data)
        self.build()
        labels, _ = self.artifacts(data, 2)
        manifest = self.build(labels_path=self.output / "labels.jsonl", rules_path=self.output / "rules.jsonl")
        self.assertEqual("features-pending", manifest["status"])
        self.assertEqual(2, manifest["candidates"])
        self.assertEqual(2, manifest["splits"]["train"]["candidates"])
        self.assertEqual({"retext": 1}, manifest["splits"]["train"]["mutations"])
        self.assertEqual(sha256(self.output / "labels.jsonl"), manifest["artifacts"]["labels"]["sha256"])
        self.assertEqual({"g": "train"}, read_json(self.output / "split-map.json")["assignment"])
        self.assertEqual(0, manifest["capture"]["discardRate"])

    def test_zero_candidates_must_be_explicit_in_rules(self):
        data = group("g")
        self.task(data)
        self.artifacts(data, 0)
        manifest = self.build(labels_path=self.output / "labels.jsonl", rules_path=self.output / "rules.jsonl")
        self.assertEqual(0, manifest["candidates"])
        self.assertEqual(1, manifest["splits"]["train"]["groupsWithoutCandidates"])

    def test_missing_labels_or_rule_rows_are_rejected(self):
        data = group("g")
        self.task(data)
        labels, rules = self.artifacts(data, 2)
        self.jsonl(self.output / "labels.jsonl", labels[:1])
        with self.assertRaisesRegex(ValueError, "Missing/extra label"):
            self.build(labels_path=self.output / "labels.jsonl", rules_path=self.output / "rules.jsonl")
        self.jsonl(self.output / "labels.jsonl", labels)
        self.jsonl(self.output / "rules.jsonl", [])
        with self.assertRaisesRegex(ValueError, "Missing groups"):
            self.build(labels_path=self.output / "labels.jsonl", rules_path=self.output / "rules.jsonl")

    def test_duplicate_group_ids_and_candidate_rows_are_rejected(self):
        data = group("g")
        self.task(data)
        labels, _ = self.artifacts(data)
        self.jsonl(self.output / "labels.jsonl", labels + labels)
        with self.assertRaisesRegex(ValueError, "Duplicate candidate"):
            self.build(labels_path=self.output / "labels.jsonl", rules_path=self.output / "rules.jsonl")
        self.task(data, "duplicate")
        with self.assertRaisesRegex(ValueError, "Duplicate groupId"):
            self.build()

    def test_failure_state_label_math_is_checked(self):
        data = group("g")
        self.task(data)
        labels, rules = self.artifacts(data)
        labels[0]["failedStates"] = [{"stateId": "s1", "mutations": ["retext"], "reason": "MISSING_TARGETS",
                                       "extraCount": 0, "missingCount": 1}]
        self.jsonl(self.output / "labels.jsonl", labels)
        with self.assertRaisesRegex(ValueError, "survival disagrees"):
            self.build(labels_path=self.output / "labels.jsonl", rules_path=self.output / "rules.jsonl")
        labels[0]["survival"] = rules[0]["ruleSurvival"] = 0
        self.jsonl(self.output / "labels.jsonl", labels)
        self.jsonl(self.output / "rules.jsonl", rules)
        self.assertEqual(1, self.build(labels_path=self.output / "labels.jsonl", rules_path=self.output / "rules.jsonl")["candidates"])

    def test_features_require_complete_candidate_set_and_correct_split(self):
        data = group("g")
        self.task(data)
        self.artifacts(data)
        features = self.output / "features.csv"
        write_json(self.output / "features.json", {"format": 1, "featureSchemaVersion": 1,
                                                   "names": ["feature"], "namesHash": hashlib.sha256(b"feature").hexdigest()[:16]})
        features.write_text("split,groupId,planKey,label,feature\ntrain,g,plan-0,1,0.5\n", encoding="utf-8")
        kwargs = {"labels_path": self.output / "labels.jsonl", "rules_path": self.output / "rules.jsonl", "features_path": features}
        manifest = self.build(**kwargs)
        self.assertEqual("complete", manifest["status"])
        self.assertEqual(sha256(features), manifest["artifacts"]["features"]["sha256"])
        self.assertEqual(sha256(self.output / "features.json"), manifest["artifacts"]["featureSchema"]["sha256"])
        features.write_text("split,groupId,planKey,label,feature\ntest,g,plan-0,1,0.5\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Feature split"):
            self.build(**kwargs)
        features.write_text("split,groupId,planKey,label,feature\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Missing feature"):
            self.build(**kwargs)

    def test_snapshot_content_changes_provenance_digest(self):
        task = self.task(group("g"))
        before = self.build()["artifacts"]["snapshots"]["sha256"]
        write_json(task.parent / "s1.layout.json", {"version": 1, "root": {"text": "changed"}})
        self.assertNotEqual(before, self.build()["artifacts"]["snapshots"]["sha256"])

    def test_export_hashes_reject_stale_layouts_and_modified_labels(self):
        data = group("g")
        task = self.task(data)
        labels, _ = self.artifacts(data)
        digest = hashlib.sha256()
        for path in sorted(task.parent.iterdir()):
            digest.update((path.relative_to(self.inputs).as_posix() + "\0" + sha256(path) + "\n").encode("utf-8"))
        exported = {"format": 1, "tool": "CodeGenDatasetExportTask", "toolVersion": 1,
                    "groups": 1, "candidates": 1, "inputSha256": digest.hexdigest(),
                    "labelsSha256": sha256(self.output / "labels.jsonl"), "rulesSha256": sha256(self.output / "rules.jsonl")}
        write_json(self.output / "export-manifest.json", exported)
        kwargs = {"labels_path": self.output / "labels.jsonl", "rules_path": self.output / "rules.jsonl"}
        self.assertEqual("complete", self.build(**kwargs)["artifacts"]["export"]["status"])
        with (self.output / "labels.jsonl").open("a", encoding="utf-8") as output:
            output.write("\n")
        with self.assertRaisesRegex(ValueError, "labelsSha256"):
            self.build(**kwargs)
        self.jsonl(self.output / "labels.jsonl", labels)
        write_json(task.parent / "s1.layout.json", {"root": {"text": "changed"}})
        with self.assertRaisesRegex(ValueError, "changed since label export"):
            self.build(**kwargs)

    def test_export_input_hash_uses_jvm_utf16_path_order(self):
        labels, rules = [], []
        # U+1F600 starts with UTF-16 D83D, before BMP E000; Python's default order is opposite.
        for index, folder in enumerate(("\U0001f600", "\ue000")):
            data = group("g" + str(index))
            self.task(data, folder)
            group_labels, group_rules = self.artifacts(data)
            labels.extend(group_labels)
            rules.extend(group_rules)
        self.jsonl(self.output / "labels.jsonl", labels)
        self.jsonl(self.output / "rules.jsonl", rules)
        digest = hashlib.sha256()
        for folder in ("\U0001f600", "\ue000"):
            for path in sorted((self.inputs / folder).iterdir()):
                digest.update((path.relative_to(self.inputs).as_posix() + "\0" + sha256(path) + "\n").encode("utf-8"))
        write_json(self.output / "export-manifest.json", {
            "format": 1, "tool": "CodeGenDatasetExportTask", "toolVersion": 1,
            "groups": 2, "candidates": 2, "inputSha256": digest.hexdigest(),
            "labelsSha256": sha256(self.output / "labels.jsonl"),
            "rulesSha256": sha256(self.output / "rules.jsonl"),
        })
        manifest = self.build(labels_path=self.output / "labels.jsonl", rules_path=self.output / "rules.jsonl")
        self.assertEqual("complete", manifest["artifacts"]["export"]["status"])

    def test_real_group_cannot_emit_public_manifest(self):
        self.task(group("g", source="real"))
        with self.assertRaisesRegex(ValueError, "CODEGEN_PRIVATE_DIR"):
            build_dataset([self.inputs], output=self.root.parent / "outside", dataset_id="x", provenance_path=self.provenance)

    def test_cli_detects_new_export_artifacts_when_rerun(self):
        data = group("g")
        self.task(data)
        self.build()
        self.artifacts(data)
        with patch("builtins.print"):
            self.assertEqual(0, main(["--input", str(self.inputs), "--output", str(self.output),
                                      "--dataset-id", "test", "--provenance", str(self.provenance)]))
        self.assertEqual("features-pending", read_json(self.output / "dataset-manifest.json")["status"])


if __name__ == "__main__":
    unittest.main()
