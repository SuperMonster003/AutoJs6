package org.autojs.autojs.core.plugin.compose

import android.os.Handler
import android.os.Looper
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.ScriptAsyncDispatcher
import org.autojs.autojs.core.looper.TimerCallbackRejectedException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** A reserved, coalescing native-to-script delivery slot, independent of explicit script posts. */
interface ScriptControlChannel : AutoCloseable {
    fun request()
    override fun close()
}

/** post always queues, even in UI mode. Renderer events never call a script from the render stack. */
interface ScriptUiDispatcher : AutoCloseable {
    fun isDispatchThread(): Boolean
    fun post(action: () -> Unit)
    fun dispatch(action: () -> Unit) { if (isDispatchThread()) action() else post(action) }
    fun createControlChannel(action: () -> Unit): ScriptControlChannel {
        check(isDispatchThread()) { "Control channels must be created on the script dispatcher" }
        return QueuedScriptControlChannel(this, action)
    }
    override fun close()

    companion object {
        fun main(): ScriptUiDispatcher {
            val handler = Handler(Looper.getMainLooper())
            return TimerScriptUiDispatcher(Looper.getMainLooper().thread,
                enqueue = { action -> if (!handler.post { action() }) throw TimerCallbackRejectedException() },
                cancel = { handler.removeCallbacksAndMessages(null) })
        }

        /** Called on the script's originating thread. One dispatcher is owned by one session. */
        fun forRuntime(runtime: ScriptRuntime, uiMode: Boolean, keepAlive: Boolean = true): ScriptUiDispatcher {
            if (uiMode) {
                check(Looper.myLooper() == Looper.getMainLooper())
                return main()
            }
            val origin = Thread.currentThread()
            val async = ScriptAsyncDispatcher(runtime, keepAlive)
            val control = ScriptAsyncDispatcher(runtime, keepAlive = false)
            return TimerScriptUiDispatcher(origin,
                enqueue = { action -> async.dispatchValues(emptyArray()) { action() } },
                cancel = { try { async.cancelSilently() } finally { control.cancelSilently() } },
                enqueueControl = { action -> control.dispatchValues(emptyArray()) { action() } })
        }

        fun mainExecutor(): Executor {
            val handler = Handler(Looper.getMainLooper())
            return Executor { action -> if (Looper.myLooper() == Looper.getMainLooper()) action.run() else handler.post(action) }
        }
    }
}

/** Default seam for custom/test dispatchers; Android implementations use the bounded shared lane. */
private class QueuedScriptControlChannel(private val dispatcher: ScriptUiDispatcher, action: () -> Unit) : ScriptControlChannel {
    private val callback = AtomicReference<(() -> Unit)?>(action)
    private val scheduled = AtomicBoolean()
    override fun request() {
        if (callback.get() == null || !scheduled.compareAndSet(false, true)) return
        try {
            dispatcher.post { scheduled.set(false); callback.get()?.invoke() }
        } catch (failure: Throwable) { scheduled.set(false); throw failure }
    }
    override fun close() { callback.set(null) }
}
