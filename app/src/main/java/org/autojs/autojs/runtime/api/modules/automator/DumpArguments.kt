package org.autojs.autojs.runtime.api.modules.automator

import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.diagnostics.DumpFormat
import org.autojs.autojs.core.automator.diagnostics.DumpOptions
import org.autojs.autojs.core.automator.toolkit.UiNodes
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.UniqueTag
import org.mozilla.javascript.Wrapper

/**
 * The options of `auto.dump(options)` and `UiObject.dumpSubtree(options)`: nothing (defaults), a
 * format name (`"text"` / `"json"` / `"xml"`), or an object with `format`, `maxDepth` (a negative
 * number or `Infinity` = unlimited), `properties` (an array of node property names, or one name),
 * `visibleOnly`, `maxNodes`, `indent` (a string, or a number of spaces) and, for `auto.dump` only,
 * `root` (a `UiObject`; the window roots otherwise). Unknown keys are refused.
 * zh-CN: `auto.dump(options)` 与 `UiObject.dumpSubtree(options)` 的选项: 不传 (默认值), 形式名 (`"text"` / `"json"` / `"xml"`),
 * 或带 `format`, `maxDepth` (负数或 `Infinity` = 不限), `properties` (节点属性名数组, 或单个名字), `visibleOnly`, `maxNodes`,
 * `indent` (字符串, 或空格数) 以及仅 `auto.dump` 接受的 `root` (一个 `UiObject`; 否则为各窗口根) 的对象. 未知键被拒绝.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal object DumpArguments {

    val KEYS: Set<String> = setOf("root", "format", "maxDepth", "properties", "visibleOnly", "maxNodes", "indent")

    class Parsed(val options: DumpOptions, val root: UiObject?)

    fun parse(arg: Any?, allowRoot: Boolean = true): Parsed = when (val value = unwrap(arg)) {
        null -> Parsed(DumpOptions(), null)
        is CharSequence -> Parsed(DumpOptions(format = DumpFormat.parse(value.toString())), null)
        is NativeObject -> parseObject(value, allowRoot)
        else -> throw IllegalArgumentException("Dump options must be a format name or an options object, got ${brief(value)}")
    }

    private fun parseObject(obj: NativeObject, allowRoot: Boolean): Parsed {
        val accepted = if (allowRoot) KEYS else KEYS - "root"
        val keys = obj.ids.map { it.toString() }
        keys.filter { it !in accepted }.takeIf { it.isNotEmpty() }?.let { unknown ->
            throw IllegalArgumentException("Unknown dump option(s) ${unknown.joinToString(", ") { "\"$it\"" }}; expected ${accepted.joinToString(", ") { "\"$it\"" }}")
        }
        fun get(key: String): Any? = unwrap(ScriptableObject.getProperty(obj, key))
        val format = get("format")?.let { DumpFormat.parse(string(it, "format")) } ?: DumpFormat.TEXT
        val maxDepth = get("maxDepth")?.let { depth(it) } ?: DumpOptions.UNLIMITED
        val properties = get("properties")?.let { names(it) }
        val visibleOnly = get("visibleOnly")?.let { boolean(it, "visibleOnly") } ?: false
        val maxNodes = get("maxNodes")?.let { count(it, "maxNodes") } ?: UiNodes.DEFAULT_MAX_NODES
        val indent = get("indent")?.let { indent(it) } ?: "  "
        val root = get("root")?.let { it as? UiObject ?: throw IllegalArgumentException("Option \"root\" must be a UiObject, got ${brief(it)}") }
        return Parsed(DumpOptions(format, maxDepth, properties, visibleOnly, maxNodes, indent), root)
    }

    private fun unwrap(value: Any?): Any? = when (value) {
        null, is Undefined, UniqueTag.NOT_FOUND -> null
        is Wrapper -> unwrap(value.unwrap())
        else -> value
    }

    private fun string(value: Any?, key: String): String = when (value) {
        is CharSequence -> value.toString()
        else -> throw IllegalArgumentException("Option \"$key\" must be a string, got ${brief(value)}")
    }

    private fun boolean(value: Any?, key: String): Boolean = when (value) {
        is Boolean -> value
        else -> throw IllegalArgumentException("Option \"$key\" must be a boolean, got ${brief(value)}")
    }

    private fun depth(value: Any?): Int = when (value) {
        is Number -> value.toDouble().let { d ->
            when {
                d.isNaN() -> throw IllegalArgumentException("Option \"maxDepth\" must be a number, got NaN")
                d < 0 || d.isInfinite() -> DumpOptions.UNLIMITED
                else -> d.toInt()
            }
        }
        else -> throw IllegalArgumentException("Option \"maxDepth\" must be a number, got ${brief(value)}")
    }

    private fun count(value: Any?, key: String): Int = when (value) {
        is Number -> value.toDouble().let { d ->
            when {
                d.isNaN() || d < 1 -> throw IllegalArgumentException("Option \"$key\" must be a positive number, got ${brief(value)}")
                d >= Int.MAX_VALUE -> Int.MAX_VALUE
                else -> d.toInt()
            }
        }
        else -> throw IllegalArgumentException("Option \"$key\" must be a positive number, got ${brief(value)}")
    }

    private fun names(value: Any?): List<String> = when (value) {
        is CharSequence -> listOf(value.toString())
        is NativeArray -> value.map { string(unwrap(it), "properties") }
        is List<*> -> value.map { string(unwrap(it), "properties") }
        else -> throw IllegalArgumentException("Option \"properties\" must be an array of property names, got ${brief(value)}")
    }

    private fun indent(value: Any?): String = when (value) {
        is CharSequence -> value.toString()
        is Number -> " ".repeat(value.toDouble().let { if (it.isNaN() || it < 0) 0 else it.toInt().coerceAtMost(16) })
        else -> throw IllegalArgumentException("Option \"indent\" must be a string or a number of spaces, got ${brief(value)}")
    }

    private fun brief(value: Any?): String = when (value) {
        null -> "null"
        is CharSequence -> "\"$value\""
        is Scriptable -> value.className
        else -> "${value::class.java.simpleName} $value"
    }

}
