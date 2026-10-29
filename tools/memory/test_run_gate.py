"""Host-side checks for run_gate.py parsing and report rendering; no adb calls.

py -3.12 -I -m unittest discover -s tools/memory -p "test_*.py"
"""
import contextlib
import io
import json
from pathlib import Path
import tempfile
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


class AttachTest(unittest.TestCase):
    """run_gate.py --attach: the report from the files tools/memory/device_gate.sh leaves on the device (Q51 (b))."""
    RUN = "SM-A566B-5935-20261029231000"

    def write(self, folder, name, text):
        (folder / f"{self.RUN}-{name}").write_text(text, encoding="utf-8")

    def tagged(self, scenario, mode, status="pass", retained=0, **extra):
        # S4f carries its own counts, as on the device (run_gate.SCENARIO_PARAMS).
        result = {**report(scenario, mode, status, retained), "runId": self.RUN, "warmups": 2, "pollMs": 10000,
                  **run_gate.SCENARIO_PARAMS.get(scenario, {}), **extra}
        return result

    def attach(self, folder, pairs, expected=None):
        with contextlib.redirect_stdout(io.StringIO()) as out:
            attached = run_gate.attach_reports(folder, self.RUN, pairs, ("S4a", "S4f"),
                                               expected if expected is not None else {"warmups": 2, "iterations": 20, "pollMs": 10000})
        return (*attached, out.getvalue())

    def test_pick_reports_falls_back_from_status_lines_to_json_to_logcat_in_pair_order(self):
        pairs = [("S4a", "normal"), ("S4a", "force"), ("S4f", "normal"), ("S4f", "force")]
        text = "INSTRUMENTATION_STATUS: aj6mem_json=" + json.dumps(report("S4f", "normal", "pass", 0))
        chunk = json.dumps(report("S4a", "force", "pass", 0))
        other = json.dumps(report("S4f", "force", "pass", 0))
        logcat = f"I/AJ6MEM( 7): JSON run S4a-force 1/1 {chunk}\nI/AJ6MEM( 7): JSON other S4f-force 1/1 {other}\n"
        asked = []

        def json_text(s, m):
            asked.append((s, m))
            return json.dumps(report("S4a", "normal", "fail", 2)) if (s, m) == ("S4a", "normal") else None

        found = run_gate.pick_reports(text, pairs, "run", json_text, lambda: logcat)
        self.assertEqual([("S4a", "normal", "fail"), ("S4a", "force", "pass"), ("S4f", "normal", "pass")],
                         [(r["scenario"], r["mode"], r["status"]) for r in found])
        # The JSON is asked only for the pairs the status lines did not cover.
        self.assertEqual([("S4a", "normal"), ("S4a", "force"), ("S4f", "force")], asked)

    def test_attach_reads_status_lines_json_and_logcat_and_keeps_the_pair_without_a_report(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            self.write(folder, "S4a-normal.log", "\n".join([
                f"start 2026-10-29T23:10:00 {self.RUN} S4a s04aCaptureScreen normal capture=on tz=Asia/Shanghai",
                "projection_before=No operations.",
                "INSTRUMENTATION_STATUS: aj6mem_json=" + json.dumps(self.tagged("S4a", "normal")),
                "INSTRUMENTATION_STATUS_CODE: 0", "rc=0", "projection_after=PROJECT_MEDIA: default; time=+1s ago",
                "end 2026-10-29T23:15:00", ""]))
            # S4a force: the status line was lost, the test's own JSON file has the report.
            self.write(folder, "S4a-force.log", "start 2026-10-29T23:16:00 x\nINSTRUMENTATION_STATUS_CODE: 0\nrc=0\nend 2026-10-29T23:21:00\n")
            self.write(folder, "S4a-force.json", json.dumps(self.tagged("S4a", "force")))
            # S4f normal: only the logcat chunks are left.
            chunked = json.dumps(self.tagged("S4f", "normal", "fail", 3))
            half = len(chunked) // 2
            self.write(folder, "S4f-normal.log", "start 2026-10-29T23:22:00 x\nINSTRUMENTATION_STATUS_CODE: -2\nrc=0\nend 2026-10-29T23:27:00\n")
            self.write(folder, "S4f-normal.logcat", f"I/AJ6MEM( 9): JSON {self.RUN} S4f-normal 1/2 {chunked[:half]}\n"
                                                    f"I/AJ6MEM( 9): JSON {self.RUN} S4f-normal 2/2 {chunked[half:]}\n")
            # S4f force: the process crashed before any report.
            self.write(folder, "S4f-force.log", "start 2026-10-29T23:28:00 x\nINSTRUMENTATION_RESULT: shortMsg=Process crashed.\nrc=0\nend 2026-10-29T23:29:00\n")
            self.write(folder, "S4f-force.crash", "x" * 300 + "\nFatal signal 6 (SIGABRT)\nAbort message: 'boom'\n")
            pairs = [("S4a", "normal"), ("S4a", "force"), ("S4f", "normal"), ("S4f", "force")]
            reports, failures, projection, window, printed = self.attach(folder, pairs)
        self.assertEqual([("S4a", "normal", "pass", "pass"), ("S4a", "force", "pass", "pass"), ("S4f", "normal", "fail", "fail")],
                         [(r["scenario"], r["mode"], r["status"], r["instrumentation"]) for r in reports])
        self.assertEqual([("S4f", "force", "crashed")], [(f["scenario"], f["mode"], f["outcome"]) for f in failures])
        # 200 characters before the signal line: 199 x and the line break.
        self.assertTrue(failures[0]["crashLog"].startswith("x" * 199 + "\nFatal signal 6"))
        self.assertNotIn("x" * 200, failures[0]["crashLog"])
        self.assertEqual({"allowedDuring": ["S4a normal", "S4a force", "S4f normal", "S4f force"], "before": "default",
                          "restoredTo": "default"}, projection)
        self.assertEqual({"started": "2026-10-29T23:10:00", "finished": "2026-10-29T23:29:00"}, window)
        self.assertIn("S4f  F NO REPORT (instrumentation crashed)", printed)

    def test_attach_refuses_missing_or_unfinished_logs_other_runs_and_other_parameters(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            with self.assertRaisesRegex(RuntimeError, "No device-side log for S4a normal"):
                self.attach(folder, [("S4a", "normal")])
            self.write(folder, "S4a-normal.log", "start 2026-10-29T23:10:00 x\nINSTRUMENTATION_STATUS_CODE: 1\n")
            with self.assertRaisesRegex(RuntimeError, "has no rc= line"):
                self.attach(folder, [("S4a", "normal")])
            self.write(folder, "S4a-normal.log", "INSTRUMENTATION_STATUS: aj6mem_json="
                       + json.dumps(self.tagged("S4a", "normal", runId="SM-A566B-5935-other")) + "\nINSTRUMENTATION_STATUS_CODE: 0\nrc=0\n")
            with self.assertRaisesRegex(RuntimeError, "a report of run SM-A566B-5935-other"):
                self.attach(folder, [("S4a", "normal")])
            self.write(folder, "S4a-normal.log", "INSTRUMENTATION_STATUS: aj6mem_json="
                       + json.dumps(self.tagged("S4a", "normal", iterations=10)) + "\nINSTRUMENTATION_STATUS_CODE: 0\nrc=0\n")
            with self.assertRaisesRegex(RuntimeError, "used iterations 10, this call expects 20"):
                self.attach(folder, [("S4a", "normal")])
            # The same files pass once the call expects what the device-side run used.
            reports, failures, projection, window, _ = self.attach(folder, [("S4a", "normal")], {"iterations": 10})
            self.assertEqual(1, len(reports))
            self.assertEqual([], failures)
            self.assertEqual({}, window)
            # S4f fixes warmups 1 / iterations 3 itself (GateScenarios.kt): its report is checked against those, not
            # the call's 2 / 20, and a run with the call's counts would be from another test build.
            self.write(folder, "S4f-force.log", "INSTRUMENTATION_STATUS: aj6mem_json="
                       + json.dumps(self.tagged("S4f", "force", warmups=1, iterations=3)) + "\nINSTRUMENTATION_STATUS_CODE: 0\nrc=0\n")
            reports, failures, _, _, _ = self.attach(folder, [("S4f", "force")])
            self.assertEqual([("S4f", "force")], [(r["scenario"], r["mode"]) for r in reports])
            self.write(folder, "S4f-force.log", "INSTRUMENTATION_STATUS: aj6mem_json="
                       + json.dumps(self.tagged("S4f", "force", warmups=2, iterations=20)) + "\nINSTRUMENTATION_STATUS_CODE: 0\nrc=0\n")
            with self.assertRaisesRegex(RuntimeError, "used warmups 2, this call expects 1"):
                self.attach(folder, [("S4f", "force")])

    def test_attached_report_header(self):
        meta = {"device": {"model": "SM-A566B", "manufacturer": "samsung", "sdk": "36", "serial": "localhost:57135"}, "versionCode": 5935,
                "versionName": "6.8.0", "package": "org.autojs.autojs6", "runId": self.RUN, "label": "p6g",
                "started": "2026-10-29T23:10:00", "finished": "2026-10-29T23:29:00", "date": "20261029", "deviceState": {}, "warmups": 2,
                "iterations": 20, "pollMs": 10000, "forceDelayMs": 1000, "capture": "on",
                "attached": {"dir": "attach-p6g", "deviceScript": "tools/memory/device_gate.sh"},
                "projectionAppOp": {"allowedDuring": ["S4a normal"], "before": "default", "restoredTo": "default"}}
        text = run_gate.markdown(meta, [report("S4a", "normal", "pass", 0)], [])
        self.assertIn("- Attached (ROADMAP appendix C Q51 (b)): a device-side run of tools/memory/device_gate.sh, collected from attach-p6g; "
                      "the run times are the device clock", text)
        self.assertIn("`allow` during S4a normal, before `default`, afterwards `default`", text)

    def runner_lines(self, *middle):
        return "\n".join(["I/TestRunner(24001): run started: 1 tests",
                          "I/TestRunner(24001): started: s04aCaptureScreen(org.autojs.autojs.memory.RuntimeLeakGateDeviceTest)",
                          *middle, ""])

    def test_detached_outcome_from_the_testrunner_lines(self):
        passed = self.runner_lines("I/TestRunner(24001): finished: s04aCaptureScreen(org.autojs.autojs.memory.RuntimeLeakGateDeviceTest)",
                                   "I/TestRunner(24001): run finished: 1 tests, 0 failed, 0 ignored")
        self.assertEqual("pass", run_gate.detached_outcome(passed))
        failed = self.runner_lines("E/TestRunner(24001): failed: s04aCaptureScreen(org.autojs.autojs.memory.RuntimeLeakGateDeviceTest)",
                                   "E/TestRunner(24001): ----- begin exception -----",
                                   "I/TestRunner(24001): finished: s04aCaptureScreen(org.autojs.autojs.memory.RuntimeLeakGateDeviceTest)",
                                   "I/TestRunner(24001): run finished: 1 tests, 1 failed, 0 ignored")
        self.assertEqual("fail", run_gate.detached_outcome(failed))
        skipped = self.runner_lines("I/TestRunner(24001): ignored: s04aCaptureScreen(org.autojs.autojs.memory.RuntimeLeakGateDeviceTest)",
                                    "I/TestRunner(24001): run finished: 1 tests, 0 failed, 1 ignored")
        self.assertEqual("skipped", run_gate.detached_outcome(skipped))
        # Started but never finished (the process died, or the logcat was taken too early): unknown, never pass.
        self.assertEqual("unknown", run_gate.detached_outcome(self.runner_lines()))
        self.assertEqual("unknown", run_gate.detached_outcome(""))
        # A `run finished` line of another test count or without the per-test `finished:` line is not a pass either.
        self.assertEqual("unknown", run_gate.detached_outcome(self.runner_lines("I/TestRunner(24001): run finished: 1 tests, 0 failed, 0 ignored")))
        self.assertEqual("unknown", run_gate.detached_outcome(self.runner_lines(
            "I/TestRunner(24001): finished: s04aCaptureScreen(org.autojs.autojs.memory.RuntimeLeakGateDeviceTest)",
            "I/TestRunner(24001): run finished: 2 tests, 0 failed, 0 ignored")))

    def test_attach_detached_logs_need_the_end_line_and_take_the_outcome_from_the_logcat(self):
        start = f"start 2026-10-29T23:22:20 {self.RUN} S4a s04aCaptureScreen normal capture=on host-clock launch=detached"
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            self.write(folder, "S4a-normal.log", "\n".join([start, "device_time=2026-10-30T00:22:22 tz=Asia/Seoul",
                                                            "projection_before=No operations.", "projection_set=PROJECT_MEDIA: allow",
                                                            "am_rc=0", "launched 2026-10-29T23:22:43", ""]))
            with self.assertRaisesRegex(RuntimeError, "has no end line: detached_gate.sh collect has not run for S4a normal"):
                self.attach(folder, [("S4a", "normal")])
            self.write(folder, "S4a-normal.log", "\n".join([start, "am_rc=0", "launched 2026-10-29T23:22:43", "json_pulled=4321",
                                                            "projection_after=PROJECT_MEDIA: default; time=+1s ago",
                                                            "end 2026-10-29T23:29:10", ""]))
            self.write(folder, "S4a-normal.json", json.dumps(self.tagged("S4a", "normal")))
            self.write(folder, "S4a-normal.logcat", self.runner_lines(
                "I/TestRunner(24001): finished: s04aCaptureScreen(org.autojs.autojs.memory.RuntimeLeakGateDeviceTest)",
                "I/TestRunner(24001): run finished: 1 tests, 0 failed, 0 ignored"))
            # S4a force: collected after a timeout, the runner never finished and the crash buffer has the reason.
            self.write(folder, "S4a-force.log", "\n".join([start.replace("normal", "force"), "am_rc=0", "end 2026-10-29T23:45:00", ""]))
            self.write(folder, "S4a-force.logcat", self.runner_lines())
            self.write(folder, "S4a-force.crash", "y" * 300 + "\nFATAL EXCEPTION: main\nProcess: org.autojs.autojs6, PID: 24001\n")
            reports, failures, projection, window, printed = self.attach(folder, [("S4a", "normal"), ("S4a", "force")])
        self.assertEqual([("S4a", "normal", "pass", "pass", "detached")],
                         [(r["scenario"], r["mode"], r["status"], r["instrumentation"], r["launch"]) for r in reports])
        self.assertEqual([("S4a", "force", "unknown")], [(f["scenario"], f["mode"], f["outcome"]) for f in failures])
        self.assertTrue(failures[0]["crashLog"].startswith("y" * 199 + "\nFATAL EXCEPTION: main"))
        self.assertEqual({"allowedDuring": ["S4a normal", "S4a force"], "before": "default", "restoredTo": "default"}, projection)
        self.assertEqual({"launch": "detached", "started": "2026-10-29T23:22:20", "finished": "2026-10-29T23:45:00"}, window)
        self.assertIn("S4a  F NO REPORT (instrumentation unknown)", printed)

    def test_detached_report_header(self):
        meta = {"device": {"model": "SM-A566B", "manufacturer": "samsung", "sdk": "36", "serial": "localhost:57135"}, "versionCode": 5935,
                "versionName": "6.8.0", "package": "org.autojs.autojs6", "runId": self.RUN, "label": "p6g",
                "started": "2026-10-29T23:22:20", "finished": "2026-10-29T23:29:10", "date": "20261029", "deviceState": {}, "warmups": 2,
                "iterations": 20, "pollMs": 10000, "forceDelayMs": 1000, "capture": "on",
                "attached": {"dir": "attach-p6g", "deviceScript": "tools/memory/detached_gate.sh", "launch": "detached"}}
        text = run_gate.markdown(meta, [report("S4a", "normal", "pass", 0)], [])
        self.assertIn("- Attached (ROADMAP appendix C Q51 (b)): `am instrument` without -w, started and collected by single adb commands "
                      "(tools/memory/detached_gate.sh) into attach-p6g; the test outcome is read from the TestRunner logcat lines; "
                      "the run times are the host clock; without -w the test has no UiAutomation connection, so the meminfo column "
                      "(dumpsys meminfo through that shell) stays empty", text)


if __name__ == "__main__":
    unittest.main()
