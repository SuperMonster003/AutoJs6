package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject

/** Derive every extra permission before a tool can perform its first side effect. */
internal object RuntimeFlowPolicy {
    fun capabilities(method: String, argsJson: String): List<String> = buildList {
        add(NodeBridgePermissionManifest.ACCESSIBILITY)
        if (method != "start") return@buildList
        // The capability catalogue asks for a method's base requirements without call arguments.
        // Malformed calls are rejected by the dispatcher before creating a tool job.
        val request = runCatching { JSONArray(argsJson) }.getOrNull() ?: return@buildList
        val operation = request.optString(2)
        val args = request.optJSONArray(3) ?: return@buildList
        val options = request.optJSONObject(4) ?: return@buildList
        fun smart(o: JSONObject) { if (o.opt("gestureFallback") != false) add(NodeBridgePermissionManifest.ACCESSIBILITY_GESTURE) }
        when (operation) {
            "smartClick", "clickIfExists", "clickAny" -> smart(options)
            "smartClickBounds", "clickBoundsIfExists", "clickBoundsAny" -> add(NodeBridgePermissionManifest.ACCESSIBILITY_GESTURE)
            "toggle", "dismissPopups" -> smart(options.optJSONObject("click") ?: JSONObject())
            "scrollUntil", "collectList" -> if (options.optString("method").equals("gesture", true)) add(NodeBridgePermissionManifest.ACCESSIBILITY_GESTURE)
            "launchAndWait" -> addAll(listOf(NodeBridgePermissionManifest.APP_QUERY, NodeBridgePermissionManifest.APP_LAUNCH))
            "backToApp" -> add(NodeBridgePermissionManifest.APP_QUERY)
            "waitForIdle", "waitForEvent" -> add(NodeBridgePermissionManifest.EVENTS)
            "waitForToast" -> addAll(listOf(NodeBridgePermissionManifest.EVENTS, NodeBridgePermissionManifest.EVENTS_TOAST))
            "waitForNotification" -> addAll(listOf(NodeBridgePermissionManifest.EVENTS, NodeBridgePermissionManifest.EVENTS_NOTIFICATION))
            "nodeAction" -> {
                val action = args.optString(1)
                if (action == "clickBounds") add(NodeBridgePermissionManifest.ACCESSIBILITY_GESTURE)
                if (action in setOf("copy", "cut", "paste") || action == "performAction" && args.optJSONArray(2)?.optInt(0) in setOf(16384, 32768, 65536)) add(NodeBridgePermissionManifest.CLIPBOARD)
            }
            "globalAction" -> if (args.optString(0) !in setOf("back", "home", "recents")) add(NodeBridgePermissionManifest.KEYS)
        }
    }.distinct()
}
