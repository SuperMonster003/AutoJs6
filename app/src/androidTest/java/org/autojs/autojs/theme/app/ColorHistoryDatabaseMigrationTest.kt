package org.autojs.autojs.theme.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.theme.app.ColorEntities.ColorGroup
import org.autojs.autojs.theme.app.ColorEntities.ColorGroupState
import org.autojs.autojs.theme.app.ColorEntities.ColorLibrary
import org.autojs.autojs.theme.app.ColorEntities.FavoriteColor
import org.autojs.autojs.theme.app.ColorEntities.HistorySource
import org.autojs.autojs.theme.app.ColorEntities.LibraryColor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration regression for [ColorHistoryDatabase] without `room-testing`: a version 1 database is built from the
 * CREATE statements recorded in `app/schemas/org.autojs.autojs.theme.app.ColorHistoryDatabase/1.json`, populated with
 * raw SQL, then opened through the production builder so Room runs `MIGRATION_1_2` and validates the result against
 * the version 2 entities (a mismatch throws on open).
 *
 * zh-CN: 不依赖 `room-testing` 的 [ColorHistoryDatabase] 迁移回归: 用 schema 快照 1.json 中记录的 CREATE 语句建立 v1 数据库
 * 并用原生 SQL 写入数据, 再经生产 builder 打开, 由 Room 执行 `MIGRATION_1_2` 并按 v2 实体校验 (不一致会在打开时抛出).
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
@RunWith(AndroidJUnit4::class)
class ColorHistoryDatabaseMigrationTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val dbName = "color-history-migration-test.db"

    @Before
    fun deleteBefore() {
        context.deleteDatabase(dbName)
    }

    @After
    fun deleteAfter() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate1To2KeepsHistoryAndDefaultsNewColumns() {
        createLegacy { db ->
            db.execSQL(
                "INSERT INTO color_history (library_id, item_id, last_used_time) VALUES (?, ?, ?)",
                arrayOf<Any?>(2, 5, 1000L),
            )
            db.execSQL(
                "INSERT INTO palette_history (last_used_time, hex) VALUES (?, ?)",
                arrayOf<Any?>(2000L, "#FF00695C"),
            )
        }

        openMigrated { db ->
            val history = runBlocking { db.colorHistoryDao().getAll() }.single()
            assertEquals(2, history.libraryId)
            assertEquals(5, history.itemId)
            assertEquals(1000L, history.lastUsedTime)
            assertEquals(1, history.useCount)
            assertEquals(HistorySource.USER, history.source)
            assertNull(history.sourceDetail)

            val palette = runBlocking { db.paletteHistoryDao().getByHex("#FF00695C") }!!
            assertEquals(2000L, palette.lastUsedTime)
            assertEquals(1, palette.useCount)
            assertEquals(HistorySource.USER, palette.source)
        }
    }

    @Test
    fun migratedLibraryTablesAreWritableAndCascade() {
        createLegacy { }

        openMigrated { db ->
            val dao = db.colorLibraryDao()
            runBlocking {
                val now = 3000L
                val libraryId = dao.insertLibrary(
                    ColorLibrary(kind = ColorLibrary.Kind.CREATED, isIntelligent = true, name = "Warm and cool", configJson = "{}", position = dao.nextLibraryPosition(), createdTime = now, modifiedTime = now),
                )
                dao.insertColors(listOf(LibraryColor(libraryId = libraryId, color = 0xFF00695C.toInt(), position = 0, createdTime = now, modifiedTime = now)))
                val groupId = dao.insertGroup(ColorGroup(libraryId = libraryId, name = "Orange", position = 0, createdTime = now, modifiedTime = now))
                dao.upsertStates(
                    listOf(
                        ColorGroupState(groupId, "day", primaryColor = 0xFFFFB74D.toInt()),
                        ColorGroupState(groupId, "night", primaryColor = null),
                    ),
                )
                dao.upsertStates(listOf(ColorGroupState(groupId, "night", primaryColor = 0xFFE65100.toInt(), accentColor = 0xFFFFD54F.toInt())))
                dao.insertFavorite(FavoriteColor(color = 0xFF00695C.toInt(), position = 0, createdTime = now))
                // A favorite is unique per color; a second insert is ignored.
                // zh-CN: 收藏按颜色唯一, 重复插入被忽略.
                assertEquals(-1L, dao.insertFavorite(FavoriteColor(color = 0xFF00695C.toInt(), position = 1, createdTime = now)))

                assertEquals(1, dao.nextLibraryPosition())
                assertEquals("Warm and cool", dao.findLibrary(libraryId)?.name)
                assertEquals(listOf(0xFF00695C.toInt()), dao.listColors(libraryId).map { it.color })
                val states = dao.listStates(libraryId).associateBy { it.stateKey }
                assertEquals(0xFFFFB74D.toInt(), states["day"]?.primaryColor)
                assertEquals(0xFFE65100.toInt(), states["night"]?.primaryColor)
                assertEquals(0xFFFFD54F.toInt(), states["night"]?.accentColor)
                assertEquals(1, dao.listFavorites().size)

                dao.deleteLibrary(libraryId)
                assertTrue(dao.listColors(libraryId).isEmpty())
                assertTrue(dao.listGroups(libraryId).isEmpty())
                assertTrue(dao.listStates(libraryId).isEmpty())
            }
        }
    }

    // RoomDatabase is not Closeable, so close it explicitly after the assertions.
    // zh-CN: RoomDatabase 不是 Closeable, 断言后显式关闭.
    private fun openMigrated(block: (ColorHistoryDatabase) -> Unit) {
        val db = ColorHistoryDatabase.createBuilder(context, dbName).build()
        try {
            block(db)
        } finally {
            db.close()
        }
    }

    // SQLiteOpenHelper implements AutoCloseable only from API 29, so it is closed explicitly (found on API 28, roadmap P7.2).
    // zh-CN: SQLiteOpenHelper 自 API 29 起才实现 AutoCloseable, 因此显式关闭 (在 API 28 上发现, Roadmap P7.2).
    private fun createLegacy(populate: (SQLiteDatabase) -> Unit) {
        val helper = LegacyOpenHelper(context, dbName, 1, SCHEMA_V1)
        try {
            helper.writableDatabase.use { db -> populate(db) }
        } finally {
            helper.close()
        }
    }

    private class LegacyOpenHelper(
        context: Context,
        name: String,
        version: Int,
        private val statements: List<String>,
    ) : SQLiteOpenHelper(context, name, null, version) {

        override fun onCreate(db: SQLiteDatabase) {
            statements.forEach { db.execSQL(it) }
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {

        // CREATE statements as recorded in app/schemas/.../1.json.
        // zh-CN: 与 schema 快照 1.json 一致的 CREATE 语句.
        private val SCHEMA_V1: List<String> = listOf(
            "CREATE TABLE IF NOT EXISTS `color_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `library_id` INTEGER NOT NULL, `item_id` INTEGER NOT NULL, `last_used_time` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_color_history_library_id_item_id` ON `color_history` (`library_id`, `item_id`)",
            "CREATE TABLE IF NOT EXISTS `palette_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `last_used_time` INTEGER NOT NULL, `hex` TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_palette_history_hex` ON `palette_history` (`hex`)",
        )
    }

}
