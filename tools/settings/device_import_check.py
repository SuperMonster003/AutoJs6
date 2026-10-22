"""Device checks of the import wizard, "Undo last import" and "Restore default settings..." (docs/settings/ROADMAP.md
P7.5 / P7.4 / P8.1 / P7.7 / P10.2 / P10.3 / P11.1, appendix D items 1 - 6, 13 and 14, E3).

Usage: py -3.12 -I tools/settings/device_import_check.py -s SERIAL PHASE --out DIR [--install APK] [--wait SECONDS]
       [--expect A|B|flipped|original|...] [--language-a TAG --language-b TAG] [--icon-a KEY --icon-b KEY]
       [--categories IDS] [--wizard import|export] [--file NAME|newest] [--to SERIAL] [--rounds N]

PHASE is one of
  prepare  device/import_check.js keeps every setting of the app (an archive sealed with its device key) and its
           internal backups in its private directory, sets the "A" values (theme color, night mode, editor text size,
           plugin sort, a test GitHub token; with --language-a / --icon-a also the language and the launcher icon),
           writes /sdcard/Download/aj6-import-check/a.zip (the default categories and credentials, as the export
           wizard does) and b.zip (plugin_enable_state with one installed plugin flipped and a package that is not
           installed, item 4), then sets and applies the "B" values. a-v2.zip, a copy of a.zip with formatVersion 2
           (item 3, archive_inspect.py --set-format-version), is pushed next to them. a.zip is pulled to DIR.
  open     opens "Manage AutoJs6 settings" with the import wizard, or the export wizard with --wizard export
           (device/open_transfer.js); the maintainer chooses the file and goes through the wizard (no taps are sent).
  page     opens "Manage AutoJs6 settings" without a wizard ("Undo last import", "Restore default settings...").
  verify   checks that the values are --expect (A after an import of a.zip, B after its undo), applied without a
           restart (theme color, night mode; language and launcher icon when given), the test token kept, the
           internal backups at most 5; pulls the newest operation backup written since prepare (T20, P9.1) to DIR
           and checks it with archive_inspect.py: no credentials.
  plugins  checks the plugin state after b.zip (--expect flipped) or its undo (--expect original) and that the package
           that is not installed was not written (item 4).
  reset    checks a reset to defaults of --categories (P8.1, item 6): their keys are gone, appearance stays (not
           checked when appearance is one of them).
  deploy-prepare  after prepare: writes c.zip (P7.6, items 10 - 12), the explorer and editor appearance categories of
           this device whose working directory names a directory that does not exist and whose editor font is a
           catalog font that is not downloaded.
  deploy-verify   checks the two deployment items after an import of c.zip: --expect deployed (directory created,
           font downloaded, both keys written) or --expect declined (both keys kept); restore deletes them again.
  keep     keeps the settings and internal backups like prepare, without setting test values (for the phases below).
  pbkdf2   times one PBKDF2 derivation of the password slot with the iterations the export writes (P7.7, appendix C
           Q30): the first run and --rounds warm runs, with the app in the foreground as in the wizard; on Android 8+
           also the "PBKDF2WithHmacSHA256" of the platform for comparison (same key).
  token    after keep: sets the test GitHub token --expect A or B and writes d.zip, the credentials sealed with the
           device key only (an archive of before P7.7). token-verify checks which test token is stored (--expect A,
           B or absent); access checks how this device reads the credentials of --file (--expect DEVICE, PASSWORD,
           LOCKED or none). --file names a file in /sdcard/Download/aj6-import-check, "newest" its newest .zip (the
           file the maintainer exported into it).
  copy     pulls --file to DIR, prints the slots of its credentials (device / password, P7.7) and pushes it to the
           device --to, whose media store then indexes it.
  data-prepare  after keep: adds three test shortcuts to the shortcut backup (the first one without an icon) and two
           test color libraries (P10.2, item 13). data-inspect checks the data of --file (the export of the
           maintainer: 2 test shortcuts, 1 test color library); data-delete deletes the test entries; data-verify
           checks them after the import (--expect imported: the exported ones are back and the last operation records
           them as added) or its undo (--expect undone: gone again, the record cleared; P10.3).
  welcome-reset  after keep: clears key_setup_wizard_welcome_done and starts the app anew (force-stop, launcher), so
           that its home page shows the welcome (P11.1, item 14); welcome-verify checks that the key is set again.
  restore  imports the kept settings back, puts the internal backups back, deletes the operation backups written
           since prepare, the test shortcuts and color libraries, the private files and
           /sdcard/Download/aj6-import-check.
  scan     indexes the files of /sdcard/Download/aj6-import-check in the media store (prepare does it too), so that
           the system file picker lists files pushed there by adb.
  screen   saves a screenshot to DIR/<serial>-<n>.png.

The wizard itself is gone through by the maintainer (dialogs, the system file picker); this script only sets values,
writes the test files and compares the state before and after. Output is one line per check (PASS / FAIL and key
values); the token and other credentials are never printed (R6). The exit status is 1 when a check fails.
"""
import argparse
import json
import pathlib
import sys
import time
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import archive_inspect  # noqa: E402
import device_store_check as base  # noqa: E402

REMOTE_DIR = "/sdcard/Download/aj6-import-check"
ACTIVITY = "org.autojs.autojs.ui.settings.transfer.SettingsTransferActivity"


def js(value):
    if value is None:
        return "null"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "\\'") + "'"


def run_phase(args, phase, **extra):
    script = (HERE / "device" / "import_check.js").read_text(encoding="utf-8")
    variables = {
        "AJ6_PHASE": phase,
        "AJ6_OUT": REMOTE_DIR,
        "AJ6_EXPECT": args.expect,
        "AJ6_LANGUAGE_A": args.language_a,
        "AJ6_LANGUAGE_B": args.language_b,
        "AJ6_ICON_A": args.icon_a,
        "AJ6_ICON_B": args.icon_b,
        "AJ6_LANGUAGE_EXPECT": {"A": args.language_a, "B": args.language_b}.get(args.expect) if args.language_a else None,
        "AJ6_ICON_EXPECT": {"A": args.icon_a, "B": args.icon_b}.get(args.expect) if args.icon_a else None,
        "AJ6_CATEGORIES": args.categories,
        "AJ6_FILE": None,
        "AJ6_ROUNDS": args.rounds,
    }
    variables.update(extra)
    prefix = "".join(f"var {name} = {js(value)};" for name, value in variables.items())
    lines = base.run_script(args.serial, prefix + chr(10) + script, args.wait)
    failed = 0
    newest = None
    for line in lines:
        kind, _, rest = line.partition(" ")
        if kind in ("PASS", "FAIL", "INFO"):
            print(f"{kind} {args.serial} {rest}")
            failed += kind == "FAIL"
            if kind == "INFO" and "newest " in rest:
                newest = rest.rsplit("newest ", 1)[1].strip()
    if "done" not in lines:
        print(f"FAIL {args.serial} the script did not finish (see logcat GlobalConsole)")
        failed += 1
    return failed, newest


def inspect(local, expect_credentials):
    inspector = archive_inspect.Inspector()
    result = inspector.inspect(local)
    ids = []
    if result:
        _, _, rows = result
        ids = [row[0] for row in rows]
    for problem in inspector.problems:
        print(f"FAIL {local.name}: {problem}")
    failed = len(inspector.problems)
    has_credentials = "credentials" in ids
    ok = has_credentials == expect_credentials
    print(f"{'PASS' if ok else 'FAIL'} {local.name}: {len(ids)} categories, credentials {'included' if has_credentials else 'absent'}")
    return failed + (not ok)


def remote_file(serial, name):
    """REMOTE_DIR/name, or the newest .zip there for "newest"; None when there is none."""
    if name != "newest":
        return f"{REMOTE_DIR}/{name}"
    found = base.adb(serial, "shell", f"ls -t {REMOTE_DIR}/*.zip").stdout.split()
    return found[0] if found and found[0].endswith(".zip") else None


def credential_slots(local):
    """The kinds of the slots of the sealed credentials of an archive ("device", "password"); empty without them."""
    with zipfile.ZipFile(local) as archive:
        for name in archive.namelist():
            if not name.endswith(".json") or name == "manifest.json":
                continue
            try:
                doc = json.loads(archive.read(name).decode("utf-8"))
            except ValueError:
                continue
            if isinstance(doc, dict) and doc.get("id") == "credentials" and isinstance(doc.get("sealed"), dict):
                return [slot.get("kind") for slot in doc["sealed"].get("slots", []) if isinstance(slot, dict)]
    return []


def launch(serial):
    """Starts the app as the launcher does (its launcher alias, not the one of LeakCanary in debug builds)."""
    out = base.adb(serial, "shell", "cmd", "package", "query-activities", "--brief", "-a", "android.intent.action.MAIN",
                   "-c", "android.intent.category.LAUNCHER", base.PACKAGE).stdout.split()
    names = [name for name in out if name.startswith(base.PACKAGE + "/") and "leakcanary" not in name]
    if names:
        base.adb(serial, "shell", "am", "start", "-n", names[0])
    else:
        base.adb(serial, "shell", "monkey", "-p", base.PACKAGE, "-c", "android.intent.category.LAUNCHER", "1")
    print(f"INFO {serial} app started from the launcher")


def sdk_of(serial):
    return int(base.adb(serial, "shell", "getprop", "ro.build.version.sdk").stdout.strip() or 0)


def scan(serial, sdk, names=("a.zip", "b.zip", "a-v2.zip")):
    """Indexes the test files, which the system file picker lists only once the media store knows them."""
    if sdk >= 29:
        base.adb(serial, "shell", "content", "call", "--uri", "content://media/", "--method", "scan_volume", "--arg", "external_primary")
    else:
        for name in names:
            base.adb(serial, "shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", f"file://{REMOTE_DIR}/{name}")
    print(f"INFO {serial} media store scanned for {REMOTE_DIR}")


def open_page(args, wizard):
    script = (HERE / "device" / "open_transfer.js").read_text(encoding="utf-8")
    prefix = f"var AJ6_ACTIVITY = '{ACTIVITY}'; var AJ6_WIZARD = {js(wizard)}; var AJ6_REMEMBERED = null;"
    lines = base.run_script(args.serial, prefix + chr(10) + script, args.wait)
    failed = 0
    for line in lines:
        kind, _, rest = line.partition(" ")
        if kind in ("PASS", "FAIL"):
            print(f"{kind} {args.serial} {rest}")
            failed += kind == "FAIL"
    return failed


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("phase", choices=[
        "prepare", "open", "page", "verify", "plugins", "reset", "deploy-prepare", "deploy-verify", "restore", "scan", "screen",
        "keep", "pbkdf2", "token", "token-verify", "access", "copy",
        "data-prepare", "data-inspect", "data-delete", "data-verify", "welcome-reset", "welcome-verify",
    ])
    parser.add_argument("--out", type=pathlib.Path, required=True)
    parser.add_argument("--install", type=pathlib.Path)
    parser.add_argument("--wait", type=int, default=90)
    parser.add_argument("--expect", default="A")
    parser.add_argument("--language-a")
    parser.add_argument("--language-b")
    parser.add_argument("--icon-a")
    parser.add_argument("--icon-b")
    parser.add_argument("--categories")
    parser.add_argument("--wizard", default="import", choices=["import", "export"])
    parser.add_argument("--file", default="newest")
    parser.add_argument("--to")
    parser.add_argument("--rounds", type=int, default=5)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    if args.install:
        result = base.adb(args.serial, "install", "-r", "-d", str(args.install))
        print(f"{'PASS' if result.returncode == 0 else 'FAIL'} {args.serial} install {args.install.name}")
        if result.returncode != 0:
            return 1
    model = base.adb(args.serial, "shell", "getprop", "ro.product.model").stdout.strip()
    sdk = base.adb(args.serial, "shell", "getprop", "ro.build.version.sdk").stdout.strip()
    print(f"device {args.serial}: {model} (API {sdk}), host versionCode {base.host_version(args.serial)}")

    failed = 0
    if args.phase == "prepare":
        base.adb(args.serial, "shell", "mkdir", "-p", REMOTE_DIR, check=True)
        failed, _ = run_phase(args, "prepare")
        local = args.out / f"{args.serial}-a.zip"
        if base.adb(args.serial, "pull", f"{REMOTE_DIR}/a.zip", str(local)).returncode == 0:
            failed += inspect(local, expect_credentials=True)
            v2 = args.out / f"{args.serial}-a-v2.zip"
            archive_inspect.set_format_version(local, v2, 2)
            if base.adb(args.serial, "push", str(v2), f"{REMOTE_DIR}/a-v2.zip").returncode == 0:
                print(f"PASS {args.serial} a-v2.zip pushed (formatVersion 2)")
                scan(args.serial, int(sdk or 0))
            else:
                print(f"FAIL {args.serial} a-v2.zip not written")
                failed += 1
        else:
            print(f"FAIL {args.serial} a.zip was not written")
            failed += 1
    elif args.phase in ("open", "page"):
        failed = open_page(args, args.wizard if args.phase == "open" else None)
    elif args.phase == "verify":
        failed, newest = run_phase(args, "verify")
        if newest:
            local = args.out / f"{args.serial}-{pathlib.PurePosixPath(newest).name}"
            if base.adb(args.serial, "pull", newest, str(local)).returncode == 0:
                failed += inspect(local, expect_credentials=False)
    elif args.phase in ("plugins", "reset", "deploy-verify"):
        failed, _ = run_phase(args, args.phase)
    elif args.phase == "deploy-prepare":
        failed, _ = run_phase(args, args.phase)
        scan(args.serial, int(sdk or 0), ("c.zip",))
    elif args.phase == "pbkdf2":
        # In the foreground, as in the wizard: some ROMs (ColorOS) slow a script of a background app down 25 - 150 times.
        launch(args.serial)
        time.sleep(3)
        failed, _ = run_phase(args, args.phase)
    elif args.phase in ("keep", "token-verify", "data-prepare", "data-delete", "data-verify", "welcome-verify"):
        failed, _ = run_phase(args, args.phase)
    elif args.phase == "token":
        base.adb(args.serial, "shell", "mkdir", "-p", REMOTE_DIR, check=True)
        failed, _ = run_phase(args, args.phase)
        scan(args.serial, int(sdk or 0), ("d.zip",))
    elif args.phase in ("access", "data-inspect"):
        remote = remote_file(args.serial, args.file)
        if remote:
            print(f"INFO {args.serial} file {pathlib.PurePosixPath(remote).name}")
            failed, _ = run_phase(args, args.phase, AJ6_FILE=remote)
        else:
            print(f"FAIL {args.serial} no .zip in {REMOTE_DIR}")
            failed = 1
    elif args.phase == "copy":
        remote = remote_file(args.serial, args.file)
        local = args.out / f"{args.serial}-{pathlib.PurePosixPath(remote).name}" if remote else None
        if not local or base.adb(args.serial, "pull", remote, str(local)).returncode != 0:
            print(f"FAIL {args.serial} {args.file} was not pulled")
            failed = 1
        else:
            inspector = archive_inspect.Inspector()
            inspector.inspect(local)
            for problem in inspector.problems:
                print(f"FAIL {local.name}: {problem}")
            failed = len(inspector.problems)
            print(f"INFO {local.name}: credentials slots [{', '.join(map(str, credential_slots(local)))}]")
            if args.to:
                name = pathlib.PurePosixPath(remote).name
                base.adb(args.to, "shell", "mkdir", "-p", REMOTE_DIR, check=True)
                if base.adb(args.to, "push", str(local), f"{REMOTE_DIR}/{name}").returncode == 0:
                    print(f"PASS {args.to} {name} pushed")
                    scan(args.to, sdk_of(args.to), (name,))
                else:
                    print(f"FAIL {args.to} {name} not pushed")
                    failed += 1
    elif args.phase == "welcome-reset":
        failed, _ = run_phase(args, args.phase)
        if not failed:
            base.adb(args.serial, "shell", "am", "force-stop", base.PACKAGE)
            launch(args.serial)
    elif args.phase == "restore":
        failed, _ = run_phase(args, "restore")
        base.adb(args.serial, "shell", "rm", "-rf", REMOTE_DIR)
    elif args.phase == "scan":
        scan(args.serial, int(sdk or 0))
    elif args.phase == "screen":
        index = len(list(args.out.glob(f"{args.serial}-screen-*.png"))) + 1
        base.screenshot(args.serial, args.out / f"{args.serial}-screen-{index}.png")
    print(f"{args.phase}: {'FAIL' if failed else 'PASS'} ({failed} failed)")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
