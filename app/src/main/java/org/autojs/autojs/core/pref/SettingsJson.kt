package org.autojs.autojs.core.pref

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.math.BigDecimal
import java.math.BigInteger

/**
 * The one JSON codec of the settings layer (docs/settings/ROADMAP.md T1 / T6, R3): every `Json` key, the archive
 * snapshot codec (P6.1) and the list values that `Pref` used to write with Gson go through it.
 *
 * JSON values are handled as plain Kotlin values: `null`, [Boolean], [Int], [Long], [Double], [String],
 * [List] of values and [Map] of [String] to values (keys in document order). Parsing uses `org.json.JSONTokener` only
 * to read quoted strings and parses everything else itself, so the result is the same with Android's org.json and
 * with the org.json artifact of the JVM tests (appendix C Q14), whose differences are evened out here:
 *
 * - `null` and `JSONObject.NULL` become Kotlin `null` (Android's `optString` would turn `JSONObject.NULL` into the
 *   text "null", the JVM artifact's into the fallback).
 * - Numbers are narrowed the same way on both: an integral number is an [Int] when it fits, else a [Long] when it fits,
 *   else a [Double]; a number with a fraction or an exponent is a [Double] (the JVM artifact returns `BigDecimal` /
 *   `BigInteger` where Android returns `Double` / `Long`, and they disagree on `-0`). Non-finite numbers are rejected.
 * - Objects keep their key order (the JVM artifact's `JSONObject` is a `HashMap`); a duplicate key is rejected.
 * - The syntax is strict JSON: double-quoted strings and keys, no unquoted words, no comments.
 *
 * Writing is done here as well, so the text does not depend on the platform either: strings escape `"`, `\`, control
 * characters, U+2028 and U+2029 like Gson does, `/` is never escaped, and with `htmlSafe` the characters `<`, `>`, `&`,
 * `=` and `'` are written as `<` and so on, which is byte for byte what `Gson().toJson` writes for a list of
 * strings (P1.4 keeps the stored list values unchanged, R1).
 *
 * zh-CN: 设置层唯一的 JSON 编解码 (ROADMAP T1 / T6, R3): 全部 `Json` 键, 归档快照编解码 (P6.1) 以及 `Pref` 以前用 Gson
 * 写的列表值都经过它. JSON 值以普通 Kotlin 值表示 (null, Boolean, Int, Long, Double, String, List, 键按文档顺序的 Map).
 * 解析只用 `JSONTokener` 读取带引号的字符串, 其余自行解析, 因此 Android 自带的 org.json 与 JVM 测试用的 org.json 构件
 * (附录 C Q14) 结果一致, 两者的差异在这里抹平: null 与 `JSONObject.NULL` 变为 Kotlin null; 数字统一收窄 (整数优先 Int,
 * 其次 Long, 再次 Double; 带小数或指数的为 Double; 两者对 `-0` 的结果也不同; 拒绝非有限数); 对象保留键顺序, 拒绝重复键;
 * 语法为严格 JSON (双引号字符串与键, 不接受无引号单词与注释). 写出也在这里完成, 文本与平台无关: 字符串按 Gson 的方式
 * 转义, 不转义 `/`, `htmlSafe` 时另外转义 `<` `>` `&` `=` `'`, 与 `Gson().toJson` 写字符串列表的结果逐字节相同 (P1.4, R1).
 */
object SettingsJson {

    /**
     * Converts between a value and its plain JSON value (see [SettingsJson]); [decode] throws
     * [SettingsJsonException] when the JSON value has another shape.
     * zh-CN: 值与普通 JSON 值之间的转换; 形状不符时 [decode] 抛 [SettingsJsonException].
     */
    interface Codec<T> {
        fun encode(value: T): Any?
        fun decode(json: Any?): T
    }

    class SettingsJsonException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

    /**
     * A list of strings, the shape of the values that `Pref.putLinkedHashSet` / `putLinkedList` write. Decoding keeps
     * other scalars as their text and drops `null` elements; values written by `Pref` contain neither.
     * zh-CN: 字符串列表, 即 `Pref.putLinkedHashSet` / `putLinkedList` 写入的值的形状. 解码时其他标量保留为文本, 丢弃 null
     * 元素; `Pref` 写入的值两者都不会出现.
     */
    @JvmField
    val STRING_LIST: Codec<List<String>> = object : Codec<List<String>> {
        override fun encode(value: List<String>): Any = ArrayList(value)
        override fun decode(json: Any?): List<String> {
            if (json !is List<*>) throw SettingsJsonException("Expected an array of strings, got ${describeType(json)}")
            return json.mapNotNull { element ->
                when (element) {
                    null -> null
                    is String -> element
                    is Boolean, is Number -> stringify(element)
                    else -> throw SettingsJsonException("Expected an array of strings, got an element of ${describeType(element)}")
                }
            }
        }
    }

    /**
     * Parses one JSON document into a plain value; throws [SettingsJsonException] for malformed text.
     * zh-CN: 把一个 JSON 文档解析为普通值; 文本格式错误时抛 [SettingsJsonException].
     */
    @JvmStatic
    fun parse(text: String): Any? {
        val source = text.removePrefix("\uFEFF")
        try {
            val tokener = JSONTokener(source)
            val value = readValue(tokener, tokener.nextClean())
            if (tokener.nextClean() != END) throw tokener.syntaxError("Unexpected text after the JSON value")
            return value
        } catch (e: JSONException) {
            throw SettingsJsonException(e.message ?: "Malformed JSON", e)
        }
    }

    /**
     * Writes a plain value (also accepting `JSONObject`, `JSONArray`, `JSONObject.NULL`, [Float], [Set] and arrays)
     * as compact JSON text.
     * zh-CN: 把普通值写为紧凑的 JSON 文本 (也接受 `JSONObject`, `JSONArray`, `JSONObject.NULL`, Float, Set 与数组).
     */
    @JvmStatic
    @JvmOverloads
    fun stringify(value: Any?, htmlSafe: Boolean = false): String = StringBuilder().also { write(value, htmlSafe, it) }.toString()

    /**
     * Converts org.json values (`JSONObject`, `JSONArray`, `JSONObject.NULL`, `BigDecimal` and other numbers) into
     * plain values with the number narrowing of [SettingsJson]; plain values are returned as they are.
     * zh-CN: 把 org.json 的值转为普通值 (数字按本类规则收窄); 普通值原样返回.
     */
    @JvmStatic
    fun normalize(value: Any?): Any? = when {
        value == null || value === JSONObject.NULL -> null
        value is Boolean || value is String -> value
        value is Number -> narrow(value)
        value is JSONObject -> LinkedHashMap<String, Any?>().also { map ->
            value.keys().forEach { key -> map[key] = normalize(value.opt(key)) }
        }
        value is JSONArray -> (0 until value.length()).map { normalize(value.opt(it)) }
        value is Map<*, *> -> LinkedHashMap<String, Any?>().also { map ->
            value.forEach { (k, v) -> map[k as? String ?: throw SettingsJsonException("Object keys must be strings, got ${describeType(k)}")] = normalize(v) }
        }
        value is Iterable<*> -> value.map(::normalize)
        value is Array<*> -> value.map(::normalize)
        value is Char -> value.toString()
        else -> throw SettingsJsonException("Unsupported JSON value of ${describeType(value)}")
    }

    /** True for `null` and `JSONObject.NULL`. */
    @JvmStatic
    fun isNull(value: Any?): Boolean = value == null || value === JSONObject.NULL

    /**
     * Narrows a JSON number to [Int]; integral [Double] values are accepted, anything out of range or with a fraction
     * gives null.
     * zh-CN: 把 JSON 数字收窄为 Int; 接受整数值的 Double, 超出范围或带小数时返回 null.
     */
    @JvmStatic
    fun toInt(value: Any?): Int? = toLong(value)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    /** Like [toInt] for [Long]. */
    @JvmStatic
    fun toLong(value: Any?): Long? = when (val n = (value as? Number)?.let(::narrowOrNull)) {
        is Int -> n.toLong()
        is Long -> n
        is Double -> if (n == Math.rint(n) && n >= -9.223372036854776E18 && n < 9.223372036854776E18) n.toLong() else null
        else -> null
    }

    /**
     * Narrows a JSON number to a finite [Float]; null when it is not a number or does not fit.
     * zh-CN: 把 JSON 数字收窄为有限的 Float; 不是数字或超出范围时返回 null.
     */
    @JvmStatic
    fun toFloat(value: Any?): Float? = toDouble(value)?.toFloat()?.takeIf { it.isFinite() }

    /** A JSON number as a finite [Double]; null when it is not a number. */
    @JvmStatic
    fun toDouble(value: Any?): Double? = when (val n = (value as? Number)?.let(::narrowOrNull)) {
        is Int -> n.toDouble()
        is Long -> n.toDouble()
        is Double -> n
        else -> null
    }

    /**
     * Encodes a list of strings as `Pref.putLinkedHashSet` / `putLinkedList` have always stored it (Gson's text).
     * zh-CN: 按 `Pref.putLinkedHashSet` / `putLinkedList` 一直以来的存储形式 (Gson 的文本) 编码字符串列表.
     */
    @JvmStatic
    fun encodeStringList(values: Iterable<String>): String = stringify(values.toList(), htmlSafe = true)

    /**
     * Decodes a stored list of strings ([STRING_LIST]); throws [SettingsJsonException] for malformed text.
     * zh-CN: 解码存储的字符串列表 ([STRING_LIST]); 文本格式错误时抛 [SettingsJsonException].
     */
    @JvmStatic
    fun decodeStringList(text: String): List<String> = STRING_LIST.decode(parse(text))

    // ------------------------------------------------------------------------------------------------- parsing

    private const val END = 0.toChar()

    // Characters that end an unquoted literal (line breaks and the end of input end it as well).
    private const val LITERAL_END = ",:[]{}\"'/\\;=# \t\u000c"

    private val NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

    private fun readValue(tokener: JSONTokener, first: Char): Any? = when (first) {
        '{' -> readObject(tokener)
        '[' -> readArray(tokener)
        '"' -> tokener.nextString(first)
        END -> throw tokener.syntaxError("Unexpected end of input")
        else -> {
            tokener.back()
            readLiteral(tokener, tokener.nextTo(LITERAL_END))
        }
    }

    private fun readLiteral(tokener: JSONTokener, literal: String): Any? = when {
        literal == "true" -> true
        literal == "false" -> false
        literal == "null" -> null
        NUMBER.matches(literal) -> {
            val integral = literal.none { it == '.' || it == 'e' || it == 'E' }
            // String.toDouble keeps the sign of "-0.0", which BigDecimal would drop.
            val n: Number? = if (integral) narrowOrNull(BigInteger(literal)) else literal.toDouble().takeIf { it.isFinite() }
            n ?: throw tokener.syntaxError("Number out of range: $literal")
        }
        literal.isEmpty() -> throw tokener.syntaxError("Missing value")
        else -> throw tokener.syntaxError("Unexpected literal \"$literal\"")
    }

    private fun readObject(tokener: JSONTokener): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        var c = tokener.nextClean()
        if (c == '}') return map
        while (true) {
            if (c != '"') throw tokener.syntaxError("Expected a double-quoted object key")
            val key = tokener.nextString(c)
            if (tokener.nextClean() != ':') throw tokener.syntaxError("Expected ':' after the key \"$key\"")
            if (map.containsKey(key)) throw tokener.syntaxError("Duplicate key \"$key\"")
            map[key] = readValue(tokener, tokener.nextClean())
            when (tokener.nextClean()) {
                ',' -> c = tokener.nextClean()
                '}' -> return map
                else -> throw tokener.syntaxError("Expected ',' or '}'")
            }
        }
    }

    private fun readArray(tokener: JSONTokener): List<Any?> {
        val list = ArrayList<Any?>()
        var c = tokener.nextClean()
        if (c == ']') return list
        while (true) {
            list.add(readValue(tokener, c))
            when (tokener.nextClean()) {
                ',' -> c = tokener.nextClean()
                ']' -> return list
                else -> throw tokener.syntaxError("Expected ',' or ']'")
            }
        }
    }

    private fun narrow(n: Number): Number = narrowOrNull(n) ?: throw SettingsJsonException("Number out of range: $n")

    private fun narrowOrNull(n: Number): Number? = when (n) {
        is Int -> n
        is Long -> if (n in Int.MIN_VALUE..Int.MAX_VALUE) n.toInt() else n
        is Short, is Byte -> n.toInt()
        is BigInteger -> when {
            n.bitLength() <= 31 -> n.toInt()
            n.bitLength() <= 63 -> n.toLong()
            else -> n.toDouble().takeIf { it.isFinite() }
        }
        // Float.toString keeps the decimal digits the value was written with (0.1f stays 0.1).
        is Float -> n.toString().toDouble().takeIf { it.isFinite() }
        is BigDecimal -> n.toDouble().takeIf { it.isFinite() }
        else -> n.toDouble().takeIf { it.isFinite() }
    }

    // ------------------------------------------------------------------------------------------------- writing

    private fun write(value: Any?, htmlSafe: Boolean, out: StringBuilder) {
        when {
            value == null || value === JSONObject.NULL -> out.append("null")
            value is Boolean -> out.append(value.toString())
            value is String -> quote(value, htmlSafe, out)
            value is Char -> quote(value.toString(), htmlSafe, out)
            value is Float -> out.append(value.also { checkFinite(it.toDouble(), it) }.toString())
            value is Double -> out.append(value.also { checkFinite(it, it) }.toString())
            value is BigDecimal -> out.append(value.toString())
            value is Number -> out.append(value.toString())
            value is JSONObject || value is JSONArray -> write(normalize(value), htmlSafe, out)
            value is Map<*, *> -> {
                out.append('{')
                var first = true
                value.forEach { (k, v) ->
                    if (!first) out.append(',')
                    first = false
                    quote(k as? String ?: throw SettingsJsonException("Object keys must be strings, got ${describeType(k)}"), htmlSafe, out)
                    out.append(':')
                    write(v, htmlSafe, out)
                }
                out.append('}')
            }
            value is Iterable<*> -> writeArray(value.iterator(), htmlSafe, out)
            value is Array<*> -> writeArray(value.iterator(), htmlSafe, out)
            else -> throw SettingsJsonException("Unsupported JSON value of ${describeType(value)}")
        }
    }

    private fun writeArray(iterator: Iterator<*>, htmlSafe: Boolean, out: StringBuilder) {
        out.append('[')
        var first = true
        iterator.forEach {
            if (!first) out.append(',')
            first = false
            write(it, htmlSafe, out)
        }
        out.append(']')
    }

    private fun checkFinite(d: Double, original: Number): Double {
        if (!d.isFinite()) throw SettingsJsonException("Non-finite numbers are not JSON: $original")
        return d
    }

    private fun quote(s: String, htmlSafe: Boolean, out: StringBuilder) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\u000c' -> out.append("\\f")
                ' ', ' ' -> unicodeEscape(c, out)
                '<', '>', '&', '=', '\'' -> if (htmlSafe) unicodeEscape(c, out) else out.append(c)
                else -> if (c < ' ') unicodeEscape(c, out) else out.append(c)
            }
        }
        out.append('"')
    }

    private fun unicodeEscape(c: Char, out: StringBuilder) {
        out.append("\\u").append(Integer.toHexString(c.code).padStart(4, '0'))
    }

    private fun describeType(value: Any?): String = if (value == null) "null" else value::class.java.name
}
