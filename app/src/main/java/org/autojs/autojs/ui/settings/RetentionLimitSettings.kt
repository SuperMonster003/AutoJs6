package org.autojs.autojs.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.keys.HistoryKeys
import org.autojs.autojs.pio.PFiles
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
 * The keys and their defaults are the declarations of [HistoryKeys] (docs/settings/ROADMAP.md P2.2); this object keeps
 * the page part (titles, units, formatting).
 * zh-CN: 键及其默认值即 [HistoryKeys] 的声明 (ROADMAP P2.2); 本对象只保留页面部分 (标题, 单位, 格式化).
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object RetentionLimitSettings {

    enum class Kind { DAYS, COUNT, BYTES }

    /** A limit: an Int key of [HistoryKeys] for days and counts, a Long key for bytes. */
    class Spec private constructor(
        val key: SettingKey<*>,
        @StringRes val titleRes: Int,
        val kind: Kind,
    ) {
        constructor(key: SettingKey.Int, @StringRes titleRes: Int, kind: Kind) : this(key as SettingKey<*>, titleRes, kind) {
            require(kind != Kind.BYTES) { "${key.name}: byte limits are Long keys" }
        }

        constructor(key: SettingKey.Long, @StringRes titleRes: Int) : this(key as SettingKey<*>, titleRes, Kind.BYTES)

        @get:StringRes
        val keyRes: Int
            get() = key.keyRes

        val defaultValue: Long
            get() = (key.defaultOrNull() as Number).toLong()
    }

    val specs: List<Spec> = listOf(
        Spec(HistoryKeys.HISTORY_MAX_DAYS, R.string.text_history_max_days, Kind.DAYS),
        Spec(HistoryKeys.HISTORY_MAX_VERSIONS, R.string.text_history_max_versions, Kind.COUNT),
        Spec(HistoryKeys.HISTORY_MAX_TOTAL_BYTES_PER_FILE, R.string.text_history_max_total_bytes_per_file),
        Spec(HistoryKeys.HISTORY_MAX_TOTAL_BYTES, R.string.text_history_max_total_bytes),
        Spec(HistoryKeys.HISTORY_MAX_FILE_SIZE_TO_TRACK_BYTES, R.string.text_history_max_file_size_to_track),
        Spec(HistoryKeys.TRASH_MAX_DAYS, R.string.text_trash_max_days, Kind.DAYS),
        Spec(HistoryKeys.TRASH_MAX_TOTAL_BYTES, R.string.text_trash_max_total_bytes),
        Spec(HistoryKeys.DRAFTS_MAX_DAYS, R.string.text_drafts_max_days, Kind.DAYS),
        Spec(HistoryKeys.DRAFTS_MAX_TOTAL_BYTES, R.string.text_drafts_max_total_bytes),
    )

    @Suppress("UNUSED_PARAMETER")
    fun specForKey(context: Context, key: String?): Spec? = specForKey(key)

    /** The spec whose key name is [key] (a preference key of the page). */
    fun specForKey(key: String?): Spec? {
        key ?: return null
        return specs.firstOrNull { it.key.name == key }
    }

    fun read(spec: Spec): Long = when (val key = spec.key) {
        is SettingKey.Int -> key.get().toLong()
        is SettingKey.Long -> key.get()
        else -> throw IllegalStateException("${key.name} is no retention limit key")
    }

    fun write(spec: Spec, value: Long) {
        when (val key = spec.key) {
            is SettingKey.Int -> key.set(value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
            is SettingKey.Long -> key.set(value.coerceAtLeast(0L))
            else -> throw IllegalStateException("${key.name} is no retention limit key")
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
