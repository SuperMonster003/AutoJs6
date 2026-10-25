package org.autojs.autojs.core.automator.pick

import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Callable
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Undefined
import java.lang.reflect.Modifier

/**
 * docs/selector/ROADMAP.md P1.3: [PickExecutor] over [FakeUiNode] on the JVM, with a [PickTarget] of plain lists. Every
 * result type (the symbols, a registry method, the array form, the empty string, a value without a result, a refused
 * name) with every compass (none, `c0`, `p`, `s1`, the empty one) over two found nodes and over none, for `pickup` and
 * for `detect`; the callback (called with the result, `undefined` keeps it, never for the empty string) and where the
 * refusals of the current augment layer are thrown (`@` with a compass always, a refused name once a node is found).
 * The expected values follow the former `Detector`, written out per result type.
 *
 * zh-CN: P1.3: 在 JVM 上以 [FakeUiNode] 与普通列表的 [PickTarget] 检查 [PickExecutor]. 每种结果类型 (符号, 注册表方法, 数组形式, 空串, 无结果的值,
 * 被拒绝的名称) 与每种罗盘 (无, `c0`, `p`, `s1`, 空罗盘) 在两个命中节点与无命中时的结果, `pickup` 与 `detect` 都覆盖; 回调 (以结果调用, `undefined`
 * 保留原结果, 空串不调用) 以及现有 augment 层的拒绝在何处抛出 (`@` 带罗盘总是, 被拒绝的名称在找到节点时). 期望值按原 `Detector` 的语义逐类写出.
 */
class PickExecutorTest {

    private val root = FakeUiNode.root {
        bounds(0, 0, 100, 100)
        child("a") {
            text = "A"
            bounds(0, 0, 50, 50)
            child("a0") {
                text = "A0"
                desc = "D0"
                bounds(0, 0, 10, 10)
            }
        }
        child("b") {
            text = "B"
            bounds(50, 50, 100, 100)
        }
    }
    private val a = root.find("a")
    private val b = root.find("b")

    @Test
    fun pickupResultsByCompass() {
        val wrong = mutableListOf<String>()
        var cases = 0
        for (found in listOf(listOf(a, b), emptyList())) for (compass in COMPASSES) for (type in TYPES) {
            val walk: (FakeUiNode) -> FakeUiNode? = { walked(it, compass) }
            val expected = expectedPickup(type, compass, found, walk)
            val actual = run(PICK.legacyPickup(null, "x", compass, type, null, BINDING), found)
            if (expected != actual) wrong += "pickup(${found.map { it.name }}, $compass, ${show(type)}): $actual / $expected"
            cases++
        }
        assertEquals(emptyList<String>(), wrong.take(20))
        assertEquals(2 * COMPASSES.size * TYPES.size, cases)
    }

    @Test
    fun detectResultsByCompass() {
        val wrong = mutableListOf<String>()
        for (node in listOf(a, null)) for (compass in COMPASSES) for (type in DETECT_TYPES) {
            val one = node?.let { walked(it, compass) }
            val expected: Any? = when (type) {
                null, "" -> null
                "#" -> one?.name
                "$" -> one?.content() ?: ""
                "." -> one?.let(::point)
                "?" -> if (compass == null) Thrown("none") else one != null
                "@" -> if (compass == null) SELECTOR else Thrown("TypeError")
                "text" -> one?.text()
                1.0 -> null
                "nope" -> if (one != null) Thrown("RangeError") else null
                else -> throw AssertionError("$type")
            }
            // `?` without a compass asks a new selector of `detect` (none here: the test target has no search).
            // zh-CN: 不带罗盘的 `?` 询问 `detect` 的新选择器 (测试目标没有搜索).
            if (expected is Thrown && expected.name == "none") continue
            val actual = run(PICK.legacyDetect(node, compass, type, null), emptyList())
            if (expected != actual) wrong += "detect(${node?.name}, $compass, ${show(type)}): $actual / $expected"
        }
        assertEquals(emptyList<String>(), wrong.take(20))
        // Plurals of one node are refused whether or not a node is given. zh-CN: 单个节点的复数结果无论有无节点都被拒绝.
        listOf(a, null).forEach { assertEquals(Thrown("RangeError"), run(PICK.legacyDetect(it, null, "widgets", null), emptyList())) }
    }

    @Test
    fun callbacks() {
        val calls = mutableListOf<Any?>()
        val keeping = Callable { _, _, _, args -> calls += args[0]; Undefined.instance }
        val replacing = Callable { _, _, _, args -> calls += args[0]; "cb" }
        assertEquals("A", run(PICK.legacyPickup(null, "x", null, "text", keeping, BINDING), listOf(a, b)))
        assertEquals(listOf<Any?>("A"), calls)
        assertEquals("cb", run(PICK.legacyPickup(null, "x", null, "text", replacing, BINDING), listOf(a, b)))
        // An empty search calls the callback with null; a value without a result as well. zh-CN: 无命中时以 null 调用回调; 无结果的值同样.
        assertEquals("cb", run(PICK.legacyPickup(null, "x", null, "#", replacing, BINDING), emptyList()))
        assertEquals("cb", run(PICK.legacyPickup(null, "x", null, 1.0, replacing, BINDING), listOf(a)))
        assertEquals(listOf<Any?>("A", "A", null, null), calls)
        // The empty string has no result and no callback. zh-CN: 空串没有结果, 也不调用回调.
        assertEquals(null, run(PICK.legacyPickup(null, "x", null, "", replacing, BINDING), listOf(a)))
        assertEquals(null, run(PICK.legacyDetect(a, null, null, replacing), emptyList()))
        assertEquals(4, calls.size)
        assertEquals(listOf("a0"), run(PICK.legacyPickup(null, "x", "c0", "[]", keeping, BINDING), listOf(a, b)))
        // The strict entries of the new layer give the same requests for accepted arguments. zh-CN: 新层的严格入口对接受的参数给出相同的请求.
        assertEquals("cb", run(PICK.pickup(arrayOf<Any?>("x", "c0", "$", replacing), BINDING), listOf(a, b)))
        assertEquals("D0", run(PICK.pickup(arrayOf<Any?>("x", "c0", "$"), BINDING), listOf(a, b)))
        assertEquals("B", run(PICK.detect(arrayOf<Any?>(a, "s1", "text")), emptyList()))
    }

    @Test
    fun refusalsOfTheNewLayerComeFirst() {
        // The strict normalizer refuses what the legacy request carries; the executor never sees it.
        // zh-CN: 严格归一化器拒绝现有层请求所携带的错误; 执行器看不到这些请求.
        assertTrue(PICK.pickup(arrayOf<Any?>("x", "p", "@"), BINDING) is PickVerdict.Refused)
        assertTrue(PICK.pickup(arrayOf<Any?>("x", "nope"), BINDING) is PickVerdict.Refused)
        assertTrue(PICK.pickup(arrayOf<Any?>("x", "c"), BINDING) is PickVerdict.Refused)
        // The legacy request throws the compass refusal once a node is walked. zh-CN: 现有层请求在行走节点时抛出罗盘错误.
        assertEquals(Thrown("RangeError"), run(PICK.legacyPickup(null, "x", "c", "#", null, BINDING), listOf(a)))
        assertEquals(null, run(PICK.legacyPickup(null, "x", "c", "#", null, BINDING), emptyList()))
    }

    private fun expectedPickup(type: Any?, compass: String?, found: List<FakeUiNode>, walk: (FakeUiNode) -> FakeUiNode?): Any? {
        val one = found.firstOrNull()?.let(walk)
        val nodes = found.mapNotNull(walk)
        return when (type) {
            "#" -> one?.name
            "{}" -> Coll(nodes.map { it.name })
            "[]" -> nodes.map { it.name }
            "$" -> one?.content() ?: ""
            "contents" -> nodes.map { it.content() }
            "." -> one?.let(::point)
            "points" -> nodes.map(::point)
            "@" -> if (compass == null) SELECTOR else Thrown("TypeError")
            "?" -> if (compass == null) found.isNotEmpty() else one != null
            "text" -> one?.text()
            listOf("child", 0.0) -> one?.child(0)?.name
            listOf("#") -> one?.let { nodes.map { it.name } }
            "", 1.0 -> null
            "nope" -> if (one != null) Thrown("RangeError") else null
            else -> throw AssertionError("$type")
        }
    }

    /** [node] walked by [compass] as `UiObject.compass` walks it (none: the node itself). */
    private fun walked(node: FakeUiNode, compass: String?): FakeUiNode? = when (compass) {
        null -> node
        else -> Compass.navigate(node, compass)
    }

    /** Executes [verdict] over [found], the result as names and lists, or the error thrown. */
    private fun run(verdict: PickVerdict<FakeUiNode>, found: List<FakeUiNode>): Any? {
        val request = (verdict as? PickVerdict.Accepted)?.request ?: throw AssertionError("refused: ${(verdict as PickVerdict.Refused).error}")
        return try {
            plain(PickExecutor(request, Target(request, found)).execute())
        } catch (e: EcmaError) {
            Thrown(e.name)
        }
    }

    private fun plain(value: Any?): Any? = when (value) {
        is FakeUiNode -> value.name
        is List<*> -> value.map(::plain)
        else -> value
    }

    private fun point(node: FakeUiNode) = "${node.centerX()},${node.centerY()}"

    private fun show(type: Any?) = if (type is String) "'$type'" else "$type"

    private data class Thrown(val name: String)

    private data class Coll(val names: List<String>)

    /** A target of plain lists: [found] is what the selector finds, the node of `detect` is the root of the request. */
    private inner class Target(private val request: PickRequest<FakeUiNode>, private val found: List<FakeUiNode>) : PickTarget<FakeUiNode> {
        override fun one(): FakeUiNode? = if (request.selector == null) request.root else found.firstOrNull()
        override fun all(): List<FakeUiNode> = found
        override fun exists() = found.isNotEmpty()
        override fun selector(): Any = SELECTOR
        override fun collection(nodes: List<FakeUiNode>): Any = Coll(nodes.map { it.name })
        override fun array(values: List<Any?>): Any = values.toList()
        override fun point(node: FakeUiNode): Any = this@PickExecutorTest.point(node)
        override fun call(callback: Callable, value: Any?): Any? = callback.call(null, null as VarScope?, null, arrayOf<Any?>(plain(value)))
        override fun toScript(value: Any?): Any? = value
    }

    private companion object {
        const val SELECTOR = "<selector>"
        val COMPASSES = listOf(null, "c0", "p", "s1", "")
        val TYPES = listOf<Any?>("#", "{}", "[]", "$", "contents", ".", "points", "@", "?", "text", listOf("child", 0.0), listOf("#"), "", 1.0, "nope")
        val DETECT_TYPES = listOf<Any?>(null, "", "#", "$", ".", "?", "@", "text", 1.0, "nope")
        val METHODS = NodeMethodTable(FakeUiNode::class.java, UiNode::class.java.methods.filter { !Modifier.isStatic(it.modifiers) })
        val PICK = PickArguments(FakeUiNode::class.java, METHODS)
        val BINDING = PickArguments.Binding({ UiSelector(null as AccessibilityBridge?) }, { BooleanFilter.BooleanSupplier { true } })
    }

}
