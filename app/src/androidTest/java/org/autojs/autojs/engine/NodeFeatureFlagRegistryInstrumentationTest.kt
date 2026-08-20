package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeFeatureFlagRegistryInstrumentationTest {

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
    fun featureFlagRegistryDefaultsExposeStableCapabilities() {
        val flags = NodeRuntimeCapabilities.featureFlags
        val ids = flags.map { it.id }.toSet()
        assertEquals(
            setOf(
                "esm",
                "dynamic_import",
                "network",
                "raw_node_network_modules",
                "websocket",
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

        assertTrue(BuildConfig.NODEJS_ESM_ENABLED)
        assertTrue(BuildConfig.NODEJS_DYNAMIC_IMPORT_ENABLED)
        assertTrue(BuildConfig.NODEJS_NETWORK_ENABLED)
        assertTrue(BuildConfig.NODEJS_RAW_NETWORK_MODULES_ENABLED)
        assertTrue(BuildConfig.NODEJS_WORKER_THREADS_ENABLED)
        assertTrue(BuildConfig.NODEJS_CHILD_PROCESS_ENABLED)
        assertTrue(BuildConfig.NODEJS_JAVA_INTEROP_ENABLED)
        assertTrue(NodeRuntimeCapabilities.featureFlagsById.getValue("esm").defaultEnabled)
        assertEquals("stable", NodeRuntimeCapabilities.featureFlagsById.getValue("esm").status)
        assertTrue(NodeRuntimeCapabilities.featureFlagsById.getValue("dynamic_import").defaultEnabled)
        assertEquals("stable", NodeRuntimeCapabilities.featureFlagsById.getValue("dynamic_import").status)

        flags
            .filter { it.status == "stable" }
            .forEach { flag ->
                assertTrue("${flag.id} must be enabled by default", flag.defaultEnabled)
            }

        flags
            .filter { it.status == "disabled" || it.status == "unsupported" || it.securityLevel == "high" || it.securityLevel == "critical" }
            .forEach { flag ->
                assertFalse("${flag.id} must not allow project.json override", flag.projectJsonOverrideAllowed)
                assertFalse("${flag.id} must not allow packaged APK override", flag.packagedApkOverrideAllowed)
            }

        val buildOverrideAllowed = flags.filter { it.buildTimeOverrideAllowed }.map { it.id }.toSet()
        assertEquals(emptySet<String>(), buildOverrideAllowed)
        assertEquals(buildOverrideAllowed, NodeRuntimeCapabilities.buildOnlyFeatureFlags.map { it.id }.toSet())
        assertEquals(emptySet<String>(), NodeRuntimeCapabilities.debugOnlyFeatureFlags.map { it.id }.toSet())
        assertEquals(emptySet<String>(), NodeRuntimeCapabilities.projectAllowedFeatureFlags.map { it.id }.toSet())
        assertEquals(emptySet<String>(), NodeRuntimeCapabilities.packagedAllowedFeatureFlags.map { it.id }.toSet())
        assertFalse(NodeRuntimeCapabilities.releaseForbiddenFeatureFlags.any { it.id == "esm" })
        assertFalse(NodeRuntimeCapabilities.releaseForbiddenFeatureFlags.any { it.id == "dynamic_import" })
        assertFalse(NodeRuntimeCapabilities.releaseForbiddenFeatureFlags.any { it.id == "network" })
        assertFalse(NodeRuntimeCapabilities.releaseForbiddenFeatureFlags.any { it.id == "raw_node_network_modules" })
        assertFalse(NodeRuntimeCapabilities.releaseForbiddenFeatureFlags.any { it.id == "vm" })
        assertFalse(NodeRuntimeCapabilities.releaseForbiddenFeatureFlags.any { it.id == "java_interop" })
        assertFalse(NodeRuntimeCapabilities.featureFlagsById.getValue("fs_streams").releaseForbidden)
        assertNotNull(NodeRuntimeCapabilities.featureFlagsById["child_process"])
        assertTrue(NodeRuntimeCapabilities.deniedFeatureFlags.any { it.id == "native_addon" })
    }

    @Test
    fun featureFlagAuditRejectsProjectAndPackagedMetadataOverrides() {
        val projectAudit = NodeRuntimeCapabilities.auditProjectJsonFeatureFlags(
            """
            {
              "type": "node",
              "node": {
                "featureFlags": {
                  "network": true,
                  "javaInterop": true,
                  "unknownFutureFlag": true
                }
              }
            }
            """.trimIndent(),
        )

        assertTrue(projectAudit.hasRejectedOverrides)
        assertEquals(
            setOf("network", "unknownfutureflag", "java_interop"),
            projectAudit.rejectedFeatureIds.toSet(),
        )
        assertTrue(projectAudit.rejected.any { it.reason == "project_json_override_denied" && it.normalizedId == "network" })
        assertTrue(projectAudit.rejected.any { it.reason == "unknown_flag" && it.normalizedId == "unknownfutureflag" })
        assertTrue(projectAudit.warnings.any { it.contains("unknownFutureFlag") })

        val packageAudit = NodeRuntimeCapabilities.auditPackageJsonFeatureFlags(
            """
            {
              "name": "feature-flag-package",
              "autojs6": {
                "node": {
                  "featureFlags": { "network": true }
                }
              }
            }
            """.trimIndent(),
        )

        assertTrue(packageAudit.hasRejectedOverrides)
        assertEquals(listOf("network"), packageAudit.rejectedFeatureIds)
        assertEquals("packaged_apk_override_denied", packageAudit.rejected.first().reason)
    }

    @Test
    fun featureFlagAuditRejectsBuildOverridesForStableCapabilities() {
        val requested = linkedMapOf(
            "esm" to true,
            "dynamicImport" to true,
            "network" to true,
            "raw_node_network_modules" to true,
            "javaInterop" to true,
        )

        val debugAudit = NodeRuntimeCapabilities.auditBuildTimeFeatureFlags(
            requestedFlags = requested,
            releaseBuild = false,
        )

        assertTrue(debugAudit.hasRejectedOverrides)
        assertEquals(emptySet<String>(), debugAudit.accepted.keys.toSet())
        assertTrue(debugAudit.rejected.all { it.reason == "build_time_override_denied" })

        val releaseAudit = NodeRuntimeCapabilities.auditBuildTimeFeatureFlags(
            requestedFlags = requested,
            releaseBuild = true,
        )

        assertTrue(releaseAudit.hasRejectedOverrides)
        assertEquals(requested.keys.map(NodeRuntimeCapabilities::normalizeFeatureFlagId).toSet(), releaseAudit.rejectedFeatureIds.toSet())
        assertTrue(releaseAudit.rejected.all { it.reason == "build_time_override_denied" })
    }

    @Test
    fun nodeProjectRejectsProjectAndPackageFeatureFlagMetadata() {
        val projectFlagRoot = File(context.cacheDir, "node-feature-flag-project-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create project feature flag audit root: $absolutePath" }
        }
        File(projectFlagRoot, "project.json").writeText(
            """
            {
              "type": "node",
              "main": "index.js",
              "node": {
                "featureFlags": {
                  "network": true
                }
              }
            }
            """.trimIndent(),
        )
        File(projectFlagRoot, "index.js").writeText("""console.log("blocked");""")

        val projectError = runCatching {
            NativeNodeEmbeddedProjectRunner.resolveProjectLaunchPlan(projectFlagRoot)
        }.exceptionOrNull()
        assertNotNull(projectError)
        assertTrue(projectError!!.message.orEmpty(), projectError.message.orEmpty().contains("project_json_override_denied"))

        val packageFlagRoot = File(context.cacheDir, "node-feature-flag-package-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create package feature flag audit root: $absolutePath" }
        }
        File(packageFlagRoot, "project.json").writeText("""{"type":"node","main":"index.js"}""")
        File(packageFlagRoot, "package.json").writeText(
            """
            {
              "name": "feature-flag-package",
              "autojs6": {
                "node": {
                  "featureFlags": { "network": true }
                }
              }
            }
            """.trimIndent(),
        )
        File(packageFlagRoot, "index.js").writeText("""console.log("blocked");""")

        val packageError = runCatching {
            NativeNodeEmbeddedProjectRunner.resolveProjectLaunchPlan(packageFlagRoot)
        }.exceptionOrNull()
        assertNotNull(packageError)
        assertTrue(packageError!!.message.orEmpty(), packageError.message.orEmpty().contains("packaged_apk_override_denied"))
    }

    @Test
    fun autojsProfileReportsFeatureFlagRegistry() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node feature flag registry profile")

        val source = """
            "nodejs";
            const profile = require("autojs6:profile");
            const expected = {
              esm: true,
              dynamic_import: true,
              network: true,
              raw_node_network_modules: true,
              websocket: true,
              worker_threads: true,
              fs_streams: true,
              zlib_streams: true,
              vm: true,
              inspector: false,
              java_interop: true,
              native_addon: false,
              child_process: true
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
            if (!Array.isArray(profile.enabledFeatures)) {
              throw new Error("enabledFeatures should be an array");
            }
            if (profile.featureFlags.esm.status !== "stable" || profile.featureFlags.esm.enabled !== true) {
              throw new Error("esm stable default mismatch");
            }
            if (profile.featureFlags.dynamic_import.gradleProperty !== null) {
              throw new Error("dynamic_import gradle property mismatch");
            }
            if (profile.featureFlags.java_interop.gradleProperty !== null) {
              throw new Error("java_interop gradle property mismatch");
            }
            if (profile.featureFlags.child_process.gradleProperty !== null) {
              throw new Error("child_process gradle property mismatch");
            }
            if (profile.featureFlags.dynamic_import.status !== "stable") {
              throw new Error("dynamic_import status mismatch");
            }
            if (profile.featureFlags.java_interop.status !== "stable") {
              throw new Error("java_interop status mismatch");
            }
            if (profile.featureFlags.child_process.status !== "stable") {
              throw new Error("child_process status mismatch");
            }
            if (profile.featureFlags.dynamic_import.buildTimeOverrideAllowed !== false) {
              throw new Error("dynamic_import build override mismatch");
            }
            if (profile.featureFlags.java_interop.buildTimeOverrideAllowed !== false) {
              throw new Error("java_interop build override mismatch");
            }
            if (profile.featureFlags.child_process.buildTimeOverrideAllowed !== false) {
              throw new Error("child_process build override mismatch");
            }
            if (
              profile.featureFlags.esm.buildOnly !== false ||
              profile.featureFlags.esm.debugOnly !== false ||
              profile.featureFlags.esm.releaseForbidden !== false
            ) {
              throw new Error("esm classification mismatch");
            }
            if (
              profile.featureFlags.dynamic_import.buildOnly !== false ||
              profile.featureFlags.dynamic_import.debugOnly !== false ||
              profile.featureFlags.dynamic_import.releaseForbidden !== false
            ) {
              throw new Error("dynamic_import classification mismatch");
            }
            if (
              profile.featureFlags.network.buildOnly !== false ||
              profile.featureFlags.network.debugOnly !== false ||
              profile.featureFlags.network.releaseForbidden !== false
            ) {
              throw new Error("network classification mismatch");
            }
            if (
              profile.featureFlags.raw_node_network_modules.gradleProperty !== null ||
              profile.featureFlags.raw_node_network_modules.status !== "stable" ||
              profile.featureFlags.raw_node_network_modules.buildTimeOverrideAllowed !== false ||
              profile.featureFlags.raw_node_network_modules.buildOnly !== false ||
              profile.featureFlags.raw_node_network_modules.debugOnly !== false ||
              profile.featureFlags.raw_node_network_modules.releaseForbidden !== false
            ) {
              throw new Error("raw_node_network_modules classification mismatch");
            }
            if (
              profile.featureFlags.fs_streams.buildOnly !== false ||
              profile.featureFlags.fs_streams.debugOnly !== false ||
              profile.featureFlags.fs_streams.releaseForbidden !== false
            ) {
              throw new Error("fs_streams classification mismatch");
            }
            if (profile.featureFlags.java_interop.projectAllowed !== false || profile.featureFlags.java_interop.packagedAllowed !== false) {
              throw new Error("java_interop metadata override classification mismatch");
            }
            if (profile.featureStates.dynamicImport !== true) {
              throw new Error("dynamicImport stable snapshot mismatch");
            }
            if (profile.featureStates.esm !== true) {
              throw new Error("legacy esm stable snapshot mismatch");
            }
            if (profile.featureStates.javaInterop !== true) {
              throw new Error("javaInterop stable snapshot mismatch");
            }
            if (profile.featureStates.rawNodeNetworkModules !== true) {
              throw new Error("rawNodeNetworkModules stable snapshot mismatch");
            }
            for (const id of ["native_addon", "inspector"]) {
              if (!profile.deniedFeatureFlags.includes(id)) {
                throw new Error("deniedFeatureFlags missing " + id);
              }
            }
            for (const id of ["esm", "dynamic_import", "network", "raw_node_network_modules", "websocket", "worker_threads", "java_interop", "child_process"]) {
              if (profile.deniedFeatureFlags.includes(id)) {
                throw new Error(id + " should not be denied by default");
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
