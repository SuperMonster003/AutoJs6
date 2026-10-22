package org.autojs.autojs.core.pref

import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.util.LinkedList

/**
 * [SettingsJson] (docs/settings/ROADMAP.md P1.1, T6, appendix C Q14): plain values, `JSONObject.NULL`, number
 * narrowing (the JVM org.json artifact returns `BigDecimal` / `BigInteger` where Android returns `Double` / `Long`),
 * strict syntax and the Gson compatible text of `Pref`'s list values (P1.4, R1).
 *
 * zh-CN: [SettingsJson] 的测试 (ROADMAP P1.1, T6, 附录 C Q14): 普通值, `JSONObject.NULL`, 数字收窄 (JVM 的 org.json 构件在
 * Android 返回 Double / Long 的地方返回 BigDecimal / BigInteger), 严格语法, 以及 `Pref` 列表值与 Gson 一致的文本.
 */
class SettingsJsonTest {

    @Test
    fun parsesPlainValuesAndKeepsKeyOrder() {
        val value = SettingsJson.parse("""{"z": 1, "a": [true, "x", {"k": false}], "m": {}}""")
        assertEquals(mapOf("z" to 1, "a" to listOf(true, "x", mapOf("k" to false)), "m" to emptyMap<String, Any?>()), value)
        assertEquals(listOf("z", "a", "m"), (value as Map<*, *>).keys.toList())
        assertEquals(" \u00e9\u4e2d/\"\\\n", SettingsJson.parse("\"\\u0020\u00e9\\u4e2d\\/\\\"\\\\\\n\""))
    }

    @Test
    fun jsonNullBecomesKotlinNull() {
        // Both org.json variants give the NULL sentinel for a JSON null; SettingsJson never lets it out.
        assertSame(JSONObject.NULL, JSONObject("""{"a": null}""").get("a"))
        assertNull(SettingsJson.parse("null"))
        assertEquals(listOf(null, 1), SettingsJson.parse("[null, 1]"))
        val map = SettingsJson.parse("""{"a": null}""") as Map<*, *>
        assertTrue(map.containsKey("a"))
        assertNull(map["a"])
        assertNull(SettingsJson.normalize(JSONObject.NULL))
        assertEquals(mapOf("a" to null, "b" to listOf(null)), SettingsJson.normalize(JSONObject().put("a", JSONObject.NULL).put("b", JSONArray().put(JSONObject.NULL))))
        assertTrue(SettingsJson.isNull(JSONObject.NULL))
        assertTrue(SettingsJson.isNull(null))
        assertFalse(SettingsJson.isNull("null"))
        assertEquals("null", SettingsJson.stringify(JSONObject.NULL))
        assertEquals("""{"a":null,"b":[null]}""", SettingsJson.stringify(mapOf("a" to JSONObject.NULL, "b" to listOf(null))))
    }

    @Test
    fun integralNumbersNarrowToIntThenLongThenDouble() {
        assertTyped(1, SettingsJson.parse("1"))
        assertTyped(Int.MAX_VALUE, SettingsJson.parse("2147483647"))
        assertTyped(Int.MIN_VALUE, SettingsJson.parse("-2147483648"))
        assertTyped(2147483648L, SettingsJson.parse("2147483648"))
        assertTyped(-2147483649L, SettingsJson.parse("-2147483649"))
        assertTyped(Long.MAX_VALUE, SettingsJson.parse("9223372036854775807"))
        assertTyped(9.223372036854775808E18, SettingsJson.parse("9223372036854775808"))
        // Android parses "-0" as the int 0 and the JVM artifact as the double -0.0; SettingsJson as the int 0 on both.
        assertTyped(0, SettingsJson.parse("-0"))
    }

    @Test
    fun fractionsAndExponentsAreDoubles() {
        // The JVM artifact's own tokenizer returns BigDecimal here, Android's returns Double.
        assertTrue(JSONTokener("1.5").nextValue() is BigDecimal)
        assertTyped(1.5, SettingsJson.parse("1.5"))
        assertTyped(1.0, SettingsJson.parse("1.0"))
        assertTyped(100.0, SettingsJson.parse("1e2"))
        assertTyped(-0.0, SettingsJson.parse("-0.0"))
        assertTyped(0.001, SettingsJson.parse("1E-3"))
        assertMalformed("1e400")
        assertMalformed("01")
        assertMalformed("1.")
        assertMalformed(".5")
    }

    @Test
    fun normalizeNarrowsOrgJsonAndJavaNumbers() {
        assertTyped(1.5, SettingsJson.normalize(BigDecimal("1.5")))
        assertTyped(2, SettingsJson.normalize(BigInteger.valueOf(2)))
        assertTyped(5000000000L, SettingsJson.normalize(BigInteger.valueOf(5000000000L)))
        assertTyped(1.0E20, SettingsJson.normalize(BigInteger.TEN.pow(20)))
        assertTyped(2, SettingsJson.normalize(2L))
        assertTyped(0.1, SettingsJson.normalize(0.1f))
        assertEquals(mapOf("a" to 1.5, "b" to listOf<Any>(1, 3000000000L)), SettingsJson.normalize(JSONObject("""{"a": 1.5, "b": [1, 3000000000]}""")))
    }

    @Test
    fun typedNarrowingForSettingValues() {
        assertEquals(2, SettingsJson.toInt(2))
        assertEquals(2, SettingsJson.toInt(2.0))
        assertEquals(0, SettingsJson.toInt(-0.0))
        assertNull(SettingsJson.toInt(2.5))
        assertNull(SettingsJson.toInt(2147483648L))
        assertNull(SettingsJson.toInt("1"))
        assertNull(SettingsJson.toInt(true))
        assertEquals(2147483648L, SettingsJson.toLong(2147483648L))
        assertEquals(3L, SettingsJson.toLong(BigDecimal("3")))
        assertNull(SettingsJson.toLong(9.3E18))
        assertNull(SettingsJson.toLong(BigInteger.TEN.pow(20)))
        assertEquals(0.1f, SettingsJson.toFloat(0.1))
        assertEquals(3f, SettingsJson.toFloat(3))
        assertNull(SettingsJson.toFloat(1.0E39))
        assertNull(SettingsJson.toFloat("0.1"))
        assertEquals(1.5, SettingsJson.toDouble(BigDecimal("1.5"))!!, 0.0)
    }

    @Test
    fun stringifyIsCompactAndPlatformIndependent() {
        val value = linkedMapOf("b" to 1, "a" to listOf(true, null, 2.5, 3000000000L, 0.1f), "s" to "/x<y>&='\"\\\n\t\u0001\u2028")
        val text = SettingsJson.stringify(value)
        assertEquals("""{"b":1,"a":[true,null,2.5,3000000000,0.1],"s":"/x<y>&='\"\\\n\t\u0001\u2028"}""", text)
        assertEquals("""{"b":1,"a":[true,null,2.5,3000000000,0.1],"s":"/x\u003cy\u003e\u0026\u003d\u0027\"\\\n\t\u0001\u2028"}""", SettingsJson.stringify(value, htmlSafe = true))
        assertEquals(SettingsJson.normalize(value), SettingsJson.parse(text))
        assertEquals("[\"a\",\"b\"]", SettingsJson.stringify(linkedSetOf("a", "b")))
        assertEquals("[1,2]", SettingsJson.stringify(arrayOf(1, 2)))
        assertEquals("{}", SettingsJson.stringify(JSONObject()))
    }

    @Test
    fun parseMapReadsTheFilesThatOrgJsonWrote() {
        // Android's JSONObject.toString escapes "/"; the file-based JSON stores read such files with parseMap (P5.1 / P5.3).
        val text = """{"path":"\/sdcard\/Scripts","n":1,"l":3000000000,"d":2.5,"b":true,"z":null,"a":[1,"x",{"k":false}],"o":{},"q":"say \"hi\"\\"}"""
        val map = SettingsJson.parseMap(text)
        assertEquals(listOf("path", "n", "l", "d", "b", "z", "a", "o", "q"), map.keys.toList())
        assertEquals("/sdcard/Scripts", map["path"])
        assertTyped(1, map["n"])
        assertTyped(3000000000L, map["l"])
        assertTyped(2.5, map["d"])
        assertTyped(true, map["b"])
        assertTrue(map.containsKey("z"))
        assertNull(map["z"])
        assertEquals(listOf(1, "x", mapOf("k" to false)), map["a"])
        assertEquals(emptyMap<String, Any?>(), map["o"])
        assertEquals("say \"hi\"\\", map["q"])
        // The values of org.json's own reader; written back, they read the same again.
        assertEquals(SettingsJson.normalize(JSONObject(text)), map)
        assertEquals(map, SettingsJson.parseMap(SettingsJson.stringify(map)))
        listOf("[1]", "\"text\"", "null", "{\"a\":1,\"a\":2}", "{\"a\":1} x", "{a:1}", "").forEach { malformed ->
            try {
                SettingsJson.parseMap(malformed)
                fail("parseMap should reject \"$malformed\"")
            } catch (_: SettingsJson.SettingsJsonException) {
                // expected
            }
        }
    }

    @Test
    fun toOrgJsonIsTheInverseOfNormalize() {
        val value = linkedMapOf<String, Any?>("a" to listOf(1, null, linkedMapOf("b" to 2.5)), "n" to null, "s" to "x", "l" to 3000000000L)
        val obj = SettingsJson.toOrgJson(value) as JSONObject
        assertSame(JSONObject.NULL, obj.get("n"))
        assertSame(JSONObject.NULL, obj.getJSONArray("a").get(1))
        assertTyped(2.5, obj.getJSONArray("a").getJSONObject(2).get("b"))
        assertTyped(3000000000L, obj.get("l"))
        assertEquals(value, SettingsJson.normalize(obj))
        assertTrue(SettingsJson.toOrgJson(listOf(1)) is JSONArray)
        assertSame(JSONObject.NULL, SettingsJson.toOrgJson(null))
        assertEquals("x", SettingsJson.toOrgJson("x"))
        try {
            SettingsJson.toOrgJson(mapOf(1 to "a"))
            fail("toOrgJson should reject keys that are not strings")
        } catch (_: SettingsJson.SettingsJsonException) {
            // expected
        }
    }

    @Test
    fun parseMapGivesMutablePlainMaps() {
        // P5.3: the file-based stores mutate the records they read, nested members included.
        val map = SettingsJson.parseMap("""{"b": {"c": [1]}, "a": null}""")
        assertEquals(listOf("b", "a"), map.keys.toList())
        assertTrue(map.containsKey("a"))
        map["d"] = 2
        @Suppress("UNCHECKED_CAST")
        ((map["b"] as MutableMap<String, Any?>)["c"] as MutableList<Any?>).add(2)
        assertEquals("""{"b":{"c":[1,2]},"a":null,"d":2}""", SettingsJson.stringify(map))
        assertThrows(SettingsJson.SettingsJsonException::class.java) { SettingsJson.parseMap("[1]") }
        assertThrows(SettingsJson.SettingsJsonException::class.java) { SettingsJson.parseMap("null") }
    }

    @Test
    fun orgJsonObjectsConvertToAndFromPlainMaps() {
        val map = SettingsJson.normalizeObject(JSONObject().put("n", 1L).put("x", JSONObject.NULL).put("a", JSONArray().put("s")))
        assertEquals(mapOf("n" to 1, "x" to null, "a" to listOf("s")), map)
        map["m"] = 3
        val back = SettingsJson.toOrgObject(map)
        assertSame(JSONObject.NULL, back.get("x"))
        assertEquals(3, back.getInt("m"))
        assertEquals("s", back.getJSONArray("a").getString(0))
    }

    @Test
    fun mapAccessorsReadLikeOrgJsonOpt() {
        val map = SettingsJson.parseMap(
            """{"s": "t", "i": 7, "l": 9007199254740993, "d": 2.5, "b": true, "bs": "TRUE", "ns": " 12", "fs": "3.9", "z": null, "o": {"k": 1}, "a": [1, "x"]}""",
        )
        assertEquals("t", map.optString("s"))
        assertEquals("7", map.optString("i"))
        assertEquals("2.5", map.optString("d"))
        assertEquals("true", map.optString("b"))
        assertEquals("""{"k":1}""", map.optString("o"))
        assertEquals("", map.optString("z"))
        assertEquals("f", map.optString("missing", "f"))
        assertEquals(9007199254740993L, map.optLong("l"))
        assertEquals(7L, map.optLong("i"))
        assertEquals(2L, map.optLong("d"))
        assertEquals(12L, map.optLong("ns"))
        assertEquals(3L, map.optLong("fs"))
        assertEquals(-1L, map.optLong("s", -1L))
        assertEquals(7, map.optInt("i"))
        assertEquals(3, map.optInt("fs"))
        assertEquals(0, map.optInt("z"))
        assertEquals(2.5, map.optDouble("d"), 0.0)
        assertTrue(map.optDouble("s").isNaN())
        assertTrue(map.optBoolean("b"))
        assertTrue(map.optBoolean("bs"))
        assertFalse(map.optBoolean("s"))
        assertTrue(map.optBoolean("missing", true))
        assertEquals(mapOf("k" to 1), map.optObject("o"))
        assertNull(map.optObject("a"))
        assertEquals(listOf(1, "x"), map.optArray("a"))
        assertNull(map.optArray("o"))
    }

    @Test
    fun putOrRemoveIsOrgJsonPut() {
        val map = SettingsJson.parseMap("""{"a": 1, "b": 2}""")
        map.putOrRemove("a", null).putOrRemove("c", "x")
        assertEquals("""{"b":2,"c":"x"}""", SettingsJson.stringify(map))
    }

    @Test
    fun indentedTextIsLaidOutLikeAndroidOrgJson() {
        val value = linkedMapOf("a" to 1, "b" to listOf(1, emptyList<Any>(), emptyMap<String, Any>()), "c" to emptyMap<String, Any>(), "d" to linkedMapOf("e" to "/"))
        val expected = listOf(
            "{",
            "  \"a\": 1,",
            "  \"b\": [",
            "    1,",
            "    [],",
            "    {}",
            "  ],",
            "  \"c\": {},",
            "  \"d\": {",
            "    \"e\": \"/\"",
            "  }",
            "}",
        ).joinToString("\n")
        assertEquals(expected, SettingsJson.stringify(value, indent = 2))
        assertEquals(SettingsJson.normalize(value), SettingsJson.parse(expected))
        assertEquals("{}", SettingsJson.stringify(emptyMap<String, Any>(), indent = 2))
        assertEquals(listOf("[", "    true", "]").joinToString("\n"), SettingsJson.stringify(listOf(true), indent = 4))
        assertEquals("1", SettingsJson.stringify(1, indent = 2))
        assertThrows(IllegalArgumentException::class.java) { SettingsJson.stringify(1, indent = -1) }
    }

    @Test
    fun stringListTextIsByteForByteGson() {
        val samples = listOf(
            listOf("/storage/emulated/0/Scripts", "/sdcard/Download"),
            listOf("192.168.1.1:9317", "a=b&c<d>e'f", "quote\" back\\slash", "tab\tnew\nline\r", "\u0000\u001f\u007f", "\u2028\u2029", "\ud83d\ude00 \u4e2d\u6587"),
            listOf(""),
            emptyList(),
            listOf("{\"path\":\"/sdcard\",\"scrollY\":12}"),
        )
        samples.forEach { list ->
            val gsonSet = Gson().toJson(LinkedHashSet(list))
            val gsonList = Gson().toJson(LinkedList(list))
            assertEquals(gsonSet, SettingsJson.encodeStringList(LinkedHashSet(list)))
            assertEquals(gsonList, SettingsJson.encodeStringList(LinkedList(list)))
            // Old text read by the new reader, new text read by the old reader (a downgrade keeps working).
            assertEquals(list, SettingsJson.decodeStringList(gsonList))
            assertEquals(list, Gson().fromJson(SettingsJson.encodeStringList(list), LinkedList<String>().javaClass).toList())
        }
    }

    @Test
    fun stringListDecodingDropsNullsAndKeepsScalarText() {
        assertEquals(listOf("a", "1", "true", "2.5"), SettingsJson.decodeStringList("""["a", null, 1, true, 2.5]"""))
        assertMalformedList("""{"a": 1}""")
        assertMalformedList("""[["nested"]]""")
        assertMalformedList("\"text\"")
    }

    @Test
    fun malformedTextIsRejected() {
        listOf(
            "", " ", "[", "[1,]", "[1 2]", "{\"a\" 1}", "{\"a\":1,}", "{a:1}", "{'a':1}", "['a']", "[abc]", "[True]",
            "{\"a\":1,\"a\":2}", "[1] x", "// c\n[1]", "[NaN]",
        ).forEach(::assertMalformed)
        assertEquals(listOf(1), SettingsJson.parse("\uFEFF [ 1 ] \n"))
    }

    @Test
    fun unsupportedValuesAreNotWritten() {
        listOf<Any>(Double.NaN, Float.POSITIVE_INFINITY, mapOf(1 to "a"), Any()).forEach { value ->
            try {
                SettingsJson.stringify(value)
                fail("stringify should reject $value")
            } catch (_: SettingsJson.SettingsJsonException) {
                // expected
            }
        }
    }

    private fun assertTyped(expected: Any, actual: Any?) {
        assertEquals(expected, actual)
        assertEquals(expected.javaClass, actual?.javaClass)
    }

    private fun assertMalformed(text: String) {
        try {
            SettingsJson.parse(text)
            fail("parse should reject \"$text\"")
        } catch (_: SettingsJson.SettingsJsonException) {
            // expected
        }
    }

    private fun assertMalformedList(text: String) {
        try {
            SettingsJson.decodeStringList(text)
            fail("decodeStringList should reject \"$text\"")
        } catch (_: SettingsJson.SettingsJsonException) {
            // expected
        }
    }
}
