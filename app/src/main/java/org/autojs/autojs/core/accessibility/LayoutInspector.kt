package org.autojs.autojs.core.accessibility

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Created by Stardust on Mar 10, 2017.
 * Modified by SuperMonster003 as of May 26, 2022.
 */
class LayoutInspector(private val mContext: Context) {

    @Volatile
    var capture: Capture? = null
        private set

    private val mCaptureAvailableListeners = CopyOnWriteArrayList<CaptureAvailableListener>()
    private val mExecutor = Executors.newSingleThreadExecutor()
    private val mA11yTool = AccessibilityTool(mContext)

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
        val root = getRootInActiveWindow(service)
        if (root == null) {
            Log.d(LOG_TAG, "captureCurrentWindow: root = null")
            capture = null
            return false
        }
        mExecutor.execute {
            val windowInfoList = runCatching {
                service.windows
            }.onFailure {
                Log.w(LOG_TAG, "captureCurrentWindow: failed to get windows", it)
            }.getOrDefault(emptyList()).mapNotNull { window ->
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

    fun addCaptureAvailableListener(l: CaptureAvailableListener) {
        mCaptureAvailableListeners.add(l)
    }

    fun removeCaptureAvailableListener(l: CaptureAvailableListener): Boolean {
        return mCaptureAvailableListeners.remove(l)
    }

    private fun getRootInActiveWindow(service: AccessibilityService): AccessibilityNodeInfo? {
        return service.rootInActiveWindow ?: service.fastRootInActiveWindow
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
    }
}
