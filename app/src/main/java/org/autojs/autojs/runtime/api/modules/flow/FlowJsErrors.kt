package org.autojs.autojs.runtime.api.modules.flow

import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.flow.FlowTaskStack
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsString
import org.autojs.autojs.runtime.api.modules.util.Inspect.inspectRhino
import org.autojs.autojs.runtime.api.modules.util.Util
import org.mozilla.javascript.Context
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.WrappedException
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * How a rejection reaches JavaScript (roadmap Appendix A.4 rule 7): a [FlowError] becomes an
 * `Error` named `FlowError` with `code` / `step` / `selector` / `elapsed` / `attempts` / `reason`
 * and the Kotlin error as `javaException` (so `s13n.throwable(e)` recovers it); a value thrown by
 * script code stays what it was; a Rhino error is wrapped the way `catch` wraps it; any other Java
 * exception becomes an `Error` carrying it as `javaException`.
 * zh-CN: 拒绝如何到达 JavaScript (Roadmap 附录 A.4 第 7 条): [FlowError] 成为名为 `FlowError` 的 `Error`, 带 `code` / `step` /
 * `selector` / `elapsed` / `attempts` / `reason`, Kotlin 错误作为 `javaException` (因此 `s13n.throwable(e)` 能取回它); 脚本代码
 * 抛出的值保持原样; Rhino 错误按 `catch` 的方式包装; 其它 Java 异常成为以 `javaException` 携带它的 `Error`.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal object FlowJsErrors {

    const val NAME = "FlowError"

    private val originalStackKey = Any()

    /** The JavaScript value [error] is delivered as. zh-CN: [error] 交给 JavaScript 时的值. */
    fun toJs(cx: Context, scope: VarScope, error: Throwable): Any? {
        val value = when (error) {
            is JavaScriptException -> error.value
            is FlowError -> newFlowError(cx, scope, error)
            is EcmaError, is WrappedException, is EvaluatorException -> RhinoScriptRuntime.wrapException(error, scope, cx)
            is RhinoException -> newError(cx, scope, error.details(), error)
            else -> newError(cx, scope, error.toString(), error)
        }
        val taskStack = FlowTaskStack.format(error) ?: return value
        if (value is ScriptableObject) {
            // Frozen or custom thrown objects must retain their original identity and rejection.
            // zh-CN: 冻结对象或自定义抛出对象须保留原来的身份和拒绝语义.
            runCatching {
                val original = value.getAssociatedValue(originalStackKey) as? String ?: run {
                    val existing = ScriptableObject.getProperty(value, "stack") as? CharSequence
                    val description = Context.toString(value)
                    val stack = if (error is RhinoException && !existing.isNullOrBlank()) {
                        if (existing.startsWith(description)) existing.toString() else "$description\n$existing"
                    } else description + error.stackTrace.take(16).joinToString("\n    at ", prefix = "\n    at ")
                    value.associateValue(originalStackKey, stack.substringBefore("\nFlow task stack:")) as String
                }
                ScriptableObject.defineProperty(value, "flowStack", taskStack, DONTENUM)
                ScriptableObject.putProperty(value, "stack", "$original\n$taskStack")
            }
        }
        return value
    }

    /** Error logging retains async frames; unannotated values keep the usual console formatting.
     * zh-CN: 错误日志保留异步栈; 未附加诊断的值继续使用原控制台格式. */
    fun consoleValue(value: Any?): Any? = runCatching {
        if (value !is Scriptable || ScriptableObject.getProperty(value, "flowStack") !is CharSequence) value
        else (ScriptableObject.getProperty(value, "stack") as? CharSequence)?.toString() ?: value
    }.getOrDefault(value)

    fun formatConsole(args: Array<out Any?>): String {
        if (args.isEmpty() || args[0].isJsString()) {
            return Util.formatRhino(*args.map(::consoleValue).toTypedArray())
        }
        // An Error message containing "%s" is data, not a console format string.
        // zh-CN: Error 消息中的 "%s" 是数据, 不是控制台格式字符串.
        return args.joinToString(" ") { value ->
            val display = consoleValue(value)
            if (display !== value) display.toString() else inspectRhino(value)
        }
    }

    /**
     * Throws [error] into the calling script: a value thrown by script code is rethrown as it was,
     * anything else as a JavaScript exception carrying [toJs].
     * zh-CN: 把 [error] 抛给调用脚本: 脚本代码抛出的值原样重抛, 其余以携带 [toJs] 的 JavaScript 异常抛出.
     */
    fun throwInto(cx: Context, scope: VarScope, error: Throwable): Nothing {
        if (error is JavaScriptException) {
            toJs(cx, scope, error)
            throw error
        }
        val linep = intArrayOf(0)
        val sourceName = Context.getSourcePositionFromStack(linep)
        throw JavaScriptException(toJs(cx, scope, error), sourceName, linep[0])
    }

    private fun newFlowError(cx: Context, scope: VarScope, error: FlowError): Scriptable {
        val o = newError(cx, scope, error.message ?: "", error)
        ScriptableObject.putProperty(o, "name", NAME)
        ScriptableObject.putProperty(o, "code", error.code.name)
        ScriptableObject.putProperty(o, "step", error.step)
        ScriptableObject.putProperty(o, "selector", error.selector)
        ScriptableObject.putProperty(o, "elapsed", error.elapsed.toDouble())
        ScriptableObject.putProperty(o, "attempts", error.attempts.toDouble())
        ScriptableObject.putProperty(o, "reason", error.reason)
        return o
    }

    private fun newError(cx: Context, scope: VarScope, message: String, cause: Throwable): Scriptable {
        val o = cx.newObject(scope, "Error", arrayOf<Any?>(message))
        ScriptableObject.defineProperty(o, "javaException", Context.javaToJS(cause, scope), DONTENUM or READONLY or PERMANENT)
        return o
    }

}
