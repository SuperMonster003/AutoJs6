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
    private val MODULE_SEGMENT = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /** Deterministically maps an admitted project file such as `pkg/main.py` to `pkg.main`. */
    fun moduleNameForFileEntryPoint(fileEntryPoint: String): String {
        validateLogicalRelativePath(
            fileEntryPoint,
            "Module source entry point",
            PythonRuntimeContract.MAX_ENTRY_POINT_BYTES,
        )
        requireValue(fileEntryPoint.endsWith(".py")) {
            "Module source entry point must use the exact lowercase .py extension"
        }
        val moduleSegments = fileEntryPoint.dropLast(3).split('/')
        requireValue(moduleSegments.none { '.' in it }) {
            "Module source entry point segments must not contain dots"
        }
        val moduleName = moduleSegments.joinToString(".")
        validateModuleEntryPoint(moduleName)
        return moduleName
    }

    /** Maps a validated dotted module name to the one SOURCE path materialized by the provider. */
    fun fileEntryPointForModuleName(moduleEntryPoint: String): String {
        validateModuleEntryPoint(moduleEntryPoint)
        return moduleEntryPoint.replace('.', '/') + ".py"
    }

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
        requireValue(!capabilities.supportsModuleEntry || capabilities.supportsWorkspaceArchive) {
            "Module entry support requires workspace archive support"
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
        if (capabilities.supportsModuleEntry) {
            requireCapability(runtimeInfo.protocolMax >= MODULE_ENTRY_PROTOCOL) {
                "Module entry support requires protocol 1.2 or newer"
            }
        }
        if (capabilities.supportsInteractiveInput) {
            requireCapability(runtimeInfo.protocolMax >= INTERACTIVE_INPUT_PROTOCOL) {
                "Interactive input support requires protocol 1.3 or newer"
            }
        }
        if (capabilities.supportsStructuredJsonResult || capabilities.supportsOutputArtifacts) {
            requireCapability(runtimeInfo.protocolMax >= STRUCTURED_RESULT_PROTOCOL) {
                "Structured result support requires protocol 1.4 or newer"
            }
        }
        if (capabilities.supportsHostCapabilityBroker) {
            requireCapability(runtimeInfo.protocolMax >= HOST_CAPABILITY_BROKER_PROTOCOL) {
                "Host capability broker support requires protocol 1.5 or newer"
            }
        }
        if (
            runtimeInfo.protocolMin <= HOST_CAPABILITY_BROKER_PROTOCOL &&
            runtimeInfo.protocolMax >= HOST_CAPABILITY_BROKER_PROTOCOL
        ) {
            requireCapability(capabilities.supportsHostCapabilityBroker) {
                "A runtime advertising protocol 1.5 requires host capability broker support"
            }
        }
    }

    fun validateRequest(request: PythonExecutionRequest) {
        validateProtocolVersion(request.protocolVersion, "request")
        validateEntryPoint(request.entryPoint, request.entryMode)
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
        if (request.entryMode == PythonEntryMode.MODULE) {
            requireValue(
                request.protocolVersion.major == MODULE_ENTRY_PROTOCOL.major &&
                    request.protocolVersion >= MODULE_ENTRY_PROTOCOL,
            ) { "Module entry requires Python Runtime protocol 1.2 or newer" }
            requireValue(request.workspaceArchive != null) {
                "Module entry requires a workspace archive"
            }
        }
        request.interactiveInput?.let { policy ->
            requireValue(
                request.protocolVersion.major == INTERACTIVE_INPUT_PROTOCOL.major &&
                    request.protocolVersion >= INTERACTIVE_INPUT_PROTOCOL,
            ) { "Interactive input requires Python Runtime protocol 1.3 or newer" }
            validateInteractiveInputPolicy(policy)
            requireValue(policy.replyTimeoutMillis <= request.timeoutMillis) {
                "Interactive input reply timeout exceeds the execution timeout"
            }
        }
        request.resultPolicy?.let { policy ->
            requireValue(
                request.protocolVersion.major == STRUCTURED_RESULT_PROTOCOL.major &&
                    request.protocolVersion >= STRUCTURED_RESULT_PROTOCOL,
            ) { "Structured results require Python Runtime protocol 1.4 or newer" }
            validateResultPolicy(policy)
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
        if (request.entryMode == PythonEntryMode.MODULE) {
            requireCapability(capabilities.supportsModuleEntry) {
                "Provider does not support module entry"
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
        request.interactiveInput?.let { policy ->
            requireCapability(capabilities.supportsInteractiveInput) {
                "Provider does not support interactive input"
            }
            requireCapability(policy.maxPrompts <= capabilities.limits.maxInputPrompts) {
                "Interactive input prompt count exceeds the provider limit"
            }
            requireCapability(policy.maxPromptBytes <= capabilities.limits.maxInputPromptBytes) {
                "Interactive input prompt bytes exceed the provider limit"
            }
            requireCapability(policy.maxReplyBytes <= capabilities.limits.maxInputReplyBytes) {
                "Interactive input reply bytes exceed the provider limit"
            }
            requireCapability(policy.replyTimeoutMillis <= capabilities.limits.maxInputWaitMillis) {
                "Interactive input wait exceeds the provider limit"
            }
        }
        request.resultPolicy?.let { policy ->
            if (policy.maxStructuredJsonBytes > 0) {
                requireCapability(capabilities.supportsStructuredJsonResult) {
                    "Provider does not support structured JSON results"
                }
                requireCapability(policy.maxStructuredJsonBytes <= capabilities.limits.maxStructuredJsonBytes) {
                    "Structured JSON result bytes exceed the provider limit"
                }
            }
            if (policy.maxArtifacts > 0) {
                requireCapability(capabilities.supportsOutputArtifacts) {
                    "Provider does not support output artifacts"
                }
                requireCapability(policy.maxArtifacts <= capabilities.limits.maxOutputArtifacts) {
                    "Output artifact count exceeds the provider limit"
                }
                requireCapability(
                    policy.maxArtifactPathBytes <= capabilities.limits.maxOutputArtifactPathBytes,
                ) { "Output artifact path bytes exceed the provider limit" }
                requireCapability(policy.maxArtifactBytes <= capabilities.limits.maxOutputArtifactBytes) {
                    "Output artifact bytes exceed the provider limit"
                }
                requireCapability(
                    policy.maxTotalArtifactBytes <= capabilities.limits.maxTotalOutputArtifactBytes,
                ) { "Aggregate output artifact bytes exceed the provider limit" }
            }
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

    fun validateInteractiveInputPolicy(policy: PythonInteractiveInputPolicy) {
        requireValue(policy.maxPrompts in 1..PythonRuntimeContract.MAX_INPUT_PROMPTS) {
            "Interactive input prompt count limit is invalid"
        }
        requireValue(policy.maxPromptBytes in 1..PythonRuntimeContract.MAX_INPUT_PROMPT_BYTES) {
            "Interactive input prompt byte limit is invalid"
        }
        requireValue(policy.maxReplyBytes in 1..PythonRuntimeContract.MAX_INPUT_REPLY_BYTES) {
            "Interactive input reply byte limit is invalid"
        }
        requireValue(policy.replyTimeoutMillis in 1L..PythonRuntimeContract.MAX_INPUT_WAIT_MILLIS) {
            "Interactive input reply timeout is invalid"
        }
    }

    fun validateInputPrompt(prompt: PythonInputPrompt) {
        requireUtf8Text(
            prompt.text,
            "Interactive input prompt",
            PythonRuntimeContract.MAX_INPUT_PROMPT_BYTES,
            allowEmpty = true,
        )
        requireValue(prompt.maxReplyBytes in 1..PythonRuntimeContract.MAX_INPUT_REPLY_BYTES) {
            "Interactive input prompt reply byte limit is invalid"
        }
        requireValue(prompt.replyTimeoutMillis in 1L..PythonRuntimeContract.MAX_INPUT_WAIT_MILLIS) {
            "Interactive input prompt timeout is invalid"
        }
    }

    fun validateInputReply(reply: PythonInputReply) {
        when (reply.status) {
            PythonInputReplyStatus.VALUE -> {
                requireValue(reply.value != null) { "Interactive input value reply is missing text" }
                requireUtf8Text(
                    reply.value!!,
                    "Interactive input reply",
                    PythonRuntimeContract.MAX_INPUT_REPLY_BYTES,
                    allowEmpty = true,
                )
            }
            PythonInputReplyStatus.EOF,
            PythonInputReplyStatus.CANCELLED,
            -> requireValue(reply.value == null) {
                "Non-value interactive input reply must not contain text"
            }
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
        result.structuredJson?.let(::validateStructuredJson)
        requireValue(result.outputArtifacts.size <= PythonRuntimeContract.MAX_OUTPUT_ARTIFACTS) {
            "Result output artifact count exceeds the protocol limit"
        }
        requireValue(result.outputArtifacts.map(PythonOutputArtifact::path).distinct().size == result.outputArtifacts.size) {
            "Result output artifact paths contain duplicates"
        }
        var totalArtifactBytes = 0L
        result.outputArtifacts.forEach { artifact ->
            validateOutputArtifact(artifact)
            totalArtifactBytes = Math.addExact(totalArtifactBytes, artifact.declaredLengthBytes)
            requireValue(totalArtifactBytes <= PythonRuntimeContract.MAX_TOTAL_OUTPUT_ARTIFACT_BYTES) {
                "Result aggregate output artifact bytes exceed the protocol limit"
            }
        }
    }

    fun validateResultPolicy(policy: PythonResultPolicy) {
        requireValue(policy.maxStructuredJsonBytes in 0..PythonRuntimeContract.MAX_STRUCTURED_JSON_BYTES) {
            "Structured JSON result limit is invalid"
        }
        val artifactsEnabled = policy.maxArtifacts > 0
        if (artifactsEnabled) {
            requireValue(policy.maxArtifacts <= PythonRuntimeContract.MAX_OUTPUT_ARTIFACTS) {
                "Output artifact count limit is invalid"
            }
            requireValue(
                policy.maxArtifactPathBytes in 1..PythonRuntimeContract.MAX_OUTPUT_ARTIFACT_PATH_BYTES,
            ) { "Output artifact path limit is invalid" }
            requireValue(policy.maxArtifactBytes in 0L..PythonRuntimeContract.MAX_OUTPUT_ARTIFACT_BYTES) {
                "Output artifact byte limit is invalid"
            }
            requireValue(
                policy.maxTotalArtifactBytes in 0L..PythonRuntimeContract.MAX_TOTAL_OUTPUT_ARTIFACT_BYTES,
            ) { "Aggregate output artifact byte limit is invalid" }
            requireValue(policy.maxArtifactBytes <= policy.maxTotalArtifactBytes) {
                "Per-artifact byte limit exceeds the aggregate limit"
            }
        } else {
            requireValue(
                policy.maxArtifacts == 0 &&
                    policy.maxArtifactPathBytes == 0 &&
                    policy.maxArtifactBytes == 0L &&
                    policy.maxTotalArtifactBytes == 0L,
            ) { "Disabled output artifacts must use zero limits" }
        }
        requireValue(policy.maxStructuredJsonBytes > 0 || artifactsEnabled) {
            "Result policy must enable structured JSON or output artifacts"
        }
    }

    fun validateOutputArtifact(artifact: PythonOutputArtifact) {
        validateOutputArtifactPath(artifact.path)
        requireValue(artifact.descriptorIndex >= 0) {
            "Output artifact descriptor index must not be negative"
        }
        requireValue(
            artifact.declaredLengthBytes in 0L..PythonRuntimeContract.MAX_OUTPUT_ARTIFACT_BYTES,
        ) { "Output artifact length exceeds the protocol limit" }
    }

    fun validateOutputArtifactPath(
        path: String,
        maximumBytes: Int = PythonRuntimeContract.MAX_OUTPUT_ARTIFACT_PATH_BYTES,
    ) {
        requireValue(maximumBytes in 1..PythonRuntimeContract.MAX_OUTPUT_ARTIFACT_PATH_BYTES) {
            "Output artifact path limit is invalid"
        }
        validateLogicalRelativePath(path, "Output artifact path", maximumBytes)
    }

    fun validateResultAgainstPolicy(
        result: PythonExecutionResult,
        policy: PythonResultPolicy?,
        descriptorCount: Int,
    ) {
        validateResult(result)
        if (policy == null) {
            requireValue(result.structuredJson == null && result.outputArtifacts.isEmpty()) {
                "Provider emitted explicit results without request authorization"
            }
            requireDescriptor(descriptorCount == 0) {
                "Result descriptors require request authorization"
            }
            return
        }
        validateResultPolicy(policy)
        result.structuredJson?.let { structuredJson ->
            requireValue(policy.maxStructuredJsonBytes > 0) {
                "Provider emitted structured JSON without request authorization"
            }
            requireValue(utf8Size(structuredJson, "Structured JSON result") <= policy.maxStructuredJsonBytes) {
                "Structured JSON result exceeds the request limit"
            }
        }
        requireValue(result.outputArtifacts.size <= policy.maxArtifacts) {
            "Output artifact count exceeds the request limit"
        }
        var totalBytes = 0L
        result.outputArtifacts.forEach { artifact ->
            requireValue(utf8Size(artifact.path, "Output artifact path") <= policy.maxArtifactPathBytes) {
                "Output artifact path exceeds the request limit"
            }
            requireValue(artifact.declaredLengthBytes <= policy.maxArtifactBytes) {
                "Output artifact exceeds the request byte limit"
            }
            totalBytes = Math.addExact(totalBytes, artifact.declaredLengthBytes)
            requireValue(totalBytes <= policy.maxTotalArtifactBytes) {
                "Output artifacts exceed the aggregate request limit"
            }
        }
        validateResultDescriptorReferences(result, descriptorCount)
    }

    fun validateResultDescriptorReferences(result: PythonExecutionResult, descriptorCount: Int) {
        requireDescriptor(descriptorCount >= 0) { "Result descriptor count must not be negative" }
        requireDescriptor(descriptorCount == result.outputArtifacts.size) {
            "Result descriptor count does not match the output artifact count"
        }
        val seen = BooleanArray(descriptorCount)
        result.outputArtifacts.forEach { artifact ->
            val index = artifact.descriptorIndex
            requireDescriptor(index in 0 until descriptorCount) {
                "Output artifact descriptor index is out of range"
            }
            requireDescriptor(!seen[index]) {
                "Output artifact descriptor index is referenced more than once"
            }
            seen[index] = true
        }
        requireDescriptor(seen.all { it }) {
            "Every result descriptor must be referenced exactly once"
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
            PythonErrorCode.INPUT_TIMEOUT,
            PythonErrorCode.INPUT_LIMIT_EXCEEDED,
            -> requireValue(error.phase == PythonFailurePhase.INTERACTIVE_INPUT) {
                "Interactive input failures must use the interactive-input phase"
            }
            PythonErrorCode.OUTPUT_ARTIFACT_REJECTED ->
                requireValue(error.phase == PythonFailurePhase.RESULT) {
                    "Output artifact failures must use the result phase"
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
        if (capabilities.supportsInteractiveInput) {
            requireValue(limits.maxInputPromptBytes in 1..PythonRuntimeContract.MAX_INPUT_PROMPT_BYTES) {
                "Interactive input prompt byte limit is invalid"
            }
            requireValue(limits.maxInputReplyBytes in 1..PythonRuntimeContract.MAX_INPUT_REPLY_BYTES) {
                "Interactive input reply byte limit is invalid"
            }
            requireValue(limits.maxInputPrompts in 1..PythonRuntimeContract.MAX_INPUT_PROMPTS) {
                "Interactive input prompt count limit is invalid"
            }
            requireValue(limits.maxInputWaitMillis in 1L..PythonRuntimeContract.MAX_INPUT_WAIT_MILLIS) {
                "Interactive input wait limit is invalid"
            }
        } else {
            requireValue(
                limits.maxInputPromptBytes == 0 &&
                    limits.maxInputReplyBytes == 0 &&
                    limits.maxInputPrompts == 0 &&
                    limits.maxInputWaitMillis == 0L,
            ) { "Unsupported interactive input must advertise zero limits" }
        }
        if (capabilities.supportsStructuredJsonResult) {
            requireValue(
                limits.maxStructuredJsonBytes in 1..PythonRuntimeContract.MAX_STRUCTURED_JSON_BYTES,
            ) { "Structured JSON result byte limit is invalid" }
        } else {
            requireValue(limits.maxStructuredJsonBytes == 0) {
                "Unsupported structured JSON results must advertise a zero limit"
            }
        }
        if (capabilities.supportsOutputArtifacts) {
            requireValue(limits.maxOutputArtifacts in 1..PythonRuntimeContract.MAX_OUTPUT_ARTIFACTS) {
                "Output artifact count limit is invalid"
            }
            requireValue(
                limits.maxOutputArtifactPathBytes in
                    1..PythonRuntimeContract.MAX_OUTPUT_ARTIFACT_PATH_BYTES,
            ) { "Output artifact path byte limit is invalid" }
            requireValue(
                limits.maxOutputArtifactBytes in 1L..PythonRuntimeContract.MAX_OUTPUT_ARTIFACT_BYTES,
            ) { "Output artifact byte limit is invalid" }
            requireValue(
                limits.maxTotalOutputArtifactBytes in
                    1L..PythonRuntimeContract.MAX_TOTAL_OUTPUT_ARTIFACT_BYTES,
            ) { "Aggregate output artifact byte limit is invalid" }
            requireValue(limits.maxOutputArtifactBytes <= limits.maxTotalOutputArtifactBytes) {
                "Per-artifact byte limit exceeds the aggregate provider limit"
            }
        } else {
            requireValue(
                limits.maxOutputArtifacts == 0 &&
                    limits.maxOutputArtifactPathBytes == 0 &&
                    limits.maxOutputArtifactBytes == 0L &&
                    limits.maxTotalOutputArtifactBytes == 0L,
            ) { "Unsupported output artifacts must advertise zero limits" }
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

    private fun validateEntryPoint(entryPoint: String, entryMode: PythonEntryMode) {
        when (entryMode) {
            PythonEntryMode.FILE -> {
                validateLogicalRelativePath(entryPoint, "Entry point", PythonRuntimeContract.MAX_ENTRY_POINT_BYTES)
                requireValue(entryPoint.endsWith(".py", ignoreCase = true)) {
                    "File entry point must use the .py extension"
                }
            }
            PythonEntryMode.MODULE -> validateModuleEntryPoint(entryPoint)
        }
    }

    private fun validateModuleEntryPoint(entryPoint: String) {
        requireUtf8Text(
            entryPoint,
            "Module entry point",
            PythonRuntimeContract.MAX_ENTRY_POINT_BYTES,
        )
        requireValue(Normalizer.normalize(entryPoint, Normalizer.Form.NFC) == entryPoint) {
            "Module entry point must use Unicode NFC normalization"
        }
        val segments = entryPoint.split('.')
        requireValue(segments.all(MODULE_SEGMENT::matches)) {
            "Module entry point must be a normalized dotted Python name"
        }
        requireValue(segments.none(PYTHON_KEYWORDS::contains)) {
            "Module entry point contains a Python keyword"
        }
        requireValue(segments.last() != "__init__") {
            "Module entry point must not target a package initializer"
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

    private fun validateStructuredJson(value: String) {
        requireUtf8Text(
            value,
            "Structured JSON result",
            PythonRuntimeContract.MAX_STRUCTURED_JSON_BYTES,
        )
        requireValue(runCatching { StrictJsonParser(value).validate() }.isSuccess) {
            "Structured JSON result is malformed"
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
    private val MODULE_ENTRY_PROTOCOL = PythonProtocolVersion(1, 2)
    private val INTERACTIVE_INPUT_PROTOCOL = PythonProtocolVersion(1, 3)
    private val STRUCTURED_RESULT_PROTOCOL = PythonProtocolVersion(1, 4)
    private val HOST_CAPABILITY_BROKER_PROTOCOL = PythonProtocolVersion(1, 5)
    private val PYTHON_KEYWORDS = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
        "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
        "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
        "try", "while", "with", "yield",
    )
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
        PythonErrorCode.INPUT_TIMEOUT,
        PythonErrorCode.INPUT_LIMIT_EXCEEDED,
        PythonErrorCode.OUTPUT_ARTIFACT_REJECTED,
    )
}

/** Dependency-free strict JSON grammar used by the shared JVM/Android protocol module. */
private class StrictJsonParser(private val text: String) {
    private var offset = 0

    fun validate() {
        whitespace()
        value(0)
        whitespace()
        require(offset == text.length)
    }

    private fun value(depth: Int) {
        require(depth <= MAX_DEPTH && offset < text.length)
        when (text[offset]) {
            '{' -> objectValue(depth + 1)
            '[' -> arrayValue(depth + 1)
            '"' -> stringValue()
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            '-', in '0'..'9' -> numberValue()
            else -> throw IllegalArgumentException("Invalid JSON value")
        }
    }

    private fun objectValue(depth: Int) {
        offset++
        whitespace()
        if (take('}')) return
        while (true) {
            stringValue()
            whitespace()
            require(take(':'))
            whitespace()
            value(depth)
            whitespace()
            if (take('}')) return
            require(take(','))
            whitespace()
        }
    }

    private fun arrayValue(depth: Int) {
        offset++
        whitespace()
        if (take(']')) return
        while (true) {
            value(depth)
            whitespace()
            if (take(']')) return
            require(take(','))
            whitespace()
        }
    }

    private fun stringValue() {
        require(take('"'))
        while (offset < text.length) {
            when (val character = text[offset++]) {
                '"' -> return
                '\\' -> {
                    require(offset < text.length)
                    when (text[offset++]) {
                        '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                        'u' -> repeat(4) {
                            require(offset < text.length && text[offset++].digitToIntOrNull(16) != null)
                        }
                        else -> throw IllegalArgumentException("Invalid JSON escape")
                    }
                }
                else -> require(character >= ' ')
            }
        }
        throw IllegalArgumentException("Unterminated JSON string")
    }

    private fun numberValue() {
        take('-')
        require(offset < text.length)
        if (take('0')) {
            require(offset >= text.length || text[offset] !in '0'..'9')
        } else {
            require(text[offset] in '1'..'9')
            while (offset < text.length && text[offset] in '0'..'9') offset++
        }
        if (take('.')) {
            require(offset < text.length && text[offset] in '0'..'9')
            while (offset < text.length && text[offset] in '0'..'9') offset++
        }
        if (offset < text.length && (text[offset] == 'e' || text[offset] == 'E')) {
            offset++
            if (offset < text.length && (text[offset] == '+' || text[offset] == '-')) offset++
            require(offset < text.length && text[offset] in '0'..'9')
            while (offset < text.length && text[offset] in '0'..'9') offset++
        }
    }

    private fun literal(value: String) {
        require(text.regionMatches(offset, value, 0, value.length))
        offset += value.length
    }

    private fun whitespace() {
        while (offset < text.length && text[offset] in JSON_WHITESPACE) offset++
    }

    private fun take(expected: Char): Boolean {
        if (offset >= text.length || text[offset] != expected) return false
        offset++
        return true
    }

    private companion object {
        const val MAX_DEPTH = 64
        val JSON_WHITESPACE = setOf(' ', '\t', '\n', '\r')
    }
}
