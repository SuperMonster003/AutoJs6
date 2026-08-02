package org.autojs.plugin.dexcompiler.api

import java.nio.charset.StandardCharsets

internal object DexCompilerFixtures {
    val requestId: DexRequestId = DexRequestId.fromBytes(ByteArray(DexRequestId.BYTE_COUNT) { it.toByte() })
    val programSha256: DexSha256 = sha256("program")
    val outputSha256: DexSha256 = sha256("output")
    val runtimeLibraries: List<DexRuntimeLibraryIdentity> = listOf(
        DexRuntimeLibraryIdentity(1_024L, sha256("boot-0")),
        DexRuntimeLibraryIdentity(2_048L, sha256("boot-1")),
    )
    val runtimeFingerprint: DexSha256 = DexRuntimeLibraryFingerprint.compute(runtimeLibraries)

    fun info(
        min: DexProtocolVersion = DexProtocolVersion(1, 0),
        max: DexProtocolVersion = DexProtocolVersion(1, 0),
    ) = DexCompilerInfo(
        protocolMin = min,
        protocolMax = max,
        providerId = "autojs6-d8",
        providerVersionName = "1.0.0",
        providerVersionCode = 4L,
        minHostVersionCode = 5_270L,
        maxHostVersionCode = null,
        abiMode = DexCompilerAbiMode.ANY,
    )

    fun capabilities(
        runtimeFingerprintOverride: DexSha256 = runtimeFingerprint,
        modes: Collection<DexCompilerMode> = listOf(DexCompilerMode.DEBUG, DexCompilerMode.RELEASE),
    ) = DexCompilerCapabilities(
        compilerFamily = DexCompilerFamily.D8,
        compilerVersion = "8.13.17",
        inputFormats = listOf(DexCompilerInputFormat.JAR),
        outputFormats = listOf(DexCompilerOutputFormat.DEX_ZIP),
        modes = modes,
        minApi = 24,
        maxApi = 36,
        supportsMultiDex = true,
        determinismClaim = DexCompilerDeterminismClaim.NOT_CLAIMED,
        runtimeLibraryModel = DexRuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1,
        acceptsExternalClasspath = false,
        supportsDesugaredLibraryConfiguration = false,
        limits = DexCompilerResourceLimits(
            maxCompressedProgramBytes = DexCompilerContract.MAX_COMPRESSED_PROGRAM_BYTES,
            maxArchiveEntries = DexCompilerContract.MAX_ARCHIVE_ENTRIES,
            maxUncompressedProgramBytes = DexCompilerContract.MAX_UNCOMPRESSED_PROGRAM_BYTES,
            maxTotalClassBytes = DexCompilerContract.MAX_TOTAL_CLASS_BYTES,
            maxSingleClassBytes = DexCompilerContract.MAX_SINGLE_CLASS_BYTES,
            maxOutputBytes = DexCompilerContract.MAX_OUTPUT_BYTES,
            maxDexEntries = DexCompilerContract.MAX_DEX_ENTRIES,
            maxDiagnosticBytes = DexCompilerContract.MAX_DIAGNOSTIC_BYTES,
            maxConcurrentSessions = DexCompilerContract.MAX_CONCURRENT_SESSIONS,
        ),
        runtimeLibraryIdentities = runtimeLibraries,
        runtimeLibraryFingerprint = runtimeFingerprintOverride,
    )

    fun request(
        protocolVersion: DexProtocolVersion = DexProtocolVersion(1, 0),
        minApi: Int = 24,
        maxOutputBytes: Long = 16L * 1024L * 1024L,
        expectedFingerprint: DexSha256 = runtimeFingerprint,
    ) = DexCompileRequest(
        requestId = requestId,
        protocolVersion = protocolVersion,
        inputFormat = DexCompilerInputFormat.JAR,
        outputFormat = DexCompilerOutputFormat.DEX_ZIP,
        mode = DexCompilerMode.RELEASE,
        minApi = minApi,
        programSizeBytes = 4_096L,
        programSha256 = programSha256,
        maxOutputBytes = maxOutputBytes,
        runtimeLibraryModel = DexRuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1,
        expectedRuntimeLibraryFingerprint = expectedFingerprint,
        canonicalizationPolicyVersion = DexCompilerContract.CANONICALIZATION_POLICY_VERSION,
        diagnosticByteLimit = DexCompilerContract.MAX_DIAGNOSTIC_BYTES,
        timeoutMillis = DexCompilerContract.DEFAULT_TIMEOUT_MILLIS,
    )

    fun diagnostics(): List<DexCompilerDiagnostic> = listOf(
        DexCompilerDiagnostic(DexCompilerDiagnosticSeverity.INFO, "D8_INFO", "Compiler started"),
        DexCompilerDiagnostic(DexCompilerDiagnosticSeverity.WARNING, "D8_WARNING", "Fixture warning"),
    )

    fun sha256(text: String): DexSha256 = DexSha256.digest(text.toByteArray(StandardCharsets.UTF_8))
}
