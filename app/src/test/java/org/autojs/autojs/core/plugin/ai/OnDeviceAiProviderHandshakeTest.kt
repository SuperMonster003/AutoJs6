package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCodec
import org.autojs.plugin.ondeviceai.api.OnDeviceAiProtocol
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

class OnDeviceAiProviderHandshakeTest {

    @Test
    fun defaultOffReturnsWithoutInspectionBindingOrExecution() {
        val transport = FakeTransport(handshakeInput())
        val worker = QueueExecutor()
        val blocking = QueueExecutor()
        val scheduler = FakeDeadlineScheduler()
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()

        coordinator(transport, worker, blocking, scheduler).start(
            handshakeRequest(enabled = false),
            outcomes::add,
        )

        assertTrue(outcomes.single() === OnDeviceAiProviderHandshakeOutcome.Disabled)
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
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()

        coordinator(transport, DIRECT, DIRECT, scheduler).start(
            handshakeRequest(),
            outcomes::add,
        )

        val selected = outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Selected
        assertEquals(transport.input.packageName, selected.pinned.component.packageName)
        assertEquals(OnDeviceAiHostTestFixtures.providerInfo().providerId, selected.pinned.providerId)
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
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()

        coordinator(transport).start(handshakeRequest(input = input), outcomes::add)

        val rejected = outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected
        assertEquals(OnDeviceAiProviderHandshakeFailure.PRE_BIND_REJECTED, rejected.failure)
        assertEquals(OnDeviceAiProviderCandidateRejection.TRUST_REQUIRED, rejected.candidateRejection)
        assertEquals(0, transport.inspectionCalls)
        assertEquals(0, transport.bindCalls)
    }

    @Test
    fun exactInspectionMustContainOneMatchingDiscoveredRow() {
        val input = handshakeInput()
        val invalidResults = listOf(
            OnDeviceAiProviderDiscoverySourceResult.Completed(emptyList()),
            OnDeviceAiProviderDiscoverySourceResult.Completed(
                listOf(
                    OnDeviceAiProviderDiscoverySourceEntry.Read(input),
                    OnDeviceAiProviderDiscoverySourceEntry.Read(input),
                ),
            ),
            OnDeviceAiProviderDiscoverySourceResult.Completed(
                listOf(
                    OnDeviceAiProviderDiscoverySourceEntry.ReadFailed(
                        OnDeviceAiProviderDiscoveryReference(input.packageName, input.className),
                    ),
                ),
            ),
            OnDeviceAiProviderDiscoverySourceResult.QueryFailed(),
        )

        invalidResults.forEach { result ->
            val transport = FakeTransport(input).apply { inspectionResults += result }
            val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
            coordinator(transport).start(handshakeRequest(input = input), outcomes::add)

            assertEquals(
                OnDeviceAiProviderHandshakeFailure.IDENTITY_DRIFT,
                (outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
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
            handshakeInput(signers = listOf(OnDeviceAiHostTestFixtures.SIGNER_B)),
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
            null to OnDeviceAiProviderHandshakeFailure.PROVIDER_INFO_MISSING,
            byteArrayOf() to OnDeviceAiProviderHandshakeFailure.PROVIDER_INFO_MISSING,
            ByteArray(AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES + 1) to
                OnDeviceAiProviderHandshakeFailure.PROVIDER_INFO_TOO_LARGE,
            byteArrayOf(1, 2, 3) to OnDeviceAiProviderHandshakeFailure.PROVIDER_INFO_INVALID,
        )

        cases.forEach { (envelope, expected) ->
            val transport = FakeTransport(handshakeInput()).apply {
                rpc.encodedProviderInfo = envelope
            }
            val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
            coordinator(transport).start(handshakeRequest(), outcomes::add)

            assertEquals(
                expected,
                (outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
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
            null to OnDeviceAiProviderHandshakeFailure.CAPABILITIES_MISSING,
            byteArrayOf() to OnDeviceAiProviderHandshakeFailure.CAPABILITIES_MISSING,
            ByteArray(AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES + 1) to
                OnDeviceAiProviderHandshakeFailure.CAPABILITIES_TOO_LARGE,
            byteArrayOf(1, 2, 3) to OnDeviceAiProviderHandshakeFailure.CAPABILITIES_INVALID,
        )

        cases.forEach { (envelope, expected) ->
            val transport = FakeTransport(handshakeInput()).apply {
                rpc.encodedCapabilities = envelope
            }
            val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
            coordinator(transport).start(handshakeRequest(), outcomes::add)

            assertEquals(
                expected,
                (outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
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
        val secondOutcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
        coordinator(second).start(handshakeRequest(input = original), secondOutcomes::add)
        assertEquals(
            OnDeviceAiProviderHandshakeFailure.IDENTITY_DRIFT,
            (secondOutcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(2, second.inspectionCalls)
        assertEquals(0, second.openRpcCalls)

        val third = FakeTransport(original).apply {
            inspectionResults += discovered(original)
            inspectionResults += discovered(original)
            inspectionResults += discovered(drifted)
        }
        val thirdOutcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
        coordinator(third).start(handshakeRequest(input = original), thirdOutcomes::add)
        assertEquals(
            OnDeviceAiProviderHandshakeFailure.IDENTITY_DRIFT,
            (thirdOutcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(3, third.inspectionCalls)
        assertEquals(1, third.rpc.providerInfoCalls)
        assertEquals(1, third.rpc.capabilitiesCalls)
    }

    @Test
    fun descriptorMismatchIsRejectedBeforeEitherGetter() {
        val transport = FakeTransport(handshakeInput()).apply { descriptorMatches = false }
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()

        coordinator(transport).start(handshakeRequest(), outcomes::add)

        assertEquals(
            OnDeviceAiProviderHandshakeFailure.DESCRIPTOR_MISMATCH,
            (outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(0, transport.rpc.providerInfoCalls)
        assertEquals(0, transport.rpc.capabilitiesCalls)
    }

    @Test
    fun finalSelectionReportsCompatibilityRejectionWithoutEquatingApkAndProviderVersions() {
        val transport = FakeTransport(handshakeInput(versionCode = 99L)).apply {
            rpc.encodedProviderInfo = AiCommonCodec.encodeProviderInfo(
                OnDeviceAiHostTestFixtures.providerInfo(
                    implementationVersionCode = 7L,
                    minimumHostVersionCode = 9_000L,
                ),
            )
        }
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()

        coordinator(transport).start(
            handshakeRequest(input = transport.input),
            outcomes::add,
        )

        val rejected = outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected
        assertEquals(OnDeviceAiProviderHandshakeFailure.CANDIDATE_REJECTED, rejected.failure)
        assertEquals(
            OnDeviceAiProviderCandidateRejection.HOST_VERSION_UNSUPPORTED,
            rejected.candidateRejection,
        )
        assertEquals(3, transport.inspectionCalls)
    }

    @Test
    fun timeoutAfterFirstGetterSkipsSecondGetterCleansUpOnceAndFusesComponent() {
        val input = handshakeInput()
        val transport = FakeTransport(input)
        val scheduler = FakeDeadlineScheduler()
        val gate = OnDeviceAiProviderHandshakeGate()
        val clock = FakeClock()
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
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
            OnDeviceAiProviderHandshakeFailure.METADATA_TIMEOUT,
            (outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(1, transport.rpc.providerInfoCalls)
        assertEquals(0, transport.rpc.capabilitiesCalls)
        assertEquals(1, transport.binding.closeCalls)
        assertEquals(1, scheduler.closeCalls)

        val retry = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
        coordinator.start(handshakeRequest(input = input), retry::add)
        assertTrue(retry.single() === OnDeviceAiProviderHandshakeOutcome.Fused)
        assertEquals(1, transport.bindCalls)
    }

    @Test
    fun lateSchedulerStillTimesOutAndFusesWhenRpcReturnsPastDeadline() {
        val input = handshakeInput()
        val transport = FakeTransport(input)
        val scheduler = FakeDeadlineScheduler()
        val gate = OnDeviceAiProviderHandshakeGate()
        val clock = FakeClock()
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
        transport.rpc.afterProviderInfo = { clock.nowMillis += 5_001L }

        coordinator(
            transport = transport,
            scheduler = scheduler,
            gate = gate,
            clock = clock,
        ).start(handshakeRequest(input = input), outcomes::add)

        assertEquals(
            OnDeviceAiProviderHandshakeFailure.METADATA_TIMEOUT,
            (outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(1, transport.rpc.providerInfoCalls)
        assertEquals(0, transport.rpc.capabilitiesCalls)
        assertEquals(0, scheduler.fireCalls)
        assertEquals(1, scheduler.closeCalls)
        assertEquals(
            OnDeviceAiProviderHandshakeAdmission.FUSED,
            gate.tryAcquire(snapshot(input).component),
        )
    }

    @Test
    fun workerQueueDelayCountsTowardTheAbsoluteDeadlineEvenWhenTimerIsLate() {
        val transport = FakeTransport(handshakeInput())
        val worker = QueueExecutor()
        val clock = FakeClock()
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()

        coordinator(transport, worker = worker, clock = clock).start(handshakeRequest(), outcomes::add)
        clock.nowMillis += 5_001L
        worker.runAll()

        assertEquals(
            OnDeviceAiProviderHandshakeFailure.BIND_TIMEOUT,
            (outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
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
        val gate = OnDeviceAiProviderHandshakeGate()
        val clock = FakeClock()
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
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
            OnDeviceAiProviderHandshakeFailure.BIND_TIMEOUT,
            (outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(0, transport.inspectionCalls)
        assertEquals(0, transport.bindCalls)
        assertEquals(1, scheduler.closeCalls)
        assertEquals(
            OnDeviceAiProviderHandshakeAdmission.ACQUIRED,
            gate.tryAcquire(snapshot(input).component),
        )
        gate.release()
    }

    @Test
    fun blockedBinderTimesOutAndKeepsGlobalGateUntilTheCallReturns() {
        val input = handshakeInput()
        val transport = FakeTransport(input)
        val scheduler = FakeDeadlineScheduler()
        val gate = OnDeviceAiProviderHandshakeGate()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocking = Executors.newSingleThreadExecutor()
        val clock = FakeClock()
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
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
            OnDeviceAiProviderHandshakeFailure.METADATA_TIMEOUT,
            (outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(1, transport.binding.closeCalls)
        assertEquals(
            OnDeviceAiProviderHandshakeAdmission.FUSED,
            gate.tryAcquire(snapshot(input).component),
        )
        val other = snapshot(handshakeInput(packageName = "other.ai.provider")).component
        assertEquals(OnDeviceAiProviderHandshakeAdmission.BUSY, gate.tryAcquire(other))

        release.countDown()
        blocking.shutdown()
        assertTrue(blocking.awaitTermination(2, TimeUnit.SECONDS))
        assertEquals(OnDeviceAiProviderHandshakeAdmission.ACQUIRED, gate.tryAcquire(other))
        gate.release()
        assertEquals(0, transport.rpc.capabilitiesCalls)
    }

    @Test
    fun cancelledBlockedRpcIsFusedByTheRetainedDeadlineWatchdog() {
        val input = handshakeInput()
        val transport = FakeTransport(input)
        val scheduler = FakeDeadlineScheduler()
        val gate = OnDeviceAiProviderHandshakeGate()
        val clock = FakeClock()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocking = Executors.newSingleThreadExecutor()
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
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
        assertTrue(outcomes.single() === OnDeviceAiProviderHandshakeOutcome.Cancelled)
        assertEquals(1, transport.binding.closeCalls)
        assertEquals(0, scheduler.closeCalls)
        assertEquals(
            OnDeviceAiProviderHandshakeAdmission.BUSY,
            gate.tryAcquire(snapshot(handshakeInput(packageName = "other.ai.provider")).component),
        )

        clock.nowMillis += 5_001L
        scheduler.fire()

        assertEquals(
            OnDeviceAiProviderHandshakeAdmission.FUSED,
            gate.tryAcquire(snapshot(input).component),
        )
        assertEquals(1, scheduler.closeCalls)
        assertEquals(1, outcomes.size)

        release.countDown()
        blocking.shutdown()
        assertTrue(blocking.awaitTermination(2, TimeUnit.SECONDS))
        val other = snapshot(handshakeInput(packageName = "other.ai.provider")).component
        assertEquals(OnDeviceAiProviderHandshakeAdmission.ACQUIRED, gate.tryAcquire(other))
        gate.release()
        assertEquals(OnDeviceAiProviderHandshakeAdmission.FUSED, gate.tryAcquire(snapshot(input).component))
        assertEquals(0, transport.rpc.capabilitiesCalls)
    }

    @Test
    fun cancelledRpcReturningPastDeadlineFusesWithoutASecondOutcomeOrSchedulerFire() {
        val input = handshakeInput()
        val transport = FakeTransport(input)
        val scheduler = FakeDeadlineScheduler()
        val gate = OnDeviceAiProviderHandshakeGate()
        val clock = FakeClock()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocking = Executors.newSingleThreadExecutor()
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
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
            assertTrue(outcomes.single() === OnDeviceAiProviderHandshakeOutcome.Cancelled)

            clock.nowMillis += 5_001L
            release.countDown()
            blocking.shutdown()
            assertTrue(blocking.awaitTermination(2, TimeUnit.SECONDS))

            assertEquals(1, outcomes.size)
            assertEquals(0, scheduler.fireCalls)
            assertEquals(1, scheduler.closeCalls)
            assertEquals(
                OnDeviceAiProviderHandshakeAdmission.FUSED,
                gate.tryAcquire(snapshot(input).component),
            )
            val other = snapshot(handshakeInput(packageName = "other.ai.provider")).component
            assertEquals(OnDeviceAiProviderHandshakeAdmission.ACQUIRED, gate.tryAcquire(other))
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
        val gate = OnDeviceAiProviderHandshakeGate()
        val clock = FakeClock()
        val rpcEntered = CountDownLatch(1)
        val releaseRpc = CountDownLatch(1)
        val closeEntered = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val blocking = Executors.newSingleThreadExecutor()
        val timer = Executors.newSingleThreadExecutor()
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
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
            assertEquals(OnDeviceAiProviderHandshakeAdmission.BUSY, gate.tryAcquire(other))
            assertTrue(outcomes.isEmpty())

            releaseClose.countDown()
            timer.shutdown()
            assertTrue(timer.awaitTermination(2, TimeUnit.SECONDS))

            assertEquals(
                OnDeviceAiProviderHandshakeFailure.METADATA_TIMEOUT,
                (outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
            )
            assertEquals(OnDeviceAiProviderHandshakeAdmission.FUSED, gate.tryAcquire(snapshot(input).component))
            assertEquals(OnDeviceAiProviderHandshakeAdmission.ACQUIRED, gate.tryAcquire(other))
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
        val gate = OnDeviceAiProviderHandshakeGate()
        val coordinator = coordinator(
            transport = transport,
            scheduler = scheduler,
            gate = gate,
        )
        val first = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
        val reentered = ArrayList<OnDeviceAiProviderHandshakeOutcome>()

        coordinator.start(handshakeRequest(input = input)) { outcome ->
            first += outcome
            coordinator.start(handshakeRequest(input = input), reentered::add)
        }

        assertTrue(first.single() is OnDeviceAiProviderHandshakeOutcome.Selected)
        assertTrue(reentered.single() is OnDeviceAiProviderHandshakeOutcome.Selected)
        assertEquals(2, transport.bindCalls)
        assertEquals(2, transport.binding.closeCalls)
    }

    @Test
    fun finalInspectionTimeoutDoesNotFuseTheComponent() {
        val input = handshakeInput()
        val transport = FakeTransport(input)
        val scheduler = FakeDeadlineScheduler()
        val gate = OnDeviceAiProviderHandshakeGate()
        val clock = FakeClock()
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
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
            OnDeviceAiProviderHandshakeFailure.METADATA_TIMEOUT,
            (outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(3, transport.inspectionCalls)
        assertEquals(1, transport.rpc.providerInfoCalls)
        assertEquals(1, transport.rpc.capabilitiesCalls)
        assertEquals(1, transport.binding.closeCalls)
        assertEquals(
            OnDeviceAiProviderHandshakeAdmission.ACQUIRED,
            gate.tryAcquire(snapshot(input).component),
        )
        gate.release()
    }

    @Test
    fun blockingExecutorRejectionIsTypedAndDoesNotLeakTheGate() {
        val transport = FakeTransport(handshakeInput())
        val rejecting = Executor { throw IllegalStateException("rejected") }
        val gate = OnDeviceAiProviderHandshakeGate()
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()

        coordinator(transport, blocking = rejecting, gate = gate).start(handshakeRequest(), outcomes::add)

        assertEquals(
            OnDeviceAiProviderHandshakeFailure.EXECUTOR_REJECTED,
            (outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
        )
        val other = snapshot(handshakeInput(packageName = "other.ai.provider")).component
        assertEquals(OnDeviceAiProviderHandshakeAdmission.ACQUIRED, gate.tryAcquire(other))
        gate.release()
    }

    @Test
    fun bindingAndRemoteFailuresRemainTypedAndCleanUpOnce() {
        val bindingFailures = listOf(
            OnDeviceAiProviderHandshakeFailure.UNEXPECTED_COMPONENT,
            OnDeviceAiProviderHandshakeFailure.NULL_BINDING,
            OnDeviceAiProviderHandshakeFailure.DISCONNECTED,
            OnDeviceAiProviderHandshakeFailure.BINDER_DIED,
        )
        bindingFailures.forEach { expected ->
            val transport = FakeTransport(handshakeInput()).apply { bindingFailure = expected }
            val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
            coordinator(transport).start(handshakeRequest(), outcomes::add)
            assertEquals(
                expected,
                (outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
            )
            assertEquals(1, transport.binding.closeCalls)
        }

        listOf(
            SecurityException() to OnDeviceAiProviderHandshakeFailure.REMOTE_SECURITY,
            IllegalStateException() to OnDeviceAiProviderHandshakeFailure.REMOTE_FAILURE,
        ).forEach { (error, expected) ->
            val transport = FakeTransport(handshakeInput()).apply {
                rpc.providerInfoFailure = error
            }
            val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
            coordinator(transport).start(handshakeRequest(), outcomes::add)
            assertEquals(
                expected,
                (outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
            )
            assertEquals(0, transport.rpc.capabilitiesCalls)
        }
    }

    @Test
    fun cancellationBeforeWorkerExecutionHasOneTerminalAndNoPlatformAccess() {
        val transport = FakeTransport(handshakeInput())
        val worker = QueueExecutor()
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
        val handle = coordinator(transport, worker = worker).start(handshakeRequest(), outcomes::add)

        handle.close()
        worker.runAll()
        handle.close()

        assertTrue(outcomes.single() === OnDeviceAiProviderHandshakeOutcome.Cancelled)
        assertEquals(0, transport.inspectionCalls)
        assertEquals(0, transport.bindCalls)
    }

    @Test
    fun duplicateConnectionHasOneTerminalAndClosesLateInstalledBinding() {
        val transport = FakeTransport(handshakeInput()).apply { duplicateConnection = true }
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()

        coordinator(transport).start(handshakeRequest(), outcomes::add)

        assertEquals(
            OnDeviceAiProviderHandshakeFailure.DUPLICATE_CONNECTION,
            (outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(1, transport.binding.closeCalls)
        assertEquals(1, outcomes.size)
    }

    @Test
    fun processGateRejectsConcurrentAttemptUntilTheFirstAttemptEnds() {
        val input = handshakeInput()
        val worker = QueueExecutor()
        val gate = OnDeviceAiProviderHandshakeGate()
        val first = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
        val second = ArrayList<OnDeviceAiProviderHandshakeOutcome>()
        val coordinator = coordinator(FakeTransport(input), worker = worker, gate = gate)

        val firstHandle = coordinator.start(handshakeRequest(input = input), first::add)
        coordinator.start(handshakeRequest(input = input), second::add)

        assertTrue(second.single() === OnDeviceAiProviderHandshakeOutcome.Busy)
        firstHandle.close()
        assertTrue(first.single() === OnDeviceAiProviderHandshakeOutcome.Cancelled)
    }

    @Test
    fun workerRejectionHasOneTypedOutcomeAndReleasesTheGate() {
        val transport = FakeTransport(handshakeInput())
        val rejecting = Executor { throw IllegalStateException("rejected") }
        val gate = OnDeviceAiProviderHandshakeGate()
        val outcomes = ArrayList<OnDeviceAiProviderHandshakeOutcome>()

        coordinator(transport, worker = rejecting, gate = gate).start(handshakeRequest(), outcomes::add)

        assertEquals(
            OnDeviceAiProviderHandshakeFailure.EXECUTOR_REJECTED,
            (outcomes.single() as OnDeviceAiProviderHandshakeOutcome.Rejected).failure,
        )
        assertEquals(
            OnDeviceAiProviderHandshakeAdmission.ACQUIRED,
            gate.tryAcquire(handshakeRequest().snapshot.component),
        )
        gate.release()
    }

    private fun coordinator(
        transport: FakeTransport,
        worker: Executor = DIRECT,
        blocking: Executor = DIRECT,
        scheduler: FakeDeadlineScheduler = FakeDeadlineScheduler(),
        gate: OnDeviceAiProviderHandshakeGate = OnDeviceAiProviderHandshakeGate(),
        clock: OnDeviceAiProviderHandshakeClock = FakeClock(),
    ) = OnDeviceAiProviderHandshakeCoordinator(
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
    val input: OnDeviceAiProviderDiscoveryInput,
) : OnDeviceAiProviderHandshakeTransport {
    val inspectionResults = ArrayDeque<OnDeviceAiProviderDiscoverySourceResult>()
    val rpc = FakeMetadataRpc()
    val binding = FakeBinding()
    var descriptorMatches = true
    var duplicateConnection = false
    var bindingFailure: OnDeviceAiProviderHandshakeFailure? = null
    var afterInspection: ((Int) -> Unit)? = null
    var inspectionCalls = 0
    var bindCalls = 0
    var openRpcCalls = 0

    override fun inspectExact(component: OnDeviceAiProviderComponent): OnDeviceAiProviderDiscoverySourceResult {
        inspectionCalls += 1
        val result = inspectionResults.pollFirst() ?: discovered(input)
        afterInspection?.invoke(inspectionCalls)
        return result
    }

    override fun bindExact(
        snapshot: OnDeviceAiProviderPackageSnapshot,
        callback: OnDeviceAiProviderBindingCallback,
    ): OnDeviceAiProviderBinding {
        bindCalls += 1
        val failure = bindingFailure
        if (failure == null) {
            callback.onConnected(FakeRawConnection)
            if (duplicateConnection) callback.onConnected(FakeRawConnection)
        } else if (failure == OnDeviceAiProviderHandshakeFailure.BINDER_DIED) {
            callback.onBinderDeath()
        } else {
            callback.onBindingFailure(failure)
        }
        return binding
    }

    override fun openMetadataRpc(connection: OnDeviceAiProviderRawConnection): OnDeviceAiProviderMetadataRpc {
        openRpcCalls += 1
        if (!descriptorMatches) throw OnDeviceAiProviderDescriptorMismatchException()
        return rpc
    }
}

private object FakeRawConnection : OnDeviceAiProviderRawConnection

private class FakeBinding : OnDeviceAiProviderBinding {
    var duringClose: (() -> Unit)? = null
    var closeCalls = 0

    override fun close() {
        closeCalls += 1
        duringClose?.invoke()
    }
}

private class FakeMetadataRpc : OnDeviceAiProviderMetadataRpc {
    var encodedProviderInfo: ByteArray? = AiCommonCodec.encodeProviderInfo(
        OnDeviceAiHostTestFixtures.providerInfo(),
    )
    var encodedCapabilities: ByteArray? = OnDeviceAiCodec.encodeCapabilities(
        OnDeviceAiHostTestFixtures.capabilities(),
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

private class FakeDeadlineScheduler : OnDeviceAiProviderHandshakeDeadlineScheduler {
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
) : OnDeviceAiProviderHandshakeClock {
    override fun elapsedRealtimeMillis(): Long = nowMillis
}

private fun handshakeRequest(
    input: OnDeviceAiProviderDiscoveryInput = handshakeInput(),
    enabled: Boolean = true,
) = OnDeviceAiProviderHandshakeRequest(
    snapshot = snapshot(input),
    environment = OnDeviceAiHostTestFixtures.environment(),
    hostCapabilities = OnDeviceAiHostTestFixtures.capabilities(),
    options = OnDeviceAiProviderHandshakeOptions(enabled = enabled, timeoutMillis = 5_000L),
)

private fun snapshot(input: OnDeviceAiProviderDiscoveryInput): OnDeviceAiProviderPackageSnapshot =
    requireNotNull(OnDeviceAiProviderPackageSnapshot.from(input))

private fun discovered(input: OnDeviceAiProviderDiscoveryInput) =
    OnDeviceAiProviderDiscoverySourceResult.Completed(
        listOf(OnDeviceAiProviderDiscoverySourceEntry.Read(input)),
    )

private fun handshakeInput(
    packageName: String = "example.ai.provider",
    className: String = "OnDeviceAiService",
    declaredAction: String = OnDeviceAiProtocol.ACTION_PROVIDER,
    exported: Boolean = true,
    applicationEnabled: Boolean = true,
    serviceEnabled: Boolean = true,
    requiredPermission: String? = OnDeviceAiProtocol.PLUGIN_PERMISSION,
    packageUid: Int = 10_001,
    versionCode: Long = 7L,
    lastUpdateTime: Long = 123L,
    signers: Collection<String> = listOf(OnDeviceAiHostTestFixtures.SIGNER_A),
    officialSignerMatch: Boolean = true,
    sameSignerAsHost: Boolean = true,
    debuggable: Boolean = false,
) = OnDeviceAiProviderDiscoveryInput(
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
