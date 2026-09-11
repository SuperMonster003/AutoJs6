package org.autojs.autojs.core.plugin.center

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.nativealign.NativePageSize
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs6.R

object PluginNativeAlignmentPrompt {
    fun runBeforeAction(context: Context, item: PluginCenterItem, download: Boolean, onContinue: () -> Unit) {
        val alignment = if (download && item.isUpdatable) item.updatableNativePageAlignment else item.nativePageAlignment
        if (!PluginNativeAlignmentPolicy.needsWarning(alignment, NativePageSize.current())) {
            onContinue()
            return
        }
        MaterialDialog.Builder(context)
            .title(R.string.text_prompt)
            .content(context.getString(R.string.prompt_plugin_native_page_alignment, item.title))
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_continue)
            .onPositive { _, _ -> onContinue() }
            .showAdaptive()
    }
}
