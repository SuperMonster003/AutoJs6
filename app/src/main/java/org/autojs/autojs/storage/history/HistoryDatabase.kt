package org.autojs.autojs.storage.history

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.autojs.autojs.storage.database.RoomDowngrade

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 3, 2026.
 */
@Database(
    entities = [
        HistoryEntities.FileEntry::class,
        HistoryEntities.Revision::class,
        TrashEntities.TrashItem::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class HistoryDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    companion object {

        @Volatile
        private var instance: HistoryDatabase? = null

        // Migration from v1 to v2 (add trash_item).
        // zh-CN: 从 v1 到 v2 的迁移 (新增 trash_item 表).
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trash_item (
                        trashId TEXT NOT NULL PRIMARY KEY,
                        originalPath TEXT NOT NULL,
                        fileId TEXT,
                        trashedAt INTEGER NOT NULL,
                        isDirectory INTEGER NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        sha256 TEXT,
                        blobRelPath TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trash_item_originalPath ON trash_item(originalPath)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trash_item_trashedAt ON trash_item(trashedAt)")
            }
        }

        // Migration from v2 to v3 (revision.label, revision.pinned).
        // zh-CN: 从 v2 到 v3 的迁移 (revision 表新增 label 与 pinned 列).
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE revision ADD COLUMN label TEXT")
                db.execSQL("ALTER TABLE revision ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        const val DATABASE_NAME = "history-database.db"

        fun getInstance(applicationContext: Context): HistoryDatabase = instance ?: synchronized(this) {
            instance ?: createBuilder(applicationContext, DATABASE_NAME).build().also { instance = it }
        }

        /**
         * Builder with every migration registered. Shared by [getInstance] and the migration test,
         * which opens a throwaway database file under a different name.
         * zh-CN: 注册了全部迁移的 builder. 供 [getInstance] 与迁移测试 (使用不同名称的临时数据库文件) 共用.
         */
        internal fun createBuilder(applicationContext: Context, name: String): RoomDatabase.Builder<HistoryDatabase> {
            return RoomDowngrade.databaseBuilder(applicationContext, HistoryDatabase::class.java, name).apply {
                addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            }
        }
    }
}
