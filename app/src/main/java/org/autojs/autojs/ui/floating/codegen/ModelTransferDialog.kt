package org.autojs.autojs.ui.floating.codegen

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import androidx.annotation.MainThread
import androidx.appcompat.widget.AppCompatTextView
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.accessibility.codegen.store.ModelVersionInfo
import org.autojs.autojs.core.accessibility.codegen.store.VersionComparison
import org.autojs.autojs.ui.floating.layoutinspector.InspectorTheme
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs6.R
import kotlin.math.roundToInt

/**
 * Download/update progress shared by settings and the floating inspector. The caller owns the
 * subscription and cancels it in [onCancel]. All calls, including progress events, use the main thread.
 * Closing a successful or failed transfer never calls the active subscription's cancellation hook.
 */
@MainThread
class ModelTransferDialog(
    context: Context,
    newInfo: ModelVersionInfo,
    oldInfo: ModelVersionInfo? = null,
    floating: Boolean = false,
    private val isUpdate: Boolean = oldInfo != null,
    onCancel: () -> Unit,
) {
    private val builder = modelDialogBuilder(context, floating)
    private val uiContext = builder.context
    private val colors = InspectorTheme(uiContext)
    private val formatter = ModelComparisonFormatter(uiContext)
    private val state = ModelTransferState()
    private var cancelCallback: (() -> Unit)? = onCancel
    private var deleteOldCallback: (() -> Unit)? = null

    private val progress = ProgressBar(uiContext, null, android.R.attr.progressBarStyleHorizontal).apply {
        isIndeterminate = newInfo.sizeBytes == null
        max = PROGRESS_STEPS
        progressTintList = ColorStateList.valueOf(colors.control)
        progressBackgroundTintList = ColorStateList.valueOf(colors.selectionBackground)
        indeterminateTintList = ColorStateList.valueOf(colors.control)
    }
    private val status = textView().apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val body = LinearLayout(uiContext).apply {
        orientation = LinearLayout.VERTICAL
        addView(progress, rowParams(bottom = 6))
        addView(status, rowParams(bottom = 16))
        if (oldInfo != null) {
            addView(ModelComparisonView(uiContext, oldInfo, newInfo), rowParams())
        } else {
            val rows = VersionComparison(formatter, formatter.locale.toLanguageTag()).build(ModelVersionInfo(), newInfo)
            rows.filter { it.field in SUMMARY_FIELDS }.forEach { row ->
                addView(textView().apply {
                    text = formatter.fieldLabel(row.field)
                    setTypeface(typeface, Typeface.BOLD)
                }, rowParams(bottom = 2))
                addView(textView().apply { text = row.newText }, rowParams(bottom = 12))
            }
        }
    }
    private val dialog: MaterialDialog = builder
        .title(if (isUpdate) R.string.text_codegen_model_updating else R.string.text_codegen_model_downloading)
        .customView(ScrollView(uiContext).apply {
            setPadding(dp(16), 0, dp(16), dp(8))
            clipToPadding = false
            addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }, false)
        .autoDismiss(false)
        .negativeText(R.string.dialog_button_cancel)
        .onNegative { _, _ -> dispose() }
        .onPositive { _, _ -> dispose() }
        .onNeutral { _, _ ->
            val delete = deleteOldCallback
            dispose()
            delete?.invoke()
        }
        .dismissListener { takeCancellation()?.invoke() }
        .build()

    init {
        updateProgress(0, newInfo.sizeBytes ?: 0)
    }

    fun show(): MaterialDialog {
        check(state.phase != ModelTransferState.Phase.CLOSED) { "Transfer dialog has been disposed" }
        return dialog.showAdaptive()
    }

    /** Late progress events after any terminal outcome are ignored. A nonpositive total is unknown. */
    fun updateProgress(bytes: Long, total: Long) {
        if (!state.acceptsProgress) return
        val downloaded = bytes.coerceAtLeast(0)
        progress.isIndeterminate = total <= 0
        if (total > 0) {
            progress.progress = ((downloaded.toDouble() / total) * PROGRESS_STEPS).roundToInt().coerceIn(0, PROGRESS_STEPS)
        }
        status.text = uiContext.getString(
            R.string.text_codegen_model_download_progress,
            formatter.size(downloaded),
            if (total > 0) formatter.size(total) else VersionComparison.MISSING,
        )
    }

    /** The comparison remains visible; deleting the old version is an explicit, one-shot action. */
    fun showSuccess(installedVersionName: String, onDeleteOld: (() -> Unit)? = null) {
        if (!state.succeed()) return
        cancelCallback = null
        deleteOldCallback = onDeleteOld
        dialog.setTitle(uiContext.getString(
            if (isUpdate) R.string.text_codegen_model_updated else R.string.text_codegen_model_downloaded,
            installedVersionName,
        ))
        progress.visibility = View.GONE
        status.visibility = View.GONE
        dialog.setActionButton(DialogAction.NEGATIVE, null)
        dialog.setActionButton(DialogAction.POSITIVE, R.string.text_done)
        dialog.setActionButton(DialogAction.NEUTRAL, onDeleteOld?.let { uiContext.getString(R.string.text_codegen_model_delete_old) })
    }

    /** [message] is already localized by the caller; the comparison remains available after failure. */
    fun showFailure(message: String) {
        if (!state.fail()) return
        cancelCallback = null
        deleteOldCallback = null
        dialog.setTitle(R.string.text_codegen_model_transfer_failed)
        progress.visibility = View.GONE
        status.text = message
        dialog.setActionButton(DialogAction.NEGATIVE, null)
        dialog.setActionButton(DialogAction.NEUTRAL, null)
        dialog.setActionButton(DialogAction.POSITIVE, R.string.text_close)
    }

    /** Dispose with the owner; if still active this also cleans up the download subscription once. */
    fun dispose() {
        val cancel = takeCancellation()
        try {
            dialog.dismiss()
        } finally {
            cancel?.invoke()
        }
    }

    private fun takeCancellation(): (() -> Unit)? {
        val cancel = if (state.close()) cancelCallback else null
        cancelCallback = null
        deleteOldCallback = null
        return cancel
    }

    private fun textView() = AppCompatTextView(uiContext).apply {
        textSize = 14f
        setTextColor(colors.text)
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    }

    private fun rowParams(bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(bottom) }

    private fun dp(value: Int): Int = (value * uiContext.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val PROGRESS_STEPS = 10_000
        val SUMMARY_FIELDS = setOf(VersionComparison.Field.NAME, VersionComparison.Field.VERSION, VersionComparison.Field.SOURCE, VersionComparison.Field.SIZE)
    }
}
