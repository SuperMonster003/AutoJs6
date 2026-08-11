package org.autojs.autojs.core.plugin.dex

import org.autojs.plugin.dexcompiler.api.DexCompileRequest
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.autojs.plugin.dexcompiler.api.DexCompilerInputBundleCodec
import org.autojs.plugin.dexcompiler.api.DexCompilerInputFormat
import org.autojs.plugin.dexcompiler.api.DexCompilerInputIdentity
import org.autojs.plugin.dexcompiler.api.DexCompilerInputLayout
import org.autojs.plugin.dexcompiler.api.DexCompilerInputRole
import org.autojs.plugin.dexcompiler.api.DexCompilerInputSetFingerprint
import org.autojs.plugin.dexcompiler.api.DexCompilerMode
import org.autojs.plugin.dexcompiler.api.DexCompilerOutputFormat
import org.autojs.plugin.dexcompiler.api.DexProtocolVersion
import org.autojs.plugin.dexcompiler.api.DexRequestId
import org.autojs.plugin.dexcompiler.api.DexRuntimeLibraryModel
import org.autojs.plugin.dexcompiler.api.DexSha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

class DexCompilerSingleFlightAttemptKeyTest {

    @Test
    fun callerRequestIdIsExcludedButPinnedIdentityAndHostApiAreIncluded() {
        val firstRequest = request()
        val secondRequest = request()
        val identity = identity(versionCode = 7L)

        val first = DexCompilerSingleFlightAttemptKey.from(firstRequest, identity, HOST_API)
        val second = DexCompilerSingleFlightAttemptKey.from(secondRequest, identity, HOST_API)

        assertNotEquals(firstRequest.requestId, secondRequest.requestId)
        assertEquals(first, second)
        assertEquals(PROGRAM_BYTES.size.toLong(), first.inputSizeBytes)
        assertEquals(DexSha256.digest(PROGRAM_BYTES).toHexString(), first.inputSha256)
        assertNotEquals(first, DexCompilerSingleFlightAttemptKey.from(secondRequest, identity(8L), HOST_API))
        assertNotEquals(first, DexCompilerSingleFlightAttemptKey.from(secondRequest, identity, HOST_API + 1))
        assertNotEquals(
            first,
            DexCompilerSingleFlightAttemptKey.from(
                secondRequest.copy(timeoutMillis = secondRequest.timeoutMillis + 1L),
                identity,
                HOST_API,
            ),
        )
    }

    @Test
    fun orderedClasspathFingerprintSeparatesSingleFlightAttempts() {
        val firstClasspath = "first-classpath".toByteArray()
        val secondClasspath = "second-classpath".toByteArray()
        val identity = identity(versionCode = 7L)
        val first = DexCompilerSingleFlightAttemptKey.from(
            classpathRequest(listOf(firstClasspath, secondClasspath)),
            identity,
            HOST_API,
        )
        val reordered = DexCompilerSingleFlightAttemptKey.from(
            classpathRequest(listOf(secondClasspath, firstClasspath)),
            identity,
            HOST_API,
        )

        assertNotEquals(first, reordered)
        assertNotEquals(first.inputSha256, reordered.inputSha256)
    }

    private fun identity(versionCode: Long) = DexCompilerPinnedIdentity(
        component = DexCompilerComponent("example.dex.compiler", "CompilerService"),
        packageUid = 10_001,
        versionCode = versionCode,
        lastUpdateTime = 123L,
        signerSha256 = setOf("ab".repeat(32)),
    )

    private fun request() = DexCompileRequest(
        requestId = DexRequestId.fromUuid(UUID.randomUUID()),
        protocolVersion = DexProtocolVersion(
            DexCompilerContract.PROTOCOL_MAJOR,
            DexCompilerContract.PROTOCOL_MINOR,
        ),
        inputFormat = DexCompilerInputFormat.JAR,
        outputFormat = DexCompilerOutputFormat.DEX_ZIP,
        mode = DexCompilerMode.RELEASE,
        minApi = HOST_API,
        programSizeBytes = PROGRAM_BYTES.size.toLong(),
        programSha256 = DexSha256.digest(PROGRAM_BYTES),
        maxOutputBytes = 16L * 1024L * 1024L,
        runtimeLibraryModel = DexRuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1,
        expectedRuntimeLibraryFingerprint = DexSha256.fromBytes(ByteArray(DexSha256.BYTE_COUNT)),
        canonicalizationPolicyVersion = DexCompilerContract.CANONICALIZATION_POLICY_VERSION,
        diagnosticByteLimit = DexCompilerContract.MAX_DIAGNOSTIC_BYTES,
        timeoutMillis = DexCompilerContract.DEFAULT_TIMEOUT_MILLIS,
    )

    private fun classpathRequest(classpath: List<ByteArray>): DexCompileRequest {
        val identities = listOf(
            DexCompilerInputIdentity(
                DexCompilerInputRole.PROGRAM,
                0,
                PROGRAM_BYTES.size.toLong(),
                DexSha256.digest(PROGRAM_BYTES),
            ),
        ) + classpath.mapIndexed { index, bytes ->
            DexCompilerInputIdentity(
                DexCompilerInputRole.CLASSPATH,
                index,
                bytes.size.toLong(),
                DexSha256.digest(bytes),
            )
        }
        val inputSet = DexCompilerInputSetFingerprint.compute(identities)
        return request().copy(
            protocolVersion = DexCompilerContract.PROTOCOL_V1_1,
            inputLayout = DexCompilerInputLayout.PROGRAM_AND_ORDERED_CLASSPATH_BUNDLE_V1,
            inputIdentities = identities,
            inputSetFingerprint = inputSet,
            inputBundleSizeBytes = DexCompilerInputBundleCodec.encodedSize(identities),
            inputBundleSha256 = inputSet,
        )
    }

    private companion object {
        val PROGRAM_BYTES = "single-flight-attempt-key".toByteArray()
        const val HOST_API = 28
    }
}

class DexCompilerSingleFlightTest {

    @Test
    fun overlappingSameKeyRunsOneLeaderAndAcquiresIndependentValues() {
        val executor = ManualExecutor()
        val source = TestValueSource()
        val producerCalls = AtomicInteger()
        val singleFlight = DexCompilerSingleFlight<String, TestLease>(executor)
        val first = startCall {
            singleFlight.execute(KEY) {
                producerCalls.incrementAndGet()
                DexCompilerSingleFlightTerminal.Value(source)
            }
        }
        assertTrue(executor.awaitTask())
        assertTrue(waitUntilWaiting(first.thread))
        val second = startCall {
            singleFlight.execute(KEY) {
                producerCalls.incrementAndGet()
                DexCompilerSingleFlightTerminal.Failure(AssertionError("Follower became leader"))
            }
        }

        try {
            assertTrue(waitUntilWaiting(second.thread))
            executor.runNext()
            first.join()
            second.join()

            assertNull(first.failure.get())
            assertNull(second.failure.get())
            val firstValue = first.outcome.get() as DexCompilerSingleFlightOutcome.Value
            val secondValue = second.outcome.get() as DexCompilerSingleFlightOutcome.Value
            assertNotSame(firstValue.value, secondValue.value)
            assertNotEquals(firstValue.value.leaseId, secondValue.value.leaseId)
            assertEquals(1, producerCalls.get())
            assertEquals(2, source.acquireCount.get())
            assertEquals(1, source.closeCount.get())
        } finally {
            first.stop()
            second.stop()
        }
    }

    @Test
    fun sharedFailureIsRemovedAndNextCallStartsANewAttempt() {
        val executor = ManualExecutor()
        val producerCalls = AtomicInteger()
        val sharedFailure = IllegalStateException("leader failed")
        val singleFlight = DexCompilerSingleFlight<String, TestLease>(executor)
        val first = startCall {
            singleFlight.execute(KEY) {
                producerCalls.incrementAndGet()
                throw sharedFailure
            }
        }
        assertTrue(executor.awaitTask())
        assertTrue(waitUntilWaiting(first.thread))
        val follower = startCall {
            singleFlight.execute(KEY) {
                producerCalls.incrementAndGet()
                DexCompilerSingleFlightTerminal.Cancelled
            }
        }

        try {
            assertTrue(waitUntilWaiting(follower.thread))
            executor.runNext()
            first.join()
            follower.join()

            assertSame(
                sharedFailure,
                (first.outcome.get() as DexCompilerSingleFlightOutcome.Failure).cause,
            )
            assertSame(
                sharedFailure,
                (follower.outcome.get() as DexCompilerSingleFlightOutcome.Failure).cause,
            )

            val retrySource = TestValueSource()
            val retry = startCall {
                singleFlight.execute(KEY) {
                    producerCalls.incrementAndGet()
                    DexCompilerSingleFlightTerminal.Value(retrySource)
                }
            }
            try {
                assertTrue(executor.awaitTask())
                executor.runNext()
                retry.join()
                assertTrue(retry.outcome.get() is DexCompilerSingleFlightOutcome.Value)
                assertEquals(2, producerCalls.get())
                assertEquals(1, retrySource.closeCount.get())
            } finally {
                retry.stop()
            }
        } finally {
            first.stop()
            follower.stop()
        }
    }

    @Test
    fun producerVirtualMachineErrorCompletesFlightReleasesResourcesAndEscapesExecutor() {
        val executor = ManualExecutor()
        val releaseCount = AtomicInteger()
        val fatal = OutOfMemoryError("synthetic producer failure")
        val singleFlight = DexCompilerSingleFlight<String, TestLease>(executor)
        val caller = startCall {
            singleFlight.execute(
                key = KEY,
                releaseProducerResources = { releaseCount.incrementAndGet() },
            ) {
                throw fatal
            }
        }

        try {
            assertTrue(executor.awaitTask())
            assertTrue(waitUntilWaiting(caller.thread))

            val escaped = assertThrows(OutOfMemoryError::class.java) { executor.runNext() }
            assertSame(fatal, escaped)
            caller.join()

            assertSame(fatal, caller.failure.get())
            assertNull(caller.outcome.get())
            assertEquals(1, releaseCount.get())
        } finally {
            caller.stop()
        }
    }

    @Test
    fun cancelledTerminalIsNotCached() {
        val producerCalls = AtomicInteger()
        val directExecutor = Executor { command -> command.run() }
        val singleFlight = DexCompilerSingleFlight<String, TestLease>(directExecutor)

        val first = singleFlight.execute(KEY) {
            producerCalls.incrementAndGet()
            DexCompilerSingleFlightTerminal.Cancelled
        }
        val source = TestValueSource()
        val second = singleFlight.execute(KEY) {
            producerCalls.incrementAndGet()
            DexCompilerSingleFlightTerminal.Value(source)
        }

        assertSame(DexCompilerSingleFlightOutcome.Cancelled, first)
        assertTrue(second is DexCompilerSingleFlightOutcome.Value)
        assertEquals(2, producerCalls.get())
        assertEquals(1, source.closeCount.get())
    }

    @Test
    fun interruptingFollowerDoesNotCancelLeaderOrRemainingConsumer() {
        val executor = ManualExecutor()
        val producerCalls = AtomicInteger()
        val producerWasInterrupted = AtomicReference<Boolean>()
        val leaderCancellationRequests = AtomicInteger()
        val followerCancellationRequests = AtomicInteger()
        val source = TestValueSource()
        val singleFlight = DexCompilerSingleFlight<String, TestLease>(executor)
        val leaderConsumer = startCall {
            singleFlight.execute(
                key = KEY,
                requestProducerCancellation = { leaderCancellationRequests.incrementAndGet() },
            ) {
                producerCalls.incrementAndGet()
                producerWasInterrupted.set(Thread.currentThread().isInterrupted)
                DexCompilerSingleFlightTerminal.Value(source)
            }
        }
        assertTrue(executor.awaitTask())
        assertTrue(waitUntilWaiting(leaderConsumer.thread))
        val interruptedFollower = startCall {
            singleFlight.execute(
                key = KEY,
                requestProducerCancellation = { followerCancellationRequests.incrementAndGet() },
            ) {
                DexCompilerSingleFlightTerminal.Failure(AssertionError("Follower became leader"))
            }
        }

        try {
            assertTrue(waitUntilWaiting(interruptedFollower.thread))
            interruptedFollower.thread.interrupt()
            interruptedFollower.join()
            assertTrue(interruptedFollower.failure.get() is InterruptedException)
            assertNull(interruptedFollower.outcome.get())
            assertEquals(0, leaderCancellationRequests.get())
            assertEquals(0, followerCancellationRequests.get())

            executor.runNext()
            leaderConsumer.join()
            assertTrue(leaderConsumer.outcome.get() is DexCompilerSingleFlightOutcome.Value)
            assertEquals(1, producerCalls.get())
            assertEquals(false, producerWasInterrupted.get())
            assertEquals(0, leaderCancellationRequests.get())
            assertEquals(0, followerCancellationRequests.get())
            assertEquals(1, source.acquireCount.get())
            assertEquals(1, source.closeCount.get())
        } finally {
            leaderConsumer.stop()
            interruptedFollower.stop()
        }
    }

    @Test
    fun lastWaiterInterruptRequestsLeaderCancellationAndMakesLateTerminalUnjoinable() {
        val executor = ManualExecutor()
        val producerCalls = AtomicInteger()
        val producerReleaseCount = AtomicInteger()
        val cancellationRequests = AtomicInteger()
        val lateSource = TestValueSource()
        val singleFlight = DexCompilerSingleFlight<String, TestLease>(executor)
        val caller = startCall {
            singleFlight.execute(
                key = KEY,
                releaseProducerResources = { producerReleaseCount.incrementAndGet() },
                requestProducerCancellation = { cancellationRequests.incrementAndGet() },
            ) {
                producerCalls.incrementAndGet()
                DexCompilerSingleFlightTerminal.Value(lateSource)
            }
        }
        var retry: TestCall<DexCompilerSingleFlightOutcome<TestLease>>? = null

        try {
            assertTrue(executor.awaitTask())
            assertTrue(waitUntilWaiting(caller.thread))
            caller.thread.interrupt()
            caller.join()
            assertTrue(caller.failure.get() is InterruptedException)
            assertNull(caller.outcome.get())
            assertEquals(1, cancellationRequests.get())
            assertEquals(0, producerReleaseCount.get())

            val retrySource = TestValueSource()
            val retryCancellationRequests = AtomicInteger()
            retry = startCall {
                singleFlight.execute(
                    key = KEY,
                    requestProducerCancellation = { retryCancellationRequests.incrementAndGet() },
                ) {
                    producerCalls.incrementAndGet()
                    DexCompilerSingleFlightTerminal.Value(retrySource)
                }
            }
            assertTrue(waitUntilWaiting(checkNotNull(retry).thread))

            // The abandoned producer may still reach a late terminal. It owns no waiter and its
            // value source must close without consuming or removing the replacement flight.
            executor.runNext()
            assertTrue(waitUntil { lateSource.closeCount.get() == 1 })
            assertEquals(0, lateSource.acquireCount.get())
            assertEquals(1, producerReleaseCount.get())
            assertTrue(checkNotNull(retry).thread.isAlive)

            executor.runNext()
            checkNotNull(retry).join()
            assertTrue(checkNotNull(retry).outcome.get() is DexCompilerSingleFlightOutcome.Value)
            assertEquals(2, producerCalls.get())
            assertEquals(1, retrySource.acquireCount.get())
            assertEquals(1, retrySource.closeCount.get())
            assertEquals(0, retryCancellationRequests.get())
        } finally {
            caller.stop()
            retry?.stop()
        }
    }

    @Test
    fun cancellationHookVirtualMachineErrorEscapesWithCallerInterruptRestored() {
        val executor = ManualExecutor()
        val releaseCount = AtomicInteger()
        val fatal = OutOfMemoryError("synthetic cancellation hook failure")
        val singleFlight = DexCompilerSingleFlight<String, TestLease>(executor)
        val caller = startCall {
            singleFlight.execute(
                key = KEY,
                releaseProducerResources = { releaseCount.incrementAndGet() },
                requestProducerCancellation = { throw fatal },
            ) {
                DexCompilerSingleFlightTerminal.Cancelled
            }
        }

        try {
            assertTrue(executor.awaitTask())
            assertTrue(waitUntilWaiting(caller.thread))
            caller.thread.interrupt()
            caller.join()

            assertSame(fatal, caller.failure.get())
            assertNull(caller.outcome.get())
            assertEquals(true, caller.interruptedOnExit.get())
            assertTrue(fatal.suppressed.any { it is InterruptedException })

            executor.runNext()
            assertEquals(1, releaseCount.get())
        } finally {
            caller.stop()
        }
    }

    @Test
    fun oneAcquireFailureDoesNotConsumeAnotherCallersValue() {
        val executor = ManualExecutor()
        val source = TestValueSource(failingAcquireNumber = 1)
        val singleFlight = DexCompilerSingleFlight<String, TestLease>(executor)
        val first = startCall {
            singleFlight.execute(KEY) { DexCompilerSingleFlightTerminal.Value(source) }
        }
        assertTrue(executor.awaitTask())
        assertTrue(waitUntilWaiting(first.thread))
        val second = startCall {
            singleFlight.execute(KEY) { DexCompilerSingleFlightTerminal.Cancelled }
        }

        try {
            assertTrue(waitUntilWaiting(second.thread))
            executor.runNext()
            first.join()
            second.join()

            val outcomes = listOf(first.outcome.get(), second.outcome.get())
            assertEquals(1, outcomes.count { it is DexCompilerSingleFlightOutcome.Failure })
            assertEquals(1, outcomes.count { it is DexCompilerSingleFlightOutcome.Value })
            assertEquals(2, source.acquireCount.get())
            assertEquals(1, source.closeCount.get())
        } finally {
            first.stop()
            second.stop()
        }
    }

    @Test
    fun leaderCallerInterruptKeepsProducerResourcesWhileFollowerResourcesReleaseImmediately() {
        val executor = ManualExecutor()
        val source = TestValueSource()
        val leaderReleaseCount = AtomicInteger()
        val followerReleaseCount = AtomicInteger()
        val producerCalls = AtomicInteger()
        val singleFlight = DexCompilerSingleFlight<String, TestLease>(executor)
        val leaderCaller = startCall {
            singleFlight.execute(
                key = KEY,
                releaseProducerResources = { leaderReleaseCount.incrementAndGet() },
            ) {
                producerCalls.incrementAndGet()
                DexCompilerSingleFlightTerminal.Value(source)
            }
        }
        assertTrue(executor.awaitTask())
        assertTrue(waitUntilWaiting(leaderCaller.thread))
        val follower = startCall {
            singleFlight.execute(
                key = KEY,
                releaseProducerResources = { followerReleaseCount.incrementAndGet() },
            ) {
                DexCompilerSingleFlightTerminal.Failure(AssertionError("Follower became leader"))
            }
        }

        try {
            assertTrue(waitUntil { followerReleaseCount.get() == 1 })
            assertTrue(waitUntilWaiting(follower.thread))
            assertEquals(0, leaderReleaseCount.get())

            leaderCaller.thread.interrupt()
            leaderCaller.join()
            assertTrue(leaderCaller.failure.get() is InterruptedException)
            assertEquals(0, leaderReleaseCount.get())

            executor.runNext()
            follower.join()

            assertTrue(follower.outcome.get() is DexCompilerSingleFlightOutcome.Value)
            assertEquals(1, producerCalls.get())
            assertEquals(1, leaderReleaseCount.get())
            assertEquals(1, followerReleaseCount.get())
            assertEquals(1, source.acquireCount.get())
            assertEquals(1, source.closeCount.get())
        } finally {
            leaderCaller.stop()
            follower.stop()
        }
    }

    @Test
    fun executorRejectionReleasesLeaderResourcesAndDoesNotCacheFailure() {
        val executorCalls = AtomicInteger()
        val producerCalls = AtomicInteger()
        val releaseCount = AtomicInteger()
        val source = TestValueSource()
        val executor = Executor { command ->
            if (executorCalls.incrementAndGet() == 1) {
                throw RejectedExecutionException("orchestration queue is full")
            }
            command.run()
        }
        val singleFlight = DexCompilerSingleFlight<String, TestLease>(executor)

        val first = singleFlight.execute(
            key = KEY,
            releaseProducerResources = { releaseCount.incrementAndGet() },
        ) {
            producerCalls.incrementAndGet()
            DexCompilerSingleFlightTerminal.Value(source)
        }
        val second = singleFlight.execute(
            key = KEY,
            releaseProducerResources = { releaseCount.incrementAndGet() },
        ) {
            producerCalls.incrementAndGet()
            DexCompilerSingleFlightTerminal.Value(source)
        }

        assertTrue((first as DexCompilerSingleFlightOutcome.Failure).cause is RejectedExecutionException)
        assertTrue(second is DexCompilerSingleFlightOutcome.Value)
        assertEquals(2, executorCalls.get())
        assertEquals(1, producerCalls.get())
        assertEquals(2, releaseCount.get())
        assertEquals(1, source.acquireCount.get())
        assertEquals(1, source.closeCount.get())
    }

    @Test
    fun publishedArtifactSourcePerformsAFreshExactCacheLookupForEveryCaller() {
        val executor = ManualExecutor()
        val lookupCount = AtomicInteger()
        val cacheKey = "ef".repeat(32)
        val source = DexCompilerSingleFlightArtifactSource(
            cacheKeySha256 = cacheKey,
            cacheHit = false,
            cache = DexCompilerVerifiedCache { requestedKey ->
                assertEquals(cacheKey, requestedKey)
                SingleFlightTestVerifiedArtifact(requestedKey, lookupCount.incrementAndGet())
            },
        )
        val singleFlight = DexCompilerSingleFlight<String, DexCompilerSingleFlightPublishedArtifact>(executor)
        val first = startCall {
            singleFlight.execute(KEY) { DexCompilerSingleFlightTerminal.Value(source) }
        }
        assertTrue(executor.awaitTask())
        assertTrue(waitUntilWaiting(first.thread))
        val second = startCall {
            singleFlight.execute(KEY) {
                DexCompilerSingleFlightTerminal.Failure(AssertionError("Follower became leader"))
            }
        }

        try {
            assertTrue(waitUntilWaiting(second.thread))
            executor.runNext()
            first.join()
            second.join()

            val firstValue = (first.outcome.get() as DexCompilerSingleFlightOutcome.Value).value
            val secondValue = (second.outcome.get() as DexCompilerSingleFlightOutcome.Value).value
            assertNotSame(firstValue.artifact, secondValue.artifact)
            assertEquals(cacheKey, firstValue.artifact.cacheKeySha256)
            assertEquals(cacheKey, secondValue.artifact.cacheKeySha256)
            assertEquals(2, lookupCount.get())
            assertFalse(firstValue.cacheHit)
            assertFalse(secondValue.cacheHit)
        } finally {
            first.stop()
            second.stop()
        }
    }

    private companion object {
        const val KEY = "fixed-attempt-key"
    }
}

class DexCompilerSingleFlightCallerCoordinatorTest {

    @Test
    fun sameKeyCallersShareOneProducerButKeepPrivateFallbacksAndCapabilities() {
        val executor = ManualExecutor()
        val producerCalls = AtomicInteger()
        val lookupCount = AtomicInteger()
        val cacheKey = "ac".repeat(32)
        val source = DexCompilerSingleFlightArtifactSource(
            cacheKeySha256 = cacheKey,
            cacheHit = false,
            cache = DexCompilerVerifiedCache { key ->
                SingleFlightTestVerifiedArtifact(key, lookupCount.incrementAndGet())
            },
        )
        val coordinator = DexCompilerSingleFlightCallerCoordinator<
            String,
            DexCompilerSingleFlightPublishedArtifact,
            TestFallback,
        >(
            DexCompilerSingleFlight<String, DexCompilerSingleFlightPublishedArtifact>(executor),
        )
        val firstFallback = TestFallback("first")
        val secondFallback = TestFallback("second")
        val first = startCall {
            coordinator.execute(KEY, firstFallback, {}) {
                producerCalls.incrementAndGet()
                DexCompilerSingleFlightTerminal.Value(source)
            }
        }
        assertTrue(executor.awaitTask())
        assertTrue(waitUntilWaiting(first.thread))
        val second = startCall {
            coordinator.execute(KEY, secondFallback, {}) {
                DexCompilerSingleFlightTerminal.Failure(AssertionError("Follower became leader"))
            }
        }

        try {
            assertTrue(waitUntilWaiting(second.thread))
            executor.runNext()
            first.join()
            second.join()

            val firstValue = first.outcome.get() as DexCompilerSingleFlightCallerOutcome.Value<
                DexCompilerSingleFlightPublishedArtifact,
                TestFallback,
            >
            val secondValue = second.outcome.get() as DexCompilerSingleFlightCallerOutcome.Value<
                DexCompilerSingleFlightPublishedArtifact,
                TestFallback,
            >
            assertSame(firstFallback, firstValue.fallback)
            assertSame(secondFallback, secondValue.fallback)
            assertNotSame(firstValue.value.artifact, secondValue.value.artifact)
            assertEquals(1, producerCalls.get())
            assertEquals(2, lookupCount.get())
            assertEquals(0, firstFallback.closeCount.get())
            assertEquals(0, secondFallback.closeCount.get())
        } finally {
            first.stop()
            second.stop()
            firstFallback.close()
            secondFallback.close()
        }
    }

    @Test
    fun interruptingLeaderCallerClosesOnlyItsFallbackAndDoesNotCancelSharedProducer() {
        val executor = ManualExecutor()
        val producerCalls = AtomicInteger()
        val producerReleaseCount = AtomicInteger()
        val producerCancellationRequests = AtomicInteger()
        val source = TestValueSource()
        val coordinator = DexCompilerSingleFlightCallerCoordinator<String, TestLease, TestFallback>(
            DexCompilerSingleFlight<String, TestLease>(executor),
        )
        val leaderFallback = TestFallback("leader")
        val followerFallback = TestFallback("follower")
        val leader = startCall {
            coordinator.execute(
                key = KEY,
                fallback = leaderFallback,
                releaseProducerResources = { producerReleaseCount.incrementAndGet() },
                requestProducerCancellation = { producerCancellationRequests.incrementAndGet() },
            ) {
                producerCalls.incrementAndGet()
                DexCompilerSingleFlightTerminal.Value(source)
            }
        }
        assertTrue(executor.awaitTask())
        assertTrue(waitUntilWaiting(leader.thread))
        val follower = startCall {
            coordinator.execute(KEY, followerFallback, {}) {
                DexCompilerSingleFlightTerminal.Failure(AssertionError("Follower became leader"))
            }
        }

        try {
            assertTrue(waitUntilWaiting(follower.thread))
            leader.thread.interrupt()
            leader.join()
            assertTrue(leader.failure.get() is InterruptedException)
            assertEquals(1, leaderFallback.closeCount.get())
            assertEquals(0, followerFallback.closeCount.get())
            assertEquals(0, producerReleaseCount.get())
            assertEquals(0, producerCancellationRequests.get())

            executor.runNext()
            follower.join()

            val followerValue = follower.outcome.get() as DexCompilerSingleFlightCallerOutcome.Value<
                TestLease,
                TestFallback,
            >
            assertSame(followerFallback, followerValue.fallback)
            assertEquals(1, producerCalls.get())
            assertEquals(1, producerReleaseCount.get())
            assertEquals(0, producerCancellationRequests.get())
            assertEquals(0, followerFallback.closeCount.get())
        } finally {
            leader.stop()
            follower.stop()
            followerFallback.close()
        }
    }

    @Test
    fun interruptingLastCallerClosesItsFallbackAndCancelsProducerWithoutFallback() {
        val executor = ManualExecutor()
        val producerReleaseCount = AtomicInteger()
        val producerCancellationRequests = AtomicInteger()
        val source = TestValueSource()
        val coordinator = DexCompilerSingleFlightCallerCoordinator<String, TestLease, TestFallback>(
            DexCompilerSingleFlight<String, TestLease>(executor),
        )
        val fallback = TestFallback("last-waiter")
        val caller = startCall {
            coordinator.execute(
                key = KEY,
                fallback = fallback,
                releaseProducerResources = { producerReleaseCount.incrementAndGet() },
                requestProducerCancellation = { producerCancellationRequests.incrementAndGet() },
            ) {
                DexCompilerSingleFlightTerminal.Value(source)
            }
        }

        try {
            assertTrue(executor.awaitTask())
            assertTrue(waitUntilWaiting(caller.thread))
            caller.thread.interrupt()
            caller.join()

            assertTrue(caller.failure.get() is InterruptedException)
            assertNull(caller.outcome.get())
            assertEquals(1, fallback.closeCount.get())
            assertEquals(1, producerCancellationRequests.get())
            assertEquals(0, producerReleaseCount.get())

            executor.runNext()
            assertEquals(1, producerReleaseCount.get())
            assertEquals(0, source.acquireCount.get())
            assertEquals(1, source.closeCount.get())
        } finally {
            caller.stop()
        }
    }

    @Test
    fun fallbackCloseVirtualMachineErrorEscapesButNonFatalCloseKeepsCancellationSemantics() {
        val fatalExecutor = ManualExecutor()
        val fatal = OutOfMemoryError("synthetic fallback close failure")
        val fatalFallback = TestFallback("fatal-close", fatal)
        val fatalCoordinator = DexCompilerSingleFlightCallerCoordinator<String, TestLease, TestFallback>(
            DexCompilerSingleFlight<String, TestLease>(fatalExecutor),
        )
        val fatalCaller = startCall {
            fatalCoordinator.execute(KEY, fatalFallback, {}) {
                DexCompilerSingleFlightTerminal.Cancelled
            }
        }

        try {
            assertTrue(fatalExecutor.awaitTask())
            assertTrue(waitUntilWaiting(fatalCaller.thread))
            fatalCaller.thread.interrupt()
            fatalCaller.join()

            assertSame(fatal, fatalCaller.failure.get())
            assertNull(fatalCaller.outcome.get())
            assertEquals(1, fatalFallback.closeCount.get())
            assertEquals(true, fatalCaller.interruptedOnExit.get())
            assertTrue(fatal.suppressed.any { it is InterruptedException })
            fatalExecutor.runNext()
        } finally {
            fatalCaller.stop()
        }

        val nonFatalExecutor = ManualExecutor()
        val nonFatal = IllegalStateException("synthetic non-fatal fallback close failure")
        val nonFatalFallback = TestFallback("non-fatal-close", nonFatal)
        val nonFatalCoordinator = DexCompilerSingleFlightCallerCoordinator<String, TestLease, TestFallback>(
            DexCompilerSingleFlight<String, TestLease>(nonFatalExecutor),
        )
        val nonFatalCaller = startCall {
            nonFatalCoordinator.execute(KEY, nonFatalFallback, {}) {
                DexCompilerSingleFlightTerminal.Cancelled
            }
        }

        try {
            assertTrue(nonFatalExecutor.awaitTask())
            assertTrue(waitUntilWaiting(nonFatalCaller.thread))
            nonFatalCaller.thread.interrupt()
            nonFatalCaller.join()

            val interrupted = nonFatalCaller.failure.get() as InterruptedException
            assertNull(nonFatalCaller.outcome.get())
            assertEquals(1, nonFatalFallback.closeCount.get())
            assertTrue(interrupted.suppressed.contains(nonFatal))
            nonFatalExecutor.runNext()
        } finally {
            nonFatalCaller.stop()
        }
    }

    @Test
    fun sharedFailureReturnsEachCallerFallbackAndNextCallRetries() {
        val producerCalls = AtomicInteger()
        val failure = IllegalStateException("shared attempt failed")
        val coordinator = DexCompilerSingleFlightCallerCoordinator<String, TestLease, TestFallback>(
            DexCompilerSingleFlight<String, TestLease>(Executor { it.run() }),
        )
        val firstFallback = TestFallback("failure")
        val retryFallback = TestFallback("retry")

        val first = coordinator.execute(KEY, firstFallback, {}) {
            producerCalls.incrementAndGet()
            DexCompilerSingleFlightTerminal.Failure(failure)
        }
        val retrySource = TestValueSource()
        val retry = coordinator.execute(KEY, retryFallback, {}) {
            producerCalls.incrementAndGet()
            DexCompilerSingleFlightTerminal.Value(retrySource)
        }

        assertSame(failure, (first as DexCompilerSingleFlightCallerOutcome.Failure).cause)
        assertSame(firstFallback, first.fallback)
        assertSame(retryFallback, (retry as DexCompilerSingleFlightCallerOutcome.Value).fallback)
        assertEquals(2, producerCalls.get())
        assertEquals(0, firstFallback.closeCount.get())
        assertEquals(0, retryFallback.closeCount.get())
        firstFallback.close()
        retryFallback.close()
    }

    @Test
    fun sharedCancellationClosesFallbackAndNeverExposesLocalFallback() {
        val fallback = TestFallback("cancelled")
        val coordinator = DexCompilerSingleFlightCallerCoordinator<String, TestLease, TestFallback>(
            DexCompilerSingleFlight<String, TestLease>(Executor { it.run() }),
        )

        val outcome = coordinator.execute(KEY, fallback, {}) {
            DexCompilerSingleFlightTerminal.Cancelled
        }

        assertSame(DexCompilerSingleFlightCallerOutcome.Cancelled, outcome)
        assertEquals(1, fallback.closeCount.get())
    }

    private companion object {
        const val KEY = "coordinator-key"
    }
}

private data class TestLease(val leaseId: Int)

private class TestFallback(
    val name: String,
    private val closeFailure: Throwable? = null,
) : AutoCloseable {
    val closeCount = AtomicInteger()

    override fun close() {
        check(closeCount.incrementAndGet() == 1) { "Fallback $name closed more than once" }
        closeFailure?.let { throw it }
    }
}

private class SingleFlightTestVerifiedArtifact(
    override val cacheKeySha256: String,
    private val generation: Int,
) : DexCompilerVerifiedArtifact {
    private val bytes = "generation-$generation".toByteArray()
    override val outputSha256: String = DexSha256.digest(bytes).toHexString()
    override val sizeBytes: Long = bytes.size.toLong()

    override fun openRead() = ByteArrayInputStream(bytes)

    override fun invalidate(): Boolean = true
}

private class TestValueSource(
    private val failingAcquireNumber: Int? = null,
) : DexCompilerSingleFlightValueSource<TestLease> {
    val acquireCount = AtomicInteger()
    val closeCount = AtomicInteger()

    override fun acquireForCaller(): TestLease {
        val number = acquireCount.incrementAndGet()
        if (number == failingAcquireNumber) throw IllegalStateException("lease acquisition failed")
        return TestLease(number)
    }

    override fun close() {
        check(closeCount.incrementAndGet() == 1) { "Value source closed more than once" }
    }
}

private class ManualExecutor : Executor {
    private val tasks = LinkedBlockingQueue<Runnable>()

    override fun execute(command: Runnable) {
        tasks.add(command)
    }

    fun awaitTask(): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L)
        while (System.nanoTime() < deadline) {
            if (tasks.isNotEmpty()) return true
            Thread.yield()
        }
        return tasks.isNotEmpty()
    }

    fun runNext() {
        val task = tasks.poll(2L, TimeUnit.SECONDS)
        checkNotNull(task) { "No single-flight leader task was queued" }
        task.run()
    }
}

private class TestCall<O : Any>(
    val thread: Thread,
    val outcome: AtomicReference<O?>,
    val failure: AtomicReference<Throwable?>,
    val interruptedOnExit: AtomicReference<Boolean>,
) {
    fun join() {
        thread.join(2_000L)
        assertFalse("Single-flight caller is still alive", thread.isAlive)
    }

    fun stop() {
        thread.interrupt()
        thread.join(2_000L)
    }
}

private fun <O : Any> startCall(
    block: () -> O,
): TestCall<O> {
    val outcome = AtomicReference<O?>()
    val failure = AtomicReference<Throwable?>()
    val interruptedOnExit = AtomicReference<Boolean>()
    val thread = Thread {
        try {
            outcome.set(block())
        } catch (error: Throwable) {
            failure.set(error)
        } finally {
            interruptedOnExit.set(Thread.currentThread().isInterrupted)
        }
    }.apply {
        name = "DexCompilerSingleFlightTest-caller"
        isDaemon = true
        start()
    }
    return TestCall(thread, outcome, failure, interruptedOnExit)
}

private fun waitUntilWaiting(thread: Thread): Boolean {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L)
    while (System.nanoTime() < deadline) {
        if (thread.state == Thread.State.WAITING) return true
        if (!thread.isAlive) return false
        Thread.yield()
    }
    return thread.state == Thread.State.WAITING
}

private fun waitUntil(condition: () -> Boolean): Boolean {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L)
    while (System.nanoTime() < deadline) {
        if (condition()) return true
        Thread.yield()
    }
    return condition()
}
