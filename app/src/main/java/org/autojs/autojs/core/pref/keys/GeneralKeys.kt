package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.RegisteredName
import org.autojs.autojs.core.pref.SettingCategory.Companion.GENERAL
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingEffect.APPLY
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs6.R

/**
 * Settings > Configuration (docs/settings/ROADMAP.md P2.1, appendix A.1 `general`): documentation source, restart
 * strategy with the backend and delay of scheduled restarts, launcher icon and the foreground service. The launcher
 * icon and the foreground service take effect through their existing entries (`APPLY`, T12; the foreground service
 * per appendix C Q13). The three automatic backup switches and the period option of P9 (D22 / T20) join this domain
 * when P9.2 / P9.3 add their resources.
 *
 * zh-CN: 设置 > 配置 (ROADMAP P2.1, 附录 A.1 `general`): 文档来源, 重启策略及计划重启的后端与延迟, 启动图标, 前台服务. 启动图标与
 * 前台服务经现有入口生效 (`APPLY`, T12; 前台服务见附录 C Q13). P9 的三个自动备份开关与周期选项 (D22 / T20) 在 P9.2 / P9.3 加入
 * 资源时并入本领域.
 */
object GeneralKeys : SettingsDomain {

    private val DOCUMENTATION_SOURCE_VALUES = intArrayOf(
        R.string.key_documentation_source_local,
        R.string.key_documentation_source_online,
    )

    private val RESTART_STRATEGY_VALUES = intArrayOf(
        R.string.key_restart_strategy_quick,
        R.string.key_restart_strategy_scheduled,
    )

    private val SCHEDULED_RESTART_BACKEND_VALUES = intArrayOf(
        R.string.key_scheduled_restart_backend_alarm_manager,
        R.string.key_scheduled_restart_backend_work_manager,
    )

    private val LAUNCHER_ICON_VALUES = intArrayOf(
        R.string.key_launcher_icon_adaptive,
        R.string.key_launcher_icon_transparent_background,
    )

    /** `DocsUtils.currentSource` reads the stored key: missing or local is offline, anything else online. */
    @JvmField
    val DOCUMENTATION_SOURCE = SettingKey.Enum(
        R.string.key_documentation_source, GENERAL, keyChoices(*DOCUMENTATION_SOURCE_VALUES),
        keyResource(R.string.default_key_documentation_source, "default_key_documentation_source"),
    )

    @JvmField
    val RESTART_STRATEGY = SettingKey.Enum(
        R.string.key_restart_strategy, GENERAL, keyChoices(*RESTART_STRATEGY_VALUES),
        keyResource(R.string.default_key_restart_strategy, "default_key_restart_strategy"),
    )

    @JvmField
    val SCHEDULED_RESTART_BACKEND = SettingKey.Enum(
        R.string.key_scheduled_restart_backend, GENERAL, keyChoices(*SCHEDULED_RESTART_BACKEND_VALUES),
        keyResource(R.string.default_key_scheduled_restart_backend, "default_key_scheduled_restart_backend"),
    )

    /** Delay of a scheduled restart in milliseconds. */
    @JvmField
    val SCHEDULED_RESTART_DELAY = SettingKey.Int(
        R.string.key_scheduled_restart_delay, GENERAL,
        intResource(R.integer.scheduled_restart_start_delay_default_value, "scheduled_restart_start_delay_default_value"),
    )

    /** Written by `LauncherIconPreference` to match the enabled launcher component. */
    @JvmField
    val LAUNCHER_ICON = SettingKey.Enum(
        R.string.key_launcher_icon, GENERAL, keyChoices(*LAUNCHER_ICON_VALUES),
        keyResource(R.string.default_key_launcher_icon, "default_key_launcher_icon"), APPLY,
    )

    /** The drawer switch of the foreground service; the script API `settings` and inrt read and write it too. */
    @JvmField
    val FOREGROUND_SERVICE = SettingKey.Bool(R.string.key_foreground_service, GENERAL, SettingDefault.of(false), APPLY)

    override val keys: List<PrefKey<*>> = listOf(
        DOCUMENTATION_SOURCE, RESTART_STRATEGY, SCHEDULED_RESTART_BACKEND, SCHEDULED_RESTART_DELAY, LAUNCHER_ICON, FOREGROUND_SERVICE,
    )

    override val names: List<RegisteredName> = valueConstants(
        *DOCUMENTATION_SOURCE_VALUES, *RESTART_STRATEGY_VALUES, *SCHEDULED_RESTART_BACKEND_VALUES, *LAUNCHER_ICON_VALUES,
    ) + excludedNames(
        // Entries of the page that hold no value, and the restart hand-over value (appendix A.2, Q13).
        R.string.key_launcher_shortcuts,
        R.string.key_setup_wizards,
        R.string.key_scripts_after_app_restart,
    )

    /**
     * True when the restart strategy is "quick" (`Pref.isQuickRestartEnabled`); a stored key out of the candidates
     * is not "quick" (R2).
     * zh-CN: 重启策略为 "快速" 时为 true; 候选项之外的存储值不算 "快速" (R2).
     */
    @JvmStatic
    val isQuickRestartEnabled: Boolean
        get() = RESTART_STRATEGY.storedKeyOrDefault() == keyOf(R.string.key_restart_strategy_quick)

    /** True when scheduled restarts use WorkManager (`Pref.isScheduledRestartPreferredWorkManager`). */
    @JvmStatic
    val isScheduledRestartPreferredWorkManager: Boolean
        get() = SCHEDULED_RESTART_BACKEND.storedKeyOrDefault() == keyOf(R.string.key_scheduled_restart_backend_work_manager)
}
