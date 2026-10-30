package org.autojs.autojs.runtime.api

import android.graphics.Rect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject

/**
 * The conversion rules of `OcrResult.extras` on plain values. Bundles (top-level and nested) need the Android runtime
 * and are covered by the instrumented `OcrResultExtrasBundleTest`.
 *
 * zh-CN: OcrResult.extras 转换规则的普通值用例. Bundle (顶层与嵌套) 需要 Android 运行时, 由插桩测试 OcrResultExtrasBundleTest 覆盖.
 */
class OcrResultExtrasTest {

    @Before
    fun setUp() {
        Context.enter()
        OcrResultExtras.warn = {}
    }

    @After
    fun tearDown() {
        Context.exit()
    }

    private fun normalize(vararg entries: Pair<String, Any?>): Map<String, Any?> {
        val values = mapOf(*entries)
        return OcrResultExtras.normalizeEntries(values.keys) { values[it] }
    }

    private fun scriptObject(vararg entries: Pair<String, Any?>) = OcrResultExtras.toScriptObject(normalize(*entries))

    private fun assertNumbers(expected: List<Double>, actual: Any?) {
        assertEquals(expected, (actual as NativeArray).map { (it as Number).toDouble() })
    }

    @Test
    fun scalarsKeepTheirMeaning() {
        val extras = normalize(
            "bool" to true,
            "string" to "cpu",
            "chars" to StringBuilder("ab"),
            "char" to 'c',
            "byte" to 1.toByte(),
            "short" to 2.toShort(),
            "int" to 3,
            "long" to 4L,
            "float" to 0.5f,
            "double" to 0.25,
            "none" to null,
        )
        assertEquals(true, extras["bool"])
        assertEquals("cpu", extras["string"])
        assertEquals("ab", extras["chars"])
        assertEquals("c", extras["char"])
        assertEquals(1, extras["byte"])
        assertEquals(2, extras["short"])
        assertEquals(3, extras["int"])
        assertEquals(4.0, extras["long"])
        assertEquals(0.5, extras["float"])
        assertEquals(0.25, extras["double"])
        assertTrue(extras.containsKey("none"))
        assertNull(extras["none"])
    }

    @Test
    fun arraysAndCollectionsBecomeArrays() {
        val extras = scriptObject(
            "floats" to floatArrayOf(1f, 2.5f),
            "ints" to intArrayOf(1, 2),
            "longs" to longArrayOf(3L),
            "doubles" to doubleArrayOf(0.5),
            "booleans" to booleanArrayOf(true),
            "strings" to arrayOf("a", "b"),
            "list" to listOf(1, "x", null),
        )
        assertNumbers(listOf(1.0, 2.5), extras["floats"])
        assertNumbers(listOf(1.0, 2.0), extras["ints"])
        assertNumbers(listOf(3.0), extras["longs"])
        assertNumbers(listOf(0.5), extras["doubles"])
        assertEquals(listOf(true), (extras["booleans"] as NativeArray).toList())
        assertEquals(listOf("a", "b"), (extras["strings"] as NativeArray).toList())
        assertEquals(listOf(1, "x", null), (extras["list"] as NativeArray).toList())
    }

    @Test
    fun nestedMapsBecomeObjects() {
        val extras = scriptObject("nested" to mapOf("deep" to mapOf("value" to 1), "list" to listOf(mapOf("x" to "y"))))
        val nested = extras["nested"] as NativeObject
        assertEquals(1, (nested["deep"] as NativeObject)["value"])
        assertEquals("y", ((nested["list"] as NativeArray)[0] as NativeObject)["x"])
    }

    @Test
    fun unsupportedValuesAreDropped() {
        val extras = normalize(
            "kept" to 1,
            "object" to Any(),
            "nonStringKeys" to mapOf(1 to 2),
            "list" to listOf(1, Any(), 2),
        )
        assertEquals(setOf("kept", "list"), extras.keys)
        assertEquals(listOf(1, 2), extras["list"])
    }

    @Test
    fun aFailingValueDropsOnlyItsKey() {
        val extras = OcrResultExtras.normalizeEntries(listOf("good", "bad")) {
            if (it == "bad") throw IllegalStateException("unreadable") else 1
        }
        assertEquals(mapOf("good" to 1), extras)
    }

    @Test
    fun emptyExtrasGiveAnEmptyObject() {
        assertTrue(OcrResultExtras.toScriptObject(emptyMap()).isEmpty())
        assertTrue(OcrResult("text", 1f, allocateRect()).extras.isEmpty())
    }

    @Test
    fun quadFollowsTheRegionOffset() {
        val extras = scriptObject("quad" to floatArrayOf(1f, 2f, 3f, 4f), "executionProvider" to "cpu")
        OcrResultExtras.offsetQuad(extras, 10, 20)
        assertNumbers(listOf(11.0, 22.0, 13.0, 24.0), extras["quad"])
        assertEquals("cpu", extras["executionProvider"])
        OcrResultExtras.offsetQuad(scriptObject("other" to 1), 10, 20)
    }

    @Test
    fun mergedLinesKeepTheValuesCommonToEveryPart() {
        val first = scriptObject(
            "quad" to floatArrayOf(1f, 2f),
            "executionProvider" to "nnapi",
            "same" to listOf(mapOf("a" to 1)),
            "onlyFirst" to 1,
        )
        val second = scriptObject(
            "quad" to floatArrayOf(3f, 4f),
            "executionProvider" to "nnapi",
            "same" to listOf(mapOf("a" to 1)),
        )
        val merged = OcrResultExtras.mergeCommon(listOf(first, second))
        assertEquals(setOf("executionProvider", "same"), merged.keys.toSet())
        assertEquals("nnapi", merged["executionProvider"])
        assertFalse(merged.containsKey("quad"))
    }

    @Test
    fun aLineOfOnePartKeepsItsExtras() {
        val part = scriptObject("quad" to floatArrayOf(1f, 2f))
        assertSame(part, OcrResultExtras.mergeCommon(listOf(part)))
        assertTrue(OcrResultExtras.mergeCommon(emptyList()).isEmpty())
    }

    @Test
    fun scriptsReadThePropertiesByName() {
        val scope = Context.getCurrentContext().initStandardObjects()
        ScriptableObject.putProperty(scope, "extras", scriptObject("executionProvider" to "cpu", "quad" to floatArrayOf(1f)))
        val text = Context.getCurrentContext().evaluateString(scope, "extras.executionProvider + ':' + extras.quad.length", "test", 1, null)
        assertEquals("cpu:1", Context.toString(text))
    }

    // android.graphics.Rect is a stub in JVM tests; OcrResult only stores it here.
    // zh-CN: JVM 测试里 android.graphics.Rect 是桩, 这里 OcrResult 只保存它.
    private fun allocateRect(): Rect = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").let { field ->
        field.isAccessible = true
        (field.get(null) as sun.misc.Unsafe).allocateInstance(Rect::class.java) as Rect
    }

}
