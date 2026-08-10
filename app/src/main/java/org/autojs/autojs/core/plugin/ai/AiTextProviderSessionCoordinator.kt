package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiProviderInfo
import org.autojs.plugin.ai.text.api.AiTextCapabilities
import org.autojs.plugin.ai.text.api.AiTextCodec
import org.autojs.plugin.ai.text.api.AiTextLimits
import org.autojs.plugin.ai.text.api.AiTextRequest
import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Opaque transport-owned connection until its provider Binder descriptor is verified. */
internal interface AiTextProviderSessionConnection

internal interface AiTextProviderSessionBinding : Closeable

internal interface AiTextProviderSessionBindingCallback {
    fun onConnected(connection: AiTextProviderSessionConnection)

    fun onBindingFailure(failure: AiTextProviderSessionRunFailure)

    fun onBinderDeath()
}

/**
 * Counts blocking Android-side work that must settle before the process gate is released.
 *
 * Android request/result descriptor readers and remote-session controls must acquire a lease
 * around each potentially blocking operation. A returned lease is idempotent and must be closed
 * in a `finally` block.
 */
internal fun interface AiTextProviderSessionOperationObserver {
    fun begin(): Closeable
}

/**
 * One callback registration prepared for exactly one synchronous `openSession` transaction.
 *
 * [open] must neither duplicate nor close [requestDescriptors]. The policy owns that lease until
 * the coordinator commits the synchronous return or failure. Implementations must validate the
 * returned session Binder descriptor, link its death recipient, and only then return a remote.
 * Closing this object detaches its callback sink; late callback PFDs must still be closed locally.
 */
internal interface AiTextProviderSessionPreparedOpen : Closeable {
    fun open(
        requestEnvelope: ByteArray,
        requestDescriptors: AiTextProviderSessionRequestDescriptorLease,
    ): AiTextProviderSessionRemote
}

/** Descriptor-verified view of the same provider Binder used for metadata and session opening. */
internal interface AiTextProviderSessionRpc {
    fun getProviderInfoEnvelope(): ByteArray?

    fun getCapabilitiesEnvelope(): ByteArray?

    fun prepareOpen(
        callback: AiTextProviderSessionCallbackIngress,
        onSessionBinderDeath: () -> Unit,
        operationObserver: AiTextProviderSessionOperationObserver,
    ): AiTextProviderSessionPreparedOpen
}

/** Exact-component boundary for one explicitly invoked, default-off Android session run. */
internal interface AiTextProviderSessionTransport {
    fun inspectExact(component: AiTextProviderComponent): AiTextProviderDiscoverySourceResult

    fun bindExact(
        snapshot: AiTextProviderPackageSnapshot,
        callback: AiTextProviderSessionBindingCallback,
    ): AiTextProviderSessionBinding

    /** Must verify `IAiTextProvider.DESCRIPTOR` before returning the RPC view. */
    fun openRpc(connection: AiTextProviderSessionConnection): AiTextProviderSessionRpc
}

/** Marker exceptions avoid retaining or logging hostile remote descriptor strings. */
internal class AiTextProviderSessionProviderDescriptorMismatchException : SecurityException()

internal class AiTextProviderRemoteSessionDescriptorMismatchException : SecurityException()

internal class AiTextProviderRemoteSessionDeadException : IllegalStateException()

internal fun interface AiTextProviderSessionDeadlineScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): Closeable
}

internal fun interface AiTextProviderSessionClock {
    fun elapsedRealtimeMillis(): Long
}

internal data class AiTextProviderSessionRunOptions(
    val enabled: Boolean = false,
    val maximumBufferedCallbacks: Int = 16,
) {
    init {
        require(
            maximumBufferedCallbacks in
                (AiTextLimits.INITIAL_CHUNK_CREDITS + 2)..MAXIMUM_BUFFERED_CALLBACKS,
        ) { "Invalid AI text session callback capacity" }
    }

    private companion object {
        const val MAXIMUM_BUFFERED_CALLBACKS = 64
    }
}

internal data class AiTextProviderSessionRunRequest(
    val snapshot: AiTextProviderPackageSnapshot,
    val plan: AiTextHostSessionPlan,
    val request: AiTextRequest,
    val requestDescriptors: AiTextProviderSessionRequestDescriptorLease,
    val environment: AiTextProviderEnvironment,
    val hostCapabilities: AiTextCapabilities,
    val requirement: AiTextCapabilityRequirement = AiTextCapabilityRequirement(),
    val options: AiTextProviderSessionRunOptions = AiTextProviderSessionRunOptions(),
)

internal enum class AiTextProviderSessionRunFailure {
    PINNED_IDENTITY_MISMATCH,
    IDENTITY_DRIFT,
    BIND_REJECTED,
    BIND_SECURITY,
    UNEXPECTED_COMPONENT,
    DUPLICATE_CONNECTION,
    NULL_BINDING,
    DISCONNECTED,
    PROVIDER_DESCRIPTOR_MISMATCH,
    SESSION_DESCRIPTOR_MISMATCH,
    REMOTE_SECURITY,
    REMOTE_FAILURE,
    PROVIDER_INFO_MISSING,
    PROVIDER_INFO_TOO_LARGE,
    PROVIDER_INFO_INVALID,
    CAPABILITIES_MISSING,
    CAPABILITIES_TOO_LARGE,
    CAPABILITIES_INVALID,
    PINNED_METADATA_MISMATCH,
    TRANSCRIPT_REJECTED,
    EXECUTOR_REJECTED,
    INTERNAL_FAILURE,
}

internal sealed class AiTextProviderSessionRunEvent {
    object Busy : AiTextProviderSessionRunEvent()
    object Fused : AiTextProviderSessionRunEvent()

    data class Transcript(
        val event: AiTextProviderSessionEvent,
    ) : AiTextProviderSessionRunEvent()

    data class Rejected(
        val failure: AiTextProviderSessionRunFailure,
        val transcriptFailure: AiTextProviderSessionFailure? = null,
    ) : AiTextProviderSessionRunEvent()
}

internal interface AiTextProviderSessionRunHandle : Closeable {
    fun cancel(): Boolean
}

/** Process-lifetime, single-active admission and per-component fuse state. */
internal class AiTextProviderSessionProcessOwner {
    private val lock = Any()
    private var active = false
    private val fusedComponents = HashSet<AiTextProviderComponent>()

    internal fun tryAcquire(component: AiTextProviderComponent): AiTextProviderSessionAdmission =
        synchronized(lock) {
            when {
                component in fusedComponents -> AiTextProviderSessionAdmission.FUSED
                active -> AiTextProviderSessionAdmission.BUSY
                else -> {
                    active = true
                    AiTextProviderSessionAdmission.ACQUIRED
                }
            }
        }

    internal fun fuse(component: AiTextProviderComponent) {
        synchronized(lock) { fusedComponents += component }
    }

    internal fun release() {
        synchronized(lock) {
            check(active) { "AI text session process owner was not acquired" }
            active = false
        }
    }
}

internal enum class AiTextProviderSessionAdmission {
    ACQUIRED,
    BUSY,
    FUSED,
}

/**
 * Default-off owner for an exact-component Android session run.
 *
 * The implementation below deliberately has no production call site. It binds and dispatches only
 * when [AiTextProviderSessionRunOptions.enabled] is explicitly true.
 */
internal class AiTextProviderSessionCoordinator(
    private val transport: AiTextProviderSessionTransport,
    workerExecutor: Executor,
    private val blockingExecutor: Executor,
    private val terminalFallbackExecutor: Executor,
    private val deadlineScheduler: AiTextProviderSessionDeadlineScheduler,
    private val clock: AiTextProviderSessionClock,
    private val owner: AiTextProviderSessionProcessOwner,
) {
    private val lane = SessionCoordinatorSerialExecutor(workerExecutor)

    fun start(
        request: AiTextProviderSessionRunRequest,
        onEvent: (AiTextProviderSessionRunEvent) -> Unit,
    ): AiTextProviderSessionRunHandle = Attempt(request, onEvent).start()

    private inner class Attempt(
        private val request: AiTextProviderSessionRunRequest,
        private val onEvent: (AiTextProviderSessionRunEvent) -> Unit,
    ) {
        private val lifecycleLock = Any()
        private val bindingSlot = SessionCoordinatorCloseSlot<AiTextProviderSessionBinding>()
        private val preparedOpenSlot = SessionCoordinatorCloseSlot<AiTextProviderSessionPreparedOpen>()
        private val deadlineSlot = SessionCoordinatorCloseSlot<Closeable>()
        private val connectionClaimed = AtomicBoolean(false)
        private val deadlineAtMillis = saturatingAdd(
            clock.elapsedRealtimeMillis(),
            request.request.options.timeoutMillis,
        )
        private val operationObserver = AiTextProviderSessionOperationObserver {
            beginObservedOperation()
        }
        private val policy = AiTextProviderSessionPolicy(
            plan = request.plan,
            request = request.request,
            requestDescriptors = request.requestDescriptors,
            serializedControlExecutor = lane.asExecutor(::executorRejected),
            terminalFallbackExecutor = terminalFallbackExecutor,
            options = AiTextProviderSessionOptions(
                enabled = request.options.enabled,
                maximumBufferedCallbacks = request.options.maximumBufferedCallbacks,
            ),
            onEvent = ::acceptPolicyEvent,
        )

        private var dispatch: AiTextProviderSessionOpenDecision.Dispatch? = null
        private var rpc: AiTextProviderSessionRpc? = null
        private var ownerAcquired = false
        private var stopRequested = false
        private var openState = OpenState.NOT_ENTERED
        private var binderRpcInFlight = false
        private var operationsInFlight = 0
        private var timeoutClaimed = false
        private var operationCrossedDeadline = false
        private var policyTerminalClaimInProgress = false
        private var deferredPolicyTerminalEvent: AiTextProviderSessionEvent? = null
        private var coordinatorResourcesClosed = false
        private var settlementInProgress = false
        private var outcomeDelivered = false
        private var outerTerminalOverride: AiTextProviderSessionRunEvent? = null
        private var policyTerminalEvent: AiTextProviderSessionEvent? = null

        fun start(): AiTextProviderSessionRunHandle {
            val handle = object : AiTextProviderSessionRunHandle {
                override fun cancel(): Boolean = cancelRun()

                override fun close() {
                    cancel()
                }
            }
            val decision = policy.beginOpen()
            if (decision !is AiTextProviderSessionOpenDecision.Dispatch) return handle
            dispatch = decision
            if (!request.snapshot.matchesSessionPinnedPackage(request.plan.pinnedProvider)) {
                claimDirectTerminal(
                    AiTextProviderSessionRunEvent.Rejected(
                        AiTextProviderSessionRunFailure.PINNED_IDENTITY_MISMATCH,
                    ),
                )
                resolveWithoutOpen()
                return handle
            }
            when (owner.tryAcquire(request.snapshot.component)) {
                AiTextProviderSessionAdmission.BUSY -> {
                    claimDirectTerminal(AiTextProviderSessionRunEvent.Busy)
                    resolveWithoutOpen()
                }
                AiTextProviderSessionAdmission.FUSED -> {
                    claimDirectTerminal(AiTextProviderSessionRunEvent.Fused)
                    resolveWithoutOpen()
                }
                AiTextProviderSessionAdmission.ACQUIRED -> {
                    synchronized(lifecycleLock) { ownerAcquired = true }
                    armDeadlineAndBegin()
                }
            }
            return handle
        }

        private fun armDeadlineAndBegin() {
            val remaining = remainingMillis()
            if (remaining <= 0L) {
                timeout()
                return
            }
            val deadline = try {
                deadlineScheduler.schedule(remaining, ::timeout)
            } catch (_: Exception) {
                terminateTransport(AiTextProviderSessionRunFailure.EXECUTOR_REJECTED)
                return
            }
            deadlineSlot.install(deadline)
            if (deadlineExpired()) {
                timeout()
            } else {
                enqueue(::begin)
            }
        }

        private fun begin() {
            if (stopForDeadlineOrTerminal()) return
            if (!reinspectMatches()) {
                terminateTransport(AiTextProviderSessionRunFailure.IDENTITY_DRIFT)
                return
            }
            if (stopForDeadlineOrTerminal()) return
            val binding = try {
                transport.bindExact(
                    request.snapshot,
                    object : AiTextProviderSessionBindingCallback {
                        override fun onConnected(connection: AiTextProviderSessionConnection) {
                            if (!connectionClaimed.compareAndSet(false, true)) {
                                terminateTransport(
                                    AiTextProviderSessionRunFailure.DUPLICATE_CONNECTION,
                                )
                                return
                            }
                            enqueue { connected(connection) }
                        }

                        override fun onBindingFailure(failure: AiTextProviderSessionRunFailure) {
                            terminateTransport(failure)
                        }

                        override fun onBinderDeath() {
                            binderDied()
                        }
                    },
                )
            } catch (_: SecurityException) {
                terminateTransport(AiTextProviderSessionRunFailure.BIND_SECURITY)
                return
            } catch (_: Exception) {
                terminateTransport(AiTextProviderSessionRunFailure.BIND_REJECTED)
                return
            }
            bindingSlot.install(binding)
        }

        private fun connected(connection: AiTextProviderSessionConnection) {
            if (stopForDeadlineOrTerminal()) return
            submitBlocking {
                val result = readAndRevalidateMetadata(connection)
                enqueue { acceptMetadata(result) }
            }
        }

        private fun readAndRevalidateMetadata(
            connection: AiTextProviderSessionConnection,
        ): MetadataReadResult {
            if (stopForDeadlineOrTerminal()) return MetadataReadResult.Abandoned
            if (!reinspectMatches()) {
                return MetadataReadResult.Failed(AiTextProviderSessionRunFailure.IDENTITY_DRIFT)
            }
            if (stopForDeadlineOrTerminal()) return MetadataReadResult.Abandoned
            val openedRpc = if (!beginBinderRpc()) {
                return MetadataReadResult.Abandoned
            } else {
                try {
                    transport.openRpc(connection)
                } catch (_: AiTextProviderSessionProviderDescriptorMismatchException) {
                    return MetadataReadResult.Failed(
                        AiTextProviderSessionRunFailure.PROVIDER_DESCRIPTOR_MISMATCH,
                    )
                } catch (_: AiTextProviderRemoteSessionDeadException) {
                    return MetadataReadResult.BinderDied
                } catch (_: SecurityException) {
                    return MetadataReadResult.Failed(AiTextProviderSessionRunFailure.REMOTE_SECURITY)
                } catch (_: Exception) {
                    return MetadataReadResult.Failed(AiTextProviderSessionRunFailure.REMOTE_FAILURE)
                } finally {
                    endBinderRpc()
                }
            }
            if (stopForDeadlineOrTerminal()) return MetadataReadResult.Abandoned
            val providerInfo = when (val value = readProviderInfo(openedRpc)) {
                null -> return MetadataReadResult.Abandoned
                MetadataValue.BinderDied -> return MetadataReadResult.BinderDied
                is MetadataValue.Failed -> return MetadataReadResult.Failed(value.failure)
                is MetadataValue.Value -> value.value
            }
            if (stopForDeadlineOrTerminal()) return MetadataReadResult.Abandoned
            val capabilities = when (val value = readCapabilities(openedRpc)) {
                null -> return MetadataReadResult.Abandoned
                MetadataValue.BinderDied -> return MetadataReadResult.BinderDied
                is MetadataValue.Failed -> return MetadataReadResult.Failed(value.failure)
                is MetadataValue.Value -> value.value
            }
            if (stopForDeadlineOrTerminal()) return MetadataReadResult.Abandoned
            val current = reinspectionSnapshot()
                ?: return MetadataReadResult.Failed(AiTextProviderSessionRunFailure.IDENTITY_DRIFT)
            if (!request.snapshot.samePackageIdentityAs(current)) {
                return MetadataReadResult.Failed(AiTextProviderSessionRunFailure.IDENTITY_DRIFT)
            }
            val candidate = AiTextProviderCandidate(
                component = current.component,
                declaredAction = current.declaredAction,
                exported = current.exported,
                applicationEnabled = current.applicationEnabled,
                serviceEnabled = current.serviceEnabled,
                requiredPermission = current.requiredPermission,
                packageUid = current.packageUid,
                versionCode = current.versionCode,
                lastUpdateTime = current.lastUpdateTime,
                signerSha256 = current.signerSha256,
                trustLevel = if (current.officialSignerMatch) {
                    AiTextProviderTrustLevel.OFFICIAL
                } else {
                    AiTextProviderTrustLevel.UNTRUSTED
                },
                sameSignerAsHost = current.sameSignerAsHost,
                providerInfo = providerInfo,
                capabilities = capabilities,
            )
            if (!AiTextProviderSelectionPolicy.revalidate(
                    pinned = request.plan.pinnedProvider,
                    current = candidate,
                    environment = request.environment,
                    hostCapabilities = request.hostCapabilities,
                    requirement = request.requirement,
                )
            ) {
                return MetadataReadResult.Failed(
                    AiTextProviderSessionRunFailure.PINNED_METADATA_MISMATCH,
                )
            }
            return MetadataReadResult.Complete(openedRpc)
        }

        private fun readProviderInfo(
            rpc: AiTextProviderSessionRpc,
        ): MetadataValue<AiProviderInfo>? {
            if (!beginBinderRpc()) return null
            val envelope = try {
                rpc.getProviderInfoEnvelope()
            } catch (_: AiTextProviderRemoteSessionDeadException) {
                return MetadataValue.BinderDied
            } catch (_: SecurityException) {
                return MetadataValue.Failed(AiTextProviderSessionRunFailure.REMOTE_SECURITY)
            } catch (_: Exception) {
                return MetadataValue.Failed(AiTextProviderSessionRunFailure.REMOTE_FAILURE)
            } finally {
                endBinderRpc()
            } ?: return MetadataValue.Failed(
                AiTextProviderSessionRunFailure.PROVIDER_INFO_MISSING,
            )
            if (envelope.isEmpty()) {
                return MetadataValue.Failed(AiTextProviderSessionRunFailure.PROVIDER_INFO_MISSING)
            }
            if (envelope.size > AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES) {
                return MetadataValue.Failed(AiTextProviderSessionRunFailure.PROVIDER_INFO_TOO_LARGE)
            }
            val decoded = runCatching {
                AiCommonCodec.decodeProviderInfo(envelope.copyOf())
            }.getOrNull() ?: return MetadataValue.Failed(
                AiTextProviderSessionRunFailure.PROVIDER_INFO_INVALID,
            )
            return MetadataValue.Value(decoded)
        }

        private fun readCapabilities(
            rpc: AiTextProviderSessionRpc,
        ): MetadataValue<AiTextCapabilities>? {
            if (!beginBinderRpc()) return null
            val envelope = try {
                rpc.getCapabilitiesEnvelope()
            } catch (_: AiTextProviderRemoteSessionDeadException) {
                return MetadataValue.BinderDied
            } catch (_: SecurityException) {
                return MetadataValue.Failed(AiTextProviderSessionRunFailure.REMOTE_SECURITY)
            } catch (_: Exception) {
                return MetadataValue.Failed(AiTextProviderSessionRunFailure.REMOTE_FAILURE)
            } finally {
                endBinderRpc()
            } ?: return MetadataValue.Failed(
                AiTextProviderSessionRunFailure.CAPABILITIES_MISSING,
            )
            if (envelope.isEmpty()) {
                return MetadataValue.Failed(AiTextProviderSessionRunFailure.CAPABILITIES_MISSING)
            }
            if (envelope.size > AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES) {
                return MetadataValue.Failed(AiTextProviderSessionRunFailure.CAPABILITIES_TOO_LARGE)
            }
            val decoded = runCatching {
                AiTextCodec.decodeCapabilities(envelope.copyOf())
            }.getOrNull() ?: return MetadataValue.Failed(
                AiTextProviderSessionRunFailure.CAPABILITIES_INVALID,
            )
            return MetadataValue.Value(decoded)
        }

        private fun acceptMetadata(result: MetadataReadResult) {
            if (stopForDeadlineOrTerminal()) return
            when (result) {
                MetadataReadResult.Abandoned -> Unit
                MetadataReadResult.BinderDied -> binderDied()
                is MetadataReadResult.Failed -> terminateTransport(result.failure)
                is MetadataReadResult.Complete -> prepareAndLaunchOpen(result.rpc)
            }
        }

        private fun prepareAndLaunchOpen(openedRpc: AiTextProviderSessionRpc) {
            if (stopForDeadlineOrTerminal()) return
            rpc = openedRpc
            val prepared = try {
                openedRpc.prepareOpen(
                    callback = checkNotNull(dispatch).callback,
                    onSessionBinderDeath = ::binderDied,
                    operationObserver = operationObserver,
                )
            } catch (_: SecurityException) {
                terminateTransport(AiTextProviderSessionRunFailure.REMOTE_SECURITY)
                return
            } catch (_: Exception) {
                terminateTransport(AiTextProviderSessionRunFailure.REMOTE_FAILURE)
                return
            }
            preparedOpenSlot.install(prepared)
            if (!isStopping()) submitBlocking { performOpen(prepared) }
        }

        private fun performOpen(prepared: AiTextProviderSessionPreparedOpen) {
            if (stopForDeadlineOrTerminal()) return
            if (!reinspectMatches()) {
                terminateTransport(AiTextProviderSessionRunFailure.IDENTITY_DRIFT)
                return
            }
            if (stopForDeadlineOrTerminal()) return
            if (!beginOpenTransaction()) {
                resolveWithoutOpen()
                return
            }
            val decision = checkNotNull(dispatch)
            var remote: AiTextProviderSessionRemote? = null
            var failure: AiTextProviderSessionRunFailure? = null
            var binderDead = false
            try {
                remote = prepared.open(
                    requestEnvelope = decision.copyRequestEnvelope(),
                    requestDescriptors = decision.requestDescriptors,
                )
            } catch (_: AiTextProviderRemoteSessionDescriptorMismatchException) {
                failure = AiTextProviderSessionRunFailure.SESSION_DESCRIPTOR_MISMATCH
            } catch (_: AiTextProviderRemoteSessionDeadException) {
                binderDead = true
            } catch (_: SecurityException) {
                failure = AiTextProviderSessionRunFailure.REMOTE_SECURITY
            } catch (_: Exception) {
                failure = AiTextProviderSessionRunFailure.REMOTE_FAILURE
            } catch (_: Throwable) {
                // The blocking-owner boundary catches Throwable. Resolve the policy open here as
                // well, otherwise an Error after synchronous dispatch would strand its request
                // descriptors and the process gate in the pre-commit state forever.
                failure = AiTextProviderSessionRunFailure.INTERNAL_FAILURE
            } finally {
                endOpenTransaction()
            }
            when {
                binderDead -> {
                    binderDied()
                    policy.commitOpenFailed()
                }
                failure != null -> {
                    if (failure == AiTextProviderSessionRunFailure.SESSION_DESCRIPTOR_MISMATCH) {
                        owner.fuse(request.snapshot.component)
                    }
                    terminateTransport(requireNotNull(failure), resolveOpen = false)
                    policy.commitOpenFailed()
                }
                remote != null -> policy.commitOpenSucceeded(remote)
                else -> {
                    terminateTransport(
                        AiTextProviderSessionRunFailure.REMOTE_FAILURE,
                        resolveOpen = false,
                    )
                    policy.commitOpenFailed()
                }
            }
        }

        private fun cancelRun(): Boolean {
            val accepted = policy.cancel()
            if (!accepted) return false
            synchronized(lifecycleLock) { stopRequested = true }
            closeTransportEarly()
            resolveWithoutOpen()
            return true
        }

        private fun binderDied() {
            val accepted = policy.binderDied()
            if (!accepted) return
            synchronized(lifecycleLock) { stopRequested = true }
            closeTransportEarly()
            resolveWithoutOpen()
        }

        private fun timeout() {
            if (!deadlineExpired()) return
            val reserved = synchronized(lifecycleLock) {
                if (policyTerminalClaimInProgress || timeoutClaimed ||
                    policyTerminalEvent != null || outcomeDelivered
                ) {
                    false
                } else {
                    policyTerminalClaimInProgress = true
                    stopRequested = true
                    true
                }
            }
            if (!reserved) return
            val accepted = try {
                policy.timeout()
            } catch (error: Throwable) {
                abandonPolicyTerminalClaim()
                throw error
            }
            if (!accepted) {
                deadlineSlot.close()
                finishPolicyTerminalClaim()?.let(::resumeDeferredPolicyTerminal)
                return
            }
            val fuse = synchronized(lifecycleLock) {
                timeoutClaimed = true
                ownerAcquired && (operationsInFlight > 0 || operationCrossedDeadline)
            }
            if (fuse) owner.fuse(request.snapshot.component)
            closeTransportEarly()
            resolveWithoutOpen()
            finishPolicyTerminalClaim()?.let(::resumeDeferredPolicyTerminal)
        }

        private fun terminateTransport(
            failure: AiTextProviderSessionRunFailure,
            resolveOpen: Boolean = true,
        ) {
            val reserved = synchronized(lifecycleLock) {
                if (policyTerminalClaimInProgress || policyTerminalEvent != null ||
                    outerTerminalOverride != null || outcomeDelivered
                ) {
                    false
                } else {
                    policyTerminalClaimInProgress = true
                    stopRequested = true
                    true
                }
            }
            if (!reserved) return
            val accepted = try {
                policy.cancel()
            } catch (error: Throwable) {
                abandonPolicyTerminalClaim()
                throw error
            }
            if (accepted) synchronized(lifecycleLock) {
                outerTerminalOverride = AiTextProviderSessionRunEvent.Rejected(failure)
            }
            if (accepted) {
                closeTransportEarly()
                if (resolveOpen) resolveWithoutOpen()
            }
            finishPolicyTerminalClaim()?.let(::resumeDeferredPolicyTerminal)
        }

        private fun claimDirectTerminal(event: AiTextProviderSessionRunEvent) {
            synchronized(lifecycleLock) {
                stopRequested = true
                outerTerminalOverride = event
            }
            policy.cancel()
            closeTransportEarly()
        }

        private fun resolveWithoutOpen() {
            val resolve = synchronized(lifecycleLock) {
                if (openState != OpenState.NOT_ENTERED) false else {
                    openState = OpenState.RESOLVED
                    true
                }
            }
            if (resolve) policy.commitOpenFailed()
        }

        private fun beginOpenTransaction(): Boolean {
            var expired = false
            synchronized(lifecycleLock) {
                if (stopRequested || policyTerminalEvent != null || openState != OpenState.NOT_ENTERED) {
                    return false
                }
                if (deadlineExpired()) {
                    expired = true
                } else {
                    check(!binderRpcInFlight) { "AI text provider Binder RPC calls must be serial" }
                    binderRpcInFlight = true
                    openState = OpenState.IN_FLIGHT
                    return true
                }
            }
            if (expired) timeout()
            return false
        }

        private fun endOpenTransaction() {
            if (deadlineExpired()) timeout()
            synchronized(lifecycleLock) {
                check(openState == OpenState.IN_FLIGHT) { "AI text session open is not in flight" }
                binderRpcInFlight = false
                openState = OpenState.RESOLVED
            }
        }

        private fun beginBinderRpc(): Boolean {
            var expired = false
            synchronized(lifecycleLock) {
                if (stopRequested || policyTerminalEvent != null) return false
                if (deadlineExpired()) {
                    expired = true
                } else {
                    check(!binderRpcInFlight) { "AI text provider Binder RPC calls must be serial" }
                    binderRpcInFlight = true
                    return true
                }
            }
            if (expired) timeout()
            return false
        }

        private fun endBinderRpc() {
            if (deadlineExpired()) timeout()
            synchronized(lifecycleLock) { binderRpcInFlight = false }
        }

        private fun acceptPolicyEvent(event: AiTextProviderSessionEvent) {
            if (!event.isSessionTerminal()) {
                val deliver = synchronized(lifecycleLock) {
                    !outcomeDelivered && policyTerminalEvent == null &&
                        outerTerminalOverride == null && !policyTerminalClaimInProgress
                }
                if (deliver) deliver(AiTextProviderSessionRunEvent.Transcript(event))
                return
            }
            var deferred = false
            val accepted = synchronized(lifecycleLock) {
                if (policyTerminalEvent != null) false else {
                    if (policyTerminalClaimInProgress) {
                        if (deferredPolicyTerminalEvent == null) {
                            deferredPolicyTerminalEvent = event
                        }
                        deferred = true
                        false
                    } else {
                        policyTerminalEvent = event
                        stopRequested = true
                        true
                    }
                }
            }
            if (deferred || !accepted) return
            closeCoordinatorResources()
            settleIfReady()
        }

        /** Releases a timeout/transport claim only after its outer result is fully established. */
        private fun finishPolicyTerminalClaim(): AiTextProviderSessionEvent? =
            synchronized(lifecycleLock) {
                check(policyTerminalClaimInProgress) {
                    "AI text session policy terminal claim was not in progress"
                }
                policyTerminalClaimInProgress = false
                deferredPolicyTerminalEvent.also { deferredPolicyTerminalEvent = null }
            }

        private fun abandonPolicyTerminalClaim() {
            synchronized(lifecycleLock) {
                policyTerminalClaimInProgress = false
                deferredPolicyTerminalEvent = null
            }
        }

        private fun resumeDeferredPolicyTerminal(event: AiTextProviderSessionEvent) {
            // A claim may be completed by a Binder, scheduler, or blocking-owner thread. Preserve
            // the policy's mandatory independent terminal-lane boundary when resuming its event.
            terminalFallbackExecutor.execute { acceptPolicyEvent(event) }
        }

        private fun closeTransportEarly() {
            deadlineSlot.close()
            preparedOpenSlot.close()
            bindingSlot.close()
        }

        private fun closeCoordinatorResources() {
            closeTransportEarly()
            synchronized(lifecycleLock) { coordinatorResourcesClosed = true }
        }

        private fun settleIfReady() {
            val settlement = synchronized(lifecycleLock) {
                if (policyTerminalEvent == null || !coordinatorResourcesClosed ||
                    operationsInFlight != 0 || settlementInProgress || outcomeDelivered
                ) {
                    null
                } else {
                    settlementInProgress = true
                    Settlement(
                        releaseOwner = ownerAcquired,
                        event = outerTerminalOverride ?: mapPolicyTerminal(
                            checkNotNull(policyTerminalEvent),
                        ),
                    )
                }
            } ?: return
            if (settlement.releaseOwner) owner.release()
            synchronized(lifecycleLock) {
                ownerAcquired = false
                outcomeDelivered = true
                settlementInProgress = false
            }
            deliver(settlement.event)
        }

        private fun mapPolicyTerminal(
            event: AiTextProviderSessionEvent,
        ): AiTextProviderSessionRunEvent = if (event is AiTextProviderSessionEvent.Rejected) {
            AiTextProviderSessionRunEvent.Rejected(
                failure = AiTextProviderSessionRunFailure.TRANSCRIPT_REJECTED,
                transcriptFailure = event.failure,
            )
        } else {
            AiTextProviderSessionRunEvent.Transcript(event)
        }

        private fun submitBlocking(action: () -> Unit) {
            if (isStopping()) return
            try {
                blockingExecutor.execute {
                    val lease = beginCoordinatorOperation() ?: return@execute
                    try {
                        if (!stopForDeadlineOrTerminal()) action()
                    } catch (_: Throwable) {
                        terminateTransport(AiTextProviderSessionRunFailure.INTERNAL_FAILURE)
                    } finally {
                        lease.close()
                    }
                }
            } catch (_: Exception) {
                terminateTransport(AiTextProviderSessionRunFailure.EXECUTOR_REJECTED)
            }
        }

        private fun enqueue(action: () -> Unit) {
            if (isStopping()) return
            lane.execute(
                command = Runnable {
                    val lease = beginCoordinatorOperation() ?: return@Runnable
                    try {
                        if (!stopForDeadlineOrTerminal()) action()
                    } catch (_: Throwable) {
                        terminateTransport(AiTextProviderSessionRunFailure.INTERNAL_FAILURE)
                    } finally {
                        lease.close()
                    }
                },
                onRejected = ::executorRejected,
            )
        }

        private fun executorRejected() {
            try {
                terminalFallbackExecutor.execute {
                    terminateTransport(AiTextProviderSessionRunFailure.EXECUTOR_REJECTED)
                }
            } catch (_: RuntimeException) {
                terminateTransport(AiTextProviderSessionRunFailure.EXECUTOR_REJECTED)
            }
        }

        private fun beginCoordinatorOperation(): Closeable? = synchronized(lifecycleLock) {
            if (stopRequested || policyTerminalEvent != null || outcomeDelivered) return@synchronized null
            operationsInFlight += 1
            OperationLease(::operationDone)
        }

        private fun beginObservedOperation(): Closeable {
            var fuseComponent = false
            var claimTimeout = false
            synchronized(lifecycleLock) {
                if (coordinatorResourcesClosed || settlementInProgress || outcomeDelivered) {
                    return Closeable {}
                }
                operationsInFlight += 1
                val expired = timeoutClaimed || deadlineExpired()
                if (expired) {
                    operationCrossedDeadline = true
                    fuseComponent = timeoutClaimed && ownerAcquired
                    claimTimeout = !timeoutClaimed
                }
            }
            if (fuseComponent) owner.fuse(request.snapshot.component)
            if (claimTimeout) timeout()
            return OperationLease(::operationDone)
        }

        private fun operationDone() {
            // Claim timeout while this lease is still counted. Crossing the absolute deadline
            // during a PFD/control operation is precisely the condition that must fuse the
            // component and keep the process gate BUSY until the operation unwinds.
            val crossedDeadline = deadlineExpired()
            if (crossedDeadline) synchronized(lifecycleLock) {
                operationCrossedDeadline = true
            }
            if (crossedDeadline) timeout()
            synchronized(lifecycleLock) {
                check(operationsInFlight > 0) { "AI text session operation count underflow" }
                operationsInFlight -= 1
            }
            settleIfReady()
        }

        private fun reinspectMatches(): Boolean = reinspectionSnapshot()?.let {
            request.snapshot.samePackageIdentityAs(it)
        } == true

        private fun reinspectionSnapshot(): AiTextProviderPackageSnapshot? {
            val result = try {
                AiTextProviderDiscoveryPolicy.inspectExact(
                    source = object : AiTextProviderDiscoverySource {
                        override fun discoverExactAction(action: String) =
                            AiTextProviderDiscoverySourceResult.WrongAction

                        override fun inspectExact(component: AiTextProviderComponent) =
                            transport.inspectExact(component)
                    },
                    component = request.snapshot.component,
                    options = AiTextProviderDiscoveryOptions(enabled = true),
                )
            } catch (_: Exception) {
                return null
            }
            val completed = result as? AiTextProviderDiscoveryResult.Completed ?: return null
            val discovered = completed.evaluations.singleOrNull()
                as? AiTextProviderDiscoveryEvaluation.Discovered ?: return null
            return discovered.snapshot
        }

        private fun stopForDeadlineOrTerminal(): Boolean {
            if (isStopping()) return true
            if (!deadlineExpired()) return false
            timeout()
            return true
        }

        private fun isStopping(): Boolean = synchronized(lifecycleLock) {
            stopRequested || policyTerminalEvent != null || outcomeDelivered
        }

        private fun deadlineExpired(): Boolean = remainingMillis() <= 0L

        private fun remainingMillis(): Long = deadlineAtMillis - clock.elapsedRealtimeMillis()

        private fun deliver(event: AiTextProviderSessionRunEvent) {
            runCatching { onEvent(event) }
        }

        private fun saturatingAdd(left: Long, right: Long): Long =
            if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
    }

    private sealed class MetadataReadResult {
        object Abandoned : MetadataReadResult()
        object BinderDied : MetadataReadResult()
        data class Failed(val failure: AiTextProviderSessionRunFailure) : MetadataReadResult()
        data class Complete(val rpc: AiTextProviderSessionRpc) : MetadataReadResult()
    }

    private sealed class MetadataValue<out T> {
        object BinderDied : MetadataValue<Nothing>()
        data class Value<T>(val value: T) : MetadataValue<T>()
        data class Failed(val failure: AiTextProviderSessionRunFailure) : MetadataValue<Nothing>()
    }

    private data class Settlement(
        val releaseOwner: Boolean,
        val event: AiTextProviderSessionRunEvent,
    )

    private enum class OpenState {
        NOT_ENTERED,
        IN_FLIGHT,
        RESOLVED,
    }
}

private fun AiTextProviderPackageSnapshot.matchesSessionPinnedPackage(
    pinned: AiTextProviderPinnedIdentity,
): Boolean =
    component == pinned.component &&
        packageUid == pinned.packageUid &&
        versionCode == pinned.versionCode &&
        lastUpdateTime == pinned.lastUpdateTime &&
        signerSha256 == pinned.signerSha256

private fun AiTextProviderSessionEvent.isSessionTerminal(): Boolean = when (this) {
    is AiTextProviderSessionEvent.Started,
    is AiTextProviderSessionEvent.Chunk,
    is AiTextProviderSessionEvent.Usage,
    -> false
    AiTextProviderSessionEvent.Disabled,
    is AiTextProviderSessionEvent.Completed,
    is AiTextProviderSessionEvent.ProviderFailed,
    AiTextProviderSessionEvent.Cancelled,
    AiTextProviderSessionEvent.TimedOut,
    AiTextProviderSessionEvent.BinderDied,
    is AiTextProviderSessionEvent.Rejected,
    -> true
}

private class OperationLease(
    private val onClose: () -> Unit,
) : Closeable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) onClose()
    }
}

private class SessionCoordinatorCloseSlot<T : Closeable> : Closeable {
    private val lock = Any()
    private var value: T? = null
    private var closed = false

    fun install(candidate: T) {
        val closeNow = synchronized(lock) {
            check(value == null) { "AI text session resource was installed more than once" }
            if (closed) true else {
                value = candidate
                false
            }
        }
        if (closeNow) runCatching { candidate.close() }
    }

    override fun close() {
        val toClose = synchronized(lock) {
            if (closed) return
            closed = true
            value.also { value = null }
        }
        if (toClose != null) runCatching { toClose.close() }
    }
}

private class SessionCoordinatorSerialExecutor(
    private val delegate: Executor,
) {
    private data class Entry(
        val command: Runnable,
        val onRejected: () -> Unit,
    )

    private val lock = Any()
    private val queue = ArrayDeque<Entry>()
    private var draining = false

    fun asExecutor(onRejected: () -> Unit): Executor = Executor { command ->
        execute(command, onRejected)
    }

    fun execute(command: Runnable, onRejected: () -> Unit) {
        val launch = synchronized(lock) {
            queue.addLast(Entry(command, onRejected))
            if (draining) false else {
                draining = true
                true
            }
        }
        if (!launch) return
        try {
            delegate.execute(::drain)
        } catch (_: RuntimeException) {
            val rejected = synchronized(lock) {
                queue.toList().also {
                    queue.clear()
                    draining = false
                }
            }
            rejected.forEach { entry -> runCatching { entry.onRejected() } }
        }
    }

    private fun drain() {
        while (true) {
            val next = synchronized(lock) {
                if (queue.isEmpty()) {
                    draining = false
                    return
                }
                queue.removeFirst()
            }
            runCatching { next.command.run() }
        }
    }
}
