"""Takes a script API surface snapshot on a device and stores it as a sorted, diff-friendly text file.

Usage: py -3.12 -I tools/augment/device_surface_snapshot.py -s SERIAL [--out tools/augment/baseline] [--tag TAG]
    [--new-modules on|off] [--extend-builtins on|off] [--roots Array.prototype Number.prototype ...] [--global-classes]
Writes surface-<model>-<versionCode>[-TAG].txt (one line per property) and the raw .json next to it.
--new-modules / --extend-builtins set the switch of the new script module layer (ROADMAP T8) / the "extend JavaScript
built-in objects" option for the snapshot and restore them afterwards. --roots also records the own properties of the
given objects (paths from global, as "Array.prototype"), as lines prefixed with "@<path>." after the global ones.
--global-classes also records the Java class names of global (the fields of augment/global/Classes.kt), which the
global object resolves through its prototype without own properties: one "@lookup.<name>" line each with `in`, own
property, `typeof` of the bare name, and `typeof` / String of the value read from global (surface_compare.py
--only @lookup compares them). logcat is cleared before each script starts and polled through its last lines, as in
device_bench.py.
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from device_bench import clear_log, console_lines  # noqa: E402
CLASSES_KT = HERE.parents[1] / "app/src/main/java/org/autojs/autojs/runtime/api/augment/global/Classes.kt"
DEVICE_DIR = "/sdcard/Download/aj6-augment-bench"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
PACKAGE = "org.autojs.autojs6"
# The end line of a script run in the Chinese or the English app language.
END_RE = re.compile(r"运行结束|finished in [\d.]+ seconds")
SWITCH_MARK = "AJ6SWITCH"
SWITCH_KEYS = {
    "new_modules": ("key_$_new_script_module_layer", "new script module layer"),
    "extend_builtins": ("key_$_extending_js_build_in_objects", "extend JavaScript built-in objects"),
}


def adb(serial, *args, check=False, text=True):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=text,
                          encoding="utf-8" if text else None, errors="replace" if text else None, check=check)


def set_pref(serial, key, value, label):
    """Sets the boolean preference [key] through Pref.putBooleanSync in a small script and returns its previous value."""
    lines = [
        "var pref = org.autojs.autojs.core.pref.Pref.INSTANCE;",
        f"var before = pref.getBoolean('{key}', false);",
        f"pref.putBooleanSync('{key}', {'true' if value else 'false'});",
        f"console.log('{SWITCH_MARK} ' + before + ' ' + pref.getBoolean('{key}', false));",
    ]
    local = HERE / "__pycache__" / "aj6_switch.js"
    local.parent.mkdir(exist_ok=True)
    local.write_text(chr(10).join(lines) + chr(10), encoding="utf-8")
    remote = f"{DEVICE_DIR}/aj6_switch.js"
    adb(serial, "shell", "mkdir", "-p", DEVICE_DIR, check=True)
    adb(serial, "push", str(local), remote, check=True)
    clear_log(serial)
    before_lines = set(console_lines(serial))
    adb(serial, "shell", "am", "start", "-n", f"{PACKAGE}/{RUN_ACTIVITY}", "-e", "path", remote, check=True)
    deadline = time.time() + 30
    while time.time() < deadline:
        time.sleep(0.5)
        for line in console_lines(serial):
            start = line.find(SWITCH_MARK + " ")
            if line in before_lines or start < 0:
                continue
            before, after = line[start + len(SWITCH_MARK) + 1:].split()[:2]
            if after != ("true" if value else "false"):
                sys.exit(f"the preference did not change: {line}")
            print(f"{label}: {before} -> {after}")
            return before == "true"
    sys.exit("no preference output from the device")


def global_class_names():
    """The field names of Classes.kt in declaration order (Canvas is commented out there)."""
    names = re.findall(r"^\s*val\s+(\w+)\s*=", CLASSES_KT.read_text(encoding="utf-8"), re.MULTILINE)
    if not names:
        sys.exit(f"no class names found in {CLASSES_KT}")
    return names


def run_snapshot(serial, roots, lookup_names):
    clear_log(serial)
    before = set(console_lines(serial))
    adb(serial, "shell", "mkdir", "-p", DEVICE_DIR, check=True)
    adb(serial, "shell", "rm", "-f", f"{DEVICE_DIR}/surface.json")
    source = (HERE / "surface_snapshot.js").read_text(encoding="utf-8")
    source = source.replace("let EXTRA_ROOTS = [];", "let EXTRA_ROOTS = " + json.dumps(roots) + ";", 1)
    source = source.replace("let LOOKUP_NAMES = [];", "let LOOKUP_NAMES = " + json.dumps(lookup_names) + ";", 1)
    local = HERE / "__pycache__" / "surface_snapshot.js"
    local.parent.mkdir(exist_ok=True)
    local.write_text(source, encoding="utf-8")
    adb(serial, "push", str(local), f"{DEVICE_DIR}/surface_snapshot.js", check=True)
    adb(serial, "shell", "am", "start", "-n", f"{PACKAGE}/{RUN_ACTIVITY}", "-e", "path", f"{DEVICE_DIR}/surface_snapshot.js", check=True)
    deadline = time.time() + 120
    seen = []
    while time.time() < deadline:
        time.sleep(0.5)
        seen = [l for l in console_lines(serial) if l not in before]
        if any(END_RE.search(l) for l in seen):
            break
    errors = [l for l in seen if "/E:" in l]
    if errors:
        print("script errors:", *errors, sep="\n  ")
    raw = adb(serial, "exec-out", "cat", f"{DEVICE_DIR}/surface.json", text=False).stdout
    if not raw.strip():
        raise SystemExit("no surface.json was produced; console: " + " | ".join(seen[-5:]))
    return json.loads(raw.decode("utf-8"))


def attrs(entry):
    flags = ""
    flags += "E" if entry.get("enumerable") else "-"
    if entry.get("accessor"):
        flags += "A"
        flags += "G" if entry.get("hasGetter") else "-"
        flags += "S" if entry.get("hasSetter") else "-"
    else:
        flags += "W" if entry.get("writable") else "-"
    flags += "C" if entry.get("configurable") else "-"
    return flags


def value_summary(value):
    if value is None:
        return "?"
    t = value.get("type")
    if "error" in value:
        return "error " + value["error"]
    if t == "function":
        return f"function name={value.get('name')!r} length={value.get('length')}"
    if t == "object":
        extra = []
        if value.get("javaClass"):
            extra.append(value["javaClass"])
        if "length" in value:
            extra.append(f"length={value['length']}")
        if value.get("proto"):
            extra.append("proto=" + " > ".join(value["proto"]))
        return f"object {value.get('tag')} " + " ".join(extra)
    if "value" in value:
        return f"{t} {value['value']!r}"
    return t


def flatten(props, prefix, lines):
    for name, entry in props.items():
        path = f"{prefix}{name}"
        if "error" in entry or entry.get("missing"):
            lines.append(f"{path}\t{'error ' + entry.get('error', 'missing')}")
            continue
        value = entry.get("value")
        lines.append(f"{path}\t{attrs(entry)}\t{value_summary(value) if not entry.get('accessor') else 'accessor'}")
        if value and value.get("props"):
            flatten(value["props"], path + ".", lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--out", type=pathlib.Path, default=HERE / "baseline")
    parser.add_argument("--tag", help="suffix of the output file names, as on / off")
    parser.add_argument("--new-modules", choices=["on", "off"], help="set the new script module layer switch for the snapshot")
    parser.add_argument("--extend-builtins", choices=["on", "off"], help="set the extend JavaScript built-in objects option for the snapshot")
    parser.add_argument("--roots", nargs="*", default=[], help="further objects to record, as paths from global (Array.prototype)")
    parser.add_argument("--global-classes", action="store_true", help="record the Java class names global resolves through its prototype")
    args = parser.parse_args()

    previous = {}
    try:
        for option, (key, label) in SWITCH_KEYS.items():
            wanted = getattr(args, option)
            if wanted:
                previous[option] = (set_pref(args.serial, key, wanted == "on", label), wanted == "on")
        snapshot = run_snapshot(args.serial, args.roots, global_class_names() if args.global_classes else [])
    finally:
        for option, (before, wanted) in previous.items():
            if before != wanted:
                key, label = SWITCH_KEYS[option]
                set_pref(args.serial, key, before, label)
    model = adb(args.serial, "shell", "getprop", "ro.product.model").stdout.strip().replace(" ", "_")
    stem = f"surface-{model}-{snapshot['versionCode']}" + (f"-{args.tag}" if args.tag else "")
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / f"{stem}.json").write_text(json.dumps(snapshot, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    lines = []
    flatten(snapshot["global"], "", lines)
    lines.sort()
    for root, props in snapshot.get("roots", {}).items():
        root_lines = []
        flatten(props, f"@{root}.", root_lines)
        lines += sorted(root_lines)
    for name, entry in sorted(snapshot.get("lookup", {}).items()):
        where = f"in={str(entry['inGlobal']).lower()} own={str(entry['own']).lower()}"
        value = f"error {entry['error']}" if "error" in entry else f"bare={entry['bare']} {entry['type']} {entry['text']}"
        lines.append(f"@lookup.{name}\t{where}\t{value}")
    header = [
        f"# AutoJs6 script API surface: {snapshot['versionName']} ({snapshot['versionCode']}) on {snapshot['device']}",
        f"# global own properties: {len(snapshot['global'])}; Object.keys(global): {len(snapshot['keysOrder'])}",
        "# columns: path, attributes (E enumerable, W writable, C configurable, A accessor with G getter / S setter), value",
        "# keys order: " + " ".join(snapshot["keysOrder"]),
        "# $ aliases: " + " ".join(f"{k}={v}" for k, v in sorted(snapshot["aliases"].items())),
    ]
    (args.out / f"{stem}.txt").write_text("\n".join(header + lines) + "\n", encoding="utf-8")
    print(f"wrote {args.out / (stem + '.txt')} ({len(lines)} properties) and {stem}.json")


if __name__ == "__main__":
    sys.exit(main())
