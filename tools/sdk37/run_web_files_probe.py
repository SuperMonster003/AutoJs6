"""Exercise the production ui.webview file chooser on an exclusively owned API 37 emulator.

Requires an installed debuggable host. Stops its running work, creates one uniquely
named Download document via adb, observes the selected package's existing compat
state and drives actual system UI. No installs, access grants or compat changes.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

from physical_device import Device, snapshot, utc_now, write_json
from run_framework_probe import checked, close_host_tasks
from run_webview_probe import DevTools, OPENER

HOST = "org.autojs.autojs6"
SCENARIOS = ("inputSelect", "inputCancel", "openSelect", "openCancel", "saveCancel", "directoryCancel")


def validate_chooser(row, enabled, name, content):
    """Require the observed gate, native result and browser content to agree."""
    scenario = row["name"]
    if scenario not in SCENARIOS:
        raise ValueError("Unexpected chooser scenario")
    kind = re.sub(r"Select$|Cancel$", "", scenario)
    dom = row["dom"]
    if dom.get("kind") != kind:
        raise ValueError("Browser result belongs to a different scenario")
    if not enabled and kind in ("save", "directory"):
        if (row.get("outcome") != "blocked-before-chooser" or row.get("callback") is not None
                or row.get("result") is not None or dom.get("error") != "AbortError"):
            raise ValueError("Disabled gate must reject before the native chooser")
        return
    callback, result = row["callback"], row["result"]
    mode = {"input": 0, "open": 0, "save": 3, "directory": 2}[kind]
    action = {"input": "GET_CONTENT", "open": "OPEN_DOCUMENT" if enabled else "GET_CONTENT",
              "save": "CREATE_DOCUMENT", "directory": "OPEN_DOCUMENT_TREE"}[kind]
    if (callback.get("mode") != mode or callback.get("permissionMode") != (0 if kind == "input" else 1)
            or callback.get("action") != "android.intent.action." + action):
        raise ValueError("Chooser mode / intent differs from the active gate")
    if scenario.endswith("Cancel"):
        cancelled = dom.get("cancelled") is True and dom.get("count") == 0 if kind == "input" else dom.get("error") == "AbortError"
        if (not cancelled or result.get("code") != 0 or result.get("uri") is not None
                or result.get("flags") != 0 or result.get("readGranted") is not False or result.get("writeGranted") is not False):
            raise ValueError("Cancellation returned a file or failed to reach the browser")
    else:
        write = enabled and kind == "open"
        if (dom.get("name") != name or dom.get("text") != content or result.get("code") != -1
                or not (result.get("uri") or "").startswith("content://")
                or result.get("flags") != (67 if write else 1) or result.get("readGranted") is not True
                or result.get("writeGranted") is not write or (kind == "open" and dom.get("permission") != "granted")):
            raise ValueError("Selected document / actual URI grants differ from the active gate")


def fixture(case_id):
    return '''<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{font:18px sans-serif;margin:20px}button,input{display:block;margin:20px 0;min-height:48px}</style>
<h2>File access CASE</h2><input type="file" id="input" accept="text/plain">
<button id="open">Open document</button><button id="save">Save document</button>
<button id="directory">Choose directory</button><pre id="status">Ready</pre>
<script>
window.receipts=[];
function report(value){receipts.push(value);document.querySelector('#status').textContent=JSON.stringify(value)}
document.querySelector('#input').onchange=async e=>{const f=e.target.files[0];report(f?{kind:'input',name:f.name,text:await f.text()}:{kind:'input',cancelled:true,event:'change',count:0})};
document.querySelector('#input').oncancel=e=>report({kind:'input',cancelled:true,event:'cancel',count:e.target.files.length});
for(const kind of ['open','save','directory'])document.getElementById(kind).onclick=async()=>{
 try{let h=kind==='open'?(await showOpenFilePicker({types:[{description:'Text',accept:{'text/plain':['.txt']}}]}))[0]:
 kind==='save'?await showSaveFilePicker({suggestedName:'sdk37-CASE.txt'}):await showDirectoryPicker();
 let r={kind,name:h.name,permission:await h.queryPermission({mode:'read'})};
 if(kind==='open')r.text=await (await h.getFile()).text();report(r)
 }catch(e){report({kind,error:e.name,message:e.message})}
};
</script>'''.replace("CASE", case_id)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--expected-target", type=int, choices=(36, 37), required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("Requires an explicit emulator console serial")
    args.output.mkdir(parents=True, exist_ok=False)
    case_id = uuid.uuid4().hex
    name = "sdk37-web-" + case_id + ".txt"
    content = "SDK37 WebView document " + case_id
    local = args.output / name
    local.write_text(content, encoding="utf-8")
    remote = "/sdcard/Download/" + name
    device = Device(args.adb, args.serial)
    result = {"caseId": case_id, "started": utc_now(), "success": False, "scenarios": []}
    client = None
    forward = None
    created = False
    launched = False

    def receipt():
        raw = device.shell("run-as", HOST, "cat", "files/sdk37-web-files-" + case_id + ".json")
        try:
            return json.loads(raw["stdout"])
        except json.JSONDecodeError:
            return {}

    def picker_nodes(label):
        xml_path = "/sdcard/sdk37-web-ui-" + case_id + ".xml"
        for attempt in range(3):
            dump = device.shell("uiautomator", "dump", "--compressed", xml_path, timeout=20)
            if dump["exitCode"] == 0 and "UI hierchary dumped to:" in dump["stdout"]:
                break
            write_json(args.output / (label + f"-dump-retry-{attempt}.json"), dump)
            time.sleep(1)
        else:
            raise RuntimeError("UI hierarchy capture failed")
        xml = checked(device.shell("cat", xml_path))
        checked(device.shell("rm", xml_path))
        (args.output / (label + ".xml")).write_text(xml, encoding="utf-8")
        return list(ET.fromstring(xml).iter("node"))

    def tap(node):
        left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds", "")))
        checked(device.shell("input", "tap", str((left + right) // 2), str((top + bottom) // 2)))

    try:
        device.require_online()
        result["device"] = device.identity()
        if not result["device"]["emulator"] or result["device"]["sdk"] != "37":
            raise RuntimeError("Requires an exclusively owned API 37 emulator")
        package = checked(device.shell("dumpsys", "package", HOST))
        if f"targetSdk={args.expected_target}" not in package or "DEBUGGABLE" not in package:
            raise RuntimeError("Host target or debuggability mismatch")
        snapshot(device, [HOST], args.output / "before")
        result["compatDefinition"] = [line.strip() for line in checked(device.shell("dumpsys", "platform_compat")).splitlines()
                                      if "name=ENABLE_FILE_SYSTEM_ACCESS;" in line]
        if (len(result["compatDefinition"]) != 1 or not re.match(
                r"ChangeId\(364980165; name=ENABLE_FILE_SYSTEM_ACCESS; enableSinceTargetSdk=37(?:;|\))", result["compatDefinition"][0])):
            raise RuntimeError("Unknown File System Access compatibility definition")
        override = re.search(r"; packageOverrides=\{([^}]*)\}", result["compatDefinition"][0])
        pairs = dict(re.findall(r"([\w.]+)=(true|false)(?:,|$)", override[1])) if override else {}
        fsa_enabled = pairs[HOST] == "true" if HOST in pairs else args.expected_target >= 37
        result["fsaEnabled"] = fsa_enabled
        apk = checked(device.shell("pm", "path", HOST)).splitlines()[0].removeprefix("package:")
        result["installedApkSha256"] = checked(device.shell("sha256sum", apk)).split()[0]
        if device.shell("test", "-e", remote)["exitCode"] != 1:
            raise RuntimeError("Fixture path already exists or cannot be inspected")
        checked(device.call("push", str(local), remote))
        created = True
        script = Path(__file__).with_name("smoke-web-files.js").read_text(encoding="utf-8").replace(
            "__SDK37_WEB_FILES_CONFIG__", json.dumps({"caseId": case_id, "html": fixture(case_id)}))
        (args.output / "script.js").write_text(script, encoding="utf-8")
        result["scriptSha256"] = hashlib.sha256(script.encode()).hexdigest()
        result["taskCleanup"] = close_host_tasks(device)
        checked(device.shell("am", "force-stop", HOST))
        launched = True
        checked(device.shell("am", "start", "-n", HOST + "/org.autojs.autojs.external.open.RunIntentActivity",
                             "-f", "0x10008000", "--es", "script", script, timeout=45))
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            native = receipt()
            if native.get("errors"):
                raise RuntimeError(json.dumps(native))
            if native.get("caseId") == case_id and native.get("target") == args.expected_target and native.get("ready") is True:
                break
            time.sleep(1)
        else:
            raise RuntimeError("WebView script did not become ready")
        pid = checked(device.shell("pidof", HOST))
        if not pid.isdigit():
            raise RuntimeError("Expected one host process")
        forward = checked(device.call("forward", "tcp:0", "localabstract:webview_devtools_remote_" + pid))
        if not forward.isdigit():
            raise RuntimeError("Expected an allocated DevTools TCP port")
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline:
            try:
                with OPENER.open(f"http://127.0.0.1:{forward}/json/list", timeout=5) as response:
                    pages = [page for page in json.load(response) if page.get("type") == "page"]
                result["devtoolsPages"] = pages
                if len(pages) == 1:
                    client = DevTools(pages[0]["webSocketDebuggerUrl"])
                    break
            except OSError:
                pass
            time.sleep(1)
        if client is None:
            raise RuntimeError("Missing fixture DevTools page")
        result["page"] = client.evaluate("({url:location.href,text:document.body.textContent,secure:isSecureContext,ua:navigator.userAgent,open:typeof showOpenFilePicker,save:typeof showSaveFilePicker,directory:typeof showDirectoryPicker})")
        if not result["page"]["secure"] or case_id not in result["page"]["text"]:
            raise RuntimeError("Fixture identity / secure context mismatch")
        for scenario in SCENARIOS:
            kind = re.sub(r"Select$|Cancel$", "", scenario)
            callbacks_before = len(receipt().get("callbacks", []))
            deadline = time.monotonic() + 15
            while time.monotonic() < deadline:
                focused = next((line for line in checked(device.shell("dumpsys", "window")).splitlines() if "mCurrentFocus=" in line), "")
                if "org.autojs.autojs.execution.ScriptExecuteActivity" in focused:
                    break
                time.sleep(0.2)
            else:
                raise RuntimeError("Host did not regain focus before " + scenario)
            time.sleep(0.5)
            # DOM locates the control; an actual adb tap supplies transient user activation.
            point = client.evaluate("(()=>{let r=document.getElementById(" + json.dumps(kind) + ").getBoundingClientRect();return {x:(r.left+r.right)/2,y:(r.top+r.bottom)/2,dpr:devicePixelRatio}})()")
            nodes = picker_nodes(scenario + "-host")
            web_bounds = {node.get("bounds", "") for node in nodes if node.get("class") == "android.webkit.WebView"}
            if len(web_bounds) != 1:
                raise RuntimeError("Expected one WebView viewport (native and virtual nodes may overlap)")
            bounds = list(map(int, re.findall(r"\d+", web_bounds.pop())))
            checked(device.shell("input", "tap", str(round(bounds[0] + point["x"] * point["dpr"])),
                                 str(round(bounds[1] + point["y"] * point["dpr"]))))
            if not fsa_enabled and kind in ("save", "directory"):
                deadline = time.monotonic() + 15
                while time.monotonic() < deadline:
                    dom = client.evaluate("receipts")
                    if len(dom) > len(result["scenarios"]):
                        break
                    time.sleep(0.2)
                native = receipt()
                if (len(dom) != len(result["scenarios"]) + 1 or dom[-1].get("kind") != kind
                        or dom[-1].get("error") != "AbortError" or len(native.get("callbacks", [])) != callbacks_before):
                    raise RuntimeError("Disabled gate did not reject unsupported chooser mode")
                result["scenarios"].append({"name": scenario, "outcome": "blocked-before-chooser",
                                             "dom": dom[-1], "callback": None, "result": None})
                continue
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                native = receipt()
                if len(native.get("callbacks", [])) > callbacks_before:
                    break
                time.sleep(0.2)
            else:
                raise RuntimeError("File chooser callback missing: " + scenario + " " + json.dumps(client.evaluate("receipts")))
            deadline = time.monotonic() + 15
            while time.monotonic() < deadline:
                focus = next((line.strip() for line in checked(device.shell("dumpsys", "window")).splitlines() if "mCurrentFocus=" in line), "")
                if "documentsui" in focus:
                    break
                time.sleep(0.2)
            else:
                raise RuntimeError("Expected the system document picker: " + focus)
            nodes = picker_nodes(scenario + "-chooser")
            capture = subprocess.run([args.adb, "-s", args.serial, "exec-out", "screencap", "-p"], capture_output=True, timeout=20)
            if capture.returncode != 0 or not capture.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
                raise RuntimeError("Picker screenshot failed")
            (args.output / (scenario + ".png")).write_bytes(capture.stdout)
            if scenario.endswith("Cancel"):
                checked(device.shell("input", "keyevent", "KEYCODE_BACK"))
            else:
                matches = [node for node in nodes if node.get("text") == name]
                if not matches:
                    menus = [node for node in nodes if node.get("content-desc") == "Show roots"]
                    if len(menus) != 1:
                        raise RuntimeError("Cannot locate document picker roots")
                    tap(menus[0])
                    nodes = picker_nodes(scenario + "-roots")
                    downloads = [node for node in nodes if node.get("text") == "Downloads"]
                    if len(downloads) != 1:
                        raise RuntimeError("Cannot uniquely locate Downloads")
                    tap(downloads[0])
                    nodes = picker_nodes(scenario + "-downloads")
                    matches = [node for node in nodes if node.get("text") == name]
                if len(matches) != 1:
                    raise RuntimeError("Cannot uniquely locate the fixture document")
                tap(matches[0])
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                dom = client.evaluate("receipts")
                native = receipt()
                if len(dom) > len(result["scenarios"]) and len(native.get("results", [])) > len(result["scenarios"]):
                    break
                time.sleep(0.2)
            else:
                raise RuntimeError("Missing browser / native picker result: " + scenario)
            row = {"name": scenario, "dom": dom[-1], "callback": native["callbacks"][-1], "result": native["results"][-1]}
            result["scenarios"].append(row)
            validate_chooser(row, fsa_enabled, name, content)
        result["receipt"] = receipt()
        expected_callbacks = 6 if fsa_enabled else 4
        if (result["receipt"].get("errors") or len(result["receipt"].get("callbacks", [])) != expected_callbacks
                or len(result["receipt"].get("results", [])) != expected_callbacks):
            raise RuntimeError("Extra or missing native callbacks")
        if (result["receipt"].get("contextChecks") != {"webActivity": True, "childTheme": True, "childActivity": True}
                or result["receipt"].get("persistedBefore") != "[]" or result["receipt"].get("persistedAfter") != "[]"):
            raise RuntimeError("Context regression or unexpected persisted grants")
        result["compatAfter"] = [line.strip() for line in checked(device.shell("dumpsys", "platform_compat")).splitlines()
                                 if "name=ENABLE_FILE_SYSTEM_ACCESS;" in line]
        if result["compatAfter"] != result["compatDefinition"]:
            raise RuntimeError("Compat changed during the run")
        result["success"] = True
    except Exception as error:
        result["error"] = f"{type(error).__name__}: {error}"
        result["receipt"] = receipt()
    finally:
        if client:
            client.close()
        if forward and forward.isdigit():
            result["forwardCleanup"] = device.call("forward", "--remove", "tcp:" + forward)
            if result["forwardCleanup"]["exitCode"] != 0:
                result["success"] = False
        if launched:
            result["stop"] = device.shell("am", "force-stop", HOST)
        if created:
            result["fixtureCleanup"] = device.shell("rm", remote)
            if result["fixtureCleanup"]["exitCode"] != 0:
                result["success"] = False
        snapshot(device, [HOST], args.output / "after")
        result["ended"] = utc_now()
        write_json(args.output / "result.json", result)
    print(json.dumps({k: v for k, v in result.items() if k in ("caseId", "success", "error")}))
    return 0 if result["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
