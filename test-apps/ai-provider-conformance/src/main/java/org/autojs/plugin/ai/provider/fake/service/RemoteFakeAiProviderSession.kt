package org.autojs.plugin.ai.provider.fake.service

import android.content.Context
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiError
import org.autojs.plugin.ai.common.api.AiErrorCode
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiRetryDisposition
import org.autojs.plugin.ai.provider.api.AiCompletionResult
import org.autojs.plugin.ai.provider.api.AiFinishReason
import org.autojs.plugin.ai.provider.api.AiReasoningMetadata
import org.autojs.plugin.ai.provider.api.AiSessionStarted
import org.autojs.plugin.ai.provider.api.AiProviderChunk
import org.autojs.plugin.ai.provider.api.AiProviderCodec
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiProviderQuotaPolicy
import org.autojs.plugin.ai.provider.api.AiProviderRequest
import org.autojs.plugin.ai.provider.api.AiToolCall
import org.autojs.plugin.ai.provider.api.AiToolCallBatch
import org.autojs.plugin.ai.provider.api.IAiCallback
import org.autojs.plugin.ai.provider.api.IAiSession
import org.autojs.plugin.ai.provider.fake.FakeAiGenerationPlan
import org.autojs.plugin.ai.provider.fake.FakeAiBinderInputPolicy
import org.autojs.plugin.ai.provider.fake.FakeAiHostileScenario
import org.autojs.plugin.ai.provider.fake.FakeAiPayloadMaterializer
import org.autojs.plugin.ai.provider.fake.FakeAiSessionMachine
import org.autojs.plugin.ai.provider.fake.FakeAiProviderEngine
import org.autojs.plugin.ai.provider.fake.FakeAiProviderFixture
import org.autojs.plugin.ai.provider.fake.FakeMaterializedRequest
import org.autojs.plugin.ai.provider.fake.FakeOwnedResourceRegistry
import org.autojs.plugin.ai.provider.fake.FakeTerminalFinalizer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class RemoteFakeAiProviderSession(
    private val context: Context,
    private val ownerUid: Int,
    requestMetadata: ByteArray,
    private val descriptors: OwnedParcelFileDescriptors,
    private val callback: IAiCallback,
    private val callerVerifier: HostCallerVerifier,
    private val worker: ExecutorService,
    private val timeoutScheduler: ScheduledExecutorService,
    private val callbackLane: SerialCallbackLane,
    private val onFinished: (RemoteFakeAiProviderSession) -> Unit,
) : IAiSession.Stub() {
    private val decodedRequest = runCatching { AiProviderCodec.decodeTextRequest(requestMetadata) }
    private val requestHint = decodedRequest.getOrNull()
    private val machine = FakeAiSessionMachine(
        maximumToolRounds = requestHint?.options?.maximumToolRounds ?: 0,
        maximumOutstandingToolCalls = FakeAiProviderFixture.capabilities.maximumOutstandingToolCalls,
    )
    private val callbackBinder = callback.asBinder()
    private val callbackDeathRecipient = IBinder.DeathRecipient { callbackDied() }
    private val callbackDeathLinked = AtomicBoolean(false)
    private val cleaned = AtomicBoolean(false)
    private val timeoutFuture = AtomicReference<Future<*>?>()
    private val futures = ConcurrentLinkedQueue<Future<*>>()
    private val pendingResultDescriptors = FakeOwnedResourceRegistry<OwnedParcelFileDescriptors>()
    private val wrongUidRelayBinding = AtomicReference<IsolatedWrongUidAiCallbackBinding?>()
    // wait/notifyAll require a Java monitor with these methods exposed.
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val signal = java.lang.Object()

    @Volatile
    private var toolResultText: String? = null

    override fun grantCredits(count: Int) {
        callerVerifier.enforceSessionOwner(ownerUid)
        if (!machine.isActive) return
        try {
            machine.grantCredits(count)
            wakeWaiters()
        } catch (_: Throwable) {
            finishFailed(AiErrorCode.PROTOCOL_VIOLATION, "Invalid fake chunk-credit grant")
        }
    }

    override fun submitToolResults(
        results: ByteArray?,
        resultDescriptors: Array<out ParcelFileDescriptor>?,
    ) {
        try {
            callerVerifier.enforceSessionOwner(ownerUid)
        } catch (error: Throwable) {
            OwnedParcelFileDescriptors.closeIncoming(resultDescriptors)
            throw error
        }
        if (results == null || resultDescriptors == null) {
            OwnedParcelFileDescriptors.closeIncoming(resultDescriptors)
            finishFailed(AiErrorCode.PROTOCOL_VIOLATION, "Fake tool result metadata is missing")
            return
        }
        if (!machine.isActive) {
            OwnedParcelFileDescriptors.closeIncoming(resultDescriptors)
            return
        }
        val resultsCopy: ByteArray
        val ownedResults: OwnedParcelFileDescriptors
        try {
            FakeAiBinderInputPolicy.requireEnvelopeSize(results.size)
            FakeAiBinderInputPolicy.requireSessionDescriptorCount(
                descriptorCount = resultDescriptors.size,
                providerMaximum = minOf(
                    FakeAiProviderFixture.capabilities.maximumSessionDescriptors,
                    AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION,
                ),
            )
            resultsCopy = results.copyOf()
            ownedResults = OwnedParcelFileDescriptors.duplicateBeforeAsync(resultDescriptors)
        } catch (_: Throwable) {
            OwnedParcelFileDescriptors.closeIncoming(resultDescriptors)
            finishFailed(AiErrorCode.PROTOCOL_VIOLATION, "Invalid fake tool result")
            return
        }
        try {
            pendingResultDescriptors.register(ownedResults)
        } catch (_: Throwable) {
            ownedResults.close()
            return
        }
        val accepted = submitWork {
            try {
                val batch = AiProviderCodec.decodeToolResultBatch(resultsCopy)
                AiProviderQuotaPolicy.validateToolResults(
                    batch = batch,
                    descriptorCount = ownedResults.count,
                    maximumDescriptors = minOf(
                        FakeAiProviderFixture.capabilities.maximumSessionDescriptors,
                        AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION,
                    ),
                )
                val texts = FakeAiPayloadMaterializer.materializeToolResults(
                    batch,
                    ownedResults::readDeclaredBytes,
                )
                machine.submitToolResults(batch.results.map { it.callId })
                synchronized(signal) {
                    check(toolResultText == null) { "Fake tool result was already stored" }
                    toolResultText = texts.joinToString(separator = "\n")
                    signal.notifyAll()
                }
            } catch (_: Throwable) {
                finishFailed(AiErrorCode.PROTOCOL_VIOLATION, "Invalid fake tool result")
            } finally {
                pendingResultDescriptors.release(ownedResults)
            }
        }
        if (!accepted) pendingResultDescriptors.release(ownedResults)
    }

    override fun generateNext(
        request: ByteArray?,
        descriptors: Array<out ParcelFileDescriptor>?,
    ) {
        try {
            callerVerifier.enforceSessionOwner(ownerUid)
        } catch (error: Throwable) {
            OwnedParcelFileDescriptors.closeIncoming(descriptors)
            throw error
        }
        OwnedParcelFileDescriptors.closeIncoming(descriptors)
        if (machine.isActive) {
            finishFailed(
                AiErrorCode.UNSUPPORTED_CAPABILITY,
                "Persistent fake sessions are not supported",
            )
        }
    }

    override fun cancel() {
        callerVerifier.enforceSessionOwner(ownerUid)
        if (!machine.isActive) return
        if (runCatching { machine.cancel() }.getOrDefault(false)) {
            FakeTerminalFinalizer.run(cleanup = { cleanup(cancelWorkers = true) }) {
                dispatchCallback { callback.onCancelled() }
            }
        }
    }

    override fun close() {
        callerVerifier.enforceSessionOwner(ownerUid)
        machine.close()
        cleanup(cancelWorkers = true)
    }

    fun start() {
        if (!linkCallbackDeath()) return
        scheduleTimeout()
        submitWork(::runSession)
    }

    fun rejectBusy() {
        if (!linkCallbackDeath()) return
        finishFailed(
            code = AiErrorCode.PROVIDER_UNAVAILABLE,
            message = "Fake AI provider already has an active session",
            retryDisposition = AiRetryDisposition.EXPLICIT_NEW_REQUEST_ONLY,
        )
    }

    fun serviceDestroyed() {
        machine.close()
        cleanup(cancelWorkers = true)
    }

    private fun runSession() {
        try {
            val request = decodedRequest.getOrThrow()
            AiProviderQuotaPolicy.validateRequest(
                request = request,
                provider = FakeAiProviderFixture.capabilities,
                descriptorCount = descriptors.count,
            )
            FakeAiProviderFixture.requireSupportedRequest(request)
            val materialized = FakeAiPayloadMaterializer.materializeRequest(
                request,
                descriptors::readDeclaredBytes,
            )
            descriptors.close()
            ensureActive()
            val hostile = FakeAiHostileScenario.fromTargetId(request.targetId)
            if (hostile == null) {
                runNormal(request, materialized)
            } else {
                runHostile(request, materialized, hostile)
            }
        } catch (_: SessionStopped) {
        } catch (_: IllegalArgumentException) {
            finishFailed(AiErrorCode.INVALID_REQUEST, "Fake AI request is invalid")
        } catch (_: IllegalStateException) {
            if (machine.isActive) {
                finishFailed(AiErrorCode.PROTOCOL_VIOLATION, "Fake AI session state is invalid")
            }
        } catch (_: Throwable) {
            if (machine.isActive) {
                finishFailed(AiErrorCode.PROVIDER_FAILED, "Fake AI provider failed")
            }
        }
    }

    private fun runNormal(request: AiProviderRequest, materialized: FakeMaterializedRequest) {
        emitStarted(request)
        // Explicit fixtures for the host's catalog-validated Agent route. The original hostile
        // target IDs are intentionally absent from the public catalog and cannot reach this route.
        if (request.targetId == FakeAiProviderFixture.TARGET_STREAM &&
            materialized.prompt == "agent-conformance:stall" && request.options.stream) {
            val sequence = awaitChunkSequence()
            dispatchCallback {
                callback.onChunk(AiProviderCodec.encodeTextChunk(
                    AiProviderChunk(sequence = sequence, textDelta = "agent-conformance:ready"),
                ))
            }
            awaitStop()
            return
        }
        if (request.targetId == FakeAiProviderFixture.TARGET_ECHO && materialized.prompt == "agent-conformance:fail") {
            finishFailed(AiErrorCode.PROVIDER_FAILED, "Private conformance provider diagnostics")
            return
        }
        val toolResult = if (request.targetId == FakeAiProviderFixture.TARGET_TOOLS) {
            val calls = FakeAiProviderEngine.createToolCalls(request)
            machine.openToolTurn(calls.calls.map { it.callId })
            dispatchCallback {
                callback.onToolCalls(AiProviderCodec.encodeToolCallBatch(calls), emptyArray())
            }
            awaitToolResult()
        } else {
            null
        }
        val plan = FakeAiProviderEngine.plan(request, materialized, toolResult)
        plan.chunks.forEach { chunk ->
            val sequence = awaitChunkSequence()
            dispatchCallback {
                callback.onChunk(
                    AiProviderCodec.encodeTextChunk(
                        AiProviderChunk(
                            sequence = sequence,
                            textDelta = chunk.textDelta,
                            reasoningDelta = chunk.reasoningDelta,
                        ),
                    ),
                )
            }
        }
        if (request.options.reportUsage) {
            dispatchCallback { callback.onUsage(AiCommonCodec.encodeUsage(plan.usage)) }
        }
        finishCompleted(request, plan)
    }

    private fun runHostile(
        request: AiProviderRequest,
        materialized: FakeMaterializedRequest,
        scenario: FakeAiHostileScenario,
    ) {
        require(request.options.stream) { "Fake hostile sessions require streaming credits" }
        // Initial credits are the fixture's explicit proof that synchronous openSession returned
        // and the host committed the remote. Hostile callbacks must exercise the active Android
        // transport, not race the provisional callback-before-return window.
        val initialCreditWindow = awaitInitialCredits()
        val plan = FakeAiProviderEngine.plan(request, materialized)
        when (scenario) {
            FakeAiHostileScenario.CHUNK_BEFORE_START -> {
                dispatchHostileChunk(sequence = 0L)
                emitStarted(request)
                finishCompleted(request, plan)
            }
            FakeAiHostileScenario.SEQUENCE_GAP -> {
                emitStarted(request)
                dispatchHostileChunk(awaitChunkSequence())
                val reserved = awaitChunkSequence()
                dispatchHostileChunk(reserved + 1L)
                finishCompleted(request, plan)
            }
            FakeAiHostileScenario.NO_CREDIT -> {
                emitStarted(request)
                repeat(AiProviderLimits.MAX_OUTSTANDING_CHUNK_CREDITS + 1) { sequence ->
                    dispatchHostileChunk(sequence.toLong())
                }
                finishCompleted(request, plan)
            }
            FakeAiHostileScenario.DUPLICATE_TERMINAL -> {
                emitStarted(request)
                finishCompleted(request, plan, callbackCount = 2)
            }
            FakeAiHostileScenario.INVALID_FD_REFERENCE -> {
                emitStarted(request)
                val invalidOutput = AiPayloadReference(
                    mimeType = AiProviderMimeType.PLAIN,
                    declaredLengthBytes = 1L,
                    descriptorIndex = 0,
                    charset = "utf-8",
                )
                finishCompleted(
                    request = request,
                    plan = plan,
                    resultOverride = AiCompletionResult(invalidOutput, AiFinishReason.STOP),
                )
            }
            FakeAiHostileScenario.COMPLETION_FD -> {
                emitStarted(request)
                finishDescriptorCompleted(request, plan)
            }
            FakeAiHostileScenario.TOOL_FD -> {
                emitStarted(request)
                emitDescriptorToolCalls()
                awaitStop()
            }
            FakeAiHostileScenario.WRONG_UID -> {
                dispatchWrongUidStarted(request)
                awaitStop()
            }
            FakeAiHostileScenario.PROCESS_DEATH -> {
                emitStarted(request)
                // One reservation lowers the captured host window. Seeing that exact window again
                // is the causal acknowledgement that the host delivered this chunk and replenished.
                dispatchHostileChunk(awaitChunkSequence())
                awaitCredits(initialCreditWindow)
                Process.killProcess(Process.myPid())
            }
            FakeAiHostileScenario.STALL -> {
                emitStarted(request)
                awaitStop()
            }
        }
    }

    private fun finishDescriptorCompleted(
        request: AiProviderRequest,
        plan: FakeAiGenerationPlan,
    ) {
        val outputBytes = plan.output.toByteArray(Charsets.UTF_8)
        val output = AiPayloadReference(
            mimeType = request.options.responseMimeType,
            declaredLengthBytes = outputBytes.size.toLong(),
            sha256 = sha256(outputBytes),
            descriptorIndex = 0,
            charset = "utf-8".takeIf { request.options.responseMimeType == AiProviderMimeType.PLAIN },
        )
        val result = AiCompletionResult(
            output = output,
            finishReason = AiFinishReason.STOP,
            usage = plan.usage.takeIf {
                request.options.reportUsage || request.options.maximumOutputTokens != null
            },
            reasoning = plan.reasoningSummary?.let { summary ->
                AiReasoningMetadata(
                    summary = summary,
                    reasoningTokens = plan.usage.reasoningTokens,
                    redacted = false,
                )
            },
        )
        val encoded = AiProviderCodec.encodeCompletionResult(result)
        if (!machine.complete()) return
        FakeTerminalFinalizer.run(cleanup = { cleanup(cancelWorkers = false) }) {
            dispatchPlainDescriptor(outputBytes) { descriptor ->
                callback.onCompleted(encoded, arrayOf(descriptor))
            }
        }
    }

    private fun emitDescriptorToolCalls() {
        val argumentBytes = TOOL_ARGUMENTS.toByteArray(Charsets.UTF_8)
        val calls = AiToolCallBatch(
            listOf(
                AiToolCall(
                    callId = FakeAiProviderEngine.TOOL_CALL_ID,
                    name = TOOL_NAME,
                    arguments = AiPayloadReference(
                        mimeType = AiProviderMimeType.JSON,
                        declaredLengthBytes = argumentBytes.size.toLong(),
                        sha256 = sha256(argumentBytes),
                        descriptorIndex = 0,
                    ),
                ),
            ),
        )
        val encoded = AiProviderCodec.encodeToolCallBatch(calls)
        dispatchReliableDescriptor(argumentBytes) { descriptor ->
            callback.onToolCalls(encoded, arrayOf(descriptor))
        }
    }

    /**
     * Publishes one small ordinary-pipe payload for cross-process ownership/materialization tests.
     * The fixture writes before publishing, so callers must keep this payload well below pipe
     * capacity. Reliable producer-status transport has a separate, process-local conformance gate.
     */
    private fun dispatchPlainDescriptor(
        bytes: ByteArray,
        publish: (ParcelFileDescriptor) -> Unit,
    ) {
        require(bytes.size <= 4 * 1024) { "Pre-buffered fixture payload is too large" }
        val safeBytes = bytes.copyOf()
        dispatchCallback {
            val pipe = ParcelFileDescriptor.createPipe()
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                    output.write(safeBytes)
                }
                publish(pipe[0])
            } finally {
                pipe.forEach { descriptor -> runCatching(descriptor::close) }
            }
        }
    }

    /** Publishes one complete reliable-pipe reader and always closes both sender-side endpoints. */
    private fun dispatchReliableDescriptor(
        bytes: ByteArray,
        publish: (ParcelFileDescriptor) -> Unit,
    ) {
        val safeBytes = bytes.copyOf()
        dispatchCallback {
            val pipe = ParcelFileDescriptor.createReliablePipe()
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                    output.write(safeBytes)
                }
                publish(pipe[0])
            } finally {
                pipe.forEach { descriptor -> runCatching(descriptor::close) }
            }
        }
    }

    private fun emitStarted(request: AiProviderRequest) {
        machine.start(firstChunkSequence = 0L)
        val metadata = AiSessionStarted(
            requestId = request.requestId,
            sessionId = "fake-session",
            protocolVersion = request.protocolVersion,
            providerId = FakeAiProviderFixture.PROVIDER_ID,
            targetId = request.targetId,
            firstChunkSequence = 0L,
            effectiveMaximumOutputBytes = request.options.maximumOutputBytes,
            effectiveMaximumToolRounds = request.options.maximumToolRounds,
        )
        dispatchCallback { callback.onStarted(AiProviderCodec.encodeSessionStarted(metadata)) }
    }

    private fun dispatchWrongUidStarted(request: AiProviderRequest) {
        val metadata = AiSessionStarted(
            requestId = request.requestId,
            sessionId = "fake-isolated-session",
            protocolVersion = request.protocolVersion,
            providerId = FakeAiProviderFixture.PROVIDER_ID,
            targetId = request.targetId,
            firstChunkSequence = 0L,
            effectiveMaximumOutputBytes = request.options.maximumOutputBytes,
            effectiveMaximumToolRounds = request.options.maximumToolRounds,
        )
        val binding = IsolatedWrongUidAiCallbackBinding(
            context = context,
            callback = callback,
            startedEnvelope = AiProviderCodec.encodeSessionStarted(metadata),
        )
        check(wrongUidRelayBinding.compareAndSet(null, binding)) {
            "Fake isolated callback relay was already installed"
        }
        if (!binding.bindAndAwaitAcknowledgement() && machine.isActive) {
            finishFailed(AiErrorCode.PROVIDER_FAILED, "Fake isolated callback relay is unavailable")
        }
    }

    private fun dispatchHostileChunk(sequence: Long) {
        dispatchCallback {
            callback.onChunk(
                AiProviderCodec.encodeTextChunk(
                    AiProviderChunk(sequence = sequence, textDelta = "hostile"),
                ),
            )
        }
    }

    private fun finishCompleted(
        request: AiProviderRequest,
        plan: FakeAiGenerationPlan,
        callbackCount: Int = 1,
        resultOverride: AiCompletionResult? = null,
    ) {
        if (!machine.complete()) return
        FakeTerminalFinalizer.run(cleanup = { cleanup(cancelWorkers = false) }) {
            val result = resultOverride ?: AiCompletionResult(
                output = plan.outputPayload,
                finishReason = AiFinishReason.STOP,
                usage = plan.usage.takeIf {
                    request.options.reportUsage || request.options.maximumOutputTokens != null
                },
                reasoning = plan.reasoningSummary?.let { summary ->
                    AiReasoningMetadata(
                        summary = summary,
                        reasoningTokens = plan.usage.reasoningTokens,
                        redacted = false,
                    )
                },
            )
            if (resultOverride == null) {
                AiProviderQuotaPolicy.validateCompletion(
                    request = request,
                    result = result,
                    descriptorCount = 0,
                    maximumDescriptors = FakeAiProviderFixture.capabilities.maximumSessionDescriptors,
                    observedUsage = plan.usage.takeIf { request.options.reportUsage },
                )
            }
            val encoded = AiProviderCodec.encodeCompletionResult(result)
            repeat(callbackCount) {
                dispatchCallback { callback.onCompleted(encoded, emptyArray()) }
            }
        }
    }

    private fun finishFailed(
        code: Int,
        message: String,
        retryDisposition: Int = AiRetryDisposition.NEVER,
    ) {
        if (!machine.isActive) return
        if (!runCatching { machine.fail() }.getOrDefault(false)) return
        FakeTerminalFinalizer.run(cleanup = { cleanup(cancelWorkers = true) }) {
            val error = AiError(code = code, message = message, retryDisposition = retryDisposition)
            dispatchCallback { callback.onFailed(AiCommonCodec.encodeError(error)) }
        }
    }

    private fun awaitChunkSequence(): Long {
        while (true) {
            ensureActive()
            machine.reserveNextChunk()?.let { return it }
            synchronized(signal) {
                if (machine.isActive && machine.creditCount() == 0) {
                    signal.wait()
                }
            }
        }
    }

    private fun awaitInitialCredits(): Int {
        while (true) {
            ensureActive()
            synchronized(signal) {
                val available = machine.creditCount()
                if (available > 0) return available
                if (machine.isActive) signal.wait()
            }
        }
    }

    private fun awaitCredits(minimum: Int) {
        require(minimum > 0)
        while (true) {
            ensureActive()
            synchronized(signal) {
                if (machine.creditCount() >= minimum) return
                if (machine.isActive) signal.wait()
            }
        }
    }

    private fun awaitToolResult(): String {
        while (true) {
            ensureActive()
            toolResultText?.let { return it }
            synchronized(signal) {
                if (machine.isActive && toolResultText == null) {
                    signal.wait()
                }
            }
        }
    }

    private fun awaitStop(): Nothing {
        while (true) {
            ensureActive()
            synchronized(signal) {
                if (machine.isActive) signal.wait()
            }
        }
    }

    private fun dispatchCallback(block: () -> Unit) {
        callbackLane.dispatch(block, onFailure = { callbackFailed() })
    }

    private fun callbackFailed() {
        machine.close()
        cleanup(cancelWorkers = true)
    }

    private fun callbackDied() {
        machine.close()
        cleanup(cancelWorkers = true)
    }

    private fun linkCallbackDeath(): Boolean {
        if (!callbackDeathLinked.compareAndSet(false, true)) return machine.isActive
        try {
            callbackBinder.linkToDeath(callbackDeathRecipient, 0)
        } catch (_: RemoteException) {
            callbackDeathLinked.set(false)
            callbackDied()
            return false
        }
        if (!callbackBinder.isBinderAlive) {
            callbackDied()
            return false
        }
        return true
    }

    private fun unlinkCallbackDeath() {
        if (!callbackDeathLinked.compareAndSet(true, false)) return
        runCatching { callbackBinder.unlinkToDeath(callbackDeathRecipient, 0) }
    }

    private fun submitWork(block: () -> Unit): Boolean {
        if (!machine.isActive) return false
        val future = try {
            worker.submit(block)
        } catch (_: RejectedExecutionException) {
            finishFailed(AiErrorCode.PROVIDER_FAILED, "Fake AI worker is unavailable")
            return false
        }
        futures += future
        if (!machine.isActive) future.cancel(true)
        return true
    }

    private fun cleanup(cancelWorkers: Boolean) {
        if (!cleaned.compareAndSet(false, true)) return
        timeoutFuture.getAndSet(null)?.cancel(false)
        descriptors.close()
        pendingResultDescriptors.close()
        wrongUidRelayBinding.getAndSet(null)?.close()
        unlinkCallbackDeath()
        wakeWaiters()
        if (cancelWorkers) futures.forEach { it.cancel(true) }
        onFinished(this)
    }

    private fun scheduleTimeout() {
        val timeoutMillis = requestHint?.options?.timeoutMillis ?: return
        val scheduled = try {
            timeoutScheduler.schedule(
                {
                    finishFailed(
                        code = AiErrorCode.TIMEOUT,
                        message = "Fake AI request timed out",
                    )
                },
                timeoutMillis,
                TimeUnit.MILLISECONDS,
            )
        } catch (_: RejectedExecutionException) {
            finishFailed(AiErrorCode.PROVIDER_FAILED, "Fake AI timeout scheduler is unavailable")
            return
        }
        if (!timeoutFuture.compareAndSet(null, scheduled) || cleaned.get()) {
            scheduled.cancel(false)
        }
    }

    private fun wakeWaiters() {
        synchronized(signal) { signal.notifyAll() }
    }

    private fun ensureActive() {
        if (!machine.isActive || Thread.currentThread().isInterrupted) throw SessionStopped()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private class SessionStopped : RuntimeException()

    private companion object {
        const val TOOL_NAME = "lookup"
        const val TOOL_ARGUMENTS = "{\"fixture\":true}"
    }
}
