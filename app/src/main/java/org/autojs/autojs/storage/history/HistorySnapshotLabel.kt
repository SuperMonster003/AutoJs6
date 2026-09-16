package org.autojs.autojs.storage.history

/**
 * Normalization of user-entered snapshot labels.
 * zh-CN: 用户输入的快照标签的规范化.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object HistorySnapshotLabel {

    const val MAX_LENGTH: Int = 80

    /**
     * Trim, collapse inner whitespace (including line breaks) to single spaces and cap the length;
     * a blank input becomes null, meaning "no label".
     * zh-CN: 去除首尾空白, 内部空白 (含换行) 折叠为单个空格并限制长度; 空白输入返回 null 表示 "无标签".
     */
    fun normalizeOrNull(raw: String?): String? {
        if (raw == null) return null
        val collapsed = raw.trim().replace(WHITESPACE_RUN, " ")
        if (collapsed.isEmpty()) return null
        return if (collapsed.length > MAX_LENGTH) collapsed.substring(0, MAX_LENGTH).trimEnd() else collapsed
    }

    private val WHITESPACE_RUN = Regex("\\s+")
}
