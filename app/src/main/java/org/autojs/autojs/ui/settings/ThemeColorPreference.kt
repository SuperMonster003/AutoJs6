package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference.SummaryProvider
import org.autojs.autojs.theme.app.ColorSelectActivity
import org.autojs.autojs.theme.preference.MaterialPreference

class ThemeColorPreference : MaterialPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    init {
        summaryProvider = SummaryProvider<ThemeColorPreference> { ColorSelectActivity.getColorString(prefContext) }
        ColorSelectActivity.onFinish = { notifyChanged() }
    }

    override fun onClick() {
        super.onClick()
        ColorSelectActivity.startActivity(prefContext)
    }

}
