"""Checks on a device the default execution budget and the output streaming of Node.js projects (api-coverage Q119 / Q126).

Usage: py -3.12 -I -u tools/api-coverage/device_node_budget.py -s SERIAL [--wait SECONDS]

Needs the host and the Node.js Runtime plugin installed (and enabled) on the device, and host access to the pushed files
(see tools/rhino/device_script_errors.py --grant-storage). Three Node projects without node.timeoutMs are pushed to
/sdcard/Download/aj6-q126 and run through the host's RunIntentActivity, one after another:

  budget   prints a line after 8 s, past the former default budget of 5000 ms (which stopped such a project after about
           4.8 s with ERR_AUTOJS6_SCRIPT_TIMEOUT); passes when the line arrives and no timeout is reported
  stream   prints a line every 500 ms for 6 s from a timer; passes when the 12 lines reach the host console spread over
           at least 4 s (lines given to the host only when the script ends would arrive together)
  sync     prints a line every 500 ms for 3 s from a busy synchronous loop; passes when they are spread over at least 2 s

Only the result line of each project is printed: PASS / FAIL, lines received and the spread of their host console times.
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import threading
import time

HOST = "org.autojs.autojs6"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
DEVICE_DIR = "/sdcard/Download/aj6-q126"
MARK = "Q126"
TIMEOUT_CODE = "ERR_AUTOJS6_SCRIPT_TIMEOUT"

PROJECTS = {
    "budget": """
console.log('%(mark)s budget start');
setTimeout(() => console.log('%(mark)s budget end'), 8000);
""",
    "stream": """
let n = 0;
const timer = setInterval(() => {
    console.log('%(mark)s stream ' + (++n));
    if (n === 12) {
        clearInterval(timer);
        console.log('%(mark)s stream end');
    }
}, 500);
""",
    "sync": """
const end = Date.now() + 3000;
let k = 0, next = Date.now();
while (Date.now() < end) {
    if (Date.now() >= next) {
        console.log('%(mark)s sync ' + (++k));
        next += 500;
    }
}
console.log('%(mark)s sync end');
""",
}

# A console line reads "<HH:MM:SS.mmm>/<level>: <text>".
LINE = re.compile(r"(\d\d):(\d\d):(\d\d)\.(\d{3})/\w: (.*)")


def adb(serial, *args, check=True, timeout=300):
    done = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    if check and done.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {(done.stderr or done.stdout).strip()[:300]}")
    return done.stdout


class Collector:
    """Reads the GlobalConsole lines of one run until the end line of the project."""

    def __init__(self, serial, name):
        adb(serial, "logcat", "-c")
        self.name, self.lines, self.timed_out, self.done = name, [], False, threading.Event()
        self.process = subprocess.Popen(["adb", "-s", serial, "logcat", "-v", "raw", "-s", "GlobalConsole:*"],
                                        stdout=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
        threading.Thread(target=self.read, daemon=True).start()

    def read(self):
        for raw in self.process.stdout:
            if TIMEOUT_CODE in raw:
                self.timed_out = True
            match = LINE.search(raw)
            if not match or not match.group(5).startswith(f"{MARK} {self.name} "):
                continue
            h, m, s, ms, text = match.groups()
            self.lines.append(((int(h) * 3600 + int(m) * 60 + int(s)) * 1000 + int(ms), text.strip()))
            if text.strip().endswith(" end"):
                self.done.set()

    def close(self):
        self.process.kill()


def run_project(serial, name, source, wait):
    local = pathlib.Path(__file__).resolve().parent / "__pycache__" / f"q126_{name}"
    local.mkdir(parents=True, exist_ok=True)
    (local / "main.cjs").write_text(source % {"mark": MARK}, encoding="utf-8")
    (local / "project.json").write_text(json.dumps({"type": "node", "main": "main.cjs"}), encoding="utf-8")
    remote = f"{DEVICE_DIR}/{name}"
    collector = Collector(serial, name)
    try:
        adb(serial, "shell", "mkdir", "-p", remote)
        for file in ("main.cjs", "project.json"):
            adb(serial, "push", str(local / file), f"{remote}/{file}")
        adb(serial, "shell", "am", "start", "-n", f"{HOST}/{RUN_ACTIVITY}", "-e", "path", f"{remote}/main.cjs")
        collector.done.wait(wait)
        # Give a timeout report after the end line a moment to arrive.
        time.sleep(2)
    finally:
        collector.close()
        for file in local.iterdir():
            file.unlink()
        local.rmdir()
    return collector


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--wait", type=int, default=40, help="seconds to wait for each project")
    args = parser.parse_args()
    failed = 0
    try:
        for name, source in PROJECTS.items():
            collector = run_project(args.serial, name, source, args.wait)
            times = [t for t, text in collector.lines if not text.endswith((" start", " end"))]
            spread = times[-1] - times[0] if len(times) > 1 else 0
            ended = collector.done.is_set()
            if name == "budget":
                ok = ended and not collector.timed_out
            else:
                ok = ended and not collector.timed_out and spread >= (4000 if name == "stream" else 2000)
            failed += not ok
            print(f"{'PASS' if ok else 'FAIL'} {name}: lines {len(collector.lines)}, ended {ended}, "
                  f"timeout reported {collector.timed_out}, spread {spread} ms")
    finally:
        adb(args.serial, "shell", "rm", "-rf", DEVICE_DIR, check=False)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
