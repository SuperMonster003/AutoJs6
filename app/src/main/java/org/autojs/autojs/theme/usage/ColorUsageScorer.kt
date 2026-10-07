package org.autojs.autojs.theme.usage

import kotlin.math.ln
import kotlin.math.pow

/**
 * Scores how much a color is used (roadmap P5.2, D12): the logarithm of the use count, decayed by the time since the
 * last use with a half-life, plus bonuses for favorites and pinned favorites. Purely local, no network.
 * zh-CN: 计算颜色的使用评分 (Roadmap P5.2, D12): 使用次数取对数, 按距上次使用的时间以半衰期衰减, 收藏与置顶另有加分. 纯本地计算, 不联网.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object ColorUsageScorer {

    const val DEFAULT_HALF_LIFE_MILLIS = 14L * 24 * 60 * 60 * 1000

    const val FAVORITE_BONUS = 1.0

    /** Added on top of [FAVORITE_BONUS]. zh-CN: 在 [FAVORITE_BONUS] 之外另加. */
    const val PINNED_BONUS = 1.0

    /** Uses of one color; colors compare without alpha. zh-CN: 一种颜色的使用情况; 比较颜色时忽略透明度. */
    data class Usage(val color: Int, val useCount: Int, val lastUsedTime: Long)

    data class Scored(val color: Int, val useCount: Int, val lastUsedTime: Long, val score: Double)

    fun score(
        usage: Usage,
        now: Long,
        favorite: Boolean = false,
        pinned: Boolean = false,
        halfLifeMillis: Long = DEFAULT_HALF_LIFE_MILLIS,
    ): Double {
        val age = (now - usage.lastUsedTime).coerceAtLeast(0L).toDouble()
        val decay = 0.5.pow(age / halfLifeMillis)
        val bonus = (if (favorite || pinned) FAVORITE_BONUS else 0.0) + (if (pinned) PINNED_BONUS else 0.0)
        return ln(1.0 + usage.useCount.coerceAtLeast(1)) * decay + bonus
    }

    /**
     * Merges the records of the same opaque color: counts add up and the latest use wins. The first appearance
     * decides the position among the results.
     * zh-CN: 合并同一不透明颜色的记录: 次数相加, 取最近一次使用时间. 结果中的位置由首次出现决定.
     */
    fun aggregate(usages: List<Usage>): List<Usage> {
        val merged = LinkedHashMap<Int, Usage>()
        usages.forEach { usage ->
            val color = opaque(usage.color)
            merged[color] = merged[color]?.let {
                Usage(color, it.useCount + usage.useCount.coerceAtLeast(1), maxOf(it.lastUsedTime, usage.lastUsedTime))
            } ?: Usage(color, usage.useCount.coerceAtLeast(1), usage.lastUsedTime)
        }
        return merged.values.toList()
    }

    /**
     * Aggregated usages from the highest score down. Ties fall back to the later use, then to the color value, so
     * the order never depends on the input order.
     * zh-CN: 合并后的使用情况, 按评分从高到低排列. 评分相同时依次按最近使用时间与颜色值排序, 因此顺序与输入顺序无关.
     *
     * @param favorites pinned state by opaque color, for colors that are favorites.
     * zh-CN: 收藏颜色 (不透明) 到其是否置顶的映射.
     */
    fun rank(
        usages: List<Usage>,
        now: Long,
        favorites: Map<Int, Boolean> = emptyMap(),
        halfLifeMillis: Long = DEFAULT_HALF_LIFE_MILLIS,
    ): List<Scored> {
        return aggregate(usages)
            .map { usage ->
                val pinned = favorites[usage.color]
                Scored(usage.color, usage.useCount, usage.lastUsedTime, score(usage, now, pinned != null, pinned == true, halfLifeMillis))
            }
            .sortedWith(compareByDescending<Scored> { it.score }.thenByDescending { it.lastUsedTime }.thenBy { it.color.toUInt() })
    }

    fun opaque(color: Int) = color or -0x1000000

}
