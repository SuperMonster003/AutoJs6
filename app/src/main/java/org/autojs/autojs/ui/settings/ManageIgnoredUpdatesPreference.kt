package org.autojs.autojs.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import androidx.preference.Preference.SummaryProvider
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.util.UpdateUtils
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on Sep 25, 2022.
 */
class ManageIgnoredUpdatesPreference : MaterialPreference, SharedPreferences.OnSharedPreferenceChangeListener {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    init {
        Pref.registerOnSharedPreferenceChangeListener(this)
        summaryProvider = SummaryProvider<ManageIgnoredUpdatesPreference> {
            Pref.getLinkedHashSet(R.string.key_ignored_updates).let {
                prefContext.resources.getQuantityString(R.plurals.text_items_total_sum_with_colon, it.size, it.size)
            }
        }
    }

    override fun onClick() {
        UpdateUtils.manageIgnoredUpdates(prefContext)
        super.onClick()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) = notifyChanged()

}
