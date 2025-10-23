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
import com.flurry.android.FlurryAgent
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.hjq.toast.Toaster
import com.tencent.bugly.Bugly
import com.tencent.bugly.crashreport.CrashReport
import io.reactivex.android.schedulers.AndroidSchedulers
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.ui.inflater.ImageLoader
import org.autojs.autojs.core.ui.inflater.util.Drawables
import org.autojs.autojs.event.GlobalKeyObserver
import org.autojs.autojs.external.receiver.DynamicBroadcastReceivers
import org.autojs.autojs.ipc.InAppEventBus
import org.autojs.autojs.leakcanary.LeakCanarySetup
import org.autojs.autojs.pluginclient.DevPluginService
import org.autojs.autojs.timing.TimedTaskManager
import org.autojs.autojs.timing.TimedTaskScheduler
import org.autojs.autojs.tool.CrashHandler
import org.autojs.autojs.ui.error.CrashReportActivity
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.greenrobot.eventbus.EventBus
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/**
 * Created by Stardust on Jan 27, 2017.
 * Modified by SuperMonster003 as of Aug 23, 2022.
 */
class App : MultiDexApplication() {

    lateinit var dynamicBroadcastReceivers: DynamicBroadcastReceivers
        private set

    lateinit var devPluginService: DevPluginService
        private set

    override fun onCreate() {
        super.onCreate()

        GlobalAppContext.set(this)
        instance = WeakReference(this)
        devPluginService = DevPluginService(this)

        setUpStaticsTool()
        setUpDebugEnvironment()
        setUpLeakCanary()

        AutoJs.initInstance(this)
        GlobalKeyObserver.initIfNeeded(applicationContext)
        setupDrawableImageLoader()
        TimedTaskScheduler.init(this)
        initDynamicBroadcastReceivers()
        initMlKitContext()
        Toaster.init(this)

        setUpDefaultNightMode()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Log.d("Shizuku", "${App::class.java.simpleName} attachBaseContext | Process=${getProcessNameCompat()}")
    }

    private fun setUpStaticsTool() {
        if (!BuildConfig.DEBUG) {
            @Suppress("SpellCheckingInspection")
            FlurryAgent.Builder()
                .withLogEnabled(false)
                .build(this, "D42MH48ZN4PJC5TKNYZD")
        }
    }

    private fun setUpDebugEnvironment() {
        Bugly.isDev = false
        val crashHandler = CrashHandler(CrashReportActivity::class.java)
        val strategy = CrashReport.UserStrategy(applicationContext).apply {
            crashHandleCallback = crashHandler
        }

        CrashReport.initCrashReport(applicationContext, BUGLY_APP_ID, false, strategy)

        crashHandler.setBuglyHandler(Thread.getDefaultUncaughtExceptionHandler())
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

        private const val BUGLY_APP_ID = "19b3607b53"

        private lateinit var instance: WeakReference<App>

        val app: App
            get() = instance.get()!!

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
