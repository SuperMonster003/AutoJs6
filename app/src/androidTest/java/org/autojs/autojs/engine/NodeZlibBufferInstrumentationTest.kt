package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeZlibBufferInstrumentationTest {

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
    fun zlibBufferCallbacksStreamsAndShadowingWork() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node zlib focused regression")

        val workDir = File(context.cacheDir, "node-zlib-buffer-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create zlib work dir: $absolutePath" }
        }
        try {
            File(workDir, "node_modules/zlib/index.js").apply {
                parentFile?.mkdirs()
                writeText("""module.exports = { shadow: true };""" + "\n", StandardCharsets.UTF_8)
            }
            val source = """
                "nodejs";
                const assert = require("assert/strict");
                const fs = require("fs");
                const zlib = require("zlib");
                const nodeZlib = require("node:zlib");
                const stream = require("stream");
                const { pipeline } = require("stream/promises");
                const { promisify } = require("util");

                async function collect(chunks, transforms) {
                  const values = [];
                  const writable = new stream.Writable({
                    write(chunk, encoding, callback) {
                      values.push(Buffer.from(chunk));
                      callback();
                    }
                  });
                  await pipeline(stream.Readable.from(chunks), ...transforms, writable);
                  return Buffer.concat(values).toString("utf8");
                }

                async function collectError(chunks, transforms) {
                  try {
                    await collect(chunks, transforms);
                    return "ok";
                  } catch (error) {
                    return error instanceof Error ? "error" : "other";
                  }
                }

                function rejectsBuiltin(name) {
                  try {
                    require(name);
                  } catch (error) {
                    return error && error.autojs6Code === "ERR_AUTOJS6_BUILTIN_DISABLED";
                  }
                  return false;
                }

                return (async () => {
                  assert.strictEqual(zlib, nodeZlib);
                  assert.strictEqual(zlib.shadow, undefined);
                  assert.strictEqual(require.resolve("node:zlib"), "zlib");
                  assert.strictEqual(rejectsBuiltin("zlib/promises"), true);
                  assert.strictEqual(rejectsBuiltin("node:zlib/promises"), true);

                  const gzip = zlib.gzipSync(Buffer.from("gzip-value"));
                  assert.strictEqual(zlib.gunzipSync(gzip).toString("utf8"), "gzip-value");
                  const largePayload = Buffer.alloc(3 * 1024 * 1024, 65);
                  assert.strictEqual(zlib.gunzipSync(zlib.gzipSync(largePayload)).length, largePayload.length);

                  const deflated = zlib.deflateSync(Buffer.from("deflate-value"));
                  assert.strictEqual(zlib.inflateSync(deflated).toString("utf8"), "deflate-value");

                  const raw = zlib.deflateRawSync(Buffer.from("raw-value"));
                  assert.strictEqual(zlib.inflateRawSync(raw).toString("utf8"), "raw-value");

                  const callbackGzip = await promisify(zlib.gzip)(Buffer.from("callback-gzip"));
                  assert.strictEqual(zlib.gunzipSync(callbackGzip).toString("utf8"), "callback-gzip");

                  const brotli = await promisify(zlib.brotliCompress)(Buffer.from("brotli-value"), {
                    params: { [zlib.constants.BROTLI_PARAM_QUALITY]: 1 }
                  });
                  assert.strictEqual(zlib.brotliDecompressSync(brotli).toString("utf8"), "brotli-value");

                  assert.strictEqual(
                    await collect([Buffer.from("stream-value")], [zlib.createGzip(), zlib.createGunzip()]),
                    "stream-value"
                  );
                  fs.writeFileSync("./stream-source.txt", "fs-pipeline-value", "utf8");
                  await pipeline(
                    fs.createReadStream("./stream-source.txt"),
                    zlib.createGzip(),
                    fs.createWriteStream("./stream-source.txt.gz")
                  );
                  assert.strictEqual(
                    zlib.gunzipSync(fs.readFileSync("./stream-source.txt.gz")).toString("utf8"),
                    "fs-pipeline-value"
                  );
                  assert.strictEqual(
                    await collectError([Buffer.from("not gzip")], [zlib.createGunzip()]),
                    "error"
                  );
                  assert.throws(() => zlib.gunzipSync(Buffer.from("not gzip")), /incorrect header|invalid/i);
                  assert.strictEqual(Object.isFrozen(zlib.constants), true);

                  console.log("zlib.buffer.focused.ready=true");
                })();
            """.trimIndent()

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-zlib-buffer-focused.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node zlib focused regression", result)
            assertTrue(
                NodeEmbeddedTestHarness.describeResult("missing zlib focused ready output", result),
                result.stdout.contains("zlib.buffer.focused.ready=true"),
            )
            assertEquals(0, result.pendingZlibCallbacks ?: -1)
            assertEquals(false, result.pendingZlibStreams)
            assertEquals(0, result.pendingZlibStreamCount ?: -1)
        } finally {
            workDir.deleteRecursively()
        }
    }
}
