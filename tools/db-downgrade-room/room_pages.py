"""Room downgrade page check (docs/db-downgrade/ROADMAP.md P2.3).

Writes "future" files of the three Room databases of the host (HistoryDatabase, ColorHistoryDatabase, KeyStoreDatabase)
while the host is stopped, then starts it and opens the pages that read them. Each future file holds the tables of the
newest snapshot in app/schemas with marker rows, one more column on one table, one more table with an index, a foreign
identity hash and user_version = snapshot version + 1, i.e. what a newer build would leave behind. RoomDowngrade has to
rebuild it on the first open.

Steps:
  1. Force-stops the host. Existing files of the three databases are renamed to <file>.room-pages-<yyyyMMddHHmmss> (never
     deleted, ROADMAP R3); the future files are built on the PC with Python sqlite3 and written through run-as.
  2. Starts the host from its launcher and checks that it is still alive after --observe seconds.
  3. Runs RoomDowngradePagesDeviceTest (instrumentation argument autojs.dbDowngrade.pages=true): drawer, version history,
     trash, color libraries, color select and keystore pages, then HistoryCleanupWorker once. The instrumentation restarts
     the host process.
  4. Counts crashes and Room open errors in logcat and the RoomDowngrade rebuild lines per database.
  5. Force-stops the host, pulls the databases and checks: user_version and identity hash of the code version, all marker
     rows there, the future column gone, the future table kept, no table left by the rebuild.

Needs a debuggable host (run-as) and its androidTest APK installed (or --apk / --test-apk). Report:
<report-dir>/roundtrip-<AVD name or model>-<versionCode>-<yyyyMMdd>-room.md, one section appended per run, PASS / FAIL with
counts and versions only (ROADMAP R4).

Usage: py -3.12 -I tools/db-downgrade-room/room_pages.py -s emulator-5620 [--apk app.apk --test-apk test.apk]
"""
import argparse
import datetime
import json
from pathlib import Path
import re
import sqlite3
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[2]
SCHEMA_DIR = ROOT / "app/schemas"
DEFAULT_PACKAGE = "org.autojs.autojs6"
RUNNER = "androidx.test.runner.AndroidJUnitRunner"
PAGES_TEST = "org.autojs.autojs.storage.database.RoomDowngradePagesDeviceTest"
PAGES_ARGUMENT = "autojs.dbDowngrade.pages"
DEVICE_TMP = "/data/local/tmp/aj6-db-downgrade-room"
COMPANIONS = ("-wal", "-shm", "-journal")
FUTURE_COLUMN = "room_future_note"
FUTURE_TABLE = "room_future_extra"
FUTURE_IDENTITY = "room-pages-future-identity"
MARKER = "aj6-db-downgrade-room"
# KeyStoreViewModel drops rows whose keystore file is missing, so the marker row points at an app file that exists.
# zh-CN: KeyStoreViewModel 会删除密钥库文件不存在的行, 因此标记行指向一个存在的应用文件.
KEYSTORE_FILE = f"files/{MARKER}.bks"


def quote(text):
    return "'" + text.replace("'", "'\\''") + "'"


class CheckError(Exception):
    pass


class Device:

    def __init__(self, serial, package):
        self.serial = serial
        self.package = package

    def adb(self, *args, timeout=60, binary=False):
        result = subprocess.run(["adb", "-s", self.serial, *args], capture_output=True, timeout=timeout)
        if binary:
            return result.returncode, result.stdout, result.stderr
        return (result.returncode, result.stdout.decode("utf-8", "replace").replace("\r\n", "\n"),
                result.stderr.decode("utf-8", "replace").replace("\r\n", "\n"))

    def shell(self, command, timeout=60):
        return self.adb("shell", command, timeout=timeout)[1]

    def run_as(self, command, timeout=60):
        return self.adb("shell", f"run-as {self.package} sh -c {quote(command)}", timeout=timeout)

    def prop(self, name):
        return self.shell(f"getprop {name}").strip()

    def version_code(self):
        match = re.search(r"versionCode=(\d+)", self.shell(f"dumpsys package {self.package}"))
        return match.group(1) if match else "unknown"

    def name(self):
        return self.prop("ro.boot.qemu.avd_name") or self.prop("ro.product.model").replace(" ", "_") or self.serial

    def pid(self):
        return self.shell(f"pidof {self.package}").strip()

    def entries(self):
        _, out, _ = self.run_as("ls databases 2>/dev/null")
        return set(out.split())

    def pull(self, name, dest):
        code, data, err = self.adb("exec-out", "run-as", self.package, "cat", f"databases/{name}", timeout=120, binary=True)
        if code != 0:
            raise CheckError(f"Unable to pull {name}: {err.decode('utf-8', 'replace').strip()[:200]}")
        Path(dest).write_bytes(data)

    def push(self, local, name):
        remote = f"{DEVICE_TMP}/{name}"
        self.adb("shell", f"mkdir -p {DEVICE_TMP} && chmod 777 {DEVICE_TMP}")
        code, _, err = self.adb("push", str(local), remote, timeout=120)
        if code != 0:
            raise CheckError(f"Unable to push {name}: {err.strip()[:200]}")
        self.adb("shell", f"chmod 644 {remote}")
        code, _, err = self.run_as(f"mkdir -p databases && cat {remote} > databases/{name}")
        self.adb("shell", f"rm -f {remote}")
        if code != 0:
            raise CheckError(f"Unable to write databases/{name}: {err.strip()[:200]}")
        _, out, _ = self.run_as(f"wc -c < databases/{name}")
        if out.strip() != str(Path(local).stat().st_size):
            raise CheckError(f"databases/{name} differs from the pushed copy")


class Database:
    """One Room database: the newest snapshot, the table that gets the future column, and the marker rows."""

    def __init__(self, key, klass, file, widened, seeds):
        self.key = key
        self.klass = klass
        self.file = file
        self.widened = widened
        self.seeds = seeds
        snapshot = max((SCHEMA_DIR / klass).glob("*.json"), key=lambda path: int(path.stem))
        self.schema = json.loads(snapshot.read_text(encoding="utf-8"))["database"]

    @property
    def version(self):
        return self.schema["version"]

    @property
    def identity(self):
        return self.schema["identityHash"]

    def tables(self):
        return {entity["tableName"] for entity in self.schema["entities"]}

    def columns(self, table):
        entity = next(entity for entity in self.schema["entities"] if entity["tableName"] == table)
        return {field["columnName"] for field in entity["fields"]}

    def build_future(self, path):
        with sqlite3.connect(path) as db:
            for entity in self.schema["entities"]:
                db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
                for index in entity.get("indices", []):
                    db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
            for table, row in self.seeds():
                names = ", ".join(row)
                marks = ", ".join("?" for _ in row)
                db.execute(f"INSERT INTO `{table}` ({names}) VALUES ({marks})", list(row.values()))
            db.execute(f"ALTER TABLE `{self.widened}` ADD COLUMN {FUTURE_COLUMN} TEXT NOT NULL DEFAULT 'future'")
            db.execute(f"CREATE TABLE {FUTURE_TABLE} (id INTEGER PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
            db.execute(f"CREATE INDEX index_{FUTURE_TABLE}_payload ON {FUTURE_TABLE} (payload)")
            db.execute(f"INSERT INTO {FUTURE_TABLE} (id, payload) VALUES (1, 'kept')")
            db.execute("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execute("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, ?)", (FUTURE_IDENTITY,))
            db.execute(f"PRAGMA user_version = {self.version + 1}")
        db.close()

    def marker_counts(self):
        counts = {}
        for table, _ in self.seeds():
            counts[table] = counts.get(table, 0) + 1
        return counts


def now_millis():
    return int(time.time() * 1000)


def history_seeds():
    now = now_millis()
    path = f"/sdcard/Scripts/{MARKER}.js"
    yield "file_entry", {"fileId": MARKER, "logicalPath": path, "createdAt": now, "lastSeenAt": now, "latestFingerprint": "fp"}
    for index in (1, 2):
        # Pinned and recent, so the retention pass of HistoryCleanupWorker keeps them.
        # zh-CN: 已固定且较新, HistoryCleanupWorker 的保留策略不会清理.
        yield "revision", {
            "revId": f"{MARKER}-{index}", "fileId": MARKER, "op": "SAVE_PRE", "createdAt": now - index, "logicalPathAtThatTime": path,
            "encoding": "UTF-8", "hadBom": 0, "sizeBytes": 1, "sha256": "sha", "blobRelPath": f"history/blob/{MARKER}/{index}",
            "label": MARKER, "pinned": 1,
        }
    yield "trash_item", {
        "trashId": MARKER, "originalPath": f"/sdcard/Scripts/{MARKER}-trash.js", "fileId": None, "trashedAt": now,
        "isDirectory": 0, "sizeBytes": 1, "sha256": None, "blobRelPath": f"trash/blob/{MARKER}",
    }


def color_seeds():
    now = now_millis()
    yield "palette_history", {"hex": "#FF5722", "last_used_time": now, "use_count": 3, "source": "USER", "source_detail": MARKER}
    yield "color_library", {
        "id": 900, "kind": "CREATED", "is_intelligent": 0, "name": MARKER, "origin": None, "config_json": None, "position": 0,
        "created_time": now, "modified_time": now,
    }
    yield "color_library_color", {"library_id": 900, "name": MARKER, "color": -16738680, "position": 0, "created_time": now, "modified_time": now}


def keystore_seeds(package):
    def seeds():
        path = f"/data/user/0/{package}/{KEYSTORE_FILE}"
        yield "KeyStore", {"absolutePath": path, "filename": Path(path).name, "password": "", "alias": "", "alias_password": "", "verified": 0}
    return seeds


def databases(package):
    return [
        Database("history", "org.autojs.autojs.storage.history.HistoryDatabase", "history-database.db", "revision", history_seeds),
        Database("color", "org.autojs.autojs.theme.app.ColorHistoryDatabase", "color-history-database.db", "color_library", color_seeds),
        Database("keystore", "org.autojs.autojs.apkbuilder.keystore.KeyStoreDatabase", "keystore-database", "KeyStore", keystore_seeds(package)),
    ]


class Result:

    def __init__(self):
        self.items = []

    def add(self, name, passed, detail):
        self.items.append((name, passed, detail))
        print(f"{'PASS' if passed else 'FAIL'} {name}: {detail}")

    @property
    def passed(self):
        return all(passed for _, passed, _ in self.items)


def prepare(dev, dbs, result):
    dev.adb("shell", f"am force-stop {dev.package}")
    stamp = datetime.datetime.now().strftime("%Y%m%d%H%M%S")
    names = dev.entries()
    moved = 0
    for db in dbs:
        for name in [db.file] + [db.file + suffix for suffix in COMPANIONS]:
            if name in names:
                dev.run_as(f"mv databases/{name} databases/{name}.room-pages-{stamp}")
                moved += 1
    dev.run_as(f"mkdir -p files && echo {MARKER} > {KEYSTORE_FILE}")
    with tempfile.TemporaryDirectory() as folder:
        for db in dbs:
            local = Path(folder) / db.file
            db.build_future(local)
            dev.push(local, db.file)
    versions = ", ".join(f"{db.key} {db.version + 1} -> {db.version}" for db in dbs)
    result.add("future files written", True, f"{versions}; {moved} existing file(s) renamed aside")


def launch(dev, observe, result):
    dev.adb("logcat", "-c")
    dev.adb("logcat", "-b", "events", "-c")
    # Debug builds also show the LeakCanary launcher, so resolve-activity answers the chooser.
    # zh-CN: debug 构建还有 LeakCanary 的启动入口, resolve-activity 会返回选择器.
    _, out, _ = dev.adb("shell", f"cmd package query-activities --brief --components -a android.intent.action.MAIN "
                                 f"-c android.intent.category.LAUNCHER {dev.package}")
    components = [line.strip() for line in out.splitlines() if line.strip().startswith(f"{dev.package}/") and "leakcanary" not in line]
    if not components:
        raise CheckError(f"No launcher activity for {dev.package}")
    component = components[0]
    dev.adb("shell", f"am start -W -n {component}", timeout=60)
    time.sleep(observe)
    pid = dev.pid()
    result.add("host start from launcher", bool(pid), f"alive after {observe:g} s" if pid else f"not running after {observe:g} s")


def parse_instrumentation(text):
    tests, current, stack = {}, {}, None
    for line in text.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, _, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
            current[key] = value
            if key == "stack":
                stack = value.strip()
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = int(line.split(":", 1)[1])
            if code != 1 and "test" in current:
                tests[current["test"]] = (code, stack)
            current, stack = {}, None
    crashed = "Process crashed" in text
    return tests, crashed


def run_pages(dev, result):
    test_package = f"{dev.package}.test"
    _, out, _ = dev.adb(
        "shell", f"am instrument -w -r -e class {PAGES_TEST} -e {PAGES_ARGUMENT} true {test_package}/{RUNNER}", timeout=600,
    )
    tests, crashed = parse_instrumentation(out)
    if not tests:
        raise CheckError(f"{PAGES_TEST} did not run (is {test_package} installed?)")
    outcome = {0: "PASS", -2: "FAIL", -4: "SKIPPED"}
    for name, (code, stack) in sorted(tests.items()):
        detail = outcome.get(code, f"code {code}")
        if code != 0 and stack:
            detail += f" ({stack[:160]})"
        result.add(f"instrumentation {name}", code == 0, detail)
    result.add("instrumentation process", not crashed, "process crashed" if crashed else "no crash")


def scan_logs(dev, dbs, result):
    _, main, _ = dev.adb("logcat", "-d", "-b", "main,system,crash", "-v", "brief", timeout=120)
    _, events, _ = dev.adb("logcat", "-d", "-b", "events", "-v", "brief", timeout=120)
    lines = main.splitlines()
    fatal = sum(1 for i, line in enumerate(lines) if "FATAL EXCEPTION" in line
                and any(f"Process: {dev.package}," in next_line for next_line in lines[i + 1:i + 3]))
    uncaught = sum(1 for line in lines if "CrashHandler" in line and "Uncaught Exception" in line)
    crashes = sum(1 for line in events.splitlines() if "am_crash" in line and f",{dev.package}," in line)
    result.add("crashes in logcat", fatal == 0 and uncaught == 0 and crashes == 0,
               f"FATAL EXCEPTION {fatal}, CrashHandler uncaught {uncaught}, am_crash {crashes}")
    room_errors = re.compile(r"A migration from \d+ to \d+ was required|Migration didn't properly handle|Can't downgrade database|"
                             r"Room cannot verify the data integrity")
    errors = sum(1 for line in lines if room_errors.search(line))
    result.add("Room open errors", errors == 0, f"{errors} line(s)")
    for db in dbs:
        rebuilt = sum(1 for line in lines if "RoomDowngrade" in line and f"/{db.file}: file version {db.version + 1} is newer than {db.version}" in line)
        result.add(f"{db.key} rebuild", rebuilt == 1, f"{rebuilt} rebuild(s) logged")
    # Every future table holds the required columns, so no database should be backed up and replaced (roadmap D9).
    # zh-CN: 每张未来表都含必需列, 因此不应有库被备份并换成空库 (路线图 D9).
    recovered = sum(1 for line in lines if "DatabaseRecovery" in line and ": cannot open, kept as " in line)
    result.add("databases sent to recovery", recovered == 0, f"{recovered} database(s)")


def verify(dev, dbs, result):
    dev.adb("shell", f"am force-stop {dev.package}")
    names = dev.entries()
    with tempfile.TemporaryDirectory() as folder:
        for db in dbs:
            if db.file not in names:
                result.add(f"{db.key} file", False, "missing")
                continue
            for name in [db.file] + [db.file + suffix for suffix in COMPANIONS]:
                if name in names:
                    dev.pull(name, Path(folder) / name)
            # Opening the copy applies its -wal, like the next start of the host would.
            # zh-CN: 打开副本时会应用其 -wal, 与宿主下次启动时一致.
            with sqlite3.connect(Path(folder) / db.file) as conn:
                version = conn.execute("PRAGMA user_version").fetchone()[0]
                identity = conn.execute("SELECT identity_hash FROM room_master_table WHERE id = 42").fetchone()
                tables = {row[0] for row in conn.execute("SELECT name FROM sqlite_master WHERE type = 'table'")}
                columns = {row[1] for row in conn.execute(f"PRAGMA table_info(`{db.widened}`)")}
                markers = {table: count_markers(conn, db, table) for table in db.marker_counts()}
                future_rows = conn.execute(f"SELECT COUNT(*) FROM {FUTURE_TABLE}").fetchone()[0] if FUTURE_TABLE in tables else 0
            conn.close()
            leftovers = sorted(name for name in tables if name.startswith("_downgrade_"))
            expected = db.marker_counts()
            result.add(f"{db.key} version", version == db.version and identity and identity[0] == db.identity,
                       f"user_version {version} (code {db.version}), identity hash {'matches' if identity and identity[0] == db.identity else 'differs'}")
            result.add(f"{db.key} marker rows", markers == expected,
                       ", ".join(f"{table} {markers.get(table, 0)}/{count}" for table, count in expected.items()))
            result.add(f"{db.key} columns", columns == db.columns(db.widened),
                       f"{db.widened}: {len(columns)} column(s), future column {'dropped' if FUTURE_COLUMN not in columns else 'still there'}")
            result.add(f"{db.key} extra table", future_rows == 1 and not leftovers,
                       f"{FUTURE_TABLE} rows {future_rows}, rebuild leftovers {len(leftovers)}")


def count_markers(conn, db, table):
    column = {"file_entry": "fileId", "revision": "label", "trash_item": "trashId", "palette_history": "source_detail",
              "color_library": "name", "color_library_color": "name", "KeyStore": "filename"}[table]
    value = f"{MARKER}.bks" if table == "KeyStore" else MARKER
    return conn.execute(f"SELECT COUNT(*) FROM `{table}` WHERE `{column}` = ?", (value,)).fetchone()[0]


def write_report(dev, args, result, started):
    report_dir = Path(args.report_dir)
    if not report_dir.is_absolute():
        report_dir = ROOT / report_dir
    report_dir.mkdir(parents=True, exist_ok=True)
    path = report_dir / f"roundtrip-{dev.name()}-{dev.version_code()}-{started:%Y%m%d}-room.md"
    lines = []
    if not path.exists():
        lines += [f"# Room 三库降级页面核对 ({dev.name()}, versionCode {dev.version_code()})", "",
                  "由 `tools/db-downgrade-room/room_pages.py` 生成 (docs/db-downgrade/ROADMAP.md P2.3). 只含通过 / 失败与关键数值.", ""]
    lines += [f"## {started:%Y-%m-%d %H:%M} {dev.serial}, API {dev.prop('ro.build.version.sdk')}, {dev.package}: "
              f"{'PASS' if result.passed else 'FAIL'}", "", "| 项 | 结果 | 数值 |", "| --- | --- | --- |"]
    lines += [f"| {name} | {'PASS' if passed else 'FAIL'} | {detail.replace('|', '/')} |" for name, passed, detail in result.items]
    with path.open("a", encoding="utf-8", newline="\n") as stream:
        stream.write("\n".join(lines) + "\n\n")
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--package", default=DEFAULT_PACKAGE)
    parser.add_argument("--apk", help="host APK to install first (debuggable)")
    parser.add_argument("--test-apk", help="androidTest APK to install first")
    parser.add_argument("--observe", type=float, default=10.0, help="seconds to watch the host after the launcher start (default 10)")
    parser.add_argument("--report-dir", default="docs/db-downgrade/evidence", help="relative paths start at the repository root")
    args = parser.parse_args()

    dev = Device(args.serial, args.package)
    result = Result()
    started = datetime.datetime.now()
    try:
        for apk in (args.apk, args.test_apk):
            if apk:
                code, out, err = dev.adb("install", "-r", "-g", apk, timeout=300)
                if code != 0:
                    raise CheckError(f"Unable to install {Path(apk).name}: {(out + err).strip()[-200:]}")
        if dev.run_as("id")[0] != 0:
            raise CheckError(f"{dev.package} is not installed or not debuggable (run-as failed)")
        dbs = databases(dev.package)
        prepare(dev, dbs, result)
        launch(dev, args.observe, result)
        run_pages(dev, result)
        scan_logs(dev, dbs, result)
        verify(dev, dbs, result)
    except (CheckError, subprocess.TimeoutExpired) as e:
        result.add("run", False, str(e)[:200])
    finally:
        dev.adb("shell", f"rm -rf {DEVICE_TMP}")
    path = write_report(dev, args, result, started)
    print(f"{'PASS' if result.passed else 'FAIL'}: {sum(1 for item in result.items if item[1])}/{len(result.items)} item(s), report {path.relative_to(ROOT) if path.is_relative_to(ROOT) else path}")
    return 0 if result.passed else 1


if __name__ == "__main__":
    sys.exit(main())
