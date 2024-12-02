package org.autojs.autojs.service

import android.content.Context
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.ui.main.drawer.ServiceItemHelper
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

open class AccessibilityService(final override val context: Context) : ServiceItemHelper {

    private val mA11yTool = AccessibilityTool(context)

    override val isRunning
        get() = mA11yTool.serviceExists() || mA11yTool.isServiceRunning()

    override fun active(): Boolean {
        return mA11yTool.restartService(true)
    }

    override fun start(): Boolean {
        return mA11yTool.startService(true)
    }

    override fun startIfNeeded() {
        if (!isRunning) {
            mA11yTool.startService(true)
        }
    }

    override fun stop(): Boolean {
        if (mA11yTool.stopService(true)) {
            return true
        }
        ViewUtils.showToast(context, R.string.text_failed_to_disable_a11y_service)
        return false
    }

    fun restart() {
        if (mA11yTool.stopService(true)) {
            start()
        } else {
            ViewUtils.showToast(context, R.string.text_failed_to_disable_a11y_service)
        }
    }

    override fun onToggleSuccess() {
        if (mA11yTool.serviceExists() && !mA11yTool.isServiceRunning()) {
            ViewUtils.showToast(context, R.string.text_a11y_service_enabled_but_not_running, true)
        }
        super.onToggleSuccess()
    }

}
