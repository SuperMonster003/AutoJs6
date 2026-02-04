package org.autojs.autojs.storage.history

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 */
class TrashBlobStore(private val context: Context) {

    fun newTrashId(): String = UUID.randomUUID().toString()

    fun writeTrashBlob(trashId: String, bytes: ByteArray): String {
        val rel = "trash/blob/$trashId.bin"
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
}
