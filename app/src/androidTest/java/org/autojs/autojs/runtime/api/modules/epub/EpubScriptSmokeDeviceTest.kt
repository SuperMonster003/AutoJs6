package org.autojs.autojs.runtime.api.modules.epub

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AutoJs
import org.autojs.autojs6.R
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.epub.EpubBookClient
import org.autojs.autojs.core.plugin.epub.EpubFixture
import org.autojs.autojs.core.plugin.epub.EpubPluginHost
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubErrorCodes
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The `epub` script API end to end (plugin roadmap P6.1): a script run by the real engine opens
 * the fixture EPUB through the installed plugin and exercises `epub.open` / the `EpubBook`
 * members / the convenience layer / the `Async` forms / the refusal codes; a script that exits
 * without closing its books leaves nothing open in the plugin; a disabled plugin surfaces as
 * `PLUGIN_DISABLED` with the localized hint. Skipped when no EPUB plugin is installed.
 *
 * ```
 * .\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.runtime.api.modules.epub.EpubScriptSmokeDeviceTest"
 * ```
 */
class EpubScriptSmokeDeviceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context
        get() = instrumentation.targetContext

    private val workDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.cacheDir, "epub-smoke").apply { mkdirs() }

    @Test
    fun extractionScriptAgainstTheFixture() {
        assumePluginInstalled()
        val file = EpubFixture.write(File(workDir, "smoke.epub"))
        val report = runScript("extract-smoke", assetSource("extract-smoke"), prelude(file), timeoutMs = 120_000L)
        val steps = report.optJSONArray("steps")
        Log.i(TAG, "extract-smoke: ${steps?.length() ?: 0} checks, open ${report.optLong("openMs")} ms, async chain ${report.optLong("asyncMs")} ms, cover=${report.opt("cover")}")
        report.toString().chunked(3000).forEachIndexed { index, chunk -> Log.i(TAG, "extract-smoke report[$index]: $chunk") }
        assertTrue("extract-smoke failed after ${steps?.length() ?: 0} checks: ${report.optString("errorCode")} ${report.optString("error")}", report.optBoolean("ok"))
        assertTrue(File(workDir, "dot.png").isFile)
        assertTrue(File(workDir, "renamed.png").isFile)
    }

    /** A script that never calls `close()`: the script's exit closes its books, so the plugin's book ceiling is untouched afterwards. */
    @Test
    fun scriptExitClosesTheBooksItLeftOpen() {
        assumePluginInstalled()
        val file = EpubFixture.write(File(workDir, "leak.epub"))
        val source = """
            var a = epub.open(EPUB_SMOKE.path);
            var b = epub.open(EPUB_SMOKE.path);
            var c = epub.open({ path: EPUB_SMOKE.path, displayName: 'third' });
            runtime.putProperty('epub.smoke.report', JSON.stringify({ ok: a.metadata.title === b.metadata.title && !c.isClosed, steps: [] }));
        """.trimIndent()
        val report = runScript("leak", source, prelude(file), timeoutMs = 60_000L)
        assertTrue(report.optBoolean("ok"))
        Thread.sleep(500)
        val clients = ArrayList<EpubBookClient>()
        try {
            runBlocking {
                repeat(EpubContract.MAX_OPEN_BOOKS) { clients += EpubPluginHost.openBook(context, file.path) }
            }
            assertEquals(EpubContract.MAX_OPEN_BOOKS, clients.size)
        } finally {
            clients.forEach { runCatching { it.close() } }
        }
    }

    /** The plugin disabled in the plugin center: `PLUGIN_DISABLED` with the localized hint in front, and `isAvailable()` false. */
    @Test
    fun disabledPluginSurfacesLocalizedRefusal() {
        val packageName = assumePluginInstalled()
        val file = EpubFixture.write(File(workDir, "disabled.epub"))
        val wasEnabled = PluginEnableStore.isEnabled(context, packageName)
        val source = """
            var out = { steps: [], available: epub.isAvailable() };
            try {
                epub.open(EPUB_SMOKE.path);
                out.thrown = false;
            } catch (e) {
                out.thrown = true;
                out.isEpubError = e instanceof epub.EpubError;
                out.code = e.code;
                out.message = e.message;
            }
            epub.metadataAsync(EPUB_SMOKE.path).then(function () {
                out.asyncCode = 'resolved';
            }, function (e) {
                out.asyncCode = e.code;
            }).then(function () {
                out.ok = true;
                runtime.putProperty('epub.smoke.report', JSON.stringify(out));
            });
        """.trimIndent()
        val report = try {
            PluginEnableStore.setEnabled(context, packageName, false)
            runScript("disabled", source, prelude(file), timeoutMs = 60_000L)
        } finally {
            PluginEnableStore.setEnabled(context, packageName, wasEnabled)
        }
        Log.i(TAG, "disabled: $report")
        assertTrue(report.optBoolean("ok"))
        assertEquals(false, report.optBoolean("available", true))
        assertTrue(report.optBoolean("thrown"))
        assertTrue(report.optBoolean("isEpubError"))
        assertEquals(EpubErrorCodes.PLUGIN_DISABLED, report.optString("code"))
        assertEquals(EpubErrorCodes.PLUGIN_DISABLED, report.optString("asyncCode"))
        val hint = context.getString(R.string.error_epub_plugin_unavailable)
        assertTrue(report.optString("message"), report.optString("message").startsWith(hint))
    }

    private fun assumePluginInstalled(): String {
        assumeTrue("the EPUB plugin is not installed", EpubPluginHost.queryServiceCount(context) > 0)
        val target = runBlocking { EpubPluginHost.discover(context) }.firstOrNull { it.pluginInfo != null }
            ?: throw AssertionError("no usable EPUB plugin")
        return target.serviceInfo.packageName
    }

    private fun prelude(file: File): String {
        val fields = linkedMapOf(
            "path" to file.path,
            "missingPath" to File(workDir, "missing.epub").path,
            "workDir" to workDir.path,
            "title" to EpubFixture.TITLE,
            "author" to EpubFixture.AUTHOR,
            "chapter1" to EpubFixture.CHAPTER1_HREF,
            "chapter2" to EpubFixture.CHAPTER2_HREF,
            "heading1" to EpubFixture.CHAPTER1_HEADING,
            "heading2" to EpubFixture.CHAPTER2_HEADING,
            "imageHref" to EpubFixture.IMAGE_HREF,
            "searchWord" to EpubFixture.SEARCH_WORD,
        )
        return "var EPUB_SMOKE = { " + fields.entries.joinToString(", ") { (key, value) -> "$key: ${JSONObject.quote(value)}" } + " };\n"
    }

    private fun assetSource(name: String): String =
        instrumentation.context.assets.open("epub/$name.js").bufferedReader().use { it.readText() }

    /** Runs [prelude] + [source] with the real engine and returns the report from runtime property `epub.smoke.report`. */
    private fun runScript(name: String, source: String, prelude: String, timeoutMs: Long): JSONObject {
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        val execution = AutoJs.instance.scriptEngineService.execute(StringScriptSource("epub-$name", prelude + source), listener, ExecutionConfig())
        try {
            assertTrue("$name did not finish within $timeoutMs ms", completed.await(timeoutMs, TimeUnit.MILLISECONDS))
            val engine = execution.engine as RhinoJavaScriptEngine
            failure.get()?.let { throw AssertionError("$name failed: ${it.message}", it) }
            val json = engine.runtime.getProperty("epub.smoke.report") as? String
                ?: throw AssertionError("$name left no report")
            return JSONObject(json)
        } finally {
            (execution.engine as? RhinoJavaScriptEngine)?.let { if (it.thread.isAlive) it.forceStop() }
        }
    }

    private companion object {
        const val TAG = "EpubScriptSmokeTest"
    }
}
