#!/usr/bin/env python3
"""The code examples of the documentation imgui pages on a device (api-coverage P5.2f-2, IMGUI-PLAN.md card B).

Every js block of api/imgui.md and api/imgui*Type.md is a whole Rhino script, apart from the Node.js and Bun blocks of the
escape hatch, which device_imgui_escape.py covers. Each block runs on its own, verbatim inside a function:

  runs    the block finishes without an exception
  alive   its session is still open after RUN_SECONDS, so the render callback did not throw (without an error listener a
          render error closes the session)
  errors  nothing reached console.error, up to SETTLE_MILLISECONDS after the close; the plugin prints the errors of the
          event listeners there, including the listeners of the closed event, and the host copies the console to logcat
  window  a host overlay window appeared and is gone after the close

Only one line per block is printed. --list prints the blocks without a device. Packages are not replaced, and nothing stays
on the device.

Usage: device_imgui_docs.py --serial SERIAL [--docs DIR] [--only NAME,NAME] [--report FILE]
       device_imgui_docs.py --list [--docs DIR]
Expects host 6.8.0 with the ImGui plugin enabled, the overlay permission and the display on.
"""
import argparse
import json
import os
from pathlib import Path
import re
import sys
import uuid

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import device_imgui_escape as escape  # noqa: E402

base = escape.base
base.DEVICE_DIR = "/sdcard/Download/aj6-imgui-docs"

RUN_MILLISECONDS = 2500
# The closed listeners run on the script thread after the close.
SETTLE_MILLISECONDS = 500
# Node.js and Bun blocks start with their directive; see device_imgui_escape.py.
OTHER_RUNTIME = re.compile(r"""^\s*["'](nodejs|bun)["'];""")
BLOCK = re.compile(r"^```js\n(.*?)^```$", re.M | re.S)

WRAPPER = r"""
@EMIT@
var aj6Result = {thrown: null};
console.log('aj6-begin-@RUN@');
try {
    (function () {
@BODY@
    })();
} catch (error) {
    aj6Result.thrown = String(error);
}
setTimeout(function () {
    try {
        imgui.getComponent('toast', 'aj6-none');
        aj6Result.alive = true;
    } catch (error) {
        aj6Result.alive = false;
        aj6Result.detail = String(error);
    }
    imgui.closeWindow();
    setTimeout(function () {
        console.log('aj6-end-@RUN@');
        aj6Emit('@RUN@', 0, aj6Result);
        aj6Emit('@RUN@', -1, {done: 1});
        setTimeout(function () {
            exit();
        }, 200);
    }, @SETTLE@);
}, @WAIT@);
"""


def blocks(docs):
    """(page, ordinal in the page, source) of every Rhino block."""
    found = []
    pages = [docs / "imgui.md", *sorted(docs.glob("imgui*Type.md"))]
    for page in pages:
        text = page.read_text(encoding="utf-8").replace("\r\n", "\n")
        ordinal = 0
        for match in BLOCK.finditer(text):
            source = match.group(1)
            if OTHER_RUNTIME.match(source):
                continue
            ordinal += 1
            found.append((f"{page.stem}#{ordinal}", source))
    return found


def console_errors(serial, run):
    """The console.error lines of a run. The console object is a Java object, so the script cannot hook it."""
    log = escape.sample.adb(serial, "logcat", "-d", "-t", "6000", "-s", "GlobalConsole:D", check=False, timeout=30) or ""
    begin = log.rfind(f"aj6-begin-{run}")
    end = log.rfind(f"aj6-end-{run}")
    if begin < 0 or end < begin:
        return [f"no console markers of {run} in logcat"]
    return [line.split("/E: ", 1)[1].strip() for line in log[begin:end].splitlines() if "/E: " in line]


def run_block(serial, source):
    run = uuid.uuid4().hex[:8]
    script = (WRAPPER.replace("@EMIT@", base.EMIT_JS).replace("@BODY@", source)
              .replace("@RUN@", run).replace("@WAIT@", str(RUN_MILLISECONDS)).replace("@SETTLE@", str(SETTLE_MILLISECONDS)))
    watch = escape.OverlayWatch(serial)
    results = base.run_script(serial, "main.js", script, run, 30)
    window = watch.finish()
    result = results.get(0)
    if result:
        result["errors"] = console_errors(serial, run)
    return result, window


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial")
    parser.add_argument("--docs", type=Path, help="the api directory of the documentation (default: the sibling checkout)")
    parser.add_argument("--only", help="comma-separated names as --list prints them")
    parser.add_argument("--report", type=Path)
    parser.add_argument("--list", action="store_true")
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="backslashreplace")
    docs = args.docs or Path(os.environ.get("AUTOJS6_DOCUMENTATION_ROOT") or HERE.parents[2] / "AutoJs6-Documentation") / "api"
    items = blocks(docs)
    if args.only:
        wanted = set(args.only.split(","))
        items = [item for item in items if item[0] in wanted]
    if args.list:
        for name, source in items:
            print(name, source.count("\n"), "lines")
        return 0
    if not args.serial:
        parser.error("--serial is required")
    report = {}
    failed = 0
    for name, source in items:
        result, window = run_block(args.serial, source)
        ok = (bool(result) and result.get("thrown") is None and result.get("alive") is True and not result.get("errors")
              and escape.window_ok(*window))
        detail = "" if ok else json.dumps(result, ensure_ascii=False)[:300]
        print(f"{'PASS' if ok else 'FAIL'} {name} {escape.window_note(*window)} {detail}".rstrip())
        report[name] = {"ok": ok, "result": result, "window": list(window)}
        failed += not ok
    print(f"{len(items) - failed} / {len(items)} passed")
    if args.report:
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
