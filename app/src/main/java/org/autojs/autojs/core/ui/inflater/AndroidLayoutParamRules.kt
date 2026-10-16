package org.autojs.autojs.core.ui.inflater

import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import java.util.WeakHashMap

/**
 * Which LayoutParams member a `layout_*` attribute sets when no handler of the inflater takes it (host roadmap
 * `docs/dev/android-xml-attributes-roadmap.md` A3): ConstraintLayout's attribute names follow fixed patterns onto its
 * public fields (`layout_constraintStart_toEndOf` -> `startToEnd`, `layout_constraintHorizontal_bias` ->
 * `horizontalBias`, `layout_constraintWidth_percent` -> `matchConstraintPercentWidth`, ...), a few names of other
 * containers follow no pattern, and the rest drop the `layout_` prefix (`layout_scrollFlags` -> `setScrollFlags`).
 * `layout_behavior` (a class name) and GridLayout's cell attributes (specs built from several values) are special.
 *
 * zh-CN: 布局器没有处理器的 `layout_*` 属性对应 LayoutParams 的哪个成员: ConstraintLayout 的属性名按固定模式映射到公开字段, 少数其它容器的名称不符合模式, 其余去掉 `layout_` 前缀;
 * `layout_behavior` (类名) 与 GridLayout 的单元格属性 (由多个值构成 spec) 为特例.
 */
internal object AndroidLayoutParamRules {

    private val CONSTRAINT_SIDES = Regex("layout_constraint(Top|Bottom|Left|Right|Start|End|Baseline)_to(Top|Bottom|Left|Right|Start|End|Baseline)Of")
    private val CONSTRAINT_AXIS = Regex("layout_constraint(Horizontal|Vertical)_(bias|weight|chainStyle)")
    private val CONSTRAINT_SIZE = Regex("layout_constraint(Width|Height)_(default|min|max|percent)")
    private val CONSTRAINT_GUIDE = Regex("layout_constraintGuide_(begin|end|percent)")
    private val GONE_MARGIN = Regex("layout_goneMargin(Top|Bottom|Left|Right|Start|End|Baseline)")

    private val EXPLICIT = mapOf(
        "layout_constraintCircle" to "circleConstraint",
        "layout_constraintCircleRadius" to "circleRadius",
        "layout_constraintCircleAngle" to "circleAngle",
        "layout_constraintDimensionRatio" to "dimensionRatio",
        "layout_constraintTag" to "constraintTag",
        "layout_constrainedWidth" to "constrainedWidth",
        "layout_constrainedHeight" to "constrainedHeight",
        "layout_alignWithParentIfMissing" to "alignWithParent",
        "layout_collapseParallaxMultiplier" to "parallaxMultiplier",
        "layout_anchor" to "anchorId",
    )

    /** Attributes for design tools only (Android Studio's layout editor); the inflater has nothing to do with them. */
    fun isDesignTime(name: String): Boolean = name.startsWith("layout_editor_")

    /** The LayoutParams property [name] sets: a public field, or the stem of a public setter. */
    fun property(name: String): String {
        EXPLICIT[name]?.let { return it }
        CONSTRAINT_SIDES.matchEntire(name)?.let { return it.groupValues[1].lowercaseFirst() + "To" + it.groupValues[2] }
        CONSTRAINT_AXIS.matchEntire(name)?.let { return it.groupValues[1].lowercaseFirst() + it.groupValues[2].uppercaseFirst() }
        CONSTRAINT_SIZE.matchEntire(name)?.let { return "matchConstraint" + it.groupValues[2].uppercaseFirst() + it.groupValues[1] }
        CONSTRAINT_GUIDE.matchEntire(name)?.let { return "guide" + it.groupValues[1].uppercaseFirst() }
        GONE_MARGIN.matchEntire(name)?.let { return "gone" + it.groupValues[1] + "Margin" }
        return name.removePrefix("layout_")
    }

    /**
     * Attributes a single member cannot take; each returns false when it does not apply to these params. They change
     * [params] in place, the caller assigns them back to the view.
     */
    val SPECIAL: Map<String, (AndroidAttributeFallback.Values, View, ViewGroup.LayoutParams, String) -> Boolean> = mapOf(
        "layout_behavior" to { _, view, params, value -> behavior(view, params, value) },
        "layout_constraintWidth" to { values, _, params, value -> constraintSize(values, params, value, horizontal = true) },
        "layout_constraintHeight" to { values, _, params, value -> constraintSize(values, params, value, horizontal = false) },
        "layout_row" to { values, view, params, value -> gridCell(view, params) { it.row = values.int(value) } },
        "layout_rowSpan" to { values, view, params, value -> gridCell(view, params) { it.rowSpan = values.int(value) } },
        "layout_rowWeight" to { values, view, params, value -> gridCell(view, params) { it.rowWeight = values.float(value) } },
        "layout_column" to { values, view, params, value -> gridCell(view, params) { it.column = values.int(value) } },
        "layout_columnSpan" to { values, view, params, value -> gridCell(view, params) { it.columnSpan = values.int(value) } },
        "layout_columnWeight" to { values, view, params, value -> gridCell(view, params) { it.columnWeight = values.float(value) } },
    )

    /** CoordinatorLayout's `layout_behavior`: a Behavior class by name (`.Name` is relative to the app's package). */
    private fun behavior(view: View, params: ViewGroup.LayoutParams, value: String): Boolean {
        val setter = params.javaClass.methods.firstOrNull { it.name == "setBehavior" && it.parameterTypes.size == 1 } ?: return false
        val name = value.trim().let { if (it.startsWith(".")) view.context.packageName + it else it }
        val type = try {
            Class.forName(name, true, view.context.classLoader)
        } catch (e: ClassNotFoundException) {
            throw IllegalArgumentException("behavior class $name is not found", e)
        }
        val behavior = type.constructors.firstOrNull { it.parameterTypes.size == 2 }?.newInstance(view.context, null)
            ?: type.getConstructor().newInstance()
        setter.invoke(params, behavior)
        return true
    }

    /**
     * ConstraintLayout 2.1's size shorthand: `wrap_content_constrained` (wrap, but within the constraints),
     * `match_constraint` (0), `wrap_content`, `match_parent` or a dimension.
     */
    private fun constraintSize(values: AndroidAttributeFallback.Values, params: ViewGroup.LayoutParams, value: String, horizontal: Boolean): Boolean {
        val constrained = params.javaClass.fields.firstOrNull { it.name == if (horizontal) "constrainedWidth" else "constrainedHeight" } ?: return false
        val size = when (value.trim()) {
            "wrap_content_constrained" -> ViewGroup.LayoutParams.WRAP_CONTENT.also { constrained.setBoolean(params, true) }
            "wrap_content" -> ViewGroup.LayoutParams.WRAP_CONTENT
            "match_parent" -> ViewGroup.LayoutParams.MATCH_PARENT
            "match_constraint" -> 0
            else -> values.dimension(value).toInt()
        }
        if (horizontal) params.width = size else params.height = size
        return true
    }

    /** One GridLayout cell as written so far; its specs are rebuilt from all of it after every attribute. */
    private class GridCell {
        var row = GridLayout.UNDEFINED
        var rowSpan = 1
        var rowWeight = 0f
        var column = GridLayout.UNDEFINED
        var columnSpan = 1
        var columnWeight = 0f
    }

    private val gridCells = WeakHashMap<View, GridCell>()

    private fun gridCell(view: View, params: ViewGroup.LayoutParams, change: (GridCell) -> Unit): Boolean {
        if (params !is GridLayout.LayoutParams) return false
        val cell = synchronized(gridCells) { gridCells.getOrPut(view) { GridCell() } }
        change(cell)
        params.rowSpec = GridLayout.spec(cell.row, cell.rowSpan, cell.rowWeight)
        params.columnSpec = GridLayout.spec(cell.column, cell.columnSpan, cell.columnWeight)
        return true
    }

    private fun String.lowercaseFirst() = replaceFirstChar { it.lowercaseChar() }
    private fun String.uppercaseFirst() = replaceFirstChar { it.uppercaseChar() }
}
