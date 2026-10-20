package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs6.R
import java.util.Locale

/**
 * Error messages of the script module layer (roadmap T5 / R4). They come from the host's string resources in the app
 * language; without an Android context (JVM unit tests) the English text of the default `values/strings.xml` is used,
 * which `ScriptMessagesTest` keeps equal to the resources.
 *
 * zh-CN: 脚本模块层的错误消息 (T5 / R4). 取自宿主字符串资源 (应用语言); 没有 Android Context 时 (JVM 单元测试) 使用默认 `values/strings.xml`
 * 的英文文本, 由 ScriptMessagesTest 保证与资源一致.
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

    private fun string(id: Int, fallback: String, vararg args: Any?): String =
        lookup { getString(id, *args) } ?: String.format(Locale.ROOT, fallback, *args)

    private fun plural(id: Int, quantity: Int, fallbackOne: String, fallbackOther: String): String =
        lookup { resources.getQuantityString(id, quantity, quantity) } ?: String.format(Locale.ROOT, if (quantity == 1) fallbackOne else fallbackOther, quantity)

    /** Null when there is no application context or the platform stubs refuse the call (JVM unit tests). */
    private inline fun lookup(block: android.content.Context.() -> String?): String? = try {
        GlobalAppContext.get().block()
    } catch (e: RuntimeException) {
        null
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

}
