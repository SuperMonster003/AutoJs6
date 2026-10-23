"""Host-side checks for ci_gate.py APK selection, rerun selection, verdict and the bridge suite of memory-gate.yml;
no adb calls.

py -3.12 -I -m unittest discover -s tools/memory -p "test_*.py"
"""
import re
import unittest

import ci_gate
import run_gate

WORKFLOW = ci_gate.ROOT / ".github" / "workflows" / "memory-gate.yml"
COLOROS_ONLY = {"ND13", "BN5", "QJ5", "LU5", "PY5"}
# needed_plugins() name -> prefix of the workflow's *_PLUGIN_TAG / *_PLUGIN_SHA256 pair.
WORKFLOW_PLUGIN_ENV = {"node": "NODEJS", "bun": "BUN", "quickjs": "QUICKJS", "lua": "LUA", "python": "PYTHON",
                       "block-compose": "BLOCK_COMPOSE"}


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


class BridgeSuiteTest(unittest.TestCase):
    def test_bridge_runs_every_scenario_and_allows_coloros_and_nd6_skips(self):
        self.assertEqual(list(run_gate.BRIDGE_SCENARIOS), ci_gate.BRIDGE_CI_SCENARIOS)
        self.assertEqual("ND1", ci_gate.BRIDGE_CI_SCENARIOS[0])
        for scenario in ("LU1", "PY5", "QJ3"):
            self.assertIn(scenario, ci_gate.BRIDGE_CI_SCENARIOS)
        self.assertEqual(COLOROS_ONLY | {"ND6"}, set(ci_gate.BRIDGE_UNAVOIDABLE_SKIPS))
        self.assertLessEqual(set(ci_gate.BRIDGE_UNAVOIDABLE_SKIPS), set(run_gate.BRIDGE_SCENARIOS))
        self.assertEqual("mp2-ci", ci_gate.SUITES["bridge"]["label"])
        self.assertEqual("ci", ci_gate.SUITES["runtime"]["label"])

    def test_needed_plugins_follow_the_scenarios_that_must_pass(self):
        everything = ci_gate.needed_plugins(ci_gate.BRIDGE_CI_SCENARIOS, COLOROS_ONLY)
        self.assertEqual({"node", "bun", "quickjs", "lua", "python", "block-compose"}, set(everything))
        self.assertEqual(ci_gate.BLOCK_COMPOSE_PLUGIN, everything["block-compose"])
        self.assertEqual(run_gate.BRIDGE_PLUGINS["lua"], everything["lua"])
        self.assertNotIn("block-compose", ci_gate.needed_plugins(ci_gate.BRIDGE_CI_SCENARIOS, COLOROS_ONLY | {"ND6"}))
        self.assertEqual({}, ci_gate.needed_plugins(["LU5"], {"LU5"}))
        self.assertEqual({}, ci_gate.needed_plugins(ci_gate.CI_SCENARIOS, set()))
        self.assertEqual({"python"}, set(ci_gate.needed_plugins(["PY1", "LU1"], {"LU1"})))


class WorkflowTest(unittest.TestCase):
    """memory-gate.yml pins a release of every plugin the bridge suite needs and allows only the skips of ci_gate.py."""

    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text(encoding="utf-8")

    def test_every_needed_plugin_has_a_pinned_release_and_a_fetch(self):
        for name in ci_gate.needed_plugins(ci_gate.BRIDGE_CI_SCENARIOS, COLOROS_ONLY):
            prefix = WORKFLOW_PLUGIN_ENV[name]
            self.assertRegex(self.text, rf"\n\s+{prefix}_PLUGIN_TAG: v\d+\.\d+\.\d+\S*\n", name)
            self.assertRegex(self.text, rf"\n\s+{prefix}_PLUGIN_SHA256: [0-9a-f]{{64}}\n", name)
            self.assertRegex(self.text, rf"\n\s+fetch AutoJs6-Plugin-\S+ \S+ \"\${prefix}_PLUGIN_TAG\" \"\${prefix}_PLUGIN_SHA256\"", name)

    def test_allowed_skips_of_both_suites(self):
        match = re.search(r"GATE_ALLOW_SKIP: \$\{\{ inputs\.suite == 'bridge' && '([^']*)' \|\| '([^']*)' }}", self.text)
        self.assertIsNotNone(match)
        bridge, runtime = (set(group.split(",")) for group in match.groups())
        self.assertEqual(COLOROS_ONLY, bridge)
        self.assertLessEqual(bridge, set(ci_gate.BRIDGE_UNAVOIDABLE_SKIPS))
        self.assertLessEqual(runtime, set(ci_gate.UNAVOIDABLE_SKIPS))
        self.assertIn('--suite "$GATE_SUITE"', self.text)
        self.assertIn('--allow-skip "$GATE_ALLOW_SKIP"', self.text)


if __name__ == "__main__":
    unittest.main()
