package org.autojs.autojs.core.accessibility.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeLifecycleTest {

    private val logs = mutableListOf<String>()

    private fun lifecycle(policy: NodeRecyclePolicy) = NodeLifecycle(policy) { message, _ -> logs += message }

    @Test
    fun policyFollowsTheApiLevelBoundary() {
        assertEquals(NodeRecyclePolicy.RECYCLE, NodeRecyclePolicy.forSdk(24))
        assertEquals(NodeRecyclePolicy.RECYCLE, NodeRecyclePolicy.forSdk(32))
        assertEquals(NodeRecyclePolicy.NO_OP, NodeRecyclePolicy.forSdk(33))
        assertEquals(NodeRecyclePolicy.NO_OP, NodeRecyclePolicy.forSdk(36))
        assertTrue(NodeRecyclePolicy.RECYCLE.recycles)
        assertFalse(NodeRecyclePolicy.NO_OP.recycles)
    }

    @Test
    fun releaseRunsTheRecycleOnlyWhenThePolicyRecycles() {
        var recycled = 0
        assertTrue(lifecycle(NodeRecyclePolicy.RECYCLE).release { recycled++ })
        assertEquals(1, recycled)
        assertFalse(lifecycle(NodeRecyclePolicy.NO_OP).release { recycled++ })
        assertEquals("no-op platforms never touch the node", 1, recycled)
    }

    @Test
    fun aFailingRecycleIsLoggedNotThrown() {
        assertFalse(lifecycle(NodeRecyclePolicy.RECYCLE).release { throw IllegalStateException("already recycled") })
        assertEquals(listOf("Unable to recycle accessibility node"), logs)
    }

    @Test
    fun releaseAllSkipsTheExceptionByIdentityAndCountsSuccesses() {
        val root = "root"
        val sameText = String(charArrayOf('r', 'o', 'o', 't'))
        val recycled = mutableListOf<String>()
        val released = lifecycle(NodeRecyclePolicy.RECYCLE).releaseAll(listOf(root, "a", sameText, "b"), except = root) { node ->
            if (node == "b") throw IllegalStateException("stale")
            recycled += node
        }
        assertEquals("an equal but distinct node is not the root", listOf("a", "root"), recycled)
        assertEquals(2, released)
        assertEquals(1, logs.size)
    }

    @Test
    fun releaseAllIsANoOpWithoutRecycling() {
        var recycled = 0
        assertEquals(0, lifecycle(NodeRecyclePolicy.NO_OP).releaseAll(listOf("a", "b")) { recycled++ })
        assertEquals(0, recycled)
    }

}
