package org.autojs.autojs

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.multidex.MultiDexApplication
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import com.flurry.android.FlurryAgent
import com.tencent.bugly.Bugly
import com.tencent.bugly.crashreport.CrashReport
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.ui.inflater.ImageLoader
import org.autojs.autojs.core.ui.inflater.util.Drawables
import org.autojs.autojs.event.GlobalKeyObserver
import org.autojs.autojs.external.receiver.DynamicBroadcastReceivers
import org.autojs.autojs.pluginclient.DevPluginService
import org.autojs.autojs.pref.Pref
import org.autojs.autojs.timing.TimedTaskManager
import org.autojs.autojs.timing.TimedTaskScheduler
import org.autojs.autojs.tool.CrashHandler
import org.autojs.autojs.ui.error.ErrorReportActivity
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/**
 * Created by Stardust on 2017/1/27.
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

        setUpStaticsTool()
        setUpDebugEnvironment()

        AutoJs.initInstance(this)
        GlobalKeyObserver.initIfNeeded()
        setupDrawableImageLoader()
        TimedTaskScheduler.init(this)
        initDynamicBroadcastReceivers()

        setUpDefaultNightMode()
        AccessibilityTool(this).service.enableIfNeeded()

        devPluginService = DevPluginService(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Log.d("Shizuku", "${App::class.java.simpleName} attachBaseContext | Process=${getProcessNameCompat()}")
    }

    private fun setUpStaticsTool() {
        if (!BuildConfig.DEBUG) {
            @Suppress("SpellCheckingInspection")
            FlurryAgent.Builder()
                .withLogEnabled(BuildConfig.DEBUG)
                .build(this, "D42MH48ZN4PJC5TKNYZD")
        }
    }

    private fun setUpDebugEnvironment() {
        Bugly.isDev = false
        val crashHandler = CrashHandler(ErrorReportActivity::class.java)

        val strategy = CrashReport.UserStrategy(applicationContext)
        strategy.setCrashHandleCallback(crashHandler)

        CrashReport.initCrashReport(applicationContext, BUGLY_APP_ID, false, strategy)

        crashHandler.setBuglyHandler(Thread.getDefaultUncaughtExceptionHandler())
        Thread.setDefaultUncaughtExceptionHandler(crashHandler)
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
        val actions = ArrayList<String>()
        TimedTaskManager.allIntentTasks
            .filter { task -> task.action != null }
            .doOnComplete {
                if (localActions.isNotEmpty()) {
                    dynamicBroadcastReceivers.register(localActions, true)
                }
                if (actions.isNotEmpty()) {
                    dynamicBroadcastReceivers.register(actions, false)
                }
                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
                    Intent(
                        DynamicBroadcastReceivers.ACTION_STARTUP
                    )
                )
            }
            .subscribe({
                if (it.isLocal) {
                    localActions.add(it.action)
                } else {
                    actions.add(it.action)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        ViewUtils.onConfigurationChanged(newConfig)
        FloatyWindowManger.getCircularMenu()?.savePosition(newConfig)
        super.onConfigurationChanged(newConfig)
    }

    companion object {

        private const val BUGLY_APP_ID = "19b3607b53"

        private lateinit var instance: WeakReference<App>

        val app: App
            get() = instance.get()!!

        fun getProcessNameCompat(): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName() else {
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
