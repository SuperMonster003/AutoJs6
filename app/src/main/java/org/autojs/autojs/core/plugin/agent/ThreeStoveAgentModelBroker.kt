package org.autojs.autojs.core.plugin.agent

import android.content.Context
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.autojs.autojs.core.plugin.ai.AiPluginAskHandle
import org.autojs.autojs.core.plugin.ai.AiPluginAskOutcome
import org.autojs.autojs.core.plugin.ai.AiPluginAskRequest
import org.autojs.autojs.core.plugin.ai.AiPluginAskRunner
import org.autojs.autojs.core.plugin.ai.AiPluginCatalogOutcome
import org.autojs.autojs.core.plugin.ai.AiPluginCatalogRequest
import org.autojs.autojs.core.plugin.ai.AiPluginCatalogRunner
import org.autojs.autojs.core.plugin.ai.AiPluginStreamEvent
import org.autojs.autojs.core.plugin.ai.AiPluginStreamRunner
import org.autojs.autojs.core.plugin.ai.AiPluginTargetSummary
import org.autojs.autojs.core.plugin.ai.AiPluginUsage
import org.autojs.autojs.core.plugin.ai.AiPluginImage
import org.autojs.autojs.core.plugin.ai.AiPluginImageDecoder
import org.autojs.autojs.core.plugin.ai.AiPluginMessage
import org.autojs.autojs.core.plugin.ai.AiPluginToolResult
import org.autojs.plugin.ai.provider.api.AiProviderImagePolicy
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import org.autojs.autojs.core.plugin.ai.AiPluginToolCall
import org.autojs.autojs.core.plugin.ai.AiPluginToolRunner
import org.autojs.autojs.core.plugin.ai.AiProviderComponent
import org.autojs.autojs.core.plugin.ai.AndroidAiPluginAskRunner
import org.autojs.autojs.core.plugin.ai.ThreeStoneAiOfficialPlugin
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityGrant
import org.autojs.autojs.util.OsConstantsCompat
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentModelBroker
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentModelCallback
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiTargetAvailability
import org.autojs.plugin.host.capability.api.HostCapabilityContract
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One link's bounded, UID-bound model proxy. Only this host-owned selection chooses a Provider;
 * the Agent supplies public target IDs and cannot obtain credentials or bind a Provider directly.
 * Provider discovery, trust pinning and revalidation remain in [AndroidAiPluginAskRunner].
 */
internal class ThreeStoveAgentModelBroker(
    context: Context,
    private val expectedCallerUid: Int,
    private val grant: HostCapabilityGrant,
    private val component: AiProviderComponent = AiProviderComponent(
        ThreeStoneAiOfficialPlugin.PACKAGE_NAME, ThreeStoneAiOfficialPlugin.CLASS_NAME),
    private val providerId: String = ThreeStoneAiOfficialPlugin.PROVIDER_ID,
    private val askRunner: AiPluginAskRunner = AndroidAiPluginAskRunner.create(context),
    private val streamRunner: AiPluginStreamRunner = AndroidAiPluginAskRunner.createStream(context),
    private val catalogRunner: AiPluginCatalogRunner = AndroidAiPluginAskRunner.createCatalog(context),
    private val toolRunner: AiPluginToolRunner = AndroidAiPluginAskRunner.createTools(context),
) : IThreeStoveAgentModelBroker.Stub(), Closeable {
    private val applicationContext = context.applicationContext
    private val closed = AtomicBoolean()
    private val operations = ConcurrentHashMap<Operation, Boolean>()
    private val admissionLock = Any()
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(128),
        { Thread(it, "three-stove-agent-model").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    private val deadlines = ScheduledThreadPoolExecutor(1) {
        Thread(it, "three-stove-agent-model-deadline").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }
    private val quota = ThreeStoveAgentModelQuota(grant.modelCallsPerMinute, grant.maxTotalTokens, SystemClock::elapsedRealtime)

    init { require(expectedCallerUid >= 0) }

    override fun getBrokerInfo(): Bundle {
        enforceCaller()
        return envelope().apply {
            val info = JsonObject().apply {
                addProperty("available", !closed.get())
                addProperty("providerId", providerId)
                addProperty("maximumInputBytes", grant.maxInputBytesPerRequest)
                addProperty("maximumOutputBytes", ThreeStoveAgentContract.MAX_MODEL_OUTPUT_BYTES)
                addProperty("maximumResponseSchemaBytes", ThreeStoveAgentContract.MAX_RESPONSE_SCHEMA_BYTES)
                addProperty(ThreeStoveAgentContract.MODEL_INFO_TOOL_CALLING_VERSION, ThreeStoveAgentContract.MODEL_TOOL_CALLING_VERSION)
                addProperty("maximumToolRounds", ThreeStoveAgentContract.MAX_MODEL_TOOL_ROUNDS)
                addProperty("maximumToolResultBytes", ThreeStoveAgentContract.MAX_MODEL_TOOL_RESULT_BYTES)
                addProperty("maximumToolResultBatchBytes", ThreeStoveAgentContract.MAX_MODEL_TOOL_RESULTS_BYTES)
                addProperty(ThreeStoveAgentContract.MODEL_INFO_VISION_VERSION, ThreeStoveAgentContract.MODEL_VISION_VERSION)
                addProperty("maximumImages", ThreeStoveAgentContract.MAX_MODEL_IMAGES)
                addProperty("maximumImageBytes", ThreeStoveAgentContract.MAX_MODEL_IMAGE_BYTES)
                addProperty("maximumTotalImageBytes", ThreeStoveAgentContract.MAX_MODEL_TOTAL_IMAGE_BYTES)
                addProperty("maximumImageEdge", AiProviderLimits.MAX_IMAGE_EDGE)
                addProperty("maximumImagePixels", AiProviderLimits.MAX_IMAGE_PIXELS)
                addProperty("maximumSessionImages", AiProviderLimits.MAX_SESSION_IMAGES)
                addProperty("maximumSessionImageBytes", AiProviderLimits.MAX_SESSION_IMAGE_BYTES)
            }
            putString(ThreeStoveAgentContract.KEY_MODEL_BROKER_INFO_JSON, info.toString())
            putString(HostCapabilityContract.KEY_GRANT_JSON, JsonObject().apply {
                addProperty("modelCallsPerMinute", grant.modelCallsPerMinute)
                addProperty("maxTotalTokens", grant.maxTotalTokens)
                addProperty("maxInputBytesPerRequest", grant.maxInputBytesPerRequest)
                addProperty("consumedTokens", quota.consumedTokens())
            }.toString())
        }
    }

    override fun listTargets(request: Bundle?, callback: IThreeStoveAgentModelCallback?) = submit(request, callback, catalogOnly = true)

    override fun generate(request: Bundle?, callback: IThreeStoveAgentModelCallback?) = submit(request, callback, catalogOnly = false)

    override fun submitToolResults(request: Bundle?) {
        try { enforceCaller() } catch (failure: Exception) { closeBundleDescriptor(request); throw failure }
        val payload = takePayload(request, ThreeStoveAgentContract.KEY_MODEL_REQUEST_JSON, allowDescriptor = true)
        val id = payload.correlationId ?: payload.inline?.let(ThreeStoveAgentModelRequests::recoverId).orEmpty()
        val operation = operations.keys.singleOrNull { it.requestId == id }
        if (operation == null || !operation.claimToolSubmission()) { payload.close(); operation?.fail(ThreeStoveAgentContract.ERROR_INVALID_REQUEST); return }
        operation.submitTools(payload)
    }

    override fun cancel(ref: Bundle?) {
        try { enforceCaller() } catch (failure: Exception) { closeBundleDescriptor(ref); throw failure }
        val payload = takePayload(ref, ThreeStoveAgentContract.KEY_MODEL_REF_JSON, allowDescriptor = false)
        val id = try { ThreeStoveAgentModelRequests.reference(payload.inline.orEmpty()) } finally { payload.close() }
        operations.keys.filter { it.requestId == id }.forEach { operation ->
            // Do not queue cancellation behind a sender-controlled payload pipe.
            operation.fail(ThreeStoveAgentContract.ERROR_CANCELLED)
        }
    }

    override fun destroy(reason: Bundle?) {
        try { enforceCaller() } catch (failure: Exception) { closeBundleDescriptor(reason); throw failure }
        // Close unexpected descriptors even when a caller supplies an otherwise unused reason.
        closeBundleDescriptor(reason)
        close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        operations.keys.forEach { it.fail(ThreeStoveAgentContract.ERROR_LINK_DETACHED) }
        worker.shutdownNow()
        deadlines.shutdownNow()
    }

    private fun enforceCaller() {
        if (Binder.getCallingUid() != expectedCallerUid) throw SecurityException("3-Stove Agent model broker caller UID is not authorized")
    }

    private fun submit(request: Bundle?, callback: IThreeStoveAgentModelCallback?, catalogOnly: Boolean) {
        try { enforceCaller() } catch (failure: Exception) {
            closeBundleDescriptor(request)
            throw failure
        }
        if (callback == null) { closeBundleDescriptor(request); return }
        val payload = try {
            takePayload(request, ThreeStoveAgentContract.KEY_MODEL_REQUEST_JSON, allowDescriptor = !catalogOnly)
        } catch (failure: ThreeStoveAgentModelException) {
            reject(callback, "", failure.code)
            return
        }
        val operation = Operation(callback, payload, catalogOnly)
        synchronized(admissionLock) {
            if (closed.get() || operations.size >= MAX_PENDING_OPERATIONS) {
                payload.close()
                reject(callback, payload.correlationId ?: payload.inline?.let(ThreeStoveAgentModelRequests::recoverId).orEmpty(),
                    if (closed.get()) ThreeStoveAgentContract.ERROR_LINK_DETACHED else ThreeStoveAgentContract.ERROR_RATE_LIMITED)
                return
            }
            operations[operation] = true
        }
        if (!operation.watchCallback()) return
        operation.armDeadline(ThreeStoveAgentModelRequests.DEFAULT_TIMEOUT_MS)
        enqueue(operation) { operation.start() }
    }

    private fun reject(callback: IThreeStoveAgentModelCallback, id: String, code: String) {
        // Rejections remain bounded and contain no caller-supplied text beyond the validated ID.
        try {
            callback.onEvent(eventBundle(event(id, "started", 1)))
            callback.onEvent(eventBundle(event(id, "failed", 2).apply { addProperty("code", code) }))
        } catch (_: Exception) { /* A dead recipient owns no operation or Provider handle. */ }
    }

    private fun enqueue(operation: Operation, block: () -> Unit) {
        try { worker.execute { if (!operation.isFinished()) block() } } catch (_: RejectedExecutionException) {
            operation.fail(if (closed.get()) ThreeStoveAgentContract.ERROR_LINK_DETACHED else ThreeStoveAgentContract.ERROR_RATE_LIMITED)
        }
    }

    private inner class Operation(
        private val callback: IThreeStoveAgentModelCallback,
        private val payload: Payload,
        private val catalogOnly: Boolean,
    ) {
        @Volatile var requestId = payload.correlationId ?: payload.inline?.let(ThreeStoveAgentModelRequests::recoverId).orEmpty()
            private set
        private val lock = Any()
        private val sequence = ThreeStoveAgentModelEventSequence()
        private val startedAt = SystemClock.elapsedRealtime()
        private var finished = false
        private var started = false
        private var watching = false
        private var deadline: Future<*>? = null
        private var handle: AiPluginAskHandle? = null
        private var request: ThreeStoveAgentModelRequest? = null
        private var reservation = false
        private var usage: AiPluginUsage? = null
        private var outputBytes = 0L
        private var chunkSequence = 0L
        private var toolRound = 0
        private var pendingToolIds = emptySet<String>()
        private var toolSubmissionClaimed = false
        private var toolOutputBytes = 0L
        private var toolResultBytes = 0L
        private var cumulativeInputBytes = 0L
        private var retainedImageTokens = 0L
        private var cumulativeImageTokens = 0L
        private var sessionImageCount = 0
        private var sessionImageBytes = 0L
        private var chargedTokens = 0L
        private var contextLimit = 0L
        private var usageUpdated = false
        private var submittedPayload: Payload? = null
        private val deathRecipient = IBinder.DeathRecipient { abandon() }

        fun isFinished(): Boolean = synchronized(lock) { finished }

        fun watchCallback(): Boolean = synchronized(lock) {
            if (finished) return false
            try {
                callback.asBinder().linkToDeath(deathRecipient, 0)
                watching = true
                true
            } catch (_: Exception) { abandon(); false }
        }

        fun armDeadline(timeoutMs: Long): Unit = synchronized(lock) {
            if (finished) return
            deadline?.cancel(false)
            try {
                deadline = deadlines.schedule({ fail(ThreeStoveAgentContract.ERROR_MODEL_TIMEOUT) },
                    (timeoutMs - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            } catch (_: RejectedExecutionException) { fail(ThreeStoveAgentContract.ERROR_LINK_DETACHED) }
        }

        fun start() {
            try {
                val json = payload.read(::isFinished)
                val timeout: Long
                if (catalogOnly) {
                    val decoded = ThreeStoveAgentModelRequests.catalog(json)
                    requestId = decoded.first
                    timeout = decoded.second
                } else {
                    val decoded = ThreeStoveAgentModelRequests.decode(json, grant.maxInputBytesPerRequest)
                    payload.validateImages(decoded.imageRefs)
                    if (payload.correlationId != null && payload.correlationId != decoded.requestId) {
                        throw ThreeStoveAgentModelException(ThreeStoveAgentContract.ERROR_INVALID_REQUEST)
                    }
                    request = decoded
                    requestId = decoded.requestId
                    timeout = decoded.timeoutMs
                }
                synchronized(lock) {
                    if (finished) return
                    emitStarted()
                    if (operations.keys.any { it !== this && it.requestId == requestId }) {
                        fail(ThreeStoveAgentContract.ERROR_INVALID_REQUEST)
                        return
                    }
                }
                armDeadline(timeout)
                synchronized(lock) {
                    if (finished) return
                    install(catalogRunner.listCatalog(AiPluginCatalogRequest(component, providerId, remainingTimeout(timeout))) { outcome ->
                        enqueue(this) {
                            when (outcome) {
                                is AiPluginCatalogOutcome.Failed -> fail(outcome.failure.agentModelError(), outcome.failure.name)
                                is AiPluginCatalogOutcome.Completed -> {
                                    if (catalogOnly) completeCatalog(outcome.catalog.targets)
                                    else generateWithTarget(outcome.catalog.targets)
                                }
                            }
                        }
                    })
                }
            } catch (failure: ThreeStoveAgentModelException) { fail(failure.code)
            } catch (_: Exception) { fail(ThreeStoveAgentContract.ERROR_INVALID_REQUEST) }
        }

        private fun completeCatalog(targets: List<AiPluginTargetSummary>) {
            val publicTargets = JsonArray().apply {
                targets.forEach { target -> add(JsonObject().apply {
                    addProperty("targetId", target.targetId)
                    addProperty("displayName", target.displayName)
                    addProperty("locality", target.locality)
                    add("capabilityIds", JsonArray().apply { target.capabilityIds.forEach { add(it) } })
                    addProperty("configured", target.configured)
                    addProperty("available", target.availability == AiTargetAvailability.AVAILABLE)
                    addProperty("maximumContextBytes", target.maximumContextBytes)
                    add("supportedControls", JsonArray().apply { target.supportedControls.forEach { add(it) } })
                }) }
            }
            finish(ThreeStoveAgentContract.MODEL_EVENT_COMPLETED, JsonObject().apply { add("targets", publicTargets) })
        }

        private fun generateWithTarget(targets: List<AiPluginTargetSummary>) {
            val decoded = request ?: return fail(ThreeStoveAgentContract.ERROR_INVALID_REQUEST)
            val target = targets.firstOrNull { it.targetId == decoded.targetId }
                ?: return fail(ThreeStoveAgentContract.ERROR_TARGET_UNAVAILABLE)
            if (!target.configured || target.availability != AiTargetAvailability.AVAILABLE) {
                return fail(ThreeStoveAgentContract.ERROR_TARGET_UNAVAILABLE)
            }
            if ((decoded.responseSchemaJson != null && AiProviderCapabilityId.STRUCTURED_JSON !in target.capabilityIds) ||
                (decoded.stream && AiProviderCapabilityId.STREAMING !in target.capabilityIds) ||
                (decoded.tools.isNotEmpty() && AiProviderCapabilityId.TOOLS !in target.capabilityIds) ||
                (decoded.vision && AiProviderCapabilityId.VISION !in target.capabilityIds)) {
                return fail(ThreeStoveAgentContract.ERROR_TARGET_UNSUPPORTED)
            }
            if (decoded.inputBytes > target.maximumContextBytes) return fail(ThreeStoveAgentContract.ERROR_QUOTA_EXCEEDED)
            synchronized(lock) {
                if (finished) return
                reservation = quota.reserve(requestId, decoded.inputBytes, decoded.maximumOutputTokens, decoded.imageTokens)
                if (!reservation) return fail(ThreeStoveAgentContract.ERROR_QUOTA_EXCEEDED)
                cumulativeInputBytes = decoded.inputBytes
                retainedImageTokens = decoded.imageTokens
                cumulativeImageTokens = decoded.imageTokens
                sessionImageCount = decoded.imageRefs.size
                sessionImageBytes = decoded.imageRefs.sumOf { it.part.payload.declaredLengthBytes }
                contextLimit = minOf(target.maximumContextBytes, grant.maxInputBytesPerRequest.toLong())
            }
            val actualRequest = try {
                // Descriptor reads must not hold the operation lock: cancel/deadline can close them.
                val images = payload.readImages(decoded.imageRefs, ::isFinished)
                val messages = decoded.messages.mapIndexed { index, message ->
                    AiPluginMessage(message.role, message.content, decoded.imageRefs.filter { it.messageIndex == index }
                        .map { checkNotNull(images[it.descriptorIndex]) })
                }
                AiPluginAskRequest(component, providerId, decoded.targetId, messages,
                    remainingTimeout(decoded.timeoutMs),
                    maximumOutputBytes = minOf(ThreeStoveAgentContract.MAX_MODEL_OUTPUT_BYTES.toLong(), target.maximumOutputBytes),
                    maximumOutputTokens = decoded.maximumOutputTokens, temperature = decoded.temperature,
                    responseSchemaJson = decoded.responseSchemaJson, tools = decoded.tools,
                    maximumToolRounds = decoded.maximumToolRounds, vision = decoded.vision)
            } catch (failure: ThreeStoveAgentModelException) { return fail(failure.code)
            } catch (_: Exception) { return fail(ThreeStoveAgentContract.ERROR_INVALID_REQUEST) }
            synchronized(lock) {
                if (finished) return
                try {
                    if (decoded.stream || decoded.tools.isNotEmpty()) {
                        val listener: (AiPluginStreamEvent) -> Unit = { providerEvent -> enqueue(this) {
                            when (providerEvent) {
                                is AiPluginStreamEvent.Opened -> Unit
                                is AiPluginStreamEvent.Chunk -> chunk(providerEvent.text)
                                is AiPluginStreamEvent.Usage -> updateUsage(providerEvent.usage)
                                is AiPluginStreamEvent.ToolCalls -> toolCalls(providerEvent.calls)
                                is AiPluginStreamEvent.Completed -> complete(providerEvent.text, providerEvent.usage, providerEvent.finishReason)
                                is AiPluginStreamEvent.Failed -> fail(providerEvent.failure.agentModelError(), providerEvent.failure.agentModelReason(providerEvent.providerReason))
                            }
                        } }
                        install(if (decoded.tools.isNotEmpty()) toolRunner.startTools(actualRequest, decoded.stream, listener)
                            else streamRunner.startStream(actualRequest, listener))
                    } else {
                        install(askRunner.start(actualRequest) { outcome -> enqueue(this) {
                            when (outcome) {
                                is AiPluginAskOutcome.Completed -> complete(outcome.text, outcome.usage, outcome.finishReason)
                                is AiPluginAskOutcome.Failed -> fail(outcome.failure.agentModelError(), outcome.failure.agentModelReason(outcome.providerReason))
                            }
                        } })
                    }
                } catch (_: Exception) { fail(ThreeStoveAgentContract.ERROR_MODEL_FAILED) }
            }
        }

        private fun remainingTimeout(timeout: Long): Long =
            (timeout - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(1_000)

        private fun updateUsage(next: AiPluginUsage): Unit = synchronized(lock) {
            if (finished) return
            usage = next
            usageUpdated = true
            // A Provider may report this round's usage after publishing its calls.
            if (!reservation && pendingToolIds.isNotEmpty()) {
                val settled = quota.settleCumulative(requestId, next, cumulativeInputBytes,
                    outputBytes + toolOutputBytes, chargedTokens, releaseReservation = false,
                    estimatedImageTokens = cumulativeImageTokens)
                chargedTokens = settled.second
                if (!settled.first) fail(ThreeStoveAgentContract.ERROR_QUOTA_EXCEEDED)
            }
            if (!finished && request?.tools?.isNotEmpty() == true) {
                emit(ThreeStoveAgentContract.MODEL_EVENT_USAGE, JsonObject().apply { add("usage", usageJson(next)) })
            }
        }

        private fun toolCalls(calls: List<AiPluginToolCall>): Unit = synchronized(lock) {
            if (finished) return
            val decoded = request ?: return fail(ThreeStoveAgentContract.ERROR_INVALID_REQUEST)
            if (calls.isEmpty() || calls.size > org.autojs.plugin.ai.provider.api.AiProviderLimits.MAX_OUTSTANDING_TOOL_CALLS ||
                calls.map { it.callId }.distinct().size != calls.size || pendingToolIds.isNotEmpty() ||
                toolRound >= decoded.maximumToolRounds || calls.any { call -> decoded.tools.none { it.name == call.name } }) {
                return fail(ThreeStoveAgentContract.ERROR_INVALID_REQUEST)
            }
            toolOutputBytes += calls.sumOf { it.argumentsJson.toByteArray(Charsets.UTF_8).size.toLong() }
            if (!settle()) return fail(ThreeStoveAgentContract.ERROR_QUOTA_EXCEEDED)
            toolRound += 1
            pendingToolIds = calls.mapTo(linkedSetOf()) { it.callId }
            toolSubmissionClaimed = false
            emit(ThreeStoveAgentContract.MODEL_EVENT_TOOL_CALLS, JsonObject().apply {
                addProperty("round", toolRound)
                add("calls", JsonArray().apply { calls.forEach { call -> add(JsonObject().apply {
                    addProperty("callId", call.callId); addProperty("name", call.name)
                    add("arguments", com.google.gson.JsonParser.parseString(call.argumentsJson))
                }) } })
                usage?.let { add("usage", usageJson(it)) }
            })
        }

        fun claimToolSubmission(): Boolean = synchronized(lock) {
            if (finished || pendingToolIds.isEmpty() || toolSubmissionClaimed) false
            else { toolSubmissionClaimed = true; true }
        }

        fun submitTools(incoming: Payload) {
            synchronized(lock) {
                if (finished) { incoming.close(); return }
                submittedPayload = incoming
            }
            try { worker.execute {
                try {
                    val submitted = ThreeStoveAgentModelRequests.toolResults(incoming.read(::isFinished))
                    incoming.validateImages(submitted.imageRefs)
                    synchronized(lock) {
                        if (finished) return@execute
                        if (submitted.requestId != requestId || submitted.round != toolRound ||
                            submitted.results.map { it.callId }.toSet() != pendingToolIds) {
                            return@execute fail(ThreeStoveAgentContract.ERROR_INVALID_REQUEST)
                        }
                        val decoded = checkNotNull(request)
                        if (submitted.imageRefs.isNotEmpty() && !decoded.vision) {
                            return@execute fail(ThreeStoveAgentContract.ERROR_TARGET_UNSUPPORTED)
                        }
                        val imageBytes = submitted.imageRefs.sumOf { it.part.payload.declaredLengthBytes }
                        if (submitted.imageRefs.size > AiProviderLimits.MAX_SESSION_IMAGES - sessionImageCount ||
                            imageBytes > AiProviderLimits.MAX_SESSION_IMAGE_BYTES - sessionImageBytes) {
                            return@execute fail(ThreeStoveAgentContract.ERROR_QUOTA_EXCEEDED)
                        }
                        toolResultBytes += submitted.inputBytes
                        val inputBytes = decoded.inputBytes + outputBytes + toolOutputBytes + toolResultBytes
                        if (inputBytes > contextLimit) return@execute fail(ThreeStoveAgentContract.ERROR_QUOTA_EXCEEDED)
                        val imageTokens = retainedImageTokens + submitted.imageTokens
                        reservation = quota.reserve(requestId, inputBytes, decoded.maximumOutputTokens, imageTokens)
                        if (!reservation) return@execute fail(ThreeStoveAgentContract.ERROR_QUOTA_EXCEEDED)
                        cumulativeInputBytes += inputBytes
                        retainedImageTokens = imageTokens
                        cumulativeImageTokens += imageTokens
                        sessionImageCount += submitted.imageRefs.size
                        sessionImageBytes += imageBytes
                        usageUpdated = false
                    }
                    val images = incoming.readImages(submitted.imageRefs, ::isFinished)
                    val results = submitted.results.map { result ->
                        AiPluginToolResult(result.callId, result.output, result.isError,
                            submitted.imagesByCallId[result.callId].orEmpty().map { checkNotNull(images[it.descriptorIndex]) })
                    }
                    synchronized(lock) {
                        if (finished) return@execute
                        if (handle?.submitToolResults(results) != true) return@execute fail(ThreeStoveAgentContract.ERROR_INVALID_REQUEST)
                        pendingToolIds = emptySet()
                        // An accepted batch starts a new model round: the request timeout applies to that round
                        // again, so a long native turn is bounded by the plugin's run budget and the round cap,
                        // not by the first request's deadline. Waiting without results still expires.
                        armDeadline(checkNotNull(request).timeoutMs + (SystemClock.elapsedRealtime() - startedAt))
                    }
                } catch (failure: ThreeStoveAgentModelException) { fail(failure.code)
                } catch (_: Exception) { fail(ThreeStoveAgentContract.ERROR_INVALID_REQUEST)
                } finally {
                    incoming.close()
                    synchronized(lock) { if (submittedPayload === incoming) submittedPayload = null }
                }
            } } catch (_: RejectedExecutionException) { incoming.close(); fail(ThreeStoveAgentContract.ERROR_RATE_LIMITED) }
        }

        private fun install(next: AiPluginAskHandle): Unit = synchronized(lock) {
            if (finished) { next.cancel(); next.close() } else {
                handle?.close()
                handle = next
            }
        }

        private fun chunk(text: String): Unit = synchronized(lock) {
            if (finished || text.isEmpty()) return
            outputBytes += text.toByteArray(Charsets.UTF_8).size
            if (outputBytes > ThreeStoveAgentContract.MAX_MODEL_OUTPUT_BYTES) return fail(ThreeStoveAgentContract.ERROR_LIMIT_EXCEEDED)
            emit(ThreeStoveAgentContract.MODEL_EVENT_CHUNK, JsonObject().apply {
                addProperty("chunkSequence", ++chunkSequence)
                addProperty("text", text)
            })
        }

        private fun complete(text: String, providerUsage: AiPluginUsage, finishReason: Int): Unit = synchronized(lock) {
            if (finished) return
            outputBytes = text.toByteArray(Charsets.UTF_8).size.toLong()
            usage = providerUsage
            usageUpdated = true
            if (pendingToolIds.isNotEmpty()) return fail(ThreeStoveAgentContract.ERROR_INVALID_REQUEST)
            if (outputBytes > ThreeStoveAgentContract.MAX_MODEL_OUTPUT_BYTES) return fail(ThreeStoveAgentContract.ERROR_LIMIT_EXCEEDED)
            if (!settle()) return fail(ThreeStoveAgentContract.ERROR_QUOTA_EXCEEDED)
            emit(ThreeStoveAgentContract.MODEL_EVENT_USAGE, JsonObject().apply {
                add("usage", usageJson(providerUsage))
            })
            finish(ThreeStoveAgentContract.MODEL_EVENT_COMPLETED, JsonObject().apply {
                addProperty("text", text)
                addProperty("finishReason", finishReason)
                addProperty("targetId", request?.targetId)
            })
        }

        fun fail(code: String, providerReason: String? = null): Unit = finish(
            if (code == ThreeStoveAgentContract.ERROR_CANCELLED) ThreeStoveAgentContract.MODEL_EVENT_CANCELLED else ThreeStoveAgentContract.MODEL_EVENT_FAILED,
            JsonObject().apply {
                addProperty("code", code)
                // Only host-owned enum names are forwarded. Provider messages and prompts are never exposed.
                providerReason?.let { addProperty("reason", it) }
            })

        private fun finish(type: String, fields: JsonObject): Unit = synchronized(lock) {
            if (finished) return
            emitStarted()
            if (finished) return
            finished = true
            settle()
            emit(type, fields)
            cleanup()
        }

        private fun settle(): Boolean {
            if (!reservation) return true
            reservation = false
            val result = quota.settleCumulative(requestId, if (usageUpdated) usage else null,
                cumulativeInputBytes, outputBytes + toolOutputBytes, chargedTokens, estimatedImageTokens = cumulativeImageTokens)
            chargedTokens = result.second
            return result.first
        }

        private fun emitStarted() {
            if (!started) { started = true; emit(ThreeStoveAgentContract.MODEL_EVENT_STARTED, JsonObject()) }
        }

        private fun emit(type: String, fields: JsonObject) {
            val next = sequence.next(type) ?: return
            val data = event(requestId, type, next).apply { fields.entrySet().forEach { add(it.key, it.value) } }
            var bundle: Bundle? = null
            try {
                bundle = try { eventBundle(data) } catch (failure: Exception) {
                    // A full cache directory or an oversized target catalog must still produce a
                    // small terminal envelope, rather than silently abandoning a live callback.
                    finished = true
                    settle()
                    cleanup()
                    envelope().apply {
                        putString(ThreeStoveAgentContract.KEY_MODEL_EVENT_JSON,
                            event(requestId, ThreeStoveAgentContract.MODEL_EVENT_FAILED, next).apply {
                                addProperty("code", (failure as? ThreeStoveAgentModelException)?.code
                                    ?: ThreeStoveAgentContract.ERROR_MODEL_FAILED)
                            }.toString())
                    }
                }
                callback.onEvent(bundle)
            } catch (_: Exception) { abandon() } finally { closeBundleDescriptor(bundle) }
        }

        private fun abandon(): Unit = synchronized(lock) {
            if (!finished) { finished = true; settle() }
            cleanup()
        }

        private fun cleanup() {
            payload.close()
            submittedPayload?.close()
            submittedPayload = null
            deadline?.cancel(false)
            deadline = null
            handle?.let { runCatching { it.cancel() }; runCatching { it.close() } }
            handle = null
            if (watching) runCatching { callback.asBinder().unlinkToDeath(deathRecipient, 0) }
            watching = false
            operations.remove(this)
        }
    }

    private fun usageJson(usage: AiPluginUsage) = JsonObject().apply {
        usage.inputTokens?.let { addProperty("inputTokens", it) }
        usage.outputTokens?.let { addProperty("outputTokens", it) }
        usage.totalTokens?.let { addProperty("totalTokens", it) }
        usage.durationMillis?.let { addProperty("durationMs", it) }
    }

    private fun eventBundle(json: JsonObject): Bundle {
        val text = json.toString()
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > ThreeStoveAgentContract.MAX_MODEL_REQUEST_PAYLOAD_BYTES) throw ThreeStoveAgentModelException(ThreeStoveAgentContract.ERROR_LIMIT_EXCEEDED)
        return envelope().apply {
            if (bytes.size <= ThreeStoveAgentContract.MAX_EVENT_JSON_BYTES) {
                putString(ThreeStoveAgentContract.KEY_MODEL_EVENT_JSON, text)
            } else {
                val file = File.createTempFile("agent-model-", ".json", applicationContext.cacheDir)
                try {
                    file.writeBytes(bytes)
                    putParcelable(ThreeStoveAgentContract.KEY_PAYLOAD_FD, ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
                } finally { file.delete() }
            }
        }
    }

    private fun takePayload(bundle: Bundle?, key: String, allowDescriptor: Boolean): Payload {
        var descriptor: ParcelFileDescriptor? = null
        try {
            @Suppress("DEPRECATION")
            val rawDescriptor = bundle?.get(ThreeStoveAgentContract.KEY_PAYLOAD_FD)
            descriptor = rawDescriptor as? ParcelFileDescriptor
            @Suppress("DEPRECATION")
            val rawImages = bundle?.get(ThreeStoveAgentContract.KEY_MODEL_IMAGE_FDS)
            val images = (rawImages as? Array<*>)?.filterIsInstance<ParcelFileDescriptor>().orEmpty()
            if (closeBundleDescriptor(bundle, if (allowDescriptor) descriptor else null,
                    if (allowDescriptor) images else emptyList()) ||
                (rawDescriptor != null && descriptor == null) ||
                (rawImages != null && (rawImages !is Array<*> || rawImages.size != images.size)) ||
                images.size > ThreeStoveAgentContract.MAX_MODEL_IMAGES ||
                images.toSet().size != images.size || images.any { it === descriptor }) {
                throw ThreeStoveAgentModelException(ThreeStoveAgentContract.ERROR_INVALID_REQUEST)
            }
            if (bundle?.getInt(ThreeStoveAgentContract.KEY_CONTRACT_VERSION) != ThreeStoveAgentContract.CONTRACT_VERSION) {
                throw ThreeStoveAgentModelException(ThreeStoveAgentContract.ERROR_UNSUPPORTED_CONTRACT)
            }
            val inline = bundle.getString(key)
            if ((inline == null) == (descriptor == null) || (!allowDescriptor && descriptor != null)) {
                throw ThreeStoveAgentModelException(ThreeStoveAgentContract.ERROR_INVALID_REQUEST)
            }
            if (inline != null && inline.toByteArray(Charsets.UTF_8).size > ThreeStoveAgentContract.MAX_MODEL_REQUEST_INLINE_BYTES) {
                throw ThreeStoveAgentModelException(ThreeStoveAgentContract.ERROR_LIMIT_EXCEEDED)
            }
            val correlationId = if (descriptor != null) {
                val reference = bundle.getString(ThreeStoveAgentContract.KEY_MODEL_REF_JSON)
                    ?: throw ThreeStoveAgentModelException(ThreeStoveAgentContract.ERROR_INVALID_REQUEST)
                if (reference.toByteArray(Charsets.UTF_8).size > 512) {
                    throw ThreeStoveAgentModelException(ThreeStoveAgentContract.ERROR_LIMIT_EXCEEDED)
                }
                ThreeStoveAgentModelRequests.reference(reference)
            } else null
            return Payload(inline, descriptor, correlationId, images)
        } catch (failure: Exception) {
            closeBundleDescriptor(bundle)
            throw (failure as? ThreeStoveAgentModelException ?: ThreeStoveAgentModelException(ThreeStoveAgentContract.ERROR_INVALID_REQUEST))
        }
    }

    private class Payload(val inline: String?, private val descriptor: ParcelFileDescriptor?,
        val correlationId: String? = null, private val images: List<ParcelFileDescriptor> = emptyList()) : Closeable {
        private var closed = false
        fun validateImages(refs: List<ThreeStoveAgentModelImageRef>) = ThreeStoveAgentModelImages.validate(refs, images.size)
        fun readImages(refs: List<ThreeStoveAgentModelImageRef>, cancelled: () -> Boolean): Map<Int, AiPluginImage> {
            validateImages(refs)
            return refs.associate { ref ->
                val bytes = AiProviderImagePolicy.materialize(ref.part) { index, length ->
                    readDescriptor(images[index], length.toInt(), cancelled)
                }
                ref.descriptorIndex to AiPluginImageDecoder.decode(bytes, ref.part.payload.mimeType, checkNotNull(ref.part.image))
            }
        }
        fun read(cancelled: () -> Boolean): String {
            inline?.let { return it }
            val bytes = readDescriptor(requireNotNull(descriptor), ThreeStoveAgentContract.MAX_MODEL_REQUEST_PAYLOAD_BYTES, cancelled)
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        }
        private fun readDescriptor(input: ParcelFileDescriptor, maximumBytes: Int, cancelled: () -> Boolean): ByteArray {
            val output = ByteArrayOutputStream()
            input.use {
                // Bounded nonblocking reads preserve cancellation even for a sender-controlled pipe.
                if (cancelled()) throw ThreeStoveAgentModelException(ThreeStoveAgentContract.ERROR_CANCELLED)
                val reopened = synchronized(this) {
                    if (closed) throw ThreeStoveAgentModelException(ThreeStoveAgentContract.ERROR_CANCELLED)
                    if (Build.VERSION.SDK_INT >= 30) {
                        val fd = input.fileDescriptor
                        Os.fcntlInt(fd, OsConstants.F_SETFL, Os.fcntlInt(fd, OsConstants.F_GETFL, 0) or OsConstants.O_NONBLOCK)
                        null
                    } else {
                        val offset = try { Os.lseek(input.fileDescriptor, 0, OsConstants.SEEK_CUR) }
                        catch (failure: ErrnoException) { if (failure.errno == OsConstants.ESPIPE) null else throw failure }
                        Os.open("/proc/self/fd/${input.fd}", OsConstants.O_RDONLY or OsConstants.O_NONBLOCK or OsConstantsCompat.O_CLOEXEC, 0).also { fd ->
                            try { if (offset != null) Os.lseek(fd, offset, OsConstants.SEEK_SET) }
                            catch (failure: Exception) { Os.close(fd); throw failure }
                        }
                    }
                }
                val fd = reopened ?: input.fileDescriptor
                try {
                    val poll = StructPollfd().apply { this.fd = fd; events = OsConstants.POLLIN.toShort() }
                    val buffer = ByteArray(8192)
                    while (true) {
                        if (cancelled()) throw ThreeStoveAgentModelException(ThreeStoveAgentContract.ERROR_CANCELLED)
                        if (Os.poll(arrayOf(poll), 100) == 0) continue
                        val count = try { Os.read(fd, buffer, 0, buffer.size) } catch (failure: ErrnoException) {
                            if (failure.errno == OsConstants.EAGAIN || failure.errno == OsConstants.EINTR) continue
                            throw failure
                        }
                        if (count == 0) break
                        if (count > maximumBytes - output.size()) throw ThreeStoveAgentModelException(ThreeStoveAgentContract.ERROR_LIMIT_EXCEEDED)
                        output.write(buffer, 0, count)
                    }
                    input.checkError()
                } finally { if (reopened != null) Os.close(reopened) }
            }
            return output.toByteArray()
        }
        override fun close() { synchronized(this) {
            closed = true
            runCatching { descriptor?.close() }
            images.forEach { runCatching { it.close() } }
        } }
    }

    companion object {
        private const val MAX_PENDING_OPERATIONS = 8
        private fun envelope() = Bundle().apply { putInt(ThreeStoveAgentContract.KEY_CONTRACT_VERSION, ThreeStoveAgentContract.CONTRACT_VERSION) }
        private fun event(id: String, type: String, sequence: Long) = JsonObject().apply {
            addProperty("requestId", id); addProperty("type", type); addProperty("sequence", sequence)
        }
        /** Closes descriptors in rejected, unknown or mistyped fields, including nested containers. */
        private fun closeBundleDescriptor(bundle: Bundle?, retainedPayload: ParcelFileDescriptor? = null,
            retainedImages: List<ParcelFileDescriptor> = emptyList()): Boolean {
            val keys = runCatching { bundle?.keySet()?.toList().orEmpty() }.getOrDefault(emptyList())
            var found = false
            fun visit(value: Any?, accept: (ParcelFileDescriptor) -> Boolean) {
                val pending = java.util.ArrayDeque<Any>()
                val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
                value?.let(pending::addLast)
                while (pending.isNotEmpty()) {
                    val next = pending.removeLast()
                    if (!seen.add(next)) continue
                    if (next is ParcelFileDescriptor) {
                        if (!accept(next)) { found = true; runCatching { next.close() } }
                    } else when (next) {
                        is Array<*> -> next.forEach { it?.let(pending::addLast) }
                        is List<*> -> next.forEach { it?.let(pending::addLast) }
                        is Bundle -> runCatching { next.keySet().toList() }.getOrDefault(emptyList()).forEach { key ->
                            @Suppress("DEPRECATION")
                            val child = runCatching { next.get(key) }.getOrNull()
                            child?.let(pending::addLast)
                        }
                    }
                }
            }
            for (key in keys) {
                @Suppress("DEPRECATION")
                val value = runCatching { bundle?.get(key) }.getOrNull()
                visit(value) { fd ->
                    (key == ThreeStoveAgentContract.KEY_PAYLOAD_FD && value === retainedPayload && fd === retainedPayload) ||
                        (key == ThreeStoveAgentContract.KEY_MODEL_IMAGE_FDS && value is Array<*> && retainedImages.any { it === fd })
                }
            }
            return found
        }
    }
}
