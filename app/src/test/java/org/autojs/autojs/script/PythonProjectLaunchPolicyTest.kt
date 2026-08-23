package org.autojs.autojs.script

import org.autojs.autojs.core.plugin.python.PythonExecutionInputPolicy
import org.autojs.plugin.python.runtime.api.PythonEntryMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class PythonProjectLaunchPolicyTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun minimalTypedManifestProducesCanonicalAdmission() {
        val root = project("""{"type":"python","main":"main.py"}""")
        val entry = File(root, "main.py").apply { writeText("print('ready')") }

        val admission = requireNotNull(PythonProjectLaunchPolicy.inspect(root, File(root, "main.js")))

        assertEquals(root.canonicalFile, admission.projectRoot)
        assertEquals(entry.canonicalFile, admission.entryFile)
        assertNull(admission.timeoutMillis)
        assertEquals(PythonEntryMode.FILE, admission.entryMode)
        val launch = ScriptLaunchSourceFactory.createPythonProjectLaunchForPreview(
            admission = admission,
            pythonPreviewEnabled = true,
        )
        val projectSource = launch.source as PythonProjectSource
        assertEquals(root.canonicalFile, projectSource.projectRoot)
        assertEquals(entry.canonicalFile, projectSource.file)
        assertEquals("main.py", projectSource.entryPoint)
        assertNull(projectSource.timeoutMillis)
        assertEquals(PythonEntryMode.FILE, projectSource.entryMode)
        assertEquals(root.canonicalPath, launch.workingDirectory)
        assertTrue(launch.pythonProjectSource === projectSource)
        assertEquals(
            "file",
            launch.createExecutionConfig().getArgument(PythonExecutionInputPolicy.ENTRY_MODE_ARGUMENT),
        )
        assertProjectError(PythonProjectLaunchError.EXECUTION_NOT_ACTIVATED) {
            ScriptLaunchSourceFactory.createPythonProjectLaunchForPreview(
                admission = admission,
                pythonPreviewEnabled = false,
            )
        }
    }

    @Test
    fun dottedModuleManifestFlowsIntoSourceAndExecutionConfig() {
        val root = project("""{"type":"python","entryMode":"module","main":"pkg.main"}""")
        File(root, "pkg/__init__.py").apply {
            parentFile.mkdirs()
            writeText("")
        }
        val entry = File(root, "pkg/main.py").apply { writeText("from . import sibling") }

        val admission = requireNotNull(PythonProjectLaunchPolicy.inspect(root, entry))
        val launch = ScriptLaunchSourceFactory.createPythonProjectLaunchForPreview(
            admission = admission,
            pythonPreviewEnabled = true,
        )
        val source = launch.source as PythonProjectSource

        assertEquals(entry.canonicalFile, admission.entryFile)
        assertEquals(PythonEntryMode.MODULE, admission.entryMode)
        assertEquals("pkg/main.py", source.entryPoint)
        assertEquals(PythonEntryMode.MODULE, source.entryMode)
        assertEquals(
            "module",
            launch.createExecutionConfig().getArgument(PythonExecutionInputPolicy.ENTRY_MODE_ARGUMENT),
        )
        assertEquals(
            admission,
            PythonProjectLaunchPolicy.inspectContainingEntry(entry),
        )
    }

    @Test
    fun moduleEntryRequiresExactModeAndNormalizedDottedMain() {
        listOf("MODULE", "module ", "", "unknown").forEach { entryMode ->
            val root = project("""{"type":"python","entryMode":"$entryMode","main":"pkg.main"}""")
            assertProjectError(PythonProjectLaunchError.CONFIG_INVALID) {
                PythonProjectLaunchPolicy.inspect(root, null)
            }
        }
        listOf("true", "null", "1", "{}").forEach { entryMode ->
            val root = project("""{"type":"python","entryMode":$entryMode,"main":"pkg.main"}""")
            assertProjectError(PythonProjectLaunchError.CONFIG_INVALID) {
                PythonProjectLaunchPolicy.inspect(root, null)
            }
        }
        listOf("pkg/main", ".pkg", "pkg..main", "pkg.class", "pkg.__init__", "pkg.café").forEach { main ->
            val root = project("""{"type":"python","entryMode":"module","main":"$main"}""")
            assertProjectError(PythonProjectLaunchError.ENTRY_INVALID) {
                PythonProjectLaunchPolicy.inspect(root, null)
            }
        }
    }

    @Test
    fun moduleEntryStillRequiresExplicitPythonProjectType() {
        val root = project("""{"type":"javascript","entryMode":"module","main":"pkg.main"}""")

        assertProjectError(PythonProjectLaunchError.TYPE_REQUIRED) {
            PythonProjectLaunchPolicy.inspect(root, null)
        }
    }

    @Test
    fun exactIntegerTimeoutMillisFlowsFromManifestIntoProjectSource() {
        val root = project("""{"type":"python","main":"main.py","timeout":90000}""")
        val entry = File(root, "main.py").apply { writeText("print('configured timeout')") }

        val admission = requireNotNull(PythonProjectLaunchPolicy.inspect(root, entry))
        val launch = ScriptLaunchSourceFactory.createPythonProjectLaunchForPreview(
            admission = admission,
            pythonPreviewEnabled = true,
        )

        assertEquals(90_000L, admission.timeoutMillis)
        assertEquals(90_000L, (launch.source as PythonProjectSource).timeoutMillis)
    }

    @Test
    fun timeoutMustBeAnExactPositiveIntegerWithinThirtyMinutes() {
        listOf(
            "0",
            "-1",
            "1800001",
            "1.5",
            "1e3",
            "\"90000\"",
            "null",
            "true",
        ).forEach { timeout ->
            val root = project("""{"type":"python","main":"main.py","timeout":$timeout}""")
            val entry = File(root, "main.py").apply { writeText("print('invalid timeout')") }

            assertProjectError(PythonProjectLaunchError.CONFIG_INVALID) {
                PythonProjectLaunchPolicy.inspect(root, entry)
            }
        }
    }

    @Test
    fun thirtyMinuteTimeoutIsAcceptedAtTheInclusiveBoundary() {
        val root = project("""{"type":"python","main":"main.py","timeout":1800000}""")
        val entry = File(root, "main.py").apply { writeText("print('maximum timeout')") }

        val admission = requireNotNull(PythonProjectLaunchPolicy.inspect(root, entry))

        assertEquals(1_800_000L, admission.timeoutMillis)
    }

    @Test
    fun pythonEntryWithoutPythonTypeIsRejected() {
        val root = project("""{"type":"javascript","main":"main.py"}""")
        File(root, "main.py").writeText("print('wrong type')")

        assertProjectError(PythonProjectLaunchError.TYPE_REQUIRED) {
            PythonProjectLaunchPolicy.inspect(root, File(root, "main.py"))
        }
    }

    @Test
    fun nonPythonProjectRemainsOnLegacyPath() {
        val root = project("""{"type":"javascript","main":"main.js"}""")
        val legacyMain = File(root, "main.js").apply { writeText("console.log('ready')") }

        assertNull(PythonProjectLaunchPolicy.inspect(root, legacyMain))
    }

    @Test
    fun invalidEntryShapesAreRejected() {
        listOf("../main.py", "/main.py", "src\\main.py", "content://authority/main.py", "main.pyc").forEach { main ->
            val encodedMain = main.replace("\\", "\\\\")
            val root = project("""{"type":"python","main":"$encodedMain"}""")
            assertProjectError(PythonProjectLaunchError.ENTRY_INVALID) {
                PythonProjectLaunchPolicy.inspect(root, File(root, main))
            }
        }
    }

    @Test
    fun missingPythonEntryIsRejected() {
        val root = project("""{"type":"python","main":"main.py"}""")

        assertProjectError(PythonProjectLaunchError.ENTRY_MISSING) {
            PythonProjectLaunchPolicy.inspect(root, File(root, "main.py"))
        }
    }

    @Test
    fun trailingJsonContentIsRejected() {
        val root = project("""{"type":"python","main":"main.py"} {}""")

        assertProjectError(PythonProjectLaunchError.CONFIG_INVALID) {
            PythonProjectLaunchPolicy.inspect(root, File(root, "main.py"))
        }
    }

    @Test
    fun nestedEntryProducesForwardSlashLogicalPath() {
        val root = project("""{"type":"python","main":"src/main.py"}""")
        val entry = File(root, "src/main.py").apply {
            parentFile.mkdirs()
            writeText("print('nested')")
        }

        val admission = requireNotNull(PythonProjectLaunchPolicy.inspect(root, entry))
        val source = PythonProjectSource.create(admission.projectRoot, admission.entryFile)

        assertEquals("src/main.py", source.entryPoint)
    }

    @Test
    fun inRootSymbolicLinkEntryIsRejected() {
        val root = project("""{"type":"python","main":"main.py"}""")
        val target = File(root, "real.py").apply { writeText("print('target')") }
        val link = File(root, "main.py")
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath().fileName)
        } catch (error: Exception) {
            assumeNoException("Symbolic links are unavailable in this test environment", error)
        }

        assertProjectError(PythonProjectLaunchError.ENTRY_ESCAPES_ROOT) {
            PythonProjectLaunchPolicy.inspect(root, link)
        }
    }

    private fun project(manifest: String): File =
        temporaryFolder.newFolder().also { root ->
            File(root, "project.json").writeText(manifest)
        }

    private fun assertProjectError(
        expected: PythonProjectLaunchError,
        block: () -> Unit,
    ) {
        try {
            block()
            fail("Expected PythonProjectLaunchException")
        } catch (failure: PythonProjectLaunchException) {
            assertEquals(expected, failure.error)
        }
    }
}
