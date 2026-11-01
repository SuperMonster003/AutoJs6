package org.autojs.autojs.core.plugin.dex

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.ArrayList
import java.util.Collections

/** Immutable, D8-only local plan for the dormant ordered-classpath seam. */
internal class DexCompilerLocalD8Plan(
    val program: File,
    orderedClasspath: Collection<File>,
    orderedRuntimeLibraries: Collection<File>,
    val minimumApi: Int,
    inputSetSha256: String,
    runtimeLibrarySha256: String,
) {
    val orderedClasspath: List<File> = immutableFiles(orderedClasspath)
    val orderedRuntimeLibraries: List<File> = immutableFiles(orderedRuntimeLibraries)
    val inputSetSha256: String = normalizedSha256(inputSetSha256, "Input-set fingerprint")
    val runtimeLibrarySha256: String = normalizedSha256(
        runtimeLibrarySha256,
        "Runtime-library fingerprint",
    )
    val cacheKeySha256: String

    init {
        require(this.orderedClasspath.isNotEmpty()) { "Ordered classpath must not be empty" }
        require(minimumApi > 0) { "Minimum API must be positive" }
        cacheKeySha256 = DexCompilerLocalD8SemanticKey.sha256(
            inputSetSha256 = this.inputSetSha256,
            minimumApi = minimumApi,
            compilerIdentity = COMPILER_IDENTITY,
            runtimeLibrarySha256 = this.runtimeLibrarySha256,
        )
    }

    companion object {
        /** Must track the pinned app dependency in gradle/libs.versions.toml. */
        const val COMPILER_IDENTITY = "com.android.tools:r8:8.13.17/D8/release/dex-indexed"

        private fun immutableFiles(files: Collection<File>): List<File> =
            Collections.unmodifiableList(ArrayList(files))
    }
}

/** Local outputs cannot share the provider cache identity or its provenance fields. */
internal object DexCompilerLocalD8SemanticKey {
    private const val FORMAT_VERSION = 1
    private val DOMAIN = "AutoJs6/DexCompilerLocalD8SemanticKey"
        .toByteArray(StandardCharsets.US_ASCII)

    fun sha256(
        inputSetSha256: String,
        minimumApi: Int,
        compilerIdentity: String,
        runtimeLibrarySha256: String,
    ): String {
        val inputSet = normalizedSha256(inputSetSha256, "Input-set fingerprint").hexToBytes()
        val runtime = normalizedSha256(
            runtimeLibrarySha256,
            "Runtime-library fingerprint",
        ).hexToBytes()
        require(minimumApi > 0) { "Minimum API must be positive" }
        require(compilerIdentity.isNotBlank()) { "Compiler identity must not be blank" }
        val compiler = compilerIdentity.toByteArray(StandardCharsets.UTF_8)
        val canonical = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { data ->
                data.writeInt(DOMAIN.size)
                data.write(DOMAIN)
                data.writeInt(FORMAT_VERSION)
                data.writeInt(inputSet.size)
                data.write(inputSet)
                data.writeInt(minimumApi)
                data.writeInt(compiler.size)
                data.write(compiler)
                data.writeInt(runtime.size)
                data.write(runtime)
            }
        }.toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(canonical).toHex()
    }
}

/** Fingerprints the exact ordered library files that the local D8 invocation will consume. */
internal object DexCompilerLocalRuntimeLibraryFingerprint {
    private const val FORMAT_VERSION = 1
    private val DOMAIN = "AutoJs6/DexCompilerLocalRuntimeLibraries"
        .toByteArray(StandardCharsets.US_ASCII)

    fun sha256(
        runtimeBuildFingerprint: String,
        orderedLibraries: Collection<File>,
        fileSha256: (File) -> String,
    ): String {
        require(runtimeBuildFingerprint.isNotBlank()) { "Runtime build fingerprint must not be blank" }
        val libraries = orderedLibraries.toList()
        val canonical = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { data ->
                data.writeInt(DOMAIN.size)
                data.write(DOMAIN)
                data.writeInt(FORMAT_VERSION)
                data.writeSizedUtf8(runtimeBuildFingerprint)
                data.writeInt(libraries.size)
                libraries.forEach { library ->
                    data.writeSizedUtf8(library.absolutePath)
                    data.write(normalizedSha256(fileSha256(library), "Runtime library SHA-256").hexToBytes())
                }
            }
        }.toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(canonical).toHex()
    }

    private fun DataOutputStream.writeSizedUtf8(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }
}

private fun normalizedSha256(value: String, label: String): String {
    require(value.length == SHA256_HEX_LENGTH && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        "$label must contain exactly 32 hexadecimal bytes"
    }
    return value.lowercase()
}

private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { index ->
    substring(index * 2, index * 2 + 2).toInt(16).toByte()
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}

private const val SHA256_HEX_LENGTH = 64
