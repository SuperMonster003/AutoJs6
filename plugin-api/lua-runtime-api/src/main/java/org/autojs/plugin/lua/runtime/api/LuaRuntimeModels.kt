package org.autojs.plugin.lua.runtime.api

import java.util.ArrayList
import java.util.Collections

data class LuaRuntimeLimits(
    val maxSourceBytes: Long,
    val maxMemoryBytes: Long,
    val maxOutputBytes: Long,
    val maxExecutionMillis: Long,
    val maxConcurrentExecutions: Int,
)

class LuaRuntimeInfo(
    val protocolMin: LuaProtocolVersion,
    val protocolMax: LuaProtocolVersion,
    val providerId: String,
    val providerVersionName: String,
    val providerVersionCode: Long,
    val runtimeFamily: LuaRuntimeFamily,
    val runtimeSlot: String,
    val languageVersion: String,
    val processAbi: String,
    supportedAbis: Collection<String>,
    capabilities: Collection<String>,
    val limits: LuaRuntimeLimits,
    val minHostVersionCode: Long? = null,
    val maxHostVersionCode: Long? = null,
) {
    val supportedAbis: List<String> = immutableList(supportedAbis)
    val capabilities: List<String> = immutableList(capabilities)

    override fun toString(): String =
        "LuaRuntimeInfo(providerId=$providerId, providerVersion=$providerVersionName, " +
            "runtimeSlot=$runtimeSlot, languageVersion=$languageVersion, abis=${supportedAbis.size}, " +
            "processAbi=$processAbi, capabilities=${capabilities.size})"
}

class LuaExecutionRequest(
    val requestId: LuaRequestId,
    val protocolVersion: LuaProtocolVersion,
    val sourceName: String,
    val sourceLengthBytes: Long,
    val sourceSha256: LuaSha256,
    val arguments: LuaValue = LuaValue.Nil,
    requiredCapabilities: Collection<String> = emptyList(),
    val outputByteLimit: Long = LuaRuntimeContract.DEFAULT_OUTPUT_BYTES,
    val memoryByteLimit: Long = LuaRuntimeContract.DEFAULT_MEMORY_BYTES,
    val timeoutMillis: Long = LuaRuntimeContract.DEFAULT_TIMEOUT_MILLIS,
) {
    val requiredCapabilities: List<String> = immutableList(requiredCapabilities)

    override fun toString(): String =
        "LuaExecutionRequest(requestId=$requestId, sourceNameUtf8Bytes=${sourceName.toByteArray(Charsets.UTF_8).size}, " +
            "sourceLengthBytes=$sourceLengthBytes, arguments=$arguments, " +
            "requiredCapabilities=${requiredCapabilities.size}, outputByteLimit=$outputByteLimit, " +
            "memoryByteLimit=$memoryByteLimit, timeoutMillis=$timeoutMillis)"
}

data class LuaExecutionStarted(
    val requestId: LuaRequestId,
    val protocolVersion: LuaProtocolVersion,
    val runtimeSlot: String,
    val languageVersion: String,
    val firstOutputSequence: Long,
    val queueElapsedMillis: Long,
)

class LuaOutputChunk(
    val requestId: LuaRequestId,
    val sequence: Long,
    val stream: LuaOutputStream,
    val text: String,
) {
    override fun equals(other: Any?): Boolean = other is LuaOutputChunk &&
        requestId == other.requestId && sequence == other.sequence && stream == other.stream && text == other.text

    override fun hashCode(): Int = arrayOf(requestId, sequence, stream, text).contentHashCode()

    override fun toString(): String =
        "LuaOutputChunk(requestId=$requestId, sequence=$sequence, stream=$stream, " +
            "utf8Bytes=${text.toByteArray(Charsets.UTF_8).size})"
}

class LuaExecutionResult(
    val requestId: LuaRequestId,
    val value: LuaValue,
    val elapsedMillis: Long,
) {
    override fun toString(): String =
        "LuaExecutionResult(requestId=$requestId, value=$value, elapsedMillis=$elapsedMillis)"
}

class LuaExecutionError(
    val requestId: LuaRequestId,
    val code: LuaExecutionErrorCode,
    val phase: LuaExecutionFailurePhase,
    val message: String,
    val retryDisposition: LuaRetryDisposition,
) {
    override fun toString(): String =
        "LuaExecutionError(requestId=$requestId, code=$code, phase=$phase, " +
            "messageCodePoints=${message.codePointCount(0, message.length)}, retryDisposition=$retryDisposition)"
}

data class LuaExecutionCancellation(
    val requestId: LuaRequestId,
    val reason: LuaCancellationReason,
    val elapsedMillis: Long,
)

class LuaHostCallRequest(
    val executionId: LuaRequestId,
    val callId: String,
    val capability: String,
    val arguments: LuaValue,
) {
    override fun toString(): String =
        "LuaHostCallRequest(executionId=$executionId, callIdUtf8Bytes=${callId.toByteArray(Charsets.UTF_8).size}, " +
            "capability=$capability, arguments=$arguments)"
}

class LuaHostCallResult(
    val executionId: LuaRequestId,
    val callId: String,
    val value: LuaValue,
) {
    override fun toString(): String =
        "LuaHostCallResult(executionId=$executionId, callIdUtf8Bytes=${callId.toByteArray(Charsets.UTF_8).size}, " +
            "value=$value)"
}

class LuaHostCallError(
    val executionId: LuaRequestId,
    val callId: String,
    val code: LuaHostErrorCode,
    val message: String,
) {
    override fun toString(): String =
        "LuaHostCallError(executionId=$executionId, callIdUtf8Bytes=${callId.toByteArray(Charsets.UTF_8).size}, code=$code, " +
            "messageCodePoints=${message.codePointCount(0, message.length)})"
}

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
