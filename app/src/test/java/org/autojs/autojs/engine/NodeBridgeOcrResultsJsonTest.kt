package org.autojs.autojs.engine

import android.graphics.Rect
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.autojs.plugin.paddle.ocr.api.OcrResult as PluginOcrResult

/**
 * The JSON array of the ocr bridge. A result without engine extras still carries an empty object; the values of a real
 * Bundle need the Android runtime and are covered by the instrumented `OcrResultExtrasBundleTest`.
 *
 * zh-CN: ocr 桥的 JSON 数组. 引擎未提供 extras 的结果也带空对象; 真实 Bundle 的取值需要 Android 运行时, 由插桩测试 OcrResultExtrasBundleTest 覆盖.
 */
class NodeBridgeOcrResultsJsonTest {

    @Test
    fun resultsWithoutEngineExtrasCarryAnEmptyObject() {
        val results = JSONArray(listOf(result("text", 0.5f, rect(1, 2, 3, 4)), result(null, Float.NaN, null)).toOcrResultsJson(10, 20))

        val first = results.getJSONObject(0)
        assertEquals("text", first.getString("text"))
        assertEquals(0.5, first.getDouble("confidence"), 0.0)
        val bounds = first.getJSONObject("bounds")
        assertEquals(listOf(11, 22, 13, 24), listOf("left", "top", "right", "bottom").map(bounds::getInt))
        assertEquals("{}", first.getJSONObject("extras").toString())

        val second = results.getJSONObject(1)
        assertEquals("", second.getString("text"))
        assertFalse(second.has("bounds"))
        assertEquals("{}", second.getJSONObject("extras").toString())
    }

    @Test
    fun mergeLineJoinsTheResultsOfALine() {
        val results = listOf(result("a", 0.5f, rect(0, 0, 10, 20)), result("b", 0.5f, rect(20, 0, 30, 20)), result("c", 0.5f, rect(0, 40, 10, 60)))

        val merged = JSONArray(results.toOcrResultsJson(10, 20, mergeLine = true))
        assertEquals(listOf("ab", "c"), (0 until merged.length()).map { merged.getJSONObject(it).getString("text") })
        assertEquals(40, merged.getJSONObject(0).getJSONObject("bounds").getInt("right"))
        assertEquals("{}", merged.getJSONObject(0).getJSONObject("extras").toString())

        assertEquals(3, JSONArray(results.toOcrResultsJson(10, 20)).length())
    }

    @Test
    fun mergeLineLeavesASingleResultWithoutBoundsAsItIs() {
        val results = JSONArray(listOf(result("a", 0.5f, null)).toOcrResultsJson(0, 0, mergeLine = true))
        assertFalse(results.getJSONObject(0).has("bounds"))
    }

    private fun result(text: String?, confidence: Float, bounds: Rect?) = PluginOcrResult().also {
        it.text = text
        it.confidence = confidence
        it.bounds = bounds
    }

    // android.graphics.Rect is a stub in JVM tests, so its constructors cannot run.
    // zh-CN: JVM 测试里 android.graphics.Rect 是桩, 不能调用构造函数.
    private fun rect(left: Int, top: Int, right: Int, bottom: Int): Rect = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").let { field ->
        field.isAccessible = true
        ((field.get(null) as sun.misc.Unsafe).allocateInstance(Rect::class.java) as Rect).also {
            it.left = left
            it.top = top
            it.right = right
            it.bottom = bottom
        }
    }

}
