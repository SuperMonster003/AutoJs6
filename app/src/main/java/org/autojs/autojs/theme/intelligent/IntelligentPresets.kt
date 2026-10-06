package org.autojs.autojs.theme.intelligent

import android.content.Context
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

    const val BACKGROUND_GROUP_LIGHT = 0L
    const val BACKGROUND_GROUP_DARK = 1L
    const val BACKGROUND_GROUP_FOLLOW = 2L
    const val BACKGROUND_GROUP_INVERSE = 3L

    /** Material families with their 50 and 800 shades (day / night). zh-CN: Material 色系及其 50 与 800 色阶 (日 / 夜). */
    private val MATERIAL_FAMILIES = listOf(
        Triple(R.string.md_red_50, R.color.md_red_50, R.color.md_red_800),
        Triple(R.string.md_pink_50, R.color.md_pink_50, R.color.md_pink_800),
        Triple(R.string.md_purple_50, R.color.md_purple_50, R.color.md_purple_800),
        Triple(R.string.md_deep_purple_50, R.color.md_deep_purple_50, R.color.md_deep_purple_800),
        Triple(R.string.md_indigo_50, R.color.md_indigo_50, R.color.md_indigo_800),
        Triple(R.string.md_blue_50, R.color.md_blue_50, R.color.md_blue_800),
        Triple(R.string.md_light_blue_50, R.color.md_light_blue_50, R.color.md_light_blue_800),
        Triple(R.string.md_cyan_50, R.color.md_cyan_50, R.color.md_cyan_800),
        Triple(R.string.md_teal_50, R.color.md_teal_50, R.color.md_teal_800),
        Triple(R.string.md_green_50, R.color.md_green_50, R.color.md_green_800),
        Triple(R.string.md_light_green_50, R.color.md_light_green_50, R.color.md_light_green_800),
        Triple(R.string.md_lime_50, R.color.md_lime_50, R.color.md_lime_800),
        Triple(R.string.md_yellow_50, R.color.md_yellow_50, R.color.md_yellow_800),
        Triple(R.string.md_amber_50, R.color.md_amber_50, R.color.md_amber_800),
        Triple(R.string.md_orange_50, R.color.md_orange_50, R.color.md_orange_800),
        Triple(R.string.md_deep_orange_50, R.color.md_deep_orange_50, R.color.md_deep_orange_800),
        Triple(R.string.md_brown_50, R.color.md_brown_50, R.color.md_brown_800),
        Triple(R.string.md_gray_50, R.color.md_gray_50, R.color.md_gray_800),
        Triple(R.string.md_blue_gray_50, R.color.md_blue_gray_50, R.color.md_blue_gray_800),
    )

    private val SHADE_SUFFIX = Regex("\\s*\\(\\d+\\)$")

    fun all(context: Context): List<IntelligentSnapshot> = listOf(materialDayNight(context), background(context))

    fun find(context: Context, libraryId: Int): IntelligentSnapshot? = when (libraryId) {
        ID_MATERIAL_DAY_NIGHT -> materialDayNight(context)
        ID_BACKGROUND -> background(context)
        else -> null
    }

    /**
     * One group per Material family: day = 50, night = 800, following night mode; picking a group pins it.
     * zh-CN: 每个 Material 色系一组: 日 = 50, 夜 = 800, 跟随夜间模式; 选择一组即固定使用该组.
     */
    fun materialDayNight(context: Context) = IntelligentSnapshot(
        libraryId = ID_MATERIAL_DAY_NIGHT,
        name = context.getString(R.string.text_intelligent_preset_material_day_night),
        config = IntelligentConfig(StateSource.NightMode),
        groups = MATERIAL_FAMILIES.mapIndexed { index, (nameRes, dayRes, nightRes) ->
            IntelligentGroup(
                id = index.toLong(),
                name = context.getString(nameRes).replace(SHADE_SUFFIX, ""),
                position = index,
                states = dayNight(context.getColor(dayRes), context.getColor(nightRes)),
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
