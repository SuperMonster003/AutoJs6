package org.autojs.autojs.util

import androidx.preference.Preference
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.preference.PreferenceButtonColorMutable
import org.autojs.autojs.theme.preference.PreferenceSummaryColorMutable
import org.autojs.autojs.theme.preference.PreferenceTitleColorMutable
import org.autojs.autojs6.R

object PreferenceUtils {

    fun Preference.setTitleColorVisualEnabledWith(checker: () -> Boolean) {
        require(this is PreferenceTitleColorMutable) { "Preference must implement PreferenceTitleColorMutable" }
        val titleTextColor = ColorUtils.getColorFromAttr(context, android.R.attr.textColorPrimary)
        titleColor = when (checker()) {
            true -> null
            else -> ColorUtils.applyAlpha(titleTextColor, if (ViewUtils.isNightModeYes(context)) 0.7 else 0.4)
        }
    }

    fun Preference.setSummaryColorVisualEnabledWith(checker: () -> Boolean) {
        require(this is PreferenceSummaryColorMutable) { "Preference must implement PreferenceSummaryColorMutable" }
        val summarayTextColor = ColorUtils.getColorFromAttr(context, android.R.attr.textColorSecondary)
        summaryColor = when (checker()) {
            true -> null
            else -> ColorUtils.applyAlpha(summarayTextColor, if (ViewUtils.isNightModeYes(context)) 0.9 else 0.8)
        }
    }

    fun Preference.setButtonColorVisualEnabledWith(checker: () -> Boolean) {
        require(this is PreferenceButtonColorMutable) { "Preference must implement PreferenceButtonColorMutable" }
        buttonColor = when (checker()) {
            true -> ThemeColorManager.colorPrimary
            else -> context.getColor(if (ViewUtils.isNightModeYes(context)) R.color.md_gray_700 else R.color.md_gray_200)
        }
    }
}
