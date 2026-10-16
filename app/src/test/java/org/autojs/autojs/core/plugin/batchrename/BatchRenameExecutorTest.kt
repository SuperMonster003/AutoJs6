package org.autojs.autojs.core.plugin.batchrename

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The rename executor and its journal (plugin roadmap P3.1, D20 / D21) on a real temporary directory. On Windows the
 * JVM tests run on a case-insensitive volume, which makes the case-only cases meaningful there.
 * zh-CN: 在真实临时目录上测试改名执行器及其日志 (插件路线图 P3.1, D20 / D21). Windows 上 JVM 测试运行在不区分大小写的卷上,
 * 仅大小写改名的用例在那里才有实际意义.
 */
class BatchRenameExecutorTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val root: File by lazy { folder.newFolder("work") }
    private val environment = FakeEnvironment()
    private val journal: BatchRenameJournalStore by lazy { BatchRenameJournalStore(File(folder.root, "journal"), log = { logged += it }) }
    private val executor: BatchRenameExecutor by lazy { BatchRenameExecutor(journal, environment) }
    private val logged = mutableListOf<String>()

    private fun file(name: String, text: String = name): File = File(root, name).apply {
        parentFile.mkdirs()
        writeText(text)
    }

    private fun dir(name: String): File = File(root, name).apply { mkdirs() }

    private fun op(source: File, newName: String, overwrite: Boolean = false) =
        BatchRenameOperation(source, newName, source.isDirectory, source.lastModified().takeUnless { source.isDirectory }, source.length().takeUnless { source.isDirectory }, overwrite)

    private fun names(directory: File = root): List<String> = directory.list().orEmpty().sorted()

    private fun BatchRenameJournalRecord.states() = entries.map { it.state }

    @Test
    fun `renames keep their contents and are journaled`() {
        val a = file("a.txt", "alpha")
        val record = executor.execute(listOf(op(a, "b.txt")), BatchRenameSource.SCRIPT)
        assertEquals(listOf("b.txt"), names())
        assertEquals("alpha", File(root, "b.txt").readText())
        assertEquals(BatchRenameBatchState.COMMITTED, record.state)
        assertEquals(record, journal.load(record.batchId))
        assertEquals(listOf(a to File(root, "b.txt")), environment.relocated)
        assertEquals(setOf(root.absoluteFile), environment.notified.single())
    }

    @Test
    fun `swaps, cycles and chains need no particular order`() {
        val swap = executor.execute(listOf(op(file("a", "A"), "b"), op(file("b", "B"), "a")), BatchRenameSource.SCRIPT)
        assertEquals(BatchRenameBatchState.COMMITTED, swap.state)
        assertEquals(listOf("B", "A"), listOf(File(root, "a").readText(), File(root, "b").readText()))
        val cycle = executor.execute(listOf(op(file("1", "one"), "2"), op(file("2", "two"), "3"), op(file("3", "three"), "1")), BatchRenameSource.SCRIPT)
        assertEquals(BatchRenameBatchState.COMMITTED, cycle.state)
        assertEquals(listOf("three", "one", "two"), listOf("1", "2", "3").map { File(root, it).readText() })
        // The chain runs in request order: x -> y needs y moved away first.
        val chain = executor.execute(listOf(op(file("x", "X"), "y"), op(file("y", "Y"), "z")), BatchRenameSource.SCRIPT)
        assertEquals(BatchRenameBatchState.COMMITTED, chain.state)
        assertEquals(listOf("X", "Y"), listOf(File(root, "y").readText(), File(root, "z").readText()))
        assertFalse("no temporary name is left", names().any { it.endsWith(".tmp") })
    }

    @Test
    fun `case-only changes take effect`() {
        val record = executor.execute(listOf(op(file("photo.jpg"), "Photo.jpg")), BatchRenameSource.SCRIPT)
        assertEquals(BatchRenameBatchState.COMMITTED, record.state)
        assertEquals(listOf("Photo.jpg"), names())
    }

    @Test
    fun `children are renamed before their directory and undo restores both`() {
        val parent = dir("album")
        val child = File(parent, "img.jpg").apply { writeText("pixels") }
        val record = executor.execute(listOf(op(parent, "Album 2026"), op(child, "photo.jpg")), BatchRenameSource.SCRIPT)
        assertEquals(BatchRenameBatchState.COMMITTED, record.state)
        assertEquals("pixels", File(root, "Album 2026/photo.jpg").readText())
        val undone = executor.undo(record.batchId)!!
        assertEquals(BatchRenameBatchState.RESTORED, undone.state)
        assertEquals("pixels", File(root, "album/img.jpg").readText())
        assertEquals(listOf("album"), names())
    }

    @Test
    fun `one failure leaves the other renames in place`() {
        file("taken.txt")
        val record = executor.execute(listOf(op(file("a.txt"), "taken.txt"), op(file("b.txt"), "c.txt")), BatchRenameSource.SCRIPT)
        assertEquals(BatchRenameBatchState.PARTIAL, record.state)
        assertEquals(listOf(BatchRenameEntryState.NOT_RENAMED, BatchRenameEntryState.RENAMED), record.states())
        assertEquals(BatchRenameExecutor.REASON_OCCUPIED, record.entries[0].reason)
        assertEquals(listOf("a.txt", "c.txt", "taken.txt"), names())
    }

    @Test
    fun `entries changed after listing are stale`() {
        val a = file("a.txt")
        val operation = op(a, "b.txt")
        a.writeText("changed since the plan")
        val missing = BatchRenameOperation(File(root, "gone.txt"), "x.txt", directory = false)
        val record = executor.execute(listOf(operation, missing, op(file("c.txt"), "d.txt")), BatchRenameSource.SCRIPT)
        assertEquals(listOf(BatchRenameEntryState.STALE, BatchRenameEntryState.STALE, BatchRenameEntryState.RENAMED), record.states())
        assertEquals(listOf(BatchRenameExecutor.REASON_CHANGED, BatchRenameExecutor.REASON_MISSING), record.entries.take(2).map { it.reason })
    }

    @Test
    fun `all or nothing rejects the whole batch`() {
        val record = executor.execute(listOf(op(file("a.txt"), "b.txt"), op(file("c.txt"), "bad/name")), BatchRenameSource.EXPLORER, pluginUid = 10_123, allOrNothing = true)
        assertEquals(BatchRenameBatchState.REJECTED, record.state)
        assertEquals(listOf(BatchRenameExecutor.REASON_REJECTED, BatchRenameExecutor.REASON_INVALID_NAME), record.entries.map { it.reason })
        assertEquals(listOf("a.txt", "c.txt"), names())
        assertEquals(10_123, journal.load(record.batchId)!!.pluginUid)
        assertTrue(environment.notified.isEmpty())
    }

    @Test
    fun `overwrite moves the replaced entry to the Trash and undo brings it back`() {
        file("keep.txt", "old")
        val record = executor.execute(listOf(op(file("new.txt", "new"), "keep.txt", overwrite = true)), BatchRenameSource.SCRIPT)
        assertEquals(BatchRenameBatchState.COMMITTED, record.state)
        assertEquals("new", File(root, "keep.txt").readText())
        assertEquals(1, environment.trash.size)
        val undone = executor.undo(record.batchId)!!
        assertEquals(BatchRenameBatchState.RESTORED, undone.state)
        assertEquals(listOf("new", "old"), listOf(File(root, "new.txt").readText(), File(root, "keep.txt").readText()))
        assertTrue(undone.entries.single().replacedRestored)
    }

    @Test
    fun `undo never overwrites an occupied old name and can be repeated`() {
        val record = executor.execute(listOf(op(file("a.txt", "first"), "b.txt")), BatchRenameSource.SCRIPT)
        file("a.txt", "someone else")
        val blocked = executor.undo(record.batchId)!!
        assertEquals(BatchRenameBatchState.RECOVERY_REQUIRED, blocked.state)
        assertEquals(BatchRenameExecutor.REASON_OCCUPIED, blocked.entries.single().reason)
        assertEquals("someone else", File(root, "a.txt").readText())
        File(root, "a.txt").delete()
        val retried = executor.undo(record.batchId)!!
        assertEquals(BatchRenameBatchState.RESTORED, retried.state)
        assertEquals("first", File(root, "a.txt").readText())
        // Nothing is left to undo.
        assertFalse(retried.undoable)
        assertEquals(BatchRenameBatchState.RESTORED, executor.undo(record.batchId)!!.state)
    }

    @Test
    fun `undo skips entries changed after the rename`() {
        val record = executor.execute(listOf(op(file("a.txt"), "b.txt"), op(file("c.txt"), "d.txt")), BatchRenameSource.SCRIPT)
        File(root, "b.txt").writeText("edited after the rename, longer than before")
        val undone = executor.undo(record.batchId)!!
        assertEquals(BatchRenameBatchState.RECOVERY_REQUIRED, undone.state)
        assertEquals(listOf(BatchRenameEntryState.STALE, BatchRenameEntryState.RESTORED), undone.states())
        assertEquals(listOf("b.txt", "c.txt"), names())
    }

    @Test
    fun `undo of a swap uses temporary names as well`() {
        val record = executor.execute(listOf(op(file("a", "A"), "b"), op(file("b", "B"), "a")), BatchRenameSource.SCRIPT)
        assertEquals(BatchRenameBatchState.RESTORED, executor.undo(record.batchId)!!.state)
        assertEquals(listOf("A", "B"), listOf(File(root, "a").readText(), File(root, "b").readText()))
    }

    @Test
    fun `progress reports every entry`() {
        val reported = mutableListOf<Pair<Int, Int>>()
        executor.execute(listOf(op(file("a"), "b"), op(file("c"), "c"), op(file("d"), "e")), BatchRenameSource.SCRIPT) { done, total, _ -> reported += done to total }
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), reported)
    }

    @Test
    fun `the journal keeps the newest batches and skips damaged files`() {
        val store = BatchRenameJournalStore(File(folder.root, "small"), maxBatches = 3, log = { logged += it })
        val small = BatchRenameExecutor(store, environment, clock = { counter++ })
        val ids = (1..5).map { small.execute(listOf(op(file("f$it"), "g$it")), BatchRenameSource.SCRIPT).batchId }
        assertEquals(ids.takeLast(3).reversed(), store.list().map { it.batchId })
        assertNull(store.load(ids.first()))
        File(folder.root, "small/b_" + "0".repeat(32) + ".json").writeText("{ not json")
        assertEquals(3, store.list().size)
        assertTrue(logged.any { it.contains("unreadable") })
        assertNull(store.load("../../etc/passwd"))
        assertTrue(BatchRenameJournalStore.isValidBatchId(store.newBatchId()))
    }

    @Test
    fun `swapped directories carry their renamed children along`() {
        val a = dir("a")
        val b = dir("b")
        val inA = File(a, "x.txt").apply { writeText("from a") }
        val record = executor.execute(listOf(op(a, "b"), op(b, "a"), op(inA, "y.txt")), BatchRenameSource.SCRIPT)
        assertEquals(BatchRenameBatchState.COMMITTED, record.state)
        assertEquals("from a", File(root, "b/y.txt").readText())
        // Each segment follows at most one rule: a -> b must not continue with b -> a.
        assertEquals(File(root, "b/y.txt").path, record.locationOf(record.entries[2]).path)
        assertEquals(File(root, "a").path, record.relocate(b.path).path)
        assertEquals(File(root, "b/other").path, record.relocate(File(a, "other").path).path)
        val undone = executor.undo(record.batchId)!!
        assertEquals(BatchRenameBatchState.RESTORED, undone.state)
        assertEquals("from a", File(root, "a/x.txt").readText())
        assertEquals(File(root, "a/x.txt").path, undone.locationOf(undone.entries[2]).path)
        assertTrue(undone.entries.all { it.renamed && !it.applied })
    }

    @Test
    fun `scope, owner and the renamed flag survive the journal`() {
        val record = executor.execute(
            listOf(op(file("a.txt"), "b.txt"), op(file("c.txt"), "bad/name")),
            BatchRenameSource.EXPLORER,
            pluginUid = 10_200,
            scope = root,
        )
        val loaded = journal.load(record.batchId)!!
        assertEquals(root.path, loaded.scope)
        assertEquals(listOf(true, false), loaded.entries.map { it.renamed })
        assertEquals(1, loaded.renamedCount)
        assertTrue(loaded.undoable)
        assertNull("an accept hook that declines leaves the batch alone", executor.undo(record.batchId) { false })
        assertEquals(listOf("b.txt", "c.txt"), names())
    }

    @Test
    fun `scripts and every plugin keep their own newest batches`() {
        val store = BatchRenameJournalStore(File(folder.root, "owners"), maxBatches = 2, log = { logged += it })
        val owners = BatchRenameExecutor(store, environment, clock = { counter++ })
        val script = (1..3).map { owners.execute(listOf(op(file("s$it"), "t$it")), BatchRenameSource.SCRIPT).batchId }
        val plugin = (1..3).map { owners.execute(listOf(op(file("p$it"), "q$it")), BatchRenameSource.EXPLORER, pluginUid = 10_300).batchId }
        val other = owners.execute(listOf(op(file("o1"), "o2")), BatchRenameSource.EXPLORER, pluginUid = 10_301).batchId
        assertEquals(
            listOf(other) + plugin.takeLast(2).reversed() + script.takeLast(2).reversed(),
            store.list().map { it.batchId },
        )
    }

    @Test
    fun `the retention follows the setting and trim applies a lowered one at once`() {
        var keep = 3
        val store = BatchRenameJournalStore(File(folder.root, "retention"), log = { logged += it }, retention = { keep })
        val kept = BatchRenameExecutor(store, environment, clock = { counter++ })
        val ids = (1..4).map { kept.execute(listOf(op(file("r$it"), "s$it")), BatchRenameSource.SCRIPT).batchId }
        assertEquals(ids.takeLast(3).reversed(), store.list().map { it.batchId })
        // A lowered setting waits for the next save or for trim. zh-CN: 调低的设置等到下一次保存或 trim 时生效.
        keep = 2
        assertEquals(3, store.list().size)
        store.trim()
        assertEquals(ids.takeLast(2).reversed(), store.list().map { it.batchId })
    }

    @Test
    fun `delete and clear remove batches of any source`() {
        val store = BatchRenameJournalStore(File(folder.root, "cleared"), log = { logged += it })
        val cleared = BatchRenameExecutor(store, environment, clock = { counter++ })
        val script = (1..2).map { cleared.execute(listOf(op(file("c$it"), "d$it")), BatchRenameSource.SCRIPT).batchId }
        val explorer = (1..2).map { cleared.execute(listOf(op(file("e$it"), "f$it")), BatchRenameSource.EXPLORER, pluginUid = 10_400).batchId }
        assertEquals(explorer.reversed() + script.reversed(), store.list().map { it.batchId })
        assertTrue(store.delete(script.first()))
        assertFalse(store.delete(script.first()))
        assertFalse(store.delete("../cleared"))
        assertEquals(2, store.clear(BatchRenameSource.EXPLORER))
        assertEquals(listOf(script.last()), store.list().map { it.batchId })
        assertEquals(1, store.clear())
        assertEquals(emptyList<BatchRenameJournalRecord>(), store.list())
        assertEquals(0, store.clear())
    }

    private var counter = 1_000L

    private class FakeEnvironment : BatchRenameEnvironment {
        val trash = LinkedHashMap<String, File>()
        val relocated = mutableListOf<Pair<File, File>>()
        val notified = mutableListOf<Set<File>>()

        override fun moveToTrash(file: File): String? {
            val id = "t${trash.size}"
            val parked = File(file.parentFile, ".trash-$id")
            return if (file.renameTo(parked)) id.also { trash[it] = parked } else null
        }

        override fun restoreFromTrash(id: String, target: File): Boolean = trash.remove(id)?.renameTo(target) == true

        override fun relocateHistory(from: File, to: File) {
            relocated += from to to
        }

        override fun notifyChanged(directories: Collection<File>) {
            notified += directories.toSet()
        }
    }
}
