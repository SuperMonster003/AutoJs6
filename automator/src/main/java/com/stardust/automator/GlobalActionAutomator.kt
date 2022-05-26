package com.stardust.automator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.*
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration

import com.stardust.concurrent.VolatileBox
import com.stardust.concurrent.VolatileDispose
import com.stardust.util.ScreenMetrics

/**
 * Created by Stardust on 2017/5/16.
 */
class GlobalActionAutomator(private val mHandler: Handler?, private val serviceProvider: () -> AccessibilityService) {

    private val service: AccessibilityService
        get() = serviceProvider()

    private var mScreenMetrics: ScreenMetrics? = null

    fun setScreenMetrics(screenMetrics: ScreenMetrics?) {
        mScreenMetrics = screenMetrics
    }

    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun recents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun notifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun quickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    fun powerDialog(): Boolean = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)

    fun splitScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)

    fun lockScreen(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        else -> tryPerformGlobalActionWithLowerApi(GLOBAL_ACTION_LOCK_SCREEN)
    }

    fun takeScreenshot(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        else -> tryPerformGlobalActionWithLowerApi(GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    fun headsethook(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> performGlobalAction(GLOBAL_ACTION_KEYCODE_HEADSETHOOK)
        else -> tryPerformGlobalActionWithLowerApi(GLOBAL_ACTION_KEYCODE_HEADSETHOOK)
    }

    fun accessibilityButton(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> performGlobalAction(GLOBAL_ACTION_ACCESSIBILITY_BUTTON)
        else -> tryPerformGlobalActionWithLowerApi(GLOBAL_ACTION_ACCESSIBILITY_BUTTON)
    }

    fun accessibilityButtonChooser(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> performGlobalAction(GLOBAL_ACTION_ACCESSIBILITY_BUTTON_CHOOSER)
        else -> tryPerformGlobalActionWithLowerApi(GLOBAL_ACTION_ACCESSIBILITY_BUTTON_CHOOSER)
    }

    fun accessibilityShortcut(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> performGlobalAction(GLOBAL_ACTION_ACCESSIBILITY_SHORTCUT)
        else -> tryPerformGlobalActionWithLowerApi(GLOBAL_ACTION_ACCESSIBILITY_SHORTCUT)
    }

    fun accessibilityAllApps(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> performGlobalAction(GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS)
        else -> tryPerformGlobalActionWithLowerApi(GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS)
    }

    fun dismissNotificationShade(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        else -> tryPerformGlobalActionWithLowerApi(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
    }

    private fun performGlobalAction(globalAction: Int): Boolean = service.performGlobalAction(globalAction)

    // @Hint by SuperMonster003 on May 12, 2022.
    //  ! Global actions such as GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS requires API Level S.
    //  ! However, there is a high possibility that global actions mentioned above
    //  ! can be performed NORMALLY on lower API Level device (e.g. API R).
    private fun tryPerformGlobalActionWithLowerApi(action: Int) = try {
        performGlobalAction(action)
    } catch (e: Throwable) {
        false
    }

    fun gesture(start: Long, duration: Long, vararg points: IntArray): Boolean {
        val path = pointsToPath(points)
        return gestures(GestureDescription.StrokeDescription(path, start, duration))
    }

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

    fun gestures(vararg strokes: GestureDescription.StrokeDescription): Boolean {
        val builder = GestureDescription.Builder()
        for (stroke in strokes) {
            builder.addStroke(stroke)
        }
        val handler = mHandler
        return if (handler == null) {
            gesturesWithoutHandler(builder.build())
        } else {
            gesturesWithHandler(handler, builder.build())
        }
    }

    private fun gesturesWithHandler(handler: Handler, description: GestureDescription): Boolean {
        val result = VolatileDispose<Boolean>()
        service.dispatchGesture(description, object : AccessibilityService.GestureResultCallback() {
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
        val myLooper = Looper.myLooper()
        if (myLooper != null) {
            val handler = Handler(myLooper)
            service.dispatchGesture(description, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    result.set(true)
                    quitLoop()
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    result.set(false)
                    quitLoop()
                }
            }, handler)
        }
        Looper.loop()
        return result.get()
    }

    fun gesturesAsync(vararg strokes: GestureDescription.StrokeDescription) {
        val builder = GestureDescription.Builder()
        for (stroke in strokes) {
            builder.addStroke(stroke)
        }
        service.dispatchGesture(builder.build(), null, null)
    }

    private fun quitLoop() {
        val looper = Looper.myLooper()
        looper?.quit()
    }

    private fun prepareLooperIfNeeded() {
        if (Looper.myLooper() == null) {
            Looper.prepare()
        }
    }

    fun click(x: Int, y: Int): Boolean = press(x, y, ViewConfiguration.getTapTimeout() + 50)

    fun press(x: Int, y: Int, delay: Int): Boolean = gesture(0, delay.toLong(), intArrayOf(x, y))

    fun longClick(x: Int, y: Int): Boolean = gesture(0, (ViewConfiguration.getLongPressTimeout() + 200).toLong(), intArrayOf(x, y))

    private fun scaleX(x: Int): Int = mScreenMetrics?.scaleX(x) ?: x

    private fun scaleY(y: Int): Int = mScreenMetrics?.scaleX(y) ?: y

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, delay: Long): Boolean = gesture(0, delay, intArrayOf(x1, y1), intArrayOf(x2, y2))

}
