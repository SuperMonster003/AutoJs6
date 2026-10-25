package org.autojs.autojs.core.automator.pick

import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Undefined
import java.lang.reflect.Modifier

/**
 * docs/selector/ROADMAP.md P1.1: [PickArguments] over [FakeUiNode] on the JVM. The 17 forms of `pickup` and the 7 of
 * `detect` (positions told by the types of the arguments, the root rule: a node first from two arguments on, the first
 * of five always), each class of argument error with its kind, function and parameter names, the order the new layer
 * tells them in (root, compass and callback types, selector, compass syntax, result type), and the timing of the
 * entries of the current augment layer (a selector refused before the search, a compass or result type carried in the
 * request). Every verdict is returned, none thrown.
 *
 * zh-CN: P1.1: 在 JVM 上以 [FakeUiNode] 检查 [PickArguments]. `pickup` 的 17 种与 `detect` 的 7 种形式 (按参数类型区分位置, root 规则: 两个参数起首个为节点,
 * 五个参数时首个总是), 每类参数错误的种类, 函数名与参数名, 新层判定的顺序 (root, 罗盘与回调的类型, 选择器, 罗盘语法, 结果类型), 以及现有 augment 层入口的
 * 时机 (选择器在搜索前拒绝, 罗盘与结果类型随请求携带). 所有判定都以返回值给出, 不抛异常.
 */
class PickArgumentsTest {

    private val node = FakeUiNode.root()
    private val callback = Callable { _, _, _, _ -> Undefined.instance }
    private val p2 = listOf(CompassStep.Up(2))

    @Test
    fun pickupForms() {
        val n = node
        val c = callback
        // args to (root, compass steps or null for none, content result, callback)
        val forms = listOf(
            arrayOf<Any?>() to Expected(null, null, false, false),
            arrayOf<Any?>(S) to Expected(null, null, false, false),
            arrayOf<Any?>(n, S) to Expected(n, null, false, false),
            arrayOf<Any?>(S, c) to Expected(null, null, false, true),
            arrayOf<Any?>(S, "p2") to Expected(null, p2, false, false),
            arrayOf<Any?>(S, "$") to Expected(null, null, true, false),
            arrayOf<Any?>(n, S, c) to Expected(n, null, false, true),
            arrayOf<Any?>(n, S, "p2") to Expected(n, p2, false, false),
            arrayOf<Any?>(n, S, "$") to Expected(n, null, true, false),
            arrayOf<Any?>(S, "p2", c) to Expected(null, p2, false, true),
            arrayOf<Any?>(S, "$", c) to Expected(null, null, true, true),
            arrayOf<Any?>(S, "p2", "$") to Expected(null, p2, true, false),
            arrayOf<Any?>(n, S, "p2", c) to Expected(n, p2, false, true),
            arrayOf<Any?>(n, S, "$", c) to Expected(n, null, true, true),
            arrayOf<Any?>(n, S, "p2", "$") to Expected(n, p2, true, false),
            arrayOf<Any?>(S, "p2", "$", c) to Expected(null, p2, true, true),
            arrayOf<Any?>(n, S, "p2", "$", c) to Expected(n, p2, true, true),
        )
        assertEquals(17, forms.size)
        forms.forEachIndexed { i, (args, expected) -> check("pickup form ${i + 1}", accepted(PICK.pickup(args, BINDING)), expected, pickup = true) }
        // The first of five arguments is the root even when it is null; a node alone is a selector form (refused).
        // zh-CN: 五个参数时首个即使为 null 也是 root; 单独的节点是选择器形式 (被拒绝).
        check("pickup null root", accepted(PICK.pickup(arrayOf<Any?>(null, S, "p2", "$", c), BINDING)), Expected(null, p2, true, true), pickup = true)
        assertError(PICK.pickup(arrayOf<Any?>(n), BINDING), PickError.Kind.TYPE, "pickup: argument \"selector\" must be of type PickupSelector, got ${FakeUiNode::class.java.name}")
    }

    @Test
    fun detectForms() {
        val n = node
        val c = callback
        val forms = listOf(
            arrayOf<Any?>(n, c) to Expected(n, null, false, true),
            arrayOf<Any?>(n, "p2") to Expected(n, p2, false, false),
            arrayOf<Any?>(n, "$") to Expected(n, null, true, false),
            arrayOf<Any?>(n, "p2", c) to Expected(n, p2, false, true),
            arrayOf<Any?>(n, "$", c) to Expected(n, null, true, true),
            arrayOf<Any?>(n, "p2", "$") to Expected(n, p2, true, false),
            arrayOf<Any?>(n, "p2", "$", c) to Expected(n, p2, true, true),
        )
        assertEquals(7, forms.size)
        forms.forEachIndexed { i, (args, expected) -> check("detect form ${i + 1}", accepted(PICK.detect(args)), expected, pickup = false) }
        check("detect null node", accepted(PICK.detect(arrayOf<Any?>(null, "$"))), Expected(null, null, true, false), pickup = false)
    }

    @Test
    fun argumentCountsAndTypes() {
        assertError(PICK.pickup(arrayOfNulls(6), BINDING), PickError.Kind.TYPE, "pickup accepts 0 to 5 arguments, got 6")
        assertError(PICK.detect(arrayOf<Any?>()), PickError.Kind.TYPE, "detect accepts 2 to 4 arguments, got 0")
        assertError(PICK.detect(arrayOf<Any?>(node)), PickError.Kind.TYPE, "detect accepts 2 to 4 arguments, got 1")
        assertError(PICK.detect(arrayOfNulls(5)), PickError.Kind.TYPE, "detect accepts 2 to 4 arguments, got 5")
        assertError(PICK.pickup(arrayOf<Any?>(1.0, S, "p", "w", null), BINDING), PickError.Kind.TYPE, "pickup: argument \"root\" must be of type UiObject, got number")
        assertError(PICK.detect(arrayOf<Any?>(1.0, "p")), PickError.Kind.TYPE, "detect: argument \"w\" must be of type UiObject, got number")
        assertError(PICK.pickup(arrayOf<Any?>(S, 1.0, "w", null), BINDING), PickError.Kind.TYPE, "pickup: argument \"compass\" must be of type string, got number")
        assertError(PICK.pickup(arrayOf<Any?>(S, ">", "w", 1.0), BINDING), PickError.Kind.TYPE, "pickup: argument \"callback\" must be of type function, got number")
        assertError(PICK.detect(arrayOf<Any?>(null, "p", "w", "f")), PickError.Kind.TYPE, "detect: argument \"callback\" must be of type function, got string")
        // Nullish compass and callback are the defaults. zh-CN: 空值的罗盘与回调即默认值.
        accepted(PICK.pickup(arrayOf<Any?>(S, null, "w", Undefined.instance), BINDING)).let {
            assertSame(PickCompass.PassOn, it.compass)
            assertNull(it.callback)
        }
    }

    @Test
    fun selectorErrors() {
        assertError(PICK.pickup(arrayOf<Any?>(true), BINDING), PickError.Kind.TYPE, "pickup: argument \"selector\" must be of type PickupSelector, got boolean")
        assertError(PICK.pickup(arrayOf<Any?>(listOf("a", listOf(true))), BINDING), PickError.Kind.TYPE, "pickup: argument \"selector[1][0]\" must be of type PickupSelector, got boolean")
        val unknown = error(PICK.pickup(arrayOf<Any?>(mapOf("txet" to "a")), BINDING), PickError.Kind.TYPE)
        assertTrue(unknown, unknown.startsWith("pickup: argument \"selector\": \"txet\" is not a condition of UiSelector, candidates: "))
        candidates(unknown).let { assertTrue("$it", it.size in 1..5 && "text" in it) }
        assertError(PICK.pickup(arrayOf<Any?>(mapOf("clickable" to "x")), BINDING), PickError.Kind.TYPE, "pickup: argument \"selector.clickable\" must be of type boolean | null, got string")
        assertError(PICK.pickup(arrayOf<Any?>(listOf("a", mapOf("depth" to Double.NaN))), BINDING), PickError.Kind.RANGE, "pickup: argument \"selector[1].depth\" cannot be NaN (Cannot round NaN value.)")
        error(PICK.pickup(arrayOf<Any?>(mapOf("textMatches" to "[")), BINDING), PickError.Kind.RANGE).let { assertTrue(it, it.startsWith("pickup: argument \"selector.textMatches\" cannot be \"[\" (")) }
        error(PICK.pickup(arrayOf<Any?>(mapOf("algorithm" to "x")), BINDING), PickError.Kind.RANGE).let { assertTrue(it, it.endsWith("(Unknown algorithm selector param: x)")) }
        // Accepted forms; `action: null` is skipped. zh-CN: 接受的形式; `action: null` 被跳过.
        listOf(null, "a", 1.0, java.math.BigInteger.ONE, UiSelector(null as AccessibilityBridge?), listOf("a", null, listOf(1.0)), mapOf("text" to "a", "depth" to 2.0, "action" to null), emptyList<Any?>())
            .forEach { assertNull("$it", PICK.selectorError("pickup", "selector", it)) }
        assertTrue(PICK.pickup(arrayOf<Any?>(mapOf("filter" to callback)), BINDING) is PickVerdict.Accepted)
    }

    @Test
    fun compassAndResultTypeErrors() {
        assertError(PICK.pickup(arrayOf<Any?>(S, "c"), BINDING), PickError.Kind.RANGE, "pickup: argument \"compass\" cannot be \"c\" (Invalid rest compass: c)")
        assertError(PICK.pickup(arrayOf<Any?>(S, "x2", "w"), BINDING), PickError.Kind.RANGE, "pickup: argument \"compass\" cannot be \"x2\" (Invalid compass: x2)")
        assertError(PICK.detect(arrayOf<Any?>(node, "s", "w")), PickError.Kind.RANGE, "detect: argument \"compass\" cannot be \"s\" (Invalid rest compass: s)")
        assertError(PICK.pickup(arrayOf<Any?>(S, "p", "@"), BINDING), PickError.Kind.TYPE, "pickup: argument \"resultType\" cannot be \"@\" (Cannot use a compass when result type is selector)")
        assertError(PICK.detect(arrayOf<Any?>(node, "widgets")), PickError.Kind.RANGE, "detect: argument \"resultType\" cannot be \"widgets\" (Result type \"widgets\" is not supported for UiObject)")
        assertError(PICK.detect(arrayOf<Any?>(node, listOf("#"))), PickError.Kind.RANGE, "detect: argument \"resultType\" cannot be [\"#\"] (Result type \"[#]\" is not supported for UiObject)")
        assertError(PICK.pickup(arrayOf<Any?>(S, listOf("child", "0")), BINDING), PickError.Kind.RANGE, "pickup: argument \"resultType\" cannot be [\"child\", \"0\"] (child(int))")
        assertError(PICK.pickup(arrayOf<Any?>(S, listOf("child", Double.NaN)), BINDING), PickError.Kind.RANGE, "pickup: argument \"resultType\" cannot be [\"child\", NaN] (Cannot round NaN value.)")
        val unknown = error(PICK.pickup(arrayOf<Any?>(S, "texts"), BINDING), PickError.Kind.RANGE)
        assertTrue(unknown, unknown.startsWith("pickup: argument \"resultType\": \"texts\" is neither a result type nor a method of UiObject, candidates: "))
        candidates(unknown).let { assertTrue("$it", it.size in 1..5 && "text" in it) }
        candidates(error(PICK.pickup(arrayOf<Any?>(S, "zzzzzzzzzz"), BINDING), PickError.Kind.RANGE)).let { assertEquals(listOf("#", "{}", "[]", "$", "."), it) }
        // Results told without calling anything. zh-CN: 不调用任何方法即可判定的结果.
        accepted(PICK.pickup(arrayOf<Any?>(S, listOf("child", 0.0)), BINDING)).result.let { assertEquals("child", (it as PickResult.Call).method.method.name) }
        assertTrue(accepted(PICK.pickup(arrayOf<Any?>(S, listOf("#")), BINDING)).result is PickResult.ArrayForm)
        // The empty string is a compass in the second position, the empty result type in the third. zh-CN: 空串在第二位是罗盘, 在第三位是空结果类型.
        assertTrue(accepted(PICK.pickup(arrayOf<Any?>(S, ""), BINDING)).compass is PickCompass.Steps)
        assertSame(PickResult.Empty, accepted(PICK.pickup(arrayOf<Any?>(S, "p", ""), BINDING)).result)
        assertSame(PickResult.Nothing, accepted(PICK.pickup(arrayOf<Any?>(S, listOf("")), BINDING)).result)
        assertNull(PICK.resultTypeError("pickup", "resultType", "?", "p2", single = false))
        assertNotNull(PICK.resultTypeError("detect", "resultType", "wc", null, single = true))
        assertNull(PICK.compassError("pickup", "compass", "c0>1"))
        assertNotNull(PICK.compassError("pickup", "compass", "c0>"))
    }

    /** The new layer tells the errors in this order whatever position they are in. zh-CN: 新层按此顺序判定错误, 与位置无关. */
    @Test
    fun strictOrder() {
        val c = callback
        val bad = mapOf("nope" to 1.0)
        assertError(PICK.pickup(arrayOf<Any?>(1.0, bad, 2.0, "nope", 3.0), BINDING), PickError.Kind.TYPE, "pickup: argument \"root\" must be of type UiObject, got number")
        assertError(PICK.pickup(arrayOf<Any?>(node, bad, 2.0, "nope", 3.0), BINDING), PickError.Kind.TYPE, "pickup: argument \"compass\" must be of type string, got number")
        assertError(PICK.pickup(arrayOf<Any?>(node, bad, "c", "nope", 3.0), BINDING), PickError.Kind.TYPE, "pickup: argument \"callback\" must be of type function, got number")
        assertTrue(error(PICK.pickup(arrayOf<Any?>(node, bad, "c", "nope", c), BINDING), PickError.Kind.TYPE).contains("is not a condition of UiSelector"))
        assertError(PICK.pickup(arrayOf<Any?>(node, S, "c", "nope", c), BINDING), PickError.Kind.RANGE, "pickup: argument \"compass\" cannot be \"c\" (Invalid rest compass: c)")
        assertTrue(error(PICK.pickup(arrayOf<Any?>(node, S, "p", "nope", c), BINDING), PickError.Kind.RANGE).contains("is neither a result type nor a method"))
    }

    /** The entries of the current augment layer: the selector refused before the search, the rest carried. zh-CN: 现有 augment 层入口的时机. */
    @Test
    fun legacyTiming() {
        assertTrue(PICK.legacyPickup(null, mapOf("nope" to 1.0), null, null, null, BINDING) is PickVerdict.Refused)
        accepted(PICK.legacyPickup(null, S, "c", "w", null, BINDING)).let {
            assertEquals(PickError.Kind.RANGE, (it.compass as PickCompass.Refused).error.kind)
            assertTrue(it.result is PickResult.Symbol)
        }
        (accepted(PICK.legacyPickup(null, S, null, "nope", null, BINDING)).result as PickResult.Refused).let {
            assertEquals(PickError.Kind.RANGE, it.error.kind)
            assertEquals(false, it.always)
        }
        (accepted(PICK.legacyPickup(null, S, "p", "@", null, BINDING)).result as PickResult.Refused).let {
            assertEquals(PickError.Kind.TYPE, it.error.kind)
            assertEquals(true, it.always)
        }
        assertEquals(true, (accepted(PICK.legacyDetect(node, null, "widgets", null)).result as PickResult.Refused).always)
        assertEquals(PickResultType.Widget, (accepted(PICK.legacyPickup(null, S, null, null, null, BINDING)).result as PickResult.Symbol).type)
        assertSame(PickCompass.PassOn, accepted(PICK.legacyPickup(null, S, "%", null, null, BINDING)).compass)
        // `detect` of the current layer: a null result type has no result, the node is no search. zh-CN: 现有层的 `detect`: null 结果类型没有结果.
        accepted(PICK.legacyDetect(node, null, null, callback)).let {
            assertSame(PickResult.Empty, it.result)
            assertSame(node, it.root)
            assertNull(it.selector)
        }
    }

    @Test
    fun selectorFormShapes() {
        listOf("a", 1.0, 1, java.math.BigInteger.TEN, mapOf("a" to 1), listOf(1), UiSelector(null as AccessibilityBridge?)).forEach { assertTrue("$it", PickArguments.isSelectorForm(it)) }
        listOf(null, true, node, callback, Undefined.instance).forEach { assertTrue("$it", !PickArguments.isSelectorForm(it)) }
        assertNull(PickArguments.patternError("f", "p", "a", "a(b)"))
        assertEquals(PickError.Kind.RANGE, PickArguments.patternError("f", "p", "[", "[")!!.kind)
    }

    private data class Expected(val root: FakeUiNode?, val compass: List<CompassStep>?, val content: Boolean, val callback: Boolean)

    private fun check(what: String, request: PickRequest<FakeUiNode>, expected: Expected, pickup: Boolean) {
        assertSame(what, expected.root, request.root)
        assertEquals(what, pickup, request.selector != null)
        when (val compass = request.compass) {
            PickCompass.PassOn -> assertNull(what, expected.compass)
            is PickCompass.Steps -> assertEquals(what, expected.compass, compass.steps)
            is PickCompass.Refused -> throw AssertionError("$what: ${compass.error}")
        }
        assertEquals(what, if (expected.content) PickResultType.Content else PickResultType.Widget, (request.result as PickResult.Symbol).type)
        assertSame(what, if (expected.callback) callback else null, request.callback)
    }

    private fun accepted(verdict: PickVerdict<FakeUiNode>): PickRequest<FakeUiNode> = when (verdict) {
        is PickVerdict.Accepted -> verdict.request
        is PickVerdict.Refused -> throw AssertionError("${verdict.error}")
    }

    private fun error(verdict: PickVerdict<FakeUiNode>, kind: PickError.Kind): String {
        val error = (verdict as? PickVerdict.Refused)?.error ?: throw AssertionError("accepted")
        assertEquals(error.message, kind, error.kind)
        return error.message
    }

    private fun assertError(verdict: PickVerdict<FakeUiNode>, kind: PickError.Kind, message: String) = assertEquals(message, error(verdict, kind))

    private fun candidates(message: String) = message.substringAfter("candidates: ").split(", ")

    private companion object {
        const val S = "aj6-none"

        /** The methods of a node of the tests: those of [UiNode]. zh-CN: 测试节点的方法: [UiNode] 的方法. */
        val METHODS = NodeMethodTable(FakeUiNode::class.java, UiNode::class.java.methods.filter { !Modifier.isStatic(it.modifiers) })

        val PICK = PickArguments(FakeUiNode::class.java, METHODS)

        val BINDING = PickArguments.Binding({ UiSelector(null as AccessibilityBridge?) }, { BooleanFilter.BooleanSupplier { true } })
    }

}
