"""Selector benchmark on a device (ROADMAP selector P0.1; rerun by P2.4 / P3): opens each screen, runs bench.js on it
through RunIntentActivity and prints one line per item: PASS / FAIL and the median (milliseconds, attempts, CPU percent).

Usage: py -3.12 -I tools/selector/device_bench.py -s SERIAL [--scenarios settings main overlay] [--rounds 10]
    [--cpu-rounds 10] [--idle-rounds 3] [--cpu-ms 10000] [--new-modules on|off] [--save] [--label NAME]
Screens: settings = home page of the system settings (restarted with am start -S), main = AutoJs6 main screen,
overlay = home screen with a raw floaty window of 300 TextViews (created by the script, searched through the window
filter). The accessibility service of AutoJs6 must be enabled (the script only waits for it) and AutoJs6 needs the
floaty permission for the overlay; dialogs on the device are left to the maintainer.
--new-modules sets the switch of the new script module layer for the run and restores it afterwards (the baseline of
2026-10-25 ran with on, the default of the build).
--save writes the summary (no samples of node text, only timings) to baseline/bench-<model>[-<label>].json.
No taps: screens are opened with am start only.
"""
import argparse
import json
import os
import sys
import time

# py -I leaves the directory of the script off sys.path; the shared helpers live next to it.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _device as d  # noqa: E402

SCREENS = {"settings": d.screen_settings, "main": d.screen_main, "overlay": d.screen_home}
TIMED = ["full", "miss", "missId", "lookupId", "missMixed", "hit", "late"]
POLLED = ["findOne600", "findOne600Id", "findOne600Default", "lateOne"]
CPU = ["cpuIdle", "cpuFixed", "cpuAssisted", "stormIdle", "stormFixed", "stormAssisted"]


def fast_path_text(f):
    """The fast path counters of auto.stats (P2.4, walks: appendix C Q15) summed over the rounds of an item, or an empty string."""
    if not f:
        return ""
    return (f" fastPath hits={f['hits']} misses={f['misses']} fallbacks={f['fallbacks']} mismatches={f['mismatches']}"
            f" walks={f.get('walks', 0)}")


def summarize(scenario, res, lines):
    """Prints the items of one scenario and returns their summary (medians only)."""
    if res is None:
        print(f"[{scenario}] FAIL no result file")
        for l in d.script_errors(lines):
            print(f"  {l[:200]}")
        return {"ok": False}
    summary = {"rootPackage": res.get("rootPackage"), "nodes": res.get("nodes"), "items": {}}
    items = res.get("items", {})
    sc = items.get("scenario", {})
    print(f"[{scenario}] scenario {'PASS' if sc.get('ok') else 'FAIL'} nodes={res.get('nodes')} root={res.get('rootPackage')}")
    for e in res.get("errors", []):
        print(f"  error: {e}")
    for name in TIMED:
        it = items.get(name)
        if it is None:
            continue
        extra = ""
        if name == "full" and it.get("sizes") and len(it["sizes"]) > 1:
            extra = f" sizes varied {it['sizes']}"
        if it.get("error"):
            extra += f" error={it['error']}"
        print(f"  {name}: {'PASS' if it.get('ok') else 'FAIL'} median={it.get('median')} ms"
              f" min={min(it['samples']) if it.get('samples') else None}"
              f" max={max(it['samples']) if it.get('samples') else None}"
              f"{'' if it.get('cpuPerRun') is None else ' cpu/run=' + str(it['cpuPerRun']) + ' ms'}{extra}")
        summary["items"][name] = {"ok": it.get("ok"), "median": it.get("median"), "samples": it.get("samples"),
                                  **({"cpuPerRun": it["cpuPerRun"]} if it.get("cpuPerRun") is not None else {}),
                                  **({"sizes": it["sizes"]} if "sizes" in it else {})}
    for name in POLLED:
        f = items.get(name)
        if f is None:
            continue
        # A fast path miss that a full search corrected (appendix C Q1) fails the item: it has to stay at 0.
        ok = f.get("ok") and not (f.get("fastPath") or {}).get("mismatches")
        print(f"  {name}: {'PASS' if ok else 'FAIL'} attempts median={f.get('medianAttempts')}"
              f" {f.get('attempts')} elapsed median={f.get('median')} ms cpu median={f.get('medianCpu')} ms"
              f" per attempt={f.get('medianCpuPerAttempt')} ms{fast_path_text(f.get('fastPath'))}"
              f"{' error=' + f['error'] if f.get('error') else ''}")
        summary["items"][name] = {"ok": ok, "medianAttempts": f.get("medianAttempts"), "attempts": f.get("attempts"),
                                  "median": f.get("median"), "samples": f.get("samples"), "medianCpu": f.get("medianCpu"),
                                  "medianCpuPerAttempt": f.get("medianCpuPerAttempt"), "fastPath": f.get("fastPath")}
    for name in CPU:
        it = items.get(name)
        if it is None:
            continue
        ok = it.get("ok") and not (it.get("fastPath") or {}).get("mismatches")
        print(f"  {name}: {'PASS' if ok else 'FAIL'} cpu median={it.get('medianCpu')}% {it.get('cpu')}"
              f" attempts median={it.get('medianAttempts')}{fast_path_text(it.get('fastPath'))}"
              f"{' error=' + it['error'] if it.get('error') else ''}")
        summary["items"][name] = {"ok": ok, "medianCpu": it.get("medianCpu"), "cpu": it.get("cpu"),
                                  "medianAttempts": it.get("medianAttempts"), "attempts": it.get("attempts"),
                                  **({"fastPath": it["fastPath"]} if it.get("fastPath") else {})}
    if "stormUpdatesPerSecond" in res:
        print(f"  storm updates per second: {res['stormUpdatesPerSecond']}")
        summary["stormUpdatesPerSecond"] = res["stormUpdatesPerSecond"]
    if res.get("late"):
        summary["late"] = res["late"]
    oks = [sc.get("ok")] + [v.get("ok") for v in summary["items"].values()]
    summary["ok"] = all(oks)
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--new-modules", choices=["on", "off"], help="set the new script module layer switch for the run")
    parser.add_argument("--scenarios", nargs="*", default=["settings", "main", "overlay"], choices=list(SCREENS))
    parser.add_argument("--settings-package", help="the package of the active window root on the settings home page when it is not the one of the settings activity (ColorOS 15: com.oplus.wirelesssettings)")
    parser.add_argument("--settle", type=float, default=0, help="seconds to wait after opening a screen, for screens that keep loading content after they open (ColorOS 15 settings home page: 20)")
    parser.add_argument("--rounds", type=int, default=10)
    parser.add_argument("--warmup", type=int, default=5)
    parser.add_argument("--cpu-rounds", type=int, default=10)
    parser.add_argument("--idle-rounds", type=int, default=3)
    parser.add_argument("--cpu-ms", type=int, default=10000)
    parser.add_argument("--save", action="store_true", help="write the summary to baseline/bench-<model>[-<label>].json")
    parser.add_argument("--label", help="suffix of the saved file (e.g. p2.4)")
    args = parser.parse_args()
    s = args.serial
    info = d.device_info(s)
    return d.with_new_modules(s, args.new_modules, lambda state: run(args, dict(info, newModules=state)))


def run(args, info):
    s = args.serial
    print(f"device {info['model']} API {info['sdk']} versionCode {info['versionCode']} (build {info['build']})"
          f" new modules {info['newModules']} {info['date']}")
    if not d.a11y_enabled(s):
        print("FAIL the accessibility service of AutoJs6 is not enabled on the device (enable it on the device first)")
        return 1
    report = {"device": info, "scenarios": {}}
    all_ok = True
    for scenario in args.scenarios:
        expect = SCREENS[scenario](s)
        if args.settle > 0:
            time.sleep(args.settle)
        if scenario == "settings" and args.settings_package:
            expect = args.settings_package
        pcy = d.host_scheduling(s)
        cpu_runs = 2 * args.cpu_rounds + args.idle_rounds
        storms = 2 if scenario == "overlay" else 1
        wait = 120 + storms * cpu_runs * (args.cpu_ms / 1000 + 1) + args.rounds * 10
        config = {"scenario": scenario, "expectPackage": expect, "rounds": args.rounds, "warmup": args.warmup,
                  "cpuRounds": args.cpu_rounds, "idleRounds": args.idle_rounds, "cpuMs": args.cpu_ms}
        res, lines = d.run_json(s, "bench.js", config, wait, name=f"bench-{scenario}.js")
        summary = summarize(scenario, res, lines)
        summary["scheduling"] = {"before": pcy, "after": d.host_scheduling(s)}
        print(f"  host scheduling group: {summary['scheduling']['before']} -> {summary['scheduling']['after']}")
        report["scenarios"][scenario] = summary
        all_ok = all_ok and summary.get("ok", False)
    d.screen_home(s)
    print(f"overall: {'PASS' if all_ok else 'FAIL'}")
    if args.save:
        d.BASELINE.mkdir(exist_ok=True)
        name = f"bench-{info['model']}" + (f"-{args.label}" if args.label else "") + ".json"
        path = d.BASELINE / name
        path.write_text(json.dumps(report, ensure_ascii=False, indent=1) + chr(10), encoding="utf-8")
        print(f"saved {path.relative_to(d.HOST).as_posix()}")
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
