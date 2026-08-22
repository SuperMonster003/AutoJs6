package org.autojs.plugin.python.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PythonRuntimeSessionPolicyTest {
    @Test
    fun startDefinesNoReplayBoundaryAndIsSingleShot() {
        val policy = policy()
        assertEquals(
            PythonExecutionSessionPolicy.ReplayDisposition.SAFE_BEFORE_DISPATCH,
            policy.replayDisposition,
        )
        assertTrue(policy.requestStart())
        assertFalse(policy.requestStart())
        assertEquals(
            PythonExecutionSessionPolicy.ReplayDisposition.FORBIDDEN_AFTER_DISPATCH,
            policy.replayDisposition,
        )
        policy.onStarted(started())
        assertFalse(policy.requestStart())
    }

    @Test
    fun cancellationBeforeDispatchAlsoForbidsReplay() {
        val policy = policy()
        assertTrue(policy.requestCancel())
        assertEquals(
            PythonExecutionSessionPolicy.ReplayDisposition.FORBIDDEN_AFTER_DISPATCH,
            policy.replayDisposition,
        )
        assertFalse(policy.requestStart())
    }

    @Test
    fun stdoutAndStderrShareOneSequenceAndCreditWindow() {
        val policy = startedPolicy()
        policy.grantOutputCredits(2)
        policy.onOutput(chunk(7, PythonOutputStream.STDOUT, "out"), PythonOutputStream.STDOUT)
        policy.onOutput(chunk(8, PythonOutputStream.STDERR, "err"), PythonOutputStream.STDERR)

        assertEquals(0, policy.outstandingCredits)
        assertEquals(3, policy.stdoutBytes)
        assertEquals(3, policy.stderrBytes)
        assertThrows(IllegalStateException::class.java) {
            policy.onOutput(chunk(9, PythonOutputStream.STDOUT, "late"), PythonOutputStream.STDOUT)
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, policy.state)
    }

    @Test
    fun invalidOnewayCallbackFailsClosedAndCannotBeRetried() {
        val reordered = startedPolicy()
        reordered.grantOutputCredits(2)
        assertThrows(IllegalStateException::class.java) {
            reordered.onOutput(chunk(8, PythonOutputStream.STDOUT, "bad"), PythonOutputStream.STDOUT)
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, reordered.state)
        assertThrows(IllegalStateException::class.java) {
            reordered.onOutput(chunk(7, PythonOutputStream.STDOUT, "retry"), PythonOutputStream.STDOUT)
        }

        val wrongCallback = startedPolicy()
        wrongCallback.grantOutputCredits(1)
        assertThrows(IllegalArgumentException::class.java) {
            wrongCallback.onOutput(chunk(7, PythonOutputStream.STDOUT, "bad"), PythonOutputStream.STDERR)
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, wrongCallback.state)
    }

    @Test
    fun outputBeforeStartedAndOutputBeyondLimitsFailClosed() {
        val notStarted = policy()
        notStarted.grantOutputCredits(1)
        assertThrows(IllegalStateException::class.java) {
            notStarted.onOutput(chunk(7, PythonOutputStream.STDOUT, "x"), PythonOutputStream.STDOUT)
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, notStarted.state)
        assertEquals(
            PythonExecutionSessionPolicy.ReplayDisposition.FORBIDDEN_AFTER_DISPATCH,
            notStarted.replayDisposition,
        )

        val tooLarge = startedPolicy(maxOutputBytes = 3, maxChunkBytes = 3)
        tooLarge.grantOutputCredits(1)
        assertThrows(IllegalStateException::class.java) {
            tooLarge.onOutput(chunk(7, PythonOutputStream.STDOUT, "four"), PythonOutputStream.STDOUT)
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, tooLarge.state)

        val aggregate = startedPolicy(maxOutputBytes = 3, maxChunkBytes = 3)
        aggregate.grantOutputCredits(2)
        aggregate.onOutput(chunk(7, PythonOutputStream.STDOUT, "ok"), PythonOutputStream.STDOUT)
        assertThrows(IllegalStateException::class.java) {
            aggregate.onOutput(chunk(8, PythonOutputStream.STDOUT, "no"), PythonOutputStream.STDOUT)
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, aggregate.state)
    }

    @Test
    fun oneByteChunksStopAtIndependentLifetimeChunkLimit() {
        val policy = startedPolicy(maxOutputBytes = 10, maxChunkBytes = 1, maxOutputChunks = 2)
        policy.grantOutputCredits(3)
        policy.onOutput(chunk(7, PythonOutputStream.STDOUT, "a"), PythonOutputStream.STDOUT)
        policy.onOutput(chunk(8, PythonOutputStream.STDERR, "b"), PythonOutputStream.STDERR)
        assertThrows(IllegalStateException::class.java) {
            policy.onOutput(chunk(9, PythonOutputStream.STDOUT, "c"), PythonOutputStream.STDOUT)
        }
        assertEquals(1, policy.stdoutBytes)
        assertEquals(1, policy.stderrBytes)
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, policy.state)
    }

    @Test
    fun resultMustMatchObservedOutputAndRejectReservedDescriptors() {
        val mismatch = startedPolicy().apply {
            grantOutputCredits(1)
            onOutput(chunk(7, PythonOutputStream.STDOUT, "ok"), PythonOutputStream.STDOUT)
        }
        assertThrows(IllegalArgumentException::class.java) {
            mismatch.onResult(result(stdoutBytes = 1, stdoutChunks = 1), descriptorCount = 0)
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, mismatch.state)

        val descriptorInjection = startedPolicy()
        assertThrows(IllegalArgumentException::class.java) {
            descriptorInjection.onResult(result(), descriptorCount = 1)
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, descriptorInjection.state)

        val valid = startedPolicy().apply {
            grantOutputCredits(1)
            onOutput(chunk(7, PythonOutputStream.STDOUT, "ok"), PythonOutputStream.STDOUT)
        }
        valid.onResult(result(stdoutBytes = 2, stdoutChunks = 1), descriptorCount = 0)
        assertEquals(PythonExecutionSessionPolicy.Terminal.RESULT, valid.terminal)
    }

    @Test
    fun cancelIsIdempotentAndAnInFlightResultMayStillWin() {
        val policy = startedPolicy()
        policy.grantOutputCredits(1)
        assertTrue(policy.requestCancel())
        assertFalse(policy.requestCancel())
        assertThrows(IllegalStateException::class.java) { policy.grantOutputCredits(1) }
        policy.onOutput(chunk(7, PythonOutputStream.STDOUT, "ok"), PythonOutputStream.STDOUT)
        policy.onResult(result(stdoutBytes = 2, stdoutChunks = 1), descriptorCount = 0)
        assertEquals(PythonExecutionSessionPolicy.Terminal.RESULT, policy.terminal)
        assertFalse(policy.requestCancel())
        assertTrue(policy.close())
        assertFalse(policy.close())
    }

    @Test
    fun cancellationMayWinAfterAStartedCallbackAlreadyInFlight() {
        val policy = policy()
        assertTrue(policy.requestStart())
        assertTrue(policy.requestCancel())
        policy.onStarted(started())
        policy.onCancelled(cancellation())
        assertEquals(PythonExecutionSessionPolicy.Terminal.CANCELLED, policy.terminal)
        assertThrows(IllegalStateException::class.java) {
            policy.onResult(result(), descriptorCount = 0)
        }
    }

    @Test
    fun cancellationBeforeStartIsTerminalWithoutDispatch() {
        val policy = policy()
        assertTrue(policy.requestCancel())
        assertFalse(policy.requestStart())
        policy.onCancelled(cancellation(elapsedMillis = 0))
        assertEquals(PythonExecutionSessionPolicy.Terminal.CANCELLED, policy.terminal)
    }

    @Test
    fun creditWindowRejectsInvalidAndOverflowGrants() {
        val zero = policy()
        assertThrows(IllegalArgumentException::class.java) { zero.grantOutputCredits(0) }
        assertEquals(PythonExecutionSessionPolicy.State.CREATED, zero.state)

        val negative = policy()
        assertThrows(IllegalArgumentException::class.java) { negative.grantOutputCredits(-1) }
        assertEquals(PythonExecutionSessionPolicy.State.CREATED, negative.state)

        val overflow = policy()
        overflow.grantOutputCredits(4)
        assertThrows(IllegalArgumentException::class.java) { overflow.grantOutputCredits(1) }
        assertEquals(PythonExecutionSessionPolicy.State.CREATED, overflow.state)
    }

    @Test
    fun mismatchedStartedRuntimeIdentityFailsClosed() {
        val policy = policy()
        policy.requestStart()
        assertThrows(IllegalArgumentException::class.java) {
            policy.onStarted(started().copy(pythonVersion = "3.14.0"))
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, policy.state)
    }

    @Test
    fun postDispatchTimeoutRoundTripsAndBecomesFailedTerminal() {
        val policy = policy()
        assertTrue(policy.requestStart())
        val timeout = PythonExecutionError(
            PythonRuntimeFixtures.requestId,
            PythonErrorCode.TIMEOUT,
            PythonFailurePhase.RUNTIME_START,
            "execution deadline exceeded",
            retryableBeforeDispatch = false,
        )
        val decoded = PythonRuntimeCodec.decodeExecutionError(
            PythonRuntimeCodec.encodeExecutionError(timeout),
        )

        policy.onFailed(decoded)
        assertEquals(PythonExecutionSessionPolicy.Terminal.FAILED, policy.terminal)
        assertEquals(PythonExecutionSessionPolicy.State.TERMINAL, policy.state)
    }

    @Test
    fun heterogeneousTerminalRaceAcceptsExactlyOneCallback() {
        val policy = startedPolicy()
        assertTrue(policy.requestCancel())
        val error = PythonExecutionError(
            PythonRuntimeFixtures.requestId,
            PythonErrorCode.INTERNAL,
            PythonFailurePhase.EXECUTION,
            "failed",
            retryableBeforeDispatch = false,
        )
        val winners = AtomicInteger()
        val executor = Executors.newFixedThreadPool(8)
        repeat(30) { index ->
            executor.submit {
                try {
                    when (index % 3) {
                        0 -> policy.onResult(result(), descriptorCount = 0)
                        1 -> policy.onFailed(error)
                        else -> policy.onCancelled(cancellation())
                    }
                    winners.incrementAndGet()
                } catch (_: RuntimeException) {
                    // Another callback won the single terminal gate.
                }
            }
        }
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(1, winners.get())
        assertTrue(policy.terminal in PythonExecutionSessionPolicy.Terminal.entries)
    }

    @Test
    fun interactivePromptsAndRepliesAreMonotonicSingleShotAndBounded() {
        val policy = startedInteractivePolicy()
        policy.onInputPrompt(PythonRuntimeFixtures.inputPrompt)
        assertTrue(policy.replyInput(PythonRuntimeFixtures.inputValueReply))
        policy.onInputPrompt(
            PythonRuntimeFixtures.inputPrompt.copy(promptId = PythonPromptId.fromLong(2)),
        )
        assertTrue(
            policy.replyInput(
                PythonRuntimeFixtures.inputEofReply.copy(promptId = PythonPromptId.fromLong(2)),
            ),
        )
        policy.onResult(result(), descriptorCount = 0)
        assertEquals(PythonExecutionSessionPolicy.Terminal.RESULT, policy.terminal)
        assertFalse(policy.replyInput(PythonRuntimeFixtures.inputValueReply))

        val duplicateReply = startedInteractivePolicy()
        duplicateReply.onInputPrompt(PythonRuntimeFixtures.inputPrompt)
        assertTrue(duplicateReply.replyInput(PythonRuntimeFixtures.inputValueReply))
        assertThrows(IllegalStateException::class.java) {
            duplicateReply.replyInput(PythonRuntimeFixtures.inputValueReply)
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, duplicateReply.state)

        val wrongId = startedInteractivePolicy()
        assertThrows(IllegalStateException::class.java) {
            wrongId.onInputPrompt(
                PythonRuntimeFixtures.inputPrompt.copy(promptId = PythonPromptId.fromLong(2)),
            )
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, wrongId.state)

        val promptLimit = startedInteractivePolicy(
            PythonRuntimeFixtures.interactiveInputPolicy.copy(maxPrompts = 1),
        )
        promptLimit.onInputPrompt(PythonRuntimeFixtures.inputPrompt)
        promptLimit.replyInput(PythonRuntimeFixtures.inputEofReply)
        assertThrows(IllegalStateException::class.java) {
            promptLimit.onInputPrompt(
                PythonRuntimeFixtures.inputPrompt.copy(promptId = PythonPromptId.fromLong(2)),
            )
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, promptLimit.state)
    }

    @Test
    fun interactivePromptRequiresAuthorizationAndTerminalEventsInvalidateLateReplies() {
        val unauthorized = startedPolicy()
        assertThrows(IllegalArgumentException::class.java) {
            unauthorized.onInputPrompt(PythonRuntimeFixtures.inputPrompt)
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, unauthorized.state)

        val failed = startedInteractivePolicy()
        failed.onInputPrompt(PythonRuntimeFixtures.inputPrompt)
        failed.onFailed(
            PythonExecutionError(
                PythonRuntimeFixtures.requestId,
                PythonErrorCode.INPUT_TIMEOUT,
                PythonFailurePhase.INTERACTIVE_INPUT,
                "input wait expired",
                retryableBeforeDispatch = false,
            ),
        )
        assertEquals(PythonExecutionSessionPolicy.Terminal.FAILED, failed.terminal)
        assertFalse(failed.replyInput(PythonRuntimeFixtures.inputValueReply))

        val pendingResult = startedInteractivePolicy()
        pendingResult.onInputPrompt(PythonRuntimeFixtures.inputPrompt)
        assertThrows(IllegalStateException::class.java) {
            pendingResult.onResult(result(), descriptorCount = 0)
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, pendingResult.state)
    }

    @Test
    fun localCloseMayEndWithoutAProtocolTerminalCallback() {
        val policy = policy()
        assertNull(policy.terminal)
        assertTrue(policy.close())
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, policy.state)
        assertNull(policy.terminal)
        assertEquals(
            PythonExecutionSessionPolicy.ReplayDisposition.FORBIDDEN_AFTER_DISPATCH,
            policy.replayDisposition,
        )
    }

    private fun policy(
        maxOutputBytes: Long = 1024,
        maxChunkBytes: Int = 256,
        maxOutputChunks: Long = minOf(PythonRuntimeContract.MAX_OUTPUT_CHUNKS, maxOutputBytes),
    ) = PythonExecutionSessionPolicy(
        PythonRuntimeFixtures.sourceOnlyRequest(maxOutputBytes, maxChunkBytes, maxOutputChunks),
        maximumCredits = 4,
        expectedPythonVersion = PythonRuntimeFixtures.runtimeInfo.pythonVersion,
    )

    private fun startedPolicy(
        maxOutputBytes: Long = 1024,
        maxChunkBytes: Int = 256,
        maxOutputChunks: Long = minOf(PythonRuntimeContract.MAX_OUTPUT_CHUNKS, maxOutputBytes),
    ) = policy(maxOutputBytes, maxChunkBytes, maxOutputChunks).apply {
        requestStart()
        onStarted(started(maxOutputBytes, maxChunkBytes, maxOutputChunks))
    }

    @Test
    fun protocol14AcceptsOnlyAuthorizedStructuredResultDescriptors() {
        val accepted = PythonExecutionSessionPolicy(
            PythonRuntimeFixtures.r4ResultRequest,
            maximumCredits = 4,
            expectedPythonVersion = PythonRuntimeFixtures.runtimeInfo.pythonVersion,
        ).apply {
            requestStart()
            onStarted(
                started(maxOutputChunks = 128).copy(protocolVersion = PythonProtocolVersion(1, 4)),
            )
        }
        accepted.onResult(PythonRuntimeFixtures.structuredResult, descriptorCount = 1)
        assertEquals(PythonExecutionSessionPolicy.Terminal.RESULT, accepted.terminal)

        val mismatch = PythonExecutionSessionPolicy(
            PythonRuntimeFixtures.r4ResultRequest,
            maximumCredits = 4,
            expectedPythonVersion = PythonRuntimeFixtures.runtimeInfo.pythonVersion,
        ).apply {
            requestStart()
            onStarted(
                started(maxOutputChunks = 128).copy(protocolVersion = PythonProtocolVersion(1, 4)),
            )
        }
        assertThrows(PythonRuntimeContractException::class.java) {
            mismatch.onResult(PythonRuntimeFixtures.structuredResult, descriptorCount = 0)
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, mismatch.state)
    }

    private fun startedInteractivePolicy(
        inputPolicy: PythonInteractiveInputPolicy = PythonRuntimeFixtures.interactiveInputPolicy,
    ) = PythonExecutionSessionPolicy(
        PythonExecutionRequest(
            requestId = PythonRuntimeFixtures.requestId,
            protocolVersion = PythonProtocolVersion(1, 3),
            entryPoint = "main.py",
            source = PythonRuntimeFixtures.source,
            timeoutMillis = 30_000,
            maxOutputBytes = 1_024,
            maxOutputChunkBytes = 256,
            maxOutputChunks = 128,
            interactiveInput = inputPolicy,
        ),
        maximumCredits = 4,
        expectedPythonVersion = PythonRuntimeFixtures.runtimeInfo.pythonVersion,
    ).apply {
        requestStart()
        onStarted(
            started(maxOutputChunks = 128).copy(protocolVersion = PythonProtocolVersion(1, 3)),
        )
    }

    private fun started(
        maxOutputBytes: Long = 1024,
        maxChunkBytes: Int = 256,
        maxOutputChunks: Long = minOf(PythonRuntimeContract.MAX_OUTPUT_CHUNKS, maxOutputBytes),
    ) = PythonRuntimeFixtures.started.copy(
        effectiveMaxOutputBytes = maxOutputBytes,
        effectiveMaxOutputChunkBytes = maxChunkBytes,
        effectiveMaxOutputChunks = maxOutputChunks,
    )

    private fun chunk(sequence: Long, stream: PythonOutputStream, text: String) = PythonOutputChunk(
        PythonRuntimeFixtures.requestId,
        sequence,
        stream,
        text.toByteArray(),
    )

    private fun result(
        stdoutBytes: Long = 0,
        stderrBytes: Long = 0,
        stdoutChunks: Long = 0,
        stderrChunks: Long = 0,
    ) = PythonExecutionResult(
        PythonRuntimeFixtures.requestId,
        exitCode = 0,
        elapsedMillis = 10,
        stdoutBytes = stdoutBytes,
        stderrBytes = stderrBytes,
        stdoutChunks = stdoutChunks,
        stderrChunks = stderrChunks,
    )

    private fun cancellation(elapsedMillis: Long = 10) = PythonExecutionCancellation(
        PythonRuntimeFixtures.requestId,
        PythonCancellationReason.REQUESTED,
        PythonFailurePhase.CANCELLATION,
        elapsedMillis,
    )
}
