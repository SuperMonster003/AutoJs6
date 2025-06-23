package com.huaban.analysis.jieba

import android.content.Context

open class WordDictionaryDatabase private constructor(context: Context): DictionaryDatabase(context) {

    override val databaseName = "dict-chinese-words.db"

    companion object {

        @Volatile
        private var instance: WordDictionaryDatabase? = null

        fun getInstance(applicationContext: Context): WordDictionaryDatabase = instance ?: synchronized(this) {
            instance ?: WordDictionaryDatabase(applicationContext).also { instance = it }
        }

    }

}