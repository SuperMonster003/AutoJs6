package org.autojs.autojs.core.automator.diagnostics

import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.pick.NodeMethodTable
import org.autojs.autojs.core.automator.pick.PickArguments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * [PickExplainer] (docs/selector/ROADMAP.md P3.5): the single result with and without `prefer(...)`, the node each step
 * of the compass reaches up to the step that reaches none, the result types as `pickup` reads them (symbols, the array
 * form, a registry method with its converted arguments, the refusals kept for the report), and the fast path, the refresh
 * on hit and `normalize(...)` lines of the report, over [FakeUiNode].
 * zh-CN: [PickExplainer] (P3.5): 有无 `prefer(...)` 时的单个结果, 罗盘每一步到达的节点直至没有到达节点的步骤, 按 `pickup` 方式读取的结果类型 (符号, 数组形式,
 * 注册表方法及转换后的参数, 拒绝留在报告中), 以及报告中的快路径, 命中刷新与 `normalize(...)` 行, 在 [FakeUiNode] 上.
 */
class PickExplainerTest {

    private val root = FakeUiNode.root("root") {
        bounds(0, 0, 1080, 1920)
        child("panel", clickable = true) {
            bounds(0, 0, 1080, 400)
            // The first match, not visible to the user. zh-CN: 首个匹配, 对用户不可见.
            child("hidden") {
                text = "OK"
                visibleToUser = false
                bounds(0, 0, 100, 50)
            }
            child("shown") {
                text = "OK"
                id = "com.example:id/ok"
                bounds(0, 100, 100, 150)
            }
            child("label") {
                text = "Label"
            }
        }
    }

    private fun selector(build: UiSelector.() -> Unit) = UiSelector(null as AccessibilityBridge?).apply { build() }

    private fun explain(
        selector: UiSelector,
        vararg rest: Any?,
        settings: PickExplainer.Settings = PickExplainer.Settings(),
    ): PickExplanation<FakeUiNode> {
        val selection = SelectorExplainer.explain(listOf(root), selector.selector.effectiveFilters, selector.toString())
        val parts = PICK.explainParts("auto.explain", rest.toList())
        return PickExplainer.explain(listOf(root), selector.selector, selection, parts, settings, { it.refresh() })
    }

    private fun lines(explanation: PickExplanation<FakeUiNode>) = explanation.render { it.name }.lines()

    @Test
    fun theFirstMatchAndTheCompassStepByStep() {
        val explanation = explain(selector { text("OK") }, "p1c2", "text")
        assertEquals("hidden", explanation.target?.name)
        assertEquals(0, explanation.targetIndex)
        assertEquals(2, explanation.candidates)
        assertEquals(listOf("p1", "c2"), explanation.trail.map { it.step })
        assertEquals(listOf("panel", "label"), explanation.trail.map { it.node?.name })
        assertEquals("label", explanation.reached?.name)
        assertEquals("method", explanation.result?.kind)
        assertEquals(
            listOf(
                "single result: hidden (first of 2 match(es))",
                "compass \"p1c2\":",
                "  1. p1  -> panel",
                "  2. c2  -> label",
                "result \"text\": method UiNode.text(): String, called on the node the compass reaches (explain calls nothing)",
                "fast path: none, no exact id condition",
                "refresh on hit: on, the single result refreshes",
                "normalize: off",
            ),
            lines(explanation),
        )
    }

    @Test
    fun preferTakesTheBestAndTheStepKPassesOverInvisibleAncestors() {
        val explanation = explain(selector { text("OK").prefer("visible") }, "k")
        assertEquals("shown", explanation.target?.name)
        assertEquals(1, explanation.targetIndex)
        assertEquals("prefer(\"visible\")", explanation.preference)
        assertEquals(listOf("k1"), explanation.trail.map { it.step })
        assertEquals("panel", explanation.reached?.name)
        assertNull(explanation.result)
        assertEquals("single result: shown (match 2 of 2 by prefer(\"visible\"))", lines(explanation)[0])
    }

    @Test
    fun theWalkStopsAtTheStepThatReachesNothing() {
        val explanation = explain(selector { text("Label") }, "c0p1")
        assertEquals(listOf("c0"), explanation.trail.map { it.step })
        assertNull(explanation.trail.single().node)
        assertNull(explanation.reached)
        assertEquals(listOf("compass \"c0p1\":", "  1. c0  -> nothing, the walk stops here"), lines(explanation).subList(1, 3))
        val none = explain(selector { text("Nothing") }, "p1")
        assertNull(none.target)
        assertTrue(none.trail.isEmpty())
        assertEquals(listOf("single result: none", "compass \"p1\": not walked, no single result"), lines(none).subList(0, 2))
        assertEquals("compass \"%\": the node itself", lines(explain(selector { text("Label") }, "%"))[1])
    }

    @Test
    fun theResultTypesAsPickupReadsThem() {
        fun result(vararg rest: Any?) = explain(selector { text("Label") }, *rest).result!!
        assertEquals("symbol", result("#").kind)
        assertEquals("symbol \"#\", the node", result("w").detail)
        assertEquals("\"widget\" (default)", result("p1", null).given)
        assertEquals("symbol \"@\", the selector itself", result("@").detail)
        val child = result(listOf("child", 1.0))
        assertEquals("[\"child\", 1]", child.given)
        assertEquals("method UiNode.child(int): UiNode, arguments [1] -> [1 (Integer)], called on the node the compass reaches (explain calls nothing)", child.detail)
        assertEquals("arrayForm", result(listOf("#")).kind)
        assertEquals("no method of that name, the array form: symbol \"[]\", an array of the nodes found", result(listOf("#")).detail)
        // The empty string alone is a compass, so the empty result type follows one. zh-CN: 单独的空串是罗盘, 因此空结果类型跟在罗盘之后.
        assertEquals("empty", result("p1", "").kind)
        assertEquals("nothing", result(3.0).kind)
        // Refusals are reported, not thrown. zh-CN: 拒绝写进报告, 不抛出.
        val selectorWithCompass = result("p1", "@")
        assertEquals("refused", selectorWithCompass.kind)
        assertTrue(selectorWithCompass.detail, selectorWithCompass.detail.startsWith("refused, TypeError: "))
        assertTrue(result("nope").detail.startsWith("refused, RangeError: "))
        val badCompass = explain(selector { text("Label") }, "p2x", "#")
        assertTrue(badCompass.compassError!!, badCompass.compassError!!.startsWith("RangeError: "))
        assertNull(badCompass.reached)
        assertTrue(explain(selector { text("Label") }, 3.0, "#").compassError!!.startsWith("TypeError: "))
    }

    @Test
    fun theFastPathTheRefreshOnHitAndNormalize() {
        val byId = selector { id("com.example:id/ok") }
        assertEquals("fast path: id(com.example:id/ok), looked up by polled queries (findOne with a timeout, untilFindOne, waitFor)", lines(explain(byId))[1])
        assertEquals("fast path: off (flag noFastPath), key id(com.example:id/ok)", lines(explain(byId, settings = PickExplainer.Settings(fastPath = false)))[1])
        root.find("shown").refreshable = false
        assertEquals(false, explain(byId).refreshes)
        assertEquals("refresh on hit: on, the single result has left the window: a single-result query searches again", lines(explain(byId))[2])
        val off = explain(byId, settings = PickExplainer.Settings(refreshOnHit = false))
        assertNull(off.refreshes)
        assertEquals("refresh on hit: off (flag noRefreshOnHit)", lines(off)[2])
        val normalized = explain(selector { text("ok").normalize() })
        assertEquals("hidden", normalized.target?.name)
        assertEquals("normalize: normalize()", lines(normalized).last())
        // A normalized id has no exact value, so no fast path. zh-CN: 规范化的 id 没有精确值, 因此没有快路径.
        assertEquals("fast path: none, no exact id condition", lines(explain(selector { id("com.example:id/OK").normalize() }))[1])
    }

    private companion object {
        val METHODS = NodeMethodTable(FakeUiNode::class.java, UiNode::class.java.methods.filter { !Modifier.isStatic(it.modifiers) })
        val PICK = PickArguments(FakeUiNode::class.java, METHODS)
    }

}
