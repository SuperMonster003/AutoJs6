package org.autojs.autojs.core.plugin.dex

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Crash-recoverable, host-private index for authenticated semantic DEX compiler cache entries.
 *
 * The caller must derive [cacheKeySha256] only after the provider handshake has authenticated every
 * input to [DexCompilerSemanticCacheKey]. The cache never accepts a pathname from that caller. A
 * validated key and a store-generated UUID are the only values used to derive child names.
 *
 * Each generation has an immutable artifact and an independently committed manifest. The manifest
 * rename is the commit point. This makes an artifact without a manifest an uncommitted crash
 * leftover, while two complete generations are safe: recovery selects one deterministically and
 * removes only the losing generation. A stale handle can therefore never delete a replacement.
 */
internal class DexCompilerPersistentCache(
    rootDirectory: File,
    private val fileOps: FileOps,
    private val limits: Limits = Limits(),
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {

    internal data class Limits(
        val maximumEntryBytes: Long = DEFAULT_MAXIMUM_ENTRY_BYTES,
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
        val lastAccessMillis: Long,
    )

    internal interface FileOps {
        fun makeOwnerReadWrite(file: File)

        fun makeOwnerReadOnly(file: File)

        fun isOwnerReadOnlyRegularFile(file: File): Boolean

        /** Verifies the already-open descriptor rather than repeating a pathname check. */
        fun isOwnerReadOnlyRegularFile(file: File, opened: FileInputStream): Boolean

        fun atomicReplace(source: File, destination: File)

        fun syncDirectory(directory: File)
    }

    private val lock = Any()
    private val root = rootDirectory.canonicalFile.also { directory ->
        require(directory.isDirectory) { "DEX compiler cache root must be a directory" }
    }
    private val entries = mutableMapOf<String, Entry>()

    init {
        synchronized(lock) { recoverLocked() }
    }

    /**
     * Publishes a verified same-directory temporary. [verified] is consumed on success.
     */
    fun publishVerified(
        cacheKeySha256: String,
        outputSha256: String,
        sizeBytes: Long,
        verified: File,
    ): Entry = synchronized(lock) {
        requireSha256(cacheKeySha256, "Semantic cache key")
        requireSha256(outputSha256, "DEX output SHA-256")
        require(sizeBytes in 1L..limits.maximumEntryBytes) { "DEX cache entry exceeds its quota" }
        val source = checkedChild(verified)
        requireValidArtifact(source, sizeBytes, outputSha256)

        val now = clockMillis().coerceAtLeast(0L)
        val entry = Entry(
            cacheKeySha256 = cacheKeySha256,
            generation = UUID.randomUUID(),
            outputSha256 = outputSha256,
            sizeBytes = sizeBytes,
            createdAtMillis = now,
            lastAccessMillis = now,
        )
        val artifact = artifactFile(entry)
        val manifest = manifestFile(entry)
        check(!artifact.exists() && !manifest.exists()) { "DEX cache generation already exists" }

        var artifactMoved = false
        var manifestCommitted = false
        try {
            fileOps.atomicReplace(source, artifact)
            artifactMoved = true
            fileOps.makeOwnerReadOnly(artifact)
            requireValidArtifact(artifact, sizeBytes, outputSha256)
            fileOps.syncDirectory(root)

            commitManifest(entry, manifest)
            manifestCommitted = true
            entries[cacheKeySha256] = entry

            // The new manifest is already durable. Every cleanup below is generation-specific, so
            // failure cannot remove the newly committed generation through a stale pathname.
            enforceLimitsLocked(protected = entry)
            removeOtherGenerationsLocked(entry)
            entry
        } catch (error: Throwable) {
            if (!manifestCommitted) {
                if (artifactMoved) deleteKnownFile(artifact)
                deleteKnownFile(manifest)
                fileOps.syncDirectory(root)
                throw error
            }
            // The manifest rename is the commit point. Cleanup or directory-sync failure after it
            // must not withdraw the new generation or expose a stale handle to a replacement. The
            // failure still propagates so callers never mistake an unsynced cleanup for success.
            entries[cacheKeySha256] = entry
            runCatching { enforceLimitsLocked(protected = entry) }
            throw error
        }
    }

    /** Looks up an exact post-handshake semantic key and revalidates bytes before returning it. */
    fun lookup(cacheKeySha256: String): Entry? = synchronized(lock) {
        requireSha256(cacheKeySha256, "Semantic cache key")
        val current = entries[cacheKeySha256] ?: return@synchronized null
        if (!isValidEntry(current)) {
            evictExactLocked(current)
            return@synchronized null
        }
        touchLocked(current)
    }

    /**
     * Opens the exact generation while holding the generation lock. The returned descriptor is
     * bound to that inode even if a later publication replaces the semantic key.
     */
    fun openRead(entry: Entry): InputStream? = synchronized(lock) {
        val current = entries[entry.cacheKeySha256] ?: return@synchronized null
        if (current.generation != entry.generation || current.outputSha256 != entry.outputSha256 ||
            current.sizeBytes != entry.sizeBytes
        ) {
            return@synchronized null
        }
        if (!isValidManifest(current)) {
            evictExactLocked(current)
            return@synchronized null
        }
        val opened = try {
            FileInputStream(artifactFile(current))
        } catch (_: Exception) {
            evictExactLocked(current)
            return@synchronized null
        }
        try {
            check(fileOps.isOwnerReadOnlyRegularFile(artifactFile(current), opened)) {
                "Opened DEX cache artifact is not owner-read-only and regular"
            }
            val summary = summarize(opened, limits.maximumEntryBytes)
            check(summary.sizeBytes == current.sizeBytes && summary.sha256 == current.outputSha256) {
                "Opened DEX cache artifact changed"
            }
            opened.channel.position(0L)
            opened
        } catch (_: Exception) {
            runCatching { opened.close() }
            evictExactLocked(current)
            null
        }
    }

    /** Invalidates only [entry]'s generation; stale handles never affect the current generation. */
    fun invalidate(entry: Entry): Boolean = synchronized(lock) {
        val current = entries[entry.cacheKeySha256] ?: return@synchronized false
        if (current.generation != entry.generation) return@synchronized false
        evictExactLocked(current)
    }

    private fun recoverLocked() {
        entries.clear()
        val files = root.listFiles().orEmpty()
        files.asSequence()
            .filter { CACHE_TEMP.matches(it.name) || LEGACY_UNINDEXED_ARTIFACT.matches(it.name) }
            .forEach(::deleteKnownFile)

        val candidates = mutableListOf<Entry>()
        root.listFiles().orEmpty().asSequence()
            .mapNotNull { file ->
                val match = MANIFEST_FILE.matchEntire(file.name) ?: return@mapNotNull null
                val pathKey = match.groupValues[1]
                val pathGeneration = runCatching { UUID.fromString(match.groupValues[2]) }.getOrNull()
                    ?: return@mapNotNull null
                val decoded = runCatching { decodeManifest(file) }.getOrNull()
                if (decoded == null || decoded.cacheKeySha256 != pathKey || decoded.generation != pathGeneration ||
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
            val winner = generations.minWithOrNull(NEWEST_FIRST) ?: return@forEach
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

    private fun touchLocked(entry: Entry): Entry {
        val now = clockMillis().coerceAtLeast(0L)
        if (now <= entry.lastAccessMillis) return entry
        val touched = entry.copy(lastAccessMillis = now)
        return try {
            commitManifest(touched, manifestFile(touched))
            entries[touched.cacheKeySha256] = touched
            touched
        } catch (_: Exception) {
            // Access-time persistence is an eviction hint. A failed touch must not turn a fully
            // verified artifact into a miss or destroy its previously committed manifest.
            val committed = runCatching { decodeManifest(manifestFile(entry)) }.getOrNull()
            if (committed != null && committed.cacheKeySha256 == entry.cacheKeySha256 &&
                committed.generation == entry.generation && committed.outputSha256 == entry.outputSha256 &&
                committed.sizeBytes == entry.sizeBytes
            ) {
                entries[committed.cacheKeySha256] = committed
                committed
            } else {
                entry
            }
        }
    }

    private fun commitManifest(entry: Entry, destination: File) {
        val temporary = checkedChild(File(root, "cache-index-${UUID.randomUUID()}.partial"))
        try {
            check(temporary.createNewFile()) { "Unable to reserve DEX cache manifest temporary" }
            fileOps.makeOwnerReadWrite(temporary)
            FileOutputStream(temporary, false).use { output ->
                output.write(encodeManifest(entry))
                output.fd.sync()
            }
            fileOps.makeOwnerReadOnly(temporary)
            check(fileOps.isOwnerReadOnlyRegularFile(temporary)) {
                "DEX cache manifest is not an owner-read-only regular file"
            }
            fileOps.atomicReplace(temporary, checkedChild(destination))
            fileOps.syncDirectory(root)
            check(fileOps.isOwnerReadOnlyRegularFile(destination)) {
                "DEX cache manifest lost its owner-read-only mode"
            }
        } finally {
            deleteKnownFile(temporary)
        }
    }

    private fun enforceLimitsLocked(protected: Entry?) {
        var totalBytes = entries.values.sumOf { it.sizeBytes }
        while (entries.size > limits.maximumEntries || totalBytes > limits.maximumTotalBytes) {
            val candidate = entries.values.asSequence()
                .filter { protected == null || it.generation != protected.generation }
                .minWithOrNull(OLDEST_FIRST)
                ?: throw IllegalStateException("DEX cache limits cannot retain the protected entry")
            if (!evictExactLocked(candidate)) {
                throw IllegalStateException("Unable to evict an over-quota DEX cache entry")
            }
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

    private fun isValidEntry(entry: Entry): Boolean {
        return try {
            if (!isValidManifest(entry)) return false
            requireValidArtifact(artifactFile(entry), entry.sizeBytes, entry.outputSha256)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isValidManifest(entry: Entry): Boolean {
        requireSha256(entry.cacheKeySha256, "Semantic cache key")
        requireSha256(entry.outputSha256, "DEX output SHA-256")
        require(entry.sizeBytes in 1L..limits.maximumEntryBytes)
        require(entry.createdAtMillis >= 0L && entry.lastAccessMillis >= entry.createdAtMillis)
        val manifest = manifestFile(entry)
        if (!fileOps.isOwnerReadOnlyRegularFile(manifest)) return false
        return decodeManifest(manifest) == entry
    }

    private fun requireValidArtifact(file: File, sizeBytes: Long, outputSha256: String) {
        require(fileOps.isOwnerReadOnlyRegularFile(file)) {
            "DEX cache artifact is not an owner-read-only regular file"
        }
        require(file.length() == sizeBytes) { "DEX cache artifact size changed" }
        require(sha256File(file) == outputSha256) { "DEX cache artifact digest changed" }
    }

    private fun decodeManifest(file: File): Entry {
        check(file.length() == MANIFEST_BYTES.toLong()) { "DEX cache manifest has the wrong size" }
        val encoded = FileInputStream(file).use { it.readBytesBounded(MANIFEST_BYTES) }
        check(encoded.size == MANIFEST_BYTES)
        val payload = encoded.copyOfRange(0, MANIFEST_PAYLOAD_BYTES)
        val claimedDigest = encoded.copyOfRange(MANIFEST_PAYLOAD_BYTES, encoded.size)
        val actualDigest = MessageDigest.getInstance("SHA-256").digest(payload)
        check(MessageDigest.isEqual(claimedDigest, actualDigest)) { "DEX cache manifest checksum changed" }
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            check(input.readInt() == MANIFEST_MAGIC) { "DEX cache manifest magic changed" }
            check(input.readInt() == MANIFEST_FORMAT_VERSION) { "Unsupported DEX cache manifest version" }
            val cacheKey = input.readBytesExact(SHA256_BYTES).toHex()
            val generation = UUID(input.readLong(), input.readLong())
            val outputSha256 = input.readBytesExact(SHA256_BYTES).toHex()
            val sizeBytes = input.readLong()
            val createdAtMillis = input.readLong()
            val lastAccessMillis = input.readLong()
            check(input.read() == -1)
            Entry(cacheKey, generation, outputSha256, sizeBytes, createdAtMillis, lastAccessMillis)
        }
    }

    private fun encodeManifest(entry: Entry): ByteArray {
        val payload = ByteArrayOutputStream(MANIFEST_PAYLOAD_BYTES)
        DataOutputStream(payload).use { output ->
            output.writeInt(MANIFEST_MAGIC)
            output.writeInt(MANIFEST_FORMAT_VERSION)
            output.write(entry.cacheKeySha256.hexToBytes())
            output.writeLong(entry.generation.mostSignificantBits)
            output.writeLong(entry.generation.leastSignificantBits)
            output.write(entry.outputSha256.hexToBytes())
            output.writeLong(entry.sizeBytes)
            output.writeLong(entry.createdAtMillis)
            output.writeLong(entry.lastAccessMillis)
        }
        val bytes = payload.toByteArray()
        check(bytes.size == MANIFEST_PAYLOAD_BYTES)
        return bytes + MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    private fun manifestFile(entry: Entry): File = manifestFile(entry.cacheKeySha256, entry.generation)

    private fun manifestFile(cacheKeySha256: String, generation: UUID): File =
        checkedChild(File(root, "$cacheKeySha256.$generation.entry"))

    private fun artifactFile(entry: Entry): File = artifactFile(entry.cacheKeySha256, entry.generation)

    private fun artifactFile(cacheKeySha256: String, generation: UUID): File =
        checkedChild(File(root, "$cacheKeySha256.$generation.dex.zip"))

    private fun checkedChild(file: File): File = file.canonicalFile.also { child ->
        require(child.parentFile == root) { "DEX compiler cache path escaped its private directory" }
    }

    private fun deleteKnownFile(file: File): Boolean {
        val child = checkedChild(file)
        return !child.exists() || child.delete() || !child.exists()
    }

    private companion object {
        const val DEFAULT_MAXIMUM_ENTRY_BYTES = 16L * 1024L * 1024L
        const val DEFAULT_MAXIMUM_TOTAL_BYTES = 128L * 1024L * 1024L
        const val DEFAULT_MAXIMUM_ENTRIES = 32
        const val MANIFEST_MAGIC = 0x414A4432
        const val MANIFEST_FORMAT_VERSION = 1
        const val SHA256_BYTES = 32
        const val MANIFEST_PAYLOAD_BYTES = 4 + 4 + SHA256_BYTES + 16 + SHA256_BYTES + 8 + 8 + 8
        const val MANIFEST_BYTES = MANIFEST_PAYLOAD_BYTES + SHA256_BYTES
        val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        val MANIFEST_FILE = Regex("([0-9a-f]{64})\\.($UUID_PATTERN)\\.entry")
        val ARTIFACT_FILE = Regex("([0-9a-f]{64})\\.($UUID_PATTERN)\\.dex\\.zip")
        val CACHE_TEMP = Regex("cache-index-$UUID_PATTERN\\.partial")
        val LEGACY_UNINDEXED_ARTIFACT = Regex("[0-9a-f]{64}\\.dex\\.zip")
        val OLDEST_FIRST = compareBy<Entry> { it.lastAccessMillis }
            .thenBy { it.createdAtMillis }
            .thenBy { it.cacheKeySha256 }
            .thenBy { it.generation.toString() }
        val NEWEST_FIRST = compareByDescending<Entry> { it.createdAtMillis }
            .thenByDescending { it.lastAccessMillis }
            .thenByDescending { it.generation.toString() }
    }
}

private fun requireSha256(value: String, label: String) {
    require(PERSISTENT_CACHE_SHA256.matches(value)) { "$label must be lowercase SHA-256" }
}

private val PERSISTENT_CACHE_SHA256 = Regex("[0-9a-f]{64}")

private fun sha256File(file: File): String = FileInputStream(file).use { input ->
    summarize(input, Long.MAX_VALUE).sha256
}

private data class DexCompilerCachedStreamSummary(
    val sizeBytes: Long,
    val sha256: String,
)

private fun summarize(input: InputStream, maximumBytes: Long): DexCompilerCachedStreamSummary {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        total = Math.addExact(total, count.toLong())
        check(total <= maximumBytes) { "DEX cache artifact exceeds its quota" }
        digest.update(buffer, 0, count)
    }
    return DexCompilerCachedStreamSummary(total, digest.digest().toHex())
}

private fun InputStream.readBytesBounded(expectedBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(expectedBytes)
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (count == 0) continue
        total = Math.addExact(total, count)
        check(total <= expectedBytes) { "DEX cache manifest exceeds its fixed size" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun DataInputStream.readBytesExact(size: Int): ByteArray = ByteArray(size).also(::readFully)

private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { index ->
    substring(index * 2, index * 2 + 2).toInt(16).toByte()
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
