package org.autojs.autojs.runtime.api.modules

import android.content.res.Resources
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs6.R
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Error messages of the script module layer (roadmap T5 / R4). They come from the host's string resources in the app
 * language; without an Android context (JVM unit tests) the English text of the default `values/strings.xml` is used,
 * which `ScriptMessagesTest` keeps equal to the resources.
 *
 * Messages are built on the error path of every failing module call (R2), so the raw resource texts are looked up once
 * per resources object and locale, and [format] fills in the arguments the way `Resources.getString(id, args)`
 * (`String.format` in the configuration locale) does, without creating a `java.util.Formatter` (roadmap P1.4: on
 * XQ-AT72 the lookup and the formatter took about 20 us per message, two messages per argument error).
 *
 * zh-CN: 脚本模块层的错误消息 (T5 / R4). 取自宿主字符串资源 (应用语言); 没有 Android Context 时 (JVM 单元测试) 使用默认 `values/strings.xml`
 * 的英文文本, 由 ScriptMessagesTest 保证与资源一致. 每个失败的模块调用都要构造消息 (R2), 因此资源原文按资源对象与语言只查一次, [format] 按
 * `Resources.getString(id, args)` (以配置语言调用 String.format) 的规则填入参数, 但不创建 java.util.Formatter (P1.4: XQ-AT72 上查资源加格式化每条约 20 us,
 * 参数错误每次两条).
 */
object ScriptMessages {

    /** "Failed to call method "[method]": [ [cause] ]", the prefix of every error of a module function. */
    @JvmStatic
    fun failedToCall(method: String, cause: String): String =
        string(R.string.error_failed_to_call_method_with_cause, FAILED_TO_CALL, method, cause)

    /** The accepted argument count: exactly [min], [min] to [max], or at least [min] when [max] is negative. */
    @JvmStatic
    fun arity(min: Int, max: Int): String = when {
        max < 0 -> plural(R.plurals.error_method_only_accepts_no_less_than_n_arguments, min, NO_LESS_THAN_ONE, NO_LESS_THAN_OTHER)
        min == max -> plural(R.plurals.error_method_only_accepts_n_arguments, min, EXACTLY_ONE, EXACTLY_OTHER)
        else -> string(R.string.error_method_only_accepts_a_number_of_arguments_in_the_range_n_to_m, IN_RANGE, min, max)
    }

    @JvmStatic
    fun argumentType(param: String, expected: String, actual: String): String =
        string(R.string.error_script_argument_must_be_of_type, ARGUMENT_TYPE, param, expected, actual)

    @JvmStatic
    fun argumentCannotBe(param: String, actual: String): String =
        string(R.string.error_script_argument_cannot_be, ARGUMENT_CANNOT_BE, param, actual)

    @JvmStatic
    fun argumentInteger(param: String, bits: Int, actual: String): String =
        string(R.string.error_script_argument_must_be_integer, ARGUMENT_INTEGER, param, bits, actual)

    @JvmStatic
    fun noOverload(arguments: String, candidates: String): String =
        string(R.string.error_script_no_overload_accepts_arguments, NO_OVERLOAD, arguments, candidates)

    @JvmStatic
    fun unknownCharset(name: String): String = string(R.string.error_unknown_charset, UNKNOWN_CHARSET, name)

    /** "Argument "[param]" must be a positive number, got [actual]" (P2.1). */
    @JvmStatic
    fun argumentPositive(param: String, actual: String): String =
        string(R.string.error_script_argument_must_be_positive, ARGUMENT_POSITIVE, param, actual)

    /** "Argument "[param]" contains no valid number": a list or range argument of numbers that are all NaN or missing (P2.1). */
    @JvmStatic
    fun argumentNoNumber(param: String): String =
        string(R.string.error_script_argument_has_no_valid_number, ARGUMENT_NO_NUMBER, param)

    /** "Argument "[param]" must have a length of [expected], got [actual]" (P2.1). */
    @JvmStatic
    fun argumentLength(param: String, expected: Int, actual: Int): String =
        string(R.string.error_script_argument_must_have_length, ARGUMENT_LENGTH, param, expected, actual)

    /** "No integer lies within the range [[min], [max]]" (P2.1). */
    @JvmStatic
    fun rangeNoInteger(min: String, max: String): String =
        string(R.string.error_script_range_has_no_integer, RANGE_NO_INTEGER, min, max)

    /** "Unknown built-in object extension "[name]", expected one of [candidates]" (P2.1, jsox). */
    @JvmStatic
    fun unknownBuiltInExtension(name: String, candidates: String): String =
        string(R.string.error_script_unknown_built_in_extension, UNKNOWN_BUILT_IN_EXTENSION, name, candidates)

    /** "An operation is not implemented", the message of `TODO()` without a reason (P3.0, global). */
    @JvmStatic
    fun notImplemented(): String = string(R.string.error_an_operation_is_not_implemented, NOT_IMPLEMENTED)

    /**
     * Another message resource of the host, formatted like the messages above (P3.0: the modules of P3 reuse the
     * messages of their current implementation). [fallback] is the text of the default `values/strings.xml`, used
     * without an Android context; write it as a string literal right after the resource id, as in
     * `ScriptMessages.resource(R.string.name, "Text with %s", arg)`, so that `ScriptMessagesTest` can find every such
     * call in the sources of this package and compare the literal with the resource (and check the resource in every
     * language).
     */
    @JvmStatic
    fun resource(id: Int, fallback: String, vararg args: Any?): String = string(id, fallback, *args)

    private fun string(id: Int, fallback: String, vararg args: Any?): String {
        val texts = texts() ?: return format(Locale.ROOT, '0', fallback, args)
        return format(texts.locale, texts.zero, texts.string(id), args)
    }

    private fun plural(id: Int, quantity: Int, fallbackOne: String, fallbackOther: String): String {
        val texts = texts() ?: return format(Locale.ROOT, '0', if (quantity == 1) fallbackOne else fallbackOther, arrayOf(quantity))
        return format(texts.locale, texts.zero, texts.plural(id, quantity), arrayOf(quantity))
    }

    /** The raw texts of one resources object in one locale, each looked up once. */
    private class Texts(val resources: Resources, val locale: Locale) {

        /** The zero digit `java.util.Formatter` uses for `%d` in [locale]. */
        val zero: Char = zeroDigit(locale)

        private val strings = ConcurrentHashMap<Int, String>()
        private val plurals = ConcurrentHashMap<Long, String>()

        fun string(id: Int): String = strings.getOrPut(id) { resources.getString(id) }

        /** The plural form is chosen by the resources (the plural rules of [locale]), as `getQuantityString` does. */
        fun plural(id: Int, quantity: Int): String = plurals.getOrPut((id.toLong() shl 32) or (quantity.toLong() and 0xFFFFFFFFL)) {
            resources.getQuantityText(id, quantity).toString()
        }

    }

    @Volatile
    private var cached: Texts? = null

    /**
     * The texts for the current resources and configuration locale (a language change creates new ones), or null when
     * there is no application context or the platform stubs refuse the call (JVM unit tests).
     */
    private fun texts(): Texts? = try {
        val resources = GlobalAppContext.get().resources
        val locale = resources.configuration.locales.get(0)
        cached?.takeIf { it.resources === resources && it.locale == locale } ?: Texts(resources, locale).also { cached = it }
    } catch (e: RuntimeException) {
        null
    }

    /** The zero digit of [locale] for `%d`, as `java.util.Formatter` determines it. */
    @JvmStatic
    internal fun zeroDigit(locale: Locale): Char = if (locale == Locale.US) '0' else DecimalFormatSymbols.getInstance(locale).zeroDigit

    /**
     * `String.format(locale, pattern, *args)` for the specifiers of the message resources: `%s` / `%N$s` (the string
     * form of the argument), `%d` / `%N$d` (an Int or Long with the digits of [zero], no grouping) and `%%`. Any other
     * specifier, a missing argument or a non-integer `%d` argument goes to `String.format`, so the result is always the
     * same as the formatter's.
     */
    @JvmStatic
    internal fun format(locale: Locale, zero: Char, pattern: String, args: Array<out Any?>): String {
        var percent = pattern.indexOf('%')
        if (percent < 0) return pattern
        val out = StringBuilder(pattern.length + 48)
        var start = 0
        var sequential = 0
        while (percent >= 0) {
            out.append(pattern, start, percent)
            var i = percent + 1
            if (i < pattern.length && pattern[i] == '%') {
                out.append('%')
                start = i + 1
                percent = pattern.indexOf('%', start)
                continue
            }
            var index = -1
            var digits = i
            while (digits < pattern.length && pattern[digits] in '0'..'9') digits++
            if (digits > i && digits < pattern.length && pattern[digits] == '$') {
                index = pattern.substring(i, digits).toInt() - 1
                i = digits + 1
            }
            if (index < 0) index = sequential++
            val conversion = if (i < pattern.length) pattern[i] else ' '
            if (index !in args.indices) return String.format(locale, pattern, *args)
            val value = args[index]
            when {
                conversion == 's' && (value == null || value is CharSequence || value is Number || value is Boolean) -> out.append(value)
                conversion == 'd' && (value is Int || value is Long) -> appendInteger(out, (value as Number).toLong(), zero)
                else -> return String.format(locale, pattern, *args)
            }
            start = i + 1
            percent = pattern.indexOf('%', start)
        }
        out.append(pattern, start, pattern.length)
        return out.toString()
    }

    private fun appendInteger(out: StringBuilder, value: Long, zero: Char) {
        val text = value.toString()
        if (zero == '0') {
            out.append(text)
            return
        }
        text.forEach { c -> out.append(if (c in '0'..'9') zero + (c - '0') else c) }
    }

    // English fallbacks, identical to the default values/strings.xml and values/plurals.xml (checked by ScriptMessagesTest).

    internal const val FAILED_TO_CALL = "Failed to call method \"%1\$s\": [ %2\$s ]"
    internal const val EXACTLY_ONE = "Method only accepts %d argument"
    internal const val EXACTLY_OTHER = "Method only accepts %d arguments"
    internal const val NO_LESS_THAN_ONE = "Method only accepts no less than %d argument"
    internal const val NO_LESS_THAN_OTHER = "Method only accepts no less than %d arguments"
    internal const val IN_RANGE = "Method only accepts a number of arguments in the range [%1\$d..%2\$d]"
    internal const val ARGUMENT_TYPE = "Argument \"%1\$s\" must be of type %2\$s, got %3\$s"
    internal const val ARGUMENT_CANNOT_BE = "Argument \"%1\$s\" cannot be %2\$s"
    internal const val ARGUMENT_INTEGER = "Argument \"%1\$s\" must be a %2\$d-bit integer, got %3\$s"
    internal const val NO_OVERLOAD = "No overload accepts the arguments (%1\$s). Candidates: %2\$s"
    internal const val UNKNOWN_CHARSET = "Unknown charset \"%1\$s\""
    internal const val ARGUMENT_POSITIVE = "Argument \"%1\$s\" must be a positive number, got %2\$s"
    internal const val ARGUMENT_NO_NUMBER = "Argument \"%1\$s\" contains no valid number"
    internal const val ARGUMENT_LENGTH = "Argument \"%1\$s\" must have a length of %2\$d, got %3\$d"
    internal const val RANGE_NO_INTEGER = "No integer lies within the range [%1\$s, %2\$s]"
    internal const val UNKNOWN_BUILT_IN_EXTENSION = "Unknown built-in object extension \"%1\$s\", expected one of %2\$s"
    internal const val NOT_IMPLEMENTED = "An operation is not implemented"

}
