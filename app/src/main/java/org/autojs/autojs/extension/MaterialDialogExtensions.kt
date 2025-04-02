package org.autojs.autojs.extension

import android.view.View
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
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

    fun MaterialDialog.makeTextCopyable(textViewGetter: (dialog: MaterialDialog) -> TextView?) {
        makeTextCopyable(textViewGetter(this))
    }

    fun MaterialDialog.makeTextCopyable(textView: TextView?, textValue: String? = textView?.text?.toString()) {
        if (textValue != null) {
            val context = this.context
            textView?.setOnClickListener {
                ClipboardUtils.setClip(context, textValue)
                ViewUtils.showSnack(this.view, "${context.getString(R.string.text_already_copied_to_clip)}: $textValue")
            }
        }
    }

    fun TextView.setCopyableTextIfAbsent(dialog: MaterialDialog, f: () -> String?) {
        val textView = this
        val textValue = f.invoke()
        if (textView.text == context.getString(R.string.ellipsis_six)) {
            textView.text = textValue.takeUnless { it.isNullOrBlank() } ?: context.getString(R.string.text_unknown)
        }
        dialog.makeTextCopyable(textView, textValue)
    }

    fun TextView.setCopyableText(dialog: MaterialDialog, f: () -> String?) {
        val textView = this
        val textValue = f.invoke()
        textView.text = textValue.takeUnless { it.isNullOrBlank() } ?: context.getString(R.string.text_unknown)
        dialog.makeTextCopyable(textView, textValue)
    }

}
