package org.autojs.autojs.core.plugin.ai

import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.text.api.AiContentPart
import org.autojs.plugin.ai.text.api.AiGenerationOptions
import org.autojs.plugin.ai.text.api.AiMessage
import org.autojs.plugin.ai.text.api.AiMessageRole
import org.autojs.plugin.ai.text.api.AiTextCapabilities
import org.autojs.plugin.ai.text.api.AiTextCapabilityId
import org.autojs.plugin.ai.text.api.AiTextMimeType
import org.autojs.plugin.ai.text.api.AiTextRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.Closeable
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Opt-in positive Android evidence for the default-off exact-component session transport. */
class AiTextProviderSessionAndroidConformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun exactComponentSessionStreamsPastInitialCreditsFromRequestDescriptor() {
        requireConformanceEnvironment()
        val component = AiTextProviderComponent(FAKE_PACKAGE, FAKE_SERVICE)
        val initial = discoverExact(component)
        val worker = boundedExecutor("ai-text-session-conformance-control", 64)
        val blocking = boundedExecutor("ai-text-session-conformance-binder", 32)
        val fallback = boundedExecutor("ai-text-session-conformance-terminal", 32)
        val scheduler = Executors.newSingleThreadScheduledExecutor(
            namedThreadFactory("ai-text-session-conformance-deadline"),
        )
        var runHandle: AiTextProviderSessionRunHandle? = null
        var requestDescriptors: AndroidAiTextProviderSessionRequestDescriptors? = null
        var requestReader: ParcelFileDescriptor? = null
        try {
            val pinned = runHandshake(initial, worker, blocking, scheduler)
            val prompt = buildString {
                repeat(24) { index -> append("chunk-").append(index).append('|') }
            }
            val promptBytes = prompt.toByteArray(Charsets.UTF_8)
            val pipe = reliablePipe(promptBytes)
            requestReader = pipe
            requestDescriptors = AndroidAiTextProviderSessionRequestDescriptors(arrayOf(pipe))
            val request = descriptorRequest(pinned, promptBytes)
            val plan = AiTextHostSessionPlan(
                pinnedProvider = pinned,
                listingGeneration = FAKE_LISTING_GENERATION,
                modelId = FAKE_STREAM_MODEL,
                modelCapabilityIds = setOf(
                    AiTextCapabilityId.STREAMING,
                    AiTextCapabilityId.STRUCTURED_JSON,
                    AiTextCapabilityId.USAGE,
                ),
                effectiveMaximumContextBytes = pinned.negotiatedCapabilities.maximumContextBytes,
                effectiveMaximumOutputBytes = pinned.negotiatedCapabilities.maximumOutputBytes,
                credentialDisposition = AiTextCredentialIsolationPolicy.disposition(pinned),
            )
            val events = Collections.synchronizedList(mutableListOf<AiTextProviderSessionRunEvent>())
            val terminal = CountDownLatch(1)
            val terminalCount = AtomicInteger()
            val callbackOnMain = AtomicBoolean(false)
            val terminalEvent = AtomicReference<AiTextProviderSessionRunEvent>()
            val coordinator = AiTextProviderSessionCoordinator(
                transport = AndroidAiTextProviderSession(context),
                workerExecutor = worker,
                blockingExecutor = blocking,
                terminalFallbackExecutor = fallback,
                deadlineScheduler = scheduler.asSessionDeadlineScheduler(),
                clock = AiTextProviderSessionClock { android.os.SystemClock.elapsedRealtime() },
                owner = AiTextProviderSessionProcessOwner(),
            )
            runHandle = coordinator.start(
                AiTextProviderSessionRunRequest(
                    snapshot = initial,
                    plan = plan,
                    request = request,
                    requestDescriptors = requestDescriptors,
                    environment = environment(),
                    hostCapabilities = hostCapabilities(),
                    options = AiTextProviderSessionRunOptions(enabled = true),
                ),
            ) { event ->
                if (Looper.myLooper() == Looper.getMainLooper()) callbackOnMain.set(true)
                events += event
                if (event.isSessionTerminal()) {
                    terminalCount.incrementAndGet()
                    if (terminalEvent.compareAndSet(null, event)) terminal.countDown()
                }
            }

            assertTrue(
                "AI text provider session did not terminate",
                terminal.await(OUTCOME_WAIT_MILLIS, TimeUnit.MILLISECONDS),
            )
            val finalEvent = terminalEvent.get()
            val completion = (
                finalEvent as? AiTextProviderSessionRunEvent.Transcript
                )?.event as? AiTextProviderSessionEvent.Completed
            assertNotNull("AI text provider session did not complete successfully: $finalEvent", completion)
            val transcript = synchronized(events) {
                events.mapNotNull { (it as? AiTextProviderSessionRunEvent.Transcript)?.event }
            }
            val started = transcript.filterIsInstance<AiTextProviderSessionEvent.Started>().single()
            val chunks = transcript.filterIsInstance<AiTextProviderSessionEvent.Chunk>()
            val usage = transcript.filterIsInstance<AiTextProviderSessionEvent.Usage>()
            assertEquals(FAKE_STREAM_MODEL, started.metadata.modelId)
            assertTrue("Session did not cross the fixed initial credit window", chunks.size > 8)
            assertEquals(chunks.indices.map(Int::toLong), chunks.map { it.chunk.sequence })
            assertEquals(prompt, chunks.joinToString(separator = "") { it.chunk.textDelta })
            assertEquals(1, usage.size)
            assertEquals(prompt, checkNotNull(completion).result.outputText)
            assertFalse("Session callback was delivered on the Android main thread", callbackOnMain.get())
            Thread.sleep(LATE_CALLBACK_SETTLE_MILLIS)
            assertEquals("Session emitted more than one terminal event", 1, terminalCount.get())
            assertTrue(initial.samePackageIdentityAs(discoverExact(component)))
            assertFalse("Request descriptor remained open after terminal settlement", pipe.fileDescriptor.valid())
        } finally {
            runCatching { runHandle?.close() }
            runCatching { requestDescriptors?.close() }
            runCatching { requestReader?.close() }
            shutdown(worker, blocking, fallback, scheduler)
        }
    }

    @Test
    fun callbackDescriptorOwnerEnforcesExactLengthProducerErrorsAndIdempotentClose() {
        requireConformanceEnvironment()
        val operationStarts = AtomicInteger()
        val operationEnds = AtomicInteger()
        val observer = AiTextProviderSessionOperationObserver {
            operationStarts.incrementAndGet()
            Closeable { operationEnds.incrementAndGet() }
        }

        val exactBytes = "exact-payload".toByteArray(Charsets.UTF_8)
        val exactIncoming = AndroidAiTextProviderSessionIncomingDescriptors(
            arrayOf(reliablePipe(exactBytes)),
            observer,
        )
        val exactLease = exactIncoming.copyForAsync()
        exactIncoming.close()
        exactIncoming.close()
        assertTrue(exactBytes.contentEquals(exactLease.readDeclaredBytes(0, exactBytes.size.toLong())))
        exactLease.close()
        exactLease.close()

        assertDescriptorReadFails(observer, "short".toByteArray(), declaredLength = 6L)
        assertDescriptorReadFails(observer, "trailing".toByteArray(), declaredLength = 7L)
        assertReliableProducerErrorFails(observer)
        assertEquals(4, operationStarts.get())
        assertEquals(operationStarts.get(), operationEnds.get())
    }

    private fun descriptorRequest(
        pinned: AiTextProviderPinnedIdentity,
        promptBytes: ByteArray,
    ): AiTextRequest {
        val payload = AiPayloadReference(
            mimeType = AiTextMimeType.PLAIN,
            declaredLengthBytes = promptBytes.size.toLong(),
            sha256 = sha256(promptBytes),
            descriptorIndex = 0,
            charset = "utf-8",
        )
        return AiTextRequest(
            requestId = "android-session-request",
            protocolVersion = AiProtocolVersion(1, 0),
            providerId = pinned.providerId,
            modelId = FAKE_STREAM_MODEL,
            messages = listOf(
                AiMessage(
                    role = AiMessageRole.USER,
                    parts = listOf(AiContentPart(payload)),
                ),
            ),
            options = AiGenerationOptions(
                stream = true,
                includeReasoning = false,
                structuredJson = false,
                reportUsage = true,
                maximumOutputBytes = 4_096,
                maximumToolRounds = 0,
                timeoutMillis = SESSION_TIMEOUT_MILLIS,
                responseMimeType = AiTextMimeType.PLAIN,
                requiredCapabilityIds = listOf(
                    AiTextCapabilityId.STREAMING,
                    AiTextCapabilityId.USAGE,
                ),
            ),
        )
    }

    private fun assertDescriptorReadFails(
        observer: AiTextProviderSessionOperationObserver,
        bytes: ByteArray,
        declaredLength: Long,
    ) {
        val incoming = AndroidAiTextProviderSessionIncomingDescriptors(
            arrayOf(reliablePipe(bytes)),
            observer,
        )
        val lease = incoming.copyForAsync()
        incoming.close()
        try {
            expectFailure { lease.readDeclaredBytes(0, declaredLength) }
        } finally {
            lease.close()
            lease.close()
        }
    }

    private fun assertReliableProducerErrorFails(
        observer: AiTextProviderSessionOperationObserver,
    ) {
        val bytes = "producer-error".toByteArray(Charsets.UTF_8)
        val pipe = ParcelFileDescriptor.createReliablePipe()
        val output = FileOutputStream(pipe[1].fileDescriptor)
        output.write(bytes)
        output.flush()
        pipe[1].closeWithError("intentional conformance producer error")
        val incoming = AndroidAiTextProviderSessionIncomingDescriptors(arrayOf(pipe[0]), observer)
        val lease = incoming.copyForAsync()
        incoming.close()
        try {
            expectFailure { lease.readDeclaredBytes(0, bytes.size.toLong()) }
        } finally {
            runCatching(output::close)
            lease.close()
        }
    }

    private fun reliablePipe(bytes: ByteArray): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createReliablePipe()
        ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
            output.write(bytes)
        }
        return pipe[0]
    }

    private fun runHandshake(
        initial: AiTextProviderPackageSnapshot,
        worker: ThreadPoolExecutor,
        blocking: ThreadPoolExecutor,
        scheduler: ScheduledExecutorService,
    ): AiTextProviderPinnedIdentity {
        val outcome = AtomicReference<AiTextProviderHandshakeOutcome>()
        val terminal = CountDownLatch(1)
        val coordinator = AiTextProviderHandshakeCoordinator(
            transport = AndroidAiTextProviderHandshake(context),
            workerExecutor = worker,
            blockingExecutor = blocking,
            deadlineScheduler = scheduler.asHandshakeDeadlineScheduler(),
            gate = AiTextProviderHandshakeGate(),
        )
        val handle = coordinator.start(
            AiTextProviderHandshakeRequest(
                snapshot = initial,
                environment = environment(),
                hostCapabilities = hostCapabilities(),
                options = AiTextProviderHandshakeOptions(
                    enabled = true,
                    timeoutMillis = HANDSHAKE_TIMEOUT_MILLIS,
                ),
            ),
        ) { value ->
            if (outcome.compareAndSet(null, value)) terminal.countDown()
        }
        try {
            assertTrue(
                "AI text provider metadata handshake did not terminate",
                terminal.await(OUTCOME_WAIT_MILLIS, TimeUnit.MILLISECONDS),
            )
            return (outcome.get() as AiTextProviderHandshakeOutcome.Selected).pinned
        } finally {
            handle.close()
        }
    }

    private fun requireConformanceEnvironment() {
        assumeTrue(
            "AI text session Android conformance is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED).toBoolean(),
        )
        assertTrue("AI text session conformance requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals(
            "AI text fake and host must use the same dedicated debug/test signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, FAKE_PACKAGE),
        )
    }

    private fun discoverExact(component: AiTextProviderComponent): AiTextProviderPackageSnapshot {
        val result = AiTextProviderDiscoveryPolicy.inspectExact(
            source = AndroidAiTextProviderDiscovery(context),
            component = component,
            options = AiTextProviderDiscoveryOptions(enabled = true),
        ) as AiTextProviderDiscoveryResult.Completed
        return (result.evaluations.single() as AiTextProviderDiscoveryEvaluation.Discovered).snapshot
    }

    private fun environment() = AiTextProviderEnvironment(
        hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
        currentAbi = Build.SUPPORTED_ABIS.firstOrNull(),
        releaseBuild = false,
    )

    private fun hostCapabilities() = AiTextCapabilities(
        supportsStreaming = true,
        supportsReasoning = true,
        supportsTools = true,
        supportsStructuredJson = true,
        supportsUsage = true,
        maximumMessages = 128,
        maximumContentParts = 256,
        maximumToolDefinitions = 32,
        maximumContextBytes = 2L * 1024L * 1024L,
        maximumOutputBytes = 2L * 1024L * 1024L,
        maximumToolRounds = 8,
        maximumOutstandingToolCalls = 16,
        maximumRequestDescriptors = 16,
        maximumSessionDescriptors = 32,
        acceptedTextMimeTypes = listOf(AiTextMimeType.PLAIN, AiTextMimeType.JSON),
        acceptedSchemaMimeTypes = listOf(AiTextMimeType.JSON),
    )

    private fun boundedExecutor(name: String, capacity: Int) = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(capacity),
        namedThreadFactory(name),
        ThreadPoolExecutor.AbortPolicy(),
    )

    private fun namedThreadFactory(name: String) = java.util.concurrent.ThreadFactory { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }

    private fun ScheduledExecutorService.asHandshakeDeadlineScheduler() =
        AiTextProviderHandshakeDeadlineScheduler { delayMillis, action ->
            val future = schedule(action, delayMillis, TimeUnit.MILLISECONDS)
            Closeable { future.cancel(false) }
        }

    private fun ScheduledExecutorService.asSessionDeadlineScheduler() =
        AiTextProviderSessionDeadlineScheduler { delayMillis, action ->
            val future = schedule(action, delayMillis, TimeUnit.MILLISECONDS)
            Closeable { future.cancel(false) }
        }

    private fun shutdown(
        worker: ThreadPoolExecutor,
        blocking: ThreadPoolExecutor,
        fallback: ThreadPoolExecutor,
        scheduler: ScheduledExecutorService,
    ) {
        worker.shutdownNow()
        blocking.shutdownNow()
        fallback.shutdownNow()
        scheduler.shutdownNow()
        worker.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        blocking.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        fallback.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        scheduler.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS)
    }

    private fun AiTextProviderSessionRunEvent.isSessionTerminal(): Boolean = when (this) {
        AiTextProviderSessionRunEvent.Busy,
        AiTextProviderSessionRunEvent.Fused,
        is AiTextProviderSessionRunEvent.Rejected,
        -> true
        is AiTextProviderSessionRunEvent.Transcript -> when (event) {
            AiTextProviderSessionEvent.Cancelled,
            AiTextProviderSessionEvent.TimedOut,
            AiTextProviderSessionEvent.BinderDied,
            is AiTextProviderSessionEvent.Completed,
            is AiTextProviderSessionEvent.ProviderFailed,
            is AiTextProviderSessionEvent.Rejected,
            -> true
            AiTextProviderSessionEvent.Disabled,
            is AiTextProviderSessionEvent.Started,
            is AiTextProviderSessionEvent.Chunk,
            is AiTextProviderSessionEvent.Usage,
            -> false
        }
    }

    private inline fun expectFailure(block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: Throwable) {
            failed = true
        }
        assertTrue("Expected descriptor operation to fail", failed)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private companion object {
        const val ARG_ENABLED = "autojs.aiText.session.conformance.enabled"
        const val FAKE_PACKAGE = "org.autojs.plugin.ai.text.fake"
        const val FAKE_SERVICE =
            "org.autojs.plugin.ai.text.fake.service.FakeAiTextProviderService"
        const val FAKE_LISTING_GENERATION = "fake-models-v1"
        const val FAKE_STREAM_MODEL = "fake.stream"
        const val HANDSHAKE_TIMEOUT_MILLIS = 15_000L
        const val SESSION_TIMEOUT_MILLIS = 30_000L
        const val OUTCOME_WAIT_MILLIS = 40_000L
        const val LATE_CALLBACK_SETTLE_MILLIS = 250L
        const val EXECUTOR_SHUTDOWN_WAIT_MILLIS = 2_000L
    }
}
