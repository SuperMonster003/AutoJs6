package org.autojs.autojs.core.plugin.imagequant

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.runtime.api.PngQuantBridge
import org.autojs.plugin.imagequant.api.ImageQuantPluginContract
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Cross-APK JNI smoke tests for the external Image Quantization runtime. */
class ImageQuantPluginHostDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun missingPluginRaisesCatchableLoadError() {
        assumeFalse("Image Quantization plugin is installed", isPluginInstalled())

        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            val error = try {
                PngQuantBridge.quantize(bitmap, 80)
                throw AssertionError("Quantization unexpectedly succeeded without its plugin")
            } catch (error: ImageQuantPluginLoadException) {
                error
            }
            assertEquals(ImageQuantPluginLoadError.PLUGIN_NOT_FOUND, error.code)
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("Image Quantization"))
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun installedPluginLoadsAndProducesIndexedPng() {
        assumeTrue("Image Quantization plugin is not installed", isPluginInstalled())

        val diagnostics = ImageQuantPluginHost.discover(context).joinToString { discovered ->
            "${discovered.serviceInfo.packageName}/${discovered.serviceInfo.name}: " +
                    (discovered.error?.code ?: "eligible")
        }
        assertTrue(diagnostics, ImageQuantPluginHost.isAvailable(context))

        val runtime = ImageQuantPluginHost.ensureLoaded(context)
        assertEquals(ImageQuantPluginHost.Origin.PLUGIN, runtime.origin)
        assertEquals(ImageQuantPluginContract.DEFAULT_PACKAGE_NAME, runtime.packageName)
        assertEquals(ImageQuantPluginContract.EXPECTED_LIBIMAGEQUANT_VERSION, runtime.libimagequantVersion)
        assertEquals(ImageQuantPluginContract.EXPECTED_LIBPNG_VERSION, runtime.libpngVersion)
        assertEquals(ImageQuantPluginContract.EXPECTED_NATIVE_NDK_VERSION, runtime.nativeNdkVersion)
        assertEquals(ImageQuantPluginContract.EXPECTED_NATIVE_STL, runtime.nativeStl)
        assertEquals(ImageQuantPluginContract.OPTIONS_API_VERSION, runtime.optionsApiVersion)
        assertEquals(ImageQuantPluginContract.RUNTIME_PROBE_VALUE, runtime.runtimeProbe)
        assertTrue(runtime.nativeLibraryPath.orEmpty(), runtime.nativeLibraryPath?.isNotBlank() == true)

        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888).apply {
            setPixels(
                intArrayOf(
                    0x00ff0000,
                    0xffff0000.toInt(),
                    0xff00ff00.toInt(),
                    0xff0000ff.toInt(),
                    0x80ffffff.toInt(),
                    0xff000000.toInt(),
                ),
                0,
                3,
                0,
                0,
                3,
                2,
            )
        }
        try {
            val encoded = PngQuantBridge.quantize(bitmap, 80)
            assertArrayEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
                encoded.copyOfRange(0, 8),
            )
            assertEquals(3, encoded.readPngInt(16))
            assertEquals(2, encoded.readPngInt(20))
            assertEquals(8, encoded[24].toInt() and 0xff)
            assertEquals("PNG must use indexed color", 3, encoded[25].toInt() and 0xff)

            val encodedWithOptions = PngQuantBridge.quantize(
                bitmap,
                PngQuantBridge.Options(
                    4,
                    1,
                    0,
                    100,
                    1.0F,
                    0,
                ),
            )
            assertEquals("PNG must use indexed color", 3, encodedWithOptions[25].toInt() and 0xff)
            assertTrue(encodedWithOptions.readPngPaletteSize() in 1..4)

            val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
            try {
                assertEquals(3, decoded.width)
                assertEquals(2, decoded.height)
                assertEquals(0, decoded.getPixel(0, 0) ushr 24)
                val quantizedAlpha = decoded.getPixel(1, 1) ushr 24
                assertTrue(
                    "Expected semi-transparent alpha near 128, but was $quantizedAlpha",
                    quantizedAlpha in 0x7f..0x81,
                )
            } finally {
                decoded.recycle()
            }

            val unsupported = Bitmap.createBitmap(2, 2, Bitmap.Config.RGB_565)
            try {
                val error = runCatching { PngQuantBridge.quantize(unsupported, 80) }.exceptionOrNull()
                assertTrue(error.toString(), error is IllegalArgumentException)
            } finally {
                unsupported.recycle()
            }

            // A converted Java exception must not poison later JNI calls or terminate the process.
            assertTrue(PngQuantBridge.quantize(bitmap, 80).isNotEmpty())
        } finally {
            bitmap.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun isPluginInstalled(): Boolean = try {
        context.packageManager.getApplicationInfo(ImageQuantPluginContract.DEFAULT_PACKAGE_NAME, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun ByteArray.readPngInt(offset: Int): Int {
        require(size >= offset + 4) { "PNG is truncated at offset $offset" }
        return ((this[offset].toInt() and 0xff) shl 24) or
                ((this[offset + 1].toInt() and 0xff) shl 16) or
                ((this[offset + 2].toInt() and 0xff) shl 8) or
                (this[offset + 3].toInt() and 0xff)
    }

    private fun ByteArray.readPngPaletteSize(): Int {
        var offset = 8
        while (offset + 12 <= size) {
            val length = readPngInt(offset)
            require(length >= 0 && offset + 12L + length <= size.toLong()) {
                "PNG chunk at offset $offset is invalid"
            }
            val type = String(this, offset + 4, 4, Charsets.US_ASCII)
            if (type == "PLTE") {
                require(length % 3 == 0) { "PNG palette length is invalid: $length" }
                return length / 3
            }
            offset += 12 + length
        }
        throw AssertionError("PNG does not contain a PLTE chunk")
    }
}
