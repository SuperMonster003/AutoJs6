package org.autojs.autojs.core.automator.selector

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.ActionFilter
import org.autojs.autojs.core.automator.filter.AppFilter
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.filter.BoundsFilter
import org.autojs.autojs.core.automator.filter.ClassNameFilter
import org.autojs.autojs.core.automator.filter.ContentFilter
import org.autojs.autojs.core.automator.filter.DescFilter
import org.autojs.autojs.core.automator.filter.DoubleMinFilter
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.filter.IdFilter
import org.autojs.autojs.core.automator.filter.IdHexFilter
import org.autojs.autojs.core.automator.filter.IntFilter
import org.autojs.autojs.core.automator.filter.MaxIntFilter
import org.autojs.autojs.core.automator.filter.MetricsFilter
import org.autojs.autojs.core.automator.filter.MetricsMaxFilter
import org.autojs.autojs.core.automator.filter.MetricsMinFilter
import org.autojs.autojs.core.automator.filter.MetricsRangeFilter
import org.autojs.autojs.core.automator.filter.MinIntFilter
import org.autojs.autojs.core.automator.filter.PackageNameFilter
import org.autojs.autojs.core.automator.filter.TextFilter
import org.autojs.autojs.core.automator.filter.ToleranceFilter
import org.autojs.autojs.core.automator.selector.SelectorArgType.ACTIONS
import org.autojs.autojs.core.automator.selector.SelectorArgType.APP
import org.autojs.autojs.core.automator.selector.SelectorArgType.BOOLEAN
import org.autojs.autojs.core.automator.selector.SelectorArgType.DOUBLE
import org.autojs.autojs.core.automator.selector.SelectorArgType.FILTER
import org.autojs.autojs.core.automator.selector.SelectorArgType.INT
import org.autojs.autojs.core.automator.selector.SelectorArgType.REGEX
import org.autojs.autojs.core.automator.selector.SelectorArgType.STRING
import org.autojs.autojs.util.App
import org.mozilla.javascript.regexp.NativeRegExp

/**
 * The one description of every selector property: builder name, overloads (parameter names and
 * kinds), deprecation and filter factory. Everything else is derived from it: the generated
 * builder methods of `UiSelectorBuilders` (see `SelectorBuilderGenerator` in the unit tests, which
 * also detects drift), the map selector parser, the global selector functions of the JS runtime,
 * and later the d.ts signatures. Entries are plain data in resolution order; no reflection is
 * involved anywhere downstream.
 *
 * zh-CN: 每个选择器属性的唯一描述: builder 名称, 重载 (参数名与种类), 弃用信息与过滤器工厂. 其余一切由它派生:
 * `UiSelectorBuilders` 的生成 builder 方法 (见单元测试中的 `SelectorBuilderGenerator`, 它同时检测漂移), map 选择器解析器,
 * JS 运行时的全局选择器函数, 以及之后的 d.ts 签名. 条目是按解析顺序排列的纯数据; 下游不再有任何反射.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
object SelectorRegistry {

    enum class Outcome { APPLIED, NO_SUCH_PROPERTY, NO_MATCHING_OVERLOAD }

    val entries: List<SelectorEntry> = buildEntries()

    private val byName: Map<String, SelectorEntry> = entries.associateBy { it.name }

    val names: List<String> get() = entries.map { it.name }

    fun entry(name: String): SelectorEntry? = byName[name]

    operator fun contains(name: String): Boolean = name in byName

    /**
     * Applies property [name] with [args] to [selector]: the first overload accepting the
     * arguments builds the filter (or switches the algorithm). [lenient] parses strings for
     * numbers and booleans, the map selector convention.
     * zh-CN: 把属性 [name] 以 [args] 施加到 [selector]: 首个接受这些参数的重载构建过滤器 (或切换算法). [lenient] 时把字符串
     * 解析为数字与布尔值, 即 map 选择器的约定.
     */
    fun apply(selector: UiSelector, name: String, args: List<Any?>, lenient: Boolean = false): Outcome {
        val entry = byName[name] ?: return Outcome.NO_SUCH_PROPERTY
        val (overload, coerced) = entry.resolve(args, lenient) ?: return Outcome.NO_MATCHING_OVERLOAD
        overload.apply(selector, coerced)
        return Outcome.APPLIED
    }

    /* The table. zh-CN: 表. */

    private class EntryBuilder(val name: String) {

        val overloads = ArrayList<SelectorOverload>()

        fun overload(vararg params: SelectorParam, apply: (UiSelector, List<Any?>) -> Unit) {
            overloads += SelectorOverload(params.toList(), apply)
        }

        fun filter(vararg params: SelectorParam, factory: (List<Any?>) -> Filter) {
            overload(*params) { selector, args -> selector.addFilter(factory(args)) }
        }

    }

    private class Entries {

        val list = ArrayList<SelectorEntry>()

        fun entry(name: String, deprecatedFor: String? = null, getter: ((UiNode<*>) -> Any?)? = null, build: EntryBuilder.() -> Unit) {
            val builder = EntryBuilder(name).apply(build)
            require(builder.overloads.isNotEmpty()) { "Selector property $name has no overload" }
            list += SelectorEntry(name, builder.overloads, deprecatedFor, getter)
        }

    }

    private fun string(name: String) = SelectorParam(name, STRING)
    private fun regex(name: String = "regex") = SelectorParam(name, REGEX)
    private fun boolean(name: String = "b") = SelectorParam(name, BOOLEAN)
    private fun int(name: String) = SelectorParam(name, INT)
    private fun double(name: String, range: ClosedFloatingPointRange<Double>? = null) = SelectorParam(name, DOUBLE, range)
    private fun app(name: String = "app") = SelectorParam(name, APP)

    @Suppress("UNCHECKED_CAST")
    private fun <A> EntryBuilder.f1(a: SelectorParam, factory: (A) -> Filter) = filter(a) { factory(it[0] as A) }

    @Suppress("UNCHECKED_CAST")
    private fun <A, B> EntryBuilder.f2(a: SelectorParam, b: SelectorParam, factory: (A, B) -> Filter) = filter(a, b) { factory(it[0] as A, it[1] as B) }

    private fun EntryBuilder.f4(a: SelectorParam, b: SelectorParam, c: SelectorParam, d: SelectorParam, factory: (Double, Double, Double, Double) -> Filter) =
        filter(a, b, c, d) { factory(it[0] as Double, it[1] as Double, it[2] as Double, it[3] as Double) }

    private fun EntryBuilder.f0(factory: () -> Filter) = filter { factory() }

    /**
     * The six builders of a string property (`text`, `textStartsWith`, `textEndsWith`, `textContains`,
     * `textMatches` (deprecated for `textMatch`), `textMatch`) over the filter object of that property.
     * zh-CN: 字符串属性的六个 builder, 建立在该属性的过滤器对象之上.
     */
    private class StringFamily(
        val equals: (String) -> Filter,
        val equalsRegex: (NativeRegExp) -> Filter,
        val startsWith: (String) -> Filter,
        val endsWith: (String) -> Filter,
        val contains: (String) -> Filter,
        val containsRegex: (NativeRegExp) -> Filter,
        val matches: (String) -> Filter,
        val matchesRegex: (NativeRegExp) -> Filter,
        val match: (String) -> Filter,
        val matchRegex: (NativeRegExp) -> Filter,
    )

    private fun Entries.stringFamily(name: String, getter: (UiNode<*>) -> Any?, family: StringFamily, extra: EntryBuilder.() -> Unit = {}) {
        entry(name, getter = getter) {
            f1(string("s"), family.equals)
            f1(regex(), family.equalsRegex)
            extra()
        }
        entry("${name}StartsWith", getter = getter) { f1(string("prefix"), family.startsWith) }
        entry("${name}EndsWith", getter = getter) { f1(string("suffix"), family.endsWith) }
        entry("${name}Contains", getter = getter) {
            f1(string("s"), family.contains)
            f1(regex(), family.containsRegex)
        }
        entry("${name}Matches", deprecatedFor = "${name}Match", getter = getter) {
            f1(string("s"), family.matches)
            f1(regex(), family.matchesRegex)
        }
        entry("${name}Match", getter = getter) {
            f1(string("s"), family.match)
            f1(regex(), family.matchRegex)
        }
    }

    /** One of the eight screen metrics with its four filter property objects, in both spellings. zh-CN: 八个屏幕度量之一及其四种过滤器属性对象, 两种拼写. */
    private class Metric(
        val name: String,
        val capitalized: String,
        val getter: (UiNode<*>) -> Int,
        val equals: MetricsFilter.MetricsProperty,
        val range: MetricsRangeFilter.IntRangeProperty,
        val min: MetricsMinFilter.IntMinProperty,
        val max: MetricsMaxFilter.IntMaxProperty,
        val boundsEquals: MetricsFilter.MetricsProperty,
        val boundsRange: MetricsRangeFilter.IntRangeProperty,
        val boundsMin: MetricsMinFilter.IntMinProperty,
        val boundsMax: MetricsMaxFilter.IntMaxProperty,
    )

    private fun metrics() = listOf(
        Metric("left", "Left", { it.left() }, MetricsFilter.LEFT, MetricsRangeFilter.LEFT, MetricsMinFilter.LEFT, MetricsMaxFilter.LEFT, MetricsFilter.BOUNDS_LEFT, MetricsRangeFilter.BOUNDS_LEFT, MetricsMinFilter.BOUNDS_LEFT, MetricsMaxFilter.BOUNDS_LEFT),
        Metric("top", "Top", { it.top() }, MetricsFilter.TOP, MetricsRangeFilter.TOP, MetricsMinFilter.TOP, MetricsMaxFilter.TOP, MetricsFilter.BOUNDS_TOP, MetricsRangeFilter.BOUNDS_TOP, MetricsMinFilter.BOUNDS_TOP, MetricsMaxFilter.BOUNDS_TOP),
        Metric("right", "Right", { it.right() }, MetricsFilter.RIGHT, MetricsRangeFilter.RIGHT, MetricsMinFilter.RIGHT, MetricsMaxFilter.RIGHT, MetricsFilter.BOUNDS_RIGHT, MetricsRangeFilter.BOUNDS_RIGHT, MetricsMinFilter.BOUNDS_RIGHT, MetricsMaxFilter.BOUNDS_RIGHT),
        Metric("bottom", "Bottom", { it.bottom() }, MetricsFilter.BOTTOM, MetricsRangeFilter.BOTTOM, MetricsMinFilter.BOTTOM, MetricsMaxFilter.BOTTOM, MetricsFilter.BOUNDS_BOTTOM, MetricsRangeFilter.BOUNDS_BOTTOM, MetricsMinFilter.BOUNDS_BOTTOM, MetricsMaxFilter.BOUNDS_BOTTOM),
        Metric("width", "Width", { it.width() }, MetricsFilter.WIDTH, MetricsRangeFilter.WIDTH, MetricsMinFilter.WIDTH, MetricsMaxFilter.WIDTH, MetricsFilter.BOUNDS_WIDTH, MetricsRangeFilter.BOUNDS_WIDTH, MetricsMinFilter.BOUNDS_WIDTH, MetricsMaxFilter.BOUNDS_WIDTH),
        Metric("height", "Height", { it.height() }, MetricsFilter.HEIGHT, MetricsRangeFilter.HEIGHT, MetricsMinFilter.HEIGHT, MetricsMaxFilter.HEIGHT, MetricsFilter.BOUNDS_HEIGHT, MetricsRangeFilter.BOUNDS_HEIGHT, MetricsMinFilter.BOUNDS_HEIGHT, MetricsMaxFilter.BOUNDS_HEIGHT),
        Metric("centerX", "CenterX", { it.centerX() }, MetricsFilter.CENTER_X, MetricsRangeFilter.CENTER_X, MetricsMinFilter.CENTER_X, MetricsMaxFilter.CENTER_X, MetricsFilter.BOUNDS_CENTER_X, MetricsRangeFilter.BOUNDS_CENTER_X, MetricsMinFilter.BOUNDS_CENTER_X, MetricsMaxFilter.BOUNDS_CENTER_X),
        Metric("centerY", "CenterY", { it.centerY() }, MetricsFilter.CENTER_Y, MetricsRangeFilter.CENTER_Y, MetricsMinFilter.CENTER_Y, MetricsMaxFilter.CENTER_Y, MetricsFilter.BOUNDS_CENTER_Y, MetricsRangeFilter.BOUNDS_CENTER_Y, MetricsMinFilter.BOUNDS_CENTER_Y, MetricsMaxFilter.BOUNDS_CENTER_Y),
    )

    private fun Entries.booleanProperty(name: String, supplier: BooleanFilter.BooleanSupplier, noArgFirst: Boolean = false) {
        entry(name, getter = { supplier[it] }) {
            if (noArgFirst) f0 { BooleanFilter(supplier) }
            f1<Boolean>(boolean()) { BooleanFilter(supplier, it) }
            if (!noArgFirst) f0 { BooleanFilter(supplier) }
        }
    }

    private fun Entries.intProperty(name: String, paramName: String, property: IntFilter.IntProperty) {
        entry(name, getter = { property[it] }) { f1<Int>(int(paramName)) { IntFilter(property, it) } }
    }

    private fun buildEntries(): List<SelectorEntry> = Entries().apply {
        stringFamily(
            "id", { it.id() }, StringFamily(
                { IdFilter.equals(it) }, { IdFilter.equals(it) },
                { IdFilter.startsWith(it) }, { IdFilter.endsWith(it) },
                { IdFilter.contains(it) }, { IdFilter.contains(it) },
                { IdFilter.matches(it) }, { IdFilter.matches(it) },
                { IdFilter.match(it) }, { IdFilter.match(it) },
            ),
        )
        entry("idHex", getter = { it.idHex() }) { f1<String>(string("str")) { IdHexFilter.equals(it) } }
        stringFamily(
            "text", { it.text() }, StringFamily(
                { TextFilter.equals(it) }, { TextFilter.equals(it) },
                { TextFilter.startsWith(it) }, { TextFilter.endsWith(it) },
                { TextFilter.contains(it) }, { TextFilter.contains(it) },
                { TextFilter.matches(it) }, { TextFilter.matches(it) },
                { TextFilter.match(it) }, { TextFilter.match(it) },
            ),
        )
        stringFamily(
            "desc", { it.desc() }, StringFamily(
                { DescFilter.equals(it) }, { DescFilter.equals(it) },
                { DescFilter.startsWith(it) }, { DescFilter.endsWith(it) },
                { DescFilter.contains(it) }, { DescFilter.contains(it) },
                { DescFilter.matches(it) }, { DescFilter.matches(it) },
                { DescFilter.match(it) }, { DescFilter.match(it) },
            ),
        )
        stringFamily(
            "content", { it.content() }, StringFamily(
                { ContentFilter.equals(it) }, { ContentFilter.equals(it) },
                { ContentFilter.startsWith(it) }, { ContentFilter.endsWith(it) },
                { ContentFilter.contains(it) }, { ContentFilter.contains(it) },
                { ContentFilter.matches(it) }, { ContentFilter.matches(it) },
                { ContentFilter.match(it) }, { ContentFilter.match(it) },
            ),
        )
        stringFamily(
            "className", { it.className() }, StringFamily(
                { ClassNameFilter.equals(it) }, { ClassNameFilter.equals(it) },
                { ClassNameFilter.startsWith(it) }, { ClassNameFilter.endsWith(it) },
                { ClassNameFilter.contains(it) }, { ClassNameFilter.contains(it) },
                { ClassNameFilter.matches(it) }, { ClassNameFilter.matches(it) },
                { ClassNameFilter.match(it) }, { ClassNameFilter.match(it) },
            ),
        )
        stringFamily(
            "packageName", { it.packageName() }, StringFamily(
                { PackageNameFilter.equals(it) }, { PackageNameFilter.equals(it) },
                { PackageNameFilter.startsWith(it) }, { PackageNameFilter.endsWith(it) },
                { PackageNameFilter.contains(it) }, { PackageNameFilter.contains(it) },
                { PackageNameFilter.matches(it) }, { PackageNameFilter.matches(it) },
                { PackageNameFilter.match(it) }, { PackageNameFilter.match(it) },
            ),
        ) {
            f1<App>(app()) { PackageNameFilter.equals(it.packageName) }
        }
        entry("currentApp", getter = { it.packageName() }) {
            f1<App>(app()) { AppFilter(it) }
            f1<String>(string("name")) { AppFilter(it) }
        }

        entry("bounds") { f4(double("l"), double("t"), double("r"), double("b")) { l, t, r, b -> BoundsFilter(l, t, r, b, BoundsFilter.TYPE_EQUALS) } }
        entry("boundsInside") { f4(double("l"), double("t"), double("r"), double("b")) { l, t, r, b -> BoundsFilter(l, t, r, b, BoundsFilter.TYPE_INSIDE) } }
        entry("boundsContains") { f4(double("l"), double("t"), double("r"), double("b")) { l, t, r, b -> BoundsFilter(l, t, r, b, BoundsFilter.TYPE_CONTAINS) } }
        for (m in metrics()) {
            entry("bounds${m.capitalized}", getter = m.getter) {
                f1<Double>(double("value")) { MetricsFilter(m.boundsEquals, it) }
                f2<Double, Double>(double("min"), double("max")) { min, max -> MetricsRangeFilter(m.boundsRange, min, max) }
            }
        }
        for (m in metrics()) {
            entry("boundsMin${m.capitalized}", getter = m.getter) { f1<Double>(double("min")) { MetricsMinFilter(m.boundsMin, it) } }
        }
        for (m in metrics()) {
            entry("boundsMax${m.capitalized}", getter = m.getter) { f1<Double>(double("max")) { MetricsMaxFilter(m.boundsMax, it) } }
        }
        for (m in metrics()) {
            entry(m.name, getter = m.getter) {
                f1<Double>(double("value")) { MetricsFilter(m.equals, it) }
                f2<Double, Double>(double("min"), double("max")) { min, max -> MetricsRangeFilter(m.range, min, max) }
            }
        }
        for (m in metrics()) {
            entry("min${m.capitalized}", getter = m.getter) { f1<Double>(double("value")) { MetricsMinFilter(m.min, it) } }
        }
        for (m in metrics()) {
            entry("max${m.capitalized}", getter = m.getter) { f1<Double>(double("value")) { MetricsMaxFilter(m.max, it) } }
        }
        entry("screenCenterX", getter = { it.centerX() }) {
            f2<Boolean, Double>(boolean(), double("tolerance", 0.0..0.5)) { b, tolerance -> ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X, b, tolerance) }
            f1<Boolean>(boolean()) { ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X, it) }
            f1<Double>(double("tolerance", 0.0..0.5)) { ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X, null, it) }
            f0 { ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X) }
        }
        entry("screenCenterY", getter = { it.centerY() }) {
            f2<Boolean, Double>(boolean(), double("tolerance", 0.0..0.5)) { b, tolerance -> ToleranceFilter(ToleranceFilter.SCREEN_CENTER_Y, b, tolerance) }
            f1<Boolean>(boolean()) { ToleranceFilter(ToleranceFilter.SCREEN_CENTER_Y, it) }
            f1<Double>(double("tolerance", 0.0..0.5)) { ToleranceFilter(ToleranceFilter.SCREEN_CENTER_Y, null, it) }
            f0 { ToleranceFilter(ToleranceFilter.SCREEN_CENTER_Y) }
        }
        entry("screenCoverage") {
            f1<Double>(double("min", 0.0..1.0)) { DoubleMinFilter(DoubleMinFilter.SCREEN_COVERAGE, it) }
            f0 { DoubleMinFilter(DoubleMinFilter.SCREEN_COVERAGE) }
        }

        entry("algorithm") { overload(string("str")) { selector, args -> selector.useAlgorithm(args[0] as String) } }
        entry("action", getter = { it.actionNames() }) { filter(SelectorParam("actions", ACTIONS)) { ActionFilter(it.filterNotNull().toTypedArray()) } }
        entry("filter") {
            f1<BooleanFilter.BooleanSupplier>(SelectorParam("filter", FILTER)) { supplier ->
                object : Filter {
                    override fun filter(node: UiNode<*>) = supplier[node]
                    override fun toString() = "filter()"
                }
            }
        }

        booleanProperty("hasChildren", BooleanFilter.HAS_CHILDREN, noArgFirst = true)
        booleanProperty("checkable", BooleanFilter.CHECKABLE)
        booleanProperty("checked", BooleanFilter.CHECKED)
        booleanProperty("focusable", BooleanFilter.FOCUSABLE)
        booleanProperty("focused", BooleanFilter.FOCUSED)
        booleanProperty("visibleToUser", BooleanFilter.VISIBLE_TO_USER)
        booleanProperty("accessibilityFocused", BooleanFilter.ACCESSIBILITY_FOCUSED)
        booleanProperty("selected", BooleanFilter.SELECTED)
        booleanProperty("clickable", BooleanFilter.CLICKABLE)
        booleanProperty("longClickable", BooleanFilter.LONG_CLICKABLE)
        booleanProperty("enabled", BooleanFilter.ENABLED)
        booleanProperty("password", BooleanFilter.PASSWORD)
        booleanProperty("scrollable", BooleanFilter.SCROLLABLE)
        booleanProperty("editable", BooleanFilter.EDITABLE)
        booleanProperty("contentInvalid", BooleanFilter.CONTENT_INVALID)
        booleanProperty("contextClickable", BooleanFilter.CONTEXT_CLICKABLE)
        booleanProperty("multiLine", BooleanFilter.MULTI_LINE)
        booleanProperty("dismissable", BooleanFilter.DISMISSABLE)

        intProperty("depth", "d", IntFilter.DEPTH)
        intProperty("row", "d", IntFilter.ROW)
        intProperty("rowCount", "d", IntFilter.ROW_COUNT)
        intProperty("rowSpan", "d", IntFilter.ROW_SPAN)
        intProperty("column", "d", IntFilter.COLUMN)
        intProperty("columnCount", "d", IntFilter.COLUMN_COUNT)
        intProperty("columnSpan", "d", IntFilter.COLUMN_SPAN)
        intProperty("drawingOrder", "order", IntFilter.DRAWING_ORDER)
        intProperty("indexInParent", "index", IntFilter.INDEX_IN_PARENT)
        intProperty("childCount", "count", IntFilter.CHILD_COUNT)
        entry("minChildCount", getter = { it.childCount() }) { f1<Int>(int("min")) { MinIntFilter(MinIntFilter.CHILD_COUNT, it) } }
        entry("maxChildCount", getter = { it.childCount() }) { f1<Int>(int("max")) { MaxIntFilter(MaxIntFilter.CHILD_COUNT, it) } }
    }.list

}
