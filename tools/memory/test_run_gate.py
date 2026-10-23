"""Host-side checks for run_gate.py parsing and report rendering; no adb calls.

py -3.12 -I -m unittest discover -s tools/memory -p "test_*.py"
"""
import json
import unittest

import run_gate


def report(scenario="S2", mode="normal", status="fail", retained=20):
    return {"scenario": scenario, "mode": mode, "status": status, "title": "threads", "iterations": 20,
            "reasons": ["retained runtimes 20/20"] if retained else [],
            "metrics": {"retained": retained, "javaHeapDeltaBytes": 3 * 1048576, "nativeHeapDeltaBytes": -1048576,
                        "scriptThreadDelta": 0, "rhinoContextDelta": 1, "engines": 0},
            "heap": {"dumpMs": 900, "analysis": {"exitedRuntimeInstances": 22, "analysisMs": 4000, "leaks": [
                {"runtimes": 22, "gcRoot": "STICKY_CLASS", "summary": "LooperHelper.sLoopers -> ScriptRuntime",
                 "path": ["org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)", "-> org.autojs.autojs.runtime.ScriptRuntime"]}]}}}


class ParseTest(unittest.TestCase):
    def test_status_lines_are_parsed_and_junk_is_ignored(self):
        text = "\n".join([
            "INSTRUMENTATION_STATUS: class=x",
            "INSTRUMENTATION_STATUS: aj6mem_json=" + json.dumps(report()),
            "INSTRUMENTATION_STATUS: aj6mem_json={broken",
            "INSTRUMENTATION_STATUS_CODE: 2",
            "INSTRUMENTATION_STATUS_CODE: -2",
        ])
        parsed = run_gate.parse_reports(text)
        self.assertEqual(1, len(parsed))
        self.assertEqual("S2", parsed[0]["scenario"])
        self.assertEqual("fail", run_gate.test_outcome(text))

    def test_outcomes(self):
        self.assertEqual("pass", run_gate.test_outcome("INSTRUMENTATION_STATUS_CODE: 1\nINSTRUMENTATION_STATUS_CODE: 0\n"))
        self.assertEqual("skipped", run_gate.test_outcome("INSTRUMENTATION_STATUS_CODE: -4\n"))
        self.assertEqual("crashed", run_gate.test_outcome("INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n"))

    def test_logcat_chunks_are_reassembled_only_when_complete(self):
        text = json.dumps(report())
        half = len(text) // 2
        lines = [f"AJ6MEM: JSON run-1 S2-normal 1/2 {text[:half]}", f"AJ6MEM: JSON run-1 S2-normal 2/2 {text[half:]}",
                 f"AJ6MEM: JSON run-1 S3-normal 1/2 {text[:half]}", f"AJ6MEM: JSON other S2-force 1/1 {text}"]
        parsed = run_gate.parse_logcat_reports("\n".join(lines), "run-1")
        self.assertEqual(["S2"], [r["scenario"] for r in parsed])

    def test_scenario_list(self):
        self.assertEqual(["S1", "S4a"], run_gate.scenario_list("s1, S4A,S1"))
        with self.assertRaises(Exception):
            run_gate.scenario_list("S99")


class MergeTest(unittest.TestCase):
    def test_rerun_pairs_replace_earlier_results_and_failures(self):
        previous = {"runId": "old", "started": "t0", "finished": "t1", "deviceState": {"powered": ["USB"]},
                    "results": [report("S1", "normal", "fail", 1), report("S2", "normal")],
                    "failures": [{"scenario": "S6", "mode": "normal", "outcome": "crashed"}]}
        meta = {"runId": "new", "started": "t2", "finished": "t3"}
        merged, failures, merged_meta = run_gate.merge_evidence(
            previous, [report("S6", "normal", "pass", 0), report("S1", "normal", "pass", 0)], [], meta)
        self.assertEqual([("S1", "pass"), ("S2", "fail"), ("S6", "pass")], [(r["scenario"], r["status"]) for r in merged])
        self.assertEqual([], failures)
        self.assertEqual("t0", merged_meta["started"])
        self.assertEqual(["old", "new"], [r["runId"] for r in merged_meta["mergedRuns"]])


class RenderTest(unittest.TestCase):
    def test_cell_and_markdown(self):
        self.assertEqual("20 / +3.00 / -1.00 / +0 / +1 / 0", run_gate.cell(report()))
        self.assertEqual("skipped", run_gate.cell({"status": "skipped"}))
        meta = {"device": {"model": "XQ-AT72", "manufacturer": "Sony", "sdk": "31", "serial": "X"}, "versionCode": 5700,
                "versionName": "6.8.0", "package": "org.autojs.autojs6", "runId": "r", "label": "baseline",
                "started": "a", "finished": "b", "date": "20261020", "deviceState": {}, "warmups": 2, "iterations": 20,
                "pollMs": 10000, "forceDelayMs": 1000, "capture": "off"}
        text = run_gate.markdown(meta, [report(), report("S1", "force", "pass", 0)],
                                 [{"scenario": "S5", "mode": "normal", "outcome": "timeout"}])
        self.assertIn("| S2 threads | N | fail | 20 / +3.00 / -1.00 / +0 / +1 / 0 |", text)
        self.assertIn("LooperHelper.sLoopers (static)", text)
        self.assertIn("| S5 | N | no report |", text)

    def test_native_detail(self):
        detailed = report("S4f", "normal", "pass", 0)
        detailed["metrics"]["native"] = {
            "nativePeakMaxBytes": 2 * 1048576, "imagesLiveDelta": 0, "openMediaImages": 0, "matsLiveDelta": 1,
            "meminfoDeltaKb": {"nativePssKb": 1024, "nativeHeapAllocKb": -512, "graphicsKb": 0, "unknownPssKb": 256},
            "instanceDeltas": {"bitmap": 2, "cvMat": None, "accessibilityNode": 0},
            "scriptStatsMax": {"frames": 200.0, "slowestCaptureMs": 812.0}}
        self.assertEqual("+2.00 | +1.00/-0.50/+0.00/+0.25 | +0 / 0 / +1 | +2/-/+0", run_gate.native_cell(detailed))
        self.assertEqual("frames 200, slowestCaptureMs 812", run_gate.stats_text(detailed))
        self.assertEqual("-", run_gate.native_cell(report()))
        self.assertIn("peak=+2.00MB images=+0 media=0 mats=+1", run_gate.summary_line(detailed))

    def test_classes_cell(self):
        judged = report("S8", "normal", "pass", 0)
        judged["metrics"]["native"] = {"perRunCounterGrowth": {"loadedClasses": {"median": 0, "max": 61, "runsGrowing": 2}},
                                       "instanceDeltasFromBaseline": {"javaClass": 62, "dexClassLoader": 0, "webView": None}}
        judged["nativeBaseline"] = {"loadedClasses": 100, "hostDexLoaders": 2}
        judged["nativeFinal"] = {"loadedClasses": 162, "hostDexLoaders": 2}
        self.assertEqual("+62 (median +0 per run) / +62 / +0 / +0 / -", run_gate.classes_cell(judged))
        # -1 means unavailable (VMDebug or a host before P4.4).
        plain = report("S1", "normal", "pass", 0)
        plain["metrics"]["native"] = {"instanceDeltasFromBaseline": {}}
        plain["nativeBaseline"] = {"loadedClasses": 100, "hostDexLoaders": -1}
        plain["nativeFinal"] = {"loadedClasses": 101, "hostDexLoaders": -1}
        self.assertEqual("+1 / - / - / - / -", run_gate.classes_cell(plain))
        self.assertEqual("-", run_gate.classes_cell(report()))


def bridge_report(scenario="ND1", mode="normal", status="pass", retained=0):
    result = report(scenario, mode, status, retained)
    result["suite"] = "bridge-gate"
    result["metrics"].update({
        "javaHeapDeltaBytes": 524288, "nativeHeapDeltaBytes": 1048576, "rhinoContextDelta": 0, "bridgeThreadDelta": 0,
        "bridgeRegistriesDelta": {"NodeJsRuntimePluginHost.connectionPool": 1},
        "bindings": {"start": {"serviceConnections": 3, "pluginConnections": 0},
                     "final": {"serviceConnections": 4, "pluginConnections": 1}, "pluginConnectionsDelta": 1},
        "bridgeThreadsNew": [],
        "pluginPss": {"start": {"totalPssKb": 40960}, "afterSecondRun": {"totalPssKb": 81408},
                      "final": {"totalPssKb": 81920}, "deltaKb": 512}})
    result["samples"] = [{"stats": {"pngBytes": 120.0}}, {"stats": {"pngBytes": 130.0, "deviceInfoKeys": 9.0}}]
    if retained:
        result["heap"] = {"dumpMs": 700, "analysis": {"instances": {"NodeJsHostCapabilityBroker": 2, "ScriptRuntime": 0},
                                                      "analysisMs": 3000, "leaks": [
            {"runtimes": 2, "gcRoot": "JNI_GLOBAL", "summary": "Binder.mOwner -> NodeJsHostCapabilityBroker",
             "path": ["android.os.Binder.mOwner", "-> org.autojs.autojs.engine.NodeJsHostCapabilityBroker"]}]}}
    return result


class FakeDevice:
    def __init__(self, outputs=None):
        self.commands, self.outputs = [], outputs or {}

    def shell(self, *args, timeout=30):
        self.commands.append(args)
        return {"exitCode": 0, "stdout": self.outputs.get(args[:2], ""), "stderr": ""}


class BridgeSuiteTest(unittest.TestCase):
    def test_scenarios_resolve_per_suite(self):
        self.assertEqual(["ND7a", "ND1"], run_gate.scenario_list("nd7A, ND1", run_gate.BRIDGE_SCENARIOS))
        self.assertEqual(list(run_gate.BRIDGE_SCENARIOS), run_gate.scenario_list(None, run_gate.BRIDGE_SCENARIOS))
        with self.assertRaises(Exception):
            run_gate.scenario_list("S1", run_gate.BRIDGE_SCENARIOS)
        self.assertEqual(list(run_gate.SCENARIOS), run_gate.scenario_list(""))

    def test_bridge_methods_exist_in_the_device_test(self):
        source = (run_gate.ROOT / "app" / "src" / "androidTest" / "java" / "org" / "autojs" / "autojs" / "memory" / "bridge"
                  / "BridgeLeakGateDeviceTest.kt").read_text(encoding="utf-8")
        for scenario, method in run_gate.BRIDGE_SCENARIOS.items():
            self.assertIn(f"fun {method}() = gate(\"{scenario}\")", source)
        bridge = run_gate.SUITES["bridge"]
        self.assertTrue(set(bridge["grants"]) | bridge["capture"] <= set(run_gate.BRIDGE_SCENARIOS))
        self.assertEqual("docs/memory-phase2/evidence", bridge["out"].relative_to(run_gate.ROOT).as_posix())
        self.assertEqual("docs/memory/evidence", run_gate.SUITES["runtime"]["out"].relative_to(run_gate.ROOT).as_posix())

    def test_bridge_cell_and_summary(self):
        self.assertEqual("0 / +0.50 / +1.00 / +0 b+0 / h+1 s+1 / 0 / 80.0 (+0.50)", run_gate.cell(bridge_report()))
        unavailable = bridge_report()
        unavailable["metrics"]["bindings"] = {"start": {"unavailable": "NoSuchFieldException"}, "final": {}, "pluginConnectionsDelta": None}
        unavailable["metrics"]["pluginPss"] = {"start": None, "afterSecondRun": None, "final": None, "deltaKb": None}
        self.assertEqual("0 / +0.50 / +1.00 / +0 b+0 / h+1 s n/a / 0 / -", run_gate.cell(unavailable))
        self.assertIn("bindings=h+1 s+1 bridgeThreads=+0 pss=80.0 (+0.50)", run_gate.summary_line(bridge_report()))
        self.assertEqual("connectionPool +1 | 0/3 -> 1/4 | - | 40.0 / 79.5 / 80.0", run_gate.bridge_cell(bridge_report()))
        self.assertEqual("pngBytes 130, deviceInfoKeys 9", run_gate.bridge_stats(bridge_report()))
        # The first gate's cell does not change.
        self.assertEqual("20 / +3.00 / -1.00 / +0 / +1 / 0", run_gate.cell(report()))

    def test_bridge_markdown(self):
        meta = {"suite": "bridge-gate", "device": {"model": "23046RP50C", "manufacturer": "Xiaomi", "sdk": "34", "serial": "X"},
                "versionCode": 5935, "versionName": "6.8.0", "package": "org.autojs.autojs6", "runId": "r", "label": "mp2-baseline",
                "started": "a", "finished": "b", "date": "20261022", "deviceState": {}, "warmups": 2, "iterations": 20,
                "pollMs": 10000, "forceDelayMs": 1000, "capture": "off",
                "plugin": {"package": run_gate.NODE_PLUGIN, "versionCode": 292, "versionName": "1.32.0"}, "pluginCommit": "6d4e6bdb",
                "hiddenApiPolicy": {"before": None, "restoredTo": None},
                "grants": [{"scenario": "ND12", "permission": "android.permission.RECORD_AUDIO", "revoked": True}]}
        ended = bridge_report("ND4", "force")
        ended["runsEndedByError"] = 20
        text = run_gate.markdown(meta, [bridge_report(), ended, bridge_report("ND2", "normal", "fail", 2)], [])
        self.assertIn("# Bridge gate 23046RP50C (5935) 20261022", text)
        self.assertIn(f"- Plugin: {run_gate.NODE_PLUGIN} versionCode 292 (1.32.0), commit 6d4e6bdb", text)
        self.assertIn("before `None`, afterwards `None`", text)
        self.assertIn("ND12 RECORD_AUDIO (revoked)", text)
        self.assertIn("| ND1 | N | connectionPool +1 | 0/3 -> 1/4 | - | 40.0 / 79.5 / 80.0 | pngBytes 130, deviceInfoKeys 9 |", text)
        self.assertIn("deviceInfoKeys 9; ended by error 20 |", text)
        self.assertIn("### ND2 N: NodeJsHostCapabilityBroker 2, ScriptRuntime 0 in the dump", text)
        self.assertIn("2 object(s), root JNI_GLOBAL", text)
        self.assertNotIn("Rhino contexts / live engines (ROADMAP appendix A)", text)
        self.assertNotIn("Heap right after the last run", text)
        dumped = bridge_report()
        dumped["heapAfterLast"] = {"dumpMs": 900, "hprof": "/x/r-ND1-normal-last.hprof", "hprofKept": False,
                                   "analysis": {"instances": {"NodeJsHostCapabilityBroker": 0, "ScriptRuntime": 0}, "leaks": []},
                                   "histogram": {"totalBytes": 3 * 1048576,
                                                 "top": [{"class": "byte[]", "count": 12, "bytes": 1048576}]}}
        text = run_gate.markdown(meta, [dumped], [])
        self.assertIn("### ND1 N: NodeJsHostCapabilityBroker 0, ScriptRuntime 0, 3.00 MB in the dump", text)
        self.assertIn("- byte[]: 12 objects, 1.00 MB", text)

    def test_bridge_markdown_lists_every_plugin_and_the_session_counts(self):
        meta = {"suite": "bridge-gate", "device": {"model": "23046RP50C"}, "versionCode": 5935, "package": "org.autojs.autojs6",
                "runId": "r", "started": "a", "finished": "b", "date": "20261023", "warmups": 2, "iterations": 20, "pollMs": 10000,
                "forceDelayMs": 1000, "capture": "off",
                "plugins": [{"engine": "bun", "package": run_gate.BUN_PLUGIN, "versionCode": 296, "versionName": "0.21.0", "commit": "fb879f3"},
                            {"engine": "quickjs", "package": run_gate.QUICKJS_PLUGIN, "versionCode": None, "versionName": None, "commit": None}]}
        bun = bridge_report("BN2")
        bun["metrics"]["sessionInstances"] = {"start": {"bun.broker": 0, "bun.callback": 0}, "final": {"bun.broker": 2, "bun.callback": 0},
                                              "retained": {"bun.broker": 2}, "unavailable": []}
        bun["samples"][0]["sessionClearedMs"] = 400
        bun["samples"][1]["sessionClearedMs"] = None
        unavailable = bridge_report("QJ1")
        unavailable["metrics"]["sessionInstances"] = {"start": {"quickjs.callback": -1}, "final": {"quickjs.callback": -1}, "retained": {},
                                                      "unavailable": ["quickjs.callback"]}
        text = run_gate.markdown(meta, [bun, unavailable, bridge_report("QJ2")], [])
        self.assertIn(f"- Plugin: {run_gate.BUN_PLUGIN} versionCode 296 (0.21.0), commit fb879f3", text)
        self.assertIn(f"- Plugin: {run_gate.QUICKJS_PLUGIN} not installed, commit -", text)
        self.assertIn("| BN2 | N | connectionPool +1 | 0/3 -> 1/4 | - | 40.0 / 79.5 / 80.0 | "
                      "pngBytes 130, deviceInfoKeys 9; session broker +2, cleared <= 400 ms |", text)
        self.assertIn("deviceInfoKeys 9; session n/a |", text)
        self.assertIn("| QJ2 | N | connectionPool +1 | 0/3 -> 1/4 | - | 40.0 / 79.5 / 80.0 | pngBytes 130, deviceInfoKeys 9 |", text)

    def test_bridge_engines_and_plugin_commits(self):
        self.assertEqual(["node", "bun", "quickjs", "lua", "python", None],
                         [run_gate.bridge_engine(s) for s in ("ND7a", "BN1", "QJ5", "LU2", "PY5", "S1")])
        self.assertEqual({"lua": "db96216", "python": "5514e82"}, run_gate.plugin_commits("lua=db96216,python=5514e82", ["lua"]))
        self.assertEqual({"bun": "fb879f3", "quickjs": "fb879f3"}, run_gate.plugin_commits("fb879f3", ["bun", "quickjs"]))
        self.assertEqual({"bun": "fb879f3", "quickjs": "0bcee52-dirty"},
                         run_gate.plugin_commits("bun=fb879f3, quickjs=0bcee52-dirty", ["bun"]))
        self.assertEqual({}, run_gate.plugin_commits(None, ["node"]))
        for bad in ("main", "deno=fb879f3", "bun=", "bun=XYZ1"):
            with self.assertRaises(ValueError):
                run_gate.plugin_commits(bad, ["bun"])
        self.assertEqual(set(run_gate.BRIDGE_ENGINES.values()), set(run_gate.BRIDGE_PLUGINS))
        self.assertTrue(all(run_gate.bridge_engine(s) for s in run_gate.BRIDGE_SCENARIOS))

    def test_bridge_merge_keeps_the_plugins_of_earlier_runs(self):
        old_node = {"package": run_gate.NODE_PLUGIN, "versionCode": 292, "versionName": "1.32.0"}
        previous = {"runId": "old", "started": "t0", "finished": "t1", "results": [bridge_report("ND1")],
                    "plugin": old_node, "pluginCommit": "6d4e6bdb"}
        quickjs = {"engine": "quickjs", "package": run_gate.QUICKJS_PLUGIN, "versionCode": 42, "versionName": "1.0.4", "commit": "0bcee52"}
        _, _, meta = run_gate.merge_evidence(previous, [bridge_report("QJ1")], [],
                                             {"runId": "new", "started": "t2", "finished": "t3", "plugins": [quickjs]},
                                             run_gate.BRIDGE_SCENARIOS)
        self.assertEqual([{"engine": "node", **old_node, "commit": "6d4e6bdb"}, quickjs], meta["plugins"])
        bun = {"engine": "bun", "package": run_gate.BUN_PLUGIN, "versionCode": 297, "versionName": "0.21.0", "commit": "abc1234"}
        _, _, again = run_gate.merge_evidence({**previous, "plugins": meta["plugins"]}, [bridge_report("BN1")], [],
                                              {"runId": "next", "started": "t4", "finished": "t5", "plugins": [bun, {**quickjs, "versionCode": 43}]},
                                              run_gate.BRIDGE_SCENARIOS)
        self.assertEqual([run_gate.NODE_PLUGIN, run_gate.BUN_PLUGIN, run_gate.QUICKJS_PLUGIN], [p["package"] for p in again["plugins"]])
        self.assertEqual(43, again["plugins"][2]["versionCode"])

    def test_bridge_merge_keeps_suite_order(self):
        previous = {"runId": "old", "started": "t0", "finished": "t1", "results": [bridge_report("ND10")],
                    "grants": [{"scenario": "ND12", "permission": "android.permission.RECORD_AUDIO", "revoked": True}]}
        merged, _, meta = run_gate.merge_evidence(previous, [bridge_report("ND7a")], [],
                                                  {"runId": "new", "started": "t2", "finished": "t3"}, run_gate.BRIDGE_SCENARIOS)
        self.assertEqual(["ND7a", "ND10"], [r["scenario"] for r in merged])
        self.assertEqual(["ND7a", "ND10"], meta["scenarios"])
        self.assertEqual(1, len(meta["grants"]))

    def test_device_helpers(self):
        dump = ("    runtime permissions:\n      android.permission.CAMERA: granted=false, flags=[ USER_SET ]\n"
                "      android.permission.RECORD_AUDIO: granted=true, flags=[ USER_SET ]\n")
        device = FakeDevice({("dumpsys", "package"): dump, ("settings", "get"): "null\n"})
        self.assertEqual({"android.permission.RECORD_AUDIO"}, run_gate.granted_permissions(device, "org.autojs.autojs6"))
        self.assertIsNone(run_gate.global_setting(device, "hidden_api_policy"))
        run_gate.put_global_setting(device, "hidden_api_policy", "1")
        run_gate.put_global_setting(device, "hidden_api_policy", None)
        self.assertEqual([("settings", "put", "global", "hidden_api_policy", "1"), ("settings", "delete", "global", "hidden_api_policy")],
                         device.commands[-2:])


if __name__ == "__main__":
    unittest.main()
