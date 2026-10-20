package org.autojs.autojs.core.console

import androidx.annotation.StringRes
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.keys.ConsoleKeys
import org.autojs.autojs.core.pref.keys.EditorKeys

/**
 * User display preferences belong to a console surface, independently of its log source. The keys are declared in
 * [ConsoleKeys] and, for the editor log panel, [EditorKeys] (docs/settings/ROADMAP.md P2.5).
 * zh-CN: 用户显示偏好属于控制台界面, 与日志来源无关. 键声明在 [ConsoleKeys] 以及 (编辑器日志面板) [EditorKeys] 中 (ROADMAP P2.5).
 */
enum class ConsoleTimeSettings(
    val visibleKey: SettingKey.Bool,
    val formatKey: SettingKey.Str,
) {
    EDITOR_PANEL(EditorKeys.EDITOR_LOG_PANEL_TIME_VISIBLE, EditorKeys.EDITOR_LOG_PANEL_TIME_FORMAT),
    LOG_ACTIVITY(ConsoleKeys.LOG_ACTIVITY_TIME_VISIBLE, ConsoleKeys.LOG_ACTIVITY_TIME_FORMAT),
    GLOBAL_CONSOLE_ELEMENT(ConsoleKeys.GLOBAL_CONSOLE_ELEMENT_TIME_VISIBLE, ConsoleKeys.GLOBAL_CONSOLE_ELEMENT_TIME_FORMAT),
    CONSOLE_ELEMENT(ConsoleKeys.CONSOLE_ELEMENT_TIME_VISIBLE, ConsoleKeys.CONSOLE_ELEMENT_TIME_FORMAT),
    FLOATING_WINDOW(ConsoleKeys.FLOATING_CONSOLE_TIME_VISIBLE, ConsoleKeys.FLOATING_CONSOLE_TIME_FORMAT);

    @get:StringRes
    val visibleKeyRes: Int
        get() = visibleKey.keyRes

    @get:StringRes
    val formatKeyRes: Int
        get() = formatKey.keyRes

    val defaultVisible: Boolean
        get() = visibleKey.defaultOrNull() ?: false

    var isTimeVisible: Boolean
        get() = visibleKey.get()
        set(value) { visibleKey.set(value) }

    var timeFormat: String
        get() = ConsoleLogFormat.normalizeTimePattern(formatKey.get())
        set(value) { formatKey.set(ConsoleLogFormat.normalizeTimePattern(value)) }
}
