#!/usr/bin/env python3
"""Closing checks of the api-coverage P2.6 / P3.6 / P4.6 card on a device.

Phases:
  flow   Flow await() and sync(timeout) (Q202): Rhino blocks and suspends, Node main / Worker and Bun main / Worker answer
         with Promises; every form must give the values and errors of Rhino.
  entry  new Worker(__filename) in a Node project without a package.json (Q200).
  exit   process.exit() leaves no armed global timer behind (Q201), with a control run that shows the harness sees a timer;
         needs no accessibility service, so it also works on devices where the timeout of auto.waitFor cannot be reached.

Only one line per check is printed; the pushed directory is removed afterwards. The packages are recorded and restored by
the record / restore phases of device_bun_js_tools.py.

Usage: device_flow_terminals.py --serial SERIAL [--phases flow,entry,exit] [--runtimes rhino,node,bun] [--report FILE]
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

# Q205: in a continuation Rhino's await() hands a rejection back as its value (an Error, {} as JSON) instead of throwing it.
RHINO_DIFFERENCES = {"await.rejected": {"ok": {}}}

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
            ok = out.get(name) == (RHINO_DIFFERENCES.get(name, expected) if runtime == "rhino" else expected)
            report[f"{runtime} {form} {name}"] = "pass" if ok else json.dumps(out.get(name, out.get("error", "missing")), ensure_ascii=False)[:220]
            failed += not ok
    if 2 in results:
        report[f"{runtime} error"] = str(results[2].get("error", ""))[:300]
        failed += 1
    return failed


def phase_flow(serial, report, runtimes):
    failed = 0
    # Rhino's await() needs the continuation feature, which reaches the engine only through a project launch.
    rhino_project = json.dumps({"name": "flow-terminals", "versionName": "1.0.0", "versionCode": 1, "packageName": "org.autojs.flowterminals",
                                "main": "main.js", "useFeatures": ["continuation"]}).encode("utf-8")
    rhino_launch = ("-e", "path", base.DEVICE_DIR, "-e", "source_kind", "autojs_project_v1", "-e", "project_root", base.DEVICE_DIR)
    for runtime, template, forms, extra, launch in (
            ("rhino", RHINO_MAIN, ((0, "main"),), {"project.json": rhino_project}, rhino_launch),
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


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--phases", default="flow,entry,exit")
    parser.add_argument("--runtimes", default="rhino,node,bun", help="flow: the runtimes to run")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="backslashreplace")
    report, failed = {}, 0
    phases = {"flow": phase_flow, "entry": phase_entry, "exit": phase_exit}
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
