package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8ArtifactRole
import org.autojs.plugin.r8compiler.api.R8CompileRequest
import org.autojs.plugin.r8compiler.api.R8CompilerCapabilities
import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.autojs.plugin.r8compiler.api.R8CompilerFamily
import org.autojs.plugin.r8compiler.api.R8CompilerIntent
import org.autojs.plugin.r8compiler.api.R8CompilerProfile
import org.autojs.plugin.r8compiler.api.R8CompilerValidation
import org.autojs.plugin.r8compiler.api.R8FallbackPolicy
import org.autojs.plugin.r8compiler.api.R8InputLayout
import org.autojs.plugin.r8compiler.api.R8InputSetFingerprint
import org.autojs.plugin.r8compiler.api.R8OutputLayout
import org.autojs.plugin.r8compiler.api.R8RequestId
import org.autojs.plugin.r8compiler.api.R8RequestedArtifact

internal object R8CompilerRequestFactory {
    fun create(
        spec: R8CompilerHostCompileSpec,
        snapshot: R8CompilerInputSnapshot,
        capabilities: R8CompilerCapabilities,
        requestId: R8RequestId,
    ): R8CompileRequest {
        val requestedArtifacts = R8ArtifactRole.values().map { role ->
            val providerMaximum = capabilities.limits.maximumFor(role)
            val requestedMaximum = spec.requestedArtifactMaximums[role] ?: providerMaximum
            require(requestedMaximum <= providerMaximum) { "R8 artifact limit exceeds provider capability" }
            R8RequestedArtifact(role, requestedMaximum)
        }
        val request = R8CompileRequest(
            requestId = requestId,
            protocolVersion = R8CompilerContract.PROTOCOL_V1,
            compilerFamily = R8CompilerFamily.R8,
            compilerIntent = R8CompilerIntent.R8_EXPLICIT,
            fallbackPolicy = R8FallbackPolicy.NONE,
            profile = R8CompilerProfile.FULL_RELEASE,
            minApi = spec.minApi,
            inputLayout = R8InputLayout.PROGRAM_CLASSPATH_AND_RULES_BUNDLE_V1,
            inputIdentities = snapshot.summary.identities,
            inputSetFingerprint = R8InputSetFingerprint.compute(snapshot.summary.identities),
            inputBundleSizeBytes = snapshot.summary.sizeBytes,
            inputBundleSha256 = snapshot.summary.contentSha256,
            runtimeLibraryModel = capabilities.runtimeLibraryModel,
            expectedRuntimeLibraryFingerprint = capabilities.runtimeLibraryFingerprint,
            expectedCapabilityFingerprint = capabilities.capabilityFingerprint,
            canonicalizationPolicyVersion = capabilities.canonicalizationPolicyVersion,
            rulePolicyVersion = capabilities.rulePolicyVersion,
            outputLayout = R8OutputLayout.DEX_AND_REPORTS_BUNDLE_V1,
            requestedArtifacts = requestedArtifacts,
            maxOutputBundleBytes = spec.maximumOutputBundleBytes ?: capabilities.limits.maxOutputBundleBytes,
            diagnosticByteLimit = spec.diagnosticByteLimit ?: capabilities.limits.maxDiagnosticBytes,
            timeoutMillis = spec.timeoutMillis ?: capabilities.limits.defaultTimeoutMillis,
        )
        R8CompilerValidation.validateRequestAgainst(request, capabilities)
        return request
    }
}

private fun org.autojs.plugin.r8compiler.api.R8ResourceLimits.maximumFor(role: R8ArtifactRole): Long =
    when (role) {
        R8ArtifactRole.DEX_ZIP -> maxDexZipBytes
        R8ArtifactRole.MAPPING_TEXT -> maxMappingBytes
        R8ArtifactRole.SEEDS_TEXT -> maxSeedsBytes
        R8ArtifactRole.USAGE_TEXT -> maxUsageBytes
        R8ArtifactRole.RETRACE_METADATA -> maxRetraceMetadataBytes
    }
