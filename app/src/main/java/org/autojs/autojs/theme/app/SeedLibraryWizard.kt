package org.autojs.autojs.theme.app

import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.app.ColorEntities.ColorLibrary
import org.autojs.autojs.theme.color.ColorHarmony
import org.autojs.autojs.theme.intelligent.IntelligentColorEngine
import org.autojs.autojs.theme.intelligent.IntelligentSeedGenerator
import org.autojs.autojs.theme.intelligent.IntelligentSeedGenerator.Scheme
import org.autojs.autojs.theme.intelligent.IntelligentSnapshot
import org.autojs.autojs.theme.intelligent.WallpaperColorSource
import org.autojs.autojs.theme.library.ColorLibraryRepository
import org.autojs.autojs.theme.library.LibraryNaming
import org.autojs.autojs6.R

/**
 * "Generate from a seed color" (roadmap P4.5): seed, scheme, state source, preview with a name, then the library is
 * created and opened.
 * zh-CN: "从种子色生成" (Roadmap P4.5): 选择种子色, 方案与状态源, 预览并命名, 然后创建并打开该库.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
class SeedLibraryWizard(
    private val activity: AppCompatActivity,
    /** Opens the color picker and passes the picked color. zh-CN: 打开调色盘并传回所选颜色. */
    private val pickColor: (initial: Int, onPicked: (Int) -> Unit) -> Unit,
    private val onCreated: (IntelligentSnapshot) -> Unit,
) {

    fun start() {
        val current = ThemeColorManager.colorPrimary
        val wallpaper = WallpaperColorSource.seeds(activity)
        val options = buildList<Pair<String, (() -> Unit)>> {
            add(activity.getString(R.string.text_seed_current_theme_color, hex(current)) to { chooseScheme(current) })
            add(activity.getString(R.string.text_seed_from_color_palette) to { pickColor(current) { chooseScheme(it) } })
            wallpaper.forEach { seed -> add(activity.getString(R.string.text_seed_wallpaper_color, hex(seed)) to { chooseScheme(seed) }) }
        }
        ColorLibraryDialogs.showSingleChoice(activity, R.string.text_choose_seed_color, options.map { it.first }) { options[it].second() }
    }

    private fun chooseScheme(seed: Int) {
        val schemes: List<Scheme> = ColorHarmony.Type.entries.map { Scheme.Harmony(it) } + Scheme.Tones()
        val labels = schemes.map { scheme ->
            val name = when (scheme) {
                is Scheme.Harmony -> ColorDerivationDialog.harmonyLabel(activity, scheme.type)
                is Scheme.Tones -> activity.getString(R.string.text_tones)
            }
            "$name (${activity.resources.getQuantityString(R.plurals.text_n_groups, scheme.colorCount, scheme.colorCount)})"
        }
        ColorLibraryDialogs.showSingleChoice(activity, R.string.text_choose_scheme, labels) { which -> chooseStateSource(seed, schemes[which], labels[which]) }
    }

    private fun chooseStateSource(seed: Int, scheme: Scheme, schemeLabel: String) {
        val labels = listOf(activity.getString(R.string.text_seed_state_night_mode), activity.getString(R.string.text_state_source_none))
        ColorLibraryDialogs.showSingleChoice(activity, R.string.text_state_source, labels) { which -> preview(seed, scheme, schemeLabel, nightMode = which == 0) }
    }

    private fun preview(seed: Int, scheme: Scheme, schemeLabel: String, nightMode: Boolean) {
        val result = IntelligentSeedGenerator.generate(seed, scheme, nightMode)
        val padding = dp(20)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, dp(8), padding, 0)
        }
        val nameInput = AppCompatEditText(activity).apply {
            hint = activity.getString(R.string.text_color_library_name)
            setText(LibraryNaming.normalize("${schemeLabel.substringBefore(" (")} ${hex(seed)}"))
        }
        val swatches = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(4))
        }
        result.groups.forEach { group ->
            swatches.addView(View(activity).apply {
                val size = dp(40)
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(10) }
                background = SegmentedCircleDrawable(IntelligentSummaryFormatter.stateColors(result.config, group))
            })
        }
        content.addView(nameInput)
        content.addView(HorizontalScrollView(activity).apply { addView(swatches) })
        content.addView(android.widget.TextView(activity).apply {
            text = IntelligentSummaryFormatter.summary(activity, result.config, result.groups)
            textSize = 13f
            setTextColor(activity.getColor(R.color.day_night_alpha_60))
        })
        MaterialDialog.Builder(activity)
            .title(R.string.text_preview)
            .customView(content, true)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.text_create)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { _, _ ->
                val name = LibraryNaming.normalize(nameInput.text?.toString()) ?: activity.getString(R.string.color_library_intelligent_colors)
                activity.lifecycleScope.launch {
                    val repository = ColorLibraryRepository.get(activity)
                    val saved = withContext(Dispatchers.IO) {
                        repository.saveIntelligent(null, name, result.config, result.groups, ColorLibrary.Kind.CREATED, origin = hex(seed))
                    }
                    IntelligentColorEngine.onLibraryChanged(saved)
                    onCreated(saved)
                }
            }
            .show()
    }

    private fun hex(color: Int) = String.format("#%06X", 0xFFFFFF and color)

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

}
