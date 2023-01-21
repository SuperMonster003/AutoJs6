package org.autojs.autojs.ui.settings

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import androidx.core.content.res.TypedArrayUtils
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.pref.Pref
import org.autojs.autojs.theme.preference.MaterialListPreference
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

class NightModePreference : MaterialListPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes, Bundle().apply {
        if (!ViewUtils.AutoNightMode.isFunctional()) {
            putString(key(R.string.key_pref_bundle_default_item), context.getString(R.string.key_night_mode_always_off))
            putIntegerArrayList(key(R.string.key_pref_bundle_disabled_items), arrayListOf(R.string.key_night_mode_follow_system))
        }
    })

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(
        context, attrs, TypedArrayUtils.getAttr(
            context, android.R.attr.dialogPreferenceStyle,
            android.R.attr.dialogPreferenceStyle,
        )
    )

    constructor(context: Context) : this(context, null)

    override fun onChangeConfirmed(dialog: MaterialDialog) {
        super.onChangeConfirmed(dialog)

        when (dialog.items?.get(dialog.selectedIndex)?.toString()) {
            prefContext.getString(R.string.entry_night_mode_follow_system) -> {
                ViewUtils.setDefaultNightMode(ViewUtils.MODE.FOLLOW)
                Pref.putBoolean(R.string.key_auto_night_mode_enabled, true)
                Pref.putBoolean(R.string.key_night_mode_enabled, ViewUtils.isNightModeYes(prefContext))
            }
            prefContext.getString(R.string.entry_night_mode_always_on) -> {
                ViewUtils.setDefaultNightMode(ViewUtils.MODE.NIGHT)
                Pref.putBoolean(R.string.key_auto_night_mode_enabled, false)
                Pref.putBoolean(R.string.key_night_mode_enabled, true)
            }
            prefContext.getString(R.string.entry_night_mode_always_off) -> {
                ViewUtils.setDefaultNightMode(ViewUtils.MODE.DAY)
                Pref.putBoolean(R.string.key_auto_night_mode_enabled, false)
                Pref.putBoolean(R.string.key_night_mode_enabled, false)
            }
            else -> Unit
        }
    }

}
