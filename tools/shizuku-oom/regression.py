"""Shizuku feature regression (docs/shizuku-oom/ROADMAP.md T6 / P2.1; issue #474).

Runs the T6 items on one device through the host already installed there (a debug build with Shizuku already authorized):

  1  `shizuku('id')` and `shizuku.execCommand('id')`: code 0 and the uid the Shizuku server runs as (2000 shell / 0 root).
  2  `shizuku.spawn('sleep 600')` runs while the script runs and is gone after the script ended.
  3  `new Shell({ adb: true })` and `{ adb: true, pty: true }`: execForResult('id') returns the server uid.
  4  File manager through Shizuku (instrumented): browsing /system and a user service directory snapshot
     (PrivilegedExplorerDeviceTest), saving a text file through a Shizuku edit session and through the editor
     (PrivilegedEditSessionsDeviceTest, PrivilegedEditorSaveDeviceTest).
  5  Accessibility service enabled through Shizuku: ShizukuEnabledServicesStore (the store of the "shizuku" start strategy)
     writes the host service into enabled_accessibility_services and the service connects; the original list is written
     back the same way (when the service was enabled before, it is removed first and added again).
  6  `shizuku.currentPackage` / `currentActivity` match the resumed activity in dumpsys; instrumented:
     WrappedShizukuTopActivityInstrumentationTest and CircularMenuMoreDialogInstrumentationTest (floating button "More").
  7  KeyCode through Shizuku (issue #492): KEYCODE_VOLUME_DOWN reaches the audio service (an ADJUST_LOWER request in the
     dumpsys audio volume log, or a changed stream volume; a muted phone changes none). Volumes and the ringer mode are
     put back afterwards.
  8  Other call sites, one smoke each (only where the server runs as shell, unless --items names it): RootAutomator on the
     Shizuku backend, the pointer location tool, the plugin center launcher icon command (an empty state map, so no
     component changes), AndroidIdReader (`device.getAndroidId(true)` equals the adb value), and the app module's Shizuku
     branches (`app.startActivity({ shizuku: true })` opens Settings, `app.kill` ends it).
  9  Volume key control: one zombie_probe.py volume-key round (a getevent under the user service, a volume up key stops the
     script, the force-stop leaves no user service and no stray command).

After the items the host is force-stopped once more and the user service processes and stray sh / getevent / sleep
processes (new since the start, reparented to init) are counted; both must be 0.

Scripts start through RunIntentActivity like zombie_probe.py; the instrumented items run `am instrument` against the test
package already installed (org.autojs.autojs6.test, built by :app:assembleAppDebugAndroidTest; back up the original one
first). `am instrument` and the force-stops make Android drop the host from enabled_accessibility_services; entries the
host had before are put back at the end, and items 5, 7 and 8 put back the settings and volumes they change. The script
never taps the screen. Temporary files live in /sdcard/Download/aj6-shizuku-oom/ and are removed at the end.

Output: <report-dir>/regression-<model>-<versionCode>-<yyyyMMdd>[-<label>].json, and regression-<yyyyMMdd>[-<label>].md
rebuilt from every regression-*-<yyyyMMdd>[-<label>].json there (one column per device). One line per item on the
terminal, PASS / FAIL at the end. Values that identify the device (the Android ID) are compared, never stored.
"""

import argparse
import datetime
import json
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time

# -I leaves the script directory out of sys.path; no __pycache__ in the tools directory either.
sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.dont_write_bytecode = True
from zombie_probe import DEVICE_DIR, ROOT, Device, Probe, ProbeError  # noqa: E402

TEST_PACKAGE = "org.autojs.autojs6.test"
RUNNER = "androidx.test.runner.AndroidJUnitRunner"
USHER = "org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"
SETTINGS_PACKAGE = "com.android.settings"
ITEMS = ("1", "2", "3", "4", "5", "6", "7", "8", "9")
SERVER_UIDS = {"shell": 2000, "root": 0}

FILE_MANAGER_TESTS = (
    "org.autojs.autojs.model.explorer.PrivilegedExplorerDeviceTest#alreadyAuthorizedShizukuCanReadSystemWithoutChangingPermissions",
    "org.autojs.autojs.model.explorer.PrivilegedExplorerDeviceTest#userServicePagesAStableSnapshotAndReleasesIt",
    "org.autojs.autojs.model.explorer.PrivilegedEditSessionsDeviceTest#authorizedSessionWritesTheOriginalTwiceAndProviderCannotBypassSaveChecks",
    "org.autojs.autojs.ui.edit.editor.PrivilegedEditorSaveDeviceTest#editorWritesBackToAnUnreadableOriginalAndCanSaveAgain",
)
TOP_ACTIVITY_TESTS = (
    "org.autojs.autojs.runtime.api.WrappedShizukuTopActivityInstrumentationTest",
    "org.autojs.autojs.ui.floating.CircularMenuMoreDialogInstrumentationTest#rowsKeepTheirPositionsAfterTheMenuAppears",
)
# AndroidJUnitRunner status codes. zh-CN: AndroidJUnitRunner 的状态码.
TEST_STATUS = {0: "pass", -1: "error", -2: "failure", -3: "ignored", -4: "assumption"}
SKIPPED = (-3, -4)

PRELUDE = r"""
var out = {};
function uidOf(text) { var m = /uid=(\d+)/.exec(String(text)); return m ? Number(m[1]) : null; }
try {
"""
EPILOGUE = r"""
} catch (e) { out.error = String(e).slice(0, 300); }
files.write(MARK, JSON.stringify(out));
exit();
"""

JS_SHIZUKU_ID = r"""
var r = shizuku('id');
out.code = r.code; out.uid = uidOf(r.result);
var r2 = shizuku.execCommand('id');
out.code2 = r2.code; out.uid2 = uidOf(r2.result);
"""

JS_SPAWN = r"""
var p = shizuku.spawn('sleep 600');
out.pid = Number(p.pid);
files.write(MARK + '.pid', String(out.pid));
sleep(3000);
"""

JS_SHELL = r"""
[['pipe', { adb: true }], ['pty', { adb: true, pty: true }]].forEach(function (c) {
    var sh = new Shell(c[1]);
    try {
        var r = sh.execForResult('id');
        out[c[0]] = { code: r.code, uid: uidOf(r.result) };
    } finally {
        sh.exitAndWaitFor();
    }
});
"""

JS_A11Y = r"""
var store = new org.autojs.autojs.core.accessibility.control.ShizukuEnabledServicesStore(context, function (m) { out.log = String(m).slice(0, 160); });
var tool = new org.autojs.autojs.core.accessibility.AccessibilityTool(context);
var usher = context.getPackageName() + '/' + USHER;
var raw = store.read();
var list = raw == null || String(raw) == 'null' ? [] : String(raw).split(':').filter(function (e) { return e; });
var others = list.filter(function (e) { return e != usher; });
function waitFor(state) {
    var t0 = Date.now();
    while (Date.now() - t0 < 10000 && tool.isRunning() != state) sleep(200);
    return tool.isRunning() == state ? Date.now() - t0 : -1;
}
out.wasEnabled = list.length != others.length;
out.entries = list.length;
if (out.wasEnabled) { store.write(others.join(':')); out.stopMs = waitFor(false); }
store.write(others.concat([usher]).join(':'));
out.startMs = waitFor(true);
if (!out.wasEnabled) { store.write(others.join(':')); out.stopMs = waitFor(false); }
"""

JS_TOP = r"""
sleep(1500);
out.pkg = String(shizuku.currentPackage());
out.act = String(shizuku.currentActivity());
"""

JS_KEYCODE = r"""
KeyCode(KEY);
sleep(800);
out.sent = KEY;
"""

JS_ROOT_AUTOMATOR = r"""
var ra = new RootAutomator(true);
ra.sendSync();
ra.exit();
out.ok = true;
"""

JS_POINTER = r"""
var tool = org.autojs.autojs.app.tool.PointerLocationTool.Companion;
out.enabled = tool.setPointerLocationEnabled(context);
out.afterEnable = tool.getPointerLocationResult(context);
out.disabled = tool.setPointerLocationDisabled(context);
out.afterDisable = tool.getPointerLocationResult(context);
"""

JS_LAUNCHER = r"""
var controller = org.autojs.autojs.core.plugin.center.PluginLauncherIconController.INSTANCE;
var method = controller.getClass().getDeclaredMethods().filter(function (m) {
    return String(m.getName()).indexOf('command') == 0 && m.getParameterTypes().length == 3;
})[0];
method.setAccessible(true);
var command = method.invoke(controller, context, context.getPackageName(), new java.util.HashMap());
var r = org.autojs.autojs.runtime.api.WrappedShizuku.INSTANCE.execCommand(context, command);
out.code = r.code;
out.ok = String(r.result).indexOf('AUTOJS_LAUNCHER_OK') >= 0;
"""

JS_ANDROID_ID = r"""
out.id = String(device.getAndroidId(true));
"""

JS_APP = r"""
app.startActivity({ action: 'android.settings.SETTINGS', shizuku: true });
sleep(2500);
out.top = String(shizuku.currentPackage());
out.killed = app.kill(SETTINGS);
sleep(1500);
out.topAfter = String(shizuku.currentPackage());
"""


class Regression:

    def __init__(self, device, args):
        self.dev = device
        self.args = args
        self.probe = Probe(device, argparse.Namespace(package=args.package, wait=args.wait, settle=args.settle,
                                                      mode="volume-key", stray_fails=True, profile=False))
        self.package = args.package
        self.uid = None

    # Helpers. zh-CN: 辅助方法.

    def run_js(self, name, body, wait=None, constants=None):
        token = secrets.token_hex(4)
        mark = f"{DEVICE_DIR}/reg-{name}-{token}.json"
        script = f"{DEVICE_DIR}/reg-{name}-{token}.js"
        head = [f"var MARK = {json.dumps(mark)};"] + [f"var {k} = {json.dumps(v)};" for k, v in (constants or {}).items()]
        self.dev.write(script, "\n".join(head) + PRELUDE + body + EPILOGUE)
        self.probe.start_script(script)
        return mark, self.wait_json(mark, wait or self.args.wait, name)

    def wait_json(self, mark, wait, name):
        deadline = time.time() + wait
        while time.time() < deadline:
            text = self.probe.read_mark(mark)
            if text:
                try:
                    return json.loads(text)
                except ValueError:
                    pass
            time.sleep(0.5)
        raise ProbeError(f"script {name} wrote no result within {wait:.0f} s")

    def instrument(self, tests, timeout=900):
        """Runs [tests] with `am instrument -r`; returns ({short name: status}, first failure line, first skip reason, instrumentation code)."""
        command = f"am instrument -w -r -e class {','.join(tests)} {TEST_PACKAGE}/{RUNNER}"
        _, out, err = self.dev.adb("shell", command, timeout=timeout)
        results, failure, skip, current, code = {}, None, None, {}, None
        for line in out.splitlines():
            if line.startswith("INSTRUMENTATION_STATUS: "):
                key, _, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
                current[key] = value
            elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
                status = int(line.split(":", 1)[1])
                if status != 1:
                    name = f"{current.get('class', '?').rsplit('.', 1)[-1]}#{current.get('test', '?')}"
                    results[name] = TEST_STATUS.get(status, str(status))
                    stack = current.get("stack", "").strip()
                    if status in SKIPPED and skip is None:
                        # The exception message only. zh-CN: 只取异常消息, 例如 "Window inspection requires API 29".
                        skip = stack.splitlines()[0].split(": ", 1)[-1][:120] if stack else results[name]
                    elif status not in SKIPPED and status != 0 and failure is None:
                        failure = f"{name}: {stack[:200]}"
                current = {}
            elif line.startswith("INSTRUMENTATION_CODE: "):
                code = int(line.split(":", 1)[1])
            elif line.startswith("INSTRUMENTATION_RESULT: shortMsg=") and failure is None:
                failure = line.split("=", 1)[1][:200]
        if not results and failure is None:
            failure = (out + err).strip()[-200:] or "no output"
        return results, failure, skip, code

    def has_test_package(self):
        return f"instrumentation:{TEST_PACKAGE}/" in self.dev.shell("pm list instrumentation")

    def instrumented(self, item, tests):
        if not self.has_test_package():
            return self.result(item, False, f"{TEST_PACKAGE} not installed")
        results, failure, skip, code = self.instrument(tests)
        passed = sum(1 for s in results.values() if s == "pass")
        skipped = sum(1 for s in results.values() if s in (TEST_STATUS[k] for k in SKIPPED))
        # Skipped tests (e.g. API 29+ only) are no failure, but one must pass. zh-CN: 被 assumption / @Ignore 跳过的用例不算失败, 但至少要有一个通过.
        ok = passed > 0 and passed + skipped == len(results) and code == -1
        note = f"{passed}/{len(results)} tests pass"
        if skipped:
            note += f", {skipped} skipped ({skip})"
        if not ok:
            note += f"; {failure}" if failure else f"; instrumentation code {code}"
        return {"passed": passed, "tests": results, "ok": ok, "note": note}

    @staticmethod
    def result(item, ok, note, **values):
        return {"item": item, "status": "PASS" if ok else "FAIL", "note": note, **values}

    def resumed_activity(self):
        dump = self.dev.shell("dumpsys activity activities")
        found = re.search(r"(?:mResumedActivity|topResumedActivity)[:=].*? u\d+ ([\w.]+)/([\w.$]+)", dump)
        if not found:
            return None, None
        pkg, act = found[1], found[2]
        return pkg, pkg + act if act.startswith(".") else act

    def secure(self, name):
        return self.dev.shell(f"settings get secure {name}").strip()

    def put_secure(self, name, value):
        if value == "null":
            self.dev.shell(f"settings delete secure {name} >/dev/null")
        else:
            self.dev.shell(f"settings put secure {name} '{value}'")

    # Items. zh-CN: 回归项.

    def item1(self):
        _, out = self.run_js("id", JS_SHIZUKU_ID)
        ok = out.get("code") == 0 and out.get("code2") == 0 and out.get("uid") == self.uid and out.get("uid2") == self.uid
        return self.result("1", ok, out.get("error") or f"shizuku() code {out.get('code')} uid {out.get('uid')}, "
                           f"execCommand code {out.get('code2')} uid {out.get('uid2')}")

    def sleeps(self):
        rows = self.dev.processes()
        pids = [r[0] for r in rows if r[4].rsplit("/", 1)[-1] == "sleep"]
        return {pid for pid, args in self.dev.args_of(pids).items() if args.strip() == "sleep 600"}

    def item2(self):
        before = self.sleeps()
        token = secrets.token_hex(4)
        mark = f"{DEVICE_DIR}/reg-spawn-{token}.json"
        script = f"{DEVICE_DIR}/reg-spawn-{token}.js"
        self.dev.write(script, f"var MARK = {json.dumps(mark)};" + PRELUDE + JS_SPAWN + EPILOGUE)
        self.probe.start_script(script)
        pid_text = None
        deadline = time.time() + self.args.wait
        while time.time() < deadline and pid_text is None:
            pid_text = self.probe.read_mark(mark + ".pid")
            time.sleep(0.3)
        running = self.sleeps() - before
        out = self.wait_json(mark, self.args.wait, "spawn")
        time.sleep(self.args.settle)
        left = self.sleeps() - before
        self.probe.kill_pids(sorted(left))
        ok = pid_text is not None and len(running) >= 1 and not left and "error" not in out
        return self.result("2", ok, out.get("error") or f"pid {'reported' if pid_text else 'missing'}, sleep 600 running "
                           f"{len(running)} while the script ran, {len(left)} left {self.args.settle:g} s after it ended")

    def item3(self):
        _, out = self.run_js("shell", JS_SHELL)
        pipe, pty = out.get("pipe") or {}, out.get("pty") or {}
        ok = all(p.get("code") == 0 and p.get("uid") == self.uid for p in (pipe, pty))
        return self.result("3", ok, out.get("error") or f"adb code {pipe.get('code')} uid {pipe.get('uid')}, "
                           f"adb + pty code {pty.get('code')} uid {pty.get('uid')}")

    def item4(self):
        found = self.instrumented("4", FILE_MANAGER_TESTS)
        if "status" in found:
            return found
        return self.result("4", found["ok"], found["note"], tests=found["tests"])

    def item5(self):
        services, enabled = self.secure("enabled_accessibility_services"), self.secure("accessibility_enabled")
        try:
            _, out = self.run_js("a11y", JS_A11Y, wait=60, constants={"USHER": USHER})
        finally:
            restored = 0
            for name, value in (("enabled_accessibility_services", services), ("accessibility_enabled", enabled)):
                if self.secure(name) != value:
                    self.put_secure(name, value)
                    restored += 1
        ok = "error" not in out and out.get("startMs", -1) >= 0 and out.get("stopMs", -1) >= 0
        note = out.get("error") or (f"enabled before {out.get('wasEnabled')}, connected after {out.get('startMs')} ms, "
                                    f"{'stopped' if out.get('wasEnabled') else 'disconnected after the list was written back'} "
                                    f"in {out.get('stopMs')} ms")
        if restored:
            note += f"; settings put back by adb: {restored}"
        return self.result("5", ok, note, settingsRestored=restored)

    def item6(self):
        _, out = self.run_js("top", JS_TOP)
        pkg, act = self.resumed_activity()
        script_ok = "error" not in out and out.get("pkg") == pkg and out.get("act") == act and bool(pkg)
        found = self.instrumented("6", TOP_ACTIVITY_TESTS)
        if "status" in found:
            return self.result("6", False, found["note"])
        note = (out.get("error") or f"currentPackage / currentActivity {'match' if script_ok else 'differ from'} dumpsys ({pkg}/{act})"
                + ("" if script_ok else f" vs {out.get('pkg')}/{out.get('act')}"))
        return self.result("6", script_ok and found["ok"], f"{note}; {found['note']}", tests=found["tests"])

    def volume_requests(self):
        """Lines of the audio service's volume log (dumpsys audio), each with a ms timestamp. zh-CN: 音量日志的各行."""
        return {line.strip() for line in self.dev.shell("dumpsys audio").splitlines()
                if "adjustSuggestedStreamVolume(" in line or "adjustStreamVolume(" in line}

    def ringer_mode(self):
        return self.dev.shell("settings get global mode_ringer").strip()

    def item7(self):
        # A muted or Do Not Disturb phone keeps every volume, so the adjust request in the volume log counts as delivered.
        # zh-CN: 静音或勿扰时音量不会变, 所以音量日志里出现对应方向的调节请求即算送达.
        before, ringer, seen = self.probe.volumes(), self.ringer_mode(), self.volume_requests()
        _, out = self.run_js("keycode", JS_KEYCODE, constants={"KEY": "KEYCODE_VOLUME_DOWN"})
        time.sleep(0.5)
        requests = sum(1 for line in self.volume_requests() - seen if "dir:ADJUST_LOWER" in line)
        after = self.probe.volumes()
        changed = sum(1 for s, (_, v) in before.items() if after.get(s, (None, v))[1] != v)
        restored = self.probe.restore_volumes(before)
        ringer_after = self.ringer_mode()
        if ringer_after != ringer:
            # Ringing at the lowest volume switches to vibrate. zh-CN: 铃声在最低音量时减小会切到振动, 用音量加键改回.
            self.dev.shell("input keyevent KEYCODE_VOLUME_UP")
            time.sleep(0.5)
            restored += self.probe.restore_volumes(before)
        ringer_back = self.ringer_mode() == ringer
        ok = "error" not in out and (requests > 0 or changed > 0) and ringer_back
        note = out.get("error") or f"VOLUME_DOWN: adjust requests {requests}, streams changed {changed}, put back {restored}"
        if ringer_after != ringer:
            note += f", ringer mode {ringer} -> {ringer_after}, {'put back' if ringer_back else 'NOT put back'}"
        return self.result("7", ok, note, volumeStreamsRestored=restored, ringerModeChanged=ringer_after != ringer)

    def item8(self):
        parts, ok = [], True

        def check(label, passed, detail):
            nonlocal ok
            ok = ok and passed
            parts.append(f"{label} {'ok' if passed else 'FAIL'} ({detail})")

        _, out = self.run_js("rootautomator", JS_ROOT_AUTOMATOR)
        check("RootAutomator", out.get("ok") is True, out.get("error") or "Shizuku backend, sendSync + exit")

        pointer = self.dev.shell("settings get system pointer_location").strip()
        try:
            _, out = self.run_js("pointer", JS_POINTER)
        finally:
            if self.dev.shell("settings get system pointer_location").strip() != pointer:
                self.dev.shell(f"settings put system pointer_location {pointer if pointer.isdigit() else 0}")
        check("pointer location", out.get("enabled") is True and out.get("afterEnable") == 1 and out.get("disabled") is True
              and out.get("afterDisable") == 0, out.get("error") or f"on {out.get('afterEnable')}, off {out.get('afterDisable')}")

        _, out = self.run_js("launcher", JS_LAUNCHER)
        check("launcher icon command", out.get("code") == 0 and out.get("ok") is True,
              out.get("error") or f"code {out.get('code')}, empty state map")

        mark, out = self.run_js("androidid", JS_ANDROID_ID)
        system_id = self.dev.shell("settings get secure android_id").strip().lower()
        same = bool(out.get("id")) and out.get("id") == system_id
        check("AndroidIdReader", same, out.get("error") or ("equals the adb value" if same else "differs from the adb value"))
        self.dev.shell(f"rm -f {mark}")

        _, out = self.run_js("app", JS_APP, constants={"SETTINGS": SETTINGS_PACKAGE})
        gone = not self.dev.shell(f"pidof {SETTINGS_PACKAGE}").strip()
        check("app Shizuku branches", out.get("top") == SETTINGS_PACKAGE and out.get("killed") is True and gone,
              out.get("error") or f"startActivity top {out.get('top')}, kill {out.get('killed')}, process gone {gone}")
        return self.result("8", ok, "; ".join(parts))

    def item9(self):
        self.probe.force_stop()
        time.sleep(self.args.settle)
        round_ = self.probe.round_volume_key(1)
        ok = (round_.get("volumeKeyStopped") is True and round_.get("residue") == 0 and round_.get("strayExec") == 0
              and round_.get("exitSource") in ("host-died", "destroy"))
        note = round_.get("note") or (f"getevent under the service {round_.get('geteventUnderService')}, stopped after "
                                      f"{round_.get('volumeKeyStopSec')} s ({round_.get('volumeKeySignal')}), residue "
                                      f"{round_.get('residue')}, exit {round_.get('exitSource')}, stray {round_.get('strayExec')}, "
                                      f"volumes put back {round_.get('volumeStreamsRestored')}")
        return self.result("9", ok, note)


def items_type(value):
    if value == "all":
        return None
    chosen = [v.strip() for v in value.split(",") if v.strip()]
    if not chosen or any(v not in ITEMS for v in chosen):
        raise argparse.ArgumentTypeError("--items takes 'all' or a comma-separated list of 1-9")
    return chosen


def report_stem(meta, label):
    return f"regression-{meta['model']}-{meta['host']['versionCode']}-{meta['date']}" + (f"-{label}" if label else "")


def write_summary(out_dir, date, label):
    """Rebuilds regression-<date>[-<label>].md from every device report of that date. zh-CN: 由同一日期的全部设备报告重建汇总."""
    suffix = f"-{date}" + (f"-{label}" if label else "")
    reports = []
    for path in sorted(out_dir.glob(f"regression-*{suffix}.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("meta", {}).get("date") == date and data.get("meta", {}).get("label") == label:
            reports.append((path, data))
    heads = [f"{d['meta']['model']} ({d['meta']['shizuku']['serverUser']})" for _, d in reports]
    lines = [
        f"# Shizuku 功能回归 (T6, P2.1): {date}" + (f" ({label})" if label else ""),
        "",
        "脚本: `tools/shizuku-oom/regression.py`, 各项定义见 `docs/shizuku-oom/ROADMAP.md` T6 与脚本说明. 每台设备的明细在同名 JSON.",
        "",
    ]
    for _, d in reports:
        m = d["meta"]
        lines.append(f"- {m['model']} ({m['serial']}): Android {m['android']} / API {m['sdk']}, 宿主 {m['host']['versionName']} "
                     f"({m['host']['versionCode']}), Shizuku {m['shizuku']['version']} 服务端身份 {m['shizuku']['serverUser']}, "
                     f"{m['startedAt']} - {m['endedAt']}, 宿主无障碍 {m['a11yBefore']} -> {m['a11yAfter']}"
                     + (f" (结束时补回 {m['a11yRestored']} 项)" if m.get("a11yRestored") else ""))
    lines += ["", "| 项 | " + " | ".join(heads) + " |", "| --- |" + " --- |" * len(heads)]
    for item in ITEMS + ("final",):
        cells = []
        for _, d in reports:
            found = next((r for r in d["items"] if r["item"] == item), None)
            cells.append(f"**{found['status']}**: {found['note']}" if found else "-")
        name = "结束后残留" if item == "final" else f"({item})"
        lines.append(f"| {name} | " + " | ".join(cells).replace("\n", " ") + " |")
    verdicts = [d["verdict"] for _, d in reports]
    overall = "PASS" if verdicts and all(v == "PASS" for v in verdicts) else "FAIL"
    lines += ["", f"结论: **{overall}** ({', '.join(f'{h} {v}' for h, v in zip(heads, verdicts))})", ""]
    path = out_dir / f"regression{suffix}.md"
    path.write_text("\n".join(lines), encoding="utf-8")
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--items", type=items_type, default=None, help="'all' (default: 1-9, item 8 only for a shell server) or e.g. 1,2,3")
    parser.add_argument("--report-dir", default="docs/shizuku-oom/evidence", help="relative paths are resolved against the repository root")
    parser.add_argument("--label", help="suffix of the report names, e.g. p1")
    parser.add_argument("--package", default="org.autojs.autojs6")
    parser.add_argument("--settle", type=float, default=3.0, help="seconds between ending something and counting")
    parser.add_argument("--wait", type=float, default=45.0, help="seconds to wait for a script result")
    parser.add_argument("--no-wake", action="store_true", help="do not send KEYCODE_WAKEUP before the run")
    args = parser.parse_args()

    dev = Device(args.serial)
    regression = Regression(dev, args)
    probe = regression.probe
    if dev.adb("get-state")[1].strip() != "device":
        raise SystemExit(f"{args.serial} is not online")
    host = probe.host_info()
    if host["versionCode"] is None:
        raise SystemExit(f"{args.package} is not installed on {args.serial}")
    if not host["shizukuPermission"]:
        raise SystemExit("The host has no Shizuku permission; grant it on the phone first")
    if probe.server_pid() is None:
        raise SystemExit("The Shizuku server is not running")
    shizuku = probe.shizuku_info()
    regression.uid = SERVER_UIDS.get(shizuku["serverUser"])
    chosen = args.items or [i for i in ITEMS if i != "8" or shizuku["serverUser"] == "shell"]

    if not args.no_wake:
        dev.shell("input keyevent KEYCODE_WAKEUP")
    if "isKeyguardShowing=true" in dev.shell("dumpsys window | grep isKeyguardShowing"):
        print("warning: the keyguard is showing; unlock the phone if the scripts do not start", file=sys.stderr)
    dev.shell(f"mkdir -p {DEVICE_DIR}")
    a11y_before = probe.a11y_entries()
    known = {r[0] for r in dev.processes()}
    started = datetime.datetime.now()
    meta = {
        "serial": args.serial,
        "model": dev.prop("ro.product.model").replace(" ", "_"),
        "android": dev.prop("ro.build.version.release"),
        "sdk": dev.prop("ro.build.version.sdk"),
        "host": host,
        "shizuku": shizuku,
        "items": chosen,
        "label": args.label,
        "date": started.strftime("%Y%m%d"),
        "startedAt": started.strftime("%Y-%m-%d %H:%M:%S"),
    }
    print(f"{meta['model']} {host['versionName']} ({host['versionCode']}), Shizuku as {shizuku['serverUser']}, items {','.join(chosen)}")
    results = []
    try:
        for item in chosen:
            try:
                found = getattr(regression, f"item{item}")()
            except (ProbeError, subprocess.TimeoutExpired, ValueError) as e:
                found = Regression.result(item, False, f"stopped: {str(e)[:200]}")
            results.append(found)
            print(f"({item}) {found['status']}  {found['note']}", flush=True)
        probe.force_stop()
        rows, services = probe.settle_and_count()
        final = {}
        probe.strays(final, rows, known)
        ok = not services and final["strayExec"] == 0
        results.append(Regression.result("final", ok, f"user service {len(services)}, stray {final['strayExec']}"
                                         + (f" ({', '.join(final['strayExecNames'])}, killed {final['strayExecKilled']})" if final["strayExec"] else "")))
        print(f"(final) {results[-1]['status']}  {results[-1]['note']}")
    finally:
        meta["endedAt"] = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        meta["a11yBefore"] = bool(probe.host_a11y(a11y_before))
        meta["a11yRestored"] = probe.restore_a11y(a11y_before)
        meta["a11yAfter"] = bool(probe.host_a11y(probe.a11y_entries()))
        dev.shell(f"rm -rf {DEVICE_DIR}")
    verdict = "PASS" if results and all(r["status"] == "PASS" for r in results) else "FAIL"
    out_dir = Path(args.report_dir)
    if not out_dir.is_absolute():
        out_dir = ROOT / out_dir
    out_dir.mkdir(parents=True, exist_ok=True)
    data = {"meta": meta, "items": results, "verdict": verdict}
    (out_dir / f"{report_stem(meta, args.label)}.json").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    summary = write_summary(out_dir, meta["date"], args.label)
    print(verdict)
    print(f"report: {summary.relative_to(ROOT) if summary.is_relative_to(ROOT) else summary}")
    return 0 if verdict == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
