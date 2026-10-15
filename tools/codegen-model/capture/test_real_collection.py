import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest
from unittest.mock import Mock, patch

from capture.real_device import PrivateAdb, RealCaptureWorker, list_devices
from capture.real_store import (CollectionStore, NOTE_HISTORY_LIMIT, SEARCH_HISTORY_LIMIT, WorkspaceLock,
                                inspect_snapshot, private_write_json)
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
    def test_delete_state_removes_only_its_snapshot_and_does_not_reuse_ids(self):
        for i, order in enumerate((("A", "B", "C"), ("B", "A", "C"), ("B", "C", "A"))):
            self.add(order)
            self.store.confirm(self.gid, i, [i + 1], "" if i == 0 else "reorder")
        original = self.store.load(self.gid)
        removed = self.store.folder(self.gid) / original["states"][1]["snapshot"]
        result = self.store.delete_state(self.gid, 1)
        self.assertFalse(removed.exists())
        self.assertEqual(["s0", "s2"], [s["stateId"] for s in result["states"]])
        self.assertEqual([original["states"][0], original["states"][2]], result["states"])
        result = self.add(("C", "A", "B"))
        self.assertEqual(["s0", "s2", "s3"], [s["stateId"] for s in result["states"]])
        for i in range(3):
            self.store.read_state(result, i)

    def test_deleting_baseline_requires_new_notes_and_confirmation(self):
        for i, order in enumerate((("A", "B", "C"), ("B", "A", "C"), ("B", "C", "A"))):
            self.add(order)
            self.store.confirm(self.gid, i, [i + 1], "" if i == 0 else "reorder")
        result = self.store.delete_state(self.gid, 0)
        self.assertEqual([[[1]], [[2]]], [s["targets"] for s in result["states"]])
        self.assertTrue(all(s["confirmedAt"] is None and s["note"] == "" for s in result["states"]))
        with self.assertRaises(ValueError):
            self.store.export(self.gid)

    def test_legacy_draft_can_delete_all_states_and_resume_without_reusing_ids(self):
        self.add()
        session = self.add(("B", "A", "C"))
        del session["nextStateNumber"]
        self.store.save(session)
        self.store.delete_state(self.gid, 1)
        self.store.delete_state(self.gid, 0)
        self.assertEqual([], self.store.load(self.gid)["states"])
        self.assertEqual("s2", self.add()["states"][0]["stateId"])

    def test_delete_state_write_failure_preserves_live_snapshot(self):
        session = self.add()
        path = self.store.folder(self.gid) / session["states"][0]["snapshot"]
        before = path.read_bytes()
        with patch("capture.real_store.private_write_json", side_effect=OSError("disk full")):
            with self.assertRaises(OSError):
                self.store.delete_state(self.gid, 0)
        self.assertEqual(before, path.read_bytes())
        self.assertEqual(1, len(self.store.load(self.gid)["states"]))

    def test_delete_state_unlink_failure_keeps_valid_references_and_reports_error(self):
        session = self.add()
        path = self.store.folder(self.gid) / session["states"][0]["snapshot"]
        with patch.object(Path, "unlink", side_effect=OSError("file locked")):
            with self.assertRaisesRegex(OSError, "状态已从草稿移除"):
                self.store.delete_state(self.gid, 0)
        self.assertEqual([], self.store.load(self.gid)["states"])
        self.assertTrue(path.exists())

    def test_delete_draft_removes_its_folder_without_touching_other_drafts(self):
        self.add()
        another = self.store.create(app=APP, title="Another task")
        folder = self.store.folder(self.gid)
        self.store.delete_draft(self.gid)
        self.assertFalse(folder.exists())
        self.assertEqual(another["groupId"], self.store.load(another["groupId"])["groupId"])
        self.assertEqual(1, self.store.report()["drafts"])

    def test_exported_group_and_states_cannot_be_deleted(self):
        task = self.complete()
        before = {p: p.read_bytes() for p in task.parent.rglob("*") if p.is_file()}
        for operation in (lambda: self.store.delete_draft(self.gid), lambda: self.store.delete_state(self.gid, 0)):
            with self.assertRaises(ValueError):
                operation()
        self.assertEqual(before, {p: p.read_bytes() for p in task.parent.rglob("*") if p.is_file()})

    def make_directory_link(self, link, target):
        try:
            link.symlink_to(target, target_is_directory=True)
        except OSError:
            if os.name != "nt":
                raise
            env = dict(os.environ, CODEGEN_LINK_TEST=str(link), CODEGEN_LINK_TARGET=str(target))
            subprocess.run(["powershell", "-NoProfile", "-Command",
                            "New-Item -ItemType Junction -Path $env:CODEGEN_LINK_TEST -Target $env:CODEGEN_LINK_TARGET | Out-Null"],
                           env=env, check=True, capture_output=True)

    def test_deletion_rejects_nested_junction_and_other_group_alias_before_removing_anything(self):
        self.add()
        other = self.store.create(app=APP, title="Other")
        other_folder = self.store.folder(other["groupId"])
        link = self.store.folder(self.gid) / "external"
        self.make_directory_link(link, other_folder)
        with self.assertRaises(ValueError):
            self.store.delete_draft(self.gid)
        self.assertEqual(1, len(self.store.load(self.gid)["states"]))
        self.assertEqual(other["groupId"], self.store.load(other["groupId"])["groupId"])
        alias_id = "real-" + "0" * 32
        self.make_directory_link(self.store.sessions / alias_id, other_folder)
        with self.assertRaises(ValueError):
            self.store.delete_draft(alias_id)
        self.assertTrue(other_folder.exists())

    def test_note_history_is_recent_unique_bounded_persistent_and_preserves_device(self):
        self.store.update_preferences(serial="Sony", anotherPreference=True)
        for i in range(NOTE_HISTORY_LIMIT + 5):
            self.store.remember_note(f"变化 {i}")
        expected = [f"变化 {i}" for i in range(NOTE_HISTORY_LIMIT + 4, 4, -1)]
        self.assertEqual(expected, self.store.note_history())
        self.store.remember_note("  变化 9  ")
        self.store.remember_note("  ")
        expected.remove("变化 9")
        expected.insert(0, "变化 9")
        resumed = CollectionStore(self.root)
        self.assertEqual(expected, resumed.note_history())
        self.assertEqual("Sony", resumed.preferences()["serial"])
        self.assertTrue(resumed.preferences()["anotherPreference"])
        resumed.update_preferences(serial="another device")
        self.assertEqual(expected, resumed.note_history())

    def test_search_history_rolls_over_and_persists_without_overwriting_other_preferences(self):
        self.store.update_preferences(serial="Sony", otherPreference=True)
        self.store.remember_note("列表重排")
        for i in range(SEARCH_HISTORY_LIMIT + 4):
            self.store.remember_search(f"条目 {i}")
        expected = [f"条目 {i}" for i in range(SEARCH_HISTORY_LIMIT + 3, 3, -1)]
        self.assertEqual(expected, self.store.search_history())
        self.store.remember_search("  条目 8  ")
        self.store.remember_search("  ")
        expected.remove("条目 8")
        expected.insert(0, "条目 8")
        self.store.update_preferences(serial="second device")
        self.store.remember_note("进度条出现")
        resumed = CollectionStore(self.root)
        self.assertEqual(expected, resumed.search_history())
        self.assertEqual(["进度条出现", "列表重排"], resumed.note_history())
        self.assertEqual("second device", resumed.preferences()["serial"])
        self.assertTrue(resumed.preferences()["otherPreference"])

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
    def create_app(self):
        import tkinter as tk
        from capture.real_gui import CollectorApp
        window = tk.Tk()
        window.withdraw()
        self.addCleanup(window.destroy)
        app = CollectorApp(window, self.store, auto_refresh=False)
        return window, app

    def wait_job(self, window, app):
        deadline = time.monotonic() + 5
        while app.busy and time.monotonic() < deadline:
            window.update()
            time.sleep(0.01)
        self.assertFalse(app.busy)
        window.update()

    def test_preview_refresh_reapplies_search_to_new_nodes_and_return_restores_task_query(self):
        window, app = self.create_app()
        self.add()
        app.open_session(self.gid)
        app.query.set("Item B")
        app.apply_search()
        adb = Mock()
        adb.foreground_package.return_value = APP
        app.selected_adb = Mock(return_value=adb)
        app.read_phone_layout = Mock(side_effect=[(snapshot(("B", "A", "C")), {}),
                                                 (snapshot(("C", "B", "A")), {})])
        app.preview_current()
        self.wait_job(window, app)
        self.assertEqual("Item B", app.query.get())
        self.assertEqual({1}, app.visible)
        app.query.set("Item A")
        app.apply_search()
        self.assertEqual({2}, app.visible)
        app.preview_current()
        self.wait_job(window, app)
        self.assertEqual("Item A", app.query.get())
        self.assertEqual({3}, app.visible)
        self.assertEqual(set(), app.selected)
        app.return_to_task()
        window.update()
        self.assertEqual("Item B", app.query.get())
        self.assertEqual({2}, app.visible)
        self.assertEqual(1, len(self.store.load(self.gid)["states"]))

    def test_capture_import_state_switch_and_confirmation_keep_search_applied(self):
        window, app = self.create_app()
        self.add()
        self.store.confirm(self.gid, 0, [1], "")
        app.open_session(self.gid)
        app.query.set("Item A")
        app.apply_search()
        app.selected_adb = Mock(return_value=Mock())
        app.read_phone_layout = Mock(return_value=(snapshot(("B", "A", "C")), {}))
        app.capture()
        self.wait_job(window, app)
        self.assertEqual("Item A", app.query.get())
        self.assertEqual({2}, app.visible)
        self.assertEqual({2}, app.selected)
        app.note.set("重排")
        app.confirm()
        window.update()
        self.assertEqual({2}, app.visible)
        imported = self.root / "next.layout.json"
        imported.write_bytes(snapshot(("C", "B", "A")))
        with patch("capture.real_gui.filedialog.askopenfilenames", return_value=(str(imported),)):
            app.import_layout()
        self.wait_job(window, app)
        self.assertEqual("Item A", app.query.get())
        self.assertEqual({3}, app.visible)
        app.show_state(0)
        window.update()
        self.assertEqual({1}, app.visible)
        self.assertEqual("Item A", app.query.get())

    def test_search_history_dropdown_applies_immediately_and_preserves_unsubmitted_input(self):
        self.store.remember_search("Item B")
        window, app = self.create_app()
        self.add()
        self.add(("B", "A", "C"))
        app.open_session(self.gid, 0)
        self.assertEqual(("Item B",), app.search_entry["values"])
        app.search_entry.current(0)
        app.search_entry.event_generate("<<ComboboxSelected>>")
        window.update()
        self.assertEqual({2}, app.visible)
        app.query.set("Item C")
        app.show_state(1)
        window.update()
        self.assertEqual({3}, app.visible)
        self.assertEqual(["Item C", "Item B"], CollectionStore(self.root).search_history())
        self.assertIsNone(self.store.load(self.gid)["states"][1]["confirmedAt"])

    def test_show_all_reveals_first_highlight_in_a_long_layout_every_time(self):
        window, app = self.create_app()
        self.gid = self.store.create(app=APP, title="long collection", scope="collection")["groupId"]
        self.add(tuple(f"row-{i:03}" for i in range(150)))
        self.store.confirm(self.gid, 0, [89, 125], "")
        app.open_session(self.gid)
        window.deiconify()
        window.update()
        app.query.set("Item row-088")
        app.apply_search()
        window.update()
        self.assertEqual({89}, app.visible)
        app.clear_search()
        window.update()
        self.assertEqual("", app.query.get())
        self.assertEqual("89", app.table.focus())
        self.assertTrue(app.table.bbox("89"))
        self.assertEqual({89, 125}, app.selected)
        app.navigate_highlight(1)
        window.update()
        self.assertEqual("125", app.table.focus())
        self.assertTrue(app.table.bbox("125"))
        app.clear_search()
        window.update()
        self.assertEqual("89", app.table.focus())
        self.assertTrue(app.table.bbox("89"))
        self.assertIn("Item row-088", self.store.search_history())

    def test_highlight_navigation_wraps_without_changing_targets_or_search(self):
        window, app = self.create_app()
        self.add()
        app.open_session(self.gid)
        app.selected = {1, 3}
        app.query.set("Item")
        app.apply_search()
        window.update()
        before = (self.store.folder(self.gid) / "session.json").read_bytes()
        for direction, expected in ((1, 1), (1, 3), (1, 1), (-1, 3), (-1, 1)):
            app.navigate_highlight(direction)
            window.update()
            self.assertEqual(str(expected), app.table.focus())
            self.assertTrue(app.details.get("1.0", "end").startswith(f"[{expected}]"))
            self.assertEqual({1, 3}, app.selected)
            self.assertEqual({"1", "3"}, set(app.table.selection()))
            self.assertEqual("Item", app.query.get())
        app.query.set("missing item")
        app.apply_search()
        window.update()
        self.assertTrue(app.next_highlight_button.instate(["disabled"]))
        self.assertTrue(app.previous_highlight_button.instate(["disabled"]))
        self.assertEqual({1, 3}, app.selected)
        app.clear_search()
        window.update()
        self.assertEqual("1", app.table.focus())
        app.clear_selection()
        window.update()
        app.navigate_highlight(1)
        self.assertTrue(app.next_highlight_button.instate(["disabled"]))
        self.assertEqual(before, (self.store.folder(self.gid) / "session.json").read_bytes())

    def test_preview_without_task_never_creates_a_group_and_disables_task_actions(self):
        window, app = self.create_app()
        before = {p: p.read_bytes() for p in self.root.rglob("*") if p.is_file()}
        adb = Mock()
        adb.foreground_package.return_value = APP
        app.selected_adb = Mock(return_value=adb)
        app.read_phone_layout = Mock(return_value=(snapshot(), {}))
        app.preview_current()
        self.wait_job(window, app)
        self.assertIsNone(app.session)
        self.assertIsNotNone(app.preview_return)
        self.assertEqual(4, len(app.nodes))
        self.assertTrue(app.confirm_button.instate(["disabled"]))
        self.assertTrue(app.export_button.instate(["disabled"]))
        self.assertTrue(app.capture_button.instate(["disabled"]))
        with self.assertRaises(ValueError):
            app.confirm()
        self.assertEqual(before, {p: p.read_bytes() for p in self.root.rglob("*") if p.is_file()})
        app.return_to_task()
        self.assertIsNone(app.preview_return)
        self.assertEqual([], app.nodes)

    def test_preview_preserves_unsaved_selection_note_query_and_can_refresh_other_app(self):
        window, app = self.create_app()
        self.add()
        app.open_session(self.gid)
        app.selected = {2}
        app.query.set("B")
        app.note.set("尚未确认的说明")
        app.render_nodes()
        window.update()
        before = (self.store.folder(self.gid) / "session.json").read_bytes()
        adb = Mock()
        adb.foreground_package.return_value = "another.app"
        app.selected_adb = Mock(return_value=adb)
        app.read_phone_layout = Mock(return_value=(snapshot(package="another.app"), {}))
        app.preview_current()
        self.wait_job(window, app)
        self.assertIn("another.app", app.state_text.get())
        app.read_phone_layout.assert_called_once_with(adb, "another.app")
        self.assertTrue(app.delete_state_button.instate(["disabled"]))
        app.preview_current()
        self.wait_job(window, app)
        app.return_to_task()
        window.update()
        self.assertEqual({2}, app.selected)
        self.assertEqual("B", app.query.get())
        self.assertEqual("尚未确认的说明", app.note.get())
        self.assertEqual(before, (self.store.folder(self.gid) / "session.json").read_bytes())
        self.assertIn("尚未确认的说明", self.store.note_history())
        self.assertTrue(app.confirm_button.instate(["!disabled"]))

    def test_preview_failure_leaves_task_view_intact(self):
        window, app = self.create_app()
        self.add()
        app.open_session(self.gid)
        app.selected = {1}
        app.error = Mock()
        adb = Mock()
        adb.foreground_package.side_effect = RuntimeError("device disconnected")
        app.selected_adb = Mock(return_value=adb)
        app.preview_current()
        self.wait_job(window, app)
        self.assertIsNone(app.preview_return)
        self.assertEqual({1}, app.selected)
        self.assertEqual(self.gid, app.session["groupId"])
        app.error.assert_called_once()

    def test_deletion_buttons_cancel_or_remove_and_reload_selection(self):
        window, app = self.create_app()
        self.add()
        self.add(("B", "A", "C"))
        app.open_session(self.gid, 1)
        with patch("capture.real_gui.messagebox.askyesno", return_value=False):
            app.delete_state()
            app.delete_draft()
        self.assertEqual(2, len(self.store.load(self.gid)["states"]))
        with patch("capture.real_gui.messagebox.askyesno", return_value=True):
            app.delete_state()
        self.wait_job(window, app)
        self.assertEqual(0, app.state_index)
        self.assertEqual(1, len(app.session["states"]))
        with patch("capture.real_gui.messagebox.askyesno", return_value=True):
            app.delete_state()
        self.wait_job(window, app)
        self.assertIsNone(app.state_index)
        self.assertTrue(app.delete_state_button.instate(["disabled"]))
        with patch("capture.real_gui.messagebox.askyesno", return_value=True):
            app.delete_draft()
        self.wait_job(window, app)
        self.assertIsNone(app.session)
        self.assertEqual([], app.group_ids)
        self.assertEqual(0, app.report_data["drafts"])

    def test_note_history_saves_unconfirmed_input_on_navigation_and_keeps_device_preference(self):
        window, app = self.create_app()
        self.add()
        self.add(("B", "A", "C"))
        app.open_session(self.gid, 0)
        app.note.set("输入但尚未确认")
        app.show_state(1)
        window.update()
        self.assertIn("输入但尚未确认", app.note_entry["values"])
        self.assertIsNone(self.store.load(self.gid)["states"][0]["confirmedAt"])
        app.devices = {"phone": {"serial": "Sony"}}
        app.device.set("phone")
        app.remember_device()
        self.assertEqual(["输入但尚未确认"], self.store.note_history())
        self.assertEqual("Sony", self.store.preferences()["serial"])

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
            for button in (app.confirm_button, app.preview_button, app.return_button,
                           app.delete_draft_button, app.delete_state_button,
                           app.previous_highlight_button, app.next_highlight_button):
                self.assertTrue(button.winfo_ismapped())
                self.assertLessEqual(button.winfo_rooty() + button.winfo_height(),
                                     window.winfo_rooty() + window.winfo_height())
                self.assertLessEqual(button.winfo_rootx() + button.winfo_width(),
                                     window.winfo_rootx() + window.winfo_width())
