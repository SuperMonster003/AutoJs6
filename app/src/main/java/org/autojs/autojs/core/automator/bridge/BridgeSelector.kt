package org.autojs.autojs.core.automator.bridge

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.filter.IntFilter
import org.autojs.autojs.core.automator.filter.Selector
import org.autojs.autojs.core.automator.filter.StringMatcher
import org.autojs.autojs.core.automator.filter.StringProperty
import org.autojs.autojs.core.automator.filter.StringPropertyFilter

/**
 * A screen rectangle of the Node Bridge's frozen `accessibility` protocol, in device pixels as the
 * platform reports them (no normalisation, an inverted rectangle stays inverted).
 * zh-CN: Node Bridge 冻结的 `accessibility` 协议中的屏幕矩形, 为平台报告的设备像素 (不规范化, 颠倒的矩形保持颠倒).
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
data class BridgeBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {

    companion object {

        fun of(node: UiNode<*>) = BridgeBounds(node.left(), node.top(), node.right(), node.bottom())

    }

}

/**
 * The selector descriptor of the Node Bridge's frozen `accessibility` protocol (roadmap P5.4) as
 * plain data: the Android side parses the JSON descriptor into it (blank strings count as absent,
 * a regex carries its `i` / `m` / `s` flags), [toFilter] turns it into the script runtime's filters.
 * The semantics are the ones the protocol froze:
 *
 * - every present condition must hold (AND), and a descriptor without any condition matches nothing;
 * - `text` / `desc` / `id` / `className` compare literally (no short-id or short-class-name
 *   expansion), the `Contains` forms as substrings, the `Matches` forms as a whole-value regex
 *   match in which a node without the value counts as having the empty string (the registry's
 *   `desc` / `id` / `className` properties never match a missing value, so the regex conditions
 *   are the bridge's own [BridgeRegexFilter]);
 * - `clickable` / `enabled` / `scrollable` compare booleans, `depth` is the level below the
 *   searched root (0 for the root), `boundsInside` / `boundsContains` compare device pixels
 *   without the screen-relative scaling of the registry's `bounds*` filters ([BridgeBoundsFilter]).
 *
 * zh-CN: Node Bridge 冻结的 `accessibility` 协议的选择器描述符 (Roadmap P5.4), 以普通数据表示: Android 侧把 JSON 描述符解析为它
 * (空白字符串视为缺席, 正则带 `i` / `m` / `s` 标志), [toFilter] 把它变为脚本运行时的过滤器. 语义即协议冻结的语义: 每个给出的条件都须成立
 * (AND), 没有任何条件的描述符不匹配任何节点; `text` / `desc` / `id` / `className` 按字面比较 (不做短 id / 短类名展开), `Contains` 形式为
 * 子串, `Matches` 形式为整值正则匹配且没有该值的节点按空串计 (registry 的 `desc` / `id` / `className` 属性对缺失值一律不匹配, 因此正则条件
 * 是 bridge 自己的 [BridgeRegexFilter]); `clickable` / `enabled` / `scrollable` 比较布尔值, `depth` 为距被搜索根节点的层数 (根为 0),
 * `boundsInside` / `boundsContains` 比较设备像素, 不做 registry 的 `bounds*` 过滤器的屏幕比例换算 ([BridgeBoundsFilter]).
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
data class BridgeSelector(
    val text: String? = null,
    val textContains: String? = null,
    val textMatches: Regex? = null,
    val desc: String? = null,
    val descContains: String? = null,
    val descMatches: Regex? = null,
    val id: String? = null,
    val idMatches: Regex? = null,
    val className: String? = null,
    val classNameMatches: Regex? = null,
    val clickable: Boolean? = null,
    val enabled: Boolean? = null,
    val scrollable: Boolean? = null,
    val depth: Int? = null,
    val boundsInside: BridgeBounds? = null,
    val boundsContains: BridgeBounds? = null,
) {

    /** Whether no condition is present; such a selector matches nothing. zh-CN: 是否没有任何条件; 这样的选择器不匹配任何节点. */
    val isEmpty: Boolean
        get() = listOf(
            text, textContains, textMatches,
            desc, descContains, descMatches,
            id, idMatches,
            className, classNameMatches,
            clickable, enabled, scrollable, depth,
            boundsInside, boundsContains,
        ).all { it == null }

    /**
     * One filter per present condition, in protocol order (the steps `accessibility.explain`
     * reports); empty for an empty descriptor.
     * zh-CN: 每个给出的条件一个过滤器, 按协议顺序 (即 `accessibility.explain` 报告的步骤); 空描述符为空列表.
     */
    fun toFilters(): List<Filter> = buildList {
            text?.let { add(StringPropertyFilter(StringProperty.TEXT, StringMatcher.equals(it))) }
            textContains?.let { add(StringPropertyFilter(StringProperty.TEXT, StringMatcher.contains(it))) }
            textMatches?.let { add(BridgeRegexFilter("textMatches", it) { node -> node.text() }) }
            desc?.let { add(StringPropertyFilter(StringProperty.DESC, StringMatcher.equals(it))) }
            descContains?.let { add(StringPropertyFilter(StringProperty.DESC, StringMatcher.contains(it))) }
            descMatches?.let { add(BridgeRegexFilter("descMatches", it) { node -> node.desc() }) }
            id?.let { add(StringPropertyFilter(StringProperty.ID, StringMatcher.equals(it))) }
            idMatches?.let { add(BridgeRegexFilter("idMatches", it) { node -> node.id() }) }
            className?.let { add(StringPropertyFilter(StringProperty.CLASS_NAME, StringMatcher.equals(it))) }
            classNameMatches?.let { add(BridgeRegexFilter("classNameMatches", it) { node -> node.className() }) }
            clickable?.let { add(BooleanFilter(BooleanFilter.CLICKABLE, it)) }
            enabled?.let { add(BooleanFilter(BooleanFilter.ENABLED, it)) }
            scrollable?.let { add(BooleanFilter(BooleanFilter.SCROLLABLE, it)) }
            depth?.let { add(IntFilter(IntFilter.Property.DEPTH, it)) }
            boundsInside?.let { add(BridgeBoundsFilter(it, inside = true)) }
            boundsContains?.let { add(BridgeBoundsFilter(it, inside = false)) }
    }

    /** The descriptor as the runtime's filters, see the class comment. zh-CN: 描述符对应的运行时过滤器, 见类注释. */
    fun toFilter(): Filter {
        if (isEmpty) return NOTHING
        return Selector().apply { toFilters().forEach(::add) }
    }

    companion object {

        /** The filter of an empty descriptor. zh-CN: 空描述符的过滤器. */
        val NOTHING: Filter = object : Filter {
            override fun filter(node: UiNode<*>) = false
            override fun toString() = "nothing()"
        }

    }

}

/**
 * A whole-value regex match on a string value of the node, the missing value counting as empty
 * (the protocol's `textMatches` / `descMatches` / `idMatches` / `classNameMatches`).
 * zh-CN: 对节点某个字符串值的整值正则匹配, 缺失值按空串计 (协议的 `textMatches` / `descMatches` / `idMatches` / `classNameMatches`).
 */
class BridgeRegexFilter(private val name: String, private val regex: Regex, private val value: (UiNode<*>) -> String?) : Filter {

    override fun filter(node: UiNode<*>) = regex.matches(value(node).orEmpty())

    override fun toString() = "$name(/${regex.pattern}/${flags()})"

    private fun flags(): String = buildString {
        if (RegexOption.IGNORE_CASE in regex.options) append('i')
        if (RegexOption.MULTILINE in regex.options) append('m')
        if (RegexOption.DOT_MATCHES_ALL in regex.options) append('s')
    }

}

/**
 * The protocol's `boundsInside` (the node's bounds lie within [bounds]) or `boundsContains` (they
 * contain it), compared in device pixels edge by edge.
 * zh-CN: 协议的 `boundsInside` (节点边界位于 [bounds] 之内) 或 `boundsContains` (节点边界包含它), 逐边按设备像素比较.
 */
class BridgeBoundsFilter(private val bounds: BridgeBounds, private val inside: Boolean) : Filter {

    override fun filter(node: UiNode<*>): Boolean = when (inside) {
        true -> node.left() >= bounds.left && node.top() >= bounds.top && node.right() <= bounds.right && node.bottom() <= bounds.bottom
        else -> node.left() <= bounds.left && node.top() <= bounds.top && node.right() >= bounds.right && node.bottom() >= bounds.bottom
    }

    override fun toString() = "${if (inside) "boundsInside" else "boundsContains"}(${bounds.left}, ${bounds.top}, ${bounds.right}, ${bounds.bottom})"

}
