package org.autojs.autojs.runtime.api.modules.ocr

import org.autojs.autojs.engine.ocrCallOptions
import org.autojs.plugin.paddle.ocr.api.PaddleOcrOptionExtraKeys
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject

/**
 * The options of an OCR call as Rhino's modules and the Node.js and Bun bridge read them. The JSON cases go through
 * `ocrCallOptions` of the bridge, which runs without a Rhino Context.
 *
 * zh-CN: Rhino 模块与 Node.js / Bun 桥读取到的 OCR 调用选项. JSON 用例走桥的 ocrCallOptions, 运行时没有 Rhino Context.
 */
class OcrCallOptionsTest {

    private fun paddle(json: String = "{}") = ocrCallOptions("paddle-ocr", JSONObject(json))

    private fun rapid(json: String = "{}") = ocrCallOptions("rapid-ocr", JSONObject(json))

    private fun nativeObject(vararg values: Pair<String, Any?>) = NativeObject().also { target ->
        values.forEach { (key, value) -> ScriptableObject.putProperty(target, key, value) }
    }

    @Test
    fun paddleDefaultsAreRhinos() {
        val options = paddle()
        assertEquals(4, options.cpuThreadNum)
        assertTrue(options.useSlim)
        assertFalse(options.useOpenCL)
        assertEquals(0, options.detLongSize)
        assertEquals(-1f, options.scoreThreshold)
        assertFalse(options.mergeLine)
        assertFalse(options.extras.containsKey(PaddleOcrOptionExtraKeys.USE_CLS))
        assertFalse(options.extras.containsKey(PaddleOcrOptionExtraKeys.DET_LIMIT_SIDE_LEN))
    }

    @Test
    fun useSlimReachesThePlugin() {
        assertFalse(paddle("""{"useSlim":false}""").useSlim)
        assertTrue(paddle("""{"useSlim":true}""").useSlim)
    }

    @Test
    fun cpuThreadNumReachesThePlugin() {
        assertEquals(2, paddle("""{"cpuThreadNum":2}""").cpuThreadNum)
    }

    @Test
    fun detLongSizeReachesThePlugin() {
        assertEquals(960, paddle("""{"detLongSize":960}""").detLongSize)
    }

    @Test
    fun scoreThresholdReachesThePlugin() {
        assertEquals(0.75f, paddle("""{"scoreThreshold":0.75}""").scoreThreshold)
    }

    @Test
    fun useClsIsOnlyPassedWhenGiven() {
        assertEquals(true, paddle("""{"useCls":true}""").extras[PaddleOcrOptionExtraKeys.USE_CLS])
        assertEquals(false, paddle("""{"useCls":false}""").extras[PaddleOcrOptionExtraKeys.USE_CLS])
    }

    @Test
    fun detLimitSideLenIsOnlyPassedWhenPositive() {
        assertEquals(736, paddle("""{"detLimitSideLen":736}""").extras[PaddleOcrOptionExtraKeys.DET_LIMIT_SIDE_LEN])
        assertFalse(paddle("""{"detLimitSideLen":0}""").extras.containsKey(PaddleOcrOptionExtraKeys.DET_LIMIT_SIDE_LEN))
    }

    @Test
    fun useOpenCLReachesThePlugin() {
        assertTrue(paddle("""{"useOpenCL":true}""").useOpenCL)
    }

    @Test
    fun mergeLineIsOffWithWordSegments() {
        assertTrue(paddle("""{"mergeLine":true}""").mergeLine)
        assertFalse(paddle("""{"mergeLine":true,"splitWords":true}""").mergeLine)
        assertFalse(paddle("""{"mergeLine":true,"useWordSegmentation":true}""").mergeLine)
    }

    @Test
    fun rapidDefaultsAreRhinos() {
        val options = rapid()
        assertEquals(1024, options.detLongSize)
        assertEquals(0.5f, options.scoreThreshold)
        assertEquals(0, options.cpuThreadNum)
        assertFalse(options.useSlim)
        assertFalse(options.mergeLine)
    }

    @Test
    fun maxSideLenWinsOverDetLongSize() {
        assertEquals(800, rapid("""{"detLongSize":800}""").detLongSize)
        assertEquals(640, rapid("""{"detLongSize":800,"maxSideLen":640}""").detLongSize)
        assertEquals(1024, rapid("""{"maxSideLen":0}""").detLongSize)
    }

    @Test
    fun boxScoreThreshBacksScoreThreshold() {
        assertEquals(0.25f, rapid("""{"boxScoreThresh":0.25}""").scoreThreshold)
        assertEquals(0.75f, rapid("""{"boxScoreThresh":0.25,"scoreThreshold":0.75}""").scoreThreshold)
    }

    @Test
    fun rapidKeepsItsVariantAliases() {
        assertEquals("pp-ocrv3", rapid("""{"variant":"v3"}""").variant)
        assertEquals("v3", rapid("""{"variant":"v3","engine":"paddle-ocr"}""").variant)
    }

    @Test
    fun mlKitTakesNoRecognitionOptions() {
        val options = ocrCallOptions("mlkit-ocr", JSONObject("""{"useSlim":true,"cpuThreadNum":2,"scoreThreshold":0.5,"useOpenCL":true,"mergeLine":true}"""))
        assertEquals(OcrCallOptions(), options)
    }

    @Test
    fun engineOptionsSelectTheirKeysAndBlankOnesAreDropped() {
        val options = paddle("""{"engineId":"paddle-ocr-pp-ocrv3","engine":" ","variant":"v3","imageQuality":80,"imageFormat":"jpeg"}""")
        assertEquals("paddle-ocr-pp-ocrv3", options.engineId)
        assertEquals(null, options.engine)
        assertEquals("v3", options.variant)
        assertEquals(80, options.extras[PaddleOcrOptionExtraKeys.IMAGE_QUALITY])
        assertEquals("jpeg", options.extras[PaddleOcrOptionExtraKeys.IMAGE_FORMAT])
    }

    @Test
    fun langAndModelStandInForTheProfile() {
        assertEquals("zh-CN", paddle("""{"lang":"zh-CN","model":"m"}""").profile)
        assertEquals("m", paddle("""{"model":"m"}""").profile)
        assertEquals("ja", paddle("""{"profile":"ja","lang":"zh-CN"}""").profile)
    }

    @Test
    fun nullCountsAsNotGiven() {
        assertEquals(paddle(), paddle("""{"useSlim":null,"cpuThreadNum":null,"scoreThreshold":null,"useCls":null}"""))
    }

    @Test
    fun jsonValuesCoerceAsInRhino() {
        val options = paddle("""{"useSlim":0,"useOpenCL":"yes","cpuThreadNum":"2","detLongSize":960.9,"useCls":{"a":1}}""")
        assertFalse(options.useSlim)
        assertTrue(options.useOpenCL)
        assertEquals(2, options.cpuThreadNum)
        assertEquals(960, options.detLongSize)
        assertEquals(true, options.extras[PaddleOcrOptionExtraKeys.USE_CLS])
    }

    @Test
    fun anInvalidNumberIsAnArgumentError() {
        assertThrows(IllegalArgumentException::class.java) { paddle("""{"cpuThreadNum":"many"}""") }
        assertThrows(IllegalArgumentException::class.java) { paddle("""{"scoreThreshold":{"a":1}}""") }
    }

    @Test
    fun bothRuntimesParseTheSameValues() {
        val rhino = nativeObject(
            "useSlim" to false, "cpuThreadNum" to 2.0, "detLongSize" to 960, "scoreThreshold" to 0.5,
            "useCls" to true, "detLimitSideLen" to 736, "mergeLine" to true, "variant" to "v3",
        )
        val json = """{"useSlim":false,"cpuThreadNum":2,"detLongSize":960,"scoreThreshold":0.5,"useCls":true,"detLimitSideLen":736,"mergeLine":true,"variant":"v3"}"""
        assertEquals(OcrCallOptions.paddle(rhino.toOcrOptionReader()), paddle(json))
        assertEquals(OcrCallOptions.rapid(rhino.toOcrOptionReader()), rapid(json))
        assertEquals(OcrCallOptions.paddle(nativeObject().toOcrOptionReader()), paddle())
        assertEquals(OcrCallOptions.rapid(nativeObject().toOcrOptionReader()), rapid())
    }

}
