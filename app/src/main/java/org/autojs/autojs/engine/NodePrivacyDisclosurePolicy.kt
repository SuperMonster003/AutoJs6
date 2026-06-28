package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject

object NodePrivacyDisclosurePolicy {

    const val SCHEMA = "autojs6-node-privacy-disclosure-policy-v1"
    const val STATUS = "policy_partial"

    const val SAFE_DEFAULT = NodeProfileSelectionDiagnostics.SAFE_DEFAULT
    const val PRO_COMPAT_OPT_IN = NodeProfileSelectionDiagnostics.PRO_COMPAT_OPT_IN
    const val DESKTOP_COMPAT_OPT_IN = NodeProfileSelectionDiagnostics.DESKTOP_COMPAT_OPT_IN
    const val DEBUG_UNSAFE_LAB = NodeProfileSelectionDiagnostics.DEBUG_UNSAFE_LAB

    const val PACKAGED_DISCLOSURE_SCHEMA = "autojs6-node-packaged-disclosure-manifest-v1"
    const val PROMOTION_BLOCKED_UNTIL = "user_prompt_and_foreground_disclosure_implemented"
    const val PACKAGED_BEHAVIOR_BLOCKED = "deny_without_reviewed_disclosure_manifest"
    const val CRASH_CIRCUIT_BREAKER_PRIVACY = "metadata_only_no_script_contents"
    const val REMOTE_PACKAGED_KILL_SWITCH_POLICY = "rejected_v1_1_publish_safe_profile_patch"
    const val ANDROID_PERMISSION_SYSTEM_ALERT_WINDOW = "android.permission.SYSTEM_ALERT_WINDOW"
    const val ANDROID_PERMISSION_BIND_ACCESSIBILITY_SERVICE = "android.permission.BIND_ACCESSIBILITY_SERVICE"

    fun highRiskSurfaceIds(): List<String> = surfaces().map { it.id }

    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("schema", SCHEMA)
        put("status", STATUS)
        put("defaultProfile", SAFE_DEFAULT)
        put("effectiveProfile", SAFE_DEFAULT)
        put("metadataOnly", true)
        put("grantsAuthority", false)
        put("implicitHighPrivilegeDenied", true)
        put("packagedImplicitHighPrivilegeDenied", true)
        put("disclosureBlocksPromotion", true)
        put("foregroundDisclosureRequiredForPromotedSensitiveSurfaces", true)
        put("userPromptRequiredForPromotedSensitiveSurfaces", true)
        put("surfaces", JSONArray(surfaces().map { it.toJson() }))
        put("channels", JSONArray(channels().map { it.toJson() }))
        put("packagedPolicy", packagedPolicyJson())
        put("doctorPrivacy", doctorPrivacyJson())
        put("automationImplications", automationImplicationsJson())
        put(
            "pending",
            JSONArray(
                listOf(
                    "settings UI disclosure review",
                    "localized prompt copy",
                    "foreground service notification ownership",
                    "packaged disclosure manifest review",
                    "device matrix for screenshot recording overlay accessibility",
                    "automatic runtime enforcement before profile promotion",
                ),
            ),
        )
    }

    fun toJson(): String = toJsonObject().toString()

    private fun surfaces(): List<DisclosureSurface> = listOf(
        DisclosureSurface(
            id = "screen_capture",
            capability = NodeBridgePermissionManifest.SCREEN_CAPTURE,
            profile = PRO_COMPAT_OPT_IN,
            androidPermissions = NodeBridgePermissionManifest.androidPermissionsForCapabilities(
                listOf(NodeBridgePermissionManifest.SCREEN_CAPTURE),
            ),
            requiresUserPrompt = true,
            requiresForegroundDisclosure = true,
            requiresNotification = true,
            promptChannel = "media_projection_prompt",
            disclosureChannel = "foreground_service_notification",
            settingsIntent = "android.media.projection.MediaProjectionManager#createScreenCaptureIntent",
        ),
        DisclosureSurface(
            id = "media_recording",
            capability = NodeBridgePermissionManifest.MEDIA_RECORDING,
            profile = PRO_COMPAT_OPT_IN,
            androidPermissions = NodeBridgePermissionManifest.androidPermissionsForCapabilities(
                listOf(NodeBridgePermissionManifest.MEDIA_RECORDING),
            ),
            requiresUserPrompt = true,
            requiresForegroundDisclosure = true,
            requiresNotification = true,
            promptChannel = "android_permission_dialog",
            disclosureChannel = "foreground_service_notification",
            settingsIntent = "android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS",
        ),
        DisclosureSurface(
            id = "ui_overlay",
            capability = NodeBridgePermissionManifest.UI_OVERLAY,
            profile = PRO_COMPAT_OPT_IN,
            androidPermissions = listOf(
                ANDROID_PERMISSION_SYSTEM_ALERT_WINDOW,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_POST_NOTIFICATIONS,
            ),
            requiresUserPrompt = true,
            requiresForegroundDisclosure = true,
            requiresNotification = true,
            requiresSettingsIntent = true,
            promptChannel = "in_app_disclosure",
            disclosureChannel = "foreground_service_notification",
            settingsIntent = "android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION",
        ),
        DisclosureSurface(
            id = "accessibility",
            capability = NodeBridgePermissionManifest.ACCESSIBILITY,
            profile = PRO_COMPAT_OPT_IN,
            androidPermissions = listOf(ANDROID_PERMISSION_BIND_ACCESSIBILITY_SERVICE),
            requiresUserPrompt = true,
            requiresForegroundDisclosure = true,
            requiresSettingsIntent = true,
            promptChannel = "in_app_disclosure",
            disclosureChannel = "accessibility_service_description",
            settingsIntent = "android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS",
        ),
        DisclosureSurface(
            id = "app_query",
            capability = NodeBridgePermissionManifest.APP_QUERY,
            profile = PRO_COMPAT_OPT_IN,
            androidPermissions = emptyList(),
            requiresUserPrompt = true,
            promptChannel = "project_capability_review",
            disclosureChannel = "packaged_metadata_review",
        ),
        DisclosureSurface(
            id = "network",
            capability = NodeBridgePermissionManifest.NETWORK,
            profile = DESKTOP_COMPAT_OPT_IN,
            androidPermissions = NodeBridgePermissionManifest.androidPermissionsForCapabilities(
                listOf(NodeBridgePermissionManifest.NETWORK),
            ),
            requiresUserPrompt = true,
            promptChannel = "project_capability_review",
            disclosureChannel = "packaged_metadata_review",
        ),
        DisclosureSurface(
            id = "raw_network",
            capability = "raw_network",
            profile = DESKTOP_COMPAT_OPT_IN,
            androidPermissions = NodeBridgePermissionManifest.androidPermissionsForCapabilities(
                listOf(NodeBridgePermissionManifest.NETWORK),
            ),
            requiresUserPrompt = true,
            promptChannel = "project_capability_review",
            disclosureChannel = "packaged_metadata_review",
        ),
        DisclosureSurface(
            id = "filesystem_relaxation",
            capability = "filesystem_relaxation",
            profile = DESKTOP_COMPAT_OPT_IN,
            androidPermissions = emptyList(),
            requiresUserPrompt = true,
            promptChannel = "in_app_disclosure",
            disclosureChannel = "storage_access_framework",
        ),
        DisclosureSurface(
            id = "java_interop",
            capability = NodeBridgePermissionManifest.JAVA_INTEROP,
            profile = PRO_COMPAT_OPT_IN,
            androidPermissions = emptyList(),
            requiresUserPrompt = true,
            promptChannel = "project_capability_review",
            disclosureChannel = "doctor_report",
        ),
        DisclosureSurface(
            id = "shell_root",
            capability = NodeBridgePermissionManifest.SHELL_ROOT,
            profile = PRO_COMPAT_OPT_IN,
            androidPermissions = emptyList(),
            requiresUserPrompt = true,
            requiresForegroundDisclosure = true,
            promptChannel = "in_app_disclosure",
            disclosureChannel = "foreground_service_notification",
        ),
        DisclosureSurface(
            id = "shell_shizuku",
            capability = NodeBridgePermissionManifest.SHELL_SHIZUKU,
            profile = PRO_COMPAT_OPT_IN,
            androidPermissions = emptyList(),
            requiresUserPrompt = true,
            promptChannel = "in_app_disclosure",
            disclosureChannel = "shizuku_permission_dialog",
        ),
        DisclosureSurface(
            id = "child_process",
            capability = "child_process",
            profile = DESKTOP_COMPAT_OPT_IN,
            androidPermissions = emptyList(),
            requiresUserPrompt = true,
            promptChannel = "project_capability_review",
            disclosureChannel = "doctor_report",
        ),
        DisclosureSurface(
            id = "native_addon",
            capability = "native_addon",
            profile = DESKTOP_COMPAT_OPT_IN,
            androidPermissions = emptyList(),
            requiresUserPrompt = true,
            promptChannel = "packaged_metadata_review",
            disclosureChannel = "signed_native_artifact_review",
        ),
        DisclosureSurface(
            id = "inspector",
            capability = "inspector",
            profile = DEBUG_UNSAFE_LAB,
            androidPermissions = emptyList(),
            requiresUserPrompt = true,
            requiresForegroundDisclosure = true,
            requiresNotification = true,
            promptChannel = "debug_session_confirmation",
            disclosureChannel = "foreground_service_notification",
        ),
        DisclosureSurface(
            id = "wasi",
            capability = "wasi",
            profile = DESKTOP_COMPAT_OPT_IN,
            androidPermissions = emptyList(),
            requiresUserPrompt = true,
            promptChannel = "project_capability_review",
            disclosureChannel = "packaged_metadata_review",
        ),
    )

    private fun channels(): List<DisclosureChannel> = listOf(
        DisclosureChannel(
            id = "android_permission_dialog",
            status = "platform_controlled",
            requiredFor = listOf("media_recording"),
        ),
        DisclosureChannel(
            id = "media_projection_prompt",
            status = "platform_controlled",
            requiredFor = listOf("screen_capture"),
        ),
        DisclosureChannel(
            id = "foreground_service_notification",
            status = "required_before_promotion",
            requiredFor = listOf("screen_capture", "media_recording", "ui_overlay", "shell_root", "inspector"),
        ),
        DisclosureChannel(
            id = "in_app_disclosure",
            status = "required_before_promotion",
            requiredFor = listOf("ui_overlay", "accessibility", "filesystem_relaxation", "shell_root", "shell_shizuku"),
        ),
        DisclosureChannel(
            id = "project_capability_review",
            status = "required_before_promotion",
            requiredFor = listOf("app_query", "network", "raw_network", "java_interop", "child_process", "wasi"),
        ),
        DisclosureChannel(
            id = "packaged_metadata_review",
            status = "required_before_packaged_promotion",
            requiredFor = listOf("app_query", "network", "raw_network", "native_addon", "wasi"),
        ),
        DisclosureChannel(
            id = "doctor_report",
            status = "diagnostic_only",
            requiredFor = listOf("java_interop", "child_process"),
        ),
    )

    private fun packagedPolicyJson(): JSONObject = JSONObject()
        .put("schema", PACKAGED_DISCLOSURE_SCHEMA)
        .put("status", STATUS)
        .put("metadataOnly", true)
        .put("grantsAuthority", false)
        .put("implicitHighPrivilegeDenied", true)
        .put("requiresDisclosureManifest", true)
        .put("reviewedManifestRequiredForPromotion", true)
        .put("denyUndeclaredHighRiskSurfaces", true)
        .put("permissionSynthesisRequiresReviewedDisclosure", true)
        .put("defaultAction", PACKAGED_BEHAVIOR_BLOCKED)
        .put("manifestKeys", JSONArray(listOf("project.json:node.disclosures", "package.json:autojs6.node.disclosures")))

    private fun doctorPrivacyJson(): JSONObject = JSONObject()
        .put("scriptContentsIncluded", false)
        .put("environmentValuesIncluded", false)
        .put("pathsRedacted", true)
        .put("surfaceMetadataOnly", true)

    private fun automationImplicationsJson(): JSONObject = JSONObject()
        .put("automaticCrashCircuitBreaker", "future_metadata_only_safe_profile_fallback")
        .put("crashEvidencePrivacy", CRASH_CIRCUIT_BREAKER_PRIVACY)
        .put("remotePackagedKillSwitch", REMOTE_PACKAGED_KILL_SWITCH_POLICY)
        .put("requiresDisclosureBeforeReenable", true)
        .put("grantsAuthority", false)

    private data class DisclosureSurface(
        val id: String,
        val capability: String,
        val profile: String,
        val androidPermissions: List<String>,
        val requiresUserPrompt: Boolean,
        val requiresForegroundDisclosure: Boolean = false,
        val requiresNotification: Boolean = false,
        val requiresSettingsIntent: Boolean = false,
        val promptChannel: String,
        val disclosureChannel: String,
        val settingsIntent: String = "",
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("capability", capability)
            .put("profile", profile)
            .put("androidPermissions", JSONArray(androidPermissions))
            .put("requiresUserPrompt", requiresUserPrompt)
            .put("requiresForegroundDisclosure", requiresForegroundDisclosure)
            .put("requiresNotification", requiresNotification)
            .put("requiresSettingsIntent", requiresSettingsIntent)
            .put("promptChannel", promptChannel)
            .put("disclosureChannel", disclosureChannel)
            .put("settingsIntent", if (settingsIntent.isBlank()) JSONObject.NULL else settingsIntent)
            .put("packagedBehavior", PACKAGED_BEHAVIOR_BLOCKED)
            .put("runtimeStatus", "fail_closed_until_disclosure")
            .put("promotionBlockedUntil", PROMOTION_BLOCKED_UNTIL)
            .put("implicitGrantAllowed", false)
            .put("authority", "metadata_only_no_authority")
    }

    private data class DisclosureChannel(
        val id: String,
        val status: String,
        val requiredFor: List<String>,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("status", status)
            .put("requiredFor", JSONArray(requiredFor))
            .put("grantsAuthority", false)
    }
}
