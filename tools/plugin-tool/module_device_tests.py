"""Runs the instrumented tests of plugin-api/plugin-tool-api on devices and prints one line per test.

Usage: py -3.12 -I -u tools/plugin-tool/module_device_tests.py -s SERIAL [-s SERIAL ...] [--class NAME] [--no-build] [--keep]

The test APK of a library module holds the module and its tests, so nothing else is installed. The script builds it
(:plugin-api:plugin-tool-api:assembleDebugAndroidTest, unless --no-build), installs it on each device, runs the
instrumentation (the whole module, or --class, for example PluginToolResultTransportTest), prints PASS / FAIL per test
with the first line of a failure, then the key values the tests log under the tag ToolResultTransport (MCP roadmap D65),
and uninstalls the APK again unless --keep.
"""
import argparse
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
APK = ROOT / "plugin-api/plugin-tool-api/build/outputs/apk/androidTest/debug/plugin-tool-api-debug-androidTest.apk"
PACKAGE = "org.autojs.plugin.tool.api.test"
RUNNER = f"{PACKAGE}/androidx.test.runner.AndroidJUnitRunner"
TEST_PACKAGE = "org.autojs.plugin.tool.api"
LOG_TAG = "ToolResultTransport"


def adb(serial, *args, check=True, timeout=600):
    done = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    if check and done.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {(done.stderr or done.stdout).strip()[:300]}")
    return done.stdout


def build():
    gradlew = ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
    done = subprocess.run([str(gradlew), ":plugin-api:plugin-tool-api:assembleDebugAndroidTest", "-q"], cwd=ROOT,
                          capture_output=True, text=True, encoding="utf-8", errors="replace")
    if done.returncode != 0:
        tail = "\n".join((done.stdout + done.stderr).strip().splitlines()[-20:])
        raise RuntimeError(f"build failed:\n{tail}")


def parse(output):
    """Returns [(test, status, first failure line)] from `am instrument -r` output."""
    results = []
    current = {}
    for line in output.splitlines():
        match = re.match(r"INSTRUMENTATION_STATUS: (\w+)=(.*)", line)
        if match:
            current[match.group(1)] = match.group(2)
            continue
        match = re.match(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)", line)
        if match:
            code = int(match.group(1))
            if code != 1 and "test" in current:
                stack = current.get("stack", "")
                results.append((current["test"], code, stack.strip().splitlines()[0] if stack else ""))
            current = {}
    return results


def run(serial, test_class, keep):
    model = adb(serial, "shell", "getprop", "ro.product.model").strip()
    sdk = adb(serial, "shell", "getprop", "ro.build.version.sdk").strip()
    print(f"== {serial} ({model}, API {sdk})")
    adb(serial, "install", "-r", "-t", str(APK))
    try:
        adb(serial, "logcat", "-c", check=False)
        args = ["shell", "am", "instrument", "-w", "-r"]
        if test_class:
            args += ["-e", "class", test_class if "." in test_class else f"{TEST_PACKAGE}.{test_class}"]
        output = adb(serial, *args, RUNNER, check=False, timeout=1800)
        results = parse(output)
        for test, code, failure in results:
            print(f"{'PASS' if code == 0 else 'SKIP' if code in (-3, -4) else 'FAIL'} {test}" + (f": {failure[:300]}" if code not in (0, -3, -4) else ""))
        passed = sum(1 for _, code, _ in results if code == 0)
        failed = sum(1 for _, code, _ in results if code not in (0, -3, -4))
        crashed = "INSTRUMENTATION_RESULT: shortMsg" in output or "Process crashed" in output
        for line in adb(serial, "logcat", "-d", "-v", "raw", "-s", f"{LOG_TAG}:I", check=False).splitlines():
            if line.strip() and not line.startswith("-"):
                print(f"  {line.strip()}")
        print(f"{serial}: {passed} passed, {failed} failed" + (", instrumentation crashed" if crashed else ""))
        return failed == 0 and not crashed and passed > 0
    finally:
        if not keep:
            adb(serial, "uninstall", PACKAGE, check=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("-s", "--serial", action="append", required=True)
    parser.add_argument("--class", dest="test_class")
    parser.add_argument("--no-build", action="store_true")
    parser.add_argument("--keep", action="store_true", help="leave the test APK installed")
    options = parser.parse_args()
    if not options.no_build:
        build()
    ok = all([run(serial, options.test_class, options.keep) for serial in options.serial])
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
