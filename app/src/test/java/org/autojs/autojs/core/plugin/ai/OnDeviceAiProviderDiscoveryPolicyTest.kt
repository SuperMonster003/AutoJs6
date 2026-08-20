package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ondeviceai.api.OnDeviceAiProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OnDeviceAiProviderDiscoveryPolicyTest {

    @Test
    fun discoveryIsDisabledByDefault() {
        val result = OnDeviceAiProviderDiscoveryPolicy.evaluate(listOf(input()))

        assertTrue(result === OnDeviceAiProviderDiscoveryResult.Disabled)
    }

    @Test
    fun defaultOffDoesNotCallTheDiscoverySource() {
        var discoveryCalls = 0
        var inspectionCalls = 0
        val source = object : OnDeviceAiProviderDiscoverySource {
            override fun discoverExactAction(action: String): OnDeviceAiProviderDiscoverySourceResult {
                discoveryCalls += 1
                return OnDeviceAiProviderDiscoverySourceResult.QueryFailed()
            }

            override fun inspectExact(
                component: OnDeviceAiProviderComponent,
            ): OnDeviceAiProviderDiscoverySourceResult {
                inspectionCalls += 1
                return OnDeviceAiProviderDiscoverySourceResult.QueryFailed()
            }
        }

        val discovered = OnDeviceAiProviderDiscoveryPolicy.discoverExactAction(
            source,
            OnDeviceAiProtocol.ACTION_PROVIDER,
        )
        val inspected = OnDeviceAiProviderDiscoveryPolicy.inspectExact(
            source,
            OnDeviceAiProviderComponent("example.ai.provider", "OnDeviceAiService"),
        )

        assertTrue(discovered === OnDeviceAiProviderDiscoveryResult.Disabled)
        assertTrue(inspected === OnDeviceAiProviderDiscoveryResult.Disabled)
        assertEquals(0, discoveryCalls)
        assertEquals(0, inspectionCalls)
    }

    @Test
    fun wrongActionAndInvalidPackageIdentitiesAreExplicitlyRejected() {
        val cases = listOf(
            input(packageName = "wrong.action", declaredAction = "wrong.action") to
                OnDeviceAiProviderDiscoveryRejection.WRONG_ACTION,
            input(packageName = "blank.component", className = " ") to
                OnDeviceAiProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "bad.uid", packageUid = -1) to
                OnDeviceAiProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "bad.version", versionCode = 0L) to
                OnDeviceAiProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "bad.update", lastUpdateTime = -1L) to
                OnDeviceAiProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "empty.signers", signers = emptyList()) to
                OnDeviceAiProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "malformed.signer", signers = listOf("not-a-digest")) to
                OnDeviceAiProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            input(packageName = "duplicate.signer", signers = listOf(SIGNER_A, SIGNER_A.uppercase())) to
                OnDeviceAiProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
        )

        cases.forEach { (value, expected) ->
            val completed = discover(listOf(value))
            val rejected = completed.evaluations.single() as OnDeviceAiProviderDiscoveryEvaluation.Rejected
            assertEquals(expected, rejected.rejection)
            assertTrue(rejected.input === value)
        }
    }

    @Test
    fun enabledDiscoveryRejectsWrongActionWithoutCallingTheSource() {
        var calls = 0
        val source = object : OnDeviceAiProviderDiscoverySource {
            override fun discoverExactAction(action: String): OnDeviceAiProviderDiscoverySourceResult {
                calls += 1
                return OnDeviceAiProviderDiscoverySourceResult.Completed(emptyList())
            }

            override fun inspectExact(
                component: OnDeviceAiProviderComponent,
            ): OnDeviceAiProviderDiscoverySourceResult = OnDeviceAiProviderDiscoverySourceResult.Completed(emptyList())
        }

        val result = OnDeviceAiProviderDiscoveryPolicy.discoverExactAction(
            source = source,
            action = "wrong.action",
            options = OnDeviceAiProviderDiscoveryOptions(enabled = true),
        )

        assertTrue(result === OnDeviceAiProviderDiscoveryResult.WrongAction)
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
            as OnDeviceAiProviderDiscoveryEvaluation.Discovered
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
                (evaluation as OnDeviceAiProviderDiscoveryEvaluation.Discovered).input.packageName
            },
        )
        assertEquals(3, completed.evaluations.size)
        assertTrue(completed.evaluations[0] is OnDeviceAiProviderDiscoveryEvaluation.Discovered)
        assertTrue(completed.evaluations[1] is OnDeviceAiProviderDiscoveryEvaluation.Discovered)
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
                OnDeviceAiProtocol.ACTION_PROVIDER,
                OnDeviceAiProtocol.ACTION_PROVIDER,
                "z.wrong.action",
            ),
            completed.evaluations.map(::evaluationInput).map { it.declaredAction },
        )
        assertEquals(
            listOf(3L, 2L, 9L),
            completed.evaluations.map(::evaluationInput).map { it.versionCode },
        )
        assertTrue(completed.evaluations.last() is OnDeviceAiProviderDiscoveryEvaluation.Rejected)
    }

    @Test
    fun sourceQueryAndIndividualReadFailuresRemainExplicit() {
        val wrongAction = OnDeviceAiProviderDiscoveryPolicy.evaluate(
            source = OnDeviceAiProviderDiscoverySourceResult.WrongAction,
            options = OnDeviceAiProviderDiscoveryOptions(enabled = true),
        )
        assertTrue(wrongAction === OnDeviceAiProviderDiscoveryResult.WrongAction)

        val queryFailed = OnDeviceAiProviderDiscoveryPolicy.evaluate(
            source = OnDeviceAiProviderDiscoverySourceResult.QueryFailed(),
            options = OnDeviceAiProviderDiscoveryOptions(enabled = true),
        )
        assertEquals(
            OnDeviceAiProviderDiscoverySourceFailure.QUERY_FAILED,
            (queryFailed as OnDeviceAiProviderDiscoveryResult.QueryFailed).failure,
        )

        val reference = OnDeviceAiProviderDiscoveryReference("broken.provider", "BrokenService")
        val completed = discover(
            OnDeviceAiProviderDiscoverySourceResult.Completed(
                listOf(
                    OnDeviceAiProviderDiscoverySourceEntry.Read(input(packageName = "valid.provider")),
                    OnDeviceAiProviderDiscoverySourceEntry.ReadFailed(reference),
                ),
            ),
        )
        val failed = completed.evaluations.first() as OnDeviceAiProviderDiscoveryEvaluation.SourceFailure
        assertEquals(reference, failed.reference)
        assertEquals(OnDeviceAiProviderDiscoverySourceFailure.SERVICE_READ_FAILED, failed.failure)
        assertTrue(completed.evaluations.last() is OnDeviceAiProviderDiscoveryEvaluation.Discovered)
    }

    @Test
    fun sourceAndExactInspectionShapesPreserveEmptyAndDuplicateRows() {
        val value = input(packageName = "duplicate.provider")
        val duplicateRows = OnDeviceAiProviderDiscoverySourceResult.Completed(
            listOf(
                OnDeviceAiProviderDiscoverySourceEntry.Read(value),
                OnDeviceAiProviderDiscoverySourceEntry.Read(value),
            ),
        )
        val source = object : OnDeviceAiProviderDiscoverySource {
            override fun discoverExactAction(action: String): OnDeviceAiProviderDiscoverySourceResult = duplicateRows

            override fun inspectExact(
                component: OnDeviceAiProviderComponent,
            ): OnDeviceAiProviderDiscoverySourceResult = if (component.packageName == value.packageName) {
                duplicateRows
            } else {
                OnDeviceAiProviderDiscoverySourceResult.Completed(emptyList())
            }
        }

        val enabled = OnDeviceAiProviderDiscoveryOptions(enabled = true)
        val discovered = OnDeviceAiProviderDiscoveryPolicy.discoverExactAction(
            source,
            OnDeviceAiProtocol.ACTION_PROVIDER,
            enabled,
        ) as OnDeviceAiProviderDiscoveryResult.Completed
        val inspected = OnDeviceAiProviderDiscoveryPolicy.inspectExact(
            source,
            OnDeviceAiProviderComponent(value.packageName, value.className),
            enabled,
        ) as OnDeviceAiProviderDiscoveryResult.Completed
        val missing = OnDeviceAiProviderDiscoveryPolicy.inspectExact(
            source,
            OnDeviceAiProviderComponent("missing.provider", "MissingService"),
            enabled,
        ) as OnDeviceAiProviderDiscoveryResult.Completed

        assertEquals(2, discovered.evaluations.size)
        assertEquals(2, inspected.evaluations.size)
        assertTrue(missing.evaluations.isEmpty())
        assertListIsImmutable(duplicateRows.entries)
        assertListIsImmutable(inspected.evaluations)
    }

    private fun discover(
        inputs: Collection<OnDeviceAiProviderDiscoveryInput>,
    ): OnDeviceAiProviderDiscoveryResult.Completed = OnDeviceAiProviderDiscoveryPolicy.evaluate(
        inputs = inputs,
        options = OnDeviceAiProviderDiscoveryOptions(enabled = true),
    ) as OnDeviceAiProviderDiscoveryResult.Completed

    private fun discover(
        source: OnDeviceAiProviderDiscoverySourceResult,
    ): OnDeviceAiProviderDiscoveryResult.Completed = OnDeviceAiProviderDiscoveryPolicy.evaluate(
        source = source,
        options = OnDeviceAiProviderDiscoveryOptions(enabled = true),
    ) as OnDeviceAiProviderDiscoveryResult.Completed

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
        val SIGNER_A = OnDeviceAiHostTestFixtures.SIGNER_A
        val SIGNER_B = OnDeviceAiHostTestFixtures.SIGNER_B
    }
}

private fun evaluationInput(
    evaluation: OnDeviceAiProviderDiscoveryEvaluation,
): OnDeviceAiProviderDiscoveryInput = when (evaluation) {
    is OnDeviceAiProviderDiscoveryEvaluation.Discovered -> evaluation.input
    is OnDeviceAiProviderDiscoveryEvaluation.Rejected -> evaluation.input
    is OnDeviceAiProviderDiscoveryEvaluation.SourceFailure -> error("Source failures do not carry complete inputs")
}

private fun input(
    packageName: String = "example.ai.provider",
    className: String = "OnDeviceAiService",
    declaredAction: String = OnDeviceAiProtocol.ACTION_PROVIDER,
    exported: Boolean = true,
    applicationEnabled: Boolean = true,
    serviceEnabled: Boolean = true,
    requiredPermission: String? = OnDeviceAiProtocol.PLUGIN_PERMISSION,
    packageUid: Int = 10_001,
    versionCode: Long = 2L,
    lastUpdateTime: Long = 20L,
    signers: Collection<String> = listOf(OnDeviceAiHostTestFixtures.SIGNER_A),
    officialSignerMatch: Boolean = true,
    sameSignerAsHost: Boolean = true,
    debuggable: Boolean = false,
) = OnDeviceAiProviderDiscoveryInput(
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
