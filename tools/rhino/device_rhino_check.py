"""Runs the Rhino related instrumented tests of the host on devices and prints one line per test.

Usage: py -3.12 -I -u tools/rhino/device_rhino_check.py -s SERIAL [-s SERIAL ...] [--install] [--class NAME ...]

Meant for a Rhino jar update (Rhino-For-AutoJs6 AGENTS.md, section 5): the production script engine, block scoping and
the Uint8Array Base64 / hex methods (RhinoConstScopingDeviceTest), Unicode property escapes, ui XML bindings, Java
reflection and class loading, images.resize arguments, and the script looper cases that need neither accessibility nor
the overlay permission. Run it on API 24 as well, the release the Android fallbacks of Rhino exist for.

With --install the debug APK and its test APK built by :app:assembleAppDebug :app:assembleAppDebugAndroidTest are
installed first (adb install -r -t, data is kept); the script prints the md5 of the installed host package before and
after, so the original package can be put back and compared. Without --install the installed packages are used.
"""
import argparse
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
APP_APK_DIR = ROOT / "app/build/outputs/apk/app/debug"
TEST_APK = ROOT / "app/build/outputs/apk/androidTest/app/debug/app-app-debug-androidTest.apk"
APP_PACKAGE = "org.autojs.autojs6"
RUNNER = "org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner"
LOOPER = "org.autojs.autojs.core.looper.ScriptLooperCompatibilityDeviceTest"
DEFAULT_CLASSES = [
    "org.autojs.autojs.engine.RhinoConstScopingDeviceTest",
    "org.autojs.autojs.engine.RhinoUnicodePropertyEscapeDeviceTest",
    "org.autojs.autojs.core.ui.widget.JsListViewDataBindingDeviceTest",
    "org.autojs.autojs.runtime.api.augment.images.ImagesResizeRhinoInstrumentationTest",
    "org.autojs.autojs.rhino.KotlinReflectionDeviceTest",
    "org.autojs.autojs.rhino.AndroidClassLoaderRawDexDeviceTest",
] + [
    f"{LOOPER}#{name}"
    for name in (
        "timersAndEventsFinishAfterCallbackRemoval",
        "cancellingFutureCallbacksLetsTheScriptExit",
        "cancellationFromAWorkerWakesTheMainLooper",
        "directPostsAndSelfCancellingIntervalsFinish",
        "scriptThreadPoolInitializesWorkersInRun",
        "workerTimersAndSleepingThreadCanBeStopped",
        "exitCancelsChildrenAndEmitsExitEvent",
        "mainThreadProxyInterruptStopsExecutingScript",
        "intervalsRemainLiveDuringSustainedDispatch",
    )
]


def adb(serial, *args, check=True, timeout=600):
    done = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    if check and done.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {(done.stderr or done.stdout).strip()[:300]}")
    return done.stdout


def installed_md5(serial):
    paths = [line.split(":", 1)[1].strip() for line in adb(serial, "shell", "pm", "path", APP_PACKAGE, check=False).splitlines() if line.startswith("package:")]
    base = next((p for p in paths if p.endswith("base.apk")), None)
    if base is None:
        return "not installed"
    out = adb(serial, "shell", "md5sum", base, check=False).split()
    return out[0][:8] if out else "unknown"


def app_apk(serial):
    abi = adb(serial, "shell", "getprop", "ro.product.cpu.abi").strip()
    candidates = sorted(APP_APK_DIR.glob(f"*-{abi}.apk")) or sorted(APP_APK_DIR.glob("*-universal.apk"))
    if not candidates:
        raise RuntimeError(f"no debug APK for {abi} in {APP_APK_DIR}")
    return candidates[0]


def run(serial, classes):
    out = adb(serial, "shell", "am", "instrument", "-w", "-r", "-e", "class", ",".join(classes), RUNNER, timeout=3600)
    results, current, stack = [], {}, {}
    for line in out.splitlines():
        if m := re.match(r"INSTRUMENTATION_STATUS: (class|test|stack)=(.*)", line):
            current[m.group(1)] = m.group(2)
        elif m := re.match(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)", line):
            code = int(m.group(1))
            name = f"{current.get('class', '?').rsplit('.', 1)[-1]}#{current.get('test', '?')}"
            if code != 1:
                results.append((name, {0: "PASS", -2: "FAIL", -1: "ERROR", -3: "SKIP", -4: "SKIP"}.get(code, f"CODE {code}"), current.get("stack", "")))
            current = {}
    crashed = "INSTRUMENTATION_RESULT: shortMsg" in out or "Process crashed" in out
    return results, crashed, out


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", action="append", required=True)
    parser.add_argument("--install", action="store_true", help="install the debug APK and its test APK first")
    parser.add_argument("--class", dest="classes", action="append", help="test class or Class#method (default: the Rhino set)")
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")
    failed = 0
    for serial in args.serial:
        sdk = adb(serial, "shell", "getprop", "ro.build.version.sdk").strip()
        print(f"== {serial} (API {sdk}) host md5 before: {installed_md5(serial)}")
        if args.install:
            for apk in (app_apk(serial), TEST_APK):
                adb(serial, "install", "-r", "-t", str(apk))
                print(f"installed {apk.name}")
            print(f"host md5 after install: {installed_md5(serial)}")
        results, crashed, raw = run(serial, args.classes or DEFAULT_CLASSES)
        for name, status, trace in results:
            first = trace.strip().splitlines()[0][:200] if trace.strip() else ""
            print(f"{status:5} {name}" + (f"  {first}" if status in ("FAIL", "ERROR") else ""))
        counts = {s: sum(1 for _, st, _ in results if st == s) for s in ("PASS", "FAIL", "ERROR", "SKIP")}
        print(f"summary {serial}: " + ", ".join(f"{k.lower()} {v}" for k, v in counts.items()) + (", instrumentation crashed" if crashed else ""))
        if not results:
            print(raw.strip()[-600:])
        failed += counts["FAIL"] + counts["ERROR"] + (1 if crashed or not results else 0)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
