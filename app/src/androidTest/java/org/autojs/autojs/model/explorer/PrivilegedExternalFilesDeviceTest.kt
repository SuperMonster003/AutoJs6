package org.autojs.autojs.model.explorer

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.ResultReceiver
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.shizuku.IUserService
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs6.R
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Real ContentResolver/AppFuse and cross-UID checks; every path belongs to a unique fixture. */
@RunWith(AndroidJUnit4::class)
class PrivilegedExternalFilesDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val mode = PrivilegedAccessMode.SHIZUKU

    @Test
    fun readableSparseFilesPreferTheOriginalEvenWhenRootWasSelected() = privateFixture { fixture ->
        val source = File(fixture.root, "readable ' 中文.bin")
        RandomAccessFile(source, "rw").use { file ->
            file.setLength(LARGE_SIZE)
            MARKERS.forEach { (offset, bytes) -> file.seek(offset); file.write(bytes) }
        }
        val prepared = fixture.prepare(source.path, PrivilegedAccessMode.ROOT)
        assertEquals(PrivilegedExternalFiles.OpenKind.DIRECT, prepared.kind)
        assertNull(prepared.token)
        assertEquals(LARGE_SIZE, prepared.size)
        assertMetadata(prepared.uri, source.name, LARGE_SIZE)
        open(prepared.uri).use { descriptor -> assertMarkersAndSeeks(descriptor, MARKERS.reversed()) }
        assertTrue("Sparse fixture must not allocate its logical size", Os.stat(source.path).st_blocks * 512 < 2L * MIB)
        fixture.assertNoCopies()
    }

    @Test
    fun authorizedProviderReadsBeyondFourGiBAndSeeksBackwardsWithoutCopying() = remoteFixture { fixture ->
        val source = fixture.sparse()
        assertFalse("This fixture must require the authorized reader", File(source).canRead())
        val prepared = fixture.prepare(source)
        assertEquals(PrivilegedExternalFiles.OpenKind.STREAM, prepared.kind)
        assertEquals(LARGE_SIZE, prepared.size)
        assertMetadata(prepared.uri, File(source).name, LARGE_SIZE)
        open(prepared.uri).use { descriptor ->
            assertMarkersAndSeeks(descriptor, MARKERS.reversed())
            assertEquals(0, Os.pread(descriptor.fileDescriptor, ByteArray(8), 0, 8, LARGE_SIZE))
        }
        fixture.assertSparse(source)
        fixture.assertNoCopies()
    }

    @Test
    fun sourceChangesFailTheOpenDescriptorAndExistingLinkWithOneNotification() = remoteFixture { fixture ->
        val source = fixture.sparse()
        val prepared = fixture.prepare(source)
        val unopenedLink = fixture.prepare(source)
        PrivilegedExternalFiles.consumeFailure(context)
        open(prepared.uri).use { descriptor ->
            assertBytes(descriptor, 0, MARKERS.first().second)
            fixture.shell("/system/bin/toybox truncate -s ${LARGE_SIZE + 1} -- ${quote(source)}")
            assertFailsPromptly("A changed source must fail an uncached read") {
                Os.pread(descriptor.fileDescriptor, ByteArray(32), 0, 32, UNCACHED_OFFSET)
            }
            assertEquals(context.getString(R.string.text_explorer_stream_changed), PrivilegedExternalFiles.consumeFailure(context))
            assertNull(PrivilegedExternalFiles.consumeFailure(context))
            // A different token prepared before the change must also reject its first open,
            // rather than following the new source merely because no FD was opened yet.
            expectLinkFailure { open(unopenedLink.uri).close() }
            assertEquals(context.getString(R.string.text_explorer_stream_changed), PrivilegedExternalFiles.consumeFailure(context))
            assertNull(PrivilegedExternalFiles.consumeFailure(context))
            expectLinkFailure { open(prepared.uri).close() }
            assertEquals(context.getString(R.string.text_explorer_stream_expired), PrivilegedExternalFiles.consumeFailure(context))
            assertNull(PrivilegedExternalFiles.consumeFailure(context))
        }
        fixture.assertNoCopies()
    }

    @Test
    fun revocationKeepsTheCallbackResponsiveUntilTheExternalDescriptorIsReleased() = remoteFixture { fixture ->
        val prepared = fixture.prepare(fixture.sparse())
        PrivilegedExternalFiles.consumeFailure(context)
        open(prepared.uri).use { descriptor ->
            assertBytes(descriptor, 0, MARKERS.first().second)
            PrivilegedExternalFiles.release(prepared)
            assertFailsPromptly("Revoking a live proxy must return an error instead of hanging") {
                Os.pread(descriptor.fileDescriptor, ByteArray(32), 0, 32, UNCACHED_OFFSET)
            }
            expectLinkFailure { open(prepared.uri).close() }
            assertEquals(context.getString(R.string.text_explorer_stream_expired), PrivilegedExternalFiles.consumeFailure(context))
            assertNull(PrivilegedExternalFiles.consumeFailure(context))
        }
    }

    @Test
    fun writableModesAndWritesThroughTheReadOnlyDescriptorAreRejected() = remoteFixture { fixture ->
        val source = fixture.sparse()
        val prepared = fixture.prepare(source)
        for (requestedMode in listOf("rw", "rwt")) {
            expectLinkFailure { open(prepared.uri, requestedMode).close() }
        }
        open(prepared.uri).use { descriptor ->
            try {
                Os.pwrite(descriptor.fileDescriptor, byteArrayOf(99), 0, 1, 0)
                fail("A read-only proxy must not accept pwrite")
            } catch (error: ErrnoException) {
                assertTrue(error.toString(), error.errno in listOf(OsConstants.EBADF, OsConstants.EACCES, OsConstants.EPERM))
            }
            assertBytes(descriptor, 0, MARKERS.first().second)
        }
        assertEquals(LARGE_SIZE, PrivilegedFileOperations.stat(context, mode, source).getLong("size"))
    }

    @Test
    fun externalUidRequiresAnExactReadGrantAndCannotObtainAPathOrWriteAccess() = remoteFixture { fixture ->
        val source = fixture.sparse()
        val noGrant = fixture.prepare(source)
        val denied = receive(noGrant, flags = 0)
        assertReceiverIsDifferentUid(denied)
        assertFalse(denied.toString(), denied.getBoolean("queried"))
        assertFalse(denied.toString(), denied.getBoolean("opened"))
        assertNotNull(denied.getString("query_error"))
        assertNotNull(denied.getString("open_error"))

        val granted = fixture.prepare(source)
        val sibling = fixture.prepare(fixture.sparse("sibling.bin"))
        val read = receive(granted, Intent.FLAG_GRANT_READ_URI_PERMISSION, other = sibling.uri)
        assertReceiverIsDifferentUid(read)
        assertNull(read.getString("fatal_error"))
        assertNull(read.getString("query_error"))
        assertNull(read.getString("open_error"))
        assertTrue(read.getBoolean("queried"))
        assertTrue(read.getBoolean("opened"))
        assertEquals(LARGE_SIZE, read.getLong("size"))
        assertEquals(File(source).name, read.getString("name"))
        assertTrue("_data must not reveal the privileged source path", read.getBoolean("data_is_null"))
        MARKERS.reversed().forEachIndexed { index, (offset, bytes) ->
            assertArrayEquals(bytes, read.getByteArray("bytes_$index"))
            assertEquals(offset, read.getLong("seek_$index"))
            assertArrayEquals(bytes, read.getByteArray("seek_bytes_$index"))
        }
        assertFalse(read.toString(), read.getBoolean("other_query_allowed"))
        assertFalse(read.toString(), read.getBoolean("other_open_allowed"))
        assertNotNull(read.getString("other_query_error"))
        assertNotNull(read.getString("other_open_error"))

        for (requestedMode in listOf("rw", "rwt")) {
            val writableGrant = fixture.prepare(source)
            val result = receive(writableGrant, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION, requestedMode)
            assertReceiverIsDifferentUid(result)
            assertTrue(result.toString(), result.getBoolean("queried"))
            assertFalse("The provider must reject $requestedMode even if Android granted WRITE", result.getBoolean("opened"))
            assertNotNull(result.getString("open_error"))
        }
        fixture.assertNoCopies()
    }

    @Test
    fun compatibilityCopyRechecksConfirmedSizeAndIdentityBeforeWriting() = remoteFixture { fixture ->
        val source = fixture.path("confirmed.bin")
        fixture.writeBytes(source, "before".toByteArray())
        val initial = PrivilegedExternalFiles.describe(fixture.isolatedContext, mode, source)
        fixture.shell("/system/bin/toybox truncate -s 4096 -- ${quote(source)}")
        expectFailure("SOURCE_CHANGED") {
            PrivilegedExternalFiles.copyForCompatibility(fixture.isolatedContext, mode, source, confirmed = initial)
        }
        fixture.assertNoCopyPayloads()

        val sameSize = PrivilegedExternalFiles.describe(fixture.isolatedContext, mode, source)
        val replacement = fixture.path("replacement.bin")
        fixture.shell("/system/bin/toybox truncate -s 4096 -- ${quote(replacement)}")
        fixture.shell("mv -f -- ${quote(replacement)} ${quote(source)}")
        expectFailure("SOURCE_CHANGED") {
            PrivilegedExternalFiles.copyForCompatibility(fixture.isolatedContext, mode, source, confirmed = sameSize)
        }
        fixture.assertNoCopyPayloads()
    }

    @Test
    fun cancellingCompatibilityCopyRemovesThePartialOutput() = remoteFixture { fixture ->
        val source = fixture.path("cancel.bin")
        fixture.shell("/system/bin/toybox truncate -s ${8L * MIB} -- ${quote(source)}")
        val description = PrivilegedExternalFiles.describe(fixture.isolatedContext, mode, source)
        val cancelled = AtomicBoolean()
        var copiedAtCancellation = 0L
        expectFailure("CANCELLED") {
            PrivilegedExternalFiles.copyForCompatibility(fixture.isolatedContext, mode, source,
                onProgress = { copied, _ ->
                    if (copied > 0) { copiedAtCancellation = copied; cancelled.set(true) }
                }, cancelled = cancelled::get, confirmed = description)
        }
        assertTrue(copiedAtCancellation in 1 until description.size)
        fixture.assertNoCopyPayloads()
        assertEquals(8L * MIB, PrivilegedFileOperations.stat(context, mode, source).getLong("size"))
    }

    @Test
    fun mediaRetrieverReadsAndSeeksARealSparseMp4LargerThanTwoGiB() = remoteFixture { fixture ->
        val originalBytes = context.resources.openRawResource(R.raw.text_tool).use { normalizeMp4ForAppend(it.readBytes()) }
        assertTrue("Use the small bundled media fixture", originalBytes.size in 1..128 * 1024)
        val local = File(fixture.root, "baseline.mp4").apply { writeBytes(originalBytes) }
        val source = fixture.path("sparse-video.mp4")
        fixture.writeBytes(source, originalBytes)
        val freeBox = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
            .putInt(1).put(byteArrayOf(0x66, 0x72, 0x65, 0x65))
            .putLong(MEDIA_SIZE - originalBytes.size).array()
        fixture.appendBytes(source, freeBox)
        fixture.shell("/system/bin/toybox truncate -s $MEDIA_SIZE -- ${quote(source)}")
        val prepared = fixture.prepare(source)
        assertEquals(PrivilegedExternalFiles.OpenKind.STREAM, prepared.kind)
        assertEquals(MEDIA_SIZE, prepared.size)
        assertEquals("video/mp4", prepared.mimeType)
        assertMetadata(prepared.uri, "sparse-video.mp4", MEDIA_SIZE)

        val baseline = MediaMetadataRetriever()
        val streamed = MediaMetadataRetriever()
        try {
            baseline.setDataSource(local.path)
            streamed.setDataSource(context, prepared.uri)
            val duration = requireNotNull(baseline.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
            assertTrue(duration > 0)
            for (key in listOf(MediaMetadataRetriever.METADATA_KEY_DURATION, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH,
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)) {
                assertEquals("Metadata key $key", baseline.extractMetadata(key), streamed.extractMetadata(key))
            }
            // The order deliberately seeks backwards and then forwards in the real video track.
            for (timeUs in listOf((duration - 1) * 1000L, 0L, duration * 500L).distinct()) {
                val expected = requireFrame(baseline, timeUs)
                val actual = requireFrame(streamed, timeUs)
                try {
                    assertEquals(expected.width, actual.width)
                    assertEquals(expected.height, actual.height)
                    assertTrue("Decoded frame differs at $timeUs us", expected.sameAs(actual))
                } finally { expected.recycle(); actual.recycle() }
            }
        } finally { runCatching { baseline.release() }; runCatching { streamed.release() } }
        fixture.assertSparse(source)
        fixture.assertNoCopies()
    }

    private fun requireFrame(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap {
        val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        assertNotNull("No decoded frame at $timeUs us", frame)
        return requireNotNull(frame)
    }

    /** A zero-sized terminal atom must be bounded before appending the extended-size free atom. */
    private fun normalizeMp4ForAppend(bytes: ByteArray): ByteArray {
        val copy = bytes.copyOf()
        val buffer = ByteBuffer.wrap(copy).order(ByteOrder.BIG_ENDIAN)
        var offset = 0
        var foundMedia = false
        var foundMovie = false
        while (offset < copy.size) {
            check(copy.size - offset >= 8) { "Incomplete MP4 atom" }
            val smallSize = buffer.getInt(offset).toLong() and 0xffff_ffffL
            val name = copy.copyOfRange(offset + 4, offset + 8).toString(Charsets.US_ASCII)
            val size = when (smallSize) {
                0L -> (copy.size - offset).toLong().also { buffer.putInt(offset, it.toInt()) }
                1L -> { check(copy.size - offset >= 16); buffer.getLong(offset + 8) }
                else -> smallSize
            }
            check(size >= if (smallSize == 1L) 16L else 8L)
            check(size <= copy.size - offset) { "MP4 atom extends beyond fixture" }
            foundMedia = foundMedia || name == "mdat"
            foundMovie = foundMovie || name == "moov"
            offset += size.toInt()
        }
        check(foundMedia && foundMovie) { "Bundled fixture must contain an actual movie track" }
        return copy
    }

    private fun receive(prepared: PrivilegedExternalFiles.PreparedOpen, flags: Int, requestedMode: String = "r", other: Uri? = null): Bundle {
        val latch = CountDownLatch(1)
        val result = AtomicReference<Bundle>()
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                result.set(requireNotNull(resultData))
                latch.countDown()
            }
        }
        val ranges = MARKERS.reversed()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(instrumentation.context.packageName, ExternalFileReceiverProbeActivity::class.java.name)
            setDataAndType(prepared.uri, prepared.mimeType)
            clipData = ClipData.newRawUri("Selected fixture only", prepared.uri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or flags)
            putExtra(ExternalFileReceiverProbeActivity.EXTRA_RECEIVER, receiver)
            putExtra(ExternalFileReceiverProbeActivity.EXTRA_OFFSETS, ranges.map { it.first }.toLongArray())
            putExtra(ExternalFileReceiverProbeActivity.EXTRA_LENGTHS, ranges.map { it.second.size }.toIntArray())
            putExtra(ExternalFileReceiverProbeActivity.EXTRA_MODE, requestedMode)
            other?.let { putExtra(ExternalFileReceiverProbeActivity.EXTRA_OTHER_URI, it.toString()) }
        }
        instrumentation.runOnMainSync { context.startActivity(intent) }
        assertTrue("The separate receiver process did not return its result", latch.await(45, TimeUnit.SECONDS))
        return requireNotNull(result.get()).also { assertNull(it.getString("fatal_error")) }
    }

    private fun assertReceiverIsDifferentUid(result: Bundle) {
        assertNotEquals("Receiver must not run with target APK privileges", Process.myUid(), result.getInt("uid"))
        assertEquals(instrumentation.context.applicationInfo.uid, result.getInt("uid"))
        assertNotEquals(Process.myPid(), result.getInt("pid"))
    }

    private fun assertMetadata(uri: Uri, expectedName: String, expectedSize: Long) {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE, "_data"), null, null, null)!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expectedName, cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)))
            assertEquals(expectedSize, cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)))
            val data = cursor.getColumnIndex("_data")
            assertTrue("No raw source path should be exposed", data < 0 || cursor.isNull(data))
        }
    }

    private fun open(uri: Uri, requestedMode: String = "r") = requireNotNull(context.contentResolver.openFileDescriptor(uri, requestedMode))
    private fun assertMarkersAndSeeks(descriptor: ParcelFileDescriptor, markers: List<Pair<Long, ByteArray>>) {
        markers.forEach { (offset, bytes) ->
            assertBytes(descriptor, offset, bytes)
            assertEquals(offset, Os.lseek(descriptor.fileDescriptor, offset, OsConstants.SEEK_SET))
            val buffer = ByteArray(bytes.size)
            assertEquals(buffer.size, Os.read(descriptor.fileDescriptor, buffer, 0, buffer.size))
            assertArrayEquals(bytes, buffer)
        }
    }
    private fun assertBytes(descriptor: ParcelFileDescriptor, offset: Long, expected: ByteArray) {
        val bytes = ByteArray(expected.size)
        assertEquals(expected.size, Os.pread(descriptor.fileDescriptor, bytes, 0, bytes.size, offset))
        assertArrayEquals(expected, bytes)
    }

    private fun assertFailsPromptly(message: String, work: () -> Unit): Throwable {
        val task = FutureTask<Throwable?> { try { work(); null } catch (error: Throwable) { error } }
        thread(name = "ProviderReadDeadlineProbe", isDaemon = true) { task.run() }
        try {
            val error = task.get(8, TimeUnit.SECONDS)
            assertNotNull(message, error)
            return requireNotNull(error)
        } finally { task.cancel(true) }
    }
    private fun expectLinkFailure(work: () -> Unit) {
        try { work(); fail("Expected a rejected file link or mode") }
        catch (_: FileNotFoundException) { }
    }
    private fun expectFailure(code: String, work: () -> Unit) {
        try { work(); fail("Expected $code") }
        catch (error: PrivilegedFileOperationException) { assertEquals(error.toString(), code, error.code) }
    }

    private fun privateFixture(action: (Fixture) -> Unit) {
        val fixture = Fixture(null)
        try { action(fixture) } finally { fixture.close() }
    }
    private fun remoteFixture(action: (Fixture) -> Unit) {
        assumeTrue("Seekable proxy FDs need Android 8+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        assumeTrue("Only existing Shizuku authorization is used", WrappedShizuku.isRunning() && WrappedShizuku.hasPermission())
        val fixture = Fixture(requireNotNull(WrappedShizuku.getServiceOrNull()))
        try {
            fixture.shell("mkdir -m 700 -- ${quote(fixture.remoteRoot)}")
            action(fixture)
        } finally { fixture.close() }
    }

    private inner class Fixture(private val service: IUserService?) {
        val root = File(context.cacheDir, "privileged-stream-provider-${UUID.randomUUID()}").apply { check(mkdir()) }
        private val boundary = root.canonicalPath
        private val scratch = File(root, "isolated-cache").apply { check(mkdir()) }
        val remoteRoot = "/data/local/tmp/autojs6-stream-provider-${UUID.randomUUID()}"
        val isolatedContext: Context = object : ContextWrapper(context.applicationContext) {
            override fun getCacheDir(): File = scratch
            override fun getApplicationContext(): Context = this
        }
        private val prepared = mutableListOf<PrivilegedExternalFiles.PreparedOpen>()

        fun path(name: String) = "$remoteRoot/$name".also { require('/' !in name && name != "." && name != "..") }
        fun prepare(path: String, access: PrivilegedAccessMode = mode) =
            PrivilegedExternalFiles.prepare(isolatedContext, access, path).also(prepared::add)

        fun sparse(name: String = "sparse ' 中文.bin"): String = path(name).also { source ->
            shell("/system/bin/toybox truncate -s $LARGE_SIZE -- ${quote(source)}")
            MARKERS.forEach { (offset, bytes) ->
                // Successful dd statistics on stderr make AbstractShell.Result report code 1.
                // Silence only dd's diagnostics; the shell still returns its real exit status.
                shell("printf '%s' ${quote(Base64.encodeToString(bytes, Base64.NO_WRAP))} | /system/bin/toybox base64 -d | /system/bin/toybox dd of=${quote(source)} bs=1 seek=$offset conv=notrunc 2>/dev/null")
            }
            shell("chmod 600 -- ${quote(source)}")
        }
        fun writeBytes(path: String, bytes: ByteArray) {
            require(path.startsWith("$remoteRoot/"))
            shell("printf '' > ${quote(path)}")
            appendBytes(path, bytes)
            shell("chmod 600 -- ${quote(path)}")
        }
        fun appendBytes(path: String, bytes: ByteArray) {
            require(path.startsWith("$remoteRoot/"))
            bytes.asList().chunked(8192).forEach { chunk ->
                val encoded = Base64.encodeToString(chunk.toByteArray(), Base64.NO_WRAP)
                shell("printf '%s' ${quote(encoded)} | /system/bin/toybox base64 -d >> ${quote(path)}")
            }
        }
        fun shell(command: String): String {
            check(REMOTE_ROOT.matches(remoteRoot))
            val result = JSONObject(requireNotNull(service).execCommand(command))
            assertEquals(result.toString(), 0, result.getInt("code"))
            return result.optString("result")
        }
        fun assertSparse(path: String) {
            val blocks = shell("/system/bin/toybox stat -c '%b' -- ${quote(path)}").trim().toLong()
            assertTrue("Fixture must remain sparse instead of consuming GiB", blocks * 512 < 2L * MIB)
        }
        fun assertNoCopies() { assertTrue("Opening/streaming must not stage file contents", scratch.listFiles().orEmpty().isEmpty()) }
        fun assertNoCopyPayloads() {
            val copies = File(scratch, "privileged-view-copies")
            assertTrue("Failed/cancelled copies must remove their UUID output directories", copies.listFiles().orEmpty().isEmpty())
        }
        fun close() {
            prepared.forEach { PrivilegedExternalFiles.release(it) }
            try {
                if (service != null) {
                    check(REMOTE_ROOT.matches(remoteRoot))
                    shell("rm -rf -- ${quote(remoteRoot)}")
                }
            } finally { removePrivate(root) }
        }
        private fun removePrivate(file: File) {
            val resolved = File(requireNotNull(file.parentFile).canonicalFile, file.name).path
            check(resolved == boundary || resolved.startsWith("$boundary/")) { "Outside this test fixture: $resolved" }
            if (OsConstants.S_ISDIR(Os.lstat(file.path).st_mode)) file.listFiles().orEmpty().forEach(::removePrivate)
            Os.remove(file.path)
        }
    }

    private fun quote(value: String) = PrivilegedExplorerProtocol.quoteShellArgument(value)

    companion object {
        private const val MIB = 1024L * 1024
        private const val LARGE_SIZE = 5L * 1024 * MIB + 65_536L
        private const val FAR_OFFSET = (1L shl 32) + 12_288L
        private const val UNCACHED_OFFSET = FAR_OFFSET + 3L * MIB
        private const val MEDIA_SIZE = (1L shl 31) + 8192L
        private val MARKERS = listOf(0L to "HEAD".toByteArray(), FAR_OFFSET to "BEYOND4G".toByteArray(), (LARGE_SIZE - 4) to "TAIL".toByteArray())
        private val REMOTE_ROOT = Regex("/data/local/tmp/autojs6-stream-provider-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    }
}
