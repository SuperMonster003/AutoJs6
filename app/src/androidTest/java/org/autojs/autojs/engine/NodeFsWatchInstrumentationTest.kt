package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeFsWatchInstrumentationTest {

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
    fun watchAndPromisesWatchStayScoped() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node fs.watch focused regression")

        val workDir = File(context.cacheDir, "node-fs-watch-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create fs.watch work dir: $absolutePath" }
        }
        val outsideFile = File(context.cacheDir, "node-fs-watch-outside-${System.nanoTime()}.txt").apply {
            writeText("outside")
        }
        try {
            val absoluteOutside = outsideFile.absolutePath.replace("\\", "\\\\")
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const fs = require("fs");
                      const fsp = require("fs/promises");
                      const path = require("path");
                      const url = require("url");

                      function codeOf(error) {
                        if (error && error.code === "${NodeRuntimeErrorCodes.EMBEDDED_NODE_UNSUPPORTED_OPTION}") {
                          return error.code;
                        }
                        return error && (error.autojs6Code || error.code || error.name) || "";
                      }

                      function syncCode(action) {
                        try {
                          action();
                          return "allowed";
                        } catch (error) {
                          return codeOf(error);
                        }
                      }

                      function watchWithListener(pathValue, options, changeAction) {
                        if (typeof options === "function") {
                          changeAction = options;
                          options = { persistent: true };
                        }
                        return new Promise((resolve, reject) => {
                          let watcher;
                          const timeout = setTimeout(() => {
                            if (watcher) watcher.close();
                            reject(new Error("watch timeout: " + pathValue));
                          }, 2500);
                          watcher = fs.watch(pathValue, options, (eventType, filename) => {
                            clearTimeout(timeout);
                            watcher.close();
                            resolve({ eventType, filename });
                          });
                          setTimeout(changeAction, 120);
                        });
                      }

                      function watchWithEvent(pathValue, changeAction) {
                        return new Promise((resolve, reject) => {
                          const watcher = fs.watch(pathValue, { persistent: true });
                          const timeout = setTimeout(() => {
                            watcher.close();
                            reject(new Error("watch event timeout: " + pathValue));
                          }, 2500);
                          watcher.on("change", (eventType, filename) => {
                            clearTimeout(timeout);
                            watcher.close();
                            resolve({ eventType, filename });
                          });
                          setTimeout(changeAction, 120);
                        });
                      }

                      async function promisesWatchNext(pathValue, options, changeAction) {
                        if (typeof options === "function") {
                          changeAction = options;
                          options = { persistent: true };
                        }
                        const iterator = fsp.watch(pathValue, options);
                        const pending = iterator.next();
                        setTimeout(changeAction, 120);
                        const event = await pending;
                        await iterator.return();
                        return event.value;
                      }

                      function eventOk(event) {
                        return !!(
                          event &&
                          (event.eventType === "change" || event.eventType === "rename") &&
                          (event.filename === null || event.filename === undefined || String(event.filename).length >= 0)
                        );
                      }

                      fs.mkdirSync("./watch", { recursive: true });
                      fs.writeFileSync("./watch/file.txt", "a", "utf8");
                      fs.writeFileSync("./watch/event.txt", "a", "utf8");
                      fs.writeFileSync("./watch/url.txt", "a", "utf8");
                      fs.writeFileSync("./watch/buffer.txt", "a", "utf8");
                      fs.writeFileSync("./watch/promise.txt", "a", "utf8");
                      fs.writeFileSync("./watch/promise-buffer.txt", "a", "utf8");
                      fs.writeFileSync("./watch/abort.txt", "a", "utf8");
                      fs.writeFileSync("./watch/preabort.txt", "a", "utf8");

                      console.log("watch.types=" + [
                        typeof fs.watch,
                        typeof fs.watchFile,
                        typeof fs.unwatchFile,
                        typeof fsp.watch
                      ].join("|"));

                      const callbackEvent = await watchWithListener(
                        "./watch/file.txt",
                        () => fs.writeFileSync("./watch/file.txt", "abcd", "utf8")
                      );
                      const emitterEvent = await watchWithEvent(
                        "./watch/event.txt",
                        () => fs.writeFileSync("./watch/event.txt", "abcde", "utf8")
                      );
                      const bufferEvent = await watchWithListener(
                        "./watch",
                        { persistent: true, encoding: "buffer" },
                        () => fs.writeFileSync("./watch/new-buffer.txt", "buffer", "utf8")
                      );
                      const urlEvent = await watchWithListener(
                        url.pathToFileURL(path.resolve(process.cwd(), "./watch/url.txt")),
                        () => fs.writeFileSync("./watch/url.txt", "url", "utf8")
                      );
                      const pathBufferEvent = await watchWithListener(
                        Buffer.from("./watch/buffer.txt"),
                        () => fs.writeFileSync("./watch/buffer.txt", "path-buffer", "utf8")
                      );
                      const promiseEvent = await promisesWatchNext(
                        "./watch/promise.txt",
                        () => fs.writeFileSync("./watch/promise.txt", "promise", "utf8")
                      );
                      const promiseBufferEvent = await promisesWatchNext(
                        "./watch",
                        { persistent: true, encoding: "buffer", maxQueue: 4, overflow: "error" },
                        () => fs.writeFileSync("./watch/new-promise-buffer.txt", "promise-buffer", "utf8")
                      );

                      let abortClosed = false;
                      let preAbortClosed = false;
                      let promiseAbortRejected = false;
                      if (typeof AbortController === "function") {
                        const controller = new AbortController();
                        const abortWatcher = fs.watch("./watch/abort.txt", { signal: controller.signal }, () => {});
                        abortClosed = await new Promise((resolve) => {
                          const timeout = setTimeout(() => {
                            abortWatcher.close();
                            resolve(false);
                          }, 1000);
                          abortWatcher.on("close", () => {
                            clearTimeout(timeout);
                            resolve(true);
                          });
                          controller.abort();
                        });

                        const preController = new AbortController();
                        preController.abort();
                        const preAbortWatcher = fs.watch("./watch/preabort.txt", { signal: preController.signal }, null);
                        preAbortClosed = await new Promise((resolve) => {
                          const timeout = setTimeout(() => {
                            preAbortWatcher.close();
                            resolve(false);
                          }, 1000);
                          preAbortWatcher.on("close", () => {
                            clearTimeout(timeout);
                            resolve(true);
                          });
                        });

                        const promiseController = new AbortController();
                        const iterator = fsp.watch("./watch/promise.txt", { signal: promiseController.signal });
                        const pending = iterator.next().then(
                          () => false,
                          (error) => error && (error.name === "AbortError" || error.code === "ABORT_ERR")
                        );
                        promiseController.abort();
                        promiseAbortRejected = await pending;
                      }

                      const nulPath = "bad" + String.fromCharCode(0) + "path";
                      const denied = [
                        syncCode(() => fs.watch("../outside.txt", () => {})),
                        syncCode(() => fs.watch("$absoluteOutside", () => {})),
                        syncCode(() => fs.watch("./watch/missing.txt", () => {})),
                        syncCode(() => fs.watch("./watch/file.txt", { recursive: true }, () => {})),
                        syncCode(() => fs.watch("./watch/file.txt", "utf8", "bad")),
                        syncCode(() => fsp.watch("../outside.txt")),
                        syncCode(() => fsp.watch("$absoluteOutside")),
                        syncCode(() => fsp.watch("./watch/missing.txt")),
                        syncCode(() => fsp.watch(nulPath))
                      ];

                      console.log("watch.callback=" + eventOk(callbackEvent));
                      console.log("watch.emitter=" + eventOk(emitterEvent));
                      console.log("watch.options=" + (
                        eventOk(bufferEvent) &&
                        (bufferEvent.filename === null || Buffer.isBuffer(bufferEvent.filename))
                      ));
                      console.log("watch.pathlike=" + (eventOk(urlEvent) && eventOk(pathBufferEvent)));
                      console.log("watch.abort=" + [abortClosed, preAbortClosed, promiseAbortRejected].join("|"));
                      console.log("watch.promises=" + [
                        eventOk(promiseEvent),
                        eventOk(promiseBufferEvent),
                        promiseBufferEvent && (promiseBufferEvent.filename === null || Buffer.isBuffer(promiseBufferEvent.filename))
                      ].join("|"));
                      console.log("watch.denied=" + denied.join("|"));
                    })().catch((error) => {
                      console.error("watch.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-fs-watch-s8-30.js>",
                workingDirectory = workDir,
                timeoutMs = 15_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node fs.watch", result)
            assertTrue(result.stdout, result.stdout.contains("watch.types=function|function|function|function"))
            assertTrue(result.stdout, result.stdout.contains("watch.callback=true"))
            assertTrue(result.stdout, result.stdout.contains("watch.emitter=true"))
            assertTrue(result.stdout, result.stdout.contains("watch.options=true"))
            assertTrue(result.stdout, result.stdout.contains("watch.pathlike=true"))
            assertTrue(result.stdout, result.stdout.contains("watch.abort=true|true|true"))
            assertTrue(result.stdout, result.stdout.contains("watch.promises=true|true|true"))
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "watch.denied=${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}|${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}|ENOENT|${NodeRuntimeErrorCodes.EMBEDDED_NODE_UNSUPPORTED_OPTION}|ERR_INVALID_ARG_TYPE|${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}|${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}|ENOENT|${NodeRuntimeErrorCodes.FS_NUL_BYTE}",
                ),
            )
        } finally {
            workDir.deleteRecursively()
            outsideFile.delete()
        }
    }

    @Test
    fun watchFileAndUnwatchStayScoped() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node fs.watchFile focused regression")

        val workDir = File(context.cacheDir, "node-fs-watch-file-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create fs.watchFile work dir: $absolutePath" }
        }
        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const fs = require("fs");
                      const path = require("path");
                      const url = require("url");

                      function delay(ms) {
                        return new Promise((resolve) => setTimeout(resolve, ms));
                      }

                      function codeOf(error) {
                        return error && (error.autojs6Code || error.code || error.name) || "";
                      }

                      function syncCode(action) {
                        try {
                          action();
                          return "allowed";
                        } catch (error) {
                          return codeOf(error);
                        }
                      }

                      function watchOnce(pathValue, options, changeAction, accept) {
                        if (typeof options === "function") {
                          accept = changeAction;
                          changeAction = options;
                          options = undefined;
                        }
                        const watchOptions = Object.assign({ interval: 50 }, options || {});
                        return new Promise((resolve, reject) => {
                          const listener = (curr, prev) => {
                            if (accept && !accept(curr, prev)) {
                              return;
                            }
                            clearTimeout(timeout);
                            fs.unwatchFile(pathValue, listener);
                            resolve({ curr, prev });
                          };
                          const timeout = setTimeout(() => {
                            fs.unwatchFile(pathValue, listener);
                            reject(new Error("watchFile timeout: " + pathValue));
                          }, 2500);
                          fs.watchFile(pathValue, watchOptions, listener);
                          setTimeout(changeAction, 120);
                        });
                      }

                      fs.mkdirSync("./watch-file", { recursive: true });
                      fs.writeFileSync("./watch-file/file.txt", "a", "utf8");
                      fs.writeFileSync("./watch-file/url.txt", "a", "utf8");
                      fs.writeFileSync("./watch-file/buffer.txt", "a", "utf8");
                      fs.writeFileSync("./watch-file/bigint.txt", "a", "utf8");
                      fs.writeFileSync("./watch-file/cancel.txt", "a", "utf8");
                      fs.writeFileSync("./watch-file/multiple.txt", "a", "utf8");
                      fs.writeFileSync("./watch-file/nonfunction-unwatch.txt", "a", "utf8");
                      fs.writeFileSync("./watch-file/ref-unref.txt", "a", "utf8");

                      console.log("watchfile.types=" + [
                        typeof fs.watchFile,
                        typeof fs.unwatchFile,
                        typeof fs.watch
                      ].join("|"));

                      const existing = await watchOnce(
                        "./watch-file/file.txt",
                        () => fs.writeFileSync("./watch-file/file.txt", "abcd", "utf8"),
                        (curr) => curr.size === 4
                      );
                      const urlPathEvent = await watchOnce(
                        url.pathToFileURL(path.resolve(process.cwd(), "./watch-file/url.txt")),
                        () => fs.writeFileSync("./watch-file/url.txt", "url", "utf8"),
                        (curr) => curr.size === 3
                      );
                      const bufferPathEvent = await watchOnce(
                        Buffer.from("./watch-file/buffer.txt"),
                        () => fs.writeFileSync("./watch-file/buffer.txt", "buffer", "utf8"),
                        (curr) => curr.size === 6
                      );
                      const bigintEvent = await watchOnce(
                        "./watch-file/bigint.txt",
                        { interval: 50, bigint: true },
                        () => fs.writeFileSync("./watch-file/bigint.txt", "abcdef", "utf8"),
                        (curr) => typeof curr.size === "bigint" && String(curr.size) === "6"
                      );
                      const missing = await watchOnce(
                        "./watch-file/missing.txt",
                        () => fs.writeFileSync("./watch-file/missing.txt", "created", "utf8"),
                        (curr) => curr.size === 7
                      );

                      let cancelledCalled = false;
                      const cancelledListener = () => {
                        cancelledCalled = true;
                      };
                      fs.watchFile("./watch-file/cancel.txt", { interval: 50 }, cancelledListener);
                      fs.unwatchFile("./watch-file/cancel.txt", cancelledListener);
                      fs.writeFileSync("./watch-file/cancel.txt", "ab", "utf8");
                      await delay(180);

                      const multipleCalls = [];
                      const firstListener = () => multipleCalls.push("first");
                      const secondListener = () => multipleCalls.push("second");
                      fs.watchFile("./watch-file/multiple.txt", { interval: 50 }, firstListener);
                      fs.watchFile("./watch-file/multiple.txt", { interval: 50 }, secondListener);
                      fs.unwatchFile("./watch-file/multiple.txt", firstListener);
                      fs.writeFileSync("./watch-file/multiple.txt", "ab", "utf8");
                      await delay(280);
                      fs.unwatchFile("./watch-file/multiple.txt");

                      let nonFunctionUnwatchCalled = false;
                      fs.watchFile("./watch-file/nonfunction-unwatch.txt", { interval: 50 }, () => {
                        nonFunctionUnwatchCalled = true;
                      });
                      fs.unwatchFile("./watch-file/nonfunction-unwatch.txt", "bad");
                      fs.writeFileSync("./watch-file/nonfunction-unwatch.txt", "ab", "utf8");
                      await delay(200);
                      fs.unwatchFile("./watch-file/nonfunction-unwatch.txt");

                      let stoppedCalled = false;
                      const stopped = fs.watchFile("./watch-file/stop.txt", { interval: 50 }, () => {
                        stoppedCalled = true;
                      });
                      const stopType = typeof stopped.stop;
                      stopped.stop();
                      fs.writeFileSync("./watch-file/stop.txt", "stopped", "utf8");
                      await delay(180);

                      const refUnrefWatcher = fs.watchFile("./watch-file/ref-unref.txt", { interval: 50 }, () => {});
                      const refUnrefTypes = [
                        typeof refUnrefWatcher.ref,
                        typeof refUnrefWatcher.unref,
                        typeof refUnrefWatcher.stop
                      ].join(",");
                      const refUnrefReturns = refUnrefWatcher.unref() === refUnrefWatcher &&
                        refUnrefWatcher.ref() === refUnrefWatcher &&
                        refUnrefWatcher.unref() === refUnrefWatcher;
                      refUnrefWatcher.stop();

                      const denied = [
                        syncCode(() => fs.watchFile("../outside.txt", () => {})),
                        syncCode(() => fs.watchFile(Buffer.from("../outside-buffer-watch-file.txt"), () => {})),
                        syncCode(() => fs.unwatchFile("/sdcard/autojs6-watch-file.txt")),
                        syncCode(() => fs.watchFile("./watch-file/file.txt", { interval: 50 }, "bad"))
                      ];

                      console.log("watchfile.values=" + [
                        existing.prev.size + "/" + existing.curr.size,
                        urlPathEvent.prev.size === 1 && urlPathEvent.curr.size === 3 &&
                          bufferPathEvent.prev.size === 1 && bufferPathEvent.curr.size === 6,
                        typeof bigintEvent.prev.size + "/" + typeof bigintEvent.curr.size + "/" +
                          String(bigintEvent.prev.size) + "/" + String(bigintEvent.curr.size),
                        missing.prev.size + "/" + missing.curr.size,
                        cancelledCalled,
                        multipleCalls.join("|"),
                        nonFunctionUnwatchCalled,
                        stopType + "/" + stoppedCalled,
                        refUnrefTypes + "/" + refUnrefReturns
                      ].join("|"));
                      console.log("watchfile.denied=" + denied.join("|"));
                    })().catch((error) => {
                      console.error("watchfile.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-fs-watch-file-s8-30.js>",
                workingDirectory = workDir,
                timeoutMs = 15_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node fs.watchFile", result)
            assertTrue(result.stdout, result.stdout.contains("watchfile.types=function|function|function"))
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "watchfile.values=1/4|true|bigint/bigint/1/6|0/7|false|second|false|function/false|function,function,function/true",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "watchfile.denied=${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}|${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}|${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}|ERR_INVALID_ARG_TYPE",
                ),
            )
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun watchLimitsAndTimeoutCleanupStayBounded() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node fs.watch limit regression")

        val workDir = File(context.cacheDir, "node-fs-watch-limits-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create fs.watch limits work dir: $absolutePath" }
        }
        try {
            val limits = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const fs = require("fs");
                      const fsp = require("fs/promises");

                      function codeOf(error) {
                        return error && (error.autojs6Code || error.code || error.name) || "";
                      }

                      function syncCode(action) {
                        try {
                          action();
                          return "allowed";
                        } catch (error) {
                          return codeOf(error);
                        }
                      }

                      fs.mkdirSync("./limits", { recursive: true });
                      const watchers = [];
                      for (let index = 0; index < 16; index += 1) {
                        const file = "./limits/quota-" + index + ".txt";
                        fs.writeFileSync(file, String(index), "utf8");
                        watchers.push(fs.watch(file, { persistent: false }, () => {}));
                      }
                      fs.writeFileSync("./limits/extra-watch.txt", "extra", "utf8");
                      fs.writeFileSync("./limits/extra-promise.txt", "extra", "utf8");
                      const quotaWatch = syncCode(() => fs.watch("./limits/extra-watch.txt", { persistent: false }, () => {}));
                      const quotaWatchFile = syncCode(() => fs.watchFile("./limits/extra-watch-file.txt", { persistent: false }, () => {}));
                      const quotaPromise = syncCode(() => fsp.watch("./limits/extra-promise.txt", { persistent: false }));
                      for (const watcher of watchers) {
                        watcher.close();
                      }

                      fs.writeFileSync("./limits/rate.txt", "rate", "utf8");
                      const rateWatcher = fs.watch("./limits/rate.txt", { persistent: false }, () => {});
                      const rateCode = syncCode(() => {
                        for (let index = 0; index < 70; index += 1) {
                          rateWatcher.emit("change", "change", "rate.txt");
                        }
                      });
                      try {
                        rateWatcher.close();
                      } catch (_) {}

                      console.log("watch.limits=" + [
                        quotaWatch,
                        quotaWatchFile,
                        quotaPromise,
                        rateCode
                      ].join("|"));
                    })().catch((error) => {
                      console.error("watch.limits.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-fs-watch-limits-s8-30.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node fs.watch limits", limits)
            assertTrue(
                limits.stdout,
                limits.stdout.contains(
                    "watch.limits=${NodeRuntimeErrorCodes.FS_WATCH_LIMIT}|${NodeRuntimeErrorCodes.FS_WATCH_LIMIT}|${NodeRuntimeErrorCodes.FS_WATCH_LIMIT}|${NodeRuntimeErrorCodes.FS_WATCH_RATE_LIMITED}",
                ),
            )

            val timeout = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const fs = require("fs");
                    fs.writeFileSync("./timeout-watch.txt", "open", "utf8");
                    const watcher = fs.watch("./timeout-watch.txt", { persistent: true }, () => {});
                    console.log("watch.timeout.opened=" + (typeof watcher.close));
                    setInterval(() => {}, 50);
                """.trimIndent(),
                sourceName = "<node-fs-watch-timeout-s8-30.js>",
                workingDirectory = workDir,
                timeoutMs = 1_000L,
            )

            assertFalse(NodeEmbeddedTestHarness.describeResult("fs.watch timeout should fail", timeout), timeout.succeeded)
            assertTrue(NodeEmbeddedTestHarness.describeResult("fs.watch timeout should time out", timeout), timeout.timedOut)

            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

            val recovery = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const fs = require("fs");
                      fs.writeFileSync("./timeout-watch.txt", "recovered", "utf8");
                      const event = await new Promise((resolve, reject) => {
                        const watcher = fs.watch("./timeout-watch.txt", { persistent: true }, (eventType, filename) => {
                          clearTimeout(timeout);
                          watcher.close();
                          resolve({ eventType, filename });
                        });
                        const timeout = setTimeout(() => {
                          watcher.close();
                          reject(new Error("watch recovery timeout"));
                        }, 2500);
                        setTimeout(() => fs.writeFileSync("./timeout-watch.txt", "after", "utf8"), 120);
                      });
                      console.log("watch.timeout.recovered=" + (
                        event &&
                        (event.eventType === "change" || event.eventType === "rename") &&
                        fs.readFileSync("./timeout-watch.txt", "utf8")
                      ));
                    })();
                """.trimIndent(),
                sourceName = "<node-fs-watch-timeout-recovery-s8-30.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node fs.watch timeout recovery", recovery)
            assertTrue(recovery.stdout, recovery.stdout.contains("watch.timeout.recovered=after"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
