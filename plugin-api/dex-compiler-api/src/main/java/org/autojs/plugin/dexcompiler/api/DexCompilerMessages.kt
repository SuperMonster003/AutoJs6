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

data class DexCompilerClasspathBundleLimits(
    val maxClasspathJarCount: Int,
    val maxCompressedClasspathJarBytes: Long,
    val maxTotalCompressedClasspathBytes: Long,
    val maxInputBundleBytes: Long,
    val maxTotalInputArchiveEntries: Int,
    val maxTotalUncompressedInputBytes: Long,
    val maxTotalInputClassBytes: Long,
)

data class DexCompilerClasspathBundleCapability(
    val inputLayout: DexCompilerInputLayout,
    val limits: DexCompilerClasspathBundleLimits,
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
    val classpathBundle: DexCompilerClasspathBundleCapability? = null,
) {
    val inputFormats: List<DexCompilerInputFormat> = immutableList(inputFormats)
    val outputFormats: List<DexCompilerOutputFormat> = immutableList(outputFormats)
    val modes: List<DexCompilerMode> = immutableList(modes)
    val runtimeLibraryIdentities: List<DexRuntimeLibraryIdentity> = immutableList(runtimeLibraryIdentities)
}

data class DexCompilerInputIdentity(
    val role: DexCompilerInputRole,
    val ordinal: Int,
    val sizeBytes: Long,
    val contentSha256: DexSha256,
)

class DexCompileRequest(
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
    val inputLayout: DexCompilerInputLayout? = null,
    inputIdentities: Collection<DexCompilerInputIdentity> = emptyList(),
    val inputSetFingerprint: DexSha256? = null,
    val inputBundleSizeBytes: Long? = null,
    val inputBundleSha256: DexSha256? = null,
) {
    val inputIdentities: List<DexCompilerInputIdentity> = immutableList(inputIdentities)

    /** Data-class-style copy that always snapshots [inputIdentities]. */
    fun copy(
        requestId: DexRequestId = this.requestId,
        protocolVersion: DexProtocolVersion = this.protocolVersion,
        inputFormat: DexCompilerInputFormat = this.inputFormat,
        outputFormat: DexCompilerOutputFormat = this.outputFormat,
        mode: DexCompilerMode = this.mode,
        minApi: Int = this.minApi,
        programSizeBytes: Long = this.programSizeBytes,
        programSha256: DexSha256 = this.programSha256,
        maxOutputBytes: Long = this.maxOutputBytes,
        runtimeLibraryModel: DexRuntimeLibraryModel = this.runtimeLibraryModel,
        expectedRuntimeLibraryFingerprint: DexSha256 = this.expectedRuntimeLibraryFingerprint,
        canonicalizationPolicyVersion: Int = this.canonicalizationPolicyVersion,
        diagnosticByteLimit: Int = this.diagnosticByteLimit,
        timeoutMillis: Long = this.timeoutMillis,
        inputLayout: DexCompilerInputLayout? = this.inputLayout,
        inputIdentities: Collection<DexCompilerInputIdentity> = this.inputIdentities,
        inputSetFingerprint: DexSha256? = this.inputSetFingerprint,
        inputBundleSizeBytes: Long? = this.inputBundleSizeBytes,
        inputBundleSha256: DexSha256? = this.inputBundleSha256,
    ): DexCompileRequest = DexCompileRequest(
        requestId = requestId,
        protocolVersion = protocolVersion,
        inputFormat = inputFormat,
        outputFormat = outputFormat,
        mode = mode,
        minApi = minApi,
        programSizeBytes = programSizeBytes,
        programSha256 = programSha256,
        maxOutputBytes = maxOutputBytes,
        runtimeLibraryModel = runtimeLibraryModel,
        expectedRuntimeLibraryFingerprint = expectedRuntimeLibraryFingerprint,
        canonicalizationPolicyVersion = canonicalizationPolicyVersion,
        diagnosticByteLimit = diagnosticByteLimit,
        timeoutMillis = timeoutMillis,
        inputLayout = inputLayout,
        inputIdentities = inputIdentities,
        inputSetFingerprint = inputSetFingerprint,
        inputBundleSizeBytes = inputBundleSizeBytes,
        inputBundleSha256 = inputBundleSha256,
    )

    operator fun component1() = requestId
    operator fun component2() = protocolVersion
    operator fun component3() = inputFormat
    operator fun component4() = outputFormat
    operator fun component5() = mode
    operator fun component6() = minApi
    operator fun component7() = programSizeBytes
    operator fun component8() = programSha256
    operator fun component9() = maxOutputBytes
    operator fun component10() = runtimeLibraryModel
    operator fun component11() = expectedRuntimeLibraryFingerprint
    operator fun component12() = canonicalizationPolicyVersion
    operator fun component13() = diagnosticByteLimit
    operator fun component14() = timeoutMillis
    operator fun component15() = inputLayout
    operator fun component16() = inputIdentities
    operator fun component17() = inputSetFingerprint
    operator fun component18() = inputBundleSizeBytes
    operator fun component19() = inputBundleSha256

    override fun equals(other: Any?): Boolean = other is DexCompileRequest &&
        requestId == other.requestId &&
        protocolVersion == other.protocolVersion &&
        inputFormat == other.inputFormat &&
        outputFormat == other.outputFormat &&
        mode == other.mode &&
        minApi == other.minApi &&
        programSizeBytes == other.programSizeBytes &&
        programSha256 == other.programSha256 &&
        maxOutputBytes == other.maxOutputBytes &&
        runtimeLibraryModel == other.runtimeLibraryModel &&
        expectedRuntimeLibraryFingerprint == other.expectedRuntimeLibraryFingerprint &&
        canonicalizationPolicyVersion == other.canonicalizationPolicyVersion &&
        diagnosticByteLimit == other.diagnosticByteLimit &&
        timeoutMillis == other.timeoutMillis &&
        inputLayout == other.inputLayout &&
        inputIdentities == other.inputIdentities &&
        inputSetFingerprint == other.inputSetFingerprint &&
        inputBundleSizeBytes == other.inputBundleSizeBytes &&
        inputBundleSha256 == other.inputBundleSha256

    override fun hashCode(): Int {
        var result = requestId.hashCode()
        result = 31 * result + protocolVersion.hashCode()
        result = 31 * result + inputFormat.hashCode()
        result = 31 * result + outputFormat.hashCode()
        result = 31 * result + mode.hashCode()
        result = 31 * result + minApi
        result = 31 * result + programSizeBytes.hashCode()
        result = 31 * result + programSha256.hashCode()
        result = 31 * result + maxOutputBytes.hashCode()
        result = 31 * result + runtimeLibraryModel.hashCode()
        result = 31 * result + expectedRuntimeLibraryFingerprint.hashCode()
        result = 31 * result + canonicalizationPolicyVersion
        result = 31 * result + diagnosticByteLimit
        result = 31 * result + timeoutMillis.hashCode()
        result = 31 * result + (inputLayout?.hashCode() ?: 0)
        result = 31 * result + inputIdentities.hashCode()
        result = 31 * result + (inputSetFingerprint?.hashCode() ?: 0)
        result = 31 * result + (inputBundleSizeBytes?.hashCode() ?: 0)
        result = 31 * result + (inputBundleSha256?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "DexCompileRequest(requestId=$requestId, protocolVersion=$protocolVersion, " +
            "inputFormat=$inputFormat, outputFormat=$outputFormat, mode=$mode, minApi=$minApi, " +
            "programSizeBytes=$programSizeBytes, programSha256=$programSha256, " +
            "maxOutputBytes=$maxOutputBytes, runtimeLibraryModel=$runtimeLibraryModel, " +
            "expectedRuntimeLibraryFingerprint=$expectedRuntimeLibraryFingerprint, " +
            "canonicalizationPolicyVersion=$canonicalizationPolicyVersion, " +
            "diagnosticByteLimit=$diagnosticByteLimit, timeoutMillis=$timeoutMillis, " +
            "inputLayout=$inputLayout, inputIdentities=$inputIdentities, " +
            "inputSetFingerprint=$inputSetFingerprint, inputBundleSizeBytes=$inputBundleSizeBytes, " +
            "inputBundleSha256=$inputBundleSha256)"
}

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
    /** Logical, path-free input origin such as `program` or `classpath:0`. */
    val origin: String? = null,
    /** Validated archive entry name, never a host or provider filesystem path. */
    val entry: String? = null,
    val position: DexCompilerDiagnosticPosition? = null,
)

data class DexCompilerDiagnosticPosition(
    val line: Int? = null,
    val column: Int? = null,
    val offset: Long? = null,
    val endLine: Int? = null,
    val endColumn: Int? = null,
    val endOffset: Long? = null,
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
