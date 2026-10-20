"""Opens the developer options page of the installed host and takes a screenshot (roadmap P1.3).

Usage: py -3.12 -I tools/augment/device_developer_options.py -s SERIAL [--out DIR] [--new-modules on|off] [--debug-print on|off]

DeveloperOptionsActivity is not exported, so `am start` from adb cannot open it; a small script started through
RunIntentActivity opens it from the app's own process instead (no taps). --new-modules / --debug-print set the hidden
preferences first (as device_eval.py does) and are not restored, so the screenshot shows those values; run again with
the old values to restore them. The screenshot is pulled to --out (default: tools/augment/__pycache__/) and the
pushed script is removed. The screen must be on and unlocked; the script only checks and reports it.
"""
import argparse
import pathlib
import re
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
DEVICE_DIR = "/sdcard/Download/aj6-augment-bench"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
PACKAGE = "org.autojs.autojs6"
ACTIVITY = "org.autojs.autojs.ui.settings.DeveloperOptionsActivity"
MARK = "AJ6OPEN"
KEYS = {
    "new_modules": "key_$_new_script_module_layer",
    "debug_print": "key_$_script_module_debug_print",
}


def adb(serial, *args, check=False):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", check=check)


def console_lines(serial):
    out = adb(serial, "logcat", "-d").stdout
    return [re.sub(r"^.*GlobalConsole: ", "", line).rstrip() for line in out.splitlines() if "GlobalConsole:" in line]


def run_script(serial, name, lines, wait=30):
    """Runs a script from [lines] and returns the first value printed after the MARK, or exits on a timeout."""
    local = HERE / "__pycache__" / name
    local.parent.mkdir(exist_ok=True)
    local.write_text(chr(10).join(lines) + chr(10), encoding="utf-8")
    remote = f"{DEVICE_DIR}/{name}"
    adb(serial, "shell", "mkdir", "-p", DEVICE_DIR, check=True)
    adb(serial, "push", str(local), remote, check=True)
    before = set(console_lines(serial))
    adb(serial, "shell", "am", "start", "-n", f"{PACKAGE}/{RUN_ACTIVITY}", "-e", "path", remote, check=True)
    deadline = time.time() + wait
    while time.time() < deadline:
        time.sleep(0.5)
        for line in console_lines(serial):
            start = line.find(MARK + " ")
            if line not in before and start >= 0:
                return line[start + len(MARK) + 1:]
    sys.exit(f"no {MARK} output from {name}")


def screen_state(serial):
    power = adb(serial, "shell", "dumpsys", "power").stdout
    window = adb(serial, "shell", "dumpsys", "window").stdout
    awake = "mWakefulness=Awake" in power
    locked = bool(re.search(r"(mDreamingLockscreen=true|isKeyguardShowing=true|mShowingLockscreen=true|showing=true)", window))
    return awake, locked


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--out", type=pathlib.Path, default=HERE / "__pycache__")
    parser.add_argument("--new-modules", choices=["on", "off"])
    parser.add_argument("--debug-print", choices=["on", "off"])
    parser.add_argument("--wait", type=float, default=2.5, help="seconds between opening the page and the screenshot")
    args = parser.parse_args()
    s = args.serial

    awake, locked = screen_state(s)
    if not awake or locked:
        sys.exit(f"the screen is {'off' if not awake else 'locked'}; turn it on and unlock it, then run again")

    for option, key in KEYS.items():
        value = getattr(args, option)
        if value:
            result = run_script(s, "aj6_pref.js", [
                "var pref = org.autojs.autojs.core.pref.Pref.INSTANCE;",
                f"pref.putBooleanSync('{key}', {'true' if value == 'on' else 'false'});",
                f"console.log('{MARK} ' + pref.getBoolean('{key}', false));",
            ])
            print(f"{key} = {result}")

    run_script(s, "aj6_open_developer_options.js", [
        f"var intent = new android.content.Intent(context, {ACTIVITY});",
        # CLEAR_TOP recreates an open developer options page, so it shows the preferences just written.
        "intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);",
        "context.startActivity(intent);",
        f"console.log('{MARK} started');",
    ])
    time.sleep(args.wait)
    top = adb(s, "shell", "dumpsys", "activity", "activities").stdout
    resumed = next((line.strip() for line in top.splitlines() if "topResumedActivity" in line or "mResumedActivity" in line), "")
    print(f"resumed: {resumed}")
    args.out.mkdir(parents=True, exist_ok=True)
    shot = args.out / f"developer-options-{s}-{time.strftime('%H%M%S')}.png"
    with shot.open("wb") as f:
        subprocess.run(["adb", "-s", s, "exec-out", "screencap", "-p"], stdout=f, check=True)
    adb(s, "shell", "rm", "-rf", DEVICE_DIR)
    print(f"screenshot: {shot}")
    if ACTIVITY.rsplit(".", 1)[1] not in resumed:
        sys.exit("the developer options page is not in front")


if __name__ == "__main__":
    main()
