package org.autojs.autojs.core.automator.bridge

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.diagnostics.NodeRefSnapshots
import org.junit.Assert.*
import org.junit.Test

class PreparedNodeActionTest {
    @Test fun inspectionSkipsHiddenNodesAndReleasesUnselectedChildren() {
        val root = FakeUiNode.root { child("hidden") { visibleToUser = false }; child("other"); child("target") }
        val released = ArrayList<String>()
        assertEquals("target", PreparedNodeAction.find(root, { it.name in setOf("hidden", "target") }, { released += it.name })?.name)
        assertEquals(listOf("hidden", "other"), released)
    }
    @Test fun inspectionRejectsTreesBeyondItsVisitBudget() {
        val root = FakeUiNode.root { repeat(2100) { child("n$it") } }
        assertThrows(NodeRefSnapshots.Stale::class.java) { PreparedNodeAction.find(root, { false }, {}) }
    }
    @Test fun inspectionFindsDeepTargetsButRetainsAHardDepthBound() {
        val root = FakeUiNode.root {}
        var nested = root
        repeat(45) { nested = nested.child("level$it") }
        nested.child("target")
        assertEquals("target", PreparedNodeAction.find(root, { it.name == "target" }, {})?.name)
        repeat(21) { nested = nested.child("deeper$it") }
        assertThrows(NodeRefSnapshots.Stale::class.java) { PreparedNodeAction.find(root, { false }, {}) }
    }
    @Test fun actionEligibilityDoesNotFallBackToUnrelatedCapabilities() {
        val node = FakeUiNode.root { clickable = true }
        assertTrue(PreparedNodeAction.accepts(node, "click"))
        for (method in listOf("longClick", "setText", "scrollForward", "scrollBackward")) assertFalse(PreparedNodeAction.accepts(node, method))
    }
    @Test fun changedPasswordEnabledOrActionStateInvalidatesPreparation() {
        val node = FakeUiNode.root { editable = true }
        PreparedNodeAction.validate(node, "setText", false, true)
        node.password = true
        assertThrows(NodeRefSnapshots.Stale::class.java) { PreparedNodeAction.validate(node, "setText", false, true) }
        node.password = false; node.enabled = false
        assertThrows(NodeRefSnapshots.Stale::class.java) { PreparedNodeAction.validate(node, "setText", false, true) }
        node.enabled = true; node.editable = false
        assertThrows(NodeRefSnapshots.Stale::class.java) { PreparedNodeAction.validate(node, "setText", false, true) }
    }
    @Test fun appendUsesFullHostTextAndDoesNotAppendToPasswordMasks() {
        assertEquals("old suffix", PreparedNodeAction.text("old", " suffix", true, false))
        assertEquals("new", PreparedNodeAction.text("masked", "new", false, true))
        assertThrows(IllegalArgumentException::class.java) { PreparedNodeAction.text("***", "x", true, true) }
    }
    @Test fun appendBoundsCombinedUtf8Bytes() {
        assertThrows(IllegalArgumentException::class.java) { PreparedNodeAction.text("中".repeat(16000), "文".repeat(16000), true, false) }
    }
}
