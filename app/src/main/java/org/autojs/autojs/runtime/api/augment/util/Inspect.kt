package org.autojs.autojs.runtime.api.augment.util

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.ProxyObject
import org.autojs.autojs.rhino.ProxyObject.Companion.AUGMENTED_CUSTOM_TO_STRING_KEY
import org.autojs.autojs.rhino.ProxyObject.Companion.AUGMENTED_OBJECT_KEY
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_GETTER_KEY
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_SETTER_KEY
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNonNullObject
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.MapExtensions.toNativeObject
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.api.StringReadable
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.global.Species.isErrorRhino
import org.autojs.autojs.runtime.api.augment.global.Species.isJavaObjectRhino
import org.autojs.autojs.runtime.api.augment.global.Species.isStringRhino
import org.autojs.autojs.runtime.api.augment.util.Util.isReferenceRhino
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.callPrototypeFunction
import org.autojs.autojs.util.RhinoUtils.callToStringFunction
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
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
import org.mozilla.javascript.MemberBox
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeDate
import org.mozilla.javascript.NativeError
import org.mozilla.javascript.NativeJavaClass
import org.mozilla.javascript.NativeJavaMethod
import org.mozilla.javascript.NativeJavaPackage
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.TopLevel
import org.mozilla.javascript.TopLevel.Builtins
import org.mozilla.javascript.Undefined.isUndefined
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.regexp.NativeRegExp
import java.util.*
import java.util.function.Supplier

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

    private val DIGITS_REGEX = Regex("\\d+")

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
                options.inquire("showHidden", transformer = ::coerceBoolean)?.let { opt.showHidden = it }
                options.inquire("depth", transformer = ::coerceIntNumber)?.let { opt.depth = it }
                options.inquire("colors", transformer = ::coerceBoolean)?.let { opt.colors = it }
                options.inquire("maxArrayItems", transformer = ::coerceIntNumber)?.let { opt.maxArrayItems = it }
                options.inquire("maxObjectKeys", transformer = ::coerceIntNumber)?.let { opt.maxObjectKeys = it }
                options.inquire("maxStringLength", transformer = ::coerceIntNumber)?.let { opt.maxStringLength = it }
                options.inquire("customInspect", transformer = ::coerceBoolean)?.let { opt.customInspect = it }
            }
            else -> throw WrappedIllegalArgumentException("Argument \"options\" ${options.jsBrief()} for util.inspect must be a JavaScript Object")
        }
        val ctx = Ctx().also { c ->
            opt.showHidden?.let { c.showHidden = it }
            opt.depth?.let { c.depth = it }
            opt.colors?.let { c.colors = it }
            opt.customInspect?.let { c.customInspect = it }
            opt.maxArrayItems?.let { c.maxArrayItems = it }
            opt.maxObjectKeys?.let { c.maxObjectKeys = it }
            opt.maxStringLength?.let { c.maxStringLength = it }

            // Pre-compute style cache to avoid constructing escape sequences every time during stylize.
            // zh-CN: 预计算样式缓存, 避免每次 stylize 都构造转义序列.
            if (c.colors) {
                val cache = HashMap<String, Pair<String, String>>(styles.size)
                styles.forEach { (styleType, colorName) ->
                    colors[colorName]?.let { codes ->
                        val prefix = "\u001b[${codes.first()}m"
                        val suffix = "\u001b[${codes.last()}m"
                        cache[styleType] = prefix to suffix
                    }
                }
                c.styleCache = cache
                c.stylize = { str, styleType ->
                    run {
                        val st = styleType ?: return@run str
                        val pair = c.styleCache[st] ?: return@run str
                        pair.first + str + pair.second
                    }
                }
            } else {
                c.stylize = { str, _ -> str }
            }
        }
        return formatValue(ctx, obj, ctx.depth)
    }

    private fun formatValue(ctx: Ctx, `val`: Any?, recurseTimes: Int?): String {
        var value = `val`

        while (value is Wrapper) {
            val unwrapped = value.unwrap()
            if (unwrapped is Number) {
                return callToStringFunction(value as Scriptable)
            }
            value = unwrapped
        }

        if (value is TopLevel) return "[object ${value.className}]"

        if (value is StringReadable) return value.toStringReadable()

        if (value is Scriptable && value !is NativeJavaPackage) runCatching {
            (value.prop(StringReadable.KEY) as? BaseFunction)?.let { f ->
                val value = Context.toString(callFunction(f, null, value, arrayOf()))
                return formatValue(ctx, value, recurseTimes)
            }
        }

        // Provide a hook for user-specified inspect functions.
        // Check that value is an object with an inspect function on it.
        // zh-CN:
        // 为用户指定的 inspect 函数提供一个钩子.
        // 检查 value 是否为带有 inspect 函数的对象.

        // noinspection JSIncompatibleTypesComparison
        if (ctx.customInspect && value is Scriptable && value.isJsNonNullObject()) {
            val inspectFunc = value.prop("inspect")
            if (inspectFunc is BaseFunction) {
                val ret = callFunction(inspectFunc, null, value, arrayOf(recurseTimes, ctx))
                return when {
                    isStringRhino(ret) -> formatValue(ctx, Context.toString(ret), recurseTimes)
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

        // Primitive types cannot have properties.
        // zh-CN: 原始类型不能有属性.
        formatPrimitive(ctx, value).takeUnless { it.isJsNullish() }?.let {
            // String truncation (avoid huge overhead caused by overlong strings).
            // zh-CN: 字符串截断 (避免超长字符串造成巨大开销).
            if (value is String && ctx.maxStringLength > 0 && it.length > ctx.maxStringLength) {
                val cut = it.substring(0, ctx.maxStringLength)
                return ctx.stylize("$cut... (${it.length - cut.length} more chars)", "string")
            }
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
        if (value is ProxyObject) return formatValue(ctx, value[AUGMENTED_OBJECT_KEY], recurseTimes)

        // @Hint by SuperMonster003 on Apr 16, 2024.
        //  ! Check if the object has a custom overwritten toString() method.
        //  ! zh-CN: 检查对象是否有自定义的 toString() 覆写方法.
        if (isReferenceRhino(value)) {
            var cur: Scriptable = value
            val objProtoForJs: Scriptable? = ScriptableObject.getObjectPrototype(cur)
            val arrProtoForJs: Scriptable? = ScriptableObject.getArrayPrototype(cur)
            val funcProtoForJs: Scriptable? = ScriptableObject.getFunctionPrototype(cur)
            val objProtoForAugmentable: Scriptable = RhinoUtils.objectPrototype
            val arrProtoForAugmentable: Scriptable = RhinoUtils.arrayPrototype
            val funcProtoForAugmentable: Scriptable = RhinoUtils.functionPrototype
            var owner: Scriptable? = null
            while (true) {
                if (js_object_hasOwnProperty(cur, "toString")) {
                    owner = cur
                    break
                }
                cur = cur.prototype ?: break
            }
            if (js_object_hasOwnProperty(cur, AUGMENTED_CUSTOM_TO_STRING_KEY)) {
                // Augmentable's custom toString method with special functionality (like Colors.toString)
                // is not involved in custom determination.
                // zh-CN: Augmentable 自定义的具有特殊功能的 toString 方法 (如 Colors.toString), 不参与自定义判定.
            } else if (owner != null) {
                val fn = owner["toString"]
                val isCustom = run {
                    if (isJavaObjectToString(fn)) {
                        // Standard Java Object#toString or equivalent implementation, skip custom check.
                        // zh-CN: 标准 Java Object#toString 或等价实现, 视为未自定义, 跳过自定义判定.
                        return@run false
                    }
                    if (fn === objProtoForJs?.get("toString") || fn === arrProtoForJs?.get("toString") || fn === funcProtoForJs?.get("toString")) {
                        // Standard JavaScript (Object/Array/Function).prototype.toString or equivalent implementation, skip custom check.
                        // zh-CN: 标准 JavaScript (Object/Array/Function).prototype.toString 或等价实现, 视为未自定义, 跳过自定义判定.
                        return@run false
                    }
                    val augmentableToStringList = listOf(
                        objProtoForAugmentable["toString"],
                        arrProtoForAugmentable["toString"],
                        funcProtoForAugmentable["toString"],
                    )
                    if (fn in augmentableToStringList) {
                        // Terminal toString in Augmentable prototype chain, skip custom check.
                        // zh-CN: Augmentable 原型链终端的 toString, 不参与自定义判定.
                        return@run false
                    }
                    return@run fn is BaseFunction && fn.arity == 0
                }
                if (isCustom) {
                    return callToStringFunction(value)
                }
            }
        }

        // Look up the keys of the object.
        // zh-CN: 查找对象的键.
        var keys: NativeArray = js_object_keys(value)
        val visibleKeys = newNativeObject().also { o ->
            keys.forEach { o.put(Context.toString(it), o, true) }
        }
        if (ctx.showHidden) {
            keys = js_object_getOwnPropertyNames(value)
        }

        // IE doesn't make error fields non-enumerable.
        // zh-CN: IE 不会使错误字段不可枚举.
        // @Reference to http://msdn.microsoft.com/en-us/library/ie/dww52sbt(v=vs.94).aspx
        if (isErrorRhino(value) && (keys.contains("message") || keys.contains("description"))) {
            return formatError(value)
        }

        // Some type of object without properties can be shortcut.
        // zh-CN: 某些没有属性的对象类型可以简化处理.
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

        // Make Array say that they are Array.
        // zh-CN: 让数组表明它们是数组.
        if (js_array_isArray(value)) {
            array = true
            braces = listOf("[", "]")
        }

        // Make functions say that they are functions.
        // zh-CN: 让函数表明它们是函数.
        if (value is BaseFunction) {
            base = " ${getBaseFunctionFormattedName(value)}"
        }

        // Make RegExps say that they are RegExps.
        // zh-CN: 让正则表达式表明它们是正则表达式.
        if (value is NativeRegExp) {
            base = " ${getRegExpFormattedName(value)}"
        }

        // Make dates with properties first say the date.
        // zh-CN: 让带属性的日期先说明它们是日期.
        if (value is NativeDate) {
            base = " ${getDateFormattedName(value, true)}"
        }

        // Make error with a message first say the error.
        // zh-CN: 让带消息的错误先说明它们是错误.
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

        // Identity-based collection, O(1) contains/add.
        // zh-CN: 基于身份的集合, O(1) contains/add.
        ctx.seen.add(value)

        val output: List<String> = if (array) {
            formatArray(ctx, value as NativeArray, recurseTimes, visibleKeys, keys)
        } else {
            try {
                // Object properties limit.
                // zh-CN: 对象属性上限.
                val limit = if (ctx.maxObjectKeys > 0) ctx.maxObjectKeys else Int.MAX_VALUE
                keys.asSequence()
                    .take(limit)
                    .map { key -> formatProperty(ctx, value, recurseTimes, visibleKeys, Context.toString(key), false) }
                    .toList()
            } catch (err: Exception) {
                if (isJavaObjectRhino(value)) {
                    RhinoUtils.unwrap(value).let {
                        if (it != value) {
                            return toJavaObjectString(it, it)
                        }
                    }
                    return toJavaObjectString(value)
                }
                val value = try {
                    Context.toString(value).ifEmpty { "" }
                } catch (e: Exception) {
                    Context.toString(callPrototypeFunction(Builtins.Object, "toString", value))
                }
                return formatValue(ctx, value, recurseTimes)
            }
        }

        // With Set, just remove directly.
        // zh-CN: 与 Set 搭配, 直接移除即可.
        ctx.seen.remove(value)

        return reduceToSingleString(output, base, braces)
    }

    private fun isJavaObjectToString(fn: Any?): Boolean {
        if (fn !is NativeJavaMethod) return false
        val methods: Array<out MemberBox> = fn.methods
        return methods.any { mb ->
            val m = mb.method()
            m.name == "toString" &&
                    m.parameterCount == 0 &&
                    m.returnType == String::class.java &&
                    m.declaringClass == Any::class.java
        }
    }

    private fun toJavaObjectString(value: Any) = "[JavaObject: ${value.javaClass.name}@${Integer.toHexString(value.hashCode())}]"

    private fun toJavaObjectString(className: Any?, value: Any?) = "[JavaObject: $className@${Integer.toHexString(value.hashCode())}]"

    private fun formatArray(ctx: Ctx, value: NativeArray, recurseTimes: Int?, visibleKeys: NativeObject, keys: NativeArray): List<String> {
        val output = mutableListOf<String>()

        // Array elements upper limit display (head/tail summary).
        // zh-CN: 数组元素上限显示 (head/tail 摘要).
        val len = value.length.toInt()
        val max = if (ctx.maxArrayItems > 0) ctx.maxArrayItems else Int.MAX_VALUE
        // First 100.
        // zh-CN: 前 100.
        val head = minOf(len, minOf(max, 100))
        val tail = when {
            // When exceeding, show last 20.
            // zh-CN: 多出时, 末尾展示 20.
            len > max -> minOf(20, max - head).coerceAtLeast(0)
            else -> 0
        }

        // Show head first.
        // zh-CN: 先展示 head.
        for (i in 0 until head) {
            val key = Context.toString(i)
            output += when (js_object_hasOwnProperty(value, key)) {
                true -> formatProperty(ctx, value, recurseTimes, visibleKeys, key, true)
                else -> ""
            }
        }

        // Middle summary.
        // zh-CN: 中间摘要.
        if (len > head + tail) {
            val omitted = len - head - tail
            output += ctx.stylize("... $omitted more items ...", "special")
        }

        // Then show tail.
        // zh-CN: 再展示 tail.
        for (i in (len - tail) until len) {
            if (i < head) continue
            val key = Context.toString(i)
            output += when (js_object_hasOwnProperty(value, key)) {
                true -> formatProperty(ctx, value, recurseTimes, visibleKeys, key, true)
                else -> ""
            }
        }

        // Append non-numeric keys.
        // zh-CN: 补充非数字键.
        keys.forEach { key ->
            val niceKey = Context.toString(key)
            if (!DIGITS_REGEX.matches(niceKey)) {
                output.add(formatProperty(ctx, value, recurseTimes, visibleKeys, niceKey, true))
            }
        }
        return output
    }

    private fun formatProperty(ctx: Ctx, value: ScriptableObject, recurseTimes: Int?, visibleKeys: NativeObject, key: String, array: Boolean): String {
        var name: String? = null
        val desc = js_object_getOwnPropertyDescriptor(value, key)
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
            val v = desc.prop("value")
            // Use identity Set to detect cycles, avoid O(n) indexOf.
            // zh-CN: 使用身份 Set 检测循环, 避免 O(n) indexOf.
            if (v is ScriptableObject && ctx.seen.contains(v)) {
                str = ctx.stylize("[Circular]", "special")
            } else {
                str = if (recurseTimes == null) {
                    formatValue(ctx, v, null)
                } else {
                    formatValue(ctx, v, recurseTimes - 1)
                }
                if (str.contains("\n")) {
                    str = if (array) {
                        str.split("\n").joinToString("\n") { "  $it" }.substring(2)
                    } else {
                        str.split("\n").joinToString("\n") { "   $it" }
                    }
                }
            }
        }
        if (name.isJsNullish()) {
            if (array && DIGITS_REGEX.matches(key)) {
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
            //  !
            //  # ctx.stylize("'$content'", "string")

            ctx.stylize(content, "string")
        }
        value is Number -> {
            ctx.stylize(Context.toString(value), "number")
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
        var colors: Boolean? = null
        var depth: Int? = null
        var showHidden: Boolean? = null
        var customInspect: Boolean? = null
        var maxArrayItems: Int? = null
        var maxObjectKeys: Int? = null
        var maxStringLength: Int? = null
    }

    class Ctx {
        var colors = false
        var depth: Int = 2
        var showHidden = false
        var customInspect = false
        var maxArrayItems: Int = 10_000
        var maxObjectKeys: Int = 10_000
        var maxStringLength: Int = 100_000

        // Identity-based collection to avoid O(n^2).
        // zh-CN: 以对象身份比较的集合, 避免 O(n^2).
        val seen: MutableSet<ScriptableObject> = Collections.newSetFromMap(IdentityHashMap())

        // Pre-compute style cache to avoid constructing escape sequences every time during stylize.
        // zh-CN: 预计算样式缓存, 避免每次 stylize 都构造转义序列.
        var styleCache: Map<String, Pair<String, String>> = emptyMap()

        lateinit var stylize: (str: String, styleType: String?) -> String
    }

}
