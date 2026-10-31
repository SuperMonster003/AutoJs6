"""Runs the Rhino related instrumented tests of the host on devices and prints one line per test.

Usage: py -3.12 -I -u tools/rhino/device_rhino_check.py -s SERIAL [-s SERIAL ...] [--install] [--class NAME ...] [--inrt]

Meant for a Rhino jar update (Rhino-For-AutoJs6 AGENTS.md, section 5): the production script engine, block scoping and
the Uint8Array Base64 / hex methods (RhinoConstScopingDeviceTest), class syntax and classes that extend Java classes
(RhinoClassSyntaxDeviceTest), Unicode property escapes, ui XML bindings, Java reflection and class loading, images.resize
arguments, and the script looper cases that need neither accessibility nor the overlay permission. Run it on API 24 as
well, the release the Android fallbacks of Rhino exist for.

With --install the debug APK and its test APK built by :app:assembleAppDebug :app:assembleAppDebugAndroidTest are
installed first (adb install -r -t, data is kept); the script prints the md5 of the installed host package before and
after, so the original package can be put back and compared. Without --install the installed packages are used.

With --inrt the cases of RhinoClassSyntaxDeviceTest run in the packaged path instead: the inrt debug APK built by
:app:assembleInrtDebug gets them as its project (assets/project), is signed with the key of the host (sign.properties,
inrt declares a signature-level permission shared with the host), installed, started and uninstalled again; the results
come back as logcat lines of the tag RhinoClassCase. An inrt app that was installed before is replaced and not put back.
"""
import argparse
import importlib.util
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
APP_APK_DIR = ROOT / "app/build/outputs/apk/app/debug"
TEST_APK = ROOT / "app/build/outputs/apk/androidTest/app/debug/app-app-debug-androidTest.apk"
INRT_APK_DIR = ROOT / "app/build/outputs/apk/inrt/debug"
CASES = ROOT / "app/src/androidTest/assets/rhino/class-syntax-cases.js"
UI_CASE = ROOT / "app/src/androidTest/assets/rhino/class-syntax-ui-case.js"
BUILD_TOOLS = pathlib.Path(os.environ.get("ANDROID_HOME", "E:/.android/sdk")) / "build-tools/37.0.0"
INRT_PACKAGE = "org.autojs.autojs6.inrt"
INRT_TAG = "RhinoClassCase"
INRT_TIMEOUT_SECONDS = 180
APP_PACKAGE = "org.autojs.autojs6"
RUNNER = "org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner"
LOOPER = "org.autojs.autojs.core.looper.ScriptLooperCompatibilityDeviceTest"
DEFAULT_CLASSES = [
    "org.autojs.autojs.engine.RhinoConstScopingDeviceTest",
    "org.autojs.autojs.engine.RhinoClassSyntaxDeviceTest",
    "org.autojs.autojs.engine.RhinoUnicodePropertyEscapeDeviceTest",
    "org.autojs.autojs.core.ui.widget.JsListViewDataBindingDeviceTest",
    "org.autojs.autojs.runtime.api.modules.images.ImagesResizeRhinoInstrumentationTest",
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
        if m := re.match(r"INSTRUMENTATION_STATUS: (class|test|stack|rhino_metric)=(.*)", line):
            current[m.group(1)] = m.group(2)
        elif m := re.match(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)", line):
            code = int(m.group(1))
            name = f"{current.get('class', '?').rsplit('.', 1)[-1]}#{current.get('test', '?')}"
            if "rhino_metric" in current:
                print(f"timing {current['rhino_metric']}")
            elif code != 1:
                results.append((name, {0: "PASS", -2: "FAIL", -1: "ERROR", -3: "SKIP", -4: "SKIP"}.get(code, f"CODE {code}"), current.get("stack", "")))
            current = {}
    crashed = "INSTRUMENTATION_RESULT: shortMsg" in out or "Process crashed" in out
    return results, crashed, out


def read_signing_properties():
    """The signing keys of the host build, read by the function of the inrt fixture tool."""
    spec = importlib.util.spec_from_file_location("inrt_fixtures", ROOT / "tools/sdk37/build_inrt_storage_fixtures.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.read_signing_properties(ROOT / "sign.properties")


def packaged_apk(apk, work):
    """The inrt APK with the cases as its project, aligned again and signed with the key of the host."""
    log = f"android.util.Log.i('{INRT_TAG}', "
    main_js = '"ui rhino";\n' + CASES.read_text(encoding="utf-8") + "\n" + UI_CASE.read_text(encoding="utf-8") + f"""
threads.start(function () {{
    var lines = [];
    rhinoClassCases.runAll(function (line) {{ lines.push(line); }});
    function finish(report) {{
        // Written at the end: the log buffer of an API 24 AVD is 256 KB and turns over during the cases.
        lines.concat('viewSubclassInUiMode ' + report, 'DONE').forEach(function (line) {{ {log}line); }});
        ui.finish();
    }}
    ui.run(function () {{
        try {{
            rhinoClassUiCase(finish);
        }} catch (e) {{
            finish('FAIL threw ' + e);
        }}
    }});
}});
"""
    config = {
        "name": "AutoJs6 Rhino class cases", "packageName": INRT_PACKAGE, "versionName": "1.0.0", "versionCode": 1,
        "main": "main.js", "build": {"id": f"rhino-class-{int(time.time())}", "number": 1, "time": 0},
        "launchConfig": {"requiresSharedStorage": False, "splashVisible": False, "logsVisible": False,
                         "launcherVisible": True, "runOnBoot": False},
    }
    unaligned, aligned = work / "unaligned.apk", work / "packaged.apk"
    with zipfile.ZipFile(apk) as source, zipfile.ZipFile(unaligned, "w") as target:
        for item in source.infolist():
            # META-INF/services stays: Rhino finds its XML implementation there.
            signature = item.filename.startswith("META-INF/") and item.filename.upper().endswith((".SF", ".RSA", ".DSA", ".EC", "MANIFEST.MF"))
            if signature or item.filename.startswith("assets/project/"):
                continue
            target.writestr(item, source.read(item.filename))
        target.writestr("assets/project/project.json", json.dumps(config), zipfile.ZIP_DEFLATED)
        target.writestr("assets/project/main.js", main_js, zipfile.ZIP_DEFLATED)
    tool = lambda name: shutil.which(name, path=str(BUILD_TOOLS)) or str(BUILD_TOOLS / name)
    subprocess.run([tool("zipalign"), "-f", "-P", "16", "4", str(unaligned), str(aligned)], check=True, capture_output=True)
    signing = read_signing_properties()
    # Passwords travel through the environment so they never appear on a command line.
    subprocess.run(
        [tool("apksigner.bat"), "sign", "--ks", str(signing["storeFile"]), "--ks-key-alias", signing["keyAlias"],
         "--ks-pass", "env:AJ6_RHINO_KS_PASS", "--key-pass", "env:AJ6_RHINO_KEY_PASS", str(aligned)],
        env=dict(os.environ, AJ6_RHINO_KS_PASS=signing["storePassword"], AJ6_RHINO_KEY_PASS=signing["keyPassword"]),
        check=True, capture_output=True,
    )
    return aligned


def inrt_launcher(serial):
    """The launcher activity of inrt: a debug APK has a second one, that of LeakCanary, which monkey may start instead."""
    out = adb(serial, "shell", "cmd", "package", "query-activities", "--brief", "-a", "android.intent.action.MAIN",
              "-c", "android.intent.category.LAUNCHER", INRT_PACKAGE)
    return next(line.strip() for line in out.splitlines() if line.strip().startswith(INRT_PACKAGE + "/") and "/leakcanary." not in line)


def run_inrt(serial):
    """Installs the packaged cases, starts them and returns the number of failures; the package is uninstalled again."""
    abi = adb(serial, "shell", "getprop", "ro.product.cpu.abi").strip()
    candidates = sorted(INRT_APK_DIR.glob(f"*-{abi}.apk")) or sorted(INRT_APK_DIR.glob("*-universal.apk")) or sorted(INRT_APK_DIR.glob("*.apk"))
    if not candidates:
        raise RuntimeError(f"no inrt APK for {abi} in {INRT_APK_DIR}")
    with tempfile.TemporaryDirectory() as work:
        apk = packaged_apk(candidates[0], pathlib.Path(work))
        print(f"inrt {candidates[0].name} ({os.path.getsize(apk) // (1 << 20)} MB)")
        adb(serial, "install", "-r", "-t", str(apk))
    try:
        since = adb(serial, "shell", "date '+%m-%d %H:%M:%S.000'").strip()
        adb(serial, "shell", "am", "start", "-W", "-n", inrt_launcher(serial))
        deadline = time.time() + INRT_TIMEOUT_SECONDS
        lines = []
        while time.time() < deadline:
            lines = adb(serial, "logcat", "-d", "-v", "raw", "-T", since, "-s", f"{INRT_TAG}:I", check=False).splitlines()
            lines = [line.strip() for line in lines if line.strip() and not line.startswith("---")]
            if "DONE" in lines:
                break
            time.sleep(2)
        reports = [line for line in lines if line != "DONE"]
        failed = 0
        expected = CASES.read_text(encoding="utf-8").count("\n    add('") + 1
        for line in reports:
            name, _, verdict = line.partition(" ")
            print(f"{'PASS' if verdict == 'PASS' else 'FAIL':5} inrt#{name}" + ("" if verdict == "PASS" else f"  {verdict[:300]}"))
            failed += verdict != "PASS"
        passed = len(reports) - failed
        if "DONE" not in lines:
            print(f"FAIL  inrt: no end of the cases in {INRT_TIMEOUT_SECONDS} s")
            failed += 1
        elif len(reports) != expected:
            print(f"FAIL  inrt: {len(reports)} results of {expected} cases")
            failed += 1
        print(f"summary {serial} inrt: pass {passed}, fail {failed}")
        return failed
    finally:
        adb(serial, "shell", "am", "force-stop", INRT_PACKAGE, check=False)
        adb(serial, "uninstall", INRT_PACKAGE, check=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", action="append", required=True)
    parser.add_argument("--install", action="store_true", help="install the debug APK and its test APK first")
    parser.add_argument("--class", dest="classes", action="append", help="test class or Class#method (default: the Rhino set)")
    parser.add_argument("--inrt", action="store_true", help="run the class syntax cases in the inrt debug APK instead")
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")
    failed = 0
    for serial in args.serial:
        sdk = adb(serial, "shell", "getprop", "ro.build.version.sdk").strip()
        print(f"== {serial} (API {sdk}) host md5 before: {installed_md5(serial)}")
        if args.inrt:
            failed += run_inrt(serial)
            continue
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
