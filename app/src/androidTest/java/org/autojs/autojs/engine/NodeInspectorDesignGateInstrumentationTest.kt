package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeInspectorDesignGateInstrumentationTest {

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
    fun inspectorFeatureFlagCannotBeEnabledByMetadata() {
        val flag = NodeRuntimeCapabilities.featureFlagsById.getValue("inspector")
        assertEquals("disabled", flag.status)
        assertEquals("high", flag.securityLevel)
        assertEquals(null, flag.gradleProperty)
        assertFalse(flag.defaultEnabled)
        assertFalse(flag.buildTimeOverrideAllowed)
        assertFalse(flag.projectJsonOverrideAllowed)
        assertFalse(flag.packagedApkOverrideAllowed)
        assertFalse(flag.buildOnly)
        assertFalse(flag.debugOnly)
        assertTrue(flag.releaseForbidden)
        assertTrue(NodeRuntimeCapabilities.deniedFeatureFlags.any { it.id == "inspector" })
        assertFalse(NodeRuntimeCapabilities.buildOnlyFeatureFlags.any { it.id == "inspector" })
        assertFalse(NodeRuntimeCapabilities.debugOnlyFeatureFlags.any { it.id == "inspector" })
        assertFalse(NodeRuntimeCapabilities.projectAllowedFeatureFlags.any { it.id == "inspector" })
        assertFalse(NodeRuntimeCapabilities.packagedAllowedFeatureFlags.any { it.id == "inspector" })

        val debugAudit = NodeRuntimeCapabilities.auditBuildTimeFeatureFlags(
            requestedFlags = mapOf("inspector" to true),
            releaseBuild = false,
            source = "debug-build",
        )
        assertTrue(debugAudit.hasRejectedOverrides)
        assertEquals(listOf("inspector"), debugAudit.rejectedFeatureIds)
        assertEquals("build_time_override_denied", debugAudit.rejected.first().reason)

        val releaseAudit = NodeRuntimeCapabilities.auditBuildTimeFeatureFlags(
            requestedFlags = mapOf("inspector" to true),
            releaseBuild = true,
            source = "release-build",
        )
        assertTrue(releaseAudit.hasRejectedOverrides)
        assertEquals(listOf("inspector"), releaseAudit.rejectedFeatureIds)
        assertEquals("release_forbidden", releaseAudit.rejected.first().reason)

        val projectAudit = NodeRuntimeCapabilities.auditProjectJsonFeatureFlags(
            """
            {
              "type": "node",
              "node": {
                "featureFlags": {
                  "inspector": true
                }
              }
            }
            """.trimIndent(),
        )
        assertTrue(projectAudit.hasRejectedOverrides)
        assertEquals(listOf("inspector"), projectAudit.rejectedFeatureIds)
        assertEquals("project_json_override_denied", projectAudit.rejected.first().reason)

        val packageAudit = NodeRuntimeCapabilities.auditPackageJsonFeatureFlags(
            """
            {
              "name": "inspector-package",
              "autojs6": {
                "node": {
                  "featureFlags": {
                    "inspector": true
                  }
                }
              }
            }
            """.trimIndent(),
        )
        assertTrue(packageAudit.hasRejectedOverrides)
        assertEquals(listOf("inspector"), packageAudit.rejectedFeatureIds)
        assertEquals("packaged_apk_override_denied", packageAudit.rejected.first().reason)
    }

    @Test
    fun scriptVisibleInspectorSurfacesStayDisabled() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node inspector design gate")

        val workDir = File(context.cacheDir, "node-inspector-design-gate-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create inspector design gate work dir: $absolutePath" }
        }
        File(workDir, "node_modules/inspector").apply {
            check(mkdirs()) { "Failed to create inspector shadow module dir: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true };""")

        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const disabledBuiltinCode = "ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED";
                    const disabledProcessCode = "ERR_AUTOJS6_PROCESS_API_DISABLED";
                    function errorCode(action) {
                      try {
                        action();
                        return "allowed";
                      } catch (error) {
                        return error && (error.code || error.autojs6Code) || "";
                      }
                    }
                    const flags = process.allowedNodeEnvironmentFlags;
                    flags.add("--inspect");
                    const debugProcessCode = typeof process._debugProcess === "function"
                      ? errorCode(() => process._debugProcess(process.pid))
                      : disabledProcessCode;
                    const checks = [
                      errorCode(() => require("inspector")) === disabledBuiltinCode,
                      errorCode(() => require("node:inspector")) === disabledBuiltinCode,
                      errorCode(() => require.resolve("inspector")) === disabledBuiltinCode,
                      errorCode(() => require.resolve("node:inspector")) === disabledBuiltinCode,
                      errorCode(() => process.getBuiltinModule("inspector")) === disabledBuiltinCode,
                      debugProcessCode === disabledProcessCode,
                      Array.isArray(process.execArgv) && process.execArgv.length === 0,
                      flags instanceof Set &&
                        Object.isFrozen(flags) &&
                        flags.size === 0 &&
                        flags.has("--inspect") === false &&
                        Array.from(flags).length === 0,
                      Object.isFrozen(process.features) &&
                        process.features.inspector === false &&
                        process.features.debug === false,
                      process.env.NODE_OPTIONS === undefined,
                      process.env.NODE_INSPECT_RESUME_ON_START === undefined
                    ];
                    console.log("inspector.design_gate=" + checks.join("/"));
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                env = mapOf(
                    "NODE_OPTIONS" to "--inspect=0.0.0.0:0 --inspect-brk",
                    "NODE_INSPECT_RESUME_ON_START" to "1",
                ),
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node inspector design gate", result)
            assertTrue(
                result.stdout,
                result.stdout.contains("inspector.design_gate=true/true/true/true/true/true/true/true/true/true/true"),
            )
            assertFalse(
                "Inspector diagnostics should not appear while the design gate is disabled: ${result.nativeValues}",
                result.nativeValues.keys.any { it.contains("inspector", ignoreCase = true) },
            )
        } finally {
            workDir.deleteRecursively()
        }
    }
}
