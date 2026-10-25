package org.autojs.autojs.core.automator.selector

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.filter.NormalizeOptions
import org.autojs.autojs.core.automator.filter.Preference
import org.autojs.autojs.core.automator.pick.PickMessages
import org.autojs.autojs.rhino.JsArgumentCoercion
import org.autojs.autojs.util.App
import org.mozilla.javascript.regexp.NativeRegExp

/**
 * The argument kinds a selector property accepts. Each kind knows how to recognise a value coming
 * from a script (strictly, as a typed builder call would receive it, or leniently, as the elements
 * of a map selector's array value) and how to coerce it into the Kotlin type of the builder
 * parameter. [kotlinType] is what the generated builder declares.
 *
 * zh-CN: 选择器属性可接受的参数种类. 每一种都知道如何识别来自脚本的值 (严格: 与类型化的 builder 调用一致; 宽松: map
 * 选择器数组值的元素), 以及如何把它转换为 builder 参数的 Kotlin 类型. [kotlinType] 是生成的 builder 所声明的类型.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
enum class SelectorArgType(val kotlinType: String, val isVararg: Boolean = false) {

    STRING("String") {
        override fun coerce(value: Any?, lenient: Boolean): Any? = (value as? CharSequence)?.toString()
    },

    REGEX("NativeRegExp") {
        override fun coerce(value: Any?, lenient: Boolean): Any? = value as? NativeRegExp
    },

    BOOLEAN("Boolean") {
        override fun coerce(value: Any?, lenient: Boolean): Any? = when (value) {
            is Boolean -> value
            is CharSequence -> if (lenient) value.toString().toBoolean() else null
            else -> null
        }
    },

    INT("Int") {
        override fun coerce(value: Any?, lenient: Boolean): Any? = when (value) {
            is Number -> JsArgumentCoercion.toInt(value)
            is CharSequence -> if (lenient) value.toString().toIntOrNull() else null
            else -> null
        }
    },

    DOUBLE("Double") {
        override fun coerce(value: Any?, lenient: Boolean): Any? = when (value) {
            is Number -> value.toDouble()
            is CharSequence -> if (lenient) value.toString().toDoubleOrNull() else null
            else -> null
        }
    },

    APP("App") {
        override fun coerce(value: Any?, lenient: Boolean): Any? = value as? App
    },

    FILTER("BooleanFilter.BooleanSupplier") {
        override fun coerce(value: Any?, lenient: Boolean): Any? = value as? BooleanFilter.BooleanSupplier
    },

    /** The `vararg actions: Any` of `action()`: any non-null values. zh-CN: `action()` 的 `vararg actions: Any`: 任意非空值. */
    ACTIONS("Any", isVararg = true) {
        override fun coerce(value: Any?, lenient: Boolean): Any? = value
    },

    /**
     * The options object of `normalize({ trim: true, ... })` (docs/selector/ROADMAP.md P3.3): any object; its names and
     * values are checked when the overload is resolved. zh-CN: `normalize({ trim: true, ... })` 的选项对象 (P3.3): 任意对象; 解析重载时检查其名称与值.
     */
    OPTIONS("Map<*, *>") {
        override fun coerce(value: Any?, lenient: Boolean): Any? = value as? Map<*, *>
    },

    /**
     * The `vararg keys: String` of `prefer(...)` (P3.4): strings, checked against the keys when the overload is resolved.
     * zh-CN: `prefer(...)` 的 `vararg keys: String` (P3.4): 字符串, 解析重载时按键名检查.
     */
    KEYS("String", isVararg = true) {
        override fun coerce(value: Any?, lenient: Boolean): Any? = (value as? CharSequence)?.toString()
    },
    ;

    /**
     * The value as the builder parameter expects it, or null when it is not acceptable ([lenient]
     * additionally parses strings for numbers and booleans, which is how map selectors always
     * treated array elements).
     * zh-CN: builder 参数所期望形式的值, 不可接受时为 null ([lenient] 时额外把字符串解析为数字与布尔值, 这是 map 选择器
     * 对数组元素的一贯处理).
     */
    abstract fun coerce(value: Any?, lenient: Boolean): Any?

}

/** A builder parameter: name, kind and the optional `@FloatRange` bounds. zh-CN: builder 参数: 名称, 种类与可选的 `@FloatRange` 范围. */
class SelectorParam(val name: String, val type: SelectorArgType, val range: ClosedFloatingPointRange<Double>? = null)

/**
 * One builder signature of a property and what applying it to a selector does.
 * zh-CN: 属性的一个 builder 签名, 以及把它施加到选择器上的动作.
 */
class SelectorOverload(val params: List<SelectorParam>, val apply: (UiSelector, List<Any?>) -> Unit) {

    val isVararg: Boolean get() = params.size == 1 && params[0].type.isVararg

    /**
     * [args] coerced for this signature, or null when they do not fit (count or kinds).
     * zh-CN: 按本签名转换后的 [args], 数量或种类不符时为 null.
     */
    fun coerce(args: List<Any?>, lenient: Boolean): List<Any?>? {
        if (isVararg) {
            return args.map { params[0].type.coerce(it, lenient) ?: return null }
        }
        if (args.size != params.size) return null
        return params.mapIndexed { i, param -> param.type.coerce(args[i], lenient) ?: return null }
    }

}

/**
 * A selector property as the registry describes it: the builder name, its overloads in resolution
 * order, the name of the method that replaces it when deprecated, and (for value properties) the
 * [UiNode] getter it reads, so that other consumers (string selectors, snapshots) can read the same
 * value without going through a filter.
 * zh-CN: 注册表所描述的选择器属性: builder 名称, 按解析顺序排列的重载, 已弃用时的替代方法名, 以及 (值属性的)
 * [UiNode] 取值器, 使其它消费方 (字符串选择器, 快照) 无需经过过滤器即可读到同一个值.
 */
class SelectorEntry(
    val name: String,
    val overloads: List<SelectorOverload>,
    val deprecatedFor: String? = null,
    val getter: ((UiNode<*>) -> Any?)? = null,
) {

    val isDeprecated: Boolean get() = deprecatedFor != null

    /** Whether a string argument of this property is compiled as a pattern (`xxxMatches`, `xxxMatch`). zh-CN: 本属性的字符串参数是否按正则编译. */
    private val compilesStrings: Boolean = name.endsWith("Matches") || name.endsWith("Match")

    /**
     * What the first overload accepting [args] makes of them, told without throwing (docs/selector/ROADMAP.md P1.4: the
     * overload check of the former `SelectorChecks`): the overload with the coerced arguments, no overload, or a refusal
     * of the overload that takes them (NaN for an integer, which `JsArgumentCoercion.toInt` cannot round; the name of
     * an unknown `algorithm`). A pattern is not compiled here: [SelectorResolution.Resolved.patterns] names what
     * applying the overload compiles.
     * zh-CN: 首个接受 [args] 的重载对其的判定, 不抛异常 (P1.4: 原 `SelectorChecks` 的重载检查): 重载及转换后的参数, 没有重载, 或接受参数的重载拒绝了它们
     * (整数处的 NaN, `JsArgumentCoercion.toInt` 无法取整; 未知的 `algorithm` 名称). 此处不编译正则: [SelectorResolution.Resolved.patterns] 给出施加重载时要编译的内容.
     */
    fun resolve(args: List<Any?>, lenient: Boolean): SelectorResolution {
        for (overload in overloads) {
            if (overload.isVararg) {
                val coerced = overload.coerce(args, lenient) ?: continue
                return rejection(coerced) ?: SelectorResolution.Resolved(overload, coerced, emptyList())
            }
            val params = overload.params
            if (args.size != params.size) continue
            var fits = true
            for (i in params.indices) {
                if (params[i].type == SelectorArgType.INT && isNaN(args[i])) return SelectorResolution.Rejected { NAN }
                if (params[i].type.coerce(args[i], lenient) == null) {
                    fits = false
                    break
                }
            }
            if (fits) return resolved(overload, args, lenient)
        }
        return SelectorResolution.NoOverload
    }

    private fun resolved(overload: SelectorOverload, args: List<Any?>, lenient: Boolean): SelectorResolution {
        val coerced = overload.params.mapIndexed { i, param -> param.type.coerce(args[i], lenient) }
        if (name == ALGORITHM) {
            val algorithm = coerced[0] as String
            if (UiSelector.algorithmOf(algorithm) == null) return SelectorResolution.Rejected { PickMessages.unknownAlgorithm(algorithm) }
        }
        rejection(coerced)?.let { return it }
        val patterns = overload.params.indices.mapNotNull { i ->
            when (overload.params[i].type) {
                // StringMatcher compiles a RegExp from its string form, `/source/flags`, and a string as it is.
                // zh-CN: StringMatcher 由 RegExp 的字符串形式 (/source/flags) 编译, 字符串则原样编译.
                SelectorArgType.REGEX -> coerced[i].toString()
                SelectorArgType.STRING -> if (compilesStrings) coerced[i] as String else null
                else -> null
            }
        }
        return SelectorResolution.Resolved(overload, coerced, patterns)
    }

    private fun isNaN(value: Any?) = value is Double && value.isNaN() || value is Float && value.isNaN()

    /**
     * The refusal of the options of `normalize` (P3.3: a name not among [NormalizeOptions.NAMES], or a value not a
     * boolean) and of the keys of `prefer` (P3.4: none, or one not among [Preference.Key]), else null.
     * zh-CN: `normalize` 的选项 (P3.3: 名称不在 [NormalizeOptions.NAMES] 中, 或值不是布尔值) 与 `prefer` 的键 (P3.4: 没有键, 或键不在 [Preference.Key] 中) 的拒绝, 否则为 null.
     */
    private fun rejection(coerced: List<Any?>): SelectorResolution.Rejected? = when (name) {
        NORMALIZE -> (coerced.singleOrNull() as? Map<*, *>)?.takeIf { NormalizeOptions.of(it) == null }?.let { options ->
            SelectorResolution.Rejected {
                val names = NormalizeOptions.NAMES.joinToString(", ")
                val unknown = options.keys.firstOrNull { it !is CharSequence || it.toString() !in NormalizeOptions.NAMES }
                if (unknown != null) "Unknown option of normalize: \"$unknown\" (options: $names)"
                else "The options of normalize take booleans (options: $names)"
            }
        }
        PREFER -> when {
            coerced.isEmpty() -> SelectorResolution.Rejected { "prefer needs at least one key (keys: ${Preference.Key.quoted})" }
            else -> coerced.firstOrNull { Preference.Key.of(it as String) == null }?.let { key ->
                SelectorResolution.Rejected { "Unknown key of prefer: \"$key\" (keys: ${Preference.Key.quoted})" }
            }
        }
        else -> null
    }

    private companion object {

        const val ALGORITHM = "algorithm"

        const val NORMALIZE = "normalize"

        const val PREFER = "prefer"

        /** The message of `roundToInt` / `roundToLong` for NaN (kotlin.math). zh-CN: kotlin.math 的 roundToInt / roundToLong 对 NaN 的信息. */
        const val NAN = "Cannot round NaN value."

    }

}

/**
 * What [SelectorEntry.resolve] makes of the arguments of a selector property.
 * zh-CN: [SelectorEntry.resolve] 对选择器属性参数的判定.
 */
sealed class SelectorResolution {

    /**
     * The first overload accepting the arguments, with them [coerced][arguments]; applying it compiles [patterns] (the
     * strings of the pattern conditions and the `/source/flags` form of a RegExp), each of which may still not compile.
     * zh-CN: 首个接受参数的重载及转换后的 [arguments]; 施加它时会编译 [patterns] (正则条件的字符串与 RegExp 的 /source/flags 形式), 它们仍可能无法编译.
     */
    class Resolved(val overload: SelectorOverload, val arguments: List<Any?>, val patterns: List<String>) : SelectorResolution()

    /** No overload takes the arguments. zh-CN: 没有重载接受参数. */
    object NoOverload : SelectorResolution()

    /** The overload that takes the arguments refuses them, for [reason] (read lazily: a string resource). zh-CN: 接受参数的重载拒绝了它们, 原因为 [reason] (读取时才生成). */
    class Rejected(private val describe: () -> String) : SelectorResolution() {
        val reason: String get() = describe()
    }

}
