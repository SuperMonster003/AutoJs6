package org.autojs.autojs.core.automator.selector

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.BooleanFilter
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
enum class SelectorArgType(val kotlinType: String) {

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
    ACTIONS("Any") {
        override fun coerce(value: Any?, lenient: Boolean): Any? = value
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

    val isVararg: Boolean get() = params.size == 1 && params[0].type == SelectorArgType.ACTIONS

    /**
     * [args] coerced for this signature, or null when they do not fit (count or kinds).
     * zh-CN: 按本签名转换后的 [args], 数量或种类不符时为 null.
     */
    fun coerce(args: List<Any?>, lenient: Boolean): List<Any?>? {
        if (isVararg) {
            return args.takeIf { list -> list.none { it == null } }
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

    /** The first overload accepting [args], with the coerced arguments. zh-CN: 首个接受 [args] 的重载及转换后的参数. */
    fun resolve(args: List<Any?>, lenient: Boolean): Pair<SelectorOverload, List<Any?>>? {
        for (overload in overloads) {
            val coerced = overload.coerce(args, lenient) ?: continue
            return overload to coerced
        }
        return null
    }

}
