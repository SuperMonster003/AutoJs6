package org.autojs.autojs.runtime.api.modules.util

import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * Value rules shared by the util modules of the new script module layer (roadmap P2.3): JS `typeof`, nullish values and
 * the errors (D10: wrong types are TypeErrors, values out of the accepted set RangeErrors, prefixed with the function).
 *
 * zh-CN: 新脚本模块层 util 各模块共用的取值规则 (P2.3): JS typeof, nullish 判断与错误 (D10).
 */
internal object UtilValues {

    fun isNullish(value: Any?): Boolean = value == null || Undefined.isUndefined(value) || value === Scriptable.NOT_FOUND

    /** The JS `typeof` of a script value (a wrapped Java object is "object", a Java class "function"). */
    fun typeOf(value: Any?): String = if (value === Scriptable.NOT_FOUND) "undefined" else RhinoScriptRuntime.`typeof`(value)

    fun typeError(function: String, param: String, expected: String, value: Any?): RuntimeException =
        RhinoScriptRuntime.typeError(ScriptMessages.failedToCall(function, ScriptMessages.argumentType(param, expected, ScriptArgs.describe(value))))

    fun typeError(function: String, cause: String): RuntimeException = RhinoScriptRuntime.typeError(ScriptMessages.failedToCall(function, cause))

    fun rangeError(function: String, cause: String): RuntimeException = RhinoScriptRuntime.rangeError(ScriptMessages.failedToCall(function, cause))

    /** A value for a message: text quoted, numbers in their JS form, others described by type. */
    fun quote(value: Any?): String = when (value) {
        null -> "null"
        is CharSequence -> "\"$value\""
        is Number -> RhinoScriptRuntime.numberToString(value.toDouble(), 10)
        else -> ScriptArgs.describe(value)
    }

}
