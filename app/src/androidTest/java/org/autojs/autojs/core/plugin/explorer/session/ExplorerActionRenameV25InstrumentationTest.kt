package org.autojs.autojs.core.plugin.explorer.session

import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.os.BundleCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.batchrename.AndroidBatchRenameEnvironment
import org.autojs.autojs.core.plugin.batchrename.BatchRenameEnvironment
import org.autojs.autojs.core.plugin.batchrename.BatchRenameExecutor
import org.autojs.autojs.core.plugin.batchrename.BatchRenameJournalStore
import org.autojs.autojs.core.plugin.batchrename.BatchRenameOperation
import org.autojs.autojs.core.plugin.batchrename.BatchRenameSource
import org.autojs.autojs.storage.history.TrashRepository
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import org.autojs.plugin.explorer.api.ExplorerActionValues
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Protocol v25: a read-only `targetRename` action renames request targets and the descendants it listed, and undoes
 * its batches, through the host's Batch Rename executor (plugin roadmap P3.1).
 * zh-CN: 协议 v25: 声明 `targetRename` 的只读动作经宿主 Batch Rename 执行器为请求目标及其列出的后代改名, 并撤销其批次.
 */
@RunWith(AndroidJUnit4::class)
class ExplorerActionRenameV25InstrumentationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val roots = ArrayList<File>()
    private val sessions = ArrayList<ExplorerActionHostSession>()
    private val journalDirectory by lazy { newRoot("journal") }
    private val environment = RecordingEnvironment()

    // Batches made within one millisecond would list in random order; the test clock always moves on.
    private val ticks = AtomicLong(System.currentTimeMillis())
    private val executor by lazy { newExecutor() }

    private fun newExecutor() = BatchRenameExecutor(BatchRenameJournalStore(journalDirectory), environment, clock = ticks::incrementAndGet)

    @After
    fun cleanUp() {
        sessions.forEach { session -> runCatching { session.close() } }
        val repository = TrashRepository(context.applicationContext)
        environment.trashIds.mapNotNull(repository::findTrashItem).forEach(repository::deleteTrashItem)
        roots.forEach { it.deleteRecursively() }
    }

    @Test
    fun targetsAndListedDescendantsAreRenamedAndRestored() {
        val root = newRoot("tree")
        val album = File(root, "album").apply { mkdirs() }
        File(album, "img1.jpg").writeText("first")
        File(album, "img2.jpg").writeText("second")
        File(album, "raw").mkdirs()
        File(album, "raw/r.dng").writeText("raw")
        val notes = File(root, "notes.txt").apply { writeText("notes") }
        File(root, "other.txt").writeText("not a target")
        val session = renameSession(root, mapOf("album" to album, "notes" to notes))

        // The parent lists names and kinds only, with the traits of its file system.
        val parentPage = session.listRenameParent(0, 2)
        assertEquals(listOf("album" to ExplorerActionValues.TARGET_DIRECTORY, "notes.txt" to ExplorerActionValues.TARGET_FILE), namesAndKinds(parentPage))
        assertEquals(2, parentPage.getInt(ExplorerActionHostSessionKeys.NEXT_OFFSET))
        assertFalse(parentPage.getBoolean(ExplorerActionHostSessionKeys.COMPLETE))
        assertFalse(parentPage.containsKey(ExplorerActionHostSessionKeys.RELATIVE_PATH))
        assertFalse(parentPage.getBoolean(ExplorerActionHostSessionKeys.RENAME_CASE_INSENSITIVE))
        assertFalse(parentPage.getBoolean(ExplorerActionHostSessionKeys.RENAME_RESTRICTED_CHARACTERS))
        val lastPage = session.listRenameParent(2, 2)
        assertEquals(listOf("other.txt"), namesAndKinds(lastPage).map { it.first })
        assertTrue(lastPage.getBoolean(ExplorerActionHostSessionKeys.COMPLETE))

        // Descendants the session has not listed cannot be named.
        expectIllegalArgument { session.renameEntries(request(entry("album", "img1.jpg", "photo-1.jpg"))) }
        session.listChildren("album", "", 0, 128)
        session.listChildren("album", "raw", 0, 128)

        val first = session.renameEntries(
            request(
                entry("album", "img1.jpg", "photo-1.jpg"),
                entry("album", "raw/r.dng", "r-1.dng"),
                entry("album", "raw", "raw-files"),
                entry("album", "", "Album 2026"),
                entry("notes", "", "notes-2026.txt"),
            ),
        )
        assertEquals(ExplorerActionHostSessionValues.RENAME_BATCH_STATE_COMMITTED, first.getInt(ExplorerActionHostSessionKeys.RENAME_BATCH_STATE))
        assertEquals(5, first.getInt(ExplorerActionHostSessionKeys.RENAME_RENAMED_COUNT))
        assertEquals(0, first.getInt(ExplorerActionHostSessionKeys.RENAME_NOT_RENAMED_COUNT))
        assertEquals(
            listOf("photo-1.jpg", "raw-files/r-1.dng", "raw-files", "", "").map { ExplorerActionHostSessionValues.RENAME_ITEM_STATE_RENAMED to it },
            items(first),
        )
        assertEquals(listOf("Album 2026", "notes-2026.txt", "other.txt"), root.list()!!.sorted())
        assertEquals("raw", File(root, "Album 2026/raw-files/r-1.dng").readText())
        assertEquals(1, environment.notified.get())

        // The session follows: targets keep their IDs at the new paths, and listed descendants keep working.
        assertEquals("notes", readTarget(session, "notes"))
        assertEquals(listOf("r-1.dng"), childNames(session.listChildren("album", "raw-files", 0, 128)))
        val second = session.renameEntries(request(entry("album", "img2.jpg", "photo-2.jpg")))
        assertEquals(listOf(ExplorerActionHostSessionValues.RENAME_ITEM_STATE_RENAMED to "photo-2.jpg"), items(second))
        assertEquals(listOf(batchId(second), batchId(first)), listedBatchIds(session))

        val undoneSecond = session.undoRenameBatch(batchId(second))
        assertEquals(ExplorerActionHostSessionValues.RENAME_BATCH_STATE_RESTORED, undoneSecond.getInt(ExplorerActionHostSessionKeys.RENAME_BATCH_STATE))
        assertEquals(1, undoneSecond.getInt(ExplorerActionHostSessionKeys.RENAME_RESTORED_COUNT))
        val undoneFirst = session.undoRenameBatch(batchId(first))
        assertEquals(ExplorerActionHostSessionValues.RENAME_BATCH_STATE_RESTORED, undoneFirst.getInt(ExplorerActionHostSessionKeys.RENAME_BATCH_STATE))
        assertEquals(
            listOf("img1.jpg", "raw/r.dng", "raw", "", "").map { ExplorerActionHostSessionValues.RENAME_ITEM_STATE_RESTORED to it },
            items(undoneFirst),
        )
        assertEquals(listOf("album", "notes.txt", "other.txt"), root.list()!!.sorted())
        assertEquals(listOf("img1.jpg", "img2.jpg", "raw"), album.list()!!.sorted())
        assertEquals("raw", File(album, "raw/r.dng").readText())
        assertEquals("notes", readTarget(session, "notes"))
        assertEquals(listOf("r.dng"), childNames(session.listChildren("album", "raw", 0, 128)))
        // A restored batch stays restored.
        assertEquals(
            ExplorerActionHostSessionValues.RENAME_BATCH_STATE_RESTORED,
            session.undoRenameBatch(batchId(first)).getInt(ExplorerActionHostSessionKeys.RENAME_BATCH_STATE),
        )
    }

    @Test
    fun staleTakenAndInvalidEntriesKeepTheirNames() {
        val root = newRoot("outcomes")
        val files = listOf("a", "b", "c", "d").associateWith { name -> File(root, "$name.txt").apply { writeText(name) } }
        File(root, "taken.txt").writeText("taken")
        val session = renameSession(root, files)
        files.getValue("c").writeText("changed after the plugin listed it")

        val result = session.renameEntries(
            request(
                entry("a", "", "taken.txt"),
                entry("b", "", "x/y"),
                entry("c", "", "c2.txt"),
                entry("d", "", "d2.txt"),
            ),
        )
        assertEquals(ExplorerActionHostSessionValues.RENAME_BATCH_STATE_PARTIAL, result.getInt(ExplorerActionHostSessionKeys.RENAME_BATCH_STATE))
        assertEquals(1, result.getInt(ExplorerActionHostSessionKeys.RENAME_RENAMED_COUNT))
        assertEquals(3, result.getInt(ExplorerActionHostSessionKeys.RENAME_NOT_RENAMED_COUNT))
        assertEquals(
            listOf(
                ExplorerActionHostSessionValues.RENAME_ITEM_STATE_NOT_RENAMED,
                ExplorerActionHostSessionValues.RENAME_ITEM_STATE_NOT_RENAMED,
                ExplorerActionHostSessionValues.RENAME_ITEM_STATE_STALE,
                ExplorerActionHostSessionValues.RENAME_ITEM_STATE_RENAMED,
            ),
            items(result).map { it.first },
        )
        assertEquals(listOf("a.txt", "b.txt", "c.txt", "d2.txt", "taken.txt"), root.list()!!.sorted())

        // All or nothing: one invalid name rejects the request and renames nothing.
        val rejected = session.renameEntries(
            request(entry("a", "", "a2.txt"), entry("b", "", ".."), allOrNothing = true),
        )
        assertEquals(ExplorerActionHostSessionValues.RENAME_BATCH_STATE_REJECTED, rejected.getInt(ExplorerActionHostSessionKeys.RENAME_BATCH_STATE))
        assertTrue(File(root, "a.txt").exists())

        // Swaps and case-only changes need no particular order.
        val swap = session.renameEntries(request(entry("a", "", "b.txt"), entry("b", "", "a.txt"), entry("d", "", "D2.txt")))
        assertEquals(ExplorerActionHostSessionValues.RENAME_BATCH_STATE_COMMITTED, swap.getInt(ExplorerActionHostSessionKeys.RENAME_BATCH_STATE))
        assertEquals("b", File(root, "a.txt").readText())
        assertEquals("a", File(root, "b.txt").readText())
        assertEquals(listOf("D2.txt", "a.txt", "b.txt", "c.txt", "taken.txt"), root.list()!!.sorted())
        assertEquals("a", readTarget(session, "a"))
        assertFalse("no temporary name is left", root.list()!!.any { it.endsWith(".tmp") })
    }

    @Test
    fun malformedRequestsAreRejectedWithoutRenaming() {
        val root = newRoot("requests")
        val folder = File(root, "folder").apply { mkdirs() }
        File(folder, "inside.txt").writeText("inside")
        val file = File(root, "file.txt").apply { writeText("file") }
        val session = renameSession(root, mapOf("folder" to folder, "file" to file))
        session.listChildren("folder", "", 0, 128)

        expectIllegalArgument { session.renameEntries(Bundle()) }
        expectIllegalArgument { session.renameEntries(request()) }
        expectIllegalArgument { session.renameEntries(request(entry("unknown", "", "x"))) }
        expectIllegalArgument { session.renameEntries(request(entry("file", "", "x"), entry("file", "", "y"))) }
        expectIllegalArgument { session.renameEntries(request(entry("file", "child", "x"))) }
        expectIllegalArgument { session.renameEntries(request(entry("folder", "../file.txt", "x"))) }
        expectIllegalArgument { session.renameEntries(request(entry("folder", "/inside.txt", "x"))) }
        expectIllegalArgument { session.renameEntries(request(Bundle().apply { putString(ExplorerActionHostSessionKeys.RENAME_TARGET_ID, "file") })) }
        expectIllegalArgument { session.queryRenameBatch("") }
        expectIllegalArgument { session.listRenameParent(-1, 10) }
        expectIllegalArgument { session.listRenameParent(0, 0) }
        assertEquals(listOf("file.txt", "folder"), root.list()!!.sorted())
        assertEquals(listOf("inside.txt"), folder.list()!!.toList())
        assertTrue(listedBatchIds(session).isEmpty())
    }

    @Test
    fun overwriteSendsTheReplacedFileToTheTrashAndUndoReturnsIt() {
        val root = newRoot("overwrite")
        val source = File(root, "new.txt").apply { writeText("new") }
        File(root, "keep.txt").writeText("old")
        val session = renameSession(root, mapOf("new" to source))

        val result = session.renameEntries(request(entry("new", "", "keep.txt", overwrite = true)))
        assertEquals(ExplorerActionHostSessionValues.RENAME_BATCH_STATE_COMMITTED, result.getInt(ExplorerActionHostSessionKeys.RENAME_BATCH_STATE))
        assertEquals(listOf("keep.txt"), root.list()!!.toList())
        assertEquals("new", File(root, "keep.txt").readText())
        assertEquals(1, environment.trashIds.size)

        val undone = session.undoRenameBatch(batchId(result))
        assertEquals(ExplorerActionHostSessionValues.RENAME_BATCH_STATE_RESTORED, undone.getInt(ExplorerActionHostSessionKeys.RENAME_BATCH_STATE))
        assertEquals("new", File(root, "new.txt").readText())
        assertEquals("old", File(root, "keep.txt").readText())
    }

    @Test
    fun historyBelongsToThePluginAndParentAndOutlivesTheExecutor() {
        val root = newRoot("history")
        val file = File(root, "a.txt").apply { writeText("a") }
        val first = renameSession(root, mapOf("a" to file))
        val batchId = batchId(first.renameEntries(request(entry("a", "", "b.txt"))))
        first.close()

        // A new journal over the same directory stands in for a restarted process.
        val restarted = newExecutor()
        val renamed = File(root, "b.txt")
        val second = renameSession(root, mapOf("b" to renamed), restarted)
        assertEquals(listOf(batchId), listedBatchIds(second))
        val queried = second.queryRenameBatch(batchId)
        assertEquals(ExplorerActionHostSessionValues.RENAME_BATCH_STATE_COMMITTED, queried.getInt(ExplorerActionHostSessionKeys.RENAME_BATCH_STATE))
        assertEquals(listOf(ExplorerActionHostSessionValues.RENAME_ITEM_STATE_RENAMED to ""), items(queried))

        // Batches of scripts, of other plugins and of other parents stay out of reach.
        val other = File(root, "other.txt").apply { writeText("other") }
        restarted.execute(listOf(BatchRenameOperation(other, "script.txt", directory = false)), BatchRenameSource.SCRIPT)
        restarted.execute(listOf(BatchRenameOperation(File(root, "script.txt"), "plugin.txt", directory = false)), BatchRenameSource.EXPLORER, pluginUid = Int.MAX_VALUE, scope = root.canonicalFile)
        assertEquals(listOf(batchId), listedBatchIds(second))
        val elsewhere = newRoot("elsewhere")
        val stranger = renameSession(elsewhere, mapOf("x" to File(elsewhere, "x.txt").apply { writeText("x") }), restarted)
        assertTrue(listedBatchIds(stranger).isEmpty())
        val unknown = ExplorerActionHostSessionValues.RENAME_BATCH_STATE_UNKNOWN
        assertEquals(unknown, stranger.queryRenameBatch(batchId).getInt(ExplorerActionHostSessionKeys.RENAME_BATCH_STATE))
        assertEquals(unknown, stranger.undoRenameBatch(batchId).getInt(ExplorerActionHostSessionKeys.RENAME_BATCH_STATE))
        assertTrue(renamed.exists())

        assertEquals(
            ExplorerActionHostSessionValues.RENAME_BATCH_STATE_RESTORED,
            second.undoRenameBatch(batchId).getInt(ExplorerActionHostSessionKeys.RENAME_BATCH_STATE),
        )
        assertEquals("a", file.readText())
    }

    @Test
    fun sessionsWithoutTargetRenameRejectTheTransactions() {
        val root = newRoot("denied")
        val file = File(root, "a.txt").apply { writeText("a") }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = root,
            targets = listOf(ExplorerActionHostSessionTarget("a", file)),
            allowCreateOutput = false,
            renameExecutor = { executor },
        ).also(sessions::add)
        expectIllegalState { session.listRenameParent(0, 10) }
        expectIllegalState { session.renameEntries(request(entry("a", "", "b.txt"))) }
        expectIllegalState { session.queryRenameBatch("b_0") }
        expectIllegalState { session.listRenameBatches() }
        expectIllegalState { session.undoRenameBatch("b_0") }
        assertTrue(file.exists())
        expectIllegalArgument {
            ExplorerActionHostSession.create(
                context = context,
                pluginPackageName = context.packageName,
                commonParent = root,
                targets = listOf(ExplorerActionHostSessionTarget("a", file)),
                allowCreateOutput = false,
                allowTargetTrash = true,
                allowTargetRename = true,
            )
        }

        val closed = renameSession(root, mapOf("a" to file))
        closed.close()
        expectIllegalState { closed.renameEntries(request(entry("a", "", "b.txt"))) }
        assertTrue(file.exists())
    }

    @Test
    fun theDefaultWiringJournalsInTheHostAndRefreshesTheFileManager() {
        val root = newRoot("default")
        val file = File(root, "a.txt").apply { writeText("a") }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = root,
            targets = listOf(ExplorerActionHostSessionTarget("a", file)),
            allowCreateOutput = false,
            allowTargetRename = true,
        ).also(sessions::add)
        val batchId = batchId(session.renameEntries(request(entry("a", "", "b.txt"))))
        val journalFile = File(context.noBackupFilesDir, "batch-rename/journals/$batchId.json")
        try {
            assertTrue(journalFile.isFile)
            assertTrue(File(root, "b.txt").exists())
            assertEquals(
                ExplorerActionHostSessionValues.RENAME_BATCH_STATE_RESTORED,
                session.undoRenameBatch(batchId).getInt(ExplorerActionHostSessionKeys.RENAME_BATCH_STATE),
            )
            assertEquals("a", file.readText())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        } finally {
            journalFile.delete()
        }
    }

    private fun newRoot(name: String): File =
        File(context.cacheDir, "explorer-action-v25-$name-${System.nanoTime()}").apply {
            assertTrue(mkdirs())
            roots += this
        }

    private fun renameSession(
        root: File,
        targets: Map<String, File>,
        renameExecutor: BatchRenameExecutor = executor,
    ): ExplorerActionHostSession = ExplorerActionHostSession.create(
        context = context,
        pluginPackageName = context.packageName,
        commonParent = root,
        targets = targets.map { (id, file) -> ExplorerActionHostSessionTarget(id, file) },
        allowCreateOutput = false,
        allowTargetRename = true,
        renameExecutor = { renameExecutor },
    ).also(sessions::add)

    private fun entry(targetId: String, relativePath: String, newName: String, overwrite: Boolean = false) = Bundle().apply {
        putString(ExplorerActionHostSessionKeys.RENAME_TARGET_ID, targetId)
        putString(ExplorerActionHostSessionKeys.RELATIVE_PATH, relativePath)
        putString(ExplorerActionHostSessionKeys.RENAME_NEW_NAME, newName)
        if (overwrite) putBoolean(ExplorerActionHostSessionKeys.RENAME_OVERWRITE, true)
    }

    private fun request(vararg entries: Bundle, allOrNothing: Boolean = false) = Bundle().apply {
        putParcelableArrayList(ExplorerActionHostSessionKeys.RENAME_ENTRIES, ArrayList(entries.toList()))
        if (allOrNothing) putBoolean(ExplorerActionHostSessionKeys.RENAME_ALL_OR_NOTHING, true)
    }

    private fun bundles(bundle: Bundle, key: String): List<Bundle> =
        BundleCompat.getParcelableArrayList(bundle, key, Bundle::class.java).orEmpty()

    private fun items(batch: Bundle): List<Pair<Int, String?>> =
        bundles(batch, ExplorerActionHostSessionKeys.RENAME_ENTRIES).map { item ->
            item.getInt(ExplorerActionHostSessionKeys.RENAME_ITEM_STATE) to
                item.getString(ExplorerActionHostSessionKeys.RENAME_NEW_RELATIVE_PATH)
        }

    private fun namesAndKinds(page: Bundle): List<Pair<String?, Int>> =
        bundles(page, ExplorerActionHostSessionKeys.ITEMS).map { item ->
            item.getString(ExplorerActionHostSessionKeys.DISPLAY_NAME) to item.getInt(ExplorerActionHostSessionKeys.KIND)
        }

    private fun childNames(page: Bundle): List<String?> =
        bundles(page, ExplorerActionHostSessionKeys.ITEMS).map { it.getString(ExplorerActionHostSessionKeys.DISPLAY_NAME) }

    private fun batchId(result: Bundle): String = requireNotNull(result.getString(ExplorerActionHostSessionKeys.RENAME_BATCH_ID))

    private fun listedBatchIds(session: ExplorerActionHostSession): List<String> =
        bundles(session.listRenameBatches(), ExplorerActionHostSessionKeys.RENAME_BATCHES).mapNotNull { batch ->
            assertFalse("summaries carry no entries", batch.containsKey(ExplorerActionHostSessionKeys.RENAME_ENTRIES))
            batch.getString(ExplorerActionHostSessionKeys.RENAME_BATCH_ID)
        }

    private fun readTarget(session: ExplorerActionHostSession, targetId: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(session.openFile(targetId, "")).use { it.readBytes().decodeToString() }

    private inner class RecordingEnvironment : BatchRenameEnvironment {
        private val android by lazy { AndroidBatchRenameEnvironment(context.applicationContext) }
        val trashIds = ArrayList<String>()
        val notified = AtomicInteger()

        override fun moveToTrash(file: File): String? = android.moveToTrash(file)?.also { trashIds += it }

        override fun restoreFromTrash(id: String, target: File): Boolean = android.restoreFromTrash(id, target)

        override fun relocateHistory(from: File, to: File) = android.relocateHistory(from, to)

        override fun notifyChanged(directories: Collection<File>) {
            notified.incrementAndGet()
        }
    }

    private fun expectIllegalState(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalStateException) {
            return
        }
        throw AssertionError("Expected IllegalStateException")
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("Expected IllegalArgumentException")
    }
}
