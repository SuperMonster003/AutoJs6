package org.autojs.autojs

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import com.benjaminwan.ocrlibrary.OcrEngine
import org.autojs.autojs.app.SimpleActivityLifecycleCallbacks
import org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl
import org.autojs.autojs.core.accessibility.AccessibilityNotificationObserver
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.LayoutInspector
import org.autojs.autojs.core.activity.ActivityInfoProvider
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.core.pref.Pref.registerOnSharedPreferenceChangeListener
import org.autojs.autojs.core.record.accessibility.AccessibilityActionRecorder
import org.autojs.autojs.engine.LoopBasedJavaScriptEngine
import org.autojs.autojs.engine.RootAutomatorEngine
import org.autojs.autojs.engine.ScriptEngineManager
import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.engine.ScriptEngineServiceBuilder
import org.autojs.autojs.inrt.autojs.LoopBasedJavaScriptEngineWithDecryption
import org.autojs.autojs.rhino.InterruptibleAndroidContextFactory
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.Shell
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.script.AutoFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.tool.UiHandler
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.mozilla.javascript.ContextFactory
import java.io.File

/**
 * Created by Stardust on Nov 29, 2017.
 * Modified by SuperMonster003 as of Jun 10, 2022.
 * Transformed by SuperMonster003 on Oct 10, 2022.
 * Modified by LZX284 (https://github.com/LZX284) as of Sep 30, 2023.
 */
abstract class AbstractAutoJs protected constructor(val application: Application) {

    val applicationContext: Context = application.applicationContext
    val appUtils by lazy { createAppUtils(applicationContext) }
    val globalConsole by lazy { createGlobalConsole() }

    val rapidOcrEngine by lazy { OcrEngine(applicationContext) }

    val layoutInspector = LayoutInspector(applicationContext)
    val uiHandler = UiHandler(applicationContext)
    val infoProvider = ActivityInfoProvider(applicationContext)
    val scriptEngineService: ScriptEngineService = run {
        val scriptEngineManager = ScriptEngineManager(applicationContext)
        scriptEngineManager.registerEngine(JavaScriptSource.ENGINE) {
            val rt = createRuntime()
            when (isInrt) {
                true -> LoopBasedJavaScriptEngineWithDecryption(rt, applicationContext)
                else -> LoopBasedJavaScriptEngine(rt, applicationContext)
            }.also { it.runtime = rt }
        }
        initContextFactory()
        scriptEngineManager.registerEngine(AutoFileSource.ENGINE) { RootAutomatorEngine(applicationContext) }
        ScriptEngineServiceBuilder()
            .uiHandler(uiHandler)
            .globalConsole(globalConsole)
            .engineManger(scriptEngineManager)
            .build()
            .also { ScriptEngineService.setInstance(it) }
    }

    val notificationObserver = AccessibilityNotificationObserver(applicationContext)

    private val accessibilityActionRecorder = AccessibilityActionRecorder()

    init {
        addAccessibilityServiceDelegates()
        registerActivityLifecycleCallbacks()
        WrappedShizuku.onCreate()
    }

    private fun initContextFactory() {
        ContextFactory.initGlobal(InterruptibleAndroidContextFactory(File(applicationContext.cacheDir, "classes")))
    }

    protected open fun createRuntime(): ScriptRuntime = ScriptRuntime.Builder()
        .setConsole(globalConsole)
        .setUiHandler(uiHandler)
        .setAccessibilityBridge(createAccessibilityBridge())
        .setAppUtils(appUtils)
        .setShellSupplier { Shell(applicationContext, true) }
        .build()

    fun createAccessibilityBridge() = AccessibilityBridgeImpl(this)

    private fun addAccessibilityServiceDelegates() {
        AccessibilityService.addDelegate(100, infoProvider)
        AccessibilityService.addDelegate(200, notificationObserver)
        AccessibilityService.addDelegate(300, accessibilityActionRecorder)
    }

    private fun registerActivityLifecycleCallbacks() {
        application.registerActivityLifecycleCallbacks(object : SimpleActivityLifecycleCallbacks() {

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                ScreenMetrics.init(activity)
                appUtils.setCurrentActivity(activity)
                registerOnSharedPreferenceChangeListener { _, key ->
                    if (key == StringUtils.key(R.string.key_keep_screen_on_when_in_foreground)) {
                        configKeepScreenOnWhenInForeground(activity)
                    }
                }
            }

            override fun onActivityPaused(activity: Activity) = appUtils.setCurrentActivity(null)

            override fun onActivityResumed(activity: Activity) {
                configKeepScreenOnWhenInForeground(activity)
                appUtils.setCurrentActivity(activity)
            }

            private fun configKeepScreenOnWhenInForeground(activity: Activity) {
                if (ViewUtils.isKeepScreenOnWhenInForegroundAllPages) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        })
    }

    open fun createAppUtils(context: Context) = AppUtils(context)

    open fun createGlobalConsole() = GlobalConsole(uiHandler)

    companion object {

        @JvmStatic
        @Suppress("KotlinConstantConditions")
        val isInrt get() = BuildConfig.isInrt

    }

}