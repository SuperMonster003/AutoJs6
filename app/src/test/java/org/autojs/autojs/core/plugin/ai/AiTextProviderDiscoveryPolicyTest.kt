package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.text.api.AiTextProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiTextProviderDiscoveryPolicyTest {

    @Test
    fun discoveryIsDisabledByDefault() {
        val result = AiTextProviderDiscoveryPolicy.evaluate(listOf(input()))

        assertTrue(result === AiTextProviderDiscoveryResult.Disabled)
    }

    @Test
    fun defaultOffDoesNotCallTheDiscoverySource() {
        var discoveryCalls = 0
        var inspectionCalls = 0
        val source = object : AiTextProviderDiscoverySource {
            override fun discoverExactAction(action: String): AiTextProviderDiscoverySourceResult {
                discoveryCalls += 1
                return AiTextProviderDiscoverySourceResult.QueryFailed()
            }

            override fun inspectExact(
                component: AiTextProviderComponent,
            ): AiTextProviderDiscoverySourceResult {
                inspectionCalls += 1
                return AiTextProviderDiscoverySourceResult.QueryFailed()
            }
        }

        val discovered = AiTextProviderDiscoveryPolicy.discoverExactAction(
            source,
            AiTextProtocol.ACTION_TEXT_PROVIDER,
        )
        val inspected = AiTextProviderDiscoveryPolicy.inspectExact(
            source,
            AiTextProviderComponent("example.ai.provider", "AiTextService"),
        )

        assertTrue(discovered === AiTextProviderDiscoveryResult.Disabled)
        assertTrue(inspected === AiTextProviderDiscoveryResult.Disabled)
        assertEquals(0, discoveryCalls)
        assertEquals(0, inspectionCalls)
    }

    @Test
    fun wrongActionAndInvalidPackageIdentitiesAreExplicitlyRejected() {
        val cases = listOf(
            input(packageName = "wrong.action", declaredAction = "wrong.action") to
                AiTextProviderDiscoveryRejection.WRONG_ACTION,
            input(packageName = "blank.component", className = " ") to
                AiTextProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "bad.uid", packageUid = -1) to
                AiTextProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "bad.version", versionCode = 0L) to
                AiTextProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "bad.update", lastUpdateTime = -1L) to
                AiTextProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "empty.signers", signers = emptyList()) to
                AiTextProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "malformed.signer", signers = listOf("not-a-digest")) to
                AiTextProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "duplicate.signer", signers = listOf(SIGNER_A, SIGNER_A.uppercase())) to
                AiTextProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
        )

        cases.forEach { (value, expected) ->
            val completed = discover(listOf(value))
            val rejected = completed.evaluations.single() as AiTextProviderDiscoveryEvaluation.Rejected
            assertEquals(expected, rejected.rejection)
            assertTrue(rejected.input === value)
        }
    }

    @Test
    fun enabledDiscoveryRejectsWrongActionWithoutCallingTheSource() {
        var calls = 0
        val source = object : AiTextProviderDiscoverySource {
            override fun discoverExactAction(action: String): AiTextProviderDiscoverySourceResult {
                calls += 1
                return AiTextProviderDiscoverySourceResult.Completed(emptyList())
            }

            override fun inspectExact(
                component: AiTextProviderComponent,
            ): AiTextProviderDiscoverySourceResult = AiTextProviderDiscoverySourceResult.Completed(emptyList())
        }

        val result = AiTextProviderDiscoveryPolicy.discoverExactAction(
            source = source,
            action = "wrong.action",
            options = AiTextProviderDiscoveryOptions(enabled = true),
        )

        assertTrue(result === AiTextProviderDiscoveryResult.WrongAction)
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
            as AiTextProviderDiscoveryEvaluation.Discovered
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
                (evaluation as AiTextProviderDiscoveryEvaluation.Discovered).input.packageName
            },
        )
        assertEquals(3, completed.evaluations.size)
        assertTrue(completed.evaluations[0] is AiTextProviderDiscoveryEvaluation.Discovered)
        assertTrue(completed.evaluations[1] is AiTextProviderDiscoveryEvaluation.Discovered)
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
                AiTextProtocol.ACTION_TEXT_PROVIDER,
                AiTextProtocol.ACTION_TEXT_PROVIDER,
                "z.wrong.action",
            ),
            completed.evaluations.map(::evaluationInput).map { it.declaredAction },
        )
        assertEquals(
            listOf(3L, 2L, 9L),
            completed.evaluations.map(::evaluationInput).map { it.versionCode },
        )
        assertTrue(completed.evaluations.last() is AiTextProviderDiscoveryEvaluation.Rejected)
    }

    @Test
    fun sourceQueryAndIndividualReadFailuresRemainExplicit() {
        val wrongAction = AiTextProviderDiscoveryPolicy.evaluate(
            source = AiTextProviderDiscoverySourceResult.WrongAction,
            options = AiTextProviderDiscoveryOptions(enabled = true),
        )
        assertTrue(wrongAction === AiTextProviderDiscoveryResult.WrongAction)

        val queryFailed = AiTextProviderDiscoveryPolicy.evaluate(
            source = AiTextProviderDiscoverySourceResult.QueryFailed(),
            options = AiTextProviderDiscoveryOptions(enabled = true),
        )
        assertEquals(
            AiTextProviderDiscoverySourceFailure.QUERY_FAILED,
            (queryFailed as AiTextProviderDiscoveryResult.QueryFailed).failure,
        )

        val reference = AiTextProviderDiscoveryReference("broken.provider", "BrokenService")
        val completed = discover(
            AiTextProviderDiscoverySourceResult.Completed(
                listOf(
                    AiTextProviderDiscoverySourceEntry.Read(input(packageName = "valid.provider")),
                    AiTextProviderDiscoverySourceEntry.ReadFailed(reference),
                ),
            ),
        )
        val failed = completed.evaluations.first() as AiTextProviderDiscoveryEvaluation.SourceFailure
        assertEquals(reference, failed.reference)
        assertEquals(AiTextProviderDiscoverySourceFailure.SERVICE_READ_FAILED, failed.failure)
        assertTrue(completed.evaluations.last() is AiTextProviderDiscoveryEvaluation.Discovered)
    }

    @Test
    fun sourceAndExactInspectionShapesPreserveEmptyAndDuplicateRows() {
        val value = input(packageName = "duplicate.provider")
        val duplicateRows = AiTextProviderDiscoverySourceResult.Completed(
            listOf(
                AiTextProviderDiscoverySourceEntry.Read(value),
                AiTextProviderDiscoverySourceEntry.Read(value),
            ),
        )
        val source = object : AiTextProviderDiscoverySource {
            override fun discoverExactAction(action: String): AiTextProviderDiscoverySourceResult = duplicateRows

            override fun inspectExact(
                component: AiTextProviderComponent,
            ): AiTextProviderDiscoverySourceResult = if (component.packageName == value.packageName) {
                duplicateRows
            } else {
                AiTextProviderDiscoverySourceResult.Completed(emptyList())
            }
        }

        val enabled = AiTextProviderDiscoveryOptions(enabled = true)
        val discovered = AiTextProviderDiscoveryPolicy.discoverExactAction(
            source,
            AiTextProtocol.ACTION_TEXT_PROVIDER,
            enabled,
        ) as AiTextProviderDiscoveryResult.Completed
        val inspected = AiTextProviderDiscoveryPolicy.inspectExact(
            source,
            AiTextProviderComponent(value.packageName, value.className),
            enabled,
        ) as AiTextProviderDiscoveryResult.Completed
        val missing = AiTextProviderDiscoveryPolicy.inspectExact(
            source,
            AiTextProviderComponent("missing.provider", "MissingService"),
            enabled,
        ) as AiTextProviderDiscoveryResult.Completed

        assertEquals(2, discovered.evaluations.size)
        assertEquals(2, inspected.evaluations.size)
        assertTrue(missing.evaluations.isEmpty())
        assertListIsImmutable(duplicateRows.entries)
        assertListIsImmutable(inspected.evaluations)
    }

    private fun discover(
        inputs: Collection<AiTextProviderDiscoveryInput>,
    ): AiTextProviderDiscoveryResult.Completed = AiTextProviderDiscoveryPolicy.evaluate(
        inputs = inputs,
        options = AiTextProviderDiscoveryOptions(enabled = true),
    ) as AiTextProviderDiscoveryResult.Completed

    private fun discover(
        source: AiTextProviderDiscoverySourceResult,
    ): AiTextProviderDiscoveryResult.Completed = AiTextProviderDiscoveryPolicy.evaluate(
        source = source,
        options = AiTextProviderDiscoveryOptions(enabled = true),
    ) as AiTextProviderDiscoveryResult.Completed

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
        val SIGNER_A = AiTextHostTestFixtures.SIGNER_A
        val SIGNER_B = AiTextHostTestFixtures.SIGNER_B
    }
}

private fun evaluationInput(
    evaluation: AiTextProviderDiscoveryEvaluation,
): AiTextProviderDiscoveryInput = when (evaluation) {
    is AiTextProviderDiscoveryEvaluation.Discovered -> evaluation.input
    is AiTextProviderDiscoveryEvaluation.Rejected -> evaluation.input
    is AiTextProviderDiscoveryEvaluation.SourceFailure -> error("Source failures do not carry complete inputs")
}

private fun input(
    packageName: String = "example.ai.provider",
    className: String = "AiTextService",
    declaredAction: String = AiTextProtocol.ACTION_TEXT_PROVIDER,
    exported: Boolean = true,
    applicationEnabled: Boolean = true,
    serviceEnabled: Boolean = true,
    requiredPermission: String? = AiTextProtocol.PLUGIN_PERMISSION,
    packageUid: Int = 10_001,
    versionCode: Long = 2L,
    lastUpdateTime: Long = 20L,
    signers: Collection<String> = listOf(AiTextHostTestFixtures.SIGNER_A),
    officialSignerMatch: Boolean = true,
    sameSignerAsHost: Boolean = true,
    debuggable: Boolean = false,
) = AiTextProviderDiscoveryInput(
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
