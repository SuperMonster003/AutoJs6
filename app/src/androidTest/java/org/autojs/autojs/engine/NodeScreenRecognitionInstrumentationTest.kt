package org.autojs.autojs.engine

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.ui.log.LogActivity
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class NodeScreenRecognitionInstrumentationTest {
    @Test fun mlKitRecognizesKnownHostTextAndQrThroughNodeImageHandles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.runOnMainSync {
            activity.setContentView(object : View(activity) {
                override fun onDraw(canvas: Canvas) {
                    canvas.drawColor(Color.WHITE)
                    val paint = Paint().apply { color = Color.BLACK; textSize = 56f; isAntiAlias = true }
                    paint.textSize *= minOf(1f, (width - 64f) / paint.measureText(NodeImageRecognitionFixture.TEXT))
                    canvas.drawText(NodeImageRecognitionFixture.TEXT, 32f, 140f, paint)
                    NodeImageRecognitionFixture.drawCode(canvas, 32, 200, minOf(8, (width - 64) / 33))
                }
            })
        }
        val directory = File(context.cacheDir, "node-recognition-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.waitForIdle(500, 5000)
            // Verify recognition on the real displayed host UI while MediaProjection consent is pending.
            val screen = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(directory, "host-ui.png").outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screen.recycle()
            val result = NodePluginTestHarness.runPluginScript(
                """
                (async () => {
                  const images = require('images'), ocr = require('ocr'), barcode = require('barcode');
                  const frame = await images.readImage('host-ui.png');
                  try {
                    const text = await ocr.recognizeText(frame, {timeoutMs: 30000});
                    if (!text.replace(/\s+/g, '').includes('ROADMAP2026')) throw new Error('Known host text missing: ' + text);
                    const code = await barcode.detect(frame, {formats: ['QR_CODE'], timeoutMs: 30000});
                    if (!code || code.rawValue !== '${NodeImageRecognitionFixture.CODE}') throw new Error('Known QR missing: ' + JSON.stringify(code));
                    let unavailable = false;
                    try { await ocr.recognizeText(frame, {engineId: 'missing-node-ocr-fixture', timeoutMs: 30000}); }
                    catch (error) { unavailable = error.category === 'unavailable' && Boolean(error.message); }
                    if (!unavailable) throw new Error('Missing OCR plugin should return a readable unavailable error');
                    console.log('m14.recognition=PASS text=' + text.replace(/\s+/g, ' ') + ' qr=' + code.rawValue);
                  } finally { await images.recycle(frame); }
                })().catch(error => { console.error(error.stack); process.exitCode = 1; });
                """.trimIndent(),
                "screen-recognition.cjs", workingDirectory = directory, timeoutMs = 75_000,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to NodeBridgePermissionManifest
                    .fromDeclaredCapabilities(listOf("image", "ocr", "barcode")).toJson()),
            )
            NodePluginTestHarness.assertSucceeded("host screen OCR and barcode", result)
            assertTrue(result.stdout, result.stdout.contains("m14.recognition=PASS"))
            println(result.stdout)
        } finally {
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
