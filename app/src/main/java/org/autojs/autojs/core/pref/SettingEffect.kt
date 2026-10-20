package org.autojs.autojs.core.pref

/**
 * When a changed setting takes effect (docs/settings/ROADMAP.md D10, T12): [NONE] the next time the value is read,
 * [APPLY] after the bound entry of `SettingsEffects` (P7.4) runs, [RESTART] after the app restarts.
 *
 * zh-CN: 设置变化后何时生效 (ROADMAP D10, T12): [NONE] 下次读取时, [APPLY] 经 `SettingsEffects` (P7.4) 绑定的入口立即应用,
 * [RESTART] 重启后.
 */
enum class SettingEffect {
    NONE,
    APPLY,
    RESTART,
}
