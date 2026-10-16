package org.autojs.autojs.core.ui.inflater

import org.autojs.autojs.core.ui.inflater.VectorPathData.Close
import org.autojs.autojs.core.ui.inflater.VectorPathData.CubicTo
import org.autojs.autojs.core.ui.inflater.VectorPathData.LineTo
import org.autojs.autojs.core.ui.inflater.VectorPathData.MoveTo
import org.autojs.autojs.core.ui.inflater.VectorPathData.QuadTo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/**
 * Host roadmap `docs/dev/android-xml-attributes-roadmap.md` A8: vector path data read as Android's PathParser reads
 * it (numbers, relative commands, implicit repetition, smooth curves, arcs), and the test vectors the device test
 * compares with the platform keep their aapt-compiled and source copies identical.
 *
 * zh-CN: 宿主路线图 A8: 按 Android PathParser 的方式读取矢量路径数据, 以及设备测试所用测试矢量图的两份副本保持一致.
 */
class VectorPathDataTest {

    @Test
    fun numbersSplitLikeThePlatform() {
        assertArrayEquals(floatArrayOf(1.5f, 0.5f), VectorPathData.numbers("1.5.5"), 0f)
        assertArrayEquals(floatArrayOf(1f, -2f, -3f), VectorPathData.numbers("1-2-3"), 0f)
        assertArrayEquals(floatArrayOf(0.001f, 20f), VectorPathData.numbers("1e-3,2e1"), 1e-6f)
        assertArrayEquals(floatArrayOf(3f, 4f), VectorPathData.numbers(" 3 ,\n4 "), 0f)
        assertThrows(IllegalArgumentException::class.java) { VectorPathData.numbers("1x") }
    }

    @Test
    fun relativeCommandsAndImplicitRepetitionBecomeAbsolute() {
        assertEquals(
            listOf(MoveTo(10f, 10f), LineTo(15f, 10f), LineTo(15f, 20f), LineTo(5f, 20f), LineTo(5f, 25f), Close, MoveTo(11f, 12f)),
            // After m, further pairs are lines; z returns to the subpath start.
            VectorPathData.parse("m10,10 5,0 0,10h-10v5zm1,2"),
        )
        assertEquals(
            listOf(MoveTo(1f, 1f), LineTo(2f, 2f), LineTo(3f, 3f)),
            VectorPathData.parse("M1,1 2,2 3,3"),
        )
    }

    @Test
    fun smoothCurvesReflectThePreviousControlPoint() {
        assertEquals(
            listOf(MoveTo(0f, 0f), CubicTo(1f, 2f, 3f, 2f, 4f, 0f), CubicTo(5f, -2f, 7f, -2f, 8f, 0f)),
            VectorPathData.parse("M0,0C1,2 3,2 4,0S7,-2 8,0"),
        )
        assertEquals(
            listOf(MoveTo(0f, 0f), QuadTo(2f, 4f, 4f, 0f), QuadTo(6f, -4f, 8f, 0f)),
            VectorPathData.parse("M0,0q2,4 4,0t4,0"),
        )
        // Without a preceding curve the first control point is the current point.
        assertEquals(CubicTo(0f, 0f, 1f, 1f, 2f, 0f), VectorPathData.parse("M0,0S1,1 2,0")[1])
    }

    @Test
    fun arcsBecomeCubicsThatEndOnTheArc() {
        val ops = VectorPathData.parse("M4,12a8,8 0 1,1 16,0")
        val cubics = ops.filterIsInstance<CubicTo>()
        // A half circle in segments of at most 45 degrees.
        assertEquals(4, cubics.size)
        assertEquals(20f, cubics.last().x, 1e-4f)
        assertEquals(12f, cubics.last().y, 1e-4f)
        // Every segment end lies on the circle of radius 8 around (12, 12), above the start for this sweep.
        for (cubic in cubics) {
            assertEquals(8.0, hypot((cubic.x - 12).toDouble(), (cubic.y - 12).toDouble()), 1e-3)
            assertTrue(cubic.y <= 12.0001f)
        }
        // A zero radius is a line; radii too small for the end points are scaled up.
        assertEquals(listOf(MoveTo(0f, 0f), LineTo(5f, 5f)), VectorPathData.parse("M0,0A0,3 0 0,1 5,5"))
        val scaled = VectorPathData.parse("M0,0A1,1 0 0,1 10,0").filterIsInstance<CubicTo>()
        assertEquals(10f, scaled.last().x, 1e-3f)
        assertEquals(0f, scaled.last().y, 1e-3f)
    }

    @Test
    fun malformedPathDataIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { VectorPathData.parse("M1,1 X2,2") }
        assertThrows(IllegalArgumentException::class.java) { VectorPathData.parse("M1") }
        assertThrows(IllegalArgumentException::class.java) { VectorPathData.parse("L1,1,2") }
    }

    @Test
    fun deviceTestVectorsKeepIdenticalCopies() {
        val compiled = File("src/androidTest/res/drawable").listFiles { file -> file.name.startsWith("ui_vector_") }.orEmpty()
        assertEquals(4, compiled.size)
        for (file in compiled) {
            assertEquals(file.name, file.readText(), File("src/androidTest/assets/ui/vector/${file.name}").readText())
        }
    }
}
