package org.autojs.autojs

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.accessibility.LayoutInspector.CaptureAvailableListener
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.execution.ScriptExecutionGlobalListener
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.runtime.api.AppUtils.Companion.ActivityShortForm
import org.autojs.autojs.runtime.api.AppUtils.Companion.BroadcastShortForm
import org.autojs.autojs.runtime.exception.ScriptException
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.ui.doc.DocumentationActivity
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.floating.FullScreenFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyFloatyWindow
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.project.BuildActivity
import org.autojs.autojs.ui.settings.AboutActivity
import org.autojs.autojs.ui.settings.PreferencesActivity
import org.autojs.autojs6.R
import java.util.concurrent.Executors

/**
 * Created by Stardust on 2017/4/2.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 * Transformed by SuperMonster003 on Oct 10, 2022.
 */
class AutoJs private constructor(private val appContext: Application) : AbstractAutoJs(appContext) {

    // @Thank to Zen2H
    private val printExecutor = Executors.newSingleThreadExecutor()

    private val accessibilityTool: AccessibilityTool
        get() = AccessibilityTool(appContext)

    init {
        scriptEngineService.registerGlobalScriptExecutionListener(ScriptExecutionGlobalListener())

        LocalBroadcastManager.getInstance(appContext).registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    val action = intent.action ?: return
                    ensureAccessibilityServiceEnabled()
                    when {
                        action.equals(LayoutBoundsFloatyWindow::class.java.name, true) -> {
                            capture(object : LayoutInspectFloatyWindow {
                                override fun create(nodeInfo: NodeInfo?) = LayoutBoundsFloatyWindow(nodeInfo, context, true)
                            })
                        }
                        action.equals(LayoutHierarchyFloatyWindow::class.java.name, true) -> {
                            capture(object : LayoutInspectFloatyWindow {
                                override fun create(nodeInfo: NodeInfo?) = LayoutHierarchyFloatyWindow(nodeInfo, context, true)
                            })
                        }
                    }
                } catch (e: Exception) {
                    if (Looper.myLooper() != Looper.getMainLooper()) {
                        throw e
                    }
                }
            }
        }, IntentFilter().apply {
            addAction(LayoutBoundsFloatyWindow::class.java.name)
            addAction(LayoutHierarchyFloatyWindow::class.java.name)
        })
    }

    private interface LayoutInspectFloatyWindow {
        fun create(nodeInfo: NodeInfo?): FullScreenFloatyWindow?
    }

    private fun capture(window: LayoutInspectFloatyWindow) {
        val inspector = layoutInspector
        val listener: CaptureAvailableListener = object : CaptureAvailableListener {
            override fun onCaptureAvailable(capture: NodeInfo?, context: Context) {
                inspector.removeCaptureAvailableListener(this)
                uiHandler.post { FloatyWindowManger.addWindow(context, window.create(capture)) }
            }
        }
        inspector.addCaptureAvailableListener(listener)
        if (!inspector.captureCurrentWindow()) {
            inspector.removeCaptureAvailableListener(listener)
        }
    }

    override fun createAppUtils(context: Context) = AppUtils(context, AppFileProvider.AUTHORITY)

    override fun createGlobalConsole(): GlobalConsole {
        val devPluginService by lazy { App.app.devPluginService }
        return object : GlobalConsole(uiHandler) {
            override fun println(level: Int, charSequence: CharSequence): String {
                return super.println(level, charSequence).also {
                    // FIXME by SuperMonster003 as of Feb 2, 2022.
                    //  ! When running in "ui" thread (ui.run() or ui.post()),
                    //  ! android.os.NetworkOnMainThreadException may happen.
                    //  ! Further more, dunno if a thread executor is a good idea.
                    printExecutor.submit { devPluginService.print(it) }
                }
            }
        }
    }

    override fun ensureAccessibilityServiceEnabled() {
        if (AccessibilityService.isNotRunning()) {
            tryEnableAccessibilityService()?.let {
                accessibilityTool.launchSettings()
                throw ScriptException(it)
            }
        }
    }

    override fun waitForAccessibilityServiceEnabled(timeout: Long) {
        if (AccessibilityService.isNotRunning()) {
            tryEnableAccessibilityService()?.let {
                accessibilityTool.launchSettings()
                if (!AccessibilityService.waitForEnabled(timeout)) {
                    throw ScriptInterruptedException()
                }
            }
        }
    }

    private fun tryEnableAccessibilityService(): String? {
        if (accessibilityTool.service.isEnabled()) {
            return context.getString(R.string.text_auto_operate_service_enabled_but_not_working)
        }
        return if (!accessibilityTool.service.enableIfNeededAndWaitFor(2000)) {
            context.getString(R.string.error_no_accessibility_permission)
        } else null
    }

    override fun createRuntime(): ScriptRuntime = super.createRuntime().apply {

        /* Activities. */

        putProperty(ActivityShortForm.SETTINGS.fullName, PreferencesActivity::class.java)
        putProperty(ActivityShortForm.PREFERENCES.fullName, PreferencesActivity::class.java)
        putProperty(ActivityShortForm.PREF.fullName, PreferencesActivity::class.java)

        putProperty(ActivityShortForm.CONSOLE.fullName, LogActivity::class.java)
        putProperty(ActivityShortForm.LOG.fullName, LogActivity::class.java)

        putProperty(ActivityShortForm.HOMEPAGE.fullName, MainActivity::class.java)
        putProperty(ActivityShortForm.HOME.fullName, MainActivity::class.java)

        putProperty(ActivityShortForm.ABOUT.fullName, AboutActivity::class.java)

        putProperty(ActivityShortForm.BUILD.fullName, BuildActivity::class.java)

        putProperty(ActivityShortForm.DOCUMENTATION.fullName, DocumentationActivity::class.java)
        putProperty(ActivityShortForm.DOC.fullName, DocumentationActivity::class.java)
        putProperty(ActivityShortForm.DOCS.fullName, DocumentationActivity::class.java)

        /* Broadcasts. */

        putProperty(BroadcastShortForm.INSPECT_LAYOUT_BOUNDS.fullName, LayoutBoundsFloatyWindow::class.java.name)
        putProperty(BroadcastShortForm.LAYOUT_BOUNDS.fullName, LayoutBoundsFloatyWindow::class.java.name)
        putProperty(BroadcastShortForm.BOUNDS.fullName, LayoutBoundsFloatyWindow::class.java.name)

        putProperty(BroadcastShortForm.INSPECT_LAYOUT_HIERARCHY.fullName, LayoutHierarchyFloatyWindow::class.java.name)
        putProperty(BroadcastShortForm.LAYOUT_HIERARCHY.fullName, LayoutHierarchyFloatyWindow::class.java.name)
        putProperty(BroadcastShortForm.HIERARCHY.fullName, LayoutHierarchyFloatyWindow::class.java.name)
    }

    companion object {
        private var isInitialized = false

        @JvmStatic
        lateinit var instance: AutoJs
            private set

        @Synchronized
        @JvmStatic
        fun initInstance(application: Application) {
            if (!isInitialized) {
                instance = AutoJs(application)
                isInitialized = true
            }
        }
    }

}