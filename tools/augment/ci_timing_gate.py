"""Regression gate for the startup timing and the failing call timing of the script module layer (ROADMAP P6.1).

Usage: py -3.12 -I -u tools/augment/ci_timing_gate.py -s SERIAL [--apk-dir app/build/outputs/apk] [--no-install]
    [--baseline tools/augment/baseline/timing-gate.json] [--profile NAME] [--out DIR] [--label NAME]
    [--warmup 100] [--rounds 2] [--runs 3] [--startup-runs 10] [--calls NAME ...] [--no-rerun]

Design and thresholds: docs/augment/P6-1-ci-timing-gate.md. .github/workflows/augment-timing-gate.yml only calls this
script; a local run on an AVD of the same recipe (API 35, google_apis, x86_64, pixel_7, 2 cores, 4096M) takes the same
steps. It installs the host and changes settings, so it refuses devices that are not emulators; phones stay with
device_r2.py (R2 on XQ-AT72 is judged by hand, the numbers of the two are not comparable).

1. Wait for the framework, install the debug host of the device ABI (-r -t -g, from output-metadata.json under
   --apk-dir) unless --no-install, allow all files access, unlock, animations off, stay awake, the cached apps
   freezer off (restored at the end), a 16 MiB logcat buffer.
2. Force-stop the host and warm the new process up with --warmup startup timing scripts.
3. --rounds rounds, each over bench/error_path.js, bench/calls_prelude.js + bench/calls/NAME.js for every --calls file
   (all with failing calls by default) and the startup timing, --runs runs of each script (--startup-runs of the
   startup timing). A run without its end line within --script-timeout is a "timeout" problem; the process is replaced
   before the next script, since the run goes on in the host.
4. Values: the better (lower) of the round medians of runtime-init-prologue (ms), and for each failing call the
   ratio of its fastest run to the fastest run of its Rhino reference over all rounds. In a calls file the reference
   is `undefined.x` measured right after the case (GATE_SHIM); in error_path.js it is that script's own (as
   device_r2.py: argument errors to `undefined.x`, Java exceptions to a Java exception thrown through Rhino's own
   bridge, `e.stack` reads to the same read after `undefined.x`).
5. Checks against the --profile entry of --baseline (or its fallbackProfile, with the wider *Fallback factors):
   prologue <= baseline x prologueFactor and <= prologueCapMs; each ratio <= max(ratioLimit, baseline ratio x
   ratioFactor) (a case without a baseline ratio: <= ratioLimit); a case of the baseline that a script run did not
   produce fails ("missing"), so a script that stops early cannot pass.
6. After a failed check (and only then) the scripts with failures run again in a new process (force-stop, the same
   warm-up); each of their cases is then checked with the better value of the two attempts (a regression is there in
   both, noise rarely twice), timeouts with the rerun's. Both attempts are reported.

Output: CI_TIMING_* lines (CI_TIMING_RESULT PASS|FAIL with the counts, one CI_TIMING_PROBLEM line per failed check
with value, baseline and limit, one CI_TIMING_KNOWN line per case with a note in the baseline file), the result JSON in --out (the input of ci_timing_baseline.py), and in GitHub Actions
the table in the job summary. Exit code: PASS 0, FAIL 1, an error before a verdict 2.
"""
import argparse
import datetime
import json
import os
import pathlib
import platform
import re
import statistics
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import device_bench as db  # noqa: E402

ROOT = HERE.parents[1]
DEFAULT_BASELINE = HERE / "baseline" / "timing-gate.json"
CASE_RE = re.compile(r"AJ6BENCH (?:call|error) (.+) ([\d.]+) us$")
PROLOGUE = "runtime-init-prologue"
EPILOGUE = "runtime-init-epilogue"
ERROR_PATH = "error_path"
STARTUP = "startup"
FRAMEWORK_TIMEOUT = 300
SCHEMA = 1
NEAR = "near "
# Put between bench/calls_prelude.js and a calls file: every failing call is followed by a measurement of the prelude's
# Rhino reference (`.x` on undefined), and its ratio is taken to that neighbouring measurement. It reads its own
# variable: calls files use the prelude's `u` as a sink (`u = keys.home`), after which `u.x` no longer throws. The prelude measures the
# reference once at the start of the script, and the emulator's speed drifts with its host within a script (the
# reference of the same script ranged 48-278 us between rounds of one local run).
GATE_SHIM = """
// ci_timing_gate.py: every failing call is followed by its Rhino reference ("near " + label).
let aj6GateTime = time;
let aj6GateUndefined;
time = function (label, fn) {
    aj6GateTime(label, fn);
    if (String(label).startsWith("fail ")) aj6GateTime("near " + label, () => { try { aj6GateUndefined.x; } catch (e) { } });
};
"""


def calls_files():
    """bench/calls/NAME.js files that have failing calls ("fail " cases), sorted by name."""
    return sorted(p.stem for p in (HERE / "bench" / "calls").glob("*.js") if 'time("fail ' in p.read_text(encoding="utf-8"))


def reference_of(script, label):
    """The Rhino reference a failing case is compared with (device_r2.reference_of), or None for other cases."""
    if script == ERROR_PATH:
        if label == "module-java-exception":
            return "rhino-java-exception"
        if label == "module-arity-read-stack":
            return "rhino-undefined-property-read-stack"
        if label.startswith("module-") and label != "module-success":
            return "rhino-undefined-property"
        return None
    return "ref rhino-undefined-property" if label.startswith("fail ") else None


def shell(s, *command):
    return db.adb(s, "shell", *command).stdout.strip()


def wait_for_framework(s):
    """Boot completed, boot animation stopped, activity and package services answering, unchanged for 10 seconds."""
    began, steady_since = time.monotonic(), None
    while time.monotonic() - began < FRAMEWORK_TIMEOUT:
        ready = (shell(s, "getprop", "sys.boot_completed") == "1"
                 and shell(s, "getprop", "init.svc.bootanim") in ("stopped", "")
                 and "activity: found" in shell(s, "service", "check", "activity")
                 and shell(s, "pm", "path", "android").startswith("package:"))
        if ready:
            steady_since = steady_since or time.monotonic()
            if time.monotonic() - steady_since >= 10:
                print(f"CI_TIMING_FRAMEWORK_READY waited={time.monotonic() - began:.0f}s", flush=True)
                return
        else:
            steady_since = None
        time.sleep(2)
    raise RuntimeError(f"the framework did not answer within {FRAMEWORK_TIMEOUT} s")


def host_apk(apk_dir, abis):
    """(path, versionCode) of the debug host split for the first device ABI that has one, else of the universal APK."""
    host_dir = apk_dir / "app" / "debug"
    elements = json.loads((host_dir / "output-metadata.json").read_text(encoding="utf-8")).get("elements") or []
    for abi in abis:
        for element in elements:
            if any(f.get("filterType") == "ABI" and f.get("value") == abi for f in element.get("filters") or []):
                return host_dir / element["outputFile"], element.get("versionCode")
    for element in elements:
        if element.get("type") in ("UNIVERSAL", "SINGLE"):
            return host_dir / element["outputFile"], element.get("versionCode")
    raise RuntimeError(f"no debug host APK for ABIs {abis} in {host_dir}")


def host_machine():
    """CPU model of the machine running the emulator (the GitHub runner or the local PC)."""
    try:
        for line in pathlib.Path("/proc/cpuinfo").read_text().splitlines():
            if line.startswith("model name"):
                return line.split(":", 1)[1].strip() + f" x{os.cpu_count()}"
    except OSError:
        pass
    if sys.platform == "win32":
        out = subprocess.run(["powershell", "-NoProfile", "-Command", "(Get-CimInstance Win32_Processor).Name"],
                             capture_output=True, text=True).stdout.strip()
        if out:
            return f"{out} x{os.cpu_count()}"
    return f"{platform.processor() or platform.machine()} x{os.cpu_count()}"


def prepare(s, args, identity):
    """Fills [identity] (device, host machine, host versionCode, the freezer setting to restore)."""
    if shell(s, "getprop", "ro.kernel.qemu") != "1" and shell(s, "getprop", "ro.boot.qemu") != "1":
        raise RuntimeError(f"{s} is not an emulator; this gate installs the host and changes settings, phones stay "
                           "with device_r2.py")
    wait_for_framework(s)
    abis = [a for a in shell(s, "getprop", "ro.product.cpu.abilist").split(",") if a]
    identity.update({
        "model": shell(s, "getprop", "ro.product.model"),
        "sdk": shell(s, "getprop", "ro.build.version.sdk"),
        "abi": abis[0] if abis else "",
        "cpus": shell(s, "cat", "/sys/devices/system/cpu/online"),
        "cpusetBackground": shell(s, "cat", "/dev/cpuset/background/cpus"),
        "cpusetTopApp": shell(s, "cat", "/dev/cpuset/top-app/cpus"),
        "hostMachine": host_machine(),
    })
    if not args.no_install:
        apk, version_code = host_apk(args.apk_dir, abis)
        out = db.adb(s, "install", "-r", "-t", "-g", str(apk))
        if out.returncode != 0 or "Success" not in out.stdout:
            raise RuntimeError(f"installing {apk.name} failed: {(out.stdout + out.stderr).strip()[-400:]}")
        identity["apk"] = apk.name
    installed = re.search(r"versionCode=(\d+)", shell(s, "dumpsys", "package", db.PACKAGE))
    if not installed:
        raise RuntimeError(f"{db.PACKAGE} is not installed")
    identity["versionCode"] = int(installed[1])
    if not args.no_install and version_code is not None and int(version_code) != identity["versionCode"]:
        raise RuntimeError(f"the installed host has versionCode {identity['versionCode']}, the APK {version_code}")
    # The timing scripts are read from /sdcard/Download: since Android 11 that takes all files access, which -g does
    # not grant (without it RunIntentActivity only shows a toast and no script runs).
    if int(identity["sdk"] or 0) >= 30:
        shell(s, "appops", "set", "--uid", db.PACKAGE, "MANAGE_EXTERNAL_STORAGE", "allow")
        if "allow" not in shell(s, "appops", "get", db.PACKAGE, "MANAGE_EXTERNAL_STORAGE"):
            raise RuntimeError("all files access (MANAGE_EXTERNAL_STORAGE) could not be allowed for the host")
    # What reactivecircus/android-emulator-runner does after the boot, repeated so that a local AVD matches;
    # RunIntentActivity starts an activity, so the screen stays on.
    shell(s, "input", "keyevent", "82")
    for setting in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        shell(s, "settings", "put", "global", setting, "0.0")
    shell(s, "svc", "power", "stayon", "true")
    # Android 14+ freezes a cached process: once RunIntentActivity finishes, a host without a foreground service is
    # cached and stops in the middle of a timing script until the next activity start (seen as single "calls" of
    # seconds on API 35). The setting takes effect at once; main() restores the previous value.
    identity["freezer"] = shell(s, "settings", "get", "global", "cached_apps_freezer")
    shell(s, "settings", "put", "global", "cached_apps_freezer", "disabled")
    # Every poll reads the last device_bench.LOG_TAIL lines; the emulator's 2 MiB default lost timing lines.
    db.adb(s, "logcat", "-G", "16M")
    print("CI_TIMING_PREPARED " + " ".join(f"{k}={str(v).replace(' ', '_')}" for k, v in identity.items()), flush=True)


def restore_freezer(s, previous):
    """Puts back the cached apps freezer setting that prepare() found ("null": it was not set)."""
    if previous is None:
        return
    if previous == "null":
        shell(s, "settings", "delete", "global", "cached_apps_freezer")
    else:
        shell(s, "settings", "put", "global", "cached_apps_freezer", previous)


def process_of(s):
    """'pid cpuset' of the host process, or 'not running'."""
    return shell(s, "pid=$(pidof " + db.PACKAGE + "); [ -n \"$pid\" ] && echo $pid $(cat /proc/$pid/cpuset)") or "not running"


def push_calls(s, name):
    """Pushes bench/calls_prelude.js, GATE_SHIM and bench/calls/[name].js as one script (as device_r2.push_calls)."""
    local = HERE / "__pycache__" / f"aj6_gate_calls_{name}.js"
    local.parent.mkdir(exist_ok=True)
    prelude = (HERE / "bench" / "calls_prelude.js").read_text(encoding="utf-8")
    calls = (HERE / "bench" / "calls" / f"{name}.js").read_text(encoding="utf-8")
    local.write_text(prelude + GATE_SHIM + chr(10) + calls, encoding="utf-8")
    db.adb(s, "shell", "mkdir", "-p", db.DEVICE_DIR, check=True)
    remote = f"{db.DEVICE_DIR}/{local.name}"
    db.adb(s, "push", str(local), remote, check=True)
    return remote


class ScriptTimeout(Exception):
    pass


def run_cases(s, remote, runs, wait):
    """{label: [us, ...]} of [runs] runs of a timing script; ScriptTimeout when a run has no end line within [wait] s.

    A run that has not ended keeps running in the host, so the caller stops the process before the next script: two
    timing scripts at once would distort both.
    """
    samples = {}
    for _ in range(runs):
        lines = db.run_script(s, remote, wait)
        for line in lines:
            m = CASE_RE.search(line)
            if m:
                samples.setdefault(m.group(1), []).append(float(m.group(2)))
        if not any(db.END_RE.search(line) for line in lines):
            raise ScriptTimeout(samples)
        time.sleep(1)
    return samples


def run_startup(s, remote, runs):
    """{step: [ms, ...]} of [runs] runs of the startup timing script."""
    samples = {}
    for _ in range(runs):
        for line in db.run_script(s, remote, 30):
            m = db.TIMING_RE.search(line)
            if m and m.group(1) in (PROLOGUE, EPILOGUE):
                samples.setdefault(m.group(1), []).append(float(m.group(2)))
        time.sleep(0.5)
    return samples


class Measurement:
    def __init__(self, s, args):
        self.s = s
        self.args = args
        self.init_script = db.push_script(s, "aj6_init_timing.js")
        self.remotes = {ERROR_PATH: db.push_script(s, "error_path.js"), STARTUP: self.init_script}
        for name in args.calls:
            self.remotes[f"calls/{name}"] = push_calls(s, name)
        # Without its timing lines every script would only time out (240 s each); stop before the run instead.
        if PROLOGUE not in run_startup(s, self.init_script, 1):
            raise RuntimeError(f"the startup timing script printed no {PROLOGUE} line (a release host, or no script runs)")

    def new_process(self):
        db.adb(self.s, "shell", "am", "force-stop", db.PACKAGE)
        time.sleep(1)
        run_startup(self.s, self.init_script, self.args.warmup)
        print(f"CI_TIMING_WARMUP scripts={self.args.warmup} process={process_of(self.s).replace(' ', ':')}", flush=True)

    def measure(self, scripts):
        """({script: {label: [(median, n) per round]}}, {script: [round, ...] that timed out}) in a new process.

        A script that times out loses that round (its partial lines are dropped) and the process is replaced before
        the next script, so that no two timing scripts overlap.
        """
        self.new_process()
        rounds = {script: {} for script in scripts}
        timeouts = {}
        for r in range(self.args.rounds):
            began = time.monotonic()
            before = process_of(self.s)
            for script in scripts:
                try:
                    if script == STARTUP:
                        runs = run_startup(self.s, self.remotes[script], self.args.startup_runs)
                    else:
                        runs = run_cases(self.s, self.remotes[script], self.args.runs, self.args.script_timeout)
                except ScriptTimeout:
                    print(f"CI_TIMING_TIMEOUT {script} round={r + 1} seconds={self.args.script_timeout}", flush=True)
                    timeouts.setdefault(script, []).append(r + 1)
                    self.new_process()
                    continue
                for label, values in runs.items():
                    rounds[script].setdefault(label, [None] * self.args.rounds)[r] = (statistics.median(values), len(values),
                                                                                      values)
            after = process_of(self.s)
            print(f"CI_TIMING_ROUND {r + 1} scripts={len(scripts)} seconds={time.monotonic() - began:.0f} "
                  f"process={before.replace(' ', ':')}" + ("" if after == before else f"->{after.replace(' ', ':')}"),
                  flush=True)
        return rounds, timeouts


def fastest(cells):
    """The lowest sample of all rounds of one case, or None."""
    samples = [x for c in cells if c for x in c[2]]
    return min(samples) if samples else None


def values_of(rounds):
    """The checked values of one attempt: {key: {"value": ..., "rounds": [...], ...}}.

    Keys: "startup: runtime-init-prologue" / "startup: runtime-init-epilogue" (ms; value: the better round median),
    "SCRIPT: LABEL" for every failing call (value: its fastest run over the fastest run of its reference, both over all
    rounds; the reference in a calls file is the neighbouring one of GATE_SHIM). Noise on an emulator only makes a run
    slower, and the fastest of 6 runs varied about half as much between whole gate runs as the ratio of round medians
    (docs/augment/P6-1-ci-timing-gate.md). "rounds" keeps the ratio of the round medians, "us" / "refUs" those medians,
    "samples" / "refSamples" every run, "preludeRounds" the ratios to the prelude's reference (device_r2.py's).
    """
    values = {}
    for script, labels in rounds.items():
        for label, cells in labels.items():
            if script == STARTUP:
                ms = [c[0] if c else None for c in cells]
                present = [v for v in ms if v is not None]
                values[f"{script}: {label}"] = {"value": min(present) if present else None, "rounds": ms,
                                                "n": [c[1] if c else 0 for c in cells],
                                                "samples": [c[2] if c else [] for c in cells]}
                continue
            ref = reference_of(script, label)
            if not ref:
                continue
            none = [None] * len(cells)
            refs = labels.get(NEAR + label, none) if script != ERROR_PATH else labels.get(ref, none)
            case_us, ref_us = fastest(cells), fastest(refs)
            entry = {
                "value": round(case_us / ref_us, 3) if case_us is not None and ref_us else None,
                "rounds": [round(c[0] / rc[0], 3) if c and rc and rc[0] > 0 else None for c, rc in zip(cells, refs)],
                "us": [c[0] if c else None for c in cells],
                "refUs": [rc[0] if rc else None for rc in refs],
                "n": [c[1] if c else 0 for c in cells],
                "samples": [c[2] if c else [] for c in cells],
                "refSamples": [rc[2] if rc else [] for rc in refs],
            }
            if script != ERROR_PATH:
                entry["preludeRounds"] = [round(c[0] / rc[0], 3) if c and rc and rc[0] > 0 else None
                                          for c, rc in zip(cells, labels.get(ref, none))]
            values[f"{script}: {label}"] = entry
    return values


def better(first, second):
    """The values of two attempts with the better (lower) value of each case: noise only makes a value larger, a
    regression is there in both attempts."""
    merged = dict(first)
    for key, v in second.items():
        old = merged.get(key, {}).get("value")
        if old is None or (v["value"] is not None and v["value"] < old):
            merged[key] = v
    return merged


def limits(baseline, profile):
    """(thresholds, profile entry or None, name of the profile used) of the baseline file."""
    thresholds = baseline.get("thresholds", {})
    profiles = baseline.get("profiles", {})
    if profile in profiles:
        return thresholds, profiles[profile], profile
    fallback = baseline.get("fallbackProfile")
    return thresholds, profiles.get(fallback), (fallback if fallback in profiles else None)


def check(values, timeouts, thresholds, entry, exact_profile, scripts):
    """[(key, value, baseline, limit, problem or None)] for every checked value, every baseline case of [scripts] and
    every script that timed out (key "SCRIPT: timeout")."""
    rows = [(f"{script}: timeout", None, None, None, "timeout") for script in timeouts]
    base_values = (entry or {}).get("values", {})
    ratio_limit = thresholds.get("ratioLimit", 1.2)
    ratio_factor = thresholds.get("ratioFactor", 1.35) if exact_profile else thresholds.get("ratioFactorFallback", 1.6)
    factor = thresholds.get("prologueFactor", 1.5) if exact_profile else thresholds.get("prologueFactorFallback", 3.0)
    cap = thresholds.get("prologueCapMs")
    keys = list(values) + [k for k in base_values if k not in values and k.split(": ")[0] in scripts]
    for key in keys:
        value = values.get(key, {}).get("value")
        base = base_values.get(key)
        if key == f"{STARTUP}: {EPILOGUE}":
            rows.append((key, value, base, None, None))
            continue
        if key == f"{STARTUP}: {PROLOGUE}":
            limit = min(x for x in (base * factor if base else None, cap) if x is not None) if (base or cap) else None
        else:
            limit = round(max(ratio_limit, base * ratio_factor), 3) if base is not None else ratio_limit
        if value is None:
            problem = "missing" if base is not None or key in values else None
        else:
            problem = "over" if limit is not None and value > limit else None
        rows.append((key, value, base, limit, problem))
    return rows


def fmt(x, unit=""):
    return "-" if x is None else (f"{x:.0f}{unit}" if unit == " ms" else f"{x:.2f}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--apk-dir", type=pathlib.Path, default=ROOT / "app" / "build" / "outputs" / "apk")
    parser.add_argument("--no-install", action="store_true", help="measure the host already installed")
    parser.add_argument("--baseline", type=pathlib.Path, default=DEFAULT_BASELINE)
    parser.add_argument("--profile", default="local-avd", help="baseline profile of the machine running the emulator")
    parser.add_argument("--out", type=pathlib.Path, default=ROOT / "build" / "augment-timing-gate")
    parser.add_argument("--label", default="", help="name of this run in the result file")
    parser.add_argument("--warmup", type=int, default=100, help="startup timing scripts before measuring")
    parser.add_argument("--rounds", type=int, default=2)
    parser.add_argument("--runs", type=int, default=3, help="runs of each script per round")
    parser.add_argument("--startup-runs", type=int, default=10, help="runs of the startup timing script per round")
    parser.add_argument("--calls", nargs="*", default=None, help="bench/calls/NAME.js (default: all with failing calls)")
    parser.add_argument("--script-timeout", type=int, default=300, help="seconds a run of one timing script may take")
    parser.add_argument("--no-rerun", action="store_true", help="no second attempt after a failed check")
    args = parser.parse_args()
    args.calls = calls_files() if args.calls is None else args.calls
    missing = [n for n in args.calls if not (HERE / "bench" / "calls" / f"{n}.js").is_file()]
    if missing:
        print(f"CI_TIMING_ERROR no such call benchmark: {', '.join(missing)}")
        return 2
    s = args.serial
    baseline = json.loads(args.baseline.read_text(encoding="utf-8")) if args.baseline.is_file() else {}
    thresholds, entry, used = limits(baseline, args.profile)
    began = time.monotonic()
    identity = {}
    try:
        prepare(s, args, identity)
        measurement = Measurement(s, args)
        scripts = [ERROR_PATH] + [f"calls/{n}" for n in args.calls] + [STARTUP]
        rounds, timeouts = measurement.measure(scripts)
        attempts = [{"values": values_of(rounds), "timeouts": timeouts}]
        rows = check(attempts[0]["values"], timeouts, thresholds, entry, used == args.profile, scripts)
        failed = {key.split(": ")[0] for key, *_, problem in rows if problem}
        if failed and not args.no_rerun:
            rerun_scripts = [x for x in scripts if x in failed]
            print(f"CI_TIMING_RERUN scripts={','.join(rerun_scripts)}", flush=True)
            rounds, timeouts = measurement.measure(rerun_scripts)
            attempts.append({"scripts": rerun_scripts, "values": values_of(rounds), "timeouts": timeouts})
            rows = check(better(attempts[0]["values"], attempts[1]["values"]), timeouts, thresholds, entry,
                         used == args.profile, scripts)
    except (RuntimeError, subprocess.CalledProcessError) as e:
        print(f"CI_TIMING_ERROR {e}")
        return 2
    finally:
        db.adb(s, "shell", "rm", "-rf", db.DEVICE_DIR)
        restore_freezer(s, identity.get("freezer"))
    problems = [r for r in rows if r[4]]
    checked = [r for r in rows if r[3] is not None]
    notes = baseline.get("notes", {})
    verdict = "FAIL" if problems else "PASS"
    note = "" if used == args.profile else f" (no baseline profile {args.profile}; using {used or 'limits only'})"
    for key, value, base, limit, problem in problems:
        unit = " ms" if key.startswith(STARTUP) else ""
        print(f"CI_TIMING_PROBLEM {key}: {problem} value={fmt(value, unit)} baseline={fmt(base, unit)} limit={fmt(limit, unit)}")
    # Known cases (a regression left to another topic, a case outside R2) stay visible on every run.
    for key, value, base, limit, problem in rows:
        if key in notes:
            print(f"CI_TIMING_KNOWN {key}: {problem or 'ok'} value={fmt(value)} baseline={fmt(base)} limit={fmt(limit)} "
                  f"note={notes[key]}")
    print(f"CI_TIMING_RESULT {verdict} checked={len(checked)} problems={len(problems)} attempts={len(attempts)} "
          f"versionCode={identity['versionCode']} profile={used or '-'} minutes={(time.monotonic() - began) / 60:.0f}{note}")
    result = {
        "schema": SCHEMA,
        "label": args.label,
        "time": datetime.datetime.now().astimezone().isoformat(timespec="seconds"),
        # The commit of the checkout the host was built in (a clone for a local baseline), not of this script's.
        "commit": os.environ.get("GITHUB_SHA") or subprocess.run(["git", "-C", str(args.apk_dir), "rev-parse", "HEAD"],
                                                                  capture_output=True, text=True).stdout.strip(),
        "device": identity,
        "params": {"warmup": args.warmup, "rounds": args.rounds, "runs": args.runs, "startupRuns": args.startup_runs,
                   "scriptTimeout": args.script_timeout, "calls": args.calls, "profile": args.profile,
                   "baselineProfile": used},
        "attempts": attempts,
        "checks": [{"key": k, "value": v, "baseline": b, "limit": lim, "problem": p} for k, v, b, lim, p in rows],
        "verdict": verdict,
    }
    args.out.mkdir(parents=True, exist_ok=True)
    stem = f"timing-gate-{(args.label or 'run')}-{identity['versionCode']}-{time.strftime('%Y%m%d-%H%M%S')}"
    path = args.out / f"{stem}.json"
    path.write_text(json.dumps(result, indent=1, ensure_ascii=True) + "\n", encoding="utf-8")
    print(f"CI_TIMING_REPORT {path}")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        lines = [f"### Augment timing gate: {verdict}{note}", "", "| check | value | baseline | limit | result |",
                 "| --- | --- | --- | --- | --- |"]
        for key, value, base, limit, problem in sorted(rows, key=lambda r: (r[4] is None, r[0])):
            unit = " ms" if key.startswith(STARTUP) else ""
            known = f" ({notes[key]})" if key in notes else ""
            lines.append(f"| {key} | {fmt(value, unit)} | {fmt(base, unit)} | {fmt(limit, unit)} | {problem or 'ok'}{known} |")
        with open(summary, "a", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
