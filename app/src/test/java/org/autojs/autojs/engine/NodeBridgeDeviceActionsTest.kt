package org.autojs.autojs.engine

import org.junit.Assert.*
import org.junit.Test

class NodeBridgeDeviceActionsTest {
    @Test fun editorPositionsAreOneBasedAndClosed() {
        assertEquals(1 to 1, NodeBridgeDeviceActions.editorPosition(emptyMap()))
        assertEquals(3 to 4, NodeBridgeDeviceActions.editorPosition(mapOf("line" to 3.0, "column" to 4)))
        listOf(mapOf("line" to 0), mapOf("line" to 1.5), mapOf("column" to 1000001), mapOf("line" to "3"), mapOf("other" to 1), mapOf("line" to null)).forEach {
            assertThrows(IllegalArgumentException::class.java) { NodeBridgeDeviceActions.editorPosition(it) }
        }
    }
    @Test fun shellOutputBudgetCountsBothStreamsWithoutSplittingUtf8() {
        assertEquals("中" to "x", NodeBridgeShellOutput.bound("中", "xy", 4))
        assertEquals("" to "a", NodeBridgeShellOutput.bound("中", "a", 2))
        assertEquals("ab" to "", NodeBridgeShellOutput.bound("abc", "def", 2))
        assertEquals("ab", NodeBridgeShellOutput.text(byteArrayOf(97, -1, 98), 3))
    }
    @Test fun shellLimitsAreStrictAndOptionalForExistingClients() {
        assertNull(NodeBridgeShellOutput.limit(null))
        assertEquals(262144, NodeBridgeShellOutput.limit(262144.0))
        listOf(0, -1, 262145, 1.5, "2", Double.NaN).forEach {
            assertThrows(IllegalArgumentException::class.java) { NodeBridgeShellOutput.limit(it) }
        }
    }

    @Test fun legacyProcessWaitUsesExitValueAndHasABoundedTimeout() {
        var running = true
        val process = object : Process() {
            override fun getOutputStream() = java.io.ByteArrayOutputStream()
            override fun getInputStream() = byteArrayOf().inputStream()
            override fun getErrorStream() = byteArrayOf().inputStream()
            override fun waitFor(): Int = error("unbounded wait must not be called")
            override fun exitValue(): Int = if (running) throw IllegalThreadStateException() else 0
            override fun destroy() { running = false }
        }
        assertFalse(NodeBridgeShellOutput.awaitExit(process, 5))
        process.destroy()
        assertTrue(NodeBridgeShellOutput.awaitExit(process, 0))
    }
}
