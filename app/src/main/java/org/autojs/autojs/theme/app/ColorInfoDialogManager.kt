package org.autojs.autojs.theme.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View.MeasureSpec.UNSPECIFIED
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.extension.MaterialDialogExtensions.makeTextCopyable
import org.autojs.autojs.extension.MaterialDialogExtensions.setCopyableText
import org.autojs.autojs.runtime.api.augment.colors.Colors
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ColorInfoDialogListItemBinding

object ColorInfoDialogManager {

    @JvmStatic
    fun showColorInfoDialog(
        context: Context,
        @ColorInt color: Int?,
        title: String? = null,
        customNeutral: CustomColorInfoDialogNeutral? = null,
    ) {
        if (color == null) {
            MaterialDialog.Builder(context)
                .title(R.string.text_prompt)
                .content(R.string.error_invalid_color)
                .positiveText(R.string.dialog_button_dismiss)
                .positiveColorRes(R.color.dialog_button_default)
                .show()
            return
        }
        val binding = ColorInfoDialogListItemBinding.inflate(LayoutInflater.from(context))

        val colorWithoutAlpha = color or -0x1000000

        val dialog = MaterialDialog.Builder(context).apply {
            title(title ?: context.getString(R.string.dialog_title_color_details))
            customView(binding.root, false)
            customNeutral?.let { neutral ->
                neutral.textRes?.let(::neutralText)
                neutral.colorRes?.let(::neutralColorRes)
                neutral.onNeutralCallback?.let {
                    onNeutral { dialog, which ->
                        it.onClick(dialog, which, colorWithoutAlpha)
                    }
                }
            }
            positiveText(R.string.dialog_button_dismiss)
            positiveColorRes(R.color.dialog_button_default)
        }.show()

        title?.run { dialog.makeTextCopyable { it.titleView } }

        CoroutineScope(Dispatchers.Main).launch {
            launch(Dispatchers.IO) {
                ColorUtils.toHex(colorWithoutAlpha).let { binding.colorHexValue.bindWith(dialog, it) }
                Colors.toRgbStringRhino(colorWithoutAlpha).let { binding.colorRgbValue.bindWith(dialog, it) }
                Colors.toHslStringRhino(colorWithoutAlpha).let { binding.colorHslValue.bindWith(dialog, it) }
                Colors.toHsvStringRhino(colorWithoutAlpha).let { binding.colorHsvValue.bindWith(dialog, it) }
                ColorUtils.toInt(colorWithoutAlpha).let { binding.colorIntValue.bindWith(dialog, it.toString()) }
            }
            restoreEssentialViews(binding)
            updateGuidelines(binding)
        }
    }

    private fun restoreEssentialViews(binding: ColorInfoDialogListItemBinding) {
        listOf(
            binding.colorHexColon to binding.colorHexValue,
            binding.colorRgbColon to binding.colorRgbValue,
            binding.colorHslColon to binding.colorHslValue,
            binding.colorHsvColon to binding.colorHsvValue,
            binding.colorIntColon to binding.colorIntValue,
        ).forEach { pair ->
            val (colonView, valueView) = pair
            colonView.isVisible = true
            valueView.isVisible = true
        }
    }

    private fun updateGuidelines(binding: ColorInfoDialogListItemBinding) {
        val filteredBindings = listOf(
            binding.colorHexLabel to binding.colorHexGuideline,
            binding.colorRgbLabel to binding.colorRgbGuideline,
            binding.colorHslLabel to binding.colorHslGuideline,
            binding.colorHsvLabel to binding.colorHsvGuideline,
            binding.colorIntLabel to binding.colorIntGuideline,
        ).filter { (it.first.parent as? ConstraintLayout)?.isVisible == true }

        val maxWidth = filteredBindings.maxOfOrNull { it.first.apply { measure(UNSPECIFIED, UNSPECIFIED) }.measuredWidth } ?: return

        filteredBindings.forEach { (_, guideline) ->
            guideline.layoutParams = (guideline.layoutParams as ConstraintLayout.LayoutParams).also {
                it.guideBegin = maxWidth
            }
        }
    }

    private suspend fun TextView.bindWith(dialog: MaterialDialog, text: String) {
        withContext(Dispatchers.Main) {
            dialog.setCopyableText(this@bindWith, text)
        }
    }

    data class CustomColorInfoDialogNeutral(
        val textRes: Int? = null,
        val colorRes: Int? = null,
        val onNeutralCallback: NeutralButtonCallback? = null,
    ) {
        interface NeutralButtonCallback {
            fun onClick(dialog: MaterialDialog, which: DialogAction, @ColorInt color: Int)
        }
    }

}