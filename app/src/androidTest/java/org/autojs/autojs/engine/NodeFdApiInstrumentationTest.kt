package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeFdApiInstrumentationTest {

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
    fun callbackAndSyncFdApisStayScopedAndOwned() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node fd API focused regression")

        val workDir = File(context.cacheDir, "node-fd-api-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create fd API work dir: $absolutePath" }
        }
        val outsideDir = File(context.cacheDir, "node-fd-api-outside-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create fd API outside dir: $absolutePath" }
        }
        try {
            val absoluteOutside = File(outsideDir, "secret.txt").absolutePath.replace("\\", "\\\\")
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const assert = require("assert/strict");
                      const fs = require("fs");
                      const nodeFs = require("node:fs");

                      function codeOf(error) {
                        return error && (error.autojs6Code || error.code || error.name) || "";
                      }

                      function ownedMessage(error) {
                        return String(error && error.message || error).includes("not open or not owned");
                      }

                      function syncCode(action) {
                        try {
                          action();
                          return "allowed";
                        } catch (error) {
                          return codeOf(error) + ":" + ownedMessage(error);
                        }
                      }

                      function callbackCode(action) {
                        return new Promise((resolve) => {
                          action((error) => {
                            resolve(error ? codeOf(error) + ":" + ownedMessage(error) : "allowed");
                          });
                        });
                      }

                      function callbackOpen(pathValue, flags) {
                        return new Promise((resolve, reject) => {
                          fs.open(pathValue, flags, (error, fd) => error ? reject(error) : resolve(fd));
                        });
                      }

                      function callbackClose(fd) {
                        return new Promise((resolve, reject) => {
                          fs.close(fd, (error) => error ? reject(error) : resolve());
                        });
                      }

                      function callbackRead(fd, buffer, offset, length, position) {
                        return new Promise((resolve, reject) => {
                          fs.read(fd, buffer, offset, length, position, (error, bytesRead, returnedBuffer) => {
                            error ? reject(error) : resolve({ bytesRead, buffer: returnedBuffer });
                          });
                        });
                      }

                      function callbackWrite(fd, data) {
                        return new Promise((resolve, reject) => {
                          fs.write(fd, data, (error, bytesWritten, returnedData) => {
                            error ? reject(error) : resolve({ bytesWritten, data: returnedData });
                          });
                        });
                      }

                      function callbackFstat(fd) {
                        return new Promise((resolve, reject) => {
                          fs.fstat(fd, (error, stat) => error ? reject(error) : resolve(stat));
                        });
                      }

                      function callbackFtruncate(fd, len) {
                        return new Promise((resolve, reject) => {
                          fs.ftruncate(fd, len, (error) => error ? reject(error) : resolve());
                        });
                      }

                      await fs.promises.mkdir("./fd", { recursive: true });
                      fs.writeFileSync("./fd/source.txt", "abcdef", "utf8");

                      const syncReadFd = fs.openSync("./fd/source.txt", "r");
                      const syncReadBuffer = Buffer.alloc(3);
                      const syncReadBytes = fs.readSync(syncReadFd, syncReadBuffer, 0, 3, 1);
                      const syncReadStat = fs.fstatSync(syncReadFd);
                      fs.closeSync(syncReadFd);
                      const syncReadAfterClose = syncCode(() => fs.readSync(syncReadFd, Buffer.alloc(1), 0, 1, 0));

                      const syncWriteFd = fs.openSync("./fd/sync-out.txt", "w+");
                      const syncWriteBytes = fs.writeSync(syncWriteFd, Buffer.from("HELLO"));
                      const syncWriteStringBytes = fs.writeSync(syncWriteFd, "!", 3, "utf8");
                      fs.ftruncateSync(syncWriteFd, 4);
                      const syncWriteStat = fs.fstatSync(syncWriteFd);
                      fs.closeSync(syncWriteFd);

                      const syncOptionFd = fs.openSync("./fd/source.txt", "r");
                      const syncOptionBuffer = Buffer.alloc(4);
                      const syncOptionBytes = fs.readSync(syncOptionFd, syncOptionBuffer, {
                        offset: 1,
                        length: 2,
                        position: 2
                      });
                      fs.closeSync(syncOptionFd);

                      const callbackWriteFd = await callbackOpen("./fd/callback.txt", "w+");
                      const callbackWritten = await callbackWrite(callbackWriteFd, "callback");
                      await callbackFtruncate(callbackWriteFd, 4);
                      const callbackWriteStat = await callbackFstat(callbackWriteFd);
                      await callbackClose(callbackWriteFd);

                      const callbackReadFd = await callbackOpen("./fd/callback.txt", "r");
                      const callbackBuffer = Buffer.alloc(4);
                      const callbackReadResult = await callbackRead(callbackReadFd, callbackBuffer, 0, 4, 0);
                      await callbackClose(callbackReadFd);

                      const closedFd = fs.openSync("./fd/closed.txt", "w");
                      fs.closeSync(closedFd);

                      const denied = [
                        syncCode(() => fs.openSync("../outside.txt", "w")),
                        syncCode(() => fs.openSync("$absoluteOutside", "r")),
                        syncCode(() => fs.readSync(987654321, Buffer.alloc(1), 0, 1, 0)),
                        syncCode(() => fs.writeSync(987654321, "x")),
                        syncCode(() => fs.fstatSync(987654321)),
                        syncCode(() => fs.ftruncateSync(987654321, 0)),
                        syncCode(() => fs.closeSync(987654321)),
                        syncCode(() => fs.fstatSync(closedFd)),
                        await callbackCode((callback) => fs.open("../outside-callback.txt", "w", callback)),
                        await callbackCode((callback) => fs.open("$absoluteOutside", "r", callback)),
                        await callbackCode((callback) => fs.read(987654321, Buffer.alloc(1), 0, 1, 0, callback)),
                        await callbackCode((callback) => fs.write(987654321, "x", callback)),
                        await callbackCode((callback) => fs.fstat(987654321, callback)),
                        await callbackCode((callback) => fs.ftruncate(987654321, 0, callback)),
                        await callbackCode((callback) => fs.close(987654321, callback))
                      ];

                      assert.equal(syncReadBytes, 3);
                      assert.equal(syncReadBuffer.toString("utf8"), "bcd");
                      assert.equal(syncReadStat.isFile(), true);
                      assert.equal(syncWriteBytes, 5);
                      assert.equal(syncWriteStringBytes, 1);
                      assert.equal(fs.readFileSync("./fd/sync-out.txt", "utf8"), "HEL!");
                      assert.equal(syncWriteStat.isFile(), true);
                      assert.equal(syncOptionBytes, 2);
                      assert.equal(syncOptionBuffer.slice(1, 3).toString("utf8"), "cd");
                      assert.equal(callbackWritten.bytesWritten, 8);
                      assert.equal(callbackWriteStat.size, 4);
                      assert.equal(callbackReadResult.bytesRead, 4);
                      assert.equal(callbackReadResult.buffer.toString("utf8"), "call");

                      console.log("fdapi.alias=" + (fs === nodeFs));
                      console.log("fdapi.types=" + [
                        typeof fs.open,
                        typeof fs.openSync,
                        typeof fs.close,
                        typeof fs.closeSync,
                        typeof fs.read,
                        typeof fs.readSync,
                        typeof fs.write,
                        typeof fs.writeSync,
                        typeof fs.fstat,
                        typeof fs.fstatSync,
                        typeof fs.ftruncate,
                        typeof fs.ftruncateSync
                      ].join("|"));
                      console.log("fdapi.sync=" + [
                        syncReadBytes,
                        syncReadBuffer.toString("utf8"),
                        syncReadStat.isFile(),
                        syncWriteBytes,
                        syncWriteStringBytes,
                        fs.readFileSync("./fd/sync-out.txt", "utf8"),
                        syncWriteStat.isFile(),
                        syncOptionBytes,
                        syncOptionBuffer.slice(1, 3).toString("utf8"),
                        syncReadAfterClose
                      ].join("|"));
                      console.log("fdapi.callback=" + [
                        callbackWritten.bytesWritten,
                        callbackWritten.data,
                        callbackWriteStat.size,
                        callbackReadResult.bytesRead,
                        callbackReadResult.buffer.toString("utf8")
                      ].join("|"));
                      console.log("fdapi.denied=" + denied.join("|"));
                    })().catch((error) => {
                      console.error("fdapi.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-fd-api-s8-28.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node fd API", result)
            assertTrue(result.stdout, result.stdout.contains("fdapi.alias=true"))
            assertTrue(result.stdout, result.stdout.contains("fdapi.types=function|function|function|function|function|function|function|function|function|function|function|function"))
            assertTrue(result.stdout, result.stdout.contains("fdapi.sync=3|bcd|true|5|1|HEL!|true|2|cd|EBADF:true"))
            assertTrue(result.stdout, result.stdout.contains("fdapi.callback=8|callback|4|4|call"))
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "fdapi.denied=ERR_AUTOJS6_FS_PATH_ESCAPE:false|ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED:false|EBADF:true|EBADF:true|EBADF:true|EBADF:true|EBADF:true|EBADF:true|ERR_AUTOJS6_FS_PATH_ESCAPE:false|ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED:false|EBADF:true|EBADF:true|EBADF:true|EBADF:true|EBADF:true",
                ),
            )
        } finally {
            workDir.deleteRecursively()
            outsideDir.deleteRecursively()
        }
    }

    @Test
    fun unclosedSyncFdDoesNotPoisonNextExecutionAfterTimeout() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node sync fd timeout recovery")

        val workDir = File(context.cacheDir, "node-fd-api-timeout-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create fd API timeout work dir: $absolutePath" }
        }
        try {
            val timeout = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const fs = require("fs");
                    const fd = fs.openSync("./leaked.txt", "w+");
                    fs.writeSync(fd, "leaked");
                    console.log("fdapi.timeout.opened=" + (typeof fd));
                    setInterval(() => {}, 50);
                """.trimIndent(),
                sourceName = "<node-fd-api-timeout-s8-28.js>",
                workingDirectory = workDir,
                timeoutMs = 1_000L,
            )

            assertFalse(NodeEmbeddedTestHarness.describeResult("fd API timeout should fail", timeout), timeout.succeeded)
            assertTrue(NodeEmbeddedTestHarness.describeResult("fd API timeout should time out", timeout), timeout.timedOut)

            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

            val recovery = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const fs = require("fs");
                    const text = fs.readFileSync("./leaked.txt", "utf8");
                    const fd = fs.openSync("./after-timeout.txt", "w+");
                    fs.writeSync(fd, "ok");
                    fs.closeSync(fd);
                    console.log("fdapi.timeout.recovered=" + text + "|" + fs.readFileSync("./after-timeout.txt", "utf8"));
                """.trimIndent(),
                sourceName = "<node-fd-api-timeout-recovery-s8-28.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node sync fd timeout recovery", recovery)
            assertTrue(recovery.stdout, recovery.stdout.contains("fdapi.timeout.recovered=leaked|ok"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
