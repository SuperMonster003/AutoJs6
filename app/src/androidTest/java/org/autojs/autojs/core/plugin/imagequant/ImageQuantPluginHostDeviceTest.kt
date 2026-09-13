package org.autojs.autojs.core.plugin.imagequant

import android.content.pm.PackageManager
import android.system.Os
import android.system.OsConstants
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
import java.io.File
import java.io.FileOutputStream

/** Cross-APK JNI smoke tests for the external Image Quantization runtime. */
class ImageQuantPluginHostDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun installedUnalignedPluginRaisesCatchableLoadError() {
        assumeTrue("Legacy native fixture is opt-in", InstrumentationRegistry.getArguments()
            .getString("autojs.nativeAlignment.legacyImageQuant").toBoolean())
        assumeTrue(org.autojs.autojs.core.plugin.nativealign.NativePageSize.current() == 16384L)
        assertTrue("Install the fixture containing the legacy 4 KB JNI library first", isPluginInstalled())
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            val error = try {
                PngQuantBridge.quantize(bitmap, 80)
                throw AssertionError("Legacy 4 KB JNI library unexpectedly loaded")
            } catch (error: ImageQuantPluginLoadException) {
                error
            }
            assertEquals(ImageQuantPluginLoadError.NATIVE_LIBRARY_PAGE_ALIGNMENT_UNSUPPORTED, error.code)
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("4096"))
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("16384"))
        } finally {
            bitmap.recycle()
        }
    }

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
        assertTrue(runtime.nativeNdkVersion in setOf(ImageQuantPluginContract.EXPECTED_NATIVE_NDK_VERSION, "28.2.13676358"))
        assertTrue((runtime.nativeMinLoadAlign ?: 0) >= org.autojs.autojs.core.plugin.nativealign.NativePageSize.current())
        assertEquals(ImageQuantPluginContract.EXPECTED_NATIVE_STL, runtime.nativeStl)
        assertEquals(ImageQuantPluginContract.OPTIONS_API_VERSION, runtime.optionsApiVersion)
        assertTrue(runtime.optionsApiVersion >= ImageQuantPluginContract.COLOR_IO_API_VERSION)
        assertTrue(runtime.optionsApiVersion >= ImageQuantPluginContract.RESOURCE_CONTROL_API_VERSION)
        assertEquals(ImageQuantPluginContract.RUNTIME_PROBE_VALUE, runtime.runtimeProbe)
        assertTrue(runtime.nativeLibraryPath.orEmpty(), runtime.nativeLibraryPath?.isNotBlank() == true)
        val nativeFile = File(checkNotNull(runtime.nativeLibraryPath))
        assertTrue("Staged native file must be read-only", !nativeFile.canWrite())
        val writeBits = OsConstants.S_IWUSR or OsConstants.S_IWGRP or OsConstants.S_IWOTH
        assertEquals("Native file must have no write mode bits", 0, Os.stat(nativeFile.path).st_mode and writeBits)

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
            assertTrue("PNG must advertise sRGB", encoded.hasPngChunk("sRGB"))
            assertTrue("Transparent PNG must contain tRNS", encoded.hasPngChunk("tRNS"))

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

            val result = PngQuantBridge.quantizeWithResult(
                bitmap,
                PngQuantBridge.Options(
                    2,
                    8,
                    0,
                    100,
                    0.0F,
                    0,
                ),
            )
            assertTrue(result.bytes.isNotEmpty())
            assertEquals(result.bytes.size, result.size)
            assertTrue("Unexpected quantization quality ${result.quality}", result.quality in 0..100)
            assertTrue(
                "Unexpected quantization error ${result.quantizationError}",
                result.quantizationError >= 0.0 && result.quantizationError.isFinite(),
            )
            assertTrue("v4 must report accounted peak memory", result.peakWorkingMemoryBytes > 0L)

            val resourceError = runCatching {
                PngQuantBridge.quantizeWithResult(
                    bitmap,
                    PngQuantBridge.Options(
                        256,
                        8,
                        0,
                        100,
                        0.0F,
                        0,
                        true,
                        5L,
                        PngQuantBridge.Options.DEFAULT_MAX_MEMORY_BYTES,
                    ),
                )
            }.exceptionOrNull()
            assertTrue(resourceError.toString(), resourceError is PngQuantBridge.ResourceLimitException)
            resourceError as PngQuantBridge.ResourceLimitException
            assertEquals(PngQuantBridge.ResourceLimitException.PIXEL_LIMIT, resourceError.reason)
            assertEquals(6L, resourceError.actualPixels)
            assertEquals(5L, resourceError.maxPixels)

            val cancellationToken = PngQuantBridge.CancellationToken().apply { cancel() }
            val cancellationError = runCatching {
                PngQuantBridge.quantize(bitmap, 80, cancellationToken)
            }.exceptionOrNull()
            assertTrue(
                cancellationError.toString(),
                cancellationError is PngQuantBridge.QuantizationCancelledException,
            )

            val qualityError = runCatching {
                PngQuantBridge.quantizeWithResult(
                    bitmap,
                    PngQuantBridge.Options(
                        2,
                        8,
                        100,
                        100,
                        0.0F,
                        0,
                    ),
                )
            }.exceptionOrNull()
            assertTrue(qualityError.toString(), qualityError is PngQuantBridge.QualityTooLowException)

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

            val opaque = PngQuantBridge.quantizeWithResult(
                bitmap,
                PngQuantBridge.Options(
                    256,
                    8,
                    0,
                    100,
                    0.0F,
                    0,
                    false,
                ),
            )
            assertTrue("Opaque output must not contain tRNS", !opaque.bytes.hasPngChunk("tRNS"))
            val opaqueDecoded = BitmapFactory.decodeByteArray(opaque.bytes, 0, opaque.bytes.size)
            try {
                assertTrue(
                    "preserveAlpha=false must produce opaque pixels",
                    IntArray(opaqueDecoded.width * opaqueDecoded.height).also {
                        opaqueDecoded.getPixels(it, 0, opaqueDecoded.width, 0, 0, opaqueDecoded.width, opaqueDecoded.height)
                    }.all { it ushr 24 == 0xff },
                )
            } finally {
                opaqueDecoded.recycle()
            }

            val rgb565 = Bitmap.createBitmap(2, 2, Bitmap.Config.RGB_565).apply {
                setPixels(
                    intArrayOf(
                        0xffff0000.toInt(),
                        0xff00ff00.toInt(),
                        0xff0000ff.toInt(),
                        0xffffffff.toInt(),
                    ),
                    0,
                    2,
                    0,
                    0,
                    2,
                    2,
                )
            }
            try {
                val converted = PngQuantBridge.quantize(rgb565, 80)
                assertTrue(converted.isNotEmpty())
                assertTrue("Converted PNG must advertise sRGB", converted.hasPngChunk("sRGB"))
                val convertedDecoded = BitmapFactory.decodeByteArray(converted, 0, converted.size)
                try {
                    assertEquals(rgb565.width, convertedDecoded.width)
                    assertEquals(rgb565.height, convertedDecoded.height)
                } finally {
                    convertedDecoded.recycle()
                }
            } finally {
                rgb565.recycle()
            }

            val outputFile = File.createTempFile("pngquant-host-m4-", ".png", context.cacheDir)
            try {
                val outputResult = FileOutputStream(outputFile).use { output ->
                    PngQuantBridge.quantizeToFileDescriptor(
                        bitmap,
                        output.fd,
                        PngQuantBridge.Options(
                            256,
                            8,
                            0,
                            100,
                            0.0F,
                            0,
                            true,
                            PngQuantBridge.Options.DEFAULT_MAX_PIXELS,
                            PngQuantBridge.Options.DEFAULT_MAX_MEMORY_BYTES,
                        ),
                    ).also {
                        assertTrue("Native code must leave the caller descriptor open", output.fd.valid())
                    }
                }
                assertEquals(outputFile.length(), outputResult.size)
                assertTrue(outputResult.size > 8L)
                assertTrue(outputResult.quality in 0..100)
                assertTrue(outputResult.quantizationError >= 0.0 && outputResult.quantizationError.isFinite())
                assertTrue("v4 FD output must report accounted peak memory", outputResult.peakWorkingMemoryBytes > 0L)
                val streamedDecoded = BitmapFactory.decodeFile(outputFile.absolutePath)
                try {
                    assertEquals(bitmap.width, streamedDecoded.width)
                    assertEquals(bitmap.height, streamedDecoded.height)
                } finally {
                    streamedDecoded.recycle()
                }
            } finally {
                outputFile.delete()
            }
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

    private fun ByteArray.hasPngChunk(expectedType: String): Boolean {
        var offset = 8
        while (offset + 12 <= size) {
            val length = readPngInt(offset)
            require(length >= 0 && offset + 12L + length <= size.toLong()) {
                "PNG chunk at offset $offset is invalid"
            }
            if (String(this, offset + 4, 4, Charsets.US_ASCII) == expectedType) return true
            offset += 12 + length
        }
        return false
    }
}
