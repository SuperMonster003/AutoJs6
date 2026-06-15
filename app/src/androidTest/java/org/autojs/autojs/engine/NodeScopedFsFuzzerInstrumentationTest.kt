package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class NodeScopedFsFuzzerInstrumentationTest {

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
    fun scopedFsPathNormalizationFuzzerDoesNotEscape() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node scoped fs fuzzer")

        val workDir = File(context.cacheDir, "node-scoped-fs-fuzzer-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create scoped fs fuzzer work dir: $absolutePath" }
        }
        val outsideDir = File(context.cacheDir, "node-scoped-fs-fuzzer-outside-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create scoped fs fuzzer outside dir: $absolutePath" }
        }
        val links = mutableListOf<File>()
        try {
            val fixtures = writeFixtures(workDir, outsideDir, links)
            assumeTrue("Symlink creation unavailable on this device", fixtures.symlinkReady)

            val cases = buildCases(fixtures)
            val source = buildRunnerSource(cases)
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-scoped-fs-fuzzer.js>",
                workingDirectory = workDir,
                timeoutMs = 15_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node scoped fs fuzzer", result)
            assertTrue(result.stdout, result.stdout.contains("scopedfs.fuzzer.seed=$BASE_SEED"))
            assertTrue(result.stdout, result.stdout.contains("scopedfs.fuzzer.done=true"))
            assertFalse(
                NodeEmbeddedTestHarness.describeResult("scoped fs fuzzer loaded outside marker", result),
                result.stdout.contains(ESCAPE_MARKER),
            )

            val summary = result.stdout.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith(SUMMARY_PREFIX) }
                ?.let { JSONObject(it.removePrefix(SUMMARY_PREFIX)) }
                ?: error(NodeEmbeddedTestHarness.describeResult("missing scoped fs fuzzer summary", result))
            val failureContext = buildSummaryFailureContext(summary, result)
            assertEquals(failureContext, cases.size, summary.getInt("attempts"))
            assertEquals(failureContext, 0, summary.getInt("escapes"))
            assertEquals(failureContext, 0, summary.getInt("unstableErrors"))
            assertEquals(failureContext, 0, summary.getInt("unexpected"))
            assertTrue(failureContext, summary.isNull("firstFailure"))
        } finally {
            deleteLinks(links)
            workDir.deleteRecursively()
            outsideDir.deleteRecursively()
        }
    }

    private fun writeFixtures(workDir: File, outsideDir: File, links: MutableList<File>): FsFuzzerFixtures {
        File(workDir, "safe.txt").writeText("safe", StandardCharsets.UTF_8)
        writeFile(workDir, "dir/nested/file.txt", "nested")
        writeFile(workDir, "dir/nested/repeated.txt", "repeated")
        writeFile(workDir, "encoded/%2e%2e.txt", "encoded-dotdot")
        writeFile(workDir, "encoded/%2f.txt", "encoded-slash")
        writeFile(workDir, "encoded/space%20name.txt", "encoded-space")
        writeFile(workDir, "unicode∕name.txt", "unicode-division-slash")
        writeFile(workDir, "unicode⁄name.txt", "unicode-fraction-slash")
        writeFile(workDir, "unicode／name.txt", "unicode-fullwidth-slash")
        writeFile(workDir, WINDOWS_STYLE_SAFE, "windows-style")
        writeFile(workDir, WINDOWS_STYLE_DOTDOT, "windows-dotdot")
        val longRelative = (0 until 6).joinToString("/") { index -> "segment_${index}_abcdefghijklmnop" } + "/value.txt"
        writeFile(workDir, longRelative, "long-path")

        File(outsideDir, "secret.txt").writeText(ESCAPE_MARKER, StandardCharsets.UTF_8)

        val insideLink = File(workDir, "inside-link")
        val outsideLink = File(workDir, "outside-link")
        val chainInsideB = File(workDir, "chain-inside-b")
        val chainInsideA = File(workDir, "chain-inside-a")
        val chainOutsideB = File(workDir, "chain-outside-b")
        val chainOutsideA = File(workDir, "chain-outside-a")
        val symlinkReady = listOf(
            insideLink to File(workDir, "dir"),
            outsideLink to outsideDir,
            chainInsideB to File(workDir, "dir"),
            chainInsideA to chainInsideB,
            chainOutsideB to outsideDir,
            chainOutsideA to chainOutsideB,
        ).all { (link, target) ->
            createSymlink(link, target).also { created -> if (created) links += link }
        }

        return FsFuzzerFixtures(
            outsideSecretAbsolute = File(outsideDir, "secret.txt").absolutePath,
            outsideDirName = outsideDir.name,
            longRelativePath = longRelative,
            symlinkReady = symlinkReady,
        )
    }

    private fun writeFile(root: File, relativePath: String, text: String) {
        File(root, relativePath).apply {
            parentFile?.mkdirs()
            writeText(text, StandardCharsets.UTF_8)
        }
    }

    private fun createSymlink(link: File, target: File): Boolean {
        return try {
            Files.deleteIfExists(link.toPath())
            Files.createSymbolicLink(link.toPath(), target.toPath())
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun deleteLinks(links: List<File>) {
        links.asReversed().forEach { link ->
            try {
                Files.deleteIfExists(link.toPath())
            } catch (_: Throwable) {
            }
        }
    }

    private fun buildCases(fixtures: FsFuzzerFixtures): List<PathFuzzCase> {
        val escapedAbsolute = fixtures.outsideSecretAbsolute.replace("\\", "\\\\")
        return listOf(
            allow("read.safe", "read", "safe.txt"),
            allow("read.dot", "read", "./safe.txt"),
            allow("read.dotdot_inside", "read", "dir/nested/../nested/file.txt"),
            allow("read.repeated_slashes", "read", "dir//nested///repeated.txt"),
            allow("promise.dotdot_inside", "promiseRead", "./dir/./nested/../nested/file.txt"),
            allow("realpath.safe", "realpath", "./dir/nested/file.txt"),
            allow("read.encoded_dotdot", "read", "encoded/%2e%2e.txt"),
            allow("read.encoded_slash", "read", "encoded/%2f.txt"),
            allow("read.encoded_space", "read", "encoded/space%20name.txt"),
            allow("read.unicode_division_slash", "read", "unicode∕name.txt"),
            allow("read.unicode_fraction_slash", "read", "unicode⁄name.txt"),
            allow("read.unicode_fullwidth_slash", "read", "unicode／name.txt"),
            allow("read.long_path", "read", fixtures.longRelativePath),
            allow("read.symlink_inside", "read", "inside-link/nested/file.txt"),
            allow("realpath.symlink_inside", "realpath", "inside-link/nested/file.txt"),
            allow("read.symlink_chain_inside", "read", "chain-inside-a/nested/file.txt"),
            allow("realpath.symlink_chain_inside", "realpath", "chain-inside-a/nested/file.txt"),
            allow("stat.safe", "stat", "dir/nested/file.txt"),
            allow("access.safe", "access", "dir/nested/file.txt"),
            flexible("read.windows_backslash", "read", WINDOWS_STYLE_SAFE),
            flexible("read.windows_dotdot_backslash", "read", WINDOWS_STYLE_DOTDOT),
            deny("read.escape_parent", "read", "../${fixtures.outsideDirName}/secret.txt", "ERR_AUTOJS6_FS_PATH_ESCAPE"),
            deny("read.escape_nested_parent", "read", "dir/../../${fixtures.outsideDirName}/secret.txt", "ERR_AUTOJS6_FS_PATH_ESCAPE"),
            deny("read.escape_absolute", "read", escapedAbsolute, "ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED"),
            deny("read.proc_absolute", "read", "/proc/version", "ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED"),
            deny("read.nul", "read", "bad\u0000path", "ERR_AUTOJS6_FS_NUL_BYTE"),
            deny("promise.escape_parent", "promiseRead", "../${fixtures.outsideDirName}/secret.txt", "ERR_AUTOJS6_FS_PATH_ESCAPE"),
            deny("realpath.escape_parent", "realpath", "../${fixtures.outsideDirName}/secret.txt", "ERR_AUTOJS6_FS_PATH_ESCAPE"),
            deny("read.symlink_escape", "read", "outside-link/secret.txt", "ERR_AUTOJS6_FS_REALPATH_ESCAPE"),
            deny("realpath.symlink_escape", "realpath", "outside-link/secret.txt", "ERR_AUTOJS6_FS_REALPATH_ESCAPE"),
            deny("read.symlink_chain_escape", "read", "chain-outside-a/secret.txt", "ERR_AUTOJS6_FS_REALPATH_ESCAPE"),
            deny("realpath.symlink_chain_escape", "realpath", "chain-outside-a/secret.txt", "ERR_AUTOJS6_FS_REALPATH_ESCAPE"),
        )
    }

    private fun allow(label: String, op: String, path: String): PathFuzzCase {
        return PathFuzzCase(label, op, path, mode = "allow")
    }

    private fun deny(label: String, op: String, path: String, expectedCode: String): PathFuzzCase {
        return PathFuzzCase(label, op, path, mode = "deny", expectedCode = expectedCode)
    }

    private fun flexible(label: String, op: String, path: String): PathFuzzCase {
        return PathFuzzCase(label, op, path, mode = "flex")
    }

    private fun buildRunnerSource(cases: List<PathFuzzCase>): String {
        val casesJson = JSONArray()
        cases.forEach { fuzzCase ->
            casesJson.put(
                JSONObject()
                    .put("label", fuzzCase.label)
                    .put("op", fuzzCase.op)
                    .put("path", fuzzCase.path)
                    .put("mode", fuzzCase.mode)
                    .put("expectedCode", fuzzCase.expectedCode ?: JSONObject.NULL),
            )
        }
        return """
            "nodejs";
            return (async () => {
              const fs = require("fs");
              const fsp = require("fs/promises");
              const path = require("path");
              const cases = $casesJson;
              const root = fs.realpathSync(".");
              const escapeMarker = "$ESCAPE_MARKER";
              const stableFsCodes = new Set(${jsonArray(STABLE_FS_ERROR_CODES)});
              const summary = {
                attempts: 0,
                escapes: 0,
                unstableErrors: 0,
                unexpected: 0,
                firstFailure: null
              };
              console.log("scopedfs.fuzzer.seed=$BASE_SEED");
              console.log("scopedfs.fuzzer.cases=" + cases.length);

              function normalize(value) {
                return String(value).replace(/\\/g, "/").replace(/\/+$/, "");
              }
              function withinRoot(value) {
                const normalizedRoot = normalize(root);
                const normalizedValue = normalize(value);
                return normalizedValue === normalizedRoot || normalizedValue.indexOf(normalizedRoot + "/") === 0;
              }
              function codeOf(error) {
                return String(error && (error.autojs6Code || error.code || error.name || ""));
              }
              function messageOf(error) {
                return String(error && (error.message || error) || "").replace(/\s+/g, " ").slice(0, 240);
              }
              function valueOf(value) {
                return value === undefined ? "" : String(value);
              }
              function fail(record) {
                if (summary.firstFailure === null) {
                  summary.firstFailure = record;
                }
              }
              function validateSuccess(fuzzCase, value) {
                const text = valueOf(value);
                if (text.indexOf(escapeMarker) >= 0) {
                  summary.escapes += 1;
                  fail({ case: fuzzCase, reason: "escape-marker", value: text });
                  return;
                }
                let real = "";
                try {
                  real = fs.realpathSync(fuzzCase.path);
                } catch (_) {
                  real = path.resolve(fuzzCase.path);
                }
                if (!withinRoot(real)) {
                  summary.escapes += 1;
                  fail({ case: fuzzCase, reason: "outside-realpath", real });
                  return;
                }
                if (fuzzCase.mode === "deny") {
                  summary.unexpected += 1;
                  fail({ case: fuzzCase, reason: "unexpected-allow", value: text, real });
                }
              }
              function validateFailure(fuzzCase, error) {
                const code = codeOf(error);
                const record = { case: fuzzCase, code, message: messageOf(error) };
                if (fuzzCase.mode === "allow") {
                  summary.unexpected += 1;
                  fail(Object.assign({ reason: "unexpected-deny" }, record));
                  return;
                }
                if (fuzzCase.mode === "deny") {
                  if (code !== fuzzCase.expectedCode) {
                    summary.unexpected += 1;
                    fail(Object.assign({ reason: "wrong-deny-code" }, record));
                  }
                  return;
                }
                if (!(stableFsCodes.has(code) || code === "ENOENT")) {
                  summary.unstableErrors += 1;
                  fail(Object.assign({ reason: "unstable-flex-code" }, record));
                }
              }
              async function execute(fuzzCase) {
                switch (fuzzCase.op) {
                  case "read":
                    return fs.readFileSync(fuzzCase.path, "utf8");
                  case "promiseRead":
                    return await fsp.readFile(fuzzCase.path, "utf8");
                  case "realpath":
                    return fs.realpathSync(fuzzCase.path);
                  case "stat":
                    return fs.statSync(fuzzCase.path).isFile() ? "file" : "other";
                  case "access":
                    fs.accessSync(fuzzCase.path, fs.constants.R_OK);
                    return "access";
                  default:
                    throw new Error("Unknown scoped fs fuzzer op: " + fuzzCase.op);
                }
              }
              for (const fuzzCase of cases) {
                summary.attempts += 1;
                try {
                  const value = await execute(fuzzCase);
                  validateSuccess(fuzzCase, value);
                } catch (error) {
                  validateFailure(fuzzCase, error);
                }
              }
              console.log("$SUMMARY_PREFIX" + JSON.stringify(summary));
              if (summary.firstFailure !== null) {
                console.error("scopedfs.fuzzer.failure=" + JSON.stringify(summary.firstFailure));
                process.exitCode = 1;
                return;
              }
              console.log("scopedfs.fuzzer.done=true");
            })();
        """.trimIndent()
    }

    private fun buildSummaryFailureContext(
        summary: JSONObject,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ): String {
        return buildString {
            appendLine("scoped fs fuzzer failed")
            appendLine("baseSeed=$BASE_SEED")
            appendLine("summary=${summary.toString(2)}")
            appendLine(NodeEmbeddedTestHarness.describeResult("scoped fs fuzzer result", result))
        }
    }

    private fun jsonArray(values: List<String>): JSONArray {
        val array = JSONArray()
        values.forEach { value -> array.put(value) }
        return array
    }

    private data class FsFuzzerFixtures(
        val outsideSecretAbsolute: String,
        val outsideDirName: String,
        val longRelativePath: String,
        val symlinkReady: Boolean,
    )

    private data class PathFuzzCase(
        val label: String,
        val op: String,
        val path: String,
        val mode: String,
        val expectedCode: String? = null,
    )

    companion object {
        private const val BASE_SEED = 85001285L
        private const val SUMMARY_PREFIX = "scopedfs.fuzzer.summary="
        private const val ESCAPE_MARKER = "AUTOJS6_SCOPED_FS_FUZZ_ESCAPE_LOADED"
        private const val WINDOWS_STYLE_SAFE = "windows\\safe.txt"
        private const val WINDOWS_STYLE_DOTDOT = "..\\inside-windows.txt"

        private val STABLE_FS_ERROR_CODES = listOf(
            "ERR_AUTOJS6_FS_SCOPED_PATH",
            "ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED",
            "ERR_AUTOJS6_FS_PATH_ESCAPE",
            "ERR_AUTOJS6_FS_REALPATH_ESCAPE",
            "ERR_AUTOJS6_FS_SYMLINK_ESCAPE",
            "ERR_AUTOJS6_FS_NUL_BYTE",
        )
    }
}
