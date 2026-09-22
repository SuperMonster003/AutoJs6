package org.autojs.autojs.core.plugin.hostbroker

import org.autojs.autojs.core.plugin.mcp.McpCapabilityGrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostCapabilityGrantTest {
    @Test fun mcpAliasRetainsTheSameDefaultGrant() {
        assertTrue(McpCapabilityGrant.DEFAULT === HostCapabilityGrant.DEFAULT)
        assertEquals(HostCapabilityGrant.Decision.Allowed,
            HostCapabilityGrant.DEFAULT.evaluate("device", "info", listOf("device"), 10))
    }

    @Test fun modelBudgetsArePositiveAndIndependentOfDeviceConcurrency() {
        val grant = HostCapabilityGrant(setOf("device.info"), setOf("device"), maxConcurrentCalls = 2)
        assertEquals(30, grant.modelCallsPerMinute)
        assertEquals(1_000_000L, grant.maxTotalTokens)
        assertEquals(128 * 1024, grant.maxInputBytesPerRequest)
        listOf<() -> Unit>(
            { grant.copy(modelCallsPerMinute = 0) },
            { grant.copy(maxTotalTokens = 0) },
            { grant.copy(maxInputBytesPerRequest = 0) },
        ).forEach { assertTrue(runCatching(it).exceptionOrNull() is IllegalArgumentException) }
    }
}
