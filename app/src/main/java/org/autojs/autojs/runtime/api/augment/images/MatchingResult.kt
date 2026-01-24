package org.autojs.autojs.runtime.api.augment.images

import org.autojs.autojs.core.image.TemplateMatching
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import kotlin.math.roundToInt

class MatchingResult(private val matchedList: List<TemplateMatching.Match>) {

    @JvmField
    val matches = matchedList.toNativeArray()

    @JvmField
    val points = matchedList.map { it.point }.toNativeArray()

    fun first() = matchedList.firstOrNull()
    fun last() = matchedList.lastOrNull()
    fun leftmost() = findMax(comparators["left"])
    fun rightmost() = findMax(comparators["right"])
    fun topmost() = findMax(comparators["top"])
    fun bottommost() = findMax(comparators["bottom"])
    fun best() = findMax(comparators["best"])
    fun worst() = findMax(comparators["worst"])

    fun findMax(comparator: Comparator<TemplateMatching.Match>?): TemplateMatching.Match? {
        return comparator?.let { matchedList.maxWithOrNull(it) }
    }

    fun sortBy(compareFn: (TemplateMatching.Match, TemplateMatching.Match) -> Int): MatchingResult {
        return MatchingResult(matchedList.sortedWith(compareFn))
    }

    fun sortBy(direction: String): MatchingResult {
        var comparator: ((TemplateMatching.Match, TemplateMatching.Match) -> Int)? = null

        direction.split("-").forEach { s ->
            val tmpComparator = comparators[s] ?: throw Exception("Unknown direction '$s' in '$direction'")
            comparator = when (comparator) {
                null -> tmpComparator
                else -> { l, r ->
                    val cmpValue = comparator?.invoke(l, r) ?: 0
                    if (cmpValue == 0) tmpComparator(l, r) else cmpValue
                }
            }
        }

        return MatchingResult(comparator?.let { matchedList.sortedWith(it) } ?: matchedList)
    }

    companion object {

        private val comparators = mapOf<String, (TemplateMatching.Match, TemplateMatching.Match) -> Int>(
            "left" to { l, r -> (l.point.x - r.point.x).roundToInt() },
            "right" to { l, r -> (r.point.x - l.point.x).roundToInt() },
            "top" to { t, b -> (t.point.y - b.point.y).roundToInt() },
            "bottom" to { t, b -> (b.point.y - t.point.y).roundToInt() },
            "best" to { a, b -> (a.similarity - b.similarity).roundToInt() },
            "worst" to { a, b -> (b.similarity - a.similarity).roundToInt() },
        )

    }

}