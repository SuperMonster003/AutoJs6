package org.autojs.autojs.model.explorer

import android.content.Context
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.Base64
import androidx.annotation.VisibleForTesting
import org.autojs.autojs.model.explorer.PrivilegedReadProtocol as Protocol
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

/** Bounded, seekable reads of a pinned regular-file descriptor, without staging or content hashes. */
object PrivilegedRandomAccess {
    const val MAX_CHUNK_BYTES = Protocol.MAX_CHUNK

    interface ReadHandle : Closeable {
        val size: Long
        /** A fresh JSON value is returned so callers cannot alter the internal comparison baseline. */
        val identity: JSONObject
        /** Reads at most 256 KiB into buffer[0..length); EOF is 0 and partial reads are valid. */
        fun readAt(offset: Long, buffer: ByteArray, length: Int): Int
        fun checkAccess()
    }

    @JvmOverloads
    fun open(context: Context, mode: PrivilegedAccessMode, path: String, expected: JSONObject? = null): ReadHandle {
        val request = openRequest(path, expected)
        return when (mode) {
            PrivilegedAccessMode.SHIZUKU -> openShizuku(request)
            PrivilegedAccessMode.ROOT -> openRootProcess(rootCommand(context, request), Protocol.READ_TIMEOUT_MS)
        }
    }

    private fun openRequest(path: String, expected: JSONObject?) = JSONObject().put("source", path)
        .put("deadlineMillis", System.currentTimeMillis() + Protocol.OPEN_TIMEOUT_MS)
        .apply { expected?.let { put("expected", JSONObject(it.toString())) } }

    /** Test shells exercise the same packaged entry point without requesting Root authorization. */
    @VisibleForTesting
    internal fun rootCommand(context: Context, request: JSONObject): List<String> {
        val encoded = Base64.encodeToString(request.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        require(encoded.length <= 65536)
        val quote = PrivilegedExplorerProtocol::quoteShellArgument
        val preferred = if (android.os.Process.is64Bit()) "/system/bin/app_process64" else "/system/bin/app_process32"
        val process = if (File(preferred).exists()) preferred else "/system/bin/app_process"
        val command = "CLASSPATH=${quote(context.applicationInfo.sourceDir)} /system/bin/toybox timeout -k 2 -s TERM " +
            "${Protocol.MAX_LIFETIME_MS / 1000} $process /system/bin ${PrivilegedReadCommand::class.java.name} ${quote(encoded)}"
        return listOf("su", "-c", command)
    }

    @VisibleForTesting
    internal fun openRootProcess(command: List<String>, requestTimeoutMillis: Long = Protocol.READ_TIMEOUT_MS): ReadHandle {
        require(requestTimeoutMillis in 1..Protocol.READ_TIMEOUT_MS)
        val process = try { ProcessBuilder(command).start() } catch (error: Exception) {
            throw Protocol.failure("ACCESS_UNAVAILABLE", "Unable to start the authorized file reader", error)
        }
        val handle = RootHandle(process, requestTimeoutMillis)
        try {
            handle.initialize()
            return handle
        } catch (error: Exception) {
            handle.close()
            throw error
        }
    }

    @VisibleForTesting
    internal fun openShizukuForTest(context: Context, path: String, accessCheck: () -> Boolean): ReadHandle =
        openShizuku(openRequest(path, null), accessCheck)

    @VisibleForTesting
    internal fun processIdForTest(handle: ReadHandle): Int = (handle as RootHandle).helperPid

    private fun openShizuku(request: JSONObject, additionalCheck: (() -> Boolean)? = null): ReadHandle {
        val handle = DescriptorHandle(Protocol.READ_TIMEOUT_MS)
        try {
            handle.initialize(request, additionalCheck)
            return handle
        } catch (error: Exception) {
            handle.close()
            throw error
        }
    }

    private abstract class BoundedHandle(private val timeoutMillis: Long) : ReadHandle {
        protected val closed = AtomicBoolean(false)
        protected val scratch = ByteArray(Protocol.MAX_CHUNK)
        protected lateinit var baseline: Protocol.Identity
        private val end = System.nanoTime() + Protocol.MAX_LIFETIME_MS * 1_000_000L
        private val requestLock = ReentrantLock()
        private val worker = Executors.newSingleThreadExecutor { work -> Thread(work, "PrivilegedFileRead").apply { isDaemon = true } }
        @Volatile private var activeTask: Future<Int>? = null

        override val size: Long get() = baseline.size
        override val identity: JSONObject get() = baseline.json()

        final override fun readAt(offset: Long, buffer: ByteArray, length: Int): Int {
            require(offset >= 0L) { "offset must not be negative" }
            require(length >= 0 && length <= buffer.size) { "length must fit the destination buffer" }
            val wanted = minOf(length, Protocol.MAX_CHUNK)
            return execute {
                val read = performRead(offset, wanted)
                if (read !in 0..wanted) throw IOException("Invalid privileged read count")
                read
            } after { read ->
                // Copy only after the bounded worker has completed. A cancelled read can never
                // write to a buffer that a ProxyFileDescriptor callback has already returned.
                if (read != 0) System.arraycopy(scratch, 0, buffer, 0, read)
            }
        }

        final override fun checkAccess() { execute { performCheck(); 0 } after {} }

        /** Keeps request serialization until any caller-buffer copy has also completed. */
        protected fun execute(action: () -> Int): Pending = Pending(action)

        protected inner class Pending(private val action: () -> Int) {
            infix fun after(finished: (Int) -> Unit): Int {
                val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
                var locked = false
                var task: Future<Int>? = null
                try {
                    requireOpen()
                    locked = requestLock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS)
                    if (!locked) throw TimeoutException("Another file read did not finish")
                    requireOpen()
                    task = worker.submit<Int> { requireOpen(); action() }
                    activeTask = task
                    if (closed.get()) task.cancel(true)
                    val remaining = deadline - System.nanoTime()
                    if (remaining <= 0) throw TimeoutException("File read deadline expired")
                    val count = task.get(remaining, TimeUnit.NANOSECONDS)
                    requireOpen()
                    finished(count)
                    return count
                } catch (error: Exception) {
                    task?.cancel(true)
                    close()
                    if (error is InterruptedException) Thread.currentThread().interrupt()
                    val cause = (error as? ExecutionException)?.cause ?: error
                    if (cause is PrivilegedFileOperationException) throw cause
                    if (cause is TimeoutException) throw Protocol.failure("TIMEOUT", "Privileged file access timed out; reopen the file", cause)
                    throw Protocol.failure(Protocol.code(cause), cause.message ?: "Privileged file access was interrupted", cause)
                } finally {
                    if (activeTask === task) activeTask = null
                    if (locked) requestLock.unlock()
                }
            }
        }

        protected fun requireOpen() {
            if (closed.get() || System.nanoTime() >= end) throw Protocol.failure("ACCESS_UNAVAILABLE", "The privileged file session is closed or expired")
            if (Thread.currentThread().isInterrupted) throw InterruptedException("File read was cancelled")
        }

        protected abstract fun performRead(offset: Long, count: Int): Int
        protected abstract fun performCheck()
        protected abstract fun release()

        final override fun close() {
            if (closed.compareAndSet(false, true)) {
                activeTask?.cancel(true)
                worker.shutdownNow()
                release()
            }
        }
    }

    private class DescriptorHandle(timeout: Long) : BoundedHandle(timeout) {
        private val ownership = Any()
        private var descriptor: ParcelFileDescriptor? = null
        private val activeDescriptors = LinkedHashSet<ParcelFileDescriptor>()
        private var binder: IBinder? = null
        private var additionalCheck: (() -> Boolean)? = null
        private val death = IBinder.DeathRecipient { close() }

        fun initialize(request: JSONObject, additionalCheck: (() -> Boolean)?) {
            this.additionalCheck = additionalCheck
            execute {
                checkAuthorization()
                val service = WrappedShizuku.getServiceOrNull() ?: throw Protocol.failure("ACCESS_UNAVAILABLE", "Shizuku service is unavailable")
                val remote = service.asBinder()
                if (!remote.isBinderAlive || !remote.pingBinder()) throw Protocol.failure("ACCESS_UNAVAILABLE", "Shizuku service disconnected")
                val stat = JSONObject(service.executeExplorerFileOperation(JSONObject(request.toString()).put("operation", "stat").toString()))
                Protocol.checkSuccess(stat)
                if (stat.optString("kind") != "file") throw Protocol.failure("UNSUPPORTED_TYPE", "Only regular files support random access")
                val openRequest = JSONObject(request.toString()).put("source", stat.getString("path")).put("expected", stat.getJSONObject("identity"))
                val opened = try {
                    service.openExplorerFile(openRequest.toString()) ?: throw Protocol.failure("ACCESS_UNAVAILABLE", "Shizuku did not return a file descriptor")
                } catch (error: IllegalStateException) {
                    // This existing AIDL method serializes its ErrnoException as an
                    // IllegalStateException message, rather than returning a JSON error object.
                    val message = error.message.orEmpty()
                    val code = when {
                        "EACCES (" in message || "EPERM (" in message -> "PERMISSION_DENIED"
                        "ENOENT (" in message -> "NOT_FOUND"
                        message.startsWith("Source changed") || message.startsWith("File changed") -> "SOURCE_CHANGED"
                        else -> "ACCESS_UNAVAILABLE"
                    }
                    throw Protocol.failure(code, message.ifBlank { "Shizuku could not open the selected file" }, error)
                }
                // Binder cancellation does not necessarily cancel the remote open. Ownership is
                // transferred only while this handle is still live; a late descriptor is closed.
                var transferred = false
                try {
                    val initial = Protocol.Identity.from(Os.fstat(opened.fileDescriptor))
                    Protocol.requireExpected(stat.getJSONObject("identity"), initial)
                    Protocol.requireExpected(request.optJSONObject("expected"), initial)
                    synchronized(ownership) {
                        requireOpen()
                        descriptor = opened
                        binder = remote
                        baseline = initial
                        transferred = true
                    }
                    remote.linkToDeath(death, 0)
                    if (closed.get()) runCatching { remote.unlinkToDeath(death, 0) }
                } finally {
                    if (!transferred) opened.close()
                }
                performCheck()
                0
            } after {}
        }

        private fun checkAuthorization() {
            if (!WrappedShizuku.isRunning() || !WrappedShizuku.hasPermission() || additionalCheck?.invoke() == false) {
                throw Protocol.failure("ACCESS_UNAVAILABLE", "Shizuku authorization is no longer available")
            }
        }

        private fun checkedDescriptor(): ParcelFileDescriptor {
            requireOpen()
            checkAuthorization()
            val remote = synchronized(ownership) { binder } ?: throw Protocol.failure("ACCESS_UNAVAILABLE", "Shizuku service is unavailable")
            if (!remote.isBinderAlive || !remote.pingBinder()) throw Protocol.failure("ACCESS_UNAVAILABLE", "Shizuku service disconnected")
            return synchronized(ownership) {
                requireOpen()
                val opened = descriptor ?: throw Protocol.failure("ACCESS_UNAVAILABLE", "The file descriptor has been closed")
                ParcelFileDescriptor.dup(opened.fileDescriptor).also(activeDescriptors::add)
            }
        }

        private inline fun <T> withDescriptor(action: (ParcelFileDescriptor) -> T): T {
            val active = checkedDescriptor()
            return try { action(active) } finally {
                synchronized(ownership) { activeDescriptors.remove(active) }
                runCatching { active.close() }
            }
        }

        override fun performRead(offset: Long, count: Int): Int = withDescriptor { descriptor ->
            val read = Protocol.read(descriptor, baseline, offset, scratch, count)
            checkAuthorization()
            if (binder?.isBinderAlive != true) throw Protocol.failure("ACCESS_UNAVAILABLE", "Shizuku service disconnected during the read")
            read
        }

        override fun performCheck() = withDescriptor { baseline.check(Os.fstat(it.fileDescriptor)) }

        override fun release() {
            val (remote, opened) = synchronized(ownership) {
                val resources = binder to (activeDescriptors.toList() + listOfNotNull(descriptor))
                activeDescriptors.clear()
                binder = null
                descriptor = null
                resources
            }
            runCatching { remote?.unlinkToDeath(death, 0) }
            // Closing an fd cannot guarantee interruption of uninterruptible filesystem I/O,
            // but this handle relinquishes every owned descriptor immediately on cancellation.
            opened.forEach { runCatching { it.close() } }
        }
    }

    private class RootHandle(private val process: Process, timeout: Long) : BoundedHandle(timeout) {
        var helperPid: Int = -1
            private set
        private val input = DataInputStream(BufferedInputStream(process.inputStream, 8192))
        private val output = DataOutputStream(process.outputStream)
        private val writeLock = Any()
        private val errors = StringBuilder()
        private var nextId = 0
        private val stderr = thread(name = "PrivilegedReadErrors", isDaemon = true) {
            runCatching {
                process.errorStream.reader(Charsets.UTF_8).use { reader ->
                    val buffer = CharArray(1024)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        synchronized(errors) {
                            val room = 4096 - errors.length
                            if (room > 0) errors.append(buffer, 0, count.coerceAtMost(room))
                        }
                    }
                }
            }
        }

        fun initialize() {
            execute {
                if (input.readInt() != Protocol.MAGIC || input.readInt() != Protocol.VERSION) {
                    throw Protocol.failure("ACCESS_UNAVAILABLE", "The privileged reader returned an invalid handshake")
                }
                val hello = Protocol.readJson(input)
                Protocol.checkSuccess(hello)
                baseline = Protocol.Identity.from(hello.getJSONObject("identity"))
                helperPid = hello.optInt("pid", -1)
                if (hello.getLong("size") != baseline.size) throw IOException("Inconsistent file size in privileged reader handshake")
                0
            } after {}
        }

        private fun request(operation: Int, offset: Long, count: Int): Int {
            requireOpen()
            if (!alive(process)) throw unavailable()
            val id = if (nextId == Int.MAX_VALUE) 1 else nextId + 1
            nextId = id
            val frame = ByteBuffer.allocate(28).putInt(operation).putInt(id).putLong(offset).putInt(count)
                .putLong(System.currentTimeMillis() + Protocol.READ_TIMEOUT_MS).array()
            synchronized(writeLock) { output.write(frame); output.flush() }
            val responseId = input.readInt()
            val status = input.readInt()
            if (responseId != id) throw Protocol.failure("ACCESS_UNAVAILABLE", "Out-of-order privileged reader response")
            if (status == 1) { Protocol.checkSuccess(Protocol.readJson(input)); throw IOException("Malformed reader error response") }
            if (status != 0) throw IOException("Invalid privileged reader status")
            val received = input.readInt()
            val maximum = if (operation == Protocol.CHECK || offset >= baseline.size) 0 else minOf(count.toLong(), baseline.size - offset).toInt()
            if (received !in 0..maximum || received > Protocol.MAX_CHUNK) throw IOException("Oversized privileged reader response")
            input.readFully(scratch, 0, received)
            return received
        }

        override fun performRead(offset: Long, count: Int): Int = request(Protocol.READ, offset, count)
        override fun performCheck() { request(Protocol.CHECK, 0L, 0) }

        private fun unavailable() = Protocol.failure("ACCESS_UNAVAILABLE", synchronized(errors) {
            errors.toString().ifBlank { "The authorized reader process disconnected" }
        })

        override fun release() {
            // Never wait on a pipe reader's monitor on the provider/UI thread. Closing stdin
            // releases an idle helper promptly; destroying the process handles failed requests.
            thread(name = "PrivilegedReadCleanup", isDaemon = true) {
                runCatching {
                    val frame = ByteBuffer.allocate(28).putInt(Protocol.CLOSE).putInt(0).putLong(0L).putInt(0).putLong(0L).array()
                    synchronized(writeLock) { output.write(frame); output.flush(); output.close() }
                }
                val end = System.nanoTime() + 500_000_000L
                while (alive(process) && System.nanoTime() < end) Thread.sleep(10)
                if (alive(process)) runCatching { process.destroy() }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val killDeadline = System.nanoTime() + 500_000_000L
                    while (alive(process) && System.nanoTime() < killDeadline) Thread.sleep(10)
                    if (alive(process)) runCatching { process.destroyForcibly() }
                }
                runCatching { process.inputStream.close() }
                runCatching { process.errorStream.close() }
                stderr.interrupt()
            }
        }
    }

    private fun alive(process: Process) = try { process.exitValue(); false } catch (_: IllegalThreadStateException) { true }
}
