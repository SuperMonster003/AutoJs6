package org.autojs.autojs.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.pref.Pref
import org.autojs.autojs.theme.preference.MaterialListPreference
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

class KeepScreenOnWhenInForegroundPreference : MaterialListPreference, SharedPreferences.OnSharedPreferenceChangeListener {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    init {
        Pref.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onChangeConfirmed(dialog: MaterialDialog) {
        super.onChangeConfirmed(dialog)

        when (dialog.items?.get(dialog.selectedIndex)?.toString()) {
            prefContext.getString(R.string.entry_keep_screen_on_when_in_foreground_disabled) -> {
                ViewUtils.setKeepScreenOnWhenInForegroundDisabled()
            }
            prefContext.getString(R.string.entry_keep_screen_on_when_in_foreground_all_pages) -> {
                ViewUtils.setKeepScreenOnWhenInForegroundAllPages()
            }
            prefContext.getString(R.string.entry_keep_screen_on_when_in_foreground_homepage_only) -> {
                ViewUtils.setKeepScreenOnWhenInForegroundHomepageOnly()
            }
            else -> Unit
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) = notifyChanged()

}
