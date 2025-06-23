package org.autojs.autojs.util

import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object MD5Utils {

    @JvmStatic
    fun md5(message: String) = try {
        MessageDigest.getInstance("MD5").let {
            it.update(message.toByteArray())
            it.digest()
        }
    } catch (e: Exception) {
        throw RuntimeException(e)
    }.let { bytes ->
        StringBuilder(32).let { sb ->
            bytes.forEach { b ->
                Integer.toHexString(0xFF and b.toInt()).let { hex ->
                    if (hex.length == 1) sb.append('0')
                    sb.append(hex)
                }
            }
            sb.toString()
        }
    }

    @JvmStatic
    fun md5(file: File): String {
        val buffer = ByteArray(1024)
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            var numRead: Int
            while (fis.read(buffer).also { numRead = it } > 0) {
                digest.update(buffer, 0, numRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @JvmStatic
    fun toHash(text: String): String = try {
        MessageDigest.getInstance("MD5")
    } catch (e: NoSuchAlgorithmException) {
        throw RuntimeException(e)
    }.digest(text.toByteArray()).let { Base64.encodeToString(it, Base64.DEFAULT) }

}