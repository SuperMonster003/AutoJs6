package org.autojs.autojs.theme.color

/**
 * Dominant colors of a set of pixels (roadmap P6.1, `colors.extract`): Xiaolin Wu's quantizer on a 32 x 32 x 32 RGB
 * histogram picks the initial colors, then a weighted k-means in OKLab refines them. Pixels with alpha below 128 are
 * ignored. Pure and deterministic.
 * zh-CN: 一组像素的主要颜色 (Roadmap P6.1, `colors.extract`): 先在 32 x 32 x 32 的 RGB 直方图上用 Wu 量化选出初始颜色,
 * 再在 OKLab 中以加权 k-means 精修. 忽略 alpha 低于 128 的像素. 纯函数, 结果确定.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object ColorQuantizer {

    /** A dominant color and the number of pixels it stands for. zh-CN: 主要颜色及其代表的像素数. */
    data class Swatch(val color: Int, val population: Int)

    const val MAX_COLORS = 16

    private const val KMEANS_ITERATIONS = 10
    private const val OPAQUE = 0xFF shl 24

    /**
     * Up to [maxColors] swatches, the most frequent first; fewer when the pixels hold fewer distinct colors.
     * zh-CN: 至多 [maxColors] 个色样, 像素数多者在前; 像素中的不同颜色较少时返回更少.
     */
    fun quantize(pixels: IntArray, maxColors: Int): List<Swatch> {
        require(maxColors in 1..MAX_COLORS) { "maxColors $maxColors is out of range 1..$MAX_COLORS" }
        val counts = LinkedHashMap<Int, Int>()
        for (pixel in pixels) {
            if ((pixel ushr 24) < 0x80) continue
            val color = pixel or OPAQUE
            counts[color] = (counts[color] ?: 0) + 1
        }
        if (counts.isEmpty()) return emptyList()
        val initial = Wu(counts).quantize(maxColors)
        return refine(counts, initial)
    }

    /**
     * Weighted k-means in OKLab over the distinct colors, starting from [initial]; empty clusters are dropped and
     * clusters that end on the same color are merged.
     * zh-CN: 以 [initial] 为起点, 在 OKLab 中对不同颜色做加权 k-means; 丢弃空簇, 合并结果颜色相同的簇.
     */
    private fun refine(counts: Map<Int, Int>, initial: List<Int>): List<Swatch> {
        val colors = counts.keys.toIntArray()
        val weights = colors.map { counts.getValue(it) }.toIntArray()
        val labs = colors.map { OkColor.toOkLab(it) }
        val centroids = initial.distinct().map { OkColor.toOkLab(it) }.toMutableList()
        val assignment = IntArray(colors.size) { -1 }
        for (iteration in 0 until KMEANS_ITERATIONS) {
            var changed = false
            labs.forEachIndexed { index, lab ->
                var nearest = 0
                var best = Double.MAX_VALUE
                centroids.forEachIndexed { c, centroid ->
                    val dl = lab.l - centroid.l
                    val da = lab.a - centroid.a
                    val db = lab.b - centroid.b
                    val distance = dl * dl + da * da + db * db
                    if (distance < best) {
                        best = distance
                        nearest = c
                    }
                }
                if (assignment[index] != nearest) {
                    assignment[index] = nearest
                    changed = true
                }
            }
            if (!changed) break
            centroids.indices.forEach { c ->
                var l = 0.0
                var a = 0.0
                var b = 0.0
                var total = 0L
                labs.forEachIndexed { index, lab ->
                    if (assignment[index] == c) {
                        l += lab.l * weights[index]
                        a += lab.a * weights[index]
                        b += lab.b * weights[index]
                        total += weights[index]
                    }
                }
                if (total > 0) centroids[c] = OkLab(l / total, a / total, b / total)
            }
        }
        val populations = LinkedHashMap<Int, Int>()
        centroids.indices.forEach { c ->
            val population = weights.indices.sumOf { if (assignment[it] == c) weights[it] else 0 }
            if (population > 0) {
                val color = OkColor.fromOkLab(centroids[c])
                populations[color] = (populations[color] ?: 0) + population
            }
        }
        return populations.map { (color, population) -> Swatch(color, population) }
            .sortedWith(compareByDescending<Swatch> { it.population }.thenBy { it.color.toUInt() })
    }

    /**
     * Xiaolin Wu's greedy orthogonal bipartition of the RGB cube ("Efficient Statistical Computations for Optimal
     * Color Quantization", Graphics Gems II, 1991), on a 5-bit histogram with cumulative moments.
     * zh-CN: Xiaolin Wu 的 RGB 立方体贪心正交二分 (Graphics Gems II, 1991), 基于 5 位直方图与累积矩.
     */
    private class Wu(counts: Map<Int, Int>) {

        private val weights = LongArray(TOTAL_SIZE)
        private val momentsR = LongArray(TOTAL_SIZE)
        private val momentsG = LongArray(TOTAL_SIZE)
        private val momentsB = LongArray(TOTAL_SIZE)
        private val moments = DoubleArray(TOTAL_SIZE)

        private class Box(var r0: Int = 0, var r1: Int = 0, var g0: Int = 0, var g1: Int = 0, var b0: Int = 0, var b1: Int = 0, var volume: Int = 0)

        private enum class Direction { RED, GREEN, BLUE }

        init {
            counts.forEach { (color, count) ->
                val red = color shr 16 and 0xFF
                val green = color shr 8 and 0xFF
                val blue = color and 0xFF
                val index = index((red shr BITS_TO_REMOVE) + 1, (green shr BITS_TO_REMOVE) + 1, (blue shr BITS_TO_REMOVE) + 1)
                weights[index] += count.toLong()
                momentsR[index] += red.toLong() * count
                momentsG[index] += green.toLong() * count
                momentsB[index] += blue.toLong() * count
                moments[index] += count.toDouble() * (red * red + green * green + blue * blue)
            }
            accumulate()
        }

        fun quantize(maxColors: Int): List<Int> {
            val boxes = Array(maxColors) { Box() }
            boxes[0].apply {
                r1 = SIDE_LENGTH - 1
                g1 = SIDE_LENGTH - 1
                b1 = SIDE_LENGTH - 1
            }
            val variances = DoubleArray(maxColors)
            var count = maxColors
            var next = 0
            var i = 1
            while (i < maxColors) {
                if (cut(boxes[next], boxes[i])) {
                    variances[next] = if (boxes[next].volume > 1) variance(boxes[next]) else 0.0
                    variances[i] = if (boxes[i].volume > 1) variance(boxes[i]) else 0.0
                } else {
                    variances[next] = 0.0
                    i--
                }
                next = 0
                var largest = variances[0]
                for (j in 1..i) {
                    if (variances[j] > largest) {
                        largest = variances[j]
                        next = j
                    }
                }
                if (largest <= 0.0) {
                    count = i + 1
                    break
                }
                i++
            }
            return (0 until count).mapNotNull { index ->
                val box = boxes[index]
                val weight = volume(box, weights)
                if (weight <= 0) return@mapNotNull null
                val r = (volume(box, momentsR) / weight).toInt()
                val g = (volume(box, momentsG) / weight).toInt()
                val b = (volume(box, momentsB) / weight).toInt()
                OPAQUE or (r shl 16) or (g shl 8) or b
            }
        }

        private fun accumulate() {
            for (r in 1 until SIDE_LENGTH) {
                val area = LongArray(SIDE_LENGTH)
                val areaR = LongArray(SIDE_LENGTH)
                val areaG = LongArray(SIDE_LENGTH)
                val areaB = LongArray(SIDE_LENGTH)
                val area2 = DoubleArray(SIDE_LENGTH)
                for (g in 1 until SIDE_LENGTH) {
                    var line = 0L
                    var lineR = 0L
                    var lineG = 0L
                    var lineB = 0L
                    var line2 = 0.0
                    for (b in 1 until SIDE_LENGTH) {
                        val index = index(r, g, b)
                        line += weights[index]
                        lineR += momentsR[index]
                        lineG += momentsG[index]
                        lineB += momentsB[index]
                        line2 += moments[index]
                        area[b] += line
                        areaR[b] += lineR
                        areaG[b] += lineG
                        areaB[b] += lineB
                        area2[b] += line2
                        val previous = index(r - 1, g, b)
                        weights[index] = weights[previous] + area[b]
                        momentsR[index] = momentsR[previous] + areaR[b]
                        momentsG[index] = momentsG[previous] + areaG[b]
                        momentsB[index] = momentsB[previous] + areaB[b]
                        moments[index] = moments[previous] + area2[b]
                    }
                }
            }
        }

        private fun variance(box: Box): Double {
            val dr = volume(box, momentsR).toDouble()
            val dg = volume(box, momentsG).toDouble()
            val db = volume(box, momentsB).toDouble()
            val xx = moments[index(box.r1, box.g1, box.b1)] - moments[index(box.r1, box.g1, box.b0)] -
                    moments[index(box.r1, box.g0, box.b1)] + moments[index(box.r1, box.g0, box.b0)] -
                    moments[index(box.r0, box.g1, box.b1)] + moments[index(box.r0, box.g1, box.b0)] +
                    moments[index(box.r0, box.g0, box.b1)] - moments[index(box.r0, box.g0, box.b0)]
            return xx - (dr * dr + dg * dg + db * db) / volume(box, weights)
        }

        private fun cut(one: Box, two: Box): Boolean {
            val wholeR = volume(one, momentsR)
            val wholeG = volume(one, momentsG)
            val wholeB = volume(one, momentsB)
            val wholeW = volume(one, weights)
            val red = maximize(one, Direction.RED, one.r0 + 1, one.r1, wholeR, wholeG, wholeB, wholeW)
            val green = maximize(one, Direction.GREEN, one.g0 + 1, one.g1, wholeR, wholeG, wholeB, wholeW)
            val blue = maximize(one, Direction.BLUE, one.b0 + 1, one.b1, wholeR, wholeG, wholeB, wholeW)
            val direction = when {
                red.second >= green.second && red.second >= blue.second -> {
                    if (red.first < 0) return false
                    Direction.RED
                }
                green.second >= red.second && green.second >= blue.second -> Direction.GREEN
                else -> Direction.BLUE
            }
            two.r1 = one.r1
            two.g1 = one.g1
            two.b1 = one.b1
            when (direction) {
                Direction.RED -> {
                    one.r1 = red.first
                    two.r0 = one.r1
                    two.g0 = one.g0
                    two.b0 = one.b0
                }
                Direction.GREEN -> {
                    one.g1 = green.first
                    two.r0 = one.r0
                    two.g0 = one.g1
                    two.b0 = one.b0
                }
                Direction.BLUE -> {
                    one.b1 = blue.first
                    two.r0 = one.r0
                    two.g0 = one.g0
                    two.b0 = one.b1
                }
            }
            one.volume = (one.r1 - one.r0) * (one.g1 - one.g0) * (one.b1 - one.b0)
            two.volume = (two.r1 - two.r0) * (two.g1 - two.g0) * (two.b1 - two.b0)
            return true
        }

        /** The best cut position along [direction] and its score; -1 when no cut splits the weight. zh-CN: 沿 [direction] 的最佳切分位置及其得分; 无法切分时为 -1. */
        private fun maximize(box: Box, direction: Direction, first: Int, last: Int, wholeR: Long, wholeG: Long, wholeB: Long, wholeW: Long): Pair<Int, Double> {
            val bottomR = bottom(box, direction, momentsR)
            val bottomG = bottom(box, direction, momentsG)
            val bottomB = bottom(box, direction, momentsB)
            val bottomW = bottom(box, direction, weights)
            var max = 0.0
            var cut = -1
            for (i in first until last) {
                var halfR = bottomR + top(box, direction, i, momentsR)
                var halfG = bottomG + top(box, direction, i, momentsG)
                var halfB = bottomB + top(box, direction, i, momentsB)
                var halfW = bottomW + top(box, direction, i, weights)
                if (halfW == 0L) continue
                var score = (halfR.toDouble() * halfR + halfG.toDouble() * halfG + halfB.toDouble() * halfB) / halfW
                halfR = wholeR - halfR
                halfG = wholeG - halfG
                halfB = wholeB - halfB
                halfW = wholeW - halfW
                if (halfW == 0L) continue
                score += (halfR.toDouble() * halfR + halfG.toDouble() * halfG + halfB.toDouble() * halfB) / halfW
                if (score > max) {
                    max = score
                    cut = i
                }
            }
            return cut to max
        }

        private fun volume(box: Box, moment: LongArray): Long =
            moment[index(box.r1, box.g1, box.b1)] - moment[index(box.r1, box.g1, box.b0)] -
                    moment[index(box.r1, box.g0, box.b1)] + moment[index(box.r1, box.g0, box.b0)] -
                    moment[index(box.r0, box.g1, box.b1)] + moment[index(box.r0, box.g1, box.b0)] +
                    moment[index(box.r0, box.g0, box.b1)] - moment[index(box.r0, box.g0, box.b0)]

        private fun bottom(box: Box, direction: Direction, moment: LongArray): Long = when (direction) {
            Direction.RED -> -moment[index(box.r0, box.g1, box.b1)] + moment[index(box.r0, box.g1, box.b0)] +
                    moment[index(box.r0, box.g0, box.b1)] - moment[index(box.r0, box.g0, box.b0)]
            Direction.GREEN -> -moment[index(box.r1, box.g0, box.b1)] + moment[index(box.r1, box.g0, box.b0)] +
                    moment[index(box.r0, box.g0, box.b1)] - moment[index(box.r0, box.g0, box.b0)]
            Direction.BLUE -> -moment[index(box.r1, box.g1, box.b0)] + moment[index(box.r1, box.g0, box.b0)] +
                    moment[index(box.r0, box.g1, box.b0)] - moment[index(box.r0, box.g0, box.b0)]
        }

        private fun top(box: Box, direction: Direction, position: Int, moment: LongArray): Long = when (direction) {
            Direction.RED -> moment[index(position, box.g1, box.b1)] - moment[index(position, box.g1, box.b0)] -
                    moment[index(position, box.g0, box.b1)] + moment[index(position, box.g0, box.b0)]
            Direction.GREEN -> moment[index(box.r1, position, box.b1)] - moment[index(box.r1, position, box.b0)] -
                    moment[index(box.r0, position, box.b1)] + moment[index(box.r0, position, box.b0)]
            Direction.BLUE -> moment[index(box.r1, box.g1, position)] - moment[index(box.r1, box.g0, position)] -
                    moment[index(box.r0, box.g1, position)] + moment[index(box.r0, box.g0, position)]
        }

        companion object {
            private const val INDEX_BITS = 5
            private const val BITS_TO_REMOVE = 8 - INDEX_BITS
            private const val SIDE_LENGTH = (1 shl INDEX_BITS) + 1
            private const val TOTAL_SIZE = SIDE_LENGTH * SIDE_LENGTH * SIDE_LENGTH

            private fun index(r: Int, g: Int, b: Int) = r * SIDE_LENGTH * SIDE_LENGTH + g * SIDE_LENGTH + b
        }

    }

}
