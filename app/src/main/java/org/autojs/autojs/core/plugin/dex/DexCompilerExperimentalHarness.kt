package org.autojs.autojs.core.plugin.dex

import org.autojs.autojs.core.plugin.concurrentSnapshot
import org.autojs.plugin.dexcompiler.api.DexCompileCancellation
import org.autojs.plugin.dexcompiler.api.DexCompileError
import org.autojs.plugin.dexcompiler.api.DexCompilerErrorCode
import org.autojs.plugin.dexcompiler.api.DexCompilerFailurePhase
import org.autojs.plugin.dexcompiler.api.DexCompileProgress
import org.autojs.plugin.dexcompiler.api.DexCompileRequest
import org.autojs.plugin.dexcompiler.api.DexCompileResult
import org.autojs.plugin.dexcompiler.api.DexCompileStarted
import org.autojs.plugin.dexcompiler.api.DexCompilerCapabilities
import org.autojs.plugin.dexcompiler.api.DexCompilerCodec
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.autojs.plugin.dexcompiler.api.DexCompilerDiagnostic
import org.autojs.plugin.dexcompiler.api.DexCompilerFamily
import org.autojs.plugin.dexcompiler.api.DexCompilerInfo
import org.autojs.plugin.dexcompiler.api.DexCompilerNegotiation
import org.autojs.plugin.dexcompiler.api.DexCompilerValidation
import java.io.Closeable
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal data class DexCompilerExperimentalInvocation(
    val request: DexCompileRequest,
    val inputSnapshot: DexCompilerInputSnapshot,
    val outputTransaction: DexCompilerOutputTransaction,
    val releaseBuild: Boolean,
    val invocationKind: DexCompilerInvocationKind = DexCompilerInvocationKind.ASYNCHRONOUS,
    val preferredComponent: DexCompilerComponent? = null,
    val requestFinalization: DexCompilerRequestFinalization = DexCompilerRequestFinalization.NONE,
    val requiredIdentity: DexCompilerPinnedIdentity? = null,
    val dispatchObserver: DexCompilerDispatchObserver = DexCompilerDispatchObserver.NONE,
) {
    init {
        require(requiredIdentity == null || preferredComponent == requiredIdentity.component) {
            "The required DEX compiler identity must match the preferred component"
        }
    }
}

internal fun interface DexCompilerDispatchObserver {
    fun onDispatchCommitted()

    companion object {
        val NONE = DexCompilerDispatchObserver {}
    }
}

internal fun interface DexCompilerMonotonicClock {
    fun elapsedRealtimeMillis(): Long
}

private val SYSTEM_DEX_COMPILER_MONOTONIC_CLOCK = DexCompilerMonotonicClock {
    System.nanoTime() / 1_000_000L
}

/**
 * Controls whether the two provider-owned request fields are finalized after authentication.
 *
 * The fixed capability-bound path cannot alter caller-owned resource limits, input identity,
 * timeout, or compilation semantics. Keeping this as a closed enum also prevents an arbitrary
 * request callback from blocking the serial control lane between handshake and dispatch.
 */
internal enum class DexCompilerRequestFinalization {
    NONE,
    CAPABILITY_BOUND,
    /** Requires the provider to support the request's already selected protocol version exactly. */
    CAPABILITY_BOUND_EXACT_PROTOCOL,
}

internal enum class DexCompilerExperimentalFailure {
    EXPERIMENT_DISABLED,
    MAIN_THREAD_SYNC_FAIL_OPEN,
    INPUT_REJECTED,
    PLUGIN_UNAVAILABLE,
    IDENTITY_DRIFT,
    HANDSHAKE_REJECTED,
    BIND_FAILED,
    DISPATCH_FAILED,
    BINDER_DIED,
    TRANSPORT_VIOLATION,
    CALLBACK_VIOLATION,
    REMOTE_FAILED,
    REMOTE_CANCELLED,
    TIMEOUT,
    OUTPUT_REJECTED,
    WORKER_REJECTED,
    BLOCKING_EXECUTOR_REJECTED,
    BUSY,
}

internal sealed class DexCompilerExperimentalOutcome {
    data class Published(
        val result: DexCompileResult,
        val validation: DexIndexedZipValidationResult,
        val identity: DexCompilerPinnedIdentity,
        val artifact: DexCompilerVerifiedArtifact,
    ) : DexCompilerExperimentalOutcome() {
        val cacheKeySha256: String
            get() = artifact.cacheKeySha256
    }

    data class CacheHit(
        val validation: DexIndexedZipValidationResult,
        val identity: DexCompilerPinnedIdentity,
        val artifact: DexCompilerVerifiedArtifact,
    ) : DexCompilerExperimentalOutcome() {
        val cacheKeySha256: String
            get() = artifact.cacheKeySha256
    }

    class RemoteFailure(
        val code: DexCompilerErrorCode,
        val phase: DexCompilerFailurePhase,
        val retryable: Boolean,
        val message: String? = null,
        diagnostics: Collection<DexCompilerDiagnostic> = emptyList(),
    ) {
        val diagnostics: List<DexCompilerDiagnostic> =
            java.util.Collections.unmodifiableList(java.util.ArrayList(diagnostics))

        override fun toString(): String = buildString {
            append("RemoteFailure(code=$code, phase=$phase, retryable=$retryable, ")
            append("hasMessage=${!message.isNullOrEmpty()}, ")
            append(DexCompilerDiagnosticSummary.from(diagnostics))
            append(')')
        }
    }

    data class UseLocalCompiler(
        val failure: DexCompilerExperimentalFailure,
        val pluginWasDispatched: Boolean,
        val pluginOutputRollbackRequired: Boolean,
        val remoteFailure: RemoteFailure? = null,
    ) : DexCompilerExperimentalOutcome()

    object Cancelled : DexCompilerExperimentalOutcome()
}

/**
 * Default-off experimental harness. The runtime reaches it only after an exact provider has been
 * explicitly selected and revalidated against the production opt-in policy.
 *
 * Control, callbacks, validation, and cleanup are serialized on [workerExecutor]. Potentially
 * blocking Binder handshakes and dispatch run on [blockingExecutor]. Production must provide a
 * bounded, zero-queue executor and fuse the provider off after a hung Binder call; a Java thread
 * blocked inside Binder cannot be killed safely. The transport is dedicated to this protocol and
 * must not delegate to the generic plugin host.
 */
internal class DexCompilerExperimentalHarness(
    private val transport: DexCompilerExperimentalTransport,
    workerExecutor: Executor,
    private val blockingExecutor: Executor,
    private val deadlineScheduler: DexCompilerDeadlineScheduler,
    private val monotonicClock: DexCompilerMonotonicClock = SYSTEM_DEX_COMPILER_MONOTONIC_CLOCK,
    private val threadProbe: DexCompilerThreadProbe,
    private val hostVersionCode: Long,
    private val hostRuntimeApi: Int,
    urgentControlCapacity: Int = 16,
    normalControlCapacity: Int = 64,
    private val maximumInMemoryOutputBytes: Long = DexCompilerExperimentalHostLimits.MAX_IN_MEMORY_OUTPUT_BYTES,
    private val outputVerifier: DexCompilerOutputVerifier = DexCompilerOutputVerifier { bytes, limits ->
        DexIndexedZipValidator.validate(bytes, limits)
    },
    private val verifiedCache: DexCompilerVerifiedCache = DexCompilerVerifiedCache { null },
) {

    init {
        require(maximumInMemoryOutputBytes > 0L)
        require(hostRuntimeApi > 0)
    }

    private val attempts = ConcurrentHashMap.newKeySet<Attempt>()
    private val activeAttempt = AtomicReference<Attempt?>()
    private val lane = DexCompilerSerialExecutor(
        delegate = workerExecutor,
        onRejected = { attempts.concurrentSnapshot().forEach(Attempt::workerRejected) },
        urgentCapacity = urgentControlCapacity,
        normalCapacity = normalControlCapacity,
    )

    fun start(
        invocation: DexCompilerExperimentalInvocation,
        onOutcome: (DexCompilerExperimentalOutcome) -> Unit,
    ): Closeable {
        val attempt = Attempt(invocation, onOutcome)
        val claimed = activeAttempt.compareAndSet(null, attempt)
        attempts += attempt
        if (!claimed) {
            attempt.useLocal(DexCompilerExperimentalFailure.BUSY)
            return Closeable {}
        }
        val gate = DexCompilerExperimentalEntryGate.decide(
            invocationKind = invocation.invocationKind,
            isMainThread = threadProbe.isMainThread(),
        )
        attempt.enqueue {
            if (gate == DexCompilerEntryDecision.FAIL_OPEN_TO_LOCAL) {
                attempt.useLocal(DexCompilerExperimentalFailure.MAIN_THREAD_SYNC_FAIL_OPEN)
            } else if (invocation.preferredComponent == null) {
                // V1 has no automatic provider selection. A caller must carry an explicit,
                // already persisted component choice into this disconnected experiment.
                attempt.useLocal(DexCompilerExperimentalFailure.EXPERIMENT_DISABLED)
            } else {
                attempt.begin()
            }
        }
        return Closeable { attempt.requestHostCancellation() }
    }

    private inner class Attempt(
        private val invocation: DexCompilerExperimentalInvocation,
        private val onOutcome: (DexCompilerExperimentalOutcome) -> Unit,
    ) {
        private val deadlineAtMillis = saturatingAdd(
            monotonicClock.elapsedRealtimeMillis(),
            invocation.request.timeoutMillis,
        )
        private val lifecycle = DexCompilerSessionLifecycle()
        private val fallback = DexCompilerFallbackController()
        private val publicationGate = DexCompilerPipePublicationGate()
        private val terminalCommitLock = Any()
        private val bindingSlot = CloseOnceSlot<DexCompilerBinding>()
        private val deadlineSlot = CloseOnceSlot<Closeable>()
        private val sessionSlot = RemoteSessionSlot()
        private val programDescriptorSlot = CloseOnceSlot<DexCompilerOwnedDescriptor>()
        private val outputDescriptorSlot = CloseOnceSlot<DexCompilerOwnedDescriptor>()
        private val finished = AtomicBoolean(false)
        private val terminalClaimed = AtomicBoolean(false)
        private val blockingJobInFlight = AtomicBoolean(false)
        private val deadlineScheduleClaimed = AtomicBoolean(false)
        private val hostCancellationIngressClaimed = AtomicBoolean(false)
        private val timeoutIngressClaimed = AtomicBoolean(false)
        private val connectionIngressClaimed = AtomicBoolean(false)
        private val bindingFailureIngressClaimed = AtomicBoolean(false)
        private val binderDeathIngressClaimed = AtomicBoolean(false)
        private val startedIngressClaimed = AtomicBoolean(false)
        private val terminalIngressClaimed = AtomicBoolean(false)
        private val progressPending = AtomicBoolean(false)
        private val callbackViolationIngressClaimed = AtomicBoolean(false)
        private val transportViolationIngressClaimed = AtomicBoolean(false)
        private var pinnedIdentity: DexCompilerPinnedIdentity? = null
        private var compilerInfo: DexCompilerInfo? = null
        private var capabilities: DexCompilerCapabilities? = null
        private var effectiveRequest: DexCompileRequest = invocation.request
        private var completedResult: DexCompileResult? = null
        private var lastWireSequence: Long? = null
        private var bindClaimed = false

        fun begin() {
            if (finished.get()) return
            try {
                verifyInputSnapshot()
            } catch (_: Throwable) {
                return useLocal(DexCompilerExperimentalFailure.INPUT_REJECTED)
            }
            try {
                if (!deadlineScheduleClaimed.compareAndSet(false, true)) return callbackViolation()
                var remainingMillis = remainingDeadlineMillis()
                if (remainingMillis <= 0L) {
                    remainingMillis = remainingDeadlineUnlessAbortPending() ?: return
                    if (remainingMillis <= 0L) return timeout()
                }
                // Preserve the deadline as a backstop even when an earlier abort action is queued.
                deadlineSlot.install(
                    deadlineScheduler.schedule(remainingMillis) {
                        if (claimAbortIngress(timeoutIngressClaimed)) enqueueTimeout()
                    },
                )
                if (finished.get()) return
                val remainingAfterSchedule = remainingDeadlineUnlessAbortPending() ?: return
                if (remainingAfterSchedule <= 0L) return timeout()
                val candidates = transport.discoverExactAction(DexCompilerContract.SERVICE_ACTION)
                val requiredIdentity = invocation.requiredIdentity
                val identity = if (requiredIdentity == null) {
                    DexCompilerExperimentalPolicy.selectAndPin(
                        candidates = candidates,
                        releaseBuild = invocation.releaseBuild,
                        preferredComponent = invocation.preferredComponent,
                    ) ?: return useLocal(DexCompilerExperimentalFailure.PLUGIN_UNAVAILABLE)
                } else {
                    val decision = DexCompilerOptInPolicy.decide(
                        options = DexCompilerOptInOptions(
                            enabled = true,
                            selectedComponent = requiredIdentity.component,
                        ),
                        releaseBuild = invocation.releaseBuild,
                        discoverCandidates = { candidates },
                    )
                    val selected = (decision as? DexCompilerOptInDecision.Selected)?.identity
                        ?: return useLocal(DexCompilerExperimentalFailure.IDENTITY_DRIFT)
                    if (selected != requiredIdentity) {
                        return useLocal(DexCompilerExperimentalFailure.IDENTITY_DRIFT)
                    }
                    selected
                }
                pinnedIdentity = identity
                if (bindClaimed) return callbackViolation()
                bindClaimed = true
                val binding = transport.bindExact(identity, object : DexCompilerBindingCallback {
                    override fun onConnected(connection: DexCompilerRemoteConnection) {
                        if (connectionIngressClaimed.compareAndSet(false, true)) {
                            enqueue { connected(connection) }
                        } else {
                            signalCallbackViolation()
                        }
                    }

                    override fun onBindingFailure(cause: Throwable?) {
                        if (claimAbortIngress(bindingFailureIngressClaimed)) {
                            enqueueUrgent { useLocal(DexCompilerExperimentalFailure.BIND_FAILED) }
                        }
                    }

                    override fun onBinderDeath() {
                        if (claimAbortIngress(binderDeathIngressClaimed)) enqueueUrgent(::binderDied)
                    }
                })
                bindingSlot.install(binding)
            } catch (_: Throwable) {
                useLocal(DexCompilerExperimentalFailure.BIND_FAILED)
            }
        }

        private fun connected(connection: DexCompilerRemoteConnection) {
            if (finished.get()) return
            val identity = pinnedIdentity ?: return callbackViolation()
            try {
                val current = transport.inspectExact(identity.component)
                val requiredIdentity = invocation.requiredIdentity
                val identityIsValid = if (requiredIdentity == null) {
                    DexCompilerExperimentalPolicy.revalidate(identity, current, invocation.releaseBuild)
                } else {
                    val decision = DexCompilerOptInPolicy.decide(
                        options = DexCompilerOptInOptions(
                            enabled = true,
                            selectedComponent = requiredIdentity.component,
                        ),
                        releaseBuild = invocation.releaseBuild,
                        discoverCandidates = { listOfNotNull(current) },
                    )
                    identity == requiredIdentity &&
                        (decision as? DexCompilerOptInDecision.Selected)?.identity == requiredIdentity
                }
                if (!identityIsValid) {
                    return useLocal(DexCompilerExperimentalFailure.IDENTITY_DRIFT, cancelRemote = true)
                }
            } catch (_: Throwable) {
                return useLocal(DexCompilerExperimentalFailure.IDENTITY_DRIFT, cancelRemote = true)
            }
            beginHandshake(connection, identity)
        }

        private fun beginHandshake(
            connection: DexCompilerRemoteConnection,
            identity: DexCompilerPinnedIdentity,
        ) {
            if (!blockingJobInFlight.compareAndSet(false, true)) return callbackViolation()
            val accepted = try {
                blockingExecutor.execute {
                    val result = runCatching {
                        val encodedInfo = connection.getCompilerInfo()
                        requireMetadataSize(encodedInfo)
                        val info = DexCompilerCodec.decodeInfo(encodedInfo)
                        val encodedCapabilities = connection.getCapabilities()
                        requireMetadataSize(encodedCapabilities)
                        Handshake(
                            info = info,
                            capabilities = DexCompilerCodec.decodeCapabilities(encodedCapabilities),
                        )
                    }
                    enqueueUrgent {
                        blockingJobInFlight.set(false)
                        if (finished.get()) return@enqueueUrgent
                        result.fold(
                            onSuccess = { handshake -> completeHandshake(connection, identity, handshake) },
                            onFailure = {
                                useLocal(
                                    DexCompilerExperimentalFailure.HANDSHAKE_REJECTED,
                                    cancelRemote = true,
                                )
                            },
                        )
                    }
                }
                true
            } catch (_: RuntimeException) {
                false
            }
            if (!accepted) {
                blockingJobInFlight.set(false)
                useLocal(DexCompilerExperimentalFailure.BLOCKING_EXECUTOR_REJECTED, cancelRemote = true)
            }
        }

        private fun completeHandshake(
            connection: DexCompilerRemoteConnection,
            identity: DexCompilerPinnedIdentity,
            handshake: Handshake,
        ) {
            try {
                effectiveRequest = validateHandshake(identity, handshake.info, handshake.capabilities)
                compilerInfo = handshake.info
                capabilities = handshake.capabilities
                // A timeout, cancellation, or transport abort may have entered while the Binder
                // handshake occupied the blocking executor. Its urgent lane action owns the
                // terminal decision; never dispatch a new remote session in front of it.
                if (!hasPendingAbortIngress() && !tryFinishFromCache() && !hasPendingAbortIngress()) {
                    beginDispatch(connection)
                }
            } catch (_: Throwable) {
                useLocal(DexCompilerExperimentalFailure.HANDSHAKE_REJECTED, cancelRemote = true)
            }
        }

        /**
         * A cache lookup is deliberately after the authenticated handshake because compiler
         * version, runtime-library fingerprint, and provider provenance are semantic-key fields.
         * A hit is consumed before either program or output descriptor is claimed.
         */
        private fun tryFinishFromCache(): Boolean {
            val cacheKeySha256 = semanticCacheKey()
            val artifact = try {
                verifiedCache.lookup(cacheKeySha256)
            } catch (_: Exception) {
                null
            } ?: return false

            val validation = try {
                require(artifact.cacheKeySha256 == cacheKeySha256) { "Cached semantic key changed" }
                val maximum = maximumOutputBytes()
                require(artifact.sizeBytes in 1L..maximum) { "Cached output size is outside negotiated limits" }
                require(artifact.sizeBytes <= Int.MAX_VALUE.toLong()) { "Cached output cannot be materialized safely" }
                val expectedSha256 = artifact.outputSha256
                require(expectedSha256.matches(LOWERCASE_SHA256)) { "Cached output SHA-256 is malformed" }
                val bytes = artifact.openRead()?.use { input ->
                    val result = ByteArray(artifact.sizeBytes.toInt())
                    var offset = 0
                    while (offset < result.size) {
                        val count = input.read(result, offset, result.size - offset)
                        if (count < 0) error("Cached output ended before its declared size")
                        if (count == 0) continue
                        offset += count
                    }
                    require(input.read() < 0) { "Cached output exceeds its declared size" }
                    result
                } ?: error("Cached output generation is unavailable")
                require(sha256(bytes) == expectedSha256) { "Cached output SHA-256 changed" }
                outputVerifier.verify(bytes, outputLimits())
            } catch (_: Exception) {
                runCatching { artifact.invalidate() }
                return false
            }

            // An abort registered during validation owns the terminal result. The cache remains
            // valid and the queued urgent action will close this attempt without remote dispatch.
            when (tryClaimPublicationTerminal()) {
                DexCompilerTerminalCommitDecision.COMMITTED -> Unit
                DexCompilerTerminalCommitDecision.ABORT_PENDING -> return true
                DexCompilerTerminalCommitDecision.DEADLINE_EXPIRED -> {
                    timeout()
                    return true
                }
            }
            check(fallback.finish()) { "Compilation attempt was already terminal" }
            finish(
                DexCompilerExperimentalOutcome.CacheHit(
                    validation = validation,
                    identity = pinnedIdentity ?: error("Pinned identity is missing"),
                    artifact = artifact,
                ),
                rollback = true,
            )
            return true
        }

        private fun validateHandshake(
            identity: DexCompilerPinnedIdentity,
            info: DexCompilerInfo,
            capabilities: DexCompilerCapabilities,
        ): DexCompileRequest {
            val negotiated = when (invocation.requestFinalization) {
                DexCompilerRequestFinalization.CAPABILITY_BOUND_EXACT_PROTOCOL ->
                    DexCompilerNegotiation.requireNegotiated(
                        providerInfo = info,
                        hostMin = invocation.request.protocolVersion,
                        hostMax = invocation.request.protocolVersion,
                    )
                DexCompilerRequestFinalization.NONE,
                DexCompilerRequestFinalization.CAPABILITY_BOUND ->
                    DexCompilerNegotiation.requireNegotiated(info)
            }
            require(info.providerVersionCode == identity.versionCode) { "Provider version identity drift" }
            val minHostVersionCode = info.minHostVersionCode
            val maxHostVersionCode = info.maxHostVersionCode
            require(minHostVersionCode == null || hostVersionCode >= minHostVersionCode) {
                "Host version is below provider minimum"
            }
            require(maxHostVersionCode == null || hostVersionCode <= maxHostVersionCode) {
                "Host version exceeds provider maximum"
            }
            val request = when (invocation.requestFinalization) {
                DexCompilerRequestFinalization.NONE -> invocation.request
                DexCompilerRequestFinalization.CAPABILITY_BOUND -> invocation.request.copy(
                    protocolVersion = negotiated,
                    expectedRuntimeLibraryFingerprint = capabilities.runtimeLibraryFingerprint,
                )
                DexCompilerRequestFinalization.CAPABILITY_BOUND_EXACT_PROTOCOL -> invocation.request.copy(
                    expectedRuntimeLibraryFingerprint = capabilities.runtimeLibraryFingerprint,
                )
            }
            DexCompilerValidation.validateRequestAgainst(request, capabilities, negotiated)
            return request
        }

        private fun beginDispatch(connection: DexCompilerRemoteConnection) {
            // Linearize the dispatch boundary against cancellation/timeout/death ingress before
            // starting the drain or claiming either descriptor. An abort that wins this lock owns
            // the terminal result; an abort after this commit cancels the already-dispatched work.
            when (tryCommitPluginDispatch()) {
                DexCompilerTerminalCommitDecision.COMMITTED -> Unit
                DexCompilerTerminalCommitDecision.ABORT_PENDING -> return
                DexCompilerTerminalCommitDecision.DEADLINE_EXPIRED -> {
                    timeout()
                    return
                }
            }
            notifyDispatchCommitted()
            if (!blockingJobInFlight.compareAndSet(false, true)) return callbackViolation()
            try {
                invocation.outputTransaction.beginDrain(maximumOutputBytes()) { terminal ->
                    when (terminal) {
                        is DexCompilerOutputDrainTerminal.Success -> enqueue { acceptOutputDrain(terminal) }
                        is DexCompilerOutputDrainTerminal.Failure -> enqueueUrgent { acceptOutputDrain(terminal) }
                    }
                }
                val programDescriptor = invocation.inputSnapshot.claimReadDescriptor()
                programDescriptorSlot.install(programDescriptor)
                val outputDescriptor = invocation.outputTransaction.claimWriteDescriptor()
                outputDescriptorSlot.install(outputDescriptor)
                val encodedRequest = DexCompilerCodec.encodeRequest(effectiveRequest)
                try {
                    blockingExecutor.execute {
                        val result = runCatching {
                            connection.openSession(
                                encodedRequest = encodedRequest,
                                programDescriptor = programDescriptor,
                                outputDescriptor = outputDescriptor,
                                callback = callback,
                            )
                        }.mapCatching { session ->
                            sessionSlot.install(session)
                        }
                        programDescriptorSlot.close()
                        outputDescriptorSlot.close()
                        enqueueUrgent {
                            blockingJobInFlight.set(false)
                            if (finished.get()) return@enqueueUrgent
                            result.exceptionOrNull()?.let {
                                useLocal(DexCompilerExperimentalFailure.DISPATCH_FAILED, cancelRemote = true)
                            }
                        }
                    }
                } catch (_: RuntimeException) {
                    blockingJobInFlight.set(false)
                    programDescriptorSlot.close()
                    outputDescriptorSlot.close()
                    useLocal(DexCompilerExperimentalFailure.BLOCKING_EXECUTOR_REJECTED, cancelRemote = true)
                }
            } catch (_: Throwable) {
                blockingJobInFlight.set(false)
                useLocal(DexCompilerExperimentalFailure.DISPATCH_FAILED, cancelRemote = true)
            }
        }

        private fun notifyDispatchCommitted() {
            try {
                invocation.dispatchObserver.onDispatchCommitted()
            } catch (_: Throwable) {
                // Instrumentation must not affect dispatch ownership or the terminal state machine.
            }
        }

        private val callback = object : DexCompilerRemoteCallback {
            override fun onStarted(metadata: ByteArray) {
                if (!claimCallbackPayload(metadata, startedIngressClaimed, requireStarted = false)) return
                val copy = metadata.copyOf()
                enqueueUrgent { acceptStarted(copy) }
            }

            override fun onProgress(progress: ByteArray) {
                if (finished.get()) return
                if (!isMetadataSizeAllowed(progress) || !startedIngressClaimed.get() || terminalIngressClaimed.get()) {
                    signalCallbackViolation()
                    return
                }
                if (!progressPending.compareAndSet(false, true)) return
                val copy = progress.copyOf()
                val accepted = lane.tryExecuteData(Runnable {
                    try {
                        acceptProgress(copy)
                    } finally {
                        progressPending.set(false)
                    }
                })
                if (!accepted) progressPending.set(false)
            }

            override fun onCompleted(result: ByteArray) {
                if (!claimCallbackPayload(result, terminalIngressClaimed, requireStarted = true)) return
                val copy = result.copyOf()
                enqueueUrgent { acceptCompleted(copy) }
            }

            override fun onFailed(error: ByteArray) {
                if (!claimCallbackPayload(error, terminalIngressClaimed, requireStarted = false)) return
                val copy = error.copyOf()
                enqueueUrgent { acceptFailed(copy) }
            }

            override fun onCancelled(cancellation: ByteArray) {
                if (!claimCallbackPayload(cancellation, terminalIngressClaimed, requireStarted = false)) return
                val copy = cancellation.copyOf()
                enqueueUrgent { acceptCancelled(copy) }
            }

            override fun onTransportViolation(cause: Throwable?) {
                if (claimAbortIngress(transportViolationIngressClaimed)) {
                    enqueueUrgent(::transportViolation)
                }
            }
        }

        private fun claimCallbackPayload(
            payload: ByteArray,
            claim: AtomicBoolean,
            requireStarted: Boolean,
        ): Boolean {
            if (finished.get()) return false
            if (!isMetadataSizeAllowed(payload) || (requireStarted && !startedIngressClaimed.get()) ||
                !claim.compareAndSet(false, true)
            ) {
                signalCallbackViolation()
                return false
            }
            if (claim === startedIngressClaimed && terminalIngressClaimed.get()) {
                signalCallbackViolation()
                return false
            }
            return true
        }

        private fun acceptStarted(encoded: ByteArray) {
            if (finished.get()) return
            val started = decodeOrReject(DexCompilerCodec::decodeStarted, encoded) ?: return
            if (!matchesStarted(started) || !lifecycle.acceptStarted()) return callbackViolation()
            lastWireSequence = started.sequence
        }

        private fun acceptProgress(encoded: ByteArray) {
            if (finished.get()) return
            val progress = decodeOrReject(DexCompilerCodec::decodeProgress, encoded) ?: return
            val previous = lastWireSequence
            if (progress.requestId != effectiveRequest.requestId || previous == null || progress.sequence <= previous ||
                !lifecycle.acceptProgress(progress.sequence)
            ) {
                return callbackViolation()
            }
            lastWireSequence = progress.sequence
        }

        private fun acceptCompleted(encoded: ByteArray) {
            if (finished.get()) return
            val result = decodeOrReject(DexCompilerCodec::decodeResult, encoded) ?: return
            if (!matchesResult(result) || !lifecycle.acceptCompleted()) return callbackViolation()
            completedResult = result
            applyPublicationDecision(
                publicationGate.acceptProviderCompletion(
                    DexCompilerStreamSummary(
                        sizeBytes = result.outputSizeBytes,
                        sha256 = result.outputSha256.toHexString(),
                    ),
                ),
            )
        }

        private fun acceptOutputDrain(terminal: DexCompilerOutputDrainTerminal) {
            if (finished.get()) return
            val decision = when (terminal) {
                is DexCompilerOutputDrainTerminal.Success -> publicationGate.acceptDrainSuccess(terminal.summary)
                is DexCompilerOutputDrainTerminal.Failure -> publicationGate.acceptDrainFailure()
            }
            applyPublicationDecision(decision)
        }

        private fun applyPublicationDecision(decision: DexCompilerPipePublicationGate.Decision) {
            when (decision) {
                DexCompilerPipePublicationGate.Decision.Waiting,
                DexCompilerPipePublicationGate.Decision.Ignored -> Unit
                is DexCompilerPipePublicationGate.Decision.Publish -> {
                    val result = completedResult ?: return callbackViolation()
                    verifyAndPublish(result, decision.summary)
                }
                is DexCompilerPipePublicationGate.Decision.Rollback ->
                    useLocal(DexCompilerExperimentalFailure.OUTPUT_REJECTED, cancelRemote = true)
            }
        }

        private fun acceptFailed(encoded: ByteArray) {
            if (finished.get()) return
            val error = decodeOrReject(DexCompilerCodec::decodeError, encoded) ?: return
            if (!matchesError(error) || !lifecycle.acceptFailed()) return callbackViolation()
            useLocal(
                failure = DexCompilerExperimentalFailure.REMOTE_FAILED,
                remoteFailure = DexCompilerExperimentalOutcome.RemoteFailure(
                    code = error.code,
                    phase = error.phase,
                    retryable = error.retryable,
                    message = error.message,
                    diagnostics = error.diagnostics,
                ),
            )
        }

        private fun acceptCancelled(encoded: ByteArray) {
            if (finished.get()) return
            val cancellation = decodeOrReject(DexCompilerCodec::decodeCancellation, encoded) ?: return
            if (!matchesCancellation(cancellation) || !lifecycle.acceptCancelled()) return callbackViolation()
            when {
                hostCancellationIngressClaimed.get() -> {
                    if (!terminalClaimed.compareAndSet(false, true)) return
                    check(fallback.cancel()) { "Cancellation terminal state was already claimed" }
                    finish(DexCompilerExperimentalOutcome.Cancelled, rollback = true)
                }
                timeoutIngressClaimed.get() -> useLocal(DexCompilerExperimentalFailure.TIMEOUT)
                else -> useLocal(DexCompilerExperimentalFailure.REMOTE_CANCELLED)
            }
        }

        private fun matchesStarted(started: DexCompileStarted): Boolean {
            val expected = capabilities ?: return false
            return started.requestId == effectiveRequest.requestId &&
                started.protocolVersion == effectiveRequest.protocolVersion &&
                started.compilerFamily == expected.compilerFamily &&
                started.compilerVersion == expected.compilerVersion &&
                started.runtimeLibraryModel == effectiveRequest.runtimeLibraryModel &&
                started.runtimeLibraryFingerprint == effectiveRequest.expectedRuntimeLibraryFingerprint
        }

        private fun matchesResult(result: DexCompileResult): Boolean {
            val expected = capabilities ?: return false
            return result.requestId == effectiveRequest.requestId &&
                result.compilerFamily == DexCompilerFamily.D8 &&
                result.compilerFamily == expected.compilerFamily &&
                result.compilerVersion == expected.compilerVersion &&
                result.mode == effectiveRequest.mode && result.minApi == effectiveRequest.minApi &&
                result.outputFormat == effectiveRequest.outputFormat &&
                result.runtimeLibraryFingerprint == effectiveRequest.expectedRuntimeLibraryFingerprint &&
                result.outputSizeBytes <= effectiveRequest.maxOutputBytes &&
                result.outputSizeBytes <= expected.limits.maxOutputBytes &&
                result.dexEntryCount <= expected.limits.maxDexEntries &&
                diagnosticsFitRequest(result.diagnostics)
        }

        private fun matchesError(error: DexCompileError): Boolean =
            error.requestId == effectiveRequest.requestId && diagnosticsFitRequest(error.diagnostics)

        private fun matchesCancellation(cancellation: DexCompileCancellation): Boolean =
            cancellation.requestId == effectiveRequest.requestId

        private fun verifyAndPublish(
            result: DexCompileResult,
            drainedSummary: DexCompilerStreamSummary,
        ) {
            var ownsTerminal = false
            var publishedArtifact: DexCompilerVerifiedArtifact? = null
            try {
                if (capabilities == null) return callbackViolation()
                val maximum = maximumOutputBytes()
                val bytes = invocation.outputTransaction.readBytes(maximum)
                require(bytes.size.toLong() == drainedSummary.sizeBytes) { "Drained output size changed" }
                require(sha256(bytes) == drainedSummary.sha256) { "Drained output SHA-256 changed" }
                require(bytes.size.toLong() == result.outputSizeBytes) { "Output size metadata mismatch" }
                require(sha256(bytes) == result.outputSha256.toHexString()) { "Output SHA-256 metadata mismatch" }
                val validation = outputVerifier.verify(bytes, outputLimits())
                require(validation.dexEntryCount == result.dexEntryCount) { "DEX entry metadata mismatch" }
                // Queue rejection can fail closed from outside the lane. Claim terminal ownership
                // before publication so no fallback can race between the cache commit and outcome.
                when (tryClaimPublicationTerminal()) {
                    DexCompilerTerminalCommitDecision.COMMITTED -> Unit
                    DexCompilerTerminalCommitDecision.ABORT_PENDING -> {
                        publicationGate.publicationFailed()
                        return
                    }
                    DexCompilerTerminalCommitDecision.DEADLINE_EXPIRED -> {
                        publicationGate.publicationFailed()
                        timeout()
                        return
                    }
                }
                ownsTerminal = true
                val cacheKeySha256 = semanticCacheKey()
                val artifact = invocation.outputTransaction.publishVerified(cacheKeySha256, bytes)
                publishedArtifact = artifact
                require(artifact.cacheKeySha256 == cacheKeySha256) { "Published cache key changed" }
                require(artifact.sizeBytes == bytes.size.toLong()) { "Published output size changed" }
                require(artifact.outputSha256 == result.outputSha256.toHexString()) {
                    "Published output SHA-256 changed"
                }
                check(fallback.finish()) { "Compilation attempt was already terminal" }
                publicationGate.publicationSucceeded()
                finish(
                    DexCompilerExperimentalOutcome.Published(
                        result = result,
                        validation = validation,
                        identity = pinnedIdentity ?: error("Pinned identity is missing"),
                        artifact = artifact,
                    ),
                    rollback = false,
                )
            } catch (_: Throwable) {
                publishedArtifact?.let { artifact -> runCatching { artifact.invalidate() } }
                publicationGate.publicationFailed()
                if (ownsTerminal) {
                    useLocalClaimed(DexCompilerExperimentalFailure.OUTPUT_REJECTED, cancelRemote = true)
                } else {
                    useLocal(DexCompilerExperimentalFailure.OUTPUT_REJECTED, cancelRemote = true)
                }
            }
        }

        private fun maximumOutputBytes(): Long {
            val expected = capabilities ?: error("Compiler capabilities are missing")
            return minOf(
                effectiveRequest.maxOutputBytes,
                expected.limits.maxOutputBytes,
                maximumInMemoryOutputBytes,
            )
        }

        private fun outputLimits(): DexIndexedZipLimits {
            val expected = capabilities ?: error("Compiler capabilities are missing")
            val maximum = maximumOutputBytes()
            return DexIndexedZipLimits(
                maxContainerBytes = maximum,
                maxDexEntries = minOf(expected.limits.maxDexEntries, DexCompilerContract.MAX_DEX_ENTRIES),
                maxDexEntryBytes = maximum,
                maxTotalDexBytes = maximum,
                allowedDexVersions = dexVersionsCompatibleWithMinApi(effectiveRequest.minApi),
            )
        }

        private fun verifyInputSnapshot() {
            DexCompilerValidation.validateRequest(invocation.request)
            require(invocation.request.minApi == hostRuntimeApi) {
                "DEX compiler v1 must use the current host runtime API"
            }
            require(invocation.request.maxOutputBytes <= maximumInMemoryOutputBytes) {
                "Requested output exceeds the experimental host in-memory limit"
            }
            require(invocation.inputSnapshot.sizeBytes == invocation.request.programSizeBytes) {
                "Input snapshot size differs from the request"
            }
            require(invocation.inputSnapshot.sha256.equals(
                invocation.request.programSha256.toHexString(),
                ignoreCase = true,
            )) { "Input snapshot SHA-256 differs from the request" }
            val bundleSize = invocation.request.inputBundleSizeBytes
            val bundleSha256 = invocation.request.inputBundleSha256
            if (bundleSize == null || bundleSha256 == null) {
                require(invocation.inputSnapshot.inputIdentities.isEmpty()) {
                    "Raw-program snapshot unexpectedly declares bundled identities"
                }
                require(invocation.inputSnapshot.wireSizeBytes == invocation.inputSnapshot.sizeBytes)
                require(invocation.inputSnapshot.wireSha256 == invocation.inputSnapshot.sha256)
            } else {
                require(invocation.inputSnapshot.inputIdentities == invocation.request.inputIdentities) {
                    "Input snapshot identities differ from the request"
                }
                require(invocation.inputSnapshot.wireSizeBytes == bundleSize) {
                    "Input bundle snapshot size differs from the request"
                }
                require(invocation.inputSnapshot.wireSha256 == bundleSha256.toHexString()) {
                    "Input bundle snapshot SHA-256 differs from the request"
                }
            }
        }

        private fun semanticCacheKey(): String {
            val identity = pinnedIdentity ?: error("Pinned identity is missing")
            val info = compilerInfo ?: error("Compiler info is missing")
            val expected = capabilities ?: error("Compiler capabilities are missing")
            val mode = when (effectiveRequest.mode) {
                org.autojs.plugin.dexcompiler.api.DexCompilerMode.DEBUG -> DexCompilationMode.DEBUG
                org.autojs.plugin.dexcompiler.api.DexCompilerMode.RELEASE -> DexCompilationMode.RELEASE
            }
            return DexCompilerSemanticCacheKey.sha256(
                DexCompilerCacheSpec(
                    inputSha256 = effectiveRequest.inputSetFingerprint?.toHexString()
                        ?: effectiveRequest.programSha256.toHexString(),
                    canonicalizationPolicyVersion = effectiveRequest.canonicalizationPolicyVersion,
                    protocolMajor = effectiveRequest.protocolVersion.major,
                    protocolMinor = effectiveRequest.protocolVersion.minor,
                    compilerFamily = expected.compilerFamily.name.lowercase(),
                    compilerVersion = expected.compilerVersion,
                    minimumApi = effectiveRequest.minApi,
                    compilationMode = mode,
                    outputMode = DexOutputMode.DEX_INDEXED_ZIP,
                    runtimeLibraryFingerprint = expected.runtimeLibraryFingerprint.toHexString(),
                    desugaredLibraryConfigurationIdentity = null,
                    semanticOptions = emptyMap(),
                    providerProvenance = DexCompilerProviderProvenance(
                        packageName = identity.component.packageName,
                        componentClassName = identity.component.className,
                        providerId = info.providerId,
                        packageUid = identity.packageUid,
                        signerSha256 = identity.signerSha256,
                        versionCode = identity.versionCode,
                        lastUpdateTime = identity.lastUpdateTime,
                    ),
                ),
            )
        }

        private fun binderDied() {
            if (finished.get()) return
            useLocal(DexCompilerExperimentalFailure.BINDER_DIED, cancelRemote = true)
        }

        private fun timeout() {
            if (finished.get()) return
            lifecycle.requestCancel()
            sessionSlot.requestCancel()
            useLocal(DexCompilerExperimentalFailure.TIMEOUT)
        }

        fun cancelByHost() {
            if (finished.get() || !terminalClaimed.compareAndSet(false, true)) return
            check(fallback.cancel()) { "Cancellation terminal state was already claimed" }
            lifecycle.requestCancel()
            sessionSlot.requestCancel()
            finish(DexCompilerExperimentalOutcome.Cancelled, rollback = true)
        }

        fun requestHostCancellation() {
            if (claimAbortIngress(hostCancellationIngressClaimed)) enqueueUrgent(::cancelByHost)
        }

        private fun callbackViolation() {
            lifecycle.requestCancel()
            useLocal(DexCompilerExperimentalFailure.CALLBACK_VIOLATION, cancelRemote = true)
        }

        private fun transportViolation() {
            lifecycle.requestCancel()
            useLocal(DexCompilerExperimentalFailure.TRANSPORT_VIOLATION, cancelRemote = true)
        }

        fun useLocal(
            failure: DexCompilerExperimentalFailure,
            cancelRemote: Boolean = false,
            remoteFailure: DexCompilerExperimentalOutcome.RemoteFailure? = null,
        ) {
            if (finished.get() || !terminalClaimed.compareAndSet(false, true)) return
            useLocalClaimed(failure, cancelRemote, remoteFailure)
        }

        private fun useLocalClaimed(
            failure: DexCompilerExperimentalFailure,
            cancelRemote: Boolean,
            remoteFailure: DexCompilerExperimentalOutcome.RemoteFailure? = null,
        ) {
            if (cancelRemote) sessionSlot.requestCancel()
            val directive = checkNotNull(fallback.tryClaimLocalFallback()) {
                "Local fallback terminal state was already claimed"
            }
            finish(
                DexCompilerExperimentalOutcome.UseLocalCompiler(
                    failure = failure,
                    pluginWasDispatched = fallback.snapshot().pluginDispatched,
                    pluginOutputRollbackRequired = directive.requiresPluginOutputRollback,
                    remoteFailure = remoteFailure,
                ),
                rollback = true,
            )
        }

        private fun finish(outcome: DexCompilerExperimentalOutcome, rollback: Boolean) {
            if (!finished.compareAndSet(false, true)) return
            attempts -= this
            deadlineSlot.close()
            sessionSlot.close()
            programDescriptorSlot.close()
            outputDescriptorSlot.close()
            if (rollback) {
                publicationGate.abort()
                runCatching { invocation.outputTransaction.rollback() }
                publicationGate.rollbackFinished()
            }
            closeQuietly(invocation.outputTransaction)
            closeQuietly(invocation.inputSnapshot)
            bindingSlot.close()
            // Retain the single-attempt claim until every old resource is closed. Clear it before
            // notifying so a reentrant start from [onOutcome] can safely begin a new attempt.
            activeAttempt.compareAndSet(this, null)
            runCatching { onOutcome(outcome) }
        }

        fun workerRejected() {
            useLocal(DexCompilerExperimentalFailure.WORKER_REJECTED, cancelRemote = true)
        }

        fun enqueue(action: () -> Unit) {
            if (!lane.tryExecute(Runnable(action))) workerRejected()
        }

        fun enqueueUrgent(action: () -> Unit) {
            if (!lane.tryExecuteUrgent(Runnable(action))) workerRejected()
        }

        private fun enqueueTimeout() {
            if (!lane.tryExecuteUrgent(Runnable(::timeout))) timeout()
        }

        private fun signalCallbackViolation() {
            if (claimAbortIngress(callbackViolationIngressClaimed)) enqueueUrgent(::callbackViolation)
        }

        /**
         * Linearizes asynchronous abort ingress against the publication commit point. A signal
         * registered before publication claims the lock must win rollback even while verification
         * is occupying the serial lane. Signals registered after the terminal CAS are late.
         */
        private fun claimAbortIngress(claim: AtomicBoolean): Boolean = synchronized(terminalCommitLock) {
            claim.compareAndSet(false, true)
        }

        private fun tryClaimPublicationTerminal(): DexCompilerTerminalCommitDecision =
            synchronized(terminalCommitLock) {
                when {
                    hasPendingAbortIngress() -> DexCompilerTerminalCommitDecision.ABORT_PENDING
                    claimExpiredDeadlineLocked() -> DexCompilerTerminalCommitDecision.DEADLINE_EXPIRED
                    terminalClaimed.compareAndSet(false, true) -> DexCompilerTerminalCommitDecision.COMMITTED
                    else -> DexCompilerTerminalCommitDecision.ABORT_PENDING
                }
            }

        private fun tryCommitPluginDispatch(): DexCompilerTerminalCommitDecision =
            synchronized(terminalCommitLock) {
                when {
                    hasPendingAbortIngress() || terminalClaimed.get() ->
                        DexCompilerTerminalCommitDecision.ABORT_PENDING
                    claimExpiredDeadlineLocked() -> DexCompilerTerminalCommitDecision.DEADLINE_EXPIRED
                    fallback.tryBeginPluginDispatch() -> DexCompilerTerminalCommitDecision.COMMITTED
                    else -> DexCompilerTerminalCommitDecision.ABORT_PENDING
                }
            }

        private fun remainingDeadlineUnlessAbortPending(): Long? = synchronized(terminalCommitLock) {
            if (hasPendingAbortIngress()) return@synchronized null
            remainingDeadlineMillis().also { remainingMillis ->
                if (remainingMillis <= 0L) timeoutIngressClaimed.compareAndSet(false, true)
            }
        }

        private fun claimExpiredDeadlineLocked(): Boolean {
            if (remainingDeadlineMillis() > 0L) return false
            timeoutIngressClaimed.compareAndSet(false, true)
            return true
        }

        private fun remainingDeadlineMillis(): Long =
            saturatingRemaining(deadlineAtMillis, monotonicClock.elapsedRealtimeMillis())

        private fun hasPendingAbortIngress(): Boolean =
            hostCancellationIngressClaimed.get() ||
                timeoutIngressClaimed.get() ||
                bindingFailureIngressClaimed.get() ||
                binderDeathIngressClaimed.get() ||
                callbackViolationIngressClaimed.get() ||
                transportViolationIngressClaimed.get()

        private fun diagnosticsFitRequest(diagnostics: Collection<DexCompilerDiagnostic>): Boolean {
            var retainedBytes = 0L
            diagnostics.forEach { diagnostic ->
                retainedBytes += diagnostic.code.toByteArray(StandardCharsets.UTF_8).size
                retainedBytes += diagnostic.message.toByteArray(StandardCharsets.UTF_8).size
                retainedBytes += diagnostic.origin?.toByteArray(StandardCharsets.UTF_8)?.size ?: 0
                retainedBytes += diagnostic.entry?.toByteArray(StandardCharsets.UTF_8)?.size ?: 0
                diagnostic.position?.let { position ->
                    retainedBytes += listOf(
                        position.line,
                        position.column,
                        position.endLine,
                        position.endColumn,
                    ).count { it != null } * Int.SIZE_BYTES
                    retainedBytes += listOf(position.offset, position.endOffset)
                        .count { it != null } * Long.SIZE_BYTES
                }
                if (retainedBytes > effectiveRequest.diagnosticByteLimit) return false
            }
            return true
        }

        private fun <T> decodeOrReject(decoder: (ByteArray) -> T, encoded: ByteArray): T? = try {
            decoder(encoded)
        } catch (_: Throwable) {
            callbackViolation()
            null
        }
    }

    private data class Handshake(
        val info: DexCompilerInfo,
        val capabilities: DexCompilerCapabilities,
    )
}

private fun isMetadataSizeAllowed(bytes: ByteArray): Boolean =
    bytes.isNotEmpty() && bytes.size <= DexCompilerContract.MAX_METADATA_BYTES

private enum class DexCompilerTerminalCommitDecision {
    COMMITTED,
    ABORT_PENDING,
    DEADLINE_EXPIRED,
}

private fun saturatingAdd(left: Long, right: Long): Long = try {
    Math.addExact(left, right)
} catch (_: ArithmeticException) {
    if (right >= 0L) Long.MAX_VALUE else Long.MIN_VALUE
}

private fun saturatingRemaining(deadlineMillis: Long, nowMillis: Long): Long {
    if (deadlineMillis <= nowMillis) return 0L
    return try {
        Math.subtractExact(deadlineMillis, nowMillis)
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }
}

private fun requireMetadataSize(bytes: ByteArray) {
    require(isMetadataSizeAllowed(bytes)) { "Binder metadata exceeds the protocol wire limit" }
}

private val LOWERCASE_SHA256 = Regex("[0-9a-f]{64}")

private class CloseOnceSlot<T : Closeable> : Closeable {
    private var value: T? = null
    private var closed = false

    @Synchronized
    fun install(resource: T) {
        if (value != null) {
            closeQuietly(resource)
            throw IllegalStateException("Resource installed more than once")
        }
        if (closed) {
            closeQuietly(resource)
        } else {
            value = resource
        }
    }

    override fun close() {
        val resource = synchronized(this) {
            if (closed) return
            closed = true
            value.also { value = null }
        }
        closeQuietly(resource)
    }
}

private class RemoteSessionSlot : Closeable {
    private var value: DexCompilerRemoteSession? = null
    private var cancelRequested = false
    private var closed = false

    fun install(session: DexCompilerRemoteSession) {
        val action = synchronized(this) {
            if (value != null) return@synchronized InstallAction.REJECT
            if (closed) InstallAction.CLOSE
            else {
                value = session
                if (cancelRequested) InstallAction.CANCEL else InstallAction.NONE
            }
        }
        when (action) {
            InstallAction.REJECT -> {
                closeQuietly(session)
                throw IllegalStateException("Remote session installed more than once")
            }
            InstallAction.CLOSE -> closeQuietly(session)
            InstallAction.CANCEL -> runCatching { session.cancel() }
            InstallAction.NONE -> Unit
        }
    }

    fun requestCancel() {
        val session = synchronized(this) {
            if (cancelRequested || closed) return
            cancelRequested = true
            value
        }
        if (session != null) runCatching { session.cancel() }
    }

    override fun close() {
        val session = synchronized(this) {
            if (closed) return
            closed = true
            value.also { value = null }
        }
        closeQuietly(session)
    }

    private enum class InstallAction {
        REJECT,
        CLOSE,
        CANCEL,
        NONE,
    }
}

private fun closeQuietly(closeable: Closeable?) {
    if (closeable != null) runCatching { closeable.close() }
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
