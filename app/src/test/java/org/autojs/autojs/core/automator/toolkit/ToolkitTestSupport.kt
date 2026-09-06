package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.flow.FakeClock
import kotlin.random.Random

/**
 * Fakes for the toolkit tests: bindings whose "screen" is a map from target to what it locates
 * right now (a node, a list of nodes, or a function producing either, so that a test can make a
 * target appear or move), an action performer whose outcomes are programmable lambdas that
 * record every call, and a rig wiring both to a manual clock and recorded sleeps.
 * zh-CN: 工具集测试的替身: 以 "屏幕" 映射 (目标 -> 此刻定位到的内容: 节点, 节点列表, 或产生二者之一的函数, 使测试能让目标出现或移动)
 * 实现的 bindings, 结果为可编程 lambda 且记录每次调用的动作执行器, 以及把两者接到手动时钟与记录式休眠的 rig.
 */
class FakeToolkitBindings : ToolkitBindings<FakeUiNode> {

    /** What a target locates right now. zh-CN: 目标此刻定位到的内容. */
    val screen = HashMap<Any?, Any?>()

    /** The active window root. zh-CN: 活动窗口的根节点. */
    var root: FakeUiNode? = null

    /** Nodes that no longer refresh. zh-CN: 不再能刷新的节点. */
    val gone = HashSet<FakeUiNode>()

    /** Called on every [refresh], before the answer. zh-CN: 每次 [refresh] 时在给出结果前调用. */
    var onRefresh: ((FakeUiNode) -> Unit)? = null

    var currentPackage: String? = null

    /** Overrides [currentPackage] when set. zh-CN: 设置后覆盖 [currentPackage]. */
    var packageSupplier: (() -> String?)? = null

    /** `resolvePackage` answers. zh-CN: `resolvePackage` 的回答. */
    val packages = HashMap<Any?, String>()

    var screenWidth = 1080

    var screenHeight = 1920

    /** `pick` rendering: the content for `null` / `"$"` / `"txt"`, the node for `"#"`, `"type(name)"` otherwise. zh-CN: `pick` 的呈现: `null` / `"$"` / `"txt"` 为内容, `"#"` 为节点, 其余为 `"type(name)"`. */
    var pick: (FakeUiNode, Any?) -> Any? = { node, type ->
        when (type) {
            null, "$", "txt", "content" -> node.content()
            "#" -> node
            else -> "$type(${node.name})"
        }
    }

    var finds = 0
        private set

    val findLog = ArrayList<Pair<Any?, FakeUiNode?>>()

    val findAllLog = ArrayList<Pair<Any?, FakeUiNode?>>()

    var refreshes = 0
        private set

    val watches = ArrayList<FakeWatch>()

    fun show(target: Any?, node: FakeUiNode?) {
        if (node == null) screen.remove(target) else screen[target] = node
    }

    fun show(target: Any?, nodes: List<FakeUiNode>) {
        screen[target] = nodes
    }

    fun show(target: Any?, supplier: () -> Any?) {
        screen[target] = supplier
    }

    private fun resolve(target: Any?): List<FakeUiNode> = when (target) {
        is FakeUiNode -> listOf(target)
        is Function0<*> -> asNodes(target())
        else -> asNodes(screen[target])
    }

    private fun asNodes(value: Any?): List<FakeUiNode> = when (value) {
        null -> emptyList()
        is FakeUiNode -> listOf(value)
        is List<*> -> value.filterIsInstance<FakeUiNode>()
        is Function0<*> -> asNodes(value())
        else -> emptyList()
    }

    private fun visible(node: FakeUiNode, root: FakeUiNode?): Boolean = node !in gone && (root == null || under(root, node))

    private fun under(root: FakeUiNode, node: FakeUiNode): Boolean {
        var n: FakeUiNode? = node
        while (n != null) {
            if (n === root) return true
            n = n.parent()
        }
        return false
    }

    override fun find(target: Any?, root: FakeUiNode?): FakeUiNode? {
        finds += 1
        val node = resolve(target).firstOrNull { visible(it, root) }
        findLog += target to root
        return node
    }

    override fun findAll(target: Any?, root: FakeUiNode?): List<FakeUiNode> {
        findAllLog += target to root
        return resolve(target).filter { visible(it, root) }
    }

    override fun probe(cond: Any?, root: FakeUiNode?): Any? = when (cond) {
        is Function0<*> -> cond().takeIf { it != null && it != false }
        else -> find(cond, root)
    }

    override fun describe(target: Any?): String? = when (target) {
        is FakeUiNode -> target.name
        is String -> "text(\"$target\")"
        is Function0<*> -> "fn"
        else -> target?.toString()
    }

    override fun activeRoot(): FakeUiNode? = root

    override fun refresh(node: FakeUiNode): Boolean {
        refreshes += 1
        onRefresh?.invoke(node)
        return node !in gone
    }

    override fun pick(node: FakeUiNode, resultType: Any?): Any? = pick.invoke(node, resultType)

    override fun currentPackage(): String? = packageSupplier?.invoke() ?: currentPackage

    override fun resolvePackage(app: Any?): String? = packages[app]

    override fun screenWidth(): Int = screenWidth

    override fun screenHeight(): Int = screenHeight

    override fun watchWindows(debounceMillis: Long, onChange: Runnable): ToolkitBindings.Watch = FakeWatch(debounceMillis, onChange).also { watches += it }

    /** A watch the test fires by hand; the callback runs on the calling thread. zh-CN: 由测试手动触发的观察; 回调在调用线程上运行. */
    class FakeWatch(val debounceMillis: Long, private val onChange: Runnable) : ToolkitBindings.Watch {

        var stopped = false
            private set

        var triggers = 0
            private set

        /** A window change: runs the callback unless stopped. zh-CN: 一次窗口变化: 未停止时运行回调. */
        fun change() {
            if (!stopped) onChange.run()
        }

        override fun stop() {
            stopped = true
        }

        override fun trigger() {
            triggers += 1
            onChange.run()
        }

    }

}

class FakeActionPerformer : ActionPerformer<FakeUiNode> {

    /** Every call in order, e.g. `click(a)`, `tap(10, 20)`, `scroll(list, DOWN)`. zh-CN: 按顺序的每次调用, 如 `click(a)`, `tap(10, 20)`, `scroll(list, DOWN)`. */
    val calls = ArrayList<String>()

    var clickResult: (FakeUiNode) -> Boolean = { true }

    var longClickResult: (FakeUiNode) -> Boolean = { true }

    var focusResult: (FakeUiNode) -> Boolean = { true }

    /** Updates the node's text by default. zh-CN: 默认更新节点的文本. */
    var setTextResult: (FakeUiNode, String) -> Boolean = { node, text ->
        node.text = text
        true
    }

    var imeEnterResult: (FakeUiNode) -> Boolean = { true }

    var scrollResult: (FakeUiNode, ScrollDirection) -> Boolean = { _, _ -> true }

    var tapResult: (Int, Int) -> Boolean = { _, _ -> true }

    var swipeResult: (IntArray) -> Boolean = { true }

    var backResult: () -> Boolean = { true }

    var launchResult: (String) -> Boolean = { true }

    val taps = ArrayList<Pair<Int, Int>>()

    /** `[x1, y1, x2, y2, duration]` of every swipe. zh-CN: 每次滑动的 `[x1, y1, x2, y2, duration]`. */
    val swipes = ArrayList<List<Int>>()

    fun calls(prefix: String): Int = calls.count { it.startsWith(prefix) }

    override fun click(node: FakeUiNode): Boolean {
        calls += "click(${node.name})"
        return clickResult(node)
    }

    override fun longClick(node: FakeUiNode): Boolean {
        calls += "longClick(${node.name})"
        return longClickResult(node)
    }

    override fun focus(node: FakeUiNode): Boolean {
        calls += "focus(${node.name})"
        return focusResult(node)
    }

    override fun setText(node: FakeUiNode, text: String): Boolean {
        calls += "setText(${node.name}, $text)"
        return setTextResult(node, text)
    }

    override fun imeEnter(node: FakeUiNode): Boolean {
        calls += "imeEnter(${node.name})"
        return imeEnterResult(node)
    }

    override fun scroll(node: FakeUiNode, direction: ScrollDirection): Boolean {
        calls += "scroll(${node.name}, $direction)"
        return scrollResult(node, direction)
    }

    override fun tap(x: Int, y: Int): Boolean {
        calls += "tap($x, $y)"
        taps += x to y
        return tapResult(x, y)
    }

    override fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMillis: Long): Boolean {
        calls += "swipe($x1, $y1, $x2, $y2, $durationMillis)"
        val swipe = intArrayOf(x1, y1, x2, y2, durationMillis.toInt())
        swipes += swipe.toList()
        return swipeResult(swipe)
    }

    override fun back(): Boolean {
        calls += "back"
        return backResult()
    }

    override fun launch(packageName: String): Boolean {
        calls += "launch($packageName)"
        return launchResult(packageName)
    }

}

/** A toolkit over the fakes, a manual clock advanced by the recorded sleeps, and a seeded random. zh-CN: 基于替身的工具集, 由记录式休眠推进的手动时钟, 以及固定种子的随机数. */
class ToolkitRig(seed: Int = 0) {

    val clock = FakeClock()

    val sleeps = ArrayList<Long>()

    val bindings = FakeToolkitBindings()

    val actions = FakeActionPerformer()

    var fatal: (Throwable) -> Boolean = { false }

    val kit = Toolkit(
        bindings,
        actions,
        clock = { clock.now },
        sleep = { millis ->
            sleeps += millis
            clock.advance(millis)
        },
        random = Random(seed),
        fatal = { fatal(it) },
    )

    /** A target that is present from the [from]-th look on (1-based). zh-CN: 从第 [from] 次查找起 (从 1 计) 存在的目标. */
    fun appearing(target: Any?, node: FakeUiNode, from: Int) {
        var looks = 0
        bindings.show(target) { if (++looks >= from) node else null }
    }

}
