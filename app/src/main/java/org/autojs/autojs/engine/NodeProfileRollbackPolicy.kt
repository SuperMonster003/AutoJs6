package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

object NodeProfileRollbackPolicy {

    const val SCHEMA = "autojs6-node-profile-rollback-policy-v1"
    const val STATUS = "playbook_partial"

    const val SAFE_DEFAULT = NodeProfileSelectionDiagnostics.SAFE_DEFAULT
    const val PRO_COMPAT_OPT_IN = NodeProfileSelectionDiagnostics.PRO_COMPAT_OPT_IN
    const val DESKTOP_COMPAT_OPT_IN = NodeProfileSelectionDiagnostics.DESKTOP_COMPAT_OPT_IN
    const val DEBUG_UNSAFE_LAB = NodeProfileSelectionDiagnostics.DEBUG_UNSAFE_LAB

    const val PRO_PROFILE_PROPERTY = "autojs.nodejs.profile.pro.enabled"
    const val DESKTOP_PROFILE_PROPERTY = "autojs.nodejs.profile.desktop.enabled"
    const val DEBUG_UNSAFE_LAB_PROPERTY = "autojs.nodejs.profile.debugUnsafeLab.enabled"
    const val RELAXED_CAPABILITIES_PROPERTY = "autojs.nodejs.relaxedCapabilities.enabled"

    fun profileKillSwitchProperties(): List<String> = listOf(
        PRO_PROFILE_PROPERTY,
        DESKTOP_PROFILE_PROPERTY,
        DEBUG_UNSAFE_LAB_PROPERTY,
    )

    fun capabilityKillSwitchProperties(): List<String> = listOf(
        NodeRuntimeCapabilities.NETWORK_EXPERIMENTAL_PROPERTY,
        NodeRuntimeCapabilities.WORKER_THREADS_EXPERIMENTAL_PROPERTY,
        NodeRuntimeCapabilities.JAVA_INTEROP_EXPERIMENTAL_PROPERTY,
        RELAXED_CAPABILITIES_PROPERTY,
    )

    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("schema", SCHEMA)
        put("status", STATUS)
        put("defaultProfile", SAFE_DEFAULT)
        put("effectiveProfile", SAFE_DEFAULT)
        put("rollbackTargetProfile", SAFE_DEFAULT)
        put("crashFallbackProfile", SAFE_DEFAULT)
        put("stressFallbackProfile", SAFE_DEFAULT)
        put("profilePromotion", false)
        put("userSelectable", false)
        put("metadataOnly", true)
        put("grantsAuthority", false)
        put("safeDefaultAlwaysAvailable", true)
        put("independentProfileDisable", true)
        put("profileKillSwitches", profileKillSwitchesJson())
        put("capabilityKillSwitches", capabilityKillSwitchesJson())
        put("profileStates", JSONArray(profileStates().map { it.toJson() }))
        put("capabilityStates", JSONArray(capabilityStates().map { it.toJson() }))
        put("rollbackTriggers", JSONArray(rollbackTriggers().map { it.toJson() }))
        put("packagedDowngrade", packagedDowngradeJson())
        put("releasePolicy", releasePolicyJson())
        put(
            "pending",
            JSONArray(
                listOf(
                    "runtime profile promotion",
                    "settings UI profile switch",
                    "packaged builder profile switch",
                    "automatic crash circuit breaker",
                    "remote packaged kill switch",
                    "privacy disclosure prompts",
                ),
            ),
        )
    }

    fun toJson(): String = toJsonObject().toString()

    private fun profileKillSwitchesJson(): JSONObject = JSONObject()
        .put(PRO_COMPAT_OPT_IN, PRO_PROFILE_PROPERTY)
        .put(DESKTOP_COMPAT_OPT_IN, DESKTOP_PROFILE_PROPERTY)
        .put(DEBUG_UNSAFE_LAB, DEBUG_UNSAFE_LAB_PROPERTY)

    private fun capabilityKillSwitchesJson(): JSONObject = JSONObject()
        .put("network", NodeRuntimeCapabilities.NETWORK_EXPERIMENTAL_PROPERTY)
        .put("worker_threads", NodeRuntimeCapabilities.WORKER_THREADS_EXPERIMENTAL_PROPERTY)
        .put("java_interop", NodeRuntimeCapabilities.JAVA_INTEROP_EXPERIMENTAL_PROPERTY)
        .put("all_relaxed_capabilities", RELAXED_CAPABILITIES_PROPERTY)
        .put("raw_node_network_modules", "stable_denial_no_runtime_switch")
        .put("filesystem_relaxation", "safe_profile_scoped_fs")
        .put("child_process", "stable_denial_no_runtime_switch")
        .put("native_addon", "stable_denial_no_runtime_switch")
        .put("inspector", "debug_profile_only_no_runtime_switch")
        .put("wasi", "stable_denial_no_runtime_switch")

    private fun profileStates(): List<ProfileState> = listOf(
        ProfileState(
            id = SAFE_DEFAULT,
            property = "always_on",
            enabled = true,
            rollbackAction = "keep_default_safe_profile",
            fallbackProfile = SAFE_DEFAULT,
            packagedAction = "no_downgrade_required",
        ),
        ProfileState(
            id = PRO_COMPAT_OPT_IN,
            property = PRO_PROFILE_PROPERTY,
            enabled = BuildConfig.NODEJS_PROFILE_PRO_ENABLED,
            rollbackAction = "disable_pro_profile_and_rebuild",
            fallbackProfile = SAFE_DEFAULT,
            packagedAction = "publish_safe_profile_patch_or_remove_pro_metadata",
        ),
        ProfileState(
            id = DESKTOP_COMPAT_OPT_IN,
            property = DESKTOP_PROFILE_PROPERTY,
            enabled = BuildConfig.NODEJS_PROFILE_DESKTOP_ENABLED,
            rollbackAction = "disable_desktop_profile_and_rebuild",
            fallbackProfile = SAFE_DEFAULT,
            packagedAction = "publish_safe_profile_patch_or_remove_desktop_metadata",
        ),
        ProfileState(
            id = DEBUG_UNSAFE_LAB,
            property = DEBUG_UNSAFE_LAB_PROPERTY,
            enabled = BuildConfig.NODEJS_PROFILE_DEBUG_UNSAFE_LAB_ENABLED && BuildConfig.DEBUG,
            rollbackAction = "disable_debug_unsafe_lab_and_rebuild",
            fallbackProfile = SAFE_DEFAULT,
            packagedAction = "release_build_forbids_debug_unsafe_lab",
        ),
    )

    private fun capabilityStates(): List<CapabilityState> = listOf(
        CapabilityState(
            id = "network",
            profile = DESKTOP_COMPAT_OPT_IN,
            property = NodeRuntimeCapabilities.NETWORK_EXPERIMENTAL_PROPERTY,
            enabled = BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED,
            rollbackAction = "set_autojs_nodejs_network_experimental_false",
        ),
        CapabilityState(
            id = "worker_threads",
            profile = DESKTOP_COMPAT_OPT_IN,
            property = NodeRuntimeCapabilities.WORKER_THREADS_EXPERIMENTAL_PROPERTY,
            enabled = BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED,
            rollbackAction = "set_autojs_nodejs_worker_threads_experimental_false",
        ),
        CapabilityState(
            id = "java_interop",
            profile = PRO_COMPAT_OPT_IN,
            property = NodeRuntimeCapabilities.JAVA_INTEROP_EXPERIMENTAL_PROPERTY,
            enabled = BuildConfig.NODEJS_JAVA_INTEROP_EXPERIMENTAL_ENABLED,
            rollbackAction = "set_autojs_nodejs_javaInterop_experimental_false",
        ),
        CapabilityState(
            id = "all_relaxed_capabilities",
            profile = "pro_or_desktop",
            property = RELAXED_CAPABILITIES_PROPERTY,
            enabled = BuildConfig.NODEJS_RELAXED_CAPABILITIES_ENABLED,
            rollbackAction = "set_autojs_nodejs_relaxedCapabilities_false",
        ),
        CapabilityState(
            id = "filesystem_relaxation",
            profile = DESKTOP_COMPAT_OPT_IN,
            property = "safe_profile_scoped_fs",
            enabled = false,
            rollbackAction = "keep_scoped_working_directory_only",
        ),
        CapabilityState(
            id = "child_process",
            profile = DESKTOP_COMPAT_OPT_IN,
            property = "stable_denial_no_runtime_switch",
            enabled = false,
            rollbackAction = "keep_child_process_builtin_denied",
        ),
        CapabilityState(
            id = "native_addon",
            profile = DESKTOP_COMPAT_OPT_IN,
            property = "stable_denial_no_runtime_switch",
            enabled = false,
            rollbackAction = "keep_node_extension_and_dlopen_denied",
        ),
        CapabilityState(
            id = "inspector",
            profile = DEBUG_UNSAFE_LAB,
            property = "debug_profile_only_no_runtime_switch",
            enabled = false,
            rollbackAction = "keep_inspector_builtin_denied",
        ),
        CapabilityState(
            id = "wasi",
            profile = DESKTOP_COMPAT_OPT_IN,
            property = "stable_denial_no_runtime_switch",
            enabled = false,
            rollbackAction = "keep_raw_wasi_denied",
        ),
    )

    private fun rollbackTriggers(): List<RollbackTrigger> = listOf(
        RollbackTrigger(
            id = "native_crash",
            evidence = "NodeCrashEvidenceStore",
            fallbackProfile = SAFE_DEFAULT,
            action = "disable_requested_profile_and_relaunch_safe_default",
        ),
        RollbackTrigger(
            id = "bridge_provider_crash",
            evidence = "NodeCrashEvidenceStore",
            fallbackProfile = SAFE_DEFAULT,
            action = "disable_provider_capability_then_relaunch_safe_default",
        ),
        RollbackTrigger(
            id = "stress_regression",
            evidence = "verifyNodePhase13FullHeavyResourceStress",
            fallbackProfile = SAFE_DEFAULT,
            action = "disable_profile_or_capability_until_stress_gate_recovers",
        ),
        RollbackTrigger(
            id = "security_corpus_regression",
            evidence = "RELAXED_CAPABILITY_SECURITY_CORPUS.json",
            fallbackProfile = SAFE_DEFAULT,
            action = "fail_closed_and_keep_relaxed_capability_unpromoted",
        ),
        RollbackTrigger(
            id = "packaged_apk_regression",
            evidence = "packaged capability metadata and crash bundle",
            fallbackProfile = SAFE_DEFAULT,
            action = "ship_safe_profile_patch_or_remove_promoted_metadata",
        ),
    )

    private fun packagedDowngradeJson(): JSONObject = JSONObject()
        .put("strategy", "publish_safe_profile_patch_or_remove_promoted_metadata")
        .put("authority", "metadata_only_no_authority")
        .put("publishSafeProfilePatch", true)
        .put("remoteKillSwitchAvailable", false)
        .put("requiresUserDisclosure", true)
        .put(
            "actions",
            JSONArray(
                listOf(
                    "remove pro_compat_opt_in or desktop_compat_opt_in metadata",
                    "remove relaxed node.permissions",
                    "rebuild packaged APK with Safe profile defaults",
                    "keep stable denial errors for undeclared capabilities",
                    "record crash/stress evidence before re-enabling",
                ),
            ),
        )

    private fun releasePolicyJson(): JSONObject = JSONObject()
        .put("releaseBuildForbidsDebugUnsafeLab", true)
        .put("releaseBuildForbidsExperimentalProfiles", true)
        .put("safeProfileReleaseFallback", true)
        .put("rollbackPropertiesDefaultFalse", true)

    private data class ProfileState(
        val id: String,
        val property: String,
        val enabled: Boolean,
        val rollbackAction: String,
        val fallbackProfile: String,
        val packagedAction: String,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("property", property)
            .put("enabled", enabled)
            .put("rollbackAction", rollbackAction)
            .put("fallbackProfile", fallbackProfile)
            .put("packagedAction", packagedAction)
    }

    private data class CapabilityState(
        val id: String,
        val profile: String,
        val property: String,
        val enabled: Boolean,
        val rollbackAction: String,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("profile", profile)
            .put("property", property)
            .put("enabled", enabled)
            .put("rollbackAction", rollbackAction)
            .put("fallbackProfile", SAFE_DEFAULT)
    }

    private data class RollbackTrigger(
        val id: String,
        val evidence: String,
        val fallbackProfile: String,
        val action: String,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("evidence", evidence)
            .put("fallbackProfile", fallbackProfile)
            .put("action", action)
    }
}
