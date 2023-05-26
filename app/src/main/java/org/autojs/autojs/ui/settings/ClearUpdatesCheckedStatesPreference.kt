package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import org.autojs.autojs.pref.Pref
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on May 8, 2023.
 */
class ClearUpdatesCheckedStatesPreference : MaterialPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onClick() {
        Pref.clearUpdatesCheckedStates()
        ViewUtils.showToast(context, R.string.text_updates_checked_states_cleared)
        super.onClick()
    }

}
