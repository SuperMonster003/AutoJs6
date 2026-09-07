package org.autojs.autojs.core.automator.bridge

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.plugin.mcp.server.api.McpServerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeNodeDumpTest {

    private fun tree(): FakeUiNode = FakeUiNode.root("root") {
        className = "android.widget.FrameLayout"
        bounds(0, 0, 1080, 1920)
        child("a") {
            text = "Login"
            clickable = true
            bounds(0, 0, 540, 100)
            child("a0") { text = "inner" }
        }
        child("b") {
            visibleToUser = false
            child("b0")
        }
        child("c") { desc = "third" }
    }

    @Test
    fun walksPreOrderWithPositions() {
        val result = BridgeNodeDump.dump(listOf(tree()))
        assertEquals(listOf("", "Login", "inner", "", "", ""), result.entries.map { it.node.text })
        assertEquals(listOf(0, 1, 2, 1, 2, 1), result.entries.map { it.depth })
        assertEquals(listOf(-1, 0, 0, 1, 0, 2), result.entries.map { it.index })
        assertEquals(listOf(3, 1, 0, 1, 0, 0), result.entries.map { it.childCount })
        assertEquals(listOf(0, 0, 0, 0, 0, 0), result.entries.map { it.root })
        assertEquals(6, result.nodes)
        assertEquals(1, result.roots)
        assertFalse(result.truncated)
        val login = result.entries[1]
        assertEquals("android.widget.FrameLayout", result.entries[0].node.className)
        assertEquals(BridgeBounds(0, 0, 540, 100), login.node.bounds)
        assertTrue(login.node.clickable)
        assertTrue(login.node.enabled)
        assertFalse(result.entries[3].visible)
    }

    @Test
    fun nodeBudgetTruncatesTheWalk() {
        val result = BridgeNodeDump.dump(listOf(tree()), BridgeDumpOptions(maxNodes = 2))
        assertEquals(2, result.nodes)
        assertTrue(result.truncated)
        assertEquals(listOf(0, 1), result.entries.map { it.depth })
    }

    @Test
    fun depthLimitKeepsChildCounts() {
        val result = BridgeNodeDump.dump(listOf(tree()), BridgeDumpOptions(maxDepth = 0))
        assertEquals(1, result.nodes)
        assertEquals(3, result.entries.single().childCount)
        assertFalse(result.truncated)

        val oneLevel = BridgeNodeDump.dump(listOf(tree()), BridgeDumpOptions(maxDepth = 1))
        assertEquals(listOf(0, 1, 1, 1), oneLevel.entries.map { it.depth })
    }

    @Test
    fun visibleOnlySkipsInvisibleSubtrees() {
        val result = BridgeNodeDump.dump(listOf(tree()), BridgeDumpOptions(visibleOnly = true))
        assertEquals(4, result.nodes)
        assertTrue(result.entries.all { it.visible })
        assertEquals(listOf(-1, 0, 0, 2), result.entries.map { it.index })
    }

    @Test
    fun severalRootsAreNumbered() {
        val second = FakeUiNode.root("second") { child("x") }
        val result = BridgeNodeDump.dump(listOf(tree(), second), BridgeDumpOptions(maxDepth = 0))
        assertEquals(listOf(0, 1), result.entries.map { it.root })
        assertEquals(2, result.roots)
        val budgeted = BridgeNodeDump.dump(listOf(tree(), second), BridgeDumpOptions(maxNodes = 7))
        assertEquals(7, budgeted.nodes)
        assertTrue(budgeted.truncated)
        assertEquals(1, budgeted.entries.last().root)
    }

    @Test
    fun optionsAreBounded() {
        assertTrue(runCatching { BridgeDumpOptions(maxDepth = -1) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { BridgeDumpOptions(maxDepth = BridgeAccessibilityLimits.MAX_DEPTH + 1) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { BridgeDumpOptions(maxNodes = 0) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { BridgeDumpOptions(maxNodes = BridgeAccessibilityLimits.MAX_NODES + 1) }.exceptionOrNull() is IllegalArgumentException)
        BridgeDumpOptions(maxDepth = BridgeAccessibilityLimits.MAX_DEPTH, maxNodes = BridgeAccessibilityLimits.MAX_NODES)
    }

    @Test
    fun limitsMirrorTheMcpContract() {
        assertEquals(McpServerContract.MAX_DUMP_NODES, BridgeAccessibilityLimits.MAX_NODES)
        assertEquals(McpServerContract.MAX_DUMP_DEPTH, BridgeAccessibilityLimits.MAX_DEPTH)
        assertEquals(McpServerContract.MAX_DUMP_TEXT_BYTES, BridgeAccessibilityLimits.MAX_TEXT_BYTES)
        assertEquals(McpServerContract.MAX_SCREENSHOT_ENCODED_BYTES, BridgeAccessibilityLimits.MAX_SCREENSHOT_ENCODED_BYTES)
    }
}
