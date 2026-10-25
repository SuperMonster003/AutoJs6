package org.autojs.autojs.runtime.api.modules.installer

import org.autojs.autojs.core.plugin.installer.InstallerError
import org.autojs.autojs.core.plugin.installer.InstallerErrorMapper
import org.autojs.autojs.core.plugin.installer.InstallerPluginException
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.installer.api.InstallerErrorCodes
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

/** All constructors and error conversions run on the calling script thread. */
internal object InstallerJsErrors {
    const val NAME = "InstallerError"
    private val constructorKey = Any()

    private const val SOURCE = """(function () {
    function InstallerError(message, code, status, systemMessage, packageName, authorizer, retryable) {
        if (!(this instanceof InstallerError)) {
            return new InstallerError(message, code, status, systemMessage, packageName, authorizer, retryable);
        }
        var text = message === undefined || message === null ? '' : String(message);
        Object.defineProperty(this, 'message', { value: text, writable: true, configurable: true });
        Object.defineProperty(this, 'code', { value: code == null ? 'INTERNAL' : String(code), enumerable: true });
        var fields = { status: status, systemMessage: systemMessage, packageName: packageName, authorizer: authorizer };
        var self = this;
        Object.keys(fields).forEach(function (key) {
            if (fields[key] !== undefined && fields[key] !== null) {
                Object.defineProperty(self, key, { value: fields[key], enumerable: true });
            }
        });
        Object.defineProperty(this, 'retryable', { value: !!retryable, enumerable: true });
        var stack = new Error(text).stack;
        if (typeof stack === 'string') {
            Object.defineProperty(this, 'stack', { value: stack, writable: true, configurable: true });
        }
    }
    InstallerError.prototype = Object.create(Error.prototype, {
        constructor: { value: InstallerError, writable: true, configurable: true },
        name: { value: 'InstallerError', writable: true, configurable: true },
        toString: {
            value: function () { return this.name + ' [' + this.code + ']: ' + this.message; },
            writable: true, configurable: true
        }
    });
    return InstallerError;
})()"""

    fun constructor(cx: Context, scope: VarScope): Function {
        val holder = scope as? SlotMapOwner<*>
        (holder?.getAssociatedValue(constructorKey) as? Function)?.let { return it }
        val created = cx.evaluateString(scope, SOURCE, "installer-error.js", 1, null) as Function
        return holder?.associateValue(constructorKey, created) as? Function ?: created
    }

    fun constructor(runtime: ScriptRuntime): Function =
        RhinoUtils.withRhinoContext(runtime) { cx -> constructor(cx, runtime.topLevelScope) }

    fun newInstallerError(cx: Context, scope: VarScope, error: InstallerError, cause: Throwable? = null): Scriptable {
        val instance = constructor(cx, scope).construct(cx, scope, arrayOf<Any?>(
            error.message, error.code, error.status, error.systemMessage, error.packageName, error.authorizer, error.retryable,
        ))
        if (cause != null) {
            ScriptableObject.defineProperty(instance, "javaException", Context.javaToJS(cause, scope), DONTENUM or READONLY or PERMANENT)
        }
        return instance
    }

    fun toJs(cx: Context, scope: VarScope, failure: Throwable): Any? = when (failure) {
        is JavaScriptException -> failure.value
        is WrappedIllegalArgumentException -> newInstallerError(cx, scope,
            InstallerErrorMapper.error(InstallerErrorCodes.INVALID_ARGUMENT, failure.details(), false), failure)
        is EcmaError, is WrappedException, is EvaluatorException -> RhinoScriptRuntime.wrapException(failure, scope, cx)
        is RhinoException -> newInstallerError(cx, scope,
            InstallerErrorMapper.error(InstallerErrorCodes.INTERNAL, failure.details(), false), failure)
        else -> newInstallerError(cx, scope, InstallerErrorMapper.fromThrowable(failure, InstallerErrorMapper.Phase.CALL), failure)
    }

    fun toJs(runtime: ScriptRuntime, failure: Throwable): Any? =
        RhinoUtils.withRhinoContext(runtime) { cx -> toJs(cx, runtime.topLevelScope, failure) }

    fun jsException(cx: Context, scope: VarScope, failure: Throwable): JavaScriptException {
        if (failure is JavaScriptException) return failure
        val line = intArrayOf(0)
        val source = Context.getSourcePositionFromStack(line)
        return JavaScriptException(toJs(cx, scope, failure), source, line[0])
    }

    fun throwInto(runtime: ScriptRuntime, failure: Throwable): Nothing {
        if (failure is JavaScriptException) throw failure
        throw RhinoUtils.withRhinoContext(runtime) { cx -> jsException(cx, runtime.topLevelScope, failure) }
    }

    fun failure(code: String, message: String): InstallerPluginException =
        InstallerPluginException(InstallerErrorMapper.error(code, message, false))
}
