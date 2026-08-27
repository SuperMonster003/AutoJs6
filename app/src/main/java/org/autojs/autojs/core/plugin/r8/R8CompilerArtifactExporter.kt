package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8ArtifactContentValidation
import org.autojs.plugin.r8compiler.api.R8ArtifactRole
import org.autojs.plugin.r8compiler.api.R8CompilerCodec
import org.autojs.plugin.r8compiler.api.R8CompilerValidation
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** Rehashes four cached reports into isolated staging, then publishes one immutable directory. */
internal object R8CompilerArtifactExporter {
    private val roles = listOf(
        R8ArtifactRole.MAPPING_TEXT,
        R8ArtifactRole.SEEDS_TEXT,
        R8ArtifactRole.USAGE_TEXT,
        R8ArtifactRole.RETRACE_METADATA,
    )

    fun export(artifact: R8CompilerStoredArtifact, destinationDirectory: File): File {
        val destination = destinationDirectory.canonicalFile
        val parent = requireNotNull(destination.parentFile) {
            "R8 export directory must have a parent"
        }.canonicalFile
        require(destination.name.isNotBlank() && destination.parentFile == parent) {
            "R8 export directory is invalid"
        }
        if (!parent.exists()) check(parent.mkdirs()) { "Unable to create R8 export parent" }
        require(parent.isDirectory) { "R8 export parent is not a directory" }
        require(!destination.exists()) { "R8 export destination already exists" }
        val staging = File(parent, ".${destination.name}.partial-${UUID.randomUUID()}").canonicalFile
        require(staging.parentFile == parent && !staging.exists()) { "R8 export staging path is invalid" }
        check(staging.mkdir()) { "Unable to create R8 export staging directory" }
        var published = false
        try {
            val identities = artifact.result.artifactIdentities.associateBy { it.role }
            roles.forEach { role ->
                val identity = checkNotNull(identities[role]) { "R8 export artifact identity is missing" }
                val destinationFile = File(staging, fileName(role)).canonicalFile
                require(destinationFile.parentFile == staging)
                artifact.openArtifact(role).use { input ->
                    copyAndVerify(input, destinationFile, identity.sizeBytes, identity.contentSha256.toByteArray())
                }
                when (role) {
                    R8ArtifactRole.MAPPING_TEXT ->
                        R8ArtifactContentValidation.validateText(destinationFile.readBytes(), requireContent = true)
                    R8ArtifactRole.SEEDS_TEXT,
                    R8ArtifactRole.USAGE_TEXT,
                    -> R8ArtifactContentValidation.validateText(destinationFile.readBytes(), requireContent = false)
                    R8ArtifactRole.RETRACE_METADATA -> R8CompilerValidation.validateRetraceMetadataAgainst(
                        R8CompilerCodec.decodeRetraceMetadata(destinationFile.readBytes()),
                        artifact.result,
                    )
                    R8ArtifactRole.DEX_ZIP -> error("DEX is outside the R8 report export boundary")
                }
            }
            try {
                Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(staging.toPath(), destination.toPath())
            }
            published = true
            return destination
        } finally {
            if (!published && staging.exists()) {
                staging.listFiles().orEmpty().forEach { child ->
                    if (child.canonicalFile.parentFile == staging && child.isFile && child.name in FILE_NAMES) {
                        check(child.delete()) { "Unable to remove failed R8 export staging file" }
                    }
                }
                check(staging.delete()) { "Unable to remove failed R8 export staging directory" }
            }
        }
    }

    private fun copyAndVerify(
        input: InputStream,
        destination: File,
        expectedSize: Long,
        expectedSha256: ByteArray,
    ) {
        require(expectedSize >= 0L && expectedSha256.size == 32)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        FileOutputStream(destination, false).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total = Math.addExact(total, read.toLong())
                require(total <= expectedSize) { "R8 export source exceeds its admitted size" }
                output.write(buffer, 0, read)
                digest.update(buffer, 0, read)
            }
            output.fd.sync()
        }
        require(total == expectedSize) { "R8 export source is truncated" }
        require(digest.digest().contentEquals(expectedSha256)) { "R8 export source digest changed" }
    }

    private fun fileName(role: R8ArtifactRole): String = when (role) {
        R8ArtifactRole.MAPPING_TEXT -> "mapping.txt"
        R8ArtifactRole.SEEDS_TEXT -> "seeds.txt"
        R8ArtifactRole.USAGE_TEXT -> "usage.txt"
        R8ArtifactRole.RETRACE_METADATA -> "retrace-metadata.bin"
        R8ArtifactRole.DEX_ZIP -> error("DEX is outside the R8 report export boundary")
    }

    private val FILE_NAMES = roles.map(::fileName).toSet()
}
