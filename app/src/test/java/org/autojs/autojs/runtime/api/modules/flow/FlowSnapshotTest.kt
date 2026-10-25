package org.autojs.autojs.runtime.api.modules.flow

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.flow.StabilityCompare
import org.autojs.autojs.core.automator.flow.Stabilizer
import org.junit.Assert.*
import org.junit.Test
import org.mozilla.javascript.Context

class FlowSnapshotTest {

    @Test
    fun mutablePlainObjectsAndArraysAreCopiedAtSamplingTime() {
        val cx = Context.enter()
        try {
            val scope = cx.initStandardObjects()
            val value = cx.evaluateString(scope, "let row = { remaining: 1 }; let rows = [row, row]; rows", "snapshot", 1, null)
            val first = FlowSnapshot.capture(value)
            cx.evaluateString(scope, "row.remaining = 2", "snapshot", 1, null)
            val second = FlowSnapshot.capture(value)
            assertNotEquals(first, second)
            assertEquals(listOf(mapOf("remaining" to 1.0), mapOf("remaining" to 1.0)), first)
            cx.evaluateString(scope, "row.rows = rows", "snapshot", 1, null)
            assertThrows(IllegalArgumentException::class.java) { FlowSnapshot.capture(value) }
        } finally {
            Context.exit()
        }
    }

    @Test
    fun nodeCollectionsCompareEachNodesStateAndOrder() {
        val a = FakeUiNode.root("a") { text = "a" }
        val b = FakeUiNode.root("b") { text = "b" }
        val first = Stabilizer.keyOf(StabilityCompare.Fingerprint, listOf(a, b))
        assertEquals(first, Stabilizer.keyOf(StabilityCompare.Fingerprint, listOf(a, b)))
        assertNotEquals(first, Stabilizer.keyOf(StabilityCompare.Fingerprint, listOf(b, a)))
        b.text = "updated"
        assertNotEquals(first, Stabilizer.keyOf(StabilityCompare.Fingerprint, listOf(a, b)))
        assertNotEquals(first, Stabilizer.keyOf(StabilityCompare.Fingerprint, listOf(a)))
    }
}
