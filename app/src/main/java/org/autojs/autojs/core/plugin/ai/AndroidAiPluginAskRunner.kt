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
import org.autojs.autojs.core.plugin.PluginAutoWake
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiProviderInfo
import org.autojs.plugin.ai.provider.api.AiCompletionResult
import org.autojs.plugin.ai.provider.api.AiContentPart
import org.autojs.plugin.ai.provider.api.AiFinishReason
import org.autojs.plugin.ai.provider.api.AiSessionStarted
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiMessage
import org.autojs.plugin.ai.provider.api.AiProviderCapabilities
import org.autojs.plugin.ai.provider.api.IAiCallback
import org.autojs.plugin.ai.provider.api.IAiProvider
import org.autojs.plugin.ai.provider.api.IAiSession
import org.autojs.plugin.ai.provider.api.AiProviderChunk
import org.autojs.plugin.ai.provider.api.AiProviderCodec
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiProviderRequest
import org.autojs.plugin.ai.provider.api.AiProviderSessionPolicy
import org.autojs.plugin.ai.provider.api.AiTargetInfo
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

internal fun AiPluginAskRequest.toProtocolMessages(): List<AiMessage> =
    messages.toProtocolMessages()

private fun List<AiPluginMessage>.toProtocolMessages(): List<AiMessage> {
    var imageIndex = 0
    return map { message ->
    val contentBytes = message.content.toByteArray(Charsets.UTF_8)
    check(contentBytes.size == message.contentBytes) { "AI plugin message UTF-8 size changed" }
    AiMessage(
        role = message.role.protocolValue,
        parts = listOf(
            AiContentPart(
                AiPayloadReference(
                    mimeType = AiProviderMimeType.PLAIN,
                    declaredLengthBytes = contentBytes.size.toLong(),
                    inlineBytes = contentBytes,
                    charset = "utf-8",
                ),
            ),
        ) + message.images.map { it.contentPart(imageIndex++) },
    )
    }
}

internal fun aiProviderPluginCapabilityRequirement(
    request: AiPluginAskRequest,
    streaming: Boolean,
    persistentSession: Boolean = false,
): AiProviderCapabilityRequirement = AiProviderCapabilityRequirement(
    requiredCapabilityIds = buildSet {
        add(AiProviderCapabilityId.USAGE)
        if (streaming) add(AiProviderCapabilityId.STREAMING)
        if (persistentSession) add(AiProviderCapabilityId.PERSISTENT_SESSION)
        if (request.structuredJson) add(AiProviderCapabilityId.STRUCTURED_JSON)
        if (request.includeReasoning) add(AiProviderCapabilityId.REASONING)
        if (request.tools.isNotEmpty()) add(AiProviderCapabilityId.TOOLS)
        if (request.vision) add(AiProviderCapabilityId.VISION)
    },
    minimumMessages = request.messages.size,
    minimumContentParts = request.messages.size + request.images.size,
    minimumToolDefinitions = request.tools.size,
    minimumToolRounds = request.maximumToolRounds,
    minimumOutstandingToolCalls = if (request.tools.isNotEmpty()) 1 else 0,
    minimumContextBytes = request.inputBytes,
    minimumOutputBytes = 1L,
    minimumRequestDescriptors = request.images.size,
    minimumSessionDescriptors = if (request.vision) maxOf(1, request.images.size) else 0,
    requiredTextMimeTypes = buildSet {
        add(AiProviderMimeType.PLAIN)
        if (request.structuredJson) add(AiProviderMimeType.JSON)
    },
    requiredSchemaMimeTypes = if (request.structuredJson || request.tools.isNotEmpty()) {
        setOf(AiProviderMimeType.JSON)
    } else {
        emptySet()
    },
)

/**
 * Lazily initialized process owner for the explicit, default-off Android AI provider route.
 * Merely constructing a script runtime does not discover or bind a provider.
 */
internal class AndroidAiPluginAskRunner private constructor(
    private val applicationContext: Context,
) : AiPluginAskRunner,
    AiPluginToolRunner,
    AiPluginStreamRunner,
    AiPluginCatalogRunner,
    AiPluginSessionRunner {

    override fun start(
        request: AiPluginAskRequest,
        onOutcome: (AiPluginAskOutcome) -> Unit,
    ): AiPluginAskHandle = processBackend(applicationContext).start(request, onOutcome)

    override fun startStream(
        request: AiPluginAskRequest,
        onEvent: (AiPluginStreamEvent) -> Unit,
    ): AiPluginAskHandle = processBackend(applicationContext).startStream(request, onEvent)

    override fun startTools(request: AiPluginAskRequest, stream: Boolean, onEvent: (AiPluginStreamEvent) -> Unit) =
        processBackend(applicationContext).startTools(request, stream, onEvent)

    override fun listCatalog(
        request: AiPluginCatalogRequest,
        onOutcome: (AiPluginCatalogOutcome) -> Unit,
    ): AiPluginAskHandle = processBackend(applicationContext).listCatalog(request, onOutcome)

    override fun openSession(
        request: AiPluginAskRequest,
        onOutcome: (AiPluginSessionOpenOutcome) -> Unit,
    ): AiPluginAskHandle = processBackend(applicationContext).openSession(request, onOutcome)

    companion object {
        fun create(context: Context): AiPluginAskRunner =
            AndroidAiPluginAskRunner(context.applicationContext)

        fun createStream(context: Context): AiPluginStreamRunner =
            AndroidAiPluginAskRunner(context.applicationContext)

        fun createTools(context: Context): AiPluginToolRunner = AndroidAiPluginAskRunner(context.applicationContext)

        fun createCatalog(context: Context): AiPluginCatalogRunner =
            AndroidAiPluginAskRunner(context.applicationContext)

        fun createSession(context: Context): AiPluginSessionRunner =
            AndroidAiPluginAskRunner(context.applicationContext)

        private val backendLock = Any()

        @Volatile
        private var sharedBackend: ProcessAiPluginAskRunner? = null

        private fun processBackend(context: Context): ProcessAiPluginAskRunner {
            sharedBackend?.let { return it }
            return synchronized(backendLock) {
                sharedBackend ?: createProcessBackend(context).also { sharedBackend = it }
            }
        }

        private fun createProcessBackend(context: Context): ProcessAiPluginAskRunner =
            ProcessAiPluginAskRunner(context.applicationContext)
    }
}

private class ProcessAiPluginAskRunner(
    private val applicationContext: Context,
) : AiPluginAskRunner,
    AiPluginToolRunner,
    AiPluginStreamRunner,
    AiPluginCatalogRunner,
    AiPluginSessionRunner {
    private val admission = AtomicBoolean(false)
    private val threadFactory = AiPluginAskThreadFactory()
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
    private val discovery = AndroidAiProviderDiscovery(applicationContext)
    private val environment = AiProviderEnvironment(
        hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
        currentAbi = Build.SUPPORTED_ABIS.firstOrNull(),
        releaseBuild = !BuildConfig.DEBUG,
        allowUserApprovedProviders = false,
    )
    private val handshake = AiProviderHandshakeCoordinator(
        transport = AndroidAiProviderHandshake(applicationContext),
        workerExecutor = coordinatorExecutor,
        blockingExecutor = blockingExecutor,
        deadlineScheduler = AiProviderHandshakeDeadlineScheduler(::schedule),
        clock = AiProviderHandshakeClock(::now),
    )
    private val targetListing = AiProviderTargetListingCoordinator(
        transport = AndroidAiProviderTargetListing(applicationContext),
        workerExecutor = coordinatorExecutor,
        blockingExecutor = blockingExecutor,
        deadlineScheduler = AiProviderTargetListingDeadlineScheduler(::schedule),
        clock = AiProviderTargetListingClock(::now),
        owner = AiProviderTargetListingProcessOwner(),
    )
    private val session = AiProviderSessionCoordinator(
        transport = AndroidAiProviderSession(applicationContext),
        workerExecutor = coordinatorExecutor,
        blockingExecutor = blockingExecutor,
        terminalFallbackExecutor = terminalFallbackExecutor,
        deadlineScheduler = AiProviderSessionDeadlineScheduler(::schedule),
        clock = AiProviderSessionClock(::now),
        owner = AiProviderSessionProcessOwner(),
    )

    override fun start(
        request: AiPluginAskRequest,
        onOutcome: (AiPluginAskOutcome) -> Unit,
    ): AiPluginAskHandle {
        if (!admission.compareAndSet(false, true)) {
            deliver {
                onOutcome(AiPluginAskOutcome.Failed(AiPluginAskFailure.BUSY))
            }
            return CompletedAiPluginAskHandle
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
        request: AiPluginAskRequest,
        onEvent: (AiPluginStreamEvent) -> Unit,
    ): AiPluginAskHandle {
        if (!admission.compareAndSet(false, true)) {
            deliver { onEvent(AiPluginStreamEvent.Failed(AiPluginAskFailure.BUSY)) }
            return CompletedAiPluginAskHandle
        }
        return Attempt(
            backend = this,
            request = request,
            streaming = true,
            onOutcome = null,
            onStreamEvent = onEvent,
        ).also(Attempt::start)
    }

    override fun startTools(request: AiPluginAskRequest, stream: Boolean, onEvent: (AiPluginStreamEvent) -> Unit): AiPluginAskHandle {
        require(request.tools.isNotEmpty())
        if (!admission.compareAndSet(false, true)) {
            deliver { onEvent(AiPluginStreamEvent.Failed(AiPluginAskFailure.BUSY)) }
            return CompletedAiPluginAskHandle
        }
        return Attempt(this, request, stream, null, onEvent).also(Attempt::start)
    }

    override fun listCatalog(
        request: AiPluginCatalogRequest,
        onOutcome: (AiPluginCatalogOutcome) -> Unit,
    ): AiPluginAskHandle {
        if (!admission.compareAndSet(false, true)) {
            deliver { onOutcome(AiPluginCatalogOutcome.Failed(AiPluginAskFailure.BUSY)) }
            return CompletedAiPluginAskHandle
        }
        return Attempt(
            backend = this,
            request = AiPluginAskRequest(
                component = request.component,
                providerId = request.providerId,
                targetId = null,
                messages = listOf(
                    AiPluginMessage(
                        role = AiPluginMessageRole.USER,
                        content = CATALOG_PLACEHOLDER_PROMPT,
                    ),
                ),
                timeoutMillis = request.timeoutMillis,
            ),
            streaming = false,
            onOutcome = null,
            onStreamEvent = null,
            onCatalog = onOutcome,
        ).also(Attempt::start)
    }

    override fun openSession(
        request: AiPluginAskRequest,
        onOutcome: (AiPluginSessionOpenOutcome) -> Unit,
    ): AiPluginAskHandle {
        if (!admission.compareAndSet(false, true)) {
            deliver {
                onOutcome(AiPluginSessionOpenOutcome.Failed(AiPluginAskFailure.BUSY))
            }
            return CompletedAiPluginAskHandle
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
            "AI provider route admission was not acquired"
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
        private val backend: ProcessAiPluginAskRunner,
        private val request: AiPluginAskRequest,
        private val streaming: Boolean,
        private val onOutcome: ((AiPluginAskOutcome) -> Unit)?,
        private val onStreamEvent: ((AiPluginStreamEvent) -> Unit)?,
        private val onCatalog: ((AiPluginCatalogOutcome) -> Unit)? = null,
        private val onSessionOpen: ((AiPluginSessionOpenOutcome) -> Unit)? = null,
    ) : AiPluginAskHandle {
        private val lock = Any()
        private val startedAtMillis = backend.now()
        private val deadlineAtMillis = saturatingAdd(startedAtMillis, request.timeoutMillis)
        private val protocolMessages = request.toProtocolMessages()
        private val requirement = aiProviderPluginCapabilityRequirement(
            request = request,
            streaming = streaming,
            persistentSession = onSessionOpen != null,
        )

        /** The exact target the catalog resolved for this attempt; echoed on every completion. */
        private var resolvedTarget: AiPluginTargetSummary? = null

        private var terminal = false
        private var stageHandle: Closeable? = null
        private var deadlineHandle: Closeable? = null

        fun start() {
            if ((request.tools.isNotEmpty() && (onStreamEvent == null || onSessionOpen != null)) ||
                (request.vision && onSessionOpen != null)) {
                fail(AiPluginAskFailure.INVALID_REQUEST)
                return
            }
            val deadline = try {
                backend.schedule(request.timeoutMillis) {
                    fail(AiPluginAskFailure.TIMED_OUT)
                }
            } catch (_: Exception) {
                fail(AiPluginAskFailure.INTERNAL_FAILURE)
                return
            }
            installDeadline(deadline)
            if (!PluginEnableStore.isEnabled(backend.applicationContext, request.component.packageName)) {
                fail(AiPluginAskFailure.AI_PROVIDER_DISABLED)
                return
            }
            enqueue(::beginDiscovery)
        }

        override fun cancel(): Boolean =
            fail(AiPluginAskFailure.CANCELLED)

        override fun submitToolResults(results: List<AiPluginToolResult>): Boolean {
            val batch = try { results.toProtocolToolResults() } catch (_: Exception) { return false }
            val session = synchronized(lock) { if (terminal) null else stageHandle as? AiProviderSessionRunHandle }
            val images = results.flatMap { it.images }
            return if (images.isEmpty()) session?.submitToolResults(batch) ?: false
                else if (!request.vision) false
                else session?.submitToolResults(batch, AiPluginImageDescriptors(images, backend.applicationContext.cacheDir)) ?: false
        }

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
                fail(AiPluginAskFailure.INTERNAL_FAILURE)
            }
        }

        private fun inspectInitialSnapshot(): InitialDiscoveryResult = try {
            when (
                val result = AiProviderDiscoveryPolicy.inspectExact(
                    source = backend.discovery,
                    component = request.component,
                    options = AiProviderDiscoveryOptions(enabled = true),
                )
            ) {
                AiProviderDiscoveryResult.Disabled,
                AiProviderDiscoveryResult.WrongAction,
                is AiProviderDiscoveryResult.QueryFailed,
                -> InitialDiscoveryResult.Rejected
                is AiProviderDiscoveryResult.Completed -> {
                    if (result.evaluations.isEmpty()) {
                        InitialDiscoveryResult.NotFound
                    } else {
                        val discovered = result.evaluations.singleOrNull()
                            as? AiProviderDiscoveryEvaluation.Discovered
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
                InitialDiscoveryResult.NotFound -> fail(AiPluginAskFailure.AI_PROVIDER_UNAVAILABLE)
                InitialDiscoveryResult.Rejected -> fail(AiPluginAskFailure.AI_PROVIDER_REJECTED)
                is InitialDiscoveryResult.Found -> beginHandshake(result.snapshot)
            }
        }

        private fun beginHandshake(snapshot: AiProviderPackageSnapshot) {
            if (!isActive()) return
            val remaining = remainingMillis()
            if (remaining <= 0L) {
                fail(AiPluginAskFailure.TIMED_OUT)
                return
            }
            val handle = try {
                backend.handshake.start(
                    AiProviderHandshakeRequest(
                        snapshot = snapshot,
                        environment = backend.environment,
                        hostCapabilities = HOST_CAPABILITIES,
                        requirement = requirement,
                        options = AiProviderHandshakeOptions(
                            enabled = true,
                            timeoutMillis = remaining,
                        ),
                    ),
                ) { outcome -> enqueue { acceptHandshake(snapshot, outcome) } }
            } catch (_: Exception) {
                fail(AiPluginAskFailure.INTERNAL_FAILURE)
                return
            }
            installStage(handle)
        }

        private fun acceptHandshake(
            snapshot: AiProviderPackageSnapshot,
            outcome: AiProviderHandshakeOutcome,
        ) {
            detachStage()
            if (!isActive()) return
            when (outcome) {
                AiProviderHandshakeOutcome.Busy -> fail(AiPluginAskFailure.BUSY)
                AiProviderHandshakeOutcome.Fused -> fail(AiPluginAskFailure.FUSED)
                AiProviderHandshakeOutcome.Cancelled -> fail(AiPluginAskFailure.CANCELLED)
                AiProviderHandshakeOutcome.Disabled -> fail(AiPluginAskFailure.INTERNAL_FAILURE)
                is AiProviderHandshakeOutcome.Rejected -> fail(
                    when (outcome.failure) {
                        AiProviderHandshakeFailure.BIND_TIMEOUT,
                        AiProviderHandshakeFailure.METADATA_TIMEOUT,
                        -> AiPluginAskFailure.TIMED_OUT
                        AiProviderHandshakeFailure.BINDER_DIED ->
                            AiPluginAskFailure.BINDER_DIED
                        AiProviderHandshakeFailure.EXECUTOR_REJECTED,
                        AiProviderHandshakeFailure.INTERNAL_FAILURE,
                        -> AiPluginAskFailure.INTERNAL_FAILURE
                        else -> AiPluginAskFailure.AI_PROVIDER_REJECTED
                    },
                )
                is AiProviderHandshakeOutcome.Selected -> {
                    val pinned = outcome.pinned
                    if (pinned.component != request.component ||
                        pinned.providerId != request.providerId
                    ) {
                        fail(AiPluginAskFailure.AI_PROVIDER_REJECTED)
                    } else {
                        beginTargetListing(snapshot, pinned)
                    }
                }
            }
        }

        private fun beginTargetListing(
            snapshot: AiProviderPackageSnapshot,
            pinned: AiProviderPinnedIdentity,
        ) {
            if (!isActive()) return
            val remaining = remainingMillis()
            if (remaining <= 0L) {
                fail(AiPluginAskFailure.TIMED_OUT)
                return
            }
            val handle = try {
                backend.targetListing.start(
                    AiProviderTargetListingRunRequest(
                        snapshot = snapshot,
                        pinnedProvider = pinned,
                        environment = backend.environment,
                        hostCapabilities = HOST_CAPABILITIES,
                        requirement = requirement,
                        options = AiProviderTargetListingRunOptions(
                            enabled = true,
                            timeoutMillis = remaining,
                        ),
                    ),
                ) { outcome -> enqueue { acceptTargetListing(snapshot, pinned, outcome) } }
            } catch (_: Exception) {
                fail(AiPluginAskFailure.INTERNAL_FAILURE)
                return
            }
            installStage(handle)
        }

        private fun acceptTargetListing(
            snapshot: AiProviderPackageSnapshot,
            pinned: AiProviderPinnedIdentity,
            outcome: AiProviderTargetListingRunOutcome,
        ) {
            detachStage()
            if (!isActive()) return
            when (outcome) {
                AiProviderTargetListingRunOutcome.Disabled ->
                    fail(AiPluginAskFailure.INTERNAL_FAILURE)
                AiProviderTargetListingRunOutcome.Busy -> fail(AiPluginAskFailure.BUSY)
                AiProviderTargetListingRunOutcome.Fused -> fail(AiPluginAskFailure.FUSED)
                AiProviderTargetListingRunOutcome.Cancelled ->
                    fail(AiPluginAskFailure.CANCELLED)
                AiProviderTargetListingRunOutcome.TimedOut ->
                    fail(AiPluginAskFailure.TIMED_OUT)
                AiProviderTargetListingRunOutcome.BinderDied ->
                    fail(AiPluginAskFailure.BINDER_DIED)
                is AiProviderTargetListingRunOutcome.ProviderFailed ->
                    fail(AiPluginAskFailure.PROVIDER_FAILED)
                is AiProviderTargetListingRunOutcome.Rejected -> fail(
                    when (outcome.failure) {
                        AiProviderTargetListingRunFailure.EXECUTOR_REJECTED,
                        AiProviderTargetListingRunFailure.INTERNAL_FAILURE,
                        -> AiPluginAskFailure.INTERNAL_FAILURE
                        else -> AiPluginAskFailure.AI_PROVIDER_REJECTED
                    },
                )
                is AiProviderTargetListingRunOutcome.Completed -> {
                    val catalog = outcome.catalog
                    if (catalog.pinnedProvider != pinned ||
                        catalog.pinnedProvider.providerId != request.providerId
                    ) {
                        fail(AiPluginAskFailure.AI_PROVIDER_REJECTED)
                        return
                    }
                    if (onCatalog != null) {
                        finishCatalog(catalog)
                        return
                    }
                    val selectedTarget = request.targetId?.let { requestedTargetId ->
                        catalog.targets.singleOrNull { it.targetId == requestedTargetId }
                            ?: run {
                                fail(AiPluginAskFailure.TARGET_NOT_FOUND)
                                return
                            }
                    } ?: catalog.defaultTarget ?: run {
                        fail(AiPluginAskFailure.TARGET_NOT_CONFIGURED)
                        return
                    }
                    resolvedTarget = selectedTarget.toPluginTargetSummary(catalog)
                    when (
                        val planned = catalog.planExact(
                            selectedTarget.targetId,
                            requirement,
                            request.backendProfile,
                        )
                    ) {
                        AiProviderExactTargetPlanResult.TargetNotFound ->
                            fail(AiPluginAskFailure.TARGET_NOT_FOUND)
                        AiProviderExactTargetPlanResult.TargetNotConfigured ->
                            fail(AiPluginAskFailure.TARGET_NOT_CONFIGURED)
                        AiProviderExactTargetPlanResult.TargetUnavailable ->
                            fail(AiPluginAskFailure.TARGET_UNAVAILABLE)
                        AiProviderExactTargetPlanResult.TargetCapabilityMismatch ->
                            fail(AiPluginAskFailure.TARGET_CAPABILITY_MISMATCH)
                        AiProviderExactTargetPlanResult.BackendUnavailable ->
                            fail(AiPluginAskFailure.BACKEND_UNAVAILABLE)
                        is AiProviderExactTargetPlanResult.Planned -> {
                            val plan = planned.plan
                            if (plan.pinnedProvider != pinned ||
                                plan.pinnedProvider.providerId != request.providerId ||
                                plan.targetId != selectedTarget.targetId ||
                                plan.modelId != selectedTarget.modelId
                            ) {
                                fail(AiPluginAskFailure.AI_PROVIDER_REJECTED)
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

        private fun finishCatalog(catalog: AiProviderTargetCatalog) {
            detachStage()
            finishCatalog(
                AiPluginCatalogOutcome.Completed(
                    AiPluginCatalog(
                        component = request.component,
                        providerId = request.providerId,
                        generation = catalog.catalogGeneration,
                        defaultTargetId = catalog.defaultTarget?.targetId,
                        targets = catalog.targets.map { it.toPluginTargetSummary(catalog) },
                    ),
                ),
            )
        }

        private fun beginSession(
            snapshot: AiProviderPackageSnapshot,
            plan: AiProviderHostSessionPlan,
        ) {
            if (!isActive()) return
            val remaining = remainingMillis()
            if (remaining < AiProviderLimits.MIN_TIMEOUT_MILLIS) {
                fail(AiPluginAskFailure.TIMED_OUT)
                return
            }
            val outputLimit = minOf(request.maximumOutputBytes, plan.effectiveMaximumOutputBytes)
            val typedRequest = try {
                AiProviderRequest(
                    requestId = "${if (streaming) "stream" else "ask"}-${UUID.randomUUID()}",
                    protocolVersion = plan.pinnedProvider.protocolVersion,
                    providerId = request.providerId,
                    targetId = plan.targetId,
                    messages = protocolMessages,
                    tools = request.tools,
                    options = request.toGenerationOptions(
                        stream = streaming,
                        maximumOutputBytes = outputLimit,
                        timeoutMillis = remaining.coerceAtMost(AiProviderLimits.MAX_TIMEOUT_MILLIS),
                        requiredCapabilityIds = buildList {
                            add(AiProviderCapabilityId.USAGE)
                            if (streaming) add(AiProviderCapabilityId.STREAMING)
                            if (request.structuredJson) add(AiProviderCapabilityId.STRUCTURED_JSON)
                        },
                    ),
                )
            } catch (_: Exception) {
                fail(AiPluginAskFailure.INVALID_REQUEST)
                return
            }
            val descriptors = try { AiPluginImageDescriptors(request.images, backend.applicationContext.cacheDir).open() }
            catch (_: Exception) { fail(AiPluginAskFailure.INTERNAL_FAILURE); return }
            val handle = try {
                backend.session.start(
                    AiProviderSessionRunRequest(
                        snapshot = snapshot,
                        plan = plan,
                        request = typedRequest,
                        requestDescriptors = descriptors,
                        environment = backend.environment,
                        hostCapabilities = HOST_CAPABILITIES,
                        requirement = requirement,
                        options = AiProviderSessionRunOptions(enabled = true),
                    ),
                ) { event -> enqueue { acceptSessionEvent(event) } }
            } catch (_: Exception) {
                descriptors.close()
                fail(AiPluginAskFailure.INTERNAL_FAILURE)
                return
            }
            installStage(handle)
        }

        private fun acceptSessionEvent(event: AiProviderSessionRunEvent) {
            if (!isActive()) return
            when (event) {
                AiProviderSessionRunEvent.Busy -> terminalSession(AiPluginAskFailure.BUSY)
                AiProviderSessionRunEvent.Fused -> terminalSession(AiPluginAskFailure.FUSED)
                is AiProviderSessionRunEvent.Rejected ->
                    terminalSession(AiPluginAskFailure.SESSION_REJECTED)
                is AiProviderSessionRunEvent.Transcript -> when (val transcript = event.event) {
                    AiProviderSessionEvent.Disabled ->
                        terminalSession(AiPluginAskFailure.INTERNAL_FAILURE)
                    is AiProviderSessionEvent.Started -> {
                        if (onStreamEvent != null) {
                            publish(AiPluginStreamEvent.Opened(requireNotNull(resolvedTarget)))
                        }
                    }
                    is AiProviderSessionEvent.Chunk -> {
                        if (!streaming) {
                            terminalSession(AiPluginAskFailure.SESSION_REJECTED)
                        } else {
                            publish(
                                AiPluginStreamEvent.Chunk(
                                    text = transcript.chunk.textDelta,
                                    reasoning = transcript.chunk.reasoningDelta,
                                ),
                            )
                        }
                    }
                    is AiProviderSessionEvent.Usage -> {
                        val usage = transcript.usage.toPluginUsage()
                        if (onStreamEvent != null) publish(AiPluginStreamEvent.Usage(usage))
                    }
                    is AiProviderSessionEvent.ToolCalls -> publish(AiPluginStreamEvent.ToolCalls(transcript.calls))
                    is AiProviderSessionEvent.Completed -> {
                        val usage = transcript.result.finalUsage?.toPluginUsage()
                        if (usage == null) {
                            terminalSession(AiPluginAskFailure.SESSION_REJECTED)
                            return
                        }
                        detachStage()
                        finish(
                            AiPluginAskOutcome.Completed(
                                text = transcript.result.outputText,
                                reasoning = transcript.result.reasoningSummaryText.orEmpty(),
                                usage = usage,
                                finishReason = transcript.finishReason,
                                target = requireNotNull(resolvedTarget),
                            ),
                        )
                    }
                    is AiProviderSessionEvent.ProviderFailed -> {
                        detachStage()
                        finish(AiPluginAskOutcome.Failed(AiPluginAskFailure.PROVIDER_FAILED, transcript.error.providerReason))
                    }
                    AiProviderSessionEvent.Cancelled ->
                        terminalSession(AiPluginAskFailure.CANCELLED)
                    AiProviderSessionEvent.TimedOut ->
                        terminalSession(AiPluginAskFailure.TIMED_OUT)
                    AiProviderSessionEvent.BinderDied ->
                        terminalSession(AiPluginAskFailure.BINDER_DIED)
                    is AiProviderSessionEvent.Rejected ->
                        terminalSession(AiPluginAskFailure.SESSION_REJECTED)
                }
            }
        }

        private fun publish(event: AiPluginStreamEvent) {
            if (onStreamEvent == null || !isActive()) return
            checkNotNull(onStreamEvent).invoke(event)
        }

        private fun terminalSession(failure: AiPluginAskFailure) {
            detachStage()
            fail(failure)
        }

        private fun fail(failure: AiPluginAskFailure): Boolean =
            if (onSessionOpen != null) {
                finishSessionOpenFailure(failure)
            } else {
                finish(AiPluginAskOutcome.Failed(failure))
            }

        private fun enqueue(action: () -> Unit) {
            if (!backend.executeOrchestration {
                    if (!isActive()) return@executeOrchestration
                    try {
                        action()
                    } catch (_: Exception) {
                        fail(AiPluginAskFailure.INTERNAL_FAILURE)
                    }
                }
            ) {
                fail(AiPluginAskFailure.INTERNAL_FAILURE)
            }
        }

        private fun installStage(handle: Closeable) {
            val closeNow = synchronized(lock) {
                if (terminal) {
                    true
                } else {
                    check(stageHandle == null) { "AI provider route stage overlap" }
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
                    check(deadlineHandle == null) { "AI provider route deadline overlap" }
                    deadlineHandle = handle
                    false
                }
            }
            if (closeNow) runCatching { handle.close() }
        }

        private fun finish(outcome: AiPluginAskOutcome): Boolean {
            if (!markTerminalAndRelease()) return false
            val onCatalog = onCatalog
            when {
                onCatalog != null -> {
                    val catalogOutcome = when (outcome) {
                        // A completed ask outcome cannot reach a catalog-view attempt; map it
                        // defensively instead of throwing inside terminal delivery.
                        is AiPluginAskOutcome.Completed ->
                            AiPluginCatalogOutcome.Failed(AiPluginAskFailure.INTERNAL_FAILURE)
                        is AiPluginAskOutcome.Failed ->
                            AiPluginCatalogOutcome.Failed(outcome.failure)
                    }
                    runCatching { checkNotNull(onCatalog).invoke(catalogOutcome) }
                }
                onStreamEvent != null -> {
                    val event = when (outcome) {
                        is AiPluginAskOutcome.Completed ->
                            AiPluginStreamEvent.Completed(
                                text = outcome.text,
                                reasoning = outcome.reasoning,
                                usage = outcome.usage,
                                finishReason = outcome.finishReason,
                                target = outcome.target,
                            )
                        is AiPluginAskOutcome.Failed ->
                            AiPluginStreamEvent.Failed(outcome.failure, outcome.providerReason)
                    }
                    runCatching { checkNotNull(onStreamEvent).invoke(event) }
                }
                else -> runCatching { checkNotNull(onOutcome).invoke(outcome) }
            }
            return true
        }

        private fun finishCatalog(outcome: AiPluginCatalogOutcome): Boolean {
            if (!markTerminalAndRelease()) return false
            runCatching { checkNotNull(onCatalog).invoke(outcome) }
            return true
        }

        private fun finishSessionOpen(
            snapshot: AiProviderPackageSnapshot,
            plan: AiProviderHostSessionPlan,
        ): Boolean {
            if (!markTerminalAndRelease(releaseAdmission = false)) return false
            val session = try {
                ProcessAiPluginSession(
                    backend = backend,
                    snapshot = snapshot,
                    plan = plan,
                    request = request,
                    target = requireNotNull(resolvedTarget),
                )
            } catch (_: Throwable) {
                backend.releaseAdmission()
                runCatching {
                    checkNotNull(onSessionOpen).invoke(
                        AiPluginSessionOpenOutcome.Failed(
                            AiPluginAskFailure.INTERNAL_FAILURE,
                        ),
                    )
                }
                return true
            }
            val delivered = runCatching {
                checkNotNull(onSessionOpen).invoke(AiPluginSessionOpenOutcome.Completed(session))
            }.isSuccess
            if (!delivered) session.close()
            return true
        }

        private fun finishSessionOpenFailure(failure: AiPluginAskFailure): Boolean {
            if (!markTerminalAndRelease()) return false
            runCatching {
                checkNotNull(onSessionOpen).invoke(AiPluginSessionOpenOutcome.Failed(failure))
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
    private class ProcessAiPluginSession(
        private val backend: ProcessAiPluginAskRunner,
        private val snapshot: AiProviderPackageSnapshot,
        private val plan: AiProviderHostSessionPlan,
        private val request: AiPluginAskRequest,
        override val target: AiPluginTargetSummary,
    ) : AiPluginSession {
        private val lock = Any()
        private val released = AtomicBoolean(false)
        private var closed = false
        private var activeTurn: Turn? = null
        private var binding: ExactBinding? = null
        private var remote: RemoteSession? = null
        private var persistentSessionId: String? = null

        override val providerId: String
            get() = request.providerId

        override val backendProfile: String?
            get() = request.backendProfile

        override val isClosed: Boolean
            get() = synchronized(lock) { closed }

        private val callback = object : IAiCallback.Stub() {
            override fun onStarted(metadata: ByteArray?) =
                forward(CallbackEvent.Started(metadata?.copyOf()))

            override fun onChunk(chunk: ByteArray?) =
                forward(CallbackEvent.Chunk(chunk?.copyOf()))

            override fun onToolCalls(
                calls: ByteArray?,
                descriptors: Array<out ParcelFileDescriptor>?,
            ) {
                closeAndroidAiProviderSessionIncomingDescriptors(descriptors)
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
                        AndroidAiProviderSessionIncomingDescriptors(
                            it,
                            NO_OP_OPERATION_OBSERVER,
                        )
                    }
                } catch (_: Throwable) {
                    closeAndroidAiProviderSessionIncomingDescriptors(descriptors)
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
                    closeWithFailure(AiPluginAskFailure.INTERNAL_FAILURE)
                }
            }
        }

        override fun startTurn(
            message: AiPluginMessage,
            streaming: Boolean,
            onOutcome: ((AiPluginAskOutcome) -> Unit)?,
            onStreamEvent: ((AiPluginStreamEvent) -> Unit)?,
        ): AiPluginAskHandle {
            require(message.role == AiPluginMessageRole.USER) {
                "A persistent local AI turn must contain one user message"
            }
            require((streaming && onOutcome == null && onStreamEvent != null) ||
                (!streaming && onOutcome != null && onStreamEvent == null)
            ) { "Persistent local AI turn callback shape is invalid" }
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
                    AiPluginAskFailure.SESSION_CLOSED,
                )
                BUSY_TURN -> deliverImmediate(
                    streaming,
                    onOutcome,
                    onStreamEvent,
                    AiPluginAskFailure.BUSY,
                )
                else -> {
                    check(turn is Turn)
                    turn.start()
                    return turn
                }
            }
            return CompletedAiPluginAskHandle
        }

        override fun close() {
            val detached = synchronized(lock) {
                if (closed) return
                closed = true
                val turn = activeTurn.also { activeTurn = null }
                DetachedResources(turn, binding.also { binding = null }, remote.also { remote = null })
            }
            detached.turn?.finishDetached(AiPluginAskFailure.SESSION_CLOSED)
            cleanup(detached.binding, detached.remote, cancelRemote = false)
        }

        private fun deliverImmediate(
            streaming: Boolean,
            onOutcome: ((AiPluginAskOutcome) -> Unit)?,
            onStreamEvent: ((AiPluginStreamEvent) -> Unit)?,
            failure: AiPluginAskFailure,
        ) {
            backend.deliver {
                if (streaming) {
                    runCatching { checkNotNull(onStreamEvent).invoke(AiPluginStreamEvent.Failed(failure)) }
                } else {
                    runCatching { checkNotNull(onOutcome).invoke(AiPluginAskOutcome.Failed(failure)) }
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
                turn.fail(AiPluginAskFailure.SESSION_REJECTED, closeSession = true)
                return
            }
            if (!backend.executeBlocking {
                    try {
                        if (!isCurrent(turn) || !reinspectionMatches()) {
                            turn.failAsync(AiPluginAskFailure.AI_PROVIDER_REJECTED)
                        } else {
                            exactBinding.bind()
                        }
                    } catch (_: SecurityException) {
                        turn.failAsync(AiPluginAskFailure.AI_PROVIDER_REJECTED)
                    } catch (_: Throwable) {
                        turn.failAsync(AiPluginAskFailure.PROVIDER_FAILED)
                    }
                }
            ) {
                turn.fail(AiPluginAskFailure.INTERNAL_FAILURE, closeSession = true)
            }
        }

        private fun beginNextTurn(turn: Turn) {
            val currentRemote = synchronized(lock) {
                remote.takeIf { !closed && activeTurn === turn }
            }
            if (currentRemote == null) {
                turn.fail(AiPluginAskFailure.SESSION_CLOSED, closeSession = true)
                return
            }
            turn.remoteReady()
            if (!backend.executeBlocking {
                    try {
                        currentRemote.generateNext(turn.requestEnvelope)
                    } catch (_: DeadObjectException) {
                        remoteDied()
                    } catch (_: Throwable) {
                        turn.failAsync(AiPluginAskFailure.PROVIDER_FAILED)
                    }
                }
            ) {
                turn.fail(AiPluginAskFailure.INTERNAL_FAILURE, closeSession = true)
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
                        ) { "local AI provider returned no persistent session" }
                        RemoteSession.create(remoteSession, ::remoteDied)
                    } catch (_: DeadObjectException) {
                        remoteDied()
                        return@executeBlocking
                    } catch (_: SecurityException) {
                        turn.failAsync(AiPluginAskFailure.AI_PROVIDER_REJECTED)
                        return@executeBlocking
                    } catch (_: Throwable) {
                        turn.failAsync(AiPluginAskFailure.PROVIDER_FAILED)
                        return@executeBlocking
                    }
                    if (!backend.executeOrchestration { installRemote(turn, opened) }) opened.close()
                }
            ) {
                turn.fail(AiPluginAskFailure.INTERNAL_FAILURE, closeSession = true)
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

        private fun verifiedProvider(binder: IBinder): IAiProvider {
            check(binder.isBinderAlive) { "local AI provider Binder is dead" }
            check(binder.interfaceDescriptor == IAiProvider.DESCRIPTOR) {
                "local AI provider descriptor mismatch"
            }
            val provider = checkNotNull(IAiProvider.Stub.asInterface(binder))
            val infoEnvelope = checkNotNull(provider.providerInfo)
            require(infoEnvelope.size <= AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES)
            val capabilitiesEnvelope = checkNotNull(provider.capabilities)
            require(capabilitiesEnvelope.size <= AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES)
            val providerInfo = AiCommonCodec.decodeProviderInfo(infoEnvelope)
            val capabilities = AiProviderCodec.decodeCapabilities(capabilitiesEnvelope)
            val current = reinspectionSnapshot()
                ?: throw IllegalStateException("local AI provider identity drifted")
            require(snapshot.samePackageIdentityForPersistentSession(current))
            val candidate = current.toPersistentSessionCandidate(providerInfo, capabilities)
            require(
                AiProviderSelectionPolicy.revalidate(
                    pinned = plan.pinnedProvider,
                    current = candidate,
                    environment = backend.environment,
                    hostCapabilities = HOST_CAPABILITIES,
                    requirement = aiProviderPluginCapabilityRequirement(
                        request,
                        streaming = false,
                        persistentSession = true,
                    ),
                ),
            ) { "local AI provider metadata no longer matches the pinned session plan" }
            return provider
        }

        private fun reinspectionMatches(): Boolean =
            reinspectionSnapshot()?.let(snapshot::samePackageIdentityForPersistentSession) == true

        private fun reinspectionSnapshot(): AiProviderPackageSnapshot? = try {
            when (
                val result = AiProviderDiscoveryPolicy.inspectExact(
                    source = backend.discovery,
                    component = request.component,
                    options = AiProviderDiscoveryOptions(enabled = true),
                )
            ) {
                is AiProviderDiscoveryResult.Completed ->
                    (result.evaluations.singleOrNull() as? AiProviderDiscoveryEvaluation.Discovered)
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
                turn.fail(AiPluginAskFailure.SESSION_REJECTED, closeSession = true)
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
                        turn.failAsync(AiPluginAskFailure.PROVIDER_FAILED)
                    }
                }
            ) {
                turn.fail(AiPluginAskFailure.INTERNAL_FAILURE, closeSession = true)
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

        private fun closeWithFailure(failure: AiPluginAskFailure) {
            val turn = synchronized(lock) { activeTurn?.takeIf { !closed } }
            if (turn != null) {
                turn.fail(failure, closeSession = true)
            } else {
                close()
            }
        }

        private fun closeWithFailureAsync(failure: AiPluginAskFailure) {
            if (!backend.executeOrchestration { closeWithFailure(failure) }) {
                closeWithFailure(failure)
            }
        }

        private fun remoteDied() {
            if (!backend.executeOrchestration {
                    closeWithFailure(AiPluginAskFailure.BINDER_DIED)
                }
            ) {
                closeWithFailure(AiPluginAskFailure.BINDER_DIED)
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
            private val owner: ProcessAiPluginSession,
            message: AiPluginMessage,
            val firstTurn: Boolean,
            private val streaming: Boolean,
            private val onOutcome: ((AiPluginAskOutcome) -> Unit)?,
            private val onStreamEvent: ((AiPluginStreamEvent) -> Unit)?,
        ) : AiPluginAskHandle {
            private val terminal = AtomicBoolean(false)
            private val ready = AtomicBoolean(false)
            private val pending = ArrayDeque<CallbackEvent>()
            private val pendingLock = Any()
            private val deadline = AtomicReference<Closeable?>()
            private val typedRequest: AiProviderRequest
            private val policy: AiProviderSessionPolicy

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
                typedRequest = AiProviderRequest(
                    requestId = "session-turn-${UUID.randomUUID()}",
                    protocolVersion = owner.plan.pinnedProvider.protocolVersion,
                    providerId = owner.request.providerId,
                    targetId = owner.plan.targetId,
                    messages = messages,
                    options = owner.request.toGenerationOptions(
                        stream = streaming,
                        maximumOutputBytes = outputLimit,
                        timeoutMillis = owner.request.timeoutMillis,
                        requiredCapabilityIds = buildList {
                            add(AiProviderCapabilityId.USAGE)
                            add(AiProviderCapabilityId.PERSISTENT_SESSION)
                            if (streaming) add(AiProviderCapabilityId.STREAMING)
                            if (owner.request.structuredJson) add(AiProviderCapabilityId.STRUCTURED_JSON)
                        },
                        persistentSession = true,
                    ),
                )
                val quota = org.autojs.plugin.ai.provider.api.AiProviderQuotaPolicy.validateRequest(
                    typedRequest,
                    owner.plan.pinnedProvider.negotiatedCapabilities,
                    descriptorCount = 0,
                )
                require(quota.inputBytes <= owner.plan.effectiveMaximumContextBytes)
                requestEnvelope = AiProviderCodec.encodeTextRequest(typedRequest)
                require(requestEnvelope.size <= AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES)
                policy = AiProviderSessionPolicy(
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
                        failAsync(AiPluginAskFailure.TIMED_OUT, cancelRemote = true)
                    }
                } catch (_: Throwable) {
                    fail(AiPluginAskFailure.INTERNAL_FAILURE, closeSession = true)
                    return
                }
                if (!deadline.compareAndSet(null, timeout)) timeout.close()
                if (!owner.backend.executeOrchestration { owner.begin(this) }) {
                    fail(AiPluginAskFailure.INTERNAL_FAILURE, closeSession = true)
                }
            }

            override fun cancel(): Boolean =
                fail(AiPluginAskFailure.CANCELLED, closeSession = true, cancelRemote = true)

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
                        fail(AiPluginAskFailure.SESSION_REJECTED, closeSession = true)
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
                            fail(AiPluginAskFailure.CANCELLED, closeSession = true)
                        CallbackEvent.Unsupported ->
                            fail(AiPluginAskFailure.SESSION_REJECTED, closeSession = true)
                    }
                } catch (_: Throwable) {
                    event.close()
                    fail(AiPluginAskFailure.SESSION_REJECTED, closeSession = true)
                }
            }

            private fun started(envelope: ByteArray?) {
                val bytes = requireEnvelope(envelope)
                val metadata: AiSessionStarted = AiProviderCodec.decodeSessionStarted(bytes)
                require(owner.acceptSessionId(metadata.sessionId))
                policy.onStarted(metadata)
                policy.grantCredits(AiProviderLimits.INITIAL_CHUNK_CREDITS)
                owner.grantCredits(this, AiProviderLimits.INITIAL_CHUNK_CREDITS)
                if (streaming) publish(AiPluginStreamEvent.Opened(owner.target))
            }

            private fun chunk(envelope: ByteArray?) {
                val value: AiProviderChunk = AiProviderCodec.decodeTextChunk(requireEnvelope(envelope))
                policy.onChunk(value)
                if (!streaming) {
                    fail(AiPluginAskFailure.SESSION_REJECTED, closeSession = true)
                    return
                }
                publish(
                    AiPluginStreamEvent.Chunk(
                        text = value.textDelta,
                        reasoning = value.reasoningDelta,
                    ),
                )
                policy.grantCredits(1)
                owner.grantCredits(this, 1)
            }

            private fun usage(envelope: ByteArray?) {
                val value = AiCommonCodec.decodeUsage(requireEnvelope(envelope))
                policy.onUsage(value)
                if (streaming) publish(AiPluginStreamEvent.Usage(value.toPluginUsage()))
            }

            private fun completed(
                envelope: ByteArray?,
                incoming: AiProviderSessionIncomingDescriptors?,
            ) {
                val lease = try {
                    incoming?.copyForAsync()
                } finally {
                    incoming?.close()
                }
                try {
                    val result: AiCompletionResult =
                        AiProviderCodec.decodeCompletionResult(requireEnvelope(envelope))
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
                        reasoning = verified.reasoningSummaryText.orEmpty(),
                        usage = finalUsage,
                        finishReason = result.finishReason,
                        closeSession = result.finishReason == AiFinishReason.LENGTH,
                    )
                } finally {
                    lease?.close()
                }
            }

            private fun providerFailed(envelope: ByteArray?) {
                val outcome = runCatching {
                    AiCommonCodec.decodeError(requireEnvelope(envelope)).toPluginAskFailure()
                }.getOrDefault(AiPluginAskOutcome.Failed(AiPluginAskFailure.SESSION_REJECTED))
                fail(outcome.failure, closeSession = true, providerReason = outcome.providerReason)
            }

            private fun complete(
                text: String,
                reasoning: String,
                usage: AiPluginUsage,
                finishReason: Int,
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
                        checkNotNull(onStreamEvent).invoke(
                            AiPluginStreamEvent.Completed(
                                text = text,
                                reasoning = reasoning,
                                usage = usage,
                                finishReason = finishReason,
                                target = owner.target,
                            ),
                        )
                    } else {
                        checkNotNull(onOutcome).invoke(
                            AiPluginAskOutcome.Completed(
                                text = text,
                                reasoning = reasoning,
                                usage = usage,
                                finishReason = finishReason,
                                target = owner.target,
                            ),
                        )
                    }
                }
            }

            fun failAsync(
                failure: AiPluginAskFailure,
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
                failure: AiPluginAskFailure,
                closeSession: Boolean,
                cancelRemote: Boolean = false,
                providerReason: AiPluginProviderFailureReason? = null,
            ): Boolean {
                if (!terminal.compareAndSet(false, true)) return false
                runCatching { policy.onFailed() }
                disposeTurnResources()
                return owner.finishTurn(this, closeSession, cancelRemote) {
                    if (streaming) {
                        checkNotNull(onStreamEvent).invoke(AiPluginStreamEvent.Failed(failure, providerReason))
                    } else {
                        checkNotNull(onOutcome).invoke(AiPluginAskOutcome.Failed(failure, providerReason))
                    }
                }
            }

            fun finishDetached(failure: AiPluginAskFailure) {
                if (!terminal.compareAndSet(false, true)) return
                disposeTurnResources()
                if (streaming) {
                    runCatching {
                        checkNotNull(onStreamEvent).invoke(AiPluginStreamEvent.Failed(failure))
                    }
                } else {
                    runCatching { checkNotNull(onOutcome).invoke(AiPluginAskOutcome.Failed(failure)) }
                }
            }

            private fun publish(event: AiPluginStreamEvent) {
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
            private val owner: ProcessAiPluginSession,
        ) : ServiceConnection, Closeable {
            private val bindingClosed = AtomicBoolean(false)
            private val registered = AtomicBoolean(false)
            private val unbound = AtomicBoolean(false)
            private val connected = AtomicBoolean(false)
            private val linked = AtomicReference<Pair<IBinder, IBinder.DeathRecipient>?>(null)

            fun bind() {
                val didBind = PluginAutoWake.bindServiceWaking(
                    backend.applicationContext,
                    Intent().setComponent(ComponentName(request.component.packageName, request.component.className)),
                    this,
                    Context.BIND_AUTO_CREATE,
                )
                check(didBind) { "Unable to bind the persistent local AI provider" }
                registered.set(true)
                unbindIfClosed()
            }

            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (bindingClosed.get()) return
                if (name != ComponentName(request.component.packageName, request.component.className) ||
                    !connected.compareAndSet(false, true)
                ) {
                    owner.closeWithFailureAsync(AiPluginAskFailure.AI_PROVIDER_REJECTED)
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
                owner.closeWithFailureAsync(AiPluginAskFailure.AI_PROVIDER_REJECTED)

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
            private val session: IAiSession,
            private val binder: IBinder,
            private val recipient: IBinder.DeathRecipient,
        ) : Closeable {
            private val closed = AtomicBoolean(false)
            private val controlLock = Any()

            fun generateNext(requestEnvelope: ByteArray) = synchronized(controlLock) {
                check(!closed.get()) { "Persistent local AI session is closed" }
                session.generateNext(requestEnvelope.copyOf(), emptyArray())
            }

            fun grantCredits(count: Int) = synchronized(controlLock) {
                check(!closed.get()) { "Persistent local AI session is closed" }
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
                    session: IAiSession,
                    onDeath: () -> Unit,
                ): RemoteSession {
                    val binder = session.asBinder()
                    check(binder.isBinderAlive) { "Persistent local AI session Binder is dead" }
                    check(binder.interfaceDescriptor == IAiSession.DESCRIPTOR) {
                        "Persistent local AI session descriptor mismatch"
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
                val descriptors: AiProviderSessionIncomingDescriptors?,
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
                AiProviderSessionOperationObserver { Closeable {} }

            private val BUSY_TURN = Any()
        }
    }

    private sealed class InitialDiscoveryResult {
        data class Found(val snapshot: AiProviderPackageSnapshot) : InitialDiscoveryResult()
        object NotFound : InitialDiscoveryResult()
        object Rejected : InitialDiscoveryResult()
    }

    private companion object {
        const val MAXIMUM_BLOCKING_OPERATIONS = 3

        /** Satisfies request validation for catalog-only attempts; never sent to a provider. */
        const val CATALOG_PLACEHOLDER_PROMPT = "-"

        val HOST_CAPABILITIES = AiProviderCapabilities(
            vision = org.autojs.plugin.ai.provider.api.AiVisionCapabilities(),
            supportsStreaming = true,
            supportsReasoning = true,
            supportsTools = true,
            supportsStructuredJson = true,
            supportsUsage = true,
            maximumMessages = AiProviderLimits.MAX_MESSAGES,
            maximumContentParts = AiProviderLimits.MAX_CONTENT_PARTS,
            maximumToolDefinitions = AiProviderLimits.MAX_TOOL_DEFINITIONS,
            maximumContextBytes = AiProviderLimits.MAX_TOTAL_INPUT_BYTES,
            maximumOutputBytes = AiProviderLimits.MAX_STREAMING_OUTPUT_BYTES,
            maximumToolRounds = AiProviderLimits.MAX_TOOL_ROUNDS,
            maximumOutstandingToolCalls = AiProviderLimits.MAX_OUTSTANDING_TOOL_CALLS,
            maximumRequestDescriptors = AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST,
            maximumSessionDescriptors = AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION,
            acceptedTextMimeTypes = listOf(AiProviderMimeType.PLAIN, AiProviderMimeType.JSON),
            acceptedSchemaMimeTypes = listOf(AiProviderMimeType.JSON),
            supportsPersistentSessions = true,
        )
    }
}

private fun AiTargetInfo.toPluginTargetSummary(
    catalog: AiProviderTargetCatalog,
) = AiPluginTargetSummary(
    targetId = targetId,
    providerId = providerId,
    profileId = profileId,
    modelId = modelId,
    displayName = displayName,
    capabilityIds = capabilityIds,
    locality = locality,
    credentialMode = credentialMode,
    availability = availability,
    configured = configured,
    isDefault = isDefault,
    maximumContextBytes = maximumContextBytes
        ?: catalog.pinnedProvider.negotiatedCapabilities.maximumContextBytes,
    maximumOutputBytes = maximumOutputBytes
        ?: catalog.pinnedProvider.negotiatedCapabilities.maximumOutputBytes,
    supportedControls = supportedControls,
    declaredHttpsOrigins = declaredHttpsOrigins,
    backendProfiles = backendProfiles,
)

private fun AiProviderPackageSnapshot.samePackageIdentityForPersistentSession(
    other: AiProviderPackageSnapshot,
): Boolean =
    component == other.component &&
        packageUid == other.packageUid &&
        versionCode == other.versionCode &&
        lastUpdateTime == other.lastUpdateTime &&
        signerSha256 == other.signerSha256

private fun AiProviderPackageSnapshot.toPersistentSessionCandidate(
    providerInfo: AiProviderInfo,
    capabilities: AiProviderCapabilities,
) = AiProviderCandidate(
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
        AiProviderTrustLevel.OFFICIAL
    } else {
        AiProviderTrustLevel.UNTRUSTED
    },
    sameSignerAsHost = sameSignerAsHost,
    providerInfo = providerInfo,
    capabilities = capabilities,
)

private object CompletedAiPluginAskHandle : AiPluginAskHandle {
    override fun cancel(): Boolean = false

    override fun close() = Unit
}

private class AiPluginAskThreadFactory(
    private val counter: AtomicInteger = AtomicInteger(),
) {
    fun named(role: String): ThreadFactory = ThreadFactory { runnable ->
        Thread(runnable, "AutoJs6-AiPlugin-$role-${counter.incrementAndGet()}")
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
