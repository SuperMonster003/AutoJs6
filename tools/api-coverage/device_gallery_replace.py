#!/usr/bin/env python3
"""Device repetition of the gallery session replacement (api-coverage roadmap Q197): a viewer is closed, a picker or an editor
opens right after it, and a new viewer replaces that one. The rounds run in Rhino, in a Node main script, in a Node Worker, in
a Bun main script and in a Bun Worker, with the fixtures, the installs and the grants of device_plugin_family.py.

A round is one viewer, one picker and one editor. Every step stamps its times on the device (the clock of a step is the local
time, so that it lines up with logcat), and a case waits longer than the plugin's claim timeout (60 s), so that a session
nobody claimed ends with the plugin's own answer instead of the case timeout. Only the failing steps and the counts are
printed; --log keeps the lines of the activity manager, of the host's plugin client and of the session Activities.

  run --serial S [--forms rhino,node,node-worker,bun,bun-worker] [--rounds 20] [--left 0] [--replace-delay 2500] [--log FILE] [--report FILE]

--left adds steps that leave a viewer to the user ({finish: false}) and open the next one; the plugin is force-stopped after them.

The Node runs declare the permissions of device_plugin_family.py, the Bun runs need its grants on. Nobody has to be at the
device, unless the plugin asks for its photo permission first: that dialog is the maintainer's.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import device_plugin_family as family  # noqa: E402

base = family.base
sample = family.sample
family.CASE_TIMEOUT_MS = 75000
BUN_TIMEOUT_MS = 600000
NL = chr(10)

CLOCK = ("var t = Date.now(), log = {}, pad = function (v, n) { return ('00' + v).slice(-n); }, clock = function () {\n"
         "    var d = new Date();\n"
         "    return pad(d.getHours(), 2) + ':' + pad(d.getMinutes(), 2) + ':' + pad(d.getSeconds(), 2) + '.' + pad(d.getMilliseconds(), 3);\n"
         "}, at = function (name) { log[name] = Date.now() - t; };\n"
         "log.start = clock();\n")

VIEW = "function (done) {\n" + CLOCK + """
    gallery.viewAsync(D + '/a.png').then(function (session) {
        at('open');
        session.on('close', function (event) { at('close'); log.reason = String(event.reason); log.end = clock(); done(log); });
        setTimeout(function () { session.close({finish: true}); }, 500);
    }, function (e) { at('rejected'); log.code = String(e.code); log.message = String(e.message); log.end = clock(); done(log); });
}"""

REPLACED = "function (done) {\n" + CLOCK + """
    OPEN.then(function () { log.first = 'answered'; at('firstEnd'); }, function (e) { log.first = String(e.code); at('firstEnd'); });
    setTimeout(function () {
        at('replace');
        log.replaceClock = clock();
        gallery.viewAsync(D + '/a.png').then(function (session) {
            at('open');
            session.on('close', function (event) { at('close'); log.reason = String(event.reason); log.end = clock(); setTimeout(function () { done(log); }, 300); });
            setTimeout(function () { session.close(); }, 500);
        }, function (e) { at('rejected'); log.code = String(e.code); log.message = String(e.message); log.end = clock(); done(log); });
    }, DELAY);
}"""

# A viewer left to the user: nothing races here, its session Activity simply stays in its task under the viewer.
LEFT = "function (done) {\n" + CLOCK + """
    gallery.viewAsync(D + '/a.png').then(function (first) {
        at('firstOpen');
        first.on('close', function () {
            setTimeout(function () {
                at('replace');
                log.replaceClock = clock();
                gallery.viewAsync(D + '/b.png').then(function (session) {
                    at('open');
                    session.on('close', function (event) { at('close'); log.reason = String(event.reason); log.end = clock(); setTimeout(function () { done(log); }, 300); });
                    setTimeout(function () { session.close(); }, 500);
                }, function (e) { at('rejected'); log.code = String(e.code); log.message = String(e.message); log.end = clock(); done(log); });
            }, DELAY);
        });
        setTimeout(function () { first.close({finish: false}); }, 500);
    }, function (e) { at('rejected'); log.code = String(e.code); log.message = String(e.message); log.end = clock(); done(log); });
}"""

PICK = "gallery.pickAsync({title: 'aj6'})"
EDIT = "gallery.editAsync(D + '/a.png', {output: D + '/out/edited.png', format: 'png'})"

LOG_LINES = re.compile(r"wm_(task_to_front|new_intent|create_activity|finish_activity|destroy_activity|task_created|task_removed|set_resumed_activity|on_create_called|on_resume_called)"
                       r"|START u0|Background activity|BackgroundActivity|\[ASM\]|ActivityTaskManager.*(Abort|blocked)|GalleryPluginHost|ANR in|FATAL EXCEPTION")
LOG_SCOPE = re.compile(r"maple|autojs6|GalleryPluginHost|ANR in|FATAL EXCEPTION|\[ASM\]")


def table(rounds, left, delay):
    cases = []
    for i in range(rounds):
        cases.append([f"r{i}.view~", VIEW])
        cases.append([f"r{i}.pick~", REPLACED.replace("OPEN", PICK).replace("DELAY", str(delay))])
        cases.append([f"r{i}.edit~", REPLACED.replace("OPEN", EDIT).replace("DELAY", str(delay))])
    for i in range(left):
        cases.append([f"l{i}.left~", LEFT.replace("DELAY", str(delay))])
    return cases


def run_form(args, form, cases):
    run = uuid.uuid4().hex[:8]
    if form == "rhino":
        return family.run_files(args, family.rhino_script(run, [[case[0], family.rhino_source(case[1])] for case in cases]), run)
    sources = [[case[0], family.runtime_source(case[0], case[1])] for case in cases]
    if form.startswith("node"):
        project = family.project(family.PERMISSIONS)
        project["node"]["timeoutMs"] = args.wait * 1000
        return family.run_files(args, family.node_script(form, run, sources), run, project)
    # A bare Bun script gets 60 s; 20 rounds take about 180 s, and the wait ends with the limit of the execution.
    project = {"name": "gallery-replace", "main": "main.js", "bun": {"timeoutMs": BUN_TIMEOUT_MS}}
    limited = argparse.Namespace(**{**vars(args), "wait": BUN_TIMEOUT_MS // 1000 + 60})
    return family.run_files(limited, family.bun_script(form, run, sources), run, project)


def verdict(case_id, value):
    """What went wrong in a step, or None."""
    if value is None:
        return "no result"
    if "th" in value:
        return f"{value['th']['name']} {value.get('message', '')}"
    log = value["ok"]
    if "rejected" in log:
        return f"rejected {log['code']} after {log['rejected']} ms: {log['message'][:120]}"
    if case_id.endswith(("view~", "left~")):
        return None if log.get("reason") == "host" else f"closed with {log.get('reason')}"
    if log.get("first") != "SESSION_REPLACED":
        return f"first session answered {log.get('first')}"
    return None if log.get("reason") == "host" else f"closed with {log.get('reason')}"


def filter_log(raw, target):
    kept = []
    for line in raw.read_text(encoding="utf-8", errors="replace").splitlines():
        if LOG_LINES.search(line) and LOG_SCOPE.search(line):
            kept.append(line)
    target.write_text(NL.join(kept) + NL, encoding="utf-8")
    return len(kept)


def run(args):
    cases = table(args.rounds, args.left, args.replace_delay)
    report, failed = {"forms": {}}, 0
    for form in args.forms.split(","):
        raw = Path(f"{args.log}.{form}.raw") if args.log else None
        capture = None
        if raw:
            handle = raw.open("wb")
            capture = subprocess.Popen(["adb", "-s", args.serial, "logcat", "-b", "main,system,events,crash", "-v", "threadtime", "-T", "1"], stdout=handle, stderr=subprocess.DEVNULL)
            time.sleep(1)
        try:
            results = run_form(args, form, cases)
        finally:
            if capture:
                capture.kill()
                capture.wait()
                handle.close()
            if args.left:
                # The viewers the steps left open go with the plugin's process.
                sample.adb(args.serial, "shell", "am", "force-stop", family.MAPLE, check=False)
        steps, failures, gaps = [], [], {}
        for i, (case_id, _) in enumerate(cases):
            value = results.get(i)
            steps.append({"id": case_id, "result": value})
            problem = verdict(case_id, value)
            if problem:
                failures.append({"id": case_id, "problem": problem, "start": (value or {}).get("ok", {}).get("start"), "replace": (value or {}).get("ok", {}).get("replaceClock"), "end": (value or {}).get("ok", {}).get("end")})
            opened = (value or {}).get("ok", {})
            if "open" in opened:
                gaps.setdefault(case_id.split(".")[1], []).append(opened["open"] - opened.get("replace", 0))
        failed += len(failures)
        slowest = {kind: max(values) for kind, values in gaps.items()}
        print(f"{form}: {len(cases) - len(failures)} / {len(cases)} steps, {len(failures)} failed, slowest open (ms) {slowest}")
        for failure in failures:
            print(f"  {failure['id']}: {failure['problem']} (start {failure['start']}, replace {failure['replace']}, end {failure['end']})")
        report["forms"][form] = {"steps": len(cases), "failures": failures, "slowestOpen": slowest, "results": steps}
        if raw:
            kept = filter_log(raw, Path(f"{args.log}.{form}.txt"))
            if not args.keep_raw:
                raw.unlink()
            print(f"  {kept} log lines kept")
    if args.report:
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=1) + NL, encoding="utf-8")
    sys.exit(1 if failed else 0)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("phase", choices=["run"])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--forms", default=",".join(family.FORMS))
    parser.add_argument("--rounds", type=int, default=20)
    parser.add_argument("--left", type=int, default=0, help="steps that leave a viewer open with {finish: false} and open another one after --replace-delay")
    parser.add_argument("--replace-delay", type=int, default=2500, help="ms between a picker or an editor and the viewer that replaces it")
    parser.add_argument("--log", help="path prefix of the filtered logcat, one file per form")
    parser.add_argument("--keep-raw", action="store_true", help="keep the whole logcat next to the filtered one")
    parser.add_argument("--report", type=Path)
    parser.add_argument("--wait", type=int, default=1800)
    parser.add_argument("--foreground", action="store_true", help="bring the host's launcher activity to the front before each script")
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="backslashreplace")
    base.FOREGROUND = args.foreground
    run(args)


if __name__ == "__main__":
    main()
