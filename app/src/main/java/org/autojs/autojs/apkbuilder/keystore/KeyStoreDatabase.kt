package org.autojs.autojs.apkbuilder.keystore

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import org.autojs.autojs.storage.database.RoomDowngrade

// The schema is exported (app/schemas) so that RoomDowngrade can rebuild a file written by a newer version.
// zh-CN: 导出 schema (app/schemas), 供 RoomDowngrade 重建较新版本写过的库文件.
@Database(entities = [KeyStore::class], version = 1, exportSchema = true)
abstract class KeyStoreDatabase : RoomDatabase() {
    abstract fun keyStoreDao(): KeyStoreDao

    companion object {
        @Volatile
        private var INSTANCE: KeyStoreDatabase? = null

        const val DATABASE_NAME = "keystore-database"

        fun getDatabase(context: Context): KeyStoreDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createBuilder(context.applicationContext, DATABASE_NAME).build().also { INSTANCE = it }
            }
        }

        /**
         * Builder shared by [getDatabase] and the downgrade test, which opens a throwaway database file under a different name.
         * zh-CN: 供 [getDatabase] 与降级测试 (使用不同名称的临时数据库文件) 共用的 builder.
         */
        internal fun createBuilder(applicationContext: Context, name: String): RoomDatabase.Builder<KeyStoreDatabase> {
            return RoomDowngrade.databaseBuilder(applicationContext, KeyStoreDatabase::class.java, name)
        }
    }
}
