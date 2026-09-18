package org.autojs.autojs.runtime.api.mail

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MailScriptValuesTest {

    @Test
    fun blankAndInvalidTextParseToJsonNull() {
        listOf(null, "", "   ", "{not json", "[1,").forEach { json ->
            assertSame(json.toString(), JsonNull.INSTANCE, MailScriptValues.parse(json))
            assertNull(json.toString(), MailScriptValues.toPlain(json))
        }
    }

    @Test
    fun objectsKeepTheirKeyOrderAndNestedShapes() {
        val plain = MailScriptValues.toPlain("""{"z":1,"a":{"list":[true,null,"x",{"n":2}]},"m":null}""")
        val map = plain as LinkedHashMap<*, *>
        assertEquals(listOf("z", "a", "m"), map.keys.toList())
        assertEquals(1, map["z"])
        assertNull(map["m"])
        assertTrue(map.containsKey("m"))
        val list = (map["a"] as Map<*, *>)["list"] as List<*>
        assertEquals(true, list[0])
        assertNull(list[1])
        assertEquals("x", list[2])
        assertEquals(2, (list[3] as Map<*, *>)["n"])
    }

    @Test
    fun numbersBecomeIntWhenTheyFitAndDoubleOtherwise() {
        val list = MailScriptValues.toPlain("""[0,-7,2147483647,2147483648,-2147483649,1.5,1e3,12345678901234]""") as List<*>
        assertEquals(0, list[0])
        assertEquals(-7, list[1])
        assertEquals(Int.MAX_VALUE, list[2])
        assertEquals(2147483648.0, list[3])
        assertEquals(-2147483649.0, list[4])
        assertEquals(1.5, list[5])
        assertEquals(1000.0, list[6])
        assertEquals(12345678901234.0, list[7])
        assertTrue(list.take(3).all { it is Int })
        assertTrue(list.drop(3).all { it is Double })
    }

    @Test
    fun scalarsAndElementsConvertDirectly() {
        assertEquals("text", MailScriptValues.toPlain("\"text\""))
        assertEquals(false, MailScriptValues.toPlain("false"))
        assertNull(MailScriptValues.toPlain("null"))
        assertNull(MailScriptValues.toPlain(null as JsonElement?))
        assertEquals(listOf<Any?>(), MailScriptValues.toPlain(JsonParser.parseString("[]")))
        assertEquals(LinkedHashMap<String, Any?>(), MailScriptValues.toPlain(JsonParser.parseString("{}")))
    }
}
