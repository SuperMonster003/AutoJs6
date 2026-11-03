package org.autojs.autojs.util

import android.os.Environment
import java.io.File

object EnvironmentUtils {

    private val legacySdcardPrefixes = listOf("/sdcard", "/mnt/sdcard")

    val externalStorageDirectory: File
        get() = Environment.getExternalStorageDirectory()

    @JvmStatic
    val externalStoragePath: String
        get() = externalStorageDirectory.path

    /**
     * Maps a `/sdcard` or `/mnt/sdcard` path that does not exist to the same location under [externalStorageRoot],
     * for devices without the `/sdcard` symlink (such as Fire OS and some custom ROMs). Existing paths, other paths,
     * and paths whose mapped location does not exist either are returned unchanged.
     *
     * zh-CN: 将不存在的 `/sdcard` 或 `/mnt/sdcard` 路径映射到 [externalStorageRoot] 下的相同位置, 用于没有 `/sdcard`
     * 软链接的设备 (如 Fire OS 及部分定制 ROM). 已存在的路径, 其他路径, 以及映射后仍不存在的路径原样返回.
     */
    @JvmStatic
    @JvmOverloads
    fun remapLegacySdcardPath(
        path: String,
        externalStorageRoot: String = externalStoragePath,
        exists: (String) -> Boolean = { File(it).exists() },
    ): String {
        if (exists(path)) return path
        val prefix = legacySdcardPrefixes.firstOrNull { path == it || path.startsWith("$it/") } ?: return path
        val root = externalStorageRoot.trimEnd('/')
        if (root.isEmpty() || root == prefix) return path
        val remapped = root + path.substring(prefix.length)
        return if (exists(remapped)) remapped else path
    }

}
