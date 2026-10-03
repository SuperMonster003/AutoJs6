package org.autojs.autojs.ui.floating.layoutinspector

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.widget.EditText
import androidx.core.graphics.ColorUtils as AndroidColors
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs6.R

/** Colors measured against the inspector surface, including both extremes behind translucent windows. */
class InspectorTheme @JvmOverloads constructor(
    context: Context,
    surface: Int = context.getColor(R.color.window_background),
    primary: Int = ThemeColorManager.colorPrimary,
) {
    private val backgrounds = if (Color.alpha(surface) == 255) listOf(surface) else listOf(
        AndroidColors.compositeColors(surface, Color.BLACK),
        AndroidColors.compositeColors(surface, Color.WHITE),
    )
    val background = AndroidColors.compositeColors(surface, context.getColor(R.color.window_background))
    val text = readable(context.getColor(R.color.day_night))
    val secondaryText = readable(AndroidColors.blendARGB(background, text, 0.7f))
    val accent = readable(primary)
    val control = readable(primary, 3.0)
    val selectionBackground = AndroidColors.blendARGB(background, accent, 0.18f)
    val selectionText = ColorUtils.adjustColorForContrast(selectionBackground, text)
    val selectionAccent = ColorUtils.adjustColorForContrast(selectionBackground, accent)
    val matchBackground = AndroidColors.blendARGB(background, accent, 0.3f)
    val matchText = ColorUtils.adjustColorForContrast(matchBackground, text)
    val onAccent = ColorUtils.adjustColorForContrast(accent, text)
    val controls = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(ColorUtils.applyAlpha(text, 0.38), control, readable(secondaryText, 3.0)),
    )

    fun applyTo(input: EditText) {
        input.setTextColor(text)
        input.setHintTextColor(secondaryText)
        input.backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(control, secondaryText),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            input.textCursorDrawable = input.textCursorDrawable?.mutate()?.apply { setTint(control) }
            input.textSelectHandle?.mutate()?.apply { setTint(control) }?.let(input::setTextSelectHandle)
            input.textSelectHandleLeft?.mutate()?.apply { setTint(control) }?.let(input::setTextSelectHandleLeft)
            input.textSelectHandleRight?.mutate()?.apply { setTint(control) }?.let(input::setTextSelectHandleRight)
        }
    }

    @JvmOverloads
    fun readable(reference: Int, minimumContrast: Double = 4.5): Int {
        val opaque = ColorUtils.applyAlpha(reference, 1.0)
        val candidates = listOf(opaque) + backgrounds.map { ColorUtils.adjustColorForContrast(it, opaque, minimumContrast) }
        return candidates.firstOrNull { candidate -> backgrounds.all { AndroidColors.calculateContrast(candidate, it) >= minimumContrast } }
            ?: listOf(Color.BLACK, Color.WHITE).maxBy { candidate -> backgrounds.minOf { AndroidColors.calculateContrast(candidate, it) } }
    }
}
