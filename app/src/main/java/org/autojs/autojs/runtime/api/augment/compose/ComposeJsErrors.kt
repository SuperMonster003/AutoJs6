package org.autojs.autojs.runtime.api.augment.compose

import org.autojs.autojs.core.plugin.compose.ComposeUiError
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.mozilla.javascript.*
import org.mozilla.javascript.Function

/** Errors belong to the script's scope; no Rhino object crosses the renderer contract. */
internal object ComposeJsErrors {
    private val constructorKey = Any()
    private const val SOURCE = """(function () {
        function ComposeError(message, code, nodeId, prop, cause) {
            if (!(this instanceof ComposeError)) return new ComposeError(message, code, nodeId, prop, cause);
            Object.defineProperty(this, 'message', {value: String(message || ''), writable: true, configurable: true});
            this.code = code || 'INVALID_ARGUMENT';
            this.nodeId = nodeId == null ? null : nodeId;
            this.prop = prop == null ? null : prop;
            this.cause = cause == null ? null : cause;
            this.retryable = ['PLUGIN_UNAVAILABLE','PLUGIN_DISABLED','PLUGIN_UNAUTHORIZED','PLUGIN_INCOMPATIBLE'].indexOf(this.code) >= 0;
            Object.defineProperty(this, 'stack', {value: new Error(this.message).stack, configurable: true});
        }
        ComposeError.prototype = Object.create(Error.prototype, {
            constructor: {value: ComposeError, writable: true, configurable: true},
            name: {value: 'ComposeError', configurable: true}
        });
        return ComposeError;
    })()"""

    fun constructor(scope: VarScope): Function = RhinoUtils.withRhinoContext { cx ->
        val holder = scope as? SlotMapOwner<*>
        (holder?.getAssociatedValue(constructorKey) as? Function) ?: (cx.evaluateString(scope, SOURCE, "compose-error.js", 1, null) as Function)
            .let { holder?.associateValue(constructorKey, it) as? Function ?: it }
    }

    fun exception(scope: VarScope, failure: Throwable): RuntimeException {
        if (failure is ContinuationPending) return failure
        if (failure is RuntimeException && org.autojs.autojs.runtime.exception.ScriptInterruptedException.causedByInterrupt(failure)) return failure
        if (failure is JavaScriptException) return failure
        val error = when (failure) {
            is ComposeUiError -> failure
            is ComposeUiContractException -> ComposeUiError.from(failure)
            is IllegalArgumentException -> ComposeUiError(ComposeUiErrorCodes.INVALID_ARGUMENT, failure.message ?: "Invalid Compose argument", cause = failure)
            else -> ComposeUiError(ComposeUiErrorCodes.RENDER_FAILED, failure.message ?: "Compose operation failed", cause = failure)
        }
        return RhinoUtils.withRhinoContext { cx ->
            val cause = when (val original = error.cause) {
                is JavaScriptException -> original.value
                is RhinoException -> cx.newObject(scope, "Error", arrayOf(original.details())).also {
                    ScriptableObject.putProperty(it, "stack", original.scriptStackTrace)
                }
                null -> null
                else -> cx.newObject(scope, "Error", arrayOf(original.message ?: original.javaClass.simpleName))
            }
            val value = constructor(scope).construct(cx, scope, arrayOf(error.message, error.code, error.nodeId, error.prop, cause))
            val line = intArrayOf(0)
            JavaScriptException(value, Context.getSourcePositionFromStack(line), line[0])
        }
    }

    fun <T> guard(scope: VarScope, action: () -> T): T = try { action() }
    catch (failure: Exception) { throw exception(scope, failure) }
    catch (failure: LinkageError) { throw exception(scope, failure) }
}

internal fun composeArguments(args: Array<out Any?>, min: Int, max: Int = min, name: String) {
    if (args.size !in min..max) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT,
        "$name expects ${if (min == max) "$min" else "$min..$max"} arguments")
}

internal fun composeFunction(value: Any?, name: String): Function = value as? Function
    ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "$name requires a function")

internal fun composeNullish(value: Any?) = value == null || value is Undefined || value === Scriptable.NOT_FOUND
