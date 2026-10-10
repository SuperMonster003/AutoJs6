"""Build, inspect, install and execute the opt-in native user APK device fixture.

Install matching debug host/test APKs and the remote-enabled APK Builder, OpenCV,
Image Quantization, Paddle OCR and Rapid OCR plugins first. The output and its
receipt remain under --out; the fixture app remains installed for inspection.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time

import scan_alignment


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--out", type=Path, default=Path("build/tmp-16kb/user-apk"))
    parser.add_argument("--without-opencv-plugin", action="store_true", help="Package without the OpenCV plugin; OCR engines must not need its runtime")
    parser.add_argument("--rapid-version", choices=("3", "6"), default="3")
    parser.add_argument("--runtime-timeout", type=int, default=360, help="Seconds allowed for first model initialization and inference")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    package = "org.autojs.autojs6.nativepagesmoke"

    def adb(*command, check=True):
        result = subprocess.run([args.adb, "-s", args.serial, *command], text=True,
                                encoding="utf-8", errors="replace", capture_output=True)
        if check and result.returncode:
            raise RuntimeError(f"adb {command}: {result.stdout}\n{result.stderr}")
        return result.stdout.strip()

    page = int(adb("shell", "getconf", "PAGE_SIZE"))
    receipt = {"serial": args.serial, "api": adb("shell", "getprop", "ro.build.version.sdk"),
               "pageSize": page, "linkerCompat": adb("shell", "getprop", "bionic.linker.16kb.app_compat.enabled"),
               "pmCompatDisabled": adb("shell", "getprop", "pm.16kb.app_compat.disabled"), "ok": False}
    receipt["dedicatedOpenCvSelected"] = not args.without_opencv_plugin
    receipt["rapidVersion"] = args.rapid_version
    try:
        producer = adb("shell", "am", "instrument", "-w", "-r", "-e", "class",
                       "org.autojs.autojs.apkbuilder.template.NativePageAlignmentApkProducerDeviceTest",
                       "-e", "autojs.nativeAlignment.produceApk", "true",
                       "-e", "autojs.nativeAlignment.includeOpenCv", str(not args.without_opencv_plugin).lower(),
                       "-e", "autojs.nativeAlignment.rapidVersion", args.rapid_version,
                       "org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner")
        (args.out / "producer.log").write_text(producer, encoding="utf-8")
        assert "OK (1 test)" in producer and "INSTRUMENTATION_STATUS_CODE: -3" not in producer, producer
        apk = args.out / "native-page-alignment-smoke.apk"
        print(adb("pull", "/sdcard/Android/data/org.autojs.autojs6/files/native-page-alignment-smoke.apk", str(apk)), flush=True)
        rows = scan_alignment.scan_apk(apk)
        info = scan_alignment.aapt2_info(apk)
        assert rows and not scan_alignment.strict_failures(rows), rows
        assert all(row["compressed"] for row in rows), rows
        assert str(info["extractNativeLibs"]).lower() == "true", info
        with apk.open("rb") as stream:
            apk_sha = hashlib.file_digest(stream, "sha256").hexdigest()
        receipt.update(apkSha256=apk_sha,
                       nativeLibraries=rows, manifest=info)
        print(adb("install", "-r", str(apk)), flush=True)
        adb("shell", "am", "force-stop", package)
        adb("shell", "rm", "-f", f"/sdcard/Android/data/{package}/files/native-16kb-result.json")
        adb("logcat", "-c")
        component = adb("shell", "cmd", "package", "resolve-activity", "--brief", "-c", "android.intent.category.LAUNCHER", package).splitlines()[-1]
        assert component.startswith(package + "/"), component
        print(adb("shell", "am", "start", "-n", component), flush=True)
        deadline = time.monotonic() + args.runtime_timeout
        result = None
        while time.monotonic() < deadline:
            raw = adb("shell", "cat", f"/sdcard/Android/data/{package}/files/native-16kb-result.json", check=False)
            try:
                result = json.loads(raw)
                break
            except json.JSONDecodeError:
                time.sleep(2)
        (args.out / "runtime.log").write_text(adb("logcat", "-d", "-s", "Native16k:I", "GlobalConsole:D", "AndroidRuntime:E", "libc:F", "DEBUG:F", "*:S"), encoding="utf-8")
        receipt["runtime"] = result
        assert result and result["ok"] and result["pageSize"] == page, result
        receipt["ok"] = True
    finally:
        (args.out / "receipt.json").write_text(json.dumps(receipt, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(receipt["runtime"], ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
