package org.autojs.autojs.core.automator

/**
 * Deterministic in-memory [UiNode] tree for JVM tests of filters, search algorithms, the compass
 * walk and everything built on them. Every property is a plain field with a neutral default, so a
 * test only states what matters to it; no Android class is involved.
 *
 * zh-CN: 供过滤器, 搜索算法, 罗盘导航及其上层逻辑的 JVM 测试使用的确定性内存 [UiNode] 树. 每个属性都是带中性默认值的
 * 普通字段, 测试只需声明与自己相关的部分; 不涉及任何 Android 类.
 *
 * ```
 * val root = FakeUiNode.root {
 *     child("a") {
 *         text = "Login"
 *         clickable = true
 *         bounds(0, 0, 100, 50)
 *         child("a0", clickable = true)
 *     }
 *     child("b")
 * }
 * ```
 */
class FakeUiNode private constructor(
    val name: String,
    private val level: Int,
    private val index: Int,
) : UiNode<FakeUiNode> {

    private val children = ArrayList<FakeUiNode>()

    var parentNode: FakeUiNode? = null
        private set

    var packageName: String? = null
    var className: String? = null
    var id: String? = null
    var idHex: String? = null
    var text: String = ""
    var desc: String? = null

    var left = 0
    var top = 0
    var right = 0
    var bottom = 0

    var checkable = false
    var checked = false
    var focusable = false
    var focused = false
    var visibleToUser = true
    var accessibilityFocused = false
    var selected = false
    var clickable = false
    var longClickable = false
    var enabled = true
    var password = false
    var scrollable = false
    var editable = false
    var contentInvalid = false
    var contextClickable = false
    var multiLine = false
    var dismissable = false

    var row = -1
    var column = -1
    var rowSpan = -1
    var columnSpan = -1
    var rowCount = 0
    var columnCount = 0
    var drawingOrder = 0

    /** Symbolic action names, `ACTION_CLICK` and so on. zh-CN: 动作符号名, 如 `ACTION_CLICK`. */
    var actions: List<String> = emptyList()

    /** What [performAction] reports. zh-CN: [performAction] 的返回值. */
    var actionResult = true

    /** Action ids handed to [performAction], in order. zh-CN: 交给 [performAction] 的动作 id, 按顺序. */
    val performedActions = ArrayList<Int>()

    var isRecycled = false
        private set

    /** What [refresh] reports: false for a node that has left the window (docs/selector/ROADMAP.md P3.2). zh-CN: [refresh] 的返回值: 已离开窗口的节点为 false (P3.2). */
    var refreshable = true

    /** `AccessibilityNodeInfo.refresh`. */
    fun refresh(): Boolean = refreshable

    /** How often [text] / [desc] were read (docs/selector/ROADMAP.md P2.1). zh-CN: [text] / [desc] 被读取的次数 (P2.1). */
    var textReads = 0
    var descReads = 0

    /**
     * What the framework lookups of the fast path (P2.3) find under this node: [Lookup.NORMAL] as the framework does,
     * [Lookup.EMPTY] nothing (a provider that does not implement them), [Lookup.UNAVAILABLE] a failure.
     * zh-CN: 快路径 (P2.3) 的框架查找在本节点之下找到的内容: [Lookup.NORMAL] 同框架, [Lookup.EMPTY] 什么也找不到 (未实现查找的提供方), [Lookup.UNAVAILABLE] 失败.
     */
    var lookup = Lookup.NORMAL

    enum class Lookup { NORMAL, EMPTY, UNAVAILABLE }

    /**
     * `findAccessibilityNodeInfosByViewId`: the nodes of the subtree with that id, null when [lookup] is unavailable.
     * zh-CN: `findAccessibilityNodeInfosByViewId`: 子树中具有该 id 的节点, [lookup] 不可用时为 null.
     */
    fun findByViewId(viewId: String): List<FakeUiNode>? = lookedUp { it.id == viewId }

    private fun lookedUp(predicate: (FakeUiNode) -> Boolean): List<FakeUiNode>? = when (lookup) {
        Lookup.NORMAL -> descendantsAndSelf().filter(predicate)
        Lookup.EMPTY -> emptyList()
        Lookup.UNAVAILABLE -> null
    }

    fun child(name: String, clickable: Boolean = false, build: FakeUiNode.() -> Unit = {}): FakeUiNode {
        val node = FakeUiNode(name, level + 1, children.size).also {
            it.parentNode = this
            it.clickable = clickable
        }
        children += node
        node.build()
        return node
    }

    fun bounds(left: Int, top: Int, right: Int, bottom: Int) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    /** Finds the node named [name] in the subtree rooted at this node. zh-CN: 在以本节点为根的子树中查找名为 [name] 的节点. */
    fun find(name: String): FakeUiNode = descendantsAndSelf().firstOrNull { it.name == name }
        ?: throw NoSuchElementException("No node named $name under ${this.name}")

    /** All nodes of the subtree rooted at this node in pre-order. zh-CN: 以本节点为根的子树的全部节点, 先序. */
    fun descendantsAndSelf(): List<FakeUiNode> = listOf(this) + children.flatMap { it.descendantsAndSelf() }

    override fun parent(): FakeUiNode? = parentNode

    override fun child(i: Int): FakeUiNode? = children.getOrNull(if (i < 0) i + children.size else i)

    override fun childCount(): Int = children.size

    override fun indexInParent(): Int = index

    override fun depth(): Int = level

    override fun drawingOrder(): Int = drawingOrder

    override fun packageName(): String? = packageName

    override fun className(): String? = className

    override fun id(): String? = id

    override fun idHex(): String? = idHex

    override fun text(): String = text.also { textReads++ }

    override fun desc(): String? = desc.also { descReads++ }

    override fun left(): Int = left

    override fun top(): Int = top

    override fun right(): Int = right

    override fun bottom(): Int = bottom

    override fun width(): Int = right - left

    override fun height(): Int = bottom - top

    override fun centerX(): Int = (left + right) shr 1

    override fun centerY(): Int = (top + bottom) shr 1

    override fun checkable(): Boolean = checkable

    override fun checked(): Boolean = checked

    override fun focusable(): Boolean = focusable

    override fun focused(): Boolean = focused

    override fun visibleToUser(): Boolean = visibleToUser

    override fun accessibilityFocused(): Boolean = accessibilityFocused

    override fun selected(): Boolean = selected

    override fun clickable(): Boolean = clickable

    override fun longClickable(): Boolean = longClickable

    override fun enabled(): Boolean = enabled

    override fun password(): Boolean = password

    override fun scrollable(): Boolean = scrollable

    override fun editable(): Boolean = editable

    override fun contentInvalid(): Boolean = contentInvalid

    override fun contextClickable(): Boolean = contextClickable

    override fun multiLine(): Boolean = multiLine

    override fun dismissable(): Boolean = dismissable

    override fun row(): Int = row

    override fun column(): Int = column

    override fun rowSpan(): Int = rowSpan

    override fun columnSpan(): Int = columnSpan

    override fun rowCount(): Int = rowCount

    override fun columnCount(): Int = columnCount

    override fun actionNames(): List<String> = actions

    override fun performAction(action: Int, vararg arguments: ActionArgument): Boolean {
        performedActions += action
        return actionResult
    }

    override fun recycle() {
        check(!isRecycled) { "$name has already been recycled" }
        isRecycled = true
    }

    override fun toString(): String = "FakeUiNode($name)"

    companion object {

        fun root(name: String = "root", clickable: Boolean = false, build: FakeUiNode.() -> Unit = {}): FakeUiNode {
            return FakeUiNode(name, 0, -1).apply {
                this.clickable = clickable
                build()
            }
        }

    }

}
