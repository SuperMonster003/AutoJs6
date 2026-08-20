package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiError
import org.autojs.plugin.ai.common.api.AiErrorCode
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiUsage
import org.autojs.plugin.ondeviceai.api.AiCompletionResult
import org.autojs.plugin.ondeviceai.api.AiContentPart
import org.autojs.plugin.ondeviceai.api.AiFinishReason
import org.autojs.plugin.ondeviceai.api.AiGenerationOptions
import org.autojs.plugin.ondeviceai.api.AiMessage
import org.autojs.plugin.ondeviceai.api.AiMessageRole
import org.autojs.plugin.ondeviceai.api.AiSessionStarted
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilityId
import org.autojs.plugin.ondeviceai.api.OnDeviceAiChunk
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCodec
import org.autojs.plugin.ondeviceai.api.OnDeviceAiLimits
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
import org.autojs.plugin.ondeviceai.api.OnDeviceAiRequest
import org.autojs.plugin.ondeviceai.api.AiToolCall
import org.autojs.plugin.ondeviceai.api.AiToolCallBatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class OnDeviceAiProviderSessionTest {

    @Test
    fun callbackBufferCapacityCannotUndercutInitialCreditWindow() {
        val minimumCapacity = OnDeviceAiLimits.INITIAL_CHUNK_CREDITS + 2
        val rejected = runCatching {
            OnDeviceAiProviderSessionOptions(
                enabled = true,
                maximumBufferedCallbacks = minimumCapacity - 1,
            )
        }.exceptionOrNull()

        assertTrue(rejected is IllegalArgumentException)
        assertEquals(
            minimumCapacity,
            OnDeviceAiProviderSessionOptions(
                enabled = true,
                maximumBufferedCallbacks = minimumCapacity,
            ).maximumBufferedCallbacks,
        )
    }

    @Test
    fun dataEventToStringsDoNotExposeSessionIdsOrGeneratedText() {
        val started = OnDeviceAiProviderSessionEvent.Started(
            AiSessionStarted(
                requestId = "secret-request",
                sessionId = "secret-session",
                protocolVersion = AiProtocolVersion(1, 0),
                providerId = PROVIDER_ID,
                modelId = MODEL_ID,
                effectiveMaximumOutputBytes = 4_096,
                effectiveMaximumToolRounds = 0,
            ),
        ).toString()
        val chunk = OnDeviceAiProviderSessionEvent.Chunk(
            OnDeviceAiChunk(
                sequence = 7,
                textDelta = "secret-generated-text",
                reasoningDelta = "secret-generated-reasoning",
            ),
        ).toString()

        assertFalse(started.contains("secret-request"))
        assertFalse(started.contains("secret-session"))
        assertFalse(started.contains("sessionId"))
        assertFalse(chunk.contains("secret-generated-text"))
        assertFalse(chunk.contains("secret-generated-reasoning"))
    }

    @Test
    fun defaultOffClosesRequestLeaseWithoutDispatchOrInlineListener() {
        val lane = ManualExecutor()
        val requestLease = RecordingRequestLease(0)
        val harness = harness(
            requestLease = requestLease,
            lane = lane,
            options = OnDeviceAiProviderSessionOptions(),
        )

        assertSame(OnDeviceAiProviderSessionOpenDecision.Disabled, harness.policy.beginOpen())
        assertEquals(1, requestLease.closeCount.get())
        assertTrue(harness.events.isEmpty())

        harness.drain()
        assertEquals(listOf(OnDeviceAiProviderSessionEvent.Disabled), harness.events)
        assertEquals(1, requestLease.closeCount.get())
    }

    @Test
    fun planRequestMismatchFailsBeforeDispatchAndClosesRequestLease() {
        val lane = ManualExecutor()
        val requestLease = RecordingRequestLease(0)
        val mismatched = request().copy(modelId = "fixture.other-model")
        val harness = harness(request = mismatched, requestLease = requestLease, lane = lane)

        val decision = harness.policy.beginOpen()
        assertEquals(
            OnDeviceAiProviderSessionOpenDecision.Rejected(
                OnDeviceAiProviderSessionFailure.PLAN_REQUEST_MISMATCH,
            ),
            decision,
        )
        assertTrue(harness.events.isEmpty())
        assertEquals(1, requestLease.closeCount.get())

        harness.drain()
        assertRejected(harness, OnDeviceAiProviderSessionFailure.PLAN_REQUEST_MISMATCH)
    }

    @Test
    fun dispatchSnapshotsEnvelopeAndDescriptorCountWhileLeaseRemainsBorrowed() {
        val lane = ManualExecutor()
        val requestLease = RecordingRequestLease(0)
        val harness = harness(requestLease = requestLease, lane = lane)
        val dispatch = harness.policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch

        val first = dispatch.copyRequestEnvelope()
        first.fill(0)
        val second = dispatch.copyRequestEnvelope()
        assertNotSame(first, second)
        assertFalse(second.all { it == 0.toByte() })
        assertEquals(0, dispatch.descriptorCount)
        requestLease.mutableCount = 7
        assertEquals(0, dispatch.descriptorCount)
        assertEquals(0, requestLease.closeCount.get())

        harness.policy.commitOpenFailed()
        assertEquals(1, requestLease.closeCount.get())
        harness.drain()
        assertRejected(harness, OnDeviceAiProviderSessionFailure.OPEN_FAILED)
    }

    @Test
    fun callbacksBeforeSynchronousOpenReturnAreDefensivelyBufferedAndCommittedInOrder() {
        val lane = ManualExecutor()
        val request = request(stream = false, reportUsage = true)
        val harness = harness(request = request, lane = lane)
        val dispatch = harness.policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
        val started = startedEnvelope(request)
        val completionLease = RecordingDescriptorLease(0)
        val completionIncoming = RecordingIncomingDescriptors(0, completionLease)

        dispatch.callback.onStarted(PROVIDER_UID, started)
        dispatch.callback.onUsage(
            PROVIDER_UID,
            AiCommonCodec.encodeUsage(AiUsage(inputTokens = 1)),
        )
        dispatch.callback.onCompleted(
            PROVIDER_UID,
            completionEnvelope(
                textPayload("ok"),
                usage = AiUsage(inputTokens = 1, outputTokens = 1, totalTokens = 2),
            ),
            completionIncoming,
        )
        started.fill(0)
        harness.drain()

        assertTrue(harness.events.isEmpty())
        assertEquals(0, harness.requestLease.closeCount.get())
        assertEquals(1, completionIncoming.copyCount.get())
        assertEquals(1, completionIncoming.closeCount.get())
        assertEquals(0, completionLease.closeCount.get())

        val remote = RecordingRemote()
        harness.policy.commitOpenSucceeded(remote)
        harness.drain()

        assertEquals(1, harness.requestLease.closeCount.get())
        assertEquals(
            listOf(
                OnDeviceAiProviderSessionEvent.Started::class.java,
                OnDeviceAiProviderSessionEvent.Usage::class.java,
                OnDeviceAiProviderSessionEvent.Completed::class.java,
            ),
            harness.events.map { it.javaClass },
        )
        val completed = harness.events.last() as OnDeviceAiProviderSessionEvent.Completed
        assertEquals("ok", completed.result.outputText)
        assertEquals(1, completionLease.closeCount.get())
        assertEquals(0, remote.cancelCount.get())
        assertEquals(1, remote.closeCount.get())
        assertTrue(remote.creditGrants.isEmpty())
    }

    @Test
    fun openFailureDiscardsBufferedCallbacksAndClosesEveryLeaseWithoutPublishingData() {
        val lane = ManualExecutor()
        val harness = harness(lane = lane)
        val dispatch = harness.policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
        val owned = RecordingDescriptorLease(0)
        val incoming = RecordingIncomingDescriptors(0, owned)

        dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(harness.request))
        dispatch.callback.onCompleted(
            PROVIDER_UID,
            completionEnvelope(textPayload("ignored")),
            incoming,
        )
        harness.drain()
        assertTrue(harness.events.isEmpty())

        harness.policy.commitOpenFailed()
        harness.drain()

        assertRejected(harness, OnDeviceAiProviderSessionFailure.OPEN_FAILED)
        assertEquals(1, harness.events.size)
        assertEquals(1, harness.requestLease.closeCount.get())
        assertEquals(1, incoming.copyCount.get())
        assertEquals(1, incoming.closeCount.get())
        assertEquals(1, owned.closeCount.get())
        assertEquals(0, owned.readCount.get())
    }

    @Test
    fun callbackUidIsRejectedBeforeEnvelopeCopyDescriptorCopyOrDecode() {
        val lane = ManualExecutor()
        val harness = harness(lane = lane)
        val dispatch = harness.policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
        val incoming = RecordingIncomingDescriptors(
            count = 1,
            lease = RecordingDescriptorLease(1),
            failCopy = true,
        )

        dispatch.callback.onCompleted(
            PROVIDER_UID + 1,
            byteArrayOf(1, 2, 3),
            incoming,
        )

        assertEquals(1, incoming.closeCount.get())
        assertEquals(0, incoming.copyCount.get())
        assertTrue(harness.events.isEmpty())

        val remote = RecordingRemote()
        harness.policy.commitOpenSucceeded(remote)
        harness.drain()
        assertRejected(harness, OnDeviceAiProviderSessionFailure.CALLBACK_UID_MISMATCH)
        assertEquals(1, remote.cancelCount.get())
        assertEquals(1, remote.closeCount.get())
    }

    @Test
    fun everyTypedCallbackRejectsMissingOversizeAndMalformedEnvelopes() {
        val kinds = listOf(
            TypedCallback.STARTED,
            TypedCallback.CHUNK,
            TypedCallback.USAGE,
            TypedCallback.COMPLETED,
            TypedCallback.FAILED,
            TypedCallback.TOOL_CALLS,
        )
        kinds.forEach { kind ->
            assertTypedCallbackFailure(kind, null, OnDeviceAiProviderSessionFailure.CALLBACK_MISSING)
            assertTypedCallbackFailure(kind, byteArrayOf(), OnDeviceAiProviderSessionFailure.CALLBACK_MISSING)
            val maximum = if (kind == TypedCallback.CHUNK) {
                OnDeviceAiLimits.MAX_CHUNK_ENVELOPE_BYTES
            } else {
                AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES
            }
            assertTypedCallbackFailure(
                kind,
                ByteArray(maximum + 1),
                OnDeviceAiProviderSessionFailure.CALLBACK_TOO_LARGE,
            )
            assertTypedCallbackFailure(
                kind,
                byteArrayOf(1, 2, 3),
                OnDeviceAiProviderSessionFailure.CALLBACK_MALFORMED,
            )
        }
    }

    @Test
    fun descriptorCallbacksRejectMissingOrExcessiveArraysAndCloseIncoming() {
        val completionHarness = activeHarness(stream = false)
        completionHarness.dispatch.callback.onCompleted(
            PROVIDER_UID,
            completionEnvelope(textPayload("ok")),
            null,
        )
        completionHarness.harness.drain()
        assertRejected(
            completionHarness.harness,
            OnDeviceAiProviderSessionFailure.CALLBACK_MISSING,
        )

        val toolHarness = activeHarness(stream = false)
        val excessive = RecordingIncomingDescriptors(
            count = toolHarness.harness.plan.pinnedProvider.negotiatedCapabilities.maximumSessionDescriptors + 1,
            lease = RecordingDescriptorLease(0),
        )
        toolHarness.dispatch.callback.onToolCalls(
            PROVIDER_UID,
            OnDeviceAiCodec.encodeToolCallBatch(
                AiToolCallBatch(listOf(AiToolCall("call-1", "lookup", jsonPayload("{}")))),
            ),
            excessive,
        )
        toolHarness.harness.drain()
        assertRejected(
            toolHarness.harness,
            OnDeviceAiProviderSessionFailure.CALLBACK_DESCRIPTOR_COUNT_INVALID,
        )
        assertEquals(1, excessive.closeCount.get())
        assertEquals(0, excessive.copyCount.get())
    }

    @Test
    fun primaryExecutorRejectionUsesFallbackLaneAndCleansLateRemoteAndLeases() {
        val fallback = ManualExecutor()
        val requestLease = RecordingRequestLease(0)
        val events = Collections.synchronizedList(mutableListOf<OnDeviceAiProviderSessionEvent>())
        val inIngress = AtomicBoolean(false)
        val policy = OnDeviceAiProviderSessionPolicy(
            plan = plan(),
            request = request(),
            requestDescriptors = requestLease,
            serializedControlExecutor = RejectingExecutor,
            terminalFallbackExecutor = fallback,
            options = OnDeviceAiProviderSessionOptions(enabled = true),
        ) { event ->
            assertFalse("Listener ran inline on Binder ingress", inIngress.get())
            events += event
        }
        val dispatch = policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
        val owned = RecordingDescriptorLease(0)
        val incoming = RecordingIncomingDescriptors(0, owned)

        inIngress.set(true)
        dispatch.callback.onCompleted(
            PROVIDER_UID,
            completionEnvelope(textPayload("ignored")),
            incoming,
        )
        inIngress.set(false)

        assertTrue(events.isEmpty())
        assertEquals(0, requestLease.closeCount.get())
        assertEquals(0, owned.closeCount.get())
        val remote = RecordingRemote()
        policy.commitOpenSucceeded(remote)
        assertTrue(events.isEmpty())

        fallback.drain()

        assertEquals(
            listOf(
                OnDeviceAiProviderSessionEvent.Rejected(
                    OnDeviceAiProviderSessionFailure.EXECUTOR_REJECTED,
                ),
            ),
            events,
        )
        assertEquals(1, requestLease.closeCount.get())
        assertEquals(1, incoming.closeCount.get())
        assertEquals(1, owned.closeCount.get())
        assertEquals(1, remote.cancelCount.get())
        assertEquals(1, remote.closeCount.get())
    }

    @Test
    fun delayedPrimaryRejectionPreservesClaimedPreCommitExternalTerminal() {
        data class Case(
            val terminate: (OnDeviceAiProviderSessionPolicy) -> Boolean,
            val event: OnDeviceAiProviderSessionEvent,
            val cancelsRemote: Boolean,
        )
        val cases = listOf(
            Case(OnDeviceAiProviderSessionPolicy::cancel, OnDeviceAiProviderSessionEvent.Cancelled, true),
            Case(OnDeviceAiProviderSessionPolicy::timeout, OnDeviceAiProviderSessionEvent.TimedOut, true),
            Case(OnDeviceAiProviderSessionPolicy::binderDied, OnDeviceAiProviderSessionEvent.BinderDied, false),
        )
        cases.forEach { case ->
            val executorEntered = CountDownLatch(1)
            val releaseExecutor = CountDownLatch(1)
            val callbackWorker = Executors.newSingleThreadExecutor()
            val terminalLane = ManualExecutor()
            val events = Collections.synchronizedList(mutableListOf<OnDeviceAiProviderSessionEvent>())
            val request = request(stream = false)
            val policy = OnDeviceAiProviderSessionPolicy(
                plan = plan(),
                request = request,
                requestDescriptors = RecordingRequestLease(0),
                serializedControlExecutor = BlockingRejectingExecutor(
                    entered = executorEntered,
                    release = releaseExecutor,
                ),
                terminalFallbackExecutor = terminalLane,
                options = OnDeviceAiProviderSessionOptions(enabled = true),
                onEvent = events::add,
            )
            try {
                val dispatch = policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
                val callback = callbackWorker.submit {
                    dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
                }
                assertTrue(executorEntered.await(2, TimeUnit.SECONDS))
                assertTrue(case.terminate(policy))

                releaseExecutor.countDown()
                callback.get(2, TimeUnit.SECONDS)
                val remote = RecordingRemote()
                policy.commitOpenSucceeded(remote)
                terminalLane.drain()

                assertEquals(listOf(case.event), events)
                assertEquals(if (case.cancelsRemote) 1 else 0, remote.cancelCount.get())
                assertEquals(1, remote.closeCount.get())
            } finally {
                releaseExecutor.countDown()
                callbackWorker.shutdownNow()
            }
        }
    }

    @Test
    fun rejectingTerminalExecutorIsFatalButStillClosesEveryOwnedResource() {
        val requestLease = RecordingRequestLease(0)
        val events = Collections.synchronizedList(mutableListOf<OnDeviceAiProviderSessionEvent>())
        val policy = OnDeviceAiProviderSessionPolicy(
            plan = plan(),
            request = request(),
            requestDescriptors = requestLease,
            serializedControlExecutor = RejectingExecutor,
            terminalFallbackExecutor = RejectingExecutor,
            options = OnDeviceAiProviderSessionOptions(enabled = true),
            onEvent = events::add,
        )
        val dispatch = policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
        val owned = RecordingDescriptorLease(0)
        val incoming = RecordingIncomingDescriptors(0, owned)

        val ingressFailure = runCatching {
            dispatch.callback.onCompleted(
                PROVIDER_UID,
                completionEnvelope(textPayload("discarded")),
                incoming,
            )
        }.exceptionOrNull()

        assertTrue(ingressFailure is IllegalStateException)
        assertTrue(
            ingressFailure?.message.orEmpty().contains("terminalFallbackExecutor"),
        )
        assertTrue(events.isEmpty())
        assertEquals(1, incoming.closeCount.get())
        assertEquals(1, owned.closeCount.get())
        assertEquals(0, requestLease.closeCount.get())

        val remote = RecordingRemote()
        val commitFailure = runCatching { policy.commitOpenSucceeded(remote) }.exceptionOrNull()
        assertTrue(commitFailure is IllegalStateException)
        assertTrue(events.isEmpty())
        assertEquals(1, requestLease.closeCount.get())
        assertEquals(1, remote.cancelCount.get())
        assertEquals(1, remote.closeCount.get())
    }

    @Test
    fun fallbackThenPrimaryRejectionUsesOneFatalDrainAndLaterFatalStillDrains() {
        val acceptedControl = ManualExecutor()
        val primaryEntered = CountDownLatch(1)
        val releasePrimary = CountDownLatch(1)
        val primaryExecutor = ArmedBlockingRejectingExecutor(
            accepted = acceptedControl,
            entered = primaryEntered,
            release = releasePrimary,
        )
        val fallbackEntered = CountDownLatch(1)
        val releaseFallback = CountDownLatch(1)
        val fallbackExecutor = BlockingRejectingExecutor(fallbackEntered, releaseFallback)
        val lateCloseEntered = CountDownLatch(1)
        val releaseLateClose = CountDownLatch(1)
        val callbackWorker = Executors.newSingleThreadExecutor()
        val lateWorker = Executors.newSingleThreadExecutor()
        val order = Collections.synchronizedList(mutableListOf<String>())
        val events = Collections.synchronizedList(mutableListOf<OnDeviceAiProviderSessionEvent>())
        val request = request(stream = false)
        val requestLease = RecordingRequestLease(0)
        val policy = OnDeviceAiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = requestLease,
            serializedControlExecutor = primaryExecutor,
            terminalFallbackExecutor = fallbackExecutor,
            options = OnDeviceAiProviderSessionOptions(enabled = true),
            onEvent = events::add,
        )
        try {
            val dispatch = policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
            dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
            val activeRemote = RecordingRemote(
                onCancel = { order += "active.cancel" },
                onClose = { order += "active.close" },
            )
            policy.commitOpenSucceeded(activeRemote)
            acceptedControl.drain()
            assertTrue(events.single() is OnDeviceAiProviderSessionEvent.Started)
            events.clear()
            assertEquals(1, requestLease.closeCount.get())

            primaryExecutor.arm()
            val owned = RecordingDescriptorLease(0)
            val incoming = RecordingIncomingDescriptors(0, owned)
            val callback = callbackWorker.submit {
                dispatch.callback.onCompleted(
                    PROVIDER_UID,
                    completionEnvelope(textPayload("discarded")),
                    incoming,
                )
            }
            assertTrue(primaryEntered.await(2, TimeUnit.SECONDS))

            val firstLate = BlockingCloseRemote(
                closeEntered = lateCloseEntered,
                releaseClose = releaseLateClose,
                onCancel = { order += "first.cancel" },
                onClose = { order += "first.close" },
            )
            val secondLate = RecordingRemote(
                onCancel = { order += "second.cancel" },
                onClose = { order += "second.close" },
            )
            val fallbackFailure = lateWorker.submit {
                policy.commitOpenSucceeded(firstLate)
            }
            assertTrue(fallbackEntered.await(2, TimeUnit.SECONDS))
            policy.commitOpenSucceeded(secondLate)

            releaseFallback.countDown()
            assertTrue(lateCloseEntered.await(2, TimeUnit.SECONDS))
            releasePrimary.countDown()
            callback.get(2, TimeUnit.SECONDS)
            assertTrue(events.isEmpty())

            releaseLateClose.countDown()
            assertFatalFuture(fallbackFailure)

            assertEquals(1, activeRemote.cancelCount.get())
            assertEquals(1, activeRemote.closeCount.get())
            assertEquals(1, firstLate.cancelCount.get())
            assertEquals(1, firstLate.closeCount.get())
            assertEquals(1, secondLate.cancelCount.get())
            assertEquals(1, secondLate.closeCount.get())
            assertEquals(1, incoming.closeCount.get())
            assertEquals(1, owned.closeCount.get())
            assertEquals(1, requestLease.closeCount.get())
            assertTrue(events.isEmpty())
            listOf("active", "first", "second").forEach { name ->
                assertTrue(order.indexOf("$name.cancel") in 0 until order.indexOf("$name.close"))
            }

            val later = RecordingRemote(
                onCancel = { order += "later.cancel" },
                onClose = { order += "later.close" },
            )
            val laterFailure = runCatching { policy.commitOpenSucceeded(later) }.exceptionOrNull()
            assertFatalFailure(laterFailure)
            assertEquals(1, later.cancelCount.get())
            assertEquals(1, later.closeCount.get())
            assertTrue(order.indexOf("later.cancel") in 0 until order.indexOf("later.close"))
            assertTrue(events.isEmpty())
        } finally {
            releasePrimary.countDown()
            releaseFallback.countDown()
            releaseLateClose.countDown()
            lateWorker.shutdownNow()
            callbackWorker.shutdownNow()
        }
    }

    @Test
    fun initialCreditGrantMaySynchronouslyReenterWithAChunkWithoutNestedDelivery() {
        val lane = ManualExecutor()
        val harness = harness(request = request(stream = true), lane = lane)
        val dispatch = harness.policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
        dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(harness.request))
        lateinit var remote: RecordingRemote
        remote = RecordingRemote(onGrant = { count ->
            if (count == OnDeviceAiLimits.INITIAL_CHUNK_CREDITS) {
                assertTrue(harness.events.isEmpty())
                dispatch.callback.onChunk(
                    PROVIDER_UID,
                    OnDeviceAiCodec.encodeTextChunk(OnDeviceAiChunk(0, textDelta = "a")),
                )
                assertTrue(harness.events.isEmpty())
            }
        })

        harness.policy.commitOpenSucceeded(remote)
        assertTrue(harness.events.isEmpty())
        harness.drain()

        assertEquals(
            listOf(
                OnDeviceAiProviderSessionEvent.Started::class.java,
                OnDeviceAiProviderSessionEvent.Chunk::class.java,
            ),
            harness.events.map { it.javaClass },
        )
        assertEquals(
            listOf(OnDeviceAiLimits.INITIAL_CHUNK_CREDITS, 1),
            remote.creditGrants,
        )
    }

    @Test
    fun minimumBufferAcceptsInitialWindowAndTerminalWithoutReplenishingClaimedChunks() {
        val lane = ManualExecutor()
        val request = request(stream = true)
        val harness = harness(
            request = request,
            lane = lane,
            options = OnDeviceAiProviderSessionOptions(
                enabled = true,
                maximumBufferedCallbacks = OnDeviceAiLimits.INITIAL_CHUNK_CREDITS + 2,
            ),
        )
        val dispatch = harness.policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
        dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
        val completionOwned = RecordingDescriptorLease(0)
        val completionIncoming = RecordingIncomingDescriptors(0, completionOwned)
        val remote = RecordingRemote(onGrant = { count ->
            if (count == OnDeviceAiLimits.INITIAL_CHUNK_CREDITS) {
                repeat(OnDeviceAiLimits.INITIAL_CHUNK_CREDITS) { sequence ->
                    dispatch.callback.onChunk(
                        PROVIDER_UID,
                        OnDeviceAiCodec.encodeTextChunk(
                            OnDeviceAiChunk(sequence.toLong(), textDelta = sequence.toString()),
                        ),
                    )
                }
                dispatch.callback.onCompleted(
                    PROVIDER_UID,
                    completionEnvelope(textPayload("01234567")),
                    completionIncoming,
                )
                assertTrue(harness.events.isEmpty())
            }
        })

        harness.policy.commitOpenSucceeded(remote)
        harness.drain()

        assertEquals(
            listOf(OnDeviceAiProviderSessionEvent.Started::class.java) +
                List(OnDeviceAiLimits.INITIAL_CHUNK_CREDITS) {
                    OnDeviceAiProviderSessionEvent.Chunk::class.java
                } + OnDeviceAiProviderSessionEvent.Completed::class.java,
            harness.events.map { it.javaClass },
        )
        assertEquals(
            (0 until OnDeviceAiLimits.INITIAL_CHUNK_CREDITS).map(Int::toLong),
            harness.events.filterIsInstance<OnDeviceAiProviderSessionEvent.Chunk>()
                .map { it.chunk.sequence },
        )
        assertEquals(
            "01234567",
            (harness.events.last() as OnDeviceAiProviderSessionEvent.Completed).result.outputText,
        )
        assertEquals(listOf(OnDeviceAiLimits.INITIAL_CHUNK_CREDITS), remote.creditGrants)
        assertEquals(0, remote.cancelCount.get())
        assertEquals(1, remote.closeCount.get())
        assertEquals(1, completionIncoming.closeCount.get())
        assertEquals(1, completionOwned.closeCount.get())
    }

    @Test
    fun initialCreditGrantFailureDiscardsBufferedCallbacksAndHasOneTerminal() {
        listOf(false, true).forEach { reenterBeforeThrow ->
            val lane = ManualExecutor()
            val teardownOrder = mutableListOf<String>()
            val harness = harness(request = request(stream = true), lane = lane)
            val dispatch = harness.policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
            dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(harness.request))
            val remote = RecordingRemote(
                onGrant = { count ->
                    if (reenterBeforeThrow && count == OnDeviceAiLimits.INITIAL_CHUNK_CREDITS) {
                        dispatch.callback.onChunk(
                            PROVIDER_UID,
                            OnDeviceAiCodec.encodeTextChunk(OnDeviceAiChunk(0, textDelta = "discarded")),
                        )
                    }
                    throw IllegalStateException("grant failed for test")
                },
                onCancel = { teardownOrder += "cancel" },
                onClose = { teardownOrder += "close" },
            )

            harness.policy.commitOpenSucceeded(remote)
            harness.drain()

            assertEquals(
                listOf(
                    OnDeviceAiProviderSessionEvent.Rejected(
                        OnDeviceAiProviderSessionFailure.REMOTE_CONTROL_FAILED,
                    ),
                ),
                harness.events,
            )
            assertEquals(listOf(OnDeviceAiLimits.INITIAL_CHUNK_CREDITS), remote.creditGrants)
            assertEquals(listOf("cancel", "close"), teardownOrder)
            assertEquals(1, remote.cancelCount.get())
            assertEquals(1, remote.closeCount.get())

            dispatch.callback.onChunk(
                PROVIDER_UID,
                OnDeviceAiCodec.encodeTextChunk(OnDeviceAiChunk(1, textDelta = "late")),
            )
            harness.drain()
            assertEquals(1, harness.events.size)
            assertEquals(listOf(OnDeviceAiLimits.INITIAL_CHUNK_CREDITS), remote.creditGrants)
        }
    }

    @Test
    fun cancelTearsDownRemoteWhileInitialCreditGrantIsBlockedButWaitsToPublish() {
        val dataLane = Executors.newSingleThreadExecutor()
        val terminalLane = Executors.newSingleThreadExecutor()
        val cancelCaller = Executors.newSingleThreadExecutor()
        val grantEntered = CountDownLatch(1)
        val releaseGrant = CountDownLatch(1)
        val remoteClosed = CountDownLatch(1)
        val terminalDelivered = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<OnDeviceAiProviderSessionEvent>())
        val request = request(stream = true)
        val policy = OnDeviceAiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = RecordingRequestLease(0),
            serializedControlExecutor = dataLane,
            terminalFallbackExecutor = terminalLane,
            options = OnDeviceAiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            if (event == OnDeviceAiProviderSessionEvent.Cancelled) terminalDelivered.countDown()
        }
        try {
            val dispatch = policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
            dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
            val remote = RecordingRemote(
                onGrant = { count ->
                    if (count == OnDeviceAiLimits.INITIAL_CHUNK_CREDITS) {
                        grantEntered.countDown()
                        check(releaseGrant.await(2, TimeUnit.SECONDS))
                    }
                },
                onClose = { remoteClosed.countDown() },
            )
            policy.commitOpenSucceeded(remote)
            assertTrue(grantEntered.await(2, TimeUnit.SECONDS))

            assertTrue(cancelCaller.submit<Boolean> { policy.cancel() }.get(2, TimeUnit.SECONDS))
            assertTrue(remoteClosed.await(2, TimeUnit.SECONDS))
            assertFalse(terminalDelivered.await(100, TimeUnit.MILLISECONDS))
            assertTrue(events.isEmpty())
            assertEquals(1, remote.cancelCount.get())
            assertEquals(1, remote.closeCount.get())

            releaseGrant.countDown()
            assertTrue(terminalDelivered.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(OnDeviceAiProviderSessionEvent.Cancelled), events)
            assertEquals(listOf(OnDeviceAiLimits.INITIAL_CHUNK_CREDITS), remote.creditGrants)
        } finally {
            releaseGrant.countDown()
            cancelCaller.shutdownNow()
            terminalLane.shutdownNow()
            dataLane.shutdownNow()
        }
    }

    @Test
    fun replenishCreditFailureClosesBufferedLeaseAndHasOneTerminal() {
        val lane = ManualExecutor()
        val teardownOrder = mutableListOf<String>()
        val harness = harness(request = request(stream = true), lane = lane)
        val dispatch = harness.policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
        dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(harness.request))
        val owned = RecordingDescriptorLease(0)
        val incoming = RecordingIncomingDescriptors(0, owned)
        val remote = RecordingRemote(
            onGrant = { count ->
                if (count == 1) {
                    dispatch.callback.onCompleted(
                        PROVIDER_UID,
                        completionEnvelope(textPayload("discarded")),
                        incoming,
                    )
                    throw IllegalStateException("replenish failed for test")
                }
            },
            onCancel = { teardownOrder += "cancel" },
            onClose = { teardownOrder += "close" },
        )
        harness.policy.commitOpenSucceeded(remote)
        harness.drain()
        assertTrue(harness.events.single() is OnDeviceAiProviderSessionEvent.Started)
        harness.events.clear()

        dispatch.callback.onChunk(
            PROVIDER_UID,
            OnDeviceAiCodec.encodeTextChunk(OnDeviceAiChunk(0, textDelta = "delivered")),
        )
        harness.drain()

        assertEquals(
            listOf(
                OnDeviceAiProviderSessionEvent.Chunk::class.java,
                OnDeviceAiProviderSessionEvent.Rejected::class.java,
            ),
            harness.events.map { it.javaClass },
        )
        assertRejected(harness, OnDeviceAiProviderSessionFailure.REMOTE_CONTROL_FAILED)
        assertEquals(
            listOf(OnDeviceAiLimits.INITIAL_CHUNK_CREDITS, 1),
            remote.creditGrants,
        )
        assertEquals(listOf("cancel", "close"), teardownOrder)
        assertEquals(1, incoming.closeCount.get())
        assertEquals(1, owned.closeCount.get())
        assertEquals(0, owned.readCount.get())

        dispatch.callback.onChunk(
            PROVIDER_UID,
            OnDeviceAiCodec.encodeTextChunk(OnDeviceAiChunk(1, textDelta = "late")),
        )
        harness.drain()
        assertEquals(2, harness.events.size)
        assertEquals(
            listOf(OnDeviceAiLimits.INITIAL_CHUNK_CREDITS, 1),
            remote.creditGrants,
        )
    }

    @Test
    fun ninthChunkBeforeAnyReplenishmentFailsClosedForNoCredit() {
        val active = activeHarness(stream = true)
        active.harness.events.clear()

        repeat(OnDeviceAiLimits.INITIAL_CHUNK_CREDITS + 1) { sequence ->
            active.dispatch.callback.onChunk(
                PROVIDER_UID,
                OnDeviceAiCodec.encodeTextChunk(
                    OnDeviceAiChunk(sequence.toLong(), textDelta = "x"),
                ),
            )
        }
        active.harness.drain()

        assertRejected(active.harness, OnDeviceAiProviderSessionFailure.CALLBACK_NO_CREDIT)
        assertEquals(1, active.harness.events.size)
        assertEquals(
            listOf(OnDeviceAiLimits.INITIAL_CHUNK_CREDITS),
            active.remote.creditGrants,
        )
        assertEquals(1, active.remote.cancelCount.get())
        assertEquals(1, active.remote.closeCount.get())
    }

    @Test
    fun chunkWhileSynchronousOpenIsUnresolvedFailsBeforeCommit() {
        val lane = ManualExecutor()
        val harness = harness(request = request(stream = true), lane = lane)
        val dispatch = harness.policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch

        dispatch.callback.onChunk(
            PROVIDER_UID,
            OnDeviceAiCodec.encodeTextChunk(OnDeviceAiChunk(0, textDelta = "early")),
        )
        harness.drain()
        assertTrue(harness.events.isEmpty())

        val remote = RecordingRemote()
        harness.policy.commitOpenSucceeded(remote)
        harness.drain()
        assertRejected(harness, OnDeviceAiProviderSessionFailure.CALLBACK_OUT_OF_ORDER)
        assertTrue(remote.creditGrants.isEmpty())
        assertEquals(1, remote.cancelCount.get())
        assertEquals(1, remote.closeCount.get())
    }

    @Test
    fun externalCancelOverridesProvisionalPreCommitIngressFailure() {
        val lane = ManualExecutor()
        val harness = harness(lane = lane)
        val dispatch = harness.policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch

        dispatch.callback.onStarted(PROVIDER_UID, null)
        assertTrue(harness.policy.cancel())
        val remote = RecordingRemote()
        harness.policy.commitOpenSucceeded(remote)
        harness.drain()

        assertEquals(listOf(OnDeviceAiProviderSessionEvent.Cancelled), harness.events)
        assertEquals(1, remote.cancelCount.get())
        assertEquals(1, remote.closeCount.get())
    }

    @Test
    fun descriptorBackedCompletionReadsExactlyAndClosesBothDescriptorOwners() {
        val active = activeHarness(stream = false)
        val owned = RecordingDescriptorLease(1) { index, length ->
            assertEquals(0, index)
            assertEquals(2L, length)
            "ok".toByteArray()
        }
        val incoming = RecordingIncomingDescriptors(1, owned)

        active.dispatch.callback.onCompleted(
            PROVIDER_UID,
            completionEnvelope(descriptorTextPayload(index = 0, length = 2)),
            incoming,
        )
        assertEquals(1, incoming.closeCount.get())
        assertEquals(1, incoming.copyCount.get())
        assertEquals(0, owned.readCount.get())

        active.harness.drain()

        val completed = active.harness.events.last() as OnDeviceAiProviderSessionEvent.Completed
        assertEquals("ok", completed.result.outputText)
        assertEquals(1, owned.readCount.get())
        assertEquals(1, owned.closeCount.get())
        assertEquals(1, active.remote.closeCount.get())
    }

    @Test
    fun completionRejectsInvalidIndexTruncatedAndOverlongDescriptorsAndAlwaysCloses() {
        data class Case(
            val payload: AiPayloadReference,
            val bytes: ByteArray,
            val expectedReads: Int,
        )
        val cases = listOf(
            Case(descriptorTextPayload(index = 1, length = 2), "ok".toByteArray(), 0),
            Case(descriptorTextPayload(index = 0, length = 2), "x".toByteArray(), 1),
            Case(descriptorTextPayload(index = 0, length = 2), "xxx".toByteArray(), 1),
        )

        cases.forEach { case ->
            val active = activeHarness(stream = false)
            val owned = RecordingDescriptorLease(1) { _, _ -> case.bytes }
            val incoming = RecordingIncomingDescriptors(1, owned)
            active.dispatch.callback.onCompleted(
                PROVIDER_UID,
                completionEnvelope(case.payload),
                incoming,
            )
            active.harness.drain()

            assertRejected(active.harness, OnDeviceAiProviderSessionFailure.CALLBACK_MALFORMED)
            assertEquals(case.expectedReads, owned.readCount.get())
            assertEquals(1, incoming.closeCount.get())
            assertEquals(1, owned.closeCount.get())
            assertEquals(1, active.remote.cancelCount.get())
            assertEquals(1, active.remote.closeCount.get())
        }
    }

    @Test
    fun toolCallsAreRequestAwareClosedAndFailClosedWithoutExecutingTools() {
        val active = activeHarness(stream = false)
        val owned = RecordingDescriptorLease(1) { _, _ -> "{}".toByteArray() }
        val incoming = RecordingIncomingDescriptors(1, owned)
        val calls = AiToolCallBatch(
            listOf(
                AiToolCall(
                    callId = "call-1",
                    name = "undeclared-tool",
                    arguments = descriptorJsonPayload(index = 0, length = 2),
                ),
            ),
        )

        active.dispatch.callback.onToolCalls(
            PROVIDER_UID,
            OnDeviceAiCodec.encodeToolCallBatch(calls),
            incoming,
        )
        active.harness.drain()

        assertRejected(active.harness, OnDeviceAiProviderSessionFailure.TOOLS_UNSUPPORTED)
        assertEquals(0, owned.readCount.get())
        assertEquals(1, incoming.closeCount.get())
        assertEquals(1, owned.closeCount.get())
        assertEquals(1, active.remote.cancelCount.get())
        assertEquals(1, active.remote.closeCount.get())
    }

    @Test
    fun externalTerminalDuringSynchronousOpenWaitsForLateHandleCleanupBeforeDelivery() {
        data class Case(
            val terminate: (OnDeviceAiProviderSessionPolicy) -> Boolean,
            val event: OnDeviceAiProviderSessionEvent,
            val cancelsRemote: Boolean,
        )
        val cases = listOf(
            Case(OnDeviceAiProviderSessionPolicy::cancel, OnDeviceAiProviderSessionEvent.Cancelled, true),
            Case(OnDeviceAiProviderSessionPolicy::timeout, OnDeviceAiProviderSessionEvent.TimedOut, true),
            Case(OnDeviceAiProviderSessionPolicy::binderDied, OnDeviceAiProviderSessionEvent.BinderDied, false),
        )
        cases.forEach { case ->
            val lane = ManualExecutor()
            val order = Collections.synchronizedList(mutableListOf<String>())
            val requestLease = RecordingRequestLease(0) { order += "request.close" }
            val harness = harness(
                requestLease = requestLease,
                lane = lane,
                onEvent = { event ->
                    order += "listener.${event.javaClass.simpleName}"
                },
            )
            harness.policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch

            assertTrue(case.terminate(harness.policy))
            assertFalse(case.terminate(harness.policy))
            harness.drain()
            assertTrue(harness.events.isEmpty())
            assertEquals(0, requestLease.closeCount.get())

            val remote = RecordingRemote(
                onCancel = { order += "remote.cancel" },
                onClose = { order += "remote.close" },
            )
            harness.policy.commitOpenSucceeded(remote)
            assertEquals(1, requestLease.closeCount.get())
            assertTrue(harness.events.isEmpty())
            harness.drain()

            assertEquals(listOf(case.event), harness.events)
            assertEquals(if (case.cancelsRemote) 1 else 0, remote.cancelCount.get())
            assertEquals(1, remote.closeCount.get())
            assertEquals("request.close", order.first())
            assertEquals("listener.${case.event.javaClass.simpleName}", order.last())
        }
    }

    @Test
    fun cancelLinearizesAgainstQueuedDataAndDoesNotReplenishLateChunkCredit() {
        val active = activeHarness(stream = true)
        active.harness.events.clear()
        active.dispatch.callback.onChunk(
            PROVIDER_UID,
            OnDeviceAiCodec.encodeTextChunk(OnDeviceAiChunk(0, textDelta = "late")),
        )
        active.dispatch.callback.onUsage(
            PROVIDER_UID,
            AiCommonCodec.encodeUsage(AiUsage(outputTokens = 1)),
        )

        assertTrue(active.harness.policy.cancel())
        active.harness.drain()

        assertEquals(listOf(OnDeviceAiProviderSessionEvent.Cancelled), active.harness.events)
        assertEquals(
            listOf(OnDeviceAiLimits.INITIAL_CHUNK_CREDITS),
            active.remote.creditGrants,
        )
        assertEquals(1, active.remote.cancelCount.get())
        assertEquals(1, active.remote.closeCount.get())
    }

    @Test
    fun cancelDoesNotWaitForClaimedDataButTerminalWaitsAndNoLaterDataIsClaimed() {
        val dataLane = Executors.newSingleThreadExecutor()
        val terminalLane = Executors.newSingleThreadExecutor()
        val cancelCaller = Executors.newSingleThreadExecutor()
        val releaseData = CountDownLatch(1)
        val startedDelivered = CountDownLatch(1)
        val dataClaimed = CountDownLatch(1)
        val remoteClosed = CountDownLatch(1)
        val terminalDelivered = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<OnDeviceAiProviderSessionEvent>())
        val request = request(stream = true)
        val requestLease = RecordingRequestLease(0)
        val policy = OnDeviceAiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = requestLease,
            serializedControlExecutor = dataLane,
            terminalFallbackExecutor = terminalLane,
            options = OnDeviceAiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            when (event) {
                is OnDeviceAiProviderSessionEvent.Started -> startedDelivered.countDown()
                is OnDeviceAiProviderSessionEvent.Chunk -> {
                    dataClaimed.countDown()
                    check(releaseData.await(2, TimeUnit.SECONDS))
                }
                OnDeviceAiProviderSessionEvent.Cancelled -> terminalDelivered.countDown()
                else -> Unit
            }
        }
        try {
            val dispatch = policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
            dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
            val remote = RecordingRemote(onClose = { remoteClosed.countDown() })
            policy.commitOpenSucceeded(remote)
            assertTrue(startedDelivered.await(2, TimeUnit.SECONDS))

            dispatch.callback.onChunk(
                PROVIDER_UID,
                OnDeviceAiCodec.encodeTextChunk(OnDeviceAiChunk(0, textDelta = "claimed")),
            )
            assertTrue(dataClaimed.await(2, TimeUnit.SECONDS))

            assertTrue(cancelCaller.submit<Boolean> { policy.cancel() }.get(2, TimeUnit.SECONDS))
            dispatch.callback.onUsage(
                PROVIDER_UID,
                AiCommonCodec.encodeUsage(AiUsage(outputTokens = 1)),
            )
            assertTrue(remoteClosed.await(2, TimeUnit.SECONDS))
            assertFalse(terminalDelivered.await(100, TimeUnit.MILLISECONDS))
            assertEquals(1, remote.cancelCount.get())
            assertEquals(1, remote.closeCount.get())

            releaseData.countDown()
            assertTrue(terminalDelivered.await(2, TimeUnit.SECONDS))
            assertEquals(
                listOf(
                    OnDeviceAiProviderSessionEvent.Started::class.java,
                    OnDeviceAiProviderSessionEvent.Chunk::class.java,
                    OnDeviceAiProviderSessionEvent.Cancelled::class.java,
                ),
                events.map { it.javaClass },
            )
            assertEquals(
                listOf(OnDeviceAiLimits.INITIAL_CHUNK_CREDITS),
                remote.creditGrants,
            )
        } finally {
            releaseData.countDown()
            cancelCaller.shutdownNow()
            terminalLane.shutdownNow()
            dataLane.shutdownNow()
        }
    }

    @Test
    fun externalCancelClaimSuppressesLaterProviderTerminalCallback() {
        val cancelFirst = activeHarness(stream = false)
        cancelFirst.harness.events.clear()
        assertTrue(cancelFirst.harness.policy.cancel())
        cancelFirst.dispatch.callback.onFailed(
            PROVIDER_UID,
            AiCommonCodec.encodeError(AiError(code = AiErrorCode.INVALID_REQUEST)),
        )
        cancelFirst.harness.drain()
        assertEquals(listOf(OnDeviceAiProviderSessionEvent.Cancelled), cancelFirst.harness.events)
        assertEquals(1, cancelFirst.remote.cancelCount.get())
        assertEquals(1, cancelFirst.remote.closeCount.get())
    }

    @Test
    fun chunkListenerMayReenterCancelWithoutDeadlockOrCreditReplenishment() {
        val dataLane = Executors.newSingleThreadExecutor()
        val terminalLane = Executors.newSingleThreadExecutor()
        val startedDelivered = CountDownLatch(1)
        val terminalDelivered = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<OnDeviceAiProviderSessionEvent>())
        val reentrantResult = AtomicBoolean(false)
        val request = request(stream = true)
        lateinit var policy: OnDeviceAiProviderSessionPolicy
        policy = OnDeviceAiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = RecordingRequestLease(0),
            serializedControlExecutor = dataLane,
            terminalFallbackExecutor = terminalLane,
            options = OnDeviceAiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            when (event) {
                is OnDeviceAiProviderSessionEvent.Started -> startedDelivered.countDown()
                is OnDeviceAiProviderSessionEvent.Chunk -> reentrantResult.set(policy.cancel())
                OnDeviceAiProviderSessionEvent.Cancelled -> terminalDelivered.countDown()
                else -> Unit
            }
        }
        try {
            val dispatch = policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
            dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
            val remote = RecordingRemote()
            policy.commitOpenSucceeded(remote)
            assertTrue(startedDelivered.await(2, TimeUnit.SECONDS))

            dispatch.callback.onChunk(
                PROVIDER_UID,
                OnDeviceAiCodec.encodeTextChunk(OnDeviceAiChunk(0, textDelta = "claim")),
            )
            assertTrue(terminalDelivered.await(2, TimeUnit.SECONDS))

            assertTrue(reentrantResult.get())
            assertEquals(
                listOf(
                    OnDeviceAiProviderSessionEvent.Started::class.java,
                    OnDeviceAiProviderSessionEvent.Chunk::class.java,
                    OnDeviceAiProviderSessionEvent.Cancelled::class.java,
                ),
                events.map { it.javaClass },
            )
            assertEquals(
                listOf(OnDeviceAiLimits.INITIAL_CHUNK_CREDITS),
                remote.creditGrants,
            )
            assertEquals(1, remote.cancelCount.get())
            assertEquals(1, remote.closeCount.get())
        } finally {
            terminalLane.shutdownNow()
            dataLane.shutdownNow()
        }
    }

    @Test
    fun timeoutTearsDownRemoteWhileCompletionReadIsBlockedAndSuppressesCompletion() {
        val dataLane = Executors.newSingleThreadExecutor()
        val terminalLane = Executors.newSingleThreadExecutor()
        val timeoutCaller = Executors.newSingleThreadExecutor()
        val startedDelivered = CountDownLatch(1)
        val readEntered = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val remoteClosed = CountDownLatch(1)
        val terminalDelivered = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<OnDeviceAiProviderSessionEvent>())
        val request = request(stream = false)
        val policy = OnDeviceAiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = RecordingRequestLease(0),
            serializedControlExecutor = dataLane,
            terminalFallbackExecutor = terminalLane,
            options = OnDeviceAiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            when (event) {
                is OnDeviceAiProviderSessionEvent.Started -> startedDelivered.countDown()
                OnDeviceAiProviderSessionEvent.TimedOut -> terminalDelivered.countDown()
                else -> Unit
            }
        }
        try {
            val dispatch = policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
            dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
            val remote = RecordingRemote(onClose = { remoteClosed.countDown() })
            policy.commitOpenSucceeded(remote)
            assertTrue(startedDelivered.await(2, TimeUnit.SECONDS))

            val owned = BlockingDescriptorLease(readEntered, releaseRead, "ok".toByteArray())
            val incoming = RecordingIncomingDescriptors(1, owned)
            dispatch.callback.onCompleted(
                PROVIDER_UID,
                completionEnvelope(descriptorTextPayload(index = 0, length = 2)),
                incoming,
            )
            assertTrue(readEntered.await(2, TimeUnit.SECONDS))

            assertTrue(timeoutCaller.submit<Boolean> { policy.timeout() }.get(2, TimeUnit.SECONDS))
            assertTrue(remoteClosed.await(2, TimeUnit.SECONDS))
            assertFalse(terminalDelivered.await(100, TimeUnit.MILLISECONDS))
            assertEquals(1, remote.cancelCount.get())
            assertEquals(1, remote.closeCount.get())

            releaseRead.countDown()
            assertTrue(terminalDelivered.await(2, TimeUnit.SECONDS))
            assertEquals(
                listOf(
                    OnDeviceAiProviderSessionEvent.Started::class.java,
                    OnDeviceAiProviderSessionEvent.TimedOut::class.java,
                ),
                events.map { it.javaClass },
            )
            assertEquals(1, incoming.closeCount.get())
            assertEquals(1, owned.readCount.get())
            assertEquals(1, owned.closeCount.get())
        } finally {
            releaseRead.countDown()
            timeoutCaller.shutdownNow()
            terminalLane.shutdownNow()
            dataLane.shutdownNow()
        }
    }

    @Test
    fun terminalWaitsForDescriptorCloseAfterPendingIsDetached() {
        val controlLane = ManualExecutor()
        val terminalLane = ManualExecutor()
        val terminalWorker = Executors.newSingleThreadExecutor()
        val controlWorker = Executors.newSingleThreadExecutor()
        val descriptorCloseEntered = CountDownLatch(1)
        val releaseDescriptorClose = CountDownLatch(1)
        val remoteClosed = CountDownLatch(1)
        val terminalDelivered = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<OnDeviceAiProviderSessionEvent>())
        val request = request(stream = false)
        val policy = OnDeviceAiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = RecordingRequestLease(0),
            serializedControlExecutor = controlLane,
            terminalFallbackExecutor = terminalLane,
            options = OnDeviceAiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            if (event == OnDeviceAiProviderSessionEvent.Cancelled) terminalDelivered.countDown()
        }
        try {
            val dispatch = policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
            dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
            val remote = RecordingRemote(onClose = { remoteClosed.countDown() })
            policy.commitOpenSucceeded(remote)
            controlLane.drain()
            assertTrue(events.single() is OnDeviceAiProviderSessionEvent.Started)
            events.clear()

            val owned = BlockingCloseDescriptorLease(
                entered = descriptorCloseEntered,
                release = releaseDescriptorClose,
            )
            val incoming = RecordingIncomingDescriptors(0, owned)
            dispatch.callback.onCompleted(
                PROVIDER_UID,
                completionEnvelope(textPayload("discarded")),
                incoming,
            )
            assertTrue(policy.cancel())

            val terminalDrain = terminalWorker.submit<Int> { terminalLane.drain() }
            assertTrue(descriptorCloseEntered.await(2, TimeUnit.SECONDS))
            assertTrue("Remote teardown must precede descriptor close", remoteClosed.await(0, TimeUnit.MILLISECONDS))
            assertEquals(1, remote.cancelCount.get())
            assertEquals(1, remote.closeCount.get())

            controlWorker.submit<Int> { controlLane.drain() }.get(2, TimeUnit.SECONDS)
            assertFalse(terminalDelivered.await(100, TimeUnit.MILLISECONDS))
            assertTrue(events.isEmpty())

            releaseDescriptorClose.countDown()
            terminalDrain.get(2, TimeUnit.SECONDS)
            controlLane.drain()
            terminalLane.drain()
            assertTrue(terminalDelivered.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(OnDeviceAiProviderSessionEvent.Cancelled), events)
            assertEquals(1, incoming.closeCount.get())
            assertEquals(1, owned.closeCount.get())
        } finally {
            releaseDescriptorClose.countDown()
            controlWorker.shutdownNow()
            terminalWorker.shutdownNow()
        }
    }

    @Test
    fun terminalWaitsForDetachedLateRemoteCloseToReturn() {
        val controlLane = ManualExecutor()
        val terminalLane = ManualExecutor()
        val terminalWorker = Executors.newSingleThreadExecutor()
        val controlWorker = Executors.newSingleThreadExecutor()
        val lateCloseEntered = CountDownLatch(1)
        val releaseLateClose = CountDownLatch(1)
        val terminalDelivered = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<OnDeviceAiProviderSessionEvent>())
        val request = request(stream = false)
        val policy = OnDeviceAiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = RecordingRequestLease(0),
            serializedControlExecutor = controlLane,
            terminalFallbackExecutor = terminalLane,
            options = OnDeviceAiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            if (event == OnDeviceAiProviderSessionEvent.Cancelled) terminalDelivered.countDown()
        }
        try {
            val dispatch = policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
            dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
            val activeRemote = RecordingRemote()
            policy.commitOpenSucceeded(activeRemote)
            controlLane.drain()
            assertTrue(events.single() is OnDeviceAiProviderSessionEvent.Started)
            events.clear()

            assertTrue(policy.cancel())
            val lateRemote = BlockingCloseRemote(lateCloseEntered, releaseLateClose)
            policy.commitOpenSucceeded(lateRemote)
            val terminalDrain = terminalWorker.submit<Int> { terminalLane.drain() }
            assertTrue(lateCloseEntered.await(2, TimeUnit.SECONDS))
            assertEquals(1, lateRemote.cancelCount.get())

            controlWorker.submit<Int> { controlLane.drain() }.get(2, TimeUnit.SECONDS)
            assertFalse(terminalDelivered.await(100, TimeUnit.MILLISECONDS))
            assertTrue(events.isEmpty())

            releaseLateClose.countDown()
            terminalDrain.get(2, TimeUnit.SECONDS)
            controlLane.drain()
            terminalLane.drain()
            assertTrue(terminalDelivered.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(OnDeviceAiProviderSessionEvent.Cancelled), events)
            assertEquals(1, lateRemote.cancelCount.get())
            assertEquals(1, lateRemote.closeCount.get())
            assertEquals(1, activeRemote.cancelCount.get())
            assertEquals(1, activeRemote.closeCount.get())
        } finally {
            releaseLateClose.countDown()
            controlWorker.shutdownNow()
            terminalWorker.shutdownNow()
        }
    }

    @Test
    fun terminalClaimAtomicallyAccountsForReentrantLateRemoteQueue() {
        val controlLane = ManualExecutor()
        val terminalLane = ManualExecutor()
        val events = mutableListOf<OnDeviceAiProviderSessionEvent>()
        val order = mutableListOf<String>()
        val beforeClaimRemote = RecordingRemote(
            onCancel = { order += "before.cancel" },
            onClose = { order += "before.close" },
        )
        val afterClaimRemote = RecordingRemote(
            onCancel = { order += "after.cancel" },
            onClose = { order += "after.close" },
        )
        val request = request(stream = false)
        lateinit var policy: OnDeviceAiProviderSessionPolicy
        policy = OnDeviceAiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = RecordingRequestLease(0),
            serializedControlExecutor = controlLane,
            terminalFallbackExecutor = terminalLane,
            options = OnDeviceAiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            if (event == OnDeviceAiProviderSessionEvent.Cancelled) {
                order += "listener.Cancelled"
                policy.commitOpenSucceeded(afterClaimRemote)
            }
        }
        val dispatch = policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
        dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
        val activeRemote = RecordingRemote(
            onCancel = { order += "active.cancel" },
            onClose = {
                order += "active.close"
                policy.commitOpenSucceeded(beforeClaimRemote)
            },
        )
        policy.commitOpenSucceeded(activeRemote)
        controlLane.drain()
        assertTrue(events.single() is OnDeviceAiProviderSessionEvent.Started)
        events.clear()

        assertTrue(policy.cancel())
        terminalLane.drain()
        controlLane.drain()
        terminalLane.drain()

        assertEquals(listOf(OnDeviceAiProviderSessionEvent.Cancelled), events)
        val listenerIndex = order.indexOf("listener.Cancelled")
        assertTrue(listenerIndex >= 0)
        assertTrue(order.indexOf("before.cancel") in 0 until listenerIndex)
        assertTrue(order.indexOf("before.close") in 0 until listenerIndex)
        assertTrue(order.indexOf("after.cancel") > listenerIndex)
        assertTrue(order.indexOf("after.close") > listenerIndex)
        assertEquals(1, beforeClaimRemote.cancelCount.get())
        assertEquals(1, beforeClaimRemote.closeCount.get())
        assertEquals(1, afterClaimRemote.cancelCount.get())
        assertEquals(1, afterClaimRemote.closeCount.get())
    }

    @Test
    fun terminalWaitsForRequestLeaseCloseAfterOpenResolution() {
        val controlLane = ManualExecutor()
        val terminalLane = ManualExecutor()
        val terminalWorker = Executors.newSingleThreadExecutor()
        val commitWorker = Executors.newSingleThreadExecutor()
        val requestCloseEntered = CountDownLatch(1)
        val releaseRequestClose = CountDownLatch(1)
        val remoteClosed = CountDownLatch(1)
        val terminalDelivered = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<OnDeviceAiProviderSessionEvent>())
        val request = request(stream = false)
        val requestLease = BlockingRequestLease(requestCloseEntered, releaseRequestClose)
        val policy = OnDeviceAiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = requestLease,
            serializedControlExecutor = controlLane,
            terminalFallbackExecutor = terminalLane,
            options = OnDeviceAiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            if (event == OnDeviceAiProviderSessionEvent.Cancelled) terminalDelivered.countDown()
        }
        try {
            policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
            assertTrue(policy.cancel())
            val remote = RecordingRemote(onClose = { remoteClosed.countDown() })
            val commit = commitWorker.submit { policy.commitOpenSucceeded(remote) }
            assertTrue(requestCloseEntered.await(2, TimeUnit.SECONDS))

            val terminalDrain = terminalWorker.submit<Int> { terminalLane.drain() }
            assertTrue(remoteClosed.await(2, TimeUnit.SECONDS))
            assertEquals(1, remote.cancelCount.get())
            assertEquals(1, remote.closeCount.get())
            assertFalse(terminalDelivered.await(100, TimeUnit.MILLISECONDS))
            assertTrue(events.isEmpty())

            releaseRequestClose.countDown()
            commit.get(2, TimeUnit.SECONDS)
            terminalDrain.get(2, TimeUnit.SECONDS)
            controlLane.drain()
            terminalLane.drain()
            assertTrue(terminalDelivered.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(OnDeviceAiProviderSessionEvent.Cancelled), events)
            assertEquals(1, requestLease.closeCount.get())
        } finally {
            releaseRequestClose.countDown()
            commitWorker.shutdownNow()
            terminalWorker.shutdownNow()
        }
    }

    @Test
    fun terminalWaitsForIncomingCloseWhenDescriptorCopyThrows() {
        val controlLane = ManualExecutor()
        val terminalLane = ManualExecutor()
        val callbackWorker = Executors.newSingleThreadExecutor()
        val terminalWorker = Executors.newSingleThreadExecutor()
        val controlWorker = Executors.newSingleThreadExecutor()
        val incomingCloseEntered = CountDownLatch(1)
        val releaseIncomingClose = CountDownLatch(1)
        val terminalDelivered = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<OnDeviceAiProviderSessionEvent>())
        val request = request(stream = false)
        val policy = OnDeviceAiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = RecordingRequestLease(0),
            serializedControlExecutor = controlLane,
            terminalFallbackExecutor = terminalLane,
            options = OnDeviceAiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            if (event is OnDeviceAiProviderSessionEvent.Rejected) terminalDelivered.countDown()
        }
        try {
            val dispatch = policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
            dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
            val remote = RecordingRemote()
            policy.commitOpenSucceeded(remote)
            controlLane.drain()
            assertTrue(events.single() is OnDeviceAiProviderSessionEvent.Started)
            events.clear()

            val incoming = ThrowingCopyBlockingCloseIncomingDescriptors(
                entered = incomingCloseEntered,
                release = releaseIncomingClose,
            )
            val callback = callbackWorker.submit {
                dispatch.callback.onCompleted(
                    PROVIDER_UID,
                    completionEnvelope(textPayload("discarded")),
                    incoming,
                )
            }
            assertTrue(incomingCloseEntered.await(2, TimeUnit.SECONDS))

            terminalWorker.submit<Int> { terminalLane.drain() }.get(2, TimeUnit.SECONDS)
            controlWorker.submit<Int> { controlLane.drain() }.get(2, TimeUnit.SECONDS)
            assertFalse(terminalDelivered.await(100, TimeUnit.MILLISECONDS))
            assertTrue(events.isEmpty())

            releaseIncomingClose.countDown()
            callback.get(2, TimeUnit.SECONDS)
            controlLane.drain()
            terminalLane.drain()
            assertTrue(terminalDelivered.await(2, TimeUnit.SECONDS))
            assertEquals(1, events.size)
            assertEquals(
                OnDeviceAiProviderSessionEvent.Rejected(
                    OnDeviceAiProviderSessionFailure.CALLBACK_DESCRIPTOR_COUNT_INVALID,
                ),
                events.single(),
            )
            assertEquals(1, incoming.copyCount.get())
            assertEquals(1, incoming.closeCount.get())
            assertEquals(1, remote.cancelCount.get())
            assertEquals(1, remote.closeCount.get())
        } finally {
            releaseIncomingClose.countDown()
            controlWorker.shutdownNow()
            terminalWorker.shutdownNow()
            callbackWorker.shutdownNow()
        }
    }

    @Test
    fun terminalWaitsUntilBlockingDescriptorCopySettlesAndOwnedLeaseCloses() {
        val active = activeHarness(stream = false)
        active.harness.events.clear()
        val copyEntered = CountDownLatch(1)
        val releaseCopy = CountDownLatch(1)
        val owned = RecordingDescriptorLease(0)
        val incoming = BlockingIncomingDescriptors(owned, copyEntered, releaseCopy)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val callback = executor.submit {
                active.dispatch.callback.onCompleted(
                    PROVIDER_UID,
                    completionEnvelope(textPayload("ignored")),
                    incoming,
                )
            }
            assertTrue(copyEntered.await(2, TimeUnit.SECONDS))

            assertTrue(active.harness.policy.cancel())
            active.harness.fallback.drain()
            active.harness.lane.drain()
            assertTrue(active.harness.events.isEmpty())
            assertEquals(1, active.remote.cancelCount.get())
            assertEquals(1, active.remote.closeCount.get())
            assertEquals(0, owned.closeCount.get())

            releaseCopy.countDown()
            callback.get(2, TimeUnit.SECONDS)
            active.harness.drain()

            assertEquals(listOf(OnDeviceAiProviderSessionEvent.Cancelled), active.harness.events)
            assertEquals(1, incoming.closeCount.get())
            assertEquals(1, owned.closeCount.get())
            assertEquals(0, owned.readCount.get())
            assertEquals(1, active.remote.cancelCount.get())
            assertEquals(1, active.remote.closeCount.get())
        } finally {
            releaseCopy.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun duplicateCommitOfActiveRemoteIsIgnoredWhileDifferentLateRemoteIsClosed() {
        val lane = ManualExecutor()
        val harness = harness(lane = lane)
        harness.policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
        val active = RecordingRemote()

        harness.policy.commitOpenSucceeded(active)
        harness.policy.commitOpenSucceeded(active)
        harness.drain()
        assertEquals(0, active.cancelCount.get())
        assertEquals(0, active.closeCount.get())

        val late = RecordingRemote()
        harness.policy.commitOpenSucceeded(late)
        harness.drain()
        assertEquals(1, late.cancelCount.get())
        assertEquals(1, late.closeCount.get())
        assertEquals(0, active.closeCount.get())

        assertTrue(harness.policy.cancel())
        harness.drain()
        assertEquals(1, active.cancelCount.get())
        assertEquals(1, active.closeCount.get())
        assertEquals(1, harness.requestLease.closeCount.get())
    }

    @Test
    fun completionAndExternalTerminalsHaveOneWinnerAndLateFdCallbacksOnlyCloseIncoming() {
        val completedFirst = activeHarness(stream = false)
        val completionOwned = RecordingDescriptorLease(0)
        completedFirst.dispatch.callback.onCompleted(
            PROVIDER_UID,
            completionEnvelope(textPayload("ok")),
            RecordingIncomingDescriptors(0, completionOwned),
        )
        completedFirst.harness.drain()
        assertTrue(completedFirst.harness.events.last() is OnDeviceAiProviderSessionEvent.Completed)
        assertFalse(completedFirst.harness.policy.cancel())
        assertFalse(completedFirst.harness.policy.timeout())
        assertFalse(completedFirst.harness.policy.binderDied())

        val eventCount = completedFirst.harness.events.size
        val lateOwned = RecordingDescriptorLease(0)
        val lateIncoming = RecordingIncomingDescriptors(0, lateOwned)
        completedFirst.dispatch.callback.onCompleted(
            PROVIDER_UID,
            completionEnvelope(textPayload("late")),
            lateIncoming,
        )
        completedFirst.harness.drain()
        assertEquals(eventCount, completedFirst.harness.events.size)
        assertEquals(1, lateIncoming.closeCount.get())
        assertEquals(0, lateIncoming.copyCount.get())
        assertEquals(0, lateOwned.closeCount.get())

        val cancelFirst = activeHarness(stream = false)
        cancelFirst.harness.events.clear()
        val discardedOwned = RecordingDescriptorLease(0)
        cancelFirst.dispatch.callback.onCompleted(
            PROVIDER_UID,
            completionEnvelope(textPayload("discarded")),
            RecordingIncomingDescriptors(0, discardedOwned),
        )
        assertTrue(cancelFirst.harness.policy.cancel())
        cancelFirst.harness.drain()
        assertEquals(listOf(OnDeviceAiProviderSessionEvent.Cancelled), cancelFirst.harness.events)
        assertEquals(1, discardedOwned.closeCount.get())
        assertEquals(0, discardedOwned.readCount.get())
        assertEquals(1, cancelFirst.remote.cancelCount.get())
        assertEquals(1, cancelFirst.remote.closeCount.get())
    }

    @Test
    fun requestDescriptorLeaseClosesExactlyOnceAcrossRepeatedResolutionAndClose() {
        val lane = ManualExecutor()
        val requestLease = RecordingRequestLease(1)
        val request = request(
            payload = descriptorTextPayload(index = 0, length = 5),
        )
        val harness = harness(
            request = request,
            requestLease = requestLease,
            lane = lane,
        )
        val dispatch = harness.policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
        assertEquals(1, dispatch.descriptorCount)
        assertSame(requestLease, dispatch.requestDescriptors)
        assertEquals(0, requestLease.closeCount.get())

        harness.policy.commitOpenFailed()
        harness.policy.commitOpenFailed()
        harness.policy.close()
        harness.drain()

        assertEquals(1, requestLease.closeCount.get())
        assertRejected(harness, OnDeviceAiProviderSessionFailure.OPEN_FAILED)
    }

    private fun assertTypedCallbackFailure(
        kind: TypedCallback,
        envelope: ByteArray?,
        expected: OnDeviceAiProviderSessionFailure,
    ) {
        val stream = kind == TypedCallback.CHUNK
        val active = if (kind == TypedCallback.STARTED) null else activeHarness(stream)
        val harness = active?.harness ?: harness(request = request(stream = stream))
        val dispatch = active?.dispatch ?: (
            harness.policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
            )
        val owned = RecordingDescriptorLease(0)
        val incoming = if (kind.needsDescriptors) RecordingIncomingDescriptors(0, owned) else null

        kind.invoke(dispatch.callback, envelope, incoming)
        if (active == null) harness.policy.commitOpenSucceeded(RecordingRemote())
        harness.drain()

        assertRejected(harness, expected)
        if (incoming != null) {
            assertEquals(1, incoming.closeCount.get())
            assertEquals(
                if (expected == OnDeviceAiProviderSessionFailure.CALLBACK_MALFORMED) 1 else 0,
                incoming.copyCount.get(),
            )
            assertEquals(
                if (expected == OnDeviceAiProviderSessionFailure.CALLBACK_MALFORMED) 1 else 0,
                owned.closeCount.get(),
            )
        }
    }

    private fun activeHarness(stream: Boolean): ActiveHarness {
        val lane = ManualExecutor()
        val request = request(stream = stream)
        val harness = harness(request = request, lane = lane)
        val dispatch = harness.policy.beginOpen() as OnDeviceAiProviderSessionOpenDecision.Dispatch
        dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
        val remote = RecordingRemote()
        harness.policy.commitOpenSucceeded(remote)
        harness.drain()
        assertTrue(harness.events.single() is OnDeviceAiProviderSessionEvent.Started)
        return ActiveHarness(harness, dispatch, remote)
    }

    private fun harness(
        request: OnDeviceAiRequest = request(),
        plan: OnDeviceAiHostSessionPlan = plan(),
        requestLease: RecordingRequestLease = RecordingRequestLease(requestDescriptorCount(request)),
        lane: ManualExecutor = ManualExecutor(),
        fallback: ManualExecutor = ManualExecutor(),
        options: OnDeviceAiProviderSessionOptions = OnDeviceAiProviderSessionOptions(enabled = true),
        onEvent: (OnDeviceAiProviderSessionEvent) -> Unit = {},
    ): Harness {
        val events = Collections.synchronizedList(mutableListOf<OnDeviceAiProviderSessionEvent>())
        val policy = OnDeviceAiProviderSessionPolicy(
            plan = plan,
            request = request,
            requestDescriptors = requestLease,
            serializedControlExecutor = lane,
            terminalFallbackExecutor = fallback,
            options = options,
        ) { event ->
            events += event
            onEvent(event)
        }
        return Harness(plan, request, requestLease, lane, fallback, events, policy)
    }

    private fun plan(): OnDeviceAiHostSessionPlan {
        val selected = OnDeviceAiProviderSelectionPolicy.select(
            candidates = listOf(OnDeviceAiHostTestFixtures.candidate()),
            environment = OnDeviceAiHostTestFixtures.environment(),
            hostCapabilities = OnDeviceAiHostTestFixtures.capabilities(),
            requirement = OnDeviceAiCapabilityRequirement(),
            options = OnDeviceAiProviderSelectionOptions(enabled = true),
        ) as OnDeviceAiProviderSelectionResult.Selected
        return OnDeviceAiHostSessionPlan(
            pinnedProvider = selected.pinned,
            listingGeneration = "fixture-generation",
            modelId = MODEL_ID,
            modelCapabilityIds = setOf(
                OnDeviceAiCapabilityId.STREAMING,
                OnDeviceAiCapabilityId.REASONING,
                OnDeviceAiCapabilityId.TOOLS,
                OnDeviceAiCapabilityId.STRUCTURED_JSON,
                OnDeviceAiCapabilityId.USAGE,
            ),
            effectiveMaximumContextBytes = 512L * 1024L,
            effectiveMaximumOutputBytes = 256L * 1024L,
            credentialDisposition = OnDeviceAiCredentialIsolationPolicy.disposition(selected.pinned),
        )
    }

    private fun request(
        stream: Boolean = false,
        reportUsage: Boolean = false,
        payload: AiPayloadReference = textPayload("hello"),
    ) = OnDeviceAiRequest(
        requestId = "request-session",
        protocolVersion = AiProtocolVersion(1, 0),
        providerId = PROVIDER_ID,
        modelId = MODEL_ID,
        messages = listOf(
            AiMessage(
                role = AiMessageRole.USER,
                parts = listOf(AiContentPart(payload)),
            ),
        ),
        options = AiGenerationOptions(
            stream = stream,
            includeReasoning = false,
            structuredJson = false,
            reportUsage = reportUsage,
            maximumOutputBytes = 4_096,
            maximumToolRounds = 0,
            timeoutMillis = 30_000,
        ),
    )

    private fun startedEnvelope(request: OnDeviceAiRequest): ByteArray = OnDeviceAiCodec.encodeSessionStarted(
        AiSessionStarted(
            requestId = request.requestId,
            sessionId = "fixture-session",
            protocolVersion = request.protocolVersion,
            providerId = request.providerId,
            modelId = request.modelId,
            firstChunkSequence = 0,
            effectiveMaximumOutputBytes = request.options.maximumOutputBytes,
            effectiveMaximumToolRounds = 0,
        ),
    )

    private fun completionEnvelope(
        output: AiPayloadReference,
        usage: AiUsage? = null,
    ): ByteArray = OnDeviceAiCodec.encodeCompletionResult(
        AiCompletionResult(
            output = output,
            finishReason = AiFinishReason.STOP,
            usage = usage,
        ),
    )

    private fun textPayload(value: String): AiPayloadReference {
        val bytes = value.toByteArray()
        return AiPayloadReference(
            mimeType = OnDeviceAiMimeType.PLAIN,
            declaredLengthBytes = bytes.size.toLong(),
            inlineBytes = bytes,
            charset = "utf-8",
        )
    }

    private fun jsonPayload(value: String): AiPayloadReference {
        val bytes = value.toByteArray()
        return AiPayloadReference(
            mimeType = OnDeviceAiMimeType.JSON,
            declaredLengthBytes = bytes.size.toLong(),
            inlineBytes = bytes,
            charset = "utf-8",
        )
    }

    private fun descriptorTextPayload(index: Int, length: Long) = AiPayloadReference(
        mimeType = OnDeviceAiMimeType.PLAIN,
        declaredLengthBytes = length,
        descriptorIndex = index,
        charset = "utf-8",
    )

    private fun descriptorJsonPayload(index: Int, length: Long) = AiPayloadReference(
        mimeType = OnDeviceAiMimeType.JSON,
        declaredLengthBytes = length,
        descriptorIndex = index,
        charset = "utf-8",
    )

    private fun requestDescriptorCount(request: OnDeviceAiRequest): Int = request.messages
        .flatMap(AiMessage::parts)
        .mapNotNull { it.payload.descriptorIndex }
        .size

    private fun assertRejected(harness: Harness, failure: OnDeviceAiProviderSessionFailure) {
        assertEquals(
            OnDeviceAiProviderSessionEvent.Rejected(failure),
            harness.events.lastOrNull(),
        )
    }

    private fun assertFatalFuture(future: java.util.concurrent.Future<*>) {
        val failure = runCatching { future.get(2, TimeUnit.SECONDS) }.exceptionOrNull()
        assertFatalFailure(failure)
    }

    private fun assertFatalFailure(failure: Throwable?) {
        val causes = generateSequence(failure) { it.cause }.toList()
        assertTrue(
            causes.any {
                it is IllegalStateException &&
                    it.message.orEmpty().contains("terminalFallbackExecutor")
            },
        )
        assertFalse(causes.any { it is StackOverflowError })
    }

    private data class Harness(
        val plan: OnDeviceAiHostSessionPlan,
        val request: OnDeviceAiRequest,
        val requestLease: RecordingRequestLease,
        val lane: ManualExecutor,
        val fallback: ManualExecutor,
        val events: MutableList<OnDeviceAiProviderSessionEvent>,
        val policy: OnDeviceAiProviderSessionPolicy,
    ) {
        fun drain() {
            var totalExecutions = 0
            while (true) {
                val executions = lane.drain() + if (fallback === lane) 0 else fallback.drain()
                if (executions == 0) return
                totalExecutions += executions
                check(totalExecutions < 1_000) { "Session executors did not become idle" }
            }
        }
    }

    private data class ActiveHarness(
        val harness: Harness,
        val dispatch: OnDeviceAiProviderSessionOpenDecision.Dispatch,
        val remote: RecordingRemote,
    )

    private enum class TypedCallback(val needsDescriptors: Boolean) {
        STARTED(false),
        CHUNK(false),
        USAGE(false),
        COMPLETED(true),
        FAILED(false),
        TOOL_CALLS(true),
        ;

        fun invoke(
            callback: OnDeviceAiProviderSessionCallbackIngress,
            envelope: ByteArray?,
            descriptors: OnDeviceAiProviderSessionIncomingDescriptors?,
        ) = when (this) {
            STARTED -> callback.onStarted(PROVIDER_UID, envelope)
            CHUNK -> callback.onChunk(PROVIDER_UID, envelope)
            USAGE -> callback.onUsage(PROVIDER_UID, envelope)
            COMPLETED -> callback.onCompleted(PROVIDER_UID, envelope, descriptors)
            FAILED -> callback.onFailed(PROVIDER_UID, envelope)
            TOOL_CALLS -> callback.onToolCalls(PROVIDER_UID, envelope, descriptors)
        }
    }

    private class ManualExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            synchronized(tasks) { tasks.addLast(command) }
        }

        fun drain(): Int {
            var executions = 0
            while (true) {
                val task = synchronized(tasks) { tasks.pollFirst() } ?: return executions
                task.run()
                executions += 1
                check(executions < 1_000) { "Manual executor did not become idle" }
            }
        }
    }

    private object RejectingExecutor : Executor {
        override fun execute(command: Runnable) {
            throw RejectedExecutionException("rejected for test")
        }
    }

    private class BlockingRejectingExecutor(
        private val entered: CountDownLatch,
        private val release: CountDownLatch,
    ) : Executor {
        override fun execute(command: Runnable) {
            entered.countDown()
            check(release.await(2, TimeUnit.SECONDS))
            throw RejectedExecutionException("delayed rejection for test")
        }
    }

    private class ArmedBlockingRejectingExecutor(
        private val accepted: Executor,
        private val entered: CountDownLatch,
        private val release: CountDownLatch,
    ) : Executor {
        private val armed = AtomicBoolean(false)

        fun arm() {
            armed.set(true)
        }

        override fun execute(command: Runnable) {
            if (!armed.get()) {
                accepted.execute(command)
                return
            }
            entered.countDown()
            check(release.await(2, TimeUnit.SECONDS))
            throw RejectedExecutionException("armed rejection for test")
        }
    }

    private class RecordingRequestLease(
        count: Int,
        private val onClose: () -> Unit = {},
    ) : OnDeviceAiProviderSessionRequestDescriptorLease {
        var mutableCount: Int = count
        override val count: Int
            get() = mutableCount
        val closeCount = AtomicInteger()

        override fun close() {
            closeCount.incrementAndGet()
            onClose()
        }
    }

    private class RecordingDescriptorLease(
        override val count: Int,
        private val reader: (Int, Long) -> ByteArray = { _, _ -> error("Unexpected descriptor read") },
    ) : OnDeviceAiProviderSessionDescriptorLease {
        val readCount = AtomicInteger()
        val closeCount = AtomicInteger()

        override fun readDeclaredBytes(index: Int, declaredLengthBytes: Long): ByteArray {
            readCount.incrementAndGet()
            return reader(index, declaredLengthBytes)
        }

        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    private open class RecordingIncomingDescriptors(
        override val count: Int,
        private val lease: OnDeviceAiProviderSessionDescriptorLease,
        private val failCopy: Boolean = false,
    ) : OnDeviceAiProviderSessionIncomingDescriptors {
        val copyCount = AtomicInteger()
        val closeCount = AtomicInteger()

        override fun copyForAsync(): OnDeviceAiProviderSessionDescriptorLease {
            copyCount.incrementAndGet()
            if (failCopy) error("copy failed for test")
            return lease
        }

        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    private class BlockingIncomingDescriptors(
        private val lease: OnDeviceAiProviderSessionDescriptorLease,
        private val entered: CountDownLatch,
        private val release: CountDownLatch,
    ) : OnDeviceAiProviderSessionIncomingDescriptors {
        override val count: Int = lease.count
        val closeCount = AtomicInteger()

        override fun copyForAsync(): OnDeviceAiProviderSessionDescriptorLease {
            entered.countDown()
            check(release.await(2, TimeUnit.SECONDS))
            return lease
        }

        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    private class ThrowingCopyBlockingCloseIncomingDescriptors(
        private val entered: CountDownLatch,
        private val release: CountDownLatch,
    ) : OnDeviceAiProviderSessionIncomingDescriptors {
        override val count: Int = 0
        val copyCount = AtomicInteger()
        val closeCount = AtomicInteger()

        override fun copyForAsync(): OnDeviceAiProviderSessionDescriptorLease {
            copyCount.incrementAndGet()
            throw IllegalStateException("copy failed for test")
        }

        override fun close() {
            closeCount.incrementAndGet()
            entered.countDown()
            check(release.await(2, TimeUnit.SECONDS))
        }
    }

    private class BlockingDescriptorLease(
        private val entered: CountDownLatch,
        private val release: CountDownLatch,
        private val bytes: ByteArray,
    ) : OnDeviceAiProviderSessionDescriptorLease {
        override val count: Int = 1
        val readCount = AtomicInteger()
        val closeCount = AtomicInteger()

        override fun readDeclaredBytes(index: Int, declaredLengthBytes: Long): ByteArray {
            readCount.incrementAndGet()
            entered.countDown()
            check(release.await(2, TimeUnit.SECONDS))
            return bytes.copyOf()
        }

        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    private class BlockingCloseDescriptorLease(
        private val entered: CountDownLatch,
        private val release: CountDownLatch,
    ) : OnDeviceAiProviderSessionDescriptorLease {
        override val count: Int = 0
        val closeCount = AtomicInteger()

        override fun readDeclaredBytes(index: Int, declaredLengthBytes: Long): ByteArray =
            error("Unexpected descriptor read")

        override fun close() {
            closeCount.incrementAndGet()
            entered.countDown()
            check(release.await(2, TimeUnit.SECONDS))
        }
    }

    private class BlockingRequestLease(
        private val entered: CountDownLatch,
        private val release: CountDownLatch,
    ) : OnDeviceAiProviderSessionRequestDescriptorLease {
        override val count: Int = 0
        val closeCount = AtomicInteger()

        override fun close() {
            closeCount.incrementAndGet()
            entered.countDown()
            check(release.await(2, TimeUnit.SECONDS))
        }
    }

    private class BlockingCloseRemote(
        private val closeEntered: CountDownLatch,
        private val releaseClose: CountDownLatch,
        private val onCancel: () -> Unit = {},
        private val onClose: () -> Unit = {},
    ) : OnDeviceAiProviderSessionRemote {
        val cancelCount = AtomicInteger()
        val closeCount = AtomicInteger()

        override fun grantCredits(count: Int) = error("Late remote must not receive credits")

        override fun cancel() {
            cancelCount.incrementAndGet()
            onCancel()
        }

        override fun close() {
            closeCount.incrementAndGet()
            onClose()
            closeEntered.countDown()
            check(releaseClose.await(2, TimeUnit.SECONDS))
        }
    }

    private class RecordingRemote(
        private val onGrant: (Int) -> Unit = {},
        private val onCancel: () -> Unit = {},
        private val onClose: () -> Unit = {},
    ) : OnDeviceAiProviderSessionRemote {
        val creditGrants: MutableList<Int> = Collections.synchronizedList(mutableListOf())
        val cancelCount = AtomicInteger()
        val closeCount = AtomicInteger()

        override fun grantCredits(count: Int) {
            creditGrants += count
            onGrant(count)
        }

        override fun cancel() {
            cancelCount.incrementAndGet()
            onCancel()
        }

        override fun close() {
            closeCount.incrementAndGet()
            onClose()
        }
    }

    private companion object {
        const val PROVIDER_UID = 10_001
        const val PROVIDER_ID = "fixture.provider"
        const val MODEL_ID = "fixture.model"
    }
}
