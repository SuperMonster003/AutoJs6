package org.autojs.autojs.core.automator.selector

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.ActionFilter
import org.autojs.autojs.core.automator.filter.AppFilter
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.filter.BoundsFilter
import org.autojs.autojs.core.automator.filter.ClassNameFilter
import org.autojs.autojs.core.automator.filter.DoubleMinFilter
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.filter.IdFilter
import org.autojs.autojs.core.automator.filter.IntFilter
import org.autojs.autojs.core.automator.filter.MetricsFilter
import org.autojs.autojs.core.automator.filter.MetricsFilter.Spelling
import org.autojs.autojs.core.automator.filter.NormalizeFilter
import org.autojs.autojs.core.automator.filter.NormalizeOptions
import org.autojs.autojs.core.automator.filter.Preference
import org.autojs.autojs.core.automator.filter.PreferFilter
import org.autojs.autojs.core.automator.filter.StringMatcher
import org.autojs.autojs.core.automator.filter.StringProperty
import org.autojs.autojs.core.automator.filter.StringPropertyFilter
import org.autojs.autojs.core.automator.filter.ToleranceFilter
import org.autojs.autojs.core.automator.selector.SelectorArgType.ACTIONS
import org.autojs.autojs.core.automator.selector.SelectorArgType.APP
import org.autojs.autojs.core.automator.selector.SelectorArgType.BOOLEAN
import org.autojs.autojs.core.automator.selector.SelectorArgType.DOUBLE
import org.autojs.autojs.core.automator.selector.SelectorArgType.FILTER
import org.autojs.autojs.core.automator.selector.SelectorArgType.INT
import org.autojs.autojs.core.automator.selector.SelectorArgType.KEYS
import org.autojs.autojs.core.automator.selector.SelectorArgType.OPTIONS
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

    /** [names] prepared once for the candidates of an unknown name (docs/augment/ROADMAP.md appendix C Q141). zh-CN: 为未知名称的候选预处理一次的 [names] (augment 附录 C Q141). */
    val suggestions: SelectorStringParser.Suggestions by lazy { SelectorStringParser.Suggestions(names) }

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
        return when (val resolution = entry.resolve(args, lenient)) {
            is SelectorResolution.Resolved -> Outcome.APPLIED.also { resolution.overload.apply(selector, resolution.arguments) }
            SelectorResolution.NoOverload -> Outcome.NO_MATCHING_OVERLOAD
            // The exceptions building the condition threw before the refusals were told first (rounding NaN, UiSelector.useAlgorithm).
            // zh-CN: 先行判定之前构建条件时抛出的异常 (NaN 取整, UiSelector.useAlgorithm).
            is SelectorResolution.Rejected -> throw IllegalArgumentException(resolution.reason)
        }
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
     * `textMatches` (deprecated for `textMatch`), `textMatch`), each a [StringPropertyFilter] over
     * [property]; `id` and `className` replace the plain-string `equals` / `startsWith` factories with
     * their argument rules.
     * zh-CN: 字符串属性的六个 builder, 每个都是 [property] 上的 [StringPropertyFilter]; `id` 与 `className` 以各自的参数规则
     * 替换普通字符串的 `equals` / `startsWith` 工厂.
     */
    private fun Entries.stringFamily(
        name: String,
        property: StringProperty,
        getter: (UiNode<*>) -> Any?,
        equals: (String) -> Filter = { StringPropertyFilter(property, StringMatcher.equals(it)) },
        startsWith: (String) -> Filter = { StringPropertyFilter(property, StringMatcher.startsWith(it)) },
        extra: EntryBuilder.() -> Unit = {},
    ) {
        entry(name, getter = getter) {
            f1(string("s"), equals)
            f1<NativeRegExp>(regex()) { StringPropertyFilter(property, StringMatcher.equals(it)) }
            extra()
        }
        entry("${name}StartsWith", getter = getter) { f1(string("prefix"), startsWith) }
        entry("${name}EndsWith", getter = getter) { f1<String>(string("suffix")) { StringPropertyFilter(property, StringMatcher.endsWith(it)) } }
        entry("${name}Contains", getter = getter) {
            f1<String>(string("s")) { StringPropertyFilter(property, StringMatcher.contains(it)) }
            f1<NativeRegExp>(regex()) { StringPropertyFilter(property, StringMatcher.contains(it)) }
        }
        entry("${name}Matches", deprecatedFor = "${name}Match", getter = getter) {
            f1<String>(string("s")) { StringPropertyFilter(property, StringMatcher.matches(it)) }
            f1<NativeRegExp>(regex()) { StringPropertyFilter(property, StringMatcher.matches(it)) }
        }
        entry("${name}Match", getter = getter) {
            f1<String>(string("s")) { StringPropertyFilter(property, StringMatcher.match(it)) }
            f1<NativeRegExp>(regex()) { StringPropertyFilter(property, StringMatcher.match(it)) }
        }
    }

    /** `left(value)` / `left(min, max)` or `boundsLeft(...)`, per [MetricsFilter.Property]. zh-CN: 按 [MetricsFilter.Property] 生成 `left(value)` / `left(min, max)` 或 `boundsLeft(...)`. */
    private fun Entries.metric(m: MetricsFilter.Property, spelling: Spelling) {
        val name = if (spelling == Spelling.PLAIN) m.toString() else "bounds${m.capitalized}"
        entry(name, getter = { m[it] }) {
            f1<Double>(double("value")) { MetricsFilter(m, MetricsFilter.Comparison.Equals(it), spelling) }
            f2<Double, Double>(double("min"), double("max")) { min, max -> MetricsFilter(m, MetricsFilter.Comparison.Range(min, max), spelling) }
        }
    }

    /** `minLeft(value)` or `boundsMinLeft(min)`. zh-CN: `minLeft(value)` 或 `boundsMinLeft(min)`. */
    private fun Entries.metricMin(m: MetricsFilter.Property, spelling: Spelling) {
        val (name, param) = if (spelling == Spelling.PLAIN) "min${m.capitalized}" to "value" else "boundsMin${m.capitalized}" to "min"
        entry(name, getter = { m[it] }) { f1<Double>(double(param)) { MetricsFilter(m, MetricsFilter.Comparison.Min(it), spelling) } }
    }

    /** `maxLeft(value)` or `boundsMaxLeft(max)`. zh-CN: `maxLeft(value)` 或 `boundsMaxLeft(max)`. */
    private fun Entries.metricMax(m: MetricsFilter.Property, spelling: Spelling) {
        val (name, param) = if (spelling == Spelling.PLAIN) "max${m.capitalized}" to "value" else "boundsMax${m.capitalized}" to "max"
        entry(name, getter = { m[it] }) { f1<Double>(double(param)) { MetricsFilter(m, MetricsFilter.Comparison.Max(it), spelling) } }
    }

    private fun Entries.booleanProperty(name: String, supplier: BooleanFilter.BooleanSupplier, noArgFirst: Boolean = false) {
        entry(name, getter = { supplier[it] }) {
            if (noArgFirst) f0 { BooleanFilter(supplier) }
            f1<Boolean>(boolean()) { BooleanFilter(supplier, it) }
            if (!noArgFirst) f0 { BooleanFilter(supplier) }
        }
    }

    private fun Entries.intProperty(name: String, paramName: String, property: IntFilter.Property) {
        entry(name, getter = { property[it] }) { f1<Int>(int(paramName)) { IntFilter(property, it) } }
    }

    /**
     * `minX(min)` / `maxX(max)`: the inclusive lower and upper bound of an integer property, so a
     * range reads `minDepth(3).maxDepth(5)` (layout inspector roadmap D19).
     * zh-CN: `minX(min)` / `maxX(max)`: 整数属性的闭区间下界与上界, 区间写作 `minDepth(3).maxDepth(5)` (布局分析 Roadmap D19).
     */
    private fun Entries.intBounds(property: IntFilter.Property) {
        entry("min${property.capitalized}", getter = { property[it] }) { f1<Int>(int("min")) { IntFilter(property, IntFilter.Comparison.Min(it)) } }
        entry("max${property.capitalized}", getter = { property[it] }) { f1<Int>(int("max")) { IntFilter(property, IntFilter.Comparison.Max(it)) } }
    }

    private fun buildEntries(): List<SelectorEntry> = Entries().apply {
        stringFamily("id", StringProperty.ID, { it.id() }, equals = { IdFilter.equals(it) }, startsWith = { IdFilter.startsWith(it) })
        entry("idHex", getter = { it.idHex() }) { f1<String>(string("str")) { StringPropertyFilter(StringProperty.ID_HEX, StringMatcher.equals(it)) } }
        stringFamily("text", StringProperty.TEXT, { it.text() })
        stringFamily("desc", StringProperty.DESC, { it.desc() })
        stringFamily("content", StringProperty.CONTENT, { it.content() })
        stringFamily("className", StringProperty.CLASS_NAME, { it.className() }, equals = { ClassNameFilter.equals(it) }, startsWith = { ClassNameFilter.startsWith(it) })
        stringFamily("packageName", StringProperty.PACKAGE_NAME, { it.packageName() }) {
            f1<App>(app()) { StringPropertyFilter(StringProperty.PACKAGE_NAME, StringMatcher.equals(it.packageName)) }
        }
        entry("currentApp", getter = { it.packageName() }) {
            f1<App>(app()) { AppFilter(it) }
            f1<String>(string("name")) { AppFilter(it) }
        }

        entry("bounds") { f4(double("l"), double("t"), double("r"), double("b")) { l, t, r, b -> BoundsFilter(l, t, r, b, BoundsFilter.TYPE_EQUALS) } }
        entry("boundsInside") { f4(double("l"), double("t"), double("r"), double("b")) { l, t, r, b -> BoundsFilter(l, t, r, b, BoundsFilter.TYPE_INSIDE) } }
        entry("boundsContains") { f4(double("l"), double("t"), double("r"), double("b")) { l, t, r, b -> BoundsFilter(l, t, r, b, BoundsFilter.TYPE_CONTAINS) } }
        for (m in MetricsFilter.Property.entries) metric(m, Spelling.BOUNDS)
        for (m in MetricsFilter.Property.entries) metricMin(m, Spelling.BOUNDS)
        for (m in MetricsFilter.Property.entries) metricMax(m, Spelling.BOUNDS)
        for (m in MetricsFilter.Property.entries) metric(m, Spelling.PLAIN)
        for (m in MetricsFilter.Property.entries) metricMin(m, Spelling.PLAIN)
        for (m in MetricsFilter.Property.entries) metricMax(m, Spelling.PLAIN)
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
        // Settings of the selector rather than conditions (docs/selector/ROADMAP.md P3.3 / P3.4): each passes every node,
        // and the last one of each kind applies to the whole selector (Selector.effectiveFilters, Selector.preference).
        // zh-CN: 选择器的设置而非条件 (P3.3 / P3.4): 各自接受每个节点, 每种取最后一个作用于整个选择器.
        entry("normalize") {
            f0 { NormalizeFilter(NormalizeOptions.ALL) }
            f1<Boolean>(boolean()) { NormalizeFilter(if (it) NormalizeOptions.ALL else NormalizeOptions.NONE) }
            f1<Map<*, *>>(SelectorParam("options", OPTIONS)) { NormalizeFilter(NormalizeOptions.of(it)!!) }
        }
        entry("prefer") {
            filter(SelectorParam("keys", KEYS)) { keys -> PreferFilter(Preference(keys.map { Preference.Key.of(it as String)!! })) }
        }
        entry("action", getter = { it.actionNames() }) { filter(SelectorParam("actions", ACTIONS)) { ActionFilter(it.filterNotNull().toTypedArray()) } }
        entry("filter") {
            f1<BooleanFilter.BooleanSupplier>(SelectorParam("filter", FILTER)) { supplier ->
                object : Filter {
                    override fun filter(node: UiNode<*>) = supplier[node]
                    override val cost get() = Filter.COST_SCRIPT
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

        intProperty("depth", "d", IntFilter.Property.DEPTH)
        intProperty("row", "d", IntFilter.Property.ROW)
        intProperty("rowCount", "d", IntFilter.Property.ROW_COUNT)
        intProperty("rowSpan", "d", IntFilter.Property.ROW_SPAN)
        intProperty("column", "d", IntFilter.Property.COLUMN)
        intProperty("columnCount", "d", IntFilter.Property.COLUMN_COUNT)
        intProperty("columnSpan", "d", IntFilter.Property.COLUMN_SPAN)
        intProperty("drawingOrder", "order", IntFilter.Property.DRAWING_ORDER)
        intProperty("indexInParent", "index", IntFilter.Property.INDEX_IN_PARENT)
        intProperty("childCount", "count", IntFilter.Property.CHILD_COUNT)
        intBounds(IntFilter.Property.CHILD_COUNT)
        intBounds(IntFilter.Property.DEPTH)
        intBounds(IntFilter.Property.INDEX_IN_PARENT)
    }.list

}
