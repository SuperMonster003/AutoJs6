"""Background microphone probe of 3-Macaw Speech roadmap D22 (P1.2) on one device.

A script started through AutoJs6's RunIntentActivity (no instrumentation, so the host process keeps its real state)
opens `speech.listen()`; the host binds the fake speech plugin of this test app with BIND_INCLUDE_CAPABILITIES and the
fake records with AudioRecord in its own process (another uid), reporting a `vad` event every 500 ms. The driver keeps
AutoJs6 in the foreground, then sends it to the background (HOME), then turns the screen off, and classifies the events
of each phase: `audio` (non-zero samples, not silenced) or `silenced` (all samples zero or isClientSilenced).

Prerequisites: the host debug build with the speech module, this test app (`adb install -t`), and RECORD_AUDIO granted
to the test app by the maintainer (`adb shell am start -n org.autojs.plugin.speech.fake/.FakeSpeechPermissionActivity`
shows the system dialog). The driver never grants permissions itself.

    py test-apps/speech-provider-conformance/run_microphone_probe.py <serial> [<serial> ...]

Output: one line per device and phase; no token, transcript or full event list is printed.
"""
import json
import os
import subprocess
import sys
import tempfile
import time

HOST = "org.autojs.autojs6"
FAKE = "org.autojs.plugin.speech.fake"
REMOTE_DIR = f"/sdcard/Android/data/{HOST}/files/speech-probe"
PHASE_SECONDS = 8
PHASES = ("foreground", "background", "screen-off")

SCRIPT = """
var out = "%(out)s";
var r = { events: [], errors: [] };
var s = speech.listen({ maxDurationMillis: %(duration)d, timeoutMillis: 0, vad: false });
s.on("ready", function (e) { r.ready = Date.now(); });
s.on("vad", function (e) { r.events.push([e.at, e.active ? 1 : 0, e.peak, e.nonZero, e.frames, e.silenced === undefined ? -1 : (e.silenced ? 1 : 0)]); });
s.on("error", function (e) { r.errors.push(e.code + " " + e.message); });
s.on("end", function (e) { r.reason = e.reason; files.write(out, JSON.stringify(r)); });
"""


def adb(serial, *args, check=True):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", check=check).stdout.strip()


def device_now(serial):
    return int(adb(serial, "shell", "date", "+%s%3N"))


def granted(serial):
    dump = adb(serial, "shell", "dumpsys", "package", FAKE, check=False)
    return "android.permission.RECORD_AUDIO: granted=true" in dump


def launcher(serial):
    return adb(serial, "shell", "cmd", "package", "resolve-activity", "--brief", "-c", "android.intent.category.LAUNCHER", HOST).splitlines()[-1].strip()


def probe(serial):
    sdk = adb(serial, "shell", "getprop", "ro.build.version.sdk")
    model = adb(serial, "shell", "getprop", "ro.product.model")
    label = f"{model} ({serial}, API {sdk})"
    if not adb(serial, "shell", "pm", "path", FAKE, check=False):
        print(f"{label}: SKIP fake speech plugin not installed")
        return False
    if not granted(serial):
        print(f"{label}: BLOCKED RECORD_AUDIO not granted to {FAKE}; ask the maintainer to allow it in the dialog of FakeSpeechPermissionActivity")
        return False
    total = PHASE_SECONDS * len(PHASES) + 4
    adb(serial, "shell", "rm", "-rf", REMOTE_DIR, check=False)
    adb(serial, "shell", "mkdir", "-p", REMOTE_DIR)
    local = os.path.join(tempfile.gettempdir(), f"speech-probe-{serial.replace(':', '_')}.js")
    with open(local, "w", encoding="utf-8") as handle:
        handle.write(SCRIPT % {"duration": total * 1000, "out": f"{REMOTE_DIR}/result.json"})
    adb(serial, "push", local, f"{REMOTE_DIR}/probe.js")
    adb(serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb(serial, "shell", "am", "start", "-n", launcher(serial))
    time.sleep(2)
    adb(serial, "shell", "am", "start", "-a", "android.intent.action.VIEW", "-t", "application/x-javascript",
        "-d", f"file://{REMOTE_DIR}/probe.js", "-n", f"{HOST}/org.autojs.autojs.external.open.RunIntentActivity")
    time.sleep(2)
    marks = {"foreground": device_now(serial)}
    time.sleep(PHASE_SECONDS)
    adb(serial, "shell", "input", "keyevent", "KEYCODE_HOME")
    marks["background"] = device_now(serial)
    time.sleep(PHASE_SECONDS / 2)
    host_fgs = "isForeground=true" in adb(serial, "shell", "dumpsys", "activity", "services", HOST, check=False)
    time.sleep(PHASE_SECONDS / 2)
    adb(serial, "shell", "input", "keyevent", "KEYCODE_SLEEP")
    marks["screen-off"] = device_now(serial)
    time.sleep(PHASE_SECONDS)
    end = device_now(serial)
    adb(serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
    deadline = time.time() + 30
    raw = ""
    while time.time() < deadline:
        raw = adb(serial, "shell", "cat", f"{REMOTE_DIR}/result.json", check=False)
        if raw.startswith("{"):
            break
        time.sleep(2)
    if not raw.startswith("{"):
        print(f"{label}: FAIL no result file (the script did not finish)")
        return False
    result = json.loads(raw)
    if result.get("errors"):
        print(f"{label}: FAIL {result['errors'][0]}")
        return False
    bounds = [marks["foreground"], marks["background"], marks["screen-off"], end]
    ok = True
    for index, phase in enumerate(PHASES):
        # Skip the first second of each phase: the process state follows the key event with a delay.
        events = [e for e in result["events"] if bounds[index] + 1000 <= e[0] < bounds[index + 1]]
        if not events:
            print(f"{label}: {phase}: no events")
            ok = False
            continue
        audio = sum(1 for e in events if e[1] == 1 and e[5] != 1)
        silenced = sum(1 for e in events if e[5] == 1)
        peak = max(e[2] for e in events)
        verdict = "audio" if audio == len(events) else ("silenced" if audio == 0 else "mixed")
        print(f"{label}: {phase}: {verdict} (events {len(events)}, with audio {audio}, flagged silenced {silenced}, peak {peak})")
    print(f"{label}: end reason {result.get('reason')}, host foreground service in the background phase {'on' if host_fgs else 'off'}")
    return ok


if __name__ == "__main__":
    serials = sys.argv[1:]
    if not serials:
        print(__doc__)
        sys.exit(2)
    results = [probe(serial) for serial in serials]
    sys.exit(0 if all(results) else 1)
