package org.autojs.autojs.engine

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeModernCoreDifferentialInstrumentationTest {

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
    fun fixtureMetadataIsCompleteAndSafe() {
        val workDir = copyCorpusToTemp("node-modern-core-differential-metadata")
        try {
            val corpusRoot = File(workDir, CORPUS_ASSET_DIR)
            val fixtures = readFixtures(corpusRoot)
            assertEquals(
                "unexpected modern core fixture order",
                listOf(
                    "esm",
                    "process-permission",
                    "async-local-storage",
                    "perf-hooks",
                    "diagnostics-channel",
                    "node-test",
                    "timers-promises",
                    "stream-promises",
                    "webcrypto",
                    "database",
                ),
                fixtures.map { it.name },
            )
            assertEquals("unexpected modern core fixture count", 10, fixtures.size)
            assertEquals(
                "modern core areas should be uniquely covered",
                fixtures.size,
                fixtures.map { it.area }.toSet().size,
            )

            fixtures.forEach { fixture ->
                assertTrue("missing fixture dir: ${fixture.name}", File(corpusRoot, fixture.name).isDirectory)
                assertTrue("missing test.cjs for ${fixture.name}", File(corpusRoot, "${fixture.name}/test.cjs").isFile)
                assertTrue("missing expected.txt for ${fixture.name}", File(corpusRoot, "${fixture.name}/expected.txt").isFile)
                assertTrue(
                    "unsupported classification for ${fixture.name}: ${fixture.expectedClassification}",
                    fixture.expectedClassification in CLASSIFICATIONS,
                )
            }

            corpusRoot.walkTopDown().filter { it.isFile }.forEach { file ->
                val relativePath = file.relativeTo(corpusRoot).invariantSeparatorsPath
                assertFalse("native addon must not be vendored: $relativePath", file.name.endsWith(".node"))
                if (file.name == "package.json") {
                    val json = JSONObject(file.readText())
                    val scripts = json.optJSONObject("scripts")
                    if (scripts != null) {
                        FORBIDDEN_PACKAGE_SCRIPTS.forEach { scriptName ->
                            assertFalse(
                                "package install script must not be present in $relativePath: $scriptName",
                                scripts.has(scriptName),
                            )
                        }
                    }
                }
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun modernCoreDifferentialCorpusPassesAndEmitsAutoJsResults() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("modern core differential corpus")

        val workDir = copyCorpusToTemp("node-modern-core-differential")
        try {
            val corpusRoot = File(workDir, CORPUS_ASSET_DIR)
            val fixtures = readFixtures(corpusRoot)
            val expectedByFixture = fixtures.associate { fixture ->
                fixture.name to File(corpusRoot, "${fixture.name}/expected.txt").readText().trim()
            }
            val fixturePayload = fixtures.joinToString(prefix = "[", postfix = "]") { fixture ->
                "{" +
                    "name:${jsString(fixture.name)}," +
                    "area:${jsString(fixture.area)}," +
                    "expectedClassification:${jsString(fixture.expectedClassification)}" +
                    "}"
            }
            val source = """
                "nodejs";
                (async () => {
                  const fixtures = $fixturePayload;
                  const expected = ${jsObject(expectedByFixture)};
                  for (const fixture of fixtures) {
                    const name = fixture.name;
                    const run = require("./$CORPUS_ASSET_DIR/" + name + "/test.cjs");
                    if (typeof run !== "function") {
                      throw new Error("modern core fixture " + name + " does not export a runner");
                    }
                    const captured = [];
                    const originalLog = console.log;
                    console.log = function captureFixtureLog() {
                      const line = Array.prototype.map.call(arguments, String).join(" ");
                      captured.push(line);
                    };
                    try {
                      const result = await run();
                      if (result !== "PASS") {
                        throw new Error("modern core fixture " + name + " returned " + result);
                      }
                      console.log = originalLog;
                      const stdout = captured.join("\n");
                      originalLog("moderncore.differential.result=" + JSON.stringify({
                        runtime: "autojs6",
                        fixture: name,
                        area: fixture.area,
                        expectedClassification: fixture.expectedClassification,
                        exitCode: 0,
                        stdout,
                        stderr: "",
                        errorCode: "",
                        errorMessage: "",
                        errorMessagePattern: "",
                        expectedOutput: expected[name] || "",
                        expectedOutputFound: stdout.indexOf(expected[name] || "") >= 0,
                      }));
                    } catch (error) {
                      console.log = originalLog;
                      originalLog("moderncore.differential.result=" + JSON.stringify({
                        runtime: "autojs6",
                        fixture: name,
                        area: fixture.area,
                        expectedClassification: fixture.expectedClassification,
                        exitCode: 1,
                        stdout: captured.join("\n"),
                        stderr: "",
                        errorCode: String(error && (error.autojs6Code || error.code || "")),
                        errorMessage: String(error && error.message || error),
                        errorMessagePattern: String(error && error.message || error).replace(/\d+/g, "<n>"),
                        expectedOutput: expected[name] || "",
                        expectedOutputFound: false,
                      }));
                      throw error;
                    }
                  }
                  console.log("moderncore.corpus.total=" + fixtures.length);
                })().catch((error) => {
                  console.error("moderncore.corpus.error=" + (error && (error.stack || error.message) || error));
                  process.exitCode = 1;
                });
            """.trimIndent()

            val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(
                listOf(NodeBridgePermissionManifest.DATABASE),
            )
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-modern-core-differential-runner.js>",
                workingDirectory = workDir,
                timeoutMs = 20_000L,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
                esmExperimentalEnabled = true,
                dynamicImportExperimentalEnabled = true,
            )

            NodeEmbeddedTestHarness.assertSucceeded("modern core differential corpus", result)
            fixtures.forEach { fixture ->
                val expected = File(corpusRoot, "${fixture.name}/expected.txt").readText().trim()
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing expected output for ${fixture.name}", result),
                    result.stdout.contains(expected),
                )
                val differentialRecord = result.stdout
                    .split("moderncore.differential.result=")
                    .drop(1)
                    .firstOrNull { record -> record.contains("\"fixture\":\"${fixture.name}\"") }
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing differential JSON for ${fixture.name}", result),
                    differentialRecord != null && differentialRecord.contains("\"runtime\":\"autojs6\""),
                )
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing expected output flag for ${fixture.name}", result),
                    differentialRecord != null && differentialRecord.contains("\"expectedOutputFound\":true"),
                )
            }
            assertTrue(result.stdout.contains("moderncore.corpus.total=${fixtures.size}"))

            result.stdout.lineSequence()
                .filter { it.startsWith("moderncore.differential.result=") }
                .forEach(::println)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun copyCorpusToTemp(prefix: String): File {
        val workDir = File(context.cacheDir, "$prefix-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create modern core differential work dir: $absolutePath" }
        }
        NodeEmbeddedTestHarness.copyAssetTree(CORPUS_ASSET_DIR, File(workDir, CORPUS_ASSET_DIR))
        return workDir
    }

    private fun readFixtures(corpusRoot: File): List<ModernCoreFixture> {
        val manifest = JSONObject(File(corpusRoot, "manifest.json").readText())
        assertEquals("modern-core-differential-v1", manifest.getString("version"))
        val array = manifest.getJSONArray("fixtures")
        return (0 until array.length()).map { index ->
            val fixture = array.getJSONObject(index)
            ModernCoreFixture(
                name = fixture.getString("name"),
                area = fixture.getString("area"),
                expectedClassification = fixture.getString("expectedClassification"),
            )
        }
    }

    private fun jsString(value: String): String {
        return buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
    }

    private fun jsObject(values: Map<String, String>): String {
        return values.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${jsString(key)}:${jsString(value)}"
        }
    }

    private data class ModernCoreFixture(
        val name: String,
        val area: String,
        val expectedClassification: String,
    )

    companion object {
        private const val CORPUS_ASSET_DIR = "node_modern_core_differential"
        private val CLASSIFICATIONS = setOf(
            "exact match",
            "intentional AutoJs6 restriction",
            "partial",
            "bug",
        )
        private val FORBIDDEN_PACKAGE_SCRIPTS = setOf("preinstall", "install", "postinstall", "prepare")
    }
}
