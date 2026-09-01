package org.autojs.autojs.apkbuilder.template

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteApkBuildDiagnosticsTest {

    @After
    fun reset() {
        RemoteApkBuildDiagnostics.resetForTest()
    }

    @Test
    fun eventNormalizesNumbersAndEmitsOnlyWhitelistedTokens() {
        val event = RemoteApkBuildDiagnostics.record(
            protocolVersion = -3,
            status = RemoteApkBuildDiagnosticStatus.FAILED,
            phase = RemoteApkBuildDiagnosticPhase.TRANSFER,
            elapsedMillis = -50L,
            outputSizeBytes = -20L,
            errorCategory = RemoteApkBuildErrorCategory.OUTPUT_REJECTED,
        )

        assertEquals(0, event.protocolVersion)
        assertEquals(0L, event.elapsedMillis)
        assertEquals(-1L, event.outputSizeBytes)
        assertTrue(
            event.safeLogLine().matches(
                Regex(
                    "^remote_apk_build seq=\\d+ protocol=\\d+ status=[A-Z_]+ phase=[A-Z_]+ " +
                        "elapsed_ms=\\d+ output_bytes=-?\\d+ error=[A-Z_]+$"
                )
            )
        )
    }

    @Test
    fun ringBufferKeepsOnlyTheNewestThirtyTwoEvents() {
        repeat(40) { index ->
            RemoteApkBuildDiagnostics.record(
                protocolVersion = index + 1,
                status = RemoteApkBuildDiagnosticStatus.SUCCEEDED,
                phase = RemoteApkBuildDiagnosticPhase.COMPLETE,
                elapsedMillis = index.toLong(),
                outputSizeBytes = index.toLong(),
                errorCategory = RemoteApkBuildErrorCategory.NONE,
            )
        }

        val snapshot = RemoteApkBuildDiagnostics.snapshot()
        assertEquals(32, snapshot.size)
        assertEquals(9L, snapshot.first().sequence)
        assertEquals(40L, snapshot.last().sequence)
    }

    @Test
    fun returnedSnapshotCannotMutateTheStoredRing() {
        val original = RemoteApkBuildDiagnostics.record(
            protocolVersion = 3,
            status = RemoteApkBuildDiagnosticStatus.CANCELLED,
            phase = RemoteApkBuildDiagnosticPhase.WAIT,
            elapsedMillis = 15L,
            outputSizeBytes = -1L,
            errorCategory = RemoteApkBuildErrorCategory.CANCELLED,
        )
        val returnedSnapshot = RemoteApkBuildDiagnostics.snapshot()
        val mutation = runCatching {
            @Suppress("UNCHECKED_CAST")
            (returnedSnapshot as MutableList<RemoteApkBuildDiagnosticEvent>).clear()
        }

        assertTrue(mutation.isFailure || returnedSnapshot.isEmpty())
        assertEquals(listOf(original), RemoteApkBuildDiagnostics.snapshot())
    }

    @Test
    fun diagnosticRepresentationsCannotContainRequestSecrets() {
        val forbidden = listOf(
            "/storage/emulated/0/private-project",
            "secret-project-name",
            "signing-password",
            "typescript-staging-key",
        )
        val event = RemoteApkBuildDiagnostics.record(
            protocolVersion = 3,
            status = RemoteApkBuildDiagnosticStatus.FAILED,
            phase = RemoteApkBuildDiagnosticPhase.PREPARE,
            elapsedMillis = 27L,
            outputSizeBytes = -1L,
            errorCategory = RemoteApkBuildErrorCategory.PREPARATION_FAILED,
        )
        val representations = event.toString() + event.safeLogLine() + RemoteApkBuildDiagnostics.snapshot()

        forbidden.forEach { secret ->
            assertFalse(representations.contains(secret))
        }
        assertFalse(event.safeLogLine().contains('/'))
    }
}
