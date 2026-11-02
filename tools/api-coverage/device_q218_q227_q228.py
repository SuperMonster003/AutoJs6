#!/usr/bin/env python3
"""Three fixes of the JS runtimes on a device (api-coverage Q218 (a), Q227 (a), Q228 (b)), each in Node and in Bun.

  broadcast  events.broadcast between a Rhino script and the main script keeps objects and arrays both ways, and an argument
             without JSON (a cycle, a function) arrives as null while the sender goes on
  terminal   a terminal waitFor followed at once by a write sees a fast echo: --rounds rounds (8 by default) in one session
  storage    shared storage of the plugin process, in three steps with the maintainer's hands between the second and the third:
             deny      remembers the appop of the plugin and takes the access away
             prompt    --mode files | logged | uncaught: the script gets the refusal unchanged and AutoJs6 opens one prompt
                       (the start of the prompt is counted in logcat); it stays open for the maintainer unless --close
                       removes its task, which leaves the access as it is
             allowed   after the maintainer granted the access: the same write succeeds, without a prompt
             restore   puts the appop of the plugin back and removes the test file

Only one line per check is printed. No dialog is answered here. record / install / restore-packages / verify keep and put back
the host, Node and Bun packages (the 3-Shell Terminal plugin is the caller's); Bun's grants are set for a run and restored
by it. Usage (all with --serial SERIAL --state DIR, a private directory):

  device_q218_q227_q228.py record
  device_q218_q227_q228.py install --apk PACKAGE=PATH [...]
  device_q218_q227_q228.py broadcast [--runtimes node,bun] [--report FILE]
  device_q218_q227_q228.py terminal [--runtimes node,bun] [--rounds 8] [--report FILE]
  device_q218_q227_q228.py storage deny|prompt|allowed|restore --runtimes node [--mode files] [--close]
  device_q218_q227_q228.py restore-packages | verify
"""
import argparse
import json
from pathlib import Path
import re
import sys
import time
import uuid

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import device_bun_js_tools as base  # noqa: E402

sample = base.sample
base.DEVICE_DIR = "/sdcard/Download/aj6-q218"
base.PACKAGES = [base.HOST, sample.NODE, sample.BUN]

BUN_GRANTS = ["engines.control", "engines.exec", "events.observe", "terminal.control", "files.control"]
NODE_PERMISSIONS = ["engines", "engines.exec", "events", "terminal", "files", "files.write"]
PLUGIN = {"node": sample.NODE, "bun": sample.BUN}
TEST_FILE = "/sdcard/aj6-q228.txt"
PROMPT = "PluginStorageAccessActivity"
PROMPT_SECONDS = 8

HEAD = {
    "node": """"nodejs";
let engines = require('engines');
let events = require('autojs6:events');
let terminal = require('autojs6:terminal');
let files = require('autojs6:files');
let fs = require('node:fs');
""",
    "bun": """"bun";
let { createHostClient } = await import(process.env.AUTOJS6_HOST_CLIENT_MODULE);
let { engines, events, terminal } = await createHostClient();
let fs = await import('node:fs');
""",
}

RHINO_PEER = r""""rhino";
var channel = '@CH@';
var finished = false;
events.broadcast.on(channel + '.to-rhino', function (object, array, text, count, flag, nothing) {
    var report = {
        native: !(object instanceof java.lang.Object) && !(array instanceof java.lang.Object),
        action: String(object.action), nested: object.nested.n, deep: object.list[1].k, isArray: Array.isArray(array), length: array.length,
        second: array[1], third: array[2].three, text: text, count: count, flag: flag, nothing: nothing === null || nothing === undefined,
        keys: Object.keys(object).sort().join(','), stringified: JSON.stringify(object),
    };
    events.broadcast.emit(channel + '.report', JSON.stringify(report));
    finished = true;
});
var cycle = {name: 'cycle'};
cycle.self = cycle;
events.broadcast.emit(channel + '.to-main', {action: 'go', nested: {n: 2}, list: [1, {k: 2}], skipped: function () {}, gone: undefined, nothing: null},
    [1, 'two', {three: 3}, function () {}, undefined], 'text', 2.5, true, null);
events.broadcast.emit(channel + '.unserializable', cycle, function () {}, undefined, NaN, 'last');
events.broadcast.emit(channel + '.sender-alive');
var waited = 0;
var timer = setInterval(function () {
    waited += 100;
    if (finished || waited > 15000) clearInterval(timer);
}, 100);
"""

BROADCAST = """
const peerSource = @PEER@;
const wait = (name, ms = 20000) => new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('no ' + name)), ms);
    events.broadcast.once(name, (...args) => { clearTimeout(timer); resolve(args); });
});
(async () => {
    const out = {};
    const toMain = wait('@CH@.to-main'), unserializable = wait('@CH@.unserializable'), alive = wait('@CH@.sender-alive');
    await events.broadcast.emit('@CH@.starting');
    await engines.execScript('aj6-q218', peerSource, {timeoutMs: 5000});
    out.toMain = await toMain;
    out.unserializable = await unserializable;
    await alive;
    const report = wait('@CH@.report');
    await events.broadcast.emit('@CH@.to-rhino', {action: 'close', nested: {n: 2}, list: [1, {k: 2}]}, [1, 'two', {three: 3}], 'text', 2.5, true, null);
    out.report = JSON.parse((await report)[0]);
    let cycle = {};
    cycle.self = cycle;
    try { await events.broadcast.emit('@CH@.sender-cycle', cycle); out.senderCycle = 'sent'; } catch (e) { out.senderCycle = String(e && e.name); }
    events.broadcast.removeAllListeners();
    aj6Emit('@RUN@', 0, out);
    aj6Emit('@RUN@', -1, {done: 1});
})().catch(e => { aj6Emit('@RUN@', 0, {failure: String(e)}); aj6Emit('@RUN@', -1, {done: 1}); });
"""

TERMINAL = """
(async () => {
    const s = await terminal.open({cwd: (await terminal.env()).home, show: false, title: 'q227'});
    let hits = 0;
    const misses = [];
    for (let i = 0; i < @ROUNDS@; i++) {
        const waiting = s.waitFor(new RegExp('q227_' + (1000 + i)), 15000).then(() => true, e => String(e && e.code || e));
        await s.write('echo q227_$((1000+' + i + '))\\n');
        const got = await waiting;
        if (got === true) hits++; else misses.push(got);
    }
    await s.close();
    aj6Emit('@RUN@', 0, {hits, misses});
    aj6Emit('@RUN@', -1, {done: 1});
})().catch(e => { aj6Emit('@RUN@', 0, {failure: String(e)}); aj6Emit('@RUN@', -1, {done: 1}); });
"""

# files is Node's in-process module (Bun's runs in the host process): "files" refuses caught, twice, once as false;
# "logged" prints a caught error; "uncaught" leaves it to the runtime. Each reports before it ends.
STORAGE = {
    "files": """
(async () => {
    const tries = [];
    for (let i = 0; i < 3; i++) {
        try { files.write('@FILE@', 'q228'); tries.push('written'); } catch (e) { tries.push(/EACCES|EPERM/.test(String(e)) ? 'refused' : String(e)); }
    }
    tries.push(files.create('@FILE@') ? 'created' : 'false');
    aj6Emit('@RUN@', 0, {tries});
    aj6Emit('@RUN@', -1, {done: 1});
})();
""",
    "logged": """
(async () => {
    const tries = [];
    for (let i = 0; i < 3; i++) {
        try { fs.writeFileSync('@FILE@', 'q228'); tries.push('written'); } catch (e) { tries.push(e.code); console.error(e); }
    }
    aj6Emit('@RUN@', 0, {tries});
    aj6Emit('@RUN@', -1, {done: 1});
})();
""",
    "uncaught": """
aj6Emit('@RUN@', 0, {tries: ['uncaught']});
fs.writeFileSync('@FILE@', 'q228');
""",
}
ALLOWED = """
(async () => {
    fs.writeFileSync('@FILE@', 'q228');
    const text = fs.readFileSync('@FILE@', 'utf8');
    fs.unlinkSync('@FILE@');
    aj6Emit('@RUN@', 0, {text});
    aj6Emit('@RUN@', -1, {done: 1});
})().catch(e => { aj6Emit('@RUN@', 0, {failure: String(e)}); aj6Emit('@RUN@', -1, {done: 1}); });
"""


def launch(runtime, serial, template, wait, **values):
    run, channel = uuid.uuid4().hex[:8], "aj6q" + uuid.uuid4().hex[:6]
    text = HEAD[runtime] + base.EMIT_JS + template
    for name, value in values.items():
        text = text.replace(f"@{name}@", str(value))
    text = text.replace("@RUN@", run).replace("@CH@", channel)
    extra = None
    if runtime == "node":
        extra = {"project.json": json.dumps({"name": "q218", "type": "node", "main": "main.js", "node": {"permissions": NODE_PERMISSIONS}}).encode("utf-8")}
    return base.run_script(serial, "main.js", text, run, wait, extra)


def appop_path(args, runtime):
    return args.state / f"appop-{runtime}.txt"


def appop(serial, runtime):
    out = sample.adb(serial, "shell", f"appops get {PLUGIN[runtime]} MANAGE_EXTERNAL_STORAGE", check=False) or ""
    match = re.search(r"MANAGE_EXTERNAL_STORAGE: (\w+)", out)
    return match.group(1) if match else "default"


def prompts_since(serial, mark):
    """Starts of the prompt activity in logcat since [mark]; the system logs each start once."""
    out = sample.adb(serial, "logcat", "-d", "-T", mark, "-s", "ActivityTaskManager:I", check=False, timeout=30) or ""
    return sum(PROMPT in line and "START" in line for line in out.splitlines())


def phase_broadcast(runtime, args, check):
    results = launch(runtime, args.serial, BROADCAST, 45, PEER=json.dumps(RHINO_PEER))
    out = results.get(0) or {}
    if "failure" in out or not out:
        check(f"{runtime} broadcast ran", False, json.dumps(results, ensure_ascii=False)[:300])
        return
    to_main = out["toMain"]
    check(f"{runtime} Rhino to main object", to_main[0] == {"action": "go", "nested": {"n": 2}, "list": [1, {"k": 2}], "nothing": None}, json.dumps(to_main[0])[:200])
    check(f"{runtime} Rhino to main array", to_main[1] == [1, "two", {"three": 3}, None, None], json.dumps(to_main[1])[:200])
    check(f"{runtime} Rhino to main primitives", to_main[2:] == ["text", 2.5, True, None], json.dumps(to_main[2:]))
    # Rhino turns a bare undefined into the string "undefined" when it calls the Java method emit, for Rhino listeners as well.
    check(f"{runtime} no JSON arrives as null", out["unserializable"] == [None, None, "undefined", None, "last"], json.dumps(out["unserializable"]))
    report = out["report"]
    check(f"{runtime} main to Rhino native values", report.get("native") is True, json.dumps(report)[:300])
    check(f"{runtime} main to Rhino object", (report.get("action"), report.get("nested"), report.get("deep"), report.get("keys")) == ("close", 2, 2, "action,list,nested"))
    check(f"{runtime} main to Rhino array", (report.get("isArray"), report.get("length"), report.get("second"), report.get("third")) == (True, 3, "two", 3))
    check(f"{runtime} main to Rhino primitives", (report.get("text"), report.get("count"), report.get("flag"), report.get("nothing")) == ("text", 2.5, True, True))
    print(f"  {runtime} a main script that sends a cycle: {out.get('senderCycle')}")


def phase_terminal(runtime, args, check):
    results = launch(runtime, args.serial, TERMINAL, 60 + 16 * args.rounds, ROUNDS=args.rounds)
    out = results.get(0) or {}
    check(f"{runtime} waitFor then write", out.get("hits") == args.rounds, f"{out.get('hits')} / {args.rounds} {json.dumps(out.get('misses') or out)[:200]}")


def phase_storage(runtime, args, check):
    serial, package = args.serial, PLUGIN[runtime]
    if args.step == "deny":
        args.state.mkdir(parents=True, exist_ok=True)
        path = appop_path(args, runtime)
        if not path.exists():
            path.write_text(appop(serial, runtime), encoding="utf-8")
        sample.adb(serial, "shell", f"appops set {package} MANAGE_EXTERNAL_STORAGE ignore")
        print(f"{runtime} all files access: {path.read_text(encoding='utf-8')} -> {appop(serial, runtime)}")
    elif args.step == "prompt":
        mark = sample.adb(serial, "shell", "date '+%m-%d %H:%M:%S.000'").strip()
        # "uncaught" never reports its end: the project folder has to outlive the failed run, which reads it back.
        results = launch(runtime, serial, STORAGE[args.mode], 8 if args.mode == "uncaught" else 30, FILE=TEST_FILE)
        out = results.get(0) or {}
        check(f"{runtime} {args.mode} refusal unchanged", "written" not in (out.get("tries") or ["written"]), json.dumps(out)[:200])
        deadline, count = time.time() + PROMPT_SECONDS, 0
        while time.time() < deadline and count == 0:
            time.sleep(0.5)
            count = prompts_since(serial, mark)
        time.sleep(2)
        count = prompts_since(serial, mark)
        check(f"{runtime} {args.mode} one prompt", count == 1, f"{count} starts")
        top = sample.adb(serial, "shell", "dumpsys activity activities", check=False, timeout=30) or ""
        check(f"{runtime} {args.mode} prompt on top", any(PROMPT in line and ("topResumedActivity" in line or "mResumedActivity" in line) for line in top.splitlines()))
        if args.close:
            # The task of this run's prompt goes away as a whole: nothing on it is pressed.
            tasks = sample.adb(serial, "shell", "am stack list", check=False, timeout=30) or ""
            for task in re.findall(r"taskId=(\d+): \S*" + PROMPT, tasks):
                sample.adb(serial, "shell", f"am stack remove {task}", check=False)
    elif args.step == "allowed":
        print(f"{runtime} all files access: {appop(serial, runtime)}")
        mark = sample.adb(serial, "shell", "date '+%m-%d %H:%M:%S.000'").strip()
        results = launch(runtime, serial, ALLOWED, 30, FILE=TEST_FILE)
        out = results.get(0) or {}
        check(f"{runtime} write after the grant", out.get("text") == "q228", json.dumps(out)[:200])
        time.sleep(2)
        check(f"{runtime} no prompt after the grant", prompts_since(serial, mark) == 0)
    elif args.step == "restore":
        path = appop_path(args, runtime)
        before = path.read_text(encoding="utf-8") if path.exists() else "default"
        sample.adb(serial, "shell", f"appops set {package} MANAGE_EXTERNAL_STORAGE {before}")
        sample.adb(serial, "shell", f"rm -f {TEST_FILE}", check=False)
        path.unlink(missing_ok=True)
        print(f"{runtime} all files access restored: {appop(serial, runtime)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("phase", choices=["record", "install", "broadcast", "terminal", "storage", "restore-packages", "verify"])
    parser.add_argument("step", nargs="?", choices=["deny", "prompt", "allowed", "restore"])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--apk", action="append", default=[])
    parser.add_argument("--runtimes", default="node,bun")
    parser.add_argument("--rounds", type=int, default=8)
    parser.add_argument("--mode", choices=sorted(STORAGE), default="files")
    parser.add_argument("--close", action="store_true", help="storage prompt: remove the task of the prompt afterwards, for the next mode")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="backslashreplace")
    if args.phase == "record":
        return base.record(args)
    if args.phase == "install":
        return base.install(args)
    if args.phase == "restore-packages":
        return base.restore(args)
    if args.phase == "verify":
        return base.verify(args)
    report = {}

    def check(name, ok, detail=""):
        report[name] = {"ok": bool(ok), "detail": detail}
        print(f"{'PASS' if ok else 'FAIL'} {name}{': ' + detail if detail else ''}")

    runtimes = args.runtimes.split(",")
    grants = "bun" in runtimes and args.phase != "storage"
    try:
        if grants:
            base.grant(argparse.Namespace(serial=args.serial, state=args.state, set="on", only=BUN_GRANTS))
        phase = {"broadcast": phase_broadcast, "terminal": phase_terminal, "storage": phase_storage}[args.phase]
        for runtime in runtimes:
            phase(runtime, args, check)
    finally:
        if grants:
            base.grant(argparse.Namespace(serial=args.serial, state=args.state, set="restore", only=BUN_GRANTS))
    if args.report:
        args.report.write_text(json.dumps(report, indent=1, ensure_ascii=False) + chr(10), encoding="utf-8")
    failed = sum(not item["ok"] for item in report.values())
    if report:
        print(f"{len(report) - failed} / {len(report)}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
