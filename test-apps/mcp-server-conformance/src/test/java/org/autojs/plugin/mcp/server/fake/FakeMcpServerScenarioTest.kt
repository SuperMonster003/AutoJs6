package org.autojs.plugin.mcp.server.fake

import org.autojs.plugin.mcp.server.api.McpServerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeMcpServerScenarioTest {

    @Test
    fun parsesEveryManifestScenario() {
        FakeMcpServerScenario.values().forEach { scenario ->
            assertEquals(scenario, FakeMcpServerScenario.fromMetadata(scenario.metadataValue))
        }
    }

    @Test
    fun rejectsUnknownManifestScenario() {
        assertThrows(IllegalArgumentException::class.java) {
            FakeMcpServerScenario.fromMetadata("custom-test-mode")
        }
    }

    @Test
    fun metadataValuesAreDistinctKebabCase() {
        val values = FakeMcpServerScenario.values().map { it.metadataValue }
        assertEquals(values.size, values.toSet().size)
        values.forEach { value -> assertTrue(value, value.matches(Regex("[a-z]+(-[a-z]+)*"))) }
    }

    @Test
    fun onlyTheWrongContractScenarioLeavesTheSupportedRange() {
        FakeMcpServerScenario.values().forEach { scenario ->
            val supported = McpServerContract.supportsContractVersion(FakeMcpServerFixture.contractVersion(scenario))
            assertEquals(scenario.name, scenario != FakeMcpServerScenario.WRONG_CONTRACT_VERSION, supported)
        }
        assertFalse(McpServerContract.supportsContractVersion(FakeMcpServerFixture.UNSUPPORTED_CONTRACT_VERSION))
    }
}
