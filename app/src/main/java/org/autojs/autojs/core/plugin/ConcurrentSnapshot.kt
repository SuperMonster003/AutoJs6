package org.autojs.autojs.core.plugin

/**
 * A copy of a collection that other threads may change meanwhile (a `ConcurrentHashMap` view, a concurrent set or queue,
 * a `CopyOnWrite` collection), taken through `toArray()` (docs/memory-phase2/ROADMAP.md C35 / C36).
 * Kotlin's `toList()` / `toSet()` / `sortedWith()` read the size first and take the only element of a one-element collection
 * with `iterator().next()` (`get(0)` for a list), which throws when another thread removes that element in between;
 * the `toArray()` of these collections copies whatever is present and does not throw.
 * zh-CN: 复制一个其他线程可能同时修改的集合 (`ConcurrentHashMap` 视图, 并发集合或队列, `CopyOnWrite` 集合), 经 `toArray()` 复制
 * (docs/memory-phase2/ROADMAP.md C35 / C36). Kotlin 的 `toList()` / `toSet()` / `sortedWith()` 先读大小, 只有一项时用
 * `iterator().next()` (列表用 `get(0)`) 取出该项, 若其他线程在两步之间移除了它便抛出异常; 这些集合的 `toArray()` 复制当时存在的元素, 不会抛出.
 */
fun <T> Collection<T>.concurrentSnapshot(): List<T> = ArrayList(this)
