package org.autojs.autojs.service

import android.content.Context
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.ui.main.drawer.ServiceItemHelper
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

class AccessibilityService(override val context: Context) : ServiceItemHelper {

    private val mA11yTool = AccessibilityTool(context)
    private val mA11yToolService = mA11yTool.service

    override val isRunning
        get() = mA11yToolService.isRunning()

    override fun active(): Boolean {
        var result = true
        result = mA11yToolService.stop(false) && result
        result = mA11yToolService.start(false) && result
        return result
    }

    override fun start(): Boolean {
        return mA11yToolService.start()
    }

    override fun startIfNeeded() {
        if (!isRunning) {
            mA11yToolService.start()
        }
    }

    override fun stop(): Boolean {
        if (mA11yToolService.stop()) {
            return true
        }
        ViewUtils.showToast(context, R.string.text_failed_to_disable_a11y_service)
        return false
    }

    fun restart() {
        if (mA11yToolService.stop()) {
            start()
        } else {
            ViewUtils.showToast(context, R.string.text_failed_to_disable_a11y_service)
        }
    }

}
