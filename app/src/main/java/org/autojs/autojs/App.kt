package org.autojs.autojs

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.ui.inflater.ImageLoader
import org.autojs.autojs.core.ui.inflater.util.Drawables
import org.autojs.autojs.engine.NativeNodeProcessService
import org.autojs.autojs.engine.NativeNodeEmbeddedScriptService
import org.autojs.autojs.engine.NodeCrashEvidenceStore
import org.autojs.autojs.engine.NodeEmbeddedProcessSessionManager
import org.autojs.autojs.engine.NodeJsRuntimePluginHost
import org.autojs.autojs.engine.NodeRuntimeBackend
import org.autojs.autojs.event.GlobalKeyObserver
import org.autojs.autojs.external.receiver.DynamicBroadcastReceivers
import org.autojs.autojs.inrt.InrtNodeRuntimeOverride
import org.autojs.autojs.inrt.InrtShortcuts
import org.autojs.autojs.ipc.InAppEventBus
import org.autojs.autojs.leakcanary.LeakCanarySetup
import org.autojs.autojs.storage.file.TmpScriptFilesCleanupScheduler
import org.autojs.autojs.storage.history.HistoryCleanupScheduler
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.timing.TimedTaskManager
import org.autojs.autojs.timing.TimedTaskScheduler
import org.autojs.autojs.tool.CrashHandler
import org.autojs.autojs.ui.error.CrashReportActivity
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.greenrobot.eventbus.EventBus
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean
import androidx.work.Configuration as WorkManagerConfiguration
import org.autojs.autojs.inrt.Pref as InrtPref

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
    private val deferredNativeNodePrewarmScheduled = AtomicBoolean(false)
    private val deferredMaintenanceScheduled = AtomicBoolean(false)

    private val deferredNativeNodePrewarm = Runnable {
        if (deferredNativeNodePrewarmScheduled.compareAndSet(true, false)) {
            Thread(::prewarmNativeNodeIfNeeded, "AutoJs6-Node-StartupPrewarm").start()
        }
    }

    private val deferredMaintenance = Runnable {
        if (deferredMaintenanceScheduled.compareAndSet(true, false)) {
            Thread(::scheduleMaintenanceWork, "AutoJs6-startup-maintenance").start()
        }
    }

    override fun onCreate() {
        super.onCreate()

        GlobalAppContext.set(this)
        instance = WeakReference(this)

        when {
            ":crash_report".matchesProcessNameSuffix() -> {
                ThemeColorManager.init()
                setUpDefaultNightMode()
            }
            isNativeNodeProcess() -> {
                setUpDebugEnvironment()
            }
            else /* Main process. */ -> {
                if (AbstractAutoJs.isInrt) {
                    InrtNodeRuntimeOverride.apply(this)
                    InrtPref.syncLaunchConfigWithBuild()
                    InrtShortcuts.syncToExplicitIntents()
                }
                setUpDebugEnvironment()
                setUpLeakCanary()
                collectPendingNodeCrashEvidence()
                // The fallback branch also serves auxiliary app processes such as
                // :background. Only the package's main process may own persistent
                // Node service bindings; otherwise independent managers can keep the
                // same slot alive and make retirement appear to time out.
                if (getProcessNameCompat() == applicationInfo.processName) {
                    NodeEmbeddedProcessSessionManager.reopenForUse()
                    scheduleNativeNodePrewarmIfNeeded()
                }

                AutoJs.initInstance(this)
                GlobalKeyObserver.initIfNeeded(applicationContext)
                setupDrawableImageLoader()
                TimedTaskScheduler.init(this)
                initDynamicBroadcastReceivers()
                Toaster.init(this)

                ThemeColorManager.init()
                setUpDefaultNightMode()

                scheduleDeferredMaintenance()
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Log.d("Shizuku", "${App::class.java.simpleName} attachBaseContext | Process=${getProcessNameCompat()}")
    }

    private fun setUpDebugEnvironment() {
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
                Pref.containsKey(R.string.key_night_mode_enabled) -> {
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

    private fun prewarmNativeNodeIfNeeded() {
        when (NodeRuntimeBackend.current()) {
            NodeRuntimeBackend.NATIVE,
            NodeRuntimeBackend.AUTO -> {
                runCatching { NativeNodeProcessService.prewarm(this) }
                    .onFailure { Log.w(TAG, "Failed to prewarm native Node.js process.", it) }
            }
            NodeRuntimeBackend.EMBEDDED -> {
                runCatching { NativeNodeEmbeddedScriptService.prewarmRuntime(this) }
                    .onFailure { Log.w(TAG, "Failed to prewarm embedded Node.js process runtime.", it) }
            }
            NodeRuntimeBackend.PLUGIN,
            NodeRuntimeBackend.PLUGIN_OPTIONAL -> {
                runCatching {
                    runBlocking { NodeJsRuntimePluginHost.prewarm(this@App) }
                }.onFailure { Log.w(TAG, "Failed to prewarm external Node.js runtime plugin.", it) }
            }
            NodeRuntimeBackend.RHINO -> Unit
        }
    }

    private fun scheduleNativeNodePrewarmIfNeeded() {
        when (NodeRuntimeBackend.current()) {
            NodeRuntimeBackend.NATIVE,
            NodeRuntimeBackend.AUTO,
            NodeRuntimeBackend.EMBEDDED,
            NodeRuntimeBackend.PLUGIN,
            NodeRuntimeBackend.PLUGIN_OPTIONAL -> {
                if (deferredNativeNodePrewarmScheduled.compareAndSet(false, true)) {
                    mainHandler.postDelayed(deferredNativeNodePrewarm, NATIVE_NODE_PREWARM_DELAY_MILLIS)
                }
            }
            NodeRuntimeBackend.RHINO -> Unit
        }
    }

    /**
     * Cancels only an application-startup prewarm that has not begun yet. A real Node request
     * must take precedence so that the delayed task cannot start competing service processes.
     */
    internal fun cancelDeferredNativeNodePrewarm() {
        if (deferredNativeNodePrewarmScheduled.compareAndSet(true, false)) {
            mainHandler.removeCallbacks(deferredNativeNodePrewarm)
        }
    }

    private fun scheduleDeferredMaintenance() {
        if (deferredMaintenanceScheduled.compareAndSet(false, true)) {
            mainHandler.postDelayed(deferredMaintenance, MAINTENANCE_SCHEDULING_DELAY_MILLIS)
        }
    }

    private fun scheduleMaintenanceWork() {
        runCatching { HistoryCleanupScheduler.scheduleStartupCleanup(this) }
            .onFailure { Log.w(TAG, "Failed to schedule startup history cleanup.", it) }
        runCatching { HistoryCleanupScheduler.schedulePeriodicCleanup(this) }
            .onFailure { Log.w(TAG, "Failed to schedule periodic history cleanup.", it) }
        runCatching { TmpScriptFilesCleanupScheduler.scheduleStartupCleanup(this) }
            .onFailure { Log.w(TAG, "Failed to schedule startup temporary script cleanup.", it) }
        runCatching { TmpScriptFilesCleanupScheduler.schedulePeriodicCleanup(this) }
            .onFailure { Log.w(TAG, "Failed to schedule periodic temporary script cleanup.", it) }
    }

    private fun collectPendingNodeCrashEvidence() {
        runCatching {
            NodeCrashEvidenceStore.collectPending(this, "app.onCreate")
        }.onSuccess { collected ->
            if (collected.length() > 0) {
                Log.w(TAG, "Collected pending Node crash evidence bundles: ${collected.length()}")
            }
        }.onFailure {
            Log.w(TAG, "Failed to collect pending Node crash evidence bundles.", it)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        ViewUtils.onConfigurationChangedForNightMode(newConfig)
        EventBus.getDefault().post(newConfig)
        FloatyWindowManger.getCircularMenu()?.savePosition(newConfig)
        super.onConfigurationChanged(newConfig)
    }

    fun clear() {
        clearAsync()
    }

    /**
     * Starts the same non-blocking cleanup as [clear] and exposes its Node shutdown task so the
     * real app-exit path can wait for it without starting a duplicate shutdown operation.
     */
    fun clearForExit(): Future<Boolean> = clearAsync()

    private fun clearAsync(): Future<Boolean> {
        cancelDeferredNativeNodePrewarm()
        val closingEpoch = NodeEmbeddedProcessSessionManager.beginClosing()
        val nodeShutdownTask = FutureTask {
            runCatching {
                NodeEmbeddedProcessSessionManager.shutdownAllAndAwait(
                    context = this,
                    reason = "app.clear",
                    expectedClosingEpoch = closingEpoch,
                )
            }.onFailure {
                Log.w(TAG, "Failed to shut down embedded Node.js process sessions.", it)
            }.getOrDefault(false)
        }
        Thread(nodeShutdownTask, "AutoJs6-Node-AppShutdown").start()
        if (deferredMaintenanceScheduled.compareAndSet(true, false)) {
            mainHandler.removeCallbacks(deferredMaintenance)
        }
        dynamicBroadcastReceivers.unregisterAll()
        InAppEventBus.clear()
        return nodeShutdownTask
    }

    override fun onTrimMemory(level: Int) {
        if (getProcessNameCompat() == applicationInfo.processName) {
            NodeEmbeddedProcessSessionManager.onTrimMemory(this, level)
        }
        super.onTrimMemory(level)
    }

    companion object {

        private const val TAG = "App"
        private const val NATIVE_NODE_PREWARM_DELAY_MILLIS = 1_800L
        private const val MAINTENANCE_SCHEDULING_DELAY_MILLIS = 2_500L

        private lateinit var instance: WeakReference<App>

        val app: App
            get() = instance.get()!!

        private fun String.matchesProcessNameSuffix(): Boolean {
            return getProcessNameCompat().endsWith(this)
        }

        private fun isNativeNodeProcess(): Boolean {
            val processSuffix = getProcessNameCompat().substringAfterLast(':')
            return processSuffix == "nodejs" || processSuffix.startsWith("nodejs_")
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
