package org.autojs.autojs.engine

import org.autojs.autojs.core.console.ConsoleInputQueue
import org.autojs.autojs.core.console.ConsoleInputRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// api-coverage P4.3 (D-7, Q165 (a)): the rawInput / rawInputPoll / rawInputCancel routes of the Bun host client's console.rawInput.
class BunConsoleInputTest {

    private val queued = mutableListOf<ConsoleInputRequest>()
    private val input = BunConsoleInput { callback ->
        ConsoleInputQueue.newRequest(this).also { request -> request.whenComplete(callback); queued += request }
    }

    private fun start() = input.invoke(BunConsoleInput.START, JSONArray()) as String
    private fun poll(id: String) = input.invoke(BunConsoleInput.POLL, JSONArray().put(id)) as JSONObject

    @Test fun pollAnswersTheSubmittedLineOnce() {
        val id = start()
        assertFalse(poll(id).getBoolean("done"))
        queued.single().complete("1 + 1")
        poll(id).let { assertTrue(it.getBoolean("done")); assertEquals("1 + 1", it.getString("text")) }
        assertThrows(IllegalArgumentException::class.java) { poll(id) }
    }

    @Test fun cancelledRequestsAnswerANullLine() {
        val first = start()
        val second = start()
        assertTrue(input.invoke(BunConsoleInput.CANCEL, JSONArray().put(first)) as Boolean)
        assertTrue(queued[0].isDone)
        assertFalse(input.invoke(BunConsoleInput.CANCEL, JSONArray().put(first)) as Boolean)
        queued[1].cancel()
        poll(second).let { assertTrue(it.getBoolean("done")); assertTrue(it.has("text") && it.isNull("text")) }
    }

    @Test fun boundsTheWaitingRequestsAndTheArguments() {
        repeat(BunConsoleInput.MAX_PENDING) { start() }
        assertThrows(IllegalArgumentException::class.java) { start() }
        assertThrows(IllegalArgumentException::class.java) { input.invoke(BunConsoleInput.START, JSONArray().put("prompt")) }
        assertThrows(IllegalArgumentException::class.java) { input.invoke(BunConsoleInput.POLL, JSONArray().put(1)) }
        assertThrows(IllegalArgumentException::class.java) { input.invoke("rawInputAll", JSONArray()) }
    }

    @Test fun closeCancelsTheWaitingRequestsAndRefusesNewOnes() {
        start()
        start()
        input.close()
        assertTrue(queued.all { it.isDone })
        assertThrows(IllegalStateException::class.java) { start() }
    }

}
