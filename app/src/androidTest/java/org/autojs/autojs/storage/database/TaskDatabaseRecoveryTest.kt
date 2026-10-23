package org.autojs.autojs.storage.database

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.timing.IntentTask
import org.autojs.autojs.timing.TimedTask
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Regression for the two task tables and [DatabaseRecovery] (docs/db-downgrade/ROADMAP.md P1.3).
 * The databases live in a private directory through [DirectoryContext], so the task databases the app under test
 * has already opened are left alone.
 *
 * - Downgrade: a file one version above the code version, with one more column and one more table, opens through
 *   the production class; rows, values and the extra column and table are kept, `user_version` becomes the code version.
 * - Idempotent upgrade: 3 -> 5 -> 3 -> 5 (TimedTask) and 2 -> 4 -> 2 -> 4 (IntentTask) neither throw nor duplicate columns.
 * - Fallback: a directory in place of the file is moved aside as `<name>.bak-0-<time>` and an empty database is used;
 *   at most [DatabaseRecovery.MAX_BACKUPS] backups are kept.
 * - Restore: the newest readable backup comes back only after a later install, with a code version not below the
 *   backup version, over a missing or empty database; also for a Room database named without `.db`, whose identity
 *   row does not count.
 *
 * zh-CN: 两张任务表与 [DatabaseRecovery] 的回归 (docs/db-downgrade/ROADMAP.md P1.3). 库文件经 [DirectoryContext] 放在私有目录,
 * 不碰被测应用已经打开的任务库.
 * - 降级: 比代码版本高 1, 多一列与一张表的文件经生产类打开; 行, 值, 多出的列与表都保留, `user_version` 变为代码版本.
 * - 幂等升级: 3 -> 5 -> 3 -> 5 (TimedTask) 与 2 -> 4 -> 2 -> 4 (IntentTask) 不抛异常, 列不重复.
 * - 兜底: 原位置是目录时改名为 `<库名>.bak-0-<时间>` 并使用空库; 最多保留 [DatabaseRecovery.MAX_BACKUPS] 份备份.
 * - 还原: 最新一份可读的备份只在之后的安装, 代码版本不低于备份版本, 当前库不存在或为空时还原; 库名不带 `.db` 的 Room 库同样适用,
 *   其 identity 行不计入.
 *
 * Created by Claude (Opus 5.5) on Oct 23, 2026.
 */
@RunWith(AndroidJUnit4::class)
class TaskDatabaseRecoveryTest {

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var dir: File
    private lateinit var context: DirectoryContext
    private val opened = mutableListOf<Database<*>>()

    @Before
    fun setUp() {
        dir = File(targetContext.cacheDir, "db-downgrade-test").apply { deleteRecursively(); mkdirs() }
        context = DirectoryContext(targetContext, dir)
    }

    @After
    fun tearDown() {
        opened.toList().forEach { close(it) }
        dir.deleteRecursively()
        listOf(TimedTaskDatabase.FILE_NAME, IntentTaskDatabase.FILE_NAME).forEach {
            NotificationManagerCompat.from(targetContext).cancel(DatabaseRecovery.NOTIFICATION_TAG_PREFIX + it.removeSuffix(".db"), DatabaseRecovery.NOTIFICATION_ID)
        }
    }

    @Test
    fun timedTaskDatabaseKeepsNewerFile() {
        writeFile(TimedTaskDatabase.FILE_NAME, TimedTaskDatabase.VERSION + 1) { db ->
            db.execSQL(TIMED_TASK_V5)
            db.execSQL("ALTER TABLE `TimedTask` ADD COLUMN `future_note` TEXT DEFAULT 'future'")
            db.execSQL("CREATE TABLE `future_extra` (`id` INTEGER PRIMARY KEY, `value` TEXT)")
            db.execSQL("INSERT INTO `future_extra` (`value`) VALUES ('kept')")
            db.execSQL(
                "INSERT INTO `TimedTask` (`time`, `scheduled`, `millis`, `script_path`, `repeat_mode`, `source_kind`, `future_note`) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(7L, 1, 4070908800000L, "/sdcard/a.js", 2, "file", "note-a"),
            )
            db.execSQL(
                "INSERT INTO `TimedTask` (`time`, `scheduled`, `millis`, `script_path`) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(8L, 0, 4070908900000L, "/sdcard/b.js"),
            )
        }
        val database = open { TimedTaskDatabase(context) }
        val tasks = database.queryAll().sortedBy { it.id }
        assertEquals(2, tasks.size)
        assertEquals("/sdcard/a.js", tasks[0].scriptPath)
        assertEquals(4070908800000L, tasks[0].millis)
        assertEquals(2, tasks[0].repeatMode)
        assertEquals("file", tasks[0].sourceKind)
        assertEquals("/sdcard/b.js", tasks[1].scriptPath)
        read(TimedTaskDatabase.FILE_NAME) { db ->
            assertEquals(TimedTaskDatabase.VERSION, db.version)
            assertEquals(listOf("note-a", "future"), strings(db, "SELECT `future_note` FROM `TimedTask` ORDER BY `id`"))
            assertEquals(listOf("kept"), strings(db, "SELECT `value` FROM `future_extra`"))
        }
    }

    @Test
    fun intentTaskDatabaseKeepsNewerFile() {
        writeFile(IntentTaskDatabase.FILE_NAME, IntentTaskDatabase.VERSION + 1) { db ->
            db.execSQL(INTENT_TASK_V4)
            db.execSQL("ALTER TABLE `IntentTask` ADD COLUMN `future_note` TEXT DEFAULT 'future'")
            db.execSQL("CREATE TABLE `future_extra` (`id` INTEGER PRIMARY KEY, `value` TEXT)")
            db.execSQL(
                "INSERT INTO `IntentTask` (`script_path`, `action`, `local`, `extras`, `future_note`) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>("/sdcard/c.js", "org.autojs.autojs6.dbdowngrade.NOOP", 1, "{\"k\":1}", "note-c"),
            )
        }
        val database = open { IntentTaskDatabase(context) }
        val tasks = database.queryAll()
        assertEquals(1, tasks.size)
        assertEquals("/sdcard/c.js", tasks[0].scriptPath)
        assertEquals("org.autojs.autojs6.dbdowngrade.NOOP", tasks[0].action)
        assertTrue(tasks[0].isLocal)
        assertEquals("{\"k\":1}", tasks[0].extras)
        read(IntentTaskDatabase.FILE_NAME) { db ->
            assertEquals(IntentTaskDatabase.VERSION, db.version)
            assertEquals(listOf("note-c"), strings(db, "SELECT `future_note` FROM `IntentTask`"))
            assertTrue(tables(db).contains("future_extra"))
        }
    }

    @Test
    fun timedTaskUpgradeIsIdempotent() {
        writeFile(TimedTaskDatabase.FILE_NAME, 3) { db ->
            db.execSQL(TIMED_TASK_V3)
            db.execSQL("INSERT INTO `TimedTask` (`time`, `millis`, `script_path`) VALUES (1, 4070908800000, '/sdcard/old.js')")
        }
        close(open { TimedTaskDatabase(context) })
        val columns = read(TimedTaskDatabase.FILE_NAME) { db -> assertEquals(5, db.version); columns(db, TimedTask.TABLE) }
        assertEquals(16, columns.size)
        // An older build lowers the version and keeps the columns. zh-CN: 旧构建调低版本并保留列.
        write(TimedTaskDatabase.FILE_NAME) { db -> db.version = 3 }
        val database = open { TimedTaskDatabase(context) }
        assertEquals("/sdcard/old.js", database.queryAll().single().scriptPath)
        read(TimedTaskDatabase.FILE_NAME) { db ->
            assertEquals(5, db.version)
            assertEquals(columns, columns(db, TimedTask.TABLE))
        }
    }

    @Test
    fun intentTaskUpgradeIsIdempotent() {
        writeFile(IntentTaskDatabase.FILE_NAME, 2) { db ->
            db.execSQL(INTENT_TASK_V2)
            db.execSQL("INSERT INTO `IntentTask` (`script_path`, `action`, `local`) VALUES ('/sdcard/old.js', 'a', 0)")
        }
        close(open { IntentTaskDatabase(context) })
        val columns = read(IntentTaskDatabase.FILE_NAME) { db -> assertEquals(4, db.version); columns(db, IntentTask.TABLE) }
        assertEquals(9, columns.size)
        write(IntentTaskDatabase.FILE_NAME) { db -> db.version = 2 }
        val database = open { IntentTaskDatabase(context) }
        assertEquals("/sdcard/old.js", database.queryAll().single().scriptPath)
        read(IntentTaskDatabase.FILE_NAME) { db ->
            assertEquals(4, db.version)
            assertEquals(columns, columns(db, IntentTask.TABLE))
        }
    }

    @Test
    fun failedOpenKeepsBackupAndUsesEmptyDatabase() {
        val file = context.getDatabasePath(TimedTaskDatabase.FILE_NAME)
        File(file, "inside").apply { parentFile!!.mkdirs(); writeText("kept") }
        val database = open { DatabaseRecovery.openOrRecover(context, TimedTaskDatabase.FILE_NAME, TimedTaskDatabase.VERSION) { TimedTaskDatabase(context) } }
        assertEquals(0L, database.count())
        val backup = DatabaseRecovery.backupsOf(file).single()
        assertEquals(0, backup.version)
        assertEquals("kept", File(backup.file, "inside").readText())
        read(TimedTaskDatabase.FILE_NAME) { db -> assertEquals(TimedTaskDatabase.VERSION, db.version) }
    }

    @Test
    fun backupsAreRotated() {
        val file = context.getDatabasePath(TimedTaskDatabase.FILE_NAME)
        val old = listOf("20000101000000", "20000102000000", "20000103000000").map { File(dir, "${file.name}.bak-0-$it").apply { mkdirs() } }
        file.mkdirs()
        open { DatabaseRecovery.openOrRecover(context, TimedTaskDatabase.FILE_NAME, TimedTaskDatabase.VERSION) { TimedTaskDatabase(context) } }
        val backups = DatabaseRecovery.backupsOf(file)
        assertEquals(DatabaseRecovery.MAX_BACKUPS, backups.size)
        assertFalse(old[0].exists())
        assertTrue(old[1].exists() && old[2].exists())
    }

    @Test
    fun restoreNeedsLaterInstallAndSatisfiedVersion() {
        val file = context.getDatabasePath(TimedTaskDatabase.FILE_NAME)
        val backup = writeBackup(5, "20261023120000", rows = 2)
        val time = stampTime("20261023120000")
        // Same second as the backup, or a newer backup version: not restored.
        // zh-CN: 与备份同一秒安装, 或备份版本更高: 不还原.
        assertFalse(DatabaseRecovery.restoreIfEligible(file, 5) { time + 999 })
        assertFalse(DatabaseRecovery.restoreIfEligible(file, 4) { time + 2000 })
        assertTrue(backup.exists())
        assertFalse(file.exists())
        assertTrue(DatabaseRecovery.restoreIfEligible(file, 5) { time + 2000 })
        assertFalse(backup.exists())
        assertEquals(2L, read(TimedTaskDatabase.FILE_NAME) { db -> db.compileStatement("SELECT COUNT(*) FROM `TimedTask`").simpleQueryForLong() })
    }

    @Test
    fun restoreReplacesOnlyEmptyDatabase() {
        val file = context.getDatabasePath(TimedTaskDatabase.FILE_NAME)
        val time = stampTime("20261023120000")
        writeFile(TimedTaskDatabase.FILE_NAME, 5) { db -> db.execSQL(TIMED_TASK_V5); db.execSQL("INSERT INTO `TimedTask` (`script_path`) VALUES ('/sdcard/new.js')") }
        val backup = writeBackup(5, "20261023120000", rows = 2)
        assertFalse(DatabaseRecovery.restoreIfEligible(file, 5) { time + 2000 })
        assertTrue(backup.exists())
        write(TimedTaskDatabase.FILE_NAME) { db -> db.execSQL("DELETE FROM `TimedTask`") }
        assertTrue(DatabaseRecovery.restoreIfEligible(file, 5) { time + 2000 })
        assertEquals(2L, read(TimedTaskDatabase.FILE_NAME) { db -> db.compileStatement("SELECT COUNT(*) FROM `TimedTask`").simpleQueryForLong() })
    }

    @Test
    fun restoreConsidersOnlyNewestReadableBackup() {
        val file = context.getDatabasePath(TimedTaskDatabase.FILE_NAME)
        val older = writeBackup(5, "20261023120000", rows = 1)
        val newer = writeBackup(6, "20261023130000", rows = 1)
        File(dir, "${file.name}.bak-0-20261023140000").mkdirs()
        assertFalse(DatabaseRecovery.restoreIfEligible(file, 5) { stampTime("20261023150000") })
        assertTrue(older.exists() && newer.exists())
    }

    @Test
    fun restoreHandlesRoomDatabaseWithoutDbSuffix() {
        // Room's keystore database: no `.db` in the name, and an empty database already has its identity row.
        // zh-CN: Room 的密钥库: 库名不带 `.db`, 空库里也已有一行 identity.
        val file = context.getDatabasePath("keystore-database")
        val roomTables = { db: SQLiteDatabase, rows: Int ->
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES (42, 'hash')")
            db.execSQL("CREATE TABLE `KeyStore` (`absolutePath` TEXT NOT NULL PRIMARY KEY)")
            repeat(rows) { db.execSQL("INSERT INTO `KeyStore` VALUES ('/sdcard/kept-$it.jks')") }
        }
        writeFile("keystore-database.bak-1-20261023120000", 1) { roomTables(it, 1) }
        File(dir, "keystore-database.bak-1-20261023120000-journal").writeText("")
        writeFile("keystore-database", 1) { roomTables(it, 0) }
        assertEquals(listOf("keystore-database.bak-1-20261023120000"), DatabaseRecovery.backupsOf(file).map { it.file.name })
        assertTrue(DatabaseRecovery.restoreIfEligible(file, 1) { stampTime("20261023120002") })
        assertEquals(1L, read("keystore-database") { db -> db.compileStatement("SELECT COUNT(*) FROM `KeyStore`").simpleQueryForLong() })
        assertTrue(DatabaseRecovery.backupsOf(file).isEmpty())
    }

    @Test
    fun openOrRecoverRestoresBackupAfterInstall() {
        // The stamp predates any install of the app under test. zh-CN: 时间戳早于被测应用的任何安装.
        val backup = writeBackup(TimedTaskDatabase.VERSION, "20000101000000", rows = 3)
        val database = open { DatabaseRecovery.openOrRecover(context, TimedTaskDatabase.FILE_NAME, TimedTaskDatabase.VERSION) { TimedTaskDatabase(context) } }
        assertEquals(3L, database.count())
        assertFalse(backup.exists())
        assertTrue(DatabaseRecovery.backupsOf(context.getDatabasePath(TimedTaskDatabase.FILE_NAME)).isEmpty())
    }

    private fun <D : Database<*>> open(block: () -> D): D = block().also { opened.add(it) }

    private fun close(database: Database<*>) {
        opened.remove(database)
        val db = Database::class.java.getDeclaredField("mWritableSQLiteDatabase").apply { isAccessible = true }.get(database) as SQLiteDatabase
        if (db.isOpen) db.close()
    }

    private fun writeFile(name: String, version: Int, block: (SQLiteDatabase) -> Unit) {
        SQLiteDatabase.openOrCreateDatabase(File(dir, name), null).use { db -> block(db); db.version = version }
    }

    private fun writeBackup(version: Int, stamp: String, rows: Int): File {
        val name = "${TimedTaskDatabase.FILE_NAME}.bak-$version-$stamp"
        writeFile(name, version) { db ->
            db.execSQL(TIMED_TASK_V5)
            repeat(rows) { db.execSQL("INSERT INTO `TimedTask` (`script_path`) VALUES ('/sdcard/kept-$it.js')") }
        }
        return File(dir, name)
    }

    private fun write(name: String, block: (SQLiteDatabase) -> Unit) {
        SQLiteDatabase.openDatabase(File(dir, name).path, null, SQLiteDatabase.OPEN_READWRITE).use(block)
    }

    private fun <R> read(name: String, block: (SQLiteDatabase) -> R): R =
        SQLiteDatabase.openDatabase(File(dir, name).path, null, SQLiteDatabase.OPEN_READONLY).use(block)

    private fun strings(db: SQLiteDatabase, sql: String): List<String?> =
        db.rawQuery(sql, null).use { cursor -> List(cursor.count) { cursor.moveToPosition(it); cursor.getString(0) } }

    private fun columns(db: SQLiteDatabase, table: String): List<String?> =
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor -> List(cursor.count) { cursor.moveToPosition(it); cursor.getString(1) } }

    private fun tables(db: SQLiteDatabase) = strings(db, "SELECT name FROM sqlite_master WHERE type = 'table'")

    private fun stampTime(stamp: String) = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).parse(stamp)!!.time

    /**
     * Puts the databases of [base] into [dir]. API 24 - 27 open through [openOrCreateDatabase], later versions through [getDatabasePath].
     * zh-CN: 把 [base] 的库文件放进 [dir]. API 24 - 27 经 [openOrCreateDatabase] 打开, 之后的版本经 [getDatabasePath].
     */
    private class DirectoryContext(base: Context, private val dir: File) : ContextWrapper(base) {

        override fun getDatabasePath(name: String) = File(dir, name)

        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)

        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)

        override fun deleteDatabase(name: String) = SQLiteDatabase.deleteDatabase(getDatabasePath(name))
    }

    private companion object {

        const val TIMED_TASK_V3 = "CREATE TABLE `TimedTask`(`id` INTEGER PRIMARY KEY AUTOINCREMENT, `time` INTEGER, `scheduled` INTEGER, " +
                "`delay` INTEGER, `interval` INTEGER, `loop_times` INTEGER, `millis` INTEGER, `script_path` TEXT)"

        const val TIMED_TASK_V5 = "CREATE TABLE `TimedTask`(`id` INTEGER PRIMARY KEY AUTOINCREMENT, `time` INTEGER, `scheduled` INTEGER, " +
                "`delay` INTEGER, `interval` INTEGER, `loop_times` INTEGER, `millis` INTEGER, `script_path` TEXT, " +
                "`repeat_mode` INTEGER DEFAULT -1, `end_mode` INTEGER DEFAULT 0, `end_value` INTEGER DEFAULT 0, `finished_times` INTEGER DEFAULT 0, " +
                "`day_of_month` INTEGER DEFAULT 0, `month_of_year` INTEGER DEFAULT 0, `source_kind` TEXT, `project_root` TEXT)"

        const val INTENT_TASK_V2 = "CREATE TABLE `IntentTask`(`id` INTEGER PRIMARY KEY AUTOINCREMENT, `script_path` TEXT NOT NULL ON CONFLICT FAIL, " +
                "`action` TEXT, `category` TEXT, `data_type` TEXT, `local` INT)"

        const val INTENT_TASK_V4 = "CREATE TABLE `IntentTask`(`id` INTEGER PRIMARY KEY AUTOINCREMENT, `script_path` TEXT NOT NULL ON CONFLICT FAIL, " +
                "`action` TEXT, `category` TEXT, `data_type` TEXT, `local` INT, `source_kind` TEXT, `project_root` TEXT, `extras` TEXT)"
    }

}
