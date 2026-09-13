package org.autojs.autojs.compat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.core.plugin.barcode.BarcodePluginHost
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.python.PythonRuntimeExecutionResult
import org.autojs.autojs.engine.PythonPluginScriptEngine
import org.autojs.autojs.engine.QuickJsPluginScriptEngine
import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.script.PythonFileSource
import org.autojs.autojs.script.StringScriptSource
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Minimal positive paths through the installed providers, not fake Binder implementations. */
class ParcelPluginRoundTripDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun quickJsExecutesSourcePfdAndReturnsConsoleCallback() {
        val packageName = "io.github.supermonster003.autojs6.plugin.quickjs.runtime"
        withEnabledPlugin(packageName) {
            val console = ScriptEngineService.getInstance().globalConsole as GlobalConsole
            val marker = "sdk37-quickjs-${UUID.randomUUID()}"
            val engine = QuickJsPluginScriptEngine(context, console)
            try {
                engine.init()
                assertEquals(0, engine.execute(StringScriptSource("sdk37.qjs", "console.log('$marker');")))
                assertConsoleLine(console, marker)
            } finally {
                engine.destroy()
            }
        }
    }

    @Test fun pythonExecutesThroughTheCurrentHostProtocolAndReturnsOutput() {
        withEnabledPlugin("io.github.supermonster003.autojs6.plugin.python.runtime") {
            val console = ScriptEngineService.getInstance().globalConsole as GlobalConsole
            val marker = "sdk37-python-${UUID.randomUUID()}"
            val source = File.createTempFile("sdk37-parcel-", ".py", context.cacheDir)
            val engine = PythonPluginScriptEngine(context, console)
            try {
                source.writeText("print('$marker', flush=True)\n", Charsets.UTF_8)
                engine.init()
                val result = (engine.execute(PythonFileSource(source)) as PythonRuntimeExecutionResult).terminal
                assertEquals(0, result.exitCode)
                assertEquals("$marker\n".toByteArray(Charsets.UTF_8).size.toLong(), result.stdoutBytes)
                assertEquals(0L, result.stderrBytes)
                assertConsoleLine(console, marker)
            } finally {
                engine.destroy()
                check(source.delete()) { "Unable to remove the temporary Python source" }
            }
        }
    }

    @Test fun barcodeDecodesImagePfdAndReturnsStructuredResults() {
        val packageName = "io.github.supermonster003.autojs6.plugin.mlkit.barcode"
        withEnabledPlugin(packageName) {
            val bitmap = ean8Fixture()
            try {
                runBlocking {
                    val target = BarcodePluginHost.discover(context, packageName).single()
                    assertNull("Barcode INFO failed", target.error)
                    assertNotNull(target.pluginInfo)
                    val rows = BarcodePluginHost.detect(context, target, bitmap)
                    val row = rows.single { it.rawValue == "96385074" }
                    assertEquals("EAN_8", row.formatName)
                    assertNotNull(row.boundingBox)
                    assertFalse(row.boundingBox.isEmpty)
                    assertTrue(row.cornerPoints.isNotEmpty())
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun withEnabledPlugin(packageName: String, block: () -> Unit) {
        context.packageManager.getPackageInfo(packageName, 0) // Missing installation must fail, not skip.
        val enabled = PluginEnableStore.isEnabled(context, packageName)
        try {
            PluginEnableStore.setEnabled(context, packageName, true)
            block()
        } finally {
            PluginEnableStore.setEnabled(context, packageName, enabled)
        }
    }

    private fun assertConsoleLine(console: GlobalConsole, marker: String) {
        val lines = console.snapshotLogEntries().flatMap { it.content.toString().lineSequence().toList() }
        assertTrue("Missing real provider console line '$marker': $lines", lines.any { it.endsWith(marker) })
    }

    private fun ean8Fixture(): Bitmap {
        // Fixed EAN-8 value 96385074, including start, center and end guards. No camera needed.
        val modules = "101" + "0001011" + "0101111" + "0111101" + "0110111" + "01010" +
            "1001110" + "1110010" + "1000100" + "1011100" + "101"
        val bitmap = Bitmap.createBitmap((modules.length + 22) * 4, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK }
        modules.forEachIndexed { index, bit ->
            if (bit == '1') canvas.drawRect((index + 11) * 4f, 20f, (index + 12) * 4f, 180f, paint)
        }
        return bitmap
    }
}
