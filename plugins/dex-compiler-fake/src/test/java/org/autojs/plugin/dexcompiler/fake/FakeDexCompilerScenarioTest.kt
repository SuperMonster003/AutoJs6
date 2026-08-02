package org.autojs.plugin.dexcompiler.fake

import org.autojs.plugin.dexcompiler.api.DexCompilerValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FakeDexCompilerScenarioTest {
    @Test
    fun parsesEveryManifestScenario() {
        FakeDexCompilerScenario.values().forEach { scenario ->
            assertEquals(scenario, FakeDexCompilerScenario.fromMetadata(scenario.metadataValue))
        }
    }

    @Test
    fun rejectsUnknownManifestScenario() {
        assertThrows(IllegalArgumentException::class.java) {
            FakeDexCompilerScenario.fromMetadata("custom-test-mode")
        }
    }

    @Test
    fun everyScenarioPublishesValidProtocolMetadata() {
        FakeDexCompilerScenario.values().forEach { scenario ->
            DexCompilerValidation.validateInfo(FakeDexCompilerFixture.info(scenario))
        }
        DexCompilerValidation.validateCapabilities(FakeDexCompilerFixture.capabilities)
    }
}
