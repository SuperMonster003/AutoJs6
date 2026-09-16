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

        // History fileId that owned originalPath when the item was moved (null for directories and untracked files).
        // Informational: restore relocates history by path, so a stale id is harmless.
        // zh-CN: 移入时拥有 originalPath 的 history fileId (目录与未纳管文件为 null).
        // 仅供参考: 恢复时按路径迁移历史, 因此 id 过期也无害.
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
