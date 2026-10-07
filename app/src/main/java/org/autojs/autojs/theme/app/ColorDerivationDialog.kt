package org.autojs.autojs.theme.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.color.ColorHarmony
import org.autojs.autojs.theme.color.ColorMetrics
import org.autojs.autojs.theme.color.ColorVisionSimulation
import org.autojs.autojs.theme.color.ColorVisionSimulation.Deficiency
import org.autojs.autojs.theme.color.ThemeColorDerivation
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.util.Locale

/**
 * Color tools of the color details (roadmap P4.3): harmonies, tones, contrast against the window backgrounds and
 * color vision simulation. Every color can be applied, added to a library or copied.
 * zh-CN: 颜色详情中的配色工具 (Roadmap P4.3): 和谐色, 色阶, 相对窗口背景的对比度与色觉模拟. 每个颜色都可以应用, 加入颜色库或复制.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object ColorDerivationDialog {

    /**
     * @param onApply applies one color as the theme color. zh-CN: 将一个颜色应用为主题色.
     * @param onAddToLibrary adds colors to a user color library. zh-CN: 将颜色加入用户颜色库.
     * @param onFavorite adds one color to the favorites (roadmap P5.1). zh-CN: 收藏一个颜色 (Roadmap P5.1).
     */
    fun show(context: Context, @ColorInt seed: Int, onApply: (Int) -> Unit, onAddToLibrary: (List<Int>) -> Unit, onFavorite: ((Int) -> Unit)? = null) {
        val color = seed or (0xFF shl 24)
        val padding = dp(context, 20)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, dp(context, 4), padding, 0)
        }

        fun row(label: String, colors: List<Int>) {
            content.addView(TextView(context).apply {
                text = label
                textSize = 13f
                setTextColor(context.getColor(R.color.day_night_alpha_60))
                setPadding(0, dp(context, 10), 0, dp(context, 4))
                setOnClickListener {
                    ColorLibraryDialogs.showActions(context, label, listOf(context.getString(R.string.text_add_all_to_color_library) to { onAddToLibrary(colors.distinct()) }))
                }
            })
            val swatches = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            colors.forEach { swatches.addView(swatch(context, it) { showColorActions(context, it, onApply, onAddToLibrary, onFavorite) }) }
            content.addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(swatches)
            })
        }

        fun header(textRes: Int) = content.addView(TextView(context).apply {
            setText(textRes)
            textSize = 15f
            setTextColor(context.getColor(R.color.day_night_full))
            setPadding(0, dp(context, 14), 0, 0)
        })

        header(R.string.text_harmony_colors)
        ColorHarmony.Type.entries.forEach { type -> row(harmonyLabel(context, type), ColorHarmony.harmony(color, type)) }

        header(R.string.text_tones)
        row(context.getString(R.string.text_tones_light_to_dark), ColorHarmony.tones(color))

        header(R.string.text_contrast)
        listOf(
            R.string.color_name_window_background_light to ThemeColorDerivation.WINDOW_BACKGROUND_LIGHT,
            R.string.color_name_window_background_night to ThemeColorDerivation.WINDOW_BACKGROUND_NIGHT,
        ).forEach { (nameRes, background) ->
            val ratio = ColorMetrics.contrastRatio(color, background)
            val verdicts = listOf(3.0, 4.5).joinToString(", ") { target ->
                context.getString(if (ratio >= target) R.string.text_contrast_meets else R.string.text_contrast_below, formatRatio(target))
            }
            val nearest = ThemeColorDerivation.ensureContrast(color, background, ThemeColorDerivation.UI_COMPONENT_CONTRAST)
            row(
                "${context.getString(nameRes)} ${hex(background)}: ${formatRatio(ratio)} ($verdicts)",
                listOf(color, nearest).distinct(),
            )
        }
        content.addView(secondaryNote(context, context.getString(R.string.text_contrast_nearest_hint)))

        header(R.string.text_color_vision)
        Deficiency.entries.forEach { deficiency ->
            row(deficiencyLabel(context, deficiency), listOf(color, ColorVisionSimulation.simulate(color, deficiency)))
        }

        MaterialDialog.Builder(context)
            .title(context.getString(R.string.text_color_tools_of, hex(color)))
            .customView(content, true)
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_default)
            .show()
    }

    private fun showColorActions(context: Context, color: Int, onApply: (Int) -> Unit, onAddToLibrary: (List<Int>) -> Unit, onFavorite: ((Int) -> Unit)?) {
        val hex = hex(color)
        ColorLibraryDialogs.showActions(
            context, hex, listOfNotNull(
                context.getString(R.string.text_apply_as_theme_color) to { onApply(color) },
                context.getString(R.string.text_add_to_color_library) to { onAddToLibrary(listOf(color)) },
                onFavorite?.let { context.getString(R.string.text_add_to_favorites) to { it(color) } },
                context.getString(R.string.text_copy) to {
                    ClipboardUtils.setClip(context, hex)
                    ViewUtils.showToast(context, R.string.text_already_copied_to_clip)
                },
            )
        )
    }

    fun harmonyLabel(context: Context, type: ColorHarmony.Type): String = context.getString(
        when (type) {
            ColorHarmony.Type.COMPLEMENTARY -> R.string.text_harmony_complementary
            ColorHarmony.Type.ANALOGOUS -> R.string.text_harmony_analogous
            ColorHarmony.Type.TRIADIC -> R.string.text_harmony_triadic
            ColorHarmony.Type.SPLIT_COMPLEMENTARY -> R.string.text_harmony_split_complementary
            ColorHarmony.Type.TETRADIC -> R.string.text_harmony_tetradic
        }
    )

    private fun deficiencyLabel(context: Context, deficiency: Deficiency): String = context.getString(
        when (deficiency) {
            Deficiency.PROTANOPIA -> R.string.text_protanopia
            Deficiency.DEUTERANOPIA -> R.string.text_deuteranopia
            Deficiency.TRITANOPIA -> R.string.text_tritanopia
        }
    )

    private fun formatRatio(ratio: Double) = String.format(Locale.ROOT, if (ratio == Math.floor(ratio)) "%.0f:1" else "%.1f:1", ratio)

    private fun swatch(context: Context, color: Int, onClick: () -> Unit) = View(context).apply {
        val size = dp(context, 36)
        layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, dp(context, 2), dp(context, 10), dp(context, 2)) }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(context, 1), context.getColor(R.color.day_night_alpha_20))
        }
        contentDescription = hex(color)
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun secondaryNote(context: Context, text: String) = TextView(context).apply {
        this.text = text
        textSize = 12f
        gravity = Gravity.START
        setTextColor(context.getColor(R.color.day_night_alpha_60))
        setPadding(0, dp(context, 4), 0, 0)
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private fun hex(color: Int) = String.format("#%06X", 0xFFFFFF and color)

}
