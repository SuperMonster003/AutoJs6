package org.autojs.autojs.ui.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.preference.MaterialListPreference
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType

class EditorEnginePreference : MaterialListPreference {

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onItemSelectionRequested(dialog: MaterialDialog, itemKey: String): Boolean {
        val selectedType = EditorEngineType.fromPreferenceValue(itemKey)
        val result = EditorEnginePreferences.selectByUser(prefContext, selectedType)
        notifyChanged()
        if (result.needsAcePlugin) {
            Handler(Looper.getMainLooper()).post {
                AceEditorPluginUi.showRequiredDialog(prefContext)
            }
        }
        // EditorEnginePreferences committed mode + effective engine atomically.
        return false
    }
}
