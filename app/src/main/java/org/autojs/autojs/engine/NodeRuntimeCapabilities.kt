package org.autojs.autojs.engine

object NodeRuntimeCapabilities {

    const val PROFILE_NAME = "AutoJs6 Safe Node Profile"
    const val ENGINE_VERSION = "autojs6-node-profile-v1"
    const val NODE_HEADER_VERSION = "24.5.0"
    const val NODE_VERSION_EXPECTED_PREFIX = "v24.5.0"

    const val ESM_EXPERIMENTAL_PROPERTY = "autojs.nodejs.esm.experimental"
    const val ESM_EXPERIMENTAL_DEFAULT = false
    const val NETWORK_EXPERIMENTAL_PROPERTY = "autojs.nodejs.network.experimental"
    const val NETWORK_EXPERIMENTAL_DEFAULT = false
    const val WORKER_THREADS_EXPERIMENTAL_PROPERTY = "autojs.nodejs.worker_threads.experimental"
    const val WORKER_THREADS_EXPERIMENTAL_DEFAULT = false

    const val RUNTIME_MODULE_SOURCE_MAX_BYTES = 262_144
    const val RUNTIME_MODULE_SOURCES_MAX_BYTES = 524_288
    const val RUNTIME_MODULE_SOURCE_MAX_COUNT = 64

    val backendSwitches = setOf("rhino", "native", "embedded", "auto")
    val experimentalFlags = mapOf(
        ESM_EXPERIMENTAL_PROPERTY to ESM_EXPERIMENTAL_DEFAULT,
        NETWORK_EXPERIMENTAL_PROPERTY to NETWORK_EXPERIMENTAL_DEFAULT,
        WORKER_THREADS_EXPERIMENTAL_PROPERTY to WORKER_THREADS_EXPERIMENTAL_DEFAULT,
    )

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
