package org.autojs.autojs.runtime.api.augment.threads

import org.autojs.autojs.annotation.RhinoStandardFunctionInterface
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component3
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile

@Suppress("unused")
class VolatileDisposeNativeObject(private val declarationScope: VarScope? = null) : NativeObject() {

    @Volatile
    private var value: Any? = null

    // Guarded by the lock; a null value counts as set.
    private var isSet = false

    private val lock = ReentrantLock()
    private val ready = lock.newCondition()

    private val mFunctionNames = arrayOf(
        ::blockedGet.name,
        ::blockedGetOrThrow.name,
        ::setAndNotify.name,
    )

    init {
        RhinoUtils.initNativeObjectPrototype(this)
        declarationScope?.let {
            RhinoUtils.defineFunctionProperties(it, this, mFunctionNames, javaClass, PERMANENT)
        } ?: RhinoUtils.defineFunctionProperties(this, mFunctionNames, javaClass, PERMANENT)
    }

    private inline fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Waits until a value is set (at once when it already is; a wakeup without one keeps waiting), or at most [timeoutMillis]
     * when positive, and returns the value (null when none was set in time). An interrupt runs [onInterrupted] and otherwise
     * ends the wait with a ScriptInterruptedException, as `sleep` does (docs/augment/ROADMAP.md appendix C Q52).
     * zh-CN: 等待直至值被设置 (已设置则立即返回; 没有值的唤醒继续等待), timeoutMillis 为正时至多等待该时长, 返回该值 (未及时设置为 null).
     * 中断时执行 onInterrupted, 否则与 sleep 一样以 ScriptInterruptedException 结束等待 (docs/augment/ROADMAP.md 附录 C Q52).
     */
    private fun awaitValue(timeoutMillis: Long, onInterrupted: (() -> Unit)? = null): Any? = withLock {
        try {
            when {
                timeoutMillis <= 0L -> while (!isSet) ready.await()
                else -> {
                    var nanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
                    while (!isSet && nanos > 0L) nanos = ready.awaitNanos(nanos)
                }
            }
        } catch (e: InterruptedException) {
            onInterrupted?.invoke()
            throw ScriptInterruptedException()
        }
        value
    }

    private fun <T : RuntimeException> instantiateRuntimeException(clazz: Class<T>) = try {
        clazz.getDeclaredConstructor().newInstance()
    } catch (e: Exception) {
        RuntimeException(e)
    }

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun blockedGet(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Any? = ensureArgumentsAtMost(args, 1) { argList ->
            val (timeout) = argList
            val self = thisObj as VolatileDisposeNativeObject
            val timeoutMillis = coerceLongNumber(timeout, 0L)
            self.awaitValue(timeoutMillis)
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun blockedGetOrThrow(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Any? = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            val (exception, timeout, defaultValue) = argList
            require(exception is Class<*>) {
                "Argument \"exception\" must be a RuntimeException Class for ${VolatileDisposeNativeObject::class.java.simpleName}::blockedGetOrThrow"
            }
            val self = thisObj as VolatileDisposeNativeObject
            val timeoutMillis = coerceLongNumber(timeout, 0L)
            self.awaitValue(timeoutMillis) {
                @Suppress("UNCHECKED_CAST")
                throw self.instantiateRuntimeException(exception as Class<RuntimeException>)
            } ?: defaultValue
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun setAndNotify(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = ensureArgumentsOnlyOne(args) { value ->
            val self = thisObj as VolatileDisposeNativeObject
            self.withLock {
                self.value = value
                self.isSet = true
                self.ready.signalAll()
            }
            UNDEFINED
        }

    }

}
