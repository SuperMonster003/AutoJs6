package org.autojs.autojs.core.plugin.epub

import android.os.ParcelFileDescriptor
import org.autojs.plugin.epub.api.EpubErrorCodes
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/**
 * The read-only descriptor of the book a script names by path (plugin roadmap P5.4, D5 / D19):
 * the path is validated before anything is opened (existence, regular file, readability, and an
 * `.epub` name or the ZIP signature `PK`; no size ceiling, roadmap Q-none), the
 * descriptor travels with `openBook` / `openReader`, and the host closes its copy right after the
 * call returns because the plugin duplicates it.
 */
class EpubSource private constructor(
    val file: File,
    val descriptor: ParcelFileDescriptor,
) : Closeable {

    override fun close() {
        runCatching { descriptor.close() }
    }

    companion object {

        private const val EPUB_EXTENSION = "epub"
        private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

        /** Checks the path without opening a descriptor; returns the file. */
        fun validate(path: String): File {
            if (path.isBlank()) throw failure(EpubErrorCodes.INVALID_ARGUMENT, "path must not be blank")
            val file = File(path)
            if (!file.exists()) throw failure(EpubErrorCodes.FILE_NOT_FOUND, "file not found: $path")
            if (!file.isFile) throw failure(EpubErrorCodes.INVALID_ARGUMENT, "not a regular file: $path")
            if (!file.canRead()) throw failure(EpubErrorCodes.FILE_UNREADABLE, "file is not readable: $path")
            if (!isEpubName(file.name) && !hasZipSignature(file)) {
                throw failure(EpubErrorCodes.NOT_EPUB, "neither an .epub name nor a ZIP container: $path")
            }
            return file
        }

        fun isEpubName(name: String): Boolean =
            name.substringAfterLast('.', "").lowercase(Locale.ROOT) == EPUB_EXTENSION

        fun hasZipSignature(file: File): Boolean = runCatching {
            FileInputStream(file).use { input ->
                val head = ByteArray(ZIP_SIGNATURE.size)
                var read = 0
                while (read < head.size) {
                    val n = input.read(head, read, head.size - read)
                    if (n < 0) break
                    read += n
                }
                read == head.size && head.contentEquals(ZIP_SIGNATURE)
            }
        }.getOrDefault(false)

        fun open(path: String): EpubSource {
            val file = validate(path)
            val descriptor = try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (t: Throwable) {
                throw EpubPluginException(EpubErrorMapper.fromThrowable(t, EpubErrorMapper.Phase.OPEN), t)
            }
            return EpubSource(file, descriptor)
        }

        private fun failure(code: String, message: String): EpubPluginException =
            EpubPluginException(EpubErrorMapper.normalize(code, message))
    }
}
