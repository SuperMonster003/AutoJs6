package org.autojs.autojs.storage.history

/**
 * Path mapping for history entries when a tracked file or directory is renamed or moved (pure, unit tested).
 * zh-CN: 已纳管的文件或目录被重命名 / 移动时, 历史条目的路径映射 (纯函数, 有单测).
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object HistoryRelocation {

    /**
     * Whether [trackedPath] is [oldPath] itself or lies beneath it (never matches a sibling that
     * merely shares a prefix, e.g. "/a/bc" for "/a/b").
     * zh-CN: [trackedPath] 是否为 [oldPath] 本身或位于其下 (不会误匹配仅共享前缀的同级, 如 "/a/b" 对 "/a/bc").
     */
    fun isAffected(trackedPath: String, oldPath: String): Boolean {
        val old = HistoryPaths.normalizePath(oldPath)
        val tracked = HistoryPaths.normalizePath(trackedPath)
        return tracked == old || tracked.startsWith("$old/")
    }

    /**
     * The logical path [trackedPath] should carry after [oldPath] was moved to [newPath],
     * or null when the entry is unaffected.
     * zh-CN: [oldPath] 移动到 [newPath] 后 [trackedPath] 应变为的 logical path; 不受影响时返回 null.
     */
    fun mapPath(trackedPath: String, oldPath: String, newPath: String): String? {
        val old = HistoryPaths.normalizePath(oldPath)
        val new = HistoryPaths.normalizePath(newPath)
        val tracked = HistoryPaths.normalizePath(trackedPath)
        return when {
            tracked == old -> new
            tracked.startsWith("$old/") -> new + tracked.substring(old.length)
            else -> null
        }
    }
}
