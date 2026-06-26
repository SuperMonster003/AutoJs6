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
    fun featureFlagRegistryDefaultsStaySafe() {
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

        assertTrue(BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED)
        assertTrue(BuildConfig.NODEJS_DYNAMIC_IMPORT_LOCAL_ENABLED)
        assertTrue(BuildConfig.NODEJS_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED)
        assertFalse(BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED)
        assertFalse(BuildConfig.NODEJS_RAW_NETWORK_MODULES_EXPERIMENTAL_ENABLED)
        assertFalse(BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED)
        assertFalse(BuildConfig.NODEJS_CHILD_PROCESS_EXPERIMENTAL_ENABLED)
        assertFalse(BuildConfig.NODEJS_JAVA_INTEROP_EXPERIMENTAL_ENABLED)
        assertEquals(null, NodeRuntimeCapabilities.experimentalFlags[NodeRuntimeCapabilities.ESM_EXPERIMENTAL_PROPERTY])
        assertEquals(null, NodeRuntimeCapabilities.experimentalFlags[NodeRuntimeCapabilities.DYNAMIC_IMPORT_LOCAL_PROPERTY])
        assertEquals(null, NodeRuntimeCapabilities.experimentalFlags[NodeRuntimeCapabilities.DYNAMIC_IMPORT_EXPERIMENTAL_PROPERTY])
        assertEquals(false, NodeRuntimeCapabilities.experimentalFlags[NodeRuntimeCapabilities.NETWORK_EXPERIMENTAL_PROPERTY])
        assertEquals(false, NodeRuntimeCapabilities.experimentalFlags[NodeRuntimeCapabilities.RAW_NETWORK_MODULES_EXPERIMENTAL_PROPERTY])
        assertEquals(false, NodeRuntimeCapabilities.experimentalFlags[NodeRuntimeCapabilities.WORKER_THREADS_EXPERIMENTAL_PROPERTY])
        assertEquals(false, NodeRuntimeCapabilities.experimentalFlags[NodeRuntimeCapabilities.CHILD_PROCESS_EXPERIMENTAL_PROPERTY])
        assertEquals(false, NodeRuntimeCapabilities.experimentalFlags[NodeRuntimeCapabilities.JAVA_INTEROP_EXPERIMENTAL_PROPERTY])
        assertTrue(NodeRuntimeCapabilities.featureFlagsById.getValue("esm").defaultEnabled)
        assertEquals("partial", NodeRuntimeCapabilities.featureFlagsById.getValue("esm").status)
        assertTrue(NodeRuntimeCapabilities.featureFlagsById.getValue("dynamic_import").defaultEnabled)
        assertEquals("partial", NodeRuntimeCapabilities.featureFlagsById.getValue("dynamic_import").status)

        flags
            .filter { it.securityLevel == "high" || it.securityLevel == "critical" }
            .filterNot { it.id == "vm" }
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
        assertEquals(
            setOf("esm", "dynamic_import", "network", "raw_node_network_modules", "worker_threads", "child_process", "java_interop"),
            buildOverrideAllowed,
        )
        assertEquals(buildOverrideAllowed, NodeRuntimeCapabilities.buildOnlyFeatureFlags.map { it.id }.toSet())
        assertEquals(buildOverrideAllowed - setOf("esm", "dynamic_import"), NodeRuntimeCapabilities.debugOnlyFeatureFlags.map { it.id }.toSet())
        assertEquals(emptySet<String>(), NodeRuntimeCapabilities.projectAllowedFeatureFlags.map { it.id }.toSet())
        assertEquals(emptySet<String>(), NodeRuntimeCapabilities.packagedAllowedFeatureFlags.map { it.id }.toSet())
        assertFalse(NodeRuntimeCapabilities.releaseForbiddenFeatureFlags.any { it.id == "esm" })
        assertFalse(NodeRuntimeCapabilities.releaseForbiddenFeatureFlags.any { it.id == "dynamic_import" })
        assertTrue(NodeRuntimeCapabilities.releaseForbiddenFeatureFlags.any { it.id == "network" })
        assertTrue(NodeRuntimeCapabilities.releaseForbiddenFeatureFlags.any { it.id == "raw_node_network_modules" })
        assertTrue(NodeRuntimeCapabilities.releaseForbiddenFeatureFlags.any { it.id == "vm" })
        assertTrue(NodeRuntimeCapabilities.releaseForbiddenFeatureFlags.any { it.id == "java_interop" })
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
                  "unknownFutureFlag": true
                },
                "experimentalFlags": ["javaInterop"]
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
                  "featureFlags": {
                    "autojs.nodejs.network.experimental": true
                  }
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
    fun featureFlagAuditAllowsDebugBuildOverridesButRejectsReleaseOverrides() {
        val requested = linkedMapOf(
            "esm" to true,
            "dynamicImport" to true,
            "autojs.nodejs.network.experimental" to true,
            "autojs.nodejs.rawNetworkModules.experimental" to true,
            "javaInterop" to true,
        )

        val debugAudit = NodeRuntimeCapabilities.auditBuildTimeFeatureFlags(
            requestedFlags = requested,
            releaseBuild = false,
        )

        assertFalse(debugAudit.toErrorMessage(), debugAudit.hasRejectedOverrides)
        assertEquals(
            setOf("esm", "dynamic_import", "network", "raw_node_network_modules", "java_interop"),
            debugAudit.accepted.keys.toSet(),
        )

        val releaseAudit = NodeRuntimeCapabilities.auditBuildTimeFeatureFlags(
            requestedFlags = requested,
            releaseBuild = true,
        )

        assertTrue(releaseAudit.hasRejectedOverrides)
        assertEquals(setOf("network", "raw_node_network_modules", "java_interop"), releaseAudit.rejectedFeatureIds.toSet())
        assertTrue(releaseAudit.rejected.all { it.reason == "release_forbidden" })
        assertTrue(releaseAudit.accepted.keys.contains("dynamic_import"))
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
                  "experimentalFlags": ["network"]
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
              network: false,
              raw_node_network_modules: false,
              websocket: false,
              worker_threads: false,
              fs_streams: true,
              zlib_streams: true,
              vm: true,
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
            if (!Array.isArray(profile.enabledExperimentalFlags)) {
              throw new Error("enabledExperimentalFlags should be an array");
            }
            if (profile.featureFlags.esm.status !== "partial" || profile.featureFlags.esm.enabled !== true) {
              throw new Error("esm partial default mismatch");
            }
            if (profile.featureFlags.dynamic_import.gradleProperty !== "autojs.nodejs.dynamicImport.local") {
              throw new Error("dynamic_import gradle property mismatch");
            }
            if (profile.featureFlags.java_interop.gradleProperty !== "autojs.nodejs.javaInterop.experimental") {
              throw new Error("java_interop gradle property mismatch");
            }
            if (profile.featureFlags.child_process.gradleProperty !== "autojs.nodejs.child_process.experimental") {
              throw new Error("child_process gradle property mismatch");
            }
            if (profile.featureFlags.dynamic_import.status !== "partial") {
              throw new Error("dynamic_import status mismatch");
            }
            if (profile.featureFlags.java_interop.status !== "experimental") {
              throw new Error("java_interop status mismatch");
            }
            if (profile.featureFlags.child_process.status !== "experimental") {
              throw new Error("child_process status mismatch");
            }
            if (profile.featureFlags.dynamic_import.buildTimeOverrideAllowed !== true) {
              throw new Error("dynamic_import build override mismatch");
            }
            if (profile.featureFlags.java_interop.buildTimeOverrideAllowed !== true) {
              throw new Error("java_interop build override mismatch");
            }
            if (profile.featureFlags.child_process.buildTimeOverrideAllowed !== true) {
              throw new Error("child_process build override mismatch");
            }
            if (
              profile.featureFlags.esm.buildOnly !== true ||
              profile.featureFlags.esm.debugOnly !== false ||
              profile.featureFlags.esm.releaseForbidden !== false
            ) {
              throw new Error("esm classification mismatch");
            }
            if (
              profile.featureFlags.dynamic_import.buildOnly !== true ||
              profile.featureFlags.dynamic_import.debugOnly !== false ||
              profile.featureFlags.dynamic_import.releaseForbidden !== false
            ) {
              throw new Error("dynamic_import classification mismatch");
            }
            if (
              profile.featureFlags.network.buildOnly !== true ||
              profile.featureFlags.network.debugOnly !== true ||
              profile.featureFlags.network.releaseForbidden !== true
            ) {
              throw new Error("network classification mismatch");
            }
            if (
              profile.featureFlags.raw_node_network_modules.gradleProperty !== "autojs.nodejs.rawNetworkModules.experimental" ||
              profile.featureFlags.raw_node_network_modules.status !== "experimental" ||
              profile.featureFlags.raw_node_network_modules.buildTimeOverrideAllowed !== true ||
              profile.featureFlags.raw_node_network_modules.buildOnly !== true ||
              profile.featureFlags.raw_node_network_modules.debugOnly !== true ||
              profile.featureFlags.raw_node_network_modules.releaseForbidden !== true
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
            if (profile.experimentalFlags.dynamicImport !== true) {
              throw new Error("dynamicImport experimental snapshot mismatch");
            }
            if (profile.experimentalFlags.esm !== true) {
              throw new Error("legacy esm experimental snapshot mismatch");
            }
            if (profile.experimentalFlags.javaInterop !== false) {
              throw new Error("javaInterop experimental snapshot mismatch");
            }
            if (profile.experimentalFlags.rawNodeNetworkModules !== false) {
              throw new Error("rawNodeNetworkModules experimental snapshot mismatch");
            }
            for (const id of ["network", "raw_node_network_modules", "worker_threads", "child_process", "native_addon", "java_interop"]) {
              if (!profile.deniedFeatureFlags.includes(id)) {
                throw new Error("deniedFeatureFlags missing " + id);
              }
            }
            if (profile.deniedFeatureFlags.includes("dynamic_import")) {
              throw new Error("dynamic_import should not be denied by default");
            }
            console.log("feature.flags.ready=true");
        """.trimIndent()

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = source,
            sourceName = "<node-feature-flag-registry.js>",
            timeoutMs = 10_000L,
            childProcessExperimentalEnabled = false,
        )

        NodeEmbeddedTestHarness.assertSucceeded("Node feature flag registry profile", result)
        assertTrue(result.stdout, result.stdout.contains("feature.flags.ready=true"))
    }
}
