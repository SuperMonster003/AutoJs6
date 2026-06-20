package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeSafeFileOperationsInstrumentationTest {

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
    fun cpAndMkdtempStayScoped() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node safe file operations cp/mkdtemp regression")

        val workDir = File(context.cacheDir, "node-safe-file-ops-cp-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create safe file operations cp work dir: $absolutePath" }
        }
        val outsideFile = File(context.cacheDir, "node-safe-file-ops-cp-outside-${System.nanoTime()}.txt").apply {
            writeText("outside")
        }
        try {
            val absoluteOutside = outsideFile.absolutePath.replace("\\", "\\\\")
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const assert = require("assert/strict");
                      const fs = require("fs");
                      const nodeFs = require("node:fs");
                      const fsp = require("fs/promises");
                      const nodeFsp = require("node:fs/promises");
                      const path = require("path");
                      const url = require("url");

                      function codesOf(error) {
                        return [error && error.autojs6Code, error && error.code, error && error.name].filter(Boolean);
                      }

                      function assertAnyCode(label, error, expectedCodes) {
                        const expected = Array.isArray(expectedCodes) ? expectedCodes : [expectedCodes];
                        const actual = codesOf(error);
                        assert.ok(
                          expected.some((code) => actual.includes(code)),
                          label + " expected one of " + expected.join("/") + " but got " + actual.join("/")
                        );
                      }

                      function expectSyncCodes(label, action, expectedCodes) {
                        try {
                          action();
                        } catch (error) {
                          assertAnyCode(label, error, expectedCodes);
                          return codesOf(error).join("/");
                        }
                        throw new Error(label + " was unexpectedly allowed");
                      }

                      async function expectPromiseCodes(label, action, expectedCodes) {
                        try {
                          await action();
                        } catch (error) {
                          assertAnyCode(label, error, expectedCodes);
                          return codesOf(error).join("/");
                        }
                        throw new Error(label + " was unexpectedly allowed");
                      }

                      function callbackCp(source, destination, options) {
                        return new Promise((resolve, reject) => {
                          fs.cp(source, destination, options, (error) => error ? reject(error) : resolve());
                        });
                      }

                      function callbackMkdtemp(prefix, options) {
                        return new Promise((resolve, reject) => {
                          const done = (error, directory) => error ? reject(error) : resolve(directory);
                          if (arguments.length >= 2) {
                            fs.mkdtemp(prefix, options, done);
                          } else {
                            fs.mkdtemp(prefix, done);
                          }
                        });
                      }

                      assert.strictEqual(fs, nodeFs);
                      assert.strictEqual(fsp, nodeFsp);
                      assert.strictEqual(fs.promises, fsp);
                      assert.deepStrictEqual([
                        typeof fs.cpSync,
                        typeof fs.cp,
                        typeof fsp.cp,
                        typeof fs.mkdtempSync,
                        typeof fs.mkdtemp,
                        typeof fsp.mkdtemp,
                        typeof fs.mkdtempDisposableSync,
                        typeof fsp.mkdtempDisposable
                      ], [
                        "function",
                        "function",
                        "function",
                        "function",
                        "function",
                        "function",
                        "function",
                        "function"
                      ]);

                      fs.writeFileSync("./src.txt", "sync", "utf8");
                      fs.cpSync("./src.txt", "./out/sync.txt", { recursive: true });
                      await fsp.cp("./src.txt", "./out/promise.txt", { recursive: true });
                      await callbackCp("./src.txt", "./out/callback.txt", { recursive: true });
                      assert.strictEqual(fs.readFileSync("./out/sync.txt", "utf8"), "sync");
                      assert.strictEqual(await fsp.readFile("./out/promise.txt", "utf8"), "sync");
                      assert.strictEqual(fs.readFileSync("./out/callback.txt", "utf8"), "sync");

                      const payload = Buffer.from([1, 2, 3, 4]);
                      fs.mkdirSync("./tree/nested", { recursive: true });
                      fs.mkdirSync("./tree/empty", { recursive: true });
                      fs.writeFileSync("./tree/a.txt", "a", "utf8");
                      fs.writeFileSync("./tree/nested/b.bin", payload);
                      await fsp.cp("./tree", "./copy/tree", { recursive: true });
                      assert.strictEqual(fs.readFileSync("./copy/tree/a.txt", "utf8"), "a");
                      assert.strictEqual(fs.statSync("./copy/tree/empty").isDirectory(), true);
                      assert.strictEqual(fs.readFileSync("./copy/tree/nested/b.bin").equals(payload), true);

                      await fsp.cp(
                        url.pathToFileURL(path.resolve(process.cwd(), "./src.txt")),
                        url.pathToFileURL(path.resolve(process.cwd(), "./out/url.txt")),
                        { recursive: true }
                      );
                      await callbackCp("./src.txt", url.pathToFileURL(path.resolve(process.cwd(), "./out/url-callback.txt")), {
                        recursive: true
                      });
                      assert.strictEqual(fs.readFileSync("./out/url.txt", "utf8"), "sync");
                      assert.strictEqual(fs.readFileSync("./out/url-callback.txt", "utf8"), "sync");

                      fs.mkdirSync("./as-dir", { recursive: true });
                      fs.cpSync("./src.txt", "./as-dir", { recursive: true });
                      assert.strictEqual(fs.readFileSync("./as-dir/src.txt", "utf8"), "sync");

                      fs.writeFileSync("./overwrite.txt", "old", "utf8");
                      fs.cpSync("./src.txt", "./overwrite.txt", { force: true });
                      assert.strictEqual(fs.readFileSync("./overwrite.txt", "utf8"), "sync");
                      fs.writeFileSync("./skip.txt", "old", "utf8");
                      fs.cpSync("./src.txt", "./skip.txt", { force: false });
                      assert.strictEqual(fs.readFileSync("./skip.txt", "utf8"), "old");

                      fs.mkdirSync("./filter-src/nested", { recursive: true });
                      fs.mkdirSync("./filter-src/skip-dir", { recursive: true });
                      fs.writeFileSync("./filter-src/keep.txt", "keep", "utf8");
                      fs.writeFileSync("./filter-src/skip.tmp", "skip", "utf8");
                      fs.writeFileSync("./filter-src/nested/keep.md", "nested", "utf8");
                      fs.writeFileSync("./filter-src/skip-dir/drop.txt", "drop", "utf8");
                      await fsp.cp("./filter-src", "./filter-out", {
                        recursive: true,
                        filter(source) {
                          return !source.endsWith(".tmp") && !source.includes("skip-dir");
                        }
                      });
                      assert.strictEqual(fs.readFileSync("./filter-out/keep.txt", "utf8"), "keep");
                      assert.strictEqual(fs.readFileSync("./filter-out/nested/keep.md", "utf8"), "nested");
                      assert.strictEqual(fs.existsSync("./filter-out/skip.tmp"), false);
                      assert.strictEqual(fs.existsSync("./filter-out/skip-dir/drop.txt"), false);

                      const syncTemp = fs.mkdtempSync("./tmp-");
                      const promiseTemp = await fsp.mkdtemp("./tmp-");
                      const callbackTemp = await callbackMkdtemp("./tmp-", "utf8");
                      const bufferTemp = fs.mkdtempSync(Buffer.from("./buffer-"));
                      const urlTemp = await fsp.mkdtemp(url.pathToFileURL(path.resolve(process.cwd(), "./url-")));
                      assert.notStrictEqual(syncTemp, promiseTemp);
                      assert.notStrictEqual(promiseTemp, callbackTemp);
                      [syncTemp, promiseTemp, callbackTemp, bufferTemp, urlTemp].forEach((entry) => {
                        assert.strictEqual(fs.statSync(entry).isDirectory(), true);
                      });

                      const syncDisposable = fs.mkdtempDisposableSync("./dispose-sync-");
                      const syncDisposablePath = syncDisposable.path;
                      fs.writeFileSync(path.join(syncDisposablePath, "a.txt"), "sync", "utf8");
                      assert.strictEqual(Object.isFrozen(syncDisposable), true);
                      assert.strictEqual(syncDisposable[Symbol.dispose], syncDisposable.remove);
                      syncDisposable.remove();
                      assert.strictEqual(fs.existsSync(syncDisposablePath), false);

                      const promiseDisposable = await fsp.mkdtempDisposable("./dispose-promise-");
                      const promiseDisposablePath = promiseDisposable.path;
                      fs.writeFileSync(path.join(promiseDisposablePath, "a.txt"), "promise", "utf8");
                      assert.strictEqual(Object.isFrozen(promiseDisposable), true);
                      assert.strictEqual(promiseDisposable[Symbol.asyncDispose], promiseDisposable.remove);
                      await promiseDisposable.remove();
                      assert.strictEqual(fs.existsSync(promiseDisposablePath), false);

                      const nulPath = "bad" + String.fromCharCode(0) + "path";
                      expectSyncCodes("cp dir no recursive", () => fs.cpSync("./tree", "./no-recursive"), ["EISDIR"]);
                      expectSyncCodes("cp errorOnExist", () => fs.cpSync("./src.txt", "./overwrite.txt", { errorOnExist: true }), ["EEXIST"]);
                      expectSyncCodes("cp source escape", () => fs.cpSync("../outside.txt", "./x", { recursive: true }), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"]);
                      expectSyncCodes("cp dest escape", () => fs.cpSync("./src.txt", "../outside-dest.txt", { recursive: true }), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"]);
                      expectSyncCodes("cp absolute outside", () => fs.cpSync("$absoluteOutside", "./x", { recursive: true }), ["${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}"]);
                      expectSyncCodes("cp self child", () => fs.cpSync("./tree", "./tree/nested/self", { recursive: true }), ["${NodeRuntimeErrorCodes.FS_SCOPED_PATH}", "EPERM"]);
                      expectSyncCodes("cp invalid filter", () => fs.cpSync("./tree", "./filter-copy", { recursive: true, filter: true }), ["ERR_INVALID_ARG_TYPE"]);
                      expectSyncCodes("cp async filter", () => fs.cpSync("./tree", "./filter-copy", {
                        recursive: true,
                        filter() {
                          return Promise.resolve(true);
                        }
                      }), ["${NodeRuntimeErrorCodes.EMBEDDED_NODE_UNSUPPORTED_OPTION}"]);
                      await expectPromiseCodes("cp missing", () => fsp.cp("./missing", "./x", { recursive: true }), ["ENOENT"]);
                      await expectPromiseCodes("cp promise dest escape", () => fsp.cp("./src.txt", "../outside-promise.txt", { recursive: true }), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"]);

                      expectSyncCodes("mkdtemp escape", () => fs.mkdtempSync("../tmp-"), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"]);
                      expectSyncCodes("mkdtemp nul", () => fs.mkdtempSync(nulPath), ["${NodeRuntimeErrorCodes.FS_NUL_BYTE}", "ERR_INVALID_ARG_TYPE"]);
                      await expectPromiseCodes("mkdtemp missing parent", () => fsp.mkdtemp("./missing/tmp-"), ["ENOENT"]);
                      expectSyncCodes("mkdtemp disposable escape", () => fs.mkdtempDisposableSync("../tmp-"), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"]);
                      await expectPromiseCodes("mkdtemp disposable promise escape", () => fsp.mkdtempDisposable("../tmp-"), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"]);

                      console.log("safe.file.ops.cp_mkdtemp.ready=true");
                    })().catch((error) => {
                      console.error("safe.file.ops.cp_mkdtemp.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-safe-file-ops-cp-mkdtemp-s8-31.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node safe file operations cp/mkdtemp regression", result)
            assertTrue(
                NodeEmbeddedTestHarness.describeResult("missing cp/mkdtemp ready output", result),
                result.stdout.contains("safe.file.ops.cp_mkdtemp.ready=true"),
            )
            assertFalse(File(workDir.parentFile, "outside-dest.txt").exists())
            assertFalse(File(workDir.parentFile, "outside-promise.txt").exists())
        } finally {
            outsideFile.delete()
            workDir.deleteRecursively()
        }
    }

    @Test
    fun statfsAndGlobStayScoped() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node safe file operations statfs/glob regression")

        val workDir = File(context.cacheDir, "node-safe-file-ops-statfs-glob-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create safe file operations statfs/glob work dir: $absolutePath" }
        }
        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const assert = require("assert/strict");
                      const fs = require("fs");
                      const nodeFs = require("node:fs");
                      const fsp = require("fs/promises");
                      const nodeFsp = require("node:fs/promises");
                      const path = require("path");
                      const url = require("url");

                      function codesOf(error) {
                        return [error && error.autojs6Code, error && error.code, error && error.name].filter(Boolean);
                      }

                      function assertAnyCode(label, error, expectedCodes) {
                        const expected = Array.isArray(expectedCodes) ? expectedCodes : [expectedCodes];
                        const actual = codesOf(error);
                        assert.ok(
                          expected.some((code) => actual.includes(code)),
                          label + " expected one of " + expected.join("/") + " but got " + actual.join("/")
                        );
                      }

                      function expectSyncCodes(label, action, expectedCodes) {
                        try {
                          action();
                        } catch (error) {
                          assertAnyCode(label, error, expectedCodes);
                          return;
                        }
                        throw new Error(label + " was unexpectedly allowed");
                      }

                      async function expectPromiseCodes(label, action, expectedCodes) {
                        try {
                          await action();
                        } catch (error) {
                          assertAnyCode(label, error, expectedCodes);
                          return;
                        }
                        throw new Error(label + " was unexpectedly allowed");
                      }

                      function statfsCallback(pathValue, options) {
                        return new Promise((resolve, reject) => {
                          const done = (error, stats) => error ? reject(error) : resolve(stats);
                          if (arguments.length >= 2) {
                            fs.statfs(pathValue, options, done);
                          } else {
                            fs.statfs(pathValue, done);
                          }
                        });
                      }

                      function globCallback(pattern, options) {
                        return new Promise((resolve, reject) => {
                          const done = (error, matches) => error ? reject(error) : resolve(matches);
                          if (arguments.length >= 2) {
                            fs.glob(pattern, options, done);
                          } else {
                            fs.glob(pattern, done);
                          }
                        });
                      }

                      async function expectCallbackCodes(label, action, expectedCodes) {
                        await new Promise((resolve, reject) => {
                          action((error) => {
                            try {
                              assert.ok(error, label + " was unexpectedly allowed");
                              assertAnyCode(label, error, expectedCodes);
                              resolve();
                            } catch (assertionError) {
                              reject(assertionError);
                            }
                          });
                        });
                      }

                      async function collect(iterator) {
                        const values = [];
                        for await (const value of iterator) {
                          values.push(value);
                        }
                        return values;
                      }

                      function normalizeList(values) {
                        return values.map((value) => String(value).replace(/\\/g, "/")).sort();
                      }

                      function numberStatfs(value) {
                        return !!(
                          value &&
                          typeof value.type === "number" &&
                          typeof value.bsize === "number" &&
                          typeof value.blocks === "number" &&
                          typeof value.bfree === "number" &&
                          typeof value.bavail === "number" &&
                          typeof value.files === "number" &&
                          typeof value.ffree === "number" &&
                          value.bsize > 0
                        );
                      }

                      function bigintStatfs(value) {
                        return !!(
                          value &&
                          typeof value.type === "bigint" &&
                          typeof value.bsize === "bigint" &&
                          typeof value.blocks === "bigint" &&
                          typeof value.bfree === "bigint" &&
                          typeof value.bavail === "bigint" &&
                          typeof value.files === "bigint" &&
                          typeof value.ffree === "bigint" &&
                          value.bsize > 0n
                        );
                      }

                      assert.strictEqual(fs, nodeFs);
                      assert.strictEqual(fsp, nodeFsp);
                      assert.strictEqual(fs.promises, fsp);
                      assert.deepStrictEqual([
                        typeof fs.statfsSync,
                        typeof fs.statfs,
                        typeof fsp.statfs,
                        typeof fs.globSync,
                        typeof fs.glob,
                        typeof fsp.glob
                      ], ["function", "function", "function", "function", "function", "function"]);

                      fs.mkdirSync("./statfs/sub", { recursive: true });
                      fs.writeFileSync("./statfs/sub/a.txt", "x", "utf8");
                      const syncInfo = fs.statfsSync("./statfs/sub");
                      const syncFileInfo = fs.statfsSync("./statfs/sub/a.txt");
                      const callbackInfo = await statfsCallback(Buffer.from("./statfs/sub"));
                      const callbackBigInfo = await statfsCallback("./statfs/sub", { bigint: true });
                      const promiseInfo = await fsp.statfs(url.pathToFileURL(path.resolve(process.cwd(), "./statfs/sub")));
                      const promiseBigInfo = await fsp.statfs("./statfs/sub", { bigint: true });
                      assert.strictEqual([syncInfo, syncFileInfo, callbackInfo, promiseInfo].every(numberStatfs), true);
                      assert.strictEqual(bigintStatfs(callbackBigInfo), true);
                      assert.strictEqual(bigintStatfs(promiseBigInfo), true);

                      const workspace = "./glob/workspace";
                      fs.mkdirSync(workspace + "/src/a", { recursive: true });
                      fs.mkdirSync(workspace + "/src/b", { recursive: true });
                      fs.mkdirSync(workspace + "/src/c", { recursive: true });
                      fs.mkdirSync(workspace + "/lib", { recursive: true });
                      fs.writeFileSync(workspace + "/src/a/index.js", "a", "utf8");
                      fs.writeFileSync(workspace + "/src/a/index.test.js", "test", "utf8");
                      fs.writeFileSync(workspace + "/src/a/readme.md", "readme", "utf8");
                      fs.writeFileSync(workspace + "/src/b/main.js", "b", "utf8");
                      fs.writeFileSync(workspace + "/src/b/util.cjs", "cjs", "utf8");
                      fs.writeFileSync(workspace + "/src/c/skip.js", "skip", "utf8");
                      fs.writeFileSync(workspace + "/lib/tool.js", "tool", "utf8");

                      assert.deepStrictEqual(
                        normalizeList(fs.globSync("src/**/*.js", { cwd: workspace })),
                        ["src/a/index.js", "src/a/index.test.js", "src/b/main.js", "src/c/skip.js"]
                      );
                      assert.deepStrictEqual(
                        normalizeList(fs.globSync("src/**/*.js", {
                          cwd: workspace,
                          exclude: ["**/*.test.js", "src/c/skip.js"]
                        })),
                        ["src/a/index.js", "src/b/main.js"]
                      );
                      assert.deepStrictEqual(
                        normalizeList(await globCallback("src/**/*.cjs", { cwd: Buffer.from(workspace) })),
                        ["src/b/util.cjs"]
                      );
                      assert.deepStrictEqual(
                        normalizeList(await collect(fsp.glob(["src/**/*.js", "lib/**/*.js"], {
                          cwd: workspace,
                          exclude: ["**/*.test.js", "src/c/skip.js"]
                        }))),
                        ["lib/tool.js", "src/a/index.js", "src/b/main.js"]
                      );
                      assert.deepStrictEqual(
                        normalizeList(fs.globSync("src/b/*.js", {
                          cwd: url.pathToFileURL(path.resolve(process.cwd(), workspace))
                        })),
                        ["src/b/main.js"]
                      );
                      const dirents = fs.globSync("src/a/*", {
                        cwd: workspace,
                        withFileTypes: true
                      });
                      assert.deepStrictEqual(
                        dirents.map((entry) => entry.name + ":" + entry.isFile() + ":" + typeof entry.parentPath).sort(),
                        ["index.js:true:string", "index.test.js:true:string", "readme.md:true:string"]
                      );
                      const excludeFunctionCalls = [];
                      assert.deepStrictEqual(
                        normalizeList(fs.globSync("src/**/*.js", {
                          cwd: workspace,
                          exclude(entry) {
                            const text = String(entry).replace(/\\/g, "/");
                            excludeFunctionCalls.push(text);
                            return text.endsWith("src/a/index.test.js") || text.endsWith("src/c/skip.js");
                          }
                        })),
                        ["src/a/index.js", "src/b/main.js"]
                      );
                      assert.strictEqual(excludeFunctionCalls.includes("src/a/index.js"), true);

                      const nulPath = "bad" + String.fromCharCode(0) + "path";
                      expectSyncCodes("statfs escape", () => fs.statfsSync("../outside"), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"]);
                      expectSyncCodes("statfs absolute", () => fs.statfsSync("/sdcard"), ["${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}"]);
                      expectSyncCodes("statfs nul", () => fs.statfsSync(nulPath), ["${NodeRuntimeErrorCodes.FS_NUL_BYTE}", "ERR_INVALID_ARG_TYPE"]);
                      expectSyncCodes("statfs callback required", () => fs.statfs("./statfs/sub"), ["ERR_INVALID_ARG_TYPE"]);
                      await expectPromiseCodes("statfs promise escape", () => fsp.statfs("../outside"), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"]);
                      await expectPromiseCodes("statfs missing", () => fsp.statfs("./statfs/missing"), ["ENOENT"]);
                      await expectCallbackCodes("statfs callback escape", (callback) => fs.statfs(Buffer.from("../outside"), callback), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"]);

                      const nulGlob = "bad" + String.fromCharCode(0) + "glob";
                      expectSyncCodes("glob parent", () => fs.globSync("../**"), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}", "EPERM"]);
                      expectSyncCodes("glob absolute", () => fs.globSync("/sdcard/**/*.js"), ["${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}", "EPERM"]);
                      expectSyncCodes("glob cwd escape", () => fs.globSync("src/**/*.js", { cwd: "../outside" }), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"]);
                      expectSyncCodes("glob follow symlinks", () => fs.globSync("src/**/*.js", { cwd: workspace, followSymlinks: true }), ["${NodeRuntimeErrorCodes.EMBEDDED_NODE_UNSUPPORTED_OPTION}"]);
                      expectSyncCodes("glob invalid exclude", () => fs.globSync("src/**/*.js", { cwd: workspace, exclude: "**/*.test.js" }), ["ERR_INVALID_ARG_TYPE"]);
                      expectSyncCodes("glob array escape", () => fs.globSync(["src/**/*.js", "../**"], { cwd: workspace }), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}", "EPERM"]);
                      expectSyncCodes("glob cwd file", () => fs.globSync("src/**/*.js", { cwd: workspace + "/src/a/index.js" }), ["ENOTDIR"]);
                      expectSyncCodes("glob callback required", () => fs.glob("src/**/*.js", { cwd: workspace }), ["ERR_INVALID_ARG_TYPE"]);
                      await expectPromiseCodes("glob promise parent", () => collect(fsp.glob("../**")), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}", "EPERM"]);
                      await expectPromiseCodes("glob promise absolute", () => collect(fsp.glob(["src/**/*.js", "/sdcard/**/*.js"], { cwd: workspace })), ["${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}", "EPERM"]);
                      await expectCallbackCodes("glob callback nul", (callback) => fs.glob(["src/**/*.js", nulGlob], { cwd: workspace }, callback), ["${NodeRuntimeErrorCodes.FS_NUL_BYTE}", "ERR_INVALID_ARG_TYPE"]);

                      console.log("safe.file.ops.statfs_glob.ready=true");
                    })().catch((error) => {
                      console.error("safe.file.ops.statfs_glob.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-safe-file-ops-statfs-glob-s8-31.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node safe file operations statfs/glob regression", result)
            assertTrue(
                NodeEmbeddedTestHarness.describeResult("missing statfs/glob ready output", result),
                result.stdout.contains("safe.file.ops.statfs_glob.ready=true"),
            )
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun unsafeFileOperationBoundariesRemainDenied() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node safe file operations policy regression")

        val workDir = File(context.cacheDir, "node-safe-file-ops-policy-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create safe file operations policy work dir: $absolutePath" }
        }
        val outsideFile = File(context.cacheDir, "node-safe-file-ops-policy-outside-${System.nanoTime()}.txt").apply {
            writeText("outside")
        }
        try {
            val absoluteOutside = outsideFile.absolutePath.replace("\\", "\\\\")
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const assert = require("assert/strict");
                      const fs = require("fs");
                      const fsp = require("fs/promises");

                      function codesOf(error) {
                        return [error && error.autojs6Code, error && error.code, error && error.name].filter(Boolean);
                      }

                      function assertAnyCode(label, error, expectedCodes) {
                        const expected = Array.isArray(expectedCodes) ? expectedCodes : [expectedCodes];
                        const actual = codesOf(error);
                        assert.ok(
                          expected.some((code) => actual.includes(code)),
                          label + " expected one of " + expected.join("/") + " but got " + actual.join("/")
                        );
                      }

                      function expectSyncCodes(label, action, expectedCodes) {
                        try {
                          action();
                        } catch (error) {
                          assertAnyCode(label, error, expectedCodes);
                          return;
                        }
                        throw new Error(label + " was unexpectedly allowed");
                      }

                      async function expectPromiseCodes(label, action, expectedCodes) {
                        try {
                          await action();
                        } catch (error) {
                          assertAnyCode(label, error, expectedCodes);
                          return;
                        }
                        throw new Error(label + " was unexpectedly allowed");
                      }

                      function linkNativeUnsupported(error) {
                        return ["EACCES", "EPERM", "ENOSYS", "EXDEV", "EOPNOTSUPP"].includes(String(error && error.code || ""));
                      }

                      fs.mkdirSync("./policy", { recursive: true });
                      fs.writeFileSync("./policy/source.txt", "source", "utf8");

                      let scopedHardLink = "supported";
                      try {
                        fs.linkSync("./policy/source.txt", "./policy/source-link.txt");
                        fs.writeFileSync("./policy/source-link.txt", "linked", "utf8");
                        assert.strictEqual(fs.readFileSync("./policy/source.txt", "utf8"), "linked");
                      } catch (error) {
                        if (!linkNativeUnsupported(error)) {
                          throw error;
                        }
                        scopedHardLink = "native-unsupported:" + String(error.code || "UNKNOWN");
                      }

                      const sourceStat = fs.statSync("./policy/source.txt");
                      try {
                        fs.chownSync("./policy/source.txt", sourceStat.uid, sourceStat.gid);
                      } catch (error) {
                        assertAnyCode("scoped chown own ids", error, ["EPERM", "EACCES", "EINVAL", "ENOSYS"]);
                      }
                      try {
                        fs.chmodSync("./policy/source.txt", 0o600);
                      } catch (error) {
                        assertAnyCode("scoped chmod", error, ["EPERM", "EACCES", "EINVAL", "ENOSYS"]);
                      }

                      expectSyncCodes("link source escape", () => fs.linkSync("../outside.txt", "./policy/out.txt"), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"]);
                      expectSyncCodes("link dest escape", () => fs.linkSync("./policy/source.txt", "../outside-link.txt"), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"]);
                      await expectPromiseCodes("promise link dest escape", () => fsp.link("./policy/source.txt", "../outside-link-promise.txt"), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"]);

                      expectSyncCodes("chmod escape", () => fs.chmodSync("../outside.txt", 0o777), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}", "EPERM"]);
                      await expectPromiseCodes("promise chmod absolute", () => fsp.chmod("$absoluteOutside", 0o777), ["${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}", "EPERM"]);
                      expectSyncCodes("chown escape", () => fs.chownSync("../outside.txt", sourceStat.uid, sourceStat.gid), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}", "EPERM"]);
                      await expectPromiseCodes("promise chown absolute", () => fsp.chown("$absoluteOutside", sourceStat.uid, sourceStat.gid), ["${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}", "EPERM"]);

                      expectSyncCodes("symlink target escape", () => fs.symlinkSync("$absoluteOutside", "./policy/external-link.txt"), [
                        "${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}",
                        "${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}",
                        "${NodeRuntimeErrorCodes.FS_SYMLINK_ESCAPE}"
                      ]);
                      expectSyncCodes("symlink dest escape", () => fs.symlinkSync("./policy/source.txt", "../outside-symlink.txt"), ["${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"]);

                      expectSyncCodes("statfs device root", () => fs.statfsSync("/proc"), ["${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}", "${NodeRuntimeErrorCodes.FS_SCOPED_PATH}"]);
                      expectSyncCodes("cp device root", () => fs.cpSync("/proc/version", "./proc-copy", { recursive: true }), ["${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}", "${NodeRuntimeErrorCodes.FS_SCOPED_PATH}"]);
                      expectSyncCodes("glob protocol", () => fs.globSync("file:///sdcard/**/*.js"), ["${NodeRuntimeErrorCodes.FS_SCOPED_PATH}", "EPERM"]);

                      console.log("safe.file.ops.policy.hardlink=" + scopedHardLink);
                      console.log("safe.file.ops.policy.ready=true");
                    })().catch((error) => {
                      console.error("safe.file.ops.policy.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-safe-file-ops-policy-s8-31.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node safe file operations policy regression", result)
            assertTrue(
                NodeEmbeddedTestHarness.describeResult("missing safe file policy ready output", result),
                result.stdout.contains("safe.file.ops.policy.ready=true"),
            )
            assertFalse(File(workDir.parentFile, "outside-link.txt").exists())
            assertFalse(File(workDir.parentFile, "outside-link-promise.txt").exists())
            assertFalse(File(workDir.parentFile, "outside-symlink.txt").exists())
        } finally {
            outsideFile.delete()
            workDir.deleteRecursively()
        }
    }
}
