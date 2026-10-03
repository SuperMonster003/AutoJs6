package org.autojs.autojs.core.plugin.compose

import org.autojs.autojs.core.looper.TimerCallbackRejectedException
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.ComposeUiLimits
import java.util.concurrent.atomic.AtomicBoolean

/** A late Android-main callback may outlive the script Looper even while its Thread is still alive. */
internal class TimerScriptUiDispatcher(
    private val origin: Thread,
    private val enqueue: (() -> Unit) -> Unit,
    private val cancel: () -> Unit,
    private val enqueueControl: (() -> Unit) -> Unit = enqueue,
    private val maxControlChannels: Int = ComposeUiLimits.MAX_SESSIONS_PER_ENGINE * 2 + 1,
) : ScriptUiDispatcher {
    private val closed = AtomicBoolean()
    private val controlLock = Any()
    private val channels = LinkedHashSet<Control>()
    private val ready = LinkedHashSet<Control>()
    private var controlScheduled = false

    override fun isDispatchThread() = Thread.currentThread() === origin

    override fun post(action: () -> Unit) {
        if (closed.get()) return
        try {
            enqueue { if (!closed.get()) action() }
        } catch (_: TimerCallbackRejectedException) {
            // This is the Timer's explicit Looper-quitting signal, not a queue-overflow or script error.
            close()
        }
    }

    override fun createControlChannel(action: () -> Unit): ScriptControlChannel {
        check(isDispatchThread()) { "Control channels must be created on the script dispatcher" }
        return synchronized(controlLock) {
            if (closed.get()) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED)
            if (channels.size >= maxControlChannels) throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED,
                "Too many Compose sessions awaiting native cleanup")
            Control(action).also(channels::add)
        }
    }

    private inner class Control(var action: (() -> Unit)?) : ScriptControlChannel {
        override fun request() {
            val schedule = synchronized(controlLock) {
                if (closed.get() || action == null) return
                ready += this
                if (controlScheduled) false else { controlScheduled = true; true }
            }
            if (schedule) try {
                enqueueControl(::drainControls)
            } catch (_: TimerCallbackRejectedException) {
                this@TimerScriptUiDispatcher.close()
            } catch (failure: Throwable) {
                synchronized(controlLock) { controlScheduled = false }
                throw failure
            }
        }
        override fun close() {
            synchronized(controlLock) { action = null; ready.remove(this); channels.remove(this) }
        }
    }

    private fun drainControls() {
        check(isDispatchThread()) { "Native controls belong to the script dispatcher" }
        val batch = synchronized(controlLock) {
            controlScheduled = false
            ready.toList().also { ready.clear() }
        }
        var failure: Throwable? = null
        batch.forEach { channel ->
            val action = synchronized(controlLock) { if (closed.get()) null else channel.action }
            try { action?.invoke() }
            catch (error: Exception) { if (failure == null) failure = error }
            catch (error: LinkageError) { if (failure == null) failure = error }
        }
        failure?.let { throw it }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            synchronized(controlLock) {
                channels.forEach { it.action = null }
                channels.clear(); ready.clear(); controlScheduled = false
            }
            cancel()
        }
    }
}
