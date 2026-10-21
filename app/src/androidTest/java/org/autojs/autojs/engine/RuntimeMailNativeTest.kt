package org.autojs.autojs.engine

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class RuntimeMailNativeTest {
    @Test fun cancelledTokenRequestsLeaveNoUndrainableTerminalPackets() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        RuntimeMailBridge(context, context.cacheDir.path).use { bridge ->
            fun call(method: String, vararg args: Any?): Any {
                var response: NodeBridgeResponse? = null
                bridge.dispatch(NodeBridgeRequest(UUID.randomUUID().toString(), "mail", method,
                    JSONArray(args.toList()).toString(), 5000)) { response = it }
                assertTrue(response.toString(), response!!.ok)
                return org.json.JSONTokener(response!!.resultJson!!).nextValue()
            }
            val ownerId = (call("open") as JSONObject).getString("id")
            val id = (call("start", ownerId, "connect", JSONArray()
                .put(JSONObject().put("address", "runtime@example.invalid").put("auth", "xoauth2"))
                .put(true)) as JSONObject).getString("id")
            val deadline = System.currentTimeMillis() + 5000
            var requested = false
            while (!requested && System.currentTimeMillis() < deadline) {
                requested = (call("poll", ownerId, JSONArray().put(id)) as JSONArray).length() > 0
                if (!requested) Thread.sleep(5)
            }
            assertTrue("Token request must be suspended before cancellation", requested)
            // Observe actual completion, not a timing sleep: the terminal packet used to be queued in catch.
            val ownersField = RuntimeMailBridge::class.java.getDeclaredField("owners").apply { isAccessible = true }
            val owner = (ownersField.get(bridge) as Map<*, *>)[ownerId]!!
            val lock = owner.javaClass.getDeclaredField("lock").apply { isAccessible = true }.get(owner)
            val jobs = owner.javaClass.getDeclaredField("jobs").apply { isAccessible = true }.get(owner) as Map<*, *>
            val job = synchronized(lock) { jobs[id] as Job }
            assertEquals(true, call("cancel", ownerId, id))
            withTimeout(5000) { job.join() }
            assertEquals("Cancelled operations have no reader for terminal packets", 0,
                (call("poll", ownerId, JSONArray().put(id)) as JSONArray).length())
            assertEquals(true, call("close", ownerId))
        }
    }

    @Test fun invalidAccountsAndClosedClientHandlesRetainTheMailErrorCode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        RuntimeMailBridge(context, context.cacheDir.path).use { bridge ->
            fun call(method: String, vararg args: Any?): NodeBridgeResponse {
                var result: NodeBridgeResponse? = null
                bridge.dispatch(NodeBridgeRequest(UUID.randomUUID().toString(), "mail", method,
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
            val job = (value("start", first, "connect", JSONArray().put(JSONObject().put("address", "invalid"))) as JSONObject).getString("id")
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
