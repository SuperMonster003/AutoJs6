package org.autojs.autojs.model.explorer

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

enum class PrivilegedAccessMode { SHIZUKU, ROOT }

class PrivilegedExplorerAccessException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    enum class Reason { PERMISSION_DENIED, SERVICE_UNAVAILABLE, TOO_LARGE, TIMEOUT, INVALID_DATA, IO }
}

internal data class PrivilegedExplorerEntry(
    val name: String,
    val directory: Boolean,
    val size: Long,
    val modified: Long,
)

internal data class PrivilegedExplorerListing(
    val path: String,
    val modified: Long,
    val entries: List<PrivilegedExplorerEntry>,
)

/** Read-only wire formats shared by the app and its authorized directory readers. */
internal object PrivilegedExplorerProtocol {
    const val MAX_ENTRIES = 20_000
    const val MAX_OUTPUT_BYTES = 8 * 1024 * 1024
    const val MAX_PAGE_CHARS = 48 * 1024
    const val MAX_PAGE_ENTRIES = 128
    const val TIMEOUT_MS = 60_000L
    private const val ROOT_COMMAND_TIMEOUT_SECONDS = 55

    fun checkedPath(path: String): String {
        require(path.startsWith('/') && '\u0000' !in path && path.toByteArray(Charsets.UTF_8).size <= 4096) {
            "Expected an absolute directory path without NUL characters (maximum 4096 bytes)"
        }
        return path.trimEnd('/').ifEmpty { "/" }
    }

    fun checkedName(name: String): String {
        require(name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\u0000' !in name) {
            "Invalid directory entry name"
        }
        return name
    }

    fun childPath(parent: String, name: String): String = "${parent.trimEnd('/')}/${checkedName(name)}"

    fun quoteShellArgument(value: String): String {
        require('\u0000' !in value) { "A shell argument cannot contain NUL" }
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    // These timeout flags are present in Android 7.0's toybox as well as current Android versions.
    // Keep this deadline shorter than the caller's timeout, including the two-second KILL grace.
    fun withRootDeadline(command: String, seconds: Int = ROOT_COMMAND_TIMEOUT_SECONDS): String {
        require(seconds in 1..ROOT_COMMAND_TIMEOUT_SECONDS)
        return "/system/bin/toybox timeout -k 2 -s TERM $seconds /system/bin/sh -c ${quoteShellArgument(command)}"
    }

    /**
     * Filenames never become shell syntax or newline-delimited records. Numeric stat fields are
     * separated from names, and a final count detects an interrupted or incomplete listing.
     * No command in this script modifies the filesystem.
     */
    fun rootCommand(path: String): String = """
        export LC_ALL=C
        set -o pipefail || exit 17
        cd ${quoteShellArgument(checkedPath(path))} || exit 11
        modified=${'$'}(/system/bin/stat -L -c '%Y' .) || exit 13
        printf 'AJ6EXPLORER1\000%s\000' "${'$'}modified"
        /system/bin/find . -mindepth 1 -maxdepth 1 -print0 | (
            count=0
            while IFS= read -r -d '' file; do
                count=${'$'}((count + 1))
                [ "${'$'}count" -le $MAX_ENTRIES ] || exit 15
                metadata=${'$'}(/system/bin/stat -L -c '%s %Y' "${'$'}file" 2>/dev/null) || metadata=${'$'}(/system/bin/stat -c '%s %Y' "${'$'}file") || exit 14
                set -- ${'$'}metadata
                [ "${'$'}#" -eq 2 ] || exit 16
                kind=f
                if [ -d "${'$'}file" ]; then kind=d; fi
                printf 'entry\000%s\000%s\000%s\000%s\000' "${'$'}{file#./}" "${'$'}kind" "${'$'}1" "${'$'}2"
            done
            printf 'end\000%s\000' "${'$'}count"
        )
    """.trimIndent()

    fun parseRootListing(path: String, output: ByteArray): PrivilegedExplorerListing {
        try {
            require(output.size <= MAX_OUTPUT_BYTES) { "Directory response is too large" }
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output)).toString()
            val fields = text.split('\u0000')
            require(fields.size >= 5 && fields[0] == "AJ6EXPLORER1" && fields.last().isEmpty()) { "Incomplete directory response" }
            val modified = secondsToMillis(fields[1])
            val entries = ArrayList<PrivilegedExplorerEntry>()
            val names = HashSet<String>()
            var index = 2
            while (index < fields.lastIndex && fields[index] == "entry") {
                require(index + 4 < fields.lastIndex && entries.size < MAX_ENTRIES) { "Invalid directory entry count" }
                val name = checkedName(fields[index + 1])
                require(names.add(name)) { "Duplicate directory entry" }
                val kind = fields[index + 2]
                require(kind == "d" || kind == "f") { "Invalid directory entry type" }
                val size = fields[index + 3].toLong().also { require(it >= 0) { "Invalid file size" } }
                entries += PrivilegedExplorerEntry(name, kind == "d", size, secondsToMillis(fields[index + 4]))
                index += 5
            }
            require(index + 2 == fields.lastIndex && fields[index] == "end" && fields[index + 1].toInt() == entries.size) {
                "Incomplete directory response"
            }
            return PrivilegedExplorerListing(checkedPath(path), modified, entries)
        } catch (error: Exception) {
            throw PrivilegedExplorerAccessException(
                PrivilegedExplorerAccessException.Reason.INVALID_DATA,
                "Unable to read the complete privileged directory listing: ${error.message}",
                error,
            )
        }
    }

    private fun secondsToMillis(value: String): Long = Math.multiplyExact(value.toLong(), 1000L)
}

/** Drains both pipes concurrently; never waits for a full pipe before reading it. */
internal object PrivilegedExplorerProcess {
    data class Result(val exitCode: Int, val stdout: ByteArray, val stderr: String)

    fun run(command: List<String>, timeoutMs: Long = PrivilegedExplorerProtocol.TIMEOUT_MS): Result {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Directory request cancelled")
        val process = ProcessBuilder(command).start()
        process.outputStream.close()
        val exceeded = AtomicBoolean(false)
        val stdout = Capture(process.inputStream, PrivilegedExplorerProtocol.MAX_OUTPUT_BYTES, exceeded)
        val stderr = Capture(process.errorStream, 64 * 1024, exceeded)
        val readers = listOf(stdout, stderr).mapIndexed { index, capture ->
            thread(name = "ExplorerRootOutput-$index", isDaemon = true) { capture.drain() }
        }
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        try {
            while (true) {
                if (exceeded.get()) {
                    throw PrivilegedExplorerAccessException(PrivilegedExplorerAccessException.Reason.TOO_LARGE, "Directory response exceeds the safe output limit")
                }
                val exitCode = try { process.exitValue() } catch (_: IllegalThreadStateException) { null }
                if (exitCode != null) {
                    readers.forEach { it.join(1000L) }
                    if (readers.any { it.isAlive }) throw IOException("Root output stream did not close")
                    if (exceeded.get()) {
                        throw PrivilegedExplorerAccessException(PrivilegedExplorerAccessException.Reason.TOO_LARGE, "Directory response exceeds the safe output limit")
                    }
                    stdout.failure?.let { throw IOException("Unable to read Root output", it) }
                    stderr.failure?.let { throw IOException("Unable to read Root error output", it) }
                    return Result(exitCode, stdout.bytes(), stderr.bytes().toString(Charsets.UTF_8))
                }
                if (System.nanoTime() >= deadline) {
                    throw PrivilegedExplorerAccessException(PrivilegedExplorerAccessException.Reason.TIMEOUT, "Root directory access timed out")
                }
                Thread.sleep(25L)
            }
        } finally {
            // A su descendant can still own a pipe after the client process exits. In particular,
            // close() can wait on a reader's monitor, so it must not delay cancellation or the UI.
            // The authorized command also has its own native deadline, independent of this reader.
            thread(name = "ExplorerRootCleanup", isDaemon = true) {
                runCatching { process.destroy() }
                runCatching { process.inputStream.close() }
                runCatching { process.errorStream.close() }
            }
            readers.forEach { if (it.isAlive) it.interrupt() }
        }
    }

    private class Capture(private val input: InputStream, private val limit: Int, private val exceeded: AtomicBoolean) {
        private val output = ByteArrayOutputStream()
        @Volatile var failure: IOException? = null
            private set

        fun drain() {
            try {
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) return
                    val available = limit - output.size()
                    if (count > available) exceeded.set(true)
                    if (available > 0) output.write(buffer, 0, count.coerceAtMost(available))
                }
            } catch (error: IOException) {
                failure = error
            }
        }

        fun bytes(): ByteArray = output.toByteArray()
    }
}
