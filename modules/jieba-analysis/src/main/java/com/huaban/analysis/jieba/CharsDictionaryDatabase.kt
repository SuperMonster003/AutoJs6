package com.huaban.analysis.jieba

import android.content.Context

open class CharsDictionaryDatabase private constructor(context: Context) : DictionaryDatabase(context) {

    override val databaseName = "dict-chinese-chars.db"

    companion object {

        @Volatile
        private var instance: CharsDictionaryDatabase? = null

        fun getInstance(applicationContext: Context): CharsDictionaryDatabase = instance ?: synchronized(this) {
            instance ?: CharsDictionaryDatabase(applicationContext).also { instance = it }
        }
    }

}