package com.kevinluo.autoglm.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.kevinluo.autoglm.ui.FloatingWindowStateManager
import com.kevinluo.autoglm.util.KeepAliveManager
import com.kevinluo.autoglm.util.Logger

class AutoGlmLifecycleCallbacks(
    private val appContext: android.content.Context,
) : Application.ActivityLifecycleCallbacks {

    private var activityCount = 0

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) {
        // 只统计 autoglm 自己的 activity（避免影响 AutoJs6）
        if (!activity.packageName.contains("autojs", ignoreCase = true) && !activity.javaClass.name.startsWith("com.kevinluo.autoglm")) {
            // 这个判断比较保守；如果你希望更严谨，可以只允许 autoglm 包名前缀
        }

        if (!activity.javaClass.name.startsWith("com.kevinluo.autoglm")) return

        activityCount++
        Logger.d(TAG, "Activity started: ${activity.localClassName}, count: $activityCount")
        if (activityCount == 1) {
            FloatingWindowStateManager.onAppForeground()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (!activity.javaClass.name.startsWith("com.kevinluo.autoglm")) return
        KeepAliveManager.syncFixState(appContext)
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) {
        if (!activity.javaClass.name.startsWith("com.kevinluo.autoglm")) return

        activityCount--
        Logger.d(TAG, "Activity stopped: ${activity.localClassName}, count: $activityCount")
        if (activityCount == 0) {
            FloatingWindowStateManager.onAppBackground(appContext)
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        private const val TAG = "AutoGlmLifecycle"
    }
}