@file:Suppress("unused")

package org.autojs.autojs.core.accessibility

import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.automator.AccessibilityEventWrapper
import org.autojs.autojs.core.automator.GlobalActionAutomator
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.action.ActionFactory
import org.autojs.autojs.core.automator.action.ActionTarget
import org.autojs.autojs.core.automator.action.SimpleAction
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.accessibility.AccessibilityConfig
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.util.DeveloperUtils
import java.util.concurrent.atomic.AtomicInteger
import android.accessibilityservice.AccessibilityService as AndroidAccessibilityService

/**
 * Created by Stardust on 2017/4/2.
 */
class SimpleActionAutomator(private val accessibilityBridge: AccessibilityBridge, private val scriptRuntime: ScriptRuntime) {

    private val globalActionAutomatorRaw by lazy {
        GlobalActionAutomator(Handler(scriptRuntime.loopers.servantLooper)) {
            ensureService()
            accessibilityBridge.service!!
        }
    }

    private val globalActionAutomator
        get() = globalActionAutomatorRaw

    private val globalActionAutomatorForGesture
        get() = globalActionAutomatorRaw.apply { setScreenMetrics(mScreenMetrics) }

    private val isRunningPackageSelf
        get() = DeveloperUtils.isSelfPackage(accessibilityBridge.infoProvider.latestPackage)

    private var mScreenMetrics: ScreenMetrics? = null

    private var mPromiseAdapter: ScriptPromiseAdapter? = null

    @ScriptInterface
    fun text(text: String, i: Int) = ActionTarget.TextActionTarget(text, i)

    @ScriptInterface
    fun bounds(left: Int, top: Int, right: Int, bottom: Int) = ActionTarget.BoundsActionTarget(Rect(left, top, right, bottom))

    @ScriptInterface
    fun editable(i: Int) = ActionTarget.EditableActionTarget(i)

    @ScriptInterface
    fun id(id: String) = ActionTarget.IdActionTarget(id)

    @ScriptInterface
    fun click(target: ActionTarget) = performAction(target.createAction(AccessibilityNodeInfoCompat.ACTION_CLICK))

    @ScriptInterface
    fun longClick(target: ActionTarget) = performAction(target.createAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))

    @ScriptInterface
    fun scrollUp(target: ActionTarget) = performAction(target.createAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))

    @ScriptInterface
    fun scrollDown(target: ActionTarget) = performAction(target.createAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))

    @ScriptInterface
    fun scrollBackward(i: Int) = performAction(ActionFactory.createScrollAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, i))

    @ScriptInterface
    fun scrollForward(i: Int) = performAction(ActionFactory.createScrollAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, i))

    @ScriptInterface
    fun scrollMaxBackward() = performAction(ActionFactory.createScrollMaxAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))

    @ScriptInterface
    fun scrollMaxForward() = performAction(ActionFactory.createScrollMaxAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))

    @ScriptInterface
    fun focus(target: ActionTarget) = performAction(target.createAction(AccessibilityNodeInfo.ACTION_FOCUS))

    @ScriptInterface
    fun select(target: ActionTarget) = performAction(target.createAction(AccessibilityNodeInfo.ACTION_SELECT))

    @ScriptInterface
    fun setText(target: ActionTarget, text: String) = performAction(target.createAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text))

    @ScriptInterface
    fun appendText(target: ActionTarget, text: String) = performAction(target.createAction(UiObject.ACTION_APPEND_TEXT, text))

    @ScriptInterface
    fun back() = globalActionAutomator.back()

    @ScriptInterface
    fun home() = globalActionAutomator.home()

    @ScriptInterface
    fun recents() = globalActionAutomator.recents()

    @ScriptInterface
    fun notifications() = globalActionAutomator.notifications()

    @ScriptInterface
    fun quickSettings() = globalActionAutomator.quickSettings()

    @ScriptInterface
    fun powerDialog() = globalActionAutomator.powerDialog()

    @ScriptInterface
    fun splitScreen() = globalActionAutomator.splitScreen()

    @ScriptInterface
    fun lockScreen() = globalActionAutomator.lockScreen()

    @ScriptInterface
    fun takeScreenshot() = globalActionAutomator.takeScreenshot()

    @ScriptInterface
    fun headsethook() = globalActionAutomator.headsethook()

    @ScriptInterface
    fun accessibilityButton() = globalActionAutomator.accessibilityButton()

    @ScriptInterface
    fun accessibilityButtonChooser() = globalActionAutomator.accessibilityButtonChooser()

    @ScriptInterface
    fun accessibilityShortcut() = globalActionAutomator.accessibilityShortcut()

    @ScriptInterface
    fun accessibilityAllApps() = globalActionAutomator.accessibilityAllApps()

    @ScriptInterface
    fun dismissNotificationShade() = globalActionAutomator.dismissNotificationShade()

    @ScriptInterface
    fun gesture(start: Long, duration: Long, vararg points: IntArray) = globalActionAutomatorForGesture.gesture(start, duration, *points)

    @ScriptInterface
    fun gestureAsync(start: Long, duration: Long, vararg points: IntArray) = globalActionAutomatorForGesture.gestureAsync(start, duration, *points)

    @ScriptInterface
    @Suppress("UNCHECKED_CAST")
    fun gestures(strokes: Any) = globalActionAutomatorForGesture.gestures(*strokes as Array<GestureDescription.StrokeDescription>)

    @ScriptInterface
    @Suppress("UNCHECKED_CAST")
    fun gesturesAsync(strokes: Any) = globalActionAutomatorForGesture.gesturesAsync(*strokes as Array<GestureDescription.StrokeDescription>)

    @ScriptInterface
    fun click(x: Int, y: Int) = globalActionAutomatorForGesture.click(x, y)

    @ScriptInterface
    fun press(x: Int, y: Int, delay: Int) = globalActionAutomatorForGesture.press(x, y, delay)

    @ScriptInterface
    fun longClick(x: Int, y: Int) = globalActionAutomatorForGesture.longClick(x, y)

    @ScriptInterface
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int) = globalActionAutomatorForGesture.swipe(x1, y1, x2, y2, duration.toLong())

    @ScriptInterface
    fun paste(target: ActionTarget) = performAction(target.createAction(AccessibilityNodeInfo.ACTION_PASTE))

    @ScriptInterface
    fun isServiceRunning() = AccessibilityService.isRunning()

    @ScriptInterface
    fun ensureService() = accessibilityBridge.ensureServiceEnabled()

    // @Created by 抠脚本人 on Jul 10, 2023.
    // TODO by 抠脚本人 on Jul 10, 2023.
    //  ! 优化实现方式
    // TODO by SuperMonster003 on Jul 12, 2023.
    //  ! Ref to Auto.js Pro
    fun registerEvent(eventName: String, callback: AccessibilityEventCallback) {
        ensureService()
        AccessibilityService.instance?.addAccessibilityEventCallback(eventName, callback)
    }

    // @Created by 抠脚本人 on Jul 10, 2023.
    fun removeEvent(eventName: String) {
        AccessibilityService.instance?.removeAccessibilityEventCallback(eventName)
    }

    private fun performAction(simpleAction: SimpleAction): Boolean {
        ensureService()
        if (AccessibilityConfig.isUnintendedGuardEnabled() && isRunningPackageSelf) {
            return false
        }
        return accessibilityBridge.windowRoots().filterNotNull().let { roots ->
            roots.isNotEmpty() && roots.map { root -> simpleAction.perform(UiObject.createRoot(root)) }.all { it /* == true */ }
        }
    }

    fun setScreenMetrics(metrics: ScreenMetrics) {
        mScreenMetrics = metrics
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun captureScreen(): ScriptPromiseAdapter {
        ScriptRuntime.requiresApi(Build.VERSION_CODES.R)
        ensureService()

        val promiseAdapter = mPromiseAdapter ?: ScriptPromiseAdapter().also { mPromiseAdapter = it }
        val service = accessibilityBridge.service!!
        val executor = service.mainExecutor
        val callback = object : AndroidAccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AndroidAccessibilityService.ScreenshotResult) {
                val hardwareBuffer = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)

                // @Hint by SuperMonster003 on Jun 9, 2023.
                //  ! To avoid the exception as below.
                //  !
                //  ! Wrapped java.lang.IllegalStateException: unable to getPixel(), pixel access is not supported on Config#HARDWARE bitmaps.
                //  !
                //  ! Reference: https://stackoverflow.com/questions/60462841/
                val bitmap = hardwareBuffer?.copy(Bitmap.Config.ARGB_8888, true)

                hardwareBuffer?.recycle()
                promiseAdapter.resolve(ImageWrapper.ofBitmap(bitmap))
                mPromiseAdapter = null
            }

            override fun onFailure(errorCode: Int) {
                if (errorCode == AndroidAccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        captureScreen()
                    }, 50)
                } else {
                    Log.w(TAG, "onFailure: $errorCode")
                    promiseAdapter.resolve(null)
                }
            }
        }

        service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)

        return promiseAdapter
    }

    companion object {

        val accessibilityDelegateCounter = AtomicInteger(1000)
        val TAG: String = SimpleActionAutomator::class.java.name

        /**
         * Created by 抠脚本人 on Jul 10, 2023.
         */
        interface AccessibilityEventCallback {
            fun onAccessibilityEvent(event: AccessibilityEventWrapper)
        }

    }

}
