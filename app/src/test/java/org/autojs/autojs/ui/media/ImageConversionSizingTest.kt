package org.autojs.autojs.ui.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageConversionSizingTest {

    private val source = ImagePixelSize(4000, 3000)

    @Test
    fun resolvesOriginalAndPercentageSizes() {
        assertEquals(
            source,
            ImageConversionSizing.resolve(source, ImageResizeRequest(ImageResizeMode.ORIGINAL)).size,
        )
        assertEquals(
            ImagePixelSize(1000, 750),
            ImageConversionSizing.resolve(
                source,
                ImageResizeRequest(ImageResizeMode.PERCENTAGE, percentage = 25),
            ).size,
        )
    }

    @Test
    fun rejectsInvalidAndExcessiveSizes() {
        assertEquals(
            ImageResizeError.INVALID_PERCENTAGE,
            ImageConversionSizing.resolve(
                source,
                ImageResizeRequest(ImageResizeMode.PERCENTAGE, percentage = 0),
            ).error,
        )
        assertEquals(
            ImageResizeError.INVALID_WIDTH,
            ImageConversionSizing.resolve(
                source,
                ImageResizeRequest(ImageResizeMode.CUSTOM, width = 0, height = 100),
            ).error,
        )
        assertEquals(
            ImageResizeError.DIMENSION_TOO_LARGE,
            ImageConversionSizing.resolve(
                source,
                ImageResizeRequest(ImageResizeMode.CUSTOM, width = 20_000, height = 100),
            ).error,
        )
        assertEquals(
            ImageResizeError.PIXEL_COUNT_TOO_LARGE,
            ImageConversionSizing.resolve(
                source,
                ImageResizeRequest(ImageResizeMode.CUSTOM, width = 10_000, height = 5_000),
            ).error,
        )
    }

    @Test
    fun computesLockedAspectRatioDimensions() {
        assertEquals(1500, ImageConversionSizing.heightForWidth(source, 2000))
        assertEquals(2000, ImageConversionSizing.widthForHeight(source, 1500))
    }

    @Test
    fun qualityAppliesOnlyToLossyFormats() {
        assertTrue(ImageOutputFormat.JPEG.supportsQuality)
        assertFalse(ImageOutputFormat.PNG.supportsQuality)
        assertTrue(ImageOutputFormat.WEBP.supportsQuality)
    }

    @Test
    fun encodedSizeEstimateRespondsToQualityAndResolution() {
        val small = ImageConversionOptions(
            format = ImageOutputFormat.JPEG,
            quality = 50,
            targetSize = ImagePixelSize(1000, 750),
        )
        val highQuality = small.copy(quality = 95)
        val large = small.copy(targetSize = ImagePixelSize(2000, 1500))

        assertTrue(ImageConversionSizing.estimateEncodedBytes(highQuality) > ImageConversionSizing.estimateEncodedBytes(small))
        assertTrue(ImageConversionSizing.estimateEncodedBytes(large) > ImageConversionSizing.estimateEncodedBytes(small))
    }
}
