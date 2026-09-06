package org.autojs.autojs.core.accessibility

import org.autojs.autojs.AbstractAutoJs
import org.autojs.autojs.core.activity.ActivityInfoProvider
import org.autojs.autojs.runtime.accessibility.AccessibilityConfig

/**
 * Transformed by SuperMonster003 on Sep 5, 2026.
 */
class AccessibilityBridgeImpl(private val autoJs: AbstractAutoJs) : AccessibilityBridge(
    autoJs.applicationContext,
    AccessibilityConfig(),
    autoJs.uiHandler,
) {

    override fun ensureServiceStarted(isForcibleRestart: Boolean) {
        autoJs.accessibilityTool.ensureService(isForcibleRestart)
    }

    override fun awaitServiceAvailable(timeoutMillis: Long): Boolean = AccessibilityTool.controller.awaitAvailable(timeoutMillis)

    // The registry's instance is null exactly when no service is bound.
    // zh-CN: 注册表的实例恰在无服务绑定时为 null.
    override val service: AccessibilityService?
        get() = AccessibilityService.instance

    override val infoProvider: ActivityInfoProvider
        get() = autoJs.infoProvider

    override val notificationObserver: AccessibilityNotificationObserver
        get() = autoJs.notificationObserver

}
