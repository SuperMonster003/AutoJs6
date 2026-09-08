package org.autojs.autojs.core.plugin.explorer.archive

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

internal class ArchiveEntryTooLargeException(
    val limitBytes: Long,
) : IOException("Archive entry exceeds the preview limit")

/** Copies one verified plugin stream into a private, session-scoped host cache file. */
internal class ArchiveEntryMaterializer(
    cacheDirectory: File,
    sessionId: String,
) : Closeable {

    private val rootDirectory = File(cacheDirectory, CACHE_ROOT_NAME)
    private val sessionDirectory = File(rootDirectory, sha256(sessionId))
    private var retainedForOpening = false

    init {
        require(cacheDirectory.isDirectory || cacheDirectory.mkdirs()) {
            "Archive preview cache is unavailable"
        }
        purgeStaleSessions()
        require(sessionDirectory.isDirectory || sessionDirectory.mkdirs()) {
            "Archive preview session cache is unavailable"
        }
    }

    fun materialize(
        entryId: String,
        displayName: String,
        expectedSize: Long,
        maxBytes: Long,
        input: InputStream,
    ): File {
        require(expectedSize >= 0L) { "Archive entry size is invalid" }
        require(maxBytes >= 0L) { "Archive preview limit is invalid" }
        if (expectedSize > maxBytes) throw ArchiveEntryTooLargeException(maxBytes)
        ensureAvailableSpace(expectedSize)

        val entryDirectory = File(sessionDirectory, sha256(entryId))
        require(entryDirectory.isDirectory || entryDirectory.mkdirs()) {
            "Archive entry cache is unavailable"
        }
        val target = containedFile(entryDirectory, cacheFileName(displayName))
        if (target.isFile && target.length() == expectedSize) {
            target.setLastModified(System.currentTimeMillis())
            return target
        }
        if (target.exists() && !target.delete()) {
            throw IOException("Cannot replace an invalid archive preview cache file")
        }

        val temporary = File.createTempFile(".entry-", ".tmp", entryDirectory)
        try {
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytes = 0L
                while (true) {
                    if (Thread.currentThread().isInterrupted) {
                        throw InterruptedIOException("Archive entry materialization was cancelled")
                    }
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    if (bytes > expectedSize - read.toLong()) {
                        throw IOException("Archive entry exceeds its declared size")
                    }
                    output.write(buffer, 0, read)
                    bytes += read
                }
                output.flush()
                if (bytes != expectedSize) {
                    throw IOException("Archive entry does not match its declared size")
                }
            }
            if (!temporary.renameTo(target)) {
                throw IOException("Cannot publish the archive preview cache file")
            }
            sessionDirectory.setLastModified(System.currentTimeMillis())
            return target
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    override fun close() {
        // Viewers and script engines may read asynchronously after Explorer closes the archive.
        if (retainedForOpening) return
        deleteContainedTree(sessionDirectory)
        if (rootDirectory.listFiles().isNullOrEmpty()) rootDirectory.delete()
    }

    fun retainForOpening() {
        retainedForOpening = true
    }

    private fun ensureAvailableSpace(expectedSize: Long) {
        val usable = sessionDirectory.usableSpace
        if (usable < CACHE_SPACE_RESERVE_BYTES || expectedSize > usable - CACHE_SPACE_RESERVE_BYTES) {
            throw IOException("Insufficient private cache space for archive preview")
        }
    }

    private fun containedFile(parent: File, childName: String): File {
        val canonicalParent = parent.canonicalFile
        val child = File(parent, childName).canonicalFile
        require(child.parentFile?.canonicalFile == canonicalParent) {
            "Archive preview cache path escaped its entry root"
        }
        return child
    }

    private fun purgeStaleSessions() {
        val cutoff = System.currentTimeMillis() - STALE_SESSION_MILLIS
        rootDirectory.listFiles()
            ?.filter { it.isDirectory && it.lastModified() in 1 until cutoff }
            ?.forEach(::deleteContainedTree)
    }

    private fun deleteContainedTree(target: File) {
        val root = rootDirectory.canonicalFile
        val resolved = target.canonicalFile
        if (resolved.parentFile?.canonicalFile != root) return
        target.deleteRecursively()
    }

    private fun cacheFileName(displayName: String): String {
        val leafStart = maxOf(displayName.lastIndexOf('/'), displayName.lastIndexOf('\\')) + 1
        val leaf = displayName.substring(leafStart)
        if (leaf == displayName && leaf.length in 1..180 && leaf != "." && leaf != ".." &&
            leaf.none { it.code < 0x20 || it.code == 0x7f || it in "\\/:*?\"<>|" ||
                it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069' }
        ) {
            return leaf
        }
        val extensionStart = leaf.lastIndexOf('.')
        val extension = leaf
            .takeIf { extensionStart in 1 until it.lastIndex }
            ?.substring(extensionStart + 1)
            ?.takeIf { candidate ->
                candidate.length <= MAX_CACHE_EXTENSION_LENGTH &&
                    candidate.all { character ->
                        character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9'
                    }
            }
            ?.lowercase(Locale.ROOT)
        return extension?.let { "entry.$it" } ?: "entry"
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private companion object {
        const val CACHE_ROOT_NAME = "archive-entry-previews"
        const val BUFFER_SIZE = 32 * 1_024
        const val CACHE_SPACE_RESERVE_BYTES = 64L * 1024L * 1024L
        const val MAX_CACHE_EXTENSION_LENGTH = 16
        val STALE_SESSION_MILLIS = TimeUnit.HOURS.toMillis(24)
    }
}
