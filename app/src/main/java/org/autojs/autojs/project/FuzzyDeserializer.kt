package org.autojs.autojs.project

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import org.autojs.autojs.annotation.DeserializedMethodName
import org.autojs.autojs.annotation.SerializedNameCompatible
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.Type

/**
 * A custom deserializer for JSON objects that enables flexible data mapping, including
 * support for alternate key matching, reverse logic for booleans, and handling of custom
 * field deserialization methods. It also provides functionality for detecting conflicts
 * when multiple keys match the same field.
 *
 * zh-CN:
 *
 * 一个用于 JSON 对象的自定义反序列化器, 支持灵活的数据映射,
 * 包括 [备用键匹配/布尔值反向逻辑/自定义字段反序列化方法处理/多键匹配同一字段时检测冲突] 的功能.
 *
 * @param T
 * The type of the object being deserialized.
 * zh-CN: 待反序列化对象类型.
 *
 * @property detectConflicts
 * Whether to detect conflicting alias keys for the same field.
 * zh-CN: 是否检测同一字段的别名 key 冲突.
 */
class FuzzyDeserializer<T> @JvmOverloads constructor(
    private val detectConflicts: Boolean = false,
) : JsonDeserializer<T> {

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
            clazz.declaredFields.forEach { processField(it, json, instance, context, detectConflicts) }
        }
        return instance
    }

    private fun processField(
        field: Field,
        json: JsonObject,
        instance: T,
        context: JsonDeserializationContext?,
        detectConflicts: Boolean,
    ) {
        field.isAccessible = true

        val serializedNameAnnotation = field.getAnnotation(SerializedName::class.java)
        val compatibleAnnotation = field.getAnnotation(SerializedNameCompatible::class.java)
        val deserializedAnnotation = field.getAnnotation(DeserializedMethodName::class.java)

        /**
         * Use the primary @SerializedName value (or field name) as the canonical key for serialization.
         * zh-CN: 使用 @SerializedName 的主值 (或字段名) 作为写回 JSON 时的 canonical key.
         */
        val canonicalKey = serializedNameAnnotation?.value ?: field.name

        val sanitizedPrimaryName = sanitizeKey(canonicalKey)
        val sanitizedAlternateNames = serializedNameAnnotation?.alternate?.map { sanitizeKey(it) } ?: emptyList()
        val sanitizedCompatibleNames = compatibleAnnotation?.with?.map { sanitizeKey(it.value) to it.isReversed } ?: emptyList()

        val serializedNames: List<Pair<String, Boolean>> =
            (sanitizedAlternateNames + sanitizedPrimaryName).map { it to false } + sanitizedCompatibleNames

        // Collect all matched JSON keys for this field to detect conflicts.
        // zh-CN: 收集该字段命中的全部 JSON key, 用于检测冲突.
        val matchedJsonEntries = json.entrySet()
            .mapNotNull { (jsonKey, jsonValue) ->
                val sanitizedJsonKey = sanitizeKey(jsonKey)
                val matched = serializedNames.firstOrNull { (serializedKey, _) -> sanitizedJsonKey == serializedKey }
                matched?.let { Triple(jsonKey, jsonValue, it.second) }
            }

        if (detectConflicts && matchedJsonEntries.size > 1) {
            val keys = matchedJsonEntries.joinToString(", ") { "\"${it.first}\"" }
            throw IllegalArgumentException("Conflicting keys for \"$canonicalKey\": $keys")
        }

        val matched = matchedJsonEntries.lastOrNull() ?: return
        val (jsonKey, jsonValue, isReversed) = matched

        // Record the original key for later serialization if the target supports it.
        // zh-CN: 如果目标对象支持记录, 则记录原始 key, 用于后续写回时保留原 key.
        if (instance is OriginalJsonKeyAware) {
            instance.recordOriginalJsonKey(canonicalKey, jsonKey)
        }

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

    /**
     * Provide a hook to record which original JSON key was used to populate a field.
     * zh-CN: 提供一个钩子, 用于记录某个字段在反序列化时实际命中的 JSON key.
     */
    interface OriginalJsonKeyAware {

        /**
         * Record the original JSON key used for a canonical key.
         * zh-CN: 记录 canonical key 对应的原始 JSON key.
         */
        fun recordOriginalJsonKey(canonicalKey: String, originalKey: String)

    }

}
