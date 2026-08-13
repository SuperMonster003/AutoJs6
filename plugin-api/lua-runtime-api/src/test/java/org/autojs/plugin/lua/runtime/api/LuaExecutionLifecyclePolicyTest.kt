package org.autojs.plugin.lua.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaExecutionLifecyclePolicyTest {
    @Test
    fun happyPathConsumesCreditsAndCompletesExactlyOnce() {
        val policy = LuaExecutionSessionPolicy(LuaRuntimeFixtures.request(), LuaRuntimeFixtures.runtimeInfo())
        assertTrue(policy.requestStart())
        assertFalse(policy.requestStart())
        policy.grantOutputCredits(2)
        policy.onStarted(LuaRuntimeFixtures.started())
        policy.onOutput(output(7L, "one"))
        policy.onOutput(output(8L, "two"))
        policy.onCompleted(result())

        assertEquals(LuaExecutionSessionPolicy.State.TERMINAL, policy.state)
        assertEquals(LuaExecutionSessionPolicy.Terminal.COMPLETED, policy.terminal)
        assertEquals(0, policy.outstandingOutputCredits)
        assertEquals(6L, policy.emittedOutputBytes)
        assertThrows(IllegalStateException::class.java) { policy.onCompleted(result()) }
    }

    @Test
    fun outputBeforeStartedOrWithoutCreditIsRejectedWithoutMutation() {
        val policy = LuaExecutionSessionPolicy(LuaRuntimeFixtures.request(), LuaRuntimeFixtures.runtimeInfo())
        policy.requestStart()
        assertThrows(IllegalStateException::class.java) { policy.onOutput(output(7L, "early")) }
        policy.onStarted(LuaRuntimeFixtures.started())
        assertThrows(IllegalStateException::class.java) { policy.onOutput(output(7L, "no-credit")) }
        assertEquals(0L, policy.emittedOutputBytes)
        assertEquals(0, policy.outstandingOutputCredits)
    }

    @Test
    fun sequenceReplayAndGapDoNotConsumeCredit() {
        val policy = startedPolicy(credits = 2)

        assertThrows(IllegalStateException::class.java) { policy.onOutput(output(8L, "gap")) }
        assertEquals(2, policy.outstandingOutputCredits)
        policy.onOutput(output(7L, "accepted"))
        assertThrows(IllegalStateException::class.java) { policy.onOutput(output(7L, "replay")) }
        assertEquals(1, policy.outstandingOutputCredits)
    }

    @Test
    fun outputQuotaViolationDoesNotConsumeCredit() {
        val policy = LuaExecutionSessionPolicy(
            LuaRuntimeFixtures.request(outputByteLimit = 3L),
            LuaRuntimeFixtures.runtimeInfo(),
        )
        policy.requestStart()
        policy.grantOutputCredits(1)
        policy.onStarted(LuaRuntimeFixtures.started())

        assertThrows(IllegalStateException::class.java) { policy.onOutput(output(7L, "four")) }
        assertEquals(1, policy.outstandingOutputCredits)
        assertEquals(0L, policy.emittedOutputBytes)
    }

    @Test
    fun creditGrantMustBePositiveAndBounded() {
        assertThrows(IllegalArgumentException::class.java) {
            LuaOutputCreditWindow(LuaRuntimeContract.MAX_OUTSTANDING_OUTPUT_CREDITS + 1)
        }
        val policy = LuaExecutionSessionPolicy(LuaRuntimeFixtures.request(), LuaRuntimeFixtures.runtimeInfo())
        assertThrows(IllegalArgumentException::class.java) { policy.grantOutputCredits(0) }
        assertThrows(IllegalArgumentException::class.java) {
            policy.grantOutputCredits(LuaRuntimeContract.MAX_OUTSTANDING_OUTPUT_CREDITS + 1)
        }
        policy.grantOutputCredits(LuaRuntimeContract.MAX_OUTSTANDING_OUTPUT_CREDITS)
        assertThrows(IllegalArgumentException::class.java) { policy.grantOutputCredits(1) }
    }

    @Test
    fun cancellationIsIdempotentAndOwnsTheTerminalRace() {
        val policy = startedPolicy(credits = 1)
        assertTrue(policy.requestCancel())
        assertFalse(policy.requestCancel())
        assertThrows(IllegalStateException::class.java) { policy.onOutput(output(7L, "late")) }
        assertThrows(IllegalStateException::class.java) { policy.onCompleted(result()) }
        policy.onCancelled(
            LuaExecutionCancellation(LuaRuntimeFixtures.REQUEST_ID, LuaCancellationReason.REQUESTED, 4L),
        )

        assertEquals(LuaExecutionSessionPolicy.Terminal.CANCELLED, policy.terminal)
        assertFalse(policy.requestCancel())
    }

    @Test
    fun providerMayFailBeforeStartedAfterStartWasRequested() {
        val policy = LuaExecutionSessionPolicy(LuaRuntimeFixtures.request(), LuaRuntimeFixtures.runtimeInfo())
        policy.requestStart()
        policy.onFailed(error())

        assertEquals(LuaExecutionSessionPolicy.Terminal.FAILED, policy.terminal)
    }

    @Test
    fun wrongRequestIdIsRejected() {
        val policy = LuaExecutionSessionPolicy(LuaRuntimeFixtures.request(), LuaRuntimeFixtures.runtimeInfo())
        policy.requestStart()
        assertThrows(IllegalArgumentException::class.java) {
            policy.onStarted(LuaRuntimeFixtures.started(LuaRuntimeFixtures.OTHER_REQUEST_ID))
        }
    }

    @Test
    fun startedRuntimeIdentityMustMatchPinnedProvider() {
        val policy = LuaExecutionSessionPolicy(LuaRuntimeFixtures.request(), LuaRuntimeFixtures.runtimeInfo())
        policy.requestStart()

        assertThrows(IllegalArgumentException::class.java) {
            policy.onStarted(
                LuaRuntimeFixtures.started().copy(runtimeSlot = "lua55"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            policy.onStarted(
                LuaRuntimeFixtures.started().copy(languageVersion = "5.4.9"),
            )
        }
    }

    @Test
    fun closeIsIdempotentAndRejectsFurtherEvents() {
        val policy = LuaExecutionSessionPolicy(LuaRuntimeFixtures.request(), LuaRuntimeFixtures.runtimeInfo())
        assertTrue(policy.close())
        assertFalse(policy.close())
        assertThrows(IllegalStateException::class.java) { policy.requestStart() }
        assertThrows(IllegalStateException::class.java) { policy.onFailed(error()) }
    }

    @Test
    fun hostCallPolicyRejectsDeniedAndReplayedCalls() {
        val policy = LuaHostCallPolicy(
            LuaRuntimeFixtures.REQUEST_ID,
            allowedCapabilities = listOf("device.info"),
        )
        val request = hostRequest("call-1", "device.info")
        policy.open(request)
        assertEquals(1, policy.outstandingCount)
        assertThrows(IllegalStateException::class.java) { policy.open(request) }
        policy.complete(
            LuaHostCallResult(LuaRuntimeFixtures.REQUEST_ID, "call-1", LuaValue.StringValue("model")),
        )
        assertEquals(0, policy.outstandingCount)
        assertThrows(IllegalStateException::class.java) { policy.open(request) }
        assertThrows(IllegalArgumentException::class.java) {
            policy.open(hostRequest("call-2", "shell.exec"))
        }
    }

    @Test
    fun hostResultMustMatchOutstandingCallAndExecution() {
        val policy = LuaHostCallPolicy(LuaRuntimeFixtures.REQUEST_ID, listOf("device.info"))
        policy.open(hostRequest("call-1", "device.info"))

        assertThrows(IllegalArgumentException::class.java) {
            policy.complete(
                LuaHostCallResult(LuaRuntimeFixtures.OTHER_REQUEST_ID, "call-1", LuaValue.StringValue("x")),
            )
        }
        assertThrows(IllegalStateException::class.java) {
            policy.fail(
                LuaHostCallError(
                    LuaRuntimeFixtures.REQUEST_ID,
                    "unknown-call",
                    LuaHostErrorCode.INTERNAL,
                    "failed",
                ),
            )
        }
        assertEquals(1, policy.outstandingCount)
    }

    @Test
    fun hostCallDescriptorsFailClosedInV1() {
        val policy = LuaHostCallPolicy(LuaRuntimeFixtures.REQUEST_ID, listOf("device.info"))
        assertThrows(LuaContractException::class.java) {
            policy.open(hostRequest("call-1", "device.info"), descriptorCount = 1)
        }
        assertEquals(0, policy.outstandingCount)

        policy.open(hostRequest("call-2", "device.info"))
        assertThrows(LuaContractException::class.java) {
            policy.complete(
                LuaHostCallResult(
                    LuaRuntimeFixtures.REQUEST_ID,
                    "call-2",
                    LuaValue.StringValue("model"),
                ),
                descriptorCount = 1,
            )
        }
        assertEquals(1, policy.outstandingCount)
    }

    @Test
    fun hostCallLifetimeIsBoundedAndCloseIsIdempotent() {
        assertThrows(IllegalArgumentException::class.java) {
            LuaHostCallPolicy(
                LuaRuntimeFixtures.REQUEST_ID,
                allowedCapabilities = listOf("device.info"),
                maximumOutstandingCalls = LuaRuntimeContract.MAX_CONCURRENT_HOST_CALLS + 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            LuaHostCallPolicy(
                LuaRuntimeFixtures.REQUEST_ID,
                allowedCapabilities = listOf("device.info"),
                maximumTotalCalls = LuaRuntimeContract.MAX_HOST_CALLS_PER_EXECUTION + 1,
            )
        }
        val policy = LuaHostCallPolicy(
            LuaRuntimeFixtures.REQUEST_ID,
            allowedCapabilities = listOf("device.info"),
            maximumOutstandingCalls = 1,
            maximumTotalCalls = 1,
        )
        policy.open(hostRequest("call-1", "device.info"))
        policy.complete(
            LuaHostCallResult(LuaRuntimeFixtures.REQUEST_ID, "call-1", LuaValue.StringValue("model")),
        )
        assertThrows(IllegalStateException::class.java) {
            policy.open(hostRequest("call-2", "device.info"))
        }
        assertTrue(policy.close())
        assertFalse(policy.close())
        assertThrows(IllegalStateException::class.java) {
            policy.open(hostRequest("call-3", "device.info"))
        }
    }

    private fun startedPolicy(credits: Int): LuaExecutionSessionPolicy =
        LuaExecutionSessionPolicy(LuaRuntimeFixtures.request(), LuaRuntimeFixtures.runtimeInfo()).also { policy ->
            policy.requestStart()
            policy.grantOutputCredits(credits)
            policy.onStarted(LuaRuntimeFixtures.started())
        }

    private fun output(sequence: Long, text: String) = LuaOutputChunk(
        LuaRuntimeFixtures.REQUEST_ID,
        sequence,
        LuaOutputStream.STDOUT,
        text,
    )

    private fun result() = LuaExecutionResult(
        LuaRuntimeFixtures.REQUEST_ID,
        LuaValue.BooleanValue(true),
        9L,
    )

    private fun error() = LuaExecutionError(
        LuaRuntimeFixtures.REQUEST_ID,
        LuaExecutionErrorCode.RUNTIME_ERROR,
        LuaExecutionFailurePhase.EXECUTION,
        "failed",
        LuaRetryDisposition.DO_NOT_RETRY,
    )

    private fun hostRequest(callId: String, capability: String) = LuaHostCallRequest(
        LuaRuntimeFixtures.REQUEST_ID,
        callId,
        capability,
        LuaValue.MapValue(emptyMap()),
    )
}
