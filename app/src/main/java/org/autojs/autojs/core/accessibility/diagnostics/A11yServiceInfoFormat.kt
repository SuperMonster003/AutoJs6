package org.autojs.autojs.core.accessibility.diagnostics

import java.util.Locale

/**
 * Formats the bit masks of `AccessibilityServiceInfo` (flags, event types, feedback types,
 * capabilities) and elapsed times for the service manager dialog and its diagnostics text.
 * Android-free so that it can be unit-tested; the bit-name lookup is injected by the caller.
 *
 * zh-CN: 把 `AccessibilityServiceInfo` 的位掩码 (flags, 事件类型, 反馈类型, capabilities) 与时长
 * 格式化为可读文本, 供服务管理器对话框及其诊断文本使用. 不依赖 Android 以便单元测试; 位名称的查找由调用方注入.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
object A11yServiceInfoFormat {

    /** The set bits of [mask] as single-bit values, lowest bit first. zh-CN: [mask] 中已置位的各位, 按低位到高位排列. */
    fun bits(mask: Int): List<Int> = (0 until Int.SIZE_BITS)
        .map { 1 shl it }
        .filter { mask and it != 0 }

    /**
     * Names every set bit of [mask] through [nameOf], strips [prefix] from the names, falls back to
     * `0x..` for bits without a name and joins the result with ", ". An empty mask yields "0".
     *
     * zh-CN: 通过 [nameOf] 为 [mask] 的每个置位取名, 去掉 [prefix] 前缀, 未知位以 `0x..` 表示, 以 ", " 连接; 空掩码返回 "0".
     */
    fun format(mask: Int, prefix: String, nameOf: (bit: Int) -> String?): String {
        if (mask == 0) return "0"
        return bits(mask).joinToString(", ") { bit ->
            nameOf(bit)?.takeIf { it.isNotBlank() }?.removePrefix(prefix) ?: "0x${Integer.toHexString(bit).uppercase(Locale.US)}"
        }
    }

    /**
     * Elapsed time for "x ago" style values: `48 ms`, `2.5 s`, `3 min 12 s`, `1 h 5 min`.
     * zh-CN: 用于 "距今" 类数值的时长文本: `48 ms`, `2.5 s`, `3 min 12 s`, `1 h 5 min`.
     */
    fun formatElapsed(millis: Long): String = when {
        millis < 0L -> "0 ms"
        millis < 1_000L -> "$millis ms"
        millis < 60_000L -> String.format(Locale.US, "%.1f s", millis / 1000.0)
        millis < 3_600_000L -> "${millis / 60_000L} min ${millis % 60_000L / 1000L} s"
        else -> "${millis / 3_600_000L} h ${millis % 3_600_000L / 60_000L} min"
    }

}
