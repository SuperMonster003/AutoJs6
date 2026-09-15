package org.autojs.plugin.common.api

/**
 * Versioned contract between AutoJs6 and official accessibility companion plugins, i.e. plugins
 * whose only job is to register an additional accessibility service next to the AutoJs6 service.
 *
 * A companion publishes its service component and the user-selected control policy through the
 * capabilities of its [PluginInfo]. AutoJs6 keeps a companion whose policy is
 * [SERVICE_POLICY_FOLLOW_HOST] aligned with its own accessibility service whenever it can enable
 * or disable services without user interaction, and notifies companions about every binding change
 * of its own service with [ACTION_HOST_SERVICE_STATE_CHANGED]. The broadcast is sent to the
 * companion package explicitly and is protected by [PLUGIN_PERMISSION].
 *
 * zh-CN: AutoJs6 与官方无障碍伴生插件 (仅用于在 AutoJs6 服务旁额外注册一个无障碍服务的插件) 之间的版本化契约.
 * 伴生插件通过 [PluginInfo] 的 capabilities 公开其服务组件与用户选择的控制策略. 当策略为
 * [SERVICE_POLICY_FOLLOW_HOST] 时, AutoJs6 会在自身能够无需用户交互地启停无障碍服务的情况下, 让伴生服务与自身服务保持一致,
 * 并在自身服务每次绑定状态变化时以 [ACTION_HOST_SERVICE_STATE_CHANGED] 通知伴生插件. 该广播显式发送至伴生包名并受 [PLUGIN_PERMISSION] 保护.
 */
object AutoJs6AccessibilityCompanionContract {
    const val CONTRACT_VERSION = 1

    const val HOST_PACKAGE_NAME = AutoJs6HostSettingsContract.HOST_PACKAGE_NAME
    const val PLUGIN_PERMISSION = AutoJs6HostSettingsContract.PLUGIN_PERMISSION

    /** Fully qualified component of the AutoJs6 accessibility service. zh-CN: AutoJs6 无障碍服务的完整组件名. */
    const val HOST_SERVICE_COMPONENT = "$HOST_PACKAGE_NAME/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"

    /**
     * Additional category on the `org.autojs.plugin.INFO` intent filter of every companion, so that
     * AutoJs6 can discover companions without binding unrelated plugins.
     * zh-CN: 每个伴生插件在 `org.autojs.plugin.INFO` intent filter 上附加的 category, 使 AutoJs6 无需绑定无关插件即可发现伴生插件.
     */
    const val INFO_CATEGORY = "org.autojs.plugin.category.ACCESSIBILITY_COMPANION"

    const val CAPABILITY_CONTRACT_VERSION = "org.autojs.plugin.accessibility.compat.CONTRACT_VERSION"
    const val CAPABILITY_MODE = "org.autojs.plugin.accessibility.compat.MODE"
    const val CAPABILITY_SERVICE_COMPONENT = "org.autojs.plugin.accessibility.compat.SERVICE_COMPONENT"
    const val CAPABILITY_TARGET_PACKAGES = "org.autojs.plugin.accessibility.compat.TARGET_PACKAGES"

    /** The user-selected control policy of the companion service. zh-CN: 用户为伴生服务选择的控制策略. */
    const val CAPABILITY_SERVICE_POLICY = "org.autojs.plugin.accessibility.compat.SERVICE_POLICY"

    const val SERVICE_POLICY_FOLLOW_HOST = "follow-host"
    const val SERVICE_POLICY_ENABLED = "enabled"
    const val SERVICE_POLICY_DISABLED = "disabled"

    /**
     * Explicit, permission-protected broadcast sent by AutoJs6 after its accessibility service was
     * bound or unbound. Companions may re-evaluate their own policy when they receive it.
     * zh-CN: AutoJs6 无障碍服务绑定或解绑后显式发送的受权限保护广播. 伴生插件收到后可重新评估自身策略.
     */
    const val ACTION_HOST_SERVICE_STATE_CHANGED = "org.autojs.plugin.action.ACCESSIBILITY_SERVICE_STATE_CHANGED"

    const val EXTRA_HOST_PACKAGE_NAME = "hostPackageName"
    const val EXTRA_HOST_SERVICE_COMPONENT = "hostServiceComponent"

    /** Whether the AutoJs6 service is listed in the system accessibility settings. zh-CN: AutoJs6 服务是否列于系统无障碍设置. */
    const val EXTRA_HOST_SERVICE_ENABLED = "hostServiceEnabled"

    /** Whether the AutoJs6 service instance is bound in the AutoJs6 process. zh-CN: AutoJs6 服务实例是否已在 AutoJs6 进程内绑定. */
    const val EXTRA_HOST_SERVICE_BOUND = "hostServiceBound"
}
