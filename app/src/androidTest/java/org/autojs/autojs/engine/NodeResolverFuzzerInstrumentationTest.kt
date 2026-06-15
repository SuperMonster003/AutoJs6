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
import java.nio.charset.StandardCharsets
import java.util.Random

class NodeResolverFuzzerInstrumentationTest {

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
    fun packageExportsImportsResolverFuzzerDoesNotCrashOrEscape() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node resolver fuzzer")

        val workDir = File(context.cacheDir, "node-resolver-fuzzer-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create resolver fuzzer work dir: $absolutePath" }
        }
        try {
            val cases = (0 until CASE_COUNT).map { index ->
                generateFuzzCase(index, BASE_SEED + (index * CASE_SEED_STEP))
            }
            writeEscapeFixtures(workDir)
            cases.forEach { fuzzCase -> writeFuzzPackage(workDir, fuzzCase) }

            val source = buildRunnerSource(cases)
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-resolver-fuzzer.js>",
                workingDirectory = workDir,
                timeoutMs = 20_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node resolver fuzzer", result)
            assertTrue(result.stdout, result.stdout.contains("resolver.fuzzer.seed=$BASE_SEED"))
            assertTrue(result.stdout, result.stdout.contains("resolver.fuzzer.done=true"))
            assertFalse(
                NodeEmbeddedTestHarness.describeResult("resolver fuzzer loaded escape marker", result),
                result.stdout.contains(ESCAPE_MARKER),
            )

            val summary = result.stdout.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith(SUMMARY_PREFIX) }
                ?.let { JSONObject(it.removePrefix(SUMMARY_PREFIX)) }
                ?: error(NodeEmbeddedTestHarness.describeResult("missing resolver fuzzer summary", result))
            val expectedRecords = cases.fold(0) { total, fuzzCase ->
                total + fuzzCase.exportRequests.size + fuzzCase.importRequests.size
            }
            val failureContext = buildSummaryFailureContext(summary, result)
            assertEquals(failureContext, CASE_COUNT, summary.getInt("cases"))
            assertEquals(failureContext, expectedRecords, summary.getInt("attempts"))
            assertEquals(failureContext, 0, summary.getInt("escapes"))
            assertEquals(failureContext, 0, summary.getInt("unstableErrors"))
            assertTrue(failureContext, summary.isNull("firstFailure"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun generateFuzzCase(index: Int, seed: Long): FuzzCase {
        val random = Random(seed)
        val packageName = "fuzzpkg-$index"
        val packageJson = JSONObject()
            .put("name", packageName)
            .put("version", "1.0.0")
            .put("main", "./fallback.js")
            .put("exports", generateExportsValue(index, random))
            .put("imports", generateImportsValue(index, random))
        return FuzzCase(
            index = index,
            seed = seed,
            packageName = packageName,
            packageJson = packageJson,
            exportRequests = EXPORT_SUBPATHS.map { subpath -> packageName + subpath },
            importRequests = IMPORT_SPECIFIERS,
        )
    }

    private fun generateExportsValue(index: Int, random: Random): Any {
        return when (index % 9) {
            0 -> random.safeTarget()
            1 -> JSONObject()
                .put(".", random.safeTarget())
                .put("./sub", random.safeTarget())
                .put("./private/*", JSONObject.NULL)
                .put("./feature/*", "./feature/*.js")
                .put("./bad", random.unsafeTarget())
                .put("./cond", random.conditionTarget())
                .put("./arr", random.arrayTarget())
            2 -> JSONObject()
                .put(".", random.conditionTarget())
                .put("./sub", random.conditionTarget())
                .put("./feature/*", random.conditionPatternTarget())
            3 -> JSONObject()
                .put(".", random.arrayTarget())
                .put("./arr", random.arrayTarget())
                .put("./sub", random.safeTarget())
            4 -> JSONObject.NULL
            5 -> JSONArray()
                .put(random.safeTarget())
                .put(random.unsafeTarget())
            6 -> JSONObject()
                .put(".", random.unsafeTarget())
                .put("./sub", random.safeTarget())
                .put("./feature/*", "./feature/*.js")
            7 -> JSONObject()
                .put("./feature/*", "./feature/*.js")
                .put("./private/*", JSONObject.NULL)
                .put("./multi/*/*", "./feature/*.js")
                .put("./badpattern/*", "./feature/no-star.js")
                .put(".", random.safeTarget())
            else -> JSONObject()
                .put("require", random.safeTarget())
                .put("node", random.unsafeTarget())
                .put("default", random.safeTarget())
        }
    }

    private fun generateImportsValue(index: Int, random: Random): Any {
        return when (index % 7) {
            0 -> JSONObject()
                .put("#ok", random.safeTarget())
                .put("#cond", random.conditionTarget())
                .put("#null", JSONObject.NULL)
                .put("#bad", random.unsafeTarget())
                .put("#arr", random.arrayTarget())
                .put("#pat/*", "./feature/*.js")
            1 -> JSONObject.NULL
            2 -> JSONArray()
                .put(random.safeTarget())
                .put(random.unsafeTarget())
            3 -> JSONObject()
                .put("#ok", random.safeTarget())
                .put("#cond", JSONObject().put("browser", random.safeTarget()))
                .put("#pat/*", random.conditionPatternTarget())
            4 -> JSONObject()
                .put("#ok", random.unsafeTarget())
                .put("#bad", random.unsafeTarget())
                .put("#arr", random.arrayTarget())
            5 -> JSONObject()
                .put("#pat/*", "./feature/*.js")
                .put("#multi/*/*", "./feature/*.js")
                .put("#badpattern/*", "./feature/no-star.js")
                .put("#null", JSONObject.NULL)
            else -> JSONObject()
                .put("#ok", random.safeTarget())
                .put("#cond", random.conditionTarget())
                .put("#missing", "./missing.js")
        }
    }

    private fun writeEscapeFixtures(workDir: File) {
        val nodeModules = File(workDir, "node_modules").apply {
            check(mkdirs()) { "Failed to create node_modules for resolver fuzzer: $absolutePath" }
        }
        File(workDir, "escape.js").writeText("""module.exports = "$ESCAPE_MARKER";""" + "\n", StandardCharsets.UTF_8)
        File(nodeModules, "escape.js").writeText("""module.exports = "$ESCAPE_MARKER";""" + "\n", StandardCharsets.UTF_8)
    }

    private fun writeFuzzPackage(workDir: File, fuzzCase: FuzzCase) {
        val packageDir = File(workDir, "node_modules/${fuzzCase.packageName}").apply {
            check(mkdirs()) { "Failed to create fuzz package dir: $absolutePath" }
        }
        File(packageDir, "package.json").writeText(fuzzCase.packageJson.toString(2) + "\n", StandardCharsets.UTF_8)
        writeModule(packageDir, "index.js", """module.exports = "case-${fuzzCase.index}-index";""")
        writeModule(packageDir, "fallback.js", """module.exports = "case-${fuzzCase.index}-fallback";""")
        writeModule(packageDir, "browser.js", """module.exports = "case-${fuzzCase.index}-browser";""")
        writeModule(packageDir, "require-only.js", """module.exports = "case-${fuzzCase.index}-require";""")
        writeModule(packageDir, "lib/sub.js", """module.exports = "case-${fuzzCase.index}-sub";""")
        writeModule(packageDir, "lib/import-ok.js", """module.exports = "case-${fuzzCase.index}-import-ok";""")
        writeModule(packageDir, "lib/import-cond.js", """module.exports = "case-${fuzzCase.index}-import-cond";""")
        writeModule(packageDir, "lib/import-default.js", """module.exports = "case-${fuzzCase.index}-import-default";""")
        writeModule(packageDir, "feature/a.js", """module.exports = "case-${fuzzCase.index}-feature-a";""")
        writeModule(packageDir, "feature/b.js", """module.exports = "case-${fuzzCase.index}-feature-b";""")
        writeModule(packageDir, "feature/no-star.js", """module.exports = "case-${fuzzCase.index}-no-star";""")
        writeModule(packageDir, "probe.js", buildProbeSource(fuzzCase))
    }

    private fun writeModule(packageDir: File, relativePath: String, source: String) {
        File(packageDir, relativePath).apply {
            parentFile?.mkdirs()
            writeText(source.trimIndent() + "\n", StandardCharsets.UTF_8)
        }
    }

    private fun buildProbeSource(fuzzCase: FuzzCase): String {
        return """
            const requests = ${jsonArray(fuzzCase.importRequests)};
            const escapeMarker = "$ESCAPE_MARKER";
            function codeOf(error) {
              return String(error && (error.autojs6Code || error.code || error.name || ""));
            }
            function messageOf(error) {
              return String(error && (error.message || error) || "").replace(/\s+/g, " ").slice(0, 240);
            }
            function valueOf(value) {
              try {
                return typeof value === "string" ? value : JSON.stringify(value);
              } catch (_) {
                return String(value);
              }
            }
            module.exports = requests.map(function(request) {
              try {
                const text = valueOf(require(request));
                return {
                  request,
                  ok: true,
                  value: text,
                  escape: text.indexOf(escapeMarker) >= 0
                };
              } catch (error) {
                return {
                  request,
                  ok: false,
                  code: codeOf(error),
                  message: messageOf(error)
                };
              }
            });
        """.trimIndent()
    }

    private fun buildRunnerSource(cases: List<FuzzCase>): String {
        val casesJson = JSONArray()
        cases.forEach { fuzzCase ->
            casesJson.put(
                JSONObject()
                    .put("index", fuzzCase.index)
                    .put("seed", fuzzCase.seed)
                    .put("packageName", fuzzCase.packageName)
                    .put("packageJson", fuzzCase.packageJson)
                    .put("exportRequests", jsonArray(fuzzCase.exportRequests))
                    .put("importRequests", jsonArray(fuzzCase.importRequests)),
            )
        }
        return """
            "nodejs";
            (() => {
              const cases = $casesJson;
              const escapeMarker = "$ESCAPE_MARKER";
              const stableErrorCodes = new Set(${jsonArray(STABLE_RESOLVER_ERROR_CODES.toList())});
              const summary = {
                cases: cases.length,
                attempts: 0,
                escapes: 0,
                unstableErrors: 0,
                firstFailure: null
              };
              console.log("resolver.fuzzer.seed=$BASE_SEED");
              console.log("resolver.fuzzer.cases=" + cases.length);
              function codeOf(error) {
                return String(error && (error.autojs6Code || error.code || error.name || ""));
              }
              function messageOf(error) {
                return String(error && (error.message || error) || "").replace(/\s+/g, " ").slice(0, 240);
              }
              function valueOf(value) {
                try {
                  return typeof value === "string" ? value : JSON.stringify(value);
                } catch (_) {
                  return String(value);
                }
              }
              function observe(record) {
                summary.attempts += 1;
                if (record.escape === true) {
                  summary.escapes += 1;
                  if (summary.firstFailure === null) {
                    summary.firstFailure = record;
                  }
                  return;
                }
                if (record.ok !== true && !stableErrorCodes.has(record.code || "")) {
                  summary.unstableErrors += 1;
                  if (summary.firstFailure === null) {
                    summary.firstFailure = record;
                  }
                }
              }
              function attemptExport(fuzzCase, request) {
                try {
                  const text = valueOf(require(request));
                  observe({
                    case: fuzzCase.index,
                    seed: fuzzCase.seed,
                    packageJson: fuzzCase.packageJson,
                    kind: "exports",
                    request,
                    ok: true,
                    value: text,
                    escape: text.indexOf(escapeMarker) >= 0
                  });
                } catch (error) {
                  observe({
                    case: fuzzCase.index,
                    seed: fuzzCase.seed,
                    packageJson: fuzzCase.packageJson,
                    kind: "exports",
                    request,
                    ok: false,
                    code: codeOf(error),
                    message: messageOf(error)
                  });
                }
              }
              function attemptImports(fuzzCase) {
                const probe = "./node_modules/" + fuzzCase.packageName + "/probe.js";
                let records;
                try {
                  records = require(probe);
                } catch (error) {
                  observe({
                    case: fuzzCase.index,
                    seed: fuzzCase.seed,
                    packageJson: fuzzCase.packageJson,
                    kind: "imports-probe",
                    request: probe,
                    ok: false,
                    code: codeOf(error),
                    message: messageOf(error)
                  });
                  return;
                }
                for (const record of records) {
                  observe({
                    case: fuzzCase.index,
                    seed: fuzzCase.seed,
                    packageJson: fuzzCase.packageJson,
                    kind: "imports",
                    request: record.request,
                    ok: record.ok === true,
                    code: record.code || "",
                    message: record.message || "",
                    value: record.value || "",
                    escape: record.escape === true || String(record.value || "").indexOf(escapeMarker) >= 0
                  });
                }
              }
              for (const fuzzCase of cases) {
                for (const request of fuzzCase.exportRequests) {
                  attemptExport(fuzzCase, request);
                }
                attemptImports(fuzzCase);
              }
              console.log("$SUMMARY_PREFIX" + JSON.stringify(summary));
              if (summary.firstFailure !== null) {
                console.error("resolver.fuzzer.failure=" + JSON.stringify(summary.firstFailure));
                process.exitCode = 1;
                return;
              }
              console.log("resolver.fuzzer.done=true");
            })();
        """.trimIndent()
    }

    private fun buildSummaryFailureContext(
        summary: JSONObject,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ): String {
        return buildString {
            appendLine("resolver fuzzer failed")
            appendLine("baseSeed=$BASE_SEED")
            appendLine("summary=${summary.toString(2)}")
            appendLine(NodeEmbeddedTestHarness.describeResult("resolver fuzzer result", result))
        }
    }

    private fun jsonArray(values: List<String>): JSONArray {
        val array = JSONArray()
        values.forEach { value -> array.put(value) }
        return array
    }

    private fun Random.safeTarget(): String {
        return choose(
            listOf(
                "./index.js",
                "./fallback.js",
                "./lib/sub.js",
                "./lib/import-ok.js",
                "./feature/a.js",
            ),
        )
    }

    private fun Random.unsafeTarget(): String {
        return choose(
            listOf(
                "../escape.js",
                "./../escape.js",
                "../../escape.js",
                "/absolute.js",
                "./missing.js",
                "./native.node",
            ),
        )
    }

    private fun Random.conditionTarget(): JSONObject {
        return if (nextBoolean()) {
            JSONObject()
                .put("require", if (nextBoolean()) safeTarget() else unsafeTarget())
                .put("default", safeTarget())
        } else {
            JSONObject()
                .put("browser", safeTarget())
                .put("development", safeTarget())
        }
    }

    private fun Random.conditionPatternTarget(): JSONObject {
        return if (nextBoolean()) {
            JSONObject()
                .put("require", "./feature/*.js")
                .put("default", "./feature/*.js")
        } else {
            JSONObject()
                .put("browser", "./feature/*.js")
                .put("development", "./feature/*.js")
        }
    }

    private fun Random.arrayTarget(): JSONArray {
        return JSONArray()
            .put(safeTarget())
            .put(unsafeTarget())
    }

    private fun <T> Random.choose(values: List<T>): T {
        return values[nextInt(values.size)]
    }

    private data class FuzzCase(
        val index: Int,
        val seed: Long,
        val packageName: String,
        val packageJson: JSONObject,
        val exportRequests: List<String>,
        val importRequests: List<String>,
    )

    companion object {
        private const val BASE_SEED = 108820612L
        private const val CASE_SEED_STEP = 104729L
        private const val CASE_COUNT = 21
        private const val SUMMARY_PREFIX = "resolver.fuzzer.summary="
        private const val ESCAPE_MARKER = "AUTOJS6_RESOLVER_FUZZ_ESCAPE_LOADED"

        private val EXPORT_SUBPATHS = listOf(
            "",
            "/sub",
            "/private/a",
            "/feature/a",
            "/bad",
            "/arr",
            "/cond",
            "/multi/a/b",
            "/badpattern/a",
            "/missing",
        )

        private val IMPORT_SPECIFIERS = listOf(
            "#ok",
            "#cond",
            "#null",
            "#bad",
            "#arr",
            "#pat/a",
            "#multi/a/b",
            "#badpattern/a",
            "#missing",
        )

        private val STABLE_RESOLVER_ERROR_CODES = setOf(
            "MODULE_NOT_FOUND",
            "ERR_PACKAGE_PATH_NOT_EXPORTED",
            "ERR_PACKAGE_IMPORT_NOT_DEFINED",
            "ERR_INVALID_PACKAGE_TARGET",
            "ERR_AUTOJS6_NATIVE_ADDON_DISABLED",
        )
    }
}
