package org.autojs.autojs

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.multidex.MultiDexApplication
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.hjq.toast.Toaster
import io.reactivex.android.schedulers.AndroidSchedulers
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.inrt.Pref as InrtPref
import org.autojs.autojs.inrt.InrtShortcuts
import org.autojs.autojs.core.ui.inflater.ImageLoader
import org.autojs.autojs.core.ui.inflater.util.Drawables
import org.autojs.autojs.event.GlobalKeyObserver
import org.autojs.autojs.external.receiver.DynamicBroadcastReceivers
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

/**
 * Created by Stardust on Jan 27, 2017.
 * Modified by SuperMonster003 as of Feb 3, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 7, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 9, 2026.
 */
class App : MultiDexApplication() {

    lateinit var dynamicBroadcastReceivers: DynamicBroadcastReceivers
        private set

    override fun onCreate() {
        super.onCreate()

        GlobalAppContext.set(this)
        instance = WeakReference(this)

        when {
            ":crash_report".matchesProcessNameSuffix() -> {
                ThemeColorManager.init()
                setUpDefaultNightMode()
            }
            else /* Main process. */ -> {
                if (AbstractAutoJs.isInrt) {
                    InrtPref.syncLaunchConfigWithBuild()
                    InrtShortcuts.syncToExplicitIntents()
                }
                setUpDebugEnvironment()
                setUpLeakCanary()

                AutoJs.initInstance(this)
                GlobalKeyObserver.initIfNeeded(applicationContext)
                setupDrawableImageLoader()
                TimedTaskScheduler.init(this)
                initDynamicBroadcastReceivers()
                initMlKitContext()
                Toaster.init(this)

                ThemeColorManager.init()
                setUpDefaultNightMode()

                HistoryCleanupScheduler.scheduleStartupCleanup(this)
                HistoryCleanupScheduler.schedulePeriodicCleanup(this)
                TmpScriptFilesCleanupScheduler.scheduleStartupCleanup(this)
                TmpScriptFilesCleanupScheduler.schedulePeriodicCleanup(this)
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

    private fun initMlKitContext() {
        MlKitContext.initializeIfNeeded(this)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        ViewUtils.onConfigurationChangedForNightMode(newConfig)
        EventBus.getDefault().post(newConfig)
        FloatyWindowManger.getCircularMenu()?.savePosition(newConfig)
        super.onConfigurationChanged(newConfig)
    }

    fun clear() {
        dynamicBroadcastReceivers.unregisterAll()
        InAppEventBus.clear()
    }

    companion object {

        private lateinit var instance: WeakReference<App>

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

}
