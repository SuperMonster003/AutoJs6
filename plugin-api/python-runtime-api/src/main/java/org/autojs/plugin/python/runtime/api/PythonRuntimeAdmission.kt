package org.autojs.plugin.python.runtime.api

class PythonRuntimeAdmissionPlan internal constructor(
    val runtimeInfo: PythonRuntimeInfo,
    val capabilities: PythonRuntimeCapabilities,
    val hostVersionCode: Long,
    val deviceAbi: String,
    val negotiatedProtocolVersion: PythonProtocolVersion,
    val request: PythonExecutionRequest,
    val descriptorCount: Int,
    val maxOutstandingOutputCredits: Int,
    val pythonVersion: String,
    private val preparedSessionPolicy: PythonExecutionSessionPolicy,
) {
    private val sessionAdmissionPolicy = PythonSessionAdmissionPolicy(request.requestId)

    val admissionState: PythonSessionAdmissionState
        get() = sessionAdmissionPolicy.state

    val admissionRejection: PythonExecutionError?
        get() = sessionAdmissionPolicy.rejection

    val sessionPolicy: PythonExecutionSessionPolicy?
        get() = preparedSessionPolicy.takeIf {
            sessionAdmissionPolicy.state == PythonSessionAdmissionState.OPENED
        }

    /**
     * Returns true only for the winning admission callback. When false, the
     * Binder adapter must best-effort close the non-winning session handle.
     */
    fun onOpened(): Boolean {
        val accepted = sessionAdmissionPolicy.onOpened()
        if (!accepted) failClosedAfterProtocolViolation()
        return accepted
    }

    fun onRejected(error: PythonExecutionError): Boolean = try {
        val accepted = sessionAdmissionPolicy.onRejected(error)
        if (accepted) {
            preparedSessionPolicy.close()
        } else {
            failClosedAfterProtocolViolation()
        }
        accepted
    } catch (error: RuntimeException) {
        preparedSessionPolicy.close()
        throw error
    }

    /** Used by the Binder adapter for a null session, decode failure, or malformed callback. */
    fun failClosedAdmission(): Boolean {
        val changed = sessionAdmissionPolicy.failClosed()
        preparedSessionPolicy.close()
        return changed
    }

    /** Used by the host admission deadline or an explicit local close. */
    fun abandonAdmission(): Boolean {
        val changed = sessionAdmissionPolicy.abandon()
        preparedSessionPolicy.close()
        return changed
    }

    private fun failClosedAfterProtocolViolation() {
        sessionAdmissionPolicy.failClosed()
        preparedSessionPolicy.close()
    }
}

object PythonRuntimeAdmissionPlanner {
    fun create(
        runtimeInfo: PythonRuntimeInfo,
        capabilities: PythonRuntimeCapabilities,
        hostVersionCode: Long,
        deviceAbi: String,
        negotiatedProtocolVersion: PythonProtocolVersion,
        request: PythonExecutionRequest,
        descriptorCount: Int,
    ): PythonRuntimeAdmissionPlan {
        PythonRuntimeValidation.validateCapabilitiesAgainstInfo(capabilities, runtimeInfo)
        requireValidHostVersionCode(hostVersionCode)
        requireHostVersionCompatible(runtimeInfo, hostVersionCode)
        requireDeviceAbiSupported(capabilities, deviceAbi)

        val expectedProtocolVersion = PythonRuntimeNegotiation.requireNegotiated(runtimeInfo)
        requireProtocol(
            negotiatedProtocolVersion == expectedProtocolVersion,
            "Negotiated protocol version does not match the highest compatible version",
        )
        PythonRuntimeValidation.validateRequestAgainst(
            request = request,
            capabilities = capabilities,
            negotiatedVersion = negotiatedProtocolVersion,
            descriptorCount = descriptorCount,
        )

        val maxOutstandingOutputCredits = capabilities.limits.maxOutstandingOutputCredits
        val pythonVersion = capabilities.pythonVersion
        return PythonRuntimeAdmissionPlan(
            runtimeInfo = runtimeInfo,
            capabilities = capabilities,
            hostVersionCode = hostVersionCode,
            deviceAbi = deviceAbi,
            negotiatedProtocolVersion = negotiatedProtocolVersion,
            request = request,
            descriptorCount = descriptorCount,
            maxOutstandingOutputCredits = maxOutstandingOutputCredits,
            pythonVersion = pythonVersion,
            preparedSessionPolicy = PythonExecutionSessionPolicy(
                expectedRequest = request,
                maximumCredits = maxOutstandingOutputCredits,
                expectedPythonVersion = pythonVersion,
            ),
        )
    }

    private fun requireValidHostVersionCode(hostVersionCode: Long) {
        if (hostVersionCode <= 0L) {
            throw PythonRuntimeContractException(
                PythonRuntimeContractViolation.INVALID_VALUE,
                "Host version code must be positive",
            )
        }
    }

    private fun requireHostVersionCompatible(runtimeInfo: PythonRuntimeInfo, hostVersionCode: Long) {
        runtimeInfo.minHostVersionCode?.let { minimum ->
            requireCapability(hostVersionCode >= minimum, "Host version is below the provider minimum")
        }
        runtimeInfo.maxHostVersionCode?.let { maximum ->
            requireCapability(hostVersionCode <= maximum, "Host version exceeds the provider maximum")
        }
    }

    private fun requireDeviceAbiSupported(capabilities: PythonRuntimeCapabilities, deviceAbi: String) {
        requireCapability(deviceAbi in capabilities.supportedAbis, "Provider does not support the device ABI")
    }

    private fun requireProtocol(condition: Boolean, message: String) {
        if (!condition) {
            throw PythonRuntimeContractException(
                PythonRuntimeContractViolation.PROTOCOL_INCOMPATIBLE,
                message,
            )
        }
    }

    private fun requireCapability(condition: Boolean, message: String) {
        if (!condition) {
            throw PythonRuntimeContractException(
                PythonRuntimeContractViolation.CAPABILITY_INCOMPATIBLE,
                message,
            )
        }
    }
}

enum class PythonSessionAdmissionState {
    PENDING,
    OPENED,
    REJECTED,
    FAILED_CLOSED,
    ABANDONED,
}

private class PythonSessionAdmissionPolicy(
    private val expectedRequestId: PythonRequestId,
) {
    @get:Synchronized
    var state: PythonSessionAdmissionState = PythonSessionAdmissionState.PENDING
        private set

    @get:Synchronized
    var rejection: PythonExecutionError? = null
        private set

    @Synchronized
    fun onOpened(): Boolean {
        if (state != PythonSessionAdmissionState.PENDING) return false
        state = PythonSessionAdmissionState.OPENED
        return true
    }

    @Synchronized
    fun onRejected(error: PythonExecutionError): Boolean {
        if (state != PythonSessionAdmissionState.PENDING) return false
        try {
            PythonRuntimeValidation.validateAdmissionError(error)
            if (error.requestId != expectedRequestId) {
                throw PythonRuntimeContractException(
                    PythonRuntimeContractViolation.INVALID_VALUE,
                    "Admission rejection request ID does not match",
                )
            }
        } catch (failure: RuntimeException) {
            state = PythonSessionAdmissionState.FAILED_CLOSED
            throw failure
        }
        rejection = error
        state = PythonSessionAdmissionState.REJECTED
        return true
    }

    @Synchronized
    fun failClosed(): Boolean {
        if (
            state == PythonSessionAdmissionState.FAILED_CLOSED ||
            state == PythonSessionAdmissionState.ABANDONED ||
            state == PythonSessionAdmissionState.REJECTED
        ) return false
        state = PythonSessionAdmissionState.FAILED_CLOSED
        return true
    }

    @Synchronized
    fun abandon(): Boolean {
        if (
            state != PythonSessionAdmissionState.PENDING &&
            state != PythonSessionAdmissionState.OPENED
        ) return false
        state = PythonSessionAdmissionState.ABANDONED
        return true
    }
}
