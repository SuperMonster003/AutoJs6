package org.autojs.autojs.util

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import org.autojs.autojs.annotation.SerializedNameCompatible
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Field
import java.lang.reflect.Type

/**
 * Created by SuperMonster003 on Nov 20, 2024.
 */
object JsonUtils {

    @JvmStatic
    fun repairJson(input: String): String {
        // 将输入字符串进行初步处理
        var json = input.replace("\n", " ").trim()

        // 修复缺少的逗号
        json = fixMissingCommas(json)

        // 修复不匹配的引号
        json = fixQuotes(json)

        // 修复不匹配的括号
        json = fixBraces(json)

        return json
    }

    private fun fixMissingCommas(input: String): String {
        val pattern = "\"\\s*\"".toRegex()
        return pattern.replace(input) { result ->
            val match = result.value
            match[0] + "," + match.substring(1)
        }
    }

    private fun fixQuotes(input: String): String {
        val chars = input.toCharArray()
        val corrected = StringBuilder()
        var inQuote = false
        var quoteCount = 0
        for (i in chars.indices) {
            if (chars[i] == '\"') {
                inQuote = !inQuote
                quoteCount++
            }
            if (chars[i] == ':' && !inQuote && i > 0 && chars[i - 1] != '\"') {
                corrected.append('\"')
            }
            corrected.append(chars[i])
            if (chars[i] == ':' && i + 1 < chars.size && chars[i + 1] != '\"') {
                corrected.append('\"')
            }
        }
        if (quoteCount % 2 != 0) {
            corrected.append('\"')
        }
        return corrected.toString()
    }

    private fun fixBraces(input: String): String {
        var openBraces = 0
        var closeBraces = 0
        var json = input
        for (char in input) {
            if (char == '{') openBraces++
            if (char == '}') closeBraces++
        }
        while (openBraces > closeBraces) {
            json += '}'
            closeBraces++
        }
        while (closeBraces > openBraces) {
            json = "{$json"
            openBraces++
        }
        return json
    }

    fun isValidJson(json: String): Boolean = runCatching { JSONObject(json) }.isSuccess || runCatching { JSONArray(json) }.isSuccess

    class FuzzyDeserializer<T> : JsonDeserializer<T> {

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): T {
            require(typeOfT is Class<*>) {
                "Expected parameter typeOfT to be of type Class, but got: ${typeOfT?.javaClass?.name}"
            }
            @Suppress("UNCHECKED_CAST")
            val clazz = typeOfT as Class<T>
            val instance = try {
                clazz.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                throw RuntimeException("Failed to create an instance of ${clazz.name}", e)
            }

            if (json is JsonObject) {
                clazz.declaredFields.forEach { processField(it, json, instance, context) }
            }
            return instance
        }

        private fun processField(field: Field, json: JsonObject, instance: T, context: JsonDeserializationContext?) {
            field.isAccessible = true

            val serializedNameAnnotation = field.getAnnotation(SerializedName::class.java)
            val compatibleAnnotation = field.getAnnotation(SerializedNameCompatible::class.java)

            val sanitizedPrimaryName = sanitizeKey(serializedNameAnnotation?.value ?: field.name)
            val sanitizedAlternateNames = serializedNameAnnotation?.alternate?.map { sanitizeKey(it) } ?: emptyList()
            val sanitizedCompatibleNames = compatibleAnnotation?.with?.map { sanitizeKey(it.value) to it.isReversed } ?: emptyList()

            val serializedNames: List<Pair<String, Boolean>> = (sanitizedAlternateNames + sanitizedPrimaryName).map { it to false } + sanitizedCompatibleNames

            for ((jsonKey, jsonValue) in json.entrySet()) {
                val sanitizedJsonKey = sanitizeKey(jsonKey)
                for (pair in serializedNames) {
                    val (serializedKey, isReversed) = pair
                    if (sanitizedJsonKey == serializedKey) {
                        when (field.type) {
                            Boolean::class.javaPrimitiveType, Boolean::class.java -> {
                                if (jsonValue.isJsonPrimitive && jsonValue.asJsonPrimitive.isBoolean) {
                                    val value = jsonValue.asBoolean
                                    field.set(instance, if (isReversed) !value else value)
                                }
                            }
                            else -> {
                                if (!jsonValue.isJsonNull) {
                                    val value = context?.deserialize<Any>(jsonValue, field.genericType)
                                    field.set(instance, value)
                                }
                            }
                        }
                        return
                    }
                }
            }
        }

        private fun sanitizeKey(key: String): String {
            return key.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        }

    }

}