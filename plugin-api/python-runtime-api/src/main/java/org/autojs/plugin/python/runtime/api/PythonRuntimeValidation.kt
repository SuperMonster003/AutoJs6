package org.autojs.plugin.python.runtime.api

import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer

object PythonRuntimeNegotiation {
    val HOST_MIN = PythonProtocolVersion(PythonRuntimeContract.PROTOCOL_MAJOR, 0)
    val HOST_MAX = PythonProtocolVersion(
        PythonRuntimeContract.PROTOCOL_MAJOR,
        PythonRuntimeContract.PROTOCOL_MINOR,
    )

    fun negotiate(
        runtimeInfo: PythonRuntimeInfo,
        hostMin: PythonProtocolVersion = HOST_MIN,
        hostMax: PythonProtocolVersion = HOST_MAX,
    ): PythonProtocolVersion? {
        PythonRuntimeValidation.validateProtocolRange(hostMin, hostMax, "host")
        PythonRuntimeValidation.validateRuntimeInfo(runtimeInfo)
        val lower = maxOf(hostMin, runtimeInfo.protocolMin)
        val upper = minOf(hostMax, runtimeInfo.protocolMax)
        return upper.takeIf { lower <= upper }
    }

    fun requireNegotiated(
        runtimeInfo: PythonRuntimeInfo,
        hostMin: PythonProtocolVersion = HOST_MIN,
        hostMax: PythonProtocolVersion = HOST_MAX,
    ): PythonProtocolVersion = negotiate(runtimeInfo, hostMin, hostMax)
        ?: throw PythonRuntimeContractException(
            PythonRuntimeContractViolation.PROTOCOL_INCOMPATIBLE,
            "Host and Python runtime protocol ranges do not overlap",
        )
}

object PythonRuntimeValidation {
    private val PROVIDER_ID = Regex("[a-z0-9][a-z0-9._-]{0,127}")
    private val PYTHON_VERSION = Regex("[0-9]+(?:\\.[0-9]+){1,3}(?:[a-zA-Z0-9.+_-]*)?")
    private val ANDROID_ABI = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")

    fun validateRuntimeInfo(info: PythonRuntimeInfo) {
        validateProtocolRange(info.protocolMin, info.protocolMax, "runtime")
        requireValue(PROVIDER_ID.matches(info.providerId)) { "Provider ID is invalid" }
        requireUtf8Text(
            info.providerVersionName,
            "Provider version name",
            PythonRuntimeContract.MAX_VERSION_TEXT_BYTES,
        )
        requireValue(info.providerVersionCode > 0L) { "Provider version code must be positive" }
        validatePythonVersion(info.pythonVersion)
        requireValue(info.implementation == PythonImplementation.CPYTHON) {
            "Python Runtime protocol v1 supports CPython only"
        }
        requireValue(info.isolationMode == PythonIsolationMode.DEDICATED_PLUGIN_PROCESS) {
            "Python Runtime protocol v1 requires a dedicated plugin process"
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

    fun validateCapabilities(capabilities: PythonRuntimeCapabilities) {
        requireValue(capabilities.implementation == PythonImplementation.CPYTHON) {
            "Python Runtime protocol v1 supports CPython only"
        }
        validatePythonVersion(capabilities.pythonVersion)
        requireValue(capabilities.supportedAbis.isNotEmpty()) { "Supported ABI list must not be empty" }
        requireValue(capabilities.supportedAbis.size <= PythonRuntimeContract.MAX_ABIS) {
            "Supported ABI count exceeds the protocol limit"
        }
        requireValue(capabilities.supportedAbis.size == capabilities.supportedAbis.toSet().size) {
            "Supported ABI list contains duplicates"
        }
        capabilities.supportedAbis.forEach { abi ->
            requireValue(ANDROID_ABI.matches(abi)) { "Invalid Android ABI identifier: $abi" }
        }
        requireValue(capabilities.supportsStructuredTraceback) {
            "Python Runtime protocol v1 requires structured tracebacks"
        }
        requireValue(
            capabilities.supportsCooperativeCancellation ==
                (capabilities.cancellationMode == PythonCancellationMode.COOPERATIVE_THEN_PROCESS_RESTART),
        ) {
            "Cooperative cancellation capability does not match the cancellation mode"
        }
        validateLimits(capabilities.limits, capabilities)
    }

    fun validateCapabilitiesAgainstInfo(
        capabilities: PythonRuntimeCapabilities,
        runtimeInfo: PythonRuntimeInfo,
    ) {
        validateCapabilities(capabilities)
        validateRuntimeInfo(runtimeInfo)
        requireCapability(capabilities.implementation == runtimeInfo.implementation) {
            "Capabilities implementation does not match runtime info"
        }
        requireCapability(capabilities.pythonVersion == runtimeInfo.pythonVersion) {
            "Capabilities Python version does not match runtime info"
        }
        if (capabilities.supportsHostCapabilitySnapshot) {
            requireCapability(runtimeInfo.protocolMax >= HOST_CAPABILITY_SNAPSHOT_PROTOCOL) {
                "Host capability snapshot support requires protocol 1.1 or newer"
            }
        }
    }

    fun validateRequest(request: PythonExecutionRequest) {
        validateProtocolVersion(request.protocolVersion, "request")
        validateEntryPoint(request.entryPoint)
        validatePayloadReference(request.source)
        requireValue(request.source.kind == PythonPayloadKind.SOURCE) {
            "Source descriptor has the wrong payload kind"
        }
        request.workspaceArchive?.let {
            validatePayloadReference(it)
            requireValue(it.kind == PythonPayloadKind.WORKSPACE_ARCHIVE) {
                "Workspace descriptor has the wrong payload kind"
            }
            requireValue(it.declaredLengthBytes > 0L) { "Workspace archive must not be empty" }
        }
        request.stdin?.let {
            validatePayloadReference(it)
            requireValue(it.kind == PythonPayloadKind.STDIN) { "stdin descriptor has the wrong payload kind" }
        }
        request.hostCapabilitySnapshot?.let {
            validatePayloadReference(it)
            requireValue(it.kind == PythonPayloadKind.HOST_CAPABILITY_SNAPSHOT) {
                "Host capability snapshot descriptor has the wrong payload kind"
            }
            requireValue(
                request.protocolVersion.major == HOST_CAPABILITY_SNAPSHOT_PROTOCOL.major &&
                    request.protocolVersion >= HOST_CAPABILITY_SNAPSHOT_PROTOCOL,
            ) {
                "Host capability snapshots require Python Runtime protocol 1.1 or newer"
            }
            requireValue(it.declaredLengthBytes > 0L) { "Host capability snapshot must not be empty" }
        }
        requireValue(request.arguments.size <= PythonRuntimeContract.MAX_ARGUMENTS) {
            "Argument count exceeds the protocol limit"
        }
        var totalArgumentBytes = 0L
        request.arguments.forEachIndexed { index, argument ->
            val size = utf8Size(argument, "Argument $index")
            requireValue(size <= PythonRuntimeContract.MAX_ARGUMENT_BYTES) {
                "Argument $index exceeds the protocol limit"
            }
            totalArgumentBytes = Math.addExact(totalArgumentBytes, size.toLong())
            requireValue(totalArgumentBytes <= PythonRuntimeContract.MAX_TOTAL_ARGUMENT_BYTES) {
                "Arguments exceed the aggregate protocol limit"
            }
        }
        requireValue(request.timeoutMillis in 1L..PythonRuntimeContract.MAX_TIMEOUT_MILLIS) {
            "Execution timeout is invalid"
        }
        requireValue(request.maxOutputBytes in 1L..PythonRuntimeContract.MAX_OUTPUT_BYTES) {
            "Execution output limit is invalid"
        }
        requireValue(request.maxOutputChunkBytes in 1..PythonRuntimeContract.MAX_OUTPUT_CHUNK_BYTES) {
            "Execution output chunk limit is invalid"
        }
        requireValue(request.maxOutputChunkBytes.toLong() <= request.maxOutputBytes) {
            "Output chunk limit exceeds the total output limit"
        }
        requireValue(request.maxOutputChunks in 1L..PythonRuntimeContract.MAX_OUTPUT_CHUNKS) {
            "Execution output chunk count limit is invalid"
        }
        requireValue(request.maxOutputChunks <= request.maxOutputBytes) {
            "Output chunk count limit exceeds the non-empty output byte budget"
        }
    }

    fun validateRequestAgainst(
        request: PythonExecutionRequest,
        capabilities: PythonRuntimeCapabilities,
        negotiatedVersion: PythonProtocolVersion,
        descriptorCount: Int,
    ) {
        validateRequest(request)
        validateCapabilities(capabilities)
        validateProtocolVersion(negotiatedVersion, "negotiated")
        requireCapability(request.protocolVersion == negotiatedVersion) {
            "Request protocol version was not negotiated"
        }
        requireCapability(request.source.declaredLengthBytes <= capabilities.limits.maxSourceBytes) {
            "Python source exceeds the provider limit"
        }
        request.workspaceArchive?.let {
            requireCapability(capabilities.supportsWorkspaceArchive) {
                "Provider does not support workspace archives"
            }
            requireCapability(it.declaredLengthBytes <= capabilities.limits.maxWorkspaceArchiveBytes) {
                "Workspace archive exceeds the provider limit"
            }
        }
        request.stdin?.let {
            requireCapability(capabilities.supportsStdinSnapshot) {
                "Provider does not support stdin snapshots"
            }
            requireCapability(it.declaredLengthBytes <= capabilities.limits.maxStdinBytes) {
                "stdin snapshot exceeds the provider limit"
            }
        }
        request.hostCapabilitySnapshot?.let {
            requireCapability(capabilities.supportsHostCapabilitySnapshot) {
                "Provider does not support host capability snapshots"
            }
            requireCapability(
                it.declaredLengthBytes <= capabilities.limits.maxHostCapabilitySnapshotBytes,
            ) { "Host capability snapshot exceeds the provider limit" }
        }
        requireCapability(request.timeoutMillis <= capabilities.limits.maxTimeoutMillis) {
            "Execution timeout exceeds the provider limit"
        }
        requireCapability(request.maxOutputBytes <= capabilities.limits.maxOutputBytes) {
            "Execution output limit exceeds the provider limit"
        }
        requireCapability(request.maxOutputChunkBytes <= capabilities.limits.maxOutputChunkBytes) {
            "Execution output chunk limit exceeds the provider limit"
        }
        requireCapability(request.maxOutputChunks <= capabilities.limits.maxOutputChunks) {
            "Execution output chunk count limit exceeds the provider limit"
        }
        validateDescriptorReferences(request, descriptorCount)
    }

    fun validateDescriptorReferences(request: PythonExecutionRequest, descriptorCount: Int) {
        requireDescriptor(descriptorCount >= 0) { "Descriptor count must not be negative" }
        val references = listOfNotNull(
            request.source,
            request.workspaceArchive,
            request.stdin,
            request.hostCapabilitySnapshot,
        )
        requireDescriptor(descriptorCount == references.size) {
            "Supplied descriptor count does not match the reference count"
        }
        val seen = BooleanArray(descriptorCount)
        references.forEach { reference ->
            val index = reference.descriptorIndex
            requireDescriptor(index in 0 until descriptorCount) { "Descriptor index is out of range" }
            requireDescriptor(!seen[index]) { "Descriptor index is referenced more than once" }
            seen[index] = true
        }
        requireDescriptor(seen.all { it }) { "Every supplied descriptor must be referenced exactly once" }
    }

    fun validateStarted(started: PythonSessionStarted) {
        validateProtocolVersion(started.protocolVersion, "started")
        validatePythonVersion(started.pythonVersion)
        requireValue(started.firstOutputSequence >= 0L) { "First output sequence must not be negative" }
        requireValue(started.effectiveMaxOutputBytes in 1L..PythonRuntimeContract.MAX_OUTPUT_BYTES) {
            "Effective output limit is invalid"
        }
        requireValue(started.effectiveMaxOutputChunkBytes in 1..PythonRuntimeContract.MAX_OUTPUT_CHUNK_BYTES) {
            "Effective output chunk limit is invalid"
        }
        requireValue(started.effectiveMaxOutputChunkBytes.toLong() <= started.effectiveMaxOutputBytes) {
            "Effective output chunk limit exceeds the total output limit"
        }
        requireValue(started.effectiveMaxOutputChunks in 1L..PythonRuntimeContract.MAX_OUTPUT_CHUNKS) {
            "Effective output chunk count limit is invalid"
        }
        requireValue(started.effectiveMaxOutputChunks <= started.effectiveMaxOutputBytes) {
            "Effective output chunk count limit exceeds the non-empty output byte budget"
        }
        requireValue(started.runtimeGeneration > 0L) { "Runtime generation must be positive" }
    }

    fun validateOutputChunk(chunk: PythonOutputChunk) {
        requireValue(chunk.sequence >= 0L) { "Output sequence must not be negative" }
        requireValue(chunk.sizeBytes in 1..PythonRuntimeContract.MAX_OUTPUT_CHUNK_BYTES) {
            "Output chunk size is invalid"
        }
    }

    fun validateResult(result: PythonExecutionResult) {
        requireValue(result.exitCode in 0..255) { "Python exit code is invalid" }
        requireValue(result.elapsedMillis >= 0L) { "Result elapsed time must not be negative" }
        requireValue(result.stdoutBytes >= 0L && result.stderrBytes >= 0L) {
            "Result output byte counts must not be negative"
        }
        requireValue(result.stdoutChunks >= 0L && result.stderrChunks >= 0L) {
            "Result output chunk counts must not be negative"
        }
        requireValue(result.stdoutChunks <= result.stdoutBytes && result.stderrChunks <= result.stderrBytes) {
            "Non-empty output chunk counts exceed their byte counts"
        }
        requireValue(
            result.stdoutChunks <= PythonRuntimeContract.MAX_OUTPUT_CHUNKS - result.stderrChunks,
        ) { "Result aggregate output chunk count exceeds the protocol limit" }
        requireValue(result.stdoutBytes <= PythonRuntimeContract.MAX_OUTPUT_BYTES) {
            "Result stdout byte count exceeds the protocol limit"
        }
        requireValue(result.stderrBytes <= PythonRuntimeContract.MAX_OUTPUT_BYTES - result.stdoutBytes) {
            "Result aggregate output byte count exceeds the protocol limit"
        }
    }

    fun validateError(error: PythonExecutionError) {
        requireUtf8Text(error.message, "Error message", PythonRuntimeContract.MAX_ERROR_MESSAGE_BYTES)
        requireValue(error.traceback.size <= PythonRuntimeContract.MAX_TRACEBACK_FRAMES) {
            "Traceback frame count exceeds the protocol limit"
        }
        val pythonException = error.code == PythonErrorCode.PYTHON_EXCEPTION
        requireValue((error.exceptionType != null) == pythonException) {
            "Python exception type presence does not match the error code"
        }
        requireValue((error.exceptionMessage != null) == pythonException) {
            "Python exception message presence does not match the error code"
        }
        requireValue(error.traceback.isEmpty() || pythonException) {
            "Traceback frames are only valid for Python exceptions"
        }
        if (pythonException) {
            requireUtf8Text(
                error.exceptionType!!,
                "Python exception type",
                PythonRuntimeContract.MAX_EXCEPTION_TEXT_BYTES,
            )
            requireUtf8Text(
                error.exceptionMessage!!,
                "Python exception message",
                PythonRuntimeContract.MAX_EXCEPTION_TEXT_BYTES,
                allowEmpty = true,
            )
        }
        error.traceback.forEachIndexed { index, frame -> validateTracebackFrame(frame, index) }
    }

    fun validateExecutionError(error: PythonExecutionError) {
        validateError(error)
        requireValue(error.code in EXECUTION_ERROR_CODES) {
            "Error code is not valid after execution dispatch"
        }
        requireValue(
            error.phase != PythonFailurePhase.NEGOTIATION &&
                error.phase != PythonFailurePhase.SESSION_OPEN,
        ) { "Execution error phase is pre-dispatch only" }
        when (error.code) {
            PythonErrorCode.SOURCE_REJECTED,
            PythonErrorCode.WORKSPACE_REJECTED,
            PythonErrorCode.STDIN_REJECTED,
            PythonErrorCode.HOST_CAPABILITY_SNAPSHOT_REJECTED,
            -> requireValue(error.phase == PythonFailurePhase.INPUT_VALIDATION) {
                "Rejected execution input must use the input-validation phase"
            }
            PythonErrorCode.PYTHON_EXCEPTION -> requireValue(error.phase == PythonFailurePhase.EXECUTION) {
                "Python exceptions must use the execution phase"
            }
            PythonErrorCode.OUTPUT_LIMIT_EXCEEDED -> requireValue(error.phase == PythonFailurePhase.OUTPUT) {
                "Output limit failures must use the output phase"
            }
            PythonErrorCode.CANCELLATION_FAILED -> requireValue(error.phase == PythonFailurePhase.CANCELLATION) {
                "Cancellation failures must use the cancellation phase"
            }
            PythonErrorCode.TIMEOUT,
            PythonErrorCode.RUNTIME_CRASHED,
            PythonErrorCode.STORAGE_EXHAUSTED,
            PythonErrorCode.INTERNAL,
            -> Unit
            else -> error("Execution error code set is out of sync")
        }
        requireValue(!error.retryableBeforeDispatch) {
            "A dispatched execution error must not advertise pre-dispatch retry safety"
        }
    }

    fun validateAdmissionError(error: PythonExecutionError) {
        validateError(error)
        requireValue(error.code in ADMISSION_ERROR_CODES) {
            "Error code is not valid for session admission"
        }
        requireValue(error.phase == PythonFailurePhase.NEGOTIATION || error.phase == PythonFailurePhase.SESSION_OPEN) {
            "Admission error phase is invalid"
        }
        when (error.code) {
            PythonErrorCode.UNSUPPORTED_PROTOCOL,
            PythonErrorCode.UNSUPPORTED_CAPABILITY,
            -> requireValue(error.phase == PythonFailurePhase.NEGOTIATION) {
                "Compatibility rejection must use the negotiation phase"
            }
            PythonErrorCode.BUSY,
            PythonErrorCode.SESSION_OPEN_FAILED,
            -> requireValue(error.phase == PythonFailurePhase.SESSION_OPEN) {
                "Session admission rejection must use the session-open phase"
            }
            PythonErrorCode.INVALID_REQUEST,
            PythonErrorCode.INTERNAL,
            -> Unit
            else -> error("Admission error code set is out of sync")
        }
        requireValue(error.code != PythonErrorCode.PYTHON_EXCEPTION && error.traceback.isEmpty()) {
            "Admission rejection cannot contain a Python exception"
        }
    }

    fun validateCancellation(cancellation: PythonExecutionCancellation) {
        requireValue(cancellation.elapsedMillis >= 0L) { "Cancellation elapsed time must not be negative" }
        requireValue(cancellation.phase == PythonFailurePhase.CANCELLATION) {
            "Cancellation terminal must use the cancellation phase"
        }
    }

    fun validateProtocolRange(min: PythonProtocolVersion, max: PythonProtocolVersion, label: String) {
        validateProtocolVersion(min, "$label minimum")
        validateProtocolVersion(max, "$label maximum")
        requireValue(min <= max) { "$label protocol range is reversed" }
    }

    fun validateProtocolVersion(version: PythonProtocolVersion, label: String) {
        requireValue(version.major > 0 && version.minor >= 0) { "$label protocol version is invalid" }
    }

    private fun validateLimits(
        limits: PythonRuntimeResourceLimits,
        capabilities: PythonRuntimeCapabilities,
    ) {
        requireValue(limits.maxSourceBytes in 1L..PythonRuntimeContract.MAX_SOURCE_BYTES) {
            "Source byte limit is invalid"
        }
        if (capabilities.supportsWorkspaceArchive) {
            requireValue(limits.maxWorkspaceArchiveBytes in 1L..PythonRuntimeContract.MAX_WORKSPACE_ARCHIVE_BYTES) {
                "Workspace archive byte limit is invalid"
            }
            requireValue(limits.maxWorkspaceEntries in 1..PythonRuntimeContract.MAX_WORKSPACE_ENTRIES) {
                "Workspace entry limit is invalid"
            }
            requireValue(
                limits.maxWorkspaceUncompressedBytes in 1L..PythonRuntimeContract.MAX_WORKSPACE_UNCOMPRESSED_BYTES,
            ) { "Workspace uncompressed byte limit is invalid" }
        } else {
            requireValue(
                limits.maxWorkspaceArchiveBytes == 0L &&
                    limits.maxWorkspaceEntries == 0 &&
                    limits.maxWorkspaceUncompressedBytes == 0L,
            ) { "Unsupported workspace transport must advertise zero limits" }
        }
        if (capabilities.supportsStdinSnapshot) {
            requireValue(limits.maxStdinBytes in 1L..PythonRuntimeContract.MAX_STDIN_BYTES) {
                "stdin byte limit is invalid"
            }
        } else {
            requireValue(limits.maxStdinBytes == 0L) { "Unsupported stdin transport must advertise a zero limit" }
        }
        if (capabilities.supportsHostCapabilitySnapshot) {
            requireValue(
                limits.maxHostCapabilitySnapshotBytes in
                    1L..PythonRuntimeContract.MAX_HOST_CAPABILITY_SNAPSHOT_BYTES,
            ) { "Host capability snapshot byte limit is invalid" }
        } else {
            requireValue(limits.maxHostCapabilitySnapshotBytes == 0L) {
                "Unsupported host capability snapshot transport must advertise a zero limit"
            }
        }
        requireValue(limits.maxOutputBytes in 1L..PythonRuntimeContract.MAX_OUTPUT_BYTES) {
            "Output byte limit is invalid"
        }
        requireValue(limits.maxOutputChunkBytes in 1..PythonRuntimeContract.MAX_OUTPUT_CHUNK_BYTES) {
            "Output chunk byte limit is invalid"
        }
        requireValue(limits.maxOutputChunkBytes.toLong() <= limits.maxOutputBytes) {
            "Output chunk byte limit exceeds the total output limit"
        }
        requireValue(limits.maxOutputChunks in 1L..PythonRuntimeContract.MAX_OUTPUT_CHUNKS) {
            "Output chunk count limit is invalid"
        }
        requireValue(limits.maxOutputChunks <= limits.maxOutputBytes) {
            "Output chunk count limit exceeds the non-empty output byte budget"
        }
        requireValue(
            limits.maxOutstandingOutputCredits in 1..PythonRuntimeContract.MAX_OUTSTANDING_OUTPUT_CREDITS,
        ) { "Output credit limit is invalid" }
        requireValue(limits.maxTimeoutMillis in 1L..PythonRuntimeContract.MAX_TIMEOUT_MILLIS) {
            "Timeout limit is invalid"
        }
        requireValue(limits.maxConcurrentSessions == PythonRuntimeContract.MAX_CONCURRENT_SESSIONS) {
            "Python Runtime protocol v1 permits exactly one concurrent session"
        }
    }

    private fun validatePayloadReference(reference: PythonPayloadReference) {
        requireValue(reference.descriptorIndex >= 0) { "Descriptor index must not be negative" }
        requireValue(reference.declaredLengthBytes >= 0L) { "Declared payload length must not be negative" }
        val maximum = when (reference.kind) {
            PythonPayloadKind.SOURCE -> PythonRuntimeContract.MAX_SOURCE_BYTES
            PythonPayloadKind.WORKSPACE_ARCHIVE -> PythonRuntimeContract.MAX_WORKSPACE_ARCHIVE_BYTES
            PythonPayloadKind.STDIN -> PythonRuntimeContract.MAX_STDIN_BYTES
            PythonPayloadKind.HOST_CAPABILITY_SNAPSHOT ->
                PythonRuntimeContract.MAX_HOST_CAPABILITY_SNAPSHOT_BYTES
        }
        requireValue(reference.declaredLengthBytes <= maximum) { "Declared payload length exceeds the protocol limit" }
    }

    private fun validateEntryPoint(entryPoint: String) {
        validateLogicalRelativePath(entryPoint, "Entry point", PythonRuntimeContract.MAX_ENTRY_POINT_BYTES)
        requireValue(entryPoint.endsWith(".py", ignoreCase = true)) {
            "Entry point must use the .py extension"
        }
    }

    private fun validateTracebackFrame(frame: PythonTracebackFrame, index: Int) {
        val fileLabel = "Traceback frame $index file name"
        when (frame.origin) {
            PythonTracebackOrigin.PROJECT,
            PythonTracebackOrigin.STDLIB,
            PythonTracebackOrigin.PACKAGE,
            -> validateLogicalRelativePath(
                frame.fileName,
                fileLabel,
                PythonRuntimeContract.MAX_TRACEBACK_TEXT_BYTES,
            )
            PythonTracebackOrigin.GENERATED -> {
                requireUtf8Text(frame.fileName, fileLabel, PythonRuntimeContract.MAX_TRACEBACK_TEXT_BYTES)
                requireValue(frame.fileName.startsWith('<') && frame.fileName.endsWith('>')) {
                    "$fileLabel must be a synthetic name for generated code"
                }
                requireValue(
                    frame.fileName.none { it == '/' || it == '\\' || Character.isISOControl(it) },
                ) { "$fileLabel contains an unsafe synthetic name" }
            }
        }
        requireValue(frame.lineNumber > 0) { "Traceback frame $index line number must be positive" }
        requireUtf8Text(
            frame.functionName,
            "Traceback frame $index function name",
            PythonRuntimeContract.MAX_TRACEBACK_TEXT_BYTES,
        )
        frame.sourceLine?.let {
            requireUtf8Text(
                it,
                "Traceback frame $index source line",
                PythonRuntimeContract.MAX_TRACEBACK_TEXT_BYTES,
                allowEmpty = true,
            )
        }
    }

    private fun validatePythonVersion(version: String) {
        requireValue(PYTHON_VERSION.matches(version)) { "Python version is invalid" }
        requireValue(utf8Size(version, "Python version") <= PythonRuntimeContract.MAX_VERSION_TEXT_BYTES) {
            "Python version exceeds the protocol limit"
        }
    }

    private fun validateLogicalRelativePath(value: String, label: String, maxBytes: Int) {
        requireValue(value.isNotBlank()) { "$label must not be blank" }
        requireValue(utf8Size(value, label) <= maxBytes) { "$label exceeds the protocol limit" }
        requireValue('\\' !in value && value.none(Character::isISOControl)) {
            "$label must use normalized forward slashes without control characters"
        }
        requireValue(Normalizer.normalize(value, Normalizer.Form.NFC) == value) {
            "$label must use Unicode NFC normalization"
        }
        requireValue(!value.startsWith('/') && !WINDOWS_DRIVE_PREFIX.matches(value)) {
            "$label must be project-relative"
        }
        val segments = value.split('/')
        requireValue(segments.none { it.isEmpty() || it == "." || it == ".." }) {
            "$label contains an unsafe path segment"
        }
    }

    private fun requireUtf8Text(value: String, label: String, maxBytes: Int, allowEmpty: Boolean = false) {
        requireValue(allowEmpty || value.isNotBlank()) { "$label must not be blank" }
        requireValue(utf8Size(value, label) <= maxBytes) { "$label exceeds $maxBytes UTF-8 bytes" }
    }

    private fun utf8Size(value: String, label: String): Int = try {
        StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(java.nio.CharBuffer.wrap(value))
            .remaining()
    } catch (error: Exception) {
        throw PythonRuntimeContractException(
            PythonRuntimeContractViolation.INVALID_VALUE,
            "$label is not valid Unicode text",
            error,
        )
    }

    private inline fun requireValue(condition: Boolean, lazyMessage: () -> String) {
        if (!condition) {
            throw PythonRuntimeContractException(
                PythonRuntimeContractViolation.INVALID_VALUE,
                lazyMessage(),
            )
        }
    }

    private inline fun requireCapability(condition: Boolean, lazyMessage: () -> String) {
        if (!condition) {
            throw PythonRuntimeContractException(
                PythonRuntimeContractViolation.CAPABILITY_INCOMPATIBLE,
                lazyMessage(),
            )
        }
    }

    private inline fun requireDescriptor(condition: Boolean, lazyMessage: () -> String) {
        if (!condition) {
            throw PythonRuntimeContractException(
                PythonRuntimeContractViolation.DESCRIPTOR_MISMATCH,
                lazyMessage(),
            )
        }
    }

    private val WINDOWS_DRIVE_PREFIX = Regex("^[a-zA-Z]:.*")
    private val HOST_CAPABILITY_SNAPSHOT_PROTOCOL = PythonProtocolVersion(1, 1)
    private val ADMISSION_ERROR_CODES = setOf(
        PythonErrorCode.INVALID_REQUEST,
        PythonErrorCode.UNSUPPORTED_PROTOCOL,
        PythonErrorCode.UNSUPPORTED_CAPABILITY,
        PythonErrorCode.BUSY,
        PythonErrorCode.SESSION_OPEN_FAILED,
        PythonErrorCode.INTERNAL,
    )
    private val EXECUTION_ERROR_CODES = setOf(
        PythonErrorCode.SOURCE_REJECTED,
        PythonErrorCode.WORKSPACE_REJECTED,
        PythonErrorCode.STDIN_REJECTED,
        PythonErrorCode.HOST_CAPABILITY_SNAPSHOT_REJECTED,
        PythonErrorCode.PYTHON_EXCEPTION,
        PythonErrorCode.TIMEOUT,
        PythonErrorCode.OUTPUT_LIMIT_EXCEEDED,
        PythonErrorCode.CANCELLATION_FAILED,
        PythonErrorCode.RUNTIME_CRASHED,
        PythonErrorCode.STORAGE_EXHAUSTED,
        PythonErrorCode.INTERNAL,
    )
}
