package org.autojs.autojs.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.util.AttributeSet
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.network.UpdateChecker
import org.autojs.autojs.network.UpdateChecker.PromptMode
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on May 31, 2022.
 */
class CheckForUpdatesPreference : MaterialPreference, OnSharedPreferenceChangeListener {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    init {
        Pref.registerOnSharedPreferenceChangeListener(this)
        summaryProvider = SummaryProvider<CheckForUpdatesPreference> {
            Pref.lastUpdatesCheckedTimeString?.let { prefContext.getString(R.string.text_last_updates_checked_time, it) }
        }
    }

    override fun onClick() {
        UpdateChecker.Builder(prefContext)
            .setPromptMode(PromptMode.DIALOG)
            .build().checkNow()
        super.onClick()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) = notifyChanged()

}