package org.autojs.autojs.theme.preference

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog

interface LongClickablePreferenceLike {

    val prefTitle: CharSequence?

    val prefContext: Context

    val longClickPrompt: CharSequence?

    val longClickPromptMore: CharSequence?

    fun longClickPromptMoreDialogHandler(d: MaterialDialog?) {}

    fun longClickPromptDialogHandler(d: MaterialDialog?) {}

}