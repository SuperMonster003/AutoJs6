package org.autojs.autojs.core.accessibility.codegen.store

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Bounded strict JSON primitives shared by the catalog and durable trust records. */
internal object ModelCatalogJson {
    fun parse(bytes: ByteArray, maxBytes: Int): JsonObject {
        require(bytes.size <= maxBytes) { "Catalog JSON exceeds its byte limit" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        return JsonReader(StringReader(text)).use { reader ->
            reader.strictness = Strictness.STRICT
            val result = read(reader, 0)
            require(reader.peek() == JsonToken.END_DOCUMENT && result.isJsonObject) { "Expected one JSON object" }
            result.asJsonObject
        }
    }

    private fun read(reader: JsonReader, depth: Int): JsonElement {
        require(depth < 32) { "Catalog JSON is nested too deeply" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().also { obj ->
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    require(!obj.has(key)) { "Duplicate JSON key: $key" }
                    obj.add(key, read(reader, depth + 1))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().also { array ->
                reader.beginArray()
                while (reader.hasNext()) array.add(read(reader, depth + 1))
                reader.endArray()
            }
            JsonToken.STRING -> JsonPrimitive(reader.nextString())
            JsonToken.NUMBER -> JsonParser.parseString(reader.nextString()).also { require(it.asDouble.isFinite()) { "Non-finite JSON number" } }
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> JsonNull.INSTANCE.also { reader.nextNull() }
            else -> throw IllegalArgumentException("Unexpected JSON token")
        }
    }

    fun obj(value: JsonElement?): JsonObject {
        require(value?.isJsonObject == true) { "Expected an object" }
        return value.asJsonObject
    }

    fun string(value: JsonElement?, maxLength: Int = 1024): String {
        require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isString) { "Expected a string" }
        return value.asString.also { require(it.isNotBlank() && it.length <= maxLength && it.none(Char::isISOControl)) { "Invalid bounded string" } }
    }

    fun integer(value: JsonElement?, minimum: Long = 1, maximum: Long = Int.MAX_VALUE.toLong()): Long {
        require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isNumber) { "Expected an integer" }
        val raw = value.asString
        require(Regex("-?(0|[1-9][0-9]*)").matches(raw)) { "Expected a lexical integer" }
        return requireNotNull(raw.toLongOrNull()) { "Integer is too large" }.also { require(it in minimum..maximum) { "Integer is out of range" } }
    }

    fun array(value: JsonElement?, minimum: Int = 0, maximum: Int = 4096): JsonArray {
        require(value?.isJsonArray == true) { "Expected an array" }
        return value.asJsonArray.also { require(it.size() in minimum..maximum) { "Array length is out of range" } }
    }

    fun flag(value: JsonElement?): Boolean {
        if (value == null) return false
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "Expected a boolean" }
        return value.asBoolean
    }

    fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    /** Android-24-compatible, strict RFC 4648 decoder; no java.util.Base64 dependency. */
    fun base64(value: String, maxBytes: Int): ByteArray {
        require(value.length <= (maxBytes.toLong() + 2) / 3 * 4 && value.length % 4 == 0) { "Invalid Base64 length" }
        val padding = if (value.endsWith("==")) 2 else if (value.endsWith('=')) 1 else 0
        val length = value.length / 4 * 3 - padding
        require(length in 1..maxBytes) { "Invalid decoded catalog length" }
        val output = ByteArray(length)
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        var target = 0
        for (offset in value.indices step 4) {
            var bits = 0
            for (index in 0..3) {
                val at = offset + index
                val char = value[at]
                val part = if (char == '=') {
                    require(at >= value.length - padding && index >= 2) { "Invalid Base64 padding" }
                    0
                } else alphabet.indexOf(char).also { require(it >= 0 && at < value.length - padding) { "Invalid Base64 character" } }
                bits = (bits shl 6) or part
            }
            if (offset + 4 == value.length) {
                require(padding != 1 || bits and 0xff == 0) { "Noncanonical Base64 padding" }
                require(padding != 2 || bits and 0xffff == 0) { "Noncanonical Base64 padding" }
            }
            if (target < output.size) output[target++] = (bits ushr 16).toByte()
            if (target < output.size) output[target++] = (bits ushr 8).toByte()
            if (target < output.size) output[target++] = bits.toByte()
        }
        return output
    }
}
