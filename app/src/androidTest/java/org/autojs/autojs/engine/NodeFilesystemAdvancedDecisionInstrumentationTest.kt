package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeFilesystemAdvancedDecisionInstrumentationTest {

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
    fun advancedFsProfileAndCoreSurfacesStayScopedAndOwned() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-33 advanced filesystem decision regression")

        val workDir = File(context.cacheDir, "node-filesystem-advanced-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create advanced filesystem work dir: $absolutePath" }
        }
        try {
            File(workDir, "advanced.txt").writeText("abcdef", StandardCharsets.UTF_8)

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const fs = require("fs");
                      const fsp = require("fs/promises");
                      const profile = require("autojs6:profile");

                      function readStream(pathValue) {
                        return new Promise((resolve, reject) => {
                          let text = "";
                          const readable = fs.createReadStream(pathValue, { encoding: "utf8" });
                          readable.on("data", (chunk) => { text += chunk; });
                          readable.once("error", reject);
                          readable.once("close", () => resolve(text));
                        });
                      }

                      function writeStream(pathValue, text) {
                        return new Promise((resolve, reject) => {
                          const writable = fs.createWriteStream(pathValue, { encoding: "utf8" });
                          writable.once("error", reject);
                          writable.once("close", resolve);
                          writable.end(text);
                        });
                      }

                      const fsProfile = profile.filesystemProfile || {};
                      const advanced = fsProfile.advancedApis || {};
                      const limits = fsProfile.limits || {};
                      console.log("p13fsadvanced.profile=" + [
                        advanced.streams,
                        advanced.fileHandle,
                        advanced.fd,
                        advanced.opendir,
                        advanced.watch,
                        advanced.realpath,
                        advanced.recursiveWatch,
                        advanced.closeOnDestroy,
                        advanced.packagedBehavior,
                        advanced.rawFd,
                        limits.watcherLimit,
                        limits.watchEventLimit,
                        limits.watchEventWindowMs,
                        Object.isFrozen(advanced),
                        Object.isFrozen(limits)
                      ].join("|"));

                      const streamText = await readStream("./advanced.txt");
                      await writeStream("./stream-output.txt", "stream:" + streamText);

                      const fileHandle = await fsp.open("./advanced.txt", "r");
                      const fileHandleText = await fileHandle.readFile("utf8");
                      await fileHandle.close();

                      const fd = fs.openSync("./advanced.txt", "r");
                      const fdBuffer = Buffer.alloc(3);
                      const fdRead = fs.readSync(fd, fdBuffer, 0, 3, 1);
                      fs.closeSync(fd);

                      const dir = fs.opendirSync(".");
                      const dirEntries = [];
                      for (;;) {
                        const entry = dir.readSync();
                        if (entry === null) break;
                        dirEntries.push(String(entry.name));
                      }
                      dir.closeSync();

                      const watcher = fs.watch("./advanced.txt", { persistent: false }, () => {});
                      const watcherSurface = [typeof watcher.close, typeof watcher.ref, typeof watcher.unref].join("/");
                      watcher.close();

                      const watchFile = fs.watchFile("./advanced.txt", { interval: 100 }, () => {});
                      const watchFileSurface = [typeof watchFile.ref, typeof watchFile.unref, typeof watchFile.stop].join("/");
                      watchFile.stop();

                      const realpath = fs.realpathSync("./advanced.txt");
                      const nativeRealpath = fs.realpathSync.native("./advanced.txt");
                      const streamOutput = fs.readFileSync("./stream-output.txt", "utf8");

                      console.log("p13fsadvanced.surface=" + [
                        streamText,
                        streamOutput,
                        fileHandleText,
                        fdRead + ":" + fdBuffer.toString("utf8"),
                        dirEntries.includes("advanced.txt") && dirEntries.includes("stream-output.txt"),
                        watcherSurface,
                        watchFileSurface,
                        /advanced\.txt$/.test(String(realpath).replace(/\\/g, "/")),
                        /advanced\.txt$/.test(String(nativeRealpath).replace(/\\/g, "/"))
                      ].join("|"));
                      console.log("p13fsadvanced.ready=true");
                    })().catch((error) => {
                      console.error("p13fsadvanced.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-filesystem-advanced-decision-p13-33.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-33 advanced filesystem decision", result)
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13fsadvanced.profile=partial|partial|partial|partial|partial|partial|unsupported|" +
                        "execution_owned|partial|false|16|64|1000|true|true",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13fsadvanced.surface=abcdef|stream:abcdef|abcdef|3:bcd|true|" +
                        "function/function/function|function/function/function|true|true",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("p13fsadvanced.ready=true"))
            assertEquals(0, result.pendingScopedFsStreamCount)
            assertEquals(0, result.pendingScopedFsWatchCount)
            assertEquals(0, result.pendingScopedFsWatchFileCount)
        } finally {
            workDir.deleteRecursively()
        }
    }
}
