package org.autojs.autojs.runtime.api.modules.images

import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Undefined
import org.opencv.imgproc.Imgproc

class OpenCvImageArgumentPolicyTest {

    @Test
    fun `resize requires its documented size array and rejects width height overload`() {
        listOf(Imgproc.INTER_AREA, 200).forEach { mistakenHeight ->
            val error = assertThrows(WrappedIllegalArgumentException::class.java) {
                OpenCvImageArgumentPolicy.parseResize(100, mistakenHeight)
            }
            assertTrue(error.message.orEmpty().contains("images.resize(image, [width, height], interpolation?)"))
        }
        listOf("100", Undefined.instance).forEach { size ->
            assertThrows(WrappedIllegalArgumentException::class.java) {
                OpenCvImageArgumentPolicy.parseResize(size, null)
            }
        }
    }

    @Test
    fun `resize accepts one or two positive finite dimensions`() {
        val square = OpenCvImageArgumentPolicy.parseResize(arrayOf(24).nativeArray(), null)
        assertEquals(24.0, square.size.width, 0.0)
        assertEquals(24.0, square.size.height, 0.0)
        assertEquals(Imgproc.INTER_LINEAR, square.interpolation)

        val rectangle = OpenCvImageArgumentPolicy.parseResize(arrayOf(320, 240).nativeArray(), "area")
        assertEquals(320.0, rectangle.size.width, 0.0)
        assertEquals(240.0, rectangle.size.height, 0.0)
        assertEquals(Imgproc.INTER_AREA, rectangle.interpolation)
    }

    @Test
    fun `resize rejects invalid array lengths and dimensions`() {
        listOf(
            emptyArray<Any?>(),
            arrayOf(1, 2, 3),
            arrayOf(0, 1),
            arrayOf(-1, 1),
            arrayOf<Any?>(Double.NaN, 1),
            arrayOf<Any?>(Double.POSITIVE_INFINITY, 1),
            arrayOf<Any?>(Int.MAX_VALUE.toDouble() + 1.0, 1),
        ).forEach { size ->
            assertThrows(WrappedIllegalArgumentException::class.java) {
                OpenCvImageArgumentPolicy.parseResize(size.nativeArray(), null)
            }
        }
    }

    @Test
    fun `interpolation accepts only OpenCV resize algorithms`() {
        val supported = mapOf(
            "nearest" to Imgproc.INTER_NEAREST,
            "INTER_LINEAR" to Imgproc.INTER_LINEAR,
            "cubic" to Imgproc.INTER_CUBIC,
            "AREA" to Imgproc.INTER_AREA,
            "lanczos4" to Imgproc.INTER_LANCZOS4,
            "linear-exact" to Imgproc.INTER_LINEAR_EXACT,
            "nearest_exact" to Imgproc.INTER_NEAREST_EXACT,
        )
        supported.forEach { (name, expected) ->
            assertEquals(expected, OpenCvImageArgumentPolicy.parseInterpolation(name))
            assertEquals(expected, OpenCvImageArgumentPolicy.parseInterpolation(expected))
        }

        listOf("INTER_MAX", "INTER_BITS", "unknown", -1, 7, 32, 5.5, Double.NaN).forEach { value ->
            assertThrows(WrappedIllegalArgumentException::class.java) {
                OpenCvImageArgumentPolicy.parseInterpolation(value)
            }
        }
    }

    private fun Array<out Any?>.nativeArray() = NativeArray(map { it }.toTypedArray())
}
