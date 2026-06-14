package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NodeFeatureFlagRegistryInstrumentationTest {

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun featureFlagRegistryDefaultsStaySafe() {
        val flags = NodeRuntimeCapabilities.featureFlags
        val ids = flags.map { it.id }.toSet()
        assertEquals(
            setOf(
                "esm",
                "dynamic_import",
                "network",
                "raw_node_network_modules",
                "worker_threads",
                "fs_streams",
                "zlib_streams",
                "vm",
                "inspector",
                "java_interop",
                "native_addon",
                "child_process",
            ),
            ids,
        )

        assertFalse(BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED)
        assertFalse(BuildConfig.NODEJS_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED)
        assertFalse(BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED)
        assertFalse(BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED)
        assertEquals(false, NodeRuntimeCapabilities.experimentalFlags[NodeRuntimeCapabilities.ESM_EXPERIMENTAL_PROPERTY])
        assertEquals(false, NodeRuntimeCapabilities.experimentalFlags[NodeRuntimeCapabilities.DYNAMIC_IMPORT_EXPERIMENTAL_PROPERTY])
        assertEquals(false, NodeRuntimeCapabilities.experimentalFlags[NodeRuntimeCapabilities.NETWORK_EXPERIMENTAL_PROPERTY])
        assertEquals(false, NodeRuntimeCapabilities.experimentalFlags[NodeRuntimeCapabilities.WORKER_THREADS_EXPERIMENTAL_PROPERTY])

        flags
            .filter { it.securityLevel == "high" || it.securityLevel == "critical" }
            .forEach { flag ->
                assertFalse("${flag.id} must not be enabled by default", flag.defaultEnabled)
            }

        flags
            .filter { it.status == "disabled" || it.status == "unsupported" || it.securityLevel == "high" || it.securityLevel == "critical" }
            .forEach { flag ->
                assertFalse("${flag.id} must not allow project.json override", flag.projectJsonOverrideAllowed)
                assertFalse("${flag.id} must not allow packaged APK override", flag.packagedApkOverrideAllowed)
            }

        val buildOverrideAllowed = flags.filter { it.buildTimeOverrideAllowed }.map { it.id }.toSet()
        assertEquals(setOf("esm", "dynamic_import", "network", "worker_threads"), buildOverrideAllowed)
        assertNotNull(NodeRuntimeCapabilities.featureFlagsById["child_process"])
        assertTrue(NodeRuntimeCapabilities.deniedFeatureFlags.any { it.id == "native_addon" })
    }

    @Test
    fun autojsProfileReportsFeatureFlagRegistry() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node feature flag registry profile")

        val source = """
            "nodejs";
            const profile = require("autojs6:profile");
            const expected = {
              esm: false,
              dynamic_import: false,
              network: false,
              raw_node_network_modules: false,
              worker_threads: false,
              fs_streams: true,
              zlib_streams: true,
              vm: false,
              inspector: false,
              java_interop: false,
              native_addon: false,
              child_process: false
            };
            if (!Object.isFrozen(profile.featureFlags)) {
              throw new Error("featureFlags must be frozen");
            }
            for (const [id, enabled] of Object.entries(expected)) {
              const flag = profile.featureFlags[id];
              if (!flag) throw new Error("missing feature flag " + id);
              if (!Object.isFrozen(flag)) throw new Error("feature flag is not frozen: " + id);
              if (flag.enabled !== enabled) {
                throw new Error("unexpected enabled state for " + id + ": " + flag.enabled);
              }
              if (flag.defaultEnabled !== enabled) {
                throw new Error("unexpected default state for " + id + ": " + flag.defaultEnabled);
              }
              if (flag.projectJsonOverrideAllowed !== false || flag.packagedApkOverrideAllowed !== false) {
                throw new Error("unsafe override policy for " + id);
              }
            }
            if (!Array.isArray(profile.enabledExperimentalFlags) || profile.enabledExperimentalFlags.length !== 0) {
              throw new Error("no experimental flags should be enabled by default");
            }
            if (profile.featureFlags.dynamic_import.gradleProperty !== "autojs.nodejs.dynamicImport.experimental") {
              throw new Error("dynamic_import gradle property mismatch");
            }
            if (profile.featureFlags.dynamic_import.status !== "experimental") {
              throw new Error("dynamic_import status mismatch");
            }
            if (profile.featureFlags.dynamic_import.buildTimeOverrideAllowed !== true) {
              throw new Error("dynamic_import build override mismatch");
            }
            if (profile.experimentalFlags.dynamicImport !== false) {
              throw new Error("dynamicImport experimental snapshot mismatch");
            }
            for (const id of ["esm", "dynamic_import", "network", "worker_threads", "child_process", "native_addon", "java_interop"]) {
              if (!profile.deniedFeatureFlags.includes(id)) {
                throw new Error("deniedFeatureFlags missing " + id);
              }
            }
            console.log("feature.flags.ready=true");
        """.trimIndent()

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = source,
            sourceName = "<node-feature-flag-registry.js>",
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("Node feature flag registry profile", result)
        assertTrue(result.stdout, result.stdout.contains("feature.flags.ready=true"))
    }
}
