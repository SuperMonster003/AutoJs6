package org.autojs.autojs.rhino

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The one set of rules for turning a JavaScript number (always a `Double` on the Rhino side) into
 * the numeric type a Java / Kotlin parameter declares. Shared by the global selector functions
 * (`SelectorMethodResolver`), the selector registry and map parser, and `UiObject.detect`'s
 * intercepted method calls, which used to disagree on truncation versus rounding.
 *
 * zh-CN: 把 JavaScript 数字 (Rhino 侧总是 `Double`) 转换为 Java / Kotlin 参数所声明数值类型的唯一一套规则.
 * 全局选择器函数 (`SelectorMethodResolver`), 选择器注册表与 map 解析器, 以及 `UiObject.detect` 的拦截调用共用,
 * 此前它们在截断与四舍五入上并不一致.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
object JsArgumentCoercion {

    /** Integral targets round to nearest; `Float` narrows. zh-CN: 整数目标四舍五入; `Float` 收窄. */
    fun coerceNumber(parameterType: Class<*>, value: Double): Any = when (parameterType) {
        Double::class.java, Double::class.javaObjectType -> value
        Float::class.java, Float::class.javaObjectType -> value.toFloat()
        Long::class.java, Long::class.javaObjectType -> value.roundToLong()
        Int::class.java, Int::class.javaObjectType -> value.roundToInt()
        Short::class.java, Short::class.javaObjectType -> value.roundToInt().toShort()
        Byte::class.java, Byte::class.javaObjectType -> value.roundToInt().toByte()
        else -> value
    }

    /** [coerceNumber] for `Double` values, anything else unchanged. zh-CN: `Double` 值经 [coerceNumber], 其它原样返回. */
    fun coerce(parameterType: Class<*>, value: Any?): Any? = when (value) {
        is Double -> coerceNumber(parameterType, value)
        else -> value
    }

    /** Any number to `Int`, rounding fractions. zh-CN: 任意数字转 `Int`, 小数四舍五入. */
    fun toInt(value: Number): Int = when (value) {
        is Int -> value
        is Double -> value.roundToInt()
        is Float -> value.roundToInt()
        else -> value.toInt()
    }

    /** Any number to `Long`, rounding fractions. zh-CN: 任意数字转 `Long`, 小数四舍五入. */
    fun toLong(value: Number): Long = when (value) {
        is Long -> value
        is Double -> value.roundToLong()
        is Float -> value.roundToLong()
        else -> value.toLong()
    }

}
