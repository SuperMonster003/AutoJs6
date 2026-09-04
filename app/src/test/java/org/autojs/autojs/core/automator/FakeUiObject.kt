package org.autojs.autojs.core.automator

/**
 * Deterministic in-memory node tree for JVM tests of search algorithms and node navigation.
 * Only the members touched by those code paths are overridden, any other call reaches
 * the Android stubs and fails loudly, which keeps the fixture honest.
 *
 * zh-CN: 供搜索算法与节点导航的 JVM 测试使用的确定性内存节点树.
 * 仅覆盖这些代码路径会触及的成员, 其它调用会落到 Android 桩并直接失败, 以保证夹具的真实性.
 *
 * ```
 * val root = FakeUiObject.root {
 *     child("a") {
 *         child("a0", clickable = true)
 *     }
 *     child("b")
 * }
 * ```
 */
class FakeUiObject private constructor(
    private val name: String,
    private val clickable: Boolean,
    private val level: Int,
    indexInParent: Int,
) : UiObject(null, level, indexInParent) {

    private val children = ArrayList<FakeUiObject>()

    var parentNode: FakeUiObject? = null
        private set

    var isRecycled = false
        private set

    fun name() = name

    fun child(name: String, clickable: Boolean = false, build: FakeUiObject.() -> Unit = {}): FakeUiObject {
        val node = FakeUiObject(name, clickable, level + 1, children.size).also { it.parentNode = this }
        children += node
        node.build()
        return node
    }

    /** Finds the node named [name] in the subtree rooted at this node. */
    fun find(name: String): FakeUiObject = descendantsAndSelf().firstOrNull { it.name == name }
        ?: throw NoSuchElementException("No node named $name under ${this.name}")

    /** All nodes of the subtree rooted at this node in pre-order. */
    fun descendantsAndSelf(): List<FakeUiObject> = listOf(this) + children.flatMap { it.descendantsAndSelf() }

    override fun getChildCount(): Int = children.size

    override fun childCount(): Int = children.size

    override fun child(i: Int): UiObject? = children.getOrNull(if (i < 0) i + children.size else i)

    override fun parent(): UiObject? = parentNode

    override fun clickable(): Boolean = clickable

    @Deprecated("Deprecated in Java")
    override fun recycle() {
        check(!isRecycled) { "$name has already been recycled" }
        isRecycled = true
    }

    override fun toString(): String = "FakeUiObject($name)"

    override fun hashCode(): Int = System.identityHashCode(this)

    override fun equals(other: Any?): Boolean = this === other

    companion object {

        fun root(name: String = "root", clickable: Boolean = false, build: FakeUiObject.() -> Unit = {}): FakeUiObject {
            return FakeUiObject(name, clickable, 0, -1).apply(build)
        }

    }

}
