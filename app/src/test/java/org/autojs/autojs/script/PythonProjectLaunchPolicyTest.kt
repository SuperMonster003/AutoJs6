package org.autojs.autojs.script

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
        val launch = ScriptLaunchSourceFactory.createPythonProjectLaunchForPreview(
            admission = admission,
            pythonPreviewEnabled = true,
        )
        val projectSource = launch.source as PythonProjectSource
        assertEquals(root.canonicalFile, projectSource.projectRoot)
        assertEquals(entry.canonicalFile, projectSource.file)
        assertEquals("main.py", projectSource.entryPoint)
        assertEquals(root.canonicalPath, launch.workingDirectory)
        assertTrue(launch.pythonProjectSource === projectSource)
        assertProjectError(PythonProjectLaunchError.EXECUTION_NOT_ACTIVATED) {
            ScriptLaunchSourceFactory.createPythonProjectLaunchForPreview(
                admission = admission,
                pythonPreviewEnabled = false,
            )
        }
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
