package org.autojs.autojs.runtime.api.augment.tts

import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.tts.TtsTask
import org.autojs.autojs.runtime.api.tts.TtsTaskController
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.augment.ScriptAsyncDispatcher
import org.autojs.autojs.runtime.api.augment.toJsPromise
import org.mozilla.javascript.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Observable handle for one logical TTS operation.
 *
 * Platform callbacks never invoke this emitter directly. [ScriptAsyncDispatcher] first moves
 * values and delivery back to the JavaScript thread that created the handle.
 */
class TtsUtterance internal constructor(
    val id: String,
    val operation: String,
    val text: String?,
    private val dispatcher: ScriptAsyncDispatcher,
    bridges: ScriptBridges,
    private val promiseAdapter: ScriptPromiseAdapter,
    val result: Any,
    private val requestStop: (String) -> Unit,
    private val onTerminal: () -> Unit,
) : EventEmitter(bridges) {

    private val utteranceState = AtomicReference(State.CREATED)
    private val terminalNotified = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val terminalLock = Any()

    val state: String
        get() = utteranceState.get().value

    @get:JvmName("getIsDone")
    val isDone: Boolean
        get() = dispatcher.isTerminal

    override fun on(eventName: String, listener: Callable): TtsUtterance {
        super.on(eventName, listener)
        return this
    }

    override fun once(eventName: String, listener: Callable): TtsUtterance {
        super.once(eventName, listener)
        return this
    }

    fun stop(): TtsUtterance {
        if (!isDone && stopRequested.compareAndSet(false, true)) {
            try {
                requestStop(id)
            } catch (error: Throwable) {
                stopRequested.set(false)
                throw error
            }
        }
        return this
    }

    fun cancel(): TtsUtterance = stop()

    private fun queued(info: Any?): Boolean {
        if (!utteranceState.compareAndSet(State.CREATED, State.QUEUED)) return false
        return dispatcher.dispatchValues(arrayOf(info)) { values ->
            emit(EVENT_QUEUED, values[0])
        }
    }

    private fun start(info: Any?): Boolean {
        while (true) {
            when (val current = utteranceState.get()) {
                State.CREATED, State.QUEUED -> {
                    if (utteranceState.compareAndSet(current, State.RUNNING)) break
                }
                State.RUNNING -> return true
                else -> return false
            }
        }
        return dispatcher.dispatchValues(arrayOf(info)) { values ->
            emit(EVENT_START, values[0])
        }
    }

    private fun chunk(info: Any?): Boolean =
        dispatchWhileActive(EVENT_CHUNK, info)

    private fun range(info: Any?): Boolean =
        dispatchWhileActive(EVENT_RANGE, info)

    private fun synthesis(info: Any?): Boolean =
        dispatchWhileActive(EVENT_SYNTHESIS, info)

    private fun audio(info: Any?): Boolean =
        dispatchWhileActive(EVENT_AUDIO, info)

    private fun dispatchWhileActive(event: String, value: Any?): Boolean {
        if (isDone) return false
        return dispatcher.dispatchValues(arrayOf(value)) { values ->
            emit(event, values[0])
        }
    }

    private fun complete(value: Any?): Boolean {
        return terminate(State.DONE, arrayOf(value), replaceQueued = false) { values ->
            try {
                emit(EVENT_DONE, values[0])
            } finally {
                promiseAdapter.resolve(values[0])
            }
        }
    }

    private fun fail(error: Throwable): Boolean {
        return terminate(State.ERROR, arrayOf(error), replaceQueued = false) { values ->
            try {
                emit(EVENT_ERROR, values[0])
            } finally {
                promiseAdapter.reject(values[0])
            }
        }
    }

    private fun stop(info: Any?, error: Throwable, cancelled: Boolean): Boolean {
        return terminate(
            if (cancelled) State.CANCELLED else State.STOPPED,
            arrayOf(info, error),
            replaceQueued = true,
        ) { values ->
            try {
                emit(EVENT_STOP, values[0])
                if (cancelled) emit(EVENT_CANCELLED, values[0])
            } finally {
                promiseAdapter.reject(values[1])
            }
        }
    }

    private fun terminate(
        state: State,
        values: Array<out Any?>,
        replaceQueued: Boolean,
        action: (Array<Any?>) -> Unit,
    ): Boolean {
        synchronized(terminalLock) {
            if (dispatcher.isTerminal) return false
            utteranceState.set(state)
            val accepted = try {
                val terminalAction: (
                    Array<out Any?>,
                    (Array<Any?>) -> Unit,
                ) -> Boolean = if (replaceQueued) {
                    dispatcher::replaceQueuedWithTerminalValues
                } else {
                    dispatcher::terminalValues
                }
                terminalAction(values) { mapped ->
                    try {
                        action(mapped)
                    } finally {
                        notifyTerminal()
                    }
                }
            } catch (error: Throwable) {
                notifyTerminal()
                throw error
            }
            return accepted
        }
    }

    private fun cancelSilently(): Boolean {
        synchronized(terminalLock) {
            val wasTerminal = dispatcher.isTerminal
            val cancelled = dispatcher.cancelSilently(::notifyTerminal)
            if (cancelled && !wasTerminal) {
                utteranceState.set(State.CANCELLED)
            }
            return cancelled
        }
    }

    private fun notifyTerminal() {
        if (terminalNotified.compareAndSet(false, true)) {
            runCatching(onTerminal)
        }
    }

    private enum class State(val value: String) {
        CREATED("created"),
        QUEUED("queued"),
        RUNNING("running"),
        DONE("done"),
        STOPPED("stopped"),
        ERROR("error"),
        CANCELLED("cancelled"),
    }

    /**
     * Service-side capability. It is never returned to Rhino, keeping terminal transitions and
     * runtime-exit suppression unreachable from ordinary scripts.
     */
    internal class Controller internal constructor(
        val utterance: TtsUtterance,
    ) : TtsTaskController {

        constructor(
            scriptRuntime: ScriptRuntime,
            id: String,
            operation: String,
            text: String?,
            requestStop: (String) -> Unit,
            onTerminal: () -> Unit,
        ) : this(
            createUtterance(
                scriptRuntime,
                id,
                operation,
                text,
                requestStop,
                onTerminal,
            ),
        )

        override val task = object : TtsTask {
            override val id get() = utterance.id
            override val operation get() = utterance.operation
            override val text get() = utterance.text
            override val result get() = utterance.result
            override val state get() = utterance.state
            override val isDone get() = utterance.isDone
            override val publicValue get() = utterance
            override fun stop(): TtsTask { utterance.stop(); return this }
        }

        override val id: String
            get() = utterance.id

        override val isDone: Boolean
            get() = utterance.isDone

        override fun queued(info: Any?): Boolean = utterance.queued(info)

        override fun start(info: Any?): Boolean = utterance.start(info)

        override fun chunk(info: Any?): Boolean = utterance.chunk(info)

        override fun range(info: Any?): Boolean = utterance.range(info)

        override fun synthesis(info: Any?): Boolean = utterance.synthesis(info)

        override fun audio(info: Any?): Boolean = utterance.audio(info)

        override fun complete(value: Any?): Boolean = utterance.complete(value)

        override fun fail(error: Throwable): Boolean = utterance.fail(error)

        override fun stop(info: Any?, error: Throwable, cancelled: Boolean): Boolean =
            utterance.stop(info, error, cancelled)

        override fun cancelSilently(): Boolean = utterance.cancelSilently()
    }

    companion object {

        const val EVENT_QUEUED = "queued"
        const val EVENT_START = "start"
        const val EVENT_CHUNK = "chunk"
        const val EVENT_RANGE = "range"
        const val EVENT_SYNTHESIS = "synthesis"
        const val EVENT_AUDIO = "audio"
        const val EVENT_DONE = "done"
        const val EVENT_STOP = "stop"
        const val EVENT_ERROR = "error"
        const val EVENT_CANCELLED = "cancelled"

        private fun createUtterance(
            scriptRuntime: ScriptRuntime,
            id: String,
            operation: String,
            text: String?,
            requestStop: (String) -> Unit,
            onTerminal: () -> Unit,
        ): TtsUtterance {
            val adapter = ScriptPromiseAdapter()
            return TtsUtterance(
                id = id,
                operation = operation,
                text = text,
                dispatcher = ScriptAsyncDispatcher(scriptRuntime),
                bridges = scriptRuntime.bridges,
                promiseAdapter = adapter,
                result = adapter.toJsPromise(scriptRuntime),
                requestStop = requestStop,
                onTerminal = onTerminal,
            )
        }
    }
}
