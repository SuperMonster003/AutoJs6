package org.autojs.autojs.core.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.util.Log
import org.autojs.autojs.core.accessibility.AccessibilityService as CoreAccessibilityService
import org.autojs.autojs.core.pref.keys.A11yKeys

class AccessibilityServiceUsher : CoreAccessibilityService() {

    override fun onServiceConnected() {
        Log.d(TAG, "onServiceConnected")
        applyServiceInfoPreferences()
        super.onServiceConnected()
    }

    /**
     * Roadmap P5.5: when the platform skipped `onServiceConnected`, the preferences are applied from
     * the first event instead, so that an adopted instance behaves like a connected one.
     * zh-CN: Roadmap P5.5: 平台跳过 `onServiceConnected` 时改由首个事件应用偏好, 使被采纳的实例与正常连接的实例行为一致.
     */
    override fun onAdoptedWithoutConnectedCallback() {
        super.onAdoptedWithoutConnectedCallback()
        runCatching { applyServiceInfoPreferences() }
            .onFailure { Log.w(TAG, "Applying the service info preferences after the adoption failed", it) }
    }

    /** The stable-mode and gesture-observing preferences, written into the service info. zh-CN: 稳定模式与手势观察偏好, 写入服务信息. */
    private fun applyServiceInfoPreferences() {
        val serviceInfo = serviceInfo ?: return
        if (A11yKeys.STABLE_MODE.get()) {
            serviceInfo.flags = serviceInfo.flags and AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.inv()
        } else {
            serviceInfo.flags = serviceInfo.flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (A11yKeys.GESTURE_OBSERVING.get()) {
                serviceInfo.flags = serviceInfo.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
            } else {
                serviceInfo.flags = serviceInfo.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
            }
        }
        setServiceInfo(serviceInfo)
    }

    companion object {

        private val TAG = AccessibilityServiceUsher::class.java.simpleName

    }

}
