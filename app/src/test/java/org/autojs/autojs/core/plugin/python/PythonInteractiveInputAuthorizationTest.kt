package org.autojs.autojs.core.plugin.python

import org.autojs.autojs.execution.ExecutionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PythonInteractiveInputAuthorizationTest {
    @Test
    fun backgroundConfigurationHasNoInteractiveInputAuthority() {
        assertNull(PythonInteractiveInputAuthorization.resolve(null))
        assertNull(PythonInteractiveInputAuthorization.resolve(ExecutionConfig()))
    }

    @Test
    fun arbitraryExecutionArgumentCannotForgeForegroundAuthority() {
        val config = ExecutionConfig().apply {
            setArgument(PythonInteractiveInputAuthorization.ARGUMENT, Any())
        }

        val error = assertThrows(PythonRuntimeHostException::class.java) {
            PythonInteractiveInputAuthorization.resolve(config)
        }
        assertEquals(PythonRuntimeHostError.INTERACTIVE_INPUT_NOT_ALLOWED, error.error)
    }
}
