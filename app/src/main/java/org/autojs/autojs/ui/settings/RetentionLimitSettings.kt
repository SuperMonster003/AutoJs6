package org.autojs.autojs.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.storage.history.HistoryPrefs
import org.autojs.autojs.storage.history.RetentionLimitUnits
import org.autojs.autojs6.R

/**
 * Registry of the retention limit preferences shown on the trash and version history settings page.
 * zh-CN: 回收站与版本历史设置页中各保留策略偏好的注册表.
 *
 * Every limit is stored as before (Int for days / counts, Long for bytes); zero or a negative
 * value means unlimited, matching HistoryRetentionPolicy.
 * zh-CN: 存储格式保持不变 (天数 / 条数为 Int, 字节为 Long); 0 或负数表示不限制, 与 HistoryRetentionPolicy 一致.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object RetentionLimitSettings {

    enum class Kind { DAYS, COUNT, BYTES }

    class Spec(
        @StringRes val keyRes: Int,
        @StringRes val titleRes: Int,
        val kind: Kind,
        val defaultValue: Long,
    )

    val specs: List<Spec> = listOf(
        Spec(R.string.key_history_max_days, R.string.text_history_max_days, Kind.DAYS, HistoryPrefs.DEFAULT_HISTORY_MAX_DAYS.toLong()),
        Spec(R.string.key_history_max_versions, R.string.text_history_max_versions, Kind.COUNT, HistoryPrefs.DEFAULT_HISTORY_MAX_VERSIONS.toLong()),
        Spec(R.string.key_history_max_total_bytes_per_file, R.string.text_history_max_total_bytes_per_file, Kind.BYTES, HistoryPrefs.DEFAULT_HISTORY_MAX_TOTAL_BYTES_PER_FILE),
        Spec(R.string.key_history_max_total_bytes, R.string.text_history_max_total_bytes, Kind.BYTES, HistoryPrefs.DEFAULT_HISTORY_MAX_TOTAL_BYTES),
        Spec(R.string.key_history_max_file_size_to_track_bytes, R.string.text_history_max_file_size_to_track, Kind.BYTES, HistoryPrefs.DEFAULT_MAX_FILE_SIZE_TO_TRACK_BYTES),
        Spec(R.string.key_trash_max_days, R.string.text_trash_max_days, Kind.DAYS, HistoryPrefs.DEFAULT_TRASH_MAX_DAYS.toLong()),
        Spec(R.string.key_trash_max_total_bytes, R.string.text_trash_max_total_bytes, Kind.BYTES, HistoryPrefs.DEFAULT_TRASH_MAX_TOTAL_BYTES),
        Spec(R.string.key_drafts_max_days, R.string.text_drafts_max_days, Kind.DAYS, HistoryPrefs.DEFAULT_DRAFTS_MAX_DAYS.toLong()),
        Spec(R.string.key_drafts_max_total_bytes, R.string.text_drafts_max_total_bytes, Kind.BYTES, HistoryPrefs.DEFAULT_DRAFTS_MAX_TOTAL_BYTES),
    )

    fun specForKey(context: Context, key: String?): Spec? {
        key ?: return null
        return specs.firstOrNull { context.getString(it.keyRes) == key }
    }

    fun read(spec: Spec): Long = when (spec.kind) {
        Kind.DAYS, Kind.COUNT -> Pref.getInt(spec.keyRes, spec.defaultValue.toInt()).toLong()
        Kind.BYTES -> Pref.getLong(spec.keyRes, spec.defaultValue)
    }

    fun write(spec: Spec, value: Long) {
        when (spec.kind) {
            Kind.DAYS, Kind.COUNT -> Pref.putInt(spec.keyRes, value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
            Kind.BYTES -> Pref.putLong(spec.keyRes, value.coerceAtLeast(0L))
        }
    }

    /**
     * Human readable current value, e.g. "30 days", "50 versions", "200 MB" or "Unlimited".
     * zh-CN: 当前值的可读形式, 如 "30 天", "50 个版本", "200 MB" 或 "不限制".
     */
    fun formatValue(context: Context, spec: Spec, value: Long): String {
        if (value <= 0L) return context.getString(R.string.text_unlimited)
        return when (spec.kind) {
            Kind.DAYS -> context.getString(R.string.text_n_days, value)
            Kind.COUNT -> context.getString(R.string.text_n_versions, value)
            Kind.BYTES -> PFiles.formatSizeWithUnit(value)
        }
    }

    /** The number the input field shows for a stored value (bytes are edited as megabytes). */
    fun toInputNumber(spec: Spec, value: Long): Long = when (spec.kind) {
        Kind.BYTES -> RetentionLimitUnits.bytesToMegabytesRounded(value)
        else -> value.coerceAtLeast(0L)
    }

    /** The stored value for a number typed into the input field. */
    fun fromInputNumber(spec: Spec, input: Long): Long = when (spec.kind) {
        Kind.BYTES -> RetentionLimitUnits.megabytesToBytes(input)
        else -> input.coerceAtLeast(0L)
    }

    fun inputHint(context: Context, spec: Spec): String = when (spec.kind) {
        Kind.DAYS -> context.getString(R.string.text_unit_days)
        Kind.COUNT -> context.getString(R.string.text_unit_versions)
        Kind.BYTES -> "MB"
    }
}
