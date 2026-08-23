package org.autojs.autojs.core.plugin.python

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonHostColorSearchTest {
    @Test
    fun findsTheFirstRowMajorRgbMatchAndIgnoresAlpha() {
        val source = PixelFixture(
            width = 4,
            pixels = intArrayOf(
                0xff000000.toInt(),
                0x00123456,
                0xff123456.toInt(),
                0xffffffff.toInt(),
                0xff123456.toInt(),
                0xff000000.toInt(),
                0xff000000.toInt(),
                0xff000000.toInt(),
            ),
        )

        val match = PythonHostColorSearch.find(source, 0x123456, 0, null)

        assertTrue(match.found)
        assertEquals(1, match.x)
        assertEquals(0, match.y)
        assertEquals(listOf(Read(0, 0, 4)), source.reads)
        assertArrayEquals(IntArray(4), source.lastDestination)
        assertEquals("autojs6-python-color-match-v1", match.toJson().get("schema").asString)
    }

    @Test
    fun honorsRegionOffsetsAndPerChannelThreshold() {
        val pixels = IntArray(20) { 0xff000000.toInt() }
        pixels[0] = 0xff102030.toInt()
        pixels[2 * 5 + 3] = 0xff11222f.toInt()
        val source = PixelFixture(width = 5, pixels = pixels)

        val match = PythonHostColorSearch.find(
            source = source,
            color = 0x102030,
            threshold = 2,
            region = PythonHostImageRegion(2, 1, 3, 3),
        )

        assertEquals(PythonHostColorMatch.found(3, 2), match)
        assertEquals(listOf(Read(2, 1, 3), Read(2, 2, 3)), source.reads)
        assertArrayEquals(IntArray(3), source.lastDestination)
    }

    @Test
    fun returnsCanonicalMissingResultAndClearsTheRowBuffer() {
        val source = PixelFixture(
            width = 2,
            pixels = intArrayOf(
                0xff102232.toInt(),
                0xff11222f.toInt(),
                0xff102232.toInt(),
                0xff11222f.toInt(),
            ),
        )

        val match = PythonHostColorSearch.find(source, 0x102030, 1, null)

        assertFalse(match.found)
        assertEquals(PythonHostColorMatch.NOT_FOUND, match.x)
        assertEquals(PythonHostColorMatch.NOT_FOUND, match.y)
        assertEquals(listOf(Read(0, 0, 2), Read(0, 1, 2)), source.reads)
        assertArrayEquals(IntArray(2), source.lastDestination)
    }

    @Test
    fun rejectsInvalidColorThresholdSourceAndRegionBounds() {
        val source = PixelFixture(2, IntArray(4))

        assertThrows(IllegalArgumentException::class.java) {
            PythonHostColorSearch.find(source, -1, 0, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PythonHostColorSearch.find(source, PythonHostImagePolicy.MAX_COLOR + 1, 0, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PythonHostColorSearch.find(source, 0, -1, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PythonHostColorSearch.find(
                source,
                0,
                PythonHostImagePolicy.MAX_COLOR_THRESHOLD + 1,
                null,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PythonHostColorSearch.find(InvalidPixelSource(0, 1), 0, 0, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PythonHostColorSearch.find(
                InvalidPixelSource(
                    PythonHostImagePolicy.MAX_DIMENSION,
                    PythonHostImagePolicy.MAX_DIMENSION,
                ),
                0,
                0,
                null,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PythonHostColorSearch.find(source, 0, 0, PythonHostImageRegion(1, 1, 2, 1))
        }
        assertTrue(source.reads.isEmpty())
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
