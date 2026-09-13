package org.autojs.autojs.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Encoded image.captureScreen uses the same crop/scale/size policy as accessibility screenshots. */
class NodeBridgeScreenshotEncodingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val diagnostics = NodeBridgeAccessibilityDiagnostics(context, context.filesDir.path)

    @Test fun cropScaleAndAllFormatsProduceOneShotImagesAndReleaseTheirBitmap() {
        listOf("jpeg", "png", "webp").forEach { format ->
            val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff225588.toInt()) }
            val response = capture("""{"format":"$format","quality":70,"scale":0.5,"region":{"left":10,"top":10,"right":110,"bottom":70}}""") { bitmap }
            assertTrue(response.toJson(), response.ok)
            assertTrue(bitmap.isRecycled)
            val result = JSONObject(response.resultJson!!)
            assertEquals("media_projection", result.getString("source"))
            assertEquals(50, result.getInt("width"))
            assertEquals(30, result.getInt("height"))
            val payload = result.getJSONObject("payload")
            assertTrue(payload.getBoolean("oneShot"))
            val file = File(payload.getString("path"))
            try {
                assertEquals(file.length(), result.getLong("bytes"))
                assertEquals("image/$format", payload.getString("mime"))
                val decoded = BitmapFactory.decodeFile(file.path)
                assertEquals(50, decoded.width)
                assertEquals(30, decoded.height)
                decoded.recycle()
            } finally { file.delete() }
        }
    }

    @Test fun noisyPngExceedsTheHostByteLimitAndTheSourceIsReleased() {
        val width = 2048
        val height = 1024
        val random = Random(33)
        val pixels = IntArray(width * height) { random.nextInt() or 0xff000000.toInt() }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val response = capture("""{"format":"png","scale":1}""") { bitmap }
        assertFalse(response.ok)
        assertEquals(NodeBridgeErrorCategory.RESOURCE_LIMIT, response.error!!.category)
        assertTrue(bitmap.isRecycled)
    }

    @Test fun badOptionsNeverCaptureAndRevokedConsentReturnsPermissionDenied() {
        val invalid = capture("""{"savePath":"x.jpg"}""") { error("invalid options must not capture") }
        assertFalse(invalid.ok)
        assertEquals(NodeBridgeErrorCategory.INVALID_REQUEST, invalid.error!!.category)
        val denied = capture("{}") { throw SecurityException("revoked") }
        assertFalse(denied.ok)
        assertEquals(NodeBridgeErrorCategory.PERMISSION_DENIED, denied.error!!.category)
    }

    @Test fun transientMissingFramesRetryTheRefreshedPipelineAndRemainBounded() {
        var calls = 0
        var pauses = 0
        val frame = Any()
        assertSame(frame, NodeScreenCaptureSession.captureFreshFrame(pause = { pauses++ }) {
            if (++calls < 3) null else frame
        })
        assertEquals(3, calls)
        assertEquals(2, pauses)

        calls = 0
        pauses = 0
        try {
            NodeScreenCaptureSession.captureFreshFrame<Any>(pause = { pauses++ }) { calls++; null }
            fail("missing frames must eventually fail")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("6 bounded attempts"))
        }
        assertEquals(6, calls)
        assertEquals(5, pauses)

        val revoked = SecurityException("revoked")
        try {
            NodeScreenCaptureSession.captureFreshFrame<Any>(pause = { fail("revoked permission must not retry") }) { throw revoked }
            fail("revoked permission must fail")
        } catch (actual: SecurityException) {
            assertSame(revoked, actual)
        }
    }

    private fun capture(options: String, source: () -> Bitmap): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        val request = NodeBridgeRequest("screen-test", "image", "captureScreen", "[$options]", 30_000L, listOf("image", "screen_capture"))
        diagnostics.captureScreen(request, source) { response = it; latch.countDown() }
        assertTrue("screenshot encoder timed out", latch.await(30, TimeUnit.SECONDS))
        return checkNotNull(response)
    }
}
