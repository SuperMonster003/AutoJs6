package org.autojs.autojs.core.plugin.dex

import org.autojs.plugin.dexcompiler.api.DexCompilerDiagnostic
import org.autojs.plugin.dexcompiler.api.DexCompilerDiagnosticSeverity
import org.autojs.plugin.dexcompiler.api.DexCompilerErrorCode
import org.autojs.plugin.dexcompiler.api.DexCompilerFailurePhase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DexCompilerRuntimeDiagnosticsTest {
    @After
    fun reset() {
        DexCompilerRuntimeDiagnostics.resetForTest()
    }

    @Test
    fun providerFailureSnapshotIsBoundedAndNeverIncludesProviderMessagesOrMetadata() {
        val diagnostics = listOf(
            diagnostic(DexCompilerDiagnosticSeverity.ERROR, "D8_MISSING_DEPENDENCY", "private/source/path"),
            diagnostic(DexCompilerDiagnosticSeverity.WARNING, "D8_DESUGARING", "secret warning"),
            diagnostic(DexCompilerDiagnosticSeverity.INFO, "D8_INFO", "secret info"),
            diagnostic(DexCompilerDiagnosticSeverity.ERROR, "D8_UNSUPPORTED", "secret error"),
            diagnostic(DexCompilerDiagnosticSeverity.WARNING, "D8.DIAGNOSTICS_TRUNCATED", "17 dropped"),
        )
        val remote = remoteFailure(diagnostics)

        DexCompilerRuntimeDiagnostics.record(useLocalOutcome(remote))
        DexCompilerRuntimeDiagnostics.recordFallback(useLocalDecision(remote))

        val route = requireNotNull(DexCompilerRuntimeDiagnostics.latest())
        val snapshot = requireNotNull(DexCompilerRuntimeDiagnostics.latestFailureForTest())
        assertEquals(DexCompilerRuntimePath.BUILTIN, route.path)
        assertEquals(DexCompilerRuntimeHint.FALLBACK, route.hint)
        assertEquals(DexCompilerExperimentalFailure.REMOTE_FAILED, snapshot.failure)
        assertEquals(DexCompilerErrorCode.COMPILATION_FAILED, snapshot.code)
        assertEquals(DexCompilerFailurePhase.COMPILATION, snapshot.phase)
        assertEquals(2, snapshot.diagnostics.errorCount)
        assertEquals(2, snapshot.diagnostics.warningCount)
        assertEquals(1, snapshot.diagnostics.infoCount)
        assertEquals(2, snapshot.diagnostics.additionalCodeCount)
        assertTrue(snapshot.diagnostics.truncated)
        assertFalse(snapshot.toString().contains("private/source/path"))
        assertFalse(snapshot.toString().contains("secret"))
        assertFalse(snapshot.toString().contains("com/example/Private.class"))
        assertFalse(remote.toString().contains("private/source/path"))
        val details = requireNotNull(DexCompilerRuntimeDiagnostics.latestFailureDetails())
        assertTrue(details.contains("REMOTE_FAILED/COMPILATION_FAILED/COMPILATION"))
        assertTrue(details.contains("E2 W2 I1"))
        assertTrue(details.contains("D8_MISSING_DEPENDENCY"))
        assertTrue(details.contains("TRUNCATED"))
        assertFalse(details.contains("private/source/path"))
        assertFalse(details.contains("secret"))
    }

    @Test
    fun routeUpdatesAndNonRemoteFallbacksDoNotOverwriteTheLastProviderFailure() {
        DexCompilerRuntimeDiagnostics.record(useLocalOutcome(remoteFailure(emptyList())))
        val original = DexCompilerRuntimeDiagnostics.latestFailureForTest()

        DexCompilerRuntimeDiagnostics.recordPublished(cacheHit = false)
        DexCompilerRuntimeDiagnostics.record(
            DexCompilerExperimentalOutcome.UseLocalCompiler(
                failure = DexCompilerExperimentalFailure.PLUGIN_UNAVAILABLE,
                pluginWasDispatched = false,
                pluginOutputRollbackRequired = false,
            ),
        )

        assertEquals(DexCompilerRuntimePath.PLUGIN, DexCompilerRuntimeDiagnostics.latest()?.path)
        assertEquals(original, DexCompilerRuntimeDiagnostics.latestFailureForTest())
    }

    @Test
    fun recordsPluginCacheAndDirectBuiltInRoutesWithoutPersistentIdentifiers() {
        DexCompilerRuntimeDiagnostics.recordPublished(cacheHit = false)
        assertEquals(DexCompilerRuntimePath.PLUGIN, DexCompilerRuntimeDiagnostics.latest()?.path)

        DexCompilerRuntimeDiagnostics.recordPublished(cacheHit = true)
        assertEquals(DexCompilerRuntimePath.CACHE_HIT, DexCompilerRuntimeDiagnostics.latest()?.path)

        DexCompilerRuntimeDiagnostics.recordBuiltIn()
        val builtIn = requireNotNull(DexCompilerRuntimeDiagnostics.latest())
        assertEquals(DexCompilerRuntimePath.BUILTIN, builtIn.path)
        assertNull(builtIn.hint)

        DexCompilerRuntimeDiagnostics.recordPublishedLoadFallback()
        val fallback = requireNotNull(DexCompilerRuntimeDiagnostics.latest())
        assertEquals(DexCompilerRuntimePath.BUILTIN, fallback.path)
        assertEquals(DexCompilerRuntimeHint.FALLBACK, fallback.hint)
    }

    @Test
    fun mapsBusyAndTimeoutFallbacksToDedicatedUserHints() {
        DexCompilerRuntimeDiagnostics.recordFallback(
            DexCompilerRuntimeDecision.UseLocal(
                reason = DexCompilerLocalReason.REMOTE_ATTEMPT_FAILED,
                experimentalFailure = DexCompilerExperimentalFailure.BUSY,
            ),
        )
        assertEquals(DexCompilerRuntimeHint.BUSY, DexCompilerRuntimeDiagnostics.latest()?.hint)

        DexCompilerRuntimeDiagnostics.recordFallback(
            DexCompilerRuntimeDecision.UseLocal(
                reason = DexCompilerLocalReason.REMOTE_ATTEMPT_FAILED,
                experimentalFailure = DexCompilerExperimentalFailure.TIMEOUT,
            ),
        )
        assertEquals(DexCompilerRuntimeHint.TIMEOUT, DexCompilerRuntimeDiagnostics.latest()?.hint)

        DexCompilerRuntimeDiagnostics.recordFallback(
            DexCompilerRuntimeDecision.UseLocal(
                reason = DexCompilerLocalReason.REMOTE_ATTEMPT_FAILED,
                remoteFailure = DexCompilerExperimentalOutcome.RemoteFailure(
                    code = DexCompilerErrorCode.BUSY,
                    phase = DexCompilerFailurePhase.QUEUE,
                    retryable = true,
                ),
            ),
        )
        val remoteBusy = requireNotNull(DexCompilerRuntimeDiagnostics.latest())
        assertEquals(DexCompilerRuntimeHint.BUSY, remoteBusy.hint)
    }

    @Test
    fun remoteFailureDefensivelyCopiesDiagnostics() {
        val source = mutableListOf(
            diagnostic(DexCompilerDiagnosticSeverity.ERROR, "D8.GENERIC", "private detail"),
        )
        val remote = remoteFailure(source)

        source.clear()

        assertEquals(1, remote.diagnostics.size)
        val mutation = runCatching {
            @Suppress("UNCHECKED_CAST")
            (remote.diagnostics as MutableList<DexCompilerDiagnostic>).clear()
        }
        assertTrue(mutation.exceptionOrNull() is UnsupportedOperationException)
        assertEquals(1, remote.diagnostics.size)
    }

    private fun useLocalDecision(remote: DexCompilerExperimentalOutcome.RemoteFailure) =
        DexCompilerRuntimeDecision.UseLocal(
            reason = DexCompilerLocalReason.REMOTE_ATTEMPT_FAILED,
            experimentalFailure = DexCompilerExperimentalFailure.REMOTE_FAILED,
            remoteFailure = remote,
        )

    private fun useLocalOutcome(remote: DexCompilerExperimentalOutcome.RemoteFailure) =
        DexCompilerExperimentalOutcome.UseLocalCompiler(
            failure = DexCompilerExperimentalFailure.REMOTE_FAILED,
            pluginWasDispatched = true,
            pluginOutputRollbackRequired = true,
            remoteFailure = remote,
        )

    private fun remoteFailure(diagnostics: List<DexCompilerDiagnostic>) =
        DexCompilerExperimentalOutcome.RemoteFailure(
            code = DexCompilerErrorCode.COMPILATION_FAILED,
            phase = DexCompilerFailurePhase.COMPILATION,
            retryable = false,
            message = "raw provider failure with a private path",
            diagnostics = diagnostics,
        )

    private fun diagnostic(
        severity: DexCompilerDiagnosticSeverity,
        code: String,
        message: String,
    ) = DexCompilerDiagnostic(
        severity = severity,
        code = code,
        message = message,
        origin = "program",
        entry = "com/example/Private.class",
    )
}
