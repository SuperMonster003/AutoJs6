package org.autojs.autojs.storage.history

import android.content.Context
import android.util.Log
import org.autojs.autojs.core.pref.keys.HistoryKeys
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
import java.util.zip.ZipOutputStream

class TrashMoveRecoveryRequiredException(
    val trashItem: TrashEntities.TrashItem,
    cause: Throwable,
) : IOException("Source removal requires recovery from Trash: ${trashItem.originalPath}", cause)

/**
 * The recovery blob of a trash item is gone from disk while its DB row still exists ("broken item").
 * zh-CN: 回收站条目的 blob 已不在磁盘上而 DB 记录仍在 ("失效条目").
 */
class TrashBlobMissingException(
    val trashItem: TrashEntities.TrashItem,
) : IOException("Trash blob not exists: ${trashItem.blobRelPath}")

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

            // Link the item to the version history entry that owns this path at move time (files only).
            // zh-CN: 把条目关联到移入时拥有该路径的版本历史条目 (仅文件).
            val historyFileId = if (isDir) null else dao.findFileByPath(HistoryPaths.normalizePath(logicalPath))?.fileId

            val item = TrashEntities.TrashItem(
                trashId = trashId,
                originalPath = logicalPath,
                fileId = historyFileId,
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

    companion object {
        private const val TAG = "TrashRepository"
    }

    fun findTrashItem(trashId: String): TrashEntities.TrashItem? = dao.getTrashItem(trashId)

    fun blobFileOf(item: TrashEntities.TrashItem): File = File(context.filesDir, item.blobRelPath)

    /**
     * True when the item's recovery blob is missing on disk (the item can neither be previewed nor restored).
     * zh-CN: 条目的 blob 在磁盘上缺失时为真 (该条目既不能预览也不能恢复).
     */
    fun isBlobMissing(item: TrashEntities.TrashItem): Boolean = !blobFileOf(item).isFile

    /**
     * Items whose blob is missing, newest first. Detection only: nothing is deleted here.
     * zh-CN: blob 缺失的条目 (新的在前). 仅检测, 不删除.
     */
    fun listTrashItemsWithMissingBlobs(): List<TrashEntities.TrashItem> = dao.listTrashItemsDesc().filter { isBlobMissing(it) }

    /**
     * Apply the trash retention policy: expire items by days, then enforce the total byte limit
     * while keeping the newest item (zero or negative limits mean unlimited).
     * zh-CN: 执行回收站保留策略: 先按天数过期, 再执行总容量上限并保留最新条目 (0 或负数表示不限制).
     */
    @JvmOverloads
    fun enforceRetentionPolicy(now: Long = System.currentTimeMillis()) {
        HistoryRetentionPolicy.expiredBeforeOrNull(now, HistoryKeys.trashMaxDays())?.let { expiredBefore ->
            val expired = dao.listExpiredTrashItems(expiredBefore)
            if (expired.isNotEmpty()) {
                dao.deleteTrashItemsByIds(expired.map { it.trashId })
                expired.forEach { blobs.deleteBlobByRelPath(it.blobRelPath) }
            }
        }
        enforceTotalBytesLimit()
    }

    /**
     * Remove the oldest items until the trash fits its total byte limit. The newest item and every
     * item in [protectedTrashIds] (for example the batch that was just moved in) are never removed,
     * so a single oversized entry stays until it expires by days or a newer item arrives.
     *
     * zh-CN: 从最旧开始删除, 直到回收站总量不超过容量上限. 最新条目与 [protectedTrashIds] 中的条目
     * (例如刚移入的一批) 永远不会被删除, 因此单个超大条目会保留到按天数过期或有更新的条目移入为止.
     *
     * @return the removed items, oldest first.
     */
    @JvmOverloads
    fun enforceTotalBytesLimit(protectedTrashIds: Set<String> = emptySet()): List<TrashEntities.TrashItem> {
        val limit = HistoryKeys.trashMaxTotalBytes()
        if (HistoryRetentionPolicy.isUnlimited(limit)) return emptyList()

        val newestFirst = dao.listTrashItemsDesc()
        if (newestFirst.isEmpty()) return emptyList()

        val newestId = newestFirst.first().trashId
        val toRemove = HistoryRetentionPolicy.selectOverBytesProtecting(
            itemsOldestFirst = newestFirst.asReversed(),
            maxBytes = limit,
            isProtected = { it.trashId == newestId || it.trashId in protectedTrashIds },
            sizeOf = { it.sizeBytes },
        )
        if (toRemove.isEmpty()) return emptyList()

        dao.deleteTrashItemsByIds(toRemove.map { it.trashId })
        toRemove.forEach { blobs.deleteBlobByRelPath(it.blobRelPath) }
        return toRemove
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

        val blobFile = blobFileOf(item)
        if (!blobFile.isFile) throw TrashBlobMissingException(item)

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
            val report = TrashArchiveExtractor.extract(blobFile, dest) { operationController.throwIfCancelled() }
            if (report.skippedUnsafe.isNotEmpty()) {
                Log.w(TAG, "Skipped ${report.skippedUnsafe.size} unsafe archive entries while restoring ${item.originalPath}: ${report.skippedUnsafe.take(5)}")
            }
            if (report.hasFailures) {
                // Keep the trash record and blob: the item is visibly still there and the caller reports
                // how much was salvaged, instead of silently dropping data that could not be read.
                // zh-CN: 保留回收站记录与 blob: 条目仍可见, 由调用方报告挽回了多少, 而不是悄悄丢弃读不出的数据.
                Log.w(TAG, "Archive of ${item.originalPath} restored partially: ${report.failed.size} entries failed")
                throw TrashArchiveExtractor.PartialFailure(report)
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

        // Version history follows the item when it is restored to a different path (files and directories).
        // zh-CN: 恢复到不同路径时, 版本历史跟随条目 (文件与目录均适用).
        val originalNormalized = HistoryPaths.normalizePath(item.originalPath)
        val destNormalized = HistoryPaths.normalizePath(dest.path)
        if (originalNormalized != destNormalized) {
            HistoryRepository(context.applicationContext).relocate(originalNormalized, destNormalized)
        }
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
