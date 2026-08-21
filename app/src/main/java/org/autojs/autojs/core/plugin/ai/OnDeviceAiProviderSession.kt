package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiUsage
import org.autojs.plugin.ondeviceai.api.AiSessionStarted
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilityId
import org.autojs.plugin.ondeviceai.api.OnDeviceAiChunk
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCodec
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCompletionQuotaSnapshot
import org.autojs.plugin.ondeviceai.api.OnDeviceAiLimits
import org.autojs.plugin.ondeviceai.api.OnDeviceAiQuotaPolicy
import org.autojs.plugin.ondeviceai.api.OnDeviceAiRequest
import org.autojs.plugin.ondeviceai.api.OnDeviceAiSessionPolicy
import java.io.Closeable
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.Executor

/**
 * Request descriptors owned by this policy after [OnDeviceAiProviderSessionPolicy.beginOpen].
 *
 * A dispatched lease remains borrowed by the synchronous openSession transaction and is closed
 * only after that transaction is resolved through commitOpenSucceeded/commitOpenFailed.
 */
internal interface OnDeviceAiProviderSessionRequestDescriptorLease : Closeable {
    val count: Int
}

/**
 * Incoming callback descriptors. [copyForAsync] is the sole receiver-owned asynchronous
 * ownership-transfer boundary; callers must not assume that platform PFD duplication preserves
 * reliable-pipe error state.
 */
internal interface OnDeviceAiProviderSessionIncomingDescriptors : Closeable {
    val count: Int
    fun copyForAsync(): OnDeviceAiProviderSessionDescriptorLease
}

/** Receiver-owned callback descriptors, readable and closeable independently of Binder ingress. */
internal interface OnDeviceAiProviderSessionDescriptorLease : Closeable {
    val count: Int
    fun readDeclaredBytes(index: Int, declaredLengthBytes: Long): ByteArray
}

/** Transport-neutral remote session handle. Every method may fail and must never be retried. */
internal interface OnDeviceAiProviderSessionRemote : Closeable {
    fun grantCredits(count: Int)
    fun cancel()
}

internal interface OnDeviceAiProviderSessionCallbackIngress {
    fun onStarted(callingUid: Int, envelope: ByteArray?)
    fun onChunk(callingUid: Int, envelope: ByteArray?)
    fun onToolCalls(
        callingUid: Int,
        envelope: ByteArray?,
        descriptors: OnDeviceAiProviderSessionIncomingDescriptors?,
    )
    fun onUsage(callingUid: Int, envelope: ByteArray?)
    fun onCompleted(
        callingUid: Int,
        envelope: ByteArray?,
        descriptors: OnDeviceAiProviderSessionIncomingDescriptors?,
    )
    fun onFailed(callingUid: Int, envelope: ByteArray?)
    fun onCancelled(callingUid: Int)
}

internal data class OnDeviceAiProviderSessionOptions(
    val enabled: Boolean = false,
    /** Holds Started + the fixed initial credit window + one terminal callback. */
    val maximumBufferedCallbacks: Int = DEFAULT_MAXIMUM_BUFFERED_CALLBACKS,
) {
    init {
        require(
            maximumBufferedCallbacks in
                MINIMUM_BUFFERED_CALLBACKS..MAXIMUM_BUFFERED_CALLBACKS_LIMIT,
        )
    }

    private companion object {
        const val DEFAULT_MAXIMUM_BUFFERED_CALLBACKS = 16
        const val MINIMUM_BUFFERED_CALLBACKS = OnDeviceAiLimits.INITIAL_CHUNK_CREDITS + 2
        const val MAXIMUM_BUFFERED_CALLBACKS_LIMIT = 64
    }
}

internal enum class OnDeviceAiProviderSessionFailure {
    PLAN_REQUEST_MISMATCH,
    REQUEST_INVALID,
    REQUEST_ENVELOPE_TOO_LARGE,
    CALLBACK_BEFORE_DISPATCH,
    CALLBACK_UID_MISMATCH,
    CALLBACK_MISSING,
    CALLBACK_TOO_LARGE,
    CALLBACK_DESCRIPTOR_COUNT_INVALID,
    CALLBACK_FLOOD,
    CALLBACK_NO_CREDIT,
    CALLBACK_MALFORMED,
    CALLBACK_OUT_OF_ORDER,
    TOOLS_UNSUPPORTED,
    OPEN_FAILED,
    REMOTE_CONTROL_FAILED,
    EXECUTOR_REJECTED,
}

internal data class OnDeviceAiProviderSessionErrorSummary(
    val code: Int,
    val retryDisposition: Int,
    val providerCodePresent: Boolean,
)

internal sealed class OnDeviceAiProviderSessionEvent {
    object Disabled : OnDeviceAiProviderSessionEvent()
    data class Started(val metadata: AiSessionStarted) : OnDeviceAiProviderSessionEvent() {
        override fun toString(): String =
            "Started(firstChunkSequence=${metadata.firstChunkSequence}, " +
                "effectiveMaximumOutputBytes=${metadata.effectiveMaximumOutputBytes}, " +
                "effectiveMaximumToolRounds=${metadata.effectiveMaximumToolRounds})"
    }
    data class Chunk(val chunk: OnDeviceAiChunk) : OnDeviceAiProviderSessionEvent() {
        override fun toString(): String = "Chunk($chunk)"
    }
    data class Usage(val usage: AiUsage) : OnDeviceAiProviderSessionEvent()
    data class Completed(val result: OnDeviceAiCompletionQuotaSnapshot) : OnDeviceAiProviderSessionEvent()
    data class ProviderFailed(val error: OnDeviceAiProviderSessionErrorSummary) : OnDeviceAiProviderSessionEvent()
    object Cancelled : OnDeviceAiProviderSessionEvent()
    object TimedOut : OnDeviceAiProviderSessionEvent()
    object BinderDied : OnDeviceAiProviderSessionEvent()
    data class Rejected(val failure: OnDeviceAiProviderSessionFailure) : OnDeviceAiProviderSessionEvent()
}

internal sealed class OnDeviceAiProviderSessionOpenDecision {
    object Disabled : OnDeviceAiProviderSessionOpenDecision()
    data class Rejected(val failure: OnDeviceAiProviderSessionFailure) : OnDeviceAiProviderSessionOpenDecision()

    class Dispatch internal constructor(
        requestEnvelope: ByteArray,
        /** Borrowed from the policy until commitOpenSucceeded/commitOpenFailed returns. */
        val requestDescriptors: OnDeviceAiProviderSessionRequestDescriptorLease,
        val callback: OnDeviceAiProviderSessionCallbackIngress,
        val descriptorCount: Int,
    ) : OnDeviceAiProviderSessionOpenDecision() {
        private val safeRequestEnvelope = requestEnvelope.copyOf()

        fun copyRequestEnvelope(): ByteArray = safeRequestEnvelope.copyOf()

        override fun toString(): String =
            "Dispatch(requestBytes=${safeRequestEnvelope.size}, descriptorCount=$descriptorCount)"
    }
}

/**
 * Default-off, transport-independent owner for one AI session transcript.
 *
 * [beginOpen] is the dispatch linearization point. Callbacks arriving before the synchronous
 * openSession transaction returns are provisional: they are only bounded, copied and buffered and
 * become visible after [commitOpenSucceeded]. An external cancel/timeout/death may supersede a
 * provisional callback failure before open resolves. Request descriptors remain owned here until
 * either open commit.
 * [serializedControlExecutor] must enqueue onto a serialized non-main, non-Binder-callback lane;
 * an inline/direct executor would violate the callback identity boundary. [terminalFallbackExecutor]
 * must be an independent, serialized, non-inline lane. It is also the terminal teardown lane, so
 * it must accept every submission and must continue making progress while the primary lane is
 * blocked. Rejecting its work is a fatal owner contract breach: local and remote resources are
 * synchronously closed as a last resort and no listener is invoked on the rejecting caller thread.
 */
internal class OnDeviceAiProviderSessionPolicy(
    private val plan: OnDeviceAiHostSessionPlan,
    private val request: OnDeviceAiRequest,
    private val requestDescriptors: OnDeviceAiProviderSessionRequestDescriptorLease,
    private val serializedControlExecutor: Executor,
    private val terminalFallbackExecutor: Executor,
    private val options: OnDeviceAiProviderSessionOptions = OnDeviceAiProviderSessionOptions(),
    private val onEvent: (OnDeviceAiProviderSessionEvent) -> Unit,
) : Closeable {
    private val lock = Any()
    private val fatalCleanupLock = Any()
    private val remoteControlLock = Any()
    private val pending = java.util.TreeMap<Long, PendingCallback>()
    private val closedRemotes = Collections.newSetFromMap(
        IdentityHashMap<OnDeviceAiProviderSessionRemote, Boolean>(),
    )
    private val cancelledRemotes = Collections.newSetFromMap(
        IdentityHashMap<OnDeviceAiProviderSessionRemote, Boolean>(),
    )

    private var state = State.CREATED
    private var sessionPolicy: OnDeviceAiSessionPolicy? = null
    private var remote: OnDeviceAiProviderSessionRemote? = null
    private var requestDescriptorCloseState = RequestDescriptorCloseState.OPEN
    private var openResolved = false
    private var nextIngressSequence = 1L
    private var nextDrainSequence = 1L
    private var bufferedCallbacks = 0
    private var startedIngressClaimed = false
    private var terminalIngressClaimed = false
    private var terminalEvent: OnDeviceAiProviderSessionEvent? = null
    private var controlScheduled = false
    private val reservations = HashSet<Long>()
    private var openDispatched = false
    private var terminalCleanupDone = false
    private var terminalDelivered = false
    private val pendingRemoteClosures = ArrayDeque<RemoteClosure>()
    private var preCommitFailure: OnDeviceAiProviderSessionFailure? = null
    private var preCommitTerminal: OnDeviceAiProviderSessionEvent? = null
    private var preCommitCancelRemote = false
    private var ingressChunkCredits = 0
    private var executorUnavailable = false
    private var fallbackScheduled = false
    private var fallbackUnavailable = false
    private var preparedRequestDescriptorCount = 0
    private var terminalRemoteTeardownPending = false
    private var activeDataDeliveries = 0
    private var activeRemoteOperations = 0
    private var activeCallbackCleanups = 0
    private var activeRemoteClosures = 0
    private var fatalCleanupInProgress = false

    val callback: OnDeviceAiProviderSessionCallbackIngress = CallbackIngress()

    fun beginOpen(): OnDeviceAiProviderSessionOpenDecision {
        val decision = synchronized(lock) {
            check(state == State.CREATED) { "on-device AI session open was already started" }
            if (!options.enabled) {
                state = State.TERMINAL
                terminalEvent = OnDeviceAiProviderSessionEvent.Disabled
                terminalRemoteTeardownPending = true
                OnDeviceAiProviderSessionOpenDecision.Disabled
            } else {
                val failure = validateAndPrepareLocked()
                if (failure != null) {
                    state = State.TERMINAL
                    terminalEvent = OnDeviceAiProviderSessionEvent.Rejected(failure)
                    terminalRemoteTeardownPending = true
                    OnDeviceAiProviderSessionOpenDecision.Rejected(failure)
                } else {
                    val envelope = runCatching { OnDeviceAiCodec.encodeTextRequest(request) }.getOrNull()
                    val envelopeFailure = when {
                        envelope == null || envelope.isEmpty() ->
                            OnDeviceAiProviderSessionFailure.REQUEST_INVALID
                        envelope.size > AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES ->
                            OnDeviceAiProviderSessionFailure.REQUEST_ENVELOPE_TOO_LARGE
                        else -> null
                    }
                    if (envelopeFailure != null) {
                        state = State.TERMINAL
                        terminalEvent = OnDeviceAiProviderSessionEvent.Rejected(envelopeFailure)
                        terminalRemoteTeardownPending = true
                        OnDeviceAiProviderSessionOpenDecision.Rejected(envelopeFailure)
                    } else {
                        state = State.OPENING
                        openDispatched = true
                        OnDeviceAiProviderSessionOpenDecision.Dispatch(
                            checkNotNull(envelope),
                            requestDescriptors,
                            callback,
                            preparedRequestDescriptorCount,
                        )
                    }
                }
            }
        }
        if (decision !is OnDeviceAiProviderSessionOpenDecision.Dispatch) {
            closeRequestDescriptors()
            scheduleControl()
            scheduleTerminalLane()
        }
        return decision
    }

    fun commitOpenSucceeded(remote: OnDeviceAiProviderSessionRemote) {
        // Implemented below with remote-control serialization and buffered callback commit.
        commitRemote(remote, succeeded = true)
    }

    fun commitOpenFailed() {
        commitRemote(null, succeeded = false)
    }

    fun cancel(): Boolean = terminateExternal(OnDeviceAiProviderSessionEvent.Cancelled, cancelRemote = true)

    fun timeout(): Boolean = terminateExternal(OnDeviceAiProviderSessionEvent.TimedOut, cancelRemote = true)

    fun binderDied(): Boolean = terminateExternal(OnDeviceAiProviderSessionEvent.BinderDied, cancelRemote = false)

    override fun close() {
        cancel()
    }

    private fun validateAndPrepareLocked(): OnDeviceAiProviderSessionFailure? {
        val pinned = plan.pinnedProvider
        if (request.protocolVersion != pinned.protocolVersion ||
            request.providerId != pinned.providerId || request.modelId != plan.modelId ||
            request.options.maximumOutputBytes > plan.effectiveMaximumOutputBytes
        ) return OnDeviceAiProviderSessionFailure.PLAN_REQUEST_MISMATCH
        if (request.tools.isNotEmpty() || request.options.maximumToolRounds > 0 ||
            OnDeviceAiCapabilityId.TOOLS in request.options.requiredCapabilityIds
        ) return OnDeviceAiProviderSessionFailure.TOOLS_UNSUPPORTED
        if (request.options.persistentSession) {
            return OnDeviceAiProviderSessionFailure.REQUEST_INVALID
        }
        val required = request.options.requiredCapabilityIds.toMutableSet().apply {
            if (request.options.stream) add(OnDeviceAiCapabilityId.STREAMING)
            if (request.options.includeReasoning) add(OnDeviceAiCapabilityId.REASONING)
            if (request.options.structuredJson) add(OnDeviceAiCapabilityId.STRUCTURED_JSON)
            if (request.options.reportUsage) {
                add(OnDeviceAiCapabilityId.USAGE)
            }
        }
        if (!plan.modelCapabilityIds.containsAll(required)) {
            return OnDeviceAiProviderSessionFailure.PLAN_REQUEST_MISMATCH
        }
        val descriptorCount = runCatching { requestDescriptors.count }.getOrNull()
            ?: return OnDeviceAiProviderSessionFailure.REQUEST_INVALID
        val quota = runCatching {
            OnDeviceAiQuotaPolicy.validateRequest(request, pinned.negotiatedCapabilities, descriptorCount)
        }.getOrNull() ?: return OnDeviceAiProviderSessionFailure.REQUEST_INVALID
        if (quota.inputBytes > plan.effectiveMaximumContextBytes) {
            return OnDeviceAiProviderSessionFailure.PLAN_REQUEST_MISMATCH
        }
        preparedRequestDescriptorCount = descriptorCount
        sessionPolicy = OnDeviceAiSessionPolicy(
            maximumToolRounds = 0,
            maximumOutstandingToolCalls = pinned.negotiatedCapabilities.maximumOutstandingToolCalls,
            maximumOutputBytes = plan.effectiveMaximumOutputBytes,
            expectedRequest = request,
            maximumSessionDescriptors = pinned.negotiatedCapabilities.maximumSessionDescriptors,
        )
        return null
    }

    private fun commitRemote(remote: OnDeviceAiProviderSessionRemote?, succeeded: Boolean) {
        var closeRequest = false
        synchronized(lock) {
            if (!openDispatched) {
                if (remote != null) pendingRemoteClosures += RemoteClosure(remote, cancel = true)
                return@synchronized
            }
            if (openResolved) {
                if (remote != null && remote !== this.remote) {
                    pendingRemoteClosures += RemoteClosure(remote, cancel = true)
                }
            } else {
                openResolved = true
                closeRequest = true
                if (state == State.OPENING && succeeded && remote != null && terminalEvent == null) {
                    this.remote = remote
                    if (preCommitTerminal != null) {
                        terminalEvent = preCommitTerminal
                        cancelRemoteOnTerminal = preCommitCancelRemote
                        terminalRemoteTeardownPending = true
                        state = State.TERMINAL
                    } else {
                        preCommitFailure?.let { failure ->
                            terminalEvent = OnDeviceAiProviderSessionEvent.Rejected(failure)
                            cancelRemoteOnTerminal = true
                            terminalRemoteTeardownPending = true
                            state = State.TERMINAL
                        }
                    }
                } else if (state == State.OPENING && terminalEvent == null) {
                    terminalEvent = preCommitTerminal ?: OnDeviceAiProviderSessionEvent.Rejected(
                        OnDeviceAiProviderSessionFailure.OPEN_FAILED,
                    )
                    cancelRemoteOnTerminal = preCommitTerminal?.let { preCommitCancelRemote } ?: false
                    terminalRemoteTeardownPending = true
                    state = State.TERMINAL
                } else {
                    if (remote != null) {
                        pendingRemoteClosures += RemoteClosure(
                            remote,
                            cancel = terminalEvent !is OnDeviceAiProviderSessionEvent.BinderDied,
                        )
                    }
                }
            }
        }
        scheduleTerminalLane()
        if (closeRequest) closeRequestDescriptors()
        scheduleControl()
        scheduleTerminalLane()
    }

    private fun terminateExternal(event: OnDeviceAiProviderSessionEvent, cancelRemote: Boolean): Boolean {
        val accepted = synchronized(lock) {
            if (terminalEvent != null || preCommitTerminal != null || sessionPolicy?.terminal != null) {
                false
            } else if (openDispatched && !openResolved) {
                sessionPolicy?.requestCancel()
                preCommitTerminal = event
                preCommitCancelRemote = cancelRemote
                terminalIngressClaimed = true
                terminalRemoteTeardownPending = true
                true
            } else {
                sessionPolicy?.requestCancel()
                terminalEvent = event
                terminalIngressClaimed = true
                cancelRemoteOnTerminal = cancelRemote
                terminalRemoteTeardownPending = true
                state = State.TERMINAL
                true
            }
        }
        if (!accepted) return false
        scheduleTerminalLane()
        if (synchronized(lock) { preCommitTerminal == null }) scheduleControl(cancelRemote)
        return true
    }

    private fun scheduleControl(cancelRemote: Boolean = false) {
        if (cancelRemote) synchronized(lock) { cancelRemoteOnTerminal = true }
        if (synchronized(lock) { executorUnavailable }) {
            scheduleTerminalLane()
            return
        }
        val submit = synchronized(lock) {
            if (controlScheduled) false else {
                controlScheduled = true
                true
            }
        }
        if (!submit) return
        try {
            serializedControlExecutor.execute(::drainControlLane)
        } catch (_: RuntimeException) {
            executorRejected()
        }
    }

    private var cancelRemoteOnTerminal = false

    private fun drainControlLane() {
        while (true) {
            when (val work = synchronized(lock) { nextControlWorkLocked() }) {
                ControlWork.Idle -> return
                is ControlWork.CloseRemote -> closeClaimedRemote(work.remote, work.cancel)
                is ControlWork.CloseCallbacks -> work.callbacks.forEach(::closeClaimedCallback)
                is ControlWork.Terminal -> {
                    work.remote?.let { closeClaimedRemote(it, work.cancelRemote) }
                    closeRequestDescriptorsIfSafe()
                    deliverSettledTerminalIfReady()
                }
                ControlWork.PublishTerminal -> deliverSettledTerminalIfReady()
                ControlWork.Activate -> activateRemote()
                is ControlWork.Callback -> {
                    try {
                        processPending(work.callback)
                    } finally {
                        work.callback.close()
                        val terminalClaimed = synchronized(lock) {
                            bufferedCallbacks -= 1
                            hasClaimedTerminalLocked()
                        }
                        if (terminalClaimed) scheduleTerminalLane()
                    }
                }
            }
        }
    }

    private fun nextControlWorkLocked(): ControlWork {
        if (fatalCleanupInProgress || (executorUnavailable && fallbackUnavailable)) {
            controlScheduled = false
            return ControlWork.Idle
        }
        pendingRemoteClosures.pollFirst()?.let {
            activeRemoteClosures += 1
            return ControlWork.CloseRemote(it.remote, it.cancel)
        }
        val terminal = terminalEvent
        if (terminal != null && openDispatched && !openResolved) {
            controlScheduled = false
            return ControlWork.Idle
        }
        if (terminal != null && pending.isNotEmpty()) {
            val callbacks = pending.values.toList()
            pending.clear()
            bufferedCallbacks -= callbacks.size
            activeCallbackCleanups += callbacks.size
            return ControlWork.CloseCallbacks(callbacks)
        }
        if (terminal != null && (
                bufferedCallbacks > 0 || activeDataDeliveries > 0 || activeRemoteOperations > 0 ||
                    activeCallbackCleanups > 0 || activeRemoteClosures > 0 ||
                    requestDescriptorCloseState != RequestDescriptorCloseState.CLOSED
            )
        ) {
            controlScheduled = false
            return ControlWork.Idle
        }
        if (terminal != null && !terminalCleanupDone) {
            terminalCleanupDone = true
            terminalRemoteTeardownPending = false
            if (remote != null) activeRemoteClosures += 1
            return ControlWork.Terminal(
                remote = remote,
                cancelRemote = cancelRemoteOnTerminal,
            )
        }
        if (terminal != null) {
            if (!terminalDelivered) return ControlWork.PublishTerminal
            controlScheduled = false
            return ControlWork.Idle
        }
        if (state == State.OPENING && openResolved && remote != null) {
            state = State.ACTIVATING
            return ControlWork.Activate
        }
        if (state == State.COMMITTED) {
            pending.remove(nextDrainSequence)?.let {
                nextDrainSequence += 1L
                return ControlWork.Callback(it)
            }
        }
        controlScheduled = false
        return ControlWork.Idle
    }

    private fun activateRemote(): Boolean {
        val active: OnDeviceAiProviderSessionRemote
        val initial: Int
        synchronized(lock) {
            if (terminalEvent != null) return false
            active = remote ?: return false
            initial = if (request.options.stream && !terminalIngressClaimed) {
                OnDeviceAiLimits.INITIAL_CHUNK_CREDITS
            } else {
                0
            }
            if (initial > 0) sessionPolicy!!.grantCredits(initial)
            ingressChunkCredits = initial
            state = State.COMMITTED
            if (initial > 0) activeRemoteOperations += 1
        }
        if (initial > 0) {
            try {
                active.grantCredits(initial)
            } catch (_: Throwable) {
                synchronized(lock) { ingressChunkCredits = 0 }
                terminateOnLane(
                    OnDeviceAiProviderSessionEvent.Rejected(
                        OnDeviceAiProviderSessionFailure.REMOTE_CONTROL_FAILED,
                    ),
                    cancelRemote = true,
                )
                return false
            } finally {
                releaseRemoteOperation()
            }
        }
        return true
    }

    private fun executorRejected() {
        val event = OnDeviceAiProviderSessionEvent.Rejected(
            OnDeviceAiProviderSessionFailure.EXECUTOR_REJECTED,
        )
        synchronized(lock) {
            controlScheduled = false
            executorUnavailable = true
            if (terminalEvent == null && preCommitTerminal == null) {
                terminalEvent = event
                state = State.TERMINAL
                cancelRemoteOnTerminal = true
                terminalRemoteTeardownPending = true
            }
        }
        scheduleTerminalLane()
    }

    private fun scheduleTerminalLane() {
        val action = synchronized(lock) {
            when {
                fatalCleanupInProgress -> TerminalLaneSubmission.SUPPRESSED_FATAL_CLEANUP
                fallbackUnavailable -> TerminalLaneSubmission.FATAL_CONTRACT_BREACH
                fallbackScheduled -> TerminalLaneSubmission.ALREADY_SCHEDULED
                !hasTerminalLaneWorkLocked() -> TerminalLaneSubmission.NO_WORK
                else -> {
                    fallbackScheduled = true
                    TerminalLaneSubmission.SUBMIT
                }
            }
        }
        when (action) {
            TerminalLaneSubmission.NO_WORK,
            TerminalLaneSubmission.ALREADY_SCHEDULED,
            TerminalLaneSubmission.SUPPRESSED_FATAL_CLEANUP,
            -> return
            TerminalLaneSubmission.FATAL_CONTRACT_BREACH ->
                fatalTerminalExecutorBreach(null)
            TerminalLaneSubmission.SUBMIT -> Unit
        }
        try {
            terminalFallbackExecutor.execute(::runTerminalLane)
        } catch (failure: RuntimeException) {
            synchronized(lock) {
                fallbackScheduled = false
                fallbackUnavailable = true
            }
            fatalTerminalExecutorBreach(failure)
        }
    }

    private fun runTerminalLane() {
        teardownRemoteImmediately()
        if (synchronized(lock) { hasClaimedTerminalLocked() }) discardPending()
        closeRequestDescriptorsIfSafe()
        deliverSettledTerminalIfReady()
        val reschedule = synchronized(lock) {
            fallbackScheduled = false
            hasTerminalLaneWorkLocked()
        }
        if (reschedule) scheduleTerminalLane()
    }

    private fun hasClaimedTerminalLocked(): Boolean =
        terminalEvent != null || preCommitFailure != null || preCommitTerminal != null

    private fun hasTerminalLaneWorkLocked(): Boolean {
        if (pendingRemoteClosures.isNotEmpty()) return true
        if (!hasClaimedTerminalLocked()) return false
        if (terminalRemoteTeardownPending) return true
        if (pending.isNotEmpty()) return true
        return terminalEvent != null && !terminalDelivered && reservations.isEmpty() &&
            bufferedCallbacks == 0 &&
            activeDataDeliveries == 0 && activeRemoteOperations == 0 &&
            activeCallbackCleanups == 0 && activeRemoteClosures == 0 &&
            requestDescriptorCloseState == RequestDescriptorCloseState.CLOSED &&
            (!openDispatched || openResolved)
    }

    private fun teardownRemoteImmediately() {
        val teardown = synchronized(lock) {
            val closures = pendingRemoteClosures.toList()
            pendingRemoteClosures.clear()
            val closePrimary = hasClaimedTerminalLocked() && terminalRemoteTeardownPending
            if (closePrimary) terminalRemoteTeardownPending = false
            activeRemoteClosures += closures.size +
                (if (closePrimary && remote != null) 1 else 0)
            RemoteTeardown(
                remote = remote.takeIf { closePrimary },
                cancelRemote = cancelRemoteOnTerminal,
                closures = closures,
            )
        }
        teardown.remote?.let { closeClaimedRemote(it, teardown.cancelRemote) }
        teardown.closures.forEach { closeClaimedRemote(it.remote, it.cancel) }
    }

    private fun deliverSettledTerminalIfReady() {
        val event = synchronized(lock) {
            if (terminalDelivered || terminalEvent == null || pendingRemoteClosures.isNotEmpty() ||
                terminalRemoteTeardownPending || pending.isNotEmpty() || reservations.isNotEmpty() ||
                bufferedCallbacks != 0 || activeDataDeliveries != 0 ||
                activeRemoteOperations != 0 || activeCallbackCleanups != 0 ||
                activeRemoteClosures != 0 ||
                requestDescriptorCloseState != RequestDescriptorCloseState.CLOSED ||
                (openDispatched && !openResolved)
            ) {
                null
            } else {
                terminalDelivered = true
                terminalEvent
            }
        } ?: return
        deliver(event)
    }

    private fun fatalTerminalExecutorBreach(cause: RuntimeException?): Nothing {
        synchronized(fatalCleanupLock) {
            synchronized(lock) {
                fatalCleanupInProgress = true
                executorUnavailable = true
                fallbackUnavailable = true
                controlScheduled = false
                fallbackScheduled = false
                if (terminalEvent == null && preCommitTerminal == null) {
                    terminalEvent = OnDeviceAiProviderSessionEvent.Rejected(
                        OnDeviceAiProviderSessionFailure.EXECUTOR_REJECTED,
                    )
                    state = State.TERMINAL
                    cancelRemoteOnTerminal = true
                    terminalRemoteTeardownPending = true
                }
            }
            var cleanupReleased = false
            try {
                while (true) {
                    teardownRemoteImmediately()
                    if (synchronized(lock) { hasClaimedTerminalLocked() }) discardPending()
                    closeRequestDescriptorsIfSafe()
                    val complete = synchronized(lock) {
                        val requestCloseReady = (!openDispatched || openResolved) &&
                            requestDescriptorCloseState == RequestDescriptorCloseState.OPEN
                        val moreWork = pendingRemoteClosures.isNotEmpty() || pending.isNotEmpty() ||
                            terminalRemoteTeardownPending || requestCloseReady
                        if (!moreWork) {
                            fatalCleanupInProgress = false
                            cleanupReleased = true
                        }
                        !moreWork
                    }
                    if (complete) break
                }
            } finally {
                if (!cleanupReleased) synchronized(lock) { fatalCleanupInProgress = false }
            }
        }
        throw IllegalStateException(
            "terminalFallbackExecutor must not reject AI session terminal cleanup",
            cause,
        )
    }

    private fun closeRequestDescriptorsIfSafe() {
        val safe = synchronized(lock) { !openDispatched || openResolved }
        if (safe) closeRequestDescriptors()
    }

    private fun closeRemoteOnce(remote: OnDeviceAiProviderSessionRemote, cancel: Boolean) {
        synchronized(remoteControlLock) {
            if (cancel && cancelledRemotes.add(remote)) runCatching(remote::cancel)
            if (closedRemotes.add(remote)) runCatching(remote::close)
        }
    }

    private fun closeClaimedRemote(remote: OnDeviceAiProviderSessionRemote, cancel: Boolean) {
        try {
            closeRemoteOnce(remote, cancel)
        } finally {
            val terminalClaimed = synchronized(lock) {
                check(activeRemoteClosures > 0)
                activeRemoteClosures -= 1
                hasClaimedTerminalLocked()
            }
            if (terminalClaimed) scheduleAfterReservationSettled()
        }
    }

    private fun closeRequestDescriptors() {
        val close = synchronized(lock) {
            if (requestDescriptorCloseState != RequestDescriptorCloseState.OPEN) {
                false
            } else {
                requestDescriptorCloseState = RequestDescriptorCloseState.CLOSING
                true
            }
        }
        if (!close) return
        runCatching(requestDescriptors::close)
        val terminalClaimed = synchronized(lock) {
            requestDescriptorCloseState = RequestDescriptorCloseState.CLOSED
            hasClaimedTerminalLocked()
        }
        if (terminalClaimed) scheduleAfterReservationSettled()
    }

    private fun deliver(event: OnDeviceAiProviderSessionEvent) {
        runCatching { onEvent(event) }
    }

    private inner class CallbackIngress : OnDeviceAiProviderSessionCallbackIngress {
        override fun onStarted(callingUid: Int, envelope: ByteArray?) =
            acceptEnvelope(CallbackKind.STARTED, callingUid, envelope, null)

        override fun onChunk(callingUid: Int, envelope: ByteArray?) =
            acceptEnvelope(CallbackKind.CHUNK, callingUid, envelope, null)

        override fun onToolCalls(
            callingUid: Int,
            envelope: ByteArray?,
            descriptors: OnDeviceAiProviderSessionIncomingDescriptors?,
        ) = acceptEnvelope(CallbackKind.TOOL_CALLS, callingUid, envelope, descriptors)

        override fun onUsage(callingUid: Int, envelope: ByteArray?) =
            acceptEnvelope(CallbackKind.USAGE, callingUid, envelope, null)

        override fun onCompleted(
            callingUid: Int,
            envelope: ByteArray?,
            descriptors: OnDeviceAiProviderSessionIncomingDescriptors?,
        ) = acceptEnvelope(CallbackKind.COMPLETED, callingUid, envelope, descriptors)

        override fun onFailed(callingUid: Int, envelope: ByteArray?) =
            acceptEnvelope(CallbackKind.FAILED, callingUid, envelope, null)

        override fun onCancelled(callingUid: Int) =
            acceptEnvelope(CallbackKind.CANCELLED, callingUid, null, null)
    }

    private fun acceptEnvelope(
        kind: CallbackKind,
        callingUid: Int,
        envelope: ByteArray?,
        incoming: OnDeviceAiProviderSessionIncomingDescriptors?,
    ) {
        if (!OnDeviceAiProviderCallbackCallerPolicy.isExpectedUid(plan.pinnedProvider, callingUid)) {
            rejectIngressWithIncomingCleanup(
                OnDeviceAiProviderSessionFailure.CALLBACK_UID_MISMATCH,
                incoming,
            )
            return
        }
        val needsDescriptors = kind == CallbackKind.TOOL_CALLS || kind == CallbackKind.COMPLETED
        if (kind != CallbackKind.CANCELLED) {
            if (envelope == null || envelope.isEmpty()) {
                rejectIngressWithIncomingCleanup(
                    OnDeviceAiProviderSessionFailure.CALLBACK_MISSING,
                    incoming,
                )
                return
            }
            val maximum = if (kind == CallbackKind.CHUNK) {
                OnDeviceAiLimits.MAX_CHUNK_ENVELOPE_BYTES
            } else {
                AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES
            }
            if (envelope.size > maximum) {
                rejectIngressWithIncomingCleanup(
                    OnDeviceAiProviderSessionFailure.CALLBACK_TOO_LARGE,
                    incoming,
                )
                return
            }
        }
        val incomingCount = if (needsDescriptors) {
            if (incoming == null) {
                rejectIngress(OnDeviceAiProviderSessionFailure.CALLBACK_MISSING)
                return
            }
            runCatching { incoming.count }.getOrNull()
        } else {
            null
        }
        if (needsDescriptors && (
                incomingCount == null || incomingCount !in 0..maximumSessionDescriptors()
            )
        ) {
            rejectIngressWithIncomingCleanup(
                OnDeviceAiProviderSessionFailure.CALLBACK_DESCRIPTOR_COUNT_INVALID,
                incoming,
            )
            return
        }

        var reservationFailure: OnDeviceAiProviderSessionFailure? = null
        var sequence = 0L
        var ignore = false
        synchronized(lock) {
            when {
                terminalEvent != null || preCommitFailure != null || preCommitTerminal != null ->
                    ignore = true
                state == State.CREATED ->
                    reservationFailure = OnDeviceAiProviderSessionFailure.CALLBACK_BEFORE_DISPATCH
                kind == CallbackKind.CHUNK && state != State.COMMITTED ->
                    reservationFailure = OnDeviceAiProviderSessionFailure.CALLBACK_OUT_OF_ORDER
                kind == CallbackKind.CHUNK && ingressChunkCredits <= 0 ->
                    reservationFailure = OnDeviceAiProviderSessionFailure.CALLBACK_NO_CREDIT
                terminalIngressClaimed ->
                    reservationFailure = OnDeviceAiProviderSessionFailure.CALLBACK_OUT_OF_ORDER
                kind == CallbackKind.STARTED && startedIngressClaimed ->
                    reservationFailure = OnDeviceAiProviderSessionFailure.CALLBACK_OUT_OF_ORDER
                bufferedCallbacks >= options.maximumBufferedCallbacks ->
                    reservationFailure = OnDeviceAiProviderSessionFailure.CALLBACK_FLOOD
                else -> {
                    sequence = nextIngressSequence++
                    reservations += sequence
                    bufferedCallbacks += 1
                    if (kind == CallbackKind.CHUNK) ingressChunkCredits -= 1
                    if (kind == CallbackKind.STARTED) startedIngressClaimed = true
                    if (kind == CallbackKind.COMPLETED || kind == CallbackKind.FAILED ||
                        kind == CallbackKind.CANCELLED || kind == CallbackKind.TOOL_CALLS
                    ) {
                        terminalIngressClaimed = true
                    }
                }
            }
        }
        if (ignore || reservationFailure != null) {
            if (reservationFailure != null) {
                rejectIngressWithIncomingCleanup(checkNotNull(reservationFailure), incoming)
            } else {
                closeIncomingSettled(incoming)
            }
            return
        }

        var envelopeCopy: ByteArray? = null
        var descriptorCopy: OnDeviceAiProviderSessionDescriptorLease? = null
        var copyFailed = false
        try {
            envelopeCopy = envelope?.copyOf()
            descriptorCopy = if (needsDescriptors) checkNotNull(incoming).copyForAsync() else null
        } catch (_: Throwable) {
            copyFailed = true
        } finally {
            runCatching { incoming?.close() }
        }
        if (copyFailed) {
            releaseReservation(sequence)
            rejectIngress(OnDeviceAiProviderSessionFailure.CALLBACK_DESCRIPTOR_COUNT_INVALID)
            return
        }
        if (descriptorCopy != null && runCatching { descriptorCopy.count }.getOrNull() != incomingCount) {
            descriptorCopy.closeQuietly()
            releaseReservation(sequence)
            rejectIngress(OnDeviceAiProviderSessionFailure.CALLBACK_DESCRIPTOR_COUNT_INVALID)
            return
        }
        val callback = PendingCallback.Value(kind, envelopeCopy, descriptorCopy)
        val accepted = synchronized(lock) {
            reservations.remove(sequence)
            if (terminalEvent != null || preCommitFailure != null || preCommitTerminal != null) {
                bufferedCallbacks -= 1
                activeCallbackCleanups += 1
                false
            } else {
                pending[sequence] = callback
                true
            }
        }
        if (!accepted) {
            closeClaimedCallback(callback)
            return
        }
        scheduleControl()
    }

    private fun processPending(callback: PendingCallback) {
        if (synchronized(lock) { terminalEvent != null }) return
        try {
            when (callback.kind) {
                CallbackKind.STARTED -> {
                    val metadata = OnDeviceAiCodec.decodeSessionStarted(checkNotNull(callback.envelope))
                    checkNotNull(sessionPolicy).onStarted(metadata)
                    deliverIfActive(OnDeviceAiProviderSessionEvent.Started(metadata))
                }
                CallbackKind.CHUNK -> {
                    val chunk = OnDeviceAiCodec.decodeTextChunk(checkNotNull(callback.envelope))
                    checkNotNull(sessionPolicy).onChunk(chunk)
                    if (deliverIfActive(OnDeviceAiProviderSessionEvent.Chunk(chunk))) {
                        replenishOneCredit()
                    }
                }
                CallbackKind.USAGE -> {
                    val usage = AiCommonCodec.decodeUsage(checkNotNull(callback.envelope))
                    checkNotNull(sessionPolicy).onUsage(usage)
                    deliverIfActive(OnDeviceAiProviderSessionEvent.Usage(usage))
                }
                CallbackKind.COMPLETED -> {
                    val descriptors = checkNotNull(callback.descriptors)
                    val result = OnDeviceAiCodec.decodeCompletionResult(checkNotNull(callback.envelope))
                    val completed = checkNotNull(sessionPolicy).onCompleted(
                        result = result,
                        descriptorCount = descriptors.count,
                        readDescriptor = descriptors::readDeclaredBytes,
                    )
                    terminateOnLane(OnDeviceAiProviderSessionEvent.Completed(completed), cancelRemote = false)
                }
                CallbackKind.FAILED -> {
                    val error = AiCommonCodec.decodeError(checkNotNull(callback.envelope))
                    checkNotNull(sessionPolicy).onFailed()
                    terminateOnLane(
                        OnDeviceAiProviderSessionEvent.ProviderFailed(
                            OnDeviceAiProviderSessionErrorSummary(
                                code = error.code,
                                retryDisposition = error.retryDisposition,
                                providerCodePresent = error.providerCode != null,
                            ),
                        ),
                        cancelRemote = false,
                    )
                }
                CallbackKind.CANCELLED -> {
                    checkNotNull(sessionPolicy).onCancelled()
                    terminateOnLane(OnDeviceAiProviderSessionEvent.Cancelled, cancelRemote = false)
                }
                CallbackKind.TOOL_CALLS -> {
                    checkNotNull(callback.descriptors)
                    OnDeviceAiCodec.decodeToolCallBatch(checkNotNull(callback.envelope))
                    terminateOnLane(
                        OnDeviceAiProviderSessionEvent.Rejected(
                            OnDeviceAiProviderSessionFailure.TOOLS_UNSUPPORTED,
                        ),
                        cancelRemote = true,
                    )
                }
            }
        } catch (_: Throwable) {
            if (synchronized(lock) { terminalEvent == null }) {
                terminateOnLane(
                    OnDeviceAiProviderSessionEvent.Rejected(
                        OnDeviceAiProviderSessionFailure.CALLBACK_MALFORMED,
                    ),
                    cancelRemote = true,
                )
            }
        }
    }

    private fun replenishOneCredit() {
        val active = synchronized(lock) {
            if (terminalEvent != null || terminalIngressClaimed || state != State.COMMITTED) return
            checkNotNull(sessionPolicy).grantCredits(1)
            ingressChunkCredits += 1
            activeRemoteOperations += 1
            checkNotNull(remote)
        }
        try {
            active.grantCredits(1)
        } catch (_: Throwable) {
            synchronized(lock) { ingressChunkCredits = 0 }
            terminateOnLane(
                OnDeviceAiProviderSessionEvent.Rejected(
                    OnDeviceAiProviderSessionFailure.REMOTE_CONTROL_FAILED,
                ),
                cancelRemote = true,
            )
        } finally {
            releaseRemoteOperation()
        }
    }

    private fun terminateOnLane(event: OnDeviceAiProviderSessionEvent, cancelRemote: Boolean) {
        val accepted = synchronized(lock) {
            if (terminalEvent != null) {
                false
            } else {
                if (cancelRemote) sessionPolicy?.requestCancel()
                terminalEvent = event
                terminalIngressClaimed = true
                cancelRemoteOnTerminal = cancelRemote
                terminalRemoteTeardownPending = true
                state = State.TERMINAL
                true
            }
        }
        if (accepted) scheduleTerminalLane()
    }

    private fun rejectIngress(failure: OnDeviceAiProviderSessionFailure) {
        val accepted = synchronized(lock) {
            if (terminalEvent != null || preCommitFailure != null || preCommitTerminal != null) {
                false
            } else if (state == State.OPENING && !openResolved) {
                sessionPolicy?.requestCancel()
                preCommitFailure = failure
                terminalIngressClaimed = true
                terminalRemoteTeardownPending = true
                true
            } else {
                sessionPolicy?.requestCancel()
                terminalEvent = OnDeviceAiProviderSessionEvent.Rejected(failure)
                terminalIngressClaimed = true
                cancelRemoteOnTerminal = true
                terminalRemoteTeardownPending = true
                state = State.TERMINAL
                true
            }
        }
        if (accepted) {
            scheduleTerminalLane()
            if (synchronized(lock) { preCommitFailure == null }) {
                scheduleControl(cancelRemote = true)
            }
        }
    }

    private fun rejectIngressWithIncomingCleanup(
        failure: OnDeviceAiProviderSessionFailure,
        incoming: OnDeviceAiProviderSessionIncomingDescriptors?,
    ) {
        if (incoming == null) {
            rejectIngress(failure)
            return
        }
        synchronized(lock) { activeCallbackCleanups += 1 }
        try {
            rejectIngress(failure)
        } finally {
            runCatching(incoming::close)
            releaseCallbackCleanup()
        }
    }

    private fun closeIncomingSettled(incoming: OnDeviceAiProviderSessionIncomingDescriptors?) {
        if (incoming == null) return
        synchronized(lock) { activeCallbackCleanups += 1 }
        runCatching(incoming::close)
        releaseCallbackCleanup()
    }

    private fun releaseReservation(sequence: Long) {
        val settledTerminal = synchronized(lock) {
            if (reservations.remove(sequence)) bufferedCallbacks -= 1
            hasClaimedTerminalLocked()
        }
        if (settledTerminal) scheduleAfterReservationSettled()
    }

    private fun scheduleAfterReservationSettled() {
        val availability = synchronized(lock) {
            if (fatalCleanupInProgress) return
            executorUnavailable to fallbackUnavailable
        }
        if (!availability.second) scheduleTerminalLane()
        if (!availability.first) scheduleControl()
    }

    private fun releaseRemoteOperation() {
        val terminalClaimed = synchronized(lock) {
            check(activeRemoteOperations > 0)
            activeRemoteOperations -= 1
            hasClaimedTerminalLocked()
        }
        if (terminalClaimed) scheduleAfterReservationSettled()
    }

    private fun discardPending() {
        val values = synchronized(lock) {
            val copy = pending.values.toList()
            pending.clear()
            bufferedCallbacks -= copy.size
            activeCallbackCleanups += copy.size
            copy
        }
        values.forEach(::closeClaimedCallback)
    }

    private fun closeClaimedCallback(callback: PendingCallback) {
        try {
            callback.close()
        } finally {
            releaseCallbackCleanup()
        }
    }

    private fun releaseCallbackCleanup() {
        val terminalClaimed = synchronized(lock) {
            check(activeCallbackCleanups > 0)
            activeCallbackCleanups -= 1
            hasClaimedTerminalLocked()
        }
        if (terminalClaimed) scheduleAfterReservationSettled()
    }

    private fun deliverIfActive(event: OnDeviceAiProviderSessionEvent): Boolean {
        val claimed = synchronized(lock) {
            if (terminalEvent != null || preCommitTerminal != null) {
                false
            } else {
                activeDataDeliveries += 1
                true
            }
        }
        if (!claimed) return false
        try {
            deliver(event)
        } finally {
            val terminalClaimed = synchronized(lock) {
                check(activeDataDeliveries > 0)
                activeDataDeliveries -= 1
                hasClaimedTerminalLocked()
            }
            if (terminalClaimed) scheduleAfterReservationSettled()
        }
        return true
    }

    private fun maximumSessionDescriptors(): Int = minOf(
        plan.pinnedProvider.negotiatedCapabilities.maximumSessionDescriptors,
        AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION,
    )

    private fun OnDeviceAiProviderSessionDescriptorLease.closeQuietly() {
        runCatching(::close)
    }

    private sealed class PendingCallback(
        val kind: CallbackKind,
        val envelope: ByteArray?,
        val descriptors: OnDeviceAiProviderSessionDescriptorLease?,
    ) : Closeable {
        class Value(
            kind: CallbackKind,
            envelope: ByteArray?,
            descriptors: OnDeviceAiProviderSessionDescriptorLease?,
        ) : PendingCallback(kind, envelope, descriptors)

        override fun close() {
            runCatching { descriptors?.close() }
        }
    }

    private data class RemoteClosure(
        val remote: OnDeviceAiProviderSessionRemote,
        val cancel: Boolean,
    )

    private data class RemoteTeardown(
        val remote: OnDeviceAiProviderSessionRemote?,
        val cancelRemote: Boolean,
        val closures: List<RemoteClosure>,
    )

    private enum class TerminalLaneSubmission {
        NO_WORK,
        ALREADY_SCHEDULED,
        SUPPRESSED_FATAL_CLEANUP,
        SUBMIT,
        FATAL_CONTRACT_BREACH,
    }

    private sealed class ControlWork {
        object Idle : ControlWork()
        object Activate : ControlWork()
        object PublishTerminal : ControlWork()
        data class Callback(val callback: PendingCallback) : ControlWork()
        data class CloseCallbacks(val callbacks: List<PendingCallback>) : ControlWork()
        data class CloseRemote(
            val remote: OnDeviceAiProviderSessionRemote,
            val cancel: Boolean,
        ) : ControlWork()
        data class Terminal(
            val remote: OnDeviceAiProviderSessionRemote?,
            val cancelRemote: Boolean,
        ) : ControlWork()
    }

    private enum class CallbackKind {
        STARTED,
        CHUNK,
        TOOL_CALLS,
        USAGE,
        COMPLETED,
        FAILED,
        CANCELLED,
    }

    private enum class State {
        CREATED,
        OPENING,
        ACTIVATING,
        COMMITTED,
        TERMINAL,
    }

    private enum class RequestDescriptorCloseState {
        OPEN,
        CLOSING,
        CLOSED,
    }
}
