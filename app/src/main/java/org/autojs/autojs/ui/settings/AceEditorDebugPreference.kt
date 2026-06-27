package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.ui.edit.editor.ace.AceEditorDebugActivity

class AceEditorDebugPreference : MaterialPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onClick() {
        AceEditorDebugActivity.launch(prefContext)
    }
}
