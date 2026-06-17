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
