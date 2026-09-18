package org.autojs.autojs.core.plugin.mail

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Receives one streamed download (`messages.raw`, `attachments.download`) on the host side
 * (roadmap P1.2): the plugin writes into the pipe write end handed over with the request, the host
 * closes its copy of that end right after the request was accepted, and [await] copies the read
 * end into `<target>.part`, checks the declared size and the ceiling, and renames the part file
 * onto the target only when everything arrived. The pure copy logic is [spool].
 */
class MailAttachmentSink private constructor(
    val target: File,
    private val readEnd: ParcelFileDescriptor,
    private val writeEnd: ParcelFileDescriptor,
    private val expectedSize: Long?,
    private val maxBytes: Long,
) : Closeable {

    private val cancelled = AtomicBoolean(false)
    private val writeEndClosed = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    /** The write end that travels with the request; the plugin duplicates it. */
    val descriptor: ParcelFileDescriptor
        get() = writeEnd

    /** Closes the host copy of the write end so the read end sees end-of-stream when the plugin is done. */
    fun closeWriteEnd() {
        if (writeEndClosed.compareAndSet(false, true)) runCatching { writeEnd.close() }
    }

    fun cancel() {
        cancelled.set(true)
        closeWriteEnd()
    }

    /** Drains the pipe into the target file; returns the final path. */
    suspend fun await(): File = withContext(Dispatchers.IO) {
        spool(ParcelFileDescriptor.AutoCloseInputStream(readEnd), target, expectedSize, maxBytes) { cancelled.get() }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            closeWriteEnd()
            runCatching { readEnd.close() }
            partFile(target).delete()
        }
    }

    companion object {

        const val BUFFER_SIZE = 64 * 1024

        /** Free space that must remain after the download when the size is known. */
        const val SPACE_MARGIN_BYTES = 8L * 1024 * 1024

        /**
         * Creates the pipe for a download into [targetPath]: a directory (existing or ending with a
         * separator) receives the sanitized [fileName], any other path is used as the file itself.
         */
        fun create(
            targetPath: String,
            fileName: String?,
            expectedSize: Long?,
            overwrite: Boolean,
            maxBytes: Long = MailContract.MAX_ATTACHMENT_BYTES,
        ): MailAttachmentSink {
            val target = resolveTarget(targetPath, fileName, overwrite)
            precheck(target, expectedSize, maxBytes)
            val pipe = ParcelFileDescriptor.createReliablePipe()
            return MailAttachmentSink(target, pipe[0], pipe[1], expectedSize, maxBytes)
        }

        /**
         * A path that is an existing directory or ends with a separator receives the sanitized
         * [fileName]; any other path names the file itself. The separator check happens on the
         * string because `java.io.File` drops trailing separators.
         */
        fun resolveTarget(targetPath: String, fileName: String?, overwrite: Boolean): File {
            require(targetPath.isNotBlank()) { "target path must not be blank" }
            val path = File(targetPath)
            val treatAsDirectory = path.isDirectory || targetPath.endsWith("/") || targetPath.endsWith(File.separator)
            val directory = if (treatAsDirectory) path else path.absoluteFile.parentFile ?: File(".")
            val name = if (treatAsDirectory) MailFileNames.sanitize(fileName) else path.name
            if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
                throw failure(MailErrorCodes.IO_FAILED, "cannot create directory ${directory.path}")
            }
            val target = File(directory, name)
            return if (overwrite || !target.exists()) target else MailFileNames.unique(directory, name)
        }

        fun precheck(target: File, expectedSize: Long?, maxBytes: Long) {
            if (expectedSize != null && expectedSize > maxBytes) {
                throw failure(MailErrorCodes.LIMIT_EXCEEDED, "attachment of $expectedSize bytes exceeds $maxBytes bytes")
            }
            val directory = target.absoluteFile.parentFile ?: return
            if (expectedSize != null && directory.isDirectory) {
                val usable = directory.usableSpace
                if (usable > 0L && usable < expectedSize + SPACE_MARGIN_BYTES) {
                    throw failure(MailErrorCodes.IO_FAILED, "insufficient space for $expectedSize bytes in ${directory.path}")
                }
            }
        }

        /**
         * Copies [input] into `<target>.part` and renames it onto [target]. Fails with
         * `LIMIT_EXCEEDED` above [maxBytes], `IO_FAILED` when the byte count differs from
         * [expectedSize], and `CANCELLED` when [isCancelled] turns true; the part file is deleted
         * on every failure and the target is never left half-written.
         */
        fun spool(
            input: InputStream,
            target: File,
            expectedSize: Long?,
            maxBytes: Long = MailContract.MAX_ATTACHMENT_BYTES,
            isCancelled: () -> Boolean = { false },
        ): File {
            precheck(target, expectedSize, maxBytes)
            val part = partFile(target)
            var written = 0L
            try {
                input.use { source ->
                    FileOutputStream(part).use { out ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            if (isCancelled()) throw failure(MailErrorCodes.CANCELLED, "attachment download cancelled")
                            val read = source.read(buffer)
                            if (read < 0) break
                            written += read
                            if (written > maxBytes) throw failure(MailErrorCodes.LIMIT_EXCEEDED, "attachment exceeds $maxBytes bytes")
                            if (expectedSize != null && written > expectedSize) {
                                throw failure(MailErrorCodes.IO_FAILED, "attachment exceeds its declared size of $expectedSize bytes")
                            }
                            out.write(buffer, 0, read)
                        }
                        out.flush()
                    }
                }
                if (isCancelled()) throw failure(MailErrorCodes.CANCELLED, "attachment download cancelled")
                if (expectedSize != null && written != expectedSize) {
                    throw failure(MailErrorCodes.IO_FAILED, "attachment ended after $written of $expectedSize bytes")
                }
                move(part, target)
                return target
            } catch (t: Throwable) {
                part.delete()
                throw if (t is MailPluginException) t else MailPluginException(MailErrorMapper.fromThrowable(t, MailErrorMapper.Phase.TRANSFER), t)
            }
        }

        fun partFile(target: File): File = File(target.absoluteFile.parentFile, target.name + ".part")

        private fun move(part: File, target: File) {
            if (target.exists() && !target.delete()) {
                throw failure(MailErrorCodes.IO_FAILED, "cannot replace ${target.path}")
            }
            if (!part.renameTo(target)) {
                throw failure(MailErrorCodes.IO_FAILED, "cannot move ${part.name} to ${target.path}")
            }
        }

        private fun failure(code: String, message: String): MailPluginException {
            return MailPluginException(MailErrorMapper.normalize(code, message, retryable = false))
        }
    }
}
