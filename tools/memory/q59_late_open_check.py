"""Checks on a device the late plugin openings of docs/memory/ROADMAP.md Q59 (a session the plugin opens after its script stopped).

Usage: py -3.12 -I -u tools/memory/q59_late_open_check.py -s SERIAL --tag before|after [--cases gallery,yolo,mail,epub,autojs4]
    [--steps 24] [--settle 40] [--yolo-model DIR] [--epub-chapters N] [--evidence DIR]

Each case starts from a cold host (am force-stop, MainActivity) and runs scripts in it (pushed to /sdcard/Download/aj6-q59 and
started through RunIntentActivity, as q54_residue_check.py does):
- a calibration script opens the plugin session twice without stopping, measures the second (warm) opening and closes it;
- then one script per delay of a sweep from 0 to 1.2 times that opening time: a script thread waits the delay after the opening
  call started and stops its own engine (engines.myEngine().forceStop(), the stop button of the task list); an opening that
  returns before the stop is closed by the script itself, so only a stop that lands inside the opening can leave anything.
Openings: gallery.view (a pushed PNG; the viewer Activity of 3-Maple Image may show briefly), yolo.load (a pushed YOLO11n
model copied into the host's files with run-as), mail.connect (opens the plugin session only, no server), epub.open (a
generated book of --epub-chapters chapters, default 3000, whose parsing in the plugin widens the window; the connections are
counted on the EPUB plugin of PLUGINS, which must be the only one installed since the host selects among them), and for the
AutoJs4 runtime an execution of a pushed script through ScriptEngineService, whose own engine is stopped (its forceStop
only flags the stop; the admission deadline of 75 s, the other Q59 path, is not reachable).

After the sweep the script waits --settle seconds (the connection pool unbinds idle plugins after 30 s) and counts the
connections the host process still holds to the plugin service (dumpsys activity services <plugin>): every one left is a
dedicated binding of a session that opened after its script stopped (the pool keeps at most one per plugin, and none after
the idle unbind). Nothing on the screen is tapped; the host is force-stopped after each case, which drops what it held.

Output: one line per case: HELD (connections left, the Q59 window reproduced), RELEASED (none left) or FAIL / SKIP, with the
opening time, the number of stops that landed inside the opening, the connection counts and the openings refused before any
stop (refused=N: the YOLO provider allows one session and the EPUB plugin 8 open books, so late sessions they still hold
make later openings fail). The evidence goes to --evidence (default docs/memory/evidence/) as
q59-<model>-<versionCode>-<tag>-<date>.md. Exit code 0 when no case fails.
"""
import argparse
import datetime
import json
import pathlib
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import ui_leak_walk as walk  # noqa: E402

REPO = HERE.parent.parent
DEVICE_DIR = "/sdcard/Download/aj6-q59"
STAGING_DIR = "/data/local/tmp/aj6-q59"
RESULT = "files/q59-result.json"
YOLO_DIR = "files/q59-yolo"
CASES = ("gallery", "yolo", "mail", "epub", "autojs4")
PLUGINS = {
    "gallery": "io.github.supermonster003.autojs6.plugin.three.maple.image",
    "yolo": "io.github.supermonster003.autojs6.plugin.yolo.ncnn",
    "mail": "io.github.supermonster003.autojs6.plugin.three.stamp.mail",
    "epub": "io.github.supermonster003.autojs6.plugin.readium.epub.reader",
    "autojs4": "io.github.supermonster003.autojs6.plugin.autojs4.runtime",
}
YOLO_COMPONENT = PLUGINS["yolo"] + "/" + PLUGINS["yolo"] + ".provider.YoloProviderService"
DEFAULT_YOLO_MODEL = REPO.parent / "AutoJs6-Plugin-Yolo-NCNN" / "fixtures" / "local" / "yolo11n"
EPUB_SAMPLE = REPO / "app" / "src" / "androidTest" / "assets" / "runtime-epub" / "book.epub"
AUTOJS4_DELAYS_MS = (0, 5, 10, 20, 40, 80, 160, 320, 640)

# The opening call of each case (`open()` returns what `close(it)` closes).
OPENINGS = {
    "gallery": ("gallery.view(__IMAGE__)", "it.close()"),
    "yolo": ("yolo.load(__MODEL__, { component: __COMPONENT__, device: 'cpu', threads: 4, "
             "decoderId: 'ultralytics-detect', timeoutMillis: 120000 })", "it.close()"),
    "mail": ("mail.connect({ address: 'q59@localhost', password: 'q59', imap: { host: '127.0.0.1', port: 1, tls: 'none' } })",
             "it.close()"),
    "epub": ("epub.open(__BOOK__)", "it.close()"),
}

SESSION_SCRIPT = r'''let out = new java.io.File(context.getApplicationContext().getFilesDir(), "q59-result.json").getPath();
let delay = __DELAY__;
let state = { delay: delay };
let save = () => files.write(out, JSON.stringify(state));
let open = () => __OPEN__;
let close = (it) => { try { __CLOSE__; } catch (e) { state.closeError = String(e); } };
let elapsed = (t0) => (java.lang.System.nanoTime() - t0) / 1e6;
if (delay < 0) {
    let first = open();
    close(first);
    let t0 = java.lang.System.nanoTime();
    let second = open();
    state.openMs = elapsed(t0);
    close(second);
    state.done = true;
    save();
} else {
    let engine = engines.myEngine();
    let t0 = java.lang.System.nanoTime();
    threads.start(function () {
        java.lang.Thread.sleep(delay);
        state.stopMs = elapsed(t0);
        state.stopping = true;
        save();
        engine.forceStop();
    });
    let it;
    try {
        it = open();
    } catch (e) {
        if (!state.stopping) {
            state.openError = String(e);
            save();
        }
        throw e;
    }
    state.returnedMs = elapsed(t0);
    close(it);
    save();
    sleep(30000);
}
'''

AUTOJS4_SCRIPT = r'''let out = new java.io.File(context.getApplicationContext().getFilesDir(), "q59-result.json").getPath();
let delay = __DELAY__;
let state = { delay: delay };
let save = () => files.write(out, JSON.stringify(state));
let source = org.autojs.autojs.script.AutoJs4ScriptSource.forFile(new java.io.File(__TARGET__));
let config = new org.autojs.autojs.execution.ExecutionConfig();
config.setWorkingDirectory(source.getWorkingDirectory());
let t0 = java.lang.System.nanoTime();
let execution = org.autojs.autojs.AutoJs.getInstance().getScriptEngineService().execute(source, config);
let engine = null;
while ((engine = execution.getEngine()) == null && java.lang.System.nanoTime() - t0 < 5e9) java.lang.Thread.sleep(1);
state.engineMs = (java.lang.System.nanoTime() - t0) / 1e6;
if (engine == null) {
    state.error = "no engine";
} else {
    java.lang.Thread.sleep(delay);
    state.stopMs = (java.lang.System.nanoTime() - t0) / 1e6;
    state.stopping = true;
    engine.forceStop();
}
save();
'''

AUTOJS4_TARGET = "sleep(3000);\n"


def log(text=""):
    print(text, flush=True)


def js(value):
    return json.dumps(value)


def connections(dev, package):
    """Connections of the host process to each service of [package], as {simple service name: count} (one ConnectionRecord
    per bindService of a ServiceConnection, listed under the host's AppBindRecord of that service). Other services of the
    plugin (an explorer action service the host binds at start, say) are counted apart, so they do not blur the session's."""
    counts = {}
    service = None
    in_host = False
    for line in dev.shell(f"dumpsys activity services {package}").splitlines():
        text = line.strip()
        if text.startswith("* ServiceRecord{"):
            service = text.rstrip("}").split("/")[-1].rsplit(".", 1)[-1]
            in_host = False
        elif text.startswith("* Client AppBindRecord"):
            in_host = f":{walk.PACKAGE}/" in text
        elif text.startswith("* IntentBindRecord") or text.startswith("All Connections") or text.startswith("Connection bindings"):
            in_host = False
        elif in_host and service and text.startswith("ConnectionRecord{"):
            counts[service] = counts.get(service, 0) + 1
    return counts


def total(counts):
    return sum(counts.values()) if counts is not None else None


def held(baseline, settled):
    """Connections left above the baseline, service by service (one the host dropped meanwhile does not offset another)."""
    return sum(max(0, n - baseline.get(name, 0)) for name, n in settled.items())


def describe(counts):
    if counts is None:
        return "-"
    return ", ".join(f"{name} {n}" for name, n in sorted(counts.items())) or "0"


def installed_version(dev, package):
    out = dev.shell(f"dumpsys package {package}")
    code = next((part.split("=", 1)[1] for part in out.split() if part.startswith("versionCode=")), None)
    name = next((line.split("=", 1)[1].strip() for line in out.splitlines() if "versionName=" in line), None)
    return f"{name} ({code})" if code else None


def push_and_run(dev, name, source):
    local = HERE / "__pycache__" / name
    local.parent.mkdir(exist_ok=True)
    local.write_text(source, encoding="utf-8")
    remote = f"{DEVICE_DIR}/{name}"
    dev.adb("push", str(local), remote, check=True)
    local.unlink()
    dev.shell(f"am start -n {walk.PACKAGE}/{walk.RUN_ACTIVITY} -e path {remote}")


def run_and_read(dev, app, name, source, ready, timeout=60.0):
    app.run_as(f"rm -f {RESULT}")
    push_and_run(dev, name, source)
    deadline = time.time() + timeout
    while time.time() < deadline:
        text = app.run_as(f"cat {RESULT}").strip()
        if text.startswith("{"):
            state = json.loads(text)
            if ready(state):
                return state
        time.sleep(0.5)
    raise walk.WalkError(f"{name} wrote no result within {timeout:.0f} s")


class CaseResult:
    def __init__(self, name):
        self.name = name
        self.verdict = "FAIL"
        self.note = ""
        self.open_ms = None
        self.baseline = None
        self.after_runs = None
        self.settled = None
        self.runs = []

    @property
    def inside(self):
        return sum(1 for run in self.runs if run.get("inside"))

    @property
    def refused(self):
        """Openings that failed before any stop (a plugin that allows one session refuses the next while a late one is held)."""
        return sum(1 for run in self.runs if run.get("openError"))

    def line(self):
        open_ms = "-" if self.open_ms is None else f"{self.open_ms:.0f}ms"
        return (f"{self.name:<8} {self.verdict:<8} open={open_ms} stops={len(self.runs)} inside={self.inside} "
                f"baseline={total(self.baseline)} afterRuns={total(self.after_runs)} settled={total(self.settled)} "
                f"{self.note}").rstrip()


def prepare(dev, app, case, args):
    """Pushes what the case opens; returns the script substitutions."""
    if case == "gallery":
        png = HERE / "__pycache__" / "q59.png"
        png.parent.mkdir(exist_ok=True)
        png.write_bytes(tiny_png())
        dev.adb("push", str(png), f"{DEVICE_DIR}/q59.png", check=True)
        png.unlink()
        return {"__IMAGE__": js(f"{DEVICE_DIR}/q59.png")}
    if case == "yolo":
        model = pathlib.Path(args.yolo_model)
        files = ("model.json", "model.ncnn.param", "model.ncnn.bin")
        if not all((model / f).is_file() for f in files):
            raise SkipCase(f"no YOLO model in {model}")
        dev.shell(f"mkdir -p {STAGING_DIR}")
        for f in files:
            dev.adb("push", str(model / f), f"{STAGING_DIR}/{f}", check=True)
        dev.shell(f"chmod 644 {STAGING_DIR}/*")
        app.run_as(f"mkdir -p {YOLO_DIR}")
        for f in files:
            app.run_as(f"cp {STAGING_DIR}/{f} {YOLO_DIR}/{f}")
        listed = app.run_as(f"ls {YOLO_DIR}")
        if not all(f in listed for f in files):
            raise walk.WalkError("the YOLO model was not copied into the host's files")
        data = app.run_as("pwd").strip() or f"/data/data/{walk.PACKAGE}"
        return {"__MODEL__": js(f"{data}/{YOLO_DIR}"), "__COMPONENT__": js(YOLO_COMPONENT)}
    if case == "epub":
        book = EPUB_SAMPLE
        if args.epub_chapters > 0:
            book = HERE / "__pycache__" / "q59.epub"
            book.parent.mkdir(exist_ok=True)
            book.write_bytes(large_epub(args.epub_chapters))
        dev.adb("push", str(book), f"{DEVICE_DIR}/q59.epub", check=True)
        if book != EPUB_SAMPLE:
            book.unlink()
        return {"__BOOK__": js(f"{DEVICE_DIR}/q59.epub")}
    if case == "autojs4":
        target = HERE / "__pycache__" / "q59-aj4.js"
        target.parent.mkdir(exist_ok=True)
        target.write_text(AUTOJS4_TARGET, encoding="utf-8")
        dev.adb("push", str(target), f"{DEVICE_DIR}/q59-aj4.js", check=True)
        target.unlink()
        return {"__TARGET__": js(f"{DEVICE_DIR}/q59-aj4.js")}
    return {}


class SkipCase(Exception):
    pass


def tiny_png():
    """A 2 x 2 opaque PNG."""
    import struct
    import zlib

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    raw = b"".join(b"\x00" + b"\x30\x80\xc0" * 2 for _ in range(2))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 2, 2, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def large_epub(chapters):
    """An EPUB 3 book of [chapters] short chapters: the plugin parses its package document while the opening call waits, which
    widens the part of the opening that a stop can land in (the runtime-epub sample opens in about 10 ms of that)."""
    import io
    import zipfile

    items = "".join(f'<item id="c{i}" href="c{i}.xhtml" media-type="application/xhtml+xml"/>' for i in range(chapters))
    spine = "".join(f'<itemref idref="c{i}"/>' for i in range(chapters))
    toc = "".join(f'<li><a href="c{i}.xhtml">Chapter {i}</a></li>' for i in range(chapters))
    page = '<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"{extra}><head><title>{title}</title></head><body>{body}</body></html>'
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr(zipfile.ZipInfo("mimetype"), "application/epub+zip", compress_type=zipfile.ZIP_STORED)
        z.writestr("META-INF/container.xml", '<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>')
        z.writestr("OEBPS/content.opf", '<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">q59-large</dc:identifier><dc:title>Q59</dc:title><dc:language>en</dc:language><meta property="dcterms:modified">2026-10-23T00:00:00Z</meta></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>' + items + '</manifest><spine>' + spine + '</spine></package>')
        z.writestr("OEBPS/nav.xhtml", page.format(extra=' xmlns:epub="http://www.idpf.org/2007/ops"', title="Contents", body=f'<nav epub:type="toc"><ol>{toc}</ol></nav>'))
        for i in range(chapters):
            z.writestr(f"OEBPS/c{i}.xhtml", page.format(extra="", title=f"Chapter {i}", body=f"<p>Chapter {i}</p>"))
    return buffer.getvalue()


def script_for(case, delay, subs):
    if case == "autojs4":
        source = AUTOJS4_SCRIPT
    else:
        opening, closing = OPENINGS[case]
        source = SESSION_SCRIPT.replace("__OPEN__", opening).replace("__CLOSE__", closing)
    source = source.replace("__DELAY__", str(delay))
    for key, value in subs.items():
        source = source.replace(key, value)
    return source


def sweep_delays(open_ms, steps):
    top = max(1.0, open_ms * 1.2)
    return sorted({round(top * i / max(1, steps - 1)) for i in range(steps)})


def run_case(case, dev, app, args):
    result = CaseResult(case)
    package = PLUGINS[case]
    if installed_version(dev, package) is None:
        result.verdict = "SKIP"
        result.note = f"{package} is not installed"
        return result
    try:
        if case != "autojs4":
            # A fresh plugin process (the AutoJs4 runtime is left alone: it carries an enabled accessibility service).
            dev.shell(f"am force-stop {package}")
        app.start_main(cold=True)
        subs = prepare(dev, app, case, args)
        result.baseline = connections(dev, package)
        if case == "autojs4":
            delays = list(AUTOJS4_DELAYS_MS)
        else:
            calibration = run_and_read(dev, app, f"q59-{case}-calibrate.js", script_for(case, -1, subs),
                                       lambda s: s.get("done"), timeout=180.0)
            if "closeError" in calibration:
                raise walk.WalkError(f"closing the calibration session failed: {calibration['closeError']}")
            result.open_ms = calibration["openMs"]
            delays = sweep_delays(result.open_ms, args.steps)
        for delay in delays:
            state = run_and_read(dev, app, f"q59-{case}-{delay}.js", script_for(case, delay, subs),
                                 lambda s: s.get("stopping") or s.get("openError") or s.get("error"), timeout=90.0)
            if state.get("error"):
                raise walk.WalkError(f"delay {delay}: {state['error']}")
            time.sleep(2.5)
            state["inside"] = case != "autojs4" and "returnedMs" not in state and "openError" not in state
            state["connections"] = connections(dev, package)
            result.runs.append(state)
        result.after_runs = connections(dev, package)
        time.sleep(args.settle)
        result.settled = connections(dev, package)
        left = held(result.baseline, result.settled)
        result.verdict = "HELD" if left > 0 else "RELEASED"
        result.note = f"held={left}" + (f" refused={result.refused}" if result.refused else "")
    except SkipCase as e:
        result.verdict = "SKIP"
        result.note = str(e)
    except (walk.WalkError, subprocess.CalledProcessError, KeyError, ValueError) as e:
        result.verdict = "FAIL"
        result.note = str(e)
    finally:
        dev.shell(f"am force-stop {walk.PACKAGE}")
        time.sleep(1)
    return result


def cleanup(dev, app):
    app.run_as(f"rm -rf {YOLO_DIR} {RESULT}")
    dev.shell(f"rm -rf {DEVICE_DIR} {STAGING_DIR}")


def evidence_path(directory, model, version_code, tag):
    stamp = datetime.date.today().strftime("%Y%m%d")
    return pathlib.Path(directory) / f"q59-{model}-{version_code}-{tag}-{stamp}.md"


def write_evidence(path, header, results):
    lines = ["# Q59 late plugin openings", ""]
    lines += [f"- {key}: {value}" for key, value in header.items()]
    lines += ["", "| Case | Verdict | Opening (ms) | Stops | Inside the opening | Baseline | After the stops | After settling |",
              "| --- | --- | --- | --- | --- | --- | --- | --- |"]
    for r in results:
        open_ms = "-" if r.open_ms is None else f"{r.open_ms:.0f}"
        lines.append(f"| {r.name} | {r.verdict} | {open_ms} | {len(r.runs)} | {r.inside} | {describe(r.baseline)} | "
                     f"{describe(r.after_runs)} | {describe(r.settled)} |")
    for r in results:
        lines += ["", f"## {r.name}", "", r.note or "-"]
        if r.runs:
            lines += ["", "| Delay (ms) | Stop at (ms) | Opening returned at (ms) | Inside | Connections | Opening failed |",
                      "| --- | --- | --- | --- | --- | --- |"]
            for run in r.runs:
                stop = run.get("stopMs")
                returned = run.get("returnedMs")
                lines.append(f"| {run['delay']} | {'-' if stop is None else f'{stop:.0f}'} | "
                             f"{'-' if returned is None else f'{returned:.0f}'} | {'yes' if run.get('inside') else 'no'} | "
                             f"{describe(run['connections'])} | {run.get('openError', '-')} |")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--cases", default=",".join(CASES))
    parser.add_argument("--steps", type=int, default=24)
    parser.add_argument("--settle", type=float, default=40.0)
    parser.add_argument("--yolo-model", default=str(DEFAULT_YOLO_MODEL))
    parser.add_argument("--epub-chapters", type=int, default=3000, help="chapters of the generated book (0: the runtime-epub sample)")
    parser.add_argument("--evidence", default=str(REPO / "docs" / "memory" / "evidence"))
    args = parser.parse_args()
    cases = [c.strip() for c in args.cases.split(",") if c.strip()]
    unknown = [c for c in cases if c not in CASES]
    if unknown:
        parser.error(f"unknown cases: {unknown}")

    dev = walk.Device(args.serial)
    app = walk.App(dev, None)
    dev.shell(f"mkdir -p {DEVICE_DIR}")
    model = dev.prop("ro.product.model")
    version_code = installed_version(dev, walk.PACKAGE)
    header = {
        "Device": f"{model} ({args.serial}), Android {dev.prop('ro.build.version.release')}",
        "Host": version_code,
        "Tag": args.tag,
        "Date": datetime.datetime.now().strftime("%Y-%m-%d %H:%M"),
        "Plugins": ", ".join(f"{c} {installed_version(dev, PLUGINS[c]) or 'not installed'}" for c in cases),
        "Sweep": f"{args.steps} delays from 0 to 1.2 x the warm opening (AutoJs4: {', '.join(map(str, AUTOJS4_DELAYS_MS))} ms "
                 f"after its engine exists), settle {args.settle:.0f} s",
        "EPUB book": f"generated, {args.epub_chapters} chapters" if args.epub_chapters > 0 else "runtime-epub sample",
    }
    log(f"device {header['Device']}, host {version_code}, tag {args.tag}")
    results = []
    try:
        for case in cases:
            result = run_case(case, dev, app, args)
            results.append(result)
            log(result.line())
    finally:
        cleanup(dev, app)
    code = version_code.split("(")[-1].rstrip(")") if version_code else "unknown"
    path = evidence_path(args.evidence, model.replace(" ", "_"), code, args.tag)
    write_evidence(path, header, results)
    log(f"evidence: {path.relative_to(REPO) if path.is_relative_to(REPO) else path}")
    return 1 if any(r.verdict == "FAIL" for r in results) else 0


if __name__ == "__main__":
    sys.exit(main())
