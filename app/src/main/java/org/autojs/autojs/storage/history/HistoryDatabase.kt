package org.autojs.autojs.storage.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 3, 2026.
 */
@Database(
    entities = [
        HistoryEntities.FileEntry::class,
        HistoryEntities.Revision::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class HistoryDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    companion object {

        @Volatile
        private var instance: HistoryDatabase? = null

        fun getInstance(applicationContext: Context): HistoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                applicationContext,
                HistoryDatabase::class.java,
                "history-database.db",
            ).build().also { instance = it }
        }
    }
}