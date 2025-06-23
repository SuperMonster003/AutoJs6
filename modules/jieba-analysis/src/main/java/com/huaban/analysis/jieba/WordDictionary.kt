package com.huaban.analysis.jieba

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.properties.Delegates

class WordDictionary(context: Context) {

    private val db: SQLiteDatabase by lazy { WordDictionaryDatabase.getInstance(context.applicationContext).database }
    private var minFreq by Delegates.notNull<Double>()

    internal var trie = DictSegment(0.toChar())
        private set

    init {
        db.rawQuery("SELECT value FROM metadata WHERE `key` = 'min_freq' LIMIT 1", null).use {
            minFreq = if (it.moveToFirst()) it.getDouble(0) else Double.MAX_VALUE
        }
        // db.rawQuery("SELECT word FROM dictionary", null).use { cursor ->
        //     trie.fillSegments(generateSequence {
        //         if (cursor.moveToNext()) cursor.getString(cursor.getColumnIndexOrThrow("word")) else null
        //     })
        // }
        loadTrieConcurrently()
    }

    private fun loadTrieConcurrently() = runBlocking {
        val chunkSize = 10000
        val deferredResults = mutableListOf<Deferred<Unit>>()

        db.rawQuery("SELECT word FROM dictionary", null).use { cursor ->
            val words = mutableListOf<String>()
            while (cursor.moveToNext()) {
                val word = cursor.getString(cursor.getColumnIndexOrThrow("word"))
                words.add(word)
                if (words.size >= chunkSize) {
                    val chunk = words.toList()
                    words.clear()
                    deferredResults.add(async(Dispatchers.Default) {
                        trie.fillSegments(chunk)
                    })
                }
            }
            // 最后一块数据
            if (words.isNotEmpty()) {
                val chunk = words.toList()
                deferredResults.add(async(Dispatchers.Default) {
                    trie.fillSegments(chunk)
                })
            }
        }

        // 等待所有分块完成
        deferredResults.awaitAll()
    }

    @Suppress("unused")
    fun resetDict() {
        trie = DictSegment(0.toChar())
    }

    fun containsWord(word: String?): Boolean {
        if (word.isNullOrEmpty()) return false
        db.rawQuery("SELECT 1 FROM dictionary WHERE word = ? LIMIT 1", arrayOf(word)).use {
            return it.count > 0
        }
    }

    fun getFreq(key: String?): Double {
        if (!key.isNullOrEmpty()) {
            db.rawQuery("SELECT normalized_freq FROM dictionary WHERE word = ? LIMIT 1", arrayOf(key)).use {
                if (it.moveToFirst()) {
                    return it.getDouble(it.getColumnIndexOrThrow("normalized_freq"))
                }
            }
        }
        return minFreq
    }

    companion object {
        @Volatile
        private var INSTANCE: WordDictionary? = null

        fun getInstance(context: Context) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: WordDictionary(context.applicationContext).also { INSTANCE = it }
        }
    }

}
