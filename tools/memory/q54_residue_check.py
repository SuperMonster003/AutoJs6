"""Checks on a device the two bounded Activity residues of docs/memory/ROADMAP.md Q54 (server mode, cancelled download).

Usage: py -3.12 -I -u tools/memory/q54_residue_check.py -s SERIAL [--cases server,download] [--tag TAG]
    [--evidence DIR] [--analysis-wait 300] [--port 18354]

Each case runs a "ui" script in the host (pushed to /sdcard/Download/aj6-q54 and started through RunIntentActivity, as
ui_leak_walk.py does); its Activity (ScriptExecuteActivity) plays the caller, then the script finishes it:
- server: builds the server mode tool (JsonSocketServerTool) with the script Activity and starts the server through it, the
  way the drawer does with MainActivity. Skipped when the server already runs (the start would reuse it). After the leak
  check a second script stops the server and restores its "normally closed" state.
- download: shows a download with progress dialog for the script Activity (DownloadManager.downloadWithProgress) from a
  local server on this computer (adb reverse; the server sends the headers and part of the body, then stalls), and cancels
  it with the "Cancel download" button of the progress dialog (performClick on the host's own dialog from the script).
  The script reports the active download tasks of the process-level manager before and after the cancel.

No window of another app is tapped. After each case the script forces collections in the app and reads the LeakCanary log:
the script Activity must be watched and nothing may stay retained. When an object stays retained, the app is sent to the
background so that LeakCanary analyzes a heap dump, and each leak is reported with whether its trace passes through the
Q54 roots. LeakCanary's "Dump heap automatically" switch is turned on for the check and restored afterwards (the app is
stopped for both).

Output: one PASS / LEAK / FAIL / SKIP line per case with the key numbers. The evidence (numbers and analysis texts) goes
to --evidence (default docs/memory/evidence/) as q54-<model>-<versionCode>-<tag>-<date>.md. The exit code is 0 when
every case passes or is skipped, 2 for a leak and 1 for a failure.
"""
import argparse
import datetime
import json
import pathlib
import re
import socket
import sys
import threading
import time

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import ui_leak_walk as walk  # noqa: E402

DEVICE_DIR = "/sdcard/Download/aj6-q54"
RESULT = "files/q54-{}.json"
CASES = ("server", "download")
MARKERS = {
    "server": ("JsonSocketServerTool", "enableLocalServer"),
    "download": ("DownloadManager",),
}

SERVER_SCRIPT = r'''"ui";
ui.layout(<frame><text text="Q54 server check" gravity="center"/></frame>);
let Tool = org.autojs.autojs.app.tool.JsonSocketServerTool;
let app = context.getApplicationContext();
let out = new java.io.File(app.getFilesDir(), "q54-server.json").getPath();
let probe = new Tool(app);
let state = { wasRunning: probe.isConnected(), normallyClosed: probe.isNormallyClosed() };
if (!state.wasRunning) {
    let caller = activity;
    new Tool(caller).connect();
}
setTimeout(() => {
    state.running = probe.isConnected();
    files.write(out, JSON.stringify(state));
    ui.finish();
}, 2500);
'''

SERVER_STOP_SCRIPT = r'''let Tool = org.autojs.autojs.app.tool.JsonSocketServerTool;
let app = context.getApplicationContext();
let out = new java.io.File(app.getFilesDir(), "q54-server-stop.json").getPath();
let state = JSON.parse(files.read(new java.io.File(app.getFilesDir(), "q54-server.json").getPath()));
let tool = new Tool(app);
if (!state.wasRunning) {
    tool.disconnect();
    tool.setNormallyClosed(state.normallyClosed);
}
sleep(1000);
files.write(out, JSON.stringify({ running: tool.isConnected(), normallyClosed: tool.isNormallyClosed() }));
'''

DOWNLOAD_SCRIPT = r'''"ui";
ui.layout(<frame><text text="Q54 download check" gravity="center"/></frame>);
let DialogAction = com.afollestad.materialdialogs.DialogAction;
let Consumer = io.reactivex.functions.Consumer;
let app = context.getApplicationContext();
let out = new java.io.File(app.getFilesDir(), "q54-download.json").getPath();
let target = new java.io.File(app.getCacheDir(), "q54-download.bin").getPath();
let dm = org.autojs.autojs.network.download.DownloadManager.getInstance();
let tasks = dm.getClass().getDeclaredField("mActiveTasks");
tasks.setAccessible(true);
let dialogField = dm.getClass().getDeclaredField("mProgressDialog");
dialogField.setAccessible(true);
let state = { events: [] };
let caller = activity;
dm.downloadWithProgress(caller, "__URL__", target).subscribe(
    new Consumer({ accept: (f) => state.events.push("next " + caller) }),
    new Consumer({ accept: (e) => state.events.push("error " + caller) })
);
setTimeout(() => {
    state.activeBefore = tasks.get(dm).size();
    let dialog = dialogField.get(dm);
    state.dialogShown = dialog != null && dialog.isShowing();
    if (dialog != null) dialog.getActionButton(DialogAction.POSITIVE).performClick();
    setTimeout(() => {
        state.activeAfter = tasks.get(dm).size();
        state.dialogAfter = dialogField.get(dm) != null;
        files.write(out, JSON.stringify(state));
        ui.finish();
    }, 1500);
}, 3000);
'''


def log(text=""):
    print(text, flush=True)


class StallingServer:
    """Answers each request with headers and part of a large body, then sends nothing more until the client goes away."""

    def __init__(self, port):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind(("127.0.0.1", port))
        self.sock.listen(8)
        self.requests = 0
        self.clients = []
        threading.Thread(target=self._accept, daemon=True).start()

    def _accept(self):
        while True:
            try:
                conn, _ = self.sock.accept()
            except OSError:
                return
            self.clients.append(conn)
            threading.Thread(target=self._serve, args=(conn,), daemon=True).start()

    def _serve(self, conn):
        try:
            conn.settimeout(10)
            data = b""
            while b"\r\n\r\n" not in data:
                chunk = conn.recv(4096)
                if not chunk:
                    return
                data += chunk
            self.requests += 1
            conn.sendall(b"HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n"
                         b"Content-Length: 104857600\r\n\r\n" + b"\0" * 65536)
            conn.settimeout(None)
            while conn.recv(4096):
                pass
        except OSError:
            pass

    def close(self):
        self.sock.close()
        for conn in self.clients:
            try:
                conn.close()
            except OSError:
                pass


class CaseResult:
    def __init__(self, name):
        self.name = name
        self.state = {}
        self.watched = []
        self.retained = 0
        self.blocks = []
        self.leaks = []
        self.errors = []
        self.note = ""
        self.skipped = ""

    @property
    def rooted(self):
        """Leaks whose trace passes through the Q54 root of this case."""
        return [l for l in self.leaks if any(m in line for line in l[4] for m in MARKERS[self.name])]

    @property
    def status(self):
        if self.skipped:
            return "SKIP"
        if self.errors:
            return "FAIL"
        if self.retained or self.leaks or self.state.get("activeAfter", 0):
            return "LEAK"
        if "ScriptExecuteActivity" not in self.watched:
            return "FAIL"
        return "PASS"


def push_and_run(dev, name, source):
    local = HERE / "__pycache__" / name
    local.parent.mkdir(exist_ok=True)
    local.write_text(source, encoding="utf-8")
    remote = f"{DEVICE_DIR}/{name}"
    dev.adb("push", str(local), remote, check=True)
    local.unlink()
    dev.shell(f"am start -n {walk.PACKAGE}/{walk.RUN_ACTIVITY} -e path {remote}")


def read_result(app, name, timeout=30.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        text = app.run_as(f"cat {RESULT.format(name)}").strip()
        if text.startswith("{"):
            return json.loads(text)
        time.sleep(1)
    raise walk.WalkError(f"the {name} script wrote no result within {timeout:.0f} s")


def wait_script_gone(dev, timeout=15.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if dev.top_activity() != "ScriptExecuteActivity":
            return
        time.sleep(0.5)
    raise walk.WalkError("the script Activity did not finish")


def run_case(name, dev, app, leaklog, args):
    result = CaseResult(name)
    app.run_as(f"rm -f {RESULT.format(name)}")
    start = leaklog.mark()
    since_ms = walk.device_epoch_ms(dev)
    server = None
    try:
        if name == "server":
            push_and_run(dev, "q54-server.js", SERVER_SCRIPT)
        else:
            server = StallingServer(args.port)
            dev.adb("reverse", f"tcp:{args.port}", f"tcp:{args.port}", check=True)
            url = f"http://127.0.0.1:{args.port}/q54.bin"
            push_and_run(dev, "q54-download.js", DOWNLOAD_SCRIPT.replace("__URL__", url))
        app.check_foreign_window()
        result.state = read_result(app, name)
        if name == "server" and result.state.get("wasRunning"):
            result.skipped = "the server was already running, so the start reused it"
            return result
        if name == "server" and not result.state.get("running"):
            result.errors.append("the server did not start")
        if name == "download":
            result.state["requests"] = server.requests
            if result.state.get("activeBefore") != 1 or not result.state.get("dialogShown"):
                result.errors.append("the download was not pending with its progress dialog before the cancel")
        wait_script_gone(dev)
    except walk.WalkError as e:
        result.errors.append(str(e))
        return result
    finally:
        if server is not None:
            dev.adb("reverse", "--remove", f"tcp:{args.port}")
            server.close()
    lines = walk.settle(app, leaklog, start)
    result.watched = [m.group(1).rsplit(".", 1)[-1] for l in lines for m in [walk.WATCH_RE.search(l)] if m]
    result.retained = walk.retained_now(lines)
    blocks = [b for b in walk.analysis_blocks(lines) if walk.dump_timestamp(b) >= since_ms]
    if result.retained > 0 and not blocks:
        blocks, result.note = walk.collect_analysis(dev, app, leaklog, start, since_ms, args.analysis_wait)
    result.blocks = blocks
    for block in blocks:
        result.leaks += walk.parse_leaks(block)
    if blocks:
        result.retained = 0
    return result


def stop_server(dev, app):
    app.run_as("rm -f files/q54-server-stop.json")
    push_and_run(dev, "q54-server-stop.js", SERVER_STOP_SCRIPT)
    return read_result(app, "server-stop")


def describe_state(result):
    s = result.state
    if result.name == "server":
        return f"server started {s.get('running')}, normally closed before {s.get('normallyClosed')}"
    return (f"requests {s.get('requests')}, dialog shown {s.get('dialogShown')}, active tasks "
            f"{s.get('activeBefore')} -> {s.get('activeAfter')} after the cancel, events {len(s.get('events', []))}")


def write_evidence(path, header, results):
    out = [f"# Q54 residue check: {header['model']} (API {header['sdk']}), host debug {header['version']}, {header['time']}",
           "", f"- Command: `{header['command']}`", f"- LeakCanary: {header['leakcanary']}", "",
           "| case | state | watched | retained | leaks (Q54 root) | result |", "| --- | --- | --- | --- | --- | --- |"]
    for r in results:
        out.append(f"| {r.name} | {r.skipped or describe_state(r)} | {', '.join(r.watched) or '-'} | {r.retained} | "
                   f"{len(r.leaks)} ({len(r.rooted)}) | {r.status} |")
    for r in results:
        if not (r.blocks or r.errors or r.note):
            continue
        out += ["", f"## {r.name}", ""]
        out += [f"- error: {e}" for e in r.errors]
        if r.note:
            out.append(f"- LeakCanary: {r.note}")
        out += [f"- {walk.summary(l)}" for l in r.leaks]
        for block in r.blocks:
            out += ["", "```text", *[l.replace("\u200b", "") for l in block], "```"]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(out) + "\n", encoding="utf-8")


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("-s", "--serial", required=True)
    ap.add_argument("--cases", default=",".join(CASES))
    ap.add_argument("--tag", default="")
    ap.add_argument("--evidence", default=str(walk.ROOT / "docs" / "memory" / "evidence"))
    ap.add_argument("--analysis-wait", type=float, default=300.0, help="seconds to wait for a LeakCanary analysis")
    ap.add_argument("--port", type=int, default=18354, help="port of the stalling download server (adb reverse)")
    args = ap.parse_args()
    cases = [c.strip() for c in args.cases.split(",") if c.strip()]
    if any(c not in CASES for c in cases):
        sys.exit(f"cases: {', '.join(CASES)}")

    dev = walk.Device(args.serial)
    dev.shell(f"mkdir -p {DEVICE_DIR}")
    package_info = dev.shell(f"dumpsys package {walk.PACKAGE}")
    m = re.search(r"versionCode=(\d+)", package_info)
    if not m or "DEBUGGABLE" not in package_info:
        sys.exit("a debug build of the host must be installed (LeakCanary and run-as need one)")
    version_code = m.group(1)
    model = dev.prop("ro.product.model").replace(" ", "_")
    sdk = dev.prop("ro.build.version.sdk")

    leaklog = walk.LeakLog(dev)
    app = walk.App(dev, leaklog)
    original = app.dump_setting()
    if original is None:
        app.start_main(cold=True)
        original = app.dump_setting()
    if original != "true" and not app.set_dump_setting("true"):
        sys.exit("could not turn on LeakCanary's automatic heap dumps")
    start = leaklog.mark()
    app.start_main(cold=True)
    setup = [m.group(1) for l in leaklog.poll()[start:] for m in [walk.SETUP_RE.search(l)] if m]
    if not setup or setup[-1] != "enabled":
        log("FAIL LeakCanary is not enabled (debug build with LeakCanary on in Memory diagnostics needed)")
        return 1
    log(f"device {model} API {sdk}, host {version_code}, LeakCanary enabled, automatic heap dumps on (was {original})")

    results = []
    try:
        for name in cases:
            result = run_case(name, dev, app, leaklog, args)
            results.append(result)
            log(f"{result.status:<4} {name:<8} {result.skipped or describe_state(result)}; watched "
                f"{', '.join(result.watched) or '-'}; retained {result.retained}; leaks {len(result.leaks)} "
                f"({len(result.rooted)} through the Q54 root)")
            for leak in result.leaks:
                log(f"      {walk.summary(leak)}")
            for e in result.errors:
                log(f"      error {e}")
            if result.note:
                log(f"      LeakCanary: {result.note}")
            if name == "server" and not result.skipped and result.state:
                stopped = stop_server(dev, app)
                log(f"      server stopped: {not stopped.get('running')}, normally closed restored to "
                    f"{stopped.get('normallyClosed')}")
                if stopped.get("running") or stopped.get("normallyClosed") != result.state.get("normallyClosed"):
                    result.errors.append("the server state could not be restored")
    finally:
        app.run_as("rm -f files/q54-server.json files/q54-server-stop.json files/q54-download.json "
                   "cache/q54-download.bin")
        dev.shell(f"rm -rf {DEVICE_DIR}")
        if original != "true":
            restored = app.set_dump_setting(original)
            log(f"automatic heap dumps restored to {original} (the app was stopped for that)" if restored
                else f"FAIL automatic heap dumps could not be restored to {original}; restore it by hand")
        header = {
            "model": model, "sdk": sdk, "version": version_code, "time": time.strftime("%Y-%m-%d %H:%M"),
            "command": "py -3.12 -I -u tools/memory/q54_residue_check.py " + " ".join(sys.argv[1:]),
            "leakcanary": f"enabled; automatic heap dumps on during the check (was {original})",
        }
        stamp = datetime.date.today().strftime("%Y%m%d")
        out = pathlib.Path(args.evidence) / f"q54-{model}-{version_code}{'-' + args.tag if args.tag else ''}-{stamp}.md"
        if results:
            write_evidence(out, header, results)
            log(f"evidence: {out.relative_to(walk.ROOT) if out.is_relative_to(walk.ROOT) else out}")

    if any(r.status == "FAIL" for r in results):
        return 1
    return 2 if any(r.status == "LEAK" for r in results) else 0


if __name__ == "__main__":
    sys.exit(main())
