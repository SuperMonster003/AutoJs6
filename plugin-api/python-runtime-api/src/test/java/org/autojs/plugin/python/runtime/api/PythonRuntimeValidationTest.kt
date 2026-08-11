package org.autojs.plugin.python.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonRuntimeValidationTest {
    @Test
    fun protocolNegotiationRequiresAnOverlappingRange() {
        assertEquals(
            PythonProtocolVersion(1, 0),
            PythonRuntimeNegotiation.requireNegotiated(PythonRuntimeFixtures.runtimeInfo),
        )

        val incompatible = PythonRuntimeFixtures.runtimeInfo.copy(
            protocolMin = PythonProtocolVersion(2, 0),
            protocolMax = PythonProtocolVersion(2, 0),
        )
        val error = assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeNegotiation.requireNegotiated(incompatible)
        }
        assertEquals(PythonRuntimeContractViolation.PROTOCOL_INCOMPATIBLE, error.violation)
    }

    @Test
    fun capabilitiesMustMatchTheAdvertisedRuntimeIdentity() {
        PythonRuntimeValidation.validateCapabilitiesAgainstInfo(
            PythonRuntimeFixtures.capabilities,
            PythonRuntimeFixtures.runtimeInfo,
        )
        val mismatched = PythonRuntimeFixtures.runtimeInfo.copy(pythonVersion = "3.14.0")
        val error = assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateCapabilitiesAgainstInfo(
                PythonRuntimeFixtures.capabilities,
                mismatched,
            )
        }
        assertEquals(PythonRuntimeContractViolation.CAPABILITY_INCOMPATIBLE, error.violation)

        val protocol10Only = assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateCapabilitiesAgainstInfo(
                PythonRuntimeFixtures.r5Capabilities,
                PythonRuntimeFixtures.runtimeInfo,
            )
        }
        assertEquals(PythonRuntimeContractViolation.CAPABILITY_INCOMPATIBLE, protocol10Only.violation)
        PythonRuntimeValidation.validateCapabilitiesAgainstInfo(
            PythonRuntimeFixtures.r5Capabilities,
            PythonRuntimeFixtures.runtimeInfo.copy(protocolMax = PythonProtocolVersion(1, 1)),
        )
    }

    @Test
    fun descriptorTableRequiresUniqueInRangeAndExhaustiveReferences() {
        PythonRuntimeValidation.validateDescriptorReferences(PythonRuntimeFixtures.request, 3)
        PythonRuntimeValidation.validateDescriptorReferences(PythonRuntimeFixtures.r5Request, 4)

        val reused = PythonExecutionRequest(
            requestId = PythonRuntimeFixtures.requestId,
            protocolVersion = PythonProtocolVersion(1, 0),
            entryPoint = "main.py",
            source = PythonRuntimeFixtures.source,
            stdin = PythonRuntimeFixtures.stdin.copy(descriptorIndex = 0),
        )
        assertDescriptorMismatch { PythonRuntimeValidation.validateDescriptorReferences(reused, 1) }
        assertDescriptorMismatch {
            PythonRuntimeValidation.validateDescriptorReferences(PythonRuntimeFixtures.request, 4)
        }
        assertDescriptorMismatch {
            PythonRuntimeValidation.validateDescriptorReferences(PythonRuntimeFixtures.request, 2)
        }
        assertDescriptorMismatch {
            PythonRuntimeValidation.validateDescriptorReferences(PythonRuntimeFixtures.request, Int.MAX_VALUE)
        }
    }

    @Test
    fun entryPointMustBeNormalizedRelativePythonPath() {
        listOf(
            "../main.py",
            "/main.py",
            "C:/main.py",
            "C:main.py",
            "src\\main.py",
            "main.pyc",
            "a//main.py",
            "line\nfeed.py",
            "e\u0301.py",
        ).forEach { path ->
            assertThrows(PythonRuntimeContractException::class.java) {
                PythonRuntimeValidation.validateRequest(requestWithPath(path))
            }
        }
        PythonRuntimeValidation.validateRequest(requestWithPath("SRC/Main.PY"))
    }

    @Test
    fun emptyPythonSourceIsAValidSnapshot() {
        val request = PythonExecutionRequest(
            requestId = PythonRuntimeFixtures.requestId,
            protocolVersion = PythonProtocolVersion(1, 0),
            entryPoint = "empty.py",
            source = PythonRuntimeFixtures.source.copy(
                declaredLengthBytes = 0,
                sha256 = PythonSha256.digest(ByteArray(0)),
            ),
        )

        PythonRuntimeValidation.validateRequest(request)
        PythonRuntimeValidation.validateDescriptorReferences(request, 1)
    }

    @Test
    fun narrowingOnlyTotalOutputKeepsDependentDefaultsValid() {
        val request = PythonExecutionRequest(
            requestId = PythonRuntimeFixtures.requestId,
            protocolVersion = PythonProtocolVersion(1, 0),
            entryPoint = "main.py",
            source = PythonRuntimeFixtures.source,
            maxOutputBytes = 17,
        )

        assertEquals(17, request.maxOutputChunkBytes)
        assertEquals(17, request.maxOutputChunks)
        PythonRuntimeValidation.validateRequest(request)
    }

    @Test
    fun requestCannotWidenProviderCapabilities() {
        val tooMuchOutput = PythonRuntimeFixtures.sourceOnlyRequest(
            maxOutputBytes = PythonRuntimeFixtures.limits.maxOutputBytes + 1,
        )
        val error = assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateRequestAgainst(
                tooMuchOutput,
                PythonRuntimeFixtures.capabilities,
                PythonProtocolVersion(1, 0),
                1,
            )
        }
        assertEquals(PythonRuntimeContractViolation.CAPABILITY_INCOMPATIBLE, error.violation)
    }

    @Test
    fun optionalTransportsRequireCapabilitiesAndZeroLimitsWhenDisabled() {
        val disabled = PythonRuntimeCapabilities(
            implementation = PythonImplementation.CPYTHON,
            pythonVersion = "3.13.9",
            supportedAbis = listOf("arm64-v8a"),
            supportsWorkspaceArchive = false,
            supportsStdinSnapshot = false,
            supportsStructuredTraceback = true,
            supportsCooperativeCancellation = false,
            cancellationMode = PythonCancellationMode.PROCESS_RESTART_ONLY,
            limits = PythonRuntimeFixtures.limits.copy(
                maxWorkspaceArchiveBytes = 0,
                maxWorkspaceEntries = 0,
                maxWorkspaceUncompressedBytes = 0,
                maxStdinBytes = 0,
            ),
        )
        PythonRuntimeValidation.validateCapabilities(disabled)
        assertFalse(disabled.supportsWorkspaceArchive)
        assertFalse(disabled.supportsHostCapabilitySnapshot)

        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateRequestAgainst(
                PythonRuntimeFixtures.request,
                disabled,
                PythonProtocolVersion(1, 0),
                3,
            )
        }
    }

    @Test
    fun hostCapabilitySnapshotRequiresProtocol11CapabilityAndBoundedExhaustiveDescriptor() {
        PythonRuntimeValidation.validateRequestAgainst(
            PythonRuntimeFixtures.r5Request,
            PythonRuntimeFixtures.r5Capabilities,
            PythonProtocolVersion(1, 1),
            4,
        )

        val protocol10 = PythonExecutionRequest(
            requestId = PythonRuntimeFixtures.requestId,
            protocolVersion = PythonProtocolVersion(1, 0),
            entryPoint = "main.py",
            source = PythonRuntimeFixtures.source,
            hostCapabilitySnapshot = PythonRuntimeFixtures.capabilitySnapshot.copy(descriptorIndex = 1),
        )
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateRequest(protocol10)
        }

        val disabled = PythonRuntimeCapabilities(
            implementation = PythonImplementation.CPYTHON,
            pythonVersion = "3.13.9",
            supportedAbis = listOf("arm64-v8a"),
            supportsWorkspaceArchive = true,
            supportsStdinSnapshot = true,
            supportsStructuredTraceback = true,
            supportsCooperativeCancellation = true,
            cancellationMode = PythonCancellationMode.COOPERATIVE_THEN_PROCESS_RESTART,
            limits = PythonRuntimeFixtures.limits,
        )
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateRequestAgainst(
                PythonRuntimeFixtures.r5Request,
                disabled,
                PythonProtocolVersion(1, 1),
                4,
            )
        }

        val tooLarge = PythonRuntimeFixtures.r5Request.let { request ->
            PythonExecutionRequest(
                requestId = request.requestId,
                protocolVersion = request.protocolVersion,
                entryPoint = request.entryPoint,
                source = request.source,
                hostCapabilitySnapshot = PythonRuntimeFixtures.capabilitySnapshot.copy(
                    descriptorIndex = 1,
                    declaredLengthBytes = PythonRuntimeContract.MAX_HOST_CAPABILITY_SNAPSHOT_BYTES + 1,
                ),
            )
        }
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateRequest(tooLarge)
        }
    }

    @Test
    fun pythonExceptionRequiresStructuredNonRetryableMetadata() {
        val missingMetadata = PythonExecutionError(
            PythonRuntimeFixtures.requestId,
            PythonErrorCode.PYTHON_EXCEPTION,
            PythonFailurePhase.EXECUTION,
            "failed",
            retryableBeforeDispatch = false,
        )
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateError(missingMetadata)
        }

        val valid = PythonExecutionError(
            PythonRuntimeFixtures.requestId,
            PythonErrorCode.PYTHON_EXCEPTION,
            PythonFailurePhase.EXECUTION,
            "failed",
            retryableBeforeDispatch = false,
            exceptionType = "RuntimeError",
            exceptionMessage = "",
            traceback = listOf(
                PythonTracebackFrame(PythonTracebackOrigin.PROJECT, "main.py", 1, "<module>"),
            ),
        )
        PythonRuntimeValidation.validateError(valid)
        assertTrue(valid.traceback.isNotEmpty())

        val absoluteFrame = PythonExecutionError(
            PythonRuntimeFixtures.requestId,
            PythonErrorCode.PYTHON_EXCEPTION,
            PythonFailurePhase.EXECUTION,
            "failed",
            retryableBeforeDispatch = false,
            exceptionType = "RuntimeError",
            exceptionMessage = "bad",
            traceback = listOf(
                PythonTracebackFrame(
                    PythonTracebackOrigin.PROJECT,
                    "/data/user/0/plugin/main.py",
                    1,
                    "<module>",
                ),
            ),
        )
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateError(absoluteFrame)
        }
    }

    @Test
    fun tracebackOriginsAreSanitizedAndExecutionErrorsCannotAuthorizeReplay() {
        val valid = PythonExecutionError(
            PythonRuntimeFixtures.requestId,
            PythonErrorCode.PYTHON_EXCEPTION,
            PythonFailurePhase.EXECUTION,
            "failed",
            retryableBeforeDispatch = false,
            exceptionType = "RuntimeError",
            exceptionMessage = "bad",
            traceback = listOf(
                PythonTracebackFrame(PythonTracebackOrigin.STDLIB, "asyncio/tasks.py", 1, "run"),
                PythonTracebackFrame(PythonTracebackOrigin.PACKAGE, "requests/api.py", 2, "get"),
                PythonTracebackFrame(PythonTracebackOrigin.GENERATED, "<stdin>", 3, "<module>"),
            ),
        )
        PythonRuntimeValidation.validateExecutionError(valid)

        val replayHint = PythonExecutionError(
            PythonRuntimeFixtures.requestId,
            PythonErrorCode.INTERNAL,
            PythonFailurePhase.EXECUTION,
            "failed",
            retryableBeforeDispatch = true,
        )
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateExecutionError(replayHint)
        }

        val admissionOnlyCode = PythonExecutionError(
            PythonRuntimeFixtures.requestId,
            PythonErrorCode.BUSY,
            PythonFailurePhase.EXECUTION,
            "busy",
            retryableBeforeDispatch = false,
        )
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateExecutionError(admissionOnlyCode)
        }
    }

    @Test
    fun admissionErrorsUseTheirFrozenPredispatchPhase() {
        PythonRuntimeValidation.validateAdmissionError(
            PythonExecutionError(
                PythonRuntimeFixtures.requestId,
                PythonErrorCode.UNSUPPORTED_PROTOCOL,
                PythonFailurePhase.NEGOTIATION,
                "unsupported",
                retryableBeforeDispatch = true,
            ),
        )
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateAdmissionError(
                PythonExecutionError(
                    PythonRuntimeFixtures.requestId,
                    PythonErrorCode.BUSY,
                    PythonFailurePhase.NEGOTIATION,
                    "busy",
                    retryableBeforeDispatch = true,
                ),
            )
        }
    }

    @Test
    fun executionErrorsUseDeterministicKindSpecificPhases() {
        PythonRuntimeValidation.validateExecutionError(
            PythonExecutionError(
                PythonRuntimeFixtures.requestId,
                PythonErrorCode.STDIN_REJECTED,
                PythonFailurePhase.INPUT_VALIDATION,
                "stdin digest mismatch",
                retryableBeforeDispatch = false,
            ),
        )
        PythonRuntimeValidation.validateExecutionError(
            PythonExecutionError(
                PythonRuntimeFixtures.requestId,
                PythonErrorCode.HOST_CAPABILITY_SNAPSHOT_REJECTED,
                PythonFailurePhase.INPUT_VALIDATION,
                "capability snapshot digest mismatch",
                retryableBeforeDispatch = false,
            ),
        )

        listOf(
            PythonErrorCode.SOURCE_REJECTED,
            PythonErrorCode.WORKSPACE_REJECTED,
            PythonErrorCode.STDIN_REJECTED,
            PythonErrorCode.HOST_CAPABILITY_SNAPSHOT_REJECTED,
        ).forEach { code ->
            assertThrows(PythonRuntimeContractException::class.java) {
                PythonRuntimeValidation.validateExecutionError(
                    PythonExecutionError(
                        PythonRuntimeFixtures.requestId,
                        code,
                        PythonFailurePhase.CLEANUP,
                        "wrong phase",
                        retryableBeforeDispatch = false,
                    ),
                )
            }
        }

        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateExecutionError(
                PythonExecutionError(
                    PythonRuntimeFixtures.requestId,
                    PythonErrorCode.OUTPUT_LIMIT_EXCEEDED,
                    PythonFailurePhase.INPUT_VALIDATION,
                    "wrong phase",
                    retryableBeforeDispatch = false,
                ),
            )
        }
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateExecutionError(
                PythonExecutionError(
                    PythonRuntimeFixtures.requestId,
                    PythonErrorCode.CANCELLATION_FAILED,
                    PythonFailurePhase.OUTPUT,
                    "wrong phase",
                    retryableBeforeDispatch = false,
                ),
            )
        }
    }

    @Test
    fun resultChunkCountsCannotExceedBytesOrTheLifetimeLimit() {
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateResult(
                PythonExecutionResult(PythonRuntimeFixtures.requestId, 0, 1, 1, 0, 2, 0),
            )
        }
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateResult(
                PythonExecutionResult(
                    PythonRuntimeFixtures.requestId,
                    0,
                    1,
                    PythonRuntimeContract.MAX_OUTPUT_CHUNKS,
                    1,
                    PythonRuntimeContract.MAX_OUTPUT_CHUNKS,
                    1,
                ),
            )
        }
    }

    @Test
    fun diagnosticsDoNotRenderOutputPayloadBytes() {
        val marker = "secret-output-marker"
        val chunk = PythonOutputChunk(
            PythonRuntimeFixtures.requestId,
            1,
            PythonOutputStream.STDERR,
            marker.toByteArray(),
        )
        assertFalse(chunk.toString().contains(marker))
        assertTrue(chunk.toString().contains("sizeBytes=${marker.length}"))
    }

    private fun requestWithPath(path: String) = PythonExecutionRequest(
        requestId = PythonRuntimeFixtures.requestId,
        protocolVersion = PythonProtocolVersion(1, 0),
        entryPoint = path,
        source = PythonRuntimeFixtures.source,
    )

    private fun assertDescriptorMismatch(block: () -> Unit) {
        val error = assertThrows(PythonRuntimeContractException::class.java, block)
        assertEquals(PythonRuntimeContractViolation.DESCRIPTOR_MISMATCH, error.violation)
    }
}
