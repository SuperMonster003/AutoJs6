package com.stardust.autojs.core.accessibility

import android.accessibilityservice.AccessibilityService
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
import com.stardust.autojs.annotation.ScriptInterface
import com.stardust.autojs.core.image.ImageWrapper
import com.stardust.autojs.core.util.ScriptPromiseAdapter
import com.stardust.autojs.runtime.ScriptRuntime
import com.stardust.autojs.runtime.accessibility.AccessibilityConfig
import com.stardust.automator.GlobalActionAutomator
import com.stardust.automator.UiObject
import com.stardust.automator.simple_action.ActionFactory
import com.stardust.automator.simple_action.ActionTarget
import com.stardust.automator.simple_action.SimpleAction
import com.stardust.util.DeveloperUtils
import com.stardust.util.ScreenMetrics
import com.stardust.view.accessibility.AccessibilityService.Companion.instance


/**
 * Created by Stardust on 2017/4/2.
 */
class SimpleActionAutomator(private val mAccessibilityBridge: AccessibilityBridge, private val mScriptRuntime: ScriptRuntime) {

    private lateinit var mGlobalActionAutomatorRaw: GlobalActionAutomator

    private val mGlobalActionAutomator: GlobalActionAutomator
        get() {
            initGlobalActionAutomatorIfNeeded()
            return mGlobalActionAutomatorRaw
        }

    private val mGlobalActionAutomatorForGesture: GlobalActionAutomator
        get() {
            initGlobalActionAutomatorIfNeeded()
            mGlobalActionAutomatorRaw.setScreenMetrics(mScreenMetrics)
            return mGlobalActionAutomatorRaw
        }

    private var mScreenMetrics: ScreenMetrics? = null

    private var mPromiseAdapter: ScriptPromiseAdapter? = null

    private val isRunningPackageSelf: Boolean
        get() = DeveloperUtils.isSelfPackage(mAccessibilityBridge.infoProvider.latestPackage)

    private fun initGlobalActionAutomatorIfNeeded() {
        if (!::mGlobalActionAutomatorRaw.isInitialized) {
            mGlobalActionAutomatorRaw = GlobalActionAutomator(Handler(mScriptRuntime.loopers.servantLooper)) {
                ensureService()
                return@GlobalActionAutomator mAccessibilityBridge.service!!
            }
        }
    }

    @ScriptInterface
    fun text(text: String, i: Int): ActionTarget = ActionTarget.TextActionTarget(text, i)

    @ScriptInterface
    fun bounds(left: Int, top: Int, right: Int, bottom: Int): ActionTarget = ActionTarget.BoundsActionTarget(Rect(left, top, right, bottom))

    @ScriptInterface
    fun editable(i: Int): ActionTarget = ActionTarget.EditableActionTarget(i)

    @ScriptInterface
    fun id(id: String): ActionTarget = ActionTarget.IdActionTarget(id)

    @ScriptInterface
    fun click(target: ActionTarget): Boolean = performAction(target.createAction(AccessibilityNodeInfo.ACTION_CLICK))

    @ScriptInterface
    fun longClick(target: ActionTarget): Boolean = performAction(target.createAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))

    @ScriptInterface
    fun scrollUp(target: ActionTarget): Boolean = performAction(target.createAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))

    @ScriptInterface
    fun scrollDown(target: ActionTarget): Boolean = performAction(target.createAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))

    @ScriptInterface
    fun scrollBackward(i: Int): Boolean = performAction(ActionFactory.createScrollAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, i))

    @ScriptInterface
    fun scrollForward(i: Int): Boolean = performAction(ActionFactory.createScrollAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, i))

    @ScriptInterface
    fun scrollMaxBackward(): Boolean = performAction(ActionFactory.createScrollMaxAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))

    @ScriptInterface
    fun scrollMaxForward(): Boolean = performAction(ActionFactory.createScrollMaxAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))

    @ScriptInterface
    fun focus(target: ActionTarget): Boolean = performAction(target.createAction(AccessibilityNodeInfo.ACTION_FOCUS))

    @ScriptInterface
    fun select(target: ActionTarget): Boolean = performAction(target.createAction(AccessibilityNodeInfo.ACTION_SELECT))

    @ScriptInterface
    fun setText(target: ActionTarget, text: String): Boolean = performAction(target.createAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text))

    @ScriptInterface
    fun appendText(target: ActionTarget, text: String): Boolean = performAction(target.createAction(UiObject.ACTION_APPEND_TEXT, text))

    @ScriptInterface
    fun back(): Boolean = mGlobalActionAutomator.back()

    @ScriptInterface
    fun home(): Boolean = mGlobalActionAutomator.home()

    @ScriptInterface
    fun recents(): Boolean = mGlobalActionAutomator.recents()

    @ScriptInterface
    fun notifications(): Boolean = mGlobalActionAutomator.notifications()

    @ScriptInterface
    fun quickSettings(): Boolean = mGlobalActionAutomator.quickSettings()

    @ScriptInterface
    fun powerDialog(): Boolean = mGlobalActionAutomator.powerDialog()

    @ScriptInterface
    fun splitScreen(): Boolean = mGlobalActionAutomator.splitScreen()

    @ScriptInterface
    fun lockScreen(): Boolean = mGlobalActionAutomator.lockScreen()

    @ScriptInterface
    fun takeScreenshot(): Boolean = mGlobalActionAutomator.takeScreenshot()

    @ScriptInterface
    fun headsethook(): Boolean = mGlobalActionAutomator.headsethook()

    @ScriptInterface
    fun accessibilityButton(): Boolean = mGlobalActionAutomator.accessibilityButton()

    @ScriptInterface
    fun accessibilityButtonChooser(): Boolean = mGlobalActionAutomator.accessibilityButtonChooser()

    @ScriptInterface
    fun accessibilityShortcut(): Boolean = mGlobalActionAutomator.accessibilityShortcut()

    @ScriptInterface
    fun accessibilityAllApps(): Boolean = mGlobalActionAutomator.accessibilityAllApps()

    @ScriptInterface
    fun dismissNotificationShade(): Boolean = mGlobalActionAutomator.dismissNotificationShade()

    @ScriptInterface
    fun gesture(start: Long, duration: Long, vararg points: IntArray): Boolean {
        return mGlobalActionAutomatorForGesture.gesture(start, duration, *points)
    }

    @ScriptInterface
    fun gestureAsync(start: Long, duration: Long, vararg points: IntArray) {
        mGlobalActionAutomatorForGesture.gestureAsync(start, duration, *points)
    }

    @ScriptInterface
    fun gestures(strokes: Any): Boolean {
        @Suppress("UNCHECKED_CAST")
        return mGlobalActionAutomatorForGesture.gestures(*strokes as Array<GestureDescription.StrokeDescription>)
    }

    @ScriptInterface
    fun gesturesAsync(strokes: Any) {
        @Suppress("UNCHECKED_CAST")
        mGlobalActionAutomatorForGesture.gesturesAsync(*strokes as Array<GestureDescription.StrokeDescription>)
    }

    @ScriptInterface
    fun click(x: Int, y: Int): Boolean {
        return mGlobalActionAutomatorForGesture.click(x, y)
    }

    @ScriptInterface
    fun press(x: Int, y: Int, delay: Int): Boolean {
        return mGlobalActionAutomatorForGesture.press(x, y, delay)
    }

    @ScriptInterface
    fun longClick(x: Int, y: Int): Boolean {
        return mGlobalActionAutomatorForGesture.longClick(x, y)
    }

    @ScriptInterface
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, delay: Int): Boolean {
        return mGlobalActionAutomatorForGesture.swipe(x1, y1, x2, y2, delay.toLong())
    }

    @ScriptInterface
    fun paste(target: ActionTarget): Boolean = performAction(target.createAction(AccessibilityNodeInfo.ACTION_PASTE))

    fun isServiceEnabled(): Boolean {
        return instance != null
    }

    fun ensureService() {
        mAccessibilityBridge.ensureServiceEnabled()
    }

    private fun performAction(simpleAction: SimpleAction): Boolean {
        ensureService()
        if (AccessibilityConfig.isUnintendedGuardEnabled() && isRunningPackageSelf) {
            return false
        }
        val roots = mAccessibilityBridge.windowRoots().filterNotNull()
        return when {
            roots.isEmpty() -> false
            else -> {
                var succeed = true
                for (root in roots) {
                    succeed = succeed and simpleAction.perform(UiObject.createRoot(root))
                }
                succeed
            }
        }
    }

    fun setScreenMetrics(metrics: ScreenMetrics) {
        mScreenMetrics = metrics
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun captureScreen(): ScriptPromiseAdapter {
        ScriptRuntime.requiresApi(Build.VERSION_CODES.R)
        ensureService()

        val promiseAdapter = mPromiseAdapter ?: ScriptPromiseAdapter()
        mPromiseAdapter = promiseAdapter

        val service = mAccessibilityBridge.service!!
        val executor = service.mainExecutor
        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                val imageWrapper = ImageWrapper.ofBitmap(bitmap)
                promiseAdapter.resolve(imageWrapper)
                mPromiseAdapter = null
            }

            override fun onFailure(errorCode: Int) {
                if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        captureScreen()
                    }, 50)
                } else {
                    Log.w(SimpleActionAutomator::class.java.name, "onFailure: $errorCode")
                    promiseAdapter.resolve(null)
                }
            }
        }

        service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)

        return promiseAdapter
    }

}
