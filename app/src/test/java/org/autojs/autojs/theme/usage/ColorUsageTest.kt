package org.autojs.autojs.theme.usage

import org.autojs.autojs.theme.color.ColorMetrics
import org.autojs.autojs.theme.usage.ColorUsageScorer.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class ColorUsageTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L
    private val red = 0xFFF44336.toInt()
    private val yellow = 0xFFFFEB3B.toInt()
    private val green = 0xFF4CAF50.toInt()
    private val blue = 0xFF2196F3.toInt()
    private val gray = 0xFF9E9E9E.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun scoresDecayByHalfLifeAndAddBonuses() {
        val fresh = ColorUsageScorer.score(Usage(red, 3, now), now)
        assertEquals(ln(4.0), fresh, 1e-12)
        assertEquals(fresh / 2, ColorUsageScorer.score(Usage(red, 3, now - 14 * day), now), 1e-12)
        assertEquals(fresh / 4, ColorUsageScorer.score(Usage(red, 3, now - 28 * day), now), 1e-12)
        // A future time (clock change) never boosts the score. zh-CN: 未来的时间 (时钟变化) 不会提高评分.
        assertEquals(fresh, ColorUsageScorer.score(Usage(red, 3, now + day), now), 1e-12)
        assertEquals(fresh + ColorUsageScorer.FAVORITE_BONUS, ColorUsageScorer.score(Usage(red, 3, now), now, favorite = true), 1e-12)
        assertEquals(
            fresh + ColorUsageScorer.FAVORITE_BONUS + ColorUsageScorer.PINNED_BONUS,
            ColorUsageScorer.score(Usage(red, 3, now), now, favorite = true, pinned = true), 1e-12,
        )
        // More uses beat fewer at the same age; a recent single use beats old frequent use eventually.
        // zh-CN: 同样新近时次数多者在前; 足够久远的高频使用最终低于最近的一次使用.
        assertTrue(ColorUsageScorer.score(Usage(red, 9, now), now) > ColorUsageScorer.score(Usage(red, 2, now), now))
        assertTrue(ColorUsageScorer.score(Usage(red, 1, now), now) > ColorUsageScorer.score(Usage(red, 20, now - 60 * day), now))
    }

    @Test
    fun aggregationMergesTheSameOpaqueColor() {
        val merged = ColorUsageScorer.aggregate(
            listOf(Usage(red, 2, now - day), Usage(blue, 1, now), Usage(red and 0x80FFFFFF.toInt(), 3, now - 2 * day)),
        )
        assertEquals(listOf(Usage(red, 5, now - day), Usage(blue, 1, now)), merged)
    }

    @Test
    fun rankingIsStableForAnyInputOrder() {
        val usages = listOf(
            Usage(red, 2, now - day),
            Usage(blue, 2, now - day),
            Usage(green, 2, now),
            Usage(yellow, 5, now - 3 * day),
            Usage(gray, 1, now - 40 * day),
        )
        val expected = ColorUsageScorer.rank(usages, now).map { it.color }
        // Equal scores: the later use first, then the color value. zh-CN: 评分相同时: 最近使用者在前, 其次按颜色值.
        assertEquals(listOf(yellow, green, blue, red, gray), expected)
        listOf(usages.reversed(), usages.shuffled(java.util.Random(1)), usages.shuffled(java.util.Random(2))).forEach {
            assertEquals(expected, ColorUsageScorer.rank(it, now).map { scored -> scored.color })
        }
        // A favorite bonus lifts an old color. zh-CN: 收藏加分可以抬升久远的颜色.
        assertEquals(gray, ColorUsageScorer.rank(usages, now, favorites = mapOf(gray to true)).first().color)
    }

    @Test
    fun clusteringRespectsTheThresholdBoundary() {
        val first = 0xFF2196F3.toInt()
        val near = 0xFF2398F0.toInt()
        val distance = ColorMetrics.deltaEOk(first, near)
        // Strictly closer than the threshold merges; exactly at it does not. zh-CN: 严格小于阈值才合并; 恰好等于时不合并.
        assertEquals(1, ColorClusterer.cluster(listOf(first, near), { it }, threshold = distance + 1e-9).size)
        assertEquals(2, ColorClusterer.cluster(listOf(first, near), { it }, threshold = distance).size)
        val clusters = ColorClusterer.cluster(listOf(near, first, red), { it }, threshold = ColorClusterer.DEFAULT_THRESHOLD)
        assertEquals(listOf(near, red), clusters.map { it.representative })
        assertEquals(listOf(near, first), clusters.first().members)
        assertEquals(1, clusters.first().merged)
        // Anchors keep their own cluster but may take later members. zh-CN: 锚点总是独立成簇, 但之后的条目可以并入.
        val anchored = ColorClusterer.cluster(listOf(first, near, near), { it }, isAnchor = { it == near })
        assertEquals(listOf(first, near, near), anchored.map { it.representative })
    }

    @Test
    fun recommendationsPutFavoritesFirstAndMergeSimilarColors() {
        val favorites = listOf(
            FavoriteEntry(1, green, pinned = false, position = 0, createdTime = 1),
            FavoriteEntry(2, blue, pinned = true, position = 1, createdTime = 2),
        )
        val history = listOf(
            HistoryEntry("material:red", red, 5, now - day),
            HistoryEntry("palette:red", red, 1, now),
            HistoryEntry("css:nearBlue", 0xFF2397F2.toInt(), 9, now),
            HistoryEntry("web:yellow", yellow, 1, now - 2 * day),
        )
        val result = ColorRecommender.recommend(favorites, history, now)
        assertEquals(listOf(blue, green, red, yellow), result.map { it.color })
        // The latest record of a color decides its key. zh-CN: 颜色的最近一条记录决定其 key.
        assertEquals("palette:red", (result[2] as Recommendation.Used).key)
        assertTrue(ColorRecommender.recommend(emptyList(), emptyList<HistoryEntry<String>>(), now).isEmpty())
        val many = (0 until 60).map { HistoryEntry(it, 0xFF000000.toInt() or (it * 0x040404 + 0x101010), 1, now - it) }
        assertEquals(ColorRecommender.MAX_ITEMS, ColorRecommender.recommend(emptyList(), many, now, threshold = 0.0).size)
    }

    @Test
    fun favoriteSortsKeepPinnedFirst() {
        val favorites = listOf(
            FavoriteEntry(1, blue, pinned = false, position = 2, createdTime = 30),
            FavoriteEntry(2, gray, pinned = false, position = 0, createdTime = 10),
            FavoriteEntry(3, red, pinned = false, position = 3, createdTime = 20),
            FavoriteEntry(4, yellow, pinned = true, position = 4, createdTime = 5),
            FavoriteEntry(5, white, pinned = false, position = 1, createdTime = 40),
        )
        assertEquals(listOf(4L, 2, 5, 1, 3), FavoriteSort.MANUAL.sort(favorites).map { it.id })
        assertEquals(listOf(4L, 5, 1, 3, 2), FavoriteSort.TIME.sort(favorites).map { it.id })
        // Red, blue by hue; grays (white, gray) last from light to dark. zh-CN: 红, 蓝按色相; 灰色 (白, 灰) 最后, 由浅到深.
        assertEquals(listOf(4L, 3, 1, 5, 2), FavoriteSort.HUE.sort(favorites).map { it.id })
        // OKLCH lightness: white 1.0, gray 0.70, blue 0.65, red 0.64. zh-CN: OKLCH 亮度: 白 1.0, 灰 0.70, 蓝 0.65, 红 0.64.
        assertEquals(listOf(4L, 5, 2, 1, 3), FavoriteSort.LIGHTNESS.sort(favorites).map { it.id })
        assertEquals(FavoriteSort.MANUAL, FavoriteSort.of("unknown"))
        assertEquals(FavoriteSort.HUE, FavoriteSort.of("hue"))
    }

}
