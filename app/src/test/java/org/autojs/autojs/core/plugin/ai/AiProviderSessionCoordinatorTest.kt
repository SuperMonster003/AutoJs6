package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiPayloadReference
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
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import org.autojs.plugin.ai.provider.api.AiProviderRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AiProviderSessionCoordinatorTest {

    @Test
    fun defaultOffClosesRequestLeaseWithoutTransportDeadlineOrOwnerSideEffects() {
        val fixture = SessionCoordinatorFixture()
        val lease = SessionRecordingRequestLease()
        val transport = FakeSessionTransport(fixture)
        val scheduler = FakeSessionDeadlineScheduler()
        val worker = SessionManualExecutor()
        val blocking = SessionManualExecutor()
        val fallback = SessionManualExecutor()
        val owner = AiProviderSessionProcessOwner()
        val events = CopyOnWriteArrayList<AiProviderSessionRunEvent>()

        coordinator(transport, worker, blocking, fallback, scheduler, owner).start(
            fixture.runRequest(lease = lease, enabled = false),
            events::add,
        )
        drain(worker, blocking, fallback)

        assertEquals(
            listOf(
                AiProviderSessionRunEvent.Transcript(
                    AiProviderSessionEvent.Disabled,
                ),
            ),
            events,
        )
        assertEquals(1, lease.closeCount.get())
        assertEquals(1, worker.executeCalls.get())
        assertEquals(0, blocking.executeCalls.get())
        assertEquals(1, fallback.executeCalls.get())
        assertEquals(0, scheduler.scheduleCalls.get())
        assertEquals(0, transport.inspectionCalls.get())
        assertEquals(0, transport.bindCalls.get())
        assertEquals(0, transport.openRpcCalls.get())
        assertEquals(
            AiProviderSessionAdmission.ACQUIRED,
            owner.tryAcquire(fixture.snapshot.component),
        )
        owner.release()
    }

    @Test
    fun sameRpcMetadataAndFourInspectionsPrecedeOneOpenWhileEarlyCallbacksCommitAfterReturn() {
        val fixture = SessionCoordinatorFixture()
        val order = Collections.synchronizedList(mutableListOf<String>())
        val lease = SessionRecordingRequestLease { order += "request.close" }
        val transport = FakeSessionTransport(fixture, order)
        val scheduler = FakeSessionDeadlineScheduler()
        val worker = SessionManualExecutor()
        val blocking = SessionManualExecutor()
        val fallback = SessionManualExecutor()
        val events = CopyOnWriteArrayList<AiProviderSessionRunEvent>()
        transport.rpc.openBehavior = { callback, _, _, requestEnvelope, requestDescriptors ->
            order += "open"
            assertSame(lease, requestDescriptors)
            assertEquals(0, lease.closeCount.get())
            assertEquals(fixture.request, AiProviderCodec.decodeTextRequest(requestEnvelope))
            order += "callback.started"
            callback.onStarted(PROVIDER_UID, fixture.startedEnvelope())
            val incoming = SessionRecordingIncomingDescriptors()
            transport.completionIncoming = incoming
            order += "callback.completed"
            callback.onCompleted(PROVIDER_UID, fixture.completionEnvelope("done"), incoming)
            assertTrue("Provisional callbacks escaped before open returned", events.isEmpty())
            order += "open.return"
            transport.remote
        }

        coordinator(
            transport,
            worker,
            blocking,
            fallback,
            scheduler,
            AiProviderSessionProcessOwner(),
        ).start(fixture.runRequest(lease), events::add)
        drain(worker, blocking, fallback)

        assertEquals(
            listOf(
                "inspect.1",
                "bind",
                "inspect.2",
                "openRpc",
                "getProviderInfo",
                "getCapabilities",
                "inspect.3",
                "prepareOpen",
                "inspect.4",
                "open",
                "callback.started",
                "callback.completed",
                "open.return",
            ),
            order.takeWhile { it != "request.close" },
        )
        assertTrue(order.indexOf("request.close") > order.indexOf("open.return"))
        assertEquals(4, transport.inspectionCalls.get())
        assertEquals(1, transport.openRpcCalls.get())
        assertEquals(1, transport.rpc.providerInfoCalls.get())
        assertEquals(1, transport.rpc.capabilitiesCalls.get())
        assertEquals(1, transport.rpc.prepareCalls.get())
        assertEquals(1, transport.rpc.prepared.openCalls.get())
        assertEquals(1, transport.rpc.prepared.closeCount.get())
        assertEquals(1, transport.binding.closeCount.get())
        assertEquals(1, scheduler.closeCount.get())
        assertEquals(1, lease.closeCount.get())
        assertEquals(1, checkNotNull(transport.completionIncoming).copyCount.get())
        assertEquals(1, checkNotNull(transport.completionIncoming).closeCount.get())
        assertEquals(1, checkNotNull(transport.completionIncoming).owned.closeCount.get())
        assertEquals(0, transport.remote.cancelCount.get())
        assertEquals(1, transport.remote.closeCount.get())
        assertEquals(
            listOf(
                AiProviderSessionEvent.Started::class.java,
                AiProviderSessionEvent.Completed::class.java,
            ),
            events.map { (it as AiProviderSessionRunEvent.Transcript).event.javaClass },
        )
    }

    @Test
    fun chunkSynchronousWithOpenIsRejectedWithoutPublishingDataOrLeakingLateRemote() {
        val fixture = SessionCoordinatorFixture(stream = true)
        val lease = SessionRecordingRequestLease()
        val transport = FakeSessionTransport(fixture)
        val worker = SessionManualExecutor()
        val blocking = SessionManualExecutor()
        val fallback = SessionManualExecutor()
        val events = CopyOnWriteArrayList<AiProviderSessionRunEvent>()
        transport.rpc.openBehavior = { callback, _, _, _, _ ->
            callback.onChunk(
                PROVIDER_UID,
                AiProviderCodec.encodeTextChunk(AiProviderChunk(sequence = 0, textDelta = "too-early")),
            )
            transport.remote
        }

        coordinator(
            transport,
            worker,
            blocking,
            fallback,
            FakeSessionDeadlineScheduler(),
            AiProviderSessionProcessOwner(),
        ).start(fixture.runRequest(lease), events::add)
        drain(worker, blocking, fallback)

        assertEquals(
            listOf(
                AiProviderSessionRunEvent.Rejected(
                    failure = AiProviderSessionRunFailure.TRANSCRIPT_REJECTED,
                    transcriptFailure = AiProviderSessionFailure.CALLBACK_OUT_OF_ORDER,
                ),
            ),
            events,
        )
        assertEquals(1, lease.closeCount.get())
        assertEquals(1, transport.remote.cancelCount.get())
        assertEquals(1, transport.remote.closeCount.get())
        assertTrue(transport.remote.creditGrants.isEmpty())
    }

    @Test
    fun pinnedMismatchAndFinalIdentityDriftBothStopBeforeOpenAtTheirExactBoundaries() {
        val fixture = SessionCoordinatorFixture()
        val mismatchedLease = SessionRecordingRequestLease()
        val mismatchedTransport = FakeSessionTransport(fixture)
        val mismatchedEvents = CopyOnWriteArrayList<AiProviderSessionRunEvent>()
        val otherSnapshot = checkNotNull(
            AiProviderPackageSnapshot.from(sessionDiscoveryInput(versionCode = 99L)),
        )

        coordinator(mismatchedTransport).start(
            fixture.runRequest(lease = mismatchedLease, snapshot = otherSnapshot),
            mismatchedEvents::add,
        )

        assertEquals(
            listOf(
                AiProviderSessionRunEvent.Rejected(
                    AiProviderSessionRunFailure.PINNED_IDENTITY_MISMATCH,
                ),
            ),
            mismatchedEvents,
        )
        assertEquals(1, mismatchedLease.closeCount.get())
        assertEquals(0, mismatchedTransport.inspectionCalls.get())
        assertEquals(0, mismatchedTransport.bindCalls.get())

        val finalReinspectionCases = listOf(
            discovered(sessionDiscoveryInput(lastUpdateTime = 999L)),
            AiProviderDiscoverySourceResult.Completed(emptyList()),
        )
        finalReinspectionCases.forEach { finalInspection ->
            val driftLease = SessionRecordingRequestLease()
            val driftTransport = FakeSessionTransport(fixture)
            repeat(3) { driftTransport.inspectionResults += discovered(fixture.input) }
            driftTransport.inspectionResults += finalInspection
            val driftEvents = CopyOnWriteArrayList<AiProviderSessionRunEvent>()

            coordinator(driftTransport).start(fixture.runRequest(driftLease), driftEvents::add)

            assertEquals(
                listOf(
                    AiProviderSessionRunEvent.Rejected(
                        AiProviderSessionRunFailure.IDENTITY_DRIFT,
                    ),
                ),
                driftEvents,
            )
            assertEquals(4, driftTransport.inspectionCalls.get())
            assertEquals(1, driftTransport.rpc.prepareCalls.get())
            assertEquals(0, driftTransport.rpc.prepared.openCalls.get())
            assertEquals(1, driftTransport.rpc.prepared.closeCount.get())
            assertEquals(1, driftTransport.binding.closeCount.get())
            assertEquals(1, driftLease.closeCount.get())
        }
    }

    @Test
    fun metadataProviderDescriptorSessionDescriptorAndOpenFailuresStayPreciselyTyped() {
        data class FailureCase(
            val expected: AiProviderSessionRunFailure,
            val configure: (FakeSessionTransport) -> Unit,
        )
        val cases = listOf(
            FailureCase(AiProviderSessionRunFailure.PROVIDER_INFO_MISSING) {
                it.rpc.encodedProviderInfo = null
            },
            FailureCase(AiProviderSessionRunFailure.PINNED_METADATA_MISMATCH) {
                it.rpc.encodedProviderInfo = AiCommonCodec.encodeProviderInfo(
                    AiProviderHostTestFixtures.providerInfo(providerId = "different.provider"),
                )
            },
            FailureCase(AiProviderSessionRunFailure.PROVIDER_DESCRIPTOR_MISMATCH) {
                it.openRpcFailure = AiProviderSessionProviderDescriptorMismatchException()
            },
            FailureCase(AiProviderSessionRunFailure.SESSION_DESCRIPTOR_MISMATCH) {
                it.rpc.openFailure = AiProviderRemoteSessionDescriptorMismatchException()
            },
            FailureCase(AiProviderSessionRunFailure.REMOTE_FAILURE) {
                it.rpc.openFailure = IllegalStateException("open failed with secret detail")
            },
        )

        cases.forEach { case ->
            val fixture = SessionCoordinatorFixture()
            val lease = SessionRecordingRequestLease()
            val transport = FakeSessionTransport(fixture).apply(case.configure)
            val events = CopyOnWriteArrayList<AiProviderSessionRunEvent>()

            coordinator(transport).start(fixture.runRequest(lease), events::add)

            assertEquals(
                "Failure case ${case.expected}",
                listOf(AiProviderSessionRunEvent.Rejected(case.expected)),
                events,
            )
            assertEquals(1, lease.closeCount.get())
            assertEquals(1, transport.binding.closeCount.get())
            assertEquals(0, transport.remote.closeCount.get())
            assertFalse(events.toString().contains("secret detail"))
        }
    }

    @Test
    fun deadProviderBinderBeforeMetadataMapsToBinderDeathWithoutOpeningASession() {
        val fixture = SessionCoordinatorFixture()
        val lease = SessionRecordingRequestLease()
        val transport = FakeSessionTransport(fixture).apply {
            openRpcFailure = AiProviderRemoteSessionDeadException()
        }
        val events = CopyOnWriteArrayList<AiProviderSessionRunEvent>()

        coordinator(transport).start(fixture.runRequest(lease), events::add)

        assertEquals(
            listOf(
                AiProviderSessionRunEvent.Transcript(
                    AiProviderSessionEvent.BinderDied,
                ),
            ),
            events,
        )
        assertEquals(2, transport.inspectionCalls.get())
        assertEquals(1, transport.openRpcCalls.get())
        assertEquals(0, transport.rpc.providerInfoCalls.get())
        assertEquals(0, transport.rpc.prepareCalls.get())
        assertEquals(1, transport.binding.closeCount.get())
        assertEquals(1, lease.closeCount.get())
        assertEquals(0, transport.remote.cancelCount.get())
        assertEquals(0, transport.remote.closeCount.get())
    }

    @Test
    fun deadMarkerFromEitherMetadataGetterMapsToBinderDeathWithoutPreparingOpen() {
        data class MetadataDeathCase(
            val expectedProviderInfoCalls: Int,
            val expectedCapabilitiesCalls: Int,
            val configure: (FakeSessionRpc) -> Unit,
        )
        val cases = listOf(
            MetadataDeathCase(expectedProviderInfoCalls = 1, expectedCapabilitiesCalls = 0) {
                it.providerInfoFailure = AiProviderRemoteSessionDeadException()
            },
            MetadataDeathCase(expectedProviderInfoCalls = 1, expectedCapabilitiesCalls = 1) {
                it.capabilitiesFailure = AiProviderRemoteSessionDeadException()
            },
        )

        cases.forEach { case ->
            val fixture = SessionCoordinatorFixture()
            val lease = SessionRecordingRequestLease()
            val transport = FakeSessionTransport(fixture).also { case.configure(it.rpc) }
            val events = CopyOnWriteArrayList<AiProviderSessionRunEvent>()

            coordinator(transport).start(fixture.runRequest(lease), events::add)

            assertEquals(
                listOf(
                    AiProviderSessionRunEvent.Transcript(
                        AiProviderSessionEvent.BinderDied,
                    ),
                ),
                events,
            )
            assertEquals(2, transport.inspectionCalls.get())
            assertEquals(1, transport.openRpcCalls.get())
            assertEquals(case.expectedProviderInfoCalls, transport.rpc.providerInfoCalls.get())
            assertEquals(case.expectedCapabilitiesCalls, transport.rpc.capabilitiesCalls.get())
            assertEquals(0, transport.rpc.prepareCalls.get())
            assertEquals(1, transport.binding.closeCount.get())
            assertEquals(1, lease.closeCount.get())
            assertEquals(0, transport.remote.cancelCount.get())
            assertEquals(0, transport.remote.closeCount.get())
        }
    }

    @Test
    fun observedOperationCrossingDeadlineFusesAndDefersTimeoutUntilItsLeaseSettles() {
        val fixture = SessionCoordinatorFixture()
        val lease = SessionRecordingRequestLease()
        val transport = FakeSessionTransport(fixture)
        val scheduler = FakeSessionDeadlineScheduler()
        val clock = FakeSessionClock()
        val worker = SessionManualExecutor()
        val blocking = SessionManualExecutor()
        val fallback = SessionManualExecutor()
        val owner = AiProviderSessionProcessOwner()
        val events = CopyOnWriteArrayList<AiProviderSessionRunEvent>()
        transport.rpc.openBehavior = { callback, _, _, _, _ ->
            callback.onStarted(PROVIDER_UID, fixture.startedEnvelope())
            transport.remote
        }

        coordinator(
            transport,
            worker,
            blocking,
            fallback,
            scheduler,
            owner,
            clock,
        ).start(fixture.runRequest(lease), events::add)
        drain(worker, blocking, fallback)
        assertEquals(1, events.size)
        assertTrue(
            (events.single() as AiProviderSessionRunEvent.Transcript).event is
                AiProviderSessionEvent.Started,
        )

        val observedOperation = transport.rpc.operationObserver.begin()
        clock.nowMillis = fixture.request.options.timeoutMillis
        scheduler.fire()
        drain(worker, blocking, fallback)

        assertEquals(1, events.size)
        assertEquals(
            AiProviderSessionAdmission.FUSED,
            owner.tryAcquire(fixture.snapshot.component),
        )
        assertEquals(1, transport.remote.cancelCount.get())
        assertEquals(1, transport.remote.closeCount.get())
        assertEquals(1, transport.binding.closeCount.get())

        observedOperation.close()
        drain(worker, blocking, fallback)

        assertEquals(
            listOf(
                AiProviderSessionEvent.Started::class.java,
                AiProviderSessionEvent.TimedOut::class.java,
            ),
            events.map { (it as AiProviderSessionRunEvent.Transcript).event.javaClass },
        )
        assertEquals(1, transport.binding.closeCount.get())
        assertEquals(1, transport.rpc.prepared.closeCount.get())
        assertEquals(1, lease.closeCount.get())
    }

    @Test
    fun lateOperationReturnLiftsTheFuseOnceTheRunSettles() {
        val fixture = SessionCoordinatorFixture()
        val lease = SessionRecordingRequestLease()
        val transport = FakeSessionTransport(fixture)
        val scheduler = FakeSessionDeadlineScheduler()
        val clock = FakeSessionClock()
        val worker = SessionManualExecutor()
        val blocking = SessionManualExecutor()
        val fallback = SessionManualExecutor()
        val owner = AiProviderSessionProcessOwner()
        val events = CopyOnWriteArrayList<AiProviderSessionRunEvent>()
        transport.rpc.openBehavior = { callback, _, _, _, _ ->
            callback.onStarted(PROVIDER_UID, fixture.startedEnvelope())
            transport.remote
        }

        coordinator(transport, worker, blocking, fallback, scheduler, owner, clock)
            .start(fixture.runRequest(lease), events::add)
        drain(worker, blocking, fallback)
        val observedOperation = transport.rpc.operationObserver.begin()
        clock.nowMillis = fixture.request.options.timeoutMillis
        scheduler.fire()
        drain(worker, blocking, fallback)
        assertEquals(AiProviderSessionAdmission.FUSED, owner.tryAcquire(fixture.snapshot.component))

        observedOperation.close()
        drain(worker, blocking, fallback)

        assertEquals(
            AiProviderSessionEvent.TimedOut::class.java,
            (events.last() as AiProviderSessionRunEvent.Transcript).event.javaClass,
        )
        assertEquals(AiProviderSessionAdmission.ACQUIRED, owner.tryAcquire(fixture.snapshot.component))
        owner.release()
        val nextEvents = CopyOnWriteArrayList<AiProviderSessionRunEvent>()
        val nextTransport = FakeSessionTransport(fixture)
        nextTransport.rpc.openBehavior = { callback, _, _, _, _ ->
            callback.onStarted(PROVIDER_UID, fixture.startedEnvelope())
            nextTransport.remote
        }
        coordinator(nextTransport, worker, blocking, fallback, scheduler, owner, clock)
            .start(fixture.runRequest(SessionRecordingRequestLease()), nextEvents::add)
        drain(worker, blocking, fallback)
        assertFalse(nextEvents.contains(AiProviderSessionRunEvent.Fused))
        assertTrue((nextEvents.first() as AiProviderSessionRunEvent.Transcript).event is AiProviderSessionEvent.Started)
    }

    @Test
    fun timeoutAcceptedBeforeLastObservedOperationSettlesStillFusesComponent() {
        val fixture = SessionCoordinatorFixture()
        val lease = SessionRecordingRequestLease()
        val transport = FakeSessionTransport(fixture)
        val scheduler = FakeSessionDeadlineScheduler()
        val clock = FakeSessionClock()
        val worker = SessionManualExecutor()
        val blocking = SessionManualExecutor()
        val fallbackQueue = SessionManualExecutor()
        val fallbackSubmitted = java.util.concurrent.CountDownLatch(1)
        val releaseFallbackSubmission = java.util.concurrent.CountDownLatch(1)
        val fallback = Executor { command ->
            fallbackSubmitted.countDown()
            check(releaseFallbackSubmission.await(5, TimeUnit.SECONDS)) {
                "Timed out waiting to release terminal fallback submission"
            }
            fallbackQueue.execute(command)
        }
        val owner = AiProviderSessionProcessOwner()
        val events = CopyOnWriteArrayList<AiProviderSessionRunEvent>()
        transport.rpc.openBehavior = { callback, _, _, _, _ ->
            callback.onStarted(PROVIDER_UID, fixture.startedEnvelope())
            transport.remote
        }

        coordinator(
            transport,
            worker,
            blocking,
            fallback,
            scheduler,
            owner,
            clock,
        ).start(fixture.runRequest(lease), events::add)
        drain(worker, blocking, fallbackQueue)
        val observedOperation = transport.rpc.operationObserver.begin()
        clock.nowMillis = fixture.request.options.timeoutMillis
        val timeoutFailure = AtomicReference<Throwable?>()
        val timeoutThread = Thread({
            runCatching(scheduler::fire).exceptionOrNull()?.let(timeoutFailure::set)
        }, "session-timeout-race")

        try {
            timeoutThread.start()
            assertTrue(
                "Timeout was not accepted before the observed operation settled",
                fallbackSubmitted.await(5, TimeUnit.SECONDS),
            )
            observedOperation.close()
        } finally {
            releaseFallbackSubmission.countDown()
            timeoutThread.join(TimeUnit.SECONDS.toMillis(5))
        }
        assertFalse("Timeout thread did not finish", timeoutThread.isAlive)
        timeoutFailure.get()?.let { throw AssertionError("Timeout thread failed", it) }
        drain(worker, blocking, fallbackQueue)

        assertEquals(
            listOf(
                AiProviderSessionEvent.Started::class.java,
                AiProviderSessionEvent.TimedOut::class.java,
            ),
            events.map { (it as AiProviderSessionRunEvent.Transcript).event.javaClass },
        )
        // The settled run lifted its own fuse once the late operation returned (D52).
        assertEquals(
            AiProviderSessionAdmission.ACQUIRED,
            owner.tryAcquire(fixture.snapshot.component),
        )
        owner.release()
        assertEquals(1, lease.closeCount.get())
        assertEquals(1, transport.remote.cancelCount.get())
        assertEquals(1, transport.remote.closeCount.get())
    }

    @Test
    fun observedOperationBeginningAfterTimeoutSampledZeroFusesImmediately() {
        val fixture = SessionCoordinatorFixture()
        val lease = SessionRecordingRequestLease()
        val transport = FakeSessionTransport(fixture)
        val scheduler = FakeSessionDeadlineScheduler()
        val clock = FakeSessionClock()
        val worker = SessionManualExecutor()
        val blocking = SessionManualExecutor()
        val fallback = SessionManualExecutor()
        val owner = AiProviderSessionProcessOwner()
        val events = CopyOnWriteArrayList<AiProviderSessionRunEvent>()
        transport.rpc.openBehavior = { callback, _, _, _, _ ->
            callback.onStarted(PROVIDER_UID, fixture.startedEnvelope())
            transport.remote
        }

        coordinator(
            transport,
            worker,
            blocking,
            fallback,
            scheduler,
            owner,
            clock,
        ).start(fixture.runRequest(lease), events::add)
        drain(worker, blocking, fallback)

        val preparedCloseEntered = java.util.concurrent.CountDownLatch(1)
        val releasePreparedClose = java.util.concurrent.CountDownLatch(1)
        transport.rpc.prepared.closeBehavior = {
            preparedCloseEntered.countDown()
            check(releasePreparedClose.await(5, TimeUnit.SECONDS)) {
                "Timed out waiting to release prepared-open close"
            }
        }
        clock.nowMillis = fixture.request.options.timeoutMillis
        val timeoutFailure = AtomicReference<Throwable?>()
        val timeoutThread = Thread({
            runCatching(scheduler::fire).exceptionOrNull()?.let(timeoutFailure::set)
        }, "session-timeout-after-zero-sample")
        lateinit var lateObservedOperation: Closeable

        try {
            timeoutThread.start()
            assertTrue(
                "Timeout did not reach transport close after sampling zero operations",
                preparedCloseEntered.await(5, TimeUnit.SECONDS),
            )
            lateObservedOperation = transport.rpc.operationObserver.begin()
            assertEquals(
                AiProviderSessionAdmission.FUSED,
                owner.tryAcquire(fixture.snapshot.component),
            )
        } finally {
            releasePreparedClose.countDown()
            timeoutThread.join(TimeUnit.SECONDS.toMillis(5))
        }
        assertFalse("Timeout thread did not finish", timeoutThread.isAlive)
        timeoutFailure.get()?.let { throw AssertionError("Timeout thread failed", it) }
        drain(worker, blocking, fallback)
        assertEquals(1, events.size)

        lateObservedOperation.close()
        drain(worker, blocking, fallback)

        assertEquals(
            listOf(
                AiProviderSessionEvent.Started::class.java,
                AiProviderSessionEvent.TimedOut::class.java,
            ),
            events.map { (it as AiProviderSessionRunEvent.Transcript).event.javaClass },
        )
        assertEquals(1, transport.rpc.prepared.closeCount.get())
        assertEquals(1, transport.binding.closeCount.get())
        assertEquals(1, lease.closeCount.get())
        assertEquals(1, transport.remote.cancelCount.get())
        assertEquals(1, transport.remote.closeCount.get())
        // The settled run lifted its own fuse once the late operation returned (D52).
        assertEquals(
            AiProviderSessionAdmission.ACQUIRED,
            owner.tryAcquire(fixture.snapshot.component),
        )
        owner.release()
    }

    @Test
    fun transportFailureWinsWhenPolicyFallbackSettlesBeforeCancelReturns() {
        val fixture = SessionCoordinatorFixture()
        val lease = SessionRecordingRequestLease()
        val transport = FakeSessionTransport(fixture)
        val owner = AiProviderSessionProcessOwner()
        val events = CopyOnWriteArrayList<AiProviderSessionRunEvent>()
        val eventThreads = CopyOnWriteArrayList<String>()
        val fallback = SessionJoiningThreadExecutor()
        transport.rpc.openBehavior = { callback, _, _, _, _ ->
            callback.onStarted(PROVIDER_UID, fixture.startedEnvelope())
            transport.remote
        }

        coordinator(
            transport = transport,
            fallback = fallback,
            owner = owner,
        ).start(fixture.runRequest(lease)) { event ->
            events += event
            eventThreads += Thread.currentThread().name
        }
        transport.failBinding(AiProviderSessionRunFailure.BIND_REJECTED)

        assertEquals(
            listOf(
                AiProviderSessionRunEvent.Transcript(
                    AiProviderSessionEvent.Started(
                        AiProviderCodec.decodeSessionStarted(fixture.startedEnvelope()),
                    ),
                ),
                AiProviderSessionRunEvent.Rejected(
                    AiProviderSessionRunFailure.BIND_REJECTED,
                ),
            ),
            events,
        )
        assertEquals(2, fallback.executeCalls.get())
        assertTrue(eventThreads.last().startsWith("session-terminal-fallback-2"))
        assertEquals(1, lease.closeCount.get())
        assertEquals(1, transport.remote.cancelCount.get())
        assertEquals(1, transport.remote.closeCount.get())
        assertEquals(
            AiProviderSessionAdmission.ACQUIRED,
            owner.tryAcquire(fixture.snapshot.component),
        )
        owner.release()
    }

    @Test
    fun throwableEscapingPreparedOpenStillCommitsFailureAndSettlesLeaseAndGate() {
        listOf<Throwable>(
            AssertionError("prepared open error detail"),
            Throwable("prepared open throwable detail"),
        ).forEach { openFailure ->
            val fixture = SessionCoordinatorFixture()
            val lease = SessionRecordingRequestLease()
            val transport = FakeSessionTransport(fixture).apply {
                rpc.openFailure = openFailure
            }
            val owner = AiProviderSessionProcessOwner()
            val events = CopyOnWriteArrayList<AiProviderSessionRunEvent>()

            coordinator(transport = transport, owner = owner).start(
                fixture.runRequest(lease),
                events::add,
            )

            assertEquals(
                openFailure.javaClass.simpleName,
                listOf(
                    AiProviderSessionRunEvent.Rejected(
                        AiProviderSessionRunFailure.INTERNAL_FAILURE,
                    ),
                ),
                events,
            )
            assertEquals(1, transport.rpc.prepared.openCalls.get())
            assertEquals(1, transport.rpc.prepared.closeCount.get())
            assertEquals(1, transport.binding.closeCount.get())
            assertEquals(1, lease.closeCount.get())
            assertEquals(0, transport.remote.cancelCount.get())
            assertEquals(0, transport.remote.closeCount.get())
            assertFalse(events.toString().contains("prepared open"))
            assertEquals(
                AiProviderSessionAdmission.ACQUIRED,
                owner.tryAcquire(fixture.snapshot.component),
            )
            owner.release()
        }
    }

    @Test
    fun cancelDuringBlockedOpenKeepsGateAndRequestLeaseUntilLateRemoteIsCancelledAndClosed() {
        blockedOpenCase(
            terminal = BlockedTerminal.CANCEL,
            expected = AiProviderSessionEvent.Cancelled,
            expectedRemoteCancels = 1,
            expectFuse = false,
        )
    }

    @Test
    fun timeoutDuringBlockedOpenFusesComponentButPublishesOnlyAfterLateRemoteCleanup() {
        blockedOpenCase(
            terminal = BlockedTerminal.TIMEOUT,
            expected = AiProviderSessionEvent.TimedOut,
            expectedRemoteCancels = 1,
            expectFuse = true,
        )
    }

    @Test
    fun binderDeathDuringBlockedOpenDoesNotCallCancelAndStillWaitsForLateRemoteClose() {
        blockedOpenCase(
            terminal = BlockedTerminal.BINDER_DEATH,
            expected = AiProviderSessionEvent.BinderDied,
            expectedRemoteCancels = 0,
            expectFuse = false,
        )
    }

    @Test
    fun workerAndBlockingExecutorRejectionHaveOneOutcomeAndReleaseEveryOwner() {
        data class RejectionCase(
            val worker: Executor,
            val blocking: Executor,
            val expectedBindCalls: Int,
        )
        val cases = listOf(
            RejectionCase(SessionRejectingExecutor, DIRECT, 0),
            RejectionCase(DIRECT, SessionRejectingExecutor, 1),
        )
        cases.forEach { case ->
            val fixture = SessionCoordinatorFixture()
            val lease = SessionRecordingRequestLease()
            val transport = FakeSessionTransport(fixture)
            val fallback = SessionManualExecutor()
            val scheduler = FakeSessionDeadlineScheduler()
            val owner = AiProviderSessionProcessOwner()
            val events = CopyOnWriteArrayList<AiProviderSessionRunEvent>()

            coordinator(
                transport = transport,
                worker = case.worker,
                blocking = case.blocking,
                fallback = fallback,
                scheduler = scheduler,
                owner = owner,
            ).start(fixture.runRequest(lease), events::add)
            fallback.drain()

            assertEquals(
                listOf(
                    AiProviderSessionRunEvent.Rejected(
                        AiProviderSessionRunFailure.EXECUTOR_REJECTED,
                    ),
                ),
                events,
            )
            assertEquals(case.expectedBindCalls, transport.bindCalls.get())
            assertEquals(1, lease.closeCount.get())
            assertEquals(
                AiProviderSessionAdmission.ACQUIRED,
                owner.tryAcquire(fixture.snapshot.component),
            )
            owner.release()
        }
    }

    @Test
    fun busyAndFusedAdmissionCloseUnacceptedLeasesWithoutPlatformAccess() {
        val fixture = SessionCoordinatorFixture()
        val owner = AiProviderSessionProcessOwner()
        assertEquals(
            AiProviderSessionAdmission.ACQUIRED,
            owner.tryAcquire(fixture.snapshot.component),
        )
        val busyLease = SessionRecordingRequestLease()
        val busyTransport = FakeSessionTransport(fixture)
        val busyEvents = CopyOnWriteArrayList<AiProviderSessionRunEvent>()
        coordinator(busyTransport, owner = owner).start(
            fixture.runRequest(busyLease),
            busyEvents::add,
        )
        assertEquals(listOf(AiProviderSessionRunEvent.Busy), busyEvents)
        assertEquals(1, busyLease.closeCount.get())
        assertEquals(0, busyTransport.inspectionCalls.get())
        owner.release()

        owner.fuse(fixture.snapshot.component)
        val fusedLease = SessionRecordingRequestLease()
        val fusedTransport = FakeSessionTransport(fixture)
        val fusedEvents = CopyOnWriteArrayList<AiProviderSessionRunEvent>()
        coordinator(fusedTransport, owner = owner).start(
            fixture.runRequest(fusedLease),
            fusedEvents::add,
        )
        assertEquals(listOf(AiProviderSessionRunEvent.Fused), fusedEvents)
        assertEquals(1, fusedLease.closeCount.get())
        assertEquals(0, fusedTransport.inspectionCalls.get())
    }

    @Test
    fun completionOwnsTheOnlyTerminalAndLateCallbacksDeathCancelOnlyCleanIncoming() {
        val fixture = SessionCoordinatorFixture()
        val lease = SessionRecordingRequestLease()
        val transport = FakeSessionTransport(fixture)
        val events = CopyOnWriteArrayList<AiProviderSessionRunEvent>()
        val lateIncoming = SessionRecordingIncomingDescriptors()

        val handle = coordinator(transport).start(fixture.runRequest(lease), events::add)
        transport.rpc.callback.onCompleted(
            PROVIDER_UID,
            fixture.completionEnvelope("late"),
            lateIncoming,
        )
        transport.rpc.sessionDeathCallback.invoke()

        assertFalse(handle.cancel())
        handle.close()
        assertEquals(
            1,
            events.count {
                (it as? AiProviderSessionRunEvent.Transcript)?.event is
                    AiProviderSessionEvent.Completed
            },
        )
        assertEquals(2, events.size)
        assertTrue((events.first() as AiProviderSessionRunEvent.Transcript).event is AiProviderSessionEvent.Started)
        assertEquals(1, lateIncoming.closeCount.get())
        assertEquals(0, lateIncoming.copyCount.get())
        assertEquals(1, transport.remote.closeCount.get())
        assertEquals(0, transport.remote.cancelCount.get())
    }

    private fun blockedOpenCase(
        terminal: BlockedTerminal,
        expected: AiProviderSessionEvent,
        expectedRemoteCancels: Int,
        expectFuse: Boolean,
    ) {
        val fixture = SessionCoordinatorFixture()
        val lease = SessionRecordingRequestLease()
        val transport = FakeSessionTransport(fixture)
        val scheduler = FakeSessionDeadlineScheduler()
        val clock = FakeSessionClock()
        val worker = SessionManualExecutor()
        val fallback = SessionManualExecutor()
        val blocking: ExecutorService = Executors.newSingleThreadExecutor()
        val owner = AiProviderSessionProcessOwner()
        val events = CopyOnWriteArrayList<AiProviderSessionRunEvent>()
        val openEntered = java.util.concurrent.CountDownLatch(1)
        val releaseOpen = java.util.concurrent.CountDownLatch(1)
        transport.rpc.openBehavior = { _, _, _, _, _ ->
            openEntered.countDown()
            assertTrue(releaseOpen.await(5, TimeUnit.SECONDS))
            transport.remote
        }
        val handle = coordinator(
            transport,
            worker,
            blocking,
            fallback,
            scheduler,
            owner,
            clock,
        ).start(fixture.runRequest(lease), events::add)

        try {
            driveUntil(worker, fallback) { openEntered.count == 0L }
            assertEquals(0, lease.closeCount.get())
            assertTrue(events.isEmpty())

            when (terminal) {
                BlockedTerminal.CANCEL -> assertTrue(handle.cancel())
                BlockedTerminal.TIMEOUT -> {
                    clock.nowMillis = fixture.request.options.timeoutMillis
                    scheduler.fire()
                }
                BlockedTerminal.BINDER_DEATH -> transport.die()
            }
            driveFor(worker, fallback)

            assertTrue("Terminal escaped before synchronous open settled", events.isEmpty())
            assertEquals(0, lease.closeCount.get())
            assertEquals(
                if (expectFuse) {
                    AiProviderSessionAdmission.FUSED
                } else {
                    AiProviderSessionAdmission.BUSY
                },
                owner.tryAcquire(fixture.snapshot.component),
            )

            releaseOpen.countDown()
            driveUntil(worker, fallback) { events.isNotEmpty() }

            assertEquals(
                listOf(AiProviderSessionRunEvent.Transcript(expected)),
                events,
            )
            assertEquals(1, lease.closeCount.get())
            assertEquals(expectedRemoteCancels, transport.remote.cancelCount.get())
            assertEquals(1, transport.remote.closeCount.get())
            assertEquals(1, transport.binding.closeCount.get())
            assertEquals(1, transport.rpc.prepared.closeCount.get())
            // Whether or not the blocked open fused the component, the settled run has lifted the fuse (D52).
            assertEquals(
                AiProviderSessionAdmission.ACQUIRED,
                owner.tryAcquire(fixture.snapshot.component),
            )
            owner.release()
        } finally {
            releaseOpen.countDown()
            blocking.shutdownNow()
            blocking.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun coordinator(
        transport: FakeSessionTransport,
        worker: Executor = DIRECT,
        blocking: Executor = DIRECT,
        fallback: Executor = SessionManualExecutor(),
        scheduler: FakeSessionDeadlineScheduler = FakeSessionDeadlineScheduler(),
        owner: AiProviderSessionProcessOwner = AiProviderSessionProcessOwner(),
        clock: AiProviderSessionClock = FakeSessionClock(),
    ) = AiProviderSessionCoordinator(
        transport = transport,
        workerExecutor = worker,
        blockingExecutor = blocking,
        terminalFallbackExecutor = fallback,
        deadlineScheduler = scheduler,
        clock = clock,
        owner = owner,
    )

    private fun drain(vararg executors: SessionManualExecutor) {
        var total = 0
        while (true) {
            val current = executors.sumOf(SessionManualExecutor::drain)
            if (current == 0) return
            total += current
            check(total < 2_000) { "Session coordinator executors did not become idle" }
        }
    }

    private fun driveUntil(
        worker: SessionManualExecutor,
        fallback: SessionManualExecutor,
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            worker.drain()
            fallback.drain()
            if (System.nanoTime() >= deadline) error("Timed out driving session coordinator")
            Thread.sleep(1)
        }
        worker.drain()
        fallback.drain()
    }

    private fun driveFor(worker: SessionManualExecutor, fallback: SessionManualExecutor) {
        repeat(10) {
            worker.drain()
            fallback.drain()
            Thread.sleep(1)
        }
    }

    private enum class BlockedTerminal {
        CANCEL,
        TIMEOUT,
        BINDER_DEATH,
    }

    private companion object {
        val DIRECT = Executor(Runnable::run)
        const val PROVIDER_UID = 10_001
    }
}

private class SessionCoordinatorFixture(
    val input: AiProviderDiscoveryInput = sessionDiscoveryInput(),
    stream: Boolean = false,
) {
    val snapshot = checkNotNull(AiProviderPackageSnapshot.from(input))
    val providerInfo = AiProviderHostTestFixtures.providerInfo()
    val capabilities = AiProviderHostTestFixtures.capabilities()
    val environment = AiProviderHostTestFixtures.environment()
    val requirement = AiProviderCapabilityRequirement()
    val pinned = (
        AiProviderSelectionPolicy.select(
            candidates = listOf(
                AiProviderCandidate(
                    component = snapshot.component,
                    declaredAction = input.declaredAction,
                    exported = input.exported,
                    applicationEnabled = input.applicationEnabled,
                    serviceEnabled = input.serviceEnabled,
                    requiredPermission = input.requiredPermission,
                    packageUid = input.packageUid,
                    versionCode = input.versionCode,
                    lastUpdateTime = input.lastUpdateTime,
                    signerSha256 = input.signerSha256,
                    trustLevel = AiProviderTrustLevel.OFFICIAL,
                    sameSignerAsHost = input.sameSignerAsHost,
                    providerInfo = providerInfo,
                    capabilities = capabilities,
                ),
            ),
            environment = environment,
            hostCapabilities = capabilities,
            requirement = requirement,
            options = AiProviderSelectionOptions(enabled = true),
        ) as AiProviderSelectionResult.Selected
        ).pinned
    private val target = AiProviderHostTestFixtures.target(
        targetId = TARGET_ID,
        modelId = MODEL_ID,
        capabilityIds = listOf(AiProviderCapabilityId.STREAMING, AiProviderCapabilityId.USAGE),
        maximumContextBytes = 512L * 1024L,
        maximumOutputBytes = 256L * 1024L,
    )
    val plan = (AiProviderHostSessionPlanner.plan(
        selected = AiProviderSelectionResult.Selected(pinned),
        catalogGeneration = "fixture-generation",
        target = target,
        requirement = requirement,
    ) as AiProviderSessionPlanningResult.Planned).plan
    val request = AiProviderRequest(
        requestId = "session-request",
        protocolVersion = AiProviderProtocol.PROTOCOL_V2,
        providerId = PROVIDER_ID,
        targetId = TARGET_ID,
        messages = listOf(
            AiMessage(
                role = AiMessageRole.USER,
                parts = listOf(AiContentPart(textPayload("hello"))),
            ),
        ),
        options = AiGenerationOptions(
            stream = stream,
            includeReasoning = false,
            structuredJson = false,
            reportUsage = false,
            maximumOutputBytes = 4_096,
            maximumToolRounds = 0,
            timeoutMillis = 30_000,
        ),
    )

    fun runRequest(
        lease: AiProviderSessionRequestDescriptorLease,
        enabled: Boolean = true,
        snapshot: AiProviderPackageSnapshot = this.snapshot,
    ) = AiProviderSessionRunRequest(
        snapshot = snapshot,
        plan = plan,
        request = request,
        requestDescriptors = lease,
        environment = environment,
        hostCapabilities = capabilities,
        requirement = requirement,
        options = AiProviderSessionRunOptions(enabled = enabled),
    )

    fun startedEnvelope(): ByteArray = AiProviderCodec.encodeSessionStarted(
        AiSessionStarted(
            requestId = request.requestId,
            sessionId = "fixture-session",
            protocolVersion = request.protocolVersion,
            providerId = request.providerId,
            targetId = request.targetId,
            firstChunkSequence = 0,
            effectiveMaximumOutputBytes = request.options.maximumOutputBytes,
            effectiveMaximumToolRounds = 0,
        ),
    )

    fun completionEnvelope(output: String): ByteArray = AiProviderCodec.encodeCompletionResult(
        AiCompletionResult(
            output = textPayload(output),
            finishReason = AiFinishReason.STOP,
        ),
    )

    private companion object {
        const val PROVIDER_ID = "fixture.provider"
        const val TARGET_ID = "fixture.provider:model"
        const val MODEL_ID = "fixture.model"
    }
}

private class FakeSessionTransport(
    fixture: SessionCoordinatorFixture,
    private val order: MutableList<String> = Collections.synchronizedList(mutableListOf()),
) : AiProviderSessionTransport {
    val inspectionResults = ArrayDeque<AiProviderDiscoverySourceResult>()
    val inspectionCalls = AtomicInteger()
    val bindCalls = AtomicInteger()
    val openRpcCalls = AtomicInteger()
    val binding = SessionRecordingBinding()
    val remote = SessionRecordingRemote()
    val rpc = FakeSessionRpc(
        fixture = fixture,
        remote = remote,
        order = order,
    )
    var openRpcFailure: Throwable? = null
    var completionIncoming: SessionRecordingIncomingDescriptors? = null
    @Volatile private var bindingCallback: AiProviderSessionBindingCallback? = null

    override fun inspectExact(component: AiProviderComponent): AiProviderDiscoverySourceResult {
        val call = inspectionCalls.incrementAndGet()
        order += "inspect.$call"
        check(component == rpc.fixture.snapshot.component)
        return synchronized(inspectionResults) {
            if (inspectionResults.isEmpty()) discovered(rpc.fixture.input) else inspectionResults.removeFirst()
        }
    }

    override fun bindExact(
        snapshot: AiProviderPackageSnapshot,
        callback: AiProviderSessionBindingCallback,
    ): AiProviderSessionBinding {
        bindCalls.incrementAndGet()
        order += "bind"
        bindingCallback = callback
        callback.onConnected(SessionFakeConnection)
        return binding
    }

    override fun openRpc(connection: AiProviderSessionConnection): AiProviderSessionRpc {
        openRpcCalls.incrementAndGet()
        order += "openRpc"
        check(connection === SessionFakeConnection)
        openRpcFailure?.let { throw it }
        return rpc
    }

    fun die() {
        checkNotNull(bindingCallback).onBinderDeath()
    }

    fun failBinding(failure: AiProviderSessionRunFailure) {
        checkNotNull(bindingCallback).onBindingFailure(failure)
    }
}

private object SessionFakeConnection : AiProviderSessionConnection

private class FakeSessionRpc(
    val fixture: SessionCoordinatorFixture,
    private val remote: SessionRecordingRemote,
    private val order: MutableList<String>,
) : AiProviderSessionRpc {
    val providerInfoCalls = AtomicInteger()
    val capabilitiesCalls = AtomicInteger()
    val prepareCalls = AtomicInteger()
    var encodedProviderInfo: ByteArray? = AiCommonCodec.encodeProviderInfo(fixture.providerInfo)
    var encodedCapabilities: ByteArray? = AiProviderCodec.encodeCapabilities(fixture.capabilities)
    var providerInfoFailure: Throwable? = null
    var capabilitiesFailure: Throwable? = null
    var openFailure: Throwable? = null
    lateinit var callback: AiProviderSessionCallbackIngress
    lateinit var sessionDeathCallback: () -> Unit
    lateinit var operationObserver: AiProviderSessionOperationObserver
    lateinit var prepared: FakeSessionPreparedOpen
    var openBehavior: (
        AiProviderSessionCallbackIngress,
        () -> Unit,
        AiProviderSessionOperationObserver,
        ByteArray,
        AiProviderSessionRequestDescriptorLease,
    ) -> AiProviderSessionRemote = { callback, _, _, _, _ ->
        callback.onStarted(fixture.input.packageUid, fixture.startedEnvelope())
        callback.onCompleted(
            fixture.input.packageUid,
            fixture.completionEnvelope("done"),
            SessionRecordingIncomingDescriptors(),
        )
        remote
    }

    override fun getProviderInfoEnvelope(): ByteArray? {
        providerInfoCalls.incrementAndGet()
        order += "getProviderInfo"
        providerInfoFailure?.let { throw it }
        return encodedProviderInfo?.copyOf()
    }

    override fun getCapabilitiesEnvelope(): ByteArray? {
        capabilitiesCalls.incrementAndGet()
        order += "getCapabilities"
        capabilitiesFailure?.let { throw it }
        return encodedCapabilities?.copyOf()
    }

    override fun prepareOpen(
        callback: AiProviderSessionCallbackIngress,
        onSessionBinderDeath: () -> Unit,
        operationObserver: AiProviderSessionOperationObserver,
    ): AiProviderSessionPreparedOpen {
        prepareCalls.incrementAndGet()
        order += "prepareOpen"
        this.callback = callback
        this.sessionDeathCallback = onSessionBinderDeath
        this.operationObserver = operationObserver
        return FakeSessionPreparedOpen(this, order).also { prepared = it }
    }

    fun open(
        requestEnvelope: ByteArray,
        requestDescriptors: AiProviderSessionRequestDescriptorLease,
    ): AiProviderSessionRemote {
        openFailure?.let { throw it }
        return openBehavior(
            callback,
            sessionDeathCallback,
            operationObserver,
            requestEnvelope,
            requestDescriptors,
        )
    }
}

private class FakeSessionPreparedOpen(
    private val rpc: FakeSessionRpc,
    private val order: MutableList<String>,
) : AiProviderSessionPreparedOpen {
    val openCalls = AtomicInteger()
    val closeCount = AtomicInteger()
    var closeBehavior: () -> Unit = {}

    override fun open(
        requestEnvelope: ByteArray,
        requestDescriptors: AiProviderSessionRequestDescriptorLease,
    ): AiProviderSessionRemote {
        openCalls.incrementAndGet()
        return rpc.open(requestEnvelope, requestDescriptors)
    }

    override fun close() {
        closeCount.incrementAndGet()
        closeBehavior()
        order += "prepared.close"
    }
}

private class SessionRecordingBinding : AiProviderSessionBinding {
    val closeCount = AtomicInteger()

    override fun close() {
        closeCount.incrementAndGet()
    }
}

private class SessionRecordingRequestLease(
    private val onClose: () -> Unit = {},
) : AiProviderSessionRequestDescriptorLease {
    override val count: Int = 0
    val closeCount = AtomicInteger()

    override fun close() {
        closeCount.incrementAndGet()
        onClose()
    }
}

private class SessionRecordingDescriptorLease : AiProviderSessionDescriptorLease {
    override val count: Int = 0
    val closeCount = AtomicInteger()

    override fun readDeclaredBytes(index: Int, declaredLengthBytes: Long): ByteArray =
        error("Unexpected descriptor read")

    override fun close() {
        closeCount.incrementAndGet()
    }
}

private class SessionRecordingIncomingDescriptors : AiProviderSessionIncomingDescriptors {
    override val count: Int = 0
    val copyCount = AtomicInteger()
    val closeCount = AtomicInteger()
    val owned = SessionRecordingDescriptorLease()

    override fun copyForAsync(): AiProviderSessionDescriptorLease {
        copyCount.incrementAndGet()
        return owned
    }

    override fun close() {
        closeCount.incrementAndGet()
    }
}

private class SessionRecordingRemote : AiProviderSessionRemote {
    val creditGrants = Collections.synchronizedList(mutableListOf<Int>())
    val cancelCount = AtomicInteger()
    val closeCount = AtomicInteger()

    override fun grantCredits(count: Int) {
        creditGrants += count
    }

    override fun cancel() {
        cancelCount.incrementAndGet()
    }

    override fun close() {
        closeCount.incrementAndGet()
    }
}

private class SessionManualExecutor : Executor {
    private val tasks = ArrayDeque<Runnable>()
    val executeCalls = AtomicInteger()

    override fun execute(command: Runnable) {
        executeCalls.incrementAndGet()
        synchronized(tasks) { tasks.addLast(command) }
    }

    fun drain(): Int {
        var count = 0
        while (true) {
            val task = synchronized(tasks) {
                if (tasks.isEmpty()) null else tasks.removeFirst()
            } ?: return count
            task.run()
            count += 1
            check(count < 2_000) { "Manual executor did not become idle" }
        }
    }
}

private object SessionRejectingExecutor : Executor {
    override fun execute(command: Runnable) {
        throw RejectedExecutionException("rejected for test")
    }
}

private class SessionJoiningThreadExecutor : Executor {
    val executeCalls = AtomicInteger()

    override fun execute(command: Runnable) {
        val call = executeCalls.incrementAndGet()
        val failure = AtomicReference<Throwable?>()
        val completed = java.util.concurrent.CountDownLatch(1)
        val thread = Thread({
            try {
                command.run()
            } catch (caught: Throwable) {
                failure.set(caught)
            } finally {
                completed.countDown()
            }
        }, "session-terminal-fallback-$call")
        thread.start()
        check(completed.await(5, TimeUnit.SECONDS)) {
            "Timed out waiting for terminal fallback"
        }
        thread.join()
        failure.get()?.let { throw AssertionError("Terminal fallback failed", it) }
    }
}

private class FakeSessionDeadlineScheduler : AiProviderSessionDeadlineScheduler {
    val scheduleCalls = AtomicInteger()
    val closeCount = AtomicInteger()
    @Volatile private var action: (() -> Unit)? = null
    private val closed = AtomicBoolean(false)

    override fun schedule(delayMillis: Long, action: () -> Unit): Closeable {
        require(delayMillis > 0L)
        scheduleCalls.incrementAndGet()
        this.action = action
        return Closeable {
            if (closed.compareAndSet(false, true)) closeCount.incrementAndGet()
        }
    }

    fun fire() {
        action?.invoke()
    }
}

private class FakeSessionClock(
    @Volatile var nowMillis: Long = 0L,
) : AiProviderSessionClock {
    override fun elapsedRealtimeMillis(): Long = nowMillis
}

private fun sessionDiscoveryInput(
    versionCode: Long = 7L,
    lastUpdateTime: Long = 123L,
) = AiProviderDiscoveryInput(
    packageName = "example.ai.provider",
    className = "AiProviderService",
    declaredAction = AiProviderProtocol.ACTION_PROVIDER,
    exported = true,
    applicationEnabled = true,
    serviceEnabled = true,
    requiredPermission = AiProviderProtocol.PLUGIN_PERMISSION,
    packageUid = 10_001,
    versionCode = versionCode,
    lastUpdateTime = lastUpdateTime,
    signerSha256 = listOf(AiProviderHostTestFixtures.SIGNER_A),
    officialSignerMatch = true,
    sameSignerAsHost = false,
    debuggable = false,
)

private fun discovered(
    input: AiProviderDiscoveryInput,
): AiProviderDiscoverySourceResult = AiProviderDiscoverySourceResult.Completed(
    listOf(AiProviderDiscoverySourceEntry.Read(input)),
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
