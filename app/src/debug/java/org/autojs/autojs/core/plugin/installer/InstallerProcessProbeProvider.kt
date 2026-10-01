package org.autojs.autojs.core.plugin.installer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.OpenableColumns
import java.io.File
import java.io.DataInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Nonexported, dedicated source process; the writer outlives only the actual host callback PID. */
class InstallerProcessProbeProvider : ContentProvider() {
    private class Writer(val stop: CountDownLatch = CountDownLatch(1), val done: CountDownLatch = CountDownLatch(1)) {
        @Volatile var bytes = 0L
        @Volatile var holding = false
    }
    private val writers = ConcurrentHashMap<String, Writer>()
    override fun onCreate() = true
    private fun source(uri: Uri): Pair<String, File> {
        check(android.os.Binder.getCallingUid() == Process.myUid())
        check(uri.pathSegments.size == 2 && uri.lastPathSegment == "fixture.apk")
        val id = uri.pathSegments[0]
        check(InstallerProcessProbeActivity.CASE.matches(id))
        val file = File(requireNotNull(context).filesDir.canonicalFile, "p6-installer-probe/$id/fixture.apk")
        check(file.isFile && file.canonicalFile == file.absoluteFile && file.length() in (100L * 1024 * 1024)..(102L * 1024 * 1024))
        return id to file
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val (id, file) = source(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply { addRow(Array<Any?>(columns.size) { index -> when (columns[index]) {
            OpenableColumns.DISPLAY_NAME -> "host-death-$id.apk"
            OpenableColumns.SIZE -> file.length()
            else -> null
        } }) }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        check(mode == "r")
        val (id, file) = source(uri)
        check(writers.isEmpty()) { "Another probe source is still open" }
        val writer = Writer()
        check(writers.putIfAbsent(id, writer) == null)
        val pipe = ParcelFileDescriptor.createPipe()
        Thread({
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                    val bytes = DataInputStream(file.inputStream()).use { input -> ByteArray(512 * 1024).also(input::readFully) }
                    output.write(bytes)
                    output.flush()
                    writer.bytes = bytes.size.toLong()
                    writer.holding = true
                    writer.stop.await(180, TimeUnit.SECONDS)
                }
            } finally { writer.holding = false; writer.done.countDown() }
        }, "p6-held-fixture-source").start()
        return pipe[0]
    }
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        check(android.os.Binder.getCallingUid() == Process.myUid())
        val id = requireNotNull(arg ?: extras?.getString("caseId"))
        check(InstallerProcessProbeActivity.CASE.matches(id))
        val writer = writers[id]
        return when (method) {
            "status" -> Bundle().apply {
                putInt("pid", Process.myPid()); putBoolean("holding", writer?.holding == true); putLong("bytesSent", writer?.bytes ?: 0)
            }
            "close" -> Bundle().apply {
                writer?.stop?.countDown()
                check(writer == null || writer.done.await(10, TimeUnit.SECONDS))
                if (writer != null) writers.remove(id, writer)
                putBoolean("closed", true)
            }
            else -> error("Unsupported probe method")
        }
    }
    override fun getType(uri: Uri): String { source(uri); return "application/vnd.android.package-archive" }
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = throw UnsupportedOperationException()
}
