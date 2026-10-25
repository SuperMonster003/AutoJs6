"""Evaluates the device cases of the JVM script module tests with the host installed on a device.

Usage: py -3.12 -I tools/augment/device_eval.py -s SERIAL [--cases DIR_OR_FILE ...] [--keep] [--strict-errors]
       [--debug-print on|off] [--console]

The script module tests write their cases (the expressions with the values the JVM gives, in "kt", and the ones they
cannot evaluate on the JVM, without a value) to app/build/script-modules-device/*.json. This script runs the same
expressions in one script on the device (RunIntentActivity), renders every result the way DifferentialRhino.render
does and compares it with the value recorded on the JVM. Two failures count as agreeing whatever their error names
unless --strict-errors is given (the JVM cannot always build the error messages). Expressions without an expected value
are only printed. The pushed script is removed afterwards unless --keep is given. For example:
py -3.12 -I tools/augment/device_eval.py -s SERIAL --cases app/build/script-modules-device --strict-errors
Until P5.2 --new-modules set the switch of the new script module layer for the run, and the default cases were those
of the P0 differential tests (app/build/augment-differential).
--debug-print sets the master switch of the new layer's debug printing (appendix C Q4) the same way, and --console
prints the console (GlobalConsole) and ScriptModules logcat lines written while the cases ran. logcat is cleared
before each script starts and polled through its last lines, as in device_bench.py.
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import time
import urllib.parse
import uuid

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from device_bench import clear_log, console_lines, pref_value_js  # noqa: E402
HOST = HERE.parents[1]
DEFAULT_CASES = HOST / "app" / "build" / "script-modules-device"
DEVICE_DIR = "/sdcard/Download/aj6-augment-bench"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
PACKAGE = "org.autojs.autojs6"
MARK = "AJ6EVAL"
DEBUG_PRINT_KEY = "key_$_script_module_debug_print"
SWITCH_MARK = "AJ6SWITCH"

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
    console.log('AJ6EVAL ' + AJ6_RUN + ' ' + i + ' ' + encodeURIComponent(aj6Render(AJ6_CASES[i])));
}
console.log('AJ6EVAL ' + AJ6_RUN + ' done');
"""


def adb(serial, *args, check=False):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", check=check)


def run_script(serial, remote, count, wait, run):
    """Clears logcat, starts the script and collects the AJ6EVAL lines of [run] until the end mark, the host's line
    for the end of the script (an error no script can catch ends it before the end mark) or the timeout.

    Each run has its own id and file name: a script of an earlier run still blocked on a case (one the timeout gave
    up on) can print its remaining results later, and before 2026-10-24 they were taken for results of this run."""
    clear_log(serial)
    before = set(console_lines(serial))
    adb(serial, "shell", "am", "start", "-n", f"{PACKAGE}/{RUN_ACTIVITY}", "-e", "path", remote, check=True)
    results = {}
    deadline = time.time() + wait
    # Every translation of text_execution_finished starts with "[<path>] ", the path ending with the file name.
    finished = "/" + remote.rsplit("/", 1)[-1] + "] "
    done = False
    while time.time() < deadline and not done:
        time.sleep(0.5)
        for line in console_lines(serial):
            if line in before:
                continue
            if "/V: [" in line and finished in line:
                done = True
                continue
            # Console lines carry a "HH:mm:ss.SSS/D: " prefix before the message.
            start = line.find(MARK + " ")
            if start < 0:
                continue
            owner, _, rest = line[start + len(MARK) + 1:].partition(" ")
            if owner != run:
                continue
            if rest == "done":
                done = True
                continue
            index, _, encoded = rest.partition(" ")
            if index.isdigit():
                results[int(index)] = urllib.parse.unquote(encoded)
    if len(results) < count:
        print(f"warning: got {len(results)} of {count} results before the timeout", file=sys.stderr)
    return results


def set_pref(serial, key, value, label):
    """Sets the boolean preference [key] through Pref.putBooleanSync in a small script and returns its previous value."""
    lines = [
        "var pref = org.autojs.autojs.core.pref.Pref.INSTANCE;",
        f"var before = {pref_value_js(key)};",
        f"pref.putBooleanSync('{key}', {'true' if value else 'false'});",
        f"console.log('{SWITCH_MARK} ' + before + ' ' + {pref_value_js(key)});",
    ]
    local = HERE / "__pycache__" / "aj6_switch.js"
    local.parent.mkdir(exist_ok=True)
    local.write_text(chr(10).join(lines) + chr(10), encoding="utf-8")
    remote = f"{DEVICE_DIR}/aj6_switch.js"
    adb(serial, "shell", "mkdir", "-p", DEVICE_DIR, check=True)
    adb(serial, "push", str(local), remote, check=True)
    clear_log(serial)
    before_lines = set(console_lines(serial))
    adb(serial, "shell", "am", "start", "-n", f"{PACKAGE}/{RUN_ACTIVITY}", "-e", "path", remote, check=True)
    deadline = time.time() + 30
    while time.time() < deadline:
        time.sleep(0.5)
        for line in console_lines(serial):
            start = line.find(SWITCH_MARK + " ")
            if line in before_lines or start < 0:
                continue
            before, after = line[start + len(SWITCH_MARK) + 1:].split()[:2]
            if after != ("true" if value else "false"):
                sys.exit(f"the switch did not change: {line}")
            print(f"{label}: {before} -> {after}")
            return before == "true"
    sys.exit("no switch output from the device")


def device_epoch(serial):
    return adb(serial, "shell", "date", "+%s", check=True).stdout.strip()


def console_since(serial, epoch):
    """GlobalConsole and ScriptModules logcat lines since [epoch] (device seconds), as "Tag/L: message"."""
    out = adb(serial, "logcat", "-d", "-v", "epoch", "-T", f"{epoch}.000", "-s", "GlobalConsole", "ScriptModules").stdout
    lines = []
    for line in out.splitlines():
        m = re.match(r"^\s*\d+\.\d+\s+\d+\s+\d+\s+([VDIWEF])\s+([^:]+?)\s*:\s?(.*)$", line)
        if m:
            lines.append(f"{m.group(2)}/{m.group(1)}: {m.group(3)}")
    return lines


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
    parser.add_argument("--strict-errors", action="store_true", help="two failures agree only with the same error name")
    parser.add_argument("--debug-print", choices=["on", "off"], help="set the debug printing master switch for the run")
    parser.add_argument("--console", action="store_true", help="print the console and ScriptModules lines of the run")
    args = parser.parse_args()
    # Device results may hold characters the console code page cannot show (a GBK console on Windows).
    sys.stdout.reconfigure(errors="backslashreplace")

    cases = load_cases(args.cases)
    if not cases:
        sys.exit("no cases found; run the differential tests first (:app:testAppDebugUnitTest)")
    run = uuid.uuid4().hex[:8]
    script = (f"var AJ6_RUN = '{run}';\nvar AJ6_CASES = " + json.dumps([entry["expression"] for _, entry in cases], ensure_ascii=True)
              + ";\n" + RENDER_JS)
    local = HERE / "__pycache__" / "aj6_device_eval.js"
    local.parent.mkdir(exist_ok=True)
    local.write_text(script, encoding="utf-8")
    remote = f"{DEVICE_DIR}/aj6_device_eval_{run}.js"
    adb(args.serial, "shell", "mkdir", "-p", DEVICE_DIR, check=True)
    adb(args.serial, "push", str(local), remote, check=True)
    wanted_debug = args.debug_print == "on"
    previous_debug = set_pref(args.serial, DEBUG_PRINT_KEY, wanted_debug, "debug printing") if args.debug_print else None
    started = device_epoch(args.serial)
    try:
        results = run_script(args.serial, remote, len(cases), args.wait, run)
        if args.console:
            print("--- console ---")
            for line in console_since(args.serial, started):
                if MARK not in line:
                    print(line)
            print("--- end of console ---")
    finally:
        if previous_debug is not None and previous_debug != wanted_debug:
            set_pref(args.serial, DEBUG_PRINT_KEY, previous_debug, "debug printing")
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
        same = device_key == expected or (not args.strict_errors and device_key.startswith("throws") and expected.startswith("throws"))
        if same:
            passed += 1
        else:
            failed += 1
            print(f"FAIL {test}: {entry['expression']}\n     jvm-kt={expected}\n     device={device_key}")
    print(f"device {args.serial}: {passed} agree with the JVM Kotlin value, {failed} differ, {printed} informational")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
