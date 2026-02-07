package org.autojs.autojs.storage.history

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 3, 2026.
 * Modified by SuperMonster003 as of Feb 6, 2026.
 */
object HistoryEntities {

    @Entity(
        tableName = "file_entry",
        indices = [
            Index(value = ["logicalPath"], unique = true),
            Index(value = ["latestFingerprint"]),
            Index(value = ["lastSeenAt"]),
        ],
    )
    data class FileEntry(
        @PrimaryKey
        val fileId: String,

        val logicalPath: String,

        val createdAt: Long,
        val lastSeenAt: Long,

        // Latest content fingerprint for "path lost but content same" matching.
        // zh-CN: 用于 "路径丢失但内容相同" 匹配的最新内容指纹.
        val latestFingerprint: String,
    ) {
        val displayPath: String
            get() = runCatching { Uri.decode(logicalPath) }.getOrElse { logicalPath }

        val canonicalPath: String
            get() = logicalPath
    }

    @Entity(
        tableName = "revision",
        indices = [
            Index(value = ["fileId"]),
            Index(value = ["createdAt"]),
        ],
    )
    data class Revision(
        @PrimaryKey
        val revId: String,

        val fileId: String,

        // Operation type for future extension (SAVE_PRE / RESTORE / TRASH etc.).
        // zh-CN: 操作类型, 供未来扩展 (SAVE_PRE / RESTORE / TRASH 等).
        val op: String,

        val createdAt: Long,

        val logicalPathAtThatTime: String,

        val encoding: String,
        val hadBom: Boolean,

        val sizeBytes: Long,

        val sha256: String,

        // Blob relative path under filesDir.
        // zh-CN: 位于 filesDir 下的相对路径.
        val blobRelPath: String,
    )
}
