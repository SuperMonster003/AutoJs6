package org.autojs.autojs.runtime.api.modules.ocr

import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max

/**
 * Merges the OCR segments of one text line, for Rhino's `OcrResult` and for the results the Node.js and Bun bridge sends
 * alike. The boxes are plain numbers, because the two sides hold different result objects.
 *
 * zh-CN: 合并同一文本行的 OCR 片段, Rhino 的 OcrResult 与 Node.js / Bun 桥发送的结果共用此实现. 文本框使用普通数字, 因为两侧的结果对象不同.
 */
internal object OcrLineMerger {

    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {

        private val height get() = bottom - top
        private val centerY get() = (top + bottom) / 2
        private val isEmpty get() = left >= right || top >= bottom

        fun isSameLine(other: Box) = abs(centerY - other.centerY) < max(height, other.height) / 2

        // Same rules as android.graphics.Rect#union: an empty box adds nothing and an empty receiver takes the other box.
        // zh-CN: 规则与 android.graphics.Rect#union 相同: 空框不起作用, 接收者为空框时取另一个框.
        fun union(other: Box) = when {
            other.isEmpty -> this
            isEmpty -> other
            else -> Box(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))
        }

        fun toRect() = Rect(left, top, right, bottom)

        companion object {
            val EMPTY = Box(0, 0, 0, 0)
        }

    }

    data class Segment<E>(val text: String, val confidence: Float, val box: Box, val extras: E)

    /**
     * Reading order: boxes on one line by their left edge, or from the right by their right edge when the text is
     * [rightToLeft], otherwise by their bottom edge.
     *
     * zh-CN: 阅读顺序: 同一行按左边界, 文本 [rightToLeft] 时按右边界从右往左, 否则按下边界.
     */
    fun compare(a: Box, b: Box, rightToLeft: Boolean = false) = when {
        !a.isSameLine(b) -> a.bottom - b.bottom
        rightToLeft -> b.right - a.right
        else -> a.left - b.left
    }

    /**
     * The [segments] in reading order, with neighbours on one line joined. The confidence of a line is weighted by the
     * text length of its segments, and [mergeExtras] combines their extras.
     *
     * zh-CN: 将 [segments] 按阅读顺序排列, 并合并相邻的同行片段. 行的置信度按片段文本长度加权, 片段的 extras 由 [mergeExtras] 合并.
     */
    fun <E> merge(segments: List<Segment<E>>, mergeExtras: (List<E>) -> E, rightToLeft: Boolean = false): List<Segment<E>> {
        if (segments.size <= 1) return segments
        val sorted = segments.sortedWith { a, b -> compare(a.box, b.box, rightToLeft) }
        val merged = ArrayList<Segment<E>>(sorted.size)

        var text = StringBuilder()
        var box = Box.EMPTY
        var weightSum = 0
        var confidenceSum = 0f
        var extrasParts = ArrayList<E>()
        var last = sorted.first()

        fun add(segment: Segment<E>) {
            box = if (text.isEmpty()) segment.box else box.union(segment.box)
            text.append(segment.text)
            val weight = max(1, segment.text.length)
            weightSum += weight
            confidenceSum += segment.confidence * weight
            extrasParts.add(segment.extras)
            last = segment
        }

        fun flush() {
            if (text.isNotEmpty()) {
                val confidence = if (weightSum > 0) confidenceSum / weightSum else 0f
                merged.add(Segment(text.toString(), confidence, box, mergeExtras(extrasParts)))
            }
            text = StringBuilder()
            weightSum = 0
            confidenceSum = 0f
            extrasParts = ArrayList()
        }

        add(last)
        for (i in 1 until sorted.size) {
            val segment = sorted[i]
            if (!last.box.isSameLine(segment.box)) flush()
            add(segment)
        }
        flush()
        return merged
    }

}

internal fun Rect.toOcrBox() = OcrLineMerger.Box(left, top, right, bottom)
