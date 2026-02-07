package org.autojs.autojs.storage.history

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 3, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 6, 2026.
 */
class HistoryRepository(private val context: Context) {

    private val db by lazy { HistoryDatabase.getInstance(context.applicationContext) }
    private val dao by lazy { db.historyDao() }
    private val blobs by lazy { HistoryBlobStore(context.applicationContext) }

    /**
     * Read revision blob bytes.
     * zh-CN: 读取 revision 对应的 blob bytes.
     */
    fun readRevisionBytes(rev: HistoryEntities.Revision): ByteArray {
        val f = File(context.filesDir, rev.blobRelPath)
        return f.inputStream().use { it.readBytes() }
    }

    /**
     * Record a SAVE_PRE snapshot (old content bytes).
     *
     * Rules:
     * - Only tracks files under internal storage root.
     * - Uses logicalPath first; fallback by latestFingerprint matching.
     * - Applies per-file retention after inserting.
     *
     * zh-CN:
     *
     * 记录一次 SAVE_PRE 快照 (旧内容 bytes).
     *
     * 规则:
     * - 仅纳管内部存储根目录下的文件.
     * - 优先按 logicalPath 匹配; 找不到时用 latestFingerprint 回退匹配.
     * - 插入后执行单文件保留策略清理.
     */
    fun recordSavePre(
        logicalPath: String,
        oldBytes: ByteArray,
        encodingName: String,
        hadBom: Boolean,
    ) {
        val now = System.currentTimeMillis()

        val oldSha = sha256Hex(oldBytes)
        val fileEntry = getOrCreateFileEntry(
            logicalPath = logicalPath,
            latestFingerprint = oldSha,
            now = now,
        )

        val revId = blobs.newRevId()
        val blobRel = blobs.writeRevisionBlob(fileEntry.fileId, revId, oldBytes)

        dao.insertRevision(
            HistoryEntities.Revision(
                revId = revId,
                fileId = fileEntry.fileId,
                op = "SAVE_PRE",
                createdAt = now,
                logicalPathAtThatTime = logicalPath,
                encoding = encodingName,
                hadBom = hadBom,
                sizeBytes = oldBytes.size.toLong(),
                sha256 = oldSha,
                blobRelPath = blobRel,
            )
        )

        cleanupPerFile(fileEntry.fileId, now)
        cleanupGlobal()
    }

    /**
     * Clear all history revisions/blobs for a given path.
     *
     * Rules:
     * - If target is a file: clear exact match.
     * - If target is a directory: clear all entries under this directory by path prefix.
     *
     * zh-CN:
     *
     * 清除指定路径对应的全部历史版本与 blob.
     *
     * 规则:
     * - 若目标是文件: 精确匹配清理.
     * - 若目标是目录: 按路径前缀清理该目录下所有已纳管条目.
     */
    fun clearHistoryForPath(path: String) {
        val normalized = path.trimEnd('/')

        val target = File(normalized)
        val fileIds = if (target.isDirectory) {
            val prefix = "$normalized/"
            dao.listFilesByPathPrefix(prefix).map { it.fileId }
        } else {
            dao.findFileByPath(normalized)?.let { listOf(it.fileId) } ?: emptyList()
        }

        if (fileIds.isEmpty()) return

        val revisions = dao.listRevisionsByFileIds(fileIds)
        dao.deleteRevisionsByFileIds(fileIds)
        revisions.forEach { blobs.deleteBlobByRelPath(it.blobRelPath) }
        dao.deleteFileEntriesByIds(fileIds)
    }

    private fun getOrCreateFileEntry(logicalPath: String, latestFingerprint: String, now: Long): HistoryEntities.FileEntry {
        val byPath = dao.findFileByPath(logicalPath)
        if (byPath != null) {
            val updated = byPath.copy(
                lastSeenAt = now,
                latestFingerprint = latestFingerprint,
            )
            dao.upsertFile(updated)
            return updated
        }

        val byFingerprint = dao.findFileByLatestFingerprint(latestFingerprint)
        if (byFingerprint != null) {
            // Re-attach to new path.
            // zh-CN: 重新绑定到新路径.
            val updated = byFingerprint.copy(
                logicalPath = logicalPath,
                lastSeenAt = now,
                latestFingerprint = latestFingerprint,
            )
            dao.upsertFile(updated)
            return updated
        }

        val created = HistoryEntities.FileEntry(
            fileId = blobs.newFileId(),
            logicalPath = logicalPath,
            createdAt = now,
            lastSeenAt = now,
            latestFingerprint = latestFingerprint,
        )
        dao.upsertFile(created)
        return created
    }

    private fun cleanupPerFile(fileId: String, now: Long) {
        val revisions = dao.listRevisionsAsc(fileId).toMutableList()
        if (revisions.isEmpty()) return

        // Read policy from preferences.
        // zh-CN: 从偏好设置读取策略参数.
        val maxDaysMs = HistoryPrefs.historyMaxDays().coerceAtLeast(0).toLong() * 24L * 60L * 60L * 1000L
        val maxVersions = HistoryPrefs.historyMaxVersions().coerceAtLeast(0)
        val maxTotalBytesPerFile = HistoryPrefs.historyMaxTotalBytesPerFile().coerceAtLeast(0L)

        // 1) Remove expired by days.
        // zh-CN: 1) 按天数删除过期版本.
        val expiredBefore = now - maxDaysMs
        val expired = revisions.filter { it.createdAt < expiredBefore }
        if (expired.isNotEmpty()) {
            deleteRevisionsAndBlobs(expired)
            revisions.removeAll(expired.toSet())
        }

        // 2) Enforce max count (remove oldest).
        // zh-CN: 2) 约束最大版本数 (删除最旧).
        if (revisions.size > maxVersions) {
            val toRemove = revisions.take(revisions.size - maxVersions)
            deleteRevisionsAndBlobs(toRemove)
            revisions.removeAll(toRemove.toSet())
        }

        // 3) Enforce max total bytes per file (remove oldest).
        // zh-CN: 3) 约束单文件历史总容量 (删除最旧).
        var totalBytes = (dao.sumBytesByFileId(fileId) ?: 0L).coerceAtLeast(0L)
        if (totalBytes > maxTotalBytesPerFile) {
            for (rev in revisions.toList()) {
                if (totalBytes <= maxTotalBytesPerFile) break
                deleteRevisionsAndBlobs(listOf(rev))
                totalBytes -= rev.sizeBytes.coerceAtLeast(0L)
                revisions.remove(rev)
            }
        }
    }

    /**
     * Delete a single revision and its blob.
     * zh-CN: 删除单个 revision 及其 blob.
     */
    fun deleteRevision(rev: HistoryEntities.Revision) {
        deleteRevisionsAndBlobs(listOf(rev))
    }

    /**
     * Clear all version history (all file entries, all revisions, all blobs).
     * zh-CN: 清空全部版本历史 (所有 file_entry, 所有 revision, 所有 blobs).
     */
    fun clearAllHistory() {
        val revs = dao.listAllRevisionsAsc()
        if (revs.isNotEmpty()) {
            revs.forEach { blobs.deleteBlobByRelPath(it.blobRelPath) }
        }
        dao.deleteAllRevisions()
        dao.deleteAllFileEntries()
    }

    fun getRevisionsCount(fileId: String): Int {
        return dao.listRevisionsAsc(fileId).size
    }

    private fun deleteRevisionsAndBlobs(revs: List<HistoryEntities.Revision>) {
        if (revs.isEmpty()) return
        dao.deleteRevisionsByIds(revs.map { it.revId })
        revs.forEach { blobs.deleteBlobByRelPath(it.blobRelPath) }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format(Locale.US, "%02x", b))
        }
        return sb.toString()
    }

    /**
     * Enforce global history total bytes limit (remove oldest revisions first).
     * zh-CN: 约束全局历史总容量上限 (优先删除最旧 revision).
     */
    private fun cleanupGlobal() {
        val maxTotalBytes = HistoryPrefs.historyMaxTotalBytes().coerceAtLeast(0L)
        if (maxTotalBytes <= 0L) return

        var totalBytes = (dao.sumAllRevisionBytes() ?: 0L).coerceAtLeast(0L)
        if (totalBytes <= maxTotalBytes) return

        // Delete in batches to reduce DB roundtrips.
        // zh-CN: 批量删除以减少 DB 往返次数.
        val batchSize = 50

        while (totalBytes > maxTotalBytes) {
            val oldest = dao.listOldestRevisions(batchSize)
            if (oldest.isEmpty()) break

            // Remove until under limit or batch exhausted.
            // zh-CN: 删除直到满足上限或本批用尽.
            val toRemove = ArrayList<HistoryEntities.Revision>(oldest.size)
            var bytesToFree = 0L

            for (rev in oldest) {
                if (totalBytes - bytesToFree <= maxTotalBytes) break
                toRemove.add(rev)
                bytesToFree += rev.sizeBytes.coerceAtLeast(0L)
            }

            if (toRemove.isEmpty()) break

            deleteRevisionsAndBlobs(toRemove)

            totalBytes = (totalBytes - bytesToFree).coerceAtLeast(0L)
        }
    }
}
