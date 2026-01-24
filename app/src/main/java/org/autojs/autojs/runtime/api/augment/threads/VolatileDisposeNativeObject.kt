package org.autojs.autojs.runtime.api.augment.threads

import org.autojs.autojs.annotation.RhinoStandardFunctionInterface
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component3
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile

@Suppress("unused")
class VolatileDisposeNativeObject : NativeObject() {

    @Volatile
    private var value: Any? = null

    private val lock = ReentrantLock()
    private val ready = lock.newCondition()

    private val mFunctionNames = arrayOf(
        ::blockedGet.name,
        ::blockedGetOrThrow.name,
        ::setAndNotify.name,
    )

    init {
        RhinoUtils.initNativeObjectPrototype(this)
        defineFunctionProperties(mFunctionNames, javaClass, PERMANENT)
    }

    private inline fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }

    private fun awaitValue(timeoutMillis: Long, onInterrupted: (() -> Unit)? = null): Any? = withLock {
        when {
            timeoutMillis <= 0L -> {
                try {
                    ready.await()
                } catch (e: InterruptedException) {
                    onInterrupted?.invoke() ?: throw RuntimeException(e)
                }
            }
            else -> {
                var nanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
                while (nanos > 0L) {
                    try {
                        ready.awaitNanos(nanos).also { nanos = it }
                    } catch (e: InterruptedException) {
                        onInterrupted?.invoke() ?: throw RuntimeException(e)
                    }
                }
            }
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
                self.ready.signalAll()
            }
            UNDEFINED
        }

    }

}