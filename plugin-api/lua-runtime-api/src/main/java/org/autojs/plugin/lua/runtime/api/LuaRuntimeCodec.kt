package org.autojs.plugin.lua.runtime.api

import org.autojs.plugin.protocol.wire.TaggedWireDocument
import org.autojs.plugin.protocol.wire.TaggedWireWriter

object LuaRuntimeCodec {
    fun encodeRuntimeInfo(value: LuaRuntimeInfo): ByteArray {
        LuaRuntimeValidation.validateRuntimeInfo(value)
        return writer(LuaRuntimeContract.SCHEMA_RUNTIME_INFO)
            .int32(RuntimeInfoTags.PROTOCOL_MIN_MAJOR, value.protocolMin.major, requiredForReader = true)
            .int32(RuntimeInfoTags.PROTOCOL_MIN_MINOR, value.protocolMin.minor, requiredForReader = true)
            .int32(RuntimeInfoTags.PROTOCOL_MAX_MAJOR, value.protocolMax.major, requiredForReader = true)
            .int32(RuntimeInfoTags.PROTOCOL_MAX_MINOR, value.protocolMax.minor, requiredForReader = true)
            .string(RuntimeInfoTags.PROVIDER_ID, value.providerId, requiredForReader = true)
            .string(RuntimeInfoTags.PROVIDER_VERSION_NAME, value.providerVersionName, requiredForReader = true)
            .int64(RuntimeInfoTags.PROVIDER_VERSION_CODE, value.providerVersionCode, requiredForReader = true)
            .int32(RuntimeInfoTags.RUNTIME_FAMILY, value.runtimeFamily.wireCode, requiredForReader = true)
            .string(RuntimeInfoTags.RUNTIME_SLOT, value.runtimeSlot, requiredForReader = true)
            .string(RuntimeInfoTags.LANGUAGE_VERSION, value.languageVersion, requiredForReader = true)
            .string(RuntimeInfoTags.PROCESS_ABI, value.processAbi, requiredForReader = true)
            .apply {
                sortedUtf8(value.supportedAbis).forEach { string(RuntimeInfoTags.SUPPORTED_ABI, it) }
                sortedUtf8(value.capabilities).forEach { string(RuntimeInfoTags.CAPABILITY, it) }
            }
            .int64(RuntimeInfoTags.MAX_SOURCE_BYTES, value.limits.maxSourceBytes, requiredForReader = true)
            .int64(RuntimeInfoTags.MAX_MEMORY_BYTES, value.limits.maxMemoryBytes, requiredForReader = true)
            .int64(RuntimeInfoTags.MAX_OUTPUT_BYTES, value.limits.maxOutputBytes, requiredForReader = true)
            .int64(RuntimeInfoTags.MAX_EXECUTION_MILLIS, value.limits.maxExecutionMillis, requiredForReader = true)
            .int32(
                RuntimeInfoTags.MAX_CONCURRENT_EXECUTIONS,
                value.limits.maxConcurrentExecutions,
                requiredForReader = true,
            )
            .apply {
                value.minHostVersionCode?.let { int64(RuntimeInfoTags.MIN_HOST_VERSION_CODE, it) }
                value.maxHostVersionCode?.let { int64(RuntimeInfoTags.MAX_HOST_VERSION_CODE, it) }
            }
            .encode()
    }

    fun decodeRuntimeInfo(bytes: ByteArray): LuaRuntimeInfo {
        val document = decode(
            bytes,
            LuaRuntimeContract.SCHEMA_RUNTIME_INFO,
            RuntimeInfoTags.ALL,
            RuntimeInfoTags.REPEATED,
        )
        return LuaRuntimeInfo(
            protocolMin = LuaProtocolVersion(
                document.requireInt32(RuntimeInfoTags.PROTOCOL_MIN_MAJOR),
                document.requireInt32(RuntimeInfoTags.PROTOCOL_MIN_MINOR),
            ),
            protocolMax = LuaProtocolVersion(
                document.requireInt32(RuntimeInfoTags.PROTOCOL_MAX_MAJOR),
                document.requireInt32(RuntimeInfoTags.PROTOCOL_MAX_MINOR),
            ),
            providerId = document.requireString(RuntimeInfoTags.PROVIDER_ID),
            providerVersionName = document.requireString(RuntimeInfoTags.PROVIDER_VERSION_NAME),
            providerVersionCode = document.requireInt64(RuntimeInfoTags.PROVIDER_VERSION_CODE),
            runtimeFamily = enumByCode(
                document.requireInt32(RuntimeInfoTags.RUNTIME_FAMILY),
                LuaRuntimeFamily.values(),
                "runtime family",
            ),
            runtimeSlot = document.requireString(RuntimeInfoTags.RUNTIME_SLOT),
            languageVersion = document.requireString(RuntimeInfoTags.LANGUAGE_VERSION),
            processAbi = document.requireString(RuntimeInfoTags.PROCESS_ABI),
            supportedAbis = document.strings(RuntimeInfoTags.SUPPORTED_ABI),
            capabilities = document.strings(RuntimeInfoTags.CAPABILITY),
            limits = LuaRuntimeLimits(
                maxSourceBytes = document.requireInt64(RuntimeInfoTags.MAX_SOURCE_BYTES),
                maxMemoryBytes = document.requireInt64(RuntimeInfoTags.MAX_MEMORY_BYTES),
                maxOutputBytes = document.requireInt64(RuntimeInfoTags.MAX_OUTPUT_BYTES),
                maxExecutionMillis = document.requireInt64(RuntimeInfoTags.MAX_EXECUTION_MILLIS),
                maxConcurrentExecutions = document.requireInt32(RuntimeInfoTags.MAX_CONCURRENT_EXECUTIONS),
            ),
            minHostVersionCode = document.optionalInt64(RuntimeInfoTags.MIN_HOST_VERSION_CODE),
            maxHostVersionCode = document.optionalInt64(RuntimeInfoTags.MAX_HOST_VERSION_CODE),
        ).also(LuaRuntimeValidation::validateRuntimeInfo)
    }

    fun encodeExecutionRequest(value: LuaExecutionRequest): ByteArray {
        LuaRuntimeValidation.validateRequest(value)
        return writer(LuaRuntimeContract.SCHEMA_EXECUTION_REQUEST)
            .bytes(RequestTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int32(RequestTags.PROTOCOL_MAJOR, value.protocolVersion.major, requiredForReader = true)
            .int32(RequestTags.PROTOCOL_MINOR, value.protocolVersion.minor, requiredForReader = true)
            .string(RequestTags.SOURCE_NAME, value.sourceName, requiredForReader = true)
            .int64(RequestTags.SOURCE_LENGTH_BYTES, value.sourceLengthBytes, requiredForReader = true)
            .bytes(RequestTags.SOURCE_SHA256, value.sourceSha256.toByteArray(), requiredForReader = true)
            .document(RequestTags.ARGUMENTS, LuaValueCodec.encode(value.arguments), requiredForReader = true)
            .apply {
                sortedUtf8(value.requiredCapabilities).forEach { string(RequestTags.REQUIRED_CAPABILITY, it) }
            }
            .int64(RequestTags.OUTPUT_BYTE_LIMIT, value.outputByteLimit, requiredForReader = true)
            .int64(RequestTags.MEMORY_BYTE_LIMIT, value.memoryByteLimit, requiredForReader = true)
            .int64(RequestTags.TIMEOUT_MILLIS, value.timeoutMillis, requiredForReader = true)
            .encode()
    }

    fun decodeExecutionRequest(bytes: ByteArray): LuaExecutionRequest {
        val document = decode(bytes, LuaRuntimeContract.SCHEMA_EXECUTION_REQUEST, RequestTags.ALL, RequestTags.REPEATED)
        return LuaExecutionRequest(
            requestId = LuaRequestId.fromBytes(document.requireBytes(RequestTags.REQUEST_ID)),
            protocolVersion = LuaProtocolVersion(
                document.requireInt32(RequestTags.PROTOCOL_MAJOR),
                document.requireInt32(RequestTags.PROTOCOL_MINOR),
            ),
            sourceName = document.requireString(RequestTags.SOURCE_NAME),
            sourceLengthBytes = document.requireInt64(RequestTags.SOURCE_LENGTH_BYTES),
            sourceSha256 = LuaSha256.fromBytes(document.requireBytes(RequestTags.SOURCE_SHA256)),
            arguments = LuaValueCodec.decode(document.requireDocument(RequestTags.ARGUMENTS)),
            requiredCapabilities = document.strings(RequestTags.REQUIRED_CAPABILITY),
            outputByteLimit = document.requireInt64(RequestTags.OUTPUT_BYTE_LIMIT),
            memoryByteLimit = document.requireInt64(RequestTags.MEMORY_BYTE_LIMIT),
            timeoutMillis = document.requireInt64(RequestTags.TIMEOUT_MILLIS),
        ).also(LuaRuntimeValidation::validateRequest)
    }

    fun encodeStarted(value: LuaExecutionStarted): ByteArray {
        LuaRuntimeValidation.validateStarted(value)
        return writer(LuaRuntimeContract.SCHEMA_EXECUTION_STARTED)
            .bytes(StartedTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int32(StartedTags.PROTOCOL_MAJOR, value.protocolVersion.major, requiredForReader = true)
            .int32(StartedTags.PROTOCOL_MINOR, value.protocolVersion.minor, requiredForReader = true)
            .string(StartedTags.RUNTIME_SLOT, value.runtimeSlot, requiredForReader = true)
            .string(StartedTags.LANGUAGE_VERSION, value.languageVersion, requiredForReader = true)
            .int64(StartedTags.FIRST_OUTPUT_SEQUENCE, value.firstOutputSequence, requiredForReader = true)
            .int64(StartedTags.QUEUE_ELAPSED_MILLIS, value.queueElapsedMillis, requiredForReader = true)
            .encode()
    }

    fun decodeStarted(bytes: ByteArray): LuaExecutionStarted {
        val document = decode(bytes, LuaRuntimeContract.SCHEMA_EXECUTION_STARTED, StartedTags.ALL)
        return LuaExecutionStarted(
            requestId = LuaRequestId.fromBytes(document.requireBytes(StartedTags.REQUEST_ID)),
            protocolVersion = LuaProtocolVersion(
                document.requireInt32(StartedTags.PROTOCOL_MAJOR),
                document.requireInt32(StartedTags.PROTOCOL_MINOR),
            ),
            runtimeSlot = document.requireString(StartedTags.RUNTIME_SLOT),
            languageVersion = document.requireString(StartedTags.LANGUAGE_VERSION),
            firstOutputSequence = document.requireInt64(StartedTags.FIRST_OUTPUT_SEQUENCE),
            queueElapsedMillis = document.requireInt64(StartedTags.QUEUE_ELAPSED_MILLIS),
        ).also(LuaRuntimeValidation::validateStarted)
    }

    fun encodeOutput(value: LuaOutputChunk): ByteArray {
        LuaRuntimeValidation.validateOutput(value)
        return writer(LuaRuntimeContract.SCHEMA_OUTPUT)
            .bytes(OutputTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int64(OutputTags.SEQUENCE, value.sequence, requiredForReader = true)
            .int32(OutputTags.STREAM, value.stream.wireCode, requiredForReader = true)
            .string(OutputTags.TEXT, value.text, requiredForReader = true)
            .encode()
    }

    fun decodeOutput(bytes: ByteArray): LuaOutputChunk {
        val document = decode(bytes, LuaRuntimeContract.SCHEMA_OUTPUT, OutputTags.ALL)
        return LuaOutputChunk(
            requestId = LuaRequestId.fromBytes(document.requireBytes(OutputTags.REQUEST_ID)),
            sequence = document.requireInt64(OutputTags.SEQUENCE),
            stream = enumByCode(document.requireInt32(OutputTags.STREAM), LuaOutputStream.values(), "output stream"),
            text = document.requireString(OutputTags.TEXT),
        ).also(LuaRuntimeValidation::validateOutput)
    }

    fun encodeResult(value: LuaExecutionResult): ByteArray {
        LuaRuntimeValidation.validateResult(value)
        return writer(LuaRuntimeContract.SCHEMA_RESULT)
            .bytes(ResultTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .document(ResultTags.VALUE, LuaValueCodec.encode(value.value), requiredForReader = true)
            .int64(ResultTags.ELAPSED_MILLIS, value.elapsedMillis, requiredForReader = true)
            .encode()
    }

    fun decodeResult(bytes: ByteArray): LuaExecutionResult {
        val document = decode(bytes, LuaRuntimeContract.SCHEMA_RESULT, ResultTags.ALL)
        return LuaExecutionResult(
            requestId = LuaRequestId.fromBytes(document.requireBytes(ResultTags.REQUEST_ID)),
            value = LuaValueCodec.decode(document.requireDocument(ResultTags.VALUE)),
            elapsedMillis = document.requireInt64(ResultTags.ELAPSED_MILLIS),
        ).also(LuaRuntimeValidation::validateResult)
    }

    fun encodeError(value: LuaExecutionError): ByteArray {
        LuaRuntimeValidation.validateError(value)
        return writer(LuaRuntimeContract.SCHEMA_ERROR)
            .bytes(ErrorTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int32(ErrorTags.CODE, value.code.wireCode, requiredForReader = true)
            .int32(ErrorTags.PHASE, value.phase.wireCode, requiredForReader = true)
            .string(ErrorTags.MESSAGE, value.message, requiredForReader = true)
            .int32(ErrorTags.RETRY_DISPOSITION, value.retryDisposition.wireCode, requiredForReader = true)
            .encode()
    }

    fun decodeError(bytes: ByteArray): LuaExecutionError {
        val document = decode(bytes, LuaRuntimeContract.SCHEMA_ERROR, ErrorTags.ALL)
        return LuaExecutionError(
            requestId = LuaRequestId.fromBytes(document.requireBytes(ErrorTags.REQUEST_ID)),
            code = enumByCode(document.requireInt32(ErrorTags.CODE), LuaExecutionErrorCode.values(), "error code"),
            phase = enumByCode(
                document.requireInt32(ErrorTags.PHASE),
                LuaExecutionFailurePhase.values(),
                "failure phase",
            ),
            message = document.requireString(ErrorTags.MESSAGE),
            retryDisposition = enumByCode(
                document.requireInt32(ErrorTags.RETRY_DISPOSITION),
                LuaRetryDisposition.values(),
                "retry disposition",
            ),
        ).also(LuaRuntimeValidation::validateError)
    }

    fun encodeCancellation(value: LuaExecutionCancellation): ByteArray {
        LuaRuntimeValidation.validateCancellation(value)
        return writer(LuaRuntimeContract.SCHEMA_CANCELLATION)
            .bytes(CancellationTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int32(CancellationTags.REASON, value.reason.wireCode, requiredForReader = true)
            .int64(CancellationTags.ELAPSED_MILLIS, value.elapsedMillis, requiredForReader = true)
            .encode()
    }

    fun decodeCancellation(bytes: ByteArray): LuaExecutionCancellation {
        val document = decode(bytes, LuaRuntimeContract.SCHEMA_CANCELLATION, CancellationTags.ALL)
        return LuaExecutionCancellation(
            requestId = LuaRequestId.fromBytes(document.requireBytes(CancellationTags.REQUEST_ID)),
            reason = enumByCode(
                document.requireInt32(CancellationTags.REASON),
                LuaCancellationReason.values(),
                "cancellation reason",
            ),
            elapsedMillis = document.requireInt64(CancellationTags.ELAPSED_MILLIS),
        ).also(LuaRuntimeValidation::validateCancellation)
    }

    fun encodeHostRequest(value: LuaHostCallRequest): ByteArray {
        LuaRuntimeValidation.validateHostRequest(value)
        return writer(LuaRuntimeContract.SCHEMA_HOST_REQUEST)
            .bytes(HostRequestTags.EXECUTION_ID, value.executionId.toByteArray(), requiredForReader = true)
            .string(HostRequestTags.CALL_ID, value.callId, requiredForReader = true)
            .string(HostRequestTags.CAPABILITY, value.capability, requiredForReader = true)
            .document(HostRequestTags.ARGUMENTS, LuaValueCodec.encode(value.arguments), requiredForReader = true)
            .encode()
    }

    fun decodeHostRequest(bytes: ByteArray): LuaHostCallRequest {
        val document = decode(bytes, LuaRuntimeContract.SCHEMA_HOST_REQUEST, HostRequestTags.ALL)
        return LuaHostCallRequest(
            executionId = LuaRequestId.fromBytes(document.requireBytes(HostRequestTags.EXECUTION_ID)),
            callId = document.requireString(HostRequestTags.CALL_ID),
            capability = document.requireString(HostRequestTags.CAPABILITY),
            arguments = LuaValueCodec.decode(document.requireDocument(HostRequestTags.ARGUMENTS)),
        ).also(LuaRuntimeValidation::validateHostRequest)
    }

    fun encodeHostResult(value: LuaHostCallResult): ByteArray {
        LuaRuntimeValidation.validateHostResult(value)
        return writer(LuaRuntimeContract.SCHEMA_HOST_RESULT)
            .bytes(HostResultTags.EXECUTION_ID, value.executionId.toByteArray(), requiredForReader = true)
            .string(HostResultTags.CALL_ID, value.callId, requiredForReader = true)
            .document(HostResultTags.VALUE, LuaValueCodec.encode(value.value), requiredForReader = true)
            .encode()
    }

    fun decodeHostResult(bytes: ByteArray): LuaHostCallResult {
        val document = decode(bytes, LuaRuntimeContract.SCHEMA_HOST_RESULT, HostResultTags.ALL)
        return LuaHostCallResult(
            executionId = LuaRequestId.fromBytes(document.requireBytes(HostResultTags.EXECUTION_ID)),
            callId = document.requireString(HostResultTags.CALL_ID),
            value = LuaValueCodec.decode(document.requireDocument(HostResultTags.VALUE)),
        ).also(LuaRuntimeValidation::validateHostResult)
    }

    fun encodeHostError(value: LuaHostCallError): ByteArray {
        LuaRuntimeValidation.validateHostError(value)
        return writer(LuaRuntimeContract.SCHEMA_HOST_ERROR)
            .bytes(HostErrorTags.EXECUTION_ID, value.executionId.toByteArray(), requiredForReader = true)
            .string(HostErrorTags.CALL_ID, value.callId, requiredForReader = true)
            .int32(HostErrorTags.CODE, value.code.wireCode, requiredForReader = true)
            .string(HostErrorTags.MESSAGE, value.message, requiredForReader = true)
            .encode()
    }

    fun decodeHostError(bytes: ByteArray): LuaHostCallError {
        val document = decode(bytes, LuaRuntimeContract.SCHEMA_HOST_ERROR, HostErrorTags.ALL)
        return LuaHostCallError(
            executionId = LuaRequestId.fromBytes(document.requireBytes(HostErrorTags.EXECUTION_ID)),
            callId = document.requireString(HostErrorTags.CALL_ID),
            code = enumByCode(document.requireInt32(HostErrorTags.CODE), LuaHostErrorCode.values(), "host error code"),
            message = document.requireString(HostErrorTags.MESSAGE),
        ).also(LuaRuntimeValidation::validateHostError)
    }

    private fun decode(
        bytes: ByteArray,
        schemaId: Int,
        knownTags: Set<Int>,
        repeatedTags: Set<Int> = emptySet(),
    ): TaggedWireDocument = TaggedWireDocument.decode(bytes)
        .requireSchema(schemaId, LuaRuntimeContract.SCHEMA_MAJOR)
        .rejectUnknownRequiredFields(knownTags)
        .validateKnownCardinality(knownTags, repeatedTags)

    private fun writer(schemaId: Int): TaggedWireWriter = TaggedWireWriter(
        schemaId,
        LuaRuntimeContract.SCHEMA_MAJOR,
        LuaRuntimeContract.SCHEMA_MINOR,
    )

    private fun <T> enumByCode(code: Int, values: Array<T>, label: String): T where T : Enum<T> {
        return values.firstOrNull { enum ->
            when (enum) {
                is LuaRuntimeFamily -> enum.wireCode
                is LuaOutputStream -> enum.wireCode
                is LuaExecutionErrorCode -> enum.wireCode
                is LuaExecutionFailurePhase -> enum.wireCode
                is LuaRetryDisposition -> enum.wireCode
                is LuaCancellationReason -> enum.wireCode
                is LuaHostErrorCode -> enum.wireCode
                else -> null
            } == code
        } ?: throw LuaContractException(LuaContractViolation.UNKNOWN_ENUM, "Unknown $label $code")
    }

    private fun sortedUtf8(values: Collection<String>): List<String> = values.sortedWith(::compareUtf8)

    private fun compareUtf8(left: String, right: String): Int {
        val a = left.toByteArray(Charsets.UTF_8)
        val b = right.toByteArray(Charsets.UTF_8)
        val shared = minOf(a.size, b.size)
        for (index in 0 until shared) {
            val comparison = (a[index].toInt() and 0xff).compareTo(b[index].toInt() and 0xff)
            if (comparison != 0) return comparison
        }
        return a.size.compareTo(b.size)
    }

    private object RuntimeInfoTags {
        const val PROTOCOL_MIN_MAJOR = 1
        const val PROTOCOL_MIN_MINOR = 2
        const val PROTOCOL_MAX_MAJOR = 3
        const val PROTOCOL_MAX_MINOR = 4
        const val PROVIDER_ID = 5
        const val PROVIDER_VERSION_NAME = 6
        const val PROVIDER_VERSION_CODE = 7
        const val RUNTIME_FAMILY = 8
        const val RUNTIME_SLOT = 9
        const val LANGUAGE_VERSION = 10
        const val SUPPORTED_ABI = 11
        const val CAPABILITY = 12
        const val MAX_SOURCE_BYTES = 13
        const val MAX_MEMORY_BYTES = 14
        const val MAX_OUTPUT_BYTES = 15
        const val MAX_EXECUTION_MILLIS = 16
        const val MAX_CONCURRENT_EXECUTIONS = 17
        const val MIN_HOST_VERSION_CODE = 18
        const val MAX_HOST_VERSION_CODE = 19
        const val PROCESS_ABI = 20
        val ALL = (1..20).toSet()
        val REPEATED = setOf(SUPPORTED_ABI, CAPABILITY)
    }

    private object RequestTags {
        const val REQUEST_ID = 1
        const val PROTOCOL_MAJOR = 2
        const val PROTOCOL_MINOR = 3
        const val SOURCE_NAME = 4
        const val SOURCE_LENGTH_BYTES = 5
        const val SOURCE_SHA256 = 6
        const val ARGUMENTS = 7
        const val REQUIRED_CAPABILITY = 8
        const val OUTPUT_BYTE_LIMIT = 9
        const val MEMORY_BYTE_LIMIT = 10
        const val TIMEOUT_MILLIS = 11
        val ALL = (1..11).toSet()
        val REPEATED = setOf(REQUIRED_CAPABILITY)
    }

    private object StartedTags {
        const val REQUEST_ID = 1
        const val PROTOCOL_MAJOR = 2
        const val PROTOCOL_MINOR = 3
        const val RUNTIME_SLOT = 4
        const val LANGUAGE_VERSION = 5
        const val FIRST_OUTPUT_SEQUENCE = 6
        const val QUEUE_ELAPSED_MILLIS = 7
        val ALL = (1..7).toSet()
    }

    private object OutputTags {
        const val REQUEST_ID = 1
        const val SEQUENCE = 2
        const val STREAM = 3
        const val TEXT = 4
        val ALL = (1..4).toSet()
    }

    private object ResultTags {
        const val REQUEST_ID = 1
        const val VALUE = 2
        const val ELAPSED_MILLIS = 3
        val ALL = (1..3).toSet()
    }

    private object ErrorTags {
        const val REQUEST_ID = 1
        const val CODE = 2
        const val PHASE = 3
        const val MESSAGE = 4
        const val RETRY_DISPOSITION = 5
        val ALL = (1..5).toSet()
    }

    private object CancellationTags {
        const val REQUEST_ID = 1
        const val REASON = 2
        const val ELAPSED_MILLIS = 3
        val ALL = (1..3).toSet()
    }

    private object HostRequestTags {
        const val EXECUTION_ID = 1
        const val CALL_ID = 2
        const val CAPABILITY = 3
        const val ARGUMENTS = 4
        val ALL = (1..4).toSet()
    }

    private object HostResultTags {
        const val EXECUTION_ID = 1
        const val CALL_ID = 2
        const val VALUE = 3
        val ALL = (1..3).toSet()
    }

    private object HostErrorTags {
        const val EXECUTION_ID = 1
        const val CALL_ID = 2
        const val CODE = 3
        const val MESSAGE = 4
        val ALL = (1..4).toSet()
    }
}
