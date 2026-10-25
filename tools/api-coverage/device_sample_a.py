#!/usr/bin/env python3
"""Device sampling A of the api-coverage review (ROADMAP P1.2).

Runs subsets of the host's existing runtime device suites (Codex batches,
`app/src/androidTest/.../engine/Runtime*DeviceTest.kt` and the image / capture
suites) on one device and prints one line per case: pass / fail, time and the
key numbers of its evidence file. Tokens, recognized text and full tool lists
are never printed.

Phases (all take --serial and --state, a private directory such as a scratchpad):
  record   package versions and APK md5 of the device before sampling (state.json)
  backup   pull the original APKs into <state>/apks
  install  install the candidate APKs given by --apk package=path (md5 verified)
  run      run one family (or --case ids) of the sampling list; results.json
  restore  reinstall the original APKs that changed and compare md5 with state.json
  verify   compare the device with state.json only
  list     print the sampling list

System consent dialogs (MediaProjection, runtime permissions, Shizuku, SAF) are
not part of the sampling list unless the dialog has been answered by the
maintainer beforehand; cases are annotated with what they need (`needs`).
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import time

HOST = "org.autojs.autojs6"
HOST_TEST = "org.autojs.autojs6.test"
NODE = "io.github.supermonster003.autojs6.plugin.nodejs"
BUN = "io.github.supermonster003.autojs6.plugin.bun.runtime"
RUNNER = f"{HOST_TEST}/androidx.test.runner.AndroidJUnitRunner"
ENGINE = "org.autojs.autojs.engine."
EVIDENCE_DIR = f"/sdcard/Android/data/{HOST}/files"

# Packages whose state is recorded; the first four are the ones sampling may replace.
PACKAGES = [HOST, HOST_TEST, NODE, BUN,
            "io.github.supermonster003.autojs6.plugin.opencv",
            "io.github.supermonster003.autojs6.plugin.imagequant",
            "io.github.supermonster003.autojs6.plugin.opencc",
            "io.github.supermonster003.autojs6.plugin.pinyin",
            "io.github.supermonster003.autojs6.plugin.pinyin4j",
            "moe.shizuku.privileged.api"]

# Device settings the suites touch (rotation is restored by the fixtures themselves).
SETTINGS = [("system", "user_rotation"), ("system", "accelerometer_rotation"),
            ("system", "screen_off_timeout"), ("global", "stay_on_while_plugged_in"),
            ("system", "screen_brightness"), ("system", "screen_brightness_mode"),
            ("secure", "enabled_accessibility_services"), ("secure", "enabled_notification_listeners")]
APPOPS = [(HOST, "PROJECT_MEDIA"), (HOST, "SYSTEM_ALERT_WINDOW"), (HOST, "POST_NOTIFICATION")]
PERMISSIONS = [(HOST, "android.permission.RECORD_AUDIO"), (HOST, "android.permission.CAMERA"),
               (HOST, "android.permission.POST_NOTIFICATIONS")]


def case(family, cid, cls, method, apis, modes, evidence=None, needs=None, args=None):
    return {"family": family, "id": cid, "class": ENGINE + cls, "method": method,
            "apis": apis, "modes": modes, "evidence": evidence, "needs": needs or [], "args": args or {}}


ALL = ["main", "worker", "bun"]
# The sampling list. `apis` are the API rows (matrix ids, or runtime handle members
# outside the matrix denominator) the case exercises, `modes` the runtime columns
# (main = Node main thread CJS / ESM, worker = Node CJS / ESM / nested Worker,
# bun = Bun host client). See REVIEW-codex-2026-10.md "P1.2".
CASES = [
    # Images (Codex runtime-image-parity / runtime-opencc-images / runtime-capture-worker).
    case("images", "img-filters", "AdvancedImageBridgeDeviceTest", "filtersColorsMetricsAndEncodingAcrossAllExecutionModes",
         ["images.cvtColor", "images.threshold", "images.adaptiveThreshold", "images.bilateralFilter", "images.inRange",
          "images.getMeanColor", "images.isGrayscale", "images.compress", "images.compressToBytes", "images.downsample",
          "images.pixel", "images.getSize"], ALL, "advanced-images-filters.json"),
    case("images", "img-features", "AdvancedImageBridgeDeviceTest", "featuresMatricesFilesAndUrlsAcrossAllExecutionModes",
         ["images.detectAndComputeFeatures", "images.matchFeatures", "images.matToImage", "images.load", "images.loadAsync",
          "images.imread", "images.ObjectFrame"], ALL, "advanced-images-features.json"),
    case("images", "img-circles", "AdvancedImageBridgeDeviceTest", "circleDetectionAcrossAllExecutionModes",
         ["images.findCircles"], ALL, "advanced-images-circles.json"),
    case("images", "img-quota", "AdvancedImageBridgeDeviceTest", "imageMatAndFeatureHandlesShareTheExistingQuota",
         ["images.readImage", "images.imread", "images.recycle", "images.isRecycled"], ["main", "bun"]),
    case("images", "img-worker-features", "AdvancedImageBridgeDeviceTest", "featureHandlesCrossWorkersButNeverCrossExecutions",
         ["images.detectAndComputeFeatures", "images.matchFeatures"], ["main", "worker"]),
    case("images", "img-bun-grants", "AdvancedImageBridgeDeviceTest", "bunFileAndProcessingGrantsCannotBeBypassedByRawImageCalls",
         ["images.load", "images.quantizeToFile"], ["bun"]),
    case("images", "img-public", "OpenccImageBridgeDeviceTest", "publicImagesPreservePixelsDimensionsAndOwnership",
         ["images.readImage", "images.saveImage", "images.clip", "images.rotate", "images.scale", "images.flip",
          "images.findColor", "images.findMultiColors", "images.findAllPointsForColor", "images.readPixels",
          "images.toBytes", "images.fromBytes"], ["main", "bun"], "opencc-images-images.json"),
    case("images", "img-transport", "OpenccImageBridgeDeviceTest", "binaryTransportJsonBoundsAndExecutionOwnership",
         ["images.toBase64", "images.fromBase64", "images.resize", "images.readPixels"], ["main", "bun"],
         "opencc-images-transport-grants.json"),
    case("images", "img-worker-bridge", "OpenccImageBridgeDeviceTest", "nodeWorkerCanUseOpenccAndImagesThroughItsDocumentedBridge",
         ["images.readImage", "images.pixel", "opencc.convert"], ["worker"], "opencc-images-worker.json"),
    case("images", "img-capture-denied", "NodeScreenCaptureInstrumentationTest", "captureWithoutConsentReturnsPermissionDeniedAndImageSaveWorks",
         ["images.captureScreen", "media_projection.requestScreenCapture", "images.saveImage"], ["main"]),
    case("images", "img-capture-manual", "NodeScreenCaptureInstrumentationTest", "approvedCaptureSavesScreenSizedPngAndStops",
         ["images.requestScreenCapture", "images.captureScreen", "images.clip", "images.findColor", "images.findImage",
          "images.stopScreenCapture"], ["main"], needs=["maintainer approves the MediaProjection dialog"],
         args={"captureConsent": "manual", "imageAnalysis": "true"}),
    # These Codex cases click the MediaProjection "Start now" button themselves; run them only with the
    # maintainer's explicit permission for that run (given for P1.2 on 2026-10-25).
    case("images", "img-capture-async", "AdvancedImageBridgeDeviceTest", "asyncCaptureAliasAndOptionsUseTheRealCaptureSession",
         ["images.requestScreenCaptureAsync", "images.getScreenCaptureOptions", "images.captureScreen", "images.getSize",
          "images.stopScreenCapture"], ["main", "bun"], needs=["maintainer permits the automatic MediaProjection click"]),
    case("images", "img-capture-workers", "CaptureWorkerBridgeDeviceTest", "workerCjsEsmAndNestedFacadesUseRealProvidersAndBinaryTransport",
         ["images.requestScreenCapture", "images.captureScreen", "images.pixel", "images.recycle", "opencc.convert"],
         ["worker"], "capture-worker-worker-modules.json", needs=["maintainer permits the automatic MediaProjection click"]),
    case("images", "img-capture-bun-rotation", "CaptureWorkerBridgeDeviceTest", "bunConsentOutlivesOneBridgeCallCapturesBothRotationsAndRequestsAgain",
         ["images.requestScreenCapture", "images.captureScreen", "images.stopScreenCapture"], ["bun"],
         "capture-worker-consent-rotation.json", needs=["maintainer permits the automatic MediaProjection click"]),
    # UI / Compose and View interop (Codex runtime-ui-compose / runtime-ui-interop / runtime-xml-views / runtime-view-components).
    case("ui", "ui-layouts", "RuntimeUiComposeDeviceTest", "layoutsBindingsAndClicksAcrossRuntimeModes",
         ["ui.showLayout", "ui.bind", "ui.state", "Window#on", "Window#close"], ALL, "runtime-ui-compose-layouts.json"),
    case("ui", "ui-compose", "RuntimeUiComposeDeviceTest", "composeClicksTextInputReactiveFramesAndRefsAcrossRuntimeModes",
         ["compose.isAvailable", "compose.version", "compose.mount", "compose.state", "compose.ref", "compose.batch",
          "compose.Button", "compose.Column", "compose.Text", "compose.TextField", "Window#flush"], ALL, "runtime-ui-compose-compose.json"),
    case("ui", "ui-ownership", "RuntimeUiComposeDeviceTest", "dialogsValidationAndOwnershipRemainExecutionScoped",
         ["compose.dialog", "compose.mount", "ui.showLayout"], ALL, "runtime-ui-compose-ownership.json"),
    case("ui", "ui-worker-cleanup", "RuntimeUiComposeDeviceTest", "terminatingWorkerClosesItsWindowWithoutEndingMain",
         ["compose.mount", "Window#close"], ["main", "worker"], "runtime-ui-compose-worker-cleanup.json"),
    case("ui", "ui-floaty", "RuntimeUiComposeDeviceTest", "floatingComposeWindowsRenderAndCloseAcrossRuntimeModes",
         ["compose.floaty", "Window#setPosition", "Window#setSize", "Window#setTouchable", "Window#disableFocus",
          "Window#getX", "Window#getWidth", "Window#close"], ALL, "runtime-ui-compose-floaty.json"),
    case("ui", "ui-snackbar", "RuntimeUiComposeDeviceTest", "snackbarActionsReachTheCreatingRuntime",
         ["compose.Scaffold", "Window#showSnackbar"], ALL, "runtime-ui-compose-snackbar.json"),
    case("ui", "ui-xml-attach", "RuntimeUiInteropDeviceTest", "xmlAttachAndNativeClickAcrossRuntimeModes",
         ["ui.inflate", "ui.createView", "compose.attach", "compose.AndroidView", "View#findView", "View#dispose"], ALL),
    case("ui", "ui-factories", "RuntimeUiInteropDeviceTest", "factoriesReplacementAndOwnershipAcrossRuntimeModes",
         ["ui.view", "ui.context", "ui.findView", "compose.attach", "compose.AndroidView", "View#dispose"], ALL),
    case("ui", "ui-xml-widgets", "RuntimeXmlViewDeviceTest", "commonXmlWidgetsAndOrderedPropertiesAcrossRuntimes",
         ["ui.inflate", "ui.context", "View#findView", "View#dispose"], ALL, "runtime-xml-view-properties.json"),
    case("ui", "ui-xml-events", "RuntimeXmlViewDeviceTest", "nativeSelectionAndProgressEventsReachTheCreatingRuntime",
         ["ui.showLayout", "Window#findView", "View#on (selection / progress)"], ALL, "runtime-xml-view-events.json"),
    case("ui", "ui-components", "RuntimeViewComponentsDeviceTest", "drawerToolbarPagerClicksAndRotationAcrossRuntimes",
         ["ui.inflate (drawer / toolbar / viewpager)", "View#findView", "WebView#loadHtml"], ALL, "runtime-view-components-interaction-rotation.json"),
    # WebView: the files / downloads batch (Codex runtime-webview-files) and the view-component WebView pages.
    case("web", "web-downloads", "RuntimeWebFilesDeviceTest", "dataAndBlobBytesCancellationAndNavigationAcrossRuntimes",
         ["WebView#loadHtml", "WebView#evaluateJavascript", "WebView#download", "WebView#getDownload",
          "WebView#readDownload", "WebView#cancelDownload", "WebView#dispose"], ALL, "runtime-web-files-downloads.json"),
    case("web", "web-links", "RuntimeWebFilesDeviceTest", "blobAndDataLinksReachTheDownloadListener",
         ["WebView#setDownloadListener", "WebView#download", "WebView#readDownload"], ALL, "runtime-web-files-links.json"),
    case("web", "web-picker-modes", "RuntimeWebFilesDeviceTest", "pickerModesAndDataDecodingRejectInvalidInputs",
         ["(host) RuntimeWebFileChooser.mode", "(host) RuntimeWebLocalDownloads.decodeData"], []),
    case("web", "web-pages", "RuntimeViewComponentsDeviceTest", "webPagesJavaScriptAndHistoryAcrossRuntimes",
         ["WebView#loadUrl", "WebView#loadHtml", "WebView#goBack", "WebView#goForward", "WebView#reload",
          "WebView#stopLoading", "WebView#evaluateJavascript", "WebView#on"], ALL,
         "runtime-view-components-web.json"),
    case("web", "web-disposal", "RuntimeViewComponentsDeviceTest", "webViewDisposalReleasesAttachedAndOffscreenPages",
         ["WebView#dispose"], ALL),
    # Shell + Shizuku (Codex runtime-shell / shell-api-coverage).
    case("shell", "shell-denials", "RuntimeShellDeviceTest", "bunGrantAndNodePrivilegeDenialsDoNotStartProcesses",
         ["shell.exec", "shell.execRoot", "shizuku.exec"], ["main", "bun"]),
    case("shell", "shell-main", "RuntimeShellDeviceTest", "mainNodeAndBunStreamOrdinaryAndShizukuProcesses",
         ["shell.getAccess", "shell.exec", "shell.execFile", "shell.spawn", "shell.open", "shizuku.requestPermission",
          "ShellProcess#write", "ShellProcess#writeLine", "ShellProcess#readLine", "ShellProcess#closeInput",
          "ShellProcess#waitFor", "ShellProcess#close"], ["main", "bun"], "runtime-shell-host.json",
         needs=["Shizuku running (adb, uid 2000) and AutoJs6 authorized in Shizuku"]),
    case("shell", "shell-workers", "RuntimeShellDeviceTest", "workersCjsEsmAndNestedReleaseShellsWithoutTouchingParents",
         ["shell.exec", "shell.spawn", "shell.open", "shizuku.requestPermission", "ShellProcess#close"], ["main", "worker"],
         "runtime-shell-worker.json", needs=["Shizuku running (adb, uid 2000) and AutoJs6 authorized in Shizuku"]),
    # Events / notifications and device / dialogs (Codex runtime-events / runtime-device-dialogs).
    case("events", "ev-denials", "RuntimeEventsDeviceTest", "missingBunGrantsAndNodeSubGrantsRejectWithoutChangingAndroidSettings",
         ["events.observeBroadcasts", "events.observeNotification", "events.observeToast", "events.observeKey",
          "notifications.notify", "notifications.openSettings"], ["main", "bun"]),
    case("events", "ev-main", "RuntimeEventsDeviceTest", "mainNodeAndBunObserveRealSourcesAndPostSilentNotifications",
         ["events.observeNotification", "events.observeToast", "events.observeKey", "events.observeBroadcasts",
          "events.on", "events.once", "events.close", "notifications.notify", "notifications.cancel", "notifications.cancelAll",
          "notifications.getPermissionStatus", "notifications.openNotificationSettings"], ["main", "bun"],
         "runtime-events-host.json", needs=["AutoJs6 notification access and accessibility enabled"]),
    case("events", "ev-workers", "RuntimeEventsDeviceTest", "cjsEsmAndNestedWorkersReleaseOwnedObserversAndNotifications",
         ["events.observeBroadcasts", "notifications.notify", "notifications.cancelAll"], ["main", "worker"],
         "runtime-events-worker.json", needs=["AutoJs6 notification access and accessibility enabled"]),
    case("events", "dd-node", "RuntimeDeviceDialogsDeviceTest", "nodeMainDeviceAndDialogs",
         ["device.getInfo", "device.keepScreenOn", "device.cancelKeepingAwake", "device.vibrate", "device.wakeUp",
          "dialogs.alert", "dialogs.confirm", "dialogs.input", "dialogs.rawInput", "dialogs.prompt", "dialogs.select",
          "dialogs.singleChoice", "dialogs.multiChoice"], ["main"], "runtime-device-dialogs-node.json"),
    case("events", "dd-worker", "RuntimeDeviceDialogsDeviceTest", "nodeWorkerDeviceAndDialogs",
         ["device.getInfo", "dialogs.alert", "dialogs.input", "dialogs.multiChoice"], ["worker"],
         "runtime-device-dialogs-worker.json"),
    case("events", "dd-bun", "RuntimeDeviceDialogsDeviceTest", "bunDeviceAndDialogs",
         ["device.getInfo", "dialogs.alert", "dialogs.input", "dialogs.multiChoice"], ["bun"], "runtime-device-dialogs-bun.json"),
    case("events", "dd-bun-denied", "RuntimeDeviceDialogsDeviceTest", "bunMissingGrantsRejectDevicePowerAudioAndDialogs",
         ["device.getWidth", "device.keepScreenOn", "device.getMusicVolume", "dialogs.confirm"], ["bun"]),
    case("events", "dd-worker-exit", "RuntimeDeviceDialogsDeviceTest", "esmAndNestedWorkerExitReleaseOwnedWindowsAndLocks",
         ["device.keepScreenOn", "dialogs.progress"], ["main", "worker"]),
]


def adb(serial, *args, timeout=180, check=True):
    result = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True,
                            encoding="utf-8", errors="replace", timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(f"adb {' '.join(args)}: {result.stdout}{result.stderr}".strip())
    return result.stdout + result.stderr


def sh(serial, command, timeout=180):
    return adb(serial, "shell", command, timeout=timeout).strip()


def package_state(serial, package):
    paths = [line[8:].strip() for line in sh(serial, f"pm path {package}").splitlines() if line.startswith("package:")]
    if not paths:
        return None
    dump = sh(serial, f"dumpsys package {package}")
    version_code = re.search(r"versionCode=(\d+)", dump)
    version_name = re.search(r"versionName=(\S+)", dump)
    flags = re.search(r"pkgFlags=\[([^\]]*)\]", dump)
    splits = {}
    for path in paths:
        splits[path.rsplit("/", 1)[1]] = {"path": path, "md5": sh(serial, f"md5sum {path}").split()[0]}
    return {"versionCode": int(version_code.group(1)) if version_code else None,
            "versionName": version_name.group(1) if version_name else None,
            "debuggable": bool(flags and "DEBUGGABLE" in flags.group(1)),
            "splits": splits}


def device_state(serial):
    state = {"serial": serial,
             "model": sh(serial, "getprop ro.product.model"),
             "release": sh(serial, "getprop ro.build.version.release"),
             "sdk": sh(serial, "getprop ro.build.version.sdk"),
             "pageSize": sh(serial, "getconf PAGE_SIZE"),
             "packages": {p: package_state(serial, p) for p in PACKAGES},
             "settings": {f"{ns}/{key}": sh(serial, f"settings get {ns} {key}") for ns, key in SETTINGS},
             "appops": {f"{p}/{op}": sh(serial, f"appops get {p} {op}") for p, op in APPOPS},
             "permissions": {}}
    for package, permission in PERMISSIONS:
        dump = sh(serial, f"dumpsys package {package}")
        match = re.search(re.escape(permission) + r": granted=(\w+)(?:, flags=\[([^\]]*)\])?", dump)
        state["permissions"][f"{package}/{permission}"] = match.group(0).split(": ", 1)[1] if match else None
    return state


def load(state_dir):
    return json.loads((state_dir / "state.json").read_text(encoding="utf-8"))


def md5(path):
    return hashlib.md5(Path(path).read_bytes()).hexdigest()


def record(args):
    args.state.mkdir(parents=True, exist_ok=True)
    target = args.state / "state.json"
    if target.exists() and not args.force:
        sys.exit(f"{target} exists; pass --force to record again")
    state = device_state(args.serial)
    target.write_text(json.dumps(state, indent=2, ensure_ascii=False), encoding="utf-8")
    for package, info in state["packages"].items():
        if info:
            print(f"{package}: versionCode {info['versionCode']} ({info['versionName']}), splits {len(info['splits'])}, "
                  f"md5 {','.join(s['md5'] for s in info['splits'].values())}{' debuggable' if info['debuggable'] else ''}")
        else:
            print(f"{package}: not installed")
    for group in ("settings", "appops", "permissions"):
        for key, value in state[group].items():
            print(f"{group} {key}: {value}")


def backup(args):
    state = load(args.state)
    for package in PACKAGES[:4] + list(args.also or []):
        info = state["packages"].get(package)
        if not info:
            continue
        folder = args.state / "apks" / package
        folder.mkdir(parents=True, exist_ok=True)
        for name, split in info["splits"].items():
            local = folder / name
            if not local.exists() or md5(local) != split["md5"]:
                adb(args.serial, "pull", split["path"], str(local), timeout=600)
            ok = md5(local) == split["md5"]
            print(f"backup {package}/{name}: {'ok' if ok else 'MD5 MISMATCH'}")
            if not ok:
                sys.exit(1)


def install_files(serial, package, files):
    files = [str(f) for f in files]
    if len(files) == 1:
        out = adb(serial, "install", "-r", "-d", "-t", files[0], timeout=600, check=False)
    else:
        out = adb(serial, "install-multiple", "-r", "-d", "-t", *files, timeout=600, check=False)
    if "Success" not in out:
        raise RuntimeError(f"install {package}: {out.strip()[-600:]}")


def install(args):
    log = args.state / "installed.json"
    installed = json.loads(log.read_text(encoding="utf-8")) if log.exists() else {}
    for item in args.apk:
        package, path = item.split("=", 1)
        path = Path(path)
        install_files(args.serial, package, [path])
        info = package_state(args.serial, package)
        remote = next(iter(info["splits"].values()))["md5"]
        ok = remote == md5(path)
        installed[package] = {"source": str(path), "md5": remote, "versionCode": info["versionCode"],
                              "versionName": info["versionName"]}
        print(f"install {package}: versionCode {info['versionCode']} ({info['versionName']}) md5 {remote} {'ok' if ok else 'MD5 MISMATCH'}")
        if not ok:
            sys.exit(1)
    log.write_text(json.dumps(installed, indent=2), encoding="utf-8")


def compare(serial, state, packages):
    differences = []
    for package in packages:
        before = state["packages"].get(package)
        now = package_state(serial, package)
        same = (before is None and now is None) or (
            before is not None and now is not None and before["versionCode"] == now["versionCode"]
            and sorted(s["md5"] for s in before["splits"].values()) == sorted(s["md5"] for s in now["splits"].values()))
        if not same:
            differences.append(package)
        print(f"verify {package}: {'same' if same else 'CHANGED'}"
              + ("" if same or now is None else f" (now versionCode {now['versionCode']})"))
    for group, current in (("settings", {f"{ns}/{key}": sh(serial, f"settings get {ns} {key}") for ns, key in SETTINGS}),
                           ("appops", {f"{p}/{op}": sh(serial, f"appops get {p} {op}") for p, op in APPOPS})):
        for key, value in current.items():
            same = state[group].get(key) == value
            if not same:
                differences.append(key)
            print(f"verify {group} {key}: {'same' if same else 'CHANGED: ' + value}")
    return differences


def restore(args):
    state = load(args.state)
    for package in PACKAGES:
        if not (args.state / "apks" / package).is_dir() and package not in PACKAGES[:4]:
            continue
        before = state["packages"].get(package)
        now = package_state(args.serial, package)
        if before is None:
            if now is not None:
                print(f"restore {package}: installed during sampling, uninstalling")
                adb(args.serial, "uninstall", package)
            continue
        if now and now["versionCode"] == before["versionCode"] and sorted(s["md5"] for s in now["splits"].values()) == sorted(s["md5"] for s in before["splits"].values()):
            print(f"restore {package}: unchanged")
            continue
        files = [args.state / "apks" / package / name for name in before["splits"]]
        install_files(args.serial, package, files)
        print(f"restore {package}: reinstalled versionCode {before['versionCode']}")
    differences = compare(args.serial, state, PACKAGES)
    print("restore: device matches the recorded state" if not differences else f"restore: differences {differences}")
    sys.exit(1 if differences else 0)


def verify(args):
    sys.exit(1 if compare(args.serial, load(args.state), PACKAGES) else 0)


def parse_instrumentation(output):
    tests, current = {}, None
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: test="):
            current = line.split("=", 1)[1].strip()
        elif line.startswith("INSTRUMENTATION_STATUS_CODE:") and current:
            code = int(line.split(":", 1)[1])
            if code in (0, -2, -3, -4):
                tests[current] = {0: "pass", -2: "fail", -3: "ignored", -4: "assumption"}[code]
    return tests


def failure_message(output):
    match = re.search(r"INSTRUMENTATION_STATUS: stack=(.*?)(?:\nINSTRUMENTATION_STATUS: |\Z)", output, re.S)
    if not match:
        match = re.search(r"INSTRUMENTATION_RESULT: shortMsg=(.*)", output)
    text = match.group(1) if match else output[-800:]
    first = [line.strip() for line in text.strip().splitlines() if line.strip()][:3]
    return " | ".join(first)[:400]


def fetch_evidence(serial, name, folder):
    local = folder / name
    out = adb(serial, "pull", f"{EVIDENCE_DIR}/{name}", str(local), check=False)
    if "error" in out.lower() or not local.exists():
        return None
    try:
        return json.loads(local.read_text(encoding="utf-8"))
    except ValueError:
        return None


def summarize(value, depth=0, prefix=""):
    """Flatten numbers and booleans of an evidence object into short key=value pairs."""
    pairs = []
    if isinstance(value, dict):
        for key, item in value.items():
            pairs += summarize(item, depth + 1, f"{prefix}{key}." if depth < 6 else prefix)
    elif isinstance(value, list):
        for index, item in enumerate(value[:12]):
            pairs += summarize(item, depth + 1, f"{prefix}{index}.")
    elif isinstance(value, bool) or isinstance(value, (int, float)):
        pairs.append(f"{prefix.rstrip('.')}={value}")
    return pairs


def run(args):
    state = load(args.state)
    selected = [c for c in CASES if (args.family and c["family"] == args.family) or (args.case and c["id"] in args.case)]
    if not selected:
        sys.exit("no cases selected")
    out_dir = args.state / "runs" / (args.family or "cases")
    out_dir.mkdir(parents=True, exist_ok=True)
    results_path = args.state / "results.json"
    results = json.loads(results_path.read_text(encoding="utf-8")) if results_path.exists() else {}
    for item in selected:
        if item["evidence"]:
            sh(args.serial, f"rm -f {EVIDENCE_DIR}/{item['evidence']}")
        extra = [part for key, value in item["args"].items() for part in ("-e", key, value)]
        if item["needs"]:
            print(f"{item['id']}: needs {'; '.join(item['needs'])}", flush=True)
        started = time.monotonic()
        output = adb(args.serial, "shell", "am", "instrument", "-w", "-r", *extra, "-e", "class",
                     f"{item['class']}#{item['method']}", RUNNER, timeout=args.timeout, check=False)
        elapsed = round(time.monotonic() - started, 1)
        (out_dir / f"{item['id']}.log").write_text(output, encoding="utf-8")
        tests = parse_instrumentation(output)
        status = tests.get(item["method"], "fail" if "FAILURES!!!" in output or "INSTRUMENTATION_FAILED" in output or not tests else "unknown")
        if status == "pass" and ("FAILURES!!!" in output or "OK (" not in output):
            status = "fail"
        entry = {"status": status, "seconds": elapsed, "apis": item["apis"], "modes": item["modes"],
                 "class": item["class"], "method": item["method"], "at": time.strftime("%Y-%m-%d %H:%M:%S")}
        if status != "pass":
            entry["message"] = failure_message(output)
        if item["evidence"]:
            evidence = fetch_evidence(args.serial, item["evidence"], out_dir)
            if evidence is not None:
                entry["numbers"] = summarize(evidence)[:40]
            sh(args.serial, f"rm -f {EVIDENCE_DIR}/{item['evidence']}")
        results[item["id"]] = entry
        results_path.write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
        line = f"{item['id']} {status} {elapsed}s [{'/'.join(item['modes'])}]"
        if entry.get("message"):
            line += f" :: {entry['message']}"
        print(line, flush=True)
        if entry.get("numbers") and args.numbers:
            print("    " + ", ".join(entry["numbers"][:args.numbers]), flush=True)


def list_cases(args):
    for item in CASES:
        if args.family and item["family"] != args.family:
            continue
        needs = f" needs: {', '.join(item['needs'])}" if item["needs"] else ""
        print(f"{item['family']:8} {item['id']:24} {item['class'].rsplit('.', 1)[1]}#{item['method']} [{'/'.join(item['modes'])}] {len(item['apis'])} apis{needs}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("phase", choices=["record", "backup", "install", "run", "restore", "verify", "list"])
    parser.add_argument("--serial", default="bek749scrwv4wo8h")
    parser.add_argument("--state", type=Path, help="private state directory (APK copies, logs, results)")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--also", nargs="*", help="backup: extra packages")
    parser.add_argument("--apk", action="append", default=[], help="install: package=path")
    parser.add_argument("--family")
    parser.add_argument("--case", nargs="*")
    parser.add_argument("--timeout", type=int, default=900)
    parser.add_argument("--numbers", type=int, default=12, help="run: key numbers printed per case")
    args = parser.parse_args()
    if args.phase != "list" and not args.state:
        parser.error("--state is required")
    {"record": record, "backup": backup, "install": install, "run": run, "restore": restore,
     "verify": verify, "list": list_cases}[args.phase](args)


if __name__ == "__main__":
    main()
