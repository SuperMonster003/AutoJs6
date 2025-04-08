package org.autojs.autojs.theme.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ColorEntities.ColorHistory::class,
        ColorEntities.PaletteHistory::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ColorHistoryDatabase : RoomDatabase() {

    abstract fun colorHistoryDao(): ColorHistoryDao

    abstract fun paletteHistoryDao(): PaletteHistoryDao

    companion object {

        @Volatile
        private var instance: ColorHistoryDatabase? = null

        fun getInstance(applicationContext: Context): ColorHistoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                applicationContext,
                ColorHistoryDatabase::class.java,
                "color-history-database.db",
            ).build().also { instance = it }
        }

    }

}