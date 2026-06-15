package org.autojs.autojs.engine

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
)

object NodeRuntimeCapabilities {

    const val PROFILE_NAME = "AutoJs6 Safe Node Profile"
    const val ENGINE_VERSION = "autojs6-node-profile-v1"
    const val NODE_HEADER_VERSION = "24.5.0"
    const val NODE_VERSION_EXPECTED_PREFIX = "v24.5.0"

    const val ESM_EXPERIMENTAL_PROPERTY = "autojs.nodejs.esm.experimental"
    const val ESM_EXPERIMENTAL_DEFAULT = false
    const val DYNAMIC_IMPORT_EXPERIMENTAL_PROPERTY = "autojs.nodejs.dynamicImport.experimental"
    const val DYNAMIC_IMPORT_EXPERIMENTAL_DEFAULT = false
    const val NETWORK_EXPERIMENTAL_PROPERTY = "autojs.nodejs.network.experimental"
    const val NETWORK_EXPERIMENTAL_DEFAULT = false
    const val WORKER_THREADS_EXPERIMENTAL_PROPERTY = "autojs.nodejs.worker_threads.experimental"
    const val WORKER_THREADS_EXPERIMENTAL_DEFAULT = false
    const val JAVA_INTEROP_EXPERIMENTAL_PROPERTY = "autojs.nodejs.javaInterop.experimental"
    const val JAVA_INTEROP_EXPERIMENTAL_DEFAULT = false

    const val RUNTIME_MODULE_SOURCE_MAX_BYTES = 262_144
    const val RUNTIME_MODULE_SOURCES_MAX_BYTES = 524_288
    const val RUNTIME_MODULE_SOURCE_MAX_COUNT = 64

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
            status = "experimental",
        ),
        NodeRuntimeFeatureFlag(
            id = "dynamic_import",
            diagnosticName = "dynamicImport",
            gradleProperty = DYNAMIC_IMPORT_EXPERIMENTAL_PROPERTY,
            defaultEnabled = DYNAMIC_IMPORT_EXPERIMENTAL_DEFAULT,
            buildTimeOverrideAllowed = true,
            projectJsonOverrideAllowed = false,
            packagedApkOverrideAllowed = false,
            securityLevel = "medium",
            status = "experimental",
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
            gradleProperty = null,
            defaultEnabled = false,
            buildTimeOverrideAllowed = false,
            projectJsonOverrideAllowed = false,
            packagedApkOverrideAllowed = false,
            securityLevel = "high",
            status = "disabled",
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
            defaultEnabled = false,
            buildTimeOverrideAllowed = false,
            projectJsonOverrideAllowed = false,
            packagedApkOverrideAllowed = false,
            securityLevel = "high",
            status = "disabled",
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
            gradleProperty = null,
            defaultEnabled = false,
            buildTimeOverrideAllowed = false,
            projectJsonOverrideAllowed = false,
            packagedApkOverrideAllowed = false,
            securityLevel = "high",
            status = "disabled",
        ),
    )
    val featureFlagsById = featureFlags.associateBy { it.id }
    val enabledByDefaultFeatureFlags = featureFlags.filter { it.defaultEnabled }
    val deniedFeatureFlags = featureFlags.filterNot { it.defaultEnabled }
    val experimentalFlags = featureFlags
        .filter { it.status == "experimental" && it.gradleProperty != null }
        .associate { it.gradleProperty!! to it.defaultEnabled }

    val backendSwitches = setOf("rhino", "native", "embedded", "auto")

    val nodeRouteFileSuffixes = setOf(".node.js", ".cjs", ".mjs")
    val commonJsModuleExtensions = setOf("js", "cjs", "json")
    val commonJsRecursiveSourceExtensions = setOf("js", "cjs")
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
        "vm",
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
}
