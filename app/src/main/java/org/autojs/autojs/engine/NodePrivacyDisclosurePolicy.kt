package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * Host-owned consent facts for sensitive Node.js bridge capabilities.
 *
 * Every bridge capability is gated by the project's `node.permissions` manifest. This block
 * additionally lists, for the sensitive ones, the Android permissions and the consent surface
 * (platform dialog, system settings screen or manifest declaration) the host relies on. Profile
 * promotion, packaged disclosure manifests and planned automation were plugin or roadmap
 * metadata and are no longer reported by the host; capability semantics come from the installed
 * plugin's capability catalog and `autojs6:profile`.
 */
object NodePrivacyDisclosurePolicy {

    const val SCHEMA = "autojs6-node-privacy-disclosure-policy-v2"
    const val STATUS = "host_consent_surfaces"

    const val ANDROID_PERMISSION_SYSTEM_ALERT_WINDOW = "android.permission.SYSTEM_ALERT_WINDOW"
    const val ANDROID_PERMISSION_BIND_ACCESSIBILITY_SERVICE = "android.permission.BIND_ACCESSIBILITY_SERVICE"

    const val CONSENT_ANDROID_PERMISSION_DIALOG = "android_permission_dialog"
    const val CONSENT_MEDIA_PROJECTION_PROMPT = "media_projection_prompt"
    const val CONSENT_SYSTEM_SETTINGS = "system_settings"
    const val CONSENT_SUPERUSER_PROMPT = "superuser_prompt"
    const val CONSENT_SHIZUKU_PERMISSION_DIALOG = "shizuku_permission_dialog"
    const val CONSENT_NODE_PERMISSIONS_MANIFEST = "node_permissions_manifest"

    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("schema", SCHEMA)
        put("status", STATUS)
        put("owner", "host")
        put("manifestGate", "node.permissions")
        put("grantsAuthority", false)
        put("implicitHighPrivilegeDenied", true)
        put("surfaces", JSONArray(surfaces().map { it.toJson() }))
        put("doctorPrivacy", doctorPrivacyJson())
        put(
            "note",
            "Capability semantics and profile metadata are reported by the installed Node.js plugin; this block lists only the Android permissions and consent surfaces the host relies on.",
        )
    }

    private fun surfaces(): List<ConsentSurface> = listOf(
        ConsentSurface(
            id = "screen_capture",
            capability = NodeBridgePermissionManifest.SCREEN_CAPTURE,
            androidPermissions = NodeBridgePermissionManifest.androidPermissionsForCapabilities(
                listOf(NodeBridgePermissionManifest.SCREEN_CAPTURE),
            ),
            consent = CONSENT_MEDIA_PROJECTION_PROMPT,
            settingsIntent = "android.media.projection.MediaProjectionManager#createScreenCaptureIntent",
        ),
        ConsentSurface(
            id = "media_recording",
            capability = NodeBridgePermissionManifest.MEDIA_RECORDING,
            androidPermissions = NodeBridgePermissionManifest.androidPermissionsForCapabilities(
                listOf(NodeBridgePermissionManifest.MEDIA_RECORDING),
            ),
            consent = CONSENT_ANDROID_PERMISSION_DIALOG,
            settingsIntent = "android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS",
        ),
        ConsentSurface(
            id = "ui_overlay",
            capability = NodeBridgePermissionManifest.UI_OVERLAY,
            androidPermissions = listOf(
                ANDROID_PERMISSION_SYSTEM_ALERT_WINDOW,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_POST_NOTIFICATIONS,
            ),
            consent = CONSENT_SYSTEM_SETTINGS,
            settingsIntent = "android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION",
        ),
        ConsentSurface(
            id = "accessibility",
            capability = NodeBridgePermissionManifest.ACCESSIBILITY,
            androidPermissions = listOf(ANDROID_PERMISSION_BIND_ACCESSIBILITY_SERVICE),
            consent = CONSENT_SYSTEM_SETTINGS,
            settingsIntent = "android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS",
        ),
        ConsentSurface(
            id = "app_query",
            capability = NodeBridgePermissionManifest.APP_QUERY,
            androidPermissions = emptyList(),
            consent = CONSENT_NODE_PERMISSIONS_MANIFEST,
        ),
        ConsentSurface(
            id = "network",
            capability = NodeBridgePermissionManifest.NETWORK,
            androidPermissions = NodeBridgePermissionManifest.androidPermissionsForCapabilities(
                listOf(NodeBridgePermissionManifest.NETWORK),
            ),
            consent = CONSENT_NODE_PERMISSIONS_MANIFEST,
        ),
        ConsentSurface(
            id = "java_interop",
            capability = NodeBridgePermissionManifest.JAVA_INTEROP,
            androidPermissions = emptyList(),
            consent = CONSENT_NODE_PERMISSIONS_MANIFEST,
        ),
        ConsentSurface(
            id = "shell_root",
            capability = NodeBridgePermissionManifest.SHELL_ROOT,
            androidPermissions = emptyList(),
            consent = CONSENT_SUPERUSER_PROMPT,
        ),
        ConsentSurface(
            id = "shell_shizuku",
            capability = NodeBridgePermissionManifest.SHELL_SHIZUKU,
            androidPermissions = emptyList(),
            consent = CONSENT_SHIZUKU_PERMISSION_DIALOG,
        ),
    )

    private fun doctorPrivacyJson(): JSONObject = JSONObject()
        .put("scriptContentsIncluded", false)
        .put("environmentValuesIncluded", false)
        .put("pathsRedacted", true)
        .put("surfaceMetadataOnly", true)

    private data class ConsentSurface(
        val id: String,
        val capability: String,
        val androidPermissions: List<String>,
        val consent: String,
        val settingsIntent: String = "",
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("capability", capability)
            .put("androidPermissions", JSONArray(androidPermissions))
            .put("consent", consent)
            .put("settingsIntent", if (settingsIntent.isBlank()) JSONObject.NULL else settingsIntent)
            .put("implicitGrantAllowed", false)
    }
}
