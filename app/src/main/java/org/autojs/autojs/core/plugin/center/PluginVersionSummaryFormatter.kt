package org.autojs.autojs.core.plugin.center

import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat
import java.util.Locale

/**
 * Formats the immutable version labels rendered by Plugin Center rows and dialogs.
 *
 * Input formatters are built once instead of being recreated for every RecyclerView bind.
 * [DateTimeFormatter] is immutable and thread-safe, so model assembly can perform this work
 * on a background dispatcher and hand the UI a display-ready string.
 *
 * Dates are always rendered as ISO 8601 calendar dates (`yyyy-MM-dd`). Plain date patterns are
 * tried first; ISO 8601 date-times (for example `2026-09-11T14:30:00+08:00`) fall back to the
 * ISO parser and keep the calendar date of their own offset instead of the device zone.
 *
 * zh-CN:
 *
 * 格式化插件中心列表项及对话框使用的不可变版本标签.
 * 输入格式器仅构建一次, 避免在 RecyclerView 每次绑定时重复创建.
 * [DateTimeFormatter] 不可变且线程安全, 因此模型组装可在后台完成格式化,
 * 再向 UI 交付可直接显示的字符串.
 * 日期统一以 ISO 8601 日历日期 (`yyyy-MM-dd`) 呈现: 先尝试纯日期格式,
 * ISO 8601 日期时间 (如 `2026-09-11T14:30:00+08:00`) 则回退到 ISO 解析器,
 * 并保留其自身时区偏移下的日历日期, 而非设备时区.
 */
internal object PluginVersionSummaryFormatter {

    private val outputFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")

    private val isoDateTimeParser = ISODateTimeFormat.dateTimeParser().withOffsetParsed()

    private val inputFormatters = listOf(
        formatter("MMM dd, yyyy", Locale.ENGLISH),
        formatter("yyyy-MM-dd"),
        formatter("yyyy/MM/dd"),
        formatter("dd/MM/yyyy"),
        formatter("MM/dd/yyyy"),
        formatter("yyyy.MM.dd"),
        formatter("dd.MM.yyyy"),
        formatter("yyyyMMdd"),
        formatter("yyyy-MM-dd HH:mm:ss"),
        formatter("yyyy/MM/dd HH:mm:ss"),
        formatter("yyyy-MM-dd HH:mm"),
        formatter("yyyy/MM/dd HH:mm"),
        formatter("dd MMM yyyy", Locale.ENGLISH),
        formatter("MMM dd yyyy", Locale.ENGLISH),
    )

    fun format(versionName: String, versionCode: Long?, versionDate: String?): String {
        val code = versionCode?.takeIf { it > 0L }
        val date = versionDate
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let(::normalizeDate)

        return buildString {
            append(versionName)
            code?.let { append(" ($it)") }
            date?.let { append(" | $it") }
        }
    }

    private fun normalizeDate(value: String): String {
        return inputFormatters.firstNotNullOfOrNull { formatter ->
            runCatching { outputFormatter.print(formatter.parseDateTime(value)) }.getOrNull()
        } ?: normalizeIsoDateTime(value) ?: value
    }

    private fun normalizeIsoDateTime(value: String): String? {
        if (!value.contains('T')) return null
        return runCatching { outputFormatter.print(isoDateTimeParser.parseDateTime(value)) }.getOrNull()
    }

    private fun formatter(pattern: String, locale: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormat.forPattern(pattern).withLocale(locale)
}
