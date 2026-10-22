"""Host-side checks for ci_gate.py APK selection, rerun selection and verdict; no adb calls.

py -3.12 -I -m unittest discover -s tools/memory -p "test_*.py"
"""
import unittest

import ci_gate


def result(scenario, status, reasons=()):
    return {"scenario": scenario, "mode": "normal", "status": status, "reasons": list(reasons)}


METADATA = {"elements": [
    {"type": "UNIVERSAL", "filters": [], "versionCode": 5835, "outputFile": "autojs6-v6.8.0-universal.apk"},
    {"type": "ONE_OF_MANY", "filters": [{"filterType": "ABI", "value": "x86_64"}], "versionCode": 5835,
     "outputFile": "autojs6-v6.8.0-x86_64.apk"},
    {"type": "ONE_OF_MANY", "filters": [{"filterType": "ABI", "value": "arm64-v8a"}], "versionCode": 5835,
     "outputFile": "autojs6-v6.8.0-arm64-v8a.apk"},
]}


class SelectionTest(unittest.TestCase):
    def test_host_apk_follows_the_first_device_abi_with_a_split(self):
        self.assertEqual(("autojs6-v6.8.0-x86_64.apk", 5835), ci_gate.host_apk_name(METADATA, ["x86_64", "arm64-v8a"]))
        self.assertEqual(("autojs6-v6.8.0-universal.apk", 5835), ci_gate.host_apk_name(METADATA, ["riscv64"]))
        single = {"elements": [{"type": "SINGLE", "filters": [], "versionCode": 0, "outputFile": "app-app-debug-androidTest.apk"}]}
        self.assertEqual("app-app-debug-androidTest.apk", ci_gate.host_apk_name(single, [])[0])

    def test_ci_scenarios_are_s1_to_s12(self):
        self.assertEqual("S1", ci_gate.CI_SCENARIOS[0])
        self.assertEqual("S12", ci_gate.CI_SCENARIOS[-1])
        self.assertIn("S4f", ci_gate.CI_SCENARIOS)
        self.assertIn("S7b", ci_gate.CI_SCENARIOS)
        self.assertNotIn("S13", ci_gate.CI_SCENARIOS)

    def test_rerun_keeps_passed_and_expected_skips_only(self):
        evidence = {"results": [result("S1", "pass"), result("S2", "fail"), result("S6", "skipped"),
                                result("S7b", "skipped"), {**result("S3", "pass"), "mode": "force"}]}
        scenarios = ["S1", "S2", "S3", "S6", "S7b", "S8"]
        self.assertEqual(["S2", "S3", "S6", "S8"], ci_gate.rerun_scenarios(evidence, scenarios, {"S7b"}))
        self.assertEqual(scenarios, ci_gate.rerun_scenarios(None, scenarios, {"S7b"}))


class VerdictTest(unittest.TestCase):
    def test_unexpected_skips_failures_and_missing_reports_are_problems(self):
        evidence = {"results": [result("S1", "pass"), result("S6", "skipped", ["the host has no overlay permission"]),
                                result("S7b", "skipped"), result("S2", "fail", ["retained runtimes 20/20"])],
                    "failures": [{"scenario": "S5", "mode": "normal", "outcome": "crashed"}]}
        passed, skipped, problems = ci_gate.verdict(evidence, ["S1", "S2", "S5", "S6", "S7b", "S8"], {"S7b"})
        self.assertEqual(["S1"], passed)
        self.assertEqual(["S7b"], skipped)
        self.assertEqual(["S2 fail: retained runtimes 20/20", "S5 no report (instrumentation crashed)",
                          "S6 skipped unexpectedly: the host has no overlay permission", "S8 no report (not run)"], problems)

    def test_evidence_line_of_run_gate(self):
        line = "Totals: {'pass': 17, 'skipped': 1}, no report 0; evidence docs\\memory\\evidence\\gate-sdk-5835-ci-20261022.md/.json\n"
        self.assertEqual("docs\\memory\\evidence\\gate-sdk-5835-ci-20261022", ci_gate.EVIDENCE_LINE.search(line)[1])


if __name__ == "__main__":
    unittest.main()
