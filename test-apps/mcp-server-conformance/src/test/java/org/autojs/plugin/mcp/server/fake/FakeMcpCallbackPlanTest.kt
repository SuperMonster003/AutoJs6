package org.autojs.plugin.mcp.server.fake

import org.autojs.plugin.mcp.server.api.McpServerContract
import org.autojs.plugin.mcp.server.api.McpServerIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class FakeMcpCallbackPlanTest {

    @Test
    fun normalScenarioSendsOneCallbackPerTransition() {
        val plan = FakeMcpCallbackPlan.forScenario(FakeMcpServerScenario.NORMAL)
        assertEquals(FakeMcpCallbackPlan(1, 1, 0, 1, oversizedStatus = false, oversizedBrokerRequest = false, killProcessAfterRunning = false), plan)
    }

    @Test
    fun duplicateScenarioDoublesEveryCallbackAndTalksAfterClose() {
        val plan = FakeMcpCallbackPlan.forScenario(FakeMcpServerScenario.DUPLICATE_CALLBACK)
        assertEquals(2, plan.runningStatusCallbacks)
        assertEquals(2, plan.stoppedStatusCallbacks)
        assertEquals(1, plan.statusCallbacksAfterClose)
        assertEquals(2, plan.eventCallbacks)
        assertFalse(plan.killProcessAfterRunning)
    }

    @Test
    fun onlyTheDeathScenarioKillsItsProcess() {
        FakeMcpServerScenario.values().forEach { scenario ->
            val plan = FakeMcpCallbackPlan.forScenario(scenario)
            assertEquals(scenario.name, scenario == FakeMcpServerScenario.DEATH_CALLBACK, plan.killProcessAfterRunning)
        }
    }

    @Test
    fun onlyTheOversizedScenarioExceedsTheContractCaps() {
        FakeMcpServerScenario.values().forEach { scenario ->
            val plan = FakeMcpCallbackPlan.forScenario(scenario)
            val oversized = scenario == FakeMcpServerScenario.OVERSIZED_BUNDLE
            assertEquals(scenario.name, oversized, plan.oversizedStatus)
            assertEquals(scenario.name, oversized, plan.oversizedBrokerRequest)
        }
    }

    @Test
    fun oversizedPayloadsExceedTheCapsTheHostMustEnforce() {
        val message = FakeMcpServerFixture.oversizedErrorMessage()
        assertEquals(FakeMcpServerFixture.OVERSIZED_ERROR_BYTES, message.length)
        assertEquals(message.length, message.toByteArray(StandardCharsets.UTF_8).size)
        assertTrue(message.length > McpServerContract.MAX_ERROR_MESSAGE_BYTES)

        val request = FakeMcpServerFixture.oversizedRequestJson()
        assertTrue(request.toByteArray(StandardCharsets.UTF_8).size > McpServerContract.MAX_BRIDGE_INLINE_JSON_BYTES)
        assertTrue("the Parcel form (UTF-16) must stay inside the Binder transaction budget", request.length * 2 < FakeMcpServerFixture.BINDER_TRANSACTION_BUDGET_BYTES)
        assertTrue(request.startsWith("{\"id\":\"${FakeMcpServerFixture.OVERSIZED_REQUEST_ID}\",\"module\":\"device\",\"method\":\"info\","))
        assertTrue(request.endsWith("\"timeoutMs\":${FakeMcpServerFixture.PROBE_TIMEOUT_MS},\"permissions\":[\"device\"]}"))
    }

    @Test
    fun probeRequestIsASmallDeviceInfoCall() {
        val request = FakeMcpServerFixture.probeRequestJson()
        assertEquals(
            "{\"id\":\"fake-probe\",\"module\":\"device\",\"method\":\"info\",\"args\":[],\"timeoutMs\":5000,\"permissions\":[\"device\"]}",
            request,
        )
        assertTrue(request.length < McpServerContract.MAX_ERROR_MESSAGE_BYTES)
    }

    @Test
    fun fixtureMetadataMatchesTheHostRequirements() {
        assertEquals(McpServerIds.REQUIRED_HOST_VERSION_CODE, FakeMcpServerFixture.requiredHostVersion())
        assertEquals("http://127.0.0.1:9637/mcp", FakeMcpServerFixture.endpoint(McpServerContract.DEFAULT_PORT))
        assertEquals(McpServerContract.CONTRACT_VERSION, FakeMcpServerFixture.contractVersion(FakeMcpServerScenario.NORMAL))
        assertEquals(McpServerContract.MAX_CONTRACT_VERSION + 1, FakeMcpServerFixture.contractVersion(FakeMcpServerScenario.WRONG_CONTRACT_VERSION))
    }
}
