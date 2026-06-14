package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeCompatCorpusInstrumentationTest {

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
    fun pureJavaScriptCommonJsCorpusPasses() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node compatibility corpus")

        val workDir = File(context.cacheDir, "node-compat-corpus-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create compatibility corpus work dir: $absolutePath" }
        }
        try {
            NodeEmbeddedTestHarness.copyAssetTree("node_compat_corpus", File(workDir, "node_compat_corpus"))
            val fixtures = listOf(
                "cjs-basic",
                "cjs-deep-deps",
                "cjs-package-exports",
                "cjs-package-imports",
                "cjs-self-reference",
                "cjs-json-cache",
                "cjs-fs-promisify",
                "cjs-buffer-path-util",
                "cjs-disabled-feature-detection",
            )
            val source = """
                "nodejs";
                (async () => {
                  const fixtures = ${fixtures.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }};
                  for (const name of fixtures) {
                    const fixturePath = "./node_compat_corpus/" + name;
                    const fixture = require(fixturePath);
                    const run = typeof fixture === "function" ? fixture : fixture.run;
                    if (typeof run !== "function") {
                      throw new Error("fixture " + name + " does not export a runner");
                    }
                    const result = await run();
                    if (result !== "PASS") {
                      throw new Error("fixture " + name + " returned " + result);
                    }
                    console.log("compat.fixture." + name + "=PASS");
                  }
                  console.log("compat.corpus.total=" + fixtures.length);
                })().catch((error) => {
                  console.error("compat.corpus.error=" + (error && (error.stack || error.message) || error));
                  process.exitCode = 1;
                });
            """.trimIndent()

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-compat-corpus-runner.js>",
                workingDirectory = workDir,
                timeoutMs = 15_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node compatibility corpus", result)
            fixtures.forEach { fixture ->
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing fixture output $fixture", result),
                    result.stdout.contains("compat.fixture.$fixture=PASS"),
                )
            }
            assertTrue(result.stdout.contains("compat.corpus.total=${fixtures.size}"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun corpusSubsetRunsFromModuleSourcesPreloadShape() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node compatibility moduleSources corpus")

        val workDir = File(context.cacheDir, "node-compat-preload-corpus-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create compatibility preload corpus work dir: $absolutePath" }
        }
        try {
            val main = File(workDir, "main.js").apply {
                writeText(""""nodejs";""" + "\n")
            }
            val preloaded = File(workDir, "preloaded-fixture.js").apply {
                writeText("""module.exports = () => "DISK";""" + "\n")
            }
            val source = """
                "nodejs";
                const fixture = require("./preloaded-fixture");
                Promise.resolve(fixture()).then((result) => {
                  if (result !== "PASS") {
                    throw new Error("preloaded fixture returned " + result);
                  }
                  console.log("compat.preload.fixture=PASS");
                }).catch((error) => {
                  console.error("compat.preload.error=" + (error && (error.stack || error.message) || error));
                  process.exitCode = 1;
                });
            """.trimIndent()
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = main.absolutePath,
                workingDirectory = workDir,
                moduleSources = mapOf(
                    preloaded.absolutePath to """
                        const assert = require("assert");
                        const path = require("path");
                        module.exports = async function run() {
                          assert.strictEqual(path.basename("/tmp/preloaded.txt"), "preloaded.txt");
                          assert.deepStrictEqual({ ok: true }, { ok: true });
                          return "PASS";
                        };
                    """.trimIndent(),
                ),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node compatibility preloaded corpus subset", result)
            assertTrue(result.stdout, result.stdout.contains("compat.preload.fixture=PASS"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
