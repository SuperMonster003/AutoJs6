package org.autojs.autojs.core.automator

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.rhino.extension.IterableExtensions.toHashCode
import org.autojs.autojs.tool.Consumer
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Scriptable

/**
 * The nodes a search found. Scripts never hold this class itself: the wrap factory hands a
 * `UiObjectCollection` to JavaScript as a real array of the nodes with every public method of the
 * collection bound onto it, so `filter` / `map` / `forEach` / `some` / `every` / `indexOf` and
 * `for...of` are the array's own, and the methods declared here (`click()` and the other actions,
 * `find`, `at` / `first` / `last` / `nonNull` / `slice`, the `*Each` variants) come on top. A
 * method declared here shadows the array's method of the same name, which is why the array
 * operations with callbacks are not re-declared.
 * zh-CN: 一次搜索找到的节点. 脚本从不直接持有本类: wrap 工厂把 `UiObjectCollection` 交给 JavaScript 时是一个真正的节点数组,
 * 并把集合的全部公开方法绑定到数组上, 因此 `filter` / `map` / `forEach` / `some` / `every` / `indexOf` 与 `for...of`
 * 都是数组自身的, 本类声明的方法 (`click()` 等动作, `find`, `at` / `first` / `last` / `nonNull` / `slice`, `*Each` 变体)
 * 叠加其上. 本类声明的方法会遮蔽数组的同名方法, 因此带回调的数组操作不在此重复声明.
 *
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Sep 6, 2026.
 */
class UiObjectCollection private constructor(val nodes: List<UiObject?>) : UiObjectActions {

    operator fun get(i: Int): UiObject? = nodes[i]

    /** The node at [i], counted from the end when negative; `null` out of range. zh-CN: 第 [i] 个节点, 负数从末尾计; 越界为 `null`. */
    fun at(i: Int): UiObject? = nodes.getOrNull(if (i < 0) i + nodes.size else i)

    /** The first node, `null` when empty. zh-CN: 首个节点, 空集合为 `null`. */
    fun first(): UiObject? = nodes.firstOrNull()

    /** The last node, `null` when empty. zh-CN: 末个节点, 空集合为 `null`. */
    fun last(): UiObject? = nodes.lastOrNull()

    /** The collection without its `null` entries. zh-CN: 去掉 `null` 项的集合. */
    fun nonNull(): UiObjectCollection = when {
        nodes.none { it == null } -> this
        else -> of(nodes.filterNotNull())
    }

    /**
     * The nodes from [start] (inclusive) to [end] (exclusive) with the index rules of
     * `Array.prototype.slice` (negative from the end, clamped), as a collection that still carries
     * the collection methods. zh-CN: 从 [start] (含) 到 [end] (不含) 的节点, 序号规则同 `Array.prototype.slice`
     * (负数从末尾计, 越界截断), 结果仍是带集合方法的集合.
     */
    @JvmOverloads
    fun slice(start: Int = 0, end: Int = nodes.size): UiObjectCollection {
        val size = nodes.size
        fun clamp(index: Int) = if (index < 0) maxOf(size + index, 0) else minOf(index, size)
        val from = clamp(start)
        val to = clamp(end)
        return when {
            from >= to -> EMPTY
            from == 0 && to == size -> this
            else -> of(nodes.subList(from, to))
        }
    }

    operator fun contains(o: UiObject?): Boolean = nodes.contains(o)

    operator fun iterator(): Iterator<UiObject?> = nodes.iterator()

    fun isEmpty() = nodes.isEmpty()

    fun isNotEmpty() = nodes.isNotEmpty()

    @Deprecated("Use isEmpty instead.", ReplaceWith("this.isEmpty()"))
    fun empty() = isEmpty()

    @Deprecated("Use isNotEmpty instead.", ReplaceWith("this.isNotEmpty()"))
    fun nonEmpty() = isNotEmpty()

    fun toArray(): Array<UiObject?> = nodes.toTypedArray()

    fun toList(): List<UiObject?> = nodes

    fun size(): Int = nodes.size

    fun each(consumer: Consumer<UiObject>) = also { nodes.forEach(consumer::accept) }

    fun find(selector: UiSelector): UiObjectCollection {
        return ArrayList<UiObject?>()
            .also { list ->
                nodes.filterNotNull().forEach { node ->
                    list.addAll(selector.findOf(node).nodes)
                }
            }.let(::of)
    }

    fun findOne(selector: UiSelector): UiObject? {
        return nodes.firstNotNullOfOrNull { node ->
            node?.let { selector.findOneOf(it) }
        }
    }

    fun performAction(action: Int) = performAction(action, *emptyArray())

    override fun performAction(action: Int, vararg arguments: ActionArgument): Boolean {
        var success = true
        nodes.filterNotNull().forEach { node ->
            when (arguments.isEmpty()) {
                true -> node.performAction(action)
                else -> node.performAction(action, *arguments)
            }.also { success = success and it }
        }
        return success
    }

    /**
     * [performAction] node by node: one result per index (`false` for a `null` entry), every node
     * attempted, unlike the boolean AND of [performAction].
     * zh-CN: 逐节点的 [performAction]: 每个序号一个结果 (`null` 项为 `false`), 每个节点都会尝试, 不同于 [performAction] 的布尔与.
     */
    fun performActionEach(action: Int, vararg arguments: ActionArgument): BooleanArray = BooleanArray(nodes.size) { i ->
        nodes[i]?.let { node ->
            when (arguments.isEmpty()) {
                true -> node.performAction(action)
                else -> node.performAction(action, *arguments)
            }
        } ?: false
    }

    fun clickEach() = performActionEach(AccessibilityActionCompat.ACTION_CLICK.id)

    fun longClickEach() = performActionEach(AccessibilityActionCompat.ACTION_LONG_CLICK.id)

    fun scrollForwardEach() = performActionEach(AccessibilityActionCompat.ACTION_SCROLL_FORWARD.id)

    fun scrollBackwardEach() = performActionEach(AccessibilityActionCompat.ACTION_SCROLL_BACKWARD.id)

    fun setTextEach(text: String) = performActionEach(
        AccessibilityActionCompat.ACTION_SET_TEXT.id,
        ActionArgument.CharSequenceActionArgument(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text),
    )

    override fun toString(): String {
        return "${UiObjectCollection::class.java.name}@${super.hashCode()}"
    }

    override fun hashCode(): Int = when {
        nodes.isEmpty() -> super.hashCode()
        else -> nodes.map { it.hashCode() }.toHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UiObjectCollection) return false
        return nodes == other.nodes
    }

    fun isSimilar(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        when (other) {
            is Scriptable -> {
                val thisHashCode = hashCode()
                val otherHashCode = RhinoUtils.hashCodeOfScriptable(other) ?: return false
                return thisHashCode == otherHashCode
            }
            else -> {
                return when {
                    other.javaClass != UiObjectCollection::class.java -> false
                    else -> hashCode() == other.hashCode()
                }
            }
        }
    }

    companion object {

        val EMPTY = of(emptyList())

        @JvmStatic
        fun of(list: List<UiObject?>) = UiObjectCollection(list)

        @JvmStatic
        fun of(list: Array<UiObject?>) = of(list.toList())

        internal fun transform(collection: UiObjectCollection, mapper: (UiObject?) -> UiObject?) = UiObjectCollection(mapNotNull(collection, mapper))

        internal fun <T> mapNotNull(collection: UiObjectCollection, mapper: (UiObject?) -> T): List<T> = collection.nodes.mapNotNull(mapper)

    }

}
