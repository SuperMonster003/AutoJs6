package org.autojs.autojs.engine

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.plugin.paddle.ocr.api.OcrResult
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class NodeBridgeScreenTextTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun request(args: String = "[]", timeout: Long = 5000) = NodeBridgeRequest(
        "screen-text", "accessibility", "readScreenText", args, timeout,
        listOf("accessibility", "screen_capture", "ocr"),
    )

    @Test fun destroyedProviderCannotInitializeNewOcrOrAgentHelpers() {
        val provider = AndroidNodeBridgeCapabilityProvider(context)
        provider.destroy()
        val requests = listOf(request(), NodeBridgeRequest(
            "closed-agent", "agent", "listScripts", "[]", 5000, listOf("agent"),
        ))
        requests.forEach { request ->
            val done = CountDownLatch(1)
            val result = AtomicReference<NodeBridgeResponse>()
            val replies = AtomicInteger()
            provider.dispatch(request) { replies.incrementAndGet(); result.set(it); done.countDown() }
            assertTrue(done.await(2, TimeUnit.SECONDS))
            assertEquals(NodeBridgeErrorCategory.PROCESS_DEAD, result.get().error?.category)
            assertEquals(1, replies.get())
        }
        val failure = runCatching { provider.prepareAgentScriptRoots(emptyList()) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure!!.message.orEmpty().contains("closed"))
    }

    @Test fun missingEnabledOcrReturnsStableUnavailableThroughRealProviderBeforeCapture() {
        @Suppress("DEPRECATION")
        val packages = context.packageManager.queryIntentServices(Intent(OcrPluginHost.ACTION_OCR), 0)
            .mapNotNull { it.serviceInfo?.packageName }.distinct()
        val preferences = context.getSharedPreferences("plugin_center_enable_state", 0)
        val original = packages.associateWith { packageName ->
            val key = "key_\$_enabled_plugin_\$_$packageName"
            if (preferences.contains(key)) preferences.getBoolean(key, true) else null
        }
        val provider = AndroidNodeBridgeCapabilityProvider(context)
        try {
            packages.forEach { PluginEnableStore.setEnabled(context, it, false) }
            assertTrue(OcrPluginHost.availableEngines(context).isEmpty())
            val done = CountDownLatch(1)
            val answer = AtomicReference<NodeBridgeResponse>()
            provider.dispatch(request()) { answer.set(it); done.countDown() }
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertFalse(answer.get().ok)
            assertEquals(NodeBridgeErrorCategory.UNAVAILABLE, answer.get().error?.category)
            assertEquals("OCR_PLUGIN_REQUIRED", answer.get().error?.message)
        } finally {
            provider.destroy()
            val edit = preferences.edit()
            original.forEach { (packageName, enabled) ->
                val key = "key_\$_enabled_plugin_\$_$packageName"
                if (enabled == null) edit.remove(key) else edit.putBoolean(key, enabled)
            }
            edit.commit()
        }
    }

    @Test fun croppedObservationHasScreenCoordinatesAndRecyclesBothBitmaps() {
        val screenshot = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        val recognized = AtomicReference<Bitmap>()
        val bridge = NodeBridgeScreenText(context, { error("unused fallback") },
            NodeBridgeScreenText.Recognizer {
                NodeBridgeScreenText.Detection { bitmap, _ ->
                    recognized.set(bitmap)
                    assertEquals(10, bitmap.width)
                    listOf(OcrResult().apply { text = "咖啡"; confidence = 0.9f; bounds = Rect(1, 2, 3, 4) })
                }
            }, { screenshot })
        bridge.use {
            val done = CountDownLatch(1)
            val result = AtomicReference<NodeBridgeResponse>()
            bridge.dispatch(request("""[{"region":[5,6,10,10]}]""")) { result.set(it); done.countDown() }
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertTrue(result.get().ok)
            val line = JSONArray(result.get().resultJson).getJSONObject(0)
            assertEquals("咖啡", line.getString("text"))
            assertEquals(6, line.getJSONObject("bounds").getInt("left"))
            assertEquals(10, line.getJSONObject("bounds").getInt("bottom"))
            assertTrue(screenshot.isRecycled)
            assertTrue(recognized.get().isRecycled)
        }
    }

    @Test fun deadlineReturnsOnceAndRecyclesLateRecognitionInput() {
        val screenshot = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val started = CountDownLatch(1)
        val released = CountDownLatch(1)
        val bridge = NodeBridgeScreenText(context, { error("unused fallback") },
            NodeBridgeScreenText.Recognizer {
                NodeBridgeScreenText.Detection { _, _ ->
                    started.countDown()
                    try { Thread.sleep(5000) } finally { released.countDown() }
                    emptyList()
                }
            }, { screenshot })
        bridge.use {
            val done = CountDownLatch(1)
            val result = AtomicReference<NodeBridgeResponse>()
            val calls = AtomicInteger()
            bridge.dispatch(request(timeout = 300)) { calls.incrementAndGet(); result.set(it); done.countDown() }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertTrue(done.await(2, TimeUnit.SECONDS))
            assertEquals(NodeBridgeErrorCategory.TIMEOUT, result.get().error?.category)
            assertTrue(released.await(2, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (!screenshot.isRecycled && System.nanoTime() < deadline) Thread.yield()
            assertTrue(screenshot.isRecycled)
            assertEquals(1, calls.get())
        }
    }

    @Test fun invalidRegionCannotOverflowAndClosedBridgeRejectsNewWork() {
        val screenshot = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val bridge = NodeBridgeScreenText(context, { error("unused fallback") },
            NodeBridgeScreenText.Recognizer { NodeBridgeScreenText.Detection { _, _ -> error("invalid region reached OCR") } }, { screenshot })
        val done = CountDownLatch(1)
        val result = AtomicReference<NodeBridgeResponse>()
        bridge.dispatch(request("""[{"region":[2147483647,0,10,1]}]""")) { result.set(it); done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(NodeBridgeErrorCategory.INVALID_REQUEST, result.get().error?.category)
        assertTrue(screenshot.isRecycled)
        bridge.close()
        bridge.dispatch(request()) { result.set(it) }
        assertEquals(NodeBridgeErrorCategory.PROCESS_DEAD, result.get().error?.category)
    }
}
