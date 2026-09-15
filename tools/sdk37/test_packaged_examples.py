"""Host-side self-checks for run_packaged_examples.py; no device is contacted."""
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import run_packaged_examples as examples  # noqa: E402


def receipt(example, checks, **extra):
    names = examples.EXPECTED_CHECKS["common"] + examples.EXPECTED_CHECKS[example]
    rows = [{"name": name, "passed": checks.get(name, True), "detail": "" if checks.get(name, True) else "returned false"} for name in names]
    return {"example": example, "package": examples.package_of(example), "api": 37, "target": 37, "checks": rows, "done": True, **extra}


class EvaluateTest(unittest.TestCase):
    def test_all_checks_pass(self):
        verdict = examples.evaluate("a11y", receipt("a11y", {}), [("install", True, "Success")], [])
        self.assertTrue(verdict["success"], verdict)
        self.assertEqual(verdict["scriptChecks"], len(examples.EXPECTED_CHECKS["common"]) + len(examples.EXPECTED_CHECKS["a11y"]))

    def test_failed_script_check_is_named(self):
        verdict = examples.evaluate("ui", receipt("ui", {"ui-edge-to-edge": False}), [], [])
        self.assertFalse(verdict["success"])
        self.assertIn("ui-edge-to-edge: returned false", verdict["failures"])

    def test_missing_check_runner_failure_and_crash(self):
        data = receipt("timer", {})
        data["checks"] = [row for row in data["checks"] if row["name"] != "tts-done"]
        verdict = examples.evaluate("timer", data, [("timed-task-fired", False, "no timed receipt")], ["AndroidRuntime: FATAL"])
        self.assertEqual(verdict["failures"], ["tts-done: missing", "timed-task-fired: no timed receipt", "AndroidRuntime crash logged"])

    def test_no_receipt_and_wrong_target(self):
        self.assertEqual(examples.evaluate("a11y", None, [], [])["failures"], ["no receipt"])
        wrong = receipt("a11y", {}, target=36)
        self.assertIn("receipt target 36", examples.evaluate("a11y", wrong, [], [])["failures"])


class RuntimeKitTest(unittest.TestCase):
    def test_reads_kit_metadata_from_plugin_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "plugin.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/runtime-kit/runtime-kit.json", json.dumps({
                    "runtimeKitId": "autojs6-runtime-v6.8.0+5280-abc-universal",
                    "host": {"versionCode": 5280}, "template": {"sha256": "0e90"}, "compatibility": {"minHostVersionCode": 5280}}))
            kit = examples.runtime_kit_of_plugin(apk)
        self.assertEqual(kit["runtimeKitId"], "autojs6-runtime-v6.8.0+5280-abc-universal")
        self.assertEqual(kit["host"]["versionCode"], 5280)
        self.assertIsNone(kit["contract"])


class MarkdownTest(unittest.TestCase):
    def test_report_lists_steps_examples_and_checks(self):
        report = {"started": "s", "finished": "f", "overall": "PASS", "pluginVersion": "6.8.0",
                  "device": {"serial": "emulator-5600", "model": "sdk_gphone16k_x86_64", "sdk": "37", "fingerprint": "fp"},
                  "runtimeKit": {"runtimeKitId": "kit", "template": {"sha256": "0123456789abcdef0123"}, "host": {"versionCode": 5280}},
                  "steps": [{"name": "host", "status": "PASS", "detail": "ok"}],
                  "examples": {"ui": {"apk": {"compileSdk": 37, "targetSdk": 37, "minSdk": 24, "localNetworkPermission": True, "sha256": "ab" * 32},
                                      "verdict": {"success": True, "failures": []}, "receipt": receipt("ui", {}),
                                      "runnerChecks": [("install", True, "Success")]}}}
        text = examples.markdown(report)
        self.assertIn("| host | PASS | ok |", text)
        self.assertIn("## ui (`org.autojs.autojs6.sdk37.ui`)", text)
        self.assertIn("| ui-edge-to-edge | True |", text)
        self.assertIn("| install | True | Success |", text)
        self.assertIn("Runtime Kit `kit`", text)


if __name__ == "__main__":
    unittest.main()
