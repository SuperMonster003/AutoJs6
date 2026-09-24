package org.autojs.autojs.engine

import android.content.Intent
import android.app.Dialog
import android.os.SystemClock
import android.widget.TextView
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

    @Test fun preparedActionUsesActualWindowDespiteStaleComponentMetadata() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("autojs.agent.accessibility").toBoolean())
        assertTrue(AccessibilityService.waitForOperational(10_000L))
        NodeAccessibilitySmokeActivity.reset()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, NodeAccessibilitySmokeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as NodeAccessibilitySmokeActivity
        var component = "test.stale.confirmation" to "test.stale.confirmation.Activity"
        val diagnostics = NodeBridgeAccessibilityDiagnostics(instrumentation.targetContext, "") { component }
        var dialog: Dialog? = null
        fun request(method: String, args: JSONArray) = NodeBridgeRequest("window-${sequence.incrementAndGet()}",
            "accessibility", method, args.toString(), 5_000L, listOf("accessibility"))
        fun dump() = diagnostics.dump(request("dump", JSONArray().put(JSONObject().put("format", "compact"))))
        fun prepare(): JSONObject {
            val snapshot = awaitFixtureDump(::dump)
            val inspected = diagnostics.inspectNode(request("inspectNode", JSONArray().put(target(snapshot, "python_selector_button")).put("click")))
            assertTrue(inspected.error?.message.orEmpty(), inspected.ok)
            return JSONObject(checkNotNull(inspected.resultJson)).getJSONObject("target")
        }
        try {
            instrumentation.waitForIdleSync()
            val initial = awaitFixtureDump(::dump)
            assertEquals(instrumentation.targetContext.packageName, initial.getString("packageName"))
            assertEquals("", initial.getString("activityName"))
            val target = prepare()
            // The event cache catches up after confirmation, while the actual window stays put.
            component = instrumentation.targetContext.packageName to activity.javaClass.name
            val refreshed = awaitFixtureDump(::dump)
            assertEquals(initial.getString("windowIdentity"), refreshed.getString("windowIdentity"))
            val clicked = diagnostics.nodeRefAction(request("click", JSONArray().put(target)))!!
            assertTrue(clicked.error?.message.orEmpty(), clicked.ok)
            assertEquals("true", clicked.resultJson)
            assertTrue(NodeAccessibilitySmokeActivity.awaitClick(3_000L))

            val second = prepare()
            instrumentation.runOnMainSync {
                dialog = Dialog(activity).apply {
                    setContentView(TextView(activity).apply { text = "node-window-confirmation-fixture" })
                    show()
                }
            }
            val deadline = SystemClock.uptimeMillis() + 5_000L
            var changed = false
            do {
                val response = dump()
                changed = response.ok && response.resultJson.orEmpty().contains("node-window-confirmation-fixture")
                if (!changed) SystemClock.sleep(100)
            } while (!changed && SystemClock.uptimeMillis() < deadline)
            assertTrue("The competing window must be visible before attempting the old target.", changed)
            assertStale(diagnostics.nodeRefAction(request("click", JSONArray().put(second)))!!)
            assertEquals(1, NodeAccessibilitySmokeActivity.clickedCount())
        } finally {
            diagnostics.clearNodeRefs()
            instrumentation.runOnMainSync { dialog?.dismiss(); activity.finish() }
        }
    }

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

    private fun awaitFixtureDump(provider: AndroidNodeBridgeCapabilityProvider): JSONObject = awaitFixtureDump {
        dispatch(provider, "dump", JSONArray().put(JSONObject().put("format", "compact")))
    }

    private fun awaitFixtureDump(dump: () -> NodeBridgeResponse): JSONObject {
        val deadline = SystemClock.uptimeMillis() + 10_000L
        var last: NodeBridgeResponse? = null
        do {
            last = dump()
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
