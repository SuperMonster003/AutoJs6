package org.autojs.autojs.runtime.api.modules.auto

import org.autojs.autojs.core.accessibility.window.WindowMatch
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.diagnostics.DumpFormat
import org.autojs.autojs.core.automator.diagnostics.NodeDump
import org.autojs.autojs.core.automator.pick.PickArguments
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.api.modules.automator.DumpArguments
import org.autojs.autojs.runtime.api.modules.automator.WindowMatchArguments
import org.autojs.autojs.runtime.api.modules.flow.FlowOptions
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.regexp.NativeRegExp
import kotlin.math.floor

/**
 * The checks of the fields of the options of `auto` on the new script module layer (roadmap P3.3, appendix C Q66 (2)):
 * the event options of `registerEvent` / `registerEvents`, the window filter of `setWindowFilter`, `findWindows` and
 * `findWindowRoots`, and the options of `dump`, made before the current parsers (`Auto.parseEventOptions`,
 * `WindowMatchArguments`, `DumpArguments`) read the same object again. What they accept is accepted here, read the way
 * they read it; what they refused is a TypeError (an unknown key, a value of another type) or a RangeError (an unknown
 * name, a negative time, a number that is NaN or no integer, a property or pattern they refuse), D10.
 *
 * zh-CN: 新脚本模块层上 auto 选项字段的检查 (P3.3, 附录 C Q66 (2)): registerEvent / registerEvents 的事件选项, setWindowFilter, findWindows 与 findWindowRoots
 * 的窗口过滤器, dump 的选项, 在现有解析器 (Auto.parseEventOptions, WindowMatchArguments, DumpArguments) 再次读取同一对象之前进行. 它们接受的这里同样接受,
 * 读取方式相同; 它们拒绝的为 TypeError (未知的键, 其他类型) 或 RangeError (未知的名称, 负的时间, NaN 或非整数, 它们拒绝的属性名或正则), D10.
 */
internal object AutoOptions {

    private val DISTINCT_MODES = FlowOptions.Words(listOf("window", "source"))

    private val WINDOW_KEYS = FlowOptions.Words(WindowMatchArguments.KEYS)

    private val WINDOW_TYPES = FlowOptions.Words(WindowMatch.TYPES.keys)

    private val DUMP_KEYS = FlowOptions.Words(DumpArguments.KEYS)

    private val DUMP_FORMATS = FlowOptions.Words(DumpFormat.entries.map { it.key })

    private val NODE_PROPERTIES = FlowOptions.Words(NodeDump.PROPERTIES)

    /**
     * The options of an event listener: `distinct` (a boolean, `"window"`, `"source"` or a key function), `within` and
     * `debounce` (milliseconds by JS ToNumber, not negative once truncated; NaN is 0). Other keys are not read, as before.
     */
    fun event(function: String, options: Any?) {
        if (options !is ScriptableObject) return
        val distinct = options.prop("distinct")
        when {
            isNullish(distinct) || distinct is Boolean -> Unit
            distinct is CharSequence -> if (distinct.toString() !in DISTINCT_MODES.set) {
                throw GlobalValues.rangeError(function, "options.distinct", DISTINCT_MODES.quoted, distinct)
            }
            distinct is ScriptableObject && distinct is Callable -> Unit
            else -> throw GlobalValues.typeError(function, "options.distinct", "boolean | string | function", distinct)
        }
        for (key in listOf("within", "debounce")) {
            val value = options.prop(key)
            if (!isNullish(value) && Context.toNumber(value).toLong() < 0) {
                throw GlobalValues.rangeError(function, "options.$key", "non-negative number", value)
            }
        }
    }

    /**
     * A window filter object: `type` (a name or an array of names in any spelling of `WindowMatch.typeOf`), `packageName`
     * and `title` (a string or a `RegExp`), `displayId`, `id` and `layer` (integers), `active` and `focused` (booleans).
     * The values are read as the current parser reads them (`get` without unwrapping).
     */
    fun windowFilter(function: String, param: String, filter: NativeObject) {
        filter.ids.forEach { id ->
            val key = id.toString()
            if (key !in WINDOW_KEYS.set) throw GlobalValues.cannotBe(function, "$param.$key", WINDOW_KEYS.given)
        }
        field(filter, WindowMatchArguments.KEY_TYPE)?.let { types(function, "$param.${WindowMatchArguments.KEY_TYPE}", it) }
        for (key in listOf(WindowMatchArguments.KEY_PACKAGE_NAME, WindowMatchArguments.KEY_TITLE)) {
            field(filter, key)?.let { text(function, "$param.$key", it) }
        }
        for (key in listOf(WindowMatchArguments.KEY_DISPLAY_ID, WindowMatchArguments.KEY_ID, WindowMatchArguments.KEY_LAYER)) {
            field(filter, key)?.let { integer(function, "$param.$key", it) }
        }
        for (key in listOf(WindowMatchArguments.KEY_ACTIVE, WindowMatchArguments.KEY_FOCUSED)) {
            field(filter, key)?.let { if (it !is Boolean) throw GlobalValues.typeError(function, "$param.$key", "boolean", it) }
        }
    }

    private fun field(obj: NativeObject, key: String): Any? = obj.get(key, obj).takeUnless { isNullish(it) }

    private fun types(function: String, where: String, value: Any) {
        val names: List<Any?> = if (value is NativeArray) value.toList() else listOf(value)
        names.forEachIndexed { i, name ->
            val at = if (value is NativeArray) "$where[$i]" else where
            if (name !is CharSequence) throw GlobalValues.typeError(function, at, "string", name)
            if (WindowMatch.typeOf(name.toString()) == null) throw GlobalValues.rangeError(function, at, WINDOW_TYPES.quoted, name)
        }
    }

    private fun text(function: String, where: String, value: Any) {
        when (value) {
            is CharSequence -> Unit
            is NativeRegExp -> PickArguments.patternError(function, where, value, value.toString())?.let { throw it.exception() }
            else -> throw GlobalValues.typeError(function, where, "string | RegExp", value)
        }
    }

    private fun integer(function: String, where: String, value: Any) {
        val number = (value as? Number)?.toDouble() ?: throw GlobalValues.typeError(function, where, "number", value)
        if (!number.isFinite() || number != floor(number)) throw GlobalValues.rangeError(function, where, "integer", value)
    }

    /**
     * The options of `dump`: a format name, or an object with `format`, `maxDepth`, `properties`, `visibleOnly`,
     * `maxNodes`, `indent` and `root`, read as `DumpArguments` reads them (wrapped values unwrapped, nullish fields the
     * defaults). The top-level type is checked by the module.
     */
    fun dump(function: String, options: Any?) {
        when (val value = unwrap(options)) {
            is CharSequence -> format(function, "options", value)
            is NativeObject -> dumpObject(function, value)
        }
    }

    private fun dumpObject(function: String, obj: NativeObject) {
        obj.ids.forEach { id ->
            val key = id.toString()
            if (key !in DUMP_KEYS.set) throw GlobalValues.cannotBe(function, "options.$key", DUMP_KEYS.given)
        }
        fun get(key: String): Any? = unwrap(ScriptableObject.getProperty(obj, key))
        get("format")?.let {
            if (it !is CharSequence) throw GlobalValues.typeError(function, "options.format", "string", it)
            format(function, "options.format", it)
        }
        get("maxDepth")?.let {
            if (it !is Number) throw GlobalValues.typeError(function, "options.maxDepth", "number", it)
            if (it.toDouble().isNaN()) throw GlobalValues.rangeError(function, "options.maxDepth", "number", it)
        }
        get("properties")?.let { properties(function, "options.properties", it) }
        get("visibleOnly")?.let { if (it !is Boolean) throw GlobalValues.typeError(function, "options.visibleOnly", "boolean", it) }
        get("maxNodes")?.let {
            if (it !is Number) throw GlobalValues.typeError(function, "options.maxNodes", "number", it)
            if (it.toDouble().isNaN() || it.toDouble() < 1) throw GlobalValues.rangeError(function, "options.maxNodes", "positive number", it)
        }
        get("indent")?.let { if (it !is CharSequence && it !is Number) throw GlobalValues.typeError(function, "options.indent", "string | number", it) }
        get("root")?.let { if (it !is UiObject) throw GlobalValues.typeError(function, "options.root", "UiObject", it) }
    }

    private fun format(function: String, where: String, value: CharSequence) {
        if (DUMP_FORMATS.set.none { it.equals(value.toString(), ignoreCase = true) }) throw GlobalValues.rangeError(function, where, DUMP_FORMATS.quoted, value)
    }

    private fun properties(function: String, where: String, value: Any) {
        val (names, indexed) = when (value) {
            is CharSequence -> listOf(value) to false
            is List<*> -> value to true
            else -> throw GlobalValues.typeError(function, where, "string | string[]", value)
        }
        names.forEachIndexed { i, element ->
            val at = if (indexed) "$where[$i]" else where
            val name = unwrap(element)
            if (name !is CharSequence) throw GlobalValues.typeError(function, at, "string", element)
            if (name.toString() !in NODE_PROPERTIES.set) throw GlobalValues.rangeError(function, at, NODE_PROPERTIES.quoted, name)
        }
    }

    /** A value as `DumpArguments` unwraps it: nullish is null, wrapped Java values unwrapped. */
    private fun unwrap(value: Any?): Any? = when {
        isNullish(value) -> null
        value is Wrapper -> unwrap(value.unwrap())
        else -> value
    }

}
