package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeFileHandleAdvancedInstrumentationTest {

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
    fun fileHandleCoreSurfaceStaysScopedAndOwned() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node FileHandle advanced focused regression")

        val workDir = File(context.cacheDir, "node-filehandle-advanced-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create FileHandle advanced work dir: $absolutePath" }
        }
        val outsideDir = File(context.cacheDir, "node-filehandle-outside-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create FileHandle outside dir: $absolutePath" }
        }
        try {
            val absoluteOutside = File(outsideDir, "secret.txt").absolutePath.replace("\\", "\\\\")
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const assert = require("assert/strict");
                      const fs = require("fs");
                      const fsp = require("fs/promises");
                      const nodeFsp = require("node:fs/promises");

                      function codeOf(error) {
                        return error && (error.autojs6Code || error.code || error.name) || "";
                      }

                      async function promiseCode(action) {
                        try {
                          await action();
                          return "allowed";
                        } catch (error) {
                          return codeOf(error) + ":" + String(error && error.message || error).includes("not open or not owned");
                        }
                      }

                      function syncCode(action) {
                        try {
                          action();
                          return "allowed";
                        } catch (error) {
                          return codeOf(error) + ":" + String(error && error.message || error).includes("not open or not owned");
                        }
                      }

                      await fsp.mkdir("./fh", { recursive: true });
                      await fsp.writeFile("./fh/source.txt", "abcdef", "utf8");

                      const readHandle = await fsp.open("./fh/source.txt", "r");
                      const readBuffer = Buffer.alloc(3);
                      const readResult = await readHandle.read(readBuffer, 0, readBuffer.length, 1);
                      const wholeText = await readHandle.readFile("utf8");
                      const readStat = await readHandle.stat();
                      await readHandle.close();
                      const readAfterClose = await promiseCode(() => readHandle.stat());

                      const promiseReadHandle = await fsp.open("./fh/source.txt", "r");
                      const readFileViaPromise = await fsp.readFile(promiseReadHandle, { encoding: "utf8" });
                      await promiseReadHandle.close();

                      const writeHandle = await fsp.open("./fh/out.txt", "w+");
                      const writeResult = await writeHandle.write(Buffer.from("HELLO"), 0, 5, 0);
                      await writeHandle.writeFile("hello");
                      await writeHandle.appendFile("-world");
                      await writeHandle.truncate(7);
                      await writeHandle.datasync();
                      await writeHandle.sync();
                      const writeStat = await writeHandle.stat();
                      const numericFd = writeHandle.fd;
                      await writeHandle.close();
                      const syncAfterClose = syncCode(() => fs.fstatSync(numericFd));

                      const appendHandle = await fsp.open("./fh/append.txt", "w+");
                      await appendHandle.writeFile("one");
                      await fsp.appendFile(appendHandle, "-two", { encoding: "utf8" });
                      await appendHandle.close();

                      const denied = [
                        await promiseCode(() => fsp.open("../outside.txt", "w")),
                        await promiseCode(() => fsp.open("$absoluteOutside", "r")),
                        await promiseCode(() => fsp.readFile(987654321, "utf8")),
                        await promiseCode(() => readHandle.read(Buffer.alloc(1), 0, 1, 0)),
                        syncCode(() => fs.readSync(987654321, Buffer.alloc(1), 0, 1, 0))
                      ];

                      const outText = await fsp.readFile("./fh/out.txt", "utf8");
                      const appendText = await fsp.readFile("./fh/append.txt", "utf8");
                      assert.equal(readResult.bytesRead, 3);
                      assert.equal(readBuffer.toString("utf8"), "bcd");
                      assert.equal(readFileViaPromise, "abcdef");
                      assert.equal(outText, "hello-w");
                      assert.equal(appendText, "one-two");

                      console.log("filehandle.alias=" + (fsp === nodeFsp && fs.promises === fsp));
                      console.log("filehandle.types=" + [
                        typeof fsp.open,
                        typeof readHandle.read,
                        typeof readHandle.write,
                        typeof readHandle.readFile,
                        typeof readHandle.writeFile,
                        typeof readHandle.appendFile,
                        typeof readHandle.truncate,
                        typeof readHandle.datasync,
                        typeof readHandle.sync,
                        typeof readHandle.stat,
                        typeof readHandle.close
                      ].join("|"));
                      console.log("filehandle.read=" + [
                        readResult.bytesRead,
                        readBuffer.toString("utf8"),
                        wholeText,
                        readStat.isFile(),
                        readFileViaPromise,
                        readAfterClose
                      ].join("|"));
                      console.log("filehandle.write=" + [
                        writeResult.bytesWritten,
                        writeResult.buffer.toString("utf8"),
                        outText,
                        writeStat.isFile(),
                        syncAfterClose,
                        appendText
                      ].join("|"));
                      console.log("filehandle.denied=" + denied.join("|"));
                    })().catch((error) => {
                      console.error("filehandle.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-filehandle-advanced-s8-27.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node FileHandle advanced", result)
            assertTrue(result.stdout, result.stdout.contains("filehandle.alias=true"))
            assertTrue(result.stdout, result.stdout.contains("filehandle.types=function|function|function|function|function|function|function|function|function|function|function"))
            assertTrue(result.stdout, result.stdout.contains("filehandle.read=3|bcd|abcdef|true|abcdef|EBADF:true"))
            assertTrue(result.stdout, result.stdout.contains("filehandle.write=5|HELLO|hello-w|true|EBADF:true|one-two"))
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "filehandle.denied=ERR_AUTOJS6_FS_PATH_ESCAPE:false|ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED:false|ERR_INVALID_ARG_TYPE:false|EBADF:true|EBADF:true",
                ),
            )
        } finally {
            workDir.deleteRecursively()
            outsideDir.deleteRecursively()
        }
    }

    @Test
    fun unclosedFileHandleDoesNotPoisonNextExecutionAfterTimeout() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node FileHandle timeout recovery")

        val workDir = File(context.cacheDir, "node-filehandle-timeout-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create FileHandle timeout work dir: $absolutePath" }
        }
        try {
            val timeout = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const fsp = require("fs/promises");
                      const handle = await fsp.open("./leaked.txt", "w+");
                      await handle.writeFile("leaked");
                      console.log("filehandle.timeout.opened=" + (typeof handle.close));
                      setInterval(() => {}, 50);
                    })();
                """.trimIndent(),
                sourceName = "<node-filehandle-timeout-s8-27.js>",
                workingDirectory = workDir,
                timeoutMs = 1_000L,
            )

            assertFalse(NodeEmbeddedTestHarness.describeResult("FileHandle timeout should fail", timeout), timeout.succeeded)
            assertTrue(NodeEmbeddedTestHarness.describeResult("FileHandle timeout should time out", timeout), timeout.timedOut)

            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

            val recovery = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const fsp = require("fs/promises");
                      const handle = await fsp.open("./leaked.txt", "r+");
                      const text = await handle.readFile("utf8");
                      await handle.close();
                      await fsp.writeFile("./after-timeout.txt", "ok", "utf8");
                      console.log("filehandle.timeout.recovered=" + text + "|" + await fsp.readFile("./after-timeout.txt", "utf8"));
                    })();
                """.trimIndent(),
                sourceName = "<node-filehandle-timeout-recovery-s8-27.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node FileHandle timeout recovery", recovery)
            assertTrue(recovery.stdout, recovery.stdout.contains("filehandle.timeout.recovered=leaked|ok"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
