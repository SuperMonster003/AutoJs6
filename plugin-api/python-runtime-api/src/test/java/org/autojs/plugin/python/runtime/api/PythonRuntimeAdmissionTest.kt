package org.autojs.plugin.python.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PythonRuntimeAdmissionTest {
    @Test
    fun plannerCreatesFullyPinnedSessionPolicies() {
        val capabilities = capabilities(maxOutstandingOutputCredits = 3)
        val plan = createPlan(capabilities = capabilities)

        assertEquals(PythonProtocolVersion(1, 0), plan.negotiatedProtocolVersion)
        assertEquals(5_000L, plan.hostVersionCode)
        assertEquals("x86_64", plan.deviceAbi)
        assertEquals("3.13.9", plan.pythonVersion)
        assertEquals(3, plan.maxOutstandingOutputCredits)
        assertEquals(3, plan.descriptorCount)
        assertEquals(PythonSessionAdmissionState.PENDING, plan.admissionState)
        assertNull(plan.sessionPolicy)
        assertTrue(plan.onOpened())

        val session = checkNotNull(plan.sessionPolicy)
        session.grantOutputCredits(3)
        assertThrows(IllegalArgumentException::class.java) {
            session.grantOutputCredits(1)
        }
        assertTrue(session.requestStart())
        session.onStarted(PythonRuntimeFixtures.started)
        assertEquals(PythonExecutionSessionPolicy.State.STARTED, session.state)
    }

    @Test
    fun plannerRejectsHostVersionAndDeviceAbiMismatches() {
        assertViolation(PythonRuntimeContractViolation.CAPABILITY_INCOMPATIBLE) {
            createPlan(hostVersionCode = 4_999)
        }
        assertViolation(PythonRuntimeContractViolation.CAPABILITY_INCOMPATIBLE) {
            createPlan(
                runtimeInfo = PythonRuntimeFixtures.runtimeInfo.copy(maxHostVersionCode = 5_999),
                hostVersionCode = 6_000,
            )
        }
        assertViolation(PythonRuntimeContractViolation.CAPABILITY_INCOMPATIBLE) {
            createPlan(deviceAbi = "armeabi-v7a")
        }
        assertViolation(PythonRuntimeContractViolation.INVALID_VALUE) {
            createPlan(hostVersionCode = 0)
        }
    }

    @Test
    fun plannerRejectsIdentityProtocolRequestAndDescriptorMismatches() {
        assertViolation(PythonRuntimeContractViolation.CAPABILITY_INCOMPATIBLE) {
            createPlan(capabilities = capabilities(pythonVersion = "3.14.0"))
        }
        assertViolation(PythonRuntimeContractViolation.PROTOCOL_INCOMPATIBLE) {
            createPlan(negotiatedProtocolVersion = PythonProtocolVersion(1, 1))
        }
        assertViolation(PythonRuntimeContractViolation.DESCRIPTOR_MISMATCH) {
            createPlan(descriptorCount = 2)
        }

        val narrowedCapabilities = capabilities(maxOutputBytes = 512)
        assertViolation(PythonRuntimeContractViolation.CAPABILITY_INCOMPATIBLE) {
            createPlan(capabilities = narrowedCapabilities)
        }
    }

    @Test
    fun openedAndRejectedAreMutuallyExclusiveAndSingleShot() {
        val opened = createPlan()
        assertTrue(opened.onOpened())
        assertEquals(PythonSessionAdmissionState.OPENED, opened.admissionState)
        assertTrue(opened.sessionPolicy != null)
        assertFalse(opened.onOpened())
        assertEquals(PythonSessionAdmissionState.FAILED_CLOSED, opened.admissionState)
        assertNull(opened.sessionPolicy)
        assertNull(opened.admissionRejection)

        val rejected = createPlan()
        val error = admissionError()
        assertTrue(rejected.onRejected(error))
        assertFalse(rejected.onRejected(error))
        assertFalse(rejected.onOpened())
        assertEquals(PythonSessionAdmissionState.REJECTED, rejected.admissionState)
        assertSame(error, rejected.admissionRejection)
        assertNull(rejected.sessionPolicy)
    }

    @Test
    fun rejectionAcceptsOnlyMatchingPreDispatchAdmissionErrors() {
        val plan = createPlan()
        val executionError = PythonExecutionError(
            requestId = PythonRuntimeFixtures.requestId,
            code = PythonErrorCode.TIMEOUT,
            phase = PythonFailurePhase.EXECUTION,
            message = "timed out",
            retryableBeforeDispatch = false,
        )
        assertThrows(PythonRuntimeContractException::class.java) {
            plan.onRejected(executionError)
        }
        assertEquals(PythonSessionAdmissionState.FAILED_CLOSED, plan.admissionState)
        assertFalse(plan.onOpened())
        assertNull(plan.sessionPolicy)

        val mismatched = createPlan()
        val wrongRequest = PythonRequestId.fromUuid(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
        assertThrows(PythonRuntimeContractException::class.java) {
            mismatched.onRejected(admissionError(requestId = wrongRequest))
        }
        assertEquals(PythonSessionAdmissionState.FAILED_CLOSED, mismatched.admissionState)
        assertFalse(mismatched.onRejected(admissionError()))
    }

    @Test
    fun admissionDeadlineAbandonsPlanAndRejectsLateOpen() {
        val plan = createPlan()
        assertNull(plan.sessionPolicy)
        assertTrue(plan.abandonAdmission())
        assertEquals(PythonSessionAdmissionState.ABANDONED, plan.admissionState)
        assertFalse(plan.onOpened())
        assertNull(plan.sessionPolicy)
    }

    @Test
    fun malformedAdmissionCallbackFailsPlanClosed() {
        val plan = createPlan()
        assertTrue(plan.failClosedAdmission())
        assertEquals(PythonSessionAdmissionState.FAILED_CLOSED, plan.admissionState)
        assertFalse(plan.onOpened())
        assertNull(plan.sessionPolicy)
    }

    @Test
    fun concurrentOpenedAndRejectedRaceHasExactlyOneWinner() {
        val plan = createPlan()
        val accepted = AtomicInteger()
        val ready = CountDownLatch(32)
        val go = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(32)
        repeat(32) { index ->
            executor.submit {
                ready.countDown()
                go.await()
                val won = if (index % 2 == 0) {
                    plan.onOpened()
                } else {
                    plan.onRejected(admissionError())
                }
                if (won) accepted.incrementAndGet()
            }
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        go.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))

        assertEquals(1, accepted.get())
        assertTrue(plan.admissionState != PythonSessionAdmissionState.PENDING)
        assertNull(plan.sessionPolicy)
    }

    private fun createPlan(
        runtimeInfo: PythonRuntimeInfo = PythonRuntimeFixtures.runtimeInfo,
        capabilities: PythonRuntimeCapabilities = PythonRuntimeFixtures.capabilities,
        hostVersionCode: Long = 5_000,
        deviceAbi: String = "x86_64",
        negotiatedProtocolVersion: PythonProtocolVersion = PythonProtocolVersion(1, 0),
        request: PythonExecutionRequest = PythonRuntimeFixtures.request,
        descriptorCount: Int = 3,
    ) = PythonRuntimeAdmissionPlanner.create(
        runtimeInfo = runtimeInfo,
        capabilities = capabilities,
        hostVersionCode = hostVersionCode,
        deviceAbi = deviceAbi,
        negotiatedProtocolVersion = negotiatedProtocolVersion,
        request = request,
        descriptorCount = descriptorCount,
    )

    private fun capabilities(
        pythonVersion: String = PythonRuntimeFixtures.capabilities.pythonVersion,
        maxOutstandingOutputCredits: Int = PythonRuntimeFixtures.limits.maxOutstandingOutputCredits,
        maxOutputBytes: Long = PythonRuntimeFixtures.limits.maxOutputBytes,
    ) = PythonRuntimeCapabilities(
        implementation = PythonRuntimeFixtures.capabilities.implementation,
        pythonVersion = pythonVersion,
        supportedAbis = PythonRuntimeFixtures.capabilities.supportedAbis,
        supportsWorkspaceArchive = PythonRuntimeFixtures.capabilities.supportsWorkspaceArchive,
        supportsStdinSnapshot = PythonRuntimeFixtures.capabilities.supportsStdinSnapshot,
        supportsStructuredTraceback = PythonRuntimeFixtures.capabilities.supportsStructuredTraceback,
        supportsCooperativeCancellation = PythonRuntimeFixtures.capabilities.supportsCooperativeCancellation,
        cancellationMode = PythonRuntimeFixtures.capabilities.cancellationMode,
        limits = PythonRuntimeFixtures.limits.copy(
            maxOutstandingOutputCredits = maxOutstandingOutputCredits,
            maxOutputBytes = maxOutputBytes,
            maxOutputChunkBytes = minOf(
                PythonRuntimeFixtures.limits.maxOutputChunkBytes,
                maxOutputBytes.toInt(),
            ),
            maxOutputChunks = minOf(PythonRuntimeFixtures.limits.maxOutputChunks, maxOutputBytes),
        ),
    )

    private fun admissionError(
        requestId: PythonRequestId = PythonRuntimeFixtures.requestId,
    ) = PythonExecutionError(
        requestId = requestId,
        code = PythonErrorCode.BUSY,
        phase = PythonFailurePhase.SESSION_OPEN,
        message = "provider is busy",
        retryableBeforeDispatch = true,
    )

    private fun assertViolation(
        expected: PythonRuntimeContractViolation,
        block: () -> Unit,
    ) {
        val error = assertThrows(PythonRuntimeContractException::class.java, block)
        assertEquals(expected, error.violation)
    }
}
