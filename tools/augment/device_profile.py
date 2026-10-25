"""Samples a looping script call with simpleperf and reports the Java frames that still run interpreted (ROADMAP P2.4).

Usage: py -3.12 -I tools/augment/device_profile.py -s SERIAL [--seconds 8] EXPRESSION ...

Each EXPRESSION (for example "base64.encode()" or "isNullish(null)") runs in a try / catch loop in its own script
for --seconds plus a margin; `simpleperf record --app` samples the host process meanwhile (the debug host is
debuggable, so interpreted and JIT-compiled Java frames carry method names). Printed per expression: the time per
iteration measured by the script, and the frames of the module layer (org.autojs.autojs.runtime.api.modules) and of
Rhino (org.mozilla.javascript) that ran from the dex file, i.e. in the ART interpreter, with their inclusive share of
the samples. A warmed process should list none of the module layer; P2.4 found a long-running process where the
module layer's hot path stayed interpreted, which made failing calls 1.13-1.46 times Rhino's own errors.
"""
import argparse
import pathlib
import re
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import device_bench as db  # noqa: E402

MARK = "AJ6PROF"
PERF_DATA = "/data/local/tmp/aj6_profile.data"
# A frame line of `simpleperf report --children --sort dso,symbol`: children%, self%, dso, symbol.
FRAME_RE = re.compile(r"^\s*([\d.]+)%\s+([\d.]+)%\s+(\S+)\s+(.+?)\s*$")
PACKAGES = ("org.autojs.autojs.runtime.api.modules.", "org.mozilla.javascript.")


def script_for(expression, run_ms):
    return (
        "let u;\n"
        f"let end = java.lang.System.currentTimeMillis() + {run_ms};\n"
        "let n = 0;\n"
        "let t0 = java.lang.System.nanoTime();\n"
        "while (java.lang.System.currentTimeMillis() < end) {\n"
        f"    for (let i = 0; i < 100; i++) {{ try {{ {expression}; }} catch (e) {{ }} }}\n"
        "    n += 100;\n"
        "}\n"
        f"console.log('{MARK} ' + ((java.lang.System.nanoTime() - t0) / 1e3 / n).toFixed(1) + ' us n=' + n);\n"
    )


def profile(s, expression, seconds, quiet=False):
    """Prints (unless quiet) and returns (us per iteration or None, [(share %, interpreted symbol), ...])."""
    local = HERE / "__pycache__" / "aj6_profile.js"
    local.parent.mkdir(exist_ok=True)
    local.write_text(script_for(expression, (seconds + 6) * 1000), encoding="utf-8")
    remote = f"{db.DEVICE_DIR}/aj6_profile.js"
    db.adb(s, "shell", "mkdir", "-p", db.DEVICE_DIR, check=True)
    db.adb(s, "push", str(local), remote, check=True)
    db.clear_log(s)
    before = set(db.console_lines(s))
    db.adb(s, "shell", "am", "start", "-n", f"{db.PACKAGE}/{db.RUN_ACTIVITY}", "-e", "path", remote, check=True)
    time.sleep(3)
    record = db.adb(s, "shell", f"simpleperf record --app {db.PACKAGE} -e cpu-clock -f 4000 -g --duration {seconds} -o {PERF_DATA}")
    if "Samples recorded" not in record.stdout + record.stderr:
        print(f"{expression}: simpleperf failed: {(record.stdout + record.stderr).strip()[-300:]}")
        return None, []
    per_call = None
    deadline = time.time() + 15
    while time.time() < deadline and per_call is None:
        time.sleep(1)
        for line in db.console_lines(s):
            if line not in before and MARK in line:
                per_call = line[line.index(MARK) + len(MARK) + 1:]
    report = db.adb(s, "shell", f"simpleperf report -i {PERF_DATA} --children --sort dso,symbol --percent-limit 0.3 2>/dev/null").stdout
    interpreted = []
    for line in report.splitlines():
        m = FRAME_RE.match(line)
        if m and ".dex" in m.group(3) and m.group(4).startswith(PACKAGES):
            interpreted.append((float(m.group(1)), m.group(4)))
    db.adb(s, "shell", "rm", "-f", PERF_DATA)
    if not quiet:
        print(f"{expression}: {per_call or 'no timing line'}")
        if not interpreted:
            print("  interpreted frames of the module layer / Rhino: none")
        for share, symbol in interpreted:
            print(f"  interpreted {share:5.1f}%  {symbol}")
    return (float(per_call.split()[0]) if per_call else None), interpreted


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--seconds", type=int, default=8, help="sampling time per expression")
    parser.add_argument("expressions", nargs="+")
    args = parser.parse_args()
    s = args.serial
    if not db.adb(s, "shell", "command -v simpleperf").stdout.strip():
        # G8441 (Android 9) has none, and the NDK's binary (r26) does not link there.
        sys.exit("simpleperf is not on the device")
    for expression in args.expressions:
        profile(s, expression, args.seconds)


if __name__ == "__main__":
    sys.exit(main())
