package org.autojs.autojs.core.automator

import org.autojs.autojs.core.automator.search.LazySearch

/**
 * What `UiSelector.findIterator()` returns (roadmap P5.3): the matches of a lazy search, one per
 * `next()`, usable as a JavaScript iterator (`for (let node of it)`) and by hand (`hasNext()` /
 * `next()`); `collect()` takes the rest into a collection, `close()` releases the nodes the walk
 * has not reached. Reads [visited] / [produced] for the cost so far; [isClosed] is a method call from
 * JavaScript (`it.isClosed()`, the bean property being `it.closed`).
 * zh-CN: `UiSelector.findIterator()` 的返回值 (Roadmap P5.3): 惰性搜索的匹配, 每次 `next()` 一个, 可作为 JavaScript 迭代器
 * (`for (let node of it)`) 或手动使用 (`hasNext()` / `next()`); `collect()` 把其余收进集合, `close()` 释放遍历尚未到达的节点.
 * [visited] / [produced] 为至今的代价; [isClosed] 在 JavaScript 中为方法调用 (`it.isClosed()`, bean 属性为 `it.closed`).
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class UiObjectIterator internal constructor(private val search: LazySearch<UiObject>) : Iterator<UiObject>, Iterable<UiObject>, AutoCloseable {

    val visited: Int get() = search.visited

    val produced: Int get() = search.produced

    val isClosed: Boolean get() = search.isClosed

    override fun iterator(): Iterator<UiObject> = this

    override fun hasNext(): Boolean = search.hasNext()

    override fun next(): UiObject = search.next()

    /** The remaining matches as a collection, exhausting the search. zh-CN: 其余匹配组成的集合, 走完搜索. */
    fun collect(): UiObjectCollection = UiObjectCollection.of(search.drain())

    /** The remaining matches as a list, exhausting the search. zh-CN: 其余匹配组成的列表, 走完搜索. */
    fun toList(): List<UiObject> = search.drain()

    override fun close() = search.close()

    override fun toString(): String = "UiObjectIterator(visited=$visited, produced=$produced${if (isClosed) ", closed" else ""})"

}
