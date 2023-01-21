package org.autojs.autojs.core.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import org.autojs.autojs.pref.Pref

class ApplicationAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.let {
                flags = (if (Pref.isStableModeEnabled) flags and it.inv() else flags or it)
            }
            AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags = (if (Pref.isGestureObservingEnabled) flags or it else flags and it.inv())
                }
            }
        }
        super.onServiceConnected()
    }

}