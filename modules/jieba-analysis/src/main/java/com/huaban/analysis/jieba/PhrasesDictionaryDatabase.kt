package com.huaban.analysis.jieba

import android.content.Context

open class PhrasesDictionaryDatabase private constructor(context: Context): DictionaryDatabase(context) {

    override val databaseName = "dict-chinese-phrases.db"

    companion object {

        @Volatile
        private var instance: PhrasesDictionaryDatabase? = null

        fun getInstance(applicationContext: Context): PhrasesDictionaryDatabase = instance ?: synchronized(this) {
            instance ?: PhrasesDictionaryDatabase(applicationContext).also { instance = it }
        }

    }

}