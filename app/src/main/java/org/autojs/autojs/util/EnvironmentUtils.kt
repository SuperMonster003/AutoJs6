package org.autojs.autojs.util

import android.os.Environment
import java.io.File

object EnvironmentUtils {

    val externalStorageDirectory: File
        get() = Environment.getExternalStorageDirectory()

    @JvmStatic
    val externalStoragePath: String
        get() = externalStorageDirectory.path

    /**
     * Normalize a file path that starts with /sdcard (or legacy /mnt/sdcard) to the
     * canonical external storage path returned by Environment.getExternalStorageDirectory().
     *
     * On standard Android, /sdcard is a symlink to /storage/emulated/0, so File(path).exists()
     * returns true and canonicalPath resolves the symlink. On devices without the symlink
     * (Fire OS, some custom ROMs), the path is remapped to the real storage root.
     *
     * Path traversal (..) components are resolved by calling canonicalPath on existing files.
     *
     * Returns null when path is null; returns the original path when neither the given
     * path nor the normalized path resolves.
     */
    @JvmStatic
    fun normalizePath(path: String?): String? {
        if (path == null) return null
        val exists = try {
            val file = File(path)
            file.exists() && file.canonicalPath.let { true }
        } catch (_: Exception) {
            false
        }
        if (exists) return File(path).canonicalPath
        val externalPath = Environment.getExternalStorageDirectory().absolutePath
        if (externalPath == "/sdcard") return path
        val normalized = path.replaceFirst(
            Regex("^/sdcard(?=/|$)"),
            externalPath
        ).replaceFirst(
            Regex("^/mnt/sdcard(?=/|$)"),
            externalPath
        )
        if (normalized == path) return path
        return try {
            val nf = File(normalized)
            if (nf.exists()) nf.canonicalPath else path
        } catch (_: Exception) {
            path
        }
    }

}