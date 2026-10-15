package org.autojs.autojs.core.plugin.gallery

import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.plugin.gallery.api.GalleryContract
import org.autojs.plugin.gallery.api.GalleryErrorCodes
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

/**
 * Read-only descriptors of the images a script names by path: each path is validated before
 * anything is opened (existence, regular file, readability), the descriptors travel with the call
 * and the host closes its copies right after the call returns because the plugin duplicates what it
 * keeps. The plugin decides whether the content is an image it opens.
 */
class GallerySources private constructor(
    val files: List<File>,
    val descriptors: List<ParcelFileDescriptor>,
) : Closeable {

    val single: ParcelFileDescriptor get() = descriptors.single()

    override fun close() {
        descriptors.forEach { runCatching { it.close() } }
    }

    companion object {

        /** Checks one path without opening it; returns the file. */
        fun validate(path: String): File {
            if (path.isBlank()) throw GalleryErrorMapper.failure(GalleryErrorCodes.INVALID_ARGUMENT, "path must not be blank")
            val file = File(path)
            if (!file.exists()) throw GalleryErrorMapper.failure(GalleryErrorCodes.FILE_NOT_FOUND, "file not found: $path")
            if (!file.isFile) throw GalleryErrorMapper.failure(GalleryErrorCodes.FILE_NOT_FOUND, "not a regular file: $path")
            if (!file.canRead()) throw GalleryErrorMapper.failure(GalleryErrorCodes.FILE_UNREADABLE, "file is not readable: $path")
            return file
        }

        fun open(paths: List<String>): GallerySources {
            val files = paths.map(::validate)
            val opened = ArrayList<ParcelFileDescriptor>(files.size)
            try {
                files.forEach { file -> opened += ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
            } catch (t: Throwable) {
                opened.forEach { runCatching { it.close() } }
                throw GalleryPluginException(GalleryErrorMapper.fromThrowable(t, GalleryErrorMapper.Phase.OPEN), t)
            }
            return GallerySources(files, opened)
        }
    }
}

/** One image the picker answered and the host kept: the script-facing `{ path, name, ... }` object. */
data class GalleryPickedImage(
    val path: String,
    val name: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val size: Long,
    val dateTaken: Long,
)

/**
 * The host side of a picker result: parses the plugin's `items` document and keeps only images the
 * script may read (an absolute path to an existing, readable regular file); duplicates and anything
 * past `MAX_PICK_COUNT` are dropped. When the plugin answered images but none of them is readable for
 * the script, the result is `ACCESS_DENIED` instead of an empty list, so the script can tell a refused
 * storage permission from an empty choice.
 */
object GalleryPickPolicy {

    fun accept(itemsJson: String?, readable: (File) -> Boolean = ::isReadableFile): List<GalleryPickedImage> {
        val root: JsonElement = try {
            JsonParser.parseString(itemsJson ?: "[]")
        } catch (t: Throwable) {
            throw GalleryErrorMapper.failure(GalleryErrorCodes.INTERNAL, "the picker answered malformed items: ${t.message}", t)
        }
        if (!root.isJsonArray) throw GalleryErrorMapper.failure(GalleryErrorCodes.INTERNAL, "the picker items are not a JSON array")
        val offered = root.asJsonArray.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject?.let(::decode) }
        val kept = LinkedHashMap<String, GalleryPickedImage>()
        for (item in offered) {
            if (kept.size >= GalleryContract.MAX_PICK_COUNT) break
            val file = File(item.path)
            if (!file.isAbsolute || !readable(file)) continue
            kept.putIfAbsent(file.absolutePath, item.copy(path = file.absolutePath))
        }
        if (kept.isEmpty() && offered.isNotEmpty()) {
            throw GalleryErrorMapper.failure(
                GalleryErrorCodes.ACCESS_DENIED,
                "none of the ${offered.size} picked images is readable for scripts (is the storage permission of AutoJs6 granted?)",
            )
        }
        return kept.values.toList()
    }

    private fun decode(json: JsonObject): GalleryPickedImage? {
        val path = json.string(GalleryContract.FIELD_PATH)?.takeIf { it.isNotBlank() } ?: return null
        return GalleryPickedImage(
            path = path,
            name = json.string(GalleryContract.FIELD_NAME) ?: File(path).name,
            mimeType = json.string(GalleryContract.FIELD_MIME_TYPE).orEmpty(),
            width = json.number(GalleryContract.FIELD_WIDTH)?.toInt() ?: 0,
            height = json.number(GalleryContract.FIELD_HEIGHT)?.toInt() ?: 0,
            size = json.number(GalleryContract.FIELD_SIZE)?.toLong() ?: 0L,
            dateTaken = json.number(GalleryContract.FIELD_DATE_TAKEN)?.toLong() ?: 0L,
        )
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.number(key: String): Number? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asNumber

    fun isReadableFile(file: File): Boolean = file.isFile && file.canRead()
}

/** Where a script wants an `edit` or `convert` output: [path] when named (absolute), else next to the source; [overwrite] replaces. */
data class GalleryOutputRequest(val path: String? = null, val overwrite: Boolean = false)

/** A written and published output, as the script receives it. */
data class GalleryWrittenImage(val path: String, val mimeType: String, val width: Int, val height: Int, val size: Long)

/** What an output decodes to when read back. */
data class GalleryImageProbe(val mimeType: String, val width: Int, val height: Int)

/**
 * The host's part of an `edit` or `convert` output: it picks the final path, stages an empty hidden
 * file in the destination folder for the plugin to write through a descriptor, reads the staging file
 * back and only then renames it onto the output (replacing an existing file only on request, the old
 * file kept until the rename succeeded). Any failure deletes the staging file, so a folder never shows
 * a half-written image.
 */
class GalleryOutput private constructor(
    private val source: File,
    private val explicit: File?,
    private val overwrite: Boolean,
    private val suffix: String,
    val staging: File,
) {

    private var publishedOrDiscarded = false

    /** The read-write descriptor of the staging file for the plugin; the caller closes it after the call. */
    fun openStaging(): ParcelFileDescriptor = try {
        ParcelFileDescriptor.open(staging, ParcelFileDescriptor.MODE_READ_WRITE)
    } catch (t: Throwable) {
        discard()
        throw GalleryPluginException(GalleryErrorMapper.fromThrowable(t, GalleryErrorMapper.Phase.OUTPUT), t)
    }

    /** The path the output gets for an image of [mimeType]. */
    fun target(mimeType: String): File = when {
        explicit != null -> explicit
        overwrite -> source
        else -> defaultTarget(source, suffix, mimeType)
    }

    /**
     * Checks the staging file against what the plugin reported and renames it onto [target]; returns
     * the published image. [probe] reads an image file back (BitmapFactory bounds by default).
     */
    fun publish(reportedMimeType: String?, reportedWidth: Int?, reportedHeight: Int?, probe: (File) -> GalleryImageProbe? = ::probeImage): GalleryWrittenImage {
        try {
            val length = staging.length()
            if (length <= 0L) throw GalleryErrorMapper.failure(GalleryErrorCodes.IO, "the plugin wrote no output")
            val read = probe(staging) ?: throw GalleryErrorMapper.failure(GalleryErrorCodes.IO, "the output does not read back as an image")
            if (reportedMimeType != null && !reportedMimeType.equals(read.mimeType, ignoreCase = true)) {
                throw GalleryErrorMapper.failure(GalleryErrorCodes.IO, "the output reads back as ${read.mimeType}, the plugin reported $reportedMimeType")
            }
            if ((reportedWidth != null && reportedWidth != read.width) || (reportedHeight != null && reportedHeight != read.height)) {
                throw GalleryErrorMapper.failure(GalleryErrorCodes.IO, "the output reads back as ${read.width} x ${read.height}, the plugin reported $reportedWidth x $reportedHeight")
            }
            val target = target(read.mimeType)
            replace(target)
            publishedOrDiscarded = true
            return GalleryWrittenImage(target.absolutePath, read.mimeType, read.width, read.height, target.length())
        } catch (t: Throwable) {
            discard()
            throw if (t is GalleryPluginException) t else GalleryPluginException(GalleryErrorMapper.fromThrowable(t, GalleryErrorMapper.Phase.OUTPUT), t)
        }
    }

    /** Deletes the staging file (cancellation, failure, timeout); harmless after [publish]. */
    fun discard() {
        if (publishedOrDiscarded) return
        publishedOrDiscarded = true
        runCatching { staging.delete() }
    }

    private fun replace(target: File) {
        if (!target.exists()) {
            if (!staging.renameTo(target)) throw IOException("cannot move the output to ${target.path}")
            return
        }
        if (target.isDirectory) throw GalleryErrorMapper.failure(GalleryErrorCodes.INVALID_ARGUMENT, "output is a directory: ${target.path}")
        // A default name was free when it was chosen; a file that appeared since is not replaced either.
        if (!overwrite) throw GalleryErrorMapper.failure(GalleryErrorCodes.OUTPUT_EXISTS, "output exists: ${target.path}")
        val backup = File(target.parentFile, ".${target.name}.gallery-${UUID.randomUUID()}.bak")
        if (!target.renameTo(backup)) throw IOException("cannot set the existing ${target.path} aside")
        if (!staging.renameTo(target)) {
            backup.renameTo(target)
            throw IOException("cannot move the output to ${target.path}")
        }
        backup.delete()
    }

    companion object {

        const val SUFFIX_EDITED = "_edited"
        const val SUFFIX_CONVERTED = "_converted"

        /**
         * Validates the request and stages the output next to its final place. An explicit output that
         * exists fails with `OUTPUT_EXISTS` unless [GalleryOutputRequest.overwrite]; a folder the host
         * cannot write fails with `ACCESS_DENIED`.
         */
        fun stage(source: File, request: GalleryOutputRequest, suffix: String): GalleryOutput {
            val explicit = request.path?.takeIf { it.isNotBlank() }?.let { File(it).absoluteFile }
            if (explicit != null) {
                if (explicit.isDirectory) throw GalleryErrorMapper.failure(GalleryErrorCodes.INVALID_ARGUMENT, "output is a directory: ${explicit.path}")
                if (explicit.exists() && !request.overwrite) throw GalleryErrorMapper.failure(GalleryErrorCodes.OUTPUT_EXISTS, "output exists: ${explicit.path}")
            }
            val directory = (explicit ?: source.absoluteFile).parentFile
                ?: throw GalleryErrorMapper.failure(GalleryErrorCodes.INVALID_ARGUMENT, "the output has no folder")
            if (!directory.isDirectory && !directory.mkdirs()) throw GalleryErrorMapper.failure(GalleryErrorCodes.ACCESS_DENIED, "cannot create ${directory.path}")
            val staging = File(directory, ".gallery-${UUID.randomUUID()}.tmp")
            val created = runCatching { staging.createNewFile() }.getOrDefault(false)
            if (!created) throw GalleryErrorMapper.failure(GalleryErrorCodes.ACCESS_DENIED, "cannot write in ${directory.path}")
            return GalleryOutput(source.absoluteFile, explicit, request.overwrite, suffix, staging)
        }

        /** `name_edited.ext` next to [source], with a counter when the name is taken (`name_edited (1).ext`). */
        fun defaultTarget(source: File, suffix: String, mimeType: String): File {
            val directory = source.absoluteFile.parentFile
            val base = source.name.substringBeforeLast('.', source.name) + suffix
            val extension = extensionOf(mimeType) ?: source.name.substringAfterLast('.', "").lowercase(Locale.ROOT).ifEmpty { "img" }
            var candidate = File(directory, "$base.$extension")
            var counter = 1
            while (candidate.exists()) {
                candidate = File(directory, "$base ($counter).$extension")
                counter++
            }
            return candidate
        }

        fun extensionOf(mimeType: String): String? = when (mimeType.lowercase(Locale.ROOT)) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> null
        }

        fun probeImage(file: File): GalleryImageProbe? {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val mimeType = options.outMimeType ?: return null
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            return GalleryImageProbe(mimeType, options.outWidth, options.outHeight)
        }
    }
}
