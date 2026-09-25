package org.autojs.plugin.ai.provider.fake

import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.provider.api.AiTargetCatalogPolicy
import org.autojs.plugin.ai.provider.api.AiTargetListRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAiTargetCatalogTest {
    @Test
    fun providerDescriptorAggregatesLocalAndRemoteTargetsWithoutExposingCredentials() {
        val info = FakeAiProviderFixture.providerInfo

        assertEquals(AiDataLocality.HYBRID, info.locality)
        assertEquals(AiCredentialMode.PLUGIN_MANAGED, info.credentialMode)
        assertEquals(listOf(FakeAiProviderFixture.REMOTE_ORIGIN), info.declaredHttpsOrigins)
        assertTrue(info.supportedAbis.isEmpty())
        assertFalse(fakeRequest(targetId = FakeAiProviderFixture.TARGET_REMOTE).toString().contains("credential"))
    }

    @Test
    fun publicTargetCatalogIsStableAndExcludesEveryHostileSelector() {
        assertEquals(
            listOf(
                FakeAiProviderFixture.TARGET_ECHO,
                FakeAiProviderFixture.TARGET_STREAM,
                FakeAiProviderFixture.TARGET_REASONING,
                FakeAiProviderFixture.TARGET_TOOLS,
                FakeAiProviderFixture.TARGET_REMOTE,
                FakeAiProviderFixture.TARGET_HYBRID,
                FakeAiProviderFixture.TARGET_UNCONFIGURED,
                FakeAiProviderFixture.TARGET_AGENT_TOOLS,
            ),
            FakeAiProviderFixture.publicTargets.map { it.targetId },
        )
        FakeAiHostileScenario.values().forEach { scenario ->
            assertFalse(FakeAiProviderFixture.publicTargets.any { it.targetId == scenario.targetId })
        }
    }

    @Test
    fun everyCatalogTargetIsCoveredByTheProviderDescriptor() {
        FakeAiProviderFixture.publicTargets.forEach { target ->
            AiTargetCatalogPolicy.requireCompatible(
                FakeAiProviderFixture.providerInfo,
                FakeAiProviderFixture.capabilities,
                target,
            )
        }
        val remote = FakeAiProviderFixture.publicTargets.single {
            it.targetId == FakeAiProviderFixture.TARGET_REMOTE
        }
        assertEquals(AiDataLocality.REMOTE, remote.locality)
        assertEquals(AiCredentialMode.PLUGIN_MANAGED, remote.credentialMode)
        assertEquals(FakeAiProviderFixture.REMOTE_PROVIDER_ID, remote.providerId)
        assertEquals(FakeAiProviderFixture.REMOTE_PROFILE_ID, remote.profileId)
        assertEquals(listOf(FakeAiProviderFixture.REMOTE_ORIGIN), remote.declaredHttpsOrigins)
    }

    @Test
    fun targetCapabilitiesAndAvailabilityAreEnforcedIndependentlyFromProviderCapabilities() {
        FakeAiProviderFixture.requireSupportedRequest(fakeRequest())
        assertThrows(IllegalArgumentException::class.java) {
            FakeAiProviderFixture.requireSupportedRequest(fakeRequest(stream = true))
        }
        FakeAiProviderFixture.requireSupportedRequest(
            fakeRequest(targetId = FakeAiProviderFixture.TARGET_STREAM, stream = true),
        )
        assertThrows(IllegalArgumentException::class.java) {
            FakeAiProviderFixture.requireSupportedRequest(
                fakeRequest(targetId = FakeAiProviderFixture.TARGET_TOOLS),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FakeAiProviderFixture.requireSupportedRequest(
                fakeRequest(targetId = FakeAiProviderFixture.TARGET_UNCONFIGURED, stream = true),
            )
        }
    }

    @Test
    fun hostileTargetIdsAreAcceptedButRemainOutsidePublicDiscovery() {
        FakeAiHostileScenario.values().forEach { scenario ->
            FakeAiProviderFixture.requireSupportedRequest(fakeRequest(targetId = scenario.targetId))
            assertEquals(scenario, FakeAiHostileScenario.fromTargetId(scenario.targetId))
        }
        assertNull(FakeAiHostileScenario.fromTargetId(FakeAiProviderFixture.TARGET_ECHO))
    }

    @Test
    fun targetPaginationIsDeterministicAndCompletesWithoutDuplicates() {
        val first = FakeAiTargetPager.page(
            AiTargetListRequest(FakeAiProviderFixture.protocolVersion, pageSize = 4),
        )
        val second = FakeAiTargetPager.page(
            AiTargetListRequest(
                FakeAiProviderFixture.protocolVersion,
                pageSize = 4,
                pageToken = first.nextPageToken,
            ),
        )

        assertEquals(
            listOf(
                FakeAiProviderFixture.TARGET_ECHO,
                FakeAiProviderFixture.TARGET_STREAM,
                FakeAiProviderFixture.TARGET_REASONING,
                FakeAiProviderFixture.TARGET_TOOLS,
            ),
            first.targets.map { it.targetId },
        )
        assertEquals(
            listOf(
                FakeAiProviderFixture.TARGET_REMOTE,
                FakeAiProviderFixture.TARGET_HYBRID,
                FakeAiProviderFixture.TARGET_UNCONFIGURED,
                FakeAiProviderFixture.TARGET_AGENT_TOOLS,
            ),
            second.targets.map { it.targetId },
        )
        assertNull(second.nextPageToken)
        assertEquals(FakeAiProviderFixture.CATALOG_GENERATION, first.catalogGeneration)
    }

    @Test
    fun targetTokenBindsPageSizeAndRejectsMalformedOrOutOfRangeOffsets() {
        val first = FakeAiTargetPager.page(
            AiTargetListRequest(FakeAiProviderFixture.protocolVersion, pageSize = 1),
        )

        assertThrows(IllegalArgumentException::class.java) {
            FakeAiTargetPager.page(
                AiTargetListRequest(
                    FakeAiProviderFixture.protocolVersion,
                    pageSize = 2,
                    pageToken = first.nextPageToken,
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FakeAiTargetPager.page(
                AiTargetListRequest(
                    FakeAiProviderFixture.protocolVersion,
                    pageSize = 1,
                    pageToken = "malformed",
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FakeAiTargetPager.page(
                AiTargetListRequest(
                    FakeAiProviderFixture.protocolVersion,
                    pageSize = 1,
                    pageToken = "fake-targets-v2:99:1",
                ),
            )
        }
    }
}
