package org.autojs.autojs.engine

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

class RuntimeEpubNativeTest {
    @Test fun invalidPathsAndClosedClientHandlesRetainTheEpubErrorCode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        RuntimeEpubBridge(context, context.cacheDir.path).use { bridge ->
            fun call(method: String, vararg args: Any?): NodeBridgeResponse {
                var result: NodeBridgeResponse? = null
                bridge.dispatch(NodeBridgeRequest(UUID.randomUUID().toString(), "epub", method,
                    JSONArray(args.toList()).toString(), 5000)) { result = it }
                return requireNotNull(result)
            }
            fun value(method: String, vararg args: Any?): Any {
                val result = call(method, *args)
                assertTrue(result.toString(), result.ok)
                return org.json.JSONTokener(result.resultJson!!).nextValue()
            }
            val first = (value("open") as JSONObject).getString("id")
            val second = (value("open") as JSONObject).getString("id")
            val job = (value("start", first, "openBook", JSONArray().put("content://invalid/book")) as JSONObject).getString("id")
            val bytes = ByteArrayOutputStream()
            var event: JSONObject? = null
            val deadline = System.currentTimeMillis() + 5000
            while (event == null && System.currentTimeMillis() < deadline) {
                assertEquals(0, (value("poll", second, JSONArray().put(job)) as JSONArray).length())
                val batch = value("poll", first, JSONArray().put(job)) as JSONArray
                for (index in 0 until batch.length()) {
                    val part = batch.getJSONObject(index)
                    bytes.write(Base64.decode(part.getString("data"), Base64.DEFAULT))
                    if (part.getBoolean("last")) event = JSONObject(bytes.toString("UTF-8"))
                }
                if (event == null) Thread.sleep(10)
            }
            assertNotNull(event)
            assertEquals("reject", event!!.getString("type"))
            assertEquals("INVALID_ARGUMENT", event.getJSONObject("value").getString("code"))
            assertEquals(true, value("close", first))
            assertEquals(false, value("close", first))
            val closed = call("poll", first, JSONArray())
            assertFalse(closed.ok)
            assertTrue(closed.toString().contains("SESSION_CLOSED"))
            assertEquals(0, (value("poll", second, JSONArray()) as JSONArray).length())
        }
    }
}
