package org.autojs.plugin.ai.text.fake

import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.text.api.AiModelListRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAiFixtureAndPagerTest {
    @Test
    fun providerIsOnDeviceCredentialFreeAndAbiIndependent() {
        val info = FakeAiTextFixture.providerInfo

        assertEquals(AiDataLocality.ON_DEVICE, info.locality)
        assertEquals(AiCredentialMode.NONE, info.credentialMode)
        assertTrue(info.declaredHttpsOrigins.isEmpty())
        assertTrue(info.supportedAbis.isEmpty())
    }

    @Test
    fun publicModelListIsStableAndExcludesEveryHostileSelector() {
        assertEquals(
            listOf("fake.echo", "fake.stream", "fake.reasoning", "fake.tools"),
            FakeAiTextFixture.publicModels.map { it.modelId },
        )
        FakeAiHostileScenario.values().forEach { scenario ->
            assertFalse(FakeAiTextFixture.publicModels.any { it.modelId == scenario.modelId })
        }
    }

    @Test
    fun modelCapabilitiesAreEnforcedIndependentlyFromProviderCapabilities() {
        FakeAiTextFixture.requireSupportedRequest(fakeRequest())
        assertThrows(IllegalArgumentException::class.java) {
            FakeAiTextFixture.requireSupportedRequest(fakeRequest(stream = true))
        }
        FakeAiTextFixture.requireSupportedRequest(
            fakeRequest(modelId = FakeAiTextFixture.MODEL_STREAM, stream = true),
        )
        assertThrows(IllegalArgumentException::class.java) {
            FakeAiTextFixture.requireSupportedRequest(fakeRequest(modelId = FakeAiTextFixture.MODEL_TOOLS))
        }
    }

    @Test
    fun hostileModelIdsAreAcceptedButRemainOutsidePublicDiscovery() {
        FakeAiHostileScenario.values().forEach { scenario ->
            FakeAiTextFixture.requireSupportedRequest(fakeRequest(modelId = scenario.modelId))
            assertEquals(scenario, FakeAiHostileScenario.fromModelId(scenario.modelId))
        }
        assertNull(FakeAiHostileScenario.fromModelId(FakeAiTextFixture.MODEL_ECHO))
    }

    @Test
    fun modelPaginationIsDeterministicAndCompletesWithoutDuplicates() {
        val first = FakeAiModelPager.page(AiModelListRequest(AiProtocolVersion(1, 0), pageSize = 2))
        val second = FakeAiModelPager.page(
            AiModelListRequest(AiProtocolVersion(1, 0), pageSize = 2, pageToken = first.nextPageToken),
        )

        assertEquals(listOf("fake.echo", "fake.stream"), first.models.map { it.modelId })
        assertEquals(listOf("fake.reasoning", "fake.tools"), second.models.map { it.modelId })
        assertNull(second.nextPageToken)
        assertEquals(FakeAiTextFixture.LISTING_GENERATION, first.listingGeneration)
    }

    @Test
    fun modelTokenBindsPageSizeAndRejectsMalformedOrOutOfRangeOffsets() {
        val first = FakeAiModelPager.page(AiModelListRequest(AiProtocolVersion(1, 0), pageSize = 1))

        assertThrows(IllegalArgumentException::class.java) {
            FakeAiModelPager.page(
                AiModelListRequest(AiProtocolVersion(1, 0), pageSize = 2, pageToken = first.nextPageToken),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FakeAiModelPager.page(
                AiModelListRequest(AiProtocolVersion(1, 0), pageSize = 1, pageToken = "malformed"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FakeAiModelPager.page(
                AiModelListRequest(
                    AiProtocolVersion(1, 0),
                    pageSize = 1,
                    pageToken = "fake-models-v1:99:1",
                ),
            )
        }
    }
}
