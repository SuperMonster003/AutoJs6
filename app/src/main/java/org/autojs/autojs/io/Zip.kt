package org.autojs.autojs.io

import org.autojs.autojs.pio.PFiles.closeSilently
import org.autojs.autojs.pio.PFiles.ensureDir
import org.autojs.autojs.pio.PFiles.write
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object Zip {
    @JvmStatic
    @Throws(IOException::class)
    fun unzip(stream: InputStream?, dir: File?) {
        var fos: FileOutputStream? = null
        var zis: ZipInputStream? = null
        try {
            zis = ZipInputStream(stream)
            var entry: ZipEntry
            while (zis.nextEntry.also { entry = it } != null) {
                val file = File(dir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    ensureDir(file.path)
                    fos = FileOutputStream(file)
                    write(zis, fos, false)
                    fos.close()
                    fos = null
                    zis.closeEntry()
                }
            }
        } finally {
            closeSilently(fos)
            closeSilently(stream)
            closeSilently(zis)
        }
    }

    @Throws(IOException::class)
    fun unzip(zipFile: File?, dir: File?) {
        unzip(FileInputStream(zipFile), dir)
    }

    // @Reference to kkevsekk1/AutoX (https://github.com/kkevsekk1/AutoX) on Nov 16, 2023.
    //  ! https://github.com/kkevsekk1/AutoX/blob/f197c5928f37345bffe7dbe9e5d41ab8f4f9424d/app/src/main/java/org/autojs/autojs/tool/ZipTool.kt#L7
    fun unzip(fromFile: File, newDir: File, unzipPath: String) {
        var niceUnzipPath = unzipPath
        if (!niceUnzipPath.endsWith(File.separator)) niceUnzipPath += File.separator
        if (!newDir.exists()) newDir.mkdirs()
        var z: ZipEntry?
        ZipInputStream(fromFile.inputStream()).use { input ->
            while (input.nextEntry.also { z = it } != null) {
                val zipEntry = z ?: continue
                if (!zipEntry.isDirectory && zipEntry.name.startsWith(niceUnzipPath)) {
                    val f = File(newDir, zipEntry.name.replace(Regex("^$niceUnzipPath"), ""))
                    f.parentFile?.let {
                        if (!it.exists()) it.mkdirs()
                    }
                    f.outputStream().use { out ->
                        input.copyTo(out)
                    }
                }
            }
        }
    }
}
