package org.autojs.autojs.core.plugin.dex

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections
import java.util.TreeMap

internal enum class DexCompilationMode(val stableId: Int) {
    DEBUG(1),
    RELEASE(2),
}

internal enum class DexOutputMode(val stableId: Int) {
    DEX_INDEXED_ZIP(1),
}

internal class DexCompilerProviderProvenance(
    packageName: String,
    componentClassName: String,
    providerId: String,
    packageUid: Int,
    signerSha256: Collection<String>,
    versionCode: Long,
    lastUpdateTime: Long,
) {
    val packageName = packageName.checkedText("Provider package name")
    val componentClassName = componentClassName.checkedText("Provider component class")
    val providerId = providerId.checkedText("Provider ID")
    val packageUid = packageUid.also { require(it >= 0) { "Provider UID must be non-negative" } }
    val signerSha256: List<String> = Collections.unmodifiableList(
        signerSha256.map { normalizedSha256(it, "Provider signer SHA-256") }.distinct().sorted(),
    )
    val versionCode = versionCode.also { require(it >= 0L) { "Provider version code must be non-negative" } }
    val lastUpdateTime = lastUpdateTime.also {
        require(it >= 0L) { "Provider last-update time must be non-negative" }
    }

    init {
        require(this.signerSha256.isNotEmpty()) { "Provider signer set must not be empty" }
    }
}

internal class DexCompilerCacheSpec(
    inputSha256: String,
    canonicalizationPolicyVersion: Int,
    protocolMajor: Int,
    protocolMinor: Int,
    compilerFamily: String,
    compilerVersion: String,
    minimumApi: Int,
    compilationMode: DexCompilationMode,
    outputMode: DexOutputMode,
    runtimeLibraryFingerprint: String,
    desugaredLibraryConfigurationIdentity: String?,
    semanticOptions: Map<String, String>,
    providerProvenance: DexCompilerProviderProvenance,
) {
    val inputSha256 = normalizedSha256(inputSha256, "Input SHA-256")
    val canonicalizationPolicyVersion = canonicalizationPolicyVersion.also {
        require(it > 0) { "Canonicalization policy version must be positive" }
    }
    val protocolMajor = protocolMajor.also { require(it > 0) { "Protocol major must be positive" } }
    val protocolMinor = protocolMinor.also { require(it >= 0) { "Protocol minor must be non-negative" } }
    val compilerFamily = compilerFamily.checkedText("Compiler family")
    val compilerVersion = compilerVersion.checkedText("Compiler version")
    val minimumApi = minimumApi.also { require(it > 0) { "Minimum API must be positive" } }
    val compilationMode = compilationMode
    val outputMode = outputMode
    val runtimeLibraryFingerprint = normalizedSha256(runtimeLibraryFingerprint, "Runtime-library fingerprint")
    val desugaredLibraryConfigurationIdentity = desugaredLibraryConfigurationIdentity?.checkedText(
        "Desugared-library configuration identity",
    )
    val semanticOptions: Map<String, String>
    val providerProvenance = providerProvenance

    init {
        require(semanticOptions.size <= MAX_OPTIONS) { "Too many semantic compiler options" }
        val copy = TreeMap<String, String>()
        semanticOptions.forEach { (key, value) ->
            copy[key.checkedText("Semantic option name")] = value.checkedText(
                "Semantic option value",
                allowEmpty = true,
            )
        }
        this.semanticOptions = Collections.unmodifiableMap(copy)
    }

    companion object {
        private const val MAX_OPTIONS = 256
    }
}

/** Produces a versioned, delimiter-safe identity for every compilation-semantic input. */
internal object DexCompilerSemanticCacheKey {

    private const val FORMAT_VERSION = 1
    private val DOMAIN = "AutoJs6/DexCompilerSemanticCacheKey".toByteArray(StandardCharsets.US_ASCII)

    fun sha256(spec: DexCompilerCacheSpec): String = sha256(canonicalBytes(spec)).toHex()

    fun canonicalBytes(spec: DexCompilerCacheSpec): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(DOMAIN.size)
            data.write(DOMAIN)
            data.writeInt(FORMAT_VERSION)
            data.writeField(1, spec.inputSha256.hexToBytes())
            data.writeIntField(2, spec.canonicalizationPolicyVersion)
            data.writeIntField(3, spec.protocolMajor)
            data.writeIntField(4, spec.protocolMinor)
            data.writeField(5, spec.compilerFamily)
            data.writeField(6, spec.compilerVersion)
            data.writeIntField(7, spec.minimumApi)
            data.writeIntField(8, spec.compilationMode.stableId)
            data.writeIntField(9, spec.outputMode.stableId)
            data.writeField(10, spec.runtimeLibraryFingerprint.hexToBytes())
            data.writeNullableField(11, spec.desugaredLibraryConfigurationIdentity)
            data.writeInt(12)
            data.writeInt(spec.semanticOptions.size)
            spec.semanticOptions.forEach { (key, value) ->
                data.writeLengthPrefixedUtf8(key)
                data.writeLengthPrefixedUtf8(value)
            }
            data.writeField(13, spec.providerProvenance.packageName)
            data.writeField(14, spec.providerProvenance.componentClassName)
            data.writeField(15, spec.providerProvenance.providerId)
            data.writeIntField(16, spec.providerProvenance.packageUid)
            data.writeInt(17)
            data.writeInt(spec.providerProvenance.signerSha256.size)
            spec.providerProvenance.signerSha256.forEach { signer -> data.writeLengthPrefixedBytes(signer.hexToBytes()) }
            data.writeLongField(18, spec.providerProvenance.versionCode)
            data.writeLongField(19, spec.providerProvenance.lastUpdateTime)
        }
        return output.toByteArray()
    }

    private fun DataOutputStream.writeField(tag: Int, value: String) {
        writeField(tag, value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun DataOutputStream.writeField(tag: Int, value: ByteArray) {
        writeInt(tag)
        writeInt(value.size)
        write(value)
    }

    private fun DataOutputStream.writeIntField(tag: Int, value: Int) {
        writeInt(tag)
        writeInt(Int.SIZE_BYTES)
        writeInt(value)
    }

    private fun DataOutputStream.writeLongField(tag: Int, value: Long) {
        writeInt(tag)
        writeInt(Long.SIZE_BYTES)
        writeLong(value)
    }

    private fun DataOutputStream.writeNullableField(tag: Int, value: String?) {
        writeInt(tag)
        if (value == null) {
            writeInt(-1)
        } else {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            writeInt(bytes.size)
            write(bytes)
        }
    }

    private fun DataOutputStream.writeLengthPrefixedUtf8(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeLengthPrefixedBytes(bytes)
    }

    private fun DataOutputStream.writeLengthPrefixedBytes(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }
}

private const val SHA256_HEX_LENGTH = 64
private const val MAX_CACHE_TEXT_BYTES = 16 * 1024

private fun String.checkedText(label: String, allowEmpty: Boolean = false): String {
    require(allowEmpty || isNotBlank()) { "$label must not be blank" }
    require(toByteArray(StandardCharsets.UTF_8).size <= MAX_CACHE_TEXT_BYTES) { "$label is too long" }
    return this
}

private fun normalizedSha256(value: String, label: String): String {
    requireSha256(value, label)
    return value.lowercase()
}

private fun requireSha256(value: String, label: String) {
    require(value.length == SHA256_HEX_LENGTH && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        "$label must contain exactly 32 hexadecimal bytes"
    }
}

private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { index ->
    substring(index * 2, index * 2 + 2).toInt(16).toByte()
}

private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
