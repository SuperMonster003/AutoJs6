package org.autojs.autojs.storage.history

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 3, 2026.
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
        uri: Uri,
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

        // 1) Remove expired by days.
        // zh-CN: 1) 按天数删除过期版本.
        val expiredBefore = now - MAX_DAYS_MS
        val expired = revisions.filter { it.createdAt < expiredBefore }
        if (expired.isNotEmpty()) {
            deleteRevisionsAndBlobs(expired)
            revisions.removeAll(expired.toSet())
        }

        // 2) Enforce max count (remove oldest).
        // zh-CN: 2) 约束最大版本数 (删除最旧).
        if (revisions.size > MAX_VERSIONS) {
            val toRemove = revisions.take(revisions.size - MAX_VERSIONS)
            deleteRevisionsAndBlobs(toRemove)
            revisions.removeAll(toRemove.toSet())
        }

        // 3) Enforce max total bytes per file (remove oldest).
        // zh-CN: 3) 约束单文件历史总容量 (删除最旧).
        var totalBytes = (dao.sumBytesByFileId(fileId) ?: 0L).coerceAtLeast(0L)
        if (totalBytes > MAX_TOTAL_BYTES_PER_FILE) {
            for (rev in revisions.toList()) {
                if (totalBytes <= MAX_TOTAL_BYTES_PER_FILE) break
                deleteRevisionsAndBlobs(listOf(rev))
                totalBytes -= rev.sizeBytes.coerceAtLeast(0L)
                revisions.remove(rev)
            }
        }
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

    companion object {
        private const val MAX_DAYS_MS: Long = 30L * 24L * 60L * 60L * 1000L
        private const val MAX_VERSIONS: Int = 50
        private const val MAX_TOTAL_BYTES_PER_FILE: Long = 200L * 1024L * 1024L
    }
}
