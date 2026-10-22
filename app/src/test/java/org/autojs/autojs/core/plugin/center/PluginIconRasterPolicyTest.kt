package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginIconRasterPolicyTest {

    // 64dp at 560 dpi (xxxhdpi emulator), the size kept by the plugin center.
    private val itemSize = 224

    @Test fun onlyNonXmlFileResourcesAreDecodedAsBitmaps() {
        assertTrue(PluginIconRasterPolicy.isBitmapFile("res/mipmap/ic_plugin_center.png"))
        assertTrue(PluginIconRasterPolicy.isBitmapFile("res/mipmap-night-v8/ic_plugin_center.PNG"))
        assertTrue(PluginIconRasterPolicy.isBitmapFile("res/mipmap-xxxhdpi-v4/ic_launcher.webp"))
        assertTrue(PluginIconRasterPolicy.isBitmapFile("res/drawable/icon.jpg"))
        assertFalse(PluginIconRasterPolicy.isBitmapFile("res/mipmap-anydpi-v26/ic_launcher.xml"))
        assertFalse(PluginIconRasterPolicy.isBitmapFile("res/drawable/frame.9.png"))
        assertFalse(PluginIconRasterPolicy.isBitmapFile(""))
        assertFalse(PluginIconRasterPolicy.isBitmapFile(null))
    }

    @Test fun densityLessArtworkIsDecodedWithoutUpscaling() {
        // A 432 px PNG in res/mipmap: Resources would scale it 3.5x to 1512 px (9 MB) at 560 dpi.
        assertEquals(1, PluginIconRasterPolicy.sampleSize(432, 432, itemSize))
        assertEquals(itemSize to itemSize, PluginIconRasterPolicy.scaledSize(432, 432, itemSize))
        val (width, height) = PluginIconRasterPolicy.scaledSize(1512, 1512, itemSize)
        assertTrue(width.toLong() * height * 4 < 1512L * 1512 * 4 / 40)
    }

    @Test fun sampleSizeKeepsTheShorterSideAtLeastTheTarget() {
        assertEquals(1, PluginIconRasterPolicy.sampleSize(447, 447, itemSize))
        assertEquals(2, PluginIconRasterPolicy.sampleSize(448, 448, itemSize))
        assertEquals(4, PluginIconRasterPolicy.sampleSize(1024, 1024, itemSize))
        assertEquals(8, PluginIconRasterPolicy.sampleSize(4096, 1800, itemSize))
        assertEquals(1, PluginIconRasterPolicy.sampleSize(100, 100, itemSize))
        assertEquals(1, PluginIconRasterPolicy.sampleSize(4096, 4096, 0))
        for (side in listOf(225, 500, 1512, 4096)) {
            val sample = PluginIconRasterPolicy.sampleSize(side, side, itemSize)
            assertTrue("side $side, sample $sample", side / sample >= itemSize)
            assertTrue("side $side, sample $sample", side / (sample * 2) < itemSize)
        }
    }

    @Test fun smallBitmapsAreKeptAsIs() {
        assertTrue(PluginIconRasterPolicy.fits(192, 192, itemSize))
        assertTrue(PluginIconRasterPolicy.fits(224, 224, itemSize))
        assertTrue(PluginIconRasterPolicy.fits(500, 224, itemSize))
        assertFalse(PluginIconRasterPolicy.fits(225, 225, itemSize))
        assertTrue(PluginIconRasterPolicy.fits(4096, 4096, 0))
        assertEquals(192 to 192, PluginIconRasterPolicy.scaledSize(192, 192, itemSize))
        assertEquals(0 to 0, PluginIconRasterPolicy.scaledSize(0, 0, itemSize))
    }

    @Test fun downscalingKeepsTheAspectRatioForCenterCrop() {
        assertEquals(448 to itemSize, PluginIconRasterPolicy.scaledSize(1024, 512, itemSize))
        assertEquals(itemSize to 336, PluginIconRasterPolicy.scaledSize(600, 900, itemSize))
        assertEquals(448 to 1, PluginIconRasterPolicy.scaledSize(2240, 5, 1))
    }
}
