#!/usr/bin/env python3
"""Device comparison of the Bun automation family (api-coverage roadmap P4.4): host.automator, host.auto, host.keys,
host.threads, host.continuation, host.timers, host.yolo, host.selector, host.ocr, host.qrcode, host.events and the
family's globals on host.global against Rhino.

The cases are those of the Node runtime (AutoJs6-Plugin-NodeJs-Runtime tools/nodejs/api/automation/cases.js, P3.4):
[id, Rhino expression, runtime expression] or [id, null, runtime expression, expected]; the runtime expressions see
automator, auto, threads, continuation, timers, yolo, selector, qrcode, keys, ocr, events and system (here the host client
namespaces of the same names, system being host.global, which carries sleep / wait). Before the cases the Bun script
installs host.global into globalThis (Q184 (a)), which the case global.installed checks. A case whose id ends with "~"
waits for an event. The host's accessibility service is expected to run (the cases read its state and windows); compare
checks it first and stops without it. Rhino and Bun run in one session and are compared there. A Bun case that does not
settle in 30 s is reported as CaseTimeout. Only counts, failing case ids and short values are printed. The run, console
and package helpers are those of device_bun_js_tools.py.

Phases (all take --serial; record / install / restore / verify / grant also --state, a private directory):
  record / install --apk P=PATH / restore / verify   as in device_bun_js_tools.py
  grant --set on|off|restore --only G ...           set (or restore) host Bun grants
  compare [--modes main,worker] [--report FILE]     Rhino, then a Bun main script and a Bun Worker, with the grants of
                                                     GRANTS on
  denied [--modes main,worker] [--report FILE]      Bun only, with QUERY_GRANTS on and the others of GRANTS off: the
                                                     entries that need a grant that is off reject NOT_GRANTED with
                                                     missingCapabilities before the host (Q181 (a) - Q183 (a)), the
                                                     local members still answer
  timeout [--report FILE]                           with the host's accessibility service off: auto.waitFor and
                                                     automator.waitForService end the whole execution in Rhino and in a
                                                     Bun main script (Q179 (b)): nothing after the wait runs

Typical run: record, install the candidate host and Bun APKs, grant --set on --only (GRANTS), compare, grant --set off
--only (GRANTS without QUERY_GRANTS), denied, grant --set restore --only (GRANTS), restore. No dialog is answered here
(the accessibility service is enabled by the maintainer); pushed scripts are removed after each phase.
"""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import uuid

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import device_bun_js_tools as base  # noqa: E402

base.DEVICE_DIR = "/sdcard/Download/aj6-bun-automation"
base.GRANTS_FILE = "bun-automation-grants.json"
NODE_CASES = "tools/nodejs/api/automation/cases.js"
GRANTS = ["accessibility.control", "accessibility.gesture", "accessibility.service", "screen.capture", "events.observe", "events.key",
          "events.touch", "events.key.intercept", "ocr.control", "barcode.control", "yolo.control", "flow.control"]
QUERY_GRANTS = ["events.observe"]
# The denied run: [id, Bun expression, expected] where expected is {missing: [...]} (NOT_GRANTED before the host, the
# missing grants compared as a set), {notGranted: grant} (NOT_GRANTED in the client's call layer, which names the grant in
# error.capability: the selector functions over flow's wait), {ok: true} (any value) or {equals: value}.
DENIED_CASES = [
    ["automator.click", "automator.click('p44_none_text')", {"missing": ["accessibility.control"]}],
    ["global.back", "system.back()", {"missing": ["accessibility.control"]}],
    ["auto.isRunning", "auto.isRunning()", {"missing": ["accessibility.control"]}],
    ["auto.dump", "auto.dump()", {"missing": ["accessibility.control"]}],
    ["automator.press", "automator.press(-10, -10, 1)", {"missing": ["accessibility.control", "accessibility.gesture"]}],
    ["auto.clearCache", "auto.clearCache()", {"missing": ["accessibility.control", "accessibility.service"]}],
    ["automator.captureScreen", "automator.captureScreen()", {"missing": ["accessibility.control", "screen.capture"]}],
    ["events.observeTouch", "events.observeTouch()", {"missing": ["events.touch"]}],
    ["events.setKeyInterceptionEnabled", "events.setKeyInterceptionEnabled('volume_up', true)", {"missing": ["events.key", "events.key.intercept"]}],
    ["ocr.recognizeText", "ocr.recognizeText({id: 'p44', __autojs6ImageHandle: 'p44'})", {"missing": ["ocr.control"]}],
    ["ocr.paddle.detect", "ocr.paddle.detect({id: 'p44', __autojs6ImageHandle: 'p44'})", {"missing": ["ocr.control"]}],
    ["qrcode.recognizeText", "qrcode.recognizeText({id: 'p44', __autojs6ImageHandle: 'p44'})", {"missing": ["barcode.control"]}],
    ["yolo.load", "yolo.load('/sdcard/Download/p44-none', {component: 'p44.none/p44.None'})", {"missing": ["yolo.control"]}],
    # The selector functions probe with flow's wait, which reads the nodes over accessibility.control (host b23220775).
    ["selector.pickup", "selector.pickup('p44_none_text')", {"notGranted": "accessibility.control"}],
    # Local members answer without grants.
    ["keys.codes", "[keys.home, keys.BACK, keys.volume_down]", {"equals": [3, 4, 25]}],
    ["threads.atomic", "threads.atomic(1).incrementAndGet()", {"equals": 2}],
    ["continuation.enabled", "continuation.enabled", {"equals": False}],
    ["timers.keepAlive", "(() => { const id = timers.keepAlive(); clearInterval(id); return typeof id; })()", {"equals": "number"}],
    ["events.emitter", "(() => { const e = events.emitter(); let v = 0; e.on('p44', x => { v = x; }); e.emit('p44', 7); return v; })()", {"equals": 7}],
    ["global.sleep", "system.sleep(10).then(() => 'ok')", {"equals": "ok"}],
]

RHINO_JS = """
function aj6JavaInfo(v) {
    if (v === null || v === undefined || typeof v !== 'object') return null;
    try { if (v instanceof java.lang.Object) return {className: String(v.getClass().getName())}; } catch (e) { return null; }
    return null;
}
var aj6Pending = 1;
function aj6Finish() {
    if (--aj6Pending === 0) aj6Emit(AJ6_RUN, -1, {done: AUTOMATION_CASES.length, sdk: android.os.Build.VERSION.SDK_INT});
}
for (var i = 0; i < AUTOMATION_CASES.length; i++) {
    var id = AUTOMATION_CASES[i][0], source = AUTOMATION_CASES[i][1], result;
    if (source === null) {
        aj6Emit(AJ6_RUN, i, {skip: 1});
    } else if (id.charAt(id.length - 1) === '~') {
        // Waits for an event: done(value) once, or a timeout after 10 s.
        aj6Pending++;
        (function (index, source) {
            var settled = false, timer;
            var settle = function (result) {
                if (settled) return;
                settled = true;
                clearTimeout(timer);
                aj6Emit(AJ6_RUN, index, result);
                aj6Finish();
            };
            timer = setTimeout(function () { settle({th: {name: 'Error', message: 'no event in 10 s'}}); }, 10000);
            try { eval('(' + source + ')')(function (value) { settle({ok: jsToolsEncode(value, aj6JavaInfo)}); }); } catch (e) { settle({th: jsToolsThrown(e)}); }
        })(i, source);
    } else {
        try { result = {ok: jsToolsEncode(eval('(' + source + ')'), aj6JavaInfo)}; } catch (e) { result = {th: jsToolsThrown(e)}; }
        aj6Emit(AJ6_RUN, i, result);
    }
}
aj6Finish();
"""
# Shared by the main script and the Worker: the cases evaluated with the host client namespaces under the names the
# Node expressions use, after host.global.install(globalThis). A rejection or throw keeps its code and missingCapabilities.
BUN_RUNNER_JS = """
function aj6Env(host) {
    host.global.install(globalThis);
    return {automator: host.automator, auto: host.auto, threads: host.threads, continuation: host.continuation, timers: host.timers,
        yolo: host.yolo, selector: host.selector, qrcode: host.qrcode, keys: host.keys, ocr: host.ocr, events: host.events,
        system: host.global};
}
async function aj6Run(env, cases, emit) {
    const names = Object.keys(env), values = names.map(name => env[name]);
    for (let i = 0; i < cases.length; i++) {
        let result;
        try {
            let value = new Function(...names, 'return (' + cases[i] + ');')(...values), timer;
            if (value && typeof value.then === 'function') {
                value = await Promise.race([value, new Promise((_, reject) => { timer = setTimeout(() => reject(Object.assign(
                    new Error('the case did not settle in ' + AJ6_CASE_TIMEOUT_MS + ' ms'), {name: 'CaseTimeout'})), AJ6_CASE_TIMEOUT_MS); })])
                    .finally(() => clearTimeout(timer));
            }
            result = {ok: jsToolsEncode(value, null)};
        } catch (e) {
            result = {th: jsToolsThrown(e), code: e && e.code ? String(e.code) : null,
                missing: e && Array.isArray(e.missingCapabilities) ? [...e.missingCapabilities] : null,
                capability: e && typeof e.capability === 'string' ? e.capability : null};
        }
        emit(i, result);
    }
}
"""
# The timeout run: index 0 before the wait, 1 after it, 2 from a timer armed before it; only 0 may arrive (Q179 (b)).
TIMEOUT_SECONDS = 12
TIMEOUT_RHINO_JS = """
aj6Emit(AJ6_RUN, 0, {ok: 'before'});
setTimeout(function () { aj6Emit(AJ6_RUN, 2, {ok: 'timer'}); aj6Emit(AJ6_RUN, -1, {done: 1}); }, 6000);
AJ6_WAIT;
aj6Emit(AJ6_RUN, 1, {ok: 'after'});
aj6Emit(AJ6_RUN, -1, {done: 1});
"""
TIMEOUT_BUN_JS = """
(async () => {
    const {createHostClient} = await import(process.env.AUTOJS6_HOST_CLIENT_MODULE);
    const host = await createHostClient();
    aj6Emit(AJ6_RUN, 0, {ok: 'before'});
    setTimeout(() => { aj6Emit(AJ6_RUN, 2, {ok: 'timer'}); aj6Emit(AJ6_RUN, -1, {done: 1}); }, 6000);
    await host.AJ6_WAIT;
    aj6Emit(AJ6_RUN, 1, {ok: 'after'});
    aj6Emit(AJ6_RUN, -1, {done: 1});
})().catch(e => { aj6Emit(AJ6_RUN, 3, {error: String(e && e.stack || e)}); aj6Emit(AJ6_RUN, -1, {done: 1}); });
"""


def encoder(args):
    """jsToolsEncode / jsToolsThrown of the shared JS tools cases (Rhino and Bun)."""
    text = (args.bun / base.CASES).read_text(encoding="utf-8")
    return text[text.index("function jsToolsEncode("):text.index("if (typeof module ===")]


def cases_source(args):
    text = (args.node / NODE_CASES).read_text(encoding="utf-8")
    return text[:text.index('if (typeof module === "object"')]


def case_list(args):
    """[id, Rhino expression or None, Bun expression, expected or None] of every case."""
    script = ("const vm = require('node:vm'), c = {}; vm.runInNewContext(require('node:fs').readFileSync(0, 'utf8'), c);"
              "process.stdout.write(JSON.stringify(c.AUTOMATION_CASES.map(x => [x[0], x[1], x[2], x.length > 3 ? x[3] : null])))")
    out = subprocess.run(["node", "-e", script], input=cases_source(args), capture_output=True, text=True, encoding="utf-8", check=True)
    return json.loads(out.stdout)


def bun_script(args, sources, modes, run):
    common = (f"const AJ6_CASES = {json.dumps(sources)}, AJ6_CASE_TIMEOUT_MS = 30000;\n"
              + encoder(args) + base.EMIT_JS + BUN_RUNNER_JS)
    worker = common + """
self.onmessage = async () => {
    try {
        const {createHostClient} = await import(process.env.AUTOJS6_HOST_CLIENT_MODULE);
        await aj6Run(aj6Env(await createHostClient()), AJ6_CASES, (i, r) => postMessage([i, r]));
    } catch (e) {
        postMessage([-2, String(e && e.stack || e)]);
    }
    postMessage([-1, null]);
};
"""
    return ('"bun";\n' + f"const AJ6_RUN = '{run}', AJ6_MODES = {json.dumps(modes)};\n" + common + """
(async () => {
    try {
        const {createHostClient} = await import(process.env.AUTOJS6_HOST_CLIENT_MODULE);
        if (AJ6_MODES.includes('main')) await aj6Run(aj6Env(await createHostClient()), AJ6_CASES, (i, r) => aj6Emit(AJ6_RUN, i, r));
        if (AJ6_MODES.includes('worker')) {
            await new Promise(resolve => {
                const w = new Worker(URL.createObjectURL(new Blob([%s], {type: 'application/javascript'})));
                w.onmessage = event => {
                    const [i, r] = event.data;
                    if (i === -1) { w.terminate(); resolve(); }
                    else if (i === -2) aj6Emit(AJ6_RUN, -2, {error: r});
                    else aj6Emit(AJ6_RUN, 100000 + i, r);
                };
                w.onerror = event => { aj6Emit(AJ6_RUN, -2, {error: String(event.message)}); resolve(); };
                w.postMessage(1);
            });
        }
    } catch (e) {
        aj6Emit(AJ6_RUN, -2, {error: String(e && e.stack || e)});
    }
    aj6Emit(AJ6_RUN, -1, {done: AJ6_CASES.length});
})();
""" % json.dumps(worker))


def compare_values(expected, actual):
    """'pass' or the reason of a failure; thrown errors match by name, a Rhino Java exception any rejection of the
    same host call (the client has no Java classes), as the Node runtime's device_automation.py compares them."""
    if "th" in expected or "th" in actual:
        if "th" not in expected:
            return f"expected {json.dumps(expected.get('ok'))[:100]}, threw {actual['th']['name']} {actual.get('code')}: {actual['th']['message'][:100]}"
        name = base.error_name(expected["th"]["name"])
        if "th" not in actual:
            return f"expected {name}, got {json.dumps(actual.get('ok'))[:100]}"
        if expected["th"]["name"].startswith("JavaException "):
            return "pass (Java exception as a rejection)"
        return "pass" if name == actual["th"]["name"] else f"expected {name}, got {actual['th']['name']}: {actual['th']['message'][:100]}"
    return "pass" if base.same(expected["ok"], actual["ok"]) else \
        f"expected {json.dumps(expected['ok'])[:120]}, got {json.dumps(actual['ok'])[:120]}"


def run_bun(args, sources):
    modes = args.modes.split(",")
    run = uuid.uuid4().hex[:8]
    results = base.run_script(args.serial, f"p44_bun_{run}.js", bun_script(args, sources, modes, run), run, args.wait)
    if -2 in results:
        print(f"bun: runner error {results[-2].get('error', '')[:600]}")
    return modes, results


def finish(args, report, failed):
    if args.report:
        Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    sys.exit(1 if failed else 0)


def service_running(serial):
    enabled = base.sample.sh(serial, "settings get secure enabled_accessibility_services") or ""
    return base.HOST + "/" in enabled


def compare(args):
    if not service_running(args.serial):
        print(f"the accessibility service of {base.HOST} is not enabled: ask the maintainer to enable it, then run compare again")
        sys.exit(2)
    table = case_list(args)
    run = uuid.uuid4().hex[:8]
    script = f"var AJ6_RUN = '{run}';\n" + cases_source(args) + encoder(args) + base.EMIT_JS + RHINO_JS
    rhino = base.run_script(args.serial, f"p44_rhino_{run}.js", script, run, args.wait)
    modes, results = run_bun(args, [case[2] for case in table])
    host = base.package_state(args.serial, base.HOST) or {}
    bun = base.package_state(args.serial, base.sample.BUN) or {}
    model = base.sample.sh(args.serial, "getprop ro.product.model")
    device = {"model": model, "sdk": rhino.get(-1, {}).get("sdk"), "host": host.get("versionCode"), "bun": bun.get("versionCode")}
    print(f"rhino: {len([i for i in rhino if i >= 0])} / {len(table)} cases, host {device['host']}, bun {device['bun']}, {model} API {device['sdk']}")
    report, failed = {"device": device, "modes": {}}, 0
    for mode, offset in (("main", 0), ("worker", 100000)):
        if mode not in modes:
            continue
        counts, failures = {"pass": 0, "fail": 0, "missing": 0, "javaException": 0}, {}
        for i, (case_id, source, _, fixed) in enumerate(table):
            actual = results.get(offset + i)
            expected = {"ok": fixed} if source is None else rhino.get(i)
            verdict = "missing" if actual is None else "missing Rhino value" if expected is None else compare_values(expected, actual)
            key = "pass" if verdict.startswith("pass") else "missing" if verdict.startswith("missing") else "fail"
            counts[key] += 1
            counts["javaException"] += 1 if verdict.startswith("pass (") else 0
            if key != "pass":
                failures[case_id] = verdict
        failed += counts["fail"] + counts["missing"]
        report["modes"][mode] = {"counts": counts, "failures": failures}
        print(f"bun {mode}: pass {counts['pass']} / fail {counts['fail']} / missing {counts['missing']} (Java exception as a rejection {counts['javaException']})")
        for case_id, verdict in list(failures.items())[:args.show]:
            print(f"  {case_id}: {verdict}")
    finish(args, report, failed)


def denied(args):
    modes, results = run_bun(args, [case[1] for case in DENIED_CASES])
    report, failed = {"modes": {}}, 0
    for mode, offset in (("main", 0), ("worker", 100000)):
        if mode not in modes:
            continue
        counts, failures = {"pass": 0, "fail": 0}, {}
        for i, (case_id, _, expected) in enumerate(DENIED_CASES):
            actual = results.get(offset + i)
            if actual is None:
                verdict = "missing"
            elif "missing" in expected:
                verdict = "pass" if actual.get("code") == "NOT_GRANTED" and sorted(actual.get("missing") or []) == sorted(expected["missing"]) else \
                    f"expected NOT_GRANTED {expected['missing']}, got {actual.get('code')} {actual.get('missing')} {json.dumps(actual.get('ok'))[:80]}"
            elif "notGranted" in expected:
                verdict = "pass" if actual.get("code") == "NOT_GRANTED" and actual.get("capability") == expected["notGranted"] else                     f"expected NOT_GRANTED of {expected['notGranted']}, got {actual.get('code')} {actual.get('capability')} {json.dumps(actual.get('ok'))[:80]}"
            elif "equals" in expected:
                verdict = "pass" if "ok" in actual and base.same(expected["equals"], actual["ok"]) else \
                    f"expected {json.dumps(expected['equals'])[:100]}, got {json.dumps(actual.get('ok', actual.get('th')))[:100]}"
            else:
                verdict = "pass" if "ok" in actual else f"threw {actual['th']['name']} {actual.get('code')}: {actual['th']['message'][:100]}"
            counts["pass" if verdict == "pass" else "fail"] += 1
            if verdict != "pass":
                failures[case_id] = verdict
        failed += counts["fail"]
        report["modes"][mode] = {"counts": counts, "failures": failures}
        print(f"bun {mode} (only {','.join(QUERY_GRANTS)}): pass {counts['pass']} / fail {counts['fail']}")
        for case_id, verdict in list(failures.items())[:args.show]:
            print(f"  {case_id}: {verdict}")
    finish(args, report, failed)


def timeout(args):
    if service_running(args.serial):
        print(f"the accessibility service of {base.HOST} is enabled: the timeout run needs it off")
        sys.exit(2)
    report, failed = {}, 0
    for wait in ("auto.waitFor(1000)", "automator.waitForService(1000)"):
        for side, template, name in (("rhino", TIMEOUT_RHINO_JS, "p44_rhino"), ("bun", TIMEOUT_BUN_JS, "p44_bun")):
            run = uuid.uuid4().hex[:8]
            body = encoder(args) + base.EMIT_JS + template.replace("AJ6_WAIT", wait)
            script = f"var AJ6_RUN = '{run}';\n" + body if side == "rhino" else f'"bun";\nconst AJ6_RUN = \'{run}\';\n' + body
            results = base.run_script(args.serial, f"{name}_{run}.js", script, run, TIMEOUT_SECONDS)
            verdict = "pass" if sorted(results) == [0] else f"got {sorted(results)} {json.dumps(results.get(3, ''))[:300]}"
            report[f"{side} {wait}"] = verdict
            failed += verdict != "pass"
            print(f"{side} {wait}: {verdict}")
    finish(args, report, failed)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("phase", choices=["record", "install", "restore", "verify", "grant", "compare", "denied", "timeout"])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--state", type=Path, help="private state directory (APK copies, recorded grants)")
    parser.add_argument("--bun", type=Path, default=HERE.parents[2] / "AutoJs6-Plugin-Bun-Runtime", help="Bun runtime checkout")
    parser.add_argument("--node", type=Path, default=HERE.parents[2] / "AutoJs6-Plugin-NodeJs-Runtime", help="Node runtime checkout (cases)")
    parser.add_argument("--modes", default="main,worker", help="compare / denied: main and / or worker")
    parser.add_argument("--report", type=Path, help="compare / denied: write the counts and failures as JSON")
    parser.add_argument("--show", type=int, default=40, help="failing cases printed per mode")
    parser.add_argument("--only", nargs="*", default=[], help="grant: the grants to change")
    parser.add_argument("--set", choices=["on", "off", "restore"], help="grant: the value to set")
    parser.add_argument("--wait", type=int, default=300)
    parser.add_argument("--apk", action="append", default=[], help="install: package=path")
    parser.add_argument("--foreground", action="store_true", help="bring the host's launcher activity to the front before each script")
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="backslashreplace")
    base.FOREGROUND = args.foreground
    if args.phase in ("record", "install", "restore", "verify", "grant") and not args.state:
        parser.error("--state is required")
    if args.phase == "grant" and (not args.set or not args.only):
        parser.error("grant needs --set and --only")
    {"record": base.record, "install": base.install, "restore": base.restore, "verify": base.verify, "grant": base.grant,
     "compare": compare, "denied": denied, "timeout": timeout}[args.phase](args)


if __name__ == "__main__":
    main()
