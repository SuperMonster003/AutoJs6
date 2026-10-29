"""Device helpers shared by the selector tools (ROADMAP selector P0): adb, running a script through RunIntentActivity,
pulling its JSON output, the screens of the scenarios and the switch of the new script module layer.

The scripts take their settings from a prelude `var AJ6SEL = {...};` written in front of them, write their result to the
JSON file named in `AJ6SEL.out` and log `AJ6SEL done` at the end (GlobalConsole lines in logcat, as
tools/augment/device_bench.py reads them). Only the result file carries data; the log lines are the progress marks.
"""
import json
import pathlib
import re
import subprocess
import sys
import time

# py -I ignores PYTHONIOENCODING; messages from the device (Chinese and others) are printed as UTF-8.
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

HERE = pathlib.Path(__file__).resolve().parent
HOST = HERE.parents[1]
BASELINE = HERE / "baseline"
WORK = HERE / "__pycache__"
DEVICE_DIR = "/sdcard/Download/aj6-selector"
PACKAGE = "org.autojs.autojs6"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
MAIN_ACTIVITY = "org.autojs.autojs.ui.main.MainActivity"
SWITCH_KEY = "key_$_new_script_module_layer"
SWITCH_MARK = "AJ6SWITCH"
MARK = "AJ6SEL"
# The end line of a script run in the Chinese or the English app language.
END_RE = re.compile(r"运行结束|finished in [\d.]+ seconds")
# The logcat lines read per poll (tools/augment/device_bench.py: some devices log hundreds of lines a second).
LOG_TAIL = 50000
# FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP: the main screen on top of its task.
MAIN_FLAGS = "0x34000000"


def adb(serial, *args, check=False, timeout=120):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", check=check, timeout=timeout)


def shell(serial, command, check=False, timeout=120):
    return adb(serial, "shell", command, check=check, timeout=timeout).stdout.strip()


def device_info(serial):
    """Model, API level and the host versionCode of the device (the build number is versionCode - 1652)."""
    model = shell(serial, "getprop ro.product.model")
    sdk = shell(serial, "getprop ro.build.version.sdk")
    dump = shell(serial, f"dumpsys package {PACKAGE}")
    m = re.search(r"versionCode=(\d+)", dump)
    version_code = int(m.group(1)) if m else None
    return {
        "serial": serial,
        "model": model,
        "sdk": int(sdk) if sdk.isdigit() else sdk,
        "versionCode": version_code,
        "build": version_code - 1652 if version_code else None,
        "date": time.strftime("%Y-%m-%d %H:%M"),
    }


def host_scheduling(serial):
    """The scheduling group of the host process (ta / fg / bg; memory: timings differ 4-6 times between groups)."""
    for line in shell(serial, "ps -A -o PID,PCY,NAME").splitlines():
        parts = line.split()
        if len(parts) == 3 and parts[2] == PACKAGE:
            return parts[1]
    return None


def a11y_enabled(serial):
    services = shell(serial, "settings get secure enabled_accessibility_services")
    return f"{PACKAGE}/" in services


def clear_log(serial):
    adb(serial, "logcat", "-c")


def console_lines(serial):
    out = adb(serial, "logcat", "-d", "-t", str(LOG_TAIL)).stdout
    return [re.sub(r"^.*GlobalConsole: ", "", l).rstrip() for l in out.splitlines() if "GlobalConsole:" in l]


def push(serial, local, name=None):
    adb(serial, "shell", "mkdir", "-p", DEVICE_DIR, check=True)
    remote = f"{DEVICE_DIR}/{name or pathlib.Path(local).name}"
    adb(serial, "push", str(local), remote, check=True)
    return remote


def prepare_script(script, config, name=None):
    """Writes `var AJ6SEL = config;` followed by the script into the work directory and returns the local path."""
    WORK.mkdir(exist_ok=True)
    local = WORK / (name or script)
    body = (HERE / script).read_text(encoding="utf-8")
    local.write_text("var AJ6SEL = " + json.dumps(config, ensure_ascii=True) + ";" + chr(10) + body, encoding="utf-8")
    return local


def run_script(serial, remote, wait):
    """Clears logcat, starts the script and returns its GlobalConsole lines until `AJ6SEL done`, the end line of the
    run or the timeout."""
    clear_log(serial)
    before = set(console_lines(serial))
    adb(serial, "shell", "am", "start", "-n", f"{PACKAGE}/{RUN_ACTIVITY}", "-e", "path", remote, check=True)
    deadline = time.time() + wait
    seen = []
    seen_set = set()

    def collect():
        for l in console_lines(serial):
            if l in before or l in seen_set:
                continue
            seen_set.add(l)
            seen.append(l)

    while time.time() < deadline:
        time.sleep(1)
        collect()
        if any(f"{MARK} done" in l or END_RE.search(l) for l in seen):
            time.sleep(1)
            collect()
            break
    return seen


def run_json(serial, script, config, wait, name=None):
    """Runs [script] with [config] and returns (result, lines): the parsed JSON file the script wrote (None when it
    wrote none) and its console lines."""
    stem = pathlib.Path(name or script).stem
    out = f"{DEVICE_DIR}/{stem}.out.json"
    adb(serial, "shell", "rm", "-f", out)
    config = dict(config, out=out)
    remote = push(serial, prepare_script(script, config, name))
    lines = run_script(serial, remote, wait)
    WORK.mkdir(exist_ok=True)
    local = WORK / f"{stem}.out.json"
    if local.exists():
        local.unlink()
    adb(serial, "pull", out, str(local))
    if not local.exists():
        return None, lines
    return json.loads(local.read_text(encoding="utf-8")), lines


def script_errors(lines, limit=5):
    """The error lines of a run (GlobalConsole `/E:`) and the progress marks, without node text."""
    return [l for l in lines if "/E:" in l][:limit]


def screen_settings(serial):
    """The home page of the system settings, restarted (-S) so that no sub-page is open; returns its package."""
    resolved = shell(serial, "cmd package resolve-activity --brief -a android.settings.SETTINGS").splitlines()
    component = resolved[-1].strip() if resolved else ""
    started = adb(serial, "shell", "am", "start", "-S", "-W", "-a", "android.settings.SETTINGS")
    if "Error" in started.stdout + started.stderr and "/" in component:
        # API 28 (G8441): with -S, am does not resolve the implicit intent ("Intent does not match any activities").
        adb(serial, "shell", "am", "start", "-S", "-W", "-n", component)
    time.sleep(3)
    return component.split("/")[0] if "/" in component else "com.android.settings"


def screen_main(serial):
    adb(serial, "shell", "am", "start", "-W", "-f", MAIN_FLAGS, "-n", f"{PACKAGE}/{MAIN_ACTIVITY}")
    time.sleep(3)
    return PACKAGE


def screen_home(serial):
    adb(serial, "shell", "am", "start", "-W", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
    time.sleep(3)
    return PACKAGE


def pref_value_js(key):
    """JS expression of the value scripts get for the boolean preference [key] (tools/augment/device_bench.py)."""
    if key != SWITCH_KEY:
        return f"pref.getBoolean('{key}', false)"
    return ("(function () { try { return String(org.autojs.autojs.core.pref.keys.DeveloperKeys.NEW_SCRIPT_MODULE_LAYER.get()) === 'true'; }"
            f" catch (e) {{ return pref.getBoolean('{key}', false); }} }})()")


def new_modules(serial, value=None):
    """Reads the switch of the new script module layer, or sets it to [value]; returns the value before."""
    WORK.mkdir(exist_ok=True)
    script = WORK / "aj6sel_switch.js"
    lines = [
        "var pref = org.autojs.autojs.core.pref.Pref.INSTANCE;",
        f"var before = {pref_value_js(SWITCH_KEY)};",
    ]
    if value is not None:
        lines.append(f"pref.putBooleanSync('{SWITCH_KEY}', {'true' if value else 'false'});")
    lines.append(f"console.log('{SWITCH_MARK} ' + before + ' ' + {pref_value_js(SWITCH_KEY)});")
    lines.append(f"console.log('{MARK} done');")
    script.write_text(chr(10).join(lines) + chr(10), encoding="utf-8")
    remote = push(serial, script)
    for line in run_script(serial, remote, 30):
        start = line.find(SWITCH_MARK + " ")
        if start >= 0:
            before, after = line[start + len(SWITCH_MARK) + 1:].split()[:2]
            if value is not None and after != ("true" if value else "false"):
                sys.exit(f"the switch did not change: {line}")
            return before == "true"
    sys.exit("no switch output from the device")


def with_new_modules(serial, value, fn):
    """Runs fn(state) with the switch of the new script module layer at [value] ("on" / "off" / None: as it is) and
    restores the previous value afterwards; state is the value in effect."""
    previous = new_modules(serial)
    wanted = previous if value is None else value == "on"
    if wanted != previous:
        new_modules(serial, wanted)
    try:
        return fn(wanted)
    finally:
        if wanted != previous:
            new_modules(serial, previous)
            print(f"new script module layer restored to {previous}")
