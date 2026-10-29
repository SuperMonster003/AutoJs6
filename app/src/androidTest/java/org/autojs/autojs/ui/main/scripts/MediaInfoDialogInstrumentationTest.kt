package org.autojs.autojs.ui.main.scripts

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.platform.app.InstrumentationRegistry
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.mediainfo.MediainfoPluginHost
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.main.ResumedMainActivity
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class MediaInfoDialogInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun neutralButtonShowsOriginalPathInLightTheme() = checkDetails(night = false)

    @Test
    fun neutralButtonShowsOriginalPathInDarkTheme() = checkDetails(night = true)

    private fun checkDetails(night: Boolean) {
        assumeTrue("A MediaInfo plugin is not installed", MediainfoPluginHost.queryServiceCount(context) > 0)
        val demoPath = arguments.getString("mediainfoDemoPath")
        val source = if (demoPath != null) File(demoPath) else {
            File.createTempFile("media-dialog-", ".mka", context.cacheDir).apply {
                instrumentation.context.assets.open("mediainfo-two-audio.mka").use { input ->
                    outputStream().use { input.copyTo(it) }
                }
            }
        }
        assertTrue("The media fixture must be readable: $source", source.isFile && source.canRead())
        val originalAutoNight = ViewUtils.isAutoNightModeEnabled
        val originalNight = ViewUtils.isNightModeEnabled
        val originalMode = AppCompatDelegate.getDefaultNightMode()
        val dialog = AtomicReference<MaterialDialog>()
        val monitor = instrumentation.addMonitor(DisplayMediaInfoActivity::class.java.name, null, false)
        var main: MainActivity? = null
        var details: DisplayMediaInfoActivity? = null
        try {
            instrumentation.runOnMainSync {
                ViewUtils.isAutoNightModeEnabled = false
                ViewUtils.isNightModeEnabled = night
                ViewUtils.setDefaultNightMode(if (night) ViewUtils.MODE.NIGHT else ViewUtils.MODE.DAY)
            }
            main = ResumedMainActivity.launch(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            captureScriptIfRequested(night)
            instrumentation.runOnMainSync {
                MediaInfoDialogManager.showMediaInfoDialog(main, ExplorerFileItem(source, null)) {
                    showListener { dialog.set(it as MaterialDialog) }
                }
            }
            awaitMain("MediaInfo neutral button did not become ready") {
                dialog.get()?.getActionButton(DialogAction.NEUTRAL)?.let {
                    it.isEnabled && it.text.toString() == "MediaInfo"
                } == true
            }
            captureIfRequested(night, "dialog")
            tap(dialog.get().getActionButton(DialogAction.NEUTRAL))
            details = instrumentation.waitForMonitorWithTimeout(monitor, 10_000) as? DisplayMediaInfoActivity
                ?: throw AssertionError("The neutral button did not open MediaInfo details")
            val activity = details
            awaitMain("The MediaInfo report was not displayed") {
                activity.findViewById<TextView>(R.id.textView).text.contains("Complete name")
            }
            instrumentation.runOnMainSync {
                assertEquals(night, ViewUtils.isNightModeYes(activity))
                val report = activity.findViewById<TextView>(R.id.textView).text.toString()
                val completeName = Regex("(?m)^Complete name\\s*:\\s*(.+)$").find(report)?.groupValues?.get(1)?.trim()
                assertEquals(report, source.absolutePath, completeName)
                assertFalse(report, report.contains("/proc/self/fd/"))
                assertFalse(report, report.contains("/io.github.supermonster003.autojs6.plugin.mediainfo/cache/"))
            }
            captureIfRequested(night, "details")
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync {
                details?.finish()
                dialog.get()?.dismiss()
                main?.finish()
                ViewUtils.isAutoNightModeEnabled = originalAutoNight
                ViewUtils.isNightModeEnabled = originalNight
                AppCompatDelegate.setDefaultNightMode(originalMode)
            }
            if (demoPath == null) source.delete()
        }
    }

    private fun awaitMain(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        throw AssertionError(message)
    }

    private fun tap(view: View) {
        val position = IntArray(2)
        instrumentation.runOnMainSync {
            assertTrue("The MediaInfo button must be visible", view.isShown)
            view.getLocationOnScreen(position)
            position[0] += view.width / 2
            position[1] += view.height / 2
        }
        val downTime = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action,
                position[0].toFloat(), position[1].toFloat(), 0,
            )
            try {
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(40)
        }
    }

    private fun captureScriptIfRequested(night: Boolean) {
        if (arguments.getString("mediainfoScreenshots") != "true") return
        val path = arguments.getString("mediainfoDemoScript") ?: return
        val monitor = instrumentation.addMonitor(LogActivity::class.java.name, null, false)
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        var activity: LogActivity? = null
        val execution = AutoJs.instance.scriptEngineService.execute(
            JavaScriptFileSource(File(path)),
            object : SimpleScriptExecutionListener() {
                override fun onStart(execution: ScriptExecution) {
                    AutoJs.instance.globalConsole.clear()
                }
                override fun onSuccess(execution: ScriptExecution, result: Any?) = completed.countDown()
                override fun onException(execution: ScriptExecution, error: Throwable) {
                    failure.set(error)
                    completed.countDown()
                }
            },
            ExecutionConfig(workingDirectory = checkNotNull(File(path).absoluteFile.parent)),
        )
        try {
            assertTrue("The demo script did not finish", completed.await(30, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("The demo script failed", it) }
            activity = instrumentation.waitForMonitorWithTimeout(monitor, 10_000) as? LogActivity
                ?: throw AssertionError("The demo script did not open the console")
            captureIfRequested(night, "script")
        } finally {
            instrumentation.removeMonitor(monitor)
            if (completed.count > 0) execution.engine.forceStop()
            instrumentation.runOnMainSync { activity?.finish() }
            instrumentation.waitForIdleSync()
        }
    }

    private fun captureIfRequested(night: Boolean, screen: String) {
        if (arguments.getString("mediainfoScreenshots") != "true") return
        instrumentation.waitForIdleSync()
        SystemClock.sleep(500)
        val directory = File(context.getExternalFilesDir(null), "mediainfo-ui-evidence").apply { mkdirs() }
        val theme = if (night) "dark" else "light"
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(directory, "$theme-$screen.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
