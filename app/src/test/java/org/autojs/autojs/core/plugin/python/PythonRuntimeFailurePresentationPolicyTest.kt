package org.autojs.autojs.core.plugin.python

import org.autojs.autojs.script.PythonFileSource
import org.autojs.autojs.script.PythonProjectSource
import org.autojs.plugin.python.runtime.api.PythonErrorCode
import org.autojs.plugin.python.runtime.api.PythonExecutionError
import org.autojs.plugin.python.runtime.api.PythonFailurePhase
import org.autojs.plugin.python.runtime.api.PythonRequestId
import org.autojs.plugin.python.runtime.api.PythonTracebackFrame
import org.autojs.plugin.python.runtime.api.PythonTracebackOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class PythonRuntimeFailurePresentationPolicyTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun standaloneMissingModuleAddsExplicitProjectHintWithoutSourcePath() {
        val privateSource = PythonFileSource(File("D:/plugin-private/staging/main.py"))

        val message = PythonRuntimeFailurePresentationPolicy.messageFor(
            privateSource,
            missingModuleError(),
        )

        assertEquals(
            "$BASE_MISSING_MODULE_MESSAGE\n${PythonRuntimeFailurePresentationPolicy.MISSING_LOCAL_MODULE_HINT}",
            message,
        )
        assertTrue(message.contains("explicit Python project"))
        assertTrue(message.contains("`project.json` with `type: python`"))
        assertFalse(message.contains(privateSource.file.path))
        assertFalse(message.contains("plugin-private"))
    }

    @Test
    fun projectMissingModuleKeepsOriginalStructuredMessageWithoutStandaloneHint() {
        val root = temporaryFolder.newFolder("python-project")
        val entry = File(root, "main.py").apply { writeText("import missing_local\n") }
        val source = PythonProjectSource.create(root, entry)

        val message = PythonRuntimeFailurePresentationPolicy.messageFor(source, missingModuleError())

        assertEquals(BASE_MISSING_MODULE_MESSAGE, message)
        assertFalse(message.contains(PythonRuntimeFailurePresentationPolicy.MISSING_LOCAL_MODULE_HINT))
    }

    @Test
    fun otherStandalonePythonFailuresKeepExistingPresentationExactly() {
        val source = PythonFileSource(File("main.py"))
        val error = PythonExecutionError(
            requestId = REQUEST_ID,
            code = PythonErrorCode.PYTHON_EXCEPTION,
            phase = PythonFailurePhase.EXECUTION,
            message = "Python execution raised an exception",
            retryableBeforeDispatch = false,
            exceptionType = "ImportError",
            exceptionMessage = "attempted relative import with no known parent package",
            traceback = listOf(
                PythonTracebackFrame(
                    origin = PythonTracebackOrigin.PROJECT,
                    fileName = "main.py",
                    lineNumber = 1,
                    functionName = "<module>",
                    sourceLine = "from . import helper",
                ),
            ),
        )

        val message = PythonRuntimeFailurePresentationPolicy.messageFor(source, error)

        assertEquals(
            "PYTHON_EXCEPTION/EXECUTION: Python execution raised an exception " +
                "[ImportError] attempted relative import with no known parent package\n" +
                "  at main.py:1 in <module>\n" +
                "    from . import helper",
            message,
        )
    }

    @Test
    fun nonPythonExceptionCodeDoesNotReceiveHintEvenWithMatchingExceptionLabel() {
        val source = PythonFileSource(File("main.py"))
        val error = PythonExecutionError(
            requestId = REQUEST_ID,
            code = PythonErrorCode.INTERNAL,
            phase = PythonFailurePhase.EXECUTION,
            message = "Internal runtime failure",
            retryableBeforeDispatch = false,
            exceptionType = "ModuleNotFoundError",
            exceptionMessage = "No module named 'helper'",
        )

        val message = PythonRuntimeFailurePresentationPolicy.messageFor(source, error)

        assertFalse(message.contains(PythonRuntimeFailurePresentationPolicy.MISSING_LOCAL_MODULE_HINT))
    }

    private fun missingModuleError() = PythonExecutionError(
        requestId = REQUEST_ID,
        code = PythonErrorCode.PYTHON_EXCEPTION,
        phase = PythonFailurePhase.EXECUTION,
        message = "Python execution raised an exception",
        retryableBeforeDispatch = false,
        exceptionType = "ModuleNotFoundError",
        exceptionMessage = "No module named 'helper'",
    )

    private companion object {
        val REQUEST_ID: PythonRequestId = PythonRequestId.fromUuid(
            UUID.fromString("12345678-1234-5678-9abc-def012345678"),
        )
        const val BASE_MISSING_MODULE_MESSAGE =
            "PYTHON_EXCEPTION/EXECUTION: Python execution raised an exception " +
                "[ModuleNotFoundError] No module named 'helper'"
    }
}
