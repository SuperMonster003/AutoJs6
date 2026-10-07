package org.autojs.autojs.theme.intelligent

import android.content.Context
import org.autojs.autojs.core.image.ColorItems
import org.autojs.autojs.theme.color.ThemeColorDerivation
import org.autojs.autojs6.R

/**
 * Built-in intelligent libraries (roadmap P1.5, P1.6). They are read-only and built from resources on demand;
 * their ids live in `0x100..0x1FF` (D9) and their group ids are stable indices.
 * zh-CN: 内置的智能库 (Roadmap P1.5, P1.6). 只读, 按需从资源构建; 库 id 位于 `0x100..0x1FF` (D9), 组 id 为稳定的下标.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
object IntelligentPresets {

    const val ID_MATERIAL_DAY_NIGHT = 0x100
    const val ID_BACKGROUND = 0x101
    const val ID_WALLPAPER = 0x102

    const val BACKGROUND_GROUP_LIGHT = 0L
    const val BACKGROUND_GROUP_DARK = 1L
    const val BACKGROUND_GROUP_FOLLOW = 2L
    const val BACKGROUND_GROUP_INVERSE = 3L

    /**
     * Material families by their 50 shade; the shades 50..900 follow it in [ColorItems.MATERIAL_COLORS].
     * zh-CN: 以 50 色阶表示的 Material 色系; 50..900 各色阶在 [ColorItems.MATERIAL_COLORS] 中紧随其后.
     */
    private val MATERIAL_FAMILIES = listOf(
        R.color.md_red_50 to R.string.md_red_50,
        R.color.md_pink_50 to R.string.md_pink_50,
        R.color.md_purple_50 to R.string.md_purple_50,
        R.color.md_deep_purple_50 to R.string.md_deep_purple_50,
        R.color.md_indigo_50 to R.string.md_indigo_50,
        R.color.md_blue_50 to R.string.md_blue_50,
        R.color.md_light_blue_50 to R.string.md_light_blue_50,
        R.color.md_cyan_50 to R.string.md_cyan_50,
        R.color.md_teal_50 to R.string.md_teal_50,
        R.color.md_green_50 to R.string.md_green_50,
        R.color.md_light_green_50 to R.string.md_light_green_50,
        R.color.md_lime_50 to R.string.md_lime_50,
        R.color.md_yellow_50 to R.string.md_yellow_50,
        R.color.md_amber_50 to R.string.md_amber_50,
        R.color.md_orange_50 to R.string.md_orange_50,
        R.color.md_deep_orange_50 to R.string.md_deep_orange_50,
        R.color.md_brown_50 to R.string.md_brown_50,
        R.color.md_gray_50 to R.string.md_gray_50,
        R.color.md_blue_gray_50 to R.string.md_blue_gray_50,
    )

    /** The shades 50, 100, ..., 900. zh-CN: 色阶 50, 100, ..., 900. */
    private const val TONAL_SHADE_COUNT = 10

    private val SHADE_SUFFIX = Regex("\\s*\\(\\d+\\)$")

    fun all(context: Context): List<IntelligentSnapshot> = listOf(materialDayNight(context), background(context), wallpaper(context))

    fun find(context: Context, libraryId: Int): IntelligentSnapshot? = when (libraryId) {
        ID_MATERIAL_DAY_NIGHT -> materialDayNight(context)
        ID_BACKGROUND -> background(context)
        ID_WALLPAPER -> wallpaper(context)
        else -> null
    }

    fun isPreset(libraryId: Int) = libraryId in setOf(ID_MATERIAL_DAY_NIGHT, ID_BACKGROUND, ID_WALLPAPER)

    /**
     * One group per wallpaper seed color (roadmap P4.2), following night mode: day and night are the nearest colors
     * with at least 3:1 against the day and night window backgrounds, like the shades of D24. Group ids are the seed
     * positions, so "the k-th group" stays selected when the wallpaper changes. Empty when the colors are unavailable.
     * zh-CN: 每个壁纸种子色一组 (Roadmap P4.2), 跟随夜间模式: 日 / 夜分别为与日间 / 夜间窗口背景对比度不低于 3:1 的最近颜色,
     * 与 D24 的色阶规则一致. 组 id 为种子色的位置, 因此壁纸变化后仍选中 "第 k 组". 无法读取颜色时没有组.
     */
    fun wallpaper(context: Context): IntelligentSnapshot {
        val names = listOf(R.string.text_wallpaper_color_primary, R.string.text_wallpaper_color_secondary, R.string.text_wallpaper_color_tertiary)
        val groups = WallpaperColorSource.seeds(context).take(names.size).mapIndexed { index, seed ->
            val (day, night) = ThemeColorDerivation.dayNightPair(seed)
            IntelligentGroup(id = index.toLong(), name = context.getString(names[index]), position = index, states = dayNight(day, night))
        }
        return IntelligentSnapshot(ID_WALLPAPER, context.getString(R.string.text_intelligent_preset_wallpaper), IntelligentConfig(StateSource.NightMode), groups)
    }

    /**
     * One group per Material family following night mode; picking a group pins it. The shades are picked by
     * contrast against the window backgrounds (D24): day = the lightest shade with at least 3:1 against the light
     * background, night = the darkest shade with at least 3:1 against the night background.
     * zh-CN: 每个 Material 色系一组, 跟随夜间模式; 选择一组即固定使用该组. 色阶按与窗口背景的对比度挑选 (D24):
     * 日 = 与日间背景对比度不低于 3:1 的最浅色阶, 夜 = 与夜间背景对比度不低于 3:1 的最深色阶.
     */
    fun materialDayNight(context: Context) = IntelligentSnapshot(
        libraryId = ID_MATERIAL_DAY_NIGHT,
        name = context.getString(R.string.text_intelligent_preset_material_day_night),
        config = IntelligentConfig(StateSource.NightMode),
        groups = MATERIAL_FAMILIES.mapIndexed { index, (fiftyRes, nameRes) ->
            val start = ColorItems.MATERIAL_COLORS.indexOfFirst { it.first == fiftyRes }
            val shades = ColorItems.MATERIAL_COLORS.subList(start, start + TONAL_SHADE_COUNT).map { context.getColor(it.first) }
            IntelligentGroup(
                id = index.toLong(),
                name = context.getString(nameRes).replace(SHADE_SUFFIX, ""),
                position = index,
                states = dayNight(ThemeColorDerivation.pickDayShade(shades), ThemeColorDerivation.pickNightShade(shades)),
            )
        },
    )

    /**
     * The window backgrounds as theme colors: light, dark, following night mode, or the inverse.
     * zh-CN: 以窗口背景色作为主题色: 亮色, 暗色, 跟随夜间模式, 或与之相反.
     */
    fun background(context: Context): IntelligentSnapshot {
        val light = context.getColor(R.color.window_background_light)
        val dark = context.getColor(R.color.window_background_night)
        val groups = listOf(
            Triple(BACKGROUND_GROUP_LIGHT, R.string.text_background_color_light, light to light),
            Triple(BACKGROUND_GROUP_DARK, R.string.text_background_color_dark, dark to dark),
            Triple(BACKGROUND_GROUP_FOLLOW, R.string.text_background_color_follow, light to dark),
            Triple(BACKGROUND_GROUP_INVERSE, R.string.text_background_color_inverse, dark to light),
        ).map { (id, nameRes, colors) ->
            IntelligentGroup(id = id, name = context.getString(nameRes), position = id.toInt(), states = dayNight(colors.first, colors.second))
        }
        return IntelligentSnapshot(ID_BACKGROUND, context.getString(R.string.text_background_color), IntelligentConfig(StateSource.NightMode), groups)
    }

    private fun dayNight(day: Int, night: Int) = mapOf(StateKeys.DAY to StateColors(day), StateKeys.NIGHT to StateColors(night))

}
