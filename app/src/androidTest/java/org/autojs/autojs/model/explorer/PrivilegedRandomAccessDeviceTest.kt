package org.autojs.autojs.model.explorer

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.shizuku.IUserService
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** App-owned sparse fixtures exercise Root transport without su; Shizuku uses existing authorization. */
@RunWith(AndroidJUnit4::class)
class PrivilegedRandomAccessDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun packagedRootReaderSeeksBothDirectionsAcrossFourGiBWithoutStaging() = fixture { root ->
        val source = sparse(root)
        rootOpen(source).use { handle ->
            assertEquals(LARGE_SIZE, handle.size)
            assertEquals(LARGE_SIZE, handle.identity.getLong("size"))
            assertMarker(handle, FAR_OFFSET, "BEYOND4G")
            assertMarker(handle, 0L, "HEAD")
            assertMarker(handle, LARGE_SIZE - 4, "TAIL")
            val zeroes = ByteArray(4096) { -1 }
            assertEquals(zeroes.size, handle.readAt(FAR_OFFSET - 8192, zeroes, zeroes.size))
            assertTrue(zeroes.all { it == 0.toByte() })
            assertEquals(0, handle.readAt(LARGE_SIZE, zeroes, zeroes.size))
            assertEquals(0, handle.readAt(Long.MAX_VALUE, zeroes, zeroes.size))
            handle.checkAccess()
            val mutable = handle.identity.put("size", 0)
            assertEquals(0L, mutable.getLong("size"))
            assertEquals(LARGE_SIZE, handle.identity.getLong("size"))
        }
        assertEquals(LARGE_SIZE, source.length())
        assertEquals(setOf(source.name), root.list()!!.toSet())
    }

    @Test
    fun requestsAreBoundedAndFinalReadsCanBeShort() = fixture { root ->
        val source = sparse(root)
        rootOpen(source).use { handle ->
            val oversized = ByteArray(PrivilegedRandomAccess.MAX_CHUNK_BYTES * 2) { 7 }
            assertEquals(PrivilegedRandomAccess.MAX_CHUNK_BYTES, handle.readAt(8192, oversized, oversized.size))
            assertTrue(oversized.take(PrivilegedRandomAccess.MAX_CHUNK_BYTES).all { it == 0.toByte() })
            assertTrue(oversized.drop(PrivilegedRandomAccess.MAX_CHUNK_BYTES).all { it == 7.toByte() })
            val tail = ByteArray(1024)
            assertEquals(4, handle.readAt(LARGE_SIZE - 4, tail, tail.size))
            assertEquals("TAIL", tail.copyOf(4).toString(Charsets.UTF_8))
            assertEquals(0, handle.readAt(0, tail, 0))
        }
    }

    @Test
    fun invalidOffsetsAndLengthsFailBeforeTouchingTheConnection() = fixture { root ->
        val source = File(root, "file").apply { writeText("content") }
        rootOpen(source).use { handle ->
            val buffer = ByteArray(8)
            expectArgument { handle.readAt(-1, buffer, 1) }
            expectArgument { handle.readAt(0, buffer, -1) }
            expectArgument { handle.readAt(0, buffer, 9) }
            assertMarker(handle, 0, "content")
        }
    }

    @Test
    fun quotedAndUnicodePathsAreOnlyTransportData() = fixture { root ->
        val source = File(root, "中文 😀 ' \" ${'$'}(touch INJECTED)\n.bin").apply { writeText("binary\u0000content") }
        rootOpen(source).use { handle -> assertMarker(handle, 0, "binary\u0000content") }
        assertFalse(File(root, "INJECTED").exists())
        assertEquals(setOf(source.name), root.list()!!.toSet())
    }

    @Test
    fun rootOpenRejectsStaleIdentitySymlinksAndSpecialNodes() = fixture { root ->
        val source = File(root, "file").apply { writeText("keep") }
        val identity = JSONObject(PrivilegedFileEngine.execute(JSONObject().put("operation", "stat").put("source", source.path).toString()))
            .getJSONObject("identity").put("ino", -1)
        expectFailure("SOURCE_CHANGED") { rootOpen(source, identity).close() }
        val link = File(root, "link").also { Os.symlink(source.path, it.path) }
        val fifo = File(root, "fifo").also { Os.mkfifo(it.path, 0x180) }
        for (special in listOf(link, fifo, root)) expectFailure("UNSUPPORTED_TYPE") { rootOpen(special).close() }
        assertEquals("keep", source.readText())
    }

    @Test
    fun rootOpenPreservesPermissionDenialAsAStructuredFailure() = fixture { root ->
        val source = File(root, "file").apply { writeText("keep") }
        Os.chmod(source.path, 0)
        try { expectFailure("PERMISSION_DENIED") { rootOpen(source).close() } }
        finally { Os.chmod(source.path, 0x180) }
        assertEquals("keep", source.readText())
    }

    @Test
    fun sourceSizeChangeFailsClosedEvenForAnEofRequest() = fixture { root ->
        val source = File(root, "file").apply { writeText("before") }
        val handle = rootOpen(source)
        source.appendText(" external")
        expectFailure("SOURCE_CHANGED") { handle.readAt(Long.MAX_VALUE, ByteArray(8), 8) }
        expectFailure("ACCESS_UNAVAILABLE") { handle.checkAccess() }
        handle.close()
        assertEquals("before external", source.readText())
    }

    @Test
    fun pausedRootReaderKeepsItsDescriptorAndCloseTerminatesItsHelper() = fixture { root ->
        val source = File(root, "file").apply { writeText("content") }
        val handle = rootOpen(source)
        val pid = PrivilegedRandomAccess.processIdForTest(handle)
        assertTrue(pid > 0)
        assertMarker(handle, 0, "content")
        Thread.sleep(1200)
        assertMarker(handle, 0, "content")
        handle.close()
        expectFailure("ACCESS_UNAVAILABLE") { handle.readAt(0, ByteArray(7), 7) }
        val deadline = System.nanoTime() + 4_000_000_000L
        while (processExists(pid) && System.nanoTime() < deadline) Thread.sleep(20)
        assertFalse("The idle reader process must terminate after close", processExists(pid))
    }

    @Test
    fun aDisconnectedHelperIsNotRestarted() = fixture { root ->
        val source = File(root, "file").apply { writeText("content") }
        val handle = rootOpen(source)
        val pid = PrivilegedRandomAccess.processIdForTest(handle)
        Os.kill(pid, OsConstants.SIGKILL)
        expectFailure("ACCESS_UNAVAILABLE") { handle.readAt(0, ByteArray(7), 7) }
        handle.close()
        assertEquals("content", source.readText())
    }

    @Test
    fun incompleteAndOversizedResponsesNeverModifyTheCallersBuffer() = fixture { root ->
        val source = File(root, "file").apply { writeText("content") }
        for (mode in listOf("oversized", "truncated")) {
            val handle = probe(source, mode)
            val buffer = ByteArray(32) { 0x55 }
            expectFailure("ACCESS_UNAVAILABLE") { handle.readAt(0, buffer, buffer.size) }
            assertTrue(buffer.all { it == 0x55.toByte() })
            handle.close()
        }
    }

    @Test
    fun anUnresponsiveReadHasADeadlineAndLeavesTheOutputBufferUntouched() = fixture { root ->
        val source = File(root, "file").apply { writeText("content") }
        val handle = probe(source, "stall", timeout = 3000)
        val buffer = ByteArray(32) { 0x55 }
        val before = System.nanoTime()
        expectFailure("TIMEOUT") { handle.readAt(0, buffer, buffer.size) }
        assertTrue(TimeUnitNanos.toMillis(System.nanoTime() - before) < 6000)
        assertTrue(buffer.all { it == 0x55.toByte() })
        handle.close()
    }

    @Test
    fun closingAnInFlightReadReleasesTheWaitingCallerImmediately() = fixture { root ->
        val source = File(root, "file").apply { writeText("content") }
        val handle = probe(source, "stall")
        val buffer = ByteArray(32) { 0x55 }
        val result = java.util.concurrent.atomic.AtomicReference<Throwable>()
        val reader = thread {
            try { handle.readAt(0, buffer, buffer.size); result.set(AssertionError("Read unexpectedly completed")) }
            catch (error: Throwable) { result.set(error) }
        }
        Thread.sleep(200)
        handle.close()
        reader.join(1500)
        assertFalse("Closing the handle must release the waiting provider callback", reader.isAlive)
        assertTrue(result.get().toString(), result.get() is PrivilegedFileOperationException)
        assertTrue(buffer.all { it == 0x55.toByte() })
    }

    @Test
    fun concurrentOffsetReadsRemainIndependent() = fixture { root ->
        val source = sparse(root)
        rootOpen(source).use { handle ->
            val failures = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
            val readers = listOf(0L to "HEAD", FAR_OFFSET to "BEYOND4G", LARGE_SIZE - 4 to "TAIL").map { (offset, marker) ->
                thread { try { repeat(10) { assertMarker(handle, offset, marker) } } catch (error: Throwable) { failures += error } }
            }
            readers.forEach { it.join(15_000) }
            assertTrue(readers.none { it.isAlive })
            assertTrue(failures.joinToString("\n"), failures.isEmpty())
        }
    }

    @Test
    fun authorizedShizukuReadsASparseFilePastFourGiBAndSeeksBackwards() = shizukuFixture { service, root ->
        val path = "$root/sparse ' 中文.bin"
        shell(service, "dd if=/dev/zero of=${quote(path)} bs=1 count=0 seek=$LARGE_SIZE 2>/dev/null")
        for ((offset, marker) in listOf(0L to "HEAD", FAR_OFFSET to "BEYOND4G", LARGE_SIZE - 4 to "TAIL")) {
            shell(service, "printf '%s' ${quote(marker)} | dd of=${quote(path)} bs=1 seek=$offset conv=notrunc 2>/dev/null")
        }
        PrivilegedRandomAccess.open(context, PrivilegedAccessMode.SHIZUKU, path).use { handle ->
            assertEquals(LARGE_SIZE, handle.size)
            assertMarker(handle, FAR_OFFSET, "BEYOND4G")
            assertMarker(handle, LARGE_SIZE - 4, "TAIL")
            assertMarker(handle, 0, "HEAD")
            assertEquals(0, handle.readAt(Long.MAX_VALUE, ByteArray(16), 16))
            handle.checkAccess()
        }
        assertEquals(LARGE_SIZE, PrivilegedFileOperations.stat(context, PrivilegedAccessMode.SHIZUKU, path).getLong("size"))
    }

    @Test
    fun shizukuAuthorizationCallbackFailsClosedWithoutRevokingTheUsersActualPermission() = shizukuFixture { service, root ->
        val path = "$root/file"
        shell(service, "printf '%s' 'content' > ${quote(path)}")
        val permission = AtomicBoolean(true)
        val handle = PrivilegedRandomAccess.openShizukuForTest(context, path, permission::get)
        assertMarker(handle, 0, "content")
        permission.set(false)
        expectFailure("ACCESS_UNAVAILABLE") { handle.readAt(0, ByteArray(7), 7) }
        permission.set(true)
        expectFailure("ACCESS_UNAVAILABLE") { handle.checkAccess() }
        handle.close()
        assertTrue(WrappedShizuku.hasPermission())
    }

    @Test
    fun shizukuDescriptorSizeChangesAreDetectedWithoutReopeningThePath() = shizukuFixture { service, root ->
        val path = "$root/file"
        shell(service, "printf '%s' 'content' > ${quote(path)}")
        val handle = PrivilegedRandomAccess.open(context, PrivilegedAccessMode.SHIZUKU, path)
        shell(service, "printf '%s' ' appended' >> ${quote(path)}")
        expectFailure("SOURCE_CHANGED") { handle.readAt(0, ByteArray(32), 32) }
        handle.close()
        assertArrayEquals("content appended".toByteArray(), remoteBytes(service, path))
    }

    @Test
    fun shizukuReportsFilePermissionDenialSeparatelyFromConnectionLoss() = shizukuFixture { service, root ->
        assumeTrue("Root-mode Shizuku can intentionally bypass Unix mode bits", shell(service, "id -u").trim() != "0")
        val path = "$root/file"
        shell(service, "printf '%s' 'content' > ${quote(path)}")
        shell(service, "chmod 000 ${quote(path)}")
        try {
            expectFailure("PERMISSION_DENIED") { PrivilegedRandomAccess.open(context, PrivilegedAccessMode.SHIZUKU, path).close() }
        } finally { shell(service, "chmod 600 ${quote(path)}") }
        assertArrayEquals("content".toByteArray(), remoteBytes(service, path))
    }

    private fun sparse(root: File): File = File(root, "sparse.bin").also { source ->
        RandomAccessFile(source, "rw").use { file ->
            file.setLength(LARGE_SIZE)
            for ((offset, marker) in listOf(0L to "HEAD", FAR_OFFSET to "BEYOND4G", LARGE_SIZE - 4 to "TAIL")) {
                file.seek(offset); file.write(marker.toByteArray())
            }
        }
    }

    private fun rootOpen(source: File, expected: JSONObject? = null): PrivilegedRandomAccess.ReadHandle {
        val request = JSONObject().put("source", source.path).put("deadlineMillis", System.currentTimeMillis() + 30_000)
        expected?.let { request.put("expected", it) }
        val command = PrivilegedRandomAccess.rootCommand(context, request)
        return PrivilegedRandomAccess.openRootProcess(listOf("/system/bin/sh", "-c", command[2]))
    }

    private fun probe(source: File, mode: String, timeout: Long = 30_000): PrivilegedRandomAccess.ReadHandle {
        val testApk = InstrumentationRegistry.getInstrumentation().context.applicationInfo.sourceDir
        val command = "CLASSPATH=${quote("${context.applicationInfo.sourceDir}:$testApk")} /system/bin/toybox timeout -k 1 8 " +
            "/system/bin/app_process /system/bin ${PrivilegedReadProtocolProbe::class.java.name} ${quote(source.path)} ${quote(mode)}"
        return PrivilegedRandomAccess.openRootProcess(listOf("/system/bin/sh", "-c", command), timeout)
    }

    private fun assertMarker(handle: PrivilegedRandomAccess.ReadHandle, offset: Long, marker: String) {
        val expected = marker.toByteArray(Charsets.UTF_8)
        val actual = ByteArray(expected.size)
        assertEquals(expected.size, handle.readAt(offset, actual, actual.size))
        assertArrayEquals(expected, actual)
    }

    private fun expectArgument(action: () -> Unit) {
        try { action(); fail("Expected an invalid range") } catch (_: IllegalArgumentException) { }
    }

    private fun expectFailure(code: String, action: () -> Unit) {
        try { action(); fail("Expected $code") }
        catch (error: PrivilegedFileOperationException) { assertEquals(error.toString(), code, error.code) }
    }

    private fun processExists(pid: Int): Boolean = try { Os.kill(pid, 0); true } catch (error: ErrnoException) {
        if (error.errno != OsConstants.ESRCH) throw error
        false
    }

    private inline fun fixture(action: (File) -> Unit) {
        val root = File(context.cacheDir, "privileged-random-read-${UUID.randomUUID()}")
        check(root.mkdir())
        try { action(root) } finally { removeFixture(root) }
    }

    private fun removeFixture(file: File) {
        if (OsConstants.S_ISDIR(Os.lstat(file.path).st_mode)) file.listFiles()!!.forEach(::removeFixture)
        Os.remove(file.path)
    }

    private inline fun shizukuFixture(action: (IUserService, String) -> Unit) {
        assumeTrue("Uses only existing Shizuku authorization", WrappedShizuku.isRunning() && WrappedShizuku.hasPermission())
        val service = checkNotNull(WrappedShizuku.getServiceOrNull())
        val root = "/data/local/tmp/autojs6-random-read-${UUID.randomUUID()}"
        shell(service, "mkdir -m 700 -- ${quote(root)}")
        try { action(service, root) } finally {
            check(root.matches(Regex("/data/local/tmp/autojs6-random-read-[a-f0-9-]{36}")))
            shell(service, "rm -rf -- ${quote(root)}")
        }
    }

    private fun shell(service: IUserService, command: String): String {
        val result = JSONObject(service.execCommand(command))
        assertEquals(result.toString(), 0, result.getInt("code"))
        return result.optString("result")
    }

    private fun remoteBytes(service: IUserService, path: String) =
        Base64.decode(shell(service, "/system/bin/toybox base64 ${quote(path)}"), Base64.DEFAULT)

    private fun quote(value: String) = PrivilegedExplorerProtocol.quoteShellArgument(value)
    private val TimeUnitNanos get() = java.util.concurrent.TimeUnit.NANOSECONDS

    companion object {
        private const val FAR_OFFSET = 4L * 1024 * 1024 * 1024 + 65537
        private const val LARGE_SIZE = 5L * 1024 * 1024 * 1024 + 123
    }
}
