package org.autojs.autojs.core.automator.pick

import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs6.R
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import java.util.Locale
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The messages of the argument errors of `pickup` / `detect` and the selector forms read like theirs (docs/selector/
 * ROADMAP.md P1.1, appendix C Q5 (2): five resources of their own, not those of the script module layer), and the texts
 * of the reasons they quote (existing resources of the readers they replace). They come from the host's string
 * resources in the app language; without an Android context (JVM unit tests) the English text of the default
 * `values/strings.xml` is used, which `PickMessagesTest` keeps equal to the resources. Built on the error path only.
 *
 * zh-CN: `pickup` / `detect` 及按其方式读取的选择器形式的参数错误消息 (P1.1, 附录 C Q5 (2): 自有的五条资源, 不用脚本模块层的消息), 以及其中引用的原因文本
 * (被取代的读取方已有的资源). 取自宿主字符串资源 (应用语言); 没有 Android Context 时 (JVM 单元测试) 使用默认 `values/strings.xml` 的英文文本, 由
 * `PickMessagesTest` 保证与资源一致. 只在出错时构造.
 *
 * Created by SuperMonster003 on Oct 25, 2026.
 */
internal object PickMessages {

    /** "[function] accepts [min] to [max] arguments, got [actual]". zh-CN: 参数个数不符. */
    fun argumentCount(function: String, min: Int, max: Int, actual: Int): String =
        resource(R.string.error_pick_argument_count, "%1\$s accepts %2\$d to %3\$d arguments, got %4\$d", function, min, max, actual)

    /** "[function]: argument "[param]" must be of type [expected], got (the type of [value])". zh-CN: 参数类型不符. */
    fun argumentType(function: String, param: String, expected: String, value: Any?): String =
        resource(R.string.error_pick_argument_type, "%1\$s: argument \"%2\$s\" must be of type %3\$s, got %4\$s", function, param, expected, describe(value))

    /** "[function]: argument "[param]" cannot be [value] ([reason])". zh-CN: 参数的值被拒绝. */
    fun argumentValue(function: String, param: String, value: Any?, reason: String): String =
        resource(R.string.error_pick_argument_value, "%1\$s: argument \"%2\$s\" cannot be %3\$s (%4\$s)", function, param, show(value), reason)

    /** A result type that is no symbol and no registry method, with [candidates] (D6 (b)). zh-CN: 既非符号也非注册表方法的结果类型, 附候选 (D6 (b)). */
    fun unknownResultType(function: String, param: String, value: Any?, candidates: List<String>): String = resource(
        R.string.error_pick_unknown_result_type, "%1\$s: argument \"%2\$s\": %3\$s is neither a result type nor a method of UiObject, candidates: %4\$s",
        function, param, show(value), candidates.joinToString(", "),
    )

    /** A key of an object selector that is no condition, with [candidates]. zh-CN: 对象选择器中不是条件的键, 附候选. */
    fun unknownCondition(function: String, param: String, key: String, candidates: List<String>): String = resource(
        R.string.error_pick_unknown_condition, "%1\$s: argument \"%2\$s\": %3\$s is not a condition of UiSelector, candidates: %4\$s",
        function, param, show(key), candidates.joinToString(", "),
    )

    /** The reason of a string that is no compass. zh-CN: 不是罗盘的字符串的原因. */
    fun invalidCompass(compass: String): String = resource(R.string.error_invalid_compass, "Invalid compass: %s", compass)

    /** The reason of a compass whose [rest] does not parse. zh-CN: 剩余部分无法解析的罗盘的原因. */
    fun invalidRestCompass(rest: String): String = resource(R.string.error_invalid_rest_compass, "Invalid rest compass: %s", rest)

    /** The reason of the selector result type given with a compass. zh-CN: 选择器结果类型与罗盘同时给出的原因. */
    fun selectorWithCompass(): String = resource(R.string.error_selector_result_type_with_compass, "Cannot use a compass when result type is selector")

    /** The reason of a plural result type for one node (`detect`). zh-CN: 单个节点 (`detect`) 的复数结果类型的原因. */
    fun notForOneNode(resultType: Any?): String =
        resource(R.string.error_illegal_ui_object_result_type, "Result type \"%s\" is not supported for UiObject", plain(resultType))

    /** The reason of an unknown `algorithm` of a selector. zh-CN: 选择器的未知 `algorithm` 的原因. */
    fun unknownAlgorithm(name: String): String = resource(R.string.error_unknown_algorithm_selector_param, "Unknown algorithm selector param: %s", name)

    /** The reason of a selector method passed without calling it. zh-CN: 未调用而直接传入的选择器方法的原因. */
    fun methodNotCalled(name: String): String =
        resource(R.string.error_selector_method_without_calling, "Selector method should be called rather than passed as a parameter: %s", name)

    /**
     * A host message resource formatted with [args] as `Resources.getString(id, args)` formats it, or [fallback] (the
     * text of the default `values/strings.xml`) without an Android context.
     */
    private fun resource(id: Int, fallback: String, vararg args: Any?): String = try {
        GlobalAppContext.get().getString(id, *args)
    } catch (e: RuntimeException) {
        String.format(Locale.ROOT, fallback, *args)
    }

    /** The type of [value] as the messages name it: `null`, `undefined`, `string`, `number`, `Array`, a class name ... */
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

    /** [value] as the messages show it: a string quoted, a number in JS form, a list as `[a, b]`, else its type. */
    fun show(value: Any?): String = when (value) {
        null -> "null"
        is CharSequence -> "\"$value\""
        is Number -> RhinoScriptRuntime.numberToString(value.toDouble(), 10)
        is List<*> -> value.joinToString(", ", "[", "]") { show(it) }
        else -> describe(value)
    }

    /** [value] as the readers this replaces printed it (`toString`, a list as `[a, b]`). */
    private fun plain(value: Any?): String = if (value is List<*>) value.joinToString(", ", "[", "]") { plain(it) } else value.toString()

}
