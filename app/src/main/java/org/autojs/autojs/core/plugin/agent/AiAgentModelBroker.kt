package org.autojs.autojs.core.plugin.agent

import android.content.Context
import android.os.Binder
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
import org.autojs.autojs.core.plugin.ai.AiProviderComponent
import org.autojs.autojs.core.plugin.ai.AndroidAiPluginAskRunner
import org.autojs.autojs.core.plugin.ai.ThreeStoneAiOfficialPlugin
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityGrant
import org.autojs.plugin.ai.agent.api.AiAgentContract
import org.autojs.plugin.ai.agent.api.IAiAgentModelBroker
import org.autojs.plugin.ai.agent.api.IAiAgentModelCallback
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
internal class AiAgentModelBroker(
    context: Context,
    private val expectedCallerUid: Int,
    private val grant: HostCapabilityGrant,
    private val component: AiProviderComponent = AiProviderComponent(
        ThreeStoneAiOfficialPlugin.PACKAGE_NAME, ThreeStoneAiOfficialPlugin.CLASS_NAME),
    private val providerId: String = ThreeStoneAiOfficialPlugin.PROVIDER_ID,
    private val askRunner: AiPluginAskRunner = AndroidAiPluginAskRunner.create(context),
    private val streamRunner: AiPluginStreamRunner = AndroidAiPluginAskRunner.createStream(context),
    private val catalogRunner: AiPluginCatalogRunner = AndroidAiPluginAskRunner.createCatalog(context),
) : IAiAgentModelBroker.Stub(), Closeable {
    private val applicationContext = context.applicationContext
    private val closed = AtomicBoolean()
    private val operations = ConcurrentHashMap<Operation, Boolean>()
    private val admissionLock = Any()
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(128),
        { Thread(it, "ai-agent-model").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    private val deadlines = ScheduledThreadPoolExecutor(1) {
        Thread(it, "ai-agent-model-deadline").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }
    private val quota = AiAgentModelQuota(grant.modelCallsPerMinute, grant.maxTotalTokens, SystemClock::elapsedRealtime)

    init { require(expectedCallerUid >= 0) }

    override fun getBrokerInfo(): Bundle {
        enforceCaller()
        return envelope().apply {
            val info = JsonObject().apply {
                addProperty("available", !closed.get())
                addProperty("providerId", providerId)
                addProperty("maximumInputBytes", grant.maxInputBytesPerRequest)
                addProperty("maximumOutputBytes", AiAgentContract.MAX_MODEL_OUTPUT_BYTES)
                addProperty("maximumResponseSchemaBytes", AiAgentContract.MAX_RESPONSE_SCHEMA_BYTES)
            }
            putString(AiAgentContract.KEY_MODEL_BROKER_INFO_JSON, info.toString())
            putString(HostCapabilityContract.KEY_GRANT_JSON, JsonObject().apply {
                addProperty("modelCallsPerMinute", grant.modelCallsPerMinute)
                addProperty("maxTotalTokens", grant.maxTotalTokens)
                addProperty("maxInputBytesPerRequest", grant.maxInputBytesPerRequest)
                addProperty("consumedTokens", quota.consumedTokens())
            }.toString())
        }
    }

    override fun listTargets(request: Bundle?, callback: IAiAgentModelCallback?) = submit(request, callback, catalogOnly = true)

    override fun generate(request: Bundle?, callback: IAiAgentModelCallback?) = submit(request, callback, catalogOnly = false)

    override fun cancel(ref: Bundle?) {
        try { enforceCaller() } catch (failure: Exception) { closeBundleDescriptor(ref); throw failure }
        val payload = takePayload(ref, AiAgentContract.KEY_MODEL_REF_JSON, allowDescriptor = false)
        val id = try { AiAgentModelRequests.reference(payload.inline.orEmpty()) } finally { payload.close() }
        operations.keys.filter { it.requestId == id }.forEach { operation ->
            // Do not queue cancellation behind a sender-controlled payload pipe.
            operation.fail(AiAgentContract.ERROR_CANCELLED)
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
        operations.keys.forEach { it.fail(AiAgentContract.ERROR_LINK_DETACHED) }
        worker.shutdownNow()
        deadlines.shutdownNow()
    }

    private fun enforceCaller() {
        if (Binder.getCallingUid() != expectedCallerUid) throw SecurityException("AI Agent model broker caller UID is not authorized")
    }

    private fun submit(request: Bundle?, callback: IAiAgentModelCallback?, catalogOnly: Boolean) {
        try { enforceCaller() } catch (failure: Exception) {
            closeBundleDescriptor(request)
            throw failure
        }
        if (callback == null) { closeBundleDescriptor(request); return }
        val payload = try {
            takePayload(request, AiAgentContract.KEY_MODEL_REQUEST_JSON, allowDescriptor = !catalogOnly)
        } catch (failure: AiAgentModelException) {
            reject(callback, "", failure.code)
            return
        }
        val operation = Operation(callback, payload, catalogOnly)
        synchronized(admissionLock) {
            if (closed.get() || operations.size >= MAX_PENDING_OPERATIONS) {
                payload.close()
                reject(callback, payload.correlationId ?: payload.inline?.let(AiAgentModelRequests::recoverId).orEmpty(),
                    if (closed.get()) AiAgentContract.ERROR_LINK_DETACHED else AiAgentContract.ERROR_RATE_LIMITED)
                return
            }
            operations[operation] = true
        }
        if (!operation.watchCallback()) return
        operation.armDeadline(AiAgentModelRequests.DEFAULT_TIMEOUT_MS)
        enqueue(operation) { operation.start() }
    }

    private fun reject(callback: IAiAgentModelCallback, id: String, code: String) {
        // Rejections remain bounded and contain no caller-supplied text beyond the validated ID.
        try {
            callback.onEvent(eventBundle(event(id, "started", 1)))
            callback.onEvent(eventBundle(event(id, "failed", 2).apply { addProperty("code", code) }))
        } catch (_: Exception) { /* A dead recipient owns no operation or Provider handle. */ }
    }

    private fun enqueue(operation: Operation, block: () -> Unit) {
        try { worker.execute { if (!operation.isFinished()) block() } } catch (_: RejectedExecutionException) {
            operation.fail(if (closed.get()) AiAgentContract.ERROR_LINK_DETACHED else AiAgentContract.ERROR_RATE_LIMITED)
        }
    }

    private inner class Operation(
        private val callback: IAiAgentModelCallback,
        private val payload: Payload,
        private val catalogOnly: Boolean,
    ) {
        @Volatile var requestId = payload.correlationId ?: payload.inline?.let(AiAgentModelRequests::recoverId).orEmpty()
            private set
        private val lock = Any()
        private val sequence = AiAgentModelEventSequence()
        private val startedAt = SystemClock.elapsedRealtime()
        private var finished = false
        private var started = false
        private var watching = false
        private var deadline: Future<*>? = null
        private var handle: AiPluginAskHandle? = null
        private var request: AiAgentModelRequest? = null
        private var reservation = false
        private var usage: AiPluginUsage? = null
        private var outputBytes = 0L
        private var chunkSequence = 0L
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
                deadline = deadlines.schedule({ fail(AiAgentContract.ERROR_MODEL_TIMEOUT) },
                    (timeoutMs - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            } catch (_: RejectedExecutionException) { fail(AiAgentContract.ERROR_LINK_DETACHED) }
        }

        fun start() {
            try {
                val json = payload.read(::isFinished)
                val timeout: Long
                if (catalogOnly) {
                    val decoded = AiAgentModelRequests.catalog(json)
                    requestId = decoded.first
                    timeout = decoded.second
                } else {
                    val decoded = AiAgentModelRequests.decode(json, grant.maxInputBytesPerRequest)
                    if (payload.correlationId != null && payload.correlationId != decoded.requestId) {
                        throw AiAgentModelException(AiAgentContract.ERROR_INVALID_REQUEST)
                    }
                    request = decoded
                    requestId = decoded.requestId
                    timeout = decoded.timeoutMs
                }
                synchronized(lock) {
                    if (finished) return
                    emitStarted()
                    if (operations.keys.any { it !== this && it.requestId == requestId }) {
                        fail(AiAgentContract.ERROR_INVALID_REQUEST)
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
            } catch (failure: AiAgentModelException) { fail(failure.code)
            } catch (_: Exception) { fail(AiAgentContract.ERROR_INVALID_REQUEST) }
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
            finish(AiAgentContract.MODEL_EVENT_COMPLETED, JsonObject().apply { add("targets", publicTargets) })
        }

        private fun generateWithTarget(targets: List<AiPluginTargetSummary>) {
            val decoded = request ?: return fail(AiAgentContract.ERROR_INVALID_REQUEST)
            val target = targets.firstOrNull { it.targetId == decoded.targetId }
                ?: return fail(AiAgentContract.ERROR_TARGET_UNAVAILABLE)
            if (!target.configured || target.availability != AiTargetAvailability.AVAILABLE) {
                return fail(AiAgentContract.ERROR_TARGET_UNAVAILABLE)
            }
            if ((decoded.responseSchemaJson != null && AiProviderCapabilityId.STRUCTURED_JSON !in target.capabilityIds) ||
                (decoded.stream && AiProviderCapabilityId.STREAMING !in target.capabilityIds)) {
                return fail(AiAgentContract.ERROR_TARGET_UNSUPPORTED)
            }
            if (decoded.inputBytes > target.maximumContextBytes) return fail(AiAgentContract.ERROR_QUOTA_EXCEEDED)
            val actualRequest = try {
                AiPluginAskRequest(component, providerId, decoded.targetId, decoded.messages,
                    remainingTimeout(decoded.timeoutMs),
                    maximumOutputBytes = minOf(AiAgentContract.MAX_MODEL_OUTPUT_BYTES.toLong(), target.maximumOutputBytes),
                    maximumOutputTokens = decoded.maximumOutputTokens, temperature = decoded.temperature,
                    responseSchemaJson = decoded.responseSchemaJson)
            } catch (_: Exception) { return fail(AiAgentContract.ERROR_INVALID_REQUEST) }
            synchronized(lock) {
                if (finished) return
                reservation = quota.reserve(requestId, decoded.inputBytes, decoded.maximumOutputTokens)
                if (!reservation) return fail(AiAgentContract.ERROR_QUOTA_EXCEEDED)
            }
            synchronized(lock) {
                if (finished) return
                try {
                    if (decoded.stream) {
                        install(streamRunner.startStream(actualRequest) { providerEvent -> enqueue(this) {
                            when (providerEvent) {
                                is AiPluginStreamEvent.Opened -> Unit
                                is AiPluginStreamEvent.Chunk -> chunk(providerEvent.text)
                                is AiPluginStreamEvent.Usage -> synchronized(lock) { if (!finished) usage = providerEvent.usage }
                                is AiPluginStreamEvent.Completed -> complete(providerEvent.text, providerEvent.usage, providerEvent.finishReason)
                                is AiPluginStreamEvent.Failed -> fail(providerEvent.failure.agentModelError(), providerEvent.failure.name)
                            }
                        } })
                    } else {
                        install(askRunner.start(actualRequest) { outcome -> enqueue(this) {
                            when (outcome) {
                                is AiPluginAskOutcome.Completed -> complete(outcome.text, outcome.usage, outcome.finishReason)
                                is AiPluginAskOutcome.Failed -> fail(outcome.failure.agentModelError(), outcome.failure.name)
                            }
                        } })
                    }
                } catch (_: Exception) { fail(AiAgentContract.ERROR_MODEL_FAILED) }
            }
        }

        private fun remainingTimeout(timeout: Long): Long =
            (timeout - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(1_000)

        private fun install(next: AiPluginAskHandle): Unit = synchronized(lock) {
            if (finished) { next.cancel(); next.close() } else {
                handle?.close()
                handle = next
            }
        }

        private fun chunk(text: String): Unit = synchronized(lock) {
            if (finished || text.isEmpty()) return
            outputBytes += text.toByteArray(Charsets.UTF_8).size
            if (outputBytes > AiAgentContract.MAX_MODEL_OUTPUT_BYTES) return fail(AiAgentContract.ERROR_LIMIT_EXCEEDED)
            emit(AiAgentContract.MODEL_EVENT_CHUNK, JsonObject().apply {
                addProperty("chunkSequence", ++chunkSequence)
                addProperty("text", text)
            })
        }

        private fun complete(text: String, providerUsage: AiPluginUsage, finishReason: Int): Unit = synchronized(lock) {
            if (finished) return
            outputBytes = text.toByteArray(Charsets.UTF_8).size.toLong()
            usage = providerUsage
            if (outputBytes > AiAgentContract.MAX_MODEL_OUTPUT_BYTES) return fail(AiAgentContract.ERROR_LIMIT_EXCEEDED)
            if (!settle()) return fail(AiAgentContract.ERROR_QUOTA_EXCEEDED)
            emit(AiAgentContract.MODEL_EVENT_USAGE, JsonObject().apply {
                add("usage", JsonObject().apply {
                    providerUsage.inputTokens?.let { addProperty("inputTokens", it) }
                    providerUsage.outputTokens?.let { addProperty("outputTokens", it) }
                    providerUsage.totalTokens?.let { addProperty("totalTokens", it) }
                    providerUsage.durationMillis?.let { addProperty("durationMs", it) }
                })
            })
            finish(AiAgentContract.MODEL_EVENT_COMPLETED, JsonObject().apply {
                addProperty("text", text)
                addProperty("finishReason", finishReason)
                addProperty("targetId", request?.targetId)
            })
        }

        fun fail(code: String, providerReason: String? = null): Unit = finish(
            if (code == AiAgentContract.ERROR_CANCELLED) AiAgentContract.MODEL_EVENT_CANCELLED else AiAgentContract.MODEL_EVENT_FAILED,
            JsonObject().apply {
                addProperty("code", code)
                // Only the enum name is forwarded. Provider messages and prompts are never exposed.
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
            return quota.settle(requestId, usage, request?.inputBytes ?: 0, outputBytes)
        }

        private fun emitStarted() {
            if (!started) { started = true; emit(AiAgentContract.MODEL_EVENT_STARTED, JsonObject()) }
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
                        putString(AiAgentContract.KEY_MODEL_EVENT_JSON,
                            event(requestId, AiAgentContract.MODEL_EVENT_FAILED, next).apply {
                                addProperty("code", (failure as? AiAgentModelException)?.code
                                    ?: AiAgentContract.ERROR_MODEL_FAILED)
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
            deadline?.cancel(false)
            deadline = null
            handle?.let { runCatching { it.cancel() }; runCatching { it.close() } }
            handle = null
            if (watching) runCatching { callback.asBinder().unlinkToDeath(deathRecipient, 0) }
            watching = false
            operations.remove(this)
        }
    }

    private fun eventBundle(json: JsonObject): Bundle {
        val text = json.toString()
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > AiAgentContract.MAX_MODEL_REQUEST_PAYLOAD_BYTES) throw AiAgentModelException(AiAgentContract.ERROR_LIMIT_EXCEEDED)
        return envelope().apply {
            if (bytes.size <= AiAgentContract.MAX_EVENT_JSON_BYTES) {
                putString(AiAgentContract.KEY_MODEL_EVENT_JSON, text)
            } else {
                val file = File.createTempFile("agent-model-", ".json", applicationContext.cacheDir)
                try {
                    file.writeBytes(bytes)
                    putParcelable(AiAgentContract.KEY_PAYLOAD_FD, ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
                } finally { file.delete() }
            }
        }
    }

    private fun takePayload(bundle: Bundle?, key: String, allowDescriptor: Boolean): Payload {
        var descriptor: ParcelFileDescriptor? = null
        try {
            @Suppress("DEPRECATION")
            val rawDescriptor = bundle?.get(AiAgentContract.KEY_PAYLOAD_FD)
            descriptor = rawDescriptor as? ParcelFileDescriptor
            // Any descriptor outside the accepted payload slot is invalid, even if it was put
            // in a String field. Retain only the one payload whose ownership we can accept.
            if (closeBundleDescriptor(bundle, if (allowDescriptor) descriptor else null) ||
                (rawDescriptor != null && descriptor == null)) {
                throw AiAgentModelException(AiAgentContract.ERROR_INVALID_REQUEST)
            }
            if (bundle?.getInt(AiAgentContract.KEY_CONTRACT_VERSION) != AiAgentContract.CONTRACT_VERSION) {
                throw AiAgentModelException(AiAgentContract.ERROR_UNSUPPORTED_CONTRACT)
            }
            val inline = bundle.getString(key)
            if ((inline == null) == (descriptor == null) || (!allowDescriptor && descriptor != null)) {
                throw AiAgentModelException(AiAgentContract.ERROR_INVALID_REQUEST)
            }
            if (inline != null && inline.toByteArray(Charsets.UTF_8).size > AiAgentContract.MAX_MODEL_REQUEST_INLINE_BYTES) {
                throw AiAgentModelException(AiAgentContract.ERROR_LIMIT_EXCEEDED)
            }
            val correlationId = if (descriptor != null) {
                val reference = bundle.getString(AiAgentContract.KEY_MODEL_REF_JSON)
                    ?: throw AiAgentModelException(AiAgentContract.ERROR_INVALID_REQUEST)
                if (reference.toByteArray(Charsets.UTF_8).size > 512) {
                    throw AiAgentModelException(AiAgentContract.ERROR_LIMIT_EXCEEDED)
                }
                AiAgentModelRequests.reference(reference)
            } else null
            return Payload(inline, descriptor, correlationId)
        } catch (failure: Exception) {
            closeBundleDescriptor(bundle)
            throw (failure as? AiAgentModelException ?: AiAgentModelException(AiAgentContract.ERROR_INVALID_REQUEST))
        }
    }

    private class Payload(val inline: String?, private val descriptor: ParcelFileDescriptor?, val correlationId: String? = null) : Closeable {
        fun read(cancelled: () -> Boolean): String {
            inline?.let { return it }
            val output = ByteArrayOutputStream()
            requireNotNull(descriptor).use { input ->
                // A sender can keep a pipe open forever. Nonblocking reads plus a bounded poll
                // let deadline/death cleanup release the link worker even for a hostile pipe.
                val fd = input.fileDescriptor
                Os.fcntlInt(fd, OsConstants.F_SETFL, Os.fcntlInt(fd, OsConstants.F_GETFL, 0) or OsConstants.O_NONBLOCK)
                val poll = StructPollfd().apply { this.fd = fd; events = OsConstants.POLLIN.toShort() }
                val buffer = ByteArray(8192)
                while (true) {
                    if (cancelled()) throw AiAgentModelException(AiAgentContract.ERROR_CANCELLED)
                    if (Os.poll(arrayOf(poll), 100) == 0) continue
                    val count = try { Os.read(fd, buffer, 0, buffer.size) } catch (failure: ErrnoException) {
                        if (failure.errno == OsConstants.EAGAIN || failure.errno == OsConstants.EINTR) continue
                        throw failure
                    }
                    if (count == 0) break
                    if (count > AiAgentContract.MAX_MODEL_REQUEST_PAYLOAD_BYTES - output.size()) {
                        throw AiAgentModelException(AiAgentContract.ERROR_LIMIT_EXCEEDED)
                    }
                    output.write(buffer, 0, count)
                }
            }
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString()
        }
        override fun close() { runCatching { descriptor?.close() } }
    }

    companion object {
        private const val MAX_PENDING_OPERATIONS = 8
        private fun envelope() = Bundle().apply { putInt(AiAgentContract.KEY_CONTRACT_VERSION, AiAgentContract.CONTRACT_VERSION) }
        private fun event(id: String, type: String, sequence: Long) = JsonObject().apply {
            addProperty("requestId", id); addProperty("type", type); addProperty("sequence", sequence)
        }
        /** Closes every top-level descriptor, including rejected, unknown and mistyped fields. */
        private fun closeBundleDescriptor(bundle: Bundle?, retainedPayload: ParcelFileDescriptor? = null): Boolean {
            val keys = runCatching { bundle?.keySet()?.toList().orEmpty() }.getOrDefault(emptyList())
            var found = false
            for (key in keys) {
                @Suppress("DEPRECATION")
                val descriptor = runCatching { bundle?.get(key) }.getOrNull() as? ParcelFileDescriptor ?: continue
                if (key == AiAgentContract.KEY_PAYLOAD_FD && descriptor === retainedPayload) continue
                found = true
                runCatching { descriptor.close() }
            }
            return found
        }
    }
}
