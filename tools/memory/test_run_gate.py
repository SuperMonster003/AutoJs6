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
            run_gate.scenario_list("S13")


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


if __name__ == "__main__":
    unittest.main()
