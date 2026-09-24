package org.autojs.autojs.core.plugin.agent

import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class AiAgentRunRecordTest {
    private fun record(interaction: String = "script") = AiAgentRunRecord("00000000-0000-0000-0000-000000000001", "test", 10, false, interaction)
    private fun event(r: AiAgentRunRecord, n: Int, type: String) = JsonObject().apply { addProperty("runId", r.id); addProperty("sequence", n); addProperty("type", type) }
    @Test fun duplicatesOutOfOrderAndForeignRunEventsAreIgnored() {
        val r=record(); val got=mutableListOf<String>(); r.observe { got.add(it["type"].asString) }
        r.accept(event(r, 2, "state").apply { addProperty("to", "running") })
        r.accept(event(r, 2, "progress")); r.accept(event(r, 1, "progress"))
        r.accept(event(r, 3, "progress").apply { addProperty("runId", "other") })
        assertEquals(listOf("state"), got); assertEquals("running", r.state)
    }
    @Test fun taskFailureIsAResolvedResultAndLateEventsCannotChangeIt() {
        val r=record(); val got=mutableListOf<String>(); r.observe { got.add(it["type"].asString) }
        r.accept(event(r, 1, "state").apply { addProperty("to", "failed") })
        r.accept(event(r, 2, "error").apply { addProperty("code", "MODEL_FAILED") })
        r.accept(event(r, 3, "done").apply { addProperty("status", "failed"); addProperty("summary", "model failed") })
        r.accept(event(r, 4, "state").apply { addProperty("to", "running") })
        r.fail("LINK_DETACHED", "late")
        assertEquals(listOf("state", "error", "done"), got); assertNull(r.failure)
        assertEquals("failed", r.result!!["status"].asString)
        r.result!!.addProperty("status", "completed"); assertEquals("failed", r.result!!["status"].asString)
    }
    @Test fun linkLossIsAHandleRejectionWithAStableTerminalState() {
        val r=record(); r.fail("PLUGIN_UNAVAILABLE", "unavailable", "drawer")
        assertTrue(r.settled); assertEquals("failed", r.state); assertNull(r.result)
        assertEquals("drawer", r.failure!!["hint"].asString)
        r.accept(event(r, 1, "done").apply { addProperty("status", "completed") }); assertNull(r.result)
    }
    @Test fun reattachmentReplaysCurrentPendingAndClosingObserverDoesNotStopDetachedRun() {
        val r=record(); r.accept(event(r, 1, "state").apply { addProperty("to", "waiting_input") })
        r.accept(event(r, 2, "input").apply { addProperty("requestId", "q1"); addProperty("kind", "text") })
        val first=mutableListOf<String>(); val sub=r.observe { first.add(it["type"].asString) }; sub.close()
        assertEquals(listOf("state", "input"), first)
        r.accept(event(r, 3, "state").apply { addProperty("to", "running") })
        assertEquals(2, first.size)
        assertThrows(IllegalStateException::class.java) { r.pendingRequest("q1", "input") }
    }
    @Test fun pluginInteractionCannotBeAnsweredThroughAScriptHandle() {
        val r=record("plugin")
        r.accept(event(r, 1, "input").apply { addProperty("requestId", "q1") })
        assertThrows(IllegalStateException::class.java) { r.pendingRequest("q1", "input") }
    }
    @Test fun snapshotNormalizesInterruptedHistoryWithoutPretendingItResumed() {
        val r=record(); r.snapshot(JsonObject().apply {
            addProperty("runId", r.id); addProperty("state", "blocked"); addProperty("sequence", 8)
            add("result", JsonObject().apply { addProperty("status", "blocked"); addProperty("error", "HOST_UNAVAILABLE") })
        })
        assertEquals("blocked", r.state); assertEquals("HOST_UNAVAILABLE", r.result!!.getAsJsonObject("error")["code"].asString)
    }
}
