package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.provider.api.AiModelInfo
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderSessionPlanningTest {

    @Test
    fun localProviderPlanRequiresNoCredentialChannel() {
        val plan = plan(
            candidate = AiProviderHostTestFixtures.candidate(),
            model = model(),
        )

        assertEquals(AiProviderCredentialDisposition.NONE, plan.credentialDisposition)
        assertEquals(512L * 1024L, plan.effectiveMaximumContextBytes)
        assertEquals(256L * 1024L, plan.effectiveMaximumOutputBytes)
        assertEquals("fixture.model", plan.modelId)
    }

    @Test
    fun remoteCredentialsRemainOwnedByTheProviderProcess() {
        val remoteInfo = AiProviderHostTestFixtures.providerInfo(
            locality = AiDataLocality.REMOTE,
            declaredHttpsOrigins = listOf("https://remote-provider.example"),
        )
        val plan = plan(
            candidate = AiProviderHostTestFixtures.candidate(providerInfo = remoteInfo),
            model = model(),
        )

        assertEquals(AiProviderCredentialDisposition.PROVIDER_PROCESS_ONLY, plan.credentialDisposition)
    }

    @Test
    fun modelMustMeetTheSameCapabilityRequirementAsTheProvider() {
        val selected = selected(AiProviderHostTestFixtures.candidate())
        val requirement = AiProviderCapabilityRequirement(
            requiredCapabilityIds = setOf(AiProviderCapabilityId.TOOLS),
        )
        val modelWithoutTools = model(capabilityIds = listOf(AiProviderCapabilityId.STREAMING))

        assertTrue(
            AiProviderHostSessionPlanner.plan(selected, LISTING_GENERATION, modelWithoutTools, requirement) ===
                AiProviderSessionPlanningResult.ModelCapabilityMismatch,
        )
    }

    @Test
    fun modelCannotAdvertiseACapabilityRemovedByProviderNegotiation() {
        val selected = selected(
            AiProviderHostTestFixtures.candidate(
                capabilities = AiProviderHostTestFixtures.capabilities(supportsReasoning = false),
            ),
        )

        assertTrue(
            AiProviderHostSessionPlanner.plan(
                selected = selected,
                listingGeneration = "fixture-generation",
                model = model(),
                requirement = AiProviderCapabilityRequirement(),
            ) === AiProviderSessionPlanningResult.ModelCapabilityMismatch,
        )
    }

    @Test
    fun safePlanAndDiagnosticsNeverExposeProviderConfiguration() {
        val secret = "SENTINEL_SECRET_CONFIGURATION"
        val info = AiProviderHostTestFixtures.providerInfo(
            displayName = secret,
            locality = AiDataLocality.REMOTE,
            declaredHttpsOrigins = listOf("https://sentinel-secret-configuration.example"),
        )
        val selected = selected(AiProviderHostTestFixtures.candidate(providerInfo = info))
        val plan = planned(selected, model())
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
    fun bindingCacheKeyIsDeterministicAndSensitiveOnlyToSafeSemanticIdentity() {
        val first = plan(
            candidate = AiProviderHostTestFixtures.candidate(
                signers = listOf(AiProviderHostTestFixtures.SIGNER_B, AiProviderHostTestFixtures.SIGNER_A),
            ),
            model = model(),
        )
        val reordered = plan(
            candidate = AiProviderHostTestFixtures.candidate(
                signers = listOf(AiProviderHostTestFixtures.SIGNER_A, AiProviderHostTestFixtures.SIGNER_B),
            ),
            model = model(),
        )
        val newerPackage = plan(
            candidate = AiProviderHostTestFixtures.candidate(
                versionCode = 8,
                signers = listOf(AiProviderHostTestFixtures.SIGNER_A, AiProviderHostTestFixtures.SIGNER_B),
            ),
            model = model(),
        )
        val otherModel = plan(
            candidate = AiProviderHostTestFixtures.candidate(
                signers = listOf(AiProviderHostTestFixtures.SIGNER_A, AiProviderHostTestFixtures.SIGNER_B),
            ),
            model = model(modelId = "fixture.other-model"),
        )
        val changedCapabilities = plan(
            candidate = AiProviderHostTestFixtures.candidate(
                signers = listOf(AiProviderHostTestFixtures.SIGNER_A, AiProviderHostTestFixtures.SIGNER_B),
            ),
            model = model(capabilityIds = listOf(AiProviderCapabilityId.STREAMING)),
        )
        val changedLimits = plan(
            candidate = AiProviderHostTestFixtures.candidate(
                signers = listOf(AiProviderHostTestFixtures.SIGNER_A, AiProviderHostTestFixtures.SIGNER_B),
            ),
            model = model(maximumOutputBytes = 128L * 1024L),
        )
        val changedListing = plan(
            candidate = AiProviderHostTestFixtures.candidate(
                signers = listOf(AiProviderHostTestFixtures.SIGNER_A, AiProviderHostTestFixtures.SIGNER_B),
            ),
            model = model(),
            listingGeneration = "fixture-generation-2",
        )
        val changedProviderCompatibility = plan(
            candidate = AiProviderHostTestFixtures.candidate(
                signers = listOf(AiProviderHostTestFixtures.SIGNER_A, AiProviderHostTestFixtures.SIGNER_B),
                providerInfo = AiProviderHostTestFixtures.providerInfo(minimumHostVersionCode = 1),
            ),
            model = model(),
        )

        val firstKey = AiProviderBindingCacheKey.create(first)
        assertEquals(firstKey, AiProviderBindingCacheKey.create(reordered))
        assertNotEquals(firstKey, AiProviderBindingCacheKey.create(newerPackage))
        assertNotEquals(firstKey, AiProviderBindingCacheKey.create(otherModel))
        assertNotEquals(firstKey, AiProviderBindingCacheKey.create(changedCapabilities))
        assertNotEquals(firstKey, AiProviderBindingCacheKey.create(changedLimits))
        assertNotEquals(firstKey, AiProviderBindingCacheKey.create(changedListing))
        assertNotEquals(firstKey, AiProviderBindingCacheKey.create(changedProviderCompatibility))
        assertTrue(Regex("^ai-provider-v1-[0-9a-f]{64}$").matches(firstKey))
    }

    @Test
    fun bindingCacheKeySeparatesCapabilityCollectionDomains() {
        val modelCapabilities = listOf(
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
            model = model(capabilityIds = modelCapabilities),
        )
        val textThenSchemaJson = plan(
            candidate = AiProviderHostTestFixtures.candidate(
                capabilities = AiProviderHostTestFixtures.capabilities(
                    supportsTools = false,
                    acceptedTextMimeTypes = listOf(AiProviderMimeType.PLAIN),
                    acceptedSchemaMimeTypes = listOf(AiProviderMimeType.JSON),
                ),
            ),
            model = model(capabilityIds = modelCapabilities),
        )

        assertNotEquals(
            AiProviderBindingCacheKey.create(textThenSchemaEmpty),
            AiProviderBindingCacheKey.create(textThenSchemaJson),
        )
    }

    @Test
    fun publicSessionPlanSnapshotsModelCapabilities() {
        val mutableCapabilities = arrayListOf(AiProviderCapabilityId.STREAMING, AiProviderCapabilityId.USAGE)
        val model = model(capabilityIds = mutableCapabilities)
        val plan = plan(AiProviderHostTestFixtures.candidate(), model)
        mutableCapabilities.clear()

        assertEquals(setOf(AiProviderCapabilityId.STREAMING, AiProviderCapabilityId.USAGE), plan.modelCapabilityIds)
        try {
            @Suppress("UNCHECKED_CAST")
            (plan.modelCapabilityIds as MutableSet<String>).add(AiProviderCapabilityId.TOOLS)
            throw AssertionError("Model capabilities must be immutable")
        } catch (_: UnsupportedOperationException) {
            // Expected.
        }
    }

    private fun plan(
        candidate: AiProviderCandidate,
        model: AiModelInfo,
        listingGeneration: String = LISTING_GENERATION,
    ): AiProviderHostSessionPlan = planned(selected(candidate), model, listingGeneration)

    private fun planned(
        selected: AiProviderSelectionResult.Selected,
        model: AiModelInfo,
        listingGeneration: String = LISTING_GENERATION,
    ): AiProviderHostSessionPlan {
        val result = AiProviderHostSessionPlanner.plan(
            selected,
            listingGeneration,
            model,
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

    private fun model(
        modelId: String = "fixture.model",
        capabilityIds: List<String> = listOf(
            AiProviderCapabilityId.STREAMING,
            AiProviderCapabilityId.REASONING,
            AiProviderCapabilityId.TOOLS,
            AiProviderCapabilityId.STRUCTURED_JSON,
            AiProviderCapabilityId.USAGE,
        ),
        maximumOutputBytes: Long = 256L * 1024L,
    ) = AiModelInfo(
        modelId = modelId,
        displayName = "Fixture Model",
        capabilityIds = capabilityIds,
        maximumContextBytes = 512L * 1024L,
        maximumOutputBytes = maximumOutputBytes,
    )

    private companion object {
        const val LISTING_GENERATION = "fixture-generation-1"
    }
}
