package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8ArtifactRole
import org.autojs.plugin.r8compiler.api.R8CompileRequest
import org.autojs.plugin.r8compiler.api.R8CompilerCapabilities
import org.autojs.plugin.r8compiler.api.R8CompilerCodec
import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.autojs.plugin.r8compiler.api.R8CompilerValidation
import org.autojs.plugin.r8compiler.api.R8RequestId
import org.autojs.plugin.r8compiler.api.R8Result
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * R8-only generation cache. A same-directory directory rename is the commit point; incomplete
 * `partial-*` generations are never visible to lookup and are removed during startup recovery.
 */
internal class R8CompilerPersistentArtifactStore(
    rootDirectory: File,
    private val limits: Limits = Limits(),
) : R8CompilerArtifactStore {
    internal data class Limits(
        val maximumEntries: Int = 32,
        val maximumEntryBytes: Long = 2L * R8CompilerContract.MAX_OUTPUT_BUNDLE_BYTES +
            R8CompilerContract.MAX_METADATA_BYTES,
        val maximumTotalBytes: Long = 1024L * 1024 * 1024,
    ) {
        init {
            require(maximumEntries > 0)
            require(maximumEntryBytes > 0L)
            require(maximumTotalBytes >= maximumEntryBytes)
        }
    }

    private val lock = Any()
    private val root = rootDirectory.canonicalFile

    init {
        if (!root.exists()) check(root.mkdirs()) { "Unable to create R8 cache root" }
        require(root.isDirectory) { "R8 cache root is not a directory" }
        synchronized(lock) { recoverLocked() }
    }

    override fun lookup(
        cacheKeySha256: String,
        request: R8CompileRequest,
        capabilities: R8CompilerCapabilities,
    ): R8CompilerStoredArtifact? = synchronized(lock) {
        requireSha256(cacheKeySha256)
        matchingGenerations(cacheKeySha256).forEach { directory ->
            val loaded = try {
                loadGeneration(directory, cacheKeySha256, request, capabilities)
            } catch (_: Exception) {
                null
            }
            if (loaded != null) {
                directory.setLastModified(System.currentTimeMillis().coerceAtLeast(0L))
                matchingGenerations(cacheKeySha256)
                    .filter { it.name != directory.name }
                    .forEach(::deleteGeneration)
                return@synchronized loaded
            }
            deleteGeneration(directory)
        }
        null
    }

    override fun publishVerified(
        cacheKeySha256: String,
        verified: R8CompilerVerifiedArtifacts,
        request: R8CompileRequest,
        capabilities: R8CompilerCapabilities,
    ): R8CompilerStoredArtifact = synchronized(lock) {
        requireSha256(cacheKeySha256)
        R8CompilerArtifactFileValidation.validate(verified, request, capabilities)
        val generation = UUID.randomUUID().toString().lowercase()
        val partial = child("partial-$generation")
        val destination = child("entry-$cacheKeySha256-$generation")
        check(partial.mkdir()) { "Unable to reserve R8 cache generation" }
        var committed = false
        try {
            copyExact(
                verified.outputBundleFile,
                File(partial, OUTPUT_BUNDLE),
                verified.result.outputBundleSizeBytes,
                verified.result.outputBundleSha256.toByteArray(),
            )
            verified.result.artifactIdentities.forEach { identity ->
                copyExact(
                    checkNotNull(verified.artifactFiles[identity.role]),
                    File(partial, fileName(identity.role)),
                    identity.sizeBytes,
                    identity.contentSha256.toByteArray(),
                )
            }
            val encodedResult = R8CompilerCodec.encodeResult(verified.result)
            require(encodedResult.size <= R8CompilerContract.MAX_METADATA_BYTES)
            FileOutputStream(File(partial, RESULT_METADATA), false).use { output ->
                output.write(encodedResult)
                output.fd.sync()
            }
            val entryBytes = generationSize(partial)
            require(entryBytes <= limits.maximumEntryBytes) { "R8 cache generation exceeds its quota" }
            validateCopiedGeneration(partial, verified.result, request, capabilities)
            check(partial.renameTo(destination)) { "Unable to commit R8 cache generation" }
            committed = true
            matchingGenerations(cacheKeySha256)
                .filter { it.name != destination.name }
                .forEach(::deleteGeneration)
            enforceLimitsLocked(destination)
            loadGeneration(destination, cacheKeySha256, request, capabilities)
        } finally {
            if (!committed) deleteGeneration(partial)
        }
    }

    private fun loadGeneration(
        directory: File,
        cacheKeySha256: String,
        request: R8CompileRequest,
        capabilities: R8CompilerCapabilities,
    ): StoredArtifact {
        val match = ENTRY.matchEntire(directory.name) ?: error("R8 cache generation name is invalid")
        require(match.groupValues[1] == cacheKeySha256)
        require(directory.canonicalFile.parentFile == root && directory.isDirectory)
        require(generationSize(directory) <= limits.maximumEntryBytes)
        val metadata = readBounded(File(directory, RESULT_METADATA), R8CompilerContract.MAX_METADATA_BYTES)
        val cached = R8CompilerCodec.decodeResult(metadata)
        val rebound = cached.withRequestId(request.requestId)
        R8CompilerValidation.validateResultAgainst(rebound, request, capabilities)
        val verified = generationArtifacts(directory, rebound)
        R8CompilerArtifactFileValidation.validate(verified, request, capabilities)
        return StoredArtifact(cacheKeySha256, match.groupValues[2], rebound, directory.name)
    }

    private fun validateCopiedGeneration(
        directory: File,
        result: R8Result,
        request: R8CompileRequest,
        capabilities: R8CompilerCapabilities,
    ) {
        R8CompilerArtifactFileValidation.validate(
            generationArtifacts(directory, result),
            request,
            capabilities,
        )
    }

    private fun generationArtifacts(directory: File, result: R8Result): R8CompilerVerifiedArtifacts =
        R8CompilerVerifiedArtifacts(
            File(directory, OUTPUT_BUNDLE),
            result,
            R8ArtifactRole.values().associateWith { role -> File(directory, fileName(role)) },
        )

    private fun matchingGenerations(cacheKeySha256: String): List<File> =
        root.listFiles().orEmpty().asSequence()
            .filter { directory ->
                val match = ENTRY.matchEntire(directory.name)
                directory.isDirectory && match?.groupValues?.get(1) == cacheKeySha256
            }
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            .toList()

    private fun recoverLocked() {
        root.listFiles().orEmpty().forEach { child ->
            when {
                PARTIAL.matches(child.name) && child.isDirectory -> deleteGeneration(child)
                ENTRY.matches(child.name) && !child.isDirectory -> child.delete()
                ENTRY.matches(child.name) && generationSizeOrNull(child) == null -> deleteGeneration(child)
            }
        }
        enforceLimitsLocked(protected = null)
    }

    private fun enforceLimitsLocked(protected: File?) {
        val generations = root.listFiles().orEmpty()
            .filter { it.isDirectory && ENTRY.matches(it.name) }
            .mapNotNull { directory ->
                val size = generationSizeOrNull(directory)
                if (size == null || size > limits.maximumEntryBytes) {
                    deleteGeneration(directory)
                    null
                } else {
                    Generation(directory, size)
                }
            }
            .sortedWith(
                compareBy<Generation> { it.directory.lastModified() }
                    .thenBy { it.directory.name },
            )
            .toMutableList()
        var total = generations.sumOf(Generation::sizeBytes)
        while (generations.size > limits.maximumEntries || total > limits.maximumTotalBytes) {
            val victim = generations.firstOrNull {
                protected == null || it.directory.name != protected.name
            }
                ?: error("R8 cache limits cannot retain the committed generation")
            check(deleteGeneration(victim.directory)) { "Unable to evict an R8 cache generation" }
            generations.remove(victim)
            total -= victim.sizeBytes
        }
    }

    private fun openGenerationFile(
        entry: StoredArtifact,
        fileName: String,
        expectedSize: Long,
        expectedSha256: ByteArray,
    ): FileInputStream =
        synchronized(lock) {
            require(expectedSha256.size == SHA256_BYTES)
            val directory = child("entry-${entry.cacheKeySha256}-${entry.generation}")
            require(directory.name == entry.directoryName && directory.isDirectory) {
                "R8 cache generation is no longer available"
            }
            val file = File(directory, fileName).canonicalFile
            require(file.parentFile == directory && file.isFile && file.length() == expectedSize) {
                "R8 cache artifact changed"
            }
            FileInputStream(file).also { input ->
                try {
                    require(input.sha256().contentEquals(expectedSha256)) {
                        "R8 cache artifact digest changed"
                    }
                    input.channel.position(0L)
                } catch (error: Throwable) {
                    try {
                        input.close()
                    } catch (closeError: Throwable) {
                        if (closeError !== error) error.addSuppressed(closeError)
                    }
                    throw error
                }
            }
        }

    private fun copyExact(
        source: File,
        destination: File,
        expectedSize: Long,
        expectedSha256: ByteArray,
    ) {
        require(source.isFile && source.length() == expectedSize)
        require(expectedSha256.size == SHA256_BYTES)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        FileInputStream(source).use { input ->
            FileOutputStream(destination, false).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    total = Math.addExact(total, read.toLong())
                    require(total <= expectedSize)
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                }
                output.fd.sync()
            }
        }
        require(total == expectedSize)
        require(digest.digest().contentEquals(expectedSha256))
    }

    private fun generationSize(directory: File): Long {
        require(directory.canonicalFile.parentFile == root)
        val files = directory.listFiles().orEmpty()
        require(files.map(File::getName).toSet() == GENERATION_FILES)
        return files.fold(0L) { total, file ->
            require(file.canonicalFile.parentFile == directory.canonicalFile && file.isFile)
            Math.addExact(total, file.length())
        }
    }

    private fun generationSizeOrNull(directory: File): Long? = try {
        generationSize(directory)
    } catch (_: Exception) {
        null
    }

    private fun deleteGeneration(directory: File): Boolean {
        if (!directory.exists()) return true
        if (directory.canonicalFile.parentFile != root || !directory.isDirectory) return false
        var success = true
        directory.listFiles().orEmpty().forEach { file ->
            if (file.canonicalFile.parentFile != directory.canonicalFile || file.isDirectory || !file.delete()) {
                success = false
            }
        }
        return directory.delete() && success
    }

    private fun child(name: String): File = File(root, name).canonicalFile.also {
        require(it.parentFile == root)
    }

    private fun readBounded(file: File, maximumBytes: Int): ByteArray {
        require(file.isFile && file.length() in 1..maximumBytes.toLong())
        return file.readBytes().also { require(it.size <= maximumBytes) }
    }

    private fun requireSha256(value: String) {
        require(SHA256.matches(value)) { "R8 semantic cache key must be SHA-256" }
    }

    private fun fileName(role: R8ArtifactRole): String = when (role) {
        R8ArtifactRole.DEX_ZIP -> "dex.zip"
        R8ArtifactRole.MAPPING_TEXT -> "mapping.txt"
        R8ArtifactRole.SEEDS_TEXT -> "seeds.txt"
        R8ArtifactRole.USAGE_TEXT -> "usage.txt"
        R8ArtifactRole.RETRACE_METADATA -> "retrace.bin"
    }

    private fun InputStream.sha256(): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
        return digest.digest()
    }

    private data class Generation(
        val directory: File,
        val sizeBytes: Long,
    )

    private inner class StoredArtifact(
        override val cacheKeySha256: String,
        override val generation: String,
        override val result: R8Result,
        val directoryName: String,
    ) : R8CompilerStoredArtifact {
        override fun openOutputBundle(): InputStream = openGenerationFile(
            this,
            OUTPUT_BUNDLE,
            result.outputBundleSizeBytes,
            result.outputBundleSha256.toByteArray(),
        )

        override fun openArtifact(role: R8ArtifactRole): InputStream {
            val identity = result.artifactIdentities.single { it.role == role }
            return openGenerationFile(
                this,
                fileName(role),
                identity.sizeBytes,
                identity.contentSha256.toByteArray(),
            )
        }
    }

    private companion object {
        const val OUTPUT_BUNDLE = "output.bundle"
        const val RESULT_METADATA = "result.bin"
        const val SHA256_BYTES = 32
        val GENERATION_FILES = setOf(
            OUTPUT_BUNDLE,
            RESULT_METADATA,
            "dex.zip",
            "mapping.txt",
            "seeds.txt",
            "usage.txt",
            "retrace.bin",
        )
        val SHA256 = Regex("[0-9a-f]{64}")
        val UUID_TEXT = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        val PARTIAL = Regex("partial-$UUID_TEXT")
        val ENTRY = Regex("entry-([0-9a-f]{64})-($UUID_TEXT)")
    }
}

private fun R8Result.withRequestId(requestId: R8RequestId): R8Result = R8Result(
    requestId = requestId,
    compilerFamily = compilerFamily,
    compilerVersion = compilerVersion,
    capabilityFingerprint = capabilityFingerprint,
    runtimeLibraryFingerprint = runtimeLibraryFingerprint,
    inputSetFingerprint = inputSetFingerprint,
    profile = profile,
    minApi = minApi,
    outputLayout = outputLayout,
    outputBundleSizeBytes = outputBundleSizeBytes,
    outputBundleSha256 = outputBundleSha256,
    artifactIdentities = artifactIdentities,
    determinismClaim = determinismClaim,
    elapsedMillis = elapsedMillis,
    diagnostics = diagnostics,
)
