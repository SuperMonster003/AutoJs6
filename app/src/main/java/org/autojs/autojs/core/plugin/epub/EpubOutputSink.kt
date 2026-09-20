package org.autojs.autojs.core.plugin.epub

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubErrorCodes
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Lands one exported resource (cover, image, chapter file) of `IEpubBook.openResource` on disk
 * (plugin roadmap P5.4): the plugin streams the resource through a reliable pipe, the host copies
 * the read end into `<target>.part`, asks the descriptor whether the plugin closed its end with
 * an error, and renames the part file onto the target only when everything arrived. The pure copy
 * logic is [spool].
 */
object EpubOutputSink {

    private const val BUFFER_SIZE = 64 * 1024

    /**
     * The file a script's `path` names: an existing directory takes [fileName] inside it, any
     * other path is the file itself; an existing target is refused unless [overwrite].
     */
    fun resolveTarget(targetPath: String, fileName: String?, overwrite: Boolean): File {
        if (targetPath.isBlank()) throw failure(EpubErrorCodes.INVALID_ARGUMENT, "target path must not be blank")
        val requested = File(targetPath)
        val target = if (requested.isDirectory) {
            val leaf = fileName?.replace('\\', '/')?.substringAfterLast('/')?.trim().orEmpty()
            if (leaf.isEmpty() || leaf == "." || leaf == "..") throw failure(EpubErrorCodes.INVALID_ARGUMENT, "a file name is required for the directory ${requested.path}")
            File(requested, leaf)
        } else {
            requested
        }
        if (target.exists()) {
            if (target.isDirectory) throw failure(EpubErrorCodes.INVALID_ARGUMENT, "target is a directory: ${target.path}")
            if (!overwrite) throw failure(EpubErrorCodes.INVALID_ARGUMENT, "target exists: ${target.path}")
        }
        val directory = target.absoluteFile.parentFile
        if (directory != null && !directory.isDirectory && !directory.mkdirs()) {
            throw failure(EpubErrorCodes.IO, "cannot create ${directory.path}")
        }
        return target
    }

    /** Drains the pipe read end into [target]; the plugin's `closeWithError` surfaces as `IO`. */
    suspend fun save(descriptor: ParcelFileDescriptor, target: File, maxBytes: Long = EpubContract.MAX_RESOURCE_BYTES): File =
        withContext(Dispatchers.IO) {
            try {
                spool(FileInputStream(descriptor.fileDescriptor), target, maxBytes) {
                    try {
                        descriptor.checkError()
                    } catch (e: IOException) {
                        throw failure(EpubErrorCodes.IO, "the plugin ended the resource stream with an error: ${e.message}")
                    }
                }
            } finally {
                runCatching { descriptor.close() }
            }
        }

    /**
     * Copies [input] into `<target>.part` and renames it onto [target]. Fails with
     * `LIMIT_EXCEEDED` above [maxBytes]; [verify] runs after end-of-stream and may throw to
     * refuse the bytes. The part file is deleted on every failure and the target is never left
     * half-written.
     */
    fun spool(input: InputStream, target: File, maxBytes: Long = EpubContract.MAX_RESOURCE_BYTES, verify: () -> Unit = {}): File {
        val part = partFile(target)
        var written = 0L
        try {
            input.use { source ->
                FileOutputStream(part).use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > maxBytes) throw failure(EpubErrorCodes.LIMIT_EXCEEDED, "resource exceeds $maxBytes bytes")
                        out.write(buffer, 0, read)
                    }
                    out.flush()
                }
                verify()
            }
            move(part, target)
            return target
        } catch (t: Throwable) {
            part.delete()
            throw if (t is EpubPluginException) t else EpubPluginException(EpubErrorMapper.fromThrowable(t, EpubErrorMapper.Phase.TRANSFER), t)
        }
    }

    fun partFile(target: File): File = File(target.absoluteFile.parentFile, target.name + ".part")

    private fun move(part: File, target: File) {
        if (target.exists() && !target.delete()) throw failure(EpubErrorCodes.IO, "cannot replace ${target.path}")
        if (!part.renameTo(target)) throw failure(EpubErrorCodes.IO, "cannot move ${part.name} to ${target.path}")
    }

    private fun failure(code: String, message: String): EpubPluginException =
        EpubPluginException(EpubErrorMapper.normalize(code, message))
}
