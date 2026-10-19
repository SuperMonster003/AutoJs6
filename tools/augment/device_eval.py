"""Evaluates the documented differences of the JVM differential tests with the host installed on a device.

Usage: py -3.12 -I tools/augment/device_eval.py -s SERIAL [--cases DIR_OR_FILE ...] [--keep]

The differential tests (app/src/test/java/org/autojs/autojs/runtime/api/augment/differential/) write their
documented differences and the expressions they had to skip on the JVM to app/build/augment-differential/*.json.
This script runs the same expressions in one script on the device (RunIntentActivity), renders every result the
way DifferentialRhino.render does and compares it with the Kotlin value recorded on the JVM. Two failures count as
agreeing whatever their error names (the JVM cannot build the Kotlin error messages), as in the P0 differential
tests. Skipped expressions have no expected value and are only printed. The pushed script is removed afterwards
unless --keep is given.
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import time
import urllib.parse

HERE = pathlib.Path(__file__).resolve().parent
HOST = HERE.parents[1]
DEFAULT_CASES = HOST / "app" / "build" / "augment-differential"
DEVICE_DIR = "/sdcard/Download/aj6-augment-bench"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
PACKAGE = "org.autojs.autojs6"
MARK = "AJ6EVAL"

RENDER_JS = r"""
function aj6Render(source) {
    try {
        var v = eval('(' + source + ')');
        if (v === undefined) return 'undefined';
        if (typeof v === 'function') return 'function';
        if (typeof v === 'number' && !isFinite(v)) return String(v);
        return JSON.stringify(v);
    } catch (e) {
        var name = (e && e.name) || Object.prototype.toString.call(e);
        if (e && e.javaException) name = 'JavaException ' + e.javaException.getClass().getSimpleName();
        return 'throws ' + name + '\u0000' + String(e && e.message !== undefined ? e.message : e);
    }
}
for (var i = 0; i < AJ6_CASES.length; i++) {
    console.log('AJ6EVAL ' + i + ' ' + encodeURIComponent(aj6Render(AJ6_CASES[i])));
}
console.log('AJ6EVAL done');
"""


def adb(serial, *args, check=False):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", check=check)


def console_lines(serial):
    out = adb(serial, "logcat", "-d").stdout
    return [re.sub(r"^.*GlobalConsole: ", "", l).rstrip() for l in out.splitlines() if "GlobalConsole:" in l]


def run_script(serial, remote, count, wait):
    """Starts the script and collects the AJ6EVAL lines (logcat is polled, never cleared)."""
    before = set(console_lines(serial))
    adb(serial, "shell", "am", "start", "-n", f"{PACKAGE}/{RUN_ACTIVITY}", "-e", "path", remote, check=True)
    results = {}
    deadline = time.time() + wait
    done = False
    while time.time() < deadline and not done:
        time.sleep(0.5)
        for line in console_lines(serial):
            # Console lines carry a "HH:mm:ss.SSS/D: " prefix before the message.
            start = line.find(MARK + " ")
            if line in before or start < 0:
                continue
            rest = line[start + len(MARK) + 1:]
            if rest == "done":
                done = True
                continue
            index, _, encoded = rest.partition(" ")
            if index.isdigit():
                results[int(index)] = urllib.parse.unquote(encoded)
    if len(results) < count:
        print(f"warning: got {len(results)} of {count} results before the timeout", file=sys.stderr)
    return results


def key_of(rendered):
    """The comparison key of a rendered result: the part before the error detail."""
    return rendered.split("\u0000", 1)[0]


def load_cases(paths):
    cases = []
    for path in paths:
        files = sorted(path.glob("*.json")) if path.is_dir() else [path]
        for file in files:
            for entry in json.loads(file.read_text(encoding="utf-8")):
                cases.append((file.stem, entry))
    return cases


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--cases", nargs="*", type=pathlib.Path, default=[DEFAULT_CASES])
    parser.add_argument("--keep", action="store_true", help="keep the pushed script on the device")
    parser.add_argument("--wait", type=int, default=120)
    args = parser.parse_args()

    cases = load_cases(args.cases)
    if not cases:
        sys.exit("no cases found; run the differential tests first (:app:testAppDebugUnitTest)")
    script = "var AJ6_CASES = " + json.dumps([entry["expression"] for _, entry in cases], ensure_ascii=True) + ";\n" + RENDER_JS
    local = HERE / "__pycache__" / "aj6_device_eval.js"
    local.parent.mkdir(exist_ok=True)
    local.write_text(script, encoding="utf-8")
    remote = f"{DEVICE_DIR}/aj6_device_eval.js"
    adb(args.serial, "shell", "mkdir", "-p", DEVICE_DIR, check=True)
    adb(args.serial, "push", str(local), remote, check=True)
    try:
        results = run_script(args.serial, remote, len(cases), args.wait)
    finally:
        if not args.keep:
            adb(args.serial, "shell", "rm", "-rf", DEVICE_DIR)

    passed = failed = printed = 0
    for index, (test, entry) in enumerate(cases):
        device = results.get(index)
        expected = entry.get("kt")
        device_key = key_of(device) if device is not None else "<no result>"
        if expected is None:
            printed += 1
            print(f"INFO {test}: {entry['expression']}\n     device={device_key}")
            continue
        same = device_key == expected or (device_key.startswith("throws") and expected.startswith("throws"))
        if same:
            passed += 1
        else:
            failed += 1
            print(f"FAIL {test}: {entry['expression']}\n     jvm-kt={expected}\n     device={device_key}")
    print(f"device {args.serial}: {passed} agree with the JVM Kotlin value, {failed} differ, {printed} informational")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
