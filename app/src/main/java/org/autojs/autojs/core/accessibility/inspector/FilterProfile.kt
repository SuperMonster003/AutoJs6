package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.ActionFilter
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.filter.IntFilter
import org.autojs.autojs.core.automator.filter.MetricsFilter
import org.autojs.autojs.core.automator.filter.ScreenScale
import org.autojs.autojs.core.automator.filter.StringMatcher
import org.autojs.autojs.core.automator.filter.StringProperty
import org.autojs.autojs.core.automator.filter.StringPropertyFilter

/**
 * One filter configuration of the layout inspector (roadmap P3.1, D6 / D7): every dimension the
 * maintainer asked for, all optional, combined with AND. [toFilters] turns the profile into the
 * filters a [TreeMatch] evaluates (roadmap D10): registry-expressible conditions reuse the script
 * selector filters so that the export (D8) is their `toString()`; the rest ([ClassPresetFilter],
 * [ScreenIntersectFilter], [AnyOfFilter]) render a legal selector fragment or a `/* ... */` comment.
 *
 * The default profile ([DEFAULT], id [DEFAULT_ID]) has no condition and is never persisted; its
 * display name comes from a string resource, so [name] is empty for it.
 *
 * zh-CN: 布局分析的一份筛选配置 (Roadmap P3.1, D6 / D7): 维护者要求的全部维度, 均可选, 以 AND 组合. [toFilters] 把配置转为
 * [TreeMatch] 求值的过滤器 (Roadmap D10): 注册表能表达的条件复用脚本选择器过滤器, 导出 (D8) 即其 `toString()`; 其余
 * ([ClassPresetFilter], [ScreenIntersectFilter], [AnyOfFilter]) 输出合法选择器片段或 `/* ... */` 注释.
 * 默认配置 ([DEFAULT], id [DEFAULT_ID]) 不含任何条件且不持久化; 其显示名来自字符串资源, 因此 [name] 为空.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
data class FilterProfile(
    val id: String,
    val name: String,
    /** Tri-state booleans (D7 a); absent or [Tri.ANY] means unconstrained. zh-CN: 布尔三态 (D7 a); 缺失或 [Tri.ANY] 表示不限. */
    val booleans: Map<BooleanProperty, Tri> = emptyMap(),
    val requireId: Boolean = false,
    val requireText: Boolean = false,
    val requireDesc: Boolean = false,
    /** Class presets (D7 c, D18), OR between them. zh-CN: 类名预设 (D7 c, D18), 预设之间为 OR. */
    val classPresets: Set<ClassPreset> = emptySet(),
    val depth: IntRangeSpec = IntRangeSpec.ANY,
    val childCount: IntRangeSpec = IntRangeSpec.ANY,
    val indexInParent: IntRangeSpec = IntRangeSpec.ANY,
    /** Minimum width / height in pixels (D7 d). zh-CN: 最小宽 / 高, 像素 (D7 d). */
    val minWidth: Int? = null,
    val minHeight: Int? = null,
    /** Keep only nodes intersecting the screen (D7 d). zh-CN: 只保留与屏幕相交的节点 (D7 d). */
    val onScreenOnly: Boolean = false,
    /** Package names, OR between them (D7 e). zh-CN: 包名, 之间为 OR (D7 e). */
    val packages: Set<String> = emptySet(),
    /** Action names the node must all support (D7 e). zh-CN: 节点必须全部支持的动作名 (D7 e). */
    val actions: Set<String> = emptySet(),
) {

    enum class Tri { ANY, YES, NO }

    /** The boolean dimensions of D7 (a), each bound to the script selector's supplier. zh-CN: D7 (a) 的布尔维度, 各自绑定脚本选择器的取值器. */
    enum class BooleanProperty(val supplier: BooleanFilter.BooleanSupplier) {
        CLICKABLE(BooleanFilter.CLICKABLE),
        LONG_CLICKABLE(BooleanFilter.LONG_CLICKABLE),
        SCROLLABLE(BooleanFilter.SCROLLABLE),
        EDITABLE(BooleanFilter.EDITABLE),
        CHECKABLE(BooleanFilter.CHECKABLE),
        CHECKED(BooleanFilter.CHECKED),
        SELECTED(BooleanFilter.SELECTED),
        ENABLED(BooleanFilter.ENABLED),
        FOCUSABLE(BooleanFilter.FOCUSABLE),
        FOCUSED(BooleanFilter.FOCUSED),
        VISIBLE_TO_USER(BooleanFilter.VISIBLE_TO_USER),
        DISMISSABLE(BooleanFilter.DISMISSABLE),
        CONTEXT_CLICKABLE(BooleanFilter.CONTEXT_CLICKABLE),
        ACCESSIBILITY_FOCUSED(BooleanFilter.ACCESSIBILITY_FOCUSED);

        /** The selector property name, e.g. `clickable`. zh-CN: 选择器属性名, 如 `clickable`. */
        val label: String get() = supplier.toString()
    }

    /** A closed integer range with optional ends. zh-CN: 两端可空的整数闭区间. */
    data class IntRangeSpec(val min: Int? = null, val max: Int? = null) {

        val isAny: Boolean get() = min == null && max == null

        fun contains(value: Int): Boolean = (min == null || value >= min) && (max == null || value <= max)

        companion object {
            val ANY = IntRangeSpec()
        }
    }

    val isDefault: Boolean get() = id == DEFAULT_ID

    /** Whether no condition is set. zh-CN: 是否未设置任何条件. */
    val isEmpty: Boolean
        get() = booleans.values.all { it == Tri.ANY } && !requireId && !requireText && !requireDesc && classPresets.isEmpty()
            && depth.isAny && childCount.isAny && indexInParent.isAny && minWidth == null && minHeight == null
            && !onScreenOnly && packages.isEmpty() && actions.isEmpty()

    /** How many dimensions carry a condition; shown as the badge count. zh-CN: 设置了条件的维度数; 用作角标数字. */
    val conditionCount: Int
        get() = booleans.values.count { it != Tri.ANY } + listOf(requireId, requireText, requireDesc, onScreenOnly).count { it } +
            (if (classPresets.isEmpty()) 0 else 1) + listOf(depth, childCount, indexInParent).count { !it.isAny } +
            listOfNotNull(minWidth, minHeight).size + (if (packages.isEmpty()) 0 else 1) + (if (actions.isEmpty()) 0 else 1)

    /**
     * The AND list of filters; [scale] resolves the screen for the size and on-screen conditions
     * (tests pin it with [ScreenScale.Source.fixed]).
     * zh-CN: AND 过滤器列表; [scale] 为尺寸与屏幕内条件提供屏幕尺寸 (测试以 [ScreenScale.Source.fixed] 固定).
     */
    fun toFilters(scale: ScreenScale.Source = ScreenScale.Source.default): List<Filter> {
        val filters = ArrayList<Filter>()
        for (property in BooleanProperty.entries) {
            when (booleans[property] ?: Tri.ANY) {
                Tri.ANY -> Unit
                Tri.YES -> filters += BooleanFilter(property.supplier, true)
                Tri.NO -> filters += BooleanFilter(property.supplier, false)
            }
        }
        if (requireId) filters += StringPropertyFilter(StringProperty.ID, StringMatcher.match(ANY_CHAR))
        if (requireText) filters += StringPropertyFilter(StringProperty.TEXT, StringMatcher.match(ANY_CHAR))
        if (requireDesc) filters += StringPropertyFilter(StringProperty.DESC, StringMatcher.match(ANY_CHAR))
        if (classPresets.isNotEmpty()) filters += ClassPresetFilter(classPresets)
        intRange(filters, IntFilter.Property.DEPTH, depth)
        intRange(filters, IntFilter.Property.CHILD_COUNT, childCount)
        intRange(filters, IntFilter.Property.INDEX_IN_PARENT, indexInParent)
        minWidth?.let { filters += MetricsFilter(MetricsFilter.Property.WIDTH, MetricsFilter.Comparison.Min(it.toDouble()), scale = scale) }
        minHeight?.let { filters += MetricsFilter(MetricsFilter.Property.HEIGHT, MetricsFilter.Comparison.Min(it.toDouble()), scale = scale) }
        if (onScreenOnly) filters += ScreenIntersectFilter(scale)
        when (packages.size) {
            0 -> Unit
            1 -> filters += packageFilter(packages.first())
            else -> filters += AnyOfFilter(packages.sorted().map(::packageFilter))
        }
        if (actions.isNotEmpty()) filters += ActionFilter(actions.sorted().toTypedArray())
        return filters
    }

    private fun intRange(filters: MutableList<Filter>, property: IntFilter.Property, range: IntRangeSpec) {
        range.min?.let { filters += IntFilter(property, IntFilter.Comparison.Min(it)) }
        range.max?.let { filters += IntFilter(property, IntFilter.Comparison.Max(it)) }
    }

    private fun packageFilter(packageName: String): Filter = StringPropertyFilter(StringProperty.PACKAGE_NAME, StringMatcher.equals(packageName))

    companion object {

        const val DEFAULT_ID = "default"

        /** The built-in, condition-free profile (D6). zh-CN: 内置的无条件配置 (D6). */
        val DEFAULT = FilterProfile(DEFAULT_ID, "")

        /** `textMatch(".")`: any character present, i.e. a non-empty value. zh-CN: `textMatch(".")`: 存在任一字符, 即值非空. */
        private const val ANY_CHAR = "."

    }

}

/**
 * Nodes intersecting the screen rectangle (roadmap D7 d); not expressible by the registry, so it
 * renders as a comment for the export.
 * zh-CN: 与屏幕矩形相交的节点 (Roadmap D7 d); 注册表无法表达, 导出时输出为注释.
 */
class ScreenIntersectFilter(private val scale: ScreenScale.Source = ScreenScale.Source.default) : Filter {

    override fun filter(node: UiNode<*>): Boolean {
        val screen = scale.scale()
        return node.right() > 0 && node.bottom() > 0 && node.left() < screen.width && node.top() < screen.height
    }

    override fun toString() = "/* onScreen */"

}

/** OR of [filters]; rendered as a comment because chained selectors only express AND. zh-CN: [filters] 的 OR; 链式选择器只能表达 AND, 故输出为注释. */
class AnyOfFilter(val filters: List<Filter>) : Filter {

    override fun filter(node: UiNode<*>) = filters.any { it.filter(node) }

    override fun toString() = filters.joinToString(" | ", prefix = "/* ", postfix = " */")

}
