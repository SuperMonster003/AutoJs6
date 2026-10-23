"""Run the opt-in Python Runtime real-plugin diagnostics (androidTest) on one device; print one line per test.

  py -3.12 -I tools/python/run_opt_in_diagnostics.py -s SERIAL [--adb ADB] [--disabled] [--only CLASS ...]

The device needs the debug Host, its androidTest package and the real Python Runtime plugin installed (this runner
never installs, uninstalls or clears anything). Every class guards itself with its own opt-in argument: by default all
nine arguments are passed and each test must pass; with --disabled none is passed and each test must fail fast on its
opt-in guard instead of binding the plugin. Output: PASS / FAIL per test with its duration and, for a failure, the
first line of the reported stack; then a summary. Exit code 0 only when every selected test met the expectation.
"""
import argparse
from pathlib import Path
import re
import subprocess
import sys
import threading
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "sdk37"))
import physical_device  # noqa: E402

PACKAGE = "org.autojs.autojs.core.plugin.python."
RUNNER = "org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner"
DIAGNOSTICS = {
    "PythonRuntimeU1R1StartLeaseInstrumentationTest": ("autojs.python.u1r1.startLease.enabled", 1),
    "PythonRuntimeU1R2BinderInstrumentationTest": ("autojs.python.u1r2.binder.enabled", 2),
    "PythonRuntimeRealPluginDiagnosticTest": ("autojs.python.r2.real.enabled", 1),
    "PythonRuntimeRealPluginCancelRebindDiagnosticTest": ("autojs.python.r2.cancelRebind.enabled", 1),
    "PythonRuntimeRealPluginExecutionMatrixDiagnosticTest": ("autojs.python.r2.execution.matrix.enabled", 4),
    "PythonRuntimeRealPluginTimeoutRebindDiagnosticTest": ("autojs.python.r2.timeoutRebind.enabled", 1),
    "PythonRuntimeRealPluginReliablePipeDiagnosticTest": ("autojs.python.r2.reliablePipe.enabled", 1),
    "PythonRuntimeRealPluginStagingTimeoutDiagnosticTest": ("autojs.python.r2.stagingTimeout.enabled", 1),
    "PythonRuntimeRealPluginBlockingRetirementDiagnosticTest": ("autojs.python.r2.blockingRetirement.enabled", 2),
}
# A guard failure happens before any bind; anything slower than this did real work.
FAST_FAIL_SECONDS = 10.0
STATUS_NAMES = {0: "ok", -1: "error", -2: "failure", -3: "ignored", -4: "assumption failure"}


def run_instrumentation(device, classes, enabled, timeout):
    """Stream `am instrument -r`, stamping each test's start and end; return (tests, terminal code, crashed, exit code)."""
    command = ["am", "instrument", "-r", "-w"]
    if enabled:
        for name in classes:
            command += ["-e", DIAGNOSTICS[name][0], "true"]
    command += ["-e", "class", ",".join(PACKAGE + name for name in classes), RUNNER]
    process = subprocess.Popen([device.adb, "-s", device.serial, "shell", " ".join(command)], stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
    watchdog = threading.Timer(timeout, process.kill)
    watchdog.start()
    tests, bundle, terminal, crashed = {}, {}, None, False
    for raw in process.stdout:
        line = raw.rstrip("\r\n")
        now = time.monotonic()
        field = re.match(r"INSTRUMENTATION_STATUS: (\w+)=(.*)", line)
        code = re.fullmatch(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)", line.strip())
        if field:
            bundle[field[1]] = field[2]
        elif code:
            name = bundle.get("class", "").removeprefix(PACKAGE) + "#" + bundle.get("test", "")
            number = int(code[1])
            if number == 1:
                tests[name] = {"start": now}
            else:
                tests.setdefault(name, {"start": now}).update(code=number, end=now, stack=bundle.get("stack", ""))
            bundle = {}
        elif line.startswith("INSTRUMENTATION_"):
            result = re.fullmatch(r"INSTRUMENTATION_CODE: (-?\d+)", line.strip())
            terminal = int(result[1]) if result else terminal
            crashed = crashed or "Process crashed." in line or line.startswith("INSTRUMENTATION_FAILED:")
    watchdog.cancel()
    exit_code = process.wait()
    return tests, terminal, crashed, exit_code


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--disabled", action="store_true", help="Pass no opt-in argument; expect fast guard failures")
    parser.add_argument("--only", nargs="+", choices=sorted(DIAGNOSTICS), help="Run only these classes")
    parser.add_argument("--timeout", type=float, default=1800.0, help="Seconds for the whole instrumentation run")
    args = parser.parse_args()
    classes = args.only or list(DIAGNOSTICS)
    device = physical_device.Device(args.adb, args.serial)
    device.require_online()

    started = time.monotonic()
    tests, terminal, crashed, exit_code = run_instrumentation(device, classes, not args.disabled, args.timeout)
    expected = sum(DIAGNOSTICS[name][1] for name in classes)
    met = 0
    for name, entry in tests.items():
        code = entry.get("code")
        seconds = entry.get("end", time.monotonic()) - entry["start"]
        # The stack field holds the exception line; later frames arrive as unprefixed lines and are not kept.
        detail = entry.get("stack", "").strip()[:220]
        if args.disabled:
            ok = code in (-2, -3, -4) and seconds <= FAST_FAIL_SECONDS and (code != -2 or "opt-in" in detail)
        else:
            ok = code == 0
        met += ok
        status = STATUS_NAMES.get(code, "no result")
        print(f"{'PASS' if ok else 'FAIL'} {name}: {status}, {seconds:.1f} s" + (f" | {detail}" if detail else ""))
    # The runner reports INSTRUMENTATION_CODE -1 when it finished, whatever the individual results were.
    complete = len(tests) == expected and terminal == -1 and not crashed and exit_code == 0
    if not complete:
        print(f"FAIL run: {len(tests)} / {expected} test(s) reported, terminal code {terminal}, crashed {crashed}, "
              f"adb exit {exit_code}")
    mode = "disabled (expect guard failures)" if args.disabled else "enabled"
    ok = complete and met == expected
    print(f"{'PASS' if ok else 'FAIL'} summary: {mode}, {met} / {expected} as expected, "
          f"{time.monotonic() - started:.0f} s")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
