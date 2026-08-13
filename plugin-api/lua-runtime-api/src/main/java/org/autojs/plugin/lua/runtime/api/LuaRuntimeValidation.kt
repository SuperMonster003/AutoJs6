package org.autojs.plugin.lua.runtime.api

import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

object LuaRuntimeNegotiation {
    val HOST_MIN = LuaProtocolVersion(LuaRuntimeContract.PROTOCOL_MAJOR, 0)
    val HOST_MAX = LuaProtocolVersion(
        LuaRuntimeContract.PROTOCOL_MAJOR,
        LuaRuntimeContract.PROTOCOL_MINOR,
    )

    fun negotiate(
        runtimeInfo: LuaRuntimeInfo,
        hostMin: LuaProtocolVersion = HOST_MIN,
        hostMax: LuaProtocolVersion = HOST_MAX,
    ): LuaProtocolVersion? {
        LuaRuntimeValidation.validateProtocolRange(hostMin, hostMax, "host")
        LuaRuntimeValidation.validateRuntimeInfo(runtimeInfo)
        val lower = maxOf(hostMin, runtimeInfo.protocolMin)
        val upper = minOf(hostMax, runtimeInfo.protocolMax)
        return upper.takeIf { lower <= upper }
    }

    fun requireNegotiated(
        runtimeInfo: LuaRuntimeInfo,
        hostMin: LuaProtocolVersion = HOST_MIN,
        hostMax: LuaProtocolVersion = HOST_MAX,
    ): LuaProtocolVersion = negotiate(runtimeInfo, hostMin, hostMax)
        ?: throw LuaContractException(
            LuaContractViolation.PROTOCOL_INCOMPATIBLE,
            "Host and Lua provider protocol ranges do not overlap",
        )
}

object LuaRuntimeValidation {
    private val PROVIDER_ID = Regex("[a-z0-9][a-z0-9._-]{0,127}")
    private val RUNTIME_SLOT = Regex("[a-z][a-z0-9._-]{0,63}")
    private val ABI = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    private val CAPABILITY = Regex("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*")
    private val CALL_ID = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

    fun validateRuntimeInfo(info: LuaRuntimeInfo) {
        validateProtocolRange(info.protocolMin, info.protocolMax, "provider")
        requireValue(PROVIDER_ID.matches(info.providerId)) { "Provider ID is invalid" }
        requireUtf8Text(info.providerId, "Provider ID", LuaRuntimeContract.MAX_PROVIDER_ID_BYTES)
        validateVersionText(
            info.providerVersionName,
            "Provider version name",
        )
        requireValue(info.providerVersionCode > 0L) { "Provider version code must be positive" }
        requireValue(info.runtimeFamily == LuaRuntimeFamily.PUC_LUA) {
            "Lua protocol v1 supports the PUC Lua runtime family only"
        }
        requireValue(RUNTIME_SLOT.matches(info.runtimeSlot)) { "Runtime slot is invalid" }
        requireUtf8Text(info.runtimeSlot, "Runtime slot", LuaRuntimeContract.MAX_RUNTIME_SLOT_BYTES)
        validateVersionText(
            info.languageVersion,
            "Language version",
        )
        requireValue(info.supportedAbis.isNotEmpty()) { "A native PUC Lua provider must declare packaged ABIs" }
        requireValue(info.supportedAbis.size <= LuaRuntimeContract.MAX_SUPPORTED_ABIS) {
            "Supported ABI count exceeds the protocol limit"
        }
        requireDistinct(info.supportedAbis, "supported ABIs")
        info.supportedAbis.forEachIndexed { index, abi ->
            requireValue(ABI.matches(abi)) { "Supported ABI $index is invalid" }
            requireUtf8Text(abi, "Supported ABI $index", LuaRuntimeContract.MAX_ABI_BYTES)
        }
        requireValue(ABI.matches(info.processAbi)) { "Process ABI is invalid" }
        requireUtf8Text(info.processAbi, "Process ABI", LuaRuntimeContract.MAX_ABI_BYTES)
        requireValue(info.processAbi in info.supportedAbis) { "Process ABI is not in the packaged ABI inventory" }
        validateCapabilities(info.capabilities, "provider capabilities")
        validateLimits(info.limits)
        info.minHostVersionCode?.let {
            requireValue(it > 0L) { "Minimum host version code must be positive" }
        }
        info.maxHostVersionCode?.let {
            requireValue(it > 0L) { "Maximum host version code must be positive" }
        }
        if (info.minHostVersionCode != null && info.maxHostVersionCode != null) {
            requireValue(info.minHostVersionCode <= info.maxHostVersionCode) {
                "Minimum host version code exceeds maximum host version code"
            }
        }
    }

    fun validateRequest(request: LuaExecutionRequest) {
        validateProtocolVersion(request.protocolVersion, "request")
        validateSourceName(request.sourceName)
        requireValue(request.sourceLengthBytes in 0L..LuaRuntimeContract.MAX_SOURCE_BYTES) {
            "Source length is invalid"
        }
        LuaValueValidation.validate(request.arguments)
        validateCapabilities(request.requiredCapabilities, "required capabilities")
        requireValue(request.outputByteLimit in 1L..LuaRuntimeContract.MAX_OUTPUT_BYTES) {
            "Output byte limit is invalid"
        }
        requireValue(request.memoryByteLimit in 1L..LuaRuntimeContract.MAX_MEMORY_BYTES) {
            "Memory byte limit is invalid"
        }
        requireValue(request.timeoutMillis in 1L..LuaRuntimeContract.MAX_TIMEOUT_MILLIS) {
            "Execution timeout is invalid"
        }
    }

    fun validateRequestAgainst(
        request: LuaExecutionRequest,
        runtimeInfo: LuaRuntimeInfo,
        negotiatedVersion: LuaProtocolVersion,
    ) {
        validateRequest(request)
        validateRuntimeInfo(runtimeInfo)
        validateProtocolVersion(negotiatedVersion, "negotiated")
        requireCapability(
            negotiatedVersion.major == LuaRuntimeContract.PROTOCOL_MAJOR &&
                negotiatedVersion.minor in 0..LuaRuntimeContract.PROTOCOL_MINOR,
        ) { "Negotiated protocol version is unsupported by this SDK" }
        requireCapability(negotiatedVersion in runtimeInfo.protocolMin..runtimeInfo.protocolMax) {
            "Negotiated protocol version is outside the provider range"
        }
        requireCapability(request.protocolVersion == negotiatedVersion) {
            "Request protocol version was not negotiated"
        }
        requireCapability(request.sourceLengthBytes <= runtimeInfo.limits.maxSourceBytes) {
            "Source exceeds the provider limit"
        }
        requireCapability(request.outputByteLimit <= runtimeInfo.limits.maxOutputBytes) {
            "Output limit exceeds the provider limit"
        }
        requireCapability(request.memoryByteLimit <= runtimeInfo.limits.maxMemoryBytes) {
            "Memory limit exceeds the provider limit"
        }
        requireCapability(request.timeoutMillis <= runtimeInfo.limits.maxExecutionMillis) {
            "Timeout exceeds the provider limit"
        }
        requireCapability(runtimeInfo.capabilities.containsAll(request.requiredCapabilities)) {
            "Provider does not support every required capability"
        }
    }

    fun validateStarted(started: LuaExecutionStarted) {
        validateProtocolVersion(started.protocolVersion, "started")
        requireValue(RUNTIME_SLOT.matches(started.runtimeSlot)) { "Started runtime slot is invalid" }
        validateVersionText(
            started.languageVersion,
            "Started language version",
        )
        requireValue(started.firstOutputSequence >= 0L) { "First output sequence must not be negative" }
        requireValue(started.queueElapsedMillis >= 0L) { "Queue elapsed time must not be negative" }
    }

    fun validateOutput(output: LuaOutputChunk) {
        requireValue(output.sequence >= 0L) { "Output sequence must not be negative" }
        requireValue(output.text.isNotEmpty()) { "Output chunks must not be empty" }
        requireUtf8Text(
            output.text,
            "Output chunk",
            LuaRuntimeContract.MAX_OUTPUT_CHUNK_BYTES,
        )
    }

    fun validateResult(result: LuaExecutionResult) {
        LuaValueValidation.validate(result.value)
        requireValue(result.elapsedMillis >= 0L) { "Result elapsed time must not be negative" }
    }

    fun validateError(error: LuaExecutionError) {
        validateMessage(error.message, "Execution error")
    }

    fun validateCancellation(cancellation: LuaExecutionCancellation) {
        requireValue(cancellation.elapsedMillis >= 0L) { "Cancellation elapsed time must not be negative" }
    }

    fun validateHostRequest(request: LuaHostCallRequest) {
        validateCallId(request.callId)
        validateCapability(request.capability, "Host capability")
        LuaValueValidation.validate(request.arguments)
    }

    fun validateHostResult(result: LuaHostCallResult) {
        validateCallId(result.callId)
        LuaValueValidation.validate(result.value)
    }

    fun validateHostError(error: LuaHostCallError) {
        validateCallId(error.callId)
        validateMessage(error.message, "Host capability error")
    }

    fun validateProtocolRange(min: LuaProtocolVersion, max: LuaProtocolVersion, label: String) {
        validateProtocolVersion(min, "$label minimum")
        validateProtocolVersion(max, "$label maximum")
        requireValue(min <= max) { "$label protocol range is reversed" }
    }

    fun validateProtocolVersion(version: LuaProtocolVersion, label: String) {
        requireValue(version.major > 0 && version.minor >= 0) { "$label protocol version is invalid" }
    }

    /** V1.0 reserves descriptor arrays for compatible minor-version expansion. */
    fun validateV1DescriptorCount(descriptorCount: Int) {
        requireValue(descriptorCount == 0) { "Lua protocol v1.0 does not define result descriptors" }
    }

    private fun validateLimits(limits: LuaRuntimeLimits) {
        requireValue(limits.maxSourceBytes in 1L..LuaRuntimeContract.MAX_SOURCE_BYTES) {
            "Provider source limit is invalid"
        }
        requireValue(limits.maxMemoryBytes in 1L..LuaRuntimeContract.MAX_MEMORY_BYTES) {
            "Provider memory limit is invalid"
        }
        requireValue(limits.maxOutputBytes in 1L..LuaRuntimeContract.MAX_OUTPUT_BYTES) {
            "Provider output limit is invalid"
        }
        requireValue(limits.maxExecutionMillis in 1L..LuaRuntimeContract.MAX_TIMEOUT_MILLIS) {
            "Provider execution-time limit is invalid"
        }
        requireValue(limits.maxConcurrentExecutions in 1..LuaRuntimeContract.MAX_CONCURRENT_EXECUTIONS) {
            "Provider concurrency limit is invalid"
        }
    }

    private fun validateCapabilities(values: Collection<String>, label: String) {
        requireValue(values.size <= LuaRuntimeContract.MAX_CAPABILITIES) { "$label exceed the protocol limit" }
        requireDistinct(values, label)
        values.forEachIndexed { index, value -> validateCapability(value, "$label entry $index") }
    }

    private fun validateCapability(value: String, label: String) {
        requireValue(CAPABILITY.matches(value)) { "$label is invalid" }
        requireUtf8Text(value, label, LuaRuntimeContract.MAX_CAPABILITY_NAME_BYTES)
    }

    private fun validateCallId(value: String) {
        requireValue(CALL_ID.matches(value)) { "Host call ID is invalid" }
        requireUtf8Text(value, "Host call ID", LuaRuntimeContract.MAX_CALL_ID_BYTES)
    }

    private fun validateSourceName(value: String) {
        requireValue(value.isNotBlank()) { "Source name must not be blank" }
        requireUtf8Text(value, "Source name", LuaRuntimeContract.MAX_SOURCE_NAME_BYTES)
        requireValue('\u0000' !in value && '\\' !in value && ':' !in value) {
            "Source name must be a logical label, not a host path or URI"
        }
        requireValue(!value.startsWith('/') && value.split('/').none { it == ".." }) {
            "Source name must not be an absolute or parent-traversing path"
        }
    }

    private fun validateMessage(value: String, label: String) {
        requireValue(value.isNotBlank()) { "$label message must not be blank" }
        requireValue(value.codePointCount(0, value.length) <= LuaRuntimeContract.MAX_ERROR_MESSAGE_CODE_POINTS) {
            "$label message is too long"
        }
        requireUtf8Text(value, "$label message", LuaRuntimeContract.MAX_METADATA_BYTES)
    }

    private fun validateVersionText(value: String, label: String) {
        requireValue(value.isNotBlank()) { "$label must not be blank" }
        requireValue(value.none { Character.isISOControl(it.code) }) { "$label contains control characters" }
        requireUtf8Text(value, label, LuaRuntimeContract.MAX_VERSION_TEXT_BYTES)
    }

    private fun requireDistinct(values: Collection<String>, label: String) {
        requireValue(values.toSet().size == values.size) { "$label contain duplicates" }
    }

    private fun requireUtf8Text(value: String, label: String, maximumBytes: Int) {
        val size = try {
            Charsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value))
                .remaining()
        } catch (e: Exception) {
            throw LuaContractException(LuaContractViolation.INVALID_VALUE, "$label is not valid UTF-8 text", e)
        }
        requireValue(size <= maximumBytes) { "$label exceeds $maximumBytes bytes" }
    }

    private inline fun requireValue(condition: Boolean, message: () -> String) {
        if (!condition) throw LuaContractException(LuaContractViolation.INVALID_VALUE, message())
    }

    private inline fun requireCapability(condition: Boolean, message: () -> String) {
        if (!condition) throw LuaContractException(LuaContractViolation.CAPABILITY_INCOMPATIBLE, message())
    }
}
