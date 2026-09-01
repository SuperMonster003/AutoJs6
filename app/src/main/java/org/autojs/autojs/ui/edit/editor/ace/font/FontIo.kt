package org.autojs.autojs.ui.edit.editor.ace.font

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

internal object FontIo {
    fun catalogLockFile(rootDirectory: File): File = resourceLockFile(rootDirectory, "catalog.lock")

    fun storeLockFile(rootDirectory: File): File = resourceLockFile(rootDirectory, "store.lock")

    /**
     * Serializes a disk transaction both between threads and between application processes.
     * The JVM lock is required because overlapping FileChannel locks throw instead of waiting.
     */
    fun <T> withFileLock(lockFile: File, action: () -> T): T {
        lockFile.parentFile?.let(::ensureDirectory)
        val key = canonicalPath(lockFile)
        val processLock = PROCESS_LOCKS.computeIfAbsent(key) { ReentrantLock() }
        processLock.lock()
        try {
            if (processLock.holdCount > 1) return action()
            RandomAccessFile(lockFile, "rw").use { file ->
                val systemLock = acquireSystemLock(file.channel)
                try {
                    return action()
                } finally {
                    systemLock.release()
                }
            }
        } finally {
            processLock.unlock()
        }
    }

    /**
     * The host and ACE plugin each ship their own FontIo class but share this on-disk store in one
     * JVM. Their private [PROCESS_LOCKS] maps cannot coordinate with each other, while the JVM-wide
     * FileChannel lock table reports that contention by throwing instead of waiting. Retry only
     * that condition so the lock retains its normal blocking semantics across both implementations.
     */
    private fun acquireSystemLock(channel: FileChannel): FileLock {
        while (true) {
            try {
                return channel.lock()
            } catch (_: OverlappingFileLockException) {
                try {
                    Thread.sleep(OVERLAPPING_FILE_LOCK_RETRY_MILLIS)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw interrupted
                }
            }
        }
    }

    /** Caller must hold the stable catalog/store resource lock. */
    fun recoverBackup(target: File): Boolean {
        if (target.exists()) return true
        val backup = File(target.parentFile, "${target.name}.bak")
        return backup.isFile && backup.renameTo(target)
    }

    /** Caller must hold the stable catalog/store resource lock. */
    fun writeAndReplace(target: File, write: (FileOutputStream) -> Unit) {
        target.parentFile?.let(::ensureDirectory)
        val temporaryPrefix = ".${target.name}.tmp-"
        target.parentFile?.listFiles { file ->
            file.isFile && file.name.startsWith(temporaryPrefix) && file.name.endsWith(".part")
        }?.forEach(File::delete)
        val temporary = File.createTempFile(temporaryPrefix, ".part", target.parentFile)
        val backup = File(target.parentFile, "${target.name}.bak")
        recoverBackup(target)
        try {
            FileOutputStream(temporary).use { output ->
                write(output)
                output.flush()
                output.fd.sync()
            }
            replaceRecoverably(temporary, target, backup)
        } finally {
            temporary.delete()
        }
    }

    private fun replaceRecoverably(temporary: File, target: File, backup: File) {
        // Android/Linux rename replaces atomically. Windows unit tests use the backup fallback.
        if (temporary.renameTo(target)) {
            backup.delete()
            return
        }

        if (!target.exists()) throw IllegalStateException("Unable to commit ${target.absolutePath}")
        backup.delete()
        if (!target.renameTo(backup)) {
            throw IllegalStateException("Unable to prepare replacement of ${target.absolutePath}")
        }
        if (temporary.renameTo(target)) {
            backup.delete()
            return
        }
        backup.renameTo(target)
        throw IllegalStateException("Unable to commit ${target.absolutePath}")
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IllegalStateException("Unable to create ${directory.absolutePath}")
        }
    }

    private fun canonicalPath(file: File): String = runCatching { file.canonicalPath }
        .getOrElse { file.absolutePath }

    private fun resourceLockFile(rootDirectory: File, name: String): File =
        File(File(rootDirectory, ".locks"), name)

    private const val OVERLAPPING_FILE_LOCK_RETRY_MILLIS = 10L
    private val PROCESS_LOCKS = ConcurrentHashMap<String, ReentrantLock>()
}
