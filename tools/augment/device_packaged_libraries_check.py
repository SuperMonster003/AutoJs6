"""ROADMAP P7.1: packaged apps take the Bundled Libraries modules they use along (design
docs/augment/P7-1-packaged-app-libraries-design.md).

    py -3.12 -I tools/augment/device_packaged_libraries_check.py -s SERIAL --host-apk HOST.apk --test-apk TEST.apk \
        --template-apk TEMPLATE.apk --plugin-apk PLUGIN.apk --work DIR

--template-apk is the APK Builder Template plugin built from a Runtime Kit of the same host (`:app:generateRuntimeKit`,
then that repository with -Pautojs.apkBuilder.templatePlugin.runtimeKitDir); its debug build, so that the original can
be installed back over it. --plugin-apk is a release (official) Bundled Libraries plugin. Steps:

    1. pull the installed host, test package and template plugin to --work, install the given ones
    2. PackagedScriptModulesProducerDeviceTest with the plugin installed: app a (the scan) and c ("Bundled Libraries");
       then with it uninstalled: app b
    3. install the three apps, run each with the plugin uninstalled, then installed, and compare the P71_RESULT line of
       its script with the expectation
    4. uninstall the apps (and the plugin unless it was there before), install the originals back, compare their md5
       and delete the outputs on the device

Prints one line per check with PASS / FAIL and the key values (files, bytes, scan time, the error of a missing library).
"""
import argparse
import hashlib
import json
import pathlib
import re
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import device_bench as db  # noqa: E402

HOST = db.PACKAGE
TEST = f"{HOST}.test"
TEMPLATE = "org.autojs.plugin.apkbuilder.template"
PLUGIN = "io.github.supermonster003.autojs6.plugin.bundled.libraries"
APPS = {app: f"{HOST}.p71{app}" for app in "abc"}
CATALOG = db.HERE.parent.parent / "app/src/test/resources/script-modules/bundled-libraries-1.0.0.json"
RUNNER = f"{TEST}/androidx.test.runner.AndroidJUnitRunner"
PRODUCER = "org.autojs.autojs.apkbuilder.template.PackagedScriptModulesProducerDeviceTest"
OUTPUTS = f"/sdcard/Android/data/{HOST}/files"
PRODUCER_RE = re.compile(r"P71_PRODUCER app=(\w) bytes=(\d+) modules=(\[.*?\]) notices=(\[.*\])$")
SCAN_RE = re.compile(r"PackagedScriptModules: scan (\d+) ms(?:, plan (\d+) ms)?")

ALL = [library["id"] for library in json.loads(CATALOG.read_text("utf-8"))["libraries"]]
# What the scan finds in the script, with the libraries fs requires. zh-CN: 扫描在脚本中找到的库, 以及 fs require 的库.
USED = ["dayjs", "pangu", "lodash", "fs"]
COPIED = {"a": ["dayjs", "pangu", "lodash", "buffer", "fs", "path"], "b": [], "c": ALL}
OK = {
    "require-dayjs": "ok 2026/10/29", "getter-dayjs": "ok 2026", "require-lodash": "ok 2,4", "getter-pangu": "ok true",
    "require-fs": "ok function", "require-os": "ok function",
}
LIBRARY = {"require-dayjs": "dayjs", "getter-dayjs": "dayjs", "require-lodash": "lodash", "getter-pangu": "pangu",
           "require-fs": "fs", "require-os": "os"}
# The packaged app has no Plugin Center: its messages must not send the user there (roadmap Q188).
# zh-CN: 打包应用没有插件中心, 其消息不应指向那里 (Q188).
PLUGIN_CENTER = ["Plugin center", "插件中心", "外掛程式中心", "外掛中心"]


def library_of(path):
    for library in json.loads(CATALOG.read_text("utf-8"))["libraries"]:
        if any(path.startswith(f) if f.endswith("/") else path == f for f in library["files"]):
            return library["id"]
    return None


def installed(serial, package):
    return f"package:{package}" in db.adb(serial, "shell", "pm", "list", "packages", package).stdout.split()


def device_md5(serial, package):
    paths = [l.removeprefix("package:").strip() for l in db.adb(serial, "shell", "pm", "path", package).stdout.splitlines() if l.startswith("package:")]
    return sorted(db.adb(serial, "shell", "md5sum", p).stdout.split()[0] for p in paths)


def install(serial, apk, *flags):
    out = db.adb(serial, "install", "-r", "-t", *flags, str(apk))
    if "Success" not in out.stdout:
        sys.exit(f"install {pathlib.Path(apk).name}: {(out.stdout + out.stderr).strip()[-300:]}")


def backup(serial, work):
    """{package: (local apk, md5)} of the installed host, test package and template plugin."""
    saved = {}
    for package in (HOST, TEST, TEMPLATE):
        if not installed(serial, package):
            saved[package] = None
            continue
        paths = [l.removeprefix("package:").strip() for l in db.adb(serial, "shell", "pm", "path", package).stdout.splitlines() if l.startswith("package:")]
        if len(paths) != 1:
            sys.exit(f"{package}: split APKs are not handled ({len(paths)})")
        local = work / f"original-{package}.apk"
        db.adb(serial, "pull", paths[0], str(local), check=True)
        md5 = hashlib.md5(local.read_bytes()).hexdigest()
        if [md5] != device_md5(serial, package):
            sys.exit(f"{package}: the pulled APK differs from the installed one")
        saved[package] = (local, md5)
    return saved


def produce(serial, apps):
    db.clear_log(serial)
    out = db.adb(serial, "shell", "am", "instrument", "-w", "-r", "-e", "class", PRODUCER, "-e", "autojs.p71.apps", ",".join(apps), RUNNER).stdout
    if "OK (1 test)" not in out:
        sys.exit(f"producer {apps}: {out.strip()[-800:]}")
    log = db.adb(serial, "logcat", "-d", "-t", str(db.LOG_TAIL)).stdout.splitlines()
    results = {}
    for line in log:
        match = PRODUCER_RE.search(line)
        if match:
            results[match.group(1)] = (int(match.group(2)), json.loads(match.group(3)), json.loads(match.group(4)))
    scans = [(int(m.group(1)), int(m.group(2) or 0)) for m in (SCAN_RE.search(l) for l in log) if m]
    return results, scans


def check_producer(app, result, scan, failures):
    size, modules, notices = result
    libraries = list(dict.fromkeys(library_of(m) for m in modules if library_of(m)))
    others = [m for m in modules if not library_of(m)]
    wanted = COPIED[app]
    problems = []
    if sorted(libraries) != sorted(wanted):
        problems.append(f"libraries {libraries} != {wanted}")
    if len(notices) != 1:
        problems.append(f"{len(notices)} notices")
    listed = ", ".join(wanted if wanted else USED)
    if notices and listed not in notices[0]:
        problems.append(f"notice does not list {listed}")
    status = "FAIL" if problems else "PASS"
    failures += [f"producer {app}: {p}" for p in problems]
    timing = f" scan {scan[0]} ms, plan {scan[1]} ms" if scan else ""
    print(f"producer {app}: {status} libraries={len(libraries)} files={len(modules) - len(others)} other-files={len(others)} bytes={size}{timing}")
    print(f"  notice: {notices[0] if notices else '-'}")


def run_app(serial, app, wait=45):
    package = APPS[app]
    db.adb(serial, "shell", "am", "force-stop", package)
    db.clear_log(serial)
    db.adb(serial, "shell", "monkey", "-p", package, "-c", "android.intent.category.LAUNCHER", "1")
    deadline = time.time() + wait
    while time.time() < deadline:
        time.sleep(1)
        for line in db.console_lines(serial):
            start = line.find("P71_RESULT ")
            if start >= 0:
                db.adb(serial, "shell", "am", "force-stop", package)
                return json.loads(line[start + len("P71_RESULT "):])
    db.adb(serial, "shell", "am", "force-stop", package)
    return None


def expected(app, plugin):
    """{item: "ok ..." or (library, reason)} for [app] with the plugin installed or not."""
    if plugin:
        return dict(OK)
    copied = COPIED[app]
    return {item: OK[item] if LIBRARY[item] in copied else (LIBRARY[item], "not-installed") for item in OK}


def check_run(app, plugin, results, failures):
    state = "plugin" if plugin else "no-plugin"
    if results is None:
        failures.append(f"run {app} {state}: no P71_RESULT")
        print(f"run {app} {state}: FAIL no P71_RESULT")
        return
    problems = []
    errors = []
    for item, want in expected(app, plugin).items():
        got = results.get(item, "")
        if isinstance(want, str):
            if got != want:
                problems.append(f"{item}: {got[:160]}")
            continue
        parts = [p.strip() for p in got.split(" | ")]
        library, reason = want
        name = item.split("-", 1)[1]
        if parts[:4] != ["throws", "MODULE_NOT_FOUND", library, reason]:
            problems.append(f"{item}: {got[:160]}")
        # The Node core modules (lodash, fs, os ...) are named as jvm-npm names them, with ".js" (roadmap Q189).
        # zh-CN: Node 核心模块 (lodash, fs, os 等) 按 jvm-npm 的方式带 ".js" (Q189).
        elif any(word in parts[4] for word in PLUGIN_CENTER) or f'"{library}"' not in parts[4] \
                or not any(f"'{name}{ext}'" in parts[4] for ext in ("", ".js")):
            problems.append(f"{item}: message {parts[4][:160]}")
        else:
            errors.append(f"{item}: {parts[4]}")
    failures += [f"run {app} {state}: {p}" for p in problems]
    ok = sum(1 for v in results.values() if v.startswith("ok "))
    print(f"run {app} {state}: {'FAIL' if problems else 'PASS'} ok={ok} throws={len(results) - ok}")
    for problem in problems:
        print(f"  {problem}")
    for error in errors[:1]:
        print(f"  first error: {error}")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--host-apk", required=True)
    parser.add_argument("--test-apk", required=True)
    parser.add_argument("--template-apk", required=True)
    parser.add_argument("--plugin-apk", required=True)
    parser.add_argument("--work", required=True, help="local directory for the backups and the produced apps")
    args = parser.parse_args()
    serial = args.serial
    work = pathlib.Path(args.work)
    work.mkdir(parents=True, exist_ok=True)
    if any(installed(serial, package) for package in APPS.values()):
        sys.exit("a P71 app is installed already")

    plugin_before = installed(serial, PLUGIN)
    a11y_before = db.adb(serial, "shell", "settings", "get", "secure", "enabled_accessibility_services").stdout.strip()
    saved = backup(serial, work)
    print("backup: " + ", ".join(f"{p} {s[1][:8] if s else '-'}" for p, s in saved.items()) + f", plugin before: {plugin_before}")
    failures = []
    try:
        install(serial, args.host_apk)
        install(serial, args.test_apk)
        install(serial, args.template_apk)
        install(serial, args.plugin_apk)

        produced, scans = produce(serial, ["a", "c"])
        db.adb(serial, "uninstall", PLUGIN)
        produced_b, scans_b = produce(serial, ["b"])
        produced.update(produced_b)
        # One scan line per build, in the order of the apps. zh-CN: 每次构建一行扫描记录, 顺序同应用.
        timings = {"a": scans[0] if scans else None, "c": scans[1] if len(scans) > 1 else None, "b": scans_b[0] if scans_b else None}
        for app in "abc":
            if app not in produced:
                sys.exit(f"producer {app}: no P71_PRODUCER line")
            check_producer(app, produced[app], timings[app], failures)
            local = work / f"p71-{app}.apk"
            db.adb(serial, "pull", f"{OUTPUTS}/p71-{app}.apk", str(local), check=True)
            install(serial, local)

        for plugin in (False, True):
            if plugin:
                install(serial, args.plugin_apk)
            for app in "abc":
                check_run(app, plugin, run_app(serial, app), failures)
    finally:
        for package in APPS.values():
            db.adb(serial, "uninstall", package)
        if not plugin_before:
            db.adb(serial, "uninstall", PLUGIN)
        db.adb(serial, "shell", "rm", "-f", *(f"{OUTPUTS}/p71-{app}.apk" for app in "abc"))
        restored = []
        for package, entry in saved.items():
            if entry is None:
                db.adb(serial, "uninstall", package)
                restored.append(f"{package} removed")
                continue
            install(serial, entry[0], "-d")
            same = device_md5(serial, package) == [entry[1]]
            restored.append(f"{package} {'md5 ok' if same else 'MD5 DIFFERS'}")
            if not same:
                failures.append(f"restore {package}: md5 differs")
        print("restore: " + ", ".join(restored))
        a11y_after = db.adb(serial, "shell", "settings", "get", "secure", "enabled_accessibility_services").stdout.strip()
        if a11y_after != a11y_before:
            # Reinstalling may drop an enabled service; turning it on again is left to the user.
            # zh-CN: 重装可能使已启用的无障碍服务失效, 由维护者重新开启.
            print(f"note: enabled accessibility services changed ({len(a11y_before.split(':'))} -> {len(a11y_after.split(':'))}), turn them on again on the device")
    print(f"P7.1 device check: {'FAIL' if failures else 'PASS'} ({len(failures)} failures)")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
