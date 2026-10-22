"""Device checks of the automatic backups (docs/settings/ROADMAP.md P9.1 - P9.3, T20, E3).

Usage: py -3.12 -I tools/settings/device_auto_backup_check.py -s SERIAL PHASE --out DIR [--install APK]
       [--first-run-wait SECONDS] [--quiet SECONDS]

PHASE is one of
  prepare        device/auto_backup_check.js keeps the four automatic backup settings, the working directory, the
                 explorer setting "Hidden files" and the names of the automatic backups there are now; checks the
                 defaults (periodic off, weekly, change off, operation on) and that no periodic work is scheduled.
  operation      the operation backup (P9.1) through AndroidSettingsAutoBackup.afterOperation, the call of the import,
                 undo and reset wizards: written while its switch is on, none while it is off. The backup is pulled
                 to DIR and checked with archive_inspect.py: no problems, no credentials and no other; the data files
                 of the data categories that have entries on the device.
  periodic       the periodic backup (P9.2): its switch schedules the unique periodic work (7 days) and its first run
                 writes a backup (the trigger of the item); daily / monthly / weekly update the work in place; eight
                 more backups keep 7; the switch off cancels the work. The first backup is pulled and checked.
  notify         a working directory under a file: the periodic work and a direct call fail, and the notification
                 0xBF5 is in dumpsys (only checked when the app may post notifications).
  change-start   the change backup (P9.3): its switch on, three changes of "Hidden files" in a few seconds.
  change-verify  after the quiet time: exactly one change backup, which is pulled and checked; the switch off
                 schedules nothing more. With all, this script waits for the backup between the two phases (adb ls of
                 the backup directory, at most --quiet + 150 s, then 15 s more for a second one), since a script of the
                 app waiting in the background may be frozen by the system (ColorOS).
  restore        puts every kept value back, deletes the automatic backups written since prepare, the notification
                 and the test files.
  all            every phase above in this order, with the quiet time between change-start and change-verify;
                 restore runs even when a phase fails.

Nothing has to be confirmed on the device. Output is one line per check (PASS / FAIL and key values); the content of
the backups is never printed (R6). The exit status is 1 when a check fails.
"""
import argparse
import pathlib
import re
import sys
import time
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import archive_inspect  # noqa: E402
import device_store_check as base  # noqa: E402

REMOTE_DIR = "/sdcard/Download/aj6-auto-backup-check"
NOTIFICATION = re.compile(r"pkg=" + re.escape(base.PACKAGE) + r" user=\S+ id=3061\b")
PHASES = ["prepare", "operation", "periodic", "notify", "change-start", "change-verify", "restore"]
CHANGE_PREFIX = "autojs6-settings-auto-change-"


def js(value):
    if value is None:
        return "null"
    if isinstance(value, int):
        return str(value)
    return "'" + str(value).replace("\\", "\\\\").replace("'", "\\'") + "'"


def run_phase(args, phase, wait):
    script = (HERE / "device" / "auto_backup_check.js").read_text(encoding="utf-8")
    variables = {
        "AJ6_PHASE": phase,
        "AJ6_OUT": REMOTE_DIR,
        "AJ6_FIRST_RUN_WAIT": args.first_run_wait,
        "AJ6_NOTIFY_WAIT": 30,
    }
    prefix = "".join(f"var {name} = {js(value)};" for name, value in variables.items())
    lines = base.run_script(args.serial, prefix + chr(10) + script, wait)
    failed = 0
    info = {}
    for line in lines:
        kind, _, rest = line.partition(" ")
        if kind in ("PASS", "FAIL", "INFO"):
            print(f"{kind} {args.serial} {phase}: {rest}")
            failed += kind == "FAIL"
            if kind == "INFO" and rest.startswith(("newest ", "directory ")):
                name, _, value = rest.partition(" ")
                info[name] = value.split(",")[0].strip()
    if "done" not in lines:
        print(f"FAIL {args.serial} {phase}: the script did not finish (see logcat GlobalConsole)")
        failed += 1
    return failed, info


def inspect(args, remote):
    """Pulls an automatic backup and checks it: a valid archive without credentials and other."""
    local = args.out / f"{args.serial}-{pathlib.PurePosixPath(remote).name}"
    if base.adb(args.serial, "pull", remote, str(local)).returncode != 0:
        print(f"FAIL {args.serial} {local.name}: not pulled")
        return 1
    inspector = archive_inspect.Inspector()
    result = inspector.inspect(local)
    ids = [row[0] for row in result[2]] if result else []
    for problem in inspector.problems:
        print(f"FAIL {local.name}: {problem}")
    with zipfile.ZipFile(local) as archive:
        listed = set(archive.namelist())
    data = sorted(path for path in archive_inspect.DATA_FILES.values() if path in listed)
    excluded = [category for category in ("credentials", "other") if category in ids]
    ok = not inspector.problems and not excluded
    print(f"{'PASS' if ok else 'FAIL'} {local.name}: {len(ids)} categories, credentials / other "
          f"{'included: ' + ', '.join(excluded) if excluded else 'absent'}, data files {', '.join(data) or 'none'}")
    return len(inspector.problems) + (not ok)


def notifications_allowed(serial, sdk):
    if sdk < 33:
        return True
    out = base.adb(serial, "shell", "dumpsys", "package", base.PACKAGE).stdout
    return "android.permission.POST_NOTIFICATIONS: granted=true" in out


def check_notification(args, sdk):
    out = base.adb(args.serial, "shell", "dumpsys", "notification", "--noredact").stdout
    posted = bool(NOTIFICATION.search(out))
    if notifications_allowed(args.serial, sdk):
        print(f"{'PASS' if posted else 'FAIL'} {args.serial} notify: failure notification 0xBF5 {'posted' if posted else 'missing'}")
        return not posted
    print(f"INFO {args.serial} notify: the app may not post notifications (POST_NOTIFICATIONS), 0xBF5 {'posted' if posted else 'absent'}")
    return 0


def change_backups(serial, directory):
    out = base.adb(serial, "shell", "ls", directory).stdout.split()
    return {name for name in out if name.startswith(CHANGE_PREFIX) and name.endswith(".zip")}


def wait_for_change_backup(args, directory):
    """Waits for the change backup over adb: the first one within --quiet + 150 s, then 15 s more for a second one."""
    before = change_backups(args.serial, directory)
    started = time.time()
    print(f"INFO {args.serial} waiting up to {args.quiet + 150} s for the change backup (quiet time 5 minutes)")
    new = set()
    while time.time() - started < args.quiet + 150 and not new:
        time.sleep(5)
        new = change_backups(args.serial, directory) - before
    seconds = round(time.time() - started)
    if not new:
        print(f"FAIL {args.serial} change: no change backup {seconds} s after change-start")
        return 1
    time.sleep(15)
    new = change_backups(args.serial, directory) - before
    if len(new) == 1:
        print(f"PASS {args.serial} change: one change backup, seen {seconds} s after change-start, none more in 15 s")
    else:
        print(f"FAIL {args.serial} change: {len(new)} change backups 15 s after the first")
    return len(new) != 1


def phase(args, name, sdk):
    if name == "prepare":
        base.adb(args.serial, "shell", "mkdir", "-p", REMOTE_DIR, check=True)
        return run_phase(args, name, 90)[0]
    if name in ("operation", "periodic", "change-verify"):
        wait = {"operation": 90, "periodic": args.first_run_wait + 210, "change-verify": 90}[name]
        failed, info = run_phase(args, name, wait)
        return failed + (inspect(args, info["newest"]) if "newest" in info else 0)
    if name == "notify":
        failed, _ = run_phase(args, name, 120)
        return failed + check_notification(args, sdk)
    if name == "change-start":
        failed, info = run_phase(args, name, 90)
        if args.phase == "all" and "directory" in info:
            failed += wait_for_change_backup(args, info["directory"])
        return failed
    failed, _ = run_phase(args, "restore", 90)
    base.adb(args.serial, "shell", "rm", "-rf", REMOTE_DIR)
    return failed


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("phase", choices=PHASES + ["all"])
    parser.add_argument("--out", type=pathlib.Path, required=True)
    parser.add_argument("--install", type=pathlib.Path)
    parser.add_argument("--first-run-wait", type=int, default=90, help="seconds the first run of the periodic work may take")
    parser.add_argument("--quiet", type=int, default=300, help="quiet time of the change backup in seconds (all)")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    if args.install:
        result = base.adb(args.serial, "install", "-r", "-d", str(args.install))
        print(f"{'PASS' if result.returncode == 0 else 'FAIL'} {args.serial} install {args.install.name}")
        if result.returncode != 0:
            return 1
    model = base.adb(args.serial, "shell", "getprop", "ro.product.model").stdout.strip()
    sdk = int(base.adb(args.serial, "shell", "getprop", "ro.build.version.sdk").stdout.strip() or 0)
    print(f"device {args.serial}: {model} (API {sdk}), host versionCode {base.host_version(args.serial)}")

    failed = 0
    if args.phase == "all":
        try:
            for name in PHASES[:-1]:
                failed += phase(args, name, sdk)
        finally:
            failed += phase(args, "restore", sdk)
    else:
        failed = phase(args, args.phase, sdk)
    print(f"{args.phase}: {'FAIL' if failed else 'PASS'} ({failed} failed)")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
