package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiProviderDiscoveryPolicyTest {

    @Test
    fun discoveryIsDisabledByDefault() {
        val result = AiProviderDiscoveryPolicy.evaluate(listOf(input()))

        assertTrue(result === AiProviderDiscoveryResult.Disabled)
    }

    @Test
    fun defaultOffDoesNotCallTheDiscoverySource() {
        var discoveryCalls = 0
        var inspectionCalls = 0
        val source = object : AiProviderDiscoverySource {
            override fun discoverExactAction(action: String): AiProviderDiscoverySourceResult {
                discoveryCalls += 1
                return AiProviderDiscoverySourceResult.QueryFailed()
            }

            override fun inspectExact(
                component: AiProviderComponent,
            ): AiProviderDiscoverySourceResult {
                inspectionCalls += 1
                return AiProviderDiscoverySourceResult.QueryFailed()
            }
        }

        val discovered = AiProviderDiscoveryPolicy.discoverExactAction(
            source,
            AiProviderProtocol.ACTION_PROVIDER,
        )
        val inspected = AiProviderDiscoveryPolicy.inspectExact(
            source,
            AiProviderComponent("example.ai.provider", "AiProviderService"),
        )

        assertTrue(discovered === AiProviderDiscoveryResult.Disabled)
        assertTrue(inspected === AiProviderDiscoveryResult.Disabled)
        assertEquals(0, discoveryCalls)
        assertEquals(0, inspectionCalls)
    }

    @Test
    fun wrongActionAndInvalidPackageIdentitiesAreExplicitlyRejected() {
        val cases = listOf(
            input(packageName = "wrong.action", declaredAction = "wrong.action") to
                AiProviderDiscoveryRejection.WRONG_ACTION,
            input(packageName = "blank.component", className = " ") to
                AiProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "bad.uid", packageUid = -1) to
                AiProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "bad.version", versionCode = 0L) to
                AiProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "bad.update", lastUpdateTime = -1L) to
                AiProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "empty.signers", signers = emptyList()) to
                AiProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "malformed.signer", signers = listOf("not-a-digest")) to
                AiProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "duplicate.signer", signers = listOf(SIGNER_A, SIGNER_A.uppercase())) to
                AiProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
        )

        cases.forEach { (value, expected) ->
            val completed = discover(listOf(value))
            val rejected = completed.evaluations.single() as AiProviderDiscoveryEvaluation.Rejected
            assertEquals(expected, rejected.rejection)
            assertTrue(rejected.input === value)
        }
    }

    @Test
    fun enabledDiscoveryRejectsWrongActionWithoutCallingTheSource() {
        var calls = 0
        val source = object : AiProviderDiscoverySource {
            override fun discoverExactAction(action: String): AiProviderDiscoverySourceResult {
                calls += 1
                return AiProviderDiscoverySourceResult.Completed(emptyList())
            }

            override fun inspectExact(
                component: AiProviderComponent,
            ): AiProviderDiscoverySourceResult = AiProviderDiscoverySourceResult.Completed(emptyList())
        }

        val result = AiProviderDiscoveryPolicy.discoverExactAction(
            source = source,
            action = "wrong.action",
            options = AiProviderDiscoveryOptions(enabled = true),
        )

        assertTrue(result === AiProviderDiscoveryResult.WrongAction)
        assertEquals(0, calls)
    }

    @Test
    fun discoveredSnapshotKeepsEverySignerAndAllLocalFacts() {
        val mutableSigners = arrayListOf(SIGNER_B.uppercase(), SIGNER_A)
        val value = input(
            exported = false,
            applicationEnabled = false,
            serviceEnabled = false,
            requiredPermission = "different.permission",
            signers = mutableSigners,
            officialSignerMatch = false,
            sameSignerAsHost = false,
            debuggable = true,
        )
        mutableSigners.clear()

        val discovered = discover(listOf(value)).evaluations.single()
            as AiProviderDiscoveryEvaluation.Discovered
        val snapshot = discovered.snapshot

        assertEquals(listOf(SIGNER_A, SIGNER_B), snapshot.signerSha256)
        assertEquals(value.packageName, snapshot.component.packageName)
        assertEquals(value.className, snapshot.component.className)
        assertEquals(value.declaredAction, snapshot.declaredAction)
        assertEquals(value.exported, snapshot.exported)
        assertEquals(value.applicationEnabled, snapshot.applicationEnabled)
        assertEquals(value.serviceEnabled, snapshot.serviceEnabled)
        assertEquals(value.requiredPermission, snapshot.requiredPermission)
        assertEquals(value.packageUid, snapshot.packageUid)
        assertEquals(value.versionCode, snapshot.versionCode)
        assertEquals(value.lastUpdateTime, snapshot.lastUpdateTime)
        assertEquals(value.officialSignerMatch, snapshot.officialSignerMatch)
        assertEquals(value.sameSignerAsHost, snapshot.sameSignerAsHost)
        assertEquals(value.debuggable, snapshot.debuggable)
        assertEquals(listOf(SIGNER_B.uppercase(), SIGNER_A), discovered.input.signerSha256)
        assertListIsImmutable(snapshot.signerSha256)
        assertListIsImmutable(discovered.input.signerSha256)
    }

    @Test
    fun evaluationsAreDeterministicallySortedWithoutRemovingDuplicates() {
        val zeta = input(packageName = "zeta.provider", packageUid = 10_003)
        val alpha = input(packageName = "alpha.provider", packageUid = 10_001)
        val duplicateAlpha = input(packageName = "alpha.provider", packageUid = 10_001)

        val completed = discover(listOf(zeta, duplicateAlpha, alpha))

        assertEquals(
            listOf("alpha.provider", "alpha.provider", "zeta.provider"),
            completed.evaluations.map { evaluation ->
                (evaluation as AiProviderDiscoveryEvaluation.Discovered).input.packageName
            },
        )
        assertEquals(3, completed.evaluations.size)
        assertTrue(completed.evaluations[0] is AiProviderDiscoveryEvaluation.Discovered)
        assertTrue(completed.evaluations[1] is AiProviderDiscoveryEvaluation.Discovered)
        assertListIsImmutable(completed.evaluations)
    }

    @Test
    fun deterministicTieBreakersApplyToAcceptedAndRejectedRows() {
        val newer = input(packageName = "same.provider", versionCode = 3L, lastUpdateTime = 30L)
        val older = input(packageName = "same.provider", versionCode = 2L, lastUpdateTime = 20L)
        val wrongAction = input(
            packageName = "same.provider",
            declaredAction = "z.wrong.action",
            versionCode = 9L,
        )

        val completed = discover(listOf(older, wrongAction, newer))

        assertEquals(
            listOf(
                AiProviderProtocol.ACTION_PROVIDER,
                AiProviderProtocol.ACTION_PROVIDER,
                "z.wrong.action",
            ),
            completed.evaluations.map(::evaluationInput).map { it.declaredAction },
        )
        assertEquals(
            listOf(3L, 2L, 9L),
            completed.evaluations.map(::evaluationInput).map { it.versionCode },
        )
        assertTrue(completed.evaluations.last() is AiProviderDiscoveryEvaluation.Rejected)
    }

    @Test
    fun sourceQueryAndIndividualReadFailuresRemainExplicit() {
        val wrongAction = AiProviderDiscoveryPolicy.evaluate(
            source = AiProviderDiscoverySourceResult.WrongAction,
            options = AiProviderDiscoveryOptions(enabled = true),
        )
        assertTrue(wrongAction === AiProviderDiscoveryResult.WrongAction)

        val queryFailed = AiProviderDiscoveryPolicy.evaluate(
            source = AiProviderDiscoverySourceResult.QueryFailed(),
            options = AiProviderDiscoveryOptions(enabled = true),
        )
        assertEquals(
            AiProviderDiscoverySourceFailure.QUERY_FAILED,
            (queryFailed as AiProviderDiscoveryResult.QueryFailed).failure,
        )

        val reference = AiProviderDiscoveryReference("broken.provider", "BrokenService")
        val completed = discover(
            AiProviderDiscoverySourceResult.Completed(
                listOf(
                    AiProviderDiscoverySourceEntry.Read(input(packageName = "valid.provider")),
                    AiProviderDiscoverySourceEntry.ReadFailed(reference),
                ),
            ),
        )
        val failed = completed.evaluations.first() as AiProviderDiscoveryEvaluation.SourceFailure
        assertEquals(reference, failed.reference)
        assertEquals(AiProviderDiscoverySourceFailure.SERVICE_READ_FAILED, failed.failure)
        assertTrue(completed.evaluations.last() is AiProviderDiscoveryEvaluation.Discovered)
    }

    @Test
    fun sourceAndExactInspectionShapesPreserveEmptyAndDuplicateRows() {
        val value = input(packageName = "duplicate.provider")
        val duplicateRows = AiProviderDiscoverySourceResult.Completed(
            listOf(
                AiProviderDiscoverySourceEntry.Read(value),
                AiProviderDiscoverySourceEntry.Read(value),
            ),
        )
        val source = object : AiProviderDiscoverySource {
            override fun discoverExactAction(action: String): AiProviderDiscoverySourceResult = duplicateRows

            override fun inspectExact(
                component: AiProviderComponent,
            ): AiProviderDiscoverySourceResult = if (component.packageName == value.packageName) {
                duplicateRows
            } else {
                AiProviderDiscoverySourceResult.Completed(emptyList())
            }
        }

        val enabled = AiProviderDiscoveryOptions(enabled = true)
        val discovered = AiProviderDiscoveryPolicy.discoverExactAction(
            source,
            AiProviderProtocol.ACTION_PROVIDER,
            enabled,
        ) as AiProviderDiscoveryResult.Completed
        val inspected = AiProviderDiscoveryPolicy.inspectExact(
            source,
            AiProviderComponent(value.packageName, value.className),
            enabled,
        ) as AiProviderDiscoveryResult.Completed
        val missing = AiProviderDiscoveryPolicy.inspectExact(
            source,
            AiProviderComponent("missing.provider", "MissingService"),
            enabled,
        ) as AiProviderDiscoveryResult.Completed

        assertEquals(2, discovered.evaluations.size)
        assertEquals(2, inspected.evaluations.size)
        assertTrue(missing.evaluations.isEmpty())
        assertListIsImmutable(duplicateRows.entries)
        assertListIsImmutable(inspected.evaluations)
    }

    private fun discover(
        inputs: Collection<AiProviderDiscoveryInput>,
    ): AiProviderDiscoveryResult.Completed = AiProviderDiscoveryPolicy.evaluate(
        inputs = inputs,
        options = AiProviderDiscoveryOptions(enabled = true),
    ) as AiProviderDiscoveryResult.Completed

    private fun discover(
        source: AiProviderDiscoverySourceResult,
    ): AiProviderDiscoveryResult.Completed = AiProviderDiscoveryPolicy.evaluate(
        source = source,
        options = AiProviderDiscoveryOptions(enabled = true),
    ) as AiProviderDiscoveryResult.Completed

    private fun <T> assertListIsImmutable(values: List<T>) {
        try {
            @Suppress("UNCHECKED_CAST")
            (values as MutableList<T>).clear()
            fail("Discovery result list must be immutable")
        } catch (_: UnsupportedOperationException) {
            // Expected.
        }
    }

    private companion object {
        val SIGNER_A = AiProviderHostTestFixtures.SIGNER_A
        val SIGNER_B = AiProviderHostTestFixtures.SIGNER_B
    }
}

private fun evaluationInput(
    evaluation: AiProviderDiscoveryEvaluation,
): AiProviderDiscoveryInput = when (evaluation) {
    is AiProviderDiscoveryEvaluation.Discovered -> evaluation.input
    is AiProviderDiscoveryEvaluation.Rejected -> evaluation.input
    is AiProviderDiscoveryEvaluation.SourceFailure -> error("Source failures do not carry complete inputs")
}

private fun input(
    packageName: String = "example.ai.provider",
    className: String = "AiProviderService",
    declaredAction: String = AiProviderProtocol.ACTION_PROVIDER,
    exported: Boolean = true,
    applicationEnabled: Boolean = true,
    serviceEnabled: Boolean = true,
    requiredPermission: String? = AiProviderProtocol.PLUGIN_PERMISSION,
    packageUid: Int = 10_001,
    versionCode: Long = 2L,
    lastUpdateTime: Long = 20L,
    signers: Collection<String> = listOf(AiProviderHostTestFixtures.SIGNER_A),
    officialSignerMatch: Boolean = true,
    sameSignerAsHost: Boolean = true,
    debuggable: Boolean = false,
) = AiProviderDiscoveryInput(
    packageName = packageName,
    className = className,
    declaredAction = declaredAction,
    exported = exported,
    applicationEnabled = applicationEnabled,
    serviceEnabled = serviceEnabled,
    requiredPermission = requiredPermission,
    packageUid = packageUid,
    versionCode = versionCode,
    lastUpdateTime = lastUpdateTime,
    signerSha256 = signers,
    officialSignerMatch = officialSignerMatch,
    sameSignerAsHost = sameSignerAsHost,
    debuggable = debuggable,
)
