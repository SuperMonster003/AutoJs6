package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject

/** Derive every extra permission before a tool can perform its first side effect. */
internal object RuntimeFlowPolicy {
    fun capabilities(method: String, argsJson: String): List<String> = buildList {
        add(NodeBridgePermissionManifest.ACCESSIBILITY)
        if (method != "start") return@buildList
        val request = JSONArray(argsJson)
        val operation = request.getString(2)
        val args = request.getJSONArray(3)
        val options = request.getJSONObject(4)
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
                val action = args.getString(1)
                if (action == "clickBounds") add(NodeBridgePermissionManifest.ACCESSIBILITY_GESTURE)
                if (action in setOf("copy", "cut", "paste") || action == "performAction" && args.getJSONArray(2).optInt(0) in setOf(16384, 32768, 65536)) add(NodeBridgePermissionManifest.CLIPBOARD)
            }
            "globalAction" -> if (args.getString(0) !in setOf("back", "home", "recents")) add(NodeBridgePermissionManifest.KEYS)
        }
    }.distinct()
}
