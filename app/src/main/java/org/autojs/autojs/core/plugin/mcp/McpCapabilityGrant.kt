package org.autojs.autojs.core.plugin.mcp

import org.autojs.autojs.engine.NodeBridgeLimits
import org.autojs.autojs.engine.NodeBridgePermissionManifest
import org.autojs.autojs.engine.NodeCapabilityBrokerPolicy
import org.autojs.plugin.mcp.server.api.McpServerContract

/**
 * Capability ceiling the host imposes on one MCP server session (roadmap decision D17).
 *
 * The grant is evaluated before every bridge dispatch: the `module.method` must be listed, the
 * permission tokens a request declares must be a subset of [permissions], and the inline request
 * must fit [maxRequestBytes]. Pure Kotlin so the policy is unit-testable without Android.
 */
data class McpCapabilityGrant(
    val allowedMethods: Set<String>,
    val permissions: Set<String>,
    val maxRequestBytes: Int = NodeCapabilityBrokerPolicy.DEFAULT_MAX_REQUEST_BYTES,
    val maxConcurrentCalls: Int = McpServerContract.MAX_CONCURRENT_TOOL_CALLS,
    val accessibilityQueriesPerSecond: Int = NodeBridgeLimits.defaults.accessibilityQueriesPerSecond,
    val defaultTimeoutMs: Long = McpServerContract.DEFAULT_TOOL_TIMEOUT_MS,
    val maxTimeoutMs: Long = McpServerContract.MAX_TOOL_TIMEOUT_MS,
) {

    init {
        require(maxRequestBytes in 1..McpServerContract.MAX_BRIDGE_INLINE_JSON_BYTES) {
            "maxRequestBytes must be within 1..${McpServerContract.MAX_BRIDGE_INLINE_JSON_BYTES}: $maxRequestBytes"
        }
        require(maxConcurrentCalls in 1..McpServerContract.MAX_CONCURRENT_TOOL_CALLS) {
            "maxConcurrentCalls must be within 1..${McpServerContract.MAX_CONCURRENT_TOOL_CALLS}: $maxConcurrentCalls"
        }
        require(accessibilityQueriesPerSecond >= 1) { "accessibilityQueriesPerSecond must be positive" }
        require(defaultTimeoutMs in 1..maxTimeoutMs) { "defaultTimeoutMs must be within 1..$maxTimeoutMs: $defaultTimeoutMs" }
        require(maxTimeoutMs <= McpServerContract.MAX_TOOL_TIMEOUT_MS) {
            "maxTimeoutMs must not exceed ${McpServerContract.MAX_TOOL_TIMEOUT_MS}: $maxTimeoutMs"
        }
        allowedMethods.forEach { entry ->
            require(entry.count { it == '.' } == 1 && !entry.startsWith('.') && !entry.endsWith('.')) {
                "allowedMethods entries must look like module.method: $entry"
            }
        }
    }

    /** Modules that appear in [allowedMethods], in insertion order. */
    val modules: Set<String>
        get() = allowedMethods.mapTo(LinkedHashSet()) { it.substringBefore('.') }

    fun allowsMethod(module: String, method: String): Boolean = key(module, method) in allowedMethods

    /** Requested tokens (normalized) that the grant does not include. */
    fun missingPermissions(requested: Iterable<String>): List<String> = requested
        .map(NodeBridgePermissionManifest::normalizeCapability)
        .filter { it.isNotEmpty() && it !in permissions }
        .distinct()

    fun evaluate(module: String, method: String, requestedPermissions: Iterable<String>, requestBytes: Int): Decision {
        if (!allowsMethod(module, method)) {
            return Decision.Denied(Reason.METHOD_NOT_GRANTED, listOf(key(module, method)))
        }
        val missing = missingPermissions(requestedPermissions)
        if (missing.isNotEmpty()) {
            return Decision.Denied(Reason.PERMISSION_NOT_GRANTED, missing)
        }
        if (requestBytes > maxRequestBytes) {
            return Decision.Denied(Reason.REQUEST_TOO_LARGE, listOf("$requestBytes > $maxRequestBytes"))
        }
        return Decision.Allowed
    }

    /** Clamps a request timeout into the grant, substituting the default for missing or invalid values. */
    fun effectiveTimeoutMs(requestedTimeoutMs: Long?): Long = when {
        requestedTimeoutMs == null || requestedTimeoutMs <= 0L -> defaultTimeoutMs
        else -> requestedTimeoutMs.coerceAtMost(maxTimeoutMs)
    }

    enum class Reason { METHOD_NOT_GRANTED, PERMISSION_NOT_GRANTED, REQUEST_TOO_LARGE }

    sealed class Decision {
        object Allowed : Decision()
        data class Denied(val reason: Reason, val detail: List<String> = emptyList()) : Decision()
    }

    companion object {

        fun key(module: String, method: String): String = "$module.$method"

        /**
         * Tokens of bridge modules that roadmap P1.3 introduces; they are registered in
         * `NodeBridgePermissionManifest.definedCapabilities` together with the modules.
         */
        const val TOKEN_FILES = "files"
        const val TOKEN_FILES_WRITE = "files.write"
        const val TOKEN_FILES_DELETE = "files.delete"
        const val TOKEN_CONSOLE = "console"

        private fun methodsOf(module: String, vararg methods: String): List<String> = methods.map { key(module, it) }

        /**
         * Roadmap appendix A.6: every bridge method an MCP session may reach, plus the `keys` global
         * actions behind the plugin's `ui_press_key` (roadmap P3.2: notifications, quick settings,
         * the power dialog, and the lock screen; `takeScreenshot` and `splitScreen` stay outside).
         */
        @JvmField
        val DEFAULT_METHODS: Set<String> = LinkedHashSet<String>().apply {
            addAll(
                methodsOf(
                    "accessibility",
                    "isEnabled", "ensureEnabled", "dump", "explain", "screenshot",
                    "findOne", "findAll", "findByText",
                    "click", "longClick", "setText", "scrollForward", "scrollBackward", "swipe", "gesture",
                    "back", "home", "recentApps",
                ),
            )
            addAll(methodsOf("keys", "notifications", "quickSettings", "powerDialog", "lockScreen"))
            addAll(methodsOf("engines", "execScript", "execScriptFile", "list", "stop", "stopAll"))
            addAll(methodsOf("console", "tail"))
            addAll(methodsOf("files", "list", "stat", "read", "write", "mkdir", "rename", "delete"))
            addAll(methodsOf("app", "launchPackage", "launchApp", "isInstalled", "editFile", "currentWindow", "listSamples", "readSample"))
            addAll(methodsOf("package_manager", "list", "verify"))
            addAll(methodsOf("clipboard", "getText", "setText", "hasText"))
            addAll(methodsOf("device", "info", "isScreenOn", "wakeUp", "isIgnoringBatteryOptimizations"))
            addAll(methodsOf("media_projection", "requestScreenCapture", "stop"))
            addAll(methodsOf("image", "captureScreen", "recycle"))
            addAll(methodsOf("shell", "exec"))
            addAll(methodsOf("toast", "toast", "showToast"))
        }

        /** Permission tokens the default methods require, plus the P1.3 file and console tokens. */
        @JvmField
        val DEFAULT_PERMISSIONS: Set<String> = linkedSetOf(
            NodeBridgePermissionManifest.ACCESSIBILITY,
            NodeBridgePermissionManifest.ACCESSIBILITY_GESTURE,
            NodeBridgePermissionManifest.KEYS,
            NodeBridgePermissionManifest.ENGINES,
            NodeBridgePermissionManifest.ENGINES_EXEC,
            NodeBridgePermissionManifest.SCREEN_CAPTURE,
            NodeBridgePermissionManifest.IMAGE,
            NodeBridgePermissionManifest.APP,
            NodeBridgePermissionManifest.APP_LAUNCH,
            NodeBridgePermissionManifest.APP_QUERY,
            NodeBridgePermissionManifest.APP_ACTIVITY,
            NodeBridgePermissionManifest.CLIPBOARD,
            NodeBridgePermissionManifest.DEVICE,
            NodeBridgePermissionManifest.DEVICE_POWER,
            NodeBridgePermissionManifest.SHELL,
            NodeBridgePermissionManifest.TOAST,
            NodeBridgePermissionManifest.PACKAGE_MANAGER,
            TOKEN_FILES,
            TOKEN_FILES_WRITE,
            TOKEN_FILES_DELETE,
            TOKEN_CONSOLE,
        )

        @JvmField
        val DEFAULT: McpCapabilityGrant = McpCapabilityGrant(
            allowedMethods = DEFAULT_METHODS,
            permissions = DEFAULT_PERMISSIONS,
        )
    }
}
