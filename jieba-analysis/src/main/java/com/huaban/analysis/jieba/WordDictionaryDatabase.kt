package com.huaban.analysis.jieba

import android.content.Context

open class WordDictionaryDatabase private constructor(context: Context): DictionaryDatabase(context) {

    override val databaseName = "dict.db"

    companion object {

        @Volatile
        private var instance: WordDictionaryDatabase? = null

        fun getInstance(context: Context): WordDictionaryDatabase = instance ?: synchronized(this) {
            instance ?: WordDictionaryDatabase(context.applicationContext).also { instance = it }
        }

    }

}