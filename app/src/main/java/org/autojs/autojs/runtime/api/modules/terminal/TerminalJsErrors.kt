package org.autojs.autojs.runtime.api.modules.terminal

import org.autojs.autojs.core.plugin.terminal.TerminalError
import org.autojs.autojs.core.plugin.terminal.TerminalErrorMapper
import org.autojs.autojs.core.plugin.terminal.TerminalPluginException
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.terminal.api.TerminalErrorCodes
import org.mozilla.javascript.Context
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.Function
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.RhinoException
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
 * How a terminal failure reaches JavaScript (plugin roadmap P4.1, appendix A.4): every host or
 * plugin failure becomes an instance of the script-visible `TerminalError` class (`name`, `code`,
 * `message`, `sessionId` when the failure concerns one session, plus the Kotlin error as a hidden
 * `javaException`), so `e instanceof terminal.TerminalError` and `e.code` work in `catch` and in
 * Promise rejections. A value thrown by script code stays what it was; a Rhino error is wrapped
 * the way `catch` wraps it. The constructor is defined once per top-level scope from [SOURCE]
 * and cached on the scope.
 * zh-CN: 终端失败如何到达 JavaScript (插件路线图 P4.1, 附录 A.4): 宿主或插件的每个失败都成为脚本可见的 `TerminalError` 类实例 (`name`, `code`,
 * `message`, 涉及单个会话时还有 `sessionId`, 外加作为隐藏 `javaException` 的 Kotlin 错误), 因此 `catch` 与 Promise 拒绝里
 * `e instanceof terminal.TerminalError` 和 `e.code` 都可用. 脚本代码抛出的值保持原样; Rhino 错误按 `catch` 的方式包装. 构造器按顶层作用域从 [SOURCE]
 * 定义一次并缓存在作用域上.
 */
internal object TerminalJsErrors {

    const val NAME = "TerminalError"

    private val constructorKey = Any()

    private const val SOURCE = """(function () {
    function TerminalError(message, code, sessionId) {
        if (!(this instanceof TerminalError)) {
            return new TerminalError(message, code, sessionId);
        }
        var text = message === undefined || message === null ? '' : String(message);
        Object.defineProperty(this, 'message', { value: text, writable: true, configurable: true, enumerable: false });
        this.code = code === undefined || code === null ? 'INTERNAL' : String(code);
        if (sessionId !== undefined && sessionId !== null) {
            this.sessionId = String(sessionId);
        }
        var stack = new Error(text).stack;
        if (typeof stack === 'string') {
            Object.defineProperty(this, 'stack', { value: stack, writable: true, configurable: true, enumerable: false });
        }
    }
    TerminalError.prototype = Object.create(Error.prototype, {
        constructor: { value: TerminalError, writable: true, configurable: true },
        name: { value: 'TerminalError', writable: true, configurable: true },
        toString: {
            value: function () { return this.name + ' [' + this.code + ']: ' + this.message; },
            writable: true,
            configurable: true
        }
    });
    return TerminalError;
})()"""

    /** The `TerminalError` constructor of [scope], created on first use. zh-CN: [scope] 的 `TerminalError` 构造器, 首次使用时创建. */
    fun constructor(cx: Context, scope: VarScope): Function {
        val holder = scope as? SlotMapOwner<*>
        (holder?.getAssociatedValue(constructorKey) as? Function)?.let { return it }
        val created = cx.evaluateString(scope, SOURCE, "terminal-error.js", 1, null) as Function
        return holder?.associateValue(constructorKey, created) as? Function ?: created
    }

    fun constructor(scriptRuntime: ScriptRuntime): Function =
        RhinoUtils.withRhinoContext(scriptRuntime) { cx -> constructor(cx, scriptRuntime.topLevelScope) }

    /** A `TerminalError` instance for [error]; [cause] rides along as `javaException`. zh-CN: [error] 的 `TerminalError` 实例; [cause] 作为 `javaException` 随行. */
    fun newTerminalError(cx: Context, scope: VarScope, error: TerminalError, cause: Throwable?): Scriptable {
        val instance = constructor(cx, scope).construct(cx, scope, arrayOf(error.message, error.code, error.sessionId))
        if (cause != null) {
            ScriptableObject.defineProperty(instance, "javaException", Context.javaToJS(cause, scope), DONTENUM or READONLY or PERMANENT)
        }
        return instance
    }

    /** The JavaScript value [error] is delivered as. zh-CN: [error] 交给 JavaScript 时的值. */
    fun toJs(cx: Context, scope: VarScope, error: Throwable): Any? = when (error) {
        is JavaScriptException -> error.value
        // Argument guards of the host raise this Rhino error; scripts expect a TerminalError with INVALID_ARGUMENT.
        // zh-CN: 宿主的参数守卫抛出这个 Rhino 错误; 脚本期望带 INVALID_ARGUMENT 的 TerminalError.
        is WrappedIllegalArgumentException -> newTerminalError(cx, scope, TerminalErrorMapper.normalize(TerminalErrorCodes.INVALID_ARGUMENT, error.details()), error)
        is EcmaError, is WrappedException, is EvaluatorException -> RhinoScriptRuntime.wrapException(error, scope, cx)
        is RhinoException -> newTerminalError(cx, scope, TerminalErrorMapper.normalize(null, error.details()), error)
        else -> newTerminalError(cx, scope, TerminalErrorMapper.fromThrowable(error, TerminalErrorMapper.Phase.CALL), error)
    }

    fun toJs(scriptRuntime: ScriptRuntime, error: Throwable): Any? =
        RhinoUtils.withRhinoContext(scriptRuntime) { cx -> toJs(cx, scriptRuntime.topLevelScope, error) }

    /** The exception that throws [toJs] of [error] into the calling script. zh-CN: 把 [error] 的 [toJs] 抛给调用脚本的异常. */
    fun jsException(cx: Context, scope: VarScope, error: Throwable): JavaScriptException {
        if (error is JavaScriptException) return error
        val linep = intArrayOf(0)
        val sourceName = Context.getSourcePositionFromStack(linep)
        return JavaScriptException(toJs(cx, scope, error), sourceName, linep[0])
    }

    /** Throws [error] into the calling script. zh-CN: 把 [error] 抛给调用脚本. */
    fun throwInto(scriptRuntime: ScriptRuntime, error: Throwable): Nothing {
        if (error is JavaScriptException) throw error
        throw RhinoUtils.withRhinoContext(scriptRuntime) { cx -> jsException(cx, scriptRuntime.topLevelScope, error) }
    }

    /** A host-side failure with [code] as the script sees it. zh-CN: 脚本眼中带 [code] 的宿主侧失败. */
    fun failure(code: String, message: String, sessionId: String? = null): TerminalPluginException =
        TerminalPluginException(TerminalErrorMapper.normalize(code, message, sessionId))
}
