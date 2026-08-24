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
    private val INPUT_MESSAGE_LIMITS = TaggedWireLimits(
        maxDocumentBytes = PythonRuntimeContract.MAX_METADATA_BYTES,
        maxFieldBytes = PythonRuntimeContract.MAX_INPUT_REPLY_BYTES,
        maxFields = 32,
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
                if (value.supportsModuleEntry) {
                    boolean(CapabilitiesTags.SUPPORTS_MODULE_ENTRY, true)
                }
                if (value.supportsInteractiveInput) {
                    boolean(CapabilitiesTags.SUPPORTS_INTERACTIVE_INPUT, true)
                }
                if (value.supportsStructuredJsonResult) {
                    boolean(CapabilitiesTags.SUPPORTS_STRUCTURED_JSON_RESULT, true)
                }
                if (value.supportsOutputArtifacts) {
                    boolean(CapabilitiesTags.SUPPORTS_OUTPUT_ARTIFACTS, true)
                }
                if (value.supportsHostCapabilityBroker) {
                    boolean(CapabilitiesTags.SUPPORTS_HOST_CAPABILITY_BROKER, true)
                }
                if (value.supportsLongRunningExecution) {
                    boolean(CapabilitiesTags.SUPPORTS_LONG_RUNNING_EXECUTION, true)
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
                supportsModuleEntry =
                    document.optionalBoolean(CapabilitiesTags.SUPPORTS_MODULE_ENTRY) ?: false,
                supportsInteractiveInput =
                    document.optionalBoolean(CapabilitiesTags.SUPPORTS_INTERACTIVE_INPUT) ?: false,
                supportsStructuredJsonResult =
                    document.optionalBoolean(CapabilitiesTags.SUPPORTS_STRUCTURED_JSON_RESULT) ?: false,
                supportsOutputArtifacts =
                    document.optionalBoolean(CapabilitiesTags.SUPPORTS_OUTPUT_ARTIFACTS) ?: false,
                supportsHostCapabilityBroker =
                    document.optionalBoolean(CapabilitiesTags.SUPPORTS_HOST_CAPABILITY_BROKER) ?: false,
                supportsLongRunningExecution =
                    document.optionalBoolean(CapabilitiesTags.SUPPORTS_LONG_RUNNING_EXECUTION) ?: false,
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
                if (value.entryMode != PythonEntryMode.FILE) {
                    int32(
                        RequestTags.ENTRY_MODE,
                        value.entryMode.wireCode,
                        requiredForReader = true,
                    )
                }
                if (value.executionMode != PythonExecutionMode.BOUNDED) {
                    int32(
                        RequestTags.EXECUTION_MODE,
                        value.executionMode.wireCode,
                        requiredForReader = true,
                    )
                }
                value.interactiveInput?.let {
                    document(
                        RequestTags.INTERACTIVE_INPUT_POLICY,
                        encodeInteractiveInputPolicy(it),
                        requiredForReader = true,
                    )
                }
                value.resultPolicy?.let {
                    document(
                        RequestTags.RESULT_POLICY,
                        encodeResultPolicy(it),
                        requiredForReader = true,
                    )
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
                entryMode = document.optionalInt32(RequestTags.ENTRY_MODE)
                    ?.let(::entryMode)
                    ?: PythonEntryMode.FILE,
                interactiveInput = document.optionalDocument(RequestTags.INTERACTIVE_INPUT_POLICY)
                    ?.let(::decodeInteractiveInputPolicy),
                resultPolicy = document.optionalDocument(RequestTags.RESULT_POLICY)
                    ?.let(::decodeResultPolicy),
                executionMode = document.optionalInt32(RequestTags.EXECUTION_MODE)
                    ?.let(::executionMode)
                    ?: PythonExecutionMode.BOUNDED,
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

    fun encodeExecutionHeartbeat(value: PythonExecutionHeartbeat): ByteArray {
        PythonRuntimeValidation.validateHeartbeat(value)
        return writer(PythonRuntimeContract.SCHEMA_EXECUTION_HEARTBEAT)
            .bytes(HeartbeatTags.REQUEST_ID, value.requestId.toByteArray())
            .int64(HeartbeatTags.SEQUENCE, value.sequence)
            .int64(HeartbeatTags.ELAPSED_MILLIS, value.elapsedMillis)
            .encode()
    }

    fun decodeExecutionHeartbeat(bytes: ByteArray): PythonExecutionHeartbeat =
        document(
            bytes,
            PythonRuntimeContract.SCHEMA_EXECUTION_HEARTBEAT,
            HeartbeatTags.ALL,
        ).let { document ->
            PythonExecutionHeartbeat(
                requestId = PythonRequestId.fromBytes(document.requireBytes(HeartbeatTags.REQUEST_ID)),
                sequence = document.requireInt64(HeartbeatTags.SEQUENCE),
                elapsedMillis = document.requireInt64(HeartbeatTags.ELAPSED_MILLIS),
            ).also(PythonRuntimeValidation::validateHeartbeat)
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

    fun encodeInputPrompt(value: PythonInputPrompt): ByteArray {
        PythonRuntimeValidation.validateInputPrompt(value)
        return writer(PythonRuntimeContract.SCHEMA_INPUT_PROMPT, INPUT_MESSAGE_LIMITS)
            .bytes(InputPromptTags.REQUEST_ID, value.requestId.toByteArray())
            .int64(InputPromptTags.PROMPT_ID, value.promptId.value)
            .string(InputPromptTags.TEXT, value.text)
            .int32(InputPromptTags.ECHO, value.echo.wireCode)
            .int32(InputPromptTags.MAX_REPLY_BYTES, value.maxReplyBytes)
            .int64(InputPromptTags.REPLY_TIMEOUT_MILLIS, value.replyTimeoutMillis)
            .encode()
    }

    fun decodeInputPrompt(bytes: ByteArray): PythonInputPrompt =
        document(
            bytes,
            PythonRuntimeContract.SCHEMA_INPUT_PROMPT,
            InputPromptTags.ALL,
            limits = INPUT_MESSAGE_LIMITS,
        ).let { document ->
            PythonInputPrompt(
                requestId = PythonRequestId.fromBytes(document.requireBytes(InputPromptTags.REQUEST_ID)),
                promptId = PythonPromptId.fromLong(document.requireInt64(InputPromptTags.PROMPT_ID)),
                text = document.requireString(InputPromptTags.TEXT),
                echo = inputEcho(document.requireInt32(InputPromptTags.ECHO)),
                maxReplyBytes = document.requireInt32(InputPromptTags.MAX_REPLY_BYTES),
                replyTimeoutMillis = document.requireInt64(InputPromptTags.REPLY_TIMEOUT_MILLIS),
            ).also(PythonRuntimeValidation::validateInputPrompt)
        }

    fun encodeInputReply(value: PythonInputReply): ByteArray {
        PythonRuntimeValidation.validateInputReply(value)
        return writer(PythonRuntimeContract.SCHEMA_INPUT_REPLY, INPUT_MESSAGE_LIMITS)
            .bytes(InputReplyTags.REQUEST_ID, value.requestId.toByteArray())
            .int64(InputReplyTags.PROMPT_ID, value.promptId.value)
            .int32(InputReplyTags.STATUS, value.status.wireCode)
            .apply { value.value?.let { string(InputReplyTags.VALUE, it) } }
            .encode()
    }

    fun decodeInputReply(bytes: ByteArray): PythonInputReply =
        document(
            bytes,
            PythonRuntimeContract.SCHEMA_INPUT_REPLY,
            InputReplyTags.ALL,
            limits = INPUT_MESSAGE_LIMITS,
        ).let { document ->
            PythonInputReply(
                requestId = PythonRequestId.fromBytes(document.requireBytes(InputReplyTags.REQUEST_ID)),
                promptId = PythonPromptId.fromLong(document.requireInt64(InputReplyTags.PROMPT_ID)),
                status = inputReplyStatus(document.requireInt32(InputReplyTags.STATUS)),
                value = document.optionalString(InputReplyTags.VALUE),
            ).also(PythonRuntimeValidation::validateInputReply)
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
            .apply {
                value.structuredJson?.let {
                    string(ResultTags.STRUCTURED_JSON, it, requiredForReader = true)
                }
                value.outputArtifacts.forEach {
                    document(
                        ResultTags.OUTPUT_ARTIFACT,
                        encodeOutputArtifact(it),
                        requiredForReader = true,
                    )
                }
            }
            .encode()
    }

    fun decodeExecutionResult(bytes: ByteArray): PythonExecutionResult =
        document(
            bytes,
            PythonRuntimeContract.SCHEMA_EXECUTION_RESULT,
            ResultTags.ALL,
            ResultTags.REPEATED,
        ).let { document ->
            PythonExecutionResult(
                requestId = PythonRequestId.fromBytes(document.requireBytes(ResultTags.REQUEST_ID)),
                exitCode = document.requireInt32(ResultTags.EXIT_CODE),
                elapsedMillis = document.requireInt64(ResultTags.ELAPSED_MILLIS),
                stdoutBytes = document.requireInt64(ResultTags.STDOUT_BYTES),
                stderrBytes = document.requireInt64(ResultTags.STDERR_BYTES),
                stdoutChunks = document.requireInt64(ResultTags.STDOUT_CHUNKS),
                stderrChunks = document.requireInt64(ResultTags.STDERR_CHUNKS),
                structuredJson = document.optionalString(ResultTags.STRUCTURED_JSON),
                outputArtifacts = document.documents(ResultTags.OUTPUT_ARTIFACT).map(::decodeOutputArtifact),
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
                if (value.maxInputPromptBytes > 0) {
                    int32(LimitsTags.MAX_INPUT_PROMPT_BYTES, value.maxInputPromptBytes)
                }
                if (value.maxInputReplyBytes > 0) {
                    int32(LimitsTags.MAX_INPUT_REPLY_BYTES, value.maxInputReplyBytes)
                }
                if (value.maxInputPrompts > 0) {
                    int32(LimitsTags.MAX_INPUT_PROMPTS, value.maxInputPrompts)
                }
                if (value.maxInputWaitMillis > 0L) {
                    int64(LimitsTags.MAX_INPUT_WAIT_MILLIS, value.maxInputWaitMillis)
                }
                if (value.maxStructuredJsonBytes > 0) {
                    int32(LimitsTags.MAX_STRUCTURED_JSON_BYTES, value.maxStructuredJsonBytes)
                }
                if (value.maxOutputArtifacts > 0) {
                    int32(LimitsTags.MAX_OUTPUT_ARTIFACTS, value.maxOutputArtifacts)
                }
                if (value.maxOutputArtifactPathBytes > 0) {
                    int32(LimitsTags.MAX_OUTPUT_ARTIFACT_PATH_BYTES, value.maxOutputArtifactPathBytes)
                }
                if (value.maxOutputArtifactBytes > 0L) {
                    int64(LimitsTags.MAX_OUTPUT_ARTIFACT_BYTES, value.maxOutputArtifactBytes)
                }
                if (value.maxTotalOutputArtifactBytes > 0L) {
                    int64(LimitsTags.MAX_TOTAL_OUTPUT_ARTIFACT_BYTES, value.maxTotalOutputArtifactBytes)
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
                maxInputPromptBytes = document.optionalInt32(LimitsTags.MAX_INPUT_PROMPT_BYTES) ?: 0,
                maxInputReplyBytes = document.optionalInt32(LimitsTags.MAX_INPUT_REPLY_BYTES) ?: 0,
                maxInputPrompts = document.optionalInt32(LimitsTags.MAX_INPUT_PROMPTS) ?: 0,
                maxInputWaitMillis = document.optionalInt64(LimitsTags.MAX_INPUT_WAIT_MILLIS) ?: 0L,
                maxStructuredJsonBytes =
                    document.optionalInt32(LimitsTags.MAX_STRUCTURED_JSON_BYTES) ?: 0,
                maxOutputArtifacts = document.optionalInt32(LimitsTags.MAX_OUTPUT_ARTIFACTS) ?: 0,
                maxOutputArtifactPathBytes =
                    document.optionalInt32(LimitsTags.MAX_OUTPUT_ARTIFACT_PATH_BYTES) ?: 0,
                maxOutputArtifactBytes =
                    document.optionalInt64(LimitsTags.MAX_OUTPUT_ARTIFACT_BYTES) ?: 0L,
                maxTotalOutputArtifactBytes =
                    document.optionalInt64(LimitsTags.MAX_TOTAL_OUTPUT_ARTIFACT_BYTES) ?: 0L,
            )
        }

    private fun encodeResultPolicy(value: PythonResultPolicy): ByteArray =
        writer(PythonRuntimeContract.SCHEMA_RESULT_POLICY)
            .int32(ResultPolicyTags.MAX_STRUCTURED_JSON_BYTES, value.maxStructuredJsonBytes)
            .int32(ResultPolicyTags.MAX_ARTIFACTS, value.maxArtifacts)
            .int32(ResultPolicyTags.MAX_ARTIFACT_PATH_BYTES, value.maxArtifactPathBytes)
            .int64(ResultPolicyTags.MAX_ARTIFACT_BYTES, value.maxArtifactBytes)
            .int64(ResultPolicyTags.MAX_TOTAL_ARTIFACT_BYTES, value.maxTotalArtifactBytes)
            .encode()

    private fun decodeResultPolicy(bytes: ByteArray): PythonResultPolicy =
        document(
            bytes,
            PythonRuntimeContract.SCHEMA_RESULT_POLICY,
            ResultPolicyTags.ALL,
        ).let { document ->
            PythonResultPolicy(
                maxStructuredJsonBytes = document.requireInt32(ResultPolicyTags.MAX_STRUCTURED_JSON_BYTES),
                maxArtifacts = document.requireInt32(ResultPolicyTags.MAX_ARTIFACTS),
                maxArtifactPathBytes = document.requireInt32(ResultPolicyTags.MAX_ARTIFACT_PATH_BYTES),
                maxArtifactBytes = document.requireInt64(ResultPolicyTags.MAX_ARTIFACT_BYTES),
                maxTotalArtifactBytes = document.requireInt64(ResultPolicyTags.MAX_TOTAL_ARTIFACT_BYTES),
            ).also(PythonRuntimeValidation::validateResultPolicy)
        }

    private fun encodeOutputArtifact(value: PythonOutputArtifact): ByteArray =
        writer(PythonRuntimeContract.SCHEMA_OUTPUT_ARTIFACT)
            .string(OutputArtifactTags.PATH, value.path)
            .fileDescriptorRef(OutputArtifactTags.DESCRIPTOR_INDEX, value.descriptorIndex)
            .int64(OutputArtifactTags.DECLARED_LENGTH_BYTES, value.declaredLengthBytes)
            .bytes(OutputArtifactTags.SHA256, value.sha256.toByteArray())
            .encode()

    private fun decodeOutputArtifact(bytes: ByteArray): PythonOutputArtifact =
        document(
            bytes,
            PythonRuntimeContract.SCHEMA_OUTPUT_ARTIFACT,
            OutputArtifactTags.ALL,
        ).let { document ->
            PythonOutputArtifact(
                path = document.requireString(OutputArtifactTags.PATH),
                descriptorIndex = document.requireFileDescriptorRef(OutputArtifactTags.DESCRIPTOR_INDEX),
                declaredLengthBytes = document.requireInt64(OutputArtifactTags.DECLARED_LENGTH_BYTES),
                sha256 = PythonSha256.fromBytes(document.requireBytes(OutputArtifactTags.SHA256)),
            ).also(PythonRuntimeValidation::validateOutputArtifact)
        }

    private fun encodeInteractiveInputPolicy(value: PythonInteractiveInputPolicy): ByteArray =
        writer(PythonRuntimeContract.SCHEMA_INTERACTIVE_INPUT_POLICY)
            .int32(InteractiveInputPolicyTags.MAX_PROMPTS, value.maxPrompts)
            .int32(InteractiveInputPolicyTags.MAX_PROMPT_BYTES, value.maxPromptBytes)
            .int32(InteractiveInputPolicyTags.MAX_REPLY_BYTES, value.maxReplyBytes)
            .int64(InteractiveInputPolicyTags.REPLY_TIMEOUT_MILLIS, value.replyTimeoutMillis)
            .encode()

    private fun decodeInteractiveInputPolicy(bytes: ByteArray): PythonInteractiveInputPolicy =
        document(
            bytes,
            PythonRuntimeContract.SCHEMA_INTERACTIVE_INPUT_POLICY,
            InteractiveInputPolicyTags.ALL,
        ).let { document ->
            PythonInteractiveInputPolicy(
                maxPrompts = document.requireInt32(InteractiveInputPolicyTags.MAX_PROMPTS),
                maxPromptBytes = document.requireInt32(InteractiveInputPolicyTags.MAX_PROMPT_BYTES),
                maxReplyBytes = document.requireInt32(InteractiveInputPolicyTags.MAX_REPLY_BYTES),
                replyTimeoutMillis = document.requireInt64(InteractiveInputPolicyTags.REPLY_TIMEOUT_MILLIS),
            ).also(PythonRuntimeValidation::validateInteractiveInputPolicy)
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

    private fun entryMode(code: Int) = enumValue(PythonEntryMode.entries, code, "entry mode") {
        it.wireCode
    }

    private fun executionMode(code: Int) =
        enumValue(PythonExecutionMode.entries, code, "execution mode") { it.wireCode }

    private fun payloadKind(code: Int) = enumValue(PythonPayloadKind.entries, code, "payload kind") {
        it.wireCode
    }

    private fun outputStream(code: Int) = enumValue(PythonOutputStream.entries, code, "output stream") {
        it.wireCode
    }

    private fun inputEcho(code: Int) = enumValue(PythonInputEcho.entries, code, "input echo") {
        it.wireCode
    }

    private fun inputReplyStatus(code: Int) =
        enumValue(PythonInputReplyStatus.entries, code, "input reply status") { it.wireCode }

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
        const val SUPPORTS_MODULE_ENTRY = 11
        const val SUPPORTS_INTERACTIVE_INPUT = 12
        const val SUPPORTS_STRUCTURED_JSON_RESULT = 13
        const val SUPPORTS_OUTPUT_ARTIFACTS = 14
        const val SUPPORTS_HOST_CAPABILITY_BROKER = 15
        const val SUPPORTS_LONG_RUNNING_EXECUTION = 16
        val ALL = (1..16).toSet()
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
        const val MAX_INPUT_PROMPT_BYTES = 13
        const val MAX_INPUT_REPLY_BYTES = 14
        const val MAX_INPUT_PROMPTS = 15
        const val MAX_INPUT_WAIT_MILLIS = 16
        const val MAX_STRUCTURED_JSON_BYTES = 17
        const val MAX_OUTPUT_ARTIFACTS = 18
        const val MAX_OUTPUT_ARTIFACT_PATH_BYTES = 19
        const val MAX_OUTPUT_ARTIFACT_BYTES = 20
        const val MAX_TOTAL_OUTPUT_ARTIFACT_BYTES = 21
        val ALL = (1..21).toSet()
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
        const val ENTRY_MODE = 14
        const val INTERACTIVE_INPUT_POLICY = 15
        const val RESULT_POLICY = 16
        const val EXECUTION_MODE = 17
        val ALL = (1..17).toSet()
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

    private object HeartbeatTags {
        const val REQUEST_ID = 1
        const val SEQUENCE = 2
        const val ELAPSED_MILLIS = 3
        val ALL = (1..3).toSet()
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
        const val STRUCTURED_JSON = 8
        const val OUTPUT_ARTIFACT = 9
        val ALL = (1..9).toSet()
        val REPEATED = setOf(OUTPUT_ARTIFACT)
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

    private object InteractiveInputPolicyTags {
        const val MAX_PROMPTS = 1
        const val MAX_PROMPT_BYTES = 2
        const val MAX_REPLY_BYTES = 3
        const val REPLY_TIMEOUT_MILLIS = 4
        val ALL = (1..4).toSet()
    }

    private object ResultPolicyTags {
        const val MAX_STRUCTURED_JSON_BYTES = 1
        const val MAX_ARTIFACTS = 2
        const val MAX_ARTIFACT_PATH_BYTES = 3
        const val MAX_ARTIFACT_BYTES = 4
        const val MAX_TOTAL_ARTIFACT_BYTES = 5
        val ALL = (1..5).toSet()
    }

    private object OutputArtifactTags {
        const val PATH = 1
        const val DESCRIPTOR_INDEX = 2
        const val DECLARED_LENGTH_BYTES = 3
        const val SHA256 = 4
        val ALL = (1..4).toSet()
    }

    private object InputPromptTags {
        const val REQUEST_ID = 1
        const val PROMPT_ID = 2
        const val TEXT = 3
        const val ECHO = 4
        const val MAX_REPLY_BYTES = 5
        const val REPLY_TIMEOUT_MILLIS = 6
        val ALL = (1..6).toSet()
    }

    private object InputReplyTags {
        const val REQUEST_ID = 1
        const val PROMPT_ID = 2
        const val STATUS = 3
        const val VALUE = 4
        val ALL = (1..4).toSet()
    }
}
