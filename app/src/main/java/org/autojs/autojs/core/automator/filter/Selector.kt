package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiNode
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The AND of a list of filters. `add` / `append` modify the list in place (a `UiSelector` grows as
 * builders are called), while evaluation and `toString` iterate a snapshot, so a selector shared
 * between threads is safe to search with while another thread is still appending to it.
 *
 * The filters are evaluated cheapest first ([Filter.cost], docs/selector/ROADMAP.md P2.2; a stable
 * sort, so equal costs keep the declared order), the result being the same AND; `toString` and the
 * list itself keep the declared order. The sorted snapshot and the text are kept until the list
 * changes (P2.1: a search records the text of its selector).
 *
 * The last `normalize(...)` among the filters ([NormalizeFilter], P3.3) makes the string conditions compare normalized
 * values, wherever they stand ([effectiveFilters]); the last `prefer(...)` ([PreferFilter], P3.4) is the [preference]
 * of the single-result queries. Both pass every node themselves.
 *
 * zh-CN: 一组过滤器的 AND. `add` / `append` 原地修改列表 (`UiSelector` 随 builder 调用而增长), 而求值与 `toString` 遍历的是
 * 快照, 因此多个线程共享的选择器在另一线程仍在追加时也可安全搜索.
 *
 * 过滤器按开销从低到高求值 ([Filter.cost], P2.2; 稳定排序, 开销相同者保持声明顺序), 结果仍是同一个 AND; `toString` 与列表本身保持声明顺序.
 * 排序后的快照与文本保留到列表变化为止 (P2.1: 每次搜索都要记录其选择器的文本).
 *
 * 过滤器中最后一个 `normalize(...)` ([NormalizeFilter], P3.3) 使各字符串条件比较规范化之后的值, 与其位置无关 ([effectiveFilters]);
 * 最后一个 `prefer(...)` ([PreferFilter], P3.4) 为单结果查询的 [preference]. 两者本身接受每个节点.
 *
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Oct 25, 2026.
 */
open class Selector : Filter {

    val filters: MutableList<Filter> = CopyOnWriteArrayList()

    @Volatile
    private var plan: Plan? = null

    override fun filter(node: UiNode<*>) = plan().filter(node)

    /** The most costly of the filters. zh-CN: 各过滤器中最高的开销. */
    override val cost: Int get() = plan().cost

    fun add(filter: Filter) = filters.add(filter)

    fun append(uiSelector: UiSelector) = filters.addAll(uiSelector.selector.filters)

    /**
     * The filters as they are now, cheapest first, as one filter: a search evaluates it on every node without looking
     * at the list again. zh-CN: 当前的过滤器, 按开销从低到高, 作为一个过滤器: 搜索在每个节点上求值它, 不再查看列表.
     */
    fun snapshot(): Filter = plan()

    /**
     * The filters as they compare, in the declared order: the string conditions normalized under `normalize(...)` (P3.3).
     * zh-CN: 实际参与比较的过滤器, 按声明顺序: `normalize(...)` 下的字符串条件已规范化 (P3.3).
     */
    val effectiveFilters: List<Filter> get() = plan().effective

    /** The preference among the candidates of a single result, `prefer(...)` (P3.4), or null. zh-CN: 单个结果的候选之间的偏好 `prefer(...)` (P3.4), 或 null. */
    val preference: Preference? get() = plan().preference

    /** The options of `normalize(...)` in effect (P3.3), or null. zh-CN: 生效中的 `normalize(...)` 选项 (P3.3), 或 null. */
    val normalization: NormalizeOptions? get() = plan().normalization

    override fun toString() = plan().text

    // The plan of the list as it is: the one kept while the list holds the same filters, else a new one.
    // zh-CN: 列表当前的计划: 列表仍是相同的过滤器时沿用保留的计划, 否则新建.
    private fun plan(): Plan = plan?.takeIf { it.isOf(filters) } ?: Plan(filters.toTypedArray()).also { plan = it }

    private class Plan(private val declared: Array<Filter>) : Filter {

        val preference: Preference? = (declared.lastOrNull { it is PreferFilter } as PreferFilter?)?.preference

        val normalization: NormalizeOptions? = (declared.lastOrNull { it is NormalizeFilter } as NormalizeFilter?)?.options?.takeUnless { it.isOff }

        val effective: List<Filter> = when (val options = normalization) {
            null -> declared.asList()
            else -> declared.map { if (it is StringPropertyFilter) it.normalized(options) else it }
        }

        private val ordered: Array<Filter> = if (effective.size < 2) effective.toTypedArray() else effective.sortedBy { it.cost }.toTypedArray()

        override val cost: Int = ordered.lastOrNull()?.cost ?: Filter.COST_PROPERTY

        // A nested selector may change behind this plan, so its text is not kept.
        // zh-CN: 嵌套的选择器可能在本计划之外变化, 因此不保留其文本.
        private val nested = declared.any { it is Selector }

        private var rendered: String? = null

        val text: String get() = if (nested) render() else rendered ?: render().also { rendered = it }

        override fun filter(node: UiNode<*>): Boolean {
            for (f in ordered) {
                if (!f.filter(node)) return false
            }
            return true
        }

        fun isOf(list: List<Filter>): Boolean = try {
            if (list.size != declared.size) return false
            for (i in declared.indices) {
                if (list[i] !== declared[i]) return false
            }
            true
        } catch (e: IndexOutOfBoundsException) {
            // Shrunk by another thread while compared. zh-CN: 比较期间被其他线程缩短.
            false
        }

        private fun render() = declared.joinToString(".").ifEmpty { "selector()" }

        override fun toString() = text

    }

}
