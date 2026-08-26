package org.autojs.autojs.engine

import android.app.Activity
import android.content.Intent
import android.media.Image
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.image.capture.ScreenCaptureRequester
import org.autojs.autojs.core.image.capture.ScreenCapturer
import org.autojs.autojs.core.image.capture.ScreenCapturerForegroundService
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.ui.main.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeScreenCaptureRealDeviceSmokeInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    private val targetContext = instrumentation.targetContext
    private var activity: MainActivity? = null
    private var requester: ScreenCaptureRequester? = null
    private var capturer: ScreenCapturer? = null

    @After
    fun tearDown() {
        runCatching {
            capturer?.release()
        }
        capturer = null
        runCatching {
            requester?.unbindService()
        }
        requester = null
        runCatching {
            targetContext.stopService(Intent(targetContext, ScreenCapturerForegroundService::class.java))
        }
        activity?.let { launched ->
            instrumentation.runOnMainSync {
                launched.finish()
            }
        }
        activity = null
    }

    @Test
    fun capturesOneFrameAfterUserGrantsScreenCapturePermission() {
        assumeTrue(
            "Pass -Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.screenCaptureSmoke=true for focused runs.",
            screenCaptureSmokeEnabled(),
        )
        assumeTrue("Screen capture requires Android 5.0+.", Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)

        val launchedActivity = launchTargetActivity()
        val permission = requestScreenCapturePermission(launchedActivity)
        permission.error?.let { error ->
            fail("Screen capture permission request failed before user decision: ${error.javaClass.name}: ${error.message}")
        }
        assertTrue(
            "Screen capture permission was denied or canceled. Re-run the optional screen_capture profile on Sony XQ-AT72 " +
                    "and approve the system screen-capture prompt.",
            permission.resultCode == Activity.RESULT_OK && permission.data != null,
        )

        val expectedWidth = ScreenMetrics.deviceScreenWidth
        val expectedHeight = ScreenMetrics.deviceScreenHeight
        assertTrue("Invalid expected screen dimensions: ${expectedWidth}x$expectedHeight", expectedWidth > 0 && expectedHeight > 0)

        val options = ScreenCapturer.Options(
            expectedWidth,
            expectedHeight,
            ScreenCapturer.ORIENTATION_NONE,
            ScreenMetrics.deviceScreenDensity,
            false,
        )
        val currentCapturer = ScreenCapturer(
            targetContext,
            checkNotNull(permission.data),
            options,
            Handler(Looper.getMainLooper()),
        )
        capturer = currentCapturer

        val frame = captureOneFrame(currentCapturer)
        try {
            assertEquals("Captured frame width mismatch", expectedWidth, frame.width)
            assertEquals("Captured frame height mismatch", expectedHeight, frame.height)
            val bitmap = ImageWrapper.toBitmap(frame)
            try {
                assertEquals("Decoded frame bitmap width mismatch", frame.width, bitmap.width)
                assertEquals("Decoded frame bitmap height mismatch", frame.height, bitmap.height)
            } finally {
                bitmap.recycle()
            }
            assertTrue("Decoded frame bitmap was not recycled", bitmap.isRecycled)
        } finally {
            frame.close()
        }

        currentCapturer.release()
        capturer = null
        targetContext.stopService(Intent(targetContext, ScreenCapturerForegroundService::class.java))
    }

    private fun launchTargetActivity(): MainActivity {
        activity?.let { launched ->
            instrumentation.runOnMainSync { launched.finish() }
        }
        val launched = instrumentation.startActivitySync(
            Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        activity = launched
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            ScreenMetrics.init(launched)
        }
        Thread.sleep(500L)
        return launched
    }

    private fun requestScreenCapturePermission(activity: MainActivity): PermissionResult {
        val latch = CountDownLatch(1)
        var resultCode = Int.MIN_VALUE
        var data: Intent? = null
        var error: Throwable? = null
        val currentRequester = ScreenCaptureRequester()
        requester = currentRequester
        instrumentation.runOnMainSync {
            currentRequester.request(
                activity,
                object : ScreenCaptureRequester.Callback {
                    override fun onRequestResult(code: Int, intent: Intent?) {
                        resultCode = code
                        data = intent
                        latch.countDown()
                    }

                    override fun onRequestError(t: Throwable) {
                        error = t
                        latch.countDown()
                    }
                },
            )
        }
        assertTrue(
            "Timed out waiting for the system screen-capture permission dialog result. " +
                    "Approve or deny the dialog on the Sony test device before ${permissionTimeoutMs()}ms elapses.",
            latch.await(permissionTimeoutMs(), TimeUnit.MILLISECONDS),
        )
        return PermissionResult(resultCode, data, error)
    }

    private fun captureOneFrame(screenCapturer: ScreenCapturer): Image {
        val timeoutMs = captureTimeoutMs()
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var lastError: Throwable? = null
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                val frame = screenCapturer.capture()
                if (frame != null) {
                    return frame
                }
            } catch (error: Throwable) {
                lastError = error
                break
            }
            Thread.sleep(120L)
        }
        val suffix = lastError?.let { error -> " Last error: ${error.javaClass.name}: ${error.message}" }.orEmpty()
        fail("Timed out capturing one screen frame within ${timeoutMs}ms after permission was granted.$suffix")
        throw AssertionError("unreachable")
    }

    private fun screenCaptureSmokeEnabled(): Boolean {
        return arguments.getString("autojs.nodejs.screenCaptureSmoke").toBooleanFlag()
    }

    private fun permissionTimeoutMs(): Long {
        return arguments.getString("autojs.nodejs.screenCaptureSmoke.permissionTimeoutMs")
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?: 45_000L
    }

    private fun captureTimeoutMs(): Long {
        return arguments.getString("autojs.nodejs.screenCaptureSmoke.captureTimeoutMs")
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?: 10_000L
    }

    private fun String?.toBooleanFlag(): Boolean {
        return equals("true", ignoreCase = true) || equals("yes", ignoreCase = true) || this == "1"
    }

    private data class PermissionResult(
        val resultCode: Int,
        val data: Intent?,
        val error: Throwable?,
    )
}
