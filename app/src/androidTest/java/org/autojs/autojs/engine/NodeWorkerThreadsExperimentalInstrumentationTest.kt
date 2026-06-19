package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeWorkerThreadsExperimentalInstrumentationTest {

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
    fun workerThreadsRemainDisabledByDefault() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("worker_threads default gate")
        assumeTrue(
            "Default worker_threads denial gate runs only when the build-level experimental flag is disabled.",
            !BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED,
        )
        assertFalse(BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED)

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                function codeOf(error) {
                  return String(error && (error.autojs6Code || error.code || error.name || ""));
                }
                try {
                  require("worker_threads");
                } catch (error) {
                  console.log("worker.default=" + codeOf(error));
                }
            """.trimIndent(),
            sourceName = "<node-worker-default-disabled.js>",
            workerThreadsExperimentalEnabled = false,
        )

        NodeEmbeddedTestHarness.assertSucceeded("worker_threads default gate", result)
        assertTrue(result.stdout, result.stdout.contains("worker.default=${NodeRuntimeErrorCodes.BUILTIN_DISABLED}"))
        assertEquals("false", result.nativeValues["embedded_script.request.worker_threads_experimental_enabled"])
        assertEquals("false", result.nativeValues["embedded_script.worker_threads_enabled"])
    }

    @Test
    fun experimentalWorkerThreadsRunPureJsWorkersOnly() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("worker_threads experimental")
        NodeWorkerThreadsTestSupport.assumeNativeWorkerThreadsAvailable("worker_threads experimental runnable smoke")

        val workDir = File(context.cacheDir, "node-worker-experimental-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create worker experimental work dir: $absolutePath" }
        }
        try {
            File(workDir, "math.cjs").writeText(
                """
                    exports.add = function add(left, right) {
                      return left + right;
                    };
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )
            File(workDir, "payload.json").writeText(
                """{"suffix":"json-dep"}""" + "\n",
                StandardCharsets.UTF_8,
            )
            File(workDir, "compute.js").writeText(
                """
                    const { parentPort, workerData, isMainThread } = require("worker_threads");
                    const assert = require("assert");
                    const math = require("./math.cjs");
                    const payload = require("./payload.json");
                    assert.strictEqual(isMainThread, false);
                    parentPort.postMessage({
                      kind: "compute",
                      value: math.add(workerData.left, workerData.right),
                      data: workerData.label,
                      suffix: payload.suffix
                    });
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )
            File(workDir, "restricted.js").writeText(
                """
                    const { parentPort } = require("worker_threads");
                    function codeOf(error) {
                      return String(error && (error.autojs6Code || error.code || error.name || ""));
                    }
                    function denied(label, action) {
                      try {
                        action();
                        parentPort.postMessage({ label, code: "allowed" });
                      } catch (error) {
                        parentPort.postMessage({ label, code: codeOf(error) });
                      }
                    }
                    denied("toast", () => require("toast"));
                    denied("accessibility", () => require("accessibility"));
                    denied("fs", () => require("fs"));
                    denied("net", () => require("net"));
                    denied("nested", () => new (require("worker_threads").Worker)("./compute.js"));
                    parentPort.postMessage({ label: "done", code: "done" });
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )
            File(workDir, "hold.js").writeText(
                """
                    setInterval(() => {}, 1000);
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )
            File(workDir, "node_modules/worker_threads").apply {
                mkdirs()
                File(this, "index.js").writeText(
                    """module.exports = { shadowed: true };""" + "\n",
                    StandardCharsets.UTF_8,
                )
            }

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async function main() {
                      const profile = require("autojs6:profile");
                      const workerThreads = require("worker_threads");
                      const { Worker } = workerThreads;

                      function codeOf(error) {
                        return String(error && (error.autojs6Code || error.code || error.name || ""));
                      }
                      function once(worker, eventName) {
                        return new Promise((resolve, reject) => {
                          worker.once("error", reject);
                          worker.once(eventName, resolve);
                        });
                      }
                      function collectUntilDone(worker) {
                        return new Promise((resolve, reject) => {
                          const values = [];
                          worker.on("message", (message) => {
                            values.push(message);
                            if (message && message.label === "done") {
                              resolve(values);
                            }
                          });
                          worker.once("error", reject);
                        });
                      }

                      console.log("worker.profile=" + [
                        profile.workerThreads,
                        profile.highRiskCapabilities.workerThreads,
                        profile.experimentalFlags.workerThreads,
                        profile.featureFlags.worker_threads.enabled,
                        profile.disabledBuiltins.includes("worker_threads")
                      ].join("|"));
                      console.log("worker.resolve=" + [
                        require.resolve("worker_threads"),
                        require.resolve("node:worker_threads")
                      ].join("|"));
                      console.log("worker.shadow=" + (workerThreads.shadowed === true));
                      console.log("worker.policy=" + [
                        workerThreads.policy.maxWorkers,
                        workerThreads.policy.startupTimeoutMs,
                        workerThreads.policy.allowedBuiltins.includes("assert")
                      ].join("|"));

                      const compute = new Worker("./compute.js", {
                        workerData: { left: 7, right: 5, label: "worker-data" }
                      });
                      const computeExit = once(compute, "exit");
                      const computeMessage = await once(compute, "message");
                      console.log("worker.compute=" + [computeMessage.value, computeMessage.data, computeMessage.suffix].join("|"));
                      console.log("worker.computeExit=" + await computeExit);

                      const hold = new Worker("./hold.js");
                      let limitCode = "";
                      try {
                        new Worker("./hold.js");
                      } catch (error) {
                        limitCode = codeOf(error);
                      }
                      const terminateCode = await hold.terminate();
                      console.log("worker.limit=" + limitCode);
                      console.log("worker.terminate=" + terminateCode);

                      const restricted = new Worker("./restricted.js");
                      const restrictedExit = once(restricted, "exit");
                      const restrictedMessages = await collectUntilDone(restricted);
                      await restrictedExit;
                      const restrictedCodes = Object.create(null);
                      for (const message of restrictedMessages) {
                        restrictedCodes[message.label] = message.code;
                      }
                      console.log("worker.restricted=" + [
                        restrictedCodes.toast,
                        restrictedCodes.accessibility,
                        restrictedCodes.fs,
                        restrictedCodes.net,
                        restrictedCodes.nested
                      ].join("|"));

                      let escapeCode = "";
                      try {
                        new Worker("../escape.js");
                      } catch (error) {
                        escapeCode = codeOf(error);
                      }
                      console.log("worker.escape=" + escapeCode);

                      const diagnostics = workerThreads.diagnostics();
                      console.log("worker.diagnostics=" + [
                        diagnostics.activeWorkerCount,
                        diagnostics.maxActiveWorkerCount,
                        diagnostics.createdCount,
                        diagnostics.terminatedCount,
                        diagnostics.rejectedByPolicyCount
                      ].join("|"));
                    })();
                """.trimIndent(),
                sourceName = "<node-worker-experimental.js>",
                workingDirectory = workDir,
                timeoutMs = 15_000L,
                workerThreadsExperimentalEnabled = true,
            )

            NodeEmbeddedTestHarness.assertSucceeded("worker_threads experimental", result)
            assertTrue(result.stdout, result.stdout.contains("worker.profile=true|true|true|true|false"))
            assertTrue(result.stdout, result.stdout.contains("worker.resolve=worker_threads|worker_threads"))
            assertTrue(result.stdout, result.stdout.contains("worker.shadow=false"))
            assertTrue(result.stdout, result.stdout.contains("worker.policy=1|5000|true"))
            assertTrue(result.stdout, result.stdout.contains("worker.compute=12|worker-data|json-dep"))
            assertTrue(result.stdout, result.stdout.contains("worker.computeExit=0"))
            assertTrue(result.stdout, result.stdout.contains("worker.limit=${NodeRuntimeErrorCodes.WORKER_LIMIT_EXCEEDED}"))
            assertTrue(result.stdout, result.stdout.contains("worker.terminate="))
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "worker.restricted=${NodeRuntimeErrorCodes.WORKER_BRIDGE_DENIED}|${NodeRuntimeErrorCodes.WORKER_BRIDGE_DENIED}|${NodeRuntimeErrorCodes.WORKER_BRIDGE_DENIED}|${NodeRuntimeErrorCodes.WORKER_BRIDGE_DENIED}|${NodeRuntimeErrorCodes.WORKER_DISABLED}",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("worker.escape=${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"))
            assertTrue(result.stdout, result.stdout.contains("worker.diagnostics=0|1|"))
            assertEquals("true", result.nativeValues["embedded_script.request.worker_threads_experimental_enabled"])
            assertEquals("true", result.nativeValues["embedded_script.worker_threads_enabled"])
            assertEquals("0", result.nativeValues["embedded_script.worker_active_count"])
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun experimentalWorkerThreadsSupportCjsAndEsmEntries() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("worker_threads CJS/ESM entry")
        NodeWorkerThreadsTestSupport.assumeNativeWorkerThreadsAvailable("worker_threads CJS/ESM entry")

        val workDir = File(context.cacheDir, "node-worker-entry-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create worker entry work dir: $absolutePath" }
        }
        try {
            File(workDir, "dep.cjs").writeText(
                """
                    module.exports = {
                      base: 10,
                      label: "cjs-dep"
                    };
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )
            File(workDir, "data.json").writeText(
                """{"extra":2,"label":"json-dep"}""" + "\n",
                StandardCharsets.UTF_8,
            )
            File(workDir, "worker.cjs").writeText(
                """
                    const { parentPort, workerData, isMainThread } = require("worker_threads");
                    const assert = require("assert/strict");
                    const path = require("path");
                    const dep = require("./dep.cjs");
                    const data = require("./data.json");
                    assert.equal(isMainThread, false);
                    assert.equal(path.basename(__filename), "worker.cjs");
                    parentPort.postMessage({
                      kind: "cjs",
                      value: dep.base + workerData.delta + data.extra,
                      label: data.label
                    });
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )
            File(workDir, "esm-dep.mjs").writeText(
                """
                    export const extra = 4;
                    export function tag(value) {
                      return "esm-" + value;
                    }
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )
            File(workDir, "worker.mjs").writeText(
                """
                    import { parentPort, workerData, isMainThread } from "worker_threads";
                    import assert from "assert/strict";
                    import dep from "./dep.cjs";
                    import { extra, tag } from "./esm-dep.mjs";

                    assert.equal(isMainThread, false);
                    parentPort.postMessage({
                      kind: "mjs",
                      value: dep.base + workerData.delta + extra,
                      label: tag(workerData.label)
                    });
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )
            File(workDir, "pkg").apply {
                mkdirs()
                File(this, "package.json").writeText(
                    """{"type":"module"}""" + "\n",
                    StandardCharsets.UTF_8,
                )
                File(this, "helper.js").writeText(
                    """
                        export const bump = 6;
                    """.trimIndent(),
                    StandardCharsets.UTF_8,
                )
                File(this, "worker.js").writeText(
                    """
                        import { parentPort, workerData } from "worker_threads";
                        import { bump } from "./helper.js";

                        parentPort.postMessage({
                          kind: "type-module",
                          value: workerData.seed + bump
                        });
                    """.trimIndent(),
                    StandardCharsets.UTF_8,
                )
            }
            File(workDir, "native.node").writeBytes(ByteArray(0))
            File(workDir, "denied.cjs").writeText(
                """
                    const { parentPort } = require("worker_threads");
                    function codeOf(error) {
                      return String(error && (error.autojs6Code || error.code || error.name || ""));
                    }
                    const codes = [];
                    function denied(action) {
                      try {
                        action();
                        codes.push("allowed");
                      } catch (error) {
                        codes.push(codeOf(error));
                      }
                    }
                    denied(() => require("toast"));
                    denied(() => require("net"));
                    denied(() => require("./native.node"));
                    parentPort.postMessage({ codes });
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async function main() {
                      const { Worker } = require("worker_threads");
                      function codeOf(error) {
                        return String(error && (error.autojs6Code || error.code || error.name || ""));
                      }
                      function run(file, workerData) {
                        return new Promise((resolve, reject) => {
                          const worker = new Worker(file, { workerData });
                          let message;
                          worker.once("message", (value) => {
                            message = value;
                          });
                          worker.once("error", reject);
                          worker.once("exit", (code) => {
                            if (code !== 0) {
                              reject(new Error("worker " + file + " exited with " + code));
                            } else {
                              resolve(message);
                            }
                          });
                        });
                      }

                      const cjs = await run("./worker.cjs", { delta: 5 });
                      console.log("worker.entry.cjs=" + [cjs.kind, cjs.value, cjs.label].join("|"));

                      const mjs = await run("./worker.mjs", { delta: 3, label: "ok" });
                      console.log("worker.entry.mjs=" + [mjs.kind, mjs.value, mjs.label].join("|"));

                      const typeModule = await run("./pkg/worker.js", { seed: 7 });
                      console.log("worker.entry.typeModule=" + [typeModule.kind, typeModule.value].join("|"));

                      const denied = await run("./denied.cjs", {});
                      console.log("worker.entry.denied=" + denied.codes.join("|"));

                      let escapeCode = "";
                      try {
                        new Worker("../outside.cjs");
                      } catch (error) {
                        escapeCode = codeOf(error);
                      }
                      console.log("worker.entry.escape=" + escapeCode);
                    })();
                """.trimIndent(),
                sourceName = "<node-worker-entry.js>",
                workingDirectory = workDir,
                timeoutMs = 15_000L,
                workerThreadsExperimentalEnabled = true,
            )

            NodeEmbeddedTestHarness.assertSucceeded("worker_threads CJS/ESM entry", result)
            assertTrue(result.stdout, result.stdout.contains("worker.entry.cjs=cjs|17|json-dep"))
            assertTrue(result.stdout, result.stdout.contains("worker.entry.mjs=mjs|17|esm-ok"))
            assertTrue(result.stdout, result.stdout.contains("worker.entry.typeModule=type-module|13"))
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "worker.entry.denied=${NodeRuntimeErrorCodes.WORKER_BRIDGE_DENIED}|${NodeRuntimeErrorCodes.WORKER_BRIDGE_DENIED}|${NodeRuntimeErrorCodes.NATIVE_ADDON_DISABLED}",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("worker.entry.escape=${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"))
            assertEquals("true", result.nativeValues["embedded_script.request.worker_threads_experimental_enabled"])
            assertEquals("true", result.nativeValues["embedded_script.worker_threads_enabled"])
            assertEquals("0", result.nativeValues["embedded_script.worker_active_count"])
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun experimentalWorkerThreadsSupportMessageChannelAndTransferList() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("worker_threads MessageChannel transferList")
        NodeWorkerThreadsTestSupport.assumeNativeWorkerThreadsAvailable("worker_threads MessageChannel transferList")

        val workDir = File(context.cacheDir, "node-worker-message-channel-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create worker message channel work dir: $absolutePath" }
        }
        try {
            File(workDir, "message-worker.cjs").writeText(
                """
                    const { parentPort, MessageChannel, MessagePort } = require("worker_threads");

                    parentPort.on("message", (message) => {
                      if (message.kind === "buffer") {
                        const view = new Uint8Array(message.buffer);
                        parentPort.postMessage({
                          kind: "buffer",
                          length: message.buffer.byteLength,
                          first: view[0]
                        });
                        return;
                      }
                      if (message.kind === "port") {
                        const port = message.port;
                        port.on("message", (portMessage) => {
                          const view = new Uint8Array(portMessage.buffer);
                          port.postMessage({
                            kind: "port-reply",
                            length: portMessage.buffer.byteLength,
                            first: view[0],
                            isPort: port instanceof MessagePort
                          }, [portMessage.buffer]);
                        });
                        if (typeof port.start === "function") {
                          port.start();
                        }
                        parentPort.postMessage({
                          kind: "port-ready",
                          isPort: port instanceof MessagePort
                        });
                        return;
                      }
                      if (message.kind === "worker-channel") {
                        const channel = new MessageChannel();
                        channel.port1.on("message", (value) => {
                          parentPort.postMessage({
                            kind: "worker-channel",
                            value: value && value.value,
                            portCheck: channel.port1 instanceof MessagePort && channel.port2 instanceof MessagePort
                          });
                          channel.port1.close();
                          channel.port2.close();
                        });
                        channel.port2.postMessage({ value: "inside-worker" });
                        if (typeof channel.port1.start === "function") {
                          channel.port1.start();
                        }
                      }
                    });
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async function main() {
                      const workerThreads = require("worker_threads");
                      const { Worker, MessageChannel, MessagePort } = workerThreads;

                      function once(emitter, eventName) {
                        return new Promise((resolve, reject) => {
                          emitter.once("error", reject);
                          emitter.once(eventName, resolve);
                        });
                      }
                      function waitFor(worker, expectedKind) {
                        return new Promise((resolve, reject) => {
                          function onMessage(message) {
                            if (message && message.kind === expectedKind) {
                              worker.off("message", onMessage);
                              resolve(message);
                            }
                          }
                          worker.on("message", onMessage);
                          worker.once("error", reject);
                        });
                      }
                      function codeOf(error) {
                        return String(error && (error.name || error.autojs6Code || error.code || ""));
                      }

                      console.log("worker.message.policy=" + [
                        workerThreads.policy.maxMessageBytes,
                        workerThreads.policy.maxQueuedMessages,
                        typeof MessageChannel,
                        typeof MessagePort
                      ].join("|"));

                      const worker = new Worker("./message-worker.cjs");
                      await once(worker, "online");

                      const buffer = new ArrayBuffer(8);
                      new Uint8Array(buffer)[0] = 17;
                      worker.postMessage({ kind: "buffer", buffer }, [buffer]);
                      console.log("worker.message.detached=" + buffer.byteLength);
                      const bufferReply = await waitFor(worker, "buffer");
                      console.log("worker.message.buffer=" + [
                        bufferReply.length,
                        bufferReply.first
                      ].join("|"));

                      const channel = new MessageChannel();
                      console.log("worker.message.channelInstance=" + [
                        channel.port1 instanceof MessagePort,
                        channel.port2 instanceof MessagePort
                      ].join("|"));
                      worker.postMessage({ kind: "port", port: channel.port2 }, [channel.port2]);
                      const ready = await waitFor(worker, "port-ready");
                      console.log("worker.message.portReady=" + ready.isPort);

                      const portReplyPromise = once(channel.port1, "message");
                      const portBuffer = new ArrayBuffer(4);
                      new Uint8Array(portBuffer)[0] = 23;
                      channel.port1.postMessage({ buffer: portBuffer }, [portBuffer]);
                      console.log("worker.message.portDetached=" + portBuffer.byteLength);
                      const portReply = await portReplyPromise;
                      console.log("worker.message.portReply=" + [
                        portReply.kind,
                        portReply.length,
                        portReply.first,
                        portReply.isPort
                      ].join("|"));
                      channel.port1.close();

                      worker.postMessage({ kind: "worker-channel" });
                      const workerChannel = await waitFor(worker, "worker-channel");
                      console.log("worker.message.workerChannel=" + [
                        workerChannel.value,
                        workerChannel.portCheck
                      ].join("|"));

                      let cloneError = "";
                      try {
                        worker.postMessage({ value: function denied() {} });
                      } catch (error) {
                        cloneError = codeOf(error);
                      }
                      console.log("worker.message.cloneError=" + cloneError);

                      let largeError = "";
                      try {
                        worker.postMessage({ value: "x".repeat(workerThreads.policy.maxMessageBytes + 1) });
                      } catch (error) {
                        largeError = codeOf(error);
                      }
                      console.log("worker.message.largeError=" + largeError);

                      let invalidTransferError = "";
                      try {
                        worker.postMessage({ value: 1 }, 7);
                      } catch (error) {
                        invalidTransferError = codeOf(error);
                      }
                      console.log("worker.message.invalidTransferError=" + invalidTransferError);

                      const burst = new MessageChannel();
                      let queueError = "";
                      try {
                        for (let index = 0; index <= workerThreads.policy.maxQueuedMessages; index += 1) {
                          burst.port1.postMessage({ index });
                        }
                      } catch (error) {
                        queueError = codeOf(error);
                      }
                      console.log("worker.message.queueError=" + queueError);

                      const leaked = new MessageChannel();
                      leaked.port1.unref();
                      leaked.port2.unref();

                      await worker.terminate();
                      console.log("worker.message.diagnostics=" + [
                        workerThreads.diagnostics().activeWorkerCount,
                        workerThreads.diagnostics().activeMessagePortCount,
                        workerThreads.diagnostics().messageRejectedCount,
                        workerThreads.diagnostics().lastMessageRejection
                      ].join("|"));
                    })();
                """.trimIndent(),
                sourceName = "<node-worker-message-channel.js>",
                workingDirectory = workDir,
                timeoutMs = 20_000L,
                workerThreadsExperimentalEnabled = true,
            )

            NodeEmbeddedTestHarness.assertSucceeded("worker_threads MessageChannel transferList", result)
            assertTrue(result.stdout, result.stdout.contains("worker.message.policy=65536|32|function|function"))
            assertTrue(result.stdout, result.stdout.contains("worker.message.detached=0"))
            assertTrue(result.stdout, result.stdout.contains("worker.message.buffer=8|17"))
            assertTrue(result.stdout, result.stdout.contains("worker.message.channelInstance=true|true"))
            assertTrue(result.stdout, result.stdout.contains("worker.message.portReady=true"))
            assertTrue(result.stdout, result.stdout.contains("worker.message.portDetached=0"))
            assertTrue(result.stdout, result.stdout.contains("worker.message.portReply=port-reply|4|23|true"))
            assertTrue(result.stdout, result.stdout.contains("worker.message.workerChannel=inside-worker|true"))
            assertTrue(result.stdout, result.stdout.contains("worker.message.cloneError=DataCloneError"))
            assertTrue(result.stdout, result.stdout.contains("worker.message.largeError=DataCloneError"))
            assertTrue(result.stdout, result.stdout.contains("worker.message.invalidTransferError=DataCloneError"))
            assertTrue(result.stdout, result.stdout.contains("worker.message.queueError=DataCloneError"))
            assertTrue(result.stdout, result.stdout.contains("worker.message.diagnostics=0|"))
            assertEquals("true", result.nativeValues["embedded_script.request.worker_threads_experimental_enabled"])
            assertEquals("true", result.nativeValues["embedded_script.worker_threads_enabled"])
            assertEquals("0", result.nativeValues["embedded_script.worker_active_count"])
            assertEquals("0", result.nativeValues["embedded_script.worker_message_port_active_count"])
        } finally {
            workDir.deleteRecursively()
        }
    }
}
