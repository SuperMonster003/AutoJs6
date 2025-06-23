package org.autojs.autojs.util

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import org.autojs.autojs.annotation.DeserializedMethodName
import org.autojs.autojs.annotation.SerializedNameCompatible
import org.json.JSONTokener
import java.lang.reflect.Field
import java.lang.reflect.Modifier
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

    /**
     * This method checks whether the given string is a valid JSON format.
     *
     * This method supports the following common JSON formats:
     * - Objects (e.g., `{"key": "value"}`)
     * - Arrays (e.g., `[1, 2, 3]`)
     * - Single values (e.g., `"string"`, `123`, `true`, `null`)
     *
     * Note:
     * 1. Empty strings or strings containing only whitespace are considered invalid JSON.
     * 2. If the JSON contains comments (e.g., `// comment` or `/* multi-line comment */`),
     *    this method will consider it invalid. Because comments are not allowed in strict JSON standards.
     *
     * zh-CN:
     *
     * 判断给定的字符串是否是有效的 JSON 格式.
     *
     * 支持以下几种常见 JSON 格式:
     * - 对象 (例如: `{"key": "value"}`)
     * - 数组 (例如: `[1, 2, 3]`)
     * - 单一值 (例如: `"string"`, `123`, `true`, `null`)
     *
     * 注意:
     * 1. 空字符串或仅包含空白字符的字符串被视为无效的 JSON.
     * 2. 如果 JSON 中包含注释 (例如: `// 注释` 或 `/* 多行注释 */`), 此方法会判断为无效. 因为注释不符合严格的 JSON 规范.
     */
    @JvmStatic
    fun isValidJson(json: String) = json.isNotBlank() && runCatching { JSONTokener(json).nextValue() }.isSuccess

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
            val deserializedAnnotation = field.getAnnotation(DeserializedMethodName::class.java)

            val sanitizedPrimaryName = sanitizeKey(serializedNameAnnotation?.value ?: field.name)
            val sanitizedAlternateNames = serializedNameAnnotation?.alternate?.map { sanitizeKey(it) } ?: emptyList()
            val sanitizedCompatibleNames = compatibleAnnotation?.with?.map { sanitizeKey(it.value) to it.isReversed } ?: emptyList()

            val serializedNames: List<Pair<String, Boolean>> = (sanitizedAlternateNames + sanitizedPrimaryName).map { it to false } + sanitizedCompatibleNames

            json.entrySet().forEach { (jsonKey, jsonValue) ->
                val sanitizedJsonKey = sanitizeKey(jsonKey)
                serializedNames.forEach { pair ->
                    val (serializedKey, isReversed) = pair
                    if (sanitizedJsonKey == serializedKey) {
                        when {
                            deserializedAnnotation != null -> {
                                handleDeserializedMethod(field, instance, jsonValue, deserializedAnnotation)
                            }
                            field.type == Boolean::class.javaPrimitiveType || field.type == Boolean::class.java -> {
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
                        return@processField
                    }
                }
            }
        }

        private fun handleDeserializedMethod(field: Field, instance: T, jsonValue: JsonElement, annotation: DeserializedMethodName) {
            require(jsonValue.isJsonPrimitive && jsonValue.asJsonPrimitive.isString) {
                "Field with @DeserializedMethodName must map to a JSON string."
            }
            val methodName = annotation.method
            val methodInput = jsonValue.asString
            val parameterTypes = annotation.parameterTypes.map { it.java }.toTypedArray()
            val method = runCatching {
                instance!!::class.java.getMethod(methodName, *parameterTypes)
            }.getOrElse { e ->
                throw RuntimeException("Method $methodName with parameter types ${parameterTypes.contentToString()} not found in ${instance!!::class.java.name}", e)
            }
            try {
                val isStatic = Modifier.isStatic(method.modifiers)
                val result = method.invoke(instance.takeUnless { isStatic }, methodInput)
                field.set(instance, result)
            } catch (e: Exception) {
                throw RuntimeException("Failed to invoke method $methodName on ${field.name}", e)
            }
        }

        private fun sanitizeKey(key: String): String {
            return key.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        }

    }

}