package org.autojs.autojs.runtime.api.modules.ocr

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.autojs.autojs.engine.ocrCallOptions
import org.autojs.plugin.paddle.ocr.api.PaddleOcrOptionExtraKeys
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The plugin options built from a call's options: the extras need a real Bundle.
 *
 * zh-CN: 由调用选项生成的插件选项: extras 需要真实的 Bundle.
 */
@RunWith(AndroidJUnit4::class)
class OcrCallOptionsBundleTest {

    @Test
    fun theOptionsOfACallReachThePluginOptions() {
        val options = ocrCallOptions(
            "paddle-ocr",
            JSONObject("""{"useSlim":false,"useCls":true,"detLimitSideLen":736,"variant":"v3","imageQuality":80,"imageFormat":"jpeg"}"""),
        ).toPluginOptions()

        assertEquals(4, options.cpuThreadNum)
        assertFalse(options.useSlim)
        assertEquals(-1f, options.scoreThreshold)
        assertTrue(options.extras.getBoolean(PaddleOcrOptionExtraKeys.USE_CLS))
        assertEquals(736, options.extras.getInt(PaddleOcrOptionExtraKeys.DET_LIMIT_SIDE_LEN))
        assertEquals("v3", options.extras.getString(PaddleOcrOptionExtraKeys.VARIANT))
        assertEquals(80, options.extras.getInt(PaddleOcrOptionExtraKeys.IMAGE_QUALITY))
        assertEquals("jpeg", options.extras.getString(PaddleOcrOptionExtraKeys.IMAGE_FORMAT))
    }

}
