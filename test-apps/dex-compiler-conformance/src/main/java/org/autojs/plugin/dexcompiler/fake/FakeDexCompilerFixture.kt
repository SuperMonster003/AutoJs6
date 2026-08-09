package org.autojs.plugin.dexcompiler.fake

import org.autojs.plugin.dexcompiler.api.DexCompilerAbiMode
import org.autojs.plugin.dexcompiler.api.DexCompilerCapabilities
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.autojs.plugin.dexcompiler.api.DexCompilerDeterminismClaim
import org.autojs.plugin.dexcompiler.api.DexCompilerFamily
import org.autojs.plugin.dexcompiler.api.DexCompilerInfo
import org.autojs.plugin.dexcompiler.api.DexCompilerInputFormat
import org.autojs.plugin.dexcompiler.api.DexCompilerMode
import org.autojs.plugin.dexcompiler.api.DexCompilerOutputFormat
import org.autojs.plugin.dexcompiler.api.DexCompilerResourceLimits
import org.autojs.plugin.dexcompiler.api.DexProtocolVersion
import org.autojs.plugin.dexcompiler.api.DexRuntimeLibraryFingerprint
import org.autojs.plugin.dexcompiler.api.DexRuntimeLibraryIdentity
import org.autojs.plugin.dexcompiler.api.DexRuntimeLibraryModel

object FakeDexCompilerFixture {
    const val HOST_PACKAGE_NAME = "org.autojs.autojs6"
    const val PROVIDER_ID = "autojs6-dex-compiler-fake"
    const val PROVIDER_VERSION = "fixture-1"
    const val MAX_PROGRAM_BYTES = 8L * 1024L * 1024L
    const val MAX_OUTPUT_BYTES = 2L * 1024L * 1024L

    val protocolVersion = DexProtocolVersion(
        DexCompilerContract.PROTOCOL_MAJOR,
        DexCompilerContract.PROTOCOL_MINOR,
    )

    private val runtimeLibraryIdentities = listOf(
        DexRuntimeLibraryIdentity(
            sizeBytes = EmbeddedDexFixture.SIZE_BYTES.toLong(),
            contentSha256 = EmbeddedDexFixture.sha256,
        )
    )

    val runtimeLibraryFingerprint = DexRuntimeLibraryFingerprint.compute(runtimeLibraryIdentities)

    fun info(scenario: FakeDexCompilerScenario): DexCompilerInfo = DexCompilerInfo(
        protocolMin = protocolVersion,
        protocolMax = protocolVersion,
        providerId = "$PROVIDER_ID.${scenario.metadataValue}",
        providerVersionName = PROVIDER_VERSION,
        providerVersionCode = 1L,
        abiMode = DexCompilerAbiMode.ANY,
    )

    val capabilities = DexCompilerCapabilities(
        compilerFamily = DexCompilerFamily.D8,
        compilerVersion = PROVIDER_VERSION,
        inputFormats = listOf(DexCompilerInputFormat.JAR),
        outputFormats = listOf(DexCompilerOutputFormat.DEX_ZIP),
        modes = listOf(DexCompilerMode.DEBUG, DexCompilerMode.RELEASE),
        minApi = 24,
        maxApi = 10_000,
        supportsMultiDex = true,
        determinismClaim = DexCompilerDeterminismClaim.NOT_CLAIMED,
        runtimeLibraryModel = DexRuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1,
        acceptsExternalClasspath = false,
        supportsDesugaredLibraryConfiguration = false,
        limits = DexCompilerResourceLimits(
            maxCompressedProgramBytes = MAX_PROGRAM_BYTES,
            maxArchiveEntries = 1_024,
            maxUncompressedProgramBytes = 16L * 1024L * 1024L,
            maxTotalClassBytes = 8L * 1024L * 1024L,
            maxSingleClassBytes = 2L * 1024L * 1024L,
            maxOutputBytes = MAX_OUTPUT_BYTES,
            maxDexEntries = 1,
            maxDiagnosticBytes = DexCompilerContract.MAX_DIAGNOSTIC_BYTES,
            maxConcurrentSessions = DexCompilerContract.MAX_CONCURRENT_SESSIONS,
        ),
        runtimeLibraryIdentities = runtimeLibraryIdentities,
        runtimeLibraryFingerprint = runtimeLibraryFingerprint,
    )
}
