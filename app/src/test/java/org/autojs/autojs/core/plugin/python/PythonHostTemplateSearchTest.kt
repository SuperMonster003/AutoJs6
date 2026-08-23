package org.autojs.autojs.core.plugin.python

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonHostTemplateSearchTest {
    @Test
    fun findsTheFirstRowMajorMatchAndTreatsNonOpaqueTemplatePixelsAsWildcards() {
        val screen = IntArray(5 * 4) { 0xff000000.toInt() }
        place(screen, 5, 2, 0)
        place(screen, 5, 0, 2)
        val source = PixelFixture(5, screen)
        val template = template()
        try {
            val match = PythonHostTemplateSearch.find(source, template, 0, null)

            assertTrue(match.found)
            assertEquals(2, match.x)
            assertEquals(0, match.y)
            assertEquals("autojs6-python-image-match-v1", match.toJson().get("schema").asString)
            assertArrayEquals(IntArray(5), source.lastDestination)
        } finally {
            template.clear()
        }
    }

    @Test
    fun honorsAbsoluteRegionOffsetsAndPerChannelThreshold() {
        val screen = IntArray(6 * 5) { 0xff000000.toInt() }
        val target = intArrayOf(
            0xff102030.toInt(),
            0xff405060.toInt(),
            0xff708090.toInt(),
            0xffa0b0c0.toInt(),
        )
        val near = target.map { pixel ->
            val red = minOf(255, (pixel ushr 16 and 0xff) + 2)
            val green = maxOf(0, (pixel ushr 8 and 0xff) - 1)
            val blue = minOf(255, (pixel and 0xff) + 1)
            (0xff shl 24) or (red shl 16) or (green shl 8) or blue
        }.toIntArray()
        copy(screen, 6, 3, 2, near, 2)
        val source = PixelFixture(6, screen)
        val template = PythonHostImageTemplate.fromDecoded(
            PythonHostDecodedImageTemplate(2, 2, target),
        )
        try {
            val match = PythonHostTemplateSearch.find(
                source,
                template,
                threshold = 2,
                region = PythonHostImageRegion(2, 1, 4, 4),
            )

            assertEquals(PythonHostImageMatch.found(3, 2), match)
            assertEquals(
                listOf(Read(2, 1, 4), Read(2, 2, 4), Read(2, 3, 4), Read(2, 4, 4)),
                source.reads,
            )
            assertArrayEquals(IntArray(4), source.lastDestination)
        } finally {
            template.clear()
        }
    }

    @Test
    fun returnsCanonicalMissAndClearsTemporaryRows() {
        val source = PixelFixture(3, IntArray(9) { 0xff000000.toInt() })
        val template = template()
        try {
            val match = PythonHostTemplateSearch.find(source, template, 0, null)

            assertFalse(match.found)
            assertEquals(PythonHostImageMatch.NOT_FOUND, match.x)
            assertEquals(PythonHostImageMatch.NOT_FOUND, match.y)
            assertArrayEquals(IntArray(3), source.lastDestination)
        } finally {
            template.clear()
        }
    }

    @Test
    fun failsClosedAtComparisonAndSearchAreaLimits() {
        val source = PixelFixture(
            width = 2,
            pixels = intArrayOf(
                0xff102030.toInt(),
                0xff102030.toInt(),
                0xff000000.toInt(),
                0xff000000.toInt(),
            ),
        )
        val template = PythonHostImageTemplate.fromDecoded(
            PythonHostDecodedImageTemplate(
                1,
                2,
                intArrayOf(0xff102030.toInt(), 0xff405060.toInt()),
            ),
        )
        try {
            assertThrows(PythonHostCapabilityResultLimitException::class.java) {
                PythonHostTemplateSearch.find(
                    source,
                    template,
                    threshold = 0,
                    region = null,
                    maximumComparisons = 1,
                )
            }
            assertArrayEquals(IntArray(2), source.lastDestination)

            assertThrows(PythonHostCapabilityResultLimitException::class.java) {
                PythonHostTemplateSearch.find(
                    InvalidPixelSource(2_048, 2_049),
                    template,
                    threshold = 0,
                    region = null,
                )
            }
        } finally {
            template.clear()
        }
    }

    @Test
    fun rejectsInvalidThresholdSourceAndTemplateRegionBounds() {
        val source = PixelFixture(2, IntArray(4) { 0xff000000.toInt() })
        val template = template()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                PythonHostTemplateSearch.find(source, template, -1, null)
            }
            assertThrows(IllegalArgumentException::class.java) {
                PythonHostTemplateSearch.find(
                    source,
                    template,
                    PythonHostImagePolicy.MAX_COLOR_THRESHOLD + 1,
                    null,
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                PythonHostTemplateSearch.find(
                    InvalidPixelSource(0, 1),
                    template,
                    0,
                    null,
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                PythonHostTemplateSearch.find(
                    source,
                    template,
                    0,
                    PythonHostImageRegion(0, 0, 1, 2),
                )
            }
            assertTrue(source.reads.isEmpty())
        } finally {
            template.clear()
        }
    }

    private fun template(): PythonHostImageTemplate = PythonHostImageTemplate.fromDecoded(
        PythonHostDecodedImageTemplate(
            width = 2,
            height = 2,
            pixels = intArrayOf(
                0x00111111,
                0xffff0000.toInt(),
                0xff00ff00.toInt(),
                0xff0000ff.toInt(),
            ),
        ),
    )

    private fun place(screen: IntArray, width: Int, x: Int, y: Int) {
        val pixels = intArrayOf(
            0xffabcdef.toInt(),
            0xffff0000.toInt(),
            0xff00ff00.toInt(),
            0xff0000ff.toInt(),
        )
        copy(screen, width, x, y, pixels, 2)
    }

    private fun copy(
        destination: IntArray,
        destinationWidth: Int,
        x: Int,
        y: Int,
        pixels: IntArray,
        sourceWidth: Int,
    ) {
        pixels.indices.forEach { index ->
            destination[(y + index / sourceWidth) * destinationWidth + x + index % sourceWidth] =
                pixels[index]
        }
    }

    private data class Read(val x: Int, val y: Int, val width: Int)

    private class PixelFixture(
        override val width: Int,
        private val pixels: IntArray,
    ) : PythonHostPixelSource {
        override val height = pixels.size / width
        val reads = mutableListOf<Read>()
        var lastDestination = IntArray(0)
            private set

        init {
            require(width > 0 && pixels.size % width == 0)
        }

        override fun readRow(x: Int, y: Int, width: Int, destination: IntArray) {
            reads += Read(x, y, width)
            lastDestination = destination
            val start = y * this.width + x
            pixels.copyInto(
                destination,
                destinationOffset = 0,
                startIndex = start,
                endIndex = start + width,
            )
        }
    }

    private class InvalidPixelSource(
        override val width: Int,
        override val height: Int,
    ) : PythonHostPixelSource {
        override fun readRow(x: Int, y: Int, width: Int, destination: IntArray) = Unit
    }
}
