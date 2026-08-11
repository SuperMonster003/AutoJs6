package org.autojs.autojs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class PythonLaunchBoundaryPolicyTest {

    @Test
    fun classifiesLocalSourceCaseInsensitivelyWithoutConfusingWindowsPathsWithUris() {
        listOf(
            "main.py",
            "MAIN.PY",
            "module.test.py",
            ".py",
            "C:\\work\\main.py",
            "\\\\server\\share\\main.PY",
        ).forEach { location ->
            assertEquals(
                location,
                PythonLaunchClassification.PYTHON_SOURCE_REQUIRES_RUNTIME,
                PythonLaunchBoundaryPolicy.classify(location),
            )
        }
    }

    @Test
    fun rejectsCompiledSourcesAndPythonUrisWithDistinctStableCodes() {
        listOf("main.pyc", "MAIN.PYC", ".pyc").forEach { location ->
            assertBoundaryError(
                location,
                PythonLaunchBoundaryError.COMPILED_SOURCE_UNSUPPORTED,
            )
        }
        listOf(
            "content://authority/scripts/main.py",
            "file:///storage/emulated/0/main.PY",
            "https://example.invalid/main.py?revision=1#run",
            "content:main.pyc",
            "content://authority/scripts/main%2Epy",
        ).forEach { location ->
            assertBoundaryError(location, PythonLaunchBoundaryError.URI_UNSUPPORTED)
        }
    }

    @Test
    fun leavesNonPythonAndAmbiguousNamesOnTheExistingLaunchPath() {
        listOf(
            "main.js",
            "archive.py.zip",
            "main.py ",
            "content://authority/scripts/main.js",
            "",
            "   ",
        ).forEach { location ->
            assertEquals(
                location,
                PythonLaunchClassification.NOT_PYTHON,
                PythonLaunchBoundaryPolicy.classify(location),
            )
        }
    }

    @Test
    fun allExistingFactoryEntryShapesFailClosedBeforeLegacyDispatch() {
        val source = AutoFileSource(File("source.py"))
        val calls = listOf<() -> Unit>(
            {
                ScriptLaunchSourceFactory.createFileLaunchForPythonPreview(
                    file = File("file.py"),
                    defaultWorkingDirectory = "default",
                    pythonPreviewEnabled = false,
                )
            },
            {
                ScriptLaunchSourceFactory.createPathLaunchForPythonPreview(
                    path = "path.py",
                    defaultWorkingDirectory = "default",
                    pythonPreviewEnabled = false,
                )
            },
            {
                ScriptLaunchSourceFactory.createProjectEntryLaunch(
                    projectRoot = File("project"),
                    entryFile = File("project/main.py"),
                    defaultWorkingDirectory = "default",
                )
            },
            {
                ScriptLaunchSourceFactory.createNodeProjectLaunch(
                    projectRoot = File("project"),
                    entryFile = File("project/main.py"),
                    defaultWorkingDirectory = "default",
                )
            },
            {
                ScriptLaunchSourceFactory.createSourceLaunch(
                    source = source,
                    defaultWorkingDirectory = "default",
                )
            },
        )

        calls.forEach { call ->
            val failure = expectBoundaryFailure(call)
            assertEquals(PythonLaunchBoundaryError.RUNTIME_NOT_ACTIVATED, failure.error)
            assertFalse(failure.message.orEmpty().contains("file.py"))
            assertFalse(failure.message.orEmpty().contains("path.py"))
            assertFalse(failure.message.orEmpty().contains("project/main.py"))
            assertFalse(failure.message.orEmpty().contains("source.py"))
        }
    }

    @Test
    fun semanticOriginsAndPythonMimeTypesCannotHideBehindTemporaryJavaScriptFiles() {
        val originSource = AutoFileSource(File("temporary.js")).apply {
            overriddenFullPath = "original.py"
            originUri = "content://authority/document/without-extension"
        }
        val originFailure = expectBoundaryFailure {
            ScriptLaunchSourceFactory.createSourceLaunch(
                source = originSource,
                defaultWorkingDirectory = "default",
            )
        }
        assertEquals(PythonLaunchBoundaryError.RUNTIME_NOT_ACTIVATED, originFailure.error)

        val uriSource = AutoFileSource(File("temporary.js")).apply {
            overriddenFullPath = "original.js"
            originUri = "content://authority/document/original.py"
        }
        val uriFailure = expectBoundaryFailure {
            ScriptLaunchSourceFactory.createSourceLaunch(
                source = uriSource,
                defaultWorkingDirectory = "default",
            )
        }
        assertEquals(PythonLaunchBoundaryError.URI_UNSUPPORTED, uriFailure.error)

        listOf(
            "text/x-python",
            "application/x-python-code; charset=utf-8",
            "APPLICATION/PYTHON",
        ).forEach { mimeType ->
            val failure = expectBoundaryFailure {
                ScriptLaunchSourceFactory.enforcePythonMimeBoundary(mimeType)
            }
            assertEquals(PythonLaunchBoundaryError.URI_UNSUPPORTED, failure.error)
        }
        ScriptLaunchSourceFactory.enforcePythonMimeBoundary("text/javascript")
    }

    private fun assertBoundaryError(
        location: String,
        expected: PythonLaunchBoundaryError,
    ) {
        val failure = expectBoundaryFailure {
            PythonLaunchBoundaryPolicy.enforceDefaultOff(location)
        }
        assertEquals(expected, failure.error)
        assertEquals("${expected.stableCode}: ${expected.stableMessage}", failure.message)
        assertFalse(failure.message.orEmpty().contains(location))
    }

    private fun expectBoundaryFailure(block: () -> Unit): PythonLaunchBoundaryException {
        try {
            block()
            fail("Expected PythonLaunchBoundaryException")
        } catch (failure: PythonLaunchBoundaryException) {
            return failure
        }
        throw AssertionError("unreachable")
    }
}
