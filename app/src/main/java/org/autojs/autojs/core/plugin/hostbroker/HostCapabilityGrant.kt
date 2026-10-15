package org.autojs.autojs.core.plugin.hostbroker

import org.autojs.autojs.engine.NodeBridgeLimits
import org.autojs.autojs.engine.NodeBridgePermissionManifest
import org.autojs.autojs.engine.NodeCapabilityBrokerPolicy
import org.autojs.plugin.host.capability.api.HostCapabilityContract

/**
 * Capability ceiling shared by MCP sessions and Agent links (roadmap decision D17).
 *
 * The grant is evaluated before every bridge dispatch: the `module.method` must be listed, the
 * permission tokens a request declares must be a subset of [permissions], and the inline request
 * must fit [maxRequestBytes]. Pure Kotlin so the policy is unit-testable without Android.
 */
data class HostCapabilityGrant(
    val allowedMethods: Set<String>,
    val permissions: Set<String>,
    val maxRequestBytes: Int = NodeCapabilityBrokerPolicy.DEFAULT_MAX_REQUEST_BYTES,
    val maxConcurrentCalls: Int = HostCapabilityContract.MAX_CONCURRENT_TOOL_CALLS,
    val accessibilityQueriesPerSecond: Int = NodeBridgeLimits.defaults.accessibilityQueriesPerSecond,
    val defaultTimeoutMs: Long = HostCapabilityContract.DEFAULT_TOOL_TIMEOUT_MS,
    val maxTimeoutMs: Long = HostCapabilityContract.MAX_TOOL_TIMEOUT_MS,
    val modelCallsPerMinute: Int = 30,
    val maxTotalTokens: Long = 1_000_000L,
    val maxInputBytesPerRequest: Int = 128 * 1024,
) {

    init {
        require(maxRequestBytes in 1..HostCapabilityContract.MAX_BRIDGE_INLINE_JSON_BYTES) {
            "maxRequestBytes must be within 1..${HostCapabilityContract.MAX_BRIDGE_INLINE_JSON_BYTES}: $maxRequestBytes"
        }
        require(maxConcurrentCalls in 1..HostCapabilityContract.MAX_CONCURRENT_TOOL_CALLS) {
            "maxConcurrentCalls must be within 1..${HostCapabilityContract.MAX_CONCURRENT_TOOL_CALLS}: $maxConcurrentCalls"
        }
        require(accessibilityQueriesPerSecond >= 1) { "accessibilityQueriesPerSecond must be positive" }
        require(defaultTimeoutMs in 1..maxTimeoutMs) { "defaultTimeoutMs must be within 1..$maxTimeoutMs: $defaultTimeoutMs" }
        require(maxTimeoutMs <= HostCapabilityContract.MAX_TOOL_TIMEOUT_MS) {
            "maxTimeoutMs must not exceed ${HostCapabilityContract.MAX_TOOL_TIMEOUT_MS}: $maxTimeoutMs"
        }
        require(modelCallsPerMinute > 0) { "modelCallsPerMinute must be positive" }
        require(maxTotalTokens > 0L) { "maxTotalTokens must be positive" }
        require(maxInputBytesPerRequest > 0) { "maxInputBytesPerRequest must be positive" }
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
        /** Read-only shared storage for model-facing sessions (MCP roadmap P11 D26); joins the MCP default in the P11 grant item. */
        const val TOKEN_FILES_SHARED_READ = "files.shared_read"
        const val TOKEN_CONSOLE = "console"

        private fun methodsOf(module: String, vararg methods: String): List<String> = methods.map { key(module, it) }

        /**
         * Existing MCP-compatible default: every bridge method an MCP session may reach, plus the `keys` global
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
                    // Plugin capability tools (MCP roadmap P11 D23 / D28): screen text via an OCR plugin, screen as an image handle.
                    "readScreenText", "captureScreenImage",
                ),
            )
            addAll(methodsOf("keys", "notifications", "quickSettings", "powerDialog", "lockScreen"))
            addAll(methodsOf("engines", "execScript", "execScriptFile", "list", "stop", "stopAll"))
            addAll(methodsOf("console", "tail"))
            addAll(methodsOf("files", "list", "stat", "read", "write", "mkdir", "rename", "delete"))
            addAll(methodsOf("app", "launchPackage", "launchApp", "isInstalled", "editFile", "currentWindow", "listSamples", "readSample", "listDocs", "readDoc", "pluginCapabilities"))
            addAll(methodsOf("package_manager", "list", "verify", "listApps"))
            addAll(methodsOf("clipboard", "getText", "setText", "hasText"))
            addAll(methodsOf("device", "info", "isScreenOn", "wakeUp", "isIgnoringBatteryOptimizations"))
            addAll(methodsOf("media_projection", "requestScreenCapture", "stop"))
            // Image handles for the plugin families below: files read within the path policy, matching through the OpenCV plugin.
            addAll(methodsOf("image", "captureScreen", "recycle", "readImage", "findImage", "matchTemplate", "findColor", "findMultiColors"))
            addAll(methodsOf("ocr", "recognize", "recognizeText", "detectTextBounds"))
            addAll(methodsOf("barcode", "detect", "detectAll", "recognizeText", "recognizeTexts"))
            addAll(methodsOf("pinyin", "convert", "simple", "compare", "compact", "fromCodePoint", "fromPhrase"))
            addAll(methodsOf("pinyin4j", "of", "as", "readings"))
            addAll(methodsOf("mediainfo", "read", "get", "countGet", "capabilities"))
            addAll(methodsOf("shell", "exec"))
            addAll(methodsOf("toast", "toast", "showToast"))
            // Registered scripts (MCP roadmap P11 D42): the catalog, one manifest, and a run validated against it.
            addAll(methodsOf("agent", "listScripts", "readManifest", "execRegistered"))
        }

        /** Permission tokens of the MCP-compatible default; Agent supplies its own narrower ceiling. */
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
            NodeBridgePermissionManifest.SHELL_ROOT,
            NodeBridgePermissionManifest.TOAST,
            NodeBridgePermissionManifest.PACKAGE_MANAGER,
            TOKEN_FILES,
            TOKEN_FILES_WRITE,
            TOKEN_FILES_DELETE,
            TOKEN_FILES_SHARED_READ,
            TOKEN_CONSOLE,
            // Plugin families (MCP roadmap P11 D28); `media` pairs with `media.metadata` for the MediaInfo module.
            NodeBridgePermissionManifest.OCR,
            NodeBridgePermissionManifest.BARCODE,
            NodeBridgePermissionManifest.PINYIN,
            NodeBridgePermissionManifest.PINYIN4J,
            NodeBridgePermissionManifest.MEDIA,
            NodeBridgePermissionManifest.MEDIA_METADATA,
            // Registered scripts (MCP roadmap P11 D42): `agent` for the catalog, `agent.exec` with the engines tokens for a run.
            NodeBridgePermissionManifest.AGENT,
            NodeBridgePermissionManifest.AGENT_EXEC,
        )

        @JvmField
        val DEFAULT: HostCapabilityGrant = HostCapabilityGrant(
            allowedMethods = DEFAULT_METHODS,
            permissions = DEFAULT_PERMISSIONS,
        )
    }
}
