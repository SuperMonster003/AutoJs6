package org.autojs.autojs.engine

import org.autojs.autojs.core.automator.bridge.BridgeBounds
import org.autojs.autojs.core.automator.bridge.BridgeNode
import org.autojs.autojs.core.automator.bridge.BridgeSelector
import org.json.JSONObject

/**
 * JSON codec of the Node Bridge's frozen `accessibility` protocol: the selector descriptor in,
 * the seven-field node descriptor out. Shared by `AndroidNodeBridgeCapabilityProvider` and the
 * diagnostics methods of MCP roadmap P1.3 so both sides encode nodes identically.
 * zh-CN: Node Bridge 冻结的 `accessibility` 协议的 JSON 编解码: 输入选择器描述符, 输出七字段节点描述符. 由
 * `AndroidNodeBridgeCapabilityProvider` 与 MCP 路线图 P1.3 的诊断方法共用, 使两侧的节点编码完全一致.
 *
 * Created by SuperMonster003 on Sep 8, 2026.
 */
internal object NodeBridgeAccessibilityCodec {

    fun selectorOf(json: JSONObject): BridgeSelector = BridgeSelector(
        text = json.optString("text").takeIf { it.isNotBlank() },
        textContains = json.optString("textContains").takeIf { it.isNotBlank() },
        textMatches = regexOf(json, "textMatches"),
        desc = json.optString("desc").takeIf { it.isNotBlank() },
        descContains = json.optString("descContains").takeIf { it.isNotBlank() },
        descMatches = regexOf(json, "descMatches"),
        id = json.optString("id").takeIf { it.isNotBlank() },
        idMatches = regexOf(json, "idMatches"),
        className = json.optString("className").takeIf { it.isNotBlank() },
        classNameMatches = regexOf(json, "classNameMatches"),
        clickable = if (json.has("clickable")) json.optBoolean("clickable") else null,
        enabled = if (json.has("enabled")) json.optBoolean("enabled") else null,
        scrollable = if (json.has("scrollable")) json.optBoolean("scrollable") else null,
        depth = if (json.has("depth")) json.optInt("depth", -1) else null,
        boundsInside = boundsOf(json, "boundsInside"),
        boundsContains = boundsOf(json, "boundsContains"),
    )

    /** A plain pattern string or `{ pattern, flags }` with `i` / `m` / `s`. zh-CN: 纯模式字符串或带 `i` / `m` / `s` 的 `{ pattern, flags }`. */
    fun regexOf(json: JSONObject, name: String): Regex? {
        val value = json.opt(name) ?: return null
        val pattern = when (value) {
            is JSONObject -> value.optString("pattern")
            is String -> value
            else -> return null
        }.takeIf { it.isNotBlank() } ?: return null
        val flags = (value as? JSONObject)?.optString("flags").orEmpty()
        val options = buildSet {
            if ('i' in flags) add(RegexOption.IGNORE_CASE)
            if ('m' in flags) add(RegexOption.MULTILINE)
            if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
        }
        return runCatching { Regex(pattern, options) }.getOrNull()
    }

    fun boundsOf(json: JSONObject, name: String): BridgeBounds? {
        val value = json.optJSONObject(name) ?: return null
        return BridgeBounds(
            value.optInt("left"),
            value.optInt("top"),
            value.optInt("right"),
            value.optInt("bottom"),
        )
    }

    fun boundsJson(bounds: BridgeBounds): JSONObject = JSONObject()
        .put("left", bounds.left)
        .put("top", bounds.top)
        .put("right", bounds.right)
        .put("bottom", bounds.bottom)

    /** The frozen seven-field node descriptor. zh-CN: 冻结的七字段节点描述符. */
    fun nodeJson(node: BridgeNode): JSONObject = JSONObject()
        .put("text", node.text)
        .put("desc", node.desc)
        .put("id", node.id)
        .put("className", node.className)
        .put("bounds", boundsJson(node.bounds))
        .put("clickable", node.clickable)
        .put("enabled", node.enabled)

}
