package org.autojs.autojs.engine

import org.autojs.autojs.core.automator.toolkit.*
import org.json.JSONArray
import org.json.JSONObject

internal class RuntimeFlowOptions(val json: JSONObject, name: String) {
    init {
        val allowed = when (name) {
            "smartClick", "clickIfExists", "clickAny" -> CLICK
            "smartClickBounds", "clickBoundsIfExists", "clickBoundsAny" -> CLICK - setOf("climb", "maxClimb", "gestureFallback")
            "findAny" -> COMMON
            "scrollUntil" -> COMMON + setOf("container", "direction", "maxSteps", "method", "stopAtEnd", "settle", "gestureDuration")
            "typeInto" -> COMMON + setOf("clear", "submit", "verify", "verifyTimeout", "verifyInterval")
            "dismissPopups" -> COMMON + setOf("once", "watch", "settle", "maxRounds", "debounce", "click", "onDismissed", "onError")
            "collectList" -> COMMON + setOf("direction", "maxSteps", "method", "resultType", "dedupe", "until", "maxItems", "settle", "gestureDuration")
            "launchAndWait" -> setOf("timeout", "interval", "bringToFront")
            "backUntil", "backToApp" -> COMMON + setOf("maxTimes", "delay")
            "toggle" -> COMMON + setOf("verifyTimeout", "verifyInterval", "click")
            "action" -> setOf("strict", "humanize")
            else -> throw IllegalArgumentException("Unknown Flow tool: $name")
        }
        require(json.keys().asSequence().all { it in allowed }) { "Unknown option for $name" }
    }
    fun raw(key: String) = RuntimeFlowJson.nullable(json.opt(key))
    fun bool(key: String, default: Boolean): Boolean = raw(key)?.let { require(it is Boolean) { "$key must be boolean" }; it } ?: default
    fun time(key: String, default: Long): Long = RuntimeFlowJson.millis(raw(key), default, key)
    fun count(key: String, default: Int): Int = raw(key)?.let {
        require(it is Number && it.toDouble().isFinite() && it.toDouble() == it.toInt().toDouble() && it.toInt() >= 0) { "$key must be a non-negative integer" }
        it.toInt()
    } ?: default
    fun locate() = Toolkit.Locate(time("timeout", 10000), time("interval", 100))
    fun direction() = raw("direction")?.let { ScrollDirection.of(it as? String) ?: error("Invalid scroll direction") } ?: ScrollDirection.DOWN
    fun method() = raw("method")?.let { ScrollMethod.of(it as? String) ?: error("Invalid scroll method") } ?: ScrollMethod.ACTION
    fun humanize(): Humanize {
        val value = raw("humanize")
        if (value == null || value == false || value == true) return Humanize.NONE
        require(value is JSONObject && value.keys().asSequence().all { it == "offset" || it == "delay" }) { "Invalid humanize options" }
        val offset = pair(value.opt("offset"), "humanize.offset", nonNegative = true)
        val delay = pair(value.opt("delay"), "humanize.delay", nonNegative = true)
        return Humanize(offset.first, offset.second, delay.first.toLong(), delay.second.toLong())
    }
    fun click(bounds: Boolean = false): SmartClick.Options {
        val offset = pair(raw("offset"), "offset")
        return SmartClick.Options(bool("climb", true), count("maxClimb", 5), bool("gestureFallback", true),
            raw("verify"), time("verifyTimeout", 2000), time("verifyInterval", 100), offset.first, offset.second, humanize(), bounds)
    }
    fun nestedClick(): SmartClick.Options {
        val value = raw("click") ?: return SmartClick.Options(humanize = humanize())
        require(value is JSONObject) { "click must be an options object" }
        val copy = JSONObject(value.toString())
        if (!copy.has("humanize") && json.has("humanize")) copy.put("humanize", json.get("humanize"))
        return RuntimeFlowOptions(copy, "smartClick").click()
    }
    fun back() = AppLauncher.BackOptions(count("maxTimes", 10), time("delay", 500), time("interval", 100), humanize())
    companion object {
        private val COMMON = setOf("timeout", "interval", "root", "humanize")
        private val CLICK = COMMON + setOf("climb", "maxClimb", "gestureFallback", "verify", "verifyTimeout", "verifyInterval", "offset")
        private fun pair(raw: Any?, name: String, nonNegative: Boolean = false): Pair<Int, Int> {
            val value = RuntimeFlowJson.nullable(raw) ?: return 0 to 0
            val values = if (value is JSONArray) RuntimeFlowJson.list(value).also { require(it.size == 2) { "$name requires two numbers" } } else listOf(value, value)
            val ints = values.map { require(it is Number && it.toDouble().isFinite() && it.toDouble() == it.toInt().toDouble() && (!nonNegative || it.toInt() >= 0)) { "$name requires integer pixels/milliseconds" }; it.toInt() }
            return ints[0] to ints[1]
        }
    }
}
