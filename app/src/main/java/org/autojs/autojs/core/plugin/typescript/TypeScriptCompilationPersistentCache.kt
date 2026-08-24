package org.autojs.autojs.core.plugin.typescript

import android.system.Os
import android.system.OsConstants
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/** Crash-recoverable, host-private storage for authenticated TypeScript output archives. */
internal class TypeScriptCompilationPersistentCache(
    rootDirectory: File,
    private val fileOps: FileOps = AndroidFileOps,
    private val limits: Limits = Limits(),
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {

    internal data class Limits(
        val maximumEntryBytes: Long = TypeScriptCompilerContract.MAX_OUTPUT_ARCHIVE_BYTES,
        val maximumTotalBytes: Long = DEFAULT_MAXIMUM_TOTAL_BYTES,
        val maximumEntries: Int = DEFAULT_MAXIMUM_ENTRIES,
    ) {
        init {
            require(maximumEntryBytes > 0L)
            require(maximumTotalBytes >= maximumEntryBytes)
            require(maximumEntries > 0)
        }
    }

    internal data class Entry(
        val cacheKeySha256: String,
        val generation: UUID,
        val outputSha256: String,
        val sizeBytes: Long,
        val createdAtMillis: Long,
        val requestId: String,
        val inputArchiveSha256: String,
        val requestManifestSha256: String,
        val outcome: String,
        val diagnosticCount: Int,
        val emittedModuleCount: Int,
        val outputManifestSha256: String,
    ) {
        val terminal: TypeScriptTerminalResult
            get() = TypeScriptTerminalResult(
                requestId = requestId,
                outcome = outcome,
                succeeded = outcome == TypeScriptCompilerContract.OUTCOME_EMITTED,
                errorCode = null,
                errorMessage = null,
                diagnosticCount = diagnosticCount,
                emittedModuleCount = emittedModuleCount,
                outputManifestSha256 = outputManifestSha256,
            )

        fun replayPrepared(current: TypeScriptPreparedCompilation): TypeScriptPreparedCompilation =
            current.copy(
                requestId = requestId,
                inputArchiveSha256 = inputArchiveSha256,
                requestManifestSha256 = requestManifestSha256,
            )
    }

    internal data class Hit(
        val entry: Entry,
        val outputFile: File,
    )

    internal interface FileOps {
        fun makeOwnerReadWrite(file: File)

        fun makeOwnerReadOnly(file: File)

        fun isOwnerReadOnlyRegularFile(file: File): Boolean

        fun atomicReplace(source: File, destination: File)

        fun syncDirectory(directory: File)
    }

    private val lock = Any()
    private val root = rootDirectory.canonicalFile.also { directory ->
        require(directory.isDirectory) { "TypeScript compiler cache root must be a directory." }
    }
    private val entries = mutableMapOf<String, Entry>()

    init {
        synchronized(lock) { recoverLocked() }
    }

    /** Copies and commits an output archive. The caller retains ownership of [outputFile]. */
    fun publish(
        cacheKeySha256: String,
        prepared: TypeScriptPreparedCompilation,
        terminal: TypeScriptTerminalResult,
        outputFile: File,
    ): Entry = synchronized(lock) {
        requireLowerSha256(cacheKeySha256, "TypeScript semantic cache key")
        require(terminal.requestId == prepared.requestId)
        requireCacheableTerminal(terminal)
        require(outputFile.isFile)

        val temporary = checkedChild(File(root, "cache-output-${UUID.randomUUID()}.partial"))
        check(temporary.createNewFile()) { "Unable to reserve TypeScript cache temporary." }
        var artifactCommitted = false
        var manifestCommitted = false
        var entry: Entry? = null
        try {
            fileOps.makeOwnerReadWrite(temporary)
            val summary = copyAndSummarize(outputFile, temporary, limits.maximumEntryBytes)
            val now = clockMillis().coerceAtLeast(0L)
            val published = Entry(
                cacheKeySha256 = cacheKeySha256,
                generation = UUID.randomUUID(),
                outputSha256 = summary.sha256,
                sizeBytes = summary.sizeBytes,
                createdAtMillis = now,
                requestId = prepared.requestId,
                inputArchiveSha256 = prepared.inputArchiveSha256,
                requestManifestSha256 = prepared.requestManifestSha256,
                outcome = terminal.outcome,
                diagnosticCount = terminal.diagnosticCount,
                emittedModuleCount = terminal.emittedModuleCount,
                outputManifestSha256 = requireNotNull(terminal.outputManifestSha256),
            )
            entry = published
            validateEntryFields(published)

            val artifact = artifactFile(published)
            val manifest = manifestFile(published)
            check(!artifact.exists() && !manifest.exists()) {
                "TypeScript cache generation already exists."
            }
            fileOps.atomicReplace(temporary, artifact)
            artifactCommitted = true
            fileOps.makeOwnerReadOnly(artifact)
            requireValidArtifact(published)
            fileOps.syncDirectory(root)

            commitManifest(published)
            manifestCommitted = true
            entries[cacheKeySha256] = published
            enforceLimitsLocked(protected = published)
            removeOtherGenerationsLocked(published)
            published
        } catch (error: Throwable) {
            if (!manifestCommitted) {
                entry?.let { failed ->
                    if (artifactCommitted) deleteKnownFile(artifactFile(failed))
                    deleteKnownFile(manifestFile(failed))
                }
                runCatching { fileOps.syncDirectory(root) }
            } else {
                val committed = checkNotNull(entry)
                entries[cacheKeySha256] = committed
                runCatching { enforceLimitsLocked(protected = committed) }
            }
            throw error
        } finally {
            deleteKnownFile(temporary)
        }
    }

    /** Revalidates both metadata and archive bytes before returning an exact generation. */
    fun lookup(cacheKeySha256: String): Hit? = synchronized(lock) {
        requireLowerSha256(cacheKeySha256, "TypeScript semantic cache key")
        val entry = entries[cacheKeySha256] ?: return@synchronized null
        if (!isValidEntry(entry)) {
            evictExactLocked(entry)
            return@synchronized null
        }
        runCatching { manifestFile(entry).setLastModified(clockMillis().coerceAtLeast(0L)) }
        Hit(entry, artifactFile(entry))
    }

    /** Invalidates only the generation represented by [entry]. */
    fun invalidate(entry: Entry): Boolean = synchronized(lock) {
        val current = entries[entry.cacheKeySha256] ?: return@synchronized false
        if (current.generation != entry.generation) return@synchronized false
        evictExactLocked(current)
    }

    private fun recoverLocked() {
        entries.clear()
        root.listFiles().orEmpty().asSequence()
            .filter { TEMP_FILE.matches(it.name) }
            .forEach(::deleteKnownFile)

        val candidates = mutableListOf<Entry>()
        root.listFiles().orEmpty().asSequence()
            .mapNotNull { file ->
                val match = MANIFEST_FILE.matchEntire(file.name) ?: return@mapNotNull null
                val pathKey = match.groupValues[1]
                val pathGeneration = runCatching { UUID.fromString(match.groupValues[2]) }.getOrNull()
                    ?: return@mapNotNull null
                val decoded = runCatching { decodeManifest(file) }.getOrNull()
                if (
                    decoded == null ||
                    decoded.cacheKeySha256 != pathKey ||
                    decoded.generation != pathGeneration ||
                    !isValidEntry(decoded)
                ) {
                    deleteKnownFile(file)
                    deleteKnownFile(artifactFile(pathKey, pathGeneration))
                    return@mapNotNull null
                }
                decoded
            }
            .forEach(candidates::add)

        candidates.groupBy(Entry::cacheKeySha256).forEach { (cacheKey, generations) ->
            val winner = generations.maxWithOrNull(NEWEST_FIRST) ?: return@forEach
            entries[cacheKey] = winner
            generations.asSequence()
                .filter { it.generation != winner.generation }
                .forEach(::deleteGenerationFiles)
        }

        val referencedArtifacts = entries.values.mapTo(mutableSetOf()) { artifactFile(it).name }
        root.listFiles().orEmpty().asSequence()
            .filter { ARTIFACT_FILE.matches(it.name) && it.name !in referencedArtifacts }
            .forEach(::deleteKnownFile)

        enforceLimitsLocked(protected = null)
        fileOps.syncDirectory(root)
    }

    private fun commitManifest(entry: Entry) {
        val destination = manifestFile(entry)
        val temporary = checkedChild(File(root, "cache-index-${UUID.randomUUID()}.partial"))
        try {
            check(temporary.createNewFile()) { "Unable to reserve TypeScript cache index temporary." }
            fileOps.makeOwnerReadWrite(temporary)
            FileOutputStream(temporary, false).use { output ->
                output.write(encodeManifest(entry))
                output.fd.sync()
            }
            fileOps.makeOwnerReadOnly(temporary)
            check(fileOps.isOwnerReadOnlyRegularFile(temporary)) {
                "TypeScript cache index is not owner-read-only."
            }
            fileOps.atomicReplace(temporary, destination)
            fileOps.syncDirectory(root)
            check(fileOps.isOwnerReadOnlyRegularFile(destination)) {
                "TypeScript cache index lost its owner-read-only mode."
            }
        } finally {
            deleteKnownFile(temporary)
        }
    }

    private fun enforceLimitsLocked(protected: Entry?) {
        var totalBytes = entries.values.sumOf(Entry::sizeBytes)
        while (entries.size > limits.maximumEntries || totalBytes > limits.maximumTotalBytes) {
            val candidate = entries.values.asSequence()
                .filter { protected == null || it.generation != protected.generation }
                .minWithOrNull(
                    compareBy<Entry> { entry ->
                        manifestFile(entry).lastModified().takeIf { it > 0L }
                            ?: entry.createdAtMillis
                    }.thenBy(Entry::createdAtMillis).thenBy { it.generation.toString() },
                )
                ?: throw IllegalStateException(
                    "TypeScript cache limits cannot retain the protected entry.",
                )
            check(evictExactLocked(candidate)) { "Unable to evict a TypeScript cache entry." }
            totalBytes -= candidate.sizeBytes
        }
    }

    private fun removeOtherGenerationsLocked(current: Entry) {
        root.listFiles().orEmpty().asSequence()
            .mapNotNull { file ->
                val match = MANIFEST_FILE.matchEntire(file.name) ?: return@mapNotNull null
                if (match.groupValues[1] != current.cacheKeySha256) return@mapNotNull null
                val generation = runCatching { UUID.fromString(match.groupValues[2]) }.getOrNull()
                    ?: return@mapNotNull null
                generation.takeIf { it != current.generation }
            }
            .forEach { generation ->
                deleteKnownFile(manifestFile(current.cacheKeySha256, generation))
                deleteKnownFile(artifactFile(current.cacheKeySha256, generation))
            }
        fileOps.syncDirectory(root)
    }

    private fun evictExactLocked(entry: Entry): Boolean {
        val current = entries[entry.cacheKeySha256]
        if (current != null && current.generation != entry.generation) return false
        if (current != null) entries.remove(entry.cacheKeySha256)
        deleteGenerationFiles(entry)
        fileOps.syncDirectory(root)
        return !manifestFile(entry).exists() && !artifactFile(entry).exists()
    }

    private fun deleteGenerationFiles(entry: Entry) {
        deleteKnownFile(manifestFile(entry))
        deleteKnownFile(artifactFile(entry))
    }

    private fun isValidEntry(entry: Entry): Boolean = try {
        validateEntryFields(entry)
        decodeManifest(manifestFile(entry)) == entry && requireValidArtifact(entry)
    } catch (_: Exception) {
        false
    }

    private fun validateEntryFields(entry: Entry) {
        requireLowerSha256(entry.cacheKeySha256, "TypeScript semantic cache key")
        requireLowerSha256(entry.outputSha256, "TypeScript cached output fingerprint")
        requireLowerSha256(entry.inputArchiveSha256, "TypeScript cached input fingerprint")
        requireLowerSha256(entry.requestManifestSha256, "TypeScript cached request fingerprint")
        requireLowerSha256(entry.outputManifestSha256, "TypeScript cached manifest fingerprint")
        require(entry.sizeBytes in 1L..limits.maximumEntryBytes)
        require(entry.createdAtMillis >= 0L)
        require(entry.requestId.isNotBlank())
        require(entry.requestId.length <= TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS)
        require(entry.outcome in CACHEABLE_OUTCOMES)
        require(entry.diagnosticCount in 0..TypeScriptR2Contract.MAX_DIAGNOSTICS_COUNT)
        require(
            entry.emittedModuleCount in 0..
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES,
        )
    }

    private fun requireCacheableTerminal(terminal: TypeScriptTerminalResult) {
        require(terminal.outcome in CACHEABLE_OUTCOMES)
        require(
            terminal.succeeded ==
                (terminal.outcome == TypeScriptCompilerContract.OUTCOME_EMITTED),
        )
        require(terminal.diagnosticCount in 0..TypeScriptR2Contract.MAX_DIAGNOSTICS_COUNT)
        require(
            terminal.emittedModuleCount in 0..
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES,
        )
        requireLowerSha256(
            requireNotNull(terminal.outputManifestSha256),
            "TypeScript cached output manifest fingerprint",
        )
    }

    private fun requireValidArtifact(entry: Entry): Boolean {
        val artifact = artifactFile(entry)
        require(fileOps.isOwnerReadOnlyRegularFile(artifact))
        require(artifact.length() == entry.sizeBytes)
        require(sha256(artifact) == entry.outputSha256)
        return true
    }

    private fun decodeManifest(file: File): Entry {
        check(fileOps.isOwnerReadOnlyRegularFile(file))
        check(file.length() in (MINIMUM_MANIFEST_BYTES + 1L)..MAXIMUM_MANIFEST_BYTES.toLong())
        val encoded = FileInputStream(file).use { input ->
            input.readBytes().also { bytes -> check(bytes.size <= MAXIMUM_MANIFEST_BYTES) }
        }
        val payload = encoded.copyOfRange(0, encoded.size - SHA256_BYTES)
        val claimedDigest = encoded.copyOfRange(encoded.size - SHA256_BYTES, encoded.size)
        val actualDigest = MessageDigest.getInstance("SHA-256").digest(payload)
        check(MessageDigest.isEqual(claimedDigest, actualDigest))
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            check(input.readInt() == MANIFEST_MAGIC)
            check(input.readInt() == MANIFEST_FORMAT_VERSION)
            val cacheKey = input.readExact(SHA256_BYTES).toLowerHex()
            val generation = UUID(input.readLong(), input.readLong())
            val outputSha256 = input.readExact(SHA256_BYTES).toLowerHex()
            val sizeBytes = input.readLong()
            val createdAtMillis = input.readLong()
            val requestId = input.readText(TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS * 4)
            val inputArchiveSha256 = input.readExact(SHA256_BYTES).toLowerHex()
            val requestManifestSha256 = input.readExact(SHA256_BYTES).toLowerHex()
            val outcome = when (input.readInt()) {
                OUTCOME_EMITTED -> TypeScriptCompilerContract.OUTCOME_EMITTED
                OUTCOME_DIAGNOSTICS -> TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS
                else -> error("Unsupported TypeScript cached terminal outcome.")
            }
            val diagnosticCount = input.readInt()
            val emittedModuleCount = input.readInt()
            val outputManifestSha256 = input.readExact(SHA256_BYTES).toLowerHex()
            check(input.read() == -1)
            Entry(
                cacheKeySha256 = cacheKey,
                generation = generation,
                outputSha256 = outputSha256,
                sizeBytes = sizeBytes,
                createdAtMillis = createdAtMillis,
                requestId = requestId,
                inputArchiveSha256 = inputArchiveSha256,
                requestManifestSha256 = requestManifestSha256,
                outcome = outcome,
                diagnosticCount = diagnosticCount,
                emittedModuleCount = emittedModuleCount,
                outputManifestSha256 = outputManifestSha256,
            )
        }
    }

    private fun encodeManifest(entry: Entry): ByteArray {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).use { output ->
            output.writeInt(MANIFEST_MAGIC)
            output.writeInt(MANIFEST_FORMAT_VERSION)
            output.write(entry.cacheKeySha256.hexToBytes())
            output.writeLong(entry.generation.mostSignificantBits)
            output.writeLong(entry.generation.leastSignificantBits)
            output.write(entry.outputSha256.hexToBytes())
            output.writeLong(entry.sizeBytes)
            output.writeLong(entry.createdAtMillis)
            output.writeText(entry.requestId)
            output.write(entry.inputArchiveSha256.hexToBytes())
            output.write(entry.requestManifestSha256.hexToBytes())
            output.writeInt(
                if (entry.outcome == TypeScriptCompilerContract.OUTCOME_EMITTED) {
                    OUTCOME_EMITTED
                } else {
                    OUTCOME_DIAGNOSTICS
                },
            )
            output.writeInt(entry.diagnosticCount)
            output.writeInt(entry.emittedModuleCount)
            output.write(entry.outputManifestSha256.hexToBytes())
        }
        return payload.toByteArray().also { bytes ->
            check(bytes.size + SHA256_BYTES <= MAXIMUM_MANIFEST_BYTES)
        }.let { bytes -> bytes + MessageDigest.getInstance("SHA-256").digest(bytes) }
    }

    private fun manifestFile(entry: Entry): File =
        manifestFile(entry.cacheKeySha256, entry.generation)

    private fun manifestFile(cacheKey: String, generation: UUID): File =
        checkedChild(File(root, "$cacheKey.$generation.entry"))

    private fun artifactFile(entry: Entry): File =
        artifactFile(entry.cacheKeySha256, entry.generation)

    private fun artifactFile(cacheKey: String, generation: UUID): File =
        checkedChild(File(root, "$cacheKey.$generation.output.zip"))

    private fun checkedChild(file: File): File = file.canonicalFile.also { child ->
        require(child.parentFile == root) { "TypeScript compiler cache path escaped its root." }
    }

    private fun deleteKnownFile(file: File): Boolean {
        val child = checkedChild(file)
        return !child.exists() || child.delete() || !child.exists()
    }

    private companion object {
        const val DEFAULT_MAXIMUM_TOTAL_BYTES = 256L * 1024L * 1024L
        const val DEFAULT_MAXIMUM_ENTRIES = 32
        const val MANIFEST_MAGIC = 0x414A5453
        const val MANIFEST_FORMAT_VERSION = 1
        const val SHA256_BYTES = 32
        const val MINIMUM_MANIFEST_BYTES = 8 + 32 + 16 + 32 + 8 + 8 + 4 + 32 + 32 + 4 + 4 + 4 + 32 + 32
        const val MAXIMUM_MANIFEST_BYTES = 2 * 1024
        const val OUTCOME_EMITTED = 1
        const val OUTCOME_DIAGNOSTICS = 2
        val CACHEABLE_OUTCOMES = setOf(
            TypeScriptCompilerContract.OUTCOME_EMITTED,
            TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS,
        )
        val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        val MANIFEST_FILE = Regex("([0-9a-f]{64})\\.($UUID_PATTERN)\\.entry")
        val ARTIFACT_FILE = Regex("([0-9a-f]{64})\\.($UUID_PATTERN)\\.output\\.zip")
        val TEMP_FILE = Regex("cache-(?:output|index)-$UUID_PATTERN\\.partial")
        val NEWEST_FIRST = compareBy<Entry>(Entry::createdAtMillis)
            .thenBy { it.generation.toString() }
    }
}

private object AndroidFileOps : TypeScriptCompilationPersistentCache.FileOps {
    override fun makeOwnerReadWrite(file: File) {
        Os.chmod(file.absolutePath, OWNER_READ_WRITE)
    }

    override fun makeOwnerReadOnly(file: File) {
        Os.chmod(file.absolutePath, OWNER_READ_ONLY)
    }

    override fun isOwnerReadOnlyRegularFile(file: File): Boolean = try {
        val stat = Os.lstat(file.absolutePath)
        OsConstants.S_ISREG(stat.st_mode) && (stat.st_mode and PERMISSION_MASK) == OWNER_READ_ONLY
    } catch (_: Exception) {
        false
    }

    override fun atomicReplace(source: File, destination: File) {
        Os.rename(source.absolutePath, destination.absolutePath)
    }

    override fun syncDirectory(directory: File) {
        val descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

    private const val PERMISSION_MASK = 0x1ff
    private const val OWNER_READ_ONLY = 0x100
    private const val OWNER_READ_WRITE = 0x180
}

private data class TypeScriptCachedFileSummary(
    val sizeBytes: Long,
    val sha256: String,
)

private fun copyAndSummarize(source: File, destination: File, maximumBytes: Long): TypeScriptCachedFileSummary {
    val digest = MessageDigest.getInstance("SHA-256")
    var total = 0L
    FileInputStream(source).use { input ->
        FileOutputStream(destination, false).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total = Math.addExact(total, count.toLong())
                check(total <= maximumBytes) { "TypeScript cache entry exceeds its quota." }
                digest.update(buffer, 0, count)
                output.write(buffer, 0, count)
            }
            output.fd.sync()
        }
    }
    check(total > 0L) { "TypeScript cache output is empty." }
    return TypeScriptCachedFileSummary(total, digest.digest().toLowerHex())
}

private fun DataOutputStream.writeText(value: String) {
    val bytes = TypeScriptUtf8.encode(value, "TypeScript cache text")
    writeInt(bytes.size)
    write(bytes)
}

private fun DataInputStream.readText(maximumBytes: Int): String {
    val size = readInt()
    check(size in 1..maximumBytes)
    return TypeScriptUtf8.decode(readExact(size), "TypeScript cache text")
}

private fun DataInputStream.readExact(size: Int): ByteArray = ByteArray(size).also(::readFully)

private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { index ->
    substring(index * 2, index * 2 + 2).toInt(16).toByte()
}
