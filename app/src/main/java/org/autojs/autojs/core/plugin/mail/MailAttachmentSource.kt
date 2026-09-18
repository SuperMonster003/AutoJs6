package org.autojs.autojs.core.plugin.mail

import android.os.ParcelFileDescriptor
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes
import java.io.Closeable
import java.io.File

/**
 * Read-only descriptors of the attachment files a script passes to `mail.send` or
 * `messages.append` (roadmap P1.2). Paths are validated before anything is opened, and the
 * descriptors are closed together after `onResult`, whatever the outcome.
 */
class MailAttachmentSource private constructor(
    val files: List<File>,
    private val descriptors: List<ParcelFileDescriptor>,
) : Closeable {

    val descriptorArray: Array<ParcelFileDescriptor>
        get() = descriptors.toTypedArray()

    val totalBytes: Long
        get() = files.sumOf { it.length() }

    override fun close() {
        descriptors.forEach { descriptor -> runCatching { descriptor.close() } }
    }

    companion object {

        /** Checks count, existence, type, readability and size; returns the files in order. */
        fun validate(
            paths: List<String>,
            maxBytes: Long = MailContract.MAX_ATTACHMENT_BYTES,
            maxCount: Int = MailContract.MAX_ATTACHMENTS_PER_MESSAGE,
        ): List<File> {
            if (paths.size > maxCount) {
                throw failure(MailErrorCodes.LIMIT_EXCEEDED, "${paths.size} attachments exceed the limit of $maxCount")
            }
            return paths.map { path ->
                if (path.isBlank()) throw failure(MailErrorCodes.INVALID_ARGUMENT, "attachment path must not be blank")
                val file = File(path)
                if (!file.exists()) throw failure(MailErrorCodes.ATTACHMENT_NOT_FOUND, "attachment not found: $path")
                if (!file.isFile) throw failure(MailErrorCodes.INVALID_ARGUMENT, "attachment is not a file: $path")
                if (!file.canRead()) throw failure(MailErrorCodes.IO_FAILED, "attachment is not readable: $path")
                if (file.length() > maxBytes) throw failure(MailErrorCodes.LIMIT_EXCEEDED, "attachment exceeds $maxBytes bytes: $path")
                file
            }
        }

        fun open(
            paths: List<String>,
            maxBytes: Long = MailContract.MAX_ATTACHMENT_BYTES,
            maxCount: Int = MailContract.MAX_ATTACHMENTS_PER_MESSAGE,
        ): MailAttachmentSource {
            val files = validate(paths, maxBytes, maxCount)
            val opened = ArrayList<ParcelFileDescriptor>(files.size)
            try {
                files.forEach { file -> opened += ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
            } catch (t: Throwable) {
                opened.forEach { descriptor -> runCatching { descriptor.close() } }
                throw MailPluginException(MailErrorMapper.fromThrowable(t, MailErrorMapper.Phase.TRANSFER), t)
            }
            return MailAttachmentSource(files, opened)
        }

        private fun failure(code: String, message: String): MailPluginException {
            return MailPluginException(MailErrorMapper.normalize(code, message, retryable = false))
        }
    }
}
