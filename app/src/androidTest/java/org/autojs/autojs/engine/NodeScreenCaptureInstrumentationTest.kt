package org.autojs.autojs.engine

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class NodeScreenCaptureInstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun permissions() = mapOf(
        NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to NodeBridgePermissionManifest
            .fromDeclaredCapabilities(listOf("screen_capture", "image")).toJson(),
    )

    @Test fun captureWithoutConsentReturnsPermissionDeniedAndImageSaveWorks() {
        val directory = File(context.cacheDir, "node-capture-${UUID.randomUUID()}").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(96, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        File(directory, "fixture.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            val result = NodePluginTestHarness.runPluginScript(
                """
                (async () => {
                  const images = require('images'), media = require('media_projection');
                  for (const call of [() => images.captureScreen(), () => media.requestScreenCapture({ requireExistingPermission: true })]) {
                    let denied = false;
                    try { await call(); } catch (error) { denied = error.category === 'permission-denied'; }
                    if (!denied) throw new Error('Capture without consent must return permission-denied');
                  }
                  const image = await images.readImage('fixture.png');
                  await images.saveImage(image, 'saved.png');
                  if (image.width !== 96 || image.height !== 80) throw new Error('Image size changed');
                  await images.recycle(image);
                  console.log('m14.image.save=PASS');
                })().catch(error => { console.error(error.stack); process.exitCode = 1; });
                """.trimIndent(),
                "capture-no-consent.cjs", workingDirectory = directory, runtimeModuleSources = permissions(),
            )
            NodePluginTestHarness.assertSucceeded("capture denied and save image", result)
            assertTrue(result.stdout, result.stdout.contains("m14.image.save=PASS"))
            val saved = BitmapFactory.decodeFile(File(directory, "saved.png").path)
            assertNotNull(saved)
            assertEquals(96, saved.width)
            assertEquals(80, saved.height)
            assertEquals(Color.GREEN, saved.getPixel(40, 30))
            saved.recycle()
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }

    /** Opt in only while a person is present to approve the Android capture dialog. */
    @Test fun approvedCaptureSavesScreenSizedPngAndStops() {
        assumeTrue("Manual Android screen capture consent required", InstrumentationRegistry.getArguments().getString("captureConsent") == "manual")
        val activity = instrumentation.startActivitySync(Intent(context, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.runOnMainSync {
            activity.setContentView(View(activity).apply { setBackgroundColor(Color.rgb(20, 190, 85)) })
        }
        val directory = File(context.cacheDir, "node-screen-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val result = NodePluginTestHarness.runPluginScript(
                """
                (async () => {
                  const images = require('images');
                  await images.requestScreenCapture({ timeoutMs: 120000 });
                  const frame = await images.captureScreen();
                  await images.saveImage(frame, 'screen.png');
                  console.log('m14.capture=' + frame.width + 'x' + frame.height);
                  await images.recycle(frame);
                  await images.stopScreenCapture();
                  let denied = false;
                  try { await images.captureScreen(); } catch (error) { denied = error.category === 'permission-denied'; }
                  if (!denied) throw new Error('Capture must require consent again after stop');
                })().catch(error => { console.error(error.stack); process.exitCode = 1; });
                """.trimIndent(),
                "screen-capture.cjs", workingDirectory = directory, timeoutMs = 150_000,
                runtimeModuleSources = permissions(),
            )
            NodePluginTestHarness.assertSucceeded("approved capture", result)
            val bitmap = BitmapFactory.decodeFile(File(directory, "screen.png").path)
            assertNotNull(bitmap)
            val orientation = context.resources.configuration.orientation
            assertEquals(ScreenMetrics.getOrientationAwareScreenWidth(orientation), bitmap.width)
            assertEquals(ScreenMetrics.getOrientationAwareScreenHeight(orientation), bitmap.height)
            println("m14.capture.png=${bitmap.width}x${bitmap.height} bytes=${File(directory, "screen.png").length()}")
            bitmap.recycle()
        } finally {
            directory.listFiles()?.forEach { it.delete() }; directory.delete()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
