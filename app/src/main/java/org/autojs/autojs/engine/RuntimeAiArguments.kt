package org.autojs.autojs.engine

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.autojs.autojs.runtime.api.ai.AiRequestInput

/** JSON input forms shared by the portable AI clients; provider validation stays in the AI core. */
internal object RuntimeAiArguments {
    fun request(args: JsonArray): AiRequestInput {
        require(args.size() in 1..2) { "AI expects a prompt/messages and optional options" }
        val value = args[0]
        val options = JsonObject()
        val messages = when {
            value.isJsonPrimitive && value.asJsonPrimitive.isString -> JsonArray().apply {
                add(JsonObject().apply { addProperty("role", "user"); add("content", value.deepCopy()) })
            }
            value.isJsonArray -> value.asJsonArray.deepCopy()
            value.isJsonObject && value.asJsonObject.has("messages") -> {
                val request = value.asJsonObject.deepCopy()
                val entries = request.remove("messages")
                require(entries.isJsonArray) { "AI messages must be an array" }
                request.entrySet().forEach { (key, item) -> options.add(key, item) }
                entries.asJsonArray
            }
            value.isJsonObject && value.asJsonObject.has("role") && value.asJsonObject.has("content") ->
                JsonArray().apply { add(value.deepCopy()) }
            else -> throw IllegalArgumentException("AI input must be a string, messages or a request object")
        }
        require(messages.size() > 0) { "AI messages must not be empty" }
        objectOrEmpty(args.getOrNull(1)).entrySet().forEach { (key, item) -> options.add(key, item.deepCopy()) }
        return AiRequestInput(messages, options)
    }

    fun session(args: JsonArray): AiRequestInput {
        require(args.size() <= 1) { "AI session expects optional options" }
        val options = objectOrEmpty(args.getOrNull(0)).deepCopy()
        val messages = JsonArray()
        options.remove("system")?.let {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotEmpty()) {
                "AI session system must be a nonempty string"
            }
            messages.add(JsonObject().apply { addProperty("role", "system"); add("content", it) })
        }
        messages.add(JsonObject().apply { addProperty("role", "user"); addProperty("content", "-") })
        return AiRequestInput(messages, options)
    }

    fun catalog(args: JsonArray): AiRequestInput {
        require(args.size() <= 1) { "AI catalog expects optional options" }
        return AiRequestInput(JsonArray(), objectOrEmpty(args.getOrNull(0)).deepCopy())
    }

    private fun objectOrEmpty(value: JsonElement?): JsonObject {
        if (value == null || value.isJsonNull) return JsonObject()
        require(value.isJsonObject) { "AI options must be an object" }
        return value.asJsonObject
    }

    private fun JsonArray.getOrNull(index: Int) = if (index < size()) get(index) else null
}
