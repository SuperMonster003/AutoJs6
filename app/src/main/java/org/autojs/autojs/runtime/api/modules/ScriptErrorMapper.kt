package org.autojs.autojs.runtime.api.modules

import android.util.Log
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
 * Nothing is printed unless [debugPrinting] allows it for the module (appendix C Q4: a master switch, off by default in
 * debug and release builds, plus a per-module list, all on by default; `ScriptModuleLayer` backs it with hidden
 * preferences until P1.3 adds the developer options). When it does, mapping a Java exception writes a D-level entry to
 * the script console of the runtime ([debugEntry]: the member, the exception and its frames in the modules package,
 * never the argument values), also when the script catches the exception, and the full stack trace to logcat. Argument
 * errors and script errors pass through above and are never printed.
 *
 * zh-CN: 脚本模块层的错误模型 (D10 / T5), 作用于模块函数, getter, setter 与构造函数抛出的一切: Rhino 异常原样透传 (脚本抛出的错误, 包括模块调用的回调,
 * 以及生成层参数转换的 TypeError / RangeError); continuation 捕获, 脚本中断与 JVM 错误原样透传; 其他 Java 异常保留类型 (脚本以 JavaException 捕获,
 * `e.javaException` 为原始对象), "方法调用失败" 前缀写入 Rhino 异常的 details (未捕获时控制台显示). 只有 [debugPrinting] 对该模块放行时才打印
 * (附录 C Q4: 总开关在 debug 与 release 构建中都默认关, 按模块列表默认全开; P1.3 加开发者选项之前由 ScriptModuleLayer 以隐藏偏好设置支撑): 映射 Java
 * 异常时向运行时的脚本控制台写一条 D 级条目 ([debugEntry]: 成员, 异常及其在模块包内的帧, 不含参数值), 脚本捕获了异常也照样写, 完整堆栈写入 logcat.
 * 参数错误与脚本错误在上面原样透传, 任何状态下都不打印.
 */
object ScriptErrorMapper {

    /** Decides whether the Java exceptions of a top-level module (by name) print their stack traces. */
    fun interface DebugPrinting {
        fun enabledFor(module: String): Boolean
    }

    /** Receives the debug entries: the script console of a runtime (`Console.println(level, text)`). */
    fun interface DebugConsole {
        fun println(level: Int, text: CharSequence)
    }

    @JvmStatic
    @Volatile
    var debugPrinting: DebugPrinting = DebugPrinting { false }

    private const val TAG = "ScriptModules"
    private const val MODULES_PACKAGE = "org.autojs.autojs.runtime.api.modules."

    /**
     * The throwable to rethrow for [e], thrown by the member [method] (as `base64.encode`) of the top-level module
     * [module] (the module itself for a sub-module's member, as `util` for `util.inspect`); [console] receives the
     * debug entry when [debugPrinting] allows it.
     */
    @JvmStatic
    @JvmOverloads
    fun map(e: Throwable, method: String, module: String = method.substringBefore('.'), console: DebugConsole? = null): Throwable = when {
        e is RhinoException -> e
        e is ContinuationPending -> e
        e is ScriptInterruptedException -> e
        ScriptInterruptedException.causedByInterrupt(e) -> ScriptInterruptedException(e)
        e is VirtualMachineError -> e
        else -> {
            if (debugPrinting.enabledFor(module)) debug(method, e, console)
            ScriptCallException(method, e)
        }
    }

    /**
     * The console entry for [e] thrown by [method]: `[modules] base64.decode: java.lang.IllegalArgumentException:
     * bad base-64`, then the frames of the modules package (`    at ...`) and `    ... N more` for the other frames.
     */
    @JvmStatic
    fun debugEntry(method: String, e: Throwable): String = buildString {
        append("[modules] ").append(method).append(": ").append(e.javaClass.name)
        e.message?.let { append(": ").append(it) }
        val frames = e.stackTrace
        val kept = frames.filter { it.className.startsWith(MODULES_PACKAGE) }
        kept.forEach { appendLine().append("    at ").append(it) }
        if (frames.size > kept.size) appendLine().append("    ... ").append(frames.size - kept.size).append(" more")
    }

    /** A failing console or logcat never replaces the error being mapped. */
    private fun debug(method: String, e: Throwable, console: DebugConsole?) {
        try {
            console?.println(Log.DEBUG, debugEntry(method, e))
        } catch (t: RuntimeException) {
            t.printStackTrace()
        }
        try {
            Log.d(TAG, "[modules] $method", e)
        } catch (t: RuntimeException) {
            // No android.util.Log on the plain JVM (unit tests).
            e.printStackTrace()
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
