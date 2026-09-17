package org.autojs.autojs.core.console

import androidx.annotation.StringRes
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs6.R

/** User display preferences belong to a console surface, independently of its log source. */
enum class ConsoleTimeSettings(
    @StringRes val visibleKeyRes: Int,
    @StringRes val formatKeyRes: Int,
    val defaultVisible: Boolean,
) {
    EDITOR_PANEL(R.string.key_editor_log_panel_time_visible, R.string.key_editor_log_panel_time_format, true),
    LOG_ACTIVITY(R.string.key_log_activity_time_visible, R.string.key_log_activity_time_format, true),
    GLOBAL_CONSOLE_ELEMENT(R.string.key_global_console_element_time_visible, R.string.key_global_console_element_time_format, false),
    CONSOLE_ELEMENT(R.string.key_console_element_time_visible, R.string.key_console_element_time_format, false),
    FLOATING_WINDOW(R.string.key_floating_console_time_visible, R.string.key_floating_console_time_format, false);

    var isTimeVisible: Boolean
        get() = Pref.getBoolean(visibleKeyRes, defaultVisible)
        set(value) { Pref.putBoolean(visibleKeyRes, value) }

    var timeFormat: String
        get() = ConsoleLogFormat.normalizeTimePattern(Pref.getString(formatKeyRes, ConsoleLogFormat.DEFAULT_TIME_PATTERN))
        set(value) { Pref.putString(formatKeyRes, ConsoleLogFormat.normalizeTimePattern(value)) }
}
