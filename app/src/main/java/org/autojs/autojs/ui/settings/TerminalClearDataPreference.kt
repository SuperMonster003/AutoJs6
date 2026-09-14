package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.terminal.TerminalPaths
import org.autojs.autojs.core.terminal.TerminalSessionManager
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Deletes `<filesDir>/terminal` (home, installed packages, npm / corepack caches) after confirmation;
 * running sessions are closed first because their shell lives inside that tree.
 * zh-CN: 确认后删除 `<filesDir>/terminal` (主目录, 已安装的包, npm / corepack 缓存); 先关闭运行中的会话, 因为其 shell 就在该目录树内.
 */
class TerminalClearDataPreference : MaterialPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onClick() {
        val paths = TerminalPaths.of(context)
        MaterialDialog.Builder(context)
            .title(R.string.text_terminal_clear_data)
            .content(context.getString(R.string.text_terminal_confirm_clear_data, paths.root.path))
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { _, _ ->
                TerminalSessionManager.closeAll()
                val cleared = paths.clearAll()
                ViewUtils.showToast(context, if (cleared) R.string.text_terminal_data_cleared else R.string.text_failed)
            }
            .widgetThemeColor()
            .showAdaptive()
    }

}
