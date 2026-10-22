@file:JvmName("SettingsJsonMaps")

package org.autojs.autojs.core.pref

/*
 * Accessors of the JSON objects that [SettingsJson.parseMap] reads (docs/settings/ROADMAP.md P5.3): the file-based JSON
 * stores keep their records as maps of plain values, and these functions read their members the way org.json's `opt*`
 * methods did, so a store reads the same values from a map that it used to read from a `JSONObject`:
 *
 * - [optString] gives a string as it is and the JSON text of other values; a missing member or `null` gives the
 *   fallback (Android's org.json gives the text "null" for a JSON null, see [SettingsJson]).
 * - [optLong] / [optInt] / [optDouble] convert any number and a numeric string (a fraction is truncated like org.json
 *   does); other values give the fallback.
 * - [optBoolean] accepts a boolean and the strings "true" / "false" in any case.
 * - [optObject] / [optArray] give a nested object or array, or null for another value.
 * - [putOrRemove] is org.json's `put(name, value)`: a null value removes the member instead of writing a JSON null.
 *
 * zh-CN: [SettingsJson.parseMap] 读出的 JSON 对象的访问函数 (ROADMAP P5.3): 文件型 JSON 存储以普通值映射作为记录, 这些函数按
 * org.json `opt*` 方法的方式读取成员, 因此存储从映射读到的值与以前从 `JSONObject` 读到的一致: [optString] 原样返回字符串,
 * 其他值返回其 JSON 文本, 缺少成员或为 null 时返回后备值 (Android 的 org.json 对 JSON null 返回文本 "null"); [optLong] /
 * [optInt] / [optDouble] 转换任意数字与数字字符串 (小数按 org.json 的方式截断), 其他值返回后备值; [optBoolean] 接受布尔值与
 * 不区分大小写的 "true" / "false"; [optObject] / [optArray] 返回嵌套的对象或数组, 其他值返回 null; [putOrRemove] 即 org.json 的
 * `put(name, value)`: 值为 null 时删除成员, 而不是写入 JSON null.
 */

fun Map<String, Any?>.optString(key: String, fallback: String = ""): String = when (val value = this[key]) {
    null -> fallback
    is String -> value
    else -> SettingsJson.stringify(value)
}

fun Map<String, Any?>.optLong(key: String, fallback: Long = 0L): Long = when (val value = this[key]) {
    is Number -> value.toLong()
    is String -> value.trim().let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() } ?: fallback
    else -> fallback
}

fun Map<String, Any?>.optInt(key: String, fallback: Int = 0): Int = when (val value = this[key]) {
    is Number -> value.toInt()
    is String -> value.trim().let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() } ?: fallback
    else -> fallback
}

fun Map<String, Any?>.optDouble(key: String, fallback: Double = Double.NaN): Double = when (val value = this[key]) {
    is Number -> value.toDouble()
    is String -> value.trim().toDoubleOrNull() ?: fallback
    else -> fallback
}

fun Map<String, Any?>.optBoolean(key: String, fallback: Boolean = false): Boolean = when (val value = this[key]) {
    is Boolean -> value
    is String -> value.toBooleanStrictOrNull() ?: value.lowercase().toBooleanStrictOrNull() ?: fallback
    else -> fallback
}

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.optObject(key: String): Map<String, Any?>? = this[key] as? Map<String, Any?>

fun Map<String, Any?>.optArray(key: String): List<Any?>? = this[key] as? List<Any?>

fun MutableMap<String, Any?>.putOrRemove(key: String, value: Any?): MutableMap<String, Any?> = apply {
    if (value == null) remove(key) else put(key, value)
}
