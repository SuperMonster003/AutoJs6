package org.autojs.autojs.core.plugin.dex

import org.autojs.plugin.dexcompiler.api.DexCompileRequest
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.autojs.plugin.dexcompiler.api.DexCompilerInputFormat
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
        val source = TestValueSource()
        val singleFlight = DexCompilerSingleFlight<String, TestLease>(executor)
        val leaderConsumer = startCall {
            singleFlight.execute(KEY) {
                producerCalls.incrementAndGet()
                producerWasInterrupted.set(Thread.currentThread().isInterrupted)
                DexCompilerSingleFlightTerminal.Value(source)
            }
        }
        assertTrue(executor.awaitTask())
        assertTrue(waitUntilWaiting(leaderConsumer.thread))
        val interruptedFollower = startCall {
            singleFlight.execute(KEY) {
                DexCompilerSingleFlightTerminal.Failure(AssertionError("Follower became leader"))
            }
        }

        try {
            assertTrue(waitUntilWaiting(interruptedFollower.thread))
            interruptedFollower.thread.interrupt()
            interruptedFollower.join()
            assertTrue(interruptedFollower.failure.get() is InterruptedException)
            assertNull(interruptedFollower.outcome.get())

            executor.runNext()
            leaderConsumer.join()
            assertTrue(leaderConsumer.outcome.get() is DexCompilerSingleFlightOutcome.Value)
            assertEquals(1, producerCalls.get())
            assertEquals(false, producerWasInterrupted.get())
            assertEquals(1, source.acquireCount.get())
            assertEquals(1, source.closeCount.get())
        } finally {
            leaderConsumer.stop()
            interruptedFollower.stop()
        }
    }

    @Test
    fun lastWaiterInterruptDetachesButR12ProducerStillCompletesAndClosesPublication() {
        val executor = ManualExecutor()
        val producerCalls = AtomicInteger()
        val source = TestValueSource()
        val singleFlight = DexCompilerSingleFlight<String, TestLease>(executor)
        val caller = startCall {
            singleFlight.execute(KEY) {
                producerCalls.incrementAndGet()
                DexCompilerSingleFlightTerminal.Value(source)
            }
        }

        try {
            assertTrue(executor.awaitTask())
            assertTrue(waitUntilWaiting(caller.thread))
            caller.thread.interrupt()
            caller.join()
            assertTrue(caller.failure.get() is InterruptedException)

            executor.runNext()
            assertEquals(1, producerCalls.get())
            assertEquals(0, source.acquireCount.get())
            assertEquals(1, source.closeCount.get())
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
        val source = TestValueSource()
        val coordinator = DexCompilerSingleFlightCallerCoordinator<String, TestLease, TestFallback>(
            DexCompilerSingleFlight<String, TestLease>(executor),
        )
        val leaderFallback = TestFallback("leader")
        val followerFallback = TestFallback("follower")
        val leader = startCall {
            coordinator.execute(KEY, leaderFallback, { producerReleaseCount.incrementAndGet() }) {
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

            executor.runNext()
            follower.join()

            val followerValue = follower.outcome.get() as DexCompilerSingleFlightCallerOutcome.Value<
                TestLease,
                TestFallback,
            >
            assertSame(followerFallback, followerValue.fallback)
            assertEquals(1, producerCalls.get())
            assertEquals(1, producerReleaseCount.get())
            assertEquals(0, followerFallback.closeCount.get())
        } finally {
            leader.stop()
            follower.stop()
            followerFallback.close()
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

private class TestFallback(val name: String) : AutoCloseable {
    val closeCount = AtomicInteger()

    override fun close() {
        check(closeCount.incrementAndGet() == 1) { "Fallback $name closed more than once" }
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
    val thread = Thread {
        try {
            outcome.set(block())
        } catch (error: Throwable) {
            failure.set(error)
        }
    }.apply {
        name = "DexCompilerSingleFlightTest-caller"
        isDaemon = true
        start()
    }
    return TestCall(thread, outcome, failure)
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
