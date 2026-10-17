package org.autojs.autojs.model.explorer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.SystemClock
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import androidx.annotation.RequiresApi
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs6.R
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Selected-file, read-only grants for external viewers. Text editing uses its own durable sessions. */
object PrivilegedExternalFiles {
    enum class OpenKind { DIRECT, STREAM }
    data class PreparedOpen(val uri: Uri, val kind: OpenKind, val size: Long, val mimeType: String, val token: String? = null)
    data class FileDescription(val size: Long, val availableBytes: Long, val identity: JSONObject? = null)
    internal data class Metadata(val name: String, val size: Long, val mimeType: String)

    private const val TOKEN_IDLE_MS = 30L * 60 * 1000
    private const val TOKEN_MAX_MS = 12L * 60 * 60 * 1000
    private const val COPY_LIFETIME_MS = 24L * 60 * 60 * 1000
    private const val RESERVE_BYTES = 32L * 1024 * 1024
    private const val MAX_TOKENS = 128
    private const val MAX_OPEN_READERS = 24
    private val sessions = ConcurrentHashMap<String, Session>()
    private val notices = ConcurrentHashMap<String, String>()
    private val openReaders = AtomicInteger()
    private val activeCopies = ConcurrentHashMap.newKeySet<String>()
    private val janitor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "PrivilegedViewerExpiry").apply { isDaemon = true }
    }.apply { scheduleWithFixedDelay({ runCatching { expire() } }, 1, 1, TimeUnit.MINUTES) }

    private class Session(
        val context: Context, val token: String, val path: String, val mode: PrivilegedAccessMode,
        val size: Long, val identity: JSONObject, val mime: String,
    ) {
        val created = SystemClock.elapsedRealtime()
        @Volatile var touched = created
        val revoked = AtomicBoolean()
        val readers = ConcurrentHashMap.newKeySet<ReaderLease>()
        val lastError = AtomicReference<Throwable?>()
        val uri: Uri = Uri.Builder().scheme("content").authority(authority(context)).appendPath(token).build()
        fun expired(now: Long) = now - created > TOKEN_MAX_MS || readers.isEmpty() && now - touched > TOKEN_IDLE_MS
    }

    fun prepare(context: Context, mode: PrivilegedAccessMode, path: String): PreparedOpen {
        tryLocal(path)?.use { local ->
            return PreparedOpen(IntentUtils.getUriOfFile(context, path), OpenKind.DIRECT, local.size, Mime.fromFileOrWildcard(path))
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) throw unsupported(context)
        expire()
        if (sessions.size >= MAX_TOKENS) {
            sessions.values.filter { it.readers.isEmpty() }.minByOrNull { it.touched }?.let(::revoke)
            if (sessions.size >= MAX_TOKENS) throw PrivilegedFileOperationException("LIMIT_EXCEEDED", "Too many files are open")
        }
        val reader = PrivilegedRandomAccess.open(context, mode, path)
        val session = Session(context.applicationContext, UUID.randomUUID().toString(), path, mode, reader.size,
            JSONObject(reader.identity.toString()), Mime.fromFileOrWildcard(path))
        // Preflight the actual AppFuse path, including a nonzero 64-bit offset, before launching a
        // viewer. Only a byte is read; no file-size-proportional scan, digest, or copy is needed.
        try {
            openProxy(session, reader, notifyFailure = false).use { descriptor ->
                if (session.size > 0) {
                    val byte = ByteArray(1)
                    val count = Os.pread(descriptor.fileDescriptor, byte, 0, 1, session.size - 1)
                    if (count != 1) throw java.io.IOException("Incomplete read preflight")
                } else Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_SET)
            }
        } catch (error: Exception) {
            session.revoked.set(true)
            session.readers.toList().forEach(ReaderLease::close)
            runCatching { reader.close() }
            val sourceError = session.lastError.get()
            if (sourceError is PrivilegedFileOperationException) throw sourceError
            if (error is PrivilegedFileOperationException) throw error
            throw unsupported(context, error)
        }
        sessions[session.token] = session
        return PreparedOpen(session.uri, OpenKind.STREAM, session.size, session.mime, session.token)
    }

    fun release(prepared: PreparedOpen) { prepared.token?.let { sessions[it]?.let(::revoke) } }
    fun consumeFailure(context: Context): String? = notices.remove(context.packageName)

    fun describe(context: Context, mode: PrivilegedAccessMode, path: String): FileDescription =
        openSource(context, mode, path).use { FileDescription(it.size, (context.cacheDir.usableSpace - RESERVE_BYTES).coerceAtLeast(0), JSONObject(it.identity.toString())) }

    /** Explicit compatibility fallback only. This is not the editor's bounded snapshot mechanism. */
    fun copyForCompatibility(
        context: Context, mode: PrivilegedAccessMode, path: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> }, cancelled: () -> Boolean = { false },
        confirmed: FileDescription? = null,
    ): File {
        fun checkCancelled() {
            if (cancelled() || Thread.currentThread().isInterrupted) throw PrivilegedFileOperationException("CANCELLED", context.getString(R.string.text_explorer_stream_copy_cancelled))
        }
        checkCancelled()
        val root = File(context.cacheDir, "privileged-view-copies").apply { check(isDirectory || mkdir()) }
        check(OsConstants.S_ISDIR(Os.lstat(root.path).st_mode))
        cleanupCopies(root)
        val directory = File(root, UUID.randomUUID().toString()).apply { check(mkdir()) }
        activeCopies += directory.path
        val output = File(directory, File(path).name)
        try {
            openSource(context, mode, path).use { source ->
                if (confirmed != null && (source.size != confirmed.size || confirmed.identity?.let { expected ->
                        val actual = source.identity
                        expected.keys().asSequence().any { expected.getLong(it) != actual.optLong(it, Long.MIN_VALUE) }
                    } == true)) throw PrivilegedFileOperationException("SOURCE_CHANGED", context.getString(R.string.text_explorer_stream_changed))
                if (source.size > root.usableSpace - RESERVE_BYTES) throw PrivilegedFileOperationException("NO_SPACE", context.getString(R.string.text_explorer_privileged_no_space))
                onProgress(0, source.size)
                output.outputStream().use { stream ->
                    val buffer = ByteArray(PrivilegedRandomAccess.MAX_CHUNK_BYTES)
                    var copied = 0L
                    while (copied < source.size) {
                        checkCancelled()
                        val count = source.readAt(copied, buffer, (source.size - copied).coerceAtMost(buffer.size.toLong()).toInt())
                        if (count <= 0) throw PrivilegedFileOperationException("SOURCE_CHANGED", "The source ended before the copy was complete")
                        stream.write(buffer, 0, count)
                        copied += count
                        onProgress(copied, source.size)
                    }
                    checkCancelled()
                    source.checkAccess()
                    stream.fd.sync()
                }
            }
            check(output.setReadOnly())
            return output
        } catch (error: Exception) {
            output.delete()
            directory.delete()
            throw error
        } finally { activeCopies -= directory.path }
    }

    internal fun metadata(context: Context, uri: Uri): Metadata = find(context, uri).let {
        Metadata(File(it.path).name, it.size, it.mime)
    }

    internal fun open(context: Context, uri: Uri): ParcelFileDescriptor {
        val session = find(context, uri)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) throw FileNotFoundException(context.getString(R.string.text_explorer_stream_unsupported))
        try {
            val reader = PrivilegedRandomAccess.open(context, session.mode, session.path, JSONObject(session.identity.toString()))
            return openProxy(session, reader, notifyFailure = true)
        } catch (error: Exception) {
            recordFailure(session, error)
            throw FileNotFoundException(message(context, error)).apply { initCause(error) }
        }
    }

    private fun find(context: Context, uri: Uri): Session {
        val token = uri.pathSegments.singleOrNull()
        if (uri.scheme != "content" || uri.authority != authority(context) || uri.query != null || uri.fragment != null ||
            token == null || !token.matches(Regex("[a-f0-9-]{36}"))) throw FileNotFoundException("Invalid file link")
        val session = sessions[token]
        if (session == null || session.context.packageName != context.packageName || session.revoked.get() || session.expired(SystemClock.elapsedRealtime())) {
            session?.let(::revoke)
            notices.putIfAbsent(context.packageName, context.getString(R.string.text_explorer_stream_expired))
            throw FileNotFoundException(context.getString(R.string.text_explorer_stream_expired))
        }
        session.touched = SystemClock.elapsedRealtime()
        return session
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun openProxy(session: Session, source: PrivilegedRandomAccess.ReadHandle, notifyFailure: Boolean): ParcelFileDescriptor {
        if (openReaders.incrementAndGet() > MAX_OPEN_READERS) {
            openReaders.decrementAndGet(); source.close()
            throw PrivilegedFileOperationException("LIMIT_EXCEEDED", "Too many file readers are open")
        }
        val thread = HandlerThread("PrivilegedViewer-${session.token.take(8)}").apply { start() }
        val lease = ReaderLease(session, source, thread)
        session.readers += lease
        try {
            return checkNotNull(session.context.getSystemService(StorageManager::class.java)).openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY,
                object : ProxyFileDescriptorCallback() {
                    private fun <T> reading(checkAccess: Boolean = true, work: () -> T): T {
                        try {
                            if (lease.closed.get() || session.revoked.get() || session.expired(SystemClock.elapsedRealtime())) {
                                throw PrivilegedFileOperationException("LINK_EXPIRED", session.context.getString(R.string.text_explorer_stream_expired))
                            }
                            session.touched = SystemClock.elapsedRealtime()
                            if (checkAccess) source.checkAccess()
                            return work()
                        } catch (error: Exception) {
                            lease.stopReading()
                            session.lastError.compareAndSet(null, error)
                            if (notifyFailure) recordFailure(session, error)
                            throw ErrnoException("read", when ((error as? PrivilegedFileOperationException)?.code) {
                                "ACCESS_UNAVAILABLE", "PERMISSION_DENIED", "LINK_EXPIRED" -> OsConstants.EACCES
                                "SOURCE_CHANGED", "NOT_FOUND" -> OsConstants.ESTALE
                                else -> OsConstants.EIO
                            })
                        }
                    }
                    override fun onGetSize(): Long = reading { source.size }
                    override fun onRead(offset: Long, size: Int, data: ByteArray): Int = reading(checkAccess = false) {
                        if (offset < 0 || size < 0 || size > data.size) throw ErrnoException("read", OsConstants.EINVAL)
                        source.readAt(offset, data, size)
                    }
                    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int = throw ErrnoException("write", OsConstants.EBADF)
                    override fun onFsync() = Unit
                    override fun onRelease() = lease.close()
                }, Handler(thread.looper),
            )
        } catch (error: Exception) { lease.close(); throw error }
    }

    private class ReaderLease(val session: Session, val source: PrivilegedRandomAccess.ReadHandle, val thread: HandlerThread) {
        val closed = AtomicBoolean()
        private val sourceClosed = AtomicBoolean()
        fun stopReading() { if (sourceClosed.compareAndSet(false, true)) runCatching { source.close() } }
        fun close() {
            if (!closed.compareAndSet(false, true)) return
            stopReading()
            session.readers -= this
            session.touched = SystemClock.elapsedRealtime()
            openReaders.decrementAndGet()
            thread.quitSafely()
        }
    }

    private fun recordFailure(session: Session, error: Exception) {
        if (session.lastError.compareAndSet(null, error) || session.lastError.get() === error) {
            notices.putIfAbsent(session.context.packageName, message(session.context, error))
        }
        // A failed authorized read cannot silently reopen with new privileges or a different inode.
        session.revoked.set(true)
    }

    private fun message(context: Context, error: Throwable) = context.getString(when ((error as? PrivilegedFileOperationException)?.code) {
        "SOURCE_CHANGED", "NOT_FOUND" -> R.string.text_explorer_stream_changed
        "LINK_EXPIRED" -> R.string.text_explorer_stream_expired
        "STREAM_UNSUPPORTED" -> R.string.text_explorer_stream_unsupported
        else -> R.string.text_explorer_stream_access_lost
    })

    private fun unsupported(context: Context, cause: Throwable? = null) =
        PrivilegedFileOperationException("STREAM_UNSUPPORTED", context.getString(R.string.text_explorer_stream_unsupported), cause = cause)

    private fun expire() {
        val now = SystemClock.elapsedRealtime()
        sessions.values.filter { it.revoked.get() || it.expired(now) }.forEach(::revoke)
    }

    private fun revoke(session: Session) {
        session.revoked.set(true)
        sessions.remove(session.token, session)
        // An already-open proxy FD must still answer later callbacks with an error. Quitting its
        // looper before onRelease could strand a viewer in a blocked read after revocation.
        session.readers.toList().forEach(ReaderLease::stopReading)
        runCatching { session.context.revokeUriPermission(session.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun authority(context: Context) = "${context.packageName}.privileged-view"

    private fun openSource(context: Context, mode: PrivilegedAccessMode, path: String) = tryLocal(path) ?: PrivilegedRandomAccess.open(context, mode, path)

    /** Actual open/read permission check, not File.canRead() or an assumption about the volume. */
    private fun tryLocal(path: String): PrivilegedRandomAccess.ReadHandle? {
        val raw = try { Os.open(path, OsConstants.O_RDONLY or OsConstants.O_NONBLOCK or 0x80000, 0) }
        catch (error: ErrnoException) {
            if (error.errno in listOf(OsConstants.EACCES, OsConstants.EPERM, OsConstants.ENOENT, OsConstants.ENOTDIR, OsConstants.ELOOP)) return null
            throw error
        }
        try {
            val stat = Os.fstat(raw)
            if (!OsConstants.S_ISREG(stat.st_mode)) throw PrivilegedFileOperationException("UNSUPPORTED_TYPE", "Only regular files can be viewed")
            if (stat.st_size > 0) Os.pread(raw, ByteArray(1), 0, 1, 0)
            val descriptor = ParcelFileDescriptor.dup(raw)
            return LocalReader(descriptor, identity(stat))
        } finally { Os.close(raw) }
    }

    private class LocalReader(private val descriptor: ParcelFileDescriptor, override val identity: JSONObject) : PrivilegedRandomAccess.ReadHandle {
        override val size = identity.getLong("size")
        private val closed = AtomicBoolean()
        override fun checkAccess() {
            if (closed.get()) throw PrivilegedFileOperationException("ACCESS_UNAVAILABLE", "The file reader is closed")
            val current = PrivilegedExternalFiles.identity(Os.fstat(descriptor.fileDescriptor))
            if (identity.keys().asSequence().any { identity.getLong(it) != current.getLong(it) }) {
                throw PrivilegedFileOperationException("SOURCE_CHANGED", "The source file changed")
            }
        }
        override fun readAt(offset: Long, buffer: ByteArray, length: Int): Int {
            require(offset >= 0 && length in 0..buffer.size)
            checkAccess()
            if (offset >= size || length == 0) return 0
            val count = Os.pread(descriptor.fileDescriptor, buffer, 0, minOf(length.toLong(), size - offset, PrivilegedRandomAccess.MAX_CHUNK_BYTES.toLong()).toInt(), offset)
            checkAccess()
            return count.coerceAtLeast(0)
        }
        override fun close() { if (closed.compareAndSet(false, true)) descriptor.close() }
    }

    private fun identity(stat: StructStat) = JSONObject().put("dev", stat.st_dev).put("ino", stat.st_ino)
        .put("mode", stat.st_mode).put("size", stat.st_size).put("modified", stat.st_mtime * 1000L).put("changed", stat.st_ctime * 1000L)
        .put("uid", stat.st_uid).put("gid", stat.st_gid).put("links", stat.st_nlink)

    private fun cleanupCopies(root: File) {
        val cutoff = System.currentTimeMillis() - COPY_LIFETIME_MS
        root.listFiles().orEmpty().filter { it.name.matches(Regex("[a-f0-9-]{36}")) && it.path !in activeCopies && it.lastModified() < cutoff }
            .filter { runCatching { OsConstants.S_ISDIR(Os.lstat(it.path).st_mode) }.getOrDefault(false) }
            .forEach { directory -> directory.listFiles().orEmpty().forEach { it.delete() }; directory.delete() }
    }
}
