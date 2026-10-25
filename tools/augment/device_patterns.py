"""Checks the pattern check of the new module layer against the regex engine of a device (appendix C Q70 (2)).

Usage: py -3.12 -I tools/augment/device_patterns.py -s SERIAL [--samples 100000] [--seed 70] [--verdicts FILE]

Runs bench/patterns_check.js in the host process (a host built with the pattern precheck, core/automator/filter/PatternPrecheck.kt since selector P1.4, Q70 (2)): every pattern the check
refuses must not compile with JsRegexUtils.compileJsOrPlainRegex, i.e. java.util.regex.Pattern on ICU. The patterns are
the corpus of the JVM test (app/src/test/resources/augment-q70/patterns.json), --samples random strings of regex
characters and --samples random patterns built from the syntax (half of them mutated). Printed per group: patterns,
failing ones, refused ones, wrongly refused ones (PASS when 0) and those left to compiling; also how many patterns
only Rhino's RegExp or only Pattern takes. --verdicts saves one line per corpus pattern (compiles, refused, RegExp
takes it: 0 / 1 each) for a comparison with the JVM. The switch of the new module layer is not needed (the check is
called directly); nothing is tapped.
"""
import argparse
import json
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import device_bench as db  # noqa: E402

CORPUS = HERE.parent.parent / "app" / "src" / "test" / "resources" / "augment-q70" / "patterns.json"
LINE_RE = re.compile(r"AJ6PAT (\S+) total (\d+) failing (\d+) refused (\d+) wrong (\d+) left (\d+) regexp-only (\d+) pattern-only (\d+)")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--samples", type=int, default=100000)
    parser.add_argument("--seed", type=int, default=70)
    parser.add_argument("--verdicts")
    a = parser.parse_args()
    sys.stdout.reconfigure(errors="backslashreplace")

    s = a.serial
    args = HERE / "__pycache__" / "patterns_args.json"
    args.parent.mkdir(exist_ok=True)
    args.write_text(json.dumps({"samples": a.samples, "seed": a.seed}), encoding="utf-8")
    db.adb(s, "shell", "mkdir", "-p", db.DEVICE_DIR, check=True)
    db.adb(s, "push", str(args), f"{db.DEVICE_DIR}/patterns_args.json", check=True)
    db.adb(s, "push", str(CORPUS), f"{db.DEVICE_DIR}/patterns.json", check=True)
    remote = db.push_script(s, "patterns_check.js")
    lines = db.run_script(s, remote, max(600, a.samples // 100))
    groups = 0
    failed = False
    for line in lines:
        m = LINE_RE.search(line)
        if m:
            groups += 1
            name, total, failing, refused, wrong, left, regexp_only, pattern_only = m.groups()
            share = 100.0 * int(left) / int(failing) if int(failing) else 0.0
            verdict = "PASS" if wrong == "0" else "FAIL"
            failed |= wrong != "0"
            print(f"{verdict} {name}: {total} patterns, {failing} fail to compile, {refused} refused, {wrong} wrongly, "
                  f"{left} left to compiling ({share:.1f}%); RegExp only {regexp_only}, Pattern only {pattern_only}")
        elif "AJ6PAT wrong" in line or "AJ6PAT done" in line:
            print("  " + line.split("AJ6PAT ", 1)[1])
        elif "/E:" in line:
            print("  script error: " + line[:200])
    if a.verdicts:
        db.adb(s, "pull", f"{db.DEVICE_DIR}/patterns_verdicts.txt", a.verdicts, check=True)
    for name in ("patterns_check.js", "patterns.json", "patterns_args.json", "patterns_verdicts.txt"):
        db.adb(s, "shell", "rm", "-f", f"{db.DEVICE_DIR}/{name}")
    if groups != 3:
        print(f"FAIL {groups} of 3 groups reported (host without PatternPrecheck, or the script did not finish)")
        failed = True
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
