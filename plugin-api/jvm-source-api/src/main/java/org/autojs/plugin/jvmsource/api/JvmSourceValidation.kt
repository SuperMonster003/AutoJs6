package org.autojs.plugin.jvmsource.api

import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object JvmSourceNegotiation {
    @JvmField
    val HOST_MIN = JvmProtocolVersion(
        JvmSourceContract.PROTOCOL_MAJOR,
        JvmSourceContract.PROTOCOL_MINOR,
    )

    @JvmField
    val HOST_MAX = JvmProtocolVersion(
        JvmSourceContract.PROTOCOL_MAJOR,
        JvmSourceContract.PROTOCOL_MINOR,
    )

    @JvmStatic
    @JvmOverloads
    fun negotiate(
        providerInfo: JvmProviderInfo,
        hostVersionCode: Long,
        hostMin: JvmProtocolVersion = HOST_MIN,
        hostMax: JvmProtocolVersion = HOST_MAX,
    ): JvmProtocolVersion? {
        if (hostVersionCode <= 0L) {
            throw JvmSourceContractException(
                JvmSourceContractViolation.INVALID_VALUE,
                "Host version code must be positive",
            )
        }
        JvmSourceValidation.validateProtocolRange(hostMin, hostMax, "host")
        JvmSourceValidation.validateProviderInfo(providerInfo)
        if (providerInfo.minHostVersionCode?.let { hostVersionCode < it } == true ||
            providerInfo.maxHostVersionCode?.let { hostVersionCode > it } == true
        ) {
            return null
        }
        val lower = maxOf(hostMin, providerInfo.protocolMin)
        val upper = minOf(hostMax, providerInfo.protocolMax)
        return upper.takeIf { lower <= upper }
    }

    @JvmStatic
    @JvmOverloads
    fun requireNegotiated(
        providerInfo: JvmProviderInfo,
        hostVersionCode: Long,
        hostMin: JvmProtocolVersion = HOST_MIN,
        hostMax: JvmProtocolVersion = HOST_MAX,
    ): JvmProtocolVersion = negotiate(providerInfo, hostVersionCode, hostMin, hostMax)
        ?: throw JvmSourceContractException(
            JvmSourceContractViolation.PROTOCOL_INCOMPATIBLE,
            "Host version and provider JVM source protocol requirements are incompatible",
        )
}

object JvmSourceValidation {
    private val PROVIDER_ID = Regex("[a-z0-9][a-z0-9._-]{0,127}")
    private val ENTRY_CLASS = Regex(
        "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*",
    )
    private val DIAGNOSTIC_CODE = Regex("[A-Z0-9][A-Z0-9._-]{0,127}")
    private val HOST_METHOD = Regex("[a-z][a-z0-9_-]*(?:\\.[a-z][a-z0-9_-]*)+")
    private val ERROR_CODE = Regex("[A-Z0-9][A-Z0-9._-]{0,127}")
    private val TOOL_VERSION = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}")
    private val PROCESS_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*(?::[A-Za-z0-9_]+)?")
    private val REQUIRED_ISOLATION = JvmIsolationCapability.entries.toSet()

    @JvmStatic
    fun validateProviderInfo(info: JvmProviderInfo) {
        validateProtocolRange(info.protocolMin, info.protocolMax, "provider")
        requireValue(PROVIDER_ID.matches(info.providerId)) { "Provider ID is invalid" }
        requireUtf8Text(
            info.providerVersionName,
            "Provider version name",
            JvmSourceContract.MAX_VERSION_TEXT_BYTES,
        )
        requireValue(info.providerVersionCode > 0L) { "Provider version code must be positive" }
        requireValue(info.entryApiVersion == JvmSourceContract.ENTRY_API_VERSION) {
            "Provider entry API version is unsupported"
        }
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

    @JvmStatic
    fun validateCapabilities(capabilities: JvmSourceCapabilities) {
        requireDistinctNonEmpty(capabilities.languages, "languages")
        requireValue(capabilities.languages.size == 1) {
            "Protocol v1 requires exactly one source language per provider"
        }
        requireValue(capabilities.languages.all { it in setOf(JvmSourceLanguage.JAVA, JvmSourceLanguage.KOTLIN) }) {
            "Unsupported JVM source language"
        }
        requireValue(capabilities.sourceCompilerFamily.language == capabilities.languages.single()) {
            "Source compiler family does not match the provider language"
        }
        validateToolVersion(capabilities.sourceCompilerVersion, "Source compiler version")
        validateToolVersion(capabilities.d8Version, "D8 version")
        requireValue(
            capabilities.toolchainFingerprint == JvmToolchainFingerprint.compute(
                language = capabilities.languages.single(),
                sourceCompilerFamily = capabilities.sourceCompilerFamily,
                sourceCompilerVersion = capabilities.sourceCompilerVersion,
                d8Version = capabilities.d8Version,
                runtimeLibraryFingerprint = capabilities.runtimeLibraryFingerprint,
            ),
        ) { "Combined toolchain fingerprint does not match its declared components" }
        requireDistinctNonEmpty(capabilities.isolationCapabilities, "isolation capabilities")
        requireValue(capabilities.isolationCapabilities.toSet().containsAll(REQUIRED_ISOLATION)) {
            "Provider does not satisfy the required disposable-worker isolation contract"
        }
        requireValue(capabilities.maxSourceBytes in 1L..JvmSourceContract.MAX_SOURCE_BYTES) {
            "Source byte limit is invalid"
        }
        requireValue(capabilities.maxStdoutBytes in 0L..JvmSourceContract.MAX_STDOUT_BYTES) {
            "stdout byte limit is invalid"
        }
        requireValue(capabilities.maxStderrBytes in 0L..JvmSourceContract.MAX_STDERR_BYTES) {
            "stderr byte limit is invalid"
        }
        requireValue(capabilities.maxDiagnosticBytes in 1..JvmSourceContract.MAX_DIAGNOSTIC_BYTES) {
            "Diagnostic byte limit is invalid"
        }
        requireValue(capabilities.maxTimeoutMillis in 1L..JvmSourceContract.MAX_TIMEOUT_MILLIS) {
            "Timeout limit is invalid"
        }
        requireValue(capabilities.maxConcurrentSessions == JvmSourceContract.MAX_CONCURRENT_SESSIONS) {
            "Protocol v1 requires exactly one active session per provider process"
        }
        requireValue(capabilities.scriptCapabilities.size <= JvmSourceContract.MAX_SCRIPT_CAPABILITIES) {
            "Script capability list exceeds the protocol limit"
        }
        requireValue(capabilities.scriptCapabilities.distinct().size == capabilities.scriptCapabilities.size) {
            "Script capability list contains duplicates"
        }
    }

    @JvmStatic
    fun validateStarted(started: JvmSourceStarted) {
        requireValue(PROVIDER_ID.matches(started.providerId)) { "Started provider ID is invalid" }
        requireValue(started.compilerPid > 0) { "Compiler PID must be positive" }
        requireValue(started.compilerUid > 0) { "Compiler UID must be positive" }
        validateProcessName(started.compilerProcessName, "Compiler process name")
    }

    @JvmStatic
    fun validateStartedAgainst(
        started: JvmSourceStarted,
        request: JvmSourceRequest,
        providerInfo: JvmProviderInfo,
        capabilities: JvmSourceCapabilities,
    ) {
        validateStarted(started)
        validateRequest(request)
        validateProviderInfo(providerInfo)
        validateCapabilities(capabilities)
        requireCapability(started.requestId == request.requestId) {
            "Started request ID differs from the submitted request"
        }
        requireCapability(started.language == request.language) {
            "Started language differs from the submitted request"
        }
        requireCapability(started.providerId == providerInfo.providerId) {
            "Started provider ID differs from the negotiated provider"
        }
        requireCapability(started.toolchainFingerprint == request.expectedToolchainFingerprint &&
            started.toolchainFingerprint == capabilities.toolchainFingerprint
        ) {
            "Started toolchain fingerprint differs from the negotiated toolchain"
        }
    }

    @JvmStatic
    fun validateDiagnostic(diagnostic: JvmSourceDiagnostic) {
        requireValue(DIAGNOSTIC_CODE.matches(diagnostic.code)) { "Diagnostic code is invalid" }
        requireCodePointLimit(
            diagnostic.message,
            "Diagnostic message",
            JvmSourceContract.MAX_DIAGNOSTIC_MESSAGE_CODE_POINTS,
        )
        diagnostic.sourceFileName?.let(::validateSourceFileName)
        val line = diagnostic.line
        val column = diagnostic.column
        requireValue((line == null) == (column == null)) {
            "Diagnostic line and column must be supplied together"
        }
        if (line != null && column != null) {
            requireValue(diagnostic.sourceFileName != null) { "Located diagnostic requires a source file name" }
            requireValue(line > 0 && column > 0) {
                "Diagnostic line and column must be positive"
            }
        }
    }

    @JvmStatic
    fun validateRequest(request: JvmSourceRequest) {
        validateProtocolVersion(request.protocolVersion, "request")
        validateSourceFileName(request.sourceFileName)
        requireValue(JvmSourceLanguage.fromFileName(request.sourceFileName) == request.language) {
            "Source file extension does not match the requested language"
        }
        requireValue(!request.sourceFileName.endsWith(".kts", ignoreCase = true)) {
            "Kotlin script files are not supported"
        }
        requireValue(request.sourceSizeBytes in 1L..JvmSourceContract.MAX_SOURCE_BYTES) {
            "Source size is outside the protocol limit"
        }
        requireUtf8Text(
            request.entryClassName,
            "Entry class name",
            JvmSourceContract.MAX_ENTRY_CLASS_NAME_BYTES,
        )
        requireValue(ENTRY_CLASS.matches(request.entryClassName)) { "Entry class name is invalid" }
        requireValue(request.minApi in JvmSourceContract.MIN_ANDROID_API..10_000) {
            "Minimum Android API is invalid"
        }
        requireValue(request.timeoutMillis in 1L..JvmSourceContract.MAX_TIMEOUT_MILLIS) {
            "Request timeout is invalid"
        }
        requireValue(request.maxStdoutBytes in 0L..JvmSourceContract.MAX_STDOUT_BYTES) {
            "Requested stdout limit is invalid"
        }
        requireValue(request.maxStderrBytes in 0L..JvmSourceContract.MAX_STDERR_BYTES) {
            "Requested stderr limit is invalid"
        }
        requireValue(request.diagnosticByteLimit in 1..JvmSourceContract.MAX_DIAGNOSTIC_BYTES) {
            "Requested diagnostic limit is invalid"
        }
        requireValue(request.allowedHostCalls.size <= JvmSourceContract.MAX_ALLOWED_HOST_CALLS) {
            "Host-call allowlist exceeds the protocol limit"
        }
        requireValue(request.allowedHostCalls.distinct().size == request.allowedHostCalls.size) {
            "Host-call allowlist contains duplicates"
        }
        request.allowedHostCalls.forEach(::validateHostMethod)
        requireValue(request.grantedCapabilities.size <= JvmSourceContract.MAX_SCRIPT_CAPABILITIES) {
            "Granted script capability list exceeds the protocol limit"
        }
        requireValue(request.grantedCapabilities.distinct().size == request.grantedCapabilities.size) {
            "Granted script capability list contains duplicates"
        }
        val grantedHostMethods = request.grantedCapabilities.mapNotNull(JvmScriptCapability::hostMethod).toSet()
        requireValue(request.allowedHostCalls.toSet() == grantedHostMethods) {
            "Host-call allowlist differs from the granted host capabilities"
        }
    }

    @JvmStatic
    fun validateRequestAgainst(
        request: JvmSourceRequest,
        capabilities: JvmSourceCapabilities,
        negotiatedVersion: JvmProtocolVersion,
    ) {
        validateRequest(request)
        validateCapabilities(capabilities)
        validateProtocolVersion(negotiatedVersion, "negotiated")
        requireCapability(request.protocolVersion == negotiatedVersion) {
            "Request protocol version differs from the negotiated version"
        }
        requireCapability(request.language in capabilities.languages) {
            "Provider does not advertise the requested source language"
        }
        requireCapability(request.expectedToolchainFingerprint == capabilities.toolchainFingerprint) {
            "Requested toolchain fingerprint differs from the provider toolchain"
        }
        requireCapability(request.sourceSizeBytes <= capabilities.maxSourceBytes) {
            "Source exceeds the provider limit"
        }
        requireCapability(request.maxStdoutBytes <= capabilities.maxStdoutBytes) {
            "Requested stdout limit exceeds the provider limit"
        }
        requireCapability(request.maxStderrBytes <= capabilities.maxStderrBytes) {
            "Requested stderr limit exceeds the provider limit"
        }
        requireCapability(request.diagnosticByteLimit <= capabilities.maxDiagnosticBytes) {
            "Requested diagnostic limit exceeds the provider limit"
        }
        requireCapability(request.timeoutMillis <= capabilities.maxTimeoutMillis) {
            "Requested timeout exceeds the provider limit"
        }
        requireCapability(capabilities.scriptCapabilities.containsAll(request.grantedCapabilities)) {
            "Request grants a script capability the provider did not advertise"
        }
    }

    @JvmStatic
    fun validateResult(result: JvmSourceResult) {
        requireUtf8Text(
            result.resultJson,
            "Result JSON",
            JvmSourceContract.MAX_RESULT_JSON_BYTES,
        )
        JvmJsonValueValidator.validate(result.resultJson, "Result JSON")
        requireValue(result.classArtifactSizeBytes in 1L..JvmSourceContract.MAX_CLASS_ARTIFACT_BYTES) {
            "Result class artifact size is outside the protocol limit"
        }
        requireValue(result.dexArtifactSizeBytes in 1L..JvmSourceContract.MAX_DEX_ARTIFACT_BYTES) {
            "Result DEX artifact size is outside the protocol limit"
        }
        requireValue(JvmDexRuntimeProfile.isKnownVersion(result.dexVersion)) {
            "Result DEX version is outside the Protocol V1 profile"
        }
        requireValue(result.stdoutSizeBytes in 0L..JvmSourceContract.MAX_STDOUT_BYTES) {
            "Result stdout size is outside the protocol limit"
        }
        requireValue(result.stderrSizeBytes in 0L..JvmSourceContract.MAX_STDERR_BYTES) {
            "Result stderr size is outside the protocol limit"
        }
        requireValue(result.workerPid > 0) { "Worker PID must be positive" }
        requireValue(result.workerUid > 0) { "Worker UID must be positive" }
        validateProcessName(result.workerProcessName, "Worker process name")
        requireValue(result.workerGeneration > 0L) { "Worker generation must be positive" }
        requireValue(result.internalBridgeCallCount >= 0) { "Internal bridge call count is negative" }
        if (result.internalBridgeCallCount == 0) {
            requireValue(result.internalBridgeCallerPid == 0 && result.internalBridgeCallerUid == 0) {
                "Unused internal bridge telemetry must have zero caller identity"
            }
        } else {
            requireValue(result.internalBridgeCallerPid == result.workerPid &&
                result.internalBridgeCallerUid == result.workerUid
            ) {
                "Internal bridge caller identity differs from the worker identity"
            }
        }
        requireValue(result.compilationElapsedMillis >= 0L) { "Compilation elapsed time is negative" }
        requireValue(result.executionElapsedMillis >= 0L) { "Execution elapsed time is negative" }
        requireValue(result.elapsedMillis >= 0L) { "Total elapsed time is negative" }
        requireValue(result.compilationElapsedMillis <= result.elapsedMillis) {
            "Compilation elapsed time exceeds total elapsed time"
        }
        requireValue(result.executionElapsedMillis <= result.elapsedMillis - result.compilationElapsedMillis) {
            "Compilation and execution elapsed time exceed total elapsed time"
        }
    }

    @JvmStatic
    fun validateResultAgainst(result: JvmSourceResult, request: JvmSourceRequest) {
        validateResult(result)
        validateRequest(request)
        requireCapability(result.requestId == request.requestId) {
            "Result request ID differs from the submitted request"
        }
        requireCapability(result.toolchainFingerprint == request.expectedToolchainFingerprint) {
            "Result toolchain fingerprint differs from the requested toolchain"
        }
        requireCapability(result.dexVersion in JvmDexRuntimeProfile.admittedVersions(request.minApi)) {
            "Result DEX version violates the submitted request minApi profile"
        }
        requireCapability(result.stdoutSizeBytes <= request.maxStdoutBytes) {
            "Result stdout size exceeds the submitted request limit"
        }
        requireCapability(result.stderrSizeBytes <= request.maxStderrBytes) {
            "Result stderr size exceeds the submitted request limit"
        }
    }

    @JvmStatic
    fun validateResultAgainstRuntime(
        result: JvmSourceResult,
        request: JvmSourceRequest,
        runtimeApi: Int,
    ) {
        validateResultAgainst(result, request)
        requireCapability(runtimeApi >= request.minApi) {
            "Host runtime API is below the submitted request minApi"
        }
        requireCapability(result.dexVersion in JvmDexRuntimeProfile.admittedVersions(runtimeApi)) {
            "Result DEX version is unsupported by the host runtime API"
        }
        val requiredLoader = JvmDexRuntimeProfile.loaderKindForApi(runtimeApi)
        requireCapability(requiredLoader != null && result.loaderKind == requiredLoader) {
            "Result DEX loader kind differs from the host runtime API policy"
        }
    }

    @JvmStatic
    fun validateError(error: JvmSourceError) {
        requireCodePointLimit(
            error.message,
            "Error message",
            JvmSourceContract.MAX_ERROR_MESSAGE_CODE_POINTS,
        )
    }

    @JvmStatic
    fun validateCancellation(cancellation: JvmSourceCancellation) {
        requireValue(cancellation.elapsedMillis >= 0L) { "Cancellation elapsed time is negative" }
    }

    @JvmStatic
    fun validateHostCall(call: JvmHostCall) {
        requireValue(call.callId > 0L) { "Host call ID must be positive" }
        validateHostMethod(call.method)
        requireUtf8Text(
            call.payloadJson,
            "Host-call payload JSON",
            JvmSourceContract.MAX_HOST_PAYLOAD_BYTES,
        )
        JvmJsonValueValidator.validate(call.payloadJson, "Host-call payload JSON")
        requireValue(call.timeoutMillis in 1L..JvmSourceContract.MAX_TIMEOUT_MILLIS) {
            "Host-call timeout is invalid"
        }
    }

    @JvmStatic
    fun validateHostResponse(response: JvmHostResponse) {
        requireValue(response.callId > 0L) { "Host response call ID must be positive" }
        if (response.succeeded) {
            requireValue(response.payloadJson != null) { "Successful host response requires a payload" }
            requireValue(response.errorCode == null && response.errorMessage == null) {
                "Successful host response must not contain an error"
            }
        } else {
            requireValue(response.payloadJson == null) { "Failed host response must not contain a payload" }
            requireValue(response.errorCode != null && response.errorMessage != null) {
                "Failed host response requires an error code and message"
            }
            val errorCode = requireNotNull(response.errorCode)
            val errorMessage = requireNotNull(response.errorMessage)
            requireValue(ERROR_CODE.matches(errorCode)) { "Host response error code is invalid" }
            requireUtf8Text(
                errorCode,
                "Host response error code",
                JvmSourceContract.MAX_ERROR_CODE_BYTES,
            )
            requireCodePointLimit(
                errorMessage,
                "Host response error message",
                JvmSourceContract.MAX_ERROR_MESSAGE_CODE_POINTS,
            )
        }
        response.payloadJson?.let {
            requireUtf8Text(it, "Host response payload JSON", JvmSourceContract.MAX_HOST_PAYLOAD_BYTES)
            JvmJsonValueValidator.validate(it, "Host response payload JSON")
        }
    }

    internal fun validateProtocolRange(min: JvmProtocolVersion, max: JvmProtocolVersion, owner: String) {
        validateProtocolVersion(min, "$owner minimum")
        validateProtocolVersion(max, "$owner maximum")
        requireValue(min <= max) { "$owner protocol minimum exceeds maximum" }
    }

    internal fun validateProtocolVersion(version: JvmProtocolVersion, owner: String) {
        requireValue(version.major > 0 && version.minor >= 0) { "$owner protocol version is invalid" }
    }

    private fun validateSourceFileName(value: String) {
        requireUtf8Text(
            value,
            "Source file name",
            JvmSourceContract.MAX_SOURCE_FILE_NAME_BYTES,
        )
        requireValue(value !in setOf(".", "..") && '/' !in value && '\\' !in value && '\u0000' !in value) {
            "Source file name must be a single safe path segment"
        }
    }

    private fun validateHostMethod(value: String) {
        requireUtf8Text(value, "Host method", JvmSourceContract.MAX_HOST_METHOD_BYTES)
        requireValue(HOST_METHOD.matches(value)) { "Host method is invalid" }
    }

    private fun validateToolVersion(value: String, label: String) {
        requireUtf8Text(value, label, JvmSourceContract.MAX_VERSION_TEXT_BYTES)
        requireValue(TOOL_VERSION.matches(value)) { "$label is invalid" }
    }

    private fun validateProcessName(value: String, label: String) {
        requireUtf8Text(value, label, JvmSourceContract.MAX_PROCESS_NAME_BYTES)
        requireValue(PROCESS_NAME.matches(value)) { "$label is invalid" }
    }

    private fun requireDistinctNonEmpty(values: Collection<*>, label: String) {
        requireValue(values.isNotEmpty()) { "$label must not be empty" }
        requireValue(values.distinct().size == values.size) { "$label must not contain duplicates" }
    }

    private fun requireUtf8Text(value: String, label: String, maximumBytes: Int) {
        val encoded = try {
            StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(java.nio.CharBuffer.wrap(value))
        } catch (error: Throwable) {
            throw JvmSourceContractException(
                JvmSourceContractViolation.INVALID_VALUE,
                "$label is not valid UTF-8 text",
                error,
            )
        }
        requireValue(encoded.remaining() in 1..maximumBytes) { "$label exceeds its UTF-8 byte limit" }
    }

    private fun requireCodePointLimit(value: String, label: String, maximumCodePoints: Int) {
        requireUtf8Text(value, label, JvmSourceContract.MAX_METADATA_BYTES)
        requireValue(value.codePointCount(0, value.length) <= maximumCodePoints) {
            "$label exceeds its code-point limit"
        }
    }

    private inline fun requireValue(value: Boolean, message: () -> String) {
        if (!value) {
            throw JvmSourceContractException(JvmSourceContractViolation.INVALID_VALUE, message())
        }
    }

    private inline fun requireCapability(value: Boolean, message: () -> String) {
        if (!value) {
            throw JvmSourceContractException(
                JvmSourceContractViolation.CAPABILITY_INCOMPATIBLE,
                message(),
            )
        }
    }
}
