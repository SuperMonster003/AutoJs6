package org.autojs.autojs.storage.history

/**
 * Unit conversions for the retention limit settings dialog (pure, unit tested).
 * zh-CN: 保留策略设置对话框使用的单位换算 (纯函数, 有单测).
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object RetentionLimitUnits {

    const val MEGABYTE: Long = 1024L * 1024L

    /** Longest digit string accepted from the input field; enough for any practical limit. */
    const val MAX_INPUT_DIGITS: Int = 12

    /**
     * Megabytes entered by the user to stored bytes; zero or negative stays zero (unlimited).
     * zh-CN: 用户输入的 MB 转为存储的字节数; 0 或负数保持为 0 (不限制).
     */
    fun megabytesToBytes(megabytes: Long): Long {
        if (megabytes <= 0L) return 0L
        val capped = megabytes.coerceAtMost(Long.MAX_VALUE / MEGABYTE)
        return capped * MEGABYTE
    }

    /**
     * Stored bytes to the megabytes shown in the input field, rounded to the nearest whole
     * megabyte but never rounding a positive limit down to zero.
     * zh-CN: 存储的字节数转为输入框显示的 MB, 四舍五入到整数, 正数上限不会被舍成 0.
     */
    fun bytesToMegabytesRounded(bytes: Long): Long {
        if (bytes <= 0L) return 0L
        val rounded = (bytes + MEGABYTE / 2) / MEGABYTE
        return rounded.coerceAtLeast(1L)
    }

    /**
     * Parse a non-negative whole number typed by the user, or null when the text is not one.
     * zh-CN: 解析用户输入的非负整数, 不合法时返回 null.
     */
    fun parseNonNegativeOrNull(input: CharSequence?): Long? {
        val text = input?.toString()?.trim().orEmpty()
        if (text.isEmpty() || text.length > MAX_INPUT_DIGITS) return null
        if (!text.all { it in '0'..'9' }) return null
        return text.toLongOrNull()
    }
}
