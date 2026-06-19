package org.autojs.autojs.engine

import androidx.test.runner.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NodeCoreBuiltinCompatibilityInstrumentationTest {

    private val context
        get() = NodeEmbeddedTestHarness.targetContext

    @Before
    fun setUp() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun tearDown() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun lowRiskCoreBuiltinsExposeAliasesShadowsAndDenials() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("core builtin compatibility")
        val workDir = freshWorkDir("node-core-builtins-cjs")
        writeShadow(workDir, "constants/index.js")
        writeShadow(workDir, "util/types.js")
        writeShadow(workDir, "events/index.js")
        writeShadow(workDir, "diagnostics_channel/index.js")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                (async () => {
                  const fs = require("fs");
                  const { createRequire } = require("module");
                  const constants = require("constants");
                  const nodeConstants = require("node:constants");
                  const utilTypes = require("util/types");
                  const nodeUtilTypes = require("node:util/types");
                  const events = require("events");
                  const nodeEvents = require("node:events");
                  const diagnostics = require("diagnostics_channel");
                  const nodeDiagnostics = require("node:diagnostics_channel");
                  const req = createRequire(__filename);

                  function denied(action) {
                    try {
                      action();
                      return "allowed";
                    } catch (error) {
                      return [
                        error.code,
                        error.autojs6Code,
                        String(error.message).includes("process.getBuiltinModule")
                      ].join("/");
                    }
                  }

                  function requireDenied(name) {
                    try {
                      require(name);
                      return name + ":allowed";
                    } catch (error) {
                      return [
                        name,
                        error.code,
                        error.autojs6Code,
                        String(error.message).includes(name.replace(/^node:/, ""))
                      ].join(":");
                    }
                  }

                  const emitter = new events.EventEmitter();
                  let seen = 0;
                  emitter.on("inc", (value) => {
                    seen += value;
                  });
                  emitter.emit("inc", 1);
                  const onceValuePromise = events.once(emitter, "ready").then((args) => args[0]);
                  process.nextTick(() => {
                    emitter.emit("ready", "ok");
                  });
                  const onceValue = await onceValuePromise;

                  const channel = diagnostics.channel("autojs6.core.builtin");
                  const nodeChannel = nodeDiagnostics.channel("autojs6.core.builtin");
                  const published = [];
                  function listener(message, name) {
                    published.push(name + ":" + message.value);
                  }
                  channel.subscribe(listener);
                  const hasBefore = channel.hasSubscribers && diagnostics.hasSubscribers("autojs6.core.builtin");
                  channel.publish({ value: "one" });
                  channel.unsubscribe(listener);
                  const hasAfter = channel.hasSubscribers || diagnostics.hasSubscribers("autojs6.core.builtin");

                  const shadowLoaded = Object.keys(require.cache).some((filename) => {
                    const normalized = filename.replace(/\\/g, "/");
                    return normalized.includes("/node_modules/constants/") ||
                      normalized.includes("/node_modules/util/") ||
                      normalized.includes("/node_modules/events/") ||
                      normalized.includes("/node_modules/diagnostics_channel/");
                  });
                  const resolveSummary = [
                    require.resolve("constants"),
                    require.resolve("node:constants"),
                    require.resolve("util/types"),
                    require.resolve("node:util/types"),
                    require.resolve("events"),
                    require.resolve("node:events"),
                    require.resolve("diagnostics_channel"),
                    require.resolve("node:diagnostics_channel")
                  ].join("|");
                  const aliases =
                    constants === nodeConstants &&
                    utilTypes === nodeUtilTypes &&
                    events === nodeEvents &&
                    diagnostics === nodeDiagnostics &&
                    req("constants") === constants &&
                    req("node:util/types") === utilTypes &&
                    req("events") === events &&
                    req("node:diagnostics_channel") === diagnostics;
                  const constantsOk =
                    Object.isFrozen(constants) &&
                    Object.isFrozen(constants.fs) &&
                    Object.isFrozen(constants.os) &&
                    constants.fs.F_OK === fs.constants.F_OK &&
                    constants.F_OK === fs.constants.F_OK &&
                    typeof constants.SIGINT === "number" &&
                    typeof constants.EACCES === "number";
                  const utilTypesOk =
                    Object.isFrozen(utilTypes) &&
                    utilTypes.isDate(new Date()) &&
                    utilTypes.isRegExp(/safe/) &&
                    utilTypes.isPromise(Promise.resolve()) &&
                    utilTypes.isArrayBuffer(new ArrayBuffer(1)) &&
                    utilTypes.isUint8Array(new Uint8Array(1));
                  const eventsOk =
                    Object.isFrozen(events) &&
                    events === events.EventEmitter &&
                    seen === 1 &&
                    onceValue === "ok" &&
                    emitter.listenerCount("ready") === 0 &&
                    typeof events.once === "function" &&
                    typeof events.on === "function" &&
                    typeof events.EventEmitterAsyncResource === "undefined" &&
                    typeof events.EventTarget === "undefined" &&
                    typeof globalThis.EventTarget === "function";
                  const diagnosticsOk =
                    Object.isFrozen(diagnostics) &&
                    channel === nodeChannel &&
                    Object.isFrozen(channel) &&
                    published.join("|") === "autojs6.core.builtin:one" &&
                    hasBefore === true &&
                    hasAfter === false &&
                    typeof diagnostics.Channel === "undefined" &&
                    typeof channel._subscribers === "undefined";

                  console.log("core.builtin.aliases=" + aliases);
                  console.log("core.builtin.resolve=" + resolveSummary);
                  console.log("core.builtin.shadow=" + (
                    constants.shadow !== true &&
                    utilTypes.shadow !== true &&
                    events.shadow !== true &&
                    diagnostics.shadow !== true &&
                    !shadowLoaded
                  ));
                  console.log("core.builtin.constants=" + constantsOk);
                  console.log("core.builtin.util_types=" + utilTypesOk);
                  console.log("core.builtin.events=" + eventsOk);
                  console.log("core.builtin.diagnostics=" + diagnosticsOk);
                  console.log("core.builtin.getBuiltin=" + [
                    "constants",
                    "util/types",
                    "events",
                    "diagnostics_channel"
                  ].map((name) => name + ":" + denied(() => process.getBuiltinModule(name))).join("|"));
                  console.log("core.builtin.disabled=" + [
                    requireDenied("child_process"),
                    requireDenied("node:vm"),
                    requireDenied("dgram")
                  ].join("|"));
                })().catch((error) => {
                  console.error("core.builtin.error=" + (error && (error.stack || error.message) || error));
                  process.exitCode = 1;
                });
            """.trimIndent(),
            sourceName = File(workDir, "main.js").absolutePath,
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("core builtin CJS compatibility", result)
        assertTrue(result.stdout.contains("core.builtin.aliases=true"))
        assertTrue(result.stdout.contains("core.builtin.resolve=constants|constants|util/types|util/types|events|events|diagnostics_channel|diagnostics_channel"))
        assertTrue(result.stdout.contains("core.builtin.shadow=true"))
        assertTrue(result.stdout.contains("core.builtin.constants=true"))
        assertTrue(result.stdout.contains("core.builtin.util_types=true"))
        assertTrue(result.stdout.contains("core.builtin.events=true"))
        assertTrue(result.stdout.contains("core.builtin.diagnostics=true"))
        assertTrue(result.stdout.contains("constants:${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.PROCESS_API_DISABLED}/true"))
        assertTrue(result.stdout.contains("util/types:${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.PROCESS_API_DISABLED}/true"))
        assertTrue(result.stdout.contains("events:${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.PROCESS_API_DISABLED}/true"))
        assertTrue(result.stdout.contains("diagnostics_channel:${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.PROCESS_API_DISABLED}/true"))
        assertTrue(result.stdout.contains("child_process:${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}:${NodeRuntimeErrorCodes.BUILTIN_DISABLED}:true"))
        assertTrue(result.stdout.contains("node:vm:${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}:${NodeRuntimeErrorCodes.BUILTIN_DISABLED}:true"))
        assertTrue(result.stdout.contains("dgram:${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}:${NodeRuntimeErrorCodes.BUILTIN_DISABLED}:true"))
    }

    @Test
    fun asyncLocalStoragePropagatesAcrossSafeAsyncBoundariesAndCleansUp() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("async_hooks context propagation")
        val workDir = freshWorkDir("node-async-context")
        File(workDir, "data.txt").writeText("data\n")
        writeShadow(workDir, "async_hooks/index.js")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                (async function main() {
                  const asyncHooks = require("async_hooks");
                  const nodeAsyncHooks = require("node:async_hooks");
                  const fs = require("fs");
                  const fsp = require("fs/promises");
                  const bridge = require("autojs6:bridge");
                  const fetchModule = require("fetch");
                  const { Buffer } = require("buffer");
                  const timerPromises = require("timers/promises");
                  const asyncLocalStorage = new asyncHooks.AsyncLocalStorage();
                  const requests = [];

                  function storeLabel() {
                    const store = asyncLocalStorage.getStore();
                    return store && store.id ? String(store.id) : "none";
                  }

                  function readFileCallback() {
                    return new Promise((resolve, reject) => {
                      fs.readFile("data.txt", "utf8", (error, text) => {
                        if (error) {
                          reject(error);
                          return;
                        }
                        resolve(storeLabel() + ":" + String(text).trim());
                      });
                    });
                  }

                  bridge.__test.setTransport({
                    postMessage(message) {
                      const request = JSON.parse(message);
                      requests.push(request.module + ":" + request.method + ":" + storeLabel());
                      setImmediate(() => {
                        const callbackStore = storeLabel();
                        if (request.module === "fetch") {
                          const body = "fetch:" + callbackStore;
                          bridge.__test.receiveMessage(JSON.stringify({
                            id: request.id,
                            ok: true,
                            result: {
                              url: request.args[0].url,
                              status: 200,
                              statusText: "OK",
                              headers: [["content-type", "text/plain"], ["x-context", callbackStore]],
                              bodyBase64: Buffer.from(body).toString("base64"),
                              bodyBytes: body.length
                            }
                          }));
                          return;
                        }
                        bridge.__test.receiveMessage(JSON.stringify({
                          id: request.id,
                          ok: true,
                          result: {
                            store: callbackStore,
                            argStore: request.args[0] && request.args[0].store
                          }
                        }));
                      });
                    }
                  });

                  const summary = await asyncLocalStorage.run({ id: "ctx" }, async () => {
                    const promiseValue = await Promise.resolve().then(storeLabel);
                    const nextTickValue = await new Promise((resolve) => process.nextTick(() => resolve(storeLabel())));
                    const microtaskValue = await new Promise((resolve) => queueMicrotask(() => resolve(storeLabel())));
                    const timeoutValue = await new Promise((resolve) => setTimeout(() => resolve(storeLabel()), 0));
                    const immediateValue = await new Promise((resolve) => setImmediate(() => resolve(storeLabel())));
                    await timerPromises.setTimeout(0);
                    const timerPromiseValue = storeLabel();
                    const fsCallbackValue = await readFileCallback();
                    const fsPromiseValue = String(await fsp.readFile("data.txt", "utf8")).trim() + ":" + storeLabel();
                    const bridgeValue = await bridge.callAutoJs(
                      "toast",
                      "toast",
                      [{ store: storeLabel() }],
                      { timeoutMs: 1000, permissions: ["toast"] }
                    );
                    const fetchResponse = await fetchModule("https://example.invalid/context", { timeoutMs: 1000 });
                    const fetchText = await fetchResponse.text();
                    const asyncResource = new asyncHooks.AsyncResource("autojs6.s7-31");
                    const resourceValue = await new Promise((resolve) => {
                      asyncResource.runInAsyncScope(() => resolve(storeLabel()));
                    });
                    asyncResource.emitDestroy();
                    return [
                      promiseValue,
                      nextTickValue,
                      microtaskValue,
                      timeoutValue,
                      immediateValue,
                      timerPromiseValue,
                      fsCallbackValue,
                      fsPromiseValue,
                      bridgeValue.store + ":" + bridgeValue.argStore + ":" + storeLabel(),
                      fetchResponse.headers.get("x-context") + ":" + fetchText + ":" + storeLabel(),
                      resourceValue
                    ].join("|");
                  });

                  let mutationBlocked = false;
                  try {
                    asyncHooks.createHook = function() {};
                  } catch (_) {
                    mutationBlocked = true;
                  }

                  console.log("async.context.summary=" + summary);
                  console.log("async.context.requests=" + requests.join("|"));
                  console.log("async.context.cleanup=" + [
                    storeLabel(),
                    bridge.__test.pendingCount(),
                    Object.isFrozen(asyncHooks),
                    asyncHooks === nodeAsyncHooks,
                    asyncHooks.shadow === true,
                    typeof asyncHooks.createHook,
                    mutationBlocked || typeof asyncHooks.createHook === "undefined",
                    typeof asyncHooks.executionAsyncId,
                    typeof asyncHooks.executionAsyncResource,
                    typeof asyncHooks.asyncWrapProviders
                  ].join("|"));
                })().catch((error) => {
                  console.error("async.context.error=" + (error && (error.stack || error.message) || error));
                  process.exitCode = 1;
                });
            """.trimIndent(),
            sourceName = File(workDir, "main.js").absolutePath,
            workingDirectory = workDir,
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("async_hooks context propagation", result)
        assertTrue(
            result.stdout,
            result.stdout.contains("async.context.summary=ctx|ctx|ctx|ctx|ctx|ctx|ctx:data|data:ctx|ctx:ctx:ctx|ctx:fetch:ctx:ctx|ctx"),
        )
        assertTrue(result.stdout, result.stdout.contains("async.context.requests=toast:toast:ctx|fetch:request:ctx"))
        assertTrue(result.stdout, result.stdout.contains("async.context.cleanup=none|0|true|true|false|undefined|true|undefined|undefined|undefined"))
        assertTrue(result.nativeValues["embedded_script.pending_scoped_fs_callbacks"], result.nativeValues["embedded_script.pending_scoped_fs_callbacks"] == "0")
    }

    @Test
    fun perfHooksAndDiagnosticsChannelExposeSafeRuntimeTelemetry() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("perf_hooks diagnostics_channel runtime telemetry")
        val workDir = freshWorkDir("node-runtime-telemetry")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                (async function main() {
                  const diagnostics = require("diagnostics_channel");
                  const perfHooks = require("perf_hooks");
                  const bridge = require("autojs6:bridge");
                  const fetchModule = require("fetch");
                  const { Buffer } = require("buffer");
                  const timerPromises = require("timers/promises");
                  const performance = perfHooks.performance;

                  const channelNames = [
                    "autojs6.bridge.rpc.start",
                    "autojs6.bridge.rpc.end",
                    "autojs6.bridge.rpc.error",
                    "autojs6.fetch.request.start",
                    "autojs6.fetch.request.end",
                    "autojs6.fetch.request.error"
                  ];
                  const events = [];
                  let safeMessages = true;

                  function codeOf(error) {
                    return String(error && (error.autojs6Code || error.code || error.name || ""));
                  }

                  function record(message, name) {
                    if (!Object.isFrozen(message)) {
                      safeMessages = false;
                    }
                    if (
                      message.args !== undefined ||
                      message.url !== undefined ||
                      message.headers !== undefined ||
                      message.bodyBase64 !== undefined ||
                      message.bodyText !== undefined ||
                      message.result !== undefined ||
                      message.androidObject !== undefined
                    ) {
                      safeMessages = false;
                    }
                    events.push({
                      name,
                      phase: message.phase || message.status || "",
                      module: message.module || "",
                      method: message.method || "",
                      scheme: message.urlScheme || "",
                      status: message.httpStatus || 0,
                      code: message.code || "",
                      durationType: typeof message.durationMs,
                      permissionsArray: Array.isArray(message.permissions)
                    });
                  }

                  channelNames.forEach((name) => diagnostics.subscribe(name, record));
                  const hasBefore = channelNames.every((name) => diagnostics.hasSubscribers(name));

                  bridge.__test.setTransport({
                    postMessage(message) {
                      const request = JSON.parse(message);
                      setImmediate(() => {
                        if (request.module === "fetch") {
                          const payload = request.args[0];
                          if (String(payload.url).indexOf("/fail") >= 0) {
                            bridge.__test.receiveMessage(JSON.stringify({
                              id: request.id,
                              ok: false,
                              error: {
                                code: "ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED",
                                message: "controlled provider failed",
                                module: "fetch",
                                method: "request"
                              }
                            }));
                            return;
                          }
                          bridge.__test.receiveMessage(JSON.stringify({
                            id: request.id,
                            ok: true,
                            result: {
                              url: payload.url,
                              status: 200,
                              statusText: "OK",
                              headers: [["content-type", "text/plain"]],
                              bodyBase64: Buffer.from("ok").toString("base64"),
                              bodyBytes: 2
                            }
                          }));
                          return;
                        }
                        bridge.__test.receiveMessage(JSON.stringify({
                          id: request.id,
                          ok: true,
                          result: { accepted: true }
                        }));
                      });
                    }
                  });

                  const eluBefore = performance.eventLoopUtilization();
                  await timerPromises.setTimeout(1);
                  const eluDelta = performance.eventLoopUtilization(eluBefore);

                  await bridge.callAutoJs("toast", "toast", [], { timeoutMs: 1000, permissions: ["toast"] });
                  const response = await fetchModule("https://example.invalid/diag", { timeoutMs: 1000 });
                  await response.text();

                  let fetchErrorCode = "";
                  try {
                    await fetchModule("https://example.invalid/fail", { timeoutMs: 1000 });
                  } catch (error) {
                    fetchErrorCode = codeOf(error);
                  }

                  const beforeUnsubscribeCount = events.length;
                  channelNames.forEach((name) => diagnostics.unsubscribe(name, record));
                  const hasAfter = channelNames.some((name) => diagnostics.hasSubscribers(name));
                  await bridge.callAutoJs("toast", "toast", [], { timeoutMs: 1000, permissions: ["toast"] });
                  const afterUnsubscribeCount = events.length;

                  console.log("perf_hooks.runtime_elu=" + [
                    typeof performance.eventLoopUtilization,
                    Object.isFrozen(eluBefore),
                    Object.isFrozen(eluDelta),
                    typeof eluBefore.idle,
                    typeof eluDelta.active,
                    typeof eluDelta.utilization
                  ].join("|"));
                  console.log("diagnostics.runtime.names=" + events.map((event) => event.name).join("|"));
                  console.log("diagnostics.runtime.safe=" + safeMessages);
                  console.log("diagnostics.runtime.subscribers=" + [hasBefore, hasAfter].join("|"));
                  console.log("diagnostics.runtime.unsubscribe=" + (beforeUnsubscribeCount === afterUnsubscribeCount));
                  console.log("diagnostics.runtime.fetch_error=" + fetchErrorCode);
                  console.log("diagnostics.runtime.fetch_messages=" + events
                    .filter((event) => event.name.indexOf("autojs6.fetch") === 0)
                    .map((event) => [event.name, event.phase, event.method, event.scheme, event.status, event.code, event.durationType].join(","))
                    .join("|"));
                  console.log("diagnostics.runtime.bridge_permissions=" + events
                    .filter((event) => event.name === "autojs6.bridge.rpc.start")
                    .every((event) => event.permissionsArray));
                })().catch((error) => {
                  console.error("diagnostics.runtime.error=" + (error && (error.stack || error.message) || error));
                  process.exitCode = 1;
                });
            """.trimIndent(),
            sourceName = File(workDir, "main.js").absolutePath,
            workingDirectory = workDir,
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("perf_hooks diagnostics_channel runtime telemetry", result)
        assertTrue(result.stdout, result.stdout.contains("perf_hooks.runtime_elu=function|true|true|number|number|number"))
        assertTrue(result.stdout, result.stdout.contains("diagnostics.runtime.safe=true"))
        assertTrue(result.stdout, result.stdout.contains("diagnostics.runtime.subscribers=true|false"))
        assertTrue(result.stdout, result.stdout.contains("diagnostics.runtime.unsubscribe=true"))
        assertTrue(result.stdout, result.stdout.contains("diagnostics.runtime.fetch_error=${NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED}"))
        assertTrue(result.stdout, result.stdout.contains("autojs6.bridge.rpc.start"))
        assertTrue(result.stdout, result.stdout.contains("autojs6.bridge.rpc.end"))
        assertTrue(result.stdout, result.stdout.contains("autojs6.bridge.rpc.error"))
        assertTrue(result.stdout, result.stdout.contains("autojs6.fetch.request.start,start,GET,https:,0,,number"))
        assertTrue(result.stdout, result.stdout.contains("autojs6.fetch.request.end,end,GET,https:,200,,number"))
        assertTrue(result.stdout, result.stdout.contains("autojs6.fetch.request.error,error,GET,https:,0,${NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED},number"))
        assertTrue(result.stdout, result.stdout.contains("diagnostics.runtime.bridge_permissions=true"))
    }

    @Test
    fun nodeTestEmbeddedSubsetRunsSingleRuntimeTap() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("node:test embedded subset")
        val workDir = freshWorkDir("node-test-subset")
        writeShadow(workDir, "test/index.js")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                const nodeTest = require("node:test");
                const assert = require("assert/strict");
                const moduleBuiltin = require("module");
                const userTestPackage = require("test");
                const order = [];

                function denied(action) {
                  try {
                    action();
                    return "allowed";
                  } catch (error) {
                    return [
                      error.code,
                      error.autojs6Code,
                      String(error.message).includes("node:test")
                    ].join("/");
                  }
                }

                console.log("node_test.surface=" + [
                  Object.isFrozen(nodeTest),
                  nodeTest === nodeTest.test,
                  typeof nodeTest.describe,
                  typeof nodeTest.it,
                  typeof nodeTest.before,
                  typeof nodeTest.afterEach,
                  typeof nodeTest.run,
                  typeof nodeTest.mock,
                  typeof nodeTest.snapshot,
                  typeof nodeTest.watch,
                  typeof nodeTest.coverage,
                  nodeTest.reporter,
                  nodeTest.assert === assert
                ].join("|"));
                console.log("node_test.module=" + [
                  moduleBuiltin.isBuiltin("node:test"),
                  moduleBuiltin.isBuiltin("test"),
                  moduleBuiltin.builtinModules.includes("node:test"),
                  moduleBuiltin.builtinModules.includes("test"),
                  require.resolve("node:test"),
                  userTestPackage.shadow === true,
                  require.resolve("test").replace(/\\/g, "/").includes("/node_modules/test/index.js")
                ].join("|"));
                console.log("node_test.denied=" + [
                  denied(() => process.getBuiltinModule("node:test")),
                  denied(() => require("node:test/reporters"))
                ].join("|"));

                nodeTest.describe("suite", () => {
                  nodeTest.before(() => {
                    order.push("before");
                  });
                  nodeTest.after(() => {
                    console.log("node_test.order=" + order.join(">"));
                  });
                  nodeTest.beforeEach((t) => {
                    order.push("beforeEach:" + t.name);
                  });
                  nodeTest.afterEach((t) => {
                    order.push("afterEach:" + t.name);
                  });
                  nodeTest.test("sync pass", (t) => {
                    assert.strictEqual(t.name, "sync pass");
                    t.diagnostic("diag-ok");
                    order.push("sync");
                  });
                  nodeTest.it("async pass", async () => {
                    await new Promise((resolve) => setTimeout(resolve, 0));
                    order.push("async");
                  });
                  nodeTest.skip("skipped", { skip: "reason" }, () => {
                    throw new Error("skip body should not run");
                  });
                  nodeTest.todo("todo item", { todo: "later" }, () => {
                    throw new Error("todo body should not run");
                  });
                  nodeTest.test("context skip", (t) => {
                    t.skip("runtime");
                  });
                  nodeTest.test("context todo", (t) => {
                    t.todo("runtime");
                  });
                });
            """.trimIndent(),
            sourceName = File(workDir, "main.js").absolutePath,
            workingDirectory = workDir,
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("node:test embedded subset", result)
        assertTrue(
            result.stdout,
            result.stdout.contains("node_test.surface=true|true|function|function|function|function|undefined|undefined|undefined|undefined|undefined|tap|true"),
        )
        assertTrue(result.stdout, result.stdout.contains("node_test.module=true|false|true|false|node:test|true|true"))
        assertTrue(
            result.stdout,
            result.stdout.contains("node_test.denied=${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.PROCESS_API_DISABLED}/true|${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.BUILTIN_DISABLED}/true"),
        )
        assertTrue(result.stdout, result.stdout.contains("TAP version 13"))
        assertTrue(result.stdout, result.stdout.contains("# diag-ok"))
        assertTrue(result.stdout, result.stdout.contains("ok 3 - skipped # SKIP reason"))
        assertTrue(result.stdout, result.stdout.contains("ok 4 - todo item # TODO later"))
        assertTrue(result.stdout, result.stdout.contains("ok 5 - context skip # SKIP runtime"))
        assertTrue(result.stdout, result.stdout.contains("ok 6 - context todo # TODO runtime"))
        assertTrue(result.stdout, result.stdout.contains("# tests 6"))
        assertTrue(result.stdout, result.stdout.contains("# pass 2"))
        assertTrue(result.stdout, result.stdout.contains("# fail 0"))
        assertTrue(result.stdout, result.stdout.contains("# skipped 2"))
        assertTrue(result.stdout, result.stdout.contains("# todo 2"))
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "node_test.order=before>beforeEach:sync pass>sync>afterEach:sync pass>" +
                    "beforeEach:async pass>async>afterEach:async pass>" +
                    "beforeEach:context skip>afterEach:context skip>" +
                    "beforeEach:context todo>afterEach:context todo",
            ),
        )
        assertTrue(result.nativeValues["embedded_script.pending_node_test_callbacks"], result.nativeValues["embedded_script.pending_node_test_callbacks"] == "0")
    }

    @Test
    fun nodeTestEmbeddedSubsetReportsFailuresTimeoutsAndLateAsyncActivity() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("node:test embedded subset failure reporting")
        val workDir = freshWorkDir("node-test-failures")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                const test = require("node:test");
                const assert = require("assert/strict");

                test("assertion failure", () => {
                  assert.strictEqual(1, 2);
                });
                test("timeout failure", { timeout: 1 }, async () => {
                  await new Promise(() => {});
                });
                test("late timer failure", () => {
                  setTimeout(() => {}, 25);
                });
            """.trimIndent(),
            sourceName = File(workDir, "main.js").absolutePath,
            workingDirectory = workDir,
            timeoutMs = 10_000L,
        )

        assertTrue(NodeEmbeddedTestHarness.describeResult("node:test failures should fail script", result), !result.succeeded)
        assertTrue(result.stdout, result.stdout.contains("not ok 1 - assertion failure"))
        assertTrue(result.stdout, result.stdout.contains("ERR_ASSERTION"))
        assertTrue(result.stdout, result.stdout.contains("not ok 2 - timeout failure"))
        assertTrue(result.stdout, result.stdout.contains("ERR_AUTOJS6_NODE_TEST_TIMEOUT"))
        assertTrue(result.stdout, result.stdout.contains("not ok 3 - late timer failure"))
        assertTrue(result.stdout, result.stdout.contains("ERR_AUTOJS6_NODE_TEST_ASYNC_ACTIVITY"))
        assertTrue(result.stdout, result.stdout.contains("# fail 3"))
        assertTrue(result.errorCode, result.errorCode == "ERR_AUTOJS6_NODE_TEST_FAILURE")
        assertTrue(result.nativeValues["embedded_script.pending_node_test_callbacks"], result.nativeValues["embedded_script.pending_node_test_callbacks"] == "0")
    }

    @Test
    fun partialEsmImportsNodeTestEmbeddedSubset() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("node:test partial ESM import")
        val workDir = freshWorkDir("node-test-esm")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import nodeTestDefault, { test, describe, it, before, after, beforeEach, afterEach, assert as testAssert, reporter } from "node:test";
                import assert from "assert/strict";

                const order = [];
                console.log("node_test.esm.surface=" + [
                  typeof nodeTestDefault,
                  nodeTestDefault === test,
                  typeof describe,
                  typeof it,
                  typeof before,
                  typeof afterEach,
                  testAssert === assert,
                  reporter
                ].join("|"));

                describe("esm suite", () => {
                  before(() => {
                    order.push("before");
                  });
                  after(() => {
                    console.log("node_test.esm.order=" + order.join(">"));
                  });
                  beforeEach((t) => {
                    order.push("beforeEach:" + t.name);
                  });
                  afterEach((t) => {
                    order.push("afterEach:" + t.name);
                  });
                  test("esm named pass", async (t) => {
                    await Promise.resolve();
                    assert.strictEqual(t.name, "esm named pass");
                    order.push("named");
                  });
                  it("esm it pass", () => {
                    assert.strictEqual(nodeTestDefault.reporter, "tap");
                    order.push("it");
                  });
                });
            """.trimIndent(),
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("node:test partial ESM import", result)
        assertTrue(result.stdout, result.stdout.contains("node_test.esm.surface=function|true|function|function|function|function|true|tap"))
        assertTrue(result.stdout, result.stdout.contains("# tests 2"))
        assertTrue(result.stdout, result.stdout.contains("# pass 2"))
        assertTrue(result.stdout, result.stdout.contains("# fail 0"))
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "node_test.esm.order=before>beforeEach:esm named pass>named>afterEach:esm named pass>" +
                    "beforeEach:esm it pass>it>afterEach:esm it pass",
            ),
        )
    }

    @Test
    fun partialEsmImportsSameCoreBuiltinWrappers() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("core builtin ESM compatibility")
        val workDir = freshWorkDir("node-core-builtins-esm")
        writeShadow(workDir, "constants/index.js")
        writeShadow(workDir, "util/types.js")
        writeShadow(workDir, "events/index.js")
        writeShadow(workDir, "diagnostics_channel/index.js")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import constants from "constants";
                import nodeConstants from "node:constants";
                import utilTypes from "util/types";
                import nodeUtilTypes from "node:util/types";
                import events, { EventEmitter } from "events";
                import nodeEvents from "node:events";
                import diagnostics from "diagnostics_channel";
                import nodeDiagnostics from "node:diagnostics_channel";

                const emitter = new EventEmitter();
                const ready = events.once(emitter, "ready").then((args) => args[0]);
                process.nextTick(() => {
                  emitter.emit("ready", "esm-ok");
                });
                const readyValue = await ready;

                const channel = diagnostics.channel("autojs6.core.esm");
                const seen = [];
                function listener(message, name) {
                  seen.push(name + ":" + message.value);
                }
                channel.subscribe(listener);
                channel.publish({ value: "esm" });
                channel.unsubscribe(listener);

                console.log("core.builtin.esm.aliases=" + (
                  constants === nodeConstants &&
                  utilTypes === nodeUtilTypes &&
                  events === nodeEvents &&
                  diagnostics === nodeDiagnostics
                ));
                console.log("core.builtin.esm.shadow=" + (
                  constants.shadow !== true &&
                  utilTypes.shadow !== true &&
                  events.shadow !== true &&
                  diagnostics.shadow !== true
                ));
                console.log("core.builtin.esm.surface=" + (
                  Object.isFrozen(constants) &&
                  typeof constants.F_OK === "number" &&
                  Object.isFrozen(utilTypes) &&
                  utilTypes.isPromise(Promise.resolve()) &&
                  events === events.EventEmitter &&
                  readyValue === "esm-ok" &&
                  typeof events.EventTarget === "undefined" &&
                  typeof globalThis.EventTarget === "function" &&
                  Object.isFrozen(diagnostics) &&
                  seen.join("|") === "autojs6.core.esm:esm"
                ));
            """.trimIndent(),
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("core builtin ESM compatibility", result)
        assertTrue(result.stdout.contains("core.builtin.esm.aliases=true"))
        assertTrue(result.stdout.contains("core.builtin.esm.shadow=true"))
        assertTrue(result.stdout.contains("core.builtin.esm.surface=true"))
    }

    private fun freshWorkDir(name: String): File {
        return File(context.cacheDir, "$name-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create core builtin test dir: $absolutePath" }
        }
    }

    private fun writeShadow(workDir: File, relativePath: String) {
        File(workDir, "node_modules/$relativePath").apply {
            parentFile?.mkdirs()
            writeText("""module.exports = { shadow: true };""")
        }
    }
}
