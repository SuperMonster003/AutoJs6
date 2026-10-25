package org.autojs.autojs.runtime.api.modules.timers

import org.autojs.autojs.AutoJs
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.autojs.autojs.runtime.api.modules.tasks.TaskArgs
import org.autojs.autojs.runtime.api.modules.tasks.WorkManager
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs6.R
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeObject

/**
 * The `timers` module on the new script module layer (roadmap P3.4): the module object's prototype is the runtime's Java
 * `Timers` (`ScriptRuntime.timers`), as with the current module, so `timers.setTimeout` and the other Java methods stay
 * readable on it; `setTimeout`, `setInterval`, `setImmediate`, their `clear` functions and the abandoned `loop` are
 * global names only, `keepAlive` is both, and the persistent task entries (6.8.0) are the ones of `workManager` (same
 * implementation and argument rules, [TaskArgs]).
 *
 * Arguments follow the real signatures (T4) with the error model of the new layer: a callback that is not a function is
 * a TypeError, a delay or id without a number a TypeError and NaN or an infinite one a RangeError (D10, appendix C Q12;
 * the current module threw IllegalArgumentException); a nullish delay is the documented default. `setIntervalExt` takes a
 * nullish interval or timeout as omitted, as documented and as the obsolete JS did (the current module refused null).
 *
 * zh-CN: 新脚本模块层上的 timers 模块 (P3.4): 模块对象的原型是运行时的 Java Timers (ScriptRuntime.timers), 与现有模块相同, 因此 timers.setTimeout
 * 等 Java 方法仍可读取; setTimeout / setInterval / setImmediate, 对应的 clear 函数与已废弃的 loop 只在全局, keepAlive 两处都有, 持久化任务入口 (6.8.0)
 * 与 workManager 相同. 参数按真实签名 (T4) 并采用新层的错误模型: 回调不是函数为 TypeError, 延迟或 ID 不是数值为 TypeError, NaN 或无穷为 RangeError (D10,
 * 附录 C Q12; 现有模块抛 IllegalArgumentException); 延迟为 null / undefined 时取文档的默认值. setIntervalExt 的间隔或超时为 null / undefined 时视为省略,
 * 与文档和旧 JS 相同 (现有模块拒绝 null).
 */
@ScriptModule(name = "timers")
class TimersModule(private val runtime: ScriptRuntime) {

    @ScriptPrototype
    val prototype: Any
        get() = runtime.timers

    @ScriptFunction(global = ScriptGlobal.ONLY)
    fun setTimeout(callback: Callable, delay: Any? = null, vararg args: Any?): Double =
        runtime.timers.setTimeout(callback, delayOf("timers.setTimeout", delay), *args)

    @ScriptFunction(global = ScriptGlobal.ONLY)
    fun setInterval(callback: Callable, delay: Any? = null, vararg args: Any?): Double =
        runtime.timers.setInterval(callback, delayOf("timers.setInterval", delay), *args)

    @ScriptFunction(global = ScriptGlobal.ONLY)
    fun setImmediate(callback: Callable, vararg args: Any?): Double = runtime.timers.setImmediate(callback, *args)

    @ScriptFunction(global = ScriptGlobal.ONLY)
    fun clearTimeout(id: Any?): Boolean = runtime.timers.clearTimeout(GlobalValues.number("timers.clearTimeout", "id", id))

    @ScriptFunction(global = ScriptGlobal.ONLY)
    fun clearInterval(id: Any?): Boolean = runtime.timers.clearInterval(GlobalValues.number("timers.clearInterval", "id", id))

    @ScriptFunction(global = ScriptGlobal.ONLY)
    fun clearImmediate(id: Any?): Boolean = runtime.timers.clearImmediate(GlobalValues.number("timers.clearImmediate", "id", id))

    /** Abandoned: only prints the warning, as the current module does (the obsolete JS threw an Error). */
    @ScriptFunction(global = ScriptGlobal.ONLY)
    fun loop() {
        val console = AutoJs.instance.globalConsole
        console.warn("")
        console.warn(ScriptMessages.resource(R.string.error_abandoned_method, "Method %s has been abandoned and should not be used", "loop"))
        console.warn("")
    }

    /**
     * Runs [listener] every [interval] milliseconds (200 when omitted or not positive) until [timeoutOrCondition], a total
     * time in milliseconds or a condition function evaluated after every run, ends it; then [callback] gets the result.
     */
    @ScriptFunction
    fun setIntervalExt(listener: Callable, interval: Any? = null, timeoutOrCondition: Any? = null, callback: Any? = null): Double {
        val millis = if (isNullish(interval)) 0.0 else GlobalValues.number("timers.setIntervalExt", "interval", interval)
        val condition = timeoutOrCondition as? Callable
        val timeout = if (condition != null || isNullish(timeoutOrCondition)) 0.0 else GlobalValues.number("timers.setIntervalExt", "timeoutOrCondition", timeoutOrCondition)
        return SetIntervalExt(runtime, listener, millis, timeout, condition, callback as? Callable).execute()
    }

    /** An empty timer keeping the script alive: once after [timeout] milliseconds, or every 10 s when it is not positive. */
    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun keepAlive(timeout: Any? = null): Double {
        val millis = if (isNullish(timeout)) 0L else GlobalValues.long("timers.keepAlive", "timeout", timeout).coerceAtLeast(0L)
        val empty = RhinoUtils.newBaseFunction(runtime.topLevelScope, "callback", { /* Empty body. */ }, RhinoUtils.NOT_CONSTRUCTABLE)
        return if (millis == 0L) runtime.timers.setInterval(empty, KEEP_ALIVE_INTERVAL) else runtime.timers.setTimeout(empty, millis)
    }

    @ScriptFunction
    fun addDailyTask(options: NativeObject? = null): Any? = WorkManager.addDailyTask(runtime, optionsArgs(options))

    @ScriptFunction
    fun addWeeklyTask(options: NativeObject? = null): Any? = WorkManager.addWeeklyTask(runtime, optionsArgs(options))

    @ScriptFunction
    fun addDisposableTask(options: NativeObject? = null): Any? = WorkManager.addDisposableTask(runtime, optionsArgs(options))

    @ScriptFunction
    fun addIntentTask(options: NativeObject? = null): Any? = WorkManager.addIntentTask(runtime, optionsArgs(options))

    @ScriptFunction
    fun getTimedTask(id: Any?): Any? = WorkManager.getTimedTask(runtime, arrayOf(TaskArgs.id("timers.getTimedTask", id)))

    @ScriptFunction
    fun getIntentTask(id: Any?): Any? = WorkManager.getIntentTask(runtime, arrayOf(TaskArgs.id("timers.getIntentTask", id)))

    @ScriptFunction
    fun removeIntentTask(id: Any?): Boolean? = WorkManager.removeIntentTask(runtime, arrayOf(TaskArgs.id("timers.removeIntentTask", id)))

    @ScriptFunction
    fun removeTimedTask(id: Any?): Boolean? = WorkManager.removeTimedTask(runtime, arrayOf(TaskArgs.id("timers.removeTimedTask", id)))

    @ScriptFunction
    fun queryIntentTasks(options: NativeObject? = null): Any = WorkManager.queryIntentTasks(runtime, optionsArgs(options))

    @ScriptFunction
    fun queryTimedTasks(options: NativeObject? = null): Any = WorkManager.queryTimedTasks(runtime, optionsArgs(options))

    private fun optionsArgs(options: NativeObject?): Array<Any?> = if (options == null) emptyArray() else arrayOf(options)

    /** The delay of `setTimeout` / `setInterval`: 1 ms when nullish, otherwise truncated to an integer (the current rule). */
    private fun delayOf(function: String, delay: Any?): Long = if (isNullish(delay)) 1L else GlobalValues.long(function, "delay", delay)

    private companion object {
        const val KEEP_ALIVE_INTERVAL = 10_000L
    }

}
