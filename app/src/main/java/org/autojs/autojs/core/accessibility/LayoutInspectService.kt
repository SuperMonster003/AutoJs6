package org.autojs.autojs.core.accessibility

import android.accessibilityservice.AccessibilityServiceInfo

/**
 * Created by Stardust on 2017/7/13.
 */
class LayoutInspectService : AccessibilityService() {

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        super.onServiceConnected()
    }

    companion object {

        val instance: LayoutInspectService? = null

    }

}

