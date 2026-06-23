package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeBridgeConformanceInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
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
    fun jsStableBridgeModuleExportsAndPromiseContractsAreStable() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node bridge conformance suite")

        val workDir = File(context.cacheDir, "node-bridge-conformance").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge conformance working directory: $absolutePath" }
        }
        File(workDir, "main.cjs").writeText("""console.log("scheduled");""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
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
                    result = method === "launchPackage" || method === "launchApp" ? true : null;
                  } else if (moduleName === "dialogs") {
                    result = method === "confirm" ? true : (method === "input" ? "typed" : (method === "select" ? 1 : null));
                  } else if (moduleName === "clipboard") {
                    result = method === "getText" ? "clip" : (method === "hasText" ? true : null);
                  } else if (moduleName === "device") {
                    result = method === "isScreenOn" ? true : null;
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
                    result = method === "notify" ? (request.args[0].id || 9001) : null;
                  } else if (moduleName === "sensors") {
                    if (method === "getAvailableSensors") {
                      result = [{ type: "accelerometer", name: "fake accelerometer", vendor: "AutoJs6", version: 1 }];
                    } else if (method === "once") {
                      result = { type: request.args[0].type, timestamp: 1, accuracy: 3, values: [1, 2, 3] };
                    } else if (method === "subscribe") {
                      result = { id: "sensor-conformance", type: request.args[0].type, samplingIntervalMs: request.args[0].samplingIntervalMs, maxQueueSize: request.args[0].maxQueueSize };
                    } else if (method === "drainEvents") {
                      result = [{ type: "event", event: { type: "light", timestamp: 2, accuracy: 3, values: [42] } }];
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
              const storage = require("storage");
              const storages = require("storages");
              const database = require("database");
              const sqlite = require("sqlite");
              const notifications = require("notifications");
              const sensors = require("sensors");
              const ui = require("ui");
              const workManager = require("work_manager");
              const compat = require("autojs6:compat");

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
                ["storage", storage],
                ["storages", storages],
                ["database", database],
                ["sqlite", sqlite],
                ["notifications", notifications],
                ["sensors", sensors],
                ["ui", ui],
                ["work_manager", workManager],
                ["autojs6:compat", compat],
                ["autojs6:compat.ui", compat.ui],
                ["autojs6:compat.tasks", compat.tasks]
              ].forEach(([name, moduleValue]) => expectFrozen(name, moduleValue));

              expectExports("toast", toast, ["showToast", "toast"]);
              expectExports("app", app, ["launchPackage", "launchApp", "openAppSetting", "startActivity"]);
              expectExports("dialogs", dialogs, ["alert", "confirm", "input", "prompt", "select"]);
              expectExports("clipboard", clipboard, ["getText", "setText", "hasText"]);
              expectExports("device", device, ["isScreenOn", "wakeUp", "vibrate"]);
              expectExports("shell", shell, ["exec", "execFile", "checkAccess", "setDefaultTimeout"]);
              expectExports("engines", engines, ["myEngine", "all", "stopAll", "stopSelf", "execScript", "execScriptFile"]);
              expectExports("accessibility", accessibility, ["isEnabled", "ensureEnabled", "click", "back", "home", "recentApps", "findByText", "clickText", "findOne", "findAll", "longClick", "setText", "scrollForward", "scrollBackward", "text", "desc", "id", "className", "textContains", "descContains", "textMatches", "descMatches", "idMatches", "classNameMatches", "clickable", "enabled", "depth", "boundsInside", "boundsContains"]);
              expectExports("media_projection", mediaProjection, ["requestScreenCapture"]);
              expectExports("image", image, ["captureScreen", "readImage", "saveImage", "clip", "resize", "grayscale", "threshold", "findImage", "matchTemplate", "findColor", "findMultiColors", "getSize", "recycle"]);
              expectExports("images", images, ["captureScreen", "readImage", "saveImage", "clip", "resize", "grayscale", "threshold", "findImage", "matchTemplate", "findColor", "findMultiColors", "getSize", "recycle"]);
              expectExports("ocr", ocr, ["recognize", "recognizeText", "detectTextBounds"]);
              expectExports("storage", storage, ["create", "open"]);
              expectExports("storages", storages, ["create", "open"]);
              expectExports("database", database, ["open"]);
              expectExports("sqlite", sqlite, ["open"]);
              expectExports("notifications", notifications, ["notify", "cancel", "cancelAll"]);
              expectExports("sensors", sensors, ["getAvailableSensors", "once", "subscribe"]);
              expectExports("ui", ui, ["showLayout", "state", "bind"]);
              expectExports("work_manager", workManager, ["scheduleOnce", "schedulePeriodic", "cancel", "list"]);
              expectExports("autojs6:compat.ui", compat.ui, ["showLayout", "state", "bind"]);
              expectExports("autojs6:compat.tasks", compat.tasks, ["addDisposableTask", "removeTask", "queryTasks"]);
              assert.strictEqual(globalThis.ui, undefined, "compat.ui must not install a default global");
              assert.strictEqual(globalThis.tasks, undefined, "compat.tasks must not install a default global");
              assert.strictEqual(typeof compat.ui.run, "undefined");
              assert.strictEqual(typeof compat.ui.post, "undefined");
              assert.strictEqual(typeof compat.ui.findById, "undefined");
              const compatInstall = compat.install({ globals: true });
              assert(compatInstall.globals.indexOf("ui") >= 0, "compat.install({ globals: true }) must install constrained ui global");
              assert.strictEqual(globalThis.ui, compat.ui);
              assert.strictEqual(globalThis.tasks, undefined, "compat.install({ globals: true }) must not install tasks global");
              assert.strictEqual(typeof globalThis.ui.layout, "undefined");

              assert.strictEqual(typeof app.packageName, "string");
              assert.strictEqual(typeof app.versionName, "string");
              assert.strictEqual(typeof app.versionCode, "number");
              assert.strictEqual(typeof device.sdkInt, "number");
              assert.strictEqual(typeof device.width, "number");
              assert.strictEqual(typeof device.height, "number");
              assert.strictEqual(typeof device.density, "number");
              assert.strictEqual(typeof workManager.policy.maxTasksPerProvider, "number");
              assert.strictEqual(engines.myEngine().engineName, "nodejs");

              await expectPromise("toast.showToast", toast.showToast("hello", { timeoutMs: 1010 }));
              await expectPromise("toast.toast", toast.toast("alias", { timeoutMs: 1011 }));
              await expectPromise("app.launchPackage", app.launchPackage("org.autojs.autojs6", { timeoutMs: 1012 }));
              await expectPromise("app.launchApp", app.launchApp("AutoJs6", { timeoutMs: 1013 }));
              await expectPromise("app.openAppSetting", app.openAppSetting("org.autojs.autojs6", { timeoutMs: 1014 }));
              await expectPromise("app.startActivity", app.startActivity({ action: "android.intent.action.VIEW", data: "https://example.com" }, { timeoutMs: 1015 }));
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
              assert.strictEqual((await expectPromise("shell.exec", shell.exec("echo ok", { timeoutMs: 1027 }))).code, 0);
              assert.strictEqual(shell.setDefaultTimeout(2000), 2000);
              assert.strictEqual(shell.checkAccess({ shizuku: true }).shizuku, false);
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
              const compatTaskId = await expectPromise("compat.tasks.addDisposableTask", compat.tasks.addDisposableTask({ path: "main.cjs", delayMs: 0, timeoutMs: 1059 }));
              assert.strictEqual(compatTaskId, "work-once");
              assert.strictEqual((await expectPromise("compat.tasks.queryTasks", compat.tasks.queryTasks({ timeoutMs: 1060 }))).length, 0);
              assert.strictEqual(await expectPromise("compat.tasks.removeTask", compat.tasks.removeTask(compatTaskId, { timeoutMs: 1061 })), true);

              assert.strictEqual(bridge.__test.pendingCount(), 0);
              const trace = requests.map((request) => request.module + "." + request.method);
              [
                "toast.showToast",
                "app.startActivity",
                "dialogs.input",
                "clipboard.hasText",
                "device.vibrate",
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
                "storage.put",
                "storages.clear",
                "database.open",
                "database.transaction",
                "sqlite.open",
                "notifications.notify",
                "notifications.cancelAll",
                "sensors.once",
                "sensors.unsubscribe",
                "ui.showLayout",
                "ui.drainEvents",
                "ui.update",
                "ui.batchUpdate",
                "ui.close",
                "work_manager.scheduleOnce",
                "work_manager.cancel",
                "work_manager.list"
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
            timeoutMs = 30_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("bridge API conformance", result)
        assertTrue(result.stdout, result.stdout.contains("bridge.conformance=PASS"))
        assertTrue(result.stdout, result.stdout.contains("bridge.conformance.pending=0"))
    }

    @Test
    fun jsBridgeConformanceErrorCapabilityAndDestroyCleanupAreStable() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node bridge conformance error semantics")

        val fullManifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(
            listOf(
                "toast",
                "dialogs",
                "accessibility",
            ),
        )
        val errorResult = NodeEmbeddedTestHarness.runEmbeddedScript(
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

        NodeEmbeddedTestHarness.assertSucceeded("bridge API conformance errors", errorResult)
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

        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

        val restrictedManifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("toast"))
        val capabilityResult = NodeEmbeddedTestHarness.runEmbeddedScript(
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
              try {
                await require("dialogs").alert("blocked", { timeoutMs: 1000 });
              } catch (error) {
                deniedCode = String(error && (error.autojs6Code || error.code || ""));
                deniedModule = String(error && error.module || "");
                deniedMethod = String(error && error.method || "");
                missing = Array.isArray(error && error.missingCapabilities) ? error.missingCapabilities.join("|") : "";
              }
              console.log("bridge.conformance.capability=" + [deniedCode, deniedModule, deniedMethod, missing, requests.length, bridge.__test.pendingCount()].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-bridge-conformance-capabilities.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to restrictedManifest.toJson()),
        )

        NodeEmbeddedTestHarness.assertSucceeded("bridge API conformance capabilities", capabilityResult)
        assertTrue(
            capabilityResult.stdout.contains(
                "bridge.conformance.capability=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|dialogs|alert|dialogs|0|0",
            ),
        )
    }

    private companion object {
        private val stableDispatchMethods: Map<String, Set<String>> = linkedMapOf(
            NodeBridgeModules.TOAST to setOf("showToast", "toast"),
            NodeBridgeModules.APP to setOf("launchPackage", "launchApp", "openAppSetting", "startActivity"),
            NodeBridgeModules.DIALOGS to setOf("alert", "confirm", "input", "select"),
            NodeBridgeModules.CLIPBOARD to setOf("getText", "setText", "hasText"),
            NodeBridgeModules.DEVICE to setOf("isScreenOn", "wakeUp", "vibrate"),
            NodeBridgeModules.SHELL to setOf("exec"),
            NodeBridgeModules.ENGINES to setOf("myEngine", "all", "stopAll", "stopSelf", "execScript", "execScriptFile"),
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
            ),
            NodeBridgeModules.MEDIA_PROJECTION to setOf("requestScreenCapture", "nextImage", "stop"),
            NodeBridgeModules.IMAGE to setOf("captureScreen", "readImage", "saveImage", "clip", "resize", "grayscale", "threshold", "findImage", "matchTemplate", "findColor", "findMultiColors", "recycle"),
            NodeBridgeModules.IMAGES to setOf("captureScreen", "readImage", "saveImage", "clip", "resize", "grayscale", "threshold", "findImage", "matchTemplate", "findColor", "findMultiColors", "recycle"),
            NodeBridgeModules.OCR to setOf("recognize", "recognizeText", "detectTextBounds"),
            NodeBridgeModules.STORAGE to setOf("get", "put", "remove", "clear", "keys", "contains"),
            NodeBridgeModules.STORAGES to setOf("get", "put", "remove", "clear", "keys", "contains"),
            NodeBridgeModules.DATABASE to setOf("open", "exec", "run", "get", "all", "transaction", "close"),
            NodeBridgeModules.SQLITE to setOf("open", "exec", "run", "get", "all", "transaction", "close"),
            NodeBridgeModules.NOTIFICATIONS to setOf("notify", "cancel", "cancelAll"),
            NodeBridgeModules.SENSORS to setOf("getAvailableSensors", "once", "subscribe", "unsubscribe", "drainEvents"),
            NodeBridgeModules.UI to setOf("showLayout", "update", "batchUpdate", "close", "drainEvents"),
            NodeBridgeModules.INPUT_OBSERVER to setOf("observeKeys", "drainEvents", "close", "getAvailableSources"),
            NodeBridgeModules.WORK_MANAGER to setOf("scheduleOnce", "schedulePeriodic", "cancel", "list"),
        )
    }
}
