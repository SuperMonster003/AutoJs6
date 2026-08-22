package org.autojs.plugin.python.runtime.api

import java.util.ArrayList
import java.util.Collections

data class PythonRuntimeInfo(
    val protocolMin: PythonProtocolVersion,
    val protocolMax: PythonProtocolVersion,
    val providerId: String,
    val providerVersionName: String,
    val providerVersionCode: Long,
    val implementation: PythonImplementation,
    val pythonVersion: String,
    val isolationMode: PythonIsolationMode,
    val minHostVersionCode: Long? = null,
    val maxHostVersionCode: Long? = null,
)

data class PythonRuntimeResourceLimits(
    val maxSourceBytes: Long,
    val maxWorkspaceArchiveBytes: Long,
    val maxWorkspaceEntries: Int,
    val maxWorkspaceUncompressedBytes: Long,
    val maxStdinBytes: Long,
    val maxOutputBytes: Long,
    val maxOutputChunkBytes: Int,
    val maxOutputChunks: Long,
    val maxOutstandingOutputCredits: Int,
    val maxTimeoutMillis: Long,
    val maxConcurrentSessions: Int,
    val maxHostCapabilitySnapshotBytes: Long = 0L,
    val maxInputPromptBytes: Int = 0,
    val maxInputReplyBytes: Int = 0,
    val maxInputPrompts: Int = 0,
    val maxInputWaitMillis: Long = 0L,
    val maxStructuredJsonBytes: Int = 0,
    val maxOutputArtifacts: Int = 0,
    val maxOutputArtifactPathBytes: Int = 0,
    val maxOutputArtifactBytes: Long = 0L,
    val maxTotalOutputArtifactBytes: Long = 0L,
)

class PythonRuntimeCapabilities(
    val implementation: PythonImplementation,
    val pythonVersion: String,
    supportedAbis: Collection<String>,
    val supportsWorkspaceArchive: Boolean,
    val supportsStdinSnapshot: Boolean,
    val supportsStructuredTraceback: Boolean,
    val supportsCooperativeCancellation: Boolean,
    val cancellationMode: PythonCancellationMode,
    val limits: PythonRuntimeResourceLimits,
    val supportsHostCapabilitySnapshot: Boolean = false,
    val supportsModuleEntry: Boolean = false,
    val supportsInteractiveInput: Boolean = false,
    val supportsStructuredJsonResult: Boolean = false,
    val supportsOutputArtifacts: Boolean = false,
) {
    val supportedAbis: List<String> = immutableList(supportedAbis)

    override fun equals(other: Any?): Boolean = other is PythonRuntimeCapabilities &&
        implementation == other.implementation &&
        pythonVersion == other.pythonVersion &&
        supportedAbis == other.supportedAbis &&
        supportsWorkspaceArchive == other.supportsWorkspaceArchive &&
        supportsStdinSnapshot == other.supportsStdinSnapshot &&
        supportsStructuredTraceback == other.supportsStructuredTraceback &&
        supportsCooperativeCancellation == other.supportsCooperativeCancellation &&
        cancellationMode == other.cancellationMode &&
        limits == other.limits &&
        supportsHostCapabilitySnapshot == other.supportsHostCapabilitySnapshot &&
        supportsModuleEntry == other.supportsModuleEntry &&
        supportsInteractiveInput == other.supportsInteractiveInput &&
        supportsStructuredJsonResult == other.supportsStructuredJsonResult &&
        supportsOutputArtifacts == other.supportsOutputArtifacts

    override fun hashCode(): Int = listOf(
        implementation,
        pythonVersion,
        supportedAbis,
        supportsWorkspaceArchive,
        supportsStdinSnapshot,
        supportsStructuredTraceback,
        supportsCooperativeCancellation,
        cancellationMode,
        limits,
        supportsHostCapabilitySnapshot,
        supportsModuleEntry,
        supportsInteractiveInput,
        supportsStructuredJsonResult,
        supportsOutputArtifacts,
    ).hashCode()
}

data class PythonPayloadReference(
    val kind: PythonPayloadKind,
    val descriptorIndex: Int,
    val declaredLengthBytes: Long,
    val sha256: PythonSha256,
)

class PythonExecutionRequest(
    val requestId: PythonRequestId,
    val protocolVersion: PythonProtocolVersion,
    val entryPoint: String,
    val source: PythonPayloadReference,
    val workspaceArchive: PythonPayloadReference? = null,
    val stdin: PythonPayloadReference? = null,
    val hostCapabilitySnapshot: PythonPayloadReference? = null,
    arguments: Collection<String> = emptyList(),
    val timeoutMillis: Long = PythonRuntimeContract.DEFAULT_TIMEOUT_MILLIS,
    val maxOutputBytes: Long = PythonRuntimeContract.MAX_OUTPUT_BYTES,
    val maxOutputChunkBytes: Int = minOf(
        PythonRuntimeContract.MAX_OUTPUT_CHUNK_BYTES.toLong(),
        maxOutputBytes,
    ).toInt(),
    val maxOutputChunks: Long = minOf(PythonRuntimeContract.MAX_OUTPUT_CHUNKS, maxOutputBytes),
    val entryMode: PythonEntryMode = PythonEntryMode.FILE,
    val interactiveInput: PythonInteractiveInputPolicy? = null,
    val resultPolicy: PythonResultPolicy? = null,
) {
    val arguments: List<String> = immutableList(arguments)

    override fun equals(other: Any?): Boolean = other is PythonExecutionRequest &&
        requestId == other.requestId &&
        protocolVersion == other.protocolVersion &&
        entryPoint == other.entryPoint &&
        entryMode == other.entryMode &&
        interactiveInput == other.interactiveInput &&
        resultPolicy == other.resultPolicy &&
        source == other.source &&
        workspaceArchive == other.workspaceArchive &&
        stdin == other.stdin &&
        hostCapabilitySnapshot == other.hostCapabilitySnapshot &&
        arguments == other.arguments &&
        timeoutMillis == other.timeoutMillis &&
        maxOutputBytes == other.maxOutputBytes &&
        maxOutputChunkBytes == other.maxOutputChunkBytes &&
        maxOutputChunks == other.maxOutputChunks

    override fun hashCode(): Int = listOf(
        requestId,
        protocolVersion,
        entryPoint,
        entryMode,
        interactiveInput,
        resultPolicy,
        source,
        workspaceArchive,
        stdin,
        hostCapabilitySnapshot,
        arguments,
        timeoutMillis,
        maxOutputBytes,
        maxOutputChunkBytes,
        maxOutputChunks,
    ).hashCode()
}

data class PythonInteractiveInputPolicy(
    val maxPrompts: Int,
    val maxPromptBytes: Int,
    val maxReplyBytes: Int,
    val replyTimeoutMillis: Long,
)

/** Execution-scoped bounds for explicit JSON results and optional output descriptors. */
data class PythonResultPolicy(
    val maxStructuredJsonBytes: Int,
    val maxArtifacts: Int,
    val maxArtifactPathBytes: Int,
    val maxArtifactBytes: Long,
    val maxTotalArtifactBytes: Long,
)

data class PythonInputPrompt(
    val requestId: PythonRequestId,
    val promptId: PythonPromptId,
    val text: String,
    val echo: PythonInputEcho,
    val maxReplyBytes: Int,
    val replyTimeoutMillis: Long,
)

data class PythonInputReply(
    val requestId: PythonRequestId,
    val promptId: PythonPromptId,
    val status: PythonInputReplyStatus,
    val value: String? = null,
)

data class PythonSessionStarted(
    val requestId: PythonRequestId,
    val protocolVersion: PythonProtocolVersion,
    val pythonVersion: String,
    val firstOutputSequence: Long,
    val effectiveMaxOutputBytes: Long,
    val effectiveMaxOutputChunkBytes: Int,
    val effectiveMaxOutputChunks: Long,
    val runtimeGeneration: Long,
)

class PythonOutputChunk(
    val requestId: PythonRequestId,
    val sequence: Long,
    val stream: PythonOutputStream,
    data: ByteArray,
) {
    private val value = data.copyOf()

    val sizeBytes: Int
        get() = value.size

    fun toByteArray(): ByteArray = value.copyOf()

    override fun equals(other: Any?): Boolean = other is PythonOutputChunk &&
        requestId == other.requestId &&
        sequence == other.sequence &&
        stream == other.stream &&
        value.contentEquals(other.value)

    override fun hashCode(): Int = 31 * listOf(requestId, sequence, stream).hashCode() + value.contentHashCode()

    override fun toString(): String =
        "PythonOutputChunk(requestId=$requestId, sequence=$sequence, stream=$stream, sizeBytes=${value.size})"
}

class PythonExecutionResult(
    val requestId: PythonRequestId,
    val exitCode: Int,
    val elapsedMillis: Long,
    val stdoutBytes: Long,
    val stderrBytes: Long,
    val stdoutChunks: Long,
    val stderrChunks: Long,
    val structuredJson: String? = null,
    outputArtifacts: Collection<PythonOutputArtifact> = emptyList(),
) {
    val outputArtifacts: List<PythonOutputArtifact> = immutableList(outputArtifacts)

    override fun equals(other: Any?): Boolean = other is PythonExecutionResult &&
        requestId == other.requestId &&
        exitCode == other.exitCode &&
        elapsedMillis == other.elapsedMillis &&
        stdoutBytes == other.stdoutBytes &&
        stderrBytes == other.stderrBytes &&
        stdoutChunks == other.stdoutChunks &&
        stderrChunks == other.stderrChunks &&
        structuredJson == other.structuredJson &&
        outputArtifacts == other.outputArtifacts

    override fun hashCode(): Int = listOf(
        requestId,
        exitCode,
        elapsedMillis,
        stdoutBytes,
        stderrBytes,
        stdoutChunks,
        stderrChunks,
        structuredJson,
        outputArtifacts,
    ).hashCode()
}

/** Metadata for one result descriptor; bytes are never embedded in the result document. */
data class PythonOutputArtifact(
    val path: String,
    val descriptorIndex: Int,
    val declaredLengthBytes: Long,
    val sha256: PythonSha256,
)

data class PythonTracebackFrame(
    val origin: PythonTracebackOrigin,
    val fileName: String,
    val lineNumber: Int,
    val functionName: String,
    val sourceLine: String? = null,
)

class PythonExecutionError(
    val requestId: PythonRequestId,
    val code: PythonErrorCode,
    val phase: PythonFailurePhase,
    val message: String,
    val retryableBeforeDispatch: Boolean,
    val exceptionType: String? = null,
    val exceptionMessage: String? = null,
    traceback: Collection<PythonTracebackFrame> = emptyList(),
) {
    val traceback: List<PythonTracebackFrame> = immutableList(traceback)

    override fun equals(other: Any?): Boolean = other is PythonExecutionError &&
        requestId == other.requestId &&
        code == other.code &&
        phase == other.phase &&
        message == other.message &&
        retryableBeforeDispatch == other.retryableBeforeDispatch &&
        exceptionType == other.exceptionType &&
        exceptionMessage == other.exceptionMessage &&
        traceback == other.traceback

    override fun hashCode(): Int = listOf(
        requestId,
        code,
        phase,
        message,
        retryableBeforeDispatch,
        exceptionType,
        exceptionMessage,
        traceback,
    ).hashCode()
}

data class PythonExecutionCancellation(
    val requestId: PythonRequestId,
    val reason: PythonCancellationReason,
    val phase: PythonFailurePhase,
    val elapsedMillis: Long,
)

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
