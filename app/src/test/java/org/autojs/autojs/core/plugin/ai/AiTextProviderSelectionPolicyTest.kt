package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.text.api.AiTextCapabilityId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiTextProviderSelectionPolicyTest {

    @Test
    fun selectionIsDisabledByDefault() {
        val result = AiTextProviderSelectionPolicy.select(
            candidates = listOf(AiTextHostTestFixtures.candidate()),
            environment = AiTextHostTestFixtures.environment(),
            hostCapabilities = AiTextHostTestFixtures.capabilities(),
            requirement = AiTextCapabilityRequirement(),
        )

        assertTrue(result === AiTextProviderSelectionResult.Disabled)
    }

    @Test
    fun discoveryBoundaryMismatchesAreRejectedBeforeNegotiation() {
        assertEquals(AiTextProviderCandidateRejection.WRONG_ACTION, rejection(candidate().copy(declaredAction = "wrong")))
        assertEquals(AiTextProviderCandidateRejection.NOT_EXPORTED, rejection(candidate().copy(exported = false)))
        assertEquals(
            AiTextProviderCandidateRejection.DISABLED,
            rejection(candidate().copy(applicationEnabled = false)),
        )
        assertEquals(
            AiTextProviderCandidateRejection.DISABLED,
            rejection(candidate().copy(serviceEnabled = false)),
        )
        assertEquals(
            AiTextProviderCandidateRejection.WRONG_PERMISSION,
            rejection(candidate().copy(requiredPermission = "wrong.permission")),
        )
        assertEquals(
            AiTextProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(packageUid = -1)),
        )
        assertEquals(
            AiTextProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(versionCode = 0)),
        )
        assertEquals(
            AiTextProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(lastUpdateTime = -1)),
        )
        assertEquals(
            AiTextProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(signerSha256 = emptyList())),
        )
        assertEquals(
            AiTextProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(signerSha256 = listOf("not-a-digest"))),
        )
        assertEquals(
            AiTextProviderCandidateRejection.INVALID_PACKAGE_IDENTITY,
            rejection(candidate().copy(signerSha256 = listOf(SIGNER_A, SIGNER_A.uppercase()))),
        )
    }

    @Test
    fun trustRequiresAnExplicitlyAcceptedSource() {
        assertEquals(
            AiTextProviderCandidateRejection.TRUST_REQUIRED,
            rejection(candidate().copy(trustLevel = AiTextProviderTrustLevel.UNTRUSTED)),
        )
        assertEquals(
            AiTextProviderCandidateRejection.USER_APPROVAL_REQUIRED,
            rejection(candidate().copy(trustLevel = AiTextProviderTrustLevel.USER_APPROVED)),
        )
        assertEquals(
            AiTextProviderCandidateRejection.DEBUG_TRUST_FORBIDDEN,
            rejection(candidate().copy(trustLevel = AiTextProviderTrustLevel.DEBUG_ONLY)),
        )
        assertSelected(
            candidate().copy(trustLevel = AiTextProviderTrustLevel.USER_APPROVED),
            environment(allowUserApprovedProviders = true),
        )
        assertSelected(
            candidate().copy(trustLevel = AiTextProviderTrustLevel.DEBUG_ONLY),
            environment(releaseBuild = false),
        )
        assertSelected(
            candidate().copy(
                trustLevel = AiTextProviderTrustLevel.UNTRUSTED,
                sameSignerAsHost = true,
            ),
        )
    }

    @Test
    fun hostVersionAbiAndProtocolMustOverlap() {
        assertEquals(
            AiTextProviderCandidateRejection.HOST_VERSION_UNSUPPORTED,
            rejection(candidate().copy(providerInfo = providerInfo(minimumHostVersionCode = 7_000))),
        )
        assertEquals(
            AiTextProviderCandidateRejection.HOST_VERSION_UNSUPPORTED,
            rejection(candidate().copy(providerInfo = providerInfo(maximumHostVersionCode = 6_000))),
        )
        assertEquals(
            AiTextProviderCandidateRejection.ABI_UNSUPPORTED,
            rejection(candidate().copy(providerInfo = providerInfo(supportedAbis = listOf("x86_64")))),
        )
        assertEquals(
            AiTextProviderCandidateRejection.ABI_UNSUPPORTED,
            rejection(
                candidate().copy(providerInfo = providerInfo(supportedAbis = listOf("arm64-v8a"))),
                environment = environment(currentAbi = null),
            ),
        )
        assertEquals(
            AiTextProviderCandidateRejection.PROTOCOL_UNSUPPORTED,
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
            requirement = AiTextCapabilityRequirement(minimumContextBytes = 256L * 1024L),
        ) as AiTextProviderSelectionResult.Selected

        assertEquals(512L * 1024L, selected.pinned.negotiatedCapabilities.maximumContextBytes)
        assertFalse(selected.pinned.negotiatedCapabilities.supportsReasoning)
        assertEquals(
            AiTextProviderCandidateRejection.CAPABILITY_MISMATCH,
            rejection(
                provider,
                hostCapabilities = host,
                requirement = AiTextCapabilityRequirement(minimumContextBytes = 700L * 1024L),
            ),
        )
        assertEquals(
            AiTextProviderCandidateRejection.CAPABILITY_MISMATCH,
            rejection(
                provider,
                hostCapabilities = host,
                requirement = AiTextCapabilityRequirement(
                    requiredCapabilityIds = setOf(AiTextCapabilityId.REASONING),
                ),
            ),
        )
    }

    @Test
    fun invalidCapabilityRequirementIsRejectedAtConstruction() {
        try {
            AiTextCapabilityRequirement(requiredCapabilityIds = setOf("unknown-capability"))
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
        val selected = select(listOf(original)) as AiTextProviderSelectionResult.Selected
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
        val ambiguous = select(listOf(zeta, alpha)) as AiTextProviderSelectionResult.Ambiguous

        assertEquals(
            listOf("alpha.provider", "zeta.provider"),
            ambiguous.candidates.map { it.component.packageName },
        )

        val selected = select(
            listOf(zeta, alpha),
            preferred = zeta.component,
        ) as AiTextProviderSelectionResult.Selected
        assertEquals("zeta.provider", selected.pinned.component.packageName)
    }

    @Test
    fun duplicateDiscoveryRowsRemainAmbiguousEvenWithPreference() {
        val value = candidate()
        val result = select(listOf(value, value.copy()), preferred = value.component)

        assertTrue(result is AiTextProviderSelectionResult.Ambiguous)
        assertEquals(2, (result as AiTextProviderSelectionResult.Ambiguous).candidates.size)
    }

    @Test
    fun revalidationRejectsEveryPinnedIdentityDrift() {
        val original = candidate()
        val pinned = (select(listOf(original)) as AiTextProviderSelectionResult.Selected).pinned

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
        val pinned = (select(listOf(candidate())) as AiTextProviderSelectionResult.Selected).pinned

        assertTrue(AiTextProviderCallbackCallerPolicy.isExpectedUid(pinned, pinned.packageUid))
        assertFalse(AiTextProviderCallbackCallerPolicy.isExpectedUid(pinned, pinned.packageUid + 1))
        assertFalse(AiTextProviderCallbackCallerPolicy.isExpectedUid(pinned, -1))
    }

    private fun rejection(
        candidate: AiTextProviderCandidate,
        environment: AiTextProviderEnvironment = environment(),
        hostCapabilities: org.autojs.plugin.ai.text.api.AiTextCapabilities = capabilities(),
        requirement: AiTextCapabilityRequirement = AiTextCapabilityRequirement(),
    ): AiTextProviderCandidateRejection {
        val result = select(
            candidates = listOf(candidate),
            environment = environment,
            hostCapabilities = hostCapabilities,
            requirement = requirement,
        ) as AiTextProviderSelectionResult.NoMatch
        return result.evaluations.single().rejection
    }

    private fun assertSelected(
        candidate: AiTextProviderCandidate,
        environment: AiTextProviderEnvironment = environment(),
    ) {
        assertTrue(select(listOf(candidate), environment = environment) is AiTextProviderSelectionResult.Selected)
    }

    private fun select(
        candidates: Collection<AiTextProviderCandidate>,
        environment: AiTextProviderEnvironment = environment(),
        hostCapabilities: org.autojs.plugin.ai.text.api.AiTextCapabilities = capabilities(),
        requirement: AiTextCapabilityRequirement = AiTextCapabilityRequirement(),
        preferred: AiTextProviderComponent? = null,
    ): AiTextProviderSelectionResult = AiTextProviderSelectionPolicy.select(
        candidates = candidates,
        environment = environment,
        hostCapabilities = hostCapabilities,
        requirement = requirement,
        options = AiTextProviderSelectionOptions(enabled = true, preferredComponent = preferred),
    )

    private fun revalidate(
        pinned: AiTextProviderPinnedIdentity,
        current: AiTextProviderCandidate?,
    ): Boolean = AiTextProviderSelectionPolicy.revalidate(
        pinned = pinned,
        current = current,
        environment = environment(),
        hostCapabilities = capabilities(),
        requirement = AiTextCapabilityRequirement(),
    )

    private companion object {
        val SIGNER_A = AiTextHostTestFixtures.SIGNER_A
        val SIGNER_B = AiTextHostTestFixtures.SIGNER_B
    }
}

private fun candidate(
    packageName: String = "example.ai.provider",
    packageUid: Int = 10_001,
): AiTextProviderCandidate = AiTextHostTestFixtures.candidate(packageName = packageName, packageUid = packageUid)
private fun providerInfo(
    minimumHostVersionCode: Long = 0L,
    maximumHostVersionCode: Long? = null,
    supportedAbis: List<String> = emptyList(),
    protocolRange: org.autojs.plugin.ai.common.api.AiProtocolRange = org.autojs.plugin.ai.text.api.AiTextProtocol.HOST_PROTOCOL_RANGE,
) = AiTextHostTestFixtures.providerInfo(
    minimumHostVersionCode = minimumHostVersionCode,
    maximumHostVersionCode = maximumHostVersionCode,
    supportedAbis = supportedAbis,
    protocolRange = protocolRange,
)
private fun capabilities(
    maximumContextBytes: Long = 1024L * 1024L,
    supportsReasoning: Boolean = true,
) = AiTextHostTestFixtures.capabilities(
    maximumContextBytes = maximumContextBytes,
    supportsReasoning = supportsReasoning,
)
private fun environment(
    currentAbi: String? = "arm64-v8a",
    releaseBuild: Boolean = true,
    allowUserApprovedProviders: Boolean = false,
) = AiTextHostTestFixtures.environment(
    currentAbi = currentAbi,
    releaseBuild = releaseBuild,
    allowUserApprovedProviders = allowUserApprovedProviders,
)
private fun unsupportedProtocolRange() = AiTextHostTestFixtures.unsupportedProtocolRange()
