#!/usr/bin/env python3
"""Device comparison of the Bun pure JS tool family (api-coverage roadmap P4.2a): util, species, the global data
functions, jsox (Mathx / Arrayx / Numberx), cvt / fmt / s13n and pinyin of the Bun host client against Rhino.

The cases are the shared JS tools cases (the Node runtime's tools/nodejs/api/js-tools/cases.js, synchronized into the
Bun checkout as tools/host-client/js-tools/cases.js): [id, expression] or [id, expression, runtime expression], where
the runtime expression (Node / Bun, may return a Promise) replaces the Rhino one and null skips the case. Results are
encoded by the cases file's own jsToolsEncode / jsToolsThrown in both engines and travel as chunked console lines
(GlobalConsole logcat). Only counts, failing case ids and short values are printed.

Phases (all take --serial; record / install / restore / verify also --state, a private directory):
  record                 versions and APK md5 of the host and the Bun plugin, and a copy of their APKs
  install --apk P=PATH   install a candidate APK (md5 verified)
  restore / verify       reinstall the recorded APKs that changed and compare the md5 with the record
  rhino [--out FILE]     run the cases in Rhino (RunIntentActivity) and write the expected values
  bun --expected FILE [--modes main,worker] [--denied G ...] [--report FILE]
                         run the cases in a Bun main script and a Bun Worker and compare them with the expected
                         values; with a denied grant (java.interop) the cases that reach the Java facade must reject
                         with NOT_GRANTED (or fail the same argument check Rhino fails first), all others still pass
                         ("open": the library getters of the global object, global.axios / dayjs / pangu / cheerio /
                         i18n, are counted apart until api-coverage Q61 / Q64 decide them)
  grant --set on|off|restore --only G ...   set (or restore) host Bun grants through a host script; the first change
                         of each grant records its previous value in --state

No dialog is answered here: the grants are host preferences (BunHostCapabilityGrants), set by a Rhino script that
runs in the host and restored afterwards. Pushed scripts are removed after each phase.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import threading
import time
import urllib.parse
import uuid

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import device_sample_a as sample  # noqa: E402

HOST = sample.HOST
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
DEVICE_DIR = "/sdcard/Download/aj6-bun-js-tools"
CASES = "tools/host-client/js-tools/cases.js"
GRANTS_FILE = "bun-js-tools-grants.json"
PACKAGES = [HOST, sample.BUN]
MARK = "AJ6JST"
CHUNK = 1000
# Bun expressions reaching the host's Java facade (java.interop): util.java.*, s13n.point / throwable, the display
# conversions, morse vibration and the java handle API itself.
JAVA_SOURCE = re.compile(r"\bjava\.|util\.java\.|s13n\.(point|throwable)\(|\.vibrate\(")
# The library getters of the Rhino global object: not in the Bun client while api-coverage Q61 / Q64 are open; counted
# apart as "open", not as failures.
OPEN_CASES = re.compile(r"^global\.(axios|dayjs|pangu|cheerio|i18n)\.")

EMIT_JS = """
function aj6Emit(run, index, result) {
    var text = encodeURIComponent(JSON.stringify(result));
    var parts = Math.max(1, Math.ceil(text.length / %d));
    for (var k = 0; k < parts; k++) {
        console.log('%s ' + run + ' ' + index + ' ' + (k + 1) + '/' + parts + ' ' + text.substr(k * %d, %d) + ' $');
    }
}
""" % (CHUNK, MARK, CHUNK, CHUNK)

RHINO_JS = """
var aj6Global = (function () { return this; })();
function aj6JavaInfo(v) {
    if (v === null || v === undefined || typeof v !== 'object') return null;
    try {
        if (v instanceof java.util.List) {
            var items = [];
            for (var i = 0; i < v.size(); i++) items.push(v.get(i));
            return {list: items};
        }
        if (v instanceof java.lang.Object) return {className: String(v.getClass().getName())};
    } catch (e) {
        return null;
    }
    return null;
}
(function (G) {
    for (var i = 0; i < JS_TOOLS_CASES.length; i++) {
        var result;
        try {
            result = {ok: jsToolsEncode(eval('(' + JS_TOOLS_CASES[i][1] + ')'), aj6JavaInfo)};
        } catch (e) {
            result = {th: jsToolsThrown(e)};
        }
        aj6Emit(AJ6_RUN, i, result);
    }
    aj6Emit(AJ6_RUN, -1, {done: JS_TOOLS_CASES.length, sdk: android.os.Build.VERSION.SDK_INT});
})(aj6Global);
"""

# The Bun case runner, shared by the main script and the Worker: the host client namespaces under the names the
# cases use (the Node module names).
BUN_RUNNER_JS = """
function aj6Env(host) {
    return {util: host.util, species: host.species, G: host.global, Mathx: host.jsox.mathx, Arrayx: host.jsox.arrayx,
        Numberx: host.jsox.numberx, jsox: host.jsox, cvt: host.cvt, fmt: host.fmt, s13n: host.s13n, pinyin: host.pinyin, java: host.java};
}
async function aj6Run(env, emit) {
    const names = Object.keys(env), values = names.map(name => env[name]);
    const javaInfo = v => v && typeof v === 'object' && typeof v.__autojs6JavaHandle === 'string' ? {className: v.className} : null;
    for (let i = 0; i < JS_TOOLS_CASES.length; i++) {
        const c = JS_TOOLS_CASES[i];
        const source = c.length > 2 ? c[2] : c[1];
        let result;
        if (source === null) {
            result = {skip: 1};
        } else {
            try {
                let value = new Function(...names, 'return (' + source + ');')(...values);
                let async = false;
                if (value && typeof value.then === 'function') {
                    async = true;
                    value = await value;
                }
                result = {ok: jsToolsEncode(value, javaInfo)};
                if (async) result.async = 1;
            } catch (e) {
                result = {th: e && e.code === 'NOT_GRANTED' ? {name: 'NOT_GRANTED', message: String(e.message)} : jsToolsThrown(e)};
            }
        }
        emit(i, result);
    }
}
"""


def bun_script(cases, modes, run):
    common = cases + EMIT_JS + BUN_RUNNER_JS
    worker = common + """
self.onmessage = async () => {
    try {
        const {createHostClient} = await import(process.env.AUTOJS6_HOST_CLIENT_MODULE);
        await aj6Run(aj6Env(await createHostClient()), (i, r) => postMessage([i, r]));
    } catch (e) {
        postMessage([-2, String(e && e.stack || e)]);
    }
    postMessage([-1, null]);
};
"""
    return ('"bun";\n' + f"const AJ6_RUN = '{run}', AJ6_MODES = {json.dumps(modes)};\n" + common + """
(async () => {
    const offset = {main: 0, worker: 100000};
    try {
        const {createHostClient} = await import(process.env.AUTOJS6_HOST_CLIENT_MODULE);
        if (AJ6_MODES.includes('main')) await aj6Run(aj6Env(await createHostClient()), (i, r) => aj6Emit(AJ6_RUN, offset.main + i, r));
        if (AJ6_MODES.includes('worker')) {
            await new Promise(resolve => {
                const w = new Worker(URL.createObjectURL(new Blob([%s], {type: 'application/javascript'})));
                w.onmessage = event => {
                    const [i, r] = event.data;
                    if (i === -1) { w.terminate(); resolve(); }
                    else if (i === -2) aj6Emit(AJ6_RUN, -2, {error: r});
                    else aj6Emit(AJ6_RUN, offset.worker + i, r);
                };
                w.onerror = event => { aj6Emit(AJ6_RUN, -2, {error: String(event.message)}); resolve(); };
                w.postMessage(1);
            });
        }
    } catch (e) {
        aj6Emit(AJ6_RUN, -2, {error: String(e && e.stack || e)});
    }
    aj6Emit(AJ6_RUN, -1, {done: JS_TOOLS_CASES.length});
})();
""" % json.dumps(worker))


GRANT_JS = r"""
var key = org.autojs.autojs.core.pref.keys.CredentialKeys.BUN_HOST_CAPABILITY_GRANT.key(AJ6_GRANT);
var before = key.storedOrNull();
if (AJ6_VALUE === 'absent') key.set(null, true); else key.set(java.lang.Boolean.valueOf(AJ6_VALUE === 'true'), true);
var after = key.storedOrNull();
aj6Emit(AJ6_RUN, 0, {before: before === null ? 'absent' : String(before), after: after === null ? 'absent' : String(after)});
aj6Emit(AJ6_RUN, -1, {done: 1});
"""


class Collector:
    """Streams the GlobalConsole logcat lines of one run and reassembles the chunked results."""

    def __init__(self, serial, run):
        self.run = run
        self.parts = {}
        self.results = {}
        self.done = threading.Event()
        sample.adb(serial, "logcat", "-c")
        self.process = subprocess.Popen(["adb", "-s", serial, "logcat", "-v", "raw", "-s", "GlobalConsole:*"],
                                        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, encoding="utf-8", errors="replace")
        threading.Thread(target=self.read, daemon=True).start()

    def read(self):
        # The host logs Bun output as it reads it, so a line may arrive split over several console entries (each with
        # the console's time / level prefix): a chunk ends with " $", and text made only of URI-encoded characters
        # continues the chunk before.
        pattern = re.compile(MARK + r" (\S+) (-?\d+) (\d+)/(\d+) (\S*) \$$")
        prefix = re.compile(r"^\d\d:\d\d:\d\d\.\d{3}/[VDIWEA]: ")
        continuation = re.compile(r"^[A-Za-z0-9%._~!*'()-]*( \$)?$")
        pending = None
        for line in self.process.stdout:
            line = prefix.sub("", line.rstrip("\r\n"))
            if pending is not None and continuation.match(line):
                pending += line
            elif MARK + " " in line:
                pending = line[line.index(MARK + " "):]
            else:
                continue
            if not pending.endswith(" $"):
                continue
            match, pending = pattern.match(pending), None
            if not match or match.group(1) != self.run:
                continue
            index, part, parts = int(match.group(2)), int(match.group(3)), int(match.group(4))
            chunks = self.parts.setdefault(index, {})
            chunks[part] = match.group(5)
            if len(chunks) == parts:
                self.results[index] = json.loads(urllib.parse.unquote("".join(chunks[k] for k in range(1, parts + 1))))
                if index == -1:
                    self.done.set()

    def close(self):
        self.process.kill()


def run_script(serial, name, text, run, wait):
    """Pushes [text], starts it with RunIntentActivity and returns the results of run [run] by index."""
    local = HERE / "__pycache__" / name
    local.parent.mkdir(exist_ok=True)
    local.write_text(text, encoding="utf-8")
    remote = f"{DEVICE_DIR}/{name}"
    sample.adb(serial, "shell", "mkdir", "-p", DEVICE_DIR)
    sample.adb(serial, "push", str(local), remote)
    collector = Collector(serial, run)
    try:
        time.sleep(0.5)
        sample.adb(serial, "shell", "am", "start", "-n", f"{HOST}/{RUN_ACTIVITY}", "-e", "path", remote)
        if not collector.done.wait(wait):
            print(f"warning: {name}: {len(collector.results)} results before the timeout", file=sys.stderr)
    finally:
        collector.close()
        local.unlink(missing_ok=True)
        sample.adb(serial, "shell", "rm", "-rf", DEVICE_DIR, check=False)
    return collector.results


def package_state(serial, package):
    """Version and APK md5 of [package], None when it is not installed."""
    out = sample.adb(serial, "shell", f"pm path {package}", check=False)
    if not any(line.startswith("package:") for line in out.splitlines()):
        return None
    return sample.package_state(serial, package)


def record(args):
    args.state.mkdir(parents=True, exist_ok=True)
    state = {"serial": args.serial, "model": sample.sh(args.serial, "getprop ro.product.model"),
             "sdk": sample.sh(args.serial, "getprop ro.build.version.sdk"), "packages": {p: package_state(args.serial, p) for p in PACKAGES}}
    (args.state / "state.json").write_text(json.dumps(state, indent=2), encoding="utf-8")
    for package, info in state["packages"].items():
        if not info:
            print(f"{package}: not installed")
            continue
        folder = args.state / "apks" / package
        folder.mkdir(parents=True, exist_ok=True)
        for name, split in info["splits"].items():
            local = folder / name
            if not local.exists() or sample.md5(local) != split["md5"]:
                sample.adb(args.serial, "pull", split["path"], str(local), timeout=600)
            if sample.md5(local) != split["md5"]:
                sys.exit(f"backup {package}/{name}: md5 mismatch")
        print(f"{package}: versionCode {info['versionCode']} ({info['versionName']}), md5 {','.join(s['md5'] for s in info['splits'].values())}"
              f"{' debuggable' if info['debuggable'] else ''}, APK copied")


def install(args):
    for item in args.apk:
        package, path = item.split("=", 1)
        sample.install_files(args.serial, package, [Path(path)])
        info = package_state(args.serial, package)
        remote = next(iter(info["splits"].values()))["md5"]
        ok = remote == sample.md5(path)
        print(f"install {package}: versionCode {info['versionCode']} ({info['versionName']}) md5 {remote} {'ok' if ok else 'MD5 MISMATCH'}")
        if not ok:
            sys.exit(1)


def compare_packages(args):
    state = json.loads((args.state / "state.json").read_text(encoding="utf-8"))
    changed = []
    for package, before in state["packages"].items():
        now = package_state(args.serial, package)
        same_package = (before is None and now is None) or (before is not None and now is not None and before["versionCode"] == now["versionCode"]
                        and sorted(s["md5"] for s in before["splits"].values()) == sorted(s["md5"] for s in now["splits"].values()))
        if not same_package:
            changed.append(package)
        print(f"verify {package}: {'same' if same_package else 'CHANGED'}" + (f" (md5 {','.join(s['md5'] for s in now['splits'].values())})" if now else " (not installed)"))
    return state, changed


def restore(args):
    state, changed = compare_packages(args)
    for package in changed:
        before = state["packages"][package]
        if before is None:
            sample.adb(args.serial, "uninstall", package)
            print(f"restore {package}: uninstalled")
        else:
            sample.install_files(args.serial, package, [args.state / "apks" / package / name for name in before["splits"]])
            print(f"restore {package}: reinstalled versionCode {before['versionCode']}")
    _, changed = compare_packages(args)
    print("restore: device matches the record" if not changed else f"restore: still different {changed}")
    sys.exit(1 if changed else 0)


def verify(args):
    sys.exit(1 if compare_packages(args)[1] else 0)


def cases_source(args):
    return (args.bun / CASES).read_text(encoding="utf-8")


def case_list(args):
    """[id, rhino expression, runtime expression or None when skipped] of every case."""
    script = ("const c = require(process.argv[1]).cases;"
              "process.stdout.write(JSON.stringify(c.map(x => [x[0], x[1], x.length > 2 ? x[2] : x[1]])))")
    out = subprocess.run(["node", "-e", script, str(args.bun / CASES)], capture_output=True, text=True, encoding="utf-8", check=True)
    return json.loads(out.stdout)


def same(a, b):
    """Encoded values are equal; a Java list (Rhino) equals an array (Bun) with equal elements."""
    if isinstance(a, dict) and "list" in a and isinstance(b, list):
        a = a["list"]
    if isinstance(b, dict) and "list" in b and isinstance(a, list):
        b = b["list"]
    if isinstance(a, dict) and isinstance(b, dict):
        return a.keys() == b.keys() and all(same(a[k], b[k]) for k in a)
    if isinstance(a, list) and isinstance(b, list):
        return len(a) == len(b) and all(same(x, y) for x, y in zip(a, b))
    return a == b and type(a) is type(b)


def error_name(name):
    """A Java exception Rhino reports as "JavaException <Simple name>" matches a Bun error of that name."""
    return name[len("JavaException "):] if name.startswith("JavaException ") else name


def verdict(expected, actual, denied):
    """'pass', 'skip' or the reason of a failure; a [denied] case passes with NOT_GRANTED, or with the Rhino result when
    it is answered before the Java facade (argument errors, nullish shortcuts)."""
    if "skip" in actual:
        return "skip"
    if denied and "th" in actual and actual["th"]["name"] == "NOT_GRANTED":
        return "pass"
    if "th" in expected:
        if "th" not in actual:
            return f"expected {expected['th']['name']}, got {json.dumps(actual.get('ok'), ensure_ascii=False)[:120]}"
        return "pass" if error_name(expected["th"]["name"]) == error_name(actual["th"]["name"]) \
            else f"expected {expected['th']['name']}, got {actual['th']['name']}: {actual['th']['message'][:120]}"
    if "th" in actual:
        return f"threw {actual['th']['name']}: {actual['th']['message'][:160]}"
    if same(expected["ok"], actual["ok"]):
        return "pass"
    return f"expected {json.dumps(expected['ok'], ensure_ascii=False)[:160]}, got {json.dumps(actual['ok'], ensure_ascii=False)[:160]}"


def rhino(args):
    run = uuid.uuid4().hex[:8]
    script = f"var AJ6_RUN = '{run}';\n" + cases_source(args) + EMIT_JS + RHINO_JS
    results = run_script(args.serial, f"aj6_js_tools_rhino_{run}.js", script, run, args.wait)
    ids = [c[0] for c in case_list(args)]
    meta = results.get(-1, {})
    host = package_state(args.serial, HOST) or {}
    model = sample.sh(args.serial, "getprop ro.product.model")
    expected = {"schema": "autojs6-js-tools-rhino-expected-v1", "host": {"versionCode": host.get("versionCode"), "versionName": host.get("versionName")},
                "device": {"model": model, "sdk": meta.get("sdk")}, "cases": {ids[i]: results[i] for i in range(len(ids)) if i in results}}
    out = args.out or args.state / f"rhino-{args.serial}.json"
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    Path(out).write_text(json.dumps(expected, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    thrown = sum(1 for v in expected["cases"].values() if "th" in v)
    print(f"rhino: {len(expected['cases'])} / {len(ids)} cases ({thrown} throw), host {host.get('versionCode')}, {model} API {meta.get('sdk')}")
    sys.exit(0 if len(expected["cases"]) == len(ids) else 1)


def bun(args):
    expected = json.loads(Path(args.expected).read_text(encoding="utf-8"))["cases"]
    cases = case_list(args)
    modes = args.modes.split(",")
    run = uuid.uuid4().hex[:8]
    results = run_script(args.serial, f"aj6_js_tools_bun_{run}.js", bun_script(cases_source(args), modes, run), run, args.wait)
    if -2 in results:
        print(f"bun: runner error {results[-2].get('error', '')[:600]}")
    report, failed = {}, 0
    for mode, offset in (("main", 0), ("worker", 100000)):
        if mode not in modes:
            continue
        counts = {"pass": 0, "fail": 0, "skip": 0, "missing": 0, "async": 0, "denied": 0, "open": 0}
        failures = {}
        for i, (case_id, _, source) in enumerate(cases):
            actual = results.get(offset + i)
            if actual is None:
                counts["missing"] += 1
                failures[case_id] = "missing"
                continue
            counts["async"] += 1 if actual.get("async") else 0
            denied = bool(args.denied) and source is not None and bool(JAVA_SOURCE.search(source))
            counts["denied"] += 1 if denied else 0
            if OPEN_CASES.search(case_id):
                counts["open"] += 1
                continue
            if case_id not in expected:
                counts["missing"] += 1
                failures[case_id] = "no expected value"
                continue
            result = verdict(expected[case_id], actual, denied)
            if result in ("pass", "skip"):
                counts[result] += 1
            else:
                counts["fail"] += 1
                failures[case_id] = result
        failed += counts["fail"] + counts["missing"]
        report[mode] = {"counts": counts, "failures": failures}
        print(f"bun {mode} (denied {','.join(args.denied) or 'none'}): pass {counts['pass']} / fail {counts['fail']} / skip {counts['skip']}"
              f" / missing {counts['missing']} / open {counts['open']} (async {counts['async']}, java cases {counts['denied'] if args.denied else '-'})")
        for case_id, reason in list(failures.items())[:args.show]:
            print(f"  {case_id}: {reason}")
    if args.report:
        Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    sys.exit(1 if failed else 0)


def grant(args):
    store = args.state / GRANTS_FILE
    saved = json.loads(store.read_text(encoding="utf-8")) if store.exists() else {}
    for name in args.only:
        value = {"on": "true", "off": "false"}.get(args.set) or saved.get(name, "absent")
        run = uuid.uuid4().hex[:8]
        script = f"var AJ6_RUN = '{run}';\nvar AJ6_GRANT = {json.dumps(name)};\nvar AJ6_VALUE = '{value}';\n" + EMIT_JS + GRANT_JS
        answer = run_script(args.serial, f"aj6_js_tools_grant_{run}.js", script, run, 60).get(0)
        if not answer:
            sys.exit(f"grant {name}: no answer from the host script")
        if args.set != "restore" and name not in saved:
            saved[name] = answer["before"]
        print(f"grant {name}: {answer['before']} -> {answer['after']}")
    if args.set == "restore":
        for name in args.only:
            saved.pop(name, None)
    args.state.mkdir(parents=True, exist_ok=True)
    if saved:
        store.write_text(json.dumps(saved), encoding="utf-8")
    else:
        store.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("phase", choices=["record", "install", "restore", "verify", "rhino", "bun", "grant"])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--state", type=Path, help="private state directory (APK copies, recorded grants, Rhino values)")
    parser.add_argument("--bun", type=Path, default=HERE.parents[2] / "AutoJs6-Plugin-Bun-Runtime", help="Bun runtime checkout")
    parser.add_argument("--out", type=Path, help="rhino: expected values file (default <state>/rhino-<serial>.json)")
    parser.add_argument("--expected", type=Path, help="bun: expected values file")
    parser.add_argument("--modes", default="main,worker", help="bun: main and / or worker")
    parser.add_argument("--denied", nargs="*", default=[], help="bun: grants that are off for this run (java.interop)")
    parser.add_argument("--report", type=Path, help="bun: write the counts and failures as JSON")
    parser.add_argument("--show", type=int, default=40, help="bun: failing cases printed per mode")
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
    if args.phase == "rhino" and not (args.out or args.state):
        parser.error("rhino needs --out or --state")
    if args.phase == "bun" and not args.expected:
        parser.error("bun needs --expected")
    {"record": record, "install": install, "restore": restore, "verify": verify, "rhino": rhino, "bun": bun, "grant": grant}[args.phase](args)


if __name__ == "__main__":
    main()
