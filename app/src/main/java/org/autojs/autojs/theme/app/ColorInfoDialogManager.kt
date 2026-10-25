package org.autojs.autojs.theme.app

import android.content.Context
import androidx.annotation.ColorInt
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.runtime.api.modules.colors.Colors
import org.autojs.autojs.ui.dialog.InfoDialog
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs6.R

object ColorInfoDialogManager {

    @JvmStatic
    fun showColorInfoDialog(
        context: Context,
        @ColorInt color: Int?,
        title: String? = null,
        customNeutral: CustomColorInfoDialogNeutral? = null,
        /** An extra action on the negative button, such as the color tools (roadmap P4.3). zh-CN: 放在否定按钮上的额外操作, 例如配色工具 (Roadmap P4.3). */
        customNegative: CustomColorInfoDialogNeutral? = null,
    ): InfoDialog? {
        if (color == null) {
            MaterialDialog.Builder(context)
                .title(R.string.text_prompt)
                .content(R.string.error_invalid_color)
                .positiveText(R.string.dialog_button_dismiss)
                .positiveColorRes(R.color.dialog_button_default)
                .showAdaptive()
            return null
        }
        val infoDialog = InfoDialog(context)
        val items = infoDialog.items.apply {
            addItem(R.string.literal_hex_uppercase, id = "colorHex")
            addItem(R.string.literal_rgb_uppercase, id = "colorRgb")
            addItem(R.string.literal_hsl_uppercase, id = "colorHsl")
            addItem(R.string.literal_hsv_uppercase, id = "colorHsv")
            addItem(R.string.literal_color_int, id = "colorInt")
        }

        val colorWithoutAlpha = color or -0x1000000

        infoDialog.builder.apply {
            title(title ?: context.getString(R.string.dialog_title_color_details))
            customNeutral?.let { neutral ->
                neutral.textRes?.let(::neutralText)
                neutral.colorRes?.let(::neutralColorRes)
                neutral.onNeutralCallback?.let {
                    onNeutral { dialog, which ->
                        it.onClick(dialog, which, colorWithoutAlpha)
                    }
                }
            }
            customNegative?.let { negative ->
                negative.textRes?.let(::negativeText)
                negative.colorRes?.let(::negativeColorRes)
                negative.onNeutralCallback?.let {
                    onNegative { dialog, which ->
                        it.onClick(dialog, which, colorWithoutAlpha)
                    }
                }
            }
            positiveText(R.string.dialog_button_dismiss)
            positiveColorRes(R.color.dialog_button_default)
        }.let { infoDialog.show(copyTitle = title != null) }

        infoDialog.scope.launch {
            val values = withContext(Dispatchers.IO) {
                listOf(
                    "colorHex" to ColorUtils.toHex(colorWithoutAlpha),
                    "colorRgb" to Colors.toRgbStringRhino(colorWithoutAlpha),
                    "colorHsl" to Colors.toHslStringRhino(colorWithoutAlpha),
                    "colorHsv" to Colors.toHsvStringRhino(colorWithoutAlpha),
                    "colorInt" to ColorUtils.toInt(colorWithoutAlpha).toString(),
                )
            }
            values.forEach { (id, value) -> items[id].setValue(value) }
        }
        return infoDialog
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