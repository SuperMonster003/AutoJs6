"""Host-side checks for the SDK 37 gate: badging, expectations, suite selection and report parsing; no adb calls."""
import tempfile
import unittest
from pathlib import Path

import run_gate as gate


BADGING = """package: name='org.autojs.autojs6' versionCode='5280' versionName='6.8.0' platformBuildVersionName='17' platformBuildVersionCode='37' compileSdkVersion='37' compileSdkVersionCodename='17'
sdkVersion:'24'
targetSdkVersion:'37'
uses-permission: name='android.permission.INTERNET'
uses-permission: name='android.permission.ACCESS_LOCAL_NETWORK'
application-debuggable
native-code: 'arm64-v8a' 'armeabi-v7a' 'x86' 'x86_64'
"""

COMPAT = """  ChangeId(416031865; name=PARCEL_HARDENING; enableSinceTargetSdk=37; packageOverrides={org.autojs.autojs6=false})
  ChangeId(1; name=USE_NEW_MESSAGEQUEUE; enableSinceTargetSdk=37)
  ChangeId(2; name=LOOPER_CLEARS_THREAD_INTERRUPTED; enableSinceTargetSdk=37; packageOverrides={other.app=true})
"""


class BadgingTest(unittest.TestCase):
    def test_fields_are_extracted(self):
        parsed = gate.parse_badging(BADGING)
        self.assertEqual(parsed["package"], "org.autojs.autojs6")
        self.assertEqual((parsed["compileSdk"], parsed["targetSdk"], parsed["minSdk"]), (37, 37, 24))
        self.assertTrue(parsed["debuggable"])
        self.assertTrue(parsed["localNetworkPermission"])
        self.assertEqual(parsed["abis"], ["arm64-v8a", "armeabi-v7a", "x86", "x86_64"])

    def test_target_36_without_the_permission(self):
        parsed = gate.parse_badging(BADGING.replace("targetSdkVersion:'37'", "targetSdkVersion:'36'")
                                    .replace("uses-permission: name='android.permission.ACCESS_LOCAL_NETWORK'\n", ""))
        self.assertEqual(parsed["targetSdk"], 36)
        self.assertFalse(parsed["localNetworkPermission"])

    def test_unrecognized_output_is_rejected(self):
        with self.assertRaises(ValueError):
            gate.parse_badging("not badging")

    def test_test_apk_without_version_and_aapt2_min_sdk_spelling(self):
        parsed = gate.parse_badging("package: name='org.autojs.autojs6.test' versionCode='' versionName=''\nminSdkVersion:'24'\ntargetSdkVersion:'37'\napplication-debuggable\n")
        self.assertEqual((parsed["package"], parsed["versionCode"], parsed["minSdk"], parsed["targetSdk"]), ("org.autojs.autojs6.test", None, 24, 37))
        self.assertEqual(parsed["abis"], [])

    def test_abi_selection_prefers_the_matching_split_then_universal(self):
        apks = [{"path": "a/autojs6-arm64-v8a.apk", "abis": ["arm64-v8a"]},
                {"path": "a/autojs6-universal.apk", "abis": ["arm64-v8a", "x86_64"]}]
        self.assertEqual(gate.pick_for_abi(apks, "arm64-v8a,armeabi-v7a")["path"], "a/autojs6-arm64-v8a.apk")
        self.assertEqual(gate.pick_for_abi(apks, "x86_64,x86")["path"], "a/autojs6-universal.apk")
        with self.assertRaises(RuntimeError):
            gate.pick_for_abi([{"path": "a/x86.apk", "abis": ["x86"]}, {"path": "a/arm.apk", "abis": ["armeabi-v7a"]}], "x86_64")


class ExpectationTest(unittest.TestCase):
    def test_looper_expectations_follow_the_readme_table(self):
        self.assertEqual(gate.looper_expectations(37, 37, {}), {"clearInterrupt": "true", "poolStarts": "0"})
        self.assertEqual(gate.looper_expectations(37, 36, {}), {"clearInterrupt": "false", "poolStarts": "1"})
        self.assertEqual(gate.looper_expectations(28, 37, {}), {"clearInterrupt": "false", "poolStarts": "1"})
        with self.assertRaises(RuntimeError):
            gate.looper_expectations(37, 37, {"USE_NEW_MESSAGEQUEUE": True})

    def test_compat_overrides_are_read_for_the_host_only(self):
        overrides = gate.compat_overrides(COMPAT, "org.autojs.autojs6", gate.LOOPER_CHANGES + ("PARCEL_HARDENING",))
        self.assertEqual(overrides, {"PARCEL_HARDENING": False})
        self.assertEqual(gate.compat_overrides(COMPAT, "org.autojs.autojs6", gate.LOOPER_CHANGES), {})

    def test_suite_selection_by_device(self):
        emulator = {"sdk": "37", "emulator": True}
        phone = {"sdk": "28", "emulator": False}
        self.assertEqual(gate.select_suites(emulator, 37, set(), ["auto"]),
                         ["cleartext", "dex", "install-state", "looper-platform", "media", "parcel", "looper-full", "a11y-smoke"])
        self.assertEqual(gate.select_suites(phone, 37, set(gate.NATIVE_PLUGINS), ["auto"]),
                         ["cleartext", "dex", "install-state", "looper-platform", "media", "native"])
        self.assertEqual(gate.select_suites(phone, 37, set(), ["dex", "dex", "media"]), ["dex", "media"])


class SourceListingTest(unittest.TestCase):
    def test_methods_are_listed_from_the_checked_out_sources(self):
        media = gate.test_methods(gate.MEDIA_CLASS)
        self.assertEqual(len(media), 11)
        self.assertTrue(all(method.startswith(gate.MEDIA_CLASS + "#") for method in media))
        self.assertEqual(len(gate.test_methods(gate.LOOPER_PLATFORM_CLASS)) + len(gate.test_methods(gate.LOOPER_SCRIPT_CLASS)), 14)
        with self.assertRaises((ValueError, OSError)):
            gate.test_methods("org.autojs.autojs.core.media.MissingDeviceTest")


class AccessibilityStateTest(unittest.TestCase):
    class FakeDevice:
        def __init__(self, dump):
            self.dump = dump

        def shell(self, *args):
            return {"exitCode": 0, "stdout": self.dump, "stderr": ""}

    def test_bound_requires_the_bound_entry_not_only_the_enabled_entry(self):
        enabled = "     Bound services:{}\n     Enabled services:{{" + gate.A11Y_SERVICE + "}}\n     Crashed services:{{" + gate.A11Y_SERVICE + "}}\n"
        bound = "     Bound services:{Service[label=AutoJs6, feedbackType[FEEDBACK_SPOKEN]]}\n     Enabled services:{{" + gate.A11Y_SERVICE + "}}\n     Crashed services:{}\n"
        self.assertFalse(gate.a11y_bound(self.FakeDevice(enabled)))
        self.assertTrue(gate.a11y_bound(self.FakeDevice(bound)))
        self.assertFalse(gate.a11y_bound(self.FakeDevice(bound.replace(gate.A11Y_SERVICE, "other/Service"))))


class ReportParsingTest(unittest.TestCase):
    def test_smoke_log_requires_completion_and_no_failures(self):
        good = "GATE_START x\nLOG [PASS] a\nWARN [SKIP] restart\nLOG 52/52 checks passed\nGATE_DONE ok\n"
        parsed = gate.parse_smoke_log(good)
        self.assertTrue(parsed["success"])
        self.assertEqual((parsed["passed"], parsed["total"], parsed["skipped"]), (52, 52, ["restart"]))
        self.assertFalse(gate.parse_smoke_log(good.replace("52/52", "51/52") + "ERR [FAIL] b\n")["success"])
        multiline = "GATE_START x\nLOG [PASS] a\nLOG \n42/42 checks passed\nGATE_DONE ok\n"
        self.assertEqual((gate.parse_smoke_log(multiline)["passed"], gate.parse_smoke_log(multiline)["success"]), (42, True))
        self.assertFalse(gate.parse_smoke_log(good.replace("GATE_DONE ok", "GATE_DONE error Error: x"))["success"])
        self.assertFalse(gate.parse_smoke_log("GATE_START x\nLOG [PASS] a\n")["success"])

    def test_junit_summary_counts_and_lists_failures(self):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, "TEST-a.xml").write_text(
                '<testsuite tests="3" failures="1" errors="0" skipped="1"><testcase classname="A" name="ok"/>'
                '<testcase classname="A" name="bad"><failure/></testcase><testcase classname="A" name="skip"><skipped/></testcase></testsuite>',
                encoding="utf-8")
            summary = gate.junit_summary([directory])
            self.assertEqual((summary["tests"], summary["failures"], summary["skipped"], summary["files"]), (3, 1, 1, 1))
            self.assertEqual(summary["failing"], ["A#bad"])
            self.assertFalse(summary["success"])
            self.assertFalse(gate.junit_summary([Path(directory, "missing")])["success"])


if __name__ == "__main__":
    unittest.main()
