package org.autojs.autojs.storage.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 */
object TrashEntities {

    @Entity(
        tableName = "trash_item",
        indices = [
            Index(value = ["originalPath"]),
            Index(value = ["trashedAt"]),
        ],
    )
    data class TrashItem(
        @PrimaryKey
        val trashId: String,

        // Original logical path under /storage/emulated/0.
        // zh-CN: 原始 logical path, 位于 /storage/emulated/0 下.
        val originalPath: String,

        // Optional: link to history fileId if we can resolve it later.
        // zh-CN: 可选: 未来可关联到 history 的 fileId.
        val fileId: String?,

        val trashedAt: Long,

        // Whether the blob represents a directory archive.
        // zh-CN: blob 是否为目录打包归档.
        val isDirectory: Boolean,

        val sizeBytes: Long,

        val sha256: String?,

        // Blob relative path under filesDir.
        // zh-CN: 位于 filesDir 下的相对路径.
        val blobRelPath: String,
    )
}
