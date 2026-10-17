package org.autojs.autojs.model.explorer

import android.os.ParcelFileDescriptor
import android.os.Environment
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Uses real Android filesystem primitives; it does not request or change Root/Shizuku access. */
@RunWith(AndroidJUnit4::class)
class PrivilegedFileEngineDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun copiesAndOpensNamesWithoutInterpretingShellSyntax() = fixture { base ->
        val source = File(base, "中文 ' \" ${'$'}(touch INJECTED)\nfile.txt").apply { writeText("first\n第二行") }
        val destination = File(base, "copied ' \"\n.txt")
        val response = execute(request("copy", source, destination))
        assertTrue(response.toString(), response.getBoolean("ok"))
        assertEquals(source.readBytes().toList(), destination.readBytes().toList())
        assertFalse(response.getBoolean("cleanupPending"))
        assertEquals(setOf(source.name, destination.name), base.list()!!.toSet())
        PrivilegedFileEngine.openReadDescriptor(request("open", source).toString()).use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                assertEquals(source.readText(), input.reader(Charsets.UTF_8).readText())
            }
        }
        assertFalse(File(base, "INJECTED").exists())
    }

    @Test
    fun copiesDirectoryLinksAsLinksAndDeletesOnlyTheLinks() = fixture { base ->
        val external = File(base, "external").apply { check(mkdir()) }
        val protectedFile = File(external, "keep.txt").apply { writeText("keep me") }
        val source = File(base, "source").apply { check(mkdir()) }
        File(source, "empty").mkdir()
        File(source, "regular").writeText("data")
        Os.symlink(external.absolutePath, File(source, "directory-link").absolutePath)
        Os.symlink(protectedFile.absolutePath, File(source, "file-link").absolutePath)
        Os.symlink("absent", File(source, "dangling-link").absolutePath)
        val destination = File(base, "destination")
        assertOk(execute(request("copy", source, destination)))
        for (name in listOf("directory-link", "file-link", "dangling-link")) {
            assertTrue(OsConstants.S_ISLNK(Os.lstat(File(destination, name).absolutePath).st_mode))
            assertEquals(Os.readlink(File(source, name).absolutePath), Os.readlink(File(destination, name).absolutePath))
        }
        assertOk(execute(request("delete", destination)))
        assertFalse(destination.exists())
        assertEquals("keep me", protectedFile.readText())
        assertTrue(source.exists())
    }

    @Test
    fun movesATreeOnlyAfterTheDestinationIsCompleteAndCleansTemporaries() = fixture { base ->
        val source = File(base, "source").apply { check(mkdir()) }
        val nested = File(source, "nested").apply { check(mkdir()) }
        File(nested, "file.txt").writeText("hello")
        File(source, "empty").mkdir()
        Os.symlink("nested/file.txt", File(source, "relative-link").absolutePath)
        val destination = File(base, "moved")
        assertOk(execute(request("move", source, destination)))
        assertFalse(source.exists())
        assertEquals("hello", File(destination, "nested/file.txt").readText())
        assertEquals("nested/file.txt", Os.readlink(File(destination, "relative-link").absolutePath))
        assertEquals(setOf("moved"), base.list()!!.toSet())
    }

    @Test
    fun renamesARegularFileAndAnEmptyDirectory() = fixture { base ->
        val file = File(base, "before.txt").apply { writeText("rename") }
        val renamed = File(base, "after.txt")
        assertOk(execute(request("rename", file, renamed)))
        assertFalse(file.exists())
        assertEquals("rename", renamed.readText())
        val directory = File(base, "before-dir").apply { check(mkdir()) }
        val renamedDirectory = File(base, "after-dir")
        assertOk(execute(request("rename", directory, renamedDirectory)))
        assertFalse(directory.exists())
        assertTrue(renamedDirectory.isDirectory)
    }

    @Test
    fun existingFilesDirectoriesAndDanglingLinksAreNeverReplaced() = fixture { base ->
        val source = File(base, "source").apply { writeText("source") }
        val file = File(base, "file").apply { writeText("existing") }
        val directory = File(base, "directory").apply { check(mkdir()) }
        val dangling = File(base, "dangling")
        Os.symlink("missing", dangling.absolutePath)
        for (operation in listOf("copy", "move", "rename")) {
            for (destination in listOf(file, directory, dangling)) {
                assertCode("DESTINATION_EXISTS", execute(request(operation, source, destination)))
                assertEquals("source", source.readText())
                assertEquals("existing", file.readText())
                assertTrue(directory.isDirectory)
                assertEquals("missing", Os.readlink(dangling.absolutePath))
            }
        }
        assertEquals(setOf("source", "file", "directory", "dangling"), base.list()!!.toSet())
    }

    @Test
    fun aReplacedSourceCannotBeMovedRenamedDeletedOrOpened() = fixture { base ->
        val source = File(base, "source").apply { writeText("old") }
        val expected = stat(source).getJSONObject("identity")
        // Keep the old inode alive to prevent the filesystem from immediately reusing its number.
        val old = File(base, "old-inode")
        check(source.renameTo(old))
        source.writeText("new")
        for (operation in listOf("move", "rename", "delete")) {
            val request = request(operation, source, File(base, "destination")).put("expected", expected)
            assertCode("SOURCE_CHANGED", execute(request))
            assertEquals("new", source.readText())
        }
        try {
            PrivilegedFileEngine.openReadDescriptor(request("open", source).put("expected", expected).toString())
            fail("Opening a replaced source must fail")
        } catch (error: PrivilegedFileEngine.Failure) {
            assertEquals("SOURCE_CHANGED", error.code)
        }
    }

    @Test
    fun destructiveOperationsRequireAnIdentity() = fixture { base ->
        val source = File(base, "source").apply { writeText("keep") }
        for (operation in listOf("move", "rename", "delete")) {
            assertCode("INVALID_REQUEST", execute(JSONObject().put("operation", operation)
                .put("source", source.absolutePath).put("destination", File(base, "new").absolutePath)))
        }
        assertEquals("keep", source.readText())
    }

    @Test
    fun rootsDotTraversalAndRecursiveDestinationsAreRejected() = fixture { base ->
        val source = File(base, "source").apply { check(mkdir()) }
        File(source, "keep").writeText("data")
        val alias = File(base, "source-alias")
        Os.symlink(source.absolutePath, alias.absolutePath)
        for (path in listOf("/", "//", "${source.absolutePath}/.", "${source.absolutePath}/../source")) {
            assertCode("INVALID_PATH", execute(JSONObject().put("operation", "copy").put("source", path)
                .put("destination", File(base, "other").absolutePath)))
        }
        for (destination in listOf(source, File(source, "nested"), File(alias, "nested"), File(base, "source/../other"))) {
            assertCode("INVALID_PATH", execute(request("copy", source, destination)))
        }
        assertEquals(setOf("keep"), source.list()!!.toSet())
    }

    @Test
    fun renameCannotMoveIntoAnotherParent() = fixture { base ->
        val source = File(base, "source").apply { writeText("keep") }
        val other = File(base, "other").apply { check(mkdir()) }
        assertCode("INVALID_PATH", execute(request("rename", source, File(other, "new"))))
        assertEquals("keep", source.readText())
    }

    @Test
    fun expiredAndCancelledRequestsDoNotMutateTheSource() = fixture { base ->
        val source = File(base, "source").apply { writeText("keep") }
        val destination = File(base, "destination")
        val expired = request("move", source, destination).put("deadlineMillis", System.currentTimeMillis() - 1L)
        assertCode("TIMEOUT", execute(expired))
        val cancelled = request("delete", source)
        val result = AtomicReference<JSONObject>()
        val worker = thread {
            Thread.currentThread().interrupt()
            result.set(execute(cancelled))
            assertTrue(Thread.currentThread().isInterrupted)
        }
        worker.join(5000L)
        assertFalse(worker.isAlive)
        assertCode("CANCELLED", result.get())
        assertEquals("keep", source.readText())
        assertFalse(destination.exists())
    }

    @Test
    fun descriptorsAndExportRejectLinksAndSpecialFiles() = fixture { base ->
        val regular = File(base, "regular").apply { writeText("data") }
        val link = File(base, "link")
        Os.symlink(regular.absolutePath, link.absolutePath)
        val fifo = File(base, "fifo")
        Os.mkfifo(fifo.path, 0x180)
        for (source in listOf(link, fifo)) {
            assertCode("UNSUPPORTED_TYPE", execute(request("export", source, File(base, "exported"))))
            try {
                PrivilegedFileEngine.openReadDescriptor(request("open", source).toString())
                fail("Special files and symlinks must not be opened")
            } catch (error: PrivilegedFileEngine.Failure) {
                assertEquals("UNSUPPORTED_TYPE", error.code)
            }
        }
        assertFalse(File(base, "exported").exists())
    }

    @Test
    fun permissionFailurePreservesTheSourceAndReportsRecoverableStaging() = fixture { base ->
        val source = File(base, "source").apply { writeText("private") }
        val destination = File(base, "destination")
        Os.chmod(source.absolutePath, 0)
        try {
            val response = execute(request("copy", source, destination))
            assertCode("PERMISSION_DENIED", response)
            assertFalse(response.getBoolean("partial"))
            assertTrue(source.exists())
            assertFalse(destination.exists())
            val recovery = response.getJSONArray("recoveryPaths")
            assertTrue(recovery.length() > 0)
            for (index in 0 until recovery.length()) assertTrue(File(recovery.getString(index)).exists())
        } finally {
            Os.chmod(source.absolutePath, 0x180)
        }
        assertEquals("private", source.readText())
    }

    @Test
    fun anExcessivelyDeepTreeIsRejectedBeforeAnyMutation() = fixture { base ->
        val source = File(base, "source").apply { check(mkdir()) }
        var child = source
        repeat(129) { child = File(child, "d").apply { check(mkdir()) } }
        val destination = File(base, "destination")
        assertCode("LIMIT_EXCEEDED", execute(request("copy", source, destination)))
        assertTrue(child.isDirectory)
        assertFalse(destination.exists())
        assertEquals(setOf("source"), base.list()!!.toSet())
    }

    @Test
    fun cancellingAfterPublicationBeginsPreservesSourceAndRecoveryPaths() = fixture { base ->
        val source = File(base, "large-source").also(::writeLargeFile)
        val destination = File(base, "destination")
        val request = request("copy", source, destination)
        val result = AtomicReference<JSONObject>()
        val worker = thread { result.set(execute(request)) }
        try {
            waitUntil(worker) { destination.exists() }
            assertTrue("The destination must become visible before the operation ends", worker.isAlive && destination.exists())
            worker.interrupt()
            worker.join(5000L)
            assertFalse("Cancellation must finish promptly", worker.isAlive)
            val response = result.get()
            assertCode("CANCELLED", response)
            assertTrue(response.toString(), response.getBoolean("partial"))
            assertEquals(32L * 1024L * 1024L, source.length())
            val recovery = response.getJSONArray("recoveryPaths")
            assertTrue(recovery.length() >= 2)
            for (index in 0 until recovery.length()) assertTrue(File(recovery.getString(index)).exists())
        } finally {
            worker.interrupt()
            worker.join(5000L)
        }
    }

    @Test
    fun aDestinationCreatedDuringStagingIsNotOverwritten() = fixture { base ->
        val source = File(base, "large-source").also(::writeLargeFile)
        val destination = File(base, "destination")
        val request = request("copy", source, destination)
        val result = AtomicReference<JSONObject>()
        val worker = thread { result.set(execute(request)) }
        try {
            waitUntil(worker) { base.listFiles().orEmpty().any { it.name.startsWith(".autojs6-copy-") } }
            val descriptor = Os.open(destination.absolutePath, OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_WRONLY, 0x180)
            FileOutputStream(descriptor).use { it.write("other process".toByteArray()) }
            worker.join(10_000L)
            assertFalse(worker.isAlive)
            assertCode("DESTINATION_EXISTS", result.get())
            assertEquals("other process", destination.readText())
            assertEquals(32L * 1024L * 1024L, source.length())
        } finally {
            worker.interrupt()
            worker.join(5000L)
        }
    }

    private fun waitUntil(worker: Thread, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (worker.isAlive && !predicate() && System.nanoTime() < deadline) Thread.sleep(1L)
    }

    @Test
    fun aSameVolumeRenameRetainsTheInodeOwnershipPermissionsAndTimestamp() = fixture { base ->
        val source = File(base, "source").apply {
            writeText("metadata")
            check(setLastModified(1_700_000_000_000L))
        }
        Os.chmod(source.absolutePath, 0x1a0) // 0640
        val before = Os.lstat(source.absolutePath)
        val destination = File(base, "destination")
        val response = execute(request("rename", source, destination))
        assertOk(response)
        assertEquals("rename", response.getString("method"))
        val after = Os.lstat(destination.absolutePath)
        assertEquals(before.st_dev, after.st_dev)
        assertEquals(before.st_ino, after.st_ino)
        assertEquals(before.st_uid, after.st_uid)
        assertEquals(before.st_gid, after.st_gid)
        assertEquals(before.st_mode, after.st_mode)
        assertEquals(before.st_mtime, after.st_mtime)
    }

    @Test
    fun copiedRegularFilesRetainPermissionsAndModificationTime() = fixture { base ->
        val source = File(base, "source").apply {
            writeText("metadata")
            check(setLastModified(1_700_000_000_000L))
        }
        Os.chmod(source.absolutePath, 0x1a0)
        val destination = File(base, "destination")
        assertOk(execute(request("copy", source, destination)))
        val before = Os.lstat(source.absolutePath)
        val after = Os.lstat(destination.absolutePath)
        assertEquals(before.st_mode, after.st_mode)
        assertEquals(before.st_mtime, after.st_mtime)
    }

    @Test
    fun twoSimultaneousMovesNeverReplaceEachOther() = fixture { base ->
        val first = File(base, "first").apply { writeText("first source") }
        val second = File(base, "second").apply { writeText("second source") }
        val destination = File(base, "destination")
        val gate = CountDownLatch(1)
        val requests = listOf(request("move", first, destination), request("move", second, destination))
        val results = requests.map { AtomicReference<JSONObject>() }
        val workers = requests.mapIndexed { index, request ->
            thread {
                gate.await()
                results[index].set(execute(request))
            }
        }
        gate.countDown()
        workers.forEach { it.join(5000L); assertFalse(it.isAlive) }
        val winner = results.indexOfFirst { it.get().getBoolean("ok") }
        assertTrue(results.toString(), winner >= 0)
        assertCode("DESTINATION_EXISTS", results[1 - winner].get())
        val sources = listOf(first, second)
        assertFalse(sources[winner].exists())
        assertTrue(sources[1 - winner].exists())
        assertEquals(if (winner == 0) "first source" else "second source", destination.readText())
    }

    @Test
    fun privateToSharedCrossFilesystemMovesAreRefusedWithoutDeletingAnything() = fixture { base ->
        sharedFixture(Environment.getExternalStorageDirectory()) { external ->
            assumeTrue(Os.lstat(base.absolutePath).st_dev != Os.lstat(external.absolutePath).st_dev)
            val source = File(base, "source").apply { writeText("private data") }
            val destination = File(external, "destination")
            assertCode("UNSUPPORTED_MOVE", execute(request("move", source, destination)))
            assertEquals("private data", source.readText())
            assertFalse(destination.exists())
            assertTrue(external.list()!!.isEmpty())
        }
    }

    @Test
    fun crossVolumeSharedStorageMovesVerifyCopyBeforeRemovingSource() {
        val primary = requireNotNull(context.getExternalFilesDir(null))
        val removable = context.getExternalFilesDirs(null).filterNotNull().firstOrNull {
            it.isDirectory && it.canWrite() &&
                Os.lstat(it.absolutePath).st_dev != Os.lstat(primary.absolutePath).st_dev
        }
        assumeTrue("A writable second shared-storage volume is required", removable != null)
        sharedFixture(primary) { from ->
            sharedFixture(requireNotNull(removable)) { to ->
                val source = File(from, "source").apply { check(mkdir()) }
                val nested = File(source, "nested").apply { check(mkdir()) }
                File(nested, "文件.txt").apply {
                    writeText("shared storage contents")
                    check(setLastModified(1_700_000_000_000L))
                }
                // Force a visible timestamp difference when FUSE updates directory mtime on
                // quarantine rename; relying on a second boundary made this bug intermittent.
                check(source.setLastModified(1_700_000_000_000L))
                val destination = File(to, "destination")
                val response = execute(request("move", source, destination))
                assertOk(response)
                assertFalse(source.exists())
                assertEquals("shared storage contents", File(destination, "nested/文件.txt").readText())
                assertEquals(1_700_000_000_000L, File(destination, "nested/文件.txt").lastModified())
                assertFalse(response.getBoolean("cleanupPending"))
                assertTrue(from.list()!!.isEmpty())
                assertEquals(setOf("destination"), to.list()!!.toSet())
            }
        }
    }

    @Test
    fun sharedStorageNativeMovesPreserveUnicodeNamesAcrossParents() {
        val volumes = context.getExternalFilesDirs(null).filterNotNull().filter { it.isDirectory && it.canWrite() }
        for (volume in volumes) {
            sharedFixture(volume) { base ->
                val from = File(base, "from").apply { check(mkdir()) }
                val to = File(base, "to").apply { check(mkdir()) }
                val source = File(from, "文件😀.txt").apply {
                    writeText("unicode native rename")
                    check(setLastModified(1_700_000_000_000L))
                }
                val destination = File(to, "更名🌏.txt")
                assertOk(execute(request("move", source, destination)))
                assertFalse(source.exists())
                assertEquals("unicode native rename", destination.readText())
                assertEquals(1_700_000_000_000L, destination.lastModified())
                val directory = File(from, "directory").apply { check(mkdir()) }
                File(directory, "child").writeText("directory data")
                check(directory.setLastModified(1_700_000_000_000L))
                val movedDirectory = File(to, "moved-directory")
                assertOk(execute(request("move", directory, movedDirectory)))
                assertFalse(directory.exists())
                assertEquals("directory data", File(movedDirectory, "child").readText())
            }
        }
    }

    private fun writeLargeFile(file: File) {
        val block = ByteArray(128 * 1024) { (it and 0xff).toByte() }
        file.outputStream().use { output -> repeat(256) { output.write(block) } }
    }

    private fun request(operation: String, source: File, destination: File? = null) = JSONObject()
        .put("operation", operation).put("source", source.absolutePath)
        .put("expected", stat(source).getJSONObject("identity"))
        .apply { destination?.let { put("destination", it.absolutePath) } }

    private fun stat(file: File) = execute(JSONObject().put("operation", "stat").put("source", file.absolutePath)).also(::assertOk)
    private fun execute(request: JSONObject) = JSONObject(PrivilegedFileEngine.execute(request.toString()))
    private fun assertOk(response: JSONObject) = assertTrue(response.toString(), response.getBoolean("ok"))
    private fun assertCode(code: String, response: JSONObject) {
        assertFalse(response.toString(), response.getBoolean("ok"))
        assertEquals(response.toString(), code, response.getString("code"))
    }

    private inline fun fixture(action: (File) -> Unit) {
        val root = File(context.cacheDir, "privileged-file-engine-${UUID.randomUUID()}")
        check(root.mkdir())
        try {
            action(root)
        } finally {
            removeFixture(root)
        }
    }

    private inline fun sharedFixture(storage: File, action: (File) -> Unit) {
        val root = File(storage, ".autojs6-privileged-engine-test-${UUID.randomUUID()}")
        assumeTrue("Shared storage is not writable", root.mkdir())
        try {
            action(root)
        } finally {
            removeFixture(root)
        }
    }

    private fun removeFixture(file: File) {
        val stat = Os.lstat(file.absolutePath)
        if (OsConstants.S_ISDIR(stat.st_mode)) file.listFiles()!!.forEach(::removeFixture)
        Os.remove(file.absolutePath)
    }
}
