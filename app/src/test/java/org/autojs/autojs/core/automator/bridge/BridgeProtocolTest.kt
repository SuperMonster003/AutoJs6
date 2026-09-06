package org.autojs.autojs.core.automator.bridge

import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.NodeRecyclePolicy
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.search.DFS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Android-free core of the Node Bridge's frozen `accessibility` protocol (roadmap P5.4): the
 * selector descriptor's filter semantics, the node descriptor and the climbing node actions, over
 * a fake tree.
 * zh-CN: Node Bridge 冻结的 `accessibility` 协议的 Android-free 核心 (Roadmap P5.4): 选择器描述符的过滤语义, 节点描述符与上溯的节点动作,
 * 在假节点树上验证.
 */
class BridgeProtocolTest {

    //        root (FrameLayout, 0 0 1080 1920)
    //       /          \
    //   login          list (scrollable, desc "Menu")
    //   (Button,       /    \
    //    "Login")   row0     row1 (inverted bounds)
    //                 \
    //                 label ("Deep", not clickable, under a clickable row)
    private fun tree() = FakeUiNode.root {
        className = "android.widget.FrameLayout"
        bounds(0, 0, 1080, 1920)
        child("login") {
            text = "Login"
            className = "android.widget.Button"
            id = "com.example:id/login"
            clickable = true
            bounds(10, 20, 110, 70)
        }
        child("list") {
            desc = "Menu"
            scrollable = true
            className = "androidx.recyclerview.widget.RecyclerView"
            bounds(0, 100, 1080, 1900)
            child("row0", clickable = true) {
                bounds(0, 100, 1080, 200)
                child("label") {
                    text = "Deep"
                    bounds(20, 120, 200, 180)
                }
            }
            child("row1") {
                text = "Row 1"
                enabled = false
                bounds(221, 2372, 612, 2361)
            }
        }
    }

    private val noRecycling = NodeLifecycle(NodeRecyclePolicy.NO_OP)

    private fun names(selector: BridgeSelector, root: FakeUiNode = tree()) = DFS.search(root, selector.toFilter(), lifecycle = noRecycling).map { it.name }

    @Test
    fun emptyDescriptorMatchesNothing() {
        assertTrue(BridgeSelector().isEmpty)
        assertEquals(emptyList<String>(), names(BridgeSelector()))
        assertEquals("nothing()", BridgeSelector().toFilter().toString())
        assertFalse(BridgeSelector(clickable = false).isEmpty)
    }

    @Test
    fun stringConditionsCompareLiterallyContainSubstringsAndMatchWholeValues() {
        assertEquals(listOf("login"), names(BridgeSelector(text = "Login")))
        assertEquals(emptyList<String>(), names(BridgeSelector(text = "login")))
        assertEquals(listOf("login"), names(BridgeSelector(textContains = "ogi")))
        assertEquals(listOf("login"), names(BridgeSelector(textMatches = Regex("log.*", RegexOption.IGNORE_CASE))))
        assertEquals(emptyList<String>(), names(BridgeSelector(textMatches = Regex("log"))))
        assertEquals(listOf("list"), names(BridgeSelector(desc = "Menu")))
        assertEquals(listOf("list"), names(BridgeSelector(descContains = "en")))
        assertEquals(listOf("login"), names(BridgeSelector(id = "com.example:id/login")))
        // No short-id or short-class-name expansion, unlike the script selector's id() / className().
        // zh-CN: 不做短 id / 短类名展开, 与脚本选择器的 id() / className() 不同.
        assertEquals(emptyList<String>(), names(BridgeSelector(id = "login")))
        assertEquals(listOf("login"), names(BridgeSelector(className = "android.widget.Button")))
        assertEquals(emptyList<String>(), names(BridgeSelector(className = "Button")))
        assertEquals(listOf("login"), names(BridgeSelector(idMatches = Regex(".*:id/login"))))
        assertEquals(listOf("login", "list"), names(BridgeSelector(classNameMatches = Regex(".*(Button|RecyclerView)"))))
        assertEquals("text(\"a\").textContains(\"b\").textMatches(/c/is)", BridgeSelector(text = "a", textContains = "b", textMatches = Regex("c", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))).toFilter().toString())
    }

    @Test
    fun regexConditionsTreatAMissingValueAsEmpty() {
        // The frozen protocol compares the empty string for a node without a description / id / class
        // name, so a pattern accepting the empty string matches such nodes.
        // zh-CN: 冻结的协议对没有描述 / id / 类名的节点比较空串, 因此接受空串的模式匹配这些节点.
        assertEquals(listOf("root", "login", "row0", "label", "row1"), names(BridgeSelector(descMatches = Regex(""))))
        assertEquals(listOf("root", "list", "row0", "label", "row1"), names(BridgeSelector(idMatches = Regex("^$"))))
        assertEquals(listOf("row0", "label", "row1"), names(BridgeSelector(classNameMatches = Regex(".{0}"))))
        assertEquals(emptyList<String>(), names(BridgeSelector(desc = "")))
    }

    @Test
    fun booleanDepthAndBoundsConditions() {
        assertEquals(listOf("login", "row0"), names(BridgeSelector(clickable = true)))
        assertEquals(listOf("row1"), names(BridgeSelector(enabled = false)))
        assertEquals(listOf("list"), names(BridgeSelector(scrollable = true)))
        assertEquals(listOf("row0", "row1"), names(BridgeSelector(depth = 2)))
        assertEquals(listOf("root"), names(BridgeSelector(depth = 0)))
        assertEquals(listOf("root", "login", "list", "row0", "label"), names(BridgeSelector(boundsInside = BridgeBounds(0, 0, 1080, 1920))))
        assertEquals(listOf("row0", "label"), names(BridgeSelector(boundsInside = BridgeBounds(0, 100, 1080, 200))))
        assertEquals(listOf("root", "list"), names(BridgeSelector(boundsContains = BridgeBounds(0, 100, 1080, 1900))))
        assertEquals(listOf("row1"), names(BridgeSelector(boundsInside = BridgeBounds(221, 2361, 612, 2372), enabled = false)))
        assertEquals(listOf("login"), names(BridgeSelector(text = "Login", clickable = true, depth = 1, boundsInside = BridgeBounds(0, 0, 200, 100))))
        assertEquals(emptyList<String>(), names(BridgeSelector(text = "Login", clickable = false)))
    }

    @Test
    fun nodeDescriptorReadsTheSevenFrozenFields() {
        val root = tree()
        val login = BridgeNode.of(root.find("login"))
        assertEquals(BridgeNode("Login", "", "com.example:id/login", "android.widget.Button", BridgeBounds(10, 20, 110, 70), clickable = true, enabled = true), login)
        val list = BridgeNode.of(root.find("list"))
        assertEquals("Menu", list.desc)
        assertEquals("", list.text)
        assertEquals("", list.id)
        // The platform rectangle is passed on as is, inverted axes included.
        // zh-CN: 平台矩形原样传递, 包括颠倒的轴.
        assertEquals(BridgeBounds(221, 2372, 612, 2361), BridgeNode.of(root.find("row1")).bounds)
        assertFalse(BridgeNode.of(root.find("row1")).enabled)
    }

    @Test
    fun clickClimbsToTheNearestEnabledClickableNodeAndFallsBackToTheNodeItself() {
        val root = tree()
        val actions = BridgeNodeActions(NodeLifecycle(NodeRecyclePolicy.RECYCLE))

        assertTrue(actions.click(root.find("label")))
        assertEquals(listOf(BridgeNodeActions.ACTION_CLICK), root.find("row0").performedActions)
        assertTrue(root.find("label").performedActions.isEmpty())
        // The ancestor was released once passed, the node itself was not.
        // zh-CN: 经过的祖先已释放, 节点本身未释放.
        assertTrue(root.find("row0").isRecycled)
        assertFalse(root.find("label").isRecycled)

        assertTrue(actions.click(root.find("login")))
        assertEquals(listOf(BridgeNodeActions.ACTION_CLICK), root.find("login").performedActions)

        // No clickable node up the chain: the node's own ACTION_CLICK decides.
        // zh-CN: 上溯链中没有可点击节点: 由节点自身的 ACTION_CLICK 决定.
        val other = tree()
        val row1 = other.find("row1")
        assertTrue(actions.click(row1))
        assertEquals(listOf(BridgeNodeActions.ACTION_CLICK), row1.performedActions)
        assertTrue(other.find("list").isRecycled)
        assertTrue(other.isRecycled)

        // A refused click on a clickable node continues up and then falls back as well.
        // zh-CN: 可点击节点拒绝点击时继续上溯, 最后同样退回.
        val refusing = tree()
        refusing.find("login").actionResult = false
        assertFalse(actions.click(refusing.find("login")))
        assertEquals(listOf(BridgeNodeActions.ACTION_CLICK, BridgeNodeActions.ACTION_CLICK), refusing.find("login").performedActions)
    }

    @Test
    fun performClimbsToTheFirstEnabledNodeAcceptingTheAction() {
        val root = tree()
        val actions = BridgeNodeActions(noRecycling)

        root.find("label").actionResult = false
        assertTrue(actions.perform(root.find("label"), BridgeNodeActions.ACTION_LONG_CLICK))
        assertEquals(listOf(BridgeNodeActions.ACTION_LONG_CLICK), root.find("label").performedActions)
        assertEquals(listOf(BridgeNodeActions.ACTION_LONG_CLICK), root.find("row0").performedActions)

        // A disabled node is skipped without being asked.
        // zh-CN: 未启用的节点直接跳过, 不询问.
        assertTrue(actions.perform(root.find("row1"), BridgeNodeActions.ACTION_SCROLL_FORWARD))
        assertTrue(root.find("row1").performedActions.isEmpty())
        assertEquals(listOf(BridgeNodeActions.ACTION_SCROLL_FORWARD), root.find("list").performedActions)

        root.descendantsAndSelf().forEach { it.actionResult = false }
        assertFalse(actions.perform(root.find("label"), BridgeNodeActions.ACTION_SCROLL_BACKWARD))
    }

    @Test
    fun setTextRequiresAnEnabledNode() {
        val root = tree()
        val actions = BridgeNodeActions(noRecycling)

        assertTrue(actions.setText(root.find("login"), "alice"))
        assertEquals(listOf(BridgeNodeActions.ACTION_SET_TEXT), root.find("login").performedActions)
        assertFalse(actions.setText(root.find("row1"), "bob"))
        assertTrue(root.find("row1").performedActions.isEmpty())
    }

}
