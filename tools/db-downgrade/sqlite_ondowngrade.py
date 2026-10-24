"""Device check of the script sqlite module's onDowngrade (docs/db-downgrade/ROADMAP.md P3.1, D6 / T5).

Usage:
  py -3.12 -I -u tools/db-downgrade/sqlite_ondowngrade.py -s SERIAL [--package PKG] [--label sqlite] [--report-dir DIR] [--keep]

Runs sqlite_ondowngrade_probe.js in the installed debug host through RunIntentActivity. The probe lives in a directory
of the host's own files (pushed to /data/local/tmp, then copied there with run-as), so the host needs no storage
permission; it writes result.json next to itself, which is read back with run-as. Cases:
  plain     no callback: a database at version 2 opened with version 1 does not throw, is at version 1 and keeps its
            rows; opening it with version 2 again calls onUpgrade 1 -> 2.
  partial   a callback object without onDowngrade: the same, and its onOpen still runs (onCreate does not).
  callback  onDowngrade(database, oldVersion, newVersion) is called once with 3 -> 2 (numbers, the Database itself, still at
            version 3 and inside the helper's transaction), before onOpen and without onCreate / onUpgrade; the row it
            inserts is kept and the file ends at version 2.
  throw     an exception from onDowngrade is thrown by sqlite.open, and the file keeps version 2 and its rows (the row the
            callback inserted before throwing is rolled back).
  readonly  { version: 1, readOnly: true } on a version 2 file behaves as plain.
The screen must be on (RunIntentActivity starts an activity) and the host needs storage access, which RunIntentActivity
checks before it runs any path (all files access on API 30+, READ_EXTERNAL_STORAGE below; on a temporary AVD
`appops set --uid PKG MANAGE_EXTERNAL_STORAGE allow` or `pm grant PKG android.permission.READ_EXTERNAL_STORAGE`). Nothing
is tapped. The pushed files are removed afterwards unless --keep is given.

Report: <report-dir>/roundtrip-<model or AVD>-<versionCode>-<yyyyMMdd>-<label>.md, one section appended per run, PASS /
FAIL per case with versions, row counts and callback events only (ROADMAP R4).
"""
import argparse
import datetime
import json
from pathlib import Path
import re
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
PROBE = Path(__file__).resolve().parent / "sqlite_ondowngrade_probe.js"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
TMP = "/data/local/tmp/aj6-db-downgrade-p3.js"
APP_DIR = "files/aj6-db-downgrade-p3"


class Device:

    def __init__(self, serial, package):
        self.serial = serial
        self.package = package

    def adb(self, *args, timeout=60):
        result = subprocess.run(["adb", "-s", self.serial, *args], capture_output=True, timeout=timeout)
        return result.returncode, result.stdout.decode("utf-8", "replace").replace("\r\n", "\n")

    def shell(self, command, timeout=60):
        return self.adb("shell", command, timeout=timeout)[1]

    def run_as(self, command):
        return self.adb("shell", f"run-as {self.package} sh -c '{command}'")

    def prop(self, name):
        return self.shell(f"getprop {name}").strip()

    def label(self):
        if self.serial.startswith("emulator-"):
            code, out = self.adb("emu", "avd", "name")
            name = out.splitlines()[0].strip() if code == 0 and out.strip() else ""
            if name:
                return name
        return self.prop("ro.product.model").replace(" ", "_") or self.serial

    def storage_access(self):
        """What RunIntentActivity's PathChecker asks for before it runs a path."""
        if int(self.prop("ro.build.version.sdk") or 0) >= 30:
            return "allow" in self.shell(f"appops get --uid {self.package} MANAGE_EXTERNAL_STORAGE")
        return "android.permission.READ_EXTERNAL_STORAGE: granted=true" in self.shell(f"dumpsys package {self.package}")

    def version(self):
        out = self.shell(f"dumpsys package {self.package}")
        code = next((line.split("versionCode=")[1].split()[0] for line in out.splitlines() if "versionCode=" in line), None)
        name = next((line.split("versionName=")[1].strip() for line in out.splitlines() if "versionName=" in line), "?")
        return code, name


def check(cases):
    """(name, ok, values) of every case."""
    items = []

    def item(name, case, ok, values):
        error = case.get("error")
        if error:
            items.append((name, False, f"error: {error}"))
        else:
            items.append((name, bool(ok), values))

    plain = cases.get("plain", {})
    item("plain: 无回调 2 -> 1", plain,
         plain.get("seeded") == 2 and plain.get("version") == 1 and plain.get("rows") == 2
         and plain.get("upgrades") == ["1->2"] and plain.get("reopened") == 2,
         f"建库 v{plain.get('seeded')}, 打开 v1 后 v{plain.get('version')} / 行 {plain.get('rows')}; "
         f"再以 v2 打开 onUpgrade {plain.get('upgrades')}, v{plain.get('reopened')}")
    partial = cases.get("partial", {})
    item("partial: 回调无 onDowngrade", partial,
         partial.get("version") == 1 and partial.get("rows") == 2 and partial.get("events") == ["open"],
         f"v{partial.get('version')} / 行 {partial.get('rows')}, 事件 {partial.get('events')}")
    callback = cases.get("callback", {})
    item("callback: onDowngrade 参数与顺序", callback,
         callback.get("events") == ["downgrade 3->2", "open"] and callback.get("argCount") == 3
         and callback.get("argTypes") == ["number", "number"]
         and callback.get("databaseClass") == "org.autojs.autojs.core.database.Database"
         and callback.get("versionInside") == 3 and callback.get("inTransaction") is True
         and callback.get("versionInOpen") == 2 and callback.get("version") == 2 and callback.get("rows") == 3,
         f"事件 {callback.get('events')}, 参数 {callback.get('argCount')} 个 {callback.get('argTypes')}, "
         f"database {callback.get('databaseClass')}, 回调内 v{callback.get('versionInside')} 事务 {callback.get('inTransaction')}, "
         f"onOpen 内 v{callback.get('versionInOpen')}, 打开后 v{callback.get('version')} / 行 {callback.get('rows')}")
    thrown = cases.get("throw", {})
    # The message ends with the probe's location, " (/data/.../probe.js#142)".
    thrown["message"] = re.sub(r" \(/[^)]*\)", "", thrown.get("message") or "")
    item("throw: onDowngrade 抛出", thrown,
         thrown.get("thrown") is True and "p3 refuses the downgrade" in (thrown.get("message") or "")
         and thrown.get("version") == 2 and thrown.get("rows") == 2,
         f"抛出 {thrown.get('thrown')} ({thrown.get('message')}), 之后 v{thrown.get('version')} / 行 {thrown.get('rows')}")
    readonly = cases.get("readonly", {})
    item("readonly: readOnly 2 -> 1", readonly,
         readonly.get("version") == 1 and readonly.get("rows") == 2,
         f"v{readonly.get('version')} / 行 {readonly.get('rows')}")
    return items


def write_report(args, dev, items, meta):
    code, name = dev.version()
    folder = Path(args.report_dir)
    if not folder.is_absolute():
        folder = ROOT / folder
    folder.mkdir(parents=True, exist_ok=True)
    device = dev.label()
    date = datetime.date.today().strftime("%Y%m%d")
    path = folder / f"roundtrip-{device}-{code}-{date}-{args.label}.md"
    verdict = "PASS" if items and all(ok for _, ok, _ in items) else "FAIL"
    lines = []
    if not path.exists():
        lines += [f"# 脚本 sqlite 模块 onDowngrade: {device} / {name} ({code})", "",
                  f"- 设备: {device} ({dev.serial}), Android {dev.prop('ro.build.version.release')} / API {dev.prop('ro.build.version.sdk')}",
                  "- 脚本: `tools/db-downgrade/sqlite_ondowngrade.py` + `sqlite_ondowngrade_probe.js` (ROADMAP P3.1, D6 / T5)",
                  "- 每节一次运行, 只记通过 / 失败与版本号, 行数, 回调事件 (R4)", ""]
    lines += [f"## {datetime.datetime.now().strftime('%H:%M:%S')} {dev.package} {verdict}", ""]
    lines += [f"- {text}" for text in meta] + [""]
    lines += ["| 项 | 结果 | 数值 |", "| --- | --- | --- |"]
    for item_name, ok, values in items:
        lines.append(f"| {item_name} | {'PASS' if ok else 'FAIL'} | {str(values).replace('|', '/')} |")
    lines.append("")
    with path.open("a", encoding="utf-8", newline="\n") as stream:
        stream.write("\n".join(lines) + "\n")
    print(f"{verdict} -> {path.relative_to(ROOT) if path.is_relative_to(ROOT) else path}")
    return verdict == "PASS"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--package", default="org.autojs.autojs6")
    parser.add_argument("--label", default="sqlite")
    parser.add_argument("--report-dir", default="docs/db-downgrade/evidence")
    parser.add_argument("--wait", type=int, default=90, help="seconds to wait for result.json")
    parser.add_argument("--keep", action="store_true", help="keep the pushed probe and its directory")
    args = parser.parse_args()
    dev = Device(args.serial, args.package)

    code, _ = dev.version()
    if code is None:
        sys.exit(f"{args.package} is not installed on {args.serial}")
    if dev.run_as("pwd")[0] != 0:
        sys.exit(f"{args.package} is not debuggable (run-as failed)")
    if "mWakefulness=Awake" not in dev.shell("dumpsys power"):
        sys.exit("Turn the screen on first")
    if not dev.storage_access():
        sys.exit(f"{args.package} has no storage access; RunIntentActivity runs no path without it")
    home = dev.run_as("pwd")[1].strip()

    dev.adb("push", str(PROBE), TMP, timeout=60)
    dev.run_as(f"rm -rf {APP_DIR} && mkdir -p {APP_DIR} && cat {TMP} > {APP_DIR}/probe.js")
    started = time.time()
    dev.shell(f"am start -n {args.package}/{RUN_ACTIVITY} -e path {home}/{APP_DIR}/probe.js")
    result = None
    while time.time() - started < args.wait:
        time.sleep(2)
        status, out = dev.run_as(f"cat {APP_DIR}/result.json")
        if status == 0 and out.strip().startswith("{"):
            result = json.loads(out)
            break
    elapsed = time.time() - started
    if not args.keep:
        dev.run_as(f"rm -rf {APP_DIR}")
        dev.shell(f"rm -f {TMP}")
    if result is None:
        items = [("probe result", False, f"no result.json within {args.wait} s")]
    else:
        items = check(result["cases"])
    for item_name, ok, values in items:
        print(f"{'PASS' if ok else 'FAIL'} {item_name}: {values}")
    meta = [f"probe 结果 {elapsed:.0f} s 内取得" if result is not None else "probe 无结果",
            f"设备文件 {'保留' if args.keep else '已删除'} ({APP_DIR}, {TMP})"]
    return 0 if write_report(args, dev, items, meta) else 1


if __name__ == "__main__":
    sys.exit(main())
