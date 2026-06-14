package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeFsPromisesInstrumentationTest {

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
    fun scopedPromisesFileHandleStreamsAndFileUrlsWork() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node fs.promises focused regression")

        val workDir = File(context.cacheDir, "node-fs-promises-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create fs.promises work dir: $absolutePath" }
        }
        try {
            val shadow = File(workDir, "node_modules/fs/promises.js").apply {
                parentFile?.mkdirs()
                writeText("""module.exports = { shadow: true };""" + "\n", StandardCharsets.UTF_8)
            }
            val source = """
                "nodejs";
                const assert = require("assert/strict");
                const fs = require("fs");
                const fsp = require("fs/promises");
                const nodeFsp = require("node:fs/promises");
                const { fileURLToPath, pathToFileURL } = require("url");

                async function collect(readable) {
                  const chunks = [];
                  for await (const chunk of readable) {
                    chunks.push(Buffer.from(chunk));
                  }
                  return Buffer.concat(chunks).toString("utf8");
                }

                return (async () => {
                  assert.strictEqual(fs.promises, fsp);
                  assert.strictEqual(fsp, nodeFsp);
                  assert.strictEqual(fsp.shadow, undefined);
                  assert.strictEqual(require.resolve("node:fs/promises"), "fs/promises");

                  await fsp.mkdir("./data/sub", { recursive: true });
                  await fsp.writeFile("./data/sub/value.txt", "one", "utf8");
                  await fsp.appendFile("./data/sub/value.txt", "-two", "utf8");
                  assert.strictEqual(await fsp.readFile("./data/sub/value.txt", "utf8"), "one-two");

                  const stat = await fsp.stat("./data/sub/value.txt");
                  const lstat = await fsp.lstat("./data/sub/value.txt");
                  assert.strictEqual(stat.isFile(), true);
                  assert.strictEqual(lstat.isFile(), true);
                  await fsp.access("./data/sub/value.txt", fs.constants.R_OK);
                  assert.deepStrictEqual(await fsp.readdir("./data"), ["sub"]);

                  const handle = await fsp.open("./data/sub/value.txt", "r");
                  try {
                    assert.strictEqual(await handle.readFile("utf8"), "one-two");
                  } finally {
                    await handle.close();
                  }

                  assert.strictEqual(await collect(fs.createReadStream("./data/sub/value.txt")), "one-two");

                  const fileUrl = pathToFileURL("./data/sub/value.txt");
                  assert.strictEqual(await fsp.readFile(fileUrl, "utf8"), "one-two");
                  assert.ok(fileURLToPath(fileUrl).replace(/\\/g, "/").endsWith("/data/sub/value.txt"));

                  await Promise.all([
                    fsp.writeFile("./data/a.txt", "a", "utf8"),
                    fsp.writeFile("./data/b.txt", "b", "utf8"),
                    fsp.writeFile("./data/c.txt", "c", "utf8")
                  ]);
                  const values = await Promise.all([
                    fsp.readFile("./data/a.txt", "utf8"),
                    fsp.readFile("./data/b.txt", "utf8"),
                    fsp.readFile("./data/c.txt", "utf8")
                  ]);
                  assert.deepStrictEqual(values, ["a", "b", "c"]);

                  await fsp.unlink("./data/a.txt");
                  await fsp.rm("./data", { recursive: true, force: true });
                  console.log("fs.promises.focused.ready=true");
                })();
            """.trimIndent()

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-fs-promises-focused.js>",
                workingDirectory = workDir,
                moduleSources = mapOf(shadow.absolutePath to shadow.readText(StandardCharsets.UTF_8)),
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node fs.promises focused regression", result)
            assertTrue(
                NodeEmbeddedTestHarness.describeResult("missing fs.promises focused ready output", result),
                result.stdout.contains("fs.promises.focused.ready=true"),
            )
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun scopedPromisesRejectEscapesAndMissingFiles() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node fs.promises focused security regression")

        val workDir = File(context.cacheDir, "node-fs-promises-security-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create fs.promises security work dir: $absolutePath" }
        }
        val outsideDir = File(context.cacheDir, "node-fs-promises-outside-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create fs.promises outside dir: $absolutePath" }
        }
        try {
            File(outsideDir, "secret.txt").writeText("secret", StandardCharsets.UTF_8)
            val absoluteSecret = File(outsideDir, "secret.txt").absolutePath.replace("\\", "\\\\")
            val source = """
                "nodejs";
                const assert = require("assert/strict");
                const fsp = require("fs/promises");
                const { pathToFileURL } = require("url");

                async function rejects(label, action, expected) {
                  try {
                    await action();
                  } catch (error) {
                    const actual = error && (error.autojs6Code || error.code || error.name);
                    if (actual !== expected && error.code !== expected && error.legacyCode !== expected) {
                      throw new Error(label + " expected " + expected + " but got " + [
                        error && error.code,
                        error && error.autojs6Code,
                        error && error.legacyCode
                      ].filter(Boolean).join("/"));
                    }
                    console.log("fs.promises.security." + label + "=" + actual);
                    return;
                  }
                  throw new Error(label + " was unexpectedly allowed");
                }

                return (async () => {
                  await rejects("absolute", () => fsp.readFile("$absoluteSecret", "utf8"), "ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED");
                  await rejects("escape", () => fsp.readFile("../secret.txt", "utf8"), "ERR_AUTOJS6_FS_PATH_ESCAPE");
                  await rejects("nul", () => fsp.readFile("bad" + String.fromCharCode(0) + "path", "utf8"), "ERR_AUTOJS6_FS_NUL_BYTE");
                  await rejects("missing", () => fsp.readFile("./missing.txt", "utf8"), "ENOENT");
                  assert.throws(() => pathToFileURL("$absoluteSecret"), /absolute path|rejects absolute path|EPERM|scoped fs path escapes/);
                  console.log("fs.promises.security.ready=true");
                })();
            """.trimIndent()

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-fs-promises-security.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node fs.promises focused security regression", result)
            assertTrue(
                NodeEmbeddedTestHarness.describeResult("missing fs.promises security ready output", result),
                result.stdout.contains("fs.promises.security.ready=true"),
            )
        } finally {
            workDir.deleteRecursively()
            outsideDir.deleteRecursively()
        }
    }
}
