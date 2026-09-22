package org.autojs.autojs.engine

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real accessibility against the existing debug UI; no Node or AI plugin is involved. */
class NodeBridgeCompactNodeRefTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val sequence = AtomicInteger()

    @Test fun compactDumpRelocatesActionsAndRejectsForeignSnapshots() {
        assumeTrue("Enable this real-UI test with -e autojs.agent.accessibility true",
            InstrumentationRegistry.getArguments().getString("autojs.agent.accessibility").toBoolean())
        assertTrue("Enable AutoJs6 accessibility before running the compact/nodeRef test.",
            AccessibilityService.waitForOperational(10_000L))
        NodeAccessibilitySmokeActivity.reset()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, NodeAccessibilitySmokeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as NodeAccessibilitySmokeActivity
        val first = AndroidNodeBridgeCapabilityProvider(instrumentation.targetContext)
        val second = AndroidNodeBridgeCapabilityProvider(instrumentation.targetContext)
        try {
            instrumentation.waitForIdleSync()
            val initial = awaitFixtureDump(first)
            assertTrue(initial.getInt("nodeCount") in 1..200)
            assertEquals("compact", initial.getString("format"))
            assertTrue(initial.getString("snapshotId").isNotBlank())
            val button = target(initial, "python_selector_button")
            val clicked = dispatch(first, "click", JSONArray().put(button))
            assertTrue(clicked.error?.message.orEmpty(), clicked.ok)
            assertEquals("true", clicked.resultJson)
            assertTrue(NodeAccessibilitySmokeActivity.awaitClick(3_000L))
            assertEquals(1, NodeAccessibilitySmokeActivity.clickedCount())

            val wrongSnapshot = JSONObject(button.toString()).put("snapshotId", "unknown-snapshot")
            assertStale(dispatch(first, "click", JSONArray().put(wrongSnapshot)))
            assertEquals(1, NodeAccessibilitySmokeActivity.clickedCount())

            // A second provider can observe the same UI but cannot borrow the first provider's refs.
            val independent = awaitFixtureDump(second)
            assertFalse(initial.getString("snapshotId") == independent.getString("snapshotId"))
            assertStale(dispatch(second, "click", JSONArray().put(button)))
            assertEquals(1, NodeAccessibilitySmokeActivity.clickedCount())

            val current = awaitFixtureDump(first)
            val edited = dispatch(first, "setText", JSONArray().put(target(current, "python_selector_input"))
                .put(NodeAccessibilitySmokeActivity.SELECTOR_INPUT_UPDATED_TEXT))
            assertTrue(edited.error?.message.orEmpty(), edited.ok)
            assertEquals("true", edited.resultJson)
            assertTrue(NodeAccessibilitySmokeActivity.awaitInputText(3_000L))
        } finally {
            first.destroy()
            second.destroy()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun awaitFixtureDump(provider: AndroidNodeBridgeCapabilityProvider): JSONObject {
        val deadline = SystemClock.uptimeMillis() + 10_000L
        var last: NodeBridgeResponse? = null
        do {
            last = dispatch(provider, "dump", JSONArray().put(JSONObject().put("format", "compact")))
            if (last.ok) {
                val dump = JSONObject(checkNotNull(last.resultJson))
                if (dump.optString("text").contains("id=python_selector_button") &&
                    dump.optString("text").contains("id=python_selector_input")) return dump
            }
            SystemClock.sleep(200)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Controlled fixture did not appear in compact dump: ${last?.error?.code}")
    }

    private fun target(dump: JSONObject, shortId: String): JSONObject {
        val line = dump.getString("text").lineSequence().single { it.contains(" id=$shortId ") }
        val ref = Regex("^#n[1-9][0-9]*").find(line)?.value ?: error("Compact node reference is missing")
        return JSONObject().put("nodeRef", ref).put("snapshotId", dump.getString("snapshotId"))
    }

    private fun dispatch(provider: AndroidNodeBridgeCapabilityProvider, method: String, args: JSONArray): NodeBridgeResponse {
        val done = CountDownLatch(1)
        val answer = AtomicReference<NodeBridgeResponse>()
        provider.dispatch(NodeBridgeRequest("compact-${sequence.incrementAndGet()}", "accessibility", method,
            args.toString(), 5_000L, listOf("accessibility"))) { response -> answer.set(response); done.countDown() }
        assertTrue("Timed out waiting for accessibility.$method", done.await(8, TimeUnit.SECONDS))
        return checkNotNull(answer.get())
    }

    private fun assertStale(response: NodeBridgeResponse) {
        assertFalse(response.ok)
        assertEquals(NodeBridgeErrorCategory.INVALID_REQUEST, response.error?.category)
        assertTrue(response.error?.message.orEmpty(), response.error?.message?.contains("NODE_REF_STALE") == true)
    }
}
