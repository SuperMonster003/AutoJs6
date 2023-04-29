package org.autojs.autojs.service

import android.content.Context
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.ui.main.drawer.ServiceItemHelper
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

class AccessibilityService(override val context: Context) : ServiceItemHelper {

    private val mAccessibilityTool: AccessibilityTool = AccessibilityTool(context)

    override val isRunning
        get() = mAccessibilityTool.service.isEnabled()

    override fun active() = start()

    override fun start() {
        mAccessibilityTool.service.enable()
    }

    override fun startIfNeeded() {
        mAccessibilityTool.service.enableIfNeeded()
    }

    override fun stop() {
        if (!mAccessibilityTool.service.disable()) {
            ViewUtils.showToast(context, R.string.text_failed_to_disable_a11y_service)
        }
    }

}
