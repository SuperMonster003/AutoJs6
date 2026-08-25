package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8ArtifactBundleCodec
import org.autojs.plugin.r8compiler.api.R8ArtifactContentValidation
import org.autojs.plugin.r8compiler.api.R8ArtifactIdentity
import org.autojs.plugin.r8compiler.api.R8ArtifactRole
import org.autojs.plugin.r8compiler.api.R8CompileRequest
import org.autojs.plugin.r8compiler.api.R8CompilerCapabilities
import org.autojs.plugin.r8compiler.api.R8CompilerCodec
import org.autojs.plugin.r8compiler.api.R8CompilerValidation
import org.autojs.plugin.r8compiler.api.R8Result
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Collections

internal class R8CompilerVerifiedArtifacts(
    val outputBundleFile: File,
    val result: R8Result,
    artifactFiles: Map<R8ArtifactRole, File>,
) {
    val artifactFiles: Map<R8ArtifactRole, File> = Collections.unmodifiableMap(
        LinkedHashMap(artifactFiles),
    )

    init {
        require(this.artifactFiles.keys.toList() == R8ArtifactRole.values().toList())
    }
}

/** Consumes the canonical bundle into isolated staging and publishes nothing until all checks pass. */
internal object R8CompilerArtifactAdopter {
    fun adopt(
        outputBundleFile: File,
        result: R8Result,
        request: R8CompileRequest,
        capabilities: R8CompilerCapabilities,
        workspace: R8CompilerTransactionWorkspace,
    ): R8CompilerVerifiedArtifacts {
        val files = linkedMapOf<R8ArtifactRole, File>()
        FileInputStream(outputBundleFile).use { input ->
            R8ArtifactBundleCodec.read(input, result, request, capabilities) { identity, entry ->
                val file = workspace.artifactFile(identity.role)
                require(files.put(identity.role, file) == null) { "R8 artifact role is duplicated" }
                FileOutputStream(file, false).use { output ->
                    entry.copyTo(output)
                    output.fd.sync()
                }
            }
        }
        val verified = R8CompilerVerifiedArtifacts(outputBundleFile, result, files)
        R8CompilerArtifactFileValidation.validate(verified, request, capabilities)
        return verified
    }
}

internal object R8CompilerArtifactFileValidation {
    fun validate(
        verified: R8CompilerVerifiedArtifacts,
        request: R8CompileRequest,
        capabilities: R8CompilerCapabilities,
    ) {
        R8CompilerValidation.validateResultAgainst(verified.result, request, capabilities)
        FileInputStream(verified.outputBundleFile).use { input ->
            R8ArtifactBundleCodec.read(input, verified.result, request, capabilities) { _, _ -> Unit }
        }
        val identities = verified.result.artifactIdentities.associateBy(R8ArtifactIdentity::role)
        R8ArtifactRole.values().forEach { role ->
            val identity = checkNotNull(identities[role])
            val file = checkNotNull(verified.artifactFiles[role])
            require(file.isFile && file.length() == identity.sizeBytes) { "R8 artifact file size changed" }
            require(sha256(file).contentEquals(identity.contentSha256.toByteArray())) {
                "R8 artifact file digest changed"
            }
            when (role) {
                R8ArtifactRole.DEX_ZIP -> R8CompilerDexZipValidator.validate(
                    file,
                    capabilities.limits.maxDexEntries,
                    capabilities.limits.maxDexZipBytes,
                )
                R8ArtifactRole.MAPPING_TEXT ->
                    R8ArtifactContentValidation.validateText(file.readBytes(), requireContent = true)
                R8ArtifactRole.SEEDS_TEXT,
                R8ArtifactRole.USAGE_TEXT,
                -> R8ArtifactContentValidation.validateText(file.readBytes(), requireContent = false)
                R8ArtifactRole.RETRACE_METADATA -> R8CompilerValidation.validateRetraceMetadataAgainst(
                    R8CompilerCodec.decodeRetraceMetadata(file.readBytes()),
                    verified.result,
                )
            }
        }
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }
}
