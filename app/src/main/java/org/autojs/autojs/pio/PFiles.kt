package org.autojs.autojs.pio

import android.content.Context
import android.content.res.AssetManager
import android.text.TextUtils
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.tool.Func1
import org.autojs.autojs.util.EnvironmentUtils
import org.autojs.autojs.util.FileUtils
import org.autojs.autojs6.R
import java.io.Closeable
import java.io.File
import java.io.File.separator
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import kotlin.math.ln
import kotlin.math.pow

/**
 * Created by Stardust on Apr 1, 2017.
 */
object PFiles {

    enum class OpenMode {
        READ, WRITE, APPEND;

        val abbr get() = name.lowercase().slice(0 until 1)
    }

    private val DEFAULT_OPEN_MODE: String = OpenMode.READ.abbr

    const val DEFAULT_BUFFER_SIZE = 8192

    @JvmField
    val DEFAULT_ENCODING: String = Charset.defaultCharset().name()

    private val sGlobalAppContext = GlobalAppContext.get()

    @JvmOverloads
    @JvmStatic
    fun open(path: String?, mode: String? = DEFAULT_OPEN_MODE, encoding: String? = DEFAULT_ENCODING, bufferSize: Int? = DEFAULT_BUFFER_SIZE): PFileInterface {
        path ?: throw IllegalArgumentException(sGlobalAppContext.getString(R.string.error_illegal_argument, "path", null))
        val niceMode = mode ?: DEFAULT_OPEN_MODE
        val niceEncoding = encoding ?: DEFAULT_ENCODING
        val niceBufferSize = bufferSize ?: DEFAULT_BUFFER_SIZE
        return when (niceMode.lowercase().slice(0 until 1)) {
            OpenMode.READ.abbr -> PReadableTextFile(path, niceEncoding, niceBufferSize)
            OpenMode.WRITE.abbr -> PWritableTextFile(path, niceEncoding, niceBufferSize, false)
            OpenMode.APPEND.abbr -> PWritableTextFile(path, niceEncoding, niceBufferSize, true)
            else -> throw IllegalArgumentException(sGlobalAppContext.getString(R.string.error_illegal_argument, "mode", mode))
        }
    }

    @JvmStatic
    fun create(path: String): Boolean {
        val f = File(path)
        return if (path.endsWith(separator)) {
            f.mkdir()
        } else {
            try {
                f.createNewFile()
            } catch (e: IOException) {
                false
            }
        }
    }

    @JvmStatic
    fun createIfNotExists(path: String): Boolean {
        ensureDir(path)
        val file = File(path)
        if (!file.exists()) {
            try {
                return file.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return false
    }

    @JvmStatic
    fun createWithDirs(path: String) = createIfNotExists(path)

    @JvmStatic
    fun exists(path: String?) = path?.let { File(it).exists() } ?: false

    @JvmStatic
    fun ensureDir(path: String): Boolean {
        val i = path.lastIndexOf(separator)
        return if (i >= 0) {
            val folder = path.substring(0, i)
            val file = File(folder)
            file.exists() || file.mkdirs()
        } else false
    }

    @JvmStatic
    fun read(path: String?, encoding: String?) = path?.let { read(File(it), encoding) } ?: ""

    @JvmStatic
    fun read(path: String?) = path?.let { read(File(it)) } ?: ""

    @JvmOverloads
    @JvmStatic
    fun read(file: File?, encoding: String? = DEFAULT_ENCODING): String {
        return try {
            read(FileInputStream(file), encoding)
        } catch (e: FileNotFoundException) {
            throw UncheckedIOException(e)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun read(inputStream: InputStream, encoding: String? = DEFAULT_ENCODING): String {
        return try {
            val bytes = ByteArray(inputStream.available())
            inputStream.read(bytes)
            String(bytes, Charset.forName(encoding))
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        } finally {
            closeSilently(inputStream)
        }
    }

    fun readBytes(stream: InputStream): ByteArray {
        return try {
            ByteArray(stream.available()).also { stream.read(it) }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    fun copyRaw(context: Context, rawId: Int, path: String): Boolean {
        val stream = context.resources.openRawResource(rawId)
        return copyStream(stream, path)
    }

    @JvmStatic
    fun copyStream(stream: InputStream, path: String): Boolean {
        if (!ensureDir(path)) return false
        val file = File(path)
        return try {
            if (!file.exists() && !file.createNewFile()) return false
            val fos = FileOutputStream(file)
            true.also { write(stream, fos) }
        } catch (e: IOException) {
            false.also { e.printStackTrace() }
        } catch (e: UncheckedIOException) {
            false.also { e.printStackTrace() }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun write(stream: InputStream, os: OutputStream, close: Boolean = true) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        try {
            while (stream.available() > 0) {
                val n = stream.read(buffer)
                if (n > 0) {
                    os.write(buffer, 0, n)
                }
            }
            if (close) {
                stream.close()
                os.close()
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    @JvmStatic
    fun write(path: String?, text: String) = write(path?.let { File(it) }, text)

    @JvmStatic
    fun write(path: String?, text: String, encoding: String?) {
        try {
            write(FileOutputStream(path), text, encoding)
        } catch (e: FileNotFoundException) {
            throw UncheckedIOException(e)
        }
    }

    @JvmStatic
    fun write(file: File?, text: String?) {
        try {
            text?.let { write(FileOutputStream(file), it) }
        } catch (e: FileNotFoundException) {
            throw UncheckedIOException(e)
        }
    }

    @JvmStatic
    fun write(fileOutputStream: OutputStream, text: String) = write(fileOutputStream, text, DEFAULT_ENCODING)

    fun write(outputStream: OutputStream, text: String, encoding: String?) {
        try {
            outputStream.write(text.toByteArray(encoding?.let { charset(it) } ?: Charset.defaultCharset()))
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        } finally {
            closeSilently(outputStream)
        }
    }

    @JvmStatic
    fun append(path: String, text: String) {
        create(path)
        try {
            write(FileOutputStream(path, true), text)
        } catch (e: FileNotFoundException) {
            throw UncheckedIOException(e)
        }
    }

    @JvmStatic
    fun append(path: String, text: String, encoding: String?) {
        create(path)
        try {
            write(FileOutputStream(path, true), text, encoding)
        } catch (e: FileNotFoundException) {
            throw UncheckedIOException(e)
        }
    }

    fun writeBytes(outputStream: OutputStream, bytes: ByteArray?) {
        try {
            outputStream.write(bytes)
            outputStream.close()
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    @JvmStatic
    fun appendBytes(path: String, bytes: ByteArray?) {
        create(path)
        try {
            writeBytes(FileOutputStream(path, true), bytes)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    @JvmStatic
    fun writeBytes(path: String?, bytes: ByteArray?) {
        try {
            writeBytes(FileOutputStream(path), bytes)
        } catch (e: FileNotFoundException) {
            throw UncheckedIOException(e)
        }
    }

    @JvmStatic
    fun copy(pathFrom: String?, pathTo: String): Boolean {
        return try {
            copyStream(FileInputStream(pathFrom), pathTo)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            false
        }
    }

    fun copyAsset(context: Context, assetFile: String?, path: String): Boolean {
        return try {
            copyStream(context.assets.open(assetFile!!), path)
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copyAssetDir(manager: AssetManager, assetsDir: String, toDir: String, list: Array<String?>?) {
        File(toDir).mkdirs()
        val ls = list ?: manager.list(assetsDir) ?: throw IOException("Not a directory: $assetsDir")
        ls.forEach { file ->
            if (!TextUtils.isEmpty(file)) {
                val fullAssetsPath = join(assetsDir, file)
                val children = manager.list(fullAssetsPath)
                if (children.isNullOrEmpty()) {
                    manager.open(fullAssetsPath).use { stream -> copyStream(stream, join(toDir, file)) }
                } else {
                    copyAssetDir(manager, fullAssetsPath, join(toDir, file), children)
                }
            }
        }
    }

    fun renameWithoutExtensionAndReturnNewPath(path: String, newName: String): String {
        val file = File(path)
        val newFile = File(file.parent, newName + "." + getExtension(file.name))
        file.renameTo(newFile)
        return newFile.absolutePath
    }

    @JvmStatic
    fun renameWithoutExtension(path: String, newName: String): Boolean {
        val file = File(path)
        val newFile = File(file.parent, newName + "." + getExtension(file.name))
        return file.renameTo(newFile)
    }

    @JvmStatic
    fun rename(path: String, newName: String): Boolean {
        val f = File(path)
        return f.renameTo(File(f.parent, newName))
    }

    @JvmStatic
    fun move(path: String, newPath: String): Boolean {
        val f = File(path)
        return f.renameTo(File(newPath))
    }

    @JvmStatic
    fun getExtension(fileName: String): String = FileUtils.getExtension(fileName)

    fun generateNotExistingPath(path: String, extension: String): String {
        if (!File(path + extension).exists()) {
            return path + extension
        }
        var i = 0
        while (true) {
            val pathI = "$path($i)$extension"
            if (!File(pathI).exists()) return pathI
            i++
        }
    }

    @JvmStatic
    fun getName(filePath: String): String = File(filePath.replace('\\', '/')).name

    @JvmStatic
    fun getNameWithoutExtension(filePath: String): String {
        val fileName = getName(filePath)
        var b = fileName.lastIndexOf('.')
        if (b < 0) b = fileName.length
        return fileName.substring(0, b)
    }

    fun copyAssetToTmpFile(context: Context, path: String): File {
        val extension = "." + getExtension(path)
        var name = getNameWithoutExtension(path)
        if (name.length < 5) {
            name += name.hashCode()
        }
        return try {
            val tmpFile = File.createTempFile(name, extension, context.cacheDir)
            copyAsset(context, path, tmpFile.path)
            tmpFile
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun deleteRecursively(file: File): Boolean {
        if (file.isFile) return file.delete()
        val children = file.listFiles()
        if (children != null) {
            for (child in children) {
                if (!deleteRecursively(child)) return false
            }
        }
        return file.delete()
    }

    @JvmStatic
    fun deleteFilesOfDir(dir: File): Boolean {
        require(dir.isDirectory) { "Not a directory: $dir" }
        val children = dir.listFiles()
        if (children != null) {
            for (child in children) {
                if (!deleteRecursively(child)) return false
            }
        }
        return true
    }

    @JvmStatic
    fun remove(path: String?): Boolean {
        return path?.let { File(it).delete() } ?: false
    }

    @JvmStatic
    fun removeDir(path: String?): Boolean {
        return path?.let { deleteRecursively(File(it)) } ?: false
    }

    @JvmStatic
    fun readAsset(assets: AssetManager, path: String?): String {
        return try {
            read(assets.open(path!!))
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    @JvmStatic
    fun listDir(path: String?): Array<String> = path?.let { File(it).list() } ?: emptyArray()

    private fun wrapNonNull(list: Array<String>?) = list ?: emptyArray()

    @JvmStatic
    fun listDir(path: String?, filter: Func1<String, Boolean?>): Array<String> {
        val file = path?.let { File(it) }
        return wrapNonNull(file?.list { _, name: String -> filter.call(name)!! })
    }

    @JvmStatic
    fun isFile(path: String?) = path?.let { File(it).isFile } ?: false

    @JvmStatic
    fun isDir(path: String?) = path?.let { File(it).isDirectory } ?: false

    @JvmStatic
    fun isEmptyDir(path: String?): Boolean {
        val file = path?.let { File(it) } ?: return false
        return file.isDirectory && file.list()!!.isEmpty()
    }

    @JvmStatic
    fun join(base: String?, vararg paths: String?): String {
        base ?: return ""
        var file = File(base)
        for (path in paths) {
            file = path?.let { File(file, it) } ?: file
        }
        return file.path
    }

    @JvmStatic
    fun getHumanReadableSize(bytes: Long): String {
        val unit = 1024
        if (bytes < unit) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()

        @Suppress("SpellCheckingInspection")
        val pre = "KMGTPE".substring(exp - 1, exp)
        return String.format(Language.getPrefLanguage().locale, "%.1f %sB", bytes / unit.toDouble().pow(exp.toDouble()), pre)
    }

    @JvmStatic
    fun getElegantPath(path: String) = getElegantPath(path, null, false)

    @JvmStatic
    fun getElegantPath(path: String, workingDirectory: String?, hasPrefix: Boolean): String {
        if (workingDirectory != null && path.startsWith(workingDirectory)) {
            return (if (hasPrefix) "\$cwd" else "") + path.substring(workingDirectory.length)
        }
        if (path.startsWith(EnvironmentUtils.externalStoragePath)) {
            return (if (hasPrefix) "\$sdcard" else "") + path.substring(EnvironmentUtils.externalStoragePath.length)
        }

        val filesPrefix = sGlobalAppContext.filesDir.path
        val samplePrefix = filesPrefix + separator + "sample"

        if (path.startsWith(samplePrefix)) {
            return (if (hasPrefix) "\$sample" else "") + path.substring(samplePrefix.length)
        }
        if (path.startsWith(filesPrefix)) {
            return (if (hasPrefix) "\$files" else "") + path.substring(filesPrefix.length)
        }

        val cachePrefix = sGlobalAppContext.cacheDir.path

        if (isCachePrefix(path)) {
            return (if (hasPrefix) "\$cache" else "") + path.substring(cachePrefix.length)
        }

        val dataPrefix = sGlobalAppContext.dataDir.path

        if (path.startsWith(dataPrefix)) {
            return (if (hasPrefix) "\$data" else "") + path.substring(dataPrefix.length)
        }

        return path
    }

    @JvmStatic
    fun isCachePrefix(path: String) = path.startsWith(sGlobalAppContext.cacheDir.path)

    @JvmStatic
    fun readBytes(path: String?): ByteArray {
        return try {
            readBytes(FileInputStream(path))
        } catch (e: FileNotFoundException) {
            throw UncheckedIOException(e)
        }
    }

    @JvmStatic
    fun closeSilently(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (ignored: IOException) {
            /* Ignored. */
        }
    }
}