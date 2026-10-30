package org.autojs.autojs.runtime.api

import android.graphics.Rect
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.autojs.autojs.engine.toOcrResultsJson
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.autojs.plugin.paddle.ocr.api.OcrResult as PluginOcrResult

/**
 * The Bundle side of `OcrResult.extras`: top-level values, nested bundles, values of unsupported types.
 *
 * zh-CN: OcrResult.extras 的 Bundle 部分: 顶层值, 嵌套 Bundle, 不支持的类型.
 */
@RunWith(AndroidJUnit4::class)
class OcrResultExtrasBundleTest {

    @Test
    fun bundlesConvertRecursively() {
        val extras = OcrResultExtras.fromBundle(
            Bundle().apply {
                putFloatArray("quad", floatArrayOf(1f, 2f))
                putString("executionProvider", "nnapi")
                putStringArrayList("names", arrayListOf("a", "b"))
                putBundle("nested", Bundle().apply { putInt("value", 1) })
                putParcelableArray("bundles", arrayOf(Bundle().apply { putBoolean("flag", true) }))
            },
        )
        assertEquals(listOf(1.0, 2.0), extras["quad"])
        assertEquals("nnapi", extras["executionProvider"])
        assertEquals(listOf("a", "b"), extras["names"])
        assertEquals(mapOf("value" to 1), extras["nested"])
        assertEquals(listOf(mapOf("flag" to true)), extras["bundles"])
    }

    @Test
    fun unsupportedValuesAreDroppedFromBundles() {
        val extras = OcrResultExtras.fromBundle(
            Bundle().apply {
                putParcelable("rect", Rect(1, 2, 3, 4))
                putInt("kept", 1)
            },
        )
        assertEquals(mapOf("kept" to 1), extras)
    }

    @Test
    fun absentAndEmptyBundlesGiveEmptyObjects() {
        assertTrue(OcrResultExtras.toScriptObject(null as Bundle?).isEmpty())
        assertTrue(OcrResultExtras.toScriptObject(Bundle()).isEmpty())
    }

    @Test
    fun theBridgeSendsTheConvertedBundleWithTheRegionOffset() {
        val result = PluginOcrResult().apply {
            text = "text"
            confidence = 0.5f
            bounds = Rect(1, 2, 3, 4)
            extras = Bundle().apply {
                putFloatArray("quad", floatArrayOf(1f, 2f, 3f, 2f, 3f, 4f, 1f, 4f))
                putString("executionProvider", "nnapi")
                putBundle("nested", Bundle().apply { putInt("value", 1) })
                putParcelable("rect", Rect(1, 2, 3, 4))
            }
        }
        val item = JSONArray(listOf(result).toOcrResultsJson(10, 20)).getJSONObject(0)

        assertEquals(11, item.getJSONObject("bounds").getInt("left"))
        val extras = item.getJSONObject("extras")
        assertEquals(setOf("executionProvider", "nested", "quad"), extras.keys().asSequence().toSet())
        val quad = extras.getJSONArray("quad")
        assertEquals(listOf(11.0, 22.0, 13.0, 22.0, 13.0, 24.0, 11.0, 24.0), List(quad.length(), quad::getDouble))
        assertEquals("nnapi", extras.getString("executionProvider"))
        assertEquals(1, extras.getJSONObject("nested").getInt("value"))
    }

}
