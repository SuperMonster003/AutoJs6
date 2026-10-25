package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.core.looper.Timer
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.VarScope
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Marshals one asynchronous operation back to the JavaScript thread that created it.
 *
 * The originating timer and looper wait id are deliberately captured by the runtime
 * constructor. Background callbacks must only enqueue immutable JVM values; conversion to
 * Rhino values and Promise/event delivery happen while [drain] runs on the captured timer.
 */
internal class ScriptAsyncDispatcher private constructor(
    private val valueMapper: (Any?) -> Any?,
    private val drainMicrotasks: () -> Unit,
    private val releaseWait: () -> Unit,
    private val timer: Timer?,
    scope: VarScope?,
    private val testScheduler: (((() -> Unit)) -> Unit)?,
    private val maxQueuedActions: Int,
    private val maxQueuedBytes: Long,
) {

    init {
        require(maxQueuedActions > 0) { "maxQueuedActions must be positive" }
        require(maxQueuedBytes > 0L && maxQueuedBytes < Long.MAX_VALUE) {
            "maxQueuedBytes must be positive and finite"
        }
    }

    private data class QueuedAction(
        val estimatedBytes: Long,
        val terminalAction: Boolean,
        val action: () -> Unit,
    )

    private val queueLock = Any()
    private val queue = ArrayDeque<QueuedAction>()
    private val terminal = AtomicBoolean(false)
    private val deliverySuppressed = AtomicBoolean(false)
    private val waitReleased = AtomicBoolean(false)
    private val originThread = Thread.currentThread()

    @GuardedBy("queueLock")
    private var drainScheduled = false

    @GuardedBy("queueLock")
    private var queuedBytes = 0L

    @GuardedBy("queueLock")
    private var terminalActionClaimed = false

    /**
     * This function is created on the originating JavaScript thread, not lazily on a background
     * callback thread.
     */
    private val drainCallable: Callable? = when {
        timer == null -> null
        else -> newBaseFunction(
            requireNotNull(scope),
            DISPATCH_FUNCTION_NAME,
            {
                drain()
                UNDEFINED
            },
            NOT_CONSTRUCTABLE,
        )
    }

    constructor(scriptRuntime: ScriptRuntime, keepAlive: Boolean = true) : this(
        valueMapper = { value ->
            RhinoUtils.toJsValue(scriptRuntime.topLevelScope, value)
        },
        drainMicrotasks = {
            Context.getCurrentContext()?.processMicrotasks()
        },
        releaseWait = if (keepAlive) captureWaitRelease(scriptRuntime) else ({}),
        timer = scriptRuntime.timers.timerForCurrentThread ?: scriptRuntime.timers.mainTimer,
        scope = scriptRuntime.topLevelScope,
        testScheduler = null,
        maxQueuedActions = DEFAULT_MAX_QUEUED_ACTIONS,
        maxQueuedBytes = DEFAULT_MAX_QUEUED_BYTES,
    )

    /**
     * Pure-JVM seam used to verify ordering and terminal behavior without an Android Looper.
     */
    internal constructor(
        valueMapper: (Any?) -> Any?,
        scheduler: ((() -> Unit) -> Unit),
        releaseWait: () -> Unit,
        drainMicrotasks: () -> Unit = {},
        maxQueuedActions: Int = DEFAULT_MAX_QUEUED_ACTIONS,
        maxQueuedBytes: Long = DEFAULT_MAX_QUEUED_BYTES,
    ) : this(
        valueMapper = valueMapper,
        drainMicrotasks = drainMicrotasks,
        releaseWait = releaseWait,
        timer = null,
        scope = null,
        testScheduler = scheduler,
        maxQueuedActions = maxQueuedActions,
        maxQueuedBytes = maxQueuedBytes,
    )

    val isTerminal: Boolean
        get() = terminal.get()

    /** A synchronous Agent join may pump its own events, never another thread's Rhino queue. */
    internal fun drainOnOriginatingThread() {
        check(Thread.currentThread() === originThread) { "Script callbacks belong to their originating thread" }
        drain()
    }

    /**
     * Delivers a non-terminal value list on the originating JavaScript thread.
     *
     * Returns false once a terminal action has been accepted.
     */
    fun dispatchValues(
        values: Array<out Any?>,
        action: (Array<Any?>) -> Unit,
    ): Boolean {
        return enqueue(
            terminalAction = false,
            estimatedBytes = estimateValues(values),
        ) {
            if (!deliverySuppressed.get()) {
                try {
                    action(mapValues(values))
                } finally {
                    drainMicrotasks()
                }
            }
        }
    }

    /**
     * Accepts exactly one terminal action, converts its values on the originating JavaScript
     * thread, and releases the captured looper wait id there even if conversion or delivery
     * throws.
     */
    fun terminalValues(
        values: Array<out Any?> = emptyArray(),
        action: (Array<Any?>) -> Unit = {},
    ): Boolean {
        return enqueueTerminalValues(
            values = values,
            clearQueuedActions = false,
            action = action,
        )
    }

    /**
     * Atomically discards every queued non-terminal delivery and replaces it with one
     * user-visible terminal action.
     *
     * An action that [drain] has already removed from the queue may already be executing and
     * therefore cannot be revoked. All actions that are still pending when this method acquires
     * [queueLock] are removed before the terminal action is appended.
     */
    fun replaceQueuedWithTerminalValues(
        values: Array<out Any?> = emptyArray(),
        action: (Array<Any?>) -> Unit = {},
    ): Boolean {
        return enqueueTerminalValues(
            values = values,
            clearQueuedActions = true,
            action = action,
        )
    }

    private fun enqueueTerminalValues(
        values: Array<out Any?>,
        clearQueuedActions: Boolean,
        action: (Array<Any?>) -> Unit,
    ): Boolean {
        return enqueue(
            terminalAction = true,
            estimatedBytes = estimateValues(values),
            clearQueuedActions = clearQueuedActions,
        ) {
            try {
                if (!deliverySuppressed.get()) {
                    try {
                        action(mapValues(values))
                    } finally {
                        drainMicrotasks()
                    }
                }
            } finally {
                releaseWaitOnce()
            }
        }
    }

    fun resolve(
        promiseAdapter: ScriptPromiseAdapter,
        value: Any?,
        onDelivered: () -> Unit = {},
    ): Boolean {
        return try {
            terminalValues(arrayOf(value)) { mapped ->
                try {
                    promiseAdapter.resolve(mapped[0])
                } finally {
                    onDelivered()
                }
            }
        } catch (error: Throwable) {
            runCatching(onDelivered).exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    fun reject(
        promiseAdapter: ScriptPromiseAdapter,
        error: Any?,
        onDelivered: () -> Unit = {},
    ): Boolean {
        return try {
            terminalValues(arrayOf(error)) { mapped ->
                try {
                    promiseAdapter.reject(mapped[0])
                } finally {
                    onDelivered()
                }
            }
        } catch (failure: Throwable) {
            runCatching(onDelivered).exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    /**
     * Marks the operation terminal without publishing a JavaScript event or Promise result.
     */
    fun cancelSilently(action: () -> Unit = {}): Boolean {
        val acceptedTerminal = terminal.compareAndSet(false, true)
        val ownsTerminal = synchronized(queueLock) {
            if (!acceptedTerminal && terminalActionClaimed) {
                return@synchronized false
            }
            val hadQueuedAction = queue.isNotEmpty()
            if (!acceptedTerminal && !hadQueuedAction) {
                return@synchronized false
            }
            deliverySuppressed.set(true)
            queue.clear()
            queuedBytes = 0L
            acceptedTerminal || hadQueuedAction
        }
        if (!ownsTerminal) return false
        try {
            action()
        } finally {
            releaseWaitOnce()
        }
        return true
    }

    private fun mapValues(values: Array<out Any?>): Array<Any?> {
        return Array(values.size) { index -> valueMapper(values[index]) }
    }

    private fun enqueue(
        terminalAction: Boolean,
        estimatedBytes: Long,
        clearQueuedActions: Boolean = false,
        action: () -> Unit,
    ): Boolean {
        var shouldSchedule = false
        synchronized(queueLock) {
            when {
                terminalAction -> {
                    if (!terminal.compareAndSet(false, true)) return false
                }
                terminal.get() -> return false
            }
            if (clearQueuedActions) {
                queue.clear()
                queuedBytes = 0L
            }
            val actionBytes = estimatedBytes.coerceAtLeast(1L)
            if (!terminalAction &&
                (queue.size >= maxQueuedActions ||
                    actionBytes > maxQueuedBytes - queuedBytes)
            ) {
                throw ScriptDispatchQueueOverflowException(
                    maxQueuedActions,
                    maxQueuedBytes,
                )
            }
            queue.addLast(QueuedAction(actionBytes, terminalAction, action))
            queuedBytes += actionBytes
            if (!drainScheduled) {
                drainScheduled = true
                shouldSchedule = true
            }
        }
        if (shouldSchedule) {
            try {
                scheduleDrain()
            } catch (t: Throwable) {
                synchronized(queueLock) {
                    drainScheduled = false
                    queue.clear()
                    queuedBytes = 0L
                }
                if (terminal.get()) {
                    releaseWaitOnce()
                }
                throw t
            }
        }
        return true
    }

    private fun scheduleDrain() {
        val scheduler = testScheduler
        if (scheduler != null) {
            scheduler(::drain)
            return
        }
        requireNotNull(timer).setImmediate(requireNotNull(drainCallable))
    }

    /**
     * Continues draining after a listener throws so that an already queued terminal action can
     * still release the looper wait id. The first failure is rethrown after the queue is empty.
     */
    private fun drain() {
        var firstFailure: Throwable? = null
        while (true) {
            val queuedAction = synchronized(queueLock) {
                if (queue.isEmpty()) {
                    drainScheduled = false
                    null
                } else {
                    queue.removeFirst().also {
                        queuedBytes -= it.estimatedBytes
                        if (it.terminalAction) terminalActionClaimed = true
                    }
                }
            } ?: break

            try {
                queuedAction.action()
            } catch (t: Throwable) {
                if (firstFailure == null) firstFailure = t
            }
        }
        firstFailure?.let { throw it }
    }

    private fun releaseWaitOnce() {
        if (waitReleased.compareAndSet(false, true)) {
            releaseWait()
        }
    }

    private annotation class GuardedBy(val value: String)

    private fun estimateValues(values: Array<out Any?>): Long {
        var total = ARRAY_OVERHEAD_BYTES
        values.forEach { value ->
            total = saturatingEstimateAdd(total, estimateValue(value))
        }
        return total
    }

    private fun estimateValue(value: Any?): Long = when (value) {
        null -> REFERENCE_BYTES
        is CharSequence -> saturatingEstimateAdd(
            OBJECT_OVERHEAD_BYTES,
            value.length.toLong() * Char.SIZE_BYTES,
        )
        is ByteArray -> saturatingEstimateAdd(OBJECT_OVERHEAD_BYTES, value.size.toLong())
        is Array<*> -> {
            var total = ARRAY_OVERHEAD_BYTES
            value.forEach { item ->
                total = saturatingEstimateAdd(total, estimateValue(item))
            }
            total
        }
        is Map<*, *> -> {
            var total = OBJECT_OVERHEAD_BYTES
            value.forEach { (key, item) ->
                total = saturatingEstimateAdd(total, estimateValue(key))
                total = saturatingEstimateAdd(total, estimateValue(item))
            }
            total
        }
        is Iterable<*> -> {
            var total = OBJECT_OVERHEAD_BYTES
            value.forEach { item ->
                total = saturatingEstimateAdd(total, estimateValue(item))
            }
            total
        }
        is Throwable -> ERROR_ESTIMATE_BYTES
        else -> SCALAR_ESTIMATE_BYTES
    }

    private fun saturatingEstimateAdd(first: Long, second: Long): Long {
        val ceiling = maxQueuedBytes + 1L
        if (first >= ceiling || second >= ceiling || second > ceiling - first) {
            return ceiling
        }
        return first + second
    }

    companion object {

        private const val DISPATCH_FUNCTION_NAME = "__script_async_dispatch__"
        private const val DEFAULT_MAX_QUEUED_ACTIONS = 512
        private const val DEFAULT_MAX_QUEUED_BYTES = 8L * 1024L * 1024L
        private const val REFERENCE_BYTES = 8L
        private const val OBJECT_OVERHEAD_BYTES = 32L
        private const val ARRAY_OVERHEAD_BYTES = 24L
        private const val SCALAR_ESTIMATE_BYTES = 24L
        private const val ERROR_ESTIMATE_BYTES = 1_024L

        private fun captureWaitRelease(scriptRuntime: ScriptRuntime): () -> Unit {
            val waitToken = scriptRuntime.loopers.waitWhenIdleToken()
            return waitToken::release
        }

    }

}

internal class ScriptDispatchQueueOverflowException(
    maxQueuedActions: Int,
    maxQueuedBytes: Long,
) : IllegalStateException(
    "Script callback queue exceeded $maxQueuedActions actions or $maxQueuedBytes estimated bytes",
)
