package org.autojs.autojs.engine

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RuntimeFlowNodeIdentityTest {
    private fun snapshot() = JSONObject("""{"windowId":3,"packageName":"fixture","className":"Row","id":"container","text":"","desc":"scope","depth":2,"indexInParent":0,"bounds":{"left":0,"top":20,"right":200,"bottom":80}}""")
    @Test fun layoutMotionPreservesScopeButInvalidatesAnActionSnapshot() {
        val old = snapshot()
        val moved = snapshot().put("bounds", JSONObject("""{"left":0,"top":40,"right":200,"bottom":100}"""))
        assertTrue(RuntimeFlowNodeIdentity.matches(old, moved, movingRoot = true))
        assertFalse(RuntimeFlowNodeIdentity.matches(old, moved))
        assertTrue(RuntimeFlowNodeIdentity.matches(old, snapshot()))
    }
    @Test fun movingRootStillRequiresTheSameWindowAndNodeIdentity() {
        for ((key, value) in mapOf("windowId" to 4, "packageName" to "other", "className" to "Button", "id" to "replacement", "desc" to "changed", "text" to "changed", "depth" to 3, "indexInParent" to 1)) {
            assertFalse(key, RuntimeFlowNodeIdentity.matches(snapshot(), snapshot().put(key, value), movingRoot = true))
        }
    }
}
