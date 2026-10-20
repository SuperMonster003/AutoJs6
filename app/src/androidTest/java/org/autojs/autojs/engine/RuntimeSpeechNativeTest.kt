package org.autojs.autojs.engine

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

class RuntimeSpeechNativeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun call(bridge: RuntimeSpeechBridge, method: String, vararg args: Any?): NodeBridgeResponse {
        var response: NodeBridgeResponse? = null
        bridge.dispatch(NodeBridgeRequest(UUID.randomUUID().toString(), "speech", method, JSONArray(args.toList()).toString(), 5000)) { response = it }
        return requireNotNull(response)
    }
    private fun value(bridge: RuntimeSpeechBridge, method: String, vararg args: Any?): Any {
        val response = call(bridge, method, *args)
        assertTrue(response.toString(), response.ok)
        return org.json.JSONTokener(response.resultJson!!).nextValue()
    }
    @Test fun pcmUploadsStayWithTheirClientAndDisappearOnClose() {
        val before = context.cacheDir.listFiles().orEmpty().map { it.name }.toSet()
        RuntimeSpeechBridge(context, context.cacheDir.path).use { bridge ->
            val first = (value(bridge, "open") as JSONObject).getString("id")
            val second = (value(bridge, "open") as JSONObject).getString("id")
            val upload = (value(bridge, "uploadBegin", first, 4) as JSONObject).getString("id")
            assertEquals(true, value(bridge, "uploadChunk", first, upload, 0, Base64.encodeToString(byteArrayOf(0, 1, 2, 3), Base64.NO_WRAP)))
            assertEquals(false, value(bridge, "uploadDiscard", second, upload))
            assertFalse(call(bridge, "uploadChunk", second, upload, 0, "AAA=").ok)
            assertFalse(call(bridge, "uploadBegin", first, 3).ok)
            assertEquals(true, value(bridge, "close", first))
            assertFalse(call(bridge, "poll", first, JSONArray()).ok)
            assertEquals(false, value(bridge, "close", first))
        }
        val remaining = context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("runtime-speech-") && it.name !in before }
        assertTrue("PCM staging left behind: $remaining", remaining.isEmpty())
    }
    @Test fun nativeArgumentFailuresKeepTheirSpeechCodeAcrossFragmentedPolling() {
        RuntimeSpeechBridge(context, context.cacheDir.path).use { bridge ->
            val client = (value(bridge, "open") as JSONObject).getString("id")
            val op = (value(bridge, "start", client, "models.getDefault", JSONArray().put("not-a-kind")) as JSONObject).getString("id")
            val result = ByteArrayOutputStream()
            val end = System.currentTimeMillis() + 5000
            var event: JSONObject? = null
            while (System.currentTimeMillis() < end && event == null) {
                val batch = value(bridge, "poll", client, JSONArray().put(op)) as JSONArray
                for (i in 0 until batch.length()) {
                    val part = batch.getJSONObject(i)
                    result.write(Base64.decode(part.getString("data"), Base64.DEFAULT))
                    if (part.getBoolean("last")) event = JSONObject(result.toString("UTF-8"))
                }
                if (event == null) Thread.sleep(10)
            }
            assertNotNull(event)
            assertEquals("reject", event!!.getString("type"))
            assertEquals("INVALID_OPTIONS", event.getJSONObject("value").getString("code"))
        }
    }
}
