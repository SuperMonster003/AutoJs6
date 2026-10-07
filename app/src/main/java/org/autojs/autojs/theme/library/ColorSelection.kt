package org.autojs.autojs.theme.library

import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_ANDROID
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_CSS
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_DEFAULT
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_MATERIAL
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_PALETTE
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_WEB

/**
 * Library id segments (roadmap D9). Preset ids keep their historical values; user libraries are tagged by type
 * so that a stored selection tells plain from intelligent libraries without a database lookup.
 * zh-CN: 颜色库 ID 分段 (Roadmap D9). 预设库沿用历史取值; 用户库按类型分段, 使保存的选中状态无需查询数据库即可区分普通库与智能库.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
object ColorLibraryIds {

    const val SELECT_NONE = -1

    const val PRESET_FIRST = 0x01
    const val PRESET_LAST = 0xFF

    const val PRESET_INTELLIGENT_FIRST = 0x100
    const val PRESET_INTELLIGENT_LAST = 0x1FF

    /**
     * Virtual libraries next to the palette (0x21) and the intelligent entry (0x22) (roadmap P5.1, P5.4). Favorites
     * are selectable: their item id is the favorite row id; recommendations apply as the item they come from.
     * zh-CN: 与调色板 (0x21) 和智能库入口 (0x22) 并列的虚拟库 (Roadmap P5.1, P5.4). 收藏可被选中, 其条目 id 为收藏行 id;
     * 推荐条目按其来源条目应用.
     */
    const val FAVORITES = 0x23
    const val RECOMMENDED = 0x24

    const val USER_PLAIN_BASE = 0x0100_0000
    const val USER_INTELLIGENT_BASE = 0x0200_0000

    /** Exclusive upper bound of user library row ids. zh-CN: 用户库行 id 的上界 (不含). */
    const val USER_ROW_ID_LIMIT = 0x0100_0000L

    fun isPreset(id: Int) = id in PRESET_FIRST..PRESET_LAST

    fun isPresetIntelligent(id: Int) = id in PRESET_INTELLIGENT_FIRST..PRESET_INTELLIGENT_LAST

    fun isUserPlain(id: Int) = id >= USER_PLAIN_BASE && id - USER_PLAIN_BASE < USER_ROW_ID_LIMIT

    fun isUserIntelligent(id: Int) = id >= USER_INTELLIGENT_BASE && id - USER_INTELLIGENT_BASE < USER_ROW_ID_LIMIT

    fun ofUserLibrary(rowId: Long, intelligent: Boolean): Int {
        require(rowId in 1 until USER_ROW_ID_LIMIT) { "User library row id $rowId is out of range" }
        return (if (intelligent) USER_INTELLIGENT_BASE else USER_PLAIN_BASE) + rowId.toInt()
    }

    fun userRowIdOf(id: Int): Long? = when {
        isUserPlain(id) -> (id - USER_PLAIN_BASE).toLong()
        isUserIntelligent(id) -> (id - USER_INTELLIGENT_BASE).toLong()
        else -> null
    }

}

/**
 * The theme color selection stored in preferences, normalized across the current keys and the legacy index.
 * zh-CN: 保存在偏好设置中的主题色选中状态, 已将当前键与 legacy 索引归一化.
 */
sealed interface ColorSelection {

    /** An item of a preset library; [itemId] is null when only the library is known. zh-CN: 预设库中的条目; 只知道库时 [itemId] 为 null. */
    data class Preset(val libraryId: Int, val itemId: Int?) : ColorSelection

    /** A color picked from the palette; the color itself is the current theme color. zh-CN: 调色板选择的颜色; 颜色本身即当前主题色. */
    data object Palette : ColorSelection

    data class UserColor(val libraryRowId: Long, val colorRowId: Long?) : ColorSelection

    /** A favorite color (roadmap P5.1). zh-CN: 收藏的颜色 (Roadmap P5.1). */
    data class Favorite(val favoriteId: Long?) : ColorSelection

    /** An active intelligent library and its starting group. zh-CN: 激活的智能库及其起始组. */
    data class Intelligent(val libraryId: Int, val groupId: Long?) : ColorSelection {
        val isPreset: Boolean
            get() = ColorLibraryIds.isPresetIntelligent(libraryId)
        val userRowId: Long?
            get() = ColorLibraryIds.userRowIdOf(libraryId)
    }

    data class Unknown(val libraryId: Int, val itemId: Int) : ColorSelection

}

/**
 * Pure codec between [ColorSelection] and the raw preference values
 * (`SELECTED_COLOR_LIBRARY_ID`, `SELECTED_COLOR_LIBRARY_ITEM_ID` and the legacy index of the old layout).
 * zh-CN: [ColorSelection] 与原始偏好值 (`SELECTED_COLOR_LIBRARY_ID`, `SELECTED_COLOR_LIBRARY_ITEM_ID` 以及旧布局的 legacy 索引) 之间的纯编解码.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
object ColorSelectionCodec {

    /** Legacy index of the custom (palette) color, `ColorSelectBaseActivity.customColorPosition`. zh-CN: 自定义 (调色板) 颜色的 legacy 索引. */
    const val LEGACY_CUSTOM_INDEX = 0

    /** Legacy index of the default theme color, `ColorSelectBaseActivity.defaultColorPosition`. zh-CN: 默认主题色的 legacy 索引. */
    const val LEGACY_DEFAULT_INDEX = 1

    /** Legacy index of the first Material color. zh-CN: 第一个 Material 颜色的 legacy 索引. */
    const val LEGACY_MATERIAL_OFFSET = 2

    /**
     * When the library key is absent, the legacy index decides; otherwise the library key does.
     * zh-CN: 库键缺失时由 legacy 索引决定, 否则由库键决定.
     */
    fun decode(libraryId: Int, itemId: Int, legacyIndex: Int, materialColorCount: Int): ColorSelection {
        val item = itemId.takeIf { it != ColorLibraryIds.SELECT_NONE }
        return when {
            libraryId == ColorLibraryIds.SELECT_NONE -> decodeLegacy(legacyIndex, materialColorCount)
            libraryId == COLOR_LIBRARY_ID_PALETTE -> ColorSelection.Palette
            libraryId == ColorLibraryIds.FAVORITES -> ColorSelection.Favorite(item?.toLong())
            ColorLibraryIds.isPresetIntelligent(libraryId) || ColorLibraryIds.isUserIntelligent(libraryId) -> {
                ColorSelection.Intelligent(libraryId, item?.toLong())
            }
            ColorLibraryIds.isUserPlain(libraryId) -> {
                ColorSelection.UserColor(ColorLibraryIds.userRowIdOf(libraryId)!!, item?.toLong())
            }
            ColorLibraryIds.isPreset(libraryId) && libraryId in PRESET_COLOR_LIBRARY_IDS -> ColorSelection.Preset(libraryId, item)
            else -> ColorSelection.Unknown(libraryId, itemId)
        }
    }

    /**
     * Raw `(libraryId, itemId)` pair to store. Palette selections keep the historical item id 0.
     * zh-CN: 要保存的原始 `(libraryId, itemId)`. 调色板选择沿用历史上的条目 id 0.
     */
    fun encode(selection: ColorSelection): Pair<Int, Int> = when (selection) {
        is ColorSelection.Preset -> selection.libraryId to (selection.itemId ?: ColorLibraryIds.SELECT_NONE)
        ColorSelection.Palette -> COLOR_LIBRARY_ID_PALETTE to 0
        is ColorSelection.UserColor -> ColorLibraryIds.ofUserLibrary(selection.libraryRowId, intelligent = false) to rowIdToItemId(selection.colorRowId)
        is ColorSelection.Favorite -> ColorLibraryIds.FAVORITES to rowIdToItemId(selection.favoriteId)
        is ColorSelection.Intelligent -> selection.libraryId to rowIdToItemId(selection.groupId)
        is ColorSelection.Unknown -> selection.libraryId to selection.itemId
    }

    private fun decodeLegacy(legacyIndex: Int, materialColorCount: Int): ColorSelection = when (legacyIndex) {
        LEGACY_CUSTOM_INDEX -> ColorSelection.Palette
        ColorLibraryIds.SELECT_NONE, LEGACY_DEFAULT_INDEX -> ColorSelection.Preset(COLOR_LIBRARY_ID_DEFAULT, null)
        else -> when (val materialIndex = legacyIndex - LEGACY_MATERIAL_OFFSET) {
            in 0 until materialColorCount -> ColorSelection.Preset(COLOR_LIBRARY_ID_MATERIAL, materialIndex)
            else -> ColorSelection.Unknown(ColorLibraryIds.SELECT_NONE, legacyIndex)
        }
    }

    private fun rowIdToItemId(rowId: Long?): Int {
        if (rowId == null) return ColorLibraryIds.SELECT_NONE
        require(rowId in 0..Int.MAX_VALUE) { "Row id $rowId does not fit an item id" }
        return rowId.toInt()
    }

    /**
     * Ids of preset libraries that hold colors (default, Material, Android, CSS, WEB). The palette (0x21) and the
     * intelligent container entry (0x22) are not selectable items.
     * zh-CN: 包含颜色的预设库 id (默认 / Material / Android / CSS / WEB). 调色板 (0x21) 与智能库入口 (0x22) 不是可选条目.
     */
    private val PRESET_COLOR_LIBRARY_IDS = setOf(
        COLOR_LIBRARY_ID_DEFAULT,
        COLOR_LIBRARY_ID_MATERIAL,
        COLOR_LIBRARY_ID_ANDROID,
        COLOR_LIBRARY_ID_CSS,
        COLOR_LIBRARY_ID_WEB,
    )

}
