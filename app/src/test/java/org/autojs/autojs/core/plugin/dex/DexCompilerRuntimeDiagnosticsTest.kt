package org.autojs.autojs.core.plugin.dex

import org.autojs.plugin.dexcompiler.api.DexCompilerDiagnostic
import org.autojs.plugin.dexcompiler.api.DexCompilerDiagnosticSeverity
import org.autojs.plugin.dexcompiler.api.DexCompilerErrorCode
import org.autojs.plugin.dexcompiler.api.DexCompilerFailurePhase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DexCompilerRuntimeDiagnosticsTest {
    @After
    fun reset() {
        DexCompilerRuntimeDiagnostics.resetForTest()
    }

    @Test
    fun remoteFailureSummaryIsBoundedAndNeverIncludesProviderMessages() {
        val diagnostics = listOf(
            diagnostic(DexCompilerDiagnosticSeverity.ERROR, "D8_MISSING_DEPENDENCY", "private/source/path"),
            diagnostic(DexCompilerDiagnosticSeverity.WARNING, "D8_DESUGARING", "secret warning"),
            diagnostic(DexCompilerDiagnosticSeverity.INFO, "D8_INFO", "secret info"),
            diagnostic(DexCompilerDiagnosticSeverity.ERROR, "D8_UNSUPPORTED", "secret error"),
            diagnostic(DexCompilerDiagnosticSeverity.WARNING, "D8.DIAGNOSTICS_TRUNCATED", "17 dropped"),
        )
        val remote = remoteFailure(diagnostics)

        DexCompilerRuntimeDiagnostics.record(useLocal(remote))

        val snapshot = requireNotNull(DexCompilerRuntimeDiagnostics.latestForTest())
        val summary = requireNotNull(DexCompilerRuntimeDiagnostics.latestSummary())
        assertEquals(2, snapshot.diagnostics.errorCount)
        assertEquals(2, snapshot.diagnostics.warningCount)
        assertEquals(1, snapshot.diagnostics.infoCount)
        assertEquals(2, snapshot.diagnostics.additionalCodeCount)
        assertTrue(snapshot.diagnostics.truncated)
        assertTrue(summary.contains("COMPILATION_FAILED/COMPILATION"))
        assertTrue(summary.contains("REMOTE_FAILED"))
        assertTrue(summary.contains("LAST_PROVIDER_FALLBACK/PROCESS_ONLY"))
        assertTrue(summary.contains("E2 W2 I1"))
        assertTrue(summary.length <= 192)
        assertFalse(summary.contains("private/source/path"))
        assertFalse(summary.contains("secret"))
        assertFalse(remote.toString().contains("private/source/path"))
        assertFalse(remote.toString().contains("secret"))
    }

    @Test
    fun nonRemoteFallbackDoesNotOverwriteTheLastProviderFailure() {
        DexCompilerRuntimeDiagnostics.record(useLocal(remoteFailure(emptyList())))
        val original = DexCompilerRuntimeDiagnostics.latestForTest()

        DexCompilerRuntimeDiagnostics.record(
            DexCompilerExperimentalOutcome.UseLocalCompiler(
                failure = DexCompilerExperimentalFailure.PLUGIN_UNAVAILABLE,
                pluginWasDispatched = false,
                pluginOutputRollbackRequired = false,
            ),
        )

        assertEquals(original, DexCompilerRuntimeDiagnostics.latestForTest())
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

    private fun useLocal(remote: DexCompilerExperimentalOutcome.RemoteFailure) =
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
    ) = DexCompilerDiagnostic(severity, code, message)
}
