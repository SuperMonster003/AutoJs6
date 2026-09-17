package org.autojs.autojs.ui.log

import android.content.Context
import androidx.annotation.StringRes
import org.autojs.autojs.core.console.ConsoleSettingsDialog
import org.autojs.autojs.core.console.ConsoleTimeSettings
import org.autojs.autojs.core.console.ConsoleView
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs6.R
import kotlin.math.roundToInt

/**
 * Persisted display settings of the embedded log pages (the log activity and the editor log panel).
 * Time display and input bar preferences are stored per page; coloring and text size remain shared.
 *
 * zh-CN: 内嵌日志页 (日志页与编辑器日志面板) 的持久化显示设置. 时间显示与输入栏按页面分别记忆, 着色与文字大小共享.
 */
class LogPageConsoleSettings(
    private val view: ConsoleView,
    @StringRes private val inputBarKeyRes: Int,
    private val timeSettings: ConsoleTimeSettings = ConsoleTimeSettings.LOG_ACTIVITY,
) : ConsoleSettingsDialog.Model {

    override var isTimeVisible: Boolean
        get() = timeSettings.isTimeVisible
        set(value) {
            timeSettings.isTimeVisible = value
            view.isTimeVisible = value
        }

    override var timeFormat: String
        get() = timeSettings.timeFormat
        set(value) {
            timeSettings.timeFormat = value
            view.timeFormat = value
        }

    override var isColorful: Boolean
        get() = Pref.getBoolean(R.string.key_log_activity_log_coloring, DEFAULT_COLORFUL)
        set(value) {
            Pref.putBoolean(R.string.key_log_activity_log_coloring, value)
            view.isColorful = value
        }

    override var textSize: Float
        get() = persistedTextSize ?: view.textSize
        set(value) {
            Pref.putInt(R.string.key_log_activity_text_size, value.roundToInt())
            view.textSize = value
        }

    override val hasWindowOptions = false

    override var isAvoidStatusBar = true

    override var titleBackgroundAlpha = 1.0

    override var contentBackgroundAlpha = 1.0

    var isInputBarVisible: Boolean
        get() = Pref.getBoolean(inputBarKeyRes, DEFAULT_INPUT_BAR_VISIBLE)
        set(value) {
            Pref.putBoolean(inputBarKeyRes, value)
            view.isInputBarVisible = value
        }

    private val persistedTextSize: Float?
        get() = Pref.getInt(R.string.key_log_activity_text_size, 0).takeIf { it > 0 }?.toFloat()

    /** Applies the persisted values to the bound view. zh-CN: 把持久化的值应用到绑定的视图. */
    fun apply() {
        view.isTimeVisible = isTimeVisible
        view.timeFormat = timeFormat
        view.isColorful = isColorful
        persistedTextSize?.let { view.textSize = it }
        view.isInputBarVisible = isInputBarVisible
    }

    fun showDialog(context: Context) = ConsoleSettingsDialog(context, this).show()

    companion object {
        private const val DEFAULT_COLORFUL = true
        private const val DEFAULT_INPUT_BAR_VISIBLE = false
    }

}
