package org.autojs.autojs.core.plugin.agent

import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityGrant
import org.autojs.autojs.engine.NodeBridgePermissionManifest

/** Host-enforced ceiling from roadmap appendix C.4; tool-group preferences only narrow it. */
internal object ThreeStoveAgentGrant {

    val allowedMethods: Set<String> = linkedMapOf(
        "accessibility" to listOf("isEnabled", "ensureEnabled", "dump", "explain", "screenshot", "readScreenText", "inspectNode",
            "findOne", "findAll", "findByText", "click", "longClick", "setText", "scrollForward", "scrollBackward",
            "swipe", "gesture", "back", "home", "recentApps"),
        "keys" to listOf("notifications", "quickSettings"),
        "agent" to listOf("listScripts", "readManifest", "execRegistered"),
        "engines" to listOf("execScript", "execScriptFile", "list", "stop", "stopAll"),
        "console" to listOf("tail"),
        "files" to listOf("list", "stat", "read", "write"),
        "app" to listOf("launchPackage", "launchApp", "isInstalled", "currentWindow", "listSamples", "readSample"),
        "package_manager" to listOf("list", "verify", "listApps"),
        "clipboard" to listOf("getText", "setText", "hasText"),
        "device" to listOf("info", "isScreenOn", "wakeUp"),
        "media_projection" to listOf("requestScreenCapture", "stop"),
        "image" to listOf("captureScreen", "recycle"),
        "shell" to listOf("exec"),
        "toast" to listOf("toast", "showToast"),
        // Self-described plugin tools (3-Macaw Speech roadmap D92): the catalog, a call and its cancellation.
        "plugin_tools" to listOf("list", "invoke", "cancel"),
    ).flatMapTo(LinkedHashSet()) { (module, methods) -> methods.map { "$module.$it" } }

    fun default(): HostCapabilityGrant = HostCapabilityGrant(
        allowedMethods = allowedMethods,
        permissions = HostCapabilityGrant.DEFAULT_PERMISSIONS - setOf(
            NodeBridgePermissionManifest.FILES_DELETE,
            NodeBridgePermissionManifest.APP_ACTIVITY,
            // Tools of USER_GRANTED providers need a per-tool switch in the consumer's settings, which Agent does not have yet (D92).
            NodeBridgePermissionManifest.PLUGIN_TOOLS_USER_GRANTED,
        ) + setOf(NodeBridgePermissionManifest.AGENT, NodeBridgePermissionManifest.AGENT_EXEC, NodeBridgePermissionManifest.OCR),
        maxConcurrentCalls = 2,
        modelCallsPerMinute = 30,
        maxTotalTokens = 1_000_000L,
        maxInputBytesPerRequest = 128 * 1024,
    )
}
