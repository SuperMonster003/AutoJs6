package org.autojs.autojs.core.plugin.ai

import android.content.Context
import android.os.Build
import android.os.SystemClock
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.text.api.AiContentPart
import org.autojs.plugin.ai.text.api.AiTextCapabilityId
import org.autojs.plugin.ai.text.api.AiGenerationOptions
import org.autojs.plugin.ai.text.api.AiMessage
import org.autojs.plugin.ai.text.api.AiMessageRole
import org.autojs.plugin.ai.text.api.AiTextCapabilities
import org.autojs.plugin.ai.text.api.AiTextLimits
import org.autojs.plugin.ai.text.api.AiTextMimeType
import org.autojs.plugin.ai.text.api.AiTextRequest
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
 * Lazily initialized process owner for the explicit, default-off Android AI text plugin route.
 * Merely constructing a script runtime does not discover or bind a provider.
 */
internal class AndroidAiTextPluginAskRunner private constructor(
    private val applicationContext: Context,
) : AiTextPluginAskRunner, AiTextPluginStreamRunner {

    override fun start(
        request: AiTextPluginAskRequest,
        onOutcome: (AiTextPluginAskOutcome) -> Unit,
    ): AiTextPluginAskHandle = processBackend(applicationContext).start(request, onOutcome)

    override fun startStream(
        request: AiTextPluginAskRequest,
        onEvent: (AiTextPluginStreamEvent) -> Unit,
    ): AiTextPluginAskHandle = processBackend(applicationContext).startStream(request, onEvent)

    companion object {
        fun create(context: Context): AiTextPluginAskRunner =
            AndroidAiTextPluginAskRunner(context.applicationContext)

        fun createStream(context: Context): AiTextPluginStreamRunner =
            AndroidAiTextPluginAskRunner(context.applicationContext)

        private val backendLock = Any()

        @Volatile
        private var sharedBackend: ProcessAiTextPluginAskRunner? = null

        private fun processBackend(context: Context): ProcessAiTextPluginAskRunner {
            sharedBackend?.let { return it }
            return synchronized(backendLock) {
                sharedBackend ?: createProcessBackend(context).also { sharedBackend = it }
            }
        }

        private fun createProcessBackend(context: Context): ProcessAiTextPluginAskRunner =
            ProcessAiTextPluginAskRunner(context.applicationContext)
    }
}

private class ProcessAiTextPluginAskRunner(
    applicationContext: Context,
) : AiTextPluginAskRunner, AiTextPluginStreamRunner {
    private val admission = AtomicBoolean(false)
    private val threadFactory = AiTextPluginAskThreadFactory()
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
    private val discovery = AndroidAiTextProviderDiscovery(applicationContext)
    private val environment = AiTextProviderEnvironment(
        hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
        currentAbi = Build.SUPPORTED_ABIS.firstOrNull(),
        releaseBuild = !BuildConfig.DEBUG,
        allowUserApprovedProviders = false,
    )
    private val handshake = AiTextProviderHandshakeCoordinator(
        transport = AndroidAiTextProviderHandshake(applicationContext),
        workerExecutor = coordinatorExecutor,
        blockingExecutor = blockingExecutor,
        deadlineScheduler = AiTextProviderHandshakeDeadlineScheduler(::schedule),
        clock = AiTextProviderHandshakeClock(::now),
    )
    private val modelListing = AiTextProviderModelListingCoordinator(
        transport = AndroidAiTextProviderModelListing(applicationContext),
        workerExecutor = coordinatorExecutor,
        blockingExecutor = blockingExecutor,
        deadlineScheduler = AiTextProviderModelListingDeadlineScheduler(::schedule),
        clock = AiTextProviderModelListingClock(::now),
        owner = AiTextProviderModelListingProcessOwner(),
    )
    private val session = AiTextProviderSessionCoordinator(
        transport = AndroidAiTextProviderSession(applicationContext),
        workerExecutor = coordinatorExecutor,
        blockingExecutor = blockingExecutor,
        terminalFallbackExecutor = terminalFallbackExecutor,
        deadlineScheduler = AiTextProviderSessionDeadlineScheduler(::schedule),
        clock = AiTextProviderSessionClock(::now),
        owner = AiTextProviderSessionProcessOwner(),
    )

    override fun start(
        request: AiTextPluginAskRequest,
        onOutcome: (AiTextPluginAskOutcome) -> Unit,
    ): AiTextPluginAskHandle {
        if (!admission.compareAndSet(false, true)) {
            deliver {
                onOutcome(AiTextPluginAskOutcome.Failed(AiTextPluginAskFailure.BUSY))
            }
            return CompletedAiTextPluginAskHandle
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
        request: AiTextPluginAskRequest,
        onEvent: (AiTextPluginStreamEvent) -> Unit,
    ): AiTextPluginAskHandle {
        if (!admission.compareAndSet(false, true)) {
            deliver { onEvent(AiTextPluginStreamEvent.Failed(AiTextPluginAskFailure.BUSY)) }
            return CompletedAiTextPluginAskHandle
        }
        return Attempt(
            backend = this,
            request = request,
            streaming = true,
            onOutcome = null,
            onStreamEvent = onEvent,
        ).also(Attempt::start)
    }

    private fun deliver(action: () -> Unit) {
        if (!execute(orchestrationExecutor) { runCatching(action) }) runCatching(action)
    }

    private fun releaseAdmission() {
        check(admission.compareAndSet(true, false)) {
            "AI text plugin route admission was not acquired"
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
    ): AiTextCapabilityRequirement =
        AiTextCapabilityRequirement(
            requiredCapabilityIds = if (streaming) {
                setOf(AiTextCapabilityId.STREAMING)
            } else {
                emptySet()
            },
            minimumMessages = 1,
            minimumContentParts = 1,
            minimumContextBytes = promptBytes.toLong(),
            minimumOutputBytes = 1L,
            minimumRequestDescriptors = 0,
            minimumSessionDescriptors = 0,
            requiredTextMimeTypes = setOf(AiTextMimeType.PLAIN),
        )

    private class Attempt(
        private val backend: ProcessAiTextPluginAskRunner,
        private val request: AiTextPluginAskRequest,
        private val streaming: Boolean,
        private val onOutcome: ((AiTextPluginAskOutcome) -> Unit)?,
        private val onStreamEvent: ((AiTextPluginStreamEvent) -> Unit)?,
    ) : AiTextPluginAskHandle {
        private val lock = Any()
        private val startedAtMillis = backend.now()
        private val deadlineAtMillis = saturatingAdd(startedAtMillis, request.timeoutMillis)
        private val promptBytes = request.prompt.toByteArray(Charsets.UTF_8)
        private val requirement = backend.requirement(promptBytes.size, streaming)

        private var terminal = false
        private var stageHandle: Closeable? = null
        private var deadlineHandle: Closeable? = null

        fun start() {
            val deadline = try {
                backend.schedule(request.timeoutMillis) {
                    finish(AiTextPluginAskOutcome.Failed(AiTextPluginAskFailure.TIMED_OUT))
                }
            } catch (_: Exception) {
                finish(AiTextPluginAskOutcome.Failed(AiTextPluginAskFailure.INTERNAL_FAILURE))
                return
            }
            installDeadline(deadline)
            enqueue(::beginDiscovery)
        }

        override fun cancel(): Boolean =
            finish(AiTextPluginAskOutcome.Failed(AiTextPluginAskFailure.CANCELLED))

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
                finish(AiTextPluginAskOutcome.Failed(AiTextPluginAskFailure.INTERNAL_FAILURE))
            }
        }

        private fun inspectInitialSnapshot(): InitialDiscoveryResult = try {
            when (
                val result = AiTextProviderDiscoveryPolicy.inspectExact(
                    source = backend.discovery,
                    component = request.component,
                    options = AiTextProviderDiscoveryOptions(enabled = true),
                )
            ) {
                AiTextProviderDiscoveryResult.Disabled,
                AiTextProviderDiscoveryResult.WrongAction,
                is AiTextProviderDiscoveryResult.QueryFailed,
                -> InitialDiscoveryResult.Rejected
                is AiTextProviderDiscoveryResult.Completed -> {
                    if (result.evaluations.isEmpty()) {
                        InitialDiscoveryResult.NotFound
                    } else {
                        val discovered = result.evaluations.singleOrNull()
                            as? AiTextProviderDiscoveryEvaluation.Discovered
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
                    AiTextPluginAskOutcome.Failed(AiTextPluginAskFailure.PROVIDER_NOT_FOUND),
                )
                InitialDiscoveryResult.Rejected -> finish(
                    AiTextPluginAskOutcome.Failed(AiTextPluginAskFailure.PROVIDER_REJECTED),
                )
                is InitialDiscoveryResult.Found -> beginHandshake(result.snapshot)
            }
        }

        private fun beginHandshake(snapshot: AiTextProviderPackageSnapshot) {
            if (!isActive()) return
            val remaining = remainingMillis()
            if (remaining <= 0L) {
                finish(AiTextPluginAskOutcome.Failed(AiTextPluginAskFailure.TIMED_OUT))
                return
            }
            val handle = try {
                backend.handshake.start(
                    AiTextProviderHandshakeRequest(
                        snapshot = snapshot,
                        environment = backend.environment,
                        hostCapabilities = HOST_CAPABILITIES,
                        requirement = requirement,
                        options = AiTextProviderHandshakeOptions(
                            enabled = true,
                            timeoutMillis = remaining,
                        ),
                    ),
                ) { outcome -> enqueue { acceptHandshake(snapshot, outcome) } }
            } catch (_: Exception) {
                finish(AiTextPluginAskOutcome.Failed(AiTextPluginAskFailure.INTERNAL_FAILURE))
                return
            }
            installStage(handle)
        }

        private fun acceptHandshake(
            snapshot: AiTextProviderPackageSnapshot,
            outcome: AiTextProviderHandshakeOutcome,
        ) {
            detachStage()
            if (!isActive()) return
            when (outcome) {
                AiTextProviderHandshakeOutcome.Busy -> fail(AiTextPluginAskFailure.BUSY)
                AiTextProviderHandshakeOutcome.Fused -> fail(AiTextPluginAskFailure.FUSED)
                AiTextProviderHandshakeOutcome.Cancelled -> fail(AiTextPluginAskFailure.CANCELLED)
                AiTextProviderHandshakeOutcome.Disabled -> fail(AiTextPluginAskFailure.INTERNAL_FAILURE)
                is AiTextProviderHandshakeOutcome.Rejected -> fail(
                    when (outcome.failure) {
                        AiTextProviderHandshakeFailure.BIND_TIMEOUT,
                        AiTextProviderHandshakeFailure.METADATA_TIMEOUT,
                        -> AiTextPluginAskFailure.TIMED_OUT
                        AiTextProviderHandshakeFailure.BINDER_DIED ->
                            AiTextPluginAskFailure.BINDER_DIED
                        AiTextProviderHandshakeFailure.EXECUTOR_REJECTED,
                        AiTextProviderHandshakeFailure.INTERNAL_FAILURE,
                        -> AiTextPluginAskFailure.INTERNAL_FAILURE
                        else -> AiTextPluginAskFailure.PROVIDER_REJECTED
                    },
                )
                is AiTextProviderHandshakeOutcome.Selected -> {
                    val pinned = outcome.pinned
                    if (pinned.component != request.component ||
                        pinned.providerId != request.providerId ||
                        pinned.locality != AiDataLocality.ON_DEVICE ||
                        pinned.credentialMode != AiCredentialMode.NONE
                    ) {
                        fail(AiTextPluginAskFailure.PROVIDER_REJECTED)
                    } else {
                        beginModelListing(snapshot, pinned)
                    }
                }
            }
        }

        private fun beginModelListing(
            snapshot: AiTextProviderPackageSnapshot,
            pinned: AiTextProviderPinnedIdentity,
        ) {
            if (!isActive()) return
            val remaining = remainingMillis()
            if (remaining <= 0L) {
                fail(AiTextPluginAskFailure.TIMED_OUT)
                return
            }
            val handle = try {
                backend.modelListing.start(
                    AiTextProviderModelListingRunRequest(
                        snapshot = snapshot,
                        pinnedProvider = pinned,
                        environment = backend.environment,
                        hostCapabilities = HOST_CAPABILITIES,
                        requirement = requirement,
                        options = AiTextProviderModelListingRunOptions(
                            enabled = true,
                            timeoutMillis = remaining,
                        ),
                    ),
                ) { outcome -> enqueue { acceptModelListing(snapshot, pinned, outcome) } }
            } catch (_: Exception) {
                fail(AiTextPluginAskFailure.INTERNAL_FAILURE)
                return
            }
            installStage(handle)
        }

        private fun acceptModelListing(
            snapshot: AiTextProviderPackageSnapshot,
            pinned: AiTextProviderPinnedIdentity,
            outcome: AiTextProviderModelListingRunOutcome,
        ) {
            detachStage()
            if (!isActive()) return
            when (outcome) {
                AiTextProviderModelListingRunOutcome.Disabled ->
                    fail(AiTextPluginAskFailure.INTERNAL_FAILURE)
                AiTextProviderModelListingRunOutcome.Busy -> fail(AiTextPluginAskFailure.BUSY)
                AiTextProviderModelListingRunOutcome.Fused -> fail(AiTextPluginAskFailure.FUSED)
                AiTextProviderModelListingRunOutcome.Cancelled ->
                    fail(AiTextPluginAskFailure.CANCELLED)
                AiTextProviderModelListingRunOutcome.TimedOut ->
                    fail(AiTextPluginAskFailure.TIMED_OUT)
                AiTextProviderModelListingRunOutcome.BinderDied ->
                    fail(AiTextPluginAskFailure.BINDER_DIED)
                is AiTextProviderModelListingRunOutcome.ProviderFailed ->
                    fail(AiTextPluginAskFailure.PROVIDER_FAILED)
                is AiTextProviderModelListingRunOutcome.Rejected -> fail(
                    when (outcome.failure) {
                        AiTextProviderModelListingRunFailure.EXECUTOR_REJECTED,
                        AiTextProviderModelListingRunFailure.INTERNAL_FAILURE,
                        -> AiTextPluginAskFailure.INTERNAL_FAILURE
                        else -> AiTextPluginAskFailure.PROVIDER_REJECTED
                    },
                )
                is AiTextProviderModelListingRunOutcome.Completed -> {
                    val catalog = outcome.catalog
                    if (catalog.pinnedProvider != pinned ||
                        catalog.pinnedProvider.providerId != request.providerId
                    ) {
                        fail(AiTextPluginAskFailure.PROVIDER_REJECTED)
                        return
                    }
                    when (val planned = catalog.planExact(request.modelId, requirement)) {
                        AiTextProviderExactModelPlanResult.ModelNotFound ->
                            fail(AiTextPluginAskFailure.MODEL_NOT_FOUND)
                        AiTextProviderExactModelPlanResult.ModelCapabilityMismatch ->
                            fail(AiTextPluginAskFailure.MODEL_CAPABILITY_MISMATCH)
                        is AiTextProviderExactModelPlanResult.Planned -> {
                            val plan = planned.plan
                            if (plan.pinnedProvider != pinned ||
                                plan.pinnedProvider.providerId != request.providerId ||
                                plan.modelId != request.modelId
                            ) {
                                fail(AiTextPluginAskFailure.PROVIDER_REJECTED)
                            } else {
                                beginSession(snapshot, plan)
                            }
                        }
                    }
                }
            }
        }

        private fun beginSession(
            snapshot: AiTextProviderPackageSnapshot,
            plan: AiTextHostSessionPlan,
        ) {
            if (!isActive()) return
            val remaining = remainingMillis()
            if (remaining < AiTextLimits.MIN_TIMEOUT_MILLIS) {
                fail(AiTextPluginAskFailure.TIMED_OUT)
                return
            }
            val outputLimit = minOf(request.maximumOutputBytes, plan.effectiveMaximumOutputBytes)
            val typedRequest = try {
                AiTextRequest(
                    requestId = "${if (streaming) "stream" else "ask"}-${UUID.randomUUID()}",
                    protocolVersion = plan.pinnedProvider.protocolVersion,
                    providerId = request.providerId,
                    modelId = request.modelId,
                    messages = listOf(
                        AiMessage(
                            role = AiMessageRole.USER,
                            parts = listOf(
                                AiContentPart(
                                    AiPayloadReference(
                                        mimeType = AiTextMimeType.PLAIN,
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
                        timeoutMillis = remaining.coerceAtMost(AiTextLimits.MAX_TIMEOUT_MILLIS),
                        responseMimeType = AiTextMimeType.PLAIN,
                        requiredCapabilityIds = if (streaming) {
                            listOf(AiTextCapabilityId.STREAMING)
                        } else {
                            emptyList()
                        },
                    ),
                )
            } catch (_: Exception) {
                fail(AiTextPluginAskFailure.INVALID_REQUEST)
                return
            }
            val descriptors = AndroidAiTextProviderSessionRequestDescriptors(emptyArray())
            val handle = try {
                backend.session.start(
                    AiTextProviderSessionRunRequest(
                        snapshot = snapshot,
                        plan = plan,
                        request = typedRequest,
                        requestDescriptors = descriptors,
                        environment = backend.environment,
                        hostCapabilities = HOST_CAPABILITIES,
                        requirement = requirement,
                        options = AiTextProviderSessionRunOptions(enabled = true),
                    ),
                ) { event -> enqueue { acceptSessionEvent(event) } }
            } catch (_: Exception) {
                descriptors.close()
                fail(AiTextPluginAskFailure.INTERNAL_FAILURE)
                return
            }
            installStage(handle)
        }

        private fun acceptSessionEvent(event: AiTextProviderSessionRunEvent) {
            if (!isActive()) return
            when (event) {
                AiTextProviderSessionRunEvent.Busy -> terminalSession(AiTextPluginAskFailure.BUSY)
                AiTextProviderSessionRunEvent.Fused -> terminalSession(AiTextPluginAskFailure.FUSED)
                is AiTextProviderSessionRunEvent.Rejected ->
                    terminalSession(AiTextPluginAskFailure.SESSION_REJECTED)
                is AiTextProviderSessionRunEvent.Transcript -> when (val transcript = event.event) {
                    AiTextProviderSessionEvent.Disabled ->
                        terminalSession(AiTextPluginAskFailure.INTERNAL_FAILURE)
                    is AiTextProviderSessionEvent.Started -> {
                        if (streaming) publish(AiTextPluginStreamEvent.Opened)
                    }
                    is AiTextProviderSessionEvent.Chunk -> {
                        if (!streaming || transcript.chunk.textDelta.isEmpty()) {
                            terminalSession(AiTextPluginAskFailure.SESSION_REJECTED)
                        } else {
                            publish(AiTextPluginStreamEvent.Chunk(transcript.chunk.textDelta))
                        }
                    }
                    is AiTextProviderSessionEvent.Usage ->
                        terminalSession(AiTextPluginAskFailure.SESSION_REJECTED)
                    is AiTextProviderSessionEvent.Completed -> {
                        detachStage()
                        finish(AiTextPluginAskOutcome.Completed(transcript.result.outputText))
                    }
                    is AiTextProviderSessionEvent.ProviderFailed ->
                        terminalSession(AiTextPluginAskFailure.PROVIDER_FAILED)
                    AiTextProviderSessionEvent.Cancelled ->
                        terminalSession(AiTextPluginAskFailure.CANCELLED)
                    AiTextProviderSessionEvent.TimedOut ->
                        terminalSession(AiTextPluginAskFailure.TIMED_OUT)
                    AiTextProviderSessionEvent.BinderDied ->
                        terminalSession(AiTextPluginAskFailure.BINDER_DIED)
                    is AiTextProviderSessionEvent.Rejected ->
                        terminalSession(AiTextPluginAskFailure.SESSION_REJECTED)
                }
            }
        }

        private fun publish(event: AiTextPluginStreamEvent) {
            if (!streaming || !isActive()) return
            checkNotNull(onStreamEvent).invoke(event)
        }

        private fun terminalSession(failure: AiTextPluginAskFailure) {
            detachStage()
            fail(failure)
        }

        private fun fail(failure: AiTextPluginAskFailure) {
            finish(AiTextPluginAskOutcome.Failed(failure))
        }

        private fun enqueue(action: () -> Unit) {
            if (!backend.executeOrchestration {
                    if (!isActive()) return@executeOrchestration
                    try {
                        action()
                    } catch (_: Exception) {
                        fail(AiTextPluginAskFailure.INTERNAL_FAILURE)
                    }
                }
            ) {
                fail(AiTextPluginAskFailure.INTERNAL_FAILURE)
            }
        }

        private fun installStage(handle: Closeable) {
            val closeNow = synchronized(lock) {
                if (terminal) {
                    true
                } else {
                    check(stageHandle == null) { "AI text plugin route stage overlap" }
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
                    check(deadlineHandle == null) { "AI text plugin route deadline overlap" }
                    deadlineHandle = handle
                    false
                }
            }
            if (closeNow) runCatching { handle.close() }
        }

        private fun finish(outcome: AiTextPluginAskOutcome): Boolean {
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
            if (streaming) {
                val event = when (outcome) {
                    is AiTextPluginAskOutcome.Completed ->
                        AiTextPluginStreamEvent.Completed(outcome.text)
                    is AiTextPluginAskOutcome.Failed ->
                        AiTextPluginStreamEvent.Failed(outcome.failure)
                }
                runCatching { checkNotNull(onStreamEvent).invoke(event) }
            } else {
                runCatching { checkNotNull(onOutcome).invoke(outcome) }
            }
            return true
        }

        private fun isActive(): Boolean = synchronized(lock) { !terminal }

        private fun remainingMillis(): Long = deadlineAtMillis - backend.now()
    }

    private sealed class InitialDiscoveryResult {
        data class Found(val snapshot: AiTextProviderPackageSnapshot) : InitialDiscoveryResult()
        object NotFound : InitialDiscoveryResult()
        object Rejected : InitialDiscoveryResult()
    }

    private companion object {
        const val MAXIMUM_BLOCKING_OPERATIONS = 3

        val HOST_CAPABILITIES = AiTextCapabilities(
            supportsStreaming = true,
            supportsReasoning = true,
            supportsTools = true,
            supportsStructuredJson = true,
            supportsUsage = true,
            maximumMessages = AiTextLimits.MAX_MESSAGES,
            maximumContentParts = AiTextLimits.MAX_CONTENT_PARTS,
            maximumToolDefinitions = AiTextLimits.MAX_TOOL_DEFINITIONS,
            maximumContextBytes = AiTextLimits.MAX_TOTAL_INPUT_BYTES,
            maximumOutputBytes = AiTextLimits.MAX_STREAMING_OUTPUT_BYTES,
            maximumToolRounds = AiTextLimits.MAX_TOOL_ROUNDS,
            maximumOutstandingToolCalls = AiTextLimits.MAX_OUTSTANDING_TOOL_CALLS,
            maximumRequestDescriptors = AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST,
            maximumSessionDescriptors = AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION,
            acceptedTextMimeTypes = listOf(AiTextMimeType.PLAIN, AiTextMimeType.JSON),
            acceptedSchemaMimeTypes = listOf(AiTextMimeType.JSON),
        )
    }
}

private object CompletedAiTextPluginAskHandle : AiTextPluginAskHandle {
    override fun cancel(): Boolean = false

    override fun close() = Unit
}

private class AiTextPluginAskThreadFactory(
    private val counter: AtomicInteger = AtomicInteger(),
) {
    fun named(role: String): ThreadFactory = ThreadFactory { runnable ->
        Thread(runnable, "AutoJs6-AiTextPlugin-$role-${counter.incrementAndGet()}")
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
