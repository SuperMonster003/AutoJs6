"""Opens the PLUGINS tab of the home page and watches the Java heap of the host for a while.

Usage: py -3.12 -I -u tools/memory/plugin_center_heap_check.py -s SERIAL [--apk FILE] [--seconds 60] [--interval 2]
    [--max-growth-mb 64] [--max-tail-growth-mb 16]

Background (2026-10-22, API 24 AVD at 560 dpi): the plugin center kept each installed plugin icon as decoded by Resources.
A 432 px PNG in res/mipmap was scaled to 1512 px (9 MB), a day and a night copy per plugin, from both the installed
snapshot and the complete discovery. Before Android 8 bitmap pixels live on the Java heap, so the heap went from about
100 MB to its 512 MB limit within seconds of opening the tab, followed by blocking GCs, an ANR or an OutOfMemoryError.

The script force-stops the host (optionally installs --apk first), starts it from the launcher alias, waits for the home
page, records the Dalvik heap, taps the PLUGINS tab (found in `uiautomator dump` by its label in any translation) and
samples `dumpsys meminfo` every --interval seconds for --seconds. It taps only that tab; it does not change settings.

Output: one PASS / FAIL line per check (process still alive with the same pid, no OutOfMemoryError and no ANR in the
log, heap growth after opening the tab, heap growth during the last third of the window) with the key numbers.
The exit code is 0 when every check passes and 1 otherwise.
"""
import argparse
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "org.autojs.autojs6"
LAUNCH_ACTIVITY = "org.autojs.autojs.launcher.AdaptiveIconAlias"
TAB_STRING = "text_plugins"
RES = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res"
DUMP_FILE = "/sdcard/aj6-plugin-center-heap-check.xml"


def adb(serial, *args, timeout=60):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", timeout=timeout)


def shell(serial, command, timeout=60):
    return adb(serial, "shell", command, timeout=timeout).stdout


def tab_labels():
    """Every translation of the PLUGINS tab title, compared case-insensitively (tabs may show them in upper case)."""
    labels = set()
    for strings in RES.glob("values*/strings.xml"):
        for node in ET.parse(strings).getroot().iter("string"):
            if node.get("name") == TAB_STRING and node.text:
                labels.add(node.text.strip().strip('"').casefold())
    return labels


def pid_of(serial):
    return shell(serial, f"pidof {PACKAGE}").strip()


def dalvik_heap_kb(serial, pid):
    """Allocated Dalvik heap in KB: the second to last number of the "Dalvik Heap" row (Heap Size, Heap Alloc, Heap Free)."""
    for line in shell(serial, f"dumpsys meminfo {pid}", timeout=30).splitlines():
        if line.strip().startswith("Dalvik Heap"):
            numbers = [int(n) for n in re.findall(r"\d+", line)]
            if len(numbers) >= 3:
                return numbers[-2]
    return None


def tab_from_view_dump(serial, index):
    """Fallback when uiautomator never sees the window idle (the API 24 home page): the [index]-th TabView of
    app:id/tab in `dumpsys activity top`, whose bounds are relative to the parent view."""
    stack = []  # (indent, absolute left, absolute top)
    in_tabs, tabs = None, []
    for line in shell(serial, "dumpsys activity top", timeout=30).splitlines():
        m = re.match(r"^(\s*)\S+\{[^}]*? (-?\d+),(-?\d+)-(-?\d+),(-?\d+)(?: #\w+ (\S+))?\}", line)
        if not m:
            continue
        indent = len(m.group(1))
        left, top, right, bottom = (int(m.group(i)) for i in range(2, 6))
        while stack and stack[-1][0] >= indent:
            stack.pop()
        base_left, base_top = (stack[-1][1], stack[-1][2]) if stack else (0, 0)
        stack.append((indent, base_left + left, base_top + top))
        if in_tabs is not None and indent <= in_tabs:
            in_tabs = None
        if m.group(6) == "app:id/tab":
            in_tabs, tabs = indent, []
        elif in_tabs is not None and "TabLayout$TabView" in line:
            tabs.append((base_left + (left + right) // 2, base_top + (top + bottom) // 2))
    return tabs[index] if index < len(tabs) else None


def find_tab(serial, labels, timeout=20.0):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        dump = adb(serial, "shell", f"uiautomator dump {DUMP_FILE}", timeout=30)
        if "idle state" in dump.stdout + dump.stderr:
            return None
        if dump.returncode == 0:
            xml = shell(serial, f"cat {DUMP_FILE}")
            try:
                root = ET.fromstring(xml[xml.find("<"):])
            except ET.ParseError:
                root = None
            if root is not None:
                for node in root.iter("node"):
                    if node.get("package") != PACKAGE:
                        continue
                    texts = {(node.get("content-desc") or "").strip().casefold(), (node.get("text") or "").strip().casefold()}
                    if texts & labels:
                        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
                        if m:
                            x1, y1, x2, y2 = map(int, m.groups())
                            return (x1 + x2) // 2, (y1 + y2) // 2
        time.sleep(1.0)
    return None


def log_since(serial, since, *extra):
    return adb(serial, "logcat", "-d", "-v", "threadtime", "-T", since, *extra, timeout=60).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--apk", help="install this APK (adb install -r) before the check")
    parser.add_argument("--tab-index", type=int, default=2,
                        help="position of the PLUGINS tab, used when uiautomator cannot read the home page")
    parser.add_argument("--seconds", type=int, default=60)
    parser.add_argument("--interval", type=float, default=2.0)
    parser.add_argument("--max-growth-mb", type=float, default=64.0,
                        help="largest allowed heap growth over the value before the tap")
    parser.add_argument("--max-tail-growth-mb", type=float, default=16.0,
                        help="largest allowed growth during the last third of the window")
    args = parser.parse_args()
    serial = args.serial
    results = []

    def check(name, ok, detail):
        results.append(ok)
        print(f"{'PASS' if ok else 'FAIL'} {name}: {detail}")

    if args.apk:
        out = adb(serial, "install", "-r", "-t", args.apk, timeout=600)
        if "Success" not in out.stdout:
            print(f"FAIL install: {(out.stdout + out.stderr).strip().splitlines()[-1:]}")
            return 1
    version = re.search(r"versionCode=(\d+)", shell(serial, f"dumpsys package {PACKAGE}"))
    limit = shell(serial, "getprop dalvik.vm.heapsize").strip()
    density = shell(serial, "wm density").strip().splitlines()[-1:]
    print(f"INFO host versionCode={version.group(1) if version else '?'}, dalvik.vm.heapsize={limit}, {density}")

    labels = tab_labels()
    shell(serial, f"am force-stop {PACKAGE}")
    since = shell(serial, "date +'%m-%d %H:%M:%S.000'").strip()
    shell(serial, f"am start -n {PACKAGE}/{LAUNCH_ACTIVITY}")
    position = find_tab(serial, labels)
    if position is None:
        deadline = time.monotonic() + 20
        while position is None and time.monotonic() < deadline:
            time.sleep(1.5)
            position = tab_from_view_dump(serial, args.tab_index)
        if position is not None:
            print(f"INFO uiautomator could not read the window, tab {args.tab_index} taken from dumpsys activity top")
    pid = pid_of(serial)
    if position is None or not pid:
        check("open home page", False, f"pid={pid or '-'}, PLUGINS tab {'found' if position else 'not found'}")
        return 1
    time.sleep(3)
    before = dalvik_heap_kb(serial, pid)
    shell(serial, f"input tap {position[0]} {position[1]}")

    samples = []
    start = time.monotonic()
    while time.monotonic() - start < args.seconds:
        time.sleep(args.interval)
        if pid_of(serial) != pid:
            break
        heap = dalvik_heap_kb(serial, pid)
        if heap is not None:
            samples.append((time.monotonic() - start, heap))

    alive = pid_of(serial) == pid
    check("process", alive, f"pid {pid} {'alive' if alive else 'gone'} after {time.monotonic() - start:.0f} s")

    own_line = re.compile(rf"^\S+ \S+\s+{pid}\s")
    oom = [line for line in log_since(serial, since).splitlines() if "OutOfMemoryError" in line and own_line.match(line)]
    check("OutOfMemoryError", not oom, f"{len(oom)} log lines")
    events = log_since(serial, since, "-b", "events")
    anr = [line for line in events.splitlines() if "am_anr" in line and PACKAGE in line]
    check("ANR", not anr, f"{len(anr)} am_anr events")

    if before is None or not samples:
        check("heap", False, "no heap samples")
        return 1
    peak = max(heap for _, heap in samples)
    growth = (peak - before) / 1024
    check("heap growth", growth <= args.max_growth_mb,
          f"before {before / 1024:.1f} MB, peak {peak / 1024:.1f} MB (+{growth:.1f}), "
          f"last {samples[-1][1] / 1024:.1f} MB, {len(samples)} samples over {samples[-1][0]:.0f} s")
    tail = [heap for t, heap in samples if t >= args.seconds * 2 / 3]
    if tail:
        tail_growth = (tail[-1] - min(tail)) / 1024
        check("heap tail", tail_growth <= args.max_tail_growth_mb,
              f"last third {min(tail) / 1024:.1f} .. {max(tail) / 1024:.1f} MB, end - min = {tail_growth:+.1f} MB")
    else:
        check("heap tail", False, "no samples in the last third of the window")
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
