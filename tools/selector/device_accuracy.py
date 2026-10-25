"""Selector accuracy cases on a device (ROADMAP selector P3.2-P3.4): the normalize case on the settings home page, then
the others on the home screen in a floaty window accuracy.js opens; prints one line per case: PASS / FAIL with the key
values (no node text).

Usage: py -3.12 -I tools/selector/device_accuracy.py -s SERIAL [--cases normalize refind prefer] [--new-modules on|off]
Needs the accessibility service of AutoJs6 running; no taps. The new script module layer switch is restored afterwards.
"""
import argparse
import os
import sys

# py -I leaves the directory of the script off sys.path; the shared helpers live next to it.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _device as d  # noqa: E402

CASES = ["normalize", "refind", "prefer"]
KEYS = ["shape", "refinds", "visible", "found", "plain", "normalized", "same", "letters", "skipped"]


def run(serial, cases):
    ok = True
    if "normalize" in cases:
        d.screen_settings(serial)
        ok = run_on(serial, ["normalize"], "accuracy-settings.js")
    rest = [c for c in cases if c != "normalize"]
    if rest:
        d.screen_home(serial)
        ok = run_on(serial, rest, "accuracy-home.js") and ok
    return ok


def run_on(serial, cases, name):
    res, lines = d.run_json(serial, "accuracy.js", {"cases": cases}, 180, name=name)
    if res is None:
        print("FAIL no result from the device")
        for line in d.script_errors(lines):
            print("  " + line)
        return False
    ok = not res.get("errors")
    for e in res.get("errors", []):
        print(f"  error: {e}")
    for c in res.get("cases", []):
        values = " ".join(f"{k}={c[k]}" for k in KEYS if k in c)
        error = f" error={c['error']}" if c.get("error") else ""
        print(f"  {c['name']}: {'PASS' if c.get('ok') else 'FAIL'} {values}{error}")
        ok = ok and bool(c.get("ok"))
    return ok


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--cases", nargs="*", default=CASES, choices=CASES)
    parser.add_argument("--new-modules", choices=["on", "off"], help="set the new script module layer switch for the run")
    args = parser.parse_args()
    info = d.device_info(args.serial)
    print(f"device {info.get('model')} API {info.get('sdk')} versionCode {info.get('versionCode')}")
    if not d.a11y_enabled(args.serial):
        sys.exit("the accessibility service of AutoJs6 is not enabled")

    def body(state):
        print(f"new modules {state}")
        return run(args.serial, args.cases)

    ok = d.with_new_modules(args.serial, args.new_modules, body)
    print(f"overall: {'PASS' if ok else 'FAIL'}")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
