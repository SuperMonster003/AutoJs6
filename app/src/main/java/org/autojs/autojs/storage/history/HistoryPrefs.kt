package org.autojs.autojs.storage.history

import org.autojs.autojs.core.pref.keys.HistoryKeys

/**
 * Preferences for history/trash/drafts policies.
 * zh-CN: history/trash/drafts 策略相关的偏好设置读取.
 *
 * The keys and readers moved to [HistoryKeys] (docs/settings/ROADMAP.md P2.2, T17); this object forwards to it until
 * the old entries are removed (P5.2 / P14.1).
 * zh-CN: 键与读取函数已并入 [HistoryKeys] (ROADMAP P2.2, T17); 本对象在旧入口删除 (P5.2 / P14.1) 前转发到它.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 5, 2026.
 */
@Deprecated("Use HistoryKeys (docs/settings/ROADMAP.md P2.2)", ReplaceWith("HistoryKeys", "org.autojs.autojs.core.pref.keys.HistoryKeys"))
object HistoryPrefs {

    // Max file size to track history snapshots (default 20MB).
    // zh-CN: 纳管历史快照的最大文件大小 (默认 20MB).
    fun maxFileSizeToTrackBytes(): Long = HistoryKeys.maxFileSizeToTrackBytes()

    // History retention days (default 30).
    // zh-CN: 历史保留天数 (默认 30).
    fun historyMaxDays(): Int = HistoryKeys.historyMaxDays()

    // History max versions per file (default 50).
    // zh-CN: 单文件历史最大版本数 (默认 50).
    fun historyMaxVersions(): Int = HistoryKeys.historyMaxVersions()

    // History max total bytes per file (default 200MB).
    // zh-CN: 单文件历史总容量上限 (默认 200MB).
    fun historyMaxTotalBytesPerFile(): Long = HistoryKeys.historyMaxTotalBytesPerFile()

    // History max total bytes (default 2GB).
    // zh-CN: 历史总容量上限 (默认 2GB).
    fun historyMaxTotalBytes(): Long = HistoryKeys.historyMaxTotalBytes()

    // Trash retention days (default 30).
    // zh-CN: 回收站保留天数 (默认 30).
    fun trashMaxDays(): Int = HistoryKeys.trashMaxDays()

    // Trash max total bytes (default 500MB).
    // zh-CN: 回收站总容量上限 (默认 500MB).
    fun trashMaxTotalBytes(): Long = HistoryKeys.trashMaxTotalBytes()

    // Draft retention days (default 7).
    // zh-CN: 草稿保留天数 (默认 7).
    fun draftsMaxDays(): Int = HistoryKeys.draftsMaxDays()

    // Draft max total bytes (default 200MB).
    // zh-CN: 草稿总容量上限 (默认 200MB).
    fun draftsMaxTotalBytes(): Long = HistoryKeys.draftsMaxTotalBytes()

    // Whether to clear all history when deleting permanently (default false).
    // zh-CN: 是否在彻底删除时同时清除历史记录 (默认 false).
    @JvmStatic
    fun deletePermanentlyAlsoClearHistory(): Boolean = HistoryKeys.deletePermanentlyAlsoClearHistory()

    internal const val DEFAULT_MAX_FILE_SIZE_TO_TRACK_BYTES: Long = HistoryKeys.DEFAULT_MAX_FILE_SIZE_TO_TRACK_BYTES

    internal const val DEFAULT_HISTORY_MAX_DAYS: Int = HistoryKeys.DEFAULT_HISTORY_MAX_DAYS
    internal const val DEFAULT_HISTORY_MAX_VERSIONS: Int = HistoryKeys.DEFAULT_HISTORY_MAX_VERSIONS
    internal const val DEFAULT_HISTORY_MAX_TOTAL_BYTES_PER_FILE: Long = HistoryKeys.DEFAULT_HISTORY_MAX_TOTAL_BYTES_PER_FILE
    internal const val DEFAULT_HISTORY_MAX_TOTAL_BYTES: Long = HistoryKeys.DEFAULT_HISTORY_MAX_TOTAL_BYTES

    internal const val DEFAULT_TRASH_MAX_DAYS: Int = HistoryKeys.DEFAULT_TRASH_MAX_DAYS
    internal const val DEFAULT_TRASH_MAX_TOTAL_BYTES: Long = HistoryKeys.DEFAULT_TRASH_MAX_TOTAL_BYTES

    internal const val DEFAULT_DRAFTS_MAX_DAYS: Int = HistoryKeys.DEFAULT_DRAFTS_MAX_DAYS
    internal const val DEFAULT_DRAFTS_MAX_TOTAL_BYTES: Long = HistoryKeys.DEFAULT_DRAFTS_MAX_TOTAL_BYTES
}
