"""Host-side device checks for the Paddle OCR PP-OCRv3 roadmap (plugin repository
AutoJs6-Plugin-Paddle-OCR-PP-OCRv3, ROADMAP.md P2.2 and P3.5).

    py -3 -I tools/paddle-ocr-v3/device_check.py coexistence -s SERIAL [--install APK ...] [--screenshot PNG]
    py -3 -I tools/paddle-ocr-v3/device_check.py packaging -s SERIAL --group GROUP [--install APK ...] [--uninstall PKG ...]

coexistence (P2.2), with Paddle Lite 1.1.0 and the ONNX Runtime 2.0.0 plugin installed side by side:
  installed   both packages and their versions
  discovery   both answer the org.autojs.plugin.PADDLE_OCR action
  variant     ocr.paddle(img, { variant }) reaches the matching package (host log "OcrPluginHost: select:")
  default     ocr.paddle(img) without criteria picks the ONNX Runtime plugin (D22) while every other Paddle OCR
              plugin is switched off in the Plugin Center store for that run (restored afterwards); a stored
              priority order of the paddle-ocr family is reported because it overrides the default
  The host then opens Plugin Center; --screenshot saves it.

packaging (P3.5) runs PaddleOcrOnnxPackagingDeviceTest with only the group's plugins enabled, lists lib/ of the
packaged APK (entries and the installed plugin that ships the same bytes go to the receipt), installs and runs it,
and compares its texts with the same runs in the host app. Groups:
  ort        ONNX Runtime plugin: fp32 / useSlim / useOpenCL / rotated sample with and without useCls; the packaged
             log shows the model directory, the execution provider and whether the classifier ran
  both       ONNX Runtime and Paddle Lite enabled: the packager selects the ONNX Runtime plugin (v3)
  rapid      Rapid OCR PP-OCRv3 with the ONNX Runtime plugin: libonnxruntime.so bytes are identical (the build
             succeeds) or the build stops with a Conflict
  no-opencv  the OpenCV plugin switched off: the build stops with the "requires the OpenCV plugin" error
  lite       only Paddle Lite 1.1.0: the packaged app runs the Paddle Lite engine
  old        the old Paddle OCR v3 1.0.x under the ONNX Runtime package name: the packager falls back to v3-paddle-lite

Prerequisites: the debug host and its test APK, and an APK Builder Template plugin built from a Runtime Kit of the
same host (`:app:generateRuntimeKit`, then that repository with -Pautojs.apkBuilder.templatePlugin.runtimeKitDir).
Prints one line per check with PASS / FAIL / INFO and key values; recognized text and full listings only go to
the receipts under --out. Temporary files on the device are removed afterwards.
"""
import argparse
import difflib
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tempfile
import time
import zipfile

HOST = "org.autojs.autojs6"
RUNNER = "org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner"
PRODUCER = "org.autojs.autojs.apkbuilder.template.PaddleOcrOnnxPackagingDeviceTest"
SMOKE = "org.autojs.autojs6.paddleocrpackagingsmoke"
TEMPLATE = "org.autojs.plugin.apkbuilder.template"
PADDLE_PREFIX = "io.github.supermonster003.autojs6.plugin.paddleocr."
ORT = PADDLE_PREFIX + "v3"
LITE = ORT + ".paddlelite"
RAPID_V3 = "io.github.supermonster003.autojs6.plugin.rapidocr.ppocrv3"
OPENCV = "io.github.supermonster003.autojs6.plugin.opencv"
ACTION = "org.autojs.plugin.PADDLE_OCR"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
PLUGIN_CENTER = "org.autojs.autojs.core.plugin.center.PluginCenterActivity"
HOST_FILES = f"/sdcard/Android/data/{HOST}/files"
WORK_DIR = f"{HOST_FILES}/paddle-ocr-v3"
PACKAGING_DIR = f"{HOST_FILES}/paddle-ocr-packaging"
RESULT = "paddle-ocr-v3-runs.json"

ROOT = Path(__file__).resolve().parents[2]
RUNS_SCRIPT = Path(__file__).with_name("ocr_runs.js")
SAMPLES = ROOT.parent / "AutoJs6-Plugin-Paddle-OCR-PP-OCRv3" / "app" / "src" / "androidTest" / "assets" / "samples"

SELECT = re.compile(r"^\s*(\d+)\.(\d+)\s.*select: (\S+) \(variant ([^)]*)\) for (.*)$", re.M)
LOADING = re.compile(r"^\s*(\d+)\.(\d+)\s.*OnnxPaddleOcrEngine: Loading (\S+) \(threads \d+, nnapi (\w+), cls (on|off)\)", re.M)
READY = re.compile(r"^\s*(\d+)\.(\d+)\s.*Sessions ready on (\w+) in (\d+) ms \(cls (on|off)\)", re.M)
NNAPI_FALLBACK = re.compile(r"^\s*(\d+)\.(\d+)\s.*(NNAPI execution provider unavailable|NNAPI needs Android)", re.M)

ORT_RUNS = [
    ("fp32", "zh", {"useSlim": False}),
    ("slim", "zh", {"useSlim": True}),
    ("nnapi", "zh", {"useSlim": False, "useOpenCL": True}),
    ("rotated", "zh-rotated", {"useSlim": False}),
    ("rotated cls", "zh-rotated", {"useSlim": False, "useCls": True}),
]
LITE_RUNS = [("fp32", "zh", {"useSlim": False}), ("slim", "zh", {"useSlim": True})]
QUICK_RUNS = [("fp32", "zh", {"useSlim": False})]
# Runs whose text is informational: the rotated sample is not expected to read without the classifier.
INFO_RUNS = {"rotated"}

GROUPS = {
    "ort": {"enable": [ORT], "variant": "v3", "runs": ORT_RUNS, "engine": "onnxruntime"},
    "both": {"enable": [ORT, LITE], "variant": "v3", "runs": QUICK_RUNS, "engine": "onnxruntime"},
    "rapid": {"enable": [ORT, RAPID_V3], "libs": "Paddle OCR,Rapid OCR", "variant": "v3", "runs": QUICK_RUNS,
              "engine": "onnxruntime", "conflict": "libonnxruntime.so"},
    "no-opencv": {"enable": [ORT], "disable": [OPENCV], "variant": "v3", "runs": [], "error": "openCvRequiredPrefix"},
    "lite": {"enable": [LITE], "variant": "v3-paddle-lite", "runs": LITE_RUNS, "engine": "paddle_light"},
    "old": {"enable": [ORT], "variant": "v3-paddle-lite", "runs": LITE_RUNS, "engine": "paddle_light",
            "oldPlugin": True},
}


class Device:
    def __init__(self, adb, serial):
        self.adb = adb
        self.serial = serial

    def run(self, *command, timeout=600):
        return subprocess.run([self.adb, "-s", self.serial, *command], text=True, encoding="utf-8",
                              errors="replace", capture_output=True, timeout=timeout)

    def shell(self, command, timeout=600):
        return self.run("shell", command, timeout=timeout).stdout.strip()

    def now(self):
        """Device time in the format of logcat -T."""
        return self.shell("date '+%m-%d %H:%M:%S.000'")

    def package(self, package):
        """(versionName, versionCode) of an installed package, or None."""
        dump = self.shell(f"dumpsys package {package}")
        code = re.search(r"versionCode=(\d+)", dump)
        name = re.search(r"versionName=(\S+)", dump)
        return (name.group(1) if name else "?", int(code.group(1))) if code else None

    def log(self, since, *filters):
        return self.run("logcat", "-d", "-v", "epoch", "-T", since, "-s", *filters).stdout


def report(name, passed, value):
    status = passed if isinstance(passed, str) else "PASS" if passed else "FAIL"
    print(f"{status:4} {name}: {value}", flush=True)
    return status != "FAIL"


def line_similarity(expected, texts):
    """Best-match similarity of each expected line, spaces ignored (as the plugin's device check)."""
    parts = [text.replace(" ", "") for text in texts]
    recognized = ["".join(parts[start:start + size]) for size in range(1, 5) for start in range(len(parts) - size + 1)]
    return [max((difflib.SequenceMatcher(None, line.replace(" ", ""), text).ratio() for text in recognized), default=0.0)
            for line in expected]


def millis(match):
    return int(match.group(1)) * 1000 + int(match.group(2)[:3].ljust(3, "0"))


def in_window(matches, entry):
    return [m for m in matches if entry.get("start", 0) - 50 <= millis(m) <= entry.get("end", 0) + 50]


def install(device, apk):
    out = device.run("install", "-r", "-t", str(apk))
    text = (out.stdout + out.stderr).strip()
    if "Success" not in text and re.search(r"UPDATE_INCOMPATIBLE|VERSION_DOWNGRADE", text):
        package = re.search(r"Package (\S+)", text)
        if package:
            device.run("uninstall", package.group(1))
            out = device.run("install", "-r", "-t", str(apk))
            text = (out.stdout + out.stderr).strip()
    return report(f"install {Path(apk).name}", "Success" in text, text.splitlines()[-1][-160:] if text else "no output")


def run_script(device, config, samples, timeout):
    """Runs ocr_runs.js in the host app; returns its result or None."""
    files = sorted({run["file"] for run in config["runs"]})
    device.shell(f"rm -rf {WORK_DIR} && mkdir -p {WORK_DIR}")
    for name in files:
        device.run("push", str(samples / name), f"{WORK_DIR}/{name}")
    output = f"{HOST_FILES}/{config['output']}"
    device.shell(f"rm -f {output} {output}.part")
    with tempfile.TemporaryDirectory() as temp:
        script = Path(temp) / "paddle-ocr-v3.js"
        script.write_text(RUNS_SCRIPT.read_text("utf-8").replace("__CONFIG__", json.dumps(config)), encoding="utf-8")
        device.run("push", str(script), f"{WORK_DIR}/paddle-ocr-v3.js")
    device.shell(f"am start -n {HOST}/{RUN_ACTIVITY} -e path {WORK_DIR}/paddle-ocr-v3.js")
    deadline = time.monotonic() + timeout
    try:
        while time.monotonic() < deadline:
            time.sleep(3)
            text = device.run("shell", "cat", output).stdout
            if text.strip().startswith("{"):
                return json.loads(text)
        return None
    finally:
        device.shell(f"rm -rf {WORK_DIR} {output} {output}.part")


def runs_config(runs, output, directory, extra=None, repeat=1):
    return {"dir": directory, "output": output, "priority": False,
            "runs": [{"id": run_id, "file": f"{sample}.png", "options": {**options, **(extra or {})}, "repeat": repeat}
                     for run_id, sample, options in runs]}


def check_texts(name, entry, expected, min_similarity, info=False):
    if entry is None or "error" in entry or "texts" not in entry:
        return report(name, False, (entry or {}).get("error", "no result")[:300])
    scores = line_similarity(expected, entry["texts"])
    matched = sum(score >= min_similarity for score in scores)
    value = f"lines {matched}/{len(scores)} (min similarity {min(scores):.2f}), {entry['times'][0]} ms"
    return report(name, "INFO" if info else matched == len(scores), value)


def coexistence(args, device, samples, expected):
    ok = all([install(device, apk) for apk in args.install])
    versions = {package: device.package(package) for package in (ORT, LITE)}
    ok &= report("installed", all(versions.values()),
                 ", ".join(f"{p.rsplit('.', 1)[-1]} {v[0]} ({v[1]})" if v else f"{p} missing" for p, v in versions.items()))
    services = device.shell(f"cmd package query-services -a {ACTION}")
    answering = sorted(set(re.findall(r"^\s*packageName=(" + re.escape(PADDLE_PREFIX) + r"\S+)$", services, re.M)))
    ok &= report("discovery", ORT in answering and LITE in answering,
                 f"{len(answering)} Paddle OCR services, v3 {'yes' if ORT in answering else 'no'}, "
                 f"paddlelite {'yes' if LITE in answering else 'no'}")
    others = sorted(p for p in (line.removeprefix("package:").strip() for line in
                                device.shell(f"pm list packages {PADDLE_PREFIX}").splitlines())
                    if p.startswith(PADDLE_PREFIX) and p not in (ORT, LITE))
    config = {"dir": WORK_DIR, "output": RESULT, "priority": True, "open": PLUGIN_CENTER, "runs": [
        {"id": "variant v3-paddle-lite", "file": "zh.png", "options": {"variant": "v3-paddle-lite", "useSlim": False}, "repeat": 1},
        {"id": "variant v3", "file": "zh.png", "options": {"variant": "v3", "useSlim": False}, "repeat": 1},
        {"id": "default", "file": "zh.png", "options": {"useSlim": False}, "repeat": 1, "disable": others},
    ]}
    since = device.now()
    result = run_script(device, config, samples, args.timeout)
    if result is None:
        return report("host script", False, f"no result within {args.timeout} s") and False
    selects = list(SELECT.finditer(device.log(since, "OcrPluginHost:I")))
    wanted = {"variant v3-paddle-lite": LITE, "variant v3": ORT, "default": ORT}
    for entry in result["runs"]:
        served = in_window(selects, entry)
        package = served[0].group(3) if served else None
        ok &= check_texts(f"{entry['id']} text", entry, expected["zh"], args.min_similarity)
        value = f"served by {package or '?'} (variant {served[0].group(4) if served else '?'}; {served[0].group(5) if served else 'no log'})"
        if entry["id"] == "default":
            value += f", {len(entry.get('disabled', {}))} other Paddle OCR plugins switched off for the run"
        ok &= report(f"{entry['id']} routing", package == wanted[entry["id"]], value)
    order = result.get("storedOrder") or []
    report("stored priority", "INFO", f"paddle-ocr order {'empty' if not order else ', '.join(o.rsplit('.', 1)[-1] for o in order)}")
    if result.get("error"):
        ok &= report("host script", False, result["error"][:300])
    if args.screenshot:
        time.sleep(4)
        args.screenshot.parent.mkdir(parents=True, exist_ok=True)
        args.screenshot.write_bytes(subprocess.run([device.adb, "-s", device.serial, "exec-out", "screencap", "-p"],
                                                   capture_output=True).stdout)
        report("plugin center", "INFO", f"screenshot {args.screenshot.name}")
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / f"{device.serial}-coexistence.json").write_text(
        json.dumps({"versions": versions, "answering": answering, "result": result}, ensure_ascii=False, indent=2), "utf-8")
    return ok


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def installed_libraries(device, cache, package):
    """{(abi, name): sha256} of an installed package, caching the pulled APKs by device and version
    (devices of different ABIs install different APKs under the same version)."""
    version = device.package(package)
    if not version:
        return None, {}
    libraries = {}
    serial = re.sub(r"[^A-Za-z0-9._-]", "_", device.serial)
    paths = [line.removeprefix("package:").strip() for line in device.shell(f"pm path {package}").splitlines() if line.startswith("package:")]
    for index, remote in enumerate(paths):
        local = cache / f"{serial}-{package}-{version[1]}-{index}.apk"
        if not local.is_file():
            device.run("pull", remote, str(local))
        with zipfile.ZipFile(local) as apk:
            for info in apk.infolist():
                match = re.fullmatch(r"lib/([^/]+)/([^/]+\.so)", info.filename)
                if match:
                    libraries[(match.group(1), match.group(2))] = sha256(apk.read(info))
    return f"{package} {version[0]} ({version[1]})", libraries


def produce(device, args, group, name, source_files):
    """Runs the producer test; returns its report."""
    device.shell(f"rm -rf {PACKAGING_DIR}/source && mkdir -p {PACKAGING_DIR}/source")
    for local, remote in source_files:
        device.run("push", str(local), f"{PACKAGING_DIR}/source/{remote}")
    extras = [("produceApk", "true"), ("name", name), ("source", f"{PACKAGING_DIR}/source"),
              ("libs", group.get("libs", "Paddle OCR")), ("enable", ",".join(group["enable"])),
              ("disable", ",".join(group.get("disable", []))), ("abi", args.abi or "")]
    command = f"am instrument -w -r -e class {PRODUCER}" + "".join(
        f" -e autojs.paddleOcrPackaging.{key} {shlex.quote(value)}" for key, value in extras if value) + f" {RUNNER}"
    output = device.shell(command, timeout=1800)
    if "OK (1 test)" not in output:
        return {"success": False, "exception": "instrumentation: " + output[-600:]}
    return json.loads(device.shell(f"cat {PACKAGING_DIR}/{name}.json"))


def run_packaged(device, apk, timeout):
    """Installs and launches the packaged app; returns (result, since, pid)."""
    device.run("uninstall", SMOKE)
    out = device.run("install", "-r", "-t", str(apk))
    if "Success" not in out.stdout:
        return {"error": "install: " + (out.stdout + out.stderr).strip()[-200:]}, None, None
    since = device.now()
    component = device.shell(f"cmd package resolve-activity --brief -c android.intent.category.LAUNCHER {SMOKE}").splitlines()[-1]
    device.shell(f"am start -n {component}")
    path = f"/sdcard/Android/data/{SMOKE}/files/{RESULT}"
    pid = None
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        time.sleep(3)
        pid = device.shell(f"pidof {SMOKE}") or pid
        text = device.run("shell", "cat", path).stdout
        if text.strip().startswith("{"):
            return json.loads(text), since, pid
    return None, since, pid


def packaging(args, device, samples, expected):
    group = GROUPS[args.group]
    ok = all([install(device, apk) for apk in args.install])
    for package in args.uninstall:
        device.run("uninstall", package)
        report(f"uninstall {package.rsplit('.', 1)[-1]}", "INFO", "absent" if not device.package(package) else "still installed")
    abi = args.abi or device.shell("getprop ro.product.cpu.abi")
    versions = {package: device.package(package) for package in group["enable"] + group.get("disable", [])}
    report("plugins", "INFO", ", ".join(f"{p.rsplit('.', 1)[-1]} {v[0]} ({v[1]})" if v else f"{p.rsplit('.', 1)[-1]} missing"
                                        for p, v in versions.items()))
    if group.get("oldPlugin") and not (versions[ORT] and versions[ORT][0].startswith("1.")):
        return report("old plugin", False, f"{ORT} is not a 1.x build: {versions[ORT]}")
    name = f"paddle-ocr-{args.group}"
    out = args.out / f"{device.serial}-{args.group}"
    out.mkdir(parents=True, exist_ok=True)
    cache = args.out / "apk-cache"
    cache.mkdir(parents=True, exist_ok=True)
    receipt = {"serial": device.serial, "model": device.shell("getprop ro.product.model"),
               "api": device.shell("getprop ro.build.version.sdk"), "abi": abi, "group": args.group, "versions": versions}

    with tempfile.TemporaryDirectory() as temp:
        main = Path(temp) / "main.js"
        main.write_text(RUNS_SCRIPT.read_text("utf-8").replace(
            "__CONFIG__", json.dumps(runs_config(group["runs"], RESULT, None))), encoding="utf-8")
        sources = [(main, "main.js")] + [(samples / f"{sample}.png", f"{sample}.png") for sample in sorted({s for _, s, _ in group["runs"]} or {"zh"})]
        build = produce(device, args, group, name, sources)
    receipt["build"] = build
    selected = build.get("selected", [])
    paddle = next((s for s in selected if s.startswith("Paddle OCR:")), None)
    want = f"Paddle OCR: {group['enable'][0]} ({group['variant']})"
    if args.group == "both":
        want = f"Paddle OCR: {ORT} (v3)"
    errors = " | ".join(build.get("errors", []) + ([build["exception"]] if build.get("exception") else []))

    conflict_bytes = None
    if group.get("conflict"):
        digests = {}
        for package in group["enable"]:
            _, libraries = installed_libraries(device, cache, package)
            digests[package] = libraries.get((abi, group["conflict"]))
        conflict_bytes = len(set(digests.values())) == 1 and None not in digests.values()
        report(f"{group['conflict']} in the plugins", "INFO",
               "identical bytes" if conflict_bytes else "different bytes " + ", ".join(
                   f"{p.rsplit('.', 1)[-1]} {(d or 'missing')[:12]}" for p, d in digests.items()))
        receipt["conflictDigests"] = digests

    if group.get("error"):
        prefix = build.get(group["error"], "")
        hit = bool(prefix) and prefix.strip()[:60] in errors
        ok &= report("build error", not build.get("success") and hit,
                     f"status {build.get('status')}, {'OpenCV plugin required' if hit else 'unexpected: ' + errors[:200]}")
        device.shell(f"rm -rf {PACKAGING_DIR}")
        (out / "receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2), "utf-8")
        return ok
    if conflict_bytes is False:
        ok &= report("build", not build.get("success") and "onflict" in errors,
                     f"status {build.get('status')}, {'Conflict as expected' if 'onflict' in errors else errors[:200]}")
        device.shell(f"rm -rf {PACKAGING_DIR}")
        (out / "receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2), "utf-8")
        return ok
    ok &= report("build", bool(build.get("success")), f"status {build.get('status')}" + (f", {errors[:300]}" if errors else ""))
    ok &= report("selected plugin", paddle == want, f"{paddle or 'none'}; also {', '.join(s for s in selected if s != paddle) or 'nothing'}")
    if not build.get("success"):
        device.shell(f"rm -rf {PACKAGING_DIR}")
        (out / "receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2), "utf-8")
        return False
    apk = out / f"{name}.apk"
    device.run("pull", f"{PACKAGING_DIR}/{name}.apk", str(apk))
    device.shell(f"rm -rf {PACKAGING_DIR}")

    owners = {}
    receipt["sources"] = []
    for package in [TEMPLATE, OPENCV] + group["enable"]:
        label, libraries = installed_libraries(device, cache, package)
        if label:
            receipt["sources"].append(label)
            for (lib_abi, lib_name), digest in libraries.items():
                owners.setdefault((lib_name, digest), []).append(label)
    rows = []
    with zipfile.ZipFile(apk) as zipped:
        for info in sorted(zipped.infolist(), key=lambda item: item.filename):
            match = re.fullmatch(r"lib/([^/]+)/([^/]+\.so)", info.filename)
            if match:
                digest = sha256(zipped.read(info))
                rows.append({"entry": info.filename, "size": info.file_size, "sha256": digest,
                             "sameBytesAs": owners.get((match.group(2), digest), [])})
        assets = zipped.namelist()
    receipt["lib"] = rows
    names = {Path(row["entry"]).name for row in rows}
    flags = {key: any(key in n for n in names) for key in ("onnxruntime", "onnxruntime4j_jni", "opencv", "paddle_light", "libNative")}
    model_dirs = sorted({m.group(1) for m in (re.match(r"assets/(?:project/)?(models/[^/]+)/", a) for a in assets) if m})
    receipt["modelDirs"] = model_dirs
    report("lib/", "INFO", f"{len(rows)} entries ({', '.join(k for k, v in flags.items() if v) or 'no OCR libraries'}), "
                           f"{sum(1 for r in rows if r['sameBytesAs'])} with plugin bytes; models {', '.join(model_dirs) or 'none'}")
    if group.get("conflict"):
        packaged = next((r["sha256"] for r in rows if Path(r["entry"]).name == group["conflict"]), None)
        ok &= report(f"packaged {group['conflict']}", packaged is not None and packaged in receipt["conflictDigests"].values(),
                     "same bytes as both plugins" if packaged else "missing")

    result, since, pid = run_packaged(device, apk, args.timeout)
    receipt["packaged"] = result
    if not result or result.get("error"):
        ok &= report("packaged run", False, (result or {}).get("error", f"no result within {args.timeout} s")[:300])
    else:
        log = device.log(since, "OnnxPaddleOcrEngine:I", "ORTSessionManager:*") if since else ""
        loading, ready, fallback = list(LOADING.finditer(log)), list(READY.finditer(log)), list(NNAPI_FALLBACK.finditer(log))
        engine = group["engine"]
        maps = result.get("maps", [])
        ok &= report("packaged engine", any(engine in m for m in maps) and (engine != "paddle_light" or not loading),
                     f"loaded {', '.join(m for m in maps if 'onnxruntime' in m or 'paddle_light' in m) or 'no engine library'}, "
                     f"{len(loading)} ONNX Runtime engine loads logged")
        host = None
        if not group.get("oldPlugin"):
            host = run_script(device, runs_config(group["runs"], RESULT, WORK_DIR, {"variant": group["variant"]}), samples, args.timeout)
        receipt["host"] = host
        host_runs = {entry["id"]: entry for entry in (host or {}).get("runs", [])}
        for entry in result["runs"]:
            run_id = entry["id"]
            sample = next(s for i, s, _ in group["runs"] if i == run_id)
            ok &= check_texts(f"packaged {run_id}", entry, expected[sample], args.min_similarity, run_id in INFO_RUNS)
            details = []
            if engine == "onnxruntime":
                load = in_window(loading, entry)
                session = in_window(ready, entry)
                if load:
                    details.append(f"model {load[0].group(3)}, nnapi {load[0].group(4)}, cls {load[0].group(5)}")
                if session:
                    details.append(f"provider {session[0].group(3)} ({session[0].group(4)} ms)")
                if in_window(fallback, entry):
                    details.append("NNAPI fallback logged")
                options = next(o for i, _, o in group["runs"] if i == run_id)
                expect_root = "models/ppocrv3-slim" if options.get("useSlim") else "models/ppocrv3"
                expect_cls = "on" if options.get("useCls") else "off"
                passed = bool(load) and load[0].group(3) == expect_root and load[0].group(5) == expect_cls
                if options.get("useOpenCL"):
                    passed &= bool(session) and (session[0].group(3) == "nnapi" or bool(in_window(fallback, entry)))
                ok &= report(f"packaged {run_id} log", passed, "; ".join(details) or "no engine log")
            if host is not None:
                other = host_runs.get(run_id)
                same = bool(other) and other.get("texts") == entry.get("texts")
                ok &= report(f"host {run_id}", "INFO" if run_id in INFO_RUNS else same,
                             f"same text as packaged: {'yes' if same else 'no'}"
                             + (f", lines {sum(s >= args.min_similarity for s in line_similarity(expected[sample], other['texts']))}/{len(expected[sample])}"
                                if other and "texts" in other else f", {(other or {}).get('error', 'no result')[:200]}"))
        device.run("uninstall", SMOKE)
    (out / "receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2), "utf-8")
    return ok


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=("coexistence", "packaging"))
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--samples", type=Path, default=SAMPLES, help="the plugin's androidTest samples (samples.json)")
    parser.add_argument("--out", type=Path, default=ROOT / "build" / "tmp-paddle-ocr-v3", help="receipts and pulled APKs")
    parser.add_argument("--install", action="append", default=[], help="APK installed first (repeatable)")
    parser.add_argument("--uninstall", action="append", default=[], help="package uninstalled first (repeatable, packaging)")
    parser.add_argument("--group", choices=sorted(GROUPS), help="packaging group")
    parser.add_argument("--abi", help="packaged ABI, defaults to the primary device ABI")
    parser.add_argument("--screenshot", type=Path, help="coexistence: save a Plugin Center screenshot")
    parser.add_argument("--min-similarity", type=float, default=0.8)
    parser.add_argument("--timeout", type=int, default=300, help="seconds allowed for each script run")
    args = parser.parse_args()
    if args.command == "packaging" and not args.group:
        parser.error("packaging needs --group")
    device = Device(args.adb, args.serial)
    samples = {key: value["lines"] for key, value in json.loads((args.samples / "samples.json").read_text("utf-8")).items()}
    print(f"device {args.serial}: {device.shell('getprop ro.product.model')}, API {device.shell('getprop ro.build.version.sdk')}, "
          f"host {device.package(HOST)}", flush=True)
    ok = (coexistence if args.command == "coexistence" else packaging)(args, device, args.samples, samples)
    print("RESULT", "PASS" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
