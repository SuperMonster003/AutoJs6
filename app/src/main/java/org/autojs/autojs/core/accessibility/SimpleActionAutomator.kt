package org.autojs.autojs.core.accessibility

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
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.accessibility.AccessibilityService.Companion.isRunning
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


/**
 * Created by Stardust on 2017/4/2.
 */
class SimpleActionAutomator(private val accessibilityBridge: AccessibilityBridge, private val scriptRuntime: ScriptRuntime) {

    private val globalActionAutomatorRaw: GlobalActionAutomator by lazy {
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
    fun back(): Boolean = globalActionAutomator.back()

    @ScriptInterface
    fun home(): Boolean = globalActionAutomator.home()

    @ScriptInterface
    fun recents(): Boolean = globalActionAutomator.recents()

    @ScriptInterface
    fun notifications(): Boolean = globalActionAutomator.notifications()

    @ScriptInterface
    fun quickSettings(): Boolean = globalActionAutomator.quickSettings()

    @ScriptInterface
    fun powerDialog(): Boolean = globalActionAutomator.powerDialog()

    @ScriptInterface
    fun splitScreen(): Boolean = globalActionAutomator.splitScreen()

    @ScriptInterface
    fun lockScreen(): Boolean = globalActionAutomator.lockScreen()

    @ScriptInterface
    fun takeScreenshot(): Boolean = globalActionAutomator.takeScreenshot()

    @ScriptInterface
    fun headsethook(): Boolean = globalActionAutomator.headsethook()

    @ScriptInterface
    fun accessibilityButton(): Boolean = globalActionAutomator.accessibilityButton()

    @ScriptInterface
    fun accessibilityButtonChooser(): Boolean = globalActionAutomator.accessibilityButtonChooser()

    @ScriptInterface
    fun accessibilityShortcut(): Boolean = globalActionAutomator.accessibilityShortcut()

    @ScriptInterface
    fun accessibilityAllApps(): Boolean = globalActionAutomator.accessibilityAllApps()

    @ScriptInterface
    fun dismissNotificationShade(): Boolean = globalActionAutomator.dismissNotificationShade()

    @ScriptInterface
    fun gesture(start: Long, duration: Long, vararg points: IntArray): Boolean {
        return globalActionAutomatorForGesture.gesture(start, duration, *points)
    }

    @ScriptInterface
    fun gestureAsync(start: Long, duration: Long, vararg points: IntArray) {
        globalActionAutomatorForGesture.gestureAsync(start, duration, *points)
    }

    @ScriptInterface
    fun gestures(strokes: Any): Boolean {
        @Suppress("UNCHECKED_CAST")
        return globalActionAutomatorForGesture.gestures(*strokes as Array<GestureDescription.StrokeDescription>)
    }

    @ScriptInterface
    fun gesturesAsync(strokes: Any) {
        @Suppress("UNCHECKED_CAST")
        globalActionAutomatorForGesture.gesturesAsync(*strokes as Array<GestureDescription.StrokeDescription>)
    }

    @ScriptInterface
    fun click(x: Int, y: Int): Boolean {
        return globalActionAutomatorForGesture.click(x, y)
    }

    @ScriptInterface
    fun press(x: Int, y: Int, delay: Int): Boolean {
        return globalActionAutomatorForGesture.press(x, y, delay)
    }

    @ScriptInterface
    fun longClick(x: Int, y: Int): Boolean {
        return globalActionAutomatorForGesture.longClick(x, y)
    }

    @ScriptInterface
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, delay: Int): Boolean {
        return globalActionAutomatorForGesture.swipe(x1, y1, x2, y2, delay.toLong())
    }

    @ScriptInterface
    fun paste(target: ActionTarget): Boolean {
        return performAction(target.createAction(AccessibilityNodeInfo.ACTION_PASTE))
    }

    @ScriptInterface
    fun isServiceRunning() = isRunning()

    @ScriptInterface
    fun ensureService() = accessibilityBridge.ensureServiceEnabled()

    private fun performAction(simpleAction: SimpleAction): Boolean {
        ensureService()
        if (AccessibilityConfig.isUnintendedGuardEnabled() && isRunningPackageSelf) {
            return false
        }
        return accessibilityBridge.windowRoots().filterNotNull().let { roots ->
            when {
                roots.isEmpty() -> false
                else -> {
                    var succeed = true
                    roots.forEach { root ->
                        simpleAction.perform(UiObject.createRoot(root)).also { succeed = succeed and it }
                    }
                    succeed
                }
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

        val service = accessibilityBridge.service!!
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
