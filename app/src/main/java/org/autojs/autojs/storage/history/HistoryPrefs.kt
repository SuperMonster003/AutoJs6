package org.autojs.autojs.storage.history

import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs6.R

/**
 * Preferences for history/trash/drafts policies.
 * zh-CN: history/trash/drafts 策略相关的偏好设置读取.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 5, 2026.
 */
object HistoryPrefs {

    // Max file size to track history snapshots (default 20MB).
    // zh-CN: 纳管历史快照的最大文件大小 (默认 20MB).
    fun maxFileSizeToTrackBytes(): Long =
        Pref.getLong(R.string.key_history_max_file_size_to_track_bytes, DEFAULT_MAX_FILE_SIZE_TO_TRACK_BYTES)

    // History retention days (default 30).
    // zh-CN: 历史保留天数 (默认 30).
    fun historyMaxDays(): Int =
        Pref.getInt(R.string.key_history_max_days, DEFAULT_HISTORY_MAX_DAYS)

    // History max versions per file (default 50).
    // zh-CN: 单文件历史最大版本数 (默认 50).
    fun historyMaxVersions(): Int =
        Pref.getInt(R.string.key_history_max_versions, DEFAULT_HISTORY_MAX_VERSIONS)

    // History max total bytes per file (default 200MB).
    // zh-CN: 单文件历史总容量上限 (默认 200MB).
    fun historyMaxTotalBytesPerFile(): Long =
        Pref.getLong(R.string.key_history_max_total_bytes_per_file, DEFAULT_HISTORY_MAX_TOTAL_BYTES_PER_FILE)

    // History max total bytes (default 2GB).
    // zh-CN: 历史总容量上限 (默认 2GB).
    fun historyMaxTotalBytes(): Long =
        Pref.getLong(R.string.key_history_max_total_bytes, DEFAULT_HISTORY_MAX_TOTAL_BYTES)

    // Trash retention days (default 30).
    // zh-CN: 回收站保留天数 (默认 30).
    fun trashMaxDays(): Int =
        Pref.getInt(R.string.key_trash_max_days, DEFAULT_TRASH_MAX_DAYS)

    // Trash max total bytes (default 500MB).
    // zh-CN: 回收站总容量上限 (默认 500MB).
    fun trashMaxTotalBytes(): Long =
        Pref.getLong(R.string.key_trash_max_total_bytes, DEFAULT_TRASH_MAX_TOTAL_BYTES)

    // Draft retention days (default 7).
    // zh-CN: 草稿保留天数 (默认 7).
    fun draftsMaxDays(): Int =
        Pref.getInt(R.string.key_drafts_max_days, DEFAULT_DRAFTS_MAX_DAYS)

    // Draft max total bytes (default 200MB).
    // zh-CN: 草稿总容量上限 (默认 200MB).
    fun draftsMaxTotalBytes(): Long =
        Pref.getLong(R.string.key_drafts_max_total_bytes, DEFAULT_DRAFTS_MAX_TOTAL_BYTES)

    // Whether to clear all history when deleting permanently (default false).
    // zh-CN: 是否在彻底删除时同时清除历史记录 (默认 false).
    @JvmStatic
    fun deletePermanentlyAlsoClearHistory(): Boolean =
        Pref.getBoolean(R.string.key_delete_permanently_also_clear_history, false)

    private const val DEFAULT_MAX_FILE_SIZE_TO_TRACK_BYTES: Long = 20L * 1024L * 1024L

    private const val DEFAULT_HISTORY_MAX_DAYS: Int = 30
    private const val DEFAULT_HISTORY_MAX_VERSIONS: Int = 50
    internal const val DEFAULT_HISTORY_MAX_TOTAL_BYTES_PER_FILE: Long = 200L * 1024L * 1024L
    internal const val DEFAULT_HISTORY_MAX_TOTAL_BYTES: Long = 2L * 1024L * 1024L * 1024L

    private const val DEFAULT_TRASH_MAX_DAYS: Int = 30
    internal const val DEFAULT_TRASH_MAX_TOTAL_BYTES: Long = 500L * 1024L * 1024L

    private const val DEFAULT_DRAFTS_MAX_DAYS: Int = 7
    private const val DEFAULT_DRAFTS_MAX_TOTAL_BYTES: Long = 200L * 1024L * 1024L
}
