package org.autojs.autojs.core.plugin.center

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PluginEnableProbeDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()

    @Suppress("DEPRECATION")
    private fun service(packageName: String) = context.packageManager.queryIntentServices(
        Intent(OcrPluginHost.ACTION_OCR).setPackage(packageName),
        PackageManager.GET_META_DATA,
    ).single().serviceInfo

    @Test
    fun missingServiceIsRejectedBeforeBinding() = runBlocking {
        val packageName = arguments.getString("brokenOcrPackage")
        assumeTrue("Pass -e brokenOcrPackage with an APK missing its service class", packageName != null)
        val started = SystemClock.elapsedRealtime()
        val error = runCatching {
            PluginEnableProbe.run(packageName!!) { OcrPluginHost.probe(context, packageName) }
        }.exceptionOrNull()
        val elapsed = SystemClock.elapsedRealtime() - started
        Log.i("PluginEnableProbeTest", "broken=$packageName elapsedMs=$elapsed error=$error")
        assertTrue(error is ClassNotFoundException)
        assertEquals(PluginErrorCode.PLUGIN_PROCESS_CRASHED, PluginErrorMapper.fromThrowable(error!!).code)
        assertTrue("Preflight took $elapsed ms", elapsed < 2_000L)
    }

    @Test
    fun installedOcrPluginEnablesAndRecognizes() = runBlocking {
        val packageName = arguments.getString("ocrPackage")
        assumeTrue("Pass -e ocrPackage with an installed OCR plugin", packageName != null)
        val started = SystemClock.elapsedRealtime()
        var target: OcrPluginHost.Discovered? = null
        PluginEnableProbe.run(packageName!!) {
            val info = OcrPluginHost.probe(context, packageName)
            target = OcrPluginHost.Discovered(service(packageName), info, null)
        }
        Log.i("PluginEnableProbeTest", "enabled=$packageName elapsedMs=${SystemClock.elapsedRealtime() - started}")
        val bitmap = Bitmap.createBitmap(800, 220, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                drawText("AUTOJS6 12345", 35f, 140f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 72f
                })
            }
            val results = OcrPluginHost.recognizeText(context, target!!, bitmap)
            Log.i("PluginEnableProbeTest", "recognized=$packageName text=$results elapsedMs=${SystemClock.elapsedRealtime() - started}")
            assertTrue("Expected digits in OCR result: $results", results.any { it.contains("12345") })
        } finally {
            bitmap.recycle()
        }
    }
}
