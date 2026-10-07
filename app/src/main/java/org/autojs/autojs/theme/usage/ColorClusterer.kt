package org.autojs.autojs.theme.usage

import org.autojs.autojs.theme.color.ColorMetrics

/**
 * Greedy clustering of similar colors by dE_ok (roadmap P5.3). Items come in priority order: each one joins the first
 * cluster whose representative is closer than the threshold, otherwise it starts a new cluster and represents it, so
 * a representative is always the highest priority item of its cluster.
 * zh-CN: 按 dE_ok 对相似颜色贪心聚类 (Roadmap P5.3). 条目按优先级顺序给出: 每个条目加入第一个代表色与其距离小于阈值的簇,
 * 否则新建一个簇并作为其代表, 因此代表总是簇内优先级最高的条目.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object ColorClusterer {

    /**
     * Distances below this are merged; about two just noticeable differences of OKLab.
     * zh-CN: 距离小于此值即合并; 约为 OKLab 中两个最小可觉差.
     */
    const val DEFAULT_THRESHOLD = 0.04

    data class Cluster<T>(val representative: T, val members: List<T>) {
        /** Members other than the representative. zh-CN: 代表以外的成员. */
        val merged: Int get() = members.size - 1
    }

    /**
     * @param isAnchor items that always start their own cluster (such as favorites); later items may still join them.
     * zh-CN: 总是新建簇的条目 (如收藏); 之后的条目仍可加入其簇.
     */
    fun <T> cluster(
        items: List<T>,
        colorOf: (T) -> Int,
        threshold: Double = DEFAULT_THRESHOLD,
        isAnchor: (T) -> Boolean = { false },
    ): List<Cluster<T>> {
        val representatives = mutableListOf<T>()
        val members = mutableListOf<MutableList<T>>()
        items.forEach { item ->
            val index = when {
                isAnchor(item) -> -1
                else -> representatives.indexOfFirst { ColorMetrics.deltaEOk(colorOf(it), colorOf(item)) < threshold }
            }
            when (index) {
                -1 -> {
                    representatives += item
                    members += mutableListOf(item)
                }
                else -> members[index] += item
            }
        }
        return representatives.indices.map { Cluster(representatives[it], members[it]) }
    }

}
