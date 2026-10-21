package org.autojs.autojs.engine

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.bridge.BridgeNode
import org.autojs.autojs.core.automator.filter.Filter
import org.json.JSONObject

/** Stateless queries for portable Flow waits. No Android nodes survive a response. */
internal object RuntimeFlowQuery {
    private val strings = setOf("text", "textContains", "desc", "descContains", "id", "className", "content")
    private val patterns = setOf("textMatches", "descMatches", "idMatches", "classNameMatches", "contentMatches")
    private val booleans = setOf("clickable", "enabled", "scrollable", "visibleToUser")
    private val bounds = setOf("boundsInside", "boundsContains")
    private val keys = strings + patterns + booleans + bounds + "depth"

    fun filter(selector: JSONObject): Filter {
        require(selector.length() > 0) { "A Flow selector must contain a condition" }
        selector.keys().forEach { key ->
            require(key in keys) { "Unsupported Flow selector field: $key" }
            val value = selector.get(key)
            when (key) {
                in strings -> require(value is String && value.isNotBlank()) { "$key must be a non-blank string" }
                in booleans -> require(value is Boolean) { "$key must be boolean" }
                in patterns -> validatePattern(value)
                in bounds -> {
                    require(value is JSONObject && value.length() == 4) { "$key requires four coordinates" }
                    listOf("left", "top", "right", "bottom").forEach { integer(value.get(it), it) }
                }
                "depth" -> require(integer(value, key) >= 0) { "depth must be non-negative" }
            }
        }
        val base = NodeBridgeAccessibilityCodec.selectorOf(selector)
        val baseFilter = base.toFilter()
        val content = selector.opt("content") as? String
        val contentPattern = if (selector.has("contentMatches")) pattern(selector.get("contentMatches")) else null
        val visible = selector.opt("visibleToUser") as? Boolean
        return object : Filter {
            override fun filter(node: UiNode<*>): Boolean {
                if (!base.isEmpty && !baseFilter.filter(node)) return false
                if (visible != null && node.visibleToUser() != visible) return false
                val values = listOf(node.text(), node.desc(), node.id())
                if (content != null && values.none { it == content }) return false
                if (contentPattern != null && values.none { it != null && contentPattern.matches(it) }) return false
                return true
            }
        }
    }

    fun snapshot(node: UiObject): JSONObject = NodeBridgeAccessibilityCodec.nodeJson(BridgeNode.of(node))
        .put("visibleToUser", node.visibleToUser())
        .put("scrollable", node.scrollable())
        .put("checked", node.checked())
        .put("selected", node.selected())
        .put("packageName", node.packageName()?.toString().orEmpty())

    private fun integer(value: Any, name: String): Int {
        require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toInt().toDouble()) { "$name must be a 32-bit integer" }
        return value.toInt()
    }

    private fun validatePattern(value: Any) { pattern(value) }

    private fun pattern(value: Any): Regex {
        val text: String
        val flags: String
        when (value) {
            is String -> { text = value; flags = "" }
            is JSONObject -> {
                require(value.keys().asSequence().all { it in setOf("pattern", "flags") }) { "Invalid regex fields" }
                text = value.get("pattern") as? String ?: throw IllegalArgumentException("pattern must be a string")
                flags = if (value.has("flags")) value.get("flags") as? String ?: throw IllegalArgumentException("flags must be a string") else ""
            }
            else -> throw IllegalArgumentException("Expected a regex string or descriptor")
        }
        require(text.isNotBlank() && flags.all { it in "ims" } && flags.toSet().size == flags.length) { "Flow patterns support non-blank expressions and i/m/s flags" }
        return Regex(text, buildSet {
            if ('i' in flags) add(RegexOption.IGNORE_CASE)
            if ('m' in flags) add(RegexOption.MULTILINE)
            if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
        })
    }
}
