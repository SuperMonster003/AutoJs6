package org.autojs.autojs.ui.explorer

import androidx.annotation.DrawableRes

/**
 * How prominently the selection toolbar offers a multi-selection action.
 * zh-CN: 选择工具栏展示多选操作的重要级别.
 */
internal enum class ExplorerSelectionActionLevel {
    /**
     * A labeled button in the toolbar, such as "Copy to" or "Delete". Primary actions beyond the
     * toolbar's button slots move to the top of "More".
     * zh-CN: 工具栏中带文字的按钮, 如 "复制到" 或 "删除". 超出按钮位数量的主要操作移至 "更多" 的顶部.
     */
    PRIMARY,

    /**
     * Listed under "More", such as "Copy path" or plugin actions after the first one.
     * zh-CN: 列于 "更多" 之中, 如 "复制路径" 或第一个之后的插件操作.
     */
    SECONDARY,
}

/**
 * Groups the actions under "More", which separates the groups with dividers.
 * zh-CN: "更多" 中的操作分组, 各组之间以分隔线隔开.
 */
internal enum class ExplorerSelectionActionGroup {
    HOST,
    PLUGIN,
}

/**
 * One multi-selection action. [ExplorerView] decides which actions apply to the current selection,
 * and [ExplorerSelectionToolbarController] places them by [level].
 * zh-CN: 一个多选操作. [ExplorerView] 决定哪些操作适用于当前选择, [ExplorerSelectionToolbarController] 按 [level] 放置它们.
 */
internal class ExplorerSelectionAction(
    /** Stable across renders, so the same button view is reused while the selection changes. */
    val key: String,
    val label: CharSequence,
    /** Required for [ExplorerSelectionActionLevel.PRIMARY]; "More" lists labels only. */
    @DrawableRes val iconRes: Int?,
    /** Optical inset for glyphs that look larger than their neighbours in the 22dp icon box. */
    val iconPaddingDp: Float = 0F,
    val level: ExplorerSelectionActionLevel,
    val group: ExplorerSelectionActionGroup = ExplorerSelectionActionGroup.HOST,
    val enabled: Boolean = true,
    val onLongClick: (() -> Boolean)? = null,
    val onClick: () -> Unit,
) {
    init {
        require(level != ExplorerSelectionActionLevel.PRIMARY || iconRes != null) {
            "Primary selection action \"$key\" needs an icon"
        }
    }
}

/**
 * Splits the actions into toolbar buttons and the "More" list.
 * zh-CN: 将操作拆分为工具栏按钮与 "更多" 列表.
 */
internal data class ExplorerSelectionActionLayout(
    val buttons: List<ExplorerSelectionAction>,
    val overflow: List<ExplorerSelectionAction>,
) {
    companion object {

        fun of(actions: List<ExplorerSelectionAction>, maxButtons: Int): ExplorerSelectionActionLayout {
            val (primary, secondary) = actions.partition { it.level == ExplorerSelectionActionLevel.PRIMARY }
            val buttons = primary.take(maxButtons)
            // A stable sort keeps the given order inside each group, with demoted primary actions first.
            // zh-CN: 稳定排序保持各组内的原有顺序, 被降级的主要操作排在前面.
            val overflow = (primary.drop(maxButtons) + secondary).sortedBy { it.group.ordinal }
            return ExplorerSelectionActionLayout(buttons, overflow)
        }

    }
}
