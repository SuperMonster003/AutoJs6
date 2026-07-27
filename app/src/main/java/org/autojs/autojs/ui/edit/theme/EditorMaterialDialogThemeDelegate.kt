package org.autojs.autojs.ui.edit.theme

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import androidx.annotation.ColorInt
import androidx.appcompat.widget.PopupMenu
import androidx.cardview.widget.CardView
import androidx.core.widget.ImageViewCompat
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.MaterialDialogThemeDelegate
import com.afollestad.materialdialogs.Theme
import com.afollestad.materialdialogs.internal.MDRootLayout
import com.afollestad.materialdialogs.R as MaterialDialogR
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs6.R as AppR
import java.util.Collections
import java.util.WeakHashMap

/**
 * Activity-scoped MaterialDialog palette for the code editor.
 *
 * The delegate deliberately holds no Context. Open dialogs are weakly tracked, allowing a theme
 * switch to recolor them in place without extending either the dialog or Activity lifetime.
 */
class EditorMaterialDialogThemeDelegate : MaterialDialogThemeDelegate {

    private data class Palette(
        @param:ColorInt val background: Int,
        @param:ColorInt val foreground: Int,
    )

    @Volatile
    private var palette: Palette? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val dialogs = Collections.newSetFromMap(
        WeakHashMap<MaterialDialog, Boolean>(),
    )

    /**
     * Installs a new editor palette and immediately refreshes dialogs which are already visible.
     */
    fun updatePalette(@ColorInt background: Int, @ColorInt foreground: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updatePalette(background, foreground) }
            return
        }
        val next = Palette(background, foreground)
        if (palette == next) {
            EditorChromePalette.update(background, foreground)
            return
        }
        palette = next
        EditorChromePalette.update(background, foreground)
        val openDialogs = synchronized(dialogs) { dialogs.toList() }
        openDialogs.forEach { dialog ->
            dialog.refreshThemeFromDelegate()
            applyToDialog(dialog, next)
        }
    }

    override fun onPrepareBuilder(builder: MaterialDialog.Builder) {
        val palette = palette ?: return
        EditorChromePalette.update(palette.background, palette.foreground)
        val control = palette.foreground

        builder
            .theme(if (ColorUtils.isLuminanceDark(palette.background)) Theme.DARK else Theme.LIGHT)
            .backgroundColor(palette.background)
            .titleColor(palette.foreground)
            .contentColor(palette.foreground)
            .itemsColor(palette.foreground)
            .positiveColor(actionStateList(effectiveActionColor(builder.explicitPositiveColor, palette)))
            .negativeColor(actionStateList(effectiveActionColor(builder.explicitNegativeColor, palette)))
            .neutralColor(actionStateList(effectiveActionColor(builder.explicitNeutralColor, palette)))
            .linkColor(actionStateList(palette.foreground))
            .widgetColor(controlStateList(palette.foreground, control))
            .choiceWidgetColor(choiceStateList(palette.foreground, control))
            .dividerColor(withAlpha(palette.foreground, DIVIDER_ALPHA))
            .buttonRippleColor(withAlpha(palette.foreground, PRESSED_ALPHA))
            .listSelector(AppR.drawable.editor_popup_item_background)
            .btnSelector(AppR.drawable.editor_popup_item_background)
            .btnSelectorStacked(AppR.drawable.editor_popup_item_background)
    }

    override fun createOptionsPopupMenu(
        context: Context,
        anchor: View,
        gravity: Int,
    ): PopupMenu {
        palette?.let { EditorChromePalette.update(it.background, it.foreground) }
        val themedContext = ContextThemeWrapper(context, AppR.style.EditorPopupMenuTheme)
        return PopupMenu(themedContext, anchor, gravity)
    }

    override fun onPrepareOptionsPopupMenu(popupMenu: PopupMenu) {
        palette?.let {
            EditorChromePalette.update(it.background, it.foreground)
            EditorChromePalette.applyTo(popupMenu.menu)
        }
    }

    override fun onDialogCreated(dialog: MaterialDialog) {
        synchronized(dialogs) { dialogs.add(dialog) }
        palette?.let { applyToDialog(dialog, it) }
    }

    override fun onDialogShown(dialog: MaterialDialog) {
        synchronized(dialogs) { dialogs.add(dialog) }
        palette?.let { applyToDialog(dialog, it) }
    }

    override fun onDialogDismissed(dialog: MaterialDialog) {
        synchronized(dialogs) { dialogs.remove(dialog) }
        EditorDialogViewThemeApplier.detach(dialog.view)
    }

    private fun applyToDialog(dialog: MaterialDialog, palette: Palette) {
        EditorChromePalette.update(palette.background, palette.foreground)
        val builder = dialog.builder
        val control = palette.foreground

        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                cornerRadius = dialog.context.resources.getDimension(
                    MaterialDialogR.dimen.md_bg_corner_radius,
                )
                setColor(palette.background)
            },
        )
        dialog.customView?.let { customView ->
            when (customView) {
                is CardView -> customView.setCardBackgroundColor(palette.background)
                else -> customView.setBackgroundColor(palette.background)
            }
        }
        EditorDialogViewThemeApplier.apply(
            dialog.view,
            palette.background,
            palette.foreground,
            control,
        )

        dialog.titleView?.setTextColor(enabledStateList(palette.foreground))
        dialog.contentView?.run {
            setTextColor(enabledStateList(palette.foreground))
            setLinkTextColor(palette.foreground)
        }
        dialog.optionsView?.let { optionsView ->
            ImageViewCompat.setImageTintList(
                optionsView,
                ColorStateList.valueOf(palette.foreground),
            )
        }
        dialog.iconView?.let { iconView ->
            ImageViewCompat.setImageTintList(
                iconView,
                ColorStateList.valueOf(palette.foreground),
            )
        }

        dialog.getActionButton(DialogAction.POSITIVE).setTextColor(
            actionStateList(effectiveActionColor(builder.explicitPositiveColor, palette)),
        )
        dialog.getActionButton(DialogAction.NEGATIVE).setTextColor(
            actionStateList(effectiveActionColor(builder.explicitNegativeColor, palette)),
        )
        dialog.getActionButton(DialogAction.NEUTRAL).setTextColor(
            actionStateList(effectiveActionColor(builder.explicitNeutralColor, palette)),
        )
        (dialog.view as? MDRootLayout)?.setDividerColor(
            withAlpha(palette.foreground, DIVIDER_ALPHA),
        )
        dialog.view.invalidate()
    }

    @ColorInt
    private fun effectiveActionColor(
        explicitColor: ColorStateList?,
        palette: Palette,
    ): Int = explicitColor?.defaultColor?.let { color ->
        ColorUtils.adjustColorForContrast(
            palette.background,
            color,
            SEMANTIC_COLOR_MINIMUM_CONTRAST,
        )
    } ?: palette.foreground

    private fun enabledStateList(@ColorInt foreground: Int) = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(),
        ),
        intArrayOf(
            withAlpha(foreground, DISABLED_ALPHA),
            foreground,
        ),
    )

    private fun actionStateList(@ColorInt color: Int) = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(),
        ),
        intArrayOf(
            withAlpha(color, DISABLED_ALPHA),
            color,
        ),
    )

    private fun controlStateList(
        @ColorInt foreground: Int,
        @ColorInt control: Int,
    ) = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_focused),
            intArrayOf(),
        ),
        intArrayOf(
            withAlpha(foreground, DISABLED_ALPHA),
            control,
            withAlpha(foreground, SECONDARY_ALPHA),
        ),
    )

    private fun choiceStateList(
        @ColorInt foreground: Int,
        @ColorInt control: Int,
    ) = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(),
        ),
        intArrayOf(
            withAlpha(foreground, DISABLED_ALPHA),
            control,
            withAlpha(foreground, SECONDARY_ALPHA),
        ),
    )

    @ColorInt
    private fun withAlpha(@ColorInt color: Int, alpha: Float): Int {
        val sourceAlpha = Color.alpha(color)
        return color and 0x00FFFFFF or
            ((sourceAlpha * alpha).toInt().coerceIn(0, 0xFF) shl 24)
    }

    private companion object {
        const val SEMANTIC_COLOR_MINIMUM_CONTRAST = 3.0
        const val SECONDARY_ALPHA = 0.68f
        const val DISABLED_ALPHA = 0.38f
        const val DIVIDER_ALPHA = 0.20f
        const val PRESSED_ALPHA = 0.14f
    }
}
