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

    @Keep
    @JvmStatic
    private external fun renameNoReplaceInternal(source: ByteArray, destination: ByteArray): Int
}
