package org.autojs.autojs.model.explorer

import android.system.OsConstants
import androidx.annotation.Keep
import java.io.File

/** Retains inode, ownership and SELinux labels while refusing to replace a racing destination. */
@Keep
object PrivilegedFileNative {
    @Volatile private var libraryPath: String? = null
    @Volatile private var loaded = false

    fun configure(path: String?) {
        if (!path.isNullOrBlank()) {
            require(File(path).isAbsolute && File(path).name == "libautojs6_file_ops.so")
            libraryPath = path
        }
    }

    @Synchronized
    private fun ensureLoaded(): Boolean {
        if (loaded) return true
        return try {
            libraryPath?.let(System::load) ?: System.loadLibrary("autojs6_file_ops")
            loaded = true
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    @JvmStatic
    fun renameNoReplace(source: String, destination: String): Int {
        if (!ensureLoaded()) return OsConstants.ENOSYS
        return renameNoReplaceInternal(source.toByteArray(Charsets.UTF_8), destination.toByteArray(Charsets.UTF_8))
    }

    /** Keeps both directory entries recoverable throughout a file-editor save. */
    @JvmStatic
    fun exchange(source: String, destination: String): Int {
        if (!ensureLoaded()) return OsConstants.ENOSYS
        return nativeSaveCall { exchangeInternal(source.toByteArray(Charsets.UTF_8), destination.toByteArray(Charsets.UTF_8)) }
    }

    /** Copies ownership, permission bits and every readable xattr; mtime becomes the save time. */
    @JvmStatic
    fun copySaveMetadata(sourceFd: Int, destinationFd: Int): Int {
        if (!ensureLoaded()) return OsConstants.ENOSYS
        return nativeSaveCall { copySaveMetadataInternal(sourceFd, destinationFd) }
    }

    @JvmStatic
    fun compareSaveMetadata(sourceFd: Int, destinationFd: Int): Int {
        if (!ensureLoaded()) return OsConstants.ENOSYS
        return nativeSaveCall { compareSaveMetadataInternal(sourceFd, destinationFd) }
    }

    /** Advisory, nonblocking transaction lease; closing the owning open description releases it. */
    @JvmStatic
    fun tryLock(fd: Int): Int {
        if (!ensureLoaded()) return OsConstants.ENOSYS
        return nativeSaveCall { tryLockInternal(fd) }
    }

    // A long-lived authorized process may still have the older library loaded after an app
    // update. Missing save primitives must refuse the operation before modifying any file.
    private inline fun nativeSaveCall(action: () -> Int): Int = try {
        action()
    } catch (_: UnsatisfiedLinkError) {
        OsConstants.ENOSYS
    }

    @Keep
    @JvmStatic
    private external fun renameNoReplaceInternal(source: ByteArray, destination: ByteArray): Int

    @Keep
    @JvmStatic
    private external fun exchangeInternal(source: ByteArray, destination: ByteArray): Int

    @Keep
    @JvmStatic
    private external fun copySaveMetadataInternal(sourceFd: Int, destinationFd: Int): Int

    @Keep
    @JvmStatic
    private external fun compareSaveMetadataInternal(sourceFd: Int, destinationFd: Int): Int

    @Keep
    @JvmStatic
    private external fun tryLockInternal(fd: Int): Int
}
