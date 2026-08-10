package org.autojs.autojs.core.plugin.dex

import org.autojs.plugin.dexcompiler.api.DexCompileResult
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.autojs.plugin.dexcompiler.api.DexCompilerErrorCode
import org.autojs.plugin.dexcompiler.api.DexCompilerFailurePhase
import org.autojs.plugin.dexcompiler.api.DexCompilerFamily
import org.autojs.plugin.dexcompiler.api.DexCompilerInputFormat
import org.autojs.plugin.dexcompiler.api.DexCompilerMode
import org.autojs.plugin.dexcompiler.api.DexCompilerOutputFormat
import org.autojs.plugin.dexcompiler.api.DexProtocolVersion
import org.autojs.plugin.dexcompiler.api.DexRuntimeLibraryModel
import org.autojs.plugin.dexcompiler.api.DexSha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DexCompilerRuntimeRequestFactoryTest {

    @Test
    fun requestCapturesEverySnapshotAndFixedRuntimeField() {
        val snapshot = TestInputSnapshot(PROGRAM_BYTES)

        val request = DexCompilerRuntimeRequestFactory.create(
            snapshot = snapshot,
            minApi = MIN_API,
            timeoutMillis = TIMEOUT_MILLIS,
        )

        assertEquals(
            DexProtocolVersion(DexCompilerContract.PROTOCOL_MAJOR, DexCompilerContract.PROTOCOL_MINOR),
            request.protocolVersion,
        )
        assertEquals(DexCompilerInputFormat.JAR, request.inputFormat)
        assertEquals(DexCompilerOutputFormat.DEX_ZIP, request.outputFormat)
        assertEquals(DexCompilerMode.RELEASE, request.mode)
        assertEquals(MIN_API, request.minApi)
        assertEquals(snapshot.sizeBytes, request.programSizeBytes)
        assertEquals(snapshot.sha256, request.programSha256.toHexString())
        assertEquals(DexCompilerExperimentalHostLimits.MAX_IN_MEMORY_OUTPUT_BYTES, request.maxOutputBytes)
        assertEquals(DexRuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1, request.runtimeLibraryModel)
        assertTrue(request.expectedRuntimeLibraryFingerprint.toByteArray().all { it == 0.toByte() })
        assertEquals(DexCompilerContract.CANONICALIZATION_POLICY_VERSION, request.canonicalizationPolicyVersion)
        assertEquals(DexCompilerContract.MAX_DIAGNOSTIC_BYTES, request.diagnosticByteLimit)
        assertEquals(TIMEOUT_MILLIS, request.timeoutMillis)
    }

    @Test
    fun everyRequestGetsANewRequestId() {
        val snapshot = TestInputSnapshot(PROGRAM_BYTES)

        val first = DexCompilerRuntimeRequestFactory.create(snapshot, MIN_API)
        val second = DexCompilerRuntimeRequestFactory.create(snapshot, MIN_API)

        assertNotEquals(first.requestId, second.requestId)
    }

    @Test
    fun timeoutAcceptsOneDefaultAndMaximum() {
        val snapshot = TestInputSnapshot(PROGRAM_BYTES)

        assertEquals(
            DexCompilerContract.DEFAULT_TIMEOUT_MILLIS,
            DexCompilerRuntimeRequestFactory.create(snapshot, MIN_API).timeoutMillis,
        )
        listOf(1L, DexCompilerContract.MAX_TIMEOUT_MILLIS).forEach { timeout ->
            assertEquals(timeout, DexCompilerRuntimeRequestFactory.create(snapshot, MIN_API, timeout).timeoutMillis)
        }
    }

    @Test
    fun timeoutRejectsZeroAndOneBeyondMaximum() {
        val snapshot = TestInputSnapshot(PROGRAM_BYTES)

        listOf(0L, DexCompilerContract.MAX_TIMEOUT_MILLIS + 1L).forEach { timeout ->
            assertThrows(IllegalArgumentException::class.java) {
                DexCompilerRuntimeRequestFactory.create(snapshot, MIN_API, timeout)
            }
        }
    }

    @Test
    fun replacingNegotiationPlaceholderPreservesSnapshotBoundFields() {
        val snapshot = TestInputSnapshot(PROGRAM_BYTES)
        val draft = DexCompilerRuntimeRequestFactory.create(snapshot, MIN_API, TIMEOUT_MILLIS)
        val replacementFingerprint = DexSha256.digest("runtime-libraries".toByteArray())

        snapshot.sizeBytes += 1L
        snapshot.sha256 = DexSha256.digest("mutated-source".toByteArray()).toHexString()
        val finalized = draft.copy(
            protocolVersion = DexProtocolVersion(DexCompilerContract.PROTOCOL_MAJOR, 7),
            expectedRuntimeLibraryFingerprint = replacementFingerprint,
        )

        assertEquals(PROGRAM_BYTES.size.toLong(), finalized.programSizeBytes)
        assertEquals(DexSha256.digest(PROGRAM_BYTES), finalized.programSha256)
        assertEquals(draft.requestId, finalized.requestId)
        assertEquals(draft.inputFormat, finalized.inputFormat)
        assertEquals(draft.outputFormat, finalized.outputFormat)
        assertEquals(draft.mode, finalized.mode)
        assertEquals(draft.minApi, finalized.minApi)
        assertEquals(draft.maxOutputBytes, finalized.maxOutputBytes)
        assertEquals(draft.runtimeLibraryModel, finalized.runtimeLibraryModel)
        assertEquals(draft.canonicalizationPolicyVersion, finalized.canonicalizationPolicyVersion)
        assertEquals(draft.diagnosticByteLimit, finalized.diagnosticByteLimit)
        assertEquals(draft.timeoutMillis, finalized.timeoutMillis)
        assertEquals(replacementFingerprint, finalized.expectedRuntimeLibraryFingerprint)
    }

    private companion object {
        val PROGRAM_BYTES = "immutable-program-snapshot".toByteArray()
        const val MIN_API = 28
        const val TIMEOUT_MILLIS = 12_345L
    }
}

class DexCompilerSynchronousAttemptRunnerTest {

    @Test
    fun publishedUseLocalAndCancelledOutcomesAreReturnedByIdentity() {
        val invocation = invocation()
        val outcomes = listOf(
            published(invocation),
            DexCompilerExperimentalOutcome.UseLocalCompiler(
                failure = DexCompilerExperimentalFailure.REMOTE_FAILED,
                pluginWasDispatched = true,
                pluginOutputRollbackRequired = true,
                remoteFailure = DexCompilerExperimentalOutcome.RemoteFailure(
                    code = DexCompilerErrorCode.COMPILATION_FAILED,
                    phase = DexCompilerFailurePhase.COMPILATION,
                    retryable = false,
                ),
            ),
            DexCompilerExperimentalOutcome.Cancelled,
        )

        outcomes.forEach { expected ->
            val capturedInvocation = AtomicReference<DexCompilerExperimentalInvocation?>()
            val closeCount = AtomicInteger()
            val runner = DexCompilerSynchronousAttemptRunner { actual, callback ->
                capturedInvocation.set(actual)
                callback(expected)
                Closeable { closeCount.incrementAndGet() }
            }

            assertSame(expected, runner.run(invocation))
            assertSame(invocation, capturedInvocation.get())
            assertEquals(0, closeCount.get())
        }
    }

    @Test
    fun interruptWhileWaitingClosesHandleWithoutProducingAnOutcome() {
        val invocation = invocation()
        val launcherReturned = CountDownLatch(1)
        val closeCount = AtomicInteger()
        val callback = AtomicReference<((DexCompilerExperimentalOutcome) -> Unit)?>()
        val returned = AtomicReference<DexCompilerExperimentalOutcome?>()
        val failure = AtomicReference<Throwable?>()
        val runner = DexCompilerSynchronousAttemptRunner { _, onOutcome ->
            callback.set(onOutcome)
            launcherReturned.countDown()
            Closeable { closeCount.incrementAndGet() }
        }
        val waitingThread = Thread {
            try {
                returned.set(runner.run(invocation))
            } catch (error: Throwable) {
                failure.set(error)
            }
        }.apply { isDaemon = true }

        waitingThread.start()
        try {
            assertTrue(launcherReturned.await(2, TimeUnit.SECONDS))
            assertNotNull(callback.get())
            assertTrue(waitUntilWaiting(waitingThread))
            waitingThread.interrupt()
            waitingThread.join(2_000L)

            assertFalse("Interrupted runner thread is still alive", waitingThread.isAlive)
            assertTrue(failure.get() is InterruptedException)
            assertNull(returned.get())
            assertEquals(1, closeCount.get())
        } finally {
            waitingThread.interrupt()
            waitingThread.join(2_000L)
        }
    }

    private fun invocation(): DexCompilerExperimentalInvocation {
        val snapshot = TestInputSnapshot("runner-program".toByteArray())
        return DexCompilerExperimentalInvocation(
            request = DexCompilerRuntimeRequestFactory.create(snapshot, 28),
            inputSnapshot = snapshot,
            outputTransaction = UnusedOutputTransaction,
            releaseBuild = false,
        )
    }

    private fun published(
        invocation: DexCompilerExperimentalInvocation,
    ): DexCompilerExperimentalOutcome.Published {
        val output = "verified-output".toByteArray()
        val outputSha256 = DexSha256.digest(output)
        return DexCompilerExperimentalOutcome.Published(
            result = DexCompileResult(
                requestId = invocation.request.requestId,
                compilerFamily = DexCompilerFamily.D8,
                compilerVersion = "test-d8",
                mode = invocation.request.mode,
                minApi = invocation.request.minApi,
                outputFormat = invocation.request.outputFormat,
                runtimeLibraryFingerprint = invocation.request.expectedRuntimeLibraryFingerprint,
                outputSizeBytes = output.size.toLong(),
                outputSha256 = outputSha256,
                dexEntryCount = 1,
                elapsedMillis = 1L,
            ),
            validation = DexIndexedZipValidationResult(
                dexEntryCount = 1,
                totalDexBytes = output.size.toLong(),
                classDefinitionCount = 1,
                dexVersions = setOf("039"),
            ),
            identity = DexCompilerPinnedIdentity(
                component = DexCompilerComponent("example.dex.provider", "CompilerService"),
                packageUid = 10_001,
                versionCode = 1L,
                lastUpdateTime = 1L,
                signerSha256 = setOf("ab".repeat(32)),
            ),
            artifact = TestVerifiedArtifact(output.size.toLong(), outputSha256.toHexString()),
        )
    }
}

private class TestInputSnapshot(bytes: ByteArray) : DexCompilerInputSnapshot {
    override var sizeBytes: Long = bytes.size.toLong()
    override var sha256: String = DexSha256.digest(bytes).toHexString()

    override fun claimReadDescriptor(): DexCompilerOwnedDescriptor = error("Not used by these tests")

    override fun retainForLocalFallback(): DexCompilerRetainedInput = error("Not used by these tests")

    override fun close() = Unit
}

private object UnusedOutputTransaction : DexCompilerOutputTransaction {
    override fun beginDrain(
        maxBytes: Long,
        onTerminal: (DexCompilerOutputDrainTerminal) -> Unit,
    ): Unit = error("Not used by these tests")

    override fun claimWriteDescriptor(): DexCompilerOwnedDescriptor = error("Not used by these tests")

    override fun readBytes(maxBytes: Long): ByteArray = error("Not used by these tests")

    override fun publishVerified(cacheKeySha256: String, bytes: ByteArray): DexCompilerVerifiedArtifact =
        error("Not used by these tests")

    override fun rollback() = Unit

    override fun close() = Unit
}

private class TestVerifiedArtifact(
    override val sizeBytes: Long,
    override val outputSha256: String,
) : DexCompilerVerifiedArtifact {
    override val cacheKeySha256: String = "cd".repeat(32)

    override fun openRead() = null

    override fun invalidate(): Boolean = false
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
