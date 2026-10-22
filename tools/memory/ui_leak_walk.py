"""Walks the main screens of AutoJs6 with LeakCanary on and reports, page by page, the leaks LeakCanary finds (docs/memory/ROADMAP.md P4.5).

Usage: py -3.12 -I -u tools/memory/ui_leak_walk.py -s SERIAL [--pages drawer,explorer,...] [--reps 3] [--tag TAG]
    [--evidence DIR] [--shots] [--analysis-wait 300] [--no-cold-start] [--keep-dump-setting] [--list]

The walk drives the host through adb only: `am start` for the exported entry points, `input tap / keyevent / swipe` on the
controls found in `uiautomator dump`. Labels are matched against every translation in app/src/main/res/values*/strings.xml,
so the walk does not depend on the language of the app. Each page is entered and left --reps times; then the script forces
two garbage collections in the app process (`run-as <package> kill -USR1 <pid>`, which makes ART collect) and reads the
LeakCanary log (tag LeakCanary): the objects it watched (destroyed activities, fragments, views, root views, services and
the runtimes of finished scripts) and how many of them stayed retained. When any did, the app is sent to the background so
that LeakCanary dumps and analyzes the heap, and the analysis is attributed to the page.

LeakCanary's own "Dump heap automatically" switch (Leaks app > About) is off by default since P1.2. The script turns it on
for the walk through the app's shared preferences (run-as, the app is stopped first) and restores the previous value at the
end, unless --keep-dump-setting is given. The LeakCanary switch of the developer options must be on (its default); the
script checks the startup log line of LeakCanarySetup and stops otherwise. In the same way the plugin center switch of the
Offline Documentation plugin is turned on for the walk when it is off (--docs-plugin as-is leaves it), so that the
documents tab, the documentation activity and the docs pane of the editor load pages instead of the "unavailable" view.

Dialogs that need a person (permission requests, pairing confirmations of other apps) are never tapped: when a window of
another package comes to the front, the script prints what is shown and waits for the maintainer to handle it on the phone.

Output: one line per page with PASS / LEAK / FAIL, the number of watched and retained objects and a one-line summary per
leak (leaking class, first suspect reference, signature prefix). The analysis texts (the text LeakCanary shows in its leak
list) go to --evidence (default docs/memory/evidence/) as ui-walk-<model>-<versionCode>-<tag>-<date>.md; --shots also
saves a screenshot per page and one of LeakCanary's leak list at the end (scaled to 540 px wide) in a folder of that name.
"""
import argparse
import datetime
import pathlib
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ElementTree

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
PACKAGE = "org.autojs.autojs6"
MAIN_ACTIVITY = "org.autojs.autojs.ui.main.MainActivity"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
DEVICE_DIR = "/sdcard/Download/aj6-ui-walk"
DUMP_FILE = "/sdcard/Download/aj6-ui-walk/window.xml"
LEAK_PREFS = "shared_prefs/LeakCanaryHeapDumpPrefs.xml"
DUMP_KEY = "AboutScreenDumpEnabled"
ENABLE_PREFS = "shared_prefs/plugin_center_enable_state.xml"
OFFLINE_DOCS = "io.github.supermonster003.autojs6.plugin.offlinedocs"

WATCH_RE = re.compile(r"Watching (?:instance of )?(\S+)(?: \((.*)\))? with key")
FOUND_RE = re.compile(r"Found (\d+) objects? retained")
NONE_RETAINED = "All retained objects have been garbage collected"
DUMP_TIME_RE = re.compile(r"^Heap dump timestamp: (\d+)")
LEAK_COUNT_RE = re.compile(r"^(\d+) (APPLICATION|LIBRARY) LEAKS?")
SETUP_RE = re.compile(r"LeakCanary has been set up as (enabled|disabled)")
ADB_DOWN_RE = re.compile(r"daemon not running|cannot connect to daemon|could not read ok from ADB Server")


def log(text=""):
    print(text, flush=True)


# ----------------------------------------------------------------------------------------------------------------------
# Device

class Node:
    def __init__(self, attrs):
        self.text = attrs.get("text", "")
        self.rid = attrs.get("resource-id", "")
        self.desc = attrs.get("content-desc", "")
        self.cls = attrs.get("class", "")
        self.pkg = attrs.get("package", "")
        self.clickable = attrs.get("clickable") == "true"
        self.scrollable = attrs.get("scrollable") == "true"
        self.selected = attrs.get("selected") == "true"
        self.checked = attrs.get("checked") == "true"
        m = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", attrs.get("bounds", ""))
        self.bounds = tuple(int(g) for g in m.groups()) if m else (0, 0, 0, 0)

    @property
    def center(self):
        x1, y1, x2, y2 = self.bounds
        return (x1 + x2) // 2, (y1 + y2) // 2

    @property
    def area(self):
        x1, y1, x2, y2 = self.bounds
        return max(0, x2 - x1) * max(0, y2 - y1)

    def __repr__(self):
        return f"Node({self.cls.rsplit('.', 1)[-1]} id={self.rid.rsplit('/', 1)[-1]!r} text={self.text[:30]!r} desc={self.desc[:30]!r})"


class Device:
    def __init__(self, serial):
        self.serial = serial
        self.nodes = []
        self.width, self.height = self._size()
        self.shell(f"mkdir -p {DEVICE_DIR}")

    def adb(self, *args, check=False, timeout=60, attempts=12):
        """Runs adb, waiting out a stalled adb server (it is shared with other sessions) for about four minutes."""
        for attempt in range(attempts):
            try:
                result = subprocess.run(["adb", "-s", self.serial, *args], capture_output=True, text=True,
                                        encoding="utf-8", errors="replace", timeout=timeout)
            except subprocess.TimeoutExpired:
                result = None
            if result is not None and not ADB_DOWN_RE.search(result.stderr or ""):
                if check and result.returncode != 0:
                    raise subprocess.CalledProcessError(result.returncode, result.args, result.stdout, result.stderr)
                return result
            if attempt == 0:
                log("      adb is not responding, waiting for it")
            time.sleep(20)
        raise WalkError("adb did not respond for about four minutes")

    def shell(self, command, timeout=60):
        return self.adb("shell", command, timeout=timeout).stdout

    def _size(self):
        out = self.shell("wm size")
        m = re.findall(r"(\d+)x(\d+)", out)
        w, h = m[-1] if m else ("1080", "2400")
        return int(w), int(h)

    def prop(self, name):
        return self.shell(f"getprop {name}").strip()

    def tap(self, x, y, pause=0.8):
        self.shell(f"input tap {x} {y}")
        time.sleep(pause)

    def key(self, name, pause=0.8):
        self.shell(f"input keyevent {name}")
        time.sleep(pause)

    def back(self, pause=1.0):
        self.key("KEYCODE_BACK", pause)

    def home(self, pause=1.0):
        self.key("KEYCODE_HOME", pause)

    def swipe(self, x1, y1, x2, y2, ms=300, pause=0.8):
        self.shell(f"input swipe {x1} {y1} {x2} {y2} {ms}")
        time.sleep(pause)

    def scroll_down(self, node=None):
        x1, y1, x2, y2 = node.bounds if node else (0, 0, self.width, self.height)
        cx = (x1 + x2) // 2
        self.swipe(cx, y1 + (y2 - y1) * 3 // 4, cx, y1 + (y2 - y1) // 4, 300)

    def scroll_up(self, node=None):
        x1, y1, x2, y2 = node.bounds if node else (0, 0, self.width, self.height)
        cx = (x1 + x2) // 2
        self.swipe(cx, y1 + (y2 - y1) // 4, cx, y1 + (y2 - y1) * 3 // 4, 300)

    def dump(self, attempts=4):
        """Reads the window hierarchy; uiautomator fails now and then while the screen animates, hence the retries."""
        for _ in range(attempts):
            out = self.shell(f"uiautomator dump {DUMP_FILE}", timeout=30)
            if "dumped to" in out:
                xml = self.adb("exec-out", "cat", DUMP_FILE).stdout
                try:
                    root = ElementTree.fromstring(xml)
                except ElementTree.ParseError:
                    time.sleep(0.6)
                    continue
                self.nodes = [Node(e.attrib) for e in root.iter("node")]
                return self.nodes
            time.sleep(0.6)
        self.nodes = []
        return self.nodes

    def find_all(self, text=None, rid=None, desc=None, cls=None, fresh=True):
        """Nodes matching every given criterion. [text] / [desc] are a set of accepted strings or a compiled pattern,
        [rid] is the resource name after `id/`, [cls] the simple class name."""
        nodes = self.dump() if fresh else self.nodes
        return [n for n in nodes if matches(n, text, rid, desc, cls)]

    def find(self, **criteria):
        found = self.find_all(**criteria)
        return found[0] if found else None

    def wait(self, timeout=10.0, **criteria):
        deadline = time.time() + timeout
        while True:
            node = self.find(**criteria)
            if node or time.time() > deadline:
                return node
            time.sleep(0.5)

    def tap_node(self, node, pause=1.0):
        self.tap(*node.center, pause=pause)

    def long_press(self, node, ms=900, pause=1.5):
        x, y = node.center
        self.swipe(x, y, x, y, ms, pause)

    def tap_any(self, timeout=8.0, **criteria):
        node = self.wait(timeout, **criteria)
        if node is None:
            raise WalkError(f"not found: {describe(criteria)}")
        self.tap_node(node)
        return node

    def top(self):
        """(package, activity) of the focused window."""
        out = self.shell("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
        m = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([^/\s}]+)(?:/([^\s}]+))?", out)
        if m:
            return m.group(1), (m.group(2) or "")
        m = re.search(r"ActivityRecord\{\S+ \S+ ([^/\s]+)/(\S+)", out)
        return (m.group(1), m.group(2)) if m else ("", "")

    def top_activity(self):
        pkg, act = self.top()
        return act.rsplit(".", 1)[-1] if pkg == PACKAGE else f"{pkg}/{act}"

    def wait_activity(self, simple_name, timeout=10.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if self.top_activity() == simple_name:
                return True
            time.sleep(0.4)
        return False

    def screenshot(self, path, width=540):
        path.parent.mkdir(parents=True, exist_ok=True)
        data = subprocess.run(["adb", "-s", self.serial, "exec-out", "screencap", "-p"], capture_output=True,
                              check=True, timeout=60).stdout
        path.write_bytes(data)
        try:
            from PIL import Image
            with Image.open(path) as image:
                if image.width > width:
                    image = image.resize((width, image.height * width // image.width))
                image.save(path, optimize=True)
        except ImportError:
            pass
        return path


class WalkError(Exception):
    pass


def matches(node, text, rid, desc, cls):
    def ok(value, wanted):
        if wanted is None:
            return True
        if isinstance(wanted, re.Pattern):
            return bool(wanted.search(value))
        return value in wanted

    if rid is not None and node.rid.rsplit("/", 1)[-1] != rid:
        return False
    if cls is not None and node.cls.rsplit(".", 1)[-1] != cls:
        return False
    return ok(node.text, text) and ok(node.desc, desc)


def describe(criteria):
    parts = []
    for k, v in criteria.items():
        if isinstance(v, re.Pattern):
            v = v.pattern
        elif isinstance(v, (set, frozenset)):
            v = sorted(v)[0] + (f" (+{len(v) - 1})" if len(v) > 1 else "")
        parts.append(f"{k}={v}")
    return ", ".join(parts)


# ----------------------------------------------------------------------------------------------------------------------
# Labels from the string resources of the app, every language

_STRINGS = {}


def _load_strings():
    if _STRINGS:
        return
    for values in sorted(RES.glob("values*/strings.xml")):
        try:
            root = ElementTree.parse(values).getroot()
        except ElementTree.ParseError:
            continue
        for e in root.iter("string"):
            name = e.get("name")
            text = "".join(e.itertext()).strip().replace("\\'", "'").replace('\\"', '"')
            if name and text and not text.startswith("@string/"):
                _STRINGS.setdefault(name, set()).add(text)


def label(*names):
    """Every translation of the string resources [names]."""
    _load_strings()
    found = set()
    for name in names:
        if name not in _STRINGS:
            raise KeyError(f"no string resource {name}")
        found |= _STRINGS[name]
    return frozenset(found)


def label_prefix(*names):
    """A pattern matching text that starts with any translation of [names] (for titles with counts or suffixes)."""
    return re.compile("^(?:" + "|".join(re.escape(t) for t in sorted(label(*names), key=len, reverse=True)) + ")")


# ----------------------------------------------------------------------------------------------------------------------
# LeakCanary

class LeakLog:
    """The LeakCanary log lines since the walk started, kept across polls (the ring buffer may drop early lines)."""

    def __init__(self, dev):
        self.dev = dev
        self.since = dev.shell("date '+%m-%d %H:%M:%S.000'").strip()
        self.lines = []
        self.seen = set()

    def poll(self):
        # Nanosecond timestamps keep identical trace lines (two "Object[] array" steps, say) apart in [seen].
        out = self.dev.adb("logcat", "-d", "-v", "time", "-v", "nsec", "-T", self.since,
                           "-s", "LeakCanary:*", "LeakCanarySetup:*").stdout
        for line in out.splitlines():
            if line in self.seen or not re.search(r"[VDIWEF]/LeakCanary", line):
                continue
            self.seen.add(line)
            self.lines.append(re.sub(r"^.*?[VDIWEF]/LeakCanary(?:Setup)?\(\s*\d+\):\s?", "", line).rstrip())
        return self.lines

    def mark(self):
        self.poll()
        return len(self.lines)


def analysis_blocks(lines):
    """Every complete "HEAP ANALYSIS RESULT" block (from the separator before it to the one after the dump duration)."""
    blocks = []
    starts = [i for i, l in enumerate(lines) if "HEAP ANALYSIS RESULT" in l]
    for n, first in enumerate(starts):
        limit = starts[n + 1] if n + 1 < len(starts) else len(lines)
        for j in range(first, limit):
            if lines[j].startswith("Heap dump duration"):
                end = j + 1 if j + 1 < limit and lines[j + 1].startswith("====") else j
                blocks.append(lines[max(first - 1, 0):end + 1])
                break
    return blocks


def dump_timestamp(block):
    for line in block:
        m = DUMP_TIME_RE.match(line.strip())
        if m:
            return int(m.group(1))
    return 0


def parse_leaks(block):
    """[(kind, signature, leaking class, first suspect reference, trace lines)] of an analysis block."""
    leaks = []
    kind = None
    current = None
    signature = ""
    for raw in block:
        line = raw.replace("\u200b", "").rstrip()
        stripped = line.strip()
        m = LEAK_COUNT_RE.match(stripped)
        if m:
            kind, current = m.group(2).lower(), None
            continue
        if re.match(r"^\d+ UNREACHABLE OBJECTS?|^METADATA", stripped):
            kind, current = None, None
            continue
        if line.startswith("===="):
            current = None
            continue
        if kind is None:
            continue
        if line.startswith("Signature: "):
            signature = line.split(": ", 1)[1].strip()
            current = None
            continue
        if line.startswith("\u252c"):  # the first line of a leak trace
            current = {"kind": kind, "signature": signature, "lines": [], "leaking": "", "suspects": []}
            leaks.append(current)
        if current is None:
            continue
        current["lines"].append(line)
        if stripped.startswith("\u2570\u2192"):
            current["leaking"] = stripped[2:].strip()
        if stripped.lstrip("\u2502 ").startswith("~") and len(current["lines"]) >= 2:
            current["suspects"].append(current["lines"][-2].strip().lstrip("\u2502\u251c\u2500\u2193 ").strip())
    return [(l["kind"], l["signature"], l["leaking"], l["suspects"][0] if l["suspects"] else "", l["lines"]) for l in leaks]


def summary(leak):
    kind, signature, leaking, suspect, _ = leak
    leaking = re.sub(r" instance$", "", leaking)
    text = f"{kind}: {leaking.rsplit('.', 1)[-1] if leaking else '?'}"
    if suspect:
        text += f" <- {suspect}"
    return text + f" (signature {signature[:8] or '-'})"


# ----------------------------------------------------------------------------------------------------------------------
# App control

class App:
    def __init__(self, dev, leaklog):
        self.dev = dev
        self.leaklog = leaklog

    def pid(self):
        return self.dev.shell(f"pidof {PACKAGE}").strip().split(" ")[0]

    def run_as(self, command):
        return self.dev.shell(f"run-as {PACKAGE} {command}")

    def gc(self):
        """ART collects garbage on SIGUSR1; run-as gives the shell the uid of the (debuggable) app."""
        pid = self.pid()
        if pid:
            self.run_as(f"kill -USR1 {pid}")

    def dump_setting(self):
        xml = self.run_as(f"cat {LEAK_PREFS}")
        m = re.search(rf'name="{DUMP_KEY}" value="(true|false)"', xml)
        return m.group(1) if m else None

    def set_dump_setting(self, value):
        """Writes LeakCanary's "Dump heap automatically" preference (LeakCanarySetup creates the file on the first start);
        the app is stopped first, because shared preferences are cached in memory and written back from there."""
        self.dev.shell(f"am force-stop {PACKAGE}")
        time.sleep(1)
        self.run_as(f"sed -i 's/name=\"{DUMP_KEY}\" value=\"[a-z]*\"/name=\"{DUMP_KEY}\" value=\"{value}\"/' {LEAK_PREFS}")
        return self.dump_setting() == value

    def plugin_enabled(self, package):
        """The plugin center switch of [package]: "true", "false" or "unset" (unset means enabled)."""
        xml = self.run_as(f"cat {ENABLE_PREFS}")
        m = re.search(rf'name="key_\$_enabled_plugin_\$_{re.escape(package)}" value="(true|false)"', xml)
        return m.group(1) if m else "unset"

    def set_plugin_enabled(self, package, value):
        """Writes the plugin center switch of [package] with the app stopped (see set_dump_setting)."""
        self.dev.shell(f"am force-stop {PACKAGE}")
        time.sleep(1)
        key = f"key_$_enabled_plugin_$_{package}"
        self.run_as(f"sed -i 's/name=\"{key}\" value=\"[a-z]*\"/name=\"{key}\" value=\"{value}\"/' {ENABLE_PREFS}")
        return self.plugin_enabled(package) == value

    def start_main(self, cold=False, timeout=25.0):
        if cold:
            self.dev.shell(f"am force-stop {PACKAGE}")
            time.sleep(1)
        self.dev.shell(f"am start -n {PACKAGE}/{MAIN_ACTIVITY}")
        if not self.dev.wait_activity("MainActivity", timeout):
            raise WalkError(f"MainActivity not shown (top: {self.dev.top_activity()})")
        time.sleep(1.5 if not cold else 4.0)

    def ensure_main(self):
        """Back to MainActivity with the drawer closed; at most a few back presses, then a fresh start."""
        for _ in range(5):
            self.check_foreign_window()
            if self.dev.top_activity() == "MainActivity":
                if self.drawer_open():
                    self.dev.back()
                    continue
                return
            self.dev.back()
        self.start_main()

    def drawer_open(self):
        return self.dev.find(rid="drawer_menu") is not None

    def check_foreign_window(self, wait=900):
        """Stops at windows of other packages (permission requests and the like) until the maintainer handles them."""
        pkg, act = self.dev.top()
        if not pkg or pkg == PACKAGE or pkg in ("com.android.launcher", "com.oplus.launcher", "com.android.systemui"):
            return
        log(f"HUMAN: a window of another app is in front ({pkg}/{act}); handle it on the phone, waiting up to {wait} s")
        deadline = time.time() + wait
        while time.time() < deadline:
            time.sleep(3)
            pkg, _ = self.dev.top()
            if pkg == PACKAGE:
                return
        raise WalkError(f"the window of {pkg} stayed in front")

    def run_script(self, name, source):
        local = HERE / "__pycache__" / name
        local.parent.mkdir(exist_ok=True)
        local.write_text(source, encoding="utf-8")
        remote = f"{DEVICE_DIR}/{name}"
        self.dev.adb("push", str(local), remote, check=True)
        self.dev.shell(f"am start -n {PACKAGE}/{RUN_ACTIVITY} -e path {remote}")


# ----------------------------------------------------------------------------------------------------------------------
# Navigation shared by the pages

WALK_DIR = "aj6-ui-walk"
WALK_FILE = "walk.js"
SUB_DIR = "sub"
SUB_FILE = "note.txt"


class Walker:
    def __init__(self, dev, app, workdir):
        self.dev = dev
        self.app = app
        self.workdir = workdir

    def prepare_files(self):
        """The folder the file pages walk through: <workdir>/aj6-ui-walk/{walk.js, sub/note.txt}."""
        base = f"{self.workdir}/{WALK_DIR}"
        self.dev.shell(f"mkdir -p {base}/{SUB_DIR}")
        self.dev.shell(f"echo 'var walk = 1;' > {base}/{WALK_FILE}")
        self.dev.shell(f"echo note > {base}/{SUB_DIR}/{SUB_FILE}")

    def main(self):
        self.app.ensure_main()

    def tab(self, name):
        node = self.dev.wait(8, desc=label(name))
        if node is None:
            raise WalkError(f"tab {name} not found")
        if not node.selected:
            self.dev.tap_node(node, pause=1.5)

    def open_drawer(self):
        if self.app.drawer_open():
            return
        self.dev.tap_any(desc=label("text_drawer_open"))
        if self.dev.wait(5, rid="drawer_menu") is None:
            raise WalkError("the drawer did not open")

    def close_drawer(self):
        if self.app.drawer_open():
            self.dev.back()
        if self.app.drawer_open():
            raise WalkError("the drawer did not close")

    def back_to(self, activity, presses=4):
        for _ in range(presses):
            if self.dev.top_activity() == activity:
                return
            self.dev.back()
            if self.dev.wait_activity(activity, 3):
                time.sleep(0.8)
                return
        raise WalkError(f"{activity} not reached by back presses (top: {self.dev.top_activity()})")

    def wait_left(self, activity, timeout=10.0):
        """Waits until [activity] is no longer in front and returns the activity that is."""
        deadline = time.time() + timeout
        while time.time() < deadline:
            top = self.dev.top_activity()
            if top and top != activity:
                time.sleep(1.5)
                return top
            time.sleep(0.4)
        raise WalkError(f"{activity} stayed in front")

    def open_from_drawer(self, rid, activity):
        self.main()
        self.open_drawer()
        self.dev.tap_any(rid=rid)
        if not self.dev.wait_activity(activity, 10):
            raise WalkError(f"{activity} not shown (top: {self.dev.top_activity()})")
        time.sleep(1.5)

    def scroll_to(self, container, **criteria):
        """Scrolls [container] (a resource name) from its top until a node matching [criteria] shows."""
        box = self.dev.wait(6, rid=container)
        if box is None:
            raise WalkError(f"{container} not found")
        for _ in range(8):
            self.dev.scroll_up(box)
            if self.dev.find(**criteria):
                break
        for _ in range(16):
            node = self.dev.find(**criteria)
            if node and node.bounds[3] <= box.bounds[3] - 4:
                return node
            self.dev.scroll_down(box)
        raise WalkError(f"not found in {container}: {describe(criteria)}")

    def row_action(self, name, rid):
        """The control [rid] in the list row whose name is [name]."""
        row = self.dev.wait(6, rid="name", text={name})
        if row is None:
            raise WalkError(f"row {name} not found")
        y = row.center[1]
        for node in self.dev.find_all(rid=rid, fresh=False):
            if node.bounds[1] - 60 <= y <= node.bounds[3] + 60:
                return node
        raise WalkError(f"{rid} of row {name} not found")

    def explorer_root(self):
        """The file tab, listing the working directory (with the walk folder in view)."""
        self.main()
        self.tab("text_file")
        base = self.workdir.rstrip("/").rsplit("/", 1)[-1]
        for _ in range(3):
            if self.dev.find(rid="name", text={WALK_DIR}):
                return
            segment = next((n for n in self.dev.find_all(text={base}, fresh=False) if n.clickable), None)
            if segment:
                self.dev.tap_node(segment, pause=1.5)
                continue
            box = self.dev.find(rid="explorer_item_list", fresh=False)
            self.dev.scroll_up(box)  # also refreshes the list at its top
            time.sleep(1.5)
        raise WalkError(f"{WALK_DIR} not listed in {self.workdir}")

    def explorer_walk_dir(self):
        self.explorer_root()
        self.dev.tap_any(rid="name", text={WALK_DIR})
        if self.dev.wait(6, rid="name", text={WALK_FILE}) is None:
            raise WalkError(f"{WALK_DIR} did not open")

    def explorer_up(self, expect):
        self.dev.tap_any(rid="go_up")
        if self.dev.wait(6, rid="name", text={expect}) is None:
            raise WalkError(f"going up did not list {expect}")

    def open_walk_file(self):
        self.explorer_walk_dir()
        self.dev.tap_any(rid="name", text={WALK_FILE})
        if not self.dev.wait_activity("EditActivity", 10):
            raise WalkError(f"EditActivity not shown (top: {self.dev.top_activity()})")
        time.sleep(2.5)

    def open_settings(self):
        self.open_from_drawer("settings", "PreferencesActivity")

    def open_setting(self, name):
        """Taps the settings entry [name] and returns the activity it opened (or PreferencesActivity for a dialog)."""
        self.open_settings()
        self.dev.tap_node(self.scroll_to("recycler_view", text=label(name)), pause=2.5)
        return self.dev.top_activity()

    def open_developer_options(self):
        if self.open_setting("text_about_app_and_developer") != "AboutActivity":
            raise WalkError("AboutActivity not shown")
        icon = self.dev.wait(6, rid="icon_about_app") or self.dev.find(rid="icon_about_app_svg_view")
        if icon is None:
            raise WalkError("the app icon of the about page not found")
        self.dev.long_press(icon)
        if not self.dev.wait_activity("DeveloperOptionsActivity", 8):
            raise WalkError(f"DeveloperOptionsActivity not shown (top: {self.dev.top_activity()})")
        time.sleep(1.5)


# ----------------------------------------------------------------------------------------------------------------------
# Pages: one function per page performs one round (enter and leave), starting and ending at MainActivity

PAGES = {}


def page(name, description):
    def register(fn):
        PAGES[name] = (fn, description)
        return fn

    return register


@page("drawer", "open the drawer, scroll its menu to the end and back, close it")
def walk_drawer(w):
    w.main()
    w.open_drawer()
    menu = w.dev.find(rid="drawer_menu", fresh=False)
    for _ in range(2):
        w.dev.scroll_down(menu)
    for _ in range(2):
        w.dev.scroll_up(menu)
    w.close_drawer()


@page("explorer", "file tab: enter the walk folder and its subfolder, go up twice, show the popup menu of a file row")
def walk_explorer(w):
    w.explorer_walk_dir()
    w.dev.tap_any(rid="name", text={SUB_DIR})
    if w.dev.wait(6, rid="name", text={SUB_FILE}) is None:
        raise WalkError(f"{SUB_DIR} did not open")
    w.explorer_up(WALK_FILE)
    w.dev.tap_node(w.row_action(WALK_FILE, "more"), pause=1.5)
    w.dev.back()
    w.explorer_up(WALK_DIR)


@page("open-file", "file tab: open walk.js in the editor, leave the editor")
def walk_open_file(w):
    w.open_walk_file()
    w.back_to("MainActivity")


@page("editor-docs", "editor: functions panel, long press sleep for its manual dialog, pin it to the docs pane, close the pane, leave")
def walk_editor_docs(w):
    w.open_walk_file()
    w.dev.tap_any(rid="ace_functions")
    prop = w.dev.wait(6, text=re.compile(r"^sleep\n"))
    if prop is None:
        raise WalkError("the functions panel did not show sleep")
    w.dev.long_press(prop, pause=2.5)
    w.dev.tap_any(rid="pin_to_left")
    time.sleep(3)
    w.dev.back()  # closes the docs pane
    w.back_to("MainActivity")


@page("docs", "documents tab: shown for a while, then back to the file tab")
def walk_docs(w):
    w.main()
    w.tab("text_documentation")
    time.sleep(3)
    w.tab("text_file")


@page("docs-activity", "documentation activity (exported entry point) opened and closed")
def walk_docs_activity(w):
    w.main()
    w.dev.shell(f"am start -n {PACKAGE}/org.autojs.autojs.ui.doc.DocumentationActivity")
    if not w.dev.wait_activity("DocumentationActivity", 10):
        raise WalkError(f"DocumentationActivity not shown (top: {w.dev.top_activity()})")
    time.sleep(3)
    w.dev.back(pause=0.3)  # "press again to exit"
    w.dev.back()
    w.back_to("MainActivity")


@page("plugins","plugins tab: details dialog and settings page of the first plugin")
def walk_plugins(w):
    w.main()
    w.tab("text_plugins")
    w.dev.tap_any(rid="btn_details")
    time.sleep(1.5)
    w.dev.back()
    w.dev.tap_any(rid="btn_settings")
    w.wait_left("MainActivity")
    w.back_to("MainActivity")
    w.tab("text_file")


@page("plugin-center", "plugin center from the drawer: details dialog of the first plugin, then back")
def walk_plugin_center(w):
    w.open_from_drawer("plugin_center", "PluginCenterActivity")
    w.dev.tap_any(rid="btn_details")
    time.sleep(1.5)
    w.dev.back()
    w.back_to("MainActivity")
    w.main()


@page("log", "log page from the toolbar")
def walk_log(w):
    w.main()
    w.dev.tap_any(rid="action_log")
    if not w.dev.wait_activity("LogActivity", 8):
        raise WalkError(f"LogActivity not shown (top: {w.dev.top_activity()})")
    time.sleep(1.5)
    w.back_to("MainActivity")


@page("tasks", "task tab while a 4 s script runs and ends (H25), then back to the file tab")
def walk_tasks(w):
    w.main()
    w.tab("text_task")
    name = "aj6_walk_task.js"
    w.app.run_script(name, "sleep(4000);\n")
    listed = re.compile(r"aj6_walk_task")
    if w.dev.wait(8, text=listed) is None:
        raise WalkError("the running script was not listed")
    deadline = time.time() + 20
    while time.time() < deadline and w.dev.find(text=listed):
        time.sleep(1)
    time.sleep(1)
    w.tab("text_file")


@page("settings", "settings page from the drawer, scrolled to the end and back")
def walk_settings(w):
    w.open_settings()
    box = w.dev.find(rid="recycler_view")
    for _ in range(6):
        w.dev.scroll_down(box)
    for _ in range(6):
        w.dev.scroll_up(box)
    w.back_to("MainActivity")


def settings_subpage(name, resource, description):
    @page(name, description)
    def walk(w):
        opened = w.open_setting(resource)
        if opened == "PreferencesActivity":
            w.dev.back()  # a dialog
        w.back_to("PreferencesActivity")
        w.back_to("MainActivity")

    return walk


settings_subpage("settings-theme", "text_theme_color", "settings > theme color")
settings_subpage("settings-shortcut-backup", "shortcut_backup_title", "settings > shortcut backup")
settings_subpage("settings-transfer", "settings_transfer_title", "settings > manage AutoJs6 settings (export / import wizard)")
settings_subpage("settings-trash", "text_retention_policies", "settings > trash and version history > retention policies")
settings_subpage("settings-editor", "text_all_settings", "settings > code editor > all settings")
settings_subpage("settings-codegen", "text_codegen_models", "settings > code generation models")
settings_subpage("settings-release", "text_release_history", "settings > release history")
settings_subpage("settings-about", "text_about_app_and_developer", "settings > about app and developer")


@page("settings-developer", "settings > about > long press the app icon > developer options")
def walk_developer(w):
    w.open_developer_options()
    w.back_to("AboutActivity")
    w.back_to("PreferencesActivity")
    w.back_to("MainActivity")


@page("settings-memory", "developer options > memory diagnostics page")
def walk_memory(w):
    w.open_developer_options()
    w.dev.tap_node(w.scroll_to("recycler_view", text=label("text_open_memory_diagnostics")), pause=2.0)
    if not w.dev.wait_activity("MemoryDiagnosticsActivity", 8):
        raise WalkError(f"MemoryDiagnosticsActivity not shown (top: {w.dev.top_activity()})")
    time.sleep(1.5)
    w.back_to("DeveloperOptionsActivity")
    w.back_to("AboutActivity")
    w.back_to("PreferencesActivity")
    w.back_to("MainActivity")


# ----------------------------------------------------------------------------------------------------------------------
# Walk

def device_epoch_ms(dev):
    return int(dev.shell("date +%s").strip() or "0") * 1000


def retained_now(lines):
    """The retained object count after the last LeakCanary statement about it in [lines] (0 after a dump)."""
    count = 0
    for line in lines:
        m = FOUND_RE.search(line)
        if m:
            count = int(m.group(1))
        elif NONE_RETAINED in line or line.startswith("Heap dump duration"):
            count = 0
    return count


class PageResult:
    def __init__(self, name):
        self.name = name
        self.reps = 0
        self.errors = []
        self.watched = []
        self.retained = 0
        self.blocks = []
        self.leaks = []
        self.shot = None
        self.note = ""

    @property
    def status(self):
        if self.errors and self.reps == 0:
            return "FAIL"
        if self.leaks or self.retained:
            return "LEAK"
        return "PASS" if not self.errors else "PASS*"


def settle(app, leaklog, start):
    """Two forced collections, then the 5 s LeakCanary takes before it calls a watched object retained, then its check."""
    time.sleep(1)
    for _ in range(2):
        app.gc()
        time.sleep(2.5)
    time.sleep(6)
    app.gc()
    time.sleep(3)
    return leaklog.poll()[start:]


def collect_analysis(dev, app, leaklog, start, since_ms, wait):
    """Sends the app to the background so that LeakCanary dumps the heap, and waits for the analysis."""
    dev.home(pause=1.5)
    deadline = time.time() + wait
    blocks = []
    note = ""
    while time.time() < deadline:
        lines = leaklog.poll()[start:]
        blocks = [b for b in analysis_blocks(lines) if dump_timestamp(b) >= since_ms]
        if blocks:
            break
        refused = [l for l in lines if "would dump heap" in l]
        if refused:
            note = refused[-1]
            break
        time.sleep(5)
    app.start_main()
    return blocks, note


def walk_page(name, w, leaklog, reps, analysis_wait, shots):
    fn, _ = PAGES[name]
    result = PageResult(name)
    start = leaklog.mark()
    since_ms = device_epoch_ms(w.dev)
    for i in range(reps):
        try:
            fn(w)
            result.reps += 1
        except WalkError as e:
            result.errors.append(f"round {i + 1}: {e}")
            try:
                w.app.ensure_main()
            except WalkError as e2:
                result.errors.append(f"recovery: {e2}")
                w.app.start_main()
    if shots:
        result.shot = w.dev.screenshot(shots / f"{name}.png")
    lines = settle(w.app, leaklog, start)
    result.watched = [m.group(1).rsplit(".", 1)[-1] for l in lines for m in [WATCH_RE.search(l)] if m]
    result.retained = retained_now(lines)
    blocks = [b for b in analysis_blocks(lines) if dump_timestamp(b) >= since_ms]
    if result.retained > 0 and not blocks:
        blocks, result.note = collect_analysis(w.dev, w.app, leaklog, start, since_ms, analysis_wait)
    result.blocks = blocks
    for block in blocks:
        result.leaks += parse_leaks(block)
    if blocks:
        result.retained = 0
    return result


def evidence_path(directory, model, version_code, tag):
    stamp = datetime.date.today().strftime("%Y%m%d")
    name = f"ui-walk-{model}-{version_code}{'-' + tag if tag else ''}-{stamp}.md"
    return directory / name


def write_evidence(path, header, results):
    out = [f"# UI leak walk: {header['model']} (API {header['sdk']}), host debug {header['version']}, {header['time']}", ""]
    out += [f"- Command: `{header['command']}`", f"- LeakCanary: {header['leakcanary']}",
            f"- Offline documentation plugin: {header['docs']}", ""]
    out += ["| page | rounds | watched | retained | application leaks | library leaks | result |",
            "| --- | --- | --- | --- | --- | --- | --- |"]
    for r in results:
        app_leaks = sum(1 for l in r.leaks if l[0] == "application")
        lib_leaks = sum(1 for l in r.leaks if l[0] == "library")
        out.append(f"| {r.name} | {r.reps} | {len(r.watched)} | {r.retained} | {app_leaks} | {lib_leaks} | {r.status} |")
    for r in results:
        if not (r.blocks or r.errors or r.note):
            continue
        out += ["", f"## {r.name}", ""]
        for e in r.errors:
            out.append(f"- error: {e}")
        if r.note:
            out.append(f"- LeakCanary: {r.note}")
        for leak in r.leaks:
            out.append(f"- {summary(leak)}")
        for block in r.blocks:
            out += ["", "```text", *[l.replace("\u200b", "") for l in block], "```"]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(out) + "\n", encoding="utf-8")


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("-s", "--serial", required=True)
    ap.add_argument("--pages", default=",".join(PAGES), help="comma separated page names (see --list)")
    ap.add_argument("--reps", type=int, default=3, help="rounds per page")
    ap.add_argument("--tag", default="", help="part of the evidence file name")
    ap.add_argument("--evidence", default=str(ROOT / "docs" / "memory" / "evidence"))
    ap.add_argument("--shots", action="store_true", help="save a screenshot per page next to the evidence file")
    ap.add_argument("--analysis-wait", type=float, default=300.0, help="seconds to wait for a LeakCanary analysis")
    ap.add_argument("--workdir", default="/sdcard/Scripts", help="working directory of the app's file tab")
    ap.add_argument("--no-cold-start", action="store_true")
    ap.add_argument("--keep-dump-setting", action="store_true",
                    help="leave LeakCanary's automatic heap dumps on after the walk")
    ap.add_argument("--docs-plugin", choices=["on", "as-is"], default="on",
                    help="on: switch the Offline Documentation plugin on for the walk (restored afterwards)")
    ap.add_argument("--list", action="store_true")
    args = ap.parse_args()

    if args.list:
        for name, (_, description) in PAGES.items():
            log(f"{name:<26} {description}")
        return 0
    pages = [p.strip() for p in args.pages.split(",") if p.strip()]
    unknown = [p for p in pages if p not in PAGES]
    if unknown:
        sys.exit(f"unknown pages: {', '.join(unknown)} (see --list)")

    dev = Device(args.serial)
    package_info = dev.shell(f"dumpsys package {PACKAGE}")
    m = re.search(r"versionCode=(\d+)", package_info)
    if not m:
        sys.exit(f"{PACKAGE} is not installed")
    version_code = m.group(1)
    if "DEBUGGABLE" not in package_info:
        sys.exit("the installed host is not a debug build (LeakCanary and run-as need one)")
    model = dev.prop("ro.product.model").replace(" ", "_")
    sdk = dev.prop("ro.build.version.sdk")

    leaklog = LeakLog(dev)
    app = App(dev, leaklog)
    walker = Walker(dev, app, args.workdir.rstrip("/"))
    walker.prepare_files()

    original = app.dump_setting()
    if original is None:
        app.start_main(cold=True)
        original = app.dump_setting()
    changed = original != "true"
    if changed and not app.set_dump_setting("true"):
        sys.exit("could not turn on LeakCanary's automatic heap dumps")
    docs_original = app.plugin_enabled(OFFLINE_DOCS)
    docs_changed = args.docs_plugin == "on" and docs_original == "false"
    if docs_changed:
        if not app.set_plugin_enabled(OFFLINE_DOCS, "true"):
            sys.exit("could not turn on the Offline Documentation plugin")
        changed = True
    docs = "enabled" if app.plugin_enabled(OFFLINE_DOCS) != "false" else "disabled"
    if docs_changed:
        docs += " for the walk (was disabled)"
    start = leaklog.mark()
    app.start_main(cold=changed or not args.no_cold_start)
    setup = [m.group(1) for l in leaklog.poll()[start:] for m in [SETUP_RE.search(l)] if m]
    if not args.no_cold_start or changed:
        if not setup:
            log("FAIL LeakCanarySetup did not log its state at startup (is this a debug build with LeakCanary?)")
            return 1
        if setup[-1] != "enabled":
            log("FAIL LeakCanary is disabled in the developer options (Memory diagnostics > LeakCanary)")
            return 1
    log(f"device {model} API {sdk}, host {version_code}, LeakCanary enabled, automatic heap dumps on for the walk "
        f"(was {original}), offline docs plugin enabled: {docs}")

    shots = None
    out = evidence_path(pathlib.Path(args.evidence), model, version_code, args.tag)
    if args.shots:
        shots = out.with_suffix("")
    results = []
    try:
        for name in pages:
            result = walk_page(name, walker, leaklog, args.reps, args.analysis_wait, shots)
            results.append(result)
            app_leaks = [l for l in result.leaks if l[0] == "application"]
            lib_leaks = [l for l in result.leaks if l[0] == "library"]
            log(f"{result.status:<5} {name:<26} rounds {result.reps}/{args.reps}  watched {len(result.watched):>3}  "
                f"retained {result.retained}  leaks {len(app_leaks)} app / {len(lib_leaks)} lib")
            for leak in result.leaks:
                log(f"      {summary(leak)}")
            for e in result.errors:
                log(f"      error {e}")
            if result.note:
                log(f"      LeakCanary: {result.note}")
        if shots and results:
            # LeakCanary's own leak list (an exported activity of the host): each leak group shows when it last occurred.
            dev.shell(f"am start -n {PACKAGE}/leakcanary.internal.activity.LeakActivity")
            if dev.wait_activity("LeakActivity", 10):
                time.sleep(2)
                dev.screenshot(shots / "leakcanary-leaks.png")
            dev.back()
    finally:
        restore = []
        if original != "true" and not args.keep_dump_setting:
            restore.append(("automatic heap dumps", lambda: app.set_dump_setting(original), original))
        if docs_changed:
            restore.append(("the Offline Documentation plugin switch",
                            lambda: app.set_plugin_enabled(OFFLINE_DOCS, docs_original), docs_original))
        for what, action, value in restore:
            try:
                restored = action()
            except WalkError:
                restored = False
            log(f"{what} restored to {value} (the app was stopped for that)" if restored
                else f"FAIL {what} could not be restored to {value}; restore it by hand")
        header = {
            "model": model, "sdk": sdk, "version": version_code, "time": time.strftime("%Y-%m-%d %H:%M"),
            "command": "py -3.12 -I -u tools/memory/ui_leak_walk.py " + " ".join(a for a in sys.argv[1:]),
            "leakcanary": f"enabled in the developer options; automatic heap dumps on during the walk (was {original})",
            "docs": docs,
        }
        if results:
            write_evidence(out, header, results)

    leak_pages = [r for r in results if r.status == "LEAK"]
    signatures = {l[1] for r in results for l in r.leaks if l[0] == "application"}
    failed = [r for r in results if r.status == "FAIL"]
    log(f"pages {len(results)}: PASS {sum(1 for r in results if r.status.startswith('PASS'))}, LEAK {len(leak_pages)}, "
        f"FAIL {len(failed)}; distinct application leak signatures {len(signatures)}")
    log(f"evidence: {out.relative_to(ROOT) if out.is_relative_to(ROOT) else out}")
    return 0 if not leak_pages and not failed else 2


if __name__ == "__main__":
    sys.exit(main())
