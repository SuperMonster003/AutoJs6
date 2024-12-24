package com.huaban.analysis.jieba

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

abstract class DictionaryDatabase internal constructor(context: Context): Closeable {

    abstract val databaseName: String

    private var shouldForciblyCopyDatabase = false

    private val compressedDatabaseName: String
        get() = "$databaseName.gzip"

    private val md5Key: String
        get() = "database_md5_$databaseName"

    val database: SQLiteDatabase by lazy {
        copyDatabaseWithCompressionAndMd5(context)
        SQLiteDatabase.openDatabase(context.getDatabasePath(databaseName).absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    }

    override fun close() = database.close()

    private fun copyDatabaseWithCompressionAndMd5(context: Context) {
        val dbFile = File(context.getDatabasePath(databaseName).path)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val storedMd5 = prefs.getString(md5Key, null)
        val currentMd5 by lazy { dbFile.md5() }

        // 如果数据库文件存在，检查 MD5
        if (dbFile.exists()) {
            if (storedMd5 != null && storedMd5 == currentMd5) {
                if (!shouldForciblyCopyDatabase) return // 文件有效，无需复制或解压
            }
        }

        // 数据文件不存在或 MD5 不匹配，重新复制
        dbFile.parentFile?.mkdirs()
        context.assets.open(compressedDatabaseName).use { compressedInputStream ->
            GZIPInputStream(compressedInputStream).use { gzipStream -> // 解压
                FileOutputStream(dbFile).use { outputStream ->
                    gzipStream.copyTo(outputStream)
                }
            }
        }

        // 保存 MD5 值
        prefs.edit().putString(md5Key, currentMd5).apply()
    }

    companion object {
        
        private const val PREF_NAME = "dict_prefs"

    }

    /**
     * 计算文件的 MD5
     */
    private fun File.md5(): String = inputStream().use { it.md5() }

    private fun InputStream.md5(): String {
        val md = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(1024)
        var read: Int
        while (this.read(buffer).also { read = it } != -1) {
            md.update(buffer, 0, read)
        }
        val digest = md.digest()
        return digest.joinToString("") { "%02x".format(it) }
    }

}