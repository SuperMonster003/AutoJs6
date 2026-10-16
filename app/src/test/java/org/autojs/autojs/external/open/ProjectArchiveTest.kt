package org.autojs.autojs.external.open

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Project zips handed over by plugins (3-Block Compose folder projects): `main.js` or `project.json` on top or inside
 * the one wrapping folder; unsafe entries and zips without a main script are refused whole; unpacking replaces the
 * zip's paths and keeps other files.
 */
class ProjectArchiveTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((path, text) in entries) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun read(vararg entries: Pair<String, String>) = ProjectArchive.read(zip(*entries).inputStream()).mapValues { String(it.value) }

    @Test
    fun readsProjectsOnTopOrInsideOneFolder() {
        assertEquals(mapOf("main.js" to "a", "res/layout/main.xml" to "<b/>"), read("main.js" to "a", "res/layout/main.xml" to "<b/>"))
        assertEquals(mapOf("project.json" to "{}", "app.js" to "a"), read("project.json" to "{}", "app.js" to "a"))
        assertEquals(mapOf("main.js" to "a", "layout.xml" to "<b/>"), read("demo/main.js" to "a", "demo/layout.xml" to "<b/>", "__MACOSX/demo/._main.js" to "x"))
    }

    @Test
    fun refusesUnsafeOrScriptlessZips() {
        assertThrows(IllegalArgumentException::class.java) { read("main.js" to "a", "../escape.js" to "b") }
        assertThrows(IllegalArgumentException::class.java) { read("main.js" to "a", "/abs.js" to "b") }
        assertThrows(IllegalArgumentException::class.java) { read("main.js" to "a", "c:/drive.js" to "b") }
        assertThrows(IllegalArgumentException::class.java) { read("layout.xml" to "<a/>") }
        assertThrows(IllegalArgumentException::class.java) { read("a/main.js" to "a", "b/main.js" to "b") }
        for (path in listOf("", "/a", "a/../b", "./a", "a//b")) assertFalse(path, ProjectArchive.isSafePath(path))
        assertTrue(ProjectArchive.isSafePath("res/layout/main.xml"))
    }

    @Test
    fun unpackingReplacesTheZipsPathsAndKeepsOtherFiles() {
        val dir = temp.newFolder("demo")
        File(dir, "main.js").writeText("old")
        File(dir, "notes.txt").writeText("mine")
        ProjectArchive.unpack(mapOf("main.js" to "new".toByteArray(), "res/values/strings.xml" to "<r/>".toByteArray()), dir)
        assertEquals("new", File(dir, "main.js").readText())
        assertEquals("mine", File(dir, "notes.txt").readText())
        assertArrayEquals("<r/>".toByteArray(), File(dir, "res/values/strings.xml").readBytes())
    }

    @Test
    fun freeFoldersCountUp() {
        val parent = temp.newFolder("scripts")
        assertEquals(File(parent, "demo"), ProjectArchive.freeFolder(parent, "demo"))
        File(parent, "demo").mkdirs()
        File(parent, "demo-2").mkdirs()
        assertEquals(File(parent, "demo-3"), ProjectArchive.freeFolder(parent, "demo"))
    }
}
