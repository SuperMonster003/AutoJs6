package org.autojs.plugin.apkbuilder.template.impl

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Compressed native entries inherit extractNativeLibs from the Runtime Kit manifest.
 *
 * `resources.arsc` is stored uncompressed with its data 4-byte aligned: since the packaged app
 * targets API 30 or newer, the package manager rejects an APK whose resource table it cannot
 * map directly ("Targeting R+ requires the resources.arsc of installed APKs to be stored
 * uncompressed and aligned on a 4-byte boundary"). The alignment uses the same extra-field
 * record as zipalign, which apksig preserves when it signs the file.
 */
internal object RemoteUnsignedApkWriter {
    private const val BUFFER_SIZE = 256 * 1024
    private const val LOCAL_HEADER_SIZE = 30
    private const val ALIGNMENT = 4
    /** zipalign / apksig "Android alignment" extra field: id, payload size, alignment multiple, zero padding. */
    private const val ALIGNMENT_EXTRA_ID = 0xD935
    private val storedEntries = setOf("resources.arsc")

    fun write(buildDir: File, unsignedApk: File, ensureActive: () -> Unit) {
        FileOutputStream(unsignedApk, false).use { output ->
            val counting = CountingOutputStream(BufferedOutputStream(output, BUFFER_SIZE))
            ZipOutputStream(counting).use { zip ->
                addDirectory(buildDir, buildDir, zip, counting, ensureActive)
                zip.finish()
                zip.flush()
            }
        }
    }

    private fun addDirectory(root: File, dir: File, zip: ZipOutputStream, counting: CountingOutputStream, ensureActive: () -> Unit) {
        dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name })?.forEach { child ->
            ensureActive()
            val path = child.relativeTo(root).path.replace(File.separatorChar, '/')
            if (shouldSkip(path, child.isDirectory)) return@forEach
            if (child.isDirectory) {
                zip.putNextEntry(ZipEntry("$path/"))
                zip.closeEntry()
                addDirectory(root, child, zip, counting, ensureActive)
            } else {
                zip.putNextEntry(if (path in storedEntries) storedAlignedEntry(path, child, counting.count) else ZipEntry(path))
                FileInputStream(child).use { input -> input.copyTo(zip, BUFFER_SIZE) }
                zip.closeEntry()
            }
        }
    }

    /** A STORED entry whose data starts on an [ALIGNMENT] boundary, given the local header offset. */
    private fun storedAlignedEntry(path: String, file: File, localHeaderOffset: Long): ZipEntry {
        val crc = CRC32()
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                crc.update(buffer, 0, read)
            }
        }
        val nameLength = path.toByteArray(Charsets.UTF_8).size
        val extraHeaderLength = 6
        val padding = ((ALIGNMENT - (localHeaderOffset + LOCAL_HEADER_SIZE + nameLength + extraHeaderLength) % ALIGNMENT) % ALIGNMENT).toInt()
        val extra = ByteBuffer.allocate(extraHeaderLength + padding).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(ALIGNMENT_EXTRA_ID.toShort())
            .putShort((2 + padding).toShort())
            .putShort(ALIGNMENT.toShort())
            .array()
        return ZipEntry(path).apply {
            method = ZipEntry.STORED
            size = file.length()
            compressedSize = file.length()
            setCrc(crc.value)
            setExtra(extra)
        }
    }

    private fun shouldSkip(path: String, isDirectory: Boolean): Boolean {
        if (!path.startsWith("META-INF/", ignoreCase = true)) return false
        if (path.startsWith("META-INF/services", ignoreCase = true)) return false
        if (isDirectory) return path.equals("META-INF", true) || path.equals("META-INF/", true)
        return true
    }

    /** Counts the bytes handed to the zip stream's sink, which is where every local header lands. */
    private class CountingOutputStream(private val sink: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            sink.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            sink.write(b, off, len)
            count += len
        }

        override fun flush() = sink.flush()

        override fun close() = sink.close()
    }
}
