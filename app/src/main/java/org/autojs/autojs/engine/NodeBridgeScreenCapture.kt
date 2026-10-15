package org.autojs.autojs.engine

import android.graphics.Bitmap
import android.os.Build
import org.autojs.autojs.core.accessibility.A11yScreenshotter
import org.autojs.autojs.core.accessibility.AccessibilityService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * One screen capture for bridge consumers: the accessibility screenshot API when it can serve
 * (API 30+, service operational), otherwise [fallback], the session's MediaProjection capture.
 * Shared by `accessibility.readScreenText` and `accessibility.captureScreenImage` (MCP roadmap P11 D23).
 */
internal object NodeBridgeScreenCapture {

    const val SOURCE_ACCESSIBILITY = "accessibility"
    const val SOURCE_MEDIA_PROJECTION = "media_projection"
    const val DEFAULT_MAX_ATTEMPTS = 60

    class Captured(val bitmap: Bitmap, val source: String)

    /**
     * [maxAttempts] x 50 ms is how long the accessibility path rides out the system's minimum
     * interval between screenshots before giving up on it; the default covers 3 s, like `screen_capture`.
     */
    fun capture(timeoutMs: Long, maxAttempts: Int = DEFAULT_MAX_ATTEMPTS, fallback: () -> Bitmap): Captured {
        val service = AccessibilityService.instance
        if (Build.VERSION.SDK_INT < 30 || service == null || !AccessibilityService.registry.isOperational) {
            return Captured(fallback(), SOURCE_MEDIA_PROJECTION)
        }
        val monitor = Any()
        val ready = CountDownLatch(1)
        var accepting = true
        var bitmap: Bitmap? = null
        try {
            A11yScreenshotter(service).takeWithRetry(maxAttempts = maxAttempts) { result ->
                synchronized(monitor) {
                    if (result is A11yScreenshotter.Result.Success) {
                        if (accepting && bitmap == null) bitmap = result.bitmap else result.bitmap.recycle()
                    }
                    ready.countDown()
                }
            }
            if (!ready.await(timeoutMs, TimeUnit.MILLISECONDS)) throw TimeoutException()
            synchronized(monitor) {
                accepting = false
                bitmap?.let { bitmap = null; return Captured(it, SOURCE_ACCESSIBILITY) }
            }
            return Captured(fallback(), SOURCE_MEDIA_PROJECTION)
        } finally {
            synchronized(monitor) { accepting = false; bitmap?.recycle(); bitmap = null }
        }
    }
}
