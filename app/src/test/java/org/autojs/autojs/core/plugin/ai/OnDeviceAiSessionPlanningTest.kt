package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ondeviceai.api.AiModelInfo
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilityId
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceAiSessionPlanningTest {

    @Test
    fun localProviderPlanRequiresNoCredentialChannel() {
        val plan = plan(
            candidate = OnDeviceAiHostTestFixtures.candidate(),
            model = model(),
        )

        assertEquals(OnDeviceAiCredentialDisposition.NONE, plan.credentialDisposition)
        assertEquals(512L * 1024L, plan.effectiveMaximumContextBytes)
        assertEquals(256L * 1024L, plan.effectiveMaximumOutputBytes)
        assertEquals("fixture.model", plan.modelId)
    }

    @Test
    fun remoteCredentialsRemainOwnedByTheProviderProcess() {
        val remoteInfo = OnDeviceAiHostTestFixtures.providerInfo(
            locality = AiDataLocality.REMOTE,
            declaredHttpsOrigins = listOf("https://remote-provider.example"),
        )
        val plan = plan(
            candidate = OnDeviceAiHostTestFixtures.candidate(providerInfo = remoteInfo),
            model = model(),
        )

        assertEquals(OnDeviceAiCredentialDisposition.PROVIDER_PROCESS_ONLY, plan.credentialDisposition)
    }

    @Test
    fun modelMustMeetTheSameCapabilityRequirementAsTheProvider() {
        val selected = selected(OnDeviceAiHostTestFixtures.candidate())
        val requirement = OnDeviceAiCapabilityRequirement(
            requiredCapabilityIds = setOf(OnDeviceAiCapabilityId.TOOLS),
        )
        val modelWithoutTools = model(capabilityIds = listOf(OnDeviceAiCapabilityId.STREAMING))

        assertTrue(
            OnDeviceAiHostSessionPlanner.plan(selected, LISTING_GENERATION, modelWithoutTools, requirement) ===
                OnDeviceAiSessionPlanningResult.ModelCapabilityMismatch,
        )
    }

    @Test
    fun modelCannotAdvertiseACapabilityRemovedByProviderNegotiation() {
        val selected = selected(
            OnDeviceAiHostTestFixtures.candidate(
                capabilities = OnDeviceAiHostTestFixtures.capabilities(supportsReasoning = false),
            ),
        )

        assertTrue(
            OnDeviceAiHostSessionPlanner.plan(
                selected = selected,
                listingGeneration = "fixture-generation",
                model = model(),
                requirement = OnDeviceAiCapabilityRequirement(),
            ) === OnDeviceAiSessionPlanningResult.ModelCapabilityMismatch,
        )
    }

    @Test
    fun safePlanAndDiagnosticsNeverExposeProviderConfiguration() {
        val secret = "SENTINEL_SECRET_CONFIGURATION"
        val info = OnDeviceAiHostTestFixtures.providerInfo(
            displayName = secret,
            locality = AiDataLocality.REMOTE,
            declaredHttpsOrigins = listOf("https://sentinel-secret-configuration.example"),
        )
        val selected = selected(OnDeviceAiHostTestFixtures.candidate(providerInfo = info))
        val plan = planned(selected, model())
        val visible = listOf(
            selected.toString(),
            selected.pinned.toString(),
            plan.toString(),
            OnDeviceAiSafeDiagnosticFormatter.format(OnDeviceAiHostDiagnosticEvent.FAILED, plan),
            OnDeviceAiProviderBindingCacheKey.create(plan),
        ).joinToString(separator = "\n")

        assertFalse(visible.contains(secret, ignoreCase = true))
        assertFalse(visible.contains("sentinel-secret-configuration.example", ignoreCase = true))
        assertFalse(visible.contains("https://", ignoreCase = true))
        assertFalse(visible.contains(OnDeviceAiHostTestFixtures.SIGNER_A, ignoreCase = true))
        assertTrue(visible.contains("fixture.provider"))
    }

    @Test
    fun bindingCacheKeyIsDeterministicAndSensitiveOnlyToSafeSemanticIdentity() {
        val first = plan(
            candidate = OnDeviceAiHostTestFixtures.candidate(
                signers = listOf(OnDeviceAiHostTestFixtures.SIGNER_B, OnDeviceAiHostTestFixtures.SIGNER_A),
            ),
            model = model(),
        )
        val reordered = plan(
            candidate = OnDeviceAiHostTestFixtures.candidate(
                signers = listOf(OnDeviceAiHostTestFixtures.SIGNER_A, OnDeviceAiHostTestFixtures.SIGNER_B),
            ),
            model = model(),
        )
        val newerPackage = plan(
            candidate = OnDeviceAiHostTestFixtures.candidate(
                versionCode = 8,
                signers = listOf(OnDeviceAiHostTestFixtures.SIGNER_A, OnDeviceAiHostTestFixtures.SIGNER_B),
            ),
            model = model(),
        )
        val otherModel = plan(
            candidate = OnDeviceAiHostTestFixtures.candidate(
                signers = listOf(OnDeviceAiHostTestFixtures.SIGNER_A, OnDeviceAiHostTestFixtures.SIGNER_B),
            ),
            model = model(modelId = "fixture.other-model"),
        )
        val changedCapabilities = plan(
            candidate = OnDeviceAiHostTestFixtures.candidate(
                signers = listOf(OnDeviceAiHostTestFixtures.SIGNER_A, OnDeviceAiHostTestFixtures.SIGNER_B),
            ),
            model = model(capabilityIds = listOf(OnDeviceAiCapabilityId.STREAMING)),
        )
        val changedLimits = plan(
            candidate = OnDeviceAiHostTestFixtures.candidate(
                signers = listOf(OnDeviceAiHostTestFixtures.SIGNER_A, OnDeviceAiHostTestFixtures.SIGNER_B),
            ),
            model = model(maximumOutputBytes = 128L * 1024L),
        )
        val changedListing = plan(
            candidate = OnDeviceAiHostTestFixtures.candidate(
                signers = listOf(OnDeviceAiHostTestFixtures.SIGNER_A, OnDeviceAiHostTestFixtures.SIGNER_B),
            ),
            model = model(),
            listingGeneration = "fixture-generation-2",
        )
        val changedProviderCompatibility = plan(
            candidate = OnDeviceAiHostTestFixtures.candidate(
                signers = listOf(OnDeviceAiHostTestFixtures.SIGNER_A, OnDeviceAiHostTestFixtures.SIGNER_B),
                providerInfo = OnDeviceAiHostTestFixtures.providerInfo(minimumHostVersionCode = 1),
            ),
            model = model(),
        )

        val firstKey = OnDeviceAiProviderBindingCacheKey.create(first)
        assertEquals(firstKey, OnDeviceAiProviderBindingCacheKey.create(reordered))
        assertNotEquals(firstKey, OnDeviceAiProviderBindingCacheKey.create(newerPackage))
        assertNotEquals(firstKey, OnDeviceAiProviderBindingCacheKey.create(otherModel))
        assertNotEquals(firstKey, OnDeviceAiProviderBindingCacheKey.create(changedCapabilities))
        assertNotEquals(firstKey, OnDeviceAiProviderBindingCacheKey.create(changedLimits))
        assertNotEquals(firstKey, OnDeviceAiProviderBindingCacheKey.create(changedListing))
        assertNotEquals(firstKey, OnDeviceAiProviderBindingCacheKey.create(changedProviderCompatibility))
        assertTrue(Regex("^on-device-ai-provider-v1-[0-9a-f]{64}$").matches(firstKey))
    }

    @Test
    fun bindingCacheKeySeparatesCapabilityCollectionDomains() {
        val modelCapabilities = listOf(
            OnDeviceAiCapabilityId.STREAMING,
            OnDeviceAiCapabilityId.REASONING,
            OnDeviceAiCapabilityId.STRUCTURED_JSON,
            OnDeviceAiCapabilityId.USAGE,
        )
        val textThenSchemaEmpty = plan(
            candidate = OnDeviceAiHostTestFixtures.candidate(
                capabilities = OnDeviceAiHostTestFixtures.capabilities(
                    supportsTools = false,
                    acceptedTextMimeTypes = listOf(OnDeviceAiMimeType.PLAIN, OnDeviceAiMimeType.JSON),
                    acceptedSchemaMimeTypes = emptyList(),
                ),
            ),
            model = model(capabilityIds = modelCapabilities),
        )
        val textThenSchemaJson = plan(
            candidate = OnDeviceAiHostTestFixtures.candidate(
                capabilities = OnDeviceAiHostTestFixtures.capabilities(
                    supportsTools = false,
                    acceptedTextMimeTypes = listOf(OnDeviceAiMimeType.PLAIN),
                    acceptedSchemaMimeTypes = listOf(OnDeviceAiMimeType.JSON),
                ),
            ),
            model = model(capabilityIds = modelCapabilities),
        )

        assertNotEquals(
            OnDeviceAiProviderBindingCacheKey.create(textThenSchemaEmpty),
            OnDeviceAiProviderBindingCacheKey.create(textThenSchemaJson),
        )
    }

    @Test
    fun publicSessionPlanSnapshotsModelCapabilities() {
        val mutableCapabilities = arrayListOf(OnDeviceAiCapabilityId.STREAMING, OnDeviceAiCapabilityId.USAGE)
        val model = model(capabilityIds = mutableCapabilities)
        val plan = plan(OnDeviceAiHostTestFixtures.candidate(), model)
        mutableCapabilities.clear()

        assertEquals(setOf(OnDeviceAiCapabilityId.STREAMING, OnDeviceAiCapabilityId.USAGE), plan.modelCapabilityIds)
        try {
            @Suppress("UNCHECKED_CAST")
            (plan.modelCapabilityIds as MutableSet<String>).add(OnDeviceAiCapabilityId.TOOLS)
            throw AssertionError("Model capabilities must be immutable")
        } catch (_: UnsupportedOperationException) {
            // Expected.
        }
    }

    private fun plan(
        candidate: OnDeviceAiProviderCandidate,
        model: AiModelInfo,
        listingGeneration: String = LISTING_GENERATION,
    ): OnDeviceAiHostSessionPlan = planned(selected(candidate), model, listingGeneration)

    private fun planned(
        selected: OnDeviceAiProviderSelectionResult.Selected,
        model: AiModelInfo,
        listingGeneration: String = LISTING_GENERATION,
    ): OnDeviceAiHostSessionPlan {
        val result = OnDeviceAiHostSessionPlanner.plan(
            selected,
            listingGeneration,
            model,
            OnDeviceAiCapabilityRequirement(),
        )
        assertTrue(result is OnDeviceAiSessionPlanningResult.Planned)
        return (result as OnDeviceAiSessionPlanningResult.Planned).plan
    }

    private fun selected(candidate: OnDeviceAiProviderCandidate): OnDeviceAiProviderSelectionResult.Selected {
        val result = OnDeviceAiProviderSelectionPolicy.select(
            candidates = listOf(candidate),
            environment = OnDeviceAiHostTestFixtures.environment(),
            hostCapabilities = OnDeviceAiHostTestFixtures.capabilities(),
            requirement = OnDeviceAiCapabilityRequirement(),
            options = OnDeviceAiProviderSelectionOptions(enabled = true),
        )
        assertTrue(result is OnDeviceAiProviderSelectionResult.Selected)
        return result as OnDeviceAiProviderSelectionResult.Selected
    }

    private fun model(
        modelId: String = "fixture.model",
        capabilityIds: List<String> = listOf(
            OnDeviceAiCapabilityId.STREAMING,
            OnDeviceAiCapabilityId.REASONING,
            OnDeviceAiCapabilityId.TOOLS,
            OnDeviceAiCapabilityId.STRUCTURED_JSON,
            OnDeviceAiCapabilityId.USAGE,
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
