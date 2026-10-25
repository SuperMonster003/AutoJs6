"""Selector result parity on a device (ROADMAP selector P0.2; the "order and count unchanged" check of P2.1-P2.3 and
P5.1): opens each fixed screen, runs parity.js on it --runs times and compares the result sequences (node fingerprints,
depth, indexInParent) of every case between the runs, and with --check against the saved snapshot.

Usage: py -3.12 -I tools/selector/device_parity.py -s SERIAL [--scenarios main settings] [--runs 2] [--new-modules on|off]
    [--save] [--check]
--save writes the first run to baseline/parity-<model>-<scenario>.json; --check compares the first run with that file
(the literals derived from the screen must hash the same, else the screen changed and the comparison is void).
Prints one line per case: PASS / FAIL and the result count; no node text (the snapshot holds hashes only).
"""
import argparse
import json
import os
import sys

# py -I leaves the directory of the script off sys.path; the shared helpers live next to it.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _device as d  # noqa: E402

SCREENS = {"main": d.screen_main, "settings": d.screen_settings}


def snapshot_path(model, scenario):
    return d.BASELINE / f"parity-{model}-{scenario}.json"


def compare(a, b):
    """Compares two parity results case by case; returns ({name: (ok, detail)}, node match ratio)."""
    out = {}
    total = same = 0
    if a.get("literals") != b.get("literals"):
        out["literals"] = (False, "the literals derived from the screen differ (the screen changed)")
    cases_b = {c["name"]: c for c in b.get("cases", [])}
    for ca in a.get("cases", []):
        cb = cases_b.get(ca["name"])
        if cb is None:
            out[ca["name"]] = (False, "missing in the other run")
            continue
        if ca.get("skipped") or cb.get("skipped"):
            out[ca["name"]] = (ca.get("skipped") == cb.get("skipped"), "skipped")
            continue
        na, nb = ca.get("nodes"), cb.get("nodes")
        if ca.get("error") or cb.get("error"):
            ok = ca.get("error") == cb.get("error")
            out[ca["name"]] = (ok, "error " + ("same" if ok else "differs"))
            continue
        n = max(len(na), len(nb))
        total += n
        same += sum(1 for x, y in zip(na, nb) if x == y)
        if na == nb:
            out[ca["name"]] = (True, f"count={len(na)}")
        else:
            first = next((i for i, (x, y) in enumerate(zip(na, nb)) if x != y), min(len(na), len(nb)))
            out[ca["name"]] = (False, f"count={len(na)} vs {len(nb)}, first difference at {first}")
    return out, (same / total if total else 1.0)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--new-modules", choices=["on", "off"], help="set the new script module layer switch for the run")
    parser.add_argument("--scenarios", nargs="*", default=["main", "settings"], choices=list(SCREENS))
    parser.add_argument("--runs", type=int, default=2)
    parser.add_argument("--save", action="store_true", help="write the first run to baseline/parity-<model>-<scenario>.json")
    parser.add_argument("--check", action="store_true", help="compare the first run with the saved snapshot")
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
    all_ok = True
    for scenario in args.scenarios:
        expect = SCREENS[scenario](s)
        runs = []
        for i in range(max(1, args.runs)):
            res, lines = d.run_json(s, "parity.js", {"scenario": scenario, "expectPackage": expect}, 180,
                                    name=f"parity-{scenario}.js")
            if res is None or res.get("errors"):
                print(f"[{scenario}] run {i + 1} FAIL {res.get('errors') if res else 'no result file'}")
                for l in d.script_errors(lines):
                    print(f"  {l[:200]}")
                all_ok = False
                break
            runs.append(res)
        if not runs:
            continue
        first = runs[0]
        counted = sum(1 for c in first["cases"] if "count" in c)
        print(f"[{scenario}] root={first.get('rootPackage')} cases={len(first['cases'])} with results={counted}")
        for c in first["cases"]:
            if c.get("error"):
                print(f"  {c['name']}: ERROR {c['error']}")
        for i, other in enumerate(runs[1:], start=2):
            result, ratio = compare(first, other)
            ok = all(v[0] for v in result.values())
            all_ok = all_ok and ok
            print(f"  run 1 vs run {i}: {'PASS' if ok else 'FAIL'} node match {ratio * 100:.1f}%")
            for name, (case_ok, detail) in result.items():
                print(f"    {name}: {'PASS' if case_ok else 'FAIL'} {detail}")
        path = snapshot_path(info["model"], scenario)
        if args.check:
            if not path.exists():
                print(f"  check: FAIL no snapshot {path.name}")
                all_ok = False
            else:
                base = json.loads(path.read_text(encoding="utf-8"))
                result, ratio = compare(base["result"], first)
                ok = all(v[0] for v in result.values())
                all_ok = all_ok and ok
                print(f"  snapshot {path.name} (build {base['device'].get('build')}) vs now: {'PASS' if ok else 'FAIL'}"
                      f" node match {ratio * 100:.1f}%")
                for name, (case_ok, detail) in result.items():
                    if not case_ok:
                        print(f"    {name}: FAIL {detail}")
        if args.save:
            d.BASELINE.mkdir(exist_ok=True)
            path.write_text(json.dumps({"device": info, "result": first}, ensure_ascii=False, separators=(",", ":"))
                            + chr(10), encoding="utf-8")
            print(f"  saved {path.relative_to(d.HOST).as_posix()}")
    d.screen_home(s)
    print(f"overall: {'PASS' if all_ok else 'FAIL'}")
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
