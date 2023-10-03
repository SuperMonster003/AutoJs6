package org.autojs.autojs.core.automator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.*
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration

import org.autojs.autojs.concurrent.VolatileBox
import org.autojs.autojs.concurrent.VolatileDispose
import org.autojs.autojs.runtime.api.ScreenMetrics

/**
 * Created by Stardust on 2017/5/16.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 */
class GlobalActionAutomator(private val mHandler: Handler?, private val serviceProvider: () -> AccessibilityService) {

    private val service: AccessibilityService
        get() = serviceProvider()

    private var mScreenMetrics: ScreenMetrics? = null

    fun setScreenMetrics(screenMetrics: ScreenMetrics?) {
        mScreenMetrics = screenMetrics
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)

    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)

    fun recents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun notifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun quickSettings() = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    fun powerDialog() = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)

    fun splitScreen() = performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)

    @SuppressLint("InlinedApi")
    fun lockScreen() = when (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        true -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        else -> tryPerformGlobalActionWithLowerApi(GLOBAL_ACTION_LOCK_SCREEN)
    }

    @SuppressLint("InlinedApi")
    fun takeScreenshot() = when (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        true -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        else -> tryPerformGlobalActionWithLowerApi(GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    @SuppressLint("InlinedApi")
    fun headsethook() = when (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        true -> performGlobalAction(GLOBAL_ACTION_KEYCODE_HEADSETHOOK)
        else -> tryPerformGlobalActionWithLowerApi(GLOBAL_ACTION_KEYCODE_HEADSETHOOK)
    }

    @SuppressLint("InlinedApi")
    fun accessibilityButton() = when (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        true -> performGlobalAction(GLOBAL_ACTION_ACCESSIBILITY_BUTTON)
        else -> tryPerformGlobalActionWithLowerApi(GLOBAL_ACTION_ACCESSIBILITY_BUTTON)
    }

    @SuppressLint("InlinedApi")
    fun accessibilityButtonChooser() = when (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        true -> performGlobalAction(GLOBAL_ACTION_ACCESSIBILITY_BUTTON_CHOOSER)
        else -> tryPerformGlobalActionWithLowerApi(GLOBAL_ACTION_ACCESSIBILITY_BUTTON_CHOOSER)
    }

    @SuppressLint("InlinedApi")
    fun accessibilityShortcut() = when (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        true -> performGlobalAction(GLOBAL_ACTION_ACCESSIBILITY_SHORTCUT)
        else -> tryPerformGlobalActionWithLowerApi(GLOBAL_ACTION_ACCESSIBILITY_SHORTCUT)
    }

    @SuppressLint("InlinedApi")
    fun accessibilityAllApps() = when (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        true -> performGlobalAction(GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS)
        else -> tryPerformGlobalActionWithLowerApi(GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS)
    }

    @SuppressLint("InlinedApi")
    fun dismissNotificationShade() = when (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        true -> performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        else -> tryPerformGlobalActionWithLowerApi(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
    }

    private fun performGlobalAction(globalAction: Int) = service.performGlobalAction(globalAction)

    // @Hint by SuperMonster003 on May 12, 2022.
    //  ! Global actions such as GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS requires API Level S.
    //  ! However, there is a high possibility that global actions mentioned above
    //  ! can be performed NORMALLY on lower API Level device (e.g. API R).
    private fun tryPerformGlobalActionWithLowerApi(action: Int) = try {
        performGlobalAction(action)
    } catch (e: Throwable) {
        false
    }

    fun gesture(start: Long, duration: Long, vararg points: IntArray) = gestures(GestureDescription.StrokeDescription(pointsToPath(points), start, duration))

    private fun pointsToPath(points: Array<out IntArray>): Path {
        val path = Path()
        path.moveTo(scaleX(points[0][0]).toFloat(), scaleY(points[0][1]).toFloat())
        for (i in 1 until points.size) {
            val point = points[i]
            path.lineTo(scaleX(point[0]).toFloat(), scaleY(point[1]).toFloat())
        }
        return path
    }

    fun gestureAsync(start: Long, duration: Long, vararg points: IntArray) {
        val path = pointsToPath(points)
        gesturesAsync(GestureDescription.StrokeDescription(path, start, duration))
    }

    fun gestures(vararg strokes: GestureDescription.StrokeDescription) = GestureDescription.Builder().let { builder ->
        val built = strokes.forEach { builder.addStroke(it) }.let { builder.build() }
        mHandler?.let { gesturesWithHandler(it, built) } ?: gesturesWithoutHandler(built)
    }

    private fun gesturesWithHandler(handler: Handler, description: GestureDescription): Boolean {
        val result = VolatileDispose<Boolean>()
        service.dispatchGesture(description, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                result.setAndNotify(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription) {
                result.setAndNotify(false)
            }
        }, handler)
        return result.blockedGet()
    }

    private fun gesturesWithoutHandler(description: GestureDescription): Boolean {
        prepareLooperIfNeeded()
        val result = VolatileBox(false)
        Looper.myLooper()?.let { myLooper ->
            service.dispatchGesture(description, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    result.set(true)
                    quitLoop()
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    result.set(false)
                    quitLoop()
                }
            }, Handler(myLooper))
        }
        Looper.loop()
        return result.get()
    }

    fun gesturesAsync(vararg strokes: GestureDescription.StrokeDescription) {
        GestureDescription.Builder().let { builder ->
            val built = strokes.forEach { builder.addStroke(it) }.let { builder.build() }
            service.dispatchGesture(built, null, null)
        }
    }

    private fun quitLoop() {
        Looper.myLooper()?.quit()
    }

    private fun prepareLooperIfNeeded() {
        Looper.myLooper() ?: Looper.prepare()
    }

    fun click(x: Int, y: Int) = press(x, y, ViewConfiguration.getTapTimeout() + 50)

    fun press(x: Int, y: Int, delay: Int) = gesture(0, delay.toLong(), intArrayOf(x, y))

    fun longClick(x: Int, y: Int) = gesture(0, (ViewConfiguration.getLongPressTimeout() + 200).toLong(), intArrayOf(x, y))

    private fun scaleX(x: Int) = mScreenMetrics?.scaleX(x) ?: x

    private fun scaleY(y: Int) = mScreenMetrics?.scaleX(y) ?: y

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long) = gesture(0, duration, intArrayOf(x1, y1), intArrayOf(x2, y2))

}
