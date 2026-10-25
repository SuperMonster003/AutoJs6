package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.autojs.core.plugin.compose.ComposeUiError
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.mozilla.javascript.*
import org.mozilla.javascript.Function
import java.util.IdentityHashMap

/** Errors belong to the script's scope; no Rhino object crosses the renderer contract. */
internal object ComposeJsErrors {
    private val constructorKey = Any()
    private val localizerKey = Any()
    private class Localizer(var format: (ComposeUiError) -> String)
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

    /** Stored in this realm, so a stopped engine and its platform can be collected together. */
    fun installLocalizer(scope: VarScope, format: (ComposeUiError) -> String) {
        val holder = scope as? SlotMapOwner<*> ?: return
        (holder.associateValue(localizerKey, Localizer(format)) as Localizer).format = format
    }

    private fun message(scope: VarScope, error: ComposeUiError): String {
        val localizer = (scope as? SlotMapOwner<*>)?.getAssociatedValue(localizerKey) as? Localizer
        return try { localizer?.format?.invoke(error) ?: error.message ?: error.code }
        catch (_: RuntimeException) { error.message ?: error.code }
    }

    fun exception(scope: VarScope, failure: Throwable): RuntimeException {
        if (failure is ContinuationPending) return failure
        if (interrupted(failure)) return failure as? RuntimeException ?: ScriptInterruptedException(failure)
        if (failure is JavaScriptException) return failure
        val error = when (failure) {
            is ComposeUiError -> failure
            is ComposeUiContractException -> ComposeUiError.from(failure)
            is IllegalArgumentException -> ComposeUiError(ComposeUiErrorCodes.INVALID_ARGUMENT, failure.message ?: "Invalid Compose argument", cause = failure)
            else -> ComposeUiError(ComposeUiErrorCodes.RENDER_FAILED, failure.message ?: "Compose operation failed", cause = failure)
        }
        return RhinoUtils.withRhinoContext { cx ->
            val cause = cause(cx, scope, error.cause)
            val value = constructor(scope).construct(cx, scope, arrayOf(message(scope, error), error.code, error.nodeId, error.prop, cause))
            val line = intArrayOf(0)
            JavaScriptException(value, Context.getSourcePositionFromStack(line), line[0])
        }
    }

    private fun interrupted(failure: Throwable): Boolean {
        val visited = IdentityHashMap<Throwable, Boolean>()
        var current: Throwable? = failure
        while (current != null && visited.put(current, true) == null) {
            if (current is ScriptInterruptedException || current is InterruptedException) return true
            current = current.cause
        }
        return false
    }

    /** Iterative conversion preserves wrapper causes without recursing on cyclic host exceptions. */
    private fun cause(context: Context, scope: VarScope, original: Throwable?): Any? {
        val converted = IdentityHashMap<Throwable, Any?>()
        var current = original
        var first: Any? = null
        var previous: Scriptable? = null
        while (current != null) {
            val failure = current
            if (converted.containsKey(failure)) {
                previous?.let { ScriptableObject.putProperty(it, "cause", converted[failure]) }
                break
            }
            val value = when (failure) {
                is JavaScriptException -> failure.value
                else -> context.newObject(scope, "Error", arrayOf(
                    if (failure is RhinoException) failure.details() else failure.message ?: failure.javaClass.simpleName,
                )).also { result ->
                    if (failure is RhinoException) ScriptableObject.putProperty(result, "stack", failure.scriptStackTrace)
                    when (failure) {
                        is ComposeUiError -> {
                            ScriptableObject.putProperty(result, "code", failure.code)
                            ScriptableObject.putProperty(result, "nodeId", failure.nodeId)
                            ScriptableObject.putProperty(result, "prop", failure.prop)
                        }
                        is ComposeUiContractException -> {
                            ScriptableObject.putProperty(result, "code", failure.code)
                            ScriptableObject.putProperty(result, "nodeId", failure.nodeId)
                            ScriptableObject.putProperty(result, "prop", failure.prop)
                        }
                    }
                }
            }
            converted[failure] = value
            if (previous == null) first = value else ScriptableObject.putProperty(previous, "cause", value)
            if (failure is JavaScriptException) break
            previous = value as Scriptable
            current = failure.cause
        }
        return first
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
