package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class NodeRuntimeFeatureFlagOverrideTarget {
    BUILD_DEBUG,
    BUILD_RELEASE,
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

data class NodeRuntimeFeatureFlag(
    val id: String,
    val diagnosticName: String,
    val gradleProperty: String?,
    val defaultEnabled: Boolean,
    val buildTimeOverrideAllowed: Boolean,
    val projectJsonOverrideAllowed: Boolean,
    val packagedApkOverrideAllowed: Boolean,
    val securityLevel: String,
    val status: String,
) {
    val buildOnly: Boolean
        get() = buildTimeOverrideAllowed && !projectJsonOverrideAllowed && !packagedApkOverrideAllowed

    val debugOnly: Boolean
        get() = buildOnly && releaseForbidden

    val projectAllowed: Boolean
        get() = projectJsonOverrideAllowed

    val packagedAllowed: Boolean
        get() = packagedApkOverrideAllowed

    val releaseForbidden: Boolean
        get() = status == "experimental" || securityLevel == "high" || securityLevel == "critical"
}

object NodeRuntimeCapabilities {

    const val PROFILE_NAME = "AutoJs6 Safe Node Profile"
    const val ENGINE_VERSION = "autojs6-node-profile-v1.1"
    const val NODE_HEADER_VERSION = "24.5.0"
    const val NODE_VERSION_EXPECTED_PREFIX = "v24.5.0"

    const val ESM_EXPERIMENTAL_PROPERTY = "autojs.nodejs.esm.experimental"
    const val ESM_EXPERIMENTAL_DEFAULT = true
    const val DYNAMIC_IMPORT_LOCAL_PROPERTY = "autojs.nodejs.dynamicImport.local"
    const val DYNAMIC_IMPORT_LOCAL_DEFAULT = true
    const val DYNAMIC_IMPORT_EXPERIMENTAL_PROPERTY = "autojs.nodejs.dynamicImport.experimental"
    const val DYNAMIC_IMPORT_EXPERIMENTAL_DEFAULT = false
    const val NETWORK_EXPERIMENTAL_PROPERTY = "autojs.nodejs.network.experimental"
    const val NETWORK_EXPERIMENTAL_DEFAULT = false
    const val RAW_NETWORK_MODULES_EXPERIMENTAL_PROPERTY = "autojs.nodejs.rawNetworkModules.experimental"
    const val RAW_NETWORK_MODULES_EXPERIMENTAL_DEFAULT = false
    const val WORKER_THREADS_EXPERIMENTAL_PROPERTY = "autojs.nodejs.worker_threads.experimental"
    const val WORKER_THREADS_EXPERIMENTAL_DEFAULT = false
    const val CHILD_PROCESS_EXPERIMENTAL_PROPERTY = "autojs.nodejs.child_process.experimental"
    const val CHILD_PROCESS_EXPERIMENTAL_DEFAULT = false
    const val JAVA_INTEROP_EXPERIMENTAL_PROPERTY = "autojs.nodejs.javaInterop.experimental"
    const val JAVA_INTEROP_EXPERIMENTAL_DEFAULT = false

    const val RUNTIME_SCRIPT_SOURCE_MAX_BYTES = 524_288
    const val RUNTIME_MODULE_SOURCE_MAX_BYTES = 16_777_216
    const val RUNTIME_MODULE_SOURCES_MAX_BYTES = 67_108_864
    const val RUNTIME_MODULE_SOURCE_MAX_COUNT = 8192

    val featureFlags = listOf(
        NodeRuntimeFeatureFlag(
            id = "esm",
            diagnosticName = "esm",
            gradleProperty = ESM_EXPERIMENTAL_PROPERTY,
            defaultEnabled = ESM_EXPERIMENTAL_DEFAULT,
            buildTimeOverrideAllowed = true,
            projectJsonOverrideAllowed = false,
            packagedApkOverrideAllowed = false,
            securityLevel = "medium",
            status = "partial",
        ),
        NodeRuntimeFeatureFlag(
            id = "dynamic_import",
            diagnosticName = "dynamicImport",
            gradleProperty = DYNAMIC_IMPORT_LOCAL_PROPERTY,
            defaultEnabled = DYNAMIC_IMPORT_LOCAL_DEFAULT,
            buildTimeOverrideAllowed = true,
            projectJsonOverrideAllowed = false,
            packagedApkOverrideAllowed = false,
            securityLevel = "medium",
            status = "partial",
        ),
        NodeRuntimeFeatureFlag(
            id = "network",
            diagnosticName = "network",
            gradleProperty = NETWORK_EXPERIMENTAL_PROPERTY,
            defaultEnabled = NETWORK_EXPERIMENTAL_DEFAULT,
            buildTimeOverrideAllowed = true,
            projectJsonOverrideAllowed = false,
            packagedApkOverrideAllowed = false,
            securityLevel = "high",
            status = "experimental",
        ),
        NodeRuntimeFeatureFlag(
            id = "raw_node_network_modules",
            diagnosticName = "rawNodeNetworkModules",
            gradleProperty = RAW_NETWORK_MODULES_EXPERIMENTAL_PROPERTY,
            defaultEnabled = RAW_NETWORK_MODULES_EXPERIMENTAL_DEFAULT,
            buildTimeOverrideAllowed = true,
            projectJsonOverrideAllowed = false,
            packagedApkOverrideAllowed = false,
            securityLevel = "high",
            status = "experimental",
        ),
        NodeRuntimeFeatureFlag(
            id = "websocket",
            diagnosticName = "webSocket",
            gradleProperty = null,
            defaultEnabled = false,
            buildTimeOverrideAllowed = false,
            projectJsonOverrideAllowed = false,
            packagedApkOverrideAllowed = false,
            securityLevel = "high",
            status = "experimental",
        ),
        NodeRuntimeFeatureFlag(
            id = "worker_threads",
            diagnosticName = "workerThreads",
            gradleProperty = WORKER_THREADS_EXPERIMENTAL_PROPERTY,
            defaultEnabled = WORKER_THREADS_EXPERIMENTAL_DEFAULT,
            buildTimeOverrideAllowed = true,
            projectJsonOverrideAllowed = false,
            packagedApkOverrideAllowed = false,
            securityLevel = "high",
            status = "experimental",
        ),
        NodeRuntimeFeatureFlag(
            id = "fs_streams",
            diagnosticName = "fsStreams",
            gradleProperty = null,
            defaultEnabled = true,
            buildTimeOverrideAllowed = false,
            projectJsonOverrideAllowed = false,
            packagedApkOverrideAllowed = false,
            securityLevel = "medium",
            status = "partial",
        ),
        NodeRuntimeFeatureFlag(
            id = "zlib_streams",
            diagnosticName = "zlibStreams",
            gradleProperty = null,
            defaultEnabled = true,
            buildTimeOverrideAllowed = false,
            projectJsonOverrideAllowed = false,
            packagedApkOverrideAllowed = false,
            securityLevel = "medium",
            status = "partial",
        ),
        NodeRuntimeFeatureFlag(
            id = "vm",
            diagnosticName = "vm",
            gradleProperty = null,
            defaultEnabled = true,
            buildTimeOverrideAllowed = false,
            projectJsonOverrideAllowed = false,
            packagedApkOverrideAllowed = false,
            securityLevel = "high",
            status = "partial",
        ),
        NodeRuntimeFeatureFlag(
            id = "inspector",
            diagnosticName = "inspector",
            gradleProperty = null,
            defaultEnabled = false,
            buildTimeOverrideAllowed = false,
            projectJsonOverrideAllowed = false,
            packagedApkOverrideAllowed = false,
            securityLevel = "high",
            status = "disabled",
        ),
        NodeRuntimeFeatureFlag(
            id = "java_interop",
            diagnosticName = "javaInterop",
            gradleProperty = JAVA_INTEROP_EXPERIMENTAL_PROPERTY,
            defaultEnabled = JAVA_INTEROP_EXPERIMENTAL_DEFAULT,
            buildTimeOverrideAllowed = true,
            projectJsonOverrideAllowed = false,
            packagedApkOverrideAllowed = false,
            securityLevel = "critical",
            status = "experimental",
        ),
        NodeRuntimeFeatureFlag(
            id = "native_addon",
            diagnosticName = "nativeAddon",
            gradleProperty = null,
            defaultEnabled = false,
            buildTimeOverrideAllowed = false,
            projectJsonOverrideAllowed = false,
            packagedApkOverrideAllowed = false,
            securityLevel = "critical",
            status = "unsupported",
        ),
        NodeRuntimeFeatureFlag(
            id = "child_process",
            diagnosticName = "childProcess",
            gradleProperty = CHILD_PROCESS_EXPERIMENTAL_PROPERTY,
            defaultEnabled = CHILD_PROCESS_EXPERIMENTAL_DEFAULT,
            buildTimeOverrideAllowed = true,
            projectJsonOverrideAllowed = false,
            packagedApkOverrideAllowed = false,
            securityLevel = "high",
            status = "experimental",
        ),
    )
    val featureFlagsById = featureFlags.associateBy { it.id }
    private val featureFlagAliases: Map<String, String> = buildMap {
        featureFlags.forEach { flag ->
            put(flag.id, flag.id)
            put(flag.diagnosticName.lowercase(Locale.US), flag.id)
            flag.gradleProperty?.let { put(it.lowercase(Locale.US), flag.id) }
        }
        put(DYNAMIC_IMPORT_EXPERIMENTAL_PROPERTY.lowercase(Locale.US), "dynamic_import")
        put(RAW_NETWORK_MODULES_EXPERIMENTAL_PROPERTY.lowercase(Locale.US), "raw_node_network_modules")
        put("dynamicimport", "dynamic_import")
        put("rawnodenetworkmodules", "raw_node_network_modules")
        put("workerthreads", "worker_threads")
        put("javainterop", "java_interop")
        put("nativeaddon", "native_addon")
        put("childprocess", "child_process")
        put("fsstreams", "fs_streams")
        put("zlibstreams", "zlib_streams")
    }
    val enabledByDefaultFeatureFlags = featureFlags.filter { it.defaultEnabled }
    val deniedFeatureFlags = featureFlags.filterNot { it.defaultEnabled }
    val buildOnlyFeatureFlags = featureFlags.filter { it.buildOnly }
    val debugOnlyFeatureFlags = featureFlags.filter { it.debugOnly }
    val projectAllowedFeatureFlags = featureFlags.filter { it.projectAllowed }
    val packagedAllowedFeatureFlags = featureFlags.filter { it.packagedAllowed }
    val releaseForbiddenFeatureFlags = featureFlags.filter { it.releaseForbidden }
    val experimentalFlags = featureFlags
        .filter { it.status == "experimental" && it.gradleProperty != null }
        .associate { it.gradleProperty!! to it.defaultEnabled }

    fun normalizeFeatureFlagId(value: String): String {
        val normalized = value
            .trim()
            .replace('-', '_')
            .replace('.', '_')
            .lowercase(Locale.US)
        return featureFlagAliases[normalized] ?: featureFlagAliases[value.trim().lowercase(Locale.US)] ?: normalized
    }

    fun auditBuildTimeFeatureFlags(
        requestedFlags: Map<String, Boolean>,
        releaseBuild: Boolean,
        source: String = "gradle",
    ): NodeRuntimeFeatureFlagAudit {
        return auditFeatureFlagOverrides(
            source = source,
            target = if (releaseBuild) {
                NodeRuntimeFeatureFlagOverrideTarget.BUILD_RELEASE
            } else {
                NodeRuntimeFeatureFlagOverrideTarget.BUILD_DEBUG
            },
            requestedFlags = requestedFlags,
        )
    }

    fun auditProjectJsonFeatureFlags(projectJsonText: String, source: String = "project.json"): NodeRuntimeFeatureFlagAudit {
        val warnings = mutableListOf<String>()
        val json = parseJsonObject(projectJsonText, source, warnings)
        val requested = json?.optJSONObject("node")?.let { collectFeatureFlagOverrides(it, "$source:node", warnings) }.orEmpty()
        return auditFeatureFlagOverrides(
            source = source,
            target = NodeRuntimeFeatureFlagOverrideTarget.PROJECT_JSON,
            requestedFlags = requested,
            warnings = warnings,
        )
    }

    fun auditProjectJsonFeatureFlags(json: JSONObject, source: String = "project.json"): NodeRuntimeFeatureFlagAudit {
        val warnings = mutableListOf<String>()
        val requested = json.optJSONObject("node")?.let { collectFeatureFlagOverrides(it, "$source:node", warnings) }.orEmpty()
        return auditFeatureFlagOverrides(
            source = source,
            target = NodeRuntimeFeatureFlagOverrideTarget.PROJECT_JSON,
            requestedFlags = requested,
            warnings = warnings,
        )
    }

    fun auditPackageJsonFeatureFlags(packageJsonText: String, source: String = "package.json"): NodeRuntimeFeatureFlagAudit {
        val warnings = mutableListOf<String>()
        val json = parseJsonObject(packageJsonText, source, warnings)
        val node = json
            ?.optJSONObject("autojs6")
            ?.optJSONObject("node")
        val requested = node?.let { collectFeatureFlagOverrides(it, "$source:autojs6.node", warnings) }.orEmpty()
        return auditFeatureFlagOverrides(
            source = source,
            target = NodeRuntimeFeatureFlagOverrideTarget.PACKAGED_APK,
            requestedFlags = requested,
            warnings = warnings,
        )
    }

    fun auditPackageJsonFeatureFlags(json: JSONObject, source: String = "package.json"): NodeRuntimeFeatureFlagAudit {
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
        val accepted = linkedMapOf<String, Boolean>()
        val rejected = mutableListOf<NodeRuntimeFeatureFlagAuditFinding>()
        requestedFlags.forEach { (rawId, requestedValue) ->
            val normalizedId = normalizeFeatureFlagId(rawId)
            val flag = featureFlagsById[normalizedId]
            val reason = when {
                flag == null -> "unknown_flag"
                target == NodeRuntimeFeatureFlagOverrideTarget.PROJECT_JSON && !flag.projectJsonOverrideAllowed -> "project_json_override_denied"
                target == NodeRuntimeFeatureFlagOverrideTarget.PACKAGED_APK && !flag.packagedApkOverrideAllowed -> "packaged_apk_override_denied"
                target == NodeRuntimeFeatureFlagOverrideTarget.BUILD_DEBUG && requestedValue && !flag.buildTimeOverrideAllowed -> "build_time_override_denied"
                target == NodeRuntimeFeatureFlagOverrideTarget.BUILD_RELEASE && requestedValue && flag.releaseForbidden -> "release_forbidden"
                target == NodeRuntimeFeatureFlagOverrideTarget.BUILD_RELEASE && requestedValue && !flag.buildTimeOverrideAllowed -> "build_time_override_denied"
                else -> null
            }
            if (reason == null && flag != null) {
                accepted[flag.id] = requestedValue
            } else {
                rejected += NodeRuntimeFeatureFlagAuditFinding(
                    id = rawId,
                    normalizedId = flag?.id ?: normalizedId,
                    requestedValue = requestedValue,
                    reason = reason ?: "unknown_flag",
                    source = source,
                )
            }
        }
        return NodeRuntimeFeatureFlagAudit(
            source = source,
            target = target,
            requested = requestedFlags,
            accepted = accepted,
            rejected = rejected,
            warnings = warnings + rejected
                .filter { it.reason == "unknown_flag" }
                .map { "Unknown Node feature flag '${it.id}' in $source was rejected." },
        )
    }

    val backendSwitches = setOf("rhino", "native", "embedded", "auto")

    val nodeRouteFileSuffixes = setOf(".node.js", ".cjs", ".mjs", ".ts", ".mts", ".cts", ".tsx")
    val commonJsModuleExtensions = setOf("js", "cjs", "cts", "ts", "json")
    val commonJsRecursiveSourceExtensions = setOf("js", "cjs", "cts", "ts")
    val typeScriptModuleExtensions = setOf("ts", "mts", "cts")
    val unsupportedTypeScriptModuleExtensions = setOf("tsx")
    val packageJsonExportConditions = listOf("require", "node", "default")

    val directBuiltinAliases = mapOf(
        "path" to "path",
        "node:path" to "path",
        "path/posix" to "path/posix",
        "node:path/posix" to "path/posix",
        "path/win32" to "path/win32",
        "node:path/win32" to "path/win32",
        "util" to "util",
        "node:util" to "util",
        "util/types" to "util/types",
        "node:util/types" to "util/types",
        "events" to "events",
        "node:events" to "events",
        "async_hooks" to "async_hooks",
        "node:async_hooks" to "async_hooks",
        "diagnostics_channel" to "diagnostics_channel",
        "node:diagnostics_channel" to "diagnostics_channel",
        "perf_hooks" to "perf_hooks",
        "node:perf_hooks" to "perf_hooks",
    )

    val limitedBuiltinModuleNames = listOf(
        "assert",
        "assert/strict",
        "async_hooks",
        "buffer",
        "console",
        "constants",
        "crypto",
        "diagnostics_channel",
        "dns",
        "dns/promises",
        "events",
        "fs",
        "fs/promises",
        "http",
        "https",
        "module",
        "net",
        "os",
        "path",
        "path/posix",
        "path/win32",
        "perf_hooks",
        "process",
        "punycode",
        "querystring",
        "readline",
        "readline/promises",
        "stream",
        "stream/consumers",
        "stream/promises",
        "stream/web",
        "string_decoder",
        "timers",
        "timers/promises",
        "tls",
        "tty",
        "url",
        "util",
        "util/types",
        "v8",
        "vm",
        "zlib",
    )

    val commonJsBuiltinModuleNames = setOf(
        "assert",
        "async_hooks",
        "buffer",
        "child_process",
        "cluster",
        "console",
        "constants",
        "crypto",
        "diagnostics_channel",
        "dns",
        "domain",
        "events",
        "fs",
        "fs/promises",
        "http",
        "https",
        "inspector",
        "module",
        "net",
        "os",
        "path",
        "perf_hooks",
        "process",
        "punycode",
        "querystring",
        "readline",
        "repl",
        "stream",
        "string_decoder",
        "timers",
        "tls",
        "tty",
        "url",
        "util",
        "v8",
        "vm",
        "wasi",
        "worker_threads",
        "zlib",
    )

    val disabledBuiltinModuleNames = setOf(
        "child_process",
        "cluster",
        "dgram",
        "domain",
        "inspector",
        "repl",
        "wasi",
        "worker_threads",
        "zlib/promises",
    )

    val networkBuiltinModuleNames = setOf(
        "dns",
        "dns/promises",
        "http",
        "https",
        "net",
        "tls",
    )

    val disabledProcessApis = setOf(
        "abort",
        "binding",
        "_linkedBinding",
        "dlopen",
        "exit",
        "kill",
        "reallyExit",
        "_debugProcess",
        "setuid",
        "setgid",
        "seteuid",
        "setegid",
        "setgroups",
        "initgroups",
    )

    fun nodePrefixed(names: Iterable<String>): Set<String> {
        return names.flatMap { name -> listOf(name, "node:$name") }.toSet()
    }

    fun isCommonJsBuiltinModuleName(name: String): Boolean {
        return name in commonJsBuiltinModuleNames || name.removePrefix("node:") in commonJsBuiltinModuleNames
    }

    private fun parseJsonObject(text: String, source: String, warnings: MutableList<String>): JSONObject? {
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
        collectFeatureFlagValue(nodeConfig.opt("experimentalFlags"), "$source.experimentalFlags", requested, warnings)
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
