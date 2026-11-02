#!/usr/bin/env python3
"""The imgui escape hatch of Node.js and Bun scripts on a device (api-coverage Q213 (b), IMGUI-PLAN.md 2.3 and card A).

imgui is rhino-only: a Node.js or Bun script opens its window through a Rhino script started with engines.execScript and
talks to it with execution arguments and events.broadcast. Each case runs in both runtimes:

  example  the example of the documentation imgui page, verbatim apart from its last console.log, which reports here
  details  the facts the page states: the engine and spawn depth of the started script, a host execution that ends after the
           window closes, a timeoutMs that only limits the start, argument and broadcast value types in both directions
  leave    the main script ends first: the panel stays open and a broadcast from another script still closes it

Overlay windows of the host are counted around every case (the plugin's window must appear and go), so a run also
needs the display on. Only one line per check is printed, without the texts of the scripts. --doc checks that the page
still holds the example (offline, no device). Bun needs three host grants, set for the run and restored by it (--state);
no dialog is answered here. Packages are not replaced.

Usage: device_imgui_escape.py --serial SERIAL --state DIR [--cases example,details,leave] [--runtimes node,bun] [--report FILE]
       device_imgui_escape.py --doc [PAGE]
Expects host 6.8.0 with the ImGui plugin enabled, and the Node and Bun plugins installed.
"""
import argparse
import json
import os
from pathlib import Path
import re
import sys
import threading
import time
import uuid

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import device_bun_js_tools as base  # noqa: E402

base.DEVICE_DIR = "/sdcard/Download/aj6-imgui-escape"
sample = base.sample

BUN_GRANTS = ["engines.control", "engines.exec", "events.observe"]
NODE_PERMISSIONS = ["engines", "engines.exec", "events"]
# JavaScriptSource.ENGINE, the engine name of Rhino scripts.
RHINO_ENGINE = "org.autojs.autojs.script.JavaScriptSource.Engine"
OVERLAY_POLL_SECONDS = 0.6
WINDOW_GONE_SECONDS = 6

# The two heads and the body are the example of the documentation page; --doc compares them with it.
NODE_HEAD = """\
"nodejs";

let engines = require('engines');
let events = require('autojs6:events');
"""

BUN_HEAD = """\
"bun";

let { createHostClient } = await import(process.env.AUTOJS6_HOST_CLIENT_MODULE);
let { engines, events } = await createHostClient();
"""

EXAMPLE_BODY = """\

let panel = String.raw`"rhino";
let progress = 0;
let session = imgui.createWindow(function (ui) {
    ui.withWindow(engines.myEngine().execArgv.title, null, 0, function () {
        ui.progressBar(progress);
        if (ui.button('取消')) {
            events.broadcast.emit('panel.cancel');
        }
    });
}, { width: 280, height: 140 });
session.on('closed', function () {
    events.broadcast.emit('panel.closed');
});
events.broadcast.on('panel.progress', function (value) {
    progress = value;
});
events.broadcast.on('panel.close', function () {
    session.close();
});
// createWindow 在窗口就绪后才返回, 监听器也已注册.
events.broadcast.emit('panel.ready');
`;

(async () => {
    let cancelled = false;
    let ready = new Promise(resolve => events.broadcast.once('panel.ready', resolve));
    let closed = new Promise(resolve => events.broadcast.once('panel.closed', resolve));
    events.broadcast.on('panel.cancel', () => cancelled = true);
    // emit 在订阅生效后才发送, 所以之后启动的面板不会错过 panel.ready.
    await events.broadcast.emit('panel.starting');

    await engines.execScript('panel', panel, { arguments: { title: '任务进度' } });
    await ready;
    for (let step = 1; step <= 5 && !cancelled; step += 1) {
        await new Promise(resolve => setTimeout(resolve, 1000)); // 实际工作.
        await events.broadcast.emit('panel.progress', step / 5);
    }
    await events.broadcast.emit('panel.close');
    await closed;
    events.broadcast.removeAllListeners();
    console.log(cancelled ? '已取消' : '已完成');
})();
"""

# A panel that answers on channel @CH@ and reports what it was given.
PROBE_PANEL = r"""
"rhino";
var argv = engines.myEngine().execArgv;
var frames = 0;
var describe = function (value) {
    var text;
    try { text = JSON.stringify(value); } catch (e) { text = String(e); }
    return {type: typeof value, java: value instanceof java.lang.Object, json: text === undefined ? null : text, action: String(value && value.action), n: String(value && value.nested && value.nested.n)};
};
try {
    var session = imgui.createWindow(function (ui) {
        frames += 1;
        ui.text('escape ' + frames);
    }, {width: 200, height: 90});
    session.on('closed', function (event) {
        events.broadcast.emit('@CH@.closed', JSON.stringify({frames: frames, reason: String(event.reason)}));
    });
    events.broadcast.on('@CH@.value', function (value) { events.broadcast.emit('@CH@.echo', JSON.stringify(describe(value))); });
    events.broadcast.on('@CH@.frames', function () { events.broadcast.emit('@CH@.framesReply', frames); });
    events.broadcast.on('@CH@.close', function () { session.close(); });
    events.broadcast.emit('@CH@.raw', {a: 1}, [1, 'two'], 'text', 2.5, true, null);
    events.broadcast.emit('@CH@.ready', JSON.stringify({title: argv.title, count: argv.count, flag: argv.flag}));
} catch (e) {
    events.broadcast.emit('@CH@.error', String(e));
}
"""

ARGUMENTS_PANEL = """"rhino";
events.broadcast.emit('@CH@.argv', JSON.stringify({keys: Object.keys(engines.myEngine().execArgv || {}).map(String)}));
"""

SHARED = """
const panelSource = @PANEL@;
const argumentsSource = @ARGUMENTS@;
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const wait = (name, ms = 20000) => new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('no ' + name)), ms);
    events.broadcast.once(name, (...args) => { clearTimeout(timer); resolve(args); });
});
const started = async () => {
    const ready = wait('@CH@.ready');
    const raw = wait('@CH@.raw');
    events.broadcast.once('@CH@.error', text => aj6Emit('@RUN@', 8, {panelError: text}));
    await events.broadcast.emit('@CH@.starting');
    const snapshot = await engines.execScript('aj6-escape', panelSource, {arguments: {title: 'escape', count: 3, flag: true}, timeoutMs: 1000});
    return [snapshot, JSON.parse((await ready)[0]), await raw];
};
const finish = () => { events.broadcast.removeAllListeners(); aj6Emit('@RUN@', -1, {done: 1}); };
"""

DETAILS = """
(async () => {
    const out = {};
    const [snapshot, ready, raw] = await started();
    out.raw = raw;
    out.snapshot = {engineName: snapshot.engineName, spawnDepth: snapshot.spawnDepth, id: typeof snapshot.id};
    out.ready = ready;
    const listed = (await engines.all()).find(item => item.id === snapshot.id);
    out.listed = listed ? {engineName: listed.engineName, state: listed.state} : null;
    for (const [name, value] of [['object', {action: 'close', nested: {n: 2}}], ['text', 'word'], ['number', 2.5], ['list', [1, 'two']], ['nothing', null]]) {
        const echo = wait('@CH@.echo');
        await events.broadcast.emit('@CH@.value', value);
        out['echo.' + name] = JSON.parse((await echo)[0]);
    }
    // Past the timeoutMs of 1000 the frames still run.
    const before = wait('@CH@.framesReply');
    await events.broadcast.emit('@CH@.frames');
    const first = (await before)[0];
    await sleep(2500);
    const after = wait('@CH@.framesReply');
    await events.broadcast.emit('@CH@.frames');
    out.frames = [first, (await after)[0]];
    const closed = wait('@CH@.closed');
    await events.broadcast.emit('@CH@.close');
    out.closed = JSON.parse((await closed)[0]);
    const deadline = Date.now() + 8000;
    while ((await engines.all()).some(item => item.id === snapshot.id) && Date.now() < deadline) await sleep(200);
    out.ended = !(await engines.all()).some(item => item.id === snapshot.id);
    const argv = wait('@CH@.argv');
    try {
        await engines.execScript('aj6-escape-arguments', argumentsSource, {arguments: {title: 'kept', nested: {a: 1}}, timeoutMs: 5000});
        out.argumentsObject = (await argv)[0];
    } catch (e) {
        out.argumentsObject = 'threw ' + (e.code || e.name);
    }
    aj6Emit('@RUN@', 0, out);
})().catch(e => aj6Emit('@RUN@', 9, {error: String(e && e.stack || e).slice(0, 400)})).finally(finish);
"""

LEAVE = """
(async () => {
    const [snapshot] = await started();
    aj6Emit('@RUN@', 0, {id: snapshot.id});
})().catch(e => aj6Emit('@RUN@', 9, {error: String(e && e.stack || e).slice(0, 400)})).finally(finish);
"""

# Closes the panel of the leave case from a script of its own.
CLOSER = """
var AJ6_CHANNEL = '@CH@';
@EMIT@
var closed = null;
events.broadcast.on(AJ6_CHANNEL + '.closed', function (value) { closed = JSON.parse(value); });
events.broadcast.emit(AJ6_CHANNEL + '.close');
var deadline = Date.now() + 8000;
setInterval(function () {
    if (closed || Date.now() > deadline) {
        aj6Emit('@RUN@', 0, {closed: closed});
        aj6Emit('@RUN@', -1, {done: 1});
        exit();
    }
}, 100);
"""


# What a failed run can leave: its main script and the panels.
STOP_LEFTOVERS = r"""
@EMIT@
var list = engines.all();
for (var i = 0; i < list.length; i++) {
    if (/aj6-imgui-escape\/main\.js|\$engine\/(aj6-escape|panel)/.test(String(list[i].getSource()))) list[i].forceStop();
}
aj6Emit('@RUN@', -1, {done: 1});
"""


def overlays(serial):
    """Application overlay windows of the host: a block per window in dumpsys window."""
    text = sample.adb(serial, "shell", "dumpsys window windows", check=False, timeout=30) or ""
    blocks = re.split(r"(?m)^\s*Window #\d+ ", text)[1:]
    return sum(f"package={sample.HOST}" in block.replace("mPackageName=", "package=") and "ty=APPLICATION_OVERLAY" in block for block in blocks)


class OverlayWatch:
    def __init__(self, serial):
        self.serial, self.before, self.peak = serial, overlays(serial), 0
        self.stop_event, self.thread = threading.Event(), threading.Thread(target=self.poll, daemon=True)
        self.thread.start()

    def poll(self):
        while not self.stop_event.wait(OVERLAY_POLL_SECONDS):
            self.peak = max(self.peak, overlays(self.serial))

    def finish(self):
        self.stop_event.set()
        self.thread.join()
        deadline = time.time() + WINDOW_GONE_SECONDS
        left = overlays(self.serial)
        while left > self.before and time.time() < deadline:
            time.sleep(0.5)
            left = overlays(self.serial)
        return self.before, self.peak, left


def source(runtime, template, run, channel):
    if template is None:
        head = NODE_HEAD if runtime == "node" else BUN_HEAD
        body = EXAMPLE_BODY.replace("console.log(cancelled ? '已取消' : '已完成');",
                                    f"aj6Emit('{run}', 0, {{cancelled}}); aj6Emit('{run}', -1, {{done: 1}});")
        return head + base.EMIT_JS + body
    head = NODE_HEAD if runtime == "node" else BUN_HEAD
    shared = SHARED.replace("@PANEL@", json.dumps(PROBE_PANEL)).replace("@ARGUMENTS@", json.dumps(ARGUMENTS_PANEL))
    return (head + base.EMIT_JS + shared + template).replace("@RUN@", run).replace("@CH@", channel)


def launch(runtime, serial, template, wait):
    """Runs one main script; the channel of the probe panel is unique to the run."""
    run, channel = uuid.uuid4().hex[:8], "aj6e" + uuid.uuid4().hex[:6]
    extra = None
    if runtime == "node":
        extra = {"project.json": json.dumps({"name": "imgui-escape", "type": "node", "main": "main.js",
                                             "node": {"permissions": NODE_PERMISSIONS}}).encode("utf-8")}
    return channel, base.run_script(serial, "main.js", source(runtime, template, run, channel), run, wait, extra)


def window_note(before, peak, left):
    return f"overlays {before} -> {peak} -> {left}"


def window_ok(before, peak, left):
    return peak > before and left == before


def case_example(runtime, serial, check):
    watch = OverlayWatch(serial)
    _, results = launch(runtime, serial, None, 40)
    window = watch.finish()
    check(f"{runtime} example finishes", results.get(0) == {"cancelled": False}, json.dumps(results, ensure_ascii=False)[:200])
    check(f"{runtime} example window", window_ok(*window), window_note(*window))


def case_details(runtime, serial, check):
    watch = OverlayWatch(serial)
    _, results = launch(runtime, serial, DETAILS, 60)
    window = watch.finish()
    out = results.get(0) or {}
    if not out:
        check(f"{runtime} details", False, json.dumps(results, ensure_ascii=False)[:400])
        return
    snapshot = out["snapshot"]
    check(f"{runtime} started in Rhino", snapshot["engineName"] == RHINO_ENGINE and snapshot["spawnDepth"] == 1, f"spawnDepth {snapshot['spawnDepth']}")
    check(f"{runtime} listed in engines.all", bool(out.get("listed")))
    ready = out.get("ready") or {}
    check(f"{runtime} arguments", (ready.get("title"), ready.get("count"), ready.get("flag")) == ("escape", 3, True),
          json.dumps([ready.get("title"), ready.get("count"), ready.get("flag")]))
    check(f"{runtime} Rhino to main values", out.get("raw") == [{"a": 1}, [1, "two"], "text", 2.5, True, None], json.dumps(out.get("raw"), ensure_ascii=False)[:200])
    check(f"{runtime} Rhino to main JSON text", ready.get("title") == "escape")
    # Objects travel both ways (Q218 (a)); the JSON text of the page's example works as well.
    echo = out.get("echo.object") or {}
    check(f"{runtime} main to Rhino object", echo.get("java") is False and (echo.get("action"), echo.get("n")) == ("close", "2")
          and json.loads(echo.get("json") or "null") == {"action": "close", "nested": {"n": 2}}, f"action {echo.get('action')}")
    check(f"{runtime} main to Rhino text and number", (out.get("echo.text") or {}).get("type") == "string" and (out.get("echo.number") or {}).get("type") == "number")
    frames = out.get("frames") or [0, 0]
    check(f"{runtime} frames past timeoutMs", frames[1] > frames[0], f"{frames[0]} -> {frames[1]}")
    closed = out.get("closed") or {}
    check(f"{runtime} session closed", closed.get("frames", 0) > 0, json.dumps(closed))
    check(f"{runtime} Rhino script ended", out.get("ended") is True, str(out.get("ended")))
    check(f"{runtime} window", window_ok(*window), window_note(*window))
    print(f"  {runtime} arguments with an object: {json.dumps(out.get('argumentsObject'))[:160]}")


def case_leave(runtime, serial, check):
    watch = OverlayWatch(serial)
    channel, results = launch(runtime, serial, LEAVE, 40)
    time.sleep(3)
    open_after_main = overlays(serial)
    run = uuid.uuid4().hex[:8]
    closer = CLOSER.replace("@EMIT@", base.EMIT_JS).replace("@CH@", channel).replace("@RUN@", run)
    closed = base.run_script(serial, "closer.js", closer, run, 20).get(0) or {}
    window = watch.finish()
    check(f"{runtime} panel outlives the main script", 0 in results and open_after_main > window[0], f"overlays {open_after_main}")
    check(f"{runtime} other script closes it", bool(closed.get("closed")) and window[2] == window[0], window_note(*window))


CASES = {"example": case_example, "details": case_details, "leave": case_leave}


def check_doc(page):
    if not page.is_file():
        print(f"SKIP doc: no {page}")
        return 0
    text = page.read_text(encoding="utf-8").replace("\r\n", "\n")
    failed = 0
    for name, block in (("Node head", NODE_HEAD), ("Bun head", BUN_HEAD), ("body", EXAMPLE_BODY)):
        ok = block in text
        print(f"{'PASS' if ok else 'FAIL'} doc {name}")
        failed += not ok
    return failed


def stop_leftovers(serial):
    run = uuid.uuid4().hex[:8]
    base.run_script(serial, "stop.js", STOP_LEFTOVERS.replace("@EMIT@", base.EMIT_JS).replace("@RUN@", run), run, 15)


def bun_grants(serial, state, value):
    arguments = argparse.Namespace(serial=serial, state=state, set=value, only=BUN_GRANTS)
    base.grant(arguments)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial")
    parser.add_argument("--state", type=Path, help="private directory that keeps the Bun grants to restore")
    parser.add_argument("--cases", default="example,details,leave")
    parser.add_argument("--runtimes", default="node,bun")
    parser.add_argument("--report", type=Path)
    parser.add_argument("--doc", nargs="?", const="", help="only compare the example with this page (default: the sibling documentation checkout)")
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="backslashreplace")
    if args.doc is not None:
        root = Path(os.environ.get("AUTOJS6_DOCUMENTATION_ROOT") or HERE.parents[2] / "AutoJs6-Documentation")
        return 1 if check_doc(Path(args.doc) if args.doc else root / "api" / "imgui.md") else 0
    if not args.serial:
        parser.error("--serial is required")
    runtimes = args.runtimes.split(",")
    if "bun" in runtimes and not args.state:
        parser.error("--state is required with the bun runtime")
    report = {}

    def check(name, ok, detail=""):
        report[name] = {"ok": bool(ok), "detail": detail}
        print(f"{'PASS' if ok else 'FAIL'} {name}{': ' + detail if detail else ''}")

    try:
        if "bun" in runtimes:
            bun_grants(args.serial, args.state, "on")
        for runtime in runtimes:
            for case in args.cases.split(","):
                CASES[case](runtime, args.serial, check)
    finally:
        stop_leftovers(args.serial)
        if "bun" in runtimes:
            bun_grants(args.serial, args.state, "restore")
    if args.report:
        args.report.write_text(json.dumps(report, indent=1, ensure_ascii=False) + chr(10), encoding="utf-8")
    failed = sum(not item["ok"] for item in report.values())
    print(f"{len(report) - failed} / {len(report)}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
