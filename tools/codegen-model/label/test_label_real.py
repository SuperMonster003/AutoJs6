import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from dataset.task_io import load_group, private_path, snapshot_root, write_json
from label.label_real import confirm_selection, flatten, label_snapshots, recommend, select


def tree(items):
    return {"bounds": [0, 0, 100, 100], "className": "Root", "children": [
        {"fullId": identifier, "className": "Button", "text": text, "bounds": [0, index * 20, 100, index * 20 + 10]}
        for index, (identifier, text) in enumerate(items)]}


class LabelRealTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.env = patch.dict(os.environ, {"CODEGEN_PRIVATE_DIR": str(self.root)})
        self.env.start()
        self.addCleanup(self.env.stop)

    def snapshot(self, name, items):
        path = self.root / name
        write_json(path, {"version": 1, "root": tree(items)})
        return path

    def test_recommendation_follows_text_after_reorder_with_duplicate_id(self):
        initial = flatten(tree([("pkg:id/buy", "Mouse"), ("pkg:id/buy", "Keyboard")]))
        shuffled = flatten(tree([("pkg:id/buy", "Keyboard"), ("pkg:id/buy", "Mouse")]))
        result = recommend([initial[1]], shuffled)
        self.assertEqual((2,), tuple(node.index for node in result.nodes))
        self.assertFalse(result.ambiguous)

    def test_text_change_uses_full_id_and_class(self):
        before = flatten(tree([("pkg:id/clock", "12:00"), ("pkg:id/other", "Other")]))
        after = flatten(tree([("pkg:id/other", "12:00"), ("pkg:id/clock", "13:00")]))
        self.assertEqual("pkg:id/clock", recommend([before[1]], after).nodes[0].full_id)

    def test_weak_class_and_position_match_requires_explicit_confirmation(self):
        nodes = flatten(tree([("", ""), ("", "")]))
        proposal = recommend([nodes[1]], nodes)
        self.assertTrue(proposal.ambiguous)
        prompts = []

        def no_answer(prompt):
            prompts.append(prompt)
            raise EOFError()

        with self.assertRaises(EOFError):
            confirm_selection(nodes, proposal.nodes, input_fn=no_answer, output=lambda _: None)
        self.assertEqual(1, len(prompts))

    def test_multiple_targets_are_distinct_and_reorder_together(self):
        before = flatten(tree([("same", "A"), ("same", "B"), ("same", "C")]))
        after = flatten(tree([("same", "C"), ("same", "A"), ("same", "B")]))
        result = recommend(before[1:], after)
        self.assertEqual(["A", "B", "C"], [node.text for node in result.nodes])
        self.assertEqual(3, len({node.index for node in result.nodes}))

    def test_queries_use_preorder_text_and_both_id_forms(self):
        nodes = flatten(tree([("pkg:id/buy", "Buy mouse"), ("pkg:id/sell", "Sell mouse")]))
        self.assertEqual([1, 2], [node.index for node in select(nodes, "index 1,2")])
        self.assertEqual([1, 2], [node.index for node in select(nodes, "text mouse")])
        self.assertEqual([1], [node.index for node in select(nodes, "id buy")])
        for query in ("index -1", "index 1,1", "index 100", "text", "other 1"):
            with self.subTest(query=query), self.assertRaises(ValueError):
                select(nodes, query)

    def test_change_skip_and_count_checks_do_not_implicitly_accept(self):
        nodes = flatten(tree([("a", "A"), ("b", "B")]))
        answers = iter(["", "c", "index 1,2", "c", "change", "index 2", "confirm"])
        result = confirm_selection(nodes, input_fn=lambda _: next(answers), output=lambda _: None)
        self.assertEqual([2], [node.index for node in result])
        self.assertIsNone(confirm_selection(nodes, nodes[1:], scope="collection", expected_count=3,
                                            input_fn=lambda _: "skip", output=lambda _: None))

    def test_session_writes_only_confirmed_states_and_valid_task(self):
        paths = [self.snapshot("s0.layout.json", [("button", "First")]),
                 self.snapshot("s1.layout.json", [("button", "Second")]),
                 self.snapshot("s2.layout.json", [("button", "Third")])]
        answers = iter(["id button", "c", "s", "c"])
        output = self.root / "labeled" / "sample.cgtask.json"
        group = label_snapshots(paths, group_id="sample", app="example.app", screen_family="screen",
                                output_path=output, mutations=[[], ["locale"], ["retext"]],
                                input_fn=lambda _: next(answers), output=lambda _: None)
        self.assertEqual(["s0", "s2"], [state["stateId"] for state in group["states"]])
        self.assertTrue(all(state["targets"] == [[0]] for state in group["states"]))
        loaded, copied = load_group(output)
        self.assertEqual("real", loaded["source"])
        self.assertTrue(all(path.is_relative_to(output.parent) for path in copied))
        self.assertTrue(all(path.exists() for path in paths))

    def test_even_strong_recommendation_at_eof_writes_nothing(self):
        paths = [self.snapshot("s0.layout.json", [("button", "Text")]),
                 self.snapshot("s1.layout.json", [("button", "Text")])]
        answers = iter(["c"])

        def answer(_):
            try:
                return next(answers)
            except StopIteration as error:
                raise EOFError() from error

        output = self.root / "labeled" / "sample.cgtask.json"
        with self.assertRaises(EOFError):
            label_snapshots(paths, group_id="sample", app="example.app", screen_family="screen",
                            output_path=output, target_queries=["index 1"], input_fn=answer, output=lambda _: None)
        self.assertFalse(output.parent.exists())

    def test_first_state_skip_has_no_output(self):
        path = self.snapshot("s0.layout.json", [("a", "A")])
        output = self.root / "sample.cgtask.json"
        result = label_snapshots([path], group_id="sample", app="app", screen_family="screen", output_path=output,
                                input_fn=lambda _: "s", output=lambda _: None)
        self.assertIsNone(result)
        self.assertFalse(output.exists())

    def test_real_paths_cannot_escape_private_dir_or_be_in_git(self):
        with self.assertRaises(ValueError):
            private_path(self.root.parent / "outside.layout.json")
        repo = self.root / "repository"
        repo.mkdir()
        (repo / ".git").write_text("gitdir: somewhere", encoding="utf-8")
        with self.assertRaises(ValueError):
            private_path(repo / "sample.cgtask.json")
        with patch.dict(os.environ, {"CODEGEN_PRIVATE_DIR": "relative"}), self.assertRaises(ValueError):
            private_path(self.root / "sample.cgtask.json")

    def test_no_correspondence_returns_no_suggestion(self):
        before = flatten(tree([("id", "Text")]))[1]
        self.assertEqual((), recommend([before], flatten({"className": "Unrelated"})).nodes)

    def test_duplicate_json_keys_and_invalid_target_paths_are_rejected(self):
        snapshot = self.snapshot("s0.layout.json", [("a", "A")])
        task = self.root / "task.cgtask.json"
        group = {"format": 1, "groupId": "a", "source": "real", "app": "a", "screenFamily": "a",
                 "request": {"scope": "single", "attributes": [], "action": "none"},
                 "states": [{"stateId": "s0", "snapshot": snapshot.name, "mutations": [], "targets": [[99]]}]}
        write_json(task, group)
        with self.assertRaisesRegex(ValueError, "does not exist"):
            load_group(task)
        task.write_text('{"format": 1, "format": 2}', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Duplicate JSON"):
            load_group(task)

    def test_layout_envelope_versions_follow_host_root_compatibility(self):
        path = self.root / "compatible.layout.json"
        for version in (None, 1, 2):
            data = {"root": {"text": "compatible"}}
            if version is not None:
                data["version"] = version
            write_json(path, data)
            self.assertEqual("compatible", snapshot_root(path)["text"])

    def test_task_controls_and_non_int32_indices_are_rejected(self):
        snapshot = self.snapshot("s0.layout.json", [("a", "A")])
        task = self.root / "task.cgtask.json"
        group = {"format": 1, "groupId": "a", "source": "real", "app": "a", "screenFamily": "a",
                 "request": {"scope": "single", "attributes": [], "action": "none"},
                 "states": [{"stateId": "s0", "snapshot": snapshot.name, "mutations": [], "targets": [[]]}]}
        group["groupId"] = "a\u0080"
        write_json(task, group)
        with self.assertRaisesRegex(ValueError, "control"):
            load_group(task)
        group["groupId"] = "a"
        for index in (True, 1.0, 2**31):
            group["states"][0]["targets"] = [[index]]
            write_json(task, group)
            with self.subTest(index=index), self.assertRaisesRegex(ValueError, "Invalid target"):
                load_group(task)


if __name__ == "__main__":
    unittest.main()
