package org.autojs.autojs.runtime.api.augment.http

import org.autojs.autojs.annotation.RhinoStandardFunctionInterface
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.runtime.api.StringReadable
import org.autojs.autojs.runtime.api.augment.converter.core.Bytes
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable

@Suppress("unused")
class HttpSaveResult @JvmOverloads constructor(
    private val resultCode: Int,
    private val outPath: String?,
    private val bytesCopied: Long,
    private val error: Throwable? = null,
) : NativeObject(), StringReadable {

    init {
        RhinoUtils.initNativeObjectPrototype(this)
        defineProperty("code", { resultCode }, null, READONLY or PERMANENT)
        defineProperty("path", { outPath }, null, READONLY or PERMANENT)
        defineProperty("bytesCopied", { bytesCopied }, null, READONLY or PERMANENT)
        defineProperty("success", { resultCode == RESULT_OK }, null, READONLY or PERMANENT)
        defineProperty("error", { error }, null, READONLY or PERMANENT)
        defineFunctionProperties(arrayOf("isSuccess"), javaClass, READONLY or PERMANENT)
    }

    override fun toStringReadable(): String = listOf(
        "${HttpSaveResult::class.java.simpleName} {",
        "  code: ${resultCode},",
        "  bytesCopied: $bytesCopied (${Bytes.string(bytesCopied.toDouble(), useSpace = true, strict = true)}),",
        "  path: '${outPath}',",
        "  error: ${error?.message?.take(256)?.let { "'$it'" }},",
        "}",
    ).joinToString("\n")

    companion object : ArgumentGuards() {

        const val RESULT_OK = 0

        private const val RESULT_FAILED_GENERIC = -1

        fun ok(path: String, bytesCopied: Long) =
            HttpSaveResult(RESULT_OK, path, bytesCopied, null)

        fun fail(path: String?, bytesCopied: Long, e: Throwable?) =
            HttpSaveResult(RESULT_FAILED_GENERIC, path, bytesCopied, e)

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun isSuccess(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Boolean = ensureArgumentsIsEmpty(args) {
            val o = thisObj as HttpSaveResult
            o.resultCode == RESULT_OK
        }

    }
}