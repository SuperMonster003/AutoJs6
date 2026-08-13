package org.autojs.autojs.core.plugin.lua

import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.engine.LuaPluginScriptEngine
import org.autojs.autojs.engine.LuaProviderExecutionException
import org.autojs.autojs.engine.LuaRuntimeExecutionException
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.LuaScriptSource
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.lua.runtime.api.LuaExecutionErrorCode
import org.autojs.plugin.lua.runtime.api.LuaExecutionFailurePhase
import org.autojs.plugin.lua.runtime.api.LuaExecutionRequest
import org.autojs.plugin.lua.runtime.api.LuaProtocolVersion
import org.autojs.plugin.lua.runtime.api.LuaRequestId
import org.autojs.plugin.lua.runtime.api.LuaRetryDisposition
import org.autojs.plugin.lua.runtime.api.LuaRuntimeContract
import org.autojs.plugin.lua.runtime.api.LuaSha256
import org.autojs.plugin.lua.runtime.api.LuaValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.Reader
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

/**
 * Opt-in R2 tests for the real Android discovery, explicit binding, Binder caller, PFD, and
 * callback paths. The fixture is synthetic and does not establish native Lua or ABI conformance.
 *
 * Every test is selected by [ARG_SCENARIO]. Scenarios whose synchronous V1 call can outlive the
 * host operation additionally require a method-only runner invocation and [ARG_FRESH_PROCESS].
 * In particular, an abandoned create-execution call intentionally poisons the process-wide host
 * lane, so those methods must never share a target process with another Lua conformance method.
 */
class LuaRuntimeAndroidConformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun normalExecutionUsesExplicitBinderAndBalancesRemoteResources() = withSelectedFixture(
        scenario = NORMAL,
        methodName = "normalExecutionUsesExplicitBinderAndBalancesRemoteResources",
    ) { control ->
        val engine = LuaPluginScriptEngine(context)
        try {
            engine.init()
            assertEquals(LuaValue.StringValue(EXPECTED_RESULT), engine.execute(source()))
        } finally {
            engine.destroy()
        }

        val counters = control.awaitCounters("normal session cleanup") {
            it.sessionsFinished == 1L && it.closeCalls >= 1L && it.sourceFdsOpened == it.sourceFdsClosed
        }
        assertTrue("Normal execution did not perform INFO discovery", counters.pluginInfoCalls > 0L)
        assertEquals(counters.pluginInfoCalls, counters.pluginInfoExits)
        assertTrue("Normal execution did not read runtime metadata", counters.runtimeInfoCalls > 0L)
        assertEquals(counters.runtimeInfoCalls, counters.runtimeInfoExits)
        assertEquals(1L, counters.createCalls)
        assertEquals(1L, counters.createExits)
        assertEquals(1L, counters.startCalls)
        assertEquals(0L, counters.cancelCalls)
        assertEquals(1L, counters.sessionsCreated)
        assertEquals(1L, counters.sourceValidations)
        assertEquals(1L, counters.startedCallbacksSent)
        assertEquals(1L, counters.terminalCallbacksSent)
        assertBalancedResources(counters)
    }

    @Test
    fun malformedRuntimeInfoIsRejectedBeforeExecutionCreation() = withSelectedFixture(
        scenario = MALFORMED_RUNTIME_INFO,
        methodName = "malformedRuntimeInfoIsRejectedBeforeExecutionCreation",
    ) { control ->
        val failure = expectFailure(LuaRuntimeHostException::class.java) {
            newHost().prepare()
        }
        assertEquals(LuaRuntimeHostFailureCode.LUA_RUNTIME_UNAVAILABLE, failure.code)

        val counters = control.awaitCounters("malformed runtime-info rejection") {
            it.runtimeInfoCalls >= 1L && it.runtimeInfoCalls == it.runtimeInfoExits
        }
        assertEquals(0L, counters.createCalls)
        assertEquals(0L, counters.sessionsCreated)
        assertEquals(0L, counters.sourceFdsOpened)
    }

    @Test
    fun runtimeNullBindingFailsClosedWithoutCreatingSession() = withSelectedFixture(
        scenario = NULL_BINDING,
        methodName = "runtimeNullBindingFailsClosedWithoutCreatingSession",
        requiresFreshProcess = true,
    ) { control ->
        val failure = expectFailure(LuaRuntimeHostException::class.java) {
            newHost().prepare()
        }
        assertEquals(LuaRuntimeHostFailureCode.LUA_RUNTIME_UNAVAILABLE, failure.code)
        assertEquals(1, failure.evaluations.size)
        val evaluation = failure.evaluations.single()
        assertEquals(LuaRuntimeProviderRejection.BIND_FAILED, evaluation.rejection)
        assertEquals(FAKE_PACKAGE, evaluation.component.packageName)
        assertEquals(NULL_BINDING_SERVICE, evaluation.component.className)

        val counters = control.awaitCounters("actionless exact runtime null binding") {
            it.nullBindingCalls == 1L && it.pluginInfoCalls == it.pluginInfoExits
        }
        assertTrue("Null-binding discovery did not read plugin INFO", counters.pluginInfoCalls > 0L)
        assertEquals(1L, counters.nullBindingCalls)
        assertEquals(0L, counters.runtimeInfoCalls)
        assertEquals(0L, counters.runtimeInfoExits)
        assertEquals(0L, counters.createCalls)
        assertEquals(0L, counters.createExits)
        assertEquals(0L, counters.startCalls)
        assertEquals(0L, counters.cancelCalls)
        assertEquals(0L, counters.closeCalls)
        assertEquals(0L, counters.sourceFdsOpened)
        assertEquals(0L, counters.sourceFdsClosed)
        assertEquals(0L, counters.sessionsCreated)
        assertEquals(0L, counters.sessionsFinished)
        assertEquals(0L, counters.sourceValidations)
        assertEquals(0L, counters.startedCallbacksSent)
        assertEquals(0L, counters.outputCallbacksSent)
        assertEquals(0L, counters.terminalCallbacksSent)
        assertEquals(0L, counters.busyRejections)
        assertEquals(0L, counters.sourceReadOnlyChecks)
        assertEquals(0L, counters.sourceReadOnlyConfirmed)
        assertEquals(0L, counters.sourceContentFailures)
        assertBalancedResources(counters)
    }

    @Test
    fun twoEligibleRuntimeComponentsAreRejectedAsAmbiguous() = withSelectedFixture(
        scenario = AMBIGUITY,
        methodName = "twoEligibleRuntimeComponentsAreRejectedAsAmbiguous",
        selection = { it.selectRuntimeAmbiguity(NORMAL, DUPLICATE_TERMINAL) },
    ) { control ->
        val failure = expectFailure(LuaRuntimeHostException::class.java) {
            newHost().prepare()
        }
        assertEquals(LuaRuntimeHostFailureCode.LUA_RUNTIME_AMBIGUOUS, failure.code)
        assertEquals(2, failure.ambiguousCandidates.size)

        val counters = control.awaitCounters("two runtime-info probes") {
            it.runtimeInfoCalls == 2L && it.runtimeInfoCalls == it.runtimeInfoExits
        }
        assertEquals(0L, counters.createCalls)
        assertEquals(0L, counters.sessionsCreated)
    }

    @Test
    fun stalledPluginInfoTimesOutFailsFastWhileOccupiedAndRecoversAfterRelease() = withSelectedFixture(
        scenario = STALL_PLUGIN_INFO,
        methodName = "stalledPluginInfoTimesOutFailsFastWhileOccupiedAndRecoversAfterRelease",
        requiresFreshProcess = true,
    ) { control ->
        val host = newHost(HOSTILE_HANDSHAKE_TIMEOUT_MILLIS)
        val timeout = expectFailure(LuaRuntimeHostException::class.java) { host.prepare() }
        assertBinderFailure(timeout, LuaBinderCallFailure.TIMEOUT)

        control.awaitCounters("plugin INFO call to remain blocked after host timeout") {
            it.pluginInfoCalls == 1L && it.pluginInfoExits == 0L
        }
        val stalled = expectFailure(LuaRuntimeHostException::class.java) { host.prepare() }
        assertBinderFailure(stalled, LuaBinderCallFailure.STALLED)

        control.releasePluginInfoStall()
        control.awaitCounters("late plugin INFO return") {
            it.pluginInfoCalls == it.pluginInfoExits && it.pluginInfoExits >= 1L
        }
        awaitBinderLaneRecovery()
        assertEquals(LuaRuntimeContract.RUNTIME_SLOT_LUA54, host.prepare().runtimeInfo.runtimeSlot)
    }

    @Test
    fun stalledRuntimeInfoTimesOutFailsFastWhileOccupiedAndRecoversAfterRelease() = withSelectedFixture(
        scenario = STALL_RUNTIME_INFO,
        methodName = "stalledRuntimeInfoTimesOutFailsFastWhileOccupiedAndRecoversAfterRelease",
        requiresFreshProcess = true,
    ) { control ->
        val host = newHost(HOSTILE_HANDSHAKE_TIMEOUT_MILLIS)
        val timeout = expectFailure(LuaRuntimeHostException::class.java) { host.prepare() }
        assertBinderFailure(timeout, LuaBinderCallFailure.TIMEOUT)

        control.awaitCounters("runtime-info call to remain blocked after host timeout") {
            it.runtimeInfoCalls == 1L && it.runtimeInfoExits == 0L
        }
        val stalled = expectFailure(LuaRuntimeHostException::class.java) { host.prepare() }
        assertBinderFailure(stalled, LuaBinderCallFailure.STALLED)

        control.releaseRuntimeInfoStall()
        control.awaitCounters("late runtime-info return") {
            it.runtimeInfoCalls == it.runtimeInfoExits && it.runtimeInfoExits >= 1L
        }
        awaitBinderLaneRecovery()
        assertEquals(LuaRuntimeContract.RUNTIME_SLOT_LUA54, host.prepare().runtimeInfo.runtimeSlot)
    }

    @Test
    fun callbackBeforeCreateReturnContainsEitherObservedBinderOrdering() = withSelectedFixture(
        scenario = CALLBACK_BEFORE_START,
        methodName = "callbackBeforeCreateReturnContainsEitherObservedBinderOrdering",
    ) { control ->
        val engine = LuaPluginScriptEngine(context)
        val executor = newExecutionExecutor("lua-callback-before-start")
        engine.init()
        val future = executor.submit<Any> { engine.execute(source()) }
        try {
            // The callback is oneway. Sending it before the synchronous create reply does not
            // guarantee that a host Binder thread receives it before the owner dispatches start.
            val earlyFailure = observeFutureFailure(future, CALLBACK_ORDERING_OBSERVATION_MILLIS)
            if (earlyFailure == null) {
                control.awaitCounters("host start in the callback-receipt-late branch") {
                    it.startCalls == 1L
                }
                engine.forceStop()
                val stopped = awaitFutureFailure(future, EXECUTION_STOP_TIMEOUT_MILLIS)
                assertTrue(
                    "Callback ordering produced an unrelated execution failure: $stopped",
                    stopped is ScriptInterruptedException || stopped is LuaRuntimeExecutionException,
                )
            } else {
                assertTrue(
                    "Callback receipt before host start did not fail as a transport violation: $earlyFailure",
                    earlyFailure is LuaRuntimeExecutionException,
                )
            }
        } finally {
            stopAndClose(engine, future, executor)
        }

        val counters = control.awaitCounters("callback ordering diagnostic cleanup") {
            it.closeCalls >= 1L && it.sessionsFinished == 1L &&
                it.sourceFdsOpened == it.sourceFdsClosed
        }
        assertTrue("Unexpected callback ordering branch", counters.startCalls in 0L..1L)
        assertEquals(1L, counters.sourceValidations)
        assertEquals(1L, counters.startedCallbacksSent)
        assertBalancedResources(counters)
    }

    @Test
    fun duplicateTerminalKeepsFirstResultAndStillDisposesTheSession() = withSelectedFixture(
        scenario = DUPLICATE_TERMINAL,
        methodName = "duplicateTerminalKeepsFirstResultAndStillDisposesTheSession",
    ) { control ->
        val engine = LuaPluginScriptEngine(context)
        try {
            engine.init()
            assertEquals(LuaValue.StringValue(EXPECTED_RESULT), engine.execute(source()))
        } finally {
            engine.destroy()
        }

        // V1 returns the first terminal outcome. It does not yet expose a diagnostic that proves
        // the later duplicate was observed, so this gate covers containment and cleanup only.
        val counters = control.awaitCounters("duplicate-terminal session cleanup") {
            it.sessionsFinished == 1L && it.closeCalls >= 1L && it.sourceFdsOpened == it.sourceFdsClosed
        }
        assertEquals(1L, counters.startCalls)
        assertEquals(1L, counters.sourceValidations)
        assertEquals(2L, counters.terminalCallbacksSent)
        assertBalancedResources(counters)
    }

    @Test
    fun zeroCreditOutputRecordsObservedOnewayOrderingAndAlwaysCleansUp() = withSelectedFixture(
        scenario = ZERO_CREDIT_OUTPUT,
        methodName = "zeroCreditOutputRecordsObservedOnewayOrderingAndAlwaysCleansUp",
        requiresFreshProcess = true,
    ) { control ->
        val engine = LuaPluginScriptEngine(context)
        val executor = newExecutionExecutor("lua-zero-credit-output")
        engine.init()
        val future = executor.submit<Any> { engine.execute(source()) }
        try {
            control.awaitCounters("zero-credit output send while create is retained") {
                it.createCalls == 1L && it.createExits == 0L && it.outputCallbacksSent == 1L
            }
            // Sending precedes credits, but oneway receipt relative to the synchronous create
            // reply is not guaranteed. Keep this as a bounded ordering diagnostic.
            SystemClock.sleep(CALLBACK_ORDERING_OBSERVATION_MILLIS)
            control.releaseCreateExecutionStall()
            when (val outcome = awaitExecutionOutcome(future, EXECUTION_STOP_TIMEOUT_MILLIS)) {
                is ExecutionOutcome.Returned ->
                    assertEquals(LuaValue.StringValue(EXPECTED_RESULT), outcome.value)
                is ExecutionOutcome.Failed ->
                    assertTrue(
                        "Zero-credit diagnostic failed outside Lua protocol handling: ${outcome.failure}",
                        outcome.failure is LuaRuntimeExecutionException,
                    )
            }
        } finally {
            runCatching { control.releaseCreateExecutionStall() }
            stopAndClose(engine, future, executor)
        }

        val counters = control.awaitCounters("zero-credit diagnostic cleanup") {
            it.createExits == 1L && it.sessionsFinished == 1L && it.closeCalls >= 1L &&
                it.sourceFdsOpened == it.sourceFdsClosed
        }
        assertEquals(1L, counters.outputCallbacksSent)
        assertEquals(1L, counters.sourceValidations)
        assertTrue("Unexpected zero-credit ordering branch", counters.startCalls in 0L..1L)
        assertBalancedResources(counters)
    }

    @Test
    fun sequenceGapFailsClosedAfterOneInvalidOutput() = withSelectedFixture(
        scenario = SEQUENCE_GAP,
        methodName = "sequenceGapFailsClosedAfterOneInvalidOutput",
    ) { control ->
        assertOutputProtocolViolation(control, expectedOutputCallbacks = 1L)
    }

    @Test
    fun sequenceReplayFailsClosedAfterTheRepeatedOutput() = withSelectedFixture(
        scenario = SEQUENCE_REPLAY,
        methodName = "sequenceReplayFailsClosedAfterTheRepeatedOutput",
    ) { control ->
        assertOutputProtocolViolation(control, expectedOutputCallbacks = 2L)
    }

    @Test
    fun outputForAnotherRequestIdFailsClosed() = withSelectedFixture(
        scenario = WRONG_REQUEST_ID,
        methodName = "outputForAnotherRequestIdFailsClosed",
    ) { control ->
        assertOutputProtocolViolation(control, expectedOutputCallbacks = 1L)
    }

    @Test
    fun truncatedRawSourcePfdFailsBeforeSessionCreation() {
        assertRawSourceRejected(
            scenario = RAW_SOURCE_TRUNCATED,
            methodName = "truncatedRawSourcePfdFailsBeforeSessionCreation",
            actualSource = RAW_SOURCE.copyOf(RAW_SOURCE.size - 1),
            expectedFailureCount = { it.sourceEarlyEofFailures },
        )
    }

    @Test
    fun trailingRawSourcePfdFailsBeforeSessionCreation() {
        assertRawSourceRejected(
            scenario = RAW_SOURCE_TRAILING,
            methodName = "trailingRawSourcePfdFailsBeforeSessionCreation",
            actualSource = RAW_SOURCE + byteArrayOf(0x00),
            expectedFailureCount = { it.sourceTrailingDataFailures },
        )
    }

    @Test
    fun digestMismatchedRawSourcePfdFailsBeforeSessionCreation() {
        val changedSource = RAW_SOURCE.copyOf().also { bytes ->
            val index = bytes.size / 2
            bytes[index] = (bytes[index].toInt() xor 0x01).toByte()
        }
        assertRawSourceRejected(
            scenario = RAW_SOURCE_DIGEST_MISMATCH,
            methodName = "digestMismatchedRawSourcePfdFailsBeforeSessionCreation",
            actualSource = changedSource,
            expectedFailureCount = { it.sourceDigestMismatchFailures },
        )
    }

    @Test
    fun ignoredCancelStillStopsLocallyAndClosesTheRemoteSession() = withSelectedFixture(
        scenario = IGNORE_CANCEL,
        methodName = "ignoredCancelStillStopsLocallyAndClosesTheRemoteSession",
        requiresFreshProcess = true,
    ) { control ->
        val engine = LuaPluginScriptEngine(context)
        val executor = newExecutionExecutor("lua-ignore-cancel")
        engine.init()
        val future = executor.submit<Any> { engine.execute(source()) }
        try {
            control.awaitCounters("ignore-cancel session start") { it.startCalls == 1L }
            engine.forceStop()
            val failure = awaitFutureFailure(future, EXECUTION_STOP_TIMEOUT_MILLIS)
            assertTrue(
                "Forced Lua stop did not map to ScriptInterruptedException: $failure",
                failure is ScriptInterruptedException,
            )
        } finally {
            stopAndClose(engine, future, executor)
        }

        val counters = control.awaitCounters("ignore-cancel forced cleanup") {
            it.cancelCalls >= 1L && it.closeCalls >= 1L && it.sessionsFinished == 1L &&
                it.sourceFdsOpened == it.sourceFdsClosed
        }
        assertEquals(1L, counters.sourceValidations)
        assertBalancedResources(counters)
    }

    @Test
    fun secondCreateReceivesBusyWhileFirstSessionRemainsActive() = withSelectedFixture(
        scenario = BUSY_SECOND_CREATE,
        methodName = "secondCreateReceivesBusyWhileFirstSessionRemainsActive",
        requiresFreshProcess = true,
    ) { control ->
        val firstEngine = LuaPluginScriptEngine(context)
        val firstExecutor = newExecutionExecutor("lua-busy-first")
        firstEngine.init()
        val firstFuture = firstExecutor.submit<Any> { firstEngine.execute(source()) }
        try {
            control.awaitCounters("first execution to occupy the provider") {
                it.sessionsCreated == 1L && it.startCalls == 1L && it.startedCallbacksSent == 1L
            }

            val secondEngine = LuaPluginScriptEngine(context)
            try {
                secondEngine.init()
                val busy = expectFailure(LuaProviderExecutionException::class.java) {
                    secondEngine.execute(source())
                }
                assertEquals(LuaExecutionErrorCode.BUSY, busy.providerError.code)
                assertEquals(LuaExecutionFailurePhase.QUEUE, busy.providerError.phase)
                assertEquals(LuaRetryDisposition.NEW_REQUEST_MAY_SUCCEED, busy.providerError.retryDisposition)
            } finally {
                secondEngine.destroy()
            }

            firstEngine.forceStop()
            val stopped = awaitFutureFailure(firstFuture, EXECUTION_STOP_TIMEOUT_MILLIS)
            assertTrue(
                "First busy-fixture execution was not locally stoppable: $stopped",
                stopped is ScriptInterruptedException,
            )
        } finally {
            stopAndClose(firstEngine, firstFuture, firstExecutor)
        }

        val counters = control.awaitCounters("busy rejection and both session cleanups") {
            it.busyRejections == 1L && it.sessionsFinished == 2L &&
                it.closeCalls >= 2L && it.sourceValidations == 2L &&
                it.sourceFdsOpened == it.sourceFdsClosed
        }
        assertEquals(2L, counters.createCalls)
        assertEquals(2L, counters.createExits)
        assertEquals(2L, counters.startCalls)
        assertEquals(2L, counters.sessionsCreated)
        assertTrue("Busy fixture did not close both sessions", counters.closeCalls >= 2L)
        assertBalancedResources(counters)
    }

    @Test
    fun interruptedStalledCreatePoisonsLaneAndClosesRetainedSourceAfterRelease() = withSelectedFixture(
        scenario = STALL_CREATE,
        methodName = "interruptedStalledCreatePoisonsLaneAndClosesRetainedSourceAfterRelease",
        requiresFreshProcess = true,
    ) { control ->
        assertCreateAbandonment(control, expectLateSession = false)
    }

    @Test
    fun lateCreateSessionIsCancelledClosedAndNeverStartedAfterCallerAbandons() = withSelectedFixture(
        scenario = LATE_CREATE_RETURN_SESSION,
        methodName = "lateCreateSessionIsCancelledClosedAndNeverStartedAfterCallerAbandons",
        requiresFreshProcess = true,
    ) { control ->
        assertCreateAbandonment(control, expectLateSession = true)
    }

    private fun assertOutputProtocolViolation(
        control: FakeLuaConformanceControlClient,
        expectedOutputCallbacks: Long,
    ) {
        val engine = LuaPluginScriptEngine(context)
        try {
            engine.init()
            expectFailure(LuaRuntimeExecutionException::class.java) {
                engine.execute(source())
            }
        } finally {
            engine.destroy()
        }

        val counters = control.awaitCounters("output protocol-violation cleanup") {
            it.sessionsFinished == 1L && it.closeCalls >= 1L && it.sourceFdsOpened == it.sourceFdsClosed
        }
        assertEquals(1L, counters.createCalls)
        assertEquals(1L, counters.createExits)
        assertEquals(1L, counters.startCalls)
        assertEquals(1L, counters.sourceValidations)
        assertEquals(1L, counters.startedCallbacksSent)
        assertEquals(expectedOutputCallbacks, counters.outputCallbacksSent)
        assertEquals(1L, counters.terminalCallbacksSent)
        assertBalancedResources(counters)
    }

    private fun assertRawSourceRejected(
        scenario: String,
        methodName: String,
        actualSource: ByteArray,
        expectedFailureCount: (FakeLuaConformanceCounters) -> Long,
    ) = withSelectedFixture(
        scenario = scenario,
        methodName = methodName,
        selection = { it.selectScenario(NORMAL) },
    ) { control ->
        val request = LuaExecutionRequest(
            requestId = LuaRequestId.fromUuid(
                UUID.nameUUIDFromBytes(scenario.toByteArray(Charsets.UTF_8)),
            ),
            protocolVersion = LuaProtocolVersion(
                LuaRuntimeContract.PROTOCOL_MAJOR,
                LuaRuntimeContract.PROTOCOL_MINOR,
            ),
            sourceName = "$scenario.lua",
            sourceLengthBytes = RAW_SOURCE.size.toLong(),
            sourceSha256 = LuaSha256.digest(RAW_SOURCE),
        )
        val observation = RawLuaRuntimeProviderClient.bind(context).use { client ->
            client.submitExpectingSourceRejection(request, actualSource)
        }
        assertTrue(
            "Raw source rejection did not cross Binder as an argument failure: ${observation.failure}",
            observation.failure is IllegalArgumentException,
        )
        assertTrue(
            "Provider-side close unexpectedly invalidated the caller-owned source descriptor",
            observation.callerDescriptorOpenAfterRemote,
        )
        assertTrue(
            "Caller-owned source descriptor remained open after the test client closed it",
            observation.callerDescriptorClosedByOwner,
        )
        assertEquals(0, observation.callbackCalls)
        assertEquals(0, observation.brokerCalls)

        val counters = control.awaitCounters("raw source rejection and descriptor cleanup") {
            it.createExits == 1L && it.sourceContentFailures == 1L &&
                it.sourceFdsOpened == it.sourceFdsClosed
        }
        assertEquals(1L, counters.createCalls)
        assertEquals(1L, counters.createExits)
        assertEquals(0L, counters.startCalls)
        assertEquals(0L, counters.cancelCalls)
        assertEquals(0L, counters.closeCalls)
        assertEquals(3L, counters.sourceFdsOpened)
        assertEquals(3L, counters.sourceFdsClosed)
        assertEquals(0L, counters.sessionsCreated)
        assertEquals(0L, counters.sessionsFinished)
        assertEquals(0L, counters.sourceValidations)
        assertEquals(1L, counters.sourceReadOnlyChecks)
        assertEquals(1L, counters.sourceReadOnlyConfirmed)
        assertEquals(1L, expectedFailureCount(counters))
        assertEquals(1L, counters.sourceContentFailures)
        assertEquals(0L, counters.startedCallbacksSent)
        assertEquals(0L, counters.outputCallbacksSent)
        assertEquals(0L, counters.terminalCallbacksSent)
        assertBalancedResources(counters)
    }

    private fun assertCreateAbandonment(
        control: FakeLuaConformanceControlClient,
        expectLateSession: Boolean,
    ) {
        val engine = LuaPluginScriptEngine(context)
        val executor = newExecutionExecutor("lua-create-abandonment")
        engine.init()
        val future = executor.submit<Any> { engine.execute(source()) }
        try {
            val stalled = control.awaitCounters("create-execution call to enter its retained-FD stall") {
                it.createCalls == 1L && it.createExits == 0L && it.sourceFdsOpened >= 1L
            }
            assertTrue(
                "Stalled create closed its retained source too early",
                stalled.sourceFdsClosed < stalled.sourceFdsOpened,
            )
            assertEquals(0L, stalled.sessionsCreated)

            engine.forceStop()
            val failure = awaitFutureFailure(future, EXECUTION_STOP_TIMEOUT_MILLIS)
            assertTrue(
                "Interrupted create did not map to ScriptInterruptedException: $failure",
                failure is ScriptInterruptedException,
            )
            assertBinderLanePoisoned()

            control.releaseCreateExecutionStall()
            val drained = if (expectLateSession) {
                control.awaitCounters("late session cancellation, close, and FD cleanup") {
                    it.createExits == 1L && it.sessionsCreated == 1L && it.sessionsFinished == 1L &&
                        it.cancelCalls >= 1L && it.closeCalls >= 1L && it.sourceFdsOpened == it.sourceFdsClosed
                }
            } else {
                control.awaitCounters("stalled create failure and retained-FD cleanup") {
                    it.createExits == 1L && it.sourceFdsOpened == it.sourceFdsClosed
                }
            }
            assertEquals(0L, drained.startCalls)
            assertEquals(if (expectLateSession) 1L else 0L, drained.sessionsCreated)
            assertEquals(if (expectLateSession) 1L else 0L, drained.sessionsFinished)
            assertEquals(if (expectLateSession) 1L else 0L, drained.sourceValidations)
            assertBalancedResources(drained)
            assertBinderLanePoisoned()
        } finally {
            runCatching { control.releaseCreateExecutionStall() }
            stopAndClose(engine, future, executor)
        }
    }

    private fun withSelectedFixture(
        scenario: String,
        methodName: String,
        requiresFreshProcess: Boolean = false,
        selection: (FakeLuaConformanceControlClient) -> Unit = { it.selectScenario(scenario) },
        test: (FakeLuaConformanceControlClient) -> Unit,
    ) {
        requireConformanceEnvironment(scenario, methodName, requiresFreshProcess)
        FakeLuaConformanceControlClient.bind(context).use { control ->
            selection(control)
            test(control)
        }
    }

    private fun requireConformanceEnvironment(
        expectedScenario: String,
        methodName: String,
        requiresFreshProcess: Boolean,
    ) {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Lua runtime Android conformance is opt-in; set $ARG_ENABLED=true",
            arguments.getString(ARG_ENABLED).toBoolean(),
        )
        assumeTrue(
            "Select exactly one Lua fixture mode with $ARG_SCENARIO=$expectedScenario",
            arguments.getString(ARG_SCENARIO) == expectedScenario,
        )

        assertTrue("Lua runtime conformance requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertTrue("Lua runtime conformance requires a debuggable host build", BuildConfig.DEBUG)
        assertEquals("Lua fixture authorizes only the production host package ID", HOST_PACKAGE, context.packageName)
        assertTrue(
            "Lua runtime conformance cannot run on the Android main thread",
            Looper.myLooper() != Looper.getMainLooper(),
        )
        assertTrue("Lua runtime conformance fixture is not installed", isPackageInstalled(FAKE_PACKAGE))
        assertEquals(
            "Lua runtime conformance fixture and host must have the same debug/test signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, FAKE_PACKAGE),
        )

        val controlInfo = context.packageManager.getServiceInfo(FakeLuaConformanceControlClient.CONTROL_COMPONENT, 0)
        assertTrue("Lua conformance control service is not exported", controlInfo.exported)
        assertTrue("Lua conformance control service is disabled", controlInfo.enabled)
        assertEquals(LuaRuntimeContract.PLUGIN_PERMISSION, controlInfo.permission)

        claimProcessScenario(expectedScenario)
        if (requiresFreshProcess) {
            assertTrue(
                "Hostile synchronous scenario requires $ARG_FRESH_PROCESS=true and a new target process",
                arguments.getString(ARG_FRESH_PROCESS).toBoolean(),
            )
            assertEquals(
                "Hostile synchronous scenario must be invoked as one method only",
                "$TEST_CLASS_NAME#$methodName",
                arguments.getString(ARG_RUNNER_CLASS),
            )
        }
    }

    private fun claimProcessScenario(expectedScenario: String) {
        val prior = PROCESS_SCENARIO.get()
        if (prior == null && PROCESS_SCENARIO.compareAndSet(null, expectedScenario)) return
        assertEquals(
            "One target process attempted more than one Lua conformance scenario",
            expectedScenario,
            PROCESS_SCENARIO.get(),
        )
    }

    private fun newHost(
        handshakeTimeoutMillis: Long = NORMAL_HANDSHAKE_TIMEOUT_MILLIS,
    ): LuaRuntimePluginHost = LuaRuntimePluginHost(
        transport = AndroidLuaRuntimeProviderTransport(context, bindTimeoutMillis = ANDROID_BIND_TIMEOUT_MILLIS),
        environment = LuaRuntimeProviderEnvironment(
            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
            supportedProcessAbis = Build.SUPPORTED_ABIS.asList(),
            releaseBuild = !BuildConfig.DEBUG,
            allowUserApprovedProviders = true,
        ),
        handshakeTimeoutMillis = handshakeTimeoutMillis,
    )

    private fun awaitBinderLaneRecovery() {
        val deadline = SystemClock.elapsedRealtime() + LANE_RECOVERY_TIMEOUT_MILLIS
        var lastFailure: LuaBinderCallException? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                LuaRuntimeBinderCallLane.call("conformance-lane-probe", LANE_PROBE_TIMEOUT_MILLIS) { Unit }
                return
            } catch (failure: LuaBinderCallException) {
                if (failure.failure !in setOf(LuaBinderCallFailure.BUSY, LuaBinderCallFailure.STALLED)) throw failure
                lastFailure = failure
                SystemClock.sleep(COUNTER_POLL_MILLIS)
            }
        }
        throw AssertionError("Lua Binder lane did not recover after the fixture released its call", lastFailure)
    }

    private fun assertBinderLanePoisoned() {
        val failure = expectFailure(LuaBinderCallException::class.java) {
            LuaRuntimeBinderCallLane.call("conformance-poison-probe", LANE_PROBE_TIMEOUT_MILLIS) { Unit }
        }
        assertEquals(LuaBinderCallFailure.POISONED, failure.failure)
    }

    private fun assertBinderFailure(failure: Throwable, expected: LuaBinderCallFailure) {
        var current: Throwable? = failure
        while (current != null && current !is LuaBinderCallException) current = current.cause
        assertNotNull("Lua host failure did not retain its bounded-call cause", current)
        assertEquals(expected, (current as LuaBinderCallException).failure)
    }

    private fun assertBalancedResources(counters: FakeLuaConformanceCounters) {
        assertEquals("Lua fixture leaked a source descriptor", counters.sourceFdsOpened, counters.sourceFdsClosed)
        assertEquals("Lua fixture leaked a session", counters.sessionsCreated, counters.sessionsFinished)
    }

    private fun source() = object : LuaScriptSource("android-conformance") {
        override val script: String = "return 'fixture-does-not-execute-source'"
        override val scriptReader: Reader? = null
    }

    private fun newExecutionExecutor(name: String): ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }

    private fun awaitFutureFailure(future: Future<*>, timeoutMillis: Long): Throwable = try {
        val result = future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        throw AssertionError("Expected Lua execution failure, but it returned $result")
    } catch (failure: ExecutionException) {
        checkNotNull(failure.cause) { "Lua execution failed without a cause" }
    } catch (failure: TimeoutException) {
        throw AssertionError("Lua execution did not reach a host outcome within $timeoutMillis ms", failure)
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        throw AssertionError("Interrupted while waiting for the Lua execution outcome", interrupted)
    }

    private fun observeFutureFailure(future: Future<*>, timeoutMillis: Long): Throwable? = try {
        val result = future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        throw AssertionError("Callback-ordering diagnostic unexpectedly returned $result")
    } catch (failure: ExecutionException) {
        checkNotNull(failure.cause) { "Lua execution failed without a cause" }
    } catch (_: TimeoutException) {
        null
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        throw AssertionError("Interrupted while observing callback Binder ordering", interrupted)
    }

    private fun awaitExecutionOutcome(future: Future<*>, timeoutMillis: Long): ExecutionOutcome = try {
        ExecutionOutcome.Returned(future.get(timeoutMillis, TimeUnit.MILLISECONDS))
    } catch (failure: ExecutionException) {
        ExecutionOutcome.Failed(checkNotNull(failure.cause) { "Lua execution failed without a cause" })
    } catch (failure: TimeoutException) {
        throw AssertionError("Lua execution did not reach an outcome within $timeoutMillis ms", failure)
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        throw AssertionError("Interrupted while waiting for the Lua execution outcome", interrupted)
    }

    private fun stopAndClose(
        engine: LuaPluginScriptEngine,
        future: Future<*>,
        executor: ExecutorService,
    ) {
        if (!future.isDone) engine.forceStop()
        runCatching { future.get(EXECUTION_STOP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) }
        engine.destroy()
        executor.shutdownNow()
        assertTrue(
            "Lua conformance execution worker did not terminate",
            executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    private fun <T : Throwable> expectFailure(type: Class<T>, action: () -> Unit): T {
        try {
            action()
        } catch (failure: Throwable) {
            if (type.isInstance(failure)) return type.cast(failure)
            throw AssertionError("Expected ${type.name}, got ${failure.javaClass.name}", failure)
        }
        throw AssertionError("Expected ${type.name}, but the operation succeeded")
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private sealed interface ExecutionOutcome {
        data class Returned(val value: Any?) : ExecutionOutcome
        data class Failed(val failure: Throwable) : ExecutionOutcome
    }

    private companion object {
        const val ARG_ENABLED = "autojs.luaRuntime.conformance.enabled"
        const val ARG_SCENARIO = "autojs.luaRuntime.conformance.scenario"
        const val ARG_FRESH_PROCESS = "autojs.luaRuntime.conformance.freshProcess"
        const val ARG_RUNNER_CLASS = "class"
        const val TEST_CLASS_NAME = "org.autojs.autojs.core.plugin.lua.LuaRuntimeAndroidConformanceTest"

        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val FAKE_PACKAGE = FakeLuaConformanceControlClient.FAKE_PACKAGE
        const val EXPECTED_RESULT = "lua-conformance-ok"

        const val NORMAL = "normal"
        const val MALFORMED_RUNTIME_INFO = "malformed-runtime-info"
        const val NULL_BINDING = "null-binding"
        const val STALL_PLUGIN_INFO = "stall-plugin-info"
        const val STALL_RUNTIME_INFO = "stall-runtime-info"
        const val STALL_CREATE = "stall-create"
        const val LATE_CREATE_RETURN_SESSION = "late-create-return-session"
        const val CALLBACK_BEFORE_START = "callback-before-start"
        const val DUPLICATE_TERMINAL = "duplicate-terminal"
        const val IGNORE_CANCEL = "ignore-cancel"
        const val ZERO_CREDIT_OUTPUT = "zero-credit-output"
        const val SEQUENCE_GAP = "sequence-gap"
        const val SEQUENCE_REPLAY = "sequence-replay"
        const val WRONG_REQUEST_ID = "wrong-request-id"
        const val BUSY_SECOND_CREATE = "busy-second-create"
        const val RAW_SOURCE_TRUNCATED = "raw-source-truncated"
        const val RAW_SOURCE_TRAILING = "raw-source-trailing"
        const val RAW_SOURCE_DIGEST_MISMATCH = "raw-source-digest-mismatch"
        const val AMBIGUITY = "ambiguity"
        const val NULL_BINDING_SERVICE = "$FAKE_PACKAGE.service.NullBindingLuaRuntimeService"

        val RAW_SOURCE = "return 'raw-source-conformance'\n".toByteArray(Charsets.UTF_8)

        const val NORMAL_HANDSHAKE_TIMEOUT_MILLIS = 5_000L
        const val HOSTILE_HANDSHAKE_TIMEOUT_MILLIS = 2_000L
        const val ANDROID_BIND_TIMEOUT_MILLIS = 5_000L
        const val CALLBACK_ORDERING_OBSERVATION_MILLIS = 2_000L
        const val EXECUTION_STOP_TIMEOUT_MILLIS = 5_000L
        const val EXECUTOR_SHUTDOWN_TIMEOUT_MILLIS = 2_000L
        const val LANE_RECOVERY_TIMEOUT_MILLIS = 5_000L
        const val LANE_PROBE_TIMEOUT_MILLIS = 100L
        const val COUNTER_POLL_MILLIS = 20L

        val PROCESS_SCENARIO = AtomicReference<String?>()
    }
}
