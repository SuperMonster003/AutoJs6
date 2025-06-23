package org.autojs.autojs.extension

import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.extension.ArrayExtensions.toNativeObject
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.global.IsNullish
import org.autojs.autojs.runtime.api.augment.global.Species
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.js_typeof
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeError
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.UniqueTag.NOT_FOUND
import org.mozilla.javascript.Wrapper

object AnyExtensions {

    inline fun <reified T> Any.jsToJava(): T = Context.jsToJava(this, T::class.java) as T

    inline fun <reified T> Any.jsTryToJava(): Any = runCatching { jsToJava<T>() }.getOrNull() ?: this

    /**
     * 通过检查接收对象是否为 nullish (包括 `null` 或 JavaScript 的 `undefined`) 对其进行消毒.
     *
     * 此扩展函数特别适用于处理源自 JavaScript 环境的值, 在这些环境中, `null` 和 `undefined` 经常出现,
     * 并且在 Kotlin/Java 代码中需要特殊处理.
     *
     * 该函数使用 [isJsNullish] 方法来确定接收者是否为 nullish:
     * - `null`: 表示没有值.
     * - `undefined`: 通常表示未初始化的变量或不存在的属性 (NOT_FOUND).
     *
     * 如果接收者被判定为 nullish, 该函数返回 `null`. 否则, 它返回原始对象.
     *
     * 使用示例:
     * ```kotlin
     * val valueFromJs: Any? = // 从 JavaScript 环境中获取值
     * val sanitizedValue = valueFromJs.jsSanitize()
     * if (sanitizedValue != null) {
     *     // 处理非 nullish 值
     * } else {
     *     // 处理 nullish 值
     * }
     * ```
     *
     * en-US:
     *
     * Sanitizes the receiver object by checking if it is nullish (either `null` or JavaScript `undefined`).
     *
     * This extension function is particularly useful when handling values that originate from JavaScript environments,
     * where `null` and `undefined` are prevalent and often need special treatment in Kotlin/Java code.
     *
     * The function uses the [isJsNullish] method to determine if the receiver is nullish:
     * - `null`: Represents the absence of a value.
     * - `undefined`: Often signifies variables that have not been initialized or properties that do not exist (NOT_FOUND).
     *
     * If the receiver is determined to be nullish, the function returns `null`. Otherwise, it returns the original object.
     *
     * Usage example:
     * ```kotlin
     * val valueFromJs: Any? = // get value from JavaScript context
     * val sanitizedValue = valueFromJs.jsSanitize()
     * if (sanitizedValue != null) {
     *     // Handle non-nullish value
     * } else {
     *     // Handle nullish value
     * }
     * ```
     *
     * @receiver T? 待消毒的对象. 可以是任何类型, 包括可空类型.
     * (en-US: The object to be sanitized. It can be any type, including nullable types.)
     *
     * @return T? 如果接收者是 nullish (`null` 或 `undefined`), 则返回 `null`. 否则返回原始对象.
     * (en-US: Returns `null` if the receiver is nullish (`null` or `undefined`), otherwise returns the original object.)
     *
     * @see isJsNullish
     */
    fun <T> T?.jsSanitize() = if (this.isJsNullish()) null else this

    fun Any?.jsUnwrapped(): Any? = when (this) {
        is String -> this
        is ConsString -> this.toString()
        is Number -> Context.toNumber(this)
        is Boolean -> Context.toBoolean(this)
        is Wrapper -> this.unwrap().jsUnwrapped()
        is Unit -> UNDEFINED
        is List<*> -> this.toNativeArray()
        is Array<*> -> this.toNativeArray()
        is Map<*, *> -> this.toNativeObject()
        is Pair<*, *> -> newNativeObject().also { newObj ->
            newObj.put(Context.toString(this.first), newObj, this.second)
        }
        else -> this
    }

    fun Any?.jsSpecies() = Species.invoke(this)

    fun Any?.isJsNullish() = IsNullish.invoke(this)

    fun Any?.isJsArray() = this != NOT_FOUND && this is Scriptable && "Array" == this.className

    fun Any?.isJsBoolean() = this != NOT_FOUND && js_typeof(this) == "boolean"

    fun Any?.isJsNumber() = this != NOT_FOUND && js_typeof(this) == "number"

    fun Any?.isJsString() = this != NOT_FOUND && js_typeof(this) == "string"

    fun Any?.isJsSymbol() = this != NOT_FOUND && js_typeof(this) == "symbol"

    fun Any?.isJsUndefined() = this == NOT_FOUND || js_typeof(this) == "undefined"

    fun Any?.isJsObject() = this != NOT_FOUND && js_typeof(this) == "object" && this != null

    fun Any?.isJsNonNullObject() = this != null && this.isJsObject()

    fun Any?.isJsBigInt() = this != NOT_FOUND && js_typeof(this) == "bigint"

    fun Any?.isJsFunction() = this != NOT_FOUND && js_typeof(this) == "function"

    fun Any?.isJsXml() = this != NOT_FOUND && js_typeof(this) == "xml"

    fun Any?.isJsRegExp() = this.isJsObject() && Species.isRegExpRhino(this)

    fun Any?.isJsDate() = this.isJsObject() && Species.isDateRhino(this)

    fun Any?.isJsError() = this.isJsObject() && (Species.isErrorRhino(this) || this is NativeError)

    fun Any?.jsBrief() = "${Context.toString(this)} (${this.jsSpecies()})"

    fun Any?.toRuntimePath(scriptRuntime: ScriptRuntime, isStrict: Boolean = false): String {
        if (isStrict && this.isJsNullish()) throw IllegalArgumentException(str(R.string.error_cannot_convert_value_into_a_script_runtime_path, this.jsBrief()))
        return scriptRuntime.files.nonNullPath(coerceString(this, "."))
    }

}
