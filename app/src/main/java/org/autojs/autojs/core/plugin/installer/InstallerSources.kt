package org.autojs.autojs.core.plugin.installer

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import org.autojs.plugin.installer.api.InstallerContract
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * One package source the host hands to the plugin: a read-only descriptor plus the display name
 * and size the request document repeats. The host owns the descriptor and closes it after the
 * terminal callback; the plugin duplicates what it needs (plugin roadmap D16).
 */
class InstallerSource private constructor(
    val item: Int,
    val displayName: String,
    val size: Long,
    val descriptor: ParcelFileDescriptor,
    /** The file the host may delete on `deleteSource` (plugin roadmap D25); null for content URIs. */
    val ownedFile: File?,
    private val openedIdentity: Identity? = null,
) : Closeable {

    fun toEntry(): InstallerJson.SourceEntry = InstallerJson.SourceEntry(item, displayName, size)

    override fun close() {
        runCatching { descriptor.close() }
    }

    /** Refuses known replacements or edits. The caller must keep the source stable until completion. */
    fun deleteOwnedFileIfUnchanged(): Boolean = runCatching {
        val file = ownedFile ?: return false
        val original = openedIdentity ?: return false
        original == Identity.read(Os.fstat(descriptor.fileDescriptor)) &&
            original == Identity.read(Os.lstat(file.path)) && file.delete()
    }.getOrDefault(false)

    private data class Identity(val device: Long, val inode: Long, val size: Long, val modified: Long,
        val changed: Long, val modifiedNanos: Long, val changedNanos: Long) {
        companion object {
            fun read(stat: StructStat): Identity? = if (!OsConstants.S_ISREG(stat.st_mode)) null else Identity(
                stat.st_dev, stat.st_ino, stat.st_size, stat.st_mtime, stat.st_ctime,
                if (Build.VERSION.SDK_INT >= 27) stat.st_mtim.tv_nsec else 0,
                if (Build.VERSION.SDK_INT >= 27) stat.st_ctim.tv_nsec else 0,
            )
        }
    }

    companion object {

        /** Opens a script- or host-supplied file read-only after existence, type, readability and size checks. */
        @Throws(IOException::class)
        fun fromFile(file: File, item: Int = 0): InstallerSource {
            if (!file.exists()) throw FileNotFoundException("package source not found: ${file.path}")
            if (!file.isFile) throw IOException("package source is not a regular file: ${file.path}")
            if (!file.canRead()) throw IOException("package source cannot be read: ${file.path}")
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val identity = Identity.read(Os.fstat(descriptor.fileDescriptor))
                    ?: throw IOException("package source is not a regular file: ${file.path}")
                if (identity.size <= 0L) throw IOException("package source is empty: ${file.path}")
                return InstallerSource(item, file.name.take(InstallerContract.MAX_DISPLAY_NAME_LENGTH), identity.size, descriptor, file, identity)
            } catch (failure: Throwable) {
                runCatching { descriptor.close() }
                throw if (failure is android.system.ErrnoException) IOException("package source metadata is unreadable", failure) else failure
            }
        }

        /** Opens a `content://` or `file://` source read-only; the display name and size come from the provider when it reports them. */
        @Throws(IOException::class)
        fun fromUri(context: Context, uri: Uri, item: Int = 0, fallbackName: String? = null,
            cancellationSignal: CancellationSignal? = null): InstallerSource {
            cancellationSignal?.throwIfCanceled()
            if (uri.scheme.equals("file", ignoreCase = true)) {
                val path = uri.path ?: throw FileNotFoundException("package source has no path: $uri")
                return fromFile(File(path), item)
            }
            require(uri.scheme.equals("content", ignoreCase = true)) { "Only file and content package URIs are supported" }
            val resolver = context.contentResolver
            var name = fallbackName
            var size = -1L
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null, cancellationSignal)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                    }
                }
            }
            cancellationSignal?.throwIfCanceled()
            val descriptor = resolver.openFileDescriptor(uri, "r", cancellationSignal) ?: throw FileNotFoundException("package source cannot be opened: $uri")
            try {
                cancellationSignal?.throwIfCanceled()
                if (size < 0L) size = runCatching { descriptor.statSize }.getOrDefault(-1L)
                if (size == 0L) throw IOException("package source is empty: $uri")
                val displayName = (name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "package").take(InstallerContract.MAX_DISPLAY_NAME_LENGTH)
                return InstallerSource(item, displayName, size.coerceAtLeast(-1L), descriptor, null)
            } catch (failure: Throwable) {
                runCatching { descriptor.close() }
                throw failure
            }
        }
    }
}
