package org.autojs.autojs

import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.util.Log
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import com.hjq.toast.Toaster
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.accessibility.companion.AccessibilityCompanionSync
import org.autojs.autojs.core.plugin.terminal.TerminalLegacyCleanup
import org.autojs.autojs.core.plugin.explorer.output.ExplorerActionOutputCoordinator
import org.autojs.autojs.core.plugin.opencv.OpenCvPluginHost
import org.autojs.autojs.core.plugin.typescript.TypeScriptCompilerStartupPrewarm
import org.autojs.autojs.core.pref.archive.AndroidSettingsAutoBackup
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.core.ui.inflater.ImageLoader
import org.autojs.autojs.core.ui.inflater.util.Drawables
import org.autojs.autojs.engine.NodeJsRuntimePluginHost
import org.autojs.autojs.event.GlobalKeyObserver
import org.autojs.autojs.external.receiver.DynamicBroadcastReceivers
import org.autojs.autojs.inrt.InrtShortcuts
import org.autojs.autojs.ipc.InAppEventBus
import org.autojs.autojs.leakcanary.LeakCanarySetup
import org.autojs.autojs.storage.file.TmpScriptFilesCleanupScheduler
import org.autojs.autojs.storage.history.HistoryCleanupScheduler
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.intelligent.IntelligentColorEngine
import org.autojs.autojs.timing.TimedTaskManager
import org.autojs.autojs.timing.TimedTaskScheduler
import org.autojs.autojs.tool.CrashHandler
import org.autojs.autojs.ui.error.CrashReportActivity
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.greenrobot.eventbus.EventBus
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import androidx.work.Configuration as WorkManagerConfiguration
import org.autojs.autojs.inrt.Pref as InrtPref
import org.autojs.autojs.theme.app.ScriptThemeChanges

/**
 * Created by Stardust on Jan 27, 2017.
 * Modified by SuperMonster003 as of Feb 3, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 7, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 9, 2026.
 */
class App : Application(), WorkManagerConfiguration.Provider {

    lateinit var dynamicBroadcastReceivers: DynamicBroadcastReceivers
        private set

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val deferredNodePluginPrewarmScheduled = AtomicBoolean(false)
    private val deferredTypeScriptCompilerPrewarmScheduled = AtomicBoolean(false)
    private val deferredTypeScriptCompilerStartupReleaseScheduled = AtomicBoolean(false)
    private val deferredMaintenanceScheduled = AtomicBoolean(false)

    private val deferredNodePluginPrewarm = Runnable {
        if (deferredNodePluginPrewarmScheduled.compareAndSet(true, false)) {
            Thread(::prewarmNodePluginIfNeeded, "AutoJs6-Node-Plugin-StartupPrewarm").start()
        }
    }

    private val deferredTypeScriptCompilerPrewarm = Runnable {
        if (deferredTypeScriptCompilerPrewarmScheduled.compareAndSet(true, false)) {
            TypeScriptCompilerStartupPrewarm.requestAsync(this, "app-start")
            scheduleTypeScriptCompilerStartupRelease()
        }
    }

    private val deferredTypeScriptCompilerStartupRelease = Runnable {
        if (deferredTypeScriptCompilerStartupReleaseScheduled.compareAndSet(true, false)) {
            TypeScriptCompilerStartupPrewarm.release("app-start-idle-timeout")
        }
    }

    private val deferredMaintenance = Runnable {
        if (deferredMaintenanceScheduled.compareAndSet(true, false)) {
            Thread(::scheduleMaintenanceWork, "AutoJs6-startup-maintenance").start()
        }
    }

    override fun onCreate() {
        super.onCreate()

        when {
            ":crash_report".matchesProcessNameSuffix() -> {
                ThemeColorManager.init()
                setUpDefaultNightMode()
            }
            else /* Main process. */ -> {
                if (getProcessNameCompat() == applicationInfo.processName && !AbstractAutoJs.isInrt) {
                    // Decodes the active intelligent library while the rest of onCreate runs (roadmap P7.4).
                    // zh-CN: 在 onCreate 其余部分运行期间解码激活的智能库 (Roadmap P7.4).
                    IntelligentColorEngine.preload()
                }
                if (AbstractAutoJs.isInrt) {
                    InrtPref.syncLaunchConfigWithBuild()
                    InrtShortcuts.syncToExplicitIntents()
                }
                setUpDebugEnvironment()
                setUpLeakCanary()
                if (getProcessNameCompat() == applicationInfo.processName) {
                    EditorEnginePreferences.initialize(this)
                    scheduleNodePluginPrewarmIfNeeded()
                    scheduleTypeScriptCompilerPrewarmIfNeeded()
                    if (!AbstractAutoJs.isInrt) {
                        OpenCvPluginHost.preload(this)
                    }
                }

                AutoJs.initInstance(this)
                AccessibilityCompanionSync.install(this)
                GlobalKeyObserver.initIfNeeded(applicationContext)
                setupDrawableImageLoader()
                TimedTaskScheduler.init(this)
                initDynamicBroadcastReceivers()
                Toaster.init(this)

                ThemeColorManager.init()
                setUpDefaultNightMode()

                if (getProcessNameCompat() == applicationInfo.processName) {
                    if (!AbstractAutoJs.isInrt) {
                        // Before any Activity draws, so a due change shows no flash (roadmap D9).
                        // zh-CN: 在任何 Activity 绘制之前执行, 使到期的变化不会闪色 (Roadmap D9).
                        IntelligentColorEngine.init(this)
                        ScriptThemeChanges.init(this)
                        org.autojs.autojs.core.plugin.center.PluginLauncherIconController.initialize(this)
                        org.autojs.autojs.core.plugin.agent.ThreeStoveAgentConnectionOwner.get(this).refresh()
                    }
                }
                scheduleDeferredMaintenance()
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Providers are published before Application.onCreate and can already receive
        // plugin appearance requests. Publish context references before that boundary.
        instance = WeakReference(this)
        GlobalAppContext.set(this)
        Log.d("Shizuku", "${App::class.java.simpleName} attachBaseContext | Process=${getProcessNameCompat()}")
    }

    private fun setUpDebugEnvironment() {
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 36) {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
                    .detectBlockedBackgroundActivityLaunch()
                    .penaltyLog()
                    .build(),
            )
        }
        val crashHandler = CrashHandler(CrashReportActivity::class.java)
        Thread.setDefaultUncaughtExceptionHandler(crashHandler)
    }

    private fun setUpLeakCanary() {
        LeakCanarySetup.setup(this)
    }

    private fun setUpDefaultNightMode() {
        ViewUtils.AutoNightMode.dysfunctionIfNeeded()
        ViewUtils.setDefaultNightMode(
            when {
                ViewUtils.isAutoNightModeEnabled -> ViewUtils.MODE.FOLLOW
                AppearanceKeys.NIGHT_MODE_ENABLED.isStored() -> {
                    when (ViewUtils.isNightModeEnabled) {
                        true -> ViewUtils.MODE.NIGHT
                        else -> ViewUtils.MODE.DAY
                    }
                }
                else -> ViewUtils.MODE.NULL
            }
        )
    }

    @SuppressLint("CheckResult")
    private fun initDynamicBroadcastReceivers() {
        dynamicBroadcastReceivers = DynamicBroadcastReceivers(this)
        val localActions = ArrayList<String>()
        val systemActions = ArrayList<String>()
        TimedTaskManager.allIntentTasks
            .filter { task -> task.action != null }
            .doOnComplete {
                if (localActions.isNotEmpty()) {
                    dynamicBroadcastReceivers.register(localActions, true)
                }
                if (systemActions.isNotEmpty()) {
                    dynamicBroadcastReceivers.register(systemActions, false)
                }

                // @Archived by SuperMonster003 on Sep 27, 2025.
                //  ! LocalBroadcastManager is deprecated.
                //  ! zh-CN: LocalBroadcastManager 已被弃用.
                //  # Intent(DynamicBroadcastReceivers.ACTION_STARTUP).let {
                //  #     LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(it)
                //  # }

                // @Hint by JetBrains AI Assistant on Sep 27, 2025.
                //  ! Why schedule on next main-loop tick?
                //  ! Post the emit to the next frame on the main thread to avoid a race where
                //  ! the SharedFlow collectors (registered via launchIn on Main) are not yet active
                //  ! at the exact moment of emission. Emitting in the next loop ensures all
                //  ! subscriptions are set up, preventing event loss when replay=0.
                //  ! zh-CN:
                //  ! 为什么要在主线程的下一帧再触发?
                //  ! 将触发放到主线程消息队列的下一轮执行, 避免 "先发后订" 的竞态:
                //  ! 订阅 (launchIn(Main)) 尚未真正激活时如果立即发送, replay=0 会丢事件.
                //  ! 下一帧再发可以确保收集器已就绪, 从而可靠接收启动事件.
                AndroidSchedulers.mainThread().scheduleDirect {
                    InAppEventBus.tryEmit(DynamicBroadcastReceivers.ACTION_STARTUP)
                }
            }
            .subscribe({
                if (it.isLocal) {
                    localActions.add(it.action)
                } else {
                    systemActions.add(it.action)
                }
            }, { it.printStackTrace() })

    }

    private fun setupDrawableImageLoader() {
        Drawables.defaultImageLoader = object : ImageLoader {
            override fun loadInto(imageView: ImageView, uri: Uri) {
                Glide.with(imageView)
                    .load(uri)
                    .into(imageView)
            }

            override fun loadIntoBackground(view: View, uri: Uri) {
                Glide.with(view)
                    .load(uri)
                    .into(object : CustomViewTarget<View, Drawable>(view) {
                        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                            view.background = resource
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            view.background = null
                        }

                        override fun onResourceCleared(placeholder: Drawable?) {
                            view.background = null
                        }
                    })
            }

            override fun load(view: View, uri: Uri): Drawable {
                throw UnsupportedOperationException()
            }

            override fun load(view: View, uri: Uri, drawableCallback: ImageLoader.DrawableCallback) {
                Glide.with(view)
                    .load(uri)
                    .into(object : CustomViewTarget<View, Drawable>(view) {
                        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                            drawableCallback.onLoaded(resource)
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            drawableCallback.onLoaded(null)
                        }

                        override fun onResourceCleared(placeholder: Drawable?) {
                            drawableCallback.onLoaded(null)
                        }
                    })
            }

            override fun load(view: View, uri: Uri, bitmapCallback: ImageLoader.BitmapCallback) {
                Glide.with(view)
                    .asBitmap()
                    .load(uri)
                    .into(object : CustomViewTarget<View, Bitmap>(view) {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            bitmapCallback.onLoaded(resource)
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            bitmapCallback.onLoaded(null)
                        }

                        override fun onResourceCleared(placeholder: Drawable?) {
                            bitmapCallback.onLoaded(null)
                        }
                    })
            }
        }
    }

    private fun prewarmNodePluginIfNeeded() {
        if (!BuildConfig.isInrt) {
            runCatching {
                runBlocking { NodeJsRuntimePluginHost.prewarm(this@App, android.os.Bundle().apply {
                    putInt(org.autojs.plugin.nodejs.api.NodeJsRuntimeContract.KEY_PREWARM_SLOTS, 1)
                }) }
            }.onFailure { Log.w(TAG, "Failed to prewarm external Node.js runtime plugin.", it) }
        }
    }

    private fun scheduleNodePluginPrewarmIfNeeded() {
        if (!BuildConfig.isInrt && deferredNodePluginPrewarmScheduled.compareAndSet(false, true)) {
            mainHandler.postDelayed(deferredNodePluginPrewarm, NODE_PLUGIN_PREWARM_DELAY_MILLIS)
        }
    }

    /**
     * Cancels only an application-startup prewarm that has not begun yet. A real Node request
     * must take precedence so that the delayed task cannot start competing service processes.
     */
    internal fun cancelDeferredNodePluginPrewarm() {
        if (deferredNodePluginPrewarmScheduled.compareAndSet(true, false)) {
            mainHandler.removeCallbacks(deferredNodePluginPrewarm)
        }
    }

    private fun scheduleTypeScriptCompilerPrewarmIfNeeded() {
        if (
            !BuildConfig.isInrt &&
            deferredTypeScriptCompilerPrewarmScheduled.compareAndSet(false, true)
        ) {
            mainHandler.postDelayed(
                deferredTypeScriptCompilerPrewarm,
                TYPESCRIPT_COMPILER_PREWARM_DELAY_MILLIS,
            )
        }
    }

    private fun scheduleTypeScriptCompilerStartupRelease() {
        if (deferredTypeScriptCompilerStartupReleaseScheduled.compareAndSet(false, true)) {
            mainHandler.postDelayed(
                deferredTypeScriptCompilerStartupRelease,
                TYPESCRIPT_COMPILER_STARTUP_KEEPALIVE_MILLIS,
            )
        }
    }

    /** A real compile/editor visit cancels both delayed startup work and its idle expiry. */
    internal fun cancelDeferredTypeScriptCompilerPrewarm() {
        if (deferredTypeScriptCompilerPrewarmScheduled.compareAndSet(true, false)) {
            mainHandler.removeCallbacks(deferredTypeScriptCompilerPrewarm)
        }
        if (deferredTypeScriptCompilerStartupReleaseScheduled.compareAndSet(true, false)) {
            mainHandler.removeCallbacks(deferredTypeScriptCompilerStartupRelease)
        }
    }

    private fun scheduleDeferredMaintenance() {
        if (deferredMaintenanceScheduled.compareAndSet(false, true)) {
            mainHandler.postDelayed(deferredMaintenance, MAINTENANCE_SCHEDULING_DELAY_MILLIS)
        }
    }

    private fun scheduleMaintenanceWork() {
        if (getProcessNameCompat() == applicationInfo.processName) {
            runCatching { ExplorerActionOutputCoordinator.recover(this) }
                .onFailure { Log.w(TAG, "Failed to recover Explorer Action output transactions.", it) }
            runCatching { TerminalLegacyCleanup.schedule(this) }
                .onFailure { Log.w(TAG, "Failed to schedule the legacy terminal cleanup.", it) }
            if (!AbstractAutoJs.isInrt) {
                runCatching { AndroidSettingsAutoBackup.start(this) }
                    .onFailure { Log.w(TAG, "Failed to start the automatic settings backups.", it) }
            }
        }
        runCatching { HistoryCleanupScheduler.scheduleStartupCleanup(this) }
            .onFailure { Log.w(TAG, "Failed to schedule startup history cleanup.", it) }
        runCatching { HistoryCleanupScheduler.schedulePeriodicCleanup(this) }
            .onFailure { Log.w(TAG, "Failed to schedule periodic history cleanup.", it) }
        runCatching { TmpScriptFilesCleanupScheduler.scheduleStartupCleanup(this) }
            .onFailure { Log.w(TAG, "Failed to schedule startup temporary script cleanup.", it) }
        runCatching { TmpScriptFilesCleanupScheduler.schedulePeriodicCleanup(this) }
            .onFailure { Log.w(TAG, "Failed to schedule periodic temporary script cleanup.", it) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        ViewUtils.onConfigurationChangedForNightMode(newConfig)
        EventBus.getDefault().post(newConfig)
        FloatyWindowManger.getCircularMenu()?.savePosition(newConfig)
        super.onConfigurationChanged(newConfig)
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW &&
            level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            TypeScriptCompilerStartupPrewarm.release("trim-memory-$level")
        }
    }

    override fun onLowMemory() {
        TypeScriptCompilerStartupPrewarm.release("low-memory")
        super.onLowMemory()
    }

    fun clear() {
        clearInternal()
    }

    fun clearForExit() = clearInternal()

    private fun clearInternal() {
        cancelDeferredNodePluginPrewarm()
        cancelDeferredTypeScriptCompilerPrewarm()
        TypeScriptCompilerStartupPrewarm.release("app-clear")
        if (deferredMaintenanceScheduled.compareAndSet(true, false)) {
            mainHandler.removeCallbacks(deferredMaintenance)
        }
        dynamicBroadcastReceivers.unregisterAll()
        InAppEventBus.clear()
    }

    companion object {

        private const val TAG = "App"
        private const val NODE_PLUGIN_PREWARM_DELAY_MILLIS = 1_800L
        private const val TYPESCRIPT_COMPILER_PREWARM_DELAY_MILLIS = 3_600L
        private const val TYPESCRIPT_COMPILER_STARTUP_KEEPALIVE_MILLIS = 60_000L
        private const val MAINTENANCE_SCHEDULING_DELAY_MILLIS = 2_500L

        @Volatile private lateinit var instance: WeakReference<App>

        val app: App
            get() = instance.get()!!

        private fun String.matchesProcessNameSuffix(): Boolean {
            return getProcessNameCompat().endsWith(this)
        }

        fun getProcessNameCompat(): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) getProcessName() else {
                try {
                    @SuppressLint("PrivateApi") val activityThread = Class.forName("android.app.ActivityThread")
                    @SuppressLint("DiscouragedPrivateApi") val method: Method = activityThread.getDeclaredMethod("currentProcessName")
                    method.invoke(null) as String
                } catch (e: ClassNotFoundException) {
                    e.printStackTrace()
                    "Unknown process name"
                }
            }
        }

    }

    // @Hint by SuperMonster003 on May 29, 2026.
    //  ! Explicitly provides workManagerConfiguration.
    //  ! Even if WorkManagerInitializer is disabled in the Manifest,
    //  ! WorkManager.getInstance(...) can complete initialization through Application configuration,
    //  ! to avoid crashes at WorkTimedTaskScheduler.schedulePeriodicCheck().
    //  ! zh-CN:
    //  ! 显式提供 workManagerConfiguration.
    //  ! 即使 Manifest 中禁用了 WorkManagerInitializer,
    //  ! WorkManager.getInstance(...) 也能通过 Application 配置完成初始化,
    //  ! 以避免 WorkTimedTaskScheduler.schedulePeriodicCheck() 处崩溃.
    override val workManagerConfiguration: WorkManagerConfiguration
        get() = WorkManagerConfiguration.Builder().build()

}
