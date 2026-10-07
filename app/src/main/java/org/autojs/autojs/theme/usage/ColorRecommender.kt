package org.autojs.autojs.theme.usage

import org.autojs.autojs.theme.color.OkColor

/**
 * A favorite color as the pure objects see it. zh-CN: 纯对象所见的收藏颜色.
 */
data class FavoriteEntry(val id: Long, val color: Int, val pinned: Boolean, val position: Int, val createdTime: Long)

/**
 * One use record of a color; [key] tells the caller how to apply it again (a library item, a palette color...).
 * zh-CN: 颜色的一条使用记录; [key] 告诉调用方如何再次应用它 (颜色库条目, 调色板颜色等).
 */
data class HistoryEntry<out K>(val key: K, val color: Int, val useCount: Int, val lastUsedTime: Long)

sealed interface Recommendation<out K> {

    val color: Int

    data class Favorite(val entry: FavoriteEntry) : Recommendation<Nothing> {
        override val color get() = entry.color
    }

    data class Used<out K>(val key: K, override val color: Int, val score: Double) : Recommendation<K>

}

/**
 * "Frequent and recommended" (roadmap P5.4): pinned favorites, the other favorites, then used colors by score, with
 * similar colors merged into the earlier entry; favorites are never merged away.
 * zh-CN: "常用与推荐" (Roadmap P5.4): 置顶收藏, 其余收藏, 然后按评分排列的已用颜色; 相似颜色并入排在前面的条目, 收藏永远不会被并掉.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object ColorRecommender {

    const val MAX_ITEMS = 30

    fun <K> recommend(
        favorites: List<FavoriteEntry>,
        history: List<HistoryEntry<K>>,
        now: Long,
        halfLifeMillis: Long = ColorUsageScorer.DEFAULT_HALF_LIFE_MILLIS,
        threshold: Double = ColorClusterer.DEFAULT_THRESHOLD,
        maxItems: Int = MAX_ITEMS,
    ): List<Recommendation<K>> {
        val pinnedByColor = favorites.associate { ColorUsageScorer.opaque(it.color) to it.pinned }
        // The latest record of a color decides how it is applied. zh-CN: 颜色的最近一条记录决定其应用方式.
        val keyByColor = history.groupBy { ColorUsageScorer.opaque(it.color) }.mapValues { (_, entries) -> entries.maxBy { it.lastUsedTime }.key }
        val usages = history.map { ColorUsageScorer.Usage(it.color, it.useCount, it.lastUsedTime) }
        val used = ColorUsageScorer.rank(usages, now, pinnedByColor, halfLifeMillis).map { Recommendation.Used(keyByColor.getValue(it.color), it.color, it.score) }
        val ordered: List<Recommendation<K>> = favorites.filter { it.pinned }.map { Recommendation.Favorite(it) } +
                favorites.filterNot { it.pinned }.map { Recommendation.Favorite(it) } +
                used
        return ColorClusterer.cluster(ordered, { it.color }, threshold, isAnchor = { it is Recommendation.Favorite })
            .map { it.representative }
            .take(maxItems)
    }

}

/**
 * Orders of the favorites page (roadmap P5.1); pinned favorites always come first.
 * zh-CN: 收藏页面的排序方式 (Roadmap P5.1); 置顶的收藏总是排在最前.
 */
enum class FavoriteSort(val key: String) {

    /** By the dragged position. zh-CN: 按拖动后的位置. */
    MANUAL("manual"),

    /** Newest favorite first. zh-CN: 最新收藏在前. */
    TIME("time"),

    /** By OKLCH hue; near-grays last, light to dark. zh-CN: 按 OKLCH 色相; 近灰色排在最后, 由浅到深. */
    HUE("hue"),

    /** Light to dark by OKLCH lightness. zh-CN: 按 OKLCH 亮度由浅到深. */
    LIGHTNESS("lightness");

    fun sort(favorites: List<FavoriteEntry>): List<FavoriteEntry> {
        val comparator: Comparator<FavoriteEntry> = when (this) {
            MANUAL -> compareBy<FavoriteEntry> { it.position }.thenBy { it.id }
            TIME -> compareByDescending<FavoriteEntry> { it.createdTime }.thenByDescending { it.id }
            HUE -> compareBy<FavoriteEntry> { isNearGray(it.color) }
                .thenBy { if (isNearGray(it.color)) 0.0 else OkColor.toOkLch(it.color).h }
                .thenByDescending { OkColor.toOkLch(it.color).l }
                .thenBy { it.id }
            LIGHTNESS -> compareByDescending<FavoriteEntry> { OkColor.toOkLch(it.color).l }.thenBy { it.id }
        }
        return favorites.sortedWith(compareByDescending<FavoriteEntry> { it.pinned }.then(comparator))
    }

    companion object {

        /** Below this OKLCH chroma the hue is not meaningful. zh-CN: OKLCH 彩度低于此值时色相没有意义. */
        const val NEAR_GRAY_CHROMA = 0.02

        fun isNearGray(color: Int) = OkColor.toOkLch(color).c < NEAR_GRAY_CHROMA

        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: MANUAL

    }

}
