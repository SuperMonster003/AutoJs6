package org.autojs.autojs.core.plugin.typescript

import org.autojs.autojs.runtime.exception.ScriptCompilationDiagnosticSeverity
import org.autojs.autojs.runtime.exception.ScriptCompilationException
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScriptDiagnosticsExceptionTest {

    @Test
    fun projectSchemaDiagnosticsIntoHostCompilationException() {
        val diagnostic = TypeScriptDiagnostic(
            phase = "semantic",
            uri = "autojs://source/project/my%20script.ts",
            range = TypeScriptDiagnosticRange(start = 12, length = 4, line = 3, column = 7),
            category = TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_ERROR,
            code = 2322,
            message = "Type mismatch",
            relatedInformation = listOf(
                TypeScriptRelatedInformation(
                    uri = "autojs://source/project/helper.ts",
                    range = TypeScriptDiagnosticRange(
                        start = 0,
                        length = 1,
                        line = 1,
                        column = 2,
                    ),
                    category = TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_MESSAGE,
                    code = 6200,
                    message = "Related declaration",
                ),
            ),
        )

        val failure: Throwable = TypeScriptDiagnosticsException(
            message = "TypeScript compilation failed",
            sourceName = "fallback.ts",
            typeScriptDiagnostics = listOf(diagnostic),
        )

        assertTrue(failure is ScriptCompilationException)
        val exception = failure as ScriptCompilationException
        val projected = exception.diagnostics.single()
        assertEquals("my script.ts", projected.sourceName)
        assertEquals(3, projected.lineNumber)
        assertEquals(7, projected.columnNumber)
        assertEquals(ScriptCompilationDiagnosticSeverity.ERROR, projected.severity)
        assertEquals("TS2322", projected.code)
        assertEquals("helper.ts", projected.relatedInformation.single().sourceName)
        assertEquals(
            "my script.ts:3:7 - error TS2322: Type mismatch",
            projected.consoleLine,
        )
    }
}
