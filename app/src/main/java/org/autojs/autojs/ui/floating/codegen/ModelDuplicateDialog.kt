package org.autojs.autojs.ui.floating.codegen

import android.content.Context
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.annotation.MainThread
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.StackingBehavior
import org.autojs.autojs.core.accessibility.codegen.store.ModelVersionInfo
import org.autojs.autojs.ui.floating.layoutinspector.InspectorDialogBuilder
import org.autojs.autojs.ui.floating.layoutinspector.InspectorTheme
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs6.R
import kotlin.math.roundToInt

/** Only reports the user's choice; the owner retains and disposes the validated import/download. */
@MainThread
object ModelDuplicateDialog {
    enum class Choice { REPLACE, KEEP_BOTH, CANCEL }

    fun showSameModel(
        context: Context,
        oldInfo: ModelVersionInfo,
        newInfo: ModelVersionInfo,
        floating: Boolean = false,
        onChoice: (Choice) -> Unit,
    ): MaterialDialog {
        val builder = modelDialogBuilder(context, floating)
        var answered = false
        fun answer(choice: Choice) {
            if (answered) return
            answered = true
            onChoice(choice)
        }
        fun choose(dialog: MaterialDialog, choice: Choice) {
            // Settle the decision before dismissal can deliver its fallback CANCEL callback.
            if (answered) return
            answered = true
            try {
                dialog.dismiss()
            } finally {
                onChoice(choice)
            }
        }
        return builder
            .title(R.string.text_codegen_model_same_name)
            .customView(ScrollView(builder.context).apply {
                val padding = (12 * resources.displayMetrics.density).roundToInt()
                setPadding(padding, 0, padding, padding)
                clipToPadding = false
                addView(ModelComparisonView(builder.context, oldInfo, newInfo), ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            }, false)
            .autoDismiss(false)
            .positiveText(R.string.text_codegen_model_replace)
            .neutralText(R.string.text_codegen_model_keep_both)
            .negativeText(R.string.dialog_button_cancel)
            .onPositive { dialog, _ -> choose(dialog, Choice.REPLACE) }
            .onNeutral { dialog, _ -> choose(dialog, Choice.KEEP_BOTH) }
            .onNegative { dialog, _ -> choose(dialog, Choice.CANCEL) }
            .dismissListener { answer(Choice.CANCEL) }
            .showAdaptive()
    }

    fun showIdentical(
        context: Context,
        floating: Boolean = false,
        onClosed: () -> Unit,
    ): MaterialDialog {
        var closed = false
        return modelDialogBuilder(context, floating)
            .content(R.string.text_codegen_model_identical)
            .positiveText(R.string.text_codegen_model_got_it)
            .dismissListener {
                if (!closed) {
                    closed = true
                    onClosed()
                }
            }
            .showAdaptive()
    }
}

/** Keep an Activity context for its ordinary window; only the inspector needs an overlay context. */
internal fun modelDialogBuilder(context: Context, floating: Boolean): MaterialDialog.Builder {
    val builder = if (floating) InspectorDialogBuilder(context) else MaterialDialog.Builder(context)
    val colors = InspectorTheme(builder.context)
    return builder.backgroundColor(colors.background)
        .titleColor(colors.text)
        .contentColor(colors.text)
        .positiveColor(colors.accent)
        .neutralColor(colors.accent)
        .negativeColor(colors.accent)
        .widgetColor(colors.control)
        .choiceWidgetColor(colors.controls)
        .stackingBehavior(StackingBehavior.ADAPTIVE)
        .cancelable(true)
        .canceledOnTouchOutside(false)
}
