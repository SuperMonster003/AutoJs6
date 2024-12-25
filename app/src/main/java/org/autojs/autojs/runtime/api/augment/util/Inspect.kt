package org.autojs.autojs.runtime.api.augment.util

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.extension.AnyExtensions.isJsNonNullObject
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.ArrayExtensions.toNativeObject
import org.autojs.autojs.extension.NumberExtensions.string
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_GETTER_KEY
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_SETTER_KEY
import org.autojs.autojs.runtime.api.StringReadable
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.global.Species.isErrorRhino
import org.autojs.autojs.runtime.api.augment.global.Species.isJavaObjectRhino
import org.autojs.autojs.runtime.api.augment.global.Species.isObjectRhino
import org.autojs.autojs.runtime.api.augment.global.Species.isStringRhino
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.callPrototypeFunction
import org.autojs.autojs.util.RhinoUtils.callToStringFunction
import org.autojs.autojs.util.RhinoUtils.javaArrayToString
import org.autojs.autojs.util.RhinoUtils.js_array_isArray
import org.autojs.autojs.util.RhinoUtils.js_json_stringify
import org.autojs.autojs.util.RhinoUtils.js_object_getOwnPropertyDescriptor
import org.autojs.autojs.util.RhinoUtils.js_object_getOwnPropertyNames
import org.autojs.autojs.util.RhinoUtils.js_object_hasOwnProperty
import org.autojs.autojs.util.RhinoUtils.js_object_keys
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeDate
import org.mozilla.javascript.NativeError
import org.mozilla.javascript.NativeJavaClass
import org.mozilla.javascript.NativeJavaPackage
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.TopLevel
import org.mozilla.javascript.TopLevel.Builtins
import org.mozilla.javascript.Undefined.isUndefined
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.regexp.NativeRegExp
import java.util.function.Supplier
import kotlin.math.roundToInt

/**
 * Created by SuperMonster003 on Jun 10, 2024.
 */
@Suppress("unused")
object Inspect : Augmentable(), Invokable {

    private val mColorsNativeObject by lazy { colors.toNativeObject() }

    private val mStylesNativeObject by lazy { styles.toNativeObject() }

    override val selfAssignmentGetters = listOf<Pair<String, Supplier<Any?>>>(
        "colors" to Supplier { mColorsNativeObject },
        "styles" to Supplier { mStylesNativeObject },
    )

    override fun invoke(vararg args: Any?): String = ensureArgumentsLengthInRange(args, 1..2) {
        val (obj, options) = it
        inspectRhino(obj, options)
    }

    /**
     * SGR (Select Graphic Rendition) parameters
     * @see <a href="https://en.wikipedia.org/wiki/ANSI_escape_code#SGR_(Select_Graphic_Rendition)_parameters">"ANSI escape code" on Wikipedia (en)</a>
     */
    @Suppress("SpellCheckingInspection")
    private val srg = object {
        val fg = object {
            val BLACK = 30
            val RED = 31
            val GREEN = 32
            val YELLOW = 33
            val BLUE = 34
            val MAGENTA = 35
            val CYAN = 36
            val WHITE = 37
            val GRAY = 90
            val BRIGHT_BLACK = 90
            val BRIGHT_RED = 91
            val BRIGHT_GREEN = 92
            val BRIGHT_YELLOW = 93
            val BRIGHT_BLUE = 94
            val BRIGHT_MAGENTA = 95
            val BRIGHT_CYAN = 96
            val BRIGHT_WHITE_ = 97
        }
        val bg = object {
            val BLACK = 40
            val RED = 41
            val GREEN = 42
            val YELLOW = 43
            val BLUE = 44
            val MAGENTA = 45
            val CYAN = 46
            val WHITE = 47
            val GRAY = 100
            val BRIGHT_BLACK = 100
            val BRIGHT_RED = 101
            val BRIGHT_GREEN = 102
            val BRIGHT_YELLOW = 103
            val BRIGHT_BLUE = 104
            val BRIGHT_MAGENTA = 105
            val BRIGHT_CYAN = 106
            val BRIGHT_WHITE_ = 107
        }
        val RESET = 0
        val BOLD = 1
        val DIM = 2
        val ITALIC = 3
        val UNDERLINE = 4
        val SLOW_BLINK = 5
        val RAPID_BLINK = 6
        val INVERT = 7
        val CONCEAL = 8
        val STRIKE = 9
        val PRIMARY_FONT = 10
        val ALTERNATIVE_FONT_1 = 11
        val ALTERNATIVE_FONT_2 = 12
        val ALTERNATIVE_FONT_3 = 13
        val ALTERNATIVE_FONT_4 = 14
        val ALTERNATIVE_FONT_5 = 15
        val ALTERNATIVE_FONT_6 = 16
        val ALTERNATIVE_FONT_7 = 17
        val ALTERNATIVE_FONT_8 = 18
        val ALTERNATIVE_FONT_9 = 19
        val FRAKTUR = 20
        val DOUBLY_UNDERLINED = 21
        val NORMAL_INTENSITY = 22
        val NEITHER_ITALIC_NOR_BLACKLETTER = 23
        val NOT_UNDERLINED = 24
        val NOT_BLINKING = 25
        val PROPORTIONAL_SPACING = 26
        val NOT_REVERSED = 27
        val REVEAL = 28
        val NOT_CROSSED_OUT = 29
        val SET_FOREGROUND_COLOR = 38
        val DEFAULT_FOREGROUND_COLOR = 39
        val SET_BACKGROUND_COLOR = 48
        val DEFAULT_BACKGROUND_COLOR = 49
        val DISABLE_PROPORTIONAL_SPACING = 50
        val FRAMED = 51
        val ENCIRCLED = 52
        val OVERLINED = 53
        val NEITHER_FRAMED_NOR_ENCIRCLED = 54
        val NOT_OVERLINED = 55
        val SET_UNDERLINE_COLOR = 58
        val DEFAULT_UNDERLINE_COLOR = 59
        val IDEOGRAM_UNDERLINE_OR_RIGHT_SIDE_LINE = 60
        val IDEOGRAM_DOUBLE_UNDERLINE = 61
        val IDEOGRAM_OVERLINE_OR_LEFT_SIDE_LINE = 62
        val IDEOGRAM_DOUBLE_OVERLINE = 63
        val IDEOGRAM_STRESS_MARKING = 64
        val NO_IDEOGRAM_ATTRIBUTES = 65
        val SUPERSCRIPT = 73
        val SUBSCRIPT = 74
        val NEITHER_SUPERSCRIPT_NOR_SUBSCRIPT = 75
    }

    /**
     * @see <a href="http://en.wikipedia.org/wiki/ANSI_escape_code#graphics">"ANSI escape code" on Wikipedia (en)</a>
     */
    private val colors = mapOf(
        "bold" to listOf(srg.BOLD, srg.NORMAL_INTENSITY),
        "italic" to listOf(srg.ITALIC, srg.NEITHER_ITALIC_NOR_BLACKLETTER),
        "underline" to listOf(srg.UNDERLINE, srg.NOT_UNDERLINED),
        "inverse" to listOf(srg.INVERT, srg.NOT_REVERSED),
        "white" to listOf(srg.fg.WHITE, srg.DEFAULT_FOREGROUND_COLOR),
        "gray" to listOf(srg.fg.GRAY, srg.DEFAULT_FOREGROUND_COLOR),
        "grey" to listOf(srg.fg.GRAY, srg.DEFAULT_FOREGROUND_COLOR),
        "black" to listOf(srg.fg.BLACK, srg.DEFAULT_FOREGROUND_COLOR),
        "blue" to listOf(srg.fg.BLUE, srg.DEFAULT_FOREGROUND_COLOR),
        "cyan" to listOf(srg.fg.CYAN, srg.DEFAULT_FOREGROUND_COLOR),
        "green" to listOf(srg.fg.GREEN, srg.DEFAULT_FOREGROUND_COLOR),
        "magenta" to listOf(srg.fg.MAGENTA, srg.DEFAULT_FOREGROUND_COLOR),
        "red" to listOf(srg.fg.RED, srg.DEFAULT_FOREGROUND_COLOR),
        "yellow" to listOf(srg.fg.YELLOW, srg.DEFAULT_FOREGROUND_COLOR),
    )

    /**
     * Avoid using 'blue' when printing on a certain platform
     * like cmd.exe on Microsoft Windows.
     */
    private val styles = mapOf(
        "regexp" to "red",
        "date" to "magenta",
        "special" to "cyan",
        "number" to "yellow",
        "boolean" to "yellow",
        "string" to "green",
        "undefined" to "gray",
        "null" to "bold",
    )

    /**
     * Echos the value of a value. Trys to print the value out
     * in the best way possible given the different types.
     *
     * Legacy params: [ obj, showHidden, depth, colors ]
     *
     * @param obj The object to print out.
     * @param options Optional options object that alters the output.
     */
    @JvmStatic
    @JvmOverloads
    @RhinoFunctionBody
    fun inspectRhino(obj: Any?, options: Any? = null): String {
        val opt = when {
            options.isJsNullish() -> Options()
            options is NativeObject -> Options().also { opt ->
                val optionShowHidden = options.prop("showHidden")
                if (!optionShowHidden.isJsNullish()) {
                    opt.showHidden = Context.toBoolean(optionShowHidden)
                }
                val optionDepth = options.prop("depth")
                if (!optionDepth.isJsNullish()) {
                    opt.depth = Context.toNumber(optionDepth).roundToInt()
                }
                val optionColors = options.prop("colors")
                if (!optionColors.isJsNullish()) {
                    opt.colors = Context.toBoolean(optionColors)
                }
            }
            else -> throw WrappedIllegalArgumentException("Argument options for util.inspect must be a JavaScript Object")
        }
        val ctx = Ctx().also {
            it.showHidden = opt.showHidden
            it.depth = opt.depth
            it.colors = opt.colors
            it.stylize = when (it.colors) {
                true -> { str, styleType ->
                    styles[styleType]?.let { style ->
                        colors[style]?.let { color ->
                            color.joinToString(str) { c -> "\u001b[${c}m" }
                        } ?: throw WrappedIllegalArgumentException("Invalid style type: $styleType")
                    } ?: str
                }
                else -> { str, _ -> str }
            }
        }
        return formatValue(ctx, obj, ctx.depth)
    }

    private fun formatValue(ctx: Ctx, `val`: Any?, recurseTimes: Int?): String {
        var value = `val`

        while (value is Wrapper) value = value.unwrap()

        if (value is TopLevel) return "[object ${value.className}]"

        if (value is StringReadable) return value.toStringReadable()

        if (value is Scriptable && value !is NativeJavaClass) runCatching {
            (value.prop(StringReadable.KEY) as? BaseFunction)?.let { f ->
                return Context.toString(callFunction(f, null, value, arrayOf()))
            }
        }

        // Provide a hook for user-specified inspect functions.
        // Check that value is an object with an inspect function on it

        // noinspection JSIncompatibleTypesComparison
        if (ctx.customInspect && value is Scriptable && value.isJsNonNullObject()) {
            val inspectFunc = value.prop("inspect")
            if (inspectFunc is BaseFunction) {
                val ret = callFunction(inspectFunc, null, value, arrayOf(recurseTimes, ctx))
                return when {
                    isStringRhino(ret) -> Context.toString(ret)
                    else -> formatValue(ctx, ret, recurseTimes)
                }
            }
        }

        if (isJavaObjectRhino(value)) {
            val v = RhinoUtils.unwrap(value)!!
            if (v.javaClass == UiObjectCollection::class.java) {
                return callToStringFunction(value as Scriptable)
            }
            if (v.javaClass.isArray) {
                return javaArrayToString(v)
            }
        }

        // Primitive types cannot have properties
        formatPrimitive(ctx, value).takeUnless { it.isJsNullish() }?.let {
            return Context.toString(it)
        }

        // @Hint by SuperMonster003 on Jun 10, 2024.
        //  ! Explicitly declare its non-null.
        //  ! zh-CN: 显式声明其非空性.
        if (value == null) throw ShouldNeverHappenException()

        if (value is NativeJavaPackage) return value.toString()
        if (value is NativeJavaClass) return value.toString()
        if (value.javaClass.isArray) return javaArrayToString(value)
        if (value !is ScriptableObject) return Context.toString(value)

        // @Hint by SuperMonster003 on Apr 16, 2024.
        //  ! Check if the object has a custom overwritten toString() method.
        //  ! zh-CN: 检查对象是否有自定义的 toString() 覆写方法.
        if (isObjectRhino(value)) {
            var tmp: Scriptable = value
            val objectPrototype by lazy { ScriptableObject.getObjectPrototype(value) }
            do {
                if (js_object_hasOwnProperty(tmp, "toString")) {
                    if ((tmp.prop("toString") as? BaseFunction)?.arity == 0) {
                        return callToStringFunction(value)
                    }
                }
                tmp = tmp.prototype ?: break
            } while (tmp != objectPrototype)
        }

        // Look up the keys of the object.
        var keys: NativeArray = js_object_keys(value)
        val visibleKeys = newNativeObject().also { o ->
            keys.forEach { o.put(Context.toString(it), o, true) }
        }

        if (ctx.showHidden) {
            keys = js_object_getOwnPropertyNames(value)
        }

        // IE doesn't make error fields non-enumerable
        // http://msdn.microsoft.com/en-us/library/ie/dww52sbt(v=vs.94).aspx
        if (isErrorRhino(value) && (keys.contains("message") || keys.contains("description"))) {
            return formatError(value)
        }

        // Some type of object without properties can be shortcut.
        if (keys.isEmpty) {
            when (value) {
                is BaseFunction -> return formatBaseFunction(ctx, value)
                is NativeRegExp -> return formatRegExp(ctx, value)
                is NativeDate -> return formatDate(ctx, value)
                is NativeError -> return formatError(value)
            }
        }

        var base = ""
        var array = false
        var braces = listOf("{", "}")

        // Make Array say that they are Array
        if (js_array_isArray(value)) {
            array = true
            braces = listOf("[", "]")
        }

        // Make functions say that they are functions
        if (value is BaseFunction) {
            base = " ${getBaseFunctionFormattedName(value)}"
        }

        // Make RegExps say that they are RegExps
        if (value is NativeRegExp) {
            base = " ${getRegExpFormattedName(value)}"
        }

        // Make dates with properties first say the date
        if (value is NativeDate) {
            base = " ${getDateFormattedName(value, true)}"
        }

        // Make error with a message first say the error
        if (value is NativeError) {
            base = " ${getErrorFormattedName(value)}"
        }

        if (keys.isEmpty && (!array || (value as NativeArray).isEmpty)) {
            return "${braces[0]}$base${braces[1]}"
        }

        if (recurseTimes != null && recurseTimes < 0) {
            return when (value) {
                is NativeRegExp -> ctx.stylize(getRegExpFormattedName(value), "regexp")
                else -> ctx.stylize("[Object]", "special")
            }
        }

        ctx.seen.add(value)

        val output: List<String> = if (array) {
            formatArray(ctx, value as NativeArray, recurseTimes, visibleKeys, keys)
        } else {
            try {
                keys.map { key -> formatProperty(ctx, value, recurseTimes, visibleKeys, Context.toString(key), false) }
            } catch (err: Exception) {
                if (isJavaObjectRhino(value)) {
                    RhinoUtils.unwrap(value).let {
                        if (it != value) {
                            return toJavaObjectString(it, it)
                        }
                    }
                    return toJavaObjectString(value)
                }
                return try {
                    Context.toString(value).ifEmpty { "" }
                } catch (e: Exception) {
                    Context.toString(callPrototypeFunction(Builtins.Object, "toString", value))
                }
            }
        }

        ctx.seen.removeLastOrNull()

        return reduceToSingleString(output, base, braces)
    }

    private fun toJavaObjectString(value: Any) = "[JavaObject: ${value.javaClass.name}@${Integer.toHexString(value.hashCode())}]"

    private fun toJavaObjectString(className: Any?, value: Any?) = "[JavaObject: $className@${Integer.toHexString(value.hashCode())}]"

    private fun formatArray(ctx: Ctx, value: NativeArray, recurseTimes: Int?, visibleKeys: NativeObject, keys: NativeArray): List<String> {
        val output = mutableListOf<String>()
        for (i in 0 until value.length) {
            val key = Context.toString(i)
            output += when (js_object_hasOwnProperty(value, key)) {
                true -> formatProperty(ctx, value, recurseTimes, visibleKeys, key, true)
                else -> ""
            }
        }
        keys.forEach { key ->
            val niceKey = Context.toString(key)
            if (!niceKey.matches(Regex("\\d+"))) {
                output.add(formatProperty(ctx, value, recurseTimes, visibleKeys, niceKey, true))
            }
        }
        return output
    }

    private fun formatProperty(ctx: Ctx, value: ScriptableObject, recurseTimes: Int?, visibleKeys: NativeObject, key: String, array: Boolean): String {
        var name: String? = null
        val desc = js_object_getOwnPropertyDescriptor(value, key) ?: newNativeObject().apply {
            put("value", this, value.prop(key))
        }
        val getter = desc.prop(PROXY_GETTER_KEY)
        val setter = desc.prop(PROXY_SETTER_KEY)

        var str: String = when {
            !getter.isJsNullish() -> {
                when {
                    !setter.isJsNullish() -> ctx.stylize("[Getter/Setter]", "special")
                    else -> ctx.stylize("[Getter]", "special")
                }
            }
            !setter.isJsNullish() -> ctx.stylize("[Setter]", "special")
            else -> ""
        }

        if (!js_object_keys(visibleKeys).contains(key)) {
            name = "[$key]"
        }

        if (str.isEmpty()) {
            if (ctx.seen.indexOf(desc.prop("value")) < 0) {
                str = if (recurseTimes == null) {
                    formatValue(ctx, desc.prop("value"), null)
                } else {
                    formatValue(ctx, desc.prop("value"), recurseTimes - 1)
                }
                if (str.contains("\n")) {
                    str = if (array) {
                        str.split("\n").joinToString("\n") { "  $it" }.substring(2)
                    } else {
                        str.split("\n").joinToString("\n") { "   $it" }
                    }
                }
            } else {
                str = ctx.stylize("[Circular]", "special")
            }
        }
        if (name.isJsNullish()) {
            if (array && key.matches(Regex("\\d+"))) {
                return str
            }
            name = Context.toString(js_json_stringify(key))
            name = if (Regex("\"([a-zA-Z_]\\w*)\"").matches(name)) {
                ctx.stylize(name.drop(1).dropLast(1), "name")
            } else {
                name.replace("'", "\\'")
                    .replace("\\\"", "\"")
                    .replace(Regex("(^\"|\"$)"), "'")
                ctx.stylize(name, "string")
            }
        }

        return "$name: $str"
    }

    private fun reduceToSingleString(output: List<String>, base: String, braces: List<String>): String {
        var numLinesEst = 0
        val length = output.fold(0) { prev, cur ->
            numLinesEst++
            if (cur.contains("\n")) {
                numLinesEst++
            }
            prev + cur.replace(Regex("\u001b\\[\\d+?m"), "").length + 1
        }
        return when {
            length > 60 -> {
                "${braces[0]}${if (base.isEmpty()) "" else "$base\n "} ${output.joinToString(",\n  ")} ${braces[1]}"
            }
            else -> "${braces[0]}$base ${output.joinToString(", ")} ${braces[1]}"
        }
    }

    private fun formatPrimitive(ctx: Ctx, value: Any?): String? = when {
        isUndefined(value) -> {
            ctx.stylize("undefined", "undefined")
        }
        value == null -> {
            // For some reason, typeof null is "object", so special case here.
            ctx.stylize("null", "null")
        }
        value is String -> {
            val content = Context.toString(js_json_stringify(value))
                .removeSurrounding("\"")
                .replace(Regex("'"), "\\\'")
                .replace(Regex("\\\\\""), "'")

            // @Hint by SuperMonster003 on Jun 13, 2024.
            //  ! I don't think this would be a good idea
            //  ! to make a string quoted by single quotation marks.
            //  ! zh-CN:
            //  ! 我认为将字符串用单引号包裹起来并不是一个好主意.
            //  ! PS: 我认为不应该翻译为 "我不认为...". :)
            //  !
            //  # ctx.stylize("'$content'", "string")

            ctx.stylize(content, "string")
        }
        value is Number -> {
            ctx.stylize(value.string, "number")
        }
        value is Boolean -> {
            ctx.stylize(Context.toString(value), "boolean")
        }
        else -> null
    }

    private fun formatError(value: Scriptable): String {
        return getErrorFormattedName(value)
    }

    private fun getErrorFormattedName(value: Scriptable) = "[${callPrototypeFunction(Builtins.Error, "toString", value)}]"

    private fun formatDate(ctx: Ctx, value: Scriptable) = ctx.stylize(getDateFormattedName(value), "date")

    private fun getDateFormattedName(value: Scriptable, isUTC: Boolean = false): String {
        val funcName = when {
            isUTC -> "toUTCString"
            else -> "toString"
        }
        return Context.toString(callPrototypeFunction("Date", funcName, value))
    }

    private fun formatRegExp(ctx: Ctx, value: Scriptable) = ctx.stylize(getRegExpFormattedName(value), "regexp")

    private fun getRegExpFormattedName(value: Scriptable): String = Context.toString(callPrototypeFunction(Builtins.RegExp, "toString", value))

    private fun formatBaseFunction(ctx: Ctx, value: ScriptableObject) = ctx.stylize(getBaseFunctionFormattedName(value), "special")

    private fun getBaseFunctionFormattedName(value: ScriptableObject): String {
        val suffix = value.prop("name").takeUnless { it.isJsNullish() }?.let { " $it()" } ?: ""
        return "[Function$suffix]"
    }

    class Options {
        var colors = false
        var depth: Int = 2
        var showHidden = false
    }

    class Ctx {
        var colors = false
        var depth: Int = 2
        var showHidden = false
        var customInspect = false
        val seen = mutableListOf<ScriptableObject>()
        lateinit var stylize: (str: String, styleType: String?) -> String
    }

}
