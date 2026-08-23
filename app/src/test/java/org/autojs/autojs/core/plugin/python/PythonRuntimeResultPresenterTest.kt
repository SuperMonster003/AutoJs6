package org.autojs.autojs.core.plugin.python

import org.autojs.autojs.script.PythonFileSource
import org.autojs.autojs.script.PythonProjectSource
import org.autojs.plugin.python.runtime.api.PythonExecutionResult
import org.autojs.plugin.python.runtime.api.PythonOutputArtifact
import org.autojs.plugin.python.runtime.api.PythonSha256
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PythonRuntimeResultPresenterTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun structuredJsonIsPrintedWithoutCreatingAnArtifactDirectory() {
        val root = temporaryFolder.newFolder()

        val presentation = PythonRuntimeResultPresenter.present(
            root,
            executionId = 7,
            result = result(structuredJson = "{\"ok\":true}"),
        )

        assertEquals(listOf("[result] {\"ok\":true}"), presentation.consoleLines)
        assertNull(presentation.artifactDirectory)
        assertTrue(presentation.artifactFiles.isEmpty())
        assertFalse(File(root, PythonRuntimeResultPresenter.OUTPUT_DIRECTORY_NAME).exists())
    }

    @Test
    fun nestedArtifactsArePublishedExactlyAndReportedWithAbsolutePaths() {
        val root = temporaryFolder.newFolder()
        val first = "binary\u0000payload".toByteArray()
        val second = ByteArray(0)
        val runtimeResult = result(
            structuredJson = "{\"artifact\":true}",
            artifacts = listOf(
                "reports/result.bin" to first,
                "empty.txt" to second,
            ),
        )

        val presentation = PythonRuntimeResultPresenter.present(root, 42, runtimeResult)

        val expectedDirectory = File(
            root,
            "${PythonRuntimeResultPresenter.OUTPUT_DIRECTORY_NAME}/" +
                "execution-42-${PythonRuntimeHostTestFixtures.requestId}",
        ).absoluteFile
        assertEquals(expectedDirectory, presentation.artifactDirectory)
        assertEquals(
            listOf(File(expectedDirectory, "reports/result.bin"), File(expectedDirectory, "empty.txt")),
            presentation.artifactFiles,
        )
        assertArrayEquals(first, presentation.artifactFiles[0].readBytes())
        assertArrayEquals(second, presentation.artifactFiles[1].readBytes())
        assertEquals(
            listOf(
                "[result] {\"artifact\":true}",
                "[artifact] ${presentation.artifactFiles[0].absolutePath}",
                "[artifact] ${presentation.artifactFiles[1].absolutePath}",
            ),
            presentation.consoleLines,
        )
        assertTrue(
            File(root, PythonRuntimeResultPresenter.OUTPUT_DIRECTORY_NAME)
                .listFiles()
                .orEmpty()
                .none { it.name.startsWith(".") },
        )
    }

    @Test
    fun anExistingExecutionDirectoryIsNeverOverwritten() {
        val root = temporaryFolder.newFolder()
        val runtimeResult = result(artifacts = listOf("result.txt" to "first".toByteArray()))
        val first = PythonRuntimeResultPresenter.present(root, 9, runtimeResult)
        val published = first.artifactFiles.single()
        assertEquals("first", published.readText())

        val error = assertThrows(PythonRuntimeHostException::class.java) {
            PythonRuntimeResultPresenter.present(
                root,
                9,
                result(artifacts = listOf("result.txt" to "replacement".toByteArray())),
            )
        }

        assertEquals(PythonRuntimeHostError.RESULT_PUBLICATION_FAILED, error.error)
        assertEquals("first", published.readText())
        assertEquals(1, requireNotNull(first.artifactDirectory?.parentFile).listFiles().orEmpty().size)
    }

    @Test
    fun unsafeOrConflictingPathsPublishNoPartialDirectory() {
        listOf(
            listOf("../escape.bin" to "escape".toByteArray()),
            listOf(
                "node/child.bin" to "child".toByteArray(),
                "node" to "prefix-conflict".toByteArray(),
            ),
        ).forEachIndexed { index, artifacts ->
            val root = temporaryFolder.newFolder("case-$index")

            val error = assertThrows(PythonRuntimeHostException::class.java) {
                PythonRuntimeResultPresenter.present(root, index, result(artifacts = artifacts))
            }

            assertEquals(PythonRuntimeHostError.RESULT_PUBLICATION_FAILED, error.error)
            assertFalse(File(root.parentFile, "escape.bin").exists())
            val container = File(root, PythonRuntimeResultPresenter.OUTPUT_DIRECTORY_NAME)
            assertTrue(container.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun sourceKindSelectsProjectRootOrSingleFileDirectory() {
        val projectRoot = temporaryFolder.newFolder("project")
        val packageRoot = File(projectRoot, "pkg").apply { assertTrue(mkdirs()) }
        val entry = File(packageRoot, "main.py").apply { writeText("pass\n") }
        val project = PythonProjectSource.create(projectRoot, entry)
        val single = PythonFileSource(entry)

        assertEquals(projectRoot.canonicalFile, PythonRuntimeResultPresenter.outputBaseDirectory(project))
        assertEquals(packageRoot.canonicalFile, PythonRuntimeResultPresenter.outputBaseDirectory(single))
    }

    private fun result(
        structuredJson: String? = null,
        artifacts: List<Pair<String, ByteArray>> = emptyList(),
    ): PythonRuntimeExecutionResult {
        val metadata = artifacts.mapIndexed { index, (path, bytes) ->
            PythonOutputArtifact(
                path = path,
                descriptorIndex = index,
                declaredLengthBytes = bytes.size.toLong(),
                sha256 = PythonSha256.digest(bytes),
            )
        }
        val terminal = PythonExecutionResult(
            requestId = PythonRuntimeHostTestFixtures.requestId,
            exitCode = 0,
            elapsedMillis = 1,
            stdoutBytes = 0,
            stderrBytes = 0,
            stdoutChunks = 0,
            stderrChunks = 0,
            structuredJson = structuredJson,
            outputArtifacts = metadata,
        )
        val hostArtifacts = metadata.zip(artifacts).map { (artifact, value) ->
            PythonRuntimeOutputArtifact(artifact.path, artifact.sha256, value.second)
        }
        return PythonRuntimeExecutionResult(terminal, hostArtifacts)
    }
}
