package org.autojs.autojs.storage.history

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.autojs.autojs.storage.history.TrashBlobStore.Companion.TRASH_BLOB_DIR_REL
import java.io.File

/**
 * Periodic cleanup worker for history/trash/drafts.
 * zh-CN: 用于 history/trash/drafts 的周期性清理 Worker.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 3, 2026.
 * Modified by SuperMonster003 as of Feb 8, 2026.
 */
class HistoryCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            cleanupExpiredRevisions()
            cleanupExpiredTrashItems()
            cleanupTrashTotalBytesLimit()
            cleanupOrphanHistoryBlobs()
            cleanupOrphanTrashBlobs()
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

        // Remove expired revisions by days.
        // zh-CN: 按天数删除过期 revision.
        val now = System.currentTimeMillis()
        val maxDaysMs = HistoryPrefs.historyMaxDays().coerceAtLeast(0).toLong() * 24L * 60L * 60L * 1000L
        val expiredBefore = now - maxDaysMs

        val expired = dao.listExpiredRevisions(expiredBefore)
        if (expired.isEmpty()) return

        dao.deleteRevisionsByIds(expired.map { it.revId })
        expired.forEach { blobs.deleteBlobByRelPath(it.blobRelPath) }
    }

    private fun cleanupExpiredTrashItems() {
        val db = HistoryDatabase.getInstance(applicationContext)
        val dao = db.historyDao()
        val trashBlobs = TrashBlobStore(applicationContext)

        // Remove expired trash items by days.
        // zh-CN: 按天数删除过期回收站条目.
        val now = System.currentTimeMillis()
        val maxDaysMs = HistoryPrefs.trashMaxDays().coerceAtLeast(0).toLong() * 24L * 60L * 60L * 1000L
        val expiredBefore = now - maxDaysMs

        val expired = dao.listExpiredTrashItems(expiredBefore)
        if (expired.isEmpty()) return

        dao.deleteTrashItemsByIds(expired.map { it.trashId })
        expired.forEach { trashBlobs.deleteBlobByRelPath(it.blobRelPath) }
    }

    /**
     * Enforce trash total bytes limit by deleting oldest items first.
     * zh-CN: 通过优先删除最旧条目来约束回收站总容量上限.
     */
    private fun cleanupTrashTotalBytesLimit() {
        val limit = HistoryPrefs.trashMaxTotalBytes().coerceAtLeast(0L)
        if (limit <= 0L) return

        val db = HistoryDatabase.getInstance(applicationContext)
        val dao = db.historyDao()
        val trashBlobs = TrashBlobStore(applicationContext)

        val items = dao.listTrashItemsDesc()
        if (items.isEmpty()) return

        var total = items.sumOf { it.sizeBytes.coerceAtLeast(0L) }
        if (total <= limit) return

        // Delete from oldest to newest until within limit.
        // zh-CN: 从最旧到最新删除, 直到满足上限.
        for (item in items.asReversed()) {
            if (total <= limit) break
            dao.deleteTrashItemsByIds(listOf(item.trashId))
            trashBlobs.deleteBlobByRelPath(item.blobRelPath)
            total -= item.sizeBytes.coerceAtLeast(0L)
        }
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

    private fun cleanupOrphanTrashBlobs() {
        val db = HistoryDatabase.getInstance(applicationContext)
        val dao = db.historyDao()

        // Build the referenced trash blob set from DB.
        // zh-CN: 从 DB 构建被引用的回收站 blob 集合.
        val referenced = dao.listAllTrashBlobRelPaths().toHashSet()

        val root = File(applicationContext.filesDir, TRASH_BLOB_DIR_REL)
        if (!root.exists() || !root.isDirectory) return

        val now = System.currentTimeMillis()

        // Delete trash blobs that are not referenced by DB.
        // zh-CN: 删除 DB 未引用的回收站 blob.
        root.walkTopDown()
            .filter { it.isFile }
            .forEach { f ->
                // Protect very new files to avoid deleting blobs created while DB write is pending.
                // zh-CN: 保护非常新的文件, 避免在 DB 写入尚未完成时误删刚创建的 blob.
                val ageMs = now - f.lastModified()
                if (ageMs >= 0L && ageMs < ORPHAN_TRASH_BLOB_MIN_AGE_MS) {
                    return@forEach
                }

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
        val draftsDir = File(applicationContext.filesDir, "drafts")
        if (!draftsDir.exists() || !draftsDir.isDirectory) return

        val now = System.currentTimeMillis()

        // 1) Remove expired by days.
        // zh-CN: 1) 按天数删除过期草稿.
        val maxDaysMs = HistoryPrefs.draftsMaxDays().coerceAtLeast(0).toLong() * 24L * 60L * 60L * 1000L
        val expiredBefore = now - maxDaysMs
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

        val limit = HistoryPrefs.draftsMaxTotalBytes().coerceAtLeast(0L)
        var total = remained.sumOf { it.length().coerceAtLeast(0L) }
        if (total <= limit) return

        for (f in remained.asReversed()) {
            if (total <= limit) break
            val len = f.length().coerceAtLeast(0L)
            if (f.delete()) {
                total -= len
            }
        }
    }

    companion object {
        // Minimum age for orphan final trash blobs before deletion to avoid races.
        // zh-CN: orphan 正式回收站 blob 的最小删除年龄, 用于避免并发时序竞争.
        private const val ORPHAN_TRASH_BLOB_MIN_AGE_MS: Long = 30L * 60L * 1000L
    }
}
