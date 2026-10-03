import argparse
from contextlib import redirect_stdout
import io
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import uuid

from capture.collect import CaptureWorker, FAMILIES, MUTATIONS, collect, default_action, make_tasks, mutation_plan, read_json, safe_token, save_json
from capture.join import AssociatedTarget


class CollectTest(unittest.TestCase):
    def test_plan_covers_all_mutations_and_is_deterministic(self):
        for family in FAMILIES:
            observed = set()
            for seed in range(48):
                states = mutation_plan(family, seed)
                self.assertEqual(states, mutation_plan(family, seed))
                self.assertEqual((), states[0])
                self.assertEqual(3, len(states))
                for mutations in states:
                    self.assertEqual(len(mutations), len(set(mutations)))
                    self.assertFalse({"strip-ids", "rename-ids"}.issubset(mutations))
                    observed.update(mutations)
            expected = set(MUTATIONS) - ({"strip-ids", "rename-ids"} if family == "no-id" else set())
            self.assertEqual(expected, observed)

    def test_target_keys_follow_the_same_control_across_shuffle(self):
        states = [{"stateId": "s0", "snapshot": "s0.layout.json", "appliedMutations": []},
                  {"stateId": "s1", "snapshot": "s1.layout.json", "appliedMutations": ["shuffle"]}]
        joined = [[AssociatedTarget("list.a", (0, 0), ("click",)), AssociatedTarget("list.b", (0, 1), ("click",))],
                  [AssociatedTarget("list.b", (0, 0), ("click",)), AssociatedTarget("list.a", (0, 1), ("click",))]]
        tasks = make_tasks("list", 0, states, joined)
        self.assertEqual([[[0, 0]], [[0, 1]]], [s["targets"] for s in tasks[0]["states"]])
        self.assertEqual("fixture", tasks[0]["source"])
        self.assertNotIn("mode", tasks[0]["request"])
        self.assertNotIn("style", tasks[0]["request"])
        self.assertEqual(["shuffle"], tasks[0]["states"][1]["mutations"])
        self.assertEqual("collection", tasks[-1]["request"]["scope"])
        self.assertEqual([[0, 0], [0, 1]], tasks[-1]["states"][0]["targets"])
        self.assertEqual([[0, 1], [0, 0]], tasks[-1]["states"][1]["targets"])
        with self.assertRaises(ValueError):
            make_tasks("list", 0, states, [joined[0], joined[1][:1]])

    def test_action_uses_declared_capabilities(self):
        self.assertEqual("set-text", default_action(AssociatedTarget("a", (), ("click", "set-text")), 1))
        self.assertEqual("long-click", default_action(AssociatedTarget("a", (), ("click", "long-click")), 1))
        self.assertEqual("none", default_action(AssociatedTarget("a", (), ()), 0))

    def test_ids_cannot_escape_paths_or_shell(self):
        for value in ("../a", "a/b", "a\\b", "a b", "$(id)", "a;id", "", "a" * 97):
            with self.subTest(value=value), self.assertRaises(ValueError):
                safe_token(value)
        self.assertEqual("list-4-mouse", safe_token("list-4-mouse"))

    def test_overlapping_workers_have_isolated_response_and_snapshot_paths(self):
        first, second = CaptureWorker(None), CaptureWorker(None)
        self.assertNotEqual(first.control, second.control)
        self.assertTrue(first.control.endswith("/" + first.run_id))


class FakeAdb:
    def __init__(self, *args):
        pass

    def shell(self, *args):
        if args[0] == "dumpsys":
            return "versionCode=1"
        return "33" if args[-1].endswith("sdk") else "fixture-device"


class FakeWorker:
    def __init__(self, adb):
        self.run_id = uuid.uuid4().hex

    def start(self):
        pass

    def stop(self):
        pass

    def capture(self, family, seed, mutations, state_id, destination):
        children = [{"className": "Button", "depth": 1, "bounds": [0, index * 10, 40, index * 10 + 8]}
                    for index in range(6)]
        snapshot = {"version": 1, "root": {"className": "Root", "bounds": [0, 0, 100, 100], "children": children}}
        truth = {"format": 1, "appliedMutations": list(mutations), "targets": [
            {**node, "key": f"{family}.key{index}", "capabilities": ["click"]} for index, node in enumerate(children)]}
        save_json(destination, snapshot)
        return snapshot, truth


class TimeoutWorker(FakeWorker):
    def capture(self, *args):
        if args[3].endswith("s0"):
            raise subprocess.TimeoutExpired("adb", 30)
        return super().capture(*args)


class RecoveryTest(unittest.TestCase):
    def run_collect(self, output, worker=FakeWorker):
        args = argparse.Namespace(dataset_id="test", families="list", first_seed=0, seeds=1,
                                  output=str(output), adb="adb", serial="test", generator_commit="a" * 40)
        with patch("capture.collect.Adb", FakeAdb), patch("capture.collect.CaptureWorker", worker), redirect_stdout(io.StringIO()):
            return collect(args)

    def test_external_groups_link_is_rejected_without_moving_existing_files(self):
        with tempfile.TemporaryDirectory() as directory:
            output, external = Path(directory) / "dataset", Path(directory) / "external"
            output.mkdir()
            external.mkdir()
            original = external / "untouched.txt"
            original.write_text("keep", encoding="utf-8")
            link = output / "groups"
            junction = False
            try:
                link.symlink_to(external, target_is_directory=True)
            except OSError:
                if os.name != "nt":
                    self.skipTest("This system does not permit directory symlinks")
                # Windows directory junctions do not require the symlink privilege. Paths are
                # values in task-specific environment variables, never interpolated shell code.
                environment = dict(os.environ, CODEGEN_TEST_LINK=str(link), CODEGEN_TEST_TARGET=str(external))
                subprocess.run(["powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                                "New-Item -ItemType Junction -Path $env:CODEGEN_TEST_LINK -Target $env:CODEGEN_TEST_TARGET | Out-Null"],
                               env=environment, check=True, capture_output=True)
                junction = True
            try:
                with self.assertRaisesRegex(ValueError, "must not link outside"):
                    self.run_collect(output)
                self.assertEqual("keep", original.read_text(encoding="utf-8"))
                self.assertEqual([original], list(external.iterdir()))
            finally:
                if junction:
                    link.rmdir()  # Remove only the junction itself, never recurse into its target.
                else:
                    link.unlink()

    def test_timeout_counts_as_discard_and_uncheckpointed_old_tasks_are_quarantined(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "dataset"
            stale = output / "groups/list-0/stale.cgtask.json"
            save_json(stale, {"oldTargets": True})
            with self.assertRaisesRegex(RuntimeError, "Discard rate"):
                self.run_collect(output, TimeoutWorker)
            manifest = read_json(output / "collection.json")
            self.assertEqual((3, 2, 1), tuple(manifest[key] for key in ("attemptedStates", "acceptedStates", "discardedStates")))
            self.assertEqual([], list(output.rglob("*.cgtask.json")))
            retained = Path(manifest["quarantinedScenarios"][0])
            self.assertTrue((retained / "stale.cgtask.json").is_file())
            self.assertFalse(retained.is_relative_to(output))

    def test_interruption_during_group_write_never_exposes_partial_tasks_and_resume_is_consistent(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "dataset"

            def fail_second_task(path, value):
                if path.name.endswith("key1.cgtask.json"):
                    raise OSError("simulated interrupted write")
                save_json(path, value)

            with patch("capture.collect.save_json", side_effect=fail_second_task), self.assertRaises(OSError):
                self.run_collect(output)
            self.assertEqual([], list(output.rglob("*.cgtask.json")))
            result = self.run_collect(output)
            self.assertEqual(7, result["groups"])
            self.assertEqual(7, len(list(output.rglob("*.cgtask.json"))))
            self.assertEqual(6, result["acceptedStates"])
            self.assertEqual(result["attemptedStates"], result["acceptedStates"] + result["discardedStates"])

    def test_hard_interrupted_state_is_counted_on_resume(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "dataset"
            self.run_collect(output)
            manifest = read_json(output / "collection.json")
            manifest["pendingState"] = {"stateId": "interrupted", "mutations": []}
            manifest["attemptedStates"] += 1
            save_json(output / "collection.json", manifest)
            with self.assertRaisesRegex(RuntimeError, "Discard rate"):
                self.run_collect(output)
            resumed = read_json(output / "collection.json")
            self.assertEqual(1, resumed["discardedStates"])
            self.assertEqual(4, resumed["attemptedStates"])
            self.assertIsNone(resumed["pendingState"])


if __name__ == "__main__":
    unittest.main()
