package org.autojs.plugin.python.runtime.api

import org.autojs.plugin.protocol.wire.TaggedWireDocument
import org.autojs.plugin.protocol.wire.TaggedWireLimits
import org.autojs.plugin.protocol.wire.TaggedWireWriter

object PythonRuntimeCodec {
    private val METADATA_LIMITS = TaggedWireLimits(
        maxDocumentBytes = PythonRuntimeContract.MAX_METADATA_BYTES,
        maxFieldBytes = 64 * 1024,
        maxFields = 1_024,
    )
    private val CHUNK_LIMITS = TaggedWireLimits(
        maxDocumentBytes = 64 * 1024,
        maxFieldBytes = PythonRuntimeContract.MAX_OUTPUT_CHUNK_BYTES,
        maxFields = 16,
    )

    fun encodeRuntimeInfo(value: PythonRuntimeInfo): ByteArray {
        PythonRuntimeValidation.validateRuntimeInfo(value)
        return writer(PythonRuntimeContract.SCHEMA_RUNTIME_INFO)
            .int32(RuntimeInfoTags.PROTOCOL_MIN_MAJOR, value.protocolMin.major)
            .int32(RuntimeInfoTags.PROTOCOL_MIN_MINOR, value.protocolMin.minor)
            .int32(RuntimeInfoTags.PROTOCOL_MAX_MAJOR, value.protocolMax.major)
            .int32(RuntimeInfoTags.PROTOCOL_MAX_MINOR, value.protocolMax.minor)
            .string(RuntimeInfoTags.PROVIDER_ID, value.providerId)
            .string(RuntimeInfoTags.PROVIDER_VERSION_NAME, value.providerVersionName)
            .int64(RuntimeInfoTags.PROVIDER_VERSION_CODE, value.providerVersionCode)
            .int32(RuntimeInfoTags.IMPLEMENTATION, value.implementation.wireCode)
            .string(RuntimeInfoTags.PYTHON_VERSION, value.pythonVersion)
            .int32(RuntimeInfoTags.ISOLATION_MODE, value.isolationMode.wireCode)
            .apply {
                value.minHostVersionCode?.let { int64(RuntimeInfoTags.MIN_HOST_VERSION_CODE, it) }
                value.maxHostVersionCode?.let { int64(RuntimeInfoTags.MAX_HOST_VERSION_CODE, it) }
            }
            .encode()
    }

    fun decodeRuntimeInfo(bytes: ByteArray): PythonRuntimeInfo =
        document(bytes, PythonRuntimeContract.SCHEMA_RUNTIME_INFO, RuntimeInfoTags.ALL).let { document ->
            PythonRuntimeInfo(
                protocolMin = PythonProtocolVersion(
                    document.requireInt32(RuntimeInfoTags.PROTOCOL_MIN_MAJOR),
                    document.requireInt32(RuntimeInfoTags.PROTOCOL_MIN_MINOR),
                ),
                protocolMax = PythonProtocolVersion(
                    document.requireInt32(RuntimeInfoTags.PROTOCOL_MAX_MAJOR),
                    document.requireInt32(RuntimeInfoTags.PROTOCOL_MAX_MINOR),
                ),
                providerId = document.requireString(RuntimeInfoTags.PROVIDER_ID),
                providerVersionName = document.requireString(RuntimeInfoTags.PROVIDER_VERSION_NAME),
                providerVersionCode = document.requireInt64(RuntimeInfoTags.PROVIDER_VERSION_CODE),
                implementation = implementation(document.requireInt32(RuntimeInfoTags.IMPLEMENTATION)),
                pythonVersion = document.requireString(RuntimeInfoTags.PYTHON_VERSION),
                isolationMode = isolationMode(document.requireInt32(RuntimeInfoTags.ISOLATION_MODE)),
                minHostVersionCode = document.optionalInt64(RuntimeInfoTags.MIN_HOST_VERSION_CODE),
                maxHostVersionCode = document.optionalInt64(RuntimeInfoTags.MAX_HOST_VERSION_CODE),
            ).also(PythonRuntimeValidation::validateRuntimeInfo)
        }

    fun encodeCapabilities(value: PythonRuntimeCapabilities): ByteArray {
        PythonRuntimeValidation.validateCapabilities(value)
        return writer(PythonRuntimeContract.SCHEMA_CAPABILITIES)
            .int32(CapabilitiesTags.IMPLEMENTATION, value.implementation.wireCode)
            .string(CapabilitiesTags.PYTHON_VERSION, value.pythonVersion)
            .strings(CapabilitiesTags.SUPPORTED_ABIS, value.supportedAbis)
            .boolean(CapabilitiesTags.SUPPORTS_WORKSPACE_ARCHIVE, value.supportsWorkspaceArchive)
            .boolean(CapabilitiesTags.SUPPORTS_STDIN_SNAPSHOT, value.supportsStdinSnapshot)
            .boolean(CapabilitiesTags.SUPPORTS_STRUCTURED_TRACEBACK, value.supportsStructuredTraceback)
            .boolean(CapabilitiesTags.SUPPORTS_COOPERATIVE_CANCELLATION, value.supportsCooperativeCancellation)
            .int32(CapabilitiesTags.CANCELLATION_MODE, value.cancellationMode.wireCode)
            .document(CapabilitiesTags.LIMITS, encodeLimits(value.limits))
            .apply {
                if (value.supportsHostCapabilitySnapshot) {
                    boolean(CapabilitiesTags.SUPPORTS_HOST_CAPABILITY_SNAPSHOT, true)
                }
            }
            .encode()
    }

    fun decodeCapabilities(bytes: ByteArray): PythonRuntimeCapabilities =
        document(
            bytes,
            PythonRuntimeContract.SCHEMA_CAPABILITIES,
            CapabilitiesTags.ALL,
            CapabilitiesTags.REPEATED,
        ).let { document ->
            PythonRuntimeCapabilities(
                implementation = implementation(document.requireInt32(CapabilitiesTags.IMPLEMENTATION)),
                pythonVersion = document.requireString(CapabilitiesTags.PYTHON_VERSION),
                supportedAbis = document.strings(CapabilitiesTags.SUPPORTED_ABIS),
                supportsWorkspaceArchive = document.requireBoolean(CapabilitiesTags.SUPPORTS_WORKSPACE_ARCHIVE),
                supportsStdinSnapshot = document.requireBoolean(CapabilitiesTags.SUPPORTS_STDIN_SNAPSHOT),
                supportsStructuredTraceback = document.requireBoolean(CapabilitiesTags.SUPPORTS_STRUCTURED_TRACEBACK),
                supportsCooperativeCancellation =
                    document.requireBoolean(CapabilitiesTags.SUPPORTS_COOPERATIVE_CANCELLATION),
                cancellationMode = cancellationMode(document.requireInt32(CapabilitiesTags.CANCELLATION_MODE)),
                limits = decodeLimits(document.requireDocument(CapabilitiesTags.LIMITS)),
                supportsHostCapabilitySnapshot =
                    document.optionalBoolean(CapabilitiesTags.SUPPORTS_HOST_CAPABILITY_SNAPSHOT) ?: false,
            ).also(PythonRuntimeValidation::validateCapabilities)
        }

    fun encodeExecutionRequest(value: PythonExecutionRequest): ByteArray {
        PythonRuntimeValidation.validateRequest(value)
        return writer(PythonRuntimeContract.SCHEMA_EXECUTION_REQUEST)
            .bytes(RequestTags.REQUEST_ID, value.requestId.toByteArray())
            .int32(RequestTags.PROTOCOL_MAJOR, value.protocolVersion.major)
            .int32(RequestTags.PROTOCOL_MINOR, value.protocolVersion.minor)
            .string(RequestTags.ENTRY_POINT, value.entryPoint)
            .document(RequestTags.SOURCE, encodePayloadReference(value.source))
            .apply {
                value.workspaceArchive?.let {
                    document(RequestTags.WORKSPACE_ARCHIVE, encodePayloadReference(it))
                }
                value.stdin?.let { document(RequestTags.STDIN, encodePayloadReference(it)) }
                value.hostCapabilitySnapshot?.let {
                    document(RequestTags.CAPABILITY_SNAPSHOT, encodePayloadReference(it))
                }
                strings(RequestTags.ARGUMENT, value.arguments)
            }
            .int64(RequestTags.TIMEOUT_MILLIS, value.timeoutMillis)
            .int64(RequestTags.MAX_OUTPUT_BYTES, value.maxOutputBytes)
            .int32(RequestTags.MAX_OUTPUT_CHUNK_BYTES, value.maxOutputChunkBytes)
            .int64(RequestTags.MAX_OUTPUT_CHUNKS, value.maxOutputChunks)
            .encode()
    }

    fun decodeExecutionRequest(bytes: ByteArray): PythonExecutionRequest =
        document(
            bytes,
            PythonRuntimeContract.SCHEMA_EXECUTION_REQUEST,
            RequestTags.ALL,
            RequestTags.REPEATED,
        ).let { document ->
            PythonExecutionRequest(
                requestId = PythonRequestId.fromBytes(document.requireBytes(RequestTags.REQUEST_ID)),
                protocolVersion = PythonProtocolVersion(
                    document.requireInt32(RequestTags.PROTOCOL_MAJOR),
                    document.requireInt32(RequestTags.PROTOCOL_MINOR),
                ),
                entryPoint = document.requireString(RequestTags.ENTRY_POINT),
                source = decodePayloadReference(document.requireDocument(RequestTags.SOURCE)),
                workspaceArchive = document.optionalDocument(RequestTags.WORKSPACE_ARCHIVE)
                    ?.let(::decodePayloadReference),
                stdin = document.optionalDocument(RequestTags.STDIN)?.let(::decodePayloadReference),
                hostCapabilitySnapshot = document.optionalDocument(RequestTags.CAPABILITY_SNAPSHOT)
                    ?.let(::decodePayloadReference),
                arguments = document.strings(RequestTags.ARGUMENT),
                timeoutMillis = document.requireInt64(RequestTags.TIMEOUT_MILLIS),
                maxOutputBytes = document.requireInt64(RequestTags.MAX_OUTPUT_BYTES),
                maxOutputChunkBytes = document.requireInt32(RequestTags.MAX_OUTPUT_CHUNK_BYTES),
                maxOutputChunks = document.requireInt64(RequestTags.MAX_OUTPUT_CHUNKS),
            ).also(PythonRuntimeValidation::validateRequest)
        }

    fun encodeSessionStarted(value: PythonSessionStarted): ByteArray {
        PythonRuntimeValidation.validateStarted(value)
        return writer(PythonRuntimeContract.SCHEMA_SESSION_STARTED)
            .bytes(StartedTags.REQUEST_ID, value.requestId.toByteArray())
            .int32(StartedTags.PROTOCOL_MAJOR, value.protocolVersion.major)
            .int32(StartedTags.PROTOCOL_MINOR, value.protocolVersion.minor)
            .string(StartedTags.PYTHON_VERSION, value.pythonVersion)
            .int64(StartedTags.FIRST_OUTPUT_SEQUENCE, value.firstOutputSequence)
            .int64(StartedTags.EFFECTIVE_MAX_OUTPUT_BYTES, value.effectiveMaxOutputBytes)
            .int32(StartedTags.EFFECTIVE_MAX_OUTPUT_CHUNK_BYTES, value.effectiveMaxOutputChunkBytes)
            .int64(StartedTags.EFFECTIVE_MAX_OUTPUT_CHUNKS, value.effectiveMaxOutputChunks)
            .int64(StartedTags.RUNTIME_GENERATION, value.runtimeGeneration)
            .encode()
    }

    fun decodeSessionStarted(bytes: ByteArray): PythonSessionStarted =
        document(bytes, PythonRuntimeContract.SCHEMA_SESSION_STARTED, StartedTags.ALL).let { document ->
            PythonSessionStarted(
                requestId = PythonRequestId.fromBytes(document.requireBytes(StartedTags.REQUEST_ID)),
                protocolVersion = PythonProtocolVersion(
                    document.requireInt32(StartedTags.PROTOCOL_MAJOR),
                    document.requireInt32(StartedTags.PROTOCOL_MINOR),
                ),
                pythonVersion = document.requireString(StartedTags.PYTHON_VERSION),
                firstOutputSequence = document.requireInt64(StartedTags.FIRST_OUTPUT_SEQUENCE),
                effectiveMaxOutputBytes = document.requireInt64(StartedTags.EFFECTIVE_MAX_OUTPUT_BYTES),
                effectiveMaxOutputChunkBytes = document.requireInt32(StartedTags.EFFECTIVE_MAX_OUTPUT_CHUNK_BYTES),
                effectiveMaxOutputChunks = document.requireInt64(StartedTags.EFFECTIVE_MAX_OUTPUT_CHUNKS),
                runtimeGeneration = document.requireInt64(StartedTags.RUNTIME_GENERATION),
            ).also(PythonRuntimeValidation::validateStarted)
        }

    fun encodeOutputChunk(value: PythonOutputChunk): ByteArray {
        PythonRuntimeValidation.validateOutputChunk(value)
        return writer(PythonRuntimeContract.SCHEMA_OUTPUT_CHUNK, CHUNK_LIMITS)
            .bytes(OutputChunkTags.REQUEST_ID, value.requestId.toByteArray())
            .int64(OutputChunkTags.SEQUENCE, value.sequence)
            .int32(OutputChunkTags.STREAM, value.stream.wireCode)
            .bytes(OutputChunkTags.DATA, value.toByteArray())
            .encode()
    }

    fun decodeOutputChunk(bytes: ByteArray): PythonOutputChunk =
        document(
            bytes,
            PythonRuntimeContract.SCHEMA_OUTPUT_CHUNK,
            OutputChunkTags.ALL,
            limits = CHUNK_LIMITS,
        ).let { document ->
            PythonOutputChunk(
                requestId = PythonRequestId.fromBytes(document.requireBytes(OutputChunkTags.REQUEST_ID)),
                sequence = document.requireInt64(OutputChunkTags.SEQUENCE),
                stream = outputStream(document.requireInt32(OutputChunkTags.STREAM)),
                data = document.requireBytes(OutputChunkTags.DATA),
            ).also(PythonRuntimeValidation::validateOutputChunk)
        }

    fun encodeExecutionResult(value: PythonExecutionResult): ByteArray {
        PythonRuntimeValidation.validateResult(value)
        return writer(PythonRuntimeContract.SCHEMA_EXECUTION_RESULT)
            .bytes(ResultTags.REQUEST_ID, value.requestId.toByteArray())
            .int32(ResultTags.EXIT_CODE, value.exitCode)
            .int64(ResultTags.ELAPSED_MILLIS, value.elapsedMillis)
            .int64(ResultTags.STDOUT_BYTES, value.stdoutBytes)
            .int64(ResultTags.STDERR_BYTES, value.stderrBytes)
            .int64(ResultTags.STDOUT_CHUNKS, value.stdoutChunks)
            .int64(ResultTags.STDERR_CHUNKS, value.stderrChunks)
            .encode()
    }

    fun decodeExecutionResult(bytes: ByteArray): PythonExecutionResult =
        document(bytes, PythonRuntimeContract.SCHEMA_EXECUTION_RESULT, ResultTags.ALL).let { document ->
            PythonExecutionResult(
                requestId = PythonRequestId.fromBytes(document.requireBytes(ResultTags.REQUEST_ID)),
                exitCode = document.requireInt32(ResultTags.EXIT_CODE),
                elapsedMillis = document.requireInt64(ResultTags.ELAPSED_MILLIS),
                stdoutBytes = document.requireInt64(ResultTags.STDOUT_BYTES),
                stderrBytes = document.requireInt64(ResultTags.STDERR_BYTES),
                stdoutChunks = document.requireInt64(ResultTags.STDOUT_CHUNKS),
                stderrChunks = document.requireInt64(ResultTags.STDERR_CHUNKS),
            ).also(PythonRuntimeValidation::validateResult)
        }

    fun encodeExecutionError(value: PythonExecutionError): ByteArray {
        PythonRuntimeValidation.validateError(value)
        return writer(PythonRuntimeContract.SCHEMA_EXECUTION_ERROR)
            .bytes(ErrorTags.REQUEST_ID, value.requestId.toByteArray())
            .int32(ErrorTags.CODE, value.code.wireCode)
            .int32(ErrorTags.PHASE, value.phase.wireCode)
            .string(ErrorTags.MESSAGE, value.message)
            .boolean(ErrorTags.RETRYABLE_BEFORE_DISPATCH, value.retryableBeforeDispatch)
            .apply {
                value.exceptionType?.let { string(ErrorTags.EXCEPTION_TYPE, it) }
                value.exceptionMessage?.let { string(ErrorTags.EXCEPTION_MESSAGE, it) }
                value.traceback.forEach { document(ErrorTags.TRACEBACK_FRAME, encodeTracebackFrame(it)) }
            }
            .encode()
    }

    fun decodeExecutionError(bytes: ByteArray): PythonExecutionError =
        document(
            bytes,
            PythonRuntimeContract.SCHEMA_EXECUTION_ERROR,
            ErrorTags.ALL,
            ErrorTags.REPEATED,
        ).let { document ->
            PythonExecutionError(
                requestId = PythonRequestId.fromBytes(document.requireBytes(ErrorTags.REQUEST_ID)),
                code = errorCode(document.requireInt32(ErrorTags.CODE)),
                phase = failurePhase(document.requireInt32(ErrorTags.PHASE)),
                message = document.requireString(ErrorTags.MESSAGE),
                retryableBeforeDispatch = document.requireBoolean(ErrorTags.RETRYABLE_BEFORE_DISPATCH),
                exceptionType = document.optionalString(ErrorTags.EXCEPTION_TYPE),
                exceptionMessage = document.optionalString(ErrorTags.EXCEPTION_MESSAGE),
                traceback = document.documents(ErrorTags.TRACEBACK_FRAME).map(::decodeTracebackFrame),
            ).also(PythonRuntimeValidation::validateError)
        }

    fun encodeCancellation(value: PythonExecutionCancellation): ByteArray {
        PythonRuntimeValidation.validateCancellation(value)
        return writer(PythonRuntimeContract.SCHEMA_CANCELLATION)
            .bytes(CancellationTags.REQUEST_ID, value.requestId.toByteArray())
            .int32(CancellationTags.REASON, value.reason.wireCode)
            .int32(CancellationTags.PHASE, value.phase.wireCode)
            .int64(CancellationTags.ELAPSED_MILLIS, value.elapsedMillis)
            .encode()
    }

    fun decodeCancellation(bytes: ByteArray): PythonExecutionCancellation =
        document(bytes, PythonRuntimeContract.SCHEMA_CANCELLATION, CancellationTags.ALL).let { document ->
            PythonExecutionCancellation(
                requestId = PythonRequestId.fromBytes(document.requireBytes(CancellationTags.REQUEST_ID)),
                reason = cancellationReason(document.requireInt32(CancellationTags.REASON)),
                phase = failurePhase(document.requireInt32(CancellationTags.PHASE)),
                elapsedMillis = document.requireInt64(CancellationTags.ELAPSED_MILLIS),
            ).also(PythonRuntimeValidation::validateCancellation)
        }

    private fun encodeLimits(value: PythonRuntimeResourceLimits): ByteArray =
        writer(PythonRuntimeContract.SCHEMA_RESOURCE_LIMITS)
            .int64(LimitsTags.MAX_SOURCE_BYTES, value.maxSourceBytes)
            .int64(LimitsTags.MAX_WORKSPACE_ARCHIVE_BYTES, value.maxWorkspaceArchiveBytes)
            .int32(LimitsTags.MAX_WORKSPACE_ENTRIES, value.maxWorkspaceEntries)
            .int64(LimitsTags.MAX_WORKSPACE_UNCOMPRESSED_BYTES, value.maxWorkspaceUncompressedBytes)
            .int64(LimitsTags.MAX_STDIN_BYTES, value.maxStdinBytes)
            .int64(LimitsTags.MAX_OUTPUT_BYTES, value.maxOutputBytes)
            .int32(LimitsTags.MAX_OUTPUT_CHUNK_BYTES, value.maxOutputChunkBytes)
            .int32(LimitsTags.MAX_OUTSTANDING_OUTPUT_CREDITS, value.maxOutstandingOutputCredits)
            .int64(LimitsTags.MAX_TIMEOUT_MILLIS, value.maxTimeoutMillis)
            .int32(LimitsTags.MAX_CONCURRENT_SESSIONS, value.maxConcurrentSessions)
            .int64(LimitsTags.MAX_OUTPUT_CHUNKS, value.maxOutputChunks)
            .apply {
                if (value.maxHostCapabilitySnapshotBytes > 0L) {
                    int64(
                        LimitsTags.MAX_HOST_CAPABILITY_SNAPSHOT_BYTES,
                        value.maxHostCapabilitySnapshotBytes,
                    )
                }
            }
            .encode()

    private fun decodeLimits(bytes: ByteArray): PythonRuntimeResourceLimits =
        document(bytes, PythonRuntimeContract.SCHEMA_RESOURCE_LIMITS, LimitsTags.ALL).let { document ->
            PythonRuntimeResourceLimits(
                maxSourceBytes = document.requireInt64(LimitsTags.MAX_SOURCE_BYTES),
                maxWorkspaceArchiveBytes = document.requireInt64(LimitsTags.MAX_WORKSPACE_ARCHIVE_BYTES),
                maxWorkspaceEntries = document.requireInt32(LimitsTags.MAX_WORKSPACE_ENTRIES),
                maxWorkspaceUncompressedBytes =
                    document.requireInt64(LimitsTags.MAX_WORKSPACE_UNCOMPRESSED_BYTES),
                maxStdinBytes = document.requireInt64(LimitsTags.MAX_STDIN_BYTES),
                maxOutputBytes = document.requireInt64(LimitsTags.MAX_OUTPUT_BYTES),
                maxOutputChunkBytes = document.requireInt32(LimitsTags.MAX_OUTPUT_CHUNK_BYTES),
                maxOutputChunks = document.requireInt64(LimitsTags.MAX_OUTPUT_CHUNKS),
                maxOutstandingOutputCredits =
                    document.requireInt32(LimitsTags.MAX_OUTSTANDING_OUTPUT_CREDITS),
                maxTimeoutMillis = document.requireInt64(LimitsTags.MAX_TIMEOUT_MILLIS),
                maxConcurrentSessions = document.requireInt32(LimitsTags.MAX_CONCURRENT_SESSIONS),
                maxHostCapabilitySnapshotBytes =
                    document.optionalInt64(LimitsTags.MAX_HOST_CAPABILITY_SNAPSHOT_BYTES) ?: 0L,
            )
        }

    private fun encodePayloadReference(value: PythonPayloadReference): ByteArray =
        writer(PythonRuntimeContract.SCHEMA_PAYLOAD_REFERENCE)
            .int32(PayloadTags.KIND, value.kind.wireCode)
            .fileDescriptorRef(PayloadTags.DESCRIPTOR_INDEX, value.descriptorIndex)
            .int64(PayloadTags.DECLARED_LENGTH_BYTES, value.declaredLengthBytes)
            .bytes(PayloadTags.SHA256, value.sha256.toByteArray())
            .encode()

    private fun decodePayloadReference(bytes: ByteArray): PythonPayloadReference =
        document(bytes, PythonRuntimeContract.SCHEMA_PAYLOAD_REFERENCE, PayloadTags.ALL).let { document ->
            PythonPayloadReference(
                kind = payloadKind(document.requireInt32(PayloadTags.KIND)),
                descriptorIndex = document.requireFileDescriptorRef(PayloadTags.DESCRIPTOR_INDEX),
                declaredLengthBytes = document.requireInt64(PayloadTags.DECLARED_LENGTH_BYTES),
                sha256 = PythonSha256.fromBytes(document.requireBytes(PayloadTags.SHA256)),
            )
        }

    private fun encodeTracebackFrame(value: PythonTracebackFrame): ByteArray =
        writer(PythonRuntimeContract.SCHEMA_TRACEBACK_FRAME)
            .string(TracebackTags.FILE_NAME, value.fileName)
            .int32(TracebackTags.LINE_NUMBER, value.lineNumber)
            .string(TracebackTags.FUNCTION_NAME, value.functionName)
            .apply { value.sourceLine?.let { string(TracebackTags.SOURCE_LINE, it) } }
            .int32(TracebackTags.ORIGIN, value.origin.wireCode)
            .encode()

    private fun decodeTracebackFrame(bytes: ByteArray): PythonTracebackFrame =
        document(bytes, PythonRuntimeContract.SCHEMA_TRACEBACK_FRAME, TracebackTags.ALL).let { document ->
            PythonTracebackFrame(
                origin = tracebackOrigin(document.requireInt32(TracebackTags.ORIGIN)),
                fileName = document.requireString(TracebackTags.FILE_NAME),
                lineNumber = document.requireInt32(TracebackTags.LINE_NUMBER),
                functionName = document.requireString(TracebackTags.FUNCTION_NAME),
                sourceLine = document.optionalString(TracebackTags.SOURCE_LINE),
            )
        }

    private fun writer(
        schemaId: Int,
        limits: TaggedWireLimits = METADATA_LIMITS,
    ) = TaggedWireWriter(
        schemaId = schemaId,
        schemaMajor = PythonRuntimeContract.SCHEMA_MAJOR,
        schemaMinor = PythonRuntimeContract.SCHEMA_MINOR,
        limits = limits,
    )

    private fun document(
        bytes: ByteArray,
        schemaId: Int,
        knownTags: Set<Int>,
        repeatedTags: Set<Int> = emptySet(),
        limits: TaggedWireLimits = METADATA_LIMITS,
    ): TaggedWireDocument = TaggedWireDocument.decode(bytes, limits)
        .requireSchema(schemaId, PythonRuntimeContract.SCHEMA_MAJOR)
        .rejectUnknownRequiredFields(knownTags)
        .validateKnownCardinality(knownTags, repeatedTags)

    private fun implementation(code: Int) = enumValue(PythonImplementation.entries, code, "implementation") {
        it.wireCode
    }

    private fun isolationMode(code: Int) = enumValue(PythonIsolationMode.entries, code, "isolation mode") {
        it.wireCode
    }

    private fun cancellationMode(code: Int) =
        enumValue(PythonCancellationMode.entries, code, "cancellation mode") { it.wireCode }

    private fun payloadKind(code: Int) = enumValue(PythonPayloadKind.entries, code, "payload kind") {
        it.wireCode
    }

    private fun outputStream(code: Int) = enumValue(PythonOutputStream.entries, code, "output stream") {
        it.wireCode
    }

    private fun tracebackOrigin(code: Int) = enumValue(PythonTracebackOrigin.entries, code, "traceback origin") {
        it.wireCode
    }

    private fun errorCode(code: Int) = enumValue(PythonErrorCode.entries, code, "error code") {
        it.wireCode
    }

    private fun failurePhase(code: Int) = enumValue(PythonFailurePhase.entries, code, "failure phase") {
        it.wireCode
    }

    private fun cancellationReason(code: Int) =
        enumValue(PythonCancellationReason.entries, code, "cancellation reason") { it.wireCode }

    private inline fun <T> enumValue(
        values: Iterable<T>,
        code: Int,
        label: String,
        wireCode: (T) -> Int,
    ): T = values.firstOrNull { wireCode(it) == code }
        ?: throw PythonRuntimeContractException(
            PythonRuntimeContractViolation.UNKNOWN_ENUM,
            "Unknown Python Runtime $label value $code",
        )

    private object RuntimeInfoTags {
        const val PROTOCOL_MIN_MAJOR = 1
        const val PROTOCOL_MIN_MINOR = 2
        const val PROTOCOL_MAX_MAJOR = 3
        const val PROTOCOL_MAX_MINOR = 4
        const val PROVIDER_ID = 5
        const val PROVIDER_VERSION_NAME = 6
        const val PROVIDER_VERSION_CODE = 7
        const val IMPLEMENTATION = 8
        const val PYTHON_VERSION = 9
        const val ISOLATION_MODE = 10
        const val MIN_HOST_VERSION_CODE = 11
        const val MAX_HOST_VERSION_CODE = 12
        val ALL = (1..12).toSet()
    }

    private object CapabilitiesTags {
        const val IMPLEMENTATION = 1
        const val PYTHON_VERSION = 2
        const val SUPPORTED_ABIS = 3
        const val SUPPORTS_WORKSPACE_ARCHIVE = 4
        const val SUPPORTS_STDIN_SNAPSHOT = 5
        const val SUPPORTS_STRUCTURED_TRACEBACK = 6
        const val SUPPORTS_COOPERATIVE_CANCELLATION = 7
        const val CANCELLATION_MODE = 8
        const val LIMITS = 9
        const val SUPPORTS_HOST_CAPABILITY_SNAPSHOT = 10
        val ALL = (1..10).toSet()
        val REPEATED = setOf(SUPPORTED_ABIS)
    }

    private object LimitsTags {
        const val MAX_SOURCE_BYTES = 1
        const val MAX_WORKSPACE_ARCHIVE_BYTES = 2
        const val MAX_WORKSPACE_ENTRIES = 3
        const val MAX_WORKSPACE_UNCOMPRESSED_BYTES = 4
        const val MAX_STDIN_BYTES = 5
        const val MAX_OUTPUT_BYTES = 6
        const val MAX_OUTPUT_CHUNK_BYTES = 7
        const val MAX_OUTSTANDING_OUTPUT_CREDITS = 8
        const val MAX_TIMEOUT_MILLIS = 9
        const val MAX_CONCURRENT_SESSIONS = 10
        const val MAX_OUTPUT_CHUNKS = 11
        const val MAX_HOST_CAPABILITY_SNAPSHOT_BYTES = 12
        val ALL = (1..12).toSet()
    }

    private object PayloadTags {
        const val KIND = 1
        const val DESCRIPTOR_INDEX = 2
        const val DECLARED_LENGTH_BYTES = 3
        const val SHA256 = 4
        val ALL = (1..4).toSet()
    }

    private object RequestTags {
        const val REQUEST_ID = 1
        const val PROTOCOL_MAJOR = 2
        const val PROTOCOL_MINOR = 3
        const val ENTRY_POINT = 4
        const val SOURCE = 5
        const val WORKSPACE_ARCHIVE = 6
        const val STDIN = 7
        const val ARGUMENT = 8
        const val TIMEOUT_MILLIS = 9
        const val MAX_OUTPUT_BYTES = 10
        const val MAX_OUTPUT_CHUNK_BYTES = 11
        const val MAX_OUTPUT_CHUNKS = 12
        const val CAPABILITY_SNAPSHOT = 13
        val ALL = (1..13).toSet()
        val REPEATED = setOf(ARGUMENT)
    }

    private object StartedTags {
        const val REQUEST_ID = 1
        const val PROTOCOL_MAJOR = 2
        const val PROTOCOL_MINOR = 3
        const val PYTHON_VERSION = 4
        const val FIRST_OUTPUT_SEQUENCE = 5
        const val EFFECTIVE_MAX_OUTPUT_BYTES = 6
        const val EFFECTIVE_MAX_OUTPUT_CHUNK_BYTES = 7
        const val RUNTIME_GENERATION = 8
        const val EFFECTIVE_MAX_OUTPUT_CHUNKS = 9
        val ALL = (1..9).toSet()
    }

    private object OutputChunkTags {
        const val REQUEST_ID = 1
        const val SEQUENCE = 2
        const val STREAM = 3
        const val DATA = 4
        val ALL = (1..4).toSet()
    }

    private object ResultTags {
        const val REQUEST_ID = 1
        const val EXIT_CODE = 2
        const val ELAPSED_MILLIS = 3
        const val STDOUT_BYTES = 4
        const val STDERR_BYTES = 5
        const val STDOUT_CHUNKS = 6
        const val STDERR_CHUNKS = 7
        val ALL = (1..7).toSet()
    }

    private object TracebackTags {
        const val FILE_NAME = 1
        const val LINE_NUMBER = 2
        const val FUNCTION_NAME = 3
        const val SOURCE_LINE = 4
        const val ORIGIN = 5
        val ALL = (1..5).toSet()
    }

    private object ErrorTags {
        const val REQUEST_ID = 1
        const val CODE = 2
        const val PHASE = 3
        const val MESSAGE = 4
        const val RETRYABLE_BEFORE_DISPATCH = 5
        const val EXCEPTION_TYPE = 6
        const val EXCEPTION_MESSAGE = 7
        const val TRACEBACK_FRAME = 8
        val ALL = (1..8).toSet()
        val REPEATED = setOf(TRACEBACK_FRAME)
    }

    private object CancellationTags {
        const val REQUEST_ID = 1
        const val REASON = 2
        const val PHASE = 3
        const val ELAPSED_MILLIS = 4
        val ALL = (1..4).toSet()
    }
}
