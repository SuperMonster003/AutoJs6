package org.autojs.autojs.theme.app

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.ThemeColorCoordinator
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.ThemeColorSource
import org.autojs.autojs.theme.intelligent.IntelligentColorEngine
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs6.R

/**
 * The "Derived colors" setting (D2, roadmap P4.6): off keeps the accent and dark colors equal to the primary color;
 * auto derives them. A change applies to the current theme color right away.
 * zh-CN: "派生配色" 设置 (D2, Roadmap P4.6): 关闭时强调色与深色同主色; 自动时由主色推导. 修改后立即作用于当前主题色.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object DerivedColorsSetting {

    fun show(context: Context) {
        val labels = listOf(context.getString(R.string.text_derived_colors_off), context.getString(R.string.text_derived_colors_auto))
        MaterialDialog.Builder(context)
            .title(R.string.text_derived_colors)
            .content(R.string.text_derived_colors_description)
            .items(labels)
            .itemsCallbackSingleChoice(if (ThemeColorManager.isDerivationEnabled) 1 else 0) { _, _, which, _ ->
                setEnabled(which == 1)
                true
            }
            .choiceWidgetThemeColor()
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .show()
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled == ThemeColorManager.isDerivationEnabled) return
        ThemeColorManager.isDerivationEnabled = enabled
        // An active intelligent library keeps its group and its explicit accent / dark colors.
        // zh-CN: 激活中的智能库保持当前组及其显式的强调色 / 深色.
        if (!IntelligentColorEngine.reapply()) {
            ThemeColorCoordinator.apply(ThemeColorManager.colorPrimary, ThemeColorSource.RESTORE)
        }
    }

}
