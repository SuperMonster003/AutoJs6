package org.autojs.autojs.storage.history

/**
 * Decision behind the "trash quota exceeded" prompt shown before moving items to the trash (D8):
 * the batch total is compared with the trash size limit; only an over-limit batch asks the user.
 * Pure: no Android dependencies.
 *
 * zh-CN: 移入回收站前 "超出回收站上限" 提示的决策 (D8): 整批总大小与回收站容量上限比较, 仅超限时询问用户. 纯逻辑, 不依赖 Android.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object TrashQuotaHint {

    enum class Decision { PROCEED, ASK }

    /** False when the limit is unlimited, so callers can skip measuring the batch entirely. */
    fun needsMeasurement(limitBytes: Long): Boolean = !HistoryRetentionPolicy.isUnlimited(limitBytes)

    fun decide(limitBytes: Long, totalBytes: Long): Decision = when {
        !needsMeasurement(limitBytes) -> Decision.PROCEED
        totalBytes <= limitBytes -> Decision.PROCEED
        else -> Decision.ASK
    }
}
