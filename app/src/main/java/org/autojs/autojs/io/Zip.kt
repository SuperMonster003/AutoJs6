package org.autojs.autojs.io

import org.autojs.autojs.pio.PFiles.ensureDir
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

object Zip {
    @JvmStatic
    @Throws(IOException::class)
    fun unzip(stream: InputStream?, dir: File?) {
        ZipInputStream(stream).use { zis ->
            // @Reference to kkevsekk1/AutoX (https://github.com/kkevsekk1/AutoX) by SuperMonster003 on May 13, 2024.
            //  ! https://github.com/kkevsekk1/AutoX/commit/7332ddeae3089c8184333e7c31ab043a1dd26ed6#diff-b24bdb81d3c156df736b3166080648f1c17c4cde73c5e272e3b1196ac724d9f7R13
            generateSequence { zis.nextEntry }.forEach { entry ->
                val file = File(dir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    ensureDir(file.path)
                    file.outputStream().use { os -> zis.copyTo(os) }
                }
            }
        }
    }

    @Throws(IOException::class)
    fun unzip(zipFile: File, dir: File) {
        FileInputStream(zipFile).use { fis -> unzip(fis, dir) }
    }

    // @Reference to kkevsekk1/AutoX (https://github.com/kkevsekk1/AutoX) by SuperMonster003 on Nov 16, 2023.
    //  ! https://github.com/kkevsekk1/AutoX/blob/f197c5928f37345bffe7dbe9e5d41ab8f4f9424d/app/src/main/java/org/autojs/autojs/tool/ZipTool.kt#L7
    fun unzip(fromFile: File, newDir: File, unzipPath: String) {
        val niceUnzipPath = when {
            unzipPath.endsWith(File.separator) -> unzipPath
            else -> unzipPath + File.separator
        }
        newDir.mkdirs()
        fromFile.inputStream().use { fis ->
            ZipInputStream(fis).use { zis ->
                generateSequence { zis.nextEntry }
                    .filter { !it.isDirectory && it.name.startsWith(niceUnzipPath) }
                    .forEach { entry ->
                        val f = File(newDir, entry.name.replace(Regex("^$niceUnzipPath"), ""))
                        f.parentFile?.mkdirs()
                        f.outputStream().use { os -> zis.copyTo(os) }
                    }
            }
        }
    }
}
