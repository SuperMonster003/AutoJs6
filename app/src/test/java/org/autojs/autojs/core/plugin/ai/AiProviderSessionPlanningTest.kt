package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiTargetInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderSessionPlanningTest {
    @Test
    fun localTargetPlanRequiresNoCredentialChannel() {
        val plan = plan(
            candidate = AiProviderHostTestFixtures.candidate(),
            target = target(),
        )

        assertEquals(AiProviderCredentialDisposition.NONE, plan.credentialDisposition)
        assertEquals(512L * 1024L, plan.effectiveMaximumContextBytes)
        assertEquals(256L * 1024L, plan.effectiveMaximumOutputBytes)
        assertEquals("fixture.provider:model", plan.targetId)
        assertEquals("fixture.model", plan.modelId)
    }

    @Test
    fun remoteCredentialsRemainOwnedByTheProviderProcess() {
        val origin = "https://remote-provider.example"
        val remoteInfo = AiProviderHostTestFixtures.providerInfo(
            locality = AiDataLocality.REMOTE,
            declaredHttpsOrigins = listOf(origin),
        )
        val plan = plan(
            candidate = AiProviderHostTestFixtures.candidate(providerInfo = remoteInfo),
            target = target(
                targetId = "fixture.provider:remote",
                profileId = "remote-profile",
                locality = AiDataLocality.REMOTE,
                declaredHttpsOrigins = listOf(origin),
            ),
        )

        assertEquals(AiProviderCredentialDisposition.PROVIDER_PROCESS_ONLY, plan.credentialDisposition)
        assertEquals("remote-profile", plan.profileId)
        assertEquals(listOf(origin), plan.targetDeclaredHttpsOrigins)
    }

    @Test
    fun targetMustMeetTheSameCapabilityRequirementAsTheProvider() {
        val selected = selected(AiProviderHostTestFixtures.candidate())
        val requirement = AiProviderCapabilityRequirement(
            requiredCapabilityIds = setOf(AiProviderCapabilityId.TOOLS),
        )
        val targetWithoutTools = target(capabilityIds = listOf(AiProviderCapabilityId.STREAMING))

        assertTrue(
            AiProviderHostSessionPlanner.plan(selected, CATALOG_GENERATION, targetWithoutTools, requirement) ===
                AiProviderSessionPlanningResult.TargetCapabilityMismatch,
        )
    }

    @Test
    fun targetCannotAdvertiseACapabilityRemovedByProviderNegotiation() {
        val selected = selected(
            AiProviderHostTestFixtures.candidate(
                capabilities = AiProviderHostTestFixtures.capabilities(supportsReasoning = false),
            ),
        )

        assertTrue(
            AiProviderHostSessionPlanner.plan(
                selected = selected,
                catalogGeneration = "fixture-generation",
                target = target(),
                requirement = AiProviderCapabilityRequirement(),
            ) === AiProviderSessionPlanningResult.TargetCapabilityMismatch,
        )
    }

    @Test
    fun safePlanAndDiagnosticsNeverExposeProviderConfiguration() {
        val secret = "SENTINEL_SECRET_CONFIGURATION"
        val secretOrigin = "https://sentinel-secret-configuration.example"
        val info = AiProviderHostTestFixtures.providerInfo(
            displayName = secret,
            locality = AiDataLocality.REMOTE,
            declaredHttpsOrigins = listOf(secretOrigin),
        )
        val selected = selected(AiProviderHostTestFixtures.candidate(providerInfo = info))
        val plan = planned(
            selected,
            target(
                targetId = "fixture.provider:remote",
                profileId = "remote-profile",
                locality = AiDataLocality.REMOTE,
                declaredHttpsOrigins = listOf(secretOrigin),
            ),
        )
        val visible = listOf(
            selected.toString(),
            selected.pinned.toString(),
            plan.toString(),
            AiProviderSafeDiagnosticFormatter.format(AiProviderHostDiagnosticEvent.FAILED, plan),
            AiProviderBindingCacheKey.create(plan),
        ).joinToString(separator = "\n")

        assertFalse(visible.contains(secret, ignoreCase = true))
        assertFalse(visible.contains("sentinel-secret-configuration.example", ignoreCase = true))
        assertFalse(visible.contains("https://", ignoreCase = true))
        assertFalse(visible.contains(AiProviderHostTestFixtures.SIGNER_A, ignoreCase = true))
        assertTrue(visible.contains("fixture.provider"))
    }

    @Test
    fun bindingCacheKeyIsDeterministicAndSensitiveToTargetIdentity() {
        fun candidate(
            versionCode: Long = 7,
            minimumHostVersionCode: Long = 0,
            signers: List<String>,
        ) = AiProviderHostTestFixtures.candidate(
            versionCode = versionCode,
            signers = signers,
            providerInfo = AiProviderHostTestFixtures.providerInfo(
                minimumHostVersionCode = minimumHostVersionCode,
            ),
        )

        val first = plan(
            candidate = candidate(signers = listOf(AiProviderHostTestFixtures.SIGNER_B, AiProviderHostTestFixtures.SIGNER_A)),
            target = target(),
        )
        val reordered = plan(
            candidate = candidate(signers = listOf(AiProviderHostTestFixtures.SIGNER_A, AiProviderHostTestFixtures.SIGNER_B)),
            target = target(),
        )
        val newerPackage = plan(
            candidate = candidate(
                versionCode = 8,
                signers = listOf(AiProviderHostTestFixtures.SIGNER_A, AiProviderHostTestFixtures.SIGNER_B),
            ),
            target = target(),
        )
        val otherTarget = plan(
            candidate = candidate(signers = listOf(AiProviderHostTestFixtures.SIGNER_A, AiProviderHostTestFixtures.SIGNER_B)),
            target = target(targetId = "fixture.provider:other", modelId = "fixture.other-model"),
        )
        val changedCapabilities = plan(
            candidate = candidate(signers = listOf(AiProviderHostTestFixtures.SIGNER_A, AiProviderHostTestFixtures.SIGNER_B)),
            target = target(capabilityIds = listOf(AiProviderCapabilityId.STREAMING)),
        )
        val changedLimits = plan(
            candidate = candidate(signers = listOf(AiProviderHostTestFixtures.SIGNER_A, AiProviderHostTestFixtures.SIGNER_B)),
            target = target(maximumOutputBytes = 128L * 1024L),
        )
        val changedCatalog = plan(
            candidate = candidate(signers = listOf(AiProviderHostTestFixtures.SIGNER_A, AiProviderHostTestFixtures.SIGNER_B)),
            target = target(),
            catalogGeneration = "fixture-generation-2",
        )
        val changedProviderCompatibility = plan(
            candidate = candidate(
                minimumHostVersionCode = 1,
                signers = listOf(AiProviderHostTestFixtures.SIGNER_A, AiProviderHostTestFixtures.SIGNER_B),
            ),
            target = target(),
        )

        val firstKey = AiProviderBindingCacheKey.create(first)
        assertEquals(firstKey, AiProviderBindingCacheKey.create(reordered))
        assertNotEquals(firstKey, AiProviderBindingCacheKey.create(newerPackage))
        assertNotEquals(firstKey, AiProviderBindingCacheKey.create(otherTarget))
        assertNotEquals(firstKey, AiProviderBindingCacheKey.create(changedCapabilities))
        assertNotEquals(firstKey, AiProviderBindingCacheKey.create(changedLimits))
        assertNotEquals(firstKey, AiProviderBindingCacheKey.create(changedCatalog))
        assertNotEquals(firstKey, AiProviderBindingCacheKey.create(changedProviderCompatibility))
        assertTrue(Regex("^ai-provider-v2-[0-9a-f]{64}$").matches(firstKey))
    }

    @Test
    fun bindingCacheKeySeparatesCapabilityCollectionDomains() {
        val targetCapabilities = listOf(
            AiProviderCapabilityId.STREAMING,
            AiProviderCapabilityId.REASONING,
            AiProviderCapabilityId.STRUCTURED_JSON,
            AiProviderCapabilityId.USAGE,
        )
        val textThenSchemaEmpty = plan(
            candidate = AiProviderHostTestFixtures.candidate(
                capabilities = AiProviderHostTestFixtures.capabilities(
                    supportsTools = false,
                    acceptedTextMimeTypes = listOf(AiProviderMimeType.PLAIN, AiProviderMimeType.JSON),
                    acceptedSchemaMimeTypes = emptyList(),
                ),
            ),
            target = target(capabilityIds = targetCapabilities),
        )
        val textThenSchemaJson = plan(
            candidate = AiProviderHostTestFixtures.candidate(
                capabilities = AiProviderHostTestFixtures.capabilities(
                    supportsTools = false,
                    acceptedTextMimeTypes = listOf(AiProviderMimeType.PLAIN),
                    acceptedSchemaMimeTypes = listOf(AiProviderMimeType.JSON),
                ),
            ),
            target = target(capabilityIds = targetCapabilities),
        )

        assertNotEquals(
            AiProviderBindingCacheKey.create(textThenSchemaEmpty),
            AiProviderBindingCacheKey.create(textThenSchemaJson),
        )
    }

    @Test
    fun publicSessionPlanSnapshotsTargetCapabilities() {
        val mutableCapabilities = arrayListOf(AiProviderCapabilityId.STREAMING, AiProviderCapabilityId.USAGE)
        val target = target(capabilityIds = mutableCapabilities)
        val plan = plan(AiProviderHostTestFixtures.candidate(), target)
        mutableCapabilities.clear()

        assertEquals(setOf(AiProviderCapabilityId.STREAMING, AiProviderCapabilityId.USAGE), plan.targetCapabilityIds)
        try {
            @Suppress("UNCHECKED_CAST")
            (plan.targetCapabilityIds as MutableSet<String>).add(AiProviderCapabilityId.TOOLS)
            throw AssertionError("Target capabilities must be immutable")
        } catch (_: UnsupportedOperationException) {
            // Expected.
        }
    }

    private fun plan(
        candidate: AiProviderCandidate,
        target: AiTargetInfo,
        catalogGeneration: String = CATALOG_GENERATION,
    ): AiProviderHostSessionPlan = planned(selected(candidate), target, catalogGeneration)

    private fun planned(
        selected: AiProviderSelectionResult.Selected,
        target: AiTargetInfo,
        catalogGeneration: String = CATALOG_GENERATION,
    ): AiProviderHostSessionPlan {
        val result = AiProviderHostSessionPlanner.plan(
            selected,
            catalogGeneration,
            target,
            AiProviderCapabilityRequirement(),
        )
        assertTrue(result is AiProviderSessionPlanningResult.Planned)
        return (result as AiProviderSessionPlanningResult.Planned).plan
    }

    private fun selected(candidate: AiProviderCandidate): AiProviderSelectionResult.Selected {
        val result = AiProviderSelectionPolicy.select(
            candidates = listOf(candidate),
            environment = AiProviderHostTestFixtures.environment(),
            hostCapabilities = AiProviderHostTestFixtures.capabilities(),
            requirement = AiProviderCapabilityRequirement(),
            options = AiProviderSelectionOptions(enabled = true),
        )
        assertTrue(result is AiProviderSelectionResult.Selected)
        return result as AiProviderSelectionResult.Selected
    }

    private fun target(
        targetId: String = "fixture.provider:model",
        profileId: String? = null,
        modelId: String = "fixture.model",
        capabilityIds: List<String> = listOf(
            AiProviderCapabilityId.STREAMING,
            AiProviderCapabilityId.REASONING,
            AiProviderCapabilityId.TOOLS,
            AiProviderCapabilityId.STRUCTURED_JSON,
            AiProviderCapabilityId.USAGE,
        ),
        maximumOutputBytes: Long = 256L * 1024L,
        locality: Int = if (profileId == null) AiDataLocality.ON_DEVICE else AiDataLocality.REMOTE,
        declaredHttpsOrigins: List<String> = if (locality == AiDataLocality.ON_DEVICE) {
            emptyList()
        } else {
            listOf("https://provider.example")
        },
    ) = AiProviderHostTestFixtures.target(
        targetId = targetId,
        profileId = profileId,
        modelId = modelId,
        displayName = "Fixture Target",
        capabilityIds = capabilityIds,
        locality = locality,
        maximumContextBytes = 512L * 1024L,
        maximumOutputBytes = maximumOutputBytes,
        declaredHttpsOrigins = declaredHttpsOrigins,
    )

    private companion object {
        const val CATALOG_GENERATION = "fixture-generation-1"
    }
}
