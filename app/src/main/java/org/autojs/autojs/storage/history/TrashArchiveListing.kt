package org.autojs.autojs.storage.history

import java.io.File
import java.util.zip.ZipFile

/**
 * Listing of a directory blob (zip) in the trash, plus a cheap "is this text" sniff for file blobs.
 * Reads the central directory only, so sizes are known without inflating anything.
 *
 * zh-CN: 回收站目录 blob (zip) 的条目列表, 以及文件 blob 的 "是否文本" 粗判. 只读中央目录, 无需解压即可得到大小.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object TrashArchiveListing {

    const val DEFAULT_MAX_ENTRIES: Int = 500
    const val BINARY_SNIFF_BYTES: Int = 8 * 1024

    data class Entry(val relativePath: String, val sizeBytes: Long, val isDirectory: Boolean)

    /**
     * [entries] holds at most `maxEntries` items ([truncated] says whether more exist); the counts
     * and [totalBytes] always cover the whole archive.
     * zh-CN: [entries] 最多 `maxEntries` 项 ([truncated] 表示还有更多); 计数与 [totalBytes] 始终覆盖整个归档.
     */
    data class Listing(
        val entries: List<Entry>,
        val truncated: Boolean,
        val fileCount: Int,
        val dirCount: Int,
        val totalBytes: Long,
    )

    fun read(zip: File, maxEntries: Int = DEFAULT_MAX_ENTRIES): Listing {
        val entries = ArrayList<Entry>()
        var truncated = false
        var fileCount = 0
        var dirCount = 0
        var totalBytes = 0L
        ZipFile(zip).use { zf ->
            val e = zf.entries()
            while (e.hasMoreElements()) {
                val zipEntry = e.nextElement()
                val isDir = zipEntry.isDirectory
                val size = if (isDir) 0L else zipEntry.size.coerceAtLeast(0L)
                if (isDir) dirCount++ else fileCount++
                totalBytes += size
                if (entries.size < maxEntries) {
                    entries.add(Entry(zipEntry.name, size, isDir))
                } else {
                    truncated = true
                }
            }
        }
        return Listing(entries, truncated, fileCount, dirCount, totalBytes)
    }

    /**
     * A NUL byte in the first [BINARY_SNIFF_BYTES] bytes is taken as "not text".
     * zh-CN: 前 [BINARY_SNIFF_BYTES] 字节中出现 NUL 即视为非文本.
     */
    fun looksBinary(head: ByteArray): Boolean = head.any { it == 0.toByte() }
}
