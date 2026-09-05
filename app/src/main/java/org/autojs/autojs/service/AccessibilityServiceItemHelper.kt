package org.autojs.autojs.service

import android.content.Context
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.ui.main.drawer.ServiceItemHelper
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Drawer-switch helper of the accessibility service (formerly `service/AccessibilityService`, renamed
 * so that only the core service class carries that name, D8). The switch mirrors the real running
 * state (enabled in system settings and bound in this process); a service that is enabled but not
 * bound shows as off with a "malfunctioning" subtitle, and switching it on re-enables it.
 *
 * zh-CN: 无障碍服务的抽屉开关辅助类 (原 `service/AccessibilityService`, 重命名后只有核心服务类使用该名称, D8).
 * 开关反映真实的运行状态 (系统设置中已启用且进程内已绑定); 已启用但未绑定的服务显示为关闭并带 "故障" 副标题, 打开开关即重新启用.
 */
open class AccessibilityServiceItemHelper(final override val context: Context) : ServiceItemHelper {

    private val mA11yTool = AccessibilityTool(context)

    override val isRunning
        get() = mA11yTool.isRunning()

    override fun active() {
        mA11yTool.restartService(true)
    }

    // The switch is synced about half a second after toggling; waiting for the bind here keeps it
    // from flipping back while the system is still binding the freshly enabled service.
    // zh-CN: 开关在切换约半秒后同步; 在此等待绑定, 避免系统仍在绑定刚启用的服务时开关又跳回去.
    override fun start(): Boolean {
        return mA11yTool.startService(true) && AccessibilityService.waitForStarted()
    }

    override fun startIfNeeded() {
        if (!isRunning) {
            start()
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
        if (!mA11yTool.restartService(true)) {
            ViewUtils.showToast(context, R.string.text_failed_to_restart_a11y_service)
        }
    }

    override fun onToggleSuccess() {
        if (mA11yTool.isMalfunctioning()) {
            ViewUtils.showToast(context, R.string.text_a11y_service_enabled_but_not_running, true)
        }
        super.onToggleSuccess()
    }

}
