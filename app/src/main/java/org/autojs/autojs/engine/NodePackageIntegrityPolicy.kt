package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject

object NodePackageIntegrityPolicy {

    const val SCHEMA = "autojs6-node-package-lock-integrity-policy-v1"
    const val STATUS = "lock_integrity_policy_partial"
    const val LOCKFILE = "autojs6-lock.json"
    const val ANDROID_RECORD_SCHEMA = "autojs6-node-android-package-record-v1"
    const val HASH_ALGORITHM = "sha512"
    const val HASH_FORMAT = "ssri_sha512_base64"
    const val SIGNATURE_SCHEME = "autojs6-package-signature-v1"
    private val RECOVERY_ACTIONS = listOf(
        "verify_package_integrity",
        "prune_stale_transactions",
        "reinstall_from_verified_source",
        "restore_previous_transaction_snapshot",
        "disable_unsigned_plugin_or_native_subject",
    )

    private val subjects = listOf(
        SubjectPolicy(
            id = "android_package_manager",
            status = "transactional_local_store",
            lockfile = "autojs6_lock_host_managed",
            signature = "host_managed_or_deferred",
            rollback = "locked_staging_restore_previous_on_failure",
            cacheCleanup = "prune_stale_transactions",
            corruptionRecovery = "verify_mismatch_reinstall_required",
            rawInstall = false,
            nativeExecution = false,
        ),
        SubjectPolicy(
            id = "plugin_packages",
            status = "local_checked_files_signed_archives_deferred",
            lockfile = LOCKFILE,
            signature = "required_before_promotion",
            rollback = "install_update_remove_must_be_transactional",
            cacheCleanup = "archive_cache_prune_required_before_marketplace",
            corruptionRecovery = "signature_or_hash_mismatch_blocks_load",
            rawInstall = false,
            nativeExecution = false,
        ),
        SubjectPolicy(
            id = "native_addons",
            status = "denied_until_signed_packaged_artifacts",
            lockfile = LOCKFILE,
            signature = "required_before_promotion",
            rollback = "package_disable_and_previous_artifact_restore_required",
            cacheCleanup = "abi_cache_prune_required_before_enablement",
            corruptionRecovery = "hash_or_signature_mismatch_blocks_load",
            rawInstall = false,
            nativeExecution = false,
        ),
    )

    fun subjectIds(): List<String> = subjects.map { it.id }

    fun recoveryActions(): List<String> = RECOVERY_ACTIONS

    fun toJson(): JSONObject = JSONObject()
        .put("schema", SCHEMA)
        .put("status", STATUS)
        .put("defaultEnabled", true)
        .put("targetProfiles", JSONArray(listOf("pro_compat_opt_in", "desktop_compat_opt_in")))
        .put("lockfile", LOCKFILE)
        .put("androidRecordSchema", ANDROID_RECORD_SCHEMA)
        .put("hashAlgorithm", HASH_ALGORITHM)
        .put("hashFormat", HASH_FORMAT)
        .put("signatureScheme", SIGNATURE_SCHEME)
        .put("signaturePolicy", "required_before_plugin_native_promotion")
        .put("rollback", rollbackPolicy())
        .put("cacheCleanup", cacheCleanupPolicy())
        .put("corruptionRecovery", corruptionRecoveryPolicy())
        .put("recoveryActions", JSONArray(RECOVERY_ACTIONS))
        .put("subjects", JSONArray(subjects.map { it.toJson() }))
        .put("unsafePackageExecution", false)
        .put("rawRegistryInstall", false)
        .put("unsignedPluginInstall", false)
        .put("unsignedNativeAddonLoad", false)
        .put("lifecycleScripts", false)
        .put("postinstallDownloads", false)
        .put("unverifiedCacheReuse", false)

    private fun rollbackPolicy(): JSONObject = JSONObject()
        .put("transactionalStaging", true)
        .put("restorePreviousOnFailure", true)
        .put("cleanupStagingOnFailure", true)
        .put("packageDisableRequiredForFailedNativePromotion", true)

    private fun cacheCleanupPolicy(): JSONObject = JSONObject()
        .put("staleTransactionsPruned", true)
        .put("unverifiedCacheReuse", false)
        .put("pluginArchiveCache", "deferred_until_signed_archive_store")
        .put("nativeAbiCache", "deferred_until_signed_packaged_artifacts")

    private fun corruptionRecoveryPolicy(): JSONObject = JSONObject()
        .put("verifyBeforeReuse", true)
        .put("mismatchBlocksLoad", true)
        .put("reinstallRequired", true)
        .put("rawCacheRepair", false)
        .put("actions", JSONArray(RECOVERY_ACTIONS))

    data class SubjectPolicy(
        val id: String,
        val status: String,
        val lockfile: String,
        val signature: String,
        val rollback: String,
        val cacheCleanup: String,
        val corruptionRecovery: String,
        val rawInstall: Boolean,
        val nativeExecution: Boolean,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("status", status)
            .put("lockfile", lockfile)
            .put("hashAlgorithm", HASH_ALGORITHM)
            .put("signature", signature)
            .put("rollback", rollback)
            .put("cacheCleanup", cacheCleanup)
            .put("corruptionRecovery", corruptionRecovery)
            .put("rawInstall", rawInstall)
            .put("nativeExecution", nativeExecution)
    }
}
