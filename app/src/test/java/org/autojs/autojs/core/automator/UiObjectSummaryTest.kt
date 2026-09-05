package org.autojs.autojs.core.automator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The property listing and the fingerprint over [FakeUiNode]: the listing keeps the historical
 * layout (quoted strings, verbatim bounds and centre, one action per line), the fingerprint is
 * stable for the same state and changes with the properties it covers only.
 * zh-CN: 在 [FakeUiNode] 上的属性列表与指纹: 列表保持历史布局 (字符串加引号, 边界与中心原样, 每行一个动作), 指纹在同一状态下
 * 稳定, 且只随其覆盖的属性变化.
 */
class UiObjectSummaryTest {

    private fun tree() = FakeUiNode.root {
        packageName = "org.example"
        className = "android.view.ViewGroup"
        child("login") {
            packageName = "org.example"
            className = "android.widget.Button"
            id = "org.example:id/login"
            text = "Login"
            desc = "Sign in"
            bounds(224, 135, 487, 228)
            clickable = true
            drawingOrder = 1
            actions = listOf("ACTION_CLICK", "ACTION_FOCUS")
        }
        child("empty")
    }

    @Test
    fun summaryKeepsTheHistoricalListing() {
        val login = tree().find("login")
        val expected = """
            {
              packageName="org.example"
              parent="android.view.ViewGroup"
              id="org.example:id/login"
              fullId="org.example:id/login"
              idHex=null
              desc="Sign in"
              text="Login"
              bounds=Rect(224, 135 - 487, 228)
              center={356, 182}
              className="android.widget.Button"
              clickable=true
              longClickable=false
              scrollable=false
              indexInParent=0
              childCount=0
              depth=1
              checked=false
              enabled=true
              editable=false
              focusable=false
              checkable=false
              selected=false
              dismissable=false
              visibleToUser=true
              contextClickable=false
              focused=false
              accessibilityFocused=false
              rowCount=0
              columnCount=0
              row=-1
              column=-1
              rowSpan=-1
              columnSpan=-1
              drawingOrder=1
              actions=[
                ACTION_CLICK
                ACTION_FOCUS
              ]
            }
        """.trimIndent()
        assertEquals(expected, UiObjectSummary.summary(login))
    }

    @Test
    fun describePrefixesTheLabelAndTheSimpleClassName() {
        val login = tree().find("login")
        val described = UiObjectSummary.describe(login)
        assertTrue(described.startsWith("[UiObject] Button {\n"))
        assertEquals("[UiObject] Button ${UiObjectSummary.summary(login)}", described)
        assertTrue(UiObjectSummary.describe(login, "Node").startsWith("[Node] Button {"))
        // A node without a class name renders as "null", as it always did.
        // zh-CN: 没有类名的节点渲染为 "null", 与从前相同.
        assertTrue(UiObjectSummary.describe(tree().find("empty")).startsWith("[UiObject] null {"))
    }

    @Test
    fun emptyListsAndNullsRenderLikeBefore() {
        val empty = tree().find("empty")
        val summary = UiObjectSummary.summary(empty)
        assertTrue(summary.contains("\n  parent=\"android.view.ViewGroup\"\n"))
        assertTrue(summary.contains("\n  id=null\n"))
        assertTrue(summary.contains("\n  text=\"\"\n"))
        assertTrue(summary.contains("\n  bounds=Rect(0, 0 - 0, 0)\n"))
        assertTrue(summary.contains("\n  center={0, 0}\n"))
        assertTrue(summary.endsWith("\n  actions=[\n    \n  ]\n}"))
    }

    @Test
    fun centreRoundsHalfUpLikeTheFormattedPoint() {
        val node = FakeUiNode.root { bounds(0, 0, 3, 5) }
        assertTrue(UiObjectSummary.summary(node).contains("center={2, 3}"))
    }

    @Test
    fun fingerprintIsStableAndSensitiveToTheCoveredProperties() {
        val a = tree().find("login")
        val b = tree().find("login")
        assertEquals(UiObjectSummary.fingerprint(a), UiObjectSummary.fingerprint(b))

        b.text = "Log in"
        assertNotEquals(UiObjectSummary.fingerprint(a), UiObjectSummary.fingerprint(b))
        b.text = a.text
        assertEquals(UiObjectSummary.fingerprint(a), UiObjectSummary.fingerprint(b))

        b.bounds(224, 135, 487, 229)
        assertNotEquals(UiObjectSummary.fingerprint(a), UiObjectSummary.fingerprint(b))
        b.bounds(224, 135, 487, 228)

        b.enabled = false
        assertNotEquals(UiObjectSummary.fingerprint(a), UiObjectSummary.fingerprint(b))
        b.enabled = true

        // Not covered: the id, actions and depth do not change the fingerprint.
        // zh-CN: 未覆盖: id, 动作与深度不改变指纹.
        b.id = "other"
        b.actions = emptyList()
        assertEquals(UiObjectSummary.fingerprint(a), UiObjectSummary.fingerprint(b))
    }

    @Test
    fun fingerprintDoesNotTouchSiblingsOrChildren() {
        val root = tree()
        val before = UiObjectSummary.fingerprint(root.find("login"))
        root.find("empty").text = "changed"
        root.child("extra")
        assertEquals(before, UiObjectSummary.fingerprint(root.find("login")))
    }

}
