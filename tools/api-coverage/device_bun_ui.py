#!/usr/bin/env python3
"""Device comparison of the Bun UI family (api-coverage roadmap P4.3): host.ui, host.floaty, host.web, host.terminal,
host.console, host.toast, host.compose and the family's globals against Rhino.

The cases are those of the Node runtime (AutoJs6-Plugin-NodeJs-Runtime tools/nodejs/api/ui-family/cases.js, P3.3):
[id, Rhino expression, runtime expression] or [id, null, runtime expression, expected]; the runtime expressions see ui,
floaty, web, terminal, console, toast and compose (here the host client's namespaces) and the family's globals (here those
of host.global). A case whose id ends with "~" waits for an event. The host is expected to hold the overlay permission and
3-Block Compose / 3-Shell Terminal to be installed. Rhino and Bun run in one session and are compared there. The run,
console and package helpers are those of device_bun_js_tools.py. A Bun case that does not settle in 30 s is reported as
CaseTimeout. Only counts, failing case ids and short values are printed.

Phases (all take --serial; record / install / restore / verify / grant also --state, a private directory):
  record / install --apk P=PATH / restore / verify   as in device_bun_js_tools.py
  grant --set on|off|restore --only G ...           set (or restore) host Bun grants
  compare [--modes main,worker] [--report FILE]     Rhino, then a Bun main script and a Bun Worker, with the grants of
                                                     GRANTS on
  denied [--modes main,worker] [--report FILE]      Bun only, with QUERY_GRANTS on and the others of GRANTS off: the
                                                     entries that need a grant that is off reject NOT_GRANTED with
                                                     missingCapabilities before the host (terminal.control is not implied
                                                     by shell.control, Q3 (a)); floaty windows and their views work with
                                                     floaty.control alone, ByteString stays local

Typical run: record, install the candidate host and Bun APKs, grant --set on --only (GRANTS), compare, grant --set off
--only (GRANTS without QUERY_GRANTS), denied, grant --set restore --only (GRANTS), restore. No dialog is answered here;
pushed scripts are removed after each phase.
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

base.DEVICE_DIR = "/sdcard/Download/aj6-bun-ui"
base.GRANTS_FILE = "bun-ui-grants.json"
NODE_CASES = "tools/nodejs/api/ui-family/cases.js"
GRANTS = ["ui.layout", "ui.compose", "ui.toast", "console.control", "clipboard.control", "floaty.control", "floaty.raw", "floaty.touch",
          "terminal.control", "shell.control"]
QUERY_GRANTS = ["floaty.control", "shell.control"]
# ui.toast predates the dotted grant keys: the host stores it as ui_toast (BunHostCapabilityGrants.KEY_UI_TOAST).
GRANT_KEYS = {"ui.toast": "ui_toast"}
# The family's globals of host.global the runtime expressions use (Node: globalThis).
GLOBALS = ["isUiThread", "toastLog", "toastVerbose", "toastInfo", "toastWarn", "toastError", "toastlog", "toastverbose", "toastinfo",
           "toastwarn", "toasterror", "newWebSocket", "newInjectableWebView", "newInjectableWebClient", "ComposeError"]
# Bun expressions that replace the Node ones: Bun's toasts settle with the ui.toast result ({shown}, Q172 (a), Bun
# docs/host-api.md) where Node's settle with null.
BUN_OVERRIDES = {"toastLog": "(async () => [typeof (await toastLog('p33 ' + 1)) === 'object', typeof toastInfo])()"}
# The denied run: [id, Bun expression, expected] where expected is {missing: [...]} (NOT_GRANTED before the host), {ok: true}
# (any value) or {equals: value}.
DENIED_CASES = [
    ["terminal.state", "terminal.state()", {"missing": ["terminal.control"]}],
    ["terminal.open", "terminal.open({show: false})", {"missing": ["terminal.control"]}],
    ["floaty.rawWindow", "floaty.rawWindow('<frame/>')", {"missing": ["floaty.raw"]}],
    ["floaty.getClip", "floaty.getClip()", {"missing": ["clipboard.control"]}],
    ["console.rawInput", "console.rawInput('p43')", {"missing": ["console.control"]}],
    ["console.build.show", "console.build({title: 'p43'}).show()", {"missing": ["console.control"]}],
    ["toast.dismissAll", "toast.dismissAll()", {"missing": ["ui.toast"]}],
    ["ui.layout", "ui.layout('<frame/>')", {"missing": ["ui.layout"]}],
    ["ui.getStatusBarHeight", "ui.getStatusBarHeight()", {"missing": ["ui.layout"]}],
    ["compose.isAvailable", "compose.isAvailable()", {"missing": ["ui.compose"]}],
    # Q173 (a): floaty.control alone opens a window and reaches its views (ui.layout off).
    ["floaty.window", "(async () => { const w = await floaty.window('<frame><text id=\"p43\" text=\"p43\"/></frame>'); await w.setPosition(12, 34);"
                      " const r = [typeof (await w.x), w.p43 !== null && w.p43 !== undefined, await floaty.hasPermission()]; await w.close();"
                      " return r; })()", {"equals": ["number", True, True]}],
    # ui.inflate and the other view routes ride floaty.control when ui.layout is off, so a WebView for a floaty window inflates.
    ["web.newInjectableWebView", "web.newInjectableWebView().then(view => typeof view.inject)", {"equals": "function"}],
    ["web.ByteString", "web.ByteString.encodeUtf8('p43').hex()", {"equals": "703433"}],
]

RHINO_JS = """
function aj6JavaInfo(v) {
    if (v === null || v === undefined || typeof v !== 'object') return null;
    try { if (v instanceof java.lang.Object) return {className: String(v.getClass().getName())}; } catch (e) { return null; }
    return null;
}
var aj6Pending = 1;
function aj6Finish() {
    if (--aj6Pending === 0) aj6Emit(AJ6_RUN, -1, {done: UI_FAMILY_CASES.length, sdk: android.os.Build.VERSION.SDK_INT});
}
for (var i = 0; i < UI_FAMILY_CASES.length; i++) {
    var id = UI_FAMILY_CASES[i][0], source = UI_FAMILY_CASES[i][1], result;
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
# Shared by the main script and the Worker: the cases evaluated with the host client namespaces and globals under the names
# the Node expressions use. A rejection or throw keeps its code and missingCapabilities (TerminalError wraps NOT_GRANTED as
# its cause; a grant the base client checks itself names it in capability only).
BUN_RUNNER_JS = """
function aj6Env(host) {
    const env = {ui: host.ui, floaty: host.floaty, web: host.web, terminal: host.terminal, console: host.console, toast: host.toast, compose: host.compose};
    for (const name of AJ6_GLOBALS) env[name] = host.global[name];
    return env;
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
            const owner = e && !Array.isArray(e.missingCapabilities) && e.cause && Array.isArray(e.cause.missingCapabilities) ? e.cause : e;
            result = {th: jsToolsThrown(e), code: owner && owner.code ? String(owner.code) : null,
                missing: owner && Array.isArray(owner.missingCapabilities) ? [...owner.missingCapabilities]
                    : owner && owner.code === 'NOT_GRANTED' && owner.capability ? [String(owner.capability)] : null};
        }
        emit(i, result);
    }
}
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
    script = ("const vm = require('node:vm'), c = {}; vm.runInNewContext(require('node:fs').readFileSync(0, 'utf8') + ';this.UI_FAMILY_CASES = UI_FAMILY_CASES;', c);"
              "process.stdout.write(JSON.stringify(c.UI_FAMILY_CASES.map(x => [x[0], x[1], x[2], x.length > 3 ? x[3] : null])))")
    out = subprocess.run(["node", "-e", script], input=cases_source(args), capture_output=True, text=True, encoding="utf-8", check=True)
    return json.loads(out.stdout)


def bun_script(args, sources, modes, run):
    common = (f"const AJ6_CASES = {json.dumps(sources)}, AJ6_GLOBALS = {json.dumps(GLOBALS)}, AJ6_CASE_TIMEOUT_MS = 30000;\n"
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
    """'pass' or the reason of a failure; thrown errors match by name, a Rhino Java exception any rejection of the same
    host call (the client has no Java classes), as device_bun_device_media.py compares them."""
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
    results = base.run_script(args.serial, f"p43_bun_{run}.js", bun_script(args, sources, modes, run), run, args.wait)
    if -2 in results:
        print(f"bun: runner error {results[-2].get('error', '')[:600]}")
    return modes, results


def finish(args, report, failed):
    if args.report:
        Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    sys.exit(1 if failed else 0)


def compare(args):
    table = case_list(args)
    run = uuid.uuid4().hex[:8]
    script = f"var AJ6_RUN = '{run}';\n" + cases_source(args) + encoder(args) + base.EMIT_JS + RHINO_JS
    rhino = base.run_script(args.serial, f"p43_rhino_{run}.js", script, run, args.wait)
    modes, results = run_bun(args, [BUN_OVERRIDES.get(case[0], case[2]) for case in table])
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
                verdict = "pass" if actual.get("code") == "NOT_GRANTED" and actual.get("missing") == expected["missing"] else \
                    f"expected NOT_GRANTED {expected['missing']}, got {actual.get('code')} {actual.get('missing')} {json.dumps(actual.get('ok'))[:80]}"
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


def grant(args):
    args.only = [GRANT_KEYS.get(name, name) for name in args.only]
    base.grant(args)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("phase", choices=["record", "install", "restore", "verify", "grant", "compare", "denied"])
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
    parser.add_argument("--foreground", action="store_true", help="compare / denied / grant: bring the host to the front first")
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="backslashreplace")
    base.FOREGROUND = args.foreground
    if args.phase in ("record", "install", "restore", "verify", "grant") and not args.state:
        parser.error("--state is required")
    if args.phase == "grant" and (not args.set or not args.only):
        parser.error("grant needs --set and --only")
    {"record": base.record, "install": base.install, "restore": base.restore, "verify": base.verify, "grant": grant,
     "compare": compare, "denied": denied}[args.phase](args)


if __name__ == "__main__":
    main()
