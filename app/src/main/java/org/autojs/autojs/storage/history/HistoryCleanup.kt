package org.autojs.autojs.storage.history

import android.content.Context
import android.util.Log
import org.autojs.autojs.core.pref.keys.HistoryKeys
import org.autojs.autojs.storage.file.EmergencyDraftDirectoryGuard
import org.autojs.autojs.storage.history.TrashBlobStore.Companion.TRASH_BLOB_DIR_REL
import java.io.File

/**
 * Cleanup passes for version history, trash and emergency drafts.
 *
 * Shared by the periodic [HistoryCleanupWorker], the on-demand cleanup enqueued after a retention
 * setting changes, and the "Clean up now" menu entries, which run it directly on an I/O thread.
 * Every pass is independent and swallows nothing: callers decide how to treat failures.
 *
 * zh-CN: 版本历史, 回收站与紧急草稿的清理过程. 由周期性 [HistoryCleanupWorker], 保留策略变更后的
 * 即时清理, 以及 "立即清理" 菜单 (直接在 I/O 线程执行) 共用. 各过程相互独立且不吞异常, 由调用方决定如何处理失败.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object HistoryCleanup {

    private const val TAG = "HistoryCleanup"

    // Minimum age for orphan history / trash blobs before deletion to avoid races with pending DB writes.
    // zh-CN: orphan 历史 / 回收站 blob 的最小删除年龄, 用于避免与尚未提交的 DB 写入发生时序竞争.
    private const val ORPHAN_BLOB_MIN_AGE_MS: Long = 30L * 60L * 1000L

    /**
     * Run every pass in order. Retention comes first so orphan detection sees the final DB state.
     * zh-CN: 依次执行全部清理过程. 保留策略在前, 使 orphan 检测看到最终的 DB 状态.
     */
    fun runAll(context: Context) {
        val appContext = context.applicationContext
        runRetention(appContext)
        cleanupOrphanHistoryBlobs(appContext)
        cleanupOrphanTrashBlobs(appContext)
        cleanupEmergencyDrafts(appContext)
        reportTrashItemsWithMissingBlobs(appContext)
    }

    /**
     * Report trash items whose blob is gone (DB row kept). They are never removed automatically:
     * the trash page marks them and offers "Remove broken items".
     * zh-CN: 报告 blob 已丢失的回收站条目 (DB 记录保留). 不会自动删除: 回收站页面会标记它们并提供 "移除失效条目".
     */
    private fun reportTrashItemsWithMissingBlobs(appContext: Context) {
        val missing = TrashRepository(appContext).listTrashItemsWithMissingBlobs()
        if (missing.isEmpty()) return
        val sample = missing.take(5).joinToString { it.originalPath }
        Log.w(TAG, "Trash has ${missing.size} item(s) with a missing blob (kept for manual removal): $sample")
    }

    /**
     * Apply the retention policies only (history days / count / bytes, trash days / bytes, drafts).
     * zh-CN: 仅执行保留策略 (历史的天数 / 条数 / 容量, 回收站的天数 / 容量, 草稿).
     */
    fun runRetention(context: Context) {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        HistoryRepository(appContext).enforceRetentionPolicy(now)
        TrashRepository(appContext).enforceRetentionPolicy(now)
        cleanupEmergencyDrafts(appContext)
    }

    private fun cleanupOrphanHistoryBlobs(appContext: Context) {
        val dao = HistoryDatabase.getInstance(appContext).historyDao()

        // Build the referenced blob set from DB.
        // zh-CN: 从 DB 构建被引用的 blob 集合.
        val referenced = dao.listAllBlobRelPaths().toHashSet()

        val root = File(appContext.filesDir, "history/blob")
        deleteUnreferencedBlobs(appContext, root, referenced)
    }

    private fun cleanupOrphanTrashBlobs(appContext: Context) {
        val dao = HistoryDatabase.getInstance(appContext).historyDao()

        // Build the referenced trash blob set from DB.
        // zh-CN: 从 DB 构建被引用的回收站 blob 集合.
        val referenced = dao.listAllTrashBlobRelPaths().toHashSet()

        val root = File(appContext.filesDir, TRASH_BLOB_DIR_REL)
        deleteUnreferencedBlobs(appContext, root, referenced)
    }

    /**
     * Delete files under [root] whose relative path is not in [referenced], then prune empty directories.
     * Blobs younger than [ORPHAN_BLOB_MIN_AGE_MS] are kept because a blob is written before its DB row.
     *
     * zh-CN: 删除 [root] 下相对路径不在 [referenced] 中的文件, 然后清理空目录.
     * 年龄小于 [ORPHAN_BLOB_MIN_AGE_MS] 的 blob 保留, 因为 blob 先于其 DB 记录写入.
     */
    private fun deleteUnreferencedBlobs(appContext: Context, root: File, referenced: Set<String>) {
        if (!root.exists() || !root.isDirectory) return

        val now = System.currentTimeMillis()

        root.walkTopDown()
            .filter { it.isFile }
            .forEach { f ->
                val ageMs = now - f.lastModified()
                if (ageMs >= 0L && ageMs < ORPHAN_BLOB_MIN_AGE_MS) {
                    return@forEach
                }

                val rel = f.relativeTo(appContext.filesDir).invariantSeparatorsPath
                if (!referenced.contains(rel)) {
                    // noinspection ResultOfMethodCallIgnored
                    f.delete()
                }
            }

        // Best-effort: remove empty directories.
        // zh-CN: 尽力删除空目录.
        root.walkBottomUp()
            .filter { it.isDirectory && it != root }
            .forEach { dir ->
                val children = dir.listFiles()
                if (children == null || children.isEmpty()) {
                    // noinspection ResultOfMethodCallIgnored
                    dir.delete()
                }
            }
    }

    private fun cleanupEmergencyDrafts(appContext: Context) {
        val draftsDir = File(appContext.filesDir, "drafts")
        if (!draftsDir.exists() || !draftsDir.isDirectory) return
        EmergencyDraftDirectoryGuard.withLock(draftsDir) {
            cleanupEmergencyDraftsLocked(draftsDir)
        }
    }

    private fun cleanupEmergencyDraftsLocked(draftsDir: File) {
        if (!draftsDir.exists() || !draftsDir.isDirectory) return

        val now = System.currentTimeMillis()

        // 1) Remove expired by days (zero or negative days means unlimited).
        // zh-CN: 1) 按天数删除过期草稿 (天数为 0 或负数表示不限).
        HistoryRetentionPolicy.expiredBeforeOrNull(now, HistoryKeys.draftsMaxDays())?.let { expiredBefore ->
            draftsDir.listFiles()?.forEach { f ->
                if (f.isFile && !EmergencyDraftDirectoryGuard.isProtectedFromDeletion(f, now) &&
                    f.lastModified() < expiredBefore
                ) {
                    // noinspection ResultOfMethodCallIgnored
                    f.delete()
                }
            }
        }

        // 2) Enforce total bytes limit, oldest first (zero or negative bytes means unlimited).
        // zh-CN: 2) 约束总容量上限, 优先删除最旧 (容量为 0 或负数表示不限).
        val limit = HistoryKeys.draftsMaxTotalBytes()
        if (HistoryRetentionPolicy.isUnlimited(limit)) return

        val oldestFirst = draftsDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.lastModified() }
            ?: return

        val toDelete = HistoryRetentionPolicy.selectOverBytesProtecting(
            itemsOldestFirst = oldestFirst,
            maxBytes = limit,
            isProtected = { EmergencyDraftDirectoryGuard.isProtectedFromDeletion(it, now) },
            sizeOf = { it.length() },
        )
        toDelete.forEach { f ->
            // noinspection ResultOfMethodCallIgnored
            f.delete()
        }
    }
}
