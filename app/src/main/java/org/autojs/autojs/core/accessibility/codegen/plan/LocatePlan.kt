package org.autojs.autojs.core.accessibility.codegen.plan

import org.autojs.autojs.core.automator.selector.SelectorRegistry
import java.util.Collections

/**
 * One literal selector condition. The registry owns names and argument types; plans cannot
 * contain raw JavaScript expressions. The first plan format uses strings, booleans and integers.
 * zh-CN: 一个字面量选择器条件. 名称与参数类型由注册表校验, 计划不接受原始 JavaScript 表达式;
 * 首版计划使用字符串, 布尔值与整数.
 */
data class Condition(val attr: String, val value: Any) {
    init {
        require(value is String || value is Boolean || value is Int) { "Unsupported selector literal type" }
        val entry = requireNotNull(SelectorRegistry.entry(attr)) { "Unknown selector condition: " + attr }
        require(attr != "algorithm" && attr != "currentApp") { "Plan conditions must be node predicates" }
        require(entry.resolve(listOf(value), lenient = false) != null) { "Invalid argument for selector condition: " + attr }
    }
}

/** Ordered, immutable conditions; an empty spec means selector().
 * zh-CN: 有序且不可变的条件; 空条件表示 selector(). */
class SelectorSpec(conditions: List<Condition> = emptyList()) {
    val conditions: List<Condition> = Collections.unmodifiableList(ArrayList(conditions))

    constructor(vararg conditions: Condition) : this(conditions.toList())

    override fun equals(other: Any?): Boolean = other is SelectorSpec && conditions == other.conditions
    override fun hashCode(): Int = conditions.hashCode()
    override fun toString(): String = SelectorText.render(this)
}

sealed interface PlanStep {
    /** findOnce(i); negative indices count from the end. zh-CN: findOnce(i), 负序号自末尾计. */
    data class Index(val index: Int) : PlanStep

    data class Up(val levels: Int) : PlanStep {
        init { require(levels > 0) { "Parent levels must be positive" } }
    }

    /** child(i); negative indices count from the end. zh-CN: child(i), 负序号自末尾计. */
    data class Child(val index: Int) : PlanStep

    /** A sibling at a relative offset, `s>n` for a positive and `s<n` for a negative [offset].
     * zh-CN: 相对偏移的兄弟节点, 正 [offset] 为 `s>n`, 负为 `s<n`. */
    data class Sibling(val offset: Int) : PlanStep {
        init { require(offset != 0) { "Sibling offset must not be zero" } }
    }

    /** Searches the anchor and its descendants. Cardinality decides first/all results.
     * zh-CN: 搜索锚点自身及后代, 基数决定取首个或全部结果. */
    data class Search(val selector: SelectorSpec) : PlanStep

    /** The node reached by the relation steps, or one of its descendants, must match [selector].
     * zh-CN: 关系步骤到达的节点或其后代必须匹配 [selector]. */
    data class Match(val selector: SelectorSpec) : PlanStep

    companion object {
        /** Steps that move through the captured tree. zh-CN: 在捕获树中移动的步骤. */
        fun isRelation(step: PlanStep): Boolean = step is Up || step is Child || step is Sibling
    }
}

sealed interface PlanReceiver {
    data object Self : PlanReceiver
    data class Up(val levels: Int) : PlanReceiver {
        init { require(levels > 0) { "Receiver parent levels must be positive" } }
    }
}

/**
 * A declarative location, independent of query timing, actions and code style. The entry selector
 * describes the target for SELF/SELF_INDEX/SELF_RELATIVE and the anchor for the other families.
 * SELF_RELATIVE keeps the entry matches whose relation steps reach a node whose subtree matches the
 * final [PlanStep.Match]; it identifies a target through the content of a nearby control.
 * zh-CN: 独立于查询时机, 动作与代码风格的声明式定位. 入口选择器在 SELF/SELF_INDEX/SELF_RELATIVE 中描述目标,
 * 在其它族中描述锚点. SELF_RELATIVE 保留 "关系步骤到达的节点子树匹配末尾 [PlanStep.Match]" 的入口匹配项,
 * 即通过邻近控件的内容确定目标.
 */
class LocatePlan(
    val family: Family,
    val selector: SelectorSpec,
    steps: List<PlanStep> = emptyList(),
    val cardinality: Cardinality = Cardinality.ONE,
    val receiver: PlanReceiver = PlanReceiver.Self,
) {
    enum class Family { SELF, SELF_INDEX, ANCHOR_PATH, ANCHOR_SEARCH, CONTAINER_ITEMS, SELF_RELATIVE }
    enum class Cardinality { ONE, ALL }

    val steps: List<PlanStep> = Collections.unmodifiableList(ArrayList(steps))

    init {
        val validShape = when (family) {
            Family.SELF -> steps.isEmpty()
            Family.SELF_INDEX -> cardinality == Cardinality.ONE && steps.size == 1 && steps.single() is PlanStep.Index
            Family.ANCHOR_PATH -> cardinality == Cardinality.ONE && steps.isNotEmpty() && steps.all { it is PlanStep.Up || it is PlanStep.Child }
            Family.ANCHOR_SEARCH -> cardinality == Cardinality.ONE && steps.size == 1 && steps.single() is PlanStep.Search
            Family.CONTAINER_ITEMS -> cardinality == Cardinality.ALL && steps.size == 1 && steps.single() is PlanStep.Search
            // An empty relative match would keep every entry match, which is a SELF plan in disguise.
            // zh-CN: 空的相对匹配会保留全部入口匹配项, 等于伪装的 SELF 计划.
            Family.SELF_RELATIVE -> steps.size in 2..MAX_RELATION_STEPS + 1 &&
                (steps.last() as? PlanStep.Match)?.selector?.conditions?.isNotEmpty() == true &&
                steps.dropLast(1).all { PlanStep.isRelation(it) }
        }
        require(validShape) { "Steps/cardinality do not fit plan family: " + family }
    }

    /** The movement steps of a SELF_RELATIVE plan; empty for other families.
     * zh-CN: SELF_RELATIVE 计划的移动步骤, 其它族为空. */
    val relation: List<PlanStep> get() = if (family == Family.SELF_RELATIVE) steps.dropLast(1) else emptyList()

    /** The nested selector of a SELF_RELATIVE plan. zh-CN: SELF_RELATIVE 计划的嵌套选择器. */
    val relativeMatch: SelectorSpec? get() = (steps.lastOrNull() as? PlanStep.Match)?.selector

    /** Versioned, length-prefixed fields keep keys unambiguous even for delimiter-rich text.
     * zh-CN: 带版本的长度前缀字段, 即使文本含分隔符也不会造成键歧义. */
    val planKey: String = buildString {
        append("v1|")
        fun field(value: String) { append(value.length).append(':').append(value) }
        field(family.name)
        field(cardinality.name)
        field(SelectorText.render(selector))
        field(this@LocatePlan.steps.size.toString())
        for (step in this@LocatePlan.steps) {
            when (step) {
                is PlanStep.Index -> { field("index"); field(step.index.toString()) }
                is PlanStep.Up -> { field("up"); field(step.levels.toString()) }
                is PlanStep.Child -> { field("child"); field(step.index.toString()) }
                is PlanStep.Sibling -> { field("sibling"); field(step.offset.toString()) }
                is PlanStep.Search -> { field("search"); field(SelectorText.render(step.selector)) }
                is PlanStep.Match -> { field("match"); field(SelectorText.render(step.selector)) }
            }
        }
        when (receiver) {
            PlanReceiver.Self -> field("self")
            is PlanReceiver.Up -> { field("receiverUp"); field(receiver.levels.toString()) }
        }
    }

    override fun equals(other: Any?): Boolean = other is LocatePlan && planKey == other.planKey
    override fun hashCode(): Int = planKey.hashCode()
    override fun toString(): String = planKey

    companion object {
        /** Relation steps a SELF_RELATIVE plan may take before its match. zh-CN: SELF_RELATIVE 匹配前至多的关系步数. */
        const val MAX_RELATION_STEPS = 4
    }
}
