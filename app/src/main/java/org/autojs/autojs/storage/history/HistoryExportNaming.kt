package org.autojs.autojs.storage.history

/**
 * Suggested file names for exported revisions.
 * zh-CN: 导出 revision 时的建议文件名.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object HistoryExportNaming {

    /**
     * Insert [stamp] before the last extension: `main.js` + `20260916-200103` gives
     * `main.20260916-200103.js`; names without an extension (or dot-files) get the stamp appended.
     * zh-CN: 把 [stamp] 插到最后一个扩展名之前: `main.js` + `20260916-200103` 得到 `main.20260916-200103.js`;
     * 无扩展名 (或点文件) 则直接追加.
     */
    fun suggestName(originalName: String, stamp: String): String {
        val name = originalName.trim().ifEmpty { "revision" }
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot >= name.length - 1) return "$name.$stamp"
        return name.substring(0, dot) + "." + stamp + name.substring(dot)
    }

    /**
     * A bare file name: not blank, no path separators, not `.` or `..`.
     * zh-CN: 合法的单段文件名: 非空, 不含路径分隔符, 不是 `.` 或 `..`.
     */
    fun isValidFileName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == "." || trimmed == "..") return false
        return !trimmed.contains('/') && !trimmed.contains('\\')
    }
}
