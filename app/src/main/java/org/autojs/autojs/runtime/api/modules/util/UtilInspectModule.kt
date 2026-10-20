package org.autojs.autojs.runtime.api.modules.util

import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.rhino.ProxyObject
import org.autojs.autojs.rhino.ProxyObject.Companion.AUGMENTED_CUSTOM_TO_STRING_KEY
import org.autojs.autojs.rhino.ProxyObject.Companion.AUGMENTED_OBJECT_KEY
import org.autojs.autojs.runtime.api.StringReadable
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeDate
import org.mozilla.javascript.NativeError
import org.mozilla.javascript.NativeJavaClass
import org.mozilla.javascript.NativeJavaMethod
import org.mozilla.javascript.NativeJavaPackage
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Symbol
import org.mozilla.javascript.TopLevel
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.regexp.NativeRegExp
import java.util.Collections
import java.util.IdentityHashMap
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * `util.inspect` on the new script module layer (roadmap P2.3): `inspect(value, options?)` or the legacy positional form
 * `inspect(value, showHidden?, depth?, colors?)` (both accepted, options override the positional values, as the
 * obsolete module did); `inspect()` is "undefined". Options: `showHidden` (false), `depth` (2; null or Infinity for no
 * limit), `colors` (false), `customInspect` (false, documented), `maxArrayItems` / `maxObjectKeys` (10000) and
 * `maxStringLength` (100000), the last three off when not positive.
 *
 * The output is the legacy Node format of the obsolete module (ROADMAP appendix E): strings in single quotes with `'`
 * escaped and `"` kept, keys that are no identifiers in single quotes, a line break before a nested multi-line value,
 * `[Function]` / `[Function name()]`, `[Error: message]` (with own properties `{ [Error: e] code: 1 }`), RegExps and
 * dates followed by their own properties, boxed Java values as `[JavaObject: 7]` and Java arrays as `Arrays.toString`. Kept
 * from the current module: Java classes and collections, StringReadable objects, module objects with a functional
 * `toString` (not treated as custom), the head / tail summary of arrays longer than `maxArrayItems`, and the string limit.
 * A custom `toString` is only used for plain objects (class Object), as in the obsolete module.
 *
 * zh-CN: 新脚本模块层上的 util.inspect (P2.3): inspect(value, options?) 或旧的位置参数形式 inspect(value, showHidden?, depth?, colors?) (两者都接受,
 * 选项覆盖位置参数, 同旧模块); inspect() 为 "undefined". 输出为旧模块的 Node 旧版格式 (附录 E), 保留现有模块的 Java 类与集合, StringReadable 对象,
 * 函数式 toString 的模块对象, 超长数组的首尾摘要与字符串上限. 自定义 toString 只用于普通对象 (类为 Object), 同旧模块.
 */
@ScriptModule(name = "inspect", parent = UtilModule::class, dollarPrefix = false)
class UtilInspectModule {

    @ScriptInvoke
    fun inspect(cx: Context, scope: VarScope, value: Any? = Undefined.instance, showHiddenOrOptions: Any? = null, depth: Any? = null, colors: Any? = null): String =
        Inspector.of(cx, scope, showHiddenOrOptions, depth, colors).format(value)

    /** SGR codes `[start, end]` by style name. */
    @ScriptGetter
    val colors: Map<String, List<Int>>
        get() = Inspector.COLORS

    /** Color names by value type. */
    @ScriptGetter
    val styles: Map<String, String>
        get() = Inspector.STYLES

}

/** One formatting pass of `util.inspect`, also used by `util.format`. */
internal class Inspector private constructor(
    private val cx: Context,
    private val scope: VarScope,
    private val showHidden: Boolean,
    /** Levels still allowed below the value, null for no limit. */
    private val depth: Double?,
    private val colors: Boolean,
    private val customInspect: Boolean,
    private val maxArrayItems: Int,
    private val maxObjectKeys: Int,
    private val maxStringLength: Int,
) {

    private val seen: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())
    private val topLevel: TopLevel = ScriptableObject.getTopLevelScope(scope)
    private val objectCtor: Scriptable by lazy { TopLevel.getBuiltinCtor(cx, scope, TopLevel.Builtins.Object) }

    fun format(value: Any?): String = formatValue(value, depth)

    private fun formatValue(input: Any?, recurseTimes: Double?): String {
        var value = input

        if (value is Wrapper) {
            formatJava(value.unwrap(), value)?.let { return it }
            value = value.unwrap()
        } else if (value != null && value !is Scriptable && !isJsString(value) && value !is Number && value !is Boolean && !Undefined.isUndefined(value)) {
            formatJava(value, value)?.let { return it }
        }

        if (value is TopLevel) return "[object ${value.globalThis.className}]"
        if (value is StringReadable) return value.toStringReadable()

        if (value is Scriptable && value !is NativeJavaPackage) {
            runCatching { ScriptableObject.getProperty(value, StringReadable.KEY) }.getOrNull()?.let { f ->
                if (f is Callable) return formatValue(RhinoScriptRuntime.toString(f.call(cx, scope, value, emptyArray())), recurseTimes)
            }
        }

        // A hook for the value's own inspect(recurseTimes, context) when customInspect is on; util.inspect itself and
        // prototype objects are left out, as in the obsolete module. A string result is used as it is.
        if (customInspect && value is Scriptable && value !is NativeJavaPackage && value !is NativeJavaClass && RhinoScriptRuntime.`typeof`(value) == "object") {
            val f = ScriptableObject.getProperty(value, "inspect")
            if (f is Callable && !isUtilInspect(f) && !isPrototypeObject(value)) {
                val result = f.call(cx, scope, value, arrayOf(recurseTimes ?: null, contextObject()))
                return if (result is CharSequence) result.toString() else formatValue(result, recurseTimes)
            }
        }

        formatPrimitive(value)?.let { return it }
        if (value == null) return stylize("null", "null")

        when (value) {
            is NativeJavaPackage, is NativeJavaClass -> return value.toString()
            !is ScriptableObject -> return RhinoScriptRuntime.toString(value)
            is ProxyObject -> return formatValue(value[AUGMENTED_OBJECT_KEY], recurseTimes)
        }
        value as ScriptableObject

        if (value.className == "Object") customToString(value)?.let { return it }

        val array = value is NativeArray
        val keys: List<Any> = when {
            value is NativeArray -> (if (showHidden) value.allIds else value.ids).toList()
            showHidden -> ownNames(value, "getOwnPropertyNames")
            else -> ownNames(value, "keys")
        }
        val visible: Set<String>? = if (array) null else if (showHidden) ownNames(value, "keys").mapTo(HashSet()) { RhinoScriptRuntime.toString(it) } else null

        if (isError(value) && keys.any { it == "message" || it == "description" }) return errorName(value)

        if (keys.isEmpty()) {
            when (value) {
                is Callable -> return stylize(functionName(value), "special")
                is NativeRegExp -> return stylize(regExpName(value), "regexp")
                is NativeDate -> return stylize(RhinoScriptRuntime.toString(callBuiltIn(TopLevel.Builtins.Date, "toString", value)), "date")
                is NativeError -> return errorName(value)
            }
        }

        var base = ""
        val braces = if (array) "[" to "]" else "{" to "}"
        when {
            value is Callable -> base = " " + functionName(value)
            value is NativeRegExp -> base = " " + regExpName(value)
            value is NativeDate -> base = " " + RhinoScriptRuntime.toString(callBuiltIn(TopLevel.Builtins.Date, "toUTCString", value))
            value is NativeError -> base = " " + errorName(value)
        }

        if (keys.isEmpty() && (!array || (value as NativeArray).length == 0L)) return braces.first + base + braces.second

        if (recurseTimes != null && recurseTimes < 0) {
            return if (value is NativeRegExp) stylize(regExpName(value), "regexp") else stylize("[Object]", "special")
        }

        seen.add(value)
        val output: List<String> = if (array) {
            formatArray(value as NativeArray, recurseTimes, keys)
        } else {
            try {
                val limit = if (maxObjectKeys > 0) maxObjectKeys else Int.MAX_VALUE
                keys.asSequence().take(limit).mapNotNull { key -> formatProperty(value, recurseTimes, visible, RhinoScriptRuntime.toString(key), false) }.toList()
            } catch (e: Exception) {
                seen.remove(value)
                return runCatching { RhinoScriptRuntime.toString(value) }.getOrElse { RhinoScriptRuntime.toString(callBuiltIn(TopLevel.Builtins.Object, "toString", value)) }
            }
        }
        seen.remove(value)
        return reduceToSingleString(output, base, braces)
    }

    /**
     * Java values: boxed Java primitives and strings as `[JavaObject: value]` (they differ from JS values under typeof and
     * ===), Java arrays as `java.util.Arrays.toString`, UiObjectCollection by its toString; null for other Java objects.
     */
    private fun formatJava(javaValue: Any?, wrapper: Any?): String? = when (javaValue) {
        is Boolean, is Char, is String -> "[JavaObject: $javaValue]"
        is Double, is Float, is Int, is Long, is Short -> "[JavaObject: ${RhinoScriptRuntime.numberToString((javaValue as Number).toDouble(), 10)}]"
        is UiObjectCollection -> RhinoScriptRuntime.toString(wrapper)
        is IntArray -> java.util.Arrays.toString(javaValue)
        is LongArray -> java.util.Arrays.toString(javaValue)
        is ShortArray -> java.util.Arrays.toString(javaValue)
        is ByteArray -> java.util.Arrays.toString(javaValue)
        is CharArray -> java.util.Arrays.toString(javaValue)
        is FloatArray -> java.util.Arrays.toString(javaValue)
        is DoubleArray -> java.util.Arrays.toString(javaValue)
        is BooleanArray -> java.util.Arrays.toString(javaValue)
        is Array<*> -> java.util.Arrays.toString(javaValue)
        else -> null
    }

    private fun formatPrimitive(value: Any?): String? = when {
        Undefined.isUndefined(value) -> stylize("undefined", "undefined")
        isJsString(value) -> {
            val quoted = "'" + jsonString(value.toString()).removeSurrounding("\"").replace("'", "\\'").replace("\\\"", "\"") + "'"
            if (maxStringLength > 0 && quoted.length > maxStringLength) {
                val cut = quoted.substring(0, maxStringLength)
                stylize("$cut... (${quoted.length - cut.length} more chars)", "string")
            } else {
                stylize(quoted, "string")
            }
        }
        value is Number -> stylize(RhinoScriptRuntime.toString(value), "number")
        value is Boolean -> stylize(value.toString(), "boolean")
        else -> null
    }

    /** A JS string value (Java strings are JS strings with the engine's wrap factory); other CharSequences are Java objects. */
    private fun isJsString(value: Any?) = value is String || value is ConsString

    /**
     * A custom `toString` of a plain object: the first own `toString` up the prototype chain, unless it is the standard
     * one of Object / Array / Function, the Java `Object#toString`, or a module's functional `toString` (marked with
     * [AUGMENTED_CUSTOM_TO_STRING_KEY]).
     */
    private fun customToString(value: ScriptableObject): String? {
        var owner: Scriptable? = value
        while (owner != null && !hasOwn(owner, "toString")) owner = owner.prototype
        if (owner == null || hasOwn(owner, AUGMENTED_CUSTOM_TO_STRING_KEY)) return null
        val f = owner.get("toString", owner)
        if (f !is Callable || isJavaObjectToString(f)) return null
        val standard = listOf(ScriptableObject.getObjectPrototype(scope), ScriptableObject.getArrayPrototype(scope), ScriptableObject.getFunctionPrototype(scope))
        if (standard.any { it != null && f === ScriptableObject.getProperty(it, "toString") }) return null
        return RhinoScriptRuntime.toString(f.call(cx, scope, value, emptyArray()))
    }

    private fun hasOwn(o: Scriptable, name: String): Boolean = when (o) {
        is ScriptableObject -> o.has(name, o)
        else -> runCatching { o.has(name, o) }.getOrDefault(false)
    }

    private fun isJavaObjectToString(f: Any): Boolean = f is NativeJavaMethod && f.methods.any { box ->
        val m = box.asMethod()
        m.name == "toString" && m.parameterCount == 0 && m.declaringClass == Any::class.java
    }

    /** Whether [f] is `util.inspect` itself (an invocable module object with the `colors` and `styles` getters). */
    private fun isUtilInspect(f: Callable): Boolean = f is BaseFunction && f.functionName == "inspect" && f.has("colors", f) && f.has("styles", f)

    private fun isPrototypeObject(value: Scriptable): Boolean {
        val ctor = ScriptableObject.getProperty(value, "constructor")
        return ctor is Scriptable && ScriptableObject.getProperty(ctor, "prototype") === value
    }

    /** The context object passed to a custom inspect function. */
    private fun contextObject(): Scriptable = cx.newObject(scope).also {
        it.put("showHidden", it, showHidden)
        it.put("depth", it, depth)
        it.put("colors", it, colors)
        it.put("customInspect", it, customInspect)
    }

    private fun isError(value: Scriptable): Boolean = value is NativeError || value.className == "Error" || errorPrototype?.let { RhinoScriptRuntime.jsDelegatesTo(value, it) } == true

    private val errorPrototype: Scriptable? by lazy { TopLevel.getBuiltinPrototype(topLevel, TopLevel.Builtins.Error) }

    private fun errorName(value: Scriptable) = "[${RhinoScriptRuntime.toString(callBuiltIn(TopLevel.Builtins.Error, "toString", value))}]"

    private fun regExpName(value: Scriptable) = RhinoScriptRuntime.toString(callBuiltIn(TopLevel.Builtins.RegExp, "toString", value))

    private fun functionName(value: Scriptable): String {
        val name = ScriptableObject.getProperty(value, "name")
        val text = if (name === Scriptable.NOT_FOUND || name == null || Undefined.isUndefined(name)) "" else RhinoScriptRuntime.toString(name)
        return if (text.isEmpty()) "[Function]" else "[Function $text()]"
    }

    private fun callBuiltIn(type: TopLevel.Builtins, name: String, thisObj: Scriptable): Any? {
        val f = ScriptableObject.getProperty(TopLevel.getBuiltinPrototype(topLevel, type), name) as Callable
        return f.call(cx, scope, thisObj, emptyArray())
    }

    private fun ownNames(value: Scriptable, method: String): List<Any> {
        val names = ScriptableObject.callMethod(cx, objectCtor, method, arrayOf(value))
        return (names as NativeArray).let { a -> List(a.length.toInt()) { i -> a.get(i, a) } }
    }

    private fun descriptor(value: Scriptable, key: String): Scriptable? =
        ScriptableObject.callMethod(cx, objectCtor, "getOwnPropertyDescriptor", arrayOf(value, key)) as? Scriptable

    private fun jsonString(text: String): String = RhinoScriptRuntime.toString(NativeJSON.stringify(cx, scope, text, null, null))

    /** Up to [maxArrayItems] elements; a longer array shows its first 100 (at most) and last 20, then other keys. */
    private fun formatArray(value: NativeArray, recurseTimes: Double?, keys: List<Any>): List<String> {
        val output = ArrayList<String>()
        val len = minOf(value.length, Int.MAX_VALUE.toLong()).toInt()
        val max = if (maxArrayItems > 0) maxArrayItems else Int.MAX_VALUE
        val head = if (len <= max) len else minOf(max, 100)
        val tail = if (len <= max) 0 else minOf(20, max - head).coerceAtLeast(0)
        for (i in 0 until head) {
            output += if (value.has(i, value)) formatProperty(value, recurseTimes, null, i.toString(), true).orEmpty() else ""
        }
        if (len > head + tail) output += stylize("... ${len - head - tail} more items ...", "special")
        for (i in maxOf(head, len - tail) until len) {
            output += if (value.has(i, value)) formatProperty(value, recurseTimes, null, i.toString(), true).orEmpty() else ""
        }
        keys.forEach { key ->
            if (key is Symbol || (key is Int && key >= 0)) return@forEach
            val name = RhinoScriptRuntime.toString(key)
            if (!DIGITS.matches(name)) formatProperty(value, recurseTimes, null, name, true)?.let(output::add)
        }
        return output
    }

    private fun formatProperty(value: Scriptable, recurseTimes: Double?, visible: Set<String>?, key: String, array: Boolean): String? {
        val desc = descriptor(value, key) ?: return null
        val getter = ScriptableObject.getProperty(desc, "get")
        val setter = ScriptableObject.getProperty(desc, "set")
        val hasGetter = getter is Callable
        val hasSetter = setter is Callable
        var str = when {
            hasGetter && hasSetter -> stylize("[Getter/Setter]", "special")
            hasGetter -> stylize("[Getter]", "special")
            hasSetter -> stylize("[Setter]", "special")
            else -> null
        }
        val enumerable = visible?.contains(key) ?: RhinoScriptRuntime.toBoolean(ScriptableObject.getProperty(desc, "enumerable"))
        var name: String? = if (enumerable) null else "[$key]"
        if (str == null) {
            val v = ScriptableObject.getProperty(desc, "value").let { if (it === Scriptable.NOT_FOUND) Undefined.instance else it }
            str = if (v is Scriptable && v in seen) {
                stylize("[Circular]", "special")
            } else {
                var s = formatValue(v, recurseTimes?.let { it - 1 })
                if ('\n' in s) {
                    s = if (array) s.split("\n").joinToString("\n") { "  $it" }.substring(2) else "\n" + s.split("\n").joinToString("\n") { "   $it" }
                }
                s
            }
        }
        if (name == null) {
            if (array && DIGITS.matches(key)) return str
            val json = jsonString(key)
            name = if (IDENTIFIER.matches(json)) {
                stylize(json.substring(1, json.length - 1), "name")
            } else {
                stylize(json.replace("'", "\\'").replace("\\\"", "\"").replace(QUOTE_ENDS, "'"), "string")
            }
        }
        return "$name: $str"
    }

    private fun reduceToSingleString(output: List<String>, base: String, braces: Pair<String, String>): String {
        val length = output.sumOf { SGR.replace(it, "").length + 1 }
        return if (length > 60) {
            braces.first + (if (base.isEmpty()) "" else "$base\n ") + " " + output.joinToString(",\n  ") + " " + braces.second
        } else {
            braces.first + base + " " + output.joinToString(", ") + " " + braces.second
        }
    }

    private fun stylize(text: String, style: String): String {
        if (!colors) return text
        val codes = STYLES[style]?.let { COLORS[it] } ?: return text
        return "$ESC[${codes[0]}m$text$ESC[${codes[1]}m"
    }

    companion object {

        private val DIGITS = Regex("\\d+")
        private val IDENTIFIER = Regex("\"[a-zA-Z_]\\w*\"")
        private val QUOTE_ENDS = Regex("^\"|\"$")
        /** The escape character of SGR sequences. */
        private val ESC: Char = 27.toChar()
        private val SGR = Regex(ESC + "\\[\\d+?m")

        /** SGR codes of the obsolete module: [start, end]. */
        val COLORS: Map<String, List<Int>> = linkedMapOf(
            "bold" to listOf(1, 22),
            "italic" to listOf(3, 23),
            "underline" to listOf(4, 24),
            "inverse" to listOf(7, 27),
            "white" to listOf(37, 39),
            "gray" to listOf(90, 39),
            "grey" to listOf(90, 39),
            "black" to listOf(30, 39),
            "blue" to listOf(34, 39),
            "cyan" to listOf(36, 39),
            "green" to listOf(32, 39),
            "magenta" to listOf(35, 39),
            "red" to listOf(31, 39),
            "yellow" to listOf(33, 39),
        )

        /** Avoid "blue" for printing on platforms such as cmd.exe on Windows. */
        val STYLES: Map<String, String> = linkedMapOf(
            "regexp" to "red",
            "date" to "magenta",
            "special" to "cyan",
            "number" to "yellow",
            "boolean" to "yellow",
            "string" to "green",
            "undefined" to "gray",
            "null" to "bold",
        )

        /** Default options (documented): depth 2, no colors, no custom inspect, 10000 array items / keys, 100000 characters. */
        fun of(cx: Context, scope: VarScope): Inspector = Inspector(cx, scope, false, 2.0, false, false, 10_000, 10_000, 100_000)

        /**
         * The options of `inspect(value, showHiddenOrOptions?, depth?, colors?)`: positional values first, then an options
         * object overriding them. Wrong types are TypeErrors (the current module rejected anything but an options object).
         */
        fun of(cx: Context, scope: VarScope, showHiddenOrOptions: Any?, depth: Any?, colors: Any?): Inspector {
            val function = "util.inspect"
            var showHidden = false
            var levels: Double? = 2.0
            var withColors = false
            var customInspect = false
            var maxArrayItems = 10_000
            var maxObjectKeys = 10_000
            var maxStringLength = 100_000
            when (val v = unwrap(depth)) {
                null, Undefined.instance -> Unit
                is Number -> levels = v.toDouble()
                else -> throw UtilValues.typeError(function, "depth", "number", depth)
            }
            when (val v = unwrap(colors)) {
                null, Undefined.instance -> Unit
                is Boolean -> withColors = v
                else -> throw UtilValues.typeError(function, "colors", "boolean", colors)
            }
            when (val v = unwrap(showHiddenOrOptions)) {
                null, Undefined.instance -> Unit
                is Boolean -> showHidden = v
                is NativeObject -> {
                    fun option(key: String): Any? = ScriptableObject.getProperty(v, key).takeUnless { it === Scriptable.NOT_FOUND || Undefined.isUndefined(it) }
                    fun number(key: String): Double? = option(key)?.let { x ->
                        RhinoScriptRuntime.toNumber(x).also { if (it.isNaN()) throw UtilValues.typeError(function, "options.$key", "number", x) }
                    }
                    option("showHidden")?.let { showHidden = RhinoScriptRuntime.toBoolean(it) }
                    if (ScriptableObject.hasProperty(v, "depth")) {
                        // Node semantics: null (and Infinity) means no limit, undefined the default.
                        when (val x = option("depth")) {
                            null -> if (ScriptableObject.getProperty(v, "depth") == null) levels = null
                            else -> levels = number("depth")
                        }
                    }
                    option("colors")?.let { withColors = RhinoScriptRuntime.toBoolean(it) }
                    option("customInspect")?.let { customInspect = RhinoScriptRuntime.toBoolean(it) }
                    number("maxArrayItems")?.let { maxArrayItems = clampInt(it) }
                    number("maxObjectKeys")?.let { maxObjectKeys = clampInt(it) }
                    number("maxStringLength")?.let { maxStringLength = clampInt(it) }
                }
                else -> throw UtilValues.typeError(function, "showHiddenOrOptions", "boolean | Object", showHiddenOrOptions)
            }
            if (levels?.isInfinite() == true && levels > 0) levels = null
            return Inspector(cx, scope, showHidden, levels, withColors, customInspect, maxArrayItems, maxObjectKeys, maxStringLength)
        }

        private fun unwrap(value: Any?): Any? = if (value is Wrapper && value !is ScriptableObject) value.unwrap() else value

        private fun clampInt(x: Double): Int = when {
            x >= Int.MAX_VALUE -> Int.MAX_VALUE
            x <= Int.MIN_VALUE -> Int.MIN_VALUE
            else -> x.toInt()
        }

    }

}
