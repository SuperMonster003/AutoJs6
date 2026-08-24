package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiProviderSelectionPolicyTest {

    @Test
    fun selectionIsDisabledByDefault() {
        val result = AiProviderSelectionPolicy.select(
            candidates = listOf(AiProviderHostTestFixtures.candidate()),
            environment = AiProviderHostTestFixtures.environment(),
            hostCapabilities = AiProviderHostTestFixtures.capabilities(),
            requirement = AiProviderCapabilityRequirement(),
        )

        assertTrue(result === AiProviderSelectionResult.Disabled)
    }

    @Test
    fun discoveryBoundaryMismatchesAreRejectedBeforeNegotiation() {
        assertEquals(AiProviderCandidateRejection.WRONG_ACTION, rejection(candidate().copy(declaredAction = "wrong")))
        assertEquals(AiProviderCandidateRejection.NOT_EXPORTED, rejection(candidate().copy(exported = false)))
        assertEquals(
            AiProviderCandidateRejection.DISABLED,
            rejection(candidate().copy(applicationEnabled = false)),
        )
        assertEquals(
            AiProviderCandidateRejection.DISABLED,
            rejection(candidate().copy(serviceEnabled = false)),
        )
        assertEquals(
            AiProviderCandidateRejection.WRONG_PERMISSION,
            rejection(candidate().copy(requiredPermission = "wrong.permission")),
        )
        assertEquals(
            AiProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(packageUid = -1)),
        )
        assertEquals(
            AiProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(versionCode = 0)),
        )
        assertEquals(
            AiProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(lastUpdateTime = -1)),
        )
        assertEquals(
            AiProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(signerSha256 = emptyList())),
        )
        assertEquals(
            AiProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(signerSha256 = listOf("not-a-digest"))),
        )
        assertEquals(
            AiProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(signerSha256 = listOf(SIGNER_A, SIGNER_A.uppercase()))),
        )
    }

    @Test
    fun trustRequiresAnExplicitlyAcceptedSource() {
        assertEquals(
            AiProviderCandidateRejection.TRUST_REQUIRED,
            rejection(candidate().copy(trustLevel = AiProviderTrustLevel.UNTRUSTED)),
        )
        assertEquals(
            AiProviderCandidateRejection.USER_APPROVAL_REQUIRED,
            rejection(candidate().copy(trustLevel = AiProviderTrustLevel.USER_APPROVED)),
        )
        assertEquals(
            AiProviderCandidateRejection.DEBUG_TRUST_FORBIDDEN,
            rejection(candidate().copy(trustLevel = AiProviderTrustLevel.DEBUG_ONLY)),
        )
        assertSelected(
            candidate().copy(trustLevel = AiProviderTrustLevel.USER_APPROVED),
            environment(allowUserApprovedProviders = true),
        )
        assertSelected(
            candidate().copy(trustLevel = AiProviderTrustLevel.DEBUG_ONLY),
            environment(releaseBuild = false),
        )
        assertSelected(
            candidate().copy(
                trustLevel = AiProviderTrustLevel.UNTRUSTED,
                sameSignerAsHost = true,
            ),
        )
    }

    @Test
    fun hostVersionAbiAndProtocolMustOverlap() {
        assertEquals(
            AiProviderCandidateRejection.HOST_VERSION_UNSUPPORTED,
            rejection(candidate().copy(providerInfo = providerInfo(minimumHostVersionCode = 7_000))),
        )
        assertEquals(
            AiProviderCandidateRejection.HOST_VERSION_UNSUPPORTED,
            rejection(candidate().copy(providerInfo = providerInfo(maximumHostVersionCode = 6_000))),
        )
        assertEquals(
            AiProviderCandidateRejection.ABI_UNSUPPORTED,
            rejection(candidate().copy(providerInfo = providerInfo(supportedAbis = listOf("x86_64")))),
        )
        assertEquals(
            AiProviderCandidateRejection.ABI_UNSUPPORTED,
            rejection(
                candidate().copy(providerInfo = providerInfo(supportedAbis = listOf("arm64-v8a"))),
                environment = environment(currentAbi = null),
            ),
        )
        assertEquals(
            AiProviderCandidateRejection.PROTOCOL_UNSUPPORTED,
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
            requirement = AiProviderCapabilityRequirement(minimumContextBytes = 256L * 1024L),
        ) as AiProviderSelectionResult.Selected

        assertEquals(512L * 1024L, selected.pinned.negotiatedCapabilities.maximumContextBytes)
        assertFalse(selected.pinned.negotiatedCapabilities.supportsReasoning)
        assertEquals(
            AiProviderCandidateRejection.CAPABILITY_MISMATCH,
            rejection(
                provider,
                hostCapabilities = host,
                requirement = AiProviderCapabilityRequirement(minimumContextBytes = 700L * 1024L),
            ),
        )
        assertEquals(
            AiProviderCandidateRejection.CAPABILITY_MISMATCH,
            rejection(
                provider,
                hostCapabilities = host,
                requirement = AiProviderCapabilityRequirement(
                    requiredCapabilityIds = setOf(AiProviderCapabilityId.REASONING),
                ),
            ),
        )
    }

    @Test
    fun invalidCapabilityRequirementIsRejectedAtConstruction() {
        try {
            AiProviderCapabilityRequirement(requiredCapabilityIds = setOf("unknown-capability"))
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
        val selected = select(listOf(original)) as AiProviderSelectionResult.Selected
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
        val ambiguous = select(listOf(zeta, alpha)) as AiProviderSelectionResult.Ambiguous

        assertEquals(
            listOf("alpha.provider", "zeta.provider"),
            ambiguous.candidates.map { it.component.packageName },
        )

        val selected = select(
            listOf(zeta, alpha),
            preferred = zeta.component,
        ) as AiProviderSelectionResult.Selected
        assertEquals("zeta.provider", selected.pinned.component.packageName)
    }

    @Test
    fun duplicateDiscoveryRowsRemainAmbiguousEvenWithPreference() {
        val value = candidate()
        val result = select(listOf(value, value.copy()), preferred = value.component)

        assertTrue(result is AiProviderSelectionResult.Ambiguous)
        assertEquals(2, (result as AiProviderSelectionResult.Ambiguous).candidates.size)
    }

    @Test
    fun revalidationRejectsEveryPinnedIdentityDrift() {
        val original = candidate()
        val pinned = (select(listOf(original)) as AiProviderSelectionResult.Selected).pinned

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
        val pinned = (select(listOf(candidate())) as AiProviderSelectionResult.Selected).pinned

        assertTrue(AiProviderCallbackCallerPolicy.isExpectedUid(pinned, pinned.packageUid))
        assertFalse(AiProviderCallbackCallerPolicy.isExpectedUid(pinned, pinned.packageUid + 1))
        assertFalse(AiProviderCallbackCallerPolicy.isExpectedUid(pinned, -1))
    }

    private fun rejection(
        candidate: AiProviderCandidate,
        environment: AiProviderEnvironment = environment(),
        hostCapabilities: org.autojs.plugin.ai.provider.api.AiProviderCapabilities = capabilities(),
        requirement: AiProviderCapabilityRequirement = AiProviderCapabilityRequirement(),
    ): AiProviderCandidateRejection {
        val result = select(
            candidates = listOf(candidate),
            environment = environment,
            hostCapabilities = hostCapabilities,
            requirement = requirement,
        ) as AiProviderSelectionResult.NoMatch
        return result.evaluations.single().rejection
    }

    private fun assertSelected(
        candidate: AiProviderCandidate,
        environment: AiProviderEnvironment = environment(),
    ) {
        assertTrue(select(listOf(candidate), environment = environment) is AiProviderSelectionResult.Selected)
    }

    private fun select(
        candidates: Collection<AiProviderCandidate>,
        environment: AiProviderEnvironment = environment(),
        hostCapabilities: org.autojs.plugin.ai.provider.api.AiProviderCapabilities = capabilities(),
        requirement: AiProviderCapabilityRequirement = AiProviderCapabilityRequirement(),
        preferred: AiProviderComponent? = null,
    ): AiProviderSelectionResult = AiProviderSelectionPolicy.select(
        candidates = candidates,
        environment = environment,
        hostCapabilities = hostCapabilities,
        requirement = requirement,
        options = AiProviderSelectionOptions(enabled = true, preferredComponent = preferred),
    )

    private fun revalidate(
        pinned: AiProviderPinnedIdentity,
        current: AiProviderCandidate?,
    ): Boolean = AiProviderSelectionPolicy.revalidate(
        pinned = pinned,
        current = current,
        environment = environment(),
        hostCapabilities = capabilities(),
        requirement = AiProviderCapabilityRequirement(),
    )

    private companion object {
        val SIGNER_A = AiProviderHostTestFixtures.SIGNER_A
        val SIGNER_B = AiProviderHostTestFixtures.SIGNER_B
    }
}

private fun candidate(
    packageName: String = "example.ai.provider",
    packageUid: Int = 10_001,
): AiProviderCandidate = AiProviderHostTestFixtures.candidate(packageName = packageName, packageUid = packageUid)
private fun providerInfo(
    minimumHostVersionCode: Long = 0L,
    maximumHostVersionCode: Long? = null,
    supportedAbis: List<String> = emptyList(),
    protocolRange: org.autojs.plugin.ai.common.api.AiProtocolRange = org.autojs.plugin.ai.provider.api.AiProviderProtocol.HOST_PROTOCOL_RANGE,
) = AiProviderHostTestFixtures.providerInfo(
    minimumHostVersionCode = minimumHostVersionCode,
    maximumHostVersionCode = maximumHostVersionCode,
    supportedAbis = supportedAbis,
    protocolRange = protocolRange,
)
private fun capabilities(
    maximumContextBytes: Long = 1024L * 1024L,
    supportsReasoning: Boolean = true,
) = AiProviderHostTestFixtures.capabilities(
    maximumContextBytes = maximumContextBytes,
    supportsReasoning = supportsReasoning,
)
private fun environment(
    currentAbi: String? = "arm64-v8a",
    releaseBuild: Boolean = true,
    allowUserApprovedProviders: Boolean = false,
) = AiProviderHostTestFixtures.environment(
    currentAbi = currentAbi,
    releaseBuild = releaseBuild,
    allowUserApprovedProviders = allowUserApprovedProviders,
)
private fun unsupportedProtocolRange() = AiProviderHostTestFixtures.unsupportedProtocolRange()
