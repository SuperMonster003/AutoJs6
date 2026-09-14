"""Negative controls for lifecycle, window and unsaved editor-state evidence."""
import copy
import contextlib
import io
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import MagicMock, patch

from configuration_evidence import ACTIVITIES, validate_result
import run_configuration_probe as probe


def fixture(route="script", form="tablet"):
    case = "a" * 32
    activity = ACTIVITIES[route]
    state = {"activity": activity, "instance": "1", "taskId": 17,
             "configuration": {"orientation": 1, "smallestWidthDp": 800 if form == "tablet" else 411, "widthDp": 800},
             "focusedWindow": activity, "input": {"text": "// sdk37-unsaved-" + case},
             "editor": {"text": "// sdk37-unsaved-" + case, "changed": True, "ready": True,
                        "type": "ACE" if route == "edit-ace" else "NATIVE", "selection": "49:49"},
             "multiWindow": False, "imeVisible": True}
    result = {"caseId": case, "route": route, "form": form, "target": 37, "installedApkSha256": "b" * 64,
              "receipt": {"caseId": case, "target": 37, "sdk": 37, "ready": True, "complete": True, "errors": [],
                          "events": [{"event": "create", "activity": activity, "instance": "1"},
                                     {"event": "configuration", "activity": activity, "instance": "1"}],
                          "commands": [{"id": 1, "action": "launch"}, {"id": 2, "action": "finish"}]},
              "stages": {"portrait": copy.deepcopy(state)},
              "stop": {"exitCode": 0}, "displayCleanup": [{"exitCode": 0}] * 3}
    if route == "script":
        requested = copy.deepcopy(state)
        requested["requestedOrientation"] = 0
        requested["configuration"]["orientation"] = 1 if form == "tablet" else 2
        result["stages"]["requested-landscape"] = requested
    state["configuration"].update(orientation=2, widthDp=1280)
    result["stages"]["landscape"] = copy.deepcopy(state)
    state["imeVisible"] = False
    result["stages"]["keyboard-hidden"] = copy.deepcopy(state)
    if form == "tablet":
        result["stages"]["restored"] = copy.deepcopy(state)
        state["multiWindow"] = True
        state["configuration"]["widthDp"] = 673
        result["stages"]["split"] = copy.deepcopy(state)
        result.update(splitStart={"exitCode": 0}, splitExit={"exitCode": 0})
    return result


class ConfigurationEvidenceTest(unittest.TestCase):
    def test_supported_routes_and_forms(self):
        for route in ACTIVITIES:
            for form in ("phone", "tablet"):
                with self.subTest(route=route, form=form):
                    self.assertEqual([], validate_result(fixture(route, form)))

    def test_wrong_run_or_platform_never_passes(self):
        for key, value in (("caseId", "c" * 32), ("target", 36), ("sdk", 36), ("complete", False), ("errors", ["exception"])):
            result = fixture()
            result["receipt"][key] = value
            with self.subTest(key=key):
                self.assertTrue(validate_result(result))

    def test_text_survival_does_not_hide_script_recreation(self):
        result = fixture()
        result["stages"]["split"]["instance"] = "2"
        result["receipt"]["events"].append({"event": "create", "activity": ACTIVITIES["script"], "instance": "2"})
        self.assertIn("Running UI script was recreated", validate_result(result))

    def test_script_requires_configuration_callback(self):
        result = fixture()
        result["receipt"]["events"].pop()
        self.assertIn("Missing configuration callback evidence", validate_result(result))

    def test_editor_text_dirty_flag_engine_and_cursor_all_matter(self):
        for key, value in (("text", "initial"), ("changed", False), ("type", "NATIVE"), ("selection", "0:0"), ("ready", False)):
            result = fixture("edit-ace")
            result["stages"]["split"]["editor"][key] = value
            with self.subTest(key=key):
                self.assertTrue(validate_result(result))

    def test_host_draft_state_is_authoritative_when_observed(self):
        result = fixture("edit-ace")
        editor = result["stages"]["split"]["editor"]
        editor.update(changed=False, draftDirty=True)
        self.assertEqual([], validate_result(result))
        editor.update(changed=True, draftDirty=False)
        self.assertIn("split: text / unsaved state lost", validate_result(result))

    def test_real_split_resize_and_exit_are_required(self):
        for key, value in (("multiWindow", False), ("configuration", {"widthDp": 1280}), ("focusedWindow", "com.android.settings/.Settings")):
            result = fixture()
            result["stages"]["split"][key] = value
            with self.subTest(key=key):
                self.assertTrue(validate_result(result))

    def test_orientation_rules_differ_on_phone_and_tablet(self):
        for form in ("phone", "tablet"):
            result = fixture(form=form)
            result["stages"]["requested-landscape"]["configuration"]["orientation"] = 2 if form == "tablet" else 1
            self.assertIn("Requested orientation behavior mismatch", validate_result(result))

    def test_missing_keyboard_or_blocking_dialog_fails(self):
        for stage, key, value in (("portrait", "imeVisible", False), ("keyboard-hidden", "imeVisible", True),
                                  ("portrait", "focusedWindow", "Application Not Responding: " + ACTIVITIES["script"])):
            result = fixture()
            result["stages"][stage][key] = value
            self.assertTrue(validate_result(result))

    def test_restoring_a_clean_file_must_not_make_it_dirty(self):
        result = fixture("edit-native")
        result["editorState"] = "clean"
        for state in result["stages"].values():
            state["editor"].update(changed=False, draftDirty=False)
        self.assertEqual([], validate_result(result))
        result["stages"]["restored"]["editor"]["draftDirty"] = True
        self.assertIn("restored: text / unsaved state lost", validate_result(result))

    def test_a_collapsed_caret_does_not_prove_range_restoration(self):
        result = fixture("edit-ace")
        result["editorRange"] = True
        for state in result["stages"].values():
            state["editor"].update(selectionStart=12, selectionEnd=32)
        self.assertEqual([], validate_result(result))
        result["stages"]["split"]["editor"]["selectionStart"] = 32
        self.assertIn("split: editor range lost", validate_result(result))

    def test_large_draft_requires_full_content_and_file_restore_evidence(self):
        result = fixture("edit-ace")
        result["editorRepeat"] = 8192
        expected = (("// sdk37-unsaved-" + result["caseId"] + "\n") * 8192).encode()
        for state in result["stages"].values():
            state["editor"].update(textLength=len(expected), textSha256=hashlib.sha256(expected).hexdigest(), draftDirty=True)
        self.assertIn("Missing file-backed draft save evidence", validate_result(result))
        result["receipt"]["events"].append({"event": "save", "activity": ACTIVITIES["edit-ace"], "instance": "1", "extra": {"draftStorage": "file"}})
        self.assertEqual([], validate_result(result))
        result["stages"]["split"]["editor"]["textSha256"] = "0" * 64
        self.assertIn("split: text / unsaved state lost", validate_result(result))

    def test_keyboard_visibility_is_not_input_visibility(self):
        result = fixture("log")
        state = result["stages"]["portrait"]
        state.update(imeBottom=400, decor={"y": 0, "height": 1920})
        state["input"]["bounds"] = {"y": 1800, "width": 1000, "height": 60}
        self.assertIn("portrait: input overlaps the IME", validate_result(result))
        state["input"]["bounds"]["y"] = 1400
        self.assertEqual([], validate_result(result))

    def test_visible_input_does_not_allow_an_obscured_send_button(self):
        result = fixture("log", "phone")
        result["stages"]["portrait"]["controls"] = {
            "fab": {"shown": True, "bounds": {"x": 900, "y": 800, "width": 100, "height": 100}},
            "submit": {"shown": True, "bounds": {"x": 910, "y": 850, "width": 90, "height": 90}},
        }
        self.assertIn("portrait: clear button overlaps console submit", validate_result(result))
        result["stages"]["portrait"]["controls"]["fab"]["bounds"]["y"] = 750
        self.assertEqual([], validate_result(result))

    def test_editor_viewport_is_used_instead_of_its_scrollable_child(self):
        result = fixture("edit-native")
        state = result["stages"]["portrait"]
        state.update(imeBottom=400, decor={"y": 0, "height": 1920})
        state["input"]["bounds"] = {"y": 200, "width": 1000, "height": 3000}
        state["editor"]["bounds"] = {"y": 200, "width": 1000, "height": 1100}
        self.assertEqual([], validate_result(result))
        state["editor"]["bounds"]["height"] = 0
        self.assertIn("portrait: input viewport is empty", validate_result(result))

    def test_hiding_the_keyboard_must_restore_editor_controls(self):
        result = fixture("edit-native", "phone")
        for name in ("portrait", "keyboard-hidden"):
            result["stages"][name]["editor"]["chrome"] = {
                "input_method_enhance_bar": {"bounds": {"height": 184}},
                "editor_status_bar": {"bounds": {"height": 63}},
            }
        self.assertEqual([], validate_result(result))
        result["stages"]["keyboard-hidden"]["editor"]["chrome"]["input_method_enhance_bar"]["bounds"]["height"] = 0
        self.assertIn("keyboard-hidden: editor chrome not restored: input_method_enhance_bar", validate_result(result))

    def test_missing_or_duplicate_commands_and_failed_cleanup(self):
        result = fixture()
        result["receipt"]["commands"][1]["id"] = 1
        self.assertTrue(validate_result(result))
        result = fixture()
        result["displayCleanup"] = [{"exitCode": 1}] * 3
        self.assertTrue(validate_result(result))

    def test_malformed_evidence_fails_without_exception(self):
        for value in (None, [], {"caseId": 1}):
            self.assertTrue(validate_result(value))
        for key, value in (("stages", None), ("receipt", []), ("installedApkSha256", None)):
            result = fixture()
            result[key] = value
            self.assertTrue(validate_result(result))

    def test_preflight_rejection_does_not_mutate_the_device(self):
        for emulator in (False, True):
            device = MagicMock()
            device.identity.return_value = {"emulator": emulator, "sdk": "37"}
            device.shell.return_value = {"exitCode": 0, "stdout": "targetSdk=36 DEBUGGABLE", "stderr": ""}
            with tempfile.TemporaryDirectory() as folder:
                argv = ["probe", "--adb", "unused", "--serial", "emulator-5592", "--expected-target", "37",
                        "--route", "script", "--form", "tablet", "--output", str(Path(folder) / "result")]
                with patch("sys.argv", argv), patch.object(probe, "Device", return_value=device), \
                        patch.object(probe, "snapshot"), contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(1, probe.main())
            self.assertFalse(any(call.args[0] in ("am", "wm", "input", "rm") for call in device.shell.call_args_list))
            device.call.assert_not_called()


if __name__ == "__main__":
    unittest.main()
