package org.autojs.autojs.storage.history

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 7, 2026.
 */
class TrashBlobStore(private val context: Context) {

    fun newTrashId(): String = UUID.randomUUID().toString()

    /**
     * Open an OutputStream for writing trash blob into a temporary file, returns tempRelPath and stream.
     * zh-CN: 打开用于写入回收站 blob 的临时文件输出流, 并返回 tempRelPath 与 stream.
     */
    fun openTrashBlobTempOutputStream(trashId: String): Pair<String, OutputStream> {
        val tempRel = tempTrashBlobRelPath(trashId)
        val f = File(context.filesDir, tempRel)
        f.parentFile?.mkdirs()
        return tempRel to f.outputStream()
    }

    /**
     * Commit a temporary trash blob into its final path by atomic rename, returns final relPath.
     * zh-CN: 通过原子 rename 将临时回收站 blob 提交到最终路径, 并返回最终 relPath.
     */
    fun commitTrashBlobTemp(tempRelPath: String, trashId: String): String {
        val finalRel = finalTrashBlobRelPath(trashId)

        val tempFile = File(context.filesDir, tempRelPath)
        val finalFile = File(context.filesDir, finalRel)

        require(tempFile.exists()) { "Temp trash blob not exists: $tempRelPath" }

        finalFile.parentFile?.mkdirs()
        check(!finalFile.exists()) { "Final trash blob already exists: $finalRel" }

        FileOutputStream(tempFile, true).use { output -> output.fd.sync() }

        // Rename in the same directory is expected to be atomic on Android/Linux.
        // zh-CN: 同目录 rename 在 Android/Linux 上通常具备原子性.
        if (!tempFile.renameTo(finalFile)) {
            throw IllegalStateException("Commit trash blob failed: $tempFile -> $finalFile")
        }
        syncDirectory(requireNotNull(finalFile.parentFile))

        return finalRel
    }

    private fun syncDirectory(directory: File) {
        var descriptor: java.io.FileDescriptor? = null
        try {
            descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
            Os.fsync(descriptor)
        } catch (error: ErrnoException) {
            if (error.errno != OsConstants.EINVAL && error.errno != OsConstants.EOPNOTSUPP) {
                throw IOException("Trash blob directory could not be synchronized", error)
            }
        } finally {
            descriptor?.let { runCatching { Os.close(it) } }
        }
    }

    fun writeTrashBlob(trashId: String, bytes: ByteArray): String {
        val rel = finalTrashBlobRelPath(trashId)
        val f = File(context.filesDir, rel)
        f.parentFile?.mkdirs()
        f.outputStream().use { it.write(bytes) }
        return rel
    }

    fun deleteBlobByRelPath(relPath: String) {
        val f = File(context.filesDir, relPath)
        if (f.exists()) {
            // noinspection ResultOfMethodCallIgnored
            f.delete()
        }
    }

    /**
     * Cleanup stale temporary trash blobs (partial files).
     * zh-CN: 清理过期的临时回收站 blob (partial 文件).
     */
    fun cleanupStalePartialTrashBlobs(maxAgeMs: Long) {
        val dir = File(context.filesDir, TRASH_BLOB_DIR_REL)
        if (!dir.exists() || !dir.isDirectory) return

        val now = System.currentTimeMillis()
        val files = dir.listFiles() ?: return

        for (f in files) {
            if (!f.isFile) continue
            if (!f.name.contains(PARTIAL_MARK)) continue

            val age = now - f.lastModified()
            if (age < maxAgeMs) continue

            // Best-effort delete, ignore result.
            // zh-CN: 尽力删除, 忽略结果.
            runCatching {
                // noinspection ResultOfMethodCallIgnored
                f.delete()
            }
        }
    }

    private fun finalTrashBlobRelPath(trashId: String): String =
        "$TRASH_BLOB_DIR_REL/$trashId.bin"

    private fun tempTrashBlobRelPath(trashId: String): String =
        "$TRASH_BLOB_DIR_REL/$trashId$PARTIAL_MARK.${UUID.randomUUID()}.bin"

    /**
     * Pack a directory into zip bytes.
     * zh-CN: 将目录打包为 zip bytes.
     */
    fun zipDirectoryToBytes(dir: File): ByteArray {
        require(dir.isDirectory) { "Not a directory: $dir" }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val basePathLen = dir.absolutePath.trimEnd('/').length + 1
            dir.walkTopDown().forEach { f ->
                if (!f.isFile) return@forEach
                val relPath = f.absolutePath.substring(basePathLen).replace(File.separatorChar, '/')
                val entry = ZipEntry(relPath)
                zos.putNextEntry(entry)
                FileInputStream(f).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    companion object {
        const val TRASH_BLOB_DIR_REL: String = "trash/blob"
        const val PARTIAL_MARK: String = ".partial"
    }
}
