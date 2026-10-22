"""Host-side checks for inrt_smoke.py parsing, verdict and project packaging; no adb calls.

py -3.12 -I -m unittest discover -s tools/memory -p "test_*.py"
"""
import json
import unittest

import inrt_smoke

MEMINFO_NEW = """Applications Memory Usage (in Kilobytes):
 App Summary
                       Pss(KB)                        Rss(KB)
                        ------                         ------
           Java Heap:     4196                          14120
               TOTAL PSS:    41234            TOTAL RSS:   112000       TOTAL SWAP PSS:      12
"""

MEMINFO_OLD = """** MEMINFO in pid 4321 [org.autojs.autojs6.inrt] **
                   Pss  Private  Private  Swapped     Heap     Heap     Heap
                 Total    Dirty    Clean    Dirty     Size    Alloc     Free
  Native Heap     9000     8900        0        0    12000    10000     2000
        TOTAL    38123    30000     4000        0    20000    16000     4000
"""


def runs(pss, threads):
    return [{"run": i + 1, "ended": True, "pssKb": p, "threads": t} for i, (p, t) in enumerate(zip(pss, threads))]


class ParseTest(unittest.TestCase):
    def test_total_pss_from_app_summary_and_old_table(self):
        self.assertEqual(inrt_smoke.parse_pss_kb(MEMINFO_NEW), 41234)
        self.assertEqual(inrt_smoke.parse_pss_kb(MEMINFO_OLD), 38123)
        self.assertIsNone(inrt_smoke.parse_pss_kb("No process found for: 1"))

    def test_threads_from_proc_status(self):
        self.assertEqual(inrt_smoke.parse_threads("Name:\tinrt\nThreads:\t57\nSigQ:\t0/1\n"), 57)
        self.assertIsNone(inrt_smoke.parse_threads("cat: /proc/1/status: Permission denied"))


class VerdictTest(unittest.TestCase):
    TOLERANCE_KB = 4 * 1024

    def test_noisy_flat_series_passes(self):
        pss = [40000, 41500, 39800, 40900, 40100, 41200, 40300, 39900, 40800, 40400]
        threads = [55, 56, 55, 57, 55, 56, 55, 55, 56, 55]
        self.assertEqual(inrt_smoke.verdict(runs(pss, threads), self.TOLERANCE_KB, 2), [])

    def test_monotonic_growth_fails_even_below_tolerance(self):
        pss = [40000 + 100 * i for i in range(10)]
        problems = inrt_smoke.verdict(runs(pss, [55] * 10), self.TOLERANCE_KB, 2)
        self.assertEqual(problems, ["PSS grows monotonically"])

    def test_constant_series_is_not_growth(self):
        self.assertEqual(inrt_smoke.verdict(runs([40000] * 10, [55] * 10), self.TOLERANCE_KB, 2), [])

    def test_step_beyond_tolerance_fails(self):
        pss = [40000, 40100, 39900, 40000, 46000, 47000, 46500, 46800, 46100, 46900]
        threads = [55, 55, 56, 55, 60, 61, 60, 61, 60, 61]
        problems = inrt_smoke.verdict(runs(pss, threads), self.TOLERANCE_KB, 2)
        self.assertTrue(any(p.startswith("PSS trend") for p in problems), problems)
        self.assertTrue(any(p.startswith("thread count trend") for p in problems), problems)

    def test_two_level_noise_passes(self):
        # 23046RP50C 2026-10-22: fresh processes land on about 215.5 or 223.5 MB.
        pss = [int(mb * 1024) for mb in (222.59, 215.57, 215.18, 226.97, 223.50, 215.80, 215.58, 223.56, 223.95, 219.92)]
        threads = [84, 86, 85, 85, 85, 84, 85, 84, 85, 84]
        self.assertEqual(inrt_smoke.verdict(runs(pss, threads), self.TOLERANCE_KB, 2), [])

    def test_steady_slope_fails(self):
        pss = [40000 + 600 * i + (300 if i % 2 else 0) for i in range(10)]
        problems = inrt_smoke.verdict(runs(pss, [55] * 10), self.TOLERANCE_KB, 2)
        self.assertTrue(any(p.startswith("PSS trend") for p in problems), problems)

    def test_unfinished_run_fails(self):
        data = runs([40000] * 3, [55] * 3)
        data[1] = {"run": 2, "ended": False}
        self.assertEqual(inrt_smoke.verdict(data, self.TOLERANCE_KB, 2), ["a run did not end or could not be measured"])


class LauncherTest(unittest.TestCase):
    def test_picks_the_app_launcher_over_leakcanary(self):
        output = (
            "2 activities found:\n  Activity #0:\n    priority=0 preferredOrder=0 match=0x108000\n"
            "    org.autojs.autojs6.inrt/leakcanary.internal.activity.LeakLauncherActivity\n  Activity #1:\n"
            "    priority=0 preferredOrder=0 match=0x108000\n    org.autojs.autojs6.inrt/org.autojs.autojs.inrt.SplashActivity\n"
        )
        self.assertEqual(inrt_smoke.pick_launcher(output), "org.autojs.autojs6.inrt/org.autojs.autojs.inrt.SplashActivity")

    def test_ignores_other_packages_and_reports_none(self):
        self.assertIsNone(inrt_smoke.pick_launcher("com.android.intentresolver/.ResolverActivity\n"))


class ProjectTest(unittest.TestCase):
    def test_project_config_keeps_the_inrt_package_and_hides_splash_and_logs(self):
        config = inrt_smoke.project_config("memory-inrt-smoke-0123456789ab")
        self.assertEqual(config["packageName"], inrt_smoke.PACKAGE)
        self.assertEqual(config["main"], "main.js")
        self.assertFalse(config["launchConfig"]["splashVisible"])
        self.assertFalse(config["launchConfig"]["logsVisible"])
        self.assertFalse(config["launchConfig"]["runOnBoot"])
        json.dumps(config)

    def test_script_covers_s2_and_s5_and_writes_the_end_marker(self):
        script = inrt_smoke.MAIN_JS
        self.assertTrue(script.startswith('"ui";'))
        for token in ("threads.start", "threads.pool", "ui.layout", "<webview", "activity.finish()", "inrt-smoke.jsonl"):
            self.assertIn(token, script)
        self.assertIn("+ '\\n'", script)


if __name__ == "__main__":
    unittest.main()
