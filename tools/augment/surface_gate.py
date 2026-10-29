"""Shows or updates the baselines of the script API surface gate (ScriptSurfaceGateTest, roadmap P6.2).

Usage: py -3.12 -I tools/augment/surface_gate.py [--diff | --update] [--limit N]

ScriptSurfaceGateTest runs in :app:testAppDebugUnitTest, so in the fast checks of prepush_check.py and in CI. It writes
the actual surfaces to app/build/script-modules-surface/ and fails when they differ from the files of the same names in
tools/augment/baseline/:
    surface-jvm.txt             the global object with every top-level module of the layer mounted on the JVM
                                (described by surface_snapshot.js, in the line format of device_surface_snapshot.py)
    script-modules-surface.txt  autojs6/script-modules.json: modules, functions with the arity of each overload,
                                accessors, installs and Java classes
--diff (the default) prints the differences between the actual files and the baselines. After an intended change of the
surface:
    gradlew :app:testAppDebugUnitTest --tests org.autojs.autojs.runtime.api.modules.ScriptSurfaceGateTest
    py -3.12 -I tools/augment/surface_gate.py --update
and commit the baseline diff together with the change. Lines starting with "#" are comments and not compared.
"""
import argparse
import pathlib
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
ACTUAL_DIR = ROOT / "app/build/script-modules-surface"
BASELINE_DIR = ROOT / "tools/augment/baseline"
FILES = ("surface-jvm.txt", "script-modules-surface.txt")


def body(path):
    """The compared lines of a surface file (without comments and empty lines)."""
    return [line for line in path.read_text(encoding="utf-8").splitlines() if line and not line.startswith("#")]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--diff", action="store_true", help="print the differences (the default)")
    mode.add_argument("--update", action="store_true", help="copy the actual surfaces over the baselines")
    parser.add_argument("--limit", type=int, default=200, help="lines of differences to print per file (default 200)")
    args = parser.parse_args()

    status = 0
    for name in FILES:
        actual, baseline = ACTUAL_DIR / name, BASELINE_DIR / name
        if not actual.is_file():
            print(f"{name}: {actual.relative_to(ROOT).as_posix()} is missing; run ScriptSurfaceGateTest first")
            status = 1
            continue
        new = body(actual)
        old = body(baseline) if baseline.is_file() else []
        removed, added = set(old) - set(new), set(new) - set(old)
        state = "no baseline yet" if not baseline.is_file() else f"{len(removed)} removed, {len(added)} added"
        if baseline.is_file() and not removed and not added and old != new:
            state += " (the same lines in another order or with duplicates)"
        print(f"{name}: {len(new)} lines, {state}")
        if args.update:
            if not baseline.is_file() or actual.read_bytes() != baseline.read_bytes():
                BASELINE_DIR.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(actual, baseline)
                print(f"  updated {baseline.relative_to(ROOT).as_posix()}")
            continue
        if old != new:
            status = 1
        diff = sorted([("-", line) for line in removed] + [("+", line) for line in added], key=lambda d: (d[1], d[0]))
        for sign, line in diff[:args.limit]:
            print(f"  {sign} {line}")
        if len(diff) > args.limit:
            print(f"  ... {len(diff) - args.limit} more")
    return status


if __name__ == "__main__":
    sys.exit(main())
