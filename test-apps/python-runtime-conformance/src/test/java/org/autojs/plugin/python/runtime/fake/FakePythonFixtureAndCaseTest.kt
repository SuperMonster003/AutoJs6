package org.autojs.plugin.python.runtime.fake

import org.autojs.plugin.protocol.wire.TaggedWireWriter
import org.autojs.plugin.python.runtime.api.PythonRuntimeCodec
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.autojs.plugin.python.runtime.api.PythonRuntimeValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePythonFixtureAndCaseTest {
    @Test
    fun fixtureIsAValidSelfConsistentProtocolProvider() {
        PythonRuntimeValidation.validateCapabilitiesAgainstInfo(
            FakePythonFixture.capabilities,
            FakePythonFixture.runtimeInfo,
        )
        assertEquals(1, FakePythonFixture.capabilities.limits.maxConcurrentSessions)
        assertTrue(FakePythonFixture.capabilities.supportsWorkspaceArchive.not())
    }

    @Test
    fun hostileCaseIdsAreStableUniqueAndExplicit() {
        val ids = FakePythonCaseId.entries.map(FakePythonCaseId::stableId)
        assertEquals(
            listOf(
                "normal",
                "structured-exception",
                "stall",
                "late-callback",
                "output-before-start",
                "output-without-credit",
                "oversized-chunk",
                "reordered-chunks",
                "duplicate-terminal",
            ),
            ids,
        )
        assertEquals(ids.size, ids.toSet().size)
        FakePythonCaseId.entries.forEach { expected ->
            assertEquals(
                expected,
                FakePythonCaseId.fromArguments(
                    listOf(FakePythonCaseId.ARGUMENT_PREFIX + expected.stableId),
                ),
            )
        }
        assertEquals(FakePythonCaseId.NORMAL, FakePythonCaseId.fromArguments(emptyList()))
        assertFails { FakePythonCaseId.fromArguments(listOf("--fake-python-case=unknown")) }
    }

    @Test
    fun correlationIdCanBeRecoveredBeforeFullAdmissionValidation() {
        val request = FakePythonTestFixtures.request()
        val encoded = PythonRuntimeCodec.encodeExecutionRequest(request)
        assertEquals(request.requestId, FakePythonRequestEnvelope.requireUniqueRequestId(encoded))

        val duplicateId = TaggedWireWriter(
            PythonRuntimeContract.SCHEMA_EXECUTION_REQUEST,
            PythonRuntimeContract.SCHEMA_MAJOR,
            PythonRuntimeContract.SCHEMA_MINOR,
        )
            .bytes(1, request.requestId.toByteArray())
            .bytes(1, request.requestId.toByteArray())
            .encode()
        assertFails { FakePythonRequestEnvelope.requireUniqueRequestId(duplicateId) }
        assertFails { FakePythonRequestEnvelope.requireUniqueRequestId(ByteArray(0)) }
    }

    @Test
    fun hostIdentityRequiresExactUidPackageOwnershipAndSameSigner() {
        val allowed = FakeHostIdentityEvidence(
            callingUid = 12345,
            installedHostUid = 12345,
            packagesForCallingUid = setOf(FakePythonFixture.HOST_PACKAGE_NAME),
            providerAndHostSignaturesMatch = true,
        )
        FakeHostIdentityPolicy.requireAllowed(allowed)

        listOf(
            allowed.copy(installedHostUid = null),
            allowed.copy(installedHostUid = 54321),
            allowed.copy(packagesForCallingUid = setOf("untrusted.example")),
            allowed.copy(providerAndHostSignaturesMatch = false),
        ).forEach { evidence ->
            val error = runCatching { FakeHostIdentityPolicy.requireAllowed(evidence) }.exceptionOrNull()
            assertTrue(error is SecurityException)
        }
    }

    private fun assertFails(block: () -> Unit) {
        assertTrue(runCatching(block).isFailure)
    }
}
