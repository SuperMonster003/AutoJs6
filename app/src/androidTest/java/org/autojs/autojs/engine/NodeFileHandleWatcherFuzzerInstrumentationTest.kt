package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeFileHandleWatcherFuzzerInstrumentationTest {

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
    fun fileHandleAndWatcherLifecycleFuzzerStaysBounded() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node FileHandle/watcher lifecycle fuzzer")

        val budget = NodeFuzzerBudget.current()
        val baseSeed = NodeFuzzerSupport.baseSeed(FUZZER_NAME, DEFAULT_BASE_SEED)
        val workDir = File(context.cacheDir, "node-fh-watch-fuzzer-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create FileHandle/watcher fuzzer work dir: $absolutePath" }
        }
        val outsideFile = File(context.cacheDir, "node-fh-watch-fuzzer-outside-${System.nanoTime()}.txt").apply {
            writeText("outside")
        }
        val cases = budget.selectCases(buildCases(), quick = QUICK_CASE_COUNT)
        try {
            val source = buildRunnerSource(cases, baseSeed, budget, outsideFile.absolutePath.replace("\\", "\\\\"))
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-filehandle-watcher-fuzzer-s8-32.js>",
                workingDirectory = workDir,
                timeoutMs = budget.timeoutMs(10_000L, 18_000L, 30_000L),
            )

            if (!result.succeeded || result.timedOut) {
                NodeFuzzerSupport.reportFailure(
                    fuzzer = FUZZER_NAME,
                    budget = budget,
                    baseSeed = baseSeed,
                    caseCount = cases.size,
                    replayClass = TEST_CLASS,
                    replayMethod = "fileHandleAndWatcherLifecycleFuzzerStaysBounded",
                    failure = JSONObject()
                        .put("phase", "script-result")
                        .put("errorCode", result.errorCode ?: JSONObject.NULL)
                        .put("errorMessage", result.errorMessage ?: JSONObject.NULL)
                        .put("timedOut", result.timedOut),
                )
            }
            NodeEmbeddedTestHarness.assertSucceeded("Node FileHandle/watcher lifecycle fuzzer", result)
            assertTrue(result.stdout, result.stdout.contains("fhwatch.fuzzer.seed=$baseSeed"))
            assertTrue(result.stdout, result.stdout.contains("fhwatch.fuzzer.budget=${budget.id}"))
            assertTrue(result.stdout, result.stdout.contains("fhwatch.fuzzer.done=true"))

            val summary = result.stdout.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith(SUMMARY_PREFIX) }
                ?.let { JSONObject(it.removePrefix(SUMMARY_PREFIX)) }
                ?: error(NodeEmbeddedTestHarness.describeResult("missing FileHandle/watcher fuzzer summary", result))
            val failureContext = buildSummaryFailureContext(summary, result, baseSeed)
            if (!summary.isNull("firstFailure")) {
                NodeFuzzerSupport.reportFailure(
                    fuzzer = FUZZER_NAME,
                    budget = budget,
                    baseSeed = baseSeed,
                    caseCount = cases.size,
                    replayClass = TEST_CLASS,
                    replayMethod = "fileHandleAndWatcherLifecycleFuzzerStaysBounded",
                    failure = summary.getJSONObject("firstFailure"),
                )
            }
            assertEquals(failureContext, cases.size, summary.getInt("attempts"))
            assertEquals(failureContext, 0, summary.getInt("unexpected"))
            assertEquals(failureContext, 0, summary.getInt("unstableErrors"))
            assertTrue(failureContext, summary.isNull("firstFailure"))
        } catch (throwable: Throwable) {
            NodeFuzzerSupport.reportFailure(
                fuzzer = FUZZER_NAME,
                budget = budget,
                baseSeed = baseSeed,
                caseCount = cases.size,
                replayClass = TEST_CLASS,
                replayMethod = "fileHandleAndWatcherLifecycleFuzzerStaysBounded",
                throwable = throwable,
            )
            throw throwable
        } finally {
            outsideFile.delete()
            workDir.deleteRecursively()
        }
    }

    @Test
    fun timeoutCleanupAfterFuzzerResourceLeakRecovers() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node FileHandle/watcher fuzzer timeout cleanup")

        val budget = NodeFuzzerBudget.current()
        val baseSeed = NodeFuzzerSupport.baseSeed(FUZZER_NAME, DEFAULT_BASE_SEED)
        val workDir = File(context.cacheDir, "node-fh-watch-fuzzer-timeout-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create FileHandle/watcher timeout work dir: $absolutePath" }
        }
        try {
            val timeout = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const fs = require("fs");
                      const fsp = require("fs/promises");
                      fs.writeFileSync("./leaked-fd.txt", "fd", "utf8");
                      fs.writeFileSync("./leaked-handle.txt", "handle", "utf8");
                      fs.writeFileSync("./leaked-watch.txt", "watch", "utf8");
                      const fd = fs.openSync("./leaked-fd.txt", "r+");
                      const handle = await fsp.open("./leaked-handle.txt", "r+");
                      const watcher = fs.watch("./leaked-watch.txt", { persistent: true }, () => {});
                      fs.writeSync(fd, "FD");
                      await handle.writeFile("HANDLE");
                      console.log("fhwatch.fuzzer.timeout.opened=" + [typeof fd, typeof handle.close, typeof watcher.close].join("|"));
                      setInterval(() => {}, 50);
                    })();
                """.trimIndent(),
                sourceName = "<node-filehandle-watcher-fuzzer-timeout-s8-32.js>",
                workingDirectory = workDir,
                timeoutMs = 1_000L,
            )

            assertFalse(NodeEmbeddedTestHarness.describeResult("FileHandle/watcher fuzzer timeout should fail", timeout), timeout.succeeded)
            assertTrue(NodeEmbeddedTestHarness.describeResult("FileHandle/watcher fuzzer timeout should time out", timeout), timeout.timedOut)

            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

            val recovery = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const fs = require("fs");
                      const fsp = require("fs/promises");
                      const handle = await fsp.open("./leaked-handle.txt", "w+");
                      try {
                        await handle.writeFile("HANDLE|ok", "utf8");
                      } finally {
                        await handle.close();
                      }
                      const fd = fs.openSync("./after-timeout.txt", "w+");
                      fs.writeSync(fd, "recovered");
                      fs.closeSync(fd);
                      fs.writeFileSync("./leaked-watch.txt", "recovered", "utf8");
                      const watcher = fs.watch("./leaked-watch.txt", { persistent: false }, () => {});
                      watcher.close();
                      console.log("fhwatch.fuzzer.timeout.recovered=" + [
                        fs.readFileSync("./leaked-fd.txt", "utf8"),
                        fs.readFileSync("./leaked-handle.txt", "utf8"),
                        fs.readFileSync("./after-timeout.txt", "utf8")
                      ].join("|"));
                    })();
                """.trimIndent(),
                sourceName = "<node-filehandle-watcher-fuzzer-timeout-recovery-s8-32.js>",
                workingDirectory = workDir,
                timeoutMs = budget.timeoutMs(8_000L, 10_000L, 15_000L),
            )

            if (!recovery.succeeded || recovery.timedOut) {
                NodeFuzzerSupport.reportFailure(
                    fuzzer = FUZZER_NAME,
                    budget = budget,
                    baseSeed = baseSeed,
                    caseCount = 1,
                    replayClass = TEST_CLASS,
                    replayMethod = "timeoutCleanupAfterFuzzerResourceLeakRecovers",
                    failure = JSONObject()
                        .put("phase", "timeout-recovery")
                        .put("errorCode", recovery.errorCode ?: JSONObject.NULL)
                        .put("errorMessage", recovery.errorMessage ?: JSONObject.NULL)
                        .put("timedOut", recovery.timedOut),
                )
            }
            NodeEmbeddedTestHarness.assertSucceeded("Node FileHandle/watcher fuzzer timeout recovery", recovery)
            assertTrue(
                recovery.stdout,
                recovery.stdout.contains("fhwatch.fuzzer.timeout.recovered=FD|HANDLE|ok|recovered"),
            )
        } catch (throwable: Throwable) {
            NodeFuzzerSupport.reportFailure(
                fuzzer = FUZZER_NAME,
                budget = budget,
                baseSeed = baseSeed,
                caseCount = 1,
                replayClass = TEST_CLASS,
                replayMethod = "timeoutCleanupAfterFuzzerResourceLeakRecovers",
                throwable = throwable,
            )
            throw throwable
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun buildCases(): List<FuzzerCase> {
        return listOf(
            FuzzerCase("close_races", "closeRaces"),
            FuzzerCase("abort_cancellation", "abortCancellation"),
            FuzzerCase("fd_reuse", "fdReuse"),
            FuzzerCase("watch_quota_exhaustion", "watchQuotaExhaustion"),
            FuzzerCase("path_rename_during_watch", "pathRenameDuringWatch"),
            FuzzerCase("symlink_replacement", "symlinkReplacement"),
            FuzzerCase("long_paths", "longPaths"),
            FuzzerCase("unicode_paths", "unicodePaths"),
        )
    }

    private fun buildRunnerSource(
        cases: List<FuzzerCase>,
        baseSeed: Long,
        budget: NodeFuzzerBudget,
        absoluteOutside: String,
    ): String {
        val casesJson = JSONArray()
        cases.forEach { fuzzCase ->
            casesJson.put(
                JSONObject()
                    .put("label", fuzzCase.label)
                    .put("op", fuzzCase.op),
            )
        }
        return """
            "nodejs";
            return (async () => {
              const assert = require("assert/strict");
              const fs = require("fs");
              const fsp = require("fs/promises");
              const path = require("path");
              const cases = $casesJson;
              const absoluteOutside = "$absoluteOutside";
              const summary = {
                attempts: 0,
                unexpected: 0,
                unstableErrors: 0,
                watchEvents: 0,
                firstFailure: null
              };
              console.log("fhwatch.fuzzer.seed=$baseSeed");
              console.log("fhwatch.fuzzer.budget=${budget.id}");
              console.log("fhwatch.fuzzer.cases=" + cases.length);

              function delay(ms) {
                return new Promise((resolve) => setTimeout(resolve, ms));
              }
              function codesOf(error) {
                return [error && error.autojs6Code, error && error.code, error && error.name].filter(Boolean).map(String);
              }
              function codeOf(error) {
                const codes = codesOf(error);
                return codes.length > 0 ? codes[0] : "";
              }
              function messageOf(error) {
                return String(error && (error.message || error) || "").replace(/\s+/g, " ").slice(0, 240);
              }
              function fail(record) {
                if (summary.firstFailure === null) {
                  summary.firstFailure = record;
                }
              }
              function markUnexpected(record) {
                summary.unexpected += 1;
                fail(record);
              }
              function markUnstable(record) {
                summary.unstableErrors += 1;
                fail(record);
              }
              function assertCase(condition, record) {
                if (!condition) {
                  markUnexpected(record);
                }
              }
              function hasAnyCode(error, expectedCodes) {
                const expected = Array.isArray(expectedCodes) ? expectedCodes.map(String) : [String(expectedCodes)];
                const actual = codesOf(error);
                return expected.some((code) => actual.includes(code));
              }
              function expectSyncReject(label, action, expectedCodes) {
                try {
                  action();
                } catch (error) {
                  if (!hasAnyCode(error, expectedCodes)) {
                    markUnexpected({ label, reason: "wrong-code", expectedCodes, actualCodes: codesOf(error), message: messageOf(error) });
                  }
                  return;
                }
                markUnexpected({ label, reason: "unexpected-allow" });
              }
              async function expectPromiseReject(label, action, expectedCodes) {
                try {
                  await action();
                } catch (error) {
                  if (!hasAnyCode(error, expectedCodes)) {
                    markUnexpected({ label, reason: "wrong-code", expectedCodes, actualCodes: codesOf(error), message: messageOf(error) });
                  }
                  return;
                }
                markUnexpected({ label, reason: "unexpected-allow" });
              }
              function callbackClose(fd) {
                return new Promise((resolve) => {
                  fs.close(fd, (error) => resolve(error ? codeOf(error) : "allowed"));
                });
              }
              function nativeSymlinkUnsupported(error) {
                return ["EACCES", "EPERM", "ENOSYS", "EOPNOTSUPP"].includes(String(error && error.code || ""));
              }
              async function runCloseRaces() {
                fs.mkdirSync("./race", { recursive: true });
                fs.writeFileSync("./race/handle.txt", "abcdef", "utf8");
                const handle = await fsp.open("./race/handle.txt", "r+");
                const closeResults = await Promise.allSettled([handle.close(), handle.close()]);
                assertCase(closeResults.some((entry) => entry.status === "fulfilled"), {
                  case: "close_races",
                  reason: "no-close-fulfilled",
                  closeResults
                });
                await expectPromiseReject("filehandle read after close", () => handle.readFile("utf8"), ["EBADF"]);

                const fd = fs.openSync("./race/fd.txt", "w+");
                fs.writeSync(fd, "fd");
                const callbackResults = await Promise.all([callbackClose(fd), callbackClose(fd)]);
                assertCase(callbackResults.includes("allowed"), {
                  case: "close_races",
                  reason: "fd-close-no-success",
                  callbackResults
                });
                assertCase(callbackResults.some((value) => value === "allowed" || value === "EBADF"), {
                  case: "close_races",
                  reason: "fd-close-unstable-code",
                  callbackResults
                });
              }
              async function runAbortCancellation() {
                if (typeof AbortController !== "function") {
                  markUnstable({ case: "abort_cancellation", reason: "abort-controller-missing" });
                  return;
                }
                fs.writeFileSync("./abort-watch.txt", "a", "utf8");
                const controller = new AbortController();
                const watcher = fs.watch("./abort-watch.txt", { persistent: true, signal: controller.signal }, () => {});
                const closed = await new Promise((resolve) => {
                  const timeout = setTimeout(() => {
                    try {
                      watcher.close();
                    } catch (_) {}
                    resolve(false);
                  }, 1000);
                  watcher.on("close", () => {
                    clearTimeout(timeout);
                    resolve(true);
                  });
                  controller.abort();
                });
                assertCase(closed, { case: "abort_cancellation", reason: "fs-watch-not-closed" });

                const promiseController = new AbortController();
                const iterator = fsp.watch("./abort-watch.txt", { signal: promiseController.signal });
                const pending = iterator.next().then(
                  () => "allowed",
                  (error) => codeOf(error)
                );
                promiseController.abort();
                const abortCode = await pending;
                assertCase(abortCode === "ABORT_ERR" || abortCode === "AbortError", {
                  case: "abort_cancellation",
                  reason: "promise-watch-wrong-abort",
                  abortCode
                });
              }
              async function runFdReuse() {
                fs.mkdirSync("./fd-reuse", { recursive: true });
                fs.writeFileSync("./fd-reuse/base.txt", "abcdef", "utf8");
                const oldFd = fs.openSync("./fd-reuse/base.txt", "r");
                fs.closeSync(oldFd);
                expectSyncReject("old fd read", () => fs.readSync(oldFd, Buffer.alloc(1), 0, 1, 0), ["EBADF"]);
                for (let index = 0; index < 12; index += 1) {
                  const file = "./fd-reuse/file-" + index + ".txt";
                  const fd = fs.openSync(file, "w+");
                  fs.writeSync(fd, "value-" + index);
                  fs.closeSync(fd);
                  assert.strictEqual(fs.readFileSync(file, "utf8"), "value-" + index);
                }
              }
              async function runWatchQuotaExhaustion() {
                fs.mkdirSync("./quota", { recursive: true });
                const watchers = [];
                try {
                  for (let index = 0; index < 16; index += 1) {
                    const file = "./quota/watch-" + index + ".txt";
                    fs.writeFileSync(file, String(index), "utf8");
                    watchers.push(fs.watch(file, { persistent: false }, () => {}));
                  }
                  fs.writeFileSync("./quota/overflow.txt", "overflow", "utf8");
                  expectSyncReject("watch quota", () => fs.watch("./quota/overflow.txt", { persistent: false }, () => {}), ["${NodeRuntimeErrorCodes.FS_WATCH_LIMIT}"]);
                } finally {
                  for (const watcher of watchers) {
                    try {
                      watcher.close();
                    } catch (_) {}
                  }
                }
              }
              async function runPathRenameDuringWatch() {
                fs.mkdirSync("./rename-watch", { recursive: true });
                fs.writeFileSync("./rename-watch/source.txt", "one", "utf8");
                const events = [];
                const watcher = fs.watch("./rename-watch", { persistent: false }, (eventType, filename) => {
                  events.push([eventType, filename === null || filename === undefined ? "" : String(filename)]);
                });
                await delay(120);
                fs.renameSync("./rename-watch/source.txt", "./rename-watch/renamed.txt");
                await delay(500);
                watcher.close();
                summary.watchEvents += events.length;
                assertCase(fs.existsSync("./rename-watch/renamed.txt") && !fs.existsSync("./rename-watch/source.txt"), {
                  case: "path_rename_during_watch",
                  reason: "rename-state-wrong",
                  events
                });
                if (events.length > 0) {
                  assertCase(events.every((entry) => entry[0] === "change" || entry[0] === "rename"), {
                    case: "path_rename_during_watch",
                    reason: "rename-event-type",
                    events
                  });
                }
              }
              async function runSymlinkReplacement() {
                fs.mkdirSync("./symlink", { recursive: true });
                fs.writeFileSync("./symlink/target.txt", "inside", "utf8");
                const link = "./symlink/link.txt";
                try {
                  fs.symlinkSync("target.txt", link);
                } catch (error) {
                  if (nativeSymlinkUnsupported(error)) {
                    return;
                  }
                  throw error;
                }
                assert.strictEqual(fs.readFileSync(link, "utf8"), "inside");
                fs.unlinkSync(link);
                expectSyncReject("external symlink replacement", () => fs.symlinkSync(absoluteOutside, link), [
                  "${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}",
                  "${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}",
                  "${NodeRuntimeErrorCodes.FS_SYMLINK_ESCAPE}",
                  "EPERM"
                ]);
                assertCase(!fs.existsSync(link), {
                  case: "symlink_replacement",
                  reason: "external-link-created"
                });
                fs.writeFileSync(link, "replacement", "utf8");
                assert.strictEqual(fs.readFileSync(link, "utf8"), "replacement");
              }
              async function runLongPaths() {
                const nested = Array.from({ length: 6 }, (_, index) => "segment_" + index + "_abcdefghijklmnop").join("/");
                const file = "./long/" + nested + "/value.txt";
                fs.mkdirSync(path.dirname(file), { recursive: true });
                const handle = await fsp.open(file, "w+");
                try {
                  await handle.writeFile("long-path", "utf8");
                } finally {
                  await handle.close();
                }
                assert.strictEqual(await fsp.readFile(file, "utf8"), "long-path");
              }
              async function runUnicodePaths() {
                const name = "./unicode/" + String.fromCharCode(0x76ee, 0x5f55) + "-delta-" + String.fromCharCode(0x0394) + ".txt";
                fs.mkdirSync(path.dirname(name), { recursive: true });
                await fsp.writeFile(name, "unicode", "utf8");
                const watcher = fs.watch(name, { persistent: false }, () => {
                  summary.watchEvents += 1;
                });
                await delay(120);
                fs.writeFileSync(name, "unicode-updated", "utf8");
                await delay(300);
                watcher.close();
                assert.strictEqual(await fsp.readFile(name, "utf8"), "unicode-updated");
              }
              const runners = {
                closeRaces: runCloseRaces,
                abortCancellation: runAbortCancellation,
                fdReuse: runFdReuse,
                watchQuotaExhaustion: runWatchQuotaExhaustion,
                pathRenameDuringWatch: runPathRenameDuringWatch,
                symlinkReplacement: runSymlinkReplacement,
                longPaths: runLongPaths,
                unicodePaths: runUnicodePaths
              };
              for (const fuzzCase of cases) {
                summary.attempts += 1;
                try {
                  const runner = runners[fuzzCase.op];
                  if (typeof runner !== "function") {
                    markUnexpected({ case: fuzzCase, reason: "missing-runner" });
                    continue;
                  }
                  await runner();
                } catch (error) {
                  markUnexpected({
                    case: fuzzCase,
                    reason: "case-threw",
                    code: codeOf(error),
                    message: messageOf(error)
                  });
                }
              }
              console.log("$SUMMARY_PREFIX" + JSON.stringify(summary));
              if (summary.firstFailure !== null) {
                console.error("fhwatch.fuzzer.failure=" + JSON.stringify(summary.firstFailure));
                process.exitCode = 1;
                return;
              }
              console.log("fhwatch.fuzzer.done=true");
            })();
        """.trimIndent()
    }

    private fun buildSummaryFailureContext(
        summary: JSONObject,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        baseSeed: Long,
    ): String {
        return buildString {
            appendLine("FileHandle/watcher fuzzer failed")
            appendLine("baseSeed=$baseSeed")
            appendLine("summary=${summary.toString(2)}")
            appendLine(NodeEmbeddedTestHarness.describeResult("FileHandle/watcher fuzzer result", result))
        }
    }

    private data class FuzzerCase(
        val label: String,
        val op: String,
    )

    companion object {
        private const val TEST_CLASS = "org.autojs.autojs.engine.NodeFileHandleWatcherFuzzerInstrumentationTest"
        private const val FUZZER_NAME = "fhwatch"
        private const val DEFAULT_BASE_SEED = 83232032L
        private const val QUICK_CASE_COUNT = 4
        private const val SUMMARY_PREFIX = "fhwatch.fuzzer.summary="
    }
}
