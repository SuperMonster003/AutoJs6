package org.autojs.autojs.core.console

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats console log lines. Android-free (only `android.util.Log` compile-time constants are used)
 * so the policy is unit-testable.
 *
 * A log entry keeps its raw content and its timestamp separately; the time prefix
 * (`HH:mm:ss.SSS/D: `) is rendered by the view that displays the entry, so the same entry can be
 * shown with or without time information by different consoles.
 *
 * zh-CN: 控制台日志行的格式化工具. 不依赖 Android (仅使用 `android.util.Log` 的编译期常量), 便于单元测试.
 * 日志条目分别保存原始内容与时间戳, 时间前缀 (`HH:mm:ss.SSS/D: `) 由显示条目的视图渲染, 因此同一条目可在不同控制台中以带或不带时间信息的方式显示.
 *
 * Created by SuperMonster003 on Sep 8, 2026.
 */
object ConsoleLogFormat {

    const val DEFAULT_TIME_PATTERN = "HH:mm:ss.SSS"

    /** Time patterns offered by the console settings dialog. zh-CN: 控制台设置对话框提供的时间格式预设. */
    @JvmField
    val PRESET_TIME_PATTERNS = listOf(
        "HH:mm:ss.SSS",
        "HH:mm:ss",
        "mm:ss.SSS",
        "yyyy-MM-dd HH:mm:ss.SSS",
        "yyyy-MM-dd HH:mm:ss",
        "MM-dd HH:mm:ss",
    )

    private const val MAX_TIME_PATTERN_LENGTH = 64

    private val sFormatters = ThreadLocal<HashMap<String, SimpleDateFormat>>()

    @JvmStatic
    fun levelChar(level: Int): String = when (level) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        Log.ASSERT -> "A"
        else -> ""
    }

    /** Whether [pattern] is a usable `SimpleDateFormat` pattern. zh-CN: [pattern] 是否为可用的 `SimpleDateFormat` 模式. */
    @JvmStatic
    fun isValidTimePattern(pattern: String?): Boolean {
        if (pattern.isNullOrBlank() || pattern.length > MAX_TIME_PATTERN_LENGTH) return false
        return runCatching { SimpleDateFormat(pattern, Locale.ROOT) }.isSuccess
    }

    /** [pattern] when valid, otherwise [DEFAULT_TIME_PATTERN]. zh-CN: [pattern] 有效时返回其本身, 否则返回 [DEFAULT_TIME_PATTERN]. */
    @JvmStatic
    fun normalizeTimePattern(pattern: String?): String = when {
        isValidTimePattern(pattern) -> pattern!!.trim()
        else -> DEFAULT_TIME_PATTERN
    }

    @JvmStatic
    @JvmOverloads
    fun formatTime(timestamp: Long, pattern: String = DEFAULT_TIME_PATTERN, locale: Locale = Locale.getDefault()): String {
        return formatter(normalizeTimePattern(pattern), locale).format(Date(timestamp))
    }

    /** The `HH:mm:ss.SSS/D: ` style prefix of an entry. zh-CN: 条目的 `HH:mm:ss.SSS/D: ` 样式前缀. */
    @JvmStatic
    @JvmOverloads
    fun prefix(timestamp: Long, level: Int, pattern: String = DEFAULT_TIME_PATTERN, locale: Locale = Locale.getDefault()): String {
        return "${formatTime(timestamp, pattern, locale)}/${levelChar(level)}: "
    }

    @JvmStatic
    @JvmOverloads
    fun formatLine(
        timestamp: Long,
        level: Int,
        content: CharSequence,
        timeVisible: Boolean = true,
        pattern: String = DEFAULT_TIME_PATTERN,
        locale: Locale = Locale.getDefault(),
    ): String = when (timeVisible) {
        true -> prefix(timestamp, level, pattern, locale) + content
        else -> content.toString()
    }

    private fun formatter(pattern: String, locale: Locale): SimpleDateFormat {
        val cache = sFormatters.get() ?: HashMap<String, SimpleDateFormat>().also { sFormatters.set(it) }
        val key = "$pattern ${locale.toLanguageTag()}"
        return cache.getOrPut(key) { SimpleDateFormat(pattern, locale) }
    }

}
