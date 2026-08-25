package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8CompileRequest
import org.autojs.plugin.r8compiler.api.R8CompilerCapabilities
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** R8 keys and storage roots are domain-separated from every existing DEX cache generation. */
internal object R8CompilerSemanticCacheKey {
    const val CACHE_DOMAIN = "autojs6:r8-compiler:v1"
    const val CACHE_DIRECTORY = "r8-compiler-cache-v1"
    private val DOMAIN = "AutoJs6:R8CompilerHostSemanticKey:v1\u0000".toByteArray(StandardCharsets.UTF_8)

    fun sha256(
        request: R8CompileRequest,
        capabilities: R8CompilerCapabilities,
        identity: R8CompilerPinnedIdentity,
    ): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.write(DOMAIN)
            output.writeUtf8(CACHE_DOMAIN)
            output.writeUtf8(identity.component.packageName)
            output.writeUtf8(identity.component.className)
            output.writeInt(identity.packageUid)
            output.writeLong(identity.versionCode)
            output.writeLong(identity.lastUpdateTime)
            output.writeInt(identity.signerSha256.size)
            identity.signerSha256.forEach { output.writeUtf8(it) }
            output.writeInt(request.protocolVersion.major)
            output.writeInt(request.protocolVersion.minor)
            output.writeInt(request.compilerFamily.wireCode)
            output.writeInt(request.compilerIntent.wireCode)
            output.writeInt(request.fallbackPolicy.wireCode)
            output.writeInt(request.profile.wireCode)
            output.writeInt(request.minApi)
            output.writeInt(request.inputLayout.wireCode)
            output.write(request.inputSetFingerprint.toByteArray())
            output.write(capabilities.runtimeLibraryFingerprint.toByteArray())
            output.write(capabilities.capabilityFingerprint.toByteArray())
            output.writeUtf8(capabilities.compilerVersion)
            output.writeInt(request.canonicalizationPolicyVersion)
            output.writeInt(request.rulePolicyVersion)
            output.writeInt(request.outputLayout.wireCode)
            output.writeLong(request.maxOutputBundleBytes)
            output.writeInt(request.requestedArtifacts.size)
            request.requestedArtifacts.forEach {
                output.writeInt(it.role.wireCode)
                output.writeLong(it.maxBytes)
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).toHex()
    }

    private fun DataOutputStream.writeUtf8(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
