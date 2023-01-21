package org.autojs.autojs.service

import android.content.Context
import org.autojs.autojs.core.accessibility.AccessibilityServiceTool
import org.autojs.autojs.ui.main.drawer.ServiceItemHelper
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

class AccessibilityService(override val context: Context) : ServiceItemHelper {

    private val mAccessibilityServiceTool: AccessibilityServiceTool = AccessibilityServiceTool(context)

    override val isRunning
        get() = mAccessibilityServiceTool.isAccessibilityServiceEnabled()

    override fun active() = start()

    override fun start() {
        mAccessibilityServiceTool.enableAccessibilityService()
    }

    override fun startIfNeeded() {
        mAccessibilityServiceTool.autoEnableIfNeeded()
    }

    override fun stop() {
        if (!mAccessibilityServiceTool.disableAccessibilityService()) {
            ViewUtils.showToast(context, R.string.text_failed_to_disable_a11y_service)
        }
    }

}
