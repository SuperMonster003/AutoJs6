package org.autojs.autojs.core.pref

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Probe for docs/settings/ROADMAP.md P1.0 / Q14: the JVM test classpath has a real org.json (`testImplementation`
 * org.json:json), not the android.jar stubs that throw `RuntimeException: Stub!`.
 *
 * zh-CN: ROADMAP P1.0 / Q14 的探针: JVM 测试类路径上的 org.json 是真实实现 (`testImplementation` org.json:json), 不是抛出
 * `RuntimeException: Stub!` 的 android.jar 桩.
 */
class OrgJsonProbeTest {

    @Test
    fun jsonObjectIsARealImplementation() {
        val text = JSONObject().put("k", 1).put("list", JSONArray().put("a")).toString()
        val parsed = JSONObject(text)
        assertEquals(1, parsed.getInt("k"))
        assertEquals("a", parsed.getJSONArray("list").getString(0))
    }

    @Test
    fun jsonObjectComesFromTheOrgJsonArtifact() {
        val location = JSONObject::class.java.protectionDomain?.codeSource?.location?.toString().orEmpty()
        assertTrue("org.json is loaded from $location", location.contains("json-20231013"))
    }
}
