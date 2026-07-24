package org.autojs.autojs.runtime.api.augment.images

import org.autojs.autojs.core.image.TemplateMatching
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray

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
        return comparator?.let { matchedList.minWithOrNull(it) }
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

    }

}
