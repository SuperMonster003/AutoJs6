package org.autojs.autojs.storage.file

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Coordinates emergency-recovery publication and cleanup across app processes. */
object EmergencyDraftDirectoryGuard {

    private val localDirectoryLocks = ConcurrentHashMap<String, ReentrantLock>()
    private val activeRegistryLock = Any()
    private val heldDirectoryLockPaths = ThreadLocal<MutableSet<String>>()
    private val activeRecoveryPaths = mutableSetOf<String>()
    private val activeFileLocks = mutableMapOf<String, ActiveFileLock>()

    /**
     * Serializes all publication and cleanup work for [directory] in this process and in the
     * app's other processes. The persistent lock file lives beside the guarded directory, so it
     * can never be mistaken for a recovery file or included in recovery retention/quota scans.
     */
    fun <T> withLock(directory: File, action: () -> T): T {
        val lockFile = directoryLockFileFor(directory)
        val lockPath = canonicalPath(lockFile)
        val localLock = localDirectoryLocks.computeIfAbsent(lockPath) { ReentrantLock(true) }
        return localLock.withLock {
            val existingHeldPaths = heldDirectoryLockPaths.get()
            if (existingHeldPaths != null && lockPath in existingHeldPaths) {
                return@withLock action()
            }

            val parent = lockFile.parentFile
                ?: throw IOException("Emergency-draft lock has no parent: $lockFile")
            if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
                throw IOException("Cannot create emergency-draft lock directory: $parent")
            }

            RandomAccessFile(lockFile, "rw").use { randomAccessFile ->
                // Do not hold the active-file registry monitor while waiting for another process.
                val fileLock = randomAccessFile.channel.lock()
                val heldPaths = existingHeldPaths ?: mutableSetOf<String>().also {
                    heldDirectoryLockPaths.set(it)
                }
                heldPaths += lockPath
                try {
                    action()
                } finally {
                    heldPaths -= lockPath
                    if (heldPaths.isEmpty()) {
                        heldDirectoryLockPaths.remove()
                    }
                    runCatching { fileLock.release() }
                }
            }
        }
    }

    fun registerActive(file: File) = synchronized(activeRegistryLock) {
        val path = file.absolutePath
        activeRecoveryPaths += path
        if (path !in activeFileLocks) {
            acquireCrossProcessLock(lockFileFor(file))?.let { activeFileLocks[path] = it }
        }
    }

    fun releaseActive(files: Collection<File>) = synchronized(activeRegistryLock) {
        files.forEach { file ->
            val path = file.absolutePath
            activeRecoveryPaths -= path
            activeFileLocks.remove(path)?.close()
            val lockFile = lockFileFor(file)
            if (!isFileLockHeld(lockFile)) {
                runCatching { lockFile.delete() }
            }
        }
    }

    fun isProtectedFromDeletion(file: File, now: Long): Boolean = synchronized(activeRegistryLock) {
        if (file.absolutePath in activeRecoveryPaths) return@synchronized true
        val lockFile = if (file.name.endsWith(ACTIVE_LOCK_SUFFIX)) file else lockFileFor(file)
        if (lockFile.exists() && isFileLockHeld(lockFile)) return@synchronized true
        val age = now - file.lastModified()
        if (file.name.contains(PENDING_ORIGINAL_MARKER) && age in 0L..<PENDING_ORIGINAL_MIN_AGE_MS) {
            return@synchronized true
        }
        age >= 0L && age < MIN_RECOVERY_FILE_AGE_MS
    }

    private fun acquireCrossProcessLock(lockFile: File): ActiveFileLock? {
        val randomAccessFile = runCatching { RandomAccessFile(lockFile, "rw") }.getOrNull()
            ?: return null
        return try {
            val lock = try {
                randomAccessFile.channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock == null) {
                randomAccessFile.close()
                null
            } else {
                ActiveFileLock(randomAccessFile, lock, lockFile)
            }
        } catch (_: Throwable) {
            runCatching { randomAccessFile.close() }
            null
        }
    }

    private fun isFileLockHeld(lockFile: File): Boolean {
        if (!lockFile.exists()) return false
        if (activeFileLocks.values.any { it.lockFile.absolutePath == lockFile.absolutePath }) {
            return true
        }
        return try {
            RandomAccessFile(lockFile, "rw").use { randomAccessFile ->
                val lock = try {
                    randomAccessFile.channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    null
                }
                if (lock == null) {
                    true
                } else {
                    lock.release()
                    false
                }
            }
        } catch (_: Throwable) {
            // Cleanup must be conservative when lock ownership cannot be determined.
            true
        }
    }

    private fun lockFileFor(file: File): File = File(file.absolutePath + ACTIVE_LOCK_SUFFIX)

    private fun directoryLockFileFor(directory: File): File {
        val absoluteDirectory = directory.absoluteFile
        val parent = absoluteDirectory.parentFile
            ?: throw IOException("Emergency-draft directory has no parent: $directory")
        return File(parent, ".${absoluteDirectory.name}$DIRECTORY_LOCK_SUFFIX")
    }

    private fun canonicalPath(file: File): String =
        runCatching { file.canonicalPath }.getOrElse { file.absolutePath }

    private class ActiveFileLock(
        private val randomAccessFile: RandomAccessFile,
        private val lock: FileLock,
        val lockFile: File,
    ) {
        fun close() {
            runCatching { lock.release() }
            runCatching { randomAccessFile.close() }
        }
    }

    // Give the user enough time to act on a just-shown recovery path even when retention or
    // quota preferences are set to zero.
    // zh-CN: 即使保留时间或容量设置为 0, 也为刚显示的恢复路径保留足够的处理时间.
    private const val ACTIVE_LOCK_SUFFIX = ".active"
    private const val DIRECTORY_LOCK_SUFFIX = ".emergency-draft.lock"
    private const val PENDING_ORIGINAL_MARKER = "_original.pending."
    private const val PENDING_ORIGINAL_MIN_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    private const val MIN_RECOVERY_FILE_AGE_MS = 30L * 60L * 1000L
}
