package org.autojs.autojs.core.plugin.dex

import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DexCompilerOptInPolicyTest {

    @Test
    fun defaultStateIsHardOffWithoutCallingDiscovery() {
        var discoveryCalls = 0

        val decision = decide { discoveryCalls += 1; listOf(candidate()) }

        assertTrue(decision === DexCompilerOptInDecision.Disabled)
        assertEquals(0, discoveryCalls)
    }

    @Test
    fun enabledStateWithoutExplicitComponentNeverDiscoversOrAutoSelects() {
        var discoveryCalls = 0

        val decision = decide(DexCompilerOptInOptions(enabled = true)) {
            discoveryCalls += 1
            listOf(candidate())
        }

        assertTrue(decision === DexCompilerOptInDecision.NoSelection)
        assertEquals(0, discoveryCalls)
    }

    @Test
    fun onlyTheExactExplicitComponentCanBeSelected() {
        val selected = DexCompilerComponent(PACKAGE, "SelectedService")
        val other = candidate(className = "OtherService", versionCode = 99)
        val exact = candidate(className = selected.className, versionCode = 7)

        val decision = decide(enabled(selected)) { listOf(other, exact) }

        decision as DexCompilerOptInDecision.Selected
        assertEquals(selected, decision.identity.component)
        assertEquals(7L, decision.identity.versionCode)
    }

    @Test
    fun aMissingExactComponentIsObservablyUnavailable() {
        val selected = DexCompilerComponent(PACKAGE, "MissingService")

        val decision = decide(enabled(selected)) { listOf(candidate(className = "OtherService")) }

        assertEquals(
            DexCompilerOptInDecision.Unavailable(
                selected,
                DexCompilerOptInUnavailableReason.NO_EXACT_MATCH,
            ),
            decision,
        )
    }

    @Test
    fun discoveryFailureIsObservablyUnavailable() {
        val selected = DexCompilerComponent(PACKAGE, "SelectedService")

        val decision = decide(enabled(selected)) { throw IllegalStateException("query failed") }

        assertEquals(
            DexCompilerOptInDecision.Unavailable(
                selected,
                DexCompilerOptInUnavailableReason.DISCOVERY_FAILED,
            ),
            decision,
        )
    }

    @Test
    fun exactCandidateMustPassTheExistingCandidatePolicy() {
        val selected = DexCompilerComponent(PACKAGE, "SelectedService")

        val decision = decide(enabled(selected)) {
            listOf(candidate(className = selected.className, permission = "wrong.permission"))
        }

        assertEquals(
            DexCompilerOptInDecision.Rejected(
                selected,
                DexCompilerOptInRejectionReason.CANDIDATE_POLICY,
                DexCompilerCandidateRejection.WRONG_PERMISSION,
            ),
            decision,
        )
    }

    @Test
    fun sameHostSignerIsRequiredEvenForAnOfficialCandidate() {
        val selected = DexCompilerComponent(PACKAGE, "SelectedService")

        val decision = decide(enabled(selected)) {
            listOf(candidate(className = selected.className, sameSignerAsHost = false))
        }

        assertEquals(
            DexCompilerOptInDecision.Rejected(
                selected,
                DexCompilerOptInRejectionReason.HOST_SIGNER_MISMATCH,
            ),
            decision,
        )
    }

    @Test
    fun duplicateExactDiscoveryRowsFailClosed() {
        val selected = DexCompilerComponent(PACKAGE, "SelectedService")
        val exact = candidate(className = selected.className)

        val decision = decide(enabled(selected)) { listOf(exact, exact) }

        assertEquals(
            DexCompilerOptInDecision.Rejected(
                selected,
                DexCompilerOptInRejectionReason.MULTIPLE_EXACT_MATCHES,
            ),
            decision,
        )
    }

    @Test
    fun componentCodecRoundTripsExactNamesDeterministically() {
        val component = DexCompilerComponent(
            "io.github.supermonster003.autojs6.plugin.dexcompiler",
            "io.github.supermonster003.autojs6.plugin.dexcompiler.DexCompilerService\$V1",
        )

        val encoded = DexCompilerComponentCodec.encode(component)

        assertEquals(component, DexCompilerComponentCodec.decodeOrNull(encoded))
        assertEquals(encoded, DexCompilerComponentCodec.encode(component))
    }

    @Test
    fun componentCodecRejectsMalformedNonCanonicalAndUnsafeValues() {
        listOf<String?>(
            null,
            "",
            "v2:1:1:ab",
            "v1:01:1:ab",
            "v1:1:01:ab",
            "v1:1:1:a",
            "v1:1:1:abc",
            "v1:x:1:ab",
            "v1:1:1:a\n",
            "v1:1:1: a",
        ).forEach { encoded ->
            assertNull("Unexpectedly decoded $encoded", DexCompilerComponentCodec.decodeOrNull(encoded))
        }

        try {
            DexCompilerComponentCodec.encode(DexCompilerComponent(" package", "Service"))
            fail("Unsafe component must not be encoded")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun decide(
        options: DexCompilerOptInOptions = DexCompilerOptInOptions(),
        source: () -> Collection<DexCompilerServiceCandidate>,
    ): DexCompilerOptInDecision = DexCompilerOptInPolicy.decide(
        options = options,
        releaseBuild = true,
        discoverCandidates = source,
    )

    private fun enabled(component: DexCompilerComponent) = DexCompilerOptInOptions(
        enabled = true,
        selectedComponent = component,
    )

    private fun candidate(
        className: String = "SelectedService",
        versionCode: Long = 7L,
        permission: String? = DexCompilerContract.PLUGIN_PERMISSION,
        sameSignerAsHost: Boolean = true,
    ) = DexCompilerServiceCandidate(
        component = DexCompilerComponent(PACKAGE, className),
        declaredAction = DexCompilerContract.SERVICE_ACTION,
        exported = true,
        applicationEnabled = true,
        serviceEnabled = true,
        requiredPermission = permission,
        packageUid = 10_000,
        versionCode = versionCode,
        lastUpdateTime = 123L,
        signerSha256 = setOf(SIGNER),
        trustLevel = DexCompilerTrustLevel.OFFICIAL,
        sameSignerAsHost = sameSignerAsHost,
    )

    private companion object {
        const val PACKAGE = "example.dex.compiler"
        val SIGNER = "aa".repeat(32)
    }
}
