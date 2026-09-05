package org.autojs.autojs.core.accessibility.control

/**
 * One way of enabling or disabling the accessibility service in system settings without user
 * interaction (root shell, `WRITE_SECURE_SETTINGS`, ...). Strategies are tried by the controller in
 * preference order; the system settings page is not a strategy because it cannot report success.
 *
 * zh-CN: 一种无需用户交互即可在系统设置中启用或禁用无障碍服务的方式 (root shell, `WRITE_SECURE_SETTINGS` 等).
 * 控制器按偏好顺序依次尝试; 系统设置页不是策略, 因为它无法报告成功与否.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
interface ServiceStartStrategy {

    /** Short name for logs, e.g. "root". zh-CN: 用于日志的短名称, 如 "root". */
    val name: String

    /** Whether the user opted in to this way and it is usable right now. zh-CN: 用户是否已选择此方式且此刻可用. */
    fun isAvailable(): Boolean

    /**
     * Enables the service and reports whether the setting lists it afterwards (read back, not
     * merely "the commands ran").
     * zh-CN: 启用服务, 并以回读结果 (而非 "命令已执行") 报告设置中是否已列出该服务.
     */
    fun enable(): Boolean

    /** Disables the service and reports whether the setting no longer lists it. zh-CN: 禁用服务, 并报告设置中是否已不再列出该服务. */
    fun disable(): Boolean

}

/**
 * Raw access to the `enabled_accessibility_services` setting. Implementations may throw; the
 * strategy turns that into a failed attempt.
 * zh-CN: 对 `enabled_accessibility_services` 设置的原始读写. 实现可以抛出异常, 策略会把它视为一次失败的尝试.
 */
interface EnabledServicesStore {

    fun read(): String?

    fun write(value: String)

}

/**
 * Enables or disables a service by rewriting the enabled-services list through an [EnabledServicesStore].
 * Only the list is written: since Android 7.0 the framework derives `accessibility_enabled` from the
 * bound services itself (`AccessibilityManagerService.updateAccessibilityEnabledSetting`), and writing
 * it here used to leave a stale `0` behind while other services stayed enabled.
 *
 * zh-CN: 通过 [EnabledServicesStore] 改写已启用服务列表来启用或禁用服务. 只写列表:
 * 自 Android 7.0 起框架会根据已绑定的服务自行推导 `accessibility_enabled`
 * (`AccessibilityManagerService.updateAccessibilityEnabledSetting`), 此前在这里写入该值会在其它服务仍启用时残留一个过期的 `0`.
 *
 * @param readBack how the result is verified; defaults to the store, callers pass the plain settings
 * read when the store itself is expensive (a root shell).
 * zh-CN: 结果的校验方式; 默认为 store 本身, store 昂贵时 (root shell) 由调用方传入普通的设置读取.
 */
class SettingsStoreStartStrategy(
    override val name: String,
    private val serviceName: String,
    private val store: EnabledServicesStore,
    private val available: () -> Boolean,
    private val readBack: () -> String? = store::read,
    private val log: (String) -> Unit = {},
) : ServiceStartStrategy {

    override fun isAvailable(): Boolean = runCatching(available)
        .onFailure { log("$name: availability check failed: $it") }
        .getOrDefault(false)

    override fun enable(): Boolean = runCatching {
        val current = store.read()
        // A service that is listed but not bound (malfunctioning) is only bound again when the
        // system sees it leave and re-enter the list, hence the two writes in that case.
        // zh-CN: 已列出但未绑定 (故障) 的服务只有在系统看到它离开并重新进入列表时才会被重新绑定, 因此该情况下写两次.
        if (EnabledA11yServices.contains(current, serviceName)) {
            store.write(EnabledA11yServices.detach(current, serviceName))
        }
        store.write(EnabledA11yServices.attach(current, serviceName))
        EnabledA11yServices.contains(readBack(), serviceName)
    }.onFailure { log("$name: unable to enable $serviceName: $it") }.getOrDefault(false)

    override fun disable(): Boolean = runCatching {
        store.write(EnabledA11yServices.detach(store.read(), serviceName))
        !EnabledA11yServices.contains(readBack(), serviceName)
    }.onFailure { log("$name: unable to disable $serviceName: $it") }.getOrDefault(false)

}
