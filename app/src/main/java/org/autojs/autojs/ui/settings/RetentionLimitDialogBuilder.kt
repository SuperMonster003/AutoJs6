package org.autojs.autojs.ui.settings

import android.content.Context
import android.text.InputType
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.storage.history.HistoryCleanupScheduler
import org.autojs.autojs.storage.history.RetentionLimitUnits
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Number input dialog for one retention limit; confirming stores the value and enqueues an
 * on-demand cleanup so the new limit takes effect right away.
 * zh-CN: 单个保留策略上限的数字输入对话框; 确认后写入偏好并排队一次即时清理, 使新上限立即生效.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
class RetentionLimitDialogBuilder(
    context: Context,
    private val spec: RetentionLimitSettings.Spec,
    onChangeConfirmed: () -> Unit = {},
) : MaterialDialog.Builder(context) {

    init {
        val current = RetentionLimitSettings.read(spec)

        title(spec.titleRes)
        content(R.string.text_zero_means_unlimited)
        inputType(InputType.TYPE_CLASS_NUMBER)
        input(
            RetentionLimitSettings.inputHint(context, spec),
            RetentionLimitSettings.toInputNumber(spec, current).toString(),
            false,
        ) { dialog, input ->
            val number = RetentionLimitUnits.parseNonNegativeOrNull(input)
            if (number == null) {
                ViewUtils.showToast(context, R.string.error_invalid_number_input)
                return@input
            }
            val stored = RetentionLimitSettings.fromInputNumber(spec, number)
            if (stored != current) {
                RetentionLimitSettings.write(spec, stored)
                HistoryCleanupScheduler.scheduleOnDemandCleanup(context)
            }
            onChangeConfirmed()
            dialog.dismiss()
        }
        options(
            listOf(
                MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_use_default)) { dialog ->
                    setInputNumber(dialog, RetentionLimitSettings.toInputNumber(spec, spec.defaultValue))
                },
                MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_unlimited)) { dialog ->
                    setInputNumber(dialog, 0L)
                },
            ),
        )
        negativeText(R.string.dialog_button_cancel)
        negativeColorRes(R.color.dialog_button_default)
        onNegative { dialog, _ -> dialog.dismiss() }
        positiveText(R.string.dialog_button_confirm)
        positiveColorRes(R.color.dialog_button_attraction)
        autoDismiss(false)
        widgetThemeColor()
    }

    private fun setInputNumber(dialog: MaterialDialog, number: Long) {
        val editText = dialog.inputEditText ?: return
        val text = number.toString()
        editText.setText(text)
        editText.setSelection(text.length)
    }
}
