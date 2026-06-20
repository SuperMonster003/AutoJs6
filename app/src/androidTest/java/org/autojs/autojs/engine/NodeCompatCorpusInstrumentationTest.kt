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

    @Test
    fun moduleConformanceV2CorpusPasses() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node module conformance v2 corpus")

        val workDir = File(context.cacheDir, "node-module-conformance-v2-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create module conformance v2 work dir: $absolutePath" }
        }
        try {
            val corpusDir = File(workDir, "node_compat_corpus_v2")
            NodeEmbeddedTestHarness.copyAssetTree("node_compat_corpus_v2", corpusDir)
            val checks = listOf(
                "require_esm",
                "tla_rejection",
                "json_attributes",
                "esm_cjs_cycle",
                "import_meta_resolve",
                "package_custom_condition",
                "typescript_mixed_graph",
            )
            val source = """
                "nodejs";
                (async () => {
                  const assert = require("assert");

                  const syncEsm = require("./sync-esm.mjs");
                  assert.strictEqual(syncEsm.named, "sync-esm");
                  assert.deepStrictEqual(syncEsm.default, { value: "sync-esm", count: 2 });
                  console.log("compat.v2.require_esm=PASS");

                  let asyncRejected = false;
                  try {
                    require("./async-esm.mjs");
                  } catch (error) {
                    asyncRejected = error && error.code === "ERR_REQUIRE_ASYNC_MODULE";
                  }
                  assert.strictEqual(asyncRejected, true);
                  console.log("compat.v2.tla_rejection=PASS");

                  const cycle = require("./cjs-cycle.cjs");
                  assert.deepStrictEqual(cycle.result(), {
                    fromEsm: "esm-cycle",
                    esmSawCjs: "cjs-cycle",
                    esmSawDone: false,
                    seen: "esm-cycle",
                    done: true,
                  });
                  console.log("compat.v2.esm_cjs_cycle=PASS");

                  const harness = await import("./esm-harness.mjs");
                  assert.strictEqual(harness.default.json, "json-attrs/dynamic-json");
                  assert.strictEqual(harness.default.jsonCacheShared, true);
                  assert.strictEqual(harness.default.customCondition, "autojs6");
                  assert.strictEqual(harness.default.tsMixed, "mts/cts");
                  assert.strictEqual(harness.default.metaResolve, true);
                  console.log("compat.v2.json_attributes=PASS");
                  console.log("compat.v2.import_meta_resolve=PASS");
                  console.log("compat.v2.package_custom_condition=PASS");
                  console.log("compat.v2.typescript_mixed_graph=PASS");
                  console.log("compat.v2.total=${checks.size}");
                })().catch((error) => {
                  console.error("compat.v2.error=" + (error && (error.stack || error.message) || error));
                  process.exitCode = 1;
                });
            """.trimIndent()

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = File(corpusDir, "main.cjs").absolutePath,
                workingDirectory = corpusDir,
                esmExperimentalEnabled = true,
                timeoutMs = 15_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node module conformance v2 corpus", result)
            checks.forEach { check ->
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing module conformance v2 output $check", result),
                    result.stdout.contains("compat.v2.$check=PASS"),
                )
            }
            assertTrue(result.stdout.contains("compat.v2.total=${checks.size}"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun moduleConformanceV2PackagedModuleSourcesGraphPasses() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node module conformance v2 packaged moduleSources graph")

        val workDir = File(context.cacheDir, "node-module-conformance-v2-packaged-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create module conformance v2 packaged work dir: $absolutePath" }
        }
        try {
            val esmDep = File(workDir, "packaged-esm.mjs")
            val cjsDep = File(workDir, "packaged-cjs.cjs")
            val jsonDep = File(workDir, "packaged-data.json")
            val tsEsmDep = File(workDir, "packaged-ts.mts")
            val tsCjsDep = File(workDir, "packaged-ts-cjs.cts")
            val dynamicDep = File(workDir, "packaged-dynamic.mjs")
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    import esmValue from "./packaged-esm.mjs";
                    import cjsValue from "./packaged-cjs.cjs";
                    import jsonData from "./packaged-data.json" with { type: "json" };
                    import tsValue from "./packaged-ts.mts";
                    const dynamic = await import("./packaged-dynamic.mjs");
                    console.log("compat.v2.packaged=" + [
                      esmValue,
                      cjsValue.value,
                      jsonData.name,
                      tsValue,
                      dynamic.default,
                    ].join("/"));
                """.trimIndent(),
                sourceName = File(workDir, "packaged-main.mjs").absolutePath,
                workingDirectory = workDir,
                moduleSources = mapOf(
                    esmDep.absolutePath to """export default "esm";""",
                    cjsDep.absolutePath to """module.exports = { value: "cjs" };""",
                    jsonDep.absolutePath to """{"name":"json"}""",
                    tsEsmDep.absolutePath to """
                        import cjs from "./packaged-ts-cjs.cts";
                        type Label = string;
                        const label: Label = "mts";
                        export default label + ":" + cjs.value;
                    """.trimIndent(),
                    tsCjsDep.absolutePath to """
                        type Label = string;
                        const value: Label = "cts";
                        module.exports = { value };
                    """.trimIndent(),
                    dynamicDep.absolutePath to """export default "dynamic";""",
                ),
                esmExperimentalEnabled = true,
                timeoutMs = 15_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node module conformance v2 packaged moduleSources graph", result)
            assertTrue(result.stdout, result.stdout.contains("compat.v2.packaged=esm/cjs/json/mts:cts/dynamic"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
