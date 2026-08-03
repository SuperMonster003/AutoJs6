package org.autojs.autojs.core.plugin.dex

import org.autojs.plugin.dexcompiler.api.DexCompileCancellation
import org.autojs.plugin.dexcompiler.api.DexCompileError
import org.autojs.plugin.dexcompiler.api.DexCompileProgress
import org.autojs.plugin.dexcompiler.api.DexCompileRequest
import org.autojs.plugin.dexcompiler.api.DexCompileResult
import org.autojs.plugin.dexcompiler.api.DexCompileStarted
import org.autojs.plugin.dexcompiler.api.DexCompilerAbiMode
import org.autojs.plugin.dexcompiler.api.DexCompilerCapabilities
import org.autojs.plugin.dexcompiler.api.DexCompilerCancellationReason
import org.autojs.plugin.dexcompiler.api.DexCompilerCodec
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.autojs.plugin.dexcompiler.api.DexCompilerDeterminismClaim
import org.autojs.plugin.dexcompiler.api.DexCompilerDiagnostic
import org.autojs.plugin.dexcompiler.api.DexCompilerDiagnosticSeverity
import org.autojs.plugin.dexcompiler.api.DexCompilerErrorCode
import org.autojs.plugin.dexcompiler.api.DexCompilerFailurePhase
import org.autojs.plugin.dexcompiler.api.DexCompilerFamily
import org.autojs.plugin.dexcompiler.api.DexCompilerInfo
import org.autojs.plugin.dexcompiler.api.DexCompilerInputFormat
import org.autojs.plugin.dexcompiler.api.DexCompilerMode
import org.autojs.plugin.dexcompiler.api.DexCompilerOutputFormat
import org.autojs.plugin.dexcompiler.api.DexCompilerProgressStage
import org.autojs.plugin.dexcompiler.api.DexCompilerResourceLimits
import org.autojs.plugin.dexcompiler.api.DexProtocolVersion
import org.autojs.plugin.dexcompiler.api.DexRequestId
import org.autojs.plugin.dexcompiler.api.DexRuntimeLibraryFingerprint
import org.autojs.plugin.dexcompiler.api.DexRuntimeLibraryIdentity
import org.autojs.plugin.dexcompiler.api.DexRuntimeLibraryModel
import org.autojs.plugin.dexcompiler.api.DexSha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class DexCompilerExperimentalHarnessTest {

    @Test
    fun experimentIsDisabledWithoutAnExplicitComponentSelection() {
        val fixture = Fixture()

        fixture.harness.start(
            DexCompilerExperimentalInvocation(
                request = fixture.request,
                inputSnapshot = fixture.snapshot,
                outputTransaction = fixture.output,
                releaseBuild = true,
            ),
            fixture.outcomes::add,
        )

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.EXPERIMENT_DISABLED, outcome.failure)
        assertFalse(outcome.pluginWasDispatched)
        assertEquals(0, fixture.transport.discoveryCount)
        assertEquals(0, fixture.transport.bindCount)
        assertEquals(1, fixture.output.rollbackCount)
        assertEquals(1, fixture.output.closeCount)
        assertEquals(1, fixture.snapshot.closeCount)
        assertEquals(0, fixture.binding.closeCount)
    }

    @Test
    fun mainThreadSynchronousCallFailsOpenWithoutDiscoveryOrBinding() {
        val fixture = Fixture(mainThread = true)

        fixture.start(invocationKind = DexCompilerInvocationKind.SYNCHRONOUS)

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.MAIN_THREAD_SYNC_FAIL_OPEN, outcome.failure)
        assertFalse(outcome.pluginWasDispatched)
        assertEquals(0, fixture.transport.discoveryCount)
        assertEquals(0, fixture.transport.bindCount)
        assertEquals(1, fixture.output.rollbackCount)
        assertEquals(1, fixture.output.closeCount)
        assertEquals(1, fixture.snapshot.closeCount)
    }

    @Test
    fun aSecondLiveInvocationFailsOpenWithoutAnotherBindOrDispatch() {
        val fixture = Fixture()
        val firstHandle = fixture.start()
        val secondSnapshot = FakeInputSnapshot(fixture.inputBytes)
        val secondOutput = FakeOutputTransaction(fixture.outputBytes)

        fixture.harness.start(
            DexCompilerExperimentalInvocation(
                request = fixture.request,
                inputSnapshot = secondSnapshot,
                outputTransaction = secondOutput,
                releaseBuild = true,
            ),
            fixture.outcomes::add,
        )

        val busy = fixture.outcomes.single() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.BUSY, busy.failure)
        assertFalse(busy.pluginWasDispatched)
        assertEquals(1, fixture.transport.bindCount)
        assertEquals(1, fixture.connection.openCount)
        assertEquals(1, secondOutput.rollbackCount)
        assertEquals(1, secondSnapshot.closeCount)
        firstHandle.close()
        assertTrue(fixture.outcomes.last() === DexCompilerExperimentalOutcome.Cancelled)
    }

    @Test
    fun workerRejectionFailsClosedAndReleasesOwnedTransactions() {
        val fixture = Fixture(workerExecutor = Executor { throw RejectedExecutionException("shutdown") })

        fixture.start()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.WORKER_REJECTED, outcome.failure)
        assertEquals(0, fixture.transport.discoveryCount)
        assertEquals(1, fixture.output.rollbackCount)
        assertEquals(1, fixture.output.closeCount)
        assertEquals(1, fixture.snapshot.closeCount)
    }

    @Test
    fun outputAboveTheExperimentalHeapCapFailsBeforeDiscovery() {
        val fixture = Fixture(maximumInMemoryOutputBytes = 2_048)

        fixture.start()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.INPUT_REJECTED, outcome.failure)
        assertFalse(outcome.pluginWasDispatched)
        assertEquals(0, fixture.transport.discoveryCount)
        assertEquals(0, fixture.transport.bindCount)
        assertEquals(1, fixture.output.rollbackCount)
        assertEquals(1, fixture.output.closeCount)
        assertEquals(1, fixture.snapshot.closeCount)
    }

    @Test
    fun identityDriftAfterBindingFailsBeforeHandshakeOrDispatch() {
        val fixture = Fixture()
        fixture.transport.inspectedCandidate = fixture.candidate.copy(lastUpdateTime = 999)

        fixture.start()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.IDENTITY_DRIFT, outcome.failure)
        assertFalse(outcome.pluginWasDispatched)
        assertEquals(1, fixture.transport.inspectCount)
        assertEquals(0, fixture.connection.infoCount)
        assertEquals(0, fixture.connection.openCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun matchingResultIsHashedStructurallyVerifiedThenPublished() {
        val fixture = Fixture()
        fixture.start()
        assertEquals(1, fixture.connection.openCount)

        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))
        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))
        fixture.transport.bindingCallback!!.onBinderDeath()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.Published
        assertEquals(fixture.candidate.pin(), outcome.identity)
        assertEquals(64, outcome.cacheKeySha256.length)
        assertEquals(outcome.cacheKeySha256, fixture.output.publishedCacheKey)
        assertEquals(1, fixture.verifierCount)
        assertEquals(1, fixture.output.readCount)
        assertEquals(1, fixture.output.publishCount)
        assertEquals(0, fixture.output.rollbackCount)
        assertEquals(1, fixture.output.closeCount)
        assertEquals(1, fixture.snapshot.closeCount)
        assertEquals(1, fixture.session.closeCount)
        assertEquals(1, fixture.binding.closeCount)
        assertEquals(1, fixture.snapshot.descriptorCloseCount)
        assertEquals(1, fixture.output.descriptorCloseCount)
        assertEquals(1, fixture.outcomes.size)
    }

    @Test
    fun providerCompletionWaitsForTheIndependentOutputDrain() {
        val fixture = Fixture()
        fixture.output.autoCompleteDrain = false
        fixture.start()
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))

        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))

        assertTrue(fixture.outcomes.isEmpty())
        assertEquals(0, fixture.output.readCount)
        assertEquals(0, fixture.output.publishCount)

        fixture.output.completeDrain()

        assertTrue(fixture.singleOutcome() is DexCompilerExperimentalOutcome.Published)
        assertEquals(1, fixture.output.readCount)
        assertEquals(1, fixture.output.publishCount)
    }

    @Test
    fun timeoutWhileWaitingForDrainRollsBackAndIgnoresLateEof() {
        val fixture = Fixture()
        fixture.output.autoCompleteDrain = false
        fixture.start()
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))
        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))

        fixture.scheduler.fire()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.TIMEOUT, outcome.failure)
        assertEquals(1, fixture.session.cancelCount)
        assertEquals(0, fixture.output.publishCount)

        fixture.output.completeDrain()

        assertEquals(1, fixture.outcomes.size)
        assertEquals(0, fixture.output.publishCount)
    }

    @Test
    fun drainFailureRollsBackAndCancelsTheRemoteSession() {
        val fixture = Fixture()
        fixture.output.autoCompleteDrain = false
        fixture.start()

        fixture.output.failDrain()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.OUTPUT_REJECTED, outcome.failure)
        assertTrue(outcome.pluginWasDispatched)
        assertEquals(1, fixture.session.cancelCount)
        assertEquals(0, fixture.output.publishCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun mismatchedDrainSummaryRejectsBeforeReadingOrVerifyingStaging() {
        val fixture = Fixture()
        fixture.output.autoCompleteDrain = false
        fixture.start()
        fixture.output.completeDrain(byteArrayOf(9, 9, 9))
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))

        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.OUTPUT_REJECTED, outcome.failure)
        assertEquals(0, fixture.output.readCount)
        assertEquals(0, fixture.verifierCount)
        assertEquals(0, fixture.output.publishCount)
    }

    @Test
    fun remoteFailureRollsBackOnceAndLateCallbacksCannotChangeOutcome() {
        val fixture = Fixture()
        fixture.start()

        fixture.callback.onFailed(DexCompilerCodec.encodeError(fixture.error()))
        fixture.callback.onFailed(DexCompilerCodec.encodeError(fixture.error()))
        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))
        fixture.transport.bindingCallback!!.onBinderDeath()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.REMOTE_FAILED, outcome.failure)
        assertTrue(outcome.pluginWasDispatched)
        assertTrue(outcome.pluginOutputRollbackRequired)
        assertEquals(1, fixture.outcomes.size)
        fixture.assertRolledBackAndUnboundExactlyOnce()
        assertEquals(1, fixture.session.closeCount)
    }

    @Test
    fun cacheKeyIsDerivedFromPostHandshakeProviderIdentity() {
        val first = Fixture(providerId = "fixture-d8-a")
        val second = Fixture(providerId = "fixture-d8-b")

        listOf(first, second).forEach { fixture ->
            fixture.start()
            assertEquals(null, fixture.output.publishedCacheKey)
            fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))
            fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))
        }

        val firstKey = (first.singleOutcome() as DexCompilerExperimentalOutcome.Published).cacheKeySha256
        val secondKey = (second.singleOutcome() as DexCompilerExperimentalOutcome.Published).cacheKeySha256
        assertNotEquals(firstKey, secondKey)
    }

    @Test
    fun duplicateConnectionNeverReclaimsDescriptorsOrRedispatches() {
        val fixture = Fixture()
        fixture.start()

        fixture.transport.bindingCallback!!.onConnected(fixture.connection)

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.CALLBACK_VIOLATION, outcome.failure)
        assertEquals(1, fixture.connection.openCount)
        assertEquals(1, fixture.snapshot.claimCount)
        assertEquals(1, fixture.output.claimCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun bindingFailureAfterConnectionRollsBackWithoutASecondBind() {
        val fixture = Fixture()
        fixture.start()

        fixture.transport.bindingCallback!!.onBindingFailure(IllegalStateException("disconnected"))

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.BIND_FAILED, outcome.failure)
        assertTrue(outcome.pluginWasDispatched)
        assertEquals(1, fixture.transport.bindCount)
        assertEquals(1, fixture.connection.openCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun ambiguousDispatchFailureNeverRetriesBinderAndFallsBackOnce() {
        val fixture = Fixture()
        fixture.connection.throwOnOpen = true

        fixture.start()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.DISPATCH_FAILED, outcome.failure)
        assertTrue(outcome.pluginWasDispatched)
        assertEquals(1, fixture.transport.bindCount)
        assertEquals(1, fixture.connection.openCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun cancellationAfterDispatchNeverFallsBackAndSuppressesLateCompletion() {
        val fixture = Fixture()
        val handle = fixture.start()

        handle.close()
        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))
        fixture.transport.bindingCallback!!.onBinderDeath()

        assertTrue(fixture.singleOutcome() === DexCompilerExperimentalOutcome.Cancelled)
        assertEquals(1, fixture.outcomes.size)
        assertEquals(1, fixture.session.cancelCount)
        assertEquals(1, fixture.session.closeCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun timeoutRollsBackAndLatePluginCancellationOrCompletionIsIgnored() {
        val fixture = Fixture()
        fixture.start()

        fixture.scheduler.fire()
        fixture.callback.onCancelled(DexCompilerCodec.encodeCancellation(fixture.cancellation()))
        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.TIMEOUT, outcome.failure)
        assertTrue(outcome.pluginWasDispatched)
        assertEquals(1, fixture.session.cancelCount)
        assertEquals(1, fixture.outcomes.size)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun duplicateOrNonIncreasingProgressIsAProtocolViolation() {
        val fixture = Fixture()
        fixture.start()
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started(sequence = 4)))
        fixture.callback.onProgress(DexCompilerCodec.encodeProgress(fixture.progress(sequence = 5)))
        fixture.callback.onProgress(DexCompilerCodec.encodeProgress(fixture.progress(sequence = 5)))

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.CALLBACK_VIOLATION, outcome.failure)
        assertEquals(1, fixture.session.cancelCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun mismatchedResultMetadataNeverPublishes() {
        val fixture = Fixture()
        fixture.start()
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))
        fixture.callback.onCompleted(
            DexCompilerCodec.encodeResult(
                fixture.result(outputSize = fixture.outputBytes.size + 1L),
            ),
        )

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.OUTPUT_REJECTED, outcome.failure)
        assertEquals(0, fixture.output.publishCount)
        assertEquals(0, fixture.output.readCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun cancellationQueuedReentrantlyDuringPublishCannotCreatePublishedFallbackSplitBrain() {
        val fixture = Fixture()
        val handle = fixture.start()
        fixture.output.onPublish = handle::close
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))

        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))

        assertTrue(fixture.singleOutcome() is DexCompilerExperimentalOutcome.Published)
        assertEquals(1, fixture.output.publishCount)
        assertEquals(0, fixture.output.rollbackCount)
        assertEquals(1, fixture.outcomes.size)
    }

    @Test
    fun cancellationObservedDuringValidationRollsBackBeforePublicationCommit() {
        val fixture = Fixture()
        val handle = fixture.start()
        fixture.output.onAfterRead = handle::close
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))

        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))

        assertTrue(fixture.singleOutcome() === DexCompilerExperimentalOutcome.Cancelled)
        assertEquals(0, fixture.output.publishCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun binderDeathObservedDuringValidationRollsBackBeforePublicationCommit() {
        val fixture = Fixture()
        fixture.start()
        fixture.output.onAfterRead = fixture.transport.bindingCallback!!::onBinderDeath
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))

        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.BINDER_DIED, outcome.failure)
        assertEquals(0, fixture.output.publishCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun queueFullRejectionDuringPublishCannotCreatePublishedFallbackSplitBrain() {
        val fixture = Fixture(urgentControlCapacity = 1)
        val handle = fixture.start()
        fixture.output.onPublish = {
            handle.close()
            fixture.transport.bindingCallback!!.onBinderDeath()
        }
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))

        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))

        assertTrue(fixture.singleOutcome() is DexCompilerExperimentalOutcome.Published)
        assertEquals(1, fixture.output.publishCount)
        assertEquals(0, fixture.output.rollbackCount)
        assertEquals(1, fixture.outcomes.size)
    }

    @Test
    fun nextAttemptCannotStartUntilPreviousCleanupCompletes() {
        val fixture = Fixture()
        val handle = fixture.start()
        val cleanupEntered = CountDownLatch(1)
        val allowCleanup = CountDownLatch(1)
        fixture.output.onClose = {
            cleanupEntered.countDown()
            assertTrue(allowCleanup.await(5, TimeUnit.SECONDS))
        }
        val closer = Executors.newSingleThreadExecutor()
        val closing = closer.submit { handle.close() }
        try {
            assertTrue(cleanupEntered.await(5, TimeUnit.SECONDS))
            val secondSnapshot = FakeInputSnapshot(fixture.inputBytes)
            val secondOutput = FakeOutputTransaction(fixture.outputBytes)

            fixture.harness.start(
                DexCompilerExperimentalInvocation(
                    request = fixture.request,
                    inputSnapshot = secondSnapshot,
                    outputTransaction = secondOutput,
                    releaseBuild = true,
                ),
                fixture.outcomes::add,
            )

            val busy = fixture.outcomes.single() as DexCompilerExperimentalOutcome.UseLocalCompiler
            assertEquals(DexCompilerExperimentalFailure.BUSY, busy.failure)
            assertEquals(1, secondOutput.closeCount)
            allowCleanup.countDown()
            closing.get(5, TimeUnit.SECONDS)
            assertTrue(fixture.outcomes.last() === DexCompilerExperimentalOutcome.Cancelled)
        } finally {
            allowCleanup.countDown()
            closer.shutdownNow()
        }
    }

    @Test
    fun remoteMutationAfterValidationReadCannotChangeHostOnlyPublishedCopy() {
        val fixture = Fixture()
        val originallyValidated = fixture.outputBytes.copyOf()
        fixture.output.onAfterRead = {
            fixture.output.replaceRemoteWritableBytes(byteArrayOf(9, 9, 9, 9))
        }
        fixture.start()
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))

        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))

        assertTrue(fixture.singleOutcome() is DexCompilerExperimentalOutcome.Published)
        assertEquals(originallyValidated.toList(), fixture.output.publishedBytes?.toList())
        assertEquals(List(4) { 9.toByte() }, fixture.output.remoteWritableBytes().toList())
    }

    @Test
    fun verifierFailureRollsBackInsteadOfPublishing() {
        val fixture = Fixture(verifierThrows = true)
        fixture.start()
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))
        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.OUTPUT_REJECTED, outcome.failure)
        assertEquals(0, fixture.output.publishCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun timeoutDuringQueuedHandshakeRollsBackWithoutWaitingForBinderWork() {
        val blocking = ManualExecutor()
        val fixture = Fixture(blockingExecutor = blocking)
        fixture.start()
        assertEquals(1, blocking.size)
        assertEquals(0, fixture.connection.infoCount)

        fixture.scheduler.fire()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.TIMEOUT, outcome.failure)
        fixture.assertRolledBackAndUnboundExactlyOnce()
        blocking.runAll()
        assertEquals(1, fixture.outcomes.size)
        assertEquals(0, fixture.connection.openCount)
    }

    @Test
    fun timeoutDuringQueuedOpenClosesClaimedDescriptorsAndLateSession() {
        val blocking = ManualExecutor()
        val fixture = Fixture(blockingExecutor = blocking)
        fixture.start()
        blocking.runNext()
        assertEquals(1, blocking.size)
        assertEquals(1, fixture.snapshot.claimCount)
        assertEquals(0, fixture.snapshot.descriptorCloseCount)

        fixture.scheduler.fire()

        assertEquals(1, fixture.snapshot.descriptorCloseCount)
        assertEquals(1, fixture.output.descriptorCloseCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
        blocking.runAll()
        assertEquals(1, fixture.session.closeCount)
        assertEquals(1, fixture.outcomes.size)
    }

    @Test
    fun blockingExecutorRejectionFailsOpenAndReleasesEverything() {
        val fixture = Fixture(blockingExecutor = Executor { throw RejectedExecutionException("fused") })

        fixture.start()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.BLOCKING_EXECUTOR_REJECTED, outcome.failure)
        assertEquals(0, fixture.connection.infoCount)
        assertEquals(0, fixture.connection.openCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun progressFloodCannotDelayHostCancellation() {
        val worker = ManualExecutor()
        val fixture = Fixture(workerExecutor = worker)
        val handle = fixture.start()
        worker.runAll()
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))
        repeat(1_000) { index ->
            fixture.callback.onProgress(DexCompilerCodec.encodeProgress(fixture.progress(index + 1L)))
        }
        handle.close()

        worker.runAll()

        assertTrue(fixture.singleOutcome() === DexCompilerExperimentalOutcome.Cancelled)
        assertEquals(1, fixture.outcomes.size)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun progressFloodCannotDelayTimeout() {
        val worker = ManualExecutor()
        val fixture = Fixture(workerExecutor = worker)
        fixture.start()
        worker.runAll()
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))
        repeat(1_000) { index ->
            fixture.callback.onProgress(DexCompilerCodec.encodeProgress(fixture.progress(index + 1L)))
        }
        fixture.scheduler.fire()

        worker.runAll()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.TIMEOUT, outcome.failure)
        assertEquals(1, fixture.outcomes.size)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun terminalCallbackPrecedesQueuedProgressData() {
        val worker = ManualExecutor()
        val fixture = Fixture(workerExecutor = worker)
        fixture.start()
        worker.runAll()
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))
        worker.runAll()
        repeat(1_000) { index ->
            fixture.callback.onProgress(DexCompilerCodec.encodeProgress(fixture.progress(index + 1L)))
        }
        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))

        worker.runAll()

        assertTrue(fixture.singleOutcome() is DexCompilerExperimentalOutcome.Published)
        assertEquals(1, fixture.output.publishCount)
    }

    @Test
    fun oversizedCallbackPayloadIsRejectedAtIngress() {
        val fixture = Fixture()
        fixture.start()

        fixture.callback.onStarted(ByteArray(DexCompilerContract.MAX_METADATA_BYTES + 1))

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.CALLBACK_VIOLATION, outcome.failure)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun explicitTransportViolationRollsBackWithoutSyntheticPayload() {
        val fixture = Fixture()
        fixture.start()

        fixture.callback.onTransportViolation(SecurityException("wrong callback UID"))

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.TRANSPORT_VIOLATION, outcome.failure)
        assertEquals(1, fixture.session.cancelCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun resultDiagnosticsMustFitCurrentRequestLimit() {
        val fixture = Fixture(diagnosticByteLimit = 0)
        fixture.start()
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))

        fixture.callback.onCompleted(
            DexCompilerCodec.encodeResult(fixture.result(diagnostics = listOf(fixture.diagnostic()))),
        )

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.CALLBACK_VIOLATION, outcome.failure)
        assertEquals(0, fixture.output.publishCount)
    }

    @Test
    fun errorDiagnosticsMustFitCurrentRequestLimit() {
        val fixture = Fixture(diagnosticByteLimit = 1)
        fixture.start()
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))

        fixture.callback.onFailed(
            DexCompilerCodec.encodeError(fixture.error(diagnostics = listOf(fixture.diagnostic()))),
        )

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.CALLBACK_VIOLATION, outcome.failure)
        assertEquals(1, fixture.session.cancelCount)
    }

    private class Fixture(
        mainThread: Boolean = false,
        verifierThrows: Boolean = false,
        workerExecutor: Executor = Executor { it.run() },
        blockingExecutor: Executor = Executor { it.run() },
        diagnosticByteLimit: Int = 1024,
        providerId: String = "fixture-d8",
        urgentControlCapacity: Int = 16,
        maximumInMemoryOutputBytes: Long = DexCompilerExperimentalHostLimits.MAX_IN_MEMORY_OUTPUT_BYTES,
    ) {
        val inputBytes = "program-jar".toByteArray()
        val outputBytes = byteArrayOf(1, 3, 3, 7)
        val requestId = DexRequestId.fromUuid(UUID(1, 2))
        private val libraryIdentity = DexRuntimeLibraryIdentity(4, DexSha256.digest(byteArrayOf(1, 2, 3, 4)))
        private val libraryFingerprint = DexRuntimeLibraryFingerprint.compute(listOf(libraryIdentity))
        val candidate = candidate()
        val info = DexCompilerInfo(
            protocolMin = DexProtocolVersion(1, 0),
            protocolMax = DexProtocolVersion(1, 0),
            providerId = providerId,
            providerVersionName = "1.0.0",
            providerVersionCode = candidate.versionCode,
            minHostVersionCode = 1,
            abiMode = DexCompilerAbiMode.ANY,
        )
        val capabilities = DexCompilerCapabilities(
            compilerFamily = DexCompilerFamily.D8,
            compilerVersion = "8.13.19",
            inputFormats = listOf(DexCompilerInputFormat.JAR),
            outputFormats = listOf(DexCompilerOutputFormat.DEX_ZIP),
            modes = listOf(DexCompilerMode.DEBUG, DexCompilerMode.RELEASE),
            minApi = 24,
            maxApi = 36,
            supportsMultiDex = true,
            determinismClaim = DexCompilerDeterminismClaim.NOT_CLAIMED,
            runtimeLibraryModel = DexRuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1,
            acceptsExternalClasspath = false,
            supportsDesugaredLibraryConfiguration = false,
            limits = DexCompilerResourceLimits(
                maxCompressedProgramBytes = 1024,
                maxArchiveEntries = 100,
                maxUncompressedProgramBytes = 2048,
                maxTotalClassBytes = 1024,
                maxSingleClassBytes = 512,
                maxOutputBytes = 4096,
                maxDexEntries = 4,
                maxDiagnosticBytes = 1024,
                maxConcurrentSessions = 1,
            ),
            runtimeLibraryIdentities = listOf(libraryIdentity),
            runtimeLibraryFingerprint = libraryFingerprint,
        )
        val request = DexCompileRequest(
            requestId = requestId,
            protocolVersion = DexProtocolVersion(1, 0),
            inputFormat = DexCompilerInputFormat.JAR,
            outputFormat = DexCompilerOutputFormat.DEX_ZIP,
            mode = DexCompilerMode.DEBUG,
            minApi = 36,
            programSizeBytes = inputBytes.size.toLong(),
            programSha256 = DexSha256.digest(inputBytes),
            maxOutputBytes = 4096,
            runtimeLibraryModel = DexRuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1,
            expectedRuntimeLibraryFingerprint = libraryFingerprint,
            canonicalizationPolicyVersion = DexCompilerContract.CANONICALIZATION_POLICY_VERSION,
            diagnosticByteLimit = diagnosticByteLimit,
            timeoutMillis = 10_000,
        )
        val snapshot = FakeInputSnapshot(inputBytes)
        val output = FakeOutputTransaction(outputBytes)
        val session = FakeRemoteSession()
        val connection = FakeConnection(info, capabilities, session)
        val binding = FakeBinding()
        val transport = FakeTransport(candidate, connection, binding)
        val scheduler = ManualScheduler()
        val outcomes = mutableListOf<DexCompilerExperimentalOutcome>()
        var verifierCount = 0
        val harness = DexCompilerExperimentalHarness(
            transport = transport,
            workerExecutor = workerExecutor,
            blockingExecutor = blockingExecutor,
            deadlineScheduler = scheduler,
            threadProbe = DexCompilerThreadProbe { mainThread },
            hostVersionCode = 100,
            urgentControlCapacity = urgentControlCapacity,
            maximumInMemoryOutputBytes = maximumInMemoryOutputBytes,
            outputVerifier = DexCompilerOutputVerifier { bytes, _ ->
                verifierCount++
                if (verifierThrows) throw IllegalArgumentException("hostile DEX")
                assertEquals(outputBytes.toList(), bytes.toList())
                DexIndexedZipValidationResult(1, bytes.size.toLong(), 1, setOf("035"))
            },
        )

        val callback: DexCompilerRemoteCallback
            get() = connection.callback.also(::assertNotNull)!!

        fun start(invocationKind: DexCompilerInvocationKind = DexCompilerInvocationKind.ASYNCHRONOUS): Closeable =
            harness.start(
                DexCompilerExperimentalInvocation(
                    request = request,
                    inputSnapshot = snapshot,
                    outputTransaction = output,
                    releaseBuild = true,
                    preferredComponent = candidate.component,
                    invocationKind = invocationKind,
                ),
                outcomes::add,
            )

        fun started(sequence: Long = 0) = DexCompileStarted(
            requestId = requestId,
            sequence = sequence,
            protocolVersion = request.protocolVersion,
            compilerFamily = capabilities.compilerFamily,
            compilerVersion = capabilities.compilerVersion,
            runtimeLibraryModel = capabilities.runtimeLibraryModel,
            runtimeLibraryFingerprint = capabilities.runtimeLibraryFingerprint,
            queueElapsedMillis = 1,
        )

        fun progress(sequence: Long) = DexCompileProgress(
            requestId = requestId,
            sequence = sequence,
            stage = DexCompilerProgressStage.COMPILING,
            current = 1,
            total = 2,
        )

        fun result(
            outputSize: Long = outputBytes.size.toLong(),
            diagnostics: Collection<DexCompilerDiagnostic> = emptyList(),
        ) = DexCompileResult(
            requestId = requestId,
            compilerFamily = capabilities.compilerFamily,
            compilerVersion = capabilities.compilerVersion,
            mode = request.mode,
            minApi = request.minApi,
            outputFormat = request.outputFormat,
            runtimeLibraryFingerprint = request.expectedRuntimeLibraryFingerprint,
            outputSizeBytes = outputSize,
            outputSha256 = DexSha256.digest(outputBytes),
            dexEntryCount = 1,
            elapsedMillis = 10,
            diagnostics = diagnostics,
        )

        fun error(diagnostics: Collection<DexCompilerDiagnostic> = emptyList()) = DexCompileError(
            requestId = requestId,
            code = DexCompilerErrorCode.COMPILATION_FAILED,
            phase = DexCompilerFailurePhase.COMPILATION,
            message = "Fixture failure",
            retryable = false,
            diagnostics = diagnostics,
        )

        fun diagnostic() = DexCompilerDiagnostic(
            severity = DexCompilerDiagnosticSeverity.WARNING,
            code = "FIXTURE.WARNING",
            message = "Fixture diagnostic",
        )

        fun cancellation() = DexCompileCancellation(
            requestId = requestId,
            reason = DexCompilerCancellationReason.REQUESTED,
            phase = DexCompilerFailurePhase.COMPILATION,
            elapsedMillis = 10,
        )

        fun singleOutcome(): DexCompilerExperimentalOutcome = outcomes.single()

        fun assertRolledBackAndUnboundExactlyOnce() {
            assertEquals(1, output.rollbackCount)
            assertEquals(1, output.closeCount)
            assertEquals(1, snapshot.closeCount)
            assertEquals(1, binding.closeCount)
        }
    }

    private class FakeTransport(
        private val candidate: DexCompilerServiceCandidate,
        private val connection: FakeConnection,
        private val binding: FakeBinding,
    ) : DexCompilerExperimentalTransport {
        var discoveryCount = 0
        var inspectCount = 0
        var bindCount = 0
        var inspectedCandidate: DexCompilerServiceCandidate? = candidate
        var bindingCallback: DexCompilerBindingCallback? = null

        override fun discoverExactAction(action: String): List<DexCompilerServiceCandidate> {
            discoveryCount++
            assertEquals(DexCompilerContract.SERVICE_ACTION, action)
            return listOf(candidate)
        }

        override fun inspectExact(component: DexCompilerComponent): DexCompilerServiceCandidate? {
            inspectCount++
            assertEquals(candidate.component, component)
            return inspectedCandidate
        }

        override fun bindExact(
            identity: DexCompilerPinnedIdentity,
            callback: DexCompilerBindingCallback,
        ): DexCompilerBinding {
            bindCount++
            assertEquals(candidate.pin(), identity)
            bindingCallback = callback
            callback.onConnected(connection)
            return binding
        }
    }

    private class FakeConnection(
        info: DexCompilerInfo,
        capabilities: DexCompilerCapabilities,
        private val session: FakeRemoteSession,
    ) : DexCompilerRemoteConnection {
        private val encodedInfo = DexCompilerCodec.encodeInfo(info)
        private val encodedCapabilities = DexCompilerCodec.encodeCapabilities(capabilities)
        var infoCount = 0
        var openCount = 0
        var throwOnOpen = false
        var callback: DexCompilerRemoteCallback? = null

        override fun getCompilerInfo(): ByteArray {
            infoCount++
            return encodedInfo.copyOf()
        }

        override fun getCapabilities(): ByteArray = encodedCapabilities.copyOf()

        override fun openSession(
            encodedRequest: ByteArray,
            programDescriptor: DexCompilerOwnedDescriptor,
            outputDescriptor: DexCompilerOwnedDescriptor,
            callback: DexCompilerRemoteCallback,
        ): DexCompilerRemoteSession {
            openCount++
            DexCompilerCodec.decodeRequest(encodedRequest)
            this.callback = callback
            if (throwOnOpen) throw IllegalStateException("ambiguous Binder failure")
            return session
        }
    }

    private class FakeRemoteSession : DexCompilerRemoteSession {
        var cancelCount = 0
        var closeCount = 0

        override fun cancel() {
            cancelCount++
        }

        override fun close() {
            closeCount++
        }
    }

    private class FakeBinding : DexCompilerBinding {
        var closeCount = 0
        override fun close() {
            closeCount++
        }
    }

    private class FakeInputSnapshot(bytes: ByteArray) : DexCompilerInputSnapshot {
        override val sizeBytes = bytes.size.toLong()
        override val sha256 = DexSha256.digest(bytes).toHexString()
        var claimCount = 0
        var descriptorCloseCount = 0
        var closeCount = 0

        override fun claimReadDescriptor(): DexCompilerOwnedDescriptor {
            claimCount++
            return object : DexCompilerOwnedDescriptor {
                override fun close() {
                    descriptorCloseCount++
                }
            }
        }

        override fun close() {
            closeCount++
        }
    }

    private class FakeOutputTransaction(bytes: ByteArray) : DexCompilerOutputTransaction {
        private var stagingBytes = bytes.copyOf()
        private var drainedBytes: ByteArray? = null
        private var drainCallback: ((DexCompilerOutputDrainTerminal) -> Unit)? = null
        private var drainFinished = false
        var autoCompleteDrain = true
        var drainStartCount = 0
        var claimCount = 0
        var descriptorCloseCount = 0
        var readCount = 0
        var publishCount = 0
        var rollbackCount = 0
        var closeCount = 0
        var onPublish: (() -> Unit)? = null
        var onAfterRead: (() -> Unit)? = null
        var onClose: (() -> Unit)? = null
        var publishedBytes: ByteArray? = null
        var publishedCacheKey: String? = null

        override fun beginDrain(
            maxBytes: Long,
            onTerminal: (DexCompilerOutputDrainTerminal) -> Unit,
        ) {
            check(drainCallback == null)
            require(maxBytes > 0L)
            drainStartCount++
            drainCallback = onTerminal
            if (autoCompleteDrain) completeDrain()
        }

        override fun claimWriteDescriptor(): DexCompilerOwnedDescriptor {
            claimCount++
            return object : DexCompilerOwnedDescriptor {
                override fun close() {
                    descriptorCloseCount++
                }
            }
        }

        override fun readBytes(maxBytes: Long): ByteArray {
            readCount++
            val bytes = checkNotNull(drainedBytes)
            require(bytes.size <= maxBytes)
            return bytes.copyOf().also { onAfterRead?.invoke() }
        }

        override fun publishVerified(cacheKeySha256: String, bytes: ByteArray) {
            publishCount++
            publishedCacheKey = cacheKeySha256
            publishedBytes = bytes.copyOf()
            onPublish?.invoke()
        }

        fun replaceRemoteWritableBytes(bytes: ByteArray) {
            stagingBytes = bytes.copyOf()
        }

        fun completeDrain(bytes: ByteArray = stagingBytes) {
            check(!drainFinished)
            drainFinished = true
            drainedBytes = bytes.copyOf()
            checkNotNull(drainCallback).invoke(
                DexCompilerOutputDrainTerminal.Success(
                    DexCompilerStreamSummary(
                        sizeBytes = bytes.size.toLong(),
                        sha256 = DexSha256.digest(bytes).toHexString(),
                    ),
                ),
            )
        }

        fun failDrain() {
            check(!drainFinished)
            drainFinished = true
            checkNotNull(drainCallback).invoke(DexCompilerOutputDrainTerminal.Failure(null))
        }

        fun remoteWritableBytes(): ByteArray = stagingBytes.copyOf()

        override fun rollback() {
            rollbackCount++
        }

        override fun close() {
            closeCount++
            onClose?.invoke()
        }
    }

    private class ManualScheduler : DexCompilerDeadlineScheduler {
        private var action: (() -> Unit)? = null
        private var closed = false

        override fun schedule(delayMillis: Long, action: () -> Unit): Closeable {
            assertTrue(delayMillis > 0)
            this.action = action
            return Closeable { closed = true }
        }

        fun fire() {
            if (!closed) action?.invoke()
        }
    }

    private class ManualExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        val size: Int
            get() = tasks.size

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun runNext() {
            tasks.removeFirst().run()
        }

        fun runAll() {
            while (tasks.isNotEmpty()) runNext()
        }
    }

    private companion object {
        val SIGNER = "aa".repeat(32)

        fun candidate() = DexCompilerServiceCandidate(
            component = DexCompilerComponent("example.dex.compiler", "CompilerService"),
            declaredAction = DexCompilerContract.SERVICE_ACTION,
            exported = true,
            applicationEnabled = true,
            serviceEnabled = true,
            requiredPermission = DexCompilerContract.PLUGIN_PERMISSION,
            packageUid = 10_000,
            versionCode = 7,
            lastUpdateTime = 123,
            signerSha256 = setOf(SIGNER),
            trustLevel = DexCompilerTrustLevel.OFFICIAL,
            sameSignerAsHost = false,
        )
    }
}
