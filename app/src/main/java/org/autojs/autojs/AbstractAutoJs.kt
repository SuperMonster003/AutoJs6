package org.autojs.autojs

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import org.autojs.autojs.app.SimpleActivityLifecycleCallbacks
import org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl
import org.autojs.autojs.core.accessibility.AccessibilityNotificationObserver
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.accessibility.LayoutInspector
import org.autojs.autojs.core.activity.ActivityInfoProvider
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.core.plugin.dex.AndroidDexCompilerRuntime
import org.autojs.autojs.core.plugin.jvm.RemoteJvmSourceHost
import org.autojs.autojs.core.pref.Pref.registerOnSharedPreferenceChangeListener
import org.autojs.autojs.core.record.accessibility.AccessibilityActionRecorder
import org.autojs.autojs.engine.LoopBasedJavaScriptEngine
import org.autojs.autojs.engine.AutoJs4PluginScriptEngine
import org.autojs.autojs.engine.LuaPluginScriptEngine
import org.autojs.autojs.engine.BunPluginScriptEngine
import org.autojs.autojs.engine.NodeJavaScriptEngineFactory
import org.autojs.autojs.engine.PythonPluginScriptEngine
import org.autojs.autojs.engine.QuickJsPluginScriptEngine
import org.autojs.autojs.engine.RemoteJvmScriptEngine
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
import org.autojs.autojs.script.AutoJs4ScriptSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.JvmFileSource
import org.autojs.autojs.script.LuaScriptSource
import org.autojs.autojs.script.PythonRuntimeChannelPolicy
import org.autojs.autojs.script.PythonScriptSource
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

    /** The shared accessibility tool for callers without a context of their own (bridges, selectors, the layout inspector). zh-CN: 供没有自身 context 的调用方 (bridge, 选择器, 布局分析器) 共享的无障碍工具. */
    val accessibilityTool = AccessibilityTool(applicationContext)
    val layoutInspector = LayoutInspector(applicationContext, accessibilityTool)
    val uiHandler = UiHandler(applicationContext)
    val infoProvider = ActivityInfoProvider(applicationContext)
    val scriptEngineService: ScriptEngineService = run {
        val scriptEngineManager = ScriptEngineManager(applicationContext)
        scriptEngineManager.registerEngine(JavaScriptSource.ENGINE) {
            val rt = createRuntime()
            when (isInrt) {
                true -> LoopBasedJavaScriptEngineWithDecryption(applicationContext)
                else -> LoopBasedJavaScriptEngine(applicationContext)
            }.also { it.runtime = rt }
        }
        scriptEngineManager.registerEngine(JavaScriptSource.ENGINE_NODEJS) {
            val rt = createRuntime()
            NodeJavaScriptEngineFactory.create(applicationContext).also { it.runtime = rt }
        }
        if (PythonRuntimeChannelPolicy.isEnabledForCurrentBuild) {
            scriptEngineManager.registerEngine(PythonScriptSource.ENGINE) {
                PythonPluginScriptEngine(applicationContext, globalConsole)
            }
        }
        if (!isInrt) {
            scriptEngineManager.registerEngine(JavaScriptSource.ENGINE_BUN) {
                BunPluginScriptEngine(applicationContext, globalConsole)
            }
            scriptEngineManager.registerEngine(JavaScriptSource.ENGINE_QUICKJS) {
                QuickJsPluginScriptEngine(applicationContext, globalConsole)
            }
            scriptEngineManager.registerEngine(AutoJs4ScriptSource.ENGINE) {
                AutoJs4PluginScriptEngine(applicationContext, globalConsole)
            }
        }
        scriptEngineManager.registerEngine(LuaScriptSource.ENGINE) {
            LuaPluginScriptEngine(applicationContext)
        }
        if (BuildConfig.JVM_SOURCE_EXPERIMENTAL_ENABLED) {
            scriptEngineManager.registerEngine(JvmFileSource.ENGINE) {
                RemoteJvmScriptEngine(
                    RemoteJvmSourceHost.createAndroid(
                        context = applicationContext,
                        featureEnabled = BuildConfig.JVM_SOURCE_EXPERIMENTAL_ENABLED,
                        hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
                        launchPackage = appUtils::launchPackage,
                        hasResumedActivity = { appUtils.currentActivity != null },
                    ),
                    globalConsole,
                )
            }
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
        AndroidDexCompilerRuntime.initialize(
            context = applicationContext,
            eligibleProcess = App.getProcessNameCompat() == applicationContext.applicationInfo.processName,
        )
        ContextFactory.initGlobal(InterruptibleAndroidContextFactory(File(applicationContext.cacheDir, "classes")))
    }

    protected open fun createRuntime(): ScriptRuntime = ScriptRuntime.Builder()
        // Each runtime owns its console (its own entries, its own floating window) and forwards
        // every line to the global console shown by the log page.
        // zh-CN: 每个运行时拥有自身的控制台 (独立条目与浮动窗口), 并把每一行转发给日志页显示的全局控制台.
        .setConsole(ConsoleImpl(uiHandler, globalConsole))
        .setUiHandler(uiHandler)
        .setAccessibilityBridge(createAccessibilityBridge().also { AccessibilityService.bridge = it })
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
