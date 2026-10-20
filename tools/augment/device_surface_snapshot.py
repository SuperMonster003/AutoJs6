"""Takes a script API surface snapshot on a device and stores it as a sorted, diff-friendly text file.

Usage: py -3.12 -I tools/augment/device_surface_snapshot.py -s SERIAL [--out tools/augment/baseline]
Writes surface-<model>-<versionCode>.txt (one line per property) and the raw surface-<model>-<versionCode>.json.
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
DEVICE_DIR = "/sdcard/Download/aj6-augment-bench"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
PACKAGE = "org.autojs.autojs6"
# The end line of a script run in the Chinese or the English app language.
END_RE = re.compile(r"运行结束|finished in [\d.]+ seconds")


def adb(serial, *args, check=False, text=True):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=text,
                          encoding="utf-8" if text else None, errors="replace" if text else None, check=check)


def console_lines(serial):
    out = adb(serial, "logcat", "-d").stdout
    return [re.sub(r"^.*GlobalConsole: ", "", l).rstrip() for l in out.splitlines() if "GlobalConsole:" in l]


def run_snapshot(serial):
    before = set(console_lines(serial))
    adb(serial, "shell", "mkdir", "-p", DEVICE_DIR, check=True)
    adb(serial, "shell", "rm", "-f", f"{DEVICE_DIR}/surface.json")
    adb(serial, "push", str(HERE / "surface_snapshot.js"), f"{DEVICE_DIR}/surface_snapshot.js", check=True)
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
    args = parser.parse_args()

    snapshot = run_snapshot(args.serial)
    model = adb(args.serial, "shell", "getprop", "ro.product.model").stdout.strip().replace(" ", "_")
    stem = f"surface-{model}-{snapshot['versionCode']}"
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / f"{stem}.json").write_text(json.dumps(snapshot, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    lines = []
    flatten(snapshot["global"], "", lines)
    lines.sort()
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
