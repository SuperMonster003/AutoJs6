package org.autojs.autojs.core.plugin.batchrename

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Target enumeration of script plans (plugin roadmap D30). zh-CN: 脚本方案的目标枚举. */
class BatchRenameTargetCollectorTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val root: File by lazy { folder.newFolder("work") }

    private fun file(name: String, text: String = name) = File(root, name).apply {
        parentFile.mkdirs()
        writeText(text)
    }

    private fun dir(name: String) = File(root, name).apply { mkdirs() }

    private fun expectCode(code: String, block: () -> Unit) {
        try {
            block()
        } catch (e: BatchRenameScriptException) {
            assertEquals(e.message, code, e.code)
            return
        }
        fail("expected $code")
    }

    private fun BatchRenameTargets.paths() = items.map { it.file.relativeTo(root).invariantSeparatorsPath to it.depth }

    @Test
    fun `named paths keep their order and folders are walked depth first by name`() {
        val album = dir("album")
        file("album/b.jpg", "bb")
        file("album/A.jpg")
        file("album/raw/r.dng")
        val notes = file("notes.txt")
        val targets = BatchRenameTargetCollector().collect(listOf(notes, album), includeSubfolders = true)
        assertEquals(
            listOf("notes.txt" to 0, "album" to 0, "album/A.jpg" to 1, "album/b.jpg" to 1, "album/raw" to 1, "album/raw/r.dng" to 2),
            targets.paths(),
        )
        assertEquals(listOf(root, album, File(album, "raw")).map { it.absoluteFile }, targets.parents.map { it.directory })
        assertEquals(listOf("album", "notes.txt"), targets.parents[0].existingNames)
        assertEquals(listOf("A.jpg", "b.jpg", "raw"), targets.parents[1].existingNames)
        val b = targets.items.single { it.file.name == "b.jpg" }
        assertEquals(2L, b.length)
        assertEquals("p1", b.parentId)
        assertTrue(targets.items.single { it.file.name == "raw" }.let { it.directory && it.length == -1L })
    }

    @Test
    fun `without subfolders only the named paths take part, and duplicates are dropped`() {
        val album = dir("album")
        file("album/a.jpg")
        val targets = BatchRenameTargetCollector().collect(listOf(album, File(root, "./album"), File(album, "../album")), includeSubfolders = false)
        assertEquals(listOf("album" to 0), targets.paths())
        val both = BatchRenameTargetCollector().collect(listOf(album, File(album, "a.jpg")), includeSubfolders = true)
        assertEquals(listOf("album" to 0, "album/a.jpg" to 1), both.paths())
    }

    @Test
    fun `missing paths, the root and empty lists are INVALID_PATHS`() {
        expectCode(BatchRenameScriptCodes.INVALID_PATHS) { BatchRenameTargetCollector().collect(emptyList(), true) }
        expectCode(BatchRenameScriptCodes.INVALID_PATHS) { BatchRenameTargetCollector().collect(listOf(File(root, "missing.txt")), true) }
        val top = generateSequence(root.absoluteFile) { it.parentFile }.last()
        expectCode(BatchRenameScriptCodes.INVALID_PATHS) { BatchRenameTargetCollector().collect(listOf(top), false) }
    }

    @Test
    fun `limits on items, depth, parents and names are LIMIT_EXCEEDED`() {
        (1..3).forEach { file("many/f$it") }
        expectCode(BatchRenameScriptCodes.LIMIT_EXCEEDED) { BatchRenameTargetCollector(maxItems = 3).collect(listOf(dir("many")), true) }
        assertEquals(1, BatchRenameTargetCollector(maxItems = 3).collect(listOf(dir("many")), false).items.size)
        file("deep/a/b/c.txt")
        expectCode(BatchRenameScriptCodes.LIMIT_EXCEEDED) { BatchRenameTargetCollector(maxDepth = 2).collect(listOf(dir("deep")), true) }
        assertEquals(4, BatchRenameTargetCollector(maxDepth = 3).collect(listOf(dir("deep")), true).items.size)
        expectCode(BatchRenameScriptCodes.LIMIT_EXCEEDED) { BatchRenameTargetCollector(maxParents = 2).collect(listOf(dir("deep")), true) }
        expectCode(BatchRenameScriptCodes.LIMIT_EXCEEDED) { BatchRenameTargetCollector(maxExistingNames = 3).collect(listOf(dir("many")), true) }
    }

    @Test
    fun `volume traits come from each parent and its names`() {
        file("a.txt")
        val seen = mutableListOf<Pair<File, List<String>>>()
        val targets = BatchRenameTargetCollector(traitsOf = { directory, names ->
            seen += directory to names
            BatchRenameVolumeTraits(caseInsensitive = true, restrictedCharacters = true)
        }).collect(listOf(File(root, "a.txt")), true)
        assertEquals(listOf(root.absoluteFile to listOf("a.txt")), seen)
        assertTrue(targets.parents.single().traits.caseInsensitive)
    }
}
