package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.text.api.AiModelInfo
import org.autojs.plugin.ai.text.api.AiTextCapabilityId
import org.autojs.plugin.ai.text.api.AiTextMimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTextSessionPlanningTest {

    @Test
    fun localProviderPlanRequiresNoCredentialChannel() {
        val plan = plan(
            candidate = AiTextHostTestFixtures.candidate(),
            model = model(),
        )

        assertEquals(AiTextCredentialDisposition.NONE, plan.credentialDisposition)
        assertEquals(512L * 1024L, plan.effectiveMaximumContextBytes)
        assertEquals(256L * 1024L, plan.effectiveMaximumOutputBytes)
        assertEquals("fixture.model", plan.modelId)
    }

    @Test
    fun remoteCredentialsRemainOwnedByTheProviderProcess() {
        val remoteInfo = AiTextHostTestFixtures.providerInfo(
            locality = AiDataLocality.REMOTE,
            declaredHttpsOrigins = listOf("https://remote-provider.example"),
        )
        val plan = plan(
            candidate = AiTextHostTestFixtures.candidate(providerInfo = remoteInfo),
            model = model(),
        )

        assertEquals(AiTextCredentialDisposition.PROVIDER_PROCESS_ONLY, plan.credentialDisposition)
    }

    @Test
    fun modelMustMeetTheSameCapabilityRequirementAsTheProvider() {
        val selected = selected(AiTextHostTestFixtures.candidate())
        val requirement = AiTextCapabilityRequirement(
            requiredCapabilityIds = setOf(AiTextCapabilityId.TOOLS),
        )
        val modelWithoutTools = model(capabilityIds = listOf(AiTextCapabilityId.STREAMING))

        assertTrue(
            AiTextHostSessionPlanner.plan(selected, LISTING_GENERATION, modelWithoutTools, requirement) ===
                AiTextSessionPlanningResult.ModelCapabilityMismatch,
        )
    }

    @Test
    fun modelCannotAdvertiseACapabilityRemovedByProviderNegotiation() {
        val selected = selected(
            AiTextHostTestFixtures.candidate(
                capabilities = AiTextHostTestFixtures.capabilities(supportsReasoning = false),
            ),
        )

        assertTrue(
            AiTextHostSessionPlanner.plan(
                selected = selected,
                listingGeneration = "fixture-generation",
                model = model(),
                requirement = AiTextCapabilityRequirement(),
            ) === AiTextSessionPlanningResult.ModelCapabilityMismatch,
        )
    }

    @Test
    fun safePlanAndDiagnosticsNeverExposeProviderConfiguration() {
        val secret = "SENTINEL_SECRET_CONFIGURATION"
        val info = AiTextHostTestFixtures.providerInfo(
            displayName = secret,
            locality = AiDataLocality.REMOTE,
            declaredHttpsOrigins = listOf("https://sentinel-secret-configuration.example"),
        )
        val selected = selected(AiTextHostTestFixtures.candidate(providerInfo = info))
        val plan = planned(selected, model())
        val visible = listOf(
            selected.toString(),
            selected.pinned.toString(),
            plan.toString(),
            AiTextSafeDiagnosticFormatter.format(AiTextHostDiagnosticEvent.FAILED, plan),
            AiTextProviderBindingCacheKey.create(plan),
        ).joinToString(separator = "\n")

        assertFalse(visible.contains(secret, ignoreCase = true))
        assertFalse(visible.contains("sentinel-secret-configuration.example", ignoreCase = true))
        assertFalse(visible.contains("https://", ignoreCase = true))
        assertFalse(visible.contains(AiTextHostTestFixtures.SIGNER_A, ignoreCase = true))
        assertTrue(visible.contains("fixture.provider"))
    }

    @Test
    fun bindingCacheKeyIsDeterministicAndSensitiveOnlyToSafeSemanticIdentity() {
        val first = plan(
            candidate = AiTextHostTestFixtures.candidate(
                signers = listOf(AiTextHostTestFixtures.SIGNER_B, AiTextHostTestFixtures.SIGNER_A),
            ),
            model = model(),
        )
        val reordered = plan(
            candidate = AiTextHostTestFixtures.candidate(
                signers = listOf(AiTextHostTestFixtures.SIGNER_A, AiTextHostTestFixtures.SIGNER_B),
            ),
            model = model(),
        )
        val newerPackage = plan(
            candidate = AiTextHostTestFixtures.candidate(
                versionCode = 8,
                signers = listOf(AiTextHostTestFixtures.SIGNER_A, AiTextHostTestFixtures.SIGNER_B),
            ),
            model = model(),
        )
        val otherModel = plan(
            candidate = AiTextHostTestFixtures.candidate(
                signers = listOf(AiTextHostTestFixtures.SIGNER_A, AiTextHostTestFixtures.SIGNER_B),
            ),
            model = model(modelId = "fixture.other-model"),
        )
        val changedCapabilities = plan(
            candidate = AiTextHostTestFixtures.candidate(
                signers = listOf(AiTextHostTestFixtures.SIGNER_A, AiTextHostTestFixtures.SIGNER_B),
            ),
            model = model(capabilityIds = listOf(AiTextCapabilityId.STREAMING)),
        )
        val changedLimits = plan(
            candidate = AiTextHostTestFixtures.candidate(
                signers = listOf(AiTextHostTestFixtures.SIGNER_A, AiTextHostTestFixtures.SIGNER_B),
            ),
            model = model(maximumOutputBytes = 128L * 1024L),
        )
        val changedListing = plan(
            candidate = AiTextHostTestFixtures.candidate(
                signers = listOf(AiTextHostTestFixtures.SIGNER_A, AiTextHostTestFixtures.SIGNER_B),
            ),
            model = model(),
            listingGeneration = "fixture-generation-2",
        )
        val changedProviderCompatibility = plan(
            candidate = AiTextHostTestFixtures.candidate(
                signers = listOf(AiTextHostTestFixtures.SIGNER_A, AiTextHostTestFixtures.SIGNER_B),
                providerInfo = AiTextHostTestFixtures.providerInfo(minimumHostVersionCode = 1),
            ),
            model = model(),
        )

        val firstKey = AiTextProviderBindingCacheKey.create(first)
        assertEquals(firstKey, AiTextProviderBindingCacheKey.create(reordered))
        assertNotEquals(firstKey, AiTextProviderBindingCacheKey.create(newerPackage))
        assertNotEquals(firstKey, AiTextProviderBindingCacheKey.create(otherModel))
        assertNotEquals(firstKey, AiTextProviderBindingCacheKey.create(changedCapabilities))
        assertNotEquals(firstKey, AiTextProviderBindingCacheKey.create(changedLimits))
        assertNotEquals(firstKey, AiTextProviderBindingCacheKey.create(changedListing))
        assertNotEquals(firstKey, AiTextProviderBindingCacheKey.create(changedProviderCompatibility))
        assertTrue(Regex("^ai-text-provider-v1-[0-9a-f]{64}$").matches(firstKey))
    }

    @Test
    fun bindingCacheKeySeparatesCapabilityCollectionDomains() {
        val modelCapabilities = listOf(
            AiTextCapabilityId.STREAMING,
            AiTextCapabilityId.REASONING,
            AiTextCapabilityId.STRUCTURED_JSON,
            AiTextCapabilityId.USAGE,
        )
        val textThenSchemaEmpty = plan(
            candidate = AiTextHostTestFixtures.candidate(
                capabilities = AiTextHostTestFixtures.capabilities(
                    supportsTools = false,
                    acceptedTextMimeTypes = listOf(AiTextMimeType.PLAIN, AiTextMimeType.JSON),
                    acceptedSchemaMimeTypes = emptyList(),
                ),
            ),
            model = model(capabilityIds = modelCapabilities),
        )
        val textThenSchemaJson = plan(
            candidate = AiTextHostTestFixtures.candidate(
                capabilities = AiTextHostTestFixtures.capabilities(
                    supportsTools = false,
                    acceptedTextMimeTypes = listOf(AiTextMimeType.PLAIN),
                    acceptedSchemaMimeTypes = listOf(AiTextMimeType.JSON),
                ),
            ),
            model = model(capabilityIds = modelCapabilities),
        )

        assertNotEquals(
            AiTextProviderBindingCacheKey.create(textThenSchemaEmpty),
            AiTextProviderBindingCacheKey.create(textThenSchemaJson),
        )
    }

    @Test
    fun publicSessionPlanSnapshotsModelCapabilities() {
        val mutableCapabilities = arrayListOf(AiTextCapabilityId.STREAMING, AiTextCapabilityId.USAGE)
        val model = model(capabilityIds = mutableCapabilities)
        val plan = plan(AiTextHostTestFixtures.candidate(), model)
        mutableCapabilities.clear()

        assertEquals(setOf(AiTextCapabilityId.STREAMING, AiTextCapabilityId.USAGE), plan.modelCapabilityIds)
        try {
            @Suppress("UNCHECKED_CAST")
            (plan.modelCapabilityIds as MutableSet<String>).add(AiTextCapabilityId.TOOLS)
            throw AssertionError("Model capabilities must be immutable")
        } catch (_: UnsupportedOperationException) {
            // Expected.
        }
    }

    private fun plan(
        candidate: AiTextProviderCandidate,
        model: AiModelInfo,
        listingGeneration: String = LISTING_GENERATION,
    ): AiTextHostSessionPlan = planned(selected(candidate), model, listingGeneration)

    private fun planned(
        selected: AiTextProviderSelectionResult.Selected,
        model: AiModelInfo,
        listingGeneration: String = LISTING_GENERATION,
    ): AiTextHostSessionPlan {
        val result = AiTextHostSessionPlanner.plan(
            selected,
            listingGeneration,
            model,
            AiTextCapabilityRequirement(),
        )
        assertTrue(result is AiTextSessionPlanningResult.Planned)
        return (result as AiTextSessionPlanningResult.Planned).plan
    }

    private fun selected(candidate: AiTextProviderCandidate): AiTextProviderSelectionResult.Selected {
        val result = AiTextProviderSelectionPolicy.select(
            candidates = listOf(candidate),
            environment = AiTextHostTestFixtures.environment(),
            hostCapabilities = AiTextHostTestFixtures.capabilities(),
            requirement = AiTextCapabilityRequirement(),
            options = AiTextProviderSelectionOptions(enabled = true),
        )
        assertTrue(result is AiTextProviderSelectionResult.Selected)
        return result as AiTextProviderSelectionResult.Selected
    }

    private fun model(
        modelId: String = "fixture.model",
        capabilityIds: List<String> = listOf(
            AiTextCapabilityId.STREAMING,
            AiTextCapabilityId.REASONING,
            AiTextCapabilityId.TOOLS,
            AiTextCapabilityId.STRUCTURED_JSON,
            AiTextCapabilityId.USAGE,
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
