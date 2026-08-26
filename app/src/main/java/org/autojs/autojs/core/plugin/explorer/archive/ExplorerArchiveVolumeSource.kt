package org.autojs.autojs.core.plugin.explorer.archive

import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerArchiveVolumeSourceKeys
import org.autojs.plugin.explorer.api.IExplorerArchiveVolumeSource
import java.io.Closeable
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Exposes only host-approved archive siblings. The plugin receives opaque IDs, never paths or an
 * unrestricted same-directory lookup primitive.
 */
internal class ExplorerArchiveVolumeSource private constructor(
    private val expectedUid: Int,
    candidates: List<VolumeCandidate>,
    private val callingUid: () -> Int,
) : IExplorerArchiveVolumeSource.Stub(), Closeable {

    private val closed = AtomicBoolean(false)
    private val openRequests = AtomicInteger(0)
    private val candidatesById = candidates.associateByTo(LinkedHashMap(), VolumeCandidate::id)

    override fun getCatalog(): Bundle {
        enforceCaller()
        checkOpen()
        val volumes = ArrayList<Bundle>(candidatesById.size)
        candidatesById.values.forEach { candidate ->
            candidate.requireUnchanged()
            volumes += candidate.toBundle()
        }
        return Bundle().apply {
            putParcelableArrayList(ExplorerArchiveVolumeSourceKeys.VOLUMES, volumes)
        }
    }

    override fun openVolume(volumeId: String): ParcelFileDescriptor {
        enforceCaller()
        checkOpen()
        require(volumeId.length in 1..ExplorerActionProtocol.MAX_ARCHIVE_VOLUME_ID_LENGTH) {
            "Archive volume ID length is invalid"
        }
        require(volumeId.none(::isInvalidOpaqueIdCharacter)) {
            "Archive volume ID is invalid"
        }
        check(openRequests.incrementAndGet() <= ExplorerActionProtocol.MAX_ARCHIVE_VOLUME_OPEN_REQUESTS) {
            "Archive volume open request limit was exceeded"
        }
        val candidate = requireNotNull(candidatesById[volumeId]) {
            "Archive volume ID is not part of this session"
        }
        candidate.requireUnchanged()
        val descriptor = ParcelFileDescriptor.open(
            candidate.file,
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
        return try {
            check(candidate.identity.matchesDescriptor(descriptor.fileDescriptor)) {
                "Archive volume changed while it was being opened"
            }
            descriptor
        } catch (error: Throwable) {
            descriptor.close()
            throw error
        }
    }

    override fun close() {
        closed.set(true)
    }

    private fun checkOpen() {
        check(!closed.get()) { "Archive volume source is closed" }
    }

    private fun enforceCaller() {
        if (callingUid() != expectedUid) {
            throw SecurityException(
                "Archive volume source caller UID does not match the launched plugin",
            )
        }
    }

    private data class VolumeCandidate(
        val id: String,
        val displayName: String,
        val file: File,
        val identity: VolumeIdentity,
    ) {
        fun requireUnchanged() {
            val current = captureCandidate(requireNotNull(file.parentFile), displayName)
            check(current != null && current.first == file && current.second == identity) {
                "Archive volume changed after the session was opened"
            }
        }

        fun toBundle(): Bundle = Bundle().apply {
            putString(ExplorerArchiveVolumeSourceKeys.VOLUME_ID, id)
            putString(ExplorerArchiveVolumeSourceKeys.DISPLAY_NAME, displayName)
            putLong(ExplorerArchiveVolumeSourceKeys.SIZE, identity.size)
            putLong(ExplorerArchiveVolumeSourceKeys.LAST_MODIFIED, identity.lastModifiedMillis)
        }
    }

    private data class VolumeIdentity(
        val device: Long,
        val inode: Long,
        val size: Long,
        val modifiedSeconds: Long,
        val lastModifiedMillis: Long,
    ) {
        fun matchesDescriptor(descriptor: FileDescriptor): Boolean {
            val stat = try {
                Os.fstat(descriptor)
            } catch (error: ErrnoException) {
                throw IOException("Archive volume descriptor cannot be inspected", error)
            }
            return OsConstants.S_ISREG(stat.st_mode) &&
                stat.st_dev == device &&
                stat.st_ino == inode &&
                stat.st_size == size &&
                stat.st_mtime == modifiedSeconds
        }

        companion object {
            fun capture(file: File): VolumeIdentity {
                val stat = try {
                    Os.stat(file.path)
                } catch (error: ErrnoException) {
                    throw IOException("Archive volume identity is unavailable", error)
                }
                check(OsConstants.S_ISREG(stat.st_mode)) {
                    "Archive volume is not a regular file"
                }
                return VolumeIdentity(
                    device = stat.st_dev,
                    inode = stat.st_ino,
                    size = stat.st_size,
                    modifiedSeconds = stat.st_mtime,
                    lastModifiedMillis = file.lastModified(),
                )
            }
        }
    }

    companion object {
        fun create(
            source: File,
            expectedUid: Int,
            callingUid: () -> Int = Binder::getCallingUid,
        ): ExplorerArchiveVolumeSource? {
            val canonicalSource = source.canonicalFile
            val parent = requireNotNull(canonicalSource.parentFile).canonicalFile
            val candidates = ExplorerArchiveVolumeNamePolicy
                .candidateNames(canonicalSource.name)
                .mapNotNull { displayName ->
                    captureCandidate(parent, displayName)?.let { (file, identity) ->
                        VolumeCandidate(
                            id = UUID.randomUUID().toString(),
                            displayName = displayName,
                            file = file,
                            identity = identity,
                        )
                    }
                }
            if (candidates.isEmpty()) return null
            check(candidates.size < ExplorerActionProtocol.MAX_ARCHIVE_VOLUMES) {
                "Archive volume candidate count is invalid"
            }
            return ExplorerArchiveVolumeSource(expectedUid, candidates, callingUid)
        }

        private fun captureCandidate(
            parent: File,
            displayName: String,
        ): Pair<File, VolumeIdentity>? {
            val unresolved = File(parent, displayName)
            val linkStat = try {
                Os.lstat(unresolved.path)
            } catch (_: ErrnoException) {
                return null
            }
            if (!OsConstants.S_ISREG(linkStat.st_mode) || OsConstants.S_ISLNK(linkStat.st_mode)) {
                return null
            }
            val candidate = try {
                unresolved.canonicalFile
            } catch (_: IOException) {
                return null
            }
            if (candidate.parentFile != parent || !candidate.isFile || !candidate.canRead()) return null
            val identity = try {
                VolumeIdentity.capture(candidate)
            } catch (_: IOException) {
                return null
            } catch (_: IllegalStateException) {
                return null
            }
            return candidate to identity
        }
    }
}

/** Produces the only sibling names that a host volume source may expose. */
internal object ExplorerArchiveVolumeNamePolicy {

    fun candidateNames(selectedDisplayName: String): List<String> {
        MODERN_RAR_PATTERN.matchEntire(selectedDisplayName)?.let { match ->
            val selectedIndex = match.groupValues[2].toIntOrNull()
                ?.takeIf { it in 1..ExplorerActionProtocol.MAX_ARCHIVE_VOLUMES }
                ?: return emptyList()
            val prefix = match.groupValues[1]
            val width = match.groupValues[2].length
            val suffix = match.groupValues[3]
            return (1..ExplorerActionProtocol.MAX_ARCHIVE_VOLUMES)
                .asSequence()
                .filter { it != selectedIndex }
                .map { index -> "$prefix${index.toString().padStart(width, '0')}$suffix" }
                .toList()
        }

        ZIP_PATTERN.matchEntire(selectedDisplayName)?.let { match ->
            val stem = match.groupValues[1]
            return (1 until ExplorerActionProtocol.MAX_ARCHIVE_VOLUMES).map { index ->
                "$stem.z${index.toString().padStart(MINIMUM_ZIP_INDEX_WIDTH, '0')}"
            }
        }

        LEGACY_RAR_PATTERN.matchEntire(selectedDisplayName)?.let { match ->
            val stem = match.groupValues[1]
            return (0 until ExplorerActionProtocol.MAX_ARCHIVE_VOLUMES - 1).map { index ->
                val prefix = ('r'.code + index / LEGACY_RAR_NUMERIC_RANGE).toChar()
                val number = index % LEGACY_RAR_NUMERIC_RANGE
                "$stem.$prefix${number.toString().padStart(2, '0')}"
            }
        }
        return emptyList()
    }

    private const val MINIMUM_ZIP_INDEX_WIDTH = 2
    private const val LEGACY_RAR_NUMERIC_RANGE = 100
    private val MODERN_RAR_PATTERN = Regex("(?i)^(.*\\.part)([0-9]+)(\\.rar)$")
    private val ZIP_PATTERN = Regex("(?i)^(.*)\\.zip$")
    private val LEGACY_RAR_PATTERN = Regex("(?i)^(.*)\\.rar$")
}

private fun isInvalidOpaqueIdCharacter(character: Char): Boolean =
    character.isWhitespace() || character.code < 0x20 || character.code == 0x7F
