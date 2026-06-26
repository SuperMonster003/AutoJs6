package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeStdlibPhase13DecisionInstrumentationTest {

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
    fun stdlibProfileAndRepresentativeModulesRecordPartialConformance() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-43 stdlib conformance decision")

        val workDir = File(context.cacheDir, "node-stdlib-p13-43-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create P13-43 stdlib work dir: $absolutePath" }
        }
        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async () => {
                      const profile = require("autojs6:profile");
                      const stdlibProfile = profile.stdlibProfile || {};
                      const moduleStatus = stdlibProfile.moduleStatus || {};

                      console.log("p13stdlib.profile=" + [
                        stdlibProfile.status,
                        stdlibProfile.defaultEnabled,
                        Array.isArray(stdlibProfile.targetProfiles) && stdlibProfile.targetProfiles.join(","),
                        stdlibProfile.conformanceSet,
                        moduleStatus.crypto,
                        moduleStatus.stream,
                        moduleStatus.zlib,
                        moduleStatus.buffer,
                        moduleStatus.url,
                        moduleStatus.os,
                        moduleStatus.readline,
                        moduleStatus.tty,
                        moduleStatus.perf_hooks,
                        moduleStatus.async_hooks,
                        moduleStatus["zlib/promises"],
                        Array.isArray(stdlibProfile.conformanceModules) && stdlibProfile.conformanceModules.join(","),
                        stdlibProfile.cjsAliases,
                        stdlibProfile.esmImports,
                        stdlibProfile.shadowing,
                        stdlibProfile.processGetBuiltinModule,
                        stdlibProfile.androidDifferences,
                        Array.isArray(stdlibProfile.androidUnsupported) && stdlibProfile.androidUnsupported.join(","),
                        stdlibProfile.resourceLimits,
                        stdlibProfile.npmCorpus,
                        stdlibProfile.desktopDifferential,
                        stdlibProfile.packagedBehavior,
                        stdlibProfile.rawDesktopParity,
                        stdlibProfile.rawNativeHandles,
                        stdlibProfile.unrestrictedNetwork,
                        stdlibProfile.broadTerminal,
                        Object.isFrozen(stdlibProfile),
                        Object.isFrozen(moduleStatus),
                        Object.isFrozen(stdlibProfile.conformanceModules),
                        Object.isFrozen(stdlibProfile.androidUnsupported)
                      ].join("|"));

                      const { Buffer, transcode } = require("buffer");
                      const crypto = require("crypto");
                      const stream = require("stream");
                      const streamPromises = require("stream/promises");
                      const streamConsumers = require("stream/consumers");
                      const zlib = require("zlib");
                      const url = require("url");
                      const os = require("os");
                      const readline = require("readline");
                      const readlinePromises = require("readline/promises");
                      const tty = require("tty");
                      const perfHooks = require("perf_hooks");
                      const asyncHooks = require("async_hooks");

                      function denySummary(action) {
                        try {
                          action();
                          return "allowed";
                        } catch (error) {
                          return [
                            error && error.code,
                            error && error.autojs6Code,
                            String(error && error.message || error).includes("disabled")
                          ].filter((value) => value !== undefined).join("/");
                        }
                      }

                      function requireDenied(specifier) {
                        try {
                          require(specifier);
                          return "allowed";
                        } catch (error) {
                          return [
                            error && error.code,
                            error && error.autojs6Code
                          ].filter(Boolean).join("/");
                        }
                      }

                      const cryptoOk =
                        crypto === require("node:crypto") &&
                        crypto.createHash("sha256").update("p13").digest("hex").length === 64 &&
                        crypto.createHmac("sha1", "key").update("value").digest("hex").length === 40 &&
                        crypto.randomBytes(8).length === 8 &&
                        crypto.webcrypto === globalThis.crypto &&
                        typeof crypto.generateKeyPair === "undefined" &&
                        typeof crypto.createCipheriv === "undefined";

                      const pipelineChunks = [];
                      await streamPromises.pipeline(
                        stream.Readable.from(["s", "t"]),
                        new stream.Writable({
                          write(chunk, encoding, callback) {
                            pipelineChunks.push(chunk.toString());
                            callback();
                          }
                        })
                      );
                      const consumed = await streamConsumers.text(stream.Readable.from(["r", "m"]));
                      const streamOk =
                        stream === require("node:stream") &&
                        streamPromises === require("node:stream/promises") &&
                        streamConsumers === require("node:stream/consumers") &&
                        require("stream/web") === require("node:stream/web") &&
                        pipelineChunks.join("") === "st" &&
                        consumed === "rm" &&
                        typeof stream.Readable.from === "function" &&
                        typeof stream.Transform === "function";

                      const gz = zlib.gzipSync(Buffer.from("p13-stdlib"));
                      const zlibOk =
                        zlib === require("node:zlib") &&
                        zlib.gunzipSync(gz).toString("utf8") === "p13-stdlib" &&
                        zlib.inflateSync(zlib.deflateSync("inflate")).toString("utf8") === "inflate" &&
                        typeof zlib.createGzip === "function" &&
                        typeof zlib.constants.Z_NO_FLUSH === "number";

                      const bufferOk =
                        Buffer.from("p13").toString("base64") === "cDEz" &&
                        Buffer.isBuffer(transcode(Buffer.from("p13"), "utf8", "utf8")) &&
                        Buffer.byteLength("p13") === 3 &&
                        typeof globalThis.Buffer === "function";

                      const parsedUrl = new url.URL("https://example.test/a?b=1");
                      const pattern = new url.URLPattern({ pathname: "/a" });
                      const urlOk =
                        url === require("node:url") &&
                        parsedUrl.searchParams.get("b") === "1" &&
                        url.domainToASCII("b\u00fccher.example").includes("xn--") &&
                        url.fileURLToPath(url.pathToFileURL("safe.txt")).endsWith("safe.txt") &&
                        pattern.test("https://example.test/a");

                      const osSetPriorityDenied = denySummary(() => os.setPriority(1));
                      const osOk =
                        os === require("node:os") &&
                        typeof os.platform() === "string" &&
                        typeof os.arch() === "string" &&
                        typeof os.tmpdir() === "string" &&
                        Array.isArray(os.loadavg()) &&
                        os.networkInterfaces() &&
                        (osSetPriorityDenied === "true" || osSetPriorityDenied.endsWith("/true"));

                      const output = new stream.Writable({
                        write(chunk, encoding, callback) {
                          callback();
                        }
                      });
                      const rl = readlinePromises.createInterface({
                        input: stream.Readable.from(["answer\n"]),
                        output,
                        terminal: false
                      });
                      const answer = await rl.question("prompt");
                      rl.close();
                      const readlineOk =
                        readline === require("node:readline") &&
                        readlinePromises === require("node:readline/promises") &&
                        answer === "answer" &&
                        typeof readline.createInterface === "function" &&
                        typeof readline.emitKeypressEvents === "function";

                      const ttyOk =
                        tty === require("node:tty") &&
                        tty.isatty(1) === false &&
                        process.stdout.isTTY === false &&
                        process.stdout.getColorDepth() === 1 &&
                        process.stdout.hasColors() === false;

                      const eventLoopBefore = perfHooks.performance.eventLoopUtilization();
                      await new Promise((resolve) => setImmediate(resolve));
                      const eventLoopAfter = perfHooks.performance.eventLoopUtilization(eventLoopBefore);
                      const perfOk =
                        perfHooks === require("node:perf_hooks") &&
                        typeof perfHooks.performance.now() === "number" &&
                        typeof perfHooks.monitorEventLoopDelay === "undefined" &&
                        typeof eventLoopAfter.utilization === "number";

                      const als = new asyncHooks.AsyncLocalStorage();
                      const asyncValue = await als.run({ id: "ctx" }, async () => {
                        await Promise.resolve();
                        return als.getStore().id;
                      });
                      const resource = new asyncHooks.AsyncResource("p13-stdlib");
                      const resourceValue = resource.runInAsyncScope(() => als.getStore());
                      resource.emitDestroy();
                      const asyncOk =
                        asyncHooks === require("node:async_hooks") &&
                        asyncValue === "ctx" &&
                        resourceValue === undefined &&
                        typeof asyncHooks.createHook === "undefined" &&
                        typeof asyncHooks.executionAsyncId === "undefined";

                      const getBuiltinOk =
                        process.getBuiltinModule("crypto") === crypto &&
                        process.getBuiltinModule("node:crypto") === crypto &&
                        process.getBuiltinModule("fs") === require("fs") &&
                        process.getBuiltinModule("node:process") === require("node:process") &&
                        process.getBuiltinModule("left-pad") === undefined &&
                        process.getBuiltinModule("node:left-pad") === undefined;
                      const getBuiltinDenied = denySummary(() => process.getBuiltinModule("dgram"));
                      let getBuiltinInvalidArg = false;
                      try {
                        process.getBuiltinModule(42);
                      } catch (error) {
                        getBuiltinInvalidArg = error && error.code === "ERR_INVALID_ARG_TYPE";
                      }
                      const zlibPromisesDenied = requireDenied("zlib/promises");

                      console.log("p13stdlib.modules=" + [
                        cryptoOk,
                        streamOk,
                        zlibOk,
                        bufferOk,
                        urlOk,
                        osOk,
                        readlineOk,
                        ttyOk,
                        perfOk,
                        asyncOk,
                        getBuiltinOk,
                        getBuiltinDenied,
                        getBuiltinInvalidArg,
                        zlibPromisesDenied
                      ].join("|"));
                    })().catch((error) => {
                      console.error("p13stdlib.error=" + (error && (error.stack || error.message) || error));
                      process.exitCode = 1;
                    });
                """.trimIndent(),
                sourceName = "<node-stdlib-phase13-p13-43.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-43 stdlib conformance", result)
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13stdlib.profile=safe_subset_partial|true|safe_default,desktop_compat_opt_in|" +
                        "node24_safe_stdlib_subset|partial|partial|partial|partial|partial|" +
                        "partial|partial|partial|partial|partial|disabled|" +
                        "crypto,stream,stream/promises,stream/consumers,stream/web,zlib,buffer,url,os," +
                        "readline,readline/promises,tty,perf_hooks,async_hooks|" +
                        "bare_and_node_prefix|partial|builtin_precedence|controlled_partial|documented_stable|" +
                        "os.setPriority,real_network_interfaces,tty_raw_mode,zlib/promises,node:test/reporters|" +
                        "bounded_callbacks_streams_crypto_zlib|real_npm_phase9_30_fixture_baseline|" +
                        "modern_core_s7_36_partial|existing_v1_1_smokes_partial|false|false|false|false|true|true|true|true",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13stdlib.modules=true|true|true|true|true|true|true|true|true|true|true|" +
                        "${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.BUILTIN_DISABLED}/true|true|" +
                        "${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.BUILTIN_DISABLED}",
                ),
            )
            assertEquals(0, result.pendingCryptoCallbacks)
            assertEquals(0, result.pendingZlibCallbacks)
            assertEquals(false, result.pendingZlibStreams)
        } finally {
            workDir.deleteRecursively()
        }
    }
}
