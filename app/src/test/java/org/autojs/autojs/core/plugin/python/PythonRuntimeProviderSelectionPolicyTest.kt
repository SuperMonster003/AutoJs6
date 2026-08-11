package org.autojs.autojs.core.plugin.python

import org.autojs.plugin.python.runtime.api.PythonProtocolVersion
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonRuntimeProviderSelectionPolicyTest {

    @Test
    fun selectionIsDisabledByDefault() {
        val result = PythonRuntimeProviderSelectionPolicy.select(
            candidates = listOf(candidate()),
            environment = environment(),
        )

        assertTrue(result === PythonRuntimeProviderSelectionResult.Disabled)
    }

    @Test
    fun discoveryAndTrustBoundariesFailClosed() {
        assertEquals(PythonRuntimeProviderCandidateRejection.WRONG_ACTION, rejection(candidate().copy(declaredAction = "wrong")))
        assertEquals(PythonRuntimeProviderCandidateRejection.NOT_EXPORTED, rejection(candidate().copy(exported = false)))
        assertEquals(PythonRuntimeProviderCandidateRejection.DISABLED, rejection(candidate().copy(serviceEnabled = false)))
        assertEquals(PythonRuntimeProviderCandidateRejection.WRONG_PERMISSION, rejection(candidate().copy(requiredPermission = "wrong")))
        assertEquals(PythonRuntimeProviderCandidateRejection.INVALID_PACKAGE_IDENTITY, rejection(candidate().copy(packageUid = -1)))
        assertEquals(PythonRuntimeProviderCandidateRejection.INVALID_PACKAGE_IDENTITY, rejection(candidate().copy(versionCode = 0)))
        assertEquals(PythonRuntimeProviderCandidateRejection.INVALID_PACKAGE_IDENTITY, rejection(candidate().copy(lastUpdateTime = 0)))
        assertEquals(PythonRuntimeProviderCandidateRejection.INVALID_PACKAGE_IDENTITY, rejection(candidate().copy(lastUpdateTime = -1)))
        assertEquals(PythonRuntimeProviderCandidateRejection.INVALID_PACKAGE_IDENTITY, rejection(candidate().copy(signerSha256 = emptyList())))
        assertEquals(
            PythonRuntimeProviderCandidateRejection.TRUST_REQUIRED,
            rejection(
                candidate().copy(
                    trustLevel = PythonRuntimeProviderTrustLevel.UNTRUSTED,
                    sameSignerAsHost = false,
                ),
            ),
        )
        assertEquals(
            PythonRuntimeProviderCandidateRejection.USER_APPROVAL_REQUIRED,
            rejection(
                candidate().copy(
                    trustLevel = PythonRuntimeProviderTrustLevel.USER_APPROVED,
                    sameSignerAsHost = false,
                ),
            ),
        )
        assertEquals(
            PythonRuntimeProviderCandidateRejection.DEBUG_TRUST_FORBIDDEN,
            rejection(
                candidate().copy(
                    trustLevel = PythonRuntimeProviderTrustLevel.DEBUG_ONLY,
                    sameSignerAsHost = false,
                ),
            ),
        )
    }

    @Test
    fun protocolCapabilityAbiAndHostVersionMustMatch() {
        assertEquals(
            PythonRuntimeProviderCandidateRejection.PROTOCOL_UNSUPPORTED,
            rejection(
                candidate().copy(
                    runtimeInfo = runtimeInfo(
                        protocolMin = PythonProtocolVersion(2, 0),
                        protocolMax = PythonProtocolVersion(2, 0),
                    ),
                ),
            ),
        )
        assertEquals(
            PythonRuntimeProviderCandidateRejection.CAPABILITY_MISMATCH,
            rejection(candidate(), requirement = PythonRuntimeCapabilityRequirement(minimumOutputBytes = 8_192L)),
        )
        assertEquals(
            PythonRuntimeProviderCandidateRejection.ABI_UNSUPPORTED,
            rejection(candidate(), environment = environment(currentAbi = "armeabi-v7a")),
        )
        assertEquals(
            PythonRuntimeProviderCandidateRejection.HOST_VERSION_UNSUPPORTED,
            rejection(candidate(), environment = environment(hostVersionCode = 7_001L)),
        )
    }

    @Test
    fun exactFullIdentityIsPinnedAndAmbiguityIsRejected() {
        val mutableSigners = arrayListOf(SIGNER_B, SIGNER_A)
        val alpha = candidate(packageName = "alpha.python", packageUid = 10_002, signers = mutableSigners)
        mutableSigners.clear()
        val zeta = candidate(packageName = "zeta.python")
        val ambiguous = select(listOf(zeta, alpha)) as PythonRuntimeProviderSelectionResult.Ambiguous

        assertEquals(listOf("alpha.python", "zeta.python"), ambiguous.candidates.map { it.component.packageName })
        val selected = select(listOf(zeta, alpha), preferred = alpha.component) as PythonRuntimeProviderSelectionResult.Selected
        assertEquals(listOf(SIGNER_A, SIGNER_B), selected.pinned.signerSha256)
        assertEquals(alpha.packageUid, selected.pinned.packageUid)
        assertEquals(alpha.versionCode, selected.pinned.versionCode)
        assertEquals(alpha.lastUpdateTime, selected.pinned.lastUpdateTime)
        assertEquals(PythonProtocolVersion(1, 0), selected.pinned.negotiatedProtocolVersion)
        assertEquals("arm64-v8a", selected.pinned.selectedAbi)

        val duplicate = select(listOf(alpha, alpha.copy()), preferred = alpha.component)
        assertTrue(duplicate is PythonRuntimeProviderSelectionResult.Ambiguous)
    }

    @Test
    fun dispatchRevalidationAndCallbackUidUseTheExactPin() {
        val original = candidate()
        val pinned = (select(listOf(original)) as PythonRuntimeProviderSelectionResult.Selected).pinned

        assertTrue(PythonRuntimeProviderSelectionPolicy.revalidate(pinned, original, environment()))
        assertFalse(PythonRuntimeProviderSelectionPolicy.revalidate(pinned, original.copy(packageUid = 10_002), environment()))
        assertFalse(PythonRuntimeProviderSelectionPolicy.revalidate(pinned, original.copy(versionCode = 8), environment()))
        assertFalse(PythonRuntimeProviderSelectionPolicy.revalidate(pinned, original.copy(lastUpdateTime = 124), environment()))
        assertFalse(PythonRuntimeProviderSelectionPolicy.revalidate(pinned, original.copy(signerSha256 = listOf(SIGNER_B)), environment()))
        assertTrue(PythonRuntimeProviderCallbackCallerPolicy.isExpectedUid(pinned, pinned.packageUid))
        assertFalse(PythonRuntimeProviderCallbackCallerPolicy.isExpectedUid(pinned, pinned.packageUid + 1))
        assertFalse(PythonRuntimeProviderCallbackCallerPolicy.isExpectedUid(pinned, -1))
    }

    @Test
    fun discoveryAndBindingPlansKeepActionScopesSeparate() {
        val pinned = (select(listOf(candidate())) as PythonRuntimeProviderSelectionResult.Selected).pinned

        assertEquals(PythonRuntimeContract.RUNTIME_ACTION, PythonRuntimeBindingPlanner.discovery().action)
        val binding = PythonRuntimeBindingPlanner.explicit(pinned)
        assertEquals(pinned.component, binding.component)
        assertEquals(null, binding.action)
    }

    private fun rejection(
        candidate: PythonRuntimeProviderCandidate,
        environment: PythonRuntimeProviderEnvironment = environment(),
        requirement: PythonRuntimeCapabilityRequirement = PythonRuntimeCapabilityRequirement(),
    ): PythonRuntimeProviderCandidateRejection {
        val result = select(listOf(candidate), environment = environment, requirement = requirement)
            as PythonRuntimeProviderSelectionResult.NoMatch
        return result.evaluations.single().rejection
    }

    private fun select(
        candidates: Collection<PythonRuntimeProviderCandidate>,
        environment: PythonRuntimeProviderEnvironment = environment(),
        requirement: PythonRuntimeCapabilityRequirement = PythonRuntimeCapabilityRequirement(),
        preferred: PythonRuntimeProviderComponent? = null,
    ): PythonRuntimeProviderSelectionResult = PythonRuntimeProviderSelectionPolicy.select(
        candidates = candidates,
        environment = environment,
        requirement = requirement,
        options = PythonRuntimeProviderSelectionOptions(enabled = true, preferredComponent = preferred),
    )

    private companion object {
        const val SIGNER_A = PythonRuntimeHostTestFixtures.SIGNER_A
        const val SIGNER_B = PythonRuntimeHostTestFixtures.SIGNER_B
    }
}

private fun candidate(
    packageName: String = "example.python.runtime",
    packageUid: Int = 10_001,
    signers: Collection<String> = listOf(PythonRuntimeHostTestFixtures.SIGNER_A),
) = PythonRuntimeHostTestFixtures.candidate(
    packageName = packageName,
    packageUid = packageUid,
    signers = signers,
)

private fun environment(
    hostVersionCode: Long = 6_000L,
    currentAbi: String = "arm64-v8a",
) = PythonRuntimeHostTestFixtures.environment(
    hostVersionCode = hostVersionCode,
    currentAbi = currentAbi,
)

private fun runtimeInfo(
    protocolMin: PythonProtocolVersion,
    protocolMax: PythonProtocolVersion,
) = PythonRuntimeHostTestFixtures.runtimeInfo(protocolMin = protocolMin, protocolMax = protocolMax)
