@file:Suppress("unused")

package org.autojs.autojs.core.accessibility

import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.accessibility.event.A11yEvent
import org.autojs.autojs.core.accessibility.event.A11yEventBus
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
import org.autojs.autojs.runtime.api.modules.global.Global
import org.autojs.autojs.util.DeveloperUtils
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by Stardust on Apr 2, 2017.
 * Modified by SuperMonster003 as of Dec 29, 2025.
 */
class SimpleActionAutomator(private val accessibilityBridge: AccessibilityBridge, private val scriptRuntime: ScriptRuntime) {

    private val mA11yEventOwnerId: String = scriptRuntime.ownerId

    private val mGlobalActionAutomator by lazy {
        createGlobalActionAutomator(handler = null)
    }

    private val mGlobalActionAutomatorForGestureRaw by lazy {
        createGlobalActionAutomator(Handler(scriptRuntime.loopers.servantLooper))
    }

    private val mGlobalActionAutomatorForGesture
        get() = mGlobalActionAutomatorForGestureRaw.apply { setScreenMetrics(mScreenMetrics) }

    // Device-pixel taps for UiObject.clickBounds(): the script's servant Looper, but no
    // ScreenMetrics, as node bounds are device pixels already.
    // zh-CN: 供 UiObject.clickBounds() 使用的设备像素点按: 走脚本的 servant Looper, 但不带 ScreenMetrics, 因为节点边界已是设备像素.
    private val mGlobalActionAutomatorForDevicePixels by lazy {
        createGlobalActionAutomator(Handler(scriptRuntime.loopers.servantLooper))
    }

    init {
        // UiObject.clickBounds() falls back to a device-pixel tap; on a virtual display it must go
        // to that display instead of the accessibility gesture service (main display only).
        accessibilityBridge.tap = AccessibilityBridge.Tap { x, y ->
            if (scriptRuntime.displayRoute.isVirtual) scriptRuntime.displayRoute.tap(x, y)
            else mGlobalActionAutomatorForDevicePixels.click(x, y)
        }
    }

    private val isRunningPackageSelf
        get() = DeveloperUtils.isSelfPackage(Global.currentPackage(scriptRuntime, emptyArray()))

    private var mScreenMetrics: ScreenMetrics? = null
    private val mScriptRuntime = WeakReference(scriptRuntime)

    private var mPromiseAdapter: ScriptPromiseAdapter? = null

    private fun createGlobalActionAutomator(handler: Handler?) =
        GlobalActionAutomator(scriptRuntime.uiHandler.applicationContext, handler) {
            ensureService()
            accessibilityBridge.service
        }

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
    fun back() = virtualKey(KeyEvent.KEYCODE_BACK) ?: mGlobalActionAutomator.back()

    @ScriptInterface
    fun home() = virtualKey(KeyEvent.KEYCODE_HOME) ?: mGlobalActionAutomator.home()

    @ScriptInterface
    fun recents() = virtualKey(KeyEvent.KEYCODE_APP_SWITCH) ?: mGlobalActionAutomator.recents()

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
    fun headsethook() = virtualKey(KeyEvent.KEYCODE_HEADSETHOOK) ?: mGlobalActionAutomator.headsethook()

    @ScriptInterface
    fun switchToInputMethod(packageName: String): Boolean {
        ScriptRuntime.requiresApi(Build.VERSION_CODES.R)
        val inputMethodManager = ScriptRuntime.applicationContext
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val inputMethod = inputMethodManager.enabledInputMethodList
            .firstOrNull { it.packageName == packageName }
            ?: return false
        return switchToInputMethodWithId(inputMethod.id)
    }

    @ScriptInterface
    fun switchToInputMethodWithId(inputMethodId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Preserve the standard script-facing version error before accessing the service.
            ScriptRuntime.requiresApi(Build.VERSION_CODES.R)
            return false
        }
        ensureService()
        return accessibilityBridge.service
            ?.softKeyboardController
            ?.switchToInputMethod(inputMethodId)
            ?: false
    }

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
    fun gesture(start: Long, duration: Long, points: Array<IntArray>): Boolean =
        if (scriptRuntime.displayRoute.isVirtual) dispatchPolyline(duration, points)
        else mGlobalActionAutomatorForGesture.gesture(start, duration, *points)

    @ScriptInterface
    @JvmOverloads
    fun gestureAsync(start: Long, duration: Long, points: Array<IntArray>, callback: GestureResultCallback? = null) {
        if (scriptRuntime.displayRoute.isVirtual) {
            val ok = dispatchPolyline(duration, points)
            completeGesture(callback, ok, points)
            return
        }
        mGlobalActionAutomatorForGesture.gestureAsync(start, duration, points, callback)
    }

    @ScriptInterface
    fun gestures(strokes: Array<GestureDescription.StrokeDescription>): Boolean =
        if (scriptRuntime.displayRoute.isVirtual) strokes.all { dispatchStroke(it) }
        else mGlobalActionAutomatorForGesture.gestures(*strokes)

    @ScriptInterface
    @JvmOverloads
    fun gesturesAsync(strokes: Array<GestureDescription.StrokeDescription>, callback: GestureResultCallback? = null) {
        if (scriptRuntime.displayRoute.isVirtual) {
            val ok = strokes.all { dispatchStroke(it) }
            completeGesture(callback, ok)
            return
        }
        mGlobalActionAutomatorForGesture.gesturesAsync(strokes, callback)
    }

    @ScriptInterface
    fun click(x: Int, y: Int): Boolean =
        if (scriptRuntime.displayRoute.isVirtual) scriptRuntime.displayRoute.tap(x, y)
        else mGlobalActionAutomatorForGesture.click(x, y)

    @ScriptInterface
    fun press(x: Int, y: Int, duration: Int): Boolean =
        if (scriptRuntime.displayRoute.isVirtual) scriptRuntime.displayRoute.longPress(x, y, duration.coerceAtLeast(1))
        else mGlobalActionAutomatorForGesture.press(x, y, duration)

    @ScriptInterface
    fun longClick(x: Int, y: Int): Boolean =
        if (scriptRuntime.displayRoute.isVirtual) scriptRuntime.displayRoute.longPress(x, y, 600)
        else mGlobalActionAutomatorForGesture.longClick(x, y)

    @ScriptInterface
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int): Boolean =
        if (scriptRuntime.displayRoute.isVirtual) scriptRuntime.displayRoute.swipe(x1, y1, x2, y2, duration.coerceAtLeast(1))
        else mGlobalActionAutomatorForGesture.swipe(x1, y1, x2, y2, duration.toLong())

    @ScriptInterface
    fun paste(target: ActionTarget) = performAction(target.createAction(AccessibilityNodeInfo.ACTION_PASTE))

    // "Running" means enabled in system settings with an instance bound in this process, the same
    // notion as auto.isRunning() and the drawer switch; it used to check the instance alone (D5).
    // zh-CN: "运行中" 指系统设置中已启用且进程内已绑定实例, 与 auto.isRunning() 及抽屉开关一致; 原先只检查实例 (D5).
    @ScriptInterface
    fun isServiceRunning() = AccessibilityTool.controller.isRunning()

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
    /**
     * Registers (or with a null [callback] removes) this script's callback for [eventName]; one slot
     * per event type and script. The subscription lives on the process-wide
     * [AccessibilityService.eventBus], so it also survives a service restart. [options] optionally
     * thins out repeated events (`distinct` / `debounce` of `auto.registerEvent`).
     * zh-CN: 注册 (或以 null [callback] 移除) 本脚本对 [eventName] 的回调; 每种事件类型每个脚本一个槽.
     * 订阅位于进程级的 [AccessibilityService.eventBus], 因此服务重启后依然存在. [options] 可选地稀释重复事件
     * (`auto.registerEvent` 的 `distinct` / `debounce`).
     */
    @JvmOverloads
    fun registerEvent(eventName: String, callback: AccessibilityEventCallback?, options: A11yEventBus.Options<A11yEvent> = A11yEventBus.Options()) {
        ensureService()
        AccessibilityService.addAccessibilityEventCallback(mA11yEventOwnerId, eventName, callback, options)
    }

    // @Created by 抠脚本人 on Jul 10, 2023.
    fun removeEvent(eventName: String) {
        AccessibilityService.removeAccessibilityEventCallback(mA11yEventOwnerId, eventName)
    }

    /**
     * Called when the script exits, only clears accessibility
     * event listeners registered by the current script.
     * zh-CN: 脚本退出时调用, 只清理由当前脚本注册的无障碍事件监听.
     */
    fun removeAllEventsForThisRuntime() {
        AccessibilityService.removeAllAccessibilityEventCallbacks(mA11yEventOwnerId)
    }

    /** null = not a virtual-screen run, so the caller keeps using the accessibility global action. */
    private fun virtualKey(keyCode: Int): Boolean? =
        if (scriptRuntime.displayRoute.isVirtual) scriptRuntime.displayRoute.key(keyCode) else null

    /** Splits a polyline gesture into straight swipes on the virtual display. */
    private fun dispatchPolyline(duration: Long, points: Array<IntArray>): Boolean {
        val route = scriptRuntime.displayRoute
        if (points.isEmpty()) return false
        if (points.size == 1) {
            return route.tap(points[0].getOrElse(0) { 0 }, points[0].getOrElse(1) { 0 })
        }
        val segment = (duration / (points.size - 1)).coerceAtLeast(1L).toInt()
        return points.asList().zipWithNext().all { (from, to) ->
            route.swipe(
                from.getOrElse(0) { 0 }, from.getOrElse(1) { 0 },
                to.getOrElse(0) { 0 }, to.getOrElse(1) { 0 },
                segment,
            )
        }
    }

    /** Approximates one accessibility stroke by its start/end points (PathMeasure samples the path). */
    private fun dispatchStroke(stroke: GestureDescription.StrokeDescription): Boolean {
        val measure = PathMeasure(stroke.path, false)
        val start = FloatArray(2)
        val end = FloatArray(2)
        measure.getPosTan(0f, start, null)
        measure.getPosTan(measure.length, end, null)
        return scriptRuntime.displayRoute.swipe(
            start[0].toInt(), start[1].toInt(), end[0].toInt(), end[1].toInt(),
            stroke.duration.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt(),
        )
    }

    /** Async gesture APIs still need their callback, even when the injection was synchronous. */
    private fun completeGesture(callback: GestureResultCallback?, ok: Boolean, points: Array<IntArray>? = null) {
        if (callback == null) return
        val first = points?.firstOrNull()
        val x = first?.getOrElse(0) { 0 }?.toFloat() ?: 0f
        val y = first?.getOrElse(1) { 0 }?.toFloat() ?: 0f
        val path = Path().apply { moveTo(x, y); lineTo(x, y) }
        val description = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 1L))
            .build()
        if (ok) callback.onCompleted(description) else callback.onCancelled(description)
    }

    private fun performAction(simpleAction: SimpleAction): Boolean {
        ensureService()
        val roots = accessibilityBridge.windowRoots()
        if (AccessibilityConfig.isUnintendedGuardEnabled()) {
            // The usage-stats / shell "foreground package" describes the physical screen. On a
            // virtual display the guard must look at the windows the scoped query actually sees.
            // zh-CN: 使用情况/ shell 得到的「前台包名」描述的是物理主屏; 虚拟屏上保护模式应看作用域内实际窗口.
            val selfInFront = if (scriptRuntime.displayRoute.isVirtual) {
                roots.any { root -> DeveloperUtils.isSelfPackage("${root.packageName}") }
            } else {
                isRunningPackageSelf
            }
            if (selfInFront) return false
        }
        return roots.isNotEmpty() &&
            roots.map { root -> simpleAction.perform(UiObject.createRoot(root)) }.all { it /* == true */ }
    }

    fun setScreenMetrics(metrics: ScreenMetrics) {
        mScreenMetrics = metrics
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun captureScreen(): ScriptPromiseAdapter {
        // A virtual display owns its ImageReader surface, so MediaProjection (default display only)
        // must not be involved. Return the same ImageWrapper type as the accessibility screenshot.
        if (scriptRuntime.displayRoute.isVirtual) {
            return ScriptPromiseAdapter().also { it.resolve(scriptRuntime.displayRoute.capture()) }
        }
        ScriptRuntime.requiresApi(Build.VERSION_CODES.R)
        ensureService()

        // A capture already in flight is shared by concurrent callers through the same promise.
        // zh-CN: 已在进行的截图经同一个 promise 与并发调用方共享.
        val promiseAdapter = mPromiseAdapter ?: ScriptPromiseAdapter().also { mPromiseAdapter = it }
        val service = accessibilityBridge.service ?: run {
            promiseAdapter.resolve(null)
            mPromiseAdapter = null
            return promiseAdapter
        }
        A11yScreenshotter(service).takeWithRetry { result ->
            when (result) {
                is A11yScreenshotter.Result.Success -> promiseAdapter.resolve(ImageWrapper.ofBitmap(scriptRuntime, result.bitmap))
                is A11yScreenshotter.Result.Failure -> {
                    Log.w(TAG, "captureScreen failed: ${result.errorCode}")
                    promiseAdapter.resolve(null)
                }
            }
            mPromiseAdapter = null
        }
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

        /** The `distinct` key function of `auto.registerEvent` options (a JS function adapted to this interface). zh-CN: `auto.registerEvent` 选项中的 `distinct` 键函数 (JS 函数适配为本接口). */
        interface EventKeyFunction {
            fun keyOf(event: AccessibilityEventWrapper): Any?
        }

    }

}
