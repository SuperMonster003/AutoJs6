package org.autojs.autojs.ui.settings

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.ui.edit.keyboard.SymbolsSettingsActivity
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils

class EditorShortcutInputPreference : MaterialPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onClick() {
        Intent(prefContext, SymbolsSettingsActivity::class.java).startSafely(prefContext, true) {
            ViewUtils.showToast(prefContext, it.message, true)
            Unit
        }
    }
}
