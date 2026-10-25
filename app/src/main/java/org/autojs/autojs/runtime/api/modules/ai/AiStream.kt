package org.autojs.autojs.runtime.api.modules.ai

import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.ScriptRuntime
import org.mozilla.javascript.Callable
import java.util.concurrent.atomic.AtomicReference

/**
 * Event-driven result of one streaming AI request.
 *
 * The emitter intentionally has no [org.autojs.autojs.core.looper.Timer]. [AiAsyncDispatcher]
 * performs the single required hop to the originating JavaScript thread before [emit] is called,
 * avoiding double scheduling and cross-thread Rhino access.
 */
class AiStream internal constructor(
    private val dispatcher: AiAsyncDispatcher,
    bridges: ScriptBridges,
    private val onTerminal: (() -> Unit)?,
) : EventEmitter(bridges) {

    private val cancellation = AtomicReference<(() -> Unit)?>()
    private val streamState = AtomicReference(State.CREATED)
    private val terminalLock = Any()

    internal constructor(
        scriptRuntime: ScriptRuntime,
        onTerminal: (() -> Unit)? = null,
    ) : this(
        dispatcher = AiAsyncDispatcher(scriptRuntime),
        bridges = scriptRuntime.bridges,
        onTerminal = onTerminal,
    )

    val state: String
        get() = streamState.get().value

    @get:JvmName("getIsDone")
    val isDone: Boolean
        get() = dispatcher.isTerminal

    override fun on(eventName: String, listener: Callable): AiStream {
        super.on(eventName, listener)
        return this
    }

    override fun once(eventName: String, listener: Callable): AiStream {
        super.once(eventName, listener)
        return this
    }

    /** Binds plugin-transport cancellation without exposing it to Rhino. */
    private fun bindCancellation(action: () -> Unit): AiStream {
        val cancelNow = synchronized(terminalLock) {
            if (isDone) {
                true
            } else {
                check(cancellation.compareAndSet(null, action)) {
                    "AI stream already has a bound cancellation"
                }
                false
            }
        }
        if (cancelNow) action()
        return this
    }

    private fun open(metadata: Any? = null): Boolean {
        if (!markOpen(allowAlreadyOpen = false)) return false
        return dispatcher.dispatchValues(arrayOf(metadata)) { values ->
            emit(EVENT_OPEN, values[0])
        }
    }

    private fun delta(text: String, chunk: Any? = null): Boolean {
        if (!markOpen()) return false
        return dispatcher.dispatchValues(arrayOf(text, chunk)) { values ->
            emit(EVENT_DELTA, values[0], values[1])
        }
    }

    private fun chunk(chunk: Any?): Boolean {
        if (!markOpen()) return false
        return dispatcher.dispatchValues(arrayOf(chunk)) { values ->
            emit(EVENT_CHUNK, values[0])
        }
    }

    /** Emits the normalized chunk and its text delta in one bounded dispatcher action. */
    private fun textChunk(text: String, chunk: Any?): Boolean {
        if (!markOpen()) return false
        return dispatcher.dispatchValues(arrayOf(text, chunk)) { values ->
            var firstFailure: Throwable? = null
            try {
                emit(EVENT_CHUNK, values[1])
            } catch (error: Throwable) {
                firstFailure = error
            }
            try {
                emit(EVENT_DELTA, values[0], values[1])
            } catch (error: Throwable) {
                firstFailure?.addSuppressed(error) ?: run { firstFailure = error }
            }
            firstFailure?.let { throw it }
        }
    }

    private fun usage(usage: Any?): Boolean {
        if (!markOpen()) return false
        return dispatcher.dispatchValues(arrayOf(usage)) { values ->
            emit(EVENT_USAGE, values[0])
        }
    }

    private fun complete(result: Any?): Boolean {
        return terminate(State.DONE, arrayOf(result)) { values ->
            emit(EVENT_DONE, values[0])
        }
    }

    private fun fail(error: Any?): Boolean {
        return terminate(State.ERROR, arrayOf(error)) { values ->
            emit(EVENT_ERROR, values[0])
        }
    }

    /**
     * User-visible cancellation. The bound plugin transport is cancelled immediately while the
     * "cancelled" event and looper-wait release are delivered on the originating JS thread.
     */
    fun cancel(): AiStream {
        var boundCancellation: (() -> Unit)? = null
        var cancellationFailure: Throwable? = null
        synchronized(terminalLock) {
            if (dispatcher.isTerminal) return this
            streamState.set(State.CANCELLED)
            val accepted = try {
                dispatcher.replaceQueuedWithTerminalValues(action = {
                    try {
                        emit(EVENT_CANCELLED)
                    } finally {
                        notifyTerminal()
                    }
                })
            } catch (error: Throwable) {
                notifyTerminal()
                cancellationFailure = error
                false
            }
            if (!accepted && cancellationFailure == null) {
                return this
            }
            boundCancellation = cancellation.getAndSet(null)
        }
        cancellationFailure = cancelTransport(
            boundCancellation = boundCancellation,
            previousFailure = cancellationFailure,
        )
        cancellationFailure?.let { throw it }
        return this
    }

    /**
     * Runtime-exit cancellation. No JavaScript event is emitted after teardown begins.
     */
    private fun cancelSilently(): Boolean {
        val boundCancellation: (() -> Unit)?
        synchronized(terminalLock) {
            if (!dispatcher.cancelSilently(::notifyTerminal)) return false
            streamState.set(State.CANCELLED)
            boundCancellation = cancellation.getAndSet(null)
        }
        cancelTransport(boundCancellation)?.let { throw it }
        return true
    }

    private fun cancelTransport(
        boundCancellation: (() -> Unit)?,
        previousFailure: Throwable? = null,
    ): Throwable? {
        var firstFailure = previousFailure
        try {
            boundCancellation?.invoke()
        } catch (error: Throwable) {
            firstFailure?.addSuppressed(error) ?: run { firstFailure = error }
        }
        return firstFailure
    }

    private fun markOpen(allowAlreadyOpen: Boolean = true): Boolean {
        while (true) {
            when (val current = streamState.get()) {
                State.CREATED -> {
                    if (streamState.compareAndSet(current, State.OPEN)) return true
                }
                State.OPEN -> return allowAlreadyOpen && !dispatcher.isTerminal
                else -> return false
            }
        }
    }

    private fun terminate(
        terminalState: State,
        values: Array<out Any?> = emptyArray(),
        action: (Array<Any?>) -> Unit,
    ): Boolean {
        synchronized(terminalLock) {
            if (dispatcher.isTerminal) return false
            streamState.set(terminalState)
            val accepted = try {
                dispatcher.terminalValues(values) { mapped ->
                    try {
                        action(mapped)
                    } finally {
                        notifyTerminal()
                    }
                }
            } catch (error: Throwable) {
                cancellation.set(null)
                notifyTerminal()
                throw error
            }
            if (!accepted) return false
            cancellation.set(null)
            return true
        }
    }

    private fun notifyTerminal() {
        runCatching { onTerminal?.invoke() }
    }

    private enum class State(val value: String) {
        CREATED("created"),
        OPEN("open"),
        DONE("done"),
        ERROR("error"),
        CANCELLED("cancelled"),
    }

    /**
     * Transport-side capability kept separate from the Java object exposed to Rhino.
     *
     * Kotlin internal methods are JVM-public and therefore discoverable through Rhino's Java
     * reflection. Holding all state transitions behind this unexposed controller leaves scripts
     * with only the intended event/cancel surface.
     */
    internal class Controller internal constructor(
        val stream: AiStream,
    ) {

        constructor(
            scriptRuntime: ScriptRuntime,
            onTerminal: (() -> Unit)? = null,
        ) : this(AiStream(scriptRuntime, onTerminal))

        constructor(
            dispatcher: AiAsyncDispatcher,
            bridges: ScriptBridges,
            onTerminal: (() -> Unit)? = null,
        ) : this(AiStream(dispatcher, bridges, onTerminal))

        val isDone: Boolean
            get() = stream.isDone

        fun bindCancellation(action: () -> Unit): Controller = apply {
            stream.bindCancellation(action)
        }

        fun open(metadata: Any? = null): Boolean = stream.open(metadata)

        fun delta(text: String, chunk: Any? = null): Boolean = stream.delta(text, chunk)

        fun chunk(chunk: Any?): Boolean = stream.chunk(chunk)

        fun textChunk(text: String, chunk: Any?): Boolean = stream.textChunk(text, chunk)

        fun usage(usage: Any?): Boolean = stream.usage(usage)

        fun complete(result: Any?): Boolean = stream.complete(result)

        fun fail(error: Any?): Boolean = stream.fail(error)

        fun cancelSilently(): Boolean = stream.cancelSilently()
    }

    companion object {

        const val EVENT_OPEN = "open"
        const val EVENT_DELTA = "delta"
        const val EVENT_CHUNK = "chunk"
        const val EVENT_USAGE = "usage"
        const val EVENT_DONE = "done"
        const val EVENT_ERROR = "error"
        const val EVENT_CANCELLED = "cancelled"

    }

}
