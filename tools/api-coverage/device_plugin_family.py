#!/usr/bin/env python3
"""Device comparison of the plugin family bridges (api-coverage roadmap P3.5 / P4.5): colors.theme, gallery (3-Maple Image),
files.batchRename (Batch Rename) and the speech analysis members (3-Macaw Speech) in Rhino, in a Node main script, in a
Node Worker, in a Bun main script and in a Bun Worker.

The cases are device_plugin_family_cases.js: one body for all runtimes (see its head). Every run gets a fresh directory
with the fixtures (two PNG files, a WAV file, three files to rename), the values are normalized by the cases themselves
and travel as chunked console lines (GlobalConsole logcat). A run is compared with Rhino's run by case; thrown errors
match by name and code. Only counts, failing case ids and short values are printed.

The picker and the editor are ended by a new viewer (SESSION_REPLACED), so no phase needs anybody at the device, unless
the plugin asks for its photo permission first: that dialog is the maintainer's. The theme color of the host is set back
to the one read at the start of each run. The renamed batches are deleted from the history, and the preset aj6-p45 stays
in Batch Rename (no script API removes a preset).

Phases (all take --serial; record / install / restore / verify / grant also --state, a private directory):
  record / install --apk P=PATH / restore / verify   as in device_bun_js_tools.py, for the host and five plugins
  grant --set on|off|restore --only G ...           set (or restore) host Bun grants (GRANTS)
  compare [--forms rhino,node,node-worker,bun,bun-worker] [--cases ID ...] [--report FILE]
          Rhino and the runtimes: the Node runs declare PERMISSIONS, the Bun runs need the grants of GRANTS on
  denied [--forms node,node-worker,bun,bun-worker] [--report FILE]
          nothing declared or granted: every host route rejects with its missing capability, the local members answer

Typical run: record, install the candidates, grant --set on --only (GRANTS), compare, grant --set restore --only (GRANTS),
denied, restore. Pushed files are removed after each run.
"""
import argparse
import json
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import time
import uuid
import zlib

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import device_bun_js_tools as base  # noqa: E402

sample = base.sample
MAPLE = "io.github.supermonster003.autojs6.plugin.three.maple.image"
BATCH = "io.github.supermonster003.autojs6.plugin.batch.rename"
MACAW = "io.github.supermonster003.autojs6.plugin.three.macaw.speech"
base.PACKAGES = [base.HOST, sample.NODE, sample.BUN, MAPLE, BATCH, MACAW]
base.DEVICE_DIR = "/sdcard/Download/aj6-plugin-family"
base.GRANTS_FILE = "plugin-family-grants.json"
CASES = HERE / "device_plugin_family_cases.js"
GRANTS = ["theme.control", "gallery.control", "batchrename.control", "speech.control"]
PERMISSIONS = ["theme", "gallery", "batch_rename", "speech"]
FORMS = ["rhino", "node", "node-worker", "bun", "bun-worker"]
CASE_TIMEOUT_MS = 45000
NL = chr(10)

# Rhino: the cases run one after the other on the script thread, so that an event case can wait.
RHINO_JS = """
var S = {};
function A(v) {
    if (Array.isArray(v)) return v;
    var out = [];
    if (typeof v.size === 'function') { for (var i = 0; i < v.size(); i++) out.push(v.get(i)); return out; }
    for (var k = 0; k < v.length; k++) out.push(v[k]);
    return out;
}
function T(fn) {
    try {
        var value = fn();
        return {ok: value === undefined ? null : value};
    } catch (e) {
        return {name: String(e.name), code: e.code === undefined || e.code === null ? null : String(e.code)};
    }
}
function THEN(fn, ok, bad) {
    var value;
    try { value = fn(); } catch (e) { bad(e); return; }
    ok(value);
}
function aj6Step(i) {
    if (i === AJ6_CASES.length) { aj6Emit(AJ6_RUN, -1, {done: i}); return; }
    var id = AJ6_CASES[i][0], body = AJ6_CASES[i][1], timer = null, settled = false;
    var finish = function (result) {
        if (settled) return;
        settled = true;
        if (timer !== null) clearTimeout(timer);
        aj6Emit(AJ6_RUN, i, result);
        setTimeout(function () { aj6Step(i + 1); }, 20);
    };
    try {
        if (id.charAt(id.length - 1) === '~') {
            timer = setTimeout(function () { finish({th: {name: 'CaseTimeout', code: null}}); }, AJ6_CASE_TIMEOUT_MS);
            eval('(' + body + ')')(function (value) { finish({ok: value === undefined ? null : value}); });
        } else {
            var value = eval('(function () {' + body + '})')();
            finish({ok: value === undefined ? null : value});
        }
    } catch (e) {
        finish({th: {name: String(e.name), code: e.code === undefined || e.code === null ? null : String(e.code)}, message: String(e.message)});
    }
}
aj6Step(0);
"""

RUNTIME_JS = """
const S = {}, A = v => Array.from(v);
const T = async fn => {
    try {
        const value = await fn();
        return {ok: value === undefined ? null : value};
    } catch (e) {
        return {name: String(e.name), code: e.code === undefined || e.code === null ? null : String(e.code)};
    }
};
const THEN = (fn, ok, bad) => { Promise.resolve().then(fn).then(ok, bad); };
const AsyncFunction = (async () => {}).constructor;
async function aj6Run(env, emit) {
    const scope = {...env, S, A, T, THEN, D: AJ6_D, PRESET_DOCUMENT, plain, allPlain, libraries, intelligent, base, written, repeated, items, presetNames, outcome};
    const names = Object.keys(scope), values = names.map(name => scope[name]);
    for (let i = 0; i < AJ6_CASES.length; i++) {
        const [id, body] = AJ6_CASES[i];
        let result, timer;
        const timeout = new Promise((_, reject) => { timer = setTimeout(() => reject(Object.assign(new Error('no answer'), {name: 'CaseTimeout'})), AJ6_CASE_TIMEOUT_MS); });
        try {
            const value = id.endsWith('~')
                ? new Promise(done => new Function(...names, 'return (' + body + ');')(...values)(done))
                : new AsyncFunction(...names, body)(...values);
            const answer = await Promise.race([value, timeout]);
            result = {ok: answer === undefined ? null : answer};
        } catch (e) {
            result = {th: {name: String(e.name), code: e.code === undefined || e.code === null ? null : String(e.code)}, message: String(e.message)};
        }
        clearTimeout(timer);
        emit(i, result);
    }
}
"""

NODE_ENV = ("{colors: require('autojs6:colors'), files: require('autojs6:files'), gallery: require('autojs6:gallery'),"
            " speech: require('autojs6:speech')}")
BUN_ENV = "{colors: host.colors, files: host.files, gallery: host.gallery, speech: host.speech}"

# The denied run. [id, expression, missing]: the call rejects with this missing capability / grant; None: it answers.
DENIED_CASES = [
    ["colors.theme.get", "colors.theme.get()", "theme"],
    ["colors.theme.set", "colors.theme.set('#336699')", "theme"],
    ["colors.theme.current", "colors.theme.current()", "theme"],
    ["gallery.isAvailable", "gallery.isAvailable()", "gallery"],
    ["gallery.convert", "gallery.convert('a.png')", "gallery"],
    ["gallery.pick", "gallery.pick()", "gallery"],
    ["gallery.view", "gallery.view('a.png')", "gallery"],
    ["gallery.edit", "gallery.edit('a.png')", "gallery"],
    ["rename.isAvailable", "files.batchRename.isAvailable()", "batch_rename"],
    ["rename.plan", "files.batchRename.plan('a', {search: 'a', replace: 'b'})", "batch_rename"],
    ["rename.callable", "files.batchRename('a', {search: 'a', replace: 'b'})", "batch_rename"],
    ["rename.history", "files.batchRename.history()", "batch_rename"],
    ["rename.presets", "files.batchRename.presets()", "batch_rename"],
    ["speech.diarize", "speech.diarize('a.wav')", "speech"],
    ["speech.detectLanguage", "speech.detectLanguage('a.wav')", "speech"],
    ["speech.tag", "speech.tag('a.wav')", "speech"],
    ["speech.speaker.list", "speech.speaker.list()", "speech"],
    ["speech.speaker.identify", "speech.speaker.identify('a.wav')", "speech"],
    ["gallery.GalleryError", "new gallery.GalleryError('m', 'X').code", None],
    ["rename.BatchRenameError", "new files.batchRename.BatchRenameError('m', 'X').code", None],
]
BUN_GRANT = {"theme": "theme.control", "gallery": "gallery.control", "batch_rename": "batchrename.control", "speech": "speech.control"}
DENIED_JS = """
async function aj6Denied(env, emit) {
    const names = Object.keys(env), values = names.map(name => env[name]);
    for (let i = 0; i < AJ6_CASES.length; i++) {
        let result;
        try {
            result = {ok: await new Function(...names, 'return (' + AJ6_CASES[i][1] + ');')(...values)};
        } catch (e) {
            result = {code: e && e.code ? String(e.code) : null, name: String(e && e.name), capability: e && e.capability ? String(e.capability) : null,
                missing: e && e.missingCapabilities ? [...e.missingCapabilities].sort() : null, message: String(e && e.message)};
        }
        emit(i, result);
    }
}
"""


def png(width, height, rgba):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    rows = b"".join(b"\0" + bytes(rgba) * width for _ in range(height))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))


def fixtures(folder):
    (folder / "a.png").write_bytes(png(8, 6, (51, 102, 153, 255)))
    (folder / "b.png").write_bytes(png(4, 4, (153, 51, 102, 128)))
    (folder / "s.wav").write_bytes(base.media_fixture())
    (folder / "r").mkdir()
    for name in ("IMG_0001.txt", "IMG_0002.txt", "notes.txt"):
        (folder / "r" / name).write_text(name, encoding="ascii")


def run_files(args, text, run, project=None):
    """Pushes a run directory (the script, project.json for Node, the fixtures), starts the script and returns its results."""
    local = HERE / "__pycache__" / f"plugin_family_{run}"
    local.mkdir(parents=True)
    directory = f"{base.DEVICE_DIR}/{run}"
    try:
        main = "main.js" if project else f"aj6_{run}.js"
        (local / main).write_text(text, encoding="utf-8")
        if project:
            (local / "project.json").write_text(json.dumps(project), encoding="utf-8")
        fixtures(local)
        sample.adb(args.serial, "shell", "mkdir", "-p", base.DEVICE_DIR)
        sample.adb(args.serial, "push", str(local), directory, timeout=120)
        collector = base.Collector(args.serial, run)
        try:
            if args.foreground:
                sample.adb(args.serial, "shell", "monkey", "-p", base.HOST, "-c", "android.intent.category.LAUNCHER", "1", check=False)
                time.sleep(2)
            time.sleep(0.5)
            sample.adb(args.serial, "shell", "am", "start", "-n", f"{base.HOST}/{base.RUN_ACTIVITY}", "-e", "path", f"{directory}/{main}")
            if not collector.done.wait(args.wait):
                print(f"warning: {len(collector.results)} results before the timeout", file=sys.stderr)
        finally:
            collector.close()
    finally:
        shutil.rmtree(local, ignore_errors=True)
        sample.adb(args.serial, "shell", "rm", "-rf", directory, check=False)
    return collector.results


def helpers():
    text = CASES.read_text(encoding="utf-8")
    return text[text.index("// Helpers shared by the runtimes"):text.index('if (typeof module === "object"')]


def case_table(args):
    script = "process.stdout.write(JSON.stringify(require(process.argv[1]).cases))"
    out = subprocess.run(["node", "-e", script, str(CASES)], capture_output=True, text=True, encoding="utf-8", check=True)
    table = json.loads(out.stdout)
    return [case for case in table if case[0] in args.cases] if args.cases else table


def rhino_source(body):
    return body.replace("AW(", "(").replace("@(", "(")


def runtime_source(case_id, body):
    if case_id.endswith("~"):
        return body
    return body.replace("function (", "async function (").replace("AW(", "await (").replace("@(", "Async(")


def header(run, directory, sources):
    return (f"const AJ6_RUN = '{run}', AJ6_D = '{directory}', D = AJ6_D, AJ6_CASE_TIMEOUT_MS = {CASE_TIMEOUT_MS};" + NL
            + f"const AJ6_CASES = {json.dumps(sources)};" + NL)


def rhino_script(run, table):
    directory = f"{base.DEVICE_DIR}/{run}"
    sources = [[case[0], rhino_source(case[1])] for case in table]
    return header(run, directory, sources).replace("const ", "var ") + helpers() + base.EMIT_JS + RHINO_JS


def worker_body(common, env, setup=""):
    return common + setup + f"aj6Run({env}, (i, r) => aj6Post([i, r])).catch(e => aj6Post([-2, String(e && e.stack || e)])).then(() => aj6Post([-1, null]));" + NL


def node_script(form, run, sources, runner="aj6Run", cases_length=None):
    common = header(run, f"{base.DEVICE_DIR}/{run}", sources) + helpers() + RUNTIME_JS + DENIED_JS
    length = cases_length if cases_length is not None else len(sources)
    if form == "node":
        return ('"nodejs";' + NL + common + base.EMIT_JS + f"{runner}({NODE_ENV}, (i, r) => aj6Emit(AJ6_RUN, i, r))"
                + ".catch(e => aj6Emit(AJ6_RUN, -2, {error: String(e && e.stack || e)}))"
                + f".finally(() => aj6Emit(AJ6_RUN, -1, {{done: {length}}}));" + NL)
    inner = (common + "const {parentPort} = require('node:worker_threads'), aj6Post = m => parentPort.postMessage(m);" + NL
             + f"{runner}({NODE_ENV}, (i, r) => aj6Post([i, r])).catch(e => aj6Post([-2, String(e && e.stack || e)])).then(() => aj6Post([-1, null]));" + NL)
    return ('"nodejs";' + NL + f"const AJ6_RUN = '{run}';" + NL + base.EMIT_JS + "const {Worker} = require('node:worker_threads');" + NL
            + f"const worker = new Worker({json.dumps(inner)}, {{eval: true}});" + NL
            + "worker.on('message', ([i, r]) => { if (i === -1) worker.terminate(); else if (i === -2) aj6Emit(AJ6_RUN, -2, {error: r}); else aj6Emit(AJ6_RUN, i, r); });" + NL
            + "worker.on('error', e => aj6Emit(AJ6_RUN, -2, {error: String(e && e.stack || e)}));" + NL
            + f"worker.on('exit', () => aj6Emit(AJ6_RUN, -1, {{done: {length}}}));" + NL)


def bun_script(form, run, sources, runner="aj6Run", cases_length=None):
    common = header(run, f"{base.DEVICE_DIR}/{run}", sources) + helpers() + RUNTIME_JS + DENIED_JS
    length = cases_length if cases_length is not None else len(sources)
    client = "const {createHostClient} = await import(process.env.AUTOJS6_HOST_CLIENT_MODULE); const host = await createHostClient();"
    if form == "bun":
        return ('"bun";' + NL + common + base.EMIT_JS + f"(async () => {{ try {{ {client} await {runner}({BUN_ENV}, (i, r) => aj6Emit(AJ6_RUN, i, r)); }}"
                + " catch (e) { aj6Emit(AJ6_RUN, -2, {error: String(e && e.stack || e)}); }" + NL
                + f"aj6Emit(AJ6_RUN, -1, {{done: {length}}}); }})();" + NL)
    inner = common + (f"self.onmessage = async () => {{ try {{ {client} await {runner}({BUN_ENV}, (i, r) => postMessage([i, r])); }}"
                      " catch (e) { postMessage([-2, String(e && e.stack || e)]); } postMessage([-1, null]); };" + NL)
    return ('"bun";' + NL + f"const AJ6_RUN = '{run}';" + NL + base.EMIT_JS + "(async () => { await new Promise(resolve => {" + NL
            + f"const w = new Worker(URL.createObjectURL(new Blob([{json.dumps(inner)}], {{type: 'application/javascript'}})));" + NL
            + "w.onmessage = event => { const [i, r] = event.data; if (i === -1) { w.terminate(); resolve(); } else if (i === -2) aj6Emit(AJ6_RUN, -2, {error: r}); else aj6Emit(AJ6_RUN, i, r); };" + NL
            + "w.onerror = event => { aj6Emit(AJ6_RUN, -2, {error: String(event.message)}); resolve(); };" + NL
            + f"w.postMessage(1); }}); aj6Emit(AJ6_RUN, -1, {{done: {length}}}); }})();" + NL)


def project(permissions):
    return {"name": "plugin-family", "type": "node", "main": "main.js", "node": {"permissions": permissions, "timeoutMs": 300000}}


def verdict(expected, actual):
    if expected is None:
        return "missing Rhino value"
    if actual is None:
        return "missing"
    left, right = {k: v for k, v in expected.items() if k != "message"}, {k: v for k, v in actual.items() if k != "message"}
    if base.same(left, right):
        return "pass"
    return f"expected {json.dumps(expected, ensure_ascii=False)[:200]}, got {json.dumps(actual, ensure_ascii=False)[:200]}"


def finish(args, report, failed):
    if args.report:
        Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=1) + NL, encoding="utf-8")
    sys.exit(1 if failed else 0)


def compare(args):
    table = case_table(args)
    forms = args.forms.split(",")
    report, failed = {"forms": {}}, 0
    run = uuid.uuid4().hex[:8]
    rhino = run_files(args, rhino_script(run, table), run)
    report["device"] = {"model": sample.sh(args.serial, "getprop ro.product.model"), "sdk": sample.sh(args.serial, "getprop ro.build.version.sdk"),
                        "versions": {p: (base.package_state(args.serial, p) or {}).get("versionCode") for p in base.PACKAGES}}
    thrown = sorted(table[i][0] for i in rhino if i >= 0 and "th" in rhino[i])
    print(f"rhino: {len([i for i in rhino if i >= 0])} / {len(table)} cases, {report['device']['model']} API {report['device']['sdk']}, thrown {thrown}")
    report["rhino"] = {table[i][0]: rhino[i] for i in sorted(rhino) if i >= 0}
    if -2 in rhino:
        print(f"rhino: runner error {rhino[-2].get('error', '')[:600]}")
    if any(table[i][0] in ("gallery.isAvailable", "rename.isAvailable") and rhino[i].get("ok") is not True for i in rhino if i >= 0):
        print("a plugin is not available in Rhino: install and enable 3-Maple Image and Batch Rename, then run compare again")
        finish(args, report, 1)
    for form in forms:
        if form == "rhino":
            continue
        run = uuid.uuid4().hex[:8]
        sources = [[case[0], runtime_source(case[0], case[1])] for case in table]
        if form.startswith("node"):
            results = run_files(args, node_script(form, run, sources), run, project(PERMISSIONS))
        else:
            results = run_files(args, bun_script(form, run, sources), run)
        if -2 in results:
            print(f"{form}: runner error {results[-2].get('error', '')[:600]}")
        counts, failures = {"pass": 0, "fail": 0, "missing": 0}, {}
        for i, (case_id, _) in enumerate(table):
            result = verdict(rhino.get(i), results.get(i))
            key = "pass" if result == "pass" else "missing" if result.startswith("missing") else "fail"
            counts[key] += 1
            if key != "pass":
                failures[case_id] = result
        failed += counts["fail"] + counts["missing"]
        report["forms"][form] = {"counts": counts, "failures": failures}
        print(f"{form}: pass {counts['pass']} / fail {counts['fail']} / missing {counts['missing']}")
        for case_id, result in list(failures.items())[:args.show]:
            print(f"  {case_id}: {result}")
    finish(args, report, failed)


def names(actual, capability):
    """The rejection names [capability]: SpeechError has no capability fields, its message does."""
    message = actual.get("message", "")
    return actual.get("capability") == capability or actual.get("missing") == [capability] or message.endswith(": " + capability) or f": {capability};" in message


def denied(args):
    report, failed = {"forms": {}}, 0
    sources = [[case[0], case[1]] for case in DENIED_CASES]
    for form in args.forms.split(","):
        if form == "rhino":
            continue
        run = uuid.uuid4().hex[:8]
        if form.startswith("node"):
            results = run_files(args, node_script(form, run, sources, "aj6Denied"), run, project([]))
        else:
            results = run_files(args, bun_script(form, run, sources, "aj6Denied"), run)
        if -2 in results:
            print(f"{form}: runner error {results[-2].get('error', '')[:600]}")
        counts, failures = {"pass": 0, "fail": 0}, {}
        for i, (case_id, _, missing) in enumerate(DENIED_CASES):
            actual = results.get(i)
            if actual is None:
                result = "missing"
            elif missing is None:
                result = "pass" if actual.get("ok") == "X" else f"expected X, got {json.dumps(actual)[:160]}"
            elif form.startswith("node"):
                result = "pass" if actual.get("code") == "ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED" and names(actual, missing) else \
                    f"expected {missing} undeclared, got {json.dumps(actual)[:200]}"
            else:
                grant = BUN_GRANT[missing]
                result = "pass" if actual.get("code") == "NOT_GRANTED" and names(actual, grant) else \
                    f"expected {grant} not granted, got {json.dumps(actual)[:200]}"
            counts["pass" if result == "pass" else "fail"] += 1
            if result != "pass":
                failures[case_id] = result
        failed += counts["fail"]
        report["forms"][form] = {"counts": counts, "failures": failures}
        print(f"denied {form}: pass {counts['pass']} / fail {counts['fail']}")
        for case_id, result in list(failures.items())[:args.show]:
            print(f"  {case_id}: {result}")
    finish(args, report, failed)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("phase", choices=["record", "install", "restore", "verify", "grant", "compare", "denied"])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--state", type=Path, help="private state directory (APK copies, recorded grants)")
    parser.add_argument("--forms", default=",".join(FORMS), help="compare / denied: the forms to run")
    parser.add_argument("--cases", nargs="*", default=[], help="compare: only these case ids")
    parser.add_argument("--report", type=Path, help="compare / denied: write the counts and failures as JSON")
    parser.add_argument("--show", type=int, default=40, help="failing cases printed per form")
    parser.add_argument("--only", nargs="*", default=[], help="grant: the grants to change")
    parser.add_argument("--set", choices=["on", "off", "restore"], help="grant: the value to set")
    parser.add_argument("--wait", type=int, default=420)
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
     "compare": compare, "denied": denied}[args.phase](args)


if __name__ == "__main__":
    main()
