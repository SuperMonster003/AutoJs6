package org.autojs.autojs.engine

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import org.autojs.plugin.ai.agent.api.AiAgentContract

/** Text-only observations; account for the encoded UTF-8 JSON, including escaping. */
internal object NodeScreenTextCodec {
    const val MAX_ITEMS = AiAgentContract.MAX_SCREEN_TEXT_ITEMS
    const val MAX_BYTES = AiAgentContract.MAX_SCREEN_TEXT_BYTES

    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
    data class Line(val text: String, val bounds: Bounds?, val confidence: Double?)

    fun encode(lines: Sequence<Line>): String {
        val result = JsonArray()
        var bytes = 2
        for (line in lines.take(MAX_ITEMS)) {
            // A single oversized observation must not allocate another unbounded JSON string.
            if (line.text.length > MAX_BYTES) break
            val item = JsonObject().apply {
                addProperty("text", line.text)
                add("confidence", line.confidence?.takeIf { it.isFinite() }?.let { com.google.gson.JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                add("bounds", line.bounds?.let { bounds -> JsonObject().apply {
                    addProperty("left", bounds.left)
                    addProperty("top", bounds.top)
                    addProperty("right", bounds.right)
                    addProperty("bottom", bounds.bottom)
                } } ?: JsonNull.INSTANCE)
            }
            val size = item.toString().toByteArray(Charsets.UTF_8).size + if (result.size() == 0) 0 else 1
            if (bytes + size > MAX_BYTES) break
            bytes += size
            result.add(item)
        }
        return result.toString()
    }
}
