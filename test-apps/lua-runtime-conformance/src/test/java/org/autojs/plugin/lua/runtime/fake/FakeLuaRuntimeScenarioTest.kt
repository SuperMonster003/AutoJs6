package org.autojs.plugin.lua.runtime.fake

import org.junit.Assert.assertEquals
import org.junit.Test

class FakeLuaRuntimeScenarioTest {
    @Test
    fun metadataValuesAreUniqueAndRoundTrip() {
        val scenarios = FakeLuaRuntimeScenario.values()

        assertEquals(scenarios.size, scenarios.map { it.metadataValue }.toSet().size)
        scenarios.forEach { scenario ->
            assertEquals(scenario, FakeLuaRuntimeScenario.fromMetadata(scenario.metadataValue))
        }
    }

    @Test
    fun counterNamesCoverTheStableSnapshotWithoutDuplicates() {
        assertEquals(FakeLuaConformanceCounterContract.SIZE, FakeLuaConformanceCounterContract.names.size)
        assertEquals(
            FakeLuaConformanceCounterContract.SIZE,
            FakeLuaConformanceCounterContract.names.toSet().size,
        )
    }
}
