package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeStreamMvpInstrumentationTest {

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
    fun streamPrimitivesPipelineFinishedAndShadowingWork() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node stream MVP focused regression")

        val workDir = File(context.cacheDir, "node-stream-mvp-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create stream MVP work dir: $absolutePath" }
        }
        try {
            File(workDir, "node_modules/stream/index.js").apply {
                parentFile?.mkdirs()
                writeText("""module.exports = { shadow: true };""" + "\n", StandardCharsets.UTF_8)
            }
            val source = """
                "nodejs";
                const assert = require("assert/strict");
                const stream = require("stream");
                const nodeStream = require("node:stream");
                const streamPromises = require("stream/promises");
                const nodeStreamPromises = require("node:stream/promises");
                const streamWeb = require("stream/web");

                async function collect(readable) {
                  const chunks = [];
                  for await (const chunk of readable) {
                    chunks.push(Buffer.from(chunk));
                  }
                  return Buffer.concat(chunks).toString("utf8");
                }

                return (async () => {
                  assert.strictEqual(stream, nodeStream);
                  assert.strictEqual(streamPromises, nodeStreamPromises);
                  assert.strictEqual(stream.shadow, undefined);
                  assert.strictEqual(require.resolve("node:stream"), "stream");

                  assert.strictEqual(await collect(stream.Readable.from(["a", "b"])), "ab");

                  const pass = new stream.PassThrough();
                  pass.end("pass");
                  assert.strictEqual(await collect(pass), "pass");

                  const upper = new stream.Transform({
                    transform(chunk, encoding, callback) {
                      callback(null, String(chunk).toUpperCase());
                    }
                  });
                  assert.strictEqual(await collect(stream.Readable.from(["a", "b"]).pipe(upper)), "AB");

                  const written = [];
                  const writable = new stream.Writable({
                    write(chunk, encoding, callback) {
                      written.push(Buffer.from(chunk).toString("utf8"));
                      callback();
                    }
                  });
                  await streamPromises.pipeline(
                    stream.Readable.from(["x", "y"]),
                    new stream.Transform({
                      transform(chunk, encoding, callback) {
                        callback(null, String(chunk).toUpperCase());
                      }
                    }),
                    writable
                  );
                  assert.deepStrictEqual(written, ["X", "Y"]);

                  const callbackPipelineText = await new Promise((resolve, reject) => {
                    const callbackChunks = [];
                    stream.pipeline(
                      stream.Readable.from(["c", "b"]),
                      new stream.Transform({
                        transform(chunk, encoding, callback) {
                          callback(null, String(chunk).toUpperCase());
                        }
                      }),
                      new stream.Writable({
                        write(chunk, encoding, callback) {
                          callbackChunks.push(Buffer.from(chunk).toString("utf8"));
                          callback();
                        }
                      }),
                      (error) => error ? reject(error) : resolve(callbackChunks.join(""))
                    );
                  });
                  assert.strictEqual(callbackPipelineText, "CB");

                  await assert.rejects(
                    () => streamPromises.pipeline(
                      stream.Readable.from(["boom"]),
                      new stream.Transform({
                        transform(chunk, encoding, callback) {
                          callback(new Error("stream boom"));
                        }
                      }),
                      new stream.Writable({
                        write(chunk, encoding, callback) {
                          callback();
                        }
                      })
                    ),
                    /stream boom/
                  );

                  await assert.rejects(
                    () => new Promise((resolve, reject) => {
                      stream.pipeline(
                        stream.Readable.from(["callback-boom"]),
                        new stream.Transform({
                          transform(chunk, encoding, callback) {
                            callback(new Error("callback stream boom"));
                          }
                        }),
                        new stream.Writable({
                          write(chunk, encoding, callback) {
                            callback();
                          }
                        }),
                        (error) => error ? reject(error) : resolve()
                      );
                    }),
                    /callback stream boom/
                  );

                  const finishedPass = new stream.PassThrough();
                  const finishedPromise = streamPromises.finished(finishedPass);
                  finishedPass.end("done");
                  finishedPass.resume();
                  await finishedPromise;

                  await new Promise((resolve, reject) => {
                    const callbackPass = new stream.PassThrough();
                    stream.finished(callbackPass, (error) => error ? reject(error) : resolve());
                    callbackPass.end("callback");
                    callbackPass.resume();
                  });

                  assert.strictEqual(typeof stream.Readable.toWeb, "function");
                  assert.strictEqual(typeof stream.Readable.fromWeb, "function");
                  assert.strictEqual(typeof stream.Writable.toWeb, "function");
                  assert.strictEqual(typeof stream.Writable.fromWeb, "function");
                  const webReadable = stream.Readable.toWeb(stream.Readable.from(["web-adapter"]));
                  assert.strictEqual(await collect(stream.Readable.fromWeb(webReadable)), "web-adapter");
                  const webWritableChunks = [];
                  const nodeWritableFromWeb = stream.Writable.fromWeb(new streamWeb.WritableStream({
                    write(chunk) {
                      webWritableChunks.push(Buffer.from(chunk).toString("utf8"));
                    }
                  }));
                  nodeWritableFromWeb.end("web-writable");
                  await streamPromises.finished(nodeWritableFromWeb);
                  assert.deepStrictEqual(webWritableChunks, ["web-writable"]);

                  let inFlight = 0;
                  let maxInFlight = 0;
                  let backpressureChunks = 0;
                  await streamPromises.pipeline(
                    stream.Readable.from(Array.from({ length: 32 }, (_, index) => Buffer.from(String(index) + ";"))),
                    new stream.Writable({
                      highWaterMark: 1,
                      write(chunk, encoding, callback) {
                        inFlight += 1;
                        maxInFlight = Math.max(maxInFlight, inFlight);
                        backpressureChunks += 1;
                        setImmediate(() => {
                          inFlight -= 1;
                          callback();
                        });
                      }
                    })
                  );
                  assert.strictEqual(backpressureChunks, 32);
                  assert.ok(maxInFlight <= 1);

                  let destroyPushed = false;
                  const destroySource = new stream.Readable({
                    read() {
                      if (destroyPushed) return;
                      destroyPushed = true;
                      this.push("destroy");
                    }
                  });
                  const destroySink = new stream.Writable({
                    write(chunk, encoding, callback) {
                      callback(new Error("sink destroy propagation"));
                    }
                  });
                  await assert.rejects(
                    () => streamPromises.pipeline(destroySource, destroySink),
                    /sink destroy propagation/
                  );
                  assert.strictEqual(stream.isDestroyed(destroySource), true);
                  assert.strictEqual(stream.isDestroyed(destroySink), true);

                  console.log("stream.mvp.focused.ready=true");
                })();
            """.trimIndent()

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-stream-mvp-focused.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node stream MVP focused regression", result)
            assertTrue(
                NodeEmbeddedTestHarness.describeResult("missing stream MVP ready output", result),
                result.stdout.contains("stream.mvp.focused.ready=true"),
            )
        } finally {
            workDir.deleteRecursively()
        }
    }
}
