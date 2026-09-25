package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiError
import org.autojs.plugin.ai.common.api.AiErrorCode
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiUsage
import org.autojs.plugin.ai.provider.api.AiCompletionResult
import org.autojs.plugin.ai.provider.api.AiContentPart
import org.autojs.plugin.ai.provider.api.AiFinishReason
import org.autojs.plugin.ai.provider.api.AiGenerationOptions
import org.autojs.plugin.ai.provider.api.AiMessage
import org.autojs.plugin.ai.provider.api.AiMessageRole
import org.autojs.plugin.ai.provider.api.AiSessionStarted
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiProviderChunk
import org.autojs.plugin.ai.provider.api.AiProviderCodec
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import org.autojs.plugin.ai.provider.api.AiProviderRequest
import org.autojs.plugin.ai.provider.api.AiToolCall
import org.autojs.plugin.ai.provider.api.AiToolCallBatch
import org.autojs.plugin.ai.provider.api.AiToolDefinition
import org.autojs.plugin.ai.provider.api.AiToolResult
import org.autojs.plugin.ai.provider.api.AiToolResultBatch
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

class AiProviderSessionTest {

    @Test
    fun callbackBufferCapacityCannotUndercutInitialCreditWindow() {
        val minimumCapacity = AiProviderLimits.INITIAL_CHUNK_CREDITS + 2
        val rejected = runCatching {
            AiProviderSessionOptions(
                enabled = true,
                maximumBufferedCallbacks = minimumCapacity - 1,
            )
        }.exceptionOrNull()

        assertTrue(rejected is IllegalArgumentException)
        assertEquals(
            minimumCapacity,
            AiProviderSessionOptions(
                enabled = true,
                maximumBufferedCallbacks = minimumCapacity,
            ).maximumBufferedCallbacks,
        )
    }

    @Test
    fun dataEventToStringsDoNotExposeSessionIdsOrGeneratedText() {
        val started = AiProviderSessionEvent.Started(
            AiSessionStarted(
                requestId = "secret-request",
                sessionId = "secret-session",
                protocolVersion = AiProtocolVersion(2, 0),
                providerId = PROVIDER_ID,
                targetId = TARGET_ID,
                effectiveMaximumOutputBytes = 4_096,
                effectiveMaximumToolRounds = 0,
            ),
        ).toString()
        val chunk = AiProviderSessionEvent.Chunk(
            AiProviderChunk(
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
            options = AiProviderSessionOptions(),
        )

        assertSame(AiProviderSessionOpenDecision.Disabled, harness.policy.beginOpen())
        assertEquals(1, requestLease.closeCount.get())
        assertTrue(harness.events.isEmpty())

        harness.drain()
        assertEquals(listOf(AiProviderSessionEvent.Disabled), harness.events)
        assertEquals(1, requestLease.closeCount.get())
    }

    @Test
    fun planRequestMismatchFailsBeforeDispatchAndClosesRequestLease() {
        val lane = ManualExecutor()
        val requestLease = RecordingRequestLease(0)
        val mismatched = request().copy(targetId = "fixture.provider:other")
        val harness = harness(request = mismatched, requestLease = requestLease, lane = lane)

        val decision = harness.policy.beginOpen()
        assertEquals(
            AiProviderSessionOpenDecision.Rejected(
                AiProviderSessionFailure.PLAN_REQUEST_MISMATCH,
            ),
            decision,
        )
        assertTrue(harness.events.isEmpty())
        assertEquals(1, requestLease.closeCount.get())

        harness.drain()
        assertRejected(harness, AiProviderSessionFailure.PLAN_REQUEST_MISMATCH)
    }

    @Test
    fun tokenLimitDispatchDoesNotRequireUsageModelCapability() {
        val request = request(maximumOutputTokens = 64)
        val plan = plan(
            targetCapabilityIds = setOf(
                AiProviderCapabilityId.STREAMING,
                AiProviderCapabilityId.REASONING,
                AiProviderCapabilityId.TOOLS,
                AiProviderCapabilityId.STRUCTURED_JSON,
            ),
        )
        val harness = harness(request = request, plan = plan)

        assertTrue(harness.policy.beginOpen() is AiProviderSessionOpenDecision.Dispatch)
        harness.policy.commitOpenFailed()
        harness.drain()
        assertRejected(harness, AiProviderSessionFailure.OPEN_FAILED)
    }

    @Test
    fun dispatchSnapshotsEnvelopeAndDescriptorCountWhileLeaseRemainsBorrowed() {
        val lane = ManualExecutor()
        val requestLease = RecordingRequestLease(0)
        val harness = harness(requestLease = requestLease, lane = lane)
        val dispatch = harness.policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch

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
        assertRejected(harness, AiProviderSessionFailure.OPEN_FAILED)
    }

    @Test
    fun callbacksBeforeSynchronousOpenReturnAreDefensivelyBufferedAndCommittedInOrder() {
        val lane = ManualExecutor()
        val request = request(stream = false, reportUsage = true)
        val harness = harness(request = request, lane = lane)
        val dispatch = harness.policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
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
                AiProviderSessionEvent.Started::class.java,
                AiProviderSessionEvent.Usage::class.java,
                AiProviderSessionEvent.Completed::class.java,
            ),
            harness.events.map { it.javaClass },
        )
        val completed = harness.events.last() as AiProviderSessionEvent.Completed
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
        val dispatch = harness.policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
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

        assertRejected(harness, AiProviderSessionFailure.OPEN_FAILED)
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
        val dispatch = harness.policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
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
        assertRejected(harness, AiProviderSessionFailure.CALLBACK_UID_MISMATCH)
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
            assertTypedCallbackFailure(kind, null, AiProviderSessionFailure.CALLBACK_MISSING)
            assertTypedCallbackFailure(kind, byteArrayOf(), AiProviderSessionFailure.CALLBACK_MISSING)
            val maximum = if (kind == TypedCallback.CHUNK) {
                AiProviderLimits.MAX_CHUNK_ENVELOPE_BYTES
            } else {
                AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES
            }
            assertTypedCallbackFailure(
                kind,
                ByteArray(maximum + 1),
                AiProviderSessionFailure.CALLBACK_TOO_LARGE,
            )
            assertTypedCallbackFailure(
                kind,
                byteArrayOf(1, 2, 3),
                AiProviderSessionFailure.CALLBACK_MALFORMED,
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
            AiProviderSessionFailure.CALLBACK_MISSING,
        )

        val toolHarness = activeHarness(stream = false)
        val excessive = RecordingIncomingDescriptors(
            count = toolHarness.harness.plan.pinnedProvider.negotiatedCapabilities.maximumSessionDescriptors + 1,
            lease = RecordingDescriptorLease(0),
        )
        toolHarness.dispatch.callback.onToolCalls(
            PROVIDER_UID,
            AiProviderCodec.encodeToolCallBatch(
                AiToolCallBatch(listOf(AiToolCall("call-1", "lookup", jsonPayload("{}")))),
            ),
            excessive,
        )
        toolHarness.harness.drain()
        assertRejected(
            toolHarness.harness,
            AiProviderSessionFailure.CALLBACK_DESCRIPTOR_COUNT_INVALID,
        )
        assertEquals(1, excessive.closeCount.get())
        assertEquals(0, excessive.copyCount.get())
    }

    @Test
    fun primaryExecutorRejectionUsesFallbackLaneAndCleansLateRemoteAndLeases() {
        val fallback = ManualExecutor()
        val requestLease = RecordingRequestLease(0)
        val events = Collections.synchronizedList(mutableListOf<AiProviderSessionEvent>())
        val inIngress = AtomicBoolean(false)
        val policy = AiProviderSessionPolicy(
            plan = plan(),
            request = request(),
            requestDescriptors = requestLease,
            serializedControlExecutor = RejectingExecutor,
            terminalFallbackExecutor = fallback,
            options = AiProviderSessionOptions(enabled = true),
        ) { event ->
            assertFalse("Listener ran inline on Binder ingress", inIngress.get())
            events += event
        }
        val dispatch = policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
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
                AiProviderSessionEvent.Rejected(
                    AiProviderSessionFailure.EXECUTOR_REJECTED,
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
            val terminate: (AiProviderSessionPolicy) -> Boolean,
            val event: AiProviderSessionEvent,
            val cancelsRemote: Boolean,
        )
        val cases = listOf(
            Case(AiProviderSessionPolicy::cancel, AiProviderSessionEvent.Cancelled, true),
            Case(AiProviderSessionPolicy::timeout, AiProviderSessionEvent.TimedOut, true),
            Case(AiProviderSessionPolicy::binderDied, AiProviderSessionEvent.BinderDied, false),
        )
        cases.forEach { case ->
            val executorEntered = CountDownLatch(1)
            val releaseExecutor = CountDownLatch(1)
            val callbackWorker = Executors.newSingleThreadExecutor()
            val terminalLane = ManualExecutor()
            val events = Collections.synchronizedList(mutableListOf<AiProviderSessionEvent>())
            val request = request(stream = false)
            val policy = AiProviderSessionPolicy(
                plan = plan(),
                request = request,
                requestDescriptors = RecordingRequestLease(0),
                serializedControlExecutor = BlockingRejectingExecutor(
                    entered = executorEntered,
                    release = releaseExecutor,
                ),
                terminalFallbackExecutor = terminalLane,
                options = AiProviderSessionOptions(enabled = true),
                onEvent = events::add,
            )
            try {
                val dispatch = policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
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
        val events = Collections.synchronizedList(mutableListOf<AiProviderSessionEvent>())
        val policy = AiProviderSessionPolicy(
            plan = plan(),
            request = request(),
            requestDescriptors = requestLease,
            serializedControlExecutor = RejectingExecutor,
            terminalFallbackExecutor = RejectingExecutor,
            options = AiProviderSessionOptions(enabled = true),
            onEvent = events::add,
        )
        val dispatch = policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
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
        val events = Collections.synchronizedList(mutableListOf<AiProviderSessionEvent>())
        val request = request(stream = false)
        val requestLease = RecordingRequestLease(0)
        val policy = AiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = requestLease,
            serializedControlExecutor = primaryExecutor,
            terminalFallbackExecutor = fallbackExecutor,
            options = AiProviderSessionOptions(enabled = true),
            onEvent = events::add,
        )
        try {
            val dispatch = policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
            dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
            val activeRemote = RecordingRemote(
                onCancel = { order += "active.cancel" },
                onClose = { order += "active.close" },
            )
            policy.commitOpenSucceeded(activeRemote)
            acceptedControl.drain()
            assertTrue(events.single() is AiProviderSessionEvent.Started)
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
        val dispatch = harness.policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
        dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(harness.request))
        lateinit var remote: RecordingRemote
        remote = RecordingRemote(onGrant = { count ->
            if (count == AiProviderLimits.INITIAL_CHUNK_CREDITS) {
                assertTrue(harness.events.isEmpty())
                dispatch.callback.onChunk(
                    PROVIDER_UID,
                    AiProviderCodec.encodeTextChunk(AiProviderChunk(0, textDelta = "a")),
                )
                assertTrue(harness.events.isEmpty())
            }
        })

        harness.policy.commitOpenSucceeded(remote)
        assertTrue(harness.events.isEmpty())
        harness.drain()

        assertEquals(
            listOf(
                AiProviderSessionEvent.Started::class.java,
                AiProviderSessionEvent.Chunk::class.java,
            ),
            harness.events.map { it.javaClass },
        )
        assertEquals(
            listOf(AiProviderLimits.INITIAL_CHUNK_CREDITS, 1),
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
            options = AiProviderSessionOptions(
                enabled = true,
                maximumBufferedCallbacks = AiProviderLimits.INITIAL_CHUNK_CREDITS + 2,
            ),
        )
        val dispatch = harness.policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
        dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
        val completionOwned = RecordingDescriptorLease(0)
        val completionIncoming = RecordingIncomingDescriptors(0, completionOwned)
        val remote = RecordingRemote(onGrant = { count ->
            if (count == AiProviderLimits.INITIAL_CHUNK_CREDITS) {
                repeat(AiProviderLimits.INITIAL_CHUNK_CREDITS) { sequence ->
                    dispatch.callback.onChunk(
                        PROVIDER_UID,
                        AiProviderCodec.encodeTextChunk(
                            AiProviderChunk(sequence.toLong(), textDelta = sequence.toString()),
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
            listOf(AiProviderSessionEvent.Started::class.java) +
                List(AiProviderLimits.INITIAL_CHUNK_CREDITS) {
                    AiProviderSessionEvent.Chunk::class.java
                } + AiProviderSessionEvent.Completed::class.java,
            harness.events.map { it.javaClass },
        )
        assertEquals(
            (0 until AiProviderLimits.INITIAL_CHUNK_CREDITS).map(Int::toLong),
            harness.events.filterIsInstance<AiProviderSessionEvent.Chunk>()
                .map { it.chunk.sequence },
        )
        assertEquals(
            "01234567",
            (harness.events.last() as AiProviderSessionEvent.Completed).result.outputText,
        )
        assertEquals(listOf(AiProviderLimits.INITIAL_CHUNK_CREDITS), remote.creditGrants)
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
            val dispatch = harness.policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
            dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(harness.request))
            val remote = RecordingRemote(
                onGrant = { count ->
                    if (reenterBeforeThrow && count == AiProviderLimits.INITIAL_CHUNK_CREDITS) {
                        dispatch.callback.onChunk(
                            PROVIDER_UID,
                            AiProviderCodec.encodeTextChunk(AiProviderChunk(0, textDelta = "discarded")),
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
                    AiProviderSessionEvent.Rejected(
                        AiProviderSessionFailure.REMOTE_CONTROL_FAILED,
                    ),
                ),
                harness.events,
            )
            assertEquals(listOf(AiProviderLimits.INITIAL_CHUNK_CREDITS), remote.creditGrants)
            assertEquals(listOf("cancel", "close"), teardownOrder)
            assertEquals(1, remote.cancelCount.get())
            assertEquals(1, remote.closeCount.get())

            dispatch.callback.onChunk(
                PROVIDER_UID,
                AiProviderCodec.encodeTextChunk(AiProviderChunk(1, textDelta = "late")),
            )
            harness.drain()
            assertEquals(1, harness.events.size)
            assertEquals(listOf(AiProviderLimits.INITIAL_CHUNK_CREDITS), remote.creditGrants)
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
        val events = Collections.synchronizedList(mutableListOf<AiProviderSessionEvent>())
        val request = request(stream = true)
        val policy = AiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = RecordingRequestLease(0),
            serializedControlExecutor = dataLane,
            terminalFallbackExecutor = terminalLane,
            options = AiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            if (event == AiProviderSessionEvent.Cancelled) terminalDelivered.countDown()
        }
        try {
            val dispatch = policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
            dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
            val remote = RecordingRemote(
                onGrant = { count ->
                    if (count == AiProviderLimits.INITIAL_CHUNK_CREDITS) {
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
            assertEquals(listOf(AiProviderSessionEvent.Cancelled), events)
            assertEquals(listOf(AiProviderLimits.INITIAL_CHUNK_CREDITS), remote.creditGrants)
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
        val dispatch = harness.policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
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
        assertTrue(harness.events.single() is AiProviderSessionEvent.Started)
        harness.events.clear()

        dispatch.callback.onChunk(
            PROVIDER_UID,
            AiProviderCodec.encodeTextChunk(AiProviderChunk(0, textDelta = "delivered")),
        )
        harness.drain()

        assertEquals(
            listOf(
                AiProviderSessionEvent.Chunk::class.java,
                AiProviderSessionEvent.Rejected::class.java,
            ),
            harness.events.map { it.javaClass },
        )
        assertRejected(harness, AiProviderSessionFailure.REMOTE_CONTROL_FAILED)
        assertEquals(
            listOf(AiProviderLimits.INITIAL_CHUNK_CREDITS, 1),
            remote.creditGrants,
        )
        assertEquals(listOf("cancel", "close"), teardownOrder)
        assertEquals(1, incoming.closeCount.get())
        assertEquals(1, owned.closeCount.get())
        assertEquals(0, owned.readCount.get())

        dispatch.callback.onChunk(
            PROVIDER_UID,
            AiProviderCodec.encodeTextChunk(AiProviderChunk(1, textDelta = "late")),
        )
        harness.drain()
        assertEquals(2, harness.events.size)
        assertEquals(
            listOf(AiProviderLimits.INITIAL_CHUNK_CREDITS, 1),
            remote.creditGrants,
        )
    }

    @Test
    fun ninthChunkBeforeAnyReplenishmentFailsClosedForNoCredit() {
        val active = activeHarness(stream = true)
        active.harness.events.clear()

        repeat(AiProviderLimits.INITIAL_CHUNK_CREDITS + 1) { sequence ->
            active.dispatch.callback.onChunk(
                PROVIDER_UID,
                AiProviderCodec.encodeTextChunk(
                    AiProviderChunk(sequence.toLong(), textDelta = "x"),
                ),
            )
        }
        active.harness.drain()

        assertRejected(active.harness, AiProviderSessionFailure.CALLBACK_NO_CREDIT)
        assertEquals(1, active.harness.events.size)
        assertEquals(
            listOf(AiProviderLimits.INITIAL_CHUNK_CREDITS),
            active.remote.creditGrants,
        )
        assertEquals(1, active.remote.cancelCount.get())
        assertEquals(1, active.remote.closeCount.get())
    }

    @Test
    fun chunkWhileSynchronousOpenIsUnresolvedFailsBeforeCommit() {
        val lane = ManualExecutor()
        val harness = harness(request = request(stream = true), lane = lane)
        val dispatch = harness.policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch

        dispatch.callback.onChunk(
            PROVIDER_UID,
            AiProviderCodec.encodeTextChunk(AiProviderChunk(0, textDelta = "early")),
        )
        harness.drain()
        assertTrue(harness.events.isEmpty())

        val remote = RecordingRemote()
        harness.policy.commitOpenSucceeded(remote)
        harness.drain()
        assertRejected(harness, AiProviderSessionFailure.CALLBACK_OUT_OF_ORDER)
        assertTrue(remote.creditGrants.isEmpty())
        assertEquals(1, remote.cancelCount.get())
        assertEquals(1, remote.closeCount.get())
    }

    @Test
    fun externalCancelOverridesProvisionalPreCommitIngressFailure() {
        val lane = ManualExecutor()
        val harness = harness(lane = lane)
        val dispatch = harness.policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch

        dispatch.callback.onStarted(PROVIDER_UID, null)
        assertTrue(harness.policy.cancel())
        val remote = RecordingRemote()
        harness.policy.commitOpenSucceeded(remote)
        harness.drain()

        assertEquals(listOf(AiProviderSessionEvent.Cancelled), harness.events)
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

        val completed = active.harness.events.last() as AiProviderSessionEvent.Completed
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

            assertRejected(active.harness, AiProviderSessionFailure.CALLBACK_MALFORMED)
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
            AiProviderCodec.encodeToolCallBatch(calls),
            incoming,
        )
        active.harness.drain()

        assertRejected(active.harness, AiProviderSessionFailure.TOOLS_UNSUPPORTED)
        assertEquals(0, owned.readCount.get())
        assertEquals(1, incoming.closeCount.get())
        assertEquals(1, owned.closeCount.get())
        assertEquals(1, active.remote.cancelCount.get())
        assertEquals(1, active.remote.closeCount.get())
    }

    @Test
    fun externalTerminalDuringSynchronousOpenWaitsForLateHandleCleanupBeforeDelivery() {
        data class Case(
            val terminate: (AiProviderSessionPolicy) -> Boolean,
            val event: AiProviderSessionEvent,
            val cancelsRemote: Boolean,
        )
        val cases = listOf(
            Case(AiProviderSessionPolicy::cancel, AiProviderSessionEvent.Cancelled, true),
            Case(AiProviderSessionPolicy::timeout, AiProviderSessionEvent.TimedOut, true),
            Case(AiProviderSessionPolicy::binderDied, AiProviderSessionEvent.BinderDied, false),
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
            harness.policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch

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
            AiProviderCodec.encodeTextChunk(AiProviderChunk(0, textDelta = "late")),
        )
        active.dispatch.callback.onUsage(
            PROVIDER_UID,
            AiCommonCodec.encodeUsage(AiUsage(outputTokens = 1)),
        )

        assertTrue(active.harness.policy.cancel())
        active.harness.drain()

        assertEquals(listOf(AiProviderSessionEvent.Cancelled), active.harness.events)
        assertEquals(
            listOf(AiProviderLimits.INITIAL_CHUNK_CREDITS),
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
        val events = Collections.synchronizedList(mutableListOf<AiProviderSessionEvent>())
        val request = request(stream = true)
        val requestLease = RecordingRequestLease(0)
        val policy = AiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = requestLease,
            serializedControlExecutor = dataLane,
            terminalFallbackExecutor = terminalLane,
            options = AiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            when (event) {
                is AiProviderSessionEvent.Started -> startedDelivered.countDown()
                is AiProviderSessionEvent.Chunk -> {
                    dataClaimed.countDown()
                    check(releaseData.await(2, TimeUnit.SECONDS))
                }
                AiProviderSessionEvent.Cancelled -> terminalDelivered.countDown()
                else -> Unit
            }
        }
        try {
            val dispatch = policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
            dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
            val remote = RecordingRemote(onClose = { remoteClosed.countDown() })
            policy.commitOpenSucceeded(remote)
            assertTrue(startedDelivered.await(2, TimeUnit.SECONDS))

            dispatch.callback.onChunk(
                PROVIDER_UID,
                AiProviderCodec.encodeTextChunk(AiProviderChunk(0, textDelta = "claimed")),
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
                    AiProviderSessionEvent.Started::class.java,
                    AiProviderSessionEvent.Chunk::class.java,
                    AiProviderSessionEvent.Cancelled::class.java,
                ),
                events.map { it.javaClass },
            )
            assertEquals(
                listOf(AiProviderLimits.INITIAL_CHUNK_CREDITS),
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
        assertEquals(listOf(AiProviderSessionEvent.Cancelled), cancelFirst.harness.events)
        assertEquals(1, cancelFirst.remote.cancelCount.get())
        assertEquals(1, cancelFirst.remote.closeCount.get())
    }

    @Test
    fun chunkListenerMayReenterCancelWithoutDeadlockOrCreditReplenishment() {
        val dataLane = Executors.newSingleThreadExecutor()
        val terminalLane = Executors.newSingleThreadExecutor()
        val startedDelivered = CountDownLatch(1)
        val terminalDelivered = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<AiProviderSessionEvent>())
        val reentrantResult = AtomicBoolean(false)
        val request = request(stream = true)
        lateinit var policy: AiProviderSessionPolicy
        policy = AiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = RecordingRequestLease(0),
            serializedControlExecutor = dataLane,
            terminalFallbackExecutor = terminalLane,
            options = AiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            when (event) {
                is AiProviderSessionEvent.Started -> startedDelivered.countDown()
                is AiProviderSessionEvent.Chunk -> reentrantResult.set(policy.cancel())
                AiProviderSessionEvent.Cancelled -> terminalDelivered.countDown()
                else -> Unit
            }
        }
        try {
            val dispatch = policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
            dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
            val remote = RecordingRemote()
            policy.commitOpenSucceeded(remote)
            assertTrue(startedDelivered.await(2, TimeUnit.SECONDS))

            dispatch.callback.onChunk(
                PROVIDER_UID,
                AiProviderCodec.encodeTextChunk(AiProviderChunk(0, textDelta = "claim")),
            )
            assertTrue(terminalDelivered.await(2, TimeUnit.SECONDS))

            assertTrue(reentrantResult.get())
            assertEquals(
                listOf(
                    AiProviderSessionEvent.Started::class.java,
                    AiProviderSessionEvent.Chunk::class.java,
                    AiProviderSessionEvent.Cancelled::class.java,
                ),
                events.map { it.javaClass },
            )
            assertEquals(
                listOf(AiProviderLimits.INITIAL_CHUNK_CREDITS),
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
        val events = Collections.synchronizedList(mutableListOf<AiProviderSessionEvent>())
        val request = request(stream = false)
        val policy = AiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = RecordingRequestLease(0),
            serializedControlExecutor = dataLane,
            terminalFallbackExecutor = terminalLane,
            options = AiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            when (event) {
                is AiProviderSessionEvent.Started -> startedDelivered.countDown()
                AiProviderSessionEvent.TimedOut -> terminalDelivered.countDown()
                else -> Unit
            }
        }
        try {
            val dispatch = policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
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
                    AiProviderSessionEvent.Started::class.java,
                    AiProviderSessionEvent.TimedOut::class.java,
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
        val events = Collections.synchronizedList(mutableListOf<AiProviderSessionEvent>())
        val request = request(stream = false)
        val policy = AiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = RecordingRequestLease(0),
            serializedControlExecutor = controlLane,
            terminalFallbackExecutor = terminalLane,
            options = AiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            if (event == AiProviderSessionEvent.Cancelled) terminalDelivered.countDown()
        }
        try {
            val dispatch = policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
            dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
            val remote = RecordingRemote(onClose = { remoteClosed.countDown() })
            policy.commitOpenSucceeded(remote)
            controlLane.drain()
            assertTrue(events.single() is AiProviderSessionEvent.Started)
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
            assertEquals(listOf(AiProviderSessionEvent.Cancelled), events)
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
        val events = Collections.synchronizedList(mutableListOf<AiProviderSessionEvent>())
        val request = request(stream = false)
        val policy = AiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = RecordingRequestLease(0),
            serializedControlExecutor = controlLane,
            terminalFallbackExecutor = terminalLane,
            options = AiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            if (event == AiProviderSessionEvent.Cancelled) terminalDelivered.countDown()
        }
        try {
            val dispatch = policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
            dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
            val activeRemote = RecordingRemote()
            policy.commitOpenSucceeded(activeRemote)
            controlLane.drain()
            assertTrue(events.single() is AiProviderSessionEvent.Started)
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
            assertEquals(listOf(AiProviderSessionEvent.Cancelled), events)
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
        val events = mutableListOf<AiProviderSessionEvent>()
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
        lateinit var policy: AiProviderSessionPolicy
        policy = AiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = RecordingRequestLease(0),
            serializedControlExecutor = controlLane,
            terminalFallbackExecutor = terminalLane,
            options = AiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            if (event == AiProviderSessionEvent.Cancelled) {
                order += "listener.Cancelled"
                policy.commitOpenSucceeded(afterClaimRemote)
            }
        }
        val dispatch = policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
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
        assertTrue(events.single() is AiProviderSessionEvent.Started)
        events.clear()

        assertTrue(policy.cancel())
        terminalLane.drain()
        controlLane.drain()
        terminalLane.drain()

        assertEquals(listOf(AiProviderSessionEvent.Cancelled), events)
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
        val events = Collections.synchronizedList(mutableListOf<AiProviderSessionEvent>())
        val request = request(stream = false)
        val requestLease = BlockingRequestLease(requestCloseEntered, releaseRequestClose)
        val policy = AiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = requestLease,
            serializedControlExecutor = controlLane,
            terminalFallbackExecutor = terminalLane,
            options = AiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            if (event == AiProviderSessionEvent.Cancelled) terminalDelivered.countDown()
        }
        try {
            policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
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
            assertEquals(listOf(AiProviderSessionEvent.Cancelled), events)
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
        val events = Collections.synchronizedList(mutableListOf<AiProviderSessionEvent>())
        val request = request(stream = false)
        val policy = AiProviderSessionPolicy(
            plan = plan(),
            request = request,
            requestDescriptors = RecordingRequestLease(0),
            serializedControlExecutor = controlLane,
            terminalFallbackExecutor = terminalLane,
            options = AiProviderSessionOptions(enabled = true),
        ) { event ->
            events += event
            if (event is AiProviderSessionEvent.Rejected) terminalDelivered.countDown()
        }
        try {
            val dispatch = policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
            dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
            val remote = RecordingRemote()
            policy.commitOpenSucceeded(remote)
            controlLane.drain()
            assertTrue(events.single() is AiProviderSessionEvent.Started)
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
                AiProviderSessionEvent.Rejected(
                    AiProviderSessionFailure.CALLBACK_DESCRIPTOR_COUNT_INVALID,
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

            assertEquals(listOf(AiProviderSessionEvent.Cancelled), active.harness.events)
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
        harness.policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
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
        assertTrue(completedFirst.harness.events.last() is AiProviderSessionEvent.Completed)
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
        assertEquals(listOf(AiProviderSessionEvent.Cancelled), cancelFirst.harness.events)
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
        val dispatch = harness.policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
        assertEquals(1, dispatch.descriptorCount)
        assertSame(requestLease, dispatch.requestDescriptors)
        assertEquals(0, requestLease.closeCount.get())

        harness.policy.commitOpenFailed()
        harness.policy.commitOpenFailed()
        harness.policy.close()
        harness.drain()

        assertEquals(1, requestLease.closeCount.get())
        assertRejected(harness, AiProviderSessionFailure.OPEN_FAILED)
    }

    private fun assertTypedCallbackFailure(
        kind: TypedCallback,
        envelope: ByteArray?,
        expected: AiProviderSessionFailure,
    ) {
        val stream = kind == TypedCallback.CHUNK
        val active = if (kind == TypedCallback.STARTED) null else activeHarness(stream)
        val harness = active?.harness ?: harness(request = request(stream = stream))
        val dispatch = active?.dispatch ?: (
            harness.policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
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
                if (expected == AiProviderSessionFailure.CALLBACK_MALFORMED) 1 else 0,
                incoming.copyCount.get(),
            )
            assertEquals(
                if (expected == AiProviderSessionFailure.CALLBACK_MALFORMED) 1 else 0,
                owned.closeCount.get(),
            )
        }
    }

    @Test
    fun nativeToolsMaterializeDescriptorsAndResumeOnlyWithTheMatchingBatch() {
        val active = activeHarness(stream = false, toolRounds = 2)
        val owned = RecordingDescriptorLease(1) { _, _ -> "{\"private\":true}".toByteArray() }
        val incoming = RecordingIncomingDescriptors(1, owned)
        active.dispatch.callback.onToolCalls(PROVIDER_UID, AiProviderCodec.encodeToolCallBatch(AiToolCallBatch(
            listOf(AiToolCall("call-1", "lookup", descriptorJsonPayload(0, 16))))), incoming)
        active.harness.drain()
        val calls = active.harness.events.last() as AiProviderSessionEvent.ToolCalls
        assertEquals("{\"private\":true}", calls.calls.single().argumentsJson)
        assertFalse(calls.toString().contains("private"))
        assertEquals(1, owned.readCount.get())
        assertEquals(1, owned.closeCount.get())
        val results = AiToolResultBatch(listOf(AiToolResult("call-1", textPayload("result"))))
        assertTrue(active.harness.policy.submitToolResults(results))
        assertFalse(active.harness.policy.submitToolResults(results))
        active.harness.drain()
        assertEquals(1, active.remote.toolResults.size)
        assertEquals(results, AiProviderCodec.decodeToolResultBatch(active.remote.toolResults.single()))
        active.dispatch.callback.onCompleted(PROVIDER_UID, completionEnvelope(textPayload("done")),
            RecordingIncomingDescriptors(0, RecordingDescriptorLease(0)))
        active.harness.drain()
        assertTrue(active.harness.events.last() is AiProviderSessionEvent.Completed)
    }

    @Test
    fun nativeToolsRejectWrongResultsReplayAndCompletionBeforeResults() {
        for (invalid in listOf("wrong-result", "replayed-call", "early-completion", "extra-round")) {
            val active = activeHarness(stream = false, toolRounds = if (invalid == "extra-round") 1 else 2)
            fun emitCall(id: String) {
                active.dispatch.callback.onToolCalls(PROVIDER_UID, AiProviderCodec.encodeToolCallBatch(AiToolCallBatch(
                    listOf(AiToolCall(id, "lookup", jsonPayload("{}"))))), RecordingIncomingDescriptors(0, RecordingDescriptorLease(0)))
                active.harness.drain()
            }
            emitCall("call-1")
            if (invalid == "early-completion") {
                active.dispatch.callback.onCompleted(PROVIDER_UID, completionEnvelope(textPayload("done")),
                    RecordingIncomingDescriptors(0, RecordingDescriptorLease(0)))
            } else {
                assertTrue(active.harness.policy.submitToolResults(AiToolResultBatch(listOf(
                    AiToolResult(if (invalid == "wrong-result") "wrong" else "call-1", textPayload("result"))))))
                active.harness.drain()
                if (invalid != "wrong-result") emitCall(if (invalid == "extra-round") "call-2" else "call-1")
            }
            active.harness.drain()
            assertTrue(active.harness.events.last() is AiProviderSessionEvent.Rejected)
            assertEquals(1, active.remote.cancelCount.get())
        }
    }

    @Test
    fun cancellationDiscardsQueuedToolResultsBeforeRemoteSubmission() {
        val active = activeHarness(stream = false, toolRounds = 1)
        active.dispatch.callback.onToolCalls(PROVIDER_UID, AiProviderCodec.encodeToolCallBatch(AiToolCallBatch(
            listOf(AiToolCall("call-1", "lookup", jsonPayload("{}"))))), RecordingIncomingDescriptors(0, RecordingDescriptorLease(0)))
        active.harness.drain()
        assertTrue(active.harness.policy.submitToolResults(AiToolResultBatch(listOf(AiToolResult("call-1", textPayload("result"))))))
        assertTrue(active.harness.policy.cancel())
        active.harness.drain()
        assertTrue(active.remote.toolResults.isEmpty())
        assertEquals(AiProviderSessionEvent.Cancelled, active.harness.events.last())
    }

    @Test fun imageToolDescriptorLeaseIsDeferredAndClosedOnSuccessFailureOrCancellation() {
        for (mode in listOf("success", "cancel", "open-failure", "wrong-count")) {
            val active = activeHarness(stream = false, toolRounds = 1, vision = true)
            active.dispatch.callback.onToolCalls(PROVIDER_UID, AiProviderCodec.encodeToolCallBatch(AiToolCallBatch(
                listOf(AiToolCall("call-1", "lookup", jsonPayload("{}"))))), RecordingIncomingDescriptors(0, RecordingDescriptorLease(0)))
            active.harness.drain()
            val result = AiToolResultBatch(listOf(AiToolResult("call-1", textPayload("screenshot"), imageParts = listOf(
                AiContentPart(AiPayloadReference(AiProviderMimeType.PNG, 1, descriptorIndex = 0, sha256 = "a".repeat(64)),
                    org.autojs.plugin.ai.provider.api.AiImageMetadata(1, 1))))))
            var opened = 0
            val lease = RecordingRequestLease(if (mode == "wrong-count") 0 else 1)
            val factory = object : AiProviderSessionRequestDescriptorFactory {
                override val count = 1
                override fun open(): AiProviderSessionRequestDescriptorLease {
                    opened++
                    check(mode != "open-failure")
                    return lease
                }
            }
            assertTrue(active.harness.policy.submitToolResults(result, factory))
            assertEquals(0, opened)
            if (mode == "cancel") active.harness.policy.cancel()
            active.harness.drain()
            assertEquals(if (mode == "cancel") 0 else 1, opened)
            assertEquals(if (mode in listOf("cancel", "open-failure")) 0 else 1, lease.closeCount.get())
            assertEquals(if (mode == "success") listOf(1) else emptyList<Int>(), active.remote.resultDescriptorCounts)
            if (mode in listOf("open-failure", "wrong-count")) {
                assertTrue(active.harness.events.last() is AiProviderSessionEvent.Rejected)
            }
        }
    }

    private fun activeHarness(stream: Boolean, toolRounds: Int = 0, vision: Boolean = false): ActiveHarness {
        val lane = ManualExecutor()
        val initial = request(stream = stream, toolRounds = toolRounds)
        val request = if (vision) initial.copy(protocolVersion = AiProviderProtocol.PROTOCOL_V2_1,
            options = initial.options.copy(requiredCapabilityIds = listOf(AiProviderCapabilityId.VISION))) else initial
        val harness = harness(request = request, lane = lane, plan = plan(vision = vision))
        val dispatch = harness.policy.beginOpen() as AiProviderSessionOpenDecision.Dispatch
        dispatch.callback.onStarted(PROVIDER_UID, startedEnvelope(request))
        val remote = RecordingRemote()
        harness.policy.commitOpenSucceeded(remote)
        harness.drain()
        assertTrue(harness.events.single() is AiProviderSessionEvent.Started)
        return ActiveHarness(harness, dispatch, remote)
    }

    private fun harness(
        request: AiProviderRequest = request(),
        plan: AiProviderHostSessionPlan = plan(),
        requestLease: RecordingRequestLease = RecordingRequestLease(requestDescriptorCount(request)),
        lane: ManualExecutor = ManualExecutor(),
        fallback: ManualExecutor = ManualExecutor(),
        options: AiProviderSessionOptions = AiProviderSessionOptions(enabled = true),
        onEvent: (AiProviderSessionEvent) -> Unit = {},
    ): Harness {
        val events = Collections.synchronizedList(mutableListOf<AiProviderSessionEvent>())
        val policy = AiProviderSessionPolicy(
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

    private fun plan(
        targetCapabilityIds: Set<String> = setOf(
            AiProviderCapabilityId.STREAMING,
            AiProviderCapabilityId.REASONING,
            AiProviderCapabilityId.TOOLS,
            AiProviderCapabilityId.STRUCTURED_JSON,
            AiProviderCapabilityId.USAGE,
        ),
        vision: Boolean = false,
    ): AiProviderHostSessionPlan {
        val capabilities = AiProviderHostTestFixtures.capabilities().copy(
            vision = if (vision) org.autojs.plugin.ai.provider.api.AiVisionCapabilities() else null)
        val provider = if (vision) AiProviderHostTestFixtures.providerInfo(protocolRange = AiProviderProtocol.HOST_PROTOCOL_RANGE)
            else AiProviderHostTestFixtures.providerInfo()
        val selected = AiProviderSelectionPolicy.select(
            candidates = listOf(AiProviderHostTestFixtures.candidate(providerInfo = provider, capabilities = capabilities)),
            environment = AiProviderHostTestFixtures.environment(),
            hostCapabilities = capabilities,
            requirement = AiProviderCapabilityRequirement(),
            options = AiProviderSelectionOptions(enabled = true),
        ) as AiProviderSelectionResult.Selected
        val target = AiProviderHostTestFixtures.target(
            targetId = TARGET_ID,
            modelId = MODEL_ID,
            capabilityIds = targetCapabilityIds.toList() + if (vision) listOf(AiProviderCapabilityId.VISION) else emptyList(),
            maximumContextBytes = 512L * 1024L,
            maximumOutputBytes = 256L * 1024L,
        )
        return (AiProviderHostSessionPlanner.plan(
            selected = selected,
            catalogGeneration = "fixture-generation",
            target = target,
            requirement = AiProviderCapabilityRequirement(),
        ) as AiProviderSessionPlanningResult.Planned).plan
    }

    private fun request(
        stream: Boolean = false,
        reportUsage: Boolean = false,
        maximumOutputTokens: Long? = null,
        payload: AiPayloadReference = textPayload("hello"),
        toolRounds: Int = 0,
    ) = AiProviderRequest(
        requestId = "request-session",
        protocolVersion = AiProviderProtocol.PROTOCOL_V2,
        providerId = PROVIDER_ID,
        targetId = TARGET_ID,
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
            maximumOutputTokens = maximumOutputTokens,
            maximumToolRounds = toolRounds,
            timeoutMillis = 30_000,
        ),
        tools = if (toolRounds > 0) listOf(AiToolDefinition("lookup", "Lookup", jsonPayload("{\"type\":\"object\"}"))) else emptyList(),
    )

    private fun startedEnvelope(request: AiProviderRequest): ByteArray = AiProviderCodec.encodeSessionStarted(
        AiSessionStarted(
            requestId = request.requestId,
            sessionId = "fixture-session",
            protocolVersion = request.protocolVersion,
            providerId = request.providerId,
            targetId = request.targetId,
            firstChunkSequence = 0,
            effectiveMaximumOutputBytes = request.options.maximumOutputBytes,
            effectiveMaximumToolRounds = request.options.maximumToolRounds,
        ),
    )

    private fun completionEnvelope(
        output: AiPayloadReference,
        usage: AiUsage? = null,
    ): ByteArray = AiProviderCodec.encodeCompletionResult(
        AiCompletionResult(
            output = output,
            finishReason = AiFinishReason.STOP,
            usage = usage,
        ),
    )

    private fun textPayload(value: String): AiPayloadReference {
        val bytes = value.toByteArray()
        return AiPayloadReference(
            mimeType = AiProviderMimeType.PLAIN,
            declaredLengthBytes = bytes.size.toLong(),
            inlineBytes = bytes,
            charset = "utf-8",
        )
    }

    private fun jsonPayload(value: String): AiPayloadReference {
        val bytes = value.toByteArray()
        return AiPayloadReference(
            mimeType = AiProviderMimeType.JSON,
            declaredLengthBytes = bytes.size.toLong(),
            inlineBytes = bytes,
            charset = "utf-8",
        )
    }

    private fun descriptorTextPayload(index: Int, length: Long) = AiPayloadReference(
        mimeType = AiProviderMimeType.PLAIN,
        declaredLengthBytes = length,
        descriptorIndex = index,
        charset = "utf-8",
    )

    private fun descriptorJsonPayload(index: Int, length: Long) = AiPayloadReference(
        mimeType = AiProviderMimeType.JSON,
        declaredLengthBytes = length,
        descriptorIndex = index,
        charset = "utf-8",
    )

    private fun requestDescriptorCount(request: AiProviderRequest): Int = request.messages
        .flatMap(AiMessage::parts)
        .mapNotNull { it.payload.descriptorIndex }
        .size

    private fun assertRejected(harness: Harness, failure: AiProviderSessionFailure) {
        assertEquals(
            AiProviderSessionEvent.Rejected(failure),
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
        val plan: AiProviderHostSessionPlan,
        val request: AiProviderRequest,
        val requestLease: RecordingRequestLease,
        val lane: ManualExecutor,
        val fallback: ManualExecutor,
        val events: MutableList<AiProviderSessionEvent>,
        val policy: AiProviderSessionPolicy,
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
        val dispatch: AiProviderSessionOpenDecision.Dispatch,
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
            callback: AiProviderSessionCallbackIngress,
            envelope: ByteArray?,
            descriptors: AiProviderSessionIncomingDescriptors?,
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
    ) : AiProviderSessionRequestDescriptorLease {
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
    ) : AiProviderSessionDescriptorLease {
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
        private val lease: AiProviderSessionDescriptorLease,
        private val failCopy: Boolean = false,
    ) : AiProviderSessionIncomingDescriptors {
        val copyCount = AtomicInteger()
        val closeCount = AtomicInteger()

        override fun copyForAsync(): AiProviderSessionDescriptorLease {
            copyCount.incrementAndGet()
            if (failCopy) error("copy failed for test")
            return lease
        }

        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    private class BlockingIncomingDescriptors(
        private val lease: AiProviderSessionDescriptorLease,
        private val entered: CountDownLatch,
        private val release: CountDownLatch,
    ) : AiProviderSessionIncomingDescriptors {
        override val count: Int = lease.count
        val closeCount = AtomicInteger()

        override fun copyForAsync(): AiProviderSessionDescriptorLease {
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
    ) : AiProviderSessionIncomingDescriptors {
        override val count: Int = 0
        val copyCount = AtomicInteger()
        val closeCount = AtomicInteger()

        override fun copyForAsync(): AiProviderSessionDescriptorLease {
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
    ) : AiProviderSessionDescriptorLease {
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
    ) : AiProviderSessionDescriptorLease {
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
    ) : AiProviderSessionRequestDescriptorLease {
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
    ) : AiProviderSessionRemote {
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
    ) : AiProviderSessionRemote {
        val creditGrants: MutableList<Int> = Collections.synchronizedList(mutableListOf())
        val toolResults: MutableList<ByteArray> = Collections.synchronizedList(mutableListOf())
        val resultDescriptorCounts = mutableListOf<Int>()
        val cancelCount = AtomicInteger()
        val closeCount = AtomicInteger()

        override fun grantCredits(count: Int) {
            creditGrants += count
            onGrant(count)
        }

        override fun submitToolResults(envelope: ByteArray) { toolResults += envelope.copyOf() }
        override fun submitToolResults(envelope: ByteArray, descriptors: AiProviderSessionRequestDescriptorLease) {
            resultDescriptorCounts += descriptors.count
            toolResults += envelope.copyOf()
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
        const val TARGET_ID = "fixture.provider:model"
        const val MODEL_ID = "fixture.model"
    }
}
