#!/usr/bin/env python3
"""Device comparison of the Bun device / sensors / media family (api-coverage roadmap P4.1c): host.device, host.sensors and
host.media against Rhino.

The cases are those of the Node runtime (AutoJs6-Plugin-NodeJs-Runtime tools/nodejs/api/device-media/cases.js, P3.1c):
[id, Rhino expression, runtime expression] or [id, null, runtime expression, expected]; the runtime expressions see
device, sensors and media (here host.device / host.sensors / host.media) and WAV (a silent 3 s WAV file on the device). A
case whose id ends with "~" waits for a sensor event (the accelerometer reports without moving the device). Results depend
on the device (Build fields, sensors, network, permissions), so Rhino and Bun run in one session and are compared there;
identifiers and addresses are compared as hashes. The run, console and package helpers are those of
device_bun_js_tools.py. A Bun case that does not settle in 30 s is reported as CaseTimeout. Only counts, failing case
ids and short values are printed.

Phases (all take --serial; record / install / restore / verify / grant also --state, a private directory):
  record / install --apk P=PATH / restore / verify   as in device_bun_js_tools.py
  grant --set on|off|restore --only G ...           set (or restore) host Bun grants
  compare [--modes main,worker] [--report FILE]     Rhino, then a Bun main script and a Bun Worker, with the grants of
                                                     GRANTS on
  denied [--modes main,worker] [--report FILE]      Bun only, with QUERY_GRANTS on and the others of GRANTS off (device.info
                                                     too): the entries that need a grant that is off reject NOT_GRANTED with
                                                     missingCapabilities before the host, the snapshot members are absent

Typical run: record, install the candidate host and Bun APKs, grant --set on --only (GRANTS), compare, grant --set off
--only (GRANTS without QUERY_GRANTS), denied, grant --set restore --only (GRANTS), restore. No dialog is answered here;
pushed scripts and the WAV file are removed after each phase.
"""
import argparse
import io
import json
from pathlib import Path
import subprocess
import sys
import uuid
import wave

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import device_bun_js_tools as base  # noqa: E402

base.DEVICE_DIR = "/sdcard/Download/aj6-bun-device-media"
base.GRANTS_FILE = "bun-device-media-grants.json"
WAV_NAME = "p41c.wav"
WAV = base.DEVICE_DIR + "/" + WAV_NAME
NODE_CASES = "tools/nodejs/api/device-media/cases.js"
GRANTS = ["device.info", "device.control", "device.power", "device.identifiers", "sensors.control", "media.control", "media.library"]
QUERY_GRANTS = ["device.control"]
# device.info predates the dotted grant keys: the host stores it as device_info (BunHostCapabilityGrants.KEY_DEVICE_INFO).
GRANT_KEYS = {"device.info": "device_info"}
# The denied run: [id, Bun expression, expected] where expected is {missing: [...]} (NOT_GRANTED before the host), {ok: true}
# (any value) or {equals: value}.
DENIED_CASES = [
    ["device.getIMEI", "device.getIMEI()", {"missing": ["device.identifiers"]}],
    ["device.getAndroidId", "device.getAndroidId()", {"missing": ["device.identifiers"]}],
    ["device.imei", "device.imei", {"missing": ["device.identifiers"]}],
    ["device.setPointerLocation", "device.setPointerLocation(false)", {"missing": ["device.power"]}],
    ["device.keepScreenDim", "device.keepScreenDim(500)", {"missing": ["device.power"]}],
    ["sensors.getAvailableSensors", "sensors.getAvailableSensors()", {"missing": ["sensors.control"]}],
    # register checks the grant before the sensor list (Node 74f6dfa8), which is empty here.
    ["sensors.register", "sensors.register('accelerometer')", {"missing": ["sensors.control"]}],
    ["media.playMusic", "media.playMusic(WAV, 0)", {"missing": ["media.control"]}],
    ["media.isMusicPlaying", "media.isMusicPlaying()", {"missing": ["media.control"]}],
    ["media.scanFile", "media.scanFile(WAV)", {"missing": ["media.library"]}],
    # Q151 (a) / Q154 (a): without device.info the snapshot members are absent; the sensor list comes with it.
    ["device.snapshot", "[typeof device.brand, typeof device.width, typeof device.pageSize, typeof device.getMarketName, Object.keys(device.roms),"
                        " sensors.getSensor('accelerometer')]",
     {"equals": ["undefined", "undefined", "undefined", "undefined", ["isBackgroundStartGranted"], None]}],
    ["device.isScreenOff", "device.isScreenOff()", {"ok": True}],
    ["device.roms.isBackgroundStartGranted", "device.roms.isBackgroundStartGranted()", {"ok": True}],
]

RHINO_JS = """
function aj6JavaInfo(v) {
    if (v === null || v === undefined || typeof v !== 'object') return null;
    try { if (v instanceof java.lang.Object) return {className: String(v.getClass().getName())}; } catch (e) { return null; }
    return null;
}
var aj6Pending = 1;
function aj6Finish() {
    if (--aj6Pending === 0) aj6Emit(AJ6_RUN, -1, {done: DEVICE_MEDIA_CASES.length, sdk: android.os.Build.VERSION.SDK_INT});
}
for (var i = 0; i < DEVICE_MEDIA_CASES.length; i++) {
    var id = DEVICE_MEDIA_CASES[i][0], source = DEVICE_MEDIA_CASES[i][1], result;
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
# Node expressions use. A rejection or throw keeps its code and missingCapabilities.
BUN_RUNNER_JS = """
function aj6Env(host) {
    return {device: host.device, sensors: host.sensors, media: host.media, WAV: AJ6_WAV, dmHash, dmCalls, dmContains,
        DM_MANUFACTURERS, DM_BRANDS, DM_ROMS, DM_SENSORS};
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
    }
}
"""


def silent_wav(seconds=3, rate=8000):
    data = io.BytesIO()
    with wave.open(data, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(rate)
        out.writeframes(bytes(2 * rate * seconds))
    return data.getvalue()


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
              "process.stdout.write(JSON.stringify(c.DEVICE_MEDIA_CASES.map(x => [x[0], x[1], x[2], x.length > 3 ? x[3] : null])))")
    out = subprocess.run(["node", "-e", script], input=cases_source(args), capture_output=True, text=True, encoding="utf-8", check=True)
    return json.loads(out.stdout)


def bun_script(args, sources, modes, run):
    common = (f"const AJ6_WAV = {json.dumps(WAV)}, AJ6_CASES = {json.dumps(sources)}, AJ6_CASE_TIMEOUT_MS = 30000;\n"
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
    """'pass' or the reason of a failure; thrown errors match by name, a Rhino Java exception any rejection of the
    same host call (the client has no Java classes), as the Node runtime's device_device_media.py compares them."""
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


def run_script(args, name, text, run):
    """base.run_script with the silent WAV next to the script; run_script removes the directory."""
    return base.run_script(args.serial, name, text, run, args.wait, {WAV_NAME: silent_wav()})


def run_bun(args, sources):
    modes = args.modes.split(",")
    run = uuid.uuid4().hex[:8]
    results = run_script(args, f"p41c_bun_{run}.js", bun_script(args, sources, modes, run), run)
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
    script = f"var AJ6_RUN = '{run}';\nvar WAV = {json.dumps(WAV)};\n" + cases_source(args) + encoder(args) + base.EMIT_JS + RHINO_JS
    rhino = run_script(args, f"p41c_rhino_{run}.js", script, run)
    modes, results = run_bun(args, [case[2] for case in table])
    host = base.package_state(args.serial, base.HOST) or {}
    bun = base.package_state(args.serial, base.sample.BUN) or {}
    model = base.sample.sh(args.serial, "getprop ro.product.model")
    ids = [case[0] for case in table]
    device = {"model": model, "sdk": rhino.get(-1, {}).get("sdk"), "host": host.get("versionCode"), "bun": bun.get("versionCode")}
    # Values that describe the device, for the report (no identifiers).
    for case_id in ("device.derived", "device.manufacturers", "device.roms", "device.screen", "sensors.available", "media.music"):
        device[case_id] = rhino.get(ids.index(case_id))
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
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="backslashreplace")
    if args.phase in ("record", "install", "restore", "verify", "grant") and not args.state:
        parser.error("--state is required")
    if args.phase == "grant" and (not args.set or not args.only):
        parser.error("grant needs --set and --only")
    {"record": base.record, "install": base.install, "restore": base.restore, "verify": base.verify, "grant": grant,
     "compare": compare, "denied": denied}[args.phase](args)


if __name__ == "__main__":
    main()
