package org.autojs.autojs.theme.library

/**
 * Name rules for user color libraries and their colors.
 * zh-CN: 用户颜色库及其颜色的命名规则.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
object LibraryNaming {

    const val MAX_LENGTH = 64

    /**
     * Trims, collapses inner whitespace and limits the length; null when nothing is left.
     * zh-CN: 去除首尾空白, 折叠内部空白并限制长度; 结果为空时返回 null.
     */
    fun normalize(input: String?): String? {
        val collapsed = input?.trim()?.replace(Regex("\\s+"), " ") ?: return null
        if (collapsed.isEmpty()) return null
        return if (collapsed.length <= MAX_LENGTH) collapsed else collapsed.take(MAX_LENGTH).trimEnd()
    }

    /**
     * [base] if it is free, otherwise `base (2)`, `base (3)` and so on. Comparison ignores case.
     * zh-CN: [base] 未被占用时原样返回, 否则依次尝试 `base (2)`, `base (3)` 等. 比较时忽略大小写.
     */
    fun unique(base: String, existing: Collection<String>): String {
        val taken = existing.mapTo(HashSet()) { it.lowercase() }
        if (base.lowercase() !in taken) return base
        var index = 2
        while (true) {
            val candidate = "$base ($index)"
            if (candidate.lowercase() !in taken) return candidate
            index += 1
        }
    }

}
