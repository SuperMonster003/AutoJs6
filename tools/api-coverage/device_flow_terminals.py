#!/usr/bin/env python3
"""Closing checks of the api-coverage P2.6 / P3.6 / P4.6 card on a device.

Phases:
  flow   Flow await() and sync(timeout) (Q202): Rhino blocks and suspends, Node main / Worker and Bun main / Worker answer
         with Promises; every form must give the values and errors of Rhino.
  entry  new Worker(__filename) in a Node project without a package.json (Q200).
  exit   process.exit() leaves no armed global timer behind (Q201), with a control run that shows the harness sees a timer;
         needs no accessibility service, so it also works on devices where the timeout of auto.waitFor cannot be reached.
  rejection  Rhino await() in a coroutine throws the rejection at the call (Q205): promise.await(), flow.await(),
         continuation.await(promise) and cont.await() after resumeError, in a background script and in UI mode. What no script
         catches ends the script with its error and leaves the host process alive: throwing it crashed the app until 6.3.4.

Only one line per check is printed; the pushed directory is removed afterwards. The packages are recorded and restored by
the record / restore phases of device_bun_js_tools.py.

Usage: device_flow_terminals.py --serial SERIAL [--phases flow,entry,exit,rejection] [--runtimes rhino,node,bun] [--report FILE]
Expects host 6.8.0 with the Node and Bun plugins installed. Flow chains that stay local need no Bun grant.
"""
import argparse
import json
from pathlib import Path
import sys
import uuid

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import device_bun_js_tools as base  # noqa: E402

base.DEVICE_DIR = "/sdcard/Download/aj6-flow-terminals"

# ASYNC and AWAIT are empty in the Rhino form. Each check gives {ok: value} or {th: [name, code, step, reason]}.
BODY = """
ASYNC function aj6Checks(flow) {
    var out = {};
    var failure = function (e) { return [String(e.name), e.code === undefined ? null : e.code, e.step === undefined ? null : e.step, e.reason === undefined ? null : e.reason]; };
    var check = ASYNC function (name, run) {
        try { out[name] = {ok: AWAIT run()}; } catch (e) { out[name] = {th: failure(e)}; }
    };
    var thrower = function () { throw new RangeError('failed step'); };
    AWAIT check('sync.value', ASYNC function () { return AWAIT flow.of(3).map(function (x) { return x + 1; }).sync(); });
    AWAIT check('sync.limit', ASYNC function () { return AWAIT flow.of(3).sync(5000); });
    AWAIT check('sync.infinity', ASYNC function () { return AWAIT flow.of(3).sync(Infinity); });
    AWAIT check('sync.null', ASYNC function () { return AWAIT flow.of(3).sync(null); });
    AWAIT check('sync.timeout', ASYNC function () { return AWAIT flow.sleep(2000).sync(50); });
    AWAIT check('sync.keeps', ASYNC function () {
        var chain = flow.sleep(400).map(function () { return 'late'; }), code = null;
        try { AWAIT chain.sync(20); } catch (e) { code = e.code; }
        var state = chain.state;
        return [code, state, AWAIT chain.sync()];
    });
    AWAIT check('sync.rejected', ASYNC function () { return AWAIT flow.of(1).map(thrower).sync(); });
    AWAIT check('sync.cancelled', ASYNC function () {
        var chain = flow.sleep(2000);
        chain.cancel();
        return AWAIT chain.sync();
    });
    AWAIT check('sync.invalid', ASYNC function () {
        try { AWAIT flow.of(1).sync(-1); return 'no error'; } catch (e) { return 'threw'; }
    });
    AWAIT check('await.value', ASYNC function () { return AWAIT flow.of(5).map(function (x) { return x + 1; }).await(); });
    AWAIT check('await.rejected', ASYNC function () { return AWAIT flow.of(1).map(thrower).await(); });
    return out;
}
"""
EXPECTED = {
    "sync.value": {"ok": 4},
    "sync.limit": {"ok": 3},
    "sync.infinity": {"ok": 3},
    "sync.null": {"ok": 3},
    "sync.timeout": {"th": ["FlowError", "TIMEOUT", "sync", "sync"]},
    "sync.keeps": {"ok": ["TIMEOUT", "pending", "late"]},
    "sync.rejected": {"th": ["RangeError", None, None, None]},
    "sync.cancelled": {"th": ["FlowError", "CANCELLED", "sleep", None]},
    "sync.invalid": {"ok": "threw"},
    "await.value": {"ok": 6},
    "await.rejected": {"th": ["RangeError", None, None, None]},
}

RHINO_MAIN = """
"use strict";
const AJ6_RUN = 'RUN';
EMIT
RHINO_BODY
aj6Emit(AJ6_RUN, 0, aj6Checks(flow));
aj6Emit(AJ6_RUN, -1, {done: 1});
"""

NODE_MAIN = """
"nodejs";
const AJ6_RUN = 'RUN';
EMIT
ASYNC_BODY
const {Worker} = require('node:worker_threads');
(async () => {
    aj6Emit(AJ6_RUN, 0, await aj6Checks(require('autojs6:flow')));
    aj6Emit(AJ6_RUN, 1, await new Promise((resolve, reject) => {
        const worker = new Worker(`
const {parentPort} = require('node:worker_threads');
const aj6Checks = ${aj6Checks};
aj6Checks(require('autojs6:flow')).then(value => parentPort.postMessage(value), e => parentPort.postMessage({error: String(e && e.stack || e)}));
`, {eval: true});
        worker.on('message', resolve);
        worker.on('error', reject);
    }));
})().catch(e => aj6Emit(AJ6_RUN, 2, {error: String(e && e.stack || e)})).finally(() => aj6Emit(AJ6_RUN, -1, {done: 1}));
"""

BUN_MAIN = """
"bun";
const AJ6_RUN = 'RUN';
EMIT
ASYNC_BODY
(async () => {
    const {createHostClient} = await import(process.env.AUTOJS6_HOST_CLIENT_MODULE);
    const host = await createHostClient();
    aj6Emit(AJ6_RUN, 0, await aj6Checks(host.flow));
    const worker = new Worker(URL.createObjectURL(new Blob([`
BODY_TEXT
self.onmessage = async () => {
    try {
        const {createHostClient} = await import(process.env.AUTOJS6_HOST_CLIENT_MODULE);
        const host = await createHostClient();
        postMessage(await aj6Checks(host.flow));
    } catch (e) {
        postMessage({error: String(e && e.stack || e)});
    }
};
`], {type: 'text/javascript'})));
    aj6Emit(AJ6_RUN, 1, await new Promise((resolve, reject) => {
        worker.onmessage = event => resolve(event.data);
        worker.onerror = event => reject(new Error(event.message));
        worker.postMessage(1);
    }));
    worker.terminate();
})().catch(e => aj6Emit(AJ6_RUN, 2, {error: String(e && e.stack || e)})).finally(() => aj6Emit(AJ6_RUN, -1, {done: 1}));
"""

ENTRY_MAIN = """
"nodejs";
const AJ6_RUN = 'RUN';
EMIT
const {Worker, isMainThread, parentPort} = require('node:worker_threads');
if (isMainThread) {
    const worker = new Worker(__filename);
    worker.on('message', value => { aj6Emit(AJ6_RUN, 0, value); aj6Emit(AJ6_RUN, -1, {done: 1}); });
    worker.on('error', e => { aj6Emit(AJ6_RUN, 1, {error: String(e && e.stack || e)}); aj6Emit(AJ6_RUN, -1, {done: 1}); });
} else {
    parentPort.postMessage({entered: require('node:path').basename(__filename)});
}
"""

# Index 0 runs at once. 1 comes from a timer that may run, 2 from what process.exit must have cleared.
EXIT_HEAD = """
"nodejs";
const AJ6_RUN = 'RUN';
EMIT
aj6Emit(AJ6_RUN, 0, {ok: 'before'});
const late = () => aj6Emit(AJ6_RUN, 2, {ok: 'late'});
"""
EXIT_CASES = {
    "control": ("setTimeout(() => { late(); aj6Emit(AJ6_RUN, -1, {done: 1}); }, 500);", [0, 2]),
    "timeout": ("setTimeout(late, 500); process.exit(0);", [0]),
    "interval": ("setInterval(late, 300); process.exit(0);", [0]),
    "immediate": ("setImmediate(late); process.exit(0);", [0]),
    "from-timer": ("setTimeout(late, 1500); setTimeout(() => { aj6Emit(AJ6_RUN, 1, {ok: 'first'}); process.exit(0); }, 300);", [0, 1]),
    "from-promise": ("setTimeout(late, 800); Promise.resolve().then(() => process.exit(0));", [0]),
    "with-worker": ("const {Worker} = require('node:worker_threads'); new Worker('setInterval(() => {}, 100)', {eval: true}); "
                    "setTimeout(late, 800); setTimeout(() => process.exit(0), 300);", [0]),
}
EXIT_WAIT_SECONDS = 4

# Every check gives {ok: value} or {th: [typeof, name, message]}. MODE is empty in a background script and "ui"; in UI mode.
REJECTION_MAIN = """
MODE
const AJ6_RUN = 'RUN';
EMIT
var out = {};
var check = function (name, run) {
    try {
        out[name] = {ok: run()};
    } catch (e) {
        var object = e !== null && e !== undefined;
        out[name] = {th: [typeof e, object && e.name !== undefined ? String(e.name) : null, object && e.message !== undefined ? String(e.message) : String(e)]};
    }
};
var rejected = function (reason) { return Promise.reject(reason); };
var thrower = function () { throw new RangeError('boom'); };
check('promise', function () { return rejected(new RangeError('boom')).await(); });
check('continuation', function () { return continuation.await(rejected(new RangeError('boom'))); });
check('flow', function () { return flow.of(1).map(thrower).await(); });
check('text', function () { return rejected('text').await(); });
check('null', function () { return rejected(null).await(); });
check('later', function () { return new Promise(function (_, reject) { setTimeout(function () { reject(new Error('later')); }, 50); }).await(); });
check('thread', function () { return new Promise(function (_, reject) { threads.start(function () { reject(new Error('thread')); }); }).await(); });
check('resumeError', function () { var cont = continuation.create(); setTimeout(function () { cont.resumeError(new Error('direct')); }, 20); return cont.await(); });
check('resumeError.thread', function () { var cont = continuation.create(); threads.start(function () { cont.resumeError(new Error('direct')); }); return cont.await(); });
check('resolved', function () { return Promise.resolve(5).await(); });
check('afterwards', function () { var seen = []; try { rejected(1).await(); } catch (e) { seen.push(e); } try { seen.push(Promise.resolve(2).await()); } finally { seen.push(3); } return seen; });
aj6Emit(AJ6_RUN, 0, out);
aj6Emit(AJ6_RUN, -1, {done: 1});
exit();
"""
REJECTION_EXPECTED = {
    "promise": {"th": ["object", "RangeError", "boom"]},
    "continuation": {"th": ["object", "RangeError", "boom"]},
    "flow": {"th": ["object", "RangeError", "boom"]},
    "text": {"th": ["string", None, "text"]},
    "null": {"th": ["object", None, "null"]},
    "later": {"th": ["object", "Error", "later"]},
    "thread": {"th": ["object", "Error", "thread"]},
    "resumeError": {"th": ["object", "Error", "direct"]},
    "resumeError.thread": {"th": ["object", "Error", "direct"]},
    "resolved": {"ok": 5},
    "afterwards": {"ok": [1, 2, 3]},
}

# Index 0 runs at once. 1 follows the failing await and 2 comes from a timer: neither may run once the script has ended.
UNCAUGHT_MAIN = """
MODE
const AJ6_RUN = 'RUN';
EMIT
aj6Emit(AJ6_RUN, 0, {ok: 'before'});
setTimeout(function () { aj6Emit(AJ6_RUN, 2, {ok: 'late'}); }, 1500);
var aj6Message = 'aj6-uncaught-RUN';
BODY
aj6Emit(AJ6_RUN, 1, {ok: 'not reached'});
"""
UNCAUGHT_CASES = {
    "promise": "Promise.reject(new Error(aj6Message)).await();",
    "continuation": "continuation.await(Promise.reject(new Error(aj6Message)));",
    "flow": "flow.of(1).map(function () { throw new Error(aj6Message); }).await();",
    "resumeError": "var cont = continuation.create(); setTimeout(function () { cont.resumeError(new Error(aj6Message)); }, 20); cont.await();",
    "resumeError.thread": "var cont = continuation.create(); threads.start(function () { cont.resumeError(new Error(aj6Message)); }); cont.await();",
    "after await": "Promise.resolve(1).await(); throw new Error(aj6Message);",
}
UNCAUGHT_WAIT_SECONDS = 4


def project(name, permissions=()):
    return {"project.json": json.dumps({"name": name, "type": "node", "main": "main.js",
                                        "node": {"permissions": list(permissions), "timeoutMs": 60000}}).encode("utf-8")}


def source(template, run):
    rhino = BODY.replace("ASYNC ", "").replace("AWAIT ", "")
    modern = BODY.replace("ASYNC ", "async ").replace("AWAIT ", "await ")
    body_text = modern.replace("\\", "\\\\").replace("`", "\\`").replace("${", "\\${")
    return (template.lstrip().replace("BODY_TEXT", body_text).replace("RHINO_BODY", rhino).replace("ASYNC_BODY", modern)
            .replace("EMIT", base.EMIT_JS).replace("RUN", run))


def verdicts(report, runtime, forms, results):
    failed = 0
    for index, form in forms:
        out = results.get(index) or {}
        for name, expected in EXPECTED.items():
            ok = out.get(name) == expected
            report[f"{runtime} {form} {name}"] = "pass" if ok else json.dumps(out.get(name, out.get("error", "missing")), ensure_ascii=False)[:220]
            failed += not ok
    if 2 in results:
        report[f"{runtime} error"] = str(results[2].get("error", ""))[:300]
        failed += 1
    return failed


# Rhino's await() needs the continuation feature, which reaches the engine only through a project launch.
RHINO_PROJECT = json.dumps({"name": "flow-terminals", "versionName": "1.0.0", "versionCode": 1, "packageName": "org.autojs.flowterminals",
                            "main": "main.js", "useFeatures": ["continuation"]}).encode("utf-8")
RHINO_LAUNCH = ("-e", "path", base.DEVICE_DIR, "-e", "source_kind", "autojs_project_v1", "-e", "project_root", base.DEVICE_DIR)


def phase_flow(serial, report, runtimes):
    failed = 0
    for runtime, template, forms, extra, launch in (
            ("rhino", RHINO_MAIN, ((0, "main"),), {"project.json": RHINO_PROJECT}, RHINO_LAUNCH),
            ("node", NODE_MAIN, ((0, "main"), (1, "worker")), project("flow-terminals"), ()),
            ("bun", BUN_MAIN, ((0, "main"), (1, "worker")), None, ())):
        if runtime not in runtimes:
            continue
        run = uuid.uuid4().hex[:8]
        failed += verdicts(report, runtime, forms, base.run_script(serial, "main.js", source(template, run), run, 60, extra, launch))
    return failed


def phase_entry(serial, report, _runtimes):
    run = uuid.uuid4().hex[:8]
    results = base.run_script(serial, "main.js", source(ENTRY_MAIN, run), run, 30, project("worker-entry"))
    ok = results.get(0) == {"entered": "main.js"}
    report["node worker entry without package.json"] = "pass" if ok else json.dumps(results, ensure_ascii=False)[:300]
    return not ok


def phase_exit(serial, report, _runtimes):
    failed = 0
    for name, (body, expected) in EXIT_CASES.items():
        run = uuid.uuid4().hex[:8]
        text = source(EXIT_HEAD + body + chr(10), run)
        results = base.run_script(serial, "main.js", text, run, EXIT_WAIT_SECONDS, project("process-exit"))
        got = sorted(index for index in results if index >= 0)
        ok = got == expected
        report[f"node exit {name}"] = "pass" if ok else f"got {got}, expected {expected}"
        failed += not ok
    return failed


def host_pid(serial):
    return base.sample.adb(serial, "shell", f"pidof {base.HOST}", check=False).strip()


def phase_rejection(serial, report, _runtimes):
    failed = 0
    for mode, directive in (("background", ""), ("ui", '"ui";')):
        run = uuid.uuid4().hex[:8]
        text = source(REJECTION_MAIN.replace("MODE", directive), run)
        out = base.run_script(serial, "main.js", text, run, 30, {"project.json": RHINO_PROJECT}, RHINO_LAUNCH).get(0) or {}
        for name, expected in REJECTION_EXPECTED.items():
            ok = out.get(name) == expected
            report[f"rhino {mode} {name}"] = "pass" if ok else json.dumps(out.get(name, "missing"), ensure_ascii=False)[:220]
            failed += not ok
        for name, body in UNCAUGHT_CASES.items():
            run = uuid.uuid4().hex[:8]
            before = host_pid(serial)
            text = source(UNCAUGHT_MAIN.replace("MODE", directive).replace("BODY", body), run)
            results = base.run_script(serial, "main.js", text, run, UNCAUGHT_WAIT_SECONDS, {"project.json": RHINO_PROJECT}, RHINO_LAUNCH)
            got = sorted(index for index in results if index >= 0)
            logged = base.sample.adb(serial, "shell", f"logcat -d -t 3000 | grep -c aj6-uncaught-{run}", check=False).strip() not in ("", "0")
            alive = host_pid(serial) == before
            ok = got == [0] and logged and alive
            report[f"rhino {mode} uncaught {name}"] = "pass" if ok else f"got {got}, logged {logged}, host alive {alive}"
            failed += not ok
    return failed


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--phases", default="flow,entry,exit,rejection")
    parser.add_argument("--runtimes", default="rhino,node,bun", help="flow: the runtimes to run")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="backslashreplace")
    report, failed = {}, 0
    phases = {"flow": phase_flow, "entry": phase_entry, "exit": phase_exit, "rejection": phase_rejection}
    for phase in args.phases.split(","):
        before = len(report)
        count = phases[phase](args.serial, report, args.runtimes.split(","))
        failed += count
        print(f"{phase}: {len(report) - before - count} / {len(report) - before}")
    for key, value in report.items():
        if value != "pass":
            print(f"  {key}: {value}")
    if args.report:
        args.report.write_text(json.dumps(report, indent=1, ensure_ascii=False) + chr(10), encoding="utf-8")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
