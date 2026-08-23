package org.autojs.autojs.runtime.exception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScriptCompilationExceptionTest {

    @Test
    fun exposeStableDiagnosticsAndClickableConsoleLocations() {
        val relatedInformation = mutableListOf(
            ScriptCompilationDiagnostic(
                sourceName = "helper.ts",
                lineNumber = 1,
                columnNumber = 2,
                severity = ScriptCompilationDiagnosticSeverity.MESSAGE,
                code = "TS6200",
                message = "Related declaration",
            ),
        )
        val sourceDiagnostics = mutableListOf(
            ScriptCompilationDiagnostic(
                sourceName = "my script.ts",
                lineNumber = 3,
                columnNumber = 7,
                severity = ScriptCompilationDiagnosticSeverity.ERROR,
                code = "TS2322",
                message = "Type mismatch",
                relatedInformation = relatedInformation,
            ),
        )

        val exception = ScriptCompilationException("Compilation failed", sourceDiagnostics)
        sourceDiagnostics.clear()
        relatedInformation.clear()

        assertEquals("Compilation failed", exception.message)
        assertEquals(1, exception.diagnostics.size)
        assertEquals(1, exception.diagnostics.single().relatedInformation.size)
        assertEquals(
            "Compilation failed\n" +
                "my script.ts:3:7 - error TS2322: Type mismatch\n" +
                "  helper.ts:1:2 - message TS6200: Related declaration",
            exception.consoleMessage,
        )
        assertFalse(exception.consoleMessage.contains(ScriptCompilationException::class.java.name))
    }
}
