"""Screenshots of the memory diagnostics page and the unreclaimed runtime warning check (docs/memory/ROADMAP.md P2.2 / P2.3).

Usage:
  py -3.12 -I -u tools/memory/device_diagnostics_page.py -s SERIAL [--out DIR] [--shots developer,page,actions]
  py -3.12 -I -u tools/memory/device_diagnostics_page.py -s SERIAL --warning-check [--pin 60] [--out DIR]

Pages (--shots): `developer` opens Developer options and scrolls to the "Memory diagnostics" category, `page` opens the memory
diagnostics page, `actions` scrolls that page to its end (unreclaimed runtimes and actions). Neither page is exported, so, as in
tools/augment/device_developer_options.py, a small script started through RunIntentActivity opens them from the app's own
process, and scrolling is done in that process too (no taps, no swipes). Screenshots go to --out (default
tools/memory/__pycache__/, not committed); read them and write the findings into the roadmap.

--warning-check (P2.3): script B ends normally on its own first and no warning may follow within --quiet seconds (negative
control). Then script A starts B again, keeps the engine objects returned by engines.all() for --pin seconds and ends;
while A holds it, the runtime of B cannot be reclaimed, so about 10 s after B ends the host must print the warning (checked in
logcat through the MemoryDiagnostics tag, together with the id, exit kind and elapsed time). The diagnostics page is
screenshot while B is held, and after A has ended a probe script calls MemoryDiagnostics.collect() and must no longer list
A or B. The developer options switch must be on (its default). The screen must be on and unlocked; the script only checks
it. Output: one PASS / FAIL line per check with the key numbers.
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
DEVICE_DIR = "/sdcard/Download/aj6-memory-diag"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
PACKAGE = "org.autojs.autojs6"
DEVELOPER_OPTIONS = "org.autojs.autojs.ui.settings.DeveloperOptionsActivity"
DIAGNOSTICS = "org.autojs.autojs.ui.settings.memory.MemoryDiagnosticsActivity"
WARNING_KEY = "key_$_memory_unreclaimed_warning"
MARK = "AJ6DIAG"
WARN_TAG = "MemoryDiagnostics"


def adb(serial, *args, check=False, timeout=60):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", check=check, timeout=timeout)


def logcat(serial):
    return adb(serial, "logcat", "-d", "-v", "threadtime").stdout.splitlines()


def push_script(serial, name, lines):
    local = HERE / "__pycache__" / name
    local.parent.mkdir(exist_ok=True)
    local.write_text(chr(10).join(lines) + chr(10), encoding="utf-8")
    remote = f"{DEVICE_DIR}/{name}"
    adb(serial, "push", str(local), remote, check=True)
    return remote


def start_script(serial, remote):
    adb(serial, "shell", "am", "start", "-n", f"{PACKAGE}/{RUN_ACTIVITY}", "-e", "path", remote, check=True)


def wait_mark(serial, tag, wait, seen):
    """Waits for a console line `MARK tag value` not in [seen] and returns value, or None after [wait] seconds."""
    deadline = time.time() + wait
    needle = f"{MARK} {tag} "
    while time.time() < deadline:
        time.sleep(0.5)
        for line in logcat(serial):
            start = line.find(needle)
            if start >= 0 and line not in seen:
                seen.add(line)
                return line[start + len(needle):].strip()
    return None


def run_script(serial, name, lines, tag, wait=30):
    seen = set(logcat(serial))
    start_script(serial, push_script(serial, name, lines))
    value = wait_mark(serial, tag, wait, seen)
    if value is None:
        sys.exit(f"no {MARK} {tag} output from {name}")
    return value


def screen_state(serial):
    power = adb(serial, "shell", "dumpsys", "power").stdout
    window = adb(serial, "shell", "dumpsys", "window").stdout
    awake = "mWakefulness=Awake" in power
    locked = bool(re.search(r"(mDreamingLockscreen=true|isKeyguardShowing=true|mShowingLockscreen=true|showing=true)", window))
    return awake, locked


def resumed_activity(serial):
    top = adb(serial, "shell", "dumpsys", "activity", "activities").stdout
    return next((line.strip() for line in top.splitlines() if "topResumedActivity" in line or "mResumedActivity" in line), "")


def screenshot(serial, out, label):
    out.mkdir(parents=True, exist_ok=True)
    shot = out / f"{label}-{serial}-{time.strftime('%Y%m%d-%H%M%S')}.png"
    with shot.open("wb") as f:
        subprocess.run(["adb", "-s", serial, "exec-out", "screencap", "-p"], stdout=f, check=True)
    return shot


def open_page(serial, activity, scroll, tag):
    """Opens [activity] in the app process; [scroll] is JS run on the UI thread with `fragment` bound, or None."""
    lines = [
        f"var intent = new android.content.Intent(context, {activity});",
        "intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);",
        "context.startActivity(intent);",
        "var shown = null;",
        "for (var i = 0; i < 40 && shown == null; i++) {",
        "    sleep(150);",
        "    var a = org.autojs.autojs.AutoJs.getInstance().getAppUtils().getCurrentActivity();",
        f"    if (a != null && String(a.getClass().getName()) == '{activity}') shown = a;",
        "}",
        f"if (shown == null) {{ console.log('{MARK} {tag} not-shown'); exit(); }}",
    ]
    if scroll:
        lines += [
            "var done = false;",
            "shown.runOnUiThread(new java.lang.Runnable({ run: function () {",
            "    var fragments = shown.getSupportFragmentManager().getFragments();",
            "    var fragment = fragments.size() > 0 ? fragments.get(0) : null;",
            f"    try {{ if (fragment != null) {{ {scroll} }} }} finally {{ done = true; }}",
            "}}));",
            "for (var j = 0; j < 40 && !done; j++) sleep(100);",
        ]
    lines.append(f"console.log('{MARK} {tag} shown');")
    return run_script(serial, f"aj6_open_{tag}.js", lines, tag)


def take_shots(serial, shots, out, wait):
    results = {}
    if "developer" in shots:
        state = open_page(serial, DEVELOPER_OPTIONS, f"fragment.scrollToPreference('{WARNING_KEY}');", "developer")
        time.sleep(wait)
        results["developer"] = (state, screenshot(serial, out, "developer-options-memory"))
    if "page" in shots:
        state = open_page(serial, DIAGNOSTICS, None, "page")
        time.sleep(max(wait, 2.5))
        results["page"] = (state, screenshot(serial, out, "memory-diagnostics"))
    if "actions" in shots:
        scroll = "var list = fragment.getListView(); list.scrollToPosition(list.getAdapter().getItemCount() - 1);"
        state = open_page(serial, DIAGNOSTICS, scroll, "actions")
        time.sleep(max(wait, 2.5))
        results["actions"] = (state, screenshot(serial, out, "memory-diagnostics-actions"))
    for label, (state, shot) in results.items():
        print(f"{'PASS' if state == 'shown' else 'FAIL'} shot {label}: {state}, {shot}")
    return all(state == "shown" for state, _ in results.values())


B_LINES = [
    f"console.log('{MARK} b started');",
    "sleep(1500);",
    f"console.log('{MARK} b ending');",
]


def a_lines(b_remote, pin_s):
    return [
        f"var b = engines.execScriptFile('{b_remote}');",
        "var held = [];",
        "for (var i = 0; i < 100 && held.length == 0; i++) {",
        "    sleep(50);",
        f"    held = engines.all().filter(function (e) {{ return String(e.getSource()).indexOf('aj6_mem_b') >= 0; }});",
        "}",
        f"console.log('{MARK} a holding ' + held.length);",
        f"sleep({pin_s * 1000});",
        "held = null;",
        "b = null;",
        f"console.log('{MARK} a released');",
    ]


PROBE_LINES = [
    "var d = org.autojs.autojs.runtime.memory.MemoryDiagnostics.INSTANCE;",
    "var s = d.collect(10000);",
    "var names = [];",
    "var r = d.retained();",
    "for (var i = 0; i < r.size(); i++) names.push(String(r.get(i).getName()));",
    f"console.log('{MARK} probe ' + JSON.stringify({{ retained: s.getRetainedRuntimes(), names: names, engines: s.getEngines() }}));",
]


def warning_lines(serial, since):
    """Warnings for script B logged by the MemoryDiagnostics tag after the logcat lines in [since]."""
    return [line for line in logcat(serial) if line not in since and f" W {WARN_TAG}" in line and "aj6_mem_b" in line]


def warning_check(serial, pin_s, quiet_s, out):
    ok = True
    pref = run_script(serial, "aj6_pref.js", [
        f"console.log('{MARK} pref ' + org.autojs.autojs.core.pref.Pref.INSTANCE.getBoolean('{WARNING_KEY}', true));",
    ], "pref")
    print(f"{'PASS' if pref == 'true' else 'FAIL'} warning switch: {pref}")
    ok &= pref == "true"
    b_remote = push_script(serial, "aj6_mem_b.js", B_LINES)

    # Negative control: B alone is reclaimed, so no warning may follow.
    # zh-CN: 对照: 单独运行的 B 会被回收, 不应出现警告.
    seen = set(logcat(serial))
    start_script(serial, b_remote)
    ended = wait_mark(serial, "b", 30, seen) and wait_mark(serial, "b", 30, seen)
    if ended != "ending":
        sys.exit("script B did not run")
    time.sleep(quiet_s)
    stray = warning_lines(serial, seen)
    print(f"{'PASS' if not stray else 'FAIL'} control: B alone, warnings within {quiet_s} s after it ended: {len(stray)}")
    ok &= not stray

    # B held by A: the warning must appear about 10 s after B ends.
    # zh-CN: A 持有 B: B 结束约 10 s 后应出现警告.
    seen = set(logcat(serial))
    a_remote = push_script(serial, "aj6_mem_a.js", a_lines(b_remote, pin_s))
    start_script(serial, a_remote)
    holding = wait_mark(serial, "a", 30, seen)
    b_end = None
    deadline = time.time() + 30
    while time.time() < deadline and b_end is None:
        value = wait_mark(serial, "b", 2, seen)
        if value == "ending":
            b_end = time.time()
    if b_end is None:
        sys.exit("script B did not end while A was holding it")
    warn = []
    while time.time() < b_end + 40 and not warn:
        time.sleep(1)
        warn = warning_lines(serial, seen)
    if warn:
        delay = time.time() - b_end
        info = re.search(r"\[id=(\d+), exit=(\w+), elapsed=(\d+) ms\]", warn[0])
        detail = f"id {info.group(1)}, exit {info.group(2)}, {int(info.group(3)) / 1000:.1f} s after B ended" if info else warn[0][-80:]
        print(f"PASS pinned: A {holding}, warning seen about {delay:.0f} s after B ended ({detail}), lines {len(warn)}")
    else:
        print(f"FAIL pinned: A {holding}, no warning within 40 s after B ended")
        ok = False
    console = [line for line in logcat(serial) if line not in seen and "GlobalConsole" in line and "aj6_mem_b" in line and "/W: " in line]
    print(f"{'PASS' if console else 'FAIL'} global console warn lines: {len(console)}")
    ok &= bool(console)

    shot_state = open_page(serial, DIAGNOSTICS, None, "pinned")
    time.sleep(3)
    shot = screenshot(serial, out, "memory-diagnostics-pinned")
    print(f"{'PASS' if shot_state == 'shown' else 'FAIL'} shot pinned page: {shot}")

    released = wait_mark(serial, "a", pin_s + 30, seen)
    print(f"{'PASS' if released == 'released' else 'FAIL'} A ended: {released}")
    ok &= released == "released"
    time.sleep(2)
    probe = json.loads(run_script(serial, "aj6_probe.js", PROBE_LINES, "probe", wait=40))
    leftover = [n for n in probe["names"] if n.startswith("aj6_mem_")]
    print(f"{'PASS' if not leftover else 'FAIL'} after A ended: collect() retained {probe['retained']}, A / B listed {leftover}, engines {probe['engines']}")
    ok &= not leftover
    return ok


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--out", type=pathlib.Path, default=HERE / "__pycache__")
    parser.add_argument("--shots", default="developer,page,actions")
    parser.add_argument("--wait", type=float, default=2.0, help="seconds between opening a page and its screenshot")
    parser.add_argument("--warning-check", action="store_true")
    parser.add_argument("--pin", type=int, default=60, help="seconds script A holds script B")
    parser.add_argument("--quiet", type=int, default=15, help="seconds without a warning after B ends alone")
    args = parser.parse_args()
    s = args.serial

    awake, locked = screen_state(s)
    if not awake or locked:
        sys.exit(f"the screen is {'off' if not awake else 'locked'}; turn it on and unlock it, then run again")
    adb(s, "shell", "mkdir", "-p", DEVICE_DIR, check=True)
    try:
        if args.warning_check:
            ok = warning_check(s, args.pin, args.quiet, args.out)
        else:
            ok = take_shots(s, [x.strip() for x in args.shots.split(",") if x.strip()], args.out, args.wait)
    finally:
        adb(s, "shell", "rm", "-rf", DEVICE_DIR)
    print(f"resumed: {resumed_activity(s)}")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
