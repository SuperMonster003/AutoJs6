package org.autojs.autojs.storage.history

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 */
class TrashRepository(private val context: Context) {

    private val db by lazy { HistoryDatabase.getInstance(context.applicationContext) }
    private val dao by lazy { db.historyDao() }
    private val blobs by lazy { TrashBlobStore(context.applicationContext) }

    /**
     * Move a file or directory into trash.
     *
     * Notes:
     * - Only internal storage paths are supported.
     * - Directory is archived as zip bytes.
     *
     * zh-CN:
     *
     * 将文件或目录移入回收站.
     *
     * 注意:
     * - 仅支持内部存储路径.
     * - 目录会被打包为 zip bytes.
     */
    fun moveToTrash(path: String): TrashEntities.TrashItem {
        val src = File(path)
        require(src.exists()) { "Source not exists: $path" }

        val logicalPath = path.trimEnd('/')

        val trashId = blobs.newTrashId()
        val now = System.currentTimeMillis()

        val (bytes, isDir) = if (src.isDirectory) {
            blobs.zipDirectoryToBytes(src) to true
        } else {
            src.inputStream().use { it.readBytes() } to false
        }

        val sha = sha256Hex(bytes)
        val rel = blobs.writeTrashBlob(trashId, bytes)

        // Delete original after blob persisted.
        // zh-CN: 在 blob 落盘后删除源文件/目录.
        val deleted = if (src.isDirectory) {
            src.deleteRecursively()
        } else {
            src.delete()
        }
        if (!deleted && src.exists()) {
            throw IllegalStateException("Failed to delete source after trash: $src")
        }

        val item = TrashEntities.TrashItem(
            trashId = trashId,
            originalPath = logicalPath,
            fileId = null,
            trashedAt = now,
            isDirectory = isDir,
            sizeBytes = bytes.size.toLong(),
            sha256 = sha,
            blobRelPath = rel,
        )
        dao.upsertTrashItem(item)
        return item
    }

    /**
     * Restore a trash item to a target path.
     *
     * Notes:
     * - For directories, dest must be a directory path.
     * - Restore will remove the trash item if succeeded.
     *
     * zh-CN:
     *
     * 将回收站条目恢复到目标路径.
     *
     * 说明:
     * - 若为目录, dest 必须是目录路径.
     * - 恢复成功后会删除回收站条目.
     */
    fun restoreTrashItemToPath(item: TrashEntities.TrashItem, dest: File) {
        val blobFile = File(context.filesDir, item.blobRelPath)
        require(blobFile.exists()) { "Trash blob not exists: ${item.blobRelPath}" }

        if (item.isDirectory) {
            dest.mkdirs()
            ZipInputStream(FileInputStream(blobFile)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val outFile = File(dest, entry.name)

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                        zis.closeEntry()
                        continue
                    }

                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { os -> zis.copyTo(os) }
                    zis.closeEntry()
                }
            }
        } else {
            dest.parentFile?.mkdirs()
            blobFile.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }

        // Remove trash record and blob after successful restore.
        // zh-CN: 恢复成功后删除回收站记录与 blob.
        dao.deleteTrashItemsByIds(listOf(item.trashId))
        blobs.deleteBlobByRelPath(item.blobRelPath)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format(Locale.US, "%02x", b))
        }
        return sb.toString()
    }
}
