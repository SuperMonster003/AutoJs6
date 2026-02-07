package org.autojs.autojs.ui.storage

import android.content.Context
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs6.R
import java.util.Locale

/**
 * Storage usage summary formatter for header/subtitle.
 * zh-CN: header/subtitle 的容量摘要格式化工具.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 5, 2026.
 */
object StorageUsageSummaryFormatter {

    private const val HEADER_PIPE: String = "  |  "
    private const val SUBTITLE_PIPE: String = " | "

    /**
     * Format percent text from bytes and limit.
     * zh-CN: 根据已用字节与上限字节格式化百分比文本.
     */
    fun formatPercentText(bytes: Long, limitBytes: Long, locale: Locale = Locale.getDefault()): String {
        val used = bytes.coerceAtLeast(0L)
        val limit = limitBytes.coerceAtLeast(0L)
        return if (limit > 0L) {
            val ratio = (used.toDouble() / limit.toDouble()).coerceAtLeast(0.0)
            String.format(locale, "%.1f%%", ratio * 100.0)
        } else {
            String.format(locale, "%.1f%%", 0.0)
        }
    }

    /**
     * Format header subtitle text.
     * zh-CN: 格式化 header 的子标题文本.
     */
    fun formatHeader(
        context: Context,
        count: Long,
        totalBytes: Long,
        limitBytes: Long,
    ): String {
        val countSafe = count.coerceAtLeast(0L)
        val bytesSafe = totalBytes.coerceAtLeast(0L)
        val limitSafe = limitBytes.coerceAtLeast(0L)

        val itemsPart = context.getString(R.string.text_items_count_colon_value, countSafe)

        val sizeText = PFiles.formatSizeWithUnit(bytesSafe)
        val percentText = formatPercentText(bytesSafe, limitSafe)

        var sizePart = context.getString(R.string.text_total_size_colon_value, sizeText)
        if (limitSafe > 0L) {
            sizePart += " ($percentText)"
        }

        return listOf(itemsPart, sizePart).joinToString(HEADER_PIPE)
    }

    /**
     * Format drawer subtitle text (compact).
     * zh-CN: 格式化抽屉子标题文本 (紧凑).
     */
    fun formatDrawerSubtitle(
        count: Long,
        totalBytes: Long,
        limitBytes: Long,
        locale: Locale = Locale.getDefault(),
    ): String {
        val countSafe = count.coerceAtLeast(0L)
        val bytesSafe = totalBytes.coerceAtLeast(0L)
        val limitSafe = limitBytes.coerceAtLeast(0L)

        val sizeText = PFiles.formatSizeWithUnit(bytesSafe)
        val percentText = formatPercentText(bytesSafe, limitSafe, locale)

        return listOf(
            countSafe.toString(),
            sizeText,
            percentText,
        ).joinToString(SUBTITLE_PIPE)
    }
}
