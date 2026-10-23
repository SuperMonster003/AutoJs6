package org.autojs.autojs.storage.database

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.autojs.autojs.external.foreground.AppForegroundService
import org.autojs.autojs.util.ForegroundServiceUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Last resort for a database that cannot be opened at all (any exception from the open call), so that the app
 * still starts instead of crashing on every launch.
 *
 * [openOrRecover] moves the file aside as `<name>.bak-<file version>-<yyyyMMddHHmmss>` (its `-journal` / `-wal` /
 * `-shm` follow as `<backup>-wal` and so on, the way SQLite names them, so a backup opens as it is) and opens again,
 * which creates an empty database. The file version is the `user_version` of the file, 0 when it cannot be read.
 * The host posts a notification; the packaged app (inrt) only logs.
 * Each database keeps the [MAX_BACKUPS] newest backups.
 *
 * Before opening, the newest readable backup (file version above 0) is moved back when all of these hold:
 * the app was installed or updated after the backup was made, the code version is not below the backup version,
 * and the current database is missing or has no rows. Older backups are never restored over a newer one.
 * Everything is read from the file names, so no record can drift from the files on disk.
 *
 * zh-CN: 数据库完全无法打开 (打开调用抛出任何异常) 时的兜底, 让应用照常启动, 而不是每次启动都崩溃.
 * [openOrRecover] 把文件改名为 `<库名>.bak-<文件版本>-<yyyyMMddHHmmss>` (其 `-journal` / `-wal` / `-shm` 随之改为
 * `<备份名>-wal` 等, 与 SQLite 的命名方式相同, 备份可直接打开), 再次打开, 得到空库. 文件版本取文件的 `user_version`,
 * 读不到时记 0. 宿主发一条通知, 打包应用 (inrt) 只记日志. 每个库保留最新的 [MAX_BACKUPS] 份备份.
 * 打开之前, 最新一份可读的备份 (文件版本大于 0) 在以下条件都满足时改回原名: 应用在备份之后安装或更新过, 代码版本不低于备份版本,
 * 当前库不存在或没有任何行. 不会用较旧的备份覆盖较新的备份. 一切信息都从文件名读取, 不存在记录与磁盘文件不一致的问题.
 *
 * Created by Claude (Opus 5.5) on Oct 23, 2026.
 */
object DatabaseRecovery {

    const val TAG = "DatabaseRecovery"

    const val MAX_BACKUPS = 3

    /**
     * Notification tag prefix, followed by the database name without `.db`.
     * zh-CN: 通知 tag 前缀, 后接去掉 `.db` 的库名.
     */
    const val NOTIFICATION_TAG_PREFIX = "db-recovery:"

    const val NOTIFICATION_ID = 0xDB0

    private const val STAMP_PATTERN = "yyyyMMddHHmmss"

    private val COMPANION_SUFFIXES = listOf("-journal", "-wal", "-shm")

    // Any database name, with or without `.db` (Room's `keystore-database` has none).
    // zh-CN: 任意库名, 带不带 `.db` 都可以 (Room 的 `keystore-database` 不带).
    private val BACKUP_NAME = Regex("""^(.+)\.bak-(\d+)-(\d{14})$""")

    // Tables that a new, empty database already has rows in. zh-CN: 新建的空库里就已经有行的表.
    private val INTERNAL_TABLES = listOf("android_metadata", "room_master_table")

    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    class Backup(val file: File, val version: Int, val time: Long)

    /**
     * Opens the database [name] with [open], restoring or creating a backup as described in [DatabaseRecovery].
     * [onFailure] runs after a failed open and before the files are moved, e.g. to close the helper.
     * A second failure is thrown.
     * zh-CN: 用 [open] 打开库 [name], 按 [DatabaseRecovery] 所述还原或备份. 打开失败后, 移动文件之前先执行 [onFailure]
     * (例如关闭 helper). 第二次失败直接抛出.
     */
    @JvmStatic
    @JvmOverloads
    fun <T> openOrRecover(context: Context, name: String, codeVersion: Int, onFailure: (Throwable) -> Unit = {}, open: () -> T): T {
        val file = context.getDatabasePath(name)
        runCatching { restoreIfEligible(file, codeVersion) { installedAt(context) } }
            .onFailure { Log.w(TAG, "$name: cannot check the backups", it) }
        val error = try {
            return open()
        } catch (e: Exception) {
            e
        }
        onFailure(error)
        val backup = backUp(file) ?: throw error
        Log.w(TAG, "$name: cannot open, kept as ${backup.file.name} (file version ${backup.version}), continuing with an empty database", error)
        prune(file)
        notify(context, file, backup)
        return open()
    }

    /**
     * Moves [file] and its companions aside. Returns `null` when there is nothing to move or the move fails.
     * zh-CN: 把 [file] 及其伴随文件改名备份. 没有可移动的文件或改名失败时返回 `null`.
     */
    @VisibleForTesting
    @JvmStatic
    fun backUp(file: File): Backup? {
        if (!file.exists()) return null
        val version = fileVersion(file)
        var time = System.currentTimeMillis() / 1000 * 1000
        var target: File
        while (true) {
            target = File(file.parentFile, "${file.name}.bak-$version-${format(time)}")
            if (!target.exists() && COMPANION_SUFFIXES.none { File(target.path + it).exists() }) break
            time += 1000
        }
        if (!file.renameTo(target)) return null
        COMPANION_SUFFIXES.forEach { suffix ->
            val companion = File(file.path + suffix)
            if (companion.exists() && !companion.renameTo(File(target.path + suffix))) {
                Log.w(TAG, "${file.name}: cannot move ${companion.name} aside")
            }
        }
        return Backup(target, version, time)
    }

    /**
     * Moves the newest readable backup of [file] back when the conditions in [DatabaseRecovery] hold.
     * [installedAt] (milliseconds) is only asked for when there is such a backup.
     * zh-CN: 满足 [DatabaseRecovery] 所述条件时, 把 [file] 最新一份可读的备份改回原名. 只有存在这样的备份时才取 [installedAt] (毫秒).
     */
    @VisibleForTesting
    @JvmStatic
    fun restoreIfEligible(file: File, codeVersion: Int, installedAt: () -> Long): Boolean {
        val latest = backupsOf(file).filter { it.version > 0 && isSQLiteFile(it.file) }.maxByOrNull { it.time } ?: return false
        if (latest.version > codeVersion) return false
        // Backup names hold whole seconds: restore only after an install in a later second.
        // zh-CN: 备份名只精确到秒: 只在之后一秒及以后的安装之后还原.
        if (installedAt() < latest.time + 1000) return false
        if (file.exists()) {
            if (!hasNoRows(file)) return false
            (listOf(file) + COMPANION_SUFFIXES.map { File(file.path + it) }).forEach { it.delete() }
        }
        COMPANION_SUFFIXES.forEach { suffix ->
            val companion = File(latest.file.path + suffix)
            if (companion.exists()) companion.renameTo(File(file.path + suffix))
        }
        if (!latest.file.renameTo(file)) {
            Log.w(TAG, "${file.name}: cannot restore ${latest.file.name}")
            return false
        }
        Log.w(TAG, "${file.name}: restored ${latest.file.name} (code version $codeVersion)")
        return true
    }

    @VisibleForTesting
    @JvmStatic
    fun backupsOf(file: File): List<Backup> {
        val names = file.parentFile?.list() ?: return emptyList()
        return names.mapNotNull { name ->
            val match = BACKUP_NAME.matchEntire(name) ?: return@mapNotNull null
            val (base, version, stamp) = match.destructured
            if (base != file.name) return@mapNotNull null
            val time = parse(stamp) ?: return@mapNotNull null
            Backup(File(file.parentFile, name), version.toIntOrNull() ?: return@mapNotNull null, time)
        }
    }

    /**
     * `user_version` of [file], read through SQLite or else from the header; 0 when neither works.
     * zh-CN: [file] 的 `user_version`, 先经 SQLite 读取, 否则读文件头; 都不行时为 0.
     */
    @VisibleForTesting
    @JvmStatic
    fun fileVersion(file: File): Int {
        if (!isSQLiteFile(file)) return 0
        runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { return it.version }
        }
        return runCatching {
            RandomAccessFile(file, "r").use { it.seek(60); it.readInt() }
        }.getOrDefault(0).coerceAtLeast(0)
    }

    private fun prune(file: File) {
        backupsOf(file).sortedByDescending { it.time }.drop(MAX_BACKUPS).forEach { backup ->
            backup.file.deleteRecursively()
            COMPANION_SUFFIXES.forEach { File(backup.file.path + it).delete() }
            Log.w(TAG, "${file.name}: removed the old backup ${backup.file.name}")
        }
    }

    private fun isSQLiteFile(file: File): Boolean {
        if (!file.isFile || file.length() < 100) return false
        return runCatching {
            RandomAccessFile(file, "r").use { input ->
                ByteArray(SQLITE_MAGIC.size).also { input.readFully(it) }.contentEquals(SQLITE_MAGIC)
            }
        }.getOrDefault(false)
    }

    /**
     * Whether no table other than the SQLite, Android and Room internal ones has a row. A file that cannot be read counts
     * as having rows, so it is never replaced.
     * zh-CN: 除 SQLite, Android 与 Room 内部表外的所有表是否都没有行. 读不了的文件算作有行, 因此不会被替换.
     */
    private fun hasNoRows(file: File): Boolean = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val tables = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite\\_%' ESCAPE '\\'",
                null,
            ).use { cursor -> List(cursor.count) { cursor.moveToPosition(it); cursor.getString(0) } }
            tables.filter { it !in INTERNAL_TABLES }.none { table ->
                db.rawQuery("SELECT EXISTS (SELECT 1 FROM `${table.replace("`", "``")}`)", null).use { it.moveToFirst() && it.getInt(0) != 0 }
            }
        }
    }.getOrDefault(false)

    private fun installedAt(context: Context): Long {
        val packageManager = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, 0)
        }
        return info.lastUpdateTime
    }

    /**
     * Posts the notice on the channel of the foreground service, the one channel of the app for its own notices
     * (as `AndroidSettingsAutoBackup` does). No tap action.
     * zh-CN: 在前台服务的通知渠道 (应用自身提示所用的唯一渠道) 发出提示 (与 `AndroidSettingsAutoBackup` 相同). 没有点按动作.
     */
    private fun notify(context: Context, file: File, backup: Backup) {
        if (BuildConfig.isInrt) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            val label = context.packageManager.getApplicationLabel(context.applicationInfo).toString()
            val channelName = context.getString(R.string.foreground_notification_channel_name, label)
            ForegroundServiceUtils.createNotificationChannelIfNeeded(context, AppForegroundService::class.java, channelName, channelName)
            val content = when (backup.version) {
                0 -> context.getString(R.string.database_recovery_notification_content_unreadable, file.name, backup.file.name)
                else -> context.getString(R.string.database_recovery_notification_content, file.name, backup.file.name, backup.version)
            }
            val notification = NotificationCompat.Builder(context, ForegroundServiceUtils.getChannelId(AppForegroundService::class.java))
                .setSmallIcon(R.drawable.autojs6_status_bar_icon)
                .setContentTitle(context.getString(R.string.database_recovery_notification_title))
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG_PREFIX + file.name.removeSuffix(".db"), NOTIFICATION_ID, notification)
        }.onFailure { Log.w(TAG, "${file.name}: cannot post the notification", it) }
    }

    private fun format(time: Long) = SimpleDateFormat(STAMP_PATTERN, Locale.US).format(Date(time))

    private fun parse(stamp: String): Long? = runCatching {
        SimpleDateFormat(STAMP_PATTERN, Locale.US).apply { isLenient = false }.parse(stamp)?.time
    }.getOrNull()

}
