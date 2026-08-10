package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiProviderInfo
import org.autojs.plugin.ai.text.api.AiModelPageTokenLedger
import org.autojs.plugin.ai.text.api.AiTextCapabilities
import org.autojs.plugin.ai.text.api.AiTextCodec
import org.autojs.plugin.ai.text.api.AiTextLimits
import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Opaque connection owned by a future platform transport until its binding is closed. */
internal interface AiTextProviderModelListingConnection

internal interface AiTextProviderModelListingBinding : Closeable

internal interface AiTextProviderModelListingBindingCallback {
    fun onConnected(connection: AiTextProviderModelListingConnection)

    fun onBindingFailure(failure: AiTextProviderModelListingRunFailure)

    fun onBinderDeath()
}

/**
 * Callback ingress for one prepared request sequence.
 *
 * An Android adapter must capture Binder.getCallingUid(), then synchronously forward the envelope
 * without copying it, clearing Binder identity, or changing threads. Before this callback returns,
 * the coordinator validates UID and size, reserves a bounded flood slot, and performs the only
 * defensive envelope copy retained for later transcript admission.
 */
internal interface AiTextProviderModelListingCallbackIngress {
    fun onPage(callingUid: Int, envelope: ByteArray?)

    fun onFailed(callingUid: Int, envelope: ByteArray?)
}

/**
 * One callback registration prepared for exactly one model-list Dispatch.
 *
 * [dispatch] may perform a Binder transaction and must not run on Android's main thread. Closing
 * the call must detach its local callback sink, even though a remote process may retain the Binder
 * callback object. It must not open a session or accept file descriptors.
 */
internal interface AiTextProviderModelListingPreparedCall : Closeable {
    fun dispatch(requestEnvelope: ByteArray)
}

/** Descriptor-verified view of the same Binder used for metadata revalidation and model listing. */
internal interface AiTextProviderModelListingRpc {
    fun getProviderInfoEnvelope(): ByteArray?

    fun getCapabilitiesEnvelope(): ByteArray?

    fun prepareCall(
        sequence: Long,
        callback: AiTextProviderModelListingCallbackIngress,
    ): AiTextProviderModelListingPreparedCall
}

/**
 * Platform boundary for an explicitly invoked model-list run.
 *
 * [openRpc] must validate the exact remote interface descriptor before constructing an AIDL proxy.
 * A future Android implementation must bind an actionless Intent to the exact component and keep
 * the returned binding alive until the coordinator closes it.
 */
internal interface AiTextProviderModelListingTransport {
    fun inspectExact(component: AiTextProviderComponent): AiTextProviderDiscoverySourceResult

    fun bindExact(
        snapshot: AiTextProviderPackageSnapshot,
        callback: AiTextProviderModelListingBindingCallback,
    ): AiTextProviderModelListingBinding

    fun openRpc(connection: AiTextProviderModelListingConnection): AiTextProviderModelListingRpc
}

/** Marker used without retaining or logging an untrusted remote descriptor string. */
internal class AiTextProviderModelListingDescriptorMismatchException : SecurityException()

internal fun interface AiTextProviderModelListingDeadlineScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): Closeable
}

internal fun interface AiTextProviderModelListingClock {
    fun elapsedRealtimeMillis(): Long
}

internal data class AiTextProviderModelListingRunOptions(
    val enabled: Boolean = false,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    init {
        require(pageSize in 1..AiTextLimits.MAX_MODELS_PER_PAGE) {
            "Invalid AI text model-list page size"
        }
        require(timeoutMillis > 0L) { "Model-list timeout must be positive" }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 50
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
    }
}

internal data class AiTextProviderModelListingRunRequest(
    val snapshot: AiTextProviderPackageSnapshot,
    val pinnedProvider: AiTextProviderPinnedIdentity,
    val environment: AiTextProviderEnvironment,
    val hostCapabilities: AiTextCapabilities,
    val requirement: AiTextCapabilityRequirement = AiTextCapabilityRequirement(),
    val options: AiTextProviderModelListingRunOptions = AiTextProviderModelListingRunOptions(),
)

internal enum class AiTextProviderModelListingRunFailure {
    PINNED_IDENTITY_MISMATCH,
    IDENTITY_DRIFT,
    BIND_REJECTED,
    BIND_SECURITY,
    UNEXPECTED_COMPONENT,
    DUPLICATE_CONNECTION,
    NULL_BINDING,
    DISCONNECTED,
    DESCRIPTOR_MISMATCH,
    REMOTE_SECURITY,
    REMOTE_FAILURE,
    PROVIDER_INFO_MISSING,
    PROVIDER_INFO_TOO_LARGE,
    PROVIDER_INFO_INVALID,
    CAPABILITIES_MISSING,
    CAPABILITIES_TOO_LARGE,
    CAPABILITIES_INVALID,
    PINNED_METADATA_MISMATCH,
    CALLBACK_BEFORE_DISPATCH,
    CALLBACK_FLOOD,
    TRANSCRIPT_REJECTED,
    EXECUTOR_REJECTED,
    INTERNAL_FAILURE,
}

internal sealed class AiTextProviderModelListingRunOutcome {
    object Disabled : AiTextProviderModelListingRunOutcome()
    object Busy : AiTextProviderModelListingRunOutcome()
    object Fused : AiTextProviderModelListingRunOutcome()
    object Cancelled : AiTextProviderModelListingRunOutcome()
    object TimedOut : AiTextProviderModelListingRunOutcome()
    object BinderDied : AiTextProviderModelListingRunOutcome()

    data class Completed(
        val catalog: AiTextProviderModelCatalog,
    ) : AiTextProviderModelListingRunOutcome()

    class ProviderFailed(
        val error: AiTextProviderModelListingErrorSummary,
    ) : AiTextProviderModelListingRunOutcome() {
        override fun toString(): String =
            "ProviderFailed(code=${error.code}, retryDisposition=${error.retryDisposition}, " +
                "providerCodePresent=${error.providerCodePresent})"
    }

    data class Rejected(
        val failure: AiTextProviderModelListingRunFailure,
        val transcriptFailure: AiTextProviderModelListingFailure? = null,
    ) : AiTextProviderModelListingRunOutcome()
}

/**
 * Explicit process-lifetime ownership for model-list attempts.
 *
 * A future host owner must share one instance across transcripts. Recreating it per attempt would
 * lose continuation-token replay evidence and defeat single-flight/fuse admission. Token evidence
 * is isolated by stable pinned transport identity fields: component, UID, APK version/update,
 * complete signer set, provider ID, and protocol version. The identity table and process-wide
 * token-entry budget fail closed instead of evicting replay evidence. The owner does not enable
 * discovery or create a production call site by itself.
 */
internal class AiTextProviderModelListingProcessOwner(
    private val maximumProviderIdentities: Int = DEFAULT_MAXIMUM_PROVIDER_IDENTITIES,
    private val maximumConsumedTokensPerProvider: Int =
        DEFAULT_MAXIMUM_CONSUMED_TOKENS_PER_PROVIDER,
) {
    private val lock = Any()
    private var active = false
    private val fusedComponents = HashSet<AiTextProviderComponent>()
    private val tokenLedgers = LinkedHashMap<ProviderTokenLedgerIdentity, AiModelPageTokenLedger>()

    init {
        require(maximumProviderIdentities in 1..MAXIMUM_PROVIDER_IDENTITIES_LIMIT) {
            "Invalid model-list provider identity capacity"
        }
        require(
            maximumConsumedTokensPerProvider in
                1..AiTextLimits.MAX_MODEL_PAGE_TOKEN_LEDGER_ENTRIES,
        ) { "Invalid per-provider model page-token capacity" }
        require(
            maximumProviderIdentities.toLong() * maximumConsumedTokensPerProvider <=
                MAXIMUM_PROCESS_TOKEN_ENTRIES,
        ) { "Model page-token process budget is too large" }
    }

    internal fun tryAcquire(
        component: AiTextProviderComponent,
    ): AiTextProviderModelListingAdmission = synchronized(lock) {
        when {
            component in fusedComponents -> AiTextProviderModelListingAdmission.FUSED
            active -> AiTextProviderModelListingAdmission.BUSY
            else -> {
                active = true
                AiTextProviderModelListingAdmission.ACQUIRED
            }
        }
    }

    internal fun fuse(component: AiTextProviderComponent) {
        synchronized(lock) { fusedComponents += component }
    }

    internal fun tokenLedgerFor(
        pinned: AiTextProviderPinnedIdentity,
    ): AiModelPageTokenLedger? = synchronized(lock) {
        val identity = ProviderTokenLedgerIdentity.from(pinned)
        tokenLedgers[identity] ?: if (tokenLedgers.size >= maximumProviderIdentities) {
            null
        } else {
            AiModelPageTokenLedger(maximumConsumedTokensPerProvider).also { ledger ->
                tokenLedgers[identity] = ledger
            }
        }
    }

    internal fun release() {
        synchronized(lock) {
            check(active) { "Model-list process owner was not acquired" }
            active = false
        }
    }

    private data class ProviderTokenLedgerIdentity(
        val component: AiTextProviderComponent,
        val packageUid: Int,
        val versionCode: Long,
        val lastUpdateTime: Long,
        val signerSha256: List<String>,
        val providerId: String,
        val protocolVersion: AiProtocolVersion,
    ) {
        companion object {
            fun from(pinned: AiTextProviderPinnedIdentity) = ProviderTokenLedgerIdentity(
                component = pinned.component,
                packageUid = pinned.packageUid,
                versionCode = pinned.versionCode,
                lastUpdateTime = pinned.lastUpdateTime,
                signerSha256 = pinned.signerSha256.toList(),
                providerId = pinned.providerId,
                protocolVersion = pinned.protocolVersion,
            )
        }
    }

    private companion object {
        const val DEFAULT_MAXIMUM_PROVIDER_IDENTITIES = 16
        const val DEFAULT_MAXIMUM_CONSUMED_TOKENS_PER_PROVIDER = 256
        const val MAXIMUM_PROVIDER_IDENTITIES_LIMIT = 256
        const val MAXIMUM_PROCESS_TOKEN_ENTRIES = 4_096L
    }
}

internal enum class AiTextProviderModelListingAdmission {
    ACQUIRED,
    BUSY,
    FUSED,
}

/**
 * Default-off, transport-independent owner for a real model-list Binder transcript.
 *
 * The metadata getters and every model-list call use the same descriptor-verified connection. The
 * coordinator revalidates bounded provider metadata against the pinned selection, then performs a
 * full exact-package reinspection immediately before every initial or continuation Dispatch. It
 * owns the binding, callback registrations, absolute deadline, operation leases, and one terminal
 * outcome. It has no production call site and never opens a session, transfers a descriptor,
 * grants credit, touches runtime/UI state, or routes an ai.* API.
 */
internal class AiTextProviderModelListingCoordinator(
    private val transport: AiTextProviderModelListingTransport,
    workerExecutor: Executor,
    private val blockingExecutor: Executor,
    private val deadlineScheduler: AiTextProviderModelListingDeadlineScheduler,
    private val clock: AiTextProviderModelListingClock,
    private val owner: AiTextProviderModelListingProcessOwner,
) {
    private val lane = ModelListingSerialExecutor(workerExecutor)

    fun start(
        request: AiTextProviderModelListingRunRequest,
        onOutcome: (AiTextProviderModelListingRunOutcome) -> Unit,
    ): Closeable {
        if (!request.options.enabled) {
            deliver(onOutcome, AiTextProviderModelListingRunOutcome.Disabled)
            return Closeable {}
        }
        if (!request.snapshot.matchesPinnedPackage(request.pinnedProvider)) {
            deliver(
                onOutcome,
                AiTextProviderModelListingRunOutcome.Rejected(
                    AiTextProviderModelListingRunFailure.PINNED_IDENTITY_MISMATCH,
                ),
            )
            return Closeable {}
        }
        when (owner.tryAcquire(request.snapshot.component)) {
            AiTextProviderModelListingAdmission.FUSED -> {
                deliver(onOutcome, AiTextProviderModelListingRunOutcome.Fused)
                return Closeable {}
            }
            AiTextProviderModelListingAdmission.BUSY -> {
                deliver(onOutcome, AiTextProviderModelListingRunOutcome.Busy)
                return Closeable {}
            }
            AiTextProviderModelListingAdmission.ACQUIRED -> Unit
        }
        val attempt = try {
            Attempt(request, onOutcome)
        } catch (_: Exception) {
            owner.release()
            deliver(
                onOutcome,
                AiTextProviderModelListingRunOutcome.Rejected(
                    AiTextProviderModelListingRunFailure.INTERNAL_FAILURE,
                ),
            )
            return Closeable {}
        }
        attempt.armDeadlineAndBegin()
        return Closeable(attempt::cancel)
    }

    private inner class Attempt(
        private val request: AiTextProviderModelListingRunRequest,
        private val onOutcome: (AiTextProviderModelListingRunOutcome) -> Unit,
    ) {
        private val lifecycleLock = Any()
        private val deadlineAtMillis = saturatingAdd(
            clock.elapsedRealtimeMillis(),
            request.options.timeoutMillis,
        )
        private val phase = AtomicReference(Phase.PRE_BIND)
        private val binderRpcInFlight = AtomicBoolean(false)
        private val bindingSlot = ModelListingCloseSlot<AiTextProviderModelListingBinding>()
        private val deadlineSlot = ModelListingCloseSlot<Closeable>()
        private val preparedCalls = ModelListingCloseRegistry<AiTextProviderModelListingPreparedCall>()
        private val issuedSequences = HashSet<Long>()
        private val successfulDispatchSequences = HashSet<Long>()
        private val pendingCallbackEvents = ArrayDeque<CallbackEvent>()

        private var connectionAccepted = false
        private var rpc: AiTextProviderModelListingRpc? = null
        private var policy: AiTextProviderModelListingPolicy? = null
        private var pendingDispatch: AiTextProviderModelListingDispatch? = null
        private var dispatchFlowActive = false
        private var callbackAdmissionActive = false
        private var operationsInFlight = 0
        private var terminal = false
        private var terminalRetainsActiveWatchdog = false
        private var terminalSettled = false
        private var gateReleased = false
        private var outcomeDelivered = false
        private var pendingOutcome: AiTextProviderModelListingRunOutcome? = null

        fun armDeadlineAndBegin() {
            val remaining = remainingMillis()
            if (remaining <= 0L) {
                timeout()
                return
            }
            val deadline = try {
                deadlineScheduler.schedule(remaining, ::timeout)
            } catch (_: Exception) {
                finish(
                    AiTextProviderModelListingRunOutcome.Rejected(
                        AiTextProviderModelListingRunFailure.EXECUTOR_REJECTED,
                    ),
                )
                return
            }
            deadlineSlot.install(deadline)
            enqueue(::begin)
        }

        private fun begin() {
            phase.set(Phase.PRE_BIND)
            if (!reinspectMatches()) {
                finish(identityDrift())
                return
            }
            if (stopForDeadlineOrTerminal()) return
            val binding = try {
                transport.bindExact(
                    request.snapshot,
                    object : AiTextProviderModelListingBindingCallback {
                        override fun onConnected(connection: AiTextProviderModelListingConnection) {
                            enqueue { connected(connection) }
                        }

                        override fun onBindingFailure(failure: AiTextProviderModelListingRunFailure) {
                            finishExternal(AiTextProviderModelListingRunOutcome.Rejected(failure))
                        }

                        override fun onBinderDeath() {
                            finishExternal(AiTextProviderModelListingRunOutcome.BinderDied)
                        }
                    },
                )
            } catch (_: SecurityException) {
                finish(
                    AiTextProviderModelListingRunOutcome.Rejected(
                        AiTextProviderModelListingRunFailure.BIND_SECURITY,
                    ),
                )
                return
            } catch (_: Exception) {
                finish(
                    AiTextProviderModelListingRunOutcome.Rejected(
                        AiTextProviderModelListingRunFailure.BIND_REJECTED,
                    ),
                )
                return
            }
            bindingSlot.install(binding)
        }

        private fun connected(connection: AiTextProviderModelListingConnection) {
            if (connectionAccepted) {
                finish(
                    AiTextProviderModelListingRunOutcome.Rejected(
                        AiTextProviderModelListingRunFailure.DUPLICATE_CONNECTION,
                    ),
                )
                return
            }
            connectionAccepted = true
            phase.set(Phase.METADATA)
            submitMetadataRead(connection)
        }

        private fun submitMetadataRead(connection: AiTextProviderModelListingConnection) {
            if (isTerminal()) return
            try {
                blockingExecutor.execute {
                    if (!beginOperation()) return@execute
                    try {
                        val result = readMetadata(connection)
                        enqueue { acceptMetadata(result) }
                    } catch (_: Throwable) {
                        finish(internalFailure())
                    } finally {
                        operationDone()
                    }
                }
            } catch (_: Exception) {
                finish(executorRejected())
            }
        }

        private fun readMetadata(
            connection: AiTextProviderModelListingConnection,
        ): MetadataReadResult {
            if (stopForDeadlineOrTerminal()) return MetadataReadResult.Abandoned
            if (!reinspectMatches()) {
                return MetadataReadResult.Failed(AiTextProviderModelListingRunFailure.IDENTITY_DRIFT)
            }
            if (stopForDeadlineOrTerminal()) return MetadataReadResult.Abandoned
            val openedRpc = if (!beginBinderRpc()) {
                return MetadataReadResult.Abandoned
            } else {
                try {
                    transport.openRpc(connection)
                } catch (_: AiTextProviderModelListingDescriptorMismatchException) {
                    return MetadataReadResult.Failed(
                        AiTextProviderModelListingRunFailure.DESCRIPTOR_MISMATCH,
                    )
                } catch (_: SecurityException) {
                    return MetadataReadResult.Failed(
                        AiTextProviderModelListingRunFailure.REMOTE_SECURITY,
                    )
                } catch (_: Exception) {
                    return MetadataReadResult.Failed(
                        AiTextProviderModelListingRunFailure.REMOTE_FAILURE,
                    )
                } finally {
                    endBinderRpc()
                }
            }
            if (stopForDeadlineOrTerminal()) return MetadataReadResult.Abandoned
            val providerInfo = when (val value = readProviderInfo(openedRpc)) {
                null -> return MetadataReadResult.Abandoned
                is MetadataValue.Failed -> return MetadataReadResult.Failed(value.failure)
                is MetadataValue.Value -> value.value
            }
            if (stopForDeadlineOrTerminal()) return MetadataReadResult.Abandoned
            val capabilities = when (val value = readCapabilities(openedRpc)) {
                null -> return MetadataReadResult.Abandoned
                is MetadataValue.Failed -> return MetadataReadResult.Failed(value.failure)
                is MetadataValue.Value -> value.value
            }
            if (stopForDeadlineOrTerminal()) return MetadataReadResult.Abandoned
            val current = reinspectionSnapshot()
                ?: return MetadataReadResult.Failed(AiTextProviderModelListingRunFailure.IDENTITY_DRIFT)
            if (!request.snapshot.samePackageIdentityAs(current)) {
                return MetadataReadResult.Failed(AiTextProviderModelListingRunFailure.IDENTITY_DRIFT)
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
                    pinned = request.pinnedProvider,
                    current = candidate,
                    environment = request.environment,
                    hostCapabilities = request.hostCapabilities,
                    requirement = request.requirement,
                )
            ) {
                return MetadataReadResult.Failed(
                    AiTextProviderModelListingRunFailure.PINNED_METADATA_MISMATCH,
                )
            }
            return MetadataReadResult.Complete(openedRpc)
        }

        private fun readProviderInfo(
            rpc: AiTextProviderModelListingRpc,
        ): MetadataValue<AiProviderInfo>? {
            if (!beginBinderRpc()) return null
            val envelope = try {
                rpc.getProviderInfoEnvelope()
            } catch (_: SecurityException) {
                return MetadataValue.Failed(AiTextProviderModelListingRunFailure.REMOTE_SECURITY)
            } catch (_: Exception) {
                return MetadataValue.Failed(AiTextProviderModelListingRunFailure.REMOTE_FAILURE)
            } finally {
                endBinderRpc()
            } ?: return MetadataValue.Failed(
                AiTextProviderModelListingRunFailure.PROVIDER_INFO_MISSING,
            )
            if (envelope.isEmpty()) {
                return MetadataValue.Failed(
                    AiTextProviderModelListingRunFailure.PROVIDER_INFO_MISSING,
                )
            }
            if (envelope.size > AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES) {
                return MetadataValue.Failed(
                    AiTextProviderModelListingRunFailure.PROVIDER_INFO_TOO_LARGE,
                )
            }
            val decoded = runCatching {
                AiCommonCodec.decodeProviderInfo(envelope.copyOf())
            }.getOrNull() ?: return MetadataValue.Failed(
                AiTextProviderModelListingRunFailure.PROVIDER_INFO_INVALID,
            )
            return MetadataValue.Value(decoded)
        }

        private fun readCapabilities(
            rpc: AiTextProviderModelListingRpc,
        ): MetadataValue<AiTextCapabilities>? {
            if (!beginBinderRpc()) return null
            val envelope = try {
                rpc.getCapabilitiesEnvelope()
            } catch (_: SecurityException) {
                return MetadataValue.Failed(AiTextProviderModelListingRunFailure.REMOTE_SECURITY)
            } catch (_: Exception) {
                return MetadataValue.Failed(AiTextProviderModelListingRunFailure.REMOTE_FAILURE)
            } finally {
                endBinderRpc()
            } ?: return MetadataValue.Failed(
                AiTextProviderModelListingRunFailure.CAPABILITIES_MISSING,
            )
            if (envelope.isEmpty()) {
                return MetadataValue.Failed(
                    AiTextProviderModelListingRunFailure.CAPABILITIES_MISSING,
                )
            }
            if (envelope.size > AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES) {
                return MetadataValue.Failed(
                    AiTextProviderModelListingRunFailure.CAPABILITIES_TOO_LARGE,
                )
            }
            val decoded = runCatching {
                AiTextCodec.decodeCapabilities(envelope.copyOf())
            }.getOrNull() ?: return MetadataValue.Failed(
                AiTextProviderModelListingRunFailure.CAPABILITIES_INVALID,
            )
            return MetadataValue.Value(decoded)
        }

        private fun acceptMetadata(result: MetadataReadResult) {
            if (stopForDeadlineOrTerminal()) return
            when (result) {
                MetadataReadResult.Abandoned -> Unit
                is MetadataReadResult.Failed -> finish(
                    AiTextProviderModelListingRunOutcome.Rejected(result.failure),
                )
                is MetadataReadResult.Complete -> {
                    val tokenLedger = owner.tokenLedgerFor(request.pinnedProvider)
                    if (tokenLedger == null) {
                        finish(internalFailure())
                        return
                    }
                    rpc = result.rpc
                    val listingPolicy = AiTextProviderModelListingPolicy(
                        pinnedProvider = request.pinnedProvider,
                        options = AiTextProviderModelListingOptions(
                            enabled = true,
                            pageSize = request.options.pageSize,
                        ),
                        tokenLedger = tokenLedger,
                    )
                    policy = listingPolicy
                    applyDecision(listingPolicy.start())
                }
            }
        }

        private fun applyDecision(decision: AiTextProviderModelListingDecision) {
            when (decision) {
                is AiTextProviderModelListingDecision.Dispatch -> queueDispatch(decision.dispatch)
                is AiTextProviderModelListingDecision.Terminal -> finish(mapOutcome(decision.outcome))
                AiTextProviderModelListingDecision.Ignored -> {
                    if (!isTerminal()) finish(internalFailure())
                }
            }
        }

        private fun queueDispatch(dispatch: AiTextProviderModelListingDispatch) {
            val launch = synchronized(lifecycleLock) {
                if (terminal) return
                check(pendingDispatch == null) { "Model-list Dispatch was queued twice" }
                pendingDispatch = dispatch
                takeDispatchIfReadyLocked()
            }
            if (launch != null) launchDispatch(launch)
        }

        private fun launchDispatch(dispatch: AiTextProviderModelListingDispatch) {
            try {
                blockingExecutor.execute {
                    if (!beginOperation()) return@execute
                    try {
                        performDispatch(dispatch)
                    } catch (_: Throwable) {
                        finish(internalFailure())
                    } finally {
                        operationDone()
                        enqueue(::dispatchFlowDone)
                    }
                }
            } catch (_: Exception) {
                finish(executorRejected())
            }
        }

        private fun performDispatch(dispatch: AiTextProviderModelListingDispatch) {
            phase.set(Phase.DISPATCH)
            if (stopForDeadlineOrTerminal()) return
            if (!reinspectMatches()) {
                finish(identityDrift())
                return
            }
            if (stopForDeadlineOrTerminal()) return
            val currentRpc = rpc ?: run {
                finish(internalFailure())
                return
            }
            val callback = callbackFor(dispatch.sequence)
            val call = try {
                currentRpc.prepareCall(dispatch.sequence, callback)
            } catch (_: SecurityException) {
                finish(remoteSecurity())
                return
            } catch (_: Exception) {
                finish(remoteFailure())
                return
            }
            preparedCalls.install(call)
            if (stopForDeadlineOrTerminal()) return
            if (!beginBinderRpc(dispatch.sequence)) return
            var succeeded = false
            try {
                call.dispatch(dispatch.copyRequestEnvelope())
                succeeded = true
            } catch (_: SecurityException) {
                finish(remoteSecurity())
            } catch (_: Exception) {
                finish(remoteFailure())
            } finally {
                endBinderRpc()
            }
            if (succeeded && !isTerminal()) {
                val callback = synchronized(lifecycleLock) {
                    successfulDispatchSequences += dispatch.sequence
                    takeCallbackIfReadyLocked()
                }
                if (callback != null) launchCallbackAdmission(callback)
                phase.set(Phase.WAITING_CALLBACK)
            }
        }

        private fun callbackFor(
            sequence: Long,
        ): AiTextProviderModelListingCallbackIngress =
            object : AiTextProviderModelListingCallbackIngress {
                override fun onPage(callingUid: Int, envelope: ByteArray?) {
                    acceptCallback(sequence, callingUid, envelope, page = true)
                }

                override fun onFailed(callingUid: Int, envelope: ByteArray?) {
                    acceptCallback(sequence, callingUid, envelope, page = false)
                }
            }

        private fun acceptCallback(
            sequence: Long,
            callingUid: Int,
            envelope: ByteArray?,
            page: Boolean,
        ) {
            if (isTerminal()) return
            if (!AiTextProviderCallbackCallerPolicy.isExpectedUid(
                    request.pinnedProvider,
                    callingUid,
                )
            ) {
                finishExternal(transcriptRejected(AiTextProviderModelListingFailure.CALLBACK_UID_MISMATCH))
                return
            }
            val oversized = when {
                envelope == null -> null
                page && envelope.size > AiTextLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES ->
                    AiTextProviderModelListingFailure.PAGE_TOO_LARGE
                !page && envelope.size > AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES ->
                    AiTextProviderModelListingFailure.ERROR_TOO_LARGE
                else -> null
            }
            if (oversized != null) {
                finishExternal(transcriptRejected(oversized))
                return
            }
            var failure: AiTextProviderModelListingRunFailure? = null
            val launch = synchronized(lifecycleLock) {
                if (terminal) return
                when {
                    sequence !in issuedSequences -> {
                        failure = AiTextProviderModelListingRunFailure.CALLBACK_BEFORE_DISPATCH
                        null
                    }
                    pendingCallbackEvents.size + (if (callbackAdmissionActive) 1 else 0) >=
                        MAX_BUFFERED_CALLBACK_EVENTS -> {
                        failure = AiTextProviderModelListingRunFailure.CALLBACK_FLOOD
                        null
                    }
                    else -> {
                        val event = CallbackEvent(
                            sequence = sequence,
                            callingUid = callingUid,
                            envelope = envelope?.copyOf(),
                            page = page,
                        )
                        pendingCallbackEvents += event
                        takeCallbackIfReadyLocked()
                    }
                }
            }
            if (failure != null) {
                finishExternal(AiTextProviderModelListingRunOutcome.Rejected(requireNotNull(failure)))
            } else if (launch != null) {
                launchCallbackAdmission(launch)
            }
        }

        private fun launchCallbackAdmission(event: CallbackEvent) {
            try {
                blockingExecutor.execute {
                    if (!beginOperation()) return@execute
                    try {
                        val result = admitCallback(event)
                        enqueue { acceptCallbackAdmission(result) }
                    } catch (_: Throwable) {
                        finish(internalFailure())
                    } finally {
                        operationDone()
                    }
                }
            } catch (_: Exception) {
                finish(executorRejected())
            }
        }

        private fun admitCallback(event: CallbackEvent): CallbackAdmissionResult {
            phase.set(Phase.CALLBACK_REINSPECTION)
            if (stopForDeadlineOrTerminal()) return CallbackAdmissionResult.Abandoned
            if (!reinspectMatches()) {
                return CallbackAdmissionResult.Failed(
                    AiTextProviderModelListingRunFailure.IDENTITY_DRIFT,
                )
            }
            if (stopForDeadlineOrTerminal()) return CallbackAdmissionResult.Abandoned
            val listingPolicy = policy ?: return CallbackAdmissionResult.Failed(
                AiTextProviderModelListingRunFailure.INTERNAL_FAILURE,
            )
            val decision = if (event.page) {
                listingPolicy.acceptPage(event.sequence, event.callingUid, event.envelope)
            } else {
                listingPolicy.acceptFailed(event.sequence, event.callingUid, event.envelope)
            }
            return CallbackAdmissionResult.Decided(decision)
        }

        private fun acceptCallbackAdmission(result: CallbackAdmissionResult) {
            if (stopForDeadlineOrTerminal()) return
            when (result) {
                CallbackAdmissionResult.Abandoned -> Unit
                is CallbackAdmissionResult.Failed -> finish(
                    AiTextProviderModelListingRunOutcome.Rejected(result.failure),
                )
                is CallbackAdmissionResult.Decided -> applyDecision(result.decision)
            }
            callbackAdmissionDone()
        }

        private fun callbackAdmissionDone() {
            val next = synchronized(lifecycleLock) {
                callbackAdmissionActive = false
                takeCallbackIfReadyLocked()
            }
            if (next != null) launchCallbackAdmission(next)
        }

        private fun takeCallbackIfReadyLocked(): CallbackEvent? {
            if (terminal || callbackAdmissionActive) return null
            val event = pendingCallbackEvents.firstOrNull {
                it.sequence in successfulDispatchSequences
            } ?: return null
            pendingCallbackEvents.remove(event)
            callbackAdmissionActive = true
            return event
        }

        private fun dispatchFlowDone() {
            val next = synchronized(lifecycleLock) {
                dispatchFlowActive = false
                takeDispatchIfReadyLocked()
            }
            if (next != null) launchDispatch(next)
        }

        private fun takeDispatchIfReadyLocked(): AiTextProviderModelListingDispatch? {
            if (terminal || dispatchFlowActive) return null
            val dispatch = pendingDispatch ?: return null
            pendingDispatch = null
            dispatchFlowActive = true
            return dispatch
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

        fun cancel() {
            finishExternal(AiTextProviderModelListingRunOutcome.Cancelled)
        }

        private fun timeout() {
            if (!deadlineExpired()) return
            val decision = claimTerminal(
                requested = AiTextProviderModelListingRunOutcome.TimedOut,
                retainActiveWatchdog = false,
            )
            if (decision == null) {
                synchronized(lifecycleLock) {
                    if (terminalRetainsActiveWatchdog && operationsInFlight > 0) {
                        owner.fuse(request.snapshot.component)
                    }
                }
                deadlineSlot.close()
                return
            }
            if (decision.fuseComponent) owner.fuse(request.snapshot.component)
            completeClaimed(
                outcome = decision.outcome,
                deliverBeforeIdle = true,
                retainDeadline = false,
            )
        }

        private fun enqueue(action: () -> Unit) {
            if (isTerminal()) return
            lane.execute(
                command = Runnable {
                    if (!beginOperation()) return@Runnable
                    try {
                        if (!stopForDeadlineOrTerminal()) action()
                    } catch (_: Throwable) {
                        finish(internalFailure())
                    } finally {
                        operationDone()
                    }
                },
                onRejected = { finish(executorRejected()) },
            )
        }

        private fun finish(outcome: AiTextProviderModelListingRunOutcome) {
            finish(outcome, allowEarlyDelivery = false)
        }

        private fun finishExternal(outcome: AiTextProviderModelListingRunOutcome) {
            finish(outcome, allowEarlyDelivery = true)
        }

        private fun finish(
            outcome: AiTextProviderModelListingRunOutcome,
            allowEarlyDelivery: Boolean,
        ) {
            val decision = claimTerminal(
                requested = outcome,
                retainActiveWatchdog = allowEarlyDelivery,
            ) ?: return
            if (decision.fuseComponent) owner.fuse(request.snapshot.component)
            completeClaimed(
                outcome = decision.outcome,
                deliverBeforeIdle = decision.timedOut ||
                    decision.outcome === AiTextProviderModelListingRunOutcome.Cancelled ||
                    allowEarlyDelivery && decision.retainDeadline,
                retainDeadline = decision.retainDeadline,
            )
        }

        private fun claimTerminal(
            requested: AiTextProviderModelListingRunOutcome,
            retainActiveWatchdog: Boolean,
        ): TerminalDecision? = synchronized(lifecycleLock) {
            if (terminal) return@synchronized null
            val timedOut = deadlineExpired()
            val outcome = if (timedOut) AiTextProviderModelListingRunOutcome.TimedOut else requested
            val retainDeadline = !timedOut && retainActiveWatchdog && operationsInFlight > 0
            terminal = true
            terminalRetainsActiveWatchdog = retainDeadline
            pendingDispatch = null
            pendingCallbackEvents.clear()
            TerminalDecision(
                outcome = outcome,
                timedOut = timedOut,
                fuseComponent = timedOut && operationsInFlight > 0,
                retainDeadline = retainDeadline,
            )
        }

        private fun completeClaimed(
            outcome: AiTextProviderModelListingRunOutcome,
            deliverBeforeIdle: Boolean,
            retainDeadline: Boolean,
        ) {
            if (!retainDeadline) deadlineSlot.close()
            preparedCalls.close()
            bindingSlot.close()
            synchronized(lifecycleLock) {
                check(pendingOutcome == null) { "Model-list outcome was stored more than once" }
                pendingOutcome = outcome
                terminalSettled = true
            }
            completeIdleIfReady()
            if (deliverBeforeIdle) deliverPendingOutcome()
        }

        private fun beginOperation(): Boolean = synchronized(lifecycleLock) {
            if (terminal) return@synchronized false
            operationsInFlight += 1
            true
        }

        private fun operationDone() {
            if (deadlineExpired()) timeout()
            synchronized(lifecycleLock) {
                check(operationsInFlight > 0) { "Model-list operation count underflow" }
                operationsInFlight -= 1
            }
            completeIdleIfReady()
        }

        private fun beginBinderRpc(sequence: Long? = null): Boolean {
            var expired = false
            synchronized(lifecycleLock) {
                if (terminal) return false
                if (deadlineExpired()) {
                    expired = true
                } else {
                    check(!binderRpcInFlight.get()) { "Model-list Binder RPC calls must be serial" }
                    if (sequence != null) issuedSequences += sequence
                    binderRpcInFlight.set(true)
                    return true
                }
            }
            if (expired) timeout()
            return false
        }

        private fun endBinderRpc() {
            if (deadlineExpired()) timeout()
            binderRpcInFlight.set(false)
        }

        private fun completeIdleIfReady() {
            var release = false
            var closeDeadline = false
            var outcome: AiTextProviderModelListingRunOutcome? = null
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
            if (release) owner.release()
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

        private fun isTerminal(): Boolean = synchronized(lifecycleLock) { terminal }

        private fun deadlineExpired(): Boolean = remainingMillis() <= 0L

        private fun remainingMillis(): Long = deadlineAtMillis - clock.elapsedRealtimeMillis()

        private fun mapOutcome(
            outcome: AiTextProviderModelListingOutcome,
        ): AiTextProviderModelListingRunOutcome = when (outcome) {
            AiTextProviderModelListingOutcome.Disabled -> AiTextProviderModelListingRunOutcome.Disabled
            AiTextProviderModelListingOutcome.Cancelled -> AiTextProviderModelListingRunOutcome.Cancelled
            AiTextProviderModelListingOutcome.TimedOut -> AiTextProviderModelListingRunOutcome.TimedOut
            AiTextProviderModelListingOutcome.BinderDied -> AiTextProviderModelListingRunOutcome.BinderDied
            is AiTextProviderModelListingOutcome.Completed ->
                AiTextProviderModelListingRunOutcome.Completed(outcome.catalog)
            is AiTextProviderModelListingOutcome.ProviderFailed ->
                AiTextProviderModelListingRunOutcome.ProviderFailed(outcome.error)
            is AiTextProviderModelListingOutcome.Rejected ->
                AiTextProviderModelListingRunOutcome.Rejected(
                    failure = AiTextProviderModelListingRunFailure.TRANSCRIPT_REJECTED,
                    transcriptFailure = outcome.failure,
                )
        }

        private fun identityDrift() = AiTextProviderModelListingRunOutcome.Rejected(
            AiTextProviderModelListingRunFailure.IDENTITY_DRIFT,
        )

        private fun executorRejected() = AiTextProviderModelListingRunOutcome.Rejected(
            AiTextProviderModelListingRunFailure.EXECUTOR_REJECTED,
        )

        private fun internalFailure() = AiTextProviderModelListingRunOutcome.Rejected(
            AiTextProviderModelListingRunFailure.INTERNAL_FAILURE,
        )

        private fun remoteSecurity() = AiTextProviderModelListingRunOutcome.Rejected(
            AiTextProviderModelListingRunFailure.REMOTE_SECURITY,
        )

        private fun remoteFailure() = AiTextProviderModelListingRunOutcome.Rejected(
            AiTextProviderModelListingRunFailure.REMOTE_FAILURE,
        )

        private fun transcriptRejected(
            failure: AiTextProviderModelListingFailure,
        ) = AiTextProviderModelListingRunOutcome.Rejected(
            failure = AiTextProviderModelListingRunFailure.TRANSCRIPT_REJECTED,
            transcriptFailure = failure,
        )

        private fun saturatingAdd(left: Long, right: Long): Long =
            if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
    }

    private sealed class MetadataReadResult {
        object Abandoned : MetadataReadResult()
        data class Failed(val failure: AiTextProviderModelListingRunFailure) : MetadataReadResult()
        data class Complete(val rpc: AiTextProviderModelListingRpc) : MetadataReadResult()
    }

    private sealed class MetadataValue<out T> {
        data class Failed(val failure: AiTextProviderModelListingRunFailure) : MetadataValue<Nothing>()
        data class Value<T>(val value: T) : MetadataValue<T>()
    }

    private data class CallbackEvent(
        val sequence: Long,
        val callingUid: Int,
        val envelope: ByteArray?,
        val page: Boolean,
    )

    private sealed class CallbackAdmissionResult {
        object Abandoned : CallbackAdmissionResult()
        data class Failed(val failure: AiTextProviderModelListingRunFailure) : CallbackAdmissionResult()
        data class Decided(
            val decision: AiTextProviderModelListingDecision,
        ) : CallbackAdmissionResult()
    }

    private data class TerminalDecision(
        val outcome: AiTextProviderModelListingRunOutcome,
        val timedOut: Boolean,
        val fuseComponent: Boolean,
        val retainDeadline: Boolean,
    )

    private enum class Phase {
        PRE_BIND,
        METADATA,
        DISPATCH,
        WAITING_CALLBACK,
        CALLBACK_REINSPECTION,
    }

    private companion object {
        const val MAX_BUFFERED_CALLBACK_EVENTS = 2

        fun deliver(
            callback: (AiTextProviderModelListingRunOutcome) -> Unit,
            outcome: AiTextProviderModelListingRunOutcome,
        ) {
            runCatching { callback(outcome) }
        }
    }
}

private fun AiTextProviderPackageSnapshot.matchesPinnedPackage(
    pinned: AiTextProviderPinnedIdentity,
): Boolean =
    component == pinned.component &&
        packageUid == pinned.packageUid &&
        versionCode == pinned.versionCode &&
        lastUpdateTime == pinned.lastUpdateTime &&
        signerSha256 == pinned.signerSha256

private class ModelListingCloseSlot<T : Closeable> : Closeable {
    private val lock = Any()
    private var value: T? = null
    private var closed = false

    fun install(candidate: T) {
        val closeNow = synchronized(lock) {
            check(value == null) { "Model-list resource was installed more than once" }
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

private class ModelListingCloseRegistry<T : Closeable> : Closeable {
    private val lock = Any()
    private val values = ArrayList<T>()
    private var closed = false

    fun install(candidate: T) {
        val closeNow = synchronized(lock) {
            if (closed) true else {
                values += candidate
                false
            }
        }
        if (closeNow) runCatching { candidate.close() }
    }

    override fun close() {
        val toClose = synchronized(lock) {
            if (closed) return
            closed = true
            values.toList().also { values.clear() }
        }
        toClose.forEach { value -> runCatching { value.close() } }
    }
}

private class ModelListingSerialExecutor(
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
