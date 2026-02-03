package org.autojs.autojs.storage.history

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File

/**
 * Periodic cleanup worker for history/trash/drafts.
 * zh-CN: 用于 history/trash/drafts 的周期性清理 Worker.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 3, 2026.
 */
class HistoryCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            cleanupExpiredRevisions()
            cleanupOrphanHistoryBlobs()
            cleanupEmergencyDrafts()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    private fun cleanupExpiredRevisions() {
        val db = HistoryDatabase.getInstance(applicationContext)
        val dao = db.historyDao()
        val blobs = HistoryBlobStore(applicationContext)

        // Remove expired revisions by days (30 days).
        // zh-CN: 按天数删除过期 revision (30 天).
        val now = System.currentTimeMillis()
        val expiredBefore = now - MAX_DAYS_MS
        val expired = dao.listExpiredRevisions(expiredBefore)
        if (expired.isEmpty()) return

        dao.deleteRevisionsByIds(expired.map { it.revId })
        expired.forEach { blobs.deleteBlobByRelPath(it.blobRelPath) }
    }

    private fun cleanupOrphanHistoryBlobs() {
        val db = HistoryDatabase.getInstance(applicationContext)
        val dao = db.historyDao()

        // Build the referenced blob set from DB.
        // zh-CN: 从 DB 构建被引用的 blob 集合.
        val referenced = dao.listAllBlobRelPaths().toHashSet()

        val root = File(applicationContext.filesDir, "history/blob")
        if (!root.exists() || !root.isDirectory) return

        // Delete blobs that are not referenced by DB.
        // zh-CN: 删除 DB 未引用的 blob.
        root.walkTopDown()
            .filter { it.isFile }
            .forEach { f ->
                val rel = f.relativeTo(applicationContext.filesDir).invariantSeparatorsPath
                if (!referenced.contains(rel)) {
                    // noinspection ResultOfMethodCallIgnored
                    f.delete()
                }
            }

        // Best-effort: remove empty directories.
        // zh-CN: 尽力删除空目录.
        root.walkBottomUp()
            .filter { it.isDirectory }
            .forEach { dir ->
                val children = dir.listFiles()
                if (children == null || children.isEmpty()) {
                    // noinspection ResultOfMethodCallIgnored
                    dir.delete()
                }
            }
    }

    private fun cleanupEmergencyDrafts() {
        // Keep consistent with EditorView's local cleanup policy.
        // zh-CN: 与 EditorView 的本地清理策略保持一致.
        val draftsDir = File(applicationContext.filesDir, "drafts")
        if (!draftsDir.exists() || !draftsDir.isDirectory) return

        val now = System.currentTimeMillis()

        // 1) Remove expired (older than 7 days).
        // zh-CN: 1) 删除过期草稿 (超过 7 天).
        val expiredBefore = now - DRAFT_MAX_DAYS_MS
        draftsDir.listFiles()?.forEach { f ->
            if (f.isFile && f.lastModified() < expiredBefore) {
                // noinspection ResultOfMethodCallIgnored
                f.delete()
            }
        }

        // 2) Enforce total bytes limit (keep newest first).
        // zh-CN: 2) 约束总容量上限 (优先保留最新).
        val remained = draftsDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: return

        var total = remained.sumOf { it.length().coerceAtLeast(0L) }
        if (total <= DRAFT_MAX_TOTAL_BYTES) return

        for (f in remained.asReversed()) {
            if (total <= DRAFT_MAX_TOTAL_BYTES) break
            val len = f.length().coerceAtLeast(0L)
            if (f.delete()) {
                total -= len
            }
        }
    }

    companion object {
        private const val MAX_DAYS_MS: Long = 30L * 24L * 60L * 60L * 1000L
        private const val DRAFT_MAX_DAYS_MS: Long = 7L * 24L * 60L * 60L * 1000L
        private const val DRAFT_MAX_TOTAL_BYTES: Long = 200L * 1024L * 1024L
    }
}