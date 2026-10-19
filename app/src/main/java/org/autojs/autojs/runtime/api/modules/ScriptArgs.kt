package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The conversion table between script values and Kotlin parameter types (roadmap T4), called only by the generated
 * bindings: argument counting, conversion per parameter kind, overload guards, result normalization and the errors
 * of the generated layer (T5: a failed conversion is a JS `TypeError`, an out-of-range number a `RangeError`).
 *
 * P1.1 prototype: the messages are plain English; P1.2 moves them to the string resources (R4) with a fallback for
 * the JVM tests, and settles the remaining rules of the table (see ROADMAP T4).
 *
 * zh-CN: 脚本值与 Kotlin 参数类型之间的转换表 (T4), 只供生成的绑定调用: 参数计数, 按参数种类转换, 重载判别, 返回值归一化, 以及生成层的错误
 * (T5: 转换失败为 JS TypeError, 数值越界为 RangeError). P1.1 原型的消息为英文, P1.2 改走字符串资源 (R4) 并为 JVM 测试提供回退.
 */
object ScriptArgs {

    /** The argument count with trailing `undefined` arguments beyond [minRequired] treated as omitted. */
    @JvmStatic
    fun count(args: Array<out Any?>, minRequired: Int): Int {
        var n = args.size
        while (n > minRequired && Undefined.isUndefined(args[n - 1])) n--
        return n
    }

    @JvmStatic
    fun isNullish(value: Any?): Boolean = value == null || Undefined.isUndefined(value)

    // Conversions. "OrNull" variants map null / undefined to null.

    @JvmStatic
    fun nonNullish(value: Any?, function: String, param: String): Any {
        if (isNullish(value)) throw typeError(function, param, "a value", value)
        return value!!
    }

    @JvmStatic
    fun string(value: Any?, function: String, param: String): String = when (val v = unwrap(value)) {
        is String -> v
        is ConsString, is CharSequence -> v.toString()
        null, Undefined.instance -> throw typeError(function, param, "a string", value)
        is org.mozilla.javascript.Symbol -> throw typeError(function, param, "a string", value)
        is Scriptable, is Number, is Boolean -> RhinoScriptRuntime.toString(v)
        else -> v.toString()
    }

    @JvmStatic
    fun stringOrNull(value: Any?, function: String, param: String): String? = if (isNullish(value)) null else string(value, function, param)

    @JvmStatic
    fun boolean(value: Any?, function: String, param: String): Boolean = when (val v = unwrap(value)) {
        is Boolean -> v
        else -> throw typeError(function, param, "a boolean", value)
    }

    @JvmStatic
    fun booleanOrNull(value: Any?, function: String, param: String): Boolean? = if (isNullish(value)) null else boolean(value, function, param)

    /** Numbers, boxed Java numbers and numeric strings (kept from the current Kotlin modules, see ROADMAP appendix E). */
    @JvmStatic
    fun double(value: Any?, function: String, param: String): Double = when (val v = unwrap(value)) {
        is Number -> v.toDouble()
        is CharSequence -> RhinoScriptRuntime.toNumber(v.toString()).also { d ->
            if (d.isNaN() && v.toString().trim() != "NaN") throw typeError(function, param, "a number", value)
        }
        else -> throw typeError(function, param, "a number", value)
    }

    @JvmStatic
    fun doubleOrNull(value: Any?, function: String, param: String): Double? = if (isNullish(value)) null else double(value, function, param)

    @JvmStatic
    fun float(value: Any?, function: String, param: String): Float = double(value, function, param).toFloat()

    @JvmStatic
    fun floatOrNull(value: Any?, function: String, param: String): Float? = if (isNullish(value)) null else float(value, function, param)

    @JvmStatic
    fun int(value: Any?, function: String, param: String): Int {
        val d = double(value, function, param)
        if (d.isNaN() || d.isInfinite() || d != Math.floor(d) || d < Int.MIN_VALUE || d > Int.MAX_VALUE) {
            throw RhinoScriptRuntime.rangeError("$function: argument \"$param\" must be an integer within the 32-bit range, got ${RhinoScriptRuntime.numberToString(d, 10)}")
        }
        return d.toInt()
    }

    @JvmStatic
    fun intOrNull(value: Any?, function: String, param: String): Int? = if (isNullish(value)) null else int(value, function, param)

    @JvmStatic
    fun long(value: Any?, function: String, param: String): Long {
        val d = double(value, function, param)
        if (d.isNaN() || d.isInfinite() || d != Math.floor(d) || d < Long.MIN_VALUE.toDouble() || d > Long.MAX_VALUE.toDouble()) {
            throw RhinoScriptRuntime.rangeError("$function: argument \"$param\" must be an integer, got ${RhinoScriptRuntime.numberToString(d, 10)}")
        }
        return d.toLong()
    }

    @JvmStatic
    fun longOrNull(value: Any?, function: String, param: String): Long? = if (isNullish(value)) null else long(value, function, param)

    /** A Java byte[] or a JS array of numbers (each number narrowed like `Number.byteValue()`, other elements 0). */
    @JvmStatic
    fun bytes(value: Any?, function: String, param: String): ByteArray = when (val v = unwrap(value)) {
        is ByteArray -> v
        is NativeArray -> ByteArray(v.length.toInt()) { i -> (v.get(i, v) as? Number)?.toByte() ?: 0 }
        else -> throw typeError(function, param, "a byte array or an array of numbers", value)
    }

    @JvmStatic
    fun bytesOrNull(value: Any?, function: String, param: String): ByteArray? = if (isNullish(value)) null else bytes(value, function, param)

    /** A JS array (elements as raw script values) or a Java list. */
    @JvmStatic
    fun list(value: Any?, function: String, param: String): List<Any?> = when (val v = if (value is NativeArray) value else unwrap(value)) {
        is NativeArray -> List(v.length.toInt()) { i -> v.get(i, v) }
        is List<*> -> v
        else -> throw typeError(function, param, "an array", value)
    }

    @JvmStatic
    fun listOrNull(value: Any?, function: String, param: String): List<Any?>? = if (isNullish(value)) null else list(value, function, param)

    /** A plain JS object (own enumerable string keys, values as raw script values) or a Java map. */
    @JvmStatic
    fun map(value: Any?, function: String, param: String): Map<String, Any?> = when (val v = if (value is NativeObject) value else unwrap(value)) {
        is NativeObject -> LinkedHashMap<String, Any?>().also { out -> v.ids.forEach { id -> if (id is String) out[id] = v.get(id, v) } }
        is Map<*, *> -> v.entries.associate { (k, e) -> k.toString() to e }
        else -> throw typeError(function, param, "an object", value)
    }

    @JvmStatic
    fun mapOrNull(value: Any?, function: String, param: String): Map<String, Any?>? = if (isNullish(value)) null else map(value, function, param)

    /** A value of [type]: the script value itself (script objects) or the Java object it wraps. */
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> typed(value: Any?, type: Class<T>, function: String, param: String): T {
        if (type.isInstance(value)) return value as T
        val unwrapped = (value as? Wrapper)?.unwrap()
        if (type.isInstance(unwrapped)) return unwrapped as T
        throw typeError(function, param, "an instance of ${type.name}", value)
    }

    @JvmStatic
    fun <T : Any> typedOrNull(value: Any?, type: Class<T>, function: String, param: String): T? = if (isNullish(value)) null else typed(value, type, function, param)

    // Variable arguments: everything from index [from] on.

    @JvmStatic
    fun rest(args: Array<out Any?>, from: Int, function: String, param: String): Array<Any?> =
        if (from >= args.size) emptyArray() else Array(args.size - from) { i -> args[from + i] }

    @JvmStatic
    fun strings(args: Array<out Any?>, from: Int, function: String, param: String): Array<String> =
        if (from >= args.size) emptyArray() else Array(args.size - from) { i -> string(args[from + i], function, param) }

    @JvmStatic
    fun doubles(args: Array<out Any?>, from: Int, function: String, param: String): DoubleArray =
        if (from >= args.size) DoubleArray(0) else DoubleArray(args.size - from) { i -> double(args[from + i], function, param) }

    @JvmStatic
    fun ints(args: Array<out Any?>, from: Int, function: String, param: String): IntArray =
        if (from >= args.size) IntArray(0) else IntArray(args.size - from) { i -> int(args[from + i], function, param) }

    // Overload guards.

    @JvmStatic
    fun isString(value: Any?): Boolean = unwrap(value) is CharSequence

    @JvmStatic
    fun isBoolean(value: Any?): Boolean = unwrap(value) is Boolean

    @JvmStatic
    fun isNumber(value: Any?): Boolean = unwrap(value) is Number

    @JvmStatic
    fun isBytes(value: Any?): Boolean = unwrap(value).let { it is ByteArray || it is NativeArray }

    @JvmStatic
    fun isList(value: Any?): Boolean = value is NativeArray || unwrap(value) is List<*>

    @JvmStatic
    fun isMap(value: Any?): Boolean = value is NativeObject || unwrap(value) is Map<*, *>

    @JvmStatic
    fun isInstance(value: Any?, type: Class<*>): Boolean = type.isInstance(value) || type.isInstance((value as? Wrapper)?.unwrap())

    // Results.

    @JvmStatic
    fun toJs(scope: VarScope, value: Any?): Any? = RhinoUtils.toJsValue(scope, value)

    /** The result of a function returning `Unit`. */
    @JvmStatic
    fun undefined(@Suppress("UNUSED_PARAMETER") unit: Unit): Any = Undefined.instance

    /** The result of a constructor: a script object, wrapping a Java result if needed. */
    @JvmStatic
    fun constructed(scope: VarScope, value: Any?): Scriptable = toJs(scope, value) as? Scriptable ?: RhinoScriptRuntime.toObject(scope, value)

    // Errors of the generated layer.

    @JvmStatic
    fun arityError(function: String, min: Int, max: Int, actual: Int): RuntimeException {
        val expected = when {
            max < 0 -> "at least $min argument${plural(min)}"
            min == max -> "$min argument${plural(min)}"
            else -> "$min to $max arguments"
        }
        return RhinoScriptRuntime.typeError("$function expects $expected, got $actual")
    }

    @JvmStatic
    fun noOverload(function: String, args: Array<out Any?>, signatures: String): RuntimeException =
        RhinoScriptRuntime.typeError("$function: no overload accepts (${args.joinToString { describe(it) }}); candidates: $signatures")

    @JvmStatic
    fun unknownMember(module: String, id: Int): RuntimeException = IllegalStateException("Script module $module has no member with id $id")

    private fun typeError(function: String, param: String, expected: String, value: Any?): RuntimeException =
        RhinoScriptRuntime.typeError("$function: argument \"$param\" must be $expected, got ${describe(value)}")

    private fun plural(n: Int) = if (n == 1) "" else "s"

    /** A short description of a script value for error messages. */
    @JvmStatic
    fun describe(value: Any?): String = when {
        value == null -> "null"
        Undefined.isUndefined(value) -> "undefined"
        value is CharSequence -> "string"
        value is Number -> "number"
        value is Boolean -> "boolean"
        value is NativeArray -> "Array"
        value is Wrapper -> value.unwrap()?.javaClass?.name ?: "null"
        value is Scriptable -> RhinoScriptRuntime.`typeof`(value).let { if (it == "object") value.className else it }
        else -> value.javaClass.name
    }

    private fun unwrap(value: Any?): Any? = if (value is Wrapper && value !is NativeArray && value !is NativeObject) value.unwrap() else value

}
