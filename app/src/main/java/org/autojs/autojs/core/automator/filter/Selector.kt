package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiNode
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The AND of a list of filters. `add` / `append` modify the list in place (a `UiSelector` grows as
 * builders are called), while evaluation and `toString` iterate a snapshot, so a selector shared
 * between threads is safe to search with while another thread is still appending to it.
 *
 * The snapshot and the text are kept until the list changes (docs/selector/ROADMAP.md P2.1: a
 * search records the text of its selector).
 *
 * zh-CN: 一组过滤器的 AND. `add` / `append` 原地修改列表 (`UiSelector` 随 builder 调用而增长), 而求值与 `toString` 遍历的是
 * 快照, 因此多个线程共享的选择器在另一线程仍在追加时也可安全搜索.
 *
 * 快照与文本保留到列表变化为止 (P2.1: 每次搜索都要记录其选择器的文本).
 *
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Oct 25, 2026.
 */
open class Selector : Filter {

    val filters: MutableList<Filter> = CopyOnWriteArrayList()

    @Volatile
    private var plan: Plan? = null

    override fun filter(node: UiNode<*>) = plan().filter(node)

    fun add(filter: Filter) = filters.add(filter)

    fun append(uiSelector: UiSelector) = filters.addAll(uiSelector.selector.filters)

    /**
     * The filters as they are now, as one filter: a search evaluates it on every node without looking at the list
     * again. zh-CN: 当前的过滤器, 作为一个过滤器: 搜索在每个节点上求值它, 不再查看列表.
     */
    fun snapshot(): Filter = plan()

    override fun toString() = plan().text

    // The plan of the list as it is: the one kept while the list holds the same filters, else a new one.
    // zh-CN: 列表当前的计划: 列表仍是相同的过滤器时沿用保留的计划, 否则新建.
    private fun plan(): Plan = plan?.takeIf { it.isOf(filters) } ?: Plan(filters.toTypedArray()).also { plan = it }

    private class Plan(private val declared: Array<Filter>) : Filter {

        // A nested selector may change behind this plan, so its text is not kept.
        // zh-CN: 嵌套的选择器可能在本计划之外变化, 因此不保留其文本.
        private val nested = declared.any { it is Selector }

        private var rendered: String? = null

        val text: String get() = if (nested) render() else rendered ?: render().also { rendered = it }

        override fun filter(node: UiNode<*>): Boolean {
            for (f in declared) {
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
