package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * Host-side echo of the profile metadata declared by a Node.js project.
 *
 * The host reads the declaration from the bridge permission manifest and reports it verbatim in
 * the doctor report. Profile semantics, stability and whether a profile grants anything are owned
 * by the installed plugin (`autojs6:profile`, runtime info and the capability catalog). The host
 * never promotes a profile and does not derive required profiles or failure hints from
 * capabilities; the permission manifest is the only host-side gate.
 */
object NodeProfileSelectionDiagnostics {

    const val SCHEMA = "autojs6-node-profile-selection-diagnostics-v2"
    const val STATUS = "host_manifest_echo"
    const val OWNER = "plugin_runtime"

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
            put("schema", SCHEMA)
            put("status", STATUS)
            put("owner", OWNER)
            put("source", source)
            put("declaredProfile", manifest.packagedMetadata.profile)
            put("requestedProfiles", JSONArray(requestedProfiles))
            put("metadataOnly", manifest.packagedMetadata.metadataOnly)
            put("grantsAuthority", manifest.packagedMetadata.grantsAuthority)
            put("declaredNodePermissions", JSONArray(declaredPermissions))
            put("declaredAndroidPermissions", JSONArray(manifest.androidPermissions))
            put(
                "androidPermissionsRequiredByNodePermissions",
                JSONArray(NodeBridgePermissionManifest.androidPermissionsForCapabilities(declaredPermissions)),
            )
            put("warnings", JSONArray(manifest.warnings + manifest.packagedMetadata.warnings))
            put(
                "note",
                "Profile semantics and stability are reported by the installed Node.js plugin; the host only echoes the declared metadata and enforces node.permissions plus Android permissions.",
            )
        }

        fun toJson(): String = toJsonObject().toString()
    }

    private fun List<String>.sortedByProfileOrder(): List<String> =
        distinct().sortedWith(
            compareBy<String> { profile ->
                profileOrder.indexOf(profile).let { index -> if (index < 0) Int.MAX_VALUE else index }
            }.thenBy { it },
        )
}
