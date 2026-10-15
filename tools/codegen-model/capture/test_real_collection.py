import json
import os
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import Mock, patch

from capture.real_device import PrivateAdb, RealCaptureWorker, list_devices
from capture.real_store import CollectionStore, WorkspaceLock, inspect_snapshot, private_write_json
from dataset.task_io import load_group, read_json, write_json

APP = "example.app"


def snapshot(order=("A", "B", "C"), stamp="first", package=APP):
    # Handwritten layouts, never captured third-party UI content.
    return json.dumps({"version": 1, "capturedAt": stamp, "root": {
        "packageName": package, "className": "FrameLayout", "bounds": [0, 0, 400, 800],
        "children": [{"className": "Button", "fullId": "example.app:id/item", "text": text,
                      "desc": "Item " + text, "clickable": True,
                      "bounds": [0, i * 100, 400, i * 100 + 90]}
                     for i, text in enumerate(order)]}}).encode("utf-8")


class PrivateCollectionCase(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        env = patch.dict(os.environ, {"CODEGEN_PRIVATE_DIR": str(self.root)})
        env.start()
        self.addCleanup(env.stop)
        self.store = CollectionStore(self.root)
        self.session = self.store.create(app=APP, title="列表中的 A", action="click")
        self.gid = self.session["groupId"]

    def add(self, order=("A", "B", "C"), **kwargs):
        return self.store.add_snapshot(self.gid, snapshot(order, **kwargs))

    def complete(self):
        for index, (order, target) in enumerate([(("A", "B", "C"), 1),
                                                (("B", "A", "C"), 2),
                                                (("B", "C", "A"), 3)]):
            self.add(order)
            self.store.confirm(self.gid, index, [target], "" if index == 0 else "重排 " + str(index))
        return self.store.export(self.gid)


class RealCollectionTest(PrivateCollectionCase):
    def test_resume_keeps_unconfirmed_states_and_never_exports_a_proposal(self):
        self.add()
        self.store.confirm(self.gid, 0, [1], "")
        self.add(("B", "A", "C"))
        resumed = CollectionStore(self.root).load(self.gid)
        self.assertIsNone(resumed["states"][1]["confirmedAt"])
        proposal = self.store.proposal(resumed, 1)
        self.assertEqual([2], [n.index for n in proposal.nodes])
        with self.assertRaises(ValueError):
            self.store.export(self.gid)
        self.assertFalse(self.store.export_path(self.gid).exists())

    def test_export_is_original_format_and_freezes_inputs(self):
        path = self.complete()
        group, paths = load_group(path)
        self.assertEqual("real", group["source"])
        self.assertEqual([[[0]], [[1]], [[2]]], [s["targets"] for s in group["states"]])
        self.assertEqual(3, len(paths))
        before = path.read_bytes()
        self.assertEqual(path, self.store.export(self.gid))
        self.assertEqual(before, path.read_bytes())
        for operation in (lambda: self.add(("D", "B", "A")),
                          lambda: self.store.confirm(self.gid, 0, [2], "changed"),
                          lambda: self.store.exclude(self.gid, 1)):
            with self.assertRaises(ValueError):
                operation()
        self.assertEqual(before, path.read_bytes())

    def test_timestamp_only_change_is_not_a_new_state(self):
        self.add()
        with self.assertRaisesRegex(ValueError, "完全相同"):
            self.add(stamp="later")
        self.assertEqual(1, len(self.store.load(self.gid)["states"]))

    def test_foreign_package_and_invalid_layout_do_not_publish(self):
        for data in (snapshot(package="another.app"), b'{"root": {}}', b"[]", b"{}", b'not json',
                     b'{"root":{},"root":{}}', b'{"root":{"bounds":[0,0,NaN,1]}}'):
            with self.subTest(data=data), self.assertRaises((ValueError, UnicodeError)):
                self.store.add_snapshot(self.gid, data)
        self.assertEqual([], self.store.load(self.gid)["states"])

    def test_initial_target_and_followup_change_note_required(self):
        self.add()
        for indices in ([], [0, 1], [True], [-1], [100]):
            with self.subTest(indices=indices), self.assertRaises(ValueError):
                self.store.confirm(self.gid, 0, indices, "")
        self.store.confirm(self.gid, 0, [1], "")
        self.add(("B", "A", "C"))
        with self.assertRaises(ValueError):
            self.store.confirm(self.gid, 1, [2], " ")

    def test_two_states_are_a_draft_not_q8_export(self):
        for i, order in enumerate((("A", "B", "C"), ("B", "A", "C"))):
            self.add(order)
            self.store.confirm(self.gid, i, [1], "change")
        with self.assertRaisesRegex(ValueError, "至少需要 3"):
            self.store.export(self.gid)
        self.assertEqual(0, self.store.report()["groups"])

    def test_collection_count_and_duplicate_targets_checked(self):
        self.gid = self.store.create(app=APP, title="two", scope="collection")["groupId"]
        self.add()
        self.store.confirm(self.gid, 0, [1, 2], "")
        self.add(("B", "A", "C"))
        for indices in ([1], [1, 1], [1, 2, 3]):
            with self.subTest(indices=indices), self.assertRaises(ValueError):
                self.store.confirm(self.gid, 1, indices, "reorder")
        self.store.confirm(self.gid, 1, [2, 1], "reorder")

    def test_exclusion_and_restoration_require_new_confirmation(self):
        self.add()
        self.store.confirm(self.gid, 0, [1], "")
        self.store.exclude(self.gid, 0)
        with self.assertRaises(ValueError):
            self.store.confirm(self.gid, 0, [1], "")
        self.store.exclude(self.gid, 0, False)
        self.assertIsNone(self.store.load(self.gid)["states"][0]["confirmedAt"])
        self.assertIsNone(self.store.load(self.gid)["states"][0]["targets"])

    def test_import_is_copied_and_source_edits_do_not_change_capture(self):
        source = self.root / "原始布局.layout.json"
        source.write_bytes(snapshot())
        session = self.store.import_snapshot(self.gid, source)
        source.write_bytes(snapshot(("X", "Y", "Z")))
        _, nodes = self.store.read_state(session, 0)
        self.assertEqual("A", nodes[1].text)
        self.assertEqual({"kind": "import"}, session["states"][0]["provenance"])

    def test_paths_outside_private_and_git_worktrees_rejected(self):
        with tempfile.TemporaryDirectory() as external:
            source = Path(external) / "s.layout.json"
            source.write_bytes(snapshot())
            with self.assertRaises(ValueError):
                self.store.import_snapshot(self.gid, source)
        git = self.root / "repo"
        (git / ".git").mkdir(parents=True)
        source = git / "s.layout.json"
        source.write_bytes(snapshot())
        with self.assertRaises(ValueError):
            self.store.import_snapshot(self.gid, source)
        for gid in ("../elsewhere", "real-../../", "a; rm"):
            with self.assertRaises(ValueError):
                self.store.folder(gid)

    def test_snapshot_tampering_rejected_before_export_and_by_report(self):
        path = self.complete()
        _, snapshots = load_group(path)
        snapshots[0].write_bytes(snapshot(("changed", "B", "C")))
        with self.assertRaises(ValueError):
            self.store.export(self.gid)
        report = self.store.report()
        self.assertEqual(0, report["groups"])
        self.assertEqual(1, len(report["errors"]))

    def test_atomic_failure_leaves_previous_draft_and_no_task(self):
        self.add()
        with patch("capture.real_store.private_write_json", side_effect=OSError("disk full")):
            with self.assertRaises(OSError):
                self.store.confirm(self.gid, 0, [1], "")
        self.assertIsNone(self.store.load(self.gid)["states"][0]["confirmedAt"])
        self.assertFalse(self.store.export_path(self.gid).exists())

    def test_orphan_snapshot_after_interruption_does_not_replace_a_state(self):
        self.add()
        with patch("capture.real_store.private_write_json", side_effect=OSError("disk full")):
            with self.assertRaises(OSError):
                self.add(("B", "A", "C"))
        resumed = self.add(("B", "A", "C"))
        self.assertEqual(2, len(resumed["states"]))
        self.store.read_state(resumed, 0)
        self.store.read_state(resumed, 1)

    def test_report_counts_existing_label_real_groups_and_detects_renamed_copy(self):
        exported = self.complete()
        report = self.store.report()
        self.assertEqual((1, 1, 0), (report["apps"], report["groups"], report["appsWithFiveGroups"]))
        self.assertFalse(report["complete"])
        # An older label_real group may reuse these layouts under its own folder.
        group = read_json(exported)
        copy = self.root / "old"
        copy.mkdir()
        for index, state in enumerate(group["states"]):
            data = (exported.parent / state["snapshot"]).read_bytes()
            state["snapshot"] = f"s{index}.layout.json"
            (copy / state["snapshot"]).write_bytes(data)
        group["groupId"] = "renamed"
        write_json(copy / "copy.cgtask.json", group)
        report = self.store.report()
        self.assertEqual(1, report["groups"])
        self.assertEqual(1, len(report["errors"]))

    def test_legacy_short_task_not_counted_as_finished(self):
        path = self.complete()
        group = read_json(path)
        group["states"] = group["states"][:2]
        folder = self.root / "legacy"
        folder.mkdir()
        for i, state in enumerate(group["states"]):
            data = (path.parent / state["snapshot"]).read_bytes()
            state["snapshot"] = f"s{i}.layout.json"
            (folder / state["snapshot"]).write_bytes(data)
        group["groupId"] = "legacy"
        write_json(folder / "legacy.cgtask.json", group)
        report = self.store.report()
        self.assertEqual(1, report["groups"])
        self.assertEqual(1, report["shortGroups"])

    def test_workspace_lock_releases_and_prevents_two_writers(self):
        first = WorkspaceLock(self.root)
        try:
            with self.assertRaises(ValueError):
                WorkspaceLock(self.root)
        finally:
            first.close()
        WorkspaceLock(self.root).close()

    def test_atomic_temporary_symlink_cannot_write_outside_private(self):
        with tempfile.TemporaryDirectory() as other:
            target = Path(other) / "secret.json"
            target.write_text("untouched")
            link = self.root / "value.json.tmp"
            try:
                link.symlink_to(target)
            except OSError:
                self.skipTest("Symlinks require permission on this Windows installation")
            with self.assertRaises(ValueError):
                private_write_json(self.root / "value.json", {})
            self.assertEqual("untouched", target.read_text())


class FakePrivateAdb:
    def __init__(self):
        self.responses = []
        self.requests = []
        self.commands = []
        self.mode = "valid"

    def device_info(self, app):
        return {"kind": "device", "serial": "fake", "appVersionCode": 3}

    def shell(self, *args):
        self.commands.append(args)
        return ""

    def push_json(self, path, value):
        if path.endswith("config.json"):
            self.config = value
        else:
            self.requests.append(value)
            response = dict(value, ok=True)
            if value["command"] == "capture":
                response.update(packageName=APP, snapshot="snapshot-" + value["requestId"] + ".layout.json")
                if self.mode == "foreign":
                    response["packageName"] = "another.app"
                if self.mode == "traversal":
                    response["snapshot"] = "../../private.json"
                if self.mode == "stale":
                    self.responses.append(dict(response, runId="older-run"))
                if self.mode == "capture-error":
                    response.update(ok=False, error="wrong active package")
            self.responses.append(response)

    def remote_json(self, path):
        if path.endswith("worker-ready.json") or path.endswith("worker-stopped.json"):
            return {"runId": self.config["runId"]}
        return self.responses.pop(0) if self.responses else None

    def run(self, *args):
        self.commands.append(args)
        return snapshot() if args[0] == "exec-out" else b""


class RealDeviceTest(unittest.TestCase):
    def test_devices_include_offline_and_unauthorized_without_selecting_one(self):
        output = b"List of devices attached\na device model:Pixel_6 transport_id:1\nb unauthorized\nc offline\n"
        with patch("capture.real_device.command", return_value=output):
            self.assertEqual(["device", "unauthorized", "offline"], [d["status"] for d in list_devices("adb")])

    def test_current_activity_parsing_and_remote_shell_escaping(self):
        with tempfile.TemporaryDirectory() as tmp, patch.dict(os.environ, {"CODEGEN_PRIVATE_DIR": tmp}):
            adb = PrivateAdb("adb", "serial", Path(tmp))
            with patch.object(adb, "run", return_value=b"topResumedActivity=ActivityRecord{x u0 example.app/.Main t12}") as run:
                self.assertEqual(APP, adb.foreground_package())
                adb.shell("cat", "/a b/$(private)")
                self.assertEqual(("shell", "cat '/a b/$(private)'"), run.call_args.args)

    def test_capture_uses_request_identity_then_releases_phone_copy(self):
        adb = FakePrivateAdb()
        worker = RealCaptureWorker(adb, APP)
        data, provenance = worker.capture_layout()
        inspect_snapshot(data, APP)
        self.assertEqual("fake", provenance["serial"])
        self.assertEqual(["capture", "release"], [r["command"] for r in adb.requests])
        self.assertTrue(worker.reusable())
        worker.stop()
        self.assertFalse(worker.started)
        self.assertTrue(all(".." not in str(c) for c in adb.commands))

    def test_response_cannot_redirect_read_to_foreign_path_or_package(self):
        for mode in ("foreign", "traversal", "capture-error"):
            adb = FakePrivateAdb()
            adb.mode = mode
            worker = RealCaptureWorker(adb, APP)
            with self.subTest(mode=mode), self.assertRaises((ValueError, RuntimeError)):
                worker.capture_layout()
            self.assertFalse(any(c[0] == "exec-out" for c in adb.commands))
            self.assertEqual("release", adb.requests[-1]["command"])

    def test_stale_worker_response_ignored(self):
        adb = FakePrivateAdb()
        adb.mode = "stale"
        data, _ = RealCaptureWorker(adb, APP).capture_layout()
        inspect_snapshot(data, APP)

    def test_workers_are_isolated_and_package_is_validated(self):
        first, second = RealCaptureWorker(None, APP), RealCaptureWorker(None, APP)
        self.assertNotEqual(first.control, second.control)
        for app in ("example.app; reboot", "../../", "app", ""):
            with self.assertRaises(ValueError):
                RealCaptureWorker(None, app)


@unittest.skipUnless(os.environ.get("CODEGEN_COLLECTOR_GUI_TEST") == "1", "Opt-in desktop UI smoke test")
class RealCollectorGuiTest(PrivateCollectionCase):
    def test_desktop_import_selection_confirmation_resume_and_background_job(self):
        import tkinter as tk
        from capture.real_gui import CollectorApp
        window = tk.Tk()
        window.withdraw()
        self.addCleanup(window.destroy)
        app = CollectorApp(window, self.store, auto_refresh=False)
        for index, (order, selected) in enumerate([(("A", "B", "C"), 1),
                                                  (("B", "A", "C"), 2),
                                                  (("B", "C", "A"), 3)]):
            self.add(order)
            app.open_session(self.gid, index)
            window.update()
            self.assertIsNone(app.session["states"][index]["confirmedAt"])
            app.table.selection_set(str(selected))
            window.update()
            self.assertEqual({selected}, app.selected)
            app.note.set("" if index == 0 else "reorder")
            app.confirm()
        app.query.set("no match")
        app.render_nodes()
        window.update()
        self.assertEqual({3}, app.selected)
        self.assertIn("搜索结果外", app.selection_text.get())
        app.clear_search()
        app.select_parent()
        self.assertEqual({0}, app.selected)
        app.open_session(self.gid, 2)
        self.assertEqual({3}, app.selected)
        app.selected = {2}
        with self.assertRaisesRegex(ValueError, "尚未保存"):
            app.export_task()
        self.assertFalse(self.store.export_path(self.gid).exists())
        app.selected = {3}
        app.error = Mock()
        app.run_job(lambda: 1 / 0, lambda _: None, "test failure")
        deadline = time.monotonic() + 3
        while app.busy and time.monotonic() < deadline:
            window.update()
            time.sleep(0.01)
        self.assertFalse(app.busy)
        app.error.assert_called_once()
        path = self.store.export(self.gid)
        app.open_session(self.gid)
        self.assertTrue(path.exists())
        self.assertEqual(1, self.store.report()["groups"])
        window.deiconify()
        for size in ("1360x880", "1040x700"):
            window.geometry(size)
            window.update()
            button = app.confirm_button
            self.assertTrue(button.winfo_ismapped())
            self.assertLessEqual(button.winfo_rooty() + button.winfo_height(),
                                 window.winfo_rooty() + window.winfo_height())
            self.assertLessEqual(button.winfo_rootx() + button.winfo_width(),
                                 window.winfo_rootx() + window.winfo_width())
