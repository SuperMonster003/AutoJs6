"""pickup / detect golden table on a device (ROADMAP selector P0.3; the "behavior unchanged" check of P1.5): runs
pick_table.js --runs times on its synthetic floaty window, checks that the runs agree row by row, and with --save
records the table as baseline/pick_table-<model>[-legacy].json (-legacy: the new script module layer off, so that the
frozen augment layer is recorded too), with --check compares a run with that file.

Usage: py -3.12 -I tools/selector/device_pick_table.py -s SERIAL [--runs 2] [--new-modules on|off] [--save] [--check]
    [--show 20]
--new-modules sets the hidden switch of the new script module layer for the run and restores it afterwards (default:
leave it as it is; the file name follows the state in effect). Prints the row count of each section, PASS / FAIL of the
run-to-run and the baseline comparison, and the ids of at most --show differing rows (the rows hold synthetic texts
only). AutoJs6 needs its accessibility service and the floaty permission; no taps.
"""
import argparse
import json
import os
import sys
from collections import Counter

# py -I leaves the directory of the script off sys.path; the shared helpers live next to it.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _device as d  # noqa: E402

FIELDS = ("call", "result", "error", "cbArg", "cbCalls")


def table_path(model, new_modules):
    return d.BASELINE / (f"pick_table-{model}" + ("" if new_modules else "-legacy") + ".json")


def section(row_id):
    return row_id.split("|", 1)[0]


def compare(a_rows, b_rows):
    """Row ids that differ between two tables (missing rows included), in the order of [a_rows]."""
    b = {r["id"]: r for r in b_rows}
    diff = []
    for r in a_rows:
        o = b.get(r["id"])
        if o is None or any(r.get(k) != o.get(k) for k in FIELDS):
            diff.append(r["id"])
    a_ids = {r["id"] for r in a_rows}
    diff.extend(r["id"] for r in b_rows if r["id"] not in a_ids)
    return diff


def dump(info, res):
    """The table as JSON with one row per line, so that a diff of two tables shows the rows that changed."""
    head = {k: v for k, v in res.items() if k != "rows"}
    lines = ["{", f' "device": {json.dumps(info, ensure_ascii=False)},', f' "meta": {json.dumps(head, ensure_ascii=False)},', ' "rows": [']
    rows = res["rows"]
    for i, r in enumerate(rows):
        lines.append("  " + json.dumps(r, ensure_ascii=False, separators=(",", ":")) + ("," if i < len(rows) - 1 else ""))
    lines.append(" ]")
    lines.append("}")
    return chr(10).join(lines) + chr(10)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--runs", type=int, default=2)
    parser.add_argument("--new-modules", choices=["on", "off"])
    parser.add_argument("--save", action="store_true")
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--show", type=int, default=20, help="differing row ids to print")
    args = parser.parse_args()
    s = args.serial
    info = d.device_info(s)
    previous = d.new_modules(s)
    wanted = previous if args.new_modules is None else args.new_modules == "on"
    if wanted != previous:
        d.new_modules(s, wanted)
    info["newModules"] = wanted
    print(f"device {info['model']} API {info['sdk']} versionCode {info['versionCode']} (build {info['build']})"
          f" new modules {wanted} {info['date']}")
    try:
        return run(args, info)
    finally:
        if wanted != previous:
            d.new_modules(s, previous)
            print(f"new script module layer restored to {previous}")


def run(args, info):
    s = args.serial
    if not d.a11y_enabled(s):
        print("FAIL the accessibility service of AutoJs6 is not enabled on the device (enable it on the device first)")
        return 1
    d.screen_home(s)
    runs = []
    for i in range(max(1, args.runs)):
        res, lines = d.run_json(s, "pick_table.js", {}, 600)
        if res is None or res.get("errors"):
            print(f"run {i + 1}: FAIL {res.get('errors') if res else 'no result file'}")
            for l in d.script_errors(lines):
                print(f"  {l[:200]}")
            return 1
        runs.append(res)
        # Each run is kept in the work directory for a closer look at differing rows.
        (d.WORK / f"pick_table.run{i + 1}.json").write_text(json.dumps(res, ensure_ascii=False), encoding="utf-8")
    first = runs[0]
    rows = first["rows"]
    counts = Counter(section(r["id"]) for r in rows)
    errors = Counter(section(r["id"]) for r in rows if "error" in r)
    print(f"rows {len(rows)} (nodes {first.get('nodes')}, sweep names {first.get('sweepNames')},"
          f" '{{}}' resolves to {first.get('collectionSpelling')}):")
    for name, n in counts.items():
        print(f"  {name}: {n} rows, {errors.get(name, 0)} errors")
    ok = True
    for i, other in enumerate(runs[1:], start=2):
        diff = compare(rows, other["rows"])
        ok = ok and not diff
        print(f"run 1 vs run {i}: {'PASS' if not diff else 'FAIL'} {len(rows) - len(diff)} / {len(rows)} rows equal")
        for rid in diff[:args.show]:
            print(f"  differs: {rid}")
    path = table_path(info["model"], info["newModules"])
    if args.check:
        if not path.exists():
            print(f"check: FAIL no table {path.name}")
            ok = False
        else:
            base = json.loads(path.read_text(encoding="utf-8"))
            diff = compare(base["rows"], rows)
            ok = ok and not diff
            base_ids = {r["id"] for r in base["rows"]}
            changed = sum(1 for x in diff if x in base_ids)
            print(f"table {path.name} (build {base['device'].get('build')}) vs now: {'PASS' if not diff else 'FAIL'}"
                  f" {len(base['rows']) - changed} / {len(base['rows'])} rows equal")
            by_section = Counter(section(x) for x in diff)
            for name, n in by_section.items():
                print(f"  {name}: {n} rows differ")
            for rid in diff[:args.show]:
                print(f"  differs: {rid}")
    if args.save:
        d.BASELINE.mkdir(exist_ok=True)
        path.write_text(dump(info, first), encoding="utf-8")
        print(f"saved {path.relative_to(d.HOST).as_posix()}")
    print(f"overall: {'PASS' if ok else 'FAIL'}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
