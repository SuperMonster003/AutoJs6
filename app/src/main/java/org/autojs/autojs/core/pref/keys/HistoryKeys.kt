package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.RegisteredName
import org.autojs.autojs.core.pref.SettingCategory.Companion.HISTORY
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs6.R

/**
 * Settings > Trash and version history (docs/settings/ROADMAP.md P2.2, appendix A.1 `history`): the nine retention
 * limits of version history, trash and drafts, and whether deleting permanently also clears the history. Zero or a
 * negative limit means unlimited (`HistoryRetentionPolicy`); the cleanup tasks read the limits when they run. The
 * readers of the former `HistoryPrefs` live here (T17); "restore does not auto save to disk" is a not-ask-again state
 * (P4.1, appendix C Q13).
 *
 * zh-CN: 设置 > 回收站与版本历史 (ROADMAP P2.2, 附录 A.1 `history`): 版本历史, 回收站与草稿的九个保留上限, 以及彻底删除时是否
 * 同时清除历史. 0 或负数表示不限制 (`HistoryRetentionPolicy`); 清理任务运行时读取上限. 原 `HistoryPrefs` 的读取函数并入本对象
 * (T17); "恢复后不自动保存到磁盘" 是不再提示状态 (P4.1, 附录 C Q13).
 */
object HistoryKeys : SettingsDomain {

    const val DEFAULT_MAX_FILE_SIZE_TO_TRACK_BYTES: Long = 20L * 1024L * 1024L

    const val DEFAULT_HISTORY_MAX_DAYS: Int = 30
    const val DEFAULT_HISTORY_MAX_VERSIONS: Int = 50
    const val DEFAULT_HISTORY_MAX_TOTAL_BYTES_PER_FILE: Long = 200L * 1024L * 1024L
    const val DEFAULT_HISTORY_MAX_TOTAL_BYTES: Long = 2L * 1024L * 1024L * 1024L

    const val DEFAULT_TRASH_MAX_DAYS: Int = 30
    const val DEFAULT_TRASH_MAX_TOTAL_BYTES: Long = 500L * 1024L * 1024L

    const val DEFAULT_DRAFTS_MAX_DAYS: Int = 7
    const val DEFAULT_DRAFTS_MAX_TOTAL_BYTES: Long = 200L * 1024L * 1024L

    /** Files larger than this get no history snapshots (default 20 MB). */
    @JvmField
    val HISTORY_MAX_FILE_SIZE_TO_TRACK_BYTES = SettingKey.Long(R.string.key_history_max_file_size_to_track_bytes, HISTORY, SettingDefault.of(DEFAULT_MAX_FILE_SIZE_TO_TRACK_BYTES))

    @JvmField
    val HISTORY_MAX_DAYS = SettingKey.Int(R.string.key_history_max_days, HISTORY, SettingDefault.of(DEFAULT_HISTORY_MAX_DAYS))

    @JvmField
    val HISTORY_MAX_VERSIONS = SettingKey.Int(R.string.key_history_max_versions, HISTORY, SettingDefault.of(DEFAULT_HISTORY_MAX_VERSIONS))

    @JvmField
    val HISTORY_MAX_TOTAL_BYTES_PER_FILE = SettingKey.Long(R.string.key_history_max_total_bytes_per_file, HISTORY, SettingDefault.of(DEFAULT_HISTORY_MAX_TOTAL_BYTES_PER_FILE))

    @JvmField
    val HISTORY_MAX_TOTAL_BYTES = SettingKey.Long(R.string.key_history_max_total_bytes, HISTORY, SettingDefault.of(DEFAULT_HISTORY_MAX_TOTAL_BYTES))

    @JvmField
    val TRASH_MAX_DAYS = SettingKey.Int(R.string.key_trash_max_days, HISTORY, SettingDefault.of(DEFAULT_TRASH_MAX_DAYS))

    @JvmField
    val TRASH_MAX_TOTAL_BYTES = SettingKey.Long(R.string.key_trash_max_total_bytes, HISTORY, SettingDefault.of(DEFAULT_TRASH_MAX_TOTAL_BYTES))

    @JvmField
    val DRAFTS_MAX_DAYS = SettingKey.Int(R.string.key_drafts_max_days, HISTORY, SettingDefault.of(DEFAULT_DRAFTS_MAX_DAYS))

    @JvmField
    val DRAFTS_MAX_TOTAL_BYTES = SettingKey.Long(R.string.key_drafts_max_total_bytes, HISTORY, SettingDefault.of(DEFAULT_DRAFTS_MAX_TOTAL_BYTES))

    /** On the main settings page, next to the entry of the trash and version history page. */
    @JvmField
    val DELETE_PERMANENTLY_ALSO_CLEAR_HISTORY = SettingKey.Bool(R.string.key_delete_permanently_also_clear_history, HISTORY, SettingDefault.of(false))

    override val keys: List<PrefKey<*>> = listOf(
        HISTORY_MAX_DAYS, HISTORY_MAX_VERSIONS, HISTORY_MAX_TOTAL_BYTES_PER_FILE, HISTORY_MAX_TOTAL_BYTES, HISTORY_MAX_FILE_SIZE_TO_TRACK_BYTES,
        TRASH_MAX_DAYS, TRASH_MAX_TOTAL_BYTES, DRAFTS_MAX_DAYS, DRAFTS_MAX_TOTAL_BYTES, DELETE_PERMANENTLY_ALSO_CLEAR_HISTORY,
    )

    // The entry of the trash and version history page holds no value.
    override val names: List<RegisteredName> = excludedNames(R.string.key_trash_and_version_history_settings)

    // Readers of the former HistoryPrefs (T17).
    // zh-CN: 原 HistoryPrefs 的读取函数 (T17).

    @JvmStatic
    fun maxFileSizeToTrackBytes(): Long = HISTORY_MAX_FILE_SIZE_TO_TRACK_BYTES.get()

    @JvmStatic
    fun historyMaxDays(): Int = HISTORY_MAX_DAYS.get()

    @JvmStatic
    fun historyMaxVersions(): Int = HISTORY_MAX_VERSIONS.get()

    @JvmStatic
    fun historyMaxTotalBytesPerFile(): Long = HISTORY_MAX_TOTAL_BYTES_PER_FILE.get()

    @JvmStatic
    fun historyMaxTotalBytes(): Long = HISTORY_MAX_TOTAL_BYTES.get()

    @JvmStatic
    fun trashMaxDays(): Int = TRASH_MAX_DAYS.get()

    @JvmStatic
    fun trashMaxTotalBytes(): Long = TRASH_MAX_TOTAL_BYTES.get()

    @JvmStatic
    fun draftsMaxDays(): Int = DRAFTS_MAX_DAYS.get()

    @JvmStatic
    fun draftsMaxTotalBytes(): Long = DRAFTS_MAX_TOTAL_BYTES.get()

    @JvmStatic
    fun deletePermanentlyAlsoClearHistory(): Boolean = DELETE_PERMANENTLY_ALSO_CLEAR_HISTORY.get()
}
