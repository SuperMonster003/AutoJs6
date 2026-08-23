package org.autojs.autojs.core.plugin.python

import org.autojs.autojs.script.PythonFileSource
import org.autojs.autojs.script.PythonProjectSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PythonHostFileScopeTest {
    @Test
    fun bindsProjectsAndStandaloneSourcesToTheirSemanticHostDirectories() = withTempRoot { root ->
        val standaloneDirectory = File(root, "standalone").apply { assertTrue(mkdirs()) }
        val standalone = File(standaloneDirectory, "main.py").apply { writeText("pass\n") }
        assertEquals(
            standaloneDirectory.canonicalFile,
            PythonHostFilePolicy.rootFor(PythonFileSource(standalone)),
        )

        val project = File(root, "project").apply { assertTrue(mkdirs()) }
        val packageDirectory = File(project, "pkg").apply { assertTrue(mkdirs()) }
        val entry = File(packageDirectory, "main.py").apply { writeText("pass\n") }
        assertEquals(
            project.canonicalFile,
            PythonHostFilePolicy.rootFor(PythonProjectSource.create(project, entry)),
        )
    }

    @Test
    fun readsWritesAndListsOnlyBoundedUtf8Content() = withTempRoot { root ->
        val scope = PythonHostFileScope(root)
        File(root, "seed.txt").writeText("seed", Charsets.UTF_8)
        File(root, "nested").apply { assertTrue(mkdir()) }

        assertEquals("seed", scope.readText("seed.txt"))
        scope.writeText("written.txt", "写入")
        assertEquals("写入", File(root, "written.txt").readText(Charsets.UTF_8))
        assertTrue(scope.exists("."))
        assertTrue(scope.exists("written.txt"))
        assertFalse(scope.exists("missing.txt"))
        assertTrue(scope.isFile("written.txt"))
        assertTrue(scope.isDirectory("."))
        assertTrue(scope.isDirectory("nested"))
        assertEquals(listOf("nested", "seed.txt", "written.txt"), scope.list("."))
    }

    @Test
    fun rejectsTraversalWrongTypesMissingParentsInvalidUtf8AndLimits() = withTempRoot { root ->
        val scope = PythonHostFileScope(root)
        File(root, "directory").apply { assertTrue(mkdir()) }
        File(root, "invalid.txt").writeBytes(byteArrayOf(0x80.toByte()))
        File(root, "large.txt").writeBytes(ByteArray(PythonHostFilePolicy.MAX_TEXT_BYTES + 1))
        repeat(PythonHostFilePolicy.MAX_LIST_ENTRIES + 1) { index ->
            File(root, "entry-$index").writeText("")
        }

        assertThrows(IllegalArgumentException::class.java) { scope.readText("../outside.txt") }
        assertThrows(IllegalArgumentException::class.java) { scope.readText("C:relative.txt") }
        assertThrows(IllegalArgumentException::class.java) { scope.readText("directory\\entry.txt") }
        assertThrows(IllegalArgumentException::class.java) { scope.readText("entry\u0085.txt") }
        assertThrows(PythonHostCapabilityPathNotFoundException::class.java) {
            scope.readText("missing.txt")
        }
        assertThrows(PythonHostCapabilityPathNotFoundException::class.java) {
            scope.writeText("missing/child.txt", "value")
        }
        assertThrows(PythonHostCapabilityPathTypeException::class.java) {
            scope.readText("directory")
        }
        assertThrows(PythonHostCapabilityFileEncodingException::class.java) {
            scope.readText("invalid.txt")
        }
        assertThrows(PythonHostCapabilityResultLimitException::class.java) {
            scope.readText("large.txt")
        }
        assertThrows(PythonHostCapabilityResultLimitException::class.java) { scope.list(".") }
    }

    private fun withTempRoot(action: (File) -> Unit) {
        val root = Files.createTempDirectory("python-host-files-").toFile()
        try {
            action(root)
        } finally {
            assertTrue("Unable to remove Python Host file test root: $root", root.deleteRecursively())
        }
    }
}
