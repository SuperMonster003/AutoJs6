"""Checks on a device that a plugin binding the system refused is released (docs/memory-phase2/ROADMAP.md C43).

Usage: py -3.12 -I -u tools/memory/bind_release_check.py -s SERIAL [--package org.autojs.autojs6] [--evidence DIR]

Runs PluginBindReleaseDeviceTest of the installed debug host and its test APK: the test disables the installed DEX compiler
plugin (`pm disable-user`) so that the system refuses every binding to it, binds once raw (the control: the refused
connection stays registered), once through PluginAutoWake.bindServiceWaking and once through the DEX compiler transport, and
enables the plugin again. A connection still registered is found by unbinding it (`unbindService` throws
IllegalArgumentException for one that is not); the transport case also requires that `dumpsys activity services` shows no
connection to the plugin. This script then makes sure the plugin is enabled again (it enables it when an interrupted run left
it disabled) and that nothing still binds to it.

The script never installs APKs and never taps the screen. Output: one line per test (PASS / FAIL / SKIP) and the plugin
state; with --evidence, c43-bind-release-<model>-<versionCode>-<yyyyMMdd>.md is written there. Exit code 0 when every test
passed.
"""
import argparse
import datetime
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "sdk37"))
import physical_device  # noqa: E402

TEST_CLASS = "org.autojs.autojs.core.plugin.PluginBindReleaseDeviceTest"
TESTS = ("theSystemKeepsARefusedConnectionRegistered", "bindServiceWakingUnbindsARefusedConnection",
         "aTransportUnbindsItsRefusedConnection")
PLUGIN = "io.github.supermonster003.autojs6.plugin.dexcompiler"


def outcomes(text):
    """Outcome of each test of the run: pass, fail or skipped."""
    result, current = {}, None
    for line in text.splitlines():
        test = re.match(r"INSTRUMENTATION_STATUS: test=(\S+)", line)
        if test:
            current = test[1]
        code = re.fullmatch(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)", line.strip())
        if code and current:
            number = int(code[1])
            if number != 1:
                result[current] = {0: "pass", -3: "skipped", -4: "skipped"}.get(number, "fail")
    return result


def disabled(device):
    return PLUGIN in device.shell("pm", "list", "packages", "-d")["stdout"].split()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--package", type=physical_device.package_name, default="org.autojs.autojs6")
    parser.add_argument("--evidence", type=Path, help="Directory for the evidence (e.g. docs/memory-phase2/evidence)")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()

    device = physical_device.Device(args.adb, args.serial)
    device.require_online()
    host = device.shell("dumpsys", "package", args.package)["stdout"]
    version = re.search(r"versionCode=(\d+)", host)
    if not version or not re.search(r"(?:pkgFlags|flags)=\[[^\]]*\bDEBUGGABLE\b", host):
        raise RuntimeError(f"Install the debug build of {args.package} first")
    runner = f"{args.package}.test/androidx.test.runner.AndroidJUnitRunner"
    if f"instrumentation:{runner} (target={args.package})" not in device.shell("pm", "list", "instrumentation")["stdout"]:
        raise RuntimeError("Install the matching test APK (app-*-debug-androidTest.apk) first")
    plugin = re.search(r"versionCode=(\d+)", device.shell("dumpsys", "package", PLUGIN)["stdout"])
    if not plugin:
        raise RuntimeError(f"{PLUGIN} is not installed; the test needs a plugin to disable")
    if disabled(device):
        raise RuntimeError(f"{PLUGIN} is disabled already; enable it first so that the run can restore it")

    result = device.shell("am", "instrument", "-w", "-r", "-e", "class", TEST_CLASS, runner, timeout=args.timeout)
    log = result["stdout"] + result["stderr"]
    found = outcomes(log)
    restored = False
    if disabled(device):
        device.shell("pm", "enable", "--user", "0", PLUGIN)
        restored = True
    held = device.shell("dumpsys", "activity", "services", PLUGIN)["stdout"].count("ConnectionRecord")

    rows = [(test, found.get(test, "missing")) for test in TESTS]
    crashed = any(marker in log for marker in ("Process crashed.", "INSTRUMENTATION_FAILED:")) or result.get("timeout")
    ok = not crashed and all(outcome == "pass" for _, outcome in rows) and not disabled(device)
    for test, outcome in rows:
        print(f"{'PASS' if outcome == 'pass' else 'SKIP' if outcome == 'skipped' else 'FAIL'} {test} ({outcome})")
    print(f"plugin {PLUGIN} versionCode {plugin[1]}: enabled{' (re-enabled by this script)' if restored else ''}, "
          f"connections held {held}; host versionCode {version[1]}{'; instrumentation crashed or timed out' if crashed else ''}")
    print("RESULT", "PASS" if ok else "FAIL")

    if args.evidence:
        identity = device.identity()
        model = re.sub(r"[^A-Za-z0-9_.-]", "_", identity.get("model") or "device")
        today = datetime.date.today().strftime("%Y%m%d")
        lines = [f"# C43 refused plugin bindings: {model} ({args.serial}), host {version[1]}, {today}", "",
                 f"- Device: {identity.get('manufacturer')} {model}, Android {identity.get('release')} (API {identity.get('sdk')})",
                 f"- Plugin disabled by the test: {PLUGIN} versionCode {plugin[1]}; enabled afterwards"
                 f"{' (by this script)' if restored else ''}, connections held afterwards: {held}",
                 f"- Test class: {TEST_CLASS} (tools/memory/bind_release_check.py, {physical_device.utc_now()})", "",
                 "| Test | Outcome |", "| --- | --- |", *(f"| {test} | {outcome} |" for test, outcome in rows), "",
                 f"Result: {'PASS' if ok else 'FAIL'}", ""]
        args.evidence.mkdir(parents=True, exist_ok=True)
        path = args.evidence / f"c43-bind-release-{model}-{version[1]}-{today}.md"
        path.write_text("\n".join(lines), encoding="utf-8")
        print("evidence", path.resolve().relative_to(ROOT) if path.resolve().is_relative_to(ROOT) else path)
    return 0 if ok else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, RuntimeError, OSError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(2)
