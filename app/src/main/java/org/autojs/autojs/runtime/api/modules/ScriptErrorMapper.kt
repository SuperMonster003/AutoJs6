package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.WrappedException

/**
 * The error model of the script module layer (roadmap D10 / T5), applied to everything a module function, getter,
 * setter or constructor throws:
 *
 * - Rhino exceptions pass through unchanged: errors thrown by scripts (`JavaScriptException`, also from callbacks the
 *   module calls) and the `TypeError` / `RangeError` of the generated argument conversion (`EcmaError`).
 * - Continuation captures and script interruptions pass through (an interruption found in the cause chain becomes a
 *   `ScriptInterruptedException`), as do JVM errors such as `StackOverflowError`.
 * - Any other Java exception keeps its type: scripts catch it as `JavaException` with the original object in
 *   `e.javaException`; the "Failed to call method" prefix goes into the Rhino exception's details, which the console
 *   shows for an uncaught error.
 *
 * Nothing is printed unless [debugPrinting] allows it for the module (appendix C Q4: a master switch, off by default,
 * plus a per-module list, all on by default; `ScriptModuleLayer` backs it with hidden preferences until P1.3 adds the
 * developer options).
 *
 * zh-CN: 脚本模块层的错误模型 (D10 / T5), 作用于模块函数, getter, setter 与构造函数抛出的一切: Rhino 异常原样透传 (脚本抛出的错误, 包括模块调用的回调,
 * 以及生成层参数转换的 TypeError / RangeError); continuation 捕获, 脚本中断与 JVM 错误原样透传; 其他 Java 异常保留类型 (脚本以 JavaException 捕获,
 * `e.javaException` 为原始对象), "方法调用失败" 前缀写入 Rhino 异常的 details (未捕获时控制台显示). 只有 [debugPrinting] 对该模块放行时才打印堆栈
 * (附录 C Q4: 总开关默认关, 按模块列表默认全开; P1.3 加开发者选项之前由 ScriptModuleLayer 以隐藏偏好设置支撑).
 */
object ScriptErrorMapper {

    /** Decides whether the Java exceptions of a top-level module (by name) print their stack traces. */
    fun interface DebugPrinting {
        fun enabledFor(module: String): Boolean
    }

    @JvmStatic
    @Volatile
    var debugPrinting: DebugPrinting = DebugPrinting { false }

    /**
     * The throwable to rethrow for [e], thrown by the member [method] (as `base64.encode`) of the top-level module
     * [module] (the module itself for a sub-module's member, as `util` for `util.inspect`).
     */
    @JvmStatic
    @JvmOverloads
    fun map(e: Throwable, method: String, module: String = method.substringBefore('.')): Throwable = when {
        e is RhinoException -> e
        e is ContinuationPending -> e
        e is ScriptInterruptedException -> e
        ScriptInterruptedException.causedByInterrupt(e) -> ScriptInterruptedException(e)
        e is VirtualMachineError -> e
        else -> {
            if (debugPrinting.enabledFor(module)) e.printStackTrace()
            ScriptCallException(method, e)
        }
    }

}

/**
 * A Java exception thrown by a module member, wrapped for Rhino without losing its type ([getWrappedException]); the
 * details name the member.
 */
class ScriptCallException(val method: String, cause: Throwable) : WrappedException(cause) {

    override fun details(): String = wrappedException.let { e ->
        ScriptMessages.failedToCall(method, e.message?.let { "${e.javaClass.name}: $it" } ?: e.javaClass.name)
    }

}
