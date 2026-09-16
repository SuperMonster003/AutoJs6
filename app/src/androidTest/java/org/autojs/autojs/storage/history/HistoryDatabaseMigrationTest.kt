package org.autojs.autojs.storage.history

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration regression for [HistoryDatabase] that does not need `room-testing` (unavailable offline):
 * a legacy database is created with the CREATE statements recorded in
 * `app/schemas/org.autojs.autojs.storage.history.HistoryDatabase/{1,2}.json`, populated with raw SQL,
 * then opened through the production builder so Room runs `MIGRATION_1_2` / `MIGRATION_2_3` and
 * validates the migrated tables against the version 3 entities (a mismatch throws on open).
 *
 * zh-CN: 不依赖 `room-testing` (离线不可用) 的 [HistoryDatabase] 迁移回归: 用 schema 快照 1.json / 2.json 中记录的
 * CREATE 语句建立旧版数据库并用原生 SQL 写入数据, 再经生产 builder 打开, 由 Room 执行 `MIGRATION_1_2` / `MIGRATION_2_3`
 * 并按 v3 实体校验迁移结果 (不一致会在打开时抛出).
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
@RunWith(AndroidJUnit4::class)
class HistoryDatabaseMigrationTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val dbName = "history-migration-test.db"

    @Before
    fun deleteBefore() {
        context.deleteDatabase(dbName)
    }

    @After
    fun deleteAfter() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate2To3KeepsRowsAndAddsLabelAndPinned() {
        createLegacy(2, SCHEMA_V2) { db ->
            insertFileEntry(db)
            insertRevision(db)
            db.execSQL(
                "INSERT INTO trash_item (trashId, originalPath, fileId, trashedAt, isDirectory, sizeBytes, sha256, blobRelPath) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("trash-1", "/storage/emulated/0/old.js", null, 2000L, 0, 12L, null, "trash/blob/trash-1"),
            )
        }

        openMigrated { db ->
            val dao = db.historyDao()

            val revision = dao.findRevisionById("rev-1")
            assertNotNull(revision)
            assertNull(revision!!.label)
            assertFalse(revision.pinned)
            assertEquals("SAVE_PRE", revision.op)
            assertEquals("file-1", dao.findFileByPath("/storage/emulated/0/a.js")?.fileId)
            assertEquals(listOf("rev-1"), dao.listOldestUnpinnedRevisions(10).map { it.revId })

            // The new columns are writable through the DAO.
            dao.updateRevisionPinned("rev-1", true)
            dao.updateRevisionLabel("rev-1", "before refactor")
            val updated = dao.findRevisionById("rev-1")!!
            assertTrue(updated.pinned)
            assertEquals("before refactor", updated.label)
            assertTrue(dao.listOldestUnpinnedRevisions(10).isEmpty())

            assertEquals("/storage/emulated/0/old.js", dao.getTrashItem("trash-1")?.originalPath)
        }
    }

    @Test
    fun migrate1To3CreatesTrashTableAndKeepsRevisions() {
        createLegacy(1, SCHEMA_V1) { db ->
            insertFileEntry(db)
            insertRevision(db)
        }

        openMigrated { db ->
            val dao = db.historyDao()

            val revision = dao.findRevisionById("rev-1")
            assertNotNull(revision)
            assertFalse(revision!!.pinned)
            assertNull(revision.label)

            val item = TrashEntities.TrashItem(
                trashId = "trash-2",
                originalPath = "/storage/emulated/0/b.js",
                fileId = "file-1",
                trashedAt = 3000L,
                isDirectory = false,
                sizeBytes = 5L,
                sha256 = null,
                blobRelPath = "trash/blob/trash-2",
            )
            dao.upsertTrashItem(item)
            assertEquals(item, dao.getTrashItem("trash-2"))
            assertEquals(listOf("trash/blob/trash-2"), dao.listAllTrashBlobRelPaths())
        }
    }

    // RoomDatabase is not Closeable, so close it explicitly after the assertions.
    // zh-CN: RoomDatabase 不是 Closeable, 断言后显式关闭.
    private fun openMigrated(block: (HistoryDatabase) -> Unit) {
        val db = HistoryDatabase.createBuilder(context, dbName).build()
        try {
            block(db)
        } finally {
            db.close()
        }
    }

    private fun insertFileEntry(db: SQLiteDatabase) {
        db.execSQL(
            "INSERT INTO file_entry (fileId, logicalPath, createdAt, lastSeenAt, latestFingerprint) VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any?>("file-1", "/storage/emulated/0/a.js", 1000L, 1500L, "fp-1"),
        )
    }

    private fun insertRevision(db: SQLiteDatabase) {
        db.execSQL(
            "INSERT INTO revision (revId, fileId, op, createdAt, logicalPathAtThatTime, encoding, hadBom, sizeBytes, sha256, blobRelPath) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("rev-1", "file-1", "SAVE_PRE", 1200L, "/storage/emulated/0/a.js", "UTF-8", 0, 42L, "sha-1", "history/blob/file-1/rev-1"),
        )
    }

    private fun createLegacy(version: Int, statements: List<String>, populate: (SQLiteDatabase) -> Unit) {
        LegacyOpenHelper(context, dbName, version, statements).use { helper ->
            helper.writableDatabase.use { db -> populate(db) }
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

        // CREATE statements as recorded in app/schemas/.../1.json (file_entry + revision without label / pinned).
        // zh-CN: 与 schema 快照 1.json 一致的 CREATE 语句 (file_entry + 无 label / pinned 的 revision).
        private val SCHEMA_V1: List<String> = listOf(
            "CREATE TABLE IF NOT EXISTS `file_entry` (`fileId` TEXT NOT NULL, `logicalPath` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastSeenAt` INTEGER NOT NULL, `latestFingerprint` TEXT NOT NULL, PRIMARY KEY(`fileId`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_file_entry_logicalPath` ON `file_entry` (`logicalPath`)",
            "CREATE INDEX IF NOT EXISTS `index_file_entry_latestFingerprint` ON `file_entry` (`latestFingerprint`)",
            "CREATE INDEX IF NOT EXISTS `index_file_entry_lastSeenAt` ON `file_entry` (`lastSeenAt`)",
            "CREATE TABLE IF NOT EXISTS `revision` (`revId` TEXT NOT NULL, `fileId` TEXT NOT NULL, `op` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `logicalPathAtThatTime` TEXT NOT NULL, `encoding` TEXT NOT NULL, `hadBom` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, `sha256` TEXT NOT NULL, `blobRelPath` TEXT NOT NULL, PRIMARY KEY(`revId`))",
            "CREATE INDEX IF NOT EXISTS `index_revision_fileId` ON `revision` (`fileId`)",
            "CREATE INDEX IF NOT EXISTS `index_revision_createdAt` ON `revision` (`createdAt`)",
        )

        // 2.json = 1.json + trash_item (what MIGRATION_1_2 creates).
        // zh-CN: 2.json = 1.json + trash_item (即 MIGRATION_1_2 所建).
        private val SCHEMA_V2: List<String> = SCHEMA_V1 + listOf(
            "CREATE TABLE IF NOT EXISTS `trash_item` (`trashId` TEXT NOT NULL, `originalPath` TEXT NOT NULL, `fileId` TEXT, `trashedAt` INTEGER NOT NULL, `isDirectory` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, `sha256` TEXT, `blobRelPath` TEXT NOT NULL, PRIMARY KEY(`trashId`))",
            "CREATE INDEX IF NOT EXISTS `index_trash_item_originalPath` ON `trash_item` (`originalPath`)",
            "CREATE INDEX IF NOT EXISTS `index_trash_item_trashedAt` ON `trash_item` (`trashedAt`)",
        )
    }
}
