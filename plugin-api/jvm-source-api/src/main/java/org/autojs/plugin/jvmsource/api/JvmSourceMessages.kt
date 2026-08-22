package org.autojs.plugin.jvmsource.api

import java.util.Collections

data class JvmProviderInfo(
    val protocolMin: JvmProtocolVersion,
    val protocolMax: JvmProtocolVersion,
    val providerId: String,
    val providerVersionName: String,
    val providerVersionCode: Long,
    val entryApiVersion: Int,
    val minHostVersionCode: Long? = null,
    val maxHostVersionCode: Long? = null,
)

/**
 * Immutable provider limits and identity. Protocol v1 permits one language and
 * binds its compiler, D8, and runtime library to [toolchainFingerprint].
 */
class JvmSourceCapabilities(
    languages: List<JvmSourceLanguage>,
    isolationCapabilities: List<JvmIsolationCapability>,
    val sourceCompilerFamily: JvmSourceCompilerFamily,
    val sourceCompilerVersion: String,
    val d8Version: String,
    val runtimeLibraryFingerprint: JvmSha256,
    val toolchainFingerprint: JvmSha256,
    val maxSourceBytes: Long,
    val maxStdoutBytes: Long,
    val maxStderrBytes: Long,
    val maxDiagnosticBytes: Int,
    val maxTimeoutMillis: Long,
    val maxConcurrentSessions: Int,
    scriptCapabilities: List<JvmScriptCapability> = emptyList(),
) {
    val languages: List<JvmSourceLanguage> = defensiveList(languages)
    val isolationCapabilities: List<JvmIsolationCapability> = defensiveList(isolationCapabilities)
    val scriptCapabilities: List<JvmScriptCapability> = defensiveList(scriptCapabilities)

    fun copy(
        languages: List<JvmSourceLanguage> = this.languages,
        isolationCapabilities: List<JvmIsolationCapability> = this.isolationCapabilities,
        sourceCompilerFamily: JvmSourceCompilerFamily = this.sourceCompilerFamily,
        sourceCompilerVersion: String = this.sourceCompilerVersion,
        d8Version: String = this.d8Version,
        runtimeLibraryFingerprint: JvmSha256 = this.runtimeLibraryFingerprint,
        toolchainFingerprint: JvmSha256 = this.toolchainFingerprint,
        maxSourceBytes: Long = this.maxSourceBytes,
        maxStdoutBytes: Long = this.maxStdoutBytes,
        maxStderrBytes: Long = this.maxStderrBytes,
        maxDiagnosticBytes: Int = this.maxDiagnosticBytes,
        maxTimeoutMillis: Long = this.maxTimeoutMillis,
        maxConcurrentSessions: Int = this.maxConcurrentSessions,
        scriptCapabilities: List<JvmScriptCapability> = this.scriptCapabilities,
    ): JvmSourceCapabilities = JvmSourceCapabilities(
        languages,
        isolationCapabilities,
        sourceCompilerFamily,
        sourceCompilerVersion,
        d8Version,
        runtimeLibraryFingerprint,
        toolchainFingerprint,
        maxSourceBytes,
        maxStdoutBytes,
        maxStderrBytes,
        maxDiagnosticBytes,
        maxTimeoutMillis,
        maxConcurrentSessions,
        scriptCapabilities,
    )

    override fun equals(other: Any?): Boolean = other is JvmSourceCapabilities &&
        languages == other.languages &&
        isolationCapabilities == other.isolationCapabilities &&
        sourceCompilerFamily == other.sourceCompilerFamily &&
        sourceCompilerVersion == other.sourceCompilerVersion &&
        d8Version == other.d8Version &&
        runtimeLibraryFingerprint == other.runtimeLibraryFingerprint &&
        toolchainFingerprint == other.toolchainFingerprint &&
        maxSourceBytes == other.maxSourceBytes &&
        maxStdoutBytes == other.maxStdoutBytes &&
        maxStderrBytes == other.maxStderrBytes &&
        maxDiagnosticBytes == other.maxDiagnosticBytes &&
        maxTimeoutMillis == other.maxTimeoutMillis &&
        maxConcurrentSessions == other.maxConcurrentSessions &&
        scriptCapabilities == other.scriptCapabilities

    override fun hashCode(): Int {
        var result = languages.hashCode()
        result = 31 * result + isolationCapabilities.hashCode()
        result = 31 * result + sourceCompilerFamily.hashCode()
        result = 31 * result + sourceCompilerVersion.hashCode()
        result = 31 * result + d8Version.hashCode()
        result = 31 * result + runtimeLibraryFingerprint.hashCode()
        result = 31 * result + toolchainFingerprint.hashCode()
        result = 31 * result + maxSourceBytes.hashCode()
        result = 31 * result + maxStdoutBytes.hashCode()
        result = 31 * result + maxStderrBytes.hashCode()
        result = 31 * result + maxDiagnosticBytes
        result = 31 * result + maxTimeoutMillis.hashCode()
        result = 31 * result + maxConcurrentSessions
        return 31 * result + scriptCapabilities.hashCode()
    }

    override fun toString(): String =
        "JvmSourceCapabilities(languages=$languages, isolationCapabilities=$isolationCapabilities, " +
            "sourceCompilerFamily=$sourceCompilerFamily, sourceCompilerVersion=$sourceCompilerVersion, " +
            "d8Version=$d8Version, runtimeLibraryFingerprint=$runtimeLibraryFingerprint, " +
            "toolchainFingerprint=$toolchainFingerprint, maxSourceBytes=$maxSourceBytes, " +
            "maxStdoutBytes=$maxStdoutBytes, maxStderrBytes=$maxStderrBytes, " +
            "maxDiagnosticBytes=$maxDiagnosticBytes, maxTimeoutMillis=$maxTimeoutMillis, " +
            "maxConcurrentSessions=$maxConcurrentSessions, scriptCapabilities=$scriptCapabilities)"
}

data class JvmSourceStarted(
    val requestId: JvmRequestId,
    val language: JvmSourceLanguage,
    val providerId: String,
    val toolchainFingerprint: JvmSha256,
    val compilerPid: Int,
    val compilerUid: Int,
    val compilerProcessName: String,
)

data class JvmSourceDiagnostic(
    val requestId: JvmRequestId,
    val severity: JvmDiagnosticSeverity,
    val code: String,
    val message: String,
    val sourceFileName: String? = null,
    val line: Int? = null,
    val column: Int? = null,
)

/** A request pinned to the exact toolchain advertised during negotiation. */
class JvmSourceRequest(
    val requestId: JvmRequestId,
    val protocolVersion: JvmProtocolVersion,
    val language: JvmSourceLanguage,
    val sourceFileName: String,
    val sourceSizeBytes: Long,
    val sourceSha256: JvmSha256,
    val expectedToolchainFingerprint: JvmSha256,
    val entryClassName: String,
    val minApi: Int,
    val timeoutMillis: Long,
    val maxStdoutBytes: Long,
    val maxStderrBytes: Long,
    val diagnosticByteLimit: Int,
    allowedHostCalls: List<String> = emptyList(),
    grantedCapabilities: List<JvmScriptCapability> = emptyList(),
) {
    val allowedHostCalls: List<String> = defensiveList(allowedHostCalls)
    val grantedCapabilities: List<JvmScriptCapability> = defensiveList(grantedCapabilities)

    fun copy(
        requestId: JvmRequestId = this.requestId,
        protocolVersion: JvmProtocolVersion = this.protocolVersion,
        language: JvmSourceLanguage = this.language,
        sourceFileName: String = this.sourceFileName,
        sourceSizeBytes: Long = this.sourceSizeBytes,
        sourceSha256: JvmSha256 = this.sourceSha256,
        expectedToolchainFingerprint: JvmSha256 = this.expectedToolchainFingerprint,
        entryClassName: String = this.entryClassName,
        minApi: Int = this.minApi,
        timeoutMillis: Long = this.timeoutMillis,
        maxStdoutBytes: Long = this.maxStdoutBytes,
        maxStderrBytes: Long = this.maxStderrBytes,
        diagnosticByteLimit: Int = this.diagnosticByteLimit,
        allowedHostCalls: List<String> = this.allowedHostCalls,
        grantedCapabilities: List<JvmScriptCapability> = this.grantedCapabilities,
    ): JvmSourceRequest = JvmSourceRequest(
        requestId,
        protocolVersion,
        language,
        sourceFileName,
        sourceSizeBytes,
        sourceSha256,
        expectedToolchainFingerprint,
        entryClassName,
        minApi,
        timeoutMillis,
        maxStdoutBytes,
        maxStderrBytes,
        diagnosticByteLimit,
        allowedHostCalls,
        grantedCapabilities,
    )

    override fun equals(other: Any?): Boolean = other is JvmSourceRequest &&
        requestId == other.requestId &&
        protocolVersion == other.protocolVersion &&
        language == other.language &&
        sourceFileName == other.sourceFileName &&
        sourceSizeBytes == other.sourceSizeBytes &&
        sourceSha256 == other.sourceSha256 &&
        expectedToolchainFingerprint == other.expectedToolchainFingerprint &&
        entryClassName == other.entryClassName &&
        minApi == other.minApi &&
        timeoutMillis == other.timeoutMillis &&
        maxStdoutBytes == other.maxStdoutBytes &&
        maxStderrBytes == other.maxStderrBytes &&
        diagnosticByteLimit == other.diagnosticByteLimit &&
        allowedHostCalls == other.allowedHostCalls &&
        grantedCapabilities == other.grantedCapabilities

    override fun hashCode(): Int {
        var result = requestId.hashCode()
        result = 31 * result + protocolVersion.hashCode()
        result = 31 * result + language.hashCode()
        result = 31 * result + sourceFileName.hashCode()
        result = 31 * result + sourceSizeBytes.hashCode()
        result = 31 * result + sourceSha256.hashCode()
        result = 31 * result + expectedToolchainFingerprint.hashCode()
        result = 31 * result + entryClassName.hashCode()
        result = 31 * result + minApi
        result = 31 * result + timeoutMillis.hashCode()
        result = 31 * result + maxStdoutBytes.hashCode()
        result = 31 * result + maxStderrBytes.hashCode()
        result = 31 * result + diagnosticByteLimit
        result = 31 * result + allowedHostCalls.hashCode()
        return 31 * result + grantedCapabilities.hashCode()
    }

    override fun toString(): String =
        "JvmSourceRequest(requestId=$requestId, protocolVersion=$protocolVersion, language=$language, " +
            "sourceFileName=$sourceFileName, sourceSizeBytes=$sourceSizeBytes, sourceSha256=$sourceSha256, " +
            "expectedToolchainFingerprint=$expectedToolchainFingerprint, entryClassName=$entryClassName, " +
            "minApi=$minApi, timeoutMillis=$timeoutMillis, maxStdoutBytes=$maxStdoutBytes, " +
            "maxStderrBytes=$maxStderrBytes, diagnosticByteLimit=$diagnosticByteLimit, " +
            "allowedHostCalls=$allowedHostCalls, grantedCapabilities=$grantedCapabilities)"
}

/**
 * Successful return value encoded as bounded JSON. The provider must normalize
 * arbitrary worker objects before crossing Binder; Java objects never cross
 * this protocol directly. The JSON literal `null` represents a null return.
 */
data class JvmSourceResult(
    val requestId: JvmRequestId,
    val toolchainFingerprint: JvmSha256,
    val classArtifactSizeBytes: Long,
    val classArtifactSha256: JvmSha256,
    val dexArtifactSizeBytes: Long,
    val dexArtifactSha256: JvmSha256,
    val dexVersion: String,
    val loaderKind: JvmDexLoaderKind,
    val resultJson: String,
    val stdoutSizeBytes: Long,
    val stdoutSha256: JvmSha256,
    val stderrSizeBytes: Long,
    val stderrSha256: JvmSha256,
    val workerPid: Int,
    val workerUid: Int,
    val workerProcessName: String,
    val workerGeneration: Long,
    val internalBridgeCallerPid: Int,
    val internalBridgeCallerUid: Int,
    val internalBridgeCallCount: Int,
    val compilationElapsedMillis: Long,
    val executionElapsedMillis: Long,
    val elapsedMillis: Long,
)

data class JvmSourceError(
    val requestId: JvmRequestId,
    val code: JvmSourceErrorCode,
    val phase: JvmSourceFailurePhase,
    val message: String,
    val retryable: Boolean,
)

data class JvmSourceCancellation(
    val requestId: JvmRequestId,
    val reason: JvmCancellationReason,
    val phase: JvmSourceFailurePhase,
    val elapsedMillis: Long,
)

data class JvmHostCall(
    val requestId: JvmRequestId,
    val callId: Long,
    val method: String,
    val payloadJson: String,
    val timeoutMillis: Long,
)

data class JvmHostResponse(
    val requestId: JvmRequestId,
    val callId: Long,
    val succeeded: Boolean,
    val payloadJson: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

private fun <T> defensiveList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
