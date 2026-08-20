package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiProviderInfo
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilities
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCodec
import org.autojs.plugin.ondeviceai.api.OnDeviceAiLimits
import org.autojs.plugin.ondeviceai.api.OnDeviceAiRequest
import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Opaque transport-owned connection until its provider Binder descriptor is verified. */
internal interface OnDeviceAiProviderSessionConnection

internal interface OnDeviceAiProviderSessionBinding : Closeable

internal interface OnDeviceAiProviderSessionBindingCallback {
    fun onConnected(connection: OnDeviceAiProviderSessionConnection)

    fun onBindingFailure(failure: OnDeviceAiProviderSessionRunFailure)

    fun onBinderDeath()
}

/**
 * Counts blocking Android-side work that must settle before the process gate is released.
 *
 * Android request/result descriptor readers and remote-session controls must acquire a lease
 * around each potentially blocking operation. A returned lease is idempotent and must be closed
 * in a `finally` block.
 */
internal fun interface OnDeviceAiProviderSessionOperationObserver {
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
internal interface OnDeviceAiProviderSessionPreparedOpen : Closeable {
    fun open(
        requestEnvelope: ByteArray,
        requestDescriptors: OnDeviceAiProviderSessionRequestDescriptorLease,
    ): OnDeviceAiProviderSessionRemote
}

/** Descriptor-verified view of the same provider Binder used for metadata and session opening. */
internal interface OnDeviceAiProviderSessionRpc {
    fun getProviderInfoEnvelope(): ByteArray?

    fun getCapabilitiesEnvelope(): ByteArray?

    fun prepareOpen(
        callback: OnDeviceAiProviderSessionCallbackIngress,
        onSessionBinderDeath: () -> Unit,
        operationObserver: OnDeviceAiProviderSessionOperationObserver,
    ): OnDeviceAiProviderSessionPreparedOpen
}

/** Exact-component boundary for one explicitly invoked, default-off Android session run. */
internal interface OnDeviceAiProviderSessionTransport {
    fun inspectExact(component: OnDeviceAiProviderComponent): OnDeviceAiProviderDiscoverySourceResult

    fun bindExact(
        snapshot: OnDeviceAiProviderPackageSnapshot,
        callback: OnDeviceAiProviderSessionBindingCallback,
    ): OnDeviceAiProviderSessionBinding

    /** Must verify `IOnDeviceAiProvider.DESCRIPTOR` before returning the RPC view. */
    fun openRpc(connection: OnDeviceAiProviderSessionConnection): OnDeviceAiProviderSessionRpc
}

/** Marker exceptions avoid retaining or logging hostile remote descriptor strings. */
internal class OnDeviceAiProviderSessionProviderDescriptorMismatchException : SecurityException()

internal class OnDeviceAiProviderRemoteSessionDescriptorMismatchException : SecurityException()

internal class OnDeviceAiProviderRemoteSessionDeadException : IllegalStateException()

internal fun interface OnDeviceAiProviderSessionDeadlineScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): Closeable
}

internal fun interface OnDeviceAiProviderSessionClock {
    fun elapsedRealtimeMillis(): Long
}

internal data class OnDeviceAiProviderSessionRunOptions(
    val enabled: Boolean = false,
    val maximumBufferedCallbacks: Int = 16,
) {
    init {
        require(
            maximumBufferedCallbacks in
                (OnDeviceAiLimits.INITIAL_CHUNK_CREDITS + 2)..MAXIMUM_BUFFERED_CALLBACKS,
        ) { "Invalid on-device AI session callback capacity" }
    }

    private companion object {
        const val MAXIMUM_BUFFERED_CALLBACKS = 64
    }
}

internal data class OnDeviceAiProviderSessionRunRequest(
    val snapshot: OnDeviceAiProviderPackageSnapshot,
    val plan: OnDeviceAiHostSessionPlan,
    val request: OnDeviceAiRequest,
    val requestDescriptors: OnDeviceAiProviderSessionRequestDescriptorLease,
    val environment: OnDeviceAiProviderEnvironment,
    val hostCapabilities: OnDeviceAiCapabilities,
    val requirement: OnDeviceAiCapabilityRequirement = OnDeviceAiCapabilityRequirement(),
    val options: OnDeviceAiProviderSessionRunOptions = OnDeviceAiProviderSessionRunOptions(),
)

internal enum class OnDeviceAiProviderSessionRunFailure {
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

internal sealed class OnDeviceAiProviderSessionRunEvent {
    object Busy : OnDeviceAiProviderSessionRunEvent()
    object Fused : OnDeviceAiProviderSessionRunEvent()

    data class Transcript(
        val event: OnDeviceAiProviderSessionEvent,
    ) : OnDeviceAiProviderSessionRunEvent()

    data class Rejected(
        val failure: OnDeviceAiProviderSessionRunFailure,
        val transcriptFailure: OnDeviceAiProviderSessionFailure? = null,
    ) : OnDeviceAiProviderSessionRunEvent()
}

internal interface OnDeviceAiProviderSessionRunHandle : Closeable {
    fun cancel(): Boolean
}

/** Process-lifetime, single-active admission and per-component fuse state. */
internal class OnDeviceAiProviderSessionProcessOwner {
    private val lock = Any()
    private var active = false
    private val fusedComponents = HashSet<OnDeviceAiProviderComponent>()

    internal fun tryAcquire(component: OnDeviceAiProviderComponent): OnDeviceAiProviderSessionAdmission =
        synchronized(lock) {
            when {
                component in fusedComponents -> OnDeviceAiProviderSessionAdmission.FUSED
                active -> OnDeviceAiProviderSessionAdmission.BUSY
                else -> {
                    active = true
                    OnDeviceAiProviderSessionAdmission.ACQUIRED
                }
            }
        }

    internal fun fuse(component: OnDeviceAiProviderComponent) {
        synchronized(lock) { fusedComponents += component }
    }

    internal fun release() {
        synchronized(lock) {
            check(active) { "on-device AI session process owner was not acquired" }
            active = false
        }
    }
}

internal enum class OnDeviceAiProviderSessionAdmission {
    ACQUIRED,
    BUSY,
    FUSED,
}

/**
 * Default-off owner for an exact-component Android session run.
 *
 * The implementation below deliberately has no production call site. It binds and dispatches only
 * when [OnDeviceAiProviderSessionRunOptions.enabled] is explicitly true.
 */
internal class OnDeviceAiProviderSessionCoordinator(
    private val transport: OnDeviceAiProviderSessionTransport,
    workerExecutor: Executor,
    private val blockingExecutor: Executor,
    private val terminalFallbackExecutor: Executor,
    private val deadlineScheduler: OnDeviceAiProviderSessionDeadlineScheduler,
    private val clock: OnDeviceAiProviderSessionClock,
    private val owner: OnDeviceAiProviderSessionProcessOwner,
) {
    private val lane = SessionCoordinatorSerialExecutor(workerExecutor)

    fun start(
        request: OnDeviceAiProviderSessionRunRequest,
        onEvent: (OnDeviceAiProviderSessionRunEvent) -> Unit,
    ): OnDeviceAiProviderSessionRunHandle = Attempt(request, onEvent).start()

    private inner class Attempt(
        private val request: OnDeviceAiProviderSessionRunRequest,
        private val onEvent: (OnDeviceAiProviderSessionRunEvent) -> Unit,
    ) {
        private val lifecycleLock = Any()
        private val bindingSlot = SessionCoordinatorCloseSlot<OnDeviceAiProviderSessionBinding>()
        private val preparedOpenSlot = SessionCoordinatorCloseSlot<OnDeviceAiProviderSessionPreparedOpen>()
        private val deadlineSlot = SessionCoordinatorCloseSlot<Closeable>()
        private val connectionClaimed = AtomicBoolean(false)
        private val deadlineAtMillis = saturatingAdd(
            clock.elapsedRealtimeMillis(),
            request.request.options.timeoutMillis,
        )
        private val operationObserver = OnDeviceAiProviderSessionOperationObserver {
            beginObservedOperation()
        }
        private val policy = OnDeviceAiProviderSessionPolicy(
            plan = request.plan,
            request = request.request,
            requestDescriptors = request.requestDescriptors,
            serializedControlExecutor = lane.asExecutor(::executorRejected),
            terminalFallbackExecutor = terminalFallbackExecutor,
            options = OnDeviceAiProviderSessionOptions(
                enabled = request.options.enabled,
                maximumBufferedCallbacks = request.options.maximumBufferedCallbacks,
            ),
            onEvent = ::acceptPolicyEvent,
        )

        private var dispatch: OnDeviceAiProviderSessionOpenDecision.Dispatch? = null
        private var rpc: OnDeviceAiProviderSessionRpc? = null
        private var ownerAcquired = false
        private var stopRequested = false
        private var openState = OpenState.NOT_ENTERED
        private var binderRpcInFlight = false
        private var operationsInFlight = 0
        private var timeoutClaimed = false
        private var operationCrossedDeadline = false
        private var policyTerminalClaimInProgress = false
        private var deferredPolicyTerminalEvent: OnDeviceAiProviderSessionEvent? = null
        private var coordinatorResourcesClosed = false
        private var settlementInProgress = false
        private var outcomeDelivered = false
        private var outerTerminalOverride: OnDeviceAiProviderSessionRunEvent? = null
        private var policyTerminalEvent: OnDeviceAiProviderSessionEvent? = null

        fun start(): OnDeviceAiProviderSessionRunHandle {
            val handle = object : OnDeviceAiProviderSessionRunHandle {
                override fun cancel(): Boolean = cancelRun()

                override fun close() {
                    cancel()
                }
            }
            val decision = policy.beginOpen()
            if (decision !is OnDeviceAiProviderSessionOpenDecision.Dispatch) return handle
            dispatch = decision
            if (!request.snapshot.matchesSessionPinnedPackage(request.plan.pinnedProvider)) {
                claimDirectTerminal(
                    OnDeviceAiProviderSessionRunEvent.Rejected(
                        OnDeviceAiProviderSessionRunFailure.PINNED_IDENTITY_MISMATCH,
                    ),
                )
                resolveWithoutOpen()
                return handle
            }
            when (owner.tryAcquire(request.snapshot.component)) {
                OnDeviceAiProviderSessionAdmission.BUSY -> {
                    claimDirectTerminal(OnDeviceAiProviderSessionRunEvent.Busy)
                    resolveWithoutOpen()
                }
                OnDeviceAiProviderSessionAdmission.FUSED -> {
                    claimDirectTerminal(OnDeviceAiProviderSessionRunEvent.Fused)
                    resolveWithoutOpen()
                }
                OnDeviceAiProviderSessionAdmission.ACQUIRED -> {
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
                terminateTransport(OnDeviceAiProviderSessionRunFailure.EXECUTOR_REJECTED)
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
                terminateTransport(OnDeviceAiProviderSessionRunFailure.IDENTITY_DRIFT)
                return
            }
            if (stopForDeadlineOrTerminal()) return
            val binding = try {
                transport.bindExact(
                    request.snapshot,
                    object : OnDeviceAiProviderSessionBindingCallback {
                        override fun onConnected(connection: OnDeviceAiProviderSessionConnection) {
                            if (!connectionClaimed.compareAndSet(false, true)) {
                                terminateTransport(
                                    OnDeviceAiProviderSessionRunFailure.DUPLICATE_CONNECTION,
                                )
                                return
                            }
                            enqueue { connected(connection) }
                        }

                        override fun onBindingFailure(failure: OnDeviceAiProviderSessionRunFailure) {
                            terminateTransport(failure)
                        }

                        override fun onBinderDeath() {
                            binderDied()
                        }
                    },
                )
            } catch (_: SecurityException) {
                terminateTransport(OnDeviceAiProviderSessionRunFailure.BIND_SECURITY)
                return
            } catch (_: Exception) {
                terminateTransport(OnDeviceAiProviderSessionRunFailure.BIND_REJECTED)
                return
            }
            bindingSlot.install(binding)
        }

        private fun connected(connection: OnDeviceAiProviderSessionConnection) {
            if (stopForDeadlineOrTerminal()) return
            submitBlocking {
                val result = readAndRevalidateMetadata(connection)
                enqueue { acceptMetadata(result) }
            }
        }

        private fun readAndRevalidateMetadata(
            connection: OnDeviceAiProviderSessionConnection,
        ): MetadataReadResult {
            if (stopForDeadlineOrTerminal()) return MetadataReadResult.Abandoned
            if (!reinspectMatches()) {
                return MetadataReadResult.Failed(OnDeviceAiProviderSessionRunFailure.IDENTITY_DRIFT)
            }
            if (stopForDeadlineOrTerminal()) return MetadataReadResult.Abandoned
            val openedRpc = if (!beginBinderRpc()) {
                return MetadataReadResult.Abandoned
            } else {
                try {
                    transport.openRpc(connection)
                } catch (_: OnDeviceAiProviderSessionProviderDescriptorMismatchException) {
                    return MetadataReadResult.Failed(
                        OnDeviceAiProviderSessionRunFailure.PROVIDER_DESCRIPTOR_MISMATCH,
                    )
                } catch (_: OnDeviceAiProviderRemoteSessionDeadException) {
                    return MetadataReadResult.BinderDied
                } catch (_: SecurityException) {
                    return MetadataReadResult.Failed(OnDeviceAiProviderSessionRunFailure.REMOTE_SECURITY)
                } catch (_: Exception) {
                    return MetadataReadResult.Failed(OnDeviceAiProviderSessionRunFailure.REMOTE_FAILURE)
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
                ?: return MetadataReadResult.Failed(OnDeviceAiProviderSessionRunFailure.IDENTITY_DRIFT)
            if (!request.snapshot.samePackageIdentityAs(current)) {
                return MetadataReadResult.Failed(OnDeviceAiProviderSessionRunFailure.IDENTITY_DRIFT)
            }
            val candidate = OnDeviceAiProviderCandidate(
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
                    OnDeviceAiProviderTrustLevel.OFFICIAL
                } else {
                    OnDeviceAiProviderTrustLevel.UNTRUSTED
                },
                sameSignerAsHost = current.sameSignerAsHost,
                providerInfo = providerInfo,
                capabilities = capabilities,
            )
            if (!OnDeviceAiProviderSelectionPolicy.revalidate(
                    pinned = request.plan.pinnedProvider,
                    current = candidate,
                    environment = request.environment,
                    hostCapabilities = request.hostCapabilities,
                    requirement = request.requirement,
                )
            ) {
                return MetadataReadResult.Failed(
                    OnDeviceAiProviderSessionRunFailure.PINNED_METADATA_MISMATCH,
                )
            }
            return MetadataReadResult.Complete(openedRpc)
        }

        private fun readProviderInfo(
            rpc: OnDeviceAiProviderSessionRpc,
        ): MetadataValue<AiProviderInfo>? {
            if (!beginBinderRpc()) return null
            val envelope = try {
                rpc.getProviderInfoEnvelope()
            } catch (_: OnDeviceAiProviderRemoteSessionDeadException) {
                return MetadataValue.BinderDied
            } catch (_: SecurityException) {
                return MetadataValue.Failed(OnDeviceAiProviderSessionRunFailure.REMOTE_SECURITY)
            } catch (_: Exception) {
                return MetadataValue.Failed(OnDeviceAiProviderSessionRunFailure.REMOTE_FAILURE)
            } finally {
                endBinderRpc()
            } ?: return MetadataValue.Failed(
                OnDeviceAiProviderSessionRunFailure.PROVIDER_INFO_MISSING,
            )
            if (envelope.isEmpty()) {
                return MetadataValue.Failed(OnDeviceAiProviderSessionRunFailure.PROVIDER_INFO_MISSING)
            }
            if (envelope.size > AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES) {
                return MetadataValue.Failed(OnDeviceAiProviderSessionRunFailure.PROVIDER_INFO_TOO_LARGE)
            }
            val decoded = runCatching {
                AiCommonCodec.decodeProviderInfo(envelope.copyOf())
            }.getOrNull() ?: return MetadataValue.Failed(
                OnDeviceAiProviderSessionRunFailure.PROVIDER_INFO_INVALID,
            )
            return MetadataValue.Value(decoded)
        }

        private fun readCapabilities(
            rpc: OnDeviceAiProviderSessionRpc,
        ): MetadataValue<OnDeviceAiCapabilities>? {
            if (!beginBinderRpc()) return null
            val envelope = try {
                rpc.getCapabilitiesEnvelope()
            } catch (_: OnDeviceAiProviderRemoteSessionDeadException) {
                return MetadataValue.BinderDied
            } catch (_: SecurityException) {
                return MetadataValue.Failed(OnDeviceAiProviderSessionRunFailure.REMOTE_SECURITY)
            } catch (_: Exception) {
                return MetadataValue.Failed(OnDeviceAiProviderSessionRunFailure.REMOTE_FAILURE)
            } finally {
                endBinderRpc()
            } ?: return MetadataValue.Failed(
                OnDeviceAiProviderSessionRunFailure.CAPABILITIES_MISSING,
            )
            if (envelope.isEmpty()) {
                return MetadataValue.Failed(OnDeviceAiProviderSessionRunFailure.CAPABILITIES_MISSING)
            }
            if (envelope.size > AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES) {
                return MetadataValue.Failed(OnDeviceAiProviderSessionRunFailure.CAPABILITIES_TOO_LARGE)
            }
            val decoded = runCatching {
                OnDeviceAiCodec.decodeCapabilities(envelope.copyOf())
            }.getOrNull() ?: return MetadataValue.Failed(
                OnDeviceAiProviderSessionRunFailure.CAPABILITIES_INVALID,
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

        private fun prepareAndLaunchOpen(openedRpc: OnDeviceAiProviderSessionRpc) {
            if (stopForDeadlineOrTerminal()) return
            rpc = openedRpc
            val prepared = try {
                openedRpc.prepareOpen(
                    callback = checkNotNull(dispatch).callback,
                    onSessionBinderDeath = ::binderDied,
                    operationObserver = operationObserver,
                )
            } catch (_: SecurityException) {
                terminateTransport(OnDeviceAiProviderSessionRunFailure.REMOTE_SECURITY)
                return
            } catch (_: Exception) {
                terminateTransport(OnDeviceAiProviderSessionRunFailure.REMOTE_FAILURE)
                return
            }
            preparedOpenSlot.install(prepared)
            if (!isStopping()) submitBlocking { performOpen(prepared) }
        }

        private fun performOpen(prepared: OnDeviceAiProviderSessionPreparedOpen) {
            if (stopForDeadlineOrTerminal()) return
            if (!reinspectMatches()) {
                terminateTransport(OnDeviceAiProviderSessionRunFailure.IDENTITY_DRIFT)
                return
            }
            if (stopForDeadlineOrTerminal()) return
            if (!beginOpenTransaction()) {
                resolveWithoutOpen()
                return
            }
            val decision = checkNotNull(dispatch)
            var remote: OnDeviceAiProviderSessionRemote? = null
            var failure: OnDeviceAiProviderSessionRunFailure? = null
            var binderDead = false
            try {
                remote = prepared.open(
                    requestEnvelope = decision.copyRequestEnvelope(),
                    requestDescriptors = decision.requestDescriptors,
                )
            } catch (_: OnDeviceAiProviderRemoteSessionDescriptorMismatchException) {
                failure = OnDeviceAiProviderSessionRunFailure.SESSION_DESCRIPTOR_MISMATCH
            } catch (_: OnDeviceAiProviderRemoteSessionDeadException) {
                binderDead = true
            } catch (_: SecurityException) {
                failure = OnDeviceAiProviderSessionRunFailure.REMOTE_SECURITY
            } catch (_: Exception) {
                failure = OnDeviceAiProviderSessionRunFailure.REMOTE_FAILURE
            } catch (_: Throwable) {
                // The blocking-owner boundary catches Throwable. Resolve the policy open here as
                // well, otherwise an Error after synchronous dispatch would strand its request
                // descriptors and the process gate in the pre-commit state forever.
                failure = OnDeviceAiProviderSessionRunFailure.INTERNAL_FAILURE
            } finally {
                endOpenTransaction()
            }
            when {
                binderDead -> {
                    binderDied()
                    policy.commitOpenFailed()
                }
                failure != null -> {
                    if (failure == OnDeviceAiProviderSessionRunFailure.SESSION_DESCRIPTOR_MISMATCH) {
                        owner.fuse(request.snapshot.component)
                    }
                    terminateTransport(requireNotNull(failure), resolveOpen = false)
                    policy.commitOpenFailed()
                }
                remote != null -> policy.commitOpenSucceeded(remote)
                else -> {
                    terminateTransport(
                        OnDeviceAiProviderSessionRunFailure.REMOTE_FAILURE,
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
            failure: OnDeviceAiProviderSessionRunFailure,
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
                outerTerminalOverride = OnDeviceAiProviderSessionRunEvent.Rejected(failure)
            }
            if (accepted) {
                closeTransportEarly()
                if (resolveOpen) resolveWithoutOpen()
            }
            finishPolicyTerminalClaim()?.let(::resumeDeferredPolicyTerminal)
        }

        private fun claimDirectTerminal(event: OnDeviceAiProviderSessionRunEvent) {
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
                    check(!binderRpcInFlight) { "on-device AI provider Binder RPC calls must be serial" }
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
                check(openState == OpenState.IN_FLIGHT) { "on-device AI session open is not in flight" }
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
                    check(!binderRpcInFlight) { "on-device AI provider Binder RPC calls must be serial" }
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

        private fun acceptPolicyEvent(event: OnDeviceAiProviderSessionEvent) {
            if (!event.isSessionTerminal()) {
                val deliver = synchronized(lifecycleLock) {
                    !outcomeDelivered && policyTerminalEvent == null &&
                        outerTerminalOverride == null && !policyTerminalClaimInProgress
                }
                if (deliver) deliver(OnDeviceAiProviderSessionRunEvent.Transcript(event))
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
        private fun finishPolicyTerminalClaim(): OnDeviceAiProviderSessionEvent? =
            synchronized(lifecycleLock) {
                check(policyTerminalClaimInProgress) {
                    "on-device AI session policy terminal claim was not in progress"
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

        private fun resumeDeferredPolicyTerminal(event: OnDeviceAiProviderSessionEvent) {
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
            event: OnDeviceAiProviderSessionEvent,
        ): OnDeviceAiProviderSessionRunEvent = if (event is OnDeviceAiProviderSessionEvent.Rejected) {
            OnDeviceAiProviderSessionRunEvent.Rejected(
                failure = OnDeviceAiProviderSessionRunFailure.TRANSCRIPT_REJECTED,
                transcriptFailure = event.failure,
            )
        } else {
            OnDeviceAiProviderSessionRunEvent.Transcript(event)
        }

        private fun submitBlocking(action: () -> Unit) {
            if (isStopping()) return
            try {
                blockingExecutor.execute {
                    val lease = beginCoordinatorOperation() ?: return@execute
                    try {
                        if (!stopForDeadlineOrTerminal()) action()
                    } catch (_: Throwable) {
                        terminateTransport(OnDeviceAiProviderSessionRunFailure.INTERNAL_FAILURE)
                    } finally {
                        lease.close()
                    }
                }
            } catch (_: Exception) {
                terminateTransport(OnDeviceAiProviderSessionRunFailure.EXECUTOR_REJECTED)
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
                        terminateTransport(OnDeviceAiProviderSessionRunFailure.INTERNAL_FAILURE)
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
                    terminateTransport(OnDeviceAiProviderSessionRunFailure.EXECUTOR_REJECTED)
                }
            } catch (_: RuntimeException) {
                terminateTransport(OnDeviceAiProviderSessionRunFailure.EXECUTOR_REJECTED)
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
                check(operationsInFlight > 0) { "on-device AI session operation count underflow" }
                operationsInFlight -= 1
            }
            settleIfReady()
        }

        private fun reinspectMatches(): Boolean = reinspectionSnapshot()?.let {
            request.snapshot.samePackageIdentityAs(it)
        } == true

        private fun reinspectionSnapshot(): OnDeviceAiProviderPackageSnapshot? {
            val result = try {
                OnDeviceAiProviderDiscoveryPolicy.inspectExact(
                    source = object : OnDeviceAiProviderDiscoverySource {
                        override fun discoverExactAction(action: String) =
                            OnDeviceAiProviderDiscoverySourceResult.WrongAction

                        override fun inspectExact(component: OnDeviceAiProviderComponent) =
                            transport.inspectExact(component)
                    },
                    component = request.snapshot.component,
                    options = OnDeviceAiProviderDiscoveryOptions(enabled = true),
                )
            } catch (_: Exception) {
                return null
            }
            val completed = result as? OnDeviceAiProviderDiscoveryResult.Completed ?: return null
            val discovered = completed.evaluations.singleOrNull()
                as? OnDeviceAiProviderDiscoveryEvaluation.Discovered ?: return null
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

        private fun deliver(event: OnDeviceAiProviderSessionRunEvent) {
            runCatching { onEvent(event) }
        }

        private fun saturatingAdd(left: Long, right: Long): Long =
            if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
    }

    private sealed class MetadataReadResult {
        object Abandoned : MetadataReadResult()
        object BinderDied : MetadataReadResult()
        data class Failed(val failure: OnDeviceAiProviderSessionRunFailure) : MetadataReadResult()
        data class Complete(val rpc: OnDeviceAiProviderSessionRpc) : MetadataReadResult()
    }

    private sealed class MetadataValue<out T> {
        object BinderDied : MetadataValue<Nothing>()
        data class Value<T>(val value: T) : MetadataValue<T>()
        data class Failed(val failure: OnDeviceAiProviderSessionRunFailure) : MetadataValue<Nothing>()
    }

    private data class Settlement(
        val releaseOwner: Boolean,
        val event: OnDeviceAiProviderSessionRunEvent,
    )

    private enum class OpenState {
        NOT_ENTERED,
        IN_FLIGHT,
        RESOLVED,
    }
}

private fun OnDeviceAiProviderPackageSnapshot.matchesSessionPinnedPackage(
    pinned: OnDeviceAiProviderPinnedIdentity,
): Boolean =
    component == pinned.component &&
        packageUid == pinned.packageUid &&
        versionCode == pinned.versionCode &&
        lastUpdateTime == pinned.lastUpdateTime &&
        signerSha256 == pinned.signerSha256

private fun OnDeviceAiProviderSessionEvent.isSessionTerminal(): Boolean = when (this) {
    is OnDeviceAiProviderSessionEvent.Started,
    is OnDeviceAiProviderSessionEvent.Chunk,
    is OnDeviceAiProviderSessionEvent.Usage,
    -> false
    OnDeviceAiProviderSessionEvent.Disabled,
    is OnDeviceAiProviderSessionEvent.Completed,
    is OnDeviceAiProviderSessionEvent.ProviderFailed,
    OnDeviceAiProviderSessionEvent.Cancelled,
    OnDeviceAiProviderSessionEvent.TimedOut,
    OnDeviceAiProviderSessionEvent.BinderDied,
    is OnDeviceAiProviderSessionEvent.Rejected,
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
            check(value == null) { "on-device AI session resource was installed more than once" }
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
