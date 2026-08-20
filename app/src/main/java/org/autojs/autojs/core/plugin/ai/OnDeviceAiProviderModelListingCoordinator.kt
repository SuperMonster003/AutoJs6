package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiProviderInfo
import org.autojs.plugin.ondeviceai.api.AiModelPageTokenLedger
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilities
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCodec
import org.autojs.plugin.ondeviceai.api.OnDeviceAiLimits
import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Opaque connection owned by a future platform transport until its binding is closed. */
internal interface OnDeviceAiProviderModelListingConnection

internal interface OnDeviceAiProviderModelListingBinding : Closeable

internal interface OnDeviceAiProviderModelListingBindingCallback {
    fun onConnected(connection: OnDeviceAiProviderModelListingConnection)

    fun onBindingFailure(failure: OnDeviceAiProviderModelListingRunFailure)

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
internal interface OnDeviceAiProviderModelListingCallbackIngress {
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
internal interface OnDeviceAiProviderModelListingPreparedCall : Closeable {
    fun dispatch(requestEnvelope: ByteArray)
}

/** Descriptor-verified view of the same Binder used for metadata revalidation and model listing. */
internal interface OnDeviceAiProviderModelListingRpc {
    fun getProviderInfoEnvelope(): ByteArray?

    fun getCapabilitiesEnvelope(): ByteArray?

    fun prepareCall(
        sequence: Long,
        callback: OnDeviceAiProviderModelListingCallbackIngress,
    ): OnDeviceAiProviderModelListingPreparedCall
}

/**
 * Platform boundary for an explicitly invoked model-list run.
 *
 * [openRpc] must validate the exact remote interface descriptor before constructing an AIDL proxy.
 * A future Android implementation must bind an actionless Intent to the exact component and keep
 * the returned binding alive until the coordinator closes it.
 */
internal interface OnDeviceAiProviderModelListingTransport {
    fun inspectExact(component: OnDeviceAiProviderComponent): OnDeviceAiProviderDiscoverySourceResult

    fun bindExact(
        snapshot: OnDeviceAiProviderPackageSnapshot,
        callback: OnDeviceAiProviderModelListingBindingCallback,
    ): OnDeviceAiProviderModelListingBinding

    fun openRpc(connection: OnDeviceAiProviderModelListingConnection): OnDeviceAiProviderModelListingRpc
}

/** Marker used without retaining or logging an untrusted remote descriptor string. */
internal class OnDeviceAiProviderModelListingDescriptorMismatchException : SecurityException()

internal fun interface OnDeviceAiProviderModelListingDeadlineScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): Closeable
}

internal fun interface OnDeviceAiProviderModelListingClock {
    fun elapsedRealtimeMillis(): Long
}

internal data class OnDeviceAiProviderModelListingRunOptions(
    val enabled: Boolean = false,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    init {
        require(pageSize in 1..OnDeviceAiLimits.MAX_MODELS_PER_PAGE) {
            "Invalid on-device AI model-list page size"
        }
        require(timeoutMillis > 0L) { "Model-list timeout must be positive" }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 50
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
    }
}

internal data class OnDeviceAiProviderModelListingRunRequest(
    val snapshot: OnDeviceAiProviderPackageSnapshot,
    val pinnedProvider: OnDeviceAiProviderPinnedIdentity,
    val environment: OnDeviceAiProviderEnvironment,
    val hostCapabilities: OnDeviceAiCapabilities,
    val requirement: OnDeviceAiCapabilityRequirement = OnDeviceAiCapabilityRequirement(),
    val options: OnDeviceAiProviderModelListingRunOptions = OnDeviceAiProviderModelListingRunOptions(),
)

internal enum class OnDeviceAiProviderModelListingRunFailure {
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

internal sealed class OnDeviceAiProviderModelListingRunOutcome {
    object Disabled : OnDeviceAiProviderModelListingRunOutcome()
    object Busy : OnDeviceAiProviderModelListingRunOutcome()
    object Fused : OnDeviceAiProviderModelListingRunOutcome()
    object Cancelled : OnDeviceAiProviderModelListingRunOutcome()
    object TimedOut : OnDeviceAiProviderModelListingRunOutcome()
    object BinderDied : OnDeviceAiProviderModelListingRunOutcome()

    data class Completed(
        val catalog: OnDeviceAiProviderModelCatalog,
    ) : OnDeviceAiProviderModelListingRunOutcome()

    class ProviderFailed(
        val error: OnDeviceAiProviderModelListingErrorSummary,
    ) : OnDeviceAiProviderModelListingRunOutcome() {
        override fun toString(): String =
            "ProviderFailed(code=${error.code}, retryDisposition=${error.retryDisposition}, " +
                "providerCodePresent=${error.providerCodePresent})"
    }

    data class Rejected(
        val failure: OnDeviceAiProviderModelListingRunFailure,
        val transcriptFailure: OnDeviceAiProviderModelListingFailure? = null,
    ) : OnDeviceAiProviderModelListingRunOutcome()
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
internal class OnDeviceAiProviderModelListingProcessOwner(
    private val maximumProviderIdentities: Int = DEFAULT_MAXIMUM_PROVIDER_IDENTITIES,
    private val maximumConsumedTokensPerProvider: Int =
        DEFAULT_MAXIMUM_CONSUMED_TOKENS_PER_PROVIDER,
) {
    private val lock = Any()
    private var active = false
    private val fusedComponents = HashSet<OnDeviceAiProviderComponent>()
    private val tokenLedgers = LinkedHashMap<ProviderTokenLedgerIdentity, AiModelPageTokenLedger>()

    init {
        require(maximumProviderIdentities in 1..MAXIMUM_PROVIDER_IDENTITIES_LIMIT) {
            "Invalid model-list provider identity capacity"
        }
        require(
            maximumConsumedTokensPerProvider in
                1..OnDeviceAiLimits.MAX_MODEL_PAGE_TOKEN_LEDGER_ENTRIES,
        ) { "Invalid per-provider model page-token capacity" }
        require(
            maximumProviderIdentities.toLong() * maximumConsumedTokensPerProvider <=
                MAXIMUM_PROCESS_TOKEN_ENTRIES,
        ) { "Model page-token process budget is too large" }
    }

    internal fun tryAcquire(
        component: OnDeviceAiProviderComponent,
    ): OnDeviceAiProviderModelListingAdmission = synchronized(lock) {
        when {
            component in fusedComponents -> OnDeviceAiProviderModelListingAdmission.FUSED
            active -> OnDeviceAiProviderModelListingAdmission.BUSY
            else -> {
                active = true
                OnDeviceAiProviderModelListingAdmission.ACQUIRED
            }
        }
    }

    internal fun fuse(component: OnDeviceAiProviderComponent) {
        synchronized(lock) { fusedComponents += component }
    }

    internal fun tokenLedgerFor(
        pinned: OnDeviceAiProviderPinnedIdentity,
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
        val component: OnDeviceAiProviderComponent,
        val packageUid: Int,
        val versionCode: Long,
        val lastUpdateTime: Long,
        val signerSha256: List<String>,
        val providerId: String,
        val protocolVersion: AiProtocolVersion,
    ) {
        companion object {
            fun from(pinned: OnDeviceAiProviderPinnedIdentity) = ProviderTokenLedgerIdentity(
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

internal enum class OnDeviceAiProviderModelListingAdmission {
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
internal class OnDeviceAiProviderModelListingCoordinator(
    private val transport: OnDeviceAiProviderModelListingTransport,
    workerExecutor: Executor,
    private val blockingExecutor: Executor,
    private val deadlineScheduler: OnDeviceAiProviderModelListingDeadlineScheduler,
    private val clock: OnDeviceAiProviderModelListingClock,
    private val owner: OnDeviceAiProviderModelListingProcessOwner,
) {
    private val lane = ModelListingSerialExecutor(workerExecutor)

    fun start(
        request: OnDeviceAiProviderModelListingRunRequest,
        onOutcome: (OnDeviceAiProviderModelListingRunOutcome) -> Unit,
    ): Closeable {
        if (!request.options.enabled) {
            deliver(onOutcome, OnDeviceAiProviderModelListingRunOutcome.Disabled)
            return Closeable {}
        }
        if (!request.snapshot.matchesPinnedPackage(request.pinnedProvider)) {
            deliver(
                onOutcome,
                OnDeviceAiProviderModelListingRunOutcome.Rejected(
                    OnDeviceAiProviderModelListingRunFailure.PINNED_IDENTITY_MISMATCH,
                ),
            )
            return Closeable {}
        }
        when (owner.tryAcquire(request.snapshot.component)) {
            OnDeviceAiProviderModelListingAdmission.FUSED -> {
                deliver(onOutcome, OnDeviceAiProviderModelListingRunOutcome.Fused)
                return Closeable {}
            }
            OnDeviceAiProviderModelListingAdmission.BUSY -> {
                deliver(onOutcome, OnDeviceAiProviderModelListingRunOutcome.Busy)
                return Closeable {}
            }
            OnDeviceAiProviderModelListingAdmission.ACQUIRED -> Unit
        }
        val attempt = try {
            Attempt(request, onOutcome)
        } catch (_: Exception) {
            owner.release()
            deliver(
                onOutcome,
                OnDeviceAiProviderModelListingRunOutcome.Rejected(
                    OnDeviceAiProviderModelListingRunFailure.INTERNAL_FAILURE,
                ),
            )
            return Closeable {}
        }
        attempt.armDeadlineAndBegin()
        return Closeable(attempt::cancel)
    }

    private inner class Attempt(
        private val request: OnDeviceAiProviderModelListingRunRequest,
        private val onOutcome: (OnDeviceAiProviderModelListingRunOutcome) -> Unit,
    ) {
        private val lifecycleLock = Any()
        private val deadlineAtMillis = saturatingAdd(
            clock.elapsedRealtimeMillis(),
            request.options.timeoutMillis,
        )
        private val phase = AtomicReference(Phase.PRE_BIND)
        private val binderRpcInFlight = AtomicBoolean(false)
        private val bindingSlot = ModelListingCloseSlot<OnDeviceAiProviderModelListingBinding>()
        private val deadlineSlot = ModelListingCloseSlot<Closeable>()
        private val preparedCalls = ModelListingCloseRegistry<OnDeviceAiProviderModelListingPreparedCall>()
        private val issuedSequences = HashSet<Long>()
        private val successfulDispatchSequences = HashSet<Long>()
        private val pendingCallbackEvents = ArrayDeque<CallbackEvent>()

        private var connectionAccepted = false
        private var rpc: OnDeviceAiProviderModelListingRpc? = null
        private var policy: OnDeviceAiProviderModelListingPolicy? = null
        private var pendingDispatch: OnDeviceAiProviderModelListingDispatch? = null
        private var dispatchFlowActive = false
        private var callbackAdmissionActive = false
        private var operationsInFlight = 0
        private var terminal = false
        private var terminalRetainsActiveWatchdog = false
        private var terminalSettled = false
        private var gateReleased = false
        private var outcomeDelivered = false
        private var pendingOutcome: OnDeviceAiProviderModelListingRunOutcome? = null

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
                    OnDeviceAiProviderModelListingRunOutcome.Rejected(
                        OnDeviceAiProviderModelListingRunFailure.EXECUTOR_REJECTED,
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
                    object : OnDeviceAiProviderModelListingBindingCallback {
                        override fun onConnected(connection: OnDeviceAiProviderModelListingConnection) {
                            enqueue { connected(connection) }
                        }

                        override fun onBindingFailure(failure: OnDeviceAiProviderModelListingRunFailure) {
                            finishExternal(OnDeviceAiProviderModelListingRunOutcome.Rejected(failure))
                        }

                        override fun onBinderDeath() {
                            finishExternal(OnDeviceAiProviderModelListingRunOutcome.BinderDied)
                        }
                    },
                )
            } catch (_: SecurityException) {
                finish(
                    OnDeviceAiProviderModelListingRunOutcome.Rejected(
                        OnDeviceAiProviderModelListingRunFailure.BIND_SECURITY,
                    ),
                )
                return
            } catch (_: Exception) {
                finish(
                    OnDeviceAiProviderModelListingRunOutcome.Rejected(
                        OnDeviceAiProviderModelListingRunFailure.BIND_REJECTED,
                    ),
                )
                return
            }
            bindingSlot.install(binding)
        }

        private fun connected(connection: OnDeviceAiProviderModelListingConnection) {
            if (connectionAccepted) {
                finish(
                    OnDeviceAiProviderModelListingRunOutcome.Rejected(
                        OnDeviceAiProviderModelListingRunFailure.DUPLICATE_CONNECTION,
                    ),
                )
                return
            }
            connectionAccepted = true
            phase.set(Phase.METADATA)
            submitMetadataRead(connection)
        }

        private fun submitMetadataRead(connection: OnDeviceAiProviderModelListingConnection) {
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
            connection: OnDeviceAiProviderModelListingConnection,
        ): MetadataReadResult {
            if (stopForDeadlineOrTerminal()) return MetadataReadResult.Abandoned
            if (!reinspectMatches()) {
                return MetadataReadResult.Failed(OnDeviceAiProviderModelListingRunFailure.IDENTITY_DRIFT)
            }
            if (stopForDeadlineOrTerminal()) return MetadataReadResult.Abandoned
            val openedRpc = if (!beginBinderRpc()) {
                return MetadataReadResult.Abandoned
            } else {
                try {
                    transport.openRpc(connection)
                } catch (_: OnDeviceAiProviderModelListingDescriptorMismatchException) {
                    return MetadataReadResult.Failed(
                        OnDeviceAiProviderModelListingRunFailure.DESCRIPTOR_MISMATCH,
                    )
                } catch (_: SecurityException) {
                    return MetadataReadResult.Failed(
                        OnDeviceAiProviderModelListingRunFailure.REMOTE_SECURITY,
                    )
                } catch (_: Exception) {
                    return MetadataReadResult.Failed(
                        OnDeviceAiProviderModelListingRunFailure.REMOTE_FAILURE,
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
                ?: return MetadataReadResult.Failed(OnDeviceAiProviderModelListingRunFailure.IDENTITY_DRIFT)
            if (!request.snapshot.samePackageIdentityAs(current)) {
                return MetadataReadResult.Failed(OnDeviceAiProviderModelListingRunFailure.IDENTITY_DRIFT)
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
                    pinned = request.pinnedProvider,
                    current = candidate,
                    environment = request.environment,
                    hostCapabilities = request.hostCapabilities,
                    requirement = request.requirement,
                )
            ) {
                return MetadataReadResult.Failed(
                    OnDeviceAiProviderModelListingRunFailure.PINNED_METADATA_MISMATCH,
                )
            }
            return MetadataReadResult.Complete(openedRpc)
        }

        private fun readProviderInfo(
            rpc: OnDeviceAiProviderModelListingRpc,
        ): MetadataValue<AiProviderInfo>? {
            if (!beginBinderRpc()) return null
            val envelope = try {
                rpc.getProviderInfoEnvelope()
            } catch (_: SecurityException) {
                return MetadataValue.Failed(OnDeviceAiProviderModelListingRunFailure.REMOTE_SECURITY)
            } catch (_: Exception) {
                return MetadataValue.Failed(OnDeviceAiProviderModelListingRunFailure.REMOTE_FAILURE)
            } finally {
                endBinderRpc()
            } ?: return MetadataValue.Failed(
                OnDeviceAiProviderModelListingRunFailure.PROVIDER_INFO_MISSING,
            )
            if (envelope.isEmpty()) {
                return MetadataValue.Failed(
                    OnDeviceAiProviderModelListingRunFailure.PROVIDER_INFO_MISSING,
                )
            }
            if (envelope.size > AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES) {
                return MetadataValue.Failed(
                    OnDeviceAiProviderModelListingRunFailure.PROVIDER_INFO_TOO_LARGE,
                )
            }
            val decoded = runCatching {
                AiCommonCodec.decodeProviderInfo(envelope.copyOf())
            }.getOrNull() ?: return MetadataValue.Failed(
                OnDeviceAiProviderModelListingRunFailure.PROVIDER_INFO_INVALID,
            )
            return MetadataValue.Value(decoded)
        }

        private fun readCapabilities(
            rpc: OnDeviceAiProviderModelListingRpc,
        ): MetadataValue<OnDeviceAiCapabilities>? {
            if (!beginBinderRpc()) return null
            val envelope = try {
                rpc.getCapabilitiesEnvelope()
            } catch (_: SecurityException) {
                return MetadataValue.Failed(OnDeviceAiProviderModelListingRunFailure.REMOTE_SECURITY)
            } catch (_: Exception) {
                return MetadataValue.Failed(OnDeviceAiProviderModelListingRunFailure.REMOTE_FAILURE)
            } finally {
                endBinderRpc()
            } ?: return MetadataValue.Failed(
                OnDeviceAiProviderModelListingRunFailure.CAPABILITIES_MISSING,
            )
            if (envelope.isEmpty()) {
                return MetadataValue.Failed(
                    OnDeviceAiProviderModelListingRunFailure.CAPABILITIES_MISSING,
                )
            }
            if (envelope.size > AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES) {
                return MetadataValue.Failed(
                    OnDeviceAiProviderModelListingRunFailure.CAPABILITIES_TOO_LARGE,
                )
            }
            val decoded = runCatching {
                OnDeviceAiCodec.decodeCapabilities(envelope.copyOf())
            }.getOrNull() ?: return MetadataValue.Failed(
                OnDeviceAiProviderModelListingRunFailure.CAPABILITIES_INVALID,
            )
            return MetadataValue.Value(decoded)
        }

        private fun acceptMetadata(result: MetadataReadResult) {
            if (stopForDeadlineOrTerminal()) return
            when (result) {
                MetadataReadResult.Abandoned -> Unit
                is MetadataReadResult.Failed -> finish(
                    OnDeviceAiProviderModelListingRunOutcome.Rejected(result.failure),
                )
                is MetadataReadResult.Complete -> {
                    val tokenLedger = owner.tokenLedgerFor(request.pinnedProvider)
                    if (tokenLedger == null) {
                        finish(internalFailure())
                        return
                    }
                    rpc = result.rpc
                    val listingPolicy = OnDeviceAiProviderModelListingPolicy(
                        pinnedProvider = request.pinnedProvider,
                        options = OnDeviceAiProviderModelListingOptions(
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

        private fun applyDecision(decision: OnDeviceAiProviderModelListingDecision) {
            when (decision) {
                is OnDeviceAiProviderModelListingDecision.Dispatch -> queueDispatch(decision.dispatch)
                is OnDeviceAiProviderModelListingDecision.Terminal -> finish(mapOutcome(decision.outcome))
                OnDeviceAiProviderModelListingDecision.Ignored -> {
                    if (!isTerminal()) finish(internalFailure())
                }
            }
        }

        private fun queueDispatch(dispatch: OnDeviceAiProviderModelListingDispatch) {
            val launch = synchronized(lifecycleLock) {
                if (terminal) return
                check(pendingDispatch == null) { "Model-list Dispatch was queued twice" }
                pendingDispatch = dispatch
                takeDispatchIfReadyLocked()
            }
            if (launch != null) launchDispatch(launch)
        }

        private fun launchDispatch(dispatch: OnDeviceAiProviderModelListingDispatch) {
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

        private fun performDispatch(dispatch: OnDeviceAiProviderModelListingDispatch) {
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
        ): OnDeviceAiProviderModelListingCallbackIngress =
            object : OnDeviceAiProviderModelListingCallbackIngress {
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
            if (!OnDeviceAiProviderCallbackCallerPolicy.isExpectedUid(
                    request.pinnedProvider,
                    callingUid,
                )
            ) {
                finishExternal(transcriptRejected(OnDeviceAiProviderModelListingFailure.CALLBACK_UID_MISMATCH))
                return
            }
            val oversized = when {
                envelope == null -> null
                page && envelope.size > OnDeviceAiLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES ->
                    OnDeviceAiProviderModelListingFailure.PAGE_TOO_LARGE
                !page && envelope.size > AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES ->
                    OnDeviceAiProviderModelListingFailure.ERROR_TOO_LARGE
                else -> null
            }
            if (oversized != null) {
                finishExternal(transcriptRejected(oversized))
                return
            }
            var failure: OnDeviceAiProviderModelListingRunFailure? = null
            val launch = synchronized(lifecycleLock) {
                if (terminal) return
                when {
                    sequence !in issuedSequences -> {
                        failure = OnDeviceAiProviderModelListingRunFailure.CALLBACK_BEFORE_DISPATCH
                        null
                    }
                    pendingCallbackEvents.size + (if (callbackAdmissionActive) 1 else 0) >=
                        MAX_BUFFERED_CALLBACK_EVENTS -> {
                        failure = OnDeviceAiProviderModelListingRunFailure.CALLBACK_FLOOD
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
                finishExternal(OnDeviceAiProviderModelListingRunOutcome.Rejected(requireNotNull(failure)))
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
                    OnDeviceAiProviderModelListingRunFailure.IDENTITY_DRIFT,
                )
            }
            if (stopForDeadlineOrTerminal()) return CallbackAdmissionResult.Abandoned
            val listingPolicy = policy ?: return CallbackAdmissionResult.Failed(
                OnDeviceAiProviderModelListingRunFailure.INTERNAL_FAILURE,
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
                    OnDeviceAiProviderModelListingRunOutcome.Rejected(result.failure),
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

        private fun takeDispatchIfReadyLocked(): OnDeviceAiProviderModelListingDispatch? {
            if (terminal || dispatchFlowActive) return null
            val dispatch = pendingDispatch ?: return null
            pendingDispatch = null
            dispatchFlowActive = true
            return dispatch
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

        fun cancel() {
            finishExternal(OnDeviceAiProviderModelListingRunOutcome.Cancelled)
        }

        private fun timeout() {
            if (!deadlineExpired()) return
            val decision = claimTerminal(
                requested = OnDeviceAiProviderModelListingRunOutcome.TimedOut,
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

        private fun finish(outcome: OnDeviceAiProviderModelListingRunOutcome) {
            finish(outcome, allowEarlyDelivery = false)
        }

        private fun finishExternal(outcome: OnDeviceAiProviderModelListingRunOutcome) {
            finish(outcome, allowEarlyDelivery = true)
        }

        private fun finish(
            outcome: OnDeviceAiProviderModelListingRunOutcome,
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
                    decision.outcome === OnDeviceAiProviderModelListingRunOutcome.Cancelled ||
                    allowEarlyDelivery && decision.retainDeadline,
                retainDeadline = decision.retainDeadline,
            )
        }

        private fun claimTerminal(
            requested: OnDeviceAiProviderModelListingRunOutcome,
            retainActiveWatchdog: Boolean,
        ): TerminalDecision? = synchronized(lifecycleLock) {
            if (terminal) return@synchronized null
            val timedOut = deadlineExpired()
            val outcome = if (timedOut) OnDeviceAiProviderModelListingRunOutcome.TimedOut else requested
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
            outcome: OnDeviceAiProviderModelListingRunOutcome,
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
            var outcome: OnDeviceAiProviderModelListingRunOutcome? = null
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
            outcome: OnDeviceAiProviderModelListingOutcome,
        ): OnDeviceAiProviderModelListingRunOutcome = when (outcome) {
            OnDeviceAiProviderModelListingOutcome.Disabled -> OnDeviceAiProviderModelListingRunOutcome.Disabled
            OnDeviceAiProviderModelListingOutcome.Cancelled -> OnDeviceAiProviderModelListingRunOutcome.Cancelled
            OnDeviceAiProviderModelListingOutcome.TimedOut -> OnDeviceAiProviderModelListingRunOutcome.TimedOut
            OnDeviceAiProviderModelListingOutcome.BinderDied -> OnDeviceAiProviderModelListingRunOutcome.BinderDied
            is OnDeviceAiProviderModelListingOutcome.Completed ->
                OnDeviceAiProviderModelListingRunOutcome.Completed(outcome.catalog)
            is OnDeviceAiProviderModelListingOutcome.ProviderFailed ->
                OnDeviceAiProviderModelListingRunOutcome.ProviderFailed(outcome.error)
            is OnDeviceAiProviderModelListingOutcome.Rejected ->
                OnDeviceAiProviderModelListingRunOutcome.Rejected(
                    failure = OnDeviceAiProviderModelListingRunFailure.TRANSCRIPT_REJECTED,
                    transcriptFailure = outcome.failure,
                )
        }

        private fun identityDrift() = OnDeviceAiProviderModelListingRunOutcome.Rejected(
            OnDeviceAiProviderModelListingRunFailure.IDENTITY_DRIFT,
        )

        private fun executorRejected() = OnDeviceAiProviderModelListingRunOutcome.Rejected(
            OnDeviceAiProviderModelListingRunFailure.EXECUTOR_REJECTED,
        )

        private fun internalFailure() = OnDeviceAiProviderModelListingRunOutcome.Rejected(
            OnDeviceAiProviderModelListingRunFailure.INTERNAL_FAILURE,
        )

        private fun remoteSecurity() = OnDeviceAiProviderModelListingRunOutcome.Rejected(
            OnDeviceAiProviderModelListingRunFailure.REMOTE_SECURITY,
        )

        private fun remoteFailure() = OnDeviceAiProviderModelListingRunOutcome.Rejected(
            OnDeviceAiProviderModelListingRunFailure.REMOTE_FAILURE,
        )

        private fun transcriptRejected(
            failure: OnDeviceAiProviderModelListingFailure,
        ) = OnDeviceAiProviderModelListingRunOutcome.Rejected(
            failure = OnDeviceAiProviderModelListingRunFailure.TRANSCRIPT_REJECTED,
            transcriptFailure = failure,
        )

        private fun saturatingAdd(left: Long, right: Long): Long =
            if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
    }

    private sealed class MetadataReadResult {
        object Abandoned : MetadataReadResult()
        data class Failed(val failure: OnDeviceAiProviderModelListingRunFailure) : MetadataReadResult()
        data class Complete(val rpc: OnDeviceAiProviderModelListingRpc) : MetadataReadResult()
    }

    private sealed class MetadataValue<out T> {
        data class Failed(val failure: OnDeviceAiProviderModelListingRunFailure) : MetadataValue<Nothing>()
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
        data class Failed(val failure: OnDeviceAiProviderModelListingRunFailure) : CallbackAdmissionResult()
        data class Decided(
            val decision: OnDeviceAiProviderModelListingDecision,
        ) : CallbackAdmissionResult()
    }

    private data class TerminalDecision(
        val outcome: OnDeviceAiProviderModelListingRunOutcome,
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
            callback: (OnDeviceAiProviderModelListingRunOutcome) -> Unit,
            outcome: OnDeviceAiProviderModelListingRunOutcome,
        ) {
            runCatching { callback(outcome) }
        }
    }
}

private fun OnDeviceAiProviderPackageSnapshot.matchesPinnedPackage(
    pinned: OnDeviceAiProviderPinnedIdentity,
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
