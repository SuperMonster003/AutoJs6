package org.autojs.autojs.storage.history

import android.content.Context
import org.autojs.autojs.ui.common.ScriptOperations
import org.autojs.autojs.util.DialogUtils
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 * Modified by SuperMonster003 as of Feb 7, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 7, 2026.
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
     * - Directory is archived as zip stream (not bytes) to avoid OOM.
     *
     * zh-CN:
     *
     * 将文件或目录移入回收站.
     *
     * 注意:
     * - 仅支持内部存储路径.
     * - 目录会以 zip 流形式归档 (不再生成 bytes), 以避免 OOM.
     */
    /**
     * Move a file or directory into trash.
     *
     * Notes:
     * - Only internal storage paths are supported.
     * - Directory is archived as zip stream (not bytes) to avoid OOM.
     *
     * zh-CN:
     *
     * 将文件或目录移入回收站.
     *
     * 注意:
     * - 仅支持内部存储路径.
     * - 目录会以 zip 流形式归档 (不再生成 bytes), 以避免 OOM.
     */
    fun moveToTrashWithProgress(
        path: String,
        progressCallback: ScriptOperations.ProgressCallback?,
        fileCallback: ScriptOperations.FileProgressCallback?,
        operationController: DialogUtils.OperationController,
    ): TrashEntities.TrashItem {
        operationController.throwIfCancelled()

        val src = File(path)
        require(src.exists()) { "Source not exists: $path" }

        val logicalPath = path.trimEnd('/')

        val trashId = blobs.newTrashId()
        val now = System.currentTimeMillis()

        val isDir = src.isDirectory

        val digest = MessageDigest.getInstance("SHA-256")
        var sizeBytes = 0L

        // Compute total bytes asynchronously because walking a directory can be expensive.
        // zh-CN: 异步计算 totalBytes, 因为目录 walk 可能很耗时.
        val totalReady = AtomicBoolean(false)
        val totalBytesRef = AtomicLong(0L)

        if (src.isFile) {
            totalBytesRef.set(src.length().coerceAtLeast(0L))
            totalReady.set(true)
        } else {
            Thread {
                runCatching {
                    fun computeTotalBytesCancellable(f: File): Long {
                        operationController.throwIfCancelled()
                        if (!f.exists()) return 0L
                        if (f.isFile) return f.length().coerceAtLeast(0L)

                        var sum = 0L
                        val children = f.listFiles() ?: return 0L
                        for (c in children) {
                            operationController.throwIfCancelled()
                            sum += computeTotalBytesCancellable(c)
                        }
                        return sum
                    }

                    val total = computeTotalBytesCancellable(src).coerceAtLeast(0L)
                    totalBytesRef.set(total)
                    totalReady.set(true)
                }
            }.apply {
                // Best-effort background computation; should not block main operation.
                // zh-CN: 尽力而为的后台计算, 不应阻塞主流程.
                isDaemon = true
                name = "TrashTotalBytes-$trashId"
                start()
            }
        }

        val processedBytes = AtomicLong(0L)

        // Report progress; if total not ready, pass 0 as requested.
        // zh-CN: 上报进度; 若 total 尚未计算完成, 按约定传入 0.
        fun reportProcessed(delta: Int) {
            if (delta <= 0) return
            val p = processedBytes.addAndGet(delta.toLong())
            val t = if (totalReady.get()) totalBytesRef.get().coerceAtLeast(0L) else 0L
            progressCallback?.onProgress(p.coerceAtLeast(0L), t)
        }

        // Open temp blob stream and commit by rename after write completed.
        // zh-CN: 打开临时 blob 流, 写入完成后通过 rename 提交.
        val (tempRel, rawOut) = blobs.openTrashBlobTempOutputStream(trashId)

        // Commit point marker for cross-resource operation (FS + DB).
        // zh-CN: 跨资源操作 (文件系统 + 数据库) 的提交点标记, 用于避免在源文件已删除后错误回滚导致数据丢失.
        var operationCommitted = false

        // Track whether temp blob has been committed.
        // zh-CN: 标记临时 blob 是否已提交.
        var blobCommitted = false

        // Final relPath after commit.
        // zh-CN: 提交后的最终 relPath.
        var finalRel: String? = null

        try {
            operationController.throwIfCancelled()

            rawOut.use { out ->
                val digestOut = DigestOutputStream(out, digest)
                val countingOut = CountingOutputStream(digestOut)

                if (isDir) {
                    // Zip directory to stream to avoid large memory usage.
                    // zh-CN: 将目录以 zip 流写入, 避免大内存占用.
                    zipDirectoryToStream(
                        dir = src,
                        out = countingOut,
                        operationController = operationController,
                        fileCallback = fileCallback,
                        onBytesProcessed = { n -> reportProcessed(n) },
                    )
                } else {
                    // Copy file to stream to avoid large memory usage.
                    // zh-CN: 将文件以流复制写入, 避免大内存占用.
                    fileCallback?.onFile(src)
                    copyFileToStream(
                        src = src,
                        out = countingOut,
                        operationController = operationController,
                        onBytesProcessed = { n -> reportProcessed(n) },
                    )
                }

                countingOut.flush()
                sizeBytes = countingOut.count
            }

            operationController.throwIfCancelled()

            // Commit temp blob into final blob name.
            // zh-CN: 将临时 blob 提交为最终 blob 文件名.
            finalRel = blobs.commitTrashBlobTemp(tempRel, trashId)
            blobCommitted = true

            val sha = sha256HexFromDigest(digest)

            val item = TrashEntities.TrashItem(
                trashId = trashId,
                originalPath = logicalPath,
                fileId = null,
                trashedAt = now,
                isDirectory = isDir,
                sizeBytes = sizeBytes.coerceAtLeast(0L),
                sha256 = sha,
                blobRelPath = finalRel,
            )

            // Persist DB record before deleting source to reduce risk of data loss.
            // zh-CN: 先写入数据库记录再删除源文件, 降低 "源已删但记录未写入" 的数据丢失风险.
            dao.upsertTrashItem(item)

            // Delete original after blob persisted and DB record persisted.
            // zh-CN: 在 blob 落盘且数据库记录写入后删除源文件/目录.
            val deleted = if (isDir) src.deleteRecursively() else src.delete()
            if (!deleted && src.exists()) {
                // Roll back if source deletion failed.
                // zh-CN: 若源文件删除失败则回滚, 避免出现 "回收站有记录但源文件仍存在" 的不一致状态.
                throw IllegalStateException("Failed to delete source after trash: $src")
            }

            // Mark committed after DB persisted and source deleted successfully.
            // zh-CN: 在数据库写入成功且源文件删除成功后标记为已提交, 避免后续异常触发破坏性回滚.
            operationCommitted = true

            /* Some code which may cause any exception in the future. */

            return item
        } catch (t: Throwable) {
            // Clean up partial data on failure or abort, but avoid rollback after commit.
            // zh-CN: 失败或中止时清理残留数据, 但在已提交后避免回滚以防数据丢失.
            @Suppress("KotlinConstantConditions")
            if (!operationCommitted) {
                runCatching { dao.deleteTrashItemsByIds(listOf(trashId)) }

                // Delete final blob if committed, otherwise delete temp blob.
                // zh-CN: 若已提交则删除最终 blob, 否则删除临时 blob.
                if (blobCommitted) {
                    finalRel?.let { runCatching { blobs.deleteBlobByRelPath(it) } }
                } else {
                    runCatching { blobs.deleteBlobByRelPath(tempRel) }
                }
            }
            throw t
        }
    }

    private fun copyFileToStream(
        src: File,
        out: OutputStream,
        operationController: DialogUtils.OperationController,
        onBytesProcessed: ((Int) -> Unit)?,
    ) {
        operationController.throwIfCancelled()
        BufferedInputStream(FileInputStream(src)).use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                operationController.throwIfCancelled()
                val n = input.read(buffer)
                if (n <= 0) break
                out.write(buffer, 0, n)
                onBytesProcessed?.invoke(n)
            }
        }
    }

    /**
     * Permanently delete a trash item (remove from trash only).
     * zh-CN: 彻底删除一个回收站条目 (仅从回收站移除).
     */
    fun deleteTrashItem(item: TrashEntities.TrashItem) {
        dao.deleteTrashItemsByIds(listOf(item.trashId))
        blobs.deleteBlobByRelPath(item.blobRelPath)
    }

    /**
     * Clear all trash items.
     * zh-CN: 清空回收站.
     */
    fun clearTrash() {
        val items = dao.listTrashItemsDesc()
        if (items.isEmpty()) {
            return
        }

        dao.deleteAllTrashItems()
        items.forEach { blobs.deleteBlobByRelPath(it.blobRelPath) }
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
    fun restoreTrashItemToPath(item: TrashEntities.TrashItem, dest: File, operationController: DialogUtils.OperationController) {
        operationController.throwIfCancelled()

        val blobFile = File(context.filesDir, item.blobRelPath)
        require(blobFile.exists()) { "Trash blob not exists: ${item.blobRelPath}" }

        // Copy stream with cancellation checkpoints.
        // zh-CN: 带取消检查点的流拷贝, 以便在大文件恢复过程中及时响应终止操作.
        fun copyToCancellable(input: InputStream, output: OutputStream) {
            val buffer = ByteArray(256 * 1024)
            while (true) {
                operationController.throwIfCancelled()
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
            }
            output.flush()
        }

        if (item.isDirectory) {
            dest.mkdirs()
            ZipInputStream(FileInputStream(blobFile)).use { zis ->
                while (true) {
                    operationController.throwIfCancelled()

                    val entry = zis.nextEntry ?: break
                    val outFile = File(dest, entry.name)

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                        zis.closeEntry()
                        continue
                    }

                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { os ->
                        copyToCancellable(zis, os)
                    }
                    zis.closeEntry()
                }
            }
        } else {
            dest.parentFile?.mkdirs()
            blobFile.inputStream().use { input ->
                dest.outputStream().use { output ->
                    copyToCancellable(input, output)
                }
            }
        }

        // Remove trash record and blob after successful restore.
        // zh-CN: 恢复成功后删除回收站记录与 blob.
        dao.deleteTrashItemsByIds(listOf(item.trashId))
        blobs.deleteBlobByRelPath(item.blobRelPath)
    }

    private fun zipDirectoryToStream(
        dir: File,
        out: OutputStream,
        operationController: DialogUtils.OperationController,
        fileCallback: ScriptOperations.FileProgressCallback?,
        onBytesProcessed: ((Int) -> Unit)?,
    ) {
        ZipOutputStream(out).use { zos ->
            val base = dir.absolutePath.trimEnd(File.separatorChar) + File.separator

            fun addFile(f: File) {
                operationController.throwIfCancelled()
                val abs = f.absolutePath
                val relPath = abs.removePrefix(base).replace(File.separatorChar, '/')

                if (f.isDirectory) {
                    val children = f.listFiles()
                    if (children.isNullOrEmpty()) {
                        // Explicit directory entry for empty folder.
                        // zh-CN: 对空目录写入显式条目.
                        val entryName = if (relPath.endsWith("/")) relPath else "$relPath/"
                        zos.putNextEntry(ZipEntry(entryName))
                        zos.closeEntry()
                    } else {
                        for (c in children) addFile(c)
                    }
                    return
                }

                fileCallback?.onFile(f)

                zos.putNextEntry(ZipEntry(relPath))
                BufferedInputStream(FileInputStream(f)).use { input ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        operationController.throwIfCancelled()
                        val n = input.read(buffer)
                        if (n <= 0) break
                        zos.write(buffer, 0, n)
                        onBytesProcessed?.invoke(n)
                    }
                }
                operationController.throwIfCancelled()
                zos.closeEntry()
            }

            addFile(dir)
            zos.finish()
        }
    }

    private fun sha256HexFromDigest(digest: MessageDigest): String {
        val bytes = digest.digest()
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format(Locale.US, "%02x", b))
        return sb.toString()
    }

    /**
     * Counting output stream wrapper.
     * zh-CN: 计数输出流包装器.
     */
    private class CountingOutputStream(private val delegate: OutputStream) : OutputStream() {
        var count: Long = 0L
            private set

        override fun write(b: Int) {
            delegate.write(b)
            count += 1L
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            count += len.toLong()
        }

        override fun flush() = delegate.flush()
        override fun close() = delegate.close()
    }
}
