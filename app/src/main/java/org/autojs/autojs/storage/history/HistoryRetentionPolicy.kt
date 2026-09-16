package org.autojs.autojs.storage.history

/**
 * Retention policy math shared by the history repository and the cleanup worker.
 * zh-CN: 历史仓库与清理 Worker 共用的保留策略计算.
 *
 * A limit of zero or a negative number means "unlimited" for every dimension
 * (days, count, bytes). All selectors operate on oldest-first lists and return
 * the items to delete, oldest first.
 * zh-CN: 任一维度 (天数 / 条数 / 字节) 的上限为 0 或负数时表示 "不限制".
 * 所有选择器都接收 "最旧优先" 的列表, 并按最旧优先返回应删除的条目.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object HistoryRetentionPolicy {

    const val DAY_MS: Long = 24L * 60L * 60L * 1000L

    fun isUnlimited(limit: Int): Boolean = limit <= 0

    fun isUnlimited(limit: Long): Boolean = limit <= 0L

    /**
     * Timestamp before which items are expired, or null when days are unlimited.
     * zh-CN: 早于该时间戳的条目视为过期; 天数不限时返回 null.
     */
    fun expiredBeforeOrNull(now: Long, maxDays: Int): Long? =
        if (isUnlimited(maxDays)) null else now - maxDays.toLong() * DAY_MS

    fun <T> selectExpired(items: List<T>, now: Long, maxDays: Int, createdAt: (T) -> Long): List<T> {
        val before = expiredBeforeOrNull(now, maxDays) ?: return emptyList()
        return items.filter { createdAt(it) < before }
    }

    fun <T> selectOverCount(itemsOldestFirst: List<T>, maxCount: Int): List<T> {
        if (isUnlimited(maxCount)) return emptyList()
        val excess = itemsOldestFirst.size - maxCount
        return if (excess > 0) itemsOldestFirst.take(excess) else emptyList()
    }

    /**
     * Select oldest items until the remaining total is within [maxBytes].
     * [totalBytes] may be supplied when the list is only a window of a larger set.
     * zh-CN: 从最旧开始选择, 直到剩余总量不超过 [maxBytes]; 列表只是更大集合的窗口时可传入 [totalBytes].
     */
    fun <T> selectOverBytes(
        itemsOldestFirst: List<T>,
        maxBytes: Long,
        totalBytes: Long? = null,
        sizeOf: (T) -> Long,
    ): List<T> {
        if (isUnlimited(maxBytes)) return emptyList()
        var total = (totalBytes ?: itemsOldestFirst.sumOf { sizeOf(it).coerceAtLeast(0L) }).coerceAtLeast(0L)
        if (total <= maxBytes) return emptyList()
        val result = ArrayList<T>()
        for (item in itemsOldestFirst) {
            if (total <= maxBytes) break
            result.add(item)
            total -= sizeOf(item).coerceAtLeast(0L)
        }
        return result
    }

    /**
     * Apply days, then count, then bytes; returns the union to delete (oldest first, no duplicates).
     * zh-CN: 依次应用天数 / 条数 / 字节上限, 返回应删除条目的并集 (最旧优先, 无重复).
     */
    fun <T> selectToDelete(
        itemsOldestFirst: List<T>,
        now: Long,
        maxDays: Int,
        maxCount: Int,
        maxBytes: Long,
        createdAt: (T) -> Long,
        sizeOf: (T) -> Long,
    ): List<T> {
        val remaining = itemsOldestFirst.toMutableList()
        val result = ArrayList<T>()

        val expired = selectExpired(remaining, now, maxDays, createdAt)
        result.addAll(expired)
        remaining.removeAll(expired.toSet())

        val overCount = selectOverCount(remaining, maxCount)
        result.addAll(overCount)
        remaining.removeAll(overCount.toSet())

        result.addAll(selectOverBytes(remaining, maxBytes, sizeOf = sizeOf))
        return result
    }
}
