package org.autojs.autojs.core.plugin.typescript

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/**
 * Produces a versioned identity for every input that can change TypeScript compilation output.
 *
 * Request IDs and request-archive fingerprints are deliberately excluded: they authenticate one
 * Binder request but are regenerated for otherwise identical executions. Source/config/package
 * fingerprints and the complete compiler authority are included instead.
 */
internal object TypeScriptCompilationCacheKey {

    private const val FORMAT_VERSION = 4
    private val DOMAIN =
        "AutoJs6/TypeScriptCompilationCacheKey".toByteArray(StandardCharsets.US_ASCII)

    fun sha256(prepared: TypeScriptPreparedCompilation): String =
        MessageDigest.getInstance("SHA-256").digest(canonicalBytes(prepared)).toLowerHex()

    fun canonicalBytes(prepared: TypeScriptPreparedCompilation): ByteArray {
        val sources = prepared.sourceFiles.sortedBy(TypeScriptPreparedSource::archivePath)
        require(sources.map(TypeScriptPreparedSource::archivePath).distinct().size == sources.size) {
            "TypeScript cache source paths must be unique."
        }

        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(DOMAIN.size)
            data.write(DOMAIN)
            data.writeInt(FORMAT_VERSION)

            data.writeText(prepared.projectId)
            data.writeText(prepared.entrySourceUri)
            data.writeText(prepared.entryPath)
            data.writeText(TypeScriptCompilerContract.MODE_DEVELOPMENT)
            data.writeText("{}")

            data.writeInt(TypeScriptCompilerContract.VERSION)
            data.writeText(TypeScriptCompilerContract.HASH)
            data.writeInt(TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION)
            data.writeInt(TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION)
            data.writeInt(TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION)
            data.writeInt(TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION)
            data.writeInt(TypeScriptR2Contract.DIAGNOSTICS_SCHEMA_REVISION)
            data.writeInt(TypeScriptR2Contract.ORDINARY_COMPILATION_INPUT_QUOTA_REVISION)
            data.writeInt(TypeScriptR2Contract.ORDINARY_COMPILATION_OUTPUT_QUOTA_REVISION)

            data.writeInt(prepared.rootPaths.size)
            prepared.rootPaths.forEach { path -> data.writeText(path) }

            data.writeInt(sources.size)
            sources.forEach { source ->
                data.writeText(source.archivePath)
                data.writeText(source.projectRelativePath)
                data.writeSha256(source.sha256)
            }

            data.writeNullableLong(prepared.configByteLength)
            data.writeNullableSha256(prepared.configSha256)

            val packageMetadata = prepared.packageMetadata
            data.writeBoolean(packageMetadata != null)
            packageMetadata?.let { metadata ->
                data.writeInt(metadata.dependencyRoots.size)
                metadata.dependencyRoots.forEach { root -> data.writeText(root) }
                data.writeInt(metadata.files.size)
                metadata.files.forEach { file ->
                    data.writeText(file.archivePath)
                    data.writeLong(file.size)
                    data.writeSha256(file.sha256)
                    data.writeText(file.typeState)
                }
                data.writeSha256(metadata.inventoryFingerprint)
            }

            val dependencyLayer = prepared.dependencyLayer
            data.writeBoolean(dependencyLayer != null)
            dependencyLayer?.let { layer ->
                data.writeText(layer.identityKind)
                data.writeText(layer.identityPath)
                data.writeSha256(layer.identityFingerprint)
                data.writeSha256(layer.inventoryFingerprint)
                data.writeSha256(layer.fingerprint)
                data.writeLong(layer.totalBytes)
                data.writeLong(layer.totalPathBytes)
                data.writeInt(layer.files.size)
                layer.files.forEach { file ->
                    data.writeText(file.archivePath)
                    data.writeLong(file.size)
                    data.writeSha256(file.sha256)
                }
            }

            data.writeAuthority(prepared.authority)
        }
        return output.toByteArray()
    }

    private fun DataOutputStream.writeAuthority(authority: TypeScriptCompilerAuthority) {
        writeText(authority.targetProfile)
        writeText(authority.compilerVersion)
        writeSha256(authority.compilerDistributionFingerprint)
        writeSha256(authority.declarationsFingerprint)
        writeSha256(authority.declarationProfileFingerprint)
        writeSha256(authority.runtimeCapabilitiesFingerprint)
        writeInt(authority.profileSchemaRevision)
        writeInt(authority.profileRevision)
        writeInt(authority.configSchemaRevision)
        writeText(authority.configKind)
        writeNullableInt(authority.configPolicyRevision)
        writeNullableSha256(authority.configPolicyFingerprint)
        writeNullableInt(authority.packageMetadataSchemaRevision)
        writeNullableInt(authority.packageMetadataPolicyRevision)
        writeNullableSha256(authority.packageMetadataPolicyFingerprint)
        writeNullableInt(authority.dependencyLayerSchemaRevision)
        writeNullableInt(authority.dependencyLayerPolicyRevision)
        writeNullableSha256(authority.dependencyLayerPolicyFingerprint)
        writeNullableInt(authority.dependencyResolverPolicyRevision)
        writeNullableSha256(authority.dependencyResolverPolicyFingerprint)
    }

    private fun DataOutputStream.writeText(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.writeSha256(value: String) {
        requireLowerSha256(value, "TypeScript cache fingerprint")
        write(value.hexToBytes())
    }

    private fun DataOutputStream.writeNullableSha256(value: String?) {
        writeBoolean(value != null)
        value?.let { fingerprint -> writeSha256(fingerprint) }
    }

    private fun DataOutputStream.writeNullableInt(value: Int?) {
        writeBoolean(value != null)
        value?.let(::writeInt)
    }

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        value?.let(::writeLong)
    }

    private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
