package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.theme.preference.MaterialListPreference
import org.autojs.autojs.ui.doc.DocumentationActivity_
import org.autojs.autojs6.R

class DocumentationLauncherIconPreference : MaterialListPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onChangeConfirmed(dialog: MaterialDialog) {
        super.onChangeConfirmed(dialog)

        when (dialog.items?.get(dialog.selectedIndex)?.toString()) {
            prefContext.getString(R.string.entry_documentation_launcher_icon_show) -> {
                AppUtils.showLauncherIcon(prefContext, DocumentationActivity_::class.java)
            }
            prefContext.getString(R.string.entry_documentation_launcher_icon_hide) -> {
                AppUtils.hideLauncherIcon(prefContext, DocumentationActivity_::class.java)
            }
            else -> Unit
        }
    }


}