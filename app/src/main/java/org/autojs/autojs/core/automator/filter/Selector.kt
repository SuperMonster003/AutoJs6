package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiNode
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The AND of a list of filters. `add` / `append` modify the list in place (a `UiSelector` grows as
 * builders are called), while evaluation and `toString` iterate a snapshot, so a selector shared
 * between threads is safe to search with while another thread is still appending to it.
 * zh-CN: 一组过滤器的 AND. `add` / `append` 原地修改列表 (`UiSelector` 随 builder 调用而增长), 而求值与 `toString` 遍历的是
 * 快照, 因此多个线程共享的选择器在另一线程仍在追加时也可安全搜索.
 *
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Sep 6, 2026.
 */
open class Selector : Filter {

    val filters: MutableList<Filter> = CopyOnWriteArrayList()

    override fun filter(node: UiNode<*>) = filters.all { it.filter(node) }

    fun add(filter: Filter) = filters.add(filter)

    fun append(uiSelector: UiSelector) = filters.addAll(uiSelector.selector.filters)

    override fun toString() = filters.joinToString(".").ifEmpty { "selector()" }

}
