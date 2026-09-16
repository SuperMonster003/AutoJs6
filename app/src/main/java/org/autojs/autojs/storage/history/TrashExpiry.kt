package org.autojs.autojs.storage.history

/**
 * When a trash item becomes eligible for cleanup, mirroring [HistoryRetentionPolicy.selectExpired]:
 * an item expires exactly `maxDays` after it was trashed; the periodic cleanup removes it at or
 * after that moment.
 *
 * zh-CN: 回收站条目何时可被清理, 与 [HistoryRetentionPolicy.selectExpired] 一致: 移入后恰好 `maxDays` 天到期,
 * 周期清理在该时刻或之后移除.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object TrashExpiry {

    const val DAY_MS: Long = 24L * 60L * 60L * 1000L
    const val HOUR_MS: Long = 60L * 60L * 1000L

    /**
     * [days] and [hours] are the whole units left; [expired] when the moment has passed;
     * [withinOneDay] when less than a day (including expired) remains, for highlighting.
     * zh-CN: [days] / [hours] 为剩余整数单位; [expired] 表示已过期; [withinOneDay] 表示不足一天 (含已过期), 用于高亮.
     */
    data class Remaining(val days: Long, val hours: Long, val expired: Boolean, val withinOneDay: Boolean)

    /** Null when the retention days are unlimited. zh-CN: 保留天数不限时为 null. */
    fun expiresAtOrNull(trashedAt: Long, maxDays: Int): Long? =
        if (HistoryRetentionPolicy.isUnlimited(maxDays)) null else trashedAt + maxDays.toLong() * DAY_MS

    fun remaining(expiresAt: Long, now: Long): Remaining {
        val left = expiresAt - now
        if (left <= 0L) return Remaining(0L, 0L, expired = true, withinOneDay = true)
        return Remaining(
            days = left / DAY_MS,
            hours = (left % DAY_MS) / HOUR_MS,
            expired = false,
            withinOneDay = left < DAY_MS,
        )
    }
}
