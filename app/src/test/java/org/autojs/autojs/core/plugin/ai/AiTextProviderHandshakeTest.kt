package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.text.api.AiTextCodec
import org.autojs.plugin.ai.text.api.AiTextProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AiTextProviderHandshakeTest {

    @Test
    fun defaultOffReturnsWithoutInspectionBindingOrExecution() {
        val transport = FakeTransport(handshakeInput())
        val worker = QueueExecutor()
        val blocking = QueueExecutor()
        val scheduler = FakeDeadlineScheduler()
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()

        coordinator(transport, worker, blocking, scheduler).start(
            handshakeRequest(enabled = false),
            outcomes::add,
        )

        assertTrue(outcomes.single() === AiTextProviderHandshakeOutcome.Disabled)
        assertEquals(0, transport.inspectionCalls)
        assertEquals(0, transport.bindCalls)
        assertEquals(0, worker.size)
        assertEquals(0, blocking.size)
        assertEquals(0, scheduler.scheduleCalls)
    }

    @Test
    fun successPerformsThreeExactInspectionsAndOneBoundedMetadataRead() {
        val transport = FakeTransport(handshakeInput())
        val scheduler = FakeDeadlineScheduler()
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()

        coordinator(transport, DIRECT, DIRECT, scheduler).start(
            handshakeRequest(),
            outcomes::add,
        )

        val selected = outcomes.single() as AiTextProviderHandshakeOutcome.Selected
        assertEquals(transport.input.packageName, selected.pinned.component.packageName)
        assertEquals(AiTextHostTestFixtures.providerInfo().providerId, selected.pinned.providerId)
        assertEquals(3, transport.inspectionCalls)
        assertEquals(1, transport.bindCalls)
        assertEquals(1, transport.openRpcCalls)
        assertEquals(1, transport.rpc.providerInfoCalls)
        assertEquals(1, transport.rpc.capabilitiesCalls)
        assertEquals(1, transport.binding.closeCalls)
        assertEquals(1, scheduler.closeCalls)
    }

    @Test
    fun preBindAdmissionRejectsDebuggableButUntrustedProviderWithoutPlatformAccess() {
        val input = handshakeInput(
            officialSignerMatch = false,
            sameSignerAsHost = false,
            debuggable = true,
        )
        val transport = FakeTransport(input)
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()

        coordinator(transport).start(handshakeRequest(input = input), outcomes::add)

        val rejected = outcomes.single() as AiTextProviderHandshakeOutcome.Rejected
        assertEquals(AiTextProviderHandshakeFailure.PRE_BIND_REJECTED, rejected.failure)
        assertEquals(AiTextProviderCandidateRejection.TRUST_REQUIRED, rejected.candidateRejection)
        assertEquals(0, transport.inspectionCalls)
        assertEquals(0, transport.bindCalls)
    }

    @Test
    fun exactInspectionMustContainOneMatchingDiscoveredRow() {
        val input = handshakeInput()
        val invalidResults = listOf(
            AiTextProviderDiscoverySourceResult.Completed(emptyList()),
            AiTextProviderDiscoverySourceResult.Completed(
                listOf(
                    AiTextProviderDiscoverySourceEntry.Read(input),
                    AiTextProviderDiscoverySourceEntry.Read(input),
                ),
            ),
            AiTextProviderDiscoverySourceResult.Completed(
                listOf(
                    AiTextProviderDiscoverySourceEntry.ReadFailed(
                        AiTextProviderDiscoveryReference(input.packageName, input.className),
                    ),
                ),
            ),
            AiTextProviderDiscoverySourceResult.QueryFailed(),
        )

        invalidResults.forEach { result ->
            val transport = FakeTransport(input).apply { inspectionResults += result }
            val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()
            coordinator(transport).start(handshakeRequest(input = input), outcomes::add)

            assertEquals(
                AiTextProviderHandshakeFailure.IDENTITY_DRIFT,
                (outcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
            )
            assertEquals(0, transport.bindCalls)
        }
    }

    @Test
    fun everyPackageSnapshotFieldParticipatesInIdentityReinspection() {
        val original = snapshot(handshakeInput())
        val drifts = listOf(
            handshakeInput(packageName = "other.provider"),
            handshakeInput(className = "OtherService"),
            handshakeInput(declaredAction = "other.action"),
            handshakeInput(exported = false),
            handshakeInput(applicationEnabled = false),
            handshakeInput(serviceEnabled = false),
            handshakeInput(requiredPermission = "other.permission"),
            handshakeInput(packageUid = 10_002),
            handshakeInput(versionCode = 8L),
            handshakeInput(lastUpdateTime = 124L),
            handshakeInput(signers = listOf(AiTextHostTestFixtures.SIGNER_B)),
            handshakeInput(officialSignerMatch = false),
            handshakeInput(sameSignerAsHost = false),
            handshakeInput(debuggable = true),
        )

        drifts.forEach { drift -> assertFalse(original.samePackageIdentityAs(snapshot(drift))) }
        assertTrue(original.samePackageIdentityAs(snapshot(handshakeInput())))
    }

    @Test
    fun providerInfoFailuresNeverReadCapabilitiesOrPublishPartialMetadata() {
        val cases = listOf(
            null to AiTextProviderHandshakeFailure.PROVIDER_INFO_MISSING,
            byteArrayOf() to AiTextProviderHandshakeFailure.PROVIDER_INFO_MISSING,
            ByteArray(AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES + 1) to
                AiTextProviderHandshakeFailure.PROVIDER_INFO_TOO_LARGE,
            byteArrayOf(1, 2, 3) to AiTextProviderHandshakeFailure.PROVIDER_INFO_INVALID,
        )

        cases.forEach { (envelope, expected) ->
            val transport = FakeTransport(handshakeInput()).apply {
                rpc.encodedProviderInfo = envelope
            }
            val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()
            coordinator(transport).start(handshakeRequest(), outcomes::add)

            assertEquals(
                expected,
                (outcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
            )
            assertEquals(1, transport.rpc.providerInfoCalls)
            assertEquals(0, transport.rpc.capabilitiesCalls)
            assertEquals(2, transport.inspectionCalls)
            assertEquals(1, transport.binding.closeCalls)
        }
    }

    @Test
    fun capabilitiesFailuresAreTypedAndDoNotReachFinalInspection() {
        val cases = listOf(
            null to AiTextProviderHandshakeFailure.CAPABILITIES_MISSING,
            byteArrayOf() to AiTextProviderHandshakeFailure.CAPABILITIES_MISSING,
            ByteArray(AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES + 1) to
                AiTextProviderHandshakeFailure.CAPABILITIES_TOO_LARGE,
            byteArrayOf(1, 2, 3) to AiTextProviderHandshakeFailure.CAPABILITIES_INVALID,
        )

        cases.forEach { (envelope, expected) ->
            val transport = FakeTransport(handshakeInput()).apply {
                rpc.encodedCapabilities = envelope
            }
            val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()
            coordinator(transport).start(handshakeRequest(), outcomes::add)

            assertEquals(
                expected,
                (outcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
            )
            assertEquals(1, transport.rpc.providerInfoCalls)
            assertEquals(1, transport.rpc.capabilitiesCalls)
            assertEquals(2, transport.inspectionCalls)
        }
    }

    @Test
    fun secondAndThirdIdentityDriftFailAtTheirExactBoundaries() {
        val original = handshakeInput()
        val drifted = handshakeInput(lastUpdateTime = original.lastUpdateTime + 1L)

        val second = FakeTransport(original).apply {
            inspectionResults += discovered(original)
            inspectionResults += discovered(drifted)
        }
        val secondOutcomes = ArrayList<AiTextProviderHandshakeOutcome>()
        coordinator(second).start(handshakeRequest(input = original), secondOutcomes::add)
        assertEquals(
            AiTextProviderHandshakeFailure.IDENTITY_DRIFT,
            (secondOutcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(2, second.inspectionCalls)
        assertEquals(0, second.openRpcCalls)

        val third = FakeTransport(original).apply {
            inspectionResults += discovered(original)
            inspectionResults += discovered(original)
            inspectionResults += discovered(drifted)
        }
        val thirdOutcomes = ArrayList<AiTextProviderHandshakeOutcome>()
        coordinator(third).start(handshakeRequest(input = original), thirdOutcomes::add)
        assertEquals(
            AiTextProviderHandshakeFailure.IDENTITY_DRIFT,
            (thirdOutcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(3, third.inspectionCalls)
        assertEquals(1, third.rpc.providerInfoCalls)
        assertEquals(1, third.rpc.capabilitiesCalls)
    }

    @Test
    fun descriptorMismatchIsRejectedBeforeEitherGetter() {
        val transport = FakeTransport(handshakeInput()).apply { descriptorMatches = false }
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()

        coordinator(transport).start(handshakeRequest(), outcomes::add)

        assertEquals(
            AiTextProviderHandshakeFailure.DESCRIPTOR_MISMATCH,
            (outcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(0, transport.rpc.providerInfoCalls)
        assertEquals(0, transport.rpc.capabilitiesCalls)
    }

    @Test
    fun finalSelectionReportsCompatibilityRejectionWithoutEquatingApkAndProviderVersions() {
        val transport = FakeTransport(handshakeInput(versionCode = 99L)).apply {
            rpc.encodedProviderInfo = AiCommonCodec.encodeProviderInfo(
                AiTextHostTestFixtures.providerInfo(
                    implementationVersionCode = 7L,
                    minimumHostVersionCode = 9_000L,
                ),
            )
        }
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()

        coordinator(transport).start(
            handshakeRequest(input = transport.input),
            outcomes::add,
        )

        val rejected = outcomes.single() as AiTextProviderHandshakeOutcome.Rejected
        assertEquals(AiTextProviderHandshakeFailure.CANDIDATE_REJECTED, rejected.failure)
        assertEquals(
            AiTextProviderCandidateRejection.HOST_VERSION_UNSUPPORTED,
            rejected.candidateRejection,
        )
        assertEquals(3, transport.inspectionCalls)
    }

    @Test
    fun timeoutAfterFirstGetterSkipsSecondGetterCleansUpOnceAndFusesComponent() {
        val input = handshakeInput()
        val transport = FakeTransport(input)
        val scheduler = FakeDeadlineScheduler()
        val gate = AiTextProviderHandshakeGate()
        val clock = FakeClock()
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()
        transport.rpc.afterProviderInfo = {
            clock.nowMillis += 5_001L
            scheduler.fire()
        }
        val coordinator = coordinator(
            transport = transport,
            scheduler = scheduler,
            gate = gate,
            clock = clock,
        )

        coordinator.start(handshakeRequest(input = input), outcomes::add)

        assertEquals(
            AiTextProviderHandshakeFailure.METADATA_TIMEOUT,
            (outcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(1, transport.rpc.providerInfoCalls)
        assertEquals(0, transport.rpc.capabilitiesCalls)
        assertEquals(1, transport.binding.closeCalls)
        assertEquals(1, scheduler.closeCalls)

        val retry = ArrayList<AiTextProviderHandshakeOutcome>()
        coordinator.start(handshakeRequest(input = input), retry::add)
        assertTrue(retry.single() === AiTextProviderHandshakeOutcome.Fused)
        assertEquals(1, transport.bindCalls)
    }

    @Test
    fun lateSchedulerStillTimesOutAndFusesWhenRpcReturnsPastDeadline() {
        val input = handshakeInput()
        val transport = FakeTransport(input)
        val scheduler = FakeDeadlineScheduler()
        val gate = AiTextProviderHandshakeGate()
        val clock = FakeClock()
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()
        transport.rpc.afterProviderInfo = { clock.nowMillis += 5_001L }

        coordinator(
            transport = transport,
            scheduler = scheduler,
            gate = gate,
            clock = clock,
        ).start(handshakeRequest(input = input), outcomes::add)

        assertEquals(
            AiTextProviderHandshakeFailure.METADATA_TIMEOUT,
            (outcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(1, transport.rpc.providerInfoCalls)
        assertEquals(0, transport.rpc.capabilitiesCalls)
        assertEquals(0, scheduler.fireCalls)
        assertEquals(1, scheduler.closeCalls)
        assertEquals(
            AiTextProviderHandshakeAdmission.FUSED,
            gate.tryAcquire(snapshot(input).component),
        )
    }

    @Test
    fun workerQueueDelayCountsTowardTheAbsoluteDeadlineEvenWhenTimerIsLate() {
        val transport = FakeTransport(handshakeInput())
        val worker = QueueExecutor()
        val clock = FakeClock()
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()

        coordinator(transport, worker = worker, clock = clock).start(handshakeRequest(), outcomes::add)
        clock.nowMillis += 5_001L
        worker.runAll()

        assertEquals(
            AiTextProviderHandshakeFailure.BIND_TIMEOUT,
            (outcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(0, transport.inspectionCalls)
        assertEquals(0, transport.bindCalls)
    }

    @Test
    fun expiredAbsoluteDeadlineBeatsLateCancellation() {
        val input = handshakeInput()
        val transport = FakeTransport(input)
        val worker = QueueExecutor()
        val scheduler = FakeDeadlineScheduler()
        val gate = AiTextProviderHandshakeGate()
        val clock = FakeClock()
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()
        val coordinator = coordinator(
            transport = transport,
            worker = worker,
            scheduler = scheduler,
            gate = gate,
            clock = clock,
        )
        val handle = coordinator.start(handshakeRequest(input = input), outcomes::add)

        clock.nowMillis += 5_001L
        handle.close()
        worker.runAll()

        assertEquals(
            AiTextProviderHandshakeFailure.BIND_TIMEOUT,
            (outcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(0, transport.inspectionCalls)
        assertEquals(0, transport.bindCalls)
        assertEquals(1, scheduler.closeCalls)
        assertEquals(
            AiTextProviderHandshakeAdmission.ACQUIRED,
            gate.tryAcquire(snapshot(input).component),
        )
        gate.release()
    }

    @Test
    fun blockedBinderTimesOutAndKeepsGlobalGateUntilTheCallReturns() {
        val input = handshakeInput()
        val transport = FakeTransport(input)
        val scheduler = FakeDeadlineScheduler()
        val gate = AiTextProviderHandshakeGate()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocking = Executors.newSingleThreadExecutor()
        val clock = FakeClock()
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()
        transport.rpc.afterProviderInfo = {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        val coordinator = coordinator(
            transport = transport,
            blocking = blocking,
            scheduler = scheduler,
            gate = gate,
            clock = clock,
        )

        coordinator.start(handshakeRequest(input = input), outcomes::add)
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        clock.nowMillis += 5_001L
        scheduler.fire()

        assertEquals(
            AiTextProviderHandshakeFailure.METADATA_TIMEOUT,
            (outcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(1, transport.binding.closeCalls)
        assertEquals(
            AiTextProviderHandshakeAdmission.FUSED,
            gate.tryAcquire(snapshot(input).component),
        )
        val other = snapshot(handshakeInput(packageName = "other.ai.provider")).component
        assertEquals(AiTextProviderHandshakeAdmission.BUSY, gate.tryAcquire(other))

        release.countDown()
        blocking.shutdown()
        assertTrue(blocking.awaitTermination(2, TimeUnit.SECONDS))
        assertEquals(AiTextProviderHandshakeAdmission.ACQUIRED, gate.tryAcquire(other))
        gate.release()
        assertEquals(0, transport.rpc.capabilitiesCalls)
    }

    @Test
    fun cancelledBlockedRpcIsFusedByTheRetainedDeadlineWatchdog() {
        val input = handshakeInput()
        val transport = FakeTransport(input)
        val scheduler = FakeDeadlineScheduler()
        val gate = AiTextProviderHandshakeGate()
        val clock = FakeClock()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocking = Executors.newSingleThreadExecutor()
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()
        transport.rpc.afterProviderInfo = {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        val coordinator = coordinator(
            transport = transport,
            blocking = blocking,
            scheduler = scheduler,
            gate = gate,
            clock = clock,
        )
        val handle = coordinator.start(handshakeRequest(input = input), outcomes::add)

        assertTrue(entered.await(2, TimeUnit.SECONDS))
        handle.close()
        assertTrue(outcomes.single() === AiTextProviderHandshakeOutcome.Cancelled)
        assertEquals(1, transport.binding.closeCalls)
        assertEquals(0, scheduler.closeCalls)
        assertEquals(
            AiTextProviderHandshakeAdmission.BUSY,
            gate.tryAcquire(snapshot(handshakeInput(packageName = "other.ai.provider")).component),
        )

        clock.nowMillis += 5_001L
        scheduler.fire()

        assertEquals(
            AiTextProviderHandshakeAdmission.FUSED,
            gate.tryAcquire(snapshot(input).component),
        )
        assertEquals(1, scheduler.closeCalls)
        assertEquals(1, outcomes.size)

        release.countDown()
        blocking.shutdown()
        assertTrue(blocking.awaitTermination(2, TimeUnit.SECONDS))
        val other = snapshot(handshakeInput(packageName = "other.ai.provider")).component
        assertEquals(AiTextProviderHandshakeAdmission.ACQUIRED, gate.tryAcquire(other))
        gate.release()
        assertEquals(AiTextProviderHandshakeAdmission.FUSED, gate.tryAcquire(snapshot(input).component))
        assertEquals(0, transport.rpc.capabilitiesCalls)
    }

    @Test
    fun cancelledRpcReturningPastDeadlineFusesWithoutASecondOutcomeOrSchedulerFire() {
        val input = handshakeInput()
        val transport = FakeTransport(input)
        val scheduler = FakeDeadlineScheduler()
        val gate = AiTextProviderHandshakeGate()
        val clock = FakeClock()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocking = Executors.newSingleThreadExecutor()
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()
        transport.rpc.afterProviderInfo = {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        val coordinator = coordinator(
            transport = transport,
            blocking = blocking,
            scheduler = scheduler,
            gate = gate,
            clock = clock,
        )

        try {
            val handle = coordinator.start(handshakeRequest(input = input), outcomes::add)
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            handle.close()
            assertTrue(outcomes.single() === AiTextProviderHandshakeOutcome.Cancelled)

            clock.nowMillis += 5_001L
            release.countDown()
            blocking.shutdown()
            assertTrue(blocking.awaitTermination(2, TimeUnit.SECONDS))

            assertEquals(1, outcomes.size)
            assertEquals(0, scheduler.fireCalls)
            assertEquals(1, scheduler.closeCalls)
            assertEquals(
                AiTextProviderHandshakeAdmission.FUSED,
                gate.tryAcquire(snapshot(input).component),
            )
            val other = snapshot(handshakeInput(packageName = "other.ai.provider")).component
            assertEquals(AiTextProviderHandshakeAdmission.ACQUIRED, gate.tryAcquire(other))
            gate.release()
            assertEquals(0, transport.rpc.capabilitiesCalls)
        } finally {
            release.countDown()
            blocking.shutdown()
            blocking.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    @Test
    fun finalOperationCannotReleaseGateBeforeTerminalSettlementCompletes() {
        val input = handshakeInput()
        val transport = FakeTransport(input)
        val scheduler = FakeDeadlineScheduler()
        val gate = AiTextProviderHandshakeGate()
        val clock = FakeClock()
        val rpcEntered = CountDownLatch(1)
        val releaseRpc = CountDownLatch(1)
        val closeEntered = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val blocking = Executors.newSingleThreadExecutor()
        val timer = Executors.newSingleThreadExecutor()
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()
        transport.rpc.afterProviderInfo = {
            rpcEntered.countDown()
            releaseRpc.await(5, TimeUnit.SECONDS)
        }
        transport.binding.duringClose = {
            closeEntered.countDown()
            releaseClose.await(5, TimeUnit.SECONDS)
        }

        try {
            coordinator(
                transport = transport,
                blocking = blocking,
                scheduler = scheduler,
                gate = gate,
                clock = clock,
            ).start(handshakeRequest(input = input), outcomes::add)
            assertTrue(rpcEntered.await(2, TimeUnit.SECONDS))

            clock.nowMillis += 5_001L
            timer.execute(scheduler::fire)
            assertTrue(closeEntered.await(2, TimeUnit.SECONDS))

            releaseRpc.countDown()
            blocking.shutdown()
            assertTrue(blocking.awaitTermination(2, TimeUnit.SECONDS))

            val other = snapshot(handshakeInput(packageName = "other.ai.provider")).component
            assertEquals(AiTextProviderHandshakeAdmission.BUSY, gate.tryAcquire(other))
            assertTrue(outcomes.isEmpty())

            releaseClose.countDown()
            timer.shutdown()
            assertTrue(timer.awaitTermination(2, TimeUnit.SECONDS))

            assertEquals(
                AiTextProviderHandshakeFailure.METADATA_TIMEOUT,
                (outcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
            )
            assertEquals(AiTextProviderHandshakeAdmission.FUSED, gate.tryAcquire(snapshot(input).component))
            assertEquals(AiTextProviderHandshakeAdmission.ACQUIRED, gate.tryAcquire(other))
            gate.release()
        } finally {
            releaseRpc.countDown()
            releaseClose.countDown()
            blocking.shutdown()
            timer.shutdown()
            blocking.awaitTermination(2, TimeUnit.SECONDS)
            timer.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    @Test
    fun normalTerminalReleasesTheGateBeforeCallbackReentry() {
        val input = handshakeInput()
        val transport = FakeTransport(input)
        val scheduler = FakeDeadlineScheduler()
        val gate = AiTextProviderHandshakeGate()
        val coordinator = coordinator(
            transport = transport,
            scheduler = scheduler,
            gate = gate,
        )
        val first = ArrayList<AiTextProviderHandshakeOutcome>()
        val reentered = ArrayList<AiTextProviderHandshakeOutcome>()

        coordinator.start(handshakeRequest(input = input)) { outcome ->
            first += outcome
            coordinator.start(handshakeRequest(input = input), reentered::add)
        }

        assertTrue(first.single() is AiTextProviderHandshakeOutcome.Selected)
        assertTrue(reentered.single() is AiTextProviderHandshakeOutcome.Selected)
        assertEquals(2, transport.bindCalls)
        assertEquals(2, transport.binding.closeCalls)
    }

    @Test
    fun finalInspectionTimeoutDoesNotFuseTheComponent() {
        val input = handshakeInput()
        val transport = FakeTransport(input)
        val scheduler = FakeDeadlineScheduler()
        val gate = AiTextProviderHandshakeGate()
        val clock = FakeClock()
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()
        transport.afterInspection = { inspectionNumber ->
            if (inspectionNumber == 3) clock.nowMillis += 5_001L
        }

        coordinator(
            transport = transport,
            scheduler = scheduler,
            gate = gate,
            clock = clock,
        ).start(handshakeRequest(input = input), outcomes::add)

        assertEquals(
            AiTextProviderHandshakeFailure.METADATA_TIMEOUT,
            (outcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(3, transport.inspectionCalls)
        assertEquals(1, transport.rpc.providerInfoCalls)
        assertEquals(1, transport.rpc.capabilitiesCalls)
        assertEquals(1, transport.binding.closeCalls)
        assertEquals(
            AiTextProviderHandshakeAdmission.ACQUIRED,
            gate.tryAcquire(snapshot(input).component),
        )
        gate.release()
    }

    @Test
    fun blockingExecutorRejectionIsTypedAndDoesNotLeakTheGate() {
        val transport = FakeTransport(handshakeInput())
        val rejecting = Executor { throw IllegalStateException("rejected") }
        val gate = AiTextProviderHandshakeGate()
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()

        coordinator(transport, blocking = rejecting, gate = gate).start(handshakeRequest(), outcomes::add)

        assertEquals(
            AiTextProviderHandshakeFailure.EXECUTOR_REJECTED,
            (outcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
        )
        val other = snapshot(handshakeInput(packageName = "other.ai.provider")).component
        assertEquals(AiTextProviderHandshakeAdmission.ACQUIRED, gate.tryAcquire(other))
        gate.release()
    }

    @Test
    fun bindingAndRemoteFailuresRemainTypedAndCleanUpOnce() {
        val bindingFailures = listOf(
            AiTextProviderHandshakeFailure.UNEXPECTED_COMPONENT,
            AiTextProviderHandshakeFailure.NULL_BINDING,
            AiTextProviderHandshakeFailure.DISCONNECTED,
            AiTextProviderHandshakeFailure.BINDER_DIED,
        )
        bindingFailures.forEach { expected ->
            val transport = FakeTransport(handshakeInput()).apply { bindingFailure = expected }
            val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()
            coordinator(transport).start(handshakeRequest(), outcomes::add)
            assertEquals(
                expected,
                (outcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
            )
            assertEquals(1, transport.binding.closeCalls)
        }

        listOf(
            SecurityException() to AiTextProviderHandshakeFailure.REMOTE_SECURITY,
            IllegalStateException() to AiTextProviderHandshakeFailure.REMOTE_FAILURE,
        ).forEach { (error, expected) ->
            val transport = FakeTransport(handshakeInput()).apply {
                rpc.providerInfoFailure = error
            }
            val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()
            coordinator(transport).start(handshakeRequest(), outcomes::add)
            assertEquals(
                expected,
                (outcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
            )
            assertEquals(0, transport.rpc.capabilitiesCalls)
        }
    }

    @Test
    fun cancellationBeforeWorkerExecutionHasOneTerminalAndNoPlatformAccess() {
        val transport = FakeTransport(handshakeInput())
        val worker = QueueExecutor()
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()
        val handle = coordinator(transport, worker = worker).start(handshakeRequest(), outcomes::add)

        handle.close()
        worker.runAll()
        handle.close()

        assertTrue(outcomes.single() === AiTextProviderHandshakeOutcome.Cancelled)
        assertEquals(0, transport.inspectionCalls)
        assertEquals(0, transport.bindCalls)
    }

    @Test
    fun duplicateConnectionHasOneTerminalAndClosesLateInstalledBinding() {
        val transport = FakeTransport(handshakeInput()).apply { duplicateConnection = true }
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()

        coordinator(transport).start(handshakeRequest(), outcomes::add)

        assertEquals(
            AiTextProviderHandshakeFailure.DUPLICATE_CONNECTION,
            (outcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(1, transport.binding.closeCalls)
        assertEquals(1, outcomes.size)
    }

    @Test
    fun processGateRejectsConcurrentAttemptUntilTheFirstAttemptEnds() {
        val input = handshakeInput()
        val worker = QueueExecutor()
        val gate = AiTextProviderHandshakeGate()
        val first = ArrayList<AiTextProviderHandshakeOutcome>()
        val second = ArrayList<AiTextProviderHandshakeOutcome>()
        val coordinator = coordinator(FakeTransport(input), worker = worker, gate = gate)

        val firstHandle = coordinator.start(handshakeRequest(input = input), first::add)
        coordinator.start(handshakeRequest(input = input), second::add)

        assertTrue(second.single() === AiTextProviderHandshakeOutcome.Busy)
        firstHandle.close()
        assertTrue(first.single() === AiTextProviderHandshakeOutcome.Cancelled)
    }

    @Test
    fun workerRejectionHasOneTypedOutcomeAndReleasesTheGate() {
        val transport = FakeTransport(handshakeInput())
        val rejecting = Executor { throw IllegalStateException("rejected") }
        val gate = AiTextProviderHandshakeGate()
        val outcomes = ArrayList<AiTextProviderHandshakeOutcome>()

        coordinator(transport, worker = rejecting, gate = gate).start(handshakeRequest(), outcomes::add)

        assertEquals(
            AiTextProviderHandshakeFailure.EXECUTOR_REJECTED,
            (outcomes.single() as AiTextProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(
            AiTextProviderHandshakeAdmission.ACQUIRED,
            gate.tryAcquire(handshakeRequest().snapshot.component),
        )
        gate.release()
    }

    private fun coordinator(
        transport: FakeTransport,
        worker: Executor = DIRECT,
        blocking: Executor = DIRECT,
        scheduler: FakeDeadlineScheduler = FakeDeadlineScheduler(),
        gate: AiTextProviderHandshakeGate = AiTextProviderHandshakeGate(),
        clock: AiTextProviderHandshakeClock = FakeClock(),
    ) = AiTextProviderHandshakeCoordinator(
        transport = transport,
        workerExecutor = worker,
        blockingExecutor = blocking,
        deadlineScheduler = scheduler,
        gate = gate,
        clock = clock,
    )

    private companion object {
        val DIRECT = Executor { command -> command.run() }
    }
}

private class FakeTransport(
    val input: AiTextProviderDiscoveryInput,
) : AiTextProviderHandshakeTransport {
    val inspectionResults = ArrayDeque<AiTextProviderDiscoverySourceResult>()
    val rpc = FakeMetadataRpc()
    val binding = FakeBinding()
    var descriptorMatches = true
    var duplicateConnection = false
    var bindingFailure: AiTextProviderHandshakeFailure? = null
    var afterInspection: ((Int) -> Unit)? = null
    var inspectionCalls = 0
    var bindCalls = 0
    var openRpcCalls = 0

    override fun inspectExact(component: AiTextProviderComponent): AiTextProviderDiscoverySourceResult {
        inspectionCalls += 1
        val result = inspectionResults.pollFirst() ?: discovered(input)
        afterInspection?.invoke(inspectionCalls)
        return result
    }

    override fun bindExact(
        snapshot: AiTextProviderPackageSnapshot,
        callback: AiTextProviderBindingCallback,
    ): AiTextProviderBinding {
        bindCalls += 1
        val failure = bindingFailure
        if (failure == null) {
            callback.onConnected(FakeRawConnection)
            if (duplicateConnection) callback.onConnected(FakeRawConnection)
        } else if (failure == AiTextProviderHandshakeFailure.BINDER_DIED) {
            callback.onBinderDeath()
        } else {
            callback.onBindingFailure(failure)
        }
        return binding
    }

    override fun openMetadataRpc(connection: AiTextProviderRawConnection): AiTextProviderMetadataRpc {
        openRpcCalls += 1
        if (!descriptorMatches) throw AiTextProviderDescriptorMismatchException()
        return rpc
    }
}

private object FakeRawConnection : AiTextProviderRawConnection

private class FakeBinding : AiTextProviderBinding {
    var duringClose: (() -> Unit)? = null
    var closeCalls = 0

    override fun close() {
        closeCalls += 1
        duringClose?.invoke()
    }
}

private class FakeMetadataRpc : AiTextProviderMetadataRpc {
    var encodedProviderInfo: ByteArray? = AiCommonCodec.encodeProviderInfo(
        AiTextHostTestFixtures.providerInfo(),
    )
    var encodedCapabilities: ByteArray? = AiTextCodec.encodeCapabilities(
        AiTextHostTestFixtures.capabilities(),
    )
    var providerInfoCalls = 0
    var capabilitiesCalls = 0
    var afterProviderInfo: (() -> Unit)? = null
    var providerInfoFailure: RuntimeException? = null

    override fun getProviderInfoEnvelope(): ByteArray? {
        providerInfoCalls += 1
        providerInfoFailure?.let { throw it }
        afterProviderInfo?.invoke()
        return encodedProviderInfo
    }

    override fun getCapabilitiesEnvelope(): ByteArray? {
        capabilitiesCalls += 1
        return encodedCapabilities
    }
}

private class FakeDeadlineScheduler : AiTextProviderHandshakeDeadlineScheduler {
    private var action: (() -> Unit)? = null
    var scheduleCalls = 0
    var closeCalls = 0
    var fireCalls = 0

    override fun schedule(delayMillis: Long, action: () -> Unit): Closeable {
        assertTrue(delayMillis > 0L)
        scheduleCalls += 1
        this.action = action
        val closed = AtomicBoolean(false)
        return Closeable {
            if (closed.compareAndSet(false, true)) closeCalls += 1
        }
    }

    fun fire() {
        assertNotNull(action)
        fireCalls += 1
        action?.invoke()
    }
}

private class QueueExecutor : Executor {
    private val commands = ArrayDeque<Runnable>()
    val size: Int
        get() = commands.size

    override fun execute(command: Runnable) {
        commands += command
    }

    fun runAll() {
        while (commands.isNotEmpty()) commands.removeFirst().run()
    }
}

private class FakeClock(
    var nowMillis: Long = 1_000L,
) : AiTextProviderHandshakeClock {
    override fun elapsedRealtimeMillis(): Long = nowMillis
}

private fun handshakeRequest(
    input: AiTextProviderDiscoveryInput = handshakeInput(),
    enabled: Boolean = true,
) = AiTextProviderHandshakeRequest(
    snapshot = snapshot(input),
    environment = AiTextHostTestFixtures.environment(),
    hostCapabilities = AiTextHostTestFixtures.capabilities(),
    options = AiTextProviderHandshakeOptions(enabled = enabled, timeoutMillis = 5_000L),
)

private fun snapshot(input: AiTextProviderDiscoveryInput): AiTextProviderPackageSnapshot =
    requireNotNull(AiTextProviderPackageSnapshot.from(input))

private fun discovered(input: AiTextProviderDiscoveryInput) =
    AiTextProviderDiscoverySourceResult.Completed(
        listOf(AiTextProviderDiscoverySourceEntry.Read(input)),
    )

private fun handshakeInput(
    packageName: String = "example.ai.provider",
    className: String = "AiTextService",
    declaredAction: String = AiTextProtocol.ACTION_TEXT_PROVIDER,
    exported: Boolean = true,
    applicationEnabled: Boolean = true,
    serviceEnabled: Boolean = true,
    requiredPermission: String? = AiTextProtocol.PLUGIN_PERMISSION,
    packageUid: Int = 10_001,
    versionCode: Long = 7L,
    lastUpdateTime: Long = 123L,
    signers: Collection<String> = listOf(AiTextHostTestFixtures.SIGNER_A),
    officialSignerMatch: Boolean = true,
    sameSignerAsHost: Boolean = true,
    debuggable: Boolean = false,
) = AiTextProviderDiscoveryInput(
    packageName = packageName,
    className = className,
    declaredAction = declaredAction,
    exported = exported,
    applicationEnabled = applicationEnabled,
    serviceEnabled = serviceEnabled,
    requiredPermission = requiredPermission,
    packageUid = packageUid,
    versionCode = versionCode,
    lastUpdateTime = lastUpdateTime,
    signerSha256 = signers,
    officialSignerMatch = officialSignerMatch,
    sameSignerAsHost = sameSignerAsHost,
    debuggable = debuggable,
)
