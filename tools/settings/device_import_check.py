"""Device checks of the import wizard, "Undo last import" and "Restore default settings..." (docs/settings/ROADMAP.md
P7.5 / P7.4 / P8.1, appendix D items 1 - 6, E3).

Usage: py -3.12 -I tools/settings/device_import_check.py -s SERIAL PHASE --out DIR [--install APK] [--wait SECONDS]
       [--expect A|B|flipped|original] [--language-a TAG --language-b TAG] [--icon-a KEY --icon-b KEY]
       [--categories IDS]

PHASE is one of
  prepare  device/import_check.js keeps every setting of the app (an archive sealed with its device key) and its
           internal backups in its private directory, sets the "A" values (theme color, night mode, editor text size,
           plugin sort, a test GitHub token; with --language-a / --icon-a also the language and the launcher icon),
           writes /sdcard/Download/aj6-import-check/a.zip (the default categories and credentials, as the export
           wizard does) and b.zip (plugin_enable_state with one installed plugin flipped and a package that is not
           installed, item 4), then sets and applies the "B" values. a-v2.zip, a copy of a.zip with formatVersion 2
           (item 3, archive_inspect.py --set-format-version), is pushed next to them. a.zip is pulled to DIR.
  open     opens "Manage AutoJs6 settings" with the import wizard (device/open_transfer.js); the maintainer chooses the
           file and goes through the wizard (no taps are sent).
  page     opens "Manage AutoJs6 settings" without a wizard ("Undo last import", "Restore default settings...").
  verify   checks that the values are --expect (A after an import of a.zip, B after its undo), applied without a
           restart (theme color, night mode; language and launcher icon when given), the test token kept, the
           internal backups at most 5; pulls the newest operation backup written since prepare (T20, P9.1) to DIR
           and checks it with archive_inspect.py: no credentials.
  plugins  checks the plugin state after b.zip (--expect flipped) or its undo (--expect original) and that the package
           that is not installed was not written (item 4).
  reset    checks a reset to defaults of --categories (P8.1, item 6): their keys are gone, appearance stays.
  restore  imports the kept settings back, puts the internal backups back, deletes the operation backups written
           since prepare, the private files and /sdcard/Download/aj6-import-check.
  scan     indexes the files of /sdcard/Download/aj6-import-check in the media store (prepare does it too), so that
           the system file picker lists files pushed there by adb.
  screen   saves a screenshot to DIR/<serial>-<n>.png.

The wizard itself is gone through by the maintainer (dialogs, the system file picker); this script only sets values,
writes the test files and compares the state before and after. Output is one line per check (PASS / FAIL and key
values); the token and other credentials are never printed (R6). The exit status is 1 when a check fails.
"""
import argparse
import pathlib
import sys

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


def scan(serial, sdk):
    """Indexes the test files, which the system file picker lists only once the media store knows them."""
    if sdk >= 29:
        base.adb(serial, "shell", "content", "call", "--uri", "content://media/", "--method", "scan_volume", "--arg", "external_primary")
    else:
        for name in ("a.zip", "b.zip", "a-v2.zip"):
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
    parser.add_argument("phase", choices=["prepare", "open", "page", "verify", "plugins", "reset", "restore", "scan", "screen"])
    parser.add_argument("--out", type=pathlib.Path, required=True)
    parser.add_argument("--install", type=pathlib.Path)
    parser.add_argument("--wait", type=int, default=90)
    parser.add_argument("--expect", default="A")
    parser.add_argument("--language-a")
    parser.add_argument("--language-b")
    parser.add_argument("--icon-a")
    parser.add_argument("--icon-b")
    parser.add_argument("--categories")
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
        failed = open_page(args, "import" if args.phase == "open" else None)
    elif args.phase == "verify":
        failed, newest = run_phase(args, "verify")
        if newest:
            local = args.out / f"{args.serial}-{pathlib.PurePosixPath(newest).name}"
            if base.adb(args.serial, "pull", newest, str(local)).returncode == 0:
                failed += inspect(local, expect_credentials=False)
    elif args.phase in ("plugins", "reset"):
        failed, _ = run_phase(args, args.phase)
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
