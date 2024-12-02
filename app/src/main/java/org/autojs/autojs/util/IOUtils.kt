package org.autojs.autojs.util

import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object IOUtils {

    private val TAG = IOUtils::class.java.simpleName

    @JvmStatic
    @JvmOverloads
    fun close(closeable: Closeable?, exceptionMatters: Boolean = true) {
        try {
            closeable?.close()
        } catch (e: IOException) {
            when {
                exceptionMatters -> throw e
                else -> Log.w(TAG, "ex: $e")
            }
        }
    }

    @JvmStatic
    fun closeQuietly(closeable: Closeable?) = close(closeable, false)

    @JvmStatic
    fun gzip(string: String): ByteArray? = try {
        ByteArrayOutputStream().use { outputStream ->
            GZIPOutputStream(outputStream).use { gzipOutputStream ->
                gzipOutputStream.write(string.toByteArray(StandardCharsets.UTF_8))
            }
            outputStream.toByteArray()
        }
    } catch (e: Exception) {
        null.also { e.printStackTrace() }
    }

    @JvmStatic
    fun gunzip(bytes: ByteArray): String? = try {
        ByteArrayOutputStream().use { outputStream ->
            GZIPInputStream(ByteArrayInputStream(bytes)).use { gzipInputStream ->
                val buffer = ByteArray(1024)
                var bytesRead: Int
                while (gzipInputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.toString("UTF-8")
            }
        }
    } catch (e: Exception) {
        null.also { e.printStackTrace() }
    }

}