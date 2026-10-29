#!/usr/bin/env python3
"""Device comparison of the Bun app / shell / shizuku family (api-coverage roadmap P4.1a): host.app, host.shell,
host.shizuku and ShellProcess of the Bun host client against Rhino.

The cases are those of the Node runtime (AutoJs6-Plugin-NodeJs-Runtime tools/nodejs/api/app-shell/cases.js, P3.1a):
[id, Rhino expression, runtime expression] or [id, null, runtime expression, expected]; the runtime expressions see
app, shell, shizuku and APK (a copy of an installed APK), here host.app / host.shell / host.shizuku. EXTRA_CASES adds
the dual-app helpers with a package that does not exist, SetScreenMetrics and Screencap. Results depend on the device
(Root, Shizuku, other users), so Rhino and Bun run in one session and are compared there. The run, console and package
helpers are those of device_bun_js_tools.py. A Bun case that does not settle in 30 s is reported as CaseTimeout. Only
counts, failing case ids and short values are printed.

Phases (all take --serial; record / install / restore / verify / grant also --state, a private directory):
  record / install --apk P=PATH / restore / verify   as in device_bun_js_tools.py
  grant --set on|off|restore --only G ...           set (or restore) host Bun grants
  compare [--modes main,worker] [--report FILE]     Rhino, then a Bun main script and a Bun Worker, with the grants
                                                     app.control, shell.control, shell.root and shell.shizuku on
  denied [--modes main,worker] [--report FILE]      Bun only, with shell.control / shell.root / shell.shizuku off and
                                                     app.control on: the shell-backed entries reject NOT_GRANTED with
                                                     missingCapabilities before the host; app queries still answer

Typical run: record, install the candidate host and Bun APKs, grant --set on --only app.control shell.control
shell.root shell.shizuku, compare, grant --set off --only shell.control shell.root shell.shizuku, denied,
grant --set restore --only (the four grants), restore. No dialog is answered here; pushed scripts and the APK copy are
removed after each phase.
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

base.DEVICE_DIR = "/sdcard/Download/aj6-bun-app-shell"
base.GRANTS_FILE = "bun-app-shell-grants.json"
# Inside the run directory: the host answers app.getApkInfo of Node / Bun only for files below the script's working
# directory (NodeBridgeProtocol.resolveScopedAppFile); the directory is removed after each run.
APK = base.DEVICE_DIR + "/sample.apk"
APK_PACKAGE = "com.android.settings"
NODE_CASES = "tools/nodejs/api/app-shell/cases.js"
# Members without a value to compare whose Rhino calls are safe on any device: a package that does not exist, the
# design size of Tap / Swipe, a screenshot into the run directory (written only with Root or Shizuku).
EXTRA_CASES = """
APP_SHELL_CASES = APP_SHELL_CASES.concat([
    ["dual.launchDual", "app.launchDual('p41a.none')", "app.launchDual('p41a.none')"],
    ["dual.launchDualPackage", "app.launchDualPackage('p41a.none')", "app.launchDualPackage('p41a.none')"],
    ["dual.launchDualApp", "app.launchDualApp('p41a none')", "app.launchDualApp('p41a none')"],
    ["dual.launchDualAppDetailsSettings", "app.launchDualAppDetailsSettings('p41a.none')", "app.launchDualAppDetailsSettings('p41a.none')"],
    ["dual.openDualAppSetting", "app.openDualAppSetting('p41a.none')", "app.openDualAppSetting('p41a.none')"],
    ["dual.uninstallDual", "app.uninstallDual('p41a.none')", "app.uninstallDual('p41a.none')"],
    ["dual.isDualInstalled.none", "app.isDualInstalled('p41a.none')", "app.isDualInstalled('p41a.none')"],
    ["key.setScreenMetrics", "SetScreenMetrics(1080, 2400)", "shell.SetScreenMetrics(1080, 2400)"],
    ["key.screencap", "Screencap('%(dir)s/p41a.png')", "shell.Screencap('%(dir)s/p41a.png')"],
    ["shizuku.currentActivity", "String(shizuku.currentActivity())", "shizuku.currentActivity()"],
    ["shell.currentActivity", "String(shell.currentActivity())", "shell.currentActivity()"]
]);
""" % {"dir": base.DEVICE_DIR}
GRANTS = ["app.control", "shell.control", "shell.root", "shell.shizuku"]
SHELL_GRANTS = GRANTS[1:]
# The denied run: [id, Bun expression, expected] where expected is {missing: [...]} (NOT_GRANTED before the host) or
# {ok: true} (any value).
DENIED_CASES = [
    ["app.kill", "app.kill('com.android.settings')", {"missing": ["shell.control"]}],
    ["app.isDualInstalled", "app.isDualInstalled('com.android.settings')", {"missing": ["shell.control"]}],
    ["app.launchDual", "app.launchDual('p41a.none')", {"missing": ["shell.control"]}],
    ["shell.execCommand", "shell.execCommand('echo p41a')", {"missing": ["shell.control"]}],
    ["shell.execCommand.root", "shell.execCommand('echo p41a', true)", {"missing": ["shell.control", "shell.root"]}],
    ["shell.currentPackage", "shell.currentPackage()", {"missing": ["shell.control"]}],
    ["shell.isRootAvailable", "shell.isRootAvailable()", {"missing": ["shell.control"]}],
    ["shell.Home", "shell.Home()", {"missing": ["shell.control", "shell.root"]}],
    ["shizuku.execCommand", "shizuku.execCommand('echo p41a')", {"missing": ["shell.control", "shell.shizuku"]}],
    ["shizuku.state", "shizuku.state", {"missing": ["shell.control"]}],
    ["process.spawn", "shell.spawn('echo p41a').ready", {"missing": ["shell.control"]}],
    ["app.getAppByAlias", "app.getAppByAlias('QQ').then(a => a && a.packageName)", {"ok": True}],
    ["app.getInstalledPackages", "app.getInstalledPackages().then(l => l.length > 0)", {"ok": True}],
    ["shell.getCommand", "shell.getCommand('ls', true)", {"ok": True}],
]

RHINO_JS = """
function aj6JavaInfo(v) {
    if (v === null || v === undefined || typeof v !== 'object') return null;
    try { if (v instanceof java.lang.Object) return {className: String(v.getClass().getName())}; } catch (e) { return null; }
    return null;
}
for (var i = 0; i < APP_SHELL_CASES.length; i++) {
    var source = APP_SHELL_CASES[i][1], result;
    if (source === null) {
        result = {skip: 1};
    } else {
        try { result = {ok: jsToolsEncode(eval('(' + source + ')'), aj6JavaInfo)}; } catch (e) { result = {th: jsToolsThrown(e)}; }
    }
    aj6Emit(AJ6_RUN, i, result);
}
aj6Emit(AJ6_RUN, -1, {done: APP_SHELL_CASES.length, sdk: android.os.Build.VERSION.SDK_INT});
"""
# Shared by the main script and the Worker: the cases evaluated with the host client namespaces under the names the
# Node expressions use. A rejection keeps its code and missingCapabilities.
BUN_RUNNER_JS = """
function aj6Env(host) {
    return {app: host.app, shell: host.shell, shizuku: host.shizuku, APK: AJ6_APK, appShellResult, appShellState};
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
                missing: e && Array.isArray(e.missingCapabilities) ? [...e.missingCapabilities] : null};
        }
        emit(i, result);
        if ((i + 1) % 20 === 0) await new Promise(resolve => setTimeout(resolve, 30));
    }
}
"""


def encoder(args):
    """jsToolsEncode / jsToolsThrown of the shared JS tools cases (Rhino and Bun)."""
    text = (args.bun / base.CASES).read_text(encoding="utf-8")
    return text[text.index("function jsToolsEncode("):text.index("if (typeof module ===")]


def cases_source(args):
    text = (args.node / NODE_CASES).read_text(encoding="utf-8")
    return text[:text.index('if (typeof module === "object"')] + EXTRA_CASES


def case_list(args):
    """[id, Rhino expression or None, Bun expression, expected or None] of every case."""
    script = ("const vm = require('node:vm'), c = {}; vm.runInNewContext(require('node:fs').readFileSync(0, 'utf8'), c);"
              "process.stdout.write(JSON.stringify(c.APP_SHELL_CASES.map(x => [x[0], x[1], x[2], x.length > 3 ? x[3] : null])))")
    out = subprocess.run(["node", "-e", script], input=cases_source(args), capture_output=True, text=True, encoding="utf-8", check=True)
    return json.loads(out.stdout)


def bun_script(args, sources, modes, run):
    common = (f"const AJ6_APK = {json.dumps(APK)}, AJ6_CASES = {json.dumps(sources)}, AJ6_CASE_TIMEOUT_MS = 30000;\n"
              + cases_source(args) + encoder(args) + base.EMIT_JS + BUN_RUNNER_JS)
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
    """'pass' or the reason of a failure; thrown errors match by name (a Rhino JavaException by its simple name)."""
    if "th" in expected or "th" in actual:
        if "th" not in expected:
            return f"expected {json.dumps(expected.get('ok'))[:100]}, threw {actual['th']['name']} {actual.get('code')}: {actual['th']['message'][:100]}"
        name = base.error_name(expected["th"]["name"])
        if "th" not in actual:
            return f"expected {name}, got {json.dumps(actual.get('ok'))[:100]}"
        if name == actual["th"]["name"]:
            return "pass"
        # A Java exception of the host (Rhino JavaException) reaches Bun as HostCallError: the client has no Java classes.
        if expected["th"]["name"].startswith("JavaException ") and actual["th"]["name"] == "HostCallError":
            return "pass (Java exception as HostCallError)"
        return f"expected {name}, got {actual['th']['name']}: {actual['th']['message'][:100]}"
    return "pass" if base.same(expected["ok"], actual["ok"]) else \
        f"expected {json.dumps(expected['ok'])[:120]}, got {json.dumps(actual['ok'])[:120]}"


def run_script(args, name, text, run):
    """base.run_script with a copy of an installed APK at APK (app.getApkInfo); run_script removes the directory."""
    out = base.sample.adb(args.serial, "shell", f"pm path {APK_PACKAGE}", check=False)
    source = next(line[len("package:"):].strip() for line in out.splitlines() if line.startswith("package:"))
    base.sample.adb(args.serial, "shell", f"mkdir -p {base.DEVICE_DIR} && cp {source} {APK}")
    return base.run_script(args.serial, name, text, run, args.wait)


def run_bun(args, sources):
    modes = args.modes.split(",")
    run = uuid.uuid4().hex[:8]
    results = run_script(args, f"aj6_app_shell_bun_{run}.js", bun_script(args, sources, modes, run), run)
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
    script = f"var AJ6_RUN = '{run}';\nvar APK = {json.dumps(APK)};\n" + cases_source(args) + encoder(args) + base.EMIT_JS + RHINO_JS
    rhino = run_script(args, f"aj6_app_shell_rhino_{run}.js", script, run)
    modes, results = run_bun(args, [case[2] for case in table])
    host = base.package_state(args.serial, base.HOST) or {}
    bun = base.package_state(args.serial, base.sample.BUN) or {}
    model = base.sample.sh(args.serial, "getprop ro.product.model")
    device = {"model": model, "sdk": rhino.get(-1, {}).get("sdk"), "host": host.get("versionCode"), "bun": bun.get("versionCode")}
    ids = [case[0] for case in table]
    for case_id in ("root.available", "shizuku.state", "app.isDualInstalled"):
        device[case_id] = rhino.get(ids.index(case_id))
    print(f"rhino: {len([i for i in rhino if i >= 0])} / {len(table)} cases, host {device['host']}, bun {device['bun']}, {model} API {device['sdk']}")
    print("device: " + ", ".join(f"{k} {json.dumps(device[k])}" for k in ("root.available", "shizuku.state", "app.isDualInstalled")))
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
        print(f"bun {mode}: pass {counts['pass']} / fail {counts['fail']} / missing {counts['missing']} (Java exception as HostCallError {counts['javaException']})")
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
            else:
                verdict = "pass" if "ok" in actual else f"threw {actual['th']['name']} {actual.get('code')}: {actual['th']['message'][:100]}"
            counts["pass" if verdict == "pass" else "fail"] += 1
            if verdict != "pass":
                failures[case_id] = verdict
        failed += counts["fail"]
        report["modes"][mode] = {"counts": counts, "failures": failures}
        print(f"bun {mode} (denied {','.join(SHELL_GRANTS)}): pass {counts['pass']} / fail {counts['fail']}")
        for case_id, verdict in list(failures.items())[:args.show]:
            print(f"  {case_id}: {verdict}")
    finish(args, report, failed)


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
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="backslashreplace")
    if args.phase in ("record", "install", "restore", "verify", "grant") and not args.state:
        parser.error("--state is required")
    if args.phase == "grant" and (not args.set or not args.only):
        parser.error("grant needs --set and --only")
    {"record": base.record, "install": base.install, "restore": base.restore, "verify": base.verify, "grant": base.grant,
     "compare": compare, "denied": denied}[args.phase](args)


if __name__ == "__main__":
    main()
