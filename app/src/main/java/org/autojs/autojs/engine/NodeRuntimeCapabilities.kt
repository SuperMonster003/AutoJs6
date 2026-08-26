package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class NodeRuntimeFeatureFlagOverrideTarget {
    PROJECT_JSON,
    PACKAGED_APK,
}

data class NodeRuntimeFeatureFlagAuditFinding(
    val id: String,
    val normalizedId: String?,
    val requestedValue: Boolean?,
    val reason: String,
    val source: String,
)

data class NodeRuntimeFeatureFlagAudit(
    val source: String,
    val target: NodeRuntimeFeatureFlagOverrideTarget,
    val requested: Map<String, Boolean>,
    val accepted: Map<String, Boolean>,
    val rejected: List<NodeRuntimeFeatureFlagAuditFinding>,
    val warnings: List<String>,
) {
    val hasRejectedOverrides: Boolean
        get() = rejected.isNotEmpty()

    val rejectedFeatureIds: List<String>
        get() = rejected.map { it.normalizedId ?: it.id }.distinct()

    fun toErrorMessage(): String {
        val details = rejected.joinToString("; ") { finding ->
            val id = finding.normalizedId ?: finding.id
            "$id:${finding.reason}"
        }
        return "Node feature flag override denied in $source for $target: $details"
    }
}

/**
 * Host-owned limits and metadata validation for the external Node.js runtime boundary.
 *
 * Runtime feature defaults, built-in module policy, profiles, and native/runtime version
 * metadata belong to the runtime plugin and must not be duplicated here.
 */
object NodeRuntimeCapabilities {

    const val RUNTIME_SCRIPT_SOURCE_MAX_BYTES = 524_288
    const val RUNTIME_MODULE_SOURCE_MAX_BYTES = 16_777_216
    const val RUNTIME_MODULE_SOURCES_MAX_BYTES = 67_108_864
    const val RUNTIME_MODULE_SOURCE_MAX_COUNT = 8192

    /**
     * Ceiling for module-source requests that yield no source at all, namely
     * `not_found` and `not_encrypted` results.
     *
     * The resolver probes several candidates per specifier (extension variants,
     * directory index files, package export conditions), so a plain project with
     * a large plaintext dependency graph legitimately issues far more requests
     * than it ever resolves sources. Charging those probes against the source
     * budget would reject ordinary projects that decrypt nothing, so they are
     * bounded separately and much more loosely.
     */
    const val RUNTIME_MODULE_SOURCE_PROBE_MAX_COUNT = 131_072

    /**
     * Absolute per-execution request ceiling, kept only as a termination
     * backstop. Productive requests are already bounded by
     * [RUNTIME_MODULE_SOURCE_MAX_COUNT] and the byte budgets before any
     * decryption happens, and zero-yield probes by
     * [RUNTIME_MODULE_SOURCE_PROBE_MAX_COUNT].
     */
    const val RUNTIME_MODULE_SOURCE_REQUEST_MAX_COUNT =
        RUNTIME_MODULE_SOURCE_MAX_COUNT + RUNTIME_MODULE_SOURCE_PROBE_MAX_COUNT

    /**
     * The module-source contract version this host's providers actually speak.
     *
     * Pinned to v1 on purpose: the shared contract in `nodejs-api` advanced to
     * v2 (operation-aware requests such as `materialize_missing_plaintext`),
     * but the host providers only implement v1 resolve semantics, and the
     * plugin's one-shot v2-to-v1 downgrade negotiation relies on providers
     * answering with exactly this version. Raise it only together with a real
     * v2 operation implementation.
     */
    const val RUNTIME_MODULE_SOURCE_PROVIDER_CONTRACT_VERSION = 1

    val nodeRouteFileSuffixes = setOf(".node.js", ".cjs", ".mjs", ".ts", ".mts", ".cts", ".tsx")

    private val legacyFeatureFlagAliases = mapOf(
        "dynamicimport" to "dynamic_import",
        "rawnodenetworkmodules" to "raw_node_network_modules",
        "workerthreads" to "worker_threads",
        "javainterop" to "java_interop",
        "nativeaddon" to "native_addon",
        "childprocess" to "child_process",
        "fsstreams" to "fs_streams",
        "zlibstreams" to "zlib_streams",
    )

    fun normalizeFeatureFlagId(value: String): String {
        val normalized = value
            .trim()
            .replace('-', '_')
            .replace('.', '_')
            .lowercase(Locale.US)
        return legacyFeatureFlagAliases[normalized] ?: normalized
    }

    fun auditProjectJsonFeatureFlags(
        projectJsonText: String,
        source: String = "project.json",
    ): NodeRuntimeFeatureFlagAudit {
        val warnings = mutableListOf<String>()
        val json = parseJsonObject(projectJsonText, source, warnings)
        val requested = json
            ?.optJSONObject("node")
            ?.let { collectFeatureFlagOverrides(it, "$source:node", warnings) }
            .orEmpty()
        return auditFeatureFlagOverrides(
            source = source,
            target = NodeRuntimeFeatureFlagOverrideTarget.PROJECT_JSON,
            requestedFlags = requested,
            warnings = warnings,
        )
    }

    fun auditProjectJsonFeatureFlags(
        json: JSONObject,
        source: String = "project.json",
    ): NodeRuntimeFeatureFlagAudit {
        val warnings = mutableListOf<String>()
        val requested = json
            .optJSONObject("node")
            ?.let { collectFeatureFlagOverrides(it, "$source:node", warnings) }
            .orEmpty()
        return auditFeatureFlagOverrides(
            source = source,
            target = NodeRuntimeFeatureFlagOverrideTarget.PROJECT_JSON,
            requestedFlags = requested,
            warnings = warnings,
        )
    }

    fun auditPackageJsonFeatureFlags(
        packageJsonText: String,
        source: String = "package.json",
    ): NodeRuntimeFeatureFlagAudit {
        val warnings = mutableListOf<String>()
        val json = parseJsonObject(packageJsonText, source, warnings)
        val requested = json
            ?.optJSONObject("autojs6")
            ?.optJSONObject("node")
            ?.let { collectFeatureFlagOverrides(it, "$source:autojs6.node", warnings) }
            .orEmpty()
        return auditFeatureFlagOverrides(
            source = source,
            target = NodeRuntimeFeatureFlagOverrideTarget.PACKAGED_APK,
            requestedFlags = requested,
            warnings = warnings,
        )
    }

    fun auditPackageJsonFeatureFlags(
        json: JSONObject,
        source: String = "package.json",
    ): NodeRuntimeFeatureFlagAudit {
        val warnings = mutableListOf<String>()
        val requested = json
            .optJSONObject("autojs6")
            ?.optJSONObject("node")
            ?.let { collectFeatureFlagOverrides(it, "$source:autojs6.node", warnings) }
            .orEmpty()
        return auditFeatureFlagOverrides(
            source = source,
            target = NodeRuntimeFeatureFlagOverrideTarget.PACKAGED_APK,
            requestedFlags = requested,
            warnings = warnings,
        )
    }

    fun requireNoRejectedFeatureFlagOverrides(audit: NodeRuntimeFeatureFlagAudit) {
        if (audit.hasRejectedOverrides) {
            throw IllegalArgumentException(audit.toErrorMessage())
        }
    }

    fun auditFeatureFlagOverrides(
        source: String,
        target: NodeRuntimeFeatureFlagOverrideTarget,
        requestedFlags: Map<String, Boolean>,
        warnings: List<String> = emptyList(),
    ): NodeRuntimeFeatureFlagAudit {
        val reason = when (target) {
            NodeRuntimeFeatureFlagOverrideTarget.PROJECT_JSON -> "project_json_override_denied"
            NodeRuntimeFeatureFlagOverrideTarget.PACKAGED_APK -> "packaged_apk_override_denied"
        }
        val rejected = requestedFlags.map { (rawId, requestedValue) ->
            NodeRuntimeFeatureFlagAuditFinding(
                id = rawId,
                normalizedId = normalizeFeatureFlagId(rawId),
                requestedValue = requestedValue,
                reason = reason,
                source = source,
            )
        }
        return NodeRuntimeFeatureFlagAudit(
            source = source,
            target = target,
            requested = requestedFlags,
            accepted = emptyMap(),
            rejected = rejected,
            warnings = warnings,
        )
    }

    private fun parseJsonObject(
        text: String,
        source: String,
        warnings: MutableList<String>,
    ): JSONObject? {
        return runCatching { JSONObject(text) }
            .onFailure { warnings += "Malformed Node feature flag metadata in $source: ${it.message.orEmpty()}" }
            .getOrNull()
    }

    private fun collectFeatureFlagOverrides(
        nodeConfig: JSONObject,
        source: String,
        warnings: MutableList<String>,
    ): Map<String, Boolean> {
        val requested = linkedMapOf<String, Boolean>()
        collectFeatureFlagValue(nodeConfig.opt("featureFlags"), "$source.featureFlags", requested, warnings)
        return requested
    }

    private fun collectFeatureFlagValue(
        value: Any?,
        source: String,
        requested: MutableMap<String, Boolean>,
        warnings: MutableList<String>,
    ) {
        when (value) {
            null, JSONObject.NULL -> Unit
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val enabled = value.opt(key).featureFlagBooleanValue()
                    if (enabled == null) {
                        warnings += "Node feature flag '$key' in $source must be boolean-like and was ignored."
                    } else {
                        requested[key] = enabled
                    }
                }
            }
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    val key = value.opt(index)
                    if (key is String && key.isNotBlank()) {
                        requested[key] = true
                    } else {
                        warnings += "Node feature flag entry $index in $source must be a non-empty string and was ignored."
                    }
                }
            }
            else -> warnings += "Node feature flag metadata in $source must be an object or string array and was ignored."
        }
    }

    private fun Any?.featureFlagBooleanValue(): Boolean? {
        return when (this) {
            is Boolean -> this
            is Number -> when (toInt()) {
                0 -> false
                1 -> true
                else -> null
            }
            is String -> when (trim().lowercase(Locale.US)) {
                "true", "yes", "1", "enabled", "on" -> true
                "false", "no", "0", "disabled", "off" -> false
                else -> null
            }
            else -> null
        }
    }
}
