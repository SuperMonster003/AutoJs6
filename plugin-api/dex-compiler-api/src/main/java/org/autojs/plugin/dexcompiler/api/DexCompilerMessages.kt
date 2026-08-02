package org.autojs.plugin.dexcompiler.api

import java.util.ArrayList
import java.util.Collections

data class DexCompilerInfo(
    val protocolMin: DexProtocolVersion,
    val protocolMax: DexProtocolVersion,
    val providerId: String,
    val providerVersionName: String,
    val providerVersionCode: Long,
    val minHostVersionCode: Long? = null,
    val maxHostVersionCode: Long? = null,
    val abiMode: DexCompilerAbiMode = DexCompilerAbiMode.ANY,
)

data class DexRuntimeLibraryIdentity(
    val sizeBytes: Long,
    val contentSha256: DexSha256,
)

data class DexCompilerResourceLimits(
    val maxCompressedProgramBytes: Long,
    val maxArchiveEntries: Int,
    val maxUncompressedProgramBytes: Long,
    val maxTotalClassBytes: Long,
    val maxSingleClassBytes: Long,
    val maxOutputBytes: Long,
    val maxDexEntries: Int,
    val maxDiagnosticBytes: Int,
    val maxConcurrentSessions: Int,
)

class DexCompilerCapabilities(
    val compilerFamily: DexCompilerFamily,
    val compilerVersion: String,
    inputFormats: Collection<DexCompilerInputFormat>,
    outputFormats: Collection<DexCompilerOutputFormat>,
    modes: Collection<DexCompilerMode>,
    val minApi: Int,
    val maxApi: Int,
    val supportsMultiDex: Boolean,
    val determinismClaim: DexCompilerDeterminismClaim,
    val runtimeLibraryModel: DexRuntimeLibraryModel,
    val acceptsExternalClasspath: Boolean,
    val supportsDesugaredLibraryConfiguration: Boolean,
    val limits: DexCompilerResourceLimits,
    runtimeLibraryIdentities: Collection<DexRuntimeLibraryIdentity>,
    val runtimeLibraryFingerprint: DexSha256,
) {
    val inputFormats: List<DexCompilerInputFormat> = immutableList(inputFormats)
    val outputFormats: List<DexCompilerOutputFormat> = immutableList(outputFormats)
    val modes: List<DexCompilerMode> = immutableList(modes)
    val runtimeLibraryIdentities: List<DexRuntimeLibraryIdentity> = immutableList(runtimeLibraryIdentities)
}

data class DexCompileRequest(
    val requestId: DexRequestId,
    val protocolVersion: DexProtocolVersion,
    val inputFormat: DexCompilerInputFormat,
    val outputFormat: DexCompilerOutputFormat,
    val mode: DexCompilerMode,
    val minApi: Int,
    val programSizeBytes: Long,
    val programSha256: DexSha256,
    val maxOutputBytes: Long,
    val runtimeLibraryModel: DexRuntimeLibraryModel,
    val expectedRuntimeLibraryFingerprint: DexSha256,
    val canonicalizationPolicyVersion: Int,
    val diagnosticByteLimit: Int,
    val timeoutMillis: Long,
)

data class DexCompileStarted(
    val requestId: DexRequestId,
    val sequence: Long,
    val protocolVersion: DexProtocolVersion,
    val compilerFamily: DexCompilerFamily,
    val compilerVersion: String,
    val runtimeLibraryModel: DexRuntimeLibraryModel,
    val runtimeLibraryFingerprint: DexSha256,
    val queueElapsedMillis: Long,
)

data class DexCompileProgress(
    val requestId: DexRequestId,
    val sequence: Long,
    val stage: DexCompilerProgressStage,
    val current: Long? = null,
    val total: Long? = null,
)

data class DexCompilerDiagnostic(
    val severity: DexCompilerDiagnosticSeverity,
    val code: String,
    val message: String,
)

class DexCompileResult(
    val requestId: DexRequestId,
    val compilerFamily: DexCompilerFamily,
    val compilerVersion: String,
    val mode: DexCompilerMode,
    val minApi: Int,
    val outputFormat: DexCompilerOutputFormat,
    val runtimeLibraryFingerprint: DexSha256,
    val outputSizeBytes: Long,
    val outputSha256: DexSha256,
    val dexEntryCount: Int,
    val elapsedMillis: Long,
    diagnostics: Collection<DexCompilerDiagnostic> = emptyList(),
) {
    val diagnostics: List<DexCompilerDiagnostic> = immutableList(diagnostics)
}

class DexCompileError(
    val requestId: DexRequestId,
    val code: DexCompilerErrorCode,
    val phase: DexCompilerFailurePhase,
    val message: String,
    val retryable: Boolean,
    diagnostics: Collection<DexCompilerDiagnostic> = emptyList(),
) {
    val diagnostics: List<DexCompilerDiagnostic> = immutableList(diagnostics)
}

data class DexCompileCancellation(
    val requestId: DexRequestId,
    val reason: DexCompilerCancellationReason,
    val phase: DexCompilerFailurePhase,
    val elapsedMillis: Long,
)

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
