"""Produce, inspect and run a packaged APK for the OpenCV / OCR runtime decoupling roadmap.

See docs/dev/opencv-ocr-runtime-decoupling-roadmap.md (P0.2, P0.3, P4.2).

Prerequisites on the device: the matching debug host and test APKs, a remote-enabled APK Builder
template plugin, and the OpenCV / OCR plugins named by --libs and --enable.

The script
1. runs OpenCvOcrPackagingDeviceTest, which builds the APK with only the --enable OCR packages enabled,
2. lists every lib/<abi>/*.so entry with its SHA-256, DT_NEEDED entries, exported / undefined cv:: symbols
   and the plugin or host APK that ships identical bytes,
3. with --run, installs the APK, launches it and collects the script result and logcat.

Example:
    python tools/opencv-ocr/verify_packaged_ocr.py 968e9f18 --name p02-v5 \
        --libs "Paddle OCR" --enable io.github.supermonster003.autojs6.plugin.paddleocr.v5 --run
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time
import zipfile

HOST_PACKAGE = "org.autojs.autojs6"
TEST_RUNNER = "org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS = "org.autojs.autojs.apkbuilder.template.OpenCvOcrPackagingDeviceTest"
SMOKE_PACKAGE = "org.autojs.autojs6.ocrpackagingsmoke"
OPENCV_PACKAGE = "io.github.supermonster003.autojs6.plugin.opencv"
DEVICE_OUTPUT_DIR = f"/sdcard/Android/data/{HOST_PACKAGE}/files/ocr-packaging"
RESULT_PATH = f"/sdcard/Android/data/{SMOKE_PACKAGE}/files/ocr-packaging-result.json"
CV_SYMBOL = re.compile(r"\b_ZN?K?2cv")


def default_readelf():
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "E:/.android/sdk")
    candidates = sorted((sdk / "ndk").glob("*/toolchains/llvm/prebuilt/*/bin/llvm-readelf*"))
    return str(candidates[-1]) if candidates else "llvm-readelf"


class Device:
    def __init__(self, adb, serial):
        self.adb = adb
        self.serial = serial

    def run(self, *command, check=True, timeout=None):
        result = subprocess.run([self.adb, "-s", self.serial, *command], text=True, encoding="utf-8",
                                errors="replace", capture_output=True, timeout=timeout)
        if check and result.returncode:
            raise RuntimeError(f"adb {command}: {result.stdout}\n{result.stderr}")
        return result.stdout.strip()

    def shell(self, command, check=True, timeout=None):
        return self.run("shell", command, check=check, timeout=timeout)


def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()


def elf_summary(readelf, path):
    dynamic = subprocess.run([readelf, "-d", "-W", str(path)], text=True, capture_output=True, errors="replace").stdout
    needed = re.findall(r"\(NEEDED\)\s+Shared library: \[([^\]]+)\]", dynamic)
    symbols = subprocess.run([readelf, "--dyn-syms", "-W", str(path)], text=True, capture_output=True, errors="replace").stdout
    exported_cv = undefined_cv = 0
    for line in symbols.splitlines():
        parts = line.split()
        if len(parts) < 8 or not CV_SYMBOL.search(parts[-1]):
            continue
        if parts[6] == "UND":
            undefined_cv += 1
        elif parts[4] in ("GLOBAL", "WEAK") and parts[5] == "DEFAULT":
            exported_cv += 1
    notes = subprocess.run([readelf, "-n", str(path)], text=True, capture_output=True, errors="replace").stdout
    ident = None
    match = re.search(r"NT_ANDROID_TYPE_IDENT\s+description data: ([0-9a-f ]+)", notes)
    if match:
        raw = bytes(int(value, 16) for value in match.group(1).split())
        ident = {"api": int.from_bytes(raw[0:4], "little"),
                 "ndk": raw[4:68].split(b"\0")[0].decode(errors="replace"),
                 "build": raw[68:132].split(b"\0")[0].decode(errors="replace")}
    return {"needed": needed, "exportedCvSymbols": exported_cv, "undefinedCvSymbols": undefined_cv, "androidIdent": ident}


def package_libraries(device, cache_dir, package):
    """Returns {(abi, name): sha256} for the installed package, caching pulled APKs by version."""
    dump = device.shell(f"dumpsys package {package}", check=False)
    version = re.search(r"versionCode=(\d+)", dump)
    if not version:
        return None, {}
    version_name = re.search(r"versionName=(\S+)", dump)
    label = f"{package} {version_name.group(1) if version_name else '?'} ({version.group(1)})"
    paths = [line.split(":", 1)[1] for line in device.shell(f"pm path {package}", check=False).splitlines() if line.startswith("package:")]
    libraries = {}
    for index, remote in enumerate(paths):
        local = cache_dir / f"{package}-{version.group(1)}-{index}.apk"
        if not local.is_file():
            device.run("pull", remote, str(local), timeout=600)
        with zipfile.ZipFile(local) as apk:
            for info in apk.infolist():
                match = re.fullmatch(r"lib/([^/]+)/([^/]+\.so)", info.filename)
                if match:
                    libraries[(match.group(1), match.group(2))] = sha256_bytes(apk.read(info))
    return label, libraries


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("serial")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--readelf", default=default_readelf())
    parser.add_argument("--out", type=Path, default=Path("build/tmp-opencv-ocr"))
    parser.add_argument("--name", required=True)
    parser.add_argument("--libs", default="", help="Comma-separated library labels, e.g. 'OpenCV,Paddle OCR,Rapid OCR'")
    parser.add_argument("--enable", default="", help="Comma-separated OCR plugin packages enabled during the build")
    parser.add_argument("--abi", default=None)
    parser.add_argument("--run", action="store_true", help="Install and run the packaged APK")
    parser.add_argument("--timeout", type=int, default=600, help="Seconds allowed for the packaged script")
    args = parser.parse_args()

    out = args.out / args.name
    out.mkdir(parents=True, exist_ok=True)
    cache = args.out / "apk-cache"
    cache.mkdir(parents=True, exist_ok=True)
    device = Device(args.adb, args.serial)
    receipt = {"serial": args.serial, "name": args.name,
               "api": device.shell("getprop ro.build.version.sdk"), "model": device.shell("getprop ro.product.model"),
               "pageSize": int(device.shell("getconf PAGE_SIZE")), "libs": args.libs, "enable": args.enable}
    try:
        extras = [("produceApk", "true"), ("name", args.name), ("libs", args.libs), ("enable", args.enable)]
        if args.abi:
            extras.append(("abi", args.abi))
        command = "am instrument -w -r -e class " + TEST_CLASS + "".join(
            f" -e autojs.ocrPackaging.{key} {shlex.quote(value)}" for key, value in extras if value) + " " + TEST_RUNNER
        producer = device.shell(command, timeout=1800)
        (out / "producer.log").write_text(producer, encoding="utf-8")
        if "OK (1 test)" not in producer:
            raise RuntimeError("Instrumentation failed:\n" + producer[-4000:])
        report = json.loads(device.shell(f"cat {DEVICE_OUTPUT_DIR}/{args.name}.json"))
        receipt["build"] = report
        if not report.get("success"):
            print(json.dumps(report, indent=2, ensure_ascii=False))
            return
        apk = out / f"{args.name}.apk"
        device.run("pull", f"{DEVICE_OUTPUT_DIR}/{args.name}.apk", str(apk), timeout=600)
        receipt["apkSha256"] = sha256_bytes(apk.read_bytes())

        owners = {}
        sources = [HOST_PACKAGE, OPENCV_PACKAGE] + [p for p in args.enable.split(",") if p]
        receipt["sources"] = []
        for package in sources:
            label, libraries = package_libraries(device, cache, package)
            if label:
                receipt["sources"].append(label)
                for key, digest in libraries.items():
                    owners.setdefault((key[1], digest), []).append(label)

        rows = []
        extracted = out / "lib"
        with zipfile.ZipFile(apk) as zipped:
            for info in sorted(zipped.infolist(), key=lambda item: item.filename):
                match = re.fullmatch(r"lib/([^/]+)/([^/]+\.so)", info.filename)
                if not match:
                    continue
                data = zipped.read(info)
                target = extracted / match.group(1) / match.group(2)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
                digest = sha256_bytes(data)
                rows.append({"entry": info.filename, "size": len(data), "sha256": digest,
                             "sameBytesAs": owners.get((match.group(2), digest), []),
                             **elf_summary(args.readelf, target)})
        receipt["nativeLibraries"] = rows
        with zipfile.ZipFile(apk) as zipped:
            receipt["markerAssets"] = sorted(name for name in zipped.namelist()
                                             if name.startswith("assets/") and "opencv" in name.lower())

        if args.run:
            device.run("uninstall", SMOKE_PACKAGE, check=False)
            print(device.run("install", "-r", str(apk), timeout=600), flush=True)
            device.shell(f"rm -f {RESULT_PATH}", check=False)
            device.run("logcat", "-c")
            component = device.shell(f"cmd package resolve-activity --brief -c android.intent.category.LAUNCHER {SMOKE_PACKAGE}").splitlines()[-1]
            device.shell(f"am start -n {component}")
            deadline = time.monotonic() + args.timeout
            result = None
            while time.monotonic() < deadline:
                raw = device.shell(f"cat {RESULT_PATH}", check=False)
                try:
                    result = json.loads(raw)
                    break
                except json.JSONDecodeError:
                    if device.shell(f"pidof {SMOKE_PACKAGE}", check=False) == "" and time.monotonic() > deadline - args.timeout + 30:
                        break
                    time.sleep(3)
            receipt["runtime"] = result
            (out / "runtime.log").write_text(device.run("logcat", "-d", "-s", "OcrPackaging:I", "GlobalConsole:D",
                                                        "OpenCvPluginHost:*", "AndroidRuntime:E", "libc:F", "DEBUG:F",
                                                        "linker:*", "*:S"), encoding="utf-8")
            device.shell(f"am force-stop {SMOKE_PACKAGE}", check=False)
    finally:
        (out / "receipt.json").write_text(json.dumps(receipt, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    summary = {key: receipt.get(key) for key in ("name", "pageSize")}
    summary["libraries"] = [f"{row['entry']} <- {', '.join(row['sameBytesAs']) or '?'}" for row in receipt.get("nativeLibraries", [])]
    runtime = receipt.get("runtime") or {}
    summary["steps"] = {name: (step.get("value") if step.get("ok") else "ERROR " + step.get("error", "")[:300])
                        for name, step in (runtime.get("steps") or {}).items() if name != "maps"}
    summary["maps"] = (runtime.get("steps") or {}).get("maps", {}).get("value")
    print(json.dumps(summary, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
