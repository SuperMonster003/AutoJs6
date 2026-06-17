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
            assertEquals("unexpected real npm fixture count", 5, fixtures.size)

            fixtures.forEach { fixture ->
                val fixtureDir = File(corpusRoot, fixture.name)
                assertTrue("missing fixture dir: ${fixture.name}", fixtureDir.isDirectory)
                assertTrue("missing package.json for ${fixture.name}", File(fixtureDir, "package.json").isFile)
                assertTrue("missing test.cjs for ${fixture.name}", File(fixtureDir, "test.cjs").isFile)
                assertTrue("missing expected.txt for ${fixture.name}", File(fixtureDir, "expected.txt").isFile)

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
                    assertFalse("ESM-only fixture package is not allowed: $relativePath", json.optString("type") == "module")
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
                    console.log = function captureFixtureLog() {
                      const line = Array.prototype.map.call(arguments, String).join(" ");
                      captured.push(line);
                      return originalLog.apply(console, arguments);
                    };
                    try {
                      const result = await run();
                      if (result !== "PASS") {
                        throw new Error("real npm fixture " + name + " returned " + result);
                      }
                      console.log = originalLog;
                      const stdout = captured.join("\n");
                      originalLog("realnpm.differential.result=" + JSON.stringify({
                        runtime: "autojs6",
                        fixture: name,
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
                      originalLog("realnpm.differential.result=" + JSON.stringify({
                        runtime: "autojs6",
                        fixture: name,
                        exitCode: 1,
                        stdout: captured.join("\n"),
                        stderr: "",
                        errorCode: String(error && error.code || ""),
                        errorMessage: String(error && error.message || error),
                        errorMessagePattern: String(error && error.message || error).replace(/\d+/g, "<n>"),
                        expectedOutput: expected[name] || "",
                        expectedOutputFound: false,
                      }));
                      throw error;
                    }
                    console.log("realnpm.fixture." + name + "=PASS");
                  }
                  console.log("realnpm.corpus.total=" + fixtures.length);
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
                    result.stdout.contains("\"runtime\":\"autojs6\",\"fixture\":\"${fixture.name}\""),
                )
            }
            assertTrue(result.stdout.contains("realnpm.corpus.total=${fixtures.size}"))

            result.stdout.lineSequence()
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
            RealNpmFixture(array.getJSONObject(index).getString("name"))
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

    private data class RealNpmFixture(val name: String)

    companion object {
        private const val CORPUS_ASSET_DIR = "npm_compat_real"
        private val FORBIDDEN_PACKAGE_SCRIPTS = setOf("preinstall", "install", "postinstall", "prepare")
    }
}
