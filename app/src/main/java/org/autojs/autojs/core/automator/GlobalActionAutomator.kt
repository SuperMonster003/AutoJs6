package org.autojs.autojs.core.automator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.*
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.ViewConfiguration

import org.autojs.autojs.concurrent.VolatileDispose
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs6.R

/**
 * Created by Stardust on May 16, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 */
class GlobalActionAutomator internal constructor(
    private val context: Context,
    private val handler: Handler?,
    private val gestureDispatcher: GestureDispatcher,
    private val serviceProvider: () -> AccessibilityService?,
) {

    constructor(context: Context, handler: Handler?, serviceProvider: () -> AccessibilityService?) :
            this(context, handler, GestureDispatcher.SYSTEM, serviceProvider)

    /**
     * Hands a gesture to the system. Instrumentation tests supply their own, since
     * [AccessibilityService.dispatchGesture] is final and needs a connected service.
     * zh-CN: 把手势交给系统. Instrumentation 测试自行提供, 因为 [AccessibilityService.dispatchGesture] 是 final 且需要已连接的服务.
     */
    internal fun interface GestureDispatcher {
        fun dispatch(service: AccessibilityService, gesture: GestureDescription, callback: GestureResultCallback, handler: Handler): Boolean

        companion object {
            val SYSTEM = GestureDispatcher { service, gesture, callback, handler -> service.dispatchGesture(gesture, callback, handler) }
        }
    }

    private val service: AccessibilityService?
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

    private fun performGlobalAction(globalAction: Int): Boolean {
        val service = service ?: return false
        return runCatching {
            service.performGlobalAction(globalAction)
        }.onFailure {
            Log.w(TAG, "Failed to perform global action: $globalAction", it)
        }.getOrDefault(false)
    }

    // @Hint by SuperMonster003 on May 12, 2022.
    //  ! Global actions such as GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS requires API Level S.
    //  ! However, there is a high possibility that global actions mentioned above
    //  ! can be performed NORMALLY on lower API Level device (e.g. API R).
    //  ! zh-CN:
    //  ! 一些全局行为 (如 GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS) 要求设备至少为 API S 级别.
    //  ! 然而, 上述全局操作在低 API 级别的设备上 (如 API R) 也有较大可能性可以正常执行.
    private fun tryPerformGlobalActionWithLowerApi(action: Int) = performGlobalAction(action)

    fun gesture(start: Long, duration: Long, vararg points: IntArray) = gesture(start, duration, "gesture", *points)

    private fun gesture(start: Long, duration: Long, actionName: String, vararg points: IntArray) = gestures(GestureDescription.StrokeDescription(pointsToPath(points, actionName), start, duration))

    private fun pointsToPath(points: Array<out IntArray>, actionName: String) = Path().also { path ->
        points.forEachIndexed { i, point ->
            val (x, y) = point
            if (x < 0 || y < 0) {
                throw IllegalArgumentException(context.getString(R.string.error_action_cannot_be_completed_with_negative_coordinate, actionName, x, y))
            }
            val xF = scaleX(x).toFloat()
            val yF = scaleY(y).toFloat()
            when (i == 0) {
                true -> path.moveTo(xF, yF).also { Log.d(TAG, "Path moved to ($xF, $yF)") }
                else -> path.lineTo(xF, yF).also { Log.d(TAG, "Path lined to ($xF, $yF)") }
            }
        }
    }

    fun gestureAsync(start: Long, duration: Long, points: Array<out IntArray>, callback: GestureResultCallback? = null) {
        val path = pointsToPath(points, "gestureAsync")
        gesturesAsync(arrayOf(GestureDescription.StrokeDescription(path, start, duration)), callback)
    }

    fun gestures(vararg strokes: GestureDescription.StrokeDescription) = GestureDescription.Builder().let { builder ->
        val built = strokes.forEach { builder.addStroke(it) }.let { builder.build() }
        gesturesWithHandler(handler ?: callbackHandler, built)
    }

    private fun gesturesWithHandler(handler: Handler, description: GestureDescription): Boolean {
        val service = service ?: return false
        val result = VolatileDispose<Boolean>()
        val dispatchGesture = runCatching {
            gestureDispatcher.dispatch(service, description, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    result.setAndNotify(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    result.setAndNotify(false)
                }
            }, handler)
        }.onFailure {
            Log.w(TAG, "Failed to dispatch gesture", it)
        }.getOrDefault(false)
        Log.d(TAG, "dispatchGesture: $dispatchGesture")
        if (!dispatchGesture) {
            return false
        }
        return result.blockedGet(128_000) ?: false
    }

    fun gesturesAsync(strokes: Array<out GestureDescription.StrokeDescription>, callback: GestureResultCallback? = null) {
        val service = service ?: return
        GestureDescription.Builder().let { builder ->
            val built = strokes.forEach { builder.addStroke(it) }.let { builder.build() }
            runCatching {
                service.dispatchGesture(built, callback, null)
            }.onFailure {
                Log.w(TAG, "Failed to dispatch async gesture", it)
            }
        }
    }

    fun click(x: Int, y: Int) = gesture(0, (ViewConfiguration.getTapTimeout() /* 100 */ * 1.25).toLong(), "click", intArrayOf(x, y))

    fun press(x: Int, y: Int, duration: Int) = gesture(0, duration.toLong(), "press", intArrayOf(x, y))

    fun longClick(x: Int, y: Int) = gesture(0, (ViewConfiguration.getLongPressTimeout() /* 400 */ * 1.25).toLong(), "longClick", intArrayOf(x, y))

    private fun scaleX(x: Int) = mScreenMetrics?.scaleX(x) ?: x

    private fun scaleY(y: Int) = mScreenMetrics?.scaleY(y) ?: y

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long) = gesture(0, duration, "swipe", intArrayOf(x1, y1), intArrayOf(x2, y2))

    companion object {

        private val TAG: String? = GlobalActionAutomator::class.java.simpleName

        /**
         * Receives the gesture results of automators built without a handler, while the calling thread
         * waits for them. The calling thread's own Looper must not be used: a Looper prepared and looped
         * there for one gesture had to be quit afterwards, which ended a script thread's Looper (its
         * timers and event callbacks stopped, and queued callbacks ran in the middle of the gesture),
         * and on the main thread the quit threw, since the main Looper may not quit.
         * zh-CN: 接收未指定 handler 的 automator 的手势结果, 调用线程在此期间等待. 不能使用调用线程自己的 Looper: 为一次
         * 手势在该线程上准备并运行的 Looper 事后必须退出, 这会结束脚本线程的 Looper (其定时器与事件回调停止, 排队的回调在手势
         * 中途执行); 在主线程上退出则会抛出异常, 因为主 Looper 不允许退出.
         */
        private val callbackHandler: Handler by lazy {
            Handler(HandlerThread("GlobalActionAutomator-gestures").apply { start() }.looper)
        }

    }

}
