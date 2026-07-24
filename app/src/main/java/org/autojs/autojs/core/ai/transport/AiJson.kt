package org.autojs.autojs.core.ai.transport

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

internal fun JsonObject.stringOrNull(name: String): String? {
    val value = get(name) ?: return null
    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
    return value.asString
}

internal fun JsonObject.longOrNull(name: String): Long? {
    val value = get(name) ?: return null
    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return null
    return try {
        value.asLong
    } catch (_: RuntimeException) {
        null
    }
}

internal fun JsonObject.booleanOrNull(name: String): Boolean? {
    val value = get(name) ?: return null
    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) return null
    return value.asBoolean
}

internal fun JsonObject.objectOrNull(name: String): JsonObject? {
    val value = get(name) ?: return null
    return value.takeIf(JsonElement::isJsonObject)?.asJsonObject
}

internal fun JsonObject.arrayOrNull(name: String): JsonArray? {
    val value = get(name) ?: return null
    return value.takeIf(JsonElement::isJsonArray)?.asJsonArray
}

internal fun JsonElement?.textValue(): String? {
    val value = this ?: return null
    return when {
        value.isJsonNull -> null
        value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString
        else -> null
    }
}

internal fun JsonElement?.jsonStringOrNull(): String? {
    val value = this ?: return null
    return when {
        value.isJsonNull -> null
        value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString
        else -> value.toString()
    }
}

internal fun JsonElement.safeDeepCopy(): JsonElement {
    return when {
        isJsonNull -> JsonNull.INSTANCE
        isJsonObject -> asJsonObject.deepCopy()
        isJsonArray -> asJsonArray.deepCopy()
        isJsonPrimitive -> {
            val primitive = asJsonPrimitive
            when {
                primitive.isBoolean -> JsonPrimitive(primitive.asBoolean)
                primitive.isNumber -> JsonPrimitive(primitive.asNumber)
                else -> JsonPrimitive(primitive.asString)
            }
        }
        else -> JsonNull.INSTANCE
    }
}
