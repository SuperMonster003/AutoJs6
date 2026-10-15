"""ADB transport for the desktop collector. All local transport files remain private."""
from __future__ import annotations

import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
import uuid

from capture.collect import Adb, CaptureWorker, HOST, json_bytes
from capture.real_store import PACKAGE, inspect_snapshot
from dataset.task_io import private_path


def find_adb(explicit=None):
    candidates = [explicit] if explicit else [shutil.which("adb")]
    if not explicit:
        for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
            if os.environ.get(key):
                candidates.append(str(Path(os.environ[key]) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")))
        local = Path(__file__).resolve().parents[3] / "local.properties"
        if local.exists():
            match = re.search(r"^sdk\.dir=(.+)$", local.read_text(encoding="utf-8"), re.M)
            if match:
                sdk = match[1].strip().replace("\\:", ":").replace("\\\\", "\\")
                candidates.append(str(Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")))
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return str(Path(candidate).resolve())
    raise ValueError("找不到 adb。请安装 Android SDK Platform-Tools, 或用 --adb 指定 adb.exe 路径。")


def command(args, timeout=30):
    try:
        result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout,
                                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    except subprocess.TimeoutExpired as error:
        raise RuntimeError("设备响应超时。请检查 USB 连接, 再重试。") from error
    if result.returncode:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError("设备命令失败: " + detail)
    return result.stdout


def list_devices(executable):
    output = command([executable, "devices", "-l"]).decode("utf-8", errors="replace")
    devices = []
    for line in output.splitlines():
        fields = line.split()
        if len(fields) >= 2 and fields[1] in ("device", "offline", "unauthorized"):
            model = next((f[6:] for f in fields[2:] if f.startswith("model:")), "Android")
            devices.append({"serial": fields[0], "status": fields[1], "model": model})
    return devices


class PrivateAdb(Adb):
    def __init__(self, executable, serial, private_root):
        super().__init__(executable, serial)
        self.private_root = private_path(private_root)

    def run(self, *args, check=True, timeout=30):
        # Keep a hidden console for each ADB invocation when started from the graphical launcher.
        if check:
            return command(self.command + list(args), timeout)
        try:
            return subprocess.run(self.command + list(args), stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                  timeout=timeout, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0)).stdout
        except subprocess.TimeoutExpired as error:
            raise RuntimeError("设备响应超时。请检查 USB 连接。") from error

    def push_json(self, path, value):
        transport = private_path(self.private_root / "collector" / ".transport")
        transport.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=transport) as directory:
            local = private_path(Path(directory) / "request.json")
            local.write_bytes(json_bytes(value))
            self.run("push", str(local), path + ".tmp")
            self.shell("mv", path + ".tmp", path)

    def foreground_package(self):
        output = self.shell("dumpsys", "activity", "activities")
        for line in output.splitlines():
            if "topResumedActivity" in line or "mResumedActivity" in line:
                match = re.search(r"\bu\d+\s+([A-Za-z][A-Za-z0-9_.]*)/", line)
                if match and PACKAGE.fullmatch(match[1]):
                    return match[1]
        raise ValueError("未能识别前台应用。请先打开目标页面, 或手动填写包名。")

    def device_info(self, app):
        versions = {}
        for name, package in (("hostVersionCode", HOST), ("appVersionCode", app)):
            if not PACKAGE.fullmatch(package):
                raise ValueError("无效的应用包名。")
            match = re.search(r"\bversionCode=(\d+)", self.shell("dumpsys", "package", package))
            versions[name] = int(match[1]) if match else None
        if versions["hostVersionCode"] is None:
            raise ValueError("设备未安装 AutoJs6。请先安装支持保存布局的 AutoJs6。")
        return {"kind": "device", "serial": self.command[-1],
                "model": self.shell("getprop", "ro.product.model"),
                "api": self.shell("getprop", "ro.build.version.sdk"), **versions}


class RealCaptureWorker(CaptureWorker):
    def __init__(self, adb, app):
        if not PACKAGE.fullmatch(app):
            raise ValueError("无效的应用包名。")
        self.adb, self.app = adb, app
        self.run_id = uuid.uuid4().hex
        self.control = f"/sdcard/Android/data/{HOST}/files/codegen-real/{self.run_id}"
        self.bootstrap = f"/sdcard/Download/autojs6-codegen-bootstrap/{self.run_id}"
        self.sequence = 0
        self.started = False
        self.last_used = 0.0
        self.provenance = None

    def start(self):
        self.provenance = self.adb.device_info(self.app)
        self.adb.shell("mkdir", "-p", self.bootstrap)
        self.adb.push_json(self.bootstrap + "/config.json",
                           {"format": 1, "runId": self.run_id, "expectedPackage": self.app})
        self.adb.run("push", str(Path(__file__).with_name("real_capture.js")), self.bootstrap + "/capture.js")
        self.adb.run("push", str(Path(__file__).with_name("real_bootstrap.js")), self.bootstrap + "/bootstrap.js")
        result = self.adb.shell("am", "start", "-W", "-n", HOST + "/org.autojs.autojs.external.open.RunIntentActivity",
                                "-a", "android.intent.action.VIEW", "-d", "file://" + self.bootstrap + "/bootstrap.js")
        if "Error:" in result or "Exception" in result:
            raise RuntimeError("无法启动采集脚本: " + result)
        try:
            self.wait_json("worker-ready.json", {"runId": self.run_id}, timeout=20)
        except RuntimeError as error:
            raise RuntimeError("采集脚本未就绪。请检查手机上的 AutoJs6 提示、文件权限及脚本日志。") from error
        finally:
            for name in ("config.json", "config.json.tmp", "capture.js", "bootstrap.js"):
                self.adb.shell("rm", "-f", self.bootstrap + "/" + name)
            self.adb.shell("rmdir", self.bootstrap)
        self.started = True
        self.last_used = time.monotonic()

    def reusable(self):
        return self.started and time.monotonic() - self.last_used < 90

    def capture_layout(self):
        if not self.started:
            self.start()
        request_id = self.request("capture", "capture")
        response = self.wait_json("response.json", {"runId": self.run_id, "requestId": request_id,
                                                     "stateId": "capture"}, timeout=15)
        self.last_used = time.monotonic()
        try:
            if response.get("ok") is not True:
                raise RuntimeError("采集失败。请打开任务对应的应用, 关闭遮挡窗口, 并启用 AutoJs6 无障碍服务。\n"
                                   + str(response.get("error", "")))
            expected = "snapshot-" + request_id + ".layout.json"
            if response.get("packageName") != self.app or response.get("snapshot") != expected:
                raise ValueError("采集响应与本次请求不符, 未读取布局。")
            data = self.adb.run("exec-out", "cat", self.control + "/" + expected)
            inspect_snapshot(data, self.app)
            return data, self.provenance
        finally:
            release = self.request("release", "release")
            self.wait_json("response.json", {"runId": self.run_id, "requestId": release,
                                             "stateId": "release", "ok": True}, timeout=10)

    def stop(self):
        if not self.started:
            return
        stopped = self.adb.remote_json(self.control + "/worker-stopped.json")
        if not stopped or stopped.get("runId") != self.run_id:
            self.request("stop")
            self.wait_json("worker-stopped.json", {"runId": self.run_id}, timeout=8)
        self.started = False
        # Delete only our own known protocol files after the worker acknowledges termination.
        expected = f"/sdcard/Android/data/{HOST}/files/codegen-real/{self.run_id}"
        if not re.fullmatch(r"[a-f0-9]{32}", self.run_id) or self.control != expected:
            raise ValueError("采集临时目录校验失败。")
        for name in ("config.json", "config.json.tmp", "capture.js", "request.json", "request.json.tmp",
                     "response.json", "response.json.tmp", "worker-ready.json", "worker-stopped.json"):
            self.adb.shell("rm", "-f", self.control + "/" + name)
        self.adb.shell("rmdir", self.control)
