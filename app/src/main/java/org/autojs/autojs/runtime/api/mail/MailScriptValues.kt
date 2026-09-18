package org.autojs.autojs.runtime.api.mail

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSyntaxException

/**
 * Conversions between the JSON documents of the mail contract and the plain JVM values the
 * script layer hands to Rhino (mail roadmap P3.1). A Gson tree becomes `LinkedHashMap` / `List`
 * / `String` / `Boolean` / `Int` / `Double` / `null`, which `RhinoUtils.toJsValue` turns into
 * script objects on the script thread; nothing here touches Rhino, so the JVM tests cover it.
 * zh-CN: 邮件契约 JSON 文档与脚本层交给 Rhino 的普通 JVM 值之间的转换 (邮件路线图 P3.1). Gson 树变为 `LinkedHashMap` / `List` /
 * `String` / `Boolean` / `Int` / `Double` / `null`, 由 `RhinoUtils.toJsValue` 在脚本线程上转成脚本对象; 这里不触碰 Rhino, 因此由 JVM 测试覆盖.
 */
internal object MailScriptValues {

    /** Parses [json] as a Gson tree; blank or invalid text becomes [JsonNull]. zh-CN: 把 [json] 解析为 Gson 树; 空白或非法文本成为 [JsonNull]. */
    fun parse(json: String?): JsonElement {
        if (json.isNullOrBlank()) return JsonNull.INSTANCE
        return try {
            JsonParser.parseString(json)
        } catch (_: JsonSyntaxException) {
            JsonNull.INSTANCE
        } catch (_: IllegalStateException) {
            JsonNull.INSTANCE
        }
    }

    /** The plain JVM value of [element]; objects keep their key order. zh-CN: [element] 的普通 JVM 值; 对象保持键序. */
    fun toPlain(element: JsonElement?): Any? = when {
        element == null || element.isJsonNull -> null
        element.isJsonObject -> LinkedHashMap<String, Any?>().also { map ->
            element.asJsonObject.entrySet().forEach { (key, value) -> map[key] = toPlain(value) }
        }
        element.isJsonArray -> element.asJsonArray.map(::toPlain)
        else -> primitive(element.asJsonPrimitive)
    }

    /** [toPlain] of the parsed [json]. zh-CN: 解析 [json] 后的 [toPlain]. */
    fun toPlain(json: String?): Any? = toPlain(parse(json))

    private fun primitive(primitive: JsonPrimitive): Any = when {
        primitive.isBoolean -> primitive.asBoolean
        primitive.isNumber -> number(primitive)
        else -> primitive.asString
    }

    /** Integral values that fit a script integer stay `Int`; everything else is a `Double`. zh-CN: 能放进脚本整数的整值保持 `Int`; 其余为 `Double`. */
    private fun number(primitive: JsonPrimitive): Number {
        val text = primitive.asString
        val integral = text.toLongOrNull()
        if (integral != null && integral in Int.MIN_VALUE..Int.MAX_VALUE) return integral.toInt()
        return integral?.toDouble() ?: primitive.asDouble
    }
}
