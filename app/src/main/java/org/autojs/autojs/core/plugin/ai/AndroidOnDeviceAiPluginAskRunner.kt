package org.autojs.autojs.core.plugin.ai

import android.content.Context
import android.os.Build
import android.os.SystemClock
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ondeviceai.api.AiContentPart
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilityId
import org.autojs.plugin.ondeviceai.api.AiGenerationOptions
import org.autojs.plugin.ondeviceai.api.AiMessage
import org.autojs.plugin.ondeviceai.api.AiMessageRole
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilities
import org.autojs.plugin.ondeviceai.api.OnDeviceAiLimits
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
import org.autojs.plugin.ondeviceai.api.OnDeviceAiRequest
import java.io.Closeable
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

/**
 * Lazily initialized process owner for the explicit, default-off Android on-device AI plugin route.
 * Merely constructing a script runtime does not discover or bind a provider.
 */
internal class AndroidOnDeviceAiPluginAskRunner private constructor(
    private val applicationContext: Context,
) : OnDeviceAiPluginAskRunner, OnDeviceAiPluginStreamRunner, OnDeviceAiPluginModelListRunner {

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

    companion object {
        fun create(context: Context): OnDeviceAiPluginAskRunner =
            AndroidOnDeviceAiPluginAskRunner(context.applicationContext)

        fun createStream(context: Context): OnDeviceAiPluginStreamRunner =
            AndroidOnDeviceAiPluginAskRunner(context.applicationContext)

        fun createModelList(context: Context): OnDeviceAiPluginModelListRunner =
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
) : OnDeviceAiPluginAskRunner, OnDeviceAiPluginStreamRunner, OnDeviceAiPluginModelListRunner {
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
                prompt = MODEL_LIST_PLACEHOLDER_PROMPT,
                timeoutMillis = request.timeoutMillis,
            ),
            streaming = false,
            onOutcome = null,
            onStreamEvent = null,
            onModelList = onOutcome,
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

    private fun requirement(
        promptBytes: Int,
        streaming: Boolean,
    ): OnDeviceAiCapabilityRequirement =
        OnDeviceAiCapabilityRequirement(
            requiredCapabilityIds = if (streaming) {
                setOf(OnDeviceAiCapabilityId.STREAMING)
            } else {
                emptySet()
            },
            minimumMessages = 1,
            minimumContentParts = 1,
            minimumContextBytes = promptBytes.toLong(),
            minimumOutputBytes = 1L,
            minimumRequestDescriptors = 0,
            minimumSessionDescriptors = 0,
            requiredTextMimeTypes = setOf(OnDeviceAiMimeType.PLAIN),
        )

    private class Attempt(
        private val backend: ProcessOnDeviceAiPluginAskRunner,
        private val request: OnDeviceAiPluginAskRequest,
        private val streaming: Boolean,
        private val onOutcome: ((OnDeviceAiPluginAskOutcome) -> Unit)?,
        private val onStreamEvent: ((OnDeviceAiPluginStreamEvent) -> Unit)?,
        private val onModelList: ((OnDeviceAiPluginModelListOutcome) -> Unit)? = null,
    ) : OnDeviceAiPluginAskHandle {
        private val lock = Any()
        private val startedAtMillis = backend.now()
        private val deadlineAtMillis = saturatingAdd(startedAtMillis, request.timeoutMillis)
        private val promptBytes = request.prompt.toByteArray(Charsets.UTF_8)
        private val requirement = backend.requirement(promptBytes.size, streaming)

        /** The model the catalog resolved for this attempt; echoed on completion. */
        private var resolvedModelId: String? = request.modelId

        private var terminal = false
        private var stageHandle: Closeable? = null
        private var deadlineHandle: Closeable? = null

        fun start() {
            val deadline = try {
                backend.schedule(request.timeoutMillis) {
                    finish(OnDeviceAiPluginAskOutcome.Failed(OnDeviceAiPluginAskFailure.TIMED_OUT))
                }
            } catch (_: Exception) {
                finish(OnDeviceAiPluginAskOutcome.Failed(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE))
                return
            }
            installDeadline(deadline)
            if (!PluginEnableStore.isEnabled(backend.applicationContext, request.component.packageName)) {
                finish(OnDeviceAiPluginAskOutcome.Failed(OnDeviceAiPluginAskFailure.PROVIDER_DISABLED))
                return
            }
            enqueue(::beginDiscovery)
        }

        override fun cancel(): Boolean =
            finish(OnDeviceAiPluginAskOutcome.Failed(OnDeviceAiPluginAskFailure.CANCELLED))

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
                finish(OnDeviceAiPluginAskOutcome.Failed(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE))
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
                InitialDiscoveryResult.NotFound -> finish(
                    OnDeviceAiPluginAskOutcome.Failed(OnDeviceAiPluginAskFailure.PROVIDER_NOT_FOUND),
                )
                InitialDiscoveryResult.Rejected -> finish(
                    OnDeviceAiPluginAskOutcome.Failed(OnDeviceAiPluginAskFailure.PROVIDER_REJECTED),
                )
                is InitialDiscoveryResult.Found -> beginHandshake(result.snapshot)
            }
        }

        private fun beginHandshake(snapshot: OnDeviceAiProviderPackageSnapshot) {
            if (!isActive()) return
            val remaining = remainingMillis()
            if (remaining <= 0L) {
                finish(OnDeviceAiPluginAskOutcome.Failed(OnDeviceAiPluginAskFailure.TIMED_OUT))
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
                finish(OnDeviceAiPluginAskOutcome.Failed(OnDeviceAiPluginAskFailure.INTERNAL_FAILURE))
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
                    messages = listOf(
                        AiMessage(
                            role = AiMessageRole.USER,
                            parts = listOf(
                                AiContentPart(
                                    AiPayloadReference(
                                        mimeType = OnDeviceAiMimeType.PLAIN,
                                        declaredLengthBytes = promptBytes.size.toLong(),
                                        inlineBytes = promptBytes,
                                        charset = "utf-8",
                                    ),
                                ),
                            ),
                        ),
                    ),
                    options = AiGenerationOptions(
                        stream = streaming,
                        includeReasoning = false,
                        structuredJson = false,
                        reportUsage = false,
                        maximumOutputBytes = outputLimit,
                        maximumToolRounds = 0,
                        timeoutMillis = remaining.coerceAtMost(OnDeviceAiLimits.MAX_TIMEOUT_MILLIS),
                        responseMimeType = OnDeviceAiMimeType.PLAIN,
                        requiredCapabilityIds = if (streaming) {
                            listOf(OnDeviceAiCapabilityId.STREAMING)
                        } else {
                            emptyList()
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
                    is OnDeviceAiProviderSessionEvent.Usage ->
                        terminalSession(OnDeviceAiPluginAskFailure.SESSION_REJECTED)
                    is OnDeviceAiProviderSessionEvent.Completed -> {
                        detachStage()
                        finish(
                            OnDeviceAiPluginAskOutcome.Completed(
                                text = transcript.result.outputText,
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

        private fun fail(failure: OnDeviceAiPluginAskFailure) {
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
                            OnDeviceAiPluginStreamEvent.Completed(outcome.text)
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

        private fun markTerminalAndRelease(): Boolean {
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
            backend.releaseAdmission()
            return true
        }

        private fun isActive(): Boolean = synchronized(lock) { !terminal }

        private fun remainingMillis(): Long = deadlineAtMillis - backend.now()
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
        )
    }
}

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
