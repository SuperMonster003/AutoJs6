package org.autojs.autojs.core.automator.diagnostics

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.bridge.BridgeSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactNodeTextTest {
    @Test fun mcpStyleRowsRetainLiteralResourceIdsIncludingTheAndroidNamespace() {
        val root = FakeUiNode.root {
            className = "android.widget.FrameLayout"
            bounds(0, 0, 1080, 2400)
            child("list") {
                className = "androidx.recyclerview.widget.RecyclerView"
                scrollable = true
                id = "com.android.settings:id/recycler_view"
                bounds(0, 220, 1080, 2400)
                child("row", clickable = true) {
                    className = "android.widget.LinearLayout"
                    id = "com.android.settings:id/recycler_item"
                    bounds(0, 220, 1080, 376)
                    child("label") { className = "android.widget.TextView"; text = "Wi-Fi"; bounds(0, 220, 1080, 320) }
                    child("switch", clickable = true) {
                        className = "android.widget.Switch"; checkable = true; checked = true
                        id = "android:id/switch_widget"; bounds(900, 250, 1060, 346)
                    }
                }
            }
        }
        val result = CompactNodeText.collect(listOf(root))
        assertFalse(result.truncated)
        assertEquals("""
            window: com.android.settings/.Settings  bounds=[0,0][1080,2400]  nodes=5
            #n1 FrameLayout [0,0][1080,2400]
            #n2  RecyclerView scrollable id=com.android.settings:id/recycler_view [0,220][1080,2400]
            #n3   LinearLayout clickable id=com.android.settings:id/recycler_item [0,220][1080,376]
            #n4    TextView "Wi-Fi" c=(540,270)
            #n5    Switch clickable checkable checked id=android:id/switch_widget c=(980,298)
        """.trimIndent(), CompactNodeText.render("com.android.settings", "com.android.settings.Settings", result.nodes, false, 200, 32))
    }

    @Test fun observedIdCanSelectItsNodeWithoutAliasingTheSameEntryInAnotherNamespace() {
        val first = FakeUiNode.root { id = "example.app:id/button" }
        val second = FakeUiNode.root { id = "android:id/button" }
        val rows = CompactNodeText.collect(listOf(first, second)).nodes
        for ((index, node) in listOf(first, second).withIndex()) {
            val observed = Regex(" id=([^ ]+)").find(CompactNodeText.line(index + 1, rows[index]))!!.groupValues[1]
            val selector = BridgeSelector(id = observed).toFilter()
            assertTrue(selector.filter(node))
            assertFalse(selector.filter(if (index == 0) second else first))
        }
    }

    @Test fun escapedTextMarkersAndPasswordRedactionAreBounded() {
        assertEquals("line\\nbreak \\\"quoted\\\" \\\\ back", CompactNodeText.clip("line\nbreak \"quoted\" \\ back"))
        assertEquals("a".repeat(40) + "...", CompactNodeText.clip("a".repeat(100_000)))
        val root = FakeUiNode.root {
            className = "android.widget.EditText"; text = "secret"; desc = "private"; password = true
            editable = true; focused = true; selected = true; longClickable = true; enabled = false
            bounds(0, 0, 100, 20)
        }
        val row = CompactNodeText.capture(root, 0)
        assertEquals("#n1 EditText long_clickable editable focused selected !enabled \"[password]\" c=(50,10)", CompactNodeText.line(1, row))
        assertFalse(CompactNodeText.line(1, row).contains("secret"))
    }

    @Test fun traversalBudgetsIncludeHiddenChildrenAndReleaseVisitedChildren() {
        val root = FakeUiNode.root {
            repeat(2500) { child("hidden$it") { visibleToUser = false } }
        }
        val released = ArrayList<FakeUiNode>()
        val result = CompactNodeText.collect(listOf(root), release = { released += it })
        assertEquals(1, result.nodes.size)
        assertTrue(result.truncated)
        assertEquals(CompactNodeText.MAX_VISITED_NODES - 1, released.size)
        assertFalse(root in released)
    }

    @Test fun depthAndNodeCapsNeverRetainLongLivedNodes() {
        val root = FakeUiNode.root { child("first") { child("deep") }; child("second") }
        val byDepth = CompactNodeText.collect(listOf(root), maxDepth = 0)
        assertEquals(1, byDepth.nodes.size)
        assertTrue(byDepth.truncated)
        val byCount = CompactNodeText.collect(listOf(root), maxNodes = 2)
        assertEquals(2, byCount.nodes.size)
        assertTrue(byCount.truncated)
        assertTrue(CompactNodeText.render("", "", byCount.nodes, true, 2, 32).contains("use ui_find"))
        root.text = "x".repeat(CompactNodeText.MAX_IDENTITY_CHARS + 1)
        val oversized = CompactNodeText.capture(root, 0)
        assertEquals(null, oversized.fingerprint)
        assertEquals(128, oversized.text.length)
    }
}
