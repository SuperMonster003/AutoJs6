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
import org.autojs.plugin.dexcompiler.api.DexCompilerInputBundleCodec
import org.autojs.plugin.dexcompiler.api.DexCompilerInputBundleSource
import org.autojs.plugin.dexcompiler.api.DexCompilerInputFormat
import org.autojs.plugin.dexcompiler.api.DexCompilerInputIdentity
import org.autojs.plugin.dexcompiler.api.DexCompilerInputLayout
import org.autojs.plugin.dexcompiler.api.DexCompilerInputRole
import org.autojs.plugin.dexcompiler.api.DexCompilerInputSetFingerprint
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
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
    fun capabilityBoundRequestIsFinalizedAfterHandshakeAndDrivesPublication() {
        val fixture = Fixture()
        val unknownFingerprint = DexSha256.fromBytes(ByteArray(DexSha256.BYTE_COUNT))
        val draft = fixture.request.copy(
            protocolVersion = DexCompilerContract.PROTOCOL_V1_0,
            expectedRuntimeLibraryFingerprint = unknownFingerprint,
        )

        fixture.start(
            request = draft,
            requestFinalization = DexCompilerRequestFinalization.CAPABILITY_BOUND,
        )

        val dispatched = fixture.connection.openedRequest.also(::assertNotNull)!!
        assertEquals(DexProtocolVersion(1, 0), dispatched.protocolVersion)
        assertEquals(fixture.capabilities.runtimeLibraryFingerprint, dispatched.expectedRuntimeLibraryFingerprint)
        assertEquals(draft.requestId, dispatched.requestId)
        assertEquals(draft.programSha256, dispatched.programSha256)
        assertEquals(draft.mode, dispatched.mode)
        assertEquals(draft.maxOutputBytes, dispatched.maxOutputBytes)
        assertEquals(draft.timeoutMillis, dispatched.timeoutMillis)
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))
        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.Published
        assertEquals(outcome.cacheKeySha256, fixture.output.publishedCacheKey)
        assertEquals(1, fixture.output.publishCount)
    }

    @Test
    fun exactV11RequestRejectsAV10ProviderBeforeCacheDescriptorOrRemoteDispatch() {
        val fixture = Fixture()
        val classpathBytes = "compile-time-classpath".toByteArray()
        val identities = listOf(
            DexCompilerInputIdentity(
                DexCompilerInputRole.PROGRAM,
                0,
                fixture.inputBytes.size.toLong(),
                DexSha256.digest(fixture.inputBytes),
            ),
            DexCompilerInputIdentity(
                DexCompilerInputRole.CLASSPATH,
                0,
                classpathBytes.size.toLong(),
                DexSha256.digest(classpathBytes),
            ),
        )
        val bundle = ByteArrayOutputStream().also { output ->
            DexCompilerInputBundleCodec.write(
                output,
                listOf(fixture.inputBytes, classpathBytes).mapIndexed { index, bytes ->
                    DexCompilerInputBundleSource(identities[index]) { ByteArrayInputStream(bytes) }
                },
            )
        }.toByteArray()
        val snapshot = FakeInputSnapshot(fixture.inputBytes, bundle, identities)
        val request = fixture.request.copy(
            protocolVersion = DexCompilerContract.PROTOCOL_V1_1,
            inputLayout = DexCompilerInputLayout.PROGRAM_AND_ORDERED_CLASSPATH_BUNDLE_V1,
            inputIdentities = identities,
            inputSetFingerprint = DexCompilerInputSetFingerprint.compute(identities),
            inputBundleSizeBytes = bundle.size.toLong(),
            inputBundleSha256 = DexSha256.digest(bundle),
        )

        fixture.harness.start(
            DexCompilerExperimentalInvocation(
                request = request,
                inputSnapshot = snapshot,
                outputTransaction = fixture.output,
                releaseBuild = true,
                preferredComponent = fixture.candidate.component,
                requestFinalization = DexCompilerRequestFinalization.CAPABILITY_BOUND_EXACT_PROTOCOL,
            ),
            fixture.outcomes::add,
        )

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.HANDSHAKE_REJECTED, outcome.failure)
        assertFalse(outcome.pluginWasDispatched)
        assertEquals(0, fixture.cacheLookupCount)
        assertEquals(0, fixture.connection.openCount)
        assertEquals(0, snapshot.claimCount)
        assertEquals(0, fixture.output.drainStartCount)
        assertEquals(0, fixture.output.claimCount)
        assertEquals(1, snapshot.closeCount)
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
    fun requiredIdentityRejectsOfficialCandidateWithoutHostSignerBeforeBinding() {
        val fixture = Fixture()

        fixture.start(requiredIdentity = fixture.candidate.pin())

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.IDENTITY_DRIFT, outcome.failure)
        assertFalse(outcome.pluginWasDispatched)
        assertEquals(1, fixture.transport.discoveryCount)
        assertEquals(0, fixture.transport.inspectCount)
        assertEquals(0, fixture.transport.bindCount)
        assertEquals(0, fixture.connection.infoCount)
        assertEquals(0, fixture.connection.openCount)
    }

    @Test
    fun requiredIdentityRejectsDriftDuringConnectedReinspectionBeforeOpenSession() {
        val fixture = Fixture(sameSignerAsHost = true)
        val requiredIdentity = fixture.candidate.pin()
        fixture.transport.inspectedCandidate = fixture.candidate.copy(lastUpdateTime = 999)

        fixture.start(requiredIdentity = requiredIdentity)

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.IDENTITY_DRIFT, outcome.failure)
        assertFalse(outcome.pluginWasDispatched)
        assertEquals(1, fixture.transport.discoveryCount)
        assertEquals(1, fixture.transport.bindCount)
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
        assertTrue(outcome.artifact === fixture.output.publishedArtifact)
        assertEquals(64, outcome.cacheKeySha256.length)
        assertEquals(outcome.cacheKeySha256, fixture.output.publishedCacheKey)
        assertEquals(fixture.outputBytes.size.toLong(), outcome.artifact.sizeBytes)
        assertEquals(DexSha256.digest(fixture.outputBytes).toHexString(), outcome.artifact.outputSha256)
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
    fun outputVersionProfileIsBoundToTheCurrentHostRuntimeApi() {
        listOf(
            25 to setOf("035", "037"),
            30 to setOf("035", "037", "038", "039", "040"),
        ).forEach { (runtimeApi, expectedVersions) ->
            val fixture = Fixture(requestMinApi = runtimeApi)
            fixture.start()
            fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))
            fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))

            assertTrue(fixture.singleOutcome() is DexCompilerExperimentalOutcome.Published)
            assertEquals(expectedVersions, fixture.verifierLimits!!.allowedDexVersions)
        }
    }

    @Test
    fun requestMinimumApiMustEqualTheCurrentHostRuntimeApi() {
        val fixture = Fixture(requestMinApi = 25, hostRuntimeApi = 26)

        fixture.start()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.INPUT_REJECTED, outcome.failure)
        assertEquals(0, fixture.transport.discoveryCount)
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

        fixture.callback.onFailed(
            DexCompilerCodec.encodeError(fixture.error(diagnostics = listOf(fixture.diagnostic()))),
        )
        fixture.callback.onFailed(DexCompilerCodec.encodeError(fixture.error()))
        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))
        fixture.transport.bindingCallback!!.onBinderDeath()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.REMOTE_FAILED, outcome.failure)
        assertEquals(DexCompilerErrorCode.COMPILATION_FAILED, outcome.remoteFailure?.code)
        assertEquals(DexCompilerFailurePhase.COMPILATION, outcome.remoteFailure?.phase)
        assertEquals(false, outcome.remoteFailure?.retryable)
        assertEquals("Fixture failure", outcome.remoteFailure?.message)
        assertEquals(listOf(fixture.diagnostic()), outcome.remoteFailure?.diagnostics)
        assertTrue(outcome.remoteFailure.toString().contains("hasMessage=true"))
        assertFalse(outcome.remoteFailure.toString().contains("Fixture failure"))
        assertFalse(outcome.remoteFailure.toString().contains("Fixture diagnostic"))
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
    fun providerCancellationFallsBackAsRemoteCancelled() {
        val fixture = Fixture()
        fixture.start()

        fixture.callback.onCancelled(DexCompilerCodec.encodeCancellation(fixture.cancellation()))

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.REMOTE_CANCELLED, outcome.failure)
        assertTrue(outcome.pluginWasDispatched)
        assertTrue(outcome.pluginOutputRollbackRequired)
        assertEquals(0, fixture.session.cancelCount)
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
    fun deadlineIsAnchoredAtAttemptCreationAndScheduledOnceForTheRemainingTime() {
        val worker = ManualExecutor()
        val fixture = Fixture(workerExecutor = worker)

        fixture.start()
        fixture.clock.advanceBy(2_500L)
        worker.runNext()

        assertEquals(1, fixture.scheduler.scheduleCount)
        assertEquals(7_500L, fixture.scheduler.lastDelayMillis)
        fixture.scheduler.fire()
        worker.runAll()
        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.TIMEOUT, outcome.failure)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun absoluteDeadlineSaturatesWithoutExtendingTheRequestedTimeout() {
        val fixture = Fixture()
        fixture.clock.set(Long.MAX_VALUE - 5L)

        fixture.start()

        assertEquals(1, fixture.scheduler.scheduleCount)
        assertEquals(5L, fixture.scheduler.lastDelayMillis)
        fixture.scheduler.fire()
        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.TIMEOUT, outcome.failure)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun attemptAlreadyExpiredBeforeBeginTimesOutWithoutSchedulingOrBinding() {
        val worker = ManualExecutor()
        val fixture = Fixture(workerExecutor = worker)

        fixture.start()
        fixture.clock.advanceBy(fixture.request.timeoutMillis)
        worker.runAll()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.TIMEOUT, outcome.failure)
        assertEquals(0, fixture.scheduler.scheduleCount)
        assertEquals(0, fixture.transport.discoveryCount)
        assertEquals(0, fixture.transport.bindCount)
        assertEquals(0, fixture.connection.openCount)
        assertEquals(1, fixture.output.rollbackCount)
        assertEquals(1, fixture.output.closeCount)
        assertEquals(1, fixture.snapshot.closeCount)
        assertEquals(0, fixture.binding.closeCount)
    }

    @Test
    fun hostCancellationRegisteredBeforeExpiredBeginKeepsCancellationOwnership() {
        val worker = ManualExecutor()
        val fixture = Fixture(workerExecutor = worker)
        val handle = fixture.start()

        handle.close()
        fixture.clock.advanceBy(fixture.request.timeoutMillis)
        worker.runAll()

        assertTrue(fixture.singleOutcome() === DexCompilerExperimentalOutcome.Cancelled)
        assertEquals(0, fixture.scheduler.scheduleCount)
        assertEquals(0, fixture.transport.discoveryCount)
        assertEquals(0, fixture.transport.bindCount)
        assertEquals(0, fixture.connection.openCount)
        assertEquals(1, fixture.output.rollbackCount)
        assertEquals(1, fixture.output.closeCount)
        assertEquals(1, fixture.snapshot.closeCount)
        assertEquals(0, fixture.binding.closeCount)
    }

    @Test
    fun expiredDeadlineAtDispatchCommitTimesOutWithoutWaitingForScheduler() {
        val blocking = ManualExecutor()
        val fixture = Fixture(blockingExecutor = blocking)
        fixture.start()
        assertTrue(fixture.outcomes.isEmpty())

        fixture.clock.advanceBy(fixture.request.timeoutMillis)
        blocking.runAll()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.TIMEOUT, outcome.failure)
        assertFalse(outcome.pluginWasDispatched)
        assertEquals(1, fixture.scheduler.scheduleCount)
        assertEquals(0, fixture.connection.openCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun expiredDeadlineAtPublicationCommitTimesOutWithoutPublishingOrSchedulerFire() {
        val fixture = Fixture()
        fixture.start()
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))

        fixture.clock.advanceBy(fixture.request.timeoutMillis)
        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.TIMEOUT, outcome.failure)
        assertTrue(outcome.pluginWasDispatched)
        assertEquals(0, fixture.output.publishCount)
        assertEquals(1, fixture.session.cancelCount)
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
    fun binderDeathRegisteredBeforeDeadlineExpiryKeepsPublicationTerminalOwnership() {
        val fixture = Fixture()
        fixture.start()
        fixture.output.onAfterRead = {
            fixture.transport.bindingCallback!!.onBinderDeath()
            fixture.clock.advanceBy(fixture.request.timeoutMillis)
        }
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))

        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.BINDER_DIED, outcome.failure)
        assertEquals(0, fixture.output.publishCount)
        assertEquals(1, fixture.session.cancelCount)
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
    fun mismatchedPublishedArtifactMetadataInvalidatesAndFallsBack() {
        val fixture = Fixture()
        fixture.output.artifactSizeDelta = 1
        fixture.start()
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))

        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.OUTPUT_REJECTED, outcome.failure)
        assertTrue(outcome.pluginWasDispatched)
        assertTrue(outcome.pluginOutputRollbackRequired)
        assertEquals(1, fixture.output.publishCount)
        assertEquals(1, fixture.output.publishedArtifact?.invalidationCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun exceptionReadingPublishedArtifactMetadataInvalidatesAndFallsBack() {
        val fixture = Fixture()
        fixture.output.throwOnArtifactOutputSha256Read = true
        fixture.start()
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))

        fixture.callback.onCompleted(DexCompilerCodec.encodeResult(fixture.result()))

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.OUTPUT_REJECTED, outcome.failure)
        assertTrue(outcome.pluginWasDispatched)
        assertTrue(outcome.pluginOutputRollbackRequired)
        assertEquals(1, fixture.output.publishCount)
        assertEquals(1, fixture.output.publishedArtifact?.invalidationCount)
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
    fun resultDiagnosticMetadataCountsAgainstCurrentRequestLimit() {
        val fixture = Fixture(diagnosticByteLimit = 2)
        fixture.start()
        fixture.callback.onStarted(DexCompilerCodec.encodeStarted(fixture.started()))

        fixture.callback.onCompleted(
            DexCompilerCodec.encodeResult(
                fixture.result(
                    diagnostics = listOf(
                        DexCompilerDiagnostic(
                            severity = DexCompilerDiagnosticSeverity.WARNING,
                            code = "F",
                            message = "m",
                            origin = "program",
                        ),
                    ),
                ),
            ),
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

    @Test
    fun verifiedCacheHitAfterHandshakeSkipsDescriptorsAndRemoteSession() {
        val fixture = Fixture(cachedBytes = byteArrayOf(1, 3, 3, 7))

        fixture.start()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.CacheHit
        assertEquals(fixture.candidate.pin(), outcome.identity)
        assertEquals(fixture.cacheLookupKey, outcome.cacheKeySha256)
        assertEquals(1, fixture.cacheLookupCount)
        assertEquals(1, fixture.verifierCount)
        assertEquals(0, fixture.connection.openCount)
        assertEquals(0, fixture.snapshot.claimCount)
        assertEquals(0, fixture.output.drainStartCount)
        assertEquals(0, fixture.output.claimCount)
        assertEquals(0, fixture.output.publishCount)
        assertEquals(0, fixture.cachedArtifact?.invalidationCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun corruptCacheGenerationIsEvictedBeforeOneRemoteDispatch() {
        val fixture = Fixture(
            cachedBytes = byteArrayOf(1, 3, 3, 7),
            cachedSha256 = DexSha256.digest(byteArrayOf(9, 9, 9)).toHexString(),
        )

        fixture.start()

        assertEquals(1, fixture.cacheLookupCount)
        assertEquals(1, fixture.cachedArtifact?.invalidationCount)
        assertEquals(1, fixture.connection.openCount)
        assertEquals(1, fixture.snapshot.claimCount)
        assertEquals(1, fixture.output.drainStartCount)
        assertEquals(1, fixture.output.claimCount)
        assertTrue(fixture.outcomes.isEmpty())
    }

    @Test
    fun cacheHitRerunsCurrentDexZipValidatorBeforeRemoteDispatch() {
        val fixture = Fixture(
            cachedBytes = byteArrayOf(1, 3, 3, 7),
            verifierThrows = true,
        )

        fixture.start()

        assertEquals(1, fixture.verifierCount)
        assertEquals(1, fixture.cachedArtifact?.invalidationCount)
        assertEquals(1, fixture.connection.openCount)
        assertEquals(1, fixture.snapshot.claimCount)
        assertTrue(fixture.outcomes.isEmpty())
    }

    @Test
    fun timeoutEnteringDuringCacheValidationPreventsRemoteDispatchWithoutEviction() {
        val fixture = Fixture(
            cachedBytes = byteArrayOf(1, 3, 3, 7),
            timeoutDuringVerify = true,
        )

        fixture.start()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.TIMEOUT, outcome.failure)
        assertEquals(0, fixture.cachedArtifact?.invalidationCount)
        assertEquals(0, fixture.connection.openCount)
        assertEquals(0, fixture.snapshot.claimCount)
        assertEquals(0, fixture.output.drainStartCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun timeoutEnteringWhileInvalidCacheIsRejectedStillPreventsRemoteDispatch() {
        val fixture = Fixture(
            cachedBytes = byteArrayOf(1, 3, 3, 7),
            timeoutDuringVerify = true,
            verifierThrows = true,
        )

        fixture.start()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.TIMEOUT, outcome.failure)
        assertEquals(1, fixture.cachedArtifact?.invalidationCount)
        assertEquals(0, fixture.connection.openCount)
        assertEquals(0, fixture.snapshot.claimCount)
        assertEquals(0, fixture.output.drainStartCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
    }

    @Test
    fun cacheLookupFailureIsAMissBeforeOneRemoteDispatch() {
        val fixture = Fixture(cacheLookupThrows = true)

        fixture.start()

        assertEquals(1, fixture.cacheLookupCount)
        assertEquals(1, fixture.connection.openCount)
        assertEquals(1, fixture.snapshot.claimCount)
        assertEquals(1, fixture.output.drainStartCount)
        assertEquals(1, fixture.output.claimCount)
        assertTrue(fixture.outcomes.isEmpty())
    }

    @Test
    fun incompatibleCapabilitiesFailBeforeCacheLookupDescriptorsOrDispatch() {
        val fixture = Fixture(
            requestMinApi = 37,
            hostRuntimeApi = 37,
            cachedBytes = byteArrayOf(1, 3, 3, 7),
        )

        fixture.start()

        val outcome = fixture.singleOutcome() as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(DexCompilerExperimentalFailure.HANDSHAKE_REJECTED, outcome.failure)
        assertEquals(0, fixture.cacheLookupCount)
        assertEquals(0, fixture.connection.openCount)
        assertEquals(0, fixture.snapshot.claimCount)
        assertEquals(0, fixture.output.drainStartCount)
        assertEquals(0, fixture.output.claimCount)
        fixture.assertRolledBackAndUnboundExactlyOnce()
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
        requestMinApi: Int = 36,
        hostRuntimeApi: Int = requestMinApi,
        sameSignerAsHost: Boolean = false,
        private val cachedBytes: ByteArray? = null,
        private val cachedSha256: String? = null,
        private val cacheLookupThrows: Boolean = false,
        private val timeoutDuringVerify: Boolean = false,
    ) {
        val inputBytes = "program-jar".toByteArray()
        val outputBytes = byteArrayOf(1, 3, 3, 7)
        val requestId = DexRequestId.fromUuid(UUID(1, 2))
        private val libraryIdentity = DexRuntimeLibraryIdentity(4, DexSha256.digest(byteArrayOf(1, 2, 3, 4)))
        private val libraryFingerprint = DexRuntimeLibraryFingerprint.compute(listOf(libraryIdentity))
        val candidate = candidate().copy(sameSignerAsHost = sameSignerAsHost)
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
            minApi = requestMinApi,
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
        val clock = ManualMonotonicClock()
        val outcomes = mutableListOf<DexCompilerExperimentalOutcome>()
        var verifierCount = 0
        var verifierLimits: DexIndexedZipLimits? = null
        var cacheLookupCount = 0
        var cacheLookupKey: String? = null
        var cachedArtifact: FakeVerifiedArtifact? = null
        val harness = DexCompilerExperimentalHarness(
            transport = transport,
            workerExecutor = workerExecutor,
            blockingExecutor = blockingExecutor,
            deadlineScheduler = scheduler,
            monotonicClock = clock,
            threadProbe = DexCompilerThreadProbe { mainThread },
            hostVersionCode = 100,
            hostRuntimeApi = hostRuntimeApi,
            urgentControlCapacity = urgentControlCapacity,
            maximumInMemoryOutputBytes = maximumInMemoryOutputBytes,
            outputVerifier = DexCompilerOutputVerifier { bytes, limits ->
                verifierCount++
                verifierLimits = limits
                if (timeoutDuringVerify) scheduler.fire()
                if (verifierThrows) throw IllegalArgumentException("hostile DEX")
                assertEquals(outputBytes.toList(), bytes.toList())
                DexIndexedZipValidationResult(1, bytes.size.toLong(), 1, setOf("035"))
            },
            verifiedCache = DexCompilerVerifiedCache { cacheKeySha256 ->
                cacheLookupCount++
                cacheLookupKey = cacheKeySha256
                if (cacheLookupThrows) throw IllegalStateException("cache unavailable")
                cachedBytes?.let { bytes ->
                    FakeVerifiedArtifact(
                        cacheKeySha256 = cacheKeySha256,
                        bytes = bytes,
                        outputSha256Value = cachedSha256 ?: DexSha256.digest(bytes).toHexString(),
                        sizeBytes = bytes.size.toLong(),
                    ).also { cachedArtifact = it }
                }
            },
        )

        val callback: DexCompilerRemoteCallback
            get() = connection.callback.also(::assertNotNull)!!

        fun start(
            invocationKind: DexCompilerInvocationKind = DexCompilerInvocationKind.ASYNCHRONOUS,
            request: DexCompileRequest = this.request,
            requestFinalization: DexCompilerRequestFinalization = DexCompilerRequestFinalization.NONE,
            requiredIdentity: DexCompilerPinnedIdentity? = null,
        ): Closeable =
            harness.start(
                DexCompilerExperimentalInvocation(
                    request = request,
                    inputSnapshot = snapshot,
                    outputTransaction = output,
                    releaseBuild = true,
                    preferredComponent = candidate.component,
                    invocationKind = invocationKind,
                    requestFinalization = requestFinalization,
                    requiredIdentity = requiredIdentity,
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
        var openedRequest: DexCompileRequest? = null

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
            openedRequest = DexCompilerCodec.decodeRequest(encodedRequest)
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

    private class FakeInputSnapshot(
        bytes: ByteArray,
        wireBytes: ByteArray = bytes,
        override val inputIdentities: List<DexCompilerInputIdentity> = emptyList(),
    ) : DexCompilerInputSnapshot {
        override val sizeBytes = bytes.size.toLong()
        override val sha256 = DexSha256.digest(bytes).toHexString()
        override val wireSizeBytes = wireBytes.size.toLong()
        override val wireSha256 = DexSha256.digest(wireBytes).toHexString()
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

        override fun retainForLocalFallback(): DexCompilerRetainedInput = object : DexCompilerRetainedInput {
            override val sizeBytes: Long = this@FakeInputSnapshot.sizeBytes
            override val sha256: String = this@FakeInputSnapshot.sha256
            override fun resolve(): File? = null
            override fun close() = Unit
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
        var publishedArtifact: FakeVerifiedArtifact? = null
        var artifactSizeDelta = 0L
        var throwOnArtifactOutputSha256Read = false

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

        override fun publishVerified(
            cacheKeySha256: String,
            bytes: ByteArray,
        ): DexCompilerVerifiedArtifact {
            publishCount++
            publishedCacheKey = cacheKeySha256
            publishedBytes = bytes.copyOf()
            onPublish?.invoke()
            return FakeVerifiedArtifact(
                cacheKeySha256 = cacheKeySha256,
                bytes = bytes,
                outputSha256Value = DexSha256.digest(bytes).toHexString(),
                sizeBytes = bytes.size.toLong() + artifactSizeDelta,
                throwOnOutputSha256Read = throwOnArtifactOutputSha256Read,
            ).also { publishedArtifact = it }
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

    private class FakeVerifiedArtifact(
        override val cacheKeySha256: String,
        bytes: ByteArray,
        private val outputSha256Value: String,
        override val sizeBytes: Long,
        private val throwOnOutputSha256Read: Boolean = false,
    ) : DexCompilerVerifiedArtifact {
        private val bytes = bytes.copyOf()
        var invalidationCount = 0
        override val outputSha256: String
            get() {
                if (throwOnOutputSha256Read) throw IllegalStateException("artifact metadata unavailable")
                return outputSha256Value
            }

        override fun openRead() = ByteArrayInputStream(bytes)
        override fun invalidate(): Boolean {
            invalidationCount++
            return invalidationCount == 1
        }
    }

    private class ManualScheduler : DexCompilerDeadlineScheduler {
        private var action: (() -> Unit)? = null
        private var closed = false
        var scheduleCount = 0
            private set
        var lastDelayMillis: Long? = null
            private set

        override fun schedule(delayMillis: Long, action: () -> Unit): Closeable {
            assertTrue(delayMillis > 0)
            scheduleCount++
            lastDelayMillis = delayMillis
            closed = false
            this.action = action
            return Closeable { closed = true }
        }

        fun fire() {
            if (!closed) action?.invoke()
        }
    }

    private class ManualMonotonicClock : DexCompilerMonotonicClock {
        private var nowMillis = 0L

        override fun elapsedRealtimeMillis(): Long = nowMillis

        fun advanceBy(deltaMillis: Long) {
            require(deltaMillis >= 0L)
            nowMillis = Math.addExact(nowMillis, deltaMillis)
        }

        fun set(valueMillis: Long) {
            nowMillis = valueMillis
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
