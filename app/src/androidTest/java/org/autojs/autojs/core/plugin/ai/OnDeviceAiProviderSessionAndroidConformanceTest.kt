package org.autojs.autojs.core.plugin.ai

import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ondeviceai.api.AiContentPart
import org.autojs.plugin.ondeviceai.api.AiGenerationOptions
import org.autojs.plugin.ondeviceai.api.AiMessage
import org.autojs.plugin.ondeviceai.api.AiMessageRole
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilities
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilityId
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
import org.autojs.plugin.ondeviceai.api.OnDeviceAiProtocol
import org.autojs.plugin.ondeviceai.api.OnDeviceAiRequest
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
class OnDeviceAiProviderSessionAndroidConformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun exactComponentSessionStreamsPastInitialCreditsFromRequestDescriptor() {
        requireConformanceEnvironment()
        val component = OnDeviceAiProviderComponent(FAKE_PACKAGE, FAKE_SERVICE)
        val initial = discoverExact(component)
        val worker = boundedExecutor("on-device-ai-session-conformance-control", 64)
        val blocking = boundedExecutor("on-device-ai-session-conformance-binder", 32)
        val fallback = boundedExecutor("on-device-ai-session-conformance-terminal", 32)
        val scheduler = Executors.newSingleThreadScheduledExecutor(
            namedThreadFactory("on-device-ai-session-conformance-deadline"),
        )
        var runHandle: OnDeviceAiProviderSessionRunHandle? = null
        var requestDescriptors: AndroidOnDeviceAiProviderSessionRequestDescriptors? = null
        var requestReader: ParcelFileDescriptor? = null
        try {
            val pinned = runHandshake(initial, worker, blocking, scheduler)
            val prompt = buildString {
                repeat(24) { index -> append("chunk-").append(index).append('|') }
            }
            val promptBytes = prompt.toByteArray(Charsets.UTF_8)
            val pipe = reliablePipe(promptBytes)
            requestReader = pipe
            requestDescriptors = AndroidOnDeviceAiProviderSessionRequestDescriptors(arrayOf(pipe))
            val request = descriptorRequest(pinned, promptBytes)
            val plan = OnDeviceAiHostSessionPlan(
                pinnedProvider = pinned,
                listingGeneration = FAKE_LISTING_GENERATION,
                modelId = FAKE_STREAM_MODEL,
                modelCapabilityIds = setOf(
                    OnDeviceAiCapabilityId.STREAMING,
                    OnDeviceAiCapabilityId.STRUCTURED_JSON,
                    OnDeviceAiCapabilityId.USAGE,
                ),
                effectiveMaximumContextBytes = pinned.negotiatedCapabilities.maximumContextBytes,
                effectiveMaximumOutputBytes = pinned.negotiatedCapabilities.maximumOutputBytes,
                credentialDisposition = OnDeviceAiCredentialIsolationPolicy.disposition(pinned),
            )
            val events = Collections.synchronizedList(mutableListOf<OnDeviceAiProviderSessionRunEvent>())
            val terminal = CountDownLatch(1)
            val terminalCount = AtomicInteger()
            val callbackOnMain = AtomicBoolean(false)
            val terminalEvent = AtomicReference<OnDeviceAiProviderSessionRunEvent>()
            val coordinator = OnDeviceAiProviderSessionCoordinator(
                transport = AndroidOnDeviceAiProviderSession(context),
                workerExecutor = worker,
                blockingExecutor = blocking,
                terminalFallbackExecutor = fallback,
                deadlineScheduler = scheduler.asSessionDeadlineScheduler(),
                clock = OnDeviceAiProviderSessionClock { android.os.SystemClock.elapsedRealtime() },
                owner = OnDeviceAiProviderSessionProcessOwner(),
            )
            runHandle = coordinator.start(
                OnDeviceAiProviderSessionRunRequest(
                    snapshot = initial,
                    plan = plan,
                    request = request,
                    requestDescriptors = requestDescriptors,
                    environment = environment(),
                    hostCapabilities = hostCapabilities(),
                    options = OnDeviceAiProviderSessionRunOptions(enabled = true),
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
                "on-device AI provider session did not terminate",
                terminal.await(OUTCOME_WAIT_MILLIS, TimeUnit.MILLISECONDS),
            )
            val finalEvent = terminalEvent.get()
            val completion = (
                finalEvent as? OnDeviceAiProviderSessionRunEvent.Transcript
                )?.event as? OnDeviceAiProviderSessionEvent.Completed
            assertNotNull("on-device AI provider session did not complete successfully: $finalEvent", completion)
            val transcript = synchronized(events) {
                events.mapNotNull { (it as? OnDeviceAiProviderSessionRunEvent.Transcript)?.event }
            }
            val started = transcript.filterIsInstance<OnDeviceAiProviderSessionEvent.Started>().single()
            val chunks = transcript.filterIsInstance<OnDeviceAiProviderSessionEvent.Chunk>()
            val usage = transcript.filterIsInstance<OnDeviceAiProviderSessionEvent.Usage>()
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
        val observer = OnDeviceAiProviderSessionOperationObserver {
            operationStarts.incrementAndGet()
            Closeable { operationEnds.incrementAndGet() }
        }

        val exactBytes = "exact-payload".toByteArray(Charsets.UTF_8)
        val exactIncoming = AndroidOnDeviceAiProviderSessionIncomingDescriptors(
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

    @Test
    fun completionDescriptorIsMaterializedAcrossProcess() {
        val result = runHostileScenario(
            modelId = FAKE_COMPLETION_FD_MODEL,
            prompt = COMPLETION_FD_TEXT,
        )

        val completed = result.requireTranscriptTerminal<OnDeviceAiProviderSessionEvent.Completed>()
        assertEquals(COMPLETION_FD_TEXT, completed.result.outputText)
        assertEquals(1, completed.result.descriptorCount)
        assertSettledExactlyOnce(result)
    }

    @Test
    fun toolDescriptorIsOwnedThenRejectedWithoutExecution() {
        val result = runHostileScenario(modelId = FAKE_TOOL_FD_MODEL)

        assertTranscriptRejected(result, OnDeviceAiProviderSessionFailure.TOOLS_UNSUPPORTED)
        assertSettledExactlyOnce(result)
    }

    @Test
    fun chunkBeforeStartFailsClosedAfterInitialCreditCommit() {
        val result = runHostileScenario(
            modelId = FAKE_CHUNK_BEFORE_START_MODEL,
        )

        assertTranscriptRejected(result, OnDeviceAiProviderSessionFailure.CALLBACK_MALFORMED)
        assertSettledExactlyOnce(result)
    }

    @Test
    fun sequenceGapFailsClosedOnce() {
        val result = runHostileScenario(
            modelId = FAKE_SEQUENCE_GAP_MODEL,
        )

        assertTranscriptRejected(result, OnDeviceAiProviderSessionFailure.CALLBACK_MALFORMED)
        assertSettledExactlyOnce(result)
    }

    @Test
    fun invalidCompletionDescriptorReferenceFailsClosedOnce() {
        val result = runHostileScenario(modelId = FAKE_INVALID_FD_REFERENCE_MODEL)

        assertTranscriptRejected(result, OnDeviceAiProviderSessionFailure.CALLBACK_MALFORMED)
        assertSettledExactlyOnce(result)
    }

    @Test
    fun duplicateTerminalPublishesOnlyOneSettledOutcome() {
        val result = runHostileScenario(modelId = FAKE_DUPLICATE_TERMINAL_MODEL)

        when (val terminal = result.terminal) {
            is OnDeviceAiProviderSessionRunEvent.Transcript ->
                assertTrue(
                    "Unexpected duplicate-terminal transcript: ${terminal.event}",
                    terminal.event is OnDeviceAiProviderSessionEvent.Completed,
                )
            is OnDeviceAiProviderSessionRunEvent.Rejected -> {
                assertEquals(OnDeviceAiProviderSessionRunFailure.TRANSCRIPT_REJECTED, terminal.failure)
                assertEquals(
                    OnDeviceAiProviderSessionFailure.CALLBACK_OUT_OF_ORDER,
                    terminal.transcriptFailure,
                )
            }
            else -> assertTrue("Unexpected duplicate-terminal outcome: $terminal", false)
        }
        assertSettledExactlyOnce(result)
    }

    @Test
    fun stalledSessionCanBeCancelledAfterStartAndReleasesOwner() {
        val result = runHostileScenario(
            modelId = FAKE_STALL_MODEL,
            cancelAfterStarted = true,
        )

        assertTrue("Host cancel was not accepted for the stalled session", result.cancelAccepted)
        result.requireTranscriptTerminal<OnDeviceAiProviderSessionEvent.Cancelled>()
        assertEquals(1, result.transcript.filterIsInstance<OnDeviceAiProviderSessionEvent.Started>().size)
        assertSettledExactlyOnce(result)
    }

    @Test
    fun callbackFromIsolatedProviderUidIsRejectedAndReleasesOwner() {
        val result = runHostileScenario(modelId = FAKE_WRONG_UID_MODEL)

        assertTranscriptRejected(result, OnDeviceAiProviderSessionFailure.CALLBACK_UID_MISMATCH)
        assertTrue(
            "A callback from the isolated UID published transcript data",
            result.transcript.isEmpty(),
        )
        assertSettledExactlyOnce(result)
    }

    @Test
    fun providerProcessDeathAfterStartedPublishesOneBinderDiedAndReleasesOwner() {
        val result = runHostileScenario(modelId = FAKE_PROCESS_DEATH_MODEL)

        val terminal = result.terminal as? OnDeviceAiProviderSessionRunEvent.Transcript
        assertNotNull("Expected BinderDied transcript terminal, got ${result.terminal}", terminal)
        assertEquals(OnDeviceAiProviderSessionEvent.BinderDied, checkNotNull(terminal).event)
        assertEquals(1, result.transcript.filterIsInstance<OnDeviceAiProviderSessionEvent.Started>().size)
        val chunk = result.transcript.filterIsInstance<OnDeviceAiProviderSessionEvent.Chunk>().single()
        assertEquals(0L, chunk.chunk.sequence)
        assertEquals("hostile", chunk.chunk.textDelta)
        assertSettledExactlyOnce(result)
    }

    private fun runHostileScenario(
        modelId: String,
        prompt: String = HOSTILE_PROMPT,
        cancelAfterStarted: Boolean = false,
    ): HostileScenarioResult {
        requireConformanceEnvironment()
        val component = OnDeviceAiProviderComponent(FAKE_PACKAGE, FAKE_SERVICE)
        val initial = discoverExact(component)
        val worker = boundedExecutor("on-device-ai-hostile-control", 64)
        val blocking = boundedExecutor("on-device-ai-hostile-binder", 32)
        val fallback = boundedExecutor("on-device-ai-hostile-terminal", 32)
        val scheduler = Executors.newSingleThreadScheduledExecutor(
            namedThreadFactory("on-device-ai-hostile-deadline"),
        )
        val owner = OnDeviceAiProviderSessionProcessOwner()
        var runHandle: OnDeviceAiProviderSessionRunHandle? = null
        var requestDescriptors: AndroidOnDeviceAiProviderSessionRequestDescriptors? = null
        try {
            val pinned = runHandshake(initial, worker, blocking, scheduler)
            requestDescriptors = AndroidOnDeviceAiProviderSessionRequestDescriptors(
                emptyArray<ParcelFileDescriptor>(),
            )
            val request = hostileRequest(
                pinned = pinned,
                modelId = modelId,
                prompt = prompt,
            )
            val plan = OnDeviceAiHostSessionPlan(
                pinnedProvider = pinned,
                listingGeneration = FAKE_LISTING_GENERATION,
                modelId = modelId,
                modelCapabilityIds = setOf(OnDeviceAiCapabilityId.STREAMING),
                effectiveMaximumContextBytes = pinned.negotiatedCapabilities.maximumContextBytes,
                effectiveMaximumOutputBytes = pinned.negotiatedCapabilities.maximumOutputBytes,
                credentialDisposition = OnDeviceAiCredentialIsolationPolicy.disposition(pinned),
            )
            val events = Collections.synchronizedList(mutableListOf<OnDeviceAiProviderSessionRunEvent>())
            val started = CountDownLatch(1)
            val terminal = CountDownLatch(1)
            val terminalCount = AtomicInteger()
            val callbackOnMain = AtomicBoolean(false)
            val terminalEvent = AtomicReference<OnDeviceAiProviderSessionRunEvent>()
            val coordinator = OnDeviceAiProviderSessionCoordinator(
                transport = AndroidOnDeviceAiProviderSession(context),
                workerExecutor = worker,
                blockingExecutor = blocking,
                terminalFallbackExecutor = fallback,
                deadlineScheduler = scheduler.asSessionDeadlineScheduler(),
                clock = OnDeviceAiProviderSessionClock { android.os.SystemClock.elapsedRealtime() },
                owner = owner,
            )
            runHandle = coordinator.start(
                OnDeviceAiProviderSessionRunRequest(
                    snapshot = initial,
                    plan = plan,
                    request = request,
                    requestDescriptors = checkNotNull(requestDescriptors),
                    environment = environment(),
                    hostCapabilities = hostCapabilities(),
                    options = OnDeviceAiProviderSessionRunOptions(enabled = true),
                ),
            ) { event ->
                if (Looper.myLooper() == Looper.getMainLooper()) callbackOnMain.set(true)
                events += event
                if ((event as? OnDeviceAiProviderSessionRunEvent.Transcript)?.event is
                    OnDeviceAiProviderSessionEvent.Started
                ) {
                    started.countDown()
                }
                if (event.isSessionTerminal()) {
                    terminalCount.incrementAndGet()
                    if (terminalEvent.compareAndSet(null, event)) terminal.countDown()
                }
            }

            var cancelAccepted = false
            if (cancelAfterStarted) {
                assertTrue(
                    "Hostile on-device AI session did not start",
                    started.await(START_WAIT_MILLIS, TimeUnit.MILLISECONDS),
                )
                cancelAccepted = checkNotNull(runHandle).cancel()
            }
            assertTrue(
                "Hostile on-device AI session did not terminate: $modelId",
                terminal.await(OUTCOME_WAIT_MILLIS, TimeUnit.MILLISECONDS),
            )
            Thread.sleep(LATE_CALLBACK_SETTLE_MILLIS)
            assertFalse(
                "Hostile on-device AI callback was delivered on the Android main thread",
                callbackOnMain.get(),
            )
            assertTrue(initial.samePackageIdentityAs(discoverExact(component)))
            assertFalse(
                "A settled hostile session accepted another cancel",
                checkNotNull(runHandle).cancel(),
            )
            checkNotNull(runHandle).close()
            val admission = owner.tryAcquire(component)
            val ownerReusable = admission == OnDeviceAiProviderSessionAdmission.ACQUIRED
            if (ownerReusable) owner.release()
            return HostileScenarioResult(
                events = synchronized(events) { events.toList() },
                terminal = checkNotNull(terminalEvent.get()),
                terminalCount = terminalCount.get(),
                cancelAccepted = cancelAccepted,
                ownerReusable = ownerReusable,
            )
        } finally {
            runCatching { runHandle?.close() }
            runCatching { requestDescriptors?.close() }
            shutdown(worker, blocking, fallback, scheduler)
        }
    }

    private fun hostileRequest(
        pinned: OnDeviceAiProviderPinnedIdentity,
        modelId: String,
        prompt: String,
    ): OnDeviceAiRequest {
        val promptBytes = prompt.toByteArray(Charsets.UTF_8)
        return OnDeviceAiRequest(
            requestId = "android-hostile-${modelId.substringAfterLast('.')}",
            protocolVersion = OnDeviceAiProtocol.PROTOCOL_V1_2,
            providerId = pinned.providerId,
            modelId = modelId,
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
                stream = true,
                includeReasoning = false,
                structuredJson = false,
                reportUsage = false,
                maximumOutputBytes = 4_096,
                maximumToolRounds = 0,
                timeoutMillis = SESSION_TIMEOUT_MILLIS,
                responseMimeType = OnDeviceAiMimeType.PLAIN,
                requiredCapabilityIds = listOf(OnDeviceAiCapabilityId.STREAMING),
            ),
        )
    }

    private fun assertTranscriptRejected(
        result: HostileScenarioResult,
        expected: OnDeviceAiProviderSessionFailure,
    ) {
        val rejected = result.terminal as? OnDeviceAiProviderSessionRunEvent.Rejected
        assertNotNull("Expected transcript rejection, got ${result.terminal}", rejected)
        val terminal = checkNotNull(rejected)
        assertEquals(
            OnDeviceAiProviderSessionRunFailure.TRANSCRIPT_REJECTED,
            terminal.failure,
        )
        assertEquals(expected, terminal.transcriptFailure)
    }

    private fun assertSettledExactlyOnce(result: HostileScenarioResult) {
        assertEquals("Hostile session emitted more than one terminal event", 1, result.terminalCount)
        assertEquals(
            "Hostile session retained more than one terminal in its event list",
            1,
            result.events.count { it.isSessionTerminal() },
        )
        assertTrue("Hostile session did not release the process owner", result.ownerReusable)
    }

    private inline fun <reified T : OnDeviceAiProviderSessionEvent> HostileScenarioResult
        .requireTranscriptTerminal(): T {
        val transcript = terminal as? OnDeviceAiProviderSessionRunEvent.Transcript
        assertNotNull("Expected transcript terminal, got $terminal", transcript)
        val event = checkNotNull(transcript).event
        assertTrue("Expected ${T::class.java.simpleName}, got $event", event is T)
        return event as T
    }

    private data class HostileScenarioResult(
        val events: List<OnDeviceAiProviderSessionRunEvent>,
        val terminal: OnDeviceAiProviderSessionRunEvent,
        val terminalCount: Int,
        val cancelAccepted: Boolean,
        val ownerReusable: Boolean,
    ) {
        val transcript: List<OnDeviceAiProviderSessionEvent>
            get() = events.mapNotNull { (it as? OnDeviceAiProviderSessionRunEvent.Transcript)?.event }
    }

    private fun descriptorRequest(
        pinned: OnDeviceAiProviderPinnedIdentity,
        promptBytes: ByteArray,
    ): OnDeviceAiRequest {
        val payload = AiPayloadReference(
            mimeType = OnDeviceAiMimeType.PLAIN,
            declaredLengthBytes = promptBytes.size.toLong(),
            sha256 = sha256(promptBytes),
            descriptorIndex = 0,
            charset = "utf-8",
        )
        return OnDeviceAiRequest(
            requestId = "android-session-request",
            protocolVersion = OnDeviceAiProtocol.PROTOCOL_V1_2,
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
                responseMimeType = OnDeviceAiMimeType.PLAIN,
                requiredCapabilityIds = listOf(
                    OnDeviceAiCapabilityId.STREAMING,
                    OnDeviceAiCapabilityId.USAGE,
                ),
            ),
        )
    }

    private fun assertDescriptorReadFails(
        observer: OnDeviceAiProviderSessionOperationObserver,
        bytes: ByteArray,
        declaredLength: Long,
    ) {
        val incoming = AndroidOnDeviceAiProviderSessionIncomingDescriptors(
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
        observer: OnDeviceAiProviderSessionOperationObserver,
    ) {
        val bytes = "producer-error".toByteArray(Charsets.UTF_8)
        val pipe = ParcelFileDescriptor.createReliablePipe()
        val output = FileOutputStream(pipe[1].fileDescriptor)
        output.write(bytes)
        output.flush()
        pipe[1].closeWithError("intentional conformance producer error")
        val incoming = AndroidOnDeviceAiProviderSessionIncomingDescriptors(arrayOf(pipe[0]), observer)
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
        initial: OnDeviceAiProviderPackageSnapshot,
        worker: ThreadPoolExecutor,
        blocking: ThreadPoolExecutor,
        scheduler: ScheduledExecutorService,
    ): OnDeviceAiProviderPinnedIdentity {
        val outcome = AtomicReference<OnDeviceAiProviderHandshakeOutcome>()
        val terminal = CountDownLatch(1)
        val coordinator = OnDeviceAiProviderHandshakeCoordinator(
            transport = AndroidOnDeviceAiProviderHandshake(context),
            workerExecutor = worker,
            blockingExecutor = blocking,
            deadlineScheduler = scheduler.asHandshakeDeadlineScheduler(),
            gate = OnDeviceAiProviderHandshakeGate(),
        )
        val handle = coordinator.start(
            OnDeviceAiProviderHandshakeRequest(
                snapshot = initial,
                environment = environment(),
                hostCapabilities = hostCapabilities(),
                options = OnDeviceAiProviderHandshakeOptions(
                    enabled = true,
                    timeoutMillis = HANDSHAKE_TIMEOUT_MILLIS,
                ),
            ),
        ) { value ->
            if (outcome.compareAndSet(null, value)) terminal.countDown()
        }
        try {
            assertTrue(
                "on-device AI provider metadata handshake did not terminate",
                terminal.await(OUTCOME_WAIT_MILLIS, TimeUnit.MILLISECONDS),
            )
            return (outcome.get() as OnDeviceAiProviderHandshakeOutcome.Selected).pinned
        } finally {
            handle.close()
        }
    }

    private fun requireConformanceEnvironment() {
        assumeTrue(
            "on-device AI session Android conformance is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED).toBoolean(),
        )
        assertTrue("on-device AI session conformance requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals(
            "on-device AI fake and host must use the same dedicated debug/test signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, FAKE_PACKAGE),
        )
    }

    private fun discoverExact(component: OnDeviceAiProviderComponent): OnDeviceAiProviderPackageSnapshot {
        val result = OnDeviceAiProviderDiscoveryPolicy.inspectExact(
            source = AndroidOnDeviceAiProviderDiscovery(context),
            component = component,
            options = OnDeviceAiProviderDiscoveryOptions(enabled = true),
        ) as OnDeviceAiProviderDiscoveryResult.Completed
        return (result.evaluations.single() as OnDeviceAiProviderDiscoveryEvaluation.Discovered).snapshot
    }

    private fun environment() = OnDeviceAiProviderEnvironment(
        hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
        currentAbi = Build.SUPPORTED_ABIS.firstOrNull(),
        releaseBuild = false,
    )

    private fun hostCapabilities() = OnDeviceAiCapabilities(
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
        acceptedTextMimeTypes = listOf(OnDeviceAiMimeType.PLAIN, OnDeviceAiMimeType.JSON),
        acceptedSchemaMimeTypes = listOf(OnDeviceAiMimeType.JSON),
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
        OnDeviceAiProviderHandshakeDeadlineScheduler { delayMillis, action ->
            val future = schedule(action, delayMillis, TimeUnit.MILLISECONDS)
            Closeable { future.cancel(false) }
        }

    private fun ScheduledExecutorService.asSessionDeadlineScheduler() =
        OnDeviceAiProviderSessionDeadlineScheduler { delayMillis, action ->
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

    private fun OnDeviceAiProviderSessionRunEvent.isSessionTerminal(): Boolean = when (this) {
        OnDeviceAiProviderSessionRunEvent.Busy,
        OnDeviceAiProviderSessionRunEvent.Fused,
        is OnDeviceAiProviderSessionRunEvent.Rejected,
        -> true
        is OnDeviceAiProviderSessionRunEvent.Transcript -> when (event) {
            OnDeviceAiProviderSessionEvent.Cancelled,
            OnDeviceAiProviderSessionEvent.TimedOut,
            OnDeviceAiProviderSessionEvent.BinderDied,
            is OnDeviceAiProviderSessionEvent.Completed,
            is OnDeviceAiProviderSessionEvent.ProviderFailed,
            is OnDeviceAiProviderSessionEvent.Rejected,
            -> true
            OnDeviceAiProviderSessionEvent.Disabled,
            is OnDeviceAiProviderSessionEvent.Started,
            is OnDeviceAiProviderSessionEvent.Chunk,
            is OnDeviceAiProviderSessionEvent.Usage,
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
        const val ARG_ENABLED = "autojs.onDeviceAi.session.conformance.enabled"
        const val FAKE_PACKAGE = "org.autojs.plugin.ondeviceai.fake"
        const val FAKE_SERVICE =
            "org.autojs.plugin.ondeviceai.fake.service.FakeOnDeviceAiProviderService"
        const val FAKE_LISTING_GENERATION = "fake-models-v1"
        const val FAKE_STREAM_MODEL = "fake.stream"
        const val FAKE_COMPLETION_FD_MODEL = "fake.hostile.completion-fd"
        const val FAKE_TOOL_FD_MODEL = "fake.hostile.tool-fd"
        const val FAKE_CHUNK_BEFORE_START_MODEL = "fake.hostile.chunk-before-start"
        const val FAKE_SEQUENCE_GAP_MODEL = "fake.hostile.sequence-gap"
        const val FAKE_DUPLICATE_TERMINAL_MODEL = "fake.hostile.duplicate-terminal"
        const val FAKE_INVALID_FD_REFERENCE_MODEL = "fake.hostile.invalid-fd-ref"
        const val FAKE_WRONG_UID_MODEL = "fake.hostile.wrong-uid"
        const val FAKE_PROCESS_DEATH_MODEL = "fake.hostile.process-death"
        const val FAKE_STALL_MODEL = "fake.hostile.stall"
        const val COMPLETION_FD_TEXT = "descriptor completion across Binder"
        const val HOSTILE_PROMPT = "hostile Android session"
        const val HANDSHAKE_TIMEOUT_MILLIS = 15_000L
        const val SESSION_TIMEOUT_MILLIS = 30_000L
        const val START_WAIT_MILLIS = 15_000L
        const val OUTCOME_WAIT_MILLIS = 40_000L
        const val LATE_CALLBACK_SETTLE_MILLIS = 250L
        const val EXECUTOR_SHUTDOWN_WAIT_MILLIS = 2_000L
    }
}
