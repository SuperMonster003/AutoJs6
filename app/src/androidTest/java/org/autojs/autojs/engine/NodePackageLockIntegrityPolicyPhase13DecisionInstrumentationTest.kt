package org.autojs.autojs.engine

import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

class NodePackageLockIntegrityPolicyPhase13DecisionInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext
    private lateinit var root: File
    private lateinit var store: File
    private lateinit var sources: File
    private lateinit var manager: NodeAndroidPackageInstallManager

    @Before
    fun setUp() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        root = File(context.cacheDir, "node-package-integrity-p13-48-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create P13-48 package integrity root: $absolutePath" }
        }
        store = File(root, "store")
        sources = File(root, "sources").apply { mkdirs() }
        manager = NodeAndroidPackageInstallManager(store) { 1_700_000_048_000L }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun policyDescriptorCoversLockHashSignatureRollbackCacheAndRecovery() {
        val policy = NodePackageIntegrityPolicy.toJson()
        assertEquals(NodePackageIntegrityPolicy.SCHEMA, policy.getString("schema"))
        assertEquals(NodePackageIntegrityPolicy.STATUS, policy.getString("status"))
        assertEquals(NodePackageIntegrityPolicy.LOCKFILE, policy.getString("lockfile"))
        assertEquals(NodePackageIntegrityPolicy.HASH_ALGORITHM, policy.getString("hashAlgorithm"))
        assertEquals(NodePackageIntegrityPolicy.SIGNATURE_SCHEME, policy.getString("signatureScheme"))
        assertEquals("required_before_plugin_native_promotion", policy.getString("signaturePolicy"))
        assertTrue(policy.getJSONObject("rollback").getBoolean("restorePreviousOnFailure"))
        assertTrue(policy.getJSONObject("rollback").getBoolean("cleanupStagingOnFailure"))
        assertTrue(policy.getJSONObject("cacheCleanup").getBoolean("staleTransactionsPruned"))
        assertTrue(policy.getJSONObject("corruptionRecovery").getBoolean("mismatchBlocksLoad"))
        assertTrue(policy.getJSONObject("corruptionRecovery").getBoolean("reinstallRequired"))
        assertEquals(
            listOf("android_package_manager", "plugin_packages", "native_addons"),
            policy.getJSONArray("subjects").toSubjectIds(),
        )
        assertFalse(policy.getBoolean("rawRegistryInstall"))
        assertFalse(policy.getBoolean("unsignedPluginInstall"))
        assertFalse(policy.getBoolean("unsignedNativeAddonLoad"))
        assertFalse(policy.getBoolean("unverifiedCacheReuse"))

        val nativeValues = manager.nativeValues()
        assertEquals(NodePackageIntegrityPolicy.SCHEMA, nativeValues["package_integrity_policy.schema"])
        assertEquals(NodePackageIntegrityPolicy.STATUS, nativeValues["package_integrity_policy.status"])
        assertEquals(NodePackageIntegrityPolicy.HASH_ALGORITHM, nativeValues["package_integrity_policy.hash"])
        assertEquals("required_before_plugin_native_promotion", nativeValues["package_integrity_policy.signature"])
        assertEquals("staging_restore_previous_on_failure", nativeValues["package_integrity_policy.rollback"])
        assertEquals("staging_prune_and_reinstall_required", nativeValues["package_integrity_policy.cache_cleanup"])
        assertEquals("verify_mismatch_reinstall_required", nativeValues["package_integrity_policy.corruption_recovery"])
        assertEquals(
            "android_package_manager,plugin_packages,native_addons",
            nativeValues["package_integrity_policy.subjects"],
        )
        assertEquals("disabled", nativeValues["package_integrity_policy.raw_registry_install"])
        assertEquals("disabled", nativeValues["package_integrity_policy.unsigned_plugin_install"])
        assertEquals("disabled", nativeValues["package_integrity_policy.unsigned_native_addon_load"])
    }

    @Test
    fun androidStoreRollbackPruneAndCorruptionRecoveryMatchPolicy() {
        val sourceV1 = packageSource(
            dirName = "pkg-v1",
            name = "@autojs6/p13-integrity",
            version = "1.0.0",
            files = mapOf("index.cjs" to "module.exports = 'v1';\n"),
        )
        val integrityV1 = manager.calculateSourceIntegrity(sourceV1)
        val installed = manager.install(request("@autojs6/p13-integrity", "1.0.0", sourceV1, integrityV1))
        assertEquals(NodeAndroidPackageInstallManager.STATUS_INSTALLED, installed.status)

        val sourceV2 = packageSource(
            dirName = "pkg-v2",
            name = "@autojs6/p13-integrity",
            version = "1.0.0",
            files = mapOf("index.cjs" to "module.exports = 'v2';\n"),
        )
        expectPackageError(NodeAndroidPackageInstallManager.ERROR_INTEGRITY_MISMATCH) {
            manager.update(request("@autojs6/p13-integrity", "1.0.0", sourceV2, integrityV1))
        }
        assertEquals("module.exports = 'v1';\n", File(manager.list().single().packageDir, "index.cjs").readText())

        File(store, ".transactions/stale-p13-48").apply {
            check(mkdirs()) { "Failed to create stale transaction dir: $absolutePath" }
        }
        val pruned = manager.prune()
        assertEquals(NodeAndroidPackageInstallManager.STATUS_PRUNED, pruned.status)
        assertFalse(File(store, ".transactions/stale-p13-48").exists())

        File(manager.list().single().packageDir, "index.cjs").appendText("// corrupt\n")
        expectPackageError(NodeAndroidPackageInstallManager.ERROR_INTEGRITY_MISMATCH) {
            manager.verify("@autojs6/p13-integrity", "1.0.0")
        }
        val integrityV2 = manager.calculateSourceIntegrity(sourceV2)
        val recovered = manager.update(request("@autojs6/p13-integrity", "1.0.0", sourceV2, integrityV2))
        assertEquals(NodeAndroidPackageInstallManager.STATUS_UPDATED, recovered.status)
        assertEquals(
            NodeAndroidPackageInstallManager.STATUS_VERIFIED,
            manager.verify("@autojs6/p13-integrity", "1.0.0").status,
        )
        assertEquals("module.exports = 'v2';\n", File(manager.list().single().packageDir, "index.cjs").readText())
    }

    @Test
    fun packageIntegrityProfileRecordsUnifiedPolicyAndFailClosedBoundaries() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-48 package lock integrity policy")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                const profile = require("autojs6:profile");
                const packageIntegrityProfile = profile.packageIntegrityProfile;
                console.log("p13integrity.profile=" + [
                  packageIntegrityProfile.status,
                  packageIntegrityProfile.defaultEnabled,
                  packageIntegrityProfile.targetProfiles.join(","),
                  packageIntegrityProfile.policySchema,
                  packageIntegrityProfile.lockfile,
                  packageIntegrityProfile.androidRecordSchema,
                  packageIntegrityProfile.hashAlgorithm,
                  packageIntegrityProfile.hashFormat,
                  packageIntegrityProfile.androidPackageManager,
                  packageIntegrityProfile.pluginPackages,
                  packageIntegrityProfile.nativeAddons,
                  packageIntegrityProfile.signatureScheme,
                  packageIntegrityProfile.signaturePolicy,
                  packageIntegrityProfile.rollback,
                  packageIntegrityProfile.cacheCleanup,
                  packageIntegrityProfile.corruptionRecovery,
                  packageIntegrityProfile.lockfileAuthority,
                  packageIntegrityProfile.subjects.join(","),
                  packageIntegrityProfile.unsafePackageExecution,
                  packageIntegrityProfile.rawRegistryInstall,
                  packageIntegrityProfile.unsignedPluginInstall,
                  packageIntegrityProfile.unsignedNativeAddonLoad,
                  packageIntegrityProfile.lifecycleScripts,
                  packageIntegrityProfile.postinstallDownloads,
                  packageIntegrityProfile.unverifiedCacheReuse,
                  Object.isFrozen(packageIntegrityProfile),
                  Object.isFrozen(packageIntegrityProfile.targetProfiles),
                  Object.isFrozen(packageIntegrityProfile.subjects)
                ].join("|"));
            """.trimIndent(),
            sourceName = "<node-package-lock-integrity-phase13-p13-48.js>",
            workingDirectory = root,
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("P13-48 package lock integrity policy", result)
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "p13integrity.profile=lock_integrity_policy_partial|true|pro_compat_opt_in,desktop_compat_opt_in|" +
                    "autojs6-node-package-lock-integrity-policy-v1|autojs6-lock.json|" +
                    "autojs6-node-android-package-record-v1|sha512|ssri_sha512_base64|" +
                    "transactional_local_store|local_checked_files_signed_archives_deferred|" +
                    "denied_until_signed_packaged_artifacts|autojs6-package-signature-v1|" +
                    "required_before_plugin_native_promotion|staging_restore_previous_on_failure|" +
                    "staging_prune_and_reinstall_required|verify_mismatch_reinstall_required|" +
                    "host_managed_required|android_package_manager,plugin_packages,native_addons|" +
                    "false|false|false|false|false|false|false|true|true|true",
            ),
        )
    }

    private fun request(
        name: String,
        version: String,
        sourceDir: File,
        integrity: String,
    ): NodeAndroidPackageInstallManager.PackageRequest {
        return NodeAndroidPackageInstallManager.PackageRequest(
            name = name,
            version = version,
            sourceDir = sourceDir,
            expectedIntegrity = integrity,
        )
    }

    private fun packageSource(
        dirName: String,
        name: String,
        version: String,
        files: Map<String, String>,
    ): File {
        val dir = File(sources, dirName).apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create package source: $absolutePath" }
        }
        File(dir, "package.json").writeText("""{"name":"$name","version":"$version"}""")
        files.forEach { (relative, text) ->
            File(dir, relative).apply {
                parentFile?.mkdirs()
                writeText(text)
            }
        }
        return dir
    }

    private fun expectPackageError(
        code: String,
        block: () -> Unit,
    ): NodeAndroidPackageInstallManager.PackageInstallException {
        try {
            block()
            fail("Expected package install error: $code")
        } catch (error: NodeAndroidPackageInstallManager.PackageInstallException) {
            assertEquals(code, error.code)
            return error
        }
        throw AssertionError("unreachable")
    }

    private fun JSONArray.toSubjectIds(): List<String> {
        return (0 until length()).map { index -> getJSONObject(index).getString("id") }
    }
}
