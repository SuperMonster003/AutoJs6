package org.autojs.autojs.util

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object IOUtils {

    private val TAG = IOUtils::class.java.simpleName

    fun close(io: Closeable?) {
        try {
            io?.close()
        } catch (e: IOException) {
            Log.w(TAG, "ex: $e")
        }
    }

    @Throws(IOException::class)
    fun close(io: Closeable?, exceptionMatters: Boolean) {
        try {
            io?.close()
        } catch (e: IOException) {
            if (exceptionMatters) {
                throw e
            }
        }
    }

    fun gzip(str: String): ByteArray? {
        var out: ByteArrayOutputStream? = null
        var gzip: GZIPOutputStream? = null
        try {
            return ByteArrayOutputStream()
                .also {
                    gzip = GZIPOutputStream(it).apply {
                        write(str.toByteArray(StandardCharsets.UTF_8))
                        finish()
                    }
                }
                .also { out = it }.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            close(out)
            close(gzip)
        }
        return null
    }

    fun gunzip(bytes: ByteArray?): String? {
        var out: ByteArrayOutputStream? = null
        var gzip: GZIPInputStream? = null
        try {
            return ByteArrayOutputStream()
                .also { outputStream ->
                    gzip = GZIPInputStream(ByteArrayInputStream(bytes)).apply {
                        var res: Int
                        val buf = ByteArray(1024)
                        while (read(buf).also { res = it } != -1) {
                            outputStream.write(buf, 0, res)
                        }
                        outputStream.flush()
                    }
                }
                .also { out = it }.toString("UTF-8")
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            close(out)
            close(gzip)
        }
        return null
    }

}