"""Checks on a device that host members calling Kotlin functions reflectively throw errors scripts can catch (api-coverage Q116).

Usage: py -3.12 -I -u tools/rhino/device_script_errors.py -s SERIAL [--apk HOST_APK] [--grant-storage] [--messages]

Before the fix, an exception of such a member reached Rhino as an InvocationTargetException and the interpreter failed
with a ClassCastException past the script's try / catch: 15 Color members proxied by ColorNativeObject (the cases of the
Node.js Runtime plugin's tools/nodejs/api/files-data/rhino-expected.json recorded as rhinoCrash), images.getSimilarity of
images with different sizes and the client options of http. Each case runs in a java.lang.Runnable, so a case that still
fails the interpreter is reported as a crash and the others go on.

The script is pushed to /sdcard/Download/aj6-q116 and run through the host's RunIntentActivity; results come back as
GlobalConsole logcat lines. One line per case is printed: PASS / FAIL, the case id and the error name (e.name, or
"JavaException <class>" with javaException). With --apk the host APK is installed first (adb install -r -t) and the md5
of the installed package is printed before and after; --grant-storage allows the host all-files access (appops) so it
can read the pushed script (an emulator or a device whose host has no such access yet).
"""
import argparse
import base64
import json
import pathlib
import struct
import subprocess
import sys
import threading
import time
from urllib.parse import unquote
import zlib

HOST = "org.autojs.autojs6"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
DEVICE_DIR = "/sdcard/Download/aj6-q116"
MARK = "Q116"

COLOR_CASES = [
    ("Color.toInt.err.extra", "Color('red').toInt(1)"),
    ("Color.toHex.err.keep", "Color('red').toHex('keep')"),
    ("Color.toHex.err.undefined", "Color('red').toHex(undefined)"),
    ("Color.toHex.err.len3", "Color('#aabbcd').toHex(3)"),
    ("Color.alpha.err.max", "Color('red').alpha({max: 2})"),
    ("Color.toHsv.err.result", "Color('red').toHsv('x')"),
    ("Color.toHsva.err.count", "Color('red').toHsva(1, 2)"),
    ("Color.isSimilar.err.none", "Color('red').isSimilar()"),
    ("Color.isEqual.err.color", "Color('red').isEqual('x')"),
    ("Color.blend.err.ratio", "Color('red').blend('blue', 2)"),
    ("Color.contrast.err.background", "Color('red').contrast('#80ffffff')"),
    ("Color.build.err.negative", "Color('red').build(0, 0)"),
    ("Color.oklab.err.receiver", "Color('red').oklab()"),
    ("Color.oklch.err.receiver", "Color('red').oklch()"),
    ("Color.extract.err", "Color('red').extract()"),
]

OTHER_CASES = [
    ("images.getSimilarity.err.size", "images.getSimilarity(images.fromBase64(PNG_1X1), images.fromBase64(PNG_2X1))"),
    ("http.client.err.connectTimeout", "http.get('http://127.0.0.1:9/', {client: {connectTimeout: [-1, 'SECONDS']}})"),
]

# Names that mean the reflective wrapper, not the member's own error, reached the script.
WRAPPER_NAMES = ("InvocationTargetException", "ClassCastException", "UndeclaredThrowableException")

SCRIPT_JS = """
var CASES = %(cases)s;
var PNG_1X1 = '%(png1)s', PNG_2X1 = '%(png2)s';
function q116Name(e) {
    var name = e && e.name ? String(e.name) : Object.prototype.toString.call(e);
    if (e && e.javaException) name = 'JavaException ' + String(e.javaException.getClass().getSimpleName());
    return name;
}
var q116Source = null, q116Result = null;
var q116Runner = new java.lang.Runnable({run: function () {
    try {
        eval('(' + q116Source + ')');
        q116Result = {notThrown: true};
    } catch (e) {
        q116Result = {name: q116Name(e), message: String(e && e.message !== undefined ? e.message : e).slice(0, 160)};
    }
}});
for (var i = 0; i < CASES.length; i++) {
    q116Source = CASES[i][1];
    q116Result = null;
    try {
        q116Runner.run();
    } catch (e) {
        q116Result = {crash: String(e && e.javaException ? e.javaException.getClass().getName() : e)};
    }
    console.log('%(mark)s ' + CASES[i][0] + ' ' + encodeURIComponent(JSON.stringify(q116Result)));
}
console.log('%(mark)s done ' + CASES.length);
"""


def adb(serial, *args, check=True, timeout=300):
    done = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    if check and done.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {(done.stderr or done.stdout).strip()[:300]}")
    return done.stdout


def installed_md5(serial):
    paths = [line.split(":", 1)[1].strip() for line in adb(serial, "shell", "pm", "path", HOST, check=False).splitlines() if line.startswith("package:")]
    base = next((p for p in paths if p.endswith("base.apk")), None)
    if base is None:
        return "not installed"
    out = adb(serial, "shell", "md5sum", base, check=False).split()
    return out[0] if out else "unknown"


def png(width, height):
    """A white RGB PNG of the given size."""
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    rows = b"".join(b"\x00" + b"\xff\xff\xff" * width for _ in range(height))
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b"")
    return base64.b64encode(data).decode("ascii")


class Collector:
    """Reads the GlobalConsole lines of one run until the done line."""

    def __init__(self, serial):
        adb(serial, "logcat", "-c")
        self.results, self.done = {}, threading.Event()
        self.process = subprocess.Popen(["adb", "-s", serial, "logcat", "-v", "raw", "-s", "GlobalConsole:*"],
                                        stdout=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
        threading.Thread(target=self.read, daemon=True).start()

    def read(self):
        for line in self.process.stdout:
            # A console line reads "<time>/<level>: <text>".
            start = line.find(f": {MARK} ")
            parts = line[start + 2:].strip().split(" ", 2) if start >= 0 else []
            if len(parts) < 3:
                continue
            if parts[1] == "done":
                self.done.set()
                return
            try:
                self.results[parts[1]] = json.loads(unquote(parts[2]))
            except ValueError:
                self.results[parts[1]] = {"unreadable": parts[2][:80]}

    def close(self):
        self.process.kill()


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--apk", help="host APK to install first")
    parser.add_argument("--grant-storage", action="store_true", help="allow the host all-files access through appops")
    parser.add_argument("--wait", type=int, default=120, help="seconds to wait for the run")
    parser.add_argument("--messages", action="store_true", help="also print the first 100 characters of each message")
    args = parser.parse_args()
    serial = args.serial

    if args.apk:
        print(f"host md5 before: {installed_md5(serial)}")
        adb(serial, "install", "-r", "-t", args.apk, timeout=600)
        print(f"host md5 after: {installed_md5(serial)}")
    if args.grant_storage:
        adb(serial, "shell", "appops", "set", HOST, "MANAGE_EXTERNAL_STORAGE", "allow", check=False)
        adb(serial, "shell", "pm", "grant", HOST, "android.permission.READ_EXTERNAL_STORAGE", check=False)

    cases = COLOR_CASES + OTHER_CASES
    script = SCRIPT_JS % {"cases": json.dumps(cases), "png1": png(1, 1), "png2": png(2, 1), "mark": MARK}
    local = pathlib.Path(__file__).resolve().parent / "__pycache__" / "q116.js"
    local.parent.mkdir(exist_ok=True)
    local.write_text(script, encoding="utf-8")
    remote = f"{DEVICE_DIR}/q116.js"
    collector = Collector(serial)
    try:
        adb(serial, "shell", "mkdir", "-p", DEVICE_DIR)
        adb(serial, "push", str(local), remote)
        adb(serial, "shell", "am", "start", "-n", f"{HOST}/{RUN_ACTIVITY}", "-e", "path", remote)
        if not collector.done.wait(args.wait):
            print(f"warning: {len(collector.results)} of {len(cases)} results before the timeout", file=sys.stderr)
        time.sleep(1)
    finally:
        collector.close()
        local.unlink(missing_ok=True)
        adb(serial, "shell", "rm", "-rf", DEVICE_DIR, check=False)

    failed = 0
    for case_id, _ in cases:
        result = collector.results.get(case_id)
        name = result.get("name") if result else None
        ok = bool(name) and not any(wrapper in name for wrapper in WRAPPER_NAMES)
        failed += not ok
        shown = name or (json.dumps(result)[:120] if result else "no result")
        print(f"{'PASS' if ok else 'FAIL'} {case_id}: {shown}")
        if args.messages and result and "message" in result:
            print(f"    {result['message'][:100]}")
    print(f"{len(cases) - failed} / {len(cases)} passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
