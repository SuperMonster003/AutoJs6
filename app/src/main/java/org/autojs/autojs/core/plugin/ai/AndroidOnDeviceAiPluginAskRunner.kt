package org.autojs.autojs.core.plugin.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.DeadObjectException
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiProviderInfo
import org.autojs.plugin.ondeviceai.api.AiCompletionResult
import org.autojs.plugin.ondeviceai.api.AiContentPart
import org.autojs.plugin.ondeviceai.api.AiFinishReason
import org.autojs.plugin.ondeviceai.api.AiSessionStarted
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilityId
import org.autojs.plugin.ondeviceai.api.AiMessage
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilities
import org.autojs.plugin.ondeviceai.api.IOnDeviceAiCallback
import org.autojs.plugin.ondeviceai.api.IOnDeviceAiProvider
import org.autojs.plugin.ondeviceai.api.IOnDeviceAiSession
import org.autojs.plugin.ondeviceai.api.OnDeviceAiChunk
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCodec
import org.autojs.plugin.ondeviceai.api.OnDeviceAiLimits
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
import org.autojs.plugin.ondeviceai.api.OnDeviceAiRequest
import org.autojs.plugin.ondeviceai.api.OnDeviceAiSessionPolicy
import java.io.Closeable
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal fun OnDeviceAiPluginAskRequest.toProtocolMessages(): List<AiMessage> =
    messages.toProtocolMessages()

private fun List<OnDeviceAiPluginMessage>.toProtocolMessages(): List<AiMessage> = map { message ->
    val contentBytes = message.content.toByteArray(Charsets.UTF_8)
    check(contentBytes.size == message.contentBytes) { "AI plugin message UTF-8 size changed" }
    AiMessage(
        role = message.role.protocolValue,
        parts = listOf(
            AiContentPart(
                AiPayloadReference(
                    mimeType = OnDeviceAiMimeType.PLAIN,
                    declaredLengthBytes = contentBytes.size.toLong(),
                    inlineBytes = contentBytes,
                    charset = "utf-8",
                ),
            ),
        ),
    )
}

internal fun onDeviceAiPluginCapabilityRequirement(
    request: OnDeviceAiPluginAskRequest,
    streaming: Boolean,
    persistentSession: Boolean = false,
): OnDeviceAiCapabilityRequirement = OnDeviceAiCapabilityRequirement(
    requiredCapabilityIds = buildSet {
        add(OnDeviceAiCapabilityId.USAGE)
        if (streaming) add(OnDeviceAiCapabilityId.STREAMING)
        if (persistentSession) add(OnDeviceAiCapabilityId.PERSISTENT_SESSION)
        if (request.structuredJson) add(OnDeviceAiCapabilityId.STRUCTURED_JSON)
    },
    minimumMessages = request.messages.size,
    minimumContentParts = request.messages.size,
    minimumContextBytes = request.inputBytes,
    minimumOutputBytes = 1L,
    minimumRequestDescriptors = 0,
    minimumSessionDescriptors = 0,
    requiredTextMimeTypes = buildSet {
        add(OnDeviceAiMimeType.PLAIN)
        if (request.structuredJson) add(OnDeviceAiMimeType.JSON)
    },
    requiredSchemaMimeTypes = if (request.structuredJson) {
        setOf(OnDeviceAiMimeType.JSON)
    } else {
        emptySet()
    },
)

/**
 * Lazily initialized process owner for the explicit, default-off Android on-device AI plugin route.
 * Merely constructing a script runtime does not discover or bind a provider.
 */
internal class AndroidOnDeviceAiPluginAskRunner private constructor(
    private val applicationContext: Context,
) : OnDeviceAiPluginAskRunner,
    OnDeviceAiPluginStreamRunner,
    OnDeviceAiPluginModelListRunner,
    OnDeviceAiPluginSessionRunner {

    override fun start(
        request: OnDeviceAiPluginAskRequest,
        onOutcome: (OnDeviceAiPluginAskOutcome) -> Unit,
    ): OnDeviceAiPluginAskHandle = processBackend(applicationContext).start(request, onOutcome)

    override fun startStream(
        request: OnDeviceAiPluginAskRequest,
        onEvent: (OnDeviceAiPluginStreamEvent) -> Unit,
    ): OnDeviceAiPluginAskHandle = processBackend(applicationContext).startStream(request, onEvent)

    override fun listModels(
        request: OnDeviceAiPluginModelListRequest,
        onOutcome: (OnDeviceAiPluginModelListOutcome) -> Unit,
    ): OnDeviceAiPluginAskHandle = processBackend(applicationContext).listModels(request, onOutcome)

    override fun openSession(
        request: OnDeviceAiPluginAskRequest,
        onOutcome: (OnDeviceAiPluginSessionOpenOutcome) -> Unit,
    ): OnDeviceAiPluginAskHandle = processBackend(applicationContext).openSession(request, onOutcome)

    companion object {
        fun create(context: Context): OnDeviceAiPluginAskRunner =
            AndroidOnDeviceAiPluginAskRunner(context.applicationContext)

        fun createStream(context: Context): OnDeviceAiPluginStreamRunner =
            AndroidOnDeviceAiPluginAskRunner(context.applicationContext)

        fun createModelList(context: Context): OnDeviceAiPluginModelListRunner =
            AndroidOnDeviceAiPluginAskRunner(context.applicationContext)

        fun createSession(context: Context): OnDeviceAiPluginSessionRunner =
            AndroidOnDeviceAiPluginAskRunner(context.applicationContext)

        private val backendLock = Any()

        @Volatile
        private var sharedBackend: ProcessOnDeviceAiPluginAskRunner? = null

        private fun processBackend(context: Context): ProcessOnDeviceAiPluginAskRunner {
            sharedBackend?.let { return it }
            return synchronized(backendLock) {
                sharedBackend ?: createProcessBackend(context).also { sharedBackend = it }
            }
        }

        private fun createProcessBackend(context: Context): ProcessOnDeviceAiPluginAskRunner =
            ProcessOnDeviceAiPluginAskRunner(context.applicationContext)
    }
}

private class ProcessOnDeviceAiPluginAskRunner(
    private val applicationContext: Context,
) : OnDeviceAiPluginAskRunner,
    OnDeviceAiPluginStreamRunner,
    OnDeviceAiPluginModelListRunner,
    OnDeviceAiPluginSessionRunner {
    private val admission = AtomicBoolean(false)
    private val threadFactory = OnDeviceAiPluginAskThreadFactory()
    private val orchestrationExecutor =
        Executors.newSingleThreadExecutor(threadFactory.named("orchestration"))
    private val coordinatorExecutor =
        Executors.newSingleThreadExecutor(threadFactory.named("control"))
    private val terminalFallbackExecutor =
        Executors.newSingleThreadExecutor(threadFactory.named("terminal"))
    private val blockingExecutor = ThreadPoolExecutor(
        0,
        MAXIMUM_BLOCKING_OPERATIONS,
        30L,
        TimeUnit.SECONDS,
        SynchronousQueue(),
        threadFactory.named("binder"),
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val scheduler =
        ScheduledThreadPoolExecutor(1, threadFactory.named("deadline")).apply {
            removeOnCancelPolicy = true
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
        }
    private val discovery = AndroidOnDeviceAiProviderDiscovery(applicationContext)
    private val environment = OnDeviceAiProviderEnvironment(
        hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
        currentAbi = Build.SUPPORTED_ABIS.firstOrNull(),
        releaseBuild = !BuildConfig.DEBUG,
        allowUserApprovedProviders = false,
    )
    private val handshake = OnDeviceAiProviderHandshakeCoordinator(
        transport = AndroidOnDeviceAiProviderHandshake(applicationContext),
        workerExecutor = coordinatorExecutor,
        blockingExecutor = blockingExecutor,
        deadlineScheduler = OnDeviceAiProviderHandshakeDeadlineScheduler(::schedule),
        clock = OnDeviceAiProviderHandshakeClock(::now),
    )
    private val modelListing = OnDeviceAiProviderModelListingCoordinator(
        transport = AndroidOnDeviceAiProviderModelListing(applicationContext),
        workerExecutor = coordinatorExecutor,
        blockingExecutor = blockingExecutor,
        deadlineScheduler = OnDeviceAiProviderModelListingDeadlineScheduler(::schedule),
        clock = OnDeviceAiProviderModelListingClock(::now),
        owner = OnDeviceAiProviderModelListingProcessOwner(),
    )
    private val session = OnDeviceAiProviderSessionCoordinator(
        transport = AndroidOnDeviceAiProviderSession(applicationContext),
        workerExecutor = coordinatorExecutor,
        blockingExecutor = blockingExecutor,
        terminalFallbackExecutor = terminalFallbackExecutor,
        deadlineScheduler = OnDeviceAiProviderSessionDeadlineScheduler(::schedule),
        clock = OnDeviceAiProviderSessionClock(::now),
        owner = OnDeviceAiProviderSessionProcessOwner(),
    )

    override fun start(
        request: OnDeviceAiPluginAskRequest,
        onOutcome: (OnDeviceAiPluginAskOutcome) -> Unit,
    ): OnDeviceAiPluginAskHandle {
        if (!admission.compareAndSet(false, true)) {
            deliver {
                onOutcome(OnDeviceAiPluginAskOutcome.Failed(OnDeviceAiPluginAskFailure.BUSY))
            }
            return CompletedOnDeviceAiPluginAskHandle
        }
        return Attempt(
            backend = this,
            request = request,
            streaming = false,
            onOutcome = onOutcome,
            onStreamEvent = null,
        ).also(Attempt::start)
    }

    override fun startStream(
        request: OnDeviceAiPluginAskRequest,
        onEvent: (OnDeviceAiPluginStreamEvent) -> Unit,
    ): OnDeviceAiPluginAskHandle {
        if (!admission.compareAndSet(false, true)) {
            deliver { onEvent(OnDeviceAiPluginStreamEvent.Failed(OnDeviceAiPluginAskFailure.BUSY)) }
            return CompletedOnDeviceAiPluginAskHandle
        }
        return Attempt(
            backend = this,
            request = request,
            streaming = true,
            onOutcome = null,
            onStreamEvent = onEvent,
        ).also(Attempt::start)
    }

    override fun listModels(
        request: OnDeviceAiPluginModelListRequest,
        onOutcome: (OnDeviceAiPluginModelListOutcome) -> Unit,
    ): OnDeviceAiPluginAskHandle {
        if (!admission.compareAndSet(false, true)) {
            deliver { onOutcome(OnDeviceAiPluginModelListOutcome.Failed(OnDeviceAiPluginAskFailure.BUSY)) }
            return CompletedOnDeviceAiPluginAskHandle
        }
        return Attempt(
            backend = this,
            request = OnDeviceAiPluginAskRequest(
                component = request.component,
                providerId = request.providerId,
                modelId = null,
                messages = listOf(
                    OnDeviceAiPluginMessage(
                        role = OnDeviceAiPluginMessageRole.USER,
                        content = MODEL_LIST_PLACEHOLDER_PROMPT,
                    ),
                ),
                timeoutMillis = request.timeoutMillis,
            ),
            streaming = false,
            onOutcome = null,
            onStreamEvent = null,
            onModelList = onOutcome,
        ).also(Attempt::start)
    }

    override fun openSession(
        request: OnDeviceAiPluginAskRequest,
        onOutcome: (OnDeviceAiPluginSessionOpenOutcome) -> Unit,
    ): OnDeviceAiPluginAskHandle {
        if (!admission.compareAndSet(false, true)) {
            deliver {
                onOutcome(OnDeviceAiPluginSessionOpenOutcome.Failed(OnDeviceAiPluginAskFailure.BUSY))
            }
            return CompletedOnDeviceAiPluginAskHandle
        }
        return Attempt(
            backend = this,
            request = request,
            streaming = false,
            onOutcome = null,
            onStreamEvent = null,
            onSessionOpen = onOutcome,
        ).also(Attempt::start)
    }

    private fun deliver(action: () -> Unit) {
        if (!execute(orchestrationExecutor) { runCatching(action) }) runCatching(action)
    }

    private fun releaseAdmission() {
        check(admission.compareAndSet(true, false)) {
            "on-device AI plugin route admission was not acquired"
        }
    }

    private fun executeOrchestration(action: () -> Unit): Boolean =
        execute(orchestrationExecutor, action)

    private fun executeBlocking(action: () -> Unit): Boolean =
        execute(blockingExecutor, action)

    private fun schedule(delayMillis: Long, action: () -> Unit): Closeable {
        val future = scheduler.schedule(action, delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        return Closeable { future.cancel(false) }
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

    private class Attempt(
        private val backend: ProcessOnDeviceAiPluginAskRunner,
        private val request: OnDeviceAiPluginAskRequest,
        private val streaming: Boolean,
        private val onOutcome: ((OnDeviceAiPluginAskOutcome) -> Unit)?,
        private val onStreamEvent: ((OnDeviceAiPluginStreamEvent) -> Unit)?,
        private val onModelList: ((OnDeviceAiPluginModelListOutcome) -> Unit)? = null,
        private val onSessionOpen: ((OnDeviceAiPluginSessionOpenOutcome) -> Unit)? = null,
    ) : OnDeviceAiPluginAskHandle {
        private val lock = Any()
        private val startedAtMillis = backend.now()
        private val deadlineAtMillis = saturatingAdd(startedAtMillis, request.timeoutMillis)
        private val protocolMessages = request.toProtocolMessages()
        private val requirement = onDeviceAiPluginCapabilityRequirement(
            request = request,
            streaming = streaming,
            persistentSession = onSessionOpen != null,
        )

        /** The model the catalog resolved for this attempt; echoed on completion. */
        private var resolvedModelId: String? = request.modelId

        private var terminal = false
        private var stageHandle: Closeable? = null
        private var deadlineHandle: Closeable? = null

        fun start() {
            val deadline = try {
                backend.schedule(request.timeoutMillis) {
                    fail(OnDeviceAiPluginAskFailure.TIMED_OUT)
                }
            } catch (_: Exception) {
                fail(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE)
                return
            }
            installDeadline(deadline)
            if (!PluginEnableStore.isEnabled(backend.applicationContext, request.component.packageName)) {
                fail(OnDeviceAiPluginAskFailure.PROVIDER_DISABLED)
                return
            }
            enqueue(::beginDiscovery)
        }

        override fun cancel(): Boolean =
            fail(OnDeviceAiPluginAskFailure.CANCELLED)

        override fun close() {
            cancel()
        }

        private fun beginDiscovery() {
            if (!isActive()) return
            if (!backend.executeBlocking {
                    val result = inspectInitialSnapshot()
                    enqueue { acceptDiscovery(result) }
                }
            ) {
                fail(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE)
            }
        }

        private fun inspectInitialSnapshot(): InitialDiscoveryResult = try {
            when (
                val result = OnDeviceAiProviderDiscoveryPolicy.inspectExact(
                    source = backend.discovery,
                    component = request.component,
                    options = OnDeviceAiProviderDiscoveryOptions(enabled = true),
                )
            ) {
                OnDeviceAiProviderDiscoveryResult.Disabled,
                OnDeviceAiProviderDiscoveryResult.WrongAction,
                is OnDeviceAiProviderDiscoveryResult.QueryFailed,
                -> InitialDiscoveryResult.Rejected
                is OnDeviceAiProviderDiscoveryResult.Completed -> {
                    if (result.evaluations.isEmpty()) {
                        InitialDiscoveryResult.NotFound
                    } else {
                        val discovered = result.evaluations.singleOrNull()
                            as? OnDeviceAiProviderDiscoveryEvaluation.Discovered
                        discovered?.let { InitialDiscoveryResult.Found(it.snapshot) }
                            ?: InitialDiscoveryResult.Rejected
                    }
                }
            }
        } catch (_: Exception) {
            InitialDiscoveryResult.Rejected
        }

        private fun acceptDiscovery(result: InitialDiscoveryResult) {
            if (!isActive()) return
            when (result) {
                InitialDiscoveryResult.NotFound -> fail(OnDeviceAiPluginAskFailure.PROVIDER_NOT_FOUND)
                InitialDiscoveryResult.Rejected -> fail(OnDeviceAiPluginAskFailure.PROVIDER_REJECTED)
                is InitialDiscoveryResult.Found -> beginHandshake(result.snapshot)
            }
        }

        private fun beginHandshake(snapshot: OnDeviceAiProviderPackageSnapshot) {
            if (!isActive()) return
            val remaining = remainingMillis()
            if (remaining <= 0L) {
                fail(OnDeviceAiPluginAskFailure.TIMED_OUT)
                return
            }
            val handle = try {
                backend.handshake.start(
                    OnDeviceAiProviderHandshakeRequest(
                        snapshot = snapshot,
                        environment = backend.environment,
                        hostCapabilities = HOST_CAPABILITIES,
                        requirement = requirement,
                        options = OnDeviceAiProviderHandshakeOptions(
                            enabled = true,
                            timeoutMillis = remaining,
                        ),
                    ),
                ) { outcome -> enqueue { acceptHandshake(snapshot, outcome) } }
            } catch (_: Exception) {
                fail(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE)
                return
            }
            installStage(handle)
        }

        private fun acceptHandshake(
            snapshot: OnDeviceAiProviderPackageSnapshot,
            outcome: OnDeviceAiProviderHandshakeOutcome,
        ) {
            detachStage()
            if (!isActive()) return
            when (outcome) {
                OnDeviceAiProviderHandshakeOutcome.Busy -> fail(OnDeviceAiPluginAskFailure.BUSY)
                OnDeviceAiProviderHandshakeOutcome.Fused -> fail(OnDeviceAiPluginAskFailure.FUSED)
                OnDeviceAiProviderHandshakeOutcome.Cancelled -> fail(OnDeviceAiPluginAskFailure.CANCELLED)
                OnDeviceAiProviderHandshakeOutcome.Disabled -> fail(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE)
                is OnDeviceAiProviderHandshakeOutcome.Rejected -> fail(
                    when (outcome.failure) {
                        OnDeviceAiProviderHandshakeFailure.BIND_TIMEOUT,
                        OnDeviceAiProviderHandshakeFailure.METADATA_TIMEOUT,
                        -> OnDeviceAiPluginAskFailure.TIMED_OUT
                        OnDeviceAiProviderHandshakeFailure.BINDER_DIED ->
                            OnDeviceAiPluginAskFailure.BINDER_DIED
                        OnDeviceAiProviderHandshakeFailure.EXECUTOR_REJECTED,
                        OnDeviceAiProviderHandshakeFailure.INTERNAL_FAILURE,
                        -> OnDeviceAiPluginAskFailure.INTERNAL_FAILURE
                        else -> OnDeviceAiPluginAskFailure.PROVIDER_REJECTED
                    },
                )
                is OnDeviceAiProviderHandshakeOutcome.Selected -> {
                    val pinned = outcome.pinned
                    if (pinned.component != request.component ||
                        pinned.providerId != request.providerId ||
                        pinned.locality != AiDataLocality.ON_DEVICE ||
                        pinned.credentialMode != AiCredentialMode.NONE
                    ) {
                        fail(OnDeviceAiPluginAskFailure.PROVIDER_REJECTED)
                    } else {
                        beginModelListing(snapshot, pinned)
                    }
                }
            }
        }

        private fun beginModelListing(
            snapshot: OnDeviceAiProviderPackageSnapshot,
            pinned: OnDeviceAiProviderPinnedIdentity,
        ) {
            if (!isActive()) return
            val remaining = remainingMillis()
            if (remaining <= 0L) {
                fail(OnDeviceAiPluginAskFailure.TIMED_OUT)
                return
            }
            val handle = try {
                backend.modelListing.start(
                    OnDeviceAiProviderModelListingRunRequest(
                        snapshot = snapshot,
                        pinnedProvider = pinned,
                        environment = backend.environment,
                        hostCapabilities = HOST_CAPABILITIES,
                        requirement = requirement,
                        options = OnDeviceAiProviderModelListingRunOptions(
                            enabled = true,
                            timeoutMillis = remaining,
                        ),
                    ),
                ) { outcome -> enqueue { acceptModelListing(snapshot, pinned, outcome) } }
            } catch (_: Exception) {
                fail(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE)
                return
            }
            installStage(handle)
        }

        private fun acceptModelListing(
            snapshot: OnDeviceAiProviderPackageSnapshot,
            pinned: OnDeviceAiProviderPinnedIdentity,
            outcome: OnDeviceAiProviderModelListingRunOutcome,
        ) {
            detachStage()
            if (!isActive()) return
            when (outcome) {
                OnDeviceAiProviderModelListingRunOutcome.Disabled ->
                    fail(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE)
                OnDeviceAiProviderModelListingRunOutcome.Busy -> fail(OnDeviceAiPluginAskFailure.BUSY)
                OnDeviceAiProviderModelListingRunOutcome.Fused -> fail(OnDeviceAiPluginAskFailure.FUSED)
                OnDeviceAiProviderModelListingRunOutcome.Cancelled ->
                    fail(OnDeviceAiPluginAskFailure.CANCELLED)
                OnDeviceAiProviderModelListingRunOutcome.TimedOut ->
                    fail(OnDeviceAiPluginAskFailure.TIMED_OUT)
                OnDeviceAiProviderModelListingRunOutcome.BinderDied ->
                    fail(OnDeviceAiPluginAskFailure.BINDER_DIED)
                is OnDeviceAiProviderModelListingRunOutcome.ProviderFailed ->
                    fail(OnDeviceAiPluginAskFailure.PROVIDER_FAILED)
                is OnDeviceAiProviderModelListingRunOutcome.Rejected -> fail(
                    when (outcome.failure) {
                        OnDeviceAiProviderModelListingRunFailure.EXECUTOR_REJECTED,
                        OnDeviceAiProviderModelListingRunFailure.INTERNAL_FAILURE,
                        -> OnDeviceAiPluginAskFailure.INTERNAL_FAILURE
                        else -> OnDeviceAiPluginAskFailure.PROVIDER_REJECTED
                    },
                )
                is OnDeviceAiProviderModelListingRunOutcome.Completed -> {
                    val catalog = outcome.catalog
                    if (catalog.pinnedProvider != pinned ||
                        catalog.pinnedProvider.providerId != request.providerId
                    ) {
                        fail(OnDeviceAiPluginAskFailure.PROVIDER_REJECTED)
                        return
                    }
                    if (onModelList != null) {
                        finishModelList(catalog)
                        return
                    }
                    val plannedModelId = request.modelId ?: run {
                        // An omitted modelId only ever resolves against a single-model catalog;
                        // anything else must stay an explicit, deterministic script decision.
                        when (catalog.models.size) {
                            0 -> {
                                fail(OnDeviceAiPluginAskFailure.MODEL_NOT_FOUND)
                                return
                            }
                            1 -> catalog.models.single().modelId
                            else -> {
                                fail(OnDeviceAiPluginAskFailure.MODEL_AMBIGUOUS)
                                return
                            }
                        }
                    }
                    resolvedModelId = plannedModelId
                    when (val planned = catalog.planExact(plannedModelId, requirement)) {
                        OnDeviceAiProviderExactModelPlanResult.ModelNotFound ->
                            fail(OnDeviceAiPluginAskFailure.MODEL_NOT_FOUND)
                        OnDeviceAiProviderExactModelPlanResult.ModelCapabilityMismatch ->
                            fail(OnDeviceAiPluginAskFailure.MODEL_CAPABILITY_MISMATCH)
                        is OnDeviceAiProviderExactModelPlanResult.Planned -> {
                            val plan = planned.plan
                            if (plan.pinnedProvider != pinned ||
                                plan.pinnedProvider.providerId != request.providerId ||
                                plan.modelId != plannedModelId
                            ) {
                                fail(OnDeviceAiPluginAskFailure.PROVIDER_REJECTED)
                            } else if (onSessionOpen != null) {
                                finishSessionOpen(snapshot, plan)
                            } else {
                                beginSession(snapshot, plan)
                            }
                        }
                    }
                }
            }
        }

        private fun finishModelList(catalog: OnDeviceAiProviderModelCatalog) {
            detachStage()
            val summaries = catalog.models.map { model ->
                OnDeviceAiPluginModelSummary(
                    modelId = model.modelId,
                    displayName = model.displayName,
                    capabilityIds = model.capabilityIds,
                    maximumContextBytes = model.maximumContextBytes,
                    maximumOutputBytes = model.maximumOutputBytes,
                )
            }
            finishList(OnDeviceAiPluginModelListOutcome.Completed(summaries))
        }

        private fun beginSession(
            snapshot: OnDeviceAiProviderPackageSnapshot,
            plan: OnDeviceAiHostSessionPlan,
        ) {
            if (!isActive()) return
            val remaining = remainingMillis()
            if (remaining < OnDeviceAiLimits.MIN_TIMEOUT_MILLIS) {
                fail(OnDeviceAiPluginAskFailure.TIMED_OUT)
                return
            }
            val outputLimit = minOf(request.maximumOutputBytes, plan.effectiveMaximumOutputBytes)
            val typedRequest = try {
                OnDeviceAiRequest(
                    requestId = "${if (streaming) "stream" else "ask"}-${UUID.randomUUID()}",
                    protocolVersion = plan.pinnedProvider.protocolVersion,
                    providerId = request.providerId,
                    modelId = plan.modelId,
                    messages = protocolMessages,
                    options = request.toGenerationOptions(
                        stream = streaming,
                        maximumOutputBytes = outputLimit,
                        timeoutMillis = remaining.coerceAtMost(OnDeviceAiLimits.MAX_TIMEOUT_MILLIS),
                        requiredCapabilityIds = buildList {
                            add(OnDeviceAiCapabilityId.USAGE)
                            if (streaming) add(OnDeviceAiCapabilityId.STREAMING)
                            if (request.structuredJson) add(OnDeviceAiCapabilityId.STRUCTURED_JSON)
                        },
                    ),
                )
            } catch (_: Exception) {
                fail(OnDeviceAiPluginAskFailure.INVALID_REQUEST)
                return
            }
            val descriptors = AndroidOnDeviceAiProviderSessionRequestDescriptors(emptyArray())
            val handle = try {
                backend.session.start(
                    OnDeviceAiProviderSessionRunRequest(
                        snapshot = snapshot,
                        plan = plan,
                        request = typedRequest,
                        requestDescriptors = descriptors,
                        environment = backend.environment,
                        hostCapabilities = HOST_CAPABILITIES,
                        requirement = requirement,
                        options = OnDeviceAiProviderSessionRunOptions(enabled = true),
                    ),
                ) { event -> enqueue { acceptSessionEvent(event) } }
            } catch (_: Exception) {
                descriptors.close()
                fail(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE)
                return
            }
            installStage(handle)
        }

        private fun acceptSessionEvent(event: OnDeviceAiProviderSessionRunEvent) {
            if (!isActive()) return
            when (event) {
                OnDeviceAiProviderSessionRunEvent.Busy -> terminalSession(OnDeviceAiPluginAskFailure.BUSY)
                OnDeviceAiProviderSessionRunEvent.Fused -> terminalSession(OnDeviceAiPluginAskFailure.FUSED)
                is OnDeviceAiProviderSessionRunEvent.Rejected ->
                    terminalSession(OnDeviceAiPluginAskFailure.SESSION_REJECTED)
                is OnDeviceAiProviderSessionRunEvent.Transcript -> when (val transcript = event.event) {
                    OnDeviceAiProviderSessionEvent.Disabled ->
                        terminalSession(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE)
                    is OnDeviceAiProviderSessionEvent.Started -> {
                        if (streaming) publish(OnDeviceAiPluginStreamEvent.Opened)
                    }
                    is OnDeviceAiProviderSessionEvent.Chunk -> {
                        if (!streaming || transcript.chunk.textDelta.isEmpty()) {
                            terminalSession(OnDeviceAiPluginAskFailure.SESSION_REJECTED)
                        } else {
                            publish(OnDeviceAiPluginStreamEvent.Chunk(transcript.chunk.textDelta))
                        }
                    }
                    is OnDeviceAiProviderSessionEvent.Usage -> {
                        val usage = transcript.usage.toPluginUsage()
                        if (streaming) publish(OnDeviceAiPluginStreamEvent.Usage(usage))
                    }
                    is OnDeviceAiProviderSessionEvent.Completed -> {
                        val usage = transcript.result.finalUsage?.toPluginUsage()
                        if (usage == null) {
                            terminalSession(OnDeviceAiPluginAskFailure.SESSION_REJECTED)
                            return
                        }
                        detachStage()
                        finish(
                            OnDeviceAiPluginAskOutcome.Completed(
                                text = transcript.result.outputText,
                                usage = usage,
                                modelId = resolvedModelId,
                            ),
                        )
                    }
                    is OnDeviceAiProviderSessionEvent.ProviderFailed ->
                        terminalSession(OnDeviceAiPluginAskFailure.PROVIDER_FAILED)
                    OnDeviceAiProviderSessionEvent.Cancelled ->
                        terminalSession(OnDeviceAiPluginAskFailure.CANCELLED)
                    OnDeviceAiProviderSessionEvent.TimedOut ->
                        terminalSession(OnDeviceAiPluginAskFailure.TIMED_OUT)
                    OnDeviceAiProviderSessionEvent.BinderDied ->
                        terminalSession(OnDeviceAiPluginAskFailure.BINDER_DIED)
                    is OnDeviceAiProviderSessionEvent.Rejected ->
                        terminalSession(OnDeviceAiPluginAskFailure.SESSION_REJECTED)
                }
            }
        }

        private fun publish(event: OnDeviceAiPluginStreamEvent) {
            if (!streaming || !isActive()) return
            checkNotNull(onStreamEvent).invoke(event)
        }

        private fun terminalSession(failure: OnDeviceAiPluginAskFailure) {
            detachStage()
            fail(failure)
        }

        private fun fail(failure: OnDeviceAiPluginAskFailure): Boolean =
            if (onSessionOpen != null) {
                finishSessionOpenFailure(failure)
            } else {
                finish(OnDeviceAiPluginAskOutcome.Failed(failure))
            }

        private fun enqueue(action: () -> Unit) {
            if (!backend.executeOrchestration {
                    if (!isActive()) return@executeOrchestration
                    try {
                        action()
                    } catch (_: Exception) {
                        fail(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE)
                    }
                }
            ) {
                fail(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE)
            }
        }

        private fun installStage(handle: Closeable) {
            val closeNow = synchronized(lock) {
                if (terminal) {
                    true
                } else {
                    check(stageHandle == null) { "on-device AI plugin route stage overlap" }
                    stageHandle = handle
                    false
                }
            }
            if (closeNow) runCatching { handle.close() }
        }

        private fun detachStage() {
            val handle = synchronized(lock) {
                stageHandle.also { stageHandle = null }
            }
            handle?.let { runCatching { it.close() } }
        }

        private fun installDeadline(handle: Closeable) {
            val closeNow = synchronized(lock) {
                if (terminal) {
                    true
                } else {
                    check(deadlineHandle == null) { "on-device AI plugin route deadline overlap" }
                    deadlineHandle = handle
                    false
                }
            }
            if (closeNow) runCatching { handle.close() }
        }

        private fun finish(outcome: OnDeviceAiPluginAskOutcome): Boolean {
            if (!markTerminalAndRelease()) return false
            val onModelList = onModelList
            when {
                onModelList != null -> {
                    val listOutcome = when (outcome) {
                        // A completed ask outcome cannot reach a model-list attempt; map it
                        // defensively instead of throwing inside terminal delivery.
                        is OnDeviceAiPluginAskOutcome.Completed ->
                            OnDeviceAiPluginModelListOutcome.Failed(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE)
                        is OnDeviceAiPluginAskOutcome.Failed ->
                            OnDeviceAiPluginModelListOutcome.Failed(outcome.failure)
                    }
                    runCatching { checkNotNull(onModelList).invoke(listOutcome) }
                }
                streaming -> {
                    val event = when (outcome) {
                        is OnDeviceAiPluginAskOutcome.Completed ->
                            OnDeviceAiPluginStreamEvent.Completed(outcome.text, outcome.usage)
                        is OnDeviceAiPluginAskOutcome.Failed ->
                            OnDeviceAiPluginStreamEvent.Failed(outcome.failure)
                    }
                    runCatching { checkNotNull(onStreamEvent).invoke(event) }
                }
                else -> runCatching { checkNotNull(onOutcome).invoke(outcome) }
            }
            return true
        }

        private fun finishList(outcome: OnDeviceAiPluginModelListOutcome): Boolean {
            if (!markTerminalAndRelease()) return false
            runCatching { checkNotNull(onModelList).invoke(outcome) }
            return true
        }

        private fun finishSessionOpen(
            snapshot: OnDeviceAiProviderPackageSnapshot,
            plan: OnDeviceAiHostSessionPlan,
        ): Boolean {
            if (!markTerminalAndRelease(releaseAdmission = false)) return false
            val session = try {
                ProcessOnDeviceAiPluginSession(
                    backend = backend,
                    snapshot = snapshot,
                    plan = plan,
                    request = request,
                )
            } catch (_: Throwable) {
                backend.releaseAdmission()
                runCatching {
                    checkNotNull(onSessionOpen).invoke(
                        OnDeviceAiPluginSessionOpenOutcome.Failed(
                            OnDeviceAiPluginAskFailure.INTERNAL_FAILURE,
                        ),
                    )
                }
                return true
            }
            val delivered = runCatching {
                checkNotNull(onSessionOpen).invoke(OnDeviceAiPluginSessionOpenOutcome.Completed(session))
            }.isSuccess
            if (!delivered) session.close()
            return true
        }

        private fun finishSessionOpenFailure(failure: OnDeviceAiPluginAskFailure): Boolean {
            if (!markTerminalAndRelease()) return false
            runCatching {
                checkNotNull(onSessionOpen).invoke(OnDeviceAiPluginSessionOpenOutcome.Failed(failure))
            }
            return true
        }

        private fun markTerminalAndRelease(releaseAdmission: Boolean = true): Boolean {
            val resources = synchronized(lock) {
                if (terminal) return false
                terminal = true
                val currentStage = stageHandle
                val currentDeadline = deadlineHandle
                stageHandle = null
                deadlineHandle = null
                currentStage to currentDeadline
            }
            resources.first?.let { runCatching { it.close() } }
            resources.second?.let { runCatching { it.close() } }
            if (releaseAdmission) backend.releaseAdmission()
            return true
        }

        private fun isActive(): Boolean = synchronized(lock) { !terminal }

        private fun remainingMillis(): Long = deadlineAtMillis - backend.now()
    }

    /**
     * One planned, process-admitted persistent plugin session. The provider binding and remote
     * Binder are opened lazily by the first turn, then retained until [close].
     */
    private class ProcessOnDeviceAiPluginSession(
        private val backend: ProcessOnDeviceAiPluginAskRunner,
        private val snapshot: OnDeviceAiProviderPackageSnapshot,
        private val plan: OnDeviceAiHostSessionPlan,
        private val request: OnDeviceAiPluginAskRequest,
    ) : OnDeviceAiPluginSession {
        private val lock = Any()
        private val released = AtomicBoolean(false)
        private var closed = false
        private var activeTurn: Turn? = null
        private var binding: ExactBinding? = null
        private var remote: RemoteSession? = null
        private var persistentSessionId: String? = null

        override val providerId: String
            get() = request.providerId

        override val modelId: String
            get() = plan.modelId

        override val isClosed: Boolean
            get() = synchronized(lock) { closed }

        private val callback = object : IOnDeviceAiCallback.Stub() {
            override fun onStarted(metadata: ByteArray?) =
                forward(CallbackEvent.Started(metadata?.copyOf()))

            override fun onChunk(chunk: ByteArray?) =
                forward(CallbackEvent.Chunk(chunk?.copyOf()))

            override fun onToolCalls(
                calls: ByteArray?,
                descriptors: Array<out ParcelFileDescriptor>?,
            ) {
                closeAndroidOnDeviceAiProviderSessionIncomingDescriptors(descriptors)
                forward(CallbackEvent.Unsupported)
            }

            override fun onUsage(usage: ByteArray?) =
                forward(CallbackEvent.Usage(usage?.copyOf()))

            override fun onCompleted(
                result: ByteArray?,
                descriptors: Array<out ParcelFileDescriptor>?,
            ) {
                val incoming = try {
                    descriptors?.let {
                        AndroidOnDeviceAiProviderSessionIncomingDescriptors(
                            it,
                            NO_OP_OPERATION_OBSERVER,
                        )
                    }
                } catch (_: Throwable) {
                    closeAndroidOnDeviceAiProviderSessionIncomingDescriptors(descriptors)
                    forward(CallbackEvent.Unsupported)
                    return
                }
                forward(CallbackEvent.Completed(result?.copyOf(), incoming))
            }

            override fun onFailed(error: ByteArray?) =
                forward(CallbackEvent.Failed(error?.copyOf()))

            override fun onCancelled() = forward(CallbackEvent.Cancelled)

            private fun forward(event: CallbackEvent) {
                val callingUid = Binder.getCallingUid()
                if (!backend.executeOrchestration { acceptCallback(callingUid, event) }) {
                    event.close()
                    closeWithFailure(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE)
                }
            }
        }

        override fun startTurn(
            message: OnDeviceAiPluginMessage,
            streaming: Boolean,
            onOutcome: ((OnDeviceAiPluginAskOutcome) -> Unit)?,
            onStreamEvent: ((OnDeviceAiPluginStreamEvent) -> Unit)?,
        ): OnDeviceAiPluginAskHandle {
            require(message.role == OnDeviceAiPluginMessageRole.USER) {
                "A persistent on-device AI turn must contain one user message"
            }
            require((streaming && onOutcome == null && onStreamEvent != null) ||
                (!streaming && onOutcome != null && onStreamEvent == null)
            ) { "Persistent on-device AI turn callback shape is invalid" }
            val turn = synchronized(lock) {
                when {
                    closed -> null
                    activeTurn != null -> BUSY_TURN
                    else -> Turn(
                        owner = this,
                        message = message,
                        firstTurn = remote == null,
                        streaming = streaming,
                        onOutcome = onOutcome,
                        onStreamEvent = onStreamEvent,
                    ).also { activeTurn = it }
                }
            }
            when (turn) {
                null -> deliverImmediate(
                    streaming,
                    onOutcome,
                    onStreamEvent,
                    OnDeviceAiPluginAskFailure.SESSION_CLOSED,
                )
                BUSY_TURN -> deliverImmediate(
                    streaming,
                    onOutcome,
                    onStreamEvent,
                    OnDeviceAiPluginAskFailure.BUSY,
                )
                else -> {
                    check(turn is Turn)
                    turn.start()
                    return turn
                }
            }
            return CompletedOnDeviceAiPluginAskHandle
        }

        override fun close() {
            val detached = synchronized(lock) {
                if (closed) return
                closed = true
                val turn = activeTurn.also { activeTurn = null }
                DetachedResources(turn, binding.also { binding = null }, remote.also { remote = null })
            }
            detached.turn?.finishDetached(OnDeviceAiPluginAskFailure.SESSION_CLOSED)
            cleanup(detached.binding, detached.remote, cancelRemote = false)
        }

        private fun deliverImmediate(
            streaming: Boolean,
            onOutcome: ((OnDeviceAiPluginAskOutcome) -> Unit)?,
            onStreamEvent: ((OnDeviceAiPluginStreamEvent) -> Unit)?,
            failure: OnDeviceAiPluginAskFailure,
        ) {
            backend.deliver {
                if (streaming) {
                    runCatching { checkNotNull(onStreamEvent).invoke(OnDeviceAiPluginStreamEvent.Failed(failure)) }
                } else {
                    runCatching { checkNotNull(onOutcome).invoke(OnDeviceAiPluginAskOutcome.Failed(failure)) }
                }
            }
        }

        private fun begin(turn: Turn) {
            if (!isCurrent(turn)) return
            if (turn.firstTurn) beginFirstTurn(turn) else beginNextTurn(turn)
        }

        private fun beginFirstTurn(turn: Turn) {
            val exactBinding = ExactBinding(this)
            val installed = synchronized(lock) {
                if (closed || activeTurn !== turn || binding != null || remote != null) false else {
                    binding = exactBinding
                    true
                }
            }
            if (!installed) {
                turn.fail(OnDeviceAiPluginAskFailure.SESSION_REJECTED, closeSession = true)
                return
            }
            if (!backend.executeBlocking {
                    try {
                        if (!isCurrent(turn) || !reinspectionMatches()) {
                            turn.failAsync(OnDeviceAiPluginAskFailure.PROVIDER_REJECTED)
                        } else {
                            exactBinding.bind()
                        }
                    } catch (_: SecurityException) {
                        turn.failAsync(OnDeviceAiPluginAskFailure.PROVIDER_REJECTED)
                    } catch (_: Throwable) {
                        turn.failAsync(OnDeviceAiPluginAskFailure.PROVIDER_FAILED)
                    }
                }
            ) {
                turn.fail(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE, closeSession = true)
            }
        }

        private fun beginNextTurn(turn: Turn) {
            val currentRemote = synchronized(lock) {
                remote.takeIf { !closed && activeTurn === turn }
            }
            if (currentRemote == null) {
                turn.fail(OnDeviceAiPluginAskFailure.SESSION_CLOSED, closeSession = true)
                return
            }
            turn.remoteReady()
            if (!backend.executeBlocking {
                    try {
                        currentRemote.generateNext(turn.requestEnvelope)
                    } catch (_: DeadObjectException) {
                        remoteDied()
                    } catch (_: Throwable) {
                        turn.failAsync(OnDeviceAiPluginAskFailure.PROVIDER_FAILED)
                    }
                }
            ) {
                turn.fail(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE, closeSession = true)
            }
        }

        private fun providerConnected(providerBinder: IBinder) {
            val turn = synchronized(lock) { activeTurn?.takeIf { !closed && it.firstTurn } } ?: return
            if (!backend.executeBlocking {
                    val opened = try {
                        val provider = verifiedProvider(providerBinder)
                        val remoteSession = requireNotNull(
                            provider.openSession(
                                turn.requestEnvelope.copyOf(),
                                emptyArray(),
                                callback,
                            ),
                        ) { "on-device AI provider returned no persistent session" }
                        RemoteSession.create(remoteSession, ::remoteDied)
                    } catch (_: DeadObjectException) {
                        remoteDied()
                        return@executeBlocking
                    } catch (_: SecurityException) {
                        turn.failAsync(OnDeviceAiPluginAskFailure.PROVIDER_REJECTED)
                        return@executeBlocking
                    } catch (_: Throwable) {
                        turn.failAsync(OnDeviceAiPluginAskFailure.PROVIDER_FAILED)
                        return@executeBlocking
                    }
                    if (!backend.executeOrchestration { installRemote(turn, opened) }) opened.close()
                }
            ) {
                turn.fail(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE, closeSession = true)
            }
        }

        private fun installRemote(turn: Turn, opened: RemoteSession) {
            val installed = synchronized(lock) {
                if (closed || activeTurn !== turn || remote != null) false else {
                    remote = opened
                    true
                }
            }
            if (!installed) {
                opened.close()
                return
            }
            turn.remoteReady()
        }

        private fun verifiedProvider(binder: IBinder): IOnDeviceAiProvider {
            check(binder.isBinderAlive) { "on-device AI provider Binder is dead" }
            check(binder.interfaceDescriptor == IOnDeviceAiProvider.DESCRIPTOR) {
                "on-device AI provider descriptor mismatch"
            }
            val provider = checkNotNull(IOnDeviceAiProvider.Stub.asInterface(binder))
            val infoEnvelope = checkNotNull(provider.providerInfo)
            require(infoEnvelope.size <= AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES)
            val capabilitiesEnvelope = checkNotNull(provider.capabilities)
            require(capabilitiesEnvelope.size <= AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES)
            val providerInfo = AiCommonCodec.decodeProviderInfo(infoEnvelope)
            val capabilities = OnDeviceAiCodec.decodeCapabilities(capabilitiesEnvelope)
            val current = reinspectionSnapshot()
                ?: throw IllegalStateException("on-device AI provider identity drifted")
            require(snapshot.samePackageIdentityForPersistentSession(current))
            val candidate = current.toPersistentSessionCandidate(providerInfo, capabilities)
            require(
                OnDeviceAiProviderSelectionPolicy.revalidate(
                    pinned = plan.pinnedProvider,
                    current = candidate,
                    environment = backend.environment,
                    hostCapabilities = HOST_CAPABILITIES,
                    requirement = onDeviceAiPluginCapabilityRequirement(
                        request,
                        streaming = false,
                        persistentSession = true,
                    ),
                ),
            ) { "on-device AI provider metadata no longer matches the pinned session plan" }
            return provider
        }

        private fun reinspectionMatches(): Boolean =
            reinspectionSnapshot()?.let(snapshot::samePackageIdentityForPersistentSession) == true

        private fun reinspectionSnapshot(): OnDeviceAiProviderPackageSnapshot? = try {
            when (
                val result = OnDeviceAiProviderDiscoveryPolicy.inspectExact(
                    source = backend.discovery,
                    component = request.component,
                    options = OnDeviceAiProviderDiscoveryOptions(enabled = true),
                )
            ) {
                is OnDeviceAiProviderDiscoveryResult.Completed ->
                    (result.evaluations.singleOrNull() as? OnDeviceAiProviderDiscoveryEvaluation.Discovered)
                        ?.snapshot
                else -> null
            }
        } catch (_: Throwable) {
            null
        }

        private fun acceptCallback(callingUid: Int, event: CallbackEvent) {
            val turn = synchronized(lock) { activeTurn?.takeIf { !closed } }
            if (turn == null) {
                event.close()
                return
            }
            if (callingUid != snapshot.packageUid) {
                event.close()
                turn.fail(OnDeviceAiPluginAskFailure.SESSION_REJECTED, closeSession = true)
                return
            }
            turn.accept(event)
        }

        private fun acceptSessionId(value: String): Boolean = synchronized(lock) {
            val existing = persistentSessionId
            if (existing == null) {
                persistentSessionId = value
                true
            } else {
                existing == value
            }
        }

        private fun grantCredits(turn: Turn, count: Int) {
            val currentRemote = synchronized(lock) {
                remote.takeIf { !closed && activeTurn === turn }
            }
            if (currentRemote == null || !backend.executeBlocking {
                    try {
                        currentRemote.grantCredits(count)
                    } catch (_: DeadObjectException) {
                        remoteDied()
                    } catch (_: Throwable) {
                        turn.failAsync(OnDeviceAiPluginAskFailure.PROVIDER_FAILED)
                    }
                }
            ) {
                turn.fail(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE, closeSession = true)
            }
        }

        private fun finishTurn(
            turn: Turn,
            closeSession: Boolean,
            cancelRemote: Boolean,
            deliver: () -> Unit,
        ): Boolean {
            val detached = synchronized(lock) {
                if (activeTurn !== turn) return false
                activeTurn = null
                if (!closeSession) return@synchronized null
                closed = true
                DetachedResources(
                    turn = null,
                    binding = binding.also { binding = null },
                    remote = remote.also { remote = null },
                )
            }
            if (detached != null) cleanup(detached.binding, detached.remote, cancelRemote)
            runCatching(deliver)
            return true
        }

        private fun closeWithFailure(failure: OnDeviceAiPluginAskFailure) {
            val turn = synchronized(lock) { activeTurn?.takeIf { !closed } }
            if (turn != null) {
                turn.fail(failure, closeSession = true)
            } else {
                close()
            }
        }

        private fun closeWithFailureAsync(failure: OnDeviceAiPluginAskFailure) {
            if (!backend.executeOrchestration { closeWithFailure(failure) }) {
                closeWithFailure(failure)
            }
        }

        private fun remoteDied() {
            if (!backend.executeOrchestration {
                    closeWithFailure(OnDeviceAiPluginAskFailure.BINDER_DIED)
                }
            ) {
                closeWithFailure(OnDeviceAiPluginAskFailure.BINDER_DIED)
            }
        }

        private fun cleanup(
            exactBinding: ExactBinding?,
            currentRemote: RemoteSession?,
            cancelRemote: Boolean,
        ) {
            val action: () -> Unit = {
                try {
                    if (cancelRemote) runCatching { currentRemote?.cancel() }
                    runCatching { currentRemote?.close() }
                    runCatching { exactBinding?.close() }
                } finally {
                    releaseAdmission()
                }
                Unit
            }
            if (currentRemote == null && exactBinding == null) {
                action()
            } else if (!backend.executeBlocking(action)) {
                action()
            }
        }

        private fun releaseAdmission() {
            if (released.compareAndSet(false, true)) backend.releaseAdmission()
        }

        private fun isCurrent(turn: Turn): Boolean = synchronized(lock) {
            !closed && activeTurn === turn
        }

        private class Turn(
            private val owner: ProcessOnDeviceAiPluginSession,
            message: OnDeviceAiPluginMessage,
            val firstTurn: Boolean,
            private val streaming: Boolean,
            private val onOutcome: ((OnDeviceAiPluginAskOutcome) -> Unit)?,
            private val onStreamEvent: ((OnDeviceAiPluginStreamEvent) -> Unit)?,
        ) : OnDeviceAiPluginAskHandle {
            private val terminal = AtomicBoolean(false)
            private val ready = AtomicBoolean(false)
            private val pending = ArrayDeque<CallbackEvent>()
            private val pendingLock = Any()
            private val deadline = AtomicReference<Closeable?>()
            private val typedRequest: OnDeviceAiRequest
            private val policy: OnDeviceAiSessionPolicy

            val requestEnvelope: ByteArray

            init {
                val messages = buildList {
                    if (firstTurn) addAll(owner.request.messages.dropLast(1))
                    add(message)
                }.toProtocolMessages()
                val outputLimit = minOf(
                    owner.request.maximumOutputBytes,
                    owner.plan.effectiveMaximumOutputBytes,
                )
                typedRequest = OnDeviceAiRequest(
                    requestId = "session-turn-${UUID.randomUUID()}",
                    protocolVersion = owner.plan.pinnedProvider.protocolVersion,
                    providerId = owner.request.providerId,
                    modelId = owner.plan.modelId,
                    messages = messages,
                    options = owner.request.toGenerationOptions(
                        stream = streaming,
                        maximumOutputBytes = outputLimit,
                        timeoutMillis = owner.request.timeoutMillis,
                        requiredCapabilityIds = buildList {
                            add(OnDeviceAiCapabilityId.USAGE)
                            add(OnDeviceAiCapabilityId.PERSISTENT_SESSION)
                            if (streaming) add(OnDeviceAiCapabilityId.STREAMING)
                            if (owner.request.structuredJson) add(OnDeviceAiCapabilityId.STRUCTURED_JSON)
                        },
                        persistentSession = true,
                    ),
                )
                val quota = org.autojs.plugin.ondeviceai.api.OnDeviceAiQuotaPolicy.validateRequest(
                    typedRequest,
                    owner.plan.pinnedProvider.negotiatedCapabilities,
                    descriptorCount = 0,
                )
                require(quota.inputBytes <= owner.plan.effectiveMaximumContextBytes)
                requestEnvelope = OnDeviceAiCodec.encodeTextRequest(typedRequest)
                require(requestEnvelope.size <= AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES)
                policy = OnDeviceAiSessionPolicy(
                    maximumToolRounds = 0,
                    maximumOutstandingToolCalls =
                        owner.plan.pinnedProvider.negotiatedCapabilities.maximumOutstandingToolCalls,
                    maximumOutputBytes = owner.plan.effectiveMaximumOutputBytes,
                    expectedRequest = typedRequest,
                    maximumSessionDescriptors =
                        owner.plan.pinnedProvider.negotiatedCapabilities.maximumSessionDescriptors,
                )
            }

            fun start() {
                val timeout = try {
                    owner.backend.schedule(owner.request.timeoutMillis) {
                        failAsync(OnDeviceAiPluginAskFailure.TIMED_OUT, cancelRemote = true)
                    }
                } catch (_: Throwable) {
                    fail(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE, closeSession = true)
                    return
                }
                if (!deadline.compareAndSet(null, timeout)) timeout.close()
                if (!owner.backend.executeOrchestration { owner.begin(this) }) {
                    fail(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE, closeSession = true)
                }
            }

            override fun cancel(): Boolean =
                fail(OnDeviceAiPluginAskFailure.CANCELLED, closeSession = true, cancelRemote = true)

            override fun close() {
                cancel()
            }

            fun remoteReady() {
                if (!ready.compareAndSet(false, true)) return
                val buffered = synchronized(pendingLock) {
                    pending.toList().also { pending.clear() }
                }
                buffered.forEach(::process)
            }

            fun accept(event: CallbackEvent) {
                if (terminal.get()) {
                    event.close()
                    return
                }
                if (!ready.get()) {
                    val reject = synchronized(pendingLock) {
                        if (ready.get()) {
                            false
                        } else if (pending.size >= MAXIMUM_BUFFERED_CALLBACKS) {
                            true
                        } else {
                            pending.addLast(event)
                            false
                        }
                    }
                    if (reject) {
                        event.close()
                        fail(OnDeviceAiPluginAskFailure.SESSION_REJECTED, closeSession = true)
                    } else if (ready.get()) {
                        val buffered = synchronized(pendingLock) {
                            pending.toList().also { pending.clear() }
                        }
                        buffered.forEach(::process)
                    }
                    return
                }
                process(event)
            }

            private fun process(event: CallbackEvent) {
                if (terminal.get()) {
                    event.close()
                    return
                }
                try {
                    when (event) {
                        is CallbackEvent.Started -> started(event.envelope)
                        is CallbackEvent.Chunk -> chunk(event.envelope)
                        is CallbackEvent.Usage -> usage(event.envelope)
                        is CallbackEvent.Completed -> completed(event.envelope, event.descriptors)
                        is CallbackEvent.Failed -> providerFailed(event.envelope)
                        CallbackEvent.Cancelled ->
                            fail(OnDeviceAiPluginAskFailure.CANCELLED, closeSession = true)
                        CallbackEvent.Unsupported ->
                            fail(OnDeviceAiPluginAskFailure.SESSION_REJECTED, closeSession = true)
                    }
                } catch (_: Throwable) {
                    event.close()
                    fail(OnDeviceAiPluginAskFailure.SESSION_REJECTED, closeSession = true)
                }
            }

            private fun started(envelope: ByteArray?) {
                val bytes = requireEnvelope(envelope)
                val metadata: AiSessionStarted = OnDeviceAiCodec.decodeSessionStarted(bytes)
                require(owner.acceptSessionId(metadata.sessionId))
                policy.onStarted(metadata)
                policy.grantCredits(OnDeviceAiLimits.INITIAL_CHUNK_CREDITS)
                owner.grantCredits(this, OnDeviceAiLimits.INITIAL_CHUNK_CREDITS)
                if (streaming) publish(OnDeviceAiPluginStreamEvent.Opened)
            }

            private fun chunk(envelope: ByteArray?) {
                val value: OnDeviceAiChunk = OnDeviceAiCodec.decodeTextChunk(requireEnvelope(envelope))
                policy.onChunk(value)
                if (!streaming || value.textDelta.isEmpty()) {
                    fail(OnDeviceAiPluginAskFailure.SESSION_REJECTED, closeSession = true)
                    return
                }
                publish(OnDeviceAiPluginStreamEvent.Chunk(value.textDelta))
                policy.grantCredits(1)
                owner.grantCredits(this, 1)
            }

            private fun usage(envelope: ByteArray?) {
                val value = AiCommonCodec.decodeUsage(requireEnvelope(envelope))
                policy.onUsage(value)
                if (streaming) publish(OnDeviceAiPluginStreamEvent.Usage(value.toPluginUsage()))
            }

            private fun completed(
                envelope: ByteArray?,
                incoming: OnDeviceAiProviderSessionIncomingDescriptors?,
            ) {
                val lease = try {
                    incoming?.copyForAsync()
                } finally {
                    incoming?.close()
                }
                try {
                    val result: AiCompletionResult =
                        OnDeviceAiCodec.decodeCompletionResult(requireEnvelope(envelope))
                    val verified = policy.onCompleted(
                        result = result,
                        descriptorCount = lease?.count ?: 0,
                        readDescriptor = lease?.let { owned ->
                            { index, length -> owned.readDeclaredBytes(index, length) }
                        },
                    )
                    val finalUsage = requireNotNull(verified.finalUsage).toPluginUsage()
                    complete(
                        text = verified.outputText,
                        usage = finalUsage,
                        closeSession = result.finishReason == AiFinishReason.LENGTH,
                    )
                } finally {
                    lease?.close()
                }
            }

            private fun providerFailed(envelope: ByteArray?) {
                val failure = runCatching {
                    val error = AiCommonCodec.decodeError(requireEnvelope(envelope))
                    when (error.code) {
                        org.autojs.plugin.ai.common.api.AiErrorCode.TIMEOUT ->
                            OnDeviceAiPluginAskFailure.TIMED_OUT
                        else -> OnDeviceAiPluginAskFailure.PROVIDER_FAILED
                    }
                }.getOrDefault(OnDeviceAiPluginAskFailure.SESSION_REJECTED)
                fail(failure, closeSession = true)
            }

            private fun complete(
                text: String,
                usage: OnDeviceAiPluginUsage,
                closeSession: Boolean,
            ): Boolean {
                if (!terminal.compareAndSet(false, true)) return false
                disposeTurnResources()
                return owner.finishTurn(
                    turn = this,
                    closeSession = closeSession,
                    cancelRemote = false,
                ) {
                    if (streaming) {
                        checkNotNull(onStreamEvent).invoke(OnDeviceAiPluginStreamEvent.Completed(text, usage))
                    } else {
                        checkNotNull(onOutcome).invoke(
                            OnDeviceAiPluginAskOutcome.Completed(text, usage, owner.modelId),
                        )
                    }
                }
            }

            fun failAsync(
                failure: OnDeviceAiPluginAskFailure,
                cancelRemote: Boolean = false,
            ) {
                if (!owner.backend.executeOrchestration {
                        fail(failure, closeSession = true, cancelRemote = cancelRemote)
                    }
                ) {
                    fail(failure, closeSession = true, cancelRemote = cancelRemote)
                }
            }

            fun fail(
                failure: OnDeviceAiPluginAskFailure,
                closeSession: Boolean,
                cancelRemote: Boolean = false,
            ): Boolean {
                if (!terminal.compareAndSet(false, true)) return false
                runCatching { policy.onFailed() }
                disposeTurnResources()
                return owner.finishTurn(this, closeSession, cancelRemote) {
                    if (streaming) {
                        checkNotNull(onStreamEvent).invoke(OnDeviceAiPluginStreamEvent.Failed(failure))
                    } else {
                        checkNotNull(onOutcome).invoke(OnDeviceAiPluginAskOutcome.Failed(failure))
                    }
                }
            }

            fun finishDetached(failure: OnDeviceAiPluginAskFailure) {
                if (!terminal.compareAndSet(false, true)) return
                disposeTurnResources()
                if (streaming) {
                    runCatching {
                        checkNotNull(onStreamEvent).invoke(OnDeviceAiPluginStreamEvent.Failed(failure))
                    }
                } else {
                    runCatching { checkNotNull(onOutcome).invoke(OnDeviceAiPluginAskOutcome.Failed(failure)) }
                }
            }

            private fun publish(event: OnDeviceAiPluginStreamEvent) {
                if (!terminal.get()) runCatching { checkNotNull(onStreamEvent).invoke(event) }
            }

            private fun disposeTurnResources() {
                deadline.getAndSet(null)?.close()
                synchronized(pendingLock) {
                    pending.forEach(CallbackEvent::close)
                    pending.clear()
                }
            }

            private fun requireEnvelope(value: ByteArray?): ByteArray = checkNotNull(value).also {
                require(it.isNotEmpty() && it.size <= AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES)
            }

            private companion object {
                const val MAXIMUM_BUFFERED_CALLBACKS = 16
            }
        }

        private inner class ExactBinding(
            private val owner: ProcessOnDeviceAiPluginSession,
        ) : ServiceConnection, Closeable {
            private val bindingClosed = AtomicBoolean(false)
            private val registered = AtomicBoolean(false)
            private val unbound = AtomicBoolean(false)
            private val connected = AtomicBoolean(false)
            private val linked = AtomicReference<Pair<IBinder, IBinder.DeathRecipient>?>(null)

            fun bind() {
                val didBind = backend.applicationContext.bindService(
                    Intent().setComponent(ComponentName(request.component.packageName, request.component.className)),
                    this,
                    Context.BIND_AUTO_CREATE,
                )
                check(didBind) { "Unable to bind the persistent on-device AI provider" }
                registered.set(true)
                unbindIfClosed()
            }

            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (bindingClosed.get()) return
                if (name != ComponentName(request.component.packageName, request.component.className) ||
                    !connected.compareAndSet(false, true)
                ) {
                    owner.closeWithFailureAsync(OnDeviceAiPluginAskFailure.PROVIDER_REJECTED)
                    return
                }
                val recipient = IBinder.DeathRecipient(owner::remoteDied)
                try {
                    service.linkToDeath(recipient, 0)
                    if (!service.isBinderAlive) throw DeadObjectException()
                    if (!linked.compareAndSet(null, service to recipient)) {
                        service.unlinkToDeath(recipient, 0)
                        throw IllegalStateException("Persistent provider death recipient is duplicated")
                    }
                    owner.providerConnected(service)
                } catch (_: Throwable) {
                    owner.remoteDied()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) = owner.remoteDied()

            override fun onBindingDied(name: ComponentName) = owner.remoteDied()

            override fun onNullBinding(name: ComponentName) =
                owner.closeWithFailureAsync(OnDeviceAiPluginAskFailure.PROVIDER_REJECTED)

            override fun close() {
                if (!bindingClosed.compareAndSet(false, true)) return
                linked.getAndSet(null)?.let { (binder, recipient) ->
                    runCatching { binder.unlinkToDeath(recipient, 0) }
                }
                unbindIfClosed()
            }

            private fun unbindIfClosed() {
                if (bindingClosed.get() && registered.get() && unbound.compareAndSet(false, true)) {
                    runCatching { backend.applicationContext.unbindService(this) }
                }
            }
        }

        private class RemoteSession private constructor(
            private val session: IOnDeviceAiSession,
            private val binder: IBinder,
            private val recipient: IBinder.DeathRecipient,
        ) : Closeable {
            private val closed = AtomicBoolean(false)
            private val controlLock = Any()

            fun generateNext(requestEnvelope: ByteArray) = synchronized(controlLock) {
                check(!closed.get()) { "Persistent on-device AI session is closed" }
                session.generateNext(requestEnvelope.copyOf(), emptyArray())
            }

            fun grantCredits(count: Int) = synchronized(controlLock) {
                check(!closed.get()) { "Persistent on-device AI session is closed" }
                session.grantCredits(count)
            }

            fun cancel() = synchronized(controlLock) {
                if (!closed.get()) session.cancel()
            }

            override fun close() {
                synchronized(controlLock) {
                    if (!closed.compareAndSet(false, true)) return
                    runCatching { binder.unlinkToDeath(recipient, 0) }
                    runCatching { session.close() }
                }
            }

            companion object {
                fun create(
                    session: IOnDeviceAiSession,
                    onDeath: () -> Unit,
                ): RemoteSession {
                    val binder = session.asBinder()
                    check(binder.isBinderAlive) { "Persistent on-device AI session Binder is dead" }
                    check(binder.interfaceDescriptor == IOnDeviceAiSession.DESCRIPTOR) {
                        "Persistent on-device AI session descriptor mismatch"
                    }
                    val recipient = IBinder.DeathRecipient(onDeath)
                    binder.linkToDeath(recipient, 0)
                    if (!binder.isBinderAlive) {
                        runCatching { binder.unlinkToDeath(recipient, 0) }
                        throw DeadObjectException()
                    }
                    return RemoteSession(session, binder, recipient)
                }
            }
        }

        private sealed class CallbackEvent : Closeable {
            data class Started(val envelope: ByteArray?) : CallbackEvent()
            data class Chunk(val envelope: ByteArray?) : CallbackEvent()
            data class Usage(val envelope: ByteArray?) : CallbackEvent()
            data class Completed(
                val envelope: ByteArray?,
                val descriptors: OnDeviceAiProviderSessionIncomingDescriptors?,
            ) : CallbackEvent() {
                override fun close() {
                    descriptors?.close()
                }
            }
            data class Failed(val envelope: ByteArray?) : CallbackEvent()
            object Cancelled : CallbackEvent()
            object Unsupported : CallbackEvent()

            override fun close() = Unit
        }

        private data class DetachedResources(
            val turn: Turn?,
            val binding: ExactBinding?,
            val remote: RemoteSession?,
        )

        private companion object {
            private val NO_OP_OPERATION_OBSERVER =
                OnDeviceAiProviderSessionOperationObserver { Closeable {} }

            private val BUSY_TURN = Any()
        }
    }

    private sealed class InitialDiscoveryResult {
        data class Found(val snapshot: OnDeviceAiProviderPackageSnapshot) : InitialDiscoveryResult()
        object NotFound : InitialDiscoveryResult()
        object Rejected : InitialDiscoveryResult()
    }

    private companion object {
        const val MAXIMUM_BLOCKING_OPERATIONS = 3

        /** Satisfies request validation for catalog-only attempts; never sent to a provider. */
        const val MODEL_LIST_PLACEHOLDER_PROMPT = "-"

        val HOST_CAPABILITIES = OnDeviceAiCapabilities(
            supportsStreaming = true,
            supportsReasoning = true,
            supportsTools = true,
            supportsStructuredJson = true,
            supportsUsage = true,
            maximumMessages = OnDeviceAiLimits.MAX_MESSAGES,
            maximumContentParts = OnDeviceAiLimits.MAX_CONTENT_PARTS,
            maximumToolDefinitions = OnDeviceAiLimits.MAX_TOOL_DEFINITIONS,
            maximumContextBytes = OnDeviceAiLimits.MAX_TOTAL_INPUT_BYTES,
            maximumOutputBytes = OnDeviceAiLimits.MAX_STREAMING_OUTPUT_BYTES,
            maximumToolRounds = OnDeviceAiLimits.MAX_TOOL_ROUNDS,
            maximumOutstandingToolCalls = OnDeviceAiLimits.MAX_OUTSTANDING_TOOL_CALLS,
            maximumRequestDescriptors = AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST,
            maximumSessionDescriptors = AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION,
            acceptedTextMimeTypes = listOf(OnDeviceAiMimeType.PLAIN, OnDeviceAiMimeType.JSON),
            acceptedSchemaMimeTypes = listOf(OnDeviceAiMimeType.JSON),
            supportsPersistentSessions = true,
        )
    }
}

private fun OnDeviceAiProviderPackageSnapshot.samePackageIdentityForPersistentSession(
    other: OnDeviceAiProviderPackageSnapshot,
): Boolean =
    component == other.component &&
        packageUid == other.packageUid &&
        versionCode == other.versionCode &&
        lastUpdateTime == other.lastUpdateTime &&
        signerSha256 == other.signerSha256

private fun OnDeviceAiProviderPackageSnapshot.toPersistentSessionCandidate(
    providerInfo: AiProviderInfo,
    capabilities: OnDeviceAiCapabilities,
) = OnDeviceAiProviderCandidate(
    component = component,
    declaredAction = declaredAction,
    exported = exported,
    applicationEnabled = applicationEnabled,
    serviceEnabled = serviceEnabled,
    requiredPermission = requiredPermission,
    packageUid = packageUid,
    versionCode = versionCode,
    lastUpdateTime = lastUpdateTime,
    signerSha256 = signerSha256,
    trustLevel = if (officialSignerMatch) {
        OnDeviceAiProviderTrustLevel.OFFICIAL
    } else {
        OnDeviceAiProviderTrustLevel.UNTRUSTED
    },
    sameSignerAsHost = sameSignerAsHost,
    providerInfo = providerInfo,
    capabilities = capabilities,
)

private object CompletedOnDeviceAiPluginAskHandle : OnDeviceAiPluginAskHandle {
    override fun cancel(): Boolean = false

    override fun close() = Unit
}

private class OnDeviceAiPluginAskThreadFactory(
    private val counter: AtomicInteger = AtomicInteger(),
) {
    fun named(role: String): ThreadFactory = ThreadFactory { runnable ->
        Thread(runnable, "AutoJs6-OnDeviceAiPlugin-$role-${counter.incrementAndGet()}")
    }
}

private fun execute(executor: ExecutorService, action: () -> Unit): Boolean = try {
    executor.execute(action)
    true
} catch (_: RejectedExecutionException) {
    false
}

private fun saturatingAdd(left: Long, right: Long): Long =
    if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
