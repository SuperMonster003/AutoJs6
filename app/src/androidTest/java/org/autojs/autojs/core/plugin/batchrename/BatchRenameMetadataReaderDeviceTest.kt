package org.autojs.autojs.core.plugin.batchrename

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.batchrename.api.BatchRenameMetadataKeys
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The photo metadata reader of the Batch Rename metadata variables (plugin roadmap P6.4) on generated JPEG, PNG and WebP
 * files: EXIF values, XMP for author and copyright, images without EXIF and files that are not images.
 * zh-CN: Batch Rename 元数据变量所用的照片元数据读取器 (插件路线图 P6.4), 使用生成的 JPEG, PNG 与 WebP 文件: EXIF 值, 作者与版权的
 * XMP 回退, 无 EXIF 的图片以及非图片文件.
 */
class BatchRenameMetadataReaderDeviceTest {

    private val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "metadata-${System.nanoTime()}").apply { mkdirs() }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    /** ExifInterface takes FNumber and ExposureTime as decimal text only. zh-CN: ExifInterface 只接受十进制文本形式的光圈值与曝光时间. */
    @Test
    fun jpegExifAndXmpBecomeRawValues() {
        val file = BatchRenameSampleImages.write(
            File(root, "photo.jpg"),
            exif = mapOf(
                ExifInterface.TAG_MAKE to "Canon",
                ExifInterface.TAG_MODEL to "EOS R5",
                ExifInterface.TAG_LENS_MODEL to "RF50mm F1.8",
                ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY to "400",
                ExifInterface.TAG_F_NUMBER to "2.8",
                ExifInterface.TAG_EXPOSURE_TIME to "0.004",
                ExifInterface.TAG_FOCAL_LENGTH to "50/1",
                ExifInterface.TAG_FLASH to "16",
                ExifInterface.TAG_COPYRIGHT to "Copyright Ann",
                ExifInterface.TAG_DATETIME_ORIGINAL to "2026:10:15 08:30:59",
                ExifInterface.TAG_EXPOSURE_BIAS_VALUE to "1/3",
                ExifInterface.TAG_ORIENTATION to "6",
                ExifInterface.TAG_COLOR_SPACE to "1",
            ),
            xmp = BatchRenameSampleImages.xmp(creator = "Ann Example", rights = "CC BY 4.0"),
            latLong = 31.2304164 to -121.4737011,
            altitude = 120.0,
        )
        val values = requireNotNull(BatchRenameMetadataReader.read(file))
        assertEquals("Canon", values[BatchRenameMetadataKeys.CAMERA_MAKE])
        assertEquals("EOS R5", values[BatchRenameMetadataKeys.CAMERA_MODEL])
        assertEquals("RF50mm F1.8", values[BatchRenameMetadataKeys.LENS])
        assertEquals(400, values[BatchRenameMetadataKeys.ISO])
        assertEquals(2.8, values[BatchRenameMetadataKeys.F_NUMBER] as Double, 1e-6)
        assertEquals(0.004, values[BatchRenameMetadataKeys.EXPOSURE_TIME] as Double, 1e-6)
        assertEquals(50.0, values[BatchRenameMetadataKeys.FOCAL_LENGTH] as Double, 1e-6)
        assertEquals(16, values[BatchRenameMetadataKeys.FLASH])
        assertEquals(64, values[BatchRenameMetadataKeys.WIDTH])
        assertEquals(48, values[BatchRenameMetadataKeys.HEIGHT])
        // EXIF lacks the artist: the author comes from XMP; the copyright stays the EXIF one.
        // zh-CN: EXIF 缺少作者: 作者取自 XMP; 版权仍为 EXIF 中的值.
        assertEquals("Ann Example", values[BatchRenameMetadataKeys.AUTHOR])
        assertEquals("Copyright Ann", values[BatchRenameMetadataKeys.COPYRIGHT])
        assertEquals(31.2304164, values[BatchRenameMetadataKeys.LATITUDE] as Double, 1e-5)
        assertEquals(-121.4737011, values[BatchRenameMetadataKeys.LONGITUDE] as Double, 1e-5)
        assertEquals(120.0, values[BatchRenameMetadataKeys.ALTITUDE] as Double, 1e-3)
        assertEquals("2026-10-15T08:30:59", values[BatchRenameMetadataKeys.DATE_TAKEN])
        assertEquals(1.0 / 3, values[BatchRenameMetadataKeys.EXPOSURE_BIAS] as Double, 1e-6)
        assertEquals(6, values[BatchRenameMetadataKeys.ORIENTATION])
        assertEquals("sRGB", values[BatchRenameMetadataKeys.COLOR_SPACE])
        // The values travel as a Bundle of the contract. zh-CN: 这些值以契约的 Bundle 形式传输.
        val bundle = BatchRenameMetadataReader.toBundle(values)
        assertEquals(values.keys, bundle.keySet())
    }

    @Test
    fun exifAuthorWinsAndXmpFillsTheCopyright() {
        val file = BatchRenameSampleImages.write(
            File(root, "second.jpg"),
            exif = mapOf(
                ExifInterface.TAG_ARTIST to "EXIF Author",
                ExifInterface.TAG_COLOR_SPACE to "65535",
                ExifInterface.TAG_INTEROPERABILITY_INDEX to "R03",
            ),
            xmp = BatchRenameSampleImages.xmp(creator = "XMP Author", rights = "CC BY 4.0"),
        )
        val values = requireNotNull(BatchRenameMetadataReader.read(file))
        assertEquals("EXIF Author", values[BatchRenameMetadataKeys.AUTHOR])
        assertEquals("CC BY 4.0", values[BatchRenameMetadataKeys.COPYRIGHT])
        assertEquals("AdobeRGB", values[BatchRenameMetadataKeys.COLOR_SPACE])
    }

    @Test
    fun pngAndWebpAreReadAndAScreenshotHasOnlyItsSize() {
        @Suppress("DEPRECATION")
        val webp = BatchRenameSampleImages.write(File(root, "a.webp"), Bitmap.CompressFormat.WEBP, mapOf(ExifInterface.TAG_MODEL to "Pixel 9"))
        assertEquals("Pixel 9", BatchRenameMetadataReader.read(webp)?.get(BatchRenameMetadataKeys.CAMERA_MODEL))
        val png = BatchRenameSampleImages.write(File(root, "b.png"), Bitmap.CompressFormat.PNG, mapOf(ExifInterface.TAG_MODEL to "Pixel 8"))
        assertEquals("Pixel 8", BatchRenameMetadataReader.read(png)?.get(BatchRenameMetadataKeys.CAMERA_MODEL))
        val screenshot = BatchRenameSampleImages.write(File(root, "Screenshot.png"), Bitmap.CompressFormat.PNG)
        assertEquals(mapOf(BatchRenameMetadataKeys.WIDTH to 64, BatchRenameMetadataKeys.HEIGHT to 48), BatchRenameMetadataReader.read(screenshot))
    }

    @Test
    fun filesThatAreNotImagesHaveNoMetadata() {
        val text = File(root, "notes.txt").apply { writeText("notes") }
        assertFalse(BatchRenameMetadataReader.isImage(text))
        assertNull(BatchRenameMetadataReader.read(text))
        val fake = File(root, "fake.jpg").apply { writeText("not a jpeg") }
        assertTrue(BatchRenameMetadataReader.isImage(fake))
        assertNull(BatchRenameMetadataReader.read(fake)?.get(BatchRenameMetadataKeys.CAMERA_MODEL))
        assertNull(BatchRenameMetadataReader.read(File(root, "missing.jpg")))
        assertEquals("2026-10-15T08:30:59", BatchRenameMetadataReader.dateTime("2026:10:15 08:30:59"))
        assertNull(BatchRenameMetadataReader.dateTime("0000:00:00 00:00:00"))
    }
}
