package org.autojs.autojs.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RuntimeOperationClientsTest {

    private class Session(val closed: AtomicBoolean) : Closeable {
        override fun close() = closed.set(true)
    }

    private class Fake : RuntimeOperationClients<Session>("fake", "FAKE") {
        val sessionClosed = AtomicBoolean(false)
        val cancelled = ArrayList<String>()
        val release = CompletableDeferred<Unit>()
        override val operations = setOf("echo", "large", "hold", "fail", "expire")
        override fun newSession() = Session(sessionClosed)
        override fun errorOf(error: Throwable): Pair<String, String> = "BROKEN" to (error.message ?: error.toString())
        override fun onCancel(client: Client, id: String) { cancelled += id }
        override suspend fun invoke(client: Client, id: String, method: String, args: JSONArray): Any? = when (method) {
            "echo" -> args.getString(0)
            "large" -> "x".repeat(40 * 1024)
            "hold" -> { client.emit(id, "event", mapOf("type" to "started")); awaitCancellation() }
            "fail" -> throw IllegalStateException("refused")
            "expire" -> withTimeout(1) { awaitCancellation() }
            else -> throw IllegalArgumentException(method)
        }
    }

    private fun Fake.call(method: String, vararg args: Any?): NodeBridgeResponse {
        var answer: NodeBridgeResponse? = null
        dispatch(NodeBridgeRequest("1", "fake", method, JSONArray(args.toList()).toString(), 1000L)) { answer = it }
        return answer!!
    }

    private fun NodeBridgeResponse.string(key: String) = JSONObject(resultJson!!).getString(key)

    private fun Fake.open() = call("open").string("id")

    private fun Fake.start(client: String, method: String, vararg args: Any?) = call("start", client, method, JSONArray(args.toList())).string("id")

    /** Polls until [id] delivered a whole message; [parts] receives the size of each poll. */
    private fun Fake.message(client: String, id: String, parts: MutableList<Int> = ArrayList()): JSONObject {
        val bytes = java.io.ByteArrayOutputStream()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val answered = JSONArray(call("poll", client, JSONArray(listOf(id))).resultJson!!)
            if (answered.length() > 0) parts += answered.length() else Thread.sleep(5)
            for (index in 0 until answered.length()) {
                val part = answered.getJSONObject(index)
                assertEquals(id, part.getString("id"))
                bytes.write(Base64.getDecoder().decode(part.getString("data")))
                if (part.getBoolean("last")) return JSONObject(bytes.toString(Charsets.UTF_8))
            }
        }
        throw AssertionError("no message for $id")
    }

    @Test fun operationsResolveAndRejectThroughPoll() {
        val fake = Fake()
        val client = fake.open()
        val resolved = fake.message(client, fake.start(client, "echo", "hello"))
        assertEquals("resolve", resolved.getString("type"))
        assertEquals("hello", resolved.getString("value"))
        val rejected = fake.message(client, fake.start(client, "fail"))
        assertEquals("reject", rejected.getString("type"))
        assertEquals("BROKEN", rejected.getJSONObject("value").getString("code"))
        assertEquals("refused", rejected.getJSONObject("value").getString("message"))
    }

    @Test fun aLiveJobsTimeoutIsAFailure() {
        val fake = Fake()
        val client = fake.open()
        assertEquals("reject", fake.message(client, fake.start(client, "expire")).getString("type"))
    }

    @Test fun largeResultsArriveInBoundedFragments() {
        val fake = Fake()
        val client = fake.open()
        val parts = ArrayList<Int>()
        assertEquals(40 * 1024, fake.message(client, fake.start(client, "large"), parts).getString("value").length)
        assertEquals(listOf(3, 1), parts)
    }

    @Test fun cancelEndsTheOperationAndForgetsItsEvents() {
        val fake = Fake()
        val client = fake.open()
        val id = fake.start(client, "hold")
        assertEquals("event", fake.message(client, id).getString("type"))
        assertTrue(fake.call("cancel", client, id).resultJson.toBoolean())
        assertEquals(listOf(id), fake.cancelled)
        assertEquals("[]", fake.call("poll", client, JSONArray(listOf(id))).resultJson)
    }

    @Test fun closingClosesTheSessionAndRefusesLaterCalls() {
        val fake = Fake()
        val client = fake.open()
        assertTrue(fake.call("close", client).resultJson.toBoolean())
        assertTrue(fake.sessionClosed.get())
        val late = fake.call("start", client, "echo", JSONArray())
        assertNotNull(late.error)
        assertEquals("ERR_AUTOJS6_FAKE_BROKEN", late.error!!.code)
        fake.close()
        assertEquals("ERR_AUTOJS6_FAKE_BROKEN", fake.call("open").error!!.code)
    }

    @Test fun unknownOperationsAreRefused() {
        val fake = Fake()
        val response = fake.call("start", fake.open(), "unknown", JSONArray())
        assertTrue(response.error!!.message!!.contains("Unknown fake operation"))
    }
}
