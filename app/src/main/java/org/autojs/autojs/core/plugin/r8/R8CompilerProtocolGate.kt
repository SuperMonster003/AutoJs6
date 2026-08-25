package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8ArtifactRole
import org.autojs.plugin.r8compiler.api.R8CompilerCapabilities
import org.autojs.plugin.r8compiler.api.R8CompilerCodec
import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.autojs.plugin.r8compiler.api.R8CompilerFamily
import org.autojs.plugin.r8compiler.api.R8CompilerInfo
import org.autojs.plugin.r8compiler.api.R8CompilerProfile
import org.autojs.plugin.r8compiler.api.R8CompilerValidation
import org.autojs.plugin.r8compiler.api.R8DeterminismClaim
import org.autojs.plugin.r8compiler.api.R8ImplicitRulePolicy
import org.autojs.plugin.r8compiler.api.R8InputLayout
import org.autojs.plugin.r8compiler.api.R8OutputLayout
import org.autojs.plugin.r8compiler.api.R8RuntimeLibraryModel

internal data class R8CompilerNegotiation(
    val identity: R8CompilerPinnedIdentity,
    val info: R8CompilerInfo,
    val capabilities: R8CompilerCapabilities,
)

/** Revalidates package identity, then pins protocol 1.0 and the complete capability fingerprint. */
internal object R8CompilerProtocolGate {
    fun negotiate(
        identity: R8CompilerPinnedIdentity,
        currentCandidate: R8CompilerServiceCandidate?,
        encodedInfo: ByteArray,
        encodedCapabilities: ByteArray,
        hostVersionCode: Long,
        minApi: Int,
    ): R8CompilerNegotiation {
        require(R8CompilerSelectionPolicy.revalidate(identity, currentCandidate)) {
            "R8 compiler package identity changed before negotiation"
        }
        val info = R8CompilerCodec.decodeInfo(encodedInfo)
        val capabilities = R8CompilerCodec.decodeCapabilities(encodedCapabilities)
        R8CompilerValidation.validateInfo(info)
        R8CompilerValidation.validateCapabilities(capabilities)
        require(info.providerId == R8CompilerContract.PROVIDER_ID) { "R8 provider ID is incompatible" }
        require(info.providerVersionCode == identity.versionCode) { "R8 provider version changed" }
        require(R8CompilerContract.PROTOCOL_V1 in info.protocolMin..info.protocolMax) {
            "R8 protocol 1.0 is not supported"
        }
        require(info.minHostVersionCode == null || hostVersionCode >= info.minHostVersionCode!!) {
            "AutoJs6 is older than the R8 provider minimum"
        }
        require(info.maxHostVersionCode == null || hostVersionCode <= info.maxHostVersionCode!!) {
            "AutoJs6 is newer than the R8 provider maximum"
        }
        require(capabilities.compilerFamily == R8CompilerFamily.R8) { "Compiler family is not R8" }
        require(capabilities.profiles == listOf(R8CompilerProfile.FULL_RELEASE)) {
            "R8 full-release profile is not exact"
        }
        require(capabilities.inputLayout == R8InputLayout.PROGRAM_CLASSPATH_AND_RULES_BUNDLE_V1 &&
            capabilities.outputLayout == R8OutputLayout.DEX_AND_REPORTS_BUNDLE_V1
        ) { "R8 bundle layout is incompatible" }
        require(capabilities.artifactRoles == R8ArtifactRole.values().toList()) {
            "R8 artifact set is not exactly the canonical five"
        }
        require(capabilities.determinismClaim == R8DeterminismClaim.NOT_CLAIMED &&
            capabilities.runtimeLibraryModel == R8RuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1 &&
            capabilities.implicitRulePolicy == R8ImplicitRulePolicy.NONE
        ) { "R8 capability semantics are incompatible" }
        require(capabilities.rulePolicyVersion == R8CompilerContract.RULE_POLICY_VERSION &&
            capabilities.canonicalizationPolicyVersion == R8CompilerContract.CANONICALIZATION_POLICY_VERSION
        ) { "R8 policy version is incompatible" }
        require(capabilities.limits.maxConcurrentSessions == 1) { "R8 provider concurrency is incompatible" }
        require(minApi in capabilities.minApi..capabilities.maxApi) { "Requested R8 min API is unsupported" }
        return R8CompilerNegotiation(identity, info, capabilities)
    }
}
