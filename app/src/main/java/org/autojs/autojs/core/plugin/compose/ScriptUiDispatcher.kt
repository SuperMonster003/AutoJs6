package org.autojs.autojs.core.plugin.compose

import android.os.Handler
import android.os.Looper
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.ScriptAsyncDispatcher
import java.util.concurrent.Executor

/** post always queues, even in UI mode. Renderer events never call a script from the render stack. */
interface ScriptUiDispatcher : AutoCloseable {
    fun isDispatchThread(): Boolean
    fun post(action: () -> Unit)
    fun dispatch(action: () -> Unit) { if (isDispatchThread()) action() else post(action) }
    override fun close()

    companion object {
        fun main(): ScriptUiDispatcher = object : ScriptUiDispatcher {
            private val handler = Handler(Looper.getMainLooper())
            @Volatile private var closed = false
            override fun isDispatchThread() = Looper.myLooper() == Looper.getMainLooper()
            override fun post(action: () -> Unit) { if (!closed) handler.post { if (!closed) action() } }
            override fun close() { closed = true; handler.removeCallbacksAndMessages(null) }
        }

        /** Called on the script's originating thread. One dispatcher is owned by one session. */
        fun forRuntime(runtime: ScriptRuntime, uiMode: Boolean, keepAlive: Boolean = true): ScriptUiDispatcher {
            if (uiMode) {
                check(Looper.myLooper() == Looper.getMainLooper())
                return main()
            }
            val origin = Thread.currentThread()
            val async = ScriptAsyncDispatcher(runtime, keepAlive)
            return object : ScriptUiDispatcher {
                override fun isDispatchThread() = Thread.currentThread() === origin
                override fun post(action: () -> Unit) { async.dispatchValues(emptyArray()) { action() } }
                override fun close() { async.cancelSilently() }
            }
        }

        fun mainExecutor(): Executor {
            val handler = Handler(Looper.getMainLooper())
            return Executor { action -> if (Looper.myLooper() == Looper.getMainLooper()) action.run() else handler.post(action) }
        }
    }
}
