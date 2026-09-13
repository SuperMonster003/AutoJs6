"""Observe real WebViews on a disposable emulator. Does not install APKs or change compat / grants.

Starts a UI script (interrupting the host), a loopback-only HTTP fixture and a temporary adb
DevTools forward. Uses the Python standard library. Every run requires a fresh output directory.
"""
import argparse
import base64
import hashlib
import http.server
import json
from pathlib import Path
import re
import secrets
import socket
import struct
import subprocess
import threading
import time
import urllib.parse
import urllib.request
import uuid

from physical_device import Device, snapshot, utc_now, write_json

HOST = "org.autojs.autojs6"
HTML_PACKAGE = "io.github.supermonster003.autojs6.plugin.htmlpreviewer"
DOCS_PACKAGE = "io.github.supermonster003.autojs6.plugin.offlinedocs"
CUSTOM_UA = "AutoJs6-SDK37-WebView-Probe/1.0"
SUFFIX = " AutoJs6-SDK37-Probe/1.0"
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


class DevTools:
    """Small local CDP client: masked client frames, fragmentation and ping handling."""
    def __init__(self, url):
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme != "ws" or parsed.hostname not in ("localhost", "127.0.0.1"):
            raise ValueError("DevTools must use the temporary local adb forward")
        self.sock = socket.create_connection((parsed.hostname, parsed.port), timeout=10)
        self.stream = self.sock.makefile("rb")
        self.sequence = 0
        key = base64.b64encode(secrets.token_bytes(16)).decode()
        request = (f"GET {parsed.path} HTTP/1.1\r\nHost: {parsed.netloc}\r\nUpgrade: websocket\r\n"
                   f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n")
        self.sock.sendall(request.encode())
        status = self.stream.readline()
        headers = {}
        while True:
            line = self.stream.readline()
            if line in (b"\r\n", b""):
                break
            name, value = line.decode().split(":", 1)
            headers[name.lower()] = value.strip()
        expected = base64.b64encode(hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest()).decode()
        if not status.startswith(b"HTTP/1.1 101 ") or headers.get("sec-websocket-accept") != expected:
            self.close()
            raise RuntimeError("DevTools WebSocket upgrade failed: " + status.decode())

    def close(self):
        self.stream.close()
        self.sock.close()

    def exact(self, size):
        value = self.stream.read(size)
        if len(value) != size:
            raise RuntimeError("DevTools closed before returning a complete message")
        return value

    def send(self, payload, opcode=1):
        mask = secrets.token_bytes(4)
        size = len(payload)
        header = bytes([0x80 | opcode, 0x80 | min(size, 126)])
        if size >= 126:
            if size > 65535:
                raise ValueError("Oversized DevTools request")
            header += struct.pack("!H", size)
        self.sock.sendall(header + mask + bytes(c ^ mask[i % 4] for i, c in enumerate(payload)))

    def receive(self):
        chunks = []
        while True:
            first, second = self.exact(2)
            size = second & 127
            if size == 126:
                size = struct.unpack("!H", self.exact(2))[0]
            elif size == 127:
                size = struct.unpack("!Q", self.exact(8))[0]
            if second & 128 or size > 2_000_000:
                raise RuntimeError("Unexpected DevTools frame")
            payload = self.exact(size)
            opcode = first & 15
            if opcode == 8:
                raise RuntimeError("DevTools closed the connection")
            if opcode == 9:
                self.send(payload, 10)
                continue
            if opcode == 10:
                continue
            if opcode not in (0, 1):
                raise RuntimeError("Unexpected DevTools opcode")
            chunks.append(payload)
            if first & 128:
                return json.loads(b"".join(chunks))

    def evaluate(self, expression):
        self.sequence += 1
        self.send(json.dumps({"id": self.sequence, "method": "Runtime.evaluate", "params": {
            "expression": expression, "returnByValue": True, "awaitPromise": True,
        }}).encode())
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            result = self.receive()
            if result.get("id") != self.sequence:
                continue
            if "error" in result or "exceptionDetails" in result.get("result", {}):
                raise RuntimeError("DevTools evaluation failed: " + json.dumps(result))
            value = result.get("result", {}).get("result", {})
            if "value" not in value:
                raise RuntimeError("DevTools evaluation returned no value")
            return value["value"]
        raise RuntimeError("DevTools evaluation timed out")


def html_fixture(case_id):
    return f'''<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<title>SDK 37 WebView probe</title><style>body{{font-family:sans-serif;margin:24px;background:#fff;color:#142b24}}
#probe{{background:#147d64;color:white;padding:24px;border-radius:12px;min-height:100px}}a{{color:#075ec9}}</style></head>
<body data-sdk37-case="{case_id}"><h1>SDK 37 WebView</h1><div id="probe">Rendered HTML / 中文 / 日本語 / العربية</div>
<p>Default, custom and appended User-Agent strings.</p><a id="local-link" href="#details">Local navigation</a>
<p id="details">Fixture {case_id}</p></body></html>'''


def reduced_ua(ua):
    return bool(re.search(r"Linux; Android 10; K; wv\)", ua) and re.search(r"Chrome/\d+\.0\.0\.0\b", ua))


def validate_observation(route, mode, expected_reduced, receipt, dom, requests, case_id):
    errors = []
    if receipt.get("caseId") != case_id or not receipt.get("complete") or receipt.get("ok") is not True:
        errors.append("Missing successful script receipt for this case")
    ua = dom.get("ua", "")
    if not ua or dom.get("ready") != "complete" or dom.get("width", 0) <= 0 or dom.get("height", 0) <= 0:
        errors.append("No completed, measured WebView document")
    if route == "ui":
        expected = CUSTOM_UA if mode == "custom" else receipt.get("defaultUa", "") + (SUFFIX if mode == "suffix" else "")
        if not expected or ua != expected or receipt.get("settingsUa") != expected:
            errors.append("DOM / WebSettings User-Agent mismatch")
        if reduced_ua(receipt.get("defaultUa", "")) != expected_reduced:
            errors.append("Default User-Agent reduction differs from the explicit expectation")
        page_requests = [r for r in requests if r["path"] == "/" + case_id]
        if not page_requests or any(r["ua"] != expected for r in page_requests):
            errors.append("Missing or mismatched HTTP User-Agent")
    elif reduced_ua(ua) != expected_reduced:
        errors.append("Document User-Agent reduction differs from the explicit expectation")
    if route in ("ui", "html"):
        if dom.get("caseId") != case_id or dom.get("probeWidth", 0) <= 0 or dom.get("probeHeight", 0) <= 0:
            errors.append("Fresh fixture did not render")
        if dom.get("probeColor") != "rgb(20, 125, 100)":
            errors.append("Fixture stylesheet did not render")
    elif "AutoJs6" not in dom.get("text", "") or dom.get("linkCount", 0) < 5:
        errors.append("Offline documentation content did not render")
    return errors


DOM_EXPRESSION = '''(() => {
 const b = document.body, p = document.getElementById('probe');
 return {url: location.href, title: document.title, ready: document.readyState, ua: navigator.userAgent,
   uaData: navigator.userAgentData ? navigator.userAgentData.toJSON() : null,
   fileSystemAccess: typeof window.showOpenFilePicker, secureContext: window.isSecureContext,
   width: b ? b.getBoundingClientRect().width : 0, height: b ? b.getBoundingClientRect().height : 0,
   caseId: b && b.getAttribute('data-sdk37-case'), probeWidth: p ? p.getBoundingClientRect().width : 0,
   probeHeight: p ? p.getBoundingClientRect().height : 0, probeColor: p ? getComputedStyle(p).backgroundColor : null,
   linkCount: document.links.length, text: b ? b.innerText.slice(0, 2000) : ''};
})()'''


def checked(result):
    if result["exitCode"] != 0:
        raise RuntimeError(json.dumps(result))
    return result["stdout"].strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--expected-target", type=int, required=True)
    parser.add_argument("--expected-webview-target", type=int, required=True,
                        help="HTML Previewer uses its own target, not the host's")
    parser.add_argument("--expected-reduced", choices=("true", "false"), required=True)
    parser.add_argument("--route", choices=("ui", "docs", "html"), default="ui")
    parser.add_argument("--ua-mode", choices=("default", "custom", "suffix", "reset"), default="default")
    parser.add_argument("--docs-page", choices=("index.html", "app.html"), default="index.html")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+|127\.0\.0\.1:\d+", args.serial):
        parser.error("Use a disposable emulator, by emulator serial or its explicit loopback adb port")
    if args.route != "ui" and args.ua_mode != "default":
        parser.error("UA attributes are only exercised through ui.webview")
    args.output.mkdir(parents=True, exist_ok=False)
    device = Device(args.adb, args.serial)
    device.require_online()
    identity = device.identity()
    if not identity["emulator"]:
        parser.error("The selected device does not identify itself as an emulator")
    package = HTML_PACKAGE if args.route == "html" else HOST
    packages = sorted({HOST, package, *([DOCS_PACKAGE] if args.route == "docs" else [])})
    result = {"started": utc_now(), "caseId": uuid.uuid4().hex, "route": args.route, "uaMode": args.ua_mode,
              "device": identity,
              "expectedTarget": args.expected_target, "expectedWebViewTarget": args.expected_webview_target,
              "expectedReduced": args.expected_reduced == "true", "success": False}
    forward = None
    server = None
    client = None
    requests = []
    try:
        snapshot(device, packages, args.output / "before")
        compat = checked(device.shell("dumpsys", "platform_compat"))
        result["uaCompat"] = [line.strip() for line in compat.splitlines() if "name=ENABLE_USER_AGENT_REDUCTION;" in line]
        result["packageTargets"] = {}
        for name, target in ((HOST, args.expected_target), (package, args.expected_webview_target)):
            state = (args.output / "before" / (name + "-package.txt")).read_text(encoding="utf-8")
            actual = re.search(r"\btargetSdk=(\d+)\b", state)
            if not actual or int(actual[1]) != target or "DEBUGGABLE" not in state:
                raise RuntimeError("Expected an installed debuggable APK with target " + str(target) + ": " + name)
            result["packageTargets"][name] = int(actual[1])
        if args.route == "ui" and int(identity["sdk"]) >= 37 and args.expected_target >= 37:
            if "android.permission.ACCESS_LOCAL_NETWORK: granted=true" not in state:
                raise RuntimeError("Grant the test host local-network permission before using the emulator host alias")
        result["apkSha256"] = {}
        for name in packages:
            paths = checked(device.shell("pm", "path", name)).splitlines()
            result["apkSha256"][name] = [checked(device.shell("sha256sum", p.removeprefix("package:"))).split()[0] for p in paths]
        result["webviewProvider"] = checked(device.shell("dumpsys", "webviewupdate"))
        case_id = result["caseId"]
        page = html_fixture(case_id)
        class Handler(http.server.BaseHTTPRequestHandler):
            def log_message(self, *unused):
                pass
            def do_GET(self):
                requests.append({"time": utc_now(), "path": self.path, "ua": self.headers.get("User-Agent"),
                                 "clientHints": {k: v for k, v in self.headers.items() if k.lower().startswith("sec-ch-")}})
                body = page.encode("utf-8") if self.path == "/" + case_id else b""
                self.send_response(200 if body else 404)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                self.wfile.write(body)
        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        config = {"caseId": case_id, "route": args.route, "uaMode": args.ua_mode,
                  "url": f"http://10.0.2.2:{server.server_port}/{case_id}", "customUa": CUSTOM_UA,
                  "suffix": SUFFIX, "html": page, "htmlPackage": HTML_PACKAGE, "docsPage": args.docs_page}
        script = Path(__file__).with_name("smoke-webview.js").read_text(encoding="utf-8").replace(
            "__SDK37_WEBVIEW_CONFIG__", json.dumps(config, ensure_ascii=True))
        (args.output / "launched-script.js").write_text(script, encoding="utf-8")
        # A previous preview can remain above a stopped host in the same cross-UID task.
        # Each case owns a new task and stops the disposable previewer before launching.
        checked(device.shell("am", "force-stop", HOST))
        checked(device.shell("am", "force-stop", HTML_PACKAGE))
        launch = device.shell("am", "start", "-n", HOST + "/org.autojs.autojs.external.open.RunIntentActivity",
                              "-f", "0x10008000", "--es", "script", script)
        write_json(args.output / "launch.json", launch)
        checked(launch)
        deadline = time.monotonic() + 90
        receipt = None
        while time.monotonic() < deadline:
            raw = device.shell("run-as", HOST, "cat", f"files/sdk37-webview-{case_id}.json")
            if raw["exitCode"] == 0:
                receipt = json.loads(raw["stdout"])
                if receipt.get("complete"):
                    break
            time.sleep(1)
        result["receipt"] = receipt
        if not receipt or not receipt.get("ok"):
            raise RuntimeError("Script did not complete successfully: " + json.dumps(receipt))
        deadline = time.monotonic() + 30
        while True:
            process = device.shell("pidof", package)
            pid = process["stdout"].strip()
            if process["exitCode"] == 0 and pid.isdigit():
                break
            if time.monotonic() >= deadline:
                raise RuntimeError("Expected one started app process: " + json.dumps(process))
            time.sleep(0.5)
        forward = checked(device.call("forward", "tcp:0", "localabstract:webview_devtools_remote_" + pid))
        if not forward.isdigit():
            raise RuntimeError("Invalid DevTools forward")
        deadline = time.monotonic() + 60
        dom = {}
        selected_url = None
        while time.monotonic() < deadline:
            try:
                with OPENER.open(f"http://127.0.0.1:{forward}/json/list", timeout=5) as response:
                    targets = json.load(response)
                candidates = [t for t in targets if t.get("type") == "page" and (
                    case_id in t.get("url", "") if args.route == "ui" else
                    "offline-docs.autojs6.local/" in t.get("url", "") if args.route == "docs" else
                    t.get("url", "").startswith("https://appassets.androidplatform.net/html-document/"))]
                if len(candidates) == 1:
                    target_url = candidates[0]["webSocketDebuggerUrl"]
                    if target_url != selected_url:
                        if client:
                            client.close()
                        client = DevTools(target_url)
                        selected_url = target_url
                    dom = client.evaluate(DOM_EXPRESSION)
                    if dom.get("ready") == "complete" and (dom.get("caseId") == case_id if args.route != "docs" else "AutoJs6" in dom.get("text", "")):
                        break
            except (OSError, ValueError) as error:
                result["lastDevToolsConnectionError"] = str(error)
            time.sleep(1)
        result["dom"] = dom
        if client and dom.get("secureContext"):
            result["clientHints"] = client.evaluate("navigator.userAgentData ? navigator.userAgentData.getHighEntropyValues(['platformVersion', 'model', 'fullVersionList', 'architecture', 'bitness']) : null")
        result["requests"] = requests
        result["errors"] = validate_observation(args.route, args.ua_mode, result["expectedReduced"], receipt, dom, requests, case_id)
        if args.route == "docs" and not dom.get("url", "").endswith("/" + args.docs_page):
            result["errors"].append("Documentation did not reach the requested page")
        if client and dom.get("caseId") == case_id:
            result["anchorNavigation"] = client.evaluate("document.getElementById('local-link').click(); location.hash")
            if result["anchorNavigation"] != "#details":
                result["errors"].append("Local document navigation failed")
        windows = checked(device.shell("dumpsys", "window"))
        (args.output / "windows.txt").write_text(windows, encoding="utf-8")
        result["focusedWindow"] = next((line.strip() for line in windows.splitlines() if "mCurrentFocus=" in line), "")
        if package + "/" not in result["focusedWindow"] or "Application Not Responding" in result["focusedWindow"]:
            result["errors"].append("The tested page is not the focused window; inspect system dialogs and screen.png")
        elif client and args.route != "html":
            client.evaluate("new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve(true))))")
        else:
            # HTML Previewer disables document JavaScript, including animation callbacks.
            # Allow native rendering to settle, then retain the screenshot for visual review.
            time.sleep(0.3)
        capture = subprocess.run([args.adb, "-s", args.serial, "exec-out", "screencap", "-p"], capture_output=True, timeout=20)
        if capture.returncode != 0 or not capture.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("Screenshot capture failed")
        (args.output / "screen.png").write_bytes(capture.stdout)
        result["success"] = not result["errors"]
    except Exception as error:
        result["error"] = f"{type(error).__name__}: {error}"
    finally:
        if client:
            client.close()
        if forward and forward.isdigit():
            result["forwardCleanup"] = device.call("forward", "--remove", "tcp:" + forward)
            if result["forwardCleanup"]["exitCode"] != 0:
                result["success"] = False
        if server:
            server.shutdown()
            server.server_close()
        try:
            snapshot(device, packages, args.output / "after")
            logs = device.call("logcat", "-d", "-t", "1000", "-v", "threadtime", "chromium:I", "AndroidRuntime:E", "ActivityTaskManager:I", "*:S")
            (args.output / "logcat-tail.txt").write_text(logs["stdout"] + logs["stderr"], encoding="utf-8")
        except (OSError, ValueError, RuntimeError) as error:
            result["evidenceCaptureError"] = str(error)
            result["success"] = False
        result["ended"] = utc_now()
        write_json(args.output / "result.json", result)
    summary = {k: v for k, v in result.items() if k in ("caseId", "success", "errors", "error")}
    summary.update(ua=result.get("dom", {}).get("ua"), url=result.get("dom", {}).get("url"))
    print(json.dumps(summary))
    return 0 if result["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
