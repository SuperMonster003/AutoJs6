package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONArray
import java.io.File

class NodeBridgeConformanceInstrumentationTest {

    private val context = NodePluginTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @After
    fun settleAfter() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @Test
    fun kotlinStableBridgeRegistryMatchesConformanceSurface() {
        stableDispatchMethods.forEach { (module, methods) ->
            assertEquals("$module dispatch methods drifted", methods, NodeBridgeModules.supportedMethodsByModule.getValue(module))
            methods.forEach { method ->
                assertTrue("$module.$method must be supported", NodeBridgeModules.supports(module, method))
                assertTrue(
                    "$module.$method must declare bridge capabilities",
                    NodeBridgePermissionManifest.requiredCapabilitiesFor(module, method).isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun pinyinStableBridgeRegistryMatchesConformanceSurface() {
        val expectedMethods = setOf("convert", "simple", "compare", "compact", "fromCodePoint", "fromPhrase")
        assertEquals(expectedMethods, stableDispatchMethods.getValue(NodeBridgeModules.PINYIN))
        assertEquals(expectedMethods, NodeBridgeModules.supportedMethodsByModule.getValue(NodeBridgeModules.PINYIN))
        expectedMethods.forEach { method ->
            assertTrue(NodeBridgeModules.supports(NodeBridgeModules.PINYIN, method))
            assertEquals(
                listOf(NodeBridgePermissionManifest.PINYIN),
                NodeBridgePermissionManifest.requiredCapabilitiesFor(NodeBridgeModules.PINYIN, method),
            )
        }
    }

    @Test
    fun pinyin4jStableBridgeRegistryMatchesConformanceSurface() {
        val expectedMethods = setOf("of", "as", "readings")
        assertEquals(expectedMethods, stableDispatchMethods.getValue(NodeBridgeModules.PINYIN4J))
        assertEquals(expectedMethods, NodeBridgeModules.supportedMethodsByModule.getValue(NodeBridgeModules.PINYIN4J))
        expectedMethods.forEach { method ->
            assertTrue(NodeBridgeModules.supports(NodeBridgeModules.PINYIN4J, method))
            assertEquals(
                listOf(NodeBridgePermissionManifest.PINYIN4J),
                NodeBridgePermissionManifest.requiredCapabilitiesFor(NodeBridgeModules.PINYIN4J, method),
            )
        }
    }

    @Test
    fun jsStableBridgeModuleExportsAndPromiseContractsAreStable() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node bridge conformance suite")

        val workDir = File(context.cacheDir, "node-bridge-conformance").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge conformance working directory: $absolutePath" }
        }
        File(workDir, "main.cjs").writeText("""console.log("scheduled");""")
        writeBridgePermissionManifest(workDir)

        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const assert = require("assert");
              const bridge = require("autojs6:bridge");
              const requests = [];
              let uiDrained = false;

              function respond(request, ok, result, error) {
                setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                  id: request.id,
                  ok,
                  result,
                  error
                })));
              }
              function imageHandle(id, width, height) {
                return { id, __autojs6ImageHandle: id, width, height };
              }
              function uiNode(text) {
                return {
                  text,
                  desc: "desc-" + text,
                  id: "id/" + text,
                  className: "android.widget.TextView",
                  bounds: { left: 1, top: 2, right: 3, bottom: 4 },
                  clickable: true,
                  enabled: true
                };
              }
              function engineResult(sourceName) {
                return {
                  id: 1,
                  engineName: "nodejs",
                  sourceName,
                  workingDirectory: ".",
                  spawnDepth: 1
                };
              }
              function workTask(id, type) {
                return {
                  id,
                  type,
                  status: "registered",
                  scheduler: "fake",
                  backend: "embedded",
                  scriptPath: "main.cjs",
                  entryKind: "scriptPath",
                  packaged: false,
                  delayMs: 0,
                  intervalMs: type === "periodic" ? 900000 : null,
                  constraints: { charging: false, network: false, idle: false },
                  packageName: "org.autojs.autojs6",
                  createdAtEpochMs: 1
                };
              }
              function packageRecord(name, version) {
                return {
                  schema: "autojs6-node-android-package-record-v1",
                  name,
                  version,
                  kind: "dependency",
                  integrity: "sha512-conformance",
                  installedAtEpochMs: 1,
                  fileCount: 1,
                  totalBytes: 42,
                  packageDir: "/data/user/0/org.autojs.autojs6/files/nodejs/package-manager-store"
                };
              }

              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  const moduleName = request.module;
                  const method = request.method;
                  let result = null;

                  if (moduleName === "toast") {
                    result = null;
                  } else if (moduleName === "app") {
                    if (method === "launchPackage" || method === "launchApp") result = true;
                    else if (method === "getPackageName") result = request.args[0] === "AutoJs6" ? "org.autojs.autojs6" : null;
                    else if (method === "getAppName") result = request.args[0] === "org.autojs.autojs6" ? "AutoJs6" : null;
                    else if (method === "isInstalled") result = request.args[0] === "org.autojs.autojs6";
                    else if (method === "viewFile" || method === "editFile") result = true;
                    else result = null;
                  } else if (moduleName === "dialogs") {
                    result = method === "confirm" ? true : (method === "input" ? "typed" : (method === "select" ? 1 : null));
                  } else if (moduleName === "clipboard") {
                    result = method === "getText" ? "clip" : (method === "hasText" ? true : null);
                  } else if (moduleName === "device") {
                    if (method === "isScreenOn" || method === "isIgnoringBatteryOptimizations") result = true;
                    else if (method === "openBatteryOptimizationSettings") {
                      result = {
                        schema: "autojs6-node-battery-optimization-settings-v1",
                        sdkInt: 35,
                        ignoringBatteryOptimizations: true,
                        canOpenSettings: true,
                        opened: false,
                        dryRun: request.args[0].dryRun === true,
                        action: "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS",
                        reason: "settings-intent-dry-run"
                      };
                    }
                  } else if (moduleName === "shell") {
                    result = { code: 0, stdout: "ok\n", stderr: "" };
                  } else if (moduleName === "engines") {
                    if (method === "all") result = request.args[0];
                    else if (method === "stopAll") result = 2;
                    else if (method === "stopSelf") result = true;
                    else if (method === "execScript") result = engineResult("inline");
                    else if (method === "execScriptFile") result = engineResult(request.args[0]);
                  } else if (moduleName === "accessibility") {
                    if (method === "isEnabled" || method === "ensureEnabled" || method === "click" || method === "back" || method === "home" || method === "recentApps" || method === "clickText" || method === "longClick" || method === "setText" || method === "scrollForward" || method === "scrollBackward") result = true;
                    else if (method === "findByText" || method === "findOne") result = uiNode(method);
                    else if (method === "findAll") result = [uiNode("one"), uiNode("two")];
                  } else if (moduleName === "media_projection") {
                    if (method === "requestScreenCapture") result = { id: "capture-session", width: 100, height: 200 };
                    else if (method === "nextImage") result = imageHandle("capture-frame", 100, 200);
                    else if (method === "stop") result = null;
                  } else if (moduleName === "image" || moduleName === "images") {
                    if (method === "captureScreen") result = imageHandle(moduleName + "-capture", 100, 200);
                    else if (method === "readImage") result = imageHandle(moduleName + "-read", 10, 20);
                    else if (method === "clip") result = imageHandle(moduleName + "-clip", request.args[1].width, request.args[1].height);
                    else if (method === "resize") result = imageHandle(moduleName + "-resize", request.args[1].width, request.args[1].height);
                    else if (method === "grayscale") result = imageHandle(moduleName + "-gray", request.args[0].width, request.args[0].height);
                    else if (method === "threshold") result = imageHandle(moduleName + "-binary", request.args[0].width, request.args[0].height);
                    else if (method === "findImage") result = { x: 7, y: 8 };
                    else if (method === "matchTemplate") result = [{ x: 1, y: 2, score: 0.9 }];
                    else if (method === "findColor") result = { x: 5, y: 6 };
                    else if (method === "findMultiColors") result = { x: 9, y: 10 };
                    else if (method === "saveImage" || method === "recycle") result = null;
                  } else if (moduleName === "ocr") {
                    result = [{ text: "hello", confidence: 0.91, bounds: { left: 1, top: 2, right: 3, bottom: 4 } }];
                  } else if (moduleName === "barcode") {
                    const barcodeSnapshot = {
                      schema: "autojs6-node-barcode-snapshot-v1",
                      rawValue: "https://example.com",
                      displayValue: "https://example.com",
                      format: 256,
                      formatName: "QR_CODE",
                      valueType: 8,
                      valueTypeName: "URL",
                      boundingBox: { left: 1, top: 2, right: 3, bottom: 4, width: 2, height: 2 },
                      structured: { payloadType: "url", url: { title: "Example", url: "https://example.com" } }
                    };
                    if (method === "detect") result = barcodeSnapshot;
                    else if (method === "detectAll") result = [barcodeSnapshot];
                    else if (method === "recognizeText") result = "https://example.com";
                    else if (method === "recognizeTexts") result = ["https://example.com", "WIFI:T:WPA;S:AutoJs6;P:secret;;"];
                  } else if (moduleName === "media") {
                    if (method === "getAudioStreamVolume") result = 3;
                    else if (method === "getAudioStreamMaxVolume") result = 15;
                    else if (method === "getAudioStreamInfo") {
                      result = { schema: "autojs6-node-media-audio-stream-v1", stream: "music", streamType: 3, volume: 3, maxVolume: 15, minVolume: 0, volumeFixed: false };
                    }
                  } else if (moduleName === "mediainfo") {
                    if (method === "read") {
                      result = { schema: "autojs6-node-mediainfo-snapshot-v1", path: request.args[0], fileName: "sample.mp3", sizeBytes: 4, inform: "", sections: {} };
                    } else if (method === "get") {
                      result = "MPEG Audio";
                    } else if (method === "capabilities") {
                      result = { schema: "autojs6-node-mediainfo-capabilities-v1", snapshotSchemas: ["autojs6-plugin-mediainfo-snapshot-v1", "autojs6-plugin-mediainfo-snapshot-v2"], defaultSnapshotSchema: "autojs6-plugin-mediainfo-snapshot-v1", engineVersion: "MediaInfoLib - v26.05" };
                    }
                  } else if (moduleName === "recorder") {
                    if (method === "getStatus") {
                      result = { schema: "autojs6-node-recorder-status-v1", recording: false, providerAvailable: false, microphonePermissionRequired: true, microphonePermissionGranted: false, foregroundDisclosureRequired: true, reason: "blocked_until_privacy_reviewed_recorder_provider" };
                    } else if (method === "start" || method === "stop") {
                      respond(request, false, null, {
                        name: "Error",
                        message: "recorder blocked",
                        code: "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED",
                        module: "recorder",
                        method
                      });
                      return;
                    }
                  } else if (moduleName === "storage" || moduleName === "storages") {
                    if (method === "get") result = { found: true, value: { ok: true } };
                    else if (method === "keys") result = ["alpha"];
                    else if (method === "contains") result = true;
                    else result = null;
                  } else if (moduleName === "database" || moduleName === "sqlite") {
                    if (method === "open") result = { id: moduleName + "-handle", name: request.args[0].name };
                    else if (method === "run") result = { rowsAffected: 1, insertId: 7 };
                    else if (method === "get") result = { name: "alpha", value: 1 };
                    else if (method === "all") result = [{ name: "alpha", value: 1 }];
                    else if (method === "transaction") result = [{ rowsAffected: 1, insertId: 8 }];
                    else if (method === "close") result = true;
                    else result = null;
                  } else if (moduleName === "notifications") {
                    if (method === "notify") result = request.args[0].id || 9001;
                    else if (method === "getPermissionStatus") {
                      result = {
                        schema: "autojs6-node-notification-permission-status-v1",
                        sdkInt: 35,
                        notificationsEnabled: true,
                        postNotificationsPermissionRequired: true,
                        postNotificationsPermissionGranted: true,
                        canOpenSettings: true
                      };
                    } else if (method === "openSettings") {
                      result = {
                        schema: "autojs6-node-notification-permission-status-v1",
                        sdkInt: 35,
                        notificationsEnabled: true,
                        postNotificationsPermissionRequired: true,
                        postNotificationsPermissionGranted: true,
                        canOpenSettings: true,
                        opened: false,
                        dryRun: request.args[0].dryRun === true,
                        action: "android.settings.APP_NOTIFICATION_SETTINGS",
                        reason: "settings-intent-dry-run"
                      };
                    }
                  } else if (moduleName === "sensors") {
                    if (method === "getAvailableSensors") {
                      result = [{ type: "accelerometer", name: "fake accelerometer", vendor: "AutoJs6", version: 1 }];
                    } else if (method === "once") {
                      result = { type: request.args[0].type, timestamp: 1, accuracy: 3, values: [1, 2, 3] };
                    } else if (method === "subscribe") {
                      globalThis.__sensorConformanceType=request.args[0].type;
                      result = { id: "sensor-conformance", type: request.args[0].type, samplingIntervalMs: request.args[0].samplingIntervalMs, maxQueueSize: request.args[0].maxQueueSize };
                    } else if (method === "drainEvents") {
                      result = [{ type: "event", event: { type: globalThis.__sensorConformanceType, timestamp: 2, accuracy: 3, values: globalThis.__sensorConformanceType === "light" ? [42] : [1,2,3] } }];
                    } else if (method === "unsubscribe") {
                      result = null;
                    }
                  } else if (moduleName === "ui") {
                    if (method === "showLayout") {
                      result = { id: "ui-conformance" };
                    } else if (method === "drainEvents") {
                      result = uiDrained ? [] : [{ type: "click", id: "ok", text: "OK" }];
                      uiDrained = true;
                  } else if (method === "update" || method === "batchUpdate" || method === "close") {
                    result = null;
                  }
                  } else if (moduleName === "work_manager") {
                    if (method === "scheduleOnce") result = "work-once";
                    else if (method === "schedulePeriodic") result = "work-periodic";
                    else if (method === "cancel") result = null;
                    else if (method === "list") result = [workTask("work-periodic", "periodic")];
                  } else if (moduleName === "package_manager") {
                    if (method === "list") result = [packageRecord("@autojs6/conformance", "1.0.0")];
                    else if (method === "planInstall") {
                      result = {
                        schema: "autojs6-node-android-package-plan-v1",
                        operation: "install",
                        status: "ready",
                        dryRun: true,
                        sourceAuthority: "local_unpacked_host_managed_only",
                        installAuthority: "package_manager_mutate_explicit_local_unpacked",
                        grantsInstallAuthority: false,
                        message: "Package source is ready for install dry-run.",
                        record: packageRecord(request.args[0].name, request.args[0].version),
                        existingRecord: null
                      };
                    } else if (method === "planUpdate") {
                      result = {
                        schema: "autojs6-node-android-package-plan-v1",
                        operation: "update",
                        status: "ready",
                        dryRun: true,
                        sourceAuthority: "local_unpacked_host_managed_only",
                        installAuthority: "package_manager_mutate_explicit_local_unpacked",
                        grantsInstallAuthority: false,
                        message: "Package source is ready for update dry-run.",
                        record: packageRecord(request.args[0].name, request.args[0].version),
                        existingRecord: packageRecord(request.args[0].name, request.args[0].version)
                      };
                    } else if (method === "planRemove") {
                      result = {
                        schema: "autojs6-node-android-package-plan-v1",
                        operation: "remove",
                        status: "ready",
                        dryRun: true,
                        sourceAuthority: "local_unpacked_host_managed_only",
                        installAuthority: "package_manager_mutate_explicit_local_unpacked",
                        grantsInstallAuthority: false,
                        message: "Package is ready for remove dry-run.",
                        record: null,
                        existingRecord: packageRecord(request.args[0].name, request.args[0].version)
                      };
                    } else if (method === "install" || method === "update" || method === "remove") {
                      result = {
                        operation: method,
                        status: method === "install" ? "installed" : (method === "update" ? "updated" : "removed"),
                        transactionId: method + "-tx",
                        message: method + " ok",
                        record: method === "remove" ? null : packageRecord(request.args[0].name, request.args[0].version)
                      };
                    }
                    else if (method === "verify") {
                      result = {
                        operation: "verify",
                        status: "verified",
                        transactionId: "",
                        message: "Package integrity verified.",
                        record: packageRecord(request.args[0].name, request.args[0].version)
                      };
                    } else if (method === "prune") {
                      result = {
                        operation: "prune",
                        status: "pruned",
                        transactionId: "",
                        message: "Removed 0 stale transaction(s).",
                        record: null
                      };
                    }
                  }
                  respond(request, true, result);
                }
              });

              function expectFrozen(name, value) {
                assert.strictEqual(Object.isFrozen(value), true, name + " must be frozen");
              }
              function expectExports(name, moduleValue, expected) {
                expected.forEach((entry) => assert.strictEqual(typeof moduleValue[entry], "function", name + "." + entry));
              }
              function expectPromise(name, value) {
                assert(value && typeof value.then === "function", name + " must return a Promise");
                return value;
              }
              async function expectRejectCode(name, promise, code) {
                try {
                  await promise;
                } catch (error) {
                  assert.strictEqual(String(error && (error.autojs6Code || error.code || "")), code, name);
                  return;
                }
                throw new Error(name + " must reject");
              }
              function expectThrowCode(name, fn, code) {
                try {
                  fn();
                } catch (error) {
                  assert.strictEqual(String(error && (error.autojs6Code || error.code || "")), code, name);
                  return;
                }
                throw new Error(name + " must throw");
              }
              const toast = require("toast");
              const app = require("app");
              const dialogs = require("dialogs");
              const clipboard = require("clipboard");
              const device = require("device");
              const shell = require("shell");
              const engines = require("engines");
              const accessibility = require("accessibility");
              const mediaProjection = require("media_projection");
              const image = require("image");
              const images = require("images");
              const ocr = require("ocr");
              const barcode = require("barcode");
              const media = require("media");
              const mediainfo = require("mediainfo");
              const recorder = require("recorder");
              const storage = require("storage");
              const storages = require("storages");
              const database = require("database");
              const sqlite = require("sqlite");
              const notifications = require("notifications");
              const sensors = require("sensors");
              const ui = require("ui");
              const workManager = require("work_manager");
              const packageManager = require("package_manager");
              const npm = require("npm");
              const compat = require("autojs6:compat");
              const jsox = require("jsox");
              const jsoxMathx = require("jsox.mathx");
              const jsoxArrayx = require("jsox.arrayx");
              const jsoxNumberx = require("jsox.numberx");

              [
                ["toast", toast],
                ["app", app],
                ["dialogs", dialogs],
                ["clipboard", clipboard],
                ["device", device],
                ["shell", shell],
                ["engines", engines],
                ["accessibility", accessibility],
                ["media_projection", mediaProjection],
                ["image", image],
                ["images", images],
                ["ocr", ocr],
                ["barcode", barcode],
                ["media", media],
                ["mediainfo", mediainfo],
                ["recorder", recorder],
                ["storage", storage],
                ["storages", storages],
                ["database", database],
                ["sqlite", sqlite],
                ["notifications", notifications],
                ["sensors", sensors],
                ["ui", ui],
                ["work_manager", workManager],
                ["package_manager", packageManager],
                ["npm", npm],
                ["jsox", jsox],
                ["jsox.mathx", jsoxMathx],
                ["jsox.mathx.alias", jsox.mathx],
                ["jsox.arrayx", jsoxArrayx],
                ["jsox.arrayx.alias", jsox.arrayx],
                ["jsox.numberx", jsoxNumberx],
                ["jsox.numberx.alias", jsox.numberx],
                ["autojs6:compat", compat],
                ["autojs6:compat.jsox", compat.jsox],
                ["autojs6:compat.jsox.mathx", compat.jsox.mathx],
                ["autojs6:compat.jsox.arrayx", compat.jsox.arrayx],
                ["autojs6:compat.jsox.numberx", compat.jsox.numberx],
                ["autojs6:compat.barcode", compat.barcode],
                ["autojs6:compat.qrcode", compat.qrcode],
                ["autojs6:compat.media", compat.media],
                ["autojs6:compat.mediainfo", compat.mediainfo],
                ["autojs6:compat.recorder", compat.recorder],
                ["autojs6:compat.ui", compat.ui],
                ["autojs6:compat.tasks", compat.tasks]
              ].forEach(([name, moduleValue]) => expectFrozen(name, moduleValue));

              expectExports("toast", toast, ["showToast", "toast"]);
              expectExports("app", app, ["intent", "intentToShell", "openUrl", "sendEmail", "uninstall", "getPackageName", "getAppName", "isInstalled", "viewFile", "editFile", "launch", "launchPackage", "launchApp", "openAppSetting", "openAppSettings", "launchSettings", "launchAppDetailsSettings", "startActivity", "sendBroadcast"]);
              expectExports("dialogs", dialogs, ["alert", "confirm", "input", "prompt", "select"]);
              expectExports("clipboard", clipboard, ["getText", "setText", "hasText"]);
              expectExports("device", device, ["isScreenOn", "wakeUp", "vibrate", "isIgnoringBatteryOptimizations", "openBatteryOptimizationSettings"]);
              expectExports("shell", shell, ["exec", "execRoot", "execShizuku", "execFile", "checkAccess", "setDefaultTimeout"]);
              expectExports("engines", engines, ["myEngine", "all", "stopAll", "stopSelf", "execScript", "execScriptFile"]);
              expectExports("accessibility", accessibility, ["isEnabled", "ensureEnabled", "click", "back", "home", "recentApps", "findByText", "clickText", "findOne", "findAll", "longClick", "setText", "scrollForward", "scrollBackward", "text", "desc", "id", "className", "textContains", "descContains", "textMatches", "descMatches", "idMatches", "classNameMatches", "clickable", "enabled", "depth", "boundsInside", "boundsContains"]);
              expectExports("media_projection", mediaProjection, ["requestScreenCapture"]);
              expectExports("image", image, ["captureScreen", "readImage", "saveImage", "clip", "resize", "grayscale", "threshold", "findImage", "matchTemplate", "findColor", "findMultiColors", "getSize", "recycle"]);
              expectExports("images", images, ["captureScreen", "readImage", "saveImage", "clip", "resize", "grayscale", "threshold", "findImage", "matchTemplate", "findColor", "findMultiColors", "getSize", "recycle"]);
              expectExports("ocr", ocr, ["recognize", "recognizeText", "detectTextBounds"]);
              expectExports("barcode", barcode, ["detect", "detectAll", "recognizeText", "recognizeTexts"]);
              expectExports("media", media, ["getAudioStreamVolume", "getAudioStreamMaxVolume", "getAudioStreamInfo"]);
              expectExports("mediainfo", mediainfo, ["read", "get", "capabilities"]);
              expectExports("recorder", recorder, ["getStatus", "start", "stop"]);
              expectExports("storage", storage, ["create", "open"]);
              expectExports("storages", storages, ["create", "open"]);
              expectExports("database", database, ["open"]);
              expectExports("sqlite", sqlite, ["open"]);
              expectExports("notifications", notifications, ["notify", "cancel", "cancelAll", "getPermissionStatus", "openSettings", "openNotificationSettings"]);
              expectExports("sensors", sensors, ["getAvailableSensors", "once", "subscribe"]);
              expectExports("ui", ui, ["showLayout", "state", "bind"]);
              expectExports("work_manager", workManager, ["scheduleOnce", "schedulePeriodic", "cancel", "list"]);
              expectExports("package_manager", packageManager, ["status", "policy", "sourcePolicy", "planInstall", "planUpdate", "planRemove", "planRegistryInstall", "planTarballInstall", "install", "update", "remove", "list", "verify", "prune"]);
              expectExports("npm", npm, ["status", "policy", "sourcePolicy", "packageManager", "planInstall", "planUpdate", "planRemove", "planRegistryInstall", "planTarballInstall", "install", "update", "remove", "list", "verify", "prune", "cli", "exec", "runScript", "installFromRegistry", "publish", "pack"]);
              expectExports("jsox", jsox, ["extend", "extendAll", "status"]);
              expectExports("jsox.mathx", jsoxMathx, ["randInt", "randomInt", "randFloat", "randomFloat", "random", "sum", "mean", "avg", "median", "var", "std", "cv", "mode", "dist", "logMn", "floorLog", "ceilLog", "roundLog", "floorPow", "ceilPow", "roundPow", "max", "maxi", "min", "mini"]);
              expectExports("jsox.arrayx", jsoxArrayx, ["ensureArray", "distinct", "distinctBy", "union", "intersect", "subtract", "differ", "sortBy", "sortByDescending", "sortDescending", "sorted", "sortedBy", "sortedByDescending", "sortedDescending", "shuffle"]);
              expectExports("jsox.numberx", jsoxNumberx, ["ensureNumber", "ensureNumberLike", "ensureNumbersLike", "check", "clamp", "clampTo", "toFixedNum", "padStart", "padEnd", "parseFloat", "parsePercent", "parseRatio", "parseAny"]);
              assert.strictEqual(jsoxNumberx.ICU, 996);
              expectExports("autojs6:compat.jsox", compat.jsox, ["extend", "extendAll", "status"]);
              expectExports("autojs6:compat.jsox.mathx", compat.jsox.mathx, ["randInt", "randomInt", "randFloat", "randomFloat", "random", "sum", "mean", "avg", "median", "var", "std", "cv", "mode", "dist", "logMn", "floorLog", "ceilLog", "roundLog", "floorPow", "ceilPow", "roundPow", "max", "maxi", "min", "mini"]);
              expectExports("autojs6:compat.jsox.arrayx", compat.jsox.arrayx, ["ensureArray", "distinct", "distinctBy", "union", "intersect", "subtract", "differ", "sortBy", "sortByDescending", "sortDescending", "sorted", "sortedBy", "sortedByDescending", "sortedDescending", "shuffle"]);
              expectExports("autojs6:compat.jsox.numberx", compat.jsox.numberx, ["ensureNumber", "ensureNumberLike", "ensureNumbersLike", "check", "clamp", "clampTo", "toFixedNum", "padStart", "padEnd", "parseFloat", "parsePercent", "parseRatio", "parseAny"]);
              assert.strictEqual(compat.jsox.numberx.ICU, 996);
              assert.strictEqual(jsox.mathx, jsox.Mathx);
              assert.strictEqual(jsox.arrayx, jsox.Arrayx);
              assert.strictEqual(jsox.numberx, jsox.Numberx);
              assert.strictEqual(jsox.mathx, jsoxMathx);
              assert.strictEqual(jsox.arrayx, jsoxArrayx);
              assert.strictEqual(jsox.numberx, jsoxNumberx);
              assert.strictEqual(compat.jsox.mathx, jsox.mathx);
              assert.strictEqual(compat.jsox.arrayx, jsox.arrayx);
              assert.strictEqual(compat.jsox.numberx, jsox.numberx);
              expectExports("autojs6:compat.barcode", compat.barcode, ["detect", "detectAll", "recognizeText", "recognizeTexts"]);
              expectExports("autojs6:compat.qrcode", compat.qrcode, ["detect", "detectAll", "recognizeText", "recognizeTexts"]);
              expectExports("autojs6:compat.media", compat.media, ["getAudioStreamVolume", "getAudioStreamMaxVolume", "getAudioStreamInfo"]);
              expectExports("autojs6:compat.mediainfo", compat.mediainfo, ["read", "get", "capabilities"]);
              expectExports("autojs6:compat.recorder", compat.recorder, ["getStatus", "start", "stop"]);
              expectExports("autojs6:compat.ui", compat.ui, ["showLayout", "state", "bind"]);
              expectExports("autojs6:compat.tasks", compat.tasks, ["addDisposableTask", "addDailyTask", "addWeeklyTask", "addIntentTask", "removeTask", "queryTasks", "queryIntentTasks", "queryTimedTasks", "getIntentTask", "getTimedTask", "removeIntentTask", "removeTimedTask", "updateTask"]);
              assert.strictEqual(globalThis.barcode, undefined, "compat.barcode must not install a default global");
              assert.strictEqual(globalThis.qrcode, undefined, "compat.qrcode must not install a default global");
              assert.strictEqual(globalThis.jsox, undefined, "compat.jsox must not install a default global");
              assert.strictEqual(globalThis.ui, undefined, "compat.ui must not install a default global");
              assert.strictEqual(globalThis.tasks, undefined, "compat.tasks must not install a default global");
              assert.strictEqual(typeof compat.ui.run, "undefined");
              assert.strictEqual(typeof compat.ui.post, "undefined");
              assert.strictEqual(typeof compat.ui.findById, "undefined");
              const compatInstall = compat.install({ globals: true });
              assert(compatInstall.globals.indexOf("barcode") >= 0, "compat.install({ globals: true }) must install barcode global");
              assert(compatInstall.globals.indexOf("qrcode") >= 0, "compat.install({ globals: true }) must install qrcode global");
              assert(compatInstall.globals.indexOf("media") >= 0, "compat.install({ globals: true }) must install media global");
              assert(compatInstall.globals.indexOf("mediainfo") >= 0, "compat.install({ globals: true }) must install mediainfo global");
              assert(compatInstall.globals.indexOf("recorder") >= 0, "compat.install({ globals: true }) must install recorder global");
              assert(compatInstall.globals.indexOf("jsox") >= 0, "compat.install({ globals: true }) must install jsox global");
              assert.strictEqual(globalThis.barcode, compat.barcode);
              assert.strictEqual(globalThis.qrcode, compat.qrcode);
              assert.strictEqual(globalThis.media, compat.media);
              assert.strictEqual(globalThis.mediainfo, compat.mediainfo);
              assert.strictEqual(globalThis.recorder, compat.recorder);
              assert.strictEqual(globalThis.jsox, compat.jsox);
              assert.strictEqual(globalThis.jsox.mathx.sum(1, 2, 3), 6);
              assert.strictEqual(globalThis.jsox.arrayx.sorted([2, 1]).join(","), "1,2");
              assert.strictEqual(globalThis.jsox.numberx.parseRatio("3:2"), 1.5);
              assert(compatInstall.globals.indexOf("ui") >= 0, "compat.install({ globals: true }) must install constrained ui global");
              assert.strictEqual(globalThis.ui, compat.ui);
              assert.strictEqual(globalThis.tasks, undefined, "compat.install({ globals: true }) must not install tasks global");
              assert.strictEqual(typeof globalThis.ui.layout, "undefined");
              assert(compat.status().implementedTasks.indexOf("P12-29") >= 0, "compat.status must include P12-29");
              assert(compat.status().implementedTasks.indexOf("P13-26") >= 0, "compat.status must include P13-26");
              assert(compat.status().implementedTasks.indexOf("P13-27") >= 0, "compat.status must include P13-27");

              assert.strictEqual(typeof app.packageName, "string");
              assert.strictEqual(typeof app.versionName, "string");
              assert.strictEqual(typeof app.versionCode, "number");
              assert.strictEqual(typeof device.sdkInt, "number");
              assert.strictEqual(typeof device.width, "number");
              assert.strictEqual(typeof device.height, "number");
              assert.strictEqual(typeof device.density, "number");
              assert.strictEqual(typeof workManager.policy.maxTasksPerProvider, "number");
              const packageManagerStatus = packageManager.status();
              const packageManagerPolicy = packageManager.policy();
              const packageManagerSourcePolicy = packageManager.sourcePolicy();
              const npmStatus = npm.status();
              const npmPolicy = npm.policy();
              assert.strictEqual(Object.isFrozen(packageManagerStatus), true, "package_manager.status result must be frozen");
              assert.strictEqual(Object.isFrozen(packageManagerPolicy), true, "package_manager.policy result must be frozen");
              assert.strictEqual(Object.isFrozen(packageManagerSourcePolicy), true, "package_manager.sourcePolicy result must be frozen");
              assert.strictEqual(Object.isFrozen(npmStatus), true, "npm.status result must be frozen");
              assert.strictEqual(Object.isFrozen(npmPolicy), true, "npm.policy result must be frozen");
              assert.strictEqual(Object.isFrozen(packageManagerSourcePolicy.allowedSources), true, "package_manager.sourcePolicy allowedSources must be frozen");
              assert.strictEqual(Object.isFrozen(packageManagerSourcePolicy.deniedSources), true, "package_manager.sourcePolicy deniedSources must be frozen");
              assert.strictEqual(Object.isFrozen(packageManagerSourcePolicy.localUnpacked), true, "package_manager.sourcePolicy localUnpacked must be frozen");
              assert.strictEqual(packageManagerStatus.status, "guarded_mutation_partial");
              assert.strictEqual(packageManagerStatus.jsBridge, "guarded_mutation_partial");
              assert.strictEqual(packageManagerStatus.requirePackageManager, "guarded_mutation_partial");
              assert.strictEqual(packageManagerStatus.grantsInstallAuthority, true);
              assert.strictEqual(packageManagerStatus.sourceAuthority, "local_unpacked_host_managed_only");
              assert.strictEqual(packageManagerPolicy.npmCli, "denied");
              assert.strictEqual(packageManagerPolicy.registryDownload, "deferred");
              assert.strictEqual(packageManagerPolicy.rawRegistryAccess, false);
              assert.strictEqual(packageManagerPolicy.installAuthority, "package_manager_mutate_explicit_local_unpacked");
              assert.deepStrictEqual(packageManagerSourcePolicy.allowedSources, ["local_unpacked_host_managed"]);
              assert.deepStrictEqual(packageManagerSourcePolicy.planOperations, ["planInstall", "planUpdate", "planRemove", "planRegistryInstall", "planTarballInstall"]);
              assert.deepStrictEqual(packageManagerSourcePolicy.mutationOperations, ["install", "update", "remove"]);
              assert.strictEqual(packageManagerSourcePolicy.mutationCapability, "package_manager.mutate");
              assert.strictEqual(packageManagerSourcePolicy.status, "guarded_local_unpacked_mutation");
              assert.strictEqual(packageManagerSourcePolicy.grantsInstallAuthority, true);
              assert.strictEqual(packageManagerSourcePolicy.registry.rawRegistryAccess, false);
              assert.strictEqual(packageManagerPolicy.npmFacade, "guarded_package_manager_alias");
              assert.strictEqual(packageManagerPolicy.requireNpm, "guarded_package_manager_alias");
              assert.strictEqual(npmStatus.status, "guarded_package_manager_alias");
              assert.strictEqual(npmStatus.aliasFor, "package_manager");
              assert.strictEqual(npmStatus.npmCli, "denied");
              assert.strictEqual(npmPolicy.cli, "denied");
              assert.strictEqual(npmPolicy.registryDownload, "deferred");
              assert.strictEqual(npmPolicy.rawRegistryAccess, false);
              assert.strictEqual(npm.packageManager(), packageManager);
              assert.strictEqual(npm.planRegistryInstall, packageManager.planRegistryInstall);
              assert.strictEqual(npm.planTarballInstall, packageManager.planTarballInstall);
              assert.strictEqual(engines.myEngine().engineName, "nodejs");

              await expectPromise("toast.showToast", toast.showToast("hello", { timeoutMs: 1010 }));
              await expectPromise("toast.toast", toast.toast("alias", { timeoutMs: 1011 }));
              await expectPromise("app.launchPackage", app.launchPackage("org.autojs.autojs6", { timeoutMs: 1012 }));
              await expectPromise("app.launchApp", app.launchApp("AutoJs6", { timeoutMs: 1013 }));
              assert.strictEqual(await expectPromise("app.getPackageName", app.getPackageName("AutoJs6", { timeoutMs: 1013 })), "org.autojs.autojs6");
              assert.strictEqual(await expectPromise("app.getAppName", app.getAppName("org.autojs.autojs6", { timeoutMs: 1013 })), "AutoJs6");
              assert.strictEqual(await expectPromise("app.isInstalled", app.isInstalled("org.autojs.autojs6", { timeoutMs: 1013 })), true);
              assert.strictEqual(await expectPromise("app.getPackageName.missing", app.getPackageName("__autojs6_missing_app__", { timeoutMs: 1013 })), null);
              assert.strictEqual(await expectPromise("app.getAppName.missing", app.getAppName("org.autojs6.missing", { timeoutMs: 1013 })), null);
              assert.strictEqual(await expectPromise("app.isInstalled.missing", app.isInstalled("org.autojs6.missing", { timeoutMs: 1013 })), false);
              assert.strictEqual(await expectPromise("app.viewFile", app.viewFile("README.md", { timeoutMs: 1013 })), true);
              assert.strictEqual(await expectPromise("app.editFile", app.editFile("README.md", { timeoutMs: 1013 })), true);
              assert.strictEqual(await expectPromise("app.launch", app.launch("org.autojs.autojs6", { timeoutMs: 1013 })), true);
              const parsedUri = app.parseUri("https://example.com/path?q=1#frag");
              assert.strictEqual(Object.isFrozen(parsedUri), true, "app.parseUri descriptor must be frozen");
              assert.strictEqual(parsedUri.schema, "autojs6-node-app-uri-descriptor-v1");
              assert.strictEqual(parsedUri.scheme, "https");
              assert.strictEqual(parsedUri.host, "example.com");
              assert.strictEqual(parsedUri.path, "/path");
              assert.strictEqual(parsedUri.query, "q=1");
              assert.strictEqual(parsedUri.fragment, "frag");
              assert.strictEqual(app.parseUri("README.md").path, "README.md");
              assert.strictEqual(app.parseUri(null), null);
              await expectPromise("app.openAppSetting", app.openAppSetting("org.autojs.autojs6", { timeoutMs: 1014 }));
              const viewIntent = app.intent({
                action: "VIEW",
                url: "example.com",
                category: "BROWSABLE",
                categories: ["android.intent.category.DEFAULT", "android.intent.category.BROWSABLE"],
                flags: [0x10000000, 0x00000020],
                extras: { source: "conformance", retry: 2, enabled: true, labels: ["node", "intent"] }
              });
              assert.strictEqual(Object.isFrozen(viewIntent), true, "app.intent descriptor must be frozen");
              assert.strictEqual(viewIntent.action, "android.intent.action.VIEW");
              assert.strictEqual(viewIntent.data, "http://example.com");
              assert.deepStrictEqual(viewIntent.categories, ["android.intent.category.BROWSABLE", "android.intent.category.DEFAULT"]);
              assert.strictEqual(viewIntent.flags, 0x10000020);
              const beforeIntentToShell = requests.length;
              const shellIntent = app.intentToShell(viewIntent);
              assert.strictEqual(typeof shellIntent, "string");
              assert(shellIntent.includes("-a 'android.intent.action.VIEW'"), shellIntent);
              assert(shellIntent.includes("-d 'http://example.com'"), shellIntent);
              assert(shellIntent.includes("-c 'android.intent.category.BROWSABLE'"), shellIntent);
              assert(shellIntent.includes("-c 'android.intent.category.DEFAULT'"), shellIntent);
              assert(shellIntent.includes("-f 268435488"), shellIntent);
              assert(shellIntent.includes("--es 'source' 'conformance'"), shellIntent);
              assert(shellIntent.includes("--ei 'retry' 2"), shellIntent);
              assert(shellIntent.includes("--ez 'enabled' true"), shellIntent);
              assert(shellIntent.includes("--esa 'labels' 'node,intent'"), shellIntent);
              assert.strictEqual(requests.length, beforeIntentToShell, "app.intentToShell must not dispatch bridge requests");
              await expectPromise("app.startActivity", app.startActivity(viewIntent, { timeoutMs: 1015 }));
              await expectPromise("app.startActivity.url", app.startActivity("https://example.com/path", { timeoutMs: 1016 }));
              await expectPromise("app.openUrl", app.openUrl("example.com/docs", { timeoutMs: 1017 }));
              await expectPromise("app.sendEmail", app.sendEmail({ to: ["dev@example.com"], subject: "AutoJs6", text: "Node app facade" }, { timeoutMs: 1018 }));
              await expectPromise("app.openAppSettings", app.openAppSettings({ timeoutMs: 1019 }));
              await expectPromise("app.launchSettings", app.launchSettings("org.autojs.autojs6", { timeoutMs: 1020 }));
              await expectPromise("app.launchAppDetailsSettings", app.launchAppDetailsSettings("org.autojs.autojs6", { timeoutMs: 1021 }));
              const appStartRequest = requests.filter((request) => request.module === "app" && request.method === "startActivity").pop();
              assert(appStartRequest, "app.startActivity request must be dispatched");
              assert.strictEqual(appStartRequest.args[0].action, "android.intent.action.SENDTO");
              assert.strictEqual(appStartRequest.args[0].data, "mailto:dev%40example.com?subject=AutoJs6&body=Node%20app%20facade");
              assert.deepStrictEqual(appStartRequest.args[0].extras["android.intent.extra.EMAIL"], ["dev@example.com"]);
              const viewStartRequest = requests.filter((request) => request.module === "app" && request.method === "startActivity")[0];
              assert.deepStrictEqual(viewStartRequest.args[0].categories, ["android.intent.category.BROWSABLE", "android.intent.category.DEFAULT"]);
              assert.strictEqual(viewStartRequest.args[0].flags, 0x10000020);
              assert.deepStrictEqual(viewStartRequest.args[0].extras.labels, ["node", "intent"]);
              const beforeUninstall = requests.length;
              await expectPromise("app.uninstall", app.uninstall("com.example.invalid", { timeoutMs: 1022 }));
              const uninstallRequest = requests.slice(beforeUninstall).find((request) => request.module === "app" && request.method === "startActivity");
              assert(uninstallRequest, "app.uninstall must dispatch through startActivity");
              assert.strictEqual(uninstallRequest.args[0].action, "android.intent.action.DELETE");
              assert.strictEqual(uninstallRequest.args[0].data, "package:com.example.invalid");
              const appSettingsRequests = requests.filter((request) => request.module === "app" && request.method === "openAppSetting");
              assert(appSettingsRequests.length >= 4, "app settings aliases must dispatch through openAppSetting bridge method");
              await expectPromise("app.sendBroadcast", app.sendBroadcast({ action: "org.autojs6.TEST" }));
              const broadcastRequest = requests.filter(request => request.module === "app" && request.method === "sendBroadcast").pop();
              assert.strictEqual(broadcastRequest.args[0].action, "org.autojs6.TEST");
              const beforeAppRejects = requests.length;
              expectThrowCode("app.getUriForFile", () => app.getUriForFile("README.md"), "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED");
              await expectRejectCode("app.kill", app.kill("org.autojs.autojs6"), "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED");
              await expectRejectCode("app.startDualActivity", app.startDualActivity({ action: "VIEW", url: "example.com" }), "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED");
              await expectRejectCode("app.launchDualPackage", app.launchDualPackage("org.autojs.autojs6"), "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED");
              await expectRejectCode("app.isDualInstalled", app.isDualInstalled("org.autojs.autojs6"), "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED");
              await expectRejectCode("app.intent.root", Promise.resolve().then(() => app.intent({ action: "android.intent.action.VIEW", root: true })), "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED");
              assert.strictEqual(requests.length, beforeAppRejects, "app raw Uri/kill/dual/root rejected stubs must not dispatch bridge requests");
              await expectPromise("dialogs.alert", dialogs.alert("title", "message", { timeoutMs: 1016 }));
              assert.strictEqual(await expectPromise("dialogs.confirm", dialogs.confirm("title", "message", { timeoutMs: 1017 })), true);
              assert.strictEqual(await expectPromise("dialogs.input", dialogs.input("title", "prefill", { timeoutMs: 1018 })), "typed");
              assert.strictEqual(await expectPromise("dialogs.prompt", dialogs.prompt("title", { timeoutMs: 1019 })), "typed");
              assert.strictEqual(await expectPromise("dialogs.select", dialogs.select("title", ["a", "b"], { timeoutMs: 1020 })), 1);
              assert.strictEqual(await expectPromise("clipboard.getText", clipboard.getText({ timeoutMs: 1021 })), "clip");
              await expectPromise("clipboard.setText", clipboard.setText("clip", { timeoutMs: 1022 }));
              assert.strictEqual(await expectPromise("clipboard.hasText", clipboard.hasText({ timeoutMs: 1023 })), true);
              assert.strictEqual(await expectPromise("device.isScreenOn", device.isScreenOn({ timeoutMs: 1024 })), true);
              await expectPromise("device.wakeUp", device.wakeUp({ timeoutMs: 1025 }));
              await expectPromise("device.vibrate", device.vibrate(5, { timeoutMs: 1026 }));
              assert.strictEqual(await expectPromise("device.isIgnoringBatteryOptimizations", device.isIgnoringBatteryOptimizations({ timeoutMs: 1026 })), true);
              const batterySettings = await expectPromise("device.openBatteryOptimizationSettings", device.openBatteryOptimizationSettings({ dryRun: true, timeoutMs: 1026 }));
              assert.strictEqual(batterySettings.schema, "autojs6-node-battery-optimization-settings-v1");
              assert.strictEqual(batterySettings.dryRun, true);
              assert.strictEqual((await expectPromise("shell.exec", shell.exec("echo ok", { timeoutMs: 1027 }))).code, 0);
              assert.strictEqual(shell.setDefaultTimeout(2000), 2000);
              const shizukuAccess = shell.checkAccess({ shizuku: true });
              assert.strictEqual(shizukuAccess.shizuku, false);
              assert(Array.isArray(shizukuAccess.missingCapabilities), "Shizuku access diagnostics must expose missingCapabilities");
              const beforeShellRejects = requests.length;
              await expectRejectCode("shell.execShizuku", shell.execShizuku("id"), "ERR_AUTOJS6_SHELL_SHIZUKU_UNSUPPORTED");
              await expectRejectCode("shell.exec.shizuku", shell.exec("id", { shizuku: true }), "ERR_AUTOJS6_SHELL_SHIZUKU_UNSUPPORTED");
              assert.strictEqual(requests.length, beforeShellRejects, "shell Shizuku rejected stubs must not dispatch bridge requests");
              assert.strictEqual((await expectPromise("shell.execFile", shell.execFile("scripts/run.sh", ["--dry-run"], { timeoutMs: 1028 }))).code, 0);
              assert.strictEqual((await expectPromise("engines.all", engines.all({ timeoutMs: 1028 }))).length >= 1, true);
              assert.strictEqual(await expectPromise("engines.stopAll", engines.stopAll({ timeoutMs: 1029 })), 2);
              assert.strictEqual(await expectPromise("engines.stopSelf", engines.stopSelf({ timeoutMs: 1030 })), true);
              assert.strictEqual((await expectPromise("engines.execScript", engines.execScript("inline", "console.log(1)", { timeoutMs: 1031 }))).sourceName, "inline");
              assert.strictEqual((await expectPromise("engines.execScriptFile", engines.execScriptFile("main.cjs", { timeoutMs: 1032 }))).sourceName, "main.cjs");
              assert.strictEqual(await expectPromise("accessibility.isEnabled", accessibility.isEnabled({ timeoutMs: 1033 })), true);
              assert.strictEqual(await expectPromise("accessibility.ensureEnabled", accessibility.ensureEnabled({ prompt: false, timeoutMs: 1034 })), true);
              assert.strictEqual(await expectPromise("accessibility.click", accessibility.click(1, 2, { timeoutMs: 1035 })), true);
              assert.strictEqual(await expectPromise("accessibility.back", accessibility.back({ timeoutMs: 1036 })), true);
              assert.strictEqual(await expectPromise("accessibility.home", accessibility.home({ timeoutMs: 1037 })), true);
              assert.strictEqual(await expectPromise("accessibility.recentApps", accessibility.recentApps({ timeoutMs: 1038 })), true);
              assert.strictEqual((await expectPromise("accessibility.findByText", accessibility.findByText("OK", { timeoutMs: 1039 }))).text, "findByText");
              assert.strictEqual(await expectPromise("accessibility.clickText", accessibility.clickText("OK", { timeoutMs: 1040 })), true);
              assert.strictEqual((await expectPromise("accessibility.findOne", accessibility.findOne(accessibility.text("OK"), { timeoutMs: 1041 }))).text, "findOne");
              assert.strictEqual((await expectPromise("accessibility.findAll", accessibility.findAll(accessibility.textContains("O"), { timeoutMs: 1042 }))).length, 2);
              assert.strictEqual(await expectPromise("accessibility.longClick", accessibility.longClick(accessibility.classNameMatches(/Button$/), { timeoutMs: 1043 })), true);
              assert.strictEqual(await expectPromise("accessibility.setText", accessibility.setText(accessibility.idMatches(".*:id/input"), "typed", { timeoutMs: 1044 })), true);
              assert.strictEqual(await expectPromise("accessibility.scrollForward", accessibility.scrollForward(accessibility.clickable(), { timeoutMs: 1045 })), true);
              assert.strictEqual(await expectPromise("accessibility.scrollBackward", accessibility.scrollBackward(accessibility.enabled(false), { timeoutMs: 1046 })), true);

              const capturer = await expectPromise("media_projection.requestScreenCapture", mediaProjection.requestScreenCapture({ timeoutMs: 1043 }));
              expectFrozen("media_projection.capturer", capturer);
              expectExports("media_projection.capturer", capturer, ["nextImage", "stop"]);
              const frame = await expectPromise("media_projection.nextImage", capturer.nextImage({ timeoutMs: 1044 }));
              await expectPromise("media_projection.stop", capturer.stop({ timeoutMs: 1045 }));
              const capture = await expectPromise("image.captureScreen", image.captureScreen({ timeoutMs: 1046 }));
              const template = await expectPromise("image.readImage", image.readImage("template.png", { timeoutMs: 1047 }));
              await expectPromise("image.saveImage", image.saveImage(capture, "out.png", { timeoutMs: 1048 }));
              const clipped = await expectPromise("image.clip", image.clip(capture, 0, 0, 5, 5, { timeoutMs: 1049 }));
              const resized = await expectPromise("image.resize", image.resize(clipped, 4, 4, { timeoutMs: 1050 }));
              const gray = await expectPromise("image.grayscale", image.grayscale(resized, { timeoutMs: 1051 }));
              const binary = await expectPromise("image.threshold", image.threshold(gray, { threshold: 128, timeoutMs: 1052 }));
              assert.deepStrictEqual(await expectPromise("image.findImage", image.findImage(binary, template, { timeoutMs: 1053 })), { x: 7, y: 8 });
              assert.strictEqual((await expectPromise("image.matchTemplate", image.matchTemplate(binary, template, { timeoutMs: 1054 })))[0].score, 0.9);
              assert.deepStrictEqual(await expectPromise("image.findColor", image.findColor(binary, "#ffffff", { timeoutMs: 1055 })), { x: 5, y: 6 });
              assert.deepStrictEqual(await expectPromise("image.findMultiColors", image.findMultiColors(binary, "#ffffff", [[1, 0, "#000000"]], { timeoutMs: 1056 })), { x: 9, y: 10 });
              assert.deepStrictEqual(image.getSize(binary), { width: 4, height: 4 });
              await expectPromise("image.recycle", image.recycle(template, { timeoutMs: 1057 }));
              assert.strictEqual((await expectPromise("images.resize", images.resize(frame, 3, 3, { timeoutMs: 1058 }))).__autojs6ImageHandle, "images-resize");
              const ocrResults = await expectPromise("ocr.recognize", ocr.recognize(capture, { timeoutMs: 1054 }));
              assert.strictEqual(ocrResults[0].text, "hello");
              assert.strictEqual(ocrResults[0].bounds.bottom, 4);
              assert.strictEqual(await expectPromise("ocr.recognizeText", ocr.recognizeText(capture, { timeoutMs: 1055 })), "hello");
              assert.strictEqual((await expectPromise("ocr.detectTextBounds", ocr.detectTextBounds(capture, { timeoutMs: 1056 })))[0].bounds.right, 3);
              const barcodeOne = await expectPromise("barcode.detect", barcode.detect(capture, { formats: ["QR_CODE"], timeoutMs: 1056 }));
              assert.strictEqual(barcodeOne.rawValue, "https://example.com");
              assert.strictEqual(barcodeOne.structured.url.url, "https://example.com");
              assert.strictEqual((await expectPromise("barcode.detectAll", barcode.detectAll(capture, { qrOnly: true, timeoutMs: 1056 })))[0].formatName, "QR_CODE");
              assert.strictEqual(await expectPromise("barcode.recognizeText", barcode.recognizeText(capture, { timeoutMs: 1056 })), "https://example.com");
              assert.strictEqual((await expectPromise("barcode.recognizeTexts", barcode.recognizeTexts(capture, { timeoutMs: 1056 }))).length, 2);
              assert.strictEqual((await expectPromise("compat.barcode.detect", compat.barcode.detect(capture, { timeoutMs: 1056 }))).rawValue, "https://example.com");
              assert.strictEqual((await expectPromise("compat.barcode.detectAll", compat.barcode.detectAll(capture, { format: "QR_CODE", timeoutMs: 1056 })))[0].formatName, "QR_CODE");
              assert.strictEqual(await expectPromise("compat.barcode.recognizeText", compat.barcode.recognizeText(capture, { timeoutMs: 1056 })), "https://example.com");
              assert.strictEqual((await expectPromise("compat.qrcode.recognizeTexts", compat.qrcode.recognizeTexts(capture, { timeoutMs: 1056 }))).length, 2);
              assert.strictEqual((await expectPromise("compat.qrcode.detectAll", compat.qrcode.detectAll(capture, { timeoutMs: 1056 })))[0].formatName, "QR_CODE");
              assert.strictEqual(await expectPromise("media.getAudioStreamVolume", media.getAudioStreamVolume("music", { timeoutMs: 1056 })), 3);
              assert.strictEqual(await expectPromise("media.getAudioStreamMaxVolume", media.getAudioStreamMaxVolume("music", { timeoutMs: 1056 })), 15);
              assert.strictEqual((await expectPromise("media.getAudioStreamInfo", media.getAudioStreamInfo("music", { timeoutMs: 1056 }))).schema, "autojs6-node-media-audio-stream-v1");
              assert.strictEqual((await expectPromise("compat.media.getAudioStreamInfo", compat.media.getAudioStreamInfo("music", { timeoutMs: 1056 }))).schema, "autojs6-node-media-audio-stream-v1");
              assert.strictEqual((await expectPromise("mediainfo.read", mediainfo.read("sample.mp3", { timeoutMs: 1056 }))).schema, "autojs6-node-mediainfo-snapshot-v1");
              assert.strictEqual((await expectPromise("mediainfo.capabilities", mediainfo.capabilities({ timeoutMs: 1056 }))).snapshotSchemas[1], "autojs6-plugin-mediainfo-snapshot-v2");
              assert.strictEqual((await expectPromise("mediainfo.call", mediainfo("sample.mp3", { timeoutMs: 1056 }))).schema, "autojs6-node-mediainfo-snapshot-v1");
              assert.strictEqual(await expectPromise("mediainfo.get", mediainfo.get("sample.mp3", "general", "Format", { timeoutMs: 1056 })), "MPEG Audio");
              assert.strictEqual((await expectPromise("compat.mediainfo.read", compat.mediainfo.read("sample.mp3", { timeoutMs: 1056 }))).schema, "autojs6-node-mediainfo-snapshot-v1");
              assert.strictEqual((await expectPromise("recorder.getStatus", recorder.getStatus({ timeoutMs: 1056 }))).schema, "autojs6-node-recorder-status-v1");
              assert.strictEqual((await expectPromise("compat.recorder.getStatus", compat.recorder.getStatus({ timeoutMs: 1056 }))).schema, "autojs6-node-recorder-status-v1");
              await expectRejectCode("recorder.start", recorder.start({ path: "record.m4a", timeoutMs: 1056 }), "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED");
              const store = storage.create("settings");
              expectFrozen("storage.store", store);
              expectExports("storage.store", store, ["get", "put", "remove", "clear", "keys", "contains"]);
              await expectPromise("storage.put", store.put("alpha", { ok: true }, { timeoutMs: 1057 }));
              assert.strictEqual((await expectPromise("storage.get", store.get("alpha", null, { timeoutMs: 1058 }))).ok, true);
              assert.deepStrictEqual(await expectPromise("storage.keys", store.keys({ timeoutMs: 1059 })), ["alpha"]);
              assert.strictEqual(await expectPromise("storage.contains", store.contains("alpha", { timeoutMs: 1060 })), true);
              await expectPromise("storage.remove", store.remove("alpha", { timeoutMs: 1061 }));
              await expectPromise("storage.clear", storages.open("settings").clear({ timeoutMs: 1062 }));
              const db = await expectPromise("database.open", database.open("conformance", { timeoutMs: 1062 }));
              expectFrozen("database.handle", db);
              expectExports("database.handle", db, ["exec", "run", "get", "all", "transaction", "close"]);
              await expectPromise("database.exec", db.exec("CREATE TABLE items(name TEXT, value INTEGER)", [], { timeoutMs: 1062 }));
              assert.strictEqual((await expectPromise("database.run", db.run("INSERT INTO items(name,value) VALUES(?,?)", ["alpha", 1], { timeoutMs: 1062 }))).rowsAffected, 1);
              assert.strictEqual((await expectPromise("database.get", db.get("SELECT name,value FROM items WHERE name=?", ["alpha"], { timeoutMs: 1062 }))).name, "alpha");
              assert.strictEqual((await expectPromise("database.all", db.all("SELECT name,value FROM items", [], { timeoutMs: 1062 })))[0].value, 1);
              assert.strictEqual((await expectPromise("database.transaction", db.transaction([{ type: "run", sql: "INSERT INTO items(name,value) VALUES(?,?)", params: ["beta", 2] }], { timeoutMs: 1062 })))[0].rowsAffected, 1);
              assert.strictEqual(await expectPromise("database.close", db.close({ timeoutMs: 1062 })), true);
              const sqliteDb = await expectPromise("sqlite.open", sqlite.open({ name: "alias" }, { timeoutMs: 1062 }));
              assert.strictEqual(sqliteDb.name, "alias");
              assert.strictEqual(await expectPromise("sqlite.close", sqliteDb.close({ timeoutMs: 1062 })), true);
              const notificationId = await expectPromise("notifications.notify", notifications.notify({ id: 9001, title: "Node", text: "running", channel: "conformance", ongoing: true, priority: "low" }, { timeoutMs: 1063 }));
              assert.strictEqual(notificationId, 9001);
              await expectPromise("notifications.cancel", notifications.cancel(notificationId, { timeoutMs: 1064 }));
              await expectPromise("notifications.cancelAll", notifications.cancelAll({ timeoutMs: 1065 }));
              const notificationStatus = await expectPromise("notifications.getPermissionStatus", notifications.getPermissionStatus({ timeoutMs: 1065 }));
              assert.strictEqual(notificationStatus.schema, "autojs6-node-notification-permission-status-v1");
              const notificationSettings = await expectPromise("notifications.openSettings", notifications.openSettings({ dryRun: true, timeoutMs: 1065 }));
              assert.strictEqual(notificationSettings.dryRun, true);
              const notificationAliasSettings = await expectPromise("notifications.openNotificationSettings", notifications.openNotificationSettings({ dryRun: true, timeoutMs: 1065 }));
              assert.strictEqual(notificationAliasSettings.schema, "autojs6-node-notification-permission-status-v1");
              assert.strictEqual((await expectPromise("sensors.getAvailableSensors", sensors.getAvailableSensors({ timeoutMs: 1066 })))[0].type, "accelerometer");
              assert.strictEqual((await expectPromise("sensors.once", sensors.once("accelerometer", { timeoutMs: 1067 }))).values[2], 3);
              let sensorCallbackCount = 0;
              let sensorCallbackResolve;
              const sensorCallbackReady = new Promise((resolve) => { sensorCallbackResolve = resolve; });
              const sensorSub = sensors.subscribe("light", (event) => {
                if (event.values[0] === 42) {
                  sensorCallbackCount += 1;
                  sensorCallbackResolve();
                }
              }, { timeoutMs: 1068, drainIntervalMs: 1 });
              expectFrozen("sensors.subscription", sensorSub);
              await expectPromise("sensors.subscribe.ready", sensorSub.ready);
              await sensorCallbackReady;
              await expectPromise("sensors.subscription.close", sensorSub.close({ timeoutMs: 1069 }));
              assert(sensorCallbackCount >= 1, "sensors subscription callback did not run");
              const uiHandle = await expectPromise("ui.showLayout", ui.showLayout({ type: "Vertical", children: [{ type: "Text", id: "title", text: "Node" }, { type: "Button", id: "ok", text: "OK" }] }, { timeoutMs: 1070, drainIntervalMs: 1 }));
              expectFrozen("ui.handle", uiHandle);
             expectExports("ui.handle", uiHandle, ["on", "update", "batchUpdate", "close"]);
              let uiClickCount = 0;
              let uiOff = function() {};
              const uiReady = new Promise((resolve) => {
                uiOff = uiHandle.on("click", (event) => {
                  if (event.id === "ok" && event.handleId === "ui-conformance") {
                    uiClickCount += 1;
                    uiOff();
                    resolve();
                  }
                }, { drainIntervalMs: 1, timeoutMs: 1071 });
              });
              await uiReady;
              await expectPromise("ui.update", uiHandle.update({ id: "title", text: "Updated" }, { timeoutMs: 1072 }));
              await expectPromise("ui.close", uiHandle.close({ timeoutMs: 1073 }));
              assert(uiClickCount >= 1, "ui click callback did not run");
              uiDrained = false;
              const compatUiHandle = await expectPromise("compat.ui.showLayout", compat.ui.showLayout({ type: "Column", children: [{ type: "Text", id: "compatTitle", text: "Compat" }, { type: "Button", id: "compatOk", text: "OK" }] }, { timeoutMs: 1074, drainIntervalMs: 1 }));
              expectFrozen("compat.ui.handle", compatUiHandle);
              expectExports("compat.ui.handle", compatUiHandle, ["on", "update", "batchUpdate", "close"]);
              let compatUiClickCount = 0;
              let compatUiOff = function() {};
              const compatUiReady = new Promise((resolve) => {
                compatUiOff = compatUiHandle.on("click", (event) => {
                  if (event.id === "ok" && event.handleId === "ui-conformance") {
                    compatUiClickCount += 1;
                    compatUiOff();
                    resolve();
                  }
                }, { drainIntervalMs: 1, timeoutMs: 1075 });
              });
              await compatUiReady;
              await expectPromise("compat.ui.update", compatUiHandle.update({ id: "compatTitle", text: "Compat Updated" }, { timeoutMs: 1076 }));
              await expectPromise("compat.ui.batchUpdate", compatUiHandle.batchUpdate([{ id: "compatOk", text: "Done", enabled: false }], { timeoutMs: 1077 }));
              await expectPromise("compat.ui.close", compatUiHandle.close({ timeoutMs: 1078 }));
              assert(compatUiClickCount >= 1, "compat.ui click callback did not run");
              assert.strictEqual(await expectPromise("work_manager.scheduleOnce", workManager.scheduleOnce({ scriptPath: "main.cjs", timeoutMs: 1055 })), "work-once");
              assert.strictEqual(await expectPromise("work_manager.schedulePeriodic", workManager.schedulePeriodic({ scriptPath: "main.cjs", intervalMs: workManager.policy.minPeriodicIntervalMs, timeoutMs: 1056 })), "work-periodic");
              await expectPromise("work_manager.cancel", workManager.cancel("work-once", { timeoutMs: 1057 }));
              assert.strictEqual((await expectPromise("work_manager.list", workManager.list({ timeoutMs: 1058 }))).length, 1);
              await expectRejectCode(
                "package_manager.install.confirmation",
                packageManager.install({ name: "@autojs6/conformance", version: "1.0.0", sourceDir: "node_modules/@autojs6/conformance", expectedIntegrity: "sha512-conformance" }),
                "ERR_AUTOJS6_PACKAGE_MANAGER_MUTATION_NOT_CONFIRMED"
              );
              await expectRejectCode("npm.cli", Promise.resolve().then(() => npm.cli(["install"])), "ERR_AUTOJS6_NPM_CLI_DENIED");
              await expectRejectCode("npm.installFromRegistry", Promise.resolve().then(() => npm.installFromRegistry("@autojs6/conformance")), "ERR_AUTOJS6_NPM_REGISTRY_DENIED");
              const beforeRemotePackagePlans = requests.length;
              const registryPlanResult = await expectPromise("package_manager.planRegistryInstall", packageManager.planRegistryInstall({ name: "@autojs6/conformance", tag: "latest" }));
              assert.strictEqual(registryPlanResult.schema, "autojs6-node-package-manager-remote-plan-v1");
              assert.strictEqual(registryPlanResult.status, "blocked_by_policy");
              assert.strictEqual(registryPlanResult.sourceAuthority, "raw_registry_denied");
              assert.strictEqual(registryPlanResult.grantsInstallAuthority, false);
              assert.strictEqual(registryPlanResult.networkAccess, false);
              assert.strictEqual(Object.isFrozen(registryPlanResult), true);
              assert.strictEqual(Object.isFrozen(registryPlanResult.request), true);
              assert.strictEqual(Object.isFrozen(registryPlanResult.resolution), true);
              const metadataRegistryPlanResult = await expectPromise("package_manager.planRegistryInstall.metadata", packageManager.planRegistryInstall({
                name: "@autojs6/conformance",
                tag: "latest",
                metadata: {
                  name: "@autojs6/conformance",
                  "dist-tags": {
                    latest: "1.0.0"
                  },
                  versions: {
                    "1.0.0": {
                      name: "@autojs6/conformance",
                      version: "1.0.0",
                      dist: {
                        tarball: "https://registry.npmjs.org/@autojs6/conformance/-/conformance-1.0.0.tgz",
                        integrity: "sha512-conformance",
                        shasum: "def456"
                      }
                    }
                  }
                }
              }));
              assert.strictEqual(metadataRegistryPlanResult.status, "blocked_by_policy");
              assert.strictEqual(metadataRegistryPlanResult.resolution.status, "resolved_from_dist_tag");
              assert.strictEqual(metadataRegistryPlanResult.resolution.resolvedVersion, "1.0.0");
              assert.strictEqual(metadataRegistryPlanResult.resolution.tarballUrl, "https://registry.npmjs.org/@autojs6/conformance/-/conformance-1.0.0.tgz");
              assert.strictEqual(metadataRegistryPlanResult.resolution.integrity, "sha512-conformance");
              assert.strictEqual(Object.isFrozen(metadataRegistryPlanResult.resolution), true);
              const rangeRegistryPlanResult = await expectPromise("package_manager.planRegistryInstall.range", packageManager.planRegistryInstall({
                name: "@autojs6/conformance-range",
                range: ">=1.0.0 <2.0.0",
                expectedIntegrity: "sha512-conformance-range",
                metadata: {
                  name: "@autojs6/conformance-range",
                  versions: {
                    "1.0.0": {
                      dist: {
                        tarball: "https://registry.npmjs.org/@autojs6/conformance-range/-/conformance-range-1.0.0.tgz",
                        integrity: "sha512-conformance-range-100"
                      }
                    },
                    "1.4.0": {
                      dist: {
                        tarball: "https://registry.npmjs.org/@autojs6/conformance-range/-/conformance-range-1.4.0.tgz",
                        integrity: "sha512-conformance-range"
                      }
                    },
                    "2.0.0": {
                      dist: {
                        tarball: "https://registry.npmjs.org/@autojs6/conformance-range/-/conformance-range-2.0.0.tgz",
                        integrity: "sha512-conformance-range-200"
                      }
                    }
                  }
                }
              }));
              assert.strictEqual(rangeRegistryPlanResult.status, "blocked_by_policy");
              assert.strictEqual(rangeRegistryPlanResult.resolution.status, "resolved_from_range");
              assert.strictEqual(rangeRegistryPlanResult.resolution.resolvedVersion, "1.4.0");
              assert.strictEqual(rangeRegistryPlanResult.resolution.integrityStatus, "expected_integrity_matched");
              assert.strictEqual(Object.isFrozen(rangeRegistryPlanResult.resolution), true);
              const npmTarballPlanResult = await expectPromise("npm.planTarballInstall", npm.planTarballInstall({ tarballUrl: "https://registry.npmjs.org/@autojs6/conformance/-/conformance-1.0.0.tgz", expectedIntegrity: "sha512-conformance" }));
              assert.strictEqual(npmTarballPlanResult.status, "blocked_by_policy");
              assert.strictEqual(npmTarballPlanResult.sourceAuthority, "remote_tarball_denied");
              assert.strictEqual(npmTarballPlanResult.networkAccess, false);
              assert.strictEqual(npmTarballPlanResult.resolution.status, "integrity_supplied");
              assert.strictEqual(npmTarballPlanResult.resolution.urlStatus, "remote_url");
              assert.strictEqual(npmTarballPlanResult.resolution.inferredName, "@autojs6/conformance");
              assert.strictEqual(npmTarballPlanResult.resolution.inferredVersion, "1.0.0");
              assert.strictEqual(npmTarballPlanResult.resolution.resolvedVersion, "1.0.0");
              assert.strictEqual(npmTarballPlanResult.resolution.integrityAlgorithm, "sha512");
              assert.strictEqual(npmTarballPlanResult.resolution.integrityAlgorithmAllowed, true);
              assert.strictEqual(npmTarballPlanResult.resolution.integrityStatus, "expected_integrity_sha512");
              assert.strictEqual(requests.length, beforeRemotePackagePlans, "remote package plan APIs must not dispatch bridge requests");
              const planInstallResult = await expectPromise("package_manager.planInstall", packageManager.planInstall({ name: "@autojs6/conformance", version: "1.0.0", sourceDir: "node_modules/@autojs6/conformance", timeoutMs: 1059 }));
              assert.strictEqual(planInstallResult.status, "ready");
              assert.strictEqual(planInstallResult.dryRun, true);
              assert.strictEqual(planInstallResult.grantsInstallAuthority, false);
              assert.strictEqual(Object.isFrozen(planInstallResult), true);
              assert.strictEqual(Object.isFrozen(planInstallResult.record), true);
              const planUpdateResult = await expectPromise("package_manager.planUpdate", packageManager.planUpdate({ name: "@autojs6/conformance", version: "1.0.0", sourceDir: "node_modules/@autojs6/conformance", timeoutMs: 1059 }));
              assert.strictEqual(planUpdateResult.operation, "update");
              assert.strictEqual(planUpdateResult.existingRecord.name, "@autojs6/conformance");
              const planRemoveResult = await expectPromise("package_manager.planRemove", packageManager.planRemove({ name: "@autojs6/conformance", version: "1.0.0", timeoutMs: 1059 }));
              assert.strictEqual(planRemoveResult.operation, "remove");
              assert.strictEqual(planRemoveResult.existingRecord.name, "@autojs6/conformance");
              const installResult = await expectPromise("package_manager.install", packageManager.install({ name: "@autojs6/conformance", version: "1.0.0", sourceDir: "node_modules/@autojs6/conformance", expectedIntegrity: planInstallResult.record.integrity, allowMutation: true, timeoutMs: 1059 }));
              assert.strictEqual(installResult.status, "installed");
              assert.strictEqual(Object.isFrozen(installResult.record), true);
              const updateResult = await expectPromise("package_manager.update", packageManager.update({ name: "@autojs6/conformance", version: "1.0.0", sourceDir: "node_modules/@autojs6/conformance", expectedIntegrity: planUpdateResult.record.integrity, allowMutation: true, timeoutMs: 1059 }));
              assert.strictEqual(updateResult.status, "updated");
              const removeResult = await expectPromise("package_manager.remove", packageManager.remove({ name: "@autojs6/conformance", version: "1.0.0", expectedIntegrity: planRemoveResult.existingRecord.integrity, allowMutation: true, timeoutMs: 1059 }));
              assert.strictEqual(removeResult.status, "removed");
              const packageList = await expectPromise("package_manager.list", packageManager.list({ timeoutMs: 1059 }));
              const npmPackageList = await expectPromise("npm.list", npm.list({ timeoutMs: 1059 }));
              assert.strictEqual(Object.isFrozen(npmPackageList), true);
              assert.strictEqual(Object.isFrozen(packageList), true);
              assert.strictEqual(packageList[0].name, "@autojs6/conformance");
              assert.strictEqual(Object.isFrozen(packageList[0]), true);
              const verifyResult = await expectPromise("package_manager.verify", packageManager.verify({ name: "@autojs6/conformance", version: "1.0.0", timeoutMs: 1060 }));
              assert.strictEqual(verifyResult.status, "verified");
              assert.strictEqual(verifyResult.record.name, "@autojs6/conformance");
              assert.strictEqual(Object.isFrozen(verifyResult), true);
              const pruneResult = await expectPromise("package_manager.prune", packageManager.prune({ maxTransactionAgeMs: 0, timeoutMs: 1061 }));
              assert.strictEqual(pruneResult.status, "pruned");
              const compatTaskId = await expectPromise("compat.tasks.addDisposableTask", compat.tasks.addDisposableTask({ path: "main.cjs", delayMs: 0, timeoutMs: 1059 }));
              assert.strictEqual(compatTaskId, "work-once");
              assert.strictEqual((await expectPromise("compat.tasks.queryTasks", compat.tasks.queryTasks({ timeoutMs: 1060 }))).length, 0);
              assert.strictEqual(await expectPromise("compat.tasks.removeTask", compat.tasks.removeTask(compatTaskId, { timeoutMs: 1061 })), true);
              assert.strictEqual((await expectPromise("compat.tasks.queryTimedTasks", compat.tasks.queryTimedTasks({ timeoutMs: 1062 }))).length, 0);
              assert.strictEqual(await expectPromise("compat.tasks.getTimedTask", compat.tasks.getTimedTask(compatTaskId, { timeoutMs: 1063 })), null);
              assert.strictEqual(await expectPromise("compat.tasks.removeTimedTask", compat.tasks.removeTimedTask(compatTaskId, { timeoutMs: 1064 })), true);
              const beforeTaskRejects = requests.length;
              await expectRejectCode("compat.tasks.addDailyTask", compat.tasks.addDailyTask({ path: "main.cjs", time: "08:30" }), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED");
              await expectRejectCode("compat.tasks.addWeeklyTask", compat.tasks.addWeeklyTask({ path: "main.cjs", time: "08:30", daysOfWeek: [1] }), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED");
              await expectRejectCode("compat.tasks.updateTask", compat.tasks.updateTask({ id: compatTaskId }), "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN");
              await expectRejectCode("compat.tasks.addIntentTask", compat.tasks.addIntentTask({ path: "main.cjs", action: "org.autojs6.TEST" }), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED");
              await expectRejectCode("compat.tasks.queryIntentTasks", compat.tasks.queryIntentTasks({ action: "org.autojs6.TEST" }), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED");
              await expectRejectCode("compat.tasks.getIntentTask", compat.tasks.getIntentTask("intent-1"), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED");
              await expectRejectCode("compat.tasks.removeIntentTask", compat.tasks.removeIntentTask("intent-1"), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED");
              assert.strictEqual(requests.length, beforeTaskRejects, "compat.tasks rejected stubs must not dispatch bridge requests");

              assert.strictEqual(bridge.__test.pendingCount(), 0);
              const trace = requests.map((request) => request.module + "." + request.method);
              [
                "toast.showToast",
                "app.startActivity",
                "dialogs.input",
                "clipboard.hasText",
                "device.vibrate",
                "device.isIgnoringBatteryOptimizations",
                "device.openBatteryOptimizationSettings",
                "shell.exec",
                "engines.execScriptFile",
                "accessibility.findAll",
                "accessibility.setText",
                "media_projection.nextImage",
                "image.findImage",
                "image.matchTemplate",
                "image.findColor",
                "images.resize",
                "ocr.recognize",
                "ocr.recognizeText",
                "ocr.detectTextBounds",
                "barcode.detect",
                "barcode.detectAll",
                "barcode.recognizeText",
                "barcode.recognizeTexts",
                "media.getAudioStreamVolume",
                "media.getAudioStreamMaxVolume",
                "media.getAudioStreamInfo",
                "mediainfo.read",
                "mediainfo.capabilities",
                "mediainfo.get",
                "recorder.getStatus",
                "recorder.start",
                "storage.put",
                "storages.clear",
                "database.open",
                "database.transaction",
                "sqlite.open",
                "notifications.notify",
                "notifications.cancelAll",
                "notifications.getPermissionStatus",
                "notifications.openSettings",
                "sensors.once",
                "sensors.unsubscribe",
                "ui.showLayout",
                "ui.drainEvents",
                "ui.update",
                "ui.batchUpdate",
                "ui.close",
                "work_manager.scheduleOnce",
                "work_manager.cancel",
                "work_manager.list",
                "package_manager.planInstall",
                "package_manager.planUpdate",
                "package_manager.planRemove",
                "package_manager.install",
                "package_manager.update",
                "package_manager.remove",
                "package_manager.list",
                "package_manager.verify",
                "package_manager.prune"
              ].forEach((entry) => assert(trace.indexOf(entry) >= 0, "missing request " + entry));
              console.log("bridge.conformance.request_count=" + requests.length);
              console.log("bridge.conformance.pending=" + bridge.__test.pendingCount());
              console.log("bridge.conformance=PASS");
            })().catch((error) => {
              console.log("bridge.conformance.error=" + (error && error.stack ? error.stack : String(error)));
              process.exitCode = 1;
            });
            """.trimIndent(),
            "<embedded-autojs-bridge-conformance.js>",
            workingDirectory = workDir,
            sandboxRoot = workDir,
            timeoutMs = 30_000L,
        )

        NodePluginTestHarness.assertSucceeded("bridge API conformance", result)
        assertTrue(result.stdout, result.stdout.contains("bridge.conformance=PASS"))
        assertTrue(result.stdout, result.stdout.contains("bridge.conformance.pending=0"))
    }

    private fun writeBridgePermissionManifest(workDir: File) {
        val permissions = stableDispatchMethods
            .flatMap { (module, methods) ->
                methods.flatMap { method -> NodeBridgePermissionManifest.requiredCapabilitiesFor(module, method) }
            }
            .distinct()
        File(workDir, "project.json").writeText("""{"node":{"permissions":${JSONArray(permissions)}}}""")
    }

    @Test
    fun jsBridgeConformanceErrorCapabilityAndDestroyCleanupAreStable() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node bridge conformance error semantics")

        val fullManifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(
            listOf(
                "toast",
                "dialogs",
                "accessibility",
            ),
        )
        val errorResult = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const assert = require("assert");
              const bridge = require("autojs6:bridge");
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  if (request.method === "isEnabled") {
                    return;
                  }
                  setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                    id: request.id,
                    ok: false,
                    error: {
                      name: "Error",
                      message: "denied by fake provider",
                      code: "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED",
                      module: request.module,
                      method: request.method
                    }
                  })));
                }
              });

              let providerErrorCode = "";
              let providerErrorModule = "";
              let providerErrorMethod = "";
              try {
                await require("dialogs").alert("denied", { timeoutMs: 1000 });
              } catch (error) {
                providerErrorCode = String(error && (error.autojs6Code || error.code || ""));
                providerErrorModule = String(error && error.module || "");
                providerErrorMethod = String(error && error.method || "");
              }

              const pending = require("accessibility").isEnabled({ timeoutMs: 5000 });
              assert.strictEqual(bridge.__test.pendingCount(), 1);
              bridge.__test.destroy("service destroyed");
              let destroyCode = "";
              let destroyModule = "";
              let destroyMethod = "";
              try {
                await pending;
              } catch (error) {
                destroyCode = String(error && (error.autojs6Code || error.code || ""));
                destroyModule = String(error && error.module || "");
                destroyMethod = String(error && error.method || "");
              }

              console.log("bridge.conformance.error=" + [providerErrorCode, providerErrorModule, providerErrorMethod].join("|"));
              console.log("bridge.conformance.destroy=" + [destroyCode, destroyModule, destroyMethod, bridge.__test.pendingCount()].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-bridge-conformance-errors.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to fullManifest.toJson()),
        )

        NodePluginTestHarness.assertSucceeded("bridge API conformance errors", errorResult)
        assertTrue(
            errorResult.stdout.contains(
                "bridge.conformance.error=${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED}|dialogs|alert",
            ),
        )
        assertTrue(
            errorResult.stdout.contains(
                "bridge.conformance.destroy=${NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD}|accessibility|isEnabled|0",
            ),
        )

        NodePluginTestHarness.settlePluginRuntime()

        val restrictedManifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("toast"))
        val capabilityResult = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  bridge.__test.receiveMessage(JSON.stringify({
                    id: request.id,
                    ok: false,
                    error: {
                      name: "Error",
                      message: "capability guard did not reject before dispatch",
                      code: "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED",
                      module: request.module,
                      method: request.method
                    }
                  }));
                }
              });
              let deniedCode = "";
              let deniedModule = "";
              let deniedMethod = "";
              let missing = "";
              const shellAccess = require("shell").checkAccess({ shizuku: true });
              const shizukuMissing = Array.isArray(shellAccess.missingCapabilities) ? shellAccess.missingCapabilities.join("|") : "";
              try {
                await require("dialogs").alert("blocked", { timeoutMs: 1000 });
              } catch (error) {
                deniedCode = String(error && (error.autojs6Code || error.code || ""));
                deniedModule = String(error && error.module || "");
                deniedMethod = String(error && error.method || "");
                missing = Array.isArray(error && error.missingCapabilities) ? error.missingCapabilities.join("|") : "";
              }
              console.log("bridge.conformance.capability=" + [deniedCode, deniedModule, deniedMethod, missing, requests.length, bridge.__test.pendingCount()].join("|"));
              console.log("bridge.conformance.shell_shizuku_missing=" + shizukuMissing);
            })();
            """.trimIndent(),
            "<embedded-autojs-bridge-conformance-capabilities.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to restrictedManifest.toJson()),
        )

        NodePluginTestHarness.assertSucceeded("bridge API conformance capabilities", capabilityResult)
        assertTrue(
            capabilityResult.stdout.contains(
                "bridge.conformance.capability=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|dialogs|alert|dialogs|0|0",
            ),
        )
        assertTrue(
            capabilityResult.stdout,
            capabilityResult.stdout.contains("bridge.conformance.shell_shizuku_missing=shell|shell.shizuku"),
        )
    }

    private companion object {
        private val stableDispatchMethods: Map<String, Set<String>> = linkedMapOf(
            NodeBridgeModules.EVENTS to setOf("observeNotification", "observeToast", "observeKey", "observeBroadcasts", "drainEvents", "close"),
            NodeBridgeModules.KEYS to setOf("notifications", "quickSettings", "lockScreen", "takeScreenshot", "splitScreen", "powerDialog"),
            NodeBridgeModules.TOAST to setOf("showToast", "toast"),
            NodeBridgeModules.APP to setOf("listSamples", "readSample", "listDocs", "readDoc",
                "launchPackage",
                "launchApp",
                "openAppSetting",
                "startActivity",
                "getPackageName",
                "getAppName",
                "isInstalled",
                "viewFile",
                "editFile",
                "currentWindow",
                "startService", "sendBroadcast", "getInstalledApps", "getPackageInfo",
            ),
            NodeBridgeModules.DIALOGS to setOf("alert", "confirm", "input", "select", "rawInput", "singleChoice", "multiChoice", "progressShow", "progressUpdate", "progressDismiss"),
            NodeBridgeModules.CLIPBOARD to setOf("getText", "setText", "hasText"),
            NodeBridgeModules.DEVICE to setOf("info", "isScreenOn", "wakeUp", "vibrate", "isIgnoringBatteryOptimizations", "openBatteryOptimizationSettings",
                "setBrightness", "setBrightnessMode", "keepScreenOn", "cancelKeepingAwake"),
            NodeBridgeModules.SHELL to setOf("exec"),
            NodeBridgeModules.ENGINES to setOf("list", "stop", "myEngine", "all", "stopAll", "stopSelf", "execScript", "execScriptFile"),
            NodeBridgeModules.ACCESSIBILITY to setOf(
                "isEnabled",
                "ensureEnabled",
                "click",
                "back",
                "home",
                "recentApps",
                "findByText",
                "clickText",
                "findOne",
                "findAll",
                "longClick",
                "setText",
                "scrollForward",
                "scrollBackward",
                "swipe",
                "gesture",
                "dump",
                "explain",
                "screenshot",
            ),
            NodeBridgeModules.MEDIA_PROJECTION to setOf("requestScreenCapture", "nextImage", "stop"),
            NodeBridgeModules.IMAGE to setOf("captureScreen", "toBytes", "readImage", "saveImage", "clip", "resize", "grayscale", "threshold", "findImage", "matchTemplate", "findColor", "findMultiColors", "recycle"),
            NodeBridgeModules.IMAGES to setOf("captureScreen", "toBytes", "readImage", "saveImage", "clip", "resize", "grayscale", "threshold", "findImage", "matchTemplate", "findColor", "findMultiColors", "recycle"),
            NodeBridgeModules.OCR to setOf("recognize", "recognizeText", "detectTextBounds"),
            NodeBridgeModules.BARCODE to setOf("detect", "detectAll", "recognizeText", "recognizeTexts"),
            NodeBridgeModules.MEDIA to setOf("getAudioStreamVolume", "getAudioStreamMaxVolume", "getAudioStreamInfo", "setAudioStreamVolume", "play", "pause", "resume", "stop", "seekTo", "getPlaybackStatus"),
            NodeBridgeModules.MEDIAINFO to setOf("read", "get", "countGet", "capabilities"),
            NodeBridgeModules.MEDIA_STORE to setOf("capabilities", "query", "get", "insert", "update", "delete", "scanFile", "exportFile"),
            NodeBridgeModules.RECORDER to setOf("getStatus", "start", "stop"),
            NodeBridgeModules.PINYIN to setOf("convert", "simple", "compare", "compact", "fromCodePoint", "fromPhrase"),
            NodeBridgeModules.PINYIN4J to setOf("of", "as", "readings"),
            NodeBridgeModules.STORAGE to setOf("get", "put", "remove", "clear", "keys", "contains"),
            NodeBridgeModules.STORAGES to setOf("get", "put", "remove", "clear", "keys", "contains"),
            NodeBridgeModules.DATABASE to setOf("open", "exec", "run", "get", "all", "transaction", "close"),
            NodeBridgeModules.SQLITE to setOf("open", "exec", "run", "get", "all", "transaction", "close"),
            NodeBridgeModules.NOTIFICATIONS to setOf("notify", "cancel", "cancelAll", "getPermissionStatus", "openSettings"),
            NodeBridgeModules.SENSORS to setOf("getAvailableSensors", "once", "subscribe", "unsubscribe", "drainEvents"),
            NodeBridgeModules.UI to setOf("showLayout", "update", "batchUpdate", "close", "drainEvents"),
            NodeBridgeModules.INPUT_OBSERVER to setOf("observeKeys", "drainEvents", "close", "getAvailableSources"),
            NodeBridgeModules.WORK_MANAGER to setOf("scheduleOnce", "schedulePeriodic", "cancel", "list"),
            NodeBridgeModules.PACKAGE_MANAGER to setOf("list", "listApps", "verify", "prune", "planInstall", "planUpdate", "planRemove", "install", "update", "remove"),
            NodeBridgeModules.FILES to setOf("list", "stat", "read", "write", "mkdir", "rename", "delete"),
            NodeBridgeModules.CONSOLE to setOf("tail"),
        )
    }
}
