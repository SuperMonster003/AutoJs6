package org.autojs.autojs.storage.history

import android.content.Context
import org.autojs.autojs.ui.common.ScriptOperations
import org.autojs.autojs.util.DialogUtils
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class TrashMoveRecoveryRequiredException(
    val trashItem: TrashEntities.TrashItem,
    cause: Throwable,
) : IOException("Source removal requires recovery from Trash: ${trashItem.originalPath}", cause)

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
    fun moveToTrashWithProgress(
        path: String,
        progressCallback: ScriptOperations.ProgressCallback?,
        fileCallback: ScriptOperations.FileProgressCallback?,
        operationController: DialogUtils.OperationController,
    ): TrashEntities.TrashItem = moveToTrashWithProgress(
        path = path,
        progressCallback = progressCallback,
        fileCallback = fileCallback,
        operationController = operationController,
        beforeSourceDelete = null,
        trashId = newTrashId(),
    )

    /**
     * Variant used by capability-scoped callers that must revalidate a source after the recovery
     * blob is complete but before the durable Trash record authorizes source removal.
     */
    fun moveToTrashWithProgress(
        path: String,
        progressCallback: ScriptOperations.ProgressCallback?,
        fileCallback: ScriptOperations.FileProgressCallback?,
        operationController: DialogUtils.OperationController,
        beforeSourceDelete: (() -> Unit)?,
    ): TrashEntities.TrashItem = moveToTrashWithProgress(
        path = path,
        progressCallback = progressCallback,
        fileCallback = fileCallback,
        operationController = operationController,
        beforeSourceDelete = beforeSourceDelete,
        trashId = newTrashId(),
    )

    /** Uses a host-reserved ID so a durable caller can recover an interrupted move. */
    fun moveToTrashWithProgress(
        path: String,
        progressCallback: ScriptOperations.ProgressCallback?,
        fileCallback: ScriptOperations.FileProgressCallback?,
        operationController: DialogUtils.OperationController,
        beforeSourceDelete: (() -> Unit)?,
        trashId: String,
    ): TrashEntities.TrashItem {
        operationController.throwIfCancelled()

        require(UUID.fromString(trashId).toString().equals(trashId, ignoreCase = true)) {
            "Trash item ID is invalid"
        }

        val src = File(path)
        require(src.exists()) { "Source not exists: $path" }

        val logicalPath = path.trimEnd('/')

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

        // Track whether temp blob has been committed.
        // zh-CN: 标记临时 blob 是否已提交.
        var blobCommitted = false

        // Final relPath after commit.
        // zh-CN: 提交后的最终 relPath.
        var finalRel: String? = null
        var durableItem: TrashEntities.TrashItem? = null
        var recoveryPersisted = false

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
            durableItem = item

            // A capability-scoped caller can reject a source that changed while the recovery blob
            // was being produced. This happens before the DB commit, so the intact source remains
            // authoritative and the unused blob can still be removed safely.
            beforeSourceDelete?.invoke()

            // Persist DB record before deleting source to reduce risk of data loss.
            // zh-CN: 先写入数据库记录再删除源文件, 降低 "源已删但记录未写入" 的数据丢失风险.
            dao.upsertTrashItem(item)
            recoveryPersisted = true

            // Delete original after blob persisted and DB record persisted.
            // zh-CN: 在 blob 落盘且数据库记录写入后删除源文件/目录.
            val deleted = if (isDir) src.deleteRecursively() else src.delete()
            if (!deleted && src.exists()) {
                // The durable recovery item must survive a partial directory deletion. Removing it
                // here could destroy the only remaining copy of children already deleted.
                throw IllegalStateException("Failed to delete source after trash: $src")
            }

            return item
        } catch (t: Throwable) {
            if (!recoveryPersisted) {
                runCatching { dao.deleteTrashItemsByIds(listOf(trashId)) }

                // Delete final blob if committed, otherwise delete temp blob.
                // zh-CN: 若已提交则删除最终 blob, 否则删除临时 blob.
                if (blobCommitted) {
                    finalRel?.let { runCatching { blobs.deleteBlobByRelPath(it) } }
                } else {
                    runCatching { blobs.deleteBlobByRelPath(tempRel) }
                }
                throw t
            }
            throw TrashMoveRecoveryRequiredException(requireNotNull(durableItem), t)
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

    fun newTrashId(): String = blobs.newTrashId()

    fun findTrashItem(trashId: String): TrashEntities.TrashItem? = dao.getTrashItem(trashId)

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
            // Write into a hidden sibling first and rename over the destination, so an abort or a
            // crash in the middle never leaves a half-written file where the original used to be.
            // zh-CN: 先写入同目录的隐藏临时文件再 rename 覆盖目标, 中止或崩溃时不会在原文件位置留下半成品.
            val parent = dest.absoluteFile.parentFile
            parent?.mkdirs()
            val tmp = File(parent, ".${dest.name}.restore.${UUID.randomUUID().toString().take(8)}.tmp")
            var committed = false
            try {
                blobFile.inputStream().use { input ->
                    FileOutputStream(tmp).use { output ->
                        copyToCancellable(input, output)
                        output.fd.sync()
                    }
                }
                operationController.throwIfCancelled()

                // rename(2) replaces an existing regular file atomically; fall back to delete + rename.
                // zh-CN: rename(2) 会原子替换已有的普通文件; 失败时回退为先删除再 rename.
                if (!tmp.renameTo(dest)) {
                    if (dest.exists() && !dest.delete()) {
                        throw IOException("Failed to replace restore destination: $dest")
                    }
                    if (!tmp.renameTo(dest)) {
                        throw IOException("Failed to commit restored file: $dest")
                    }
                }
                committed = true
            } finally {
                if (!committed) {
                    runCatching {
                        // noinspection ResultOfMethodCallIgnored
                        tmp.delete()
                    }
                }
            }
        }

        // Remove trash record and blob after successful restore.
        // zh-CN: 恢复成功后删除回收站记录与 blob.
        dao.deleteTrashItemsByIds(listOf(item.trashId))
        blobs.deleteBlobByRelPath(item.blobRelPath)
    }

    /** Restores only when the destination name is still unused; existing data is never replaced. */
    fun restoreTrashItemToPathWithoutOverwrite(
        item: TrashEntities.TrashItem,
        dest: File,
        operationController: DialogUtils.OperationController,
    ) {
        require(!dest.exists()) { "Trash restore destination already exists: $dest" }
        if (item.isDirectory) {
            check(dest.mkdir()) { "Trash restore directory could not be reserved: $dest" }
        } else {
            val parent = requireNotNull(dest.parentFile)
            require(parent.isDirectory) { "Trash restore parent is unavailable: $parent" }
            check(dest.createNewFile()) { "Trash restore file could not be reserved: $dest" }
        }
        restoreTrashItemToPath(item, dest, operationController)
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
