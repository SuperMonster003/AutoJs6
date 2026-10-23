package org.autojs.autojs.theme.app

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.autojs.autojs.storage.database.RoomDowngrade
import org.autojs.autojs.theme.library.ColorLibraryDao

@Database(
    entities = [
        ColorEntities.ColorHistory::class,
        ColorEntities.PaletteHistory::class,
        ColorEntities.ColorLibrary::class,
        ColorEntities.LibraryColor::class,
        ColorEntities.ColorGroup::class,
        ColorEntities.ColorGroupState::class,
        ColorEntities.FavoriteColor::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ColorHistoryDatabase : RoomDatabase() {

    abstract fun colorHistoryDao(): ColorHistoryDao

    abstract fun paletteHistoryDao(): PaletteHistoryDao

    abstract fun colorLibraryDao(): ColorLibraryDao

    companion object {

        @Volatile
        private var instance: ColorHistoryDatabase? = null

        // Migration from v1 to v2 (user libraries, groups, favorites; history usage and source columns).
        // zh-CN: 从 v1 到 v2 的迁移 (用户颜色库, 组, 收藏; 历史表新增使用次数与来源列).
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("color_history", "palette_history")) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `use_count` INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'USER'")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `source_detail` TEXT")
                }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `color_library` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, " +
                            "`is_intelligent` INTEGER NOT NULL, `name` TEXT NOT NULL, `origin` TEXT, `config_json` TEXT, `position` INTEGER NOT NULL, " +
                            "`created_time` INTEGER NOT NULL, `modified_time` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_color_library_position` ON `color_library` (`position`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `color_library_color` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `library_id` INTEGER NOT NULL, " +
                            "`name` TEXT, `color` INTEGER NOT NULL, `position` INTEGER NOT NULL, `created_time` INTEGER NOT NULL, `modified_time` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`library_id`) REFERENCES `color_library`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_color_library_color_library_id` ON `color_library_color` (`library_id`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `color_group` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `library_id` INTEGER NOT NULL, " +
                            "`name` TEXT, `enabled` INTEGER NOT NULL, `position` INTEGER NOT NULL, `created_time` INTEGER NOT NULL, `modified_time` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`library_id`) REFERENCES `color_library`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_color_group_library_id` ON `color_group` (`library_id`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `color_group_state` (`group_id` INTEGER NOT NULL, `state_key` TEXT NOT NULL, `primary_color` INTEGER, " +
                            "`accent_color` INTEGER, `dark_color` INTEGER, PRIMARY KEY(`group_id`, `state_key`), " +
                            "FOREIGN KEY(`group_id`) REFERENCES `color_group`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `color_favorite` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `color` INTEGER NOT NULL, " +
                            "`pinned` INTEGER NOT NULL, `position` INTEGER NOT NULL, `created_time` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_color_favorite_color` ON `color_favorite` (`color`)")
            }
        }

        const val DATABASE_NAME = "color-history-database.db"

        fun getInstance(applicationContext: Context): ColorHistoryDatabase = instance ?: synchronized(this) {
            instance ?: createBuilder(applicationContext, DATABASE_NAME).build().also { instance = it }
        }

        /**
         * Builder with every migration registered. Shared by [getInstance] and the migration test,
         * which opens a throwaway database file under a different name.
         * zh-CN: 注册了全部迁移的 builder. 供 [getInstance] 与迁移测试 (使用不同名称的临时数据库文件) 共用.
         */
        internal fun createBuilder(applicationContext: Context, name: String): RoomDatabase.Builder<ColorHistoryDatabase> {
            return RoomDowngrade.databaseBuilder(applicationContext, ColorHistoryDatabase::class.java, name).apply {
                addMigrations(MIGRATION_1_2)
            }
        }

    }

}
