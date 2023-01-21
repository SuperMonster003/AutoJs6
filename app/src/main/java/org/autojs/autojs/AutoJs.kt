package org.autojs.autojs

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.autojs.autojs.core.accessibility.AccessibilityServiceTool
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.LayoutInspector.CaptureAvailableListener
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.execution.ScriptExecutionGlobalListener
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.pluginclient.DevPluginService
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.runtime.exception.ScriptException
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.floating.FullScreenFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyFloatyWindow
import org.autojs.autojs.ui.log.LogActivity_
import org.autojs.autojs.ui.settings.SettingsActivity_
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

    private val accessibilityTool: AccessibilityServiceTool
        get() = AccessibilityServiceTool(appContext)

    init {
        scriptEngineService.registerGlobalScriptExecutionListener(ScriptExecutionGlobalListener())

        LocalBroadcastManager.getInstance(appContext).registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    ensureAccessibilityServiceEnabled()
                    when (intent.action) {
                        LayoutBoundsFloatyWindow::class.java.name -> capture(object : LayoutInspectFloatyWindow {
                            override fun create(nodeInfo: NodeInfo?) = LayoutBoundsFloatyWindow(nodeInfo, context)
                        })
                        LayoutHierarchyFloatyWindow::class.java.name -> capture(object : LayoutInspectFloatyWindow {
                            override fun create(nodeInfo: NodeInfo?) = LayoutHierarchyFloatyWindow(nodeInfo, context)
                        })
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
                accessibilityTool.goToAccessibilitySetting()
                throw ScriptException(it)
            }
        }
    }

    override fun waitForAccessibilityServiceEnabled(timeout: Long) {
        if (AccessibilityService.isNotRunning()) {
            tryEnableAccessibilityService()?.let {
                accessibilityTool.goToAccessibilitySetting()
                if (!AccessibilityService.waitForEnabled(timeout)) {
                    throw ScriptInterruptedException()
                }
            }
        }
    }

    private fun tryEnableAccessibilityService(): String? {
        if (accessibilityTool.isAccessibilityServiceEnabled()) {
            return context.getString(R.string.text_auto_operate_service_enabled_but_not_working)
        }
        return if (!accessibilityTool.enableAccessibilityServiceAutomaticallyIfNeededAndWaitFor(2000)) {
            context.getString(R.string.text_no_accessibility_permission)
        } else null
    }

    override fun createRuntime(): ScriptRuntime = super.createRuntime().apply {
        putProperty("class.settings", SettingsActivity_::class.java)
        putProperty("class.console", LogActivity_::class.java)
        putProperty("broadcast.inspect_layout_bounds", LayoutBoundsFloatyWindow::class.java.name)
        putProperty("broadcast.inspect_layout_hierarchy", LayoutHierarchyFloatyWindow::class.java.name)
    }

    companion object {
        private var isInitialized = false

        @JvmStatic
        lateinit var instance: AutoJs
            private set

        @Synchronized
        fun initInstance(application: Application) {
            if (!isInitialized) {
                instance = AutoJs(application)
                isInitialized = true
            }
        }
    }

}