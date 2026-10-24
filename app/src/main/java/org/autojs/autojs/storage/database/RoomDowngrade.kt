package org.autojs.autojs.storage.database

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps a Room database usable when its file was written by a newer build (`user_version` above the code version),
 * which Room would otherwise reject with "A migration from X to Y was required but not found".
 *
 * [databaseBuilder] installs an open helper factory whose callback sees the file version in `onDowngrade`.
 * There it registers a `Migration(file version, code version)` on the builder and hands over to Room, which runs
 * the migration, validates the result against the entities and updates the identity hash. The migration rebuilds
 * every table of the schema snapshot packaged for the code version (assets `db-schemas/<class>/<version>.json`,
 * copied from `app/schemas` at build time): it creates the table from the recorded `createSql`, copies the columns
 * the file and the snapshot share, drops the old table, renames the new one and recreates the indices.
 * Tables the snapshot does not declare are kept as they are (Room only validates declared entities).
 * A table missing a column this version requires (NOT NULL, no default), or sharing no column with the snapshot,
 * cannot keep its rows, so the migration fails with the table and the reason (db-downgrade roadmap D9).
 * Any failure rolls the transaction back and leaves the file unchanged; the first open goes through
 * [DatabaseRecovery], which then keeps the whole file as a backup, continues with an empty database
 * and restores the backup once a build of that version or later is installed.
 *
 * The file version comes from SQLiteOpenHelper while Room opens the database on a background thread
 * (none of these databases allows main thread queries), so building the database does no disk IO.
 * Registering after build relies on the builder sharing its migration container with the built database,
 * as Room 2.8.1 does; `RoomDowngradeTest` covers it.
 *
 * zh-CN: 让 Room 库在文件被较新构建写过 (`user_version` 高于代码版本) 时仍可打开; 否则 Room 会抛出
 * "A migration from X to Y was required but not found".
 * [databaseBuilder] 安装一个 open helper factory, 其回调在 `onDowngrade` 中得到文件版本, 随即向 builder 注册
 * `Migration(文件版本, 代码版本)` 并交回 Room, 由 Room 执行迁移, 按实体校验结果并更新 identity hash.
 * 迁移按为代码版本打包的 schema 快照 (assets `db-schemas/<类全名>/<版本>.json`, 构建时从 `app/schemas` 复制) 重建
 * 每张表: 按记录的 `createSql` 建表, 复制文件与快照共有的列, 删除旧表, 新表改名, 重建索引. 快照未声明的表原样保留
 * (Room 只校验声明的实体). 某张表缺少本版本必需的列 (NOT NULL 且无默认值), 或与快照没有共有列时, 其行无法保留, 迁移因此失败,
 * 异常写明表名与原因 (db-downgrade 路线图 D9). 任何失败都会回滚事务, 文件保持不变; 首次打开经过 [DatabaseRecovery],
 * 此时由它把整个文件留作备份并改用空库, 装回该版本及以后的构建时再还原备份.
 * 文件版本由 SQLiteOpenHelper 在 Room 于后台线程打开库时给出 (这几个库都不允许主线程查询), 因此构建库时没有磁盘 IO.
 * 构建后再注册迁移依赖 builder 与构建出的库共用同一个迁移容器 (Room 2.8.1 如此), 由 `RoomDowngradeTest` 覆盖.
 *
 * Created by Claude (Opus 5.5) on Oct 23, 2026.
 */
object RoomDowngrade {

    private const val TAG = "RoomDowngrade"

    const val SCHEMA_ASSET_DIR = "db-schemas"

    private const val NEW_TABLE_PREFIX = "_downgrade_new_"

    /**
     * Same as [Room.databaseBuilder], plus the downgrade described in [RoomDowngrade].
     * zh-CN: 与 [Room.databaseBuilder] 相同, 另加 [RoomDowngrade] 所述的降级处理.
     */
    fun <T : RoomDatabase> databaseBuilder(context: Context, klass: Class<T>, name: String): RoomDatabase.Builder<T> {
        val builder = Room.databaseBuilder(context, klass, name)
        val registered = HashSet<Pair<Int, Int>>()
        val register = { fileVersion: Int, codeVersion: Int ->
            // The framework helper retries a failed open once, so the callback may run twice.
            // zh-CN: framework helper 打开失败时会重试一次, 回调可能执行两次.
            synchronized(registered) {
                if (registered.add(fileVersion to codeVersion)) {
                    builder.addMigrations(SchemaDowngradeMigration(context.applicationContext, klass.name, fileVersion, codeVersion))
                }
            }
        }
        return builder.openHelperFactory(OpenHelperFactory(FrameworkSQLiteOpenHelperFactory(), register))
    }

    private class OpenHelperFactory(
        private val delegate: SupportSQLiteOpenHelper.Factory,
        private val register: (fileVersion: Int, codeVersion: Int) -> Unit,
    ) : SupportSQLiteOpenHelper.Factory {

        override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
            val callback = DowngradeCallback(configuration.callback, register)
            val helper = delegate.create(
                SupportSQLiteOpenHelper.Configuration(
                    configuration.context,
                    configuration.name,
                    callback,
                    configuration.useNoBackupDirectory,
                    configuration.allowDataLossOnRecovery,
                )
            )
            return RecoveringOpenHelper(configuration.context, helper, configuration.callback.version)
        }
    }

    /**
     * Sends the first open of [delegate] through [DatabaseRecovery.openOrRecover], so that a file Room still cannot open
     * (the rebuild failed and rolled back, the identity check failed, the file is not a database) is moved aside as a
     * backup and an empty database is created instead of failing every query. Later calls go to [delegate] directly,
     * since Room asks for the database on every query. The open happens on Room's background thread, like the downgrade.
     * zh-CN: 让 [delegate] 的首次打开经过 [DatabaseRecovery.openOrRecover]: Room 仍无法打开的文件 (重建失败并回滚,
     * identity 校验失败, 文件不是数据库) 改名为备份, 改用新建的空库, 而不是每次查询都失败. 之后的调用直接交给 [delegate],
     * 因为 Room 每次查询都会取库. 打开与降级一样发生在 Room 的后台线程.
     */
    private class RecoveringOpenHelper(
        private val context: Context,
        private val delegate: SupportSQLiteOpenHelper,
        private val codeVersion: Int,
    ) : SupportSQLiteOpenHelper by delegate {

        @Volatile
        private var opened = false

        override val writableDatabase: SupportSQLiteDatabase
            get() = database { delegate.writableDatabase }

        override val readableDatabase: SupportSQLiteDatabase
            get() = database { delegate.readableDatabase }

        private fun database(get: () -> SupportSQLiteDatabase): SupportSQLiteDatabase {
            if (opened) return get()
            // An in-memory database has no file to back up.
            // zh-CN: 内存库没有可备份的文件.
            val name = delegate.databaseName ?: return get()
            synchronized(this) {
                if (opened) return get()
                return DatabaseRecovery.openOrRecover(context, name, codeVersion, onFailure = { delegate.close() }, open = get).also { opened = true }
            }
        }

        override fun close() {
            synchronized(this) {
                opened = false
                delegate.close()
            }
        }
    }

    private class DowngradeCallback(
        private val room: SupportSQLiteOpenHelper.Callback,
        private val register: (fileVersion: Int, codeVersion: Int) -> Unit,
    ) : SupportSQLiteOpenHelper.Callback(room.version) {

        override fun onConfigure(db: SupportSQLiteDatabase) = room.onConfigure(db)

        override fun onCreate(db: SupportSQLiteDatabase) = room.onCreate(db)

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = room.onUpgrade(db, oldVersion, newVersion)

        override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            Log.w(TAG, "${db.path}: file version $oldVersion is newer than $newVersion, rebuilding from the schema")
            register(oldVersion, newVersion)
            room.onDowngrade(db, oldVersion, newVersion)
        }

        override fun onOpen(db: SupportSQLiteDatabase) = room.onOpen(db)

        override fun onCorruption(db: SupportSQLiteDatabase) = room.onCorruption(db)
    }

    private class SchemaDowngradeMigration(
        private val context: Context,
        private val databaseClassName: String,
        fileVersion: Int,
        codeVersion: Int,
    ) : Migration(fileVersion, codeVersion) {

        override fun migrate(db: SupportSQLiteDatabase) {
            val schema = context.assets.open("$SCHEMA_ASSET_DIR/$databaseClassName/$endVersion.json").bufferedReader().use {
                Schema.parse(it.readText())
            }
            check(schema.version == endVersion) { "Schema snapshot of $databaseClassName has version ${schema.version}, expected $endVersion" }
            rebuild(db, schema, startVersion)
        }
    }

    internal class Field(val columnName: String, val notNull: Boolean, val defaultValue: String?)

    internal class Entity(
        val tableName: String,
        val createSql: String,
        val fields: List<Field>,
        val autoGeneratedKey: String?,
        val indexSqls: List<String>,
        val isFts: Boolean,
    ) {
        fun createSql(table: String) = createSql.replace(TABLE_NAME_PLACEHOLDER, table)

        fun indexSqls(table: String) = indexSqls.map { it.replace(TABLE_NAME_PLACEHOLDER, table) }
    }

    internal class View(val viewName: String, val createSql: String)

    internal class Schema(val version: Int, val entities: List<Entity>, val views: List<View>) {

        companion object {

            fun parse(json: String): Schema {
                val database = JSONObject(json).getJSONObject("database")
                val entities = database.optJSONArray("entities").objects().map { entity ->
                    val primaryKey = entity.optJSONObject("primaryKey")
                    val keyColumns = primaryKey?.optJSONArray("columnNames")?.strings().orEmpty()
                    Entity(
                        tableName = entity.getString("tableName"),
                        createSql = entity.getString("createSql"),
                        fields = entity.getJSONArray("fields").objects().map { field ->
                            Field(
                                columnName = field.getString("columnName"),
                                notNull = field.optBoolean("notNull"),
                                defaultValue = if (field.has("defaultValue")) field.getString("defaultValue") else null,
                            )
                        },
                        autoGeneratedKey = keyColumns.singleOrNull()?.takeIf { primaryKey?.optBoolean("autoGenerate") == true },
                        indexSqls = entity.optJSONArray("indices").objects().map { it.getString("createSql") },
                        isFts = entity.has("ftsVersion"),
                    )
                }
                val views = database.optJSONArray("views").objects().map { View(it.getString("viewName"), it.getString("createSql")) }
                return Schema(database.getInt("version"), entities, views)
            }

            private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }

            private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
        }
    }

    private const val TABLE_NAME_PLACEHOLDER = "\${TABLE_NAME}"
    private const val VIEW_NAME_PLACEHOLDER = "\${VIEW_NAME}"

    internal fun rebuild(db: SupportSQLiteDatabase, schema: Schema, fileVersion: Int) {
        schema.entities.firstOrNull { it.isFts }?.let { error("FTS table ${it.tableName} is not supported") }
        // Renaming a table makes SQLite 3.26+ re-check every view and trigger, including those of newer tables
        // that may reference a table dropped a moment ago; the legacy behavior only renames.
        // zh-CN: SQLite 3.26+ 改名时会重新检查所有视图与触发器, 包括较新版本的表中可能引用刚删除的表的那些; 旧行为只改名.
        val legacyAlterTable = db.query("PRAGMA legacy_alter_table").use { if (it.moveToFirst()) it.getInt(0) else null }
        db.execSQL("PRAGMA legacy_alter_table = ON")
        try {
            schema.views.forEach { db.execSQL("DROP VIEW IF EXISTS `${it.viewName}`") }
            schema.entities.forEach { rebuildTable(db, it, fileVersion) }
            schema.views.forEach { db.execSQL(it.createSql.replace(VIEW_NAME_PLACEHOLDER, it.viewName)) }
        } finally {
            if (legacyAlterTable == 0) db.execSQL("PRAGMA legacy_alter_table = OFF")
        }
    }

    private fun rebuildTable(db: SupportSQLiteDatabase, entity: Entity, fileVersion: Int) {
        val table = entity.tableName
        val fileColumns = columnsOf(db, table)
        if (fileColumns.isEmpty()) {
            db.execSQL(entity.createSql(table))
            entity.indexSqls(table).forEach { db.execSQL(it) }
            Log.w(TAG, "$table: missing in the file, created")
            return
        }
        val shared = entity.fields.filter { it.columnName in fileColumns }
        check(shared.isNotEmpty()) { "$table (file version $fileVersion) shares no column with the schema snapshot, its rows cannot be kept" }
        val missingRequired = entity.fields.filter {
            it.notNull && it.defaultValue == null && it.columnName !in fileColumns && it.columnName != entity.autoGeneratedKey
        }
        check(missingRequired.isEmpty()) {
            "$table (file version $fileVersion) lacks required column(s) ${missingRequired.joinToString { it.columnName }}, its rows cannot be kept"
        }
        val newTable = NEW_TABLE_PREFIX + table
        db.execSQL("DROP TABLE IF EXISTS `$newTable`")
        db.execSQL(entity.createSql(newTable))
        val sequence = sequenceOf(db, table)
        val columns = shared.joinToString { "`${it.columnName}`" }
        val values = shared.joinToString {
            if (it.notNull && it.defaultValue != null) "COALESCE(`${it.columnName}`, ${it.defaultValue})" else "`${it.columnName}`"
        }
        db.execSQL("INSERT INTO `$newTable` ($columns) SELECT $values FROM `$table`")
        db.execSQL("DROP TABLE `$table`")
        Log.w(TAG, "$table: kept ${shared.size} of ${fileColumns.size} column(s), dropped ${fileColumns.size - shared.size}")
        db.execSQL("ALTER TABLE `$newTable` RENAME TO `$table`")
        entity.indexSqls(table).forEach { db.execSQL(it) }
        if (sequence != null) {
            restoreSequence(db, table, sequence)
        }
    }

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): Set<String> {
        return db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
        }
    }

    // AUTOINCREMENT keys must not reuse ids of rows deleted before the downgrade.
    // zh-CN: AUTOINCREMENT 主键不应复用降级前已删除行的 id.
    private fun sequenceOf(db: SupportSQLiteDatabase, table: String): Long? {
        if (!hasSequenceTable(db)) return null
        return db.query("SELECT seq FROM sqlite_sequence WHERE name = ?", arrayOf(table)).use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    private fun restoreSequence(db: SupportSQLiteDatabase, table: String, sequence: Long) {
        if (!hasSequenceTable(db)) return
        val current = db.query("SELECT seq FROM sqlite_sequence WHERE name = ?", arrayOf(table)).use { if (it.moveToFirst()) it.getLong(0) else null }
        when {
            current == null -> db.execSQL("INSERT INTO sqlite_sequence (name, seq) VALUES (?, ?)", arrayOf<Any>(table, sequence))
            current < sequence -> db.execSQL("UPDATE sqlite_sequence SET seq = ? WHERE name = ?", arrayOf<Any>(sequence, table))
        }
    }

    private fun hasSequenceTable(db: SupportSQLiteDatabase): Boolean {
        return db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'sqlite_sequence'").use { it.moveToFirst() }
    }

}
