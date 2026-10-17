package org.autojs.autojs.model.explorer

import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.shizuku.UserService
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Exercises real Android shell/Os/Binder payload behavior without requesting Root or permissions. */
@RunWith(AndroidJUnit4::class)
class PrivilegedExplorerDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shellProtocolPreservesSpecialNamesAndFollowsDirectoryLinks() = inTestDirectory { base ->
        val root = File(base, "path ' \" ${'$'}(touch INJECTED)\n中文").apply { check(mkdir()) }
        val names = listOf("line\nbreak.js", "single'quote", "double\"quote", "${'$'}(touch INJECTED)", "-leading", "*?[]", "back\\slash", "tab\tname", "文件.txt")
        names.forEach { name ->
            File(root, name).apply {
                writeText("content")
                check(setLastModified(1_700_000_000_000L))
            }
        }
        val hidden = File(root, ".hidden").apply { check(mkdir()) }
        Os.symlink(hidden.absolutePath, File(root, "linked-directory").absolutePath)
        Os.symlink(File(root, "absent-target").absolutePath, File(root, "dangling-link").absolutePath)

        val result = runShellListing(root)
        assertEquals(result.stderr, 0, result.exitCode)
        val listing = PrivilegedExplorerProtocol.parseRootListing(root.absolutePath, result.stdout)
        assertEquals(names.toSet() + setOf(".hidden", "linked-directory", "dangling-link"), listing.entries.map { it.name }.toSet())
        listing.entries.filter { it.name in names }.forEach {
            assertEquals(7L, it.size)
            assertEquals(1_700_000_000_000L, it.modified)
            assertFalse(it.directory)
        }
        assertTrue(listing.entries.single { it.name == "linked-directory" }.directory)
        assertFalse(listing.entries.single { it.name == "dangling-link" }.directory)
        assertFalse("A filename must never be executed", File(root, "INJECTED").exists())
    }

    @Test
    fun shellProtocolDistinguishesAnEmptyDirectoryFromAFailedListing() = inTestDirectory { root ->
        val empty = runShellListing(root)
        assertEquals(empty.stderr, 0, empty.exitCode)
        assertTrue(PrivilegedExplorerProtocol.parseRootListing(root.absolutePath, empty.stdout).entries.isEmpty())
        val missing = runShellListing(File(root, "missing"))
        assertNotEquals("An unavailable directory must not look empty", 0, missing.exitCode)
    }

    @Test
    fun userServicePagesAStableSnapshotAndReleasesIt() = inTestDirectory { root ->
        val expected = (0 until 301).map { "entry-$it ' 中文\n" }.toSet()
        expected.forEach { File(root, it).writeText("data") }
        val service = UserService(context)
        var response = JSONObject(service.listExplorerDirectory(root.absolutePath, "", 0))
        assertFalse(response.toString(), response.has("error"))
        val token = response.getString("token")
        File(root, "created-after-snapshot").writeText("later")
        val actual = mutableSetOf<String>()
        var pages = 0
        while (true) {
            pages++
            assertFalse(response.toString(), response.has("error"))
            assertEquals(expected.size, response.getInt("total"))
            assertTrue(response.toString().length <= PrivilegedExplorerProtocol.MAX_PAGE_CHARS + 16 * 1024)
            val entries = response.getJSONArray("entries")
            assertTrue(entries.length() <= PrivilegedExplorerProtocol.MAX_PAGE_ENTRIES)
            for (index in 0 until entries.length()) assertTrue(actual.add(entries.getJSONObject(index).getString("name")))
            val next = response.getInt("nextOffset")
            if (next == -1) break
            assertEquals(actual.size, next)
            response = JSONObject(service.listExplorerDirectory(root.absolutePath, token, next))
        }
        assertTrue("The fixture must cross page boundaries", pages >= 3)
        assertEquals(expected, actual)
        service.closeExplorerDirectory(token)
        assertTrue(JSONObject(service.listExplorerDirectory(root.absolutePath, token, 0)).has("error"))
    }

    @Test
    fun userServiceBoundsSnapshotsAndRejectsInvalidContinuations() = inTestDirectory { root ->
        val service = UserService(context)
        val firstToken = JSONObject(service.listExplorerDirectory(root.absolutePath, "", 0)).getString("token")
        service.listExplorerDirectory(root.absolutePath, "", 0)
        service.listExplorerDirectory(root.absolutePath, "", 0)
        assertTrue("Old snapshots must be evicted", JSONObject(service.listExplorerDirectory(root.absolutePath, firstToken, 0)).has("error"))
        assertTrue(JSONObject(service.listExplorerDirectory(File(root, "missing").absolutePath, "", 0)).has("error"))
        assertTrue(JSONObject(service.listExplorerDirectory(root.absolutePath, "", 1)).has("error"))
        assertTrue(JSONObject(service.listExplorerDirectory("relative/path", "", 0)).has("error"))
        assertTrue(JSONObject(service.listExplorerDirectory(root.absolutePath, "forged-token", 0)).has("error"))
    }

    @Test
    fun userServiceReportsTheDirectoryLimitInsteadOfReturningPartialData() = inTestDirectory { root ->
        repeat(PrivilegedExplorerProtocol.MAX_ENTRIES + 1) { check(File(root, "entry-$it").createNewFile()) }
        val response = JSONObject(UserService(context).listExplorerDirectory(root.absolutePath, "", 0))
        assertEquals(PrivilegedExplorerAccessException.Reason.TOO_LARGE.name, response.getString("error"))
        assertFalse(response.has("entries"))
    }

    @Test
    fun cancellingAShellReaderDestroysItsProcess() = inTestDirectory { root ->
        val pidFile = File(root, "pid")
        val failure = AtomicReference<Throwable>()
        val reader = thread(name = "ExplorerCancellationTest", isDaemon = true) {
            try {
                val command = "printf '%s' \"${'$'}${'$'}\" > ${PrivilegedExplorerProtocol.quoteShellArgument(pidFile.absolutePath)}; exec /system/bin/sleep 30"
                PrivilegedExplorerProcess.run(listOf("/system/bin/sh", "-c", command))
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        try {
            val start = SystemClock.elapsedRealtime()
            while ((!pidFile.exists() || pidFile.length() == 0L) && SystemClock.elapsedRealtime() - start < 5000L) SystemClock.sleep(10L)
            assertTrue("The shell fixture must start", pidFile.exists() && pidFile.length() > 0L)
            val pid = pidFile.readText().toInt()
            reader.interrupt()
            reader.join(3000L)
            assertFalse("Cancellation must finish promptly", reader.isAlive)
            assertTrue(failure.get() is InterruptedException)
            val cleanupStart = SystemClock.elapsedRealtime()
            while (processExists(pid) && SystemClock.elapsedRealtime() - cleanupStart < 3000L) SystemClock.sleep(10L)
            assertFalse("The cancelled process must be reaped", processExists(pid))
        } finally {
            reader.interrupt()
            reader.join(3000L)
        }
    }

    @Test
    fun nativeDeadlineStopsTheCommandWithoutRootAuthorization() = inTestDirectory { root ->
        val pidFile = File(root, "native-timeout-pid")
        val command = "printf '%s' \"${'$'}${'$'}\" > ${PrivilegedExplorerProtocol.quoteShellArgument(pidFile.absolutePath)}; exec /system/bin/sleep 30"
        val start = SystemClock.elapsedRealtime()
        val result = PrivilegedExplorerProcess.run(
            listOf("/system/bin/sh", "-c", PrivilegedExplorerProtocol.withRootDeadline(command, 1)),
            timeoutMs = 10_000L,
        )
        // Android 7's timeout returns its child's signal status; newer toybox returns 124.
        assertTrue("Native timeout must stop the command: ${result.exitCode}", result.exitCode in setOf(124, 137, 143))
        assertTrue(SystemClock.elapsedRealtime() - start < 5000L)
        assertFalse("The native timeout must reap its command", processExists(pidFile.readText().toInt()))
    }

    @Test
    fun alreadyAuthorizedShizukuCanReadSystemWithoutChangingPermissions() {
        assumeTrue("Shizuku is not already running and authorized", WrappedShizuku.isRunning() && WrappedShizuku.hasPermission())
        val page = PrivilegedExplorerAccess.listDirectory("/system", PrivilegedAccessMode.SHIZUKU)
        assertTrue(page is PrivilegedExplorerPage)
        assertEquals("/system", page.path)
        val entries = page.toList()
        assertTrue(entries.isNotEmpty())
        assertTrue(entries.all { it is PrivilegedExplorerItem })
        assertTrue(entries.all { !it.canCopy() && !it.canMove() && !it.canDelete() && !it.canRename() && !it.isExecutable })
    }

    private fun runShellListing(directory: File) = PrivilegedExplorerProcess.run(
        listOf("/system/bin/sh", "-c", PrivilegedExplorerProtocol.rootCommand(directory.absolutePath)),
    )

    private fun processExists(pid: Int): Boolean = try {
        Os.kill(pid, 0)
        true
    } catch (error: ErrnoException) {
        if (error.errno != OsConstants.ESRCH) throw error
        false
    }

    private inline fun inTestDirectory(action: (File) -> Unit) {
        val root = File(context.cacheDir, "privileged-explorer-test-${UUID.randomUUID()}")
        check(root.mkdir())
        try {
            action(root)
        } finally {
            check(root.deleteRecursively()) { "Unable to clean up the test fixture" }
        }
    }
}
