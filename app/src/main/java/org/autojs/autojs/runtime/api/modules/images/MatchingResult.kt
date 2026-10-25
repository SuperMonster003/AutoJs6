package org.autojs.autojs.runtime.api.modules.images

import org.autojs.autojs.core.image.TemplateMatching
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.mozilla.javascript.Context

/**
 * Result of `images.matchTemplate()`: an immutable, ordered list of template matches with selectors and sorting helpers.
 * zh-CN: `images.matchTemplate()` 的返回结果: 不可变的有序匹配列表, 附带选择与排序辅助方法.
 */
class MatchingResult(private val matchedList: List<TemplateMatching.Match>) {

    @JvmField
    val matches = matchedList.toNativeArray()

    @JvmField
    val points = matchedList.map { it.point }.toNativeArray()

    /** Number of matches. zh-CN: 匹配数量. */
    @JvmField
    val size = matchedList.size

    fun isEmpty() = matchedList.isEmpty()
    fun isNotEmpty() = matchedList.isNotEmpty()

    fun first() = matchedList.firstOrNull()
    fun last() = matchedList.lastOrNull()
    fun leftmost() = findMax(comparators["left"])
    fun rightmost() = findMax(comparators["right"])
    fun topmost() = findMax(comparators["top"])
    fun bottommost() = findMax(comparators["bottom"])
    fun best() = findMax(comparators["best"])
    fun worst() = findMax(comparators["worst"])

    fun findMax(comparator: Comparator<TemplateMatching.Match>?): TemplateMatching.Match? {
        return comparator?.let { matchedList.minWithOrNull(it) }
    }

    /**
     * Returns a new result holding the matches for which the predicate is truthy (JS semantics).
     * zh-CN: 返回仅包含使谓词为真值 (JS 语义) 的匹配的新结果.
     */
    fun filter(predicate: (TemplateMatching.Match) -> Any?): MatchingResult {
        return MatchingResult(matchedList.filter { Context.toBoolean(predicate(it)) })
    }

    /**
     * Sorts with a comparator that may return any JS number (e.g. `a.similarity - b.similarity`); only its sign matters.
     * zh-CN: 使用比较函数排序, 该函数可返回任意 JS 数字 (如 `a.similarity - b.similarity`), 仅符号有效.
     */
    fun sortBy(compareFn: (TemplateMatching.Match, TemplateMatching.Match) -> Any?): MatchingResult {
        return MatchingResult(matchedList.sortedWith { l, r -> toCompareResult(compareFn(l, r)) })
    }

    fun sortBy(direction: String): MatchingResult {
        var comparator: ((TemplateMatching.Match, TemplateMatching.Match) -> Int)? = null

        direction.split("-").forEach { s ->
            val tmpComparator = comparators[s] ?: throw Exception("Unknown direction '$s' in '$direction'")
            comparator = when (comparator) {
                null -> tmpComparator
                else -> {
                    val previousComparator = requireNotNull(comparator)
                    val combinedComparator: (TemplateMatching.Match, TemplateMatching.Match) -> Int = { l, r ->
                        val cmpValue = previousComparator(l, r)
                        if (cmpValue == 0) tmpComparator(l, r) else cmpValue
                    }
                    combinedComparator
                }
            }
        }

        return MatchingResult(comparator?.let { matchedList.sortedWith(it) } ?: matchedList)
    }

    companion object {

        private val comparators = mapOf<String, (TemplateMatching.Match, TemplateMatching.Match) -> Int>(
            "left" to { l, r -> l.point.x.compareTo(r.point.x) },
            "right" to { l, r -> r.point.x.compareTo(l.point.x) },
            "top" to { t, b -> t.point.y.compareTo(b.point.y) },
            "bottom" to { t, b -> b.point.y.compareTo(t.point.y) },
            "best" to { a, b -> b.similarity.compareTo(a.similarity) },
            "worst" to { a, b -> a.similarity.compareTo(b.similarity) },
        )

        /** Coerces a JS comparator result to -1, 0 or 1; NaN and non-numeric values count as equal. */
        private fun toCompareResult(value: Any?): Int {
            val number = when (value) {
                is Number -> value.toDouble()
                else -> Context.toNumber(value)
            }
            return when {
                number.isNaN() -> 0
                number < 0 -> -1
                number > 0 -> 1
                else -> 0
            }
        }

    }

}
