package org.autojs.autojs.model.explorer

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Base64
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class PrivilegedFileOperationException(
    val code: String,
    message: String,
    val partial: Boolean = false,
    val recoveryPaths: List<String> = emptyList(),
    cause: Throwable? = null,
) : IOException(message, cause) {
    internal var receipt: File? = null
}

/** No mutation is retried after a lost Binder/process reply: its outcome may already be committed. */
object PrivilegedFileOperations {
    private const val OPERATION_TIMEOUT_MS = 600_000L
    private const val OPEN_TIMEOUT_MS = 180_000L
    private const val MAX_OPEN_BYTES = 1024L * 1024 * 1024
    private const val CACHE_LIFETIME_MS = 24L * 60 * 60 * 1000
    private val directoryLeases = ConcurrentHashMap<String, ReentrantLock>()
    private val ipcExecutor = ThreadPoolExecutor(
        0, 2, 60L, TimeUnit.SECONDS, SynchronousQueue(),
        { task -> Thread(task, "ExplorerFileOperation").apply { isDaemon = true } },
    )

    fun stat(context: Context, mode: PrivilegedAccessMode, path: String): JSONObject =
        execute(context, mode, "stat", path)

    fun snapshot(context: Context, mode: PrivilegedAccessMode, path: String): JSONObject =
        execute(context, mode, "snapshot", path)

    internal fun backup(context: Context, mode: PrivilegedAccessMode, request: JSONObject): JSONObject = withDirectoryLease(request) {
        val mutation = request.getString("operation") in listOf("backup_delete", "backup_restore")
        val timeout = if (mutation) 180_000L else 60_000L
        request.put("deadlineMillis", System.currentTimeMillis() + timeout)
        configureRequest(context, request)
        executeRequest(context, mode, request, mutation, timeout)
    }

    /** Content travels through a descriptor/stdin, never through a Binder byte array or shell text. */
    internal fun save(context: Context, mode: PrivilegedAccessMode, request: JSONObject, contents: File): JSONObject = withDirectoryLease(request) {
        configureRequest(context, request)
        val remaining = (request.getLong("deadlineMillis") - System.currentTimeMillis()).coerceAtLeast(1L)
        executePrepared(context, mode, request, true, remaining, contents)
    }

    internal fun reconcileSave(context: Context, mode: PrivilegedAccessMode, attempt: JSONObject): JSONObject = withDirectoryLease(attempt) {
        val request = JSONObject(attempt.toString()).put("operation", "reconcile")
            .put("deadlineMillis", System.currentTimeMillis() + 30_000L)
        configureRequest(context, request)
        // Reconciliation may retire the old transaction, but never writes the original content.
        executeRequest(context, mode, request, false, 30_000L)
    }

    private fun <T> withDirectoryLease(request: JSONObject, action: () -> T): T {
        val source = File(request.optString("canonicalSource", request.getString("source")))
        val directory = if (request.optString("operation") == "backup_scan") source else source.parentFile ?: source
        val key = runCatching { directory.canonicalPath }.getOrDefault(directory.absolutePath)
        // Fair local queuing avoids a background cleanup racing the next save. Remote flock and
        // expected hashes remain authoritative against other processes and lost connections.
        return directoryLeases.getOrPut(key) { ReentrantLock(true) }.withLock(action)
    }

    fun execute(
        context: Context,
        mode: PrivilegedAccessMode,
        operation: String,
        source: String,
        destination: String? = null,
        expected: JSONObject? = null,
    ): JSONObject {
        val mutation = operation !in listOf("stat", "snapshot")
        val timeoutMs = if (mutation) OPERATION_TIMEOUT_MS else 60_000L
        val request = request(operation, source, expected, timeoutMs).apply {
            configureRequest(context, this)
            destination?.let { put("destination", it) }
        }
        return executePrepared(context, mode, request, mutation, timeoutMs)
    }

    private fun configureRequest(context: Context, request: JSONObject) {
        request.put("nativeLibrary", File(context.applicationInfo.nativeLibraryDir, "libautojs6_file_ops.so").path)
        request.put("apkPath", context.applicationInfo.sourceDir)
        request.put("client64Bit", android.os.Process.is64Bit())
    }

    private fun executePrepared(context: Context, mode: PrivilegedAccessMode, request: JSONObject, mutation: Boolean, timeoutMs: Long, contents: File? = null): JSONObject {
        // Editor sessions persist their own request, draft, and recovery paths before sending it.
        val receipt = if (mutation && request.optString("operation") != "save") PrivilegedFileOperationJournal.begin(context, SettingsJson.normalizeObject(request)) else null
        var preserveReceipt = false
        return try {
            executeRequest(context, mode, request, mutation, timeoutMs, contents).also { result ->
                if (receipt != null && result.optBoolean("cleanupPending")) {
                    preserveReceipt = true
                    result.put("_receipt", receipt.path)
                    val paths = result.optJSONArray("recoveryPaths")
                    PrivilegedFileOperationJournal.preserve(receipt, PrivilegedFileOperationException("CLEANUP_PENDING", "Temporary files remain", recoveryPaths =
                        if (paths == null) emptyList() else (0 until paths.length()).map(paths::getString)))
                }
            }
        } catch (error: PrivilegedFileOperationException) {
            if (receipt != null && (error.code == "OUTCOME_UNKNOWN" || error.partial || error.recoveryPaths.isNotEmpty())) {
                preserveReceipt = true
                error.receipt = receipt
                PrivilegedFileOperationJournal.preserve(receipt, error)
            }
            throw error
        } finally {
            if (!preserveReceipt) receipt?.let(PrivilegedFileOperationJournal::finish)
        }
    }

    private fun executeRequest(context: Context, mode: PrivilegedAccessMode, request: JSONObject, mutation: Boolean, timeoutMs: Long, contents: File? = null): JSONObject {
        val response = try {
            when (mode) {
                PrivilegedAccessMode.SHIZUKU -> callShizuku(timeoutMs + 5000) {
                    val service = requireShizuku()
                    when {
                        request.getString("operation").startsWith("backup_") -> service.manageExplorerFileBackup(request.toString())
                        request.getString("operation") == "snapshot" -> service.snapshotExplorerFile(request.toString())
                        request.getString("operation") == "reconcile" -> service.reconcileExplorerFileSave(request.toString())
                        request.getString("operation") == "save" -> ParcelFileDescriptor.open(checkNotNull(contents), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                            service.saveExplorerFile(request.toString(), descriptor)
                        }
                        else -> service.executeExplorerFileOperation(request.toString())
                    }
                }
                PrivilegedAccessMode.ROOT -> {
                    val result = contents?.inputStream().use { input ->
                        PrivilegedExplorerProcess.run(rootCommand(context, request, timeoutMs), timeoutMs + 10000, input)
                    }
                    if (result.exitCode != 0) {
                        val message = result.stderr.ifBlank { "Root exited with ${result.exitCode}" }
                        if (request.optString("operation") in listOf("save", "backup_delete", "backup_restore") && !result.stderr.contains(PrivilegedFileCommand.SAVE_STARTED_MARKER)) {
                            throw PrivilegedFileOperationException("ACCESS_UNAVAILABLE", message)
                        }
                        throw IOException(message)
                    }
                    result.stdout.toString(Charsets.UTF_8)
                }
            }
        } catch (error: PrivilegedFileOperationException) {
            throw error
        } catch (error: Exception) {
            throw PrivilegedFileOperationException(
                if (mutation) "OUTCOME_UNKNOWN" else "ACCESS_UNAVAILABLE",
                error.message ?: "File access is unavailable", cause = error,
            )
        }
        val result = try { JSONObject(response) } catch (error: Exception) {
            throw PrivilegedFileOperationException(if (mutation) "OUTCOME_UNKNOWN" else "IO", "Invalid operation response", cause = error)
        }
        if (!result.optBoolean("ok")) {
            val preserved = result.optJSONArray("recoveryPaths")
            throw PrivilegedFileOperationException(
                result.optString("code", "IO"), result.optString("message", "Operation failed"),
                result.optBoolean("partial"),
                if (preserved == null) emptyList() else (0 until preserved.length()).map(preserved::getString),
            )
        }
        return result
    }

    /** A private, read-only snapshot grants other apps access to this file only, never its parent. */
    fun materialize(context: Context, mode: PrivilegedAccessMode, path: String): File {
        val snapshot = stat(context, mode, path)
        if (snapshot.getString("kind") != "file") throw PrivilegedFileOperationException("UNSUPPORTED_TYPE", "Only regular files can be opened")
        val size = snapshot.getLong("size")
        val cacheRoot = File(context.cacheDir, "privileged-file-previews").apply { check(isDirectory || mkdirs()) }
        check(OsConstants.S_ISDIR(Os.lstat(cacheRoot.path).st_mode)) { "Preview storage must be a real directory" }
        cleanupPreviews(cacheRoot)
        if (size > MAX_OPEN_BYTES || size > cacheRoot.usableSpace - 32L * 1024 * 1024) {
            throw PrivilegedFileOperationException("OPEN_LIMIT", "Not enough space for a temporary file")
        }
        val directory = File(cacheRoot, UUID.randomUUID().toString()).apply { check(mkdir()) }
        val output = File(directory, File(path).name)
        val request = request("read", path, snapshot.getJSONObject("identity"), OPEN_TIMEOUT_MS)
        try {
            FileOutputStream(output).use { stream ->
                when (mode) {
                    PrivilegedAccessMode.SHIZUKU -> {
                        openShizukuDescriptor(request).use { descriptor ->
                            callShizuku(OPEN_TIMEOUT_MS) {
                                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                                    copyBounded(input, stream, size, request.getLong("deadlineMillis"))
                                }
                            }
                        }
                    }
                    PrivilegedAccessMode.ROOT -> readRoot(context, request, stream, size)
                }
                stream.fd.sync()
            }
            val after = stat(context, mode, path)
            if (after.getJSONObject("identity").toString() != snapshot.getJSONObject("identity").toString()) {
                throw PrivilegedFileOperationException("SOURCE_CHANGED", "File changed while opening")
            }
            output.setReadOnly()
            return output
        } catch (error: Exception) {
            output.delete()
            directory.delete()
            if (error is PrivilegedFileOperationException) throw error
            throw PrivilegedFileOperationException("ACCESS_UNAVAILABLE", error.message ?: "Unable to open file", cause = error)
        }
    }

    internal fun rootCommand(context: Context, request: JSONObject, timeoutMs: Long): List<String> {
        request.put("nativeLibrary", File(context.applicationInfo.nativeLibraryDir, "libautojs6_file_ops.so").path)
        val encoded = Base64.encodeToString(request.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val quote = PrivilegedExplorerProtocol::quoteShellArgument
        val matchingProcess = if (android.os.Process.is64Bit()) "/system/bin/app_process64" else "/system/bin/app_process32"
        val appProcess = matchingProcess.takeIf { File(it).exists() } ?: "/system/bin/app_process"
        val command = "CLASSPATH=${quote(context.applicationInfo.sourceDir)} /system/bin/toybox timeout -k 2 -s TERM ${timeoutMs / 1000 + 3} " +
            "$appProcess /system/bin ${PrivilegedFileCommand::class.java.name} ${quote(encoded)}"
        return listOf("su", "-c", command)
    }

    private fun readRoot(context: Context, request: JSONObject, output: FileOutputStream, size: Long) {
        val process = ProcessBuilder(rootCommand(context, request, OPEN_TIMEOUT_MS)).start()
        process.outputStream.close()
        val errors = StringBuffer()
        val stderr = thread(name = "ExplorerFileReadErrors", isDaemon = true) {
            runCatching { process.errorStream.bufferedReader().use { reader ->
                val buffer = CharArray(1024)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (errors.length < 4096) errors.append(buffer, 0, count.coerceAtMost(4096 - errors.length))
                }
            } }
        }
        val reader = FutureTask {
            process.inputStream.use { copyBounded(it, output, size, request.getLong("deadlineMillis")) }
        }
        thread(name = "ExplorerFileRead", isDaemon = true) { reader.run() }
        try {
            try {
                reader.get(OPEN_TIMEOUT_MS + 5000, TimeUnit.MILLISECONDS)
            } catch (error: ExecutionException) {
                throw error.cause ?: error
            }
            val exitDeadline = System.nanoTime() + 5_000_000_000L
            while (runCatching { process.exitValue() }.isFailure) {
                if (System.nanoTime() >= exitDeadline) throw IOException("Root file read did not finish")
                Thread.sleep(20)
            }
            stderr.join(1000)
            if (process.exitValue() != 0) throw IOException(errors.toString().ifBlank { "Root file read failed" })
        } finally {
            reader.cancel(true)
            thread(name = "ExplorerFileReadCleanup", isDaemon = true) {
                process.destroy()
                runCatching { process.inputStream.close() }
                runCatching { process.errorStream.close() }
            }
        }
    }

    private fun copyBounded(input: InputStream, output: FileOutputStream, expectedSize: Long, deadline: Long) {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            if (Thread.currentThread().isInterrupted || System.currentTimeMillis() >= deadline) throw IOException("File read interrupted or timed out")
            val count = input.read(buffer)
            if (count < 0) break
            copied += count
            if (copied > expectedSize || copied > MAX_OPEN_BYTES) throw PrivilegedFileOperationException("SOURCE_CHANGED", "File size changed while opening")
            output.write(buffer, 0, count)
        }
        if (copied != expectedSize) throw PrivilegedFileOperationException("SOURCE_CHANGED", "Incomplete file read")
    }

    private fun request(operation: String, source: String, expected: JSONObject?, timeoutMs: Long) = JSONObject()
        .put("operation", operation).put("source", source).put("deadlineMillis", System.currentTimeMillis() + timeoutMs)
        .apply { expected?.let { put("expected", it) } }

    private fun requireShizuku() = if (WrappedShizuku.isRunning() && WrappedShizuku.hasPermission()) {
        WrappedShizuku.getServiceOrNull() ?: throw PrivilegedFileOperationException("ACCESS_UNAVAILABLE", "Shizuku service is unavailable")
    } else throw PrivilegedFileOperationException("ACCESS_UNAVAILABLE", "Shizuku authorization is unavailable")

    /** A cancelled Binder future may still return a descriptor; retain ownership until handed off. */
    private fun openShizukuDescriptor(request: JSONObject): ParcelFileDescriptor {
        val lock = Any()
        var abandoned = false
        var pending: ParcelFileDescriptor? = null
        val future = ipcExecutor.submit<ParcelFileDescriptor> {
            val descriptor = requireShizuku().openExplorerFile(request.toString()) ?: throw IOException("No file descriptor returned")
            synchronized(lock) {
                if (abandoned) {
                    descriptor.close()
                    throw IOException("File open request was cancelled")
                }
                pending = descriptor
            }
            descriptor
        }
        try {
            val descriptor = future.get(10_000, TimeUnit.MILLISECONDS)
            synchronized(lock) { pending = null }
            return descriptor
        } catch (error: Exception) {
            synchronized(lock) {
                abandoned = true
                pending?.let { runCatching { it.close() } }
                pending = null
            }
            future.cancel(true)
            throw (error as? ExecutionException)?.cause ?: error
        }
    }

    private fun <T> callShizuku(timeoutMs: Long, block: () -> T): T {
        val task = ipcExecutor.submit<T>(block)
        try {
            return task.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        } catch (error: TimeoutException) {
            throw IOException("Shizuku file operation timed out", error)
        } finally {
            task.cancel(true)
        }
    }

    private fun cleanupPreviews(root: File) {
        val cutoff = System.currentTimeMillis() - CACHE_LIFETIME_MS
        root.listFiles()?.filter {
            it.name.matches(Regex("[a-f0-9-]{36}")) &&
                runCatching { OsConstants.S_ISDIR(Os.lstat(it.path).st_mode) }.getOrDefault(false) && it.lastModified() < cutoff
        }
            ?.forEach { directory -> directory.listFiles()?.forEach(File::delete); directory.delete() }
    }
}
