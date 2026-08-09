package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiProviderInfo
import org.autojs.plugin.ai.text.api.AiTextCapabilities
import org.autojs.plugin.ai.text.api.AiTextCodec
import org.autojs.plugin.ai.text.api.AiTextProtocol
import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Opaque, transport-owned connection that has not yet passed the Binder descriptor check. */
internal interface AiTextProviderRawConnection

/** Descriptor-verified, metadata-only view. It deliberately exposes no model or session methods. */
internal interface AiTextProviderMetadataRpc {
    fun getProviderInfoEnvelope(): ByteArray?

    fun getCapabilitiesEnvelope(): ByteArray?
}

internal interface AiTextProviderBinding : Closeable

internal interface AiTextProviderBindingCallback {
    fun onConnected(connection: AiTextProviderRawConnection)

    fun onBindingFailure(failure: AiTextProviderHandshakeFailure)

    fun onBinderDeath()
}

/**
 * Platform boundary for an explicitly invoked, metadata-only provider handshake.
 *
 * [openMetadataRpc] must validate the exact remote interface descriptor before constructing an
 * AIDL proxy. All three methods may block and are invoked away from Android's main thread.
 */
internal interface AiTextProviderHandshakeTransport {
    fun inspectExact(component: AiTextProviderComponent): AiTextProviderDiscoverySourceResult

    fun bindExact(
        snapshot: AiTextProviderPackageSnapshot,
        callback: AiTextProviderBindingCallback,
    ): AiTextProviderBinding

    fun openMetadataRpc(connection: AiTextProviderRawConnection): AiTextProviderMetadataRpc
}

internal fun interface AiTextProviderHandshakeDeadlineScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): Closeable
}

internal fun interface AiTextProviderHandshakeClock {
    fun elapsedRealtimeMillis(): Long
}

internal data class AiTextProviderHandshakeOptions(
    val enabled: Boolean = false,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    init {
        require(timeoutMillis > 0L) { "Handshake timeout must be positive" }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
    }
}

internal data class AiTextProviderHandshakeRequest(
    val snapshot: AiTextProviderPackageSnapshot,
    val environment: AiTextProviderEnvironment,
    val hostCapabilities: AiTextCapabilities,
    val requirement: AiTextCapabilityRequirement = AiTextCapabilityRequirement(),
    val options: AiTextProviderHandshakeOptions = AiTextProviderHandshakeOptions(),
)

internal enum class AiTextProviderHandshakeFailure {
    PRE_BIND_REJECTED,
    IDENTITY_DRIFT,
    BIND_REJECTED,
    BIND_SECURITY,
    BIND_TIMEOUT,
    UNEXPECTED_COMPONENT,
    DUPLICATE_CONNECTION,
    NULL_BINDING,
    DISCONNECTED,
    BINDER_DIED,
    DESCRIPTOR_MISMATCH,
    REMOTE_SECURITY,
    REMOTE_FAILURE,
    PROVIDER_INFO_MISSING,
    PROVIDER_INFO_TOO_LARGE,
    PROVIDER_INFO_INVALID,
    CAPABILITIES_MISSING,
    CAPABILITIES_TOO_LARGE,
    CAPABILITIES_INVALID,
    METADATA_TIMEOUT,
    CANDIDATE_REJECTED,
    EXECUTOR_REJECTED,
    INTERNAL_FAILURE,
}

internal sealed class AiTextProviderHandshakeOutcome {
    object Disabled : AiTextProviderHandshakeOutcome()
    object Busy : AiTextProviderHandshakeOutcome()
    object Fused : AiTextProviderHandshakeOutcome()
    object Cancelled : AiTextProviderHandshakeOutcome()

    data class Selected(
        val pinned: AiTextProviderPinnedIdentity,
    ) : AiTextProviderHandshakeOutcome()

    data class Rejected(
        val failure: AiTextProviderHandshakeFailure,
        val candidateRejection: AiTextProviderCandidateRejection? = null,
    ) : AiTextProviderHandshakeOutcome()
}

/**
 * Process-wide admission and fuse state for metadata handshakes.
 *
 * A timed-out synchronous Binder transaction cannot be killed safely. Its lease remains active
 * until the blocking job actually returns, while its component remains fused for this process.
 */
internal class AiTextProviderHandshakeGate {
    private val lock = Any()
    private var active = false
    private val fused = HashSet<AiTextProviderComponent>()

    fun tryAcquire(component: AiTextProviderComponent): AiTextProviderHandshakeAdmission =
        synchronized(lock) {
            when {
                component in fused -> AiTextProviderHandshakeAdmission.FUSED
                active -> AiTextProviderHandshakeAdmission.BUSY
                else -> {
                    active = true
                    AiTextProviderHandshakeAdmission.ACQUIRED
                }
            }
        }

    fun release() {
        synchronized(lock) {
            check(active) { "Handshake gate was not acquired" }
            active = false
        }
    }

    fun fuse(component: AiTextProviderComponent) {
        synchronized(lock) { fused += component }
    }
}

internal enum class AiTextProviderHandshakeAdmission {
    ACQUIRED,
    BUSY,
    FUSED,
}

/**
 * Default-off metadata-only Binder handshake. This class has no production call site.
 *
 * A successful attempt reaches three exact-component reinspection boundaries: before binding,
 * after connecting, and after both bounded metadata reads. Earlier failures stop at the last
 * boundary they reached. This class never lists models, opens a session, or retains the Binder
 * after returning a pinned selection result.
 *
 * Ordinary terminal outcomes release the process gate before invoking the callback. Cancellation
 * and deadline outcomes may be reported while a platform operation is still unwinding, so their
 * callbacks must remain non-blocking and a reentrant attempt may still observe
 * [AiTextProviderHandshakeOutcome.Busy].
 */
internal class AiTextProviderHandshakeCoordinator(
    private val transport: AiTextProviderHandshakeTransport,
    workerExecutor: Executor,
    private val blockingExecutor: Executor,
    private val deadlineScheduler: AiTextProviderHandshakeDeadlineScheduler,
    private val gate: AiTextProviderHandshakeGate = PROCESS_GATE,
    private val clock: AiTextProviderHandshakeClock = SYSTEM_CLOCK,
) {
    private val lane = HandshakeSerialExecutor(workerExecutor)

    fun start(
        request: AiTextProviderHandshakeRequest,
        onOutcome: (AiTextProviderHandshakeOutcome) -> Unit,
    ): Closeable {
        if (!request.options.enabled) {
            deliver(onOutcome, AiTextProviderHandshakeOutcome.Disabled)
            return Closeable {}
        }
        when (gate.tryAcquire(request.snapshot.component)) {
            AiTextProviderHandshakeAdmission.FUSED -> {
                deliver(onOutcome, AiTextProviderHandshakeOutcome.Fused)
                return Closeable {}
            }
            AiTextProviderHandshakeAdmission.BUSY -> {
                deliver(onOutcome, AiTextProviderHandshakeOutcome.Busy)
                return Closeable {}
            }
            AiTextProviderHandshakeAdmission.ACQUIRED -> Unit
        }

        val attempt = try {
            Attempt(request, onOutcome)
        } catch (_: Exception) {
            gate.release()
            deliver(
                onOutcome,
                AiTextProviderHandshakeOutcome.Rejected(
                    AiTextProviderHandshakeFailure.INTERNAL_FAILURE,
                ),
            )
            return Closeable {}
        }
        attempt.armDeadlineAndBegin()
        return Closeable { attempt.cancel() }
    }

    private inner class Attempt(
        private val request: AiTextProviderHandshakeRequest,
        private val onOutcome: (AiTextProviderHandshakeOutcome) -> Unit,
    ) {
        private val lifecycleLock = Any()
        @Volatile
        private var terminal = false
        private var terminalSettled = false
        private var terminalWasCancellation = false
        private var operationsInFlight = 1
        private var gateReleased = false
        private var pendingOutcome: AiTextProviderHandshakeOutcome? = null
        private var outcomeDelivered = false
        private val connectionClaimed = AtomicBoolean(false)
        private val binderRpcInFlight = AtomicBoolean(false)
        private val bindingSlot = HandshakeCloseSlot<AiTextProviderBinding>()
        private val deadlineSlot = HandshakeCloseSlot<Closeable>()
        private val phase = AtomicReference(Phase.PRE_BIND)
        private val deadlineAtMillis = saturatingAdd(
            clock.elapsedRealtimeMillis(),
            request.options.timeoutMillis,
        )

        fun armDeadlineAndBegin() {
            try {
                val remainingMillis = remainingMillis()
                if (remainingMillis <= 0L) {
                    timeout()
                } else {
                    deadlineSlot.install(
                        deadlineScheduler.schedule(remainingMillis, ::timeout),
                    )
                    if (deadlineExpired()) timeout()
                }
                enqueue(::begin)
            } catch (_: Exception) {
                finish(
                    AiTextProviderHandshakeOutcome.Rejected(
                        AiTextProviderHandshakeFailure.EXECUTOR_REJECTED,
                    ),
                )
            } finally {
                operationDone()
            }
        }

        fun begin() {
            if (stopForDeadlineOrTerminal()) return
            val localRejection = preBindRejection(request.snapshot)
            if (localRejection != null) {
                return finish(
                    AiTextProviderHandshakeOutcome.Rejected(
                        failure = AiTextProviderHandshakeFailure.PRE_BIND_REJECTED,
                        candidateRejection = localRejection,
                    ),
                )
            }
            if (!reinspectMatches()) {
                return finish(
                    AiTextProviderHandshakeOutcome.Rejected(
                        AiTextProviderHandshakeFailure.IDENTITY_DRIFT,
                    ),
                )
            }
            if (stopForDeadlineOrTerminal()) return
            phase.set(Phase.BINDING)
            try {
                val binding = transport.bindExact(
                    request.snapshot,
                    object : AiTextProviderBindingCallback {
                        override fun onConnected(connection: AiTextProviderRawConnection) {
                            if (!connectionClaimed.compareAndSet(false, true)) {
                                finish(
                                    AiTextProviderHandshakeOutcome.Rejected(
                                        AiTextProviderHandshakeFailure.DUPLICATE_CONNECTION,
                                    ),
                                )
                                return
                            }
                            enqueue { connected(connection) }
                        }

                        override fun onBindingFailure(failure: AiTextProviderHandshakeFailure) {
                            finish(AiTextProviderHandshakeOutcome.Rejected(failure))
                        }

                        override fun onBinderDeath() {
                            finish(
                                AiTextProviderHandshakeOutcome.Rejected(
                                    AiTextProviderHandshakeFailure.BINDER_DIED,
                                ),
                            )
                        }
                    },
                )
                bindingSlot.install(binding)
            } catch (_: SecurityException) {
                finish(
                    AiTextProviderHandshakeOutcome.Rejected(
                        AiTextProviderHandshakeFailure.BIND_SECURITY,
                    ),
                )
            } catch (_: Exception) {
                finish(
                    AiTextProviderHandshakeOutcome.Rejected(
                        AiTextProviderHandshakeFailure.BIND_REJECTED,
                    ),
                )
            }
        }

        private fun connected(connection: AiTextProviderRawConnection) {
            if (stopForDeadlineOrTerminal()) return
            phase.set(Phase.POST_CONNECT_INSPECTION)
            if (!reinspectMatches()) {
                return finish(
                    AiTextProviderHandshakeOutcome.Rejected(
                        AiTextProviderHandshakeFailure.IDENTITY_DRIFT,
                    ),
                )
            }
            beginMetadataRead(connection)
        }

        private fun beginMetadataRead(connection: AiTextProviderRawConnection) {
            if (stopForDeadlineOrTerminal()) return
            phase.set(Phase.METADATA)
            try {
                blockingExecutor.execute {
                    if (!beginOperation()) return@execute
                    try {
                        val result = readMetadata(connection)
                        if (!isTerminal()) enqueue { acceptMetadata(result) }
                    } finally {
                        binderRpcInFlight.set(false)
                        operationDone()
                    }
                }
            } catch (_: Exception) {
                finish(
                    AiTextProviderHandshakeOutcome.Rejected(
                        AiTextProviderHandshakeFailure.EXECUTOR_REJECTED,
                    ),
                )
            }
        }

        private fun readMetadata(
            connection: AiTextProviderRawConnection,
        ): MetadataReadResult {
            if (stopForDeadlineOrTerminal()) return MetadataReadResult.Abandoned
            if (!beginBinderRpc()) return MetadataReadResult.Abandoned
            val rpc = try {
                transport.openMetadataRpc(connection)
            } catch (_: AiTextProviderDescriptorMismatchException) {
                return MetadataReadResult.Failed(AiTextProviderHandshakeFailure.DESCRIPTOR_MISMATCH)
            } catch (_: SecurityException) {
                return MetadataReadResult.Failed(AiTextProviderHandshakeFailure.REMOTE_SECURITY)
            } catch (_: Exception) {
                return MetadataReadResult.Failed(AiTextProviderHandshakeFailure.REMOTE_FAILURE)
            } finally {
                endBinderRpc()
            }
            if (stopForDeadlineOrTerminal()) return MetadataReadResult.Abandoned

            val providerInfo = when (val read = readProviderInfo(rpc)) {
                null -> return MetadataReadResult.Abandoned
                is MetadataValue.Failed -> return MetadataReadResult.Failed(read.failure)
                is MetadataValue.Value -> read.value
            }
            if (stopForDeadlineOrTerminal()) return MetadataReadResult.Abandoned
            val capabilities = when (val read = readCapabilities(rpc)) {
                null -> return MetadataReadResult.Abandoned
                is MetadataValue.Failed -> return MetadataReadResult.Failed(read.failure)
                is MetadataValue.Value -> read.value
            }
            return if (stopForDeadlineOrTerminal()) {
                MetadataReadResult.Abandoned
            } else {
                MetadataReadResult.Complete(providerInfo, capabilities)
            }
        }

        private fun readProviderInfo(rpc: AiTextProviderMetadataRpc): MetadataValue<AiProviderInfo>? {
            if (!beginBinderRpc()) return null
            val envelope = try {
                rpc.getProviderInfoEnvelope()
            } catch (_: SecurityException) {
                return MetadataValue.Failed(AiTextProviderHandshakeFailure.REMOTE_SECURITY)
            } catch (_: Exception) {
                return MetadataValue.Failed(AiTextProviderHandshakeFailure.REMOTE_FAILURE)
            } finally {
                endBinderRpc()
            } ?: return MetadataValue.Failed(AiTextProviderHandshakeFailure.PROVIDER_INFO_MISSING)
            if (envelope.isEmpty()) {
                return MetadataValue.Failed(AiTextProviderHandshakeFailure.PROVIDER_INFO_MISSING)
            }
            if (envelope.size > AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES) {
                return MetadataValue.Failed(AiTextProviderHandshakeFailure.PROVIDER_INFO_TOO_LARGE)
            }
            val decoded = runCatching { AiCommonCodec.decodeProviderInfo(envelope.copyOf()) }.getOrNull()
                ?: return MetadataValue.Failed(AiTextProviderHandshakeFailure.PROVIDER_INFO_INVALID)
            return MetadataValue.Value(decoded)
        }

        private fun readCapabilities(
            rpc: AiTextProviderMetadataRpc,
        ): MetadataValue<AiTextCapabilities>? {
            if (!beginBinderRpc()) return null
            val envelope = try {
                rpc.getCapabilitiesEnvelope()
            } catch (_: SecurityException) {
                return MetadataValue.Failed(AiTextProviderHandshakeFailure.REMOTE_SECURITY)
            } catch (_: Exception) {
                return MetadataValue.Failed(AiTextProviderHandshakeFailure.REMOTE_FAILURE)
            } finally {
                endBinderRpc()
            } ?: return MetadataValue.Failed(AiTextProviderHandshakeFailure.CAPABILITIES_MISSING)
            if (envelope.isEmpty()) {
                return MetadataValue.Failed(AiTextProviderHandshakeFailure.CAPABILITIES_MISSING)
            }
            if (envelope.size > AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES) {
                return MetadataValue.Failed(AiTextProviderHandshakeFailure.CAPABILITIES_TOO_LARGE)
            }
            val decoded = runCatching { AiTextCodec.decodeCapabilities(envelope.copyOf()) }.getOrNull()
                ?: return MetadataValue.Failed(AiTextProviderHandshakeFailure.CAPABILITIES_INVALID)
            return MetadataValue.Value(decoded)
        }

        private fun acceptMetadata(result: MetadataReadResult) {
            if (stopForDeadlineOrTerminal()) return
            when (result) {
                MetadataReadResult.Abandoned -> Unit
                is MetadataReadResult.Failed -> finish(
                    AiTextProviderHandshakeOutcome.Rejected(result.failure),
                )
                is MetadataReadResult.Complete -> finishSelection(result)
            }
        }

        private fun finishSelection(metadata: MetadataReadResult.Complete) {
            if (stopForDeadlineOrTerminal()) return
            phase.set(Phase.FINAL_INSPECTION)
            val current = reinspectionSnapshot() ?: return finish(
                AiTextProviderHandshakeOutcome.Rejected(
                    AiTextProviderHandshakeFailure.IDENTITY_DRIFT,
                ),
            )
            if (!request.snapshot.samePackageIdentityAs(current)) {
                return finish(
                    AiTextProviderHandshakeOutcome.Rejected(
                        AiTextProviderHandshakeFailure.IDENTITY_DRIFT,
                    ),
                )
            }
            if (stopForDeadlineOrTerminal()) return
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
                providerInfo = metadata.providerInfo,
                capabilities = metadata.capabilities,
            )
            val selected = AiTextProviderSelectionPolicy.select(
                candidates = listOf(candidate),
                environment = request.environment,
                hostCapabilities = request.hostCapabilities,
                requirement = request.requirement,
                options = AiTextProviderSelectionOptions(
                    enabled = true,
                    preferredComponent = current.component,
                ),
            )
            when (selected) {
                is AiTextProviderSelectionResult.Selected -> {
                    if (stopForDeadlineOrTerminal()) return
                    finish(AiTextProviderHandshakeOutcome.Selected(selected.pinned))
                }
                is AiTextProviderSelectionResult.NoMatch -> finish(
                    AiTextProviderHandshakeOutcome.Rejected(
                        failure = AiTextProviderHandshakeFailure.CANDIDATE_REJECTED,
                        candidateRejection = selected.evaluations.singleOrNull()?.rejection,
                    ),
                )
                AiTextProviderSelectionResult.Disabled,
                is AiTextProviderSelectionResult.Ambiguous,
                -> finish(
                    AiTextProviderHandshakeOutcome.Rejected(
                        AiTextProviderHandshakeFailure.CANDIDATE_REJECTED,
                    ),
                )
            }
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

        private fun timeout() {
            if (!deadlineExpired()) return
            val failure = if (phase.get() == Phase.METADATA || phase.get() == Phase.FINAL_INSPECTION) {
                AiTextProviderHandshakeFailure.METADATA_TIMEOUT
            } else {
                AiTextProviderHandshakeFailure.BIND_TIMEOUT
            }
            val decision = claimTerminal(AiTextProviderHandshakeOutcome.Rejected(failure))
            if (decision == null) {
                synchronized(lifecycleLock) {
                    if (terminalWasCancellation && binderRpcInFlight.get()) {
                        gate.fuse(request.snapshot.component)
                    }
                }
                deadlineSlot.close()
                return
            }
            if (decision.fuseComponent) gate.fuse(request.snapshot.component)
            completeClaimed(
                outcome = decision.outcome,
                deliverBeforeIdle = true,
                retainDeadline = false,
            )
        }

        fun cancel() {
            finish(AiTextProviderHandshakeOutcome.Cancelled)
        }

        fun enqueue(action: () -> Unit) {
            if (isTerminal()) return
            lane.execute(
                command = Runnable {
                    if (!beginOperation()) return@Runnable
                    try {
                        if (!stopForDeadlineOrTerminal()) action()
                    } catch (_: Throwable) {
                        finish(
                            AiTextProviderHandshakeOutcome.Rejected(
                                AiTextProviderHandshakeFailure.INTERNAL_FAILURE,
                            ),
                        )
                    } finally {
                        operationDone()
                    }
                },
                onRejected = {
                    finish(
                        AiTextProviderHandshakeOutcome.Rejected(
                            AiTextProviderHandshakeFailure.EXECUTOR_REJECTED,
                        ),
                    )
                },
            )
        }

        fun finish(outcome: AiTextProviderHandshakeOutcome) {
            val decision = claimTerminal(outcome) ?: return
            if (decision.fuseComponent) gate.fuse(request.snapshot.component)
            completeClaimed(
                outcome = decision.outcome,
                deliverBeforeIdle = decision.timedOut || decision.outcome === AiTextProviderHandshakeOutcome.Cancelled,
                retainDeadline = decision.retainDeadline,
            )
        }

        private fun completeClaimed(
            outcome: AiTextProviderHandshakeOutcome,
            deliverBeforeIdle: Boolean = false,
            retainDeadline: Boolean = false,
        ) {
            if (!retainDeadline) deadlineSlot.close()
            bindingSlot.close()
            synchronized(lifecycleLock) {
                check(pendingOutcome == null) { "Handshake outcome was stored more than once" }
                pendingOutcome = outcome
                terminalSettled = true
            }
            completeIdleIfReady()
            if (deliverBeforeIdle) deliverPendingOutcome()
        }

        private fun claimTerminal(
            requested: AiTextProviderHandshakeOutcome,
        ): TerminalDecision? = synchronized(lifecycleLock) {
            if (terminal) return@synchronized null
            val timedOut = deadlineExpired()
            val outcome = if (timedOut) {
                AiTextProviderHandshakeOutcome.Rejected(timeoutFailure())
            } else {
                requested
            }
            terminal = true
            terminalWasCancellation = outcome === AiTextProviderHandshakeOutcome.Cancelled
            val binderActive = binderRpcInFlight.get()
            TerminalDecision(
                outcome = outcome,
                timedOut = timedOut,
                fuseComponent = timedOut && binderActive,
                retainDeadline = outcome === AiTextProviderHandshakeOutcome.Cancelled && binderActive,
            )
        }

        private fun isTerminal(): Boolean = terminal

        private fun beginOperation(): Boolean = synchronized(lifecycleLock) {
            if (terminal) return@synchronized false
            operationsInFlight += 1
            true
        }

        private fun beginBinderRpc(): Boolean {
            var expired = false
            synchronized(lifecycleLock) {
                if (terminal) return false
                if (deadlineExpired()) {
                    expired = true
                } else {
                    check(!binderRpcInFlight.get()) { "Metadata Binder RPC calls must be serial" }
                    binderRpcInFlight.set(true)
                    return true
                }
            }
            if (expired) timeout()
            return false
        }

        private fun endBinderRpc() {
            // Arbitrate a late scheduler while the call is still marked active. Otherwise a Binder
            // return just after the absolute deadline could clear the evidence needed to fuse it.
            if (deadlineExpired()) timeout()
            binderRpcInFlight.set(false)
        }

        private fun operationDone() {
            synchronized(lifecycleLock) {
                check(operationsInFlight > 0) { "Handshake operation count underflow" }
                operationsInFlight -= 1
            }
            completeIdleIfReady()
        }

        private fun completeIdleIfReady() {
            var release = false
            var closeDeadline = false
            var outcome: AiTextProviderHandshakeOutcome? = null
            synchronized(lifecycleLock) {
                val idleAndSettled = terminal && terminalSettled && operationsInFlight == 0
                if (idleAndSettled && !gateReleased) {
                    gateReleased = true
                    release = true
                }
                if (idleAndSettled && !outcomeDelivered) {
                    outcome = pendingOutcome
                    if (outcome != null) outcomeDelivered = true
                }
                closeDeadline = idleAndSettled
            }
            if (release) gate.release()
            if (closeDeadline) deadlineSlot.close()
            outcome?.let { deliver(onOutcome, it) }
        }

        private fun deliverPendingOutcome() {
            val outcome = synchronized(lifecycleLock) {
                if (outcomeDelivered) return
                pendingOutcome?.also { outcomeDelivered = true }
            }
            if (outcome != null) deliver(onOutcome, outcome)
        }

        private fun stopForDeadlineOrTerminal(): Boolean {
            if (isTerminal()) return true
            if (!deadlineExpired()) return false
            timeout()
            return true
        }

        private fun deadlineExpired(): Boolean = remainingMillis() <= 0L

        private fun timeoutFailure(): AiTextProviderHandshakeFailure =
            if (phase.get() == Phase.METADATA || phase.get() == Phase.FINAL_INSPECTION) {
                AiTextProviderHandshakeFailure.METADATA_TIMEOUT
            } else {
                AiTextProviderHandshakeFailure.BIND_TIMEOUT
            }

        private fun remainingMillis(): Long = deadlineAtMillis - clock.elapsedRealtimeMillis()

        private fun saturatingAdd(left: Long, right: Long): Long =
            if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    }

    private data class TerminalDecision(
        val outcome: AiTextProviderHandshakeOutcome,
        val timedOut: Boolean,
        val fuseComponent: Boolean,
        val retainDeadline: Boolean,
    )

    private fun preBindRejection(
        snapshot: AiTextProviderPackageSnapshot,
    ): AiTextProviderCandidateRejection? = when {
        snapshot.declaredAction != AiTextProtocol.ACTION_TEXT_PROVIDER ->
            AiTextProviderCandidateRejection.WRONG_ACTION
        !snapshot.exported -> AiTextProviderCandidateRejection.NOT_EXPORTED
        !snapshot.applicationEnabled || !snapshot.serviceEnabled -> AiTextProviderCandidateRejection.DISABLED
        snapshot.requiredPermission != AiTextProtocol.PLUGIN_PERMISSION ->
            AiTextProviderCandidateRejection.WRONG_PERMISSION
        !snapshot.officialSignerMatch && !snapshot.sameSignerAsHost ->
            AiTextProviderCandidateRejection.TRUST_REQUIRED
        else -> null
    }

    private sealed class MetadataReadResult {
        object Abandoned : MetadataReadResult()
        data class Failed(val failure: AiTextProviderHandshakeFailure) : MetadataReadResult()
        data class Complete(
            val providerInfo: AiProviderInfo,
            val capabilities: AiTextCapabilities,
        ) : MetadataReadResult()
    }

    private sealed class MetadataValue<out T> {
        data class Value<T>(val value: T) : MetadataValue<T>()
        data class Failed(val failure: AiTextProviderHandshakeFailure) : MetadataValue<Nothing>()
    }

    private enum class Phase {
        PRE_BIND,
        BINDING,
        POST_CONNECT_INSPECTION,
        METADATA,
        FINAL_INSPECTION,
    }

    private companion object {
        val PROCESS_GATE = AiTextProviderHandshakeGate()
        val SYSTEM_CLOCK = AiTextProviderHandshakeClock { System.nanoTime() / 1_000_000L }

        fun deliver(
            callback: (AiTextProviderHandshakeOutcome) -> Unit,
            outcome: AiTextProviderHandshakeOutcome,
        ) {
            runCatching { callback(outcome) }
        }
    }
}

/** Marker used by transports without exposing remote descriptor text to callers or logs. */
internal class AiTextProviderDescriptorMismatchException : SecurityException()

private class HandshakeCloseSlot<T : Closeable> : Closeable {
    private val lock = Any()
    private var value: T? = null
    private var closed = false

    fun install(candidate: T) {
        val closeNow = synchronized(lock) {
            check(value == null) { "Handshake resource was installed more than once" }
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

private class HandshakeSerialExecutor(
    private val delegate: Executor,
) {
    private data class Entry(
        val command: Runnable,
        val onRejected: () -> Unit,
    )

    private val lock = Any()
    private val queue = ArrayDeque<Entry>()
    private var draining = false

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
        } catch (_: Exception) {
            val rejected = synchronized(lock) {
                val values = queue.toList()
                queue.clear()
                draining = false
                values
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
