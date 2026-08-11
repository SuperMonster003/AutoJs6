package org.autojs.autojs.core.plugin.dex

import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DexCompilerExperimentalPolicyTest {

    @Test
    fun acceptsOnlyOfficialOrSameSignerCandidates() {
        assertNull(DexCompilerExperimentalPolicy.rejection(candidate(trust = DexCompilerTrustLevel.OFFICIAL), true))
        assertNull(
            DexCompilerExperimentalPolicy.rejection(
                candidate(trust = DexCompilerTrustLevel.REQUIRED, sameSigner = true),
                true,
            ),
        )
        assertEquals(
            DexCompilerCandidateRejection.RELEASE_TRUST_FORBIDDEN,
            DexCompilerExperimentalPolicy.rejection(candidate(trust = DexCompilerTrustLevel.TRUSTED), true),
        )
        assertEquals(
            DexCompilerCandidateRejection.RELEASE_TRUST_FORBIDDEN,
            DexCompilerExperimentalPolicy.rejection(candidate(trust = DexCompilerTrustLevel.USER_GRANTED), true),
        )
        assertEquals(
            DexCompilerCandidateRejection.TRUST_REQUIRED,
            DexCompilerExperimentalPolicy.rejection(candidate(trust = DexCompilerTrustLevel.TRUSTED), false),
        )
    }

    @Test
    fun rejectsEveryDiscoveryBoundaryMismatch() {
        assertEquals(
            DexCompilerCandidateRejection.WRONG_ACTION,
            DexCompilerExperimentalPolicy.rejection(candidate(action = "wrong"), true),
        )
        assertEquals(
            DexCompilerCandidateRejection.NOT_EXPORTED,
            DexCompilerExperimentalPolicy.rejection(candidate(exported = false), true),
        )
        assertEquals(
            DexCompilerCandidateRejection.DISABLED,
            DexCompilerExperimentalPolicy.rejection(candidate(applicationEnabled = false), true),
        )
        assertEquals(
            DexCompilerCandidateRejection.DISABLED,
            DexCompilerExperimentalPolicy.rejection(candidate(serviceEnabled = false), true),
        )
        assertEquals(
            DexCompilerCandidateRejection.WRONG_PERMISSION,
            DexCompilerExperimentalPolicy.rejection(candidate(permission = "wrong.permission"), true),
        )
        assertEquals(
            DexCompilerCandidateRejection.INVALID_IDENTITY,
            DexCompilerExperimentalPolicy.rejection(candidate(signers = emptySet()), true),
        )
    }

    @Test
    fun selectionPinsExactComponentAndImmutablePackageIdentity() {
        val older = candidate(versionCode = 6, className = "Old")
        val selected = candidate(versionCode = 7, className = "Current")
        val pinned = DexCompilerExperimentalPolicy.selectAndPin(listOf(older, selected), releaseBuild = true)

        assertNotNull(pinned)
        assertEquals(DexCompilerComponent(PACKAGE, "Current"), pinned!!.component)
        assertEquals(10_000, pinned.packageUid)
        assertEquals(7, pinned.versionCode)
        assertEquals(listOf(SIGNER), pinned.signerSha256)
        assertTrue(DexCompilerExperimentalPolicy.revalidate(pinned, selected, releaseBuild = true))
        assertFalse(
            DexCompilerExperimentalPolicy.revalidate(
                pinned,
                selected.copy(lastUpdateTime = selected.lastUpdateTime + 1),
                releaseBuild = true,
            ),
        )
        assertFalse(
            DexCompilerExperimentalPolicy.revalidate(
                pinned,
                selected.copy(packageUid = selected.packageUid + 1),
                releaseBuild = true,
            ),
        )
        assertFalse(
            DexCompilerExperimentalPolicy.revalidate(
                pinned,
                selected.copy(signerSha256 = setOf("bb".repeat(32))),
                releaseBuild = true,
            ),
        )
    }

    @Test
    fun mainThreadSynchronousEntryFailsOpenButAsyncEntryDoesNot() {
        assertEquals(
            DexCompilerEntryDecision.FAIL_OPEN_TO_LOCAL,
            DexCompilerExperimentalEntryGate.decide(DexCompilerInvocationKind.SYNCHRONOUS, true),
        )
        assertEquals(
            DexCompilerEntryDecision.RUN_EXPERIMENT,
            DexCompilerExperimentalEntryGate.decide(DexCompilerInvocationKind.ASYNCHRONOUS, true),
        )
        assertEquals(
            DexCompilerEntryDecision.RUN_EXPERIMENT,
            DexCompilerExperimentalEntryGate.decide(DexCompilerInvocationKind.SYNCHRONOUS, false),
        )
    }

    @Test
    fun recoveryDeletesOnlyKnownTransactionNamesAndNeverPublishedCache() {
        assertTrue(DexCompilerArtifactRecoveryPolicy.isTransientArtifact("input-123e4567-e89b-12d3-a456-426614174000.jar"))
        assertTrue(DexCompilerArtifactRecoveryPolicy.isTransientArtifact("input-123e4567-e89b-12d3-a456-426614174000.bundle"))
        assertTrue(DexCompilerArtifactRecoveryPolicy.isTransientArtifact("output-123e4567-e89b-12d3-a456-426614174000.partial"))
        assertTrue(DexCompilerArtifactRecoveryPolicy.isTransientArtifact("verified-123e4567-e89b-12d3-a456-426614174000.partial"))
        assertFalse(DexCompilerArtifactRecoveryPolicy.isTransientArtifact("aa.dex.zip"))
        assertFalse(DexCompilerArtifactRecoveryPolicy.isTransientArtifact("output-user-file.partial"))
    }

    @Test
    fun callbackCallerMustMatchPinnedPackageUid() {
        assertTrue(DexCompilerTransportCallerPolicy.isExpectedUid(10_000, 10_000))
        assertFalse(DexCompilerTransportCallerPolicy.isExpectedUid(10_000, 10_001))
        assertFalse(DexCompilerTransportCallerPolicy.isExpectedUid(-1, -1))
    }

    private fun candidate(
        action: String = DexCompilerContract.SERVICE_ACTION,
        exported: Boolean = true,
        applicationEnabled: Boolean = true,
        serviceEnabled: Boolean = true,
        permission: String? = DexCompilerContract.PLUGIN_PERMISSION,
        packageUid: Int = 10_000,
        versionCode: Long = 7,
        className: String = "CompilerService",
        signers: Set<String> = setOf(SIGNER),
        trust: DexCompilerTrustLevel = DexCompilerTrustLevel.OFFICIAL,
        sameSigner: Boolean = false,
    ) = DexCompilerServiceCandidate(
        component = DexCompilerComponent(PACKAGE, className),
        declaredAction = action,
        exported = exported,
        applicationEnabled = applicationEnabled,
        serviceEnabled = serviceEnabled,
        requiredPermission = permission,
        packageUid = packageUid,
        versionCode = versionCode,
        lastUpdateTime = 123,
        signerSha256 = signers,
        trustLevel = trust,
        sameSignerAsHost = sameSigner,
    )

    private companion object {
        const val PACKAGE = "example.dex.compiler"
        val SIGNER = "aa".repeat(32)
    }
}
