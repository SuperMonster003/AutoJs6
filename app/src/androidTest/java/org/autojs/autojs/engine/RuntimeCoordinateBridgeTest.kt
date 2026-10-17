package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Exercises the JSON adapter on Android, where org.json is implemented. */
class RuntimeCoordinateBridgeTest {
    @Test
    fun callsUseLiveRotationAndExplicitBases() {
        var current = RuntimeCoordinateBridge.Display(1080, 2400, 0)
        val bridge = RuntimeCoordinateBridge { current }
        fun call(method: String, args: String = "[]") = bridge.invoke(method, JSONArray(args))
        assertEquals(1080.0, call("cX"))
        assertEquals(540.0, call("cX", "[0.5]"))
        assertEquals(675.0, call("cXy", "[0.5]"))
        assertEquals(960.0, call("cYx", "[0.5]"))
        assertEquals(810.0, call("cX", "[540,720,false]"))
        current = RuntimeCoordinateBridge.Display(2400, 1080, 1)
        assertEquals(2400.0, call("cX"))
        assertEquals(540.0, call("cY", "[0.5]"))
        assertEquals(960.0, call("cXy", "[0.5,\"16:9\"]"))
        assertEquals(675.0, call("cYx", "[0.5,\"16:9\"]"))
        assertEquals(480.0, call("cX", "[0.2,720,true]"))
        assertEquals(1, (call("getInfo") as JSONObject).getInt("rotation"))
    }

    @Test
    fun basesAreAtomicOneTimeAndIsolatedByExecution() {
        val a = RuntimeCoordinateBridge { RuntimeCoordinateBridge.Display(1080, 2400, 0) }
        val b = RuntimeCoordinateBridge { RuntimeCoordinateBridge.Display(1080, 2400, 0) }
        assertThrows(IllegalArgumentException::class.java) { a.invoke("setScaleBases", JSONArray("[1080,0]")) }
        a.invoke("setScaleBases", JSONArray("[1080,2400]"))
        assertEquals(1080, a.invoke("getScaleBaseX", JSONArray()))
        assertEquals(720, b.invoke("getScaleBaseX", JSONArray()))
        assertEquals(2400, (a.invoke("getScaleBases", JSONArray()) as JSONObject).getInt("y"))
        assertThrows(IllegalArgumentException::class.java) { a.invoke("setScaleBaseX", JSONArray("[720]")) }
        b.invoke("setScaleBaseX", JSONArray("[1080]"))
        assertThrows(IllegalArgumentException::class.java) { b.invoke("cXy", JSONArray("[0.5]")) }
        assertEquals(1200.0, b.invoke("cXy", JSONArray("[0.5,1]")))
    }

    @Test
    fun invalidArityModesAndBasesAreRejected() {
        val bridge = RuntimeCoordinateBridge { RuntimeCoordinateBridge.Display(1080, 2400, 0) }
        for ((method, args) in listOf(
            "cXy" to "[]", "cYx" to "[0.5,\"16:0\"]", "cX" to "[1,0,false]",
            "cX" to "[0.5,true,false]", "getInfo" to "[1]", "setScaleBases" to "[720]",
        )) assertThrows("$method $args", IllegalArgumentException::class.java) { bridge.invoke(method, JSONArray(args)) }
    }
}
