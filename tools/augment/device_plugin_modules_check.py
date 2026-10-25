"""ROADMAP P4.3: the modules of the Bundled Libraries plugin on a device in the four plugin states.

    py -3.12 -I tools/augment/device_plugin_modules_check.py -s SERIAL --plugin-apk PLUGIN.apk [--local-first]

The host on the device must be a build without the 14 module files in assets/modules (design
docs/augment/P4-3-bundled-libraries-design.md (g), roadmap Q102), except with --local-first, which expects the normal
build (files present) and checks that every module still loads with the plugin not installed. The plugin states, in
order and in one host process (so the broadcasts of each change are what makes it visible):

    not-installed      the plugin is uninstalled first
    enabled            adb install of --plugin-apk
    plugin-disabled    the Plugin Center switch, set off through PluginEnableStore by a script (and on again after)
    library-disabled   axios and buffer switched off (the switches of the plugin page, through the receiver of the debug
                       build of the plugin; on again after)
    uninstalled        adb uninstall

In each state bench/plugin_modules_smoke/main.js runs once; the "AJ6REQ <item> <json>" lines must match the expectation
of the state (an item without one, as the getters of an available library, must be there; position-axios, the error
position, is printed only). Until P5.2 the script ran with the switch of the new script module layer off and then on,
and the two runs had to print the same. Prints one line per state and item group and PASS / FAIL. The plugin is left
uninstalled and the device directory removed.
"""
import argparse
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import device_bench as db  # noqa: E402
import device_require_check as drc  # noqa: E402

PLUGIN = "io.github.supermonster003.autojs6.plugin.bundled.libraries"
LOCAL = db.HERE / "bench" / "plugin_modules_smoke"
REMOTE = f"{db.DEVICE_DIR}/p43"
SWITCH_MARK = "AJ6PLUGIN"

# Library of each require item. zh-CN: 每个 require 项所属的库.
LIBRARY = {
    "axios": "axios", "cheerio": "cheerio", "dayjs": "dayjs", "i18n": "i18n", "banana-i18n": "i18n", "pangu": "pangu",
    "lodash": "lodash", "buffer": "buffer", "events": "events", "fs": "fs", "os": "os", "path": "path",
    "process": "process", "node-path": "path", "nodejs": "nodejs",
}
# The library a module needs first (fs: buffer, nodejs: process then buffer). zh-CN: 模块首先依赖的库.
NEEDS = {"fs": "buffer", "nodejs": "buffer"}
OK_VALUES = {
    "axios": "function", "cheerio": "x", "dayjs": 0, "i18n": "function", "banana-i18n": "function", "pangu": 9,
    "lodash": "2,4", "buffer": 2, "events": 7, "fs": True, "os": "string", "path": True, "process": "object",
    "node-path": "/", "nodejs": "function",
}
GETTERS = ["axios", "cheerio", "dayjs", "i18n", "pangu"]
DISABLED_LIBRARIES = ["axios", "buffer"]


def expected(state):
    """{item: expected value} of [state]; an item without one may print anything (a getter: "ok" with any value)."""
    result = {}

    def unavailable(library, reason):
        return ["throws", "Error", "MODULE_NOT_FOUND", library, reason]

    for item, library in LIBRARY.items():
        key = f"require-{item}"
        if state in ("enabled", "local-first"):
            result[key] = ["ok", OK_VALUES[item]]
        elif state in ("not-installed", "uninstalled", "plugin-disabled"):
            reason = "plugin-disabled" if state == "plugin-disabled" else "not-installed"
            result[key] = unavailable(library, reason)
        elif state == "library-disabled":
            needed = library if library in DISABLED_LIBRARIES else NEEDS.get(item)
            result[key] = unavailable(needed, "library-disabled") if needed in DISABLED_LIBRARIES else ["ok", OK_VALUES[item]]
    for name in GETTERS:
        key = f"getter-{name}"
        if state in ("not-installed", "uninstalled", "plugin-disabled"):
            result[key] = unavailable(name, "plugin-disabled" if state == "plugin-disabled" else "not-installed")
        elif state == "library-disabled" and name in DISABLED_LIBRARIES:
            result[key] = unavailable(name, "library-disabled")
        else:
            result[key] = None  # "ok" with the type of the module
    available = state in ("enabled", "local-first")
    result["identity-axios"] = ["ok", True] if available else None
    result["message-axios"] = "no throw" if available else None
    return result


def run_script_lines(serial, text, mark, wait=30):
    """Runs a one-off script and returns the value after [mark] in its console output."""
    script = db.HERE / "__pycache__" / "aj6_p43_step.js"
    script.parent.mkdir(exist_ok=True)
    script.write_text(text, encoding="utf-8")
    db.adb(serial, "push", str(script), f"{REMOTE}/step.js", check=True)
    for line in db.run_script(serial, f"{REMOTE}/step.js", wait):
        start = line.find(mark + " ")
        if start >= 0:
            return line[start + len(mark) + 1:].strip()
    sys.exit(f"no {mark} output from the device")


def set_plugin_enabled(serial, enabled):
    value = "true" if enabled else "false"
    text = (
        "var store = org.autojs.autojs.core.plugin.center.PluginEnableStore.INSTANCE;\n"
        "var context = org.autojs.autojs.app.GlobalAppContext.get();\n"
        f"store.setEnabled(context, '{PLUGIN}', {value});\n"
        f"console.log('{SWITCH_MARK} ' + store.isEnabled(context, '{PLUGIN}', true));\n"
    )
    after = run_script_lines(serial, text, SWITCH_MARK)
    if after != value:
        sys.exit(f"the Plugin Center switch did not change: {after}")


def set_libraries(serial, libraries, enabled):
    """The switches of the plugin page, through the receiver of its debug build (`pm disable` of the aliases is refused
    to the shell since Android 9); the broadcast starts the plugin process, AutoJs6 sees PACKAGE_CHANGED."""
    value = "true" if enabled else "false"
    out = db.adb(serial, "shell", "am", "broadcast", "-n", f"{PLUGIN}/{PLUGIN}.DebugLibrarySwitchReceiver",
                 "--es", "library", ",".join(libraries), "--ez", "enabled", value, check=True).stdout
    want = ",".join(f"{library}={value}" for library in libraries)
    if f'data="{want}"' not in out:
        sys.exit(f"the library switches did not change: {out.strip()}")


def installed(serial):
    return PLUGIN in db.adb(serial, "shell", "pm", "list", "packages", PLUGIN).stdout


def run_smoke(serial, wait):
    return drc.run_marked(serial, f"{REMOTE}/main.js", wait)


def check(state, run):
    items, finished = run
    failures = [] if finished else [f"{state}: run did not finish"]
    want = expected(state)
    groups = {}
    for item in dict.fromkeys(list(want) + list(items)):
        got = items.get(item)
        if item == "position-axios":
            print(f"{state} {item}: {got} (registered)")
            continue
        if got is None:
            failures.append(f"{state} {item}")
            print(f"{state} {item}: MISSING")
            continue
        value = json.loads(got)
        if item.startswith("getter-") and want.get(item) is None:
            ok = value[0] == "ok"
        elif want.get(item) is None:
            ok = True
        else:
            ok = value == want[item]
        groups.setdefault(ok, []).append(item)
        if not ok:
            failures.append(f"{state} {item}")
            print(f"{state} {item}: FAIL {got} (expected {json.dumps(want.get(item))})")
    print(f"{state}: {len(groups.get(True, []))} items as expected, {len(groups.get(False, []))} failed")
    if "message-axios" in items and want.get("message-axios") is None:
        print(f"{state} message-axios: {items['message-axios']}")
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--plugin-apk", help="the debug APK of AutoJs6-Plugin-Bundled-Libraries")
    parser.add_argument("--local-first", action="store_true", help="the host still has the files: modules load without the plugin")
    parser.add_argument("--wait", type=int, default=60, help="seconds to wait for each run")
    args = parser.parse_args()
    s = args.serial
    if not args.local_first and not args.plugin_apk:
        parser.error("--plugin-apk is required without --local-first")
    db.adb(s, "shell", "rm", "-rf", REMOTE)
    db.adb(s, "shell", "mkdir", "-p", db.DEVICE_DIR, check=True)
    db.adb(s, "push", str(LOCAL), REMOTE, check=True)
    failures = []
    try:
        if installed(s):
            db.adb(s, "uninstall", PLUGIN, check=True)
        if args.local_first:
            failures += check("local-first", run_smoke(s, args.wait))
        else:
            failures += check("not-installed", run_smoke(s, args.wait))
            db.adb(s, "install", "-r", args.plugin_apk, check=True)
            failures += check("enabled", run_smoke(s, args.wait))
            set_plugin_enabled(s, False)
            failures += check("plugin-disabled", run_smoke(s, args.wait))
            set_plugin_enabled(s, True)
            set_libraries(s, DISABLED_LIBRARIES, False)
            failures += check("library-disabled", run_smoke(s, args.wait))
            set_libraries(s, DISABLED_LIBRARIES, True)
            db.adb(s, "uninstall", PLUGIN, check=True)
            failures += check("uninstalled", run_smoke(s, args.wait))
    finally:
        if installed(s):
            db.adb(s, "uninstall", PLUGIN)
        db.adb(s, "shell", "rm", "-rf", REMOTE)
    print("FAIL: " + ", ".join(failures) if failures else "PASS")
    return 1 if failures else 0

if __name__ == "__main__":
    sys.exit(main())
