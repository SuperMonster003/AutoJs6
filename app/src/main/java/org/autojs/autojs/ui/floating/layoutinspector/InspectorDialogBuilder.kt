package org.autojs.autojs.ui.floating.layoutinspector

import android.content.Context
import android.view.ContextThemeWrapper
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.util.ContextUtils.withHostConfiguration
import org.autojs.autojs6.R

/** All inspector dialogs share the host configuration and a surface-aware theme. */
open class InspectorDialogBuilder(context: Context) : MaterialDialog.Builder(
    ContextThemeWrapper(context.applicationContext.withHostConfiguration(), R.style.AppTheme),
) {
    init {
        // MaterialDialog also uses this context to inflate list rows and radio buttons.
        // zh-CN: MaterialDialog 也用此 context 加载列表行与单选按钮.
        val colors = InspectorTheme(this.context)
        backgroundColor(colors.background)
        titleColor(colors.text)
        contentColor(colors.text)
        itemsColor(colors.text)
        positiveColor(colors.accent)
        negativeColor(colors.accent)
        neutralColor(colors.accent)
        widgetColor(colors.control)
        choiceWidgetColor(colors.controls)
    }
}
