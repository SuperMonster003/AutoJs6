package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeModuleCompileCacheInstrumentationTest {

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
    fun compileCacheDiagnosticsCoverCjsEsmAndStrippedTypeScript() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node module compile cache diagnostics")

        val workDir = File(context.cacheDir, "node-compile-cache-graph-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create compile cache work dir: $absolutePath" }
        }
        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    import esmValue from "./esm.mjs";
                    import cjsValue from "./lib.cjs";
                    import tsValue from "./typed.mts";
                    console.log("compile.cache.graph=" + [esmValue, cjsValue.value, tsValue].join("/"));
                """.trimIndent(),
                sourceName = File(workDir, "main.mjs").absolutePath,
                workingDirectory = workDir,
                moduleSources = mapOf(
                    File(workDir, "esm.mjs").absolutePath to """export default "esm";""",
                    File(workDir, "lib.cjs").absolutePath to """module.exports = { value: "cjs" };""",
                    File(workDir, "typed.mts").absolutePath to """
                        type Label = string;
                        const label: Label = "ts";
                        export default label;
                    """.trimIndent(),
                ),
                esmEnabled = true,
                timeoutMs = 15_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node module compile cache diagnostics", result)
            assertTrue(result.stdout, result.stdout.contains("compile.cache.graph=esm/cjs/ts"))
            assertCompileCacheEnabled(result, workDir)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun compileCacheControlsStayInternalAndUserEnvCannotOverridePolicy() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node module compile cache policy")

        val workDir = File(context.cacheDir, "node-compile-cache-policy-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create compile cache policy work dir: $absolutePath" }
        }
        val hostileDir = File(workDir, "hostile-cache")
        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const moduleBuiltin = require("node:module");
                    const profile = require("autojs6:profile");
                    console.log("compile.cache.safe_subset=" + [
                      moduleBuiltin.enableCompileCache === undefined,
                      moduleBuiltin.flushCompileCache === undefined,
                      moduleBuiltin.constants === undefined,
                      process.env.AUTOJS6_NODE_COMPILE_CACHE_DIR === undefined,
                      process.env.NODE_COMPILE_CACHE === undefined,
                      process.env.NODE_DISABLE_COMPILE_CACHE === undefined,
                      Object.isFrozen(profile.compileCache),
                      profile.compileCache.appPrivateOnly === true,
                      profile.compileCache.userControlExposed === false,
                      profile.compileCache.requested === true,
                      profile.compileCache.customLoader === true
                    ].join("/"));
                    console.log("compile.cache.profile=" + [
                      profile.compileCache.enabled,
                      profile.compileCache.hitMissStatus,
                      profile.compileCache.schema
                    ].join("/"));
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                env = mapOf(
                    NativeNodeEmbeddedRuntimeBridge.COMPILE_CACHE_ENV_ENABLED to "0",
                    NativeNodeEmbeddedRuntimeBridge.COMPILE_CACHE_ENV_DIR to hostileDir.absolutePath,
                    "NODE_COMPILE_CACHE" to hostileDir.absolutePath,
                    "NODE_DISABLE_COMPILE_CACHE" to "1",
                ),
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node module compile cache policy", result)
            assertTrue(
                result.stdout,
                result.stdout.contains("compile.cache.safe_subset=true/true/true/true/true/true/true/true/true/true/true"),
            )
            assertTrue(result.stdout, result.stdout.contains("compile.cache.profile=true/unavailable_custom_loader/"))
            assertFalse("Hostile compile cache dir should not be used", hostileDir.exists())
            assertCompileCacheEnabled(result, workDir)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun assertCompileCacheEnabled(
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        workDir: File,
    ) {
        val nativeValues = result.nativeValues
        assertEquals("true", nativeValues["embedded_script.compile_cache.requested"])
        assertEquals("true", nativeValues["embedded_script.compile_cache.enabled"])
        assertTrue(
            NodeEmbeddedTestHarness.describeResult("compile cache status", result),
            nativeValues["embedded_script.compile_cache.status"] in setOf("ENABLED", "ALREADY_ENABLED"),
        )
        assertEquals(
            NativeNodeEmbeddedRuntimeBridge.COMPILE_CACHE_SCHEMA_VERSION,
            nativeValues["embedded_script.compile_cache.schema"],
        )
        val directory = nativeValues.getValue("embedded_script.compile_cache.directory")
        val baseDir = NativeNodeEmbeddedRuntimeBridge.compileCacheBaseDir(context).absolutePath
        assertTrue("directory=$directory baseDir=$baseDir", directory.startsWith(baseDir))
        assertFalse("directory should be app-private, not project-local: $directory", directory.startsWith(workDir.absolutePath))
        assertTrue("compile cache directory should exist: $directory", File(directory).isDirectory)
        assertTrue(nativeValues["embedded_script.compile_cache.key"].orEmpty().length >= 32)
        assertTrue(nativeValues["embedded_script.compile_cache.runtime_key"].orEmpty().length >= 16)
        assertTrue(nativeValues["embedded_script.compile_cache.project_key"].orEmpty().length >= 16)
        assertTrue(nativeValues["embedded_script.compile_cache.loader"].orEmpty().contains("autojs6-commonjs-loader-v1.1"))
        assertTrue(nativeValues["embedded_script.compile_cache.v8_version"].orEmpty().isNotBlank())
        assertEquals("true", nativeValues["embedded_script.compile_cache.custom_loader"])
        assertEquals("0", nativeValues["embedded_script.compile_cache.hit_count"])
        assertEquals("0", nativeValues["embedded_script.compile_cache.miss_count"])
        assertEquals("unavailable_custom_loader", nativeValues["embedded_script.compile_cache.hit_miss_status"])
    }
}
