"""Check when the host answers an unavailable 3-Stove Agent (3-Stove Agent ROADMAP D80) on a device.

The host debug APK and 3-Stove Agent must already be installed, and the plugin must be authorized
with a trusted or confirmed signer, so that switching it off in Plugin Center yields PLUGIN_DISABLED.
The script switches the plugin off for the run and restores the previous switch afterwards.

Checks:
  background  The workbench's automatic attach broadcast reaches the host (seen in the broadcast history),
              and opening the home page afterwards shows no host dialog window.
  hint        ai.agent.run and ai.agent.presets fail with the PLUGIN_DISABLED hint, and the home page
              still shows no dialog afterwards.
  foreground  (needs the maintainer) After "Open AutoJs6" is tapped in the workbench, Plugin Center
              becomes the resumed activity without a dialog. Run it on its own while someone is at the phone.

Examples:
  python tools/three-stove-agent/guide_timing_check.py QV770340J7
  python tools/three-stove-agent/guide_timing_check.py QV770340J7 --checks foreground --tap-timeout 600
"""
import argparse
import json
import re
import shlex
import subprocess
import sys
import time
from pathlib import Path

HOST = "org.autojs.autojs6"
PLUGIN = "io.github.supermonster003.autojs6.plugin.three.stove.agent"
RUNNER = f"{HOST}/org.autojs.autojs.external.open.RunIntentActivity"
HOME = f"{HOST}/org.autojs.autojs.ui.main.MainActivity"
PLUGIN_CENTER = "org.autojs.autojs.core.plugin.center.PluginCenterActivity"
WORKBENCH = f"{PLUGIN}/.ui.LauncherActivity"
FILES = f"/sdcard/Android/data/{HOST}/files"
ATTACH_ACTION = "org.autojs.autojs6.action.THREE_STOVE_AGENT_ATTACH"
DISABLED_HINT = "3-Stove Agent is disabled in AutoJs6 Plugin Center; enable it there"

TEMPLATE = r'''
var P = Packages.org.autojs.autojs.core.plugin;
var receipt = {mode: "__MODE__"};
var output = new java.io.File(context.getExternalFilesDir(null), "__RECEIPT__").getAbsolutePath();
function state() { return String(P.agent.ThreeStoveAgentPluginInspector.INSTANCE.inspect(context).getState().name()); }
function finish() { receipt.complete = true; files.write(output, JSON.stringify(receipt)); exit(); }
receipt.before = state();
if (receipt.mode === "disable" || receipt.mode === "restore") {
    P.center.PluginEnableStore.INSTANCE.setEnabled(context, "__PLUGIN__", __ENABLED__);
    receipt.after = state();
    finish();
} else {
    var run = ai.agent.run("3-Stove Agent guide timing check");
    run.result.then(function () {}, function () {});
    receipt.runState = run.state;
    receipt.runCode = run.error ? run.error.code : null;
    receipt.runHint = run.error ? run.error.hint : null;
    ai.agent.presets().then(function () { receipt.presets = "resolved"; finish(); },
        function (e) { receipt.presetsCode = e.code; receipt.presetsHint = e.hint; finish(); });
    setInterval(function () {}, 1000);
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("serial")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--checks", default="background,hint", help="comma-separated: background, hint, foreground")
    parser.add_argument("--tap-timeout", type=int, default=120)
    parser.add_argument("--out", type=Path, default=Path("build/tmp-three-stove-agent/guide-timing"))
    args = parser.parse_args()
    checks = set(args.checks.split(","))
    args.out.mkdir(parents=True, exist_ok=True)
    results = []

    def adb(*command, check=True, timeout=120):
        result = subprocess.run([args.adb, "-s", args.serial, *command], text=True, encoding="utf-8",
                                errors="replace", capture_output=True, timeout=timeout)
        if check and result.returncode:
            raise RuntimeError(f"adb {command[:3]} failed: {result.stderr.strip()[:200]}")
        return result.stdout.strip()

    def report(name, ok, detail):
        results.append(ok)
        print(f"{'PASS' if ok else 'FAIL'} {name}: {detail}", flush=True)

    def host_script(mode, enabled="true", timeout=40):
        name = f"three-stove-agent-guide-{mode}.json"
        script = (TEMPLATE.replace("__MODE__", mode).replace("__RECEIPT__", name)
                  .replace("__PLUGIN__", PLUGIN).replace("__ENABLED__", enabled))
        adb("shell", "rm", "-f", f"{FILES}/{name}", check=False)
        adb("shell", shlex.join(["am", "start", "-n", RUNNER, "--es", "script", script]))
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            text = adb("shell", "cat", f"{FILES}/{name}", check=False)
            if text.startswith("{"):
                receipt = json.loads(text)
                if receipt.get("complete"):
                    return receipt
            time.sleep(1)
        raise RuntimeError(f"no receipt for {mode} within {timeout} s")

    def host_dialogs():
        """Visible host windows of type APPLICATION, i.e. dialogs; activities are BASE_APPLICATION."""
        blocks = re.split(r"(?m)^  Window #\d+ ", adb("shell", "dumpsys", "window", "windows"))
        return sum(1 for b in blocks if f"package={HOST} " in b and re.search(r"\bty=APPLICATION\b", b)
                   and "isOnScreen=true" in b)

    def resumed():
        match = re.search(r"topResumedActivity=ActivityRecord\{\S+ u0 ([^\s}]+)", adb("shell", "dumpsys", "activity", "activities"))
        return match.group(1) if match else ""

    def attach_broadcasts():
        history = adb("shell", "dumpsys", "activity", "broadcasts", "history", timeout=60)
        return len(re.findall(rf"BroadcastRecord\{{\S+ u0 {re.escape(ATTACH_ACTION)}", history))

    def open_home():
        # A finishing script activity can hand the front back to an older task; retry until home is resumed.
        top = ""
        for _ in range(3):
            adb("shell", "am", "start", "-n", HOME)
            for _ in range(6):
                time.sleep(1)
                top = resumed()
                if top == HOME:
                    break
            if top == HOME:
                break
        time.sleep(3)
        return resumed(), host_dialogs()

    before = host_script("disable", "false")
    was_enabled = before["before"] != "PLUGIN_DISABLED"
    if before["after"] != "PLUGIN_DISABLED":
        print(f"FAIL precondition: state after switching off is {before['after']} (was {before['before']})")
        host_script("restore", "true" if was_enabled else "false")
        return 1
    print(f"INFO precondition: {before['before']} -> PLUGIN_DISABLED", flush=True)
    try:
        if "background" in checks:
            # Background: each time the workbench starts unattached it sends the attach broadcast.
            open_home()
            sent = attach_broadcasts()
            adb("shell", "am", "start", "-n", WORKBENCH)
            time.sleep(6)
            delivered = attach_broadcasts() - sent
            report("background broadcast reaches the host", delivered > 0, f"new attach broadcasts={delivered}")
            top, dialogs = open_home()
            report("background: home page shows no dialog", top == HOME and dialogs == 0, f"resumed={top.split('/')[-1]}, dialogs={dialogs}")

        if "hint" in checks:
            hint = host_script("hint")
            report("hint: ai.agent.run", hint["runState"] == "failed" and hint["runCode"] == "PLUGIN_UNAVAILABLE"
                   and hint["runHint"] == DISABLED_HINT, f"state={hint['runState']}, code={hint['runCode']}, hint={hint['runHint']!r}")
            report("hint: ai.agent.presets", hint.get("presetsCode") == "PLUGIN_UNAVAILABLE" and hint.get("presetsHint") == DISABLED_HINT,
                   f"code={hint.get('presetsCode')}, hint={hint.get('presetsHint')!r}")
            top, dialogs = open_home()
            report("hint: home page shows no dialog", top == HOME and dialogs == 0, f"resumed={top.split('/')[-1]}, dialogs={dialogs}")

        if "foreground" in checks:
            open_home()
            adb("shell", "am", "start", "-n", WORKBENCH)
            print(f"ACTION 请在手机的 3-Stove Agent 工作台点 \"打开 AutoJs6\" (等待 {args.tap_timeout} 秒)", flush=True)
            deadline = time.monotonic() + args.tap_timeout
            top = ""
            while time.monotonic() < deadline and not top.endswith(PLUGIN_CENTER):
                time.sleep(1)
                top = resumed()
            time.sleep(2)
            dialogs = host_dialogs()
            shot = args.out / "foreground-plugin-center.png"
            shot.write_bytes(subprocess.run([args.adb, "-s", args.serial, "exec-out", "screencap", "-p"], capture_output=True).stdout)
            report("foreground: Open AutoJs6 opens Plugin Center", top.endswith(PLUGIN_CENTER) and dialogs == 0,
                   f"resumed={top.split('/')[-1] or 'none'}, dialogs={dialogs}, screenshot={shot}")
    finally:
        after = host_script("restore", "true" if was_enabled else "false")
        print(f"INFO restored Plugin Center switch: {'on' if was_enabled else 'off'} (state {after['after']})", flush=True)
    print(f"{'PASS' if all(results) else 'FAIL'} {sum(results)}/{len(results)}")
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
