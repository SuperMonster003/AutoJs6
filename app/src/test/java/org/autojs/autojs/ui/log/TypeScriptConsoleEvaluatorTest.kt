package org.autojs.autojs.ui.log

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mozilla.javascript.Undefined

class TypeScriptConsoleEvaluatorTest {

    @Test
    fun formatsPrimitiveRhinoResultsForConsoleOutput() {
        assertEquals("42", TypeScriptConsoleEvaluator.formatResult(42.0))
        assertEquals("hello", TypeScriptConsoleEvaluator.formatResult("hello"))
        assertEquals("null", TypeScriptConsoleEvaluator.formatResult(null))
        assertEquals("undefined", TypeScriptConsoleEvaluator.formatResult(Undefined.instance))
    }

}
