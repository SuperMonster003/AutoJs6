package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.console.ConsoleLogFormat
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingCategory.Companion.CONSOLE
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs6.R

/**
 * Logs and console (docs/settings/ROADMAP.md P2.5, appendix A.1 `console`): the display settings of the log page
 * (time, input bar, coloring, text size, TypeScript console) and the time display of the console element, the global
 * console element and the floating console window (`ConsoleTimeSettings`; the editor log panel keys are in
 * [EditorKeys]). Time patterns are normalized when read and written (`ConsoleLogFormat.normalizeTimePattern`).
 *
 * zh-CN: 日志与控制台 (ROADMAP P2.5, 附录 A.1 `console`): 日志页的显示设置 (时间, 输入栏, 着色, 文字大小, TypeScript 控制台), 以及
 * 控制台控件, 全局控制台控件与悬浮控制台窗口的时间显示 (`ConsoleTimeSettings`; 编辑器日志面板的键在 [EditorKeys]). 时间格式在读写时
 * 规范化.
 */
object ConsoleKeys : SettingsDomain {

    @JvmField
    val LOG_ACTIVITY_TIME_VISIBLE = SettingKey.Bool(R.string.key_log_activity_time_visible, CONSOLE, SettingDefault.of(true))

    @JvmField
    val LOG_ACTIVITY_TIME_FORMAT = SettingKey.Str(R.string.key_log_activity_time_format, CONSOLE, SettingDefault.of(ConsoleLogFormat.DEFAULT_TIME_PATTERN))

    @JvmField
    val LOG_ACTIVITY_INPUT_BAR_VISIBLE = SettingKey.Bool(R.string.key_log_activity_input_bar_visible, CONSOLE, SettingDefault.of(false))

    @JvmField
    val LOG_ACTIVITY_LOG_COLORING = SettingKey.Bool(R.string.key_log_activity_log_coloring, CONSOLE, SettingDefault.of(true))

    /** Text size in px of the log pages; 0 keeps the size of the view. */
    @JvmField
    val LOG_ACTIVITY_TEXT_SIZE = SettingKey.Int(R.string.key_log_activity_text_size, CONSOLE, SettingDefault.of(0))

    @JvmField
    val LOG_ACTIVITY_TYPESCRIPT_CONSOLE_VISIBLE = SettingKey.Bool(R.string.key_log_activity_typescript_console_visible, CONSOLE, SettingDefault.of(false))

    @JvmField
    val GLOBAL_CONSOLE_ELEMENT_TIME_VISIBLE = SettingKey.Bool(R.string.key_global_console_element_time_visible, CONSOLE, SettingDefault.of(false))

    @JvmField
    val GLOBAL_CONSOLE_ELEMENT_TIME_FORMAT = SettingKey.Str(R.string.key_global_console_element_time_format, CONSOLE, SettingDefault.of(ConsoleLogFormat.DEFAULT_TIME_PATTERN))

    @JvmField
    val CONSOLE_ELEMENT_TIME_VISIBLE = SettingKey.Bool(R.string.key_console_element_time_visible, CONSOLE, SettingDefault.of(false))

    @JvmField
    val CONSOLE_ELEMENT_TIME_FORMAT = SettingKey.Str(R.string.key_console_element_time_format, CONSOLE, SettingDefault.of(ConsoleLogFormat.DEFAULT_TIME_PATTERN))

    @JvmField
    val FLOATING_CONSOLE_TIME_VISIBLE = SettingKey.Bool(R.string.key_floating_console_time_visible, CONSOLE, SettingDefault.of(false))

    @JvmField
    val FLOATING_CONSOLE_TIME_FORMAT = SettingKey.Str(R.string.key_floating_console_time_format, CONSOLE, SettingDefault.of(ConsoleLogFormat.DEFAULT_TIME_PATTERN))

    override val keys: List<PrefKey<*>> = listOf(
        LOG_ACTIVITY_TIME_VISIBLE, LOG_ACTIVITY_TIME_FORMAT, LOG_ACTIVITY_INPUT_BAR_VISIBLE, LOG_ACTIVITY_LOG_COLORING,
        LOG_ACTIVITY_TEXT_SIZE, LOG_ACTIVITY_TYPESCRIPT_CONSOLE_VISIBLE,
        GLOBAL_CONSOLE_ELEMENT_TIME_VISIBLE, GLOBAL_CONSOLE_ELEMENT_TIME_FORMAT,
        CONSOLE_ELEMENT_TIME_VISIBLE, CONSOLE_ELEMENT_TIME_FORMAT,
        FLOATING_CONSOLE_TIME_VISIBLE, FLOATING_CONSOLE_TIME_FORMAT,
    )
}
