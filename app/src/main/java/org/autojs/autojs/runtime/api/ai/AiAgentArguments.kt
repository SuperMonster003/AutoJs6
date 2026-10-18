package org.autojs.autojs.runtime.api.ai

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract as C

/** Public script arguments are validated before any plugin admission or side effect. */
internal object AiAgentArguments {
    val optionKeys = setOf("preset", "target", "tools", "budget", "confirm", "interaction", "detached", "context", "parameters", "memory", "scriptRoots", "locale", "plan")
    val groups = setOf("observe", "act", "ocr", "gesture", "script", "script_dynamic", "files", "shell", "memory", "user", "mcp")
    private val defaultGroups = groups - setOf("gesture", "script_dynamic", "files", "shell", "mcp")
    private val budgets = mapOf("maxSteps" to 200L, "maxModelCalls" to 300L, "maxDurationMs" to 3_600_000L, "maxTotalTokens" to 1_000_000L)

    fun goal(value: String): String = text(value, "goal", C.MAX_GOAL_BYTES)

    fun options(value: JsonObject): JsonObject {
        closed(value, optionKeys)
        value["preset"]?.let { text(string(it), "preset", 256) }
        value["target"]?.let {
            val target = text(string(it), "target", 256)
            require(target.matches(Regex("[a-z0-9][a-z0-9._-]{0,127}:[a-z0-9][a-z0-9._-]{0,127}"))) { "Invalid Agent target" }
        }
        value["locale"]?.let { text(string(it), "locale", 64) }
        // plan: 3-Stove Agent plan mode (a reviewed plan before the first tool); the plugin validates it again.
        for (name in listOf("detached", "memory", "plan")) value[name]?.let { flag(it) }
        value["confirm"]?.let { require(string(it) in setOf("default", "cautious")) { "Invalid confirmation policy" } }
        value["interaction"]?.let { require(string(it) in setOf("plugin", "script")) { "Invalid interaction mode" } }
        value["context"]?.let { text(string(it), "context", C.MAX_CONTEXT_BYTES, blank = true) }
        val parameters = value["parameters"]?.let { obj(it).also { p -> size(p, 16 * 1024) } } ?: JsonObject()
        if (parameters.size() > 0) size(JsonObject().apply {
            addProperty("context", value["context"]?.asString.orEmpty()); add("parameters", parameters)
        }, C.MAX_CONTEXT_BYTES)
        value["budget"]?.let { raw ->
            val budget = obj(raw); closed(budget, budgets.keys)
            budget.entrySet().forEach { (name, number) -> integer(number, name, 1, budgets.getValue(name)) }
            val detached = value["detached"]?.asBoolean == true
            budget["maxDurationMs"]?.let { integer(it, "maxDurationMs", 1, if (detached) 3_600_000 else 1_800_000) }
        }
        value["tools"]?.let { effectiveTools(it, groups) }
        value["scriptRoots"]?.let { raw ->
            strings(raw, "scriptRoots", 32).forEach { path ->
                text(path, "scriptRoots", 1024)
                require(path.startsWith('/') && path != "/" && path.none { it < ' ' || it == '\\' } && path.split('/').none { it == "." || it == ".." }) { "Invalid script root" }
            }
        }
        size(value, 28 * 1024)
        return value.deepCopy()
    }

    /** An assistant's budget/tool/root restrictions survive per-call overrides. */
    fun merge(base: JsonObject, overrides: JsonObject): JsonObject {
        options(base); options(overrides)
        val merged = base.deepCopy().apply { overrides.entrySet().forEach { (key, value) -> add(key, value.deepCopy()) } }
        if (base.has("budget") && overrides.has("budget")) {
            val before = base.getAsJsonObject("budget")
            val budget = before.deepCopy()
            overrides.getAsJsonObject("budget").entrySet().forEach { (key, value) ->
                require(!before.has(key) || value.asLong <= before[key].asLong) { "Assistant budget can only be narrowed" }
                budget.add(key, value.deepCopy())
            }
            merged.add("budget", budget)
        }
        if (base.has("tools") && overrides.has("tools")) {
            val allowed = effectiveTools(base["tools"])
            val next = effectiveTools(overrides["tools"], allowed)
            require(allowed.containsAll(next)) { "Assistant tools can only be narrowed" }
            merged.add("tools", JsonArray().apply { next.sorted().forEach(::add) })
        }
        if (base.has("scriptRoots") && overrides.has("scriptRoots")) {
            require(strings(base["scriptRoots"], "scriptRoots", 32).containsAll(strings(overrides["scriptRoots"], "scriptRoots", 32))) { "Assistant roots can only be narrowed" }
        }
        if (base["confirm"]?.asString == "cautious") require(merged["confirm"].asString == "cautious") { "Assistant confirmation policy can only be narrowed" }
        return options(merged)
    }

    fun effectiveTools(value: JsonElement, defaults: Set<String> = defaultGroups): Set<String> = if (value.isJsonArray) {
        strings(value, "tools", groups.size).also { require(groups.containsAll(it)) { "Unknown tool group" } }.toSet()
    } else {
        val tools = obj(value); closed(tools, setOf("enable", "disable"))
        require(tools.entrySet().all { it.value.isJsonArray }) { "Tool enable/disable must be arrays" }
        val enable = tools["enable"]?.let { effectiveTools(it, defaults) } ?: defaults
        val disable = tools["disable"]?.let { effectiveTools(it, defaults) }.orEmpty()
        enable - disable
    }

    fun filter(value: JsonObject): JsonObject {
        closed(value, setOf("state", "preset", "since", "until", "limit"))
        value["state"]?.let { raw ->
            val states = if (raw.isJsonArray) strings(raw, "state", C.RUN_STATES.size) else listOf(string(raw))
            require(states.isNotEmpty() && C.RUN_STATES.containsAll(states)) { "Invalid run state" }
        }
        value["preset"]?.let { text(string(it), "preset", 256) }
        for (name in listOf("since", "until")) value[name]?.let { integer(it, name, 0, 9_007_199_254_740_991) }
        if (value.has("since") && value.has("until")) require(value["since"].asLong <= value["until"].asLong)
        value["limit"]?.let { integer(it, "limit", 1, 50) }
        return value.deepCopy()
    }

    fun matches(row: JsonObject, filter: JsonObject): Boolean {
        filter["state"]?.let { states ->
            if (states.isJsonArray && states.asJsonArray.none { it.asString == row["state"]?.asString }) return false
            if (!states.isJsonArray && states.asString != row["state"]?.asString) return false
        }
        if (filter.has("preset") && filter["preset"].asString != (row["preset"]?.asString ?: "default")) return false
        val time = row["startedAt"]?.asLong ?: 0
        return (!filter.has("since") || time >= filter["since"].asLong) && (!filter.has("until") || time <= filter["until"].asLong)
    }

    fun runId(value: String): String = value.also { require(it.matches(Regex("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}"))) { "Invalid Agent run ID" } }
    fun text(value: String, name: String, maximum: Int, blank: Boolean = false): String = value.also {
        require((blank || it.isNotBlank()) && it.toByteArray(Charsets.UTF_8).size <= maximum) { "Invalid Agent $name" }
        var index = 0
        while (index < it.length) {
            val c = it[index++]
            require(!c.isLowSurrogate()) { "Invalid Unicode in $name" }
            if (c.isHighSurrogate()) require(index < it.length && it[index++].isLowSurrogate()) { "Invalid Unicode in $name" }
        }
    }
    fun integer(value: JsonElement, name: String, minimum: Long, maximum: Long): Long {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "$name must be an integer" }
        val number = try { value.asBigDecimal.longValueExact() } catch (_: Exception) { throw IllegalArgumentException("$name must be an integer") }
        require(number in minimum..maximum) { "$name is out of range" }
        return number
    }
    private fun strings(value: JsonElement, name: String, maximum: Int): List<String> {
        require(value.isJsonArray && value.asJsonArray.size() <= maximum) { "Invalid $name array" }
        return value.asJsonArray.map(::string).also { require(it.distinct().size == it.size) { "Duplicate $name" } }
    }
    private fun string(value: JsonElement): String {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Expected string" }; return value.asString
    }
    private fun flag(value: JsonElement) { require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "Expected boolean" } }
    private fun obj(value: JsonElement): JsonObject { require(value.isJsonObject) { "Expected object" }; return value.asJsonObject }
    private fun closed(value: JsonObject, keys: Set<String>) { require(keys.containsAll(value.keySet())) { "Unknown Agent option" } }
    private fun size(value: JsonElement, maximum: Int) { require(value.toString().toByteArray(Charsets.UTF_8).size <= maximum) { "Agent options exceed byte limit" } }
}
