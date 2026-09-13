"""Reject chooser evidence that confuses disabled gates, cancellation or URI access."""
import contextlib
import copy
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import MagicMock, patch

import run_web_files_probe as probe


class WebFilesEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.row = {"name": "openSelect", "dom": {"kind": "open", "name": "case.txt", "text": "unique", "permission": "granted"},
                    "callback": {"mode": 0, "permissionMode": 1, "action": "android.intent.action.OPEN_DOCUMENT"},
                    "result": {"code": -1, "uri": "content://provider/case", "flags": 67, "readGranted": True, "writeGranted": True}}

    def validate(self, row, enabled=True):
        probe.validate_chooser(row, enabled, "case.txt", "unique")

    def test_content_and_grants_must_both_match(self):
        self.validate(self.row)
        for section, key, value in (("dom", "text", "old"), ("dom", "kind", "input"),
                                    ("result", "readGranted", False), ("result", "writeGranted", False),
                                    ("result", "flags", 1), ("result", "uri", None)):
            row = copy.deepcopy(self.row)
            row[section][key] = value
            with self.subTest(section=section, key=key), self.assertRaises(ValueError):
                self.validate(row)

    def test_disabled_gate_has_different_intent_and_grants(self):
        with self.assertRaises(ValueError):
            self.validate(self.row, False)
        self.row["callback"]["action"] = "android.intent.action.GET_CONTENT"
        self.row["result"].update(flags=1, writeGranted=False)
        self.validate(self.row, False)

    def test_blocked_mode_is_not_a_native_cancellation(self):
        row = {"name": "directoryCancel", "dom": {"kind": "directory", "error": "AbortError"},
               "outcome": "blocked-before-chooser", "callback": None, "result": None}
        self.validate(row, False)
        row["result"] = {"code": 0, "uri": None}
        with self.assertRaises(ValueError):
            self.validate(row, False)

    def test_cancellation_requires_native_and_browser_agreement(self):
        self.row["name"] = "openCancel"
        self.row["dom"] = {"kind": "open", "error": "AbortError"}
        with self.assertRaises(ValueError):
            self.validate(self.row)
        self.row["result"] = {"code": 0, "uri": None, "flags": 0, "readGranted": False, "writeGranted": False}
        self.validate(self.row)

    def test_preflight_rejection_does_not_stop_the_host(self):
        for emulator in (False, True):
            device = MagicMock()
            device.identity.return_value = {"emulator": emulator, "sdk": "37"}
            device.shell.return_value = {"exitCode": 0, "stdout": "targetSdk=36 DEBUGGABLE", "stderr": ""}
            with tempfile.TemporaryDirectory() as folder:
                argv = ["probe", "--adb", "unused-adb", "--serial", "emulator-5590", "--expected-target", "37",
                        "--output", str(Path(folder) / "result")]
                with patch("sys.argv", argv), patch.object(probe, "Device", return_value=device), \
                        patch.object(probe, "snapshot"), contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(1, probe.main())
            self.assertFalse(any(call.args[0] in ("am", "rm", "input") for call in device.shell.call_args_list))
            device.call.assert_not_called()


if __name__ == "__main__":
    unittest.main()
