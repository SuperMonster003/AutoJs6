@file:Suppress("unused")

package org.autojs.autojs.core.accessibility

import android.accessibilityservice.AccessibilityService.GestureResultCallback
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
import org.autojs.autojs.runtime.api.augment.global.Global
import org.autojs.autojs.util.DeveloperUtils
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger
import android.accessibilityservice.AccessibilityService as AndroidAccessibilityService

/**
 * Created by Stardust on Apr 2, 2017.
 */
class SimpleActionAutomator(private val accessibilityBridge: AccessibilityBridge, private val scriptRuntime: ScriptRuntime) {

    private val mGlobalActionAutomatorRaw by lazy {
        GlobalActionAutomator(scriptRuntime.uiHandler.applicationContext, Handler(scriptRuntime.loopers.servantLooper)) {
            ensureService()
            accessibilityBridge.service!!
        }
    }

    private val mGlobalActionAutomator by lazy {
        mGlobalActionAutomatorRaw
    }

    private val mGlobalActionAutomatorForGesture by lazy {
        mGlobalActionAutomatorRaw.apply { setScreenMetrics(mScreenMetrics) }
    }

    private val isRunningPackageSelf
        get() = DeveloperUtils.isSelfPackage(Global.currentPackage(scriptRuntime, emptyArray()))

    private var mScreenMetrics: ScreenMetrics? = null
    private val mScriptRuntime = WeakReference(scriptRuntime)

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
    fun back() = mGlobalActionAutomator.back()

    @ScriptInterface
    fun home() = mGlobalActionAutomator.home()

    @ScriptInterface
    fun recents() = mGlobalActionAutomator.recents()

    @ScriptInterface
    fun notifications() = mGlobalActionAutomator.notifications()

    @ScriptInterface
    fun quickSettings() = mGlobalActionAutomator.quickSettings()

    @ScriptInterface
    fun powerDialog() = mGlobalActionAutomator.powerDialog()

    @ScriptInterface
    fun splitScreen() = mGlobalActionAutomator.splitScreen()

    @ScriptInterface
    fun lockScreen() = mGlobalActionAutomator.lockScreen()

    @ScriptInterface
    fun takeScreenshot() = mGlobalActionAutomator.takeScreenshot()

    @ScriptInterface
    fun headsethook() = mGlobalActionAutomator.headsethook()

    @ScriptInterface
    fun accessibilityButton() = mGlobalActionAutomator.accessibilityButton()

    @ScriptInterface
    fun accessibilityButtonChooser() = mGlobalActionAutomator.accessibilityButtonChooser()

    @ScriptInterface
    fun accessibilityShortcut() = mGlobalActionAutomator.accessibilityShortcut()

    @ScriptInterface
    fun accessibilityAllApps() = mGlobalActionAutomator.accessibilityAllApps()

    @ScriptInterface
    fun dismissNotificationShade() = mGlobalActionAutomator.dismissNotificationShade()

    @ScriptInterface
    fun gesture(start: Long, duration: Long, points: Array<IntArray>) = mGlobalActionAutomatorForGesture.gesture(start, duration, *points)

    @ScriptInterface
    @JvmOverloads
    fun gestureAsync(start: Long, duration: Long, points: Array<IntArray>, callback: GestureResultCallback? = null) = mGlobalActionAutomatorForGesture.gestureAsync(start, duration, points, callback)

    @ScriptInterface
    fun gestures(strokes: Array<GestureDescription.StrokeDescription>) = mGlobalActionAutomatorForGesture.gestures(*strokes)

    @ScriptInterface
    @JvmOverloads
    fun gesturesAsync(strokes: Array<GestureDescription.StrokeDescription>, callback: GestureResultCallback? = null) = mGlobalActionAutomatorForGesture.gesturesAsync(strokes, callback)

    @ScriptInterface
    fun click(x: Int, y: Int) = mGlobalActionAutomatorForGesture.click(x, y)

    @ScriptInterface
    fun press(x: Int, y: Int, duration: Int) = mGlobalActionAutomatorForGesture.press(x, y, duration)

    @ScriptInterface
    fun longClick(x: Int, y: Int) = mGlobalActionAutomatorForGesture.longClick(x, y)

    @ScriptInterface
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int) = mGlobalActionAutomatorForGesture.swipe(x1, y1, x2, y2, duration.toLong())

    @ScriptInterface
    fun paste(target: ActionTarget) = performAction(target.createAction(AccessibilityNodeInfo.ACTION_PASTE))

    @ScriptInterface
    fun isServiceRunning() = AccessibilityService.hasInstance()

    @ScriptInterface
    fun ensureService() = accessibilityBridge.ensureServiceStarted()

    // @Created by 抠脚本人 on Jul 10, 2023.
    // TODO by 抠脚本人 on Jul 10, 2023.
    //  ! 优化实现方式.
    //  ! en-US (translated by SuperMonster003 on Jul 26, 2024):
    //  ! Optimize implementation method.
    // TODO by SuperMonster003 on Jul 12, 2023.
    //  ! Ref to Auto.js Pro.
    //  ! zh-CN: 参考 Auto.js Pro.
    fun registerEvent(eventName: String, callback: AccessibilityEventCallback?) {
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
                Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)?.let { hardwareBuffer ->
                    // @Hint by SuperMonster003 on Jun 9, 2023.
                    //  ! To avoid the exception as below.
                    //  # java.lang.IllegalStateException: unable to getPixel(), pixel access is not supported on Config#HARDWARE bitmaps.
                    //  ! Reference: https://stackoverflow.com/questions/60462841/.
                    //  ! zh-CN:
                    //  ! 用于避免以下异常发生.
                    //  # java.lang.IllegalStateException: 无法调用 getPixel(), Config#HARDWARE 位图不支持像素访问.
                    //  ! 参阅: https://stackoverflow.com/questions/60462841/.
                    val bitmap = hardwareBuffer.copy(Bitmap.Config.ARGB_8888, true)

                    hardwareBuffer.recycle()
                    promiseAdapter.resolve(ImageWrapper.ofBitmap(bitmap))
                }
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
