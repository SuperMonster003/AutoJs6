package org.autojs.autojs.extension

import android.view.View
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

object MaterialDialogExtensions {

    fun MaterialDialog.makeSettingsLaunchable(viewGetter: (dialog: MaterialDialog) -> View?, packageName: String?) {
        viewGetter(this)?.setOnClickListener {
            packageName?.let { pkg ->
                IntentUtils.goToAppDetailSettings(this.context, pkg)
            } ?: ViewUtils.showSnack(this.view, R.string.error_app_not_installed)
        }
    }

    @JvmStatic
    fun MaterialDialog.makeTextCopyable(textViewGetter: (dialog: MaterialDialog) -> TextView?) {
        makeTextCopyable(textViewGetter(this))
    }

    @JvmStatic
    @JvmOverloads
    fun MaterialDialog.makeTextCopyable(textView: TextView?, textValue: String? = textView?.text?.toString()) {
        if (textValue != null) {
            val context = this.context
            textView?.setOnClickListener {
                ClipboardUtils.setClip(context, textValue)
                ViewUtils.showSnack(this.view, "${context.getString(R.string.text_already_copied_to_clip)}: $textValue")
            }
        }
    }

    fun MaterialDialog.setCopyableTextIfAbsent(textView: TextView, textValue: String?) {
        if (textView.text == context.getString(R.string.ellipsis_six)) {
            textView.text = textValue.takeUnless { it.isNullOrBlank() } ?: context.getString(R.string.text_unknown)
        }
        this.makeTextCopyable(textView, textValue)
    }

    fun MaterialDialog.setCopyableTextIfAbsent(textView: TextView, scope: CoroutineScope, f: () -> String?) {
        scope.launch(Dispatchers.IO) {
            val textValue = f.invoke()
            withContext(Dispatchers.Main) {
                setCopyableTextIfAbsent(textView, textValue)
            }
        }
    }

    fun MaterialDialog.setCopyableText(textView: TextView, textValue: String?) {
        textView.text = textValue.takeUnless { it.isNullOrBlank() } ?: context.getString(R.string.text_unknown)
        this.makeTextCopyable(textView, textValue)
    }

    @JvmStatic
    fun MaterialDialog.Builder.choiceWidgetThemeColor() = also {
        it.choiceWidgetColor(ThemeColorHelper.getThemeColorStateList(context))
    }

    @JvmStatic
    fun MaterialDialog.Builder.widgetThemeColor() = also {
        it.widgetColor(ThemeColorHelper.getThemeColorStateList(context))
    }

}
