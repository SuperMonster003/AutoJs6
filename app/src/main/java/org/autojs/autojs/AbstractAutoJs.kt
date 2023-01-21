package org.autojs.autojs

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import org.autojs.autojs.app.SimpleActivityLifecycleCallbacks
import org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl
import org.autojs.autojs.core.accessibility.AccessibilityNotificationObserver
import org.autojs.autojs.core.accessibility.AccessibilityService.Companion.addDelegate
import org.autojs.autojs.core.accessibility.LayoutInspector
import org.autojs.autojs.core.activity.ActivityInfoProvider
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.core.image.capture.ScreenCaptureRequesterImpl
import org.autojs.autojs.core.record.accessibility.AccessibilityActionRecorder
import org.autojs.autojs.engine.LoopBasedJavaScriptEngine
import org.autojs.autojs.engine.RootAutomatorEngine
import org.autojs.autojs.engine.ScriptEngineManager
import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.engine.ScriptEngineServiceBuilder
import org.autojs.autojs.rhino.InterruptibleAndroidContextFactory
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.accessibility.AccessibilityConfig
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.Shell
import org.autojs.autojs.script.AutoFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.tool.UiHandler
import org.autojs.autojs.util.ResourceMonitor
import org.autojs.autojs.util.ResourceMonitor.UnclosedResourceException
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.WrappedException
import java.io.File

/**
 * Created by Stardust on 2017/11/29.
 * Modified by SuperMonster003 as of Jun 10, 2022.
 * Transformed by SuperMonster003 on Oct 10, 2022.
 */
abstract class AbstractAutoJs protected constructor(protected val application: Application) {

    lateinit var runtime: ScriptRuntime
        private set

    val context: Context = application.applicationContext

    val appUtils by lazy { createAppUtils(context) }
    val globalConsole by lazy { createGlobalConsole() }

    val layoutInspector = LayoutInspector(context)
    val uiHandler = UiHandler(context)
    val infoProvider = ActivityInfoProvider(context)
    val scriptEngineManager = ScriptEngineManager(context)
    val scriptEngineService: ScriptEngineService = run {
        scriptEngineManager.registerEngine(JavaScriptSource.ENGINE) {
            LoopBasedJavaScriptEngine(context).also { engine ->
                engine.runtime = createRuntime().also { runtime = it }
            }
        }
        initContextFactory()
        scriptEngineManager.registerEngine(AutoFileSource.ENGINE) { RootAutomatorEngine(context) }
        ScriptEngineServiceBuilder()
            .uiHandler(uiHandler)
            .globalConsole(globalConsole)
            .engineManger(scriptEngineManager)
            .build()
            .also { ScriptEngineService.setInstance(it) }
    }

    val notificationObserver = AccessibilityNotificationObserver(context)

    private val accessibilityActionRecorder = AccessibilityActionRecorder()

    init {
        addAccessibilityServiceDelegates()
        registerActivityLifecycleCallbacks()
        getResourceMonitorReady()
    }

    private fun initContextFactory() {
        ContextFactory.initGlobal(InterruptibleAndroidContextFactory(File(context.cacheDir, "classes")))
    }

    protected open fun createRuntime(): ScriptRuntime = ScriptRuntime.Builder()
        .setConsole(ConsoleImpl(uiHandler, globalConsole))
        .setUiHandler(uiHandler)
        .setScreenCaptureRequester(ScreenCaptureRequesterImpl(this))
        .setAccessibilityBridge(AccessibilityBridgeImpl(this))
        .setAppUtils(appUtils)
        .setEngineService(scriptEngineService)
        .setShellSupplier { Shell(context, true) }
        .build()

    private fun addAccessibilityServiceDelegates() {
        addDelegate(100, infoProvider)
        addDelegate(200, notificationObserver)
        addDelegate(300, accessibilityActionRecorder)
    }

    private fun registerActivityLifecycleCallbacks() {
        application.registerActivityLifecycleCallbacks(object : SimpleActivityLifecycleCallbacks() {

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                ScreenMetrics.initIfNeeded(activity)
                appUtils.setCurrentActivity(activity)
            }

            override fun onActivityPaused(activity: Activity) = appUtils.setCurrentActivity(null)

            override fun onActivityResumed(activity: Activity) = appUtils.setCurrentActivity(activity)

        })
    }

    private fun getResourceMonitorReady() {
        ResourceMonitor.setExceptionCreator { resource: ResourceMonitor.Resource? ->
            if (org.mozilla.javascript.Context.getCurrentContext() != null) {
                WrappedException(UnclosedResourceException(resource))
            } else {
                UnclosedResourceException(resource)
            }.apply { fillInStackTrace() }
        }
        ResourceMonitor.setUnclosedResourceDetectedHandler { unclosedResourceDetectedException ->
            globalConsole.error(unclosedResourceDetectedException)
        }
    }

    abstract fun ensureAccessibilityServiceEnabled()

    abstract fun waitForAccessibilityServiceEnabled(timeout: Long)

    open fun createAppUtils(context: Context) = AppUtils(context)

    open fun createGlobalConsole() = GlobalConsole(uiHandler)

    fun createAccessibilityConfig() = AccessibilityConfig()

}