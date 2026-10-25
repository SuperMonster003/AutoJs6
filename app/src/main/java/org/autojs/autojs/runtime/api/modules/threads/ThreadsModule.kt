package org.autojs.autojs.runtime.api.modules.threads

import org.autojs.autojs.core.looper.TimerThread
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs6.R
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Synchronizer
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper
import java.util.concurrent.ThreadPoolExecutor
import org.mozilla.javascript.Function as RhinoFunction

/**
 * The `threads` module on the new script module layer (roadmap P3.4): the module object's prototype is the runtime's Java
 * `Threads` (`ScriptRuntime.threads`: `currentThread`, `atomic`, `lock`, `shutDownAll`, ...), as with the current module;
 * `interrupt`, `start`, `pool` and `disposable` are module functions (`disposable` returns the script object of 6.7.0, not
 * the Java one of the prototype) and `sync` is a global name only.
 *
 * Arguments follow the real signatures (T4) with the error model of the new layer (D10): an action that is neither a
 * function nor a `java.lang.Runnable`, options that are not an object and a `sync` target that is not a function are
 * TypeErrors, a pool size or keep-alive time without a number a TypeError and a negative one a RangeError (the current
 * module threw IllegalArgumentException). A thread that cannot start because the script is exiting or was interrupted
 * gives `undefined` as documented; any other failure keeps its Java type (the current module rethrew it as a
 * RuntimeException with the message and an array address).
 *
 * zh-CN: 新脚本模块层上的 threads 模块 (P3.4): 模块对象的原型是运行时的 Java Threads (ScriptRuntime.threads: currentThread, atomic, lock, shutDownAll 等),
 * 与现有模块相同; interrupt, start, pool 与 disposable 为模块函数 (disposable 返回 6.7.0 起的脚本对象, 而非原型上的 Java 对象), sync 只在全局. 参数按真实签名
 * (T4) 并采用新层的错误模型 (D10): 任务既不是函数也不是 Runnable, 选项不是对象, sync 的目标不是函数均为 TypeError, 线程池大小或存活时间不是数值为 TypeError,
 * 为负数为 RangeError (现有模块抛 IllegalArgumentException). 因脚本正在退出或被中断而无法启动的线程按文档返回 undefined; 其他失败保留 Java 类型 (现有模块
 * 把它包装为带消息与数组地址的 RuntimeException).
 */
@ScriptModule(name = "threads")
class ThreadsModule(private val runtime: ScriptRuntime) {

    @ScriptPrototype
    val prototype: Any
        get() = runtime.threads

    /** Interrupts [thread] when it is a living TimerThread; anything else is ignored, as documented. */
    @ScriptFunction
    fun interrupt(thread: Any?) {
        val target = if (thread is Wrapper) thread.unwrap() else thread
        if (target is TimerThread && target.isAlive) target.interrupt()
    }

    @ScriptFunction
    fun start(action: Any): Any {
        val runnable = when (val target = if (action is Wrapper) action.unwrap() else action) {
            is Callable -> Runnable { RhinoUtils.callFunction(runtime, target, runtime.topLevelScope, Undefined.SCRIPTABLE_UNDEFINED, emptyArray()) }
            is Runnable -> target
            else -> throw GlobalValues.typeError("threads.start", "action", "function | java.lang.Runnable", action)
        }
        return try {
            runtime.threads.start(runnable)
        } catch (e: Exception) {
            if (ScriptInterruptedException.causedByInterrupt(Throwable(e))) return Undefined.instance
            if (e.message?.endsWith(ScriptMessages.resource(R.string.error_script_is_on_exiting, "Script is on exiting")) == true) return Undefined.instance
            throw e
        }
    }

    /** A pool of TimerThreads with the options' sizes (0 / 0, keep-alive 60000 ms when absent), managed by the runtime. */
    @ScriptFunction
    fun pool(options: NativeObject? = null): ThreadPoolExecutor {
        val corePoolSize = nonNegative("corePoolSize", options, 0) { GlobalValues.int(POOL, "options.corePoolSize", it).toLong() }.toInt()
        val maxPoolSize = nonNegative("maxPoolSize", options, 0) { GlobalValues.int(POOL, "options.maxPoolSize", it).toLong() }.toInt()
        val keepAliveTime = nonNegative("keepAliveTime", options, 60_000L) { GlobalValues.long(POOL, "options.keepAliveTime", it) }
        return runtime.threads.internalPool(corePoolSize, maxPoolSize, keepAliveTime)
    }

    @ScriptFunction
    fun disposable(): VolatileDisposeNativeObject = VolatileDisposeNativeObject(runtime.topLevelScope)

    /** A function calling [func] inside the Java monitor of [lock] (of the call's `this` without one), Rhino's Synchronizer. */
    @ScriptFunction(global = ScriptGlobal.ONLY)
    fun sync(scope: VarScope, func: Callable, lock: Any? = null): Synchronizer {
        val target = func as? RhinoFunction ?: object : BaseFunction() {
            override fun call(cx: Context, nt: Any?, scope: VarScope, thisObj: Any?, args: Array<out Any?>): Any? = func.call(cx, scope, thisObj, args)
        }.also { RhinoUtils.initNewBaseFunction(scope, it) }
        return Synchronizer(target, lock)
    }

    private inline fun nonNegative(name: String, options: ScriptableObject?, default: Long, read: (Any?) -> Long): Long {
        val value = options?.let { ScriptableObject.getProperty(it, name) }
        if (isNullish(value)) return default
        return read(value).also { if (it < 0) throw GlobalValues.rangeError(POOL, "options.$name", "non-negative number", value) }
    }

    private companion object {
        const val POOL = "threads.pool"
    }

}
