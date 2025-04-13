package org.autojs.autojs

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.accessibility.Capture
import org.autojs.autojs.core.accessibility.LayoutInspector.CaptureAvailableListener
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.execution.ScriptExecutionGlobalListener
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.runtime.api.AppUtils.Companion.ActivityShortForm
import org.autojs.autojs.runtime.api.AppUtils.Companion.BroadcastShortForm
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.floating.FullScreenFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyFloatyWindow
import org.autojs.autojs.util.RhinoUtils.isBackgroundThread
import java.util.concurrent.Executors
import org.autojs.autojs.inrt.autojs.AutoJs as AutoJsInrt

/**
 * Created by Stardust on Apr 2, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 * Transformed by SuperMonster003 on Oct 10, 2022.
 * Modified by LZX284 (https://github.com/LZX284) as of Sep 30, 2023.
 */
open class AutoJs(appContext: Application) : AbstractAutoJs(appContext) {

    // @Thank to Zen2H
    private val mPrintExecutor = Executors.newSingleThreadExecutor()

    private val mA11yTool = AccessibilityTool(appContext)

    init {
        scriptEngineService.registerGlobalScriptExecutionListener(ScriptExecutionGlobalListener())

        LocalBroadcastManager.getInstance(appContext).registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    val action = intent.action ?: return
                    when {
                        action.equals(LayoutBoundsFloatyWindow::class.java.name, true) -> {
                            mA11yTool.ensureService()
                            capture(object : LayoutInspectFloatyWindow {
                                override fun create(capture: Capture) = LayoutBoundsFloatyWindow(capture, context, true)
                            })
                        }
                        action.equals(LayoutHierarchyFloatyWindow::class.java.name, true) -> {
                            mA11yTool.ensureService()
                            capture(object : LayoutInspectFloatyWindow {
                                override fun create(capture: Capture) = LayoutHierarchyFloatyWindow(capture, context, true)
                            })
                        }
                    }
                } catch (e: Exception) {
                    if (isBackgroundThread()) {
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
        fun create(capture: Capture): FullScreenFloatyWindow
    }

    private fun capture(window: LayoutInspectFloatyWindow) {
        val inspector = layoutInspector
        val listener: CaptureAvailableListener = object : CaptureAvailableListener {
            override fun onCaptureAvailable(capture: Capture, context: Context) {
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
                    //  ! Furthermore, dunno if a thread executor is a good idea.
                    //  ! 当在 "ui" 线程执行时 (如 ui.run() 或 ui.post()),
                    //  ! 可能会发生 android.os.NetworkOnMainThreadException 异常.
                    //  ! 而且, 我不确定使用线程执行器是否是个好主意.
                    mPrintExecutor.submit { devPluginService.print(it) }
                }
            }
        }
    }

    override fun createRuntime(): ScriptRuntime = super.createRuntime().apply {

        /* Activities. */

        listOf(
            ActivityShortForm.SETTINGS, ActivityShortForm.PREFERENCES, ActivityShortForm.PREF,
            ActivityShortForm.DOCUMENTATION, ActivityShortForm.DOC, ActivityShortForm.DOCS,
            ActivityShortForm.HOMEPAGE, ActivityShortForm.HOME,
            ActivityShortForm.CONSOLE, ActivityShortForm.LOG,
            ActivityShortForm.ABOUT,
            ActivityShortForm.BUILD,
        ).forEach { putProperty(it.fullName, it.classType) }

        /* Broadcasts. */

        listOf(
            BroadcastShortForm.INSPECT_LAYOUT_BOUNDS, BroadcastShortForm.LAYOUT_BOUNDS, BroadcastShortForm.BOUNDS,
            BroadcastShortForm.INSPECT_LAYOUT_HIERARCHY, BroadcastShortForm.LAYOUT_HIERARCHY, BroadcastShortForm.HIERARCHY,
        ).forEach { putProperty(it.fullName, it.className) }
    }

    companion object {
        private var isInitialized = false
        private val TAG = AutoJs::class.java.simpleName

        @SuppressLint("StaticFieldLeak")
        @JvmStatic
        lateinit var instance: AutoJs
            private set

        @Synchronized
        @JvmStatic
        fun initInstance(application: Application) {
            Log.d(TAG, "AutoJs isInitialized: $isInitialized")
            if (!isInitialized) {
                instance = when (isInrt) {
                    true -> AutoJsInrt(application)
                    else -> AutoJs(application)
                }
                isInitialized = true
            }
        }
    }

}