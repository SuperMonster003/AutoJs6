package org.autojs.autojs.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NodeBridgeWorkingDirectoryTest {

    @Test
    fun anExistingDirectoryHasNoProblem() = withTempRoot { root ->
        assertNull(NodeBridgeWorkingDirectory.problem(root.path) { error("storage access is not consulted for an existing directory") })
        assertNull(NodeBridgeWorkingDirectory.error(root.path, "files", "write") { true })
    }

    @Test
    fun aMissingDirectoryNamesThePathAndTheWayOut() = withTempRoot { root ->
        val missing = File(root, "Scripts").path
        val message = NodeBridgeWorkingDirectory.problem(missing) { true }!!
        assertTrue(message, message.contains("$missing does not exist"))
        assertTrue(message, message.contains("Working directory path"))
    }

    @Test
    fun aMissingDirectoryWithoutStorageAccessBlamesThePermission() = withTempRoot { root ->
        val message = NodeBridgeWorkingDirectory.problem(File(root, "Scripts").path) { false }!!
        assertTrue(message, message.contains("has no storage access"))
        assertTrue(message, message.contains("All files access"))
    }

    @Test
    fun aFileInPlaceOfTheDirectoryIsReportedAsSuch() = withTempRoot { root ->
        val file = File(root, "Scripts").apply { writeText("not a directory") }
        val message = NodeBridgeWorkingDirectory.problem(file.path) { true }!!
        assertTrue(message, message.contains("is a file, not a directory"))
    }

    @Test
    fun aBlankPathAsksForAWorkingDirectory() {
        val message = NodeBridgeWorkingDirectory.problem("  ") { true }!!
        assertTrue(message, message.contains("no working directory set"))
    }

    @Test
    fun theBridgeErrorCarriesItsOwnCodeAndTheProviderCategory() = withTempRoot { root ->
        val missing = File(root, "Scripts").path
        val error = NodeBridgeWorkingDirectory.error(missing, "engines", "execScript") { true }
        assertNotNull(error)
        assertEquals(NodeRuntimeErrorCodes.WORKING_DIRECTORY_UNAVAILABLE, error!!.code)
        assertEquals(NodeBridgeErrorCategory.PROVIDER_FAILED, error.category)
        assertEquals("engines", error.module)
        assertEquals("execScript", error.method)
        assertTrue(error.message, error.message.startsWith("engines.execScript: the AutoJs6 working directory $missing does not exist"))
    }

    private fun withTempRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("node-bridge-working-directory").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
