package org.autojs.autojs.storage.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.app.NotificationManagerCompat
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.apkbuilder.keystore.KeyStore
import org.autojs.autojs.apkbuilder.keystore.KeyStoreDatabase
import org.autojs.autojs.storage.history.HistoryDatabase
import org.autojs.autojs.theme.app.ColorHistoryDatabase
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Downgrade regression for [RoomDowngrade] (docs/db-downgrade/ROADMAP.md P2.2): a "future" database file is written
 * with raw SQL (the tables of the packaged schema snapshot, one more column, one more table and index, a foreign
 * identity hash, `user_version` one above the code version), then opened through the production builder.
 * Room must open it without throwing, keep the rows and the values of the shared columns, keep the extra table,
 * store the identity hash of the code version, and open it again without another migration.
 * A file whose rebuild fails is kept by [DatabaseRecovery] as a backup and replaced by an empty database.
 *
 * zh-CN: [RoomDowngrade] 的降级回归 (docs/db-downgrade/ROADMAP.md P2.2): 用原生 SQL 写出 "未来" 库文件 (打包的 schema
 * 快照中的表, 多一列, 多一张表与索引, 别的 identity hash, `user_version` 比代码版本高 1), 再经生产 builder 打开.
 * Room 应打开而不抛异常, 保留行与共有列的值, 保留多出的表, 写入代码版本的 identity hash, 再次打开时不再迁移.
 * 重建失败的文件由 [DatabaseRecovery] 留作备份, 改用空库.
 *
 * Created by Claude (Opus 5.5) on Oct 23, 2026.
 */
@RunWith(AndroidJUnit4::class)
class RoomDowngradeTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val names = listOf(HISTORY_DB, COLOR_DB, KEYSTORE_DB, FALLBACK_DB)

    @Before
    fun deleteBefore() = deleteAll()

    @After
    fun deleteAfter() {
        deleteAll()
        NotificationManagerCompat.from(context).cancel(DatabaseRecovery.NOTIFICATION_TAG_PREFIX + FALLBACK_DB.removeSuffix(".db"), DatabaseRecovery.NOTIFICATION_ID)
    }

    private fun deleteAll() = names.forEach { name ->
        DatabaseRecovery.backupsOf(context.getDatabasePath(name)).forEach { context.deleteDatabase(it.file.name) }
        context.deleteDatabase(name)
    }

    @Test
    fun historyDatabaseKeepsSharedColumnsOfNewerFile() {
        val schema = createFuture(HISTORY_DB, HistoryDatabase::class.java) { db ->
            db.execSQL(
                "INSERT INTO file_entry (fileId, logicalPath, createdAt, lastSeenAt, latestFingerprint) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>("file-1", "/storage/emulated/0/a.js", 1000L, 1500L, "fp-1"),
            )
            db.execSQL(
                "INSERT INTO revision (revId, fileId, op, createdAt, logicalPathAtThatTime, encoding, hadBom, sizeBytes, sha256, blobRelPath, label, pinned) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("rev-1", "file-1", "SAVE_PRE", 1200L, "/storage/emulated/0/a.js", "UTF-8", 0, 42L, "sha-1", "history/blob/file-1/rev-1", "before refactor", 1),
            )
            db.execSQL(
                "INSERT INTO trash_item (trashId, originalPath, fileId, trashedAt, isDirectory, sizeBytes, sha256, blobRelPath) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("trash-1", "/storage/emulated/0/old.js", null, 2000L, 0, 12L, null, "trash/blob/trash-1"),
            )
            db.execSQL("ALTER TABLE revision ADD COLUMN future_note TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE revision SET future_note = 'future'")
            db.execSQL("CREATE INDEX index_revision_future_note ON revision (future_note)")
            createFutureTable(db)
        }

        open(HistoryDatabase.createBuilder(context, HISTORY_DB)) { room, db ->
            assertDowngraded(db, schema, "revision")
            val dao = room.historyDao()
            val revision = dao.findRevisionById("rev-1")!!
            assertEquals("SAVE_PRE", revision.op)
            assertEquals("before refactor", revision.label)
            assertTrue(revision.pinned)
            assertEquals(42L, revision.sizeBytes)
            assertEquals("file-1", dao.findFileByPath("/storage/emulated/0/a.js")?.fileId)
            assertEquals("/storage/emulated/0/old.js", dao.getTrashItem("trash-1")?.originalPath)
            assertEquals(0, count(db, "SELECT COUNT(*) FROM sqlite_master WHERE name = 'index_revision_future_note'"))
        }
        reopen(HistoryDatabase.createBuilder(context, HISTORY_DB), HISTORY_DB, schema) { room ->
            assertEquals("before refactor", room.historyDao().findRevisionById("rev-1")?.label)
        }
    }

    @Test
    fun colorHistoryDatabaseKeepsRowsForeignKeysAndSequence() {
        val schema = createFuture(COLOR_DB, ColorHistoryDatabase::class.java) { db ->
            db.execSQL(
                "INSERT INTO color_library (id, kind, is_intelligent, name, origin, config_json, position, created_time, modified_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(7, "USER", 0, "Lib", null, null, 0, 100L, 200L),
            )
            db.execSQL(
                "INSERT INTO color_library_color (id, library_id, name, color, position, created_time, modified_time) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(3, 7, "Teal", 0xFF112233.toInt(), 0, 100L, 200L),
            )
            db.execSQL(
                "INSERT INTO palette_history (id, hex, last_used_time, use_count, source, source_detail) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(5, "#112233", 300L, 3, "USER", "picker"),
            )
            db.execSQL("UPDATE sqlite_sequence SET seq = 100 WHERE name = 'color_library'")
            db.execSQL("ALTER TABLE color_library ADD COLUMN future_icon TEXT")
            createFutureTable(db)
        }

        open(ColorHistoryDatabase.createBuilder(context, COLOR_DB)) { _, db ->
            assertDowngraded(db, schema, "color_library")
            db.query("SELECT name, created_time, modified_time FROM color_library WHERE id = 7").use {
                assertTrue(it.moveToFirst())
                assertEquals("Lib", it.getString(0))
                assertEquals(100L, it.getLong(1))
                assertEquals(200L, it.getLong(2))
            }
            // Dropping the parent table during the rebuild must not cascade to its children.
            // zh-CN: 重建时删除父表不应级联删除子表的行.
            assertEquals(1, count(db, "SELECT COUNT(*) FROM color_library_color WHERE library_id = 7 AND color = ${0xFF112233.toInt()}"))
            db.query("SELECT use_count, source_detail FROM palette_history WHERE id = 5").use {
                assertTrue(it.moveToFirst())
                assertEquals(3, it.getInt(0))
                assertEquals("picker", it.getString(1))
            }
            db.execSQL("INSERT INTO color_library (kind, is_intelligent, name, position, created_time, modified_time) VALUES ('USER', 0, 'New', 1, 1, 1)")
            assertEquals(101, count(db, "SELECT id FROM color_library WHERE name = 'New'"))
        }
        reopen(ColorHistoryDatabase.createBuilder(context, COLOR_DB), COLOR_DB, schema) { room ->
            assertEquals(1, runBlocking { room.paletteHistoryDao().getAll() }.size)
        }
    }

    @Test
    fun keyStoreDatabaseKeepsSharedColumnsOfNewerFile() {
        val schema = createFuture(KEYSTORE_DB, KeyStoreDatabase::class.java) { db ->
            db.execSQL(
                "INSERT INTO KeyStore (absolutePath, filename, password, alias, alias_password, verified) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("/storage/emulated/0/a.bks", "a.bks", "p", "key0", "kp", 1),
            )
            db.execSQL("ALTER TABLE KeyStore ADD COLUMN future_kind TEXT NOT NULL DEFAULT 'bks'")
            createFutureTable(db)
        }

        open(KeyStoreDatabase.createBuilder(context, KEYSTORE_DB)) { room, db ->
            assertDowngraded(db, schema, "KeyStore")
            assertEquals(
                listOf(KeyStore("/storage/emulated/0/a.bks", "a.bks", "p", "key0", "kp", true)),
                runBlocking { room.keyStoreDao().getAll() },
            )
        }
        reopen(KeyStoreDatabase.createBuilder(context, KEYSTORE_DB), KEYSTORE_DB, schema) { room ->
            assertEquals(1, runBlocking { room.keyStoreDao().getAll() }.size)
        }
    }

    @Test
    fun tableMissingRequiredColumnIsKeptAsideAndStartsEmpty() {
        val schema = createFuture(KEYSTORE_DB, KeyStoreDatabase::class.java) { db ->
            // The newer version dropped alias_password (NOT NULL without default in this version).
            // zh-CN: 较新版本删掉了 alias_password (本版本中为 NOT NULL 且无默认值).
            db.execSQL("DROP TABLE KeyStore")
            db.execSQL(
                "CREATE TABLE KeyStore (absolutePath TEXT NOT NULL, filename TEXT NOT NULL, password TEXT NOT NULL, alias TEXT NOT NULL, " +
                        "verified INTEGER NOT NULL, PRIMARY KEY(absolutePath))"
            )
            db.execSQL("INSERT INTO KeyStore VALUES ('/storage/emulated/0/a.bks', 'a.bks', 'p', 'key0', 1)")
        }

        open(KeyStoreDatabase.createBuilder(context, KEYSTORE_DB)) { room, db ->
            assertEquals(schema.version, count(db, "PRAGMA user_version"))
            assertTrue(runBlocking { room.keyStoreDao().getAll() }.isEmpty())
            val kept = "_downgrade_kept_KeyStore_v${schema.version + 1}"
            assertEquals(1, count(db, "SELECT COUNT(*) FROM `$kept` WHERE absolutePath = '/storage/emulated/0/a.bks'"))
            runBlocking { room.keyStoreDao().upsert(KeyStore("/storage/emulated/0/b.bks", "b.bks")) }
            assertEquals(1, runBlocking { room.keyStoreDao().getAll() }.size)
        }
    }

    @Test
    fun fileThatStillCannotOpenIsBackedUpAndReplacedByEmptyDatabase() {
        val schema = createFuture(FALLBACK_DB, HistoryDatabase::class.java) { db ->
            // The newer version made sha256 nullable and stored a NULL, which this version cannot hold,
            // so the rebuild fails and rolls back.
            // zh-CN: 较新版本把 sha256 改为可空并写入了 NULL, 本版本无法容纳, 因此重建失败并回滚.
            db.execSQL("DROP TABLE revision")
            db.execSQL(
                "CREATE TABLE revision (revId TEXT NOT NULL, fileId TEXT NOT NULL, op TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                        "logicalPathAtThatTime TEXT NOT NULL, encoding TEXT NOT NULL, hadBom INTEGER NOT NULL, sizeBytes INTEGER NOT NULL, " +
                        "sha256 TEXT, blobRelPath TEXT NOT NULL, label TEXT, pinned INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(revId))"
            )
            db.execSQL(
                "INSERT INTO revision VALUES ('rev-1', 'file-1', 'SAVE_PRE', 1200, '/storage/emulated/0/a.js', 'UTF-8', 0, 42, NULL, 'history/blob/file-1/rev-1', NULL, 1)"
            )
        }
        val file = context.getDatabasePath(FALLBACK_DB)

        open(HistoryDatabase.createBuilder(context, FALLBACK_DB)) { _, db ->
            assertEquals(schema.version, count(db, "PRAGMA user_version"))
            assertEquals(0, count(db, "SELECT COUNT(*) FROM revision"))
            db.execSQL(
                "INSERT INTO file_entry (fileId, logicalPath, createdAt, lastSeenAt, latestFingerprint) VALUES ('file-2', '/storage/emulated/0/b.js', 1, 1, 'fp-2')"
            )
        }
        val backup = DatabaseRecovery.backupsOf(file).single()
        assertEquals(schema.version + 1, backup.version)
        SQLiteDatabase.openDatabase(backup.file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(schema.version + 1, db.version)
            db.rawQuery("SELECT COUNT(*) FROM revision WHERE revId = 'rev-1' AND sha256 IS NULL", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
        }

        // The backup is newer than the code, so the next open neither restores it nor backs up again.
        // zh-CN: 备份比代码新, 因此下次打开既不还原它, 也不再次备份.
        open(HistoryDatabase.createBuilder(context, FALLBACK_DB)) { _, db ->
            assertEquals(1, count(db, "SELECT COUNT(*) FROM file_entry WHERE fileId = 'file-2'"))
        }
        assertEquals(listOf(backup.file), DatabaseRecovery.backupsOf(file).map { it.file })
    }

    private class SchemaInfo(val version: Int, val identityHash: String, val columns: Map<String, Set<String>>)

    /**
     * Writes the tables of the packaged snapshot of [klass], lets [future] add rows and newer changes,
     * then stamps a foreign identity hash and `user_version` one above the snapshot.
     * zh-CN: 写出 [klass] 打包快照中的表, 由 [future] 写入行与较新的变化, 再写入别的 identity hash 与比快照高 1 的 `user_version`.
     */
    private fun createFuture(name: String, klass: Class<out RoomDatabase>, future: (SQLiteDatabase) -> Unit): SchemaInfo {
        val asset = context.assets.list("${RoomDowngrade.SCHEMA_ASSET_DIR}/${klass.name}")!!.maxBy { it.substringBefore('.').toInt() }
        val json = context.assets.open("${RoomDowngrade.SCHEMA_ASSET_DIR}/${klass.name}/$asset").bufferedReader().use { it.readText() }
        val schema = RoomDowngrade.Schema.parse(json)
        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema.entities.forEach { entity ->
                db.execSQL(entity.createSql(entity.tableName))
                entity.indexSqls(entity.tableName).forEach { db.execSQL(it) }
            }
            future(db)
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'future-identity-hash')")
            db.version = schema.version + 1
        }
        return SchemaInfo(
            version = schema.version,
            identityHash = JSONObject(json).getJSONObject("database").getString("identityHash"),
            columns = schema.entities.associate { entity -> entity.tableName to entity.fields.map { it.columnName }.toSet() },
        )
    }

    private fun createFutureTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE future_table (id INTEGER PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
        db.execSQL("CREATE INDEX index_future_table_payload ON future_table (payload)")
        db.execSQL("INSERT INTO future_table (id, payload) VALUES (1, 'kept')")
    }

    private fun assertDowngraded(db: SupportSQLiteDatabase, schema: SchemaInfo, widenedTable: String) {
        assertEquals(schema.version, count(db, "PRAGMA user_version"))
        db.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use {
            assertTrue(it.moveToFirst())
            assertEquals(schema.identityHash, it.getString(0))
        }
        assertEquals(schema.columns.getValue(widenedTable), columnsOf(db, widenedTable))
        assertEquals(1, count(db, "SELECT COUNT(*) FROM future_table WHERE payload = 'kept'"))
        assertEquals(0, count(db, "SELECT COUNT(*) FROM sqlite_master WHERE name LIKE '\\_downgrade\\_%' ESCAPE '\\'"))
    }

    // RoomDatabase is not Closeable; the support database triggers the open (and the downgrade) off the main thread.
    // zh-CN: RoomDatabase 不是 Closeable; 通过 support 库在非主线程触发打开 (及降级).
    private fun <T : RoomDatabase> open(builder: RoomDatabase.Builder<T>, block: (T, SupportSQLiteDatabase) -> Unit) {
        val room = builder.build()
        try {
            block(room, room.openHelper.writableDatabase)
        } finally {
            room.close()
        }
    }

    private fun <T : RoomDatabase> reopen(builder: RoomDatabase.Builder<T>, name: String, schema: SchemaInfo, block: (T) -> Unit) {
        // The file now has the code version, so SQLiteOpenHelper does not call onDowngrade again,
        // and Room's identity check passes only if the migration stored the code version's hash.
        // zh-CN: 文件已是代码版本, SQLiteOpenHelper 不会再次调用 onDowngrade; 只有迁移写入了代码版本的 hash, Room 的 identity 检查才会通过.
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(schema.version, it.version)
        }
        open(builder) { room, db ->
            assertFalse(columnsOf(db, "future_table").isEmpty())
            block(room)
        }
    }

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): Set<String> {
        return db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }
    }

    private fun count(db: SupportSQLiteDatabase, sql: String): Int {
        return db.query(sql).use { if (it.moveToFirst()) it.getInt(0) else -1 }
    }

    private companion object {
        const val HISTORY_DB = "room-downgrade-test-history.db"
        const val COLOR_DB = "room-downgrade-test-color.db"
        const val KEYSTORE_DB = "room-downgrade-test-keystore"
        const val FALLBACK_DB = "room-downgrade-test-fallback.db"
    }
}
