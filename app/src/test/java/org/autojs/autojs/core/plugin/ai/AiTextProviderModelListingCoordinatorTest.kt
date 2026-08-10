package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.text.api.AiModelInfo
import org.autojs.plugin.ai.text.api.AiModelListRequest
import org.autojs.plugin.ai.text.api.AiModelPage
import org.autojs.plugin.ai.text.api.AiTextCapabilityId
import org.autojs.plugin.ai.text.api.AiTextCodec
import org.autojs.plugin.ai.text.api.AiTextLimits
import org.autojs.plugin.ai.text.api.AiTextProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AiTextProviderModelListingCoordinatorTest {

    @Test
    fun defaultOffHasNoTransportSchedulerOrOwnerSideEffects() {
        val input = modelListingInput()
        val transport = FakeModelListingTransport(input)
        val scheduler = FakeModelListingDeadlineScheduler()
        val owner = modelListingOwner()
        val outcomes = CopyOnWriteArrayList<AiTextProviderModelListingRunOutcome>()

        coordinator(transport, scheduler = scheduler, owner = owner).start(
            modelListingRequest(input = input, enabled = false),
            outcomes::add,
        )

        assertSame(AiTextProviderModelListingRunOutcome.Disabled, outcomes.single())
        assertEquals(0, scheduler.scheduleCalls)
        assertEquals(0, transport.inspectionCalls)
        assertEquals(0, transport.bindCalls)
        assertEquals(0, transport.openRpcCalls)
        assertEquals(0, transport.rpc.dispatchCalls)
        assertEquals(
            AiTextProviderModelListingAdmission.ACQUIRED,
            owner.tryAcquire(snapshot(input).component),
        )
        owner.release()
    }

    @Test
    fun singlePageCompletesFromThePinnedSameBinderTranscript() {
        val input = modelListingInput()
        val transport = FakeModelListingTransport(input).apply {
            pageResponses += PageResponse(PINNED_UID, page(model("fixture.single")))
            respondWithQueuedPages()
        }
        val scheduler = FakeModelListingDeadlineScheduler()
        val outcomes = CopyOnWriteArrayList<AiTextProviderModelListingRunOutcome>()

        coordinator(transport, scheduler = scheduler).start(
            modelListingRequest(input),
            outcomes::add,
        )

        val completed = outcomes.single() as AiTextProviderModelListingRunOutcome.Completed
        assertEquals(listOf("fixture.single"), completed.catalog.models.map { it.modelId })
        assertEquals(1, transport.openRpcCalls)
        assertEquals(1, transport.rpc.providerInfoCalls)
        assertEquals(1, transport.rpc.capabilitiesCalls)
        assertEquals(1, transport.rpc.prepareCallCalls)
        assertEquals(1, transport.rpc.dispatchCalls)
        assertEquals(1, transport.binding.closeCalls.get())
        assertEquals(1, transport.rpc.preparedCallCloseCalls.get())
        assertEquals(1, scheduler.closeCalls.get())
    }

    @Test
    fun pageSizeOneFollowsFourRealContinuationDecisionsWithoutDispatchReentry() {
        val input = modelListingInput()
        val transport = FakeModelListingTransport(input).apply {
            pageResponses += PageResponse(PINNED_UID, page(model("fixture.model-1"), next = "token-1"))
            pageResponses += PageResponse(PINNED_UID, page(model("fixture.model-2"), next = "token-2"))
            pageResponses += PageResponse(PINNED_UID, page(model("fixture.model-3"), next = "token-3"))
            pageResponses += PageResponse(PINNED_UID, page(model("fixture.model-4")))
            respondWithQueuedPages()
        }
        val outcomes = CopyOnWriteArrayList<AiTextProviderModelListingRunOutcome>()

        coordinator(transport).start(
            modelListingRequest(input = input, pageSize = 1),
            outcomes::add,
        )

        val completed = outcomes.single() as AiTextProviderModelListingRunOutcome.Completed
        assertEquals(
            listOf("fixture.model-1", "fixture.model-2", "fixture.model-3", "fixture.model-4"),
            completed.catalog.models.map { it.modelId },
        )
        assertEquals(listOf(null, "token-1", "token-2", "token-3"), transport.rpc.requests.map { it.pageToken })
        assertTrue(transport.rpc.requests.all { it.pageSize == 1 })
        assertEquals(4, transport.rpc.dispatchCalls)
        assertEquals(1, transport.rpc.maximumConcurrentDispatches.get())
        assertEquals(4, transport.rpc.preparedCallCloseCalls.get())
    }

    @Test
    fun tokenCapacityAndReplayEvidenceAreIsolatedByCompletePinnedProviderIdentity() {
        val owner = modelListingOwner(
            maximumProviderIdentities = 2,
            maximumConsumedTokensPerProvider = 1,
        )
        val firstInput = modelListingInput()
        val firstTransport = FakeModelListingTransport(firstInput).apply {
            pageResponses += PageResponse(firstInput.packageUid, page(model("fixture.first-1"), "shared-token"))
            pageResponses += PageResponse(firstInput.packageUid, page(model("fixture.first-2"), "first-overflow"))
            respondWithQueuedPages()
        }
        val firstOutcomes = CopyOnWriteArrayList<AiTextProviderModelListingRunOutcome>()

        coordinator(firstTransport, owner = owner).start(
            modelListingRequest(input = firstInput, pageSize = 1),
            firstOutcomes::add,
        )

        val firstRejected = firstOutcomes.single() as AiTextProviderModelListingRunOutcome.Rejected
        assertEquals(AiTextProviderModelListingRunFailure.TRANSCRIPT_REJECTED, firstRejected.failure)
        assertEquals(AiTextProviderModelListingFailure.PAGINATION_INVALID, firstRejected.transcriptFailure)
        assertEquals(2, firstTransport.rpc.dispatchCalls)

        val secondInput = modelListingInput(
            packageName = "second.ai.provider",
            packageUid = PINNED_UID + 1,
        )
        val secondTransport = FakeModelListingTransport(secondInput).apply {
            pageResponses += PageResponse(secondInput.packageUid, page(model("fixture.second-1"), "shared-token"))
            pageResponses += PageResponse(secondInput.packageUid, page(model("fixture.second-2")))
            respondWithQueuedPages()
        }
        val secondOutcomes = CopyOnWriteArrayList<AiTextProviderModelListingRunOutcome>()

        coordinator(secondTransport, owner = owner).start(
            modelListingRequest(input = secondInput, pageSize = 1),
            secondOutcomes::add,
        )

        val secondCompleted = secondOutcomes.single() as AiTextProviderModelListingRunOutcome.Completed
        assertEquals(listOf("fixture.second-1", "fixture.second-2"), secondCompleted.catalog.models.map { it.modelId })
        assertEquals(2, secondTransport.rpc.dispatchCalls)
        assertNull(
            owner.tokenLedgerFor(
                pinned(modelListingInput(packageName = "third.ai.provider", packageUid = PINNED_UID + 2)),
            ),
        )
        assertTrue(
            runCatching {
                AiTextProviderModelListingProcessOwner(
                    maximumProviderIdentities = 2,
                    maximumConsumedTokensPerProvider =
                        AiTextLimits.MAX_MODEL_PAGE_TOKEN_LEDGER_ENTRIES,
                )
            }.isFailure,
        )
    }

    @Test
    fun metadataRevalidationMismatchStopsBeforeAnyModelListDispatch() {
        val input = modelListingInput()
        val transport = FakeModelListingTransport(input).apply {
            rpc.encodedProviderInfo = AiCommonCodec.encodeProviderInfo(
                AiTextHostTestFixtures.providerInfo(providerId = "different.provider"),
            )
        }
        val outcomes = CopyOnWriteArrayList<AiTextProviderModelListingRunOutcome>()

        coordinator(transport).start(modelListingRequest(input), outcomes::add)

        val rejected = outcomes.single() as AiTextProviderModelListingRunOutcome.Rejected
        assertEquals(AiTextProviderModelListingRunFailure.PINNED_METADATA_MISMATCH, rejected.failure)
        assertEquals(1, transport.openRpcCalls)
        assertEquals(1, transport.rpc.providerInfoCalls)
        assertEquals(1, transport.rpc.capabilitiesCalls)
        assertEquals(0, transport.rpc.prepareCallCalls)
        assertEquals(0, transport.rpc.dispatchCalls)
    }

    @Test
    fun exactIdentityDriftImmediatelyBeforeDispatchStopsWithoutPreparingACall() {
        val input = modelListingInput()
        val drifted = modelListingInput(versionCode = input.versionCode + 1L)
        val transport = FakeModelListingTransport(input).apply {
            repeat(3) { inspectionResults += discovered(input) }
            inspectionResults += discovered(drifted)
        }
        val outcomes = CopyOnWriteArrayList<AiTextProviderModelListingRunOutcome>()

        coordinator(transport).start(modelListingRequest(input), outcomes::add)

        val rejected = outcomes.single() as AiTextProviderModelListingRunOutcome.Rejected
        assertEquals(AiTextProviderModelListingRunFailure.IDENTITY_DRIFT, rejected.failure)
        assertEquals(4, transport.inspectionCalls)
        assertEquals(0, transport.rpc.prepareCallCalls)
        assertEquals(0, transport.rpc.dispatchCalls)
    }

    @Test
    fun callbackIngressRejectsUidAndTypedOversizeBeforeCopyOrReinspection() {
        val cases = listOf(
            CallbackIngressCase(
                failure = AiTextProviderModelListingFailure.CALLBACK_UID_MISMATCH,
                invoke = { callback ->
                    callback.onPage(PINNED_UID + 1, ByteArray(AiTextLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES + 1))
                },
            ),
            CallbackIngressCase(
                failure = AiTextProviderModelListingFailure.PAGE_TOO_LARGE,
                invoke = { callback ->
                    callback.onPage(PINNED_UID, ByteArray(AiTextLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES + 1))
                },
            ),
            CallbackIngressCase(
                failure = AiTextProviderModelListingFailure.ERROR_TOO_LARGE,
                invoke = { callback ->
                    callback.onFailed(PINNED_UID, ByteArray(AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES + 1))
                },
            ),
        )
        cases.forEach { case ->
            val input = modelListingInput()
            val transport = FakeModelListingTransport(input)
            val outcomes = CopyOnWriteArrayList<AiTextProviderModelListingRunOutcome>()
            coordinator(transport).start(modelListingRequest(input), outcomes::add)

            case.invoke(requireNotNull(transport.rpc.lastCallback))

            val rejected = outcomes.single() as AiTextProviderModelListingRunOutcome.Rejected
            assertEquals(AiTextProviderModelListingRunFailure.TRANSCRIPT_REJECTED, rejected.failure)
            assertEquals(case.failure, rejected.transcriptFailure)
            assertEquals(4, transport.inspectionCalls)
            assertEquals(1, transport.rpc.dispatchCalls)
        }
    }

    @Test
    fun thirdCallbackIsRejectedByFloodGateWhileOneIsActiveAndOnePending() {
        val input = modelListingInput()
        val admissionEntered = CountDownLatch(1)
        val releaseAdmission = CountDownLatch(1)
        val dispatchReturned = CountDownLatch(1)
        val transport = FakeModelListingTransport(input).apply {
            inspectBehavior = { inspection ->
                if (inspection == 5) {
                    admissionEntered.countDown()
                    assertTrue("callback inspection was not released", releaseAdmission.await(5, TimeUnit.SECONDS))
                }
            }
            rpc.dispatchBehavior = { _, _ -> dispatchReturned.countDown() }
        }
        val blocking = Executors.newSingleThreadExecutor()
        val outcomes = CopyOnWriteArrayList<AiTextProviderModelListingRunOutcome>()

        try {
            coordinator(transport, blocking = blocking).start(modelListingRequest(input), outcomes::add)
            assertTrue("dispatch did not return", dispatchReturned.await(5, TimeUnit.SECONDS))
            blocking.submit {}.get(5, TimeUnit.SECONDS)
            val callback = requireNotNull(transport.rpc.lastCallback)
            val envelope = page(model("fixture.flood"))

            callback.onPage(PINNED_UID, envelope)
            assertTrue("callback admission did not enter", admissionEntered.await(5, TimeUnit.SECONDS))
            callback.onPage(PINNED_UID, envelope)
            callback.onPage(PINNED_UID, envelope)

            val rejected = outcomes.single() as AiTextProviderModelListingRunOutcome.Rejected
            assertEquals(AiTextProviderModelListingRunFailure.CALLBACK_FLOOD, rejected.failure)
            releaseAdmission.countDown()
            blocking.submit {}.get(5, TimeUnit.SECONDS)
            assertEquals(1, outcomes.size)
            assertEquals(1, transport.binding.closeCalls.get())
            assertEquals(1, transport.rpc.preparedCallCloseCalls.get())
        } finally {
            releaseAdmission.countDown()
            blocking.shutdownNow()
            blocking.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun cancelDeathAndCallbackEachPublishAtMostOneTerminalOutcome() {
        FirstTerminal.entries.forEach { first ->
            val input = modelListingInput()
            val transport = FakeModelListingTransport(input)
            val outcomes = CopyOnWriteArrayList<AiTextProviderModelListingRunOutcome>()
            val handle = coordinator(transport).start(modelListingRequest(input), outcomes::add)
            val callback = assertNotNull(transport.rpc.lastCallback).let {
                requireNotNull(transport.rpc.lastCallback)
            }

            when (first) {
                FirstTerminal.CANCEL -> handle.close()
                FirstTerminal.DEATH -> transport.die()
                FirstTerminal.CALLBACK -> callback.onPage(PINNED_UID, page(model("fixture.race")))
            }
            handle.close()
            transport.die()
            callback.onPage(PINNED_UID, page(model("fixture.late")))

            assertEquals("first=$first", 1, outcomes.size)
            when (first) {
                FirstTerminal.CANCEL -> assertSame(AiTextProviderModelListingRunOutcome.Cancelled, outcomes.single())
                FirstTerminal.DEATH -> assertSame(AiTextProviderModelListingRunOutcome.BinderDied, outcomes.single())
                FirstTerminal.CALLBACK -> assertTrue(outcomes.single() is AiTextProviderModelListingRunOutcome.Completed)
            }
            assertEquals("first=$first", 1, transport.binding.closeCalls.get())
            assertEquals("first=$first", 1, transport.rpc.preparedCallCloseCalls.get())
        }
    }

    @Test
    fun completedOutcomeCallbackCanImmediatelyStartAnotherListingWithoutBusy() {
        val input = modelListingInput()
        val transport = FakeModelListingTransport(input).apply {
            pageResponses += PageResponse(PINNED_UID, page(model("fixture.first")))
            pageResponses += PageResponse(PINNED_UID, page(model("fixture.second")))
            respondWithQueuedPages()
        }
        val owner = modelListingOwner()
        val first = CopyOnWriteArrayList<AiTextProviderModelListingRunOutcome>()
        val second = CopyOnWriteArrayList<AiTextProviderModelListingRunOutcome>()
        val coordinator = coordinator(transport, owner = owner)

        coordinator.start(modelListingRequest(input)) { outcome ->
            first += outcome
            coordinator.start(modelListingRequest(input), second::add)
        }

        assertTrue(first.single() is AiTextProviderModelListingRunOutcome.Completed)
        assertTrue(second.single() is AiTextProviderModelListingRunOutcome.Completed)
        assertEquals(2, transport.rpc.dispatchCalls)
    }

    @Test
    fun completedOutcomeCallbackBlockedPastDeadlineCannotFuseProvider() {
        val input = modelListingInput()
        val callbackEntered = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val transport = FakeModelListingTransport(input).apply {
            pageResponses += PageResponse(PINNED_UID, page(model("fixture.blocked-outcome")))
            respondWithQueuedPages()
        }
        val scheduler = FakeModelListingDeadlineScheduler()
        val clock = FakeModelListingClock()
        val owner = modelListingOwner()
        val worker = Executors.newSingleThreadExecutor()
        val outcomes = CopyOnWriteArrayList<AiTextProviderModelListingRunOutcome>()

        try {
            coordinator(
                transport = transport,
                worker = worker,
                scheduler = scheduler,
                clock = clock,
                owner = owner,
            ).start(modelListingRequest(input, timeoutMillis = TIMEOUT_MILLIS)) { outcome ->
                outcomes += outcome
                callbackEntered.countDown()
                assertTrue("outcome callback was not released", releaseCallback.await(5, TimeUnit.SECONDS))
            }

            assertTrue("outcome callback did not enter", callbackEntered.await(5, TimeUnit.SECONDS))
            assertTrue(outcomes.single() is AiTextProviderModelListingRunOutcome.Completed)
            clock.nowMillis += TIMEOUT_MILLIS
            scheduler.fire()

            assertEquals(
                AiTextProviderModelListingAdmission.ACQUIRED,
                owner.tryAcquire(snapshot(input).component),
            )
            owner.release()
            assertEquals(1, scheduler.closeCalls.get())

            releaseCallback.countDown()
            worker.submit {}.get(5, TimeUnit.SECONDS)
            assertEquals(1, outcomes.size)
        } finally {
            releaseCallback.countDown()
            worker.shutdownNow()
            worker.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun completedClaimBeforeOperationDoneIgnoresStaleDeadlineWithoutFuse() {
        val input = modelListingInput()
        val closeEntered = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val transport = FakeModelListingTransport(input).apply {
            pageResponses += PageResponse(PINNED_UID, page(model("fixture.completed-before-unwind")))
            respondWithQueuedPages()
            rpc.preparedCloseBehavior = {
                closeEntered.countDown()
                releaseClose.await(5, TimeUnit.SECONDS)
            }
        }
        val scheduler = FakeModelListingDeadlineScheduler()
        val clock = FakeModelListingClock()
        val owner = modelListingOwner()
        val worker = Executors.newSingleThreadExecutor()
        val outcomes = CopyOnWriteArrayList<AiTextProviderModelListingRunOutcome>()

        try {
            coordinator(
                transport = transport,
                worker = worker,
                scheduler = scheduler,
                clock = clock,
                owner = owner,
            ).start(modelListingRequest(input, timeoutMillis = TIMEOUT_MILLIS), outcomes::add)

            assertTrue("prepared-call close did not enter", closeEntered.await(5, TimeUnit.SECONDS))
            assertTrue(outcomes.isEmpty())
            clock.nowMillis += TIMEOUT_MILLIS
            scheduler.fire()

            assertEquals(
                AiTextProviderModelListingAdmission.BUSY,
                owner.tryAcquire(snapshot(input).component),
            )
            assertTrue(outcomes.isEmpty())
            assertEquals(1, scheduler.closeCalls.get())

            releaseClose.countDown()
            worker.submit {}.get(5, TimeUnit.SECONDS)
            assertTrue(outcomes.single() is AiTextProviderModelListingRunOutcome.Completed)
            assertEquals(
                AiTextProviderModelListingAdmission.ACQUIRED,
                owner.tryAcquire(snapshot(input).component),
            )
            owner.release()
            assertEquals(1, transport.rpc.preparedCallCloseCalls.get())
            assertEquals(1, transport.binding.closeCalls.get())
        } finally {
            releaseClose.countDown()
            worker.shutdownNow()
            worker.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun cancelledBlockedInspectionKeepsWatchdogAndGateUntilLateUnwind() {
        val input = modelListingInput()
        val other = snapshot(modelListingInput(packageName = "other.ai.provider")).component
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val transport = FakeModelListingTransport(input).apply {
            inspectBehavior = { inspection ->
                if (inspection == 1) {
                    entered.countDown()
                    assertTrue("blocked inspection was not released", release.await(5, TimeUnit.SECONDS))
                }
            }
        }
        val scheduler = FakeModelListingDeadlineScheduler()
        val clock = FakeModelListingClock()
        val owner = modelListingOwner()
        val worker = Executors.newSingleThreadExecutor()
        val outcomes = CopyOnWriteArrayList<AiTextProviderModelListingRunOutcome>()

        try {
            val handle = coordinator(
                transport = transport,
                worker = worker,
                scheduler = scheduler,
                clock = clock,
                owner = owner,
            ).start(modelListingRequest(input, timeoutMillis = TIMEOUT_MILLIS), outcomes::add)

            assertTrue("inspection did not enter", entered.await(5, TimeUnit.SECONDS))
            handle.close()
            assertSame(AiTextProviderModelListingRunOutcome.Cancelled, outcomes.single())
            assertEquals(AiTextProviderModelListingAdmission.BUSY, owner.tryAcquire(other))

            clock.nowMillis += TIMEOUT_MILLIS
            scheduler.fire()
            assertEquals(AiTextProviderModelListingAdmission.FUSED, owner.tryAcquire(snapshot(input).component))
            assertEquals(1, outcomes.size)

            release.countDown()
            worker.submit {}.get(5, TimeUnit.SECONDS)
            assertEquals(AiTextProviderModelListingAdmission.ACQUIRED, owner.tryAcquire(other))
            owner.release()
            assertEquals(0, transport.bindCalls)
            assertEquals(0, transport.binding.closeCalls.get())
            assertEquals(1, scheduler.closeCalls.get())
        } finally {
            release.countDown()
            worker.shutdownNow()
            worker.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun deathDuringBlockedPrepareKeepsWatchdogAndClosesLateCallOnce() {
        val input = modelListingInput()
        val other = snapshot(modelListingInput(packageName = "other.ai.provider")).component
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val transport = FakeModelListingTransport(input).apply {
            rpc.prepareBehavior = { _, _ ->
                entered.countDown()
                assertTrue("blocked prepare was not released", release.await(5, TimeUnit.SECONDS))
            }
        }
        val scheduler = FakeModelListingDeadlineScheduler()
        val clock = FakeModelListingClock()
        val owner = modelListingOwner()
        val blocking = Executors.newSingleThreadExecutor()
        val outcomes = CopyOnWriteArrayList<AiTextProviderModelListingRunOutcome>()

        try {
            coordinator(
                transport = transport,
                blocking = blocking,
                scheduler = scheduler,
                clock = clock,
                owner = owner,
            ).start(modelListingRequest(input, timeoutMillis = TIMEOUT_MILLIS), outcomes::add)

            assertTrue("prepare did not enter", entered.await(5, TimeUnit.SECONDS))
            transport.die()
            assertSame(AiTextProviderModelListingRunOutcome.BinderDied, outcomes.single())
            assertEquals(AiTextProviderModelListingAdmission.BUSY, owner.tryAcquire(other))

            clock.nowMillis += TIMEOUT_MILLIS
            scheduler.fire()
            assertEquals(AiTextProviderModelListingAdmission.FUSED, owner.tryAcquire(snapshot(input).component))
            assertEquals(1, transport.binding.closeCalls.get())
            assertEquals(0, transport.rpc.preparedCallCloseCalls.get())

            release.countDown()
            blocking.submit {}.get(5, TimeUnit.SECONDS)
            assertEquals(AiTextProviderModelListingAdmission.ACQUIRED, owner.tryAcquire(other))
            owner.release()
            assertEquals(1, outcomes.size)
            assertEquals(1, transport.binding.closeCalls.get())
            assertEquals(1, transport.rpc.preparedCallCloseCalls.get())
            assertEquals(1, scheduler.closeCalls.get())
        } finally {
            release.countDown()
            blocking.shutdownNow()
            blocking.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun blockedDispatchTimeoutOrDeathFusesAndReleasesGateOnlyAfterUnwind() {
        BlockedTerminal.entries.forEach { terminal ->
            val input = modelListingInput()
            val other = snapshot(modelListingInput(packageName = "other.ai.provider")).component
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val transport = FakeModelListingTransport(input).apply {
                rpc.dispatchBehavior = { _, _ ->
                    entered.countDown()
                    assertTrue("blocked dispatch was not released", release.await(5, TimeUnit.SECONDS))
                }
            }
            val scheduler = FakeModelListingDeadlineScheduler()
            val clock = FakeModelListingClock()
            val owner = modelListingOwner()
            val blocking = Executors.newSingleThreadExecutor()
            val outcomes = CopyOnWriteArrayList<AiTextProviderModelListingRunOutcome>()

            try {
                coordinator(
                    transport = transport,
                    blocking = blocking,
                    scheduler = scheduler,
                    clock = clock,
                    owner = owner,
                ).start(modelListingRequest(input, timeoutMillis = TIMEOUT_MILLIS), outcomes::add)

                assertTrue("dispatch did not enter", entered.await(5, TimeUnit.SECONDS))
                if (terminal == BlockedTerminal.DEATH) {
                    transport.die()
                    assertSame(AiTextProviderModelListingRunOutcome.BinderDied, outcomes.single())
                    assertEquals(AiTextProviderModelListingAdmission.BUSY, owner.tryAcquire(other))
                }
                clock.nowMillis += TIMEOUT_MILLIS
                scheduler.fire()

                if (terminal == BlockedTerminal.TIMEOUT) {
                    assertSame(AiTextProviderModelListingRunOutcome.TimedOut, outcomes.single())
                }
                assertEquals(AiTextProviderModelListingAdmission.FUSED, owner.tryAcquire(snapshot(input).component))
                assertEquals(AiTextProviderModelListingAdmission.BUSY, owner.tryAcquire(other))
                assertEquals(1, transport.binding.closeCalls.get())
                assertEquals(1, transport.rpc.preparedCallCloseCalls.get())

                release.countDown()
                blocking.submit {}.get(5, TimeUnit.SECONDS)

                assertEquals(AiTextProviderModelListingAdmission.FUSED, owner.tryAcquire(snapshot(input).component))
                assertEquals(AiTextProviderModelListingAdmission.ACQUIRED, owner.tryAcquire(other))
                owner.release()
                assertEquals(1, outcomes.size)
                assertEquals(1, transport.binding.closeCalls.get())
                assertEquals(1, transport.rpc.preparedCallCloseCalls.get())
                assertEquals(1, scheduler.closeCalls.get())
            } finally {
                release.countDown()
                blocking.shutdownNow()
                blocking.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }

    private fun coordinator(
        transport: FakeModelListingTransport,
        worker: Executor = DIRECT,
        blocking: Executor = DIRECT,
        scheduler: FakeModelListingDeadlineScheduler = FakeModelListingDeadlineScheduler(),
        clock: AiTextProviderModelListingClock = FakeModelListingClock(),
        owner: AiTextProviderModelListingProcessOwner = modelListingOwner(),
    ) = AiTextProviderModelListingCoordinator(
        transport = transport,
        workerExecutor = worker,
        blockingExecutor = blocking,
        deadlineScheduler = scheduler,
        clock = clock,
        owner = owner,
    )

    private enum class FirstTerminal {
        CANCEL,
        DEATH,
        CALLBACK,
    }

    private enum class BlockedTerminal {
        TIMEOUT,
        DEATH,
    }

    private companion object {
        val DIRECT = Executor { command -> command.run() }
    }
}

private class FakeModelListingTransport(
    private val input: AiTextProviderDiscoveryInput,
) : AiTextProviderModelListingTransport {
    val inspectionResults = ArrayDeque<AiTextProviderDiscoverySourceResult>()
    val binding = FakeModelListingBinding()
    val rpc = FakeModelListingRpc()
    val pageResponses = ArrayDeque<PageResponse>()
    @Volatile var inspectionCalls = 0
    @Volatile var bindCalls = 0
    @Volatile var openRpcCalls = 0
    @Volatile private var bindingCallback: AiTextProviderModelListingBindingCallback? = null
    var inspectBehavior: (Int) -> Unit = {}

    override fun inspectExact(component: AiTextProviderComponent): AiTextProviderDiscoverySourceResult {
        inspectionCalls += 1
        inspectBehavior(inspectionCalls)
        return inspectionResults.pollFirst() ?: discovered(input)
    }

    override fun bindExact(
        snapshot: AiTextProviderPackageSnapshot,
        callback: AiTextProviderModelListingBindingCallback,
    ): AiTextProviderModelListingBinding {
        bindCalls += 1
        bindingCallback = callback
        callback.onConnected(FakeModelListingConnection)
        return binding
    }

    override fun openRpc(
        connection: AiTextProviderModelListingConnection,
    ): AiTextProviderModelListingRpc {
        openRpcCalls += 1
        return rpc
    }

    fun respondWithQueuedPages() {
        rpc.dispatchBehavior = { _, callback ->
            val response = pageResponses.removeFirst()
            callback.onPage(response.callingUid, response.envelope)
        }
    }

    fun die() {
        bindingCallback?.onBinderDeath()
    }
}

private object FakeModelListingConnection : AiTextProviderModelListingConnection

private class FakeModelListingBinding : AiTextProviderModelListingBinding {
    val closeCalls = AtomicInteger()
    private val closed = AtomicBoolean()

    override fun close() {
        if (closed.compareAndSet(false, true)) closeCalls.incrementAndGet()
    }
}

private class FakeModelListingRpc : AiTextProviderModelListingRpc {
    @Volatile var encodedProviderInfo: ByteArray? = AiCommonCodec.encodeProviderInfo(
        AiTextHostTestFixtures.providerInfo(),
    )
    @Volatile var encodedCapabilities: ByteArray? = AiTextCodec.encodeCapabilities(
        AiTextHostTestFixtures.capabilities(),
    )
    @Volatile var providerInfoCalls = 0
    @Volatile var capabilitiesCalls = 0
    @Volatile var prepareCallCalls = 0
    @Volatile var dispatchCalls = 0
    @Volatile var lastCallback: AiTextProviderModelListingCallbackIngress? = null
    val preparedCallCloseCalls = AtomicInteger()
    val maximumConcurrentDispatches = AtomicInteger()
    val requests = CopyOnWriteArrayList<AiModelListRequest>()
    var prepareBehavior: (Long, AiTextProviderModelListingCallbackIngress) -> Unit = { _, _ -> }
    var dispatchBehavior: (AiModelListRequest, AiTextProviderModelListingCallbackIngress) -> Unit = { _, _ -> }
    var preparedCloseBehavior: () -> Unit = {}
    private val concurrentDispatches = AtomicInteger()

    override fun getProviderInfoEnvelope(): ByteArray? {
        providerInfoCalls += 1
        return encodedProviderInfo?.copyOf()
    }

    override fun getCapabilitiesEnvelope(): ByteArray? {
        capabilitiesCalls += 1
        return encodedCapabilities?.copyOf()
    }

    override fun prepareCall(
        sequence: Long,
        callback: AiTextProviderModelListingCallbackIngress,
    ): AiTextProviderModelListingPreparedCall {
        prepareCallCalls += 1
        lastCallback = callback
        prepareBehavior(sequence, callback)
        return object : AiTextProviderModelListingPreparedCall {
            private val closed = AtomicBoolean()

            override fun dispatch(requestEnvelope: ByteArray) {
                dispatchCalls += 1
                val active = concurrentDispatches.incrementAndGet()
                maximumConcurrentDispatches.updateAndGet { current -> maxOf(current, active) }
                try {
                    val request = AiTextCodec.decodeModelListRequest(requestEnvelope.copyOf())
                    requests += request
                    dispatchBehavior(request, callback)
                } finally {
                    concurrentDispatches.decrementAndGet()
                }
            }

            override fun close() {
                if (closed.compareAndSet(false, true)) {
                    preparedCallCloseCalls.incrementAndGet()
                    preparedCloseBehavior()
                }
            }
        }
    }
}

private class FakeModelListingDeadlineScheduler : AiTextProviderModelListingDeadlineScheduler {
    @Volatile private var action: (() -> Unit)? = null
    @Volatile var scheduleCalls = 0
    val closeCalls = AtomicInteger()

    override fun schedule(delayMillis: Long, action: () -> Unit): Closeable {
        assertTrue(delayMillis > 0L)
        scheduleCalls += 1
        this.action = action
        val closed = AtomicBoolean()
        return Closeable {
            if (closed.compareAndSet(false, true)) closeCalls.incrementAndGet()
        }
    }

    fun fire() {
        requireNotNull(action).invoke()
    }
}

private class FakeModelListingClock(
    @Volatile var nowMillis: Long = 1_000L,
) : AiTextProviderModelListingClock {
    override fun elapsedRealtimeMillis(): Long = nowMillis
}

private data class PageResponse(
    val callingUid: Int,
    val envelope: ByteArray?,
)

private data class CallbackIngressCase(
    val failure: AiTextProviderModelListingFailure,
    val invoke: (AiTextProviderModelListingCallbackIngress) -> Unit,
)

private fun modelListingOwner(
    maximumProviderIdentities: Int = 16,
    maximumConsumedTokensPerProvider: Int = 256,
) = AiTextProviderModelListingProcessOwner(
    maximumProviderIdentities = maximumProviderIdentities,
    maximumConsumedTokensPerProvider = maximumConsumedTokensPerProvider,
)

private fun modelListingRequest(
    input: AiTextProviderDiscoveryInput = modelListingInput(),
    enabled: Boolean = true,
    pageSize: Int = 50,
    timeoutMillis: Long = TIMEOUT_MILLIS,
) = AiTextProviderModelListingRunRequest(
    snapshot = snapshot(input),
    pinnedProvider = pinned(input),
    environment = AiTextHostTestFixtures.environment(),
    hostCapabilities = AiTextHostTestFixtures.capabilities(),
    options = AiTextProviderModelListingRunOptions(
        enabled = enabled,
        pageSize = pageSize,
        timeoutMillis = timeoutMillis,
    ),
)

private fun pinned(
    input: AiTextProviderDiscoveryInput = modelListingInput(),
) = AiTextProviderPinnedIdentity(
    component = AiTextProviderComponent(input.packageName, input.className),
    packageUid = input.packageUid,
    versionCode = input.versionCode,
    lastUpdateTime = input.lastUpdateTime,
    signerSha256 = input.signerSha256,
    providerInfo = AiTextHostTestFixtures.providerInfo(),
    protocolVersion = AiTextProtocol.HOST_PROTOCOL_RANGE.maximum,
    negotiatedCapabilities = AiTextHostTestFixtures.capabilities(),
)

private fun snapshot(input: AiTextProviderDiscoveryInput): AiTextProviderPackageSnapshot =
    requireNotNull(AiTextProviderPackageSnapshot.from(input))

private fun discovered(input: AiTextProviderDiscoveryInput) =
    AiTextProviderDiscoverySourceResult.Completed(
        listOf(AiTextProviderDiscoverySourceEntry.Read(input)),
    )

private fun modelListingInput(
    packageName: String = "example.ai.provider",
    className: String = "AiTextService",
    packageUid: Int = PINNED_UID,
    versionCode: Long = 7L,
) = AiTextProviderDiscoveryInput(
    packageName = packageName,
    className = className,
    declaredAction = AiTextProtocol.ACTION_TEXT_PROVIDER,
    exported = true,
    applicationEnabled = true,
    serviceEnabled = true,
    requiredPermission = AiTextProtocol.PLUGIN_PERMISSION,
    packageUid = packageUid,
    versionCode = versionCode,
    lastUpdateTime = 123L,
    signerSha256 = listOf(AiTextHostTestFixtures.SIGNER_A, AiTextHostTestFixtures.SIGNER_B),
    officialSignerMatch = true,
    sameSignerAsHost = false,
    debuggable = false,
)

private fun page(
    model: AiModelInfo,
    next: String? = null,
) = AiTextCodec.encodeModelPage(
    AiModelPage(
        listingGeneration = "fixture-generation",
        models = listOf(model),
        nextPageToken = next,
    ),
)

private fun model(modelId: String) = AiModelInfo(
    modelId = modelId,
    displayName = "Fixture model",
    capabilityIds = listOf(AiTextCapabilityId.STREAMING),
    maximumContextBytes = 64L * 1024L,
    maximumOutputBytes = 64L * 1024L,
)

private const val PINNED_UID = 10_001
private const val TIMEOUT_MILLIS = 5_000L
