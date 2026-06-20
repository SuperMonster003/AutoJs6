package org.autojs.autojs.engine

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeRealNpmCompatInstrumentationTest {

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
        val workDir = copyCorpusToTemp("node-real-npm-metadata")
        try {
            val corpusRoot = File(workDir, CORPUS_ASSET_DIR)
            val fixtures = readFixtures(corpusRoot)
            assertEquals("unexpected real npm fixture count", EXPECTED_FIXTURE_COUNT, fixtures.size)
            assertTrue("real npm corpus should grow beyond the Phase 7 baseline", fixtures.size > PHASE7_FIXTURE_COUNT)
            assertEquals(EXPECTED_FIXTURE_COUNT - PHASE7_FIXTURE_COUNT, fixtures.size - PHASE7_FIXTURE_COUNT)
            assertRequiredPatternsPresent(fixtures)

            fixtures.forEach { fixture ->
                val fixtureDir = File(corpusRoot, fixture.name)
                assertTrue("missing fixture dir: ${fixture.name}", fixtureDir.isDirectory)
                assertTrue("missing package.json for ${fixture.name}", File(fixtureDir, "package.json").isFile)
                assertTrue("missing test.cjs for ${fixture.name}", File(fixtureDir, "test.cjs").isFile)
                assertTrue("missing expected.txt for ${fixture.name}", File(fixtureDir, "expected.txt").isFile)
                val rootPackageJson = JSONObject(File(fixtureDir, "package.json").readText())
                assertFalse("fixture runner package must stay CommonJS-compatible: ${fixture.name}", rootPackageJson.optString("type") == "module")

                val licenseNote = File(fixtureDir, "LICENSE_NOTE.md")
                assertTrue("missing license note for ${fixture.name}", licenseNote.isFile)
                assertTrue(
                    "license note should mark fixed snapshots for ${fixture.name}",
                    licenseNote.readText().contains("fixed test snapshots"),
                )
            }

            corpusRoot.walkTopDown().filter { it.isFile }.forEach { file ->
                val relativePath = file.relativeTo(corpusRoot).invariantSeparatorsPath
                assertFalse("native addon must not be vendored: $relativePath", file.name.endsWith(".node"))
                if (file.name == "package.json") {
                    val json = JSONObject(file.readText())
                    if (json.optString("type") == "module") {
                        assertTrue("ESM package metadata is only allowed below node_modules: $relativePath", relativePath.contains("/node_modules/"))
                    }
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
    fun realPureJavaScriptPackageCorpusPasses() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("real npm compatibility corpus")

        val workDir = copyCorpusToTemp("node-real-npm-corpus")
        try {
            val corpusRoot = File(workDir, CORPUS_ASSET_DIR)
            val fixtures = readFixtures(corpusRoot)
            val fixtureNames = fixtures.map { it.name }
            val expectedByFixture = fixtures.associate { fixture ->
                fixture.name to File(corpusRoot, "${fixture.name}/expected.txt").readText().trim()
            }
            val source = """
                "nodejs";
                (async () => {
                  const fixtures = ${fixtureNames.joinToString(prefix = "[", postfix = "]") { jsString(it) }};
                  const expected = ${jsObject(expectedByFixture)};
                  for (const name of fixtures) {
                    const run = require("./$CORPUS_ASSET_DIR/" + name + "/test.cjs");
                    if (typeof run !== "function") {
                      throw new Error("real npm fixture " + name + " does not export a runner");
                    }
                    const captured = [];
                    const originalLog = console.log;
                    const originalError = console.error;
                    console.log = function captureFixtureLog() {
                      const line = Array.prototype.map.call(arguments, String).join(" ");
                      captured.push(line);
                    };
                    try {
                      const result = await run();
                      if (result !== "PASS") {
                        throw new Error("real npm fixture " + name + " returned " + result);
                      }
                      console.log = originalLog;
                      const stdout = captured.join("\n");
                      const expectedOutput = expected[name] || "";
                      const expectedOutputFound = stdout.indexOf(expectedOutput) >= 0;
                      if (stdout.length > 0) {
                        originalLog(stdout);
                      }
                      originalLog("realnpm.fixture." + name + "=PASS");
                      originalError("realnpm.differential.result=" + JSON.stringify({
                        runtime: "autojs6",
                        fixture: name,
                        exitCode: 0,
                        expectedOutputFound,
                      }));
                    } catch (error) {
                      console.log = originalLog;
                      const stdout = captured.join("\n");
                      if (stdout.length > 0) {
                        originalLog(stdout);
                      }
                      originalError("realnpm.differential.result=" + JSON.stringify({
                        runtime: "autojs6",
                        fixture: name,
                        exitCode: 1,
                        errorCode: String(error && error.code || ""),
                        errorMessage: String(error && error.message || error),
                        errorMessagePattern: String(error && error.message || error).replace(/\d+/g, "<n>"),
                      }));
                      throw error;
                    }
                  }
                  console.log("realnpm.corpus.baseline=$V1_0_BASELINE_FIXTURE_COUNT");
                  console.log("realnpm.corpus.phase7=$PHASE7_FIXTURE_COUNT");
                  console.log("realnpm.corpus.total=" + fixtures.length);
                  console.log("realnpm.corpus.delta=" + (fixtures.length - $V1_0_BASELINE_FIXTURE_COUNT));
                  console.log("realnpm.corpus.phase8_delta=" + (fixtures.length - $PHASE7_FIXTURE_COUNT));
                })().catch((error) => {
                  console.error("realnpm.corpus.error=" + (error && (error.stack || error.message) || error));
                  process.exitCode = 1;
                });
            """.trimIndent()

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-real-npm-compat-runner.js>",
                workingDirectory = workDir,
                timeoutMs = 20_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("real npm compatibility corpus", result)
            val diagnosticOutput = result.stdout + "\n" + result.stderr
            fixtures.forEach { fixture ->
                val expected = File(corpusRoot, "${fixture.name}/expected.txt").readText().trim()
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing expected output for ${fixture.name}", result),
                    result.stdout.contains(expected),
                )
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing fixture pass marker for ${fixture.name}", result),
                    result.stdout.contains("realnpm.fixture.${fixture.name}=PASS"),
                )
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing differential JSON for ${fixture.name}", result),
                    diagnosticOutput.contains("\"runtime\":\"autojs6\",\"fixture\":\"${fixture.name}\""),
                )
            }
            assertTrue(result.stdout.contains("realnpm.corpus.baseline=$V1_0_BASELINE_FIXTURE_COUNT"))
            assertTrue(result.stdout.contains("realnpm.corpus.phase7=$PHASE7_FIXTURE_COUNT"))
            assertTrue(result.stdout.contains("realnpm.corpus.total=${fixtures.size}"))
            assertTrue(result.stdout.contains("realnpm.corpus.delta=${fixtures.size - V1_0_BASELINE_FIXTURE_COUNT}"))
            assertTrue(result.stdout.contains("realnpm.corpus.phase8_delta=${fixtures.size - PHASE7_FIXTURE_COUNT}"))

            diagnosticOutput.lineSequence()
                .filter { it.startsWith("realnpm.differential.result=") }
                .forEach(::println)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun copyCorpusToTemp(prefix: String): File {
        val workDir = File(context.cacheDir, "$prefix-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create real npm corpus work dir: $absolutePath" }
        }
        NodeEmbeddedTestHarness.copyAssetTree(CORPUS_ASSET_DIR, File(workDir, CORPUS_ASSET_DIR))
        return workDir
    }

    private fun readFixtures(corpusRoot: File): List<RealNpmFixture> {
        val manifest = JSONObject(File(corpusRoot, "manifest.json").readText())
        val array = manifest.getJSONArray("fixtures")
        return (0 until array.length()).map { index ->
            val fixture = array.getJSONObject(index)
            val patterns = fixture.optJSONArray("patterns")
            RealNpmFixture(
                name = fixture.getString("name"),
                patterns = (0 until (patterns?.length() ?: 0)).map { patternIndex -> patterns!!.getString(patternIndex) },
            )
        }
    }

    private fun assertRequiredPatternsPresent(fixtures: List<RealNpmFixture>) {
        val patterns = fixtures.flatMap { it.patterns }.toSet()
        REQUIRED_REAL_NPM_PATTERNS.forEach { pattern ->
            assertTrue("missing real npm pattern: $pattern in $patterns", pattern in patterns)
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

    private data class RealNpmFixture(val name: String, val patterns: List<String>)

    companion object {
        private const val CORPUS_ASSET_DIR = "npm_compat_real"
        private const val V1_0_BASELINE_FIXTURE_COUNT = 7
        private const val PHASE7_FIXTURE_COUNT = 13
        private const val EXPECTED_FIXTURE_COUNT = 20
        private val FORBIDDEN_PACKAGE_SCRIPTS = setOf("preinstall", "install", "postinstall", "prepare")
        private val REQUIRED_REAL_NPM_PATTERNS = setOf(
            "ESM-only small package",
            "hybrid CJS/ESM package",
            "AsyncLocalStorage",
            "streams/promises",
            "WebCrypto",
            "fetch wrapper fallback",
            "worker CPU package",
            "test-runner package",
            "database abstraction package",
            "native TypeScript source project",
            "hybrid require(esm)",
            "package using node:http client",
            "package using FormData/fetch",
            "package using FileHandle",
            "package using fs.watch",
            "package benefiting from compile cache",
        )
    }
}
