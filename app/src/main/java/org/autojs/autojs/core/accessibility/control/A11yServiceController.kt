package org.autojs.autojs.core.accessibility.control

import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry.State

/**
 * Starts, stops and waits for the accessibility service, combining the two axes of its state:
 * "enabled in system settings" (read through [enabledServicesReader], not observable in-process) and
 * the in-process [registry] state (bound / operational).
 *
 * The service type is a type parameter and every Android touch point is injected, so the class is
 * Android-free and unit-tested; [org.autojs.autojs.core.accessibility.AccessibilityTool] wires the
 * real dependencies and is the Android-side facade.
 *
 * zh-CN: 负责无障碍服务的启动, 停止与等待, 组合其状态的两个维度: "系统设置中已启用"
 * (经 [enabledServicesReader] 读取, 进程内不可观测) 与进程内 [registry] 状态 (已绑定 / 可操作).
 * 服务类型为泛型参数, 所有 Android 触点均由外部注入, 因此本类不依赖 Android 并有单元测试;
 * [org.autojs.autojs.core.accessibility.AccessibilityTool] 负责装配真实依赖, 是 Android 侧的门面.
 *
 * @param strategies unattended ways of enabling / disabling the service, in preference order.
 * @param disableSelf asks the bound instance to disable itself; the fallback when no strategy works.
 * @param settingsUi opens the system accessibility settings for the user.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
class A11yServiceController<S : Any>(
    val serviceName: String,
    private val registry: A11yServiceRegistry<S>,
    private val enabledServicesReader: () -> String?,
    private val strategies: List<ServiceStartStrategy>,
    private val disableSelf: () -> Boolean,
    private val settingsUi: () -> Unit,
    private val startTimeoutMillis: Long = DEFAULT_START_TIMEOUT_MILLIS,
    private val stopTimeoutMillis: Long = DEFAULT_STOP_TIMEOUT_MILLIS,
    private val log: (String) -> Unit = {},
) {

    /** Why a requested level could not be reached. zh-CN: 请求的状态未能达到的原因. */
    enum class FailureReason {
        /** Not enabled in system settings. zh-CN: 系统设置中未启用. */
        NOT_ENABLED,

        /** Enabled in system settings, but the system has not bound an instance in this process. zh-CN: 系统设置中已启用, 但系统尚未在本进程绑定实例. */
        ENABLED_BUT_UNBOUND,

        /** Bound, but no event has been delivered yet. zh-CN: 已绑定, 但尚未收到任何事件. */
        BOUND_BUT_NOT_OPERATIONAL,
    }

    sealed class EnsureResult {

        abstract val isSatisfied: Boolean

        /** [via] names the strategy that did it, [SETTINGS_UI], or null when nothing had to be done. zh-CN: [via] 为起作用的策略名, [SETTINGS_UI], 或 null (无需操作). */
        data class Satisfied(val via: String?) : EnsureResult() {
            override val isSatisfied get() = true
        }

        data class Failed(val reason: FailureReason) : EnsureResult() {
            override val isSatisfied get() = false
        }

    }

    // Strategy writes are serialized; the waits below deliberately happen outside the lock so that
    // a script waiting for the user never blocks a stop request from elsewhere.
    // zh-CN: 策略的写入串行化; 下方的等待有意放在锁外, 使等待用户操作的脚本不会阻塞其它地方的停止请求.
    private val writeLock = Any()

    /* Predicates. zh-CN: 谓词. */

    /** Every enabled service listed in system settings. zh-CN: 系统设置中列出的全部已启用服务. */
    fun enabledServices(): List<String> = EnabledA11yServices.parse(enabledServicesReader())

    /** Enabled in system settings. zh-CN: 系统设置中已启用. */
    fun hasService(): Boolean = EnabledA11yServices.contains(enabledServicesReader(), serviceName)

    /** An instance is bound in this process. zh-CN: 进程内存在绑定实例. */
    fun hasInstance(): Boolean = registry.isBound

    fun isRunning(): Boolean = hasService() && hasInstance()

    /** Running and at least one event has been delivered. zh-CN: 运行中且至少收到过一个事件. */
    fun isOperational(): Boolean = isRunning() && registry.isOperational

    /** Enabled in system settings but not bound, typically after the process was killed. zh-CN: 系统设置中已启用但未绑定, 常见于进程被杀之后. */
    fun isMalfunctioning(): Boolean = hasService() && !hasInstance()

    fun isSatisfied(level: State): Boolean = when (level) {
        State.UNBOUND -> true
        State.CONNECTED -> isRunning()
        State.OPERATIONAL -> isOperational()
    }

    fun failureReason(): FailureReason = when {
        !hasService() -> FailureReason.NOT_ENABLED
        !registry.isBound -> FailureReason.ENABLED_BUT_UNBOUND
        else -> FailureReason.BOUND_BUT_NOT_OPERATIONAL
    }

    /* Control. zh-CN: 控制. */

    /**
     * Enables the service without waiting for it to bind. A running service is left alone (the
     * strategies re-enable, which would needlessly restart it). Returns whether the service is now
     * enabled in system settings; when it is not and [launchSettingsIfNeeded] is set, the settings
     * page has been opened for the user.
     *
     * zh-CN: 启用服务, 不等待绑定. 已运行的服务不做处理 (策略会重新启用, 相当于无谓的重启).
     * 返回服务现在是否已在系统设置中启用; 未启用且 [launchSettingsIfNeeded] 为 true 时, 已为用户打开设置页.
     */
    fun start(launchSettingsIfNeeded: Boolean): Boolean {
        if (isRunning()) {
            log("Accessibility service is running")
            return true
        }
        val via = enableWithStrategies()
        if (via != null) {
            log("Accessibility service enabled via $via")
            return true
        }
        if (launchSettingsIfNeeded) settingsUi()
        return false
    }

    /**
     * Disables the service through the strategies, falling back to asking the instance to disable
     * itself. Returns whether the service is no longer enabled in system settings; the system unbinds
     * asynchronously (see [waitForStopped]).
     *
     * zh-CN: 通过策略禁用服务, 兜底为让实例自行禁用. 返回服务是否已不在系统设置中启用; 系统的解绑是异步的 (见 [waitForStopped]).
     */
    fun stop(launchSettingsIfNeeded: Boolean): Boolean {
        val via = disableWithStrategies()
        if (via != null) {
            log("Accessibility service disabled via $via")
            return true
        }
        if (disableSelf() && !hasService()) {
            log("Accessibility service disabled via disableSelf")
            return true
        }
        if (launchSettingsIfNeeded) settingsUi()
        return false
    }

    /**
     * [stop], wait for the system to unbind the instance, then [start]. Re-enabling before the
     * unbind is coalesced into "still enabled" by some systems and nothing restarts.
     * zh-CN: [stop], 等待系统解绑实例, 再 [start]. 在解绑前重新启用会被部分系统合并为 "仍然启用", 实际不重启.
     */
    fun restart(launchSettingsIfNeeded: Boolean): Boolean {
        val stopped = stop(false)
        if (stopped && !waitForStopped()) {
            log("Accessibility service was not unbound within $stopTimeoutMillis ms after being disabled")
        }
        return start(launchSettingsIfNeeded) && stopped
    }

    /**
     * Makes sure the service has reached [level], enabling it if necessary: first through the
     * strategies (each given [startTimeoutMillis] to bind), then, if [launchSettingsIfNeeded], by
     * opening the settings page for the user. Once the service is bound, [level] is awaited for up
     * to [timeoutMillis] (negative waits forever). A [forceRestart] first stops a service that is
     * enabled in system settings and waits for the unbind.
     *
     * zh-CN: 确保服务达到 [level], 必要时启用它: 先经策略 (每个策略给 [startTimeoutMillis] 完成绑定),
     * 再在 [launchSettingsIfNeeded] 时为用户打开设置页. 服务绑定后最多等待 [timeoutMillis] 达到 [level] (负数为无限等待).
     * [forceRestart] 会先停止已在系统设置中启用的服务并等待解绑.
     */
    fun ensure(
        level: State,
        timeoutMillis: Long = startTimeoutMillis,
        launchSettingsIfNeeded: Boolean = true,
        forceRestart: Boolean = false,
    ): EnsureResult {
        if (forceRestart && hasService()) {
            if (stop(launchSettingsIfNeeded) && !waitForStopped()) {
                log("Accessibility service was not unbound within $stopTimeoutMillis ms before the forced restart")
            }
        } else {
            if (isSatisfied(level)) return EnsureResult.Satisfied(null)
            // Already bound: only the level (the first event) is outstanding, and re-enabling the
            // service through a strategy would restart it instead.
            // zh-CN: 已绑定: 只差状态 (首个事件) 尚未达到, 此时经策略重新启用反而会重启服务.
            if (isRunning()) return awaitLevel(level, timeoutMillis, null)
        }
        val bindTimeout = if (timeoutMillis in 0 until startTimeoutMillis) timeoutMillis else startTimeoutMillis
        val via = strategies.firstOrNull { strategy ->
            strategy.isAvailable() && synchronized(writeLock) { strategy.enable() } && registry.await(State.CONNECTED, bindTimeout)
        }?.name
        if (via != null) {
            log("Accessibility service enabled via $via")
            return awaitLevel(level, timeoutMillis, via)
        }
        if (!launchSettingsIfNeeded) {
            return EnsureResult.Failed(failureReason())
        }
        settingsUi()
        return awaitLevel(level, timeoutMillis, SETTINGS_UI)
    }

    /**
     * The automatic-start policy applied when an app screen is created: only a malfunctioning
     * service is re-enabled, and only after the system was given [startTimeoutMillis] to bind it by
     * itself (right after a process start it may still be about to). A service that is not enabled
     * is left alone: the user may have stopped it deliberately.
     *
     * zh-CN: 应用页面创建时的自动启动策略: 只重新启用故障状态的服务, 且先给系统 [startTimeoutMillis] 自行完成绑定
     * (进程刚启动时系统可能正要绑定). 未启用的服务不做处理: 用户可能是有意关闭的.
     */
    fun restoreIfMalfunctioning(): Boolean {
        if (!isMalfunctioning()) return false
        if (registry.await(State.CONNECTED, startTimeoutMillis)) return false
        if (!isMalfunctioning()) return false
        log("Accessibility service is enabled but not bound, re-enabling it")
        return start(false)
    }

    /**
     * Waits up to [timeoutMillis] for the service to be bound again without involving the user
     * (roadmap P5.5, Flow `recoverService`): a malfunctioning service (enabled but unbound) is
     * re-enabled through the strategies, as [restoreIfMalfunctioning] would; a service that is
     * not enabled is only waited for, in case the user or the system brings it back (D16: a
     * deliberate stop is respected). Never opens the settings page.
     * zh-CN: 至多等待 [timeoutMillis] 毫秒让服务重新绑定, 不牵涉用户 (Roadmap P5.5, Flow 的 `recoverService`): 故障状态的服务
     * (已启用但未绑定) 经策略重新启用, 同 [restoreIfMalfunctioning]; 未启用的服务只是等待, 以防用户或系统把它带回来 (D16: 尊重有意的
     * 关闭). 从不打开设置页.
     */
    fun awaitAvailable(timeoutMillis: Long): Boolean {
        if (isSatisfied(State.CONNECTED)) return true
        if (isMalfunctioning() && ensure(State.CONNECTED, timeoutMillis, launchSettingsIfNeeded = false).isSatisfied) return true
        return registry.await(State.CONNECTED, timeoutMillis)
    }

    fun waitForStarted(timeoutMillis: Long = startTimeoutMillis): Boolean = registry.await(State.CONNECTED, timeoutMillis)

    fun waitForOperational(timeoutMillis: Long = startTimeoutMillis): Boolean = registry.await(State.OPERATIONAL, timeoutMillis)

    fun waitForStopped(timeoutMillis: Long = stopTimeoutMillis): Boolean = registry.awaitUnbound(timeoutMillis)

    private fun enableWithStrategies(): String? = strategies.firstOrNull { strategy ->
        strategy.isAvailable() && synchronized(writeLock) { strategy.enable() }
    }?.name

    private fun disableWithStrategies(): String? = strategies.firstOrNull { strategy ->
        strategy.isAvailable() && synchronized(writeLock) { strategy.disable() }
    }?.name

    private fun awaitLevel(level: State, timeoutMillis: Long, via: String?): EnsureResult = when {
        registry.await(level, timeoutMillis) -> EnsureResult.Satisfied(via)
        else -> EnsureResult.Failed(failureReason())
    }

    companion object {

        const val DEFAULT_START_TIMEOUT_MILLIS = 2000L

        const val DEFAULT_STOP_TIMEOUT_MILLIS = 2000L

        /** The [EnsureResult.Satisfied.via] value when the user enabled the service in system settings. zh-CN: 用户在系统设置中启用服务时的 [EnsureResult.Satisfied.via] 值. */
        const val SETTINGS_UI = "settings"

    }

}
