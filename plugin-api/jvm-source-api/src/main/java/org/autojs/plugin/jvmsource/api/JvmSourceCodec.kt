package org.autojs.plugin.jvmsource.api

import org.autojs.plugin.protocol.wire.TaggedWireDocument
import org.autojs.plugin.protocol.wire.TaggedWireLimits
import org.autojs.plugin.protocol.wire.TaggedWireWriter

object JvmSourceCodec {
    private val WIRE_LIMITS = TaggedWireLimits(
        maxDocumentBytes = JvmSourceContract.MAX_METADATA_BYTES,
        maxFieldBytes = JvmSourceContract.MAX_METADATA_BYTES,
        maxFields = 128,
    )

    @JvmStatic
    fun encodeProviderInfo(value: JvmProviderInfo): ByteArray {
        JvmSourceValidation.validateProviderInfo(value)
        return writer(JvmSourceContract.SCHEMA_PROVIDER_INFO)
            .int32(ProviderInfoTags.PROTOCOL_MIN_MAJOR, value.protocolMin.major, requiredForReader = true)
            .int32(ProviderInfoTags.PROTOCOL_MIN_MINOR, value.protocolMin.minor, requiredForReader = true)
            .int32(ProviderInfoTags.PROTOCOL_MAX_MAJOR, value.protocolMax.major, requiredForReader = true)
            .int32(ProviderInfoTags.PROTOCOL_MAX_MINOR, value.protocolMax.minor, requiredForReader = true)
            .string(ProviderInfoTags.PROVIDER_ID, value.providerId, requiredForReader = true)
            .string(ProviderInfoTags.PROVIDER_VERSION_NAME, value.providerVersionName, requiredForReader = true)
            .int64(ProviderInfoTags.PROVIDER_VERSION_CODE, value.providerVersionCode, requiredForReader = true)
            .int32(ProviderInfoTags.ENTRY_API_VERSION, value.entryApiVersion, requiredForReader = true)
            .apply {
                value.minHostVersionCode?.let { int64(ProviderInfoTags.MIN_HOST_VERSION_CODE, it) }
                value.maxHostVersionCode?.let { int64(ProviderInfoTags.MAX_HOST_VERSION_CODE, it) }
            }
            .encode()
    }

    @JvmStatic
    fun decodeProviderInfo(bytes: ByteArray): JvmProviderInfo {
        val document = document(bytes, JvmSourceContract.SCHEMA_PROVIDER_INFO, ProviderInfoTags.ALL)
        return JvmProviderInfo(
            protocolMin = JvmProtocolVersion(
                document.requireInt32(ProviderInfoTags.PROTOCOL_MIN_MAJOR),
                document.requireInt32(ProviderInfoTags.PROTOCOL_MIN_MINOR),
            ),
            protocolMax = JvmProtocolVersion(
                document.requireInt32(ProviderInfoTags.PROTOCOL_MAX_MAJOR),
                document.requireInt32(ProviderInfoTags.PROTOCOL_MAX_MINOR),
            ),
            providerId = document.requireString(ProviderInfoTags.PROVIDER_ID),
            providerVersionName = document.requireString(ProviderInfoTags.PROVIDER_VERSION_NAME),
            providerVersionCode = document.requireInt64(ProviderInfoTags.PROVIDER_VERSION_CODE),
            entryApiVersion = document.requireInt32(ProviderInfoTags.ENTRY_API_VERSION),
            minHostVersionCode = document.optionalInt64(ProviderInfoTags.MIN_HOST_VERSION_CODE),
            maxHostVersionCode = document.optionalInt64(ProviderInfoTags.MAX_HOST_VERSION_CODE),
        ).also(JvmSourceValidation::validateProviderInfo)
    }

    @JvmStatic
    fun encodeCapabilities(value: JvmSourceCapabilities): ByteArray {
        JvmSourceValidation.validateCapabilities(value)
        return writer(JvmSourceContract.SCHEMA_CAPABILITIES)
            .strings(CapabilityTags.LANGUAGES, value.languages.map(JvmSourceLanguage::wireName), requiredForReader = true)
            .strings(
                CapabilityTags.ISOLATION_CAPABILITIES,
                value.isolationCapabilities.map(JvmIsolationCapability::wireName),
                requiredForReader = true,
            )
            .int64(CapabilityTags.MAX_SOURCE_BYTES, value.maxSourceBytes, requiredForReader = true)
            .int64(CapabilityTags.MAX_STDOUT_BYTES, value.maxStdoutBytes, requiredForReader = true)
            .int64(CapabilityTags.MAX_STDERR_BYTES, value.maxStderrBytes, requiredForReader = true)
            .int32(CapabilityTags.MAX_DIAGNOSTIC_BYTES, value.maxDiagnosticBytes, requiredForReader = true)
            .int64(CapabilityTags.MAX_TIMEOUT_MILLIS, value.maxTimeoutMillis, requiredForReader = true)
            .int32(CapabilityTags.MAX_CONCURRENT_SESSIONS, value.maxConcurrentSessions, requiredForReader = true)
            .string(
                CapabilityTags.SOURCE_COMPILER_FAMILY,
                value.sourceCompilerFamily.wireName,
                requiredForReader = true,
            )
            .string(
                CapabilityTags.SOURCE_COMPILER_VERSION,
                value.sourceCompilerVersion,
                requiredForReader = true,
            )
            .string(CapabilityTags.D8_VERSION, value.d8Version, requiredForReader = true)
            .bytes(
                CapabilityTags.RUNTIME_LIBRARY_FINGERPRINT,
                value.runtimeLibraryFingerprint.toByteArray(),
                requiredForReader = true,
            )
            .bytes(
                CapabilityTags.TOOLCHAIN_FINGERPRINT,
                value.toolchainFingerprint.toByteArray(),
                requiredForReader = true,
            )
            .strings(
                CapabilityTags.SCRIPT_CAPABILITIES,
                value.scriptCapabilities.map(JvmScriptCapability::wireName),
            )
            .encode()
    }

    @JvmStatic
    fun decodeCapabilities(bytes: ByteArray): JvmSourceCapabilities {
        val document = document(
            bytes,
            JvmSourceContract.SCHEMA_CAPABILITIES,
            CapabilityTags.ALL,
            CapabilityTags.REPEATED,
        )
        return JvmSourceCapabilities(
            languages = document.strings(CapabilityTags.LANGUAGES).map { value ->
                enumByName<JvmSourceLanguage>(value, "source language") { it.wireName }
            },
            isolationCapabilities = document.strings(CapabilityTags.ISOLATION_CAPABILITIES).map { value ->
                enumByName<JvmIsolationCapability>(value, "isolation capability") { it.wireName }
            },
            sourceCompilerFamily = enumByName<JvmSourceCompilerFamily>(
                document.requireString(CapabilityTags.SOURCE_COMPILER_FAMILY),
                "source compiler family",
            ) { it.wireName },
            sourceCompilerVersion = document.requireString(CapabilityTags.SOURCE_COMPILER_VERSION),
            d8Version = document.requireString(CapabilityTags.D8_VERSION),
            runtimeLibraryFingerprint = JvmSha256.fromBytes(
                document.requireBytes(CapabilityTags.RUNTIME_LIBRARY_FINGERPRINT),
            ),
            toolchainFingerprint = JvmSha256.fromBytes(
                document.requireBytes(CapabilityTags.TOOLCHAIN_FINGERPRINT),
            ),
            maxSourceBytes = document.requireInt64(CapabilityTags.MAX_SOURCE_BYTES),
            maxStdoutBytes = document.requireInt64(CapabilityTags.MAX_STDOUT_BYTES),
            maxStderrBytes = document.requireInt64(CapabilityTags.MAX_STDERR_BYTES),
            maxDiagnosticBytes = document.requireInt32(CapabilityTags.MAX_DIAGNOSTIC_BYTES),
            maxTimeoutMillis = document.requireInt64(CapabilityTags.MAX_TIMEOUT_MILLIS),
            maxConcurrentSessions = document.requireInt32(CapabilityTags.MAX_CONCURRENT_SESSIONS),
            scriptCapabilities = document.strings(CapabilityTags.SCRIPT_CAPABILITIES).map { value ->
                enumByName<JvmScriptCapability>(value, "script capability") { it.wireName }
            },
        ).also(JvmSourceValidation::validateCapabilities)
    }

    @JvmStatic
    fun encodeStarted(value: JvmSourceStarted): ByteArray {
        JvmSourceValidation.validateStarted(value)
        return writer(JvmSourceContract.SCHEMA_STARTED)
            .bytes(StartedTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .string(StartedTags.LANGUAGE, value.language.wireName, requiredForReader = true)
            .string(StartedTags.PROVIDER_ID, value.providerId, requiredForReader = true)
            .bytes(
                StartedTags.TOOLCHAIN_FINGERPRINT,
                value.toolchainFingerprint.toByteArray(),
                requiredForReader = true,
            )
            .int32(StartedTags.COMPILER_PID, value.compilerPid, requiredForReader = true)
            .int32(StartedTags.COMPILER_UID, value.compilerUid, requiredForReader = true)
            .string(
                StartedTags.COMPILER_PROCESS_NAME,
                value.compilerProcessName,
                requiredForReader = true,
            )
            .encode()
    }

    @JvmStatic
    fun decodeStarted(bytes: ByteArray): JvmSourceStarted {
        val document = document(bytes, JvmSourceContract.SCHEMA_STARTED, StartedTags.ALL)
        return JvmSourceStarted(
            requestId = JvmRequestId.fromBytes(document.requireBytes(StartedTags.REQUEST_ID)),
            language = enumByName<JvmSourceLanguage>(
                document.requireString(StartedTags.LANGUAGE),
                "source language",
            ) { it.wireName },
            providerId = document.requireString(StartedTags.PROVIDER_ID),
            toolchainFingerprint = JvmSha256.fromBytes(
                document.requireBytes(StartedTags.TOOLCHAIN_FINGERPRINT),
            ),
            compilerPid = document.requireInt32(StartedTags.COMPILER_PID),
            compilerUid = document.requireInt32(StartedTags.COMPILER_UID),
            compilerProcessName = document.requireString(StartedTags.COMPILER_PROCESS_NAME),
        ).also(JvmSourceValidation::validateStarted)
    }

    @JvmStatic
    fun encodeDiagnostic(value: JvmSourceDiagnostic): ByteArray {
        JvmSourceValidation.validateDiagnostic(value)
        return writer(JvmSourceContract.SCHEMA_DIAGNOSTIC)
            .bytes(DiagnosticTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int32(DiagnosticTags.SEVERITY, value.severity.wireCode, requiredForReader = true)
            .string(DiagnosticTags.CODE, value.code, requiredForReader = true)
            .string(DiagnosticTags.MESSAGE, value.message, requiredForReader = true)
            .apply {
                value.sourceFileName?.let { string(DiagnosticTags.SOURCE_FILE_NAME, it) }
                value.line?.let { int32(DiagnosticTags.LINE, it) }
                value.column?.let { int32(DiagnosticTags.COLUMN, it) }
                value.runtimeExceptionClassName?.let {
                    string(DiagnosticTags.RUNTIME_EXCEPTION_CLASS_NAME, it, requiredForReader = true)
                }
            }
            .encode()
    }

    @JvmStatic
    fun decodeDiagnostic(bytes: ByteArray): JvmSourceDiagnostic {
        val document = document(bytes, JvmSourceContract.SCHEMA_DIAGNOSTIC, DiagnosticTags.ALL)
        return JvmSourceDiagnostic(
            requestId = JvmRequestId.fromBytes(document.requireBytes(DiagnosticTags.REQUEST_ID)),
            severity = enumByCode<JvmDiagnosticSeverity>(
                document.requireInt32(DiagnosticTags.SEVERITY),
                "diagnostic severity",
            ) { it.wireCode },
            code = document.requireString(DiagnosticTags.CODE),
            message = document.requireString(DiagnosticTags.MESSAGE),
            sourceFileName = document.optionalString(DiagnosticTags.SOURCE_FILE_NAME),
            line = document.optionalInt32(DiagnosticTags.LINE),
            column = document.optionalInt32(DiagnosticTags.COLUMN),
            runtimeExceptionClassName = document.optionalString(DiagnosticTags.RUNTIME_EXCEPTION_CLASS_NAME),
        ).also(JvmSourceValidation::validateDiagnostic)
    }

    @JvmStatic
    fun encodeRequest(value: JvmSourceRequest): ByteArray {
        JvmSourceValidation.validateRequest(value)
        return writer(JvmSourceContract.SCHEMA_REQUEST)
            .bytes(RequestTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int32(RequestTags.PROTOCOL_MAJOR, value.protocolVersion.major, requiredForReader = true)
            .int32(RequestTags.PROTOCOL_MINOR, value.protocolVersion.minor, requiredForReader = true)
            .string(RequestTags.LANGUAGE, value.language.wireName, requiredForReader = true)
            .string(RequestTags.SOURCE_FILE_NAME, value.sourceFileName, requiredForReader = true)
            .int64(RequestTags.SOURCE_SIZE_BYTES, value.sourceSizeBytes, requiredForReader = true)
            .bytes(RequestTags.SOURCE_SHA256, value.sourceSha256.toByteArray(), requiredForReader = true)
            .string(RequestTags.ENTRY_CLASS_NAME, value.entryClassName, requiredForReader = true)
            .int32(RequestTags.MIN_API, value.minApi, requiredForReader = true)
            .int64(RequestTags.TIMEOUT_MILLIS, value.timeoutMillis, requiredForReader = true)
            .int64(RequestTags.MAX_STDOUT_BYTES, value.maxStdoutBytes, requiredForReader = true)
            .int64(RequestTags.MAX_STDERR_BYTES, value.maxStderrBytes, requiredForReader = true)
            .int32(RequestTags.DIAGNOSTIC_BYTE_LIMIT, value.diagnosticByteLimit, requiredForReader = true)
            .strings(RequestTags.ALLOWED_HOST_CALLS, value.allowedHostCalls)
            .strings(
                RequestTags.GRANTED_CAPABILITIES,
                value.grantedCapabilities.map(JvmScriptCapability::wireName),
            )
            .bytes(
                RequestTags.EXPECTED_TOOLCHAIN_FINGERPRINT,
                value.expectedToolchainFingerprint.toByteArray(),
                requiredForReader = true,
            )
            .apply {
                if (value.protocolVersion >= JvmSourceContract.ARGS_PROTOCOL_VERSION) {
                    string(RequestTags.ARGS_JSON, value.argsJson, requiredForReader = true)
                }
            }
            .encode()
    }

    @JvmStatic
    fun decodeRequest(bytes: ByteArray): JvmSourceRequest {
        val document = document(
            bytes,
            JvmSourceContract.SCHEMA_REQUEST,
            RequestTags.ALL,
            RequestTags.REPEATED,
        )
        val protocolVersion = JvmProtocolVersion(
            document.requireInt32(RequestTags.PROTOCOL_MAJOR),
            document.requireInt32(RequestTags.PROTOCOL_MINOR),
        )
        val encodedArgs = document.optionalString(RequestTags.ARGS_JSON)
        if (protocolVersion >= JvmSourceContract.ARGS_PROTOCOL_VERSION && encodedArgs == null) {
            throw JvmSourceContractException(
                JvmSourceContractViolation.INVALID_VALUE,
                "Protocol ${protocolVersion.major}.${protocolVersion.minor} request is missing arguments JSON",
            )
        }
        return JvmSourceRequest(
            requestId = JvmRequestId.fromBytes(document.requireBytes(RequestTags.REQUEST_ID)),
            protocolVersion = protocolVersion,
            language = enumByName<JvmSourceLanguage>(
                document.requireString(RequestTags.LANGUAGE),
                "source language",
            ) { it.wireName },
            sourceFileName = document.requireString(RequestTags.SOURCE_FILE_NAME),
            sourceSizeBytes = document.requireInt64(RequestTags.SOURCE_SIZE_BYTES),
            sourceSha256 = JvmSha256.fromBytes(document.requireBytes(RequestTags.SOURCE_SHA256)),
            expectedToolchainFingerprint = JvmSha256.fromBytes(
                document.requireBytes(RequestTags.EXPECTED_TOOLCHAIN_FINGERPRINT),
            ),
            entryClassName = document.requireString(RequestTags.ENTRY_CLASS_NAME),
            minApi = document.requireInt32(RequestTags.MIN_API),
            timeoutMillis = document.requireInt64(RequestTags.TIMEOUT_MILLIS),
            maxStdoutBytes = document.requireInt64(RequestTags.MAX_STDOUT_BYTES),
            maxStderrBytes = document.requireInt64(RequestTags.MAX_STDERR_BYTES),
            diagnosticByteLimit = document.requireInt32(RequestTags.DIAGNOSTIC_BYTE_LIMIT),
            allowedHostCalls = document.strings(RequestTags.ALLOWED_HOST_CALLS),
            grantedCapabilities = document.strings(RequestTags.GRANTED_CAPABILITIES).map { value ->
                enumByName<JvmScriptCapability>(value, "granted script capability") { it.wireName }
            },
            argsJson = encodedArgs ?: "{}",
        ).also(JvmSourceValidation::validateRequest)
    }

    @JvmStatic
    fun encodeResult(value: JvmSourceResult): ByteArray {
        JvmSourceValidation.validateResult(value)
        return writer(JvmSourceContract.SCHEMA_RESULT)
            .bytes(ResultTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .string(ResultTags.RESULT_JSON, value.resultJson, requiredForReader = true)
            .int64(ResultTags.COMPILATION_ELAPSED_MILLIS, value.compilationElapsedMillis, requiredForReader = true)
            .int64(ResultTags.EXECUTION_ELAPSED_MILLIS, value.executionElapsedMillis, requiredForReader = true)
            .int64(ResultTags.ELAPSED_MILLIS, value.elapsedMillis, requiredForReader = true)
            .bytes(
                ResultTags.TOOLCHAIN_FINGERPRINT,
                value.toolchainFingerprint.toByteArray(),
                requiredForReader = true,
            )
            .bytes(
                ResultTags.CLASS_ARTIFACT_SHA256,
                value.classArtifactSha256.toByteArray(),
                requiredForReader = true,
            )
            .bytes(
                ResultTags.DEX_ARTIFACT_SHA256,
                value.dexArtifactSha256.toByteArray(),
                requiredForReader = true,
            )
            .int64(ResultTags.STDOUT_SIZE_BYTES, value.stdoutSizeBytes, requiredForReader = true)
            .bytes(ResultTags.STDOUT_SHA256, value.stdoutSha256.toByteArray(), requiredForReader = true)
            .int64(ResultTags.STDERR_SIZE_BYTES, value.stderrSizeBytes, requiredForReader = true)
            .bytes(ResultTags.STDERR_SHA256, value.stderrSha256.toByteArray(), requiredForReader = true)
            .int64(
                ResultTags.CLASS_ARTIFACT_SIZE_BYTES,
                value.classArtifactSizeBytes,
                requiredForReader = true,
            )
            .int64(
                ResultTags.DEX_ARTIFACT_SIZE_BYTES,
                value.dexArtifactSizeBytes,
                requiredForReader = true,
            )
            .int32(ResultTags.WORKER_PID, value.workerPid, requiredForReader = true)
            .int32(ResultTags.WORKER_UID, value.workerUid, requiredForReader = true)
            .string(ResultTags.WORKER_PROCESS_NAME, value.workerProcessName, requiredForReader = true)
            .int64(ResultTags.WORKER_GENERATION, value.workerGeneration, requiredForReader = true)
            .int32(
                ResultTags.INTERNAL_BRIDGE_CALLER_PID,
                value.internalBridgeCallerPid,
                requiredForReader = true,
            )
            .int32(
                ResultTags.INTERNAL_BRIDGE_CALLER_UID,
                value.internalBridgeCallerUid,
                requiredForReader = true,
            )
            .int32(
                ResultTags.INTERNAL_BRIDGE_CALL_COUNT,
                value.internalBridgeCallCount,
                requiredForReader = true,
            )
            .string(ResultTags.DEX_VERSION, value.dexVersion, requiredForReader = true)
            .int32(ResultTags.LOADER_KIND, value.loaderKind.wireCode, requiredForReader = true)
            .encode()
    }

    @JvmStatic
    fun decodeResult(bytes: ByteArray): JvmSourceResult {
        val document = document(bytes, JvmSourceContract.SCHEMA_RESULT, ResultTags.ALL)
        return JvmSourceResult(
            requestId = JvmRequestId.fromBytes(document.requireBytes(ResultTags.REQUEST_ID)),
            toolchainFingerprint = JvmSha256.fromBytes(
                document.requireBytes(ResultTags.TOOLCHAIN_FINGERPRINT),
            ),
            classArtifactSizeBytes = document.requireInt64(ResultTags.CLASS_ARTIFACT_SIZE_BYTES),
            classArtifactSha256 = JvmSha256.fromBytes(
                document.requireBytes(ResultTags.CLASS_ARTIFACT_SHA256),
            ),
            dexArtifactSizeBytes = document.requireInt64(ResultTags.DEX_ARTIFACT_SIZE_BYTES),
            dexArtifactSha256 = JvmSha256.fromBytes(
                document.requireBytes(ResultTags.DEX_ARTIFACT_SHA256),
            ),
            dexVersion = document.requireString(ResultTags.DEX_VERSION),
            loaderKind = enumByCode<JvmDexLoaderKind>(
                document.requireInt32(ResultTags.LOADER_KIND),
                "DEX loader kind",
            ) { it.wireCode },
            resultJson = document.requireString(ResultTags.RESULT_JSON),
            stdoutSizeBytes = document.requireInt64(ResultTags.STDOUT_SIZE_BYTES),
            stdoutSha256 = JvmSha256.fromBytes(document.requireBytes(ResultTags.STDOUT_SHA256)),
            stderrSizeBytes = document.requireInt64(ResultTags.STDERR_SIZE_BYTES),
            stderrSha256 = JvmSha256.fromBytes(document.requireBytes(ResultTags.STDERR_SHA256)),
            workerPid = document.requireInt32(ResultTags.WORKER_PID),
            workerUid = document.requireInt32(ResultTags.WORKER_UID),
            workerProcessName = document.requireString(ResultTags.WORKER_PROCESS_NAME),
            workerGeneration = document.requireInt64(ResultTags.WORKER_GENERATION),
            internalBridgeCallerPid = document.requireInt32(ResultTags.INTERNAL_BRIDGE_CALLER_PID),
            internalBridgeCallerUid = document.requireInt32(ResultTags.INTERNAL_BRIDGE_CALLER_UID),
            internalBridgeCallCount = document.requireInt32(ResultTags.INTERNAL_BRIDGE_CALL_COUNT),
            compilationElapsedMillis = document.requireInt64(ResultTags.COMPILATION_ELAPSED_MILLIS),
            executionElapsedMillis = document.requireInt64(ResultTags.EXECUTION_ELAPSED_MILLIS),
            elapsedMillis = document.requireInt64(ResultTags.ELAPSED_MILLIS),
        ).also(JvmSourceValidation::validateResult)
    }

    @JvmStatic
    fun encodeError(value: JvmSourceError): ByteArray {
        JvmSourceValidation.validateError(value)
        return writer(JvmSourceContract.SCHEMA_ERROR)
            .bytes(ErrorTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int32(ErrorTags.CODE, value.code.wireCode, requiredForReader = true)
            .int32(ErrorTags.PHASE, value.phase.wireCode, requiredForReader = true)
            .string(ErrorTags.MESSAGE, value.message, requiredForReader = true)
            .boolean(ErrorTags.RETRYABLE, value.retryable, requiredForReader = true)
            .encode()
    }

    @JvmStatic
    fun decodeError(bytes: ByteArray): JvmSourceError {
        val document = document(bytes, JvmSourceContract.SCHEMA_ERROR, ErrorTags.ALL)
        return JvmSourceError(
            requestId = JvmRequestId.fromBytes(document.requireBytes(ErrorTags.REQUEST_ID)),
            code = enumByCode<JvmSourceErrorCode>(document.requireInt32(ErrorTags.CODE), "error code") {
                it.wireCode
            },
            phase = enumByCode<JvmSourceFailurePhase>(document.requireInt32(ErrorTags.PHASE), "failure phase") {
                it.wireCode
            },
            message = document.requireString(ErrorTags.MESSAGE),
            retryable = document.requireBoolean(ErrorTags.RETRYABLE),
        ).also(JvmSourceValidation::validateError)
    }

    @JvmStatic
    fun encodeCancellation(value: JvmSourceCancellation): ByteArray {
        JvmSourceValidation.validateCancellation(value)
        return writer(JvmSourceContract.SCHEMA_CANCELLATION)
            .bytes(CancellationTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int32(CancellationTags.REASON, value.reason.wireCode, requiredForReader = true)
            .int32(CancellationTags.PHASE, value.phase.wireCode, requiredForReader = true)
            .int64(CancellationTags.ELAPSED_MILLIS, value.elapsedMillis, requiredForReader = true)
            .encode()
    }

    @JvmStatic
    fun decodeCancellation(bytes: ByteArray): JvmSourceCancellation {
        val document = document(bytes, JvmSourceContract.SCHEMA_CANCELLATION, CancellationTags.ALL)
        return JvmSourceCancellation(
            requestId = JvmRequestId.fromBytes(document.requireBytes(CancellationTags.REQUEST_ID)),
            reason = enumByCode<JvmCancellationReason>(
                document.requireInt32(CancellationTags.REASON),
                "cancellation reason",
            ) { it.wireCode },
            phase = enumByCode<JvmSourceFailurePhase>(
                document.requireInt32(CancellationTags.PHASE),
                "failure phase",
            ) { it.wireCode },
            elapsedMillis = document.requireInt64(CancellationTags.ELAPSED_MILLIS),
        ).also(JvmSourceValidation::validateCancellation)
    }

    @JvmStatic
    fun encodeHostCall(value: JvmHostCall): ByteArray {
        JvmSourceValidation.validateHostCall(value)
        return writer(JvmSourceContract.SCHEMA_HOST_CALL)
            .bytes(HostCallTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int64(HostCallTags.CALL_ID, value.callId, requiredForReader = true)
            .string(HostCallTags.METHOD, value.method, requiredForReader = true)
            .string(HostCallTags.PAYLOAD_JSON, value.payloadJson, requiredForReader = true)
            .int64(HostCallTags.TIMEOUT_MILLIS, value.timeoutMillis, requiredForReader = true)
            .encode()
    }

    @JvmStatic
    fun decodeHostCall(bytes: ByteArray): JvmHostCall {
        val document = document(bytes, JvmSourceContract.SCHEMA_HOST_CALL, HostCallTags.ALL)
        return JvmHostCall(
            requestId = JvmRequestId.fromBytes(document.requireBytes(HostCallTags.REQUEST_ID)),
            callId = document.requireInt64(HostCallTags.CALL_ID),
            method = document.requireString(HostCallTags.METHOD),
            payloadJson = document.requireString(HostCallTags.PAYLOAD_JSON),
            timeoutMillis = document.requireInt64(HostCallTags.TIMEOUT_MILLIS),
        ).also(JvmSourceValidation::validateHostCall)
    }

    @JvmStatic
    fun encodeHostResponse(value: JvmHostResponse): ByteArray {
        JvmSourceValidation.validateHostResponse(value)
        return writer(JvmSourceContract.SCHEMA_HOST_RESPONSE)
            .bytes(HostResponseTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int64(HostResponseTags.CALL_ID, value.callId, requiredForReader = true)
            .boolean(HostResponseTags.SUCCEEDED, value.succeeded, requiredForReader = true)
            .apply {
                value.payloadJson?.let { string(HostResponseTags.PAYLOAD_JSON, it) }
                value.errorCode?.let { string(HostResponseTags.ERROR_CODE, it) }
                value.errorMessage?.let { string(HostResponseTags.ERROR_MESSAGE, it) }
            }
            .encode()
    }

    @JvmStatic
    fun decodeHostResponse(bytes: ByteArray): JvmHostResponse {
        val document = document(bytes, JvmSourceContract.SCHEMA_HOST_RESPONSE, HostResponseTags.ALL)
        return JvmHostResponse(
            requestId = JvmRequestId.fromBytes(document.requireBytes(HostResponseTags.REQUEST_ID)),
            callId = document.requireInt64(HostResponseTags.CALL_ID),
            succeeded = document.requireBoolean(HostResponseTags.SUCCEEDED),
            payloadJson = document.optionalString(HostResponseTags.PAYLOAD_JSON),
            errorCode = document.optionalString(HostResponseTags.ERROR_CODE),
            errorMessage = document.optionalString(HostResponseTags.ERROR_MESSAGE),
        ).also(JvmSourceValidation::validateHostResponse)
    }

    private fun writer(schemaId: Int): TaggedWireWriter = TaggedWireWriter(
        schemaId = schemaId,
        schemaMajor = JvmSourceContract.SCHEMA_MAJOR,
        schemaMinor = JvmSourceContract.SCHEMA_MINOR,
        limits = WIRE_LIMITS,
    )

    private fun document(
        bytes: ByteArray,
        schemaId: Int,
        knownTags: Set<Int>,
        repeatedTags: Set<Int> = emptySet(),
    ): TaggedWireDocument = TaggedWireDocument.decode(bytes, WIRE_LIMITS)
        .requireSchema(schemaId, JvmSourceContract.SCHEMA_MAJOR)
        .rejectUnknownRequiredFields(knownTags)
        .validateKnownCardinality(knownTags, repeatedTags)

    private inline fun <reified T : Enum<T>> enumByCode(
        value: Int,
        label: String,
        crossinline code: (T) -> Int,
    ): T = enumValues<T>().firstOrNull { code(it) == value }
        ?: throw JvmSourceContractException(
            JvmSourceContractViolation.UNKNOWN_ENUM,
            "Unknown $label value $value",
        )

    private inline fun <reified T : Enum<T>> enumByName(
        value: String,
        label: String,
        crossinline name: (T) -> String,
    ): T = enumValues<T>().firstOrNull { name(it) == value }
        ?: throw JvmSourceContractException(
            JvmSourceContractViolation.UNKNOWN_ENUM,
            "Unknown $label value $value",
        )

    private object ProviderInfoTags {
        const val PROTOCOL_MIN_MAJOR = 1
        const val PROTOCOL_MIN_MINOR = 2
        const val PROTOCOL_MAX_MAJOR = 3
        const val PROTOCOL_MAX_MINOR = 4
        const val PROVIDER_ID = 5
        const val PROVIDER_VERSION_NAME = 6
        const val PROVIDER_VERSION_CODE = 7
        const val ENTRY_API_VERSION = 8
        const val MIN_HOST_VERSION_CODE = 9
        const val MAX_HOST_VERSION_CODE = 10
        val ALL = (1..10).toSet()
    }

    private object CapabilityTags {
        const val LANGUAGES = 1
        const val ISOLATION_CAPABILITIES = 2
        const val MAX_SOURCE_BYTES = 3
        const val MAX_STDOUT_BYTES = 4
        const val MAX_STDERR_BYTES = 5
        const val MAX_DIAGNOSTIC_BYTES = 6
        const val MAX_TIMEOUT_MILLIS = 7
        const val MAX_CONCURRENT_SESSIONS = 8
        const val SOURCE_COMPILER_FAMILY = 9
        const val SOURCE_COMPILER_VERSION = 10
        const val D8_VERSION = 11
        const val RUNTIME_LIBRARY_FINGERPRINT = 12
        const val TOOLCHAIN_FINGERPRINT = 13
        const val SCRIPT_CAPABILITIES = 14
        val ALL = (1..14).toSet()
        val REPEATED = setOf(LANGUAGES, ISOLATION_CAPABILITIES, SCRIPT_CAPABILITIES)
    }

    private object StartedTags {
        const val REQUEST_ID = 1
        const val LANGUAGE = 2
        const val PROVIDER_ID = 3
        const val TOOLCHAIN_FINGERPRINT = 4
        const val COMPILER_PID = 5
        const val COMPILER_UID = 6
        const val COMPILER_PROCESS_NAME = 7
        val ALL = (1..7).toSet()
    }

    private object DiagnosticTags {
        const val REQUEST_ID = 1
        const val SEVERITY = 2
        const val CODE = 3
        const val MESSAGE = 4
        const val SOURCE_FILE_NAME = 5
        const val LINE = 6
        const val COLUMN = 7
        const val RUNTIME_EXCEPTION_CLASS_NAME = 8
        val ALL = (1..8).toSet()
    }

    private object RequestTags {
        const val REQUEST_ID = 1
        const val PROTOCOL_MAJOR = 2
        const val PROTOCOL_MINOR = 3
        const val LANGUAGE = 4
        const val SOURCE_FILE_NAME = 5
        const val SOURCE_SIZE_BYTES = 6
        const val SOURCE_SHA256 = 7
        const val ENTRY_CLASS_NAME = 8
        const val MIN_API = 9
        const val TIMEOUT_MILLIS = 10
        const val MAX_STDOUT_BYTES = 11
        const val MAX_STDERR_BYTES = 12
        const val DIAGNOSTIC_BYTE_LIMIT = 13
        const val ALLOWED_HOST_CALLS = 14
        const val EXPECTED_TOOLCHAIN_FINGERPRINT = 15
        const val GRANTED_CAPABILITIES = 16
        const val ARGS_JSON = 17
        val ALL = (1..17).toSet()
        val REPEATED = setOf(ALLOWED_HOST_CALLS, GRANTED_CAPABILITIES)
    }

    private object ResultTags {
        const val REQUEST_ID = 1
        const val RESULT_JSON = 2
        const val COMPILATION_ELAPSED_MILLIS = 3
        const val EXECUTION_ELAPSED_MILLIS = 4
        const val ELAPSED_MILLIS = 5
        const val TOOLCHAIN_FINGERPRINT = 6
        const val CLASS_ARTIFACT_SHA256 = 7
        const val DEX_ARTIFACT_SHA256 = 8
        const val STDOUT_SIZE_BYTES = 9
        const val STDOUT_SHA256 = 10
        const val STDERR_SIZE_BYTES = 11
        const val STDERR_SHA256 = 12
        const val CLASS_ARTIFACT_SIZE_BYTES = 13
        const val DEX_ARTIFACT_SIZE_BYTES = 14
        const val WORKER_PID = 15
        const val WORKER_UID = 16
        const val WORKER_PROCESS_NAME = 17
        const val WORKER_GENERATION = 18
        const val INTERNAL_BRIDGE_CALLER_PID = 19
        const val INTERNAL_BRIDGE_CALLER_UID = 20
        const val INTERNAL_BRIDGE_CALL_COUNT = 21
        const val DEX_VERSION = 22
        const val LOADER_KIND = 23
        val ALL = (1..23).toSet()
    }

    private object ErrorTags {
        const val REQUEST_ID = 1
        const val CODE = 2
        const val PHASE = 3
        const val MESSAGE = 4
        const val RETRYABLE = 5
        val ALL = (1..5).toSet()
    }

    private object CancellationTags {
        const val REQUEST_ID = 1
        const val REASON = 2
        const val PHASE = 3
        const val ELAPSED_MILLIS = 4
        val ALL = (1..4).toSet()
    }

    private object HostCallTags {
        const val REQUEST_ID = 1
        const val CALL_ID = 2
        const val METHOD = 3
        const val PAYLOAD_JSON = 4
        const val TIMEOUT_MILLIS = 5
        val ALL = (1..5).toSet()
    }

    private object HostResponseTags {
        const val REQUEST_ID = 1
        const val CALL_ID = 2
        const val SUCCEEDED = 3
        const val PAYLOAD_JSON = 4
        const val ERROR_CODE = 5
        const val ERROR_MESSAGE = 6
        val ALL = (1..6).toSet()
    }
}
