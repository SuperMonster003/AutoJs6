"""Writes a baseline profile of the timing gate (ROADMAP P6.1) from result files of ci_timing_gate.py.

Usage: py -3.12 -I tools/augment/ci_timing_baseline.py --profile NAME RESULT.json [RESULT.json ...]
    [--baseline tools/augment/baseline/timing-gate.json] [--write]

Design: docs/augment/P6-1-ci-timing-gate.md. Each result contributes its checked values (the better round of the last
attempt of every case: prologue ms, failing call ratios); the profile gets their median per case and the spread
(min / max). The cases of the profile are replaced by those of the results, so a case removed from the benchmark
scripts leaves the baseline with the update. All results must come from the same device recipe (model, SDK, ABI,
online CPUs); at least 3 are expected (fewer only with a warning). Without --write the old and new values are printed
and nothing is written; thresholds and other profiles are kept.
"""
import argparse
import datetime
import json
import pathlib
import statistics
import sys

HERE = pathlib.Path(__file__).resolve().parent
DEFAULT_BASELINE = HERE / "baseline" / "timing-gate.json"
RECIPE_KEYS = ("model", "sdk", "abi", "cpus")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--profile", required=True, help="e.g. local-avd, github-actions")
    parser.add_argument("--baseline", type=pathlib.Path, default=DEFAULT_BASELINE)
    parser.add_argument("--write", action="store_true", help="write the baseline file (default: print only)")
    parser.add_argument("results", nargs="+", type=pathlib.Path)
    args = parser.parse_args()
    results = [json.loads(p.read_text(encoding="utf-8")) for p in args.results]
    recipes = {tuple(r["device"].get(k) for k in RECIPE_KEYS) for r in results}
    if len(recipes) != 1:
        sys.exit(f"the results come from different device recipes {sorted(recipes)} ({', '.join(RECIPE_KEYS)})")
    if len(results) < 3:
        print(f"warning: {len(results)} result(s); the design asks for at least 3")
    per_key = {}
    for r in results:
        for row in r["checks"]:
            if row["value"] is not None:
                per_key.setdefault(row["key"], []).append(row["value"])
    partial = sorted(k for k, v in per_key.items() if len(v) < len(results))
    for key in partial:
        print(f"warning: {key} is in {len(per_key[key])} of {len(results)} results")
    baseline = json.loads(args.baseline.read_text(encoding="utf-8")) if args.baseline.is_file() else {"schema": 1}
    old = baseline.setdefault("profiles", {}).get(args.profile, {}).get("values", {})
    values = {k: round(statistics.median(v), 3) for k, v in sorted(per_key.items())}
    spread = {k: [round(min(v), 3), round(max(v), 3)] for k, v in sorted(per_key.items())}
    print(f"profile {args.profile}: {len(values)} cases from {len(results)} results")
    for key in sorted(set(values) | set(old)):
        before, after = old.get(key), values.get(key)
        if before != after:
            print(f"  {key}: {before if before is not None else '-'} -> {after if after is not None else 'removed'}"
                  + (f" (spread {spread[key][0]}-{spread[key][1]})" if key in spread else ""))
    first = results[0]["device"]
    baseline["profiles"][args.profile] = {
        "updated": datetime.date.today().isoformat(),
        "recipe": {k: first.get(k) for k in RECIPE_KEYS},
        "sources": [{"label": r.get("label"), "time": r.get("time"), "commit": r.get("commit"),
                     "versionCode": r["device"].get("versionCode"), "hostMachine": r["device"].get("hostMachine"),
                     "verdict": r.get("verdict")} for r in results],
        "values": values,
        "spread": spread,
    }
    if not args.write:
        print("dry run: pass --write to write " + str(args.baseline))
        return 0
    args.baseline.parent.mkdir(parents=True, exist_ok=True)
    args.baseline.write_text(json.dumps(baseline, indent=1, ensure_ascii=True) + "\n", encoding="utf-8")
    print(f"written {args.baseline}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
