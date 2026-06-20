package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeOpendirInstrumentationTest {

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
    fun opendirDirAndDirentSurfaceStaysScoped() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node opendir focused regression")

        val workDir = File(context.cacheDir, "node-opendir-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create opendir work dir: $absolutePath" }
        }
        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const assert = require("assert/strict");
                      const fs = require("fs");
                      const fsp = require("fs/promises");

                      function callbackOpendir(pathValue, options) {
                        return new Promise((resolve, reject) => {
                          fs.opendir(pathValue, options, (error, dir) => error ? reject(error) : resolve(dir));
                        });
                      }

                      function callbackRead(dir) {
                        return new Promise((resolve, reject) => {
                          dir.read((error, entry) => error ? reject(error) : resolve(entry));
                        });
                      }

                      function callbackClose(dir) {
                        return new Promise((resolve, reject) => {
                          dir.close((error) => error ? reject(error) : resolve());
                        });
                      }

                      function codeOf(error) {
                        if (error && error.code === "ERR_DIR_CLOSED") {
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

                      async function promiseCode(action) {
                        try {
                          await action();
                          return "allowed";
                        } catch (error) {
                          return codeOf(error);
                        }
                      }

                      function entryNames(entries) {
                        return entries.map((entry) => String(entry.name)).sort().join("|");
                      }

                      function entrySummary(entries) {
                        return entries.map((entry) => [
                          String(entry.name),
                          entry.isDirectory(),
                          entry.isFile(),
                          typeof entry.path,
                          typeof entry.parentPath
                        ].join(":")).sort().join("|");
                      }

                      function recursiveSummary(entries) {
                        return entries.map((entry) => {
                          const parent = String(entry.parentPath || entry.path || ".")
                            .replace(/\\/g, "/")
                            .replace(/^\.\//, "")
                            .replace(/\/$/, "");
                          const name = String(entry.name);
                          const full = (parent ? parent + "/" : "") + name;
                          return [full, entry.isDirectory(), entry.isFile(), entry.isSymbolicLink()].join(":");
                        }).sort().join("|");
                      }

                      async function readAllAsync(dir) {
                        const entries = [];
                        for (;;) {
                          const entry = await dir.read();
                          if (entry === null) break;
                          entries.push(entry);
                        }
                        return entries;
                      }

                      function readAllSync(dir) {
                        const entries = [];
                        for (;;) {
                          const entry = dir.readSync();
                          if (entry === null) break;
                          entries.push(entry);
                        }
                        return entries;
                      }

                      async function readAllCallback(dir) {
                        const entries = [];
                        for (;;) {
                          const entry = await callbackRead(dir);
                          if (entry === null) break;
                          entries.push(entry);
                        }
                        return entries;
                      }

                      fs.mkdirSync("./walk/sub", { recursive: true });
                      fs.writeFileSync("./walk/a.txt", "a", "utf8");
                      fs.writeFileSync("./walk/sub/b.js", "b", "utf8");

                      console.log("opendir.types=" + [
                        typeof fs.opendir,
                        typeof fs.opendirSync,
                        typeof fsp.opendir
                      ].join("|"));

                      const syncDir = fs.opendirSync("./walk", { bufferSize: 1 });
                      const syncEntries = readAllSync(syncDir);
                      syncDir.closeSync();
                      const syncClosed = syncCode(() => syncDir.readSync());
                      assert.equal(entryNames(syncEntries), "a.txt|sub");

                      const callbackDir = await callbackOpendir("./walk", { bufferSize: 1 });
                      const callbackEntries = await readAllCallback(callbackDir);
                      await callbackClose(callbackDir);
                      const callbackClosed = await new Promise((resolve) => {
                        callbackDir.read((error) => resolve(error ? codeOf(error) : "allowed"));
                      });
                      assert.equal(entryNames(callbackEntries), "a.txt|sub");

                      const promiseDir = await fsp.opendir("./walk");
                      const first = await promiseDir.read();
                      const rest = await readAllAsync(promiseDir);
                      await promiseDir.close();
                      const promiseClosed = await promiseCode(() => promiseDir.read());

                      const iterDir = await fsp.opendir("./walk");
                      const iterEntries = [];
                      for await (const entry of iterDir) {
                        iterEntries.push(entry);
                      }
                      const iterClosed = await promiseCode(() => iterDir.read());

                      const recursiveSync = recursiveSummary(readAllSync(fs.opendirSync("./walk", { recursive: true, bufferSize: 1 })));
                      const recursivePromiseDir = await fsp.opendir(Buffer.from("./walk"), { recursive: true, bufferSize: 1 });
                      const recursivePromiseEntries = [];
                      for await (const entry of recursivePromiseDir) {
                        recursivePromiseEntries.push(entry);
                      }
                      const recursivePromise = recursiveSummary(recursivePromiseEntries);
                      const recursiveExpected = "walk/a.txt:false:true:false|walk/sub/b.js:false:true:false|walk/sub:true:false:false";

                      const bufferDir = await fsp.opendir("./walk", { encoding: "buffer" });
                      const bufferEntry = await bufferDir.read();
                      await bufferDir.close();

                      console.log("opendir.sync=" + [
                        entrySummary(syncEntries),
                        syncClosed
                      ].join("|"));
                      console.log("opendir.callback=" + [
                        entrySummary(callbackEntries),
                        callbackClosed
                      ].join("|"));
                      console.log("opendir.promise=" + [
                        first && typeof first.isFile,
                        entryNames([first].concat(rest)),
                        promiseClosed
                      ].join("|"));
                      console.log("opendir.iter=" + [
                        entryNames(iterEntries),
                        iterClosed
                      ].join("|"));
                      console.log("opendir.recursive=" + [
                        recursiveSync === recursiveExpected,
                        recursivePromise === recursiveExpected,
                        recursiveSync
                      ].join("|"));
                      console.log("opendir.buffer=" + [
                        Buffer.isBuffer(bufferEntry && bufferEntry.name),
                        ["a.txt", "sub"].includes(String(bufferEntry && bufferEntry.name && bufferEntry.name.toString("utf8")))
                      ].join("|"));
                    })().catch((error) => {
                      console.error("opendir.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-opendir-s8-29.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node opendir", result)
            assertTrue(result.stdout, result.stdout.contains("opendir.types=function|function|function"))
            assertTrue(result.stdout, result.stdout.contains("opendir.sync=a.txt:false:true:string:string|sub:true:false:string:string|ERR_DIR_CLOSED"))
            assertTrue(result.stdout, result.stdout.contains("opendir.callback=a.txt:false:true:string:string|sub:true:false:string:string|ERR_DIR_CLOSED"))
            assertTrue(result.stdout, result.stdout.contains("opendir.promise=function|a.txt|sub|ERR_DIR_CLOSED"))
            assertTrue(result.stdout, result.stdout.contains("opendir.iter=a.txt|sub|ERR_DIR_CLOSED"))
            assertTrue(result.stdout, result.stdout.contains("opendir.recursive=true|true|walk/a.txt:false:true:false|walk/sub/b.js:false:true:false|walk/sub:true:false:false"))
            assertTrue(result.stdout, result.stdout.contains("opendir.buffer=true|true"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun opendirRejectsUnsafePathsAndClosedDirs() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node opendir security regression")

        val workDir = File(context.cacheDir, "node-opendir-security-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create opendir security work dir: $absolutePath" }
        }
        val outsideDir = File(context.cacheDir, "node-opendir-outside-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create opendir outside dir: $absolutePath" }
        }
        try {
            File(outsideDir, "secret.txt").writeText("secret")
            val absoluteOutside = outsideDir.absolutePath.replace("\\", "\\\\")
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const fs = require("fs");
                      const fsp = require("fs/promises");

                      function codeOf(error) {
                        if (error && error.code === "ERR_DIR_CLOSED") {
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

                      async function promiseCode(action) {
                        try {
                          await action();
                          return "allowed";
                        } catch (error) {
                          return codeOf(error);
                        }
                      }

                      function callbackCode(action) {
                        return new Promise((resolve) => {
                          action((error) => resolve(error ? codeOf(error) : "allowed"));
                        });
                      }

                      fs.mkdirSync("./safe", { recursive: true });
                      fs.writeFileSync("./safe/file.txt", "x", "utf8");
                      const closed = fs.opendirSync("./safe");
                      closed.closeSync();
                      const nulPath = "bad" + String.fromCharCode(0) + "path";

                      const denied = [
                        syncCode(() => fs.opendirSync("../outside")),
                        syncCode(() => fs.opendirSync("$absoluteOutside")),
                        syncCode(() => fs.opendirSync("./safe/file.txt")),
                        syncCode(() => fs.opendirSync("./missing")),
                        syncCode(() => fs.opendirSync(nulPath)),
                        syncCode(() => fs.opendir("./safe")),
                        syncCode(() => closed.readSync()),
                        await callbackCode((callback) => fs.opendir("../outside", callback)),
                        await callbackCode((callback) => fs.opendir("$absoluteOutside", callback)),
                        await promiseCode(() => fsp.opendir("../outside")),
                        await promiseCode(() => fsp.opendir("$absoluteOutside")),
                        await promiseCode(() => fsp.opendir("./safe/file.txt")),
                        await promiseCode(() => fsp.opendir("./missing")),
                        await promiseCode(() => fsp.opendir(nulPath)),
                        await promiseCode(() => fsp.opendir("../outside", { recursive: true }))
                      ];

                      console.log("opendir.denied=" + denied.join("|"));
                    })().catch((error) => {
                      console.error("opendir.security.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-opendir-security-s8-29.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node opendir security", result)
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "opendir.denied=ERR_AUTOJS6_FS_PATH_ESCAPE|ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED|ENOTDIR|ENOENT|ERR_AUTOJS6_FS_NUL_BYTE|ERR_INVALID_ARG_TYPE|ERR_DIR_CLOSED|ERR_AUTOJS6_FS_PATH_ESCAPE|ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED|ERR_AUTOJS6_FS_PATH_ESCAPE|ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED|ENOTDIR|ENOENT|ERR_AUTOJS6_FS_NUL_BYTE|ERR_AUTOJS6_FS_PATH_ESCAPE",
                ),
            )
        } finally {
            workDir.deleteRecursively()
            outsideDir.deleteRecursively()
        }
    }
}
