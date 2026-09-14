package org.autojs.autojs.ui.floating.layoutinspector

import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.rhino.extension.NumberExtensions.jsString
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Turns node attributes, as displayed by the node info view (attribute name + formatted value),
 * into selector source: one call per attribute (`text('OK')`), a chained selector
 * (`id('x').text('y')`) or an object selector (`{ id: 'x', bounds: [l, t, r, b] }`) in the form
 * accepted by `pickup()` / `detect()`, where an array value is spread into the selector arguments.
 * zh-CN: 把节点信息视图显示的属性 (属性名 + 格式化的值) 转为选择器源码: 单个调用 (`text('OK')`), 链式选择器 (`id('x').text('y')`)
 * 或 `pickup()` / `detect()` 接受的对象选择器 (`{ id: 'x', bounds: [l, t, r, b] }`, 数组值会展开为选择器参数).
 *
 * Created by SuperMonster003 on Sep 14, 2026.
 */
object NodeSelectorSnippets {

    private val WIDGET_PREFIX_REGEX = "^android\\.widget\\.".toRegex()
    private val NON_BOUNDS_CHARS_REGEX = "[^\\d,-]".toRegex()
    private val NON_NUMBER_CHARS_REGEX = "[^\\d.-]+".toRegex()

    /** A single-quoted JS string literal. zh-CN: 单引号 JS 字符串字面量. */
    fun quote(s: String) = "'" + s
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\r", "\\r")
        .replace("\n", "\\n") + "'"

    /** `id('x').text('y')` for the given (name, value) pairs. zh-CN: 由 (属性名, 值) 生成 `id('x').text('y')`. */
    fun chainedSelector(attributes: List<Pair<String, String>>) = attributes.joinToString(".") { (name, value) -> selectorCall(name, value) }

    /** `{\n    id: 'x',\n}` for the given (name, value) pairs, `{}` when empty. zh-CN: 由 (属性名, 值) 生成对象选择器, 为空时给出 `{}`. */
    fun objectSelector(attributes: List<Pair<String, String>>): String {
        val entries = attributes.flatMap { (name, value) -> objectEntries(name, value) }
        if (entries.isEmpty()) return "{}"
        return entries.joinToString(separator = "", prefix = "{\n", postfix = "}") { (key, value) -> "    $key: $value,\n" }
    }

    /** One attribute as a selector call, e.g. `text('OK')` or `bounds(0, 0, 10, 10)`. zh-CN: 单个属性的选择器调用, 如 `text('OK')` 或 `bounds(0, 0, 10, 10)`. */
    fun selectorCall(name: String, value: String): String = when (name) {
        "className" -> "className(${quote(simplifyClassName(value))})"
        "actionNames" -> "action(${actionList(value).joinToString(", ") { quote(it) }})"
        "bounds" -> "bounds(${boundsArguments(value)})"
        "center" -> parseCenter(value).let { (x, y) -> "centerX(${rangeArguments(x)}).centerY(${rangeArguments(y)})" }
        else -> if (isStringAttribute(name)) "$name(${quote(value)})" else "$name($value)"
    }

    /** One attribute as object selector entries; the center becomes `centerX` / `centerY`. zh-CN: 单个属性的对象选择器条目; 中心点拆为 `centerX` / `centerY`. */
    fun objectEntries(name: String, value: String): List<Pair<String, String>> = when (name) {
        "className" -> listOf("className" to quote(simplifyClassName(value)))
        "actionNames" -> listOf("action" to actionList(value).joinToString(", ", "[", "]") { quote(it) })
        "bounds" -> listOf("bounds" to "[${boundsArguments(value)}]")
        "center" -> parseCenter(value).let { (x, y) -> listOf("centerX" to rangeEntry(x), "centerY" to rangeEntry(y)) }
        else -> listOf(name to if (isStringAttribute(name)) quote(value) else value)
    }

    private fun isStringAttribute(name: String) = NodeInfo::class.java.getDeclaredField(name).type == String::class.java

    private fun simplifyClassName(value: String) = value.replace(WIDGET_PREFIX_REGEX, "")

    private fun actionList(value: String) = value.split("\n").filter { it.isNotEmpty() }

    private fun boundsArguments(value: String) = value.replace(NON_BOUNDS_CHARS_REGEX, "").replace(",", ", ")

    private fun parseCenter(value: String): Pair<Double, Double> {
        val (x, y) = value.split(Regex(",\\s*")).map { it.replace(NON_NUMBER_CHARS_REGEX, "").toDouble() }
        return x to y
    }

    /** `12` for a whole number, `11, 12` (floor, ceil) for a fractional one. zh-CN: 整数给出 `12`, 小数给出 `11, 12` (下取整, 上取整). */
    private fun rangeArguments(v: Double) = when (v) {
        v.toLong().toDouble() -> v.toLong().toString()
        else -> "${floor(v).jsString}, ${ceil(v).jsString}"
    }

    private fun rangeEntry(v: Double) = when (v) {
        v.toLong().toDouble() -> v.toLong().toString()
        else -> "[${floor(v).jsString}, ${ceil(v).jsString}]"
    }

}
