package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilityId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OnDeviceAiProviderSelectionPolicyTest {

    @Test
    fun selectionIsDisabledByDefault() {
        val result = OnDeviceAiProviderSelectionPolicy.select(
            candidates = listOf(OnDeviceAiHostTestFixtures.candidate()),
            environment = OnDeviceAiHostTestFixtures.environment(),
            hostCapabilities = OnDeviceAiHostTestFixtures.capabilities(),
            requirement = OnDeviceAiCapabilityRequirement(),
        )

        assertTrue(result === OnDeviceAiProviderSelectionResult.Disabled)
    }

    @Test
    fun discoveryBoundaryMismatchesAreRejectedBeforeNegotiation() {
        assertEquals(OnDeviceAiProviderCandidateRejection.WRONG_ACTION, rejection(candidate().copy(declaredAction = "wrong")))
        assertEquals(OnDeviceAiProviderCandidateRejection.NOT_EXPORTED, rejection(candidate().copy(exported = false)))
        assertEquals(
            OnDeviceAiProviderCandidateRejection.DISABLED,
            rejection(candidate().copy(applicationEnabled = false)),
        )
        assertEquals(
            OnDeviceAiProviderCandidateRejection.DISABLED,
            rejection(candidate().copy(serviceEnabled = false)),
        )
        assertEquals(
            OnDeviceAiProviderCandidateRejection.WRONG_PERMISSION,
            rejection(candidate().copy(requiredPermission = "wrong.permission")),
        )
        assertEquals(
            OnDeviceAiProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(packageUid = -1)),
        )
        assertEquals(
            OnDeviceAiProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(versionCode = 0)),
        )
        assertEquals(
            OnDeviceAiProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(lastUpdateTime = -1)),
        )
        assertEquals(
            OnDeviceAiProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(signerSha256 = emptyList())),
        )
        assertEquals(
            OnDeviceAiProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(signerSha256 = listOf("not-a-digest"))),
        )
        assertEquals(
            OnDeviceAiProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(signerSha256 = listOf(SIGNER_A, SIGNER_A.uppercase()))),
        )
    }

    @Test
    fun trustRequiresAnExplicitlyAcceptedSource() {
        assertEquals(
            OnDeviceAiProviderCandidateRejection.TRUST_REQUIRED,
            rejection(candidate().copy(trustLevel = OnDeviceAiProviderTrustLevel.UNTRUSTED)),
        )
        assertEquals(
            OnDeviceAiProviderCandidateRejection.USER_APPROVAL_REQUIRED,
            rejection(candidate().copy(trustLevel = OnDeviceAiProviderTrustLevel.USER_APPROVED)),
        )
        assertEquals(
            OnDeviceAiProviderCandidateRejection.DEBUG_TRUST_FORBIDDEN,
            rejection(candidate().copy(trustLevel = OnDeviceAiProviderTrustLevel.DEBUG_ONLY)),
        )
        assertSelected(
            candidate().copy(trustLevel = OnDeviceAiProviderTrustLevel.USER_APPROVED),
            environment(allowUserApprovedProviders = true),
        )
        assertSelected(
            candidate().copy(trustLevel = OnDeviceAiProviderTrustLevel.DEBUG_ONLY),
            environment(releaseBuild = false),
        )
        assertSelected(
            candidate().copy(
                trustLevel = OnDeviceAiProviderTrustLevel.UNTRUSTED,
                sameSignerAsHost = true,
            ),
        )
    }

    @Test
    fun hostVersionAbiAndProtocolMustOverlap() {
        assertEquals(
            OnDeviceAiProviderCandidateRejection.HOST_VERSION_UNSUPPORTED,
            rejection(candidate().copy(providerInfo = providerInfo(minimumHostVersionCode = 7_000))),
        )
        assertEquals(
            OnDeviceAiProviderCandidateRejection.HOST_VERSION_UNSUPPORTED,
            rejection(candidate().copy(providerInfo = providerInfo(maximumHostVersionCode = 6_000))),
        )
        assertEquals(
            OnDeviceAiProviderCandidateRejection.ABI_UNSUPPORTED,
            rejection(candidate().copy(providerInfo = providerInfo(supportedAbis = listOf("x86_64")))),
        )
        assertEquals(
            OnDeviceAiProviderCandidateRejection.ABI_UNSUPPORTED,
            rejection(
                candidate().copy(providerInfo = providerInfo(supportedAbis = listOf("arm64-v8a"))),
                environment = environment(currentAbi = null),
            ),
        )
        assertEquals(
            OnDeviceAiProviderCandidateRejection.PROTOCOL_UNSUPPORTED,
            rejection(
                candidate().copy(providerInfo = providerInfo(protocolRange = unsupportedProtocolRange())),
            ),
        )
        assertSelected(candidate().copy(providerInfo = providerInfo(supportedAbis = emptyList())))
    }

    @Test
    fun capabilityMatchingUsesTheHostProviderIntersection() {
        val host = capabilities(maximumContextBytes = 512L * 1024L, supportsReasoning = false)
        val provider = candidate().copy(capabilities = capabilities(maximumContextBytes = 1024L * 1024L))
        val selected = select(
            listOf(provider),
            hostCapabilities = host,
            requirement = OnDeviceAiCapabilityRequirement(minimumContextBytes = 256L * 1024L),
        ) as OnDeviceAiProviderSelectionResult.Selected

        assertEquals(512L * 1024L, selected.pinned.negotiatedCapabilities.maximumContextBytes)
        assertFalse(selected.pinned.negotiatedCapabilities.supportsReasoning)
        assertEquals(
            OnDeviceAiProviderCandidateRejection.CAPABILITY_MISMATCH,
            rejection(
                provider,
                hostCapabilities = host,
                requirement = OnDeviceAiCapabilityRequirement(minimumContextBytes = 700L * 1024L),
            ),
        )
        assertEquals(
            OnDeviceAiProviderCandidateRejection.CAPABILITY_MISMATCH,
            rejection(
                provider,
                hostCapabilities = host,
                requirement = OnDeviceAiCapabilityRequirement(
                    requiredCapabilityIds = setOf(OnDeviceAiCapabilityId.REASONING),
                ),
            ),
        )
    }

    @Test
    fun invalidCapabilityRequirementIsRejectedAtConstruction() {
        try {
            OnDeviceAiCapabilityRequirement(requiredCapabilityIds = setOf("unknown-capability"))
            fail("Unknown capability must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun singleCandidatePinsAnImmutableExactIdentity() {
        val mutableSigners = arrayListOf(SIGNER_B, SIGNER_A)
        val original = candidate().copy(signerSha256 = mutableSigners)
        mutableSigners.clear()
        val selected = select(listOf(original)) as OnDeviceAiProviderSelectionResult.Selected
        val pinned = selected.pinned

        assertEquals(listOf(SIGNER_A, SIGNER_B), pinned.signerSha256)
        assertEquals(original.component, pinned.component)
        assertEquals(original.packageUid, pinned.packageUid)
        assertEquals(original.versionCode, pinned.versionCode)
        assertEquals(original.lastUpdateTime, pinned.lastUpdateTime)
        assertEquals(original.providerInfo.providerId, pinned.providerId)
        try {
            @Suppress("UNCHECKED_CAST")
            (pinned.signerSha256 as MutableList<String>).add(SIGNER_A)
            fail("Pinned signer list must be immutable")
        } catch (_: UnsupportedOperationException) {
            // Expected.
        }
    }

    @Test
    fun multipleEligibleProvidersAreRejectedInDeterministicOrder() {
        val zeta = candidate(packageName = "zeta.provider")
        val alpha = candidate(packageName = "alpha.provider", packageUid = 10_002)
        val ambiguous = select(listOf(zeta, alpha)) as OnDeviceAiProviderSelectionResult.Ambiguous

        assertEquals(
            listOf("alpha.provider", "zeta.provider"),
            ambiguous.candidates.map { it.component.packageName },
        )

        val selected = select(
            listOf(zeta, alpha),
            preferred = zeta.component,
        ) as OnDeviceAiProviderSelectionResult.Selected
        assertEquals("zeta.provider", selected.pinned.component.packageName)
    }

    @Test
    fun duplicateDiscoveryRowsRemainAmbiguousEvenWithPreference() {
        val value = candidate()
        val result = select(listOf(value, value.copy()), preferred = value.component)

        assertTrue(result is OnDeviceAiProviderSelectionResult.Ambiguous)
        assertEquals(2, (result as OnDeviceAiProviderSelectionResult.Ambiguous).candidates.size)
    }

    @Test
    fun revalidationRejectsEveryPinnedIdentityDrift() {
        val original = candidate()
        val pinned = (select(listOf(original)) as OnDeviceAiProviderSelectionResult.Selected).pinned

        assertTrue(revalidate(pinned, original))
        assertFalse(revalidate(pinned, original.copy(packageUid = original.packageUid + 1)))
        assertFalse(revalidate(pinned, original.copy(versionCode = original.versionCode + 1)))
        assertFalse(revalidate(pinned, original.copy(lastUpdateTime = original.lastUpdateTime + 1)))
        assertFalse(revalidate(pinned, original.copy(signerSha256 = listOf(SIGNER_B))))
        assertFalse(
            revalidate(
                pinned,
                original.copy(providerInfo = original.providerInfo.copy(displayName = "Changed metadata")),
            ),
        )
        assertFalse(
            revalidate(
                pinned,
                original.copy(capabilities = original.capabilities.copy(maximumOutputBytes = 1024)),
            ),
        )
        assertFalse(revalidate(pinned, null))
    }

    @Test
    fun callbacksMustComeFromThePinnedPackageUid() {
        val pinned = (select(listOf(candidate())) as OnDeviceAiProviderSelectionResult.Selected).pinned

        assertTrue(OnDeviceAiProviderCallbackCallerPolicy.isExpectedUid(pinned, pinned.packageUid))
        assertFalse(OnDeviceAiProviderCallbackCallerPolicy.isExpectedUid(pinned, pinned.packageUid + 1))
        assertFalse(OnDeviceAiProviderCallbackCallerPolicy.isExpectedUid(pinned, -1))
    }

    private fun rejection(
        candidate: OnDeviceAiProviderCandidate,
        environment: OnDeviceAiProviderEnvironment = environment(),
        hostCapabilities: org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilities = capabilities(),
        requirement: OnDeviceAiCapabilityRequirement = OnDeviceAiCapabilityRequirement(),
    ): OnDeviceAiProviderCandidateRejection {
        val result = select(
            candidates = listOf(candidate),
            environment = environment,
            hostCapabilities = hostCapabilities,
            requirement = requirement,
        ) as OnDeviceAiProviderSelectionResult.NoMatch
        return result.evaluations.single().rejection
    }

    private fun assertSelected(
        candidate: OnDeviceAiProviderCandidate,
        environment: OnDeviceAiProviderEnvironment = environment(),
    ) {
        assertTrue(select(listOf(candidate), environment = environment) is OnDeviceAiProviderSelectionResult.Selected)
    }

    private fun select(
        candidates: Collection<OnDeviceAiProviderCandidate>,
        environment: OnDeviceAiProviderEnvironment = environment(),
        hostCapabilities: org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilities = capabilities(),
        requirement: OnDeviceAiCapabilityRequirement = OnDeviceAiCapabilityRequirement(),
        preferred: OnDeviceAiProviderComponent? = null,
    ): OnDeviceAiProviderSelectionResult = OnDeviceAiProviderSelectionPolicy.select(
        candidates = candidates,
        environment = environment,
        hostCapabilities = hostCapabilities,
        requirement = requirement,
        options = OnDeviceAiProviderSelectionOptions(enabled = true, preferredComponent = preferred),
    )

    private fun revalidate(
        pinned: OnDeviceAiProviderPinnedIdentity,
        current: OnDeviceAiProviderCandidate?,
    ): Boolean = OnDeviceAiProviderSelectionPolicy.revalidate(
        pinned = pinned,
        current = current,
        environment = environment(),
        hostCapabilities = capabilities(),
        requirement = OnDeviceAiCapabilityRequirement(),
    )

    private companion object {
        val SIGNER_A = OnDeviceAiHostTestFixtures.SIGNER_A
        val SIGNER_B = OnDeviceAiHostTestFixtures.SIGNER_B
    }
}

private fun candidate(
    packageName: String = "example.ai.provider",
    packageUid: Int = 10_001,
): OnDeviceAiProviderCandidate = OnDeviceAiHostTestFixtures.candidate(packageName = packageName, packageUid = packageUid)
private fun providerInfo(
    minimumHostVersionCode: Long = 0L,
    maximumHostVersionCode: Long? = null,
    supportedAbis: List<String> = emptyList(),
    protocolRange: org.autojs.plugin.ai.common.api.AiProtocolRange = org.autojs.plugin.ondeviceai.api.OnDeviceAiProtocol.HOST_PROTOCOL_RANGE,
) = OnDeviceAiHostTestFixtures.providerInfo(
    minimumHostVersionCode = minimumHostVersionCode,
    maximumHostVersionCode = maximumHostVersionCode,
    supportedAbis = supportedAbis,
    protocolRange = protocolRange,
)
private fun capabilities(
    maximumContextBytes: Long = 1024L * 1024L,
    supportsReasoning: Boolean = true,
) = OnDeviceAiHostTestFixtures.capabilities(
    maximumContextBytes = maximumContextBytes,
    supportsReasoning = supportsReasoning,
)
private fun environment(
    currentAbi: String? = "arm64-v8a",
    releaseBuild: Boolean = true,
    allowUserApprovedProviders: Boolean = false,
) = OnDeviceAiHostTestFixtures.environment(
    currentAbi = currentAbi,
    releaseBuild = releaseBuild,
    allowUserApprovedProviders = allowUserApprovedProviders,
)
private fun unsupportedProtocolRange() = OnDeviceAiHostTestFixtures.unsupportedProtocolRange()
