package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import org.autojs.autojs.network.UpdateChecker
import org.autojs.autojs.network.UpdateChecker.PromptMode
import org.autojs.autojs.theme.preference.MaterialPreference

/**
 * Created by SuperMonster003 on Apr 21, 2025.
 */
class CheckForUpdatesWithLocalVersionIgnoredPreference : MaterialPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onClick() {
        UpdateChecker.Builder(context)
            .setPromptMode(PromptMode.DIALOG)
            .build()
            .checkNow(true)
        super.onClick()
    }

}
