package org.autojs.autojs.external

import org.autojs.autojs.script.PythonLaunchBoundaryError
import org.autojs.autojs.script.PythonLaunchBoundaryException
import org.autojs.autojs.script.PythonProjectLaunchError
import org.autojs.autojs.script.PythonProjectLaunchException
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class ScriptIntentBoundaryPolicyTest {

    @Test
    fun nonLocalAndMixedUriRequestsFailWithStableErrors() {
        assertIntentError(ScriptIntentBoundaryError.URI_SCHEME_UNSUPPORTED) {
            ScriptIntents.validateExternalUriRequestShape(
                scheme = "https",
                hasPathExtra = false,
                hasJsonExtra = false,
                hasPreExecuteExtra = false,
                hasSourceKindExtra = false,
                hasProjectRootExtra = false,
            )
        }
        assertIntentError(ScriptIntentBoundaryError.URI_PAYLOAD_CONFLICT) {
            ScriptIntents.validateExternalUriRequestShape(
                scheme = "file",
                hasPathExtra = false,
                hasJsonExtra = true,
                hasPreExecuteExtra = false,
                hasSourceKindExtra = false,
                hasProjectRootExtra = false,
            )
        }
    }

    @Test
    fun contentContextProjectDescriptorAndPythonExecutionStayClosed() {
        assertProjectError(PythonProjectLaunchError.CONTEXT_INVALID) {
            ScriptIntents.validateExternalUriRequestShape(
                scheme = "content",
                hasPathExtra = false,
                hasJsonExtra = false,
                hasPreExecuteExtra = false,
                hasSourceKindExtra = true,
                hasProjectRootExtra = true,
            )
        }
        assertProjectError(PythonProjectLaunchError.CONTEXT_REQUIRED) {
            ScriptIntents.enforceSingleFileImportBoundary("project.json", "/document/project.json")
        }
        assertBoundaryError(PythonLaunchBoundaryError.URI_UNSUPPORTED) {
            ScriptIntents.enforceContentPythonExecutionBoundary(
                displayName = "main.py",
                uriPath = "/document/main.py",
                originPath = null,
                explicitMimeType = "text/plain",
                providerMimeType = "text/x-python",
            )
        }
    }

    private fun assertIntentError(expected: ScriptIntentBoundaryError, block: () -> Unit) {
        try {
            block()
            fail("Expected ScriptIntentBoundaryException")
        } catch (failure: ScriptIntentBoundaryException) {
            assertSame(expected, failure.error)
        }
    }

    private fun assertProjectError(expected: PythonProjectLaunchError, block: () -> Unit) {
        try {
            block()
            fail("Expected PythonProjectLaunchException")
        } catch (failure: PythonProjectLaunchException) {
            assertSame(expected, failure.error)
        }
    }

    private fun assertBoundaryError(expected: PythonLaunchBoundaryError, block: () -> Unit) {
        try {
            block()
            fail("Expected PythonLaunchBoundaryException")
        } catch (failure: PythonLaunchBoundaryException) {
            assertSame(expected, failure.error)
        }
    }
}
