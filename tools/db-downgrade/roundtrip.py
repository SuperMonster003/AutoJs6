"""Database downgrade round trip (docs/db-downgrade/ROADMAP.md P0.3, T6).

The host and the packaged app (inrt) open two SQLiteOpenHelper databases while App.onCreate runs: TimedTaskDatabase.db
(table TimedTask) and IntentTaskDatabase.db (table IntentTask). A build whose code version is lower than the version of
the file used to throw "Can't downgrade database from version X to Y" there, so the process died at start and whoever
had started it (the Shizuku server's provider call, WorkManager, a sticky service...) started it again. A fix only
protects the build that carries it, so the round trip builds "future data" for the installed build instead of
installing an older release on newer data (ROADMAP 1.4, T6).

Subcommands (all but forensics need a debuggable build, for run-as):
  make-future    Pulls both databases (with -wal / -shm / -journal) and, on the PC with Python sqlite3, seeds marker
                 rows, adds a column and a table and sets user_version to the code version + 1; pushes the single file
                 back and removes the device's -wal / -shm / -journal. The device needs no sqlite3.
  check          Starts the app (--start: its launcher activity, or an explicit broadcast that only starts the process),
                 observes --observe seconds, force-stops it and reads the databases.
                 PASS: alive at the end, no crash and no second process start logged, user_version equals the code
                 version, all marker rows there, the future column and table kept.
  inject-fail    Moves each database aside, puts a directory of the same name in its place and starts the app. PASS: no
                 crash, a <name>.bak-<version>-<yyyyMMddHHmmss> entry appeared, the new database is at the code version
                 and (host) the recovery notification was posted. The moved database is put back afterwards.
  restore-check  Renames each database to a backup named as DatabaseRecovery names it, then (a) starts the app without
                 reinstalling: not restored; (b) reinstalls --apk and starts: restored with all marker rows; (c) renames
                 it to a backup of code version + 1, reinstalls and starts: not restored (puts it back afterwards).
  wake           Restarts an adb-started Shizuku server --rounds times; every round counts the process starts and the
                 crashes of each --package in --observe seconds, then force-stops it (before the fix the loop does not
                 end by itself).
  suite          make-future, check, inject-fail and restore-check in a row.
  forensics      Read only, for a device whose app must stay as it is (XQ-AT72): dropbox crash entries, package facts,
                 bad processes, jobs, alarms and process starts in the event log, as counts and first lines.

Report: <report-dir>/roundtrip-<model or AVD>-<versionCode>-<yyyyMMdd>[-<label>].md, one section appended per run. Every
item is PASS / FAIL with counts, versions, row numbers and file names only (ROADMAP R4): no logcat text beyond the first
line of an exception, no database content, no task script paths.
"""
import argparse
import datetime
import hashlib
from pathlib import Path
import re
import sqlite3
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[2]
SOURCE_DIR = ROOT / "app/src/main/java/org/autojs/autojs/storage/database"
DEFAULT_PACKAGE = "org.autojs.autojs6"
DEVICE_TMP = "/data/local/tmp/aj6-db-downgrade"
# Marker rows point at a script that never exists and run once in 2099, so no scheduler ever starts them.
# zh-CN: 标记行指向不存在的脚本, 只在 2099 年运行一次, 调度器不会启动它们.
MARKER_PREFIX = "/sdcard/aj6-db-downgrade/marker-"
MARKER_ACTION = "org.autojs.autojs6.dbdowngrade.NOOP"
FAR_FUTURE_MILLIS = 4070908800000
FUTURE_COLUMN = "roundtrip_future_note"
FUTURE_TABLE = "roundtrip_future_extra"
COMPANIONS = ("-wal", "-shm", "-journal")
# Must match DatabaseRecovery: <file>.bak-<file version>-<yyyyMMddHHmmss>, the log tag and the notification tag.
# zh-CN: 须与 DatabaseRecovery 一致: <文件>.bak-<文件版本>-<yyyyMMddHHmmss>, 日志标签与通知标签.
BACKUP_PATTERN = re.compile(r"^(?P<file>.+?\.db)\.bak-(?P<version>\d+)-(?P<time>\d{14})$")
RECOVERY_LOG_TAG = "DatabaseRecovery"
NOTICE_TAG_PREFIX = "db-recovery:"
DOWNGRADE_TEXT = "Can't downgrade database"
# --start broadcast: an exported receiver every build has (permission DUMP, which the shell holds) and an action it
# ignores, so only the process starts (App.onCreate), as when a provider or a broadcast starts it in the background.
# zh-CN: --start broadcast: 每个构建都有的导出接收器 (权限 DUMP, shell 持有) 与它忽略的 action, 只拉起进程 (App.onCreate),
# 与后台被 provider 或广播拉起时相同.
START_RECEIVER = "androidx.profileinstaller.ProfileInstallReceiver"
START_ACTION = "org.autojs.autojs6.dbdowngrade.START"
SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
DEFAULT_START_SH = f"/sdcard/Android/data/{SHIZUKU_PACKAGE}/start.sh"
SERVER_NAME = "shizuku_server"
EVENT_LINE = re.compile(r"^(\d\d-\d\d \d\d:\d\d:\d\d)\.\d+.*?\s(am_proc_start|am_crash|am_process_crashed_too_much|am_proc_died)\s*:\s*\[(.*)\]\s*$")
TIMED_SEED = {
    "time": 0, "scheduled": 0, "delay": 0, "interval": 0, "loop_times": 1, "millis": FAR_FUTURE_MILLIS,
    "repeat_mode": 0, "end_mode": 0, "end_value": 0, "finished_times": 0, "day_of_month": 0, "month_of_year": 0,
}
INTENT_SEED = {"action": MARKER_ACTION, "local": 1}


class Db:

    def __init__(self, key, file, table, source, seed, values):
        self.key = key
        self.file = file
        self.table = table
        self.source = source
        self.seed = seed
        self.values = values


DATABASES = (
    Db("timed", "TimedTaskDatabase.db", "TimedTask", "TimedTaskDatabase.java", 3, TIMED_SEED),
    Db("intent", "IntentTaskDatabase.db", "IntentTask", "IntentTaskDatabase.java", 2, INTENT_SEED),
)


class RoundTripError(Exception):
    pass


def code_versions(overrides):
    versions = {}
    for db in DATABASES:
        text = (SOURCE_DIR / db.source).read_text(encoding="utf-8")
        match = re.search(r"\bVERSION\s*=\s*(\d+)\s*;", text)
        if not match:
            raise RoundTripError(f"No VERSION in {db.source}")
        versions[db.key] = int(match.group(1))
    for item in overrides or ():
        key, _, value = item.partition("=")
        if key not in versions or not value.isdigit():
            raise RoundTripError(f"--code-version expects timed=N or intent=N, got {item}")
        versions[key] = int(value)
    return versions


def quote(text):
    return "'" + text.replace("'", "'\\''") + "'"


class Device:

    def __init__(self, serial):
        self.serial = serial
        self._sdk = None

    def adb(self, *args, timeout=60, binary=False):
        result = subprocess.run(["adb", "-s", self.serial, *args], capture_output=True, timeout=timeout)
        if binary:
            return result.returncode, result.stdout, result.stderr
        return (result.returncode, result.stdout.decode("utf-8", "replace").replace("\r\n", "\n"),
                result.stderr.decode("utf-8", "replace").replace("\r\n", "\n"))

    def shell(self, command, timeout=60):
        return self.adb("shell", command, timeout=timeout)[1]

    def prop(self, name):
        return self.shell(f"getprop {name}").strip()

    @property
    def sdk(self):
        if self._sdk is None:
            self._sdk = int(self.prop("ro.build.version.sdk") or 0)
        return self._sdk

    def now(self):
        """Device time in the logcat -T format."""
        return self.shell("date +'%m-%d %H:%M:%S.000'").strip()

    def stamp(self):
        return self.shell("date +%Y%m%d%H%M%S").strip()

    def label(self):
        if self.serial.startswith("emulator-"):
            code, out, _ = self.adb("emu", "avd", "name")
            name = out.splitlines()[0].strip() if code == 0 and out.strip() else ""
            if name:
                return name
        return self.prop("ro.product.model").replace(" ", "_") or self.serial

    def processes(self):
        """(pid, name) of every process."""
        out = self.shell("ps -A -o PID,NAME" if self.sdk >= 26 else "ps")
        rows = []
        for line in out.splitlines()[1:]:
            parts = line.split()
            if self.sdk >= 26 and len(parts) >= 2 and parts[0].isdigit():
                rows.append((int(parts[0]), parts[-1]))
            elif self.sdk < 26 and len(parts) >= 3 and parts[1].isdigit():
                rows.append((int(parts[1]), parts[-1]))
        return rows

    def pids(self, name):
        return [pid for pid, process in self.processes() if process == name]

    def events(self, since):
        return self.adb("shell", f"logcat -d -b events -T {quote(since)}", timeout=90)[1].splitlines()

    def log_count(self, since, tag, level="W"):
        out = self.adb("shell", f"logcat -d -s {tag}:{level} -T {quote(since)}", timeout=90)[1]
        return sum(1 for line in out.splitlines() if f" {tag}" in line or f"/{tag}" in line)

    def crash_logs(self, since):
        """{pid: logged a downgrade error} of the processes that logged an uncaught exception.

        Since 6.7.0 (Bugly removed) CrashHandler resolves its "system handler" lazily, after it has become the default
        handler itself, so a main thread crash recurses into CrashHandler and ends with exitProcess(1): no am_crash, no
        dropbox entry and no bad-process limit. The log lines of CrashHandler and AndroidRuntime catch both kinds.
        zh-CN: 6.7.0 (移除 Bugly) 起 CrashHandler 在自己已成为默认处理器之后才取 "系统处理器", 主线程崩溃会递归回到自身, 以
        exitProcess(1) 结束: 没有 am_crash, 没有 dropbox 条目, 也不受 bad-process 限制. 用 CrashHandler 与 AndroidRuntime 的
        日志行同时覆盖两种情况.
        """
        out = self.adb("shell", f"logcat -d -v brief -s CrashHandler:E AndroidRuntime:E -T {quote(since)}", timeout=90)[1]
        crashed, downgrade = set(), set()
        for line in out.splitlines():
            match = re.match(r"^[EF]/(CrashHandler|AndroidRuntime)\(\s*(\d+)\): (.*)$", line)
            if not match:
                continue
            pid = int(match.group(2))
            if match.group(3).startswith(("Uncaught Exception", "FATAL EXCEPTION")):
                crashed.add(pid)
            if DOWNGRADE_TEXT in match.group(3):
                downgrade.add(pid)
        return {pid: pid in downgrade for pid in crashed}


class App:

    def __init__(self, dev, package, start_mode="launcher"):
        self.dev = dev
        self.package = package
        self.start_mode = start_mode
        self._launcher = None

    def run_as(self, command, timeout=60):
        return self.dev.adb("shell", f"run-as {self.package} sh -c {quote(command)}", timeout=timeout)

    def debuggable(self):
        code, out, _ = self.run_as("id")
        return code == 0 and "uid=" in out

    def version(self):
        out = self.dev.shell(f"dumpsys package {self.package}")
        code = re.search(r"versionCode=(\d+)", out)
        name = re.search(r"versionName=(\S+)", out)
        return (int(code.group(1)) if code else 0), (name.group(1) if name else "?")

    def installed(self):
        return f"package:{self.package}" in self.dev.shell(f"pm list packages {self.package}").split()

    def launcher(self):
        """The launcher activity of the app itself: debug builds add LeakCanary's "Leaks" icon, which monkey may pick instead."""
        if self._launcher is None:
            out = self.dev.shell(f"cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER {self.package}")
            components = re.findall(rf"^\s*({re.escape(self.package)}/\S+)\s*$", out, re.M)
            self._launcher = next((c for c in components if "leakcanary" not in c.lower()), "")
        return self._launcher

    def start(self):
        if self.start_mode == "broadcast":
            # 0x20: FLAG_INCLUDE_STOPPED_PACKAGES, the app is force-stopped before every start.
            # zh-CN: 0x20: FLAG_INCLUDE_STOPPED_PACKAGES, 每次启动前应用都已被强停.
            self.dev.adb("shell", f"am broadcast -f 0x20 -n {self.package}/{START_RECEIVER} -a {START_ACTION}", timeout=60)
        elif self.launcher():
            self.dev.adb("shell", f"am start -n {self.launcher()} -a android.intent.action.MAIN -c android.intent.category.LAUNCHER", timeout=60)
        else:
            self.dev.adb("shell", f"monkey -p {self.package} -c android.intent.category.LAUNCHER 1", timeout=60)

    def start_text(self):
        if self.start_mode == "broadcast":
            return f"显式广播 ({START_RECEIVER.rsplit('.', 1)[-1]}, 只拉起进程)"
        return f"桌面入口 ({self.launcher() or 'monkey'})"

    def stop(self):
        self.dev.adb("shell", f"am force-stop {self.package}")

    def alive(self):
        return bool(self.dev.pids(self.package))

    def entries(self):
        """Names in databases/ (empty when the directory does not exist)."""
        code, out, _ = self.run_as("ls -a databases 2>/dev/null")
        return sorted({line.strip() for line in out.splitlines() if line.strip() not in ("", ".", "..")}) if code == 0 else []

    def kind(self, name):
        _, out, _ = self.run_as(f"if [ -d databases/{name} ]; then echo dir; elif [ -f databases/{name} ]; then echo file; else echo none; fi")
        return out.strip() or "none"

    def pull(self, name, dest):
        code, data, err = self.dev.adb("exec-out", "run-as", self.package, "cat", f"databases/{name}", timeout=120, binary=True)
        if code != 0:
            raise RoundTripError(f"Unable to pull {name}: {err.decode('utf-8', 'replace').strip()[:200]}")
        Path(dest).write_bytes(data)
        return data

    def pull_set(self, file, folder):
        """Pulls <file> and its companions into [folder]; returns the local path of <file>, or None."""
        names = self.entries()
        if file not in names or self.kind(file) != "file":
            return None
        for name in [file] + [file + suffix for suffix in COMPANIONS]:
            if name in names:
                self.pull(name, Path(folder) / name)
        local = Path(folder) / file
        if not local.read_bytes().startswith(b"SQLite format 3\0") and local.stat().st_size > 0:
            raise RoundTripError(f"{file} is not an SQLite file")
        return local

    def push(self, local, name):
        remote = f"{DEVICE_TMP}/{name}"
        self.dev.adb("shell", f"mkdir -p {DEVICE_TMP} && chmod 777 {DEVICE_TMP}")
        code, _, err = self.dev.adb("push", str(local), remote, timeout=120)
        if code != 0:
            raise RoundTripError(f"Unable to push {name}: {err.strip()[:200]}")
        self.dev.adb("shell", f"chmod 644 {remote}")
        code, _, err = self.run_as(f"cat {remote} > databases/{name}")
        self.dev.adb("shell", f"rm -f {remote}")
        if code != 0:
            raise RoundTripError(f"Unable to write databases/{name}: {err.strip()[:200]}")
        with tempfile.TemporaryDirectory() as folder:
            pulled = self.pull(name, Path(folder) / name)
        if hashlib.sha256(pulled).digest() != hashlib.sha256(Path(local).read_bytes()).digest():
            raise RoundTripError(f"databases/{name} differs from the pushed copy")

    def remove_companions(self, file):
        self.run_as(" ; ".join(f"rm -f databases/{file}{suffix}" for suffix in COMPANIONS))

    def backups(self, file):
        return [name for name in self.entries() if (match := BACKUP_PATTERN.match(name)) and match.group("file") == file]

    def ensure_databases(self, wait=40):
        """Starts the app once when a database file is missing, so the build creates both."""
        if all(db.file in self.entries() for db in DATABASES):
            return False
        self.start()
        deadline = time.time() + wait
        while time.time() < deadline and not all(db.file in self.entries() for db in DATABASES):
            time.sleep(1)
        time.sleep(2)
        self.stop()
        if not all(db.file in self.entries() for db in DATABASES):
            raise RoundTripError("The app did not create both databases")
        return True

    def notices(self):
        """Tags of the recovery notifications the app has posted."""
        out = self.dev.shell("dumpsys notification --noredact", timeout=60)
        return sorted(set(re.findall(rf"\|{re.escape(self.package)}\|-?\d+\|({re.escape(NOTICE_TAG_PREFIX)}[^|]+)\|", out)))


def inspect(path, table):
    """user_version, table presence, rows, marker rows and the future column / table of a local database copy."""
    con = sqlite3.connect(str(path))
    try:
        tables = {row[0] for row in con.execute("SELECT name FROM sqlite_master WHERE type = 'table'")}
        columns = [row[1] for row in con.execute(f"PRAGMA table_info(`{table}`)")]
        info = {
            "version": con.execute("PRAGMA user_version").fetchone()[0],
            "table": table in tables,
            "rows": 0,
            "markers": 0,
            "futureColumn": FUTURE_COLUMN in columns,
            "futureTable": FUTURE_TABLE in tables,
        }
        if table in tables:
            info["rows"] = con.execute(f"SELECT COUNT(*) FROM `{table}`").fetchone()[0]
            if "script_path" in columns:
                info["markers"] = con.execute(f"SELECT COUNT(*) FROM `{table}` WHERE script_path LIKE ?", (MARKER_PREFIX + "%",)).fetchone()[0]
        return info
    finally:
        con.close()


def read_databases(app):
    """inspect() of every database on the device (None for a missing one)."""
    result = {}
    with tempfile.TemporaryDirectory() as folder:
        for db in DATABASES:
            local = app.pull_set(db.file, folder)
            result[db.key] = inspect(local, db.table) if local else None
    return result


def make_future_file(path, db, target_version):
    """Seeds marker rows, adds the future column and table and sets user_version; returns (old version, new version)."""
    con = sqlite3.connect(str(path))
    try:
        con.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        con.execute("PRAGMA journal_mode = DELETE")
        old = con.execute("PRAGMA user_version").fetchone()[0]
        columns = [row[1] for row in con.execute(f"PRAGMA table_info(`{db.table}`)")]
        if not columns:
            raise RoundTripError(f"{db.file} has no table {db.table}")
        present = con.execute(f"SELECT COUNT(*) FROM `{db.table}` WHERE script_path LIKE ?", (MARKER_PREFIX + "%",)).fetchone()[0]
        for index in range(present + 1, db.seed + 1):
            values = {key: value for key, value in db.values.items() if key in columns}
            values["script_path"] = f"{MARKER_PREFIX}{db.key}-{index}.js"
            names = ", ".join(f"`{key}`" for key in values)
            con.execute(f"INSERT INTO `{db.table}` ({names}) VALUES ({', '.join('?' for _ in values)})", tuple(values.values()))
        if FUTURE_COLUMN not in columns:
            con.execute(f"ALTER TABLE `{db.table}` ADD COLUMN `{FUTURE_COLUMN}` TEXT DEFAULT 'future'")
        con.execute(f"CREATE TABLE IF NOT EXISTS `{FUTURE_TABLE}` (`id` INTEGER PRIMARY KEY, `note` TEXT)")
        if not con.execute(f"SELECT COUNT(*) FROM `{FUTURE_TABLE}`").fetchone()[0]:
            con.execute(f"INSERT INTO `{FUTURE_TABLE}` (`note`) VALUES ('future')")
        con.execute(f"PRAGMA user_version = {int(target_version)}")
        con.commit()
        return old, target_version
    finally:
        con.close()


def summarize_events(lines, package):
    """Process starts (with their hosting types), crashes (first exception line), crashed-too-much and deaths."""
    summary = {"starts": 0, "startTypes": {}, "crashes": 0, "downgradeCrashes": 0, "exception": "", "tooMuch": 0, "died": 0,
               "first": "", "last": "", "pids": set()}
    for line in lines:
        match = EVENT_LINE.match(line)
        if not match:
            continue
        when, tag, body = match.groups()
        fields = body.split(",")
        if tag == "am_proc_start" and len(fields) >= 5 and fields[3] == package:
            # [user, pid, uid, process, hosting type, hosting name]
            summary["starts"] += 1
            if fields[1].strip().isdigit():
                summary["pids"].add(int(fields[1]))
            kind = fields[4].strip() or "?"
            if len(fields) >= 6 and fields[5].strip():
                # Hosting component, class name only: {pkg/a.b.C} -> C. zh-CN: 拉起组件只留类名.
                kind += " " + re.split(r"[/.]", fields[5].strip().strip("{}"))[-1][:60]
            summary["startTypes"][kind] = summary["startTypes"].get(kind, 0) + 1
        elif tag == "am_crash" and len(fields) >= 8 and fields[2] == package:
            # [user, pid, process, flags, exception, message..., file, line(, recoverable)]
            summary["crashes"] += 1
            tail = 3 if len(fields) >= 9 and fields[-1].strip().lstrip("-").isdigit() and fields[-2].strip().lstrip("-").isdigit() else 2
            message = ",".join(fields[5:len(fields) - tail]).strip()
            if DOWNGRADE_TEXT in message:
                summary["downgradeCrashes"] += 1
            if not summary["exception"]:
                summary["exception"] = f"{fields[4].strip()}: {message}"[:160]
        elif tag == "am_process_crashed_too_much" and len(fields) >= 2 and fields[1] == package:
            summary["tooMuch"] += 1
        elif tag == "am_proc_died" and len(fields) >= 3 and fields[2] == package:
            summary["died"] += 1
        else:
            continue
        summary["first"] = summary["first"] or when
        summary["last"] = when
    return summary


def types_text(types):
    return ", ".join(f"{kind} {count}" for kind, count in sorted(types.items(), key=lambda item: -item[1])) or "-"


class Context:

    def __init__(self, args):
        self.args = args
        self.dev = Device(args.serial)
        self.app = App(self.dev, args.package, args.start)
        self.versions = code_versions(args.code_version)
        self.items = []

    def item(self, name, ok, values):
        self.items.append((name, ok, values))
        mark = "PASS" if ok is True else ("FAIL" if ok is False else "INFO")
        print(f"  [{mark}] {name}: {values}")
        return ok

    def report(self, command, extra_meta=()):
        version_code, version_name = self.app.version() if self.app.installed() else (0, "?")
        folder = Path(self.args.report_dir)
        if not folder.is_absolute():
            folder = ROOT / folder
        folder.mkdir(parents=True, exist_ok=True)
        date = datetime.date.today().strftime("%Y%m%d")
        label = f"-{self.args.label}" if self.args.label else ""
        device = self.dev.label()
        path = folder / f"roundtrip-{device}-{version_code}-{date}{label}.md"
        decided = [ok for _, ok, _ in self.items if ok is not None]
        verdict = ("PASS" if all(decided) else "FAIL") if decided else "INFO"
        lines = []
        if not path.exists():
            lines += [f"# 数据库降级往返: {device} / {version_name} ({version_code})", "",
                      f"- 设备: {device} ({self.dev.serial}), Android {self.dev.prop('ro.build.version.release')} / API {self.dev.sdk}",
                      f"- 脚本: `tools/db-downgrade/roundtrip.py` (ROADMAP P0.3, T6); 代码版本 TimedTask {self.versions['timed']} / IntentTask {self.versions['intent']}",
                      "- 每节一次运行, 只记通过 / 失败与关键数值 (R4)", ""]
        lines += [f"## {datetime.datetime.now().strftime('%H:%M:%S')} {command} {self.args.package} {verdict}", ""]
        for meta in extra_meta:
            lines.append(f"- {meta}")
        if extra_meta:
            lines.append("")
        lines += ["| 项 | 结果 | 数值 |", "| --- | --- | --- |"]
        for name, ok, values in self.items:
            mark = "PASS" if ok is True else ("FAIL" if ok is False else "-")
            lines.append(f"| {name} | {mark} | {str(values).replace('|', '/')} |")
        lines.append("")
        with path.open("a", encoding="utf-8", newline="\n") as stream:
            stream.write("\n".join(lines) + "\n")
        print(f"{command} {self.args.package}: {verdict} -> {path.relative_to(ROOT) if path.is_relative_to(ROOT) else path}")
        self.items = []
        return verdict == "PASS"

    def require_debuggable(self):
        if not self.app.installed():
            raise RoundTripError(f"{self.args.package} is not installed")
        if not self.app.debuggable():
            raise RoundTripError(f"{self.args.package} is not debuggable (run-as failed)")


def make_future(ctx):
    ctx.require_debuggable()
    app = ctx.app
    app.stop()
    created = app.ensure_databases()
    ctx.item("databases", None, "created by a first start" if created else "present")
    with tempfile.TemporaryDirectory() as folder:
        for db in DATABASES:
            local = app.pull_set(db.file, folder)
            if local is None:
                ctx.item(db.file, False, "missing")
                continue
            target = ctx.versions[db.key] + ctx.args.bump
            old, new = make_future_file(local, db, target)
            app.push(local, db.file)
            app.remove_companions(db.file)
            info = inspect(local, db.table)
            ok = info["version"] == target and info["markers"] == db.seed and info["futureColumn"] and info["futureTable"]
            ctx.item(db.file, ok, f"user_version {old} -> {new}, rows {info['rows']} (markers {info['markers']}), "
                                  f"future column {info['futureColumn']}, future table {info['futureTable']}")
    return ctx.report("make-future")


def observe_start(ctx, observe, probe=None):
    """Starts the app, waits, calls [probe] while the app still runs (force-stop clears its notifications on newer
    Android versions), force-stops it; returns (event summary, alive at the end, start time, probe result)."""
    app = ctx.app
    app.stop()
    time.sleep(1)
    since = ctx.dev.now()
    app.start()
    time.sleep(observe)
    alive = app.alive()
    probed = probe() if probe else None
    app.stop()
    time.sleep(1)
    summary = summarize_events(ctx.dev.events(since), app.package)
    attach_logs(summary, ctx.dev.crash_logs(since))
    return summary, alive, since, probed


def attach_logs(summary, logs):
    """Adds the crashes logged by the package's processes (see Device.crash_logs)."""
    own = {pid: downgrade for pid, downgrade in logs.items() if pid in summary["pids"]}
    summary["logged"] = len(own)
    summary["loggedDowngrade"] = sum(1 for downgrade in own.values() if downgrade)
    return summary


def crash_text(summary):
    return (f"am_crash {summary['crashes']} (downgrade {summary['downgradeCrashes']}), logged {summary['logged']} "
            f"(downgrade {summary['loggedDowngrade']}), crashed too much {summary['tooMuch']}"
            + (f"; first: {summary['exception']}" if summary["exception"] else ""))


def no_crash(summary):
    return summary["crashes"] == 0 and summary["logged"] == 0


def check_items(ctx, summary, alive, expect_markers=True, expect_future=True):
    ctx.item("process", alive, "alive" if alive else "not running at the end")
    ctx.item("crashes", no_crash(summary), crash_text(summary))
    ctx.item("process starts", summary["starts"] <= 1,
             f"am_proc_start {summary['starts']} (restarts {max(summary['starts'] - 1, 0)}; {types_text(summary['startTypes'])})"
             + (f", {summary['first']} - {summary['last']}" if summary["first"] else ""))
    databases = read_databases(ctx.app)
    for db in DATABASES:
        info = databases[db.key]
        if info is None:
            ctx.item(db.file, False, "missing")
            continue
        ok = info["version"] == ctx.versions[db.key] and info["table"]
        if expect_markers:
            ok = ok and info["markers"] == db.seed
        if expect_future:
            ok = ok and info["futureColumn"] and info["futureTable"]
        ctx.item(db.file, ok, f"user_version {info['version']} (code {ctx.versions[db.key]}), rows {info['rows']} "
                              f"(markers {info['markers']}/{db.seed}), future column {info['futureColumn']}, future table {info['futureTable']}")
    return databases


def check(ctx):
    ctx.require_debuggable()
    summary, alive, _, _ = observe_start(ctx, ctx.args.observe)
    check_items(ctx, summary, alive, expect_future=not ctx.args.no_future)
    return ctx.report("check", [f"启动方式: {ctx.app.start_text()}, 观察 {ctx.args.observe} s 后强停"])


def inject_fail(ctx):
    ctx.require_debuggable()
    app = ctx.app
    dev = ctx.dev
    app.stop()
    app.ensure_databases()
    if dev.sdk >= 33:
        dev.adb("shell", f"pm grant {app.package} android.permission.POST_NOTIFICATIONS")
    dev.adb("shell", f"cmd notification cancel_all {app.package} >/dev/null 2>&1 || true")
    before = {db.file: set(app.backups(db.file)) for db in DATABASES}
    for db in DATABASES:
        # Keep the real files under a name DatabaseRecovery never touches, then put a directory in their place.
        # zh-CN: 先把真实文件改成 DatabaseRecovery 不会碰的名字, 再在原位置放一个同名目录.
        app.run_as(" ; ".join([f"mv databases/{name} databases/{name}.roundtrip-kept" for name in
                               [db.file] + [db.file + suffix for suffix in COMPANIONS] if name in app.entries()]
                              + [f"mkdir databases/{db.file}"]))
        ctx.item(f"{db.file} injected", app.kind(db.file) == "dir", f"databases/{db.file} is a {app.kind(db.file)}")
    since = dev.now()
    host = app.package == DEFAULT_PACKAGE
    summary, alive, _, tags = observe_start(ctx, ctx.args.observe, app.notices if host else None)
    ctx.item("process", alive, "alive" if alive else "not running at the end")
    ctx.item("crashes", no_crash(summary), f"{crash_text(summary)}, process starts {summary['starts']}")
    ctx.item("recovery log", dev.log_count(since, RECOVERY_LOG_TAG) >= len(DATABASES), f"{RECOVERY_LOG_TAG} W lines {dev.log_count(since, RECOVERY_LOG_TAG)}")
    databases = read_databases(app)
    for db in DATABASES:
        new = sorted(set(app.backups(db.file)) - before[db.file])
        info = databases[db.key]
        ok = len(new) == 1 and info is not None and info["version"] == ctx.versions[db.key] and info["rows"] == 0
        ctx.item(f"{db.file} recovered", ok, f"new backups {', '.join(new) or 'none'}; new database "
                 + (f"user_version {info['version']}, rows {info['rows']}" if info else "missing"))
    if host:
        ctx.item("notification", all(NOTICE_TAG_PREFIX + db.file.removesuffix(".db") in tags for db in DATABASES), f"tags {', '.join(tags) or 'none'}")
    # Put the kept files back and drop what the injection produced (the empty databases and the empty directories).
    # zh-CN: 放回保留的文件, 去掉注入产生的内容 (空库与空目录).
    app.stop()
    for db in DATABASES:
        new = sorted(set(app.backups(db.file)) - before[db.file])
        commands = [f"rm -f databases/{db.file}{suffix}" for suffix in ("",) + COMPANIONS]
        commands += [f"rmdir databases/{name} 2>/dev/null || rm -f databases/{name}" for name in new]
        commands += [f"mv databases/{name}.roundtrip-kept databases/{name}" for name in
                     [db.file] + [db.file + suffix for suffix in COMPANIONS] if f"{name}.roundtrip-kept" in app.entries()]
        app.run_as(" ; ".join(commands))
    dev.adb("shell", f"cmd notification cancel_all {app.package} >/dev/null 2>&1 || true")
    restored = read_databases(app)
    ctx.item("kept files back", all(restored[db.key] is not None and restored[db.key]["markers"] == db.seed for db in DATABASES),
             ", ".join(f"{db.file} markers {restored[db.key]['markers'] if restored[db.key] else 'missing'}" for db in DATABASES))
    return ctx.report("inject-fail", [f"注入: databases/<库文件> 换成同名目录; 启动方式: {app.start_text()}, 观察 {ctx.args.observe} s"])


def rename_to_backup(app, db, version, stamp):
    """Renames <file> and its companions as DatabaseRecovery does: <backup>, <backup>-wal, <backup>-shm, <backup>-journal."""
    backup = f"{db.file}.bak-{version}-{stamp}"
    entries = app.entries()
    app.run_as(" ; ".join(f"mv databases/{db.file}{suffix} databases/{backup}{suffix}" for suffix in ("",) + COMPANIONS
                          if db.file + suffix in entries))
    return backup


def rename_from_backup(app, db, backup):
    entries = app.entries()
    commands = [f"rm -f databases/{db.file}{suffix}" for suffix in ("",) + COMPANIONS]
    commands += [f"mv databases/{backup}{suffix} databases/{db.file}{suffix}" for suffix in ("",) + COMPANIONS if backup + suffix in entries]
    app.run_as(" ; ".join(commands))


def restore_check(ctx):
    ctx.require_debuggable()
    if not ctx.args.apk:
        raise RoundTripError("restore-check needs --apk (the installed build, reinstalled to count as a later install)")
    app = ctx.app
    dev = ctx.dev
    app.stop()
    start = read_databases(app)
    if any(start[db.key] is None or start[db.key]["markers"] != db.seed for db in DATABASES):
        raise RoundTripError("restore-check needs the marker rows (run make-future and check first)")

    def install():
        # A streamed install occasionally hangs on an emulator (once on API 24, 300 s): retry once.
        # zh-CN: 模拟器上的流式安装偶尔会卡住 (API 24 上出现过一次, 300 s): 重试一次.
        for attempt in range(2):
            try:
                code, out, err = dev.adb("install", "-r", "-t", ctx.args.apk, timeout=180)
                break
            except subprocess.TimeoutExpired:
                if attempt:
                    raise
        if code != 0 or "Success" not in out:
            raise RoundTripError(f"install failed: {(out + err).strip()[-200:]}")

    def run(observe):
        summary, alive, _, _ = observe_start(ctx, observe)
        return summary, alive, read_databases(app)

    def put_back(current):
        """Renames the backups still present back to the database names, so a failed step does not leave the data aside."""
        app.stop()
        entries = app.entries()
        for db in DATABASES:
            if current.get(db.file) in entries:
                rename_from_backup(app, db, current[db.file])

    backups = {}
    try:
        # (a) A backup the running build made itself is not restored until another install.
        # zh-CN: (a) 本构建自己留下的备份, 在再次安装之前不还原.
        stamp = dev.stamp()
        backups = {db.file: rename_to_backup(app, db, ctx.versions[db.key], stamp) for db in DATABASES}
        summary, alive, databases = run(ctx.args.observe)
        ok = alive and no_crash(summary) and all(backups[db.file] in app.entries() and databases[db.key]["markers"] == 0 for db in DATABASES)
        ctx.item("(a) no reinstall", ok, f"alive {alive}, am_crash {summary['crashes']}; "
                 + ", ".join(f"{db.file} backup kept {backups[db.file] in app.entries()}, markers {databases[db.key]['markers']}" for db in DATABASES))
        # (b) After a reinstall, a backup no newer than the code version replaces the empty database.
        # zh-CN: (b) 重新安装后, 文件版本不高于代码版本的备份取代空库.
        time.sleep(1)
        install()
        summary, alive, databases = run(ctx.args.observe)
        ok = alive and no_crash(summary) and all(not app.backups(db.file) and databases[db.key]["markers"] == db.seed
                                                       and databases[db.key]["version"] == ctx.versions[db.key] for db in DATABASES)
        ctx.item("(b) reinstalled, version satisfied", ok, f"alive {alive}, am_crash {summary['crashes']}; "
                 + ", ".join(f"{db.file} backups left {len(app.backups(db.file))}, markers {databases[db.key]['markers']}/{db.seed}, "
                             f"user_version {databases[db.key]['version']}" for db in DATABASES))
        # (c) A backup newer than the code version stays where it is.
        # zh-CN: (c) 文件版本高于代码版本的备份保持不动.
        app.stop()
        stamp = dev.stamp()
        backups = {db.file: rename_to_backup(app, db, ctx.versions[db.key] + 1, stamp) for db in DATABASES}
        time.sleep(1)
        install()
        summary, alive, databases = run(ctx.args.observe)
        ok = alive and no_crash(summary) and all(backups[db.file] in app.entries() and databases[db.key]["markers"] == 0 for db in DATABASES)
        ctx.item("(c) reinstalled, backup newer than code", ok, f"alive {alive}, am_crash {summary['crashes']}; "
                 + ", ".join(f"{db.file} backup kept {backups[db.file] in app.entries()}, markers {databases[db.key]['markers']}" for db in DATABASES))
    finally:
        # Put the data back by hand for the next steps, also after a failed step.
        # zh-CN: 手工放回数据, 供后续步骤使用; 某步失败时也放回.
        put_back(backups)
    final = read_databases(app)
    ctx.item("data back", all(final[db.key] and final[db.key]["markers"] == db.seed for db in DATABASES),
             ", ".join(f"{db.file} markers {final[db.key]['markers'] if final[db.key] else 'missing'}" for db in DATABASES))
    return ctx.report("restore-check", [f"APK: {Path(ctx.args.apk).name}; 启动方式: {app.start_text()}, 每步观察 {ctx.args.observe} s"])


def shizuku_starter(dev, start_sh):
    """`sh <start.sh>` when Shizuku has written it, else its starter library (what start.sh runs)."""
    if dev.adb("shell", f"ls {start_sh}")[0] == 0:
        return f"sh {start_sh}"
    paths = [line.split(":", 1)[1].strip() for line in dev.shell(f"pm path {SHIZUKU_PACKAGE}").splitlines() if line.startswith("package:")]
    if not paths:
        raise RoundTripError(f"{SHIZUKU_PACKAGE} is not installed")
    folder = paths[0].rsplit("/", 1)[0] + "/lib"
    for abi in dev.shell(f"ls {folder}").split():
        library = f"{folder}/{abi}/libshizuku.so"
        if dev.adb("shell", f"ls {library}")[0] == 0:
            return library
    raise RoundTripError(f"Neither {start_sh} nor libshizuku.so found")


def wake(ctx):
    dev = ctx.dev
    packages = ctx.args.wake_package or [ctx.args.package]
    starter = shizuku_starter(dev, ctx.args.start_sh)
    totals = {package: {"starts": 0, "crashes": 0, "rounds": 0} for package in packages}
    for index in range(1, ctx.args.rounds + 1):
        for package in packages:
            App(dev, package).stop()
        time.sleep(1)
        old = dev.pids(SERVER_NAME)
        since = dev.now()
        dev.adb("shell", f"{starter} >/dev/null 2>&1", timeout=120)
        deadline = time.time() + 30
        new = []
        while time.time() < deadline:
            new = dev.pids(SERVER_NAME)
            if new and set(new) != set(old):
                break
            time.sleep(0.5)
        restarted = bool(new) and set(new) != set(old)
        time.sleep(ctx.args.observe)
        alive = {package: App(dev, package).alive() for package in packages}
        lines = dev.events(since)
        logs = dev.crash_logs(since)
        for package in packages:
            App(dev, package).stop()
        for package in packages:
            summary = attach_logs(summarize_events(lines, package), logs)
            totals[package]["starts"] += summary["starts"]
            totals[package]["crashes"] += summary["crashes"] + summary["logged"]
            totals[package]["rounds"] += 1
            ok = restarted and no_crash(summary) and summary["starts"] <= 1
            ctx.item(f"round {index} {package}", ok,
                     f"server restarted {restarted}, am_proc_start {summary['starts']} ({types_text(summary['startTypes'])}), "
                     f"{crash_text(summary)}, alive at {ctx.args.observe} s {alive[package]}")
    shizuku = re.search(r"versionName=(\S+)", dev.shell(f"dumpsys package {SHIZUKU_PACKAGE}"))
    meta = [f"Shizuku {shizuku.group(1) if shizuku else '?'}, 服务端经 `{starter.split('/')[-1] if '/data/app/' in starter else starter}` 重启, "
            f"每轮观察 {ctx.args.observe} s 后强停",
            "合计: " + "; ".join(f"{package} am_proc_start {value['starts']}, 崩溃 (am_crash + logged) {value['crashes']} ({value['rounds']} 轮)"
                                for package, value in totals.items())]
    return ctx.report("wake", meta)


def suite(ctx):
    results = [make_future(ctx), check(ctx), inject_fail(ctx)]
    if ctx.args.apk:
        results.append(restore_check(ctx))
    return all(results)


def section(text, header):
    """Lines of one `dumpsys` section, from [header] to the next unindented line."""
    lines = text.splitlines()
    for start, line in enumerate(lines):
        if line.strip().startswith(header):
            body = []
            for follow in lines[start + 1:]:
                if follow and not follow[0].isspace():
                    break
                body.append(follow)
            return body
    return []


def forensics(ctx):
    dev = ctx.dev
    package = ctx.args.package
    out = dev.shell(f"dumpsys package {package}", timeout=60)
    code = re.search(r"versionCode=(\d+)", out)
    name = re.search(r"versionName=(\S+)", out)
    first = re.search(r"firstInstallTime=([^\r\n]+)", out)
    last = re.search(r"lastUpdateTime=([^\r\n]+)", out)
    installer = re.search(r"installerPackageName=(\S+)", out)
    flags = re.search(r"pkgFlags=\[([^\]]*)\]", out)
    authorities = sorted(set(re.findall(r"\[(" + re.escape(package) + r"[^\]]*)\]", "\n".join(section(out, "ContentProvider Authorities:")))))
    ctx.item("package", None, f"versionCode {code.group(1) if code else '?'}, versionName {name.group(1) if name else '?'}, "
                              f"installer {installer.group(1) if installer else '-'}, flags [{flags.group(1).strip() if flags else ''}]")
    ctx.item("install times", None, f"first {first.group(1).strip() if first else '?'}, last update {last.group(1).strip() if last else '?'}")
    ctx.item("provider authorities", None, ", ".join(authorities) or "none")
    receivers = len(re.findall(r"^\s+[0-9a-f]+ " + re.escape(package) + r"/\S+", "\n".join(section(out, "Receiver Resolver Table:")), re.M))
    services = len(re.findall(r"^\s+[0-9a-f]+ " + re.escape(package) + r"/\S+", "\n".join(section(out, "Service Resolver Table:")), re.M))
    ctx.item("resolver entries", None, f"receiver {receivers}, service {services}")

    crash = dev.adb("shell", "dumpsys dropbox --print data_app_crash", timeout=180)[1]
    entries = re.split(r"\n(?=\d{4}-\d\d-\d\d \d\d:\d\d:\d\d data_app_crash)", crash)
    times, exceptions, frames, versions = [], {}, {}, set()
    for entry in entries:
        head = re.match(r"(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d) data_app_crash", entry.strip())
        if not head or not re.search(rf"^Process: {re.escape(package)}\s*$", entry, re.M):
            continue
        times.append(head.group(1))
        version = re.search(r"^Package: \S+ v(\d+)", entry, re.M)
        if version:
            versions.add(version.group(1))
        causes = re.findall(r"^(?:Caused by: )?([\w.$]+(?:Exception|Error)[^\r\n]*)$", entry, re.M)
        root = causes[-1][:160] if causes else "?"
        exceptions[root] = exceptions.get(root, 0) + 1
        tail = entry.split("Caused by:")[-1]
        frame = re.search(r"^\s+at ([^\r\n]+)", tail, re.M)
        if frame:
            frames[frame.group(1)[:120]] = frames.get(frame.group(1)[:120], 0) + 1
    if times:
        ctx.item("dropbox data_app_crash", None, f"{len(times)} entries, {min(times)} - {max(times)}, versionCode {', '.join(sorted(versions)) or '?'}")
        for text, count in sorted(exceptions.items(), key=lambda item: -item[1])[:3]:
            ctx.item("dropbox root cause", None, f"{count} x {text}")
        for text, count in sorted(frames.items(), key=lambda item: -item[1])[:2]:
            ctx.item("dropbox top frame of the root cause", None, f"{count} x {text}")
        per_day = {}
        for when in times:
            per_day[when[:10]] = per_day.get(when[:10], 0) + 1
        ctx.item("dropbox entries per day", None, ", ".join(f"{day} {count}" for day, count in sorted(per_day.items())))
    else:
        ctx.item("dropbox data_app_crash", None, "no entry for the package (rotated or never written)")
    # An entry older than the crashes means "never written" (CrashHandler exits by itself), not "rotated".
    # zh-CN: 最早条目早于崩溃时间时, 说明是 "未写入" (CrashHandler 自行退出), 不是 "已轮转".
    listed = re.findall(r"^(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d) \S+", dev.shell("dumpsys dropbox", timeout=120), re.M)
    ctx.item("dropbox window", None, f"{len(listed)} entries of all tags, oldest {min(listed) if listed else '-'}")

    processes = dev.shell("dumpsys activity processes", timeout=60)
    bad = [line.strip() for line in section(processes, "Bad processes:") if package in line]
    crashed = [line.strip() for line in processes.splitlines() if package in line and ("crash" in line.lower() or "bad" in line.lower())]
    running = [line.strip() for line in processes.splitlines() if re.search(rf"ProcessRecord\{{\S+ \d+:{re.escape(package)}(/|:)", line)]
    ctx.item("bad processes", None, "; ".join(bad)[:300] or "none listed")
    ctx.item("crash records", None, "; ".join(crashed)[:300] or "none")
    ctx.item("process now", None, f"{len(running)} ProcessRecord lines" + (f", pids {dev.pids(package)}" if running else ""))
    jobs = dev.shell("dumpsys jobscheduler", timeout=60)
    job_lines = [line for line in jobs.splitlines() if re.search(rf"JOB #\S+: \S+ {re.escape(package)}/", line)]
    ctx.item("jobs", None, f"{len(job_lines)} registered" + (f": {', '.join(sorted({l.split('/')[-1].split()[0] for l in job_lines}))[:200]}" if job_lines else ""))
    alarms = dev.shell("dumpsys alarm", timeout=60)
    alarm_lines = [line for line in alarms.splitlines() if re.search(rf"\b(RTC|ELAPSED)\w* #\d+: Alarm\{{.*{re.escape(package)}", line)]
    ctx.item("alarms", None, f"{len(alarm_lines)} pending")
    events = dev.adb("shell", "logcat -d -b events", timeout=120)[1].splitlines()
    summary = summarize_events(events, package)
    oldest = next((EVENT_LINE.match(line) or re.match(r"^(\d\d-\d\d \d\d:\d\d:\d\d)", line) for line in events if re.match(r"^\d\d-\d\d", line)), None)
    ctx.item("event log", None, f"buffer from {oldest.group(1) if oldest else '?'}; am_proc_start {summary['starts']} ({types_text(summary['startTypes'])}), "
                                f"am_crash {summary['crashes']} (downgrade {summary['downgradeCrashes']}), crashed too much {summary['tooMuch']}, "
                                f"died {summary['died']}" + (f", {summary['first']} - {summary['last']}" if summary["first"] else ""))
    alive = bool(dev.pids(package))
    ctx.item("running now", None, "yes" if alive else "no")
    return ctx.report("forensics", ["只读取证: dumpsys package / dropbox / activity processes / jobscheduler / alarm, logcat -b events; 未装包, 未改设置"])


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0], formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=("make-future", "check", "inject-fail", "restore-check", "wake", "suite", "forensics"))
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--package", default=DEFAULT_PACKAGE, help="org.autojs.autojs6 (host) or org.autojs.autojs6.inrt")
    parser.add_argument("--report-dir", default="docs/db-downgrade/evidence", help="relative paths start at the repository root")
    parser.add_argument("--label", default="", help="report name suffix, e.g. before-fix")
    parser.add_argument("--start", choices=("launcher", "broadcast"), default="launcher",
                        help="how check / inject-fail / restore-check start the app (default launcher; broadcast for an inrt build without a project)")
    parser.add_argument("--observe", type=float, default=10.0, help="seconds to watch the app after a start (default 10)")
    parser.add_argument("--bump", type=int, default=1, help="make-future: file version = code version + this (default 1)")
    parser.add_argument("--code-version", action="append", help="override the version read from the source, e.g. timed=6")
    parser.add_argument("--no-future", action="store_true", help="check: do not require the future column and table")
    parser.add_argument("--apk", help="restore-check / suite: the installed build's APK, reinstalled as a later install")
    parser.add_argument("--rounds", type=int, default=3, help="wake: Shizuku server restarts (default 3)")
    parser.add_argument("--wake-package", action="append", help="wake: packages to count (default --package)")
    parser.add_argument("--start-sh", default=DEFAULT_START_SH, help="wake: Shizuku start script on the device")
    args = parser.parse_args()
    ctx = Context(args)
    try:
        passed = {"make-future": make_future, "check": check, "inject-fail": inject_fail, "restore-check": restore_check,
                  "wake": wake, "suite": suite, "forensics": forensics}[args.command](ctx)
    except (RoundTripError, subprocess.TimeoutExpired) as error:
        print(f"ERROR: {error}")
        return 2
    return 0 if passed or args.command == "forensics" else 1


if __name__ == "__main__":
    sys.exit(main())
