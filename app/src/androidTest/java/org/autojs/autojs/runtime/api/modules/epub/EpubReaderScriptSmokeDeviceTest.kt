package org.autojs.autojs.runtime.api.modules.epub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.epub.EpubFixture
import org.autojs.autojs.core.plugin.epub.EpubPluginHost
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs.ui.settings.AboutActivity
import org.autojs.plugin.epub.api.EpubCapabilityKeys
import org.autojs.plugin.epub.api.EpubContract
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * `epub.read` end to end (plugin roadmap P6.2): a script run by the real engine opens the plugin's
 * reader from a foreground host Activity, receives `open` / `progress` / `error` / `close`, drives
 * `goTo`, `nextChapter`, `setPreferences`, `bookmarks` and `close({ finish: true })`, and sees the
 * refusal codes; a script that exits with a session open ends the session while the reader
 * Activity stays for the user (plugin decision D32). Skipped when no EPUB plugin with
 * `reader-session` is installed.
 *
 * ```
 * .\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.runtime.api.modules.epub.EpubReaderScriptSmokeDeviceTest"
 * ```
 */
class EpubReaderScriptSmokeDeviceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context
        get() = instrumentation.targetContext

    private val workDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.cacheDir, "epub-smoke").apply { mkdirs() }

    private var foreground: Activity? = null
    private var pluginPackage: String? = null

    @After
    fun tearDown() {
        foreground?.let { activity -> instrumentation.runOnMainSync { activity.finish() } }
        foreground = null
        pluginPackage?.let { packageName ->
            instrumentation.uiAutomation.executeShellCommand("am force-stop $packageName").close()
            Thread.sleep(1_000)
        }
        pluginPackage = null
    }

    @Test
    fun readerScriptDrivesTheSessionThroughItsEvents() {
        val packageName = pluginWithReaderSessionOrSkip()
        val file = EpubFixture.write(File(workDir, "reader-smoke.epub"))
        startForeground()
        val report = runScript("reader-smoke", assetSource("reader-smoke"), prelude(file), timeoutMs = 120_000L)
        val steps = report.optJSONArray("steps")
        Log.i(TAG, "reader-smoke: ${steps?.length() ?: 0} checks, read ${report.optLong("readMs")} ms, events=${report.optJSONArray("events")}, errors=${report.optJSONArray("errors")}")
        report.toString().chunked(3000).forEachIndexed { index, chunk -> Log.i(TAG, "reader-smoke report[$index]: $chunk") }
        assertTrue("reader-smoke failed after ${steps?.length() ?: 0} checks in phase ${report.optString("phase")}: ${report.optString("errorCode")} ${report.optString("error")}", report.optBoolean("ok"))
        assertEquals("close({ finish: true }) finished the reader Activity", 0, awaitReaderActivityCount(packageName, expected = 0))
    }

    /** The script exits (`exit()`) while its session is open: the session ends, the reader stays (D32). */
    @Test
    fun scriptExitEndsTheSessionAndLeavesTheReaderOpen() {
        val packageName = pluginWithReaderSessionOrSkip()
        val file = EpubFixture.write(File(workDir, "reader-exit.epub"))
        startForeground()
        val report = runScript("reader-exit", EXIT_SCRIPT, prelude(file), timeoutMs = 90_000L)
        Log.i(TAG, "reader-exit: $report")
        assertTrue(report.optBoolean("ok"))
        assertEquals("open", report.optString("state"))
        assertTrue("no close event reaches a script that exited", !report.has("closed"))
        assertEquals("the reader Activity stays after the script's exit (D32)", 1, awaitReaderActivityCount(packageName, expected = 1))
    }

    /**
     * `epub.read` while the plugin's reader is already on top of its task (left there by a
     * previous script, D32): the launch intent may be delivered to that instance instead of
     * creating a new one, and the plugin (roadmap P6.2) must still claim the new session.
     */
    @Test
    fun readWhileTheReaderIsAlreadyOpenClaimsTheNewSession() {
        val packageName = pluginWithReaderSessionOrSkip()
        val file = EpubFixture.write(File(workDir, "reader-replace.epub"))
        startForeground()
        val first = runScript("reader-replace-first", EXIT_SCRIPT, prelude(file), timeoutMs = 90_000L)
        assertTrue("the first script saw its open event: $first", first.optBoolean("ok"))
        assertEquals("the first reader stays open", 1, awaitReaderActivityCount(packageName, expected = 1))
        val source = """
            var out = { ok: false };
            var session = epub.read(EPUB_SMOKE.path, { href: EPUB_SMOKE.chapter1 });
            session.on('open', function (e) {
                out.href = e.href;
                out.ok = e.href === EPUB_SMOKE.chapter1;
                session.close({ finish: true });
            });
            session.on('close', function (e) {
                out.reason = e.reason;
                runtime.putProperty('epub.smoke.report', JSON.stringify(out));
            });
        """.trimIndent()
        val second = runScript("reader-replace-second", source, prelude(file), timeoutMs = 90_000L)
        Log.i(TAG, "reader-replace: $second")
        assertTrue("the second session opened at chapter 1: $second", second.optBoolean("ok"))
        assertEquals("host", second.optString("reason"))
        assertEquals("close({ finish: true }) finished the second reader", 0, awaitReaderActivityCount(packageName, expected = 0))
    }

    private fun pluginWithReaderSessionOrSkip(): String {
        assumeTrue("the EPUB plugin is not installed", EpubPluginHost.queryServiceCount(context) > 0)
        val target = runBlocking { EpubPluginHost.discover(context) }.firstOrNull { it.pluginInfo != null }
            ?: throw AssertionError("no usable EPUB plugin")
        val features = runBlocking { EpubPluginHost.getCapabilities(context) }.getStringArray(EpubCapabilityKeys.FEATURES)?.toList().orEmpty()
        assumeTrue("the plugin advertises no reader session", EpubContract.FEATURE_READER_SESSION in features)
        return target.serviceInfo.packageName.also { pluginPackage = it }
    }

    /** A host Activity in the foreground, so the host may start the plugin's reader Activity. */
    private fun startForeground(): Activity {
        val intent = Intent(context, AboutActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return instrumentation.startActivitySync(intent).also { foreground = it }
    }

    /** How many Activities of the plugin the activity manager lists (one history line per record). */
    private fun readerActivityCount(packageName: String): Int {
        val descriptor = instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
        val dump = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        val marker = "$packageName/"
        return dump.lineSequence().count { line -> line.contains("Hist") && line.contains("ActivityRecord{") && line.contains(marker) }
    }

    /** Polls [readerActivityCount] until it reaches [expected] or the wait runs out, and returns the last count. */
    private fun awaitReaderActivityCount(packageName: String, expected: Int, timeoutMs: Long = 8_000L): Int {
        val deadline = System.currentTimeMillis() + timeoutMs
        var count = readerActivityCount(packageName)
        while (count != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(250)
            count = readerActivityCount(packageName)
        }
        Log.i(TAG, "plugin activities: $count (expected $expected)")
        return count
    }

    private fun prelude(file: File): String {
        val fields = linkedMapOf(
            "path" to file.path,
            "missingPath" to File(workDir, "missing.epub").path,
            "workDir" to workDir.path,
            "title" to EpubFixture.TITLE,
            "chapter1" to EpubFixture.CHAPTER1_HREF,
            "chapter2" to EpubFixture.CHAPTER2_HREF,
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
            failure.get()?.takeUnless { it is ScriptInterruptedException }?.let { throw AssertionError("$name failed: ${it.message}", it) }
            val json = engine.runtime.getProperty("epub.smoke.report") as? String
                ?: throw AssertionError("$name left no report")
            return JSONObject(json)
        } finally {
            (execution.engine as? RhinoJavaScriptEngine)?.let { if (it.thread.isAlive) it.forceStop() }
        }
    }

    private companion object {
        const val TAG = "EpubReaderScriptSmokeTest"

        /** Opens a session, reports at `open` and exits inside the listener; the reader must stay (D32). */
        val EXIT_SCRIPT = """
            var out = { steps: [], ok: false };
            var session = epub.read(EPUB_SMOKE.path, { progression: 0.5 });
            session.on('open', function (e) {
                out.href = e.href;
                out.state = session.state;
                out.ok = session.isOpen;
                runtime.putProperty('epub.smoke.report', JSON.stringify(out));
                exit();
            });
            session.on('close', function (e) {
                out.closed = e.reason;
                runtime.putProperty('epub.smoke.report', JSON.stringify(out));
            });
        """.trimIndent()
    }
}
