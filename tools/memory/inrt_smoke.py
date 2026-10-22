"""Packaged-app (inrt) memory smoke test (docs/memory/ROADMAP.md P6.3).

Packages the normal-mode scripts of gate scenarios S2 (threads.start x 10 + threads.pool) and S5
(ui.layout with a button, a text and a webview, then activity.finish()) as one inrt project, launches
the packaged app --runs times and records, after each run has ended, the TOTAL PSS of its process
(dumpsys meminfo) and its thread count (/proc/<pid>/status). Between runs the app is force-stopped.
Verdict: neither series grows monotonically and its Theil-Sen trend (median pairwise slope times
runs - 1) is no more than --pss-tolerance-mb / --thread-tolerance.

Packaging is the one tools/sdk37/build_inrt_storage_fixtures.py uses: the inrt debug template from
`./gradlew :app:assembleInrtDebug` keeps its runtime DEX, resources and manifest; only assets/project
is replaced, then the APK is aligned and signed with the host's own key (sign.properties), because inrt
declares a signature-level permission shared with the host and must coexist with the debug host. The
inrt code is not changed. The script never taps the screen and never changes settings; an inrt app that
was installed before is pulled first and installed back afterwards (otherwise the smoke APK is removed).

Output: one line per run (PSS, threads) and the verdict; the report is
docs/memory/evidence/inrt-smoke-<model>-<template versionCode>[-<label>]-<yyyyMMdd>.md and the same-named .json.
"""
import argparse
import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import statistics
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
# Appended, not prepended: tools/sdk37 has its own test_run_gate.py, which must not shadow ours in unittest discovery.
sys.path.append(str(ROOT / "tools" / "sdk37"))
import physical_device  # noqa: E402

PACKAGE = "org.autojs.autojs6.inrt"
MARKER = "files/inrt-smoke.jsonl"
WORK_DIR = ROOT / "app" / "build" / "memory-inrt-smoke"

# Normal-mode S2 then S5 (app/src/androidTest/.../scenarios/GateScenarios.kt) in one ui script. The exit
# listener appends one line to the marker file, which tells the driver that the run has ended.
MAIN_JS = """"ui";
var HOLD = 50;
for (var i = 0; i < 10; i++) {
    threads.start(function () { sleep(HOLD); });
}
var pool = threads.pool({ corePoolSize: 2, maxPoolSize: 2 });
for (var j = 0; j < 4; j++) {
    pool.execute(function () { sleep(HOLD); });
}
pool.shutdown();
events.on('exit', function () {
    var writer = new java.io.FileWriter(new java.io.File(context.getFilesDir(), 'inrt-smoke.jsonl'), true);
    try {
        writer.write(JSON.stringify({ endedAt: Date.now() }) + '\\n');
    } finally {
        writer.close();
    }
});
ui.layout(
    <vertical>
        <button id="gateButton" text="memory gate"/>
        <text id="gateText" text="memory gate"/>
        <webview id="gateWeb" h="120"/>
    </vertical>
);
ui.gateWeb.loadData('<html><body>memory gate</body></html>', 'text/html', 'utf-8');
setTimeout(function () { activity.finish(); }, 300);
"""


def load_fixture_module():
    """read_signing_properties of the storage fixture builder (same signing rules as the Gradle config)."""
    path = ROOT / "tools" / "sdk37" / "build_inrt_storage_fixtures.py"
    spec = importlib.util.spec_from_file_location("build_inrt_storage_fixtures", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def build_tools(sdk):
    versions = sorted((p for p in (sdk / "build-tools").iterdir() if (p / "zipalign.exe").exists() or (p / "zipalign").exists()),
                      key=lambda p: [int(x) if x.isdigit() else 0 for x in re.split(r"[.-]", p.name)])
    if not versions:
        raise SystemExit(f"No build-tools with zipalign under {sdk}")
    return versions[-1]


def project_config(build_id):
    return {
        "name": "AutoJs6 memory inrt smoke", "packageName": PACKAGE, "versionName": "1.0.0", "versionCode": 1,
        "main": "main.js", "build": {"id": build_id, "number": 1, "time": 0},
        "launchConfig": {"requiresSharedStorage": False, "splashVisible": False, "logsVisible": False,
                         "launcherVisible": True, "runOnBoot": False},
    }


def package_apk(template, signing_properties, sdk, jdk):
    """Replace assets/project of the inrt template with the smoke project, align and sign it with the host key."""
    WORK_DIR.mkdir(parents=True, exist_ok=True)
    tools = build_tools(sdk)
    exe, bat = (".exe", ".bat") if os.name == "nt" else ("", "")
    signing = load_fixture_module().read_signing_properties(signing_properties)
    env = dict(os.environ, JAVA_HOME=str(jdk))
    digest = hashlib.sha256(template.read_bytes()).hexdigest()
    unsigned, aligned, apk = (WORK_DIR / name for name in ("smoke-unsigned.apk", "smoke-aligned.apk", "inrt-smoke.apk"))
    with zipfile.ZipFile(template) as source, zipfile.ZipFile(unsigned, "w") as dest:
        for entry in source.infolist():
            name = entry.filename
            if name.startswith("assets/project/") or (name.startswith("META-INF/") and name.upper().endswith((".SF", ".RSA", ".DSA", ".EC", "MANIFEST.MF"))):
                continue
            dest.writestr(entry, source.read(name))
        config = project_config("memory-inrt-smoke-" + digest[:12])
        dest.writestr("assets/project/project.json", json.dumps(config), compress_type=zipfile.ZIP_DEFLATED)
        dest.writestr("assets/project/main.js", MAIN_JS, compress_type=zipfile.ZIP_DEFLATED)

    def run(*command, extra_env=None):
        return subprocess.run([str(v) for v in command], env=dict(env, **(extra_env or {})), check=True,
                              capture_output=True, text=True, encoding="utf-8", errors="replace").stdout

    run(tools / ("zipalign" + exe), "-f", "-P", "16", "4", unsigned, aligned)
    # Passwords travel through the environment so they never appear on a command line.
    run(tools / ("apksigner" + bat), "sign", "--ks", signing["storeFile"], "--ks-key-alias", signing["keyAlias"],
        "--ks-pass", "env:AJ6_SMOKE_KS_PASS", "--key-pass", "env:AJ6_SMOKE_KEY_PASS", "--out", apk, aligned,
        extra_env={"AJ6_SMOKE_KS_PASS": signing["storePassword"], "AJ6_SMOKE_KEY_PASS": signing["keyPassword"]})
    badging = run(tools / ("aapt2" + exe), "dump", "badging", apk)
    unsigned.unlink()
    aligned.unlink()
    if f"package: name='{PACKAGE}'" not in badging:
        raise SystemExit("The template is not an inrt APK")
    version = re.search(r"versionCode='(\d+)'", badging)
    return {"apk": apk, "apkSha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "templateSha256": digest,
            "templateVersionCode": int(version[1]) if version else None, "buildTools": tools.name}


def parse_pss_kb(text):
    """TOTAL PSS of dumpsys meminfo <pid>: the App Summary line (API 28+) or the TOTAL row of the table (older)."""
    found = re.search(r"TOTAL PSS:\s+(\d+)", text) or re.search(r"^\s*TOTAL\s+(\d+)", text, re.MULTILINE)
    return int(found[1]) if found else None


def parse_threads(text):
    found = re.search(r"^Threads:\s+(\d+)", text, re.MULTILINE)
    return int(found[1]) if found else None


def monotonic_growth(values):
    """True when every value is >= the previous one and the last exceeds the first."""
    return len(values) > 1 and all(b >= a for a, b in zip(values, values[1:])) and values[-1] > values[0]


def trend(values):
    """Theil-Sen estimate of the change from the first run to the last: the median pairwise slope times (n - 1).

    Not the medians of the first and last few runs: a fresh process lands on one of two PSS levels about 8 MB apart
    (23046RP50C, 2026-10-22), so a few runs at each end can differ by a whole level with no trend at all.
    """
    if len(values) < 2:
        return 0
    slopes = [(values[j] - values[i]) / (j - i) for i in range(len(values)) for j in range(i + 1, len(values))]
    return statistics.median(slopes) * (len(values) - 1)


def verdict(runs, pss_tolerance_kb, thread_tolerance):
    problems = []
    if not runs or any(not r.get("ended") or r.get("pssKb") is None or r.get("threads") is None for r in runs):
        problems.append("a run did not end or could not be measured")
        return problems
    pss = [r["pssKb"] for r in runs]
    threads = [r["threads"] for r in runs]
    if monotonic_growth(pss):
        problems.append("PSS grows monotonically")
    if trend(pss) > pss_tolerance_kb:
        problems.append(f"PSS trend over the runs is +{trend(pss) / 1024:.2f} MB")
    if monotonic_growth(threads):
        problems.append("thread count grows monotonically")
    if trend(threads) > thread_tolerance:
        problems.append(f"thread count trend over the runs is +{trend(threads):g}")
    return problems


def marker_lines(device):
    result = device.shell("run-as", PACKAGE, "cat", MARKER)
    return len([line for line in result["stdout"].splitlines() if line.startswith("{")]) if result["exitCode"] == 0 else 0


def process_id(device):
    result = device.shell("pidof", PACKAGE)
    text = result["stdout"].strip()
    return int(text.split()[0]) if result["exitCode"] == 0 and text.split() and text.split()[0].isdigit() else None


def thread_count(device, pid):
    for command in (("cat", f"/proc/{pid}/status"), ("run-as", PACKAGE, "cat", f"/proc/{pid}/status")):
        value = parse_threads(device.shell(*command)["stdout"])
        if value is not None:
            return value
    return None


def installed_paths(device, package):
    result = device.shell("pm", "path", package)
    return [line[len("package:"):].strip() for line in result["stdout"].splitlines() if line.startswith("package:")]


def version_code(device, package):
    found = re.search(r"versionCode=(\d+)", device.shell("dumpsys", "package", package)["stdout"])
    return int(found[1]) if found else None


def launcher_component(device):
    # A debug inrt app has a second launcher activity (LeakCanary's), with which resolve-activity answers the chooser.
    result = device.shell("cmd", "package", "query-activities", "--brief", "-a", "android.intent.action.MAIN",
                          "-c", "android.intent.category.LAUNCHER", PACKAGE)
    return pick_launcher(result["stdout"])


def pick_launcher(output):
    components = [line.strip() for line in output.splitlines() if line.strip().startswith(PACKAGE + "/")]
    preferred = [component for component in components if "/leakcanary." not in component]
    return (preferred or components or [None])[0]


def markdown(meta, runs, problems):
    mb = lambda kb: f"{kb / 1024:.2f}" if kb is not None else "-"
    lines = [
        f"# inrt memory smoke: {meta['device']['model']} (API {meta['device']['sdk']})", "",
        "ROADMAP P6.3: the normal-mode scripts of gate scenarios S2 and S5 packaged as one inrt project "
        "(tools/memory/inrt_smoke.py), launched once per run; PSS and threads are read after the run has ended, "
        "then the app is force-stopped.", "",
        f"- Started: {meta['started']}; finished: {meta['finished']}",
        f"- Device: {meta['device']['model']} ({meta['device']['serial']}), Android {meta['device']['release']} / API {meta['device']['sdk']}",
        f"- Template: inrt debug versionCode {meta['templateVersionCode']}, sha256 `{meta['templateSha256'][:16]}`; "
        f"smoke APK sha256 `{meta['apkSha256'][:16]}` (assets/project replaced, signed with the host key, build-tools {meta['buildTools']})",
        f"- Previously installed inrt app: {meta['previous'] or 'none'}; afterwards: {meta['restored']}",
        f"- Settle after the run ended: {meta['settleSeconds']} s; tolerances: PSS {meta['pssToleranceMb']} MB, threads {meta['threadTolerance']}",
        f"- Verdict: **{'PASS' if not problems else 'FAIL'}**" + ("" if not problems else " (" + "; ".join(problems) + ")"), "",
        "| Run | Ended (s) | PID | TOTAL PSS MB | Threads |", "| --- | --- | --- | --- | --- |",
    ]
    for r in runs:
        lines.append(f"| {r['run']} | {r.get('endSeconds', '-')} | {r.get('pid') or '-'} | {mb(r.get('pssKb'))} | {r.get('threads', '-')} |")
    if runs and not problems:
        pss = [r["pssKb"] for r in runs]
        threads = [r["threads"] for r in runs]
        lines += ["", f"PSS {mb(min(pss))}..{mb(max(pss))} MB (Theil-Sen trend over the runs {trend(pss) / 1024:+.2f} MB), "
                      f"threads {min(threads)}..{max(threads)} (trend {trend(threads):+g})."]
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--template", type=Path, default=ROOT / "app/build/outputs/apk/inrt/debug/inrt-v6.8.0-universal.apk")
    parser.add_argument("--signing-properties", type=Path, default=ROOT / "sign.properties")
    parser.add_argument("--sdk", type=Path, default=Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "."))
    parser.add_argument("--jdk", type=Path, default=Path(os.environ.get("JAVA_HOME") or "."))
    parser.add_argument("--runs", type=int, default=10)
    parser.add_argument("--timeout", type=int, default=120, help="Seconds a run may take to end")
    parser.add_argument("--settle", type=int, default=3, help="Seconds between the end of a run and the measurement")
    parser.add_argument("--pss-tolerance-mb", type=float, default=4.0)
    parser.add_argument("--thread-tolerance", type=int, default=2)
    parser.add_argument("--label", default="")
    parser.add_argument("--out", type=Path, default=ROOT / "docs" / "memory" / "evidence")
    args = parser.parse_args()
    if args.label and not re.fullmatch(r"[a-z0-9-]{1,32}", args.label):
        parser.error("--label must be lower-case letters, digits or '-'")
    if not 2 <= args.runs <= 50:
        parser.error("--runs must be between 2 and 50")
    if not args.template.is_file():
        parser.error(f"Template not found: {args.template} (./gradlew :app:assembleInrtDebug)")

    device = physical_device.Device(args.adb, args.serial)
    device.require_online()
    identity = device.identity()
    started = datetime.datetime.now().astimezone()
    built = package_apk(args.template, args.signing_properties, args.sdk, args.jdk)

    # Keep whatever inrt app the device had, so that it can be installed back.
    previous_paths = installed_paths(device, PACKAGE)
    previous = None
    backup = []
    backup_dir = WORK_DIR / ("previous-" + re.sub(r"[^\w.-]", "_", args.serial))
    if backup_dir.is_dir() and any(backup_dir.iterdir()):
        # Left by an interrupted run: what is installed now may be the smoke app, so backing it up would lose the original.
        raise SystemExit(f"{backup_dir} is not empty: install those APKs back (or remove the directory) first")
    if previous_paths:
        previous = f"versionCode {version_code(device, PACKAGE)}"
        backup_dir.mkdir(parents=True, exist_ok=True)
        for index, remote in enumerate(previous_paths):
            local = backup_dir / f"{index}-{Path(remote).name}"
            if device.call("pull", remote, str(local), timeout=300)["exitCode"] != 0 or not local.is_file():
                raise SystemExit(f"Cannot back up the installed inrt app ({remote}); nothing was installed")
            backup.append(local)
    install = device.call("install", "-r", "-t", "-d", str(built["apk"]), timeout=300)
    if install["exitCode"] != 0 or "Success" not in install["stdout"]:
        raise SystemExit("Install failed: " + (install["stdout"] + install["stderr"]).strip()[-300:])
    component = launcher_component(device)
    if not component:
        raise SystemExit("No launcher activity for " + PACKAGE)

    runs = []
    try:
        device.shell("am", "force-stop", PACKAGE)
        device.shell("run-as", PACKAGE, "rm", "-f", MARKER)
        for index in range(1, args.runs + 1):
            device.shell("am", "force-stop", PACKAGE)
            device.shell("input", "keyevent", "KEYCODE_WAKEUP")
            before = marker_lines(device)
            begin = time.monotonic()
            device.shell("am", "start", "-W", "-n", component, timeout=60)
            record = {"run": index, "ended": False}
            while time.monotonic() - begin < args.timeout:
                if marker_lines(device) > before:
                    record["ended"] = True
                    record["endSeconds"] = round(time.monotonic() - begin, 1)
                    break
                time.sleep(0.5)
            if record["ended"]:
                time.sleep(args.settle)
                pid = process_id(device)
                record["pid"] = pid
                if pid:
                    record["pssKb"] = parse_pss_kb(device.shell("dumpsys", "meminfo", str(pid), timeout=60)["stdout"])
                    record["threads"] = thread_count(device, pid)
            runs.append(record)
            pss = f"{record['pssKb'] / 1024:.2f}MB" if record.get("pssKb") is not None else "-"
            print(f"run {index:2d}: {'ended' if record['ended'] else 'TIMEOUT'} pss={pss} threads={record.get('threads', '-')}", flush=True)
            device.shell("am", "force-stop", PACKAGE)
    finally:
        device.shell("am", "force-stop", PACKAGE)
        device.shell("run-as", PACKAGE, "rm", "-f", MARKER)
        if backup:
            command = ("install-multiple" if len(backup) > 1 else "install", "-r", "-t", "-d", *map(str, backup))
            result = device.call(*command, timeout=300)
            if result["exitCode"] == 0:
                restored = f"installed back (versionCode {version_code(device, PACKAGE)})"
                shutil.rmtree(backup_dir)
            else:
                restored = f"RESTORE FAILED (the APKs stay in {backup_dir})"
        else:
            result = device.call("uninstall", PACKAGE, timeout=120)
            restored = "smoke APK uninstalled" if result["exitCode"] == 0 else "UNINSTALL FAILED"

    problems = verdict(runs, args.pss_tolerance_mb * 1024, args.thread_tolerance)
    finished = datetime.datetime.now().astimezone()
    meta = {
        "started": started.isoformat(timespec="seconds"), "finished": finished.isoformat(timespec="seconds"),
        "device": identity, "templateVersionCode": built["templateVersionCode"], "templateSha256": built["templateSha256"],
        "apkSha256": built["apkSha256"], "buildTools": built["buildTools"], "previous": previous, "restored": restored,
        "runs": args.runs, "settleSeconds": args.settle, "pssToleranceMb": args.pss_tolerance_mb,
        "threadTolerance": args.thread_tolerance, "verdict": "PASS" if not problems else "FAIL", "problems": problems,
    }
    model = re.sub(r"[^A-Za-z0-9_-]+", "_", identity.get("model") or args.serial)
    name = f"inrt-smoke-{model}-{built['templateVersionCode']}" + (f"-{args.label}" if args.label else "") + f"-{started:%Y%m%d}"
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / (name + ".md")).write_text(markdown(meta, runs, problems), encoding="utf-8")
    physical_device.write_json(args.out / (name + ".json"), {**meta, "results": runs})
    print(f"{'PASS' if not problems else 'FAIL'}" + ("" if not problems else ": " + "; ".join(problems)) + f" ({restored}); report {name}.md")
    return 0 if not problems else 2


if __name__ == "__main__":
    sys.exit(main())
