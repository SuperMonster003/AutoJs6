package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject

object NodeProfileSelectionDiagnostics {

    const val SCHEMA = "autojs6-node-profile-selection-diagnostics-v1"
    const val STATUS = "diagnostic_only"

    const val SAFE_DEFAULT = "safe_default"
    const val PRO_COMPAT_OPT_IN = "pro_compat_opt_in"
    const val DESKTOP_COMPAT_OPT_IN = "desktop_compat_opt_in"
    const val DEBUG_UNSAFE_LAB = "debug_unsafe_lab"

    private val profileOrder = listOf(
        SAFE_DEFAULT,
        PRO_COMPAT_OPT_IN,
        DESKTOP_COMPAT_OPT_IN,
        DEBUG_UNSAFE_LAB,
    )

    private val proBridgeCapabilities = setOf(
        NodeBridgePermissionManifest.ACCESSIBILITY,
        NodeBridgePermissionManifest.SCREEN_CAPTURE,
        NodeBridgePermissionManifest.IMAGE,
        NodeBridgePermissionManifest.OCR,
        NodeBridgePermissionManifest.BARCODE,
        NodeBridgePermissionManifest.MEDIA,
        NodeBridgePermissionManifest.MEDIA_AUDIO,
        NodeBridgePermissionManifest.MEDIA_METADATA,
        NodeBridgePermissionManifest.MEDIA_RECORDING,
        NodeBridgePermissionManifest.NOTIFICATIONS,
        NodeBridgePermissionManifest.NOTIFICATIONS_SETTINGS,
        NodeBridgePermissionManifest.SENSORS,
        NodeBridgePermissionManifest.UI,
        NodeBridgePermissionManifest.UI_OVERLAY,
        NodeBridgePermissionManifest.UI_OVERLAY_PERMISSION,
        NodeBridgePermissionManifest.UI_OVERLAY_FOREGROUND,
        NodeBridgePermissionManifest.UI_OVERLAY_PACKAGED,
        NodeBridgePermissionManifest.DEVICE_POWER,
        NodeBridgePermissionManifest.SHELL,
        NodeBridgePermissionManifest.SHELL_ROOT,
        NodeBridgePermissionManifest.SHELL_SHIZUKU,
        NodeBridgePermissionManifest.JAVA_INTEROP,
        NodeBridgePermissionManifest.RHINO,
        NodeBridgePermissionManifest.INPUT_OBSERVER,
        NodeBridgePermissionManifest.INPUT_OBSERVER_KEYS,
        NodeBridgePermissionManifest.INPUT_OBSERVER_TOUCH,
        NodeBridgePermissionManifest.INPUT_OBSERVER_INTERCEPT,
        NodeBridgePermissionManifest.FLOATY_RAW,
        NodeBridgePermissionManifest.FLOATY_TOUCH,
    )

    private val desktopBridgeCapabilities = setOf(
        NodeBridgePermissionManifest.NETWORK,
    )

    fun fromWorkingDirectory(workingDirectory: String?): Report =
        fromManifest(
            manifest = NodeBridgePermissionManifest.fromWorkingDirectory(workingDirectory),
            source = if (workingDirectory.isNullOrBlank()) "runtime_default" else "working_directory",
        )

    fun fromManifest(
        manifest: NodeBridgePermissionManifest.Manifest,
        source: String = "runtime",
    ): Report = Report(
        manifest = manifest,
        source = source,
    )

    fun requiredProfileForCapability(capability: String): String {
        val normalized = NodeBridgePermissionManifest.normalizeCapability(capability)
        if (normalized in desktopBridgeCapabilities) {
            return DESKTOP_COMPAT_OPT_IN
        }
        if (normalized in proBridgeCapabilities || normalized.startsWith("plugin.")) {
            return PRO_COMPAT_OPT_IN
        }
        return SAFE_DEFAULT
    }

    fun requiredProfilesForCapabilities(capabilities: Iterable<String>): List<String> =
        capabilities
            .map(::requiredProfileForCapability)
            .sortedByProfileOrder()

    fun requiredAndroidPermissionsForCapabilities(capabilities: Iterable<String>): List<String> =
        NodeBridgePermissionManifest.androidPermissionsForCapabilities(capabilities)

    fun failureHintForMissingCapabilities(
        moduleName: String,
        methodName: String,
        missingCapabilities: Iterable<String>,
    ): JSONObject {
        val missing = missingCapabilities
            .map(NodeBridgePermissionManifest::normalizeCapability)
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        val requiredProfiles = requiredProfilesForCapabilities(missing)
        val androidPermissions = requiredAndroidPermissionsForCapabilities(missing)
        return JSONObject().apply {
            put("schema", "autojs6-node-profile-selection-failure-hint-v1")
            put("module", moduleName)
            put("method", methodName)
            put("effectiveProfile", SAFE_DEFAULT)
            put("requiredProfiles", JSONArray(requiredProfiles))
            put("requiredNodePermissions", JSONArray(missing))
            put("requiredAndroidPermissions", JSONArray(androidPermissions))
            put("profileSelectionRequired", requiredProfiles.any { it != SAFE_DEFAULT })
            put("message", missingCapabilityMessage(moduleName, methodName, missing))
            put(
                "declarationHint",
                "Declare node.permissions/project autojs6.node.permissions for the missing capabilities; profile metadata is diagnostic-only until promotion is implemented.",
            )
            put("declarationExamples", declarationExamplesJson(missing))
        }
    }

    fun missingCapabilityMessage(
        moduleName: String,
        methodName: String,
        missingCapabilities: Iterable<String>,
    ): String {
        val missing = missingCapabilities
            .map(NodeBridgePermissionManifest::normalizeCapability)
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        val requiredProfiles = requiredProfilesForCapabilities(missing)
        val androidPermissions = requiredAndroidPermissionsForCapabilities(missing)
        return buildString {
            append("AutoJs6 bridge capability is not declared for ")
            append(moduleName)
            append(".")
            append(methodName)
            append(": ")
            append(missing.joinToString(", "))
            append("; required profile: ")
            append(requiredProfiles.ifEmpty { listOf(SAFE_DEFAULT) }.joinToString(", "))
            append("; declare node.permissions: ")
            append(missing.joinToString(", "))
            if (androidPermissions.isNotEmpty()) {
                append("; required Android permissions: ")
                append(androidPermissions.joinToString(", "))
            }
        }
    }

    data class Report(
        val manifest: NodeBridgePermissionManifest.Manifest,
        val source: String,
    ) {
        private val requestedProfiles: List<String>
            get() = manifest.packagedMetadata.requestedProfiles
                .ifEmpty { listOf(manifest.packagedMetadata.profile) }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedByProfileOrder()

        private val declaredNodePermissions: List<String>
            get() = manifest.permissions
                .map(NodeBridgePermissionManifest::normalizeCapability)
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()

        fun toJsonObject(): JSONObject = JSONObject().apply {
            val declaredPermissions = declaredNodePermissions
            val requested = requestedProfiles
            val blockedPromotions = requested.filter { it != SAFE_DEFAULT }
            put("schema", SCHEMA)
            put("status", STATUS)
            put("source", source)
            put("defaultProfile", SAFE_DEFAULT)
            put("selectedProfile", SAFE_DEFAULT)
            put("effectiveProfile", SAFE_DEFAULT)
            put("declaredProfile", manifest.packagedMetadata.profile)
            put("requestedProfiles", JSONArray(requested))
            put("selectionMode", "safe_default_effective_metadata_only")
            put("profilePromotion", false)
            put("userSelectable", false)
            put("projectSettingsSupported", true)
            put("packagedSettingsSupported", true)
            put("debugProfileReleaseDenied", true)
            put("metadataOnly", manifest.packagedMetadata.metadataOnly)
            put("grantsAuthority", manifest.packagedMetadata.grantsAuthority)
            put("declaredNodePermissions", JSONArray(declaredPermissions))
            put("declaredAndroidPermissions", JSONArray(manifest.androidPermissions))
            put("profileCatalog", profileCatalogJson(requested))
            put("highRiskCapabilities", JSONArray(highRiskCapabilitiesJson(declaredPermissions)))
            put("blockedProfilePromotions", JSONArray(blockedPromotions))
            put("failureHints", failureHintsJson())
            put("warnings", JSONArray(manifest.warnings + manifest.packagedMetadata.warnings))
        }

        fun toJson(): String = toJsonObject().toString()
    }

    private fun profileCatalogJson(requestedProfiles: List<String>): JSONArray = JSONArray(
        profileOrder.map { profile ->
            JSONObject().apply {
                put("id", profile)
                put("requested", profile in requestedProfiles)
                put("effective", profile == SAFE_DEFAULT)
                put("available", profile == SAFE_DEFAULT)
                put("status", if (profile == SAFE_DEFAULT) "effective" else "diagnostic_only")
                put("description", profileDescription(profile))
            }
        },
    )

    private fun profileDescription(profile: String): String = when (profile) {
        SAFE_DEFAULT -> "Safe default Node.js profile currently enforced at runtime."
        PRO_COMPAT_OPT_IN -> "Auto.js Pro compatibility profile requested by metadata, not promoted yet."
        DESKTOP_COMPAT_OPT_IN -> "Desktop Node.js compatibility profile requested by metadata, not promoted yet."
        DEBUG_UNSAFE_LAB -> "Debug-only unsafe lab profile requested by metadata, blocked for release."
        else -> "Unknown profile metadata, treated as diagnostic-only."
    }

    private fun highRiskCapabilitiesJson(declaredNodePermissions: List<String>): List<JSONObject> {
        val declaredCapabilities = declaredNodePermissions
            .filter { capability ->
                requiredProfileForCapability(capability) != SAFE_DEFAULT ||
                        NodeBridgePermissionManifest.androidPermissionsForCapabilities(listOf(capability)).isNotEmpty()
            }
            .map { capability ->
                JSONObject().apply {
                    put("id", capability)
                    put("source", "permission_manifest")
                    put("declared", true)
                    put("effectiveProfile", SAFE_DEFAULT)
                    put("requiredProfile", requiredProfileForCapability(capability))
                    put("profilePromoted", false)
                    put("requiredAndroidPermissions", JSONArray(requiredAndroidPermissionsForCapabilities(listOf(capability))))
                }
            }
        return declaredCapabilities
    }

    private fun failureHintsJson(): JSONObject = JSONObject().apply {
        put("capabilityNotDeclared", failureHintForMissingCapabilities("module", "method", listOf("capability")))
        put("profilePromotionBlocked", "Requested Pro/Desktop/Debug profile metadata is visible but does not grant authority in Phase 13.")
    }

    private fun declarationExamplesJson(missingCapabilities: List<String>): JSONObject = JSONObject().apply {
        put("nodePermissions", JSONArray(missingCapabilities))
        put(
            "projectJson",
            JSONObject().put(
                "node",
                JSONObject().put("permissions", JSONArray(missingCapabilities)),
            ),
        )
        put(
            "packageJson",
            JSONObject().put(
                "autojs6",
                JSONObject().put(
                    "node",
                    JSONObject().put("permissions", JSONArray(missingCapabilities)),
                ),
            ),
        )
    }

    private fun List<String>.sortedByProfileOrder(): List<String> =
        distinct().sortedWith(
            compareBy<String> { profile ->
                profileOrder.indexOf(profile).let { index -> if (index < 0) Int.MAX_VALUE else index }
            }.thenBy { it },
        )
}
