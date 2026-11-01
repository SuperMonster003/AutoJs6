#!/usr/bin/env python3
"""Device sampling of api-coverage P6.1 on the lowest roadmap device (Sony G8441, API 28).

Reuses the existing device suites: the sampled entries of P3 / P4 are cases of the Node runtime's device harnesses
(tools/nodejs/api/*/device_*.py) and of device_plugin_family.py, the two P1 batches are cases of device_sample_a.py.
A harness runs whole in Rhino and in the Node forms (one child process per harness, because they share the
device_js_tools module and set its globals), and the wrapper keeps the short values of the sampled cases. Bun is not
run here: its plugin has minSdk 33 (`close_range` is killed by the app seccomp filter below it), and the `bun-limit`
phase proves that with the real APK. One line per item: PASS / FAIL / SKIP and short values; tokens, screen text and
tool lists are never printed.

Phases (all take --serial; everything but list also --state, a private directory):
  list                       the sampling list
  record / install --apk P=PATH / restore / verify   as in device_bun_js_tools.py, for the host, its test APK, Node, Bun
  prepare / unprepare        record the settings of SETTINGS, turn the host's accessibility service on and keep the
                             screen awake; unprepare writes the recorded values back
  bun-limit --apk PATH       installing the Bun APK must fail with INSTALL_FAILED_OLDER_SDK
  sample [--harnesses ...] [--forms main,worker] [--report FILE] [--markdown FILE]
                             run the harnesses and print the sampled items
  p1 --batch images|ui|capture   the P1.2 cases of the batch (results in <state>/results.json)

Typical run: record, bun-limit, install, prepare, sample, p1 --batch images, p1 --batch ui, unprepare, restore.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
from types import SimpleNamespace

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import device_bun_js_tools as base  # noqa: E402

sample = base.sample
base.PACKAGES = [base.HOST, sample.HOST_TEST, sample.NODE, sample.BUN]
NODE_API = HERE.parents[2] / "AutoJs6-Plugin-NodeJs-Runtime" / "tools" / "nodejs" / "api"
HARNESSES = {"app-shell": NODE_API / "app-shell" / "device_app_shell.py", "system": NODE_API / "system" / "device_system.py",
             "device-media": NODE_API / "device-media" / "device_device_media.py", "files-data": NODE_API / "files-data" / "device_files_data.py",
             "js-tools": NODE_API / "js-tools" / "device_js_tools.py", "ui-family": NODE_API / "ui-family" / "device_ui_family.py",
             "automation": NODE_API / "automation" / "device_automation.py", "plugin-family": HERE / "device_plugin_family.py"}
FILES_GROUPS = "zip,data"
BUN_LIMIT = "LIMIT (API 33)"
FORM_TITLES = {"main": "Node main", "worker": "Node Worker", "esm": "Node ESM"}
PLUGIN_FORMS = {"main": "node", "worker": "node-worker"}
ACCESSIBILITY_SERVICE = "org.autojs.autojs6/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"
SETTINGS = [("secure", "enabled_accessibility_services"), ("secure", "accessibility_enabled"), ("global", "stay_on_while_plugged_in")]

# [family, entry, roadmap item, harness, case id]: the families of P3.1 - P3.5 / P4.1 - P4.5. Each entry runs in Node
# main, Node Worker and Bun; Bun is the API 33 limit on this device.
SAMPLES = [
    ["system", "app.getInstalledPackages", "P3.1a / P4.1a", "app-shell", "app.getInstalledPackages"],
    ["system", "sysprops.get", "P3.1b / P4.1b", "system", "sysprops.get"],
    ["system", "notice.channel.create", "P3.1b / P4.1b", "system", "notice.channel"],
    ["device", "device.getMarketName", "P3.1c / P4.1c", "device-media", "device.derived"],
    ["device", "sensors.register", "P3.1c / P4.1c", "device-media", "sensors.change~"],
    ["device", "media.playMusic", "P3.1c / P4.1c", "device-media", "media.music"],
    ["storage", "storages.names", "P3.2b / P4.2b", "files-data", "data/storages.names.includes"],
    ["storage", "Storage#size", "P3.2b / P4.2b", "files-data", "data/Storage.size"],
    ["storage", "zip.zipFile", "P3.2b / P4.2b", "files-data", "zip/zip.zipFile.basic"],
    ["network", "web.newWebSocket", "P3.3 / P4.3", "ui-family", "web.newWebSocket.failure~"],
    ["network", "web.ByteString", "P3.3 / P4.3", "ui-family", "web.ByteString"],
    ["js-tools", "util.format", "P3.2a / P4.2a", "js-tools", "util.format.placeholders"],
    ["js-tools", "Numberx.clamp", "P3.2a / P4.2a", "js-tools", "Numberx.clamp.values"],
    ["ui", "floaty.window", "P3.3 / P4.3", "ui-family", "floaty.window"],
    ["ui", "console.build", "P3.3 / P4.3", "ui-family", "console.build"],
    ["ui", "terminal.open", "P3.3 / P4.3", "ui-family", "terminal.session"],
    ["automation", "automator.click", "P3.4 / P4.4", "automation", "automator.click.bounds"],
    ["automation", "threads.start", "P3.4 / P4.4", "automation", "threads.start"],
    ["automation", "events.broadcast", "P3.4 / P4.4", "automation", "events.broadcast~"],
    ["image", "ocr.mode", "P3.4 / P4.4", "automation", "ocr.mode"],
    ["image", "qrcode", "P3.4 / P4.4", "automation", "qrcode.members"],
    ["image", "yolo.load", "P3.4 / P4.4", "automation", "yolo.load.unknown"],
    ["bridges", "colors.theme.set", "P3.5 / P4.5", "plugin-family", "theme.set"],
    ["bridges", "gallery.convert", "P3.5 / P4.5", "plugin-family", "gallery.convert"],
    ["bridges", "files.batchRename.preview", "P3.5 / P4.5", "plugin-family", "rename.preview"],
    ["bridges", "speech.speaker.list", "P3.5 / P4.5", "plugin-family", "speech.speaker.list"],
]
# The P1.2 cases of device_sample_a.py. images and ui need no system dialog; capture clicks MediaProjection's "Start now"
# itself, so it runs only with the maintainer's permission for that run (Q34 (a)).
BATCHES = {"images": ["img-filters", "img-features", "img-circles", "img-quota", "img-worker-features", "img-public", "img-transport",
                      "img-worker-bridge", "img-capture-denied"],
           "ui": ["ui-layouts", "ui-compose", "ui-ownership", "ui-worker-cleanup", "ui-floaty", "ui-snackbar", "ui-xml-attach", "ui-factories",
                  "ui-xml-widgets", "ui-xml-events", "ui-components"],
           "capture": ["img-capture-async", "img-capture-workers"]}


def short(result):
    if result is None:
        return "missing"
    if "th" in result:
        return f"throws {result['th'].get('name')}"
    if "skip" in result:
        return "skip"
    return json.dumps(result.get("ok"), ensure_ascii=False)[:72]


def load(path):
    sys.path.insert(0, str(path.parent))
    spec = importlib.util.spec_from_file_location(path.stem, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[path.stem] = module
    spec.loader.exec_module(module)
    return module


def harness_child(args):
    """One harness in this process; writes counts, failures and the values of the sampled cases to --out."""
    module = load(HARNESSES[args.harness])
    forms, captured, out = args.forms.split(","), {}, Path(args.out)
    harness_report = out.with_suffix(".harness.json")
    options = SimpleNamespace(serial=args.serial, forms=args.forms, report=str(harness_report), wait=args.wait, show=args.show, state=None,
                              groups=FILES_GROUPS, out=str(out.with_suffix(".rhino.json")), expected=str(out.with_suffix(".rhino.json")),
                              cases=[], foreground=False, rhino=None)
    if args.harness == "plugin-family":
        options.forms = ",".join(["rhino"] + [PLUGIN_FORMS[form] for form in forms])
        labels, run_files = iter(["rhino"] + forms), module.run_files

        def capture_files(*items, **options_):
            results = run_files(*items, **options_)
            captured[next(labels)] = results
            return results
        module.run_files = capture_files
    else:
        owner = module if args.harness == "js-tools" else module.base
        run_script = owner.run_script

        def capture_script(serial, name, *items, **options_):
            results = run_script(serial, name, *items, **options_)
            form = re.search(r"_(?:node_)?(rhino|[a-z]+)_[0-9a-f]{8}\.js$", name)
            captured[form.group(1) if form else name] = results
            return results
        owner.run_script = capture_script
    code = 0
    try:
        if args.harness in ("js-tools", "files-data"):
            code = module.phase_rhino(options) or module.phase_node(options)
        elif args.harness == "plugin-family":
            module.compare(options)
        else:
            code = module.phase_compare(options)
    except SystemExit as stop:
        code = stop.code or 0
    if args.harness == "js-tools":
        ids = module.case_ids()
    elif args.harness == "files-data":
        ids = module.case_ids(FILES_GROUPS.split(","))
    elif args.harness == "plugin-family":
        ids = [case[0] for case in module.case_table(options)]
    else:
        ids = [case[0] for case in module.cases()]
    wanted = {item[4] for item in SAMPLES if item[3] == args.harness}
    report = json.loads(harness_report.read_text(encoding="utf-8")) if harness_report.exists() else {}
    results = report.get("forms", report)
    named = {PLUGIN_FORMS[form] if args.harness == "plugin-family" else form: form for form in forms}
    out.write_text(json.dumps({
        "harness": args.harness, "exit": code, "rhinoCases": len([i for i in captured.get("rhino", {}) if i >= 0]), "total": len(ids),
        "counts": {named[key]: value.get("counts") for key, value in results.items() if key in named},
        "failures": {named[key]: value.get("failures", {}) for key, value in results.items() if key in named},
        "values": {case: {form: short(captured.get(form, {}).get(ids.index(case))) for form in ["rhino", *forms]}
                   for case in wanted if case in ids},
        "unknownCases": sorted(wanted - set(ids))}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    return code


def run_harness(args, name):
    out = args.state / "harness" / f"{name}.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.unlink(missing_ok=True)
    command = [sys.executable, str(Path(__file__).resolve()), "harness", "--serial", args.serial, "--harness", name, "--forms", args.forms,
               "--out", str(out), "--wait", str(args.wait), "--show", str(args.show)]
    done = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if not out.exists():
        print(f"{name}: no result, exit {done.returncode}: {(done.stderr or done.stdout)[-400:]}")
        return {"harness": name, "exit": done.returncode, "counts": {}, "failures": {}, "values": {}, "unknownCases": [], "crashed": True}
    return json.loads(out.read_text(encoding="utf-8"))


def item_status(result, case, form):
    if result.get("crashed") or form not in result["failures"]:
        return "FAIL"
    return "FAIL" if case in result["failures"][form] else "PASS"


def markdown_table(items):
    lines = ["| 族 | 入口 | 条目 | 用例 | Node main | Node Worker | Bun | 关键数值 (main) |", "|---|---|---|---|---|---|---|---|"]
    for item in items:
        lines.append(f"| {item['family']} | `{item['entry']}` | {item['roadmap']} | {item['harness']}: {item['case']} | {item['main']} | {item['worker']} | {BUN_LIMIT} | "
                     + "`" + item["value"].replace("|", "/") + "` |")
    return "\n".join(lines) + "\n"


def run_sample(args):
    forms = args.forms.split(",")
    names = args.harnesses or list(dict.fromkeys(item[3] for item in SAMPLES))
    results, items, failed = {name: run_harness(args, name) for name in names}, [], 0
    for name, result in results.items():
        counts = " / ".join(f"{form} " + " ".join(f"{key} {value}" for key, value in (result["counts"].get(form) or {}).items()) for form in forms)
        print(f"== {name}: rhino {result.get('rhinoCases')} / {result.get('total')}, {counts}, exit {result['exit']}")
        for form in forms:
            for case, verdict in list((result["failures"].get(form) or {}).items())[:args.show]:
                print(f"  {FORM_TITLES.get(form, form)} {case}: {verdict[:220]}")
        if result["unknownCases"]:
            print(f"  unknown cases: {result['unknownCases']}")
    for family, entry, roadmap, name, case in SAMPLES:
        if name not in results:
            continue
        result = results[name]
        values = result["values"].get(case) or {}
        states = {form: item_status(result, case, form) if case in result["values"] else "FAIL" for form in forms}
        failed += "FAIL" in states.values()
        items.append({"family": family, "entry": entry, "roadmap": roadmap, "harness": name, "case": case, **states,
                      "value": values.get("main", "missing"), "rhino": values.get("rhino", "missing")})
        print(f"{'FAIL' if 'FAIL' in states.values() else 'PASS'} {family} {entry}: " + " ".join(f"{form}={state}" for form, state in states.items())
              + f" bun={BUN_LIMIT} value={values.get('main', 'missing')}")
    print(f"sampled {len(items)}: pass {len(items) - failed}, fail {failed}")
    if args.report:
        Path(args.report).write_text(json.dumps({"items": items, "harnesses": results}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    if args.markdown:
        Path(args.markdown).write_text(markdown_table(items), encoding="utf-8")
    return 1 if failed else 0


def run_p1(args):
    cases = BATCHES[args.batch]
    sample.run(SimpleNamespace(state=args.state, serial=args.serial, family=None, case=cases, timeout=args.timeout, numbers=args.numbers))
    results = json.loads((args.state / "results.json").read_text(encoding="utf-8"))
    failed = [case for case in cases if results.get(case, {}).get("status") != "pass"]
    print(f"batch {args.batch}: pass {len(cases) - len(failed)} / {len(cases)}" + (f", failed {failed}" if failed else "") + f", bun {BUN_LIMIT}")
    return 1 if failed else 0


def bun_limit(args):
    done = subprocess.run(["adb", "-s", args.serial, "install", "-r", "-t", str(args.apk[0])], capture_output=True, text=True, encoding="utf-8", errors="replace")
    text = done.stdout + done.stderr
    limited = "INSTALL_FAILED_OLDER_SDK" in text
    print(f"{'LIMIT' if limited else 'FAIL'} Bun APK on API {sample.sh(args.serial, 'getprop ro.build.version.sdk')}: "
          + ("INSTALL_FAILED_OLDER_SDK" if limited else f"install exit {done.returncode}: {text[-200:]}"))
    return 0 if limited else 1


def prepare(args):
    target = args.state / "settings.json"
    if not target.exists():
        target.write_text(json.dumps({f"{ns}/{key}": sample.sh(args.serial, f"settings get {ns} {key}") for ns, key in SETTINGS}, indent=1), encoding="utf-8")
    state = json.loads(target.read_text(encoding="utf-8"))
    services = [item for item in state["secure/enabled_accessibility_services"].split(":") if item and item != "null"]
    if ACCESSIBILITY_SERVICE not in services:
        services.append(ACCESSIBILITY_SERVICE)
    sample.sh(args.serial, f"settings put secure enabled_accessibility_services {':'.join(services)}")
    sample.sh(args.serial, "settings put secure accessibility_enabled 1")
    sample.sh(args.serial, "settings put global stay_on_while_plugged_in 3")
    sample.sh(args.serial, "input keyevent KEYCODE_WAKEUP")
    print(f"accessibility services {len(services)}, stay awake on")
    return 0


def unprepare(args):
    state = json.loads((args.state / "settings.json").read_text(encoding="utf-8"))
    for key, value in state.items():
        namespace, name = key.split("/")
        sample.sh(args.serial, f"settings delete {namespace} {name}" if value == "null" else f"settings put {namespace} {name} {value}")
    print("settings written back: " + ", ".join(key for key in state))
    return 0


def list_samples():
    for family, entry, roadmap, harness, case in SAMPLES:
        print(f"{family:10} {entry:28} {roadmap:14} {harness}: {case}")
    for batch, cases in BATCHES.items():
        print(f"P1.2 {batch}: {' '.join(cases)}")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("phase", choices=["list", "record", "install", "restore", "verify", "prepare", "unprepare", "bun-limit", "sample", "p1", "harness"])
    parser.add_argument("--serial", default="BH900ASK9E")
    parser.add_argument("--state", type=Path, help="private state directory (APK copies, logs, results)")
    parser.add_argument("--apk", action="append", default=[], help="install: package=path; bun-limit: the APK")
    parser.add_argument("--harnesses", nargs="*", choices=sorted(HARNESSES), help="sample: only these harnesses")
    parser.add_argument("--harness", choices=sorted(HARNESSES), help=argparse.SUPPRESS)
    parser.add_argument("--out", help=argparse.SUPPRESS)
    parser.add_argument("--forms", default="main,worker")
    parser.add_argument("--report", help="sample: all counts and items as JSON")
    parser.add_argument("--markdown", help="sample: the item table for the report")
    parser.add_argument("--batch", choices=sorted(BATCHES), help="p1: the batch")
    parser.add_argument("--wait", type=int, default=300)
    parser.add_argument("--show", type=int, default=20)
    parser.add_argument("--timeout", type=int, default=900)
    parser.add_argument("--numbers", type=int, default=8)
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="backslashreplace")
    if args.phase == "harness":
        return harness_child(args)
    if args.phase == "list":
        return list_samples()
    if not args.state:
        parser.error("--state is required")
    if args.phase in ("record", "install", "restore", "verify"):
        return getattr(base, args.phase)(args)
    return {"prepare": prepare, "unprepare": unprepare, "bun-limit": bun_limit, "sample": run_sample, "p1": run_p1}[args.phase](args)


if __name__ == "__main__":
    sys.exit(main())
