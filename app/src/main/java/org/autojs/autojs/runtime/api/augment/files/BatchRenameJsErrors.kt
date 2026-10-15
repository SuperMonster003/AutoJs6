package org.autojs.autojs.runtime.api.augment.files

import org.autojs.autojs.core.plugin.batchrename.BatchRenameScriptCodes
import org.autojs.autojs.core.plugin.batchrename.BatchRenameScriptException
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Context
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.Function
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.SlotMapOwner
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.WrappedException
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * How a `files.batchRename` failure reaches JavaScript (plugin roadmap D27): as an instance of the script-visible
 * `BatchRenameError` class (`name`, `code`, `message`, plus the Kotlin error as a hidden `javaException`), so
 * `e instanceof files.batchRename.BatchRenameError` and `e.code` work in `catch` and in Promise rejections. A value
 * thrown by script code (an `onProgress` callback) stays what it was. Same construction as `EpubError`.
 * zh-CN: `files.batchRename` 的失败如何到达 JavaScript (插件路线图 D27): 成为脚本可见的 `BatchRenameError` 类实例 (`name`, `code`,
 * `message`, 外加作为隐藏 `javaException` 的 Kotlin 错误), 因此 `catch` 与 Promise 拒绝里 `e instanceof files.batchRename.BatchRenameError`
 * 和 `e.code` 都可用. 脚本代码 (`onProgress` 回调) 抛出的值保持原样. 构造方式与 `EpubError` 相同.
 */
internal object BatchRenameJsErrors {

    const val NAME = "BatchRenameError"

    private val constructorKey = Any()

    private const val SOURCE = """(function () {
    function BatchRenameError(message, code) {
        if (!(this instanceof BatchRenameError)) {
            return new BatchRenameError(message, code);
        }
        var text = message === undefined || message === null ? '' : String(message);
        Object.defineProperty(this, 'message', { value: text, writable: true, configurable: true, enumerable: false });
        this.code = code === undefined || code === null ? 'INTERNAL' : String(code);
        var stack = new Error(text).stack;
        if (typeof stack === 'string') {
            Object.defineProperty(this, 'stack', { value: stack, writable: true, configurable: true, enumerable: false });
        }
    }
    BatchRenameError.prototype = Object.create(Error.prototype, {
        constructor: { value: BatchRenameError, writable: true, configurable: true },
        name: { value: 'BatchRenameError', writable: true, configurable: true },
        toString: {
            value: function () { return this.name + ' [' + this.code + ']: ' + this.message; },
            writable: true,
            configurable: true
        }
    });
    return BatchRenameError;
})()"""

    fun constructor(cx: Context, scope: VarScope): Function {
        val holder = scope as? SlotMapOwner<*>
        (holder?.getAssociatedValue(constructorKey) as? Function)?.let { return it }
        val created = cx.evaluateString(scope, SOURCE, "batch-rename-error.js", 1, null) as Function
        return holder?.associateValue(constructorKey, created) as? Function ?: created
    }

    fun constructor(scriptRuntime: ScriptRuntime): Function =
        RhinoUtils.withRhinoContext(scriptRuntime) { cx -> constructor(cx, scriptRuntime.topLevelScope) }

    private fun newError(cx: Context, scope: VarScope, code: String, message: String, cause: Throwable): Scriptable {
        val instance = constructor(cx, scope).construct(cx, scope, arrayOf(message, code))
        ScriptableObject.defineProperty(instance, "javaException", Context.javaToJS(cause, scope), DONTENUM or READONLY or PERMANENT)
        return instance
    }

    fun toJs(cx: Context, scope: VarScope, error: Throwable): Any? = when (error) {
        is JavaScriptException -> error.value
        is BatchRenameScriptException -> newError(cx, scope, error.code, error.message.orEmpty(), error)
        // Argument guards of the host raise this Rhino error. zh-CN: 宿主的参数守卫抛出这个 Rhino 错误.
        is WrappedIllegalArgumentException -> newError(cx, scope, BatchRenameScriptCodes.INVALID_OPTIONS, error.details(), error)
        is EcmaError, is WrappedException, is EvaluatorException -> RhinoScriptRuntime.wrapException(error, scope, cx)
        else -> newError(cx, scope, BatchRenameScriptCodes.INTERNAL, error.message ?: error.toString(), error)
    }

    fun toJs(scriptRuntime: ScriptRuntime, error: Throwable): Any? =
        RhinoUtils.withRhinoContext(scriptRuntime) { cx -> toJs(cx, scriptRuntime.topLevelScope, error) }

    /** Throws [error] into the calling script. zh-CN: 把 [error] 抛给调用脚本. */
    fun throwInto(scriptRuntime: ScriptRuntime, error: Throwable): Nothing {
        if (error is JavaScriptException) throw error
        throw RhinoUtils.withRhinoContext(scriptRuntime) { cx ->
            val linep = intArrayOf(0)
            val sourceName = Context.getSourcePositionFromStack(linep)
            JavaScriptException(toJs(cx, scriptRuntime.topLevelScope, error), sourceName, linep[0])
        }
    }
}
