package org.autojs.autojs.core.accessibility

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import org.autojs.autojs.core.accessibility.window.A11yWindowRootPolicy
import org.autojs.autojs.core.accessibility.window.WindowRootPolicy
import org.autojs.autojs.core.accessibility.window.WindowRootProvider
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Created by Stardust on Mar 10, 2017.
 * Modified by SuperMonster003 as of Sep 5, 2026.
 */
class LayoutInspector(private val mContext: Context, private val mA11yTool: AccessibilityTool) {

    // The inspector looks at whatever is on screen: no script window filter, and when the system
    // does not provide the active window root yet, the last window that got focus (fast root), then
    // the application windows of the window list.
    // zh-CN: 布局分析器看的是屏幕上的一切: 不用脚本的窗口过滤器; 系统尚未提供活动窗口根节点时, 依次退到最近获得焦点的窗口 (fast root) 与窗口列表中的应用窗口.
    private val mRootProvider = WindowRootProvider.default
    private val mActiveWindowPolicy: A11yWindowRootPolicy = WindowRootPolicy()
    private val mFallbackPolicy = mActiveWindowPolicy.copy(mode = WindowRootPolicy.Mode.FAST, fallback = WindowRootPolicy.Fallback.APPLICATION_WINDOWS)

    @Volatile
    var capture: Capture? = null
        private set

    private val mCaptureAvailableListeners = CopyOnWriteArrayList<CaptureAvailableListener>()
    private val mExecutor = Executors.newSingleThreadExecutor()

    interface CaptureAvailableListener {
        fun onCaptureAvailable(capture: Capture, context: Context)
    }

    fun captureCurrentWindow(): Boolean {
        val service = AccessibilityService.instance
        if (service == null) {
            Log.d(LOG_TAG, "captureCurrentWindow: service = null")
            capture = null
            return false
        }
        mA11yTool.clearCache()
        val root = getRootInActiveWindow()
        if (root == null) {
            Log.d(LOG_TAG, "captureCurrentWindow: root = null")
            capture = null
            return false
        }
        mExecutor.execute {
            val windowInfoList = mRootProvider.windows().mapNotNull { window ->
                runCatching {
                    WindowInfo.create(mContext, window)
                }.onFailure {
                    Log.w(LOG_TAG, "captureCurrentWindow: failed to capture window", it)
                }.getOrNull()
            }
            val rootInfo = runCatching {
                NodeInfo.capture(mContext, root)
            }.onFailure {
                Log.w(LOG_TAG, "captureCurrentWindow: failed to capture root", it)
            }.getOrNull()
            if (rootInfo == null) {
                capture = null
                return@execute
            }
            val newCapture = Capture(windowInfoList, rootInfo)
            capture = newCapture
            for (listener in mCaptureAvailableListeners) {
                try {
                    listener.onCaptureAvailable(newCapture, mContext)
                } catch (e: Throwable) {
                    Log.e(LOG_TAG, "captureCurrentWindow: listener failed", e)
                }
            }
        }
        return true
    }

    /**
     * [captureCurrentWindow] retried on the main thread every [intervalMillis] while the active
     * window root is not available yet, which is common right after the service has been bound;
     * [onUnavailable] runs once all [attempts] have failed. Listeners are notified as usual when a
     * capture succeeds.
     *
     * zh-CN: [captureCurrentWindow] 的重试版: 活动窗口根节点尚不可用时 (服务刚绑定后常见) 每 [intervalMillis] 在主线程重试,
     * [attempts] 次全部失败后调用 [onUnavailable]. 捕获成功时照常通知监听者.
     */
    @JvmOverloads
    fun captureCurrentWindowWithRetry(
        attempts: Int = DEFAULT_CAPTURE_ATTEMPTS,
        intervalMillis: Long = DEFAULT_CAPTURE_INTERVAL_MILLIS,
        onUnavailable: Runnable?,
    ) {
        val handler = Handler(Looper.getMainLooper())
        var remaining = attempts
        handler.post(object : Runnable {
            override fun run() {
                if (captureCurrentWindow()) return
                if (--remaining > 0) {
                    handler.postDelayed(this, intervalMillis)
                } else {
                    Log.d(LOG_TAG, "captureCurrentWindowWithRetry: root still unavailable after $attempts attempts")
                    onUnavailable?.run()
                }
            }
        })
    }

    fun addCaptureAvailableListener(l: CaptureAvailableListener) {
        mCaptureAvailableListeners.add(l)
    }

    fun removeCaptureAvailableListener(l: CaptureAvailableListener): Boolean {
        return mCaptureAvailableListeners.remove(l)
    }

    private fun getRootInActiveWindow(): AccessibilityNodeInfo? {
        return mRootProvider.activeRoot(mActiveWindowPolicy) ?: mRootProvider.activeRoot(mFallbackPolicy)
    }

    private fun refreshChildList(root: AccessibilityNodeInfo?) {
        root?.refresh() ?: return
        val childCount = root.childCount
        for (i in 0 until childCount) {
            refreshChildList(root.getChild(i))
        }
    }

    fun clearCapture() {
        capture = null
    }

    companion object {

        private val LOG_TAG = LayoutInspector::class.java.simpleName

        /** Retry budget of [captureCurrentWindowWithRetry]: 10 x 300 ms = 3 s. zh-CN: [captureCurrentWindowWithRetry] 的重试预算: 10 x 300 ms = 3 s. */
        const val DEFAULT_CAPTURE_ATTEMPTS = 10

        const val DEFAULT_CAPTURE_INTERVAL_MILLIS = 300L

    }
}
