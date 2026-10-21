package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.KeyFamily
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.SettingsRegistry
import org.autojs.autojs.core.pref.store.SettingsStore
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The internal backups of imports and resets (docs/settings/ROADMAP.md T9, D8 / D11), in [directory]
 * (`filesDir/settings-backups/` in the app): before an import or a reset to defaults, every settings category,
 * credentials (sealed for the device [key], D23) and `other` included (private directory, R6 allows it), is written as `auto-import-<ts>.zip` or
 * `auto-reset-<ts>.zip`, and [KEEP] of them are kept together, the newest ones. Once the operation is done,
 * [LAST_OPERATION] records what it touched; "undo the last import" ([undoPlan]) imports the touched categories again
 * from its backup, through the same planner (T8) without deployment items, and writes no backup of its own (the
 * operation backup of T20 is P9's). Data categories (T19) are recorded but not undone yet: appendix C Q27 decided that
 * they are, which P10.3 implements; until then the undo lists them ([Undo.dataNotUndone]). Pure JVM (R3):
 * the snapshot, the host and the clock come from the caller.
 *
 * zh-CN: 导入与恢复默认的内部备份 (ROADMAP T9, D8 / D11), 位于 [directory] (应用中为 `filesDir/settings-backups/`): 导入或恢复默认
 * 之前, 把全部设置分类 (含凭据与 `other`, 私有目录, R6 允许; 凭据以设备密钥 [key] 加密, D23) 写为 `auto-import-<ts>.zip` 或 `auto-reset-<ts>.zip`, 两种合计保留最新的
 * [KEEP] 份. 操作完成后由 [LAST_OPERATION] 记录触及的分类; "撤销上次导入" ([undoPlan]) 以其备份对触及分类再做一次导入, 经同一计划器
 * (T8) 且没有部署项, 撤销本身不写内部备份 (T20 的操作后备份属于 P9). 数据分类 (T19) 目前只记录不撤销: 附录 C Q27 已定为也要撤销,
 * 由 P10.3 实现, 在此之前撤销结果把它们列出 ([Undo.dataNotUndone]). 纯 JVM (R3): 快照,
 * 宿主与时钟由调用方提供.
 */
class SettingsBackupVault @JvmOverloads constructor(
    val directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val keep: Int = KEEP,
    private val key: SettingsDeviceKey? = null,
) {

    init {
        require(keep >= 1) { "A vault keeps at least one backup" }
    }

    /** What a backup is taken before. zh-CN: 备份在何种操作之前生成. */
    enum class Operation(val label: String) {
        IMPORT("import"),
        RESET("reset"),
        ;

        val prefix: String
            get() = "auto-$label-"

        companion object {
            @JvmStatic
            fun ofLabel(label: String): Operation? = values().firstOrNull { it.label == label }
        }
    }

    /** One backup file: the operation, its time as named (`yyyyMMdd-HHmmss`) and a counter of the same second. */
    data class Backup(val file: File, val operation: Operation, val time: String, val sequence: Int) {
        val name: String
            get() = file.name
    }

    /**
     * The record of the last import or reset ([LAST_OPERATION]): when it ran, the backup taken before it, and the
     * settings and data categories it touched.
     * zh-CN: 最近一次导入或恢复默认的记录: 运行时间, 之前生成的备份, 触及的设置分类与数据分类.
     */
    data class LastOperation @JvmOverloads constructor(
        val operation: Operation,
        val time: Long,
        val backup: String,
        val categories: List<String>,
        val data: List<String> = emptyList(),
    ) {

        fun toJson(): Map<String, Any?> = linkedMapOf(
            "operation" to operation.label,
            "time" to time,
            "backup" to backup,
            "categories" to categories,
            "data" to data,
        )

        companion object {

            /** The record of [json], or null when it is no record of this version. */
            @JvmStatic
            fun fromJson(json: Any?): LastOperation? {
                val root = json as? Map<*, *> ?: return null
                val operation = (root["operation"] as? String)?.let(Operation::ofLabel) ?: return null
                val time = SettingsJson.toLong(root["time"]) ?: return null
                val backup = (root["backup"] as? String)?.takeIf { BACKUP_NAME.matches(it) } ?: return null
                val categories = strings(root["categories"]) ?: return null
                val data = if (root["data"] == null) emptyList() else strings(root["data"]) ?: return null
                return LastOperation(operation, time, backup, categories, data)
            }

            private fun strings(json: Any?): List<String>? =
                (json as? List<*>)?.takeIf { list -> list.all { it is String && SettingsArchive.isCategoryId(it) } }?.map { it as String }
        }
    }

    /**
     * What undoing the last operation does: the plan of importing its touched categories from its backup (no
     * deployment items), and the data categories it touched that this version does not undo yet (Q27 decided they are
     * undone, P10.3; the result page tells about them only when there are any).
     * zh-CN: 撤销上次操作的内容: 以其备份导入触及分类的计划 (没有部署项), 以及本版本尚未撤销的触及数据分类 (Q27 已定为也要撤销, P10.3;
     * 结果页只在有这类分类时提示).
     */
    data class Undo(val operation: LastOperation, val plan: ImportPlan, val dataNotUndone: List<String>)

    /** The backups, newest first. zh-CN: 全部备份, 最新的在前. */
    fun backups(): List<Backup> = (directory.listFiles() ?: emptyArray()).mapNotNull(::backupOf)
        .sortedWith(compareByDescending<Backup> { it.time }.thenByDescending { it.sequence })

    /**
     * Writes [snapshot] (every settings category: `SettingsSnapshot.capture(SettingCategory.ALL)`) and [data] as the
     * backup of [operation], then keeps the newest [keep] backups ([prune]); returns the backup. A failed write leaves
     * no partial file behind.
     * zh-CN: 把 [snapshot] (全部设置分类) 与 [data] 写为 [operation] 的备份, 然后只保留最新的 [keep] 份 ([prune]); 返回该备份.
     * 写入失败不留下不完整的文件.
     */
    @JvmOverloads
    fun backup(
        operation: Operation,
        snapshot: SettingsSnapshot,
        host: SettingsArchiveManifest.Host,
        device: SettingsArchiveManifest.Device,
        data: List<SettingsArchiveWriter.DataFile> = emptyList(),
    ): Backup {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create $directory")
        val now = clock()
        val time = FILE_NAME_TIME.format(Instant.ofEpochMilli(now).atZone(zone))
        val taken = backups().filter { it.time == time }.maxOfOrNull { it.sequence }
        val sequence = if (taken == null) 0 else taken + 1
        val file = File(directory, operation.prefix + time + (if (sequence == 0) "" else "-$sequence") + ".zip")
        writeAtomically(file) { out -> SettingsArchiveWriter.write(out, snapshot, SettingsArchive.exportTime(now, zone), host, device, data, key = key) }
        prune()
        return backupOf(file)!!
    }

    /**
     * Deletes the backups beyond the newest [keep] and returns them; files that are no backups stay.
     * zh-CN: 删除最新 [keep] 份之外的备份并返回它们; 不是备份的文件保留.
     */
    fun prune(): List<File> = backups().drop(keep).map { it.file }.filter { it.delete() }

    /**
     * Records [operation] as the last operation ([LAST_OPERATION]); call it once the import or reset is done.
     * zh-CN: 把 [operation] 记录为最近一次操作; 在导入或恢复默认完成后调用.
     */
    fun record(operation: LastOperation) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create $directory")
        writeAtomically(File(directory, LAST_OPERATION)) { it.write(SettingsJson.stringify(operation.toJson(), indent = 2).toByteArray(Charsets.UTF_8)) }
    }

    /** The last operation, or null when there is none or its record is unreadable. zh-CN: 最近一次操作; 没有或无法读取时为 null. */
    fun lastOperation(): LastOperation? {
        val file = File(directory, LAST_OPERATION)
        if (!file.isFile) return null
        return try {
            LastOperation.fromJson(SettingsJson.parse(file.readText(Charsets.UTF_8)))
        } catch (_: SettingsJson.SettingsJsonException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    /** True when there is a last operation whose backup is still there ("undo the last import" is enabled). */
    fun canUndo(): Boolean = lastOperation()?.let { File(directory, it.backup).isFile } == true

    /**
     * Forgets the last operation, after it has been undone (an undo is not undone again).
     * zh-CN: 撤销完成后忘记最近一次操作 (撤销不能再次撤销).
     */
    fun clearLastOperation() {
        File(directory, LAST_OPERATION).delete()
    }

    /**
     * The undo of the last operation, or null when there is none: the touched settings categories imported again from
     * its backup ([SettingsImportPlanner], no deployment items). Categories the backup does not hold are left out;
     * throws [SettingsArchiveException] when the backup is missing or unreadable.
     * zh-CN: 撤销最近一次操作的内容, 没有时为 null: 以其备份再次导入触及的设置分类 ([SettingsImportPlanner], 没有部署项). 备份中没有的
     * 分类不计入; 备份缺失或无法读取时抛 [SettingsArchiveException].
     */
    @JvmOverloads
    fun undoPlan(
        environment: ImportEnvironment,
        keys: List<PrefKey<*>> = SettingsRegistry.keys,
        families: List<KeyFamily<*, *>> = SettingsRegistry.families,
        targets: List<ImportTarget> = SettingsImportTargets.ALL,
        open: (StoreId) -> SettingsStore = { SettingsStores[it] },
    ): Undo? {
        val last = lastOperation() ?: return null
        val file = File(directory, last.backup)
        if (!file.isFile) throw SettingsArchiveException(SettingsArchiveException.Reason.MISSING_FILE, "The backup ${last.backup} is gone")
        val reader = SettingsArchiveReader({ file.inputStream() }, key = key)
        val manifest = reader.manifest()
        val held = manifest.categories.map { it.id }.toSet()
        val ids = last.categories.filter { it in held }
        val plan = SettingsImportPlanner.plan(
            reader.categories(ids, manifest), ids, environment, withDeployments = false, keys = keys, families = families, targets = targets, open = open,
        )
        return Undo(last, plan, last.data)
    }

    private fun backupOf(file: File): Backup? {
        if (!file.isFile) return null
        val match = BACKUP_NAME.matchEntire(file.name) ?: return null
        val (label, time, sequence) = match.destructured
        return Backup(file, Operation.ofLabel(label)!!, time, sequence.ifEmpty { "0" }.toInt())
    }

    private fun writeAtomically(file: File, write: (OutputStream) -> Unit) {
        val temp = File(file.parentFile, file.name + ".tmp")
        try {
            temp.outputStream().use(write)
            if (file.exists() && !file.delete()) throw IOException("Cannot replace $file")
            if (!temp.renameTo(file)) throw IOException("Cannot rename $temp to $file")
        } finally {
            temp.delete()
        }
    }

    companion object {

        /** The directory name under `filesDir` (T9). zh-CN: `filesDir` 下的目录名 (T9). */
        const val DIRECTORY = "settings-backups"

        /** The record of the last operation (T9). zh-CN: 最近一次操作的记录 (T9). */
        const val LAST_OPERATION = "last-operation.json"

        /** Backups of imports and resets kept together (T9). zh-CN: 导入与恢复默认合计保留的备份数 (T9). */
        const val KEEP = 5

        private val BACKUP_NAME = Regex("""auto-(import|reset)-(\d{8}-\d{6})(?:-([1-9]\d{0,8}))?\.zip""")

        private val FILE_NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
    }
}
