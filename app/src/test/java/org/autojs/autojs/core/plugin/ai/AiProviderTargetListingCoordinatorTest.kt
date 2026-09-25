package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.provider.api.AiTargetInfo
import org.autojs.plugin.ai.provider.api.AiTargetListRequest
import org.autojs.plugin.ai.provider.api.AiTargetPage
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiProviderCodec
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
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

class AiProviderTargetListingCoordinatorTest {

    @Test
    fun defaultOffHasNoTransportSchedulerOrOwnerSideEffects() {
        val input = targetListingInput()
        val transport = FakeTargetListingTransport(input)
        val scheduler = FakeTargetListingDeadlineScheduler()
        val owner = targetListingOwner()
        val outcomes = CopyOnWriteArrayList<AiProviderTargetListingRunOutcome>()

        coordinator(transport, scheduler = scheduler, owner = owner).start(
            targetListingRequest(input = input, enabled = false),
            outcomes::add,
        )

        assertSame(AiProviderTargetListingRunOutcome.Disabled, outcomes.single())
        assertEquals(0, scheduler.scheduleCalls)
        assertEquals(0, transport.inspectionCalls)
        assertEquals(0, transport.bindCalls)
        assertEquals(0, transport.openRpcCalls)
        assertEquals(0, transport.rpc.dispatchCalls)
        assertEquals(
            AiProviderTargetListingAdmission.ACQUIRED,
            owner.tryAcquire(snapshot(input).component),
        )
        owner.release()
    }

    @Test
    fun singlePageCompletesFromThePinnedSameBinderTranscript() {
        val input = targetListingInput()
        val transport = FakeTargetListingTransport(input).apply {
            pageResponses += PageResponse(PINNED_UID, page(target("fixture.single")))
            respondWithQueuedPages()
        }
        val scheduler = FakeTargetListingDeadlineScheduler()
        val outcomes = CopyOnWriteArrayList<AiProviderTargetListingRunOutcome>()

        coordinator(transport, scheduler = scheduler).start(
            targetListingRequest(input),
            outcomes::add,
        )

        val completed = outcomes.single() as AiProviderTargetListingRunOutcome.Completed
        assertEquals(listOf("fixture.single"), completed.catalog.targets.map { it.modelId })
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
        val input = targetListingInput()
        val transport = FakeTargetListingTransport(input).apply {
            pageResponses += PageResponse(PINNED_UID, page(target("fixture.model-1"), next = "token-1"))
            pageResponses += PageResponse(PINNED_UID, page(target("fixture.model-2"), next = "token-2"))
            pageResponses += PageResponse(PINNED_UID, page(target("fixture.model-3"), next = "token-3"))
            pageResponses += PageResponse(PINNED_UID, page(target("fixture.model-4")))
            respondWithQueuedPages()
        }
        val outcomes = CopyOnWriteArrayList<AiProviderTargetListingRunOutcome>()

        coordinator(transport).start(
            targetListingRequest(input = input, pageSize = 1),
            outcomes::add,
        )

        val completed = outcomes.single() as AiProviderTargetListingRunOutcome.Completed
        assertEquals(
            listOf("fixture.model-1", "fixture.model-2", "fixture.model-3", "fixture.model-4"),
            completed.catalog.targets.map { it.modelId },
        )
        assertEquals(listOf(null, "token-1", "token-2", "token-3"), transport.rpc.requests.map { it.pageToken })
        assertTrue(transport.rpc.requests.all { it.pageSize == 1 })
        assertEquals(4, transport.rpc.dispatchCalls)
        assertEquals(1, transport.rpc.maximumConcurrentDispatches.get())
        assertEquals(4, transport.rpc.preparedCallCloseCalls.get())
    }

    @Test
    fun tokenCapacityAndReplayEvidenceAreIsolatedByCompletePinnedProviderIdentity() {
        val owner = targetListingOwner(
            maximumProviderIdentities = 2,
            maximumConsumedTokensPerProvider = 1,
        )
        val firstInput = targetListingInput()
        val firstTransport = FakeTargetListingTransport(firstInput).apply {
            pageResponses += PageResponse(firstInput.packageUid, page(target("fixture.first-1"), "shared-token"))
            pageResponses += PageResponse(firstInput.packageUid, page(target("fixture.first-2"), "first-overflow"))
            respondWithQueuedPages()
        }
        val firstOutcomes = CopyOnWriteArrayList<AiProviderTargetListingRunOutcome>()

        coordinator(firstTransport, owner = owner).start(
            targetListingRequest(input = firstInput, pageSize = 1),
            firstOutcomes::add,
        )

        val firstRejected = firstOutcomes.single() as AiProviderTargetListingRunOutcome.Rejected
        assertEquals(AiProviderTargetListingRunFailure.TRANSCRIPT_REJECTED, firstRejected.failure)
        assertEquals(AiProviderTargetListingFailure.PAGINATION_INVALID, firstRejected.transcriptFailure)
        assertEquals(2, firstTransport.rpc.dispatchCalls)

        val secondInput = targetListingInput(
            packageName = "second.ai.provider",
            packageUid = PINNED_UID + 1,
        )
        val secondTransport = FakeTargetListingTransport(secondInput).apply {
            pageResponses += PageResponse(secondInput.packageUid, page(target("fixture.second-1"), "shared-token"))
            pageResponses += PageResponse(secondInput.packageUid, page(target("fixture.second-2")))
            respondWithQueuedPages()
        }
        val secondOutcomes = CopyOnWriteArrayList<AiProviderTargetListingRunOutcome>()

        coordinator(secondTransport, owner = owner).start(
            targetListingRequest(input = secondInput, pageSize = 1),
            secondOutcomes::add,
        )

        val secondCompleted = secondOutcomes.single() as AiProviderTargetListingRunOutcome.Completed
        assertEquals(listOf("fixture.second-1", "fixture.second-2"), secondCompleted.catalog.targets.map { it.modelId })
        assertEquals(2, secondTransport.rpc.dispatchCalls)
        assertNull(
            owner.tokenLedgerFor(
                pinned(targetListingInput(packageName = "third.ai.provider", packageUid = PINNED_UID + 2)),
            ),
        )
        assertTrue(
            runCatching {
                AiProviderTargetListingProcessOwner(
                    maximumProviderIdentities = 2,
                    maximumConsumedTokensPerProvider =
                        AiProviderLimits.MAX_TARGET_PAGE_TOKEN_LEDGER_ENTRIES,
                )
            }.isFailure,
        )
    }

    @Test
    fun metadataRevalidationMismatchStopsBeforeAnyTargetListDispatch() {
        val input = targetListingInput()
        val transport = FakeTargetListingTransport(input).apply {
            rpc.encodedProviderInfo = AiCommonCodec.encodeProviderInfo(
                AiProviderHostTestFixtures.providerInfo(providerId = "different.provider"),
            )
        }
        val outcomes = CopyOnWriteArrayList<AiProviderTargetListingRunOutcome>()

        coordinator(transport).start(targetListingRequest(input), outcomes::add)

        val rejected = outcomes.single() as AiProviderTargetListingRunOutcome.Rejected
        assertEquals(AiProviderTargetListingRunFailure.PINNED_METADATA_MISMATCH, rejected.failure)
        assertEquals(1, transport.openRpcCalls)
        assertEquals(1, transport.rpc.providerInfoCalls)
        assertEquals(1, transport.rpc.capabilitiesCalls)
        assertEquals(0, transport.rpc.prepareCallCalls)
        assertEquals(0, transport.rpc.dispatchCalls)
    }

    @Test
    fun exactIdentityDriftImmediatelyBeforeDispatchStopsWithoutPreparingACall() {
        val input = targetListingInput()
        val drifted = targetListingInput(versionCode = input.versionCode + 1L)
        val transport = FakeTargetListingTransport(input).apply {
            repeat(3) { inspectionResults += discovered(input) }
            inspectionResults += discovered(drifted)
        }
        val outcomes = CopyOnWriteArrayList<AiProviderTargetListingRunOutcome>()

        coordinator(transport).start(targetListingRequest(input), outcomes::add)

        val rejected = outcomes.single() as AiProviderTargetListingRunOutcome.Rejected
        assertEquals(AiProviderTargetListingRunFailure.IDENTITY_DRIFT, rejected.failure)
        assertEquals(4, transport.inspectionCalls)
        assertEquals(0, transport.rpc.prepareCallCalls)
        assertEquals(0, transport.rpc.dispatchCalls)
    }

    @Test
    fun callbackIngressRejectsUidAndTypedOversizeBeforeCopyOrReinspection() {
        val cases = listOf(
            CallbackIngressCase(
                failure = AiProviderTargetListingFailure.CALLBACK_UID_MISMATCH,
                invoke = { callback ->
                    callback.onPage(PINNED_UID + 1, ByteArray(AiProviderLimits.MAX_TARGET_PAGE_ENVELOPE_BYTES + 1))
                },
            ),
            CallbackIngressCase(
                failure = AiProviderTargetListingFailure.PAGE_TOO_LARGE,
                invoke = { callback ->
                    callback.onPage(PINNED_UID, ByteArray(AiProviderLimits.MAX_TARGET_PAGE_ENVELOPE_BYTES + 1))
                },
            ),
            CallbackIngressCase(
                failure = AiProviderTargetListingFailure.ERROR_TOO_LARGE,
                invoke = { callback ->
                    callback.onFailed(PINNED_UID, ByteArray(AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES + 1))
                },
            ),
        )
        cases.forEach { case ->
            val input = targetListingInput()
            val transport = FakeTargetListingTransport(input)
            val outcomes = CopyOnWriteArrayList<AiProviderTargetListingRunOutcome>()
            coordinator(transport).start(targetListingRequest(input), outcomes::add)

            case.invoke(requireNotNull(transport.rpc.lastCallback))

            val rejected = outcomes.single() as AiProviderTargetListingRunOutcome.Rejected
            assertEquals(AiProviderTargetListingRunFailure.TRANSCRIPT_REJECTED, rejected.failure)
            assertEquals(case.failure, rejected.transcriptFailure)
            assertEquals(4, transport.inspectionCalls)
            assertEquals(1, transport.rpc.dispatchCalls)
        }
    }

    @Test
    fun thirdCallbackIsRejectedByFloodGateWhileOneIsActiveAndOnePending() {
        val input = targetListingInput()
        val admissionEntered = CountDownLatch(1)
        val releaseAdmission = CountDownLatch(1)
        val dispatchReturned = CountDownLatch(1)
        val transport = FakeTargetListingTransport(input).apply {
            inspectBehavior = { inspection ->
                if (inspection == 5) {
                    admissionEntered.countDown()
                    assertTrue("callback inspection was not released", releaseAdmission.await(5, TimeUnit.SECONDS))
                }
            }
            rpc.dispatchBehavior = { _, _ -> dispatchReturned.countDown() }
        }
        val blocking = Executors.newSingleThreadExecutor()
        val outcomes = CopyOnWriteArrayList<AiProviderTargetListingRunOutcome>()

        try {
            coordinator(transport, blocking = blocking).start(targetListingRequest(input), outcomes::add)
            assertTrue("dispatch did not return", dispatchReturned.await(5, TimeUnit.SECONDS))
            blocking.submit {}.get(5, TimeUnit.SECONDS)
            val callback = requireNotNull(transport.rpc.lastCallback)
            val envelope = page(target("fixture.flood"))

            callback.onPage(PINNED_UID, envelope)
            assertTrue("callback admission did not enter", admissionEntered.await(5, TimeUnit.SECONDS))
            callback.onPage(PINNED_UID, envelope)
            callback.onPage(PINNED_UID, envelope)

            val rejected = outcomes.single() as AiProviderTargetListingRunOutcome.Rejected
            assertEquals(AiProviderTargetListingRunFailure.CALLBACK_FLOOD, rejected.failure)
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
            val input = targetListingInput()
            val transport = FakeTargetListingTransport(input)
            val outcomes = CopyOnWriteArrayList<AiProviderTargetListingRunOutcome>()
            val handle = coordinator(transport).start(targetListingRequest(input), outcomes::add)
            val callback = assertNotNull(transport.rpc.lastCallback).let {
                requireNotNull(transport.rpc.lastCallback)
            }

            when (first) {
                FirstTerminal.CANCEL -> handle.close()
                FirstTerminal.DEATH -> transport.die()
                FirstTerminal.CALLBACK -> callback.onPage(PINNED_UID, page(target("fixture.race")))
            }
            handle.close()
            transport.die()
            callback.onPage(PINNED_UID, page(target("fixture.late")))

            assertEquals("first=$first", 1, outcomes.size)
            when (first) {
                FirstTerminal.CANCEL -> assertSame(AiProviderTargetListingRunOutcome.Cancelled, outcomes.single())
                FirstTerminal.DEATH -> assertSame(AiProviderTargetListingRunOutcome.BinderDied, outcomes.single())
                FirstTerminal.CALLBACK -> assertTrue(outcomes.single() is AiProviderTargetListingRunOutcome.Completed)
            }
            assertEquals("first=$first", 1, transport.binding.closeCalls.get())
            assertEquals("first=$first", 1, transport.rpc.preparedCallCloseCalls.get())
        }
    }

    @Test
    fun completedOutcomeCallbackCanImmediatelyStartAnotherListingWithoutBusy() {
        val input = targetListingInput()
        val transport = FakeTargetListingTransport(input).apply {
            pageResponses += PageResponse(PINNED_UID, page(target("fixture.first")))
            pageResponses += PageResponse(PINNED_UID, page(target("fixture.second")))
            respondWithQueuedPages()
        }
        val owner = targetListingOwner()
        val first = CopyOnWriteArrayList<AiProviderTargetListingRunOutcome>()
        val second = CopyOnWriteArrayList<AiProviderTargetListingRunOutcome>()
        val coordinator = coordinator(transport, owner = owner)

        coordinator.start(targetListingRequest(input)) { outcome ->
            first += outcome
            coordinator.start(targetListingRequest(input), second::add)
        }

        assertTrue(first.single() is AiProviderTargetListingRunOutcome.Completed)
        assertTrue(second.single() is AiProviderTargetListingRunOutcome.Completed)
        assertEquals(2, transport.rpc.dispatchCalls)
    }

    @Test
    fun completedOutcomeCallbackBlockedPastDeadlineCannotFuseProvider() {
        val input = targetListingInput()
        val callbackEntered = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val transport = FakeTargetListingTransport(input).apply {
            pageResponses += PageResponse(PINNED_UID, page(target("fixture.blocked-outcome")))
            respondWithQueuedPages()
        }
        val scheduler = FakeTargetListingDeadlineScheduler()
        val clock = FakeTargetListingClock()
        val owner = targetListingOwner()
        val worker = Executors.newSingleThreadExecutor()
        val outcomes = CopyOnWriteArrayList<AiProviderTargetListingRunOutcome>()

        try {
            coordinator(
                transport = transport,
                worker = worker,
                scheduler = scheduler,
                clock = clock,
                owner = owner,
            ).start(targetListingRequest(input, timeoutMillis = TIMEOUT_MILLIS)) { outcome ->
                outcomes += outcome
                callbackEntered.countDown()
                assertTrue("outcome callback was not released", releaseCallback.await(5, TimeUnit.SECONDS))
            }

            assertTrue("outcome callback did not enter", callbackEntered.await(5, TimeUnit.SECONDS))
            assertTrue(outcomes.single() is AiProviderTargetListingRunOutcome.Completed)
            clock.nowMillis += TIMEOUT_MILLIS
            scheduler.fire()

            assertEquals(
                AiProviderTargetListingAdmission.ACQUIRED,
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
        val input = targetListingInput()
        val closeEntered = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val transport = FakeTargetListingTransport(input).apply {
            pageResponses += PageResponse(PINNED_UID, page(target("fixture.completed-before-unwind")))
            respondWithQueuedPages()
            rpc.preparedCloseBehavior = {
                closeEntered.countDown()
                releaseClose.await(5, TimeUnit.SECONDS)
            }
        }
        val scheduler = FakeTargetListingDeadlineScheduler()
        val clock = FakeTargetListingClock()
        val owner = targetListingOwner()
        val worker = Executors.newSingleThreadExecutor()
        val outcomes = CopyOnWriteArrayList<AiProviderTargetListingRunOutcome>()

        try {
            coordinator(
                transport = transport,
                worker = worker,
                scheduler = scheduler,
                clock = clock,
                owner = owner,
            ).start(targetListingRequest(input, timeoutMillis = TIMEOUT_MILLIS), outcomes::add)

            assertTrue("prepared-call close did not enter", closeEntered.await(5, TimeUnit.SECONDS))
            assertTrue(outcomes.isEmpty())
            clock.nowMillis += TIMEOUT_MILLIS
            scheduler.fire()

            assertEquals(
                AiProviderTargetListingAdmission.BUSY,
                owner.tryAcquire(snapshot(input).component),
            )
            assertTrue(outcomes.isEmpty())
            assertEquals(1, scheduler.closeCalls.get())

            releaseClose.countDown()
            worker.submit {}.get(5, TimeUnit.SECONDS)
            assertTrue(outcomes.single() is AiProviderTargetListingRunOutcome.Completed)
            assertEquals(
                AiProviderTargetListingAdmission.ACQUIRED,
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
        val input = targetListingInput()
        val other = snapshot(targetListingInput(packageName = "other.ai.provider")).component
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val transport = FakeTargetListingTransport(input).apply {
            inspectBehavior = { inspection ->
                if (inspection == 1) {
                    entered.countDown()
                    assertTrue("blocked inspection was not released", release.await(5, TimeUnit.SECONDS))
                }
            }
        }
        val scheduler = FakeTargetListingDeadlineScheduler()
        val clock = FakeTargetListingClock()
        val owner = targetListingOwner()
        val worker = Executors.newSingleThreadExecutor()
        val outcomes = CopyOnWriteArrayList<AiProviderTargetListingRunOutcome>()

        try {
            val handle = coordinator(
                transport = transport,
                worker = worker,
                scheduler = scheduler,
                clock = clock,
                owner = owner,
            ).start(targetListingRequest(input, timeoutMillis = TIMEOUT_MILLIS), outcomes::add)

            assertTrue("inspection did not enter", entered.await(5, TimeUnit.SECONDS))
            handle.close()
            assertSame(AiProviderTargetListingRunOutcome.Cancelled, outcomes.single())
            assertEquals(AiProviderTargetListingAdmission.BUSY, owner.tryAcquire(other))

            clock.nowMillis += TIMEOUT_MILLIS
            scheduler.fire()
            assertEquals(AiProviderTargetListingAdmission.FUSED, owner.tryAcquire(snapshot(input).component))
            assertEquals(1, outcomes.size)

            release.countDown()
            worker.submit {}.get(5, TimeUnit.SECONDS)
            assertEquals(AiProviderTargetListingAdmission.ACQUIRED, owner.tryAcquire(other))
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
        val input = targetListingInput()
        val other = snapshot(targetListingInput(packageName = "other.ai.provider")).component
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val transport = FakeTargetListingTransport(input).apply {
            rpc.prepareBehavior = { _, _ ->
                entered.countDown()
                assertTrue("blocked prepare was not released", release.await(5, TimeUnit.SECONDS))
            }
        }
        val scheduler = FakeTargetListingDeadlineScheduler()
        val clock = FakeTargetListingClock()
        val owner = targetListingOwner()
        val blocking = Executors.newSingleThreadExecutor()
        val outcomes = CopyOnWriteArrayList<AiProviderTargetListingRunOutcome>()

        try {
            coordinator(
                transport = transport,
                blocking = blocking,
                scheduler = scheduler,
                clock = clock,
                owner = owner,
            ).start(targetListingRequest(input, timeoutMillis = TIMEOUT_MILLIS), outcomes::add)

            assertTrue("prepare did not enter", entered.await(5, TimeUnit.SECONDS))
            transport.die()
            assertSame(AiProviderTargetListingRunOutcome.BinderDied, outcomes.single())
            assertEquals(AiProviderTargetListingAdmission.BUSY, owner.tryAcquire(other))

            clock.nowMillis += TIMEOUT_MILLIS
            scheduler.fire()
            assertEquals(AiProviderTargetListingAdmission.FUSED, owner.tryAcquire(snapshot(input).component))
            assertEquals(1, transport.binding.closeCalls.get())
            assertEquals(0, transport.rpc.preparedCallCloseCalls.get())

            release.countDown()
            blocking.submit {}.get(5, TimeUnit.SECONDS)
            assertEquals(AiProviderTargetListingAdmission.ACQUIRED, owner.tryAcquire(other))
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
            val input = targetListingInput()
            val other = snapshot(targetListingInput(packageName = "other.ai.provider")).component
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val transport = FakeTargetListingTransport(input).apply {
                rpc.dispatchBehavior = { _, _ ->
                    entered.countDown()
                    assertTrue("blocked dispatch was not released", release.await(5, TimeUnit.SECONDS))
                }
            }
            val scheduler = FakeTargetListingDeadlineScheduler()
            val clock = FakeTargetListingClock()
            val owner = targetListingOwner()
            val blocking = Executors.newSingleThreadExecutor()
            val outcomes = CopyOnWriteArrayList<AiProviderTargetListingRunOutcome>()

            try {
                coordinator(
                    transport = transport,
                    blocking = blocking,
                    scheduler = scheduler,
                    clock = clock,
                    owner = owner,
                ).start(targetListingRequest(input, timeoutMillis = TIMEOUT_MILLIS), outcomes::add)

                assertTrue("dispatch did not enter", entered.await(5, TimeUnit.SECONDS))
                if (terminal == BlockedTerminal.DEATH) {
                    transport.die()
                    assertSame(AiProviderTargetListingRunOutcome.BinderDied, outcomes.single())
                    assertEquals(AiProviderTargetListingAdmission.BUSY, owner.tryAcquire(other))
                }
                clock.nowMillis += TIMEOUT_MILLIS
                scheduler.fire()

                if (terminal == BlockedTerminal.TIMEOUT) {
                    assertSame(AiProviderTargetListingRunOutcome.TimedOut, outcomes.single())
                }
                assertEquals(AiProviderTargetListingAdmission.FUSED, owner.tryAcquire(snapshot(input).component))
                assertEquals(AiProviderTargetListingAdmission.BUSY, owner.tryAcquire(other))
                assertEquals(1, transport.binding.closeCalls.get())
                assertEquals(1, transport.rpc.preparedCallCloseCalls.get())

                release.countDown()
                blocking.submit {}.get(5, TimeUnit.SECONDS)

                assertEquals(AiProviderTargetListingAdmission.FUSED, owner.tryAcquire(snapshot(input).component))
                assertEquals(AiProviderTargetListingAdmission.ACQUIRED, owner.tryAcquire(other))
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
        transport: FakeTargetListingTransport,
        worker: Executor = DIRECT,
        blocking: Executor = DIRECT,
        scheduler: FakeTargetListingDeadlineScheduler = FakeTargetListingDeadlineScheduler(),
        clock: AiProviderTargetListingClock = FakeTargetListingClock(),
        owner: AiProviderTargetListingProcessOwner = targetListingOwner(),
    ) = AiProviderTargetListingCoordinator(
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

private class FakeTargetListingTransport(
    private val input: AiProviderDiscoveryInput,
) : AiProviderTargetListingTransport {
    val inspectionResults = ArrayDeque<AiProviderDiscoverySourceResult>()
    val binding = FakeTargetListingBinding()
    val rpc = FakeTargetListingRpc()
    val pageResponses = ArrayDeque<PageResponse>()
    @Volatile var inspectionCalls = 0
    @Volatile var bindCalls = 0
    @Volatile var openRpcCalls = 0
    @Volatile private var bindingCallback: AiProviderTargetListingBindingCallback? = null
    var inspectBehavior: (Int) -> Unit = {}

    override fun inspectExact(component: AiProviderComponent): AiProviderDiscoverySourceResult {
        inspectionCalls += 1
        inspectBehavior(inspectionCalls)
        return inspectionResults.pollFirst() ?: discovered(input)
    }

    override fun bindExact(
        snapshot: AiProviderPackageSnapshot,
        callback: AiProviderTargetListingBindingCallback,
    ): AiProviderTargetListingBinding {
        bindCalls += 1
        bindingCallback = callback
        callback.onConnected(FakeTargetListingConnection)
        return binding
    }

    override fun openRpc(
        connection: AiProviderTargetListingConnection,
    ): AiProviderTargetListingRpc {
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

private object FakeTargetListingConnection : AiProviderTargetListingConnection

private class FakeTargetListingBinding : AiProviderTargetListingBinding {
    val closeCalls = AtomicInteger()
    private val closed = AtomicBoolean()

    override fun close() {
        if (closed.compareAndSet(false, true)) closeCalls.incrementAndGet()
    }
}

private class FakeTargetListingRpc : AiProviderTargetListingRpc {
    @Volatile var encodedProviderInfo: ByteArray? = AiCommonCodec.encodeProviderInfo(
        AiProviderHostTestFixtures.providerInfo(),
    )
    @Volatile var encodedCapabilities: ByteArray? = AiProviderCodec.encodeCapabilities(
        AiProviderHostTestFixtures.capabilities(),
    )
    @Volatile var providerInfoCalls = 0
    @Volatile var capabilitiesCalls = 0
    @Volatile var prepareCallCalls = 0
    @Volatile var dispatchCalls = 0
    @Volatile var lastCallback: AiProviderTargetListingCallbackIngress? = null
    val preparedCallCloseCalls = AtomicInteger()
    val maximumConcurrentDispatches = AtomicInteger()
    val requests = CopyOnWriteArrayList<AiTargetListRequest>()
    var prepareBehavior: (Long, AiProviderTargetListingCallbackIngress) -> Unit = { _, _ -> }
    var dispatchBehavior: (AiTargetListRequest, AiProviderTargetListingCallbackIngress) -> Unit = { _, _ -> }
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
        callback: AiProviderTargetListingCallbackIngress,
    ): AiProviderTargetListingPreparedCall {
        prepareCallCalls += 1
        lastCallback = callback
        prepareBehavior(sequence, callback)
        return object : AiProviderTargetListingPreparedCall {
            private val closed = AtomicBoolean()

            override fun dispatch(requestEnvelope: ByteArray) {
                dispatchCalls += 1
                val active = concurrentDispatches.incrementAndGet()
                maximumConcurrentDispatches.updateAndGet { current -> maxOf(current, active) }
                try {
                    val request = AiProviderCodec.decodeTargetListRequest(requestEnvelope.copyOf())
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

private class FakeTargetListingDeadlineScheduler : AiProviderTargetListingDeadlineScheduler {
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

private class FakeTargetListingClock(
    @Volatile var nowMillis: Long = 1_000L,
) : AiProviderTargetListingClock {
    override fun elapsedRealtimeMillis(): Long = nowMillis
}

private data class PageResponse(
    val callingUid: Int,
    val envelope: ByteArray?,
)

private data class CallbackIngressCase(
    val failure: AiProviderTargetListingFailure,
    val invoke: (AiProviderTargetListingCallbackIngress) -> Unit,
)

private fun targetListingOwner(
    maximumProviderIdentities: Int = 16,
    maximumConsumedTokensPerProvider: Int = 256,
) = AiProviderTargetListingProcessOwner(
    maximumProviderIdentities = maximumProviderIdentities,
    maximumConsumedTokensPerProvider = maximumConsumedTokensPerProvider,
)

private fun targetListingRequest(
    input: AiProviderDiscoveryInput = targetListingInput(),
    enabled: Boolean = true,
    pageSize: Int = 50,
    timeoutMillis: Long = TIMEOUT_MILLIS,
) = AiProviderTargetListingRunRequest(
    snapshot = snapshot(input),
    pinnedProvider = pinned(input),
    environment = AiProviderHostTestFixtures.environment(),
    hostCapabilities = AiProviderHostTestFixtures.capabilities(),
    options = AiProviderTargetListingRunOptions(
        enabled = enabled,
        pageSize = pageSize,
        timeoutMillis = timeoutMillis,
    ),
)

private fun pinned(
    input: AiProviderDiscoveryInput = targetListingInput(),
) = AiProviderPinnedIdentity(
    component = AiProviderComponent(input.packageName, input.className),
    packageUid = input.packageUid,
    versionCode = input.versionCode,
    lastUpdateTime = input.lastUpdateTime,
    signerSha256 = input.signerSha256,
    providerInfo = AiProviderHostTestFixtures.providerInfo(),
    protocolVersion = AiProviderProtocol.PROTOCOL_V2,
    negotiatedCapabilities = AiProviderHostTestFixtures.capabilities(),
)

private fun snapshot(input: AiProviderDiscoveryInput): AiProviderPackageSnapshot =
    requireNotNull(AiProviderPackageSnapshot.from(input))

private fun discovered(input: AiProviderDiscoveryInput) =
    AiProviderDiscoverySourceResult.Completed(
        listOf(AiProviderDiscoverySourceEntry.Read(input)),
    )

private fun targetListingInput(
    packageName: String = "example.ai.provider",
    className: String = "AiProviderService",
    packageUid: Int = PINNED_UID,
    versionCode: Long = 7L,
) = AiProviderDiscoveryInput(
    packageName = packageName,
    className = className,
    declaredAction = AiProviderProtocol.ACTION_PROVIDER,
    exported = true,
    applicationEnabled = true,
    serviceEnabled = true,
    requiredPermission = AiProviderProtocol.PLUGIN_PERMISSION,
    packageUid = packageUid,
    versionCode = versionCode,
    lastUpdateTime = 123L,
    signerSha256 = listOf(AiProviderHostTestFixtures.SIGNER_A, AiProviderHostTestFixtures.SIGNER_B),
    officialSignerMatch = true,
    sameSignerAsHost = false,
    debuggable = false,
)

private fun page(
    target: AiTargetInfo,
    next: String? = null,
) = AiProviderCodec.encodeTargetPage(
    AiTargetPage(
        catalogGeneration = "fixture-generation",
        targets = listOf(target),
        nextPageToken = next,
    ),
)

private fun target(modelId: String) = AiProviderHostTestFixtures.target(
    targetId = "fixture.provider:${modelId.substringAfterLast('.')}",
    modelId = modelId,
    displayName = "Fixture model",
    capabilityIds = listOf(AiProviderCapabilityId.STREAMING),
    maximumContextBytes = 64L * 1024L,
    maximumOutputBytes = 64L * 1024L,
)

private const val PINNED_UID = 10_001
private const val TIMEOUT_MILLIS = 5_000L
