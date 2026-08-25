package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8ArtifactRole
import org.autojs.plugin.r8compiler.api.R8CapabilityFingerprint
import org.autojs.plugin.r8compiler.api.R8CompileRequest
import org.autojs.plugin.r8compiler.api.R8CompilerCapabilities
import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.autojs.plugin.r8compiler.api.R8CompilerFamily
import org.autojs.plugin.r8compiler.api.R8CompilerInfo
import org.autojs.plugin.r8compiler.api.R8CompilerIntent
import org.autojs.plugin.r8compiler.api.R8CompilerProfile
import org.autojs.plugin.r8compiler.api.R8DeterminismClaim
import org.autojs.plugin.r8compiler.api.R8FallbackPolicy
import org.autojs.plugin.r8compiler.api.R8ImplicitRulePolicy
import org.autojs.plugin.r8compiler.api.R8InputBundleCodec
import org.autojs.plugin.r8compiler.api.R8InputIdentity
import org.autojs.plugin.r8compiler.api.R8InputLayout
import org.autojs.plugin.r8compiler.api.R8InputRole
import org.autojs.plugin.r8compiler.api.R8InputSetFingerprint
import org.autojs.plugin.r8compiler.api.R8OutputLayout
import org.autojs.plugin.r8compiler.api.R8RequestId
import org.autojs.plugin.r8compiler.api.R8RequestedArtifact
import org.autojs.plugin.r8compiler.api.R8ResourceLimits
import org.autojs.plugin.r8compiler.api.R8RuntimeLibraryFingerprint
import org.autojs.plugin.r8compiler.api.R8RuntimeLibraryIdentity
import org.autojs.plugin.r8compiler.api.R8RuntimeLibraryModel
import org.autojs.plugin.r8compiler.api.R8Sha256
import java.util.UUID

internal object R8CompilerHostFixtures {
    const val PACKAGE = "io.github.supermonster003.autojs6.plugin.r8compiler"
    const val CLASS = "$PACKAGE.R8CompilerService"
    val SIGNER = "11".repeat(32)

    fun component() = R8CompilerComponent(PACKAGE, CLASS)

    fun candidate(
        component: R8CompilerComponent = component(),
        action: String = R8CompilerContract.SERVICE_ACTION,
        exported: Boolean = true,
        applicationEnabled: Boolean = true,
        serviceEnabled: Boolean = true,
        permission: String? = R8CompilerContract.PLUGIN_PERMISSION,
        uid: Int = 10_321,
        versionCode: Long = 1L,
        updateTime: Long = 2L,
        signers: Set<String> = setOf(SIGNER),
        sameSigner: Boolean = true,
    ) = R8CompilerServiceCandidate(
        component,
        action,
        exported,
        applicationEnabled,
        serviceEnabled,
        permission,
        uid,
        versionCode,
        updateTime,
        signers,
        sameSigner,
    )

    fun info(
        providerId: String = R8CompilerContract.PROVIDER_ID,
        versionCode: Long = 1L,
        protocolMin: org.autojs.plugin.r8compiler.api.R8ProtocolVersion = R8CompilerContract.PROTOCOL_V1,
        protocolMax: org.autojs.plugin.r8compiler.api.R8ProtocolVersion = R8CompilerContract.PROTOCOL_V1,
        minHostVersionCode: Long? = 5_270L,
        maxHostVersionCode: Long? = null,
    ) = R8CompilerInfo(
        protocolMin,
        protocolMax,
        providerId,
        "0.1.0-provider-dev",
        versionCode,
        minHostVersionCode,
        maxHostVersionCode,
    )

    fun capabilities(compilerVersion: String = "8.13.17"): R8CompilerCapabilities {
        val runtimeIdentities = listOf(R8RuntimeLibraryIdentity(123L, sha(7)))
        val runtimeFingerprint = R8RuntimeLibraryFingerprint.compute(runtimeIdentities)
        val limits = R8ResourceLimits(
            maxProgramBytes = 64L * 1024 * 1024,
            maxClasspathCount = 16,
            maxClasspathJarBytes = 64L * 1024 * 1024,
            maxTotalClasspathBytes = 128L * 1024 * 1024,
            maxKeepRuleFiles = 16,
            maxConsumerRuleFiles = 32,
            maxRuleFileBytes = 256L * 1024,
            maxTotalRuleBytes = 2L * 1024 * 1024,
            maxRuleLinesPerFile = 4_096,
            maxTotalRuleLines = 16_384,
            maxRuleLineBytes = 16 * 1024,
            maxInputBundleBytes = 196L * 1024 * 1024,
            maxArchiveEntries = 20_000,
            maxTotalArchiveEntries = 60_000,
            maxUncompressedProgramBytes = 256L * 1024 * 1024,
            maxTotalUncompressedInputBytes = 512L * 1024 * 1024,
            maxTotalClassBytes = 256L * 1024 * 1024,
            maxSingleClassBytes = 8L * 1024 * 1024,
            maxOutputBundleBytes = 128L * 1024 * 1024,
            maxDexZipBytes = 96L * 1024 * 1024,
            maxMappingBytes = 16L * 1024 * 1024,
            maxSeedsBytes = 8L * 1024 * 1024,
            maxUsageBytes = 8L * 1024 * 1024,
            maxRetraceMetadataBytes = 256L * 1024,
            maxDexEntries = 64,
            maxDiagnosticBytes = 64 * 1024,
            maxConcurrentSessions = 1,
            defaultTimeoutMillis = 120_000L,
            maxTimeoutMillis = 300_000L,
        )

        fun create(fingerprint: R8Sha256) = R8CompilerCapabilities(
            compilerFamily = R8CompilerFamily.R8,
            compilerVersion = compilerVersion,
            profiles = listOf(R8CompilerProfile.FULL_RELEASE),
            minApi = 24,
            maxApi = 36,
            inputLayout = R8InputLayout.PROGRAM_CLASSPATH_AND_RULES_BUNDLE_V1,
            outputLayout = R8OutputLayout.DEX_AND_REPORTS_BUNDLE_V1,
            artifactRoles = R8ArtifactRole.values().toList(),
            determinismClaim = R8DeterminismClaim.NOT_CLAIMED,
            runtimeLibraryModel = R8RuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1,
            rulePolicyVersion = R8CompilerContract.RULE_POLICY_VERSION,
            canonicalizationPolicyVersion = R8CompilerContract.CANONICALIZATION_POLICY_VERSION,
            implicitRulePolicy = R8ImplicitRulePolicy.NONE,
            limits = limits,
            runtimeLibraryIdentities = runtimeIdentities,
            runtimeLibraryFingerprint = runtimeFingerprint,
            capabilityFingerprint = fingerprint,
            supportsMultiDex = true,
        )
        val provisional = create(R8Sha256.ZERO)
        return create(R8CapabilityFingerprint.compute(provisional))
    }

    fun request(
        capabilities: R8CompilerCapabilities = capabilities(),
        minApi: Int = 24,
        programDigestSeed: Int = 1,
    ): R8CompileRequest {
        val inputs = listOf(
            R8InputIdentity(R8InputRole.PROGRAM_JAR, 0, -1, 12L, sha(programDigestSeed), R8Sha256.ZERO),
            R8InputIdentity(R8InputRole.KEEP_RULES, 0, -1, 8L, sha(2), R8Sha256.ZERO),
        )
        return R8CompileRequest(
            requestId = R8RequestId.fromUuid(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")),
            protocolVersion = R8CompilerContract.PROTOCOL_V1,
            compilerFamily = R8CompilerFamily.R8,
            compilerIntent = R8CompilerIntent.R8_EXPLICIT,
            fallbackPolicy = R8FallbackPolicy.NONE,
            profile = R8CompilerProfile.FULL_RELEASE,
            minApi = minApi,
            inputLayout = R8InputLayout.PROGRAM_CLASSPATH_AND_RULES_BUNDLE_V1,
            inputIdentities = inputs,
            inputSetFingerprint = R8InputSetFingerprint.compute(inputs),
            inputBundleSizeBytes = R8InputBundleCodec.encodedSize(inputs),
            inputBundleSha256 = sha(3),
            runtimeLibraryModel = R8RuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1,
            expectedRuntimeLibraryFingerprint = capabilities.runtimeLibraryFingerprint,
            expectedCapabilityFingerprint = capabilities.capabilityFingerprint,
            canonicalizationPolicyVersion = R8CompilerContract.CANONICALIZATION_POLICY_VERSION,
            rulePolicyVersion = R8CompilerContract.RULE_POLICY_VERSION,
            outputLayout = R8OutputLayout.DEX_AND_REPORTS_BUNDLE_V1,
            requestedArtifacts = listOf(
                R8RequestedArtifact(R8ArtifactRole.DEX_ZIP, capabilities.limits.maxDexZipBytes),
                R8RequestedArtifact(R8ArtifactRole.MAPPING_TEXT, capabilities.limits.maxMappingBytes),
                R8RequestedArtifact(R8ArtifactRole.SEEDS_TEXT, capabilities.limits.maxSeedsBytes),
                R8RequestedArtifact(R8ArtifactRole.USAGE_TEXT, capabilities.limits.maxUsageBytes),
                R8RequestedArtifact(R8ArtifactRole.RETRACE_METADATA, capabilities.limits.maxRetraceMetadataBytes),
            ),
            maxOutputBundleBytes = capabilities.limits.maxOutputBundleBytes,
            diagnosticByteLimit = capabilities.limits.maxDiagnosticBytes,
            timeoutMillis = capabilities.limits.defaultTimeoutMillis,
        )
    }

    fun identity(
        versionCode: Long = 1L,
        updateTime: Long = 2L,
        signer: String = SIGNER,
    ) = R8CompilerPinnedIdentity(component(), 10_321, versionCode, updateTime, listOf(signer))

    private fun sha(seed: Int) = R8Sha256.fromBytes(ByteArray(32) { (seed + it).toByte() })
}
