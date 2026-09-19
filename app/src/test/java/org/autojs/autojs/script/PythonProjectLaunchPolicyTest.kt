package org.autojs.autojs.script

import org.autojs.autojs.core.plugin.python.PythonExecutionInputPolicy
import org.autojs.plugin.python.runtime.api.PythonEntryMode
import org.autojs.plugin.python.runtime.api.PythonExecutionMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(PythonExecutionMode.BOUNDED, admission.executionMode)
        assertNull(admission.stdin)
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
        assertNull(projectSource.stdin)
        assertEquals(root.canonicalPath, launch.workingDirectory)
        assertTrue(launch.pythonProjectSource === projectSource)
        assertEquals(
            "file",
            launch.createExecutionConfig().getArgument(PythonExecutionInputPolicy.ENTRY_MODE_ARGUMENT),
        )
        assertFalse(
            launch.createExecutionConfig().arguments.containsKey(
                PythonExecutionInputPolicy.STDIN_SNAPSHOT_ARGUMENT,
            ),
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
            checkNotNull(parentFile).mkdirs()
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
    fun inlineStdinFlowsAsStrictUtf8FromManifestIntoExecutionConfig() {
        val root = project(
            """{"type":"python","main":"main.py","stdin":"fixtures/input.txt\n雪\n"}""",
        )
        val entry = File(root, "main.py").apply { writeText("print('stdin')") }
        File(root, "fixtures/input.txt").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("must not be inferred as stdin")
        }

        val admission = requireNotNull(PythonProjectLaunchPolicy.inspect(root, entry))
        val launch = ScriptLaunchSourceFactory.createPythonProjectLaunchForPreview(
            admission = admission,
            pythonPreviewEnabled = true,
        )
        val source = launch.source as PythonProjectSource
        val expectedText = "fixtures/input.txt\n雪\n"

        assertEquals(PythonProjectStdinSource.InlineText(expectedText), admission.stdin)
        assertEquals(admission.stdin, source.stdin)
        val config = launch.createExecutionConfig()
        assertEquals(
            expectedText,
            config.getArgument(PythonExecutionInputPolicy.STDIN_SNAPSHOT_ARGUMENT),
        )
        assertArrayEquals(
            expectedText.toByteArray(Charsets.UTF_8),
            PythonExecutionInputPolicy.resolveStdinSnapshot(config),
        )
    }

    @Test
    fun projectRelativeStdinFileIsSnapshottedBeforeExecution() {
        val root = project(
            """{"type":"python","main":"main.py","stdin":{"file":"fixtures/input.bin"}}""",
        )
        val entry = File(root, "main.py").apply { writeText("print('stdin file')") }
        val expected = byteArrayOf(0, 1, 2, 10, 13, 127, -1)
        val stdinFile = File(root, "fixtures/input.bin").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(expected)
        }

        val admission = requireNotNull(PythonProjectLaunchPolicy.inspect(root, entry))
        val launch = ScriptLaunchSourceFactory.createPythonProjectLaunchForPreview(
            admission = admission,
            pythonPreviewEnabled = true,
        )
        assertEquals(PythonProjectStdinSource.ProjectFile("fixtures/input.bin"), admission.stdin)

        val config = launch.createExecutionConfig()
        stdinFile.writeBytes(byteArrayOf(9, 8, 7))

        assertArrayEquals(
            expected,
            PythonExecutionInputPolicy.resolveStdinSnapshot(config),
        )
    }

    @Test
    fun explicitEmptyInlineStdinRemainsAZeroByteSnapshot() {
        val root = project("""{"type":"python","main":"main.py","stdin":""}""")
        val entry = File(root, "main.py").apply { writeText("print('empty stdin')") }

        val admission = requireNotNull(PythonProjectLaunchPolicy.inspect(root, entry))
        val config = ScriptLaunchSourceFactory.createPythonProjectLaunchForPreview(
            admission = admission,
            pythonPreviewEnabled = true,
        ).createExecutionConfig()

        assertTrue(config.arguments.containsKey(PythonExecutionInputPolicy.STDIN_SNAPSHOT_ARGUMENT))
        assertArrayEquals(ByteArray(0), PythonExecutionInputPolicy.resolveStdinSnapshot(config))
    }

    @Test
    fun stdinDeclarationRejectsAmbiguousOrNonStringShapes() {
        listOf(
            "null",
            "true",
            "1",
            "[]",
            "{}",
            "{\"text\":\"ambiguous\"}",
            "{\"file\":1}",
            "{\"file\":\"input.txt\",\"extra\":true}",
        ).forEach { stdin ->
            val root = project("""{"type":"python","main":"main.py","stdin":$stdin}""")
            File(root, "main.py").writeText("print('invalid stdin')")

            assertProjectError(PythonProjectLaunchError.STDIN_INVALID) {
                PythonProjectLaunchPolicy.inspect(root, null)
            }
        }
    }

    @Test
    fun stdinFileRequiresANormalizedExistingProjectRelativeRegularFile() {
        listOf(
            "" to PythonProjectLaunchError.STDIN_INVALID,
            "../input.txt" to PythonProjectLaunchError.STDIN_INVALID,
            "/input.txt" to PythonProjectLaunchError.STDIN_INVALID,
            "fixtures\\input.txt" to PythonProjectLaunchError.STDIN_INVALID,
            "content://authority/input.txt" to PythonProjectLaunchError.STDIN_INVALID,
            "missing.txt" to PythonProjectLaunchError.STDIN_MISSING,
            "fixtures" to PythonProjectLaunchError.STDIN_MISSING,
        ).forEach { (path, expectedError) ->
            val encodedPath = path.replace("\\", "\\\\")
            val root = project(
                """{"type":"python","main":"main.py","stdin":{"file":"$encodedPath"}}""",
            )
            File(root, "main.py").writeText("print('invalid stdin file')")
            File(root, "fixtures").mkdirs()

            assertProjectError(expectedError) {
                PythonProjectLaunchPolicy.inspect(root, null)
            }
        }
    }

    @Test
    fun stdinFileAboveOneMiBIsRejectedBeforeLaunch() {
        val root = project(
            """{"type":"python","main":"main.py","stdin":{"file":"input.bin"}}""",
        )
        File(root, "main.py").writeText("print('large stdin')")
        File(root, "input.bin").writeBytes(
            ByteArray(PythonProjectLaunchPolicy.MAX_STDIN_SNAPSHOT_BYTES.toInt() + 1),
        )

        assertProjectError(PythonProjectLaunchError.STDIN_TOO_LARGE) {
            PythonProjectLaunchPolicy.inspect(root, null)
        }
    }

    @Test
    fun stdinFileSymbolicLinksAreRejected() {
        val root = project(
            """{"type":"python","main":"main.py","stdin":{"file":"input.txt"}}""",
        )
        File(root, "main.py").writeText("print('linked stdin')")
        val target = File(root, "real.txt").apply { writeText("target") }
        val link = File(root, "input.txt")
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath().fileName)
        } catch (error: Exception) {
            assumeNoException("Symbolic links are unavailable in this test environment", error)
        }

        assertProjectError(PythonProjectLaunchError.STDIN_ESCAPES_ROOT) {
            PythonProjectLaunchPolicy.inspect(root, null)
        }
    }

    @Test
    fun invalidStdinOnANonPythonProjectDoesNotClaimTheLegacyProject() {
        val root = project("""{"type":"javascript","main":"main.js","stdin":true}""")
        val entry = File(root, "main.js").apply { writeText("console.log('legacy')") }

        assertNull(PythonProjectLaunchPolicy.inspect(root, entry))
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
    fun explicitLongRunningModePropagatesWithoutABoundedTimeout() {
        val root = project(
            """{"type":"python","main":"main.py","executionMode":"long-running"}""",
        )
        val entry = File(root, "main.py").apply { writeText("print('long-running')") }

        val admission = requireNotNull(PythonProjectLaunchPolicy.inspect(root, entry))
        val launch = ScriptLaunchSourceFactory.createPythonProjectLaunchForPreview(
            admission = admission,
            pythonPreviewEnabled = true,
        )
        val source = launch.source as PythonProjectSource

        assertEquals(PythonExecutionMode.LONG_RUNNING, admission.executionMode)
        assertEquals(PythonExecutionMode.LONG_RUNNING, source.executionMode)
        assertNull(source.timeoutMillis)
    }

    @Test
    fun longRunningModeRejectsTimeoutsAndUnknownModeNames() {
        listOf(
            """{"type":"python","main":"main.py","executionMode":"long-running","timeout":1}""",
            """{"type":"python","main":"main.py","executionMode":"forever"}""",
        ).forEach { manifest ->
            val root = project(manifest)
            val entry = File(root, "main.py").apply { writeText("print('invalid mode')") }
            assertProjectError(PythonProjectLaunchError.CONFIG_INVALID) {
                PythonProjectLaunchPolicy.inspect(root, entry)
            }
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
            checkNotNull(parentFile).mkdirs()
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
